import copy
import sys
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from core import digest, WRITABLE
from conversion_fixtures import inventory, conversion_plan, reseal, two_scopes

try:
    import stock_converter as converter
except ModuleNotFoundError:
    converter = None


class StockConverterTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(converter, "需要代码化当前库存身份转换")
        self.s = inventory()
        self.p = conversion_plan(self.s)

    def test_quantity_to_registered_sn_preserves_every_quantity_amount_and_original_ledger(self):
        before = copy.deepcopy(self.s)
        after, j = converter.apply(self.s, self.p)
        self.assertEqual(before, self.s)
        stocks = after["tables"]["wm_material_stock"]
        old = next(r for r in stocks if r["stock_id"] == "401")
        self.assertEqual("0.0000", old["stock_quantity"])
        self.assertEqual("0.0000", old["total_cost"])
        self.assertEqual("LEGACY-LOT-CODE", old["sn_code"])
        current = sorted(
            (r for r in stocks if Decimal(r["stock_quantity"]) > 0), key=lambda r: int(r["sn_id"])
        )
        self.assertEqual(["3.3334", "3.3333", "3.3333"], [r["total_cost"] for r in current])
        self.assertEqual(Decimal("3"), sum(Decimal(r["stock_quantity"]) for r in stocks))
        self.assertEqual(Decimal("10"), sum(Decimal(r["total_cost"]) for r in stocks))
        for r in current:
            self.assertEqual("1.0000", r["stock_quantity"])
            self.assertEqual("2", r["tracking_version"])
            for field in (
                "warehouse_id",
                "area_id",
                "location_id",
                "batch_id",
                "vendor_id",
                "unit_cost",
                "base_uom_id",
            ):
                self.assertEqual(before["tables"]["wm_material_stock"][0][field], r[field])
            identity = next(n for n in after["tables"]["wm_sn"] if n["sn_id"] == r["sn_id"])
            self.assertEqual(r["stock_id"], identity["stock_id"])
            self.assertEqual("IN_STOCK", identity["status"])
        self.assertEqual(
            before["tables"]["wm_material_stock_tx"], after["tables"]["wm_material_stock_tx"]
        )
        policy = after["tables"]["bd_material"][0]
        self.assertEqual(
            ("1", "2", "AUDIT"),
            (policy["is_serial_manage"], policy["tracking_version"], policy["tracking_stage"]),
        )
        self.assertEqual(
            before["tables"]["bd_material"][0]["allow_mixed_lot"], policy["allow_mixed_lot"]
        )
        self.assertEqual("APPLIED", j["status"])
        self.assertTrue(after["stock_conversion_bridges"])

    def test_batch_conversion_and_single_unit_legacy_sn_keep_original_identity_and_batch(self):
        for serial, batch in ((False, True), (True, False), (True, True)):
            with self.subTest(serial=serial, batch=batch):
                s = inventory(quantity="1.0000" if serial else "3.0000", serial=serial, batch=batch)
                p = conversion_plan(s)
                after, j = converter.apply(s, p)
                self.assertEqual(
                    s["tables"]["wm_material_stock_tx"], after["tables"]["wm_material_stock_tx"]
                )
                self.assertEqual(
                    s["tables"]["wm_material_stock"][0]["sn_id"],
                    after["tables"]["wm_material_stock"][0]["sn_id"],
                )
                self.assertEqual(
                    s["tables"]["wm_material_stock"][0]["batch_id"],
                    after["tables"]["wm_material_stock"][1]["batch_id"],
                )

    def test_shared_legacy_multi_sn_is_blocked_instead_of_choosing_a_unit_to_inherit(self):
        s = inventory(serial=True, batch=True)
        p = conversion_plan(s)
        before = copy.deepcopy(s)
        report = converter.assess(s, p)
        self.assertFalse(report["ready"])
        self.assertEqual("LEGACY_MULTI_SN_REVIEW", report["tasks"][0]["code"])
        self.assertTrue(report["tasks"][0]["key"])
        with self.assertRaises(converter.ConversionBlocked):
            converter.apply(s, p)
        self.assertEqual(before, s)
        self.assertEqual(
            ("IN_STOCK", "401"),
            (s["tables"]["wm_sn"][0]["status"], s["tables"]["wm_sn"][0]["stock_id"]),
        )

    def test_registered_replacements_cannot_leave_a_shared_old_identity_claiming_zero_stock(self):
        s = inventory(serial=True)
        extra = {
            **s["tables"]["wm_sn"][-1],
            "sn_id": "104",
            "sn_code": "SYNTH-004",
            "registration_command": "SYNTH-REGISTER-4",
        }
        s["tables"]["wm_sn"].append(extra)
        p = conversion_plan(s)
        p["sources"][0]["targets"] = [
            t for t in p["sources"][0]["targets"] if t["identity_id"] != "101"
        ]
        reseal(p)
        before = copy.deepcopy(s)
        report = converter.assess(s, p)
        self.assertFalse(report["ready"])
        self.assertEqual("LEGACY_MULTI_SN_REVIEW", report["tasks"][0]["code"])
        with self.assertRaises(converter.ConversionBlocked):
            converter.apply(s, p)
        self.assertEqual(before, s)
        self.assertEqual("3.0000", s["tables"]["wm_material_stock"][0]["stock_quantity"])

    def test_in_stock_identity_without_exactly_one_positive_stock_blocks_entire_material(self):
        for pointer in (None, "999"):
            with self.subTest(pointer=pointer):
                s = inventory()
                extra = {
                    **s["tables"]["wm_sn"][-1],
                    "sn_id": "104",
                    "sn_code": "SYNTH-004",
                    "registration_command": "SYNTH-REGISTER-4",
                    "status": "IN_STOCK",
                    "stock_id": pointer,
                }
                s["tables"]["wm_sn"].append(extra)
                p = conversion_plan(s)
                p["sources"][0]["targets"] = [
                    t for t in p["sources"][0]["targets"] if t["identity_id"] != "104"
                ]
                reseal(p)
                before = copy.deepcopy(s)
                report = converter.assess(s, p)
                self.assertFalse(report["ready"])
                self.assertEqual("IN_STOCK_IDENTITY_REVIEW", report["tasks"][0]["code"])
                with self.assertRaises(converter.ConversionBlocked):
                    converter.apply(s, p)
                self.assertEqual(before, s)

    def test_zero_quantity_amount_residue_anywhere_in_material_cannot_be_hidden(self):
        s = inventory()
        s["tables"]["wm_material_stock"].append(
            {
                **s["tables"]["wm_material_stock"][0],
                "stock_id": "402",
                "stock_quantity": "0.0000",
                "total_cost": "2.0000",
                "location_id": "99",
            }
        )
        p = conversion_plan(s)
        before = copy.deepcopy(s)
        report = converter.assess(s, p)
        self.assertFalse(report["ready"])
        self.assertEqual("ZERO_QUANTITY_COST_REVIEW", report["tasks"][0]["code"])
        with self.assertRaises(converter.ConversionBlocked):
            converter.apply(s, p)
        self.assertEqual(before, s)

    def test_orphan_pool_balance_blocks_but_zero_zero_history_pool_is_preserved(self):
        from fixtures import complete_row

        for quantity, amount in (("1.0000", "0.0000"), ("0.0000", "1.0000"), ("0.0000", "0.0000")):
            with self.subTest(quantity=quantity, amount=amount):
                s = inventory()
                s["tables"]["wm_valuation_group"].append(
                    complete_row(
                        "wm_valuation_group",
                        dict(
                            tenant_id="T1",
                            valuation_group_id="800",
                            material_id="10",
                            quantity=quantity,
                            amount=amount,
                        ),
                    )
                )
                p = conversion_plan(s)
                report = converter.assess(s, p)
                self.assertEqual(quantity == amount == "0.0000", report["ready"])
                if report["ready"]:
                    after, journal = converter.apply(s, p)
                    self.assertEqual(
                        s["tables"]["wm_valuation_group"][0],
                        after["tables"]["wm_valuation_group"][0],
                    )
                    restored, _ = converter.revert(after, journal)
                    self.assertEqual(s["tables"], restored["tables"])
                else:
                    self.assertEqual("VALUATION_GROUP_REVIEW", report["tasks"][0]["code"])

    def test_numeric_sn_order_and_request_order_do_not_change_allocation(self):
        for identity, sn in zip(self.s["tables"]["wm_sn"], ("9", "10", "2")):
            identity["sn_id"] = sn
        self.p = conversion_plan(self.s)
        self.p["sources"][0]["targets"].reverse()
        reseal(self.p)
        after, j = converter.apply(self.s, self.p)
        first = next(
            r
            for r in after["tables"]["wm_material_stock"]
            if r["sn_id"] == "2" and r["stock_id"] != "401"
        )
        self.assertEqual("3.3334", first["total_cost"])

    def test_replay_and_safe_revert_are_idempotent_and_restore_exact_snapshot(self):
        after, j = converter.apply(self.s, self.p)
        self.assertEqual((after, j), converter.apply(after, self.p, j))
        restored, rev = converter.revert(after, j)
        self.assertEqual(self.s["tables"], restored["tables"])
        self.assertEqual("REVERTED", rev["status"])
        self.assertEqual("REVERTED", restored["conversion_run_states"][-1]["status"])
        self.assertEqual((restored, rev), converter.revert(restored, rev))
        with self.assertRaises(ValueError):
            converter.apply(after, self.p)

    def test_source_policy_and_identity_hash_cas(self):
        for change in (
            lambda s: s["tables"]["wm_material_stock"][0].update(total_cost="11.0000"),
            lambda s: s["tables"]["bd_material"][0].update(tracking_version="2"),
            lambda s: s["tables"]["wm_sn"][0].update(identity_version="2"),
        ):
            s = copy.deepcopy(self.s)
            change(s)
            self.assertFalse(converter.assess(s, self.p)["ready"])
            with self.assertRaises(ValueError):
                converter.apply(s, self.p)

    def test_missing_approval_or_tampered_census_cannot_authorize_conversion(self):
        for field in ("approved_by", "artifact", "artifact_sha256"):
            p = copy.deepcopy(self.p)
            p["census"].pop(field)
            with self.assertRaises(ValueError):
                converter.apply(self.s, p)

    def test_unknown_or_cross_tenant_identity_cannot_be_created(self):
        for identity in ("999",):
            self.p["sources"][0]["targets"][0]["identity_id"] = identity
            reseal(self.p)
            with self.assertRaises(ValueError):
                converter.apply(self.s, self.p)
        self.s["tables"]["wm_sn"][0]["tenant_id"] = "T2"
        self.p = conversion_plan(self.s)
        with self.assertRaises(ValueError):
            converter.apply(self.s, self.p)

    def test_reserved_wip_or_fractional_stock_produces_difference_tasks(self):
        for changes in (
            {"reserved_quantity": "1.0000"},
            {"wo_id": "600"},
            {"stock_quantity": "2.5000"},
            {"frozen_flag": "1"},
        ):
            with self.subTest(changes=changes):
                s = copy.deepcopy(self.s)
                s["tables"]["wm_material_stock"][0].update(changes)
                p = conversion_plan(s)
                report = converter.assess(s, p)
                self.assertFalse(report["ready"])
                self.assertTrue(report["tasks"])
                with self.assertRaises(ValueError):
                    converter.apply(s, p)

    def test_all_material_positive_stock_must_be_included(self):
        self.s["tables"]["wm_material_stock"].append(
            {**self.s["tables"]["wm_material_stock"][0], "stock_id": "402"}
        )
        self.p = conversion_plan(self.s)
        self.assertFalse(converter.assess(self.s, self.p)["ready"])

    def test_new_business_blocks_restore_and_returns_compensation_tasks(self):
        after, j = converter.apply(self.s, self.p)
        after["tables"]["mes_label_print_task"].append({"tenant_id": "T1", "task_id": "77"})
        with self.assertRaises(converter.CompensationRequired) as caught:
            converter.revert(after, j)
        self.assertTrue(caught.exception.report["compensation_tasks"])
        self.assertEqual("COMPENSATION_REQUIRED", caught.exception.report["status"])

    def test_native_export_key_order_allows_revert_without_changing_frozen_journal(self):
        for target, stock in zip(self.p["sources"][0]["targets"], ("903", "901", "902")):
            target["stock_id"] = stock
        reseal(self.p)
        after, journal = converter.apply(self.s, self.p)
        original = copy.deepcopy(journal)
        after["tables"]["wm_material_stock"].sort(key=lambda row: int(row["stock_id"]))
        restored, reverted = converter.revert(after, journal)
        self.assertEqual(self.s["tables"], restored["tables"])
        self.assertEqual(original, journal)
        self.assertEqual(original["after_snapshot_hash"], reverted["after_snapshot_hash"])

    def test_native_export_order_is_idempotent_for_apply_and_reverted_journal(self):
        after, journal = converter.apply(self.s, self.p)
        after["tables"]["wm_sn"].reverse()
        replay, same = converter.apply(after, self.p, journal)
        self.assertEqual(after, replay)
        self.assertEqual(journal, same)
        restored, reverted = converter.revert(after, journal)
        restored["tables"]["wm_sn"].reverse()
        replay, same = converter.revert(restored, reverted)
        self.assertEqual(restored, replay)
        self.assertEqual(reverted, same)

    def test_native_audit_state_order_does_not_change_idempotent_revert(self):
        self.s["conversion_run_states"].append(
            {
                "tenant_id": "T1",
                "run_id": "Z-PRIOR",
                "material_id": "10",
                "status": "REVERTED",
                "version": "2",
                "plan_hash": "a" * 64,
                "bridge_hash": "b" * 64,
            }
        )
        self.p = conversion_plan(self.s)
        self.p["run_id"] = "A-FRESH"
        reseal(self.p)
        after, journal = converter.apply(self.s, self.p)
        after["conversion_run_states"].reverse()
        restored, reverted = converter.revert(after, journal)
        restored["conversion_run_states"].reverse()
        replay, same = converter.revert(restored, reverted)
        self.assertEqual(restored, replay)
        self.assertEqual(reverted, same)

    def test_row_order_normalization_does_not_hide_duplicate_rows_or_changed_values(self):
        after, journal = converter.apply(self.s, self.p)
        for kind in ("duplicate", "changed"):
            with self.subTest(kind=kind):
                current = copy.deepcopy(after)
                current["tables"]["wm_material_stock"].reverse()
                if kind == "duplicate":
                    current["tables"]["wm_material_stock"].append(
                        copy.deepcopy(current["tables"]["wm_material_stock"][0])
                    )
                else:
                    current["tables"]["wm_material_stock"][0]["total_cost"] = "99.0000"
                with self.assertRaises(converter.CompensationRequired):
                    converter.revert(current, journal)

    def test_row_order_normalization_keeps_export_boundary_and_frozen_evidence_protected(self):
        after, journal = converter.apply(self.s, self.p)
        for field in ("captured_at", "stock_conversion_bridges"):
            with self.subTest(field=field):
                current = copy.deepcopy(after)
                current["tables"]["wm_sn"].reverse()
                if field == "captured_at":
                    current[field] = "2026-10-19 00:00:01"
                else:
                    current[field][0]["plan"]["census"]["approved_by"] = "different-reviewer"
                with self.assertRaises(converter.CompensationRequired):
                    converter.revert(current, journal)

    def test_repair_five_table_write_contract_is_unchanged(self):
        self.assertEqual(
            {"wm_sn", "wm_material_stock", "wm_material_stock_tx", "pro_sn", "lt_lot"},
            set(WRITABLE),
        )

    def test_census_artifact_binds_entire_snapshot_including_zero_pool_peers(self):
        zero = {
            **self.s["tables"]["wm_material_stock"][0],
            "stock_id": "402",
            "stock_quantity": "0.0000",
            "total_cost": "0.0000",
        }
        self.s["tables"]["wm_material_stock"].append(zero)
        self.p = conversion_plan(self.s)
        zero["sn_code"] = "UNAPPROVED-OLD-CODE"
        self.p["snapshot_hash"] = digest(self.s)
        with self.assertRaises(ValueError):
            converter.apply(self.s, self.p)

    def test_census_bridge_explains_source_cutoff_without_fake_receipt_or_gain(self):
        from audit import audit

        before = audit(self.s)
        after, j = converter.apply(self.s, self.p)
        report = audit(after)
        self.assertEqual(before["totals"], report["totals"])
        self.assertNotIn("STOCK_TX_END_BALANCE_MISMATCH", {f["code"] for f in report["findings"]})
        self.assertIn("CENSUS_IDENTITY_HISTORY_BOUNDARY", {f["code"] for f in report["findings"]})
        self.assertEqual([], after.get("opening_balances", []))

    def test_original_ledger_gap_remains_visible_after_current_stock_conversion(self):
        from audit import audit

        self.s["tables"]["wm_material_stock_tx"] = []
        self.p = conversion_plan(self.s)
        self.p["census"]["history_boundary"]["coverage"] = "GAPPED_AGGREGATE"
        reseal(self.p)
        after, j = converter.apply(self.s, self.p)
        report = audit(after)
        self.assertIn("QUANTITY_RECONCILIATION_MISMATCH", {f["code"] for f in report["findings"]})
        self.assertIn("CENSUS_IDENTITY_HISTORY_BOUNDARY", {f["code"] for f in report["findings"]})

    def test_forged_bridge_must_not_hide_legacy_stock_transaction_mismatch(self):
        from audit import audit

        after, j = converter.apply(self.s, self.p)
        after["stock_conversion_bridges"][0]["sources"][0]["source_before"][
            "stock_quantity"
        ] = "99.0000"
        report = audit(after)
        self.assertIn("INVALID_STOCK_CONVERSION_BRIDGE", {f["code"] for f in report["findings"]})
        self.assertIn("STOCK_TX_END_BALANCE_MISMATCH", {f["code"] for f in report["findings"]})

    def test_zero_legacy_pool_peers_are_mapped_without_changing_their_original_codes(self):
        zero = {
            **self.s["tables"]["wm_material_stock"][0],
            "stock_id": "402",
            "stock_quantity": "0.0000",
            "total_cost": "0.0000",
            "sn_code": "SYNTH-OLD-ZERO",
        }
        self.s["tables"]["wm_material_stock"].append(zero)
        self.p = conversion_plan(self.s)
        after, j = converter.apply(self.s, self.p)
        z = next(r for r in after["tables"]["wm_material_stock"] if r["stock_id"] == "402")
        self.assertEqual(
            ("2", "801", "SYNTH-OLD-ZERO"),
            (z["tracking_version"], z["valuation_group_id"], z["sn_code"]),
        )

    def test_existing_v2_transactions_cannot_be_attached_to_a_new_census_pool(self):
        self.s["tables"]["wm_material_stock_tx"][0]["tracking_version"] = "2"
        self.p = conversion_plan(self.s)
        self.assertFalse(converter.assess(self.s, self.p)["ready"])

    def test_converter_audit_source_scope_is_required_and_new_state_blocks_old_revert(self):
        incomplete = copy.deepcopy(self.s)
        incomplete.pop("conversion_run_states")
        p = conversion_plan(incomplete)
        with self.assertRaises(ValueError):
            converter.apply(incomplete, p)
        after, j = converter.apply(self.s, self.p)
        after["conversion_run_states"].append(
            {**after["conversion_run_states"][0], "run_id": "ANOTHER-CONVERSION"}
        )
        with self.assertRaises(converter.CompensationRequired) as caught:
            converter.revert(after, j)
        self.assertEqual(
            "wm_trace_stock_conversion_run",
            caught.exception.report["compensation_tasks"][0]["table"],
        )

    def test_existing_single_unit_legacy_sn_with_missing_old_pointer_is_fixed_only_at_census(self):
        s = inventory(quantity="1.0000", serial=True)
        s["tables"]["wm_sn"][0]["stock_id"] = None
        p = conversion_plan(s)
        after, j = converter.apply(s, p)
        self.assertEqual("901", after["tables"]["wm_sn"][0]["stock_id"])
        self.assertIsNone(j["before_snapshot"]["tables"]["wm_sn"][0]["stock_id"])

    def test_journal_records_computed_zero_net_material_balances(self):
        after, j = converter.apply(self.s, self.p)
        self.assertEqual({"quantity": "3.0000", "amount": "10.0000"}, j["balances"]["before"])
        self.assertEqual(j["balances"]["before"], j["balances"]["after"])

    def test_post_census_v2_outbound_uses_original_pool_baseline_without_false_audit_gain(self):
        from audit import audit

        after, j = converter.apply(self.s, self.p)
        current = next(r for r in after["tables"]["wm_material_stock"] if r["stock_id"] == "901")
        amount = current["total_cost"]
        current.update(stock_quantity="0.0000", total_cost="0.0000", active_sn_id=None, version="1")
        original = after["tables"]["wm_material_stock_tx"][0]
        after["tables"]["wm_material_stock_tx"].append(
            {
                **original,
                "tx_id": "502",
                "tx_flag": "-1",
                "tx_quantity": "1.0000",
                "before_quantity": "1.0000",
                "after_quantity": "0.0000",
                "tx_amount": amount,
                "material_stock_id": "901",
                "sn_id": "101",
                "sn_code": "SYNTH-001",
                "tracking_version": "2",
                "tx_date": "2026-10-19 01:00:00",
            }
        )
        report = audit(after)
        codes = {r["code"] for r in report["findings"]}
        self.assertFalse(
            codes
            & {
                "VALUATION_GROUP_MISMATCH",
                "STOCK_TX_END_BALANCE_MISMATCH",
                "QUANTITY_RECONCILIATION_MISMATCH",
                "AMOUNT_RECONCILIATION_MISMATCH",
            }
        )
        self.assertIn("CENSUS_IDENTITY_HISTORY_BOUNDARY", codes)
        with self.assertRaises(converter.CompensationRequired):
            converter.revert(after, j)

    def test_entire_material_can_convert_multiple_explicit_independent_cost_scopes(self):
        s, p = two_scopes()
        after, j = converter.apply(s, p)
        self.assertEqual({"quantity": "5.0000", "amount": "15.0000"}, j["balances"]["after"])
        self.assertEqual(2, len(after["tables"]["wm_valuation_group"]))
        second = [
            r
            for r in after["tables"]["wm_material_stock"]
            if r["valuation_group_id"] == "802" and Decimal(r["stock_quantity"]) > 0
        ]
        self.assertEqual(["2.5000", "2.5000"], [r["total_cost"] for r in second])
        self.assertTrue(all(r["location_id"] == "5" and r["vendor_id"] == "6" for r in second))

    def test_multiple_legacy_cost_sources_in_same_runtime_pool_are_not_merged(self):
        s, p = two_scopes()
        second = s["tables"]["wm_material_stock"][1]
        second.update(location_id="3", vendor_id="4")
        p["snapshot_hash"] = digest(s)
        p["sources"][1]["before_hash"] = digest(second)
        p["sources"][1]["cost_scope"]["dimensions"].update(location_id="3", vendor_id="4")
        reseal(p)
        report = converter.assess(s, p)
        self.assertFalse(report["ready"])
        self.assertEqual("COST_SCOPE_REVIEW", report["tasks"][0]["code"])

    def test_unresolved_reservation_evidence_is_not_overridden_by_zero_reserved_counter(self):
        from fixtures import complete_row

        self.s["tables"]["wm_trace_material_reservation"].append(
            complete_row(
                "wm_trace_material_reservation",
                {"tenant_id": "T1", "reservation_id": "77", "status": "RESERVED"},
            )
        )
        self.s["tables"]["wm_trace_material_reservation_line"].append(
            complete_row(
                "wm_trace_material_reservation_line",
                {
                    "tenant_id": "T1",
                    "reservation_line_id": "78",
                    "reservation_id": "77",
                    "source_stock_id": "401",
                    "quantity": "1.0000",
                    "consumed_quantity": "0.0000",
                },
            )
        )
        self.p = conversion_plan(self.s)
        self.assertEqual(
            "BUSINESS_DOCUMENT_REQUIRED", converter.assess(self.s, self.p)["tasks"][0]["code"]
        )

    def test_unknown_or_still_open_reservation_status_requires_business_resolution(self):
        from fixtures import complete_row

        for status, consumed in (
            ("PENDING", "0.0000"),
            ("CANCELLED", "0.0000"),
            ("RESERVED", "1.0000"),
        ):
            with self.subTest(status=status, consumed=consumed):
                s = copy.deepcopy(self.s)
                s["tables"]["wm_trace_material_reservation"].append(
                    complete_row(
                        "wm_trace_material_reservation",
                        {
                            "tenant_id": "T1",
                            "reservation_id": "77",
                            "material_id": "10",
                            "status": status,
                        },
                    )
                )
                s["tables"]["wm_trace_material_reservation_line"].append(
                    complete_row(
                        "wm_trace_material_reservation_line",
                        {
                            "tenant_id": "T1",
                            "reservation_line_id": "78",
                            "reservation_id": "77",
                            "source_stock_id": "401",
                            "quantity": "1.0000",
                            "consumed_quantity": consumed,
                        },
                    )
                )
                report = converter.assess(s, conversion_plan(s))
                self.assertFalse(report["ready"])
                self.assertEqual("BUSINESS_DOCUMENT_REQUIRED", report["tasks"][0]["code"])

    def test_completed_reservation_uses_actual_release_semantics_and_rejects_invalid_totals(self):
        from fixtures import complete_row

        header = complete_row(
            "wm_trace_material_reservation",
            {"tenant_id": "T1", "reservation_id": "77", "material_id": "10", "status": "CONSUMED"},
        )
        line = complete_row(
            "wm_trace_material_reservation_line",
            {
                "tenant_id": "T1",
                "reservation_line_id": "78",
                "reservation_id": "77",
                "source_stock_id": "401",
                "quantity": "1.0000",
                "consumed_quantity": "0.0000",
            },
        )
        self.s["tables"]["wm_trace_material_reservation"].append(header)
        self.s["tables"]["wm_trace_material_reservation_line"].append(line)
        self.assertTrue(converter.assess(self.s, conversion_plan(self.s))["ready"])
        for consumed in ("-1.0000", "2.0000"):
            with self.subTest(consumed=consumed):
                line["consumed_quantity"] = consumed
                report = converter.assess(self.s, conversion_plan(self.s))
                self.assertFalse(report["ready"])
                self.assertEqual("BUSINESS_DOCUMENT_REQUIRED", report["tasks"][0]["code"])

    def test_completed_reservation_parent_must_match_the_source_material(self):
        from fixtures import complete_row

        self.s["tables"]["wm_trace_material_reservation"].append(
            complete_row(
                "wm_trace_material_reservation",
                {
                    "tenant_id": "T1",
                    "reservation_id": "77",
                    "material_id": "999",
                    "status": "CONSUMED",
                },
            )
        )
        self.s["tables"]["wm_trace_material_reservation_line"].append(
            complete_row(
                "wm_trace_material_reservation_line",
                {
                    "tenant_id": "T1",
                    "reservation_line_id": "78",
                    "reservation_id": "77",
                    "source_stock_id": "401",
                    "quantity": "1.0000",
                    "consumed_quantity": "0.0000",
                },
            )
        )
        report = converter.assess(self.s, conversion_plan(self.s))
        self.assertFalse(report["ready"])
        self.assertEqual("BUSINESS_DOCUMENT_REQUIRED", report["tasks"][0]["code"])

    def test_deleted_or_unknown_batch_is_not_a_formal_current_stock_source(self):
        for managed in ("0", "1"):
            for flag in ("2", None):
                with self.subTest(del_flag=flag, batch_managed=managed):
                    s = inventory(batch=True)
                    s["tables"]["wm_batch"][0]["del_flag"] = flag
                    s["tables"]["bd_material"][0]["is_batch_manage"] = managed
                    report = converter.assess(s, conversion_plan(s))
                    self.assertFalse(report["ready"])
                    self.assertEqual("BATCH_MISMATCH", report["tasks"][0]["code"])

    def test_unknown_original_unit_cost_is_reviewed_without_repricing(self):
        self.s["tables"]["wm_material_stock"][0]["unit_cost"] = "1.000000"
        self.p = conversion_plan(self.s)
        self.assertEqual("UNIT_COST_REVIEW", converter.assess(self.s, self.p)["tasks"][0]["code"])

    def test_reverted_run_stays_a_source_for_a_fresh_approved_conversion(self):
        after, j = converter.apply(self.s, self.p)
        restored, rev = converter.revert(after, j)
        p = conversion_plan(restored)
        with self.assertRaises(ValueError):
            converter.apply(restored, p)
        p["run_id"] = "SYNTH-CONVERT-2"
        p["sources"][0]["valuation_group_id"] = "802"
        for index, t in enumerate(p["sources"][0]["targets"]):
            t["stock_id"] = str(910 + index)
        reseal(p)
        again, j2 = converter.apply(restored, p)
        self.assertEqual(
            ["REVERTED", "APPLIED"], [r["status"] for r in again["conversion_run_states"]]
        )
        self.assertEqual(1, len(again["stock_conversion_bridges"]))

    def test_cross_material_v2_history_cannot_be_hidden_outside_census_ledger_filter(self):
        self.s["tables"]["wm_material_stock_tx"][0].update(material_id="999", tracking_version="2")
        self.p = conversion_plan(self.s)
        self.p["census"]["history_boundary"]["ledger_hash"] = digest([])
        reseal(self.p)
        self.assertFalse(converter.assess(self.s, self.p)["ready"])

    def test_whitespace_is_not_physical_approval_or_a_cost_source_reference(self):
        for field in ("approved_by", "reference", "movement_freeze_reference"):
            with self.subTest(field=field):
                p = copy.deepcopy(self.p)
                p["census"][field] = "   "
                reseal(p)
                with self.assertRaises(ValueError):
                    converter.apply(self.s, p)
        p = copy.deepcopy(self.p)
        p["sources"][0]["cost_scope"]["source_reference"] = "   "
        reseal(p)
        with self.assertRaises(ValueError):
            converter.apply(self.s, p)

    def test_blank_registered_identity_code_cannot_be_used_as_real_census_sn(self):
        self.s["tables"]["wm_sn"][0]["sn_code"] = "   "
        self.p = conversion_plan(self.s)
        with self.assertRaises(ValueError):
            converter.apply(self.s, self.p)

    def test_stock_unit_must_match_material_base_unit_without_implicit_conversion(self):
        self.s["tables"]["wm_material_stock"][0]["base_uom_id"] = "2"
        self.p = conversion_plan(self.s)
        self.assertFalse(converter.assess(self.s, self.p)["ready"])
