"""新增来源账只能参与只读基线，不能把缺失快照或新业务当作无变化。"""
import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import repair
from core import COLUMNS, KEYS, REQUIRED_TABLES, WRITABLE, digest, row_key, validate_snapshot
from fixtures import snapshot, plan, complete_row
from sql_writer import emit

SOURCES = {
    "wm_barcode": ("primary_object_type", "SN"),
    "wm_work_order_policy_adoption": ("status", "APPLIED"),
    "wm_work_order_tracking_policy": ("policy_version", "99"),
    "mes_label_print_task": ("status", "SENDING"),
    "pro_completion_material_allocation": ("quantity", "0.5000"),
    "pro_product_lot_output": ("quantity", "0.5000"),
    "wm_trace_material_consumption": ("quantity", "0.5000"),
    "wm_trace_material_reservation": ("status", "CONSUMED"),
    "wm_trace_material_reservation_line": ("consumed_quantity", "1.0000"),
    "wm_serial_reservation": ("status", "REGISTERED"),
    "wm_serial_sequence": ("current_value", "2"),
    "wm_serial_lifecycle_command": ("reason", "CHANGED-RETIREMENT-REASON"),
    "wm_trace_outbox": ("status", "APPLIED"),
    "wm_trace_inbox": ("status", "APPLIED"),
    "mes_trace_outbox": ("status", "APPLIED"),
    "mes_trace_inbox": ("status", "APPLIED"),
    "pro_completion_manifest": ("status", "CANCEL_REQUESTED"),
}


def source_row(table):
    # 每行包含全部白名单列；所有数据为合成值，不表示真实业务授权。
    row = {}
    for name, kind in COLUMNS[table].items():
        if kind.startswith("decimal"): value = "1.0000"
        elif kind.startswith(("bigint", "int", "tinyint")): value = "1"
        elif kind == "char(64)": value = "a" * 64
        elif kind == "char(1)": value = "0"
        elif kind.startswith("datetime"): value = "2026-10-18 00:00:00.000" if kind == "datetime(3)" else "2026-10-18 00:00:00"
        elif kind == "date": value = "2026-10-18"
        elif kind in ("longtext", "text"): value = "{}"
        else: value = "SYNTH"
        row[name] = value
    row["tenant_id"] = "T1"
    row[SOURCES[table][0]] = "0.0000" if table == "wm_trace_material_reservation_line" else "1.0000" if SOURCES[table][0] == "quantity" else "1" if table == "wm_serial_sequence" else "PENDING" if table == "mes_label_print_task" else "RESERVED"
    if table == "mes_label_print_task":
        row["sn_id"] = "201"; row["print_source"] = "AUTO"; row["attempt_count"] = "0"
    if table.endswith(("trace_inbox","trace_outbox")):row["status"]="NEW" if table.endswith("outbox") else "WAITING"
    if table == "pro_completion_manifest":row["status"]="FROZEN"
    if table == "wm_serial_lifecycle_command":
        row.update(action="RETIRE_UNUSED_MANUAL",identity_id="101",reason="SYNTH-RETIREMENT",before_version="1",after_version="2",before_status="REGISTERED",after_status="RETIRED",reservation_status="CANCELLED",source_type="ERP_MANUAL",source_id=None)
    if table == "wm_work_order_tracking_policy":
        row.update(erp_wo_id="1",material_id="10",policy_version="1",snapshot_version="1",provenance="SOURCE_POLICY_ADOPTION",evidence_ref="synthetic://original-policy/1",actor_id="7")
    if table == "wm_work_order_policy_adoption":row.update(status="APPROVED",version="1",proposed_by="6",approved_by="7",applied_at=None,snapshot_hash=None)
    if table == "wm_barcode":row.update(primary_object_type=None,barcode_id="1",material_id="10",sn_id=None,stock_id="401")
    if "erp_batch_id" in row: row["erp_batch_id"] = None
    return row


def plan_for_datasource(s, ds):
    if ds == "mes": return plan(s)
    return plan(s, table="wm_material_stock", key={"tenant_id":"T1", "stock_id":"401"}, changes={"version":"2"})


class SourceSafetyTests(unittest.TestCase):
    def test_every_source_table_is_required_readable_and_never_repair_writable(self):
        for table in SOURCES:
            with self.subTest(table=table):
                self.assertIn(table, REQUIRED_TABLES)
                self.assertIn(table, KEYS)
                self.assertIn(table, COLUMNS)
                self.assertNotIn(table, WRITABLE)

    def test_missing_any_source_export_blocks_repair_instead_of_assuming_empty(self):
        for table in SOURCES:
            with self.subTest(table=table):
                s = snapshot()
                s["tables"].pop(table, None)
                s["complete_tables"] = [name for name in s["complete_tables"] if name != table]
                p = plan(s)
                with self.assertRaisesRegex(ValueError, "完整"):
                    repair.apply(s, p)

    def test_source_table_must_be_explicitly_declared_complete_even_when_empty(self):
        for table in SOURCES:
            with self.subTest(table=table):
                s = snapshot(); s["complete_tables"] = [name for name in s["complete_tables"] if name != table]
                p = plan(s)
                with self.assertRaisesRegex(ValueError, "完整"):
                    repair.apply(s, p)

    def test_any_new_task_consumption_reservation_or_sequence_requires_compensation(self):
        for table in SOURCES:
            with self.subTest(table=table):
                s = snapshot(); p = plan(s); after, journal = repair.apply(s, p)
                after["tables"].setdefault(table, []).append(source_row(table))
                with self.assertRaisesRegex(ValueError, "COMPENSATION_REQUIRED"):
                    repair.revert(after, journal)
                with self.assertRaisesRegex(ValueError, "COMPENSATION_REQUIRED"):
                    emit(after, journal, target="mes:fixture_mes", backup_reference="synthetic-backup", allow_db_writes=True, mode="revert")
                self.assertEqual("101", after["tables"]["pro_sn"][0]["identity_id"])

    def test_same_row_count_source_state_change_also_requires_compensation(self):
        for table, (column, changed) in SOURCES.items():
            with self.subTest(table=table):
                s = snapshot(); s["tables"].setdefault(table, []).append(source_row(table)); p = plan(s)
                after, journal = repair.apply(s, p)
                after["tables"][table][0][column] = changed
                with self.assertRaisesRegex(ValueError, "COMPENSATION_REQUIRED"):
                    repair.revert(after, journal)

    def test_new_cancellation_ack_in_existing_applied_event_blocks_old_revert(self):
        for table in ("wm_trace_outbox","wm_trace_inbox","mes_trace_outbox","mes_trace_inbox"):
            with self.subTest(table=table):
                baseline=snapshot();row=source_row(table);row["status"]="APPLIED"
                baseline["tables"][table].append(row)
                ds="mes" if table.startswith("mes_") else "erp"
                approved=plan_for_datasource(baseline,ds);after,journal=repair.apply(baseline,approved)
                after["tables"][table][0]["result_payload"]='{"fixture":"changed-cancellation-ack"}'
                self.assertEqual("APPLIED",after["tables"][table][0]["status"])
                with self.assertRaisesRegex(ValueError,"COMPENSATION_REQUIRED"):repair.revert(after,journal)
                with self.assertRaisesRegex(ValueError,"COMPENSATION_REQUIRED"):
                    emit(after,journal,target=ds+":fixture_"+ds,backup_reference="synthetic-backup",allow_db_writes=True,mode="revert")

    def test_source_projection_may_not_omit_status_lease_or_quantity_columns(self):
        for table, (column, _) in SOURCES.items():
            with self.subTest(table=table):
                s = snapshot(); row = source_row(table); row.pop(column)
                s["tables"].setdefault(table, []).append(row); p = plan(s)
                with self.assertRaisesRegex(ValueError, "全部列|投影|完整"):
                    repair.apply(s, p)

    def test_source_records_cannot_be_targeted_for_automatic_repair(self):
        for table, (column, changed) in SOURCES.items():
            with self.subTest(table=table):
                s = snapshot(); row = source_row(table); s["tables"].setdefault(table, []).append(row)
                p = plan(s, table=table, key=row_key(table, row), changes={column: changed})
                with self.assertRaisesRegex(ValueError, "修复白名单"):
                    repair.apply(s, p)

    def test_sql_baseline_locks_empty_sources_on_both_datasources(self):
        for ds in ("erp", "mes"):
            s = snapshot(); p = plan_for_datasource(s, ds); after, journal = repair.apply(s, p)
            for mode, baseline in (("apply", s), ("revert", after)):
                sql = emit(baseline, journal, target=ds+":fixture_"+ds, backup_reference="synthetic-backup", allow_db_writes=True, mode=mode)
                for table in SOURCES:
                    if ("mes" if table.startswith(("pro_", "mes_")) else "erp") != ds: continue
                    with self.subTest(table=table, mode=mode):
                        self.assertIn("SELECT COUNT(*) INTO v_count FROM `"+table+"` WHERE tenant_id IN (", sql)
                        self.assertNotIn("UPDATE `"+table+"`", sql)
                        self.assertNotIn("INSERT INTO `"+table+"`", sql)

    def test_sql_baseline_hashes_existing_source_full_state(self):
        s = snapshot()
        for table in SOURCES: s["tables"].setdefault(table, []).append(source_row(table))
        for table, (column, _) in SOURCES.items():
            ds = "mes" if table.startswith(("pro_", "mes_")) else "erp"
            baseline = copy.deepcopy(s); p = plan_for_datasource(baseline, ds); after, journal = repair.apply(baseline, p)
            with self.subTest(table=table):
                sql = emit(after, journal, target=ds+":fixture_"+ds, backup_reference="synthetic-backup", allow_db_writes=True, mode="revert")
                line = next(line for line in sql.splitlines() if " INTO v_hash FROM `"+table+"`" in line)
                for field in COLUMNS[table]: self.assertIn("`"+field+"`", line)
                self.assertIn("FOR UPDATE", line)

    def test_legacy_journal_without_new_sources_cannot_be_silently_upgraded(self):
        s = snapshot(); p = plan(s); after, journal = repair.apply(s, p)
        old_before, old_after = copy.deepcopy(s), copy.deepcopy(after)
        for candidate in (old_before, old_after):
            for table in SOURCES: candidate["tables"].pop(table, None)
            candidate["complete_tables"] = [name for name in candidate["complete_tables"] if name not in SOURCES]
        old = copy.deepcopy(journal)
        old["plan"]["snapshot_hash"] = digest(old_before)
        old["plan_hash"] = digest(old["plan"])
        old["before_snapshot_hash"] = digest(old_before); old["after_snapshot_hash"] = digest(old_after)
        old["journal_hash"] = digest({k: v for k, v in old.items() if k != "journal_hash"})
        with self.assertRaisesRegex(ValueError, "完整"):
            repair.revert(old_after, old)
        with self.assertRaisesRegex(ValueError, "COMPENSATION_REQUIRED"):
            repair.revert(after, old)

    def test_example_artifacts_hold_complete_source_baseline(self):
        from core import load
        root = Path(__file__).resolve().parents[1] / "examples"
        baseline = load(root / "synthetic-snapshot.json")
        approved = load(root / "synthetic-plan.json")
        after, journal = repair.apply(baseline, approved)
        restored, _ = repair.revert(after, journal)
        self.assertEqual(baseline, restored)
        for table in SOURCES:
            self.assertIn(table, baseline["complete_tables"])
            self.assertIn(table, baseline["tables"])

    def test_actual_consumption_may_not_hide_source_allocation_field(self):
        self.assertIn("source_allocation_id", COLUMNS["lt_wo_consumption"])
        baseline = snapshot()
        row = {name: None for name in COLUMNS["lt_wo_consumption"]}
        row.update(tenant_id="T1", consumption_id="1")
        baseline["tables"]["lt_wo_consumption"] = [row]
        validate_snapshot(baseline)
        row.pop("source_allocation_id")
        with self.assertRaisesRegex(ValueError, "全部列|投影|完整"):
            validate_snapshot(baseline)

    def test_actual_consumption_source_allocation_change_prevents_old_journal_revert(self):
        baseline = snapshot()
        baseline["tables"]["lt_wo_consumption"] = [complete_row("lt_wo_consumption", {
            "tenant_id":"T1", "consumption_id":"801", "wo_id":"601", "lot_id":"701",
            "material_id":"10", "consumed_qty":"1.0000", "consumed_time":"2026-10-18 00:00:00",
            "source_allocation_id":None, "del_flag":"0"
        })]
        approved = plan(baseline); after, journal = repair.apply(baseline, approved)
        sql = emit(after, journal, target="mes:fixture_mes", backup_reference="synthetic-backup", allow_db_writes=True, mode="revert")
        line = next(line for line in sql.splitlines() if " INTO v_hash FROM `lt_wo_consumption`" in line)
        self.assertIn("`source_allocation_id`", line)
        after["tables"]["lt_wo_consumption"][0]["source_allocation_id"] = "901"
        with self.assertRaisesRegex(ValueError, "COMPENSATION_REQUIRED"):
            repair.revert(after, journal)
