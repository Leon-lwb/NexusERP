"""仅由显式--run入口执行的真实MySQL converter回归，不进入默认discover。"""

import copy
import json
import unittest
from decimal import Decimal
from core import digest


class ConverterCases(unittest.TestCase):
    env = None

    def prepare(self, **options):
        case = self.env.new_case(self.id().split(".")[-1], **options)
        case.prepare()
        return case

    def assert_applied(self, case):
        actual = case.actual()
        self.assertEqual(case.expected, actual)
        stocks = actual["tables"]["wm_material_stock"]
        positive = [row for row in stocks if Decimal(row["stock_quantity"]) > 0]
        self.assertEqual(3, len(positive))
        self.assertEqual(Decimal("3"), sum(Decimal(row["stock_quantity"]) for row in stocks))
        self.assertEqual(Decimal("10"), sum(Decimal(row["total_cost"]) for row in stocks))
        self.assertTrue(
            all(
                row["stock_quantity"] == "1.0000" and row["unit_cost"] == "3.333333"
                for row in positive
            )
        )
        self.assertEqual(
            {"101": "3.3334", "102": "3.3333", "103": "3.3333"},
            {row["sn_id"]: row["total_cost"] for row in positive},
        )
        old = next(row for row in stocks if row["stock_id"] == "401")
        original = case.before["tables"]["wm_material_stock"][0]
        self.assertEqual(original["sn_code"], old["sn_code"])
        self.assertEqual("0.0000", old["stock_quantity"])
        self.assertEqual(
            case.before["tables"]["wm_material_stock_tx"], actual["tables"]["wm_material_stock_tx"]
        )
        pool = actual["tables"]["wm_valuation_group"][0]
        self.assertEqual(
            ("3.0000", "10.0000", "3.333333"), (pool["quantity"], pool["amount"], pool["unit_cost"])
        )
        policy = actual["tables"]["bd_material"][0]
        self.assertEqual(
            ("1", "AUDIT", "2"),
            (policy["is_serial_manage"], policy["tracking_stage"], policy["tracking_version"]),
        )
        for field in ("is_batch_manage", "allow_mixed_lot"):
            self.assertEqual(case.before["tables"]["bd_material"][0][field], policy[field])
        for identity in actual["tables"]["wm_sn"]:
            current = next(row for row in positive if row["sn_id"] == identity["sn_id"])
            self.assertEqual(
                ("IN_STOCK", current["stock_id"], "1", "3"),
                (
                    identity["status"],
                    identity["stock_id"],
                    identity["warehouse_id"],
                    identity["location_id"],
                ),
            )
        self.assertEqual(
            ["APPLIED", "1", "1"],
            case.query(
                "SELECT status,version,BINARY SHA2(journal_json,256)=BINARY journal_hash FROM wm_trace_stock_conversion_run;"
            ).first,
        )

    def test_00_hex_literals_have_literal_coercibility_and_exact_utf8(self):
        from sql_writer import literal

        case = self.env.new_case(self.id().split(".")[-1])
        value = "合成 'quote' \\path\n第二行"
        result = case.query(
            "SELECT COERCIBILITY("
            + literal("T1")
            + "),HEX("
            + literal(value)
            + "),HEX("
            + literal("")
            + ");"
        ).first
        self.assertEqual(["4", value.encode("utf-8").hex().upper(), ""], result)

    def test_01_quantity_apply_conserves_stock_cost_valuation_identities_and_policy(self):
        case = self.prepare()
        case.execute("apply")
        self.assert_applied(case)

    def test_02_batch_to_batch_serial_preserves_real_batch(self):
        case = self.prepare(batch=True)
        case.execute("apply")
        self.assert_applied(case)
        self.assertTrue(
            all(row["batch_id"] == "301" for row in case.actual()["tables"]["wm_material_stock"])
        )

    def test_03_single_piece_legacy_sn_keeps_exact_identity_through_apply_and_revert(self):
        case = self.prepare(serial=True, batch=True, quantity="1.0000")
        case.execute("apply")
        actual = case.actual()
        self.assertEqual(case.expected, actual)
        positive = [
            r for r in actual["tables"]["wm_material_stock"] if Decimal(r["stock_quantity"]) > 0
        ]
        self.assertEqual(1, len(positive))
        self.assertEqual(
            ("101", "SYNTH-001", "1.0000", "10.0000"),
            tuple(positive[0][k] for k in ("sn_id", "sn_code", "stock_quantity", "total_cost")),
        )
        old = next(r for r in actual["tables"]["wm_material_stock"] if r["stock_id"] == "401")
        self.assertEqual(
            ("101", "SYNTH-001", "0.0000"),
            tuple(old[k] for k in ("sn_id", "sn_code", "stock_quantity")),
        )
        identity = actual["tables"]["wm_sn"][0]
        self.assertEqual(
            ("101", "IN_STOCK", "901"), tuple(identity[k] for k in ("sn_id", "status", "stock_id"))
        )
        self.assertEqual(
            case.before["tables"]["wm_material_stock_tx"], actual["tables"]["wm_material_stock_tx"]
        )
        case.generate_revert()
        case.execute("revert")
        self.assertEqual(case.before["tables"], case.actual()["tables"])
        identity = case.actual()["tables"]["wm_sn"][0]
        self.assertEqual(("IN_STOCK", "401"), (identity["status"], identity["stock_id"]))
        before = case.raw()
        case.execute("revert")
        self.assertEqual(before, case.raw())

    def test_04_repeated_apply_has_no_new_business_or_audit(self):
        case = self.prepare()
        case.execute("apply")
        before = case.raw()
        case.execute("apply")
        self.assertEqual(before, case.raw())

    def assert_atomic_rejection(self, case, error=1644):
        before = case.raw()
        case.execute("apply", error=error)
        self.assertEqual(before, case.raw())
        self.assertEqual(
            "0", case.query("SELECT COUNT(*) FROM wm_trace_stock_conversion_run;").scalar
        )
        self.assertEqual(
            str(len(before["wm_valuation_group"])),
            case.query("SELECT COUNT(*) FROM wm_valuation_group;").scalar,
        )

    def test_05_changed_source_hash_rolls_back_prepared_audit(self):
        case = self.prepare()
        case.mutate(
            "UPDATE wm_material_stock SET total_cost=11.0000,version=version+1 WHERE stock_id=401;"
        )
        self.assert_atomic_rejection(case)

    def test_06_missing_registered_identity_rejects_without_partial_conversion(self):
        case = self.prepare()
        case.mutate("DELETE FROM wm_sn WHERE sn_id=103;")
        self.assertFalse(case.offline_check()["ready"])
        self.assert_atomic_rejection(case)

    def test_07_pending_reserved_counter_is_not_split(self):
        case = self.prepare()
        case.mutate("UPDATE wm_material_stock SET reserved_quantity=1.0000 WHERE stock_id=401;")
        report = case.offline_check()
        self.assertFalse(report["ready"])
        self.assertEqual("BUSINESS_DOCUMENT_REQUIRED", report["tasks"][0]["code"])
        self.assert_atomic_rejection(case)

    def test_08_real_pending_reservation_blocks_even_with_zero_counter(self):
        case = self.prepare()
        case.insert(
            "wm_trace_material_reservation",
            dict(
                reservation_id="77",
                tenant_id="T1",
                command_id="SYNTH-RESERVATION",
                erp_wo_id="601",
                mes_wo_id="701",
                requirement_detail_id="801",
                material_id="10",
                policy_version="1",
                quantity="1.0000",
                content_hash="a" * 64,
                status="RESERVED",
            ),
        )
        case.insert(
            "wm_trace_material_reservation_line",
            dict(
                reservation_line_id="78",
                tenant_id="T1",
                reservation_id="77",
                line_no="1",
                source_stock_id="401",
                quantity="1.0000",
                consumed_quantity="0.0000",
                version="1",
            ),
        )
        report = case.offline_check()
        self.assertFalse(report["ready"])
        self.assertEqual("BUSINESS_DOCUMENT_REQUIRED", report["tasks"][0]["code"])
        self.assert_atomic_rejection(case)

    def test_09_whole_material_scope_cannot_omit_a_new_positive_source(self):
        case = self.prepare()
        row = copy.deepcopy(case.before["tables"]["wm_material_stock"][0])
        row.update(
            stock_id="402",
            stock_quantity="1.0000",
            total_cost="2.0000",
            unit_cost="2.000000",
            location_id="5",
        )
        case.insert("wm_material_stock", row)
        report = case.offline_check()
        self.assertFalse(report["ready"])
        self.assertEqual("MATERIAL_SCOPE_INCOMPLETE", report["tasks"][0]["code"])
        self.assert_atomic_rejection(case)

    def test_10_no_business_revert_and_repeated_revert_preserve_original_audit(self):
        case = self.prepare()
        case.execute("apply")
        original = case.audit_row()
        case.generate_revert()
        case.execute("revert")
        self.assertEqual(case.before["tables"], case.actual()["tables"])
        audit = case.audit_row()
        self.assertEqual(("REVERTED", "2"), (audit["status"], audit["version"]))
        self.assertEqual(original["journal_json"], audit["journal_json"])
        self.assertEqual(original["journal_hash"], audit["journal_hash"])
        self.assertEqual(case.load("reverted-journal.json")["journal_hash"], audit["revert_hash"])
        before = case.raw()
        case.execute("revert")
        self.assertEqual(before, case.raw())

    def test_11_new_ledger_rejects_old_revert_sql_and_only_outputs_compensation(self):
        case = self.prepare()
        case.execute("apply")
        case.generate_revert()
        current = case.actual()
        stock = next(r for r in current["tables"]["wm_material_stock"] if r["stock_id"] == "901")
        tx = copy.deepcopy(case.before["tables"]["wm_material_stock_tx"][0])
        tx.update(
            tx_id="502",
            tx_type="SYNTH_OUTBOUND",
            material_stock_id="901",
            sn_id="101",
            sn_code="SYNTH-001",
            tx_flag="0",
            tx_quantity="1.0000",
            before_quantity="1.0000",
            after_quantity="0.0000",
            tx_amount=stock["total_cost"],
            tracking_version="2",
        )
        case.insert("wm_material_stock_tx", tx)
        case.mutate(
            "UPDATE wm_material_stock SET stock_quantity=0,total_cost=0,version=version+1 WHERE stock_id=901;"
        )
        case.mutate(
            "UPDATE wm_sn SET status='ISSUED',stock_id=NULL,identity_version=identity_version+1 WHERE sn_id=101;"
        )
        before = case.raw()
        case.execute("revert", error=1644)
        self.assertEqual(before, case.raw())
        case.save("after-new-business.json", case.actual())
        case.cli(
            "convert-revert",
            "--snapshot",
            "after-new-business.json",
            "--journal",
            "journal.json",
            "--output",
            "unsafe-restored.json",
            "--journal-output",
            "unsafe-reverted.json",
            "--compensation-output",
            "compensation.json",
            expected=3,
        )
        case.cli(
            "convert-emit-sql",
            "--mode",
            "revert",
            "--snapshot",
            "after-new-business.json",
            "--journal",
            "journal.json",
            "--target",
            "erp:" + case.schema,
            "--backup-reference",
            "OWNED-TMPFS-SYNTHETIC",
            "--allow-db-writes",
            "--output",
            "unsafe-revert.sql",
            "--compensation-output",
            "sql-compensation.json",
            expected=3,
        )
        self.assertTrue(case.load("compensation.json")["compensation_tasks"])
        self.assertEqual("COMPENSATION_REQUIRED", case.load("sql-compensation.json")["status"])
        for name in ("unsafe-restored.json", "unsafe-reverted.json", "unsafe-revert.sql"):
            self.assertFalse((case.output / name).exists())

    def test_12_late_unique_constraint_failure_rolls_back_stock_pool_and_audit(self):
        case = self.prepare()
        case.mutate(
            "ALTER TABLE wm_material_stock ADD UNIQUE KEY uk_converter_test_location(tenant_id,warehouse_id,location_id);"
        )
        self.assert_atomic_rejection(case, error=1062)

    def test_13_metadata_stock_ids_need_not_follow_sn_order(self):
        case = self.prepare(shuffle_stock_ids=True)
        case.execute("apply")
        actual = case.actual()
        self.assertEqual(
            Decimal("10"),
            sum(Decimal(r["total_cost"]) for r in actual["tables"]["wm_material_stock"]),
        )
        case.generate_revert()
        case.execute("revert")
        self.assertEqual(case.before["tables"], case.actual()["tables"])

    def test_14_unicode_quotes_and_control_characters_keep_exact_hashes(self):
        case = self.prepare(unicode_fields=True)
        case.execute("apply")
        self.assert_applied(case)

    def test_15_cross_tenant_target_id_collision_preserves_both_tenants(self):
        case = self.prepare()
        row = copy.deepcopy(case.before["tables"]["wm_material_stock"][0])
        row.update(tenant_id="T2", stock_id="901", stock_quantity="0.0000", total_cost="0.0000")
        case.insert("wm_material_stock", row)
        self.assert_atomic_rejection(case, error=1062)

    def test_16_existing_zero_history_peer_joins_the_same_runtime_valuation_pool(self):
        case = self.prepare(zero_peer=True)
        case.execute("apply")
        self.assert_applied(case)
        peer = next(
            row for row in case.actual()["tables"]["wm_material_stock"] if row["stock_id"] == "402"
        )
        self.assertEqual(
            ("0.0000", "0.0000", "2", "801", "SYNTH-ZERO-HISTORY"),
            (
                peer["stock_quantity"],
                peer["total_cost"],
                peer["tracking_version"],
                peer["valuation_group_id"],
                peer["sn_code"],
            ),
        )
        case.generate_revert()
        case.execute("revert")
        self.assertEqual(case.before["tables"], case.actual()["tables"])

    def assert_shared_legacy_rejected(self, case):
        before = case.raw()
        case.cli(
            "convert-check",
            "--snapshot",
            "before.json",
            "--plan",
            "plan.json",
            "--output",
            "legacy-review-tasks.json",
            expected=3,
        )
        report = case.load("legacy-review-tasks.json")
        self.assertFalse(report["ready"])
        self.assertEqual("LEGACY_MULTI_SN_REVIEW", report["tasks"][0]["code"])
        case.cli(
            "convert-apply",
            "--snapshot",
            "before.json",
            "--plan",
            "plan.json",
            "--output",
            "blocked-after.json",
            "--journal-output",
            "blocked-journal.json",
            expected=2,
        )
        self.assertEqual(before, case.raw())
        self.assertFalse((case.output / "blocked-after.json").exists())
        self.assertFalse((case.output / "blocked-journal.json").exists())
        self.assertFalse((case.output / "review-apply.sql").exists())
        old = case.query(
            "SELECT stock_quantity,sn_id,sn_code FROM wm_material_stock WHERE stock_id=401;"
        ).first
        identity = case.query("SELECT status,stock_id,sn_code FROM wm_sn WHERE sn_id=101;").first
        self.assertEqual(["3.0000", "101", "SYNTH-001"], old)
        self.assertEqual(["IN_STOCK", "401", "SYNTH-001"], identity)
        self.assertEqual(
            "0", case.query("SELECT COUNT(*) FROM wm_trace_stock_conversion_run;").scalar
        )

    def test_17_shared_legacy_sn_requires_review(self):
        case = self.env.new_case(self.id().split(".")[-1], serial=True, batch=True)
        case.prepare(ready=False)
        self.assert_shared_legacy_rejected(case)

    def test_18_registered_replacements_keep_legacy_blocked(self):
        from conversion_fixtures import reseal

        case = self.env.new_case(self.id().split(".")[-1], serial=True)
        case.prepare(ready=False)
        extra = {
            **case.before["tables"]["wm_sn"][-1],
            "sn_id": "104",
            "sn_code": "SYNTH-004",
            "registration_command": "SYNTH-REGISTER-4",
        }
        case.insert("wm_sn", extra)
        case.before = case.actual()
        case.plan = case.plan_for(case.before)
        case.plan["sources"][0]["targets"] = [
            t for t in case.plan["sources"][0]["targets"] if t["identity_id"] != "101"
        ]
        reseal(case.plan)
        case.save("before.json", case.before)
        case.save("plan.json", case.plan)
        self.assert_shared_legacy_rejected(case)

    def test_19_stale_apply_rejects_shared_sn_source(self):
        case = self.prepare()
        case.mutate(
            "UPDATE wm_material_stock SET sn_id=101,sn_code='SYNTH-001' WHERE stock_id=401;"
        )
        case.mutate("UPDATE wm_sn SET status='IN_STOCK',stock_id=401 WHERE sn_id=101;")
        self.assert_atomic_rejection(case)
        self.assertEqual(
            ["IN_STOCK", "401"],
            case.query("SELECT status,stock_id FROM wm_sn WHERE sn_id=101;").first,
        )

    def test_20_orphan_in_stock_identity_blocks_conversion(self):
        case = self.prepare()
        extra = {
            **case.before["tables"]["wm_sn"][-1],
            "sn_id": "104",
            "sn_code": "SYNTH-004",
            "registration_command": "SYNTH-REGISTER-4",
            "status": "IN_STOCK",
            "stock_id": None,
        }
        case.insert("wm_sn", extra)
        report = case.offline_check()
        self.assertFalse(report["ready"])
        self.assertEqual("IN_STOCK_IDENTITY_REVIEW", report["tasks"][0]["code"])
        self.assert_atomic_rejection(case)
        self.assertEqual(
            ["IN_STOCK", "NULL"],
            case.query("SELECT status,stock_id FROM wm_sn WHERE sn_id=104;").first,
        )

    def test_21_new_policy_bank_blocks_old_revert_and_emits_compensation(self):
        case = self.prepare()
        case.execute("apply")
        case.generate_revert()
        row = dict(
            snapshot_id="1001",
            tenant_id="T1",
            erp_wo_id="601",
            material_id="10",
            snapshot_version="1",
            policy_version="1",
            policy_json="{}",
            policy_hash="a" * 64,
            source_json="{}",
            source_hash="b" * 64,
            snapshot_hash="c" * 64,
            provenance="SOURCE_POLICY_ADOPTION",
            evidence_ref="synthetic://approved-original-policy/1",
            frozen_at="2026-01-01 00:00:00.000",
            effective_from="2026-01-01 00:00:00.000",
            actor_id="7",
        )
        case.insert("wm_work_order_tracking_policy", row)
        before = case.raw()
        case.execute("revert", error=1644)
        self.assertEqual(before, case.raw())
        case.save("changed.json", case.actual())
        case.cli(
            "convert-revert",
            "--snapshot",
            "changed.json",
            "--journal",
            "journal.json",
            "--output",
            "unsafe-restored.json",
            "--journal-output",
            "unsafe-journal.json",
            "--compensation-output",
            "compensation.json",
            expected=3,
        )
        self.assertTrue(
            any(
                t["table"] == "wm_work_order_tracking_policy"
                for t in case.load("compensation.json")["compensation_tasks"]
            )
        )
        self.assertFalse((case.output / "unsafe-restored.json").exists())

    def test_22_zero_quantity_cost_residue_is_not_hidden(self):
        case = self.prepare()
        row = copy.deepcopy(case.before["tables"]["wm_material_stock"][0])
        row.update(stock_id="402", stock_quantity="0.0000", total_cost="2.0000", location_id="99")
        case.insert("wm_material_stock", row)
        report = case.offline_check()
        self.assertFalse(report["ready"])
        self.assertEqual("ZERO_QUANTITY_COST_REVIEW", report["tasks"][0]["code"])
        self.assert_atomic_rejection(case)

    def test_23_orphan_nonzero_pool_requires_review(self):
        case = self.prepare()
        case.insert(
            "wm_valuation_group",
            dict(
                valuation_group_id="800",
                tenant_id="T1",
                material_id="10",
                warehouse_id="1",
                batch_id=None,
                source_stock_id="401",
                quantity="1.0000",
                amount="2.0000",
                unit_cost="2.000000",
                valuation_method="MOVING_AVERAGE",
                policy_version="1",
                valuation_snapshot="{}",
            ),
        )
        report = case.offline_check()
        self.assertFalse(report["ready"])
        self.assertEqual("VALUATION_GROUP_REVIEW", report["tasks"][0]["code"])
        self.assert_atomic_rejection(case)

    def test_24_zero_history_pool_survives_apply_revert(self):
        case = self.prepare(zero_history_pool=True)
        case.execute("apply")
        self.assertEqual(case.expected, case.actual())
        old = next(
            r
            for r in case.actual()["tables"]["wm_valuation_group"]
            if r["valuation_group_id"] == "800"
        )
        self.assertEqual(case.before["tables"]["wm_valuation_group"][0], old)
        case.generate_revert()
        case.execute("revert")
        self.assertEqual(case.before["tables"], case.actual()["tables"])

    def adoption_fixture(self):
        from harness import ident, sql_value
        from test_policy_adoption import fixture, seal
        from core import canonical

        case = self.env.new_case(self.id().split(".")[-1])
        case.prepare(ready=False)
        for table in self.env.tables:
            case.mutate("UPDATE " + ident(table) + " SET tenant_id='000001' WHERE tenant_id='T1';")
        case.mutate(self.env.ddl["prd_work_order"])
        _, plan = fixture()
        row = plan["source_before"]
        case.mutate(
            "INSERT INTO prd_work_order ("
            + ",".join(ident(k) for k in row)
            + ") VALUES ("
            + ",".join(sql_value(v) for v in row.values())
            + ");"
        )
        expression = (
            "JSON_OBJECT("
            + ",".join(
                sql_value(k) + ",CAST(" + ident(k) + " AS CHAR CHARACTER SET utf8mb4)"
                for k in sorted(row)
            )
            + ")"
        )
        actual = json.loads(
            case.query("SELECT " + expression + " FROM prd_work_order WHERE wo_id=501;").scalar
        )
        before = json.loads(json.dumps(case.before).replace('"T1"', '"000001"'))
        before["tables"] = {
            name: (
                self.env.rows(case.schema, name, all_tenants=True)
                if name in self.env.erp_tables
                else []
            )
            for name in before["tables"]
        }
        plan.update(
            snapshot_hash=digest(before), source_before=actual, source_before_hash=digest(actual)
        )
        seal(plan)
        case.save("adopt-before.json", before)
        case.save("adopt-plan.json", plan)
        case.cli(
            "policy-adoption-plan",
            "--snapshot",
            "adopt-before.json",
            "--plan",
            "adopt-plan.json",
            "--output",
            "adopt-journal.json",
            "--snapshot-output",
            "adopt-after.json",
        )
        case.cli(
            "policy-adoption-emit-sql",
            "--snapshot",
            "adopt-before.json",
            "--journal",
            "adopt-journal.json",
            "--target",
            "erp:" + case.schema,
            "--backup-reference",
            "OWNED-TMPFS-SYNTHETIC",
            "--allow-db-writes",
            "--output",
            "review-adopt.sql",
        )
        return case

    def test_25_original_policy_adoption_replays_without_business_writes(self):
        case = self.adoption_fixture()
        before = case.raw()
        case.execute("adopt")
        after = case.raw()
        expected = case.load("adopt-journal.json")["rows"]
        self.assertEqual(expected, after["wm_work_order_policy_adoption"])
        self.assertEqual("APPROVED", after["wm_work_order_policy_adoption"][0]["status"])
        self.assertEqual([], after["wm_work_order_tracking_policy"])
        for table in before:
            if table != "wm_work_order_policy_adoption":
                self.assertEqual(before[table], after[table])
        case.execute("adopt")
        self.assertEqual(after, case.raw())
        case.mutate("UPDATE wm_sn SET identity_version=identity_version+1 WHERE sn_id=101;")
        changed = case.raw()
        case.execute("adopt", error=1644)
        self.assertEqual(changed, case.raw())

    def test_26_original_policy_adoption_rejects_changed_work_order(self):
        case = self.adoption_fixture()
        case.mutate("UPDATE prd_work_order SET remark='SYNTH-CHANGED' WHERE wo_id=501;")
        before = case.raw()
        case.execute("adopt", error=1644)
        self.assertEqual(before, case.raw())
        self.assertEqual([], case.raw()["wm_work_order_tracking_policy"])
        self.assertEqual([], case.raw()["wm_work_order_policy_adoption"])

    def strict_review(self, case, *, confirmed=True):
        from sql_writer import literal

        script = self.env.raw[
            "script/sql/update/2026-10-19-material-trace-strict-constraints-erp.sql"
        ].decode("utf-8")
        prefix = "-- target: erp:" + case.schema + "\n"
        values = dict(
            material_trace_expected_schema=case.schema,
            material_trace_expected_server_uuid=self.env.server,
            material_trace_backup_reference="OWNED-TMPFS-SYNTHETIC",
            material_trace_maintenance_reference="SYNTH-EXCLUSIVE-CENSUS",
            material_trace_strict_tenant_cleanup_confirmed=(
                "TENANT_CLEANUP_VERIFIED" if confirmed else None
            ),
        )
        prefix += "".join(
            "SET @" + name + "=" + literal(value) + ";\n" for name, value in values.items()
        )
        (case.output / "review-strict.sql").write_text(prefix + script, encoding="utf-8")

    def tenant_nullability(self, case):
        return case.query(
            "SELECT TABLE_NAME,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('wm_sn','wm_material_stock') AND COLUMN_NAME='tenant_id' ORDER BY TABLE_NAME;"
        ).rows

    def test_27_strict_constraints_reject_null_tenants_and_replay(self):
        from harness import SqlFailure

        case = self.prepare()
        self.strict_review(case)
        before = case.raw()
        case.execute("strict")
        self.assertEqual(before, case.raw())
        self.assertEqual(
            [["wm_material_stock", "NO"], ["wm_sn", "NO"]], self.tenant_nullability(case)
        )
        for table in ("wm_sn", "wm_material_stock"):
            row = copy.deepcopy(case.before["tables"][table][0])
            row["tenant_id"] = None
            if table == "wm_sn":
                row.update(sn_id="104", sn_code="SYNTH-NULL")
            else:
                row.update(stock_id="904")
            with self.assertRaises(SqlFailure) as caught:
                case.insert(table, row)
            self.assertIn("1048", caught.exception.output)
        self.assertEqual(before, case.raw())
        case.execute("strict")
        self.assertEqual(before, case.raw())

    def test_28_strict_constraints_stop_before_alter_on_legacy_null(self):
        case = self.prepare()
        case.mutate("UPDATE wm_sn SET tenant_id=NULL WHERE sn_id=101;")
        case.mutate("UPDATE wm_material_stock SET tenant_id=NULL WHERE stock_id=401;")
        before = case.raw()
        self.strict_review(case)
        case.execute("strict", error=1644)
        self.assertEqual(before, case.raw())
        self.assertEqual(
            [["wm_material_stock", "YES"], ["wm_sn", "YES"]], self.tenant_nullability(case)
        )

    def test_29_strict_constraints_stop_on_old_code_conflict(self):
        case = self.prepare()
        case.mutate("ALTER TABLE wm_sn DROP INDEX uk_sn_code;")
        duplicate = copy.deepcopy(case.before["tables"]["wm_sn"][0])
        duplicate["sn_id"] = "104"
        case.insert("wm_sn", duplicate)
        before = case.raw()
        self.strict_review(case)
        case.execute("strict", error=1644)
        self.assertEqual(before, case.raw())
        self.assertEqual(
            [["wm_material_stock", "YES"], ["wm_sn", "YES"]], self.tenant_nullability(case)
        )

    def test_30_strict_constraints_require_explicit_cleanup_confirmation(self):
        case = self.prepare()
        before = case.raw()
        self.strict_review(case, confirmed=False)
        case.execute("strict", error=1644)
        self.assertEqual(before, case.raw())
        self.assertEqual(
            [["wm_material_stock", "YES"], ["wm_sn", "YES"]], self.tenant_nullability(case)
        )

    def test_31_new_barcode_blocks_old_revert_without_rebinding(self):
        case = self.prepare()
        case.execute("apply")
        case.generate_revert()
        case.insert(
            "wm_barcode",
            dict(
                barcode_id="900",
                tenant_id="T1",
                barcode_code="SYNTH-LEGACY-BC",
                material_id="10",
                stock_id="401",
                primary_object_type=None,
            ),
        )
        before = case.raw()
        case.execute("revert", error=1644)
        self.assertEqual(before, case.raw())
        case.save("changed.json", case.actual())
        case.cli(
            "convert-revert",
            "--snapshot",
            "changed.json",
            "--journal",
            "journal.json",
            "--output",
            "unsafe.json",
            "--journal-output",
            "unsafe-journal.json",
            "--compensation-output",
            "compensation.json",
            expected=3,
        )
        self.assertTrue(
            any(
                t["table"] == "wm_barcode"
                for t in case.load("compensation.json")["compensation_tasks"]
            )
        )
        self.assertIsNone(case.raw()["wm_barcode"][0]["primary_object_type"])
