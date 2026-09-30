"""静态契约检查；绝不连接 MySQL 或执行迁移。"""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
ERP_MAIN = ROOT / "script/sql/ry-erp.sql"
SYSTEM_MAIN = ROOT / "script/sql/ry-vue.sql"
ERP_DELTA = ROOT / "script/sql/update/2026-09-30-p1-purchase-lineage-ry-erp.sql"
SYSTEM_DELTA = ROOT / "script/sql/update/2026-09-30-p1-decision-contract-ry-vue.sql"

VENDOR_COLUMNS = (
    "order_uom_id", "verified_moq_qty", "moq_uom_id", "order_multiple", "allow_split",
    "units_per_pack", "terms_version", "terms_effective_at", "terms_expires_at", "terms_verified_at",
)
UOM_COLUMNS = ("quantity_scale", "conversion_version", "conversion_verified_at")


def read(path):
    return path.read_text(encoding="utf-8-sig")


def executable_sql(sql):
    """检查可执行迁移语句前，先移除注释。"""
    sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.S)
    return re.sub(r"(?m)^\s*--[^\n]*", "", sql)


def create_table(sql, name):
    match = re.search(r"CREATE TABLE `" + re.escape(name) + r"`\s*\((.*?)\) ENGINE\s*=", sql, re.S)
    if not match:
        raise AssertionError(f"Missing CREATE TABLE {name}")
    return match.group(1)


def value_count(values):
    """统计 SQL 种子值数量，忽略单引号字符串内的逗号。"""
    quoted = False
    commas = 0
    index = 0
    while index < len(values):
        char = values[index]
        if char == "'":
            if quoted and index + 1 < len(values) and values[index + 1] == "'":
                index += 1
            else:
                quoted = not quoted
        elif char == "," and not quoted:
            commas += 1
        index += 1
    if quoted:
        raise AssertionError("Unterminated SQL seed string")
    return commas + 1

class P1PurchaseDdlContract(unittest.TestCase):
    def test_one_time_deltas_only_add_schema_and_select_database_explicitly(self):
        for path, database in ((SYSTEM_DELTA, "ry-vue"), (ERP_DELTA, "ry-erp")):
            with self.subTest(path=path.name):
                text = read(path)
                sql = executable_sql(text)
                self.assertRegex(sql, rf"(?i)\bUSE\s+`{database}`\s*;")
                self.assertNotRegex(sql, r"(?i)\b(?:DROP|TRUNCATE|DELETE|UPDATE|INSERT|REPLACE)\b")
                self.assertNotRegex(sql, r"(?i)\bIF\s+NOT\s+EXISTS\b")
                statements = [s.strip() for s in sql.split(";") if s.strip()]
                self.assertTrue(statements[0] == f"USE `{database}`", statements[:1])
                self.assertTrue(all(re.match(r"(?i)^(?:ALTER TABLE|CREATE TABLE)\b", s)
                                    for s in statements[1:]), statements)
                self.assertIn("information_schema.COLUMNS", text)
                self.assertIn("information_schema.TABLES", text)
                self.assertIn("information_schema.STATISTICS", text)
                self.assertIn("不可重放", text)
                self.assertNotIn("information_schema.", sql)
                self.assertEqual(len(re.findall(r"(?i)\bUSE\s+`", sql)), 1)
                self.assertNotRegex(sql, r"(?i)\b(?:ry-vue|ry-erp)\b\s*\.\s*`")

    def test_system_contract_is_nullable_and_never_upgrades_old_runs(self):
        main, delta = read(SYSTEM_MAIN), executable_sql(read(SYSTEM_DELTA))
        for table, column in (("ai_decision_run", "decision_contract_version"),
                              ("ai_decision_action", "purchase_payload_version")):
            for ddl in (create_table(main, table), delta):
                self.assertRegex(ddl, rf"`{column}`\s+tinyint\(4\)\s+NULL\s+DEFAULT\s+NULL")
            self.assertRegex(delta, rf"ALTER TABLE `{table}`")
            self.assertNotRegex(create_table(main, table), r"(?i)\bDEFAULT\s+2\b")
        self.assertNotIn("ai_decision_", executable_sql(read(ERP_DELTA)))

    def test_vendor_and_uom_terms_are_unknown_for_existing_and_seed_rows(self):
        main, delta = read(ERP_MAIN), executable_sql(read(ERP_DELTA))
        for table, columns in (("bd_material_vendor", VENDOR_COLUMNS),
                               ("bd_unit_of_measure", UOM_COLUMNS)):
            definition = create_table(main, table)
            for col in columns:
                with self.subTest(table=table, column=col):
                    self.assertRegex(definition, rf"`{col}`\s+[^\n]+\bNULL\s+DEFAULT\s+NULL\b")
                    self.assertRegex(delta, rf"ADD COLUMN `{col}`\s+[^\n]+\bNULL\s+DEFAULT\s+NULL\b")
            seeds = re.findall(rf"(?m)^INSERT INTO `{table}` \(([^)]*)\) VALUES \((.*)\);", main)
            self.assertGreater(len(seeds), 0)
            self.assertEqual(len(seeds), len(re.findall(rf"(?m)^INSERT INTO `{table}`", main)))
            for column_list, values in seeds:
                self.assertEqual(len(column_list.split(", ")), value_count(values))
                self.assertFalse(any(f"`{col}`" in column_list for col in columns))
        self.assertNotIn("bd_material_vendor", executable_sql(read(SYSTEM_DELTA)))

    def test_guard_and_detail_to_line_ledger_with_snowflake_ids(self):
        main, delta = read(ERP_MAIN), executable_sql(read(ERP_DELTA))
        guard = create_table(main, "pu_requirement_material_guard")
        self.assertRegex(guard, r"PRIMARY KEY \(`tenant_id`, `material_id`\)")
        self.assertRegex(guard, r"`tenant_id` varchar\(20\)[^\n]+NOT NULL")
        ledger = create_table(main, "pu_requirement_allocation_ledger")
        for col in ("allocation_id", "tenant_id", "material_id", "source_type", "detail_id",
                    "requisition_line_id", "order_line_id", "base_uom_id", "occupied_base_qty",
                    "allocation_status", "allocation_key", "version", "released_at"):
            self.assertIn(f"`{col}`", ledger)
        self.assertRegex(ledger, r"`allocation_id` bigint\(20\) NOT NULL.*雪花ID")
        self.assertRegex(ledger, r"UNIQUE INDEX `uk_pral_tenant_key`\(`tenant_id`, `allocation_key`\)")
        self.assertRegex(ledger, r"INDEX `idx_pral_tenant_detail`\(`tenant_id`, `detail_id`\)")
        self.assertRegex(ledger, r"INDEX `idx_pral_tenant_req_line`\(`tenant_id`, `requisition_line_id`\)")
        self.assertRegex(ledger, r"INDEX `idx_pral_tenant_order_line`\(`tenant_id`, `order_line_id`\)")
        events = create_table(main, "pu_requirement_allocation_event")
        self.assertIn("`allocation_id`", events)
        self.assertIn("`event_id` bigint(20) NOT NULL", events)
        for table in ("pu_requirement_material_guard", "pu_requirement_allocation_ledger",
                      "pu_requirement_allocation_event"):
            self.assertRegex(delta, rf"CREATE TABLE `{table}`")
        self.assertNotIn("pu_requirement_material_guard", executable_sql(read(SYSTEM_DELTA)))

    def test_new_tables_and_added_column_types_match_fresh_install(self):
        main, delta = read(ERP_MAIN), executable_sql(read(ERP_DELTA))
        for table in ("pu_requirement_material_guard", "pu_requirement_allocation_ledger",
                      "pu_requirement_allocation_event"):
            with self.subTest(table=table):
                self.assertEqual(create_table(main, table), create_table(delta, table))
        for table, columns in (("bd_material_vendor", VENDOR_COLUMNS),
                               ("bd_unit_of_measure", UOM_COLUMNS)):
            definition = create_table(main, table)
            for col in columns:
                with self.subTest(table=table, column=col):
                    added = re.search(rf"ADD COLUMN (`{col}` [^\n]+?) AFTER `[^`]+`", delta)
                    installed = re.search(rf"(?m)^  (`{col}` [^\n]+)", definition)
                    self.assertIsNotNone(added)
                    self.assertIsNotNone(installed)
                    self.assertEqual(added.group(1), installed.group(1).rstrip(","))

    def test_legacy_source_id_definition_unchanged(self):
        main, delta = read(ERP_MAIN), executable_sql(read(ERP_DELTA))
        line = create_table(main, "pu_purchase_requisition_line")
        self.assertIn("`source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID'", line)
        self.assertNotRegex(delta, r"(?i)\bALTER\s+TABLE\s+`pu_purchase_requisition_line`")
        self.assertNotIn("`source_id`", delta)


if __name__ == "__main__":
    unittest.main()