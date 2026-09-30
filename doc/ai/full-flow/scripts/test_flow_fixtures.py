import unittest
from datetime import date
from pathlib import Path
from unittest.mock import patch
import sys
from types import SimpleNamespace
sys.path.insert(0, str(Path(__file__).resolve().parent))
from flow_fixtures import build_business_dates, validate_business_name, expected_case_group, build_case_facts
from inventory_local3306 import validate_target, safe_identifier, build_count_query, redact_error, mysql_select, TABLES, candidate_query, grants_select_only, run_snapshot, CANDIDATES, _sanitize

class FixtureContractTest(unittest.TestCase):
    def test_dates(self):
        dates = build_business_dates(date(2026, 9, 28))
        self.assertLess(dates.order_date, dates.material_required_date)
        self.assertLess(dates.material_required_date, dates.promise_date)
        self.assertLess(dates.promise_date, dates.purchase_arrival_date)
        self.assertLess(dates.production_start_date, dates.production_end_date)

    def test_names(self):
        for name in ('智能制造测试订单', '演示订单', '临时计划', 'DEMO-AI-DECISION-001', 'test-order', 'Mock-workorder'):
            with self.subTest(name=name):
                self.assertFalse(validate_business_name(name))
        self.assertTrue(validate_business_name('华东精工伺服驱动器订单'))

    def test_groups(self):
        for number, group in ((1, 'normal'), (6, 'shortage_supplier_delay'), (25, 'capacity_bottleneck'), (40, 'capacity_evidence_missing')):
            self.assertEqual(expected_case_group(number), group)
        with self.assertRaises(ValueError):
            expected_case_group(41)

    def test_facts_use_historical_expectations_without_demo_ids(self):
        case = build_case_facts(40)
        self.assertEqual(case.case_id, 'DELIVERY-040')
        self.assertFalse(case.risk_facts['capacityComplete'])
        self.assertNotIn('bizId', case.risk_facts)
        self.assertEqual(case.expected_status, 'FAILED')

class InventoryContractTest(unittest.TestCase):
    def test_missing_credentials_never_connect(self):
        from inventory_local3306 import main
        with patch.dict('os.environ', {'MYSQL_USER': '', 'MYSQL_PWD': ''}), \
             patch('sys.argv', ['inventory_local3306.py']), \
             patch('inventory_local3306.subprocess.run') as run, \
             patch('sys.stderr'):
            self.assertEqual(main(), 1)
        run.assert_not_called()

    def test_admin_requires_explicit_opt_in_and_persists_no_password(self):
        from inventory_local3306 import main
        report = {'missing': [], 'snapshot': {'grants': 'admin read-only invocation, grants not least privilege verified'},
                  'usable_for_write_phase': True}
        with patch.dict('os.environ', {'MYSQL_USER': 'root', 'MYSQL_PWD': 'secret'}), \
             patch('inventory_local3306.inventory', return_value=report) as inventory_call, \
             patch.object(Path, 'write_text') as write_text, \
             patch.object(Path, 'mkdir'), patch('sys.stderr'):
            with patch('sys.argv', ['inventory_local3306.py']):
                self.assertEqual(main(), 1)
            inventory_call.assert_not_called()
            write_text.assert_not_called()
            with patch('sys.argv', ['inventory_local3306.py', '--allow-admin-readonly']):
                self.assertEqual(main(), 1)  # 仍不能用于写入。
            inventory_call.assert_called_once_with('root', 'secret', allow_admin_readonly=True)
            saved = write_text.call_args.args[0]
            self.assertNotIn('secret', saved)
            self.assertFalse(__import__('json').loads(saved)['usable_for_write_phase'])

    def test_admin_snapshot_grants_do_not_enable_writes(self):
        output = ('GRANT ALL PRIVILEGES ON *.* TO `root`@`localhost` WITH GRANT OPTION\n'
                  '__INVENTORY_0__\n2026-09-28\n__INVENTORY_1__\n')
        with patch('inventory_local3306.subprocess.run', return_value=SimpleNamespace(returncode=0, stdout=output, stderr='')) as run:
            with self.assertRaises(RuntimeError):
                run_snapshot(['SELECT CURRENT_DATE()'], 'root', 'secret')
            result = run_snapshot(['SELECT CURRENT_DATE()'], 'root', 'secret', allow_admin_readonly=True)
            self.assertEqual(result, [[['2026-09-28']]])
            args, kwargs = run.call_args
            self.assertIn('--skip-reconnect', args[0])
            self.assertIn('--host=127.0.0.1', args[0])
            self.assertIn('--port=3306', args[0])
            self.assertIn('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY', kwargs['input'])
            self.assertNotIn('secret', str(args[0]) + kwargs['input'])
            with self.assertRaises(ValueError):
                run_snapshot(["SELECT 1 INTO OUTFILE '/tmp/x'"], 'root', 'secret', allow_admin_readonly=True)
            self.assertEqual(run.call_count, 2)
        with patch('inventory_local3306.subprocess.run', return_value=SimpleNamespace(returncode=0, stdout='__INVENTORY_0__\n1\n__INVENTORY_1__\n', stderr='')):
            with self.assertRaises(RuntimeError) as raised:
                run_snapshot(['SELECT 1'], 'root', 'secret', allow_admin_readonly=True)
        self.assertNotIn('secret', str(raised.exception))

    def test_target_guard(self):
        for host, port, profile, schema in [('remote',3306,'dev','ry-vue'),('localhost',3307,'dev','ry-vue'),('localhost',3306,'prod','ry-vue'),('localhost',3306,'dev','decision-demo'),('localhost',3306,'dev','prod_data')]:
            with self.subTest(host=host, schema=schema), self.assertRaises(ValueError):
                validate_target(host, port, profile, schema)
        validate_target('127.0.0.1', 3306, 'dev', 'ry-vue')

    def test_query_allowlist_and_read_only(self):
        with self.assertRaises(ValueError):
            safe_identifier('ry-erp;DROP', {'ry-erp'})
        with self.assertRaises(ValueError):
            safe_identifier('ry-erp', {'ry-vue'})
        self.assertEqual(safe_identifier('product_code', {'product_code'}), '`product_code`')
        q = build_count_query('ry-erp', 'bd_material', {'ry-erp': {'bd_material'}})
        self.assertEqual(q, 'SELECT COUNT(*) FROM `ry-erp`.`bd_material`')
        with self.assertRaises(ValueError):
            build_count_query('ry-erp', 'unknown', {'ry-erp': {'bd_material'}})

    def test_mysql_query_uses_child_only_password_and_read_only_session(self):
        from types import SimpleNamespace
        with patch('inventory_local3306.subprocess.run', return_value=SimpleNamespace(returncode=0, stdout='1\n', stderr='')) as run:
            self.assertEqual(mysql_select('SELECT COUNT(*) FROM information_schema.TABLES', 'auditor', 'secret'), [['1']])
        args, kwargs = run.call_args
        self.assertNotIn('secret', ' '.join(args[0]))
        self.assertEqual(kwargs['env']['MYSQL_PWD'], 'secret')
        self.assertIn('--init-command=SET SESSION TRANSACTION READ ONLY', args[0])
        self.assertIn('--skip-reconnect', args[0])
        for sql in ('DELETE FROM foo', 'SELECT 1; DROP TABLE x', 'SELECT 1\\x', 'SELECT 1 INTO OUTFILE \"/tmp/x\"', 'SELECT * FROM t FOR UPDATE'):
            with self.assertRaises(ValueError):
                mysql_select(sql, 'auditor', 'secret')

    def test_interrupted_snapshot_discards_partial_rows(self):
        output = 'GRANT SELECT ON *.* TO `audit`@`localhost`\n__INVENTORY_0__\npartial\n'
        with patch('inventory_local3306.subprocess.run', return_value=SimpleNamespace(returncode=1, stdout=output, stderr='lost connection password=secret')):
            with self.assertRaises(RuntimeError) as raised:
                run_snapshot(['SELECT 1'], 'audit', 'secret')
        self.assertNotIn('partial', str(raised.exception))
        self.assertNotIn('secret', str(raised.exception))

    def test_mysql_errors_are_not_echoed(self):
        from types import SimpleNamespace
        with patch('inventory_local3306.subprocess.run', return_value=SimpleNamespace(returncode=1, stdout='', stderr='secret password=secret')):
            with self.assertRaises(RuntimeError) as raised:
                mysql_select('SELECT 1', 'auditor', 'secret')
        self.assertNotIn('secret', str(raised.exception))

    def test_mes_numbering_column_required(self):
        self.assertIn('wo_no', TABLES['ry-mes']['pro_work_order'])

    def test_active_tenant_predicate_and_verified_candidates(self):
        for schema, table, fragment in (
            ('ry-vue', 'sys_user', "status = '0'"),
            ('ry-vue', 'sys_dept', "status = '0'"),
            ('ry-erp', 'bd_material', 'status = 1'),
            ('ry-mes', 'mes_resource_pool', "status = 'ENABLED'"),
            ('ry-iot', 'iot_device', "status = 'ACTIVE'"),
        ):
            with self.subTest(table=table):
                q = candidate_query(schema, table, ('tenant_id',), ('tenant_id',))
                self.assertIn(fragment, q)
                if table != 'sys_tenant':
                    self.assertIn('`ry-vue`.`sys_tenant`', q)
                self.assertIn('ORDER BY', q)
                self.assertNotIn('LIMIT 30', q)
        self.assertIn("ORDER BY `id`", candidate_query('ry-vue', 'sys_tenant', ('tenant_id',), ('id',)))
        for table in ('wm_storage_area', 'wm_storage_location'):
            self.assertIn('frozen_flag = 0', candidate_query('ry-erp', table, ('tenant_id',), ('tenant_id',)))
        self.assertIn('status = 1', candidate_query('ry-erp', 'wm_warehouse', ('tenant_id', 'warehouse_id'), ('warehouse_id',)))
        self.assertIn("del_flag = '0'", candidate_query('ry-erp', 'bd_client', ('tenant_id', 'client_id'), ('client_id',)))

    def test_menu_schema_has_status_but_no_delete_flag(self):
        self.assertIn('status', TABLES['ry-vue']['sys_menu'])
        self.assertNotIn('del_flag', TABLES['ry-vue']['sys_menu'])

    def test_rules_include_join_keys_and_metadata(self):
        for table, columns in (
            ('sys_auto_code_rule', ('rule_id','tenant_id')),
            ('sys_auto_code_part', ('rule_id','tenant_id','part_index')),
        ):
            self.assertTrue(set(columns) <= set(TABLES['ry-vue'][table]))
        self.assertTrue({'rule_type','seq_length','check_digit_rule'} <= set(TABLES['ry-erp']['bd_serial_number_rule']))

    def test_mes_resource_item_requires_active_same_tenant_parent(self):
        q = candidate_query('ry-mes', 'mes_resource_item', ('tenant_id','pool_id'), ('item_id',))
        self.assertIn("status = 'AVAILABLE'", q)
        self.assertIn('EXISTS (SELECT 1 FROM `ry-mes`.`mes_resource_pool` p', q)
        self.assertIn('p.pool_id = `mes_resource_item`.pool_id', q)
        self.assertIn('p.tenant_id = `mes_resource_item`.tenant_id', q)
        self.assertIn("p.status = 'ENABLED'", q)
        self.assertIn("p.del_flag = '0'", q)

    def test_grants_reject_any_write_or_unresolved_roles(self):
        self.assertTrue(grants_select_only(['GRANT USAGE ON *.* TO `audit`@`localhost`',
                                            'GRANT SELECT ON `ry-erp`.* TO `audit`@`localhost`']))
        for grants in ([], ['GRANT ALL PRIVILEGES ON *.* TO `audit`@`localhost`'],
                       ['GRANT SELECT, INSERT ON *.* TO `audit`@`localhost`'],
                       ['GRANT SELECT ON *.* TO `audit`@`localhost` WITH GRANT OPTION'],
                       ['GRANT `reader`@`%` TO `audit`@`localhost`']):
            self.assertFalse(grants_select_only(grants))

    def test_single_read_only_snapshot_session_and_no_grant_disclosure(self):
        output = 'GRANT SELECT ON *.* TO `audit`@`localhost`\n__INVENTORY_0__\n2026-09-28 10:20:30.123456\t2026-09-28\n__INVENTORY_1__\n8\n__INVENTORY_2__\n'
        with patch('inventory_local3306.subprocess.run', return_value=SimpleNamespace(returncode=0, stdout=output, stderr='')) as run:
            result = run_snapshot(['SELECT NOW(6), CURRENT_DATE()', 'SELECT COUNT(*) FROM `ry-erp`.`bd_material`'], 'audit', 'secret')
        self.assertEqual(result, [[['2026-09-28 10:20:30.123456','2026-09-28']], [['8']]])
        args, kwargs = run.call_args
        self.assertIn('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY', kwargs['input'])
        self.assertIn('SHOW GRANTS', kwargs['input'])
        self.assertIn('--skip-reconnect', args[0])
        self.assertEqual(kwargs['input'].count('COMMIT'), 1)
        self.assertNotIn('secret', str(args[0]) + kwargs['input'])
        self.assertEqual(kwargs['env']['MYSQL_PWD'], 'secret')
        with patch('inventory_local3306.subprocess.run', return_value=SimpleNamespace(returncode=0, stdout=output.replace('GRANT SELECT', 'GRANT INSERT'), stderr='')):
            with self.assertRaises(RuntimeError):
                run_snapshot(['SELECT 1'], 'audit', 'secret')
    def test_source_proven_client_and_warehouse_candidates(self):
        self.assertIn('bd_client', CANDIDATES['ry-erp'])
        self.assertIn('wm_warehouse', CANDIDATES['ry-erp'])
        self.assertIn('client_id', CANDIDATES['ry-erp']['bd_client'])
        self.assertIn('warehouse_id', CANDIDATES['ry-erp']['wm_warehouse'])
        self.assertIn("del_flag = '0'", candidate_query('ry-erp', 'bd_client', ('tenant_id','client_id'), ('client_id',)))
        self.assertIn('status = 1', candidate_query('ry-erp', 'wm_warehouse', ('tenant_id','warehouse_id'), ('warehouse_id',)))

    def test_autocode_part_requires_matching_tenant_and_rule(self):
        q = candidate_query('ry-vue', 'sys_auto_code_part', ('tenant_id', 'rule_id', 'part_index'), ('part_id',))
        self.assertIn('r.rule_id =', q)
        self.assertIn('r.tenant_id =', q)
        self.assertIn('ORDER BY `tenant_id`, `rule_id`, `part_index`, `part_id`', q)

    def test_inventory_uses_one_snapshot_and_includes_source_verified_rows(self):
        from inventory_local3306 import inventory
        def metadata(sql, user, password):
            if 'SCHEMA_NAME FROM' in sql:
                return [[schema] for schema in TABLES]
            if 'ENGINE FROM' in sql:
                schema = sql.split("TABLE_SCHEMA = '")[1].split("'")[0]
                return [[table, 'InnoDB'] for table in TABLES[schema]]
            schema = sql.split("TABLE_SCHEMA = '")[1].split("'")[0]
            table = sql.split("TABLE_NAME = '")[1].split("'")[0]
            if 'COLUMN_NAME FROM information_schema.COLUMNS' in sql:
                return [[col] for col in TABLES[schema][table]]
            return [[TABLES[schema][table][0]]]
        def snapshot(queries, user, password, allow_admin_readonly=False):
            self.assertEqual(allow_admin_readonly, user == 'root')
            self.assertTrue(all(q.startswith('SELECT ') for q in queries))
            self.assertIn('CURRENT_DATE()', queries[0])
            self.assertTrue(any('ORDER BY' in q and 'sys_auto_code_part' in q for q in queries))
            self.assertTrue(any("m.status = '0'" in q and 'sys_menu' in q for q in queries))
            self.assertTrue(any('COUNT(*)' in q and 'active_tenant_total' not in q for q in queries))
            return [[['2026-09-28 10:20:30.123456', '2026-09-28']]] + [
                [['0']] if 'COUNT(' in q else
                [['000001', 'C', '7191380123456789012']] if 'FROM `ry-erp`.`bd_client`' in q else
                [] for q in queries[1:]]
        with patch('inventory_local3306.mysql_select', side_effect=metadata), patch('inventory_local3306.run_snapshot', side_effect=snapshot) as run:
            report = inventory('auditor', 'secret')
        run.assert_called_once()
        self.assertTrue(report['snapshot']['verified'])
        self.assertEqual(report['snapshot']['db_date'], '2026-09-28')
        self.assertTrue(report['usable_for_write_phase'])
        self.assertNotIn('bd_client', report['schemas']['ry-erp']['unverified_candidates'])
        self.assertIn('bd_client', report['schemas']['ry-erp']['candidates'])
        self.assertIn('wm_warehouse', report['schemas']['ry-erp']['candidates'])
        from erp_flow_runner import _inventory_row
        self.assertEqual(_inventory_row(report['schemas']['ry-erp'], 'bd_client', '000001',
                                        'client_code', 'C', id_field='client_id')['client_id'],
                         '7191380123456789012')
        self.assertEqual(report['schemas']['ry-vue']['candidate_columns']['sys_auto_code_part'], list(CANDIDATES['ry-vue']['sys_auto_code_part']))
        with patch('inventory_local3306.mysql_select', side_effect=metadata), patch('inventory_local3306.run_snapshot', side_effect=snapshot):
            admin_report = inventory('root', 'secret', allow_admin_readonly=True)
        self.assertFalse(admin_report['usable_for_write_phase'])
        self.assertEqual(admin_report['snapshot']['grants'], 'admin read-only invocation, grants not least privilege verified')
        self.assertNotIn('secret', __import__('json').dumps(admin_report))

    def test_candidate_fields_suppress_personal_or_unstructured_data(self):
        for value in ('person@example.com', '13812345678', '张三', 'token=private', 'eyJhbGci.eyJzdWI.signature'):
            with self.subTest(value=value):
                self.assertEqual(_sanitize(value), '[REDACTED]')
        self.assertEqual(_sanitize('MTR-20260928-001'), 'MTR-20260928-001')

    def test_only_authorized_snowflake_ids_bypass_phone_redaction(self):
        for field in ('client_id', 'material_id', 'routing_id', 'bom_id', 'warehouse_id', 'user_id'):
            with self.subTest(field=field):
                self.assertEqual(_sanitize('7191380123456789012', field), '7191380123456789012')
        for field in (None, 'client_code', 'material_code', 'warehouse_code', 'user_name',
                      'password', 'made_up_id', 'tenant_id'):
            with self.subTest(field=field):
                self.assertEqual(_sanitize('7191380123456789012', field), '[REDACTED]')
        for value in ('13812345678', '0138123456789012345', '9311380123456789012',
                      '71913801234567890123', '719138012345678901a', '7191380123456789012token'):
            with self.subTest(value=value):
                self.assertEqual(_sanitize(value, 'client_id'), '[REDACTED]')
        self.assertEqual(_sanitize('13812345678', 'client_code'), '[REDACTED]')
        self.assertEqual(_sanitize('secret=13812345678', 'client_id'), '[REDACTED]')
        self.assertEqual(_sanitize('person@example.com', 'user_id'), '[REDACTED]')

    def test_redacts_errors(self):
        self.assertEqual(redact_error('mysql: Access denied for user admin password=secret'), 'MySQL inventory query failed; check local credentials and schema permissions (details suppressed).')

if __name__ == '__main__':
    unittest.main()
