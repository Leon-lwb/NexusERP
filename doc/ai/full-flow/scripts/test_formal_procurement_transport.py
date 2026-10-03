"""任务 C 的传输契约测试；注入的服务严格保持离线。"""
import importlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('formal_procurement_transport'),
                             'Task C transport implementation is missing')
        self.m=importlib.import_module('formal_procurement_transport')
        self.p=importlib.import_module('procurement_runtime_preflight')
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.patch=patch.object(self.m,'PRIVATE_ROOT',Path(self.temp.name))
        self.patch.start();self.addCleanup(self.patch.stop)
        self.calls=[]
        self.recovered=None
        outer=self
        class Authority(self.m.WriteAuthority):
            def require_authorized(self,*,transport,scope):return None
            def require_retry_safe(self,*,transport,scope):return None
        class Lifecycle(self.m.LifecycleAuthority):
            def require_closed(self,*,scope):return None
        class Runtime(self.p.RuntimeAuthority):
            def require_current(self,**kwargs):return None
        class Recovery(self.m.RecoveryReader):
            def read_back(self,*,scope,request):return outer.recovered
        self.authority,self.lifecycle,self.runtime,self.recovery=Authority(),Lifecycle(),Runtime(),Recovery()
        self.journal=self.m.AuditJournal(Path(self.temp.name)/'runtime/audit.jsonl')
        self.client=self.build()

    def opener(self,request,timeout):
        self.calls.append(request)
        if request.get_method()=='GET':
            raw=b'{"code":200,"data":{"user":{"tenantId":"000000","userId":"9223372036854775807"}}}'
        else:raw=b'{"code":200}'
        response=io.BytesIO(raw);response.status=200
        return response

    def build(self,**overrides):
        args={'target':self.p.RuntimeTarget(),'credentials':self.p.Credentials(token='secret-token',
              client_id='test-client',tenant_id='000000'),'expected_head':'a'*40,
              'authority':self.authority,'runtime':self.runtime,'lifecycle':self.lifecycle,
              'recovery':self.recovery,'journal':self.journal,'opener':self.opener}
        return self.m.FormalProcurementTransport(**{**args,**overrides})

    def request(self,path='/erp/pu/requisition/certified',body=None,method='POST'):
        return {'method':method,'path':path,'body':{'requestKey':'procurement-intent-0001','materialId':'9223372036854775807',
             'vendorId':'123456789123456789','windowEnd':'2026-10-30',
             'requirementDetailIds':['123456789123456788'],'expectedSourceFingerprint':'e'*64} if body is None else body}

    def dispatched(self):return [r for r in self.calls if r.get_method()!='GET']

    def proof(self,request=None):
        request=request or self.request()
        return self.m.RecoveryEvidence(tenant_id='000000',request_sha256=self.m.request_digest(request),
                   entity_id='9223372036854775807',server_number='REQ202610020001',
                   api_sha256='b'*64,db_sha256='c'*64,source_sha256='d'*64,
                   source_fingerprint=request['body']['expectedSourceFingerprint'])

    def test_missing_independent_authority_cannot_send(self):
        for field in ('authority','runtime','lifecycle','recovery','journal'):
            with self.subTest(field=field),self.assertRaises(self.m.TransportBlocked):
                self.build(**{field:None}).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_boolean_permissions_cannot_send(self):
        for field in ('authority','runtime','lifecycle'):
            with self.subTest(field=field),self.assertRaises(self.m.TransportBlocked):
                self.build(**{field:True}).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_void_success_requires_independent_readback(self):
        result=self.client.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual('UNCERTAIN',result['status'])
        self.assertEqual(1,len(self.dispatched()))

    def test_server_number_and_snowflake_id_come_from_readback(self):
        self.recovered=self.proof()
        result=self.client.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual('COMPLETED',result['status'])
        self.assertEqual('9223372036854775807',result['entityId'])
        self.assertEqual('REQ202610020001',result['serverNumber'])

    def test_client_number_and_numeric_id_are_refused_before_dispatch(self):
        for body in ({'requisitionNo':'REQ-GUESSED'}, {'requisitionId':'123'},
                     {'requisitionNo':'','materialId':9223372036854775807},
                     {'requisitionNo':'','qty':0.1}):
            with self.subTest(body=body),self.assertRaises(self.m.TransportBlocked):
                self.client.send(self.request(body=body),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_tenant_override_is_refused(self):
        with self.assertRaises(self.m.TransportBlocked):
            self.client.send(self.request(body={'requisitionNo':'','tenantId':'123456'}),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_api_methods_and_paths_are_allowlisted(self):
        for method,path in (('DELETE','/erp/pu/requisition/123'),('POST','/auth/login'),
                            ('GET','/erp/pu/order/generate/123'),('POST','//example.org'),
                            ('POST','/erp/wm/materialstock'),('POST','/workflow/task/complete')):
            with self.subTest(path=path),self.assertRaises(self.m.TransportBlocked):
                self.client.send(self.request(path=path,method=method),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_no_secrets_payload_or_entity_id_in_durable_audit(self):
        self.recovered=self.proof();self.client.send(self.request(),intent_id='procurement-intent-0001')
        text=self.journal.path.read_text(encoding='utf-8')
        for secret in ('secret-token','test-client','9223372036854775807','REQ202610020001','materialId','requestKey','procurement-intent-0001'):
            self.assertNotIn(secret,text)
        self.assertIn('DISPATCHED',text)
        self.assertIn('COMPLETED',text)

    def test_dispatch_is_fsynced_before_network_write(self):
        previous=self.opener
        def inspect(request,timeout):
            if request.get_method()!='GET':
                self.assertIn('DISPATCHED',self.journal.path.read_text(encoding='utf-8'))
            return previous(request,timeout)
        self.build(opener=inspect).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual(1,len(self.dispatched()))

    def test_uncertain_create_is_not_blindly_resent(self):
        self.client.send(self.request(),intent_id='procurement-intent-0001')
        result=self.client.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual('UNCERTAIN',result['status'])
        self.assertEqual(1,len(self.dispatched()))
        class Denied(self.m.WriteAuthority):
            def require_authorized(self,**kwargs):return None
        with self.assertRaises(self.m.TransportBlocked):
            self.build(authority=Denied()).send(self.request(),intent_id='procurement-intent-0001',retry=True)
        self.assertEqual(1,len(self.dispatched()))

    def test_restart_recovers_an_uncertain_request_without_reposting(self):
        self.client.send(self.request(),intent_id='procurement-intent-0001')
        self.recovered=self.proof()
        restarted=self.build(journal=self.m.AuditJournal(self.journal.path))
        result=restarted.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual('RECOVERED',result['status'])
        self.assertEqual(1,len(self.dispatched()))

    def test_changed_body_for_same_intent_cannot_replace_original_request(self):
        self.client.send(self.request(),intent_id='procurement-intent-0001')
        request=self.request();request['body']['expectedSourceFingerprint']='f'*64
        with self.assertRaises(self.m.TransportBlocked):self.client.send(request,intent_id='procurement-intent-0001')
        self.assertEqual(1,len(self.dispatched()))

    def test_timeout_does_not_retry_or_print_exception_body(self):
        previous=self.opener
        def timeout(request,timeout):
            if request.get_method()=='GET':return previous(request,timeout)
            self.calls.append(request);raise TimeoutError('secret-token private-pwd')
        result=self.build(opener=timeout).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual('UNCERTAIN',result['status'])
        self.assertEqual(1,len(self.dispatched()))
        self.assertNotIn('secret-token',json.dumps(result))

    def test_authentication_failure_prevents_any_write_and_body_echo(self):
        def expired(request,timeout):
            self.calls.append(request);response=io.BytesIO(b'{"code":401,"msg":"secret-token"}');response.status=200;return response
        with self.assertRaises(self.m.TransportBlocked) as caught:
            self.build(opener=expired).send(self.request(),intent_id='procurement-intent-0001')
        self.assertNotIn('secret-token',str(caught.exception));self.assertEqual([],self.dispatched())

    def test_cross_tenant_or_wrong_request_recovery_is_not_accepted(self):
        self.client.send(self.request(),intent_id='procurement-intent-0001')
        for kwargs in ({'tenant_id':'123456'},{'request_sha256':'f'*64}, {'server_number':''},
                       {'entity_id':9223372036854775807},{'db_sha256':''}):
            values={'tenant_id':'000000','request_sha256':self.m.request_digest(self.request()),
                    'entity_id':'9223372036854775807','server_number':'REQ202610020001',
                    'api_sha256':'b'*64,'db_sha256':'c'*64,'source_sha256':'d'*64,'source_fingerprint':'e'*64,**kwargs}
            self.recovered=self.m.RecoveryEvidence(**values)
            with self.subTest(kwargs=kwargs),self.assertRaises(self.m.TransportBlocked):
                self.client.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual(1,len(self.dispatched()))

    def test_journal_hash_chain_tampering_closes_dispatch(self):
        self.client.send(self.request(),intent_id='procurement-intent-0001')
        text=self.journal.path.read_text(encoding='utf-8').replace('UNCERTAIN','COMPLETED')
        self.journal.path.write_text(text,encoding='utf-8')
        request=self.request();request['body']['requestKey']='procurement-intent-0002'
        with self.assertRaises(self.m.TransportBlocked):self.build().send(request,intent_id='procurement-intent-0002')
        self.assertEqual(1,len(self.dispatched()))

    def test_journal_cannot_live_outside_private_evidence_tree(self):
        with self.assertRaises(self.m.TransportBlocked):self.m.AuditJournal(Path(self.temp.name).parent/'other.jsonl')

    def test_only_certified_idempotent_action_can_be_explicitly_retried(self):
        req=self.request()
        self.client.send(req,intent_id='procurement-intent-0001')
        self.client.send(req,intent_id='procurement-intent-0001',retry=True)
        self.assertEqual(2,len(self.dispatched()))

    def test_retry_authority_must_enforce_not_return_a_boolean(self):
        class Bad(self.m.WriteAuthority):
            def require_authorized(self,**kwargs):return None
            def require_retry_safe(self,**kwargs):return True
        req=self.request()
        client=self.build(authority=Bad());client.send(req,intent_id='procurement-intent-0001')
        with self.assertRaises(self.m.TransportBlocked):client.send(req,intent_id='procurement-intent-0001',retry=True)
        self.assertEqual(1,len(self.dispatched()))

    def test_scalar_snowflake_response_does_not_become_binary_float(self):
        d=self.p.decode_document(b'{"code":200,"data":9223372036854775807}')
        self.assertEqual(9223372036854775807,d['data'])
        self.assertIsInstance(d['data'],int)


    def test_certified_command_has_exactly_six_identity_fields(self):
        original=self.request()
        for field in ('qty','quantity','unit','baseUomId','verified','apply','tenantId','requisitionNo'):
            request=self.request();request['body'][field]='unexpected'
            with self.subTest(field=field),self.assertRaises(self.m.TransportBlocked):
                self.client.send(request,intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())
        self.assertEqual(6,len(original['body']))

    def test_only_exact_certified_endpoint_is_enabled_this_round(self):
        for path in ('/erp/pu/requisition','/erp/pu/requisition/certified/mrp',
                     '/erp/pu/requisition/certified/low-stock','/erp/bd/material',
                     '/erp/wm/receive/execute/123','/erp/pu/order'):
            with self.subTest(path=path),self.assertRaises(self.m.TransportBlocked):
                self.client.send(self.request(path=path),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_request_key_is_the_durable_intent_not_an_independent_override(self):
        with self.assertRaises(self.m.TransportBlocked):
            self.client.send(self.request(),intent_id='procurement-intent-0002')
        self.assertEqual([],self.dispatched())

    def test_source_ids_window_and_fingerprint_must_be_complete(self):
        for field,value in (('requestKey','short'),('materialId',1),('vendorId',''),
                            ('requirementDetailIds',[]),('requirementDetailIds',['123','123']),
                            ('expectedSourceFingerprint',None),('windowEnd','2026-02-30')):
            request=self.request();request['body'][field]=value
            with self.subTest(field=field,value=value),self.assertRaises(self.m.TransportBlocked):
                self.client.send(request,intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_legacy_create_cannot_be_retried_even_with_a_permissive_authority(self):
        request={'method':'POST','path':'/erp/pu/requisition','body':{'requisitionNo':''}}
        with self.assertRaises(self.m.TransportBlocked):
            self.client.send(request,intent_id='procurement-intent-0001',retry=True)
        self.assertEqual([],self.dispatched())


    def test_authority_cannot_change_destination_after_authentication(self):
        outer=self
        class Mutating(self.m.WriteAuthority):
            def require_authorized(self,*,transport,scope):
                transport.target=outer.p.RuntimeTarget(base_url='http://remote.example:8080')
                return None
        with self.assertRaises(self.m.TransportBlocked):
            self.build(authority=Mutating()).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_authority_cannot_swap_credential_or_tenant_after_authentication(self):
        outer=self
        class Mutating(self.m.WriteAuthority):
            def require_authorized(self,*,transport,scope):
                transport.credentials=outer.p.Credentials(token='other-secret',client_id='client',tenant_id='123456')
                return None
        with self.assertRaises(self.m.TransportBlocked):
            self.build(authority=Mutating()).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_nested_lock_denial_preserves_first_lock_owner(self):
        scope=self.m.WriteScope('000000','procurement-intent-0001','a'*64,'b'*64,'c'*64,
                               'd'*40,'requisition.certified.create',False)
        with self.journal.transaction():
            with self.assertRaises(self.m.TransportBlocked):
                with self.journal.transaction():pass
            self.journal.append(scope,'DISPATCHED')
        self.assertEqual(1,len(self.journal.load()))

    def test_independent_recovery_must_match_frozen_source_fingerprint(self):
        from dataclasses import fields
        self.assertIn('source_fingerprint',{f.name for f in fields(self.m.RecoveryEvidence)})
        self.client.send(self.request(),intent_id='procurement-intent-0001')
        self.recovered=self.m.RecoveryEvidence(tenant_id='000000',request_sha256=self.m.request_digest(self.request()),
                 entity_id='9223372036854775807',server_number='REQ202610020001',api_sha256='b'*64,
                 db_sha256='c'*64,source_sha256='d'*64,source_fingerprint='f'*64)
        with self.assertRaises(self.m.TransportBlocked):
            self.client.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual(1,len(self.dispatched()))

    def test_lifecycle_scope_contains_frozen_source_identities_not_quantity_flags(self):
        from dataclasses import fields
        self.assertIn('command',{f.name for f in fields(self.m.WriteScope)})
        seen=[]
        class Lifecycle(self.m.LifecycleAuthority):
            def require_closed(self,*,scope):seen.append(scope.command);return None
        self.build(lifecycle=Lifecycle()).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual('9223372036854775807',seen[0].material_id)
        self.assertEqual(('123456789123456788',),seen[0].requirement_detail_ids)
        self.assertFalse(hasattr(seen[0],'quantity'))

    def test_read_result_snowflake_ids_are_kept_as_strings(self):
        def response(request,timeout):
            data=io.BytesIO(b'{"code":200,"data":{"materialId":9223372036854775807,"qty":0.123456789123}}')
            data.status=200;return data
        result=self.build(opener=response).get('/erp/bd/material/123')
        self.assertEqual('9223372036854775807',result['data']['materialId'])
        self.assertEqual('0.123456789123',str(result['data']['qty']))

    def test_read_id_encoded_as_float_is_not_accepted_as_a_native_snowflake(self):
        def response(request,timeout):
            data=io.BytesIO(b'{"code":200,"data":{"materialId":9.223372036854776e18}}')
            data.status=200;return data
        with self.assertRaises(self.m.TransportBlocked):
            self.build(opener=response).get('/erp/bd/material/123')

    def test_boolean_results_from_enforcing_gates_are_not_permission(self):
        class Runtime(self.p.RuntimeAuthority):
            def require_current(self,**kwargs):return True
        class Lifecycle(self.m.LifecycleAuthority):
            def require_closed(self,**kwargs):return True
        for field,value in (('runtime',Runtime()),('lifecycle',Lifecycle())):
            with self.subTest(field=field),self.assertRaises(self.m.TransportBlocked):
                self.build(**{field:value}).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual([],self.dispatched())



    def test_omitted_runtime_uses_concrete_authority_but_does_not_issue_write_permission(self):
        from procurement_runtime_attestation import BackendRuntimeAuthority
        client=self.m.FormalProcurementTransport(credentials=self.p.Credentials(token='secret-token',
                client_id='test-client',tenant_id='000000'),expected_head='a'*40,opener=self.opener)
        self.assertIsInstance(client.runtime,BackendRuntimeAuthority)
        self.assertEqual([],self.calls,'constructor must not perform reads or writes')
        with self.assertRaises(self.m.TransportBlocked):client.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual([],self.calls,'default runtime is not a business-write/lifecycle grant')

    def test_live_runtime_rejection_preserves_only_a_fixed_nonsecret_category(self):
        outer=self
        class Runtime(self.p.RuntimeAuthority):
            def require_current(self,**kwargs):
                raise outer.p.PreflightBlocked('secret-token database-password',category='ATTESTATION_ENDPOINT_MISSING')
        with self.assertRaises(self.m.TransportBlocked) as caught:
            self.build(runtime=Runtime()).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual('ATTESTATION_ENDPOINT_MISSING',getattr(caught.exception,'category',None))
        self.assertNotIn('secret-token',str(caught.exception));self.assertEqual([],self.dispatched())

    def test_runtime_is_rechecked_after_source_authorities_immediately_before_dispatch(self):
        outer=self
        class Runtime(self.p.RuntimeAuthority):
            def __init__(self):self.calls=0
            def require_current(self,**kwargs):
                self.calls+=1
                if self.calls>1:raise outer.p.PreflightBlocked('fixture restart',category='RUNTIME_RESTARTED')
        runtime=Runtime()
        with self.assertRaises(self.m.TransportBlocked):
            self.build(runtime=runtime).send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual(2,runtime.calls);self.assertEqual([],self.dispatched())
        self.assertEqual([],self.journal.load(),'never journal DISPATCHED when final runtime check fails')



    def test_untrusted_runtime_error_category_cannot_leak_or_escape_as_an_unhashable_exception(self):
        outer=self
        for category in ('secret-token',['secret-token'],{'secret-token':True}):
            class Runtime(self.p.RuntimeAuthority):
                def require_current(self,**kwargs):
                    raise outer.p.PreflightBlocked('secret-token',category=category)
            try:self.build(runtime=Runtime()).send(self.request(),intent_id='procurement-intent-0001')
            except self.m.TransportBlocked as error:
                self.assertEqual('BUILD_ATTESTATION_REJECTED',error.category);self.assertNotIn('secret-token',str(error))
            except Exception:self.fail('runtime error category must remain a fixed, redacted TransportBlocked')
            else:self.fail('an invalid runtime category must not authorize a dispatch')
        self.assertEqual([],self.dispatched())


if __name__=='__main__':unittest.main()
