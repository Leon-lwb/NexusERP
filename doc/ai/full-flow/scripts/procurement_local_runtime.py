"""本地开发的服务/进程/数据源一致性检查，不要求正式签发或最终JVM字节证明。

只适用于已获授权的loopback dev测试环境。IDE和调试agent可用；Git与磁盘编译
产物只是上下文/诊断。业务来源、权限、数量、事务和幂等仍由实际写入口校验。
"""
from __future__ import annotations
from copy import deepcopy
from datetime import datetime,timezone
import re,secrets
from urllib.parse import urlencode
from urllib.request import Request

from procurement_runtime_preflight import (ROOT,Credentials,LocalSettings,PreflightBlocked,RuntimeAuthority,SCHEMAS,
    ReadOnlyClient,authentication_evidence,diagnostic_category,digest,local_opener,process_evidence,
    request_document,settings_from_files,strict_id)
from procurement_runtime_attestation import (ATTESTATION_PATH,ATTESTATION_CATEGORIES,BackendRuntimeAuthority,
    MAX_AGE_SECONDS,_fresh,_hash,_listener,_process_started_at_millis,live_database_identities)

LOCAL_RUNTIME_CATEGORIES=frozenset({'RUNTIME_CHECK_MODE_UNSUPPORTED','LOCAL_RUNTIME_CHECK_REJECTED',
    'LOCAL_RUNTIME_DIAGNOSTIC_UNAVAILABLE'})
RUNTIME_CHECK_MODES=('local-dev','strict')


def check(condition,category):
    if not condition:raise PreflightBlocked('本地运行检查未通过，详细内容已抑制',category=category)


class LocalDevelopmentRuntimeAuthority(RuntimeAuthority):
    """在本地受信开发假设下核对真正运行目标，不把普通诊断伪装成生产证书。"""
    def __init__(self,*,credentials,settings=None,root=ROOT,config_dir=None,opener=None,clock=None,
                 process_probe=None,database_probe=None):
        check(isinstance(credentials,Credentials),'AUTH_CONTEXT_UNVERIFIED')
        credentials.validate()
        check(settings is None or isinstance(settings,LocalSettings),'LIVE_DATABASE_UNVERIFIED')
        self._credentials=credentials;self._settings=settings;self._root=root;self._config_dir=config_dir
        self._opener=opener or local_opener();self._clock=clock or (lambda:datetime.now(timezone.utc))
        self._process_probe=process_probe or (lambda:process_evidence(self._root))
        self._database_probe=database_probe or self._live_databases
        self._evidence=self._empty_evidence('NOT_OBSERVED')
    @staticmethod
    def _empty_evidence(status):
        return {'status':status,'runtimeCheckMode':'local-dev','businessWriteAuthorized':False,
            'productionProof':False,'finalClassBytesProven':False,'artifactSnapshotsAreLoadedProof':False,
            'expectedCommitBinding':'WORKSPACE_CONTEXT_ONLY'}
    def evidence(self):return deepcopy(self._evidence)
    def _live_databases(self):
        settings=self._settings or settings_from_files(self._root,config_dir=self._config_dir)
        return live_database_identities(settings)
    def require_current(self,*,target,expected_head,tenant_id,principal_sha256):
        self._evidence=self._empty_evidence('BLOCKED')
        try:
            target.validate();strict_id(tenant_id,tenant=True)
            check(isinstance(expected_head,str) and re.fullmatch('[0-9a-f]{40}',expected_head) and _hash(principal_sha256),
                'AUTH_CONTEXT_UNVERIFIED')
            self._credentials.validate();check(tenant_id==self._credentials.tenant_id,'AUTH_CONTEXT_MISMATCH')
            binding=self._credentials,self._opener,self._process_probe,self._database_probe
            before=self._clock();check(isinstance(before,datetime) and before.tzinfo is not None,'ATTESTATION_STALE')
            process=self._process_probe();listener=_listener(process)
            auth=authentication_evidence(ReadOnlyClient(target,self._credentials,opener=self._opener),self._credentials)
            check(auth.get('status')=='VERIFIED' and auth.get('principalSha256')==principal_sha256,'AUTH_CONTEXT_UNVERIFIED')
            challenge=secrets.token_hex(32)
            request=Request(target.base_url.rstrip('/')+ATTESTATION_PATH+'?'+urlencode({'challenge':challenge}),
                headers=self._credentials.headers(),method='GET')
            document=request_document(request,self._opener,attempts=1)
            check(type(document.get('code'))is int and document['code']in (200,412),'LOCAL_RUNTIME_DIAGNOSTIC_UNAVAILABLE')
            data=document.get('data');check(isinstance(data,dict) and type(data.get('schemaVersion'))is int and
                data['schemaVersion']==2,'LOCAL_RUNTIME_DIAGNOSTIC_UNAVAILABLE')
            check(data.get('challengeSha256')==digest(challenge),'ATTESTATION_CHALLENGE_MISMATCH')
            check(data.get('principalSha256')==principal_sha256 and data.get('tenantSha256')==digest(tenant_id),'AUTH_CONTEXT_MISMATCH')
            runtime=data.get('runtime');check(isinstance(runtime,dict),'RUNTIME_PROCESS_UNVERIFIED')
            check(type(runtime.get('pid'))is int and (runtime['pid'],_process_started_at_millis(runtime.get('startedAt')))==listener,
                'RUNTIME_PROCESS_UNVERIFIED')
            check(runtime.get('activeProfiles')==['dev'],'RUNTIME_PROFILE_MISMATCH')
            after=self._clock();_fresh(data.get('observedAt'),before,after)
            check(data.get('datasourceObservationStatus')=='OBSERVED','RUNTIME_DATASOURCE_MISMATCH')
            reported=data.get('datasources');check(isinstance(reported,dict) and set(reported)==set(SCHEMAS),'RUNTIME_DATASOURCE_MISMATCH')
            actual=self._database_probe();check(isinstance(actual,dict) and set(actual)==set(SCHEMAS),'LIVE_DATABASE_UNVERIFIED')
            identities={}
            for name,schema in SCHEMAS.items():
                item=reported[name];observed=actual[name]
                check(isinstance(item,dict) and isinstance(observed,dict),'RUNTIME_DATASOURCE_MISMATCH')
                check(item.get('identitySource')=='LIVE_JDBC_METADATA_SELECT','RUNTIME_DATASOURCE_MISMATCH')
                check(type(item.get('port'))is int and item['port']==3306 and item.get('schema')==schema and
                    _hash(item.get('serverUuidSha256')),'RUNTIME_DATASOURCE_MISMATCH')
                check(type(observed.get('port'))is int and observed['port']==3306 and observed.get('schema')==schema and
                    _hash(observed.get('serverUuidSha256')),'LIVE_DATABASE_UNVERIFIED')
                check(item['serverUuidSha256']==observed['serverUuidSha256'],'RUNTIME_DATASOURCE_MISMATCH')
                _fresh(item.get('observedAt'),before,self._clock())
                identities[name]={'port':3306,'schema':schema,'serverUuidSha256':observed['serverUuidSha256']}
            auth_after=authentication_evidence(ReadOnlyClient(target,self._credentials,opener=self._opener),self._credentials)
            check(auth_after.get('status')=='VERIFIED' and auth_after.get('principalSha256')==principal_sha256,'AUTH_CONTEXT_MISMATCH')
            check((self._credentials,self._opener,self._process_probe,self._database_probe)==binding,'AUTH_CONTEXT_MISMATCH')
            # 最后一次网络认证结束后再核进程，避免把请求期间重启的新服务当成旧观测。
            check(_listener(self._process_probe())==listener,'RUNTIME_RESTARTED')
            final=self._clock();_fresh(data['observedAt'],before,final)
            check(0<=(final-before).total_seconds()<=MAX_AGE_SECONDS,'ATTESTATION_STALE')
            warnings=['STRONG_SIGNING_NOT_REQUIRED_FOR_LOCAL_DEV','BUILD_ARTIFACTS_ARE_DIAGNOSTICS_NOT_FINAL_BYTES']
            snapshots=process.get('targetSnapshots',{})
            if not isinstance(snapshots,dict):snapshots={}
            for snapshot in snapshots.values():
                if not isinstance(snapshot,dict) or not isinstance(snapshot.get('mtime'),str):continue
                try:artifact_time=_process_started_at_millis(snapshot['mtime'])
                except PreflightBlocked:continue
                if artifact_time>listener[1]:warnings.append('ARTIFACT_NEWER_THAN_PROCESS_REQUIRES_API_RECHECK');break
            self._evidence.update(status='LOCAL_DEV_CHECKED',source='local_development_service_and_datasource_consistency',
                observedAt=final.isoformat(),runtime={'pid':listener[0],'startedAtEpochMillis':listener[1],'activeProfiles':['dev']},
                datasources=identities,workspaceHead=expected_head,artifactSnapshotCount=len(snapshots),warnings=warnings,
                requiresBusinessSourceAndWriteAuthority=True)
            return None
        except Exception as error:
            category=diagnostic_category(error)
            category=category if category in ATTESTATION_CATEGORIES|LOCAL_RUNTIME_CATEGORIES else 'LOCAL_RUNTIME_CHECK_REJECTED'
            self._evidence.update(diagnosticCategory=category)
            raise PreflightBlocked('本地运行检查失败，详细内容已抑制',category=category) from None


def create_runtime_authority(*,mode='local-dev',credentials,**kwargs):
    """本地默认普通检查；高保证严格诊断必须显式选择，二者不混淆。"""
    check(type(mode)is str and mode in RUNTIME_CHECK_MODES,'RUNTIME_CHECK_MODE_UNSUPPORTED')
    if mode=='strict':return BackendRuntimeAuthority(credentials=credentials,**kwargs)
    return LocalDevelopmentRuntimeAuthority(credentials=credentials,**kwargs)
