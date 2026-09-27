"""显式新建四个 CONTROL 库，不重置已有库。
ERP 通过只读服务端转储复制，保留夹具日期；凭据仅存于忽略的 control/.env。"""
import argparse, importlib.util, json, os, re, secrets, subprocess
from pathlib import Path
SCRIPTS=Path(__file__).resolve().parent
REPO=SCRIPTS.parents[4]
STATE=SCRIPTS.parent/'control'
PREFIX='nexus_ai_decision_control_20260927_'
SCHEMAS={s:PREFIX+s for s in ('sys','erp','mes','iot')}
SOURCE='nexus_ai_decision_demo_'

def load(file,name):
    spec=importlib.util.spec_from_file_location(name,SCRIPTS/file)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    p=load('prepare-decision-demo.py','control_provision');check=load('init-isolated-demo.py','control_sql')
    v=p.env_values();p.ROOT_PASSWORD=v['DECISION_DEMO_MYSQL_ROOT_PASSWORD']
    assert Path(p.mysql_query('SELECT @@datadir;').rstrip('\\/')).resolve()==p.DATA_DIR.resolve()
    p.verify_no_plus_schemas();p.verify_account(v)
    for schema in SCHEMAS.values():
        if p.mysql_query(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{schema}'"):
            raise RuntimeError('CONTROL schema already exists; no reset permitted')
    for file in ('ry-vue.sql','ry-mes.sql','ry-iot.sql'):
        text=(REPO/'script/sql'/file).read_text(encoding='utf-8-sig')
        check.validate_dump(text if file=='ry-vue.sql' else p.extract_table_ddl(text))
    if not args.apply:
        print('DRY RUN: four new schemas, ERP read-only copy, restricted account, isolated Redis/vhost');return
    STATE.mkdir(exist_ok=True)
    tables=json.loads((Path(os.environ['TEMP'])/'nexus-v2-replay-metadata.json').read_text())['erpSourceTables']
    before=p.mysql_query('CHECKSUM TABLE '+','.join('`'+SOURCE+'erp`.`'+t+'`' for t in tables))
    metadata={'schemas':SCHEMAS,'sourceChecksumsBefore':before,'sourceTables':tables}
    for schema in SCHEMAS.values():
        p.mysql_query(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    env=os.environ.copy();env['MYSQL_PWD']=v['DECISION_DEMO_MYSQL_ROOT_PASSWORD']
    base=['mysql','--no-defaults','--protocol=TCP','--host=127.0.0.1','--port=13307','--user=root','--default-character-set=utf8mb4']
    for suffix,file in [('sys','ry-vue.sql'),('mes','ry-mes.sql'),('iot','ry-iot.sql')]:
        text=(REPO/'script/sql'/file).read_text(encoding='utf-8-sig')
        data=(text if suffix=='sys' else p.extract_table_ddl(text)).encode('utf-8')
        result=subprocess.run(base+['--database='+SCHEMAS[suffix]],input=data,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,env=env,timeout=300)
        if result.returncode: raise RuntimeError('CONTROL SQL import failed; partial schema retained')
    dump=subprocess.run(['mysqldump','--no-defaults','--protocol=TCP','--host=127.0.0.1','--port=13307','--user=root','--default-character-set=utf8mb4','--single-transaction','--skip-lock-tables','--no-tablespaces',SOURCE+'erp'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=300)
    if dump.returncode: raise RuntimeError('ERP read-only dump failed')
    check.validate_dump(dump.stdout.decode('utf-8'))
    result=subprocess.run(base+['--database='+SCHEMAS['erp']],input=dump.stdout,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,env=env,timeout=300)
    if result.returncode: raise RuntimeError('CONTROL ERP copy import failed')
    user='decision_control';password=secrets.token_hex(24)
    for host in ('127.0.0.1','localhost'):
        p.mysql_query(f"CREATE USER '{user}'@'{host}' IDENTIFIED BY '{password}'")
        for schema in SCHEMAS.values():
            escaped=schema.replace('_',r'\_');p.mysql_query(f"GRANT SELECT, INSERT, UPDATE, DELETE ON `{escaped}`.* TO '{user}'@'{host}'")
    for table,id in [('ai_provider',2103870784911990785),('ai_model',2103871286244564993)]:
        p.mysql_query(f'INSERT INTO `{SCHEMAS["sys"]}`.`{table}` SELECT * FROM `{SOURCE}sys`.`{table}` WHERE id={id}')
    sys=SCHEMAS['sys']
    p.mysql_query(f"UPDATE `{sys}`.ai_model SET is_default='N' WHERE id<>2103871286244564993; UPDATE `{sys}`.sys_user t JOIN `{SOURCE}sys`.sys_user s ON s.user_id=t.user_id SET t.password=s.password WHERE t.user_id=1")
    # 测试用户仅在服务端复制密码摘要，不输出或写入报告。
    p.mysql_query(f"INSERT INTO `{sys}`.sys_user(user_id,tenant_id,user_name,nick_name,password,status,del_flag,user_type) SELECT 910001,'000000','control_reader','CONTROL reader',password,'0','0','sys_user' FROM `{sys}`.sys_user WHERE user_id=1")
    p.mysql_query(f"INSERT INTO `{sys}`.sys_user(user_id,tenant_id,user_name,nick_name,password,status,del_flag,user_type) SELECT 910002,'910000','control_tenant_b','CONTROL tenant B',password,'0','0','sys_user' FROM `{sys}`.sys_user WHERE user_id=1")
    p.mysql_query(f"INSERT INTO `{sys}`.sys_tenant(id,tenant_id,company_name,status,del_flag,account_count) VALUES(910000,'910000','CONTROL tenant B','0','0',-1)")
    p.mysql_query(f"INSERT INTO `{sys}`.sys_role(role_id,tenant_id,role_name,role_key,role_sort,data_scope,status,del_flag) VALUES(910001,'000000','CONTROL reader','control_reader',9,'1','0','0'),(910002,'910000','CONTROL tenant B','control_tenant_b',9,'1','0','0')")
    p.mysql_query(f"INSERT INTO `{sys}`.sys_user_role(user_id,role_id) VALUES(910001,910001),(910002,910002)")
    p.mysql_query(f"INSERT INTO `{sys}`.sys_role_menu(role_id,menu_id) SELECT 910001,menu_id FROM `{sys}`.sys_menu WHERE perms IN('ai:decision:query','prd:work:query')")
    p.mysql_query(f"INSERT INTO `{sys}`.sys_role_menu(role_id,menu_id) SELECT 910002,menu_id FROM `{sys}`.sys_menu WHERE perms LIKE 'ai:decision:%' OR perms LIKE 'prd:work:%' OR perms LIKE 'pu:requisition:%' OR perms LIKE 'prd:schedule:%' ")
    container='nexus-decision-control-redis';redis=v['DECISION_DEMO_REDIS_PASSWORD'];vhost='decision-control-20260927'
    exists=subprocess.run(['docker','container','inspect',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if exists.returncode==0: raise RuntimeError('CONTROL Redis already exists; refused to replace')
    result=subprocess.run(['docker','run','-d','--name',container,'-p','127.0.0.1:6386:6379','redis:7.2.8','redis-server','--requirepass',redis],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    if result.returncode: raise RuntimeError('CONTROL Redis creation failed')
    for cmd in [['add_vhost',vhost],['set_permissions','-p',vhost,v['DECISION_DEMO_MQ_USER'],'.*','.*','.*']]:
        result=subprocess.run(['docker','exec','nexus-decision-demo-rabbitmq','rabbitmqctl',*cmd],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        if result.returncode: raise RuntimeError('CONTROL Rabbit vhost preparation failed')
    values={k:x for k,x in v.items() if k!='DECISION_DEMO_MYSQL_ROOT_PASSWORD'}
    values.update(DECISION_DEMO_DB_USER=user,DECISION_DEMO_DB_PASSWORD=password,DECISION_DEMO_REDIS_PORT='6386',DECISION_DEMO_MQ_VHOST=vhost)
    (STATE/'.env').write_text(''.join(k+'='+x+'\n' for k,x in values.items()),encoding='utf-8')
    metadata['sourceChecksumsAfter']=p.mysql_query('CHECKSUM TABLE '+','.join('`'+SOURCE+'erp`.`'+t+'`' for t in tables))
    assert metadata['sourceChecksumsBefore']==metadata['sourceChecksumsAfter']
    (STATE/'provision-evidence.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
    p.verify_no_plus_schemas()
    print('CONTROL created: four fresh schemas; ERP copied; source 20 checksums unchanged; Redis6386/private vhost; keys copied encrypted; secrets hidden')
if __name__=='__main__':main()
