"""固定 ry-erp 七个原生数量列的无损表达审计，不认证历史来源。

接口 audit_input(snapshot) 只接受内存快照，不做数据库调用。快照形状为：
  schema: 'ry-erp'
  metadata: {表名: {engine: 'InnoDB', tableType: 'BASE TABLE',
      columns: {列名: {type: 原生 DATA_TYPE, precision: int 或 None,
                       scale: int 或 None, nullable: bool}}}}
  rows: {表名: [{原生主键: int, tenant_id: str 或 None, 原数量列: 原生值}]}
  rowCounts: {表名: 独立观察的 COUNT(*) 非负 int}
每张表必须包含原生主键、tenant_id 及该表受审数量列的元数据和完整行集合。
DOUBLE 必须是真实 float，DECIMAL 必须是 Decimal；不得先字符串化、CAST 或舍入。

接口 audit_connection(conn) 使用调用方已验证 target/tenant/auth 且已开启 READ ONLY
REPEATABLE READ 一致性事务的连接，要求支持 cursor(prepared=True) 原生二进制协议。
本模块只回读 schema、会话 RR 与活动事务；READ ONLY 的实际事务属性及外部授权由
调用方负责，不把会话 read_only 值或传入的布尔值当成认证。连接及事务所有权不转移。

整个物理列包含所有租户与已删除行，不限单租户。NULL 保留未知；缺行、缺观察和
空集合不能证明可靠零。只返回统计、固定错误类别、原始带类型身份/值的行集合摘要。
不返回数量、主键、租户、用户、来源 flags 或数据库错误原文。
审计不负责登录、授权、事务配置、DDL/DML、服务调用或自动迁移；也不检查默认值、
索引/触发器/外键、维护窗口或迁移放行条件。可表达不等于历史来源已认证。
"""
from __future__ import annotations

from decimal import Decimal
import hashlib
import json
import struct
from types import MappingProxyType

from stock_native_precision_migration import MigrationBlocked, exact_quantity


SCHEMA = 'ry-erp'
SCOPE = 'ENTIRE_PHYSICAL_COLUMNS_ALL_TENANTS'
NATIVE_COLUMNS = MappingProxyType({
    'wm_material_receive_line': ('quantity_received',),
    'wm_material_receive_detail': ('quantity',),
    'wm_material_stock': ('stock_quantity', 'reserved_quantity'),
    'wm_material_stock_tx': ('tx_quantity', 'before_quantity', 'after_quantity'),
})
PRIMARY_KEYS = MappingProxyType({
    'wm_material_receive_line': 'line_id',
    'wm_material_receive_detail': 'detail_id',
    'wm_material_stock': 'stock_id',
    'wm_material_stock_tx': 'tx_id',
})
ERROR_CATEGORIES = (
    'TARGET_SCHEMA_REJECTED', 'SNAPSHOT_SHAPE_REJECTED', 'TABLE_METADATA_REQUIRED',
    'TRANSACTIONAL_BASE_TABLE_REQUIRED', 'COLUMN_MISSING', 'COLUMN_METADATA_REQUIRED',
    'SOURCE_TYPE_UNSUPPORTED', 'ROWS_MISSING', 'ROW_COUNT_UNVERIFIED', 'ROW_COUNT_MISMATCH',
    'ROW_VALUE_MISSING', 'NO_ROWS_OBSERVED', 'ROW_IDENTITY_REQUIRED', 'ROW_DUPLICATE',
    'NATIVE_BINARY_VALUE_REQUIRED', 'NATIVE_DECIMAL_VALUE_REQUIRED', 'NULL_NOT_ALLOWED',
    'LOSSY_VALUE', 'MYSQL_DOUBLE_CONVERSION_UNPROVEN', 'RAW_VALUE_UNSUPPORTED',
    'CONNECTION_SNAPSHOT_REQUIRED', 'NATIVE_DRIVER_REQUIRED', 'DATABASE_READ_FAILED',
    'METADATA_CHANGED', 'INTERNAL_AUDIT_FAILED',
)
# 只回显固定原生类型名；未知或伪造的元数据文本不进入报告。
_KNOWN_NATIVE_TYPES = (
    'decimal', 'double', 'float', 'bigint', 'int', 'mediumint', 'smallint', 'tinyint', 'bit',
    'varchar', 'char', 'text', 'binary', 'varbinary', 'blob', 'datetime', 'timestamp', 'date', 'time', 'json',
)
_UNKNOWN_CATEGORIES = frozenset({
    'ROWS_MISSING', 'ROW_COUNT_UNVERIFIED', 'ROW_COUNT_MISMATCH', 'ROW_VALUE_MISSING',
    'NO_ROWS_OBSERVED',
})


def _new_report():
    tables = {}
    for table, names in NATIVE_COLUMNS.items():
        columns = {name: {
            'nativeType': None, 'precision': None, 'scale': None, 'nullable': None,
            'rows': None, 'nulls': None, 'exactlyRepresentable': None, 'rejected': None,
            'rowSetSha256': None, 'status': 'UNKNOWN', 'errorCategories': {},
        } for name in names}
        tables[table] = {'rows': None, 'rowSetSha256': None, 'status': 'UNKNOWN',
                         'errorCategories': {}, 'columns': columns}
    return {
        'schema': SCHEMA, 'scope': SCOPE, 'targetType': 'DECIMAL(18,4)',
        'status': 'UNKNOWN', 'sourceCertification': 'NOT_PERFORMED',
        'historicalPrecisionRecovered': False, 'ddlEligibility': 'NOT_ASSESSED',
        'errorCategories': {}, 'snapshotSha256': None, 'tables': tables,
    }


def _issue(report, code, table=None, column=None):
    # 不接受外部错误文字或动态类别，更不把 SQL 错误拼入报告。
    code = code if code in ERROR_CATEGORIES else 'INTERNAL_AUDIT_FAILED'
    destinations = [report]
    if table is not None:
        destinations.append(report['tables'][table])
        if column is not None:
            destinations.append(report['tables'][table]['columns'][column])
    for item in destinations:
        errors = item['errorCategories']
        errors[code] = errors.get(code, 0) + 1


def _table_issue(report, table, code):
    for name in NATIVE_COLUMNS[table]:
        _issue(report, code, table, name)


def _json_bytes(value):
    return json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(',', ':'),
                      allow_nan=False).encode('ascii')


def _typed_raw(value):
    """只在内存编码原生值，保留 Decimal 原指数、浮点二进制及带类型身份。"""
    if value is None:
        return ['null']
    if isinstance(value, Decimal):
        sign, digits, exponent = value.as_tuple()
        return ['decimal', sign, list(digits), exponent]
    if type(value) is float:
        return ['double', struct.pack('>d', value).hex()]
    if type(value) in (int, str, bool):
        return [type(value).__name__, value]
    if type(value) in (bytes, bytearray):
        return [type(value).__name__, value.hex()]
    raise MigrationBlocked('RAW_VALUE_UNSUPPORTED')


def _row_set_hash(encoded_rows):
    # 排序后带长度编码，行顺序不影响集合摘要，也不把重复行当作同一集合。
    digest = hashlib.sha256()
    for raw in sorted(encoded_rows):
        digest.update(str(len(raw)).encode('ascii') + b':' + raw)
    return digest.hexdigest()


def _quantity_spec(spec):
    if type(spec) is not dict or type(spec.get('nullable')) is not bool:
        return 'COLUMN_METADATA_REQUIRED'
    kind = spec.get('type')
    if type(kind) is not str or kind not in ('decimal', 'double'):
        return 'SOURCE_TYPE_UNSUPPORTED'
    precision, scale = spec.get('precision'), spec.get('scale')
    if type(precision) is not int or not 1 <= precision <= 65:
        return 'COLUMN_METADATA_REQUIRED'
    if kind == 'decimal':
        if type(scale) is not int or not 0 <= scale <= min(30, precision):
            return 'COLUMN_METADATA_REQUIRED'
    elif scale is not None and (type(scale) is not int or not 0 <= scale <= 30):
        return 'COLUMN_METADATA_REQUIRED'
    return None


def _table_specs(report, table, metadata):
    entry = metadata.get(table)
    if type(entry) is not dict or type(entry.get('columns')) is not dict:
        _table_issue(report, table, 'TABLE_METADATA_REQUIRED')
        return None
    if (type(entry.get('engine')) is not str or type(entry.get('tableType')) is not str
            or entry.get('engine') != 'InnoDB' or entry.get('tableType') != 'BASE TABLE'):
        _table_issue(report, table, 'TRANSACTIONAL_BASE_TABLE_REQUIRED')
        return None
    columns = entry['columns']
    valid = True
    for name, kinds in ((PRIMARY_KEYS[table], ('bigint',)), ('tenant_id', ('varchar', 'char'))):
        identity = columns.get(name)
        if (type(identity) is not dict or type(identity.get('type')) is not str
                or identity.get('type') not in kinds or type(identity.get('nullable')) is not bool
                or (name == PRIMARY_KEYS[table] and identity['nullable'])):
            _table_issue(report, table, 'ROW_IDENTITY_REQUIRED')
            valid = False
    quantities = {}
    for name in NATIVE_COLUMNS[table]:
        if name not in columns:
            _issue(report, 'COLUMN_MISSING', table, name)
            valid = False
            continue
        spec = columns[name]
        if type(spec) is dict and type(spec.get('type')) is str and spec['type'] in _KNOWN_NATIVE_TYPES:
            report['tables'][table]['columns'][name]['nativeType'] = spec['type']
        error = _quantity_spec(spec)
        if error is not None:
            _issue(report, error, table, name)
            valid = False
            continue
        item = report['tables'][table]['columns'][name]
        item.update(nativeType=spec['type'], precision=spec['precision'], scale=spec['scale'],
                    nullable=spec['nullable'])
        quantities[name] = spec
    return quantities if valid else None


def _complete_rows(report, table, rows, counts, identity_columns):
    if table not in rows or type(rows[table]) not in (list, tuple):
        _table_issue(report, table, 'ROWS_MISSING')
        return None
    count = counts.get(table)
    if type(count) is not int or count < 0:
        _table_issue(report, table, 'ROW_COUNT_UNVERIFIED')
        return None
    values = rows[table]
    if len(values) != count:
        _table_issue(report, table, 'ROW_COUNT_MISMATCH')
        return None
    seen = set()
    primary = PRIMARY_KEYS[table]
    for row in values:
        if (type(row) is not dict or type(row.get(primary)) is not int or 'tenant_id' not in row
                or (row['tenant_id'] is not None and type(row['tenant_id']) is not str)):
            _table_issue(report, table, 'ROW_IDENTITY_REQUIRED')
            return None
        # 物理身份可以保留已声明的NULL未知，但与非可空定义矛盾时不能形成完整观察。
        if row['tenant_id'] is None and not identity_columns['tenant_id']['nullable']:
            _table_issue(report, table, 'ROW_IDENTITY_REQUIRED')
            return None
        if row[primary] in seen:
            _table_issue(report, table, 'ROW_DUPLICATE')
            return None
        seen.add(row[primary])
        if any(name not in row for name in NATIVE_COLUMNS[table]):
            _table_issue(report, table, 'ROW_VALUE_MISSING')
            return None
    return values


def _audit_rows(report, table, specs, rows):
    entry = report['tables'][table]
    entry['rows'] = len(rows)
    raw_table_rows = []
    raw_columns = {name: [] for name in specs}
    for item in entry['columns'].values():
        item.update(rows=len(rows), nulls=0, exactlyRepresentable=0, rejected=0)
    if not rows:
        _table_issue(report, table, 'NO_ROWS_OBSERVED')
    for row in rows:
        identity = [_typed_raw(row[PRIMARY_KEYS[table]]), _typed_raw(row['tenant_id'])]
        raw_values = []
        for name, spec in specs.items():
            value = row[name]
            item = entry['columns'][name]
            item['nulls'] += int(value is None)
            try:
                # 必须直接调用共享函数；其内部用 Decimal.from_float 校验真实浮点。
                exact = exact_quantity(value, spec['type'], 'PRESERVE_NULL_UNKNOWN', spec['nullable'])
                item['exactlyRepresentable'] += int(exact is not None)
            except MigrationBlocked as error:
                item['rejected'] += 1
                _issue(report, error.code, table, name)
            try:
                raw = _typed_raw(value)
            except MigrationBlocked:
                raw_columns[name] = None
                raw_values.append(None)
                _issue(report, 'RAW_VALUE_UNSUPPORTED', table, name)
                continue
            raw_values.append([name, raw])
            if raw_columns[name] is not None:
                raw_columns[name].append(_json_bytes([identity, name, raw]))
        raw_table_rows.append(_json_bytes([identity, raw_values]))
    if all(values is not None for values in raw_columns.values()):
        entry['rowSetSha256'] = _row_set_hash(raw_table_rows)
    for name, encoded in raw_columns.items():
        if encoded is not None:
            entry['columns'][name]['rowSetSha256'] = _row_set_hash(encoded)


def _status(errors, *, unknown=False):
    if any(code not in _UNKNOWN_CATEGORIES for code in errors):
        return 'REJECTED'
    if errors or unknown:
        return 'UNKNOWN'
    return 'EXACTLY_REPRESENTABLE'


def _finish(report):
    for entry in report['tables'].values():
        for item in entry['columns'].values():
            item['status'] = _status(item['errorCategories'],
                                     unknown=item['rows'] is None or item['rows'] == 0 or bool(item['nulls']))
        entry['status'] = _status(entry['errorCategories'], unknown=any(
            item['status'] != 'EXACTLY_REPRESENTABLE' for item in entry['columns'].values()))
    report['status'] = _status(report['errorCategories'], unknown=any(
        entry['status'] != 'EXACTLY_REPRESENTABLE' for entry in report['tables'].values()))
    if all(entry['rowSetSha256'] is not None for entry in report['tables'].values()):
        # 摘要只绑定本次受审原列与原始身份；不包含、也不认证旧来源 flags。
        report['snapshotSha256'] = hashlib.sha256(_json_bytes({
            'schema': SCHEMA, 'scope': SCOPE, 'tables': report['tables'],
        })).hexdigest()
    return report


def _audit_input(snapshot):
    """纯内存入口；调用者必须提供全物理列原生快照及独立行数，不接受 JSON 数值替身。"""
    report = _new_report()
    if type(snapshot) is not dict:
        _issue(report, 'SNAPSHOT_SHAPE_REJECTED')
        return _finish(report)
    if snapshot.get('schema') != SCHEMA:
        _issue(report, 'TARGET_SCHEMA_REJECTED')
        return _finish(report)
    metadata, rows, counts = (snapshot.get(name) for name in ('metadata', 'rows', 'rowCounts'))
    if type(metadata) is not dict or type(rows) is not dict or type(counts) is not dict:
        _issue(report, 'SNAPSHOT_SHAPE_REJECTED')
        return _finish(report)
    if any(set(value) - set(NATIVE_COLUMNS) for value in (metadata, rows, counts)):
        _issue(report, 'SNAPSHOT_SHAPE_REJECTED')
        return _finish(report)
    for table in NATIVE_COLUMNS:
        specs = _table_specs(report, table, metadata)
        if specs is None:
            continue
        values = _complete_rows(report, table, rows, counts, metadata[table]['columns'])
        if values is not None:
            _audit_rows(report, table, specs, values)
    return _finish(report)


def audit_input(snapshot):
    """纯内存入口，连异常对象也只返回固定类别，不附带原生行值或异常原文。"""
    try:
        return _audit_input(snapshot)
    except Exception:
        report = _new_report()
        _issue(report, 'INTERNAL_AUDIT_FAILED')
        return _finish(report)

def _select(conn, sql, params=None):
    """只使用调用方的 prepared 游标，不连接、不配置、不回退文本协议。"""
    try:
        cursor = conn.cursor(prepared=True)
    except Exception:
        raise MigrationBlocked('NATIVE_DRIVER_REQUIRED') from None
    try:
        cursor.execute(sql, params)
        values = cursor.fetchall()
        if type(values) not in (list, tuple) or any(type(row) not in (list, tuple) for row in values):
            raise MigrationBlocked('DATABASE_READ_FAILED')
        return values
    except Exception:
        raise MigrationBlocked('DATABASE_READ_FAILED') from None
    finally:
        try:
            cursor.close()
        except Exception:
            raise MigrationBlocked('DATABASE_READ_FAILED') from None


def _connection_state(conn):
    if getattr(conn, 'in_transaction', None) is not True:
        raise MigrationBlocked('CONNECTION_SNAPSHOT_REQUIRED')
    values = _select(conn, 'SELECT DATABASE(), @@SESSION.tx_isolation')
    if len(values) != 1 or len(values[0]) != 2:
        raise MigrationBlocked('CONNECTION_SNAPSHOT_REQUIRED')
    schema, isolation = values[0]
    if schema != SCHEMA:
        raise MigrationBlocked('TARGET_SCHEMA_REJECTED')
    if isolation not in ('REPEATABLE-READ', 'REPEATABLE READ'):
        raise MigrationBlocked('CONNECTION_SNAPSHOT_REQUIRED')
    if getattr(conn, 'in_transaction', None) is not True:
        raise MigrationBlocked('CONNECTION_SNAPSHOT_REQUIRED')


def _read_metadata(conn):
    """只取四张物理表、七个原列及原生身份的必要元数据，首尾交叉核对。"""
    tables = tuple(NATIVE_COLUMNS)
    placeholders = ','.join('%s' for _ in tables)
    table_rows = _select(conn,
        'SELECT TABLE_SCHEMA,TABLE_NAME,ENGINE,TABLE_TYPE FROM information_schema.TABLES '
        f'WHERE TABLE_SCHEMA=%s AND TABLE_NAME IN ({placeholders}) ORDER BY TABLE_NAME',
        (SCHEMA, *tables))
    metadata = {}
    for row in table_rows:
        if len(row) != 4:
            raise MigrationBlocked('TABLE_METADATA_REQUIRED')
        schema, table, engine, table_type = row
        if schema != SCHEMA or table not in NATIVE_COLUMNS:
            raise MigrationBlocked('TARGET_SCHEMA_REJECTED')
        if table in metadata:
            raise MigrationBlocked('TABLE_METADATA_REQUIRED')
        metadata[table] = {'engine': engine, 'tableType': table_type, 'columns': {}}
    names = tuple(dict.fromkeys(name for table, quantities in NATIVE_COLUMNS.items()
                               for name in (PRIMARY_KEYS[table], 'tenant_id', *quantities)))
    column_placeholders = ','.join('%s' for _ in names)
    column_rows = _select(conn,
        'SELECT TABLE_SCHEMA,TABLE_NAME,COLUMN_NAME,DATA_TYPE,NUMERIC_PRECISION,NUMERIC_SCALE,IS_NULLABLE '
        'FROM information_schema.COLUMNS '
        f'WHERE TABLE_SCHEMA=%s AND TABLE_NAME IN ({placeholders}) '
        f'AND COLUMN_NAME IN ({column_placeholders}) ORDER BY TABLE_NAME,ORDINAL_POSITION',
        (SCHEMA, *tables, *names))
    for row in column_rows:
        if len(row) != 7:
            raise MigrationBlocked('COLUMN_METADATA_REQUIRED')
        schema, table, name, kind, precision, scale, nullable = row
        if schema != SCHEMA or table not in NATIVE_COLUMNS:
            raise MigrationBlocked('TARGET_SCHEMA_REJECTED')
        allowed = (PRIMARY_KEYS[table], 'tenant_id', *NATIVE_COLUMNS[table])
        if table not in metadata:
            raise MigrationBlocked('COLUMN_METADATA_REQUIRED')
        # 并集查询会读到明细的 line_id 等跨表同名列；它们不属于本表受审列。
        if name not in allowed:
            continue
        if nullable not in ('YES', 'NO'):
            raise MigrationBlocked('COLUMN_METADATA_REQUIRED')
        if name in metadata[table]['columns']:
            raise MigrationBlocked('COLUMN_METADATA_REQUIRED')
        metadata[table]['columns'][name] = {
            'type': kind, 'precision': precision, 'scale': scale, 'nullable': nullable == 'YES',
        }
    return metadata


def _read_count(conn, table):
    values = _select(conn, f'SELECT COUNT(*) FROM `ry-erp`.`{table}`')
    if (len(values) != 1 or len(values[0]) != 1 or type(values[0][0]) is not int
            or values[0][0] < 0):
        raise MigrationBlocked('ROW_COUNT_UNVERIFIED')
    return values[0][0]


def audit_connection(conn):
    """审计已验证且开启 READ ONLY RR 的调用方连接；绝不认证来源或提供迁移放行。"""
    try:
        _connection_state(conn)
        metadata = _read_metadata(conn)
        precheck = _new_report()
        valid = True
        for table in NATIVE_COLUMNS:
            if _table_specs(precheck, table, metadata) is None:
                valid = False
        if not valid:
            return _finish(precheck)
        counts, rows = {}, {}
        for table, quantities in NATIVE_COLUMNS.items():
            counts[table] = _read_count(conn, table)
            names = (PRIMARY_KEYS[table], 'tenant_id', *quantities)
            fields = ','.join('`' + name + '`' for name in names)
            # DDL 影响全物理列，因此不加租户、del_flag、数量或 LIMIT 过滤。
            values = _select(conn,
                f'SELECT {fields} FROM `ry-erp`.`{table}` ORDER BY `{PRIMARY_KEYS[table]}`')
            if any(len(row) != len(names) for row in values):
                raise MigrationBlocked('ROW_VALUE_MISSING')
            rows[table] = [dict(zip(names, row)) for row in values]
        for table in NATIVE_COLUMNS:
            if _read_count(conn, table) != counts[table]:
                raise MigrationBlocked('ROW_COUNT_MISMATCH')
        _connection_state(conn)
        if _read_metadata(conn) != metadata:
            raise MigrationBlocked('METADATA_CHANGED')
        return audit_input({'schema': SCHEMA, 'metadata': metadata, 'rows': rows, 'rowCounts': counts})
    except MigrationBlocked as error:
        report = _new_report()
        _issue(report, error.code)
        return _finish(report)
    except Exception:
        # 驱动属性、游标、行形状等意外错误也只给固定类别，不能泄露远端错误原文。
        report = _new_report()
        _issue(report, 'DATABASE_READ_FAILED')
        return _finish(report)
