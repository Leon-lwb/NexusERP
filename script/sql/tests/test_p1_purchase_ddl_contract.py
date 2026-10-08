"""静态契约检查；绝不连接 MySQL 或执行迁移。"""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
ERP_MAIN = ROOT / "script/sql/ry-erp.sql"
SYSTEM_MAIN = ROOT / "script/sql/ry-vue.sql"

VENDOR_COLUMNS = (
    "order_uom_id", "verified_moq_qty", "moq_uom_id", "order_multiple", "allow_split",
    "units_per_pack", "terms_version", "terms_effective_at", "terms_expires_at", "terms_verified_at",
)
UOM_COLUMNS = ("quantity_scale", "conversion_version", "conversion_verified_at")


def read(path):
    return path.read_text(encoding="utf-8-sig")


def executable_sql(sql):
    """检查新装 SQL 前，先移除注释。"""
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
    def test_purchase_and_decision_contracts_belong_to_their_install_schema(self):
        erp, system = read(ERP_MAIN), read(SYSTEM_MAIN)
        owned = ((erp, system, ("pu_requirement_material_guard", "pu_requirement_allocation_ledger",
                                "pu_requirement_allocation_event")),
                 (system, erp, ("ai_decision_run", "ai_decision_action")))
        for main, other, tables in owned:
            for name in tables:
                with self.subTest(table=name):
                    create_table(main, name)
                    engine = re.search(rf"(?s)CREATE TABLE `{name}`\s*\(.*?\) ENGINE\s*=\s*(\w+)", main)
                    self.assertEqual("InnoDB", engine.group(1))
                    self.assertNotRegex(other, rf"CREATE TABLE `{name}`")
                    self.assertNotRegex(executable_sql(main), rf"(?i)\b(?:INSERT|REPLACE)\s+INTO\s+`{name}`")

    def test_system_contract_is_nullable_and_never_certifies_unknown_runs(self):
        main = read(SYSTEM_MAIN)
        for name, column in (("ai_decision_run", "decision_contract_version"),
                             ("ai_decision_action", "purchase_payload_version")):
            definition = create_table(main, name)
            self.assertRegex(definition, rf"`{column}`\s+tinyint\(4\)\s+NULL\s+DEFAULT\s+NULL")
            self.assertNotRegex(definition, r"(?i)\bDEFAULT\s+2\b")

    def test_vendor_and_uom_terms_are_unknown_for_existing_and_seed_rows(self):
        main = read(ERP_MAIN)
        for name, columns in (("bd_material_vendor", VENDOR_COLUMNS),
                              ("bd_unit_of_measure", UOM_COLUMNS)):
            definition = create_table(main, name)
            for col in columns:
                with self.subTest(table=name, column=col):
                    self.assertRegex(definition, rf"`{col}`\s+[^\n]+\bNULL\s+DEFAULT\s+NULL\b")
            seeds = re.findall(rf"(?m)^INSERT INTO `{name}` \(([^)]*)\) VALUES \((.*)\);", main)
            self.assertGreater(len(seeds), 0)
            self.assertEqual(len(seeds), len(re.findall(rf"(?m)^INSERT INTO `{name}`", main)))
            for column_list, values in seeds:
                self.assertEqual(len(column_list.split(", ")), value_count(values))
                self.assertFalse(any(f"`{col}`" in column_list for col in columns))

    def test_guard_and_detail_to_line_ledger_with_snowflake_ids(self):
        main = read(ERP_MAIN)
        guard = create_table(main, "pu_requirement_material_guard")
        self.assertRegex(guard, r"PRIMARY KEY \(`tenant_id`, `material_id`\)")
        self.assertRegex(guard, r"`tenant_id` varchar\(20\)[^\n]+NOT NULL")
        ledger = create_table(main, "pu_requirement_allocation_ledger")
        for col in ("allocation_id", "tenant_id", "material_id", "source_type", "detail_id",
                    "requisition_line_id", "order_line_id", "base_uom_id", "occupied_base_qty",
                    "allocation_status", "allocation_key", "version", "released_at"):
            self.assertIn(f"`{col}`", ledger)
        self.assertRegex(ledger, r"`allocation_id` bigint\(20\) NOT NULL.*雪花ID")
        self.assertRegex(ledger, r"`tenant_id` varchar\(20\)[^\n]+NOT NULL")
        self.assertRegex(ledger, r"UNIQUE INDEX `uk_pral_tenant_key`\(`tenant_id`, `allocation_key`\)")
        self.assertRegex(ledger, r"UNIQUE INDEX `uk_pral_tenant_req_detail_no`\(`tenant_id`, `requisition_line_id`, `detail_id`, `allocation_no`\)")
        self.assertRegex(ledger, r"INDEX `idx_pral_tenant_detail`\(`tenant_id`, `detail_id`\)")
        self.assertRegex(ledger, r"INDEX `idx_pral_tenant_req_line`\(`tenant_id`, `requisition_line_id`\)")
        self.assertRegex(ledger, r"INDEX `idx_pral_tenant_order_line`\(`tenant_id`, `order_line_id`\)")
        for col in ("covered_demand_base_qty", "occupied_base_qty", "fulfilled_base_qty", "released_base_qty"):
            self.assertRegex(ledger, rf"`{col}` decimal\(18, 4\) NOT NULL")
        events = create_table(main, "pu_requirement_allocation_event")
        self.assertIn("`allocation_id` bigint(20) NOT NULL", events)
        self.assertIn("`event_id` bigint(20) NOT NULL", events)
        self.assertIn("`event_sequence` int(11) NOT NULL", events)
        self.assertRegex(events, r"`tenant_id` varchar\(20\)[^\n]+NOT NULL")
        self.assertRegex(events, r"UNIQUE INDEX `uk_prae_tenant_event_key`\(`tenant_id`, `event_key`\)")
        self.assertRegex(events, r"UNIQUE INDEX `uk_prae_tenant_allocation_sequence`\(`tenant_id`, `allocation_id`, `event_sequence`\)")
        for col in ("occupied_base_before", "occupied_base_after"):
            self.assertIn(f"`{col}` decimal(18, 4) NOT NULL", events)

    def test_vendor_and_uom_column_types_preserve_current_purchase_contract(self):
        main = read(ERP_MAIN)
        vendor_types = {
            "order_uom_id": "bigint(20)", "verified_moq_qty": "decimal(18, 4)",
            "moq_uom_id": "bigint(20)", "order_multiple": "decimal(18, 4)",
            "allow_split": "tinyint(1)", "units_per_pack": "decimal(18, 4)",
            "terms_version": "varchar(64)", "terms_effective_at": "datetime",
            "terms_expires_at": "datetime", "terms_verified_at": "datetime",
        }
        uom_types = {"quantity_scale": "tinyint(3) unsigned", "conversion_version": "varchar(64)",
                     "conversion_verified_at": "datetime"}
        self.assertEqual(set(VENDOR_COLUMNS), set(vendor_types))
        self.assertEqual(set(UOM_COLUMNS), set(uom_types))
        for name, types in (("bd_material_vendor", vendor_types), ("bd_unit_of_measure", uom_types)):
            definition = create_table(main, name)
            for col, kind in types.items():
                with self.subTest(table=name, column=col):
                    self.assertIn(f"`{col}` {kind} NULL DEFAULT NULL", definition)
        self.assertRegex(create_table(main, "bd_material_vendor"),
                         r"UNIQUE INDEX `uk_material_supplier`\(`material_id`, `vendor_id`, `tenant_id`\)")
        self.assertRegex(create_table(main, "bd_unit_of_measure"),
                         r"UNIQUE INDEX `uk_uom_code`\(`uom_code`, `tenant_id`\)")

    def test_legacy_source_id_definition_unchanged(self):
        line = create_table(read(ERP_MAIN), "pu_purchase_requisition_line")
        self.assertIn("`source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID'", line)


if __name__ == "__main__":
    unittest.main()