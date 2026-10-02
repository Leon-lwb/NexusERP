"""Task C: offline tests. No local services or shared Maven are touched."""
import importlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from decimal import Decimal


class RuntimePreflightTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('procurement_runtime_preflight'),
                             'Task C preflight implementation is missing')
        self.m = importlib.import_module('procurement_runtime_preflight')

    def credentials(self):
        return self.m.Credentials(token='private-test-token', client_id='test-client', tenant_id='000000')

    def test_loopback_dev3306_and8080_are_the_only_target(self):
        self.m.RuntimeTarget().validate()
        for args in ({'base_url':'https://127.0.0.1:8080'}, {'base_url':'http://example.org:8080'},
                     {'base_url':'http://127.0.0.1:8081'}, {'base_url':'http://x@127.0.0.1:8080'},
                     {'base_url':'http://127.0.0.1:8080?x=1'}, {'db_port':3307}, {'profile':'prod'},
                     {'db_host':'192.168.1.1'}, {'profile':'decision-demo'}):
            with self.subTest(args=args), self.assertRaises(self.m.PreflightBlocked):
                self.m.RuntimeTarget(**args).validate()

    def test_credentials_repr_and_evidence_never_contain_secrets(self):
        c = self.credentials()
        self.assertNotIn(c.token, repr(c))
        self.assertNotIn(c.client_id, repr(c))
        self.assertNotIn(c.token, json.dumps(c.evidence()))
        self.assertTrue(c.evidence()['credentialPresent'])
        self.assertEqual(64, len(c.evidence()['credentialSha256']))

    def test_partial_environment_credentials_are_not_merged_with_a_file(self):
        with self.assertRaises(self.m.PreflightBlocked):
            self.m.load_credentials(environ={'NEXUS_FULL_FLOW_TOKEN':'secret'})

    def test_full_environment_context_requires_explicit_tenant(self):
        env = {'NEXUS_FULL_FLOW_TOKEN':'test', 'NEXUS_FULL_FLOW_CLIENT_ID':'client',
               'NEXUS_FULL_FLOW_TENANT_ID':'000000'}
        c = self.m.load_credentials(environ=env)
        self.assertEqual('000000', c.tenant_id)

    def test_cookie_context_is_supported_but_token_cookie_mix_is_refused(self):
        c = self.m.Credentials(cookie='session=test-value', client_id='client', tenant_id='000000')
        self.assertEqual('session=test-value', c.headers()['Cookie'])
        with self.assertRaises(self.m.PreflightBlocked):
            self.m.Credentials(token='secret', cookie='session=secret', client_id='client',
                               tenant_id='000000').validate()

    def test_auth_headers_cannot_inject_newlines(self):
        for kwargs in ({'token':'x\r\nx'}, {'client_id':'x\nx'}, {'cookie':'session=x\nx','token':''}):
            args={'token':'test','client_id':'client','tenant_id':'000000',**kwargs}
            with self.subTest(kwargs=kwargs), self.assertRaises(self.m.PreflightBlocked):
                self.m.Credentials(**args).validate()

    def test_snowflake_ids_are_strings_and_binary_floats_are_refused(self):
        self.assertEqual('9223372036854775807',self.m.strict_id('9223372036854775807'))
        for value in (9223372036854775807,1,True,1.0,'1e18','-1','0'):
            with self.subTest(value=value), self.assertRaises(self.m.PreflightBlocked):
                self.m.strict_id(value)
        self.assertEqual('0.123456789123456789', str(self.m.exact_decimal('0.123456789123456789')))
        for value in (0.1, True, 'NaN', 'Infinity', '1e3'):
            with self.subTest(value=value), self.assertRaises(self.m.PreflightBlocked):
                self.m.exact_decimal(value)

    def test_json_numbers_are_parsed_without_binary_float(self):
        d=self.m.decode_document(b'{"code":200,"data":{"materialId":"9223372036854775807","qty":0.123456789123456789}}')
        self.assertEqual('9223372036854775807',d['data']['materialId'])
        self.assertEqual(Decimal('0.123456789123456789'),d['data']['qty'])
        for raw in (b'{"code":200,"code":401}', b'{"q":NaN}',b'[]'):
            with self.subTest(raw=raw), self.assertRaises(self.m.PreflightBlocked):
                self.m.decode_document(raw)

    def test_preflight_http_cannot_call_a_get_that_creates_records(self):
        calls=[]
        client=self.m.ReadOnlyClient(self.m.RuntimeTarget(),self.credentials(),opener=lambda *a,**k:calls.append(a))
        for path in ('/erp/pu/order/generate/123','/auth/login','//example.org',
                     '/system/user/../getInfo','/system/user/getInfo?token=secret'):
            with self.subTest(path=path),self.assertRaises(self.m.PreflightBlocked):client.get(path)
        self.assertEqual([],calls)

    def test_expired_session_reports_only_business_code_not_backend_message(self):
        class Client:
            def get(self,path):return {'code':401,'msg':'token expired private-test-token'}
        result=self.m.authentication_evidence(Client(),self.credentials())
        self.assertEqual('BLOCKED',result['status'])
        self.assertEqual(401,result['businessCode'])
        self.assertNotIn('private-test-token',json.dumps(result))
        self.assertNotIn('msg',result)

    def test_authenticated_tenant_is_derived_from_getinfo_not_header(self):
        class Client:
            def get(self,path):return {'code':200,'data':{'user':{'userId':'9223372036854775807','tenantId':'123456'},'permissions':['*:*:*']}}
        result=self.m.authentication_evidence(Client(),self.credentials())
        self.assertEqual('BLOCKED',result['status'])
        self.assertEqual('authenticated_tenant_mismatch',result['reason'])

    def test_authenticated_identity_is_hashed_not_dumped(self):
        class Client:
            def get(self,path):return {'code':200,'data':{'user':{'userId':'9223372036854775807','tenantId':'000000','userName':'private-person'},'permissions':['*:*:*']}}
        result=self.m.authentication_evidence(Client(),self.credentials())
        self.assertEqual('VERIFIED',result['status'])
        self.assertNotIn('private-person',json.dumps(result))
        self.assertNotIn('9223372036854775807',json.dumps(result))

    def test_process_or_target_files_are_not_a_loaded_head_attestation(self):
        auth={'status':'VERIFIED','principalSha256':'a'*64}
        db={'status':'OBSERVED','gaps':[],'fingerprint':'b'*64}
        result=self.m.readiness(self.m.RuntimeTarget(),self.credentials(),'c'*40,auth,db,
                                {'pid':123,'targetExists':True,'verified':True})
        self.assertEqual('BLOCKED',result['status'])
        self.assertIn('trusted_loaded_build_and_effective_target_attestation_missing',result['gaps'])
        self.assertFalse(result['businessWriteAuthorized'])

    def test_boolean_build_attestation_is_not_accepted(self):
        result=self.m.readiness(self.m.RuntimeTarget(),self.credentials(),'c'*40,
             {'status':'VERIFIED','principalSha256':'a'*64},{'status':'OBSERVED','gaps':[]}, {}, attestor=True)
        self.assertEqual('BLOCKED',result['status'])

    def test_native_quantity_metadata_must_be_decimal_and_transactional(self):
        metadata={'ry-erp.wm_material_stock':{'engine':'MyISAM','columns':{
            'stock_quantity':{'type':'double','precision':22,'scale':None},
            'reserved_quantity':{'type':'decimal','precision':18,'scale':6}}}}
        gaps=self.m.metadata_gaps(metadata)
        self.assertIn('ry-erp.wm_material_stock: engine must be InnoDB',gaps)
        self.assertIn('ry-erp.wm_material_stock.stock_quantity: native DECIMAL required',gaps)

    def test_multi_document_yaml_and_maven_placeholders_are_supported(self):
        docs=self.m.parse_config_text('spring:\n  profiles:\n    active: @profiles.active@\n---\nsecret:\n  test: value\n')
        self.assertEqual('@profiles.active@', docs[0]['spring']['profiles']['active'])
        self.assertEqual('value', docs[1]['secret']['test'])

    def test_database_connector_failure_is_redacted_and_has_mysql_errno(self):
        def failed(**kwargs):
            error=RuntimeError(1045,'secret password test-token')
            raise error
        settings=self.m.LocalSettings({'master':{'username':'person','password':'private-pwd',
                 'url':'jdbc:mysql://localhost:3306/ry-vue'}},{})
        report=self.m.database_evidence(settings,'000000',connector=failed)
        self.assertEqual('BLOCKED',report['status'])
        self.assertEqual(1045,report['mysqlErrorCode'])
        self.assertNotIn('private-pwd',json.dumps(report))
        self.assertNotIn('test-token',json.dumps(report))

    def test_no_database_fallback_to_unapproved_host(self):
        calls=[]
        settings=self.m.LocalSettings({'master':{'username':'x','password':'x',
                'url':'jdbc:mysql://remote.example:3306/ry-vue'}},{})
        with self.assertRaises(self.m.PreflightBlocked):
            self.m.database_evidence(settings,'000000',connector=lambda **kw:calls.append(kw))
        self.assertEqual([],calls)


    def test_auth_failure_has_safe_diagnostic_category(self):
        class Client:
            def get(self,path):return {'code':401,'msg':'secret-token'}
        self.assertEqual('AUTH_FAILED',self.m.authentication_evidence(Client(),self.credentials()).get('diagnosticCategory'))

    def test_mysql_auth_error_has_safe_diagnostic_category(self):
        def failed(**kwargs):raise RuntimeError(1045,'private-pwd secret-token')
        settings=self.m.LocalSettings({'master':{'username':'x','password':'private-pwd',
                       'url':'jdbc:mysql://localhost:3306/ry-vue'}},{})
        report=self.m.database_evidence(settings,'000000',connector=failed)
        self.assertEqual('AUTH_FAILED',report.get('diagnosticCategory'))
        self.assertIs(False,report.get('actualTargetVerified'))

    def test_unknown_cli_argument_never_echoes_a_secret(self):
        stdout,stderr=io.StringIO(),io.StringIO()
        with patch('sys.stdout',stdout),patch('sys.stderr',stderr):
            try:code=self.m.main(['--token','private-test-token'])
            except SystemExit:code=None
        self.assertEqual(2,code)
        self.assertNotIn('private-test-token',stdout.getvalue()+stderr.getvalue())

    def test_diagnostics_distinguish_refusal_timeout_and_missing_dependency(self):
        self.assertEqual('CONNECTION_REFUSED',self.m.diagnostic_category(ConnectionRefusedError()))
        self.assertEqual('CONNECTION_TIMEOUT',self.m.diagnostic_category(TimeoutError()))
        self.assertEqual('DEPENDENCY_MISSING',self.m.diagnostic_category(ModuleNotFoundError()))

    def fake_db(self,*,cleanup_fail=False,wrong_schema=False):
        outer=self
        class Cursor:
            def __enter__(self):return self
            def __exit__(self,*args):return False
            def execute(self,sql,params=None):
                outer.queries.append((sql,params));self.sql,self.params=sql,params
            def fetchone(self):
                if 'VERSION()' in self.sql:
                    return ('8.0.39',3306,'test-uuid','private-host',0,'other' if wrong_schema else 'ry-vue')
                if 'COALESCE(SUM' in self.sql:return (1,Decimal('0.123456789123'),Decimal('0.000000000001'))
                return (1,)
            def fetchall(self):
                if self.sql=='SHOW GRANTS':return [('GRANT SELECT ON *.* TO test',)]
                if 'information_schema.SCHEMATA' in self.sql:return [(v,) for v in self_outer.m.SCHEMAS.values()]
                if 'information_schema.TABLES' in self.sql:return [(t,'InnoDB') for t in self.params[1:]]
                if 'information_schema.STATISTICS' in self.sql:
                    return [('PRIMARY',0,1,'tenant_id',None),('PRIMARY',0,2,'request_key',None),
                            ('uk_certified_purchase_requisition',0,1,'tenant_id',None),
                            ('uk_certified_purchase_requisition',0,2,'requisition_id',None)]
                if 'information_schema.COLUMNS' in self.sql:
                    rows=[];schema=self.params[0]
                    for table in self.params[1:]:
                        rows.extend([(table,'tenant_id','varchar',None,None),(table,'del_flag','char',None,None)])
                        for col in self_outer.m.DECIMAL_FIELDS.get(schema+'.'+table,()):rows.append((table,col,'decimal',28,12))
                    return rows
                return []
        class DB:
            def cursor(self):return Cursor()
            def rollback(self):
                outer.rollback_calls+=1
                if cleanup_fail:raise RuntimeError('secret-token cleanup')
            def close(self):
                outer.close_calls+=1
                if cleanup_fail:raise RuntimeError('private-pwd cleanup')
        self_outer=self;self.queries=[];self.rollback_calls=self.close_calls=0
        return DB()

    def test_database_schema_and_native_precision_come_from_live_queries(self):
        db=self.fake_db()
        settings=self.m.LocalSettings({'master':{'username':'x','password':'private-pwd',
                    'url':'jdbc:mysql://localhost:3306/ry-vue'}},{})
        report=self.m.database_evidence(settings,'000000',connector=lambda **kw:db)
        self.assertEqual('OBSERVED',report.get('status'))
        self.assertEqual('ry-vue',report.get('currentSchema'))
        self.assertEqual(sorted(self.m.SCHEMAS.values()),report.get('actualSchemas'))
        self.assertTrue(report.get('actualTargetVerified'))
        self.assertEqual('0.123456789123',report['stockAggregate']['quantity'])
        self.assertEqual('UNVERIFIED',report['stockOrigin']['status'])
        self.assertEqual((1,1),(self.rollback_calls,self.close_calls))
        self.assertIn(('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY',None),self.queries)
        for sql,params in self.queries:
            self.assertFalse(sql.startswith(('INSERT','UPDATE','DELETE','DROP','ALTER')))
            if '`ry-' in sql:self.assertIn('tenant_id=%s',sql);self.assertEqual(('000000',),params)
        self.assertNotIn('private-pwd',json.dumps(report))
        self.assertNotIn('private-host',json.dumps(report))

    def test_actual_schema_mismatch_is_not_configuration_target_verification(self):
        db=self.fake_db(wrong_schema=True)
        settings=self.m.LocalSettings({'master':{'username':'x','password':'x','url':'jdbc:mysql://localhost:3306/ry-vue'}},{})
        report=self.m.database_evidence(settings,'000000',connector=lambda **kw:db)
        self.assertEqual('TARGET_MISMATCH',report.get('diagnosticCategory'))
        self.assertIs(False,report.get('actualTargetVerified'))

    def test_database_cleanup_failure_cannot_leak_a_secret(self):
        db=self.fake_db(cleanup_fail=True)
        settings=self.m.LocalSettings({'master':{'username':'x','password':'x','url':'jdbc:mysql://localhost:3306/ry-vue'}},{})
        try:report=self.m.database_evidence(settings,'000000',connector=lambda **kw:db)
        except Exception:report=None
        self.assertIsNotNone(report,'cleanup must be redacted rather than escape')
        self.assertEqual('BLOCKED',report.get('status'))
        self.assertNotIn('private-pwd',json.dumps(report))
        self.assertNotIn('secret-token',json.dumps(report))


    def test_missing_read_driver_is_a_diagnostic_not_an_exception_echo(self):
        settings=self.m.LocalSettings({'master':{'username':'x','password':'x','url':'jdbc:mysql://localhost:3306/ry-vue'}},{})
        with patch.dict('sys.modules',{'pymysql':None}):
            report=self.m.database_evidence(settings,'000000')
        self.assertEqual('DEPENDENCY_MISSING',report['diagnosticCategory'])
        self.assertEqual('BLOCKED',report['status'])

    def test_evidence_directory_uses_actual_cross_day_observation_date(self):
        from datetime import datetime,timezone,timedelta
        observed=datetime(2026,10,3,0,1,tzinfo=timezone(timedelta(hours=8)))
        self.assertEqual('20261003-unified-procurement',self.m.evidence_directory(observed).parent.name)

    def test_redirect_handler_never_forwards_a_credential(self):
        self.assertIsNone(self.m._NoRedirect().redirect_request(None,None,302,'moved',{},'http://remote.example'))

    def test_unknown_http_exception_is_suppressed(self):
        def broken(*args,**kwargs):raise RuntimeError('secret-token private-pwd')
        client=self.m.ReadOnlyClient(self.m.RuntimeTarget(),self.credentials(),opener=broken)
        report=self.m.authentication_evidence(client,self.credentials())
        self.assertEqual('BLOCKED',report['status'])
        self.assertNotIn('secret-token',json.dumps(report))
        self.assertNotIn('private-pwd',json.dumps(report))


    def test_native_tiny_decimal_keeps_scale_without_binary_conversion(self):
        self.assertEqual('0.000000000001',format(self.m.exact_decimal(Decimal('1E-12')),'f'))

    def test_tenant_string_domain_is_not_a_snowflake_domain(self):
        self.assertEqual('99999999999999999999',self.m.strict_id('99999999999999999999',tenant=True))
        with self.assertRaises(self.m.PreflightBlocked):self.m.strict_id('0',tenant=True)
        with self.assertRaises(self.m.PreflightBlocked):self.m.strict_id('01')

    def test_enforcing_attestor_cannot_return_true_as_authorization(self):
        class Attestor(self.m.RuntimeAuthority):
            def require_current(self,**kwargs):return True
        report=self.m.readiness(self.m.RuntimeTarget(),self.credentials(),'a'*40,
             {'status':'VERIFIED','principalSha256':'b'*64},{'status':'OBSERVED','gaps':[]},{},attestor=Attestor())
        self.assertEqual('BLOCKED',report['status'])
        self.assertIn('BUILD_ATTESTATION_REJECTED',report['diagnosticCategories'])


    def test_certified_permanent_receipt_table_is_in_runtime_metadata_scope(self):
        self.assertIn('pu_certified_purchase_command',self.m.TABLES['ry-erp'])

    def test_certified_receipt_requires_full_native_unique_keys(self):
        self.assertTrue(callable(getattr(self.m,'certified_index_gaps',None)))
        rows=[('PRIMARY',0,1,'tenant_id',None),('PRIMARY',0,2,'request_key',None),
              ('uk_certified_purchase_requisition',0,1,'tenant_id',None),
              ('uk_certified_purchase_requisition',0,2,'requisition_id',None)]
        self.assertEqual([],self.m.certified_index_gaps(rows))
        for broken in ([],rows[:-1],[('PRIMARY',0,1,'tenant_id',3),*rows[1:]],
                       [('PRIMARY',1,1,'tenant_id',None),*rows[1:]]):
            with self.subTest(broken=broken):self.assertTrue(self.m.certified_index_gaps(broken))


if __name__=='__main__':unittest.main()
