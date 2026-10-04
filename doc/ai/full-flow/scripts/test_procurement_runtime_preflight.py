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

    def fake_db(self,*,cleanup_fail=False,wrong_schema=False,stock_count=1,missing_table=None):
        from test_procurement_schema_contract import fresh_metadata
        contract_metadata = fresh_metadata()
        outer=self
        class Cursor:
            def __enter__(self):return self
            def __exit__(self,*args):return False
            def execute(self,sql,params=None):
                outer.queries.append((sql,params));self.sql,self.params=sql,params
            def fetchone(self):
                if 'VERSION()' in self.sql:
                    return ('8.0.39',3306,'test-uuid','private-host',0,'other' if wrong_schema else 'ry-vue')
                if 'COALESCE(SUM' in self.sql:
                    return (stock_count, Decimal('0.123456789123') if stock_count else Decimal('0'),
                            Decimal('0.000000000001') if stock_count else Decimal('0'))
                if self.sql.startswith('SELECT COUNT(*) FROM `ry-erp`.`wm_material_stock`'):
                    return (stock_count,)
                return (1,)
            def fetchall(self):
                if self.sql=='SHOW GRANTS':return [('GRANT SELECT ON *.* TO test',)]
                if 'information_schema.SCHEMATA' in self.sql:return [(v,) for v in self_outer.m.SCHEMAS.values()]
                if 'information_schema.TABLES' in self.sql:
                    return [(t,'InnoDB') for t in self.params[1:]
                            if self.params[0]+'.'+t != missing_table]
                if 'information_schema.STATISTICS' in self.sql:
                    return [(table,*part) for table in self.params[1:]
                            if self.params[0]+'.'+table != missing_table
                            for part in contract_metadata.get(self.params[0]+'.'+table, {}).get('indexes', [])]
                if 'information_schema.COLUMNS' in self.sql:
                    rows=[];schema=self.params[0]
                    for table in self.params[1:]:
                        key = schema+'.'+table
                        if key == missing_table: continue
                        def spec(kind, length=None, precision=None, scale=None):
                            return {'type':kind, 'precision':precision, 'scale':scale, 'nullable':'NO',
                                    'characterMaximumLength':length,
                                    'columnType':f'{kind}({length})' if length else
                                                 f'decimal({precision},{scale})' if kind=='decimal' else kind,
                                    'collation':'utf8mb4_unicode_ci' if kind in ('varchar','char') else None,
                                    'datetimePrecision':None, 'extra':'', 'generationExpression':''}
                        columns = {'tenant_id':spec('varchar', 20), 'del_flag':spec('char', 1)}
                        for col in self_outer.m.DECIMAL_FIELDS.get(key, ()):
                            precision, scale = (18,4) if table in ('wm_material_stock','wm_material_stock_tx') else (28,12)
                            columns[col] = spec('decimal', precision=precision, scale=scale)
                        columns.update(contract_metadata.get(key, {}).get('columns', {}))
                        fields = ('type','precision','scale','nullable','characterMaximumLength','columnType',
                                  'collation','datetimePrecision','extra','generationExpression')
                        rows.extend((table,col,*(observed[field] for field in fields))
                                    for col,observed in columns.items())
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

    def test_inspect_local_explicit_strict_keeps_strong_diagnostic_without_business_permission(self):
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
             patch('procurement_local_runtime.BackendRuntimeAuthority',Authority), \
             patch('procurement_runtime_attestation.management_metadata_evidence',return_value={'status':'OBSERVED','activeProfiles':['dev'],'notLoadedArtifactProof':True}) as management, \
             patch.object(self.m.subprocess,'check_output',side_effect=['codex/fixture\n','a'*40+'\n','2026-10-03T00:00:00+08:00\n','']):
            report=self.m.inspect_local(runtime_check_mode='strict')
        self.assertEqual(['constructor','require_current'],[item[0] for item in calls])
        self.assertEqual(1,management.call_count,'explicit strict preserves actuator diagnostics')
        self.assertEqual(['dev'],report.get('management',{}).get('activeProfiles'))
        self.assertEqual('READ_ONLY_READY',report['status']);self.assertFalse(report['businessWriteAuthorized'])



    def test_inspect_local_default_uses_local_consistency_without_actuator_or_signer(self):
        from procurement_local_runtime import LocalDevelopmentRuntimeAuthority
        calls=[]
        class Authority(LocalDevelopmentRuntimeAuthority):
            def __init__(self,**kwargs):
                calls.append(('constructor',kwargs));self._evidence=self._empty_evidence('LOCAL_DEV_CHECKED')
            def require_current(self,**kwargs):calls.append(('require_current',kwargs));return None
        with patch.object(self.m,'resolve_existing_authentication',return_value=(self.credentials(),{'status':'VERIFIED','principalSha256':'b'*64})), \
             patch.object(self.m,'settings_from_files',return_value=self.m.LocalSettings({'master':{'username':'x','password':'x','url':'jdbc:mysql://localhost:3306/ry-vue'}},{})), \
             patch.object(self.m,'database_evidence',return_value={'status':'OBSERVED','gaps':[]}), \
             patch.object(self.m,'process_evidence',return_value={}), \
             patch('procurement_local_runtime.LocalDevelopmentRuntimeAuthority',Authority), \
             patch('procurement_runtime_attestation.management_metadata_evidence',side_effect=AssertionError('本地默认不需要actuator强证明')) as management, \
             patch.object(self.m.subprocess,'check_output',side_effect=['codex/fixture\n','a'*40+'\n','2026-10-04T00:00:00+08:00\n','']):
            report=self.m.inspect_local()
        self.assertEqual(['constructor','require_current'],[item[0] for item in calls]);management.assert_not_called()
        self.assertEqual('local-dev',report['runtimeCheckMode']);self.assertEqual('LOCAL_DEV_CHECKED',report['loadedRuntime']['status'])
        self.assertFalse(report['loadedRuntime']['productionProof']);self.assertFalse(report['businessWriteAuthorized'])
        self.assertFalse(report['git']['finalSourceCommitAndLoadedArtifactVerificationRequired'])
    def test_unknown_runtime_mode_rejected_before_process_or_network_probe(self):
        with patch.object(self.m,'process_evidence',side_effect=AssertionError('不应执行进程检查')) as process:
            with self.assertRaises(self.m.PreflightBlocked) as caught:self.m.inspect_local(runtime_check_mode='prod-bypass')
        process.assert_not_called();self.assertEqual('RUNTIME_CHECK_MODE_UNSUPPORTED',caught.exception.category)

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





    def test_empty_stock_rows_never_remove_source_completeness_gate(self):
        db = self.fake_db(stock_count=0)
        settings = self.m.LocalSettings({'master': {'username':'x', 'password':'x',
                                         'url':'jdbc:mysql://localhost:3306/ry-vue'}}, {})
        observed = self.m.database_evidence(settings, '000000', connector=lambda **kw: db)
        self.assertEqual('EMPTY_OBSERVED', observed['stockOrigin']['status'])
        self.assertIs(True, observed['stockOrigin']['requiresFullSourceLedgerAndReceiptReconciliation'])
        self.assertIn('empty_stock_snapshot_completeness_not_established', observed['gaps'])
        class Authority(self.m.RuntimeAuthority):
            def require_current(self, **kwargs): return None
        ready = self.m.readiness(self.m.RuntimeTarget(), self.credentials(), 'a'*40,
                                  {'status':'VERIFIED', 'principalSha256':'b'*64},
                                  observed, {}, attestor=Authority())
        self.assertEqual('BLOCKED', ready['status'])
        self.assertIn('STOCK_SOURCE_UNVERIFIED', ready['diagnosticCategories'])
        self.assertIs(False, ready['businessWriteAuthorized'])


    def test_v2_birth_schema_is_checked_before_any_tenant_business_read(self):
        self.assertIn('wm_receipt_iqc_birth', self.m.TABLES['ry-erp'])
        self.assertIn('qc_receipt_iqc_birth', self.m.TABLES['ry-mes'])
        db = self.fake_db(missing_table='ry-mes.qc_receipt_iqc_birth')
        settings = self.m.LocalSettings({'master': {'username':'x', 'password':'x',
                                         'url':'jdbc:mysql://localhost:3306/ry-vue'}}, {})
        observed = self.m.database_evidence(settings, '000000', connector=lambda **kw: db)
        self.assertEqual('BLOCKED', observed['status'])
        self.assertTrue(any('ry-mes.qc_receipt_iqc_birth' in gap for gap in observed['gaps']))
        self.assertEqual('BLOCKED', observed['procurementSchema']['status'])
        self.assertTrue(observed['actualTargetVerified'])
        self.assertFalse(any(' FROM `ry-' in sql for sql, _ in self.queries))
        self.assertEqual((1, 1), (self.rollback_calls, self.close_calls))

    def test_full_v2_schema_observation_is_not_source_or_write_certification(self):
        db = self.fake_db()
        settings = self.m.LocalSettings({'master': {'username':'x', 'password':'x',
                                         'url':'jdbc:mysql://localhost:3306/ry-vue'}}, {})
        observed = self.m.database_evidence(settings, '000000', connector=lambda **kw: db)
        contract = observed.get('procurementSchema', {})
        self.assertEqual('SCHEMA_METADATA_MATCH', contract.get('status'))
        self.assertEqual('NOT_PERFORMED', contract.get('sourceCertification'))
        self.assertIs(False, contract.get('businessWriteAuthorized'))
        self.assertEqual('UNVERIFIED', observed['stockOrigin']['status'])
        self.assertTrue(all('IS_NULLABLE' in sql and 'GENERATION_EXPRESSION' in sql
                            for sql, _ in self.queries if 'information_schema.COLUMNS' in sql))
        self.assertTrue(all('INDEX_TYPE' in sql for sql, _ in self.queries
                            if 'information_schema.STATISTICS' in sql))

class AuthenticationDiagnosticTests(unittest.TestCase):
    """失败诊断只保留固定分类和数值，不能误导操作者或开放业务读取。"""

    def setUp(self):
        self.m = importlib.import_module('procurement_runtime_preflight')
        self.env = {'NEXUS_FULL_FLOW_TOKEN': 'private-test-session',
                    'NEXUS_FULL_FLOW_CLIENT_ID': 'private-client',
                    'NEXUS_FULL_FLOW_TENANT_ID': '000000'}

    def response_opener(self, code):
        class Response:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def read(self, *args):
                return json.dumps({'code': code, 'msg': 'private-test-session private-pwd',
                                   'data': {'private': 'secret-business-row'}}).encode('utf-8')
        return lambda *args, **kwargs: Response()

    def test_explicit_failed_session_preserves_401_without_remote_body(self):
        credentials, auth = self.m.resolve_existing_authentication(
            environ=self.env, opener=self.response_opener(401))
        self.assertIsNone(credentials)
        self.assertEqual('BLOCKED', auth['status'])
        self.assertEqual(401, auth.get('businessCode'))
        self.assertEqual('AUTH_FAILED', auth['diagnosticCategory'])
        self.assertEqual(0, auth['loginRequests'])
        self.assertEqual(0, auth['businessWrites'])
        self.assertNotIn('private-test-session', json.dumps(auth))
        self.assertNotIn('private-pwd', json.dumps(auth))
        self.assertNotIn('secret-business-row', json.dumps(auth))

    def test_permission_and_backend_errors_are_not_reported_as_expired_login(self):
        for code, category in ((403, 'READ_PERMISSION_DENIED'), (429, 'AUTH_RATE_LIMITED'),
                               (500, 'AUTH_BACKEND_ERROR'), (503, 'AUTH_BACKEND_ERROR')):
            with self.subTest(code=code):
                credentials, auth = self.m.resolve_existing_authentication(
                    environ=self.env, opener=self.response_opener(code))
                self.assertIsNone(credentials)
                self.assertEqual(code, auth.get('businessCode'))
                self.assertEqual(category, auth['diagnosticCategory'])

    def test_unknown_business_code_and_malformed_shape_remain_unverified(self):
        for code in (404, 412, True, '401', None):
            with self.subTest(code=code):
                credentials, auth = self.m.resolve_existing_authentication(
                    environ=self.env, opener=self.response_opener(code))
                self.assertIsNone(credentials)
                self.assertEqual('AUTH_CONTEXT_UNVERIFIED', auth['diagnosticCategory'])
                self.assertEqual(code if type(code) is int else None, auth.get('businessCode'))

    def test_transport_failure_retains_safe_specific_diagnostic(self):
        from urllib.error import HTTPError
        def rejected(*args, **kwargs):
            raise HTTPError('http://127.0.0.1:8080', 403, 'private-pwd', {}, None)
        credentials, auth = self.m.resolve_existing_authentication(environ=self.env, opener=rejected)
        self.assertIsNone(credentials)
        self.assertEqual('READ_PERMISSION_DENIED', auth['diagnosticCategory'])
        self.assertNotIn('private-pwd', json.dumps(auth))

    def test_cli_summary_exposes_only_safe_authentication_diagnostic(self):
        report = {'status': 'BLOCKED', 'observedAt': '2026-10-03T15:39:00+08:00',
                  'gaps': ['fresh_authenticated_tenant_principal_context_missing'],
                  'diagnosticCategories': ['AUTH_FAILED'],
                  'authentication': {'status': 'BLOCKED', 'businessCode': 401,
                                     'diagnosticCategory': 'AUTH_FAILED',
                                     'credentialSha256': 'private-fingerprint',
                                     'arbitraryRemoteBody': 'private-test-session'}}
        output = io.StringIO()
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(self.m, 'PRIVATE_ROOT', Path(directory)), \
             patch.object(self.m, 'inspect_local', return_value=report), \
             patch('sys.stdout', output):
            self.assertEqual(2, self.m.main([]))
        summary = json.loads(output.getvalue())
        self.assertEqual({'status': 'BLOCKED', 'businessCode': 401,
                          'diagnosticCategory': 'AUTH_FAILED'}, summary.get('authentication'))
        self.assertIs(False, summary['businessWriteAuthorized'])
        self.assertNotIn('private-test-session', output.getvalue())
        self.assertNotIn('private-fingerprint', output.getvalue())






    def test_multiple_failed_candidates_keep_bounded_classifications_and_code_counts(self):
        codes = {'private-test-one':401, 'private-test-two':500}
        def opener(request, **kwargs):
            code = codes[request.get_header('Authorization').removeprefix('Bearer ')]
            return self.response_opener(code)(request, **kwargs)
        with tempfile.TemporaryDirectory() as directory, patch.object(self.m, 'PRIVATE_ROOT', Path(directory)):
            folder = Path(directory)/'20261003-procurement-execution'/'runtime'
            folder.mkdir(parents=True)
            for index, token in enumerate(codes):
                (folder/f'private-credential-{index}.json').write_text(json.dumps({
                    'token':token, 'clientId':'private-client', 'tenantId':'000000',
                    'baseUrl':'http://127.0.0.1:8080'}), encoding='utf-8')
            credentials, auth = self.m.resolve_existing_authentication(environ={}, opener=opener)
        self.assertIsNone(credentials)
        self.assertEqual('AUTH_CONTEXT_UNVERIFIED', auth['diagnosticCategory'])
        self.assertEqual(['AUTH_BACKEND_ERROR', 'AUTH_FAILED'], auth.get('failureCategories'))
        self.assertEqual([{'code':401, 'count':1}, {'code':500, 'count':1}], auth.get('businessCodeCounts'))
        self.assertNotIn('private-test-one', json.dumps(auth))
        self.assertNotIn('private-client', json.dumps(auth))
        self.assertNotIn('private-credential', json.dumps(auth))

    def test_non_success_business_code_survives_unrelated_invalid_id_fields(self):
        class Response:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def read(self, *args): return json.dumps(self.body).encode('utf-8')
        for code, category in ((401,'AUTH_FAILED'), (403,'READ_PERMISSION_DENIED'),
                               (429,'AUTH_RATE_LIMITED'), (500,'AUTH_BACKEND_ERROR')):
            with self.subTest(code=code):
                response = Response()
                response.body = {'code':code, 'data':{'debugId':True, 'userId':1.5}}
                credentials, auth = self.m.resolve_existing_authentication(
                    environ=self.env, opener=lambda *a, **kw: response)
                self.assertIsNone(credentials)
                self.assertEqual(code, auth.get('businessCode'))
                self.assertEqual(category, auth['diagnosticCategory'])
        response = Response()
        response.body = {'code':200, 'data':{'user':{'userId':True, 'tenantId':'000000'}, 'permissions':[]}}
        credentials, auth = self.m.resolve_existing_authentication(
            environ=self.env, opener=lambda *a, **kw: response)
        self.assertIsNone(credentials)
        self.assertEqual('BLOCKED', auth['status'])

    def test_http_failure_keeps_safe_status_separate_from_business_code(self):
        from urllib.error import HTTPError
        for status, category in ((401,'AUTH_FAILED'), (403,'READ_PERMISSION_DENIED'),
                                 (429,'AUTH_RATE_LIMITED'), (500,'AUTH_BACKEND_ERROR'),
                                 (503,'AUTH_BACKEND_ERROR'), (404,'HTTP_REJECTED')):
            def rejected(*args, **kwargs):
                raise HTTPError('http://127.0.0.1:8080', status, 'private-pwd', {}, None)
            with self.subTest(status=status):
                credentials, auth = self.m.resolve_existing_authentication(environ=self.env, opener=rejected)
                self.assertIsNone(credentials)
                self.assertEqual(status, auth.get('httpStatus'))
                self.assertIsNone(auth.get('businessCode'))
                self.assertEqual(category, auth['diagnosticCategory'])
                self.assertNotIn('private-pwd', json.dumps(auth))

    def test_known_urllib_transport_causes_keep_specific_fixed_diagnostics(self):
        from urllib.error import URLError
        for reason, category in ((ConnectionRefusedError('private-pwd'),'CONNECTION_REFUSED'),
                                 (TimeoutError('private-pwd'),'CONNECTION_TIMEOUT'),
                                 ('private-pwd','PROBE_FAILED')):
            def unavailable(*args, **kwargs): raise URLError(reason)
            with self.subTest(category=category):
                credentials, auth = self.m.resolve_existing_authentication(environ=self.env, opener=unavailable)
                self.assertIsNone(credentials)
                self.assertEqual(category, auth['diagnosticCategory'])
                self.assertNotIn('private-pwd', json.dumps(auth))


class SchemaMetadataQueryTests(unittest.TestCase):
    """结构读取保留原生属性及永久唯一键，不查询或改写业务行。"""

    def setUp(self):
        self.m = importlib.import_module('procurement_runtime_preflight')
        self.assertTrue(callable(getattr(self.m, '_read_schema_metadata', None)),
                        '完整结构合同缺少实际元数据读取入口')

    def cursor(self, *, duplicate_column=False, unexpected_table=False):
        class Cursor:
            def __init__(self): self.calls = []
            def execute(self, sql, params): self.calls.append((sql, params)); self.sql = sql
            def fetchall(self):
                if 'information_schema.TABLES' in self.sql:
                    return [('wm_material_stock', 'InnoDB')]
                if 'information_schema.COLUMNS' in self.sql:
                    table = 'unapproved_table' if unexpected_table else 'wm_material_stock'
                    rows = [(table, 'tenant_id', 'varchar', None, None, 'NO', 20, 'varchar(20)', 'utf8mb4_unicode_ci', None, '', ''),
                            (table, 'stock_quantity', 'decimal', 18, 4, 'NO', None, 'decimal(18,4)', None, None, '', '')]
                    if duplicate_column: rows.append(rows[-1])
                    return rows
                if 'information_schema.STATISTICS' in self.sql:
                    return [('wm_material_stock', 'PRIMARY', 0, 1, 'stock_id', None, 'BTREE')]
                return []
        return Cursor()

    def test_read_only_parameterized_queries_keep_native_column_and_unique_key_attributes(self):
        cursor = self.cursor()
        metadata, indexes = self.m._read_schema_metadata(cursor, {'ry-erp': ('wm_material_stock',)})
        key = 'ry-erp.wm_material_stock'
        self.assertEqual('InnoDB', metadata[key]['engine'])
        self.assertEqual({'type':'varchar', 'precision':None, 'scale':None, 'nullable':'NO',
                          'characterMaximumLength':20, 'columnType':'varchar(20)', 'collation':'utf8mb4_unicode_ci',
                          'datetimePrecision':None, 'extra':'', 'generationExpression':''}, metadata[key]['columns']['tenant_id'])
        self.assertEqual([('PRIMARY', 0, 1, 'stock_id', None, 'BTREE')], indexes[key])
        self.assertEqual(3, len(cursor.calls))
        for sql, params in cursor.calls:
            self.assertTrue(sql.startswith('SELECT '))
            self.assertIn('information_schema.', sql)
            self.assertEqual(('ry-erp', 'wm_material_stock'), params)
        self.assertIn('IS_NULLABLE', cursor.calls[1][0])
        self.assertIn('CHARACTER_MAXIMUM_LENGTH', cursor.calls[1][0])
        self.assertIn('COLUMN_TYPE', cursor.calls[1][0])

    def test_duplicate_or_unexpected_column_metadata_is_not_silently_accepted(self):
        for args in ({'duplicate_column':True}, {'unexpected_table':True}):
            with self.subTest(args=args), self.assertRaises(self.m.PreflightBlocked):
                self.m._read_schema_metadata(self.cursor(**args), {'ry-erp': ('wm_material_stock',)})

    def test_unapproved_schema_or_unsafe_table_name_is_rejected_before_query(self):
        for groups in ({'unknown': ('wm_material_stock',)}, {'ry-erp': ('stock;DROP',)}):
            cursor = self.cursor()
            with self.subTest(groups=groups), self.assertRaises(self.m.PreflightBlocked):
                self.m._read_schema_metadata(cursor, groups)
            self.assertEqual([], cursor.calls)


if __name__ == '__main__':
    unittest.main()
