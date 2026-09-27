"""带护栏的本地演示字典、结构及元数据修复；不修改数量、审批或主数据。"""
import argparse,importlib.util,json,os,re,subprocess,uuid,hashlib
from decimal import Decimal
from pathlib import Path
from datetime import datetime
SYS='nexus_ai_decision_demo_sys';ERP='nexus_ai_decision_demo_erp';TYPE='pu_source_type'

def ident(v):
    if not isinstance(v,str) or not re.fullmatch(r'[1-9][0-9]{0,18}',v) or int(v)>9223372036854775807:raise ValueError('Invalid ID')
    return v

def validate(f,id,version):
    ident(id)
    if type(version) is not int or version<0:raise ValueError('Invalid version')
    try:
        h,a,u=f['header'],f['action'],f['user']
        if (h['id']!=id or h['tenant']!='000000' or h['status']!='DRAFT' or h['source']!='AI_DECISION'
            or h['version']!=version or h['applicant']!=u['id'] or h['creator']!=u['id']
            or a['approvedBy']!=u['id'] or u['tenant']!='000000' or u['status']!='0'
            or h['action']!=a['id'] or h['run']!=a['run'] or h['key']!=a['key'] or not h['key']
            or a['approval']!='APPROVED' or a['execution']!='SUCCEEDED' or a['resultId']!=id
            or f['runStatus'] not in ('SUCCEEDED','PARTIAL_SUCCESS')):raise ValueError('Draft provenance/state changed')
        lines=f['lines'];intent=f['intent'];masters={m['id']:m for m in f['materials']}
        if not lines or len(lines)!=len(intent):raise ValueError('Line count changed')
        def core(l):
            qty,gap=Decimal(l['qty']),Decimal(l['gap'])
            if not qty.is_finite() or not gap.is_finite() or qty<=0 or gap<=0:raise ValueError('Invalid quantity')
            return (ident(l['material']),qty,gap,l['date'])
        if sorted(map(core,lines))!=sorted(map(core,intent)):raise ValueError('Frozen numeric intent changed')
        for l in lines:
            ident(l['id']);m=masters[l['material']]
            if (l['tenant']!='000000' or l['source']!='AI_DECISION' or l['sourceId']!=h['run'] or l['approved'] is not None
                or m['tenant']!='000000' or m['status']!=1 or not m['code'] or not m['name']):raise ValueError('Untrusted line/master')
    except (KeyError,TypeError) as e:raise ValueError('Incomplete backfill evidence') from e

def literal(value):
    return 'NULL' if value is None else 'CONVERT(0x'+str(value).encode('utf-8').hex()+' USING utf8mb4) COLLATE utf8mb4_unicode_ci'

def load(p,id):
    q=p.mysql_query
    h=json.loads(q(f"SELECT JSON_OBJECT('id',CAST(requisition_id AS CHAR),'tenant',tenant_id,'status',status,'source',source_type,'version',version,'applicant',CAST(applicant_id AS CHAR),'creator',CAST(create_by AS CHAR),'run',CAST(decision_run_id AS CHAR),'action',CAST(decision_action_id AS CHAR),'key',decision_idempotency_key,'deptId',CAST(dept_id AS CHAR),'deptName',dept_name,'createDept',CAST(create_dept AS CHAR)) FROM `{ERP}`.pu_purchase_requisition WHERE requisition_id={ident(id)} AND del_flag='0'"))
    a=json.loads(q(f"SELECT JSON_OBJECT('id',CAST(id AS CHAR),'run',CAST(run_id AS CHAR),'approval',approval_status,'execution',execution_status,'approvedBy',CAST(approved_by AS CHAR),'resultId',CAST(result_biz_id AS CHAR),'key',idempotency_key,'payload',payload_json) FROM `{SYS}`.ai_decision_action WHERE id={ident(h['action'])} AND tenant_id='000000' AND result_biz_type='PU_PURCHASE_REQUISITION'"))
    u=json.loads(q(f"SELECT JSON_OBJECT('id',CAST(user_id AS CHAR),'tenant',tenant_id,'status',status,'deptId',CAST(dept_id AS CHAR)) FROM `{SYS}`.sys_user WHERE user_id={ident(h['applicant'])} AND tenant_id='000000' AND del_flag='0'"))
    dept=h['deptId'] or u['deptId'];name=None
    if dept:
        name=q(f"SELECT dept_name FROM `{SYS}`.sys_dept WHERE dept_id={ident(dept)} AND tenant_id='000000' AND status='0' AND del_flag='0'")
        if not name:raise ValueError('Department unavailable')
    lines=[json.loads(x) for x in q(f"SELECT JSON_OBJECT('id',CAST(line_id AS CHAR),'tenant',tenant_id,'material',CAST(material_id AS CHAR),'qty',CAST(apply_quantity AS CHAR),'gap',CAST(shortage_quantity AS CHAR),'date',DATE_FORMAT(required_date,'%Y-%m-%d'),'source',source_type,'sourceId',CAST(source_id AS CHAR),'approved',CAST(approved_quantity AS CHAR)) FROM `{ERP}`.pu_purchase_requisition_line WHERE requisition_id={id} AND del_flag='0'").splitlines()]
    payload=json.loads(a['payload']);intent=[{'material':x['materialId'],'qty':x.get('quantity') or x.get('gap'),'gap':x.get('gap') or x.get('quantity'),'date':x.get('requiredDate') or payload['requiredDate']} for x in payload['materialLines']]
    masters=[]
    for l in lines:
        m=json.loads(q(f"SELECT JSON_OBJECT('id',CAST(material_id AS CHAR),'tenant',tenant_id,'status',status,'code',material_code,'name',material_name,'spec',material_spec,'unitId',CAST(base_uom_id AS CHAR)) FROM `{ERP}`.bd_material WHERE material_id={ident(l['material'])} AND del_flag='0'"))
        m['unit']=None
        if m['unitId']:
            m['unit']=q(f"SELECT uom_name FROM `{ERP}`.bd_unit_of_measure WHERE uom_id={ident(m['unitId'])} AND tenant_id='000000' AND status=1 AND del_flag='0'")
            if not m['unit']:raise ValueError('Base unit unavailable')
        masters.append(m)
    return {'header':h,'action':a,'user':u,'lines':lines,'intent':intent,'materials':masters,'runStatus':q(f"SELECT status FROM `{SYS}`.ai_decision_run WHERE id={ident(h['run'])} AND tenant_id='000000'"),'deptId':dept,'deptName':name}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');parser.add_argument('--requisition-id',required=True);parser.add_argument('--expected-version',type=int,required=True);args=parser.parse_args();ident(args.requisition_id)
    path=Path(__file__).with_name('prepare-decision-demo.py');s=importlib.util.spec_from_file_location('metadata_demo',path);p=importlib.util.module_from_spec(s);s.loader.exec_module(p)
    v=p.env_values();p.ROOT_PASSWORD=v['DECISION_DEMO_MYSQL_ROOT_PASSWORD'];p.MYSQL.append('--raw')
    assert p.mysql_query('SELECT @@port')=='13307' and Path(p.mysql_query('SELECT @@datadir').rstrip('\\/')).resolve()==p.DATA_DIR.resolve()
    assert p.MARKER.read_text(encoding='utf-8')=='nexus-ai-decision-demo-mysql:13307:secured\n';p.verify_no_plus_schemas();p.verify_account(v)
    f=load(p,args.requisition_id);validate(f,args.requisition_id,args.expected_version)
    assert p.mysql_query(f"SELECT COUNT(*) FROM `{SYS}`.sys_dict_type WHERE tenant_id='000000' AND dict_type='{TYPE}'")=='1'
    dict_exists=p.mysql_query(f"SELECT COUNT(*) FROM `{SYS}`.sys_dict_data WHERE tenant_id='000000' AND dict_type='{TYPE}' AND dict_value='AI_DECISION'")
    if dict_exists not in ('0','1'):raise ValueError('Duplicate dictionary value')
    if dict_exists=='0' and p.mysql_query(f"SELECT COUNT(*) FROM `{SYS}`.sys_dict_data WHERE dict_code=3000044")!='0':raise ValueError('Dictionary ID conflict')
    if not args.apply:
        print(json.dumps({'mode':'DRY_RUN','draft':args.requisition_id,'version':args.expected_version,'dept':f['deptName'],'willNotInventSpecOrUnit':True,'dictionaryExists':dict_exists=='1'},ensure_ascii=False));return
    root=Path(os.environ['TEMP'])/'nexus-requisition-metadata-backups';root.mkdir(exist_ok=True);stem=datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]
    env=os.environ.copy();env['MYSQL_PWD']=v['DECISION_DEMO_MYSQL_ROOT_PASSWORD'];backups=[]
    for schema,table,where in [(SYS,'sys_dict_data',"dict_type='pu_source_type'"),(ERP,'pu_purchase_requisition','requisition_id='+args.requisition_id),(ERP,'pu_purchase_requisition_line','requisition_id='+args.requisition_id)]:
        file=root/(stem+'-'+table+'.sql')
        with file.open('xb') as out:r=subprocess.run(['mysqldump','--no-defaults','--protocol=TCP','--host=127.0.0.1','--port=13307','--user=root','--default-character-set=utf8mb4','--single-transaction','--skip-lock-tables','--no-tablespaces','--where='+where,schema,table],stdout=out,stderr=subprocess.PIPE,env=env,timeout=120)
        if r.returncode or not file.stat().st_size:raise RuntimeError('Private backup failed')
        backups.append({'file':str(file),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
    for column,size in [('material_name',200),('material_spec',500),('unit',64)]:
        n=int(p.mysql_query(f"SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{ERP}' AND TABLE_NAME='pu_purchase_requisition_line' AND COLUMN_NAME='{column}'"))
        if n<size:
            comment=p.mysql_query(f"SELECT COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{ERP}' AND TABLE_NAME='pu_purchase_requisition_line' AND COLUMN_NAME='{column}'").replace("'","''")
            p.mysql_query(f"ALTER TABLE `{ERP}`.pu_purchase_requisition_line MODIFY `{column}` VARCHAR({size}) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '{comment}'")
    f=load(p,args.requisition_id);validate(f,args.requisition_id,args.expected_version);h=f['header'];rid=h['id'];uid=f['user']['id'];dept=f['deptId'];audit_dept=f['user']['deptId']
    sql=["START TRANSACTION;",f"SELECT requisition_id FROM `{ERP}`.pu_purchase_requisition WHERE requisition_id={rid} FOR UPDATE;",f"SELECT line_id FROM `{ERP}`.pu_purchase_requisition_line WHERE requisition_id={rid} FOR UPDATE;",
         f"UPDATE `{ERP}`.pu_purchase_requisition SET dept_id=COALESCE(dept_id,{dept or 'NULL'}),dept_name=COALESCE(NULLIF(dept_name,''),{literal(f['deptName'])}),create_dept=IF(create_dept IS NULL OR create_dept<0,{audit_dept or 'NULL'},create_dept),version=version+1 WHERE requisition_id={rid} AND tenant_id='000000' AND source_type='AI_DECISION' AND status='DRAFT' AND version={args.expected_version} AND decision_run_id={h['run']} AND decision_action_id={h['action']} AND decision_idempotency_key={literal(h['key'])};",
         "SET @metadata_header_changed=ROW_COUNT();"]
    masters={m['id']:m for m in f['materials']}
    for line in f['lines']:
        m=masters[line['material']];missing=[]
        if not m['spec']:missing.append('规格')
        if not m['unit']:missing.append('基本计量单位')
        note=None if not missing else '物料'+'、'.join(missing)+'未维护，请完善物料档案；未进行单位换算'
        sql.append(f"UPDATE `{ERP}`.pu_purchase_requisition_line SET material_code=COALESCE(NULLIF(material_code,''),{literal(m['code'])}),material_name=COALESCE(NULLIF(material_name,''),{literal(m['name'])}),material_spec=COALESCE(NULLIF(material_spec,''),{literal(m['spec'])}),unit=COALESCE(NULLIF(unit,''),{literal(m['unit'])}),create_dept=IF(create_dept IS NULL OR create_dept<0,{audit_dept or 'NULL'},create_dept),create_by=IF(create_by IS NULL OR create_by<0,{uid},create_by),update_by=IF(update_by IS NULL OR update_by<0,{uid},update_by),remark=COALESCE(NULLIF(remark,''),{literal(note)}) WHERE @metadata_header_changed=1 AND line_id={line['id']} AND requisition_id={rid} AND tenant_id='000000' AND material_id={line['material']} AND source_type='AI_DECISION' AND source_id={h['run']} AND apply_quantity={line['qty']} AND shortage_quantity={line['gap']} AND approved_quantity IS NULL;")
    sql.append(f"INSERT INTO `{SYS}`.sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,list_class,is_default,create_dept,create_by,create_time,remark) SELECT 3000044,'000000',4,'AI交付风险决策','AI_DECISION','{TYPE}','info','N',{audit_dept or 'NULL'},{uid},NOW(),'授权AI工业决策生成的采购申请草稿' WHERE @metadata_header_changed=1 AND NOT EXISTS(SELECT 1 FROM `{SYS}`.sys_dict_data WHERE tenant_id='000000' AND dict_type='{TYPE}' AND dict_value='AI_DECISION');")
    sql+=["SELECT CONCAT('HEADER_CAS=',@metadata_header_changed);",'COMMIT;'];result=p.mysql_query('\n'.join(sql),expect_success=False)
    if result.returncode:
        error=result.stderr.decode('utf-8',errors='replace')
        match=re.search(r'ERROR \d+[^\n]*',error)
        raise RuntimeError('Metadata transaction refused: '+(match.group(0)[:250] if match else 'SQL_FAILED'))
    out=result.stdout.decode('utf-8',errors='replace')
    if 'HEADER_CAS=1' not in out.splitlines():raise RuntimeError('Concurrent edit: no metadata/dictionary repair committed')
    after=load(p,rid);assert after['lines']==f['lines'] and after['header']['version']==args.expected_version+1
    audit={'before':f,'after':after,'backups':backups,'nonInventedFields':['materialSpec','unit'] if any(not m['spec'] or not m['unit'] for m in f['materials']) else [],'plusUntouched':True}
    file=root/(stem+'-audit.json');file.write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'draft':rid,'version':after['header']['version'],'dept':after['deptName'],'quantityAndApprovalUnchanged':True,'privateAudit':str(file),'plusUntouched':True},ensure_ascii=False))
if __name__=='__main__':main()
