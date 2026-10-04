"""任务 C 的传输契约测试；注入的服务严格保持离线。"""
import importlib
import importlib.util
import io
import inspect
import json
from dataclasses import replace
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
        self.readbacks=[]
        outer=self
        class Authority(self.m.WriteAuthority):
            def require_authorized(self,*,transport,scope):return None
            def require_retry_safe(self,*,transport,scope):return None
        class Lifecycle(self.m.LifecycleAuthority):
            def require_closed(self,*,scope):return None
        class Runtime(self.p.RuntimeAuthority):
            def require_current(self,**kwargs):return None
        class Recovery(self.m.RecoveryReader):
            def read_back(self,*,scope,request):
                outer.readbacks.append((scope,request));return outer.recovered
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

    def receipt_request(self, step='shelf', receive_id='9223372036854775806'):
        return {'method':'POST','path':f'/erp/wm/receive/certified/{step}/{receive_id}','body':None}

    def test_certified_shelf_dispatches_only_its_state_transition(self):
        try:
            result=self.client.send(self.receipt_request(),intent_id='receipt-shelf-intent-0001')
        except self.m.TransportBlocked:
            self.fail('受控上架入口缺失，不能退回旧 CRUD 上架')
        self.assertEqual('UNCERTAIN',result['status'])
        self.assertEqual(['/erp/wm/receive/certified/shelf/9223372036854775806'],
                         [r.selector for r in self.dispatched()])
        self.assertIsNone(self.dispatched()[0].data)
        self.assertEqual('receipt.certified.shelf',self.journal.load()[0]['action'])

    def test_certified_execute_is_a_separate_original_intent(self):
        seen=[]
        class Lifecycle(self.m.LifecycleAuthority):
            def require_closed(self,*,scope):seen.append(scope);return None
        client=self.build(lifecycle=Lifecycle())
        try:
            client.send(self.receipt_request(),intent_id='receipt-shelf-intent-0001')
            client.send(self.receipt_request('execute'),intent_id='receipt-post-intent-0001')
        except self.m.TransportBlocked:
            self.fail('实际过账入口尚未与受控上架分别续接')
        self.assertEqual(['/erp/wm/receive/certified/shelf/9223372036854775806',
                          '/erp/wm/receive/certified/execute/9223372036854775806'],
                         [r.selector for r in self.dispatched()])
        self.assertEqual({'receipt.certified.shelf','receipt.certified.post'},{s.action for s in seen})
        self.assertTrue(all(s.command.receive_id=='9223372036854775806' for s in seen))


    def receipt_proof(self, request=None, *, stage='SHELVED', notification=None, lifecycle=None, **changes):
        """仅为离线服务读取器提供替身，不生成或发送真实出生事实。"""
        evidence=getattr(self.m,'ReceiptRecoveryEvidence',None)
        self.assertIsNotNone(evidence,'缺少原生出生、V2 与永久过账分层读回契约')
        request=request or self.receipt_request()
        values={'tenant_id':'000000','request_sha256':self.m.request_digest(request),
                'entity_id':request['path'].rsplit('/',1)[1],'server_number':'RCV202610030001',
                'api_sha256':'a'*64,'db_sha256':'b'*64,'source_sha256':'c'*64,'source_fingerprint':'d'*64,
                'material_id':'9223372036854775807','inspection_id':'123456789123456788',
                'source_kind':'MES_QC_IQC_RECEIVE_MATERIAL_V2','birth_sha256':'e'*64,
                'binding_sha256':'f'*64,'iqc_sha256':'1'*64,'stage':stage,
                'posting_sha256':'2'*64 if stage=='FINISHED' else None,
                'notification_status':notification,'lifecycle_sha256':lifecycle,**changes}
        return evidence(**values)

    def test_shelf_completes_only_after_native_birth_and_v2_readback(self):
        request=self.receipt_request()
        previous=self.opener
        def shelved(http,timeout):
            if http.get_method()=='POST':self.recovered=self.receipt_proof(request)
            return previous(http,timeout)
        result=self.build(opener=shelved).send(request,intent_id='receipt-shelf-intent-0001')
        self.assertEqual('COMPLETED',result['status'])
        self.assertEqual('RCV202610030001',result['serverNumber'])
        self.assertEqual(1,len(self.dispatched()))
        self.assertEqual('COMPLETED',self.journal.load()[-1]['event'])

    def test_restart_shelf_reads_native_permanent_result_without_reposting(self):
        request=self.receipt_request()
        self.client.send(request,intent_id='receipt-shelf-intent-0001')
        self.recovered=self.receipt_proof(request)
        restarted=self.build(journal=self.m.AuditJournal(self.journal.path))
        result=restarted.send(request,intent_id='receipt-shelf-intent-0001')
        self.assertEqual('RECOVERED',result['status'])
        self.assertEqual(1,len(self.dispatched()))
        self.assertEqual(request,self.readbacks[-1][1])

    def test_terminal_shelf_restores_posted_origin_without_calling_execute(self):
        request=self.receipt_request()
        self.client.send(request,intent_id='receipt-shelf-intent-0001')
        self.recovered=self.receipt_proof(request,stage='FINISHED',notification='PENDING')
        result=self.build().send(request,intent_id='receipt-shelf-intent-0001')
        self.assertEqual('RECOVERED',result['status'])
        self.assertEqual(['POST'],[r.get_method() for r in self.dispatched()])
        self.assertTrue(all('/shelf/' in r.selector for r in self.dispatched()))

    def test_duplicate_receipt_without_local_history_uses_native_readback_not_a_write(self):
        request=self.receipt_request('execute')
        self.recovered=self.receipt_proof(request,stage='FINISHED',notification='ENQUEUED',lifecycle='3'*64)
        result=self.client.send(request,intent_id='receipt-post-intent-0001')
        self.assertEqual('RECOVERED',result['status'])
        self.assertEqual([],self.dispatched())
        self.assertEqual('RECOVERED',self.journal.load()[-1]['event'])

    def test_execute_pending_notification_stays_uncertain(self):
        request=self.receipt_request('execute')
        previous=self.opener
        def pending(http,timeout):
            if http.get_method()=='POST':
                self.recovered=self.receipt_proof(request,stage='FINISHED',notification='PENDING')
            return previous(http,timeout)
        result=self.build(opener=pending).send(request,intent_id='receipt-post-intent-0001')
        self.assertEqual('UNCERTAIN',result['status'])
        self.assertEqual('await_receipt_lifecycle_readback',result['reason'])
        self.assertEqual(['DISPATCHED','UNCERTAIN'],[r['event'] for r in self.journal.load()])
        self.assertNotEqual('0'*64,self.journal.load()[-1]['resultSha256'])
        again=self.build().send(request,intent_id='receipt-post-intent-0001')
        self.assertEqual('UNCERTAIN',again['status'])
        self.assertEqual(1,len(self.dispatched()))

    def test_explicit_pending_execute_retry_requires_proof_and_replays_only_original_service(self):
        request=self.receipt_request('execute')
        previous=self.opener
        retries=[]
        class Authority(self.m.WriteAuthority):
            def require_authorized(self,**kwargs):return None
            def require_retry_safe(self,*,transport,scope):retries.append(scope);return None
        def service(http,timeout):
            if http.get_method()=='POST':
                enqueued=bool(self.dispatched())
                self.recovered=self.receipt_proof(request,stage='FINISHED',
                    notification='ENQUEUED' if enqueued else 'PENDING',lifecycle='3'*64 if enqueued else None)
            return previous(http,timeout)
        client=self.build(authority=Authority(),opener=service)
        self.assertEqual('UNCERTAIN',client.send(request,intent_id='receipt-post-intent-0001')['status'])
        restarted=self.build(authority=client.authority,opener=service,journal=self.m.AuditJournal(self.journal.path))
        self.assertEqual('COMPLETED',restarted.send(request,intent_id='receipt-post-intent-0001',retry=True)['status'])
        self.assertEqual(1,len(retries))
        self.assertEqual([request['path'],request['path']],[r.selector for r in self.dispatched()])
        self.assertTrue(all(r.data is None for r in self.dispatched()))
        self.assertEqual('RECOVERED',restarted.send(request,intent_id='receipt-post-intent-0001')['status'])
        self.assertEqual(2,len(self.dispatched()))

    def test_enqueued_without_consumed_receipt_lifecycle_is_not_completion(self):
        request=self.receipt_request('execute')
        self.client.send(request,intent_id='receipt-post-intent-0001')
        self.recovered=self.receipt_proof(request,stage='FINISHED',notification='ENQUEUED')
        result=self.client.send(request,intent_id='receipt-post-intent-0001')
        self.assertEqual('UNCERTAIN',result['status'])
        self.assertEqual(1,len(self.dispatched()))
        self.assertNotIn('RECOVERED',[r['event'] for r in self.journal.load()])

    def test_receipt_recovery_rejects_historical_or_incomplete_native_evidence(self):
        request=self.receipt_request('execute')
        self.client.send(request,intent_id='receipt-post-intent-0001')
        valid=self.receipt_proof(request,stage='FINISHED',notification='ENQUEUED',lifecycle='3'*64)
        for changes in ({'source_kind':'MES_QC_IQC_RECEIVE_MATERIAL_V1'},{'birth_sha256':''},
                        {'binding_sha256':None},{'iqc_sha256':''},{'source_fingerprint':None},
                        {'posting_sha256':None},{'notification_status':'DONE'},
                        {'notification_status':'ENQUEUED','lifecycle_sha256':'verified'}):
            self.recovered=replace(valid,**changes)
            with self.subTest(changes=changes),self.assertRaises(self.m.TransportBlocked):
                self.client.send(request,intent_id='receipt-post-intent-0001',retry=True)
        self.assertEqual(1,len(self.dispatched()))

    def test_receipt_recovery_must_match_native_identity_and_original_request(self):
        request=self.receipt_request()
        self.client.send(request,intent_id='receipt-shelf-intent-0001')
        valid=self.receipt_proof(request)
        for changes in ({'tenant_id':'123456'},{'request_sha256':'0'*64},{'entity_id':'123'},
                        {'material_id':9223372036854775807},{'inspection_id':1},{'server_number':''}):
            self.recovered=replace(valid,**changes)
            with self.subTest(changes=changes),self.assertRaises(self.m.TransportBlocked):
                self.client.send(request,intent_id='receipt-shelf-intent-0001')
        self.assertEqual(1,len(self.dispatched()))

    def test_shelf_evidence_cannot_be_projected_as_a_posted_receipt(self):
        request=self.receipt_request('execute')
        self.recovered=self.receipt_proof(request)
        with self.assertRaises(self.m.TransportBlocked):
            self.client.send(request,intent_id='receipt-post-intent-0001')
        self.assertEqual([],self.dispatched())

    def test_bare_cache_status_or_generic_proof_cannot_restore_native_receipt(self):
        request=self.receipt_request()
        self.client.send(request,intent_id='receipt-shelf-intent-0001')
        generic=replace(self.proof(),request_sha256=self.m.request_digest(request),entity_id='9223372036854775806')
        for proof in ({'status':'SHELVED','verified':True},{'status':'DONE','birthSha256':'e'*64},generic):
            self.recovered=proof
            with self.subTest(proof=type(proof).__name__),self.assertRaises(self.m.TransportBlocked):
                self.client.send(request,intent_id='receipt-shelf-intent-0001')
        self.assertEqual(1,len(self.dispatched()))

    def test_completed_receipt_cannot_be_retried_after_readback_disappears_or_regresses(self):
        request=self.receipt_request('execute')
        valid=self.receipt_proof(request,stage='FINISHED',notification='ENQUEUED',lifecycle='3'*64)
        self.recovered=valid
        self.assertEqual('RECOVERED',self.client.send(request,intent_id='receipt-post-intent-0001')['status'])
        for proof in (None,replace(valid,notification_status='PENDING',lifecycle_sha256=None)):
            self.recovered=proof
            with self.subTest(proof=proof is None),self.assertRaises(self.m.TransportBlocked):
                self.client.send(request,intent_id='receipt-post-intent-0001',retry=True)
        self.assertEqual([],self.dispatched())

    def test_changed_receipt_path_cannot_replace_the_original_durable_intent(self):
        self.client.send(self.receipt_request(),intent_id='receipt-shelf-intent-0001')
        for request in (self.receipt_request('execute'),self.receipt_request(receive_id='123')):
            with self.subTest(path=request['path']),self.assertRaises(self.m.TransportBlocked):
                self.client.send(request,intent_id='receipt-shelf-intent-0001',retry=True)
        self.assertEqual(1,len(self.dispatched()))

    def test_receipt_requests_refuse_body_flags_legacy_methods_and_non_native_ids(self):
        for step in ('shelf','execute'):
            original=self.receipt_request(step)
            for field in ('quantity','baseUomId','verified','birth','status','tenantId'):
                request={**original,'body':{field:'unexpected'}}
                with self.subTest(step=step,field=field),self.assertRaises(self.m.TransportBlocked):
                    self.client.send(request,intent_id='receipt-original-intent')
            for changes in ({'method':'PUT'},{'query':{'verified':True}},
                            {'path':f'/erp/wm/receive/{step}/123'},
                            {'path':f'/erp/wm/receive/certified/{step}/9223372036854775808'},
                            {'path':f'/erp/wm/receive/certified/{step}/00123'}):
                with self.subTest(changes=changes),self.assertRaises(self.m.TransportBlocked):
                    self.client.send({**original,**changes},intent_id='receipt-original-intent')
        self.assertEqual([],self.calls)

    def test_permanent_receipt_recovery_get_has_no_business_dispatch(self):
        path='/erp/wm/receive/certified/recovery/9223372036854775806'
        try:self.client.get(path)
        except self.m.TransportBlocked:self.fail('永久收货恢复GET尚未接入只读路由')
        self.assertEqual(1,len(self.calls))
        self.assertEqual('GET',self.calls[0].get_method())
        self.assertTrue(self.calls[0].full_url.endswith(path))
        self.assertEqual([],self.dispatched())
        self.assertEqual([],self.journal.load())

    def test_permanent_recovery_identity_is_not_overridden_by_query_or_unsafe_id(self):
        path='/erp/wm/receive/certified/recovery/'
        for request_path, query in ((path+'9223372036854775806',{'receiveId':'1'}),
                                    (path+'9223372036854775808',None), (path+'01',None)):
            with self.subTest(path=request_path),self.assertRaises(self.m.TransportBlocked):
                self.client.get(request_path,query)
        self.assertEqual([],self.calls)

    def test_native_iqc_gets_are_read_only_without_a_crud_birth_fallback(self):
        for path,query in (('/mes/qc/iqc/list',{'receiveId':'9223372036854775806','materialId':'9223372036854775807'}),
                           ('/mes/qc/iqc/123456789123456788',None),
                           ('/mes/qc/iqc/123456789123456788/details',None)):
            try:self.client.get(path,query)
            except self.m.TransportBlocked:self.fail('正式 transport 缺少真实 IQC 本体和明细只读取证入口')
        request={'method':'POST','path':'/mes/qc/iqc',
                 'body':{'receiveId':'9223372036854775806','materialId':'9223372036854775807','iqcNo':'CLIENT-NUMBER'}}
        with self.assertRaises(self.m.TransportBlocked):self.client.send(request,intent_id='iqc-original-intent')
        self.assertEqual([],self.dispatched())
        self.assertEqual(3,len(self.calls))

    def test_receipt_retry_still_requires_the_original_private_dispatch(self):
        with self.assertRaises(self.m.TransportBlocked):
            self.client.send(self.receipt_request('execute'),intent_id='receipt-post-intent-0001',retry=True)
        self.assertEqual([],self.dispatched())

    def test_missing_loading_proof_blocks_both_receipt_steps_and_terminal_recovery(self):
        outer=self
        class Runtime(self.p.RuntimeAuthority):
            def require_current(self,**kwargs):
                raise outer.p.PreflightBlocked('替身没有真实加载证明',category='ATTESTATION_ENDPOINT_MISSING')
        for step in ('shelf','execute'):
            with self.subTest(step=step),self.assertRaises(self.m.TransportBlocked):
                self.build(runtime=Runtime()).send(self.receipt_request(step),intent_id=f'receipt-{step}-intent-0001')
        request=self.receipt_request('execute')
        self.recovered=self.receipt_proof(request,stage='FINISHED',notification='ENQUEUED',lifecycle='3'*64)
        with self.assertRaises(self.m.TransportBlocked):
            self.build(runtime=Runtime()).send(request,intent_id='receipt-post-intent-0001')
        self.assertEqual([],self.dispatched())
        self.assertEqual([],self.journal.load())

    def test_receipt_http_terminal_flags_do_not_substitute_for_native_readback(self):
        previous=self.opener
        def bare(http,timeout):
            if http.get_method()=='GET':return previous(http,timeout)
            self.calls.append(http)
            response=io.BytesIO(b'{"code":200,"data":{"status":"FINISHED","verified":true,"notificationPending":false}}')
            response.status=200;return response
        result=self.build(opener=bare).send(self.receipt_request('execute'),intent_id='receipt-post-intent-0001')
        self.assertEqual('UNCERTAIN',result['status'])
        self.assertEqual(1,len(self.dispatched()))
        self.assertEqual('UNCERTAIN',self.journal.load()[-1]['event'])

    def test_pending_receipt_retry_denies_missing_or_boolean_retry_authority(self):
        request=self.receipt_request('execute')
        self.client.send(request,intent_id='receipt-post-intent-0001')
        self.recovered=self.receipt_proof(request,stage='FINISHED',notification='PENDING')
        class Denied(self.m.WriteAuthority):
            def require_authorized(self,**kwargs):return None
        class Boolean(Denied):
            def require_retry_safe(self,**kwargs):return True
        for authority in (Denied(),Boolean()):
            with self.subTest(authority=type(authority).__name__),self.assertRaises(self.m.TransportBlocked):
                self.build(authority=authority).send(request,intent_id='receipt-post-intent-0001',retry=True)
        self.assertEqual(1,len(self.dispatched()))

    def test_terminal_receipt_readback_cannot_outlive_the_loading_proof(self):
        request=self.receipt_request('execute')
        self.recovered=self.receipt_proof(request,stage='FINISHED',notification='ENQUEUED',lifecycle='3'*64)
        outer=self
        class Runtime(self.p.RuntimeAuthority):
            def __init__(self):self.calls=0
            def require_current(self,**kwargs):
                self.calls+=1
                if self.calls>1:raise outer.p.PreflightBlocked('替身证明失效',category='RUNTIME_RESTARTED')
        runtime=Runtime()
        with self.assertRaises(self.m.TransportBlocked):
            self.build(runtime=runtime).send(request,intent_id='receipt-post-intent-0001')
        self.assertEqual(2,runtime.calls)
        self.assertEqual([],self.dispatched())
        self.assertEqual([],self.journal.load())

    def test_native_receipt_read_failure_is_not_absence_or_permission_to_dispatch(self):
        outer=self
        class Recovery(self.m.RecoveryReader):
            def read_back(self,**kwargs):raise outer.m.TransportBlocked('UNIT_ORIGIN_UNPROVEN secret-token')
        for step in ('shelf','execute'):
            with self.subTest(step=step),self.assertRaises(self.m.TransportBlocked) as caught:
                self.build(recovery=Recovery()).send(self.receipt_request(step),intent_id=f'receipt-{step}-intent-0001')
            self.assertNotIn('secret-token',str(caught.exception))
        self.assertEqual([],self.dispatched())
        self.assertEqual([],self.journal.load())

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



    def build_with_runtime_mode(self,mode,**overrides):
        # 先断言选型接口存在，RED 明确报告缺少接线而非意外的 TypeError。
        self.assertIn('runtime_check_mode',inspect.signature(self.m.FormalProcurementTransport).parameters,
                      '真正执行客户端必须提供本地/严格运行检查选型')
        return self.build(**{'runtime':self.m._DEFAULT_RUNTIME,'runtime_check_mode':mode,**overrides})

    def test_omitted_runtime_uses_local_authority_but_does_not_issue_write_permission(self):
        client=self.m.FormalProcurementTransport(credentials=self.p.Credentials(token='secret-token',
                client_id='test-client',tenant_id='000000'),expected_head='a'*40,opener=self.opener)
        self.assertEqual('LocalDevelopmentRuntimeAuthority',type(client.runtime).__name__)
        local=importlib.import_module('procurement_local_runtime')
        self.assertIsInstance(client.runtime,local.LocalDevelopmentRuntimeAuthority)
        self.assertIsInstance(client.runtime,self.p.RuntimeAuthority)
        self.assertEqual([],self.calls,'构造不能认证、连接数据库或发送业务请求')
        with self.assertRaises(self.m.TransportBlocked):client.send(self.request(),intent_id='procurement-intent-0001')
        self.assertEqual([],self.calls,'本地运行检查不是业务授权或生命周期许可')

    def test_omitted_runtime_factory_binds_existing_credentials_and_opener(self):
        with patch.object(self.m,'create_runtime_authority',return_value=self.runtime,create=True) as factory:
            client=self.build(runtime=self.m._DEFAULT_RUNTIME)
        factory.assert_called_once_with(mode='local-dev',credentials=client.credentials,opener=self.opener)
        self.assertIs(client.runtime,self.runtime)
        self.assertEqual([],self.calls)

    def test_explicit_local_mode_selects_the_same_default_authority(self):
        client=self.build_with_runtime_mode('local-dev')
        local=importlib.import_module('procurement_local_runtime')
        self.assertIsInstance(client.runtime,local.LocalDevelopmentRuntimeAuthority)
        self.assertEqual([],self.calls)

    def test_explicit_strict_mode_retains_backend_attestation_without_io(self):
        from procurement_runtime_attestation import BackendRuntimeAuthority
        client=self.build_with_runtime_mode('strict')
        self.assertIsInstance(client.runtime,BackendRuntimeAuthority)
        self.assertFalse(client.runtime.evidence()['businessWriteAuthorized'])
        self.assertEqual([],self.calls,'strict 仍只在校验时读取，不在构造时访问服务')

    def test_unsupported_runtime_mode_is_fixed_redacted_and_never_dispatches(self):
        with self.assertRaises(self.m.TransportBlocked) as caught:
            self.build_with_runtime_mode('secret-token-unsupported-mode')
        self.assertEqual('RUNTIME_CHECK_MODE_UNSUPPORTED',caught.exception.category)
        self.assertNotIn('secret-token',str(caught.exception))
        self.assertEqual([],self.calls)
        self.assertEqual([],self.journal.load())

    def test_runtime_mode_is_forwarded_to_factory_with_the_bound_io(self):
        for mode in ('local-dev','strict'):
            with self.subTest(mode=mode),patch.object(self.m,'create_runtime_authority',
                    return_value=self.runtime,create=True) as factory:
                client=self.build_with_runtime_mode(mode)
                factory.assert_called_once_with(mode=mode,credentials=client.credentials,opener=self.opener)
                self.assertIs(client.runtime,self.runtime)
        self.assertEqual([],self.calls)

    def test_explicit_none_runtime_is_not_replaced_by_factory_or_mode(self):
        for mode in ('local-dev','strict'):
            with self.subTest(mode=mode),patch.object(self.m,'create_runtime_authority',create=True) as factory:
                client=self.build_with_runtime_mode(mode,runtime=None)
                self.assertIsNone(client.runtime)
                with self.assertRaises(self.m.TransportBlocked):
                    client.send(self.request(),intent_id='procurement-intent-0001')
                factory.assert_not_called()
        self.assertEqual([],self.calls)
        self.assertEqual([],self.journal.load())

    def test_explicit_runtime_authority_is_not_replaced_by_mode(self):
        for mode in ('local-dev','strict'):
            with self.subTest(mode=mode),patch.object(self.m,'create_runtime_authority',create=True) as factory:
                client=self.build_with_runtime_mode(mode,runtime=self.runtime)
                self.assertIs(client.runtime,self.runtime)
                factory.assert_not_called()
        self.assertEqual([],self.calls)

    def test_default_runtime_cannot_supply_missing_or_boolean_business_gates(self):
        # 隔离运行检查使其成功，反例专门验证真正的业务门禁依然拒绝。
        for field in ('authority','lifecycle','recovery','journal'):
            for value in (None,True):
                with self.subTest(field=field,value=value),patch.object(self.m,'create_runtime_authority',
                        return_value=self.runtime,create=True) as factory:
                    client=self.build(runtime=self.m._DEFAULT_RUNTIME,**{field:value})
                    with self.assertRaises(self.m.TransportBlocked):
                        client.send(self.request(),intent_id='procurement-intent-0001')
                    factory.assert_called_once()
        self.assertEqual([],self.calls)
        self.assertEqual([],self.journal.load())

    def test_default_runtime_does_not_bypass_source_lifecycle_rejection(self):
        class Lifecycle(self.m.LifecycleAuthority):
            def require_closed(self,*,scope):raise RuntimeError('secret-token missing business source')
        with patch.object(self.m,'create_runtime_authority',return_value=self.runtime,create=True) as factory:
            client=self.build(runtime=self.m._DEFAULT_RUNTIME,lifecycle=Lifecycle())
            with self.assertRaises(self.m.TransportBlocked) as caught:
                client.send(self.request(),intent_id='procurement-intent-0001')
            factory.assert_called_once()
        self.assertNotIn('secret-token',str(caught.exception))
        self.assertEqual([],self.dispatched())
        self.assertEqual([],self.journal.load())

    def test_factory_error_category_is_redacted_and_not_arbitrary_runtime_text(self):
        for category in ('RUNTIME_SECRET_TOKEN',['secret-token'],{'secret-token':True}):
            error=self.p.PreflightBlocked('secret-token database-password',category=category)
            with self.subTest(category=category):
                with patch.object(self.m,'create_runtime_authority',side_effect=error,create=True):
                    with self.assertRaises(self.m.TransportBlocked) as caught:
                        self.build_with_runtime_mode('local-dev')
                self.assertEqual('BUILD_ATTESTATION_REJECTED',caught.exception.category)
                self.assertNotIn('secret-token',str(caught.exception))
        self.assertEqual([],self.calls)

    def test_local_runtime_rejection_preserves_only_its_fixed_nonsecret_category(self):
        outer=self
        self.assertIn('RUNTIME_CHECK_MODE_UNSUPPORTED',self.m.LOCAL_RUNTIME_CATEGORIES)
        # 穷举主线程公开的本地固定类别；不能按任意 RUNTIME_ 前缀放行错误文本。
        for category in sorted(self.m.LOCAL_RUNTIME_CATEGORIES):
            class Runtime(self.p.RuntimeAuthority):
                def require_current(self,**kwargs):
                    raise outer.p.PreflightBlocked('secret-token database-password',category=category)
            with self.subTest(category=category):
                with self.assertRaises(self.m.TransportBlocked) as caught:
                    self.build(runtime=Runtime()).send(self.request(),intent_id='procurement-intent-0001')
                self.assertEqual(category,caught.exception.category)
                self.assertNotIn('secret-token',str(caught.exception))
        self.assertEqual([],self.dispatched())
        self.assertEqual([],self.journal.load())

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
        for category in ('secret-token','RUNTIME_SECRET_TOKEN',['secret-token'],{'secret-token':True}):
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
