"""仅维护本地测试库，不绕过 HTTP 权限或执行业务动作。
扩展节点类型列，并可将已审计的动作前长度错误运行标为失效。
不标记成功、不重置审批、不重试流程、不修改 plus 库。"""
import argparse,hashlib,importlib.util,json,os,re,subprocess,uuid
from datetime import datetime
from pathlib import Path
SYS='nexus_ai_decision_demo_sys'
ERP='nexus_ai_decision_demo_erp'
REPO=Path(__file__).resolve().parents[5]

def decimal(value):
    if not isinstance(value,str) or not re.fullmatch(r'[1-9][0-9]{0,18}',value):
        raise ValueError('Invalid repair identifier')
    return value

def validate_recovery(facts,run_id):
    decimal(run_id)
    try:
        run,flow,actions=facts['run'],facts['flow'],facts['actions']
        if (run['id']!=run_id or run['tenant']!='000000' or run['status']!='APPROVED'
            or run['flowId']!=flow['id'] or flow['tenant']!=run['tenant']
            or flow['status']!='FAILED' or flow['runId']!=run_id
            or flow['flowKey']!='system_delivery_risk_execution_v1_000000'
            or flow['nodeTypeError']!=1 or flow['succeeded']!=0 or flow['failed']!=0
            or any(type(facts[k]) is not int or facts[k]!=0 for k in ('nodeRows','businessRows','otherExecutions'))
            or any(type(flow[k]) is not int for k in ('succeeded','failed','nodeTypeError'))
            or not isinstance(actions,list) or not actions
            or not any(a['approval']=='APPROVED' for a in actions)):
            raise ValueError('Repair cannot prove a zero-write pre-action failure')
        decimal(flow['id'])
        if type(run['version']) is not int or run['version']<0:raise ValueError('Invalid repair version')
        for action in actions:
            decimal(action['id'])
            if (action['type'] not in {'CREATE_PURCHASE_REQUISITION_DRAFT','CREATE_SCHEDULE_PLAN_DRAFT'}
                or action['approval'] not in {'APPROVED','REJECTED'} or action['execution']!='NOT_STARTED'
                or action['resultBizId'] is not None or action['resultJson'] is not None):
                raise ValueError('Repair action may have progressed or lacks a reviewed business type')
    except (KeyError,TypeError) as invalid:
        raise ValueError('Repair evidence is incomplete') from invalid

def module():
    file=Path(__file__).with_name('prepare-decision-demo.py')
    spec=importlib.util.spec_from_file_location('repair_demo_provision',file)
    p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p);return p

def load_facts(p,run_id):
    run_id=decimal(run_id)
    def obj(sql):
        text=p.mysql_query(sql)
        if not text:raise ValueError('Repair evidence row missing')
        return json.loads(text)
    run=obj(f"SELECT JSON_OBJECT('id',CAST(id AS CHAR),'tenant',tenant_id,'status',status,'version',version,'flowId',CAST(execution_flow_instance_id AS CHAR)) FROM `{SYS}`.ai_decision_run WHERE id={run_id} AND del_flag='0'")
    flow_id=decimal(run['flowId'])
    flow=obj(f"SELECT JSON_OBJECT('id',CAST(i.id AS CHAR),'tenant',i.tenant_id,'status',i.status,'runId',JSON_UNQUOTE(JSON_EXTRACT(i.input_json,'$.runId')),'flowKey',f.flow_key,'nodeTypeError',i.error_msg LIKE '%Data too long%node_type%','succeeded',i.node_success,'failed',i.node_failed) FROM `{SYS}`.ai_flow_instance i JOIN `{SYS}`.ai_flow f ON f.id=i.flow_id WHERE i.id={flow_id}")
    actions=[json.loads(x) for x in p.mysql_query(f"SELECT JSON_OBJECT('id',CAST(id AS CHAR),'type',action_type,'approval',approval_status,'execution',execution_status,'resultBizId',CAST(result_biz_id AS CHAR),'resultJson',result_json,'idempotencyKey',idempotency_key) FROM `{SYS}`.ai_decision_action WHERE run_id={run_id} ORDER BY id").splitlines()]
    ids=','.join(decimal(a['id']) for a in actions) or '0'
    keys=[]
    for action in actions:
        key=action.get('idempotencyKey')
        if not isinstance(key,str) or not 1<=len(key)<=128:raise ValueError('Missing idempotency provenance')
        keys.append('0x'+key.encode('utf-8').hex())
    key_sql=','.join(keys) or 'NULL'
    biz_sql=f"(decision_run_id={run_id} OR decision_action_id IN ({ids}) OR decision_idempotency_key IN ({key_sql}))"
    business_rows=int(p.mysql_query(f"SELECT (SELECT COUNT(*) FROM `{ERP}`.pu_purchase_requisition WHERE {biz_sql})+(SELECT COUNT(*) FROM `{ERP}`.prd_schedule_plan WHERE {biz_sql})"))
    return {'run':run,'flow':flow,'actions':actions,'nodeRows':int(p.mysql_query(f"SELECT COUNT(*) FROM `{SYS}`.ai_flow_node_run WHERE instance_id={flow_id}")),
        'businessRows':business_rows,'otherExecutions':int(p.mysql_query(f"SELECT COUNT(*) FROM `{SYS}`.ai_flow_instance i JOIN `{SYS}`.ai_flow f ON f.id=i.flow_id WHERE f.flow_key='system_delivery_risk_execution_v1_000000' AND JSON_UNQUOTE(JSON_EXTRACT(i.input_json,'$.runId'))='{run_id}' AND i.id<>{flow_id}")),
        '_bizCondition':biz_sql}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');parser.add_argument('--stale-run-id');args=parser.parse_args()
    if args.stale_run_id:decimal(args.stale_run_id)
    p=module();values=p.env_values();p.ROOT_PASSWORD=values['DECISION_DEMO_MYSQL_ROOT_PASSWORD'];p.MYSQL.append('--raw')
    assert p.MARKER.read_text(encoding='utf-8')=='nexus-ai-decision-demo-mysql:13307:secured\n'
    assert p.mysql_query('SELECT @@port')=='13307'
    assert Path(p.mysql_query('SELECT @@datadir').rstrip('\\/')).resolve()==p.DATA_DIR.resolve()
    p.verify_no_plus_schemas();p.verify_account(values)
    column=json.loads(p.mysql_query(f"SELECT JSON_OBJECT('type',DATA_TYPE,'length',CHARACTER_MAXIMUM_LENGTH,'nullable',IS_NULLABLE,'charset',CHARACTER_SET_NAME,'collation',COLLATION_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{SYS}' AND TABLE_NAME='ai_flow_node_run' AND COLUMN_NAME='node_type'"))
    if column['type']!='varchar' or column['nullable']!='NO' or column['charset']!='utf8mb4' or column['collation']!='utf8mb4_unicode_ci':raise ValueError('Unexpected node_type definition')
    facts=load_facts(p,args.stale_run_id) if args.stale_run_id else None
    if facts:validate_recovery(facts,args.stale_run_id)
    if not args.apply:
        print(json.dumps({'mode':'DRY_RUN','schema':SYS,'oldLength':column['length'],'plannedLength':max(64,column['length']),'runId':args.stale_run_id,'zeroBusinessWrites':facts is not None and facts['businessRows']==0,'willOnlyStaleNeverExecute':facts is not None}));return
    backup_dir=Path(os.environ['TEMP'])/'nexus-decision-schema-backups';backup_dir.mkdir(exist_ok=True)
    stem=datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]
    backup=backup_dir/(stem+'-ai_flow_node_run.sql')
    env=os.environ.copy();env['MYSQL_PWD']=values['DECISION_DEMO_MYSQL_ROOT_PASSWORD']
    with backup.open('xb') as output:
        result=subprocess.run(['mysqldump','--no-defaults','--protocol=TCP','--host=127.0.0.1','--port=13307','--user=root','--default-character-set=utf8mb4','--single-transaction','--skip-lock-tables','--no-tablespaces',SYS,'ai_flow_node_run'],stdout=output,stderr=subprocess.PIPE,env=env,timeout=120)
    if result.returncode or backup.stat().st_size==0:raise RuntimeError('Private table backup failed; no migration applied')
    audit={'schema':SYS,'columnBefore':column,'backupFile':str(backup),'backupSha256':hashlib.sha256(backup.read_bytes()).hexdigest(),'runBefore':None if facts is None else {k:v for k,v in facts.items() if k!='_bizCondition'}}
    if column['length']<64:
        p.mysql_query(f"ALTER TABLE `{SYS}`.ai_flow_node_run MODIFY COLUMN node_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型: LLM/AGENT/RAG/IF/CODE'")
    audit['columnAfter']=int(p.mysql_query(f"SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{SYS}' AND TABLE_NAME='ai_flow_node_run' AND COLUMN_NAME='node_type'"));assert audit['columnAfter']>=64
    if facts:
        fresh=load_facts(p,args.stale_run_id);validate_recovery(fresh,args.stale_run_id)
        run=fresh['run'];rid=run['id'];fid=run['flowId'];version=run['version']
        sql=f"""START TRANSACTION;
SELECT id FROM `{SYS}`.ai_decision_run WHERE id={rid} FOR UPDATE;
SELECT id FROM `{SYS}`.ai_flow_instance WHERE id={fid} FOR UPDATE;
SELECT id FROM `{SYS}`.ai_decision_action WHERE run_id={rid} FOR UPDATE;
UPDATE `{SYS}`.ai_decision_run r SET status='STALE',version=version+1,error_code='EXECUTION_FLOW_NOT_STARTED',error_message='执行流程在启动动作前失败，请重新分析并审批'
WHERE r.id={rid} AND r.tenant_id='000000' AND r.status='APPROVED' AND r.version={version} AND r.execution_flow_instance_id={fid}
AND EXISTS(SELECT 1 FROM `{SYS}`.ai_flow_instance i JOIN `{SYS}`.ai_flow f ON f.id=i.flow_id WHERE i.id={fid} AND i.tenant_id='000000' AND f.flow_key='system_delivery_risk_execution_v1_000000' AND JSON_UNQUOTE(JSON_EXTRACT(i.input_json,'$.runId'))='{rid}' AND i.status='FAILED' AND i.node_success=0 AND i.node_failed=0 AND i.error_msg LIKE '%Data too long%node_type%')
AND NOT EXISTS(SELECT 1 FROM `{SYS}`.ai_flow_node_run n WHERE n.instance_id={fid})
AND NOT EXISTS(SELECT 1 FROM `{SYS}`.ai_flow_instance i JOIN `{SYS}`.ai_flow f ON f.id=i.flow_id WHERE f.flow_key='system_delivery_risk_execution_v1_000000' AND JSON_UNQUOTE(JSON_EXTRACT(i.input_json,'$.runId'))='{rid}' AND i.id<>{fid})
AND NOT EXISTS(SELECT 1 FROM `{SYS}`.ai_decision_action a WHERE a.run_id={rid} AND (a.execution_status IS NULL OR a.execution_status<>'NOT_STARTED' OR a.approval_status IS NULL OR a.approval_status NOT IN ('APPROVED','REJECTED') OR a.action_type IS NULL OR a.action_type NOT IN ('CREATE_PURCHASE_REQUISITION_DRAFT','CREATE_SCHEDULE_PLAN_DRAFT') OR a.result_biz_id IS NOT NULL OR a.result_json IS NOT NULL))
AND (SELECT COUNT(*) FROM `{SYS}`.ai_decision_action WHERE run_id={rid})={len(fresh['actions'])}
AND EXISTS(SELECT 1 FROM `{SYS}`.ai_decision_action WHERE run_id={rid} AND approval_status='APPROVED')
AND NOT EXISTS(SELECT 1 FROM `{ERP}`.pu_purchase_requisition WHERE {fresh['_bizCondition']})
AND NOT EXISTS(SELECT 1 FROM `{ERP}`.prd_schedule_plan WHERE {fresh['_bizCondition']});
SELECT CONCAT('REPAIR_ROW_COUNT=',ROW_COUNT());
COMMIT;"""
        output=p.mysql_query(sql)
        if 'REPAIR_ROW_COUNT=1' not in output.splitlines():raise RuntimeError('Recovery CAS refused; schema expanded but no decision state was changed')
        audit['runAfter']=json.loads(p.mysql_query(f"SELECT JSON_OBJECT('id',CAST(id AS CHAR),'status',status,'version',version,'errorCode',error_code,'executionFlowId',CAST(execution_flow_instance_id AS CHAR)) FROM `{SYS}`.ai_decision_run WHERE id={rid}"))
        assert audit['runAfter']['status']=='STALE'
        assert load_facts(p,rid)['businessRows']==0
    audit_file=backup_dir/(stem+'-audit.json');audit_file.write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'schema':SYS,'nodeTypeLength':audit['columnAfter'],'runId':args.stale_run_id,'runStatus':None if facts is None else audit['runAfter']['status'],'businessWrites':0,'privateAudit':str(audit_file),'plusUntouched':True},ensure_ascii=False))
if __name__=='__main__':main()
