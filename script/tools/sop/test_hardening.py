"""Adversarial regressions from independent safety review, no live service access."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parent))
import batch
import pdf_document
from storage import S3Transport, S3Settings, SafetyError
from test_batch import fixture,artifacts,applied
from test_storage import DATA,ARTIFACT

class HardeningTests(unittest.TestCase):
    def test_duplicate_or_invalid_legacy_primary_key_rejects(self):
        for table,key in [('versions','version_id'),('bindings','binding_id')]:
            for value in [1,'1.5',0,999]:
                s=fixture();s[table][1][key]=value
                with self.assertRaises(SafetyError):batch.validate_source(s)

    def test_persisted_backup_cannot_drop_preserved_columns(self):
        for table,key in [('documents','approval_time'),('documents','remark'),('versions','published_time'),('bindings','version_no')]:
            s=fixture();s[table][0].pop(key)
            with self.assertRaises(SafetyError):batch.validate_source(s)

    def test_external_approval_digest_rejects_coordinated_file_substitution(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'manifest.json';p.write_bytes(b'{"reviewed":true}')
            approved=hashlib.sha256(p.read_bytes()).hexdigest()
            batch.verify_approval_digest(p,approved)
            p.write_bytes(b'{"reviewed":false}')
            with self.assertRaises(SafetyError):batch.verify_approval_digest(p,approved)
            with self.assertRaises(SafetyError):batch.verify_approval_digest(p,'')

    def test_qc_pdf_cannot_be_substituted_for_laser_despite_valid_pdf_hash(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'qc.pdf'
            pdf_document.render_pdf({'sop_id':54,'sop_code':'SOP20260914054','sop_name':'质检作业指导书','process_code':'QC'},p)
            with self.assertRaises(ValueError):
                pdf_document.validate_expected_pdf(p,{'sop_id':1,'sop_code':'SOP20260914001','sop_name':'激光切割作业指导书','process_code':'LASER'})
            pdf_document.validate_expected_pdf(p,{'sop_id':54,'sop_code':'SOP20260914054','sop_name':'质检作业指导书','process_code':'QC'})

    def test_all_protected_history_queries_are_current_locking_reads(self):
        s=fixture();sql=[]
        class Cursor:
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def execute(self,query,args=None):self.query=query;sql.append(query)
            def fetchall(self):
                if 'sop_document' in self.query:return [] if 'NOT BETWEEN' in self.query else s['documents']
                if 'sop_version' in self.query:return [] if 'NOT BETWEEN' in self.query else s['versions']
                if 'sop_process_binding' in self.query:return s['bindings']
                if 'es_signature' in self.query:return [{'signature_id':99,'biz_id':2}]
                return []
        class Conn:
            def cursor(self):return Cursor()
        result=batch.read_snapshot(Conn(),lock=True)
        self.assertEqual([1,2],result['viewedSopIds'])
        self.assertEqual(7,len(sql))
        self.assertTrue(all(q.endswith('FOR UPDATE') for q in sql))

    def test_unverified_bucket_cannot_receive_any_put(self):
        t=S3Transport(S3Settings('159.75.14.226:9005','sop','a','s'))
        with patch.object(t,'_request',return_value=(404,{},b'')) as request,patch.object(t,'verify'):
            with self.assertRaises(SafetyError):t.upload_immutable(ARTIFACT,DATA)
        self.assertFalse(any(c.args[0]=='PUT' for c in request.call_args_list))

    def test_public_policy_rejects_before_upload_and_no_policy_accepts(self):
        t=S3Transport(S3Settings('159.75.14.226:9005','sop','a','s'))
        policy={'Statement':[{'Effect':'Allow','Principal':'*','Action':['s3:GetObject'],'Resource':['arn:aws:s3:::sop/*']}]}
        with patch.object(t,'_request',return_value=(200,{},json.dumps(policy).encode())):
            with self.assertRaises(SafetyError):t.preflight_privacy([ARTIFACT])
        self.assertEqual(frozenset(),t._privacy_keys)
        with patch.object(t,'_request',return_value=(404,{'no-bucket-policy':'true'},b'')):
            t.preflight_privacy([ARTIFACT])
        self.assertIn(ARTIFACT['objectKey'],t._privacy_keys)

    def _connection(self,commit_fails=False,release_fails=False):
        class Cursor:
            rowcount=1
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def execute(self,sql,args=None):
                if release_fails and 'RELEASE_LOCK' in sql:raise OSError('private-cleanup-secret')
            def fetchone(self):return {'acquired':1}
        class Conn:
            committed=False
            def cursor(self):return Cursor()
            def begin(self):pass
            def commit(self):
                self.committed=True
                if commit_fails:raise OSError('private-ack-secret')
            def rollback(self):pass
        return Conn()

    def test_lost_commit_ack_is_unknown_not_falsely_rolled_back(self):
        s=fixture();rows=batch.plan_rows(s,artifacts(s));now=applied(s,rows);conn=self._connection(commit_fails=True)
        with patch('batch.read_snapshot',side_effect=[s,now]),patch('batch._read_new_oss'),self.assertRaises(SafetyError) as e:
            batch.write_database(conn,s,rows)
        self.assertTrue(conn.committed)
        self.assertEqual('DATABASE_COMMIT_OUTCOME_UNKNOWN',str(e.exception))

    def test_post_commit_cleanup_failure_does_not_mask_confirmed_commit(self):
        s=fixture();rows=batch.plan_rows(s,artifacts(s));now=applied(s,rows);conn=self._connection(release_fails=True)
        with patch('batch.read_snapshot',side_effect=[s,now]),patch('batch._read_new_oss'):
            batch.write_database(conn,s,rows)
        self.assertTrue(conn.committed)


class AdditionalRaceTests(unittest.TestCase):
    def test_manifest_is_parsed_from_the_exact_approved_byte_buffer(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d);source=fixture();items=artifacts(source)
            manifest={'batch':batch.BATCH,'tenantId':batch.TENANT,'sourceDigest':batch.canonical_digest(source),'context':{},'artifacts':items}
            changed=copy.deepcopy(manifest);changed['artifacts'][0]['ossId']='4000000000000000001'
            batch.save_private_json(directory/'backup.json',source)
            batch.save_private_json(directory/'manifest.json',manifest)
            file=directory/'manifest.json';approved=hashlib.sha256(file.read_bytes()).hexdigest()
            original_read=Path.read_bytes;reads=[]
            def replace_after_read(path):
                data=original_read(path)
                if path==file:
                    reads.append(path)
                    file.write_text(json.dumps(changed,ensure_ascii=False),encoding='utf-8')
                return data
            info={a['sopCode']:{k:a[k] for k in ('sha256','sm3','fileSize','pages','textCharacters')} for a in items}
            with patch.object(Path,'read_bytes',replace_after_read),patch('batch.validate_expected_pdf',side_effect=lambda path,doc:info[doc['sop_code']]):
                _,loaded,rows=batch._load_prepared(directory,{},approved)
            self.assertEqual(int(items[0]['ossId']),rows[0]['oss']['oss_id'])
            self.assertEqual(items[0]['ossId'],loaded['artifacts'][0]['ossId'])
            self.assertEqual(1,len(reads))

    def test_anonymous_mutation_or_wildcard_allow_is_never_private(self):
        for action in ['s3:PutObject','s3:DeleteObject','s3:PutObjectAcl','s3:*']:
            t=S3Transport(S3Settings('159.75.14.226:9005','sop','a','s'))
            policy={'Statement':[{'Effect':'Allow','Principal':{'AWS':['*']},'Action':[action],'Resource':['arn:aws:s3:::sop/*']}]}
            with patch.object(t,'_request',return_value=(200,{},json.dumps(policy).encode())),self.assertRaises(SafetyError):
                t.preflight_privacy([ARTIFACT])
            self.assertEqual(frozenset(),t._privacy_keys)

if __name__=='__main__':unittest.main()
