"""认证绑定的运行时权威入口；签名发布和真实 premain 见证仍不能代替独占链证明。

固定外部根与回调/成功 Class 身份由进程内 agent 核验，HTTP JSON 只是传输载体。
JDK 公共 API 无法认证全部 Java/native transformer 及未来注册禁令，当前没有可
诚实签发最终加载字节证明的域。无论响应声称什么 verified 标志，均保持 BLOCKED；
不得用 Git HEAD、磁盘摘要、资源内容或后端自报标志升级为业务写入前置证明。
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
# 空集合明确表示尚无可信独占链签发域，不能通过参数或配置增补。
SUPPORTED_PROOF_DOMAINS = frozenset()
MAX_AGE_SECONDS = 30
CLOCK_SKEW_SECONDS = 2
BOUNDARY_CONTRACT = 'PROCUREMENT_RECEIPT_IQC_BIRTH_V2'
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
    'com.nexus.erp.wm.service.WmReceiptIqcBirthService',
    'com.nexus.mes.qc.service.QcReceiptIqcBirthService',
    'com.nexus.erp.wm.service.WmReceiptIqcEvidenceService',
    'com.nexus.mes.qc.service.QcReceiptIqcEvidenceService',
    'com.nexus.erp.wm.service.WmCertifiedReceiptPostingService',
    'com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard',
    'com.nexus.erp.wm.service.PdaReceiveShelfAllocationService',
    'com.nexus.erp.wm.service.impl.PdaReceiveServiceImpl',
    'com.nexus.erp.mq.consumer.ErpIqcCompletedConsumer',
    'com.nexus.mes.common.mq.consumer.IqcAutoCreateConsumer',
    'com.nexus.common.mq.iqc.ReceiptIqcBirthSupport',
    'com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService',
    'com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService',
)
REQUIRED_CLASS_SET_SHA256 = digest('\n'.join(sorted(REQUIRED_CLASSES)) + '\n')
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
    'ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN','ATTESTATION_TRUST_ROOT_UNAVAILABLE',
    'ATTESTATION_RELEASE_SIGNATURE_INVALID','ATTESTATION_RELEASE_MANIFEST_INVALID',
    'ATTESTATION_LAUNCH_MODE_UNSUPPORTED','ATTESTATION_REQUIRED_CLASS_NOT_LOADED',
    'ATTESTATION_CALLBACK_BYTES_MISMATCH','ATTESTATION_CLASS_LOADER_UNSUPPORTED',
    'ATTESTATION_REDEFINITION_UNSUPPORTED','ATTESTATION_PACKAGING_UNSUPPORTED',
    'ATTESTATION_RELEASE_INPUT_CHANGED','ATTESTATION_INSTRUMENTATION_UNPROVEN',
    'ATTESTATION_DUPLICATE_AGENT_UNSUPPORTED','ATTESTATION_PREMAIN_TOO_LATE',
    'ATTESTATION_IMMUTABLE_FILES_UNPROVEN','ATTESTATION_AGENT_STARTUP_FAILED',
    'ATTESTATION_AGENT_ABSENT','ATTESTATION_BUILD_UNPROVEN',
    'ATTESTATION_BOUNDARY_CONTRACT_UNSUPPORTED','ATTESTATION_BOUNDARY_CLASS_SET_INCOMPLETE',
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


def _process_started_at_millis(value):
    # 仅接受 OS 创建时间的带偏移格式；观测时间的新鲜度容忍窗不参与进程绑定。
    _require(type(value) is str and len(value)<=64,'RUNTIME_PROCESS_UNVERIFIED')
    match=re.fullmatch(r'([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2})'
                      r'(?:\.([0-9]{1,9}))?(Z|[+-][0-9]{2}:[0-9]{2})',value)
    _require(match is not None,'RUNTIME_PROCESS_UNVERIFIED')
    prefix,fraction,offset=match.groups()
    _require(offset=='Z' or (int(offset[1:3])<24 and int(offset[4:])<60),
             'RUNTIME_PROCESS_UNVERIFIED')
    # 截取微秒只为兼容 Python 3.10，随后用整数绑定本机 ProcessHandle 的毫秒精度。
    normalized=prefix+('.'+(fraction+'000000')[:6] if fraction else '')+offset
    try:
        elapsed=_time(normalized)-datetime(1970,1,1,tzinfo=timezone.utc)
    except PreflightBlocked:
        raise PreflightBlocked('process creation time unavailable; details suppressed',
                               category='RUNTIME_PROCESS_UNVERIFIED') from None
    # 这是共同精度的规范化，不四舍五入、不容忍相邻毫秒；负 epoch 也向下取整。
    return elapsed.days*86_400_000+elapsed.seconds*1000+elapsed.microseconds//1000


def _listener(probe):
    _require(isinstance(probe,dict),'RUNTIME_PROCESS_UNVERIFIED')
    identities=set()
    for item in probe.get('listeners',[]):
        if isinstance(item,dict) and item.get('port')==8080:
            _require(type(item.get('pid')) is int and item['pid']>0
                     and str(item.get('name','')).lower() in ('java.exe','javaw.exe','java'),
                     'RUNTIME_PROCESS_UNVERIFIED')
            identities.add((item['pid'],_process_started_at_millis(item.get('startedAt'))))
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
    生成新的 nonce 并读取运行中的后端契约，再次检查监听器和身份主体。
    目前所有独占链域均不受支持，因此任何响应都不能返回成功；尚未有可
    验证的加载证明时不借用数据库连接。独立数据库身份读取函数仍供只读
    核验使用，不以其成功替代加载证明。expected_head 仅保留接口兼容性，
    不再把相等的源码提交视为已加载证据。测试适配器只替换 I/O 边界。
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
            _require(type(document.get('code')) is int and document['code'] in (200,412),
                     'ATTESTATION_ENDPOINT_MISSING' if document.get('code')==404 else 'ATTESTATION_CONTRACT_UNAVAILABLE')
            data=document.get('data');_require(isinstance(data,dict),'ATTESTATION_CONTRACT_UNAVAILABLE')
            _require(type(data.get('schemaVersion')) is int and data['schemaVersion']==2,'ATTESTATION_CONTRACT_UNAVAILABLE')
            _require(data.get('challengeSha256')==digest(nonce),'ATTESTATION_CHALLENGE_MISMATCH')
            _require(data.get('tenantSha256')==digest(tenant_id) and data.get('principalSha256')==principal_sha256,
                     'AUTH_CONTEXT_MISMATCH')
            after=self._clock();_fresh(data.get('observedAt'),before,after)
            _require(0 <= (after-before).total_seconds() <= MAX_AGE_SECONDS,'ATTESTATION_STALE')
            runtime=data.get('runtime');_require(isinstance(runtime,dict),'RUNTIME_PROCESS_UNVERIFIED')
            _require(type(runtime.get('pid')) is int and (runtime['pid'],_process_started_at_millis(runtime.get('startedAt')))==process_before
                     and _hash(runtime.get('instanceSha256')),'RUNTIME_PROCESS_UNVERIFIED')
            _require(runtime.get('activeProfiles')==['dev'],'RUNTIME_PROFILE_MISMATCH')
            build=data.get('build');_require(isinstance(build,dict),'LOADED_BUILD_UNVERIFIED')
            _require(build.get('boundaryContract')==BOUNDARY_CONTRACT
                     and build.get('requiredClassSetSha256')==REQUIRED_CLASS_SET_SHA256,
                     'ATTESTATION_BOUNDARY_CONTRACT_UNSUPPORTED')
            _require(_listener(self._process_probe())==process_before,'RUNTIME_RESTARTED')
            auth_after=authentication_evidence(ReadOnlyClient(target,self._credentials,opener=self._opener),self._credentials)
            _require(auth_after.get('status')=='VERIFIED' and auth_after.get('principalSha256')==principal_sha256,
                     'AUTH_CONTEXT_MISMATCH')
            _require((self._credentials,self._opener)==binding,'AUTH_CONTEXT_MISMATCH')
            _fresh(data['observedAt'],before,self._clock())
            witness=build.get('witness')
            reported_state=witness.get('state') if isinstance(witness,dict) else 'ABSENT'
            if reported_state not in {'ABSENT','DISABLED','INVALIDATED','PENDING_DEFINITIONS','LOAD_IDENTITY_CONFIRMED'}:
                reported_state='UNSUPPORTED'
            self._evidence.update({'supportedProofDomain':'NONE','reportedWitnessState':reported_state})
            # 回调与真实 Class 身份是有限见证；后续 transformer 可在同一 JVM 改写字节。
            # 没有外部强制独占链的实现，不能预留一个仅靠 JSON 名称/标志启用的成功路径。
            category=data.get('diagnosticCategory') if document['code']==412 or data.get('status')=='BLOCKED' else None
            category=category if category in ATTESTATION_CATEGORIES else 'LOADED_BUILD_UNVERIFIED'
            if category=='LOADED_BUILD_UNVERIFIED' and reported_state=='LOAD_IDENTITY_CONFIRMED' and build.get('supportedProofDomain')=='NONE':
                category='ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN'
            raise PreflightBlocked('exclusive loaded-byte authority has no supported trusted issuer',category=category)
        except Exception as error:
            category=diagnostic_category(error)
            category=category if isinstance(category,str) and category in ATTESTATION_CATEGORIES else 'ATTESTATION_REJECTED'
            self._evidence.update({'diagnosticCategory':category,'reason':'authenticated_runtime_authority_not_established'})
            raise PreflightBlocked('loaded runtime authority rejected; details suppressed',category=category) from None
