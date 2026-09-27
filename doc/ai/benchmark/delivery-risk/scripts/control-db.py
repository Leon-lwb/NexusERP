"""仅供离线 CONTROL 测试使用的 SQL 辅助工具，无 HTTP 路由且不打入生产包。
加载凭据及连接 MySQL 前先校验；实际补偿只能经过 ERP 服务。"""
import importlib.util,json,sys,re
from pathlib import Path
allowed={'nexus_ai_decision_control_20260927_'+x for x in ('sys','erp','mes','iot')}

def validate_sql(sql):
    qualified=re.findall(r'`([^`]+)`\.',sql)
    references=re.findall(r'(?i)\b(?:FROM|JOIN|UPDATE|INTO|TABLE)\s+(`[^`]+`\.`[^`]+`|[a-zA-Z0-9_.-]+)',sql)
    database_refs=re.findall(r'(?i)\b(nexus_ai_decision_[a-z0-9_]+|information_schema|performance_schema|mysql)\s*`?\s*\.',sql)
    if (any(x.lower() not in allowed for x in database_refs) or not qualified or any(x not in allowed for x in qualified)
        or not references or any(not re.fullmatch(r'`(?:'+'|'.join(allowed)+r')`\.`[a-zA-Z0-9_]+`', x) for x in references)
        or ';' in sql.rstrip().rstrip(';')
        or not re.match(r'(?i)^\s*(SELECT|INSERT|UPDATE|SHOW COLUMNS FROM|CHECKSUM TABLE)\b',sql)
        or re.search(r'(?i)\b(DROP|CREATE|ALTER|GRANT|REVOKE|USE|SOURCE|TRUNCATE|DELETE|CALL|OUTFILE|DUMPFILE|PREPARE|LOAD)\b',sql)):
        raise RuntimeError('CONTROL SQL boundary refused')


def main():
    sql=json.loads(sys.stdin.read())['sql']
    validate_sql(sql)
    root=Path(__file__).resolve().parents[5]
    spec=importlib.util.spec_from_file_location('control_db_provision',root/'doc/ai/benchmark/delivery-risk/scripts/prepare-decision-demo.py')
    p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
    values=p.env_values();p.ROOT_PASSWORD=values['DECISION_DEMO_MYSQL_ROOT_PASSWORD'];p.MYSQL.append('--raw')
    assert Path(p.mysql_query('SELECT @@datadir;').rstrip('\\/')).resolve()==p.DATA_DIR.resolve()
    print(json.dumps(p.mysql_query(sql).splitlines(),ensure_ascii=True))

if __name__=='__main__':main()
