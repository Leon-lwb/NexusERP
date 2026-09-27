"""合并的数据库结构契约测试，保留节点类型与采购元数据全部三项检查。"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]


class FlowSchemaContractTest(unittest.TestCase):
    """核对每个工业节点标识都可完整保存。"""

    def test_node_type_column_holds_every_industrial_provider_without_truncation(self):
        sql = (ROOT / "script/sql/ry-vue.sql").read_text(encoding="utf-8")
        table = re.search(r"CREATE TABLE `ai_flow_node_run`\s*\((.*?)\)\s*ENGINE\s*=", sql, re.S).group(1)
        capacity = int(re.search(r"`node_type` varchar\((\d+)\)", table).group(1))
        providers = ROOT / "ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/node/industrial"
        codes = []
        for file in providers.glob("*.java"):
            codes += re.findall(r'return "(industrial\.[a-zA-Z]+)"', file.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(codes), 7)
        for code in codes:
            self.assertLessEqual(len(code), capacity, code)


class MetadataSchemaTest(unittest.TestCase):
    """核对 AI 来源字典和主数据展示字段容量。"""

    def test_purchase_source_dictionary_contains_one_ai_decision_entry(self):
        sql = (ROOT / "script/sql/ry-vue.sql").read_text(encoding="utf-8")
        entries = [line for line in sql.splitlines()
                   if line.startswith("INSERT INTO `sys_dict_data`")
                   and "'pu_source_type'" in line and "'AI_DECISION'" in line]
        self.assertEqual(1, len(entries))
        self.assertIn("AI交付风险决策", entries[0])

    def test_requisition_snapshot_fields_can_hold_master_display_fields(self):
        sql = (ROOT / "script/sql/ry-erp.sql").read_text(encoding="utf-8")
        table = lambda name: re.search(r"CREATE TABLE `" + name + r"`\s*\((.*?)\)\s*ENGINE\s*=", sql, re.S).group(1)
        length = lambda name, key: int(re.search(r"`" + key + r"` varchar\((\d+)\)", table(name)).group(1))
        for line, master, name in [("material_name", "material_name", "bd_material"),
                                   ("material_spec", "material_spec", "bd_material"),
                                   ("unit", "uom_name", "bd_unit_of_measure")]:
            self.assertGreaterEqual(length("pu_purchase_requisition_line", line), length(name, master), line)


if __name__ == "__main__":
    unittest.main()
