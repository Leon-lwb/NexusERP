"""Authorized remote SOP bucket/config tests; mocked HTTP, no live side effects."""
import hashlib
import io
import json
from pathlib import Path
import sys
import unittest
import urllib.error
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parent))
from storage import SafetyError,S3Settings,S3Transport
from provision import AppSession,config_payload,validate_config_rows,create_nondefault_config

REMOTE='159.75.14.226:9005'

class RemoteBucketTests(unittest.TestCase):
    def remote(self):return S3Transport(S3Settings(REMOTE,'sop','private-access','private-secret'))
    def test_only_explicit_remote_target_accepts(self):
        t=self.remote();self.assertEqual(REMOTE,t.settings.endpoint)
        for endpoint in ['159.75.14.227:9005','159.75.14.226:9000','example.com:9005']:
            with self.assertRaises(SafetyError):S3Settings(endpoint,'sop','a','s')

    def test_new_bucket_is_created_without_policy_acl_or_delete(self):
        t=self.remote()
        with patch.object(t,'_request',side_effect=[(404,{},b''),(200,{},b''),(200,{},b''),(404,{'no-bucket-policy':'true'},b'')]) as request,patch.object(t,'preflight_privacy') as privacy:
            result=t.ensure_private_bucket()
        self.assertTrue(result['bucketCreated'])
        self.assertEqual(['HEAD','PUT','HEAD','GET'],[c.args[0] for c in request.call_args_list])
        self.assertTrue(all(c.kwargs.get('bucket_root') for c in request.call_args_list[:3]))
        self.assertTrue(all(not c.kwargs.get('bucket_policy') for c in request.call_args_list[:3]))
        privacy.assert_called_once()

    def test_existing_bucket_must_verify_private_and_never_gets_mutation(self):
        t=self.remote()
        with patch.object(t,'_request',return_value=(200,{},b'')) as request,patch.object(t,'preflight_privacy',side_effect=SafetyError('MINIO_ANONYMOUS_POLICY_EXPOSES_BATCH')):
            with self.assertRaises(SafetyError):t.ensure_private_bucket()
        self.assertTrue(all(c.args[0] in ('HEAD','GET') for c in request.call_args_list))

    def test_existing_ruoyi_bucket_cannot_be_created_or_overwritten(self):
        t=S3Transport(S3Settings('127.0.0.1:9005','ruoyi','a','s'))
        with self.assertRaises(SafetyError):t.ensure_private_bucket()
        key='mes/sop/000000/reference-20261008/SOP_TEST/V1.1/'+'a'*64+'.pdf'
        with self.assertRaises(SafetyError):t._request('PUT',key,b'%PDF-test')
        for method in ['DELETE','POST','PATCH']:
            with self.assertRaises(SafetyError):t._request(method,'',bucket_root=True)

    def test_shared_policy_read_only_audit_is_bounded(self):
        t=self.remote()
        with patch.object(t,'_request',return_value=(200,{},b'{"Statement":[]}')) as req:
            digest=t.shared_bucket_policy_digest()
        self.assertEqual(hashlib.sha256(b'{"Statement":[]}').hexdigest(),digest)
        self.assertEqual('GET',req.call_args.args[0])
        self.assertEqual('ruoyi',req.call_args.kwargs.get('policy_bucket'))

    def test_bucket_create_http_error_exposes_only_category(self):
        def opener(req,timeout):raise urllib.error.HTTPError(req.full_url,403,'private-secret',{},io.BytesIO(b'private-secret'))
        t=S3Transport(S3Settings(REMOTE,'sop','private-access','private-secret'),opener)
        with self.assertRaises(SafetyError) as e:t._request('PUT','',bucket_root=True)
        self.assertEqual('MINIO_HTTP_403',str(e.exception))

class ConfigTests(unittest.TestCase):
    def credential(self):return AppSession('private-token','private-client','000000')
    def old(self):return [{'ossConfigId':'1','configKey':'minio','bucketName':'ruoyi','status':'0','accessPolicy':'0','endpoint':'127.0.0.1:9005'}]
    def test_nondefault_payload_private_sop_target(self):
        payload=config_payload(S3Settings(REMOTE,'sop','private-access','private-secret'))
        for k,v in {'configKey':'sop','bucketName':'sop','endpoint':REMOTE,'accessPolicy':'0','status':'1','isHttps':'N'}.items():self.assertEqual(v,payload[k])
        self.assertEqual('',payload['prefix']);self.assertEqual('',payload['domain'])
        self.assertNotIn('private-token',repr(self.credential()))
        self.assertNotIn('private-client',repr(self.credential()))

    def test_valid_config_preserves_existing_default(self):
        old=self.old();new=old+[dict(config_payload(S3Settings(REMOTE,'sop','a','s')),ossConfigId='7')]
        validate_config_rows(old,new)
        for mutate in [lambda rows:rows[0].update(status='1'),lambda rows:rows[1].update(status='0'),lambda rows:rows[1].update(accessPolicy='1'),lambda rows:rows[1].update(endpoint='remote')]:
            rows=json.loads(json.dumps(new));mutate(rows)
            with self.assertRaises(SafetyError):validate_config_rows(old,rows)

    def test_config_post_uses_normal_endpoint_not_change_status(self):
        session=self.credential();settings=S3Settings(REMOTE,'sop','a','s')
        before=self.old();after=before+[dict(config_payload(settings),ossConfigId='7')]
        calls=[]
        def request(method,path,payload=None):
            calls.append((method,path,payload))
            if method=='POST':return {'code':200}
            return {'code':200,'rows':before if len(calls)==1 else after,'total':len(before) if len(calls)==1 else len(after)}
        with patch.object(AppSession,'request',side_effect=request):result=create_nondefault_config(session,settings)
        self.assertTrue(result['configCreated'])
        self.assertEqual([('GET','/resource/oss/config/list'),('POST','/resource/oss/config'),('GET','/resource/oss/config/list')],[(c[0],c[1]) for c in calls])
        self.assertTrue(all('changeStatus' not in c[1] for c in calls))



class WholePrivateBucketTests(unittest.TestCase):
    def test_anonymous_other_prefix_policy_is_not_a_private_sop_bucket(self):
        t=S3Transport(S3Settings(REMOTE,'sop','a','s'))
        policy={'Statement':[{'Effect':'Allow','Principal':'*','Action':'s3:GetObject','Resource':'arn:aws:s3:::sop/public/*'}]}
        with patch.object(t,'_request',side_effect=[(200,{},b''),(200,{},json.dumps(policy).encode())]):
            with self.assertRaises(SafetyError):t.ensure_private_bucket()

if __name__=='__main__':unittest.main()
