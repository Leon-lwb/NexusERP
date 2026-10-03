"""任务 C 的可执行运行时权威校验器：离线 HTTP/进程/数据库边界测试。"""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import hashlib
import importlib
import importlib.util
import io
import json
from pathlib import Path
import unittest
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlsplit


class BackendRuntimeAuthorityTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('procurement_runtime_attestation'),
                             'executable authenticated RuntimeAuthority is missing')
        self.m=importlib.import_module('procurement_runtime_attestation')
        self.p=importlib.import_module('procurement_runtime_preflight')
        self.now=datetime(2026,10,3,9,30,tzinfo=timezone(timedelta(hours=8)))
        self.started=self.now-timedelta(hours=1)
        self.credentials=self.p.Credentials(token='private-token',client_id='fixture-client',tenant_id='000000')
        self.process={'listeners':[{'port':8080,'pid':13579,'name':'java.exe','startedAt':self.started.isoformat()}]}
        self.calls=[];self.mutate=lambda data:None;self.http_error=None;self.clock_calls=0
        self.identity={'principalSha256':self.p.digest('1'),'tenantSha256':self.p.digest('000000')}
        self.databases={name:{'port':3306,'schema':schema,'serverUuidSha256':'d'*64}
                        for name,schema in self.p.SCHEMAS.items()}
        self.authority=self.build()

    def build(self,**overrides):
        args={'credentials':self.credentials,'opener':self.opener,'clock':lambda:self.now,
              'process_probe':lambda:deepcopy(self.process),'database_probe':lambda:deepcopy(self.databases)}
        return self.m.BackendRuntimeAuthority(**{**args,**overrides})

    def payload(self,challenge):
        return {'schemaVersion':1,'challengeSha256':self.p.digest(challenge),
                'observedAt':self.now.isoformat(),**self.identity,
                'runtime':{'pid':13579,'startedAt':self.started.isoformat(),'instanceSha256':'e'*64,
                           'activeProfiles':['dev'],'measurementMode':'IMMUTABLE_STARTUP_CLASS_BYTES',
                           'classLoading':'STANDARD_IMMUTABLE','runtimeOverrides':[]},
                'build':{'gitCommit':'a'*40,'artifactSha256':'b'*64,'manifestSha256':'c'*64,
                         'manifestSource':'EMBEDDED_BUILD_MANIFEST',
                         'classes':{name:{'sha256':'f'*64,'manifestClassSha256':'f'*64,'originSha256':'e'*64}
                                    for name in self.m.REQUIRED_CLASSES}},
                'datasources':{name:{**item,'host':'127.0.0.1','identitySource':'LIVE_JDBC_METADATA_SELECT',
                                    'observedAt':self.now.isoformat()}
                               for name,item in self.databases.items()}}

    def opener(self,request,timeout):
        self.calls.append(request);path=urlsplit(request.full_url).path
        if path=='/system/user/getInfo':
            document={'code':200,'data':{'user':{'userId':'1','tenantId':'000000'}}}
        else:
            self.assertEqual('/system/runtime/attestation',path)
            if self.http_error:raise self.http_error
            challenge=parse_qs(urlsplit(request.full_url).query)['challenge'][0]
            data=self.payload(challenge);self.mutate(data);document={'code':200,'data':data}
        result=io.BytesIO(json.dumps(document).encode());result.status=200;return result

    def enforce(self,authority=None):
        return (authority or self.authority).require_current(target=self.p.RuntimeTarget(),
                expected_head='a'*40,tenant_id='000000',principal_sha256=self.identity['principalSha256'])

    def test_constructor_has_no_network_or_permission_side_effects(self):
        self.assertEqual([],self.calls)
        self.assertIsInstance(self.authority,self.p.RuntimeAuthority)
        self.assertEqual('NOT_OBSERVED',self.authority.evidence()['status'])

    def test_authenticated_nonce_bound_build_and_live_datasource_proof_enforces(self):
        self.assertIsNone(self.enforce())
        evidence=self.authority.evidence();self.assertEqual('VERIFIED',evidence['status'])
        self.assertEqual('authenticated_backend_loaded_manifest_and_live_database_identity',evidence['source'])
        self.assertFalse(evidence['businessWriteAuthorized'])
        self.assertEqual(len(self.m.REQUIRED_CLASSES),evidence['loadedClassCount'])
        self.assertNotIn('private-token',json.dumps(evidence))
        self.assertTrue(all(r.get_method()=='GET' for r in self.calls))
        self.assertTrue(all(r.get_header('Authorization')=='Bearer private-token' for r in self.calls))

    def test_each_enforcement_fetches_a_new_challenge_and_does_not_accept_saved_reports(self):
        self.enforce();self.enforce()
        challenges=[parse_qs(urlsplit(r.full_url).query)['challenge'][0] for r in self.calls
                    if urlsplit(r.full_url).path=='/system/runtime/attestation']
        self.assertEqual(2,len(challenges));self.assertNotEqual(challenges[0],challenges[1])
        self.assertNotIn('attestation_file',__import__('inspect').signature(self.m.BackendRuntimeAuthority).parameters)

    def test_flags_filetime_git_head_or_classpath_are_not_loaded_artifact_proof(self):
        for field,value in (('build',{'gitCommit':'a'*40,'verified':True,'filetime':self.now.isoformat()}),
                            ('runtime',{'pid':13579,'startedAt':self.started.isoformat(),'classpathVerified':True})):
            with self.subTest(field=field):
                self.mutate=lambda d,f=field,v=value:d.__setitem__(f,v)
                with self.assertRaises(self.p.PreflightBlocked):self.enforce()
                self.assertEqual('BLOCKED',self.authority.evidence()['status'])

    def test_final_committed_head_loaded_class_hash_origin_and_manifest_are_required(self):
        mutations=[lambda d:d['build'].__setitem__('gitCommit','b'*40),
                   lambda d:d['build'].__setitem__('manifestSource','CURRENT_GIT_HEAD'),
                   lambda d:d['build'].__setitem__('artifactSha256',True),
                   lambda d:d['build']['classes'].pop(self.m.REQUIRED_CLASSES[0]),
                   lambda d:d['build']['classes'][self.m.REQUIRED_CLASSES[0]].__setitem__('sha256','b'*64),
                   lambda d:d['build']['classes'][self.m.REQUIRED_CLASSES[0]].__setitem__('originSha256',None)]
        for mutate in mutations:
            with self.subTest(mutation=mutations.index(mutate)):
                self.mutate=mutate
                with self.assertRaises(self.p.PreflightBlocked):self.enforce()

    def test_nonce_principal_tenant_profile_and_runtime_overrides_cannot_be_spoofed(self):
        mutations=[lambda d:d.__setitem__('challengeSha256','0'*64),
                   lambda d:d.__setitem__('principalSha256','0'*64),
                   lambda d:d.__setitem__('tenantSha256','0'*64),
                   lambda d:d['runtime'].__setitem__('activeProfiles',['dev','decision-demo']),
                   lambda d:d['runtime'].__setitem__('runtimeOverrides',['javaagent']),
                   lambda d:d['runtime'].__setitem__('measurementMode','CURRENT_DISK_CLASSES'),
                   lambda d:d['runtime'].__setitem__('classLoading','CUSTOM')]
        for mutate in mutations:
            with self.subTest(mutation=mutations.index(mutate)):
                self.mutate=mutate
                with self.assertRaises(self.p.PreflightBlocked):self.enforce()

    def test_stale_future_and_naive_time_are_rejected(self):
        for when in ((self.now-timedelta(minutes=1)).isoformat(),(self.now+timedelta(minutes=1)).isoformat(),
                     self.now.replace(tzinfo=None).isoformat()):
            with self.subTest(time=when):
                self.mutate=lambda d,w=when:d.__setitem__('observedAt',w)
                with self.assertRaises(self.p.PreflightBlocked):self.enforce()

    def test_backend_pid_start_and_local_listener_must_remain_bound(self):
        self.mutate=lambda d:d['runtime'].__setitem__('pid',999)
        with self.assertRaises(self.p.PreflightBlocked):self.enforce()
        self.mutate=lambda d:self.process['listeners'][0].__setitem__('pid',24680)
        with self.assertRaises(self.p.PreflightBlocked):self.enforce()

    def test_all_four_backend_datasources_must_equal_live_mysql_identity_not_url(self):
        mutations=[lambda d:d['datasources'].pop('mes'),
                   lambda d:d['datasources']['master'].__setitem__('port',3307),
                   lambda d:d['datasources']['erp'].__setitem__('schema','ry-vue'),
                   lambda d:d['datasources']['iot'].__setitem__('serverUuidSha256','9'*64),
                   lambda d:d['datasources']['master'].__setitem__('identitySource','CONFIG_JDBC_URL')]
        for mutate in mutations:
            with self.subTest(mutation=mutations.index(mutate)):
                self.mutate=mutate
                with self.assertRaises(self.p.PreflightBlocked):self.enforce()

    def test_endpoint_absence_is_specific_and_unknown_errors_never_echo_secrets(self):
        self.http_error=HTTPError('http://127.0.0.1:8080',404,'private-token',{},None)
        with self.assertRaises(self.p.PreflightBlocked) as caught:self.enforce()
        self.assertEqual('ATTESTATION_ENDPOINT_MISSING',caught.exception.category)
        self.assertNotIn('private-token',str(caught.exception))
        def failing():raise RuntimeError('private-token database-password')
        self.http_error=None
        with self.assertRaises(self.p.PreflightBlocked) as caught:self.enforce(self.build(database_probe=failing))
        self.assertNotIn('database-password',str(caught.exception))

    def test_authority_binds_its_own_credentials_not_callers_identity_strings(self):
        for kwargs in ({'tenant_id':'123456'}, {'principal_sha256':'0'*64}):
            args={'target':self.p.RuntimeTarget(),'expected_head':'a'*40,'tenant_id':'000000',
                  'principal_sha256':self.identity['principalSha256'],**kwargs}
            with self.subTest(kwargs=kwargs),self.assertRaises(self.p.PreflightBlocked):
                self.authority.require_current(**args)



    def test_native_cim_seven_digit_fraction_is_supported_without_float_or_naive_time(self):
        timestamp='2026-10-03T09:10:44.3424190+08:00'
        try:parsed=self.m._time(timestamp)
        except self.p.PreflightBlocked:parsed=None
        self.assertIsNotNone(parsed,'native CIM ISO timestamp must not block before an authenticated backend probe')
        self.assertEqual('2026-10-03T09:10:44.342419+08:00',parsed.isoformat())



    def test_live_database_identities_use_four_actual_connections_and_read_only_cleanup(self):
        calls=[];queries=[];cleanups=[];outer=self
        class Cursor:
            def __init__(self,schema):self.schema=schema
            def __enter__(self):return self
            def __exit__(self,*args):return False
            def execute(self,sql):queries.append(sql)
            def fetchone(self):return (3306,'12345678-1234-1234-1234-123456789abc',self.schema)
        class Connection:
            def __init__(self,schema):self.schema=schema
            def cursor(self):return Cursor(self.schema)
            def rollback(self):cleanups.append(('rollback',self.schema))
            def close(self):cleanups.append(('close',self.schema))
        def connector(**kwargs):calls.append(kwargs);return Connection(kwargs['database'])
        settings=self.p.LocalSettings({name:{'username':'fixture-'+name,'password':'private-'+name,
                    'url':'jdbc:mysql://127.0.0.1:3306/'+schema} for name,schema in self.p.SCHEMAS.items()},{})
        result=self.m.live_database_identities(settings,connector=connector)
        self.assertEqual(set(self.p.SCHEMAS),set(result));self.assertEqual(4,len(calls));self.assertEqual(8,len(cleanups))
        self.assertEqual(['fixture-'+name for name in self.p.SCHEMAS],[item['user'] for item in calls])
        self.assertTrue(all(q.startswith(('SET SESSION TRANSACTION','START TRANSACTION','SELECT ')) for q in queries))
        self.assertEqual(4,queries.count('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY'))
        self.assertNotIn('private-master',json.dumps(result));self.assertNotIn('12345678-1234-',json.dumps(result))

    def test_unresolved_encrypted_password_is_never_sent_to_a_database_connector(self):
        calls=[]
        settings=self.p.LocalSettings({name:{'username':'fixture-'+name,'password':'ENC(private-ciphertext)',
                   'url':'jdbc:mysql://127.0.0.1:3306/'+schema} for name,schema in self.p.SCHEMAS.items()},{})
        with self.assertRaises(self.p.PreflightBlocked):
            self.m.live_database_identities(settings,connector=lambda **kw:calls.append(kw))
        self.assertEqual([],calls,'the original 1045 mistake must fail before any password is transmitted')

    def test_stock_authenticated_actuator_metadata_is_diagnostic_not_loaded_build_authority(self):
        probe=getattr(self.m,'management_metadata_evidence',None)
        self.assertTrue(callable(probe),'existing authenticated actuator metadata needs a safe read-only probe')
        import tempfile
        calls=[]
        def opener(request,timeout):
            calls.append(request)
            doc={'git':{'commit':{'id':'a'*40}},'build':{'version':'fixture'}} if request.full_url.endswith('/info') else {'activeProfiles':['dev'],'propertySources':[{'properties':{'password':{'value':'private-actuator-secret'}}}]}
            response=io.BytesIO(json.dumps(doc).encode());response.status=200;return response
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);folder=root/'ruoyi-admin/src/main/resources';folder.mkdir(parents=True)
            (folder/'application.yml').write_text('spring:\n  boot:\n    admin:\n      client:\n        username: fixture-monitor\n        password: 123456\n',encoding='utf-8')
            (folder/'application-dev.yml').write_text('{}',encoding='utf-8')
            evidence=probe(root=root,opener=opener)
        self.assertEqual('OBSERVED',evidence['status']);self.assertTrue(evidence['gitPresent'])
        self.assertTrue(evidence['notLoadedArtifactProof']);self.assertEqual(['dev'],evidence['activeProfiles'])
        self.assertTrue(all(request.get_method()=='GET' for request in calls));self.assertEqual(2,len(calls))
        self.assertNotIn('private-actuator-secret',json.dumps(evidence));self.assertNotIn('123456',json.dumps(evidence))



    def test_loopback_hostname_alias_is_bound_to_the_same_8080_listener_not_a_different_environment(self):
        credentials=self.p.Credentials(token='private-token',client_id='fixture-client',tenant_id='000000',
                                       base_url='http://localhost:8080')
        try:result=self.enforce(self.build(credentials=credentials))
        except self.p.PreflightBlocked:result='rejected'
        self.assertIsNone(result,'validated localhost/127.0.0.1 aliases must not prevent a real local attestation')

    def test_receipt_producer_and_mq_writer_classes_are_in_the_required_loaded_manifest(self):
        for name in ('com.nexus.erp.wm.service.impl.WmMqSendService',
                     'com.nexus.erp.mq.message.StockInMessage',
                     'com.nexus.erp.mq.consumer.PurchaseReceiveWritebackConsumer',
                     'com.nexus.common.mq.consumer.BaseMessageConsumer'):
            self.assertIn(name,self.m.REQUIRED_CLASSES)


if __name__=='__main__':unittest.main()
