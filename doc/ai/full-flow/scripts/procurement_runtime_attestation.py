"""可执行的只读已加载运行时权威校验器；绝不授予业务写入权限。

信任根是正在运行的本机回环后端返回的新鲜且经过认证的响应，而不是
可编辑的证据文件、git status、JVM 类路径、调用方标志或磁盘时间。
构建度量契约刻意采用比标准 actuator info 更严格的要求。
缺少该后端契约时，默认具体实现仍明确处于 BLOCKED 状态。
"""
from __future__ import annotations
from copy import deepcopy
from datetime import datetime, timezone
import re
import secrets
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request

from procurement_runtime_preflight import (Credentials, LocalSettings, PreflightBlocked,
    ROOT, RuntimeAuthority, RuntimeTarget, SCHEMAS, authentication_evidence, digest,
    local_opener, process_evidence, ReadOnlyClient, request_document, settings_from_files, strict_id,
    diagnostic_category, configuration_value, parse_config_text, _flatten, LOCAL_TIMEZONE)

ATTESTATION_PATH = '/system/runtime/attestation'
MAX_AGE_SECONDS = 30
CLOCK_SKEW_SECONDS = 2
REQUIRED_CLASSES = (
    'com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService',
    'com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard',
    'com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService',
    'com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService',
    'com.nexus.erp.pu.service.impl.PuPurchaseOrderServiceImpl',
    'com.nexus.erp.wm.service.impl.WmMaterialReceiveCrudServiceImpl',
    'com.nexus.erp.wm.service.impl.WmMaterialReceiveExecutorServiceImpl',
    'com.nexus.erp.mq.consumer.StockInConsumer',
    'com.nexus.erp.wm.service.impl.WmMqSendService',
    'com.nexus.erp.mq.message.StockInMessage',
    'com.nexus.erp.mq.consumer.PurchaseReceiveWritebackConsumer',
    'com.nexus.common.mq.consumer.BaseMessageConsumer',
)
ATTESTATION_CATEGORIES = frozenset({
    'ATTESTATION_ENDPOINT_MISSING','ATTESTATION_CONTRACT_UNAVAILABLE','ATTESTATION_REJECTED',
    'ATTESTATION_STALE','ATTESTATION_CHALLENGE_MISMATCH','AUTH_CONTEXT_MISMATCH',
    'AUTH_CONTEXT_UNVERIFIED','RUNTIME_PROCESS_UNVERIFIED','RUNTIME_RESTARTED',
    'RUNTIME_PROFILE_MISMATCH','RUNTIME_OVERRIDES_UNVERIFIED','LOADED_BUILD_UNVERIFIED',
    'LOADED_BUILD_MISMATCH','RUNTIME_DATASOURCE_MISMATCH','LIVE_DATABASE_UNVERIFIED',
    'DEPENDENCY_MISSING','AUTH_FAILED','READ_PERMISSION_DENIED','CONNECTION_FAILED',
    'CONNECTION_REFUSED','CONNECTION_TIMEOUT','SCHEMA_MISSING','TARGET_MISMATCH',
    'READONLY_CLEANUP_FAILED','ENCRYPTED_CONFIG_UNRESOLVED','ENCRYPTED_CONFIG_UNSUPPORTED',
    'ENCRYPTED_CONFIG_DECRYPTION_FAILED',
})


def _require(condition, category):
    if not condition:
        raise PreflightBlocked('runtime attestation rejected; details suppressed', category=category)


def _hash(value):
    return isinstance(value,str) and re.fullmatch(r'[0-9a-f]{64}',value) is not None


def _time(value):
    _require(isinstance(value,str) and len(value)<=64,'ATTESTATION_STALE')
    try:
        # Python 3.10 仅接受 3 位或 6 位小数；CIM 输出 7 位。
        normalized=re.sub(r'(\.\d{6})\d{1,3}(?=[+-]\d{2}:\d{2}$)',r'\1',value.replace('Z','+00:00'))
        result=datetime.fromisoformat(normalized)
        _require(result.tzinfo is not None and result.utcoffset() is not None,'ATTESTATION_STALE')
        return result
    except (ValueError, TypeError):
        raise PreflightBlocked('runtime timestamp unavailable; details suppressed',
                               category='ATTESTATION_STALE') from None


def _fresh(value, lower, upper):
    timestamp=_time(value)
    _require(-CLOCK_SKEW_SECONDS <= (timestamp-lower).total_seconds()
             and (timestamp-upper).total_seconds() <= CLOCK_SKEW_SECONDS
             and (upper-timestamp).total_seconds() <= MAX_AGE_SECONDS,'ATTESTATION_STALE')
    return timestamp


def _listener(probe):
    _require(isinstance(probe,dict),'RUNTIME_PROCESS_UNVERIFIED')
    identities=set()
    for item in probe.get('listeners',[]):
        if isinstance(item,dict) and item.get('port')==8080:
            _require(type(item.get('pid')) is int and item['pid']>0
                     and str(item.get('name','')).lower() in ('java.exe','javaw.exe','java'),
                     'RUNTIME_PROCESS_UNVERIFIED')
            identities.add((item['pid'],_time(item.get('startedAt'))))
    _require(len(identities)==1,'RUNTIME_PROCESS_UNVERIFIED')
    return next(iter(identities))


def live_database_identities(settings, *, connector=None):
    """使用每个已配置数据源自身的凭据读取，并通过实际 SELECT 查询获取其身份。

    配置仅用于选择获准尝试的本机回环连接。DATABASE、
    @@port 和 @@server_uuid 来自实际连接，而非其 JDBC URL。
    不执行 DDL、业务 DML 或提交；清理失败将阻断权威校验。
    """
    _require(isinstance(settings,LocalSettings),'LIVE_DATABASE_UNVERIFIED')
    settings.validate()
    _require(set(settings.datasources)==set(SCHEMAS),'LIVE_DATABASE_UNVERIFIED')
    if connector is None:
        try:
            import pymysql
            connector=pymysql.connect
        except ImportError:
            raise PreflightBlocked('read-only database driver unavailable',category='DEPENDENCY_MISSING') from None
    result={}
    for name,schema in SCHEMAS.items():
        connection=None
        try:
            datasource=settings.datasources[name]
            connection=connector(host='127.0.0.1',port=3306,user=datasource['username'],
                                 password=datasource['password'],database=schema,autocommit=False,
                                 connect_timeout=5,read_timeout=10,write_timeout=10)
            with connection.cursor() as cursor:
                cursor.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
                cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                cursor.execute('SELECT @@port,@@server_uuid,DATABASE()')
                port,uuid,actual_schema=cursor.fetchone()
                _require(type(port) is int and port==3306 and actual_schema==schema
                         and isinstance(uuid,str) and re.fullmatch(r'[0-9a-fA-F-]{36}',uuid) is not None,
                         'TARGET_MISMATCH')
                result[name]={'port':port,'schema':actual_schema,'serverUuidSha256':digest(uuid)}
        except Exception as error:
            category=diagnostic_category(error)
            raise PreflightBlocked('live read-only datasource identity unavailable; details suppressed',
                category=category if isinstance(category,str) and category in ATTESTATION_CATEGORIES else 'LIVE_DATABASE_UNVERIFIED') from None
        finally:
            failed=False
            if connection is not None:
                for cleanup in (connection.rollback,connection.close):
                    try:cleanup()
                    except Exception:failed=True
            if failed:
                raise PreflightBlocked('read-only connection cleanup unverified',
                                       category='READONLY_CLEANUP_FAILED') from None
    return result



def management_metadata_evidence(*, root=ROOT, config_dir=None, opener=None, environ=None):
    """读取现有受 Basic 认证保护的 actuator info/env；绝不将元数据当作证明。

    即使 actuator 被配置为公开这些值，也不返回
    env/configprops/凭据的值。仅凭 GitProperties 不能证明已加载的类。
    """
    result={'status':'BLOCKED','notLoadedArtifactProof':True,'businessWriteAuthorized':False,
            'businessWrites':0,'apiReadRequests':0}
    try:
        import base64
        root=Path(root).resolve()
        approved=(root/'ruoyi-admin/src/main/resources',root/'ruoyi-admin/target/classes')
        base=Path(config_dir).resolve() if config_dir else (approved[1] if (approved[1]/'application.yml').is_file() else approved[0])
        _require(base in approved,'ATTESTATION_CONTRACT_UNAVAILABLE')
        context={}
        for name in ('application.yml','application-dev.yml','application-secret.yml'):
            path=base/name
            if not path.is_file():
                _require(name=='application-secret.yml','ATTESTATION_CONTRACT_UNAVAILABLE')
                continue
            _require(path.stat().st_size<=1_000_000,'ATTESTATION_CONTRACT_UNAVAILABLE')
            for document in parse_config_text(path.read_text(encoding='utf-8-sig')):
                context.update(_flatten(document))
        env=os.environ if environ is None else environ
        values=[]
        for key in ('spring.boot.admin.client.username','spring.boot.admin.client.password'):
            value=context.get(key)
            _require(isinstance(value,str) or type(value) is int,'ATTESTATION_CONTRACT_UNAVAILABLE')
            value=configuration_value(str(value),context,env)
            _require(0<len(value)<=4096 and not any(ord(c)<32 or ord(c)==127 for c in value),
                     'ATTESTATION_CONTRACT_UNAVAILABLE')
            values.append(value)
        _require(':' not in values[0],'ATTESTATION_CONTRACT_UNAVAILABLE')
        headers={'Authorization':'Basic '+base64.b64encode(':'.join(values).encode('utf-8')).decode('ascii'),
                 'Accept':'application/json'}
        documents={}
        for endpoint in ('info','env'):
            result['apiReadRequests']+=1
            document=request_document(Request('http://127.0.0.1:8080/actuator/'+endpoint,
                                      headers=headers,method='GET'),opener or local_opener(),attempts=1)
            _require(document.get('code') is None or type(document['code']) is int and document['code']==200,
                     'AUTH_CONTEXT_UNVERIFIED')
            documents[endpoint]=document
        profiles=documents['env'].get('activeProfiles')
        _require(isinstance(profiles,list) and len(profiles)<=16 and all(isinstance(v,str) for v in profiles),
                 'RUNTIME_PROFILE_MISMATCH')
        info=documents['info']
        result.update({'status':'OBSERVED','source':'existing_authenticated_management_endpoints',
            'observedAt':datetime.now(LOCAL_TIMEZONE).isoformat(),'managementCredentialPresent':True,
            'gitPresent':isinstance(info.get('git'),dict),'buildPresent':isinstance(info.get('build'),dict),
            'embeddedRuntimeManifestPresent':isinstance(info.get('nexusRuntime'),dict),
            'activeProfiles':profiles if set(profiles)<={'dev','local','prod','decision-demo','default'} else None,
            'activeProfileSetSha256':digest('\n'.join(sorted(profiles))),
            'propertyValuesNeverReported':True})
        return result
    except Exception as error:
        category=diagnostic_category(error)
        result['diagnosticCategory']=category if isinstance(category,str) and category in ATTESTATION_CATEGORIES else 'ATTESTATION_REJECTED'
        result['reason']='existing_actuator_metadata_unavailable_details_suppressed'
        return result


class BackendRuntimeAuthority(RuntimeAuthority):
    """默认的强制校验实现，绑定显式提供的现有凭据。

    构造或导入时不执行 I/O。每次 require_current 都会重新认证、
    签发新的 nonce 并读取运行中的后端契约，比较独立获取的
    实际数据源身份，随后重新检查监听器及身份主体。
    成功时仅返回 None，绝不是报告、布尔值或写入授权。
    仅测试使用的适配器替换的是 I/O 边界，而非提供 verified 或 apply 开关。
    """
    def __init__(self, *, credentials, settings=None, root=ROOT, config_dir=None,
                 opener=None, clock=None, process_probe=None, database_probe=None):
        _require(isinstance(credentials,Credentials),'AUTH_CONTEXT_UNVERIFIED')
        self._credentials=credentials.validate()
        self._settings=settings
        self._root,self._config_dir=root,config_dir
        self._opener=opener or local_opener()
        self._clock=clock or (lambda:datetime.now(timezone.utc))
        self._process_probe=process_probe or (lambda:process_evidence(self._root))
        self._database_probe=database_probe or self._live_databases
        self._evidence={'status':'NOT_OBSERVED','businessWriteAuthorized':False}

    def evidence(self):
        return deepcopy(self._evidence)

    def _live_databases(self):
        settings=self._settings or settings_from_files(self._root,config_dir=self._config_dir)
        return live_database_identities(settings)

    def _open_contract(self, request, timeout):
        try:return self._opener(request,timeout=timeout)
        except HTTPError as error:
            if error.code==404:
                raise PreflightBlocked('authenticated backend runtime contract endpoint missing',
                                       category='ATTESTATION_ENDPOINT_MISSING') from None
            raise

    def require_current(self, *, target, expected_head, tenant_id, principal_sha256):
        self._evidence={'status':'BLOCKED','businessWriteAuthorized':False,'endpoint':ATTESTATION_PATH}
        try:
            target.validate();strict_id(tenant_id,tenant=True)
            _require(isinstance(expected_head,str) and re.fullmatch(r'[0-9a-f]{40}',expected_head)
                     and _hash(principal_sha256),'LOADED_BUILD_UNVERIFIED')
            # 两个上下文均独立校验为唯一获准的本机 8080
            # 监听器；localhost/127.0.0.1 互为别名，并非不同环境。
            self._credentials.validate()
            _require(tenant_id==self._credentials.tenant_id,'AUTH_CONTEXT_MISMATCH')
            binding=self._credentials,self._opener
            before=self._clock();_require(isinstance(before,datetime) and before.tzinfo is not None,'ATTESTATION_STALE')
            process_before=_listener(self._process_probe())
            auth=authentication_evidence(ReadOnlyClient(target,self._credentials,opener=self._opener),self._credentials)
            _require(auth.get('status')=='VERIFIED','AUTH_CONTEXT_UNVERIFIED')
            _require(auth.get('principalSha256')==principal_sha256,'AUTH_CONTEXT_MISMATCH')
            nonce=secrets.token_hex(32)
            request=Request(target.base_url.rstrip('/')+ATTESTATION_PATH+'?'+urlencode({'challenge':nonce}),
                            headers=self._credentials.headers(),method='GET')
            document=request_document(request,self._open_contract,attempts=1)
            _require(type(document.get('code')) is int and document['code']==200,
                     'ATTESTATION_ENDPOINT_MISSING' if document.get('code')==404 else 'ATTESTATION_CONTRACT_UNAVAILABLE')
            data=document.get('data');_require(isinstance(data,dict),'ATTESTATION_CONTRACT_UNAVAILABLE')
            _require(type(data.get('schemaVersion')) is int and data['schemaVersion']==1,'ATTESTATION_CONTRACT_UNAVAILABLE')
            _require(data.get('challengeSha256')==digest(nonce),'ATTESTATION_CHALLENGE_MISMATCH')
            _require(data.get('tenantSha256')==digest(tenant_id) and data.get('principalSha256')==principal_sha256,
                     'AUTH_CONTEXT_MISMATCH')
            after=self._clock();_fresh(data.get('observedAt'),before,after)
            _require(0 <= (after-before).total_seconds() <= MAX_AGE_SECONDS,'ATTESTATION_STALE')
            runtime=data.get('runtime');_require(isinstance(runtime,dict),'RUNTIME_PROCESS_UNVERIFIED')
            _require(type(runtime.get('pid')) is int and (runtime['pid'],_time(runtime.get('startedAt')))==process_before
                     and _hash(runtime.get('instanceSha256')),'RUNTIME_PROCESS_UNVERIFIED')
            _require(runtime.get('activeProfiles')==['dev'],'RUNTIME_PROFILE_MISMATCH')
            _require(runtime.get('measurementMode')=='IMMUTABLE_STARTUP_CLASS_BYTES'
                     and runtime.get('classLoading')=='STANDARD_IMMUTABLE' and runtime.get('runtimeOverrides')==[],
                     'RUNTIME_OVERRIDES_UNVERIFIED')
            build=data.get('build');_require(isinstance(build,dict),'LOADED_BUILD_UNVERIFIED')
            _require(build.get('gitCommit')==expected_head,'LOADED_BUILD_MISMATCH')
            _require(build.get('manifestSource')=='EMBEDDED_BUILD_MANIFEST'
                     and _hash(build.get('artifactSha256')) and _hash(build.get('manifestSha256')),
                     'LOADED_BUILD_UNVERIFIED')
            classes=build.get('classes');_require(isinstance(classes,dict),'LOADED_BUILD_UNVERIFIED')
            for name in REQUIRED_CLASSES:
                item=classes.get(name)
                _require(isinstance(item,dict) and _hash(item.get('sha256')) and _hash(item.get('originSha256'))
                         and item['sha256']==item.get('manifestClassSha256'),'LOADED_BUILD_UNVERIFIED')
            backend=data.get('datasources');actual=self._database_probe()
            _require(isinstance(backend,dict) and isinstance(actual,dict)
                     and set(backend)==set(actual)==set(SCHEMAS),'RUNTIME_DATASOURCE_MISMATCH')
            for name,schema in SCHEMAS.items():
                item=backend[name];local=actual[name]
                _require(isinstance(item,dict) and isinstance(local,dict)
                         and item.get('identitySource')=='LIVE_JDBC_METADATA_SELECT'
                         and item.get('host') in ('localhost','127.0.0.1') and type(item.get('port')) is int
                         and item['port']==local.get('port')==3306 and item.get('schema')==local.get('schema')==schema
                         and _hash(item.get('serverUuidSha256'))
                         and item['serverUuidSha256']==local.get('serverUuidSha256'),'RUNTIME_DATASOURCE_MISMATCH')
                _fresh(item.get('observedAt'),before,self._clock())
            _require(_listener(self._process_probe())==process_before,'RUNTIME_RESTARTED')
            auth_after=authentication_evidence(ReadOnlyClient(target,self._credentials,opener=self._opener),self._credentials)
            _require(auth_after.get('status')=='VERIFIED' and auth_after.get('principalSha256')==principal_sha256,
                     'AUTH_CONTEXT_MISMATCH')
            _require((self._credentials,self._opener)==binding,'AUTH_CONTEXT_MISMATCH')
            observed=self._clock();_fresh(data['observedAt'],before,observed)
            self._evidence={'status':'VERIFIED','businessWriteAuthorized':False,
                'source':'authenticated_backend_loaded_manifest_and_live_database_identity',
                'observedAt':observed.astimezone(LOCAL_TIMEZONE).isoformat(),'endpoint':ATTESTATION_PATH,
                'principalSha256':principal_sha256,'tenantSha256':digest(tenant_id),
                'gitCommit':expected_head,'artifactSha256':build['artifactSha256'],
                'manifestSha256':build['manifestSha256'],'loadedClassCount':len(REQUIRED_CLASSES),
                'processPid':process_before[0],'processStartedAt':process_before[1].isoformat(),
                'instanceSha256':runtime['instanceSha256'],'activeProfiles':['dev'],
                'datasourceIdentitySha256':digest(str(sorted((name,item['schema'],item['serverUuidSha256'])
                                                            for name,item in actual.items())))}
            return None
        except Exception as error:
            category=diagnostic_category(error)
            category=category if isinstance(category,str) and category in ATTESTATION_CATEGORIES else 'ATTESTATION_REJECTED'
            self._evidence.update({'diagnosticCategory':category,'reason':'authenticated_runtime_authority_not_established'})
            raise PreflightBlocked('loaded runtime authority rejected; details suppressed',category=category) from None
