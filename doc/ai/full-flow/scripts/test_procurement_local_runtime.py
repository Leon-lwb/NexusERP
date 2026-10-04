"""本地开发运行检查的离线反例；不连接真实服务或数据库。"""
from copy import deepcopy
from datetime import datetime,timedelta,timezone
import importlib,importlib.util,io,json,unittest
from urllib.parse import parse_qs,urlsplit


class LocalDevelopmentRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('procurement_local_runtime'),'缺少已批准的本地运行检查')
        self.m=importlib.import_module('procurement_local_runtime');self.p=importlib.import_module('procurement_runtime_preflight')
        self.now=datetime(2026,10,4,5,tzinfo=timezone.utc);self.started=self.now-timedelta(hours=1)
        self.credentials=self.p.Credentials(token='private-token',client_id='private-client',tenant_id='000000')
        self.principal=self.p.digest('1');self.calls=[];self.mutate=lambda data:None
        self.process={'ports':{'8080':'REACHABLE','3306':'REACHABLE'},'listeners':[{'port':8080,'pid':13579,'name':'java.exe','startedAt':self.started.isoformat()}],
            'targetSnapshots':{'module/target/classes/Service.class':{'mtime':self.started.isoformat(),'sha256':'f'*64,'notLoadedArtifactProof':True}}}
        self.databases={name:{'port':3306,'schema':schema,'serverUuidSha256':'d'*64} for name,schema in self.p.SCHEMAS.items()}
        self.authority=self.build()
    def build(self,**overrides):
        args={'credentials':self.credentials,'opener':self.opener,'clock':lambda:self.now,
            'process_probe':lambda:deepcopy(self.process),'database_probe':lambda:deepcopy(self.databases)}
        return self.m.LocalDevelopmentRuntimeAuthority(**{**args,**overrides})
    def payload(self,challenge):
        return {'schemaVersion':2,'status':'BLOCKED','diagnosticCategory':'ATTESTATION_BUILD_UNPROVEN',
            'challengeSha256':self.p.digest(challenge),'principalSha256':self.principal,'tenantSha256':self.p.digest('000000'),
            'observedAt':self.now.isoformat(),'runtime':{'pid':13579,'startedAt':self.started.isoformat(),'activeProfiles':['dev']},
            'datasourceObservationStatus':'OBSERVED','datasources':{name:{**item,'identitySource':'LIVE_JDBC_METADATA_SELECT',
                'observedAt':self.now.isoformat()} for name,item in self.databases.items()},
            'build':{'supportedProofDomain':'NONE','proofStatus':'UNPROVEN','finalBytesProven':False,
                'diagnosticCategory':'ATTESTATION_AGENTS_UNSUPPORTED'}}
    def opener(self,request,timeout):
        self.calls.append(request);path=urlsplit(request.full_url).path
        if path=='/system/user/getInfo':document={'code':200,'data':{'user':{'userId':'1','tenantId':'000000'}}}
        else:
            self.assertEqual('/system/runtime/attestation',path)
            challenge=parse_qs(urlsplit(request.full_url).query)['challenge'][0]
            data=self.payload(challenge);self.mutate(data);document={'code':412,'data':data}
        response=io.BytesIO(json.dumps(document).encode());response.status=200;return response
    def enforce(self,authority=None,target=None):
        return (authority or self.authority).require_current(target=target or self.p.RuntimeTarget(),expected_head='a'*40,
            tenant_id='000000',principal_sha256=self.principal)
    def test_constructor_has_no_network_or_database_side_effect(self):
        self.assertEqual([],self.calls);self.assertEqual('NOT_OBSERVED',self.authority.evidence()['status'])
    def test_ide_and_unproven_strong_signature_do_not_block_local_consistency(self):
        self.assertIsNone(self.enforce());evidence=self.authority.evidence()
        self.assertEqual('LOCAL_DEV_CHECKED',evidence['status']);self.assertFalse(evidence['productionProof'])
        self.assertFalse(evidence['finalClassBytesProven']);self.assertFalse(evidence['businessWriteAuthorized'])
        self.assertEqual('WORKSPACE_CONTEXT_ONLY',evidence['expectedCommitBinding'])
    def test_json_verified_flags_never_upgrade_to_production_proof(self):
        self.mutate=lambda data:data['build'].update(proofStatus='VERIFIED',finalBytesProven=True)
        self.enforce();self.assertFalse(self.authority.evidence()['productionProof']);self.assertFalse(self.authority.evidence()['finalClassBytesProven'])
    def test_remote_and_production_targets_still_rejected(self):
        for target in (self.p.RuntimeTarget(base_url='http://remote.example:8080'),self.p.RuntimeTarget(profile='prod')):
            with self.subTest(target=target),self.assertRaises(self.p.PreflightBlocked):self.enforce(target=target)
        self.assertEqual([],self.calls)
    def test_production_runtime_profile_rejected_before_database_probe(self):
        calls=[];authority=self.build(database_probe=lambda:calls.append('database'))
        self.mutate=lambda data:data['runtime'].update(activeProfiles=['prod'])
        with self.assertRaises(self.p.PreflightBlocked):self.enforce(authority)
        self.assertEqual([],calls)
    def test_wrong_pid_or_start_time_is_not_current_service(self):
        for values in ({'pid':13580},{'startedAt':(self.started+timedelta(milliseconds=1)).isoformat()}):
            self.mutate=lambda data:data['runtime'].update(values)
            with self.subTest(values=values),self.assertRaises(self.p.PreflightBlocked):self.enforce()
    def test_restart_during_check_is_detected(self):
        count=[0]
        def process():
            result=deepcopy(self.process);count[0]+=1
            if count[0]>1:result['listeners'][0]['pid']+=1
            return result
        with self.assertRaises(self.p.PreflightBlocked):self.enforce(self.build(process_probe=process))
    def test_restart_during_final_authentication_cannot_pass_old_runtime(self):
        auth_calls=[0]
        def opener(request,timeout):
            if urlsplit(request.full_url).path=='/system/user/getInfo':
                auth_calls[0]+=1
                if auth_calls[0]==2:self.process['listeners'][0]['pid']=23579
            return self.opener(request,timeout)
        authority=self.build(opener=opener)
        with self.assertRaises(self.p.PreflightBlocked) as caught:self.enforce(authority)
        self.assertEqual('RUNTIME_RESTARTED',caught.exception.category)
        self.assertEqual('BLOCKED',authority.evidence()['status'])
    def test_identity_and_nonce_binding_remain(self):
        for values in ({'tenantSha256':'e'*64},{'principalSha256':'e'*64},{'challengeSha256':'e'*64}):
            self.mutate=lambda data:data.update(values)
            with self.subTest(values=values),self.assertRaises(self.p.PreflightBlocked):self.enforce()
    def test_stale_document_rejected(self):
        self.mutate=lambda data:data.update(observedAt=(self.now-timedelta(minutes=3)).isoformat())
        with self.assertRaises(self.p.PreflightBlocked):self.enforce()
    def test_reported_datasource_must_be_real_and_match_independent_read(self):
        mutations=(lambda d:d.update(datasourceObservationStatus='UNPROVEN'),
            lambda d:d['datasources'].pop('erp'),lambda d:d['datasources']['erp'].update(schema='ry-vue'),
            lambda d:d['datasources']['erp'].update(serverUuidSha256='e'*64),
            lambda d:d['datasources']['erp'].update(identitySource='CONFIGURATION_URL'),
            lambda d:d['datasources']['erp'].update(port=3306.0))
        for mutate in mutations:
            self.mutate=mutate
            with self.subTest(mutation=mutate),self.assertRaises(self.p.PreflightBlocked):self.enforce()
    def test_actual_database_failure_is_not_a_flag_or_secret_echo(self):
        def broken():raise RuntimeError('private-token private-password')
        authority=self.build(database_probe=broken)
        with self.assertRaises(self.p.PreflightBlocked) as caught:self.enforce(authority)
        self.assertNotIn('private-token',str(caught.exception));self.assertNotIn('private-password',json.dumps(authority.evidence()))
    def test_every_check_is_new_get_only_observation(self):
        self.enforce();self.enforce()
        challenges=[parse_qs(urlsplit(request.full_url).query)['challenge'][0] for request in self.calls if urlsplit(request.full_url).path.endswith('/attestation')]
        self.assertEqual(2,len(set(challenges)));self.assertTrue(all(request.get_method()=='GET' for request in self.calls))
    def test_artifact_after_start_is_warning_not_final_loaded_proof(self):
        self.process['targetSnapshots']['module/target/classes/Service.class']['mtime']=self.now.isoformat()
        self.enforce();evidence=self.authority.evidence()
        self.assertIn('ARTIFACT_NEWER_THAN_PROCESS_REQUIRES_API_RECHECK',evidence['warnings'])
        self.assertFalse(evidence['artifactSnapshotsAreLoadedProof'])
    def test_factory_default_local_and_opt_in_strict_are_distinct(self):
        local=self.m.create_runtime_authority(credentials=self.credentials)
        strict=self.m.create_runtime_authority(mode='strict',credentials=self.credentials)
        self.assertIsInstance(local,self.m.LocalDevelopmentRuntimeAuthority)
        self.assertIsInstance(strict,importlib.import_module('procurement_runtime_attestation').BackendRuntimeAuthority)
        with self.assertRaises(self.p.PreflightBlocked):self.m.create_runtime_authority(mode='production-bypass',credentials=self.credentials)
    def test_preflight_reports_local_level_without_business_authorization(self):
        self.enforce()
        report=self.p.readiness(self.p.RuntimeTarget(),self.credentials,'a'*40,{'status':'VERIFIED','principalSha256':self.principal},
            {'status':'OBSERVED','gaps':[]},self.process,attestor=self.authority)
        self.assertEqual('LOCAL_DEV_CHECKED',report['loadedRuntime']['status']);self.assertFalse(report['businessWriteAuthorized'])
        self.assertFalse(report['loadedRuntime']['productionProof'])
    def test_preflight_local_does_not_discard_business_source_gaps(self):
        self.enforce()
        report=self.p.readiness(self.p.RuntimeTarget(),self.credentials,'a'*40,{'status':'VERIFIED','principalSha256':self.principal},
            {'status':'OBSERVED','gaps':['stock_balance_source_reconciliation_not_established']},self.process,attestor=self.authority)
        self.assertEqual('BLOCKED',report['status']);self.assertEqual(['STOCK_SOURCE_UNVERIFIED'],report['diagnosticCategories'])
        self.assertEqual('LOCAL_DEV_CHECKED',report['loadedRuntime']['status'])
    def test_default_inspector_is_local_and_accepts_explicit_strict(self):
        import inspect
        self.assertEqual('local-dev',inspect.signature(self.p.inspect_local).parameters['runtime_check_mode'].default)

if __name__=='__main__':unittest.main()
