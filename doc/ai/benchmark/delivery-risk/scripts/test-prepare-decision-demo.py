"""离线检查，不连接 MySQL/Docker 或输出凭据。"""
import importlib.util
import unittest
from unittest.mock import patch
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[4]
SPEC = importlib.util.spec_from_file_location('prepare_decision_demo', HERE / 'prepare-decision-demo.py')
DEMO = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DEMO)


class RuntimePreparationSafetyTest(unittest.TestCase):
    def test_only_new_mes_iot_schemas_are_importable(self):
        self.assertEqual('nexus_ai_decision_demo_mes', DEMO.schema_for('ry-mes.sql'))
        self.assertEqual('nexus_ai_decision_demo_iot', DEMO.schema_for('ry-iot.sql'))
        for name in ('ry-vue.sql', 'ry-erp.sql', 'ry-mes.sql.bak', '../ry-erp.sql'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                DEMO.schema_for(name)

    def test_ddl_extraction_drops_records_and_root_routines(self):
        for file, count in (('ry-mes.sql', 76), ('ry-iot.sql', 16)):
            with self.subTest(file=file):
                ddl = DEMO.extract_table_ddl((REPO / 'script/sql' / file).read_text(encoding='utf-8-sig'))
                self.assertEqual(count, ddl.count('CREATE TABLE `'))
                self.assertNotIn('INSERT INTO', ddl)
                self.assertNotIn('DROP TABLE', ddl)
                self.assertNotIn('DEFINER=', ddl)
                self.assertNotIn('PROCEDURE', ddl)
                self.assertNotIn('USE `', ddl)

    def test_ddl_extraction_rejects_schema_switch_or_qualified_tables(self):
        table = 'CREATE TABLE `demo` (\n  `id` bigint NOT NULL\n) ENGINE = InnoDB;'
        for unsafe in ('USE `ry-mes`;\n' + table, 'DROP DATABASE `ry-vue`;\n' + table,
                       table.replace('`demo`', '`ry-erp`.`demo`'),
                       table + '\nCALL `dangerous`();', table + '\nSOURCE other.sql;',
                       table + '\nGRANT ALL ON *.* TO test;'):
            with self.subTest(unsafe=unsafe[:60]), self.assertRaises(ValueError):
                DEMO.extract_table_ddl(unsafe)

    def test_read_only_verify_must_not_create_private_secret_file(self):
        with patch.object(DEMO, 'ENV_FILE') as private_file:
            private_file.exists.return_value = False
            with self.assertRaises(RuntimeError):
                DEMO.env_values()
            private_file.open.assert_not_called()
    def test_disposable_server_refuses_any_plus_schema(self):
        with patch.object(DEMO, 'mysql_query', return_value='ry-vue'):
            with self.assertRaises(RuntimeError):
                DEMO.verify_no_plus_schemas()
        with patch.object(DEMO, 'mysql_query', return_value=''):
            DEMO.verify_no_plus_schemas()
    def test_account_grants_only_schema_scoped_dml(self):
        statements = DEMO.grant_statements('decision_demo', 'a' * 48)
        self.assertIn('CREATE USER', statements[0])
        for name in DEMO.ALL_SCHEMAS:
            self.assertTrue(any(name.replace('_', r'\_') in line for line in statements))
        self.assertTrue(all(' ON *.* ' not in line for line in statements))
        self.assertTrue(all('DROP ' not in line and 'GRANT ALL' not in line for line in statements))
        self.assertTrue(all('ry-vue' not in line and 'ry-erp' not in line for line in statements))

    def test_root_importer_never_targets_unsecured_existing_mysql(self):
        self.assertIn("--port=13307", DEMO.MYSQL)
        self.assertEqual("--no-defaults", DEMO.MYSQL[1])
        self.assertNotIn("--port=3306", DEMO.MYSQL)
        self.assertEqual("13307", DEMO.fixed_env_values()["DECISION_DEMO_DB_PORT"])

    def test_mysql_directory_is_named_and_cannot_target_existing_instances(self):
        self.assertEqual('nexus-ai-decision-demo-mysql', DEMO.DATA_ROOT.name)
        self.assertEqual('data', DEMO.DATA_DIR.name)
        self.assertEqual('mysqld.exe', DEMO.MYSQLD_BIN.name.lower())
        with self.assertRaises(ValueError):
            DEMO.check_data_directory(DEMO.REPO / 'script/sql')
        with self.assertRaises(ValueError):
            DEMO.check_data_directory(Path('D:/software/mysql-5.7.14/data'))
        self.assertEqual(DEMO.DATA_DIR, DEMO.check_data_directory(DEMO.DATA_DIR))
    def test_startup_requires_verified_isolated_environment_and_excludes_root_secret(self):
        launcher = (HERE / 'run-decision-demo.ps1').read_text(encoding='utf-8')
        self.assertIn('--verify', launcher)
        self.assertIn('Get-NetTCPConnection', launcher)
        self.assertIn('DECISION_DEMO_MYSQL_ROOT_PASSWORD', launcher)
        self.assertIn('spring.profiles.active=dev,decision-demo', launcher)
        self.assertNotIn('MYSQL_ROOT_PASSWORD=$env:', launcher)
    def test_compose_maps_only_loopback_demo_ports(self):
        text = (HERE.parents[0] / 'compose.demo.yml').read_text(encoding='utf-8')
        for port in ('6385:6379', '5685:5672', '15685:15672'):
            self.assertIn('127.0.0.1:' + port, text)
        self.assertNotIn('0.0.0.0:', text)
        self.assertNotIn('6379:6379', text.replace('6385:6379', ''))
        self.assertNotIn('5672:5672', text.replace('5685:5672', ''))
        self.assertNotIn('image: mysql:', text)
        self.assertNotIn('13307:3306', text)


if __name__ == '__main__':
    unittest.main()
