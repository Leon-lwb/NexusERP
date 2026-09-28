"""本机 plus 一次性迁移 SQL 的离线契约，不连接数据库或读取秘密。"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SQL = Path(__file__).with_name("2026-09-28-plus工业决策本地增量.sql")


def definition(schema, table):
    """从已审查的初始化主脚本读取精确表定义。"""
    text = (ROOT / "script/sql" / (schema + ".sql")).read_text(encoding="utf-8")
    return re.search(r"CREATE TABLE `" + table + r"`\s*\(.*?\) ENGINE[^;]*;", text, re.S).group()


class LocalPlusMigrationTest(unittest.TestCase):
    """只允许已授权的结构与基础菜单/字典新增。"""

    @classmethod
    def setUpClass(cls):
        cls.sql = SQL.read_text(encoding="utf-8")

    def test_session_guard_precedes_business_ddl_and_cannot_silently_skip(self):
        first = self.sql.index("CREATE TABLE `ry-vue`.`ai_decision_run`")
        guard = self.sql[:first]
        self.assertIn("@@port = 3306", guard)
        self.assertIn("GET_LOCK(", guard)
        self.assertIn("PRIMARY KEY", guard)
        self.assertIn("IFNULL(NOT (", guard)
        for key in ("TARGET", "SCHEMA_BASE", "MENU_BASE", "DICT_BASE"):
            self.assertGreaterEqual(guard.count("'" + key + "'"), 2)
        self.assertIn("lock_wait_timeout = 15", guard)

    def test_three_tables_exactly_match_main_definitions_without_drop(self):
        self.assertNotRegex(self.sql, r"(?i)\bDROP\s+(?:DATABASE|TABLE)\b")
        self.assertNotRegex(self.sql, r"(?im)^\s*(?:DELETE|UPDATE|REPLACE|TRUNCATE)\b")
        tables = ("ai_decision_run", "ai_decision_evidence", "ai_decision_action")
        for table in tables:
            expected = definition("ry-vue", table).replace("CREATE TABLE `" + table + "`", "CREATE TABLE `ry-vue`.`" + table + "`", 1)
            self.assertIn(expected, self.sql)
        self.assertEqual(3, len(re.findall(r"CREATE TABLE `ry-vue`\.`ai_decision_", self.sql)))

    def test_erp_columns_and_indexes_are_not_lost(self):
        names = {
            "prd_schedule_plan": ("decision_run_id", "decision_action_id", "decision_idempotency_key", "decision_scenario_code", "decision_work_order_ids_json", "version"),
            "pu_purchase_requisition": ("decision_run_id", "decision_action_id", "decision_idempotency_key", "version"),
        }
        for table, keys in names.items():
            body = re.search(r"ALTER TABLE `ry-erp`\.`" + table + r"`(.*?);", self.sql, re.S).group(1)
            main = definition("ry-erp", table)
            for key in keys:
                line = next(line.strip().rstrip(",") for line in main.splitlines() if line.strip().startswith("`" + key + "`"))
                self.assertIn("ADD COLUMN " + line, body)
            for line in main.splitlines():
                index = line.strip().rstrip(",")
                if re.match(r"(?:UNIQUE )?INDEX `", index) and "decision_" in index:
                    self.assertIn("ADD " + index, body)
        for index in ("uk_psp_decision_idem", "idx_psp_decision_run", "uk_pur_decision_idem", "idx_pur_decision_run"):
            self.assertEqual(1, len(re.findall(r"ADD (?:UNIQUE )?INDEX `" + index + "`", self.sql)))

    def test_only_capacity_expands_and_no_department_or_business_rewrite(self):
        pairs = (("ry-vue", "ai_flow_node_run", "node_type"), ("ry-erp", "pu_purchase_requisition_line", "material_name"),
                 ("ry-erp", "pu_purchase_requisition_line", "material_spec"), ("ry-erp", "pu_purchase_requisition_line", "unit"))
        for schema, table, key in pairs:
            line = next(line.strip().rstrip(",") for line in definition(schema, table).splitlines() if line.strip().startswith("`" + key + "`"))
            self.assertIn("MODIFY COLUMN " + line, self.sql)
        self.assertNotIn("ADD COLUMN `dept_id`", self.sql)
        self.assertNotIn("ADD COLUMN `dept_name`", self.sql)
        self.assertNotIn("nexus_ai_decision_demo", self.sql)
        self.assertNotIn("INSERT INTO `ry-vue`.`sys_role_menu`", self.sql)

    def test_menu_dictionary_are_explicit_and_transactional(self):
        start = self.sql.index("START TRANSACTION;")
        commit = self.sql.index("COMMIT;", start)
        inserts = self.sql[start:commit]
        menus = re.findall(r"INSERT INTO `ry-vue`\.`sys_menu` \([^;]+?\) VALUES \((185[0-9]),", inserts)
        self.assertEqual([str(i) for i in range(1850, 1860)], menus)
        self.assertEqual(1, inserts.count("INSERT INTO `ry-vue`.`sys_dict_data`"))
        self.assertIn("(3000044, '000000', 4, 'AI交付风险决策', 'AI_DECISION', 'pu_source_type'", inserts)
        self.assertNotRegex(inserts, r"(?i)INSERT\s+IGNORE|ON\s+DUPLICATE\s+KEY")
        main = (ROOT / "script/sql/ry-vue.sql").read_text(encoding="utf-8")
        for table in ("sys_menu", "sys_dict_data"):
            columns = re.findall(r"^\s*`([^`]+)` ", definition("ry-vue", table), re.M)
            prefix = "INSERT INTO `ry-vue`.`" + table + "` (" + ", ".join("`" + key + "`" for key in columns) + ") VALUES"
            for line in main.splitlines():
                is_menu = table == "sys_menu" and re.match(r"INSERT INTO `sys_menu` VALUES \(185[0-9],", line)
                is_dictionary = table == "sys_dict_data" and line.startswith("INSERT INTO `sys_dict_data` VALUES (3000044,")
                if is_menu or is_dictionary:
                    expected = line.replace("INSERT INTO `" + table + "` VALUES", prefix, 1)
                    self.assertIn(expected, inserts)

    def test_checker_rejects_lost_tenant_scope_and_unique_index(self):
        """反向变异验证：索引不可降级或丢失租户范围。"""
        original = self.sql
        mutations = [
            original.replace("ADD UNIQUE INDEX `uk_psp_decision_idem`", "ADD INDEX `uk_psp_decision_idem`", 1),
            original.replace("ADD UNIQUE INDEX `uk_pur_decision_idem`(`tenant_id`, `decision_idempotency_key`)",
                             "ADD UNIQUE INDEX `uk_pur_decision_idem`(`decision_idempotency_key`)", 1),
        ]
        try:
            for mutated in mutations:
                self.assertNotEqual(original, mutated, "变异必须确实改变检查对象")
                self.sql = mutated
                with self.assertRaises(AssertionError):
                    self.test_erp_columns_and_indexes_are_not_lost()
        finally:
            self.sql = original

    def test_checker_rejects_menu_permission_and_column_mapping_changes(self):
        """反向变异验证：完整种子值及显式列映射不能悄然漂移。"""
        original = self.sql
        mutations = [
            original.replace("'ai:decision:approve'", "'ai:flow:run'", 1),
            original.replace("`menu_name`, `parent_id`", "`parent_id`, `menu_name`", 1),
            original.replace("'info', 'N', 103, 1, '2026-09-27 23:00:00'", "'danger', 'Y', 103, 1, '2026-09-27 23:00:00'", 1),
        ]
        try:
            for mutated in mutations:
                self.assertNotEqual(original, mutated, "变异必须确实改变检查对象")
                self.sql = mutated
                with self.assertRaises(AssertionError):
                    self.test_menu_dictionary_are_explicit_and_transactional()
        finally:
            self.sql = original


if __name__ == "__main__":
    unittest.main()
