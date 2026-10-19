"""历史工单只能凭原策略归档和实盘审批生成独立待审采纳，不借用当前主数据。"""
import copy
import hashlib
import json
from pathlib import Path
import re
import sys
import subprocess
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from conversion_fixtures import inventory
from core import canonical,digest
try:
    import policy_adoption as adoption
except ModuleNotFoundError:
    adoption=None


def framed(*values):
    text="".join("-1:" if v is None else str(len(str(v).encode("utf-16-le"))//2)+":"+str(v) for v in values)
    return hashlib.sha256(text.encode()).hexdigest()


def fixture():
    snapshot=json.loads(json.dumps(inventory()).replace('"T1"','"000001"'))
    snapshot['tables']['bd_material'][0]['tracking_version']='99'
    root=Path(__file__).resolve().parents[4]
    sql=(root/'script/sql/ry-erp.sql').read_text(encoding='utf-8-sig')
    block=re.search(r"CREATE TABLE `prd_work_order` \([\s\S]*?\n\)[^;]*;",sql)[0]
    row={}
    for name,definition in re.findall(r"^  `([^`]+)` ([^\r\n]+)",block,re.M):
        row[name]=None if "NOT NULL" not in definition else "1" if definition.startswith(("bigint","int","tinyint","decimal")) else "2026-10-18 00:00:00" if definition.startswith("datetime") else "SYNTH"
    row.update(tenant_id='000001',wo_id='501',material_id='10',del_flag='0',wo_status='IN_PROGRESS')
    wo=dict(erpWoId='501',tenantId='000001',rootMaterialId='10')
    for field in ('bom_id','bom_version','source_type','source_id','plan_id','backflush_flag'):wo[field]=row[field]
    wo.update(bomComponents=[],requirementComponents=[],materialIds=['10'])
    wo_json=json.dumps(wo,ensure_ascii=False,separators=(',',':'))
    policy=dict(batchManaged=False,serialManaged=True,allowMixedLot=False,version='7',stage='AUDIT')
    source=canonical(dict(schemaVersion=1,tenantId='000001',erpWoId='501',materials=[dict(materialId='10',origin='IMMUTABLE_SOURCE_SNAPSHOT',reference='synthetic://original-release/501',documentHash='a'*64,capturedAt='2026-10-17T00:00:00Z',policy=policy)]))
    source_hash=hashlib.sha256(source.encode()).hexdigest();wo_hash=hashlib.sha256(wo_json.encode()).hexdigest()
    approved='2026-10-18T00:00:00Z';expires='2026-10-26T00:00:00Z'
    from datetime import datetime
    epoch=lambda value:int(datetime.fromisoformat(value.replace('Z','+00:00')).timestamp()*1000)
    request=framed('WO_POLICY_ADOPTION_V1','000001','700','SYNTH-ADOPT-1','501',wo_hash,source_hash,'6','7',epoch(approved),epoch(expires))
    decision=canonical(dict(decision='APPROVED',requestHash=request,approvedBy='7',approvedAt=approved,reference='synthetic://independent-approval/700'))
    plan=dict(schema_version=1,kind='ORIGINAL_WORK_ORDER_POLICY_ADOPTION',run_id='SYNTH-ADOPT-1',adoption_id='700',command_id='SYNTH-ADOPT-1',tenant_id='000001',erp_wo_id='501',proposed_by='6',approved_by='7',approved_at=approved,expires_at=expires,timestamp_zone='+08:00',snapshot_hash=digest(snapshot),source_before=row,source_before_hash=digest(row),wo_source_json=wo_json,expected_wo_hash=wo_hash,source_json=source,source_hash=source_hash,approval_json=decision,approval_hash=hashlib.sha256(decision.encode()).hexdigest(),request_hash=request)
    return snapshot,seal(plan)


def seal(plan):
    intent=copy.deepcopy({k:v for k,v in plan.items() if k not in ('approval','census')})
    for name,kind in [('approval','SIGNED_REVIEW'),('census','PHYSICAL_COUNT')]:
        evidence=dict(evidence_id='SYNTH-'+name,kind=kind,tenant_id=plan['tenant_id'],reference='synthetic://'+name+'/501',approved_by='synthetic-reviewer',adoption=intent)
        evidence['artifact']=copy.deepcopy({k:v for k,v in evidence.items() if k!='evidence_id'})
        evidence['artifact_sha256']=digest(evidence['artifact']);plan[name]=evidence
    return plan


class PolicyAdoptionTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(adoption,'需要可实施的离线原策略采纳入口')
        self.snapshot,self.plan=fixture()

    def test_imports_independent_approval_without_writing_bank_or_current_master(self):
        result=adoption.prepare(self.snapshot,self.plan);row=result['rows'][0]
        self.assertEqual(('APPROVED','1','6','7'),tuple(row[k] for k in ('status','version','proposed_by','approved_by')))
        self.assertIsNone(row['applied_at']);self.assertIsNone(row['snapshot_hash'])
        self.assertEqual('7',json.loads(row['source_json'])['materials'][0]['policy']['version'])
        for table in self.snapshot['tables']:
            if table!='wm_work_order_policy_adoption':self.assertEqual(self.snapshot['tables'][table],result['after_snapshot']['tables'][table])

    def test_missing_original_evidence_or_census_cannot_fall_back_to_current_policy(self):
        for field in ('approval','census','source_json','source_hash','approval_json'):
            plan=copy.deepcopy(self.plan);plan.pop(field)
            with self.subTest(field=field),self.assertRaises(ValueError):adoption.prepare(self.snapshot,plan)

    def test_source_hashes_and_complete_material_scope_are_required(self):
        for field in ('source_hash','source_before_hash','expected_wo_hash','approval_hash','request_hash'):
            plan=copy.deepcopy(self.plan);plan[field]='b'*64;seal(plan)
            with self.subTest(field=field),self.assertRaises(ValueError):adoption.prepare(self.snapshot,plan)
        plan=copy.deepcopy(self.plan);bundle=json.loads(plan['source_json']);bundle['materials'][0]['materialId']='20'
        plan['source_json']=canonical(bundle);plan['source_hash']=hashlib.sha256(plan['source_json'].encode()).hexdigest();seal(plan)
        with self.assertRaises(ValueError):adoption.prepare(self.snapshot,plan)

    def test_self_approval_current_master_origin_and_future_archive_are_rejected(self):
        plan=copy.deepcopy(self.plan);plan['proposed_by']=plan['approved_by'];seal(plan)
        with self.assertRaises(ValueError):adoption.prepare(self.snapshot,plan)
        for field,value in (('origin','CURRENT_MASTER'),('capturedAt','2026-10-25T00:00:00Z')):
            plan=copy.deepcopy(self.plan);bundle=json.loads(plan['source_json']);bundle['materials'][0][field]=value
            plan['source_json']=canonical(bundle);plan['source_hash']=hashlib.sha256(plan['source_json'].encode()).hexdigest();seal(plan)
            with self.subTest(field=field),self.assertRaises(ValueError):adoption.prepare(self.snapshot,plan)

    def test_exact_import_replays_but_changed_business_or_approval_cannot_be_overwritten(self):
        journal=adoption.prepare(self.snapshot,self.plan)
        replay=adoption.prepare(journal['after_snapshot'],self.plan)
        self.assertEqual(journal['rows'],replay['rows']);self.assertEqual('ALREADY_PRESENT',replay['status'])
        for table,column,value in (('wm_sn','identity_version','9'),('wm_work_order_policy_adoption','version','2')):
            changed=copy.deepcopy(journal['after_snapshot']);changed['tables'][table][0][column]=value
            with self.subTest(table=table),self.assertRaises(ValueError):adoption.prepare(changed,self.plan)

    def test_sql_only_inserts_approval_and_binds_source_and_full_baseline(self):
        journal=adoption.prepare(self.snapshot,self.plan)
        sql=adoption.emit(self.snapshot,journal,target='erp:fixture_erp',backup_reference='SYNTH-BACKUP',allow_db_writes=True)
        for token in ('@@server_uuid','SERIALIZABLE','ROLLBACK','FROM `prd_work_order`','FOR UPDATE','FROM `wm_serial_sequence`','INSERT INTO `wm_work_order_policy_adoption`'):self.assertIn(token,sql)
        self.assertNotRegex(sql,r'(?i)(UPDATE|DELETE FROM|INSERT IGNORE|REPLACE INTO) `')
        self.assertNotIn('INSERT INTO `wm_work_order_tracking_policy`',sql)
        with self.assertRaises(ValueError):adoption.emit(self.snapshot,journal,target='erp:wrong',backup_reference='x',allow_db_writes=True)

    def test_journal_cannot_be_resealed_to_write_policy_or_arbitrary_rows(self):
        journal=adoption.prepare(self.snapshot,self.plan);journal['rows'][0]['approved_by']='99'
        journal['journal_hash']=digest({k:v for k,v in journal.items() if k!='journal_hash'})
        with self.assertRaises(ValueError):adoption.emit(self.snapshot,journal,target='erp:fixture_erp',backup_reference='x',allow_db_writes=True)

    def test_real_cli_generates_review_json_and_sql_and_never_overwrites_evidence(self):
        tool=Path(__file__).resolve().parents[1]/'cli.py'
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as directory:
            work=Path(directory)
            for name,value in [('snapshot.json',self.snapshot),('plan.json',self.plan)]:
                (work/name).write_text(json.dumps(value,ensure_ascii=False),encoding='utf-8')
            def run(*args):return subprocess.run([sys.executable,'-B',str(tool),*args],cwd=work,capture_output=True,text=True,encoding='utf-8')
            result=run('policy-adoption-plan','--snapshot','snapshot.json','--plan','plan.json','--output','journal.json','--snapshot-output','after.json')
            self.assertEqual(0,result.returncode,result.stderr)
            journal=json.loads((work/'journal.json').read_text(encoding='utf-8'));self.assertEqual('APPROVED',journal['rows'][0]['status'])
            result=run('policy-adoption-emit-sql','--snapshot','snapshot.json','--journal','journal.json','--target','erp:fixture_erp','--backup-reference','SYNTH-BACKUP','--allow-db-writes','--output','review.sql')
            self.assertEqual(0,result.returncode,result.stderr)
            before=(work/'snapshot.json').read_bytes()
            result=run('policy-adoption-plan','--snapshot','snapshot.json','--plan','plan.json','--output','snapshot.json','--snapshot-output','unsafe.json')
            self.assertNotEqual(0,result.returncode);self.assertEqual(before,(work/'snapshot.json').read_bytes());self.assertFalse((work/'unsafe.json').exists())
