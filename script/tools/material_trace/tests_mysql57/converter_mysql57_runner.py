#!/usr/bin/env python3
"""显式授权后在独立自有MySQL5.7容器执行converter生成文件；不接受外部目标。"""
from __future__ import annotations
import argparse
import copy
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import time
import unittest
import uuid
from datetime import datetime,timezone
from pathlib import Path
from harness import DockerSandbox,ValidationError,SqlFailure,HERE,ROOT,extract_tables,ident,sql_value,cleanup_owned

sys.dont_write_bytecode=True
TOOL=HERE.parent
sys.path[:0]=[str(TOOL),str(TOOL/'tests')]
from core import COLUMNS,KEYS,REQUIRED_TABLES,canonical,digest,datasource
from sql_writer import literal as sql_literal
from conversion_fixtures import inventory,conversion_plan,reseal
import stock_converter


def arguments(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',action='store_true',help='仅允许新建、使用、清理本次拥有的隔离容器')
    parser.add_argument('--output',default='results/converter-sql/'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8])
    return parser.parse_args(argv)


def result_directory(relative):
    target=(HERE/relative).resolve();allowed=(HERE/'results/converter-sql').resolve()
    if target==allowed or not target.is_relative_to(allowed):raise ValidationError('结果仅可放在独立results/converter-sql子目录')
    return target


def check_review_binding(sql,schema,server):
    if '-- target: erp:'+schema+'\n' not in sql or sql_literal(server) not in sql:
        raise ValidationError('待审SQL必须绑定本次自建库及实际server_uuid')


def save(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf-8',newline='\n')


class Environment:
    """仅从当前DDL取CREATE TABLE；业务种子均来自明确合成夹具。"""
    def __init__(self,box,output):
        self.box,self.output,self.sessions,self.columns_cache,self.schemas=box,output,{}, {},set()
        self.counter=0
        names=['script/sql/ry-erp.sql','script/sql/update/2026-10-19-material-trace-strict-constraints-erp.sql']+['script/tools/material_trace/'+name+'.py' for name in ('schema_contract','core','metadata','repair','audit','sql_writer','stock_converter','stock_conversion_sql','policy_adoption','cli')]
        names+=['script/tools/material_trace/tests/'+name+'.py' for name in ('fixtures','conversion_fixtures','test_policy_adoption')]
        names+=['script/tools/material_trace/tests_mysql57/'+name+'.py' for name in ('harness','converter_mysql57_runner','converter_mysql57_cases')]
        names+=['ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/'+name+'.java' for name in ('PrdWorkOrderTrackingPolicyService','PrdWorkOrderPolicyAdoptionService','PrdWorkOrderPolicyAdoptionAuthorizer')]
        self.raw={name:(ROOT/name).read_bytes() for name in names}
        self.hashes={name:hashlib.sha256(data).hexdigest() for name,data in self.raw.items()}
        self.ddl=extract_tables(self.raw['script/sql/ry-erp.sql'].decode('utf-8-sig'))
        self.erp_tables=sorted(name for name in REQUIRED_TABLES if datasource(name)=='erp')
        self.tables=self.erp_tables+['wm_trace_stock_conversion_run']
        box.verify()
        self.server=box.session(None).execute('SELECT @@server_uuid;').scalar
        native=box.sessions[-1].execute('SELECT VERSION(),@@version_comment,@@sql_mode,@@transaction_isolation;').first
        if not native[0].startswith('5.7.'):raise ValidationError('仅允许实际MySQL5.7')
        self.native=dict(zip(('version','version_comment','sql_mode','isolation'),native))

    def query(self,schema,sql,**kwargs):
        if schema not in self.schemas:raise ValidationError('不能向未由本runner创建的schema发送SQL')
        if schema not in self.sessions:self.sessions[schema]=self.box.session(schema)
        return self.sessions[schema].execute(sql,**kwargs)

    def columns(self,schema,table):
        key=(schema,table)
        if key not in self.columns_cache:
            if table not in self.tables:raise ValidationError('非本次批准的ERP表')
            rows=self.query(schema,'SELECT COLUMN_NAME,IS_NULLABLE,IF(COLUMN_DEFAULT IS NULL,1,0),EXTRA,COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='+sql_value(schema)+' AND TABLE_NAME='+sql_value(table)+' ORDER BY ORDINAL_POSITION;').rows
            self.columns_cache[key]={r[0]:{'nullable':r[1]=='YES','no_default':r[2]=='1','generated':'GENERATED' in r[3],'type':r[4]} for r in rows}
        return self.columns_cache[key]

    def rows(self,schema,table,*,all_tenants=False):
        columns=self.columns(schema,table)
        fields=sorted(columns)
        expression='JSON_OBJECT('+','.join(sql_value(name)+',CAST('+ident(name)+' AS CHAR CHARACTER SET utf8mb4)' for name in fields)+')'
        where='' if all_tenants else ' WHERE BINARY tenant_id=BINARY '+sql_value('T1')
        order=','.join(ident(name) for name in KEYS.get(table,('tenant_id','run_id')))
        return [json.loads(row[0]) for row in self.query(schema,'SELECT '+expression+' FROM '+ident(table)+where+' ORDER BY '+order+';').rows]

    def new_case(self,name,**options):
        self.counter+=1;schema='mtc_'+self.box.token[:8]+'_'+str(self.counter)
        output=self.output/name;output.mkdir(exist_ok=False)
        ddl='CREATE DATABASE '+ident(schema)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nUSE '+ident(schema)+';\n'+'\n'.join(self.ddl[name] for name in self.tables)+'\n'
        (output/'fresh-erp-tables.sql').write_text(ddl,encoding='utf-8',newline='\n')
        self.box.run_sql(None,ddl);self.schemas.add(schema)
        for table in self.tables:
            if set(self.columns(schema,table))!=set(COLUMNS[table]):raise ValidationError('当前主DDL与metadata字段偏离：'+table)
        return Case(self,schema,output,**options)

    def changed_inputs(self):
        return [name for name,value in self.hashes.items() if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=value]


class Case:
    def __init__(self,env,schema,output,**options):
        self.env,self.schema,self.output,self.options=env,schema,output,options
        self.before=self.expected=self.plan=self.journal=None
        self.mutations=[]

    def query(self,sql,**kwargs):return self.env.query(self.schema,sql,**kwargs)
    def save(self,name,value):save(self.output/name,value)
    def load(self,name):return json.loads((self.output/name).read_text(encoding='utf-8'))

    def mutate(self,sql):
        self.mutations.append(sql)
        (self.output/'fixture-mutations.sql').write_text('\n'.join(self.mutations)+'\n',encoding='utf-8',newline='\n')
        return self.query(sql)

    def insert(self,table,values):
        columns=self.env.columns(self.schema,table)
        if set(values)-set(columns):raise ValidationError('合成种子列偏离主DDL')
        selected={name:value for name,value in values.items() if not columns[name]['generated'] and not(value is None and not columns[name]['nullable'] and not columns[name]['no_default'])}
        for name,column in columns.items():
            if not column['generated'] and not column['nullable'] and column['no_default'] and selected.get(name) is None:
                raise ValidationError('合成夹具未明确提供NOT NULL列：'+table+'.'+name)
        sql='INSERT INTO '+ident(table)+' ('+','.join(ident(name) for name in selected)+') VALUES ('+','.join(sql_value(value) for value in selected.values())+');'
        return self.mutate(sql)

    def plan_for(self,snapshot):
        plan=conversion_plan(snapshot);cutover=snapshot['captured_at']
        plan['cutover_at']=cutover;plan['census']['observed_at']=cutover;plan['census']['history_boundary']['through']=cutover
        if self.options.get('shuffle_stock_ids'):
            for target,stock in zip(plan['sources'][0]['targets'],('903','901','902')):target['stock_id']=stock
        return reseal(plan)

    def prepare(self,*,ready=True):
        model=inventory(quantity=self.options.get('quantity','3.0000'),serial=self.options.get('serial',False),batch=self.options.get('batch',False))
        material=model['tables']['bd_material'][0]
        material.update(material_code='SYNTH-M10',material_name='合成物料',category_id='1',material_type='1')
        if self.options.get('unicode_fields'):material['material_name']="合成 'quote' \\path\n第二行"
        for row in model['tables']['wm_material_stock']:
            row.update(material_code=material['material_code'],material_name=material['material_name'])
        for row in model['tables']['wm_material_stock_tx']:
            row.update(tx_type='SYNTH_OPENING_AGGREGATE',warehouse_id='1',location_id='3',tx_date='2026-01-01 00:00:00')
        if self.options.get('zero_peer'):
            old=model['tables']['wm_material_stock'][0]
            model['tables']['wm_material_stock'].append({**old,'stock_id':'402','stock_quantity':'0.0000','total_cost':'0.0000','sn_code':'SYNTH-ZERO-HISTORY'})
        if self.options.get('zero_history_pool'):
            model['tables']['wm_valuation_group'].append(dict(valuation_group_id='800',tenant_id='T1',material_id='10',warehouse_id='1',batch_id=None,source_stock_id='401',quantity='0.0000',amount='0.0000',unit_cost='0.000000',valuation_method='MOVING_AVERAGE',policy_version='1',valuation_snapshot='{}'))
        for table in ('bd_material','wm_batch','wm_sn','wm_material_stock','wm_material_stock_tx','wm_valuation_group'):
            for row in model['tables'][table]:self.insert(table,row)
        model['target'].update(environment='owned-converter-mysql57-synthetic',erp_database=self.schema,mes_database='synthetic_mes_not_connected')
        model['server_ids']['erp']=self.env.server
        model['captured_at']=self.query("SELECT DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s');").scalar
        model['watermarks']={'erp':'owned-container-frozen-fixture','mes':'synthetic-empty-logical-fixture-not-connected'}
        self.before=self.export(model)
        self.plan=self.plan_for(self.before)
        self.save('before.json',self.before);self.save('plan.json',self.plan)
        if not ready:return
        self.cli('convert-check','--snapshot','before.json','--plan','plan.json','--output','check.json')
        self.cli('convert-apply','--snapshot','before.json','--plan','plan.json','--output','expected-after.json','--journal-output','journal.json')
        self.expected=self.load('expected-after.json');self.journal=self.load('journal.json')
        self.cli('convert-emit-sql','--mode','apply','--snapshot','before.json','--journal','journal.json','--target','erp:'+self.schema,'--backup-reference','OWNED-TMPFS-SYNTHETIC','--allow-db-writes','--output','review-apply.sql')

    def export(self,template):
        snapshot=copy.deepcopy(template)
        snapshot['tables']={name:self.env.rows(self.schema,name) if datasource(name)=='erp' else [] for name in sorted(REQUIRED_TABLES)}
        snapshot['complete_tables']=sorted(REQUIRED_TABLES)
        states=[];bridges={}
        for row in self.env.rows(self.schema,'wm_trace_stock_conversion_run'):
            states.append({name:row[name] for name in stock_converter.STATE_FIELDS})
            journal=json.loads(row['journal_json']);journal['journal_hash']=row['journal_hash']
            if hashlib.sha256(row['journal_json'].encode('utf-8')).hexdigest()!=row['journal_hash']:raise ValidationError('数据库原日志字节摘要不符')
            expected=stock_converter.validate_journal(journal)
            if row['status']=='APPLIED':
                bridge=next(value for value in expected['stock_conversion_bridges'] if value['run_id']==row['run_id'])
                bridges[(row['tenant_id'],row['run_id'])]=bridge
        snapshot['conversion_run_states']=states
        if bridges:snapshot['stock_conversion_bridges']=[bridges[key] for key in sorted(bridges)]
        else:snapshot.pop('stock_conversion_bridges',None)
        return snapshot

    def actual(self):return self.export(self.before)
    def raw(self):return {name:self.env.rows(self.schema,name,all_tenants=True) for name in self.env.tables}
    def audit_row(self):return self.env.rows(self.schema,'wm_trace_stock_conversion_run')[0]

    def cli(self,*args,expected=0):
        result=subprocess.run([sys.executable,'-B',str(TOOL/'cli.py'),*args],cwd=self.output,capture_output=True,text=True,encoding='utf-8',env={**os.environ,'PYTHONUTF8':'1'},timeout=45)
        with (self.output/'cli-results.jsonl').open('a',encoding='utf-8') as out:
            out.write(json.dumps({'arguments':args,'exit_code':result.returncode,'stdout':result.stdout,'stderr':result.stderr},ensure_ascii=False)+'\n')
        if result.returncode!=expected:raise ValidationError('CLI '+str(args)+' returned '+str(result.returncode)+': '+result.stderr+' '+result.stdout)
        return result

    def execute(self,mode,*,error=None):
        path=self.output/('review-'+mode+'.sql')
        if not path.resolve().is_relative_to(self.env.output.resolve()):raise ValidationError('待审SQL路径越界')
        sql=path.read_text(encoding='utf-8');check_review_binding(sql,self.schema,self.env.server)
        try:output=self.env.box.run_sql(self.schema,sql,error=error)
        except SqlFailure as exc:
            (self.output/(mode+'-native-failure.txt')).write_text(exc.output,encoding='utf-8');raise
        (self.output/(mode+'-native-result.txt')).write_text(output,encoding='utf-8')
        return output

    def offline_check(self):
        snapshot=self.actual();self.save('invalid-current.json',snapshot);self.save('invalid-plan.json',self.plan_for(snapshot))
        self.cli('convert-check','--snapshot','invalid-current.json','--plan','invalid-plan.json','--output','difference-tasks.json',expected=3)
        return self.load('difference-tasks.json')

    def generate_revert(self):
        self.save('actual-after.json',self.actual())
        self.cli('convert-revert','--snapshot','actual-after.json','--journal','journal.json','--output','expected-restored.json','--journal-output','reverted-journal.json','--compensation-output','safe-revert.json')
        self.cli('convert-emit-sql','--mode','revert','--snapshot','actual-after.json','--journal','journal.json','--target','erp:'+self.schema,'--backup-reference','OWNED-TMPFS-SYNTHETIC','--allow-db-writes','--output','review-revert.sql')


class RecordedResult(unittest.TextTestResult):
    def __init__(self,*args,**kwargs):super().__init__(*args,**kwargs);self.records=[]
    def startTest(self,test):super().startTest(test);self.started=time.monotonic()
    def addSuccess(self,test):super().addSuccess(test);self.record(test,'PASS')
    def addFailure(self,test,err):super().addFailure(test,err);self.record(test,'FAIL',self._exc_info_to_string(err,test))
    def addError(self,test,err):super().addError(test,err);self.record(test,'FAIL',self._exc_info_to_string(err,test))
    def addSkip(self,test,reason):super().addSkip(test,reason);self.record(test,'SKIP',reason)
    def record(self,test,status,detail=None):
        row={'name':test.id(),'status':status,'seconds':round(time.monotonic()-self.started,3)}
        if detail:row['detail']=detail
        self.records.append(row)
        print(status,test.id().split('.')[-1],flush=True)


def main(argv=None):
    args=arguments(argv)
    if not args.run:
        print('Opt-in required: --run only creates an owned MySQL5.7 container; no existing target arguments.')
        return 2
    output=result_directory(args.output);output.mkdir(parents=True,exist_ok=False)
    box=DockerSandbox();report={'started_at':datetime.now(timezone.utc).isoformat(),'workspace':str(ROOT),'container':{'name':box.name,'run_id':box.token},'boundaries':['Fresh ERP tables extracted from current install DDL; only synthetic seed INSERTs.','Only generated, UUID/database-bound review SQL is executed by this runner.','MES is explicitly empty logical fixture data; no cross-datasource atomicity claimed.','No Java/Spring ERP posting, printer, accounting voucher, real census or existing database verification.','Late constraint fault injection exists only in this owned temporary schema.'],'cases':[]}
    stream=io.StringIO();result=None
    try:
        print('Owned converter container:',box.name,flush=True);box.start()
        profile=box.verify();env=Environment(box,output)
        report.update(mysql=env.native,source_sha256=env.hashes)
        report['container'].update(id=box.cid,image=box.image,network=profile['HostConfig']['NetworkMode'],tmpfs=profile['HostConfig']['Tmpfs'],port_bindings=profile['HostConfig'].get('PortBindings'))
        from converter_mysql57_cases import ConverterCases
        ConverterCases.env=env
        result=unittest.TextTestRunner(stream=stream,verbosity=2,resultclass=RecordedResult).run(unittest.defaultTestLoader.loadTestsFromTestCase(ConverterCases))
        report['cases']=result.records;report['changed_inputs']=env.changed_inputs()
        if report['changed_inputs']:report['input_error']='源码在验证中变化，不能声明覆盖最新输入'
    except Exception as exc:
        import traceback
        report['runner_error']=str(exc);report['traceback']=traceback.format_exc();print(report['traceback'],flush=True)
    finally:
        (output/'unittest-output.txt').write_text(stream.getvalue(),encoding='utf-8')
        cleanup_owned(box,output,report)
        report['finished_at']=datetime.now(timezone.utc).isoformat()
        report['counts']={name:sum(row['status']==status for row in report['cases']) for name,status in (('pass','PASS'),('fail','FAIL'),('skip','SKIP'))}
        report['counts']['executed']=report['counts']['pass']+report['counts']['fail']
        save(output/'report.json',report)
    print('REPORT',str(output/'report.json'),flush=True);print('COUNTS',json.dumps(report['counts']),'owned container removed:',box.removed,flush=True)
    return int(not result or not result.wasSuccessful() or bool(result.skipped) or not result.testsRun or bool(report.get('runner_error') or report.get('input_error') or report.get('cleanup_error')) or not box.removed)


if __name__=='__main__':raise SystemExit(main())
