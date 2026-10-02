"""Auditable procurement transport; import/construction never performs a write.

No execute CLI, login, SQL, inventory seeding, approval impersonation, or boolean
write switch. The main integration must supply independent enforcing authorities
and an API+DB/source readback adapter. HTTP success (often R<Void>) is NOT business
completion. Journal contains digests, never tokens, request bodies or business IDs.
Read retries are bounded; writes dispatch once. Uncertain commands are recover-first; explicit replay requires proven idempotency.
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

PRIVATE_ROOT = _PRIVATE_ROOT

class TransportBlocked(RuntimeError):
    """Fixed diagnostic; raw HTTP, credentials and adapter errors are suppressed."""

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
class WriteScope:
    tenant_id: str = field(repr=False)
    intent_id: str = field(repr=False)
    request_sha256: str
    principal_sha256: str
    target_sha256: str
    expected_head: str
    action: str
    retry: bool
    command: CertifiedIdentity | None = field(default=None,repr=False)

    @property
    def intent_sha256(self):
        return _sha(self.target_sha256 + '\0' + self.tenant_id + '\0' + self.intent_id)

class WriteAuthority(ABC):
    """Independent, time-bounded, exact-request permission; never issued by Task C.

    Enforce the transport/principal, target, tenant, intent, exact request hash,
    action and expiry. No apply/verified flag or editable JSON is a grant. On
    retry additionally certify settled original request and server-side action
    idempotency in the attested loaded build, NOT merely absence of a DB row.
    """
    @abstractmethod
    def require_authorized(self, *, transport, scope):
        raise TransportBlocked('independent exact-request business write authority required')

    def require_retry_safe(self, *, transport, scope):
        raise TransportBlocked('independent settled-request idempotency proof required')

class LifecycleAuthority(ABC):
    """Main thread must close source+writer+lifecycle before EACH action/recovery.

    Check complete tenant-scoped baseline, original sources and line quantities,
    draft/approval state, real actor/Flowable process, supply/receipt/IQC/shelf
    evidence as appropriate, and idempotency/outbox boundaries. No model-invented
    price, stock, arrival, approval or production fact is admissible. Raises on
    any gap. Never substitute a dictionary of empty/presumed booleans.
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

    def validate(self, scope):
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
        require(scope.command is not None and _hash(self.source_fingerprint) and
                self.source_fingerprint==scope.command.expected_source_fingerprint,
                'persisted source fingerprint differs from frozen original command')
        return self

class RecoveryReader(ABC):
    """Independent enforcing reader, not an HTTP response projector.

    Must SELECT and GET actual tenant-scoped records, uniquely match ORIGINAL
    intent/source/baseline, server-generated ID/number, all lines/quantities and
    persisted lifecycle state. Conflicts/incomplete pagination/read failure must
    raise, never mean absent. Return None only for a completed, unambiguous read
    with no matching committed record. Digest fields are audit links, not proof
    by themselves. Task C intentionally supplies no production implementation.
    """
    @abstractmethod
    def read_back(self, *, scope, request):
        raise TransportBlocked('independent persisted business readback required')

# This round exposes ONLY the unified certified endpoint. Its server-side
# permanent (tenant_id, request_key) receipt binds actor and original request
# hash and rejects changed/pending/compensated commands. This is a source
# contract, NOT proof that the running backend implements it. Explicit replay
# requires fresh RuntimeAuthority, settled-request retry proof and lifecycle.
CERTIFIED_ENDPOINT = '/erp/pu/requisition/certified'
CERTIFIED_FIELDS = frozenset({'requestKey','materialId','vendorId','windowEnd',
                             'requirementDetailIds','expectedSourceFingerprint'})
IDEMPOTENT_ACTIONS = frozenset({'requisition.certified.create'})

def _values(value, tenant_id):
    # Read-query guard only; certified write commands cannot contain quantities,
    # units, tenant overrides, approval flags, or caller-assigned business codes.
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
    require(frozen['method']=='POST' and frozen['path']==CERTIFIED_ENDPOINT,
            'only unified POST certified procurement is enabled this round')
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
    """Append-only fsynced digest journal with a process-wide OS exclusion lock.

    Hash chains detect damage, not independent source authenticity. If the file is
    missing/corrupt, callers must not resume old intents in a fresh journal; the
    external lifecycle authority must prove a complete pre-create baseline.
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
            self.load()  # Validate the entire history before any new operation.
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
                'resultSha256':_sha(_json({'entityId':proof.entity_id,'serverNumber':proof.server_number,
                                        'api':proof.api_sha256,'db':proof.db_sha256,'source':proof.source_sha256})) if proof else '0'*64,
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
    r'/workflow/process/iscompleted',
)
READ_QUERY_FIELDS = frozenset({'pageNum','pageSize','orderNo','requisitionNo','noticeId','orderId',
                              'materialId','sourceId','sourceType','status','processInstanceId','procInsId'})

class FormalProcurementTransport:
    """Caller explicitly supplies original intents; no default issuer or executor.

    send({method,path,body,query?}, intent_id=..., retry=False) is the write API.
    Recover uses the same send with original hash: already-dispatched commands
    read back first; explicit replay requires independent idempotency enforcement. Even HTTP 200 returns UNCERTAIN until independent
    readback. post/put require explicit intent_id; they are NOT drop-in write
    bypasses for existing read-only ApiClient. Main thread must adapt its gates.
    """
    def __init__(self, *, target=None, credentials, expected_head, authority=None,
                 runtime=None, lifecycle=None, recovery=None, journal=None, opener=None):
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

    def _enforce(self,scope):
        try:
            require(self.runtime.require_current(target=self.target,expected_head=self.expected_head,
                        tenant_id=self.credentials.tenant_id,principal_sha256=scope.principal_sha256) is None,
                    'runtime authority must enforce, not return a flag')
            require(self.authority.require_authorized(transport=self,scope=scope) is None,
                    'write authority must enforce, not return a flag')
            require(self.lifecycle.require_closed(scope=scope) is None,
                    'lifecycle authority must enforce, not return a flag')
        except Exception:
            raise TransportBlocked('independent runtime/write/source/lifecycle authority rejected') from None

    def _readback(self,scope,request):
        try:
            proof=self.recovery.read_back(scope=scope,request=deepcopy(request))
            if proof is not None:
                require(isinstance(proof,RecoveryEvidence),'typed independent business readback required')
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
        require(frozen['body']['requestKey']==intent_id,'requestKey must be the durable original intent')
        with self.journal.transaction():
            scope=WriteScope(tenant_id=self.credentials.tenant_id,intent_id=intent_id,
                request_sha256=request_digest(frozen),principal_sha256=self._identity(),
                target_sha256=_sha(_json(self.target.evidence())),expected_head=self.expected_head,
                action=action,retry=retry,command=CertifiedIdentity.from_body(frozen['body']))
            history=self.journal.last(scope)
            self._enforce(scope)
            self._binding_guard(binding)
            if history:
                proof=self._readback(scope,frozen)
                self._binding_guard(binding)
                if proof:
                    self.journal.append(scope,'RECOVERED',proof=proof)
                    return self._result(proof,'RECOVERED')
                require(history['event'] not in ('COMPLETED','RECOVERED'),
                        'previously completed source missing; no resend')
                if not retry:
                    return {'status':'UNCERTAIN','reason':'await_independent_readback'}
                require(action in IDEMPOTENT_ACTIONS,'ambiguous CREATE/generator/update cannot be retried')
                try:
                    require(self.authority.require_retry_safe(transport=self,scope=scope) is None,
                            'retry authority must enforce, not return a flag')
                except Exception:
                    raise TransportBlocked('independent settled-request retry proof rejected') from None
            else:
                require(not retry,'retry requires the durable original dispatch')
            self._binding_guard(binding)
            headers=binding[1].headers()
            payload=frozen['body']
            body=_json(payload).encode('utf-8') if payload is not None else None
            if body is not None:headers['Content-Type']='application/json'
            query=frozen.get('query')
            http=Request(binding[0].base_url.rstrip('/')+frozen['path']+('?' + urlencode(query) if query else ''),
                         data=body,headers=headers,method=frozen['method'])
            # Write-ahead record survives response timeouts and process crashes.
            # Never infer no-commit from timeout, exception type or missing row.
            self.journal.append(scope,'DISPATCHED')
            try:
                document=request_document(http,binding[3],attempts=1)
                require(type(document.get('code')) is int and document['code']==200,
                        'business response not successful; details suppressed')
                proof=self._readback(scope,frozen)
                self._binding_guard(binding)
            except Exception:
                self.journal.append(scope,'UNCERTAIN')
                return {'status':'UNCERTAIN','reason':'transport_or_readback_unresolved'}
            if proof is None:
                self.journal.append(scope,'UNCERTAIN')
                return {'status':'UNCERTAIN','reason':'await_independent_readback'}
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
