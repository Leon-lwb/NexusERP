"""仅在显式启用时初始化一次性 NexusAI MySQL 库，任何不确定性均拒绝执行。

不使用已有 plus 库，不在缺少 --database 时执行主 SQL，
导入部分失败时不自动删除数据库。本工具不启动应用。"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
ROOT = Path(__file__).resolve().parents[1]
schemas = {
    'ry-vue.sql': 'nexus_ai_decision_demo_sys',
    'ry-erp.sql': 'nexus_ai_decision_demo_erp',
}
ERP = schemas['ry-erp.sql']
MYSQL = ['mysql', '--protocol=TCP', '--host=127.0.0.1', '--port=3306', '--user=root',
         '--default-character-set=utf8mb4', '--batch', '--skip-column-names']


def schema_for(filename):
    if filename not in schemas:
        raise ValueError('Only the two fixed system/ERP SQL scripts may be imported')
    return schemas[filename]


def check_schema(name):
    if name not in schemas.values() or not name.startswith('nexus_ai_decision_demo_'):
        raise ValueError('Refusing to select a non-disposable schema')
    return name


def validate_dump(text):
    if not text or not re.search(r'(?im)^\s*CREATE TABLE\s+`', text):
        raise ValueError('Not a valid main schema dump')
    forbidden = re.compile(r'(?i)^(?:USE\b|(?:CREATE|DROP)\s+DATABASE\b|'
                           r'CREATE\s+(?:DEFINER\b|PROCEDURE\b|FUNCTION\b|TRIGGER\b|EVENT\b|USER\b)|'
                           r'DROP\s+(?:PROCEDURE|FUNCTION|TRIGGER|EVENT|USER)\b|'
                           r'GRANT\b|REVOKE\b|FLUSH\b|SET\s+GLOBAL\b|'
                           r'SOURCE\b|DELIMITER\b|PREPARE\b|EXECUTE\b|LOAD\s+DATA\b|\\\.)')
    qualified = re.compile(r'`[^`\n]+`\s*\.\s*`[^`\n]+`')
    in_block_comment = False
    for number, line in enumerate(text.splitlines(), 1):
        head = line.lstrip()
        if in_block_comment:
            if '*/' not in head:
                continue
            head = head.split('*/', 1)[1].lstrip()
            in_block_comment = False
        if head.startswith(('--', '#', '//')):
            continue
        while head.startswith('/*'):
            if '*/' not in head:
                in_block_comment = True
                break
            head = head.split('*/', 1)[1].lstrip()
        if in_block_comment or not head:
            continue
        if forbidden.match(head) or qualified.search(head):
            raise ValueError(f'Unsafe schema switch, import or qualified table at line {number}')


def validate_seed(text):
    if not text or 'START TRANSACTION;' not in text or 'COMMIT;' not in text:
        raise ValueError('Incomplete demo seed')
    guard = f"DATABASE() = '{ERP}'"
    found = 0
    # 拆分语句前删除生成器行注释：注释可能含分号，
    # 不得将注释拆成不存在的 SQL 语句。
    uncommented = '\n'.join(line for line in text.splitlines()
                            if not line.lstrip().startswith('--'))
    for statement in uncommented.split(';'):
        # 剩余语句仅允许事务边界或一条带范围护栏的 INSERT。
        body = statement.strip()
        if not body or body.upper() in ('START TRANSACTION', 'COMMIT'):
            continue
        if not re.match(r'(?i)^INSERT\s+INTO\s+`', body) or guard not in body:
            raise ValueError('Demo seed contains unguarded or unsupported SQL statement')
        found += 1
    if found < 40:
        raise ValueError('Demo seed must cover all 40 cases')


def mysql_query(sql, database=None):
    command = list(MYSQL)
    if database is not None:
        command.append('--database=' + check_schema(database))
    command.append('--execute=' + sql)
    completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                               timeout=15, check=False)
    if completed.returncode:
        raise RuntimeError('Local MySQL query failed; details withheld to avoid leaking credentials')
    return completed.stdout.decode('utf-8', errors='replace').strip()


def import_file(path, schema):
    command = MYSQL + ['--database=' + check_schema(schema)]
    with path.open('rb') as content:
        completed = subprocess.run(command, stdin=content, stdout=subprocess.DEVNULL,
                                   stderr=subprocess.PIPE, timeout=300, check=False)
    if completed.returncode:
        raise RuntimeError(f'Disposable import failed for {schema}; schema kept for manual inspection, '
                           'no DROP issued')


def main():
    parser = argparse.ArgumentParser(description='Preflight or explicitly create new local disposable demo schemas')
    parser.add_argument('--apply', action='store_true', help='create schemas and import SQL after safety preflight')
    args = parser.parse_args()
    for file, schema in schemas.items():
        sql_path = REPO / 'script/sql' / file
        validate_dump(sql_path.read_text(encoding='utf-8-sig'))
        print(f'Preflight: {file} targets only {schema} ({sql_path.stat().st_size} bytes)')
    for file in ('demo-master-data.sql', 'demo-business-data.sql'):
        validate_seed((ROOT / 'seed' / file).read_text(encoding='utf-8'))
    if not args.apply:
        print('DRY RUN ONLY; pass --apply explicitly after reviewing the new schema names.')
        return
    for schema in schemas.values():
        if mysql_query(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '{check_schema(schema)}'"):
            raise RuntimeError(f'{schema} already exists; refusing to import or reset any database')
    for file, schema in schemas.items():
        mysql_query(f'CREATE DATABASE `{check_schema(schema)}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        if mysql_query('SELECT DATABASE()', schema) != schema:
            raise RuntimeError(f'Wrong selected database for {schema}; stopped before SQL import')
        print(f'Importing {file} into newly created {schema} (plus schemas untouched)')
        import_file(REPO / 'script/sql' / file, schema)
    for file in ('demo-master-data.sql', 'demo-business-data.sql'):
        print(f'Importing guarded {file} into {ERP}')
        import_file(ROOT / 'seed' / file, ERP)
    print('Isolated SQL import complete. No application, Flow, RabbitMQ or HTTP verification implied.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f'ABORTED: {error}', file=sys.stderr)
        sys.exit(1)