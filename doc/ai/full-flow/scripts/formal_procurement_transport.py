"""可审计的采购传输通道；导入或构造时绝不执行写入。

不提供执行命令行入口，不执行登录、SQL、库存数据植入或审批身份冒用，也不提供布尔型
写入开关。主集成必须提供独立的强制校验机制
以及 API 与数据库/来源回读适配器。HTTP 成功（通常为 R<Void>）不代表业务
完成。日志只包含摘要，绝不包含令牌、请求体或业务 ID。
读取重试有次数限制；写入只发送一次。不确定的命令须先恢复核验；显式重放要求幂等性已获证明。
"""
from __future__ import annotations
from abc import ABC, abstractmethod
from contextlib import contextmanager
from copy import deepcopy
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import threading
from urllib.parse import urlencode
from urllib.request import Request

from procurement_runtime_preflight import (PRIVATE_ROOT as _PRIVATE_ROOT, Credentials, PreflightBlocked,
    RuntimeAuthority, RuntimeTarget, ReadOnlyClient, authentication_evidence, exact_decimal,
    local_opener, request_document, response_id_strings, safe_path, strict_id)

from procurement_runtime_attestation import BackendRuntimeAuthority, ATTESTATION_CATEGORIES

_DEFAULT_RUNTIME = object()
PRIVATE_ROOT = _PRIVATE_ROOT

class TransportBlocked(RuntimeError):
    """固定诊断信息；不暴露原始 HTTP 信息、凭据或适配器错误。"""
    def __init__(self,message,*,category='UNVERIFIED'):
        super().__init__(message)
        self.category=category

def require(condition, message):
    if not condition:
        raise TransportBlocked(message)

def _json(value):
    def encode(item):
        if isinstance(item, Decimal):
            return format(exact_decimal(item), 'f')
        raise TransportBlocked('unsupported JSON value')
    try:
        return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False,
                          default=encode, allow_nan=False)
    except (ValueError, TypeError, RecursionError, PreflightBlocked):
        raise TransportBlocked('invalid request JSON; contents suppressed') from None

def _sha(value):
    return hashlib.sha256(value.encode('utf-8')).hexdigest()

def _hash(value):
    return isinstance(value, str) and re.fullmatch(r'[0-9a-f]{64}', value) is not None

def request_digest(request):
    return _sha(_json(request))

@dataclass(frozen=True)
class CertifiedIdentity:
    request_key: str = field(repr=False)
    material_id: str = field(repr=False)
    vendor_id: str = field(repr=False)
    window_end: str
    requirement_detail_ids: tuple[str,...] = field(repr=False)
    expected_source_fingerprint: str

    @classmethod
    def from_body(cls,body):
        return cls(body['requestKey'],body['materialId'],body['vendorId'],body['windowEnd'],
                   tuple(body['requirementDetailIds']),body['expectedSourceFingerprint'])

@dataclass(frozen=True)
class ReceiptIdentity:
    """仅绑定路径中的真实收货身份，不接收客户端数量、单位或出生结论。"""
    receive_id: str = field(repr=False)

@dataclass(frozen=True)
class WriteScope:
    tenant_id: str = field(repr=False)
    intent_id: str = field(repr=False)
    request_sha256: str
    principal_sha256: str
    target_sha256: str
    expected_head: str
    action: str
    retry: bool
    command: CertifiedIdentity | ReceiptIdentity | None = field(default=None,repr=False)

    @property
    def intent_sha256(self):
        return _sha(self.target_sha256 + '\0' + self.tenant_id + '\0' + self.intent_id)

class WriteAuthority(ABC):
    """独立、限时且绑定精确请求的权限；绝不由任务 C 签发。

    强制校验传输通道/身份主体、目标、租户、意图、精确请求哈希、
    操作及有效期。apply/verified 标志或可编辑的 JSON 均不构成授权。
    重试时还须证明原始请求的处理状态已最终确定，以及经证明的已加载构建中服务端操作的
    幂等性；不能仅以数据库中不存在某行作为证明。
    """
    @abstractmethod
    def require_authorized(self, *, transport, scope):
        raise TransportBlocked('independent exact-request business write authority required')

    def require_retry_safe(self, *, transport, scope):
        raise TransportBlocked('independent settled-request idempotency proof required')

class LifecycleAuthority(ABC):
    """主线程必须在每次操作或恢复前完成来源、写入方及生命周期的闭环核验。

    检查完整的租户范围基线、原始来源及明细数量、
    草稿/审批状态、真实操作人/Flowable 流程，以及适用的供应/收货/IQC/上架
    证据与幂等性/outbox 边界。不接受模型编造的
    价格、库存、到货、审批或生产事实。
    恢复必须重核已提交的永久出生/原流水，不以当前单位补源或按当前可用库存重算历史入库；
    首次动作与恢复须分别核验实际服务约束，不能把终态当成首次供给。
    任何缺口均须抛出异常；绝不能用包含空值或假定布尔值的字典代替核验。
    """
    @abstractmethod
    def require_closed(self, *, scope):
        raise TransportBlocked('independent source and lifecycle closure required')

@dataclass(frozen=True)
class RecoveryEvidence:
    tenant_id: str = field(repr=False)
    request_sha256: str
    entity_id: str = field(repr=False)
    server_number: str = field(repr=False)
    api_sha256: str
    db_sha256: str
    source_sha256: str
    source_fingerprint: str | None = None

    def _validate_common(self, scope):
        try:
            strict_id(self.tenant_id, tenant=True)
            strict_id(self.entity_id)
        except PreflightBlocked:
            raise TransportBlocked('invalid recovery identity') from None
        require(self.tenant_id == scope.tenant_id and self.request_sha256 == scope.request_sha256,
                'recovery tenant or original request mismatch')
        require(isinstance(self.server_number, str) and
                re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}', self.server_number) is not None,
                'independent server-generated number readback required')
        require(all(_hash(v) for v in (self.api_sha256,self.db_sha256,self.source_sha256)),
                'independent API/database/source readback digests required')
        return self

    def validate(self, scope):
        self._validate_common(scope)
        require(isinstance(scope.command,CertifiedIdentity) and _hash(self.source_fingerprint) and
                self.source_fingerprint==scope.command.expected_source_fingerprint,
                'persisted source fingerprint differs from frozen original command')
        return self

    def completed_for(self, scope):
        return True

@dataclass(frozen=True,kw_only=True)
class ReceiptRecoveryEvidence(RecoveryEvidence):
    """独立服务读回的原生证据摘要；不是客户端出生数据或写入授权。

    读取器须实际核验 ERP 永久出生登记、MES 同快照出生绑定及完整检验、V2 和原收货范围。
    FINISHED 还须核验永久过账与全量原流水；通知接受及采购整批消费未闭合时不能宣称过账完成。
    状态及摘要形状仅供分流/审计，不能替代读取器的强制核验。
    """
    material_id: str = field(repr=False)
    inspection_id: str = field(repr=False)
    source_kind: str
    birth_sha256: str
    binding_sha256: str
    iqc_sha256: str
    stage: str
    posting_sha256: str | None = None
    notification_status: str | None = None
    lifecycle_sha256: str | None = None

    def validate(self, scope):
        self._validate_common(scope)
        require(isinstance(scope.command,ReceiptIdentity) and scope.action in RECEIPT_ACTIONS and
                self.entity_id==scope.command.receive_id,'native recovery receipt identity mismatch')
        try: strict_id(self.material_id);strict_id(self.inspection_id)
        except PreflightBlocked: raise TransportBlocked('native material and inspection identities required') from None
        require(self.source_kind=='MES_QC_IQC_RECEIVE_MATERIAL_V2' and
                all(_hash(v) for v in (self.source_fingerprint,self.birth_sha256,self.binding_sha256,self.iqc_sha256)),
                'native birth binding and complete V2 inspection readback required; historical IQC forbidden')
        require(self.stage in ('SHELVED','FINISHED'),'native receipt completion stage required')
        if self.stage=='SHELVED':
            require(scope.action=='receipt.certified.shelf' and self.posting_sha256 is None and
                    self.notification_status is None and self.lifecycle_sha256 is None,
                    'shelving is only a state transition, not stock posting or notification completion')
        else:
            require(_hash(self.posting_sha256) and self.notification_status in ('PENDING','ENQUEUED') and
                    (self.lifecycle_sha256 is None or _hash(self.lifecycle_sha256)),
                    'permanent posting/original transactions and notification readback required')
        return self

    def completed_for(self, scope):
        return scope.action=='receipt.certified.shelf' or (
            self.notification_status=='ENQUEUED' and _hash(self.lifecycle_sha256))


def _result_digest(proof):
    values={'entityId':proof.entity_id,'serverNumber':proof.server_number,
            'api':proof.api_sha256,'db':proof.db_sha256,'source':proof.source_sha256}
    if isinstance(proof,ReceiptRecoveryEvidence):
        # 出生和永久结果只以摘要关联到审计链，绝不落明文业务身份或事实。
        values.update(materialId=proof.material_id,inspectionId=proof.inspection_id,
            sourceFingerprint=proof.source_fingerprint,sourceKind=proof.source_kind,
            birth=proof.birth_sha256,binding=proof.binding_sha256,iqc=proof.iqc_sha256,
            stage=proof.stage,posting=proof.posting_sha256,notification=proof.notification_status,
            lifecycle=proof.lifecycle_sha256)
    return _sha(_json(values))

class RecoveryReader(ABC):
    """独立执行强制校验的读取器，不是 HTTP 响应投影器。

    必须通过 SELECT 和 GET 读取租户范围内的实际记录，并唯一匹配原始
    意图/来源/基线、服务端生成的 ID/单号、全部明细/数量及
    已持久化的生命周期状态。冲突、分页不完整或读取失败必须
    抛出异常，绝不能解释为记录不存在。只有读取已完整完成且无歧义、
    并且没有匹配的已提交记录时才返回 None。摘要字段只是审计关联，本身不能作为
    证明。收货分支必须通过实际服务读取永久出生/V2/仓储父链和原物理过账全集；
    不能拿 GET 的裸状态、Redis 占位或当前单位推断原生事实。终态恢复读取原结果，不补建 IQC。
    无本地发送记录的新核对意图仅可读回外部生命周期已完整核验的永久结果；这不授权重发。
    遗失旧审计的原意图仍须被外部校验拒绝，不能借新日志冒充其恢复。
    本通道刻意不提供生产读取器或替代运行态证明。
    """
    @abstractmethod
    def read_back(self, *, scope, request):
        raise TransportBlocked('independent persisted business readback required')

# 申请创建仅暴露统一可信端点。其服务端永久回执 (tenant_id, request_key) 绑定操作人与原始请求
# 哈希，并拒绝已变更、处理中或已补偿的命令。这是源码
# 契约，不是运行中的后端已实现该契约的证明。显式重放
# 要求新鲜有效的 RuntimeAuthority、原始请求处理状态已最终确定的重试证明及生命周期核验。
CERTIFIED_ENDPOINT = '/erp/pu/requisition/certified'
CERTIFIED_FIELDS = frozenset({'requestKey','materialId','vendorId','windowEnd',
                             'requirementDetailIds','expectedSourceFingerprint'})
# 收货以永久租户/收货身份恢复；上架仅改状态，过账及通知恢复必须单独发送。
RECEIPT_ROUTE = r'/erp/wm/receive/certified/(shelf|execute)/([1-9][0-9]*)'
RECEIPT_ACTIONS = frozenset({'receipt.certified.shelf','receipt.certified.post'})
IDEMPOTENT_ACTIONS = frozenset({'requisition.certified.create'}) | RECEIPT_ACTIONS

def _values(value, tenant_id):
    # 仅用于读取查询的防护；可信写入命令不能包含数量、
    # 单位、租户覆盖值、审批标志或调用方指定的业务编码。
    require(not isinstance(value,float),'binary floating-point values forbidden')
    if isinstance(value,dict):
        require(all(isinstance(k,str) for k in value),'query field names must be strings')
        for key,item in value.items():
            if key in ('tenantId','tenant_id'):
                require(item==tenant_id and isinstance(item,str),'tenant override forbidden')
            elif key.endswith('Id') or key.endswith('_id'):
                try: strict_id(item)
                except PreflightBlocked: raise TransportBlocked('native string query ID required') from None
            _values(item,tenant_id)
    elif isinstance(value,list):
        raise TransportBlocked('unbounded query collection forbidden')
    else:
        require(value is None or isinstance(value,(str,int)) and not isinstance(value,bool),
                'ordinary scalar query value required')

def validate_request(request,tenant_id):
    require(isinstance(request,dict) and {'method','path','body'}<=set(request) and
            set(request)<={'method','path','body','query'},'explicit original request shape required')
    frozen=deepcopy(request)
    require(frozen['method']=='POST' and isinstance(frozen['path'],str),
            'only explicit certified POST business commands are enabled')
    receipt=re.fullmatch(RECEIPT_ROUTE,frozen['path'])
    if receipt:
        require(frozen['body'] is None and frozen.get('query') is None,
                'certified receipt takes only a path identity; body/query overrides forbidden')
        try: strict_id(receipt.group(2))
        except PreflightBlocked: raise TransportBlocked('native receipt identity required') from None
        return frozen,'receipt.certified.shelf' if receipt.group(1)=='shelf' else 'receipt.certified.post'
    require(frozen['path']==CERTIFIED_ENDPOINT,'unsupported certified business endpoint')
    require(not frozen.get('query'),'certified command query overrides forbidden')
    body=frozen['body']
    require(isinstance(body,dict) and set(body)==CERTIFIED_FIELDS,
            'certified command requires exactly six source-identity fields; no quantity/unit/flags')
    require(isinstance(body['requestKey'],str) and
            re.fullmatch(r'[A-Za-z0-9:_-]{16,128}',body['requestKey']) is not None,
            'stable server idempotency request key required')
    try:
        strict_id(body['materialId']);strict_id(body['vendorId'])
        identities=body['requirementDetailIds']
        require(isinstance(identities,list) and 0<len(identities)<=1000,'bounded explicit source-detail IDs required')
        for identity in identities:strict_id(identity)
        require(len(set(identities))==len(identities),'duplicate source-detail IDs forbidden')
    except PreflightBlocked:
        raise TransportBlocked('all source IDs must remain native decimal strings') from None
    from datetime import date
    require(isinstance(body['windowEnd'],str),'explicit ISO date window required')
    try:
        require(date.fromisoformat(body['windowEnd']).isoformat()==body['windowEnd'],'ordinary ISO date required')
    except ValueError:
        raise TransportBlocked('valid ISO date window required') from None
    require(_hash(body['expectedSourceFingerprint']),'frozen source fingerprint required')
    require(len(_json(frozen).encode('utf-8'))<=1_000_000,'bounded exact original request required')
    return frozen,'requisition.certified.create'


class AuditJournal:
    """仅追加且经 fsync 同步的摘要日志，使用进程范围的操作系统互斥锁。

    哈希链只能检测损坏，不能独立证明来源真实性。如果文件
    缺失或损坏，调用方不得在新日志中恢复旧意图；
    外部生命周期校验器必须证明创建前的基线完整。
    """
    FIELDS = frozenset({'seq','at','event','action','intentSha256','requestSha256',
                       'tenantSha256','principalSha256','targetSha256','resultSha256',
                       'priorSha256','eventSha256'})
    EVENTS = frozenset({'DISPATCHED','UNCERTAIN','COMPLETED','RECOVERED'})

    def __init__(self, path):
        self.path = self._path(path, '.jsonl')
        self._owner = None

    @staticmethod
    def _path(path, suffix):
        root = PRIVATE_ROOT.resolve()
        candidate = Path(path)
        resolved = candidate.resolve()
        require(resolved.is_relative_to(root) and resolved != root and resolved.suffix == suffix and
                not candidate.is_symlink(), 'journal must remain inside private evidence tree')
        return resolved

    @contextmanager
    def transaction(self):
        self._path(self.path, '.jsonl')
        self.path.parent.mkdir(parents=True, exist_ok=True)
        lockpath = self._path(self.path.with_suffix('.lock'), '.lock')
        try:
            handle = lockpath.open('a+b')
        except OSError:
            raise TransportBlocked('audit lock unavailable; no dispatch') from None
        locked = False
        try:
            if os.name == 'nt':
                import msvcrt
                if handle.seek(0, os.SEEK_END) == 0:
                    handle.write(b'0');handle.flush()
                handle.seek(0)
                msvcrt.locking(handle.fileno(),msvcrt.LK_NBLCK,1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
            locked = True
            self._owner = threading.get_ident()
            self.load()  # 在任何新操作之前校验完整历史。
            yield
        except OSError:
            raise TransportBlocked('audit lock or persistence failure; dispatch disabled') from None
        finally:
            if locked:
                self._owner = None
                if os.name == 'nt':
                    handle.seek(0);msvcrt.locking(handle.fileno(),msvcrt.LK_UNLCK,1)
                else:
                    fcntl.flock(handle.fileno(),fcntl.LOCK_UN)
            handle.close()

    def load(self):
        self._path(self.path, '.jsonl')
        if not self.path.exists():
            return []
        require(self.path.is_file() and self.path.stat().st_size <= 16_000_000,'bounded audit history required')
        try:
            raw = self.path.read_bytes()
            require(not raw or raw.endswith(b'\n'),'truncated audit history; manual investigation required')
            from procurement_runtime_preflight import decode_document
            rows, prior = [], '0'*64
            for line in raw.splitlines():
                record = decode_document(line)
                require(set(record)==self.FIELDS and type(record['seq']) is int and record['seq']==len(rows) and
                        record['event'] in self.EVENTS and record['priorSha256']==prior,'audit chain framing failed')
                require(all(_hash(record[name]) for name in ('intentSha256','requestSha256','tenantSha256',
                          'principalSha256','targetSha256','resultSha256','priorSha256','eventSha256')),
                        'audit digest shape invalid')
                payload={key:value for key,value in record.items() if key!='eventSha256'}
                require(_sha(_json(payload))==record['eventSha256'],'audit hash chain corrupted')
                prior=record['eventSha256'];rows.append(record)
            return rows
        except (OSError, PreflightBlocked, ValueError, KeyError, TypeError):
            raise TransportBlocked('audit history invalid; contents suppressed') from None

    def last(self,scope):
        matching=[row for row in self.load() if row['intentSha256']==scope.intent_sha256]
        for row in matching:
            require(row['requestSha256']==scope.request_sha256 and row['principalSha256']==scope.principal_sha256 and
                    row['tenantSha256']==_sha(scope.tenant_id) and row['targetSha256']==scope.target_sha256 and
                    row['action']==scope.action,'intent original request or principal changed')
        return matching[-1] if matching else None

    def append(self, scope, event, *, proof=None):
        require(self._owner==threading.get_ident() and event in self.EVENTS,'exclusive audit transaction required')
        rows=self.load()
        record={'seq':len(rows),'at':datetime.now().astimezone().isoformat(),'event':event,'action':scope.action,
                'intentSha256':scope.intent_sha256,'requestSha256':scope.request_sha256,
                'tenantSha256':_sha(scope.tenant_id),'principalSha256':scope.principal_sha256,
                'targetSha256':scope.target_sha256,
                'resultSha256':_result_digest(proof) if proof else '0'*64,
                'priorSha256':rows[-1]['eventSha256'] if rows else '0'*64}
        record['eventSha256']=_sha(_json(record))
        self._path(self.path, '.jsonl')
        try:
            with self.path.open('a',encoding='utf-8',newline='\n') as handle:
                handle.write(_json(record)+'\n');handle.flush();os.fsync(handle.fileno())
        except OSError:
            raise TransportBlocked('audit persistence failure; unresolved original dispatch preserved') from None


READ_ROUTES = (
    r'/system/user/getInfo',
    r'/erp/bd/(?:material|vendor|bom|bom/detail|routing)/(?:list|[1-9][0-9]*)',
    r'/erp/pu/(?:requisition|order)/(?:list|[1-9][0-9]*(?:/(?:proc|lines))?)',
    r'/erp/wm/(?:arrivalnotice|receive)/(?:list|[1-9][0-9]*(?:/proc)?)',
    r'/erp/prd/(?:plan|work|requirement)/(?:list|[1-9][0-9]*)',
    r'/mes/qc/iqc/(?:list|[1-9][0-9]*(?:/details)?)',
    r'/workflow/process/iscompleted',
)
READ_QUERY_FIELDS = frozenset({'pageNum','pageSize','orderNo','requisitionNo','noticeId','orderId',
                              'materialId','receiveId','iqcId','sourceId','sourceType','status','processInstanceId','procInsId'})

class FormalProcurementTransport:
    """调用方提供原始意图；默认运行时仅用于只读校验，绝不签发写入授权。

    send({method,path,body,query?}, intent_id=..., retry=False) 是写入 API。
    恢复时使用同一个 send 及原始哈希：已发送的命令
    须先回读核验；显式重放要求独立强制校验幂等性。即使 HTTP 200，在独立
    回读核验前仍返回 UNCERTAIN。post/put 要求显式提供 intent_id；它们不能作为现有只读
    ApiClient 的直接替换来绕过写入限制。主线程必须对接其门禁。
    """
    def __init__(self, *, target=None, credentials, expected_head, authority=None,
                 runtime=_DEFAULT_RUNTIME, lifecycle=None, recovery=None, journal=None, opener=None):
        try:
            self.target=(target or RuntimeTarget()).validate()
            self.credentials=credentials.validate()
        except (PreflightBlocked, AttributeError, TypeError):
            raise TransportBlocked('valid explicit local target and authentication context required') from None
        require(isinstance(expected_head,str) and re.fullmatch(r'[0-9a-f]{40}',expected_head) is not None,
                'exact expected Git HEAD required')
        self.expected_head=expected_head
        self.authority,self.runtime,self.lifecycle,self.recovery,self.journal=authority,runtime,lifecycle,recovery,journal
        self._opener=opener or local_opener()
        if runtime is _DEFAULT_RUNTIME:
            self.runtime=BackendRuntimeAuthority(credentials=self.credentials,opener=self._opener)
        self._principal=None

    def get(self,path,query=None):
        try: safe_path(path)
        except PreflightBlocked: raise TransportBlocked('unsafe read API path') from None
        require(any(re.fullmatch(pattern,path) for pattern in READ_ROUTES),'side-effecting or unsupported GET forbidden')
        require(query is None or isinstance(query,dict) and set(query)<=READ_QUERY_FIELDS,'unsupported read query')
        if query:
            _values(query,self.credentials.tenant_id)
            require(len(_json(query))<=4096,'bounded read query required')
        request=Request(self.target.base_url.rstrip('/')+path+('?' + urlencode(query) if query else ''),
                        headers=self.credentials.headers(),method='GET')
        try:
            document=request_document(request,self._opener,attempts=3)
            require(type(document.get('code')) is int and document['code']==200,'authenticated read rejected; body suppressed')
            return response_id_strings(document)
        except PreflightBlocked:
            raise TransportBlocked('read transport rejected; details suppressed') from None

    def _identity(self):
        auth=authentication_evidence(ReadOnlyClient(self.target,self.credentials,opener=self._opener),self.credentials)
        require(auth.get('status')=='VERIFIED' and _hash(auth.get('principalSha256')),
                'fresh authenticated tenant/principal context required')
        principal=auth['principalSha256']
        require(self._principal in (None,principal),'authenticated principal changed; original binding required')
        self._principal=principal
        return principal

    def _capabilities(self):
        require(isinstance(self.authority,WriteAuthority),'independent business write authority required')
        require(isinstance(self.runtime,RuntimeAuthority),'trusted loaded runtime authority required')
        require(isinstance(self.lifecycle,LifecycleAuthority),'independent source/lifecycle authority required')
        require(isinstance(self.recovery,RecoveryReader),'independent API+database recovery reader required')
        require(isinstance(self.journal,AuditJournal),'durable private audit journal required')

    def _require_runtime(self,scope):
        try:
            require(self.runtime.require_current(target=self.target,expected_head=self.expected_head,
                        tenant_id=self.credentials.tenant_id,principal_sha256=scope.principal_sha256) is None,
                    'runtime authority must enforce, not return a flag')
        except Exception as error:
            category=getattr(error,'category',None)
            raise TransportBlocked('loaded runtime attestation rejected; details suppressed',
                category=category if isinstance(category,str) and category in ATTESTATION_CATEGORIES else 'BUILD_ATTESTATION_REJECTED') from None

    def _enforce(self,scope):
        try:
            require(self.authority.require_authorized(transport=self,scope=scope) is None,
                    'write authority must enforce, not return a flag')
            require(self.lifecycle.require_closed(scope=scope) is None,
                    'lifecycle authority must enforce, not return a flag')
        except Exception:
            raise TransportBlocked('independent write/source/lifecycle authority rejected') from None
        # 在可能耗时的外部来源检查之后，获取新鲜有效的已加载运行时证明。
        self._require_runtime(scope)

    def _readback(self,scope,request):
        try:
            proof=self.recovery.read_back(scope=scope,request=deepcopy(request))
            if proof is not None:
                require(isinstance(proof,RecoveryEvidence),'typed independent business readback required')
                require(isinstance(proof,ReceiptRecoveryEvidence) if scope.action in RECEIPT_ACTIONS
                        else not isinstance(proof,ReceiptRecoveryEvidence),'action-specific native readback required')
                proof.validate(scope)
            return proof
        except Exception:
            raise TransportBlocked('independent recovery absent, ambiguous or invalid; details suppressed') from None

    @staticmethod
    def _result(proof,status):
        return {'code':200,'status':status,'entityId':proof.entity_id,'serverNumber':proof.server_number}

    def _binding_guard(self,binding):
        require((self.target,self.credentials,self.expected_head,self._opener)==binding,
                'authenticated target/tenant/credential/build binding changed')
        try:self.target.validate();self.credentials.validate()
        except PreflightBlocked:
            raise TransportBlocked('bound target/authentication context invalid') from None

    def send(self,request,*,intent_id,retry=False):
        self._capabilities()
        require(isinstance(self.target,RuntimeTarget) and isinstance(self.credentials,Credentials),
                'explicit native target and credential binding required')
        binding=(self.target,self.credentials,self.expected_head,self._opener)
        self._binding_guard(binding)
        require(type(retry) is bool,'retry marker is not an authorization')
        require(isinstance(intent_id,str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}',intent_id) is not None,
                'explicit stable original intent required')
        frozen,action=validate_request(request,self.credentials.tenant_id)
        command=ReceiptIdentity(frozen['path'].rsplit('/',1)[1]) if action in RECEIPT_ACTIONS else CertifiedIdentity.from_body(frozen['body'])
        if isinstance(command,CertifiedIdentity):
            require(command.request_key==intent_id,'requestKey must be the durable original intent')
        with self.journal.transaction():
            scope=WriteScope(tenant_id=self.credentials.tenant_id,intent_id=intent_id,
                request_sha256=request_digest(frozen),principal_sha256=self._identity(),
                target_sha256=_sha(_json(self.target.evidence())),expected_head=self.expected_head,
                action=action,retry=retry,command=command)
            history=self.journal.last(scope)
            self._enforce(scope)
            self._binding_guard(binding)
            if not history:
                require(not retry,'retry requires the durable original dispatch')
            if history or action in RECEIPT_ACTIONS:
                # 终态和重复先走永久事实读回；上架恢复绝不隐式调用过账。
                proof=self._readback(scope,frozen)
                self._binding_guard(binding)
                if proof is not None and proof.completed_for(scope):
                    self._require_runtime(scope)
                    self._binding_guard(binding)
                    self.journal.append(scope,'RECOVERED',proof=proof)
                    return self._result(proof,'RECOVERED')
                if history:
                    require(history['event'] not in ('COMPLETED','RECOVERED'),
                            'previously completed source missing or regressed; no resend')
                if proof is not None and not retry:
                    return {'status':'UNCERTAIN','reason':'await_receipt_lifecycle_readback'}
                if history:
                    if not retry:
                        return {'status':'UNCERTAIN','reason':'await_independent_readback'}
                    require(action in IDEMPOTENT_ACTIONS,'ambiguous CREATE/generator/update cannot be retried')
                    try:
                        require(self.authority.require_retry_safe(transport=self,scope=scope) is None,
                                'retry authority must enforce, not return a flag')
                    except Exception:
                        raise TransportBlocked('independent settled-request retry proof rejected') from None
            self._binding_guard(binding)
            headers=binding[1].headers()
            payload=frozen['body']
            body=_json(payload).encode('utf-8') if payload is not None else None
            if body is not None:headers['Content-Type']='application/json'
            query=frozen.get('query')
            http=Request(binding[0].base_url.rstrip('/')+frozen['path']+('?' + urlencode(query) if query else ''),
                         data=body,headers=headers,method=frozen['method'])
            # 预写记录可在响应超时及进程崩溃后保留。
            # 绝不根据超时、异常类型或缺失行推断未提交。
            self._enforce(scope)
            self._binding_guard(binding)
            self.journal.append(scope,'DISPATCHED')
            try:
                document=request_document(http,binding[3],attempts=1)
                require(type(document.get('code')) is int and document['code']==200,
                        'business response not successful; details suppressed')
                proof=self._readback(scope,frozen)
                if proof is not None:self._require_runtime(scope)
                self._binding_guard(binding)
            except Exception:
                self.journal.append(scope,'UNCERTAIN')
                return {'status':'UNCERTAIN','reason':'transport_or_readback_unresolved'}
            if proof is None:
                self.journal.append(scope,'UNCERTAIN')
                return {'status':'UNCERTAIN','reason':'await_independent_readback'}
            if not proof.completed_for(scope):
                self.journal.append(scope,'UNCERTAIN',proof=proof)
                return {'status':'UNCERTAIN','reason':'await_receipt_lifecycle_readback'}
            self.journal.append(scope,'COMPLETED',proof=proof)
            return self._result(proof,'COMPLETED')

    def post(self,path,payload=None,query=None,*,intent_id,retry=False):
        request={'method':'POST','path':path,'body':payload}
        if query is not None:request['query']=query
        return self.send(request,intent_id=intent_id,retry=retry)

    def put(self,path,payload=None,query=None,*,intent_id,retry=False):
        request={'method':'PUT','path':path,'body':payload}
        if query is not None:request['query']=query
        return self.send(request,intent_id=intent_id,retry=retry)
