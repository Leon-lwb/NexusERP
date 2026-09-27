"""一次性 MySQL 导入器的安全契约；测试不连接 MySQL。"""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('isolated_demo', Path(__file__).with_name('init-isolated-demo.py'))
DEMO = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DEMO)


class ImportSafetyTest(unittest.TestCase):
    def test_fixed_schema_names_never_include_plus_databases(self):
        self.assertEqual('nexus_ai_decision_demo_erp', DEMO.schema_for('ry-erp.sql'))
        self.assertEqual('nexus_ai_decision_demo_sys', DEMO.schema_for('ry-vue.sql'))
        self.assertEqual({'ry-vue.sql', 'ry-erp.sql'}, set(DEMO.schemas))
        with self.assertRaises(ValueError):
            DEMO.schema_for('ry-mes.sql')
        self.assertNotIn('ry-erp', DEMO.schemas.values())
        self.assertNotIn('ry-vue', DEMO.schemas.values())
        with self.assertRaises(ValueError):
            DEMO.check_schema('ry-vue')

    def test_dump_cannot_switch_or_qualify_a_target_schema(self):
        for sql in ('USE `ry-vue`;', 'DROP DATABASE IF EXISTS `ry-vue`;',
                    'INSERT INTO `ry-vue`.`sys_user` VALUES (1);',
                    'CREATE TABLE `mysql`.`user` (id BIGINT);',
                    'CREATE TABLE `ry-erp`.`prd_work_order` (id BIGINT);',
                    'CREATE DEFINER=`root`@`localhost` PROCEDURE `cross_db`() SELECT 1;',
                    'GRANT ALL ON *.* TO demo;', 'SET GLOBAL sql_mode = "";',
                    'SOURCE ../other.sql;'):
            with self.subTest(sql=sql), self.assertRaises(ValueError):
                DEMO.validate_dump('CREATE TABLE `demo` (id BIGINT);\n' + sql)
        DEMO.validate_dump('DROP TABLE IF EXISTS `demo`;\nCREATE TABLE `demo` (id BIGINT);')

    def test_dump_comment_contents_are_not_interpreted_as_executable_statements(self):
        DEMO.validate_dump("/* Navicat\n Source Database: old\n USE `ry-vue`;\n*/\n"
                           "CREATE TABLE `demo` (id BIGINT);")
    def test_seed_requires_guard_on_each_mutating_statement(self):
        good_row = "INSERT INTO `demo` VALUES (1) WHERE DATABASE() = 'nexus_ai_decision_demo_erp';"
        good = 'START TRANSACTION;\n' + ('\n' + good_row) * 40 + '\nCOMMIT;'
        DEMO.validate_seed(good)
        DEMO.validate_seed('-- Generator note; comment only\n' + good)
        with self.assertRaisesRegex(ValueError, 'unguarded'):
            DEMO.validate_seed(good + '\nDELETE FROM `prd_work_order`;')
        with self.assertRaisesRegex(ValueError, 'unguarded'):
            DEMO.validate_seed(good + '\nDROP TABLE `prd_work_order`;')
        with self.assertRaisesRegex(ValueError, 'unguarded'):
            DEMO.validate_seed(good + "\nINSERT INTO `demo` VALUES (1) WHERE DATABASE() = 'ry-erp';")

    def test_all_repository_dumps_pass_preflight_without_database_access(self):
        for file in DEMO.schemas:
            with self.subTest(file=file):
                DEMO.validate_dump((ROOT.parents[3] / 'script/sql' / file).read_text(encoding='utf-8-sig'))
        for file in ('demo-master-data.sql', 'demo-business-data.sql'):
            DEMO.validate_seed((ROOT / 'seed' / file).read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()