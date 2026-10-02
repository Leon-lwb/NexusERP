"""本地只读库存精度/历史来源诊断，绝不签发采购或库存写入认证。

范围：dev、loopback TCP3306、ry-erp 和显式租户；沿 inventory_local3306 的
SELECT-only 授权、单连接 RR/READ ONLY 快照及脱敏错误风格。完整租户行无 LIMIT，
不过滤删除/旧合同；不 CAST 浮点量，不用当前单位修复历史，不输出修复 SQL。
仅识别 WM_MATERIAL_RECEIVE 的头/行/实际上架明细来源，其他业务类型明确阻断，
不冒充完整业务 verifier。收货实际 quantity 的 DOUBLE 也要独立报告。

所有输出均 BLOCKED/authorizes_writes=false：即使候选合同/连续量/所有者引用齐全，
仍须主线程接入受控新写服务和事务内独立业务 verifier。此文件不修改 A 的 verifier。
原始记录只在进程内检查；忽略目录报告只有计数、原生类型和固定原因码，无凭据/ID/
单号/姓名/实际数量/证据摘要/配置。普通运行需要进程环境 MYSQL_USER/MYSQL_PWD，
禁止 root 或 admin 升格。未授权的实际数据库访问不由本脚本自动触发。
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone, timedelta
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import re
import sys

from inventory_local3306 import _client_args, _run, _safe_select, grants_select_only
from preflight_local3306 import validate_runtime_args

ROOT = Path(__file__).resolve().parents[4]
OUTPUT = ROOT / '.codex/full-flow-20260928/inventory-certification-audit.json'
COMMON = ('quantity_contract_version', 'quantity_origin', 'quantity_uom_version', 'quantity_evidence_hash')
COLUMNS = {
    'sys_tenant': ('tenant_id', 'status', 'del_flag'),
    'bd_material': ('tenant_id', 'material_id', 'base_uom_id', 'safety_stock', 'status', 'del_flag'),
    'bd_unit_of_measure': ('tenant_id', 'uom_id', 'is_base', 'primary_id', 'conversion_rate', 'quantity_scale',
                           'conversion_version', 'conversion_verified_at', 'status', 'del_flag'),
    'wm_warehouse': ('tenant_id', 'warehouse_id', 'status', 'del_flag'),
    'wm_storage_area': ('tenant_id', 'area_id', 'warehouse_id', 'enable_flag', 'frozen_flag', 'location_flag', 'del_flag'),
    'wm_storage_location': ('tenant_id', 'location_id', 'warehouse_id', 'area_id', 'enable_flag', 'frozen_flag', 'del_flag'),
    'wm_material_stock': ('tenant_id', 'stock_id', 'material_id', 'base_uom_id', 'warehouse_id', 'area_id', 'location_id',
        'stock_quantity', 'reserved_quantity', 'stock_quantity_exact', 'reserved_quantity_exact', 'frozen_flag', 'version',
        'del_flag', *COMMON, 'quantity_version', 'reservation_version', 'quantity_source_head_tx_id',
        'reservation_head_event_id', 'quantity_source_at'),
    'wm_material_stock_tx': ('tenant_id', 'tx_id', 'material_stock_id', 'material_id', 'base_uom_id', 'tx_flag',
        'tx_quantity', 'before_quantity', 'after_quantity', 'tx_quantity_exact', 'before_quantity_exact', 'after_quantity_exact',
        'source_doc_type', 'source_doc_id', 'source_doc_line_id', 'del_flag', *COMMON,
        'quantity_version_before', 'quantity_version_after', 'source_event_key'),
    'wm_material_receive': ('tenant_id', 'receive_id', 'status', 'del_flag'),
    'wm_material_receive_line': ('tenant_id', 'line_id', 'receive_id', 'material_id', 'base_uom_id', 'quantity_received', 'del_flag'),
    # source_doc_line_id 是 line_id，绝不能替换为上架明细 detail_id。
    'wm_material_receive_detail': ('tenant_id', 'detail_id', 'line_id', 'receive_id', 'material_id', 'base_uom_id', 'quantity', 'del_flag'),
    'so_customer_order': ('tenant_id', 'co_id', 'status', 'del_flag'),
    'so_sales_order_line': ('tenant_id', 'line_id', 'so_id', 'material_id', 'unit', 'reserved_qty', 'del_flag'),
    'prd_work_order': ('tenant_id', 'wo_id', 'material_id', 'unit_id', 'wo_status', 'del_flag'),
    'prd_material_requirement': ('tenant_id', 'requirement_id', 'source_type', 'status', 'del_flag'),
    'prd_material_requirement_detail': ('tenant_id', 'detail_id', 'requirement_id', 'material_id', 'unit',
                                        'required_quantity', 'issued_quantity', 'del_flag'),
    'wm_stock_reservation_event': ('tenant_id', 'event_id', 'stock_id', 'material_id', 'base_uom_id', 'quantity_uom_version',
        'owner_doc_type', 'owner_doc_id', 'owner_doc_line_id', 'owner_mrp_detail_id', 'event_type', 'reserved_delta',
        'before_reserved_quantity', 'after_reserved_quantity', 'reservation_version_before', 'reservation_version_after',
        'stock_version_before', 'stock_version_after', 'source_doc_type', 'source_doc_id', 'source_doc_line_id',
        'source_event_key', 'related_stock_tx_id', 'quantity_contract_version', 'quantity_origin', 'quantity_evidence_hash'),
}
DECIMAL_FIELDS = {
    'bd_material': {'safety_stock': 4},
    'bd_unit_of_measure': {'conversion_rate': 6},
    'wm_material_stock': {c: 4 for c in ('stock_quantity', 'reserved_quantity', 'stock_quantity_exact', 'reserved_quantity_exact')},
    'wm_material_stock_tx': {c: 4 for c in ('tx_quantity', 'before_quantity', 'after_quantity', 'tx_quantity_exact',
                                          'before_quantity_exact', 'after_quantity_exact')},
    'wm_material_receive_line': {'quantity_received': 4},
    'wm_material_receive_detail': {'quantity': 4},
    'so_sales_order_line': {'reserved_qty': 4},
    'prd_material_requirement_detail': {'required_quantity': 4, 'issued_quantity': 4},
    'wm_stock_reservation_event': {c: 4 for c in ('reserved_delta', 'before_reserved_quantity', 'after_reserved_quantity')},
}
IDS = {'sys_tenant': 'tenant_id', 'bd_material': 'material_id', 'bd_unit_of_measure': 'uom_id',
       'wm_warehouse': 'warehouse_id', 'wm_storage_area': 'area_id', 'wm_storage_location': 'location_id',
       'wm_material_stock': 'stock_id', 'wm_material_stock_tx': 'tx_id', 'wm_material_receive': 'receive_id',
       'wm_material_receive_line': 'line_id', 'wm_material_receive_detail': 'detail_id', 'so_customer_order': 'co_id',
       'so_sales_order_line': 'line_id', 'prd_work_order': 'wo_id', 'prd_material_requirement': 'requirement_id',
       'prd_material_requirement_detail': 'detail_id', 'wm_stock_reservation_event': 'event_id'}


def validate_target(host, port, profile, schema, tenant_id):
    if validate_runtime_args(host, port, profile) or schema != 'ry-erp' or not re.fullmatch(r'[0-9]{6,20}', tenant_id):
        raise ValueError('BLOCKED: only explicit local dev TCP3306 ERP tenant scope is permitted.')


def _schema(table):
    return 'ry-vue' if table == 'sys_tenant' else 'ry-erp'


def metadata_queries():
    predicate = "((TABLE_SCHEMA = 'ry-erp' AND TABLE_NAME IN (" + ','.join(
        "'" + t + "'" for t in COLUMNS if t != 'sys_tenant') + ")) OR (TABLE_SCHEMA = 'ry-vue' AND TABLE_NAME = 'sys_tenant'))"
    return {
        'metadata_tables': ('SELECT TABLE_SCHEMA, TABLE_NAME, ENGINE FROM information_schema.TABLES WHERE ' + predicate +
                            ' ORDER BY TABLE_SCHEMA, TABLE_NAME', ('schema', 'table', 'engine')),
        'metadata_columns': ('SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE '
                             'FROM information_schema.COLUMNS WHERE ' + predicate +
                             ' ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION',
                             ('schema', 'table', 'column', 'type', 'precision', 'scale')),
    }


def decode_metadata(tables, columns):
    result = {}
    for schema, table, engine in tables:
        if table in COLUMNS and schema == _schema(table):
            result[table] = {'engine': engine, 'columns': {}}
    for schema, table, column, data_type, precision, scale in columns:
        if table in result and schema == _schema(table) and column in COLUMNS[table]:
            result[table]['columns'][column] = {'type': data_type,
                'precision': None if precision in ('NULL', None) else int(precision),
                'scale': None if scale in ('NULL', None) else int(scale)}
    return result


def build_snapshot_queries(metadata, tenant_id):
    validate_target('127.0.0.1', 3306, 'dev', 'ry-erp', tenant_id)
    result = metadata_queries()
    # 固定库名限定的SELECT令DATABASE()可以独立核实连接目标，不把跨库表名推断当库身份。
    result['environment'] = ('SELECT DATABASE(), @@session.tx_isolation, @@session.tx_read_only, UNIX_TIMESTAMP(NOW(6))',
                             ('database_name', 'isolation', 'read_only', 'clock_epoch'))
    for table, whitelist in COLUMNS.items():
        available = metadata.get(table, {}).get('columns', {})
        fields = tuple(c for c in whitelist if c in available)
        if 'tenant_id' not in fields or IDS[table] not in fields:
            continue
        result[table] = ('SELECT ' + ', '.join('`' + c + '`' for c in fields) + ' FROM `' + _schema(table) + '`.`' + table +
                         "` WHERE tenant_id = '" + tenant_id + "' ORDER BY `" + IDS[table] + '`', fields)
    return result


def _audit_snapshot(queries, user, password):
    """复用原盘点的安全客户端，显式选择ERP库；只改变本连接会话设置。"""
    if not queries or any(not _safe_select(q) for q in queries):
        raise ValueError('BLOCKED: audit snapshot accepts SELECT only.')
    script = ("SHOW GRANTS;\nSET SESSION TRANSACTION READ ONLY;\n"
              "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;\n"
              "SET SESSION time_zone = '+08:00';\n"
              "START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;\n")
    for index, query in enumerate(queries):
        script += f"SELECT '__STOCK_AUDIT_{index}__';\n{query};\n"
    script += f"SELECT '__STOCK_AUDIT_{len(queries)}__';\nCOMMIT;\n"
    lines = _run(_client_args(user) + ['--database=ry-erp'], password, script)
    try:
        cursor = lines.index('__STOCK_AUDIT_0__')
        if not grants_select_only(lines[:cursor]):
            raise ValueError('grants')
        result = []
        for index in range(len(queries)):
            if lines[cursor] != f'__STOCK_AUDIT_{index}__':
                raise ValueError('framing')
            next_cursor = lines.index(f'__STOCK_AUDIT_{index + 1}__', cursor + 1)
            result.append([line.split('\t') for line in lines[cursor + 1:next_cursor]])
            cursor = next_cursor
        if cursor != len(lines) - 1:
            raise ValueError('framing')
        return result
    except (ValueError, IndexError):
        raise RuntimeError('BLOCKED: SELECT-only grants or snapshot framing unavailable (details suppressed).') from None


def _native_decimal(metadata, table, column, scale=4):
    native = metadata.get(table, {}).get('columns', {}).get(column, {})
    return (native.get('type'),native.get('precision'),native.get('scale')) == ('decimal',18,scale)


def _decimal(value, scale=4, signed=False):
    # 元数据核验另行执行；文本仅用于发现异常，不把DOUBLE输出的十进制文本当精确证明。
    if not isinstance(value, (str, Decimal)) or not re.fullmatch(r'-?[0-9]+(?:\.[0-9]+)?', str(value)):
        return None
    try:
        amount = Decimal(value)
        if not amount.is_finite() or abs(amount) >= Decimal('100000000000000') or (not signed and amount < 0):
            return None
        if amount.as_tuple().exponent < -scale and amount != amount.quantize(Decimal(1).scaleb(-scale)):
            return None
        return amount
    except InvalidOperation:
        return None


def _integer(value, positive=False):
    if not isinstance(value, str) or not re.fullmatch(r'0|[1-9][0-9]*', value):
        return None
    number = int(value)
    return number if number <= 2**63 - 1 and (not positive or number > 0) else None


def _identity(value):
    return _integer(value, positive=True) is not None


def _active(row):
    return row.get('del_flag') == '0'


def _origin(row):
    return (row.get('quantity_contract_version') == '1' and row.get('quantity_origin') == 'NATIVE_V1'
            and re.fullmatch(r'[0-9a-f]{64}', row.get('quantity_evidence_hash') or '') is not None)


def _source_reference(row):
    return _identity(row.get('source_doc_id')) and _identity(row.get('source_doc_line_id'))


def _date_before(value, epoch):
    # 本地dev业务时区固定+08。只诊断时间，不能用时间单独认证历史单位/来源。
    if not isinstance(value, str) or not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?', value):
        return False
    try:
        value_at = datetime.fromisoformat(value).replace(tzinfo=timezone(timedelta(hours=8)))
        delta = value_at - datetime(1970,1,1,tzinfo=timezone.utc)
        exact_epoch = Decimal(delta.days * 86400 + delta.seconds) + Decimal(delta.microseconds) / Decimal(1000000)
        return exact_epoch <= epoch
    except (ValueError, TypeError, OverflowError):
        return False


def evaluate_snapshot(metadata, data, tenant_id):
    validate_target('127.0.0.1', 3306, 'dev', 'ry-erp', tenant_id)
    issues = Counter()
    # 诊断不是事务锁内执行证书，也未证明应用构建/全写入口；不能提供verified/apply开关。
    issues['BUSINESS_SOURCE_PROOF_REQUIRED'] += 1
    issues['NEW_WRITE_SERVICE_NOT_ATTESTED'] += 1
    native = []
    for table, fields in COLUMNS.items():
        details = metadata.get(table, {})
        if not details or any(c not in details.get('columns', {}) for c in fields):
            issues['SCHEMA_CONTRACT_MISSING'] += 1
        if details and details.get('engine') != 'InnoDB':
            issues['NONTRANSACTIONAL_TABLE'] += 1
        for column, scale in DECIMAL_FIELDS.get(table, {}).items():
            actual = details.get('columns', {}).get(column, {})
            if (actual.get('type'), actual.get('precision'), actual.get('scale')) != ('decimal', 18, scale):
                issues['SOURCE_QUANTITY_NATIVE_UNPROVEN' if table.startswith('wm_material_receive')
                       else 'NATIVE_QUANTITY_TYPE_UNPROVEN'] += 1
            # 只输出已知类型/小整数;未知字符串或注入的元数据绝不原样输出。
            native.append({'table': table, 'column': column, 'type': actual.get('type') if actual.get('type') in
                ('decimal','double','float','bigint','int','varchar') else 'unknown',
                'precision': actual.get('precision') if isinstance(actual.get('precision'), int) and 0 <= actual['precision'] <= 65 else None,
                'scale': actual.get('scale') if isinstance(actual.get('scale'), int) and 0 <= actual['scale'] <= 30 else None})
    environment = data.get('environment', [])
    env = environment[0] if len(environment) == 1 else {}
    clock = _decimal(env.get('clock_epoch'), scale=6)
    if env.get('database_name') != 'ry-erp' or env.get('isolation') != 'REPEATABLE-READ' or env.get('read_only') != '1' or clock is None:
        issues['SNAPSHOT_ENVIRONMENT_UNPROVEN'] += 1
    clock = clock if clock is not None else Decimal(0)
    rows = {t: data.get(t, []) for t in COLUMNS}
    if any(t in metadata and t not in data for t in COLUMNS):
        issues['SNAPSHOT_ROWS_INCOMPLETE'] += 1
    indexes = {}
    for table, values in rows.items():
        seen = {}
        keys = set()
        for row in values:
            if row.get('tenant_id') != tenant_id:
                issues['TENANT_SCOPE_MISMATCH'] += 1
            row_id = row.get(IDS[table])
            if row_id in seen:
                issues['DUPLICATE_ROW_IDENTITY'] += 1
            if table != 'sys_tenant' and not _identity(row_id):
                issues['ROW_IDENTITY_UNPROVEN'] += 1
            seen[row_id] = row
            key = row.get('source_event_key')
            if table in ('wm_material_stock_tx','wm_stock_reservation_event') and key:
                if key in keys:
                    issues['DUPLICATE_SOURCE_EVENT_KEY'] += 1
                keys.add(key)
        indexes[table] = seen
    tenant = rows['sys_tenant']
    if len(tenant) != 1 or tenant[0].get('tenant_id') != tenant_id or tenant[0].get('status') != '0' or not _active(tenant[0]):
        issues['TENANT_NOT_ACTIVE'] += 1
    stocks = rows['wm_material_stock']
    if not stocks:
        issues['EMPTY_STOCK_NOT_PROVEN_ZERO'] += 1
    tx_by_stock = defaultdict(list)
    for tx in rows['wm_material_stock_tx']:
        tx_by_stock[tx.get('material_stock_id')].append(tx)
        if not _origin(tx) or not _active(tx) or not tx.get('source_event_key'):
            issues['TX_ORIGIN_UNPROVEN'] += 1
        stock = indexes['wm_material_stock'].get(tx.get('material_stock_id'), {})
        if not stock or tx.get('material_id') != stock.get('material_id') or tx.get('base_uom_id') != stock.get('base_uom_id') or tx.get('quantity_uom_version') != stock.get('quantity_uom_version'):
            issues['TX_STOCK_LINEAGE_UNPROVEN'] += 1
        _check_receipt_source(tx, indexes, rows, metadata, issues)
    events_by_stock = defaultdict(list)
    for event in rows['wm_stock_reservation_event']:
        events_by_stock[event.get('stock_id')].append(event)
        if event.get('stock_id') not in indexes['wm_material_stock']:
            issues['RESERVATION_CHAIN_UNPROVEN'] += 1
    frozen = 0
    for stock in stocks:
        material = indexes['bd_material'].get(stock.get('material_id'), {})
        unit = indexes['bd_unit_of_measure'].get(material.get('base_uom_id'), {})
        scale = _integer(unit.get('quantity_scale'))
        if not material or not _active(material) or material.get('status') != '1' or _decimal(material.get('safety_stock')) is None:
            issues['MATERIAL_CONTRACT_UNPROVEN'] += 1
        if not unit or not _active(unit) or unit.get('status') != '1' or unit.get('is_base') != '1' or unit.get('primary_id') not in (None,unit.get('uom_id')) or _decimal(unit.get('conversion_rate'),6) != Decimal(1) or scale is None or scale > 4 or not unit.get('conversion_version') or not _date_before(unit.get('conversion_verified_at'), clock):
            issues['UNIT_CONTRACT_UNPROVEN'] += 1
        if not _identity(stock.get('base_uom_id')) or stock.get('base_uom_id') != material.get('base_uom_id'):
            issues['STOCK_UNIT_IDENTITY_UNPROVEN'] += 1
        if not stock.get('quantity_uom_version') or stock.get('quantity_uom_version') != unit.get('conversion_version'):
            issues['STOCK_UNIT_VERSION_UNPROVEN'] += 1
        if not _origin(stock) or not _active(stock) or _integer(stock.get('version')) is None or not _date_before(stock.get('quantity_source_at'),clock):
            issues['STOCK_ORIGIN_UNPROVEN'] += 1
        amount = (_decimal(stock.get('stock_quantity'), scale if scale is not None and scale <= 4 else 4)
                  if _native_decimal(metadata,'wm_material_stock','stock_quantity') else None)
        reserved = (_decimal(stock.get('reserved_quantity'), scale if scale is not None and scale <= 4 else 4)
                    if _native_decimal(metadata,'wm_material_stock','reserved_quantity') else None)
        if amount is None or reserved is None:
            issues['STOCK_QUANTITY_UNKNOWN_OR_INVALID'] += 1
        if amount is not None and reserved is not None and reserved > amount:
            issues['RESERVED_EXCEEDS_STOCK'] += 1
        if _decimal(stock.get('stock_quantity_exact')) != amount or _decimal(stock.get('reserved_quantity_exact')) != reserved or stock.get('stock_quantity_exact') is None or stock.get('reserved_quantity_exact') is None:
            issues['STOCK_EXACT_SHADOW_DIVERGES'] += 1
        warehouse = indexes['wm_warehouse'].get(stock.get('warehouse_id'), {})
        area = indexes['wm_storage_area'].get(stock.get('area_id'), {})
        location = indexes['wm_storage_location'].get(stock.get('location_id'), {})
        valid_location = (not stock.get('location_id') and area.get('location_flag') == '0') or (
            location and _active(location) and location.get('enable_flag') == '1' and location.get('frozen_flag') in ('0','1')
            and location.get('warehouse_id') == stock.get('warehouse_id') and location.get('area_id') == stock.get('area_id'))
        if not warehouse or not _active(warehouse) or warehouse.get('status') != '1' or not area or not _active(area) or area.get('enable_flag') != '1' or area.get('frozen_flag') not in ('0','1') or area.get('location_flag') not in ('0','1') or area.get('warehouse_id') != stock.get('warehouse_id') or not valid_location or stock.get('frozen_flag') not in ('0','1'):
            issues['STORAGE_LINEAGE_UNPROVEN'] += 1
        if stock.get('frozen_flag') == '1' or area.get('frozen_flag') == '1' or location.get('frozen_flag') == '1':
            frozen += 1
        native_tx = all(_native_decimal(metadata,'wm_material_stock_tx',c) for c in DECIMAL_FIELDS['wm_material_stock_tx'])
        if not native_tx or not _check_tx_chain(stock, tx_by_stock[stock.get('stock_id')], amount):
            issues['STOCK_TX_CHAIN_UNPROVEN'] += 1
        events = events_by_stock[stock.get('stock_id')]
        if reserved is not None and reserved > 0:
            issues['RESERVATION_OWNERSHIP_PROOF_REQUIRED'] += 1
        if not _check_reservations(stock, events, reserved, indexes, issues):
            issues['RESERVATION_CHAIN_UNPROVEN'] += 1
    _check_owner_balances(rows,metadata,issues)
    return {'status': 'BLOCKED', 'authorizes_writes': False, 'scope': 'local-dev-erp-explicit-tenant-redacted',
            'facts': {'row_counts': {t: len(values) for t, values in rows.items()}, 'frozen_stock_rows': frozen,
                      'certified_stock_rows': 0, 'isolated_stock_rows': len(stocks),
                      'stock_rows_without_candidate_contract': sum(not _origin(s) for s in stocks),
                      'tx_rows_without_candidate_contract': sum(not _origin(t) for t in rows['wm_material_stock_tx'])},
            'native_columns': native, 'blocked_reasons': [{'code': code, 'count': issues[code]} for code in sorted(issues)]}


def _check_receipt_source(tx, indexes, rows, metadata, issues):
    if not _source_reference(tx):
        issues['SOURCE_BUSINESS_REFERENCE_MISSING'] += 1
        return
    if tx.get('source_doc_type') != 'WM_MATERIAL_RECEIVE':
        issues['SOURCE_BUSINESS_TYPE_UNSUPPORTED'] += 1
        return
    head = indexes['wm_material_receive'].get(tx['source_doc_id'], {})
    line = indexes['wm_material_receive_line'].get(tx['source_doc_line_id'], {})
    details = [r for r in rows['wm_material_receive_detail'] if r.get('line_id') == tx['source_doc_line_id']
               and r.get('receive_id') == tx['source_doc_id']]
    if not head or not _active(head) or head.get('status') != 'FINISHED' or not line or not _active(line) or line.get('receive_id') != tx['source_doc_id'] or line.get('material_id') != tx.get('material_id') or line.get('base_uom_id') != tx.get('base_uom_id') or not details or any(not _active(r) or r.get('material_id') != tx.get('material_id') or r.get('base_uom_id') != tx.get('base_uom_id') for r in details):
        issues['SOURCE_BUSINESS_LINEAGE_UNPROVEN'] += 1
    if not _native_decimal(metadata,'wm_material_receive_line','quantity_received') or not _native_decimal(metadata,'wm_material_receive_detail','quantity') or _decimal(line.get('quantity_received')) is None or any(_decimal(r.get('quantity')) is None for r in details):
        issues['SOURCE_BUSINESS_QUANTITY_UNPROVEN'] += 1
    # 不将line/detail量直接当tx量：同一业务行可能按批次/储位拆成多个stock。
    # 本轮仅核身份及原生精度，精确分摊/历史单位版本由受控服务独立重建。


def _check_tx_chain(stock, txs, amount):
    if not txs or amount is None:
        return False
    version, balance = 0, Decimal(0)
    for tx in sorted(txs, key=lambda t: _integer(t.get('quantity_version_after')) or -1):
        before, after, qty = (_decimal(tx.get(c)) for c in ('before_quantity','after_quantity','tx_quantity'))
        if before is None or after is None or qty is None or not _origin(tx) or not _active(tx) or tx.get('tx_flag') not in ('1','-1') or _integer(tx.get('quantity_version_before')) != version or _integer(tx.get('quantity_version_after')) != version + 1 or before != balance or before + (qty if tx['tx_flag'] == '1' else -qty) != after or any(_decimal(tx.get(c + '_exact')) != v for c, v in [('before_quantity',before),('after_quantity',after),('tx_quantity',qty)]):
            return False
        version, balance = version + 1, after
    return (version == _integer(stock.get('quantity_version')) and balance == amount
            and txs and max(txs,key=lambda t: _integer(t.get('quantity_version_after')) or -1).get('tx_id') == stock.get('quantity_source_head_tx_id'))


def _check_reservations(stock, events, reserved, indexes, issues):
    if reserved is None:
        return False
    if not events:
        return reserved == 0 and stock.get('reservation_version') == '0' and stock.get('reservation_head_event_id') is None
    version, balance = 0, Decimal(0)
    owners = defaultdict(Decimal)
    for event in sorted(events, key=lambda e: _integer(e.get('reservation_version_after')) or -1):
        before, after = (_decimal(event.get(c)) for c in ('before_reserved_quantity','after_reserved_quantity'))
        delta = _decimal(event.get('reserved_delta'), signed=True)
        kind = event.get('event_type')
        if not _origin(event) or event.get('material_id') != stock.get('material_id') or event.get('base_uom_id') != stock.get('base_uom_id') or event.get('quantity_uom_version') != stock.get('quantity_uom_version') or before is None or after is None or delta is None or delta == 0 or _integer(event.get('reservation_version_before')) != version or _integer(event.get('reservation_version_after')) != version + 1 or before != balance or before + delta != after or kind not in ('RESERVE','RELEASE','CONSUME') or (delta > 0) != (kind == 'RESERVE') or not event.get('source_event_key') or not _source_reference(event):
            return False
        before_cas, after_cas = (_integer(event.get(c)) for c in ('stock_version_before','stock_version_after'))
        if before_cas is None or after_cas != before_cas + 1:
            return False
        if kind == 'CONSUME':
            tx = indexes['wm_material_stock_tx'].get(event.get('related_stock_tx_id'), {})
            if not tx or tx.get('material_stock_id') != stock.get('stock_id') or tx.get('tx_flag') != '-1':
                return False
        if event.get('owner_doc_type') == 'SO_LINE':
            line = indexes['so_sales_order_line'].get(event.get('owner_doc_line_id'), {})
            head = indexes['so_customer_order'].get(event.get('owner_doc_id'), {})
            if not line or not head or not _active(line) or not _active(head) or line.get('so_id') != event.get('owner_doc_id') or line.get('material_id') != stock.get('material_id') or line.get('unit') != stock.get('base_uom_id') or event.get('owner_mrp_detail_id') is not None:
                return False
        elif event.get('owner_doc_type') == 'WO_MRP_DETAIL':
            # 原MRP头无工单父ID。不能把新事件声明的woId当成历史父链证明。
            issues['MRP_OWNER_LINK_UNPROVEN'] += 1
            return False
        else:
            return False
        owner = (event.get('owner_doc_type'), event.get('owner_doc_id'), event.get('owner_doc_line_id'))
        owners[owner] += delta
        if owners[owner] < 0:
            return False
        version, balance = version + 1, after
    last = max(events,key=lambda e: _integer(e.get('reservation_version_after')) or -1)
    return version == _integer(stock.get('reservation_version')) and balance == reserved and last.get('event_id') == stock.get('reservation_head_event_id')


def _check_owner_balances(rows, metadata, issues):
    if not all(_native_decimal(metadata,'wm_stock_reservation_event',c) for c in DECIMAL_FIELDS['wm_stock_reservation_event']):
        issues['RESERVATION_OWNER_BALANCE_UNPROVEN'] += 1
        return
    totals = defaultdict(Decimal)
    for event in rows['wm_stock_reservation_event']:
        if event.get('owner_doc_type') != 'SO_LINE':
            continue
        delta = _decimal(event.get('reserved_delta'),signed=True)
        if delta is None:
            issues['RESERVATION_OWNER_BALANCE_UNPROVEN'] += 1
            continue
        totals[(event.get('owner_doc_id'),event.get('owner_doc_line_id'))] += delta
    for line in rows['so_sales_order_line']:
        owner = (line.get('so_id'),line.get('line_id'))
        source = (_decimal(line.get('reserved_qty')) if _native_decimal(metadata,'so_sales_order_line','reserved_qty') else None)
        if source is None or source != totals.pop(owner,Decimal(0)):
            issues['RESERVATION_OWNER_BALANCE_UNPROVEN'] += 1
    if totals:
        issues['RESERVATION_OWNER_BALANCE_UNPROVEN'] += len(totals)


def collect_audit(user, password, tenant_id, *, runner=None):
    """真实入口两阶段读取元数据；快照首尾再读并比对，发生漂移不保留业务诊断。"""
    validate_target('127.0.0.1',3306,'dev','ry-erp',tenant_id)
    if user.lower() == 'root' or not re.fullmatch(r'[A-Za-z0-9_@.-]+',user) or not password:
        raise ValueError('BLOCKED: approved non-root SELECT-only credentials required (values suppressed).')
    fetch = runner or _audit_snapshot
    try:
        preview = metadata_queries()
        args = list(preview.values())
        # 两个阶段均显式选择ERP库，沿SELECT-only授权与READ ONLY策略。
        initial = fetch([q for q,_ in args], user, password)
        if len(initial) != 2:
            raise RuntimeError('framing')
        meta = decode_metadata(*initial)
        queries = build_snapshot_queries(meta,tenant_id)
        # 同连接首尾元数据，业务行无认证输出；非事务元数据漂移直接停止。
        tail = metadata_queries()
        all_queries = list(queries.values()) + list(tail.values())
        fetched = fetch([q for q,_ in all_queries],user,password)
        if len(fetched) != len(all_queries):
            raise RuntimeError('framing')
        current = decode_metadata(fetched[0],fetched[1])
        final = decode_metadata(fetched[-2],fetched[-1])
        if meta != current or current != final:
            raise RuntimeError('schema drift')
        data = {}
        for (name, (_, fields)), values in zip(queries.items(),fetched):
            if name.startswith('metadata_'):
                continue
            if any(len(row) != len(fields) for row in values):
                raise RuntimeError('row framing')
            data[name] = [dict(zip(fields,(None if v == 'NULL' else v for v in row))) for row in values]
        return evaluate_snapshot(meta,data,tenant_id)
    except (ValueError,RuntimeError,TypeError,KeyError,IndexError):
        raise RuntimeError('BLOCKED: read-only inventory snapshot/schema/grants could not be verified (details suppressed).') from None


class _RedactedArgumentParser(argparse.ArgumentParser):
    def error(self, _message):
        # argparse默认错误会回显未知参数/非法值，误传密码也不得打印。
        raise ValueError('BLOCKED: audit arguments unavailable (details suppressed).')


def main(argv=None):
    parser = _RedactedArgumentParser(description='Read-only local inventory audit; never authorizes writes')
    parser.add_argument('--host',default='127.0.0.1')
    parser.add_argument('--port',type=int,default=3306)
    parser.add_argument('--profile',default='dev')
    parser.add_argument('--schema',default='ry-erp')
    parser.add_argument('--tenant-id',required=True)
    parser.add_argument('--output',default=str(OUTPUT))
    try:
        args = parser.parse_args(argv)
        validate_target(args.host,args.port,args.profile,args.schema,args.tenant_id)
        if Path(args.output).resolve() != OUTPUT.resolve():
            raise ValueError('BLOCKED: only the fixed ignored audit report is permitted.')
        user,password = os.environ.get('MYSQL_USER',''),os.environ.get('MYSQL_PWD','')
        if not password or not re.fullmatch(r'[A-Za-z0-9_@.-]+',user) or user.lower() == 'root':
            raise ValueError('BLOCKED: approved non-root SELECT-only environment credentials required (values suppressed).')
        report = collect_audit(user,password,args.tenant_id)
        OUTPUT.parent.mkdir(parents=True,exist_ok=True)
        OUTPUT.write_text(json.dumps(report,ensure_ascii=False,indent=2) + '\n',encoding='utf-8')
        print('BLOCKED: read-only redacted audit saved under ignored inventory-certification-audit.json; not a write certificate.')
        return 1
    except (ValueError,RuntimeError,OSError):
        print('BLOCKED: inventory audit target/credentials/snapshot/output unavailable (details suppressed).',file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
