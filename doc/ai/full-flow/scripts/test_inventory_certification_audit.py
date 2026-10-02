"""只读库存审计的纯数据与受控传输回归；不会连接数据库。"""
import contextlib
import copy
import importlib
import io
import json
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
try:
    audit = importlib.import_module('inventory_certification_audit')
except ModuleNotFoundError:
    audit = None

TENANT = '000000'


def fixture():
    metadata = {name: {'engine': 'InnoDB', 'columns': {c: {'type': 'varchar', 'precision': None, 'scale': None}
                  for c in cols}} for name, cols in audit.COLUMNS.items()}
    for name, cols in audit.DECIMAL_FIELDS.items():
        for c, scale in cols.items():
            metadata[name]['columns'][c] = {'type': 'decimal', 'precision': 18, 'scale': scale}
    data = {name: [] for name in audit.COLUMNS}
    data['environment'] = [{'database_name': 'ry-erp', 'isolation': 'REPEATABLE-READ', 'read_only': '1', 'clock_epoch': '1790910000'}]
    data['sys_tenant'] = [{'tenant_id': TENANT, 'status': '0', 'del_flag': '0'}]
    data['bd_material'] = [{'tenant_id': TENANT, 'material_id': '11', 'base_uom_id': '3', 'safety_stock': '0.0000', 'status': '1', 'del_flag': '0'}]
    data['bd_unit_of_measure'] = [{'tenant_id': TENANT, 'uom_id': '3', 'status': '1', 'del_flag': '0', 'is_base': '1',
        'primary_id': None, 'conversion_rate': '1.000000', 'quantity_scale': '4', 'conversion_version': 'u1', 'conversion_verified_at': '2026-09-30 00:00:00'}]
    data['wm_warehouse'] = [{'tenant_id': TENANT, 'warehouse_id': '4', 'status': '1', 'del_flag': '0'}]
    data['wm_storage_area'] = [{'tenant_id': TENANT, 'area_id': '5', 'warehouse_id': '4', 'enable_flag': '1', 'location_flag': '0', 'frozen_flag': '0', 'del_flag': '0'}]
    data['wm_material_stock'] = [{'tenant_id': TENANT, 'stock_id': '7', 'material_id': '11', 'base_uom_id': '3',
        'stock_quantity': '2.0000', 'reserved_quantity': '0.5000', 'stock_quantity_exact': '2.0000', 'reserved_quantity_exact': '0.5000',
        'warehouse_id': '4', 'area_id': '5', 'location_id': None, 'frozen_flag': '0', 'del_flag': '0', 'version': '2',
        'quantity_contract_version': '1', 'quantity_origin': 'NATIVE_V1', 'quantity_uom_version': 'u1',
        'quantity_evidence_hash': 'a' * 64, 'quantity_version': '1', 'reservation_version': '1',
        'quantity_source_head_tx_id': '8', 'reservation_head_event_id': '9', 'quantity_source_at': '2026-09-30 00:00:00'}]
    data['wm_material_stock_tx'] = [{'tenant_id': TENANT, 'tx_id': '8', 'material_stock_id': '7', 'material_id': '11',
        'base_uom_id': '3', 'tx_flag': '1', 'tx_quantity': '2.0000', 'before_quantity': '0.0000', 'after_quantity': '2.0000',
        'tx_quantity_exact': '2.0000', 'before_quantity_exact': '0.0000', 'after_quantity_exact': '2.0000',
        'quantity_contract_version': '1', 'quantity_origin': 'NATIVE_V1', 'quantity_uom_version': 'u1', 'quantity_evidence_hash': 'a' * 64,
        'quantity_version_before': '0', 'quantity_version_after': '1', 'source_event_key': 'receipt-1',
        'source_doc_type': 'WM_MATERIAL_RECEIVE', 'source_doc_id': '21', 'source_doc_line_id': '22', 'del_flag': '0'}]
    data['wm_material_receive'] = [{'tenant_id': TENANT, 'receive_id': '21', 'status': 'FINISHED', 'del_flag': '0'}]
    data['wm_material_receive_line'] = [{'tenant_id': TENANT, 'line_id': '22', 'receive_id': '21', 'material_id': '11', 'base_uom_id': '3', 'quantity_received': '2.0000', 'del_flag': '0'}]
    data['wm_material_receive_detail'] = [{'tenant_id': TENANT, 'detail_id': '23', 'line_id': '22', 'receive_id': '21', 'material_id': '11', 'base_uom_id': '3', 'quantity': '2.0000', 'del_flag': '0'}]
    data['so_customer_order'] = [{'tenant_id': TENANT, 'co_id': '31', 'status': 'CONFIRMED', 'del_flag': '0'}]
    data['so_sales_order_line'] = [{'tenant_id': TENANT, 'line_id': '32', 'so_id': '31', 'material_id': '11', 'unit': '3', 'reserved_qty': '0.5000', 'del_flag': '0'}]
    data['wm_stock_reservation_event'] = [{'tenant_id': TENANT, 'event_id': '9', 'stock_id': '7', 'material_id': '11', 'base_uom_id': '3',
        'quantity_uom_version': 'u1', 'owner_doc_type': 'SO_LINE', 'owner_doc_id': '31', 'owner_doc_line_id': '32', 'owner_mrp_detail_id': None,
        'event_type': 'RESERVE', 'reserved_delta': '0.5000', 'before_reserved_quantity': '0.0000', 'after_reserved_quantity': '0.5000',
        'reservation_version_before': '0', 'reservation_version_after': '1', 'stock_version_before': '1', 'stock_version_after': '2',
        'source_doc_type': 'SO_LINE', 'source_doc_id': '31', 'source_doc_line_id': '32', 'source_event_key': 'reserve-1',
        'related_stock_tx_id': None, 'quantity_contract_version': '1', 'quantity_origin': 'NATIVE_V1', 'quantity_evidence_hash': 'b' * 64}]
    return metadata, data


class AuditTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(audit, 'Read-only inventory certification audit is not implemented')
        self.meta, self.data = fixture()

    def report(self):
        return audit.evaluate_snapshot(self.meta, self.data, TENANT)

    def codes(self):
        return {r['code'] for r in self.report()['blocked_reasons']}

    def test_target_requires_explicit_local_dev_erp_tenant(self):
        audit.validate_target('127.0.0.1', 3306, 'dev', 'ry-erp', TENANT)
        audit.validate_target('localhost', 3306, 'dev', 'ry-erp', TENANT)
        for values in [('10.0.0.2',3306,'dev','ry-erp',TENANT), ('127.0.0.1',3307,'dev','ry-erp',TENANT),
                       ('localhost',3306,'prod','ry-erp',TENANT), ('localhost',3306,'decision-demo','ry-erp',TENANT),
                       ('localhost',3306,'dev','ry-vue',TENANT), ('localhost',3306,'dev','ry-erp',"0';DROP TABLE x"),
                       ('localhost',3306,'dev','ry-erp',''), ('localhost',3306,'dev','ry-erp','*')]:
            with self.subTest(values=values), self.assertRaises(ValueError):
                audit.validate_target(*values)

    def test_float_stock_cannot_be_cast_or_shadow_upgraded(self):
        self.meta['wm_material_stock']['columns']['stock_quantity']['type'] = 'double'
        self.assertIn('NATIVE_QUANTITY_TYPE_UNPROVEN', self.codes())
        self.assertEqual(self.report()['status'], 'BLOCKED')

    def test_two_decimal_tx_cannot_be_upgraded_by_textual_four_decimal_values(self):
        self.meta['wm_material_stock_tx']['columns']['tx_quantity']['precision'] = 12
        self.meta['wm_material_stock_tx']['columns']['tx_quantity']['scale'] = 2
        self.assertIn('NATIVE_QUANTITY_TYPE_UNPROVEN', self.codes())

    def test_double_receive_detail_still_blocks_exact_inventory(self):
        self.meta['wm_material_receive_detail']['columns']['quantity']['type'] = 'double'
        self.assertIn('SOURCE_QUANTITY_NATIVE_UNPROVEN', self.codes())

    def test_missing_additive_columns_are_reported_without_crashing_or_ignoring_history(self):
        del self.meta['wm_material_stock']['columns']['quantity_origin']
        self.assertIn('SCHEMA_CONTRACT_MISSING', self.codes())

    def test_empty_inventory_is_not_certified_zero(self):
        self.data['wm_material_stock'] = []
        self.assertIn('EMPTY_STOCK_NOT_PROVEN_ZERO', self.codes())

    def test_null_and_explicit_zero_have_different_reasons(self):
        self.data['wm_material_stock'][0]['stock_quantity'] = None
        self.assertIn('STOCK_QUANTITY_UNKNOWN_OR_INVALID', self.codes())
        self.data['wm_material_stock'][0]['stock_quantity'] = '0.0000'
        self.data['wm_material_stock'][0]['reserved_quantity'] = '0.0000'
        self.assertNotIn('STOCK_QUANTITY_UNKNOWN_OR_INVALID', self.codes())
        self.assertIn('STOCK_EXACT_SHADOW_DIVERGES', self.codes())

    def test_float_python_quantity_is_rejected_even_with_exact_metadata(self):
        self.data['wm_material_stock'][0]['stock_quantity'] = 2.0
        self.assertIn('STOCK_QUANTITY_UNKNOWN_OR_INVALID', self.codes())

    def test_quantity_overflow_or_rounding_is_not_accepted(self):
        for value in ('1.00001', '-1.0000', '100000000000000.0000', 'NaN', '2e0'):
            with self.subTest(value=value):
                self.data['wm_material_stock'][0]['stock_quantity'] = value
                self.assertIn('STOCK_QUANTITY_UNKNOWN_OR_INVALID', self.codes())

    def test_reservation_exceeding_stock_is_rejected(self):
        self.data['wm_material_stock'][0]['reserved_quantity'] = '3.0000'
        self.assertIn('RESERVED_EXCEEDS_STOCK', self.codes())

    def test_unknown_origin_or_version_is_not_a_verified_flag(self):
        for field, value in [('quantity_origin','VERIFIED'), ('quantity_contract_version',None), ('quantity_evidence_hash',None)]:
            with self.subTest(field=field):
                old = self.data['wm_material_stock'][0][field]
                self.data['wm_material_stock'][0][field] = value
                self.assertIn('STOCK_ORIGIN_UNPROVEN', self.codes())
                self.data['wm_material_stock'][0][field] = old

    def test_current_unit_name_and_version_cannot_be_inferred_for_history(self):
        self.data['wm_material_stock'][0]['base_uom_id'] = 'kilogram'
        self.assertIn('STOCK_UNIT_IDENTITY_UNPROVEN', self.codes())
        self.data['wm_material_stock'][0]['base_uom_id'] = '3'
        self.data['wm_material_stock'][0]['quantity_uom_version'] = 'old'
        self.assertIn('STOCK_UNIT_VERSION_UNPROVEN', self.codes())

    def test_inactive_unit_unknown_scale_or_nonroot_unit_is_blocked(self):
        for field, value in [('status','0'), ('quantity_scale',None), ('primary_id','2'), ('conversion_verified_at',None)]:
            with self.subTest(field=field):
                old = self.data['bd_unit_of_measure'][0][field]
                self.data['bd_unit_of_measure'][0][field] = value
                self.assertIn('UNIT_CONTRACT_UNPROVEN', self.codes())
                self.data['bd_unit_of_measure'][0][field] = old

    def test_disabled_wrong_parent_or_unknown_frozen_node_is_blocked(self):
        for field, value in [('enable_flag','0'), ('warehouse_id','999'), ('frozen_flag',None), ('location_flag','1')]:
            with self.subTest(field=field):
                old = self.data['wm_storage_area'][0][field]
                self.data['wm_storage_area'][0][field] = value
                self.assertIn('STORAGE_LINEAGE_UNPROVEN', self.codes())
                self.data['wm_storage_area'][0][field] = old

    def test_frozen_is_counted_without_becoming_supply(self):
        self.data['wm_material_stock'][0]['frozen_flag'] = '1'
        self.assertEqual(self.report()['facts']['frozen_stock_rows'], 1)
        self.assertFalse(self.report()['authorizes_writes'])
        self.assertNotIn('available_quantity', self.report()['facts'])

    def test_missing_tx_or_unlinked_deleted_tx_blocks(self):
        self.data['wm_material_stock_tx'] = []
        self.assertIn('STOCK_TX_CHAIN_UNPROVEN', self.codes())
        self.data = fixture()[1]
        self.data['wm_material_stock_tx'][0]['del_flag'] = '1'
        self.assertIn('TX_ORIGIN_UNPROVEN', self.codes())

    def test_tx_versions_need_contiguous_origin_and_final_balance(self):
        for field, value in [('quantity_version_before','4'), ('quantity_version_after','9'), ('after_quantity','1.0000')]:
            with self.subTest(field=field):
                old = self.data['wm_material_stock_tx'][0][field]
                self.data['wm_material_stock_tx'][0][field] = value
                self.assertIn('STOCK_TX_CHAIN_UNPROVEN', self.codes())
                self.data['wm_material_stock_tx'][0][field] = old

    def test_tx_source_is_not_guessed_from_doc_code_or_id(self):
        for field, value, code in [('source_doc_type','MYSTERY','SOURCE_BUSINESS_TYPE_UNSUPPORTED'),
                                 ('source_doc_id',None,'SOURCE_BUSINESS_REFERENCE_MISSING'),
                                 ('source_doc_line_id','23','SOURCE_BUSINESS_LINEAGE_UNPROVEN')]:
            with self.subTest(field=field):
                old = self.data['wm_material_stock_tx'][0][field]
                self.data['wm_material_stock_tx'][0][field] = value
                self.assertIn(code, self.codes())
                self.data['wm_material_stock_tx'][0][field] = old

    def test_receipt_source_requires_head_line_and_detail_line_id_not_detail_id(self):
        for tab in ('wm_material_receive', 'wm_material_receive_line', 'wm_material_receive_detail'):
            with self.subTest(table=tab):
                old = self.data[tab]
                self.data[tab] = []
                self.assertIn('SOURCE_BUSINESS_LINEAGE_UNPROVEN', self.codes())
                self.data[tab] = old
        self.assertNotIn('SOURCE_BUSINESS_LINEAGE_UNPROVEN', self.codes())

    def test_candidate_status_hash_and_nonzero_reservation_never_issue_certificate(self):
        report = self.report()
        self.assertEqual(report['status'], 'BLOCKED')
        self.assertFalse(report['authorizes_writes'])
        self.assertIn('BUSINESS_SOURCE_PROOF_REQUIRED', self.codes())
        self.assertIn('RESERVATION_OWNERSHIP_PROOF_REQUIRED', self.codes())
        self.assertNotIn('verified', report)

    def test_reservation_without_ledger_is_unknown_not_other_demand(self):
        self.data['wm_stock_reservation_event'] = []
        self.assertIn('RESERVATION_CHAIN_UNPROVEN', self.codes())
        del self.meta['wm_stock_reservation_event']
        self.assertIn('SCHEMA_CONTRACT_MISSING', self.codes())

    def test_reservation_event_wrong_owner_unit_delta_version_or_consume_tx_blocks(self):
        for field, value in [('owner_doc_line_id','999'), ('base_uom_id','4'), ('reserved_delta','0.6000'),
                             ('reservation_version_before','4'), ('event_type','CONSUME'), ('quantity_contract_version',None)]:
            with self.subTest(field=field):
                old = self.data['wm_stock_reservation_event'][0][field]
                self.data['wm_stock_reservation_event'][0][field] = value
                self.assertIn('RESERVATION_CHAIN_UNPROVEN', self.codes())
                self.data['wm_stock_reservation_event'][0][field] = old

    def test_mrp_owner_requires_real_work_order_and_requirement_source_parent_chain(self):
        self.data['wm_stock_reservation_event'][0].update(owner_doc_type='WO_MRP_DETAIL', owner_mrp_detail_id='99')
        self.assertIn('MRP_OWNER_LINK_UNPROVEN', self.codes())

    def test_duplicate_id_and_source_key_are_not_summed(self):
        self.data['wm_material_stock_tx'].append(copy.deepcopy(self.data['wm_material_stock_tx'][0]))
        self.assertIn('DUPLICATE_ROW_IDENTITY', self.codes())
        self.assertIn('DUPLICATE_SOURCE_EVENT_KEY', self.codes())

    def test_foreign_tenant_is_rejected_not_hidden_in_sum(self):
        self.data['wm_material_stock_tx'][0]['tenant_id'] = '999999'
        self.assertIn('TENANT_SCOPE_MISMATCH', self.codes())

    def test_unknown_or_deleted_tenant_is_rejected(self):
        self.data['sys_tenant'][0]['status'] = '1'
        self.assertIn('TENANT_NOT_ACTIVE', self.codes())

    def test_noninnodb_or_wrong_isolation_or_not_readonly_is_rejected(self):
        self.meta['wm_material_stock_tx']['engine'] = 'MyISAM'
        self.assertIn('NONTRANSACTIONAL_TABLE', self.codes())
        self.data['environment'][0]['isolation'] = 'READ-COMMITTED'
        self.assertIn('SNAPSHOT_ENVIRONMENT_UNPROVEN', self.codes())

    def test_output_is_only_allowlisted_counts_types_and_reasons_never_raw_business(self):
        self.data['wm_material_stock'][0]['stock_id'] = 'private-token-value'
        self.data['wm_material_stock'][0]['material_name'] = 'Personal real business name'
        rendered = json.dumps(self.report())
        for raw in ('private-token-value','Personal real business name','receipt-1','u1',TENANT,'2.0000','a' * 64):
            self.assertNotIn(raw, rendered)
        self.assertNotIn('repair_sql', rendered)

    def test_queries_are_readonly_tenant_scoped_no_limits_no_cast_no_status_filter(self):
        queries = audit.build_snapshot_queries(self.meta, TENANT)
        for name, (sql, fields) in queries.items():
            self.assertTrue(sql.startswith('SELECT '))
            self.assertNotRegex(sql.upper(), r'\b(?:UPDATE|INSERT|DELETE|CAST|CONVERT|LIMIT|INTO)\b')
            self.assertNotIn(';', sql)
            if name not in ('environment','metadata_tables','metadata_columns'):
                self.assertIn("tenant_id = '000000'", sql)
                self.assertNotIn("del_flag = '0'", sql, 'Do not hide historical rows')

    def test_only_existing_whitelisted_fields_are_selected_for_legacy_schemas(self):
        del self.meta['wm_material_stock']['columns']['quantity_origin']
        sql, fields = audit.build_snapshot_queries(self.meta, TENANT)['wm_material_stock']
        self.assertNotIn('quantity_origin', fields)
        self.assertNotIn('quantity_origin', sql)
        self.assertNotIn('*', sql)

    def test_injected_schema_names_and_columns_cannot_expand_reader(self):
        self.meta['secret_config'] = {'engine':'InnoDB','columns':{'password':{'type':'varchar'}}}
        self.meta['wm_material_stock']['columns']['private_key'] = {'type':'varchar'}
        queries = repr(audit.build_snapshot_queries(self.meta, TENANT))
        self.assertNotIn('secret_config', queries)
        self.assertNotIn('private_key', queries)

    def test_metadata_decoder_accepts_only_audit_tables_and_numeric_types(self):
        tables = [['ry-erp','wm_material_stock','InnoDB'], ['ry-erp','secret_config','InnoDB']]
        cols = [['ry-erp','wm_material_stock','stock_quantity','double','12','2'],
                ['ry-erp','secret_config','password','varchar','NULL','NULL']]
        meta = audit.decode_metadata(tables, cols)
        self.assertNotIn('secret_config', meta)
        self.assertEqual(meta['wm_material_stock']['columns']['stock_quantity']['type'], 'double')

    def test_error_and_missing_credentials_do_not_echo_details_or_connect(self):
        stream = io.StringIO()
        with patch.dict(os.environ, {}, clear=True), patch.object(audit, 'collect_audit') as collect, contextlib.redirect_stderr(stream):
            self.assertEqual(audit.main(['--tenant-id',TENANT]), 1)
            collect.assert_not_called()
        self.assertIn('BLOCKED', stream.getvalue())
        with patch.dict(os.environ, {'MYSQL_USER':'reader','MYSQL_PWD':'top-secret-password'}, clear=True), \
             patch.object(audit, 'collect_audit', side_effect=RuntimeError('token=private-server-value')), \
             contextlib.redirect_stderr(stream):
            self.assertEqual(audit.main(['--tenant-id',TENANT]), 1)
        self.assertNotIn('top-secret-password', stream.getvalue())
        self.assertNotIn('private-server-value', stream.getvalue())

    def test_admin_grants_and_root_user_are_not_offered_as_authorization_options(self):
        stream = io.StringIO()
        with patch.dict(os.environ, {'MYSQL_USER':'root','MYSQL_PWD':'sensitive'}, clear=True), \
             patch.object(audit, 'collect_audit') as collect, contextlib.redirect_stderr(stream):
            self.assertEqual(audit.main(['--tenant-id', TENANT]), 1)
            collect.assert_not_called()

    def test_only_ignored_fixed_output_can_be_written(self):
        with patch.object(audit, 'collect_audit') as collect, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(audit.main(['--tenant-id',TENANT,'--output',str(HERE / 'tracked-audit.json')]), 1)
            collect.assert_not_called()


    def test_candidate_contract_is_never_counted_as_certified_inventory(self):
        facts = self.report()['facts']
        self.assertEqual(facts.get('certified_stock_rows', 'not-implemented'), 0)
        self.assertEqual(facts.get('isolated_stock_rows', 'not-implemented'), 1)
        self.assertEqual(facts.get('stock_rows_without_candidate_contract', 'not-implemented'), 0)

    def test_native_float_zero_never_participates_in_exact_chain_arithmetic(self):
        self.meta['wm_material_stock']['columns']['stock_quantity']['type'] = 'double'
        self.data['wm_material_stock'][0]['stock_quantity'] = '0.0000'
        self.assertIn('STOCK_QUANTITY_UNKNOWN_OR_INVALID', self.codes())

    def test_real_snapshot_transport_sets_database_readonly_rr_and_timezone_and_suppresses_grants(self):
        self.assertTrue(hasattr(audit, '_audit_snapshot'), 'Explicit ERP snapshot transport is missing')
        lines = ['GRANT SELECT ON *.* TO read_only_account', '__STOCK_AUDIT_0__', 'one', '__STOCK_AUDIT_1__']
        with patch.object(audit, '_run', return_value=lines) as run:
            self.assertEqual(audit._audit_snapshot(['SELECT 1'], 'reader', 'password-value'), [[['one']]])
        args, password, script = run.call_args.args
        self.assertIn('--database=ry-erp', args)
        self.assertNotIn('password-value', ' '.join(args) + script)
        self.assertIn('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ', script)
        self.assertIn('SET SESSION TRANSACTION READ ONLY', script)
        self.assertIn("SET SESSION time_zone = '+08:00'", script)
        self.assertIn('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY', script)
        self.assertIn('COMMIT;', script)
        self.assertEqual(password, 'password-value')

    def test_real_snapshot_transport_rejects_admin_grants_bad_framing_and_nonselect_sql(self):
        self.assertTrue(hasattr(audit, '_audit_snapshot'), 'Explicit ERP snapshot transport is missing')
        for lines in [['GRANT ALL PRIVILEGES ON *.* TO root', '__STOCK_AUDIT_0__', 'one', '__STOCK_AUDIT_1__'],
                      ['GRANT SELECT ON *.* TO reader', '__STOCK_AUDIT_0__', 'one']]:
            with patch.object(audit, '_run', return_value=lines), self.assertRaises(RuntimeError):
                audit._audit_snapshot(['SELECT 1'], 'reader', 'password-value')
        for sql in ['UPDATE wm_material_stock SET stock_quantity=0', 'SELECT 1;DELETE FROM x', 'SELECT 1 FOR UPDATE']:
            with patch.object(audit, '_run') as run, self.assertRaises(ValueError):
                audit._audit_snapshot([sql], 'reader', 'password-value')
            run.assert_not_called()

    def test_collect_audit_rechecks_metadata_frames_without_a_database_connection(self):
        def metadata_rows():
            tables, cols = [], []
            for table, detail in self.meta.items():
                schema = 'ry-vue' if table == 'sys_tenant' else 'ry-erp'
                tables.append([schema,table,detail['engine']])
                for col, native in detail['columns'].items():
                    cols.append([schema,table,col,native['type'],str(native['precision']) if native['precision'] is not None else 'NULL',
                                 str(native['scale']) if native['scale'] is not None else 'NULL'])
            return [tables,cols]
        calls = []
        def runner(sqls,user,password):
            calls.append(sqls)
            if len(calls) == 1:
                return metadata_rows()
            queries = audit.build_snapshot_queries(self.meta,TENANT)
            result = metadata_rows()
            for name, (_, fields) in list(queries.items())[2:]:
                result.append([[row.get(field) if row.get(field) is not None else 'NULL' for field in fields]
                                for row in self.data.get(name,[])])
            return result + metadata_rows()
        report = audit.collect_audit('reader','password-value',TENANT,runner=runner)
        self.assertEqual(len(calls),2)
        self.assertEqual(report['facts']['row_counts']['wm_material_stock'],1)
        self.assertEqual(report['status'],'BLOCKED')
        self.assertNotIn('SNAPSHOT_ENVIRONMENT_UNPROVEN',{r['code'] for r in report['blocked_reasons']})
        self.assertEqual(calls[1][0],calls[1][-2])
        self.assertEqual(calls[1][1],calls[1][-1])


    def test_metadata_drift_in_snapshot_is_discarded_with_redacted_failure(self):
        initial = [[['ry-erp','wm_material_stock','InnoDB']],
                   [['ry-erp','wm_material_stock','stock_quantity','double','12','2']]]
        calls = 0
        def runner(queries,user,password):
            nonlocal calls
            calls += 1
            if calls == 1:
                return copy.deepcopy(initial)
            changed = copy.deepcopy(initial)
            changed[1][0][3] = 'decimal'
            return copy.deepcopy(initial) + [[['ry-erp','REPEATABLE-READ','1','1791000000']]] + changed
        with self.assertRaisesRegex(RuntimeError,'details suppressed'):
            audit.collect_audit('reader','password-private',TENANT,runner=runner)
        self.assertEqual(calls,2)

    def test_incomplete_native_row_frame_is_rejected_not_silently_zipped(self):
        # 元数据支持行主键/租户，但客户端返回行漏列。错误必须丢弃快照且脱敏。
        initial = [[['ry-erp','wm_material_stock','InnoDB']],
                   [['ry-erp','wm_material_stock','stock_id','bigint','20','0'],
                    ['ry-erp','wm_material_stock','tenant_id','varchar','NULL','NULL']]]
        calls = 0
        def runner(queries,user,password):
            nonlocal calls
            calls += 1
            if calls == 1:
                return copy.deepcopy(initial)
            return copy.deepcopy(initial) + [[['ry-erp','REPEATABLE-READ','1','1791000000']], [['raw-private-identity']]] + copy.deepcopy(initial)
        with self.assertRaisesRegex(RuntimeError,'details suppressed'):
            audit.collect_audit('reader','password-private',TENANT,runner=runner)

    def test_timezone_offset_and_future_microsecond_source_are_not_accepted(self):
        self.data['wm_material_stock'][0]['quantity_source_at'] = '2026-09-30 00:00:00+07:00'
        self.assertIn('STOCK_ORIGIN_UNPROVEN', self.codes())
        self.data['environment'][0]['clock_epoch'] = '1790697600.000000'
        self.data['wm_material_stock'][0]['quantity_source_at'] = '2026-09-30 00:00:00.000001'
        self.assertIn('STOCK_ORIGIN_UNPROVEN', self.codes())

    def test_unparseable_business_quantity_is_reported_even_when_ids_match(self):
        self.data['wm_material_receive_detail'][0]['quantity'] = None
        self.assertIn('SOURCE_BUSINESS_QUANTITY_UNPROVEN',self.codes())

    def test_all_stock_owner_balances_are_compared_with_source_sales_reservation(self):
        self.data['so_sales_order_line'][0]['reserved_qty'] = '0.0000'
        self.assertIn('RESERVATION_OWNER_BALANCE_UNPROVEN',self.codes())


    def test_bad_cli_arguments_never_echo_credentials_in_argparse_errors(self):
        for argv in [['--tenant-id',TENANT,'--password=private-credential-value'],
                     ['--tenant-id',TENANT,'--port','private-credential-value']]:
            stream = io.StringIO()
            with contextlib.redirect_stderr(stream), patch.object(audit,'collect_audit') as collect:
                try:
                    result = audit.main(argv)
                except SystemExit as exc:
                    result = exc.code
                self.assertEqual(result,1)
                self.assertNotIn('private-credential-value',stream.getvalue())
                self.assertIn('BLOCKED',stream.getvalue())
                collect.assert_not_called()

    def test_legacy_and_exact_rows_cannot_be_partially_certified_or_hide_unknown_stock(self):
        old = copy.deepcopy(self.data['wm_material_stock'][0])
        old.update(stock_id='70',quantity_origin='UNPROVEN',quantity_contract_version=None,
                   quantity_source_head_tx_id=None,stock_quantity='9.0000')
        self.data['wm_material_stock'].append(old)
        facts=self.report()['facts']
        self.assertEqual(facts['isolated_stock_rows'],2)
        self.assertEqual(facts['certified_stock_rows'],0)
        self.assertIn('STOCK_ORIGIN_UNPROVEN',self.codes())


if __name__ == '__main__':
    unittest.main()
