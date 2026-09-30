"""只读且受白名单限制的本地 MySQL 盘点；输出不包含凭证或个人数据。"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[4]
OUTPUT = ROOT / '.codex' / 'full-flow-20260928' / 'inventory.json'
TABLES = {
    'ry-vue': {
        'sys_tenant': ('tenant_id', 'status', 'del_flag'),
        'sys_user': ('user_id', 'tenant_id', 'user_name', 'status', 'del_flag'),
        'sys_dept': ('dept_id', 'tenant_id', 'dept_name', 'status', 'del_flag'),
        'sys_auto_code_rule': ('rule_id', 'tenant_id', 'rule_code', 'enable_flag', 'del_flag'),
        'sys_auto_code_part': ('tenant_id', 'rule_id', 'part_index', 'part_type', 'fix_character', 'date_format', 'cycle_method', 'seria_now_no', 'del_flag'),
        'sys_menu': ('menu_id', 'perms', 'status'),
        'sys_role': ('role_id', 'tenant_id', 'status', 'del_flag'),
        'sys_role_menu': ('role_id', 'menu_id'),
        'ai_config': ('config_group',),
        'ai_provider': ('tenant_id', 'provider_type', 'status', 'del_flag'),
        'ai_model': ('tenant_id', 'model_type', 'status', 'del_flag'),
        'ai_decision_run': ('tenant_id', 'biz_code', 'create_time'),
        'ai_decision_action': ('tenant_id', 'create_time'),
        'ai_decision_evidence': ('tenant_id', 'create_time'),
    },
    'ry-erp': {
        'bd_serial_number_rule': ('tenant_id', 'rule_code', 'rule_type', 'prefix', 'date_format', 'seq_length', 'seq_cycle', 'current_seq', 'check_digit_rule', 'status', 'del_flag'),
        'bd_client': ('tenant_id', 'client_code', 'del_flag'),
        'bd_vendor': ('tenant_id', 'vendor_code', 'status', 'del_flag'),
        'bd_material': ('tenant_id', 'material_code', 'status', 'del_flag'),
        'bd_unit_of_measure': ('tenant_id', 'uom_code', 'status', 'del_flag'),
        'bd_bom': ('tenant_id', 'bom_id', 'status', 'del_flag'),
        'bd_routing': ('tenant_id', 'routing_code', 'status', 'del_flag'),
        'wm_warehouse': ('tenant_id', 'warehouse_code', 'status', 'del_flag'),
        'wm_storage_area': ('tenant_id', 'area_code', 'enable_flag', 'frozen_flag', 'del_flag'),
        'wm_storage_location': ('tenant_id', 'location_code', 'enable_flag', 'frozen_flag', 'del_flag'),
        'bd_workshop': ('tenant_id', 'workshop_code', 'status', 'del_flag'),
        'bd_workstation': ('tenant_id', 'workstation_code', 'status', 'del_flag'),
        'so_customer_order': ('tenant_id', 'co_no', 'create_time'),
        'pu_purchase_order': ('tenant_id', 'order_no', 'create_time'),
        'prd_work_order': ('tenant_id', 'wo_no', 'create_time'),
    },
    'ry-mes': {
        'mes_resource_pool': ('tenant_id', 'pool_code', 'status', 'del_flag'),
        'mes_resource_item': ('tenant_id', 'pool_id', 'status', 'del_flag'),
        'pro_work_order': ('tenant_id', 'wo_no', 'create_time'),
    },
    'ry-iot': {
        'iot_product': ('tenant_id', 'product_code', 'status', 'del_flag'),
        'iot_device': ('tenant_id', 'device_code', 'status', 'del_flag'),
    },
}
# 仓库中的 MES 生产工单表是 pro_work_order，而非 mes_work_order。
BUSINESS = {
    'ry-vue': ('ai_decision_run', 'ai_decision_action', 'ai_decision_evidence'),
    'ry-erp': ('so_customer_order', 'pu_purchase_order', 'prd_work_order'),
    'ry-mes': ('pro_work_order',),
    'ry-iot': (),
}

# 取值依据 script/sql/ry-*.sql 中 status/del_flag 的定义。
# 未能证明有效状态的表刻意不纳入候选范围。
ACTIVE = {
    'sys_tenant': "status = '0'", 'sys_user': "status = '0'", 'sys_dept': "status = '0'",
    'sys_auto_code_rule': "enable_flag = 'Y'",
    'sys_auto_code_part': "EXISTS (SELECT 1 FROM `ry-vue`.`sys_auto_code_rule` r WHERE r.rule_id = `sys_auto_code_part`.rule_id AND r.tenant_id = `sys_auto_code_part`.tenant_id AND r.enable_flag = 'Y' AND r.del_flag = '0')",
    'sys_role': "status = '0'", 'ai_provider': "status = '1'", 'ai_model': "status = '1'",
    # bd_client 没有 status 列；未删除是唯一由来源证明的可用性规则。
    'bd_client': '1 = 1', 'wm_warehouse': 'status = 1',
    'bd_serial_number_rule': "status = 'ACTIVE'", 'bd_vendor': "status = 'ACTIVE'",
    'bd_material': 'status = 1', 'bd_unit_of_measure': 'status = 1',
    'bd_bom': 'status = 1', 'bd_routing': 'status = 1',
    'wm_storage_area': 'enable_flag = 1 AND frozen_flag = 0',
    'wm_storage_location': 'enable_flag = 1 AND frozen_flag = 0',
    'bd_workshop': 'status = 1', 'bd_workstation': 'status = 1',
    'mes_resource_pool': "status = 'ENABLED'",
    'mes_resource_item': ("status = 'AVAILABLE' AND EXISTS (SELECT 1 FROM `ry-mes`.`mes_resource_pool` p "
                          "WHERE p.pool_id = `mes_resource_item`.pool_id "
                          "AND p.tenant_id = `mes_resource_item`.tenant_id "
                          "AND p.status = 'ENABLED' AND p.del_flag = '0')"),
    'iot_product': "status = 'ACTIVE'", 'iot_device': "status = 'ACTIVE'",
}
CANDIDATES = {
    'ry-vue': {
        'sys_tenant': ('tenant_id',), 'sys_user': ('user_id', 'tenant_id'),
        'sys_dept': ('dept_id', 'tenant_id'),
        'sys_auto_code_rule': ('tenant_id','rule_id','rule_code','enable_flag'),
        'sys_auto_code_part': ('tenant_id','rule_id','part_index','part_type','fix_character','date_format','cycle_method','seria_now_no'),
        'ai_provider': ('tenant_id','provider_type','status'),
        'ai_model': ('tenant_id','model_type','status'),
    },
    'ry-erp': {
        'bd_serial_number_rule': ('tenant_id','rule_code','rule_type','prefix','date_format','seq_length','seq_cycle','current_seq','check_digit_rule'),
        'bd_client': ('tenant_id','client_code','client_id'), 'bd_vendor': ('tenant_id','vendor_code'), 'bd_material': ('tenant_id','material_code','material_id'),
        'bd_unit_of_measure': ('tenant_id','uom_code'), 'bd_bom': ('tenant_id','bom_id'),
        'bd_routing': ('tenant_id','routing_code','routing_id'), 'wm_warehouse': ('tenant_id','warehouse_code','warehouse_id'), 'wm_storage_area': ('tenant_id','area_code'),
        'wm_storage_location': ('tenant_id','location_code'), 'bd_workshop': ('tenant_id','workshop_code'),
        'bd_workstation': ('tenant_id','workstation_code'),
    },
    'ry-mes': {'mes_resource_pool': ('tenant_id','pool_code'), 'mes_resource_item': ('tenant_id','pool_id')},
    'ry-iot': {'iot_product': ('tenant_id','product_code'), 'iot_device': ('tenant_id','device_code')},
}
ACTIVE_TENANTS = "SELECT tenant_id FROM `ry-vue`.`sys_tenant` WHERE status = '0' AND del_flag = '0'"
BUSINESS_CODES = {'ai_decision_run':'biz_code', 'so_customer_order':'co_no',
                  'pu_purchase_order':'order_no', 'prd_work_order':'wo_no', 'pro_work_order':'wo_no'}

def validate_target(host: str, port: int, profile: str, schema: str) -> None:
    if host not in ('localhost', '127.0.0.1') or port != 3306 or profile != 'dev' or schema not in TABLES:
        raise ValueError('Only dev on localhost TCP 3306 with the four approved ry-* schemas is permitted; prod and decision-demo are forbidden')

def safe_identifier(name: str, available: set[str]) -> str:
    if name not in available or not re.fullmatch(r'[a-z][a-z0-9_-]*', name):
        raise ValueError('Schema or table is not present in the approved metadata allowlist')
    return '`' + name + '`'

def build_count_query(schema: str, table: str, available: dict[str, set[str]]) -> str:
    if schema not in TABLES or table not in TABLES[schema]:
        raise ValueError('Unapproved table')
    return f'SELECT COUNT(*) FROM {safe_identifier(schema, set(available))}.{safe_identifier(table, available[schema])}'

def candidate_query(schema: str, table: str, columns: tuple[str, ...], pk: tuple[str, ...]) -> str:
    if table not in CANDIDATES.get(schema, {}) or table not in ACTIVE or not pk:
        raise ValueError('Unverified active state or primary key; not a candidate')
    if not set(columns) <= set(CANDIDATES[schema][table]) or any(not re.fullmatch(r'[a-z][a-z0-9_]*', c) for c in pk):
        raise ValueError('Unapproved candidate field')
    quoted = f'{safe_identifier(schema, set(TABLES))}.{safe_identifier(table, set(TABLES[schema]))}'
    where = "del_flag = '0' AND " + ACTIVE[table]
    if table != 'sys_tenant':
        where += f' AND tenant_id IN ({ACTIVE_TENANTS})'
    order = (('tenant_id', 'rule_id', 'part_index') if table == 'sys_auto_code_part' else
             ('tenant_id', 'rule_id') if table == 'sys_auto_code_rule' else ())
    order = tuple(dict.fromkeys(order + pk))
    return ('SELECT ' + ', '.join(safe_identifier(c, set(columns)) for c in columns) +
            f' FROM {quoted} WHERE {where} ORDER BY ' + ', '.join(safe_identifier(c, set(order)) for c in order))

def redact_error(_detail: str) -> str:
    return 'MySQL inventory query failed; check local credentials and schema permissions (details suppressed).'

def _client_args(user: str) -> list[str]:
    # 并非所有 mysql 版本都能用 --no-defaults 禁用 .mylogin.cnf；必须
    # 显式提供主机、用户和密码，绝不推断或默认使用 root。
    return ['mysql', '--no-defaults', '--protocol=TCP', '--host=127.0.0.1', '--port=3306',
            f'--user={user}', '--batch', '--skip-column-names', '--skip-reconnect']

def _client_env(password: str) -> dict[str, str]:
    env = os.environ.copy()
    env['MYSQL_PWD'] = password
    env.pop('MYSQL_TEST_LOGIN_FILE', None)
    return env

def _run(args: list[str], password: str, script: str | None = None) -> list[str]:
    try:
        result = subprocess.run(args, env=_client_env(password), input=script,
                                capture_output=True, text=True, timeout=120, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError(redact_error(str(exc))) from None
    if result.returncode:
        raise RuntimeError(redact_error(result.stderr)) from None
    return result.stdout.splitlines()

def _safe_select(sql: str) -> bool:
    return (bool(re.match(r'^SELECT\s', sql, re.I)) and ';' not in sql and '\\' not in sql
            and not re.search(r'\bINTO\b|\bFOR\s+UPDATE\b|\bLOCK\s+IN\s+SHARE\s+MODE\b', sql, re.I))

def mysql_select(sql: str, user: str, password: str) -> list[list[str]]:
    if not (_safe_select(sql) or sql == 'SHOW GRANTS'):
        raise ValueError('Only one SELECT or explicit SHOW GRANTS is allowed')
    return [line.split('\t') for line in _run(_client_args(user) + ['--init-command=SET SESSION TRANSACTION READ ONLY', '--execute=' + sql], password) if line]

def grants_select_only(grants: list[str]) -> bool:
    # 遇到 ALL/WRITE/GRANT OPTION 或未解析的角色就校验失败并关闭通路；不持久化原始授权信息。
    return bool(grants) and all(re.match(r'^GRANT (USAGE|SELECT) ON [^\n]+ TO [^\n]+$', g, re.I) and
                               not re.search(r'\bWITH\b', g, re.I) for g in grants) and any(
                                   g.upper().startswith('GRANT SELECT ON ') for g in grants)

def run_snapshot(queries: list[str], user: str, password: str, allow_admin_readonly: bool = False) -> list[list[list[str]]]:
    if not queries or any(not _safe_select(q) for q in queries):
        raise ValueError('Snapshot accepts only SELECT statements')
    script = ('SHOW GRANTS;\nSET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;\n'
              'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;\n')
    for index, query in enumerate(queries):
        script += f"SELECT '__INVENTORY_{index}__';\n{query};\n"
    script += "SELECT '__INVENTORY_" + str(len(queries)) + "__';\nCOMMIT;\n"
    output = _run(_client_args(user), password, script)
    first_marker = '__INVENTORY_0__'
    grants = output[:output.index(first_marker)] if first_marker in output else []
    if not grants or (allow_admin_readonly and not all(g.startswith('GRANT ') for g in grants)) or (not allow_admin_readonly and not grants_select_only(grants)):
        raise RuntimeError('SELECT-only grants could not be verified; inventory is not usable (grant details suppressed).')
    result = []
    cursor = output.index(first_marker)
    for index in range(len(queries)):
        if cursor >= len(output) or output[cursor] != f'__INVENTORY_{index}__':
            raise RuntimeError('Snapshot framing failed; inventory discarded.')
        try:
            next_cursor = output.index(f'__INVENTORY_{index + 1}__', cursor + 1)
        except ValueError:
            raise RuntimeError('Snapshot framing failed; inventory discarded.') from None
        result.append([line.split('\t') for line in output[cursor + 1:next_cursor]])
        cursor = next_cursor
    if cursor != len(output) - 1:
        raise RuntimeError('Snapshot framing failed; inventory discarded.')
    return result

# 只有这些批准的候选列存储数值实体 ID，而不是业务或联系信息。
SNOWFLAKE_ID_COLUMNS = frozenset({'client_id', 'material_id', 'routing_id', 'bom_id',
                                  'warehouse_id', 'user_id', 'dept_id', 'rule_id', 'pool_id'})

def _sanitize(value: str, column: str | None = None) -> str:
    # 类似电话号码的文本仅在属于规范、有符号 BIGINT 雪花 ID 时才予以豁免。
    snowflake_id = (column in SNOWFLAKE_ID_COLUMNS and
                    re.fullmatch(r'[1-9][0-9]{17,18}', value) is not None and
                    int(value) <= 2**63 - 1)
    # 仅允许范围明确的机器标识和规则组成部分；拒绝类似联系信息或密钥的数据。
    if (not re.fullmatch(r'[A-Za-z0-9_+/-]{1,128}', value) or
            (not snowflake_id and re.search(r'1[3-9][0-9]{9}', value)) or
            re.search(r'secret|password|token|apikey|api_key|bearer|private|sk-[a-z0-9]', value, re.I)):
        return '[REDACTED]'
    return value

def inventory(user: str, password: str, allow_admin_readonly: bool = False) -> dict:
    fetch = lambda sql: mysql_select(sql, user, password)
    actual_schemas = {r[0] for r in fetch('SELECT SCHEMA_NAME FROM information_schema.SCHEMATA')}
    missing: list[str] = []
    metadata: dict = {}
    schemas: dict = {}
    for schema, expected in TABLES.items():
        if schema not in actual_schemas:
            missing.append(f'{schema}: schema missing')
            schemas[schema] = {'missing': sorted(expected)}
            continue
        rows = fetch("SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + schema + "' AND TABLE_TYPE = 'BASE TABLE'")
        tables = {r[0]: r[1] for r in rows}
        absent = set(expected) - tables.keys()
        for table in absent:
            missing.append(f'{schema}.{table}: table missing')
        schemas[schema] = {'table_count': len(tables), 'missing': sorted(absent)}
        metadata[schema] = {}
        for table, fields in expected.items():
            if table not in tables:
                continue
            columns = {r[0] for r in fetch("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + schema + "' AND TABLE_NAME = '" + table + "'")}
            pk = tuple(r[0] for r in fetch("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = '" + schema + "' AND TABLE_NAME = '" + table + "' AND CONSTRAINT_NAME = 'PRIMARY' ORDER BY ORDINAL_POSITION"))
            metadata[schema][table] = (columns, pk)
            for field in set(fields) - columns:
                missing.append(f'{schema}.{table}.{field}: column missing')
            if tables[table].upper() != 'INNODB':
                missing.append(f'{schema}.{table}: transactional engine not verified')
            if table in CANDIDATES.get(schema, {}) and (not pk or not set(pk) <= columns):
                missing.append(f'{schema}.{table}: primary key missing')
    report = {'target': 'localhost TCP 3306 / dev', 'schemas': schemas,
              'missing': sorted(missing), 'snapshot': {'verified': False,
                           'grants': ('admin read-only invocation, grants not least privilege verified'
                                      if allow_admin_readonly else 'not verified')},
              'usable_for_write_phase': False}
    if missing:
        return report
    queries = ['SELECT NOW(6), CURRENT_DATE()']
    assignments = []
    def add(query, destination, key):
        queries.append(query)
        assignments.append((destination, key))
    for schema, tables in metadata.items():
        counts = schemas[schema]['baseline_counts'] = {}
        candidates = schemas[schema]['candidates'] = {}
        schemas[schema]['candidate_columns'] = {t: list(fields) for t, fields in CANDIDATES.get(schema, {}).items() if t in ACTIVE}
        unverified = schemas[schema]['unverified_candidates'] = []
        for table, (columns, pk) in sorted(tables.items()):
            quoted = f'{safe_identifier(schema, set(TABLES))}.{safe_identifier(table, set(tables))}'
            entry = counts[table] = {}
            add(f'SELECT COUNT(*) FROM {quoted}', entry, 'total')
            if 'tenant_id' in columns and table != 'sys_tenant':
                add(f'SELECT COUNT(*) FROM {quoted} WHERE tenant_id IN ({ACTIVE_TENANTS})', entry, 'active_tenant_total')
            if table in BUSINESS[schema]:
                add(f'SELECT COUNT(*) FROM {quoted} WHERE create_time >= CURRENT_DATE() AND create_time < CURRENT_DATE() + INTERVAL 1 DAY', entry, 'created_on_db_date')
                code = BUSINESS_CODES.get(table)
                if code:
                    add(f'SELECT COUNT(DISTINCT {safe_identifier(code, columns)}) FROM {quoted}', entry, 'distinct_business_codes')
            if table in CANDIDATES.get(schema, {}):
                if table not in ACTIVE:
                    unverified.append(table)
                else:
                    add(candidate_query(schema, table, CANDIDATES[schema][table], pk), candidates, table)
        if schema == 'ry-vue':
            # 只读取有效租户下有效角色获授的权限，不读取用户个人字段。
            add("SELECT DISTINCT m.perms FROM `ry-vue`.`sys_menu` m JOIN `ry-vue`.`sys_role_menu` rm ON rm.menu_id = m.menu_id JOIN `ry-vue`.`sys_role` r ON r.role_id = rm.role_id WHERE r.status = '0' AND r.del_flag = '0' AND r.tenant_id IN (" + ACTIVE_TENANTS + ") AND m.status = '0' AND m.perms LIKE 'ai:decision:%' ORDER BY m.perms", schemas[schema], 'decision_permissions')
    results = (run_snapshot(queries, user, password, allow_admin_readonly=True) if allow_admin_readonly
               else run_snapshot(queries, user, password))
    timestamp = results[0]
    if len(timestamp) != 1 or len(timestamp[0]) != 2:
        raise RuntimeError('Database snapshot timestamp missing; inventory discarded.')
    report['snapshot'] = {'verified': True, 'db_timestamp': timestamp[0][0], 'db_date': timestamp[0][1],
                          'isolation': 'REPEATABLE READ / WITH CONSISTENT SNAPSHOT, READ ONLY',
                          'engines': 'InnoDB', 'grants': ('admin read-only invocation, grants not least privilege verified'
                                                   if allow_admin_readonly else 'SELECT-only verified (raw grants discarded)')}
    redacted = False
    for (destination, key), rows in zip(assignments, results[1:]):
        if isinstance(destination, dict) and key in ('total','active_tenant_total','created_on_db_date','distinct_business_codes'):
            if len(rows) != 1 or len(rows[0]) != 1:
                raise RuntimeError('Baseline count missing; inventory discarded.')
            destination[key] = int(rows[0][0])
        else:
            candidate_columns = next((details['candidate_columns'][key] for details in schemas.values()
                                      if destination is details['candidates'] and key in details['candidate_columns']), None)
            clean = [[_sanitize(value, candidate_columns[i] if candidate_columns and i < len(candidate_columns) else None)
                      for i, value in enumerate(row)] for row in rows]
            redacted |= any('[REDACTED]' in row for row in clean)
            destination[key] = clean
    report['snapshot']['redacted_candidates'] = redacted
    # 部分实体没有经核验的有效状态：保留总数，但必须停止写阶段。
    report['usable_for_write_phase'] = not allow_admin_readonly and not redacted and not any(
        details['unverified_candidates'] for details in schemas.values())
    return report
def main() -> int:
    parser = argparse.ArgumentParser(description='Read-only local 3306 inventory')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--profile', default='dev')
    parser.add_argument('--schema', default='ry-vue')
    parser.add_argument('--allow-admin-readonly', action='store_true', help='Permit administrative grants for this read-only inventory only; never authorizes writes')
    parser.add_argument('--output', default=str(OUTPUT))
    args = parser.parse_args()
    try:
        validate_target(args.host, args.port, args.profile, args.schema)
        if Path(args.output).resolve() != OUTPUT.resolve():
            raise ValueError('Output must be the ignored .codex/full-flow-20260928/inventory.json')
        user, password = os.environ.get('MYSQL_USER'), os.environ.get('MYSQL_PWD')
        if not password or not user or not re.fullmatch(r'[A-Za-z0-9_@.-]+', user) or (user.lower() == 'root' and not args.allow_admin_readonly):
            raise ValueError('Set MYSQL_USER to an approved non-root read-only local account (or explicitly opt in to admin read-only) and MYSQL_PWD securely in the process environment; neither is printed')
        data = (inventory(user, password, allow_admin_readonly=True) if args.allow_admin_readonly
                else inventory(user, password))
        if args.allow_admin_readonly:
            data['usable_for_write_phase'] = False
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
        if data['missing']:
            print(f'Inventory incomplete: {len(data["missing"])} missing schema/table/column entries; see ignored inventory.json', file=sys.stderr)
            return 1
        if not data['usable_for_write_phase']:
            print('Inventory recorded but unverified candidates or snapshot/grants prevent write-phase use.', file=sys.stderr)
            return 1
        print('Read-only inventory saved under ignored .codex/full-flow-20260928/inventory.json')
        return 0
    except (ValueError, RuntimeError) as exc:
        print(str(exc), file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
