"""带护栏的本地演示环境准备器，不访问或重置 plus 数据库。"""
import argparse
import importlib.util
import os
import re
import secrets
import shutil
import socket
import time
import subprocess
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
ROOT = SCRIPTS.parent
REPO = SCRIPTS.parents[4]
DATA_ROOT = REPO.parent / 'nexus-ai-decision-demo-mysql'
DATA_DIR = DATA_ROOT / 'data'
MARKER = DATA_ROOT / 'secured-instance.txt'
MYSQLD_BIN = Path(shutil.which('mysqld') or 'D:/software/mysql-5.7.14/bin/mysqld.exe')
ENV_FILE = ROOT / '.env'  # 仓库忽略所有层级的 .env 文件。
SCHEMAS = {'ry-mes.sql': 'nexus_ai_decision_demo_mes',
           'ry-iot.sql': 'nexus_ai_decision_demo_iot'}
CORE_SCHEMAS = {'ry-vue.sql': 'nexus_ai_decision_demo_sys',
                'ry-erp.sql': 'nexus_ai_decision_demo_erp'}
ALL_SCHEMAS = (*CORE_SCHEMAS.values(), *SCHEMAS.values())
EXPECTED_TABLES = {'ry-mes.sql': 76, 'ry-iot.sql': 16}
ACCOUNT = 'decision_demo'
MYSQL = ['mysql', '--no-defaults', '--protocol=TCP', '--host=127.0.0.1', '--port=13307',
         '--user=root', '--default-character-set=utf8mb4', '--batch', '--skip-column-names']
ROOT_PASSWORD = None


def schema_for(file):
    if file not in SCHEMAS:
        raise ValueError('Only MES and IoT table-only dumps are accepted here')
    return SCHEMAS[file]


def extract_table_ddl(text):
    """仅提取未限定数据库名的 CREATE TABLE，排除转储数据及高权限例程。"""
    executable = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    if '/*' in executable or '*/' in executable:
        raise ValueError('Unclosed SQL block comment')
    executable = re.sub(r'(?m)^\s*--[^\n]*$', '', executable)
    if re.search(r'(?im)^\s*(?:USE\b|(?:DROP|CREATE)\s+DATABASE\b|'
                 r'CALL\b|SOURCE\b|GRANT\b|REVOKE\b|(?:DROP|CREATE)\s+USER\b|'
                 r'LOAD\s+DATA\b|\\\.)', executable):
        raise ValueError('Source dump contains a schema switch or unsupported command')
    if re.search(r'`[^`\n]+`\s*\.\s*`[^`\n]+`', executable):
        raise ValueError('Source dump references a qualified table')
    statements = re.findall(r'(?ms)^CREATE TABLE\s+`[a-zA-Z0-9_]+`\s*\(.*?^\)\s*ENGINE\s*=\s*[^\n]+;\s*$', text)
    count = len(re.findall(r'(?m)^CREATE TABLE\s+', text))
    if not statements or len(statements) != count:
        raise ValueError('Unable to extract every CREATE TABLE statement safely')
    names = re.findall(r'^CREATE TABLE\s+`([a-zA-Z0-9_]+)`', '\n'.join(statements), re.M)
    if len(names) != len(set(names)):
        raise ValueError('Source dump contains duplicate table names')
    return ('SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\n'
            + '\n'.join(statements) + '\nSET FOREIGN_KEY_CHECKS = 1;\n')


def grant_statements(user, password):
    if user != ACCOUNT or not re.fullmatch(r'[0-9a-f]{48}', password):
        raise ValueError('Invalid dedicated demo account')
    statements = []
    for host in ('127.0.0.1', 'localhost'):
        principal = f"'{ACCOUNT}'@'{host}'"
        statements.append(f"CREATE USER {principal} IDENTIFIED BY '{password}';")
        for schema in ALL_SCHEMAS:
            # 数据库级 GRANT 中，下划线默认会作为 LIKE 通配符，必须转义。
            escaped = schema.replace('_', r'\_')
            statements.append(f'GRANT SELECT, INSERT, UPDATE, DELETE ON `{escaped}`.* TO {principal};')
    return statements


def mysql_query(sql, database=None, password=None, user='root', expect_success=True, timeout=60):
    if database is not None and database not in ALL_SCHEMAS:
        raise ValueError('Refusing to select a non-demo schema')
    if user == 'root' and ROOT_PASSWORD is None:
        raise RuntimeError('Isolated MySQL root credential not initialized')
    cmd = [*MYSQL[:5], f'--user={user}', *MYSQL[6:]]
    if database:
        cmd.append(f'--database={database}')
    env = os.environ.copy()
    env['MYSQL_PWD'] = (ROOT_PASSWORD if password is None else password) if user == 'root' else password
    result = subprocess.run(cmd, input=sql.encode('utf-8'), stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=env, timeout=timeout, check=False)
    if expect_success and result.returncode:
        raise RuntimeError('Isolated MySQL operation failed; details withheld to protect credentials')
    return result if not expect_success else result.stdout.decode('utf-8', errors='replace').strip()


def fixed_env_values():
    return {
        'DECISION_DEMO_DB_HOST': '127.0.0.1', 'DECISION_DEMO_DB_PORT': '13307',
        'DECISION_DEMO_DB_USER': ACCOUNT,
        'DECISION_DEMO_REDIS_HOST': '127.0.0.1', 'DECISION_DEMO_REDIS_PORT': '6385',
        'DECISION_DEMO_MQ_HOST': '127.0.0.1', 'DECISION_DEMO_MQ_PORT': '5685',
        'DECISION_DEMO_MQ_USER': ACCOUNT, 'DECISION_DEMO_MQ_VHOST': '/decision-demo',
    }


def env_values(create=False):
    expected = fixed_env_values()
    secret_keys = ('DECISION_DEMO_MYSQL_ROOT_PASSWORD', 'DECISION_DEMO_DB_PASSWORD',
                   'DECISION_DEMO_REDIS_PASSWORD', 'DECISION_DEMO_MQ_PASSWORD')
    if ENV_FILE.exists():
        values = {}
        for line in ENV_FILE.read_text(encoding='utf-8').splitlines():
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            if not re.fullmatch(r'DECISION_DEMO_[A-Z_]+=[a-zA-Z0-9_./-]+', line):
                raise ValueError('Unsupported demo .env line; no secret overwritten')
            key, value = line.split('=', 1)
            if key in values:
                raise ValueError('Duplicate demo .env key')
            values[key] = value
        if (set(values) != set(expected) | set(secret_keys)
                or any(values.get(key) != value for key, value in expected.items())
                or any(not re.fullmatch(r'[0-9a-f]{48}', values[key]) for key in secret_keys)):
            raise ValueError('Existing demo .env does not match isolated runtime; review it manually')
        return values
    if not create:
        raise RuntimeError('Missing local demo secret file; run --apply first')
    values = {**expected, **{key: secrets.token_hex(24) for key in secret_keys}}
    # 采用独占创建，不覆盖已有凭据文件。
    with ENV_FILE.open('x', encoding='utf-8', newline='\n') as handle:
        for key, value in values.items():
            handle.write(f'{key}={value}\n')
    return values


def schema_exists(schema):
    if schema not in ALL_SCHEMAS:
        raise ValueError('Unknown demo schema')
    return mysql_query("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + schema + "';") == schema


def table_count(schema):
    if schema not in ALL_SCHEMAS:
        raise ValueError('Unknown demo schema')
    return int(mysql_query("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + schema + "';"))


def inspect_sources():
    ddl = {}
    for file, schema in SCHEMAS.items():
        text = (REPO / 'script/sql' / file).read_text(encoding='utf-8-sig')
        prepared = extract_table_ddl(text)
        if prepared.count('CREATE TABLE `') != EXPECTED_TABLES[file]:
            raise ValueError(f'Source table count changed for {file}; audit before import')
        ddl[schema] = prepared
    return ddl


def inspect_core_sources():
    initializer_path = SCRIPTS / 'init-isolated-demo.py'
    spec = importlib.util.spec_from_file_location('original_safe_initializer', initializer_path)
    initializer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(initializer)
    sources = {}
    for file, schema in CORE_SCHEMAS.items():
        text = (REPO / 'script/sql' / file).read_text(encoding='utf-8-sig')
        initializer.validate_dump(text)  # 拒绝切换数据库及跨库限定表名。
        sources[schema] = text
    for file in ('demo-master-data.sql', 'demo-business-data.sql'):
        initializer.validate_seed((ROOT / 'seed' / file).read_text(encoding='utf-8'))
    return sources


def check_data_directory(candidate):
    """新数据目录必须位于明确指定的工作区同级路径，不得进入 plus 目录。"""
    expected = Path(os.path.abspath(DATA_DIR))
    path = Path(os.path.abspath(candidate))
    if path != expected or expected.resolve() != expected:
        raise ValueError('Refusing an unknown or linked MySQL data directory')
    for parent in (DATA_ROOT, DATA_DIR):
        if parent.is_symlink() or (hasattr(parent, 'is_junction') and parent.is_junction()):
            raise ValueError('MySQL data directory cannot be a symlink or junction')
    return DATA_DIR


def port_listening(port=13307):
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=1):
            return True
    except OSError:
        return False


def mysql_command(initializing=False):
    check_data_directory(DATA_DIR)
    if os.name != 'nt' or not MYSQLD_BIN.is_file() or MYSQLD_BIN.parent.parent.resolve() == DATA_ROOT.resolve():
        raise RuntimeError('Only the installed Windows mysqld from outside the demo directory is allowed')
    if initializing:
        return [str(MYSQLD_BIN), '--no-defaults', '--initialize-insecure',
                f'--basedir={MYSQLD_BIN.parent.parent}', f'--datadir={DATA_DIR}', '--console']
    return [str(MYSQLD_BIN), '--no-defaults',
            f'--basedir={MYSQLD_BIN.parent.parent}', f'--datadir={DATA_DIR}',
            '--port=13307', '--bind-address=127.0.0.1', '--lower_case_table_names=1',
            '--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci',
            '--sql-mode=NO_ENGINE_SUBSTITUTION', '--default-time-zone=+08:00',
            f'--pid-file={DATA_ROOT / "mysqld.pid"}', '--console']


def start_local_mysql(values, start=True):
    check_data_directory(DATA_DIR)
    created = False
    process = None
    if not MARKER.exists():
        if not start:
            raise RuntimeError('Local isolated MySQL has not been initialized')
        if DATA_ROOT.exists():
            raise RuntimeError('Unexpected existing demo data directory; no auto-reset allowed')
        if port_listening():
            raise RuntimeError('Port 13307 is occupied by another process; no initialization')
        DATA_ROOT.mkdir()
        DATA_DIR.mkdir()
        initialization = subprocess.run(mysql_command(initializing=True), stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, timeout=150, check=False)
        if initialization.returncode:
            raise RuntimeError('New isolated MySQL initialization failed; data kept for audit')
        created = True
    elif MARKER.read_text(encoding='utf-8') != 'nexus-ai-decision-demo-mysql:13307:secured\n':
        raise RuntimeError('Unexpected MySQL instance marker; no reuse allowed')
    if created and port_listening():
        raise RuntimeError('Port 13307 became occupied during initialization')
    if not port_listening() and start:
        with (DATA_ROOT / 'mysqld.log').open('ab') as log:
            flags = subprocess.DETACHED_PROCESS | subprocess.CREATE_NO_WINDOW
            process = subprocess.Popen(mysql_command(), stdout=log, stderr=subprocess.STDOUT,
                                       creationflags=flags)
        for _ in range(90):
            if process.poll() is not None:
                raise RuntimeError('Isolated MySQL process exited; check private mysqld.log')
            if port_listening():
                break
            time.sleep(1)
    if not port_listening():
        raise RuntimeError('Isolated MySQL 13307 is not listening')
    if created:
        try:
            # --initialize-insecure 初始化的 root 暂时仅可本机访问，必须立即设置密码。
            mysql_query("ALTER USER 'root'@'localhost' IDENTIFIED BY '" +
                        values['DECISION_DEMO_MYSQL_ROOT_PASSWORD'] + "';", password='')
        except Exception:
            if process is not None:
                process.terminate()  # 仅处理上方精确创建的新进程。
            raise RuntimeError('New isolated MySQL could not be secured; process stopped')
        MARKER.write_text('nexus-ai-decision-demo-mysql:13307:secured\n', encoding='utf-8')
    actual = mysql_query('SELECT @@datadir;').rstrip('\\/')
    if Path(actual).resolve() != DATA_DIR.resolve():
        raise RuntimeError('Port 13307 is not the explicitly named isolated MySQL datadir')
    if not mysql_query('SHOW GRANTS FOR CURRENT_USER();'):
        raise RuntimeError('Isolated MySQL has no privilege enforcement')
    print('Dedicated local MySQL 5.7 ready on loopback 13307 (separate data directory)')

def docker_ready(values):
    cmd = ['docker', 'compose', '-p', 'nexus-decision-demo', '--env-file', str(ENV_FILE),
           '-f', str(ROOT / 'compose.demo.yml')]
    for suffix in (['config', '--quiet'], ['up', '-d', '--wait', '--wait-timeout', '180']):
        result = subprocess.run(cmd + suffix, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=240, check=False)
        if result.returncode:
            raise RuntimeError('Isolated Docker Compose failed; existing containers were not targeted')
    print('Independent Redis/RabbitMQ containers are healthy on loopback 6385/5685')


def verify_no_plus_schemas():
    found = mysql_query("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE "
                        "SCHEMA_NAME IN ('ry-vue','ry-erp','ry-mes','ry-iot');")
    if found:
        raise RuntimeError('A plus schema exists on the isolated MySQL server; refusing to proceed')

def verify_existing_core():
    for schema in CORE_SCHEMAS.values():
        if not schema_exists(schema) or table_count(schema) < 100:
            raise RuntimeError(f'Isolated {schema} missing or incomplete; no fallback to plus')
    if mysql_query('SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 1850 AND 1859;',
                   CORE_SCHEMAS['ry-vue.sql']) != '10':
        raise RuntimeError('Isolated Decision menu 1850-1859 incomplete')
    if mysql_query("SELECT COUNT(*) FROM prd_work_order WHERE tenant_id='000000' "
                   "AND wo_no LIKE 'DEMO-AI-DECISION-%';", CORE_SCHEMAS['ry-erp.sql']) != '40':
        raise RuntimeError('Isolated ERP does not have 40 demo work orders')


def create_core(sources):
    for file, schema in CORE_SCHEMAS.items():
        if schema_exists(schema):
            continue
        mysql_query(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        if mysql_query('SELECT DATABASE();', schema) != schema:
            raise RuntimeError(f'Unexpected MySQL schema selection: {schema}')
        mysql_query(sources[schema], schema, timeout=300)
        print(f'Imported {file} into new isolated {schema}')
        if file == 'ry-erp.sql':
            for seed in ('demo-master-data.sql', 'demo-business-data.sql'):
                mysql_query((ROOT / 'seed' / seed).read_text(encoding='utf-8'), schema, timeout=300)
    verify_existing_core()
    print('Isolated core schema and 40 ERP work orders verified')


def create_mes_iot(ddl):
    existing = {schema: schema_exists(schema) for schema in SCHEMAS.values()}
    for file, schema in SCHEMAS.items():
        if existing[schema] and table_count(schema) != EXPECTED_TABLES[file]:
            raise RuntimeError(f'{schema} is partial; no reset or DROP allowed')
    for file, schema in SCHEMAS.items():
        if existing[schema]:
            print(f'Preserved already provisioned {schema} ({table_count(schema)} tables)')
            continue
        mysql_query(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        if mysql_query('SELECT DATABASE();', schema) != schema:
            raise RuntimeError(f'Unexpected MySQL schema selection: {schema}')
        mysql_query(ddl[schema], schema, timeout=300)
        if table_count(schema) != EXPECTED_TABLES[file]:
            raise RuntimeError(f'{schema} import incomplete; no DROP issued')
        print(f'Created {schema} with {EXPECTED_TABLES[file]} empty tables')


def verify_account(values):
    user, password = values['DECISION_DEMO_DB_USER'], values['DECISION_DEMO_DB_PASSWORD']
    for schema in ALL_SCHEMAS:
        if mysql_query('SELECT DATABASE();', schema, password, user) != schema:
            raise RuntimeError(f'Demo account selected unexpected schema: {schema}')
    denied = mysql_query('SELECT 1 FROM `ry-vue`.`sys_menu` LIMIT 1;',
                         password=password, user=user, expect_success=False)
    error = denied.stderr.decode('utf-8', errors='replace').lower()
    if denied.returncode == 0 or not any(code in error for code in ('error 1142', 'error 1044', 'error 1049')):
        raise RuntimeError('Demo account cannot be proven to have no plus access')


def verify_only():
    global ROOT_PASSWORD
    values = env_values()
    ROOT_PASSWORD = values['DECISION_DEMO_MYSQL_ROOT_PASSWORD']
    for file, schema in CORE_SCHEMAS.items():
        if not schema_exists(schema) or table_count(schema) < 100:
            raise RuntimeError(f'Core demo database not ready: {schema}')
    verify_no_plus_schemas()
    verify_existing_core()
    for file, schema in SCHEMAS.items():
        if not schema_exists(schema) or table_count(schema) != EXPECTED_TABLES[file]:
            raise RuntimeError(f'Demo database not ready: {schema}')
    verify_account(values)
    start_local_mysql(values, start=False)
    for name in ('nexus-decision-demo-redis', 'nexus-decision-demo-rabbitmq'):
        result = subprocess.run(['docker', 'inspect', '--format', '{{.State.Health.Status}}', name],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20, check=False)
        if result.returncode or result.stdout.decode('utf-8', errors='replace').strip() != 'healthy':
            raise RuntimeError(f'Isolated container not healthy: {name}')
    print('Ready: four demo schemas, restricted account, local MySQL and two containers verified')

def apply():
    global ROOT_PASSWORD
    ddl = inspect_sources()
    core = inspect_core_sources()
    values = env_values(create=True)
    ROOT_PASSWORD = values['DECISION_DEMO_MYSQL_ROOT_PASSWORD']
    start_local_mysql(values)
    docker_ready(values)
    # 执行任何 SQL 写入前，确认隔离服务器已启用授权表。
    if mysql_query('SHOW GRANTS FOR CURRENT_USER();') == '' or not mysql_query('SELECT CURRENT_USER();').startswith('root@'):
        raise RuntimeError('Isolated MySQL privilege checks unavailable; no SQL import')
    verify_no_plus_schemas()
    for schema in ALL_SCHEMAS:
        if schema_exists(schema) and (table_count(schema) == 0 or
                                      (schema in SCHEMAS.values() and table_count(schema) !=
                                       EXPECTED_TABLES[next(file for file, name in SCHEMAS.items() if name == schema)])):
            raise RuntimeError(f'Isolated {schema} already exists partially; audit manually')
    create_core(core)
    create_mes_iot(ddl)
    existing_users = mysql_query("SELECT COUNT(*) FROM mysql.user WHERE User='" + ACCOUNT + "';")
    if existing_users not in ('0', '2'):
        raise RuntimeError('Demo account exists with unexpected host entries; audit manually')
    if existing_users == '0':
        for statement in grant_statements(ACCOUNT, values['DECISION_DEMO_DB_PASSWORD']):
            mysql_query(statement)
    verify_account(values)
    print('Demo account has DML access to four demo schemas and is denied plus access')
    print(f'Local secret file: {ENV_FILE} (ignored by Git; values withheld)')
    print('Existing 3306 MySQL and its plus schemas were never used as an import target')


def main():
    parser = argparse.ArgumentParser(description='Prepare isolated localhost demo services only')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--apply', action='store_true', help='explicitly create missing isolated resources')
    group.add_argument('--verify', action='store_true', help='read-only preflight of the isolated runtime')
    args = parser.parse_args()
    ddl = inspect_sources()
    for schema, text in ddl.items():
        print(f'Preflight: {schema} -> {text.count("CREATE TABLE `")} empty tables, no DML/routines')
    if args.verify:
        verify_only()
    elif args.apply:
        apply()
    else:
        print('DRY RUN ONLY; --apply uses isolated local MySQL 13307 and Redis/RabbitMQ containers.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.TimeoutExpired) as exc:
        print(f'ABORTED: {exc}')  # 不输出子进程标准错误、SQL 或生成的密码。
        raise SystemExit(1)
