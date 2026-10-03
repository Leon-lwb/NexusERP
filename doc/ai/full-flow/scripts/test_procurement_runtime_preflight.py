"""任务 C：离线测试。不接触本地服务或共享 Maven。"""
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

    def test_stock_native_precision_contract_matches_migration_without_certifying_sources(self):
        for precision, scale in ((18, 4), (28, 12), (18, 2), (12, 4)):
            with self.subTest(precision=precision, scale=scale):
                columns = {name: {'type':'decimal', 'precision':precision, 'scale':scale}
                           for name in ('stock_quantity','reserved_quantity')}
                columns['tenant_id'] = {'type':'varchar'}
                metadata = {'ry-erp.wm_material_stock': {'engine':'InnoDB', 'columns':columns}}
                gaps = self.m.metadata_gaps(metadata)
                stock_gaps = [gap for gap in gaps if gap.startswith('ry-erp.wm_material_stock.')]
                self.assertEqual(not (precision == 18 and scale == 4), bool(stock_gaps))

    def test_tx_native_precision_requires_same_contract_but_migration_does_not_rewrite_tx(self):
        columns = {name: {'type':'decimal', 'precision':18, 'scale':2}
                   for name in ('tx_quantity','before_quantity','after_quantity')}
        columns['tenant_id'] = {'type':'varchar'}
        gaps = self.m.metadata_gaps({'ry-erp.wm_material_stock_tx': {'engine':'InnoDB', 'columns':columns}})
        self.assertEqual(3, len([gap for gap in gaps if gap.startswith('ry-erp.wm_material_stock_tx.')]))

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
                        for col in self_outer.m.DECIMAL_FIELDS.get(schema+'.'+table,()):
                            precision,scale = (18,4) if table in ('wm_material_stock','wm_material_stock_tx') else (28,12)
                            rows.append((table,col,'decimal',precision,scale))
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
        self.assertEqual('20261003-procurement-execution',self.m.evidence_directory(observed).parent.name)

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



    # 测试向量由已安装的 Jasypt 1.9.3 命令行工具使用仅用于测试夹具的密钥生成。
    ENCRYPTED_FIXTURE = 'ENC(D28akg/ic9qZL9TTAqhsDp74muCG6mcmGQlZiCw9bes+GzedDgzu9uaSfijUAQlgMogBh6R1zsiWhfFyJ2GXdA==)'

    def crypto_context(self):
        return {'jasypt.encryptor.algorithm':'PBEWITHHMACSHA512ANDAES_256',
                'jasypt.encryptor.password':'${JASYPT_PASSWORD:fixture-local-secret}'}

    def test_encrypted_config_is_decrypted_in_memory_using_real_jasypt_vector(self):
        decrypt=getattr(self.m,'configuration_value',None)
        self.assertTrue(callable(decrypt),'ENC configuration must not be sent as a MySQL password')
        plain=decrypt(self.ENCRYPTED_FIXTURE,self.crypto_context(),{})
        self.assertEqual('fixture-db-password',plain)

    def test_encrypted_config_wrong_key_and_unsupported_algorithm_are_fixed_diagnostics(self):
        decrypt=getattr(self.m,'configuration_value',None)
        self.assertTrue(callable(decrypt),'bounded local configuration decryption is missing')
        for ctx,env in ((self.crypto_context(),{'JASYPT_PASSWORD':'private-wrong-key'}),
                        ({**self.crypto_context(),'jasypt.encryptor.algorithm':'unsupported-secret-algorithm'},{})):
            with self.subTest(envPresent=bool(env)):
                try: decrypt(self.ENCRYPTED_FIXTURE,ctx,env)
                except self.m.PreflightBlocked as error:
                    self.assertIn(error.category,('ENCRYPTED_CONFIG_UNSUPPORTED','ENCRYPTED_CONFIG_DECRYPTION_FAILED'))
                    self.assertNotIn('private-wrong-key',str(error));self.assertNotIn('unsupported-secret-algorithm',str(error))
                else:self.fail('failed decryption must not silently fall through to a password')

    def test_plaintext_config_is_not_modified_and_encrypted_password_never_in_summary(self):
        decrypt=getattr(self.m,'configuration_value',None)
        self.assertTrue(callable(decrypt),'local configuration resolver is missing')
        self.assertEqual('fixture-plain',decrypt('${DB_PASSWORD}',{}, {'DB_PASSWORD':'fixture-plain'}))
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);resources=root/'ruoyi-admin/src/main/resources';resources.mkdir(parents=True)
            (resources/'application.yml').write_text('spring:\n  profiles:\n    active: dev\njasypt:\n  encryptor:\n    algorithm: PBEWITHHMACSHA512ANDAES_256\n    password: fixture-local-secret\n',encoding='utf-8')
            text='spring:\n  datasource:\n    dynamic:\n      datasource:\n'
            for name,schema in self.m.SCHEMAS.items():
                text+=f'        {name}:\n          url: jdbc:mysql://127.0.0.1:3306/{schema}\n          username: fixture-user\n          password: {self.ENCRYPTED_FIXTURE}\n'
            (resources/'application-dev.yml').write_text(text,encoding='utf-8')
            settings=self.m.settings_from_files(root,environ={})
            self.assertEqual('fixture-db-password',settings.datasources['master']['password'])
            self.assertTrue(settings.summary.get('encryptedValuesResolvedInMemory'))
            self.assertNotIn('fixture-db-password',json.dumps(settings.summary))



    def test_existing_private_sessions_are_discovered_but_fresh_getinfo_is_still_required(self):
        resolve=getattr(self.m,'resolve_existing_authentication',None)
        self.assertTrue(callable(resolve),'existing local sessions must be freshly verified without a login')
        calls=[]
        def opener(request,timeout):
            calls.append(request)
            good=request.get_header('Authorization')=='Bearer private-fresh-session'
            doc={'code':200,'data':{'user':{'userId':'1','tenantId':'000000'}}} if good else {'code':401,'msg':'private-stale-session'}
            response=io.BytesIO(json.dumps(doc).encode());response.status=200;return response
        with tempfile.TemporaryDirectory() as temporary,patch.object(self.m,'PRIVATE_ROOT',Path(temporary)):
            root=Path(temporary);fresh=root/'20261003-procurement-execution/runtime/private-credential-20261003T090000.json';fresh.parent.mkdir(parents=True)
            for file,token in ((root/'session-token.json','private-stale-session'),(fresh,'private-fresh-session')):
                file.write_text(json.dumps({'token':token,'clientId':'fixture-client','tenantId':'000000','baseUrl':'http://127.0.0.1:8080'}),encoding='utf-8')
            credentials,auth=resolve(environ={},opener=opener)
            self.assertEqual('private-fresh-session',credentials.token);self.assertEqual('VERIFIED',auth['status'])
            self.assertNotIn('private-fresh-session',json.dumps(auth));self.assertTrue(all(c.get_method()=='GET' for c in calls))
            self.assertTrue(all(url.endswith('/system/user/getInfo') for url in (c.full_url for c in calls)))

    def test_session_discovery_does_not_merge_partial_environment_or_accept_active_flags(self):
        resolve=getattr(self.m,'resolve_existing_authentication',None)
        self.assertTrue(callable(resolve),'fresh local session resolver is missing')
        calls=[]
        with tempfile.TemporaryDirectory() as temporary,patch.object(self.m,'PRIVATE_ROOT',Path(temporary)):
            (Path(temporary)/'session-token.json').write_text(json.dumps({'token':'private-token','clientId':'fixture-client',
                'tenantId':'000000','baseUrl':'http://127.0.0.1:8080','status':'ACTIVE'}),encoding='utf-8')
            for env in ({'NEXUS_FULL_FLOW_TOKEN':'partial-private-token'},{}):
                credentials,auth=resolve(environ=env,opener=lambda *a,**kw:calls.append(a))
                self.assertIsNone(credentials);self.assertEqual('BLOCKED',auth['status'])
                self.assertNotIn('partial-private-token',json.dumps(auth))
            self.assertEqual([],calls)

    def test_loaded_runtime_is_probed_even_while_independent_schema_guards_are_blocked(self):
        calls=[]
        class Authority(self.m.RuntimeAuthority):
            def require_current(self,**kwargs):
                calls.append(kwargs)
                raise self_outer.m.PreflightBlocked('secret backend contents',category='ATTESTATION_ENDPOINT_MISSING')
        self_outer=self
        result=self.m.readiness(self.m.RuntimeTarget(),self.credentials(),'a'*40,
                {'status':'VERIFIED','principalSha256':'b'*64},
                {'status':'BLOCKED','gaps':['actual_schema_gap']},{},attestor=Authority())
        self.assertEqual(1,len(calls),'schema blockers must not hide the loaded-build diagnostic')
        self.assertIn('ATTESTATION_ENDPOINT_MISSING',result['diagnosticCategories'])
        self.assertNotIn('secret backend contents',json.dumps(result));self.assertEqual('BLOCKED',result['status'])

    def test_inspect_local_constructs_default_concrete_runtime_authority_without_business_permission(self):
        resolve=getattr(self.m,'resolve_existing_authentication',None)
        self.assertTrue(callable(resolve),'default inspection needs freshly verified existing credentials')
        calls=[];outer=self
        class Authority(self.m.RuntimeAuthority):
            def __init__(self,**kwargs):calls.append(('constructor',kwargs))
            def require_current(self,**kwargs):calls.append(('require_current',kwargs));return None
        with patch.object(self.m,'resolve_existing_authentication',return_value=(self.credentials(),{'status':'VERIFIED','principalSha256':'b'*64})), \
             patch.object(self.m,'settings_from_files',return_value=self.m.LocalSettings({'master':{'username':'x','password':'x','url':'jdbc:mysql://localhost:3306/ry-vue'}},{})), \
             patch.object(self.m,'database_evidence',return_value={'status':'OBSERVED','gaps':[]}), \
             patch.object(self.m,'process_evidence',return_value={}), \
             patch('procurement_runtime_attestation.BackendRuntimeAuthority',Authority), \
             patch('procurement_runtime_attestation.management_metadata_evidence',return_value={'status':'OBSERVED','activeProfiles':['dev'],'notLoadedArtifactProof':True}) as management, \
             patch.object(self.m.subprocess,'check_output',side_effect=['codex/fixture\n','a'*40+'\n','2026-10-03T00:00:00+08:00\n','']):
            report=self.m.inspect_local()
        self.assertEqual(['constructor','require_current'],[item[0] for item in calls])
        self.assertEqual(1,management.call_count,'default preflight must safely observe existing actuator metadata')
        self.assertEqual(['dev'],report.get('management',{}).get('activeProfiles'))
        self.assertEqual('READ_ONLY_READY',report['status']);self.assertFalse(report['businessWriteAuthorized'])



    def test_direct_cli_does_not_create_a_second_runtimeauthority_or_credentials_class(self):
        import subprocess,sys
        program="""import ast,sys,types
from pathlib import Path
path=Path(sys.argv[1]).resolve()
sys.path.insert(0,str(path.parent))
tree=ast.parse(path.read_text(encoding='utf-8'))
tree.body=tree.body[:-1]
module=types.ModuleType('__main__');module.__file__=str(path)
sys.modules['__main__']=module
exec(compile(tree,str(path),'exec'),module.__dict__)
import procurement_runtime_attestation as authority
assert authority.RuntimeAuthority is module.RuntimeAuthority, 'CLI RuntimeAuthority identity split'
assert authority.Credentials is module.Credentials, 'CLI Credentials identity split'
"""
        completed=subprocess.run([sys.executable,'-c',program,str(Path(self.m.__file__).resolve())],
                                 capture_output=True,text=True,timeout=10)
        self.assertEqual(0,completed.returncode,completed.stderr)



    def test_windows_listener_timestamp_is_explicitly_offset_aware_for_runtime_binding(self):
        from types import SimpleNamespace
        commands=[]
        def run(command,**kwargs):
            commands.append(command)
            return SimpleNamespace(returncode=0,stdout='[{"port":8080,"pid":13579,"name":"java.exe","startedAt":"2026-10-03T08:00:00+08:00","commandLine":"fixture"}]')
        with tempfile.TemporaryDirectory() as temporary,patch.object(self.m.os,'name','nt'), \
             patch.object(self.m.subprocess,'run',side_effect=run),patch.object(self.m.socket,'create_connection',side_effect=OSError):
            self.m.process_evidence(Path(temporary))
        self.assertIn('[DateTimeOffset]',commands[0][-1],
                      'CIM CreationDate is often naive; requesting o alone cannot bind an aware backend timestamp')



    def test_schema_contract_failure_is_not_misreported_as_a_login_or_actual_database_target_failure(self):
        db=self.fake_db()
        settings=self.m.LocalSettings({'master':{'username':'fixture','password':'fixture',
                    'url':'jdbc:mysql://127.0.0.1:3306/ry-vue'}},{})
        with patch.object(self.m,'metadata_gaps',return_value=['ry-erp.wm_material_stock.stock_quantity: native DECIMAL required']):
            report=self.m.database_evidence(settings,'000000',connector=lambda **kwargs:db)
        self.assertTrue(report['actualTargetVerified']);self.assertEqual('BLOCKED',report['status'])
        self.assertEqual('SCHEMA_OR_PRECISION_UNVERIFIED',report.get('diagnosticCategory'))
        self.assertIs(False,report.get('backendDatasourceBindingVerified'))


if __name__=='__main__':unittest.main()
