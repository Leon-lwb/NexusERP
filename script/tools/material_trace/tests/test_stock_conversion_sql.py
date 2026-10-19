import copy
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from conversion_fixtures import inventory, conversion_plan
import stock_converter as converter

try:
    import stock_conversion_sql as writer
except ModuleNotFoundError:
    writer = None


class StockConversionSqlTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(writer, "需要独立可审单库SQL生成器")
        self.s = inventory(batch=True)
        self.p = conversion_plan(self.s)
        self.after, self.j = converter.apply(self.s, self.p)

    def emit(self, mode="apply", snapshot=None, **extra):
        options = dict(
            target="erp:fixture_erp",
            backup_reference="synthetic-backup",
            allow_db_writes=True,
            mode=mode,
        )
        options.update(extra)
        return writer.emit(
            snapshot or (self.s if mode == "apply" else self.after), self.j, **options
        )

    def test_explicit_target_server_uuid_backup_and_full_baseline_are_mandatory(self):
        for options in (
            {},
            {"target": "mes:fixture_mes", "backup_reference": "x", "allow_db_writes": True},
            {"target": "erp:fixture_erp", "backup_reference": "", "allow_db_writes": True},
        ):
            with self.assertRaises(ValueError):
                writer.emit(self.s, self.j, **options)
        for mode in ("apply", "revert"):
            sql = self.emit(mode)
            for token in (
                "@@server_uuid",
                "SERIALIZABLE",
                "FOR UPDATE",
                "SHA2(",
                "ROLLBACK",
                "SIGNAL SQLSTATE",
                "wm_trace_stock_conversion_run",
                "journal_json",
                "conversion_run_states",
            ):
                self.assertIn(token, sql)
            for table in (
                "wm_serial_sequence",
                "wm_serial_reservation",
                "wm_trace_material_consumption",
            ):
                self.assertIn("SELECT COUNT(*) INTO v_count FROM `" + table + "`", sql)

    def test_apply_clears_legacy_before_creating_positive_sn_rows_and_preserves_ledger(self):
        sql = self.emit()
        self.assertLess(
            sql.index("UPDATE `wm_material_stock`"), sql.index("INSERT INTO `wm_material_stock`")
        )
        self.assertIn("INSERT INTO `wm_valuation_group`", sql)
        self.assertIn("UPDATE `wm_sn`", sql)
        self.assertIn("UPDATE `bd_material`", sql)
        self.assertNotRegex(sql, r"(?i)(INSERT INTO|UPDATE|DELETE FROM) `wm_material_stock_tx`")
        self.assertNotRegex(sql, r"(?i)(INSERT INTO|UPDATE) `(?:gl_|fin_)")
        insert = next(
            line for line in sql.splitlines() if "INSERT INTO `wm_material_stock`" in line
        )
        self.assertNotIn("`active_sn_id`", insert)

    def test_revert_only_removes_own_new_rows_and_keeps_conversion_audit(self):
        sql = self.emit("revert")
        self.assertIn("DELETE FROM `wm_material_stock`", sql)
        self.assertIn("DELETE FROM `wm_valuation_group`", sql)
        self.assertLess(
            sql.index("DELETE FROM `wm_material_stock`"), sql.index("UPDATE `wm_material_stock`")
        )
        self.assertIn("status='REVERTED'", sql)
        self.assertNotIn("DELETE FROM `wm_trace_stock_conversion_run`", sql)
        self.assertNotRegex(sql, r"(?i)UPDATE `wm_trace_stock_conversion_run` SET journal_json")

    def test_new_business_never_generates_restore_sql(self):
        changed = copy.deepcopy(self.after)
        changed["tables"]["wm_material_stock_tx"].append(
            {**changed["tables"]["wm_material_stock_tx"][0], "tx_id": "502"}
        )
        with self.assertRaises(converter.CompensationRequired):
            self.emit("revert", changed)

    def test_resealed_journal_cannot_add_arbitrary_sql_mutations(self):
        j = copy.deepcopy(self.j)
        j["items"][0]["table"] = "wm_material_stock_tx"
        from core import digest

        j["journal_hash"] = digest({k: v for k, v in j.items() if k != "journal_hash"})
        with self.assertRaises(ValueError):
            writer.emit(
                self.s,
                j,
                target="erp:fixture_erp",
                backup_reference="synthetic-backup",
                allow_db_writes=True,
            )
