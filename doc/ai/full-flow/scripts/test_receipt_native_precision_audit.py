"""七字段原生精度审计的纯内存测试；不连接数据库或外部服务。"""
import copy
from decimal import Decimal, localcontext
import importlib
import importlib.util
import json
import math
import struct
import unittest
from unittest.mock import patch


MODULE = 'receipt_native_precision_audit'
audit = importlib.import_module(MODULE) if importlib.util.find_spec(MODULE) else None
LINE = 'wm_material_receive_line'
DETAIL = 'wm_material_receive_detail'
STOCK = 'wm_material_stock'
TX = 'wm_material_stock_tx'
FIELDS = {
    LINE: ('quantity_received',),
    DETAIL: ('quantity',),
    STOCK: ('stock_quantity', 'reserved_quantity'),
    TX: ('tx_quantity', 'before_quantity', 'after_quantity'),
}
PRIMARY = {LINE: 'line_id', DETAIL: 'detail_id', STOCK: 'stock_id', TX: 'tx_id'}
SCOPE = 'ENTIRE_PHYSICAL_COLUMNS_ALL_TENANTS'


def fixture():
    """原生值与独立行数观察分别提供，空集合不能冒充缺失行。"""
    metadata, rows = {}, {}
    for number, (table, names) in enumerate(FIELDS.items(), 1):
        columns = {
            PRIMARY[table]: {'type': 'bigint', 'precision': 19, 'scale': 0, 'nullable': False},
            'tenant_id': {'type': 'varchar', 'precision': None, 'scale': None, 'nullable': True},
        }
        kind = 'double' if table == STOCK else 'decimal'
        for name in names:
            columns[name] = {'type': kind, 'precision': 22 if kind == 'double' else 12,
                             'scale': None if kind == 'double' else 2, 'nullable': True}
        metadata[table] = {'engine': 'InnoDB', 'tableType': 'BASE TABLE', 'columns': columns}
        rows[table] = [{PRIMARY[table]: 918273645000 + number, 'tenant_id': 'secret-tenant',
                        **{name: 1.25 if kind == 'double' else Decimal('1.25') for name in names}}]
    return {'schema': 'ry-erp', 'metadata': metadata, 'rows': rows,
            'rowCounts': {table: len(values) for table, values in rows.items()}}


class SnapshotAuditTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(audit, '必须交付七字段原生精度审计模块')
        self.snapshot = fixture()

    def report(self):
        return audit.audit_input(self.snapshot)

    def column(self, report, table=STOCK, name='stock_quantity'):
        return report['tables'][table]['columns'][name]

    def test_seven_native_fields_and_no_source_certification(self):
        result = self.report()
        self.assertEqual(result['schema'], 'ry-erp')
        self.assertEqual(result['scope'], SCOPE)
        self.assertEqual(result['targetType'], 'DECIMAL(18,4)')
        self.assertEqual(result['status'], 'EXACTLY_REPRESENTABLE')
        self.assertEqual(result['sourceCertification'], 'NOT_PERFORMED')
        self.assertIs(result['historicalPrecisionRecovered'], False)
        self.assertEqual(result['ddlEligibility'], 'NOT_ASSESSED')
        self.assertEqual(set(result['tables']), set(FIELDS))
        for table, names in FIELDS.items():
            self.assertEqual(set(result['tables'][table]['columns']), set(names))
            for name in names:
                item = self.column(result, table, name)
                self.assertEqual((item['rows'], item['nulls'], item['exactlyRepresentable'], item['rejected']),
                                 (1, 0, 1, 0))
                self.assertEqual(item['nativeType'], self.snapshot['metadata'][table]['columns'][name]['type'])
                self.assertRegex(item['rowSetSha256'], r'^[0-9a-f]{64}$')
        self.assertRegex(result['snapshotSha256'], r'^[0-9a-f]{64}$')

    def test_calls_shared_exact_quantity_with_original_float(self):
        native = importlib.import_module('stock_native_precision_migration')
        with patch.object(audit, 'exact_quantity', wraps=native.exact_quantity) as exact:
            result = self.report()
        self.assertEqual(result['status'], 'EXACTLY_REPRESENTABLE')
        arguments = exact.call_args_list
        self.assertEqual(len(arguments), 7)
        self.assertTrue(any(call.args == (1.25, 'double', 'PRESERVE_NULL_UNKNOWN', True)
                            and type(call.args[0]) is float for call in arguments))

    def test_original_5_4292_double_is_rejected_without_rounding(self):
        value = 5.4292
        self.snapshot['rows'][STOCK][0]['stock_quantity'] = value
        self.assertNotEqual(Decimal.from_float(value), Decimal(str(value)))
        result = self.report()
        item = self.column(result)
        self.assertEqual((item['exactlyRepresentable'], item['rejected']), (0, 1))
        self.assertEqual(item['errorCategories'], {'LOSSY_VALUE': 1})
        self.assertEqual(result['status'], 'REJECTED')
        self.assertEqual(self.snapshot['rows'][STOCK][0]['stock_quantity'].hex(), value.hex())

    def test_decimal_5_4292_is_representable_not_historical_recovery(self):
        column = self.snapshot['metadata'][LINE]['columns']['quantity_received']
        column.update(precision=18, scale=4)
        self.snapshot['rows'][LINE][0]['quantity_received'] = Decimal('5.4292')
        result = self.report()
        self.assertEqual(self.column(result, LINE, 'quantity_received')['exactlyRepresentable'], 1)
        self.assertIs(result['historicalPrecisionRecovered'], False)

    def test_legacy_510_and_origin_flags_never_certify_source(self):
        row = self.snapshot['rows'][STOCK][0]
        row.update(stock_quantity=510.0, stock_quantity_exact=Decimal('510'),
                   quantity_origin='NATIVE_V1', quantity_contract_version=1)
        before = copy.deepcopy(self.snapshot)
        result = self.report()
        self.assertEqual(self.column(result)['exactlyRepresentable'], 1)
        self.assertEqual(result['sourceCertification'], 'NOT_PERFORMED')
        self.assertEqual(result['ddlEligibility'], 'NOT_ASSESSED')
        self.assertEqual(self.snapshot, before)

    def test_nullable_null_is_unknown_not_zero_or_rejected(self):
        self.snapshot['rows'][STOCK][0]['reserved_quantity'] = None
        result = self.report()
        item = self.column(result, STOCK, 'reserved_quantity')
        self.assertEqual((item['rows'], item['nulls'], item['exactlyRepresentable'], item['rejected']),
                         (1, 1, 0, 0))
        self.assertEqual(item['status'], 'UNKNOWN')
        self.assertEqual(result['status'], 'UNKNOWN')
        self.assertEqual(item['errorCategories'], {})

    def test_nonnullable_null_is_rejected(self):
        self.snapshot['metadata'][DETAIL]['columns']['quantity']['nullable'] = False
        self.snapshot['rows'][DETAIL][0]['quantity'] = None
        item = self.column(self.report(), DETAIL, 'quantity')
        self.assertEqual(item['rejected'], 1)
        self.assertEqual(item['errorCategories'], {'NULL_NOT_ALLOWED': 1})

    def test_nonfinite_double_is_rejected_with_fixed_code(self):
        for value in (math.nan, math.inf, -math.inf):
            with self.subTest(value_kind='非有限浮点'):
                self.snapshot['rows'][STOCK][0]['stock_quantity'] = value
                result = self.report()
                self.assertEqual(self.column(result)['errorCategories'], {'LOSSY_VALUE': 1})
                json.dumps(result, allow_nan=False)

    def test_nonfinite_decimal_is_rejected_with_fixed_code(self):
        for value in (Decimal('NaN'), Decimal('sNaN'), Decimal('Infinity'), Decimal('-Infinity')):
            with self.subTest(value_kind='非有限十进制'):
                self.snapshot['rows'][DETAIL][0]['quantity'] = value
                self.assertEqual(self.column(self.report(), DETAIL, 'quantity')['errorCategories'], {'LOSSY_VALUE': 1})

    def test_decimal_requires_native_decimal_not_string_integer_or_float(self):
        for value in ('1.25', 1, True, 1.25, b'1.25'):
            with self.subTest(native_type=type(value).__name__):
                self.snapshot['rows'][DETAIL][0]['quantity'] = value
                item = self.column(self.report(), DETAIL, 'quantity')
                self.assertEqual(item['errorCategories'], {'NATIVE_DECIMAL_VALUE_REQUIRED': 1})
                self.assertEqual(item['rejected'], 1)

    def test_double_requires_actual_float(self):
        for value in ('1.25', 1, True, Decimal('1.25')):
            with self.subTest(native_type=type(value).__name__):
                self.snapshot['rows'][STOCK][0]['stock_quantity'] = value
                self.assertEqual(self.column(self.report())['errorCategories'], {'NATIVE_BINARY_VALUE_REQUIRED': 1})

    def test_scale_and_range_are_checked_without_local_context_rounding(self):
        for value in (Decimal('0.00001'), Decimal('100000000000000'),
                      Decimal('-100000000000000'), math.nextafter(1.25, 2.0), 5e-324):
            with self.subTest(native_type=type(value).__name__):
                table, name = (DETAIL, 'quantity') if isinstance(value, Decimal) else (STOCK, 'stock_quantity')
                self.snapshot['rows'][table][0][name] = value
                with localcontext() as context:
                    context.prec = 3
                    item = self.column(self.report(), table, name)
                self.assertEqual(item['errorCategories'], {'LOSSY_VALUE': 1})
                self.assertEqual(item['rejected'], 1)

    def test_in_range_decimal_boundary_is_exact(self):
        self.snapshot['metadata'][DETAIL]['columns']['quantity'].update(precision=18, scale=4)
        for value in (Decimal('99999999999999.9999'), Decimal('-99999999999999.9999')):
            with self.subTest(boundary='目标边界'):
                self.snapshot['rows'][DETAIL][0]['quantity'] = value
                self.assertEqual(self.column(self.report(), DETAIL, 'quantity')['exactlyRepresentable'], 1)

    def test_mysql_double_conversion_unproven_has_own_fixed_category(self):
        self.snapshot['rows'][STOCK][0]['stock_quantity'] = 12345678901234.25
        item = self.column(self.report())
        self.assertEqual(item['errorCategories'], {'MYSQL_DOUBLE_CONVERSION_UNPROVEN': 1})
        self.assertEqual(item['rejected'], 1)

    def test_main_unknown_or_missing_schema_is_rejected_and_not_echoed(self):
        for schema in ('ry-vue', 'ry-mes', 'ry-iot', 'secret-unknown-schema', None):
            with self.subTest(schema_scope='不受支持'):
                self.snapshot['schema'] = schema
                result = self.report()
                self.assertEqual(result['status'], 'REJECTED')
                self.assertEqual(result['errorCategories'], {'TARGET_SCHEMA_REJECTED': 1})
                self.assertIsNone(result['snapshotSha256'])
                self.assertNotIn('secret-unknown-schema', json.dumps(result))

    def test_missing_column_metadata_is_rejected_not_guessed(self):
        del self.snapshot['metadata'][LINE]['columns']['quantity_received']
        result = self.report()
        item = self.column(result, LINE, 'quantity_received')
        self.assertIn('COLUMN_MISSING', item['errorCategories'])
        self.assertIsNone(item['exactlyRepresentable'])
        self.assertEqual(result['status'], 'REJECTED')

    def test_unsupported_native_type_is_rejected_not_cast(self):
        self.snapshot['metadata'][STOCK]['columns']['stock_quantity']['type'] = 'secret-type'
        result = self.report()
        self.assertIn('SOURCE_TYPE_UNSUPPORTED', self.column(result)['errorCategories'])
        self.assertIsNone(self.column(result)['nativeType'])
        self.assertNotIn('secret-type', json.dumps(result))

    def test_metadata_shape_is_required_not_coerced(self):
        for update in ({'nullable': 'YES'}, {'precision': '18'}, {'scale': '2'},
                       {'precision': 2, 'scale': 4}, {'nullable': None}):
            with self.subTest(shape=tuple(update)):
                self.snapshot = fixture()
                self.snapshot['metadata'][DETAIL]['columns']['quantity'].update(update)
                item = self.column(self.report(), DETAIL, 'quantity')
                self.assertIn('COLUMN_METADATA_REQUIRED', item['errorCategories'])
                self.assertIsNone(item['exactlyRepresentable'])

    def test_missing_rows_missing_quantity_and_unobserved_count_are_unknown_not_zero(self):
        for missing in ('table', 'value', 'count'):
            with self.subTest(missing=missing):
                self.snapshot = fixture()
                if missing == 'table':
                    del self.snapshot['rows'][DETAIL]
                elif missing == 'value':
                    del self.snapshot['rows'][DETAIL][0]['quantity']
                else:
                    del self.snapshot['rowCounts'][DETAIL]
                result = self.report()
                item = self.column(result, DETAIL, 'quantity')
                self.assertIsNone(item['rows'])
                self.assertIsNone(item['nulls'])
                self.assertIsNone(item['exactlyRepresentable'])
                self.assertIsNone(item['rejected'])
                self.assertIsNone(item['rowSetSha256'])
                self.assertNotEqual(result['status'], 'EXACTLY_REPRESENTABLE')

    def test_count_mismatch_or_bool_count_is_not_complete_snapshot(self):
        for count in (0, 2, True, '1', None):
            with self.subTest(count_type=type(count).__name__):
                self.snapshot['rowCounts'][DETAIL] = count
                item = self.column(self.report(), DETAIL, 'quantity')
                self.assertIsNone(item['exactlyRepresentable'])
                self.assertIsNone(item['rowSetSha256'])

    def test_observed_empty_table_is_unknown_not_reliable_zero(self):
        self.snapshot['rows'][DETAIL] = []
        self.snapshot['rowCounts'][DETAIL] = 0
        result = self.report()
        item = self.column(result, DETAIL, 'quantity')
        self.assertEqual(item['rows'], 0)
        self.assertEqual(item['status'], 'UNKNOWN')
        self.assertEqual(result['status'], 'UNKNOWN')
        self.assertIn('NO_ROWS_OBSERVED', item['errorCategories'])

    def test_identity_missing_nonnative_or_duplicate_is_rejected(self):
        for mutation in ('missing_pk', 'missing_tenant', 'text_pk', 'duplicate'):
            with self.subTest(identity=mutation):
                self.snapshot = fixture()
                row = self.snapshot['rows'][STOCK][0]
                if mutation == 'missing_pk':
                    del row['stock_id']
                elif mutation == 'missing_tenant':
                    del row['tenant_id']
                elif mutation == 'text_pk':
                    row['stock_id'] = str(row['stock_id'])
                else:
                    self.snapshot['rows'][STOCK].append(copy.deepcopy(row))
                    self.snapshot['rowCounts'][STOCK] = 2
                result = self.report()
                self.assertEqual(result['status'], 'REJECTED')
                self.assertIsNone(result['tables'][STOCK]['rowSetSha256'])

    def test_nontransactional_table_or_view_is_rejected(self):
        for update in ({'engine': 'MyISAM'}, {'tableType': 'VIEW'}):
            with self.subTest(metadata=tuple(update)):
                self.snapshot = fixture()
                self.snapshot['metadata'][STOCK].update(update)
                self.assertEqual(self.report()['status'], 'REJECTED')

    def test_row_set_hash_is_order_independent_and_identity_sensitive(self):
        second = copy.deepcopy(self.snapshot['rows'][STOCK][0])
        second.update(stock_id=918273645999, tenant_id='another-private-tenant')
        self.snapshot['rows'][STOCK].append(second)
        self.snapshot['rowCounts'][STOCK] = 2
        before = self.report()
        self.snapshot['rows'][STOCK].reverse()
        reordered = self.report()
        self.assertEqual(before['snapshotSha256'], reordered['snapshotSha256'])
        self.snapshot['rows'][STOCK][0]['tenant_id'] = 'changed-private-tenant'
        changed = self.report()
        self.assertNotEqual(reordered['tables'][STOCK]['rowSetSha256'], changed['tables'][STOCK]['rowSetSha256'])

    def test_row_set_hash_preserves_raw_decimal_scale_and_float_negative_zero(self):
        self.snapshot['rows'][DETAIL][0]['quantity'] = Decimal('1.2500')
        before = self.report()
        self.snapshot['rows'][DETAIL][0]['quantity'] = Decimal('1.25')
        after = self.report()
        self.assertNotEqual(before['tables'][DETAIL]['rowSetSha256'], after['tables'][DETAIL]['rowSetSha256'])
        self.snapshot['rows'][STOCK][0]['stock_quantity'] = -0.0
        negative = self.report()
        self.snapshot['rows'][STOCK][0]['stock_quantity'] = 0.0
        positive = self.report()
        self.assertNotEqual(negative['tables'][STOCK]['rowSetSha256'], positive['tables'][STOCK]['rowSetSha256'])

    def test_column_hash_only_changes_for_its_raw_value_or_identity(self):
        before = self.report()
        self.snapshot['rows'][STOCK][0]['stock_quantity'] = 2.5
        after = self.report()
        self.assertNotEqual(self.column(before)['rowSetSha256'], self.column(after)['rowSetSha256'])
        self.assertEqual(self.column(before, STOCK, 'reserved_quantity')['rowSetSha256'],
                         self.column(after, STOCK, 'reserved_quantity')['rowSetSha256'])

    def test_report_never_contains_raw_quantity_ids_tenants_or_extra_source_flags(self):
        self.snapshot['metadata'][DETAIL]['columns']['quantity'].update(precision=18, scale=4)
        self.snapshot['rows'][DETAIL][0].update(quantity=Decimal('987654321.1234'),
                                                create_by='private-user', quantity_origin='private-origin')
        result = self.report()
        output = json.dumps(result, ensure_ascii=False, allow_nan=False)
        for secret in ('987654321.1234', '918273645', 'secret-tenant', 'private-user', 'private-origin'):
            self.assertNotIn(secret, output)
        for code in result['errorCategories']:
            self.assertIn(code, audit.ERROR_CATEGORIES)

    def test_input_is_not_modified(self):
        before = copy.deepcopy(self.snapshot)
        self.report()
        self.assertEqual(self.snapshot, before)

    def test_bad_snapshot_shape_has_only_fixed_errors(self):
        for value in (None, [], 'private-snapshot', {'schema': 'ry-erp'}):
            with self.subTest(shape=type(value).__name__):
                result = audit.audit_input(value)
                self.assertNotEqual(result['status'], 'EXACTLY_REPRESENTABLE')
                self.assertTrue(result['errorCategories'])
                self.assertLessEqual(set(result['errorCategories']), set(audit.ERROR_CATEGORIES))
                self.assertNotIn('private-snapshot', json.dumps(result))

    def test_native_float_type_is_reported_but_unsupported_not_silently_cast(self):
        self.snapshot['metadata'][STOCK]['columns']['stock_quantity']['type'] = 'float'
        item = self.column(self.report())
        self.assertEqual(item['nativeType'], 'float')
        self.assertEqual(item['status'], 'REJECTED')
        self.assertEqual(item['errorCategories'], {'SOURCE_TYPE_UNSUPPORTED': 1})

    def test_raw_float_hash_preserves_distinct_nan_payloads_even_when_rejected(self):
        hashes = []
        for payload in ('7ff8000000000001', '7ff8000000000002'):
            self.snapshot['rows'][STOCK][0]['stock_quantity'] = struct.unpack('>d', bytes.fromhex(payload))[0]
            item = self.column(self.report())
            self.assertEqual(item['errorCategories'], {'LOSSY_VALUE': 1})
            hashes.append(item['rowSetSha256'])
        self.assertNotEqual(hashes[0], hashes[1])

    def test_input_exception_never_exposes_original_metadata_or_quantity(self):
        class PoisonedType:
            def __eq__(self, _):
                raise RuntimeError('private-input-error quantity-secret 987654321.1234')
        self.snapshot['metadata'][STOCK]['columns']['stock_quantity']['type'] = PoisonedType()
        result = self.report()
        self.assertEqual(result['status'], 'REJECTED')
        self.assertEqual(result['errorCategories'], {'SOURCE_TYPE_UNSUPPORTED': 1})
        self.assertNotIn('private-input-error', json.dumps(result))

    def test_nonnative_object_is_rejected_without_repr_or_str(self):
        class PrivateValue:
            def __repr__(self):
                raise RuntimeError('private-value-error quantity-secret')
            __str__ = __repr__
        self.snapshot['rows'][STOCK][0]['stock_quantity'] = PrivateValue()
        result = self.report()
        item = self.column(result)
        self.assertEqual(item['rejected'], 1)
        self.assertIn('NATIVE_BINARY_VALUE_REQUIRED', item['errorCategories'])
        self.assertIn('RAW_VALUE_UNSUPPORTED', item['errorCategories'])
        self.assertIsNone(result['snapshotSha256'])
        self.assertIsNone(item['rowSetSha256'])
        self.assertNotIn('private-value-error', json.dumps(result))

    def test_source_flags_are_not_part_of_raw_quantity_identity_digest(self):
        before = self.report()
        self.snapshot['rows'][STOCK][0].update(quantity_origin='UNPROVEN', quantity_contract_version=None,
                                                stock_quantity_exact=Decimal('510'))
        after = self.report()
        self.assertEqual(before['snapshotSha256'], after['snapshotSha256'])
        self.assertEqual(after['sourceCertification'], 'NOT_PERFORMED')

class FakeCursor:
    """模拟原生二进制游标；任何非 SELECT 请求都使测试失败。"""
    def __init__(self, connection):
        self.connection = connection
        self.values = []
        self.closed = False

    def execute(self, sql, params=None):
        if not sql.lstrip().upper().startswith('SELECT '):
            raise AssertionError('审计只能 SELECT，不得配置或写库')
        self.connection.queries.append((sql, params))
        if self.connection.fail_contains and self.connection.fail_contains in sql:
            raise RuntimeError('private-user secret-password SQL-secret 987654321.1234')
        self.values = self.connection.answer(sql, params)

    def fetchall(self):
        return self.values

    def close(self):
        self.closed = True
        if self.connection.close_failure:
            raise RuntimeError('private-close-error SQL-secret')


class FakeConnection:
    """调用方已经持有的内存连接，绝不创建真实数据库连接。"""
    def __init__(self):
        self.snapshot = fixture()
        self.in_transaction = True
        self.isolation = 'REPEATABLE-READ'
        self.queries = []
        self.cursor_options = []
        self.cursors = []
        self.fail_contains = None
        self.close_failure = False
        self.prepared_supported = True
        self.metadata_round = 0
        self.metadata_change = False
        self.count_queries = {}
        self.changed_count_table = None
        self.environment_round = 0
        self.schema_changes_at_end = False
        self.transaction_ends_at_end = False
        self.truncated_table = None

    def cursor(self, **options):
        self.cursor_options.append(options)
        if options != {'prepared': True} or not self.prepared_supported:
            raise TypeError('private-driver-error 不允许文本协议回退')
        cursor = FakeCursor(self)
        self.cursors.append(cursor)
        return cursor

    def answer(self, sql, params):
        if 'DATABASE()' in sql:
            self.environment_round += 1
            if self.environment_round > 1 and self.transaction_ends_at_end:
                self.in_transaction = False
            schema = 'ry-vue' if self.environment_round > 1 and self.schema_changes_at_end else self.snapshot['schema']
            return [(schema, self.isolation)]
        if 'information_schema.TABLES' in sql:
            return [(self.snapshot['schema'], table, entry['engine'], entry['tableType'])
                    for table, entry in self.snapshot['metadata'].items()]
        if 'information_schema.COLUMNS' in sql:
            self.metadata_round += 1
            values = []
            for table, entry in self.snapshot['metadata'].items():
                for name, spec in entry['columns'].items():
                    scale = 4 if self.metadata_change and self.metadata_round > 1 and table == DETAIL and name == 'quantity' else spec['scale']
                    values.append((self.snapshot['schema'], table, name, spec['type'], spec['precision'], scale,
                                   'YES' if spec['nullable'] else 'NO'))
            return values
        tables = [table for table in FIELDS if '`ry-erp`.`' + table + '`' in sql]
        if len(tables) != 1:
            raise AssertionError('存在未批准的查询目标')
        table = tables[0]
        if ' WHERE ' in sql.upper() or ' LIMIT ' in sql.upper():
            raise AssertionError('物理列审计不得按租户、删除或行数过滤')
        if 'COUNT(*)' in sql:
            self.count_queries[table] = self.count_queries.get(table, 0) + 1
            count = self.snapshot['rowCounts'].get(table)
            if self.changed_count_table == table and self.count_queries[table] > 1:
                count += 1
            return [(count,)]
        names = (PRIMARY[table], 'tenant_id', *FIELDS[table])
        values = self.snapshot['rows'][table]
        if self.truncated_table == table:
            values = values[:-1]
        return [tuple(row[name] for name in names) for row in values]

    def close(self):
        raise AssertionError('连接所有权属于调用方，审计不得关闭连接')

    def commit(self):
        raise AssertionError('审计不得提交调用方事务')

    def rollback(self):
        raise AssertionError('审计不得回滚调用方事务')

    def start_transaction(self, **_):
        raise AssertionError('事务必须由调用方预先验证并开启')


class ConnectionAuditTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(audit, '必须交付七字段原生精度审计模块')
        self.assertTrue(callable(getattr(audit, 'audit_connection', None)), '必须提供调用方连接入口')
        self.connection = FakeConnection()

    def report(self):
        return audit.audit_connection(self.connection)

    def test_prepared_readonly_snapshot_equals_pure_memory_audit(self):
        result = self.report()
        self.assertEqual(result, audit.audit_input(self.connection.snapshot))
        self.assertTrue(self.connection.queries)
        self.assertTrue(all(options == {'prepared': True} for options in self.connection.cursor_options))
        self.assertTrue(all(cursor.closed for cursor in self.connection.cursors))
        self.assertTrue(self.connection.in_transaction)

    def test_entire_physical_columns_include_other_tenants_and_deleted_rows(self):
        other = copy.deepcopy(self.connection.snapshot['rows'][STOCK][0])
        other.update(stock_id=918273645888, tenant_id='other-private-tenant', del_flag='1', stock_quantity=5.4292)
        self.connection.snapshot['rows'][STOCK].append(other)
        self.connection.snapshot['rowCounts'][STOCK] = 2
        result = self.report()
        item = result['tables'][STOCK]['columns']['stock_quantity']
        self.assertEqual((item['rows'], item['exactlyRepresentable'], item['rejected']), (2, 1, 1))
        self.assertEqual(result['scope'], SCOPE)
        physical_queries = [sql for sql, _ in self.connection.queries if '`ry-erp`.' in sql]
        self.assertTrue(physical_queries)
        self.assertTrue(all(' WHERE ' not in sql.upper() and ' LIMIT ' not in sql.upper() for sql in physical_queries))
        self.assertTrue(all('_exact`' not in sql and 'quantity_origin' not in sql for sql in physical_queries))

    def test_main_or_unknown_schema_is_rejected_before_metadata_or_rows(self):
        for schema in ('ry-vue', 'private-unknown-schema', None):
            with self.subTest(scope='非 ERP'):
                self.connection = FakeConnection()
                self.connection.snapshot['schema'] = schema
                result = self.report()
                self.assertEqual(result['errorCategories'], {'TARGET_SCHEMA_REJECTED': 1})
                self.assertEqual(len(self.connection.queries), 1)
                self.assertNotIn('private-unknown-schema', json.dumps(result))

    def test_no_active_transaction_is_rejected_without_configuring_it(self):
        for state in (False, None, 1):
            with self.subTest(state_type=type(state).__name__):
                self.connection = FakeConnection()
                self.connection.in_transaction = state
                result = self.report()
                self.assertEqual(result['errorCategories'], {'CONNECTION_SNAPSHOT_REQUIRED': 1})
                self.assertEqual(self.connection.queries, [])
        del self.connection.in_transaction
        self.assertEqual(self.report()['errorCategories'], {'CONNECTION_SNAPSHOT_REQUIRED': 1})

    def test_non_rr_isolation_is_rejected_without_start_or_set(self):
        for isolation in ('READ-COMMITTED', None, 'private-isolation'):
            with self.subTest(isolation_scope='未验证 RR'):
                self.connection = FakeConnection()
                self.connection.isolation = isolation
                result = self.report()
                self.assertEqual(result['errorCategories'], {'CONNECTION_SNAPSHOT_REQUIRED': 1})
                self.assertEqual(len(self.connection.queries), 1)
                self.assertNotIn('private-isolation', json.dumps(result))

    def test_missing_native_driver_has_no_text_protocol_fallback(self):
        self.connection.prepared_supported = False
        result = self.report()
        self.assertEqual(result['errorCategories'], {'NATIVE_DRIVER_REQUIRED': 1})
        self.assertEqual(self.connection.cursor_options, [{'prepared': True}])

    def test_sql_errors_are_suppressed_and_never_leak_credentials_or_quantities(self):
        for statement in ('DATABASE()', 'information_schema.COLUMNS', '`wm_material_stock`'):
            with self.subTest(query_kind='固定读取失败'):
                self.connection = FakeConnection()
                self.connection.fail_contains = statement
                result = self.report()
                self.assertEqual(result['errorCategories'], {'DATABASE_READ_FAILED': 1})
                self.assertEqual(result['status'], 'REJECTED')
                self.assertIsNone(result['snapshotSha256'])
                self.assertTrue(all(cursor.closed for cursor in self.connection.cursors))
                for secret in ('private-user', 'secret-password', 'SQL-secret', '987654321.1234'):
                    self.assertNotIn(secret, json.dumps(result))

    def test_cursor_close_error_is_suppressed_without_closing_connection(self):
        self.connection.close_failure = True
        result = self.report()
        self.assertEqual(result['errorCategories'], {'DATABASE_READ_FAILED': 1})
        self.assertNotIn('private-close-error', json.dumps(result))

    def test_missing_column_is_rejected_before_any_physical_data_read(self):
        del self.connection.snapshot['metadata'][LINE]['columns']['quantity_received']
        result = self.report()
        self.assertEqual(result['status'], 'REJECTED')
        self.assertIn('COLUMN_MISSING', result['errorCategories'])
        self.assertFalse(any('`ry-erp`.' in sql for sql, _ in self.connection.queries))

    def test_metadata_before_and_after_must_match(self):
        self.connection.metadata_change = True
        result = self.report()
        self.assertEqual(result['errorCategories'], {'METADATA_CHANGED': 1})
        self.assertIsNone(result['snapshotSha256'])

    def test_native_text_numeric_is_rejected_even_if_driver_claims_prepared(self):
        self.connection.snapshot['rows'][DETAIL][0]['quantity'] = '1.25'
        result = self.report()
        self.assertEqual(result['tables'][DETAIL]['columns']['quantity']['errorCategories'],
                         {'NATIVE_DECIMAL_VALUE_REQUIRED': 1})

    def test_row_truncation_is_unknown_not_reliable_zero(self):
        self.connection.truncated_table = DETAIL
        result = self.report()
        item = result['tables'][DETAIL]['columns']['quantity']
        self.assertIsNone(item['rows'])
        self.assertIsNone(item['exactlyRepresentable'])
        self.assertIsNone(item['rowSetSha256'])
        self.assertIn('ROW_COUNT_MISMATCH', item['errorCategories'])

    def test_count_is_crosschecked_before_and_after_all_table_reads(self):
        result = self.report()
        self.assertEqual(result['status'], 'EXACTLY_REPRESENTABLE')
        self.assertEqual(self.connection.count_queries, {table: 2 for table in FIELDS})

    def test_count_change_is_not_a_complete_snapshot(self):
        self.connection.changed_count_table = STOCK
        result = self.report()
        self.assertNotEqual(result['status'], 'EXACTLY_REPRESENTABLE')
        self.assertIn('ROW_COUNT_MISMATCH', result['errorCategories'])
        self.assertIsNone(result['snapshotSha256'])

    def test_schema_switch_at_end_is_rejected_not_a_snapshot(self):
        self.connection.schema_changes_at_end = True
        result = self.report()
        self.assertEqual(result['errorCategories'], {'TARGET_SCHEMA_REJECTED': 1})
        self.assertIsNone(result['snapshotSha256'])

    def test_transaction_ending_mid_read_is_rejected(self):
        self.connection.transaction_ends_at_end = True
        result = self.report()
        self.assertEqual(result['errorCategories'], {'CONNECTION_SNAPSHOT_REQUIRED': 1})
        self.assertIsNone(result['snapshotSha256'])

    def test_related_identity_columns_in_metadata_do_not_expand_or_break_audit_scope(self):
        # 收货明细真实存在 line_id；跨表列名并集会读到它，但它不是本表受审原生身份。
        self.connection.snapshot['metadata'][DETAIL]['columns']['line_id'] = {
            'type': 'bigint', 'precision': 19, 'scale': 0, 'nullable': True,
        }
        result = self.report()
        self.assertEqual(result['status'], 'EXACTLY_REPRESENTABLE')
        self.assertEqual(set(result['tables'][DETAIL]['columns']), {'quantity'})
        self.assertEqual(result, audit.audit_input(self.connection.snapshot))

class NativeMetadataBoundaryReviewTests(unittest.TestCase):
    """未知身份定义或伪装相等的类型对象不能形成完整原生观察。"""
    def test_missing_unknown_identity_nullable_rejects_snapshot(self):
        for name in ('stock_id','tenant_id'):
            for value in ('MISSING',None,0,'NO'):
                snapshot=fixture()
                if value=='MISSING':snapshot['metadata'][STOCK]['columns'][name].pop('nullable')
                else:snapshot['metadata'][STOCK]['columns'][name]['nullable']=value
                with self.subTest(name=name,value=value):
                    result=audit.audit_input(snapshot)
                    self.assertEqual('REJECTED',result['status'])
                    self.assertIsNone(result['tables'][STOCK]['rowSetSha256'])
    def test_nonnullable_tenant_null_is_not_complete_native_identity(self):
        snapshot=fixture();snapshot['metadata'][STOCK]['columns']['tenant_id']['nullable']=False
        snapshot['rows'][STOCK][0]['tenant_id']=None
        result=audit.audit_input(snapshot)
        self.assertEqual('REJECTED',result['status'])
        self.assertIsNone(result['tables'][STOCK]['rowSetSha256'])
    def test_spoofed_dtype_is_rejected_and_never_echoed(self):
        class UnknownType(str):
            def __eq__(self,other):return other=='decimal'
        snapshot=fixture()
        snapshot['metadata'][LINE]['columns']['quantity_received']['type']=UnknownType('SYNTHETIC_PRIVATE_METADATA')
        result=audit.audit_input(snapshot)
        self.assertEqual('REJECTED',result['status'])
        self.assertNotIn('SYNTHETIC_PRIVATE_METADATA',json.dumps(result))

if __name__ == '__main__':
    unittest.main()
