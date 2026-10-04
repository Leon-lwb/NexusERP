"""本地开发测试的只读运行预检：默认普通服务/进程/四库检查。

用户已明确本地无真实用户，不要求正式签发或完整JVM防篡改体系。默认local-dev
仅核对认证、dev/loopback进程及实际四库一致性；Git/编译产物仅作诊断，绝不宣称
最终定义字节或生产认证。strict保留为显式高保证诊断。业务来源/单位/额度、写授权、
生命周期、幂等及独立对账不因运行模式简化而放松。本工具不写业务数据或启停服务。
"""
from __future__ import annotations
from abc import ABC, abstractmethod
import argparse
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener
from stock_native_precision_migration import NATIVE_COLUMNS, native_precision_gaps
from procurement_schema_contract import TABLES as CERTIFIED_TABLES, schema_evidence as certified_schema_evidence

# 直接执行脚本与具体权威校验实现必须共用同一组类。
if __name__ == '__main__':
    sys.modules['procurement_runtime_preflight'] = sys.modules[__name__]

ROOT = Path(__file__).resolve().parents[4]
PRIVATE_ROOT = ROOT / '.codex' / 'full-flow-20260928'
LOCAL_TIMEZONE = timezone(timedelta(hours=8))
MAX_DOCUMENT = 1_000_000
SCHEMAS = {'master':'ry-vue', 'erp':'ry-erp', 'mes':'ry-mes', 'iot':'ry-iot'}
TABLES = {
    'ry-vue': ('sys_tenant', 'sys_user', 'sys_auto_code_rule', 'sys_auto_code_part'),
    'ry-erp': ('bd_material', 'bd_material_vendor', 'bd_vendor', 'bd_bom', 'bd_bom_detail',
               'bd_routing', 'prd_production_plan', 'prd_material_requirement',
               'prd_material_requirement_detail', 'prd_work_order',
               'pu_purchase_requisition', 'pu_purchase_requisition_line',
               'pu_purchase_order', 'pu_purchase_order_line', 'pu_certified_purchase_command', 'wm_arrival_notice',
               'wm_arrival_notice_line', 'wm_material_receive', 'wm_material_receive_line',
               'wm_material_receive_detail', 'wm_material_stock', 'wm_material_stock_tx'),
    'ry-mes': ('pro_work_order',), 'ry-iot': ('iot_device',),
}
# 新出生及永久命令/账本/过账的结构必须一起观察；缺项不能继续读取业务行。
for _schema, _tables in CERTIFIED_TABLES.items():
    TABLES[_schema] = tuple(dict.fromkeys((*TABLES.get(_schema, ()), *_tables)))

DECIMAL_FIELDS = {
    'ry-erp.wm_material_stock': ('stock_quantity', 'reserved_quantity'),
    'ry-erp.wm_material_stock_tx': ('tx_quantity', 'before_quantity', 'after_quantity'),
    'ry-erp.pu_purchase_requisition_line': ('apply_quantity',),
    'ry-erp.pu_purchase_order_line': ('order_quantity', 'received_quantity'),
    'ry-erp.prd_material_requirement_detail': ('required_quantity',),
}

class PreflightBlocked(RuntimeError):
    """固定诊断信息；绝不包装原始远端或配置异常。"""
    def __init__(self,message,*,category='UNVERIFIED',http_status=None):
        super().__init__(message)
        self.category=category
        self.http_status = http_status if type(http_status) is int and 100 <= http_status <= 599 else None

def diagnostic_category(error):
    # urllib 包装只按已知异常类型展开，不读取或输出远端原因文本。
    if isinstance(error, URLError) and isinstance(error.reason, (ConnectionRefusedError, TimeoutError)):
        return diagnostic_category(error.reason)
    if isinstance(error,ImportError):return 'DEPENDENCY_MISSING'
    if isinstance(error,ConnectionRefusedError):return 'CONNECTION_REFUSED'
    if isinstance(error,TimeoutError):return 'CONNECTION_TIMEOUT'
    errno=error.args[0] if error.args and type(error.args[0]) is int else None
    categories={1045:'AUTH_FAILED',1044:'READ_PERMISSION_DENIED',1049:'SCHEMA_MISSING',
                1146:'TABLE_MISSING',1054:'COLUMN_MISSING',1142:'READ_PERMISSION_DENIED',
                2002:'CONNECTION_FAILED',2003:'CONNECTION_FAILED',2026:'TLS_CONNECTION_FAILED'}
    if errno in categories:return categories[errno]
    if isinstance(error,PreflightBlocked):return error.category
    return 'PROBE_FAILED'

def require(condition, message):
    if not condition:
        raise PreflightBlocked(message)

def digest(value):
    return hashlib.sha256(str(value).encode('utf-8')).hexdigest()

def strict_id(value, *, tenant=False):
    if tenant:
        require(isinstance(value,str) and re.fullmatch(r'[0-9]{6,20}',value) is not None,
                'tenant must remain an explicit 6-20 digit string')
    else:
        require(isinstance(value,str) and re.fullmatch(r'[1-9][0-9]{0,18}',value) is not None,
                'IDs must be canonical positive decimal strings')
        require(int(value)<=2**63-1,'ID outside signed BIGINT domain')
    return value

def exact_decimal(value):
    require(isinstance(value,(Decimal,str)),'native Decimal or decimal string required')
    if isinstance(value,Decimal):
        require(value.is_finite(),'finite native Decimal required')
        parts=value.as_tuple()
        require(len(parts.digits)+abs(parts.exponent)<=MAX_DOCUMENT,'bounded native Decimal required')
        return value
    require(len(value)<=MAX_DOCUMENT and re.fullmatch(r'-?[0-9]+(?:\.[0-9]+)?',value) is not None,
            'finite ordinary decimal string required')
    try:return Decimal(value)
    except InvalidOperation:
        raise PreflightBlocked('finite ordinary decimal string required') from None

@dataclass(frozen=True)
class RuntimeTarget:
    base_url: str = 'http://127.0.0.1:8080'
    db_host: str = '127.0.0.1'
    db_port: int = 3306
    profile: str = 'dev'

    def validate(self):
        try:
            p = urlsplit(self.base_url)
            require(p.scheme == 'http' and p.hostname in ('localhost', '127.0.0.1') and
                    p.port == 8080 and not p.username and not p.password and
                    p.path in ('', '/') and not p.query and not p.fragment,
                    'only loopback HTTP 8080 is allowed')
        except (ValueError, TypeError):
            raise PreflightBlocked('only loopback HTTP 8080 is allowed') from None
        require(self.db_host in ('localhost', '127.0.0.1') and type(self.db_port) is int and
                self.db_port == 3306 and self.profile == 'dev', 'only dev loopback TCP 3306 is allowed')
        return self

    def evidence(self):
        self.validate()
        return {'apiOrigin':self.base_url.rstrip('/'), 'databaseHost':self.db_host,
                'databasePort':self.db_port, 'profile':self.profile, 'schemas':SCHEMAS.copy()}

@dataclass(frozen=True)
class Credentials:
    token: str = field(default='', repr=False)
    client_id: str = field(default='', repr=False)
    tenant_id: str = field(default='', repr=False)
    cookie: str = field(default='', repr=False)
    base_url: str = 'http://127.0.0.1:8080'
    source: str = 'explicit_context'

    def validate(self):
        RuntimeTarget(base_url=self.base_url).validate()
        strict_id(self.tenant_id, tenant=True)
        require(bool(self.token) != bool(self.cookie), 'exactly one token or cookie context required')
        require(isinstance(self.client_id, str) and bool(self.client_id), 'explicit client context required')
        for value in (self.token, self.cookie, self.client_id):
            require(isinstance(value, str) and len(value) <= 16384 and
                    not any(ord(c) < 32 or ord(c) == 127 for c in value), 'unsafe credential header')
        require(not self.token.lower().startswith('bearer '), 'supply token without scheme')
        return self

    def headers(self):
        self.validate()
        result = {'clientid':self.client_id, 'Accept':'application/json'}
        result['Authorization' if self.token else 'Cookie'] = 'Bearer ' + self.token if self.token else self.cookie
        return result

    def evidence(self):
        self.validate()
        return {'credentialPresent':True, 'credentialKind':'bearer' if self.token else 'cookie',
                'credentialSha256':digest(self.token or self.cookie), 'clientIdPresent':True,
                'clientIdSha256':digest(self.client_id), 'tenantSha256':digest(self.tenant_id)}

def private_file(path, *, suffix='.json', root=None):
    root = (root or PRIVATE_ROOT).resolve()
    candidate = Path(path)
    resolved = candidate.resolve()
    require(resolved.is_relative_to(root) and resolved != root and resolved.suffix == suffix,
            'explicit private evidence path required')
    require(not candidate.is_symlink(), 'symlink evidence file forbidden')
    return resolved

def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON key forbidden')
        result[key] = value
    return result

def decode_document(raw):
    require(isinstance(raw, bytes) and len(raw) <= MAX_DOCUMENT, 'bounded JSON document required')
    try:
        document = json.loads(raw.decode('utf-8-sig'), parse_float=Decimal,
                              parse_constant=lambda _:require(False, 'non-finite JSON forbidden'),
                              object_pairs_hook=_unique_object)
        require(isinstance(document, dict), 'JSON object required')
        return document
    except (ValueError, UnicodeError, RecursionError):
        raise PreflightBlocked('invalid JSON document; body suppressed') from None

def load_credentials(*, target=None, path=None, environ=None):
    target = (target or RuntimeTarget()).validate()
    env = os.environ if environ is None else environ
    names = ('NEXUS_FULL_FLOW_TOKEN', 'NEXUS_FULL_FLOW_COOKIE',
             'NEXUS_FULL_FLOW_CLIENT_ID', 'NEXUS_FULL_FLOW_TENANT_ID')
    if any(env.get(name) for name in names):
        result = Credentials(token=env.get(names[0], ''), cookie=env.get(names[1], ''),
                             client_id=env.get(names[2], ''), tenant_id=env.get(names[3], ''),
                             base_url=target.base_url, source='process_environment').validate()
    else:
        require(path is not None, 'trusted authentication context missing; no login attempted')
        p = private_file(path)
        require(p.is_file() and p.stat().st_size <= MAX_DOCUMENT, 'bounded local credential file required')
        try:
            data = decode_document(p.read_bytes())
            require(set(data) <= {'token', 'cookie', 'clientId', 'tenantId', 'baseUrl'},
                    'unsupported credential file shape')
            result = Credentials(token=data.get('token', ''), cookie=data.get('cookie', ''),
                                 client_id=data.get('clientId', ''), tenant_id=data.get('tenantId', ''),
                                 base_url=data.get('baseUrl', ''), source='explicit_private_file').validate()
        except OSError:
            raise PreflightBlocked('credential file cannot be read; details suppressed') from None
    require(urlsplit(result.base_url).hostname in ('localhost', '127.0.0.1') and
            urlsplit(result.base_url).port == urlsplit(target.base_url).port,
            'credential origin differs from target')
    return result

class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None

def local_opener():
    return build_opener(ProxyHandler({}), _NoRedirect()).open

def safe_path(path):
    require(isinstance(path, str) and re.fullmatch(r'/[A-Za-z0-9_/-]+', path) is not None and
            not path.startswith('//') and '//' not in path and
            not any(s in ('.', '..') for s in path.split('/')), 'unsafe API path')
    return path

def request_document(request, opener, *, attempts=1):
    require(attempts in (1, 3) and (attempts == 1 or request.get_method() == 'GET'),
            'only safe reads may be automatically retried')
    for attempt in range(attempts):
        try:
            with opener(request, timeout=10) as response:
                status = getattr(response, 'status', 200)
                require(type(status) is int and 200 <= status < 300, 'HTTP status rejected; body suppressed')
                return decode_document(response.read(MAX_DOCUMENT + 1))
        except HTTPError as error:
            if request.get_method() == 'GET' and error.code in (429, 502, 503, 504) and attempt + 1 < attempts:
                continue
            status = error.code if type(error.code) is int and 100 <= error.code <= 599 else None
            category = ({401:'AUTH_FAILED', 403:'READ_PERMISSION_DENIED', 429:'HTTP_RATE_LIMITED'}
                        .get(status, 'HTTP_BACKEND_ERROR' if status is not None and status >= 500
                             else 'HTTP_REJECTED'))
            raise PreflightBlocked('HTTP request rejected; body suppressed', category=category,
                                   http_status=status) from None
        except (URLError, TimeoutError, OSError) as error:
            if request.get_method() == 'GET' and attempt + 1 < attempts:
                continue
            raise PreflightBlocked('HTTP transport unavailable; details suppressed',category=diagnostic_category(error)) from None
        except Exception as error:
            if isinstance(error,PreflightBlocked):raise
            raise PreflightBlocked('HTTP response unavailable; details suppressed',category=diagnostic_category(error)) from None

def response_id_strings(value,*,identity=False):
    """仅允许将新获取的原始 JSON 整数 ID 规范化为字符串，绝不接受浮点数。

    Python 的 JSON 整数保留所有数字；不要在此传入经 JS 舍入的 ID。
    计数等非 ID 数值仍保持为整数，数量仍保持为 Decimal。
    """
    if identity:
        if value is None or isinstance(value,str):return value
        require(type(value) is int and 0<=value<=2**63-1,'native integer/string response ID required')
        return str(value)
    if isinstance(value,dict):
        result={}
        for key,item in value.items():
            is_id=key.endswith('Id') or key.endswith('_id') or key=='id'
            if key.endswith('Ids') and isinstance(item,list):
                result[key]=[response_id_strings(v,identity=True) for v in item]
            else:result[key]=response_id_strings(item,identity=is_id)
        return result
    if isinstance(value,list):return [response_id_strings(v) for v in value]
    return value

class ReadOnlyClient:
    """仅允许认证 GET 请求；禁止 GET order/generate。"""
    def __init__(self, target, credentials, *, opener=None):
        self.target, self.credentials = target.validate(), credentials.validate()
        self._opener = opener or local_opener()

    def get(self, path='/system/user/getInfo'):
        require(safe_path(path) == '/system/user/getInfo', 'preflight permits getInfo only')
        request = Request(self.target.base_url.rstrip('/') + path,
                          headers=self.credentials.headers(), method='GET')
        document = request_document(request, self._opener, attempts=3)
        # 失败响应先交给固定业务码诊断，不让无关的非法 ID 抹掉 401 等证据。
        # 成功响应仍完整保留原生整数/字符串身份校验，绝不按失败路径放宽。
        return response_id_strings(document) if type(document.get('code')) is int and document['code'] == 200 else document

def authentication_evidence(client, credentials):
    result = {**credentials.evidence(), 'status':'BLOCKED'}
    try:
        document = client.get('/system/user/getInfo')
        code = document.get('code') if isinstance(document, dict) else None
        result['businessCode'] = code if type(code) is int and 100 <= code <= 599 else None
        if type(code) is not int or code != 200:
            result['reason'] = 'authentication_not_successful'
            # 业务错误码只作诊断；后端故障和缺权限不能误报为会话过期。
            result['diagnosticCategory'] = 'AUTH_CONTEXT_UNVERIFIED'
            if type(code) is int:
                fallback = 'AUTH_BACKEND_ERROR' if 500 <= code <= 599 else 'AUTH_CONTEXT_UNVERIFIED'
                result['diagnosticCategory'] = {
                    401:'AUTH_FAILED', 403:'READ_PERMISSION_DENIED', 429:'AUTH_RATE_LIMITED'
                }.get(code, fallback)
            return result
        data = document.get('data')
        require(isinstance(data, dict) and isinstance(data.get('user'), dict), 'authentication shape unavailable')
        user = data['user']
        tenant = strict_id(user.get('tenantId'), tenant=True)
        principal = strict_id(user.get('userId'))
        if tenant != credentials.tenant_id:
            result['reason'] = 'authenticated_tenant_mismatch'
            result['diagnosticCategory']='AUTH_CONTEXT_MISMATCH'
            return result
        permissions = data.get('permissions', [])
        require(isinstance(permissions, list) and all(isinstance(p, str) for p in permissions),
                'authentication permission shape unavailable')
        result.update({'status':'VERIFIED', 'principalSha256':digest(principal),
                       'permissionCount':len(permissions), 'permissionSetSha256':digest('\n'.join(sorted(permissions))),
                       'observedAt':datetime.now(LOCAL_TIMEZONE).isoformat()})
        return result
    except Exception as error:
        result['reason'] = 'authentication_probe_unavailable_or_invalid'
        category = diagnostic_category(error)
        result['diagnosticCategory'] = {'HTTP_RATE_LIMITED':'AUTH_RATE_LIMITED',
                                        'HTTP_BACKEND_ERROR':'AUTH_BACKEND_ERROR'}.get(category, category)
        if isinstance(error, PreflightBlocked) and error.http_status is not None:
            result['httpStatus'] = error.http_status
        return result


def _failure_diagnostics(failures):
    """最多八个候选的固定分类及数值计数，不输出候选身份、路径或远端本体。"""
    summary = {'failureCategories':sorted({failure['diagnosticCategory'] for failure in failures})}
    for field, output in (('businessCode', 'businessCodeCounts'), ('httpStatus', 'httpStatusCounts')):
        counts = {}
        for failure in failures:
            value = failure.get(field)
            if type(value) is int and 100 <= value <= 599:
                counts[value] = counts.get(value, 0) + 1
        summary[output] = [{'code':value, 'count':counts[value]} for value in sorted(counts)]
    return summary


def resolve_existing_authentication(*, target=None, path=None, environ=None, opener=None):
    """仅查找既定的私有会话位置；通过 GET 验证每个候选项。

    绝不执行登录、抓取任意浏览器用户配置、接受 ACTIVE 标志、拼接
    不完整上下文或按文件时间选择未验证的令牌。候选名称
    仅限定搜索范围；新鲜的 getInfo 响应用于证明身份与租户。
    """
    target=(target or RuntimeTarget()).validate()
    env=os.environ if environ is None else environ
    names=('NEXUS_FULL_FLOW_TOKEN','NEXUS_FULL_FLOW_COOKIE',
           'NEXUS_FULL_FLOW_CLIENT_ID','NEXUS_FULL_FLOW_TENANT_ID')
    explicit=path is not None or any(env.get(name) for name in names)
    if explicit:
        candidates=[path]
    else:
        root=PRIVATE_ROOT.resolve()
        found=[]
        for directory in root.glob('*-procurement-execution/runtime'):
            if re.fullmatch(r'[0-9]{8}-procurement-execution',directory.parent.name):
                found.extend(directory.glob('private-credential-*.json'))
        candidates=sorted(found,key=lambda item:str(item),reverse=True)[:7]
        saved=root/'session-token.json'
        if saved.is_file():candidates.append(saved)
    verified=[];categories=set();failures=[]
    for candidate in candidates:
        try:
            credentials=load_credentials(target=target,path=candidate,environ=env)
            auth=authentication_evidence(ReadOnlyClient(target,credentials,opener=opener),credentials)
            if auth.get('status')=='VERIFIED':
                auth.update({'contextSource':'explicit_context' if explicit else 'verified_existing_private_session',
                             'credentialSourcePathSha256':digest(str(Path(candidate).resolve())) if candidate else None})
                verified.append((credentials,auth))
            else:
                categories.add(auth.get('diagnosticCategory','AUTH_CONTEXT_UNVERIFIED'))
                failures.append(auth)
        except (PreflightBlocked,OSError) as error:
            category = diagnostic_category(error)
            categories.add(category)
            failures.append({'status':'BLOCKED', 'diagnosticCategory':category,
                             'reason':'authentication_probe_unavailable_or_invalid'})
    identities={(c.tenant_id,a['principalSha256']) for c,a in verified}
    if len(identities)==1:
        credentials,auth=verified[0]
        auth.update({'candidateCount':len(candidates),'verifiedCandidateCount':len(verified)})
        return credentials,auth
    # 明确的单一上下文保留数值与固定分类，不回传响应本体、凭证或候选文件名。
    if len(candidates) == 1 and len(failures) == 1:
        failure = failures[0]
        report = {'status':'BLOCKED', 'reason':failure['reason'],
                  'businessCode':failure.get('businessCode'),
                  'diagnosticCategory':failure['diagnosticCategory'],
                  'candidateCount':1, 'businessWrites':0, 'loginRequests':0,
                  **_failure_diagnostics(failures)}
        if failure.get('httpStatus') is not None:
            report['httpStatus'] = failure['httpStatus']
        return None, report
    category = ('AUTH_CONTEXT_AMBIGUOUS' if len(identities) > 1
                else next(iter(categories)) if len(categories) == 1
                else 'AUTH_CONTEXT_UNVERIFIED')
    return None,{'status':'BLOCKED','reason':'existing_authentication_context_missing_invalid_or_ambiguous',
                 'diagnosticCategory':category,'candidateCount':len(candidates),'businessWrites':0,
                 'loginRequests':0, **_failure_diagnostics(failures)}


class RuntimeAuthority(ABC):
    """运行上下文检查契约，不是业务写入授权或可提交的verified旗标。

    local-dev核对受信本地开发服务、身份、进程与四库一致性，不要求签发根或
    最终类字节证明；strict是显式的更高保证诊断。expected_head在本地模式只作
    源码上下文，不能冒充已加载版本。每次执行/恢复均fresh检查，歧义抛异常；
    成功只返回None，不签发来源、审批、数量或生命周期授权。
    """
    @abstractmethod
    def require_current(self, *, target, expected_head, tenant_id, principal_sha256):
        raise PreflightBlocked('trusted loaded runtime and effective target attestation required')

def parse_config_text(text):
    import yaml
    text = re.sub(r'^(\s*[^:#\n]+:\s*)(@[A-Za-z0-9_.-]+@)(\s*(?:#.*)?)$',
                  r'\1"\2"\3', text, flags=re.M)
    try:
        return [d for d in yaml.safe_load_all(text) if isinstance(d, dict)]
    except yaml.YAMLError:
        raise PreflightBlocked('configuration parse failed; contents suppressed') from None

def _flatten(data, prefix='', result=None):
    result = {} if result is None else result
    for key, value in data.items():
        name = (prefix + '.' if prefix else '') + str(key)
        if isinstance(value, dict):
            _flatten(value, name, result)
        else:
            result[name] = value
    return result

def _resolve(value, context, env, seen=()):
    require(isinstance(value, str), 'explicit string configuration value required')
    match = re.fullmatch(r'\$\{([^}:]+)(?::([^}]*))?\}', value)
    if match:
        name, default = match.groups()
        require(name not in seen, 'recursive configuration reference')
        selected = env.get(name, context.get(name, default))
        require(selected is not None, 'configuration reference unavailable')
        return _resolve(selected, context, env, (*seen, name))
    require('${' not in value and not re.search(r'@[A-Za-z0-9_.-]+@', value), 'configuration reference unresolved')
    return value


def configuration_value(value, context, env):
    """在内存中解析本地配置的 Jasypt 格式，绝不将密文用作数据库连接凭据。

    刻意只支持本仓库明确指定的 AES-256/HMAC-SHA512
    配置，使用随机的 16 字节盐/IV 并以 base64 输出。其他加密
    设置一律拒绝；不猜测密码、不启动 Java 子进程，也不记录日志。
    """
    resolved = _resolve(value, context, env)
    if not resolved.startswith('ENC('):
        return resolved
    algorithm = context.get('jasypt.encryptor.algorithm')
    if algorithm != 'PBEWITHHMACSHA512ANDAES_256':
        raise PreflightBlocked('local encrypted configuration algorithm unsupported',
                               category='ENCRYPTED_CONFIG_UNSUPPORTED')
    options = {'jasypt.encryptor.salt-generator-classname':'org.jasypt.salt.RandomSaltGenerator',
               'jasypt.encryptor.iv-generator-classname':'org.jasypt.iv.RandomIvGenerator',
               'jasypt.encryptor.string-output-type':'base64'}
    if any(context.get(key, default) != default for key, default in options.items()) or any(
            context.get(key) for key in ('jasypt.encryptor.provider-name','jasypt.encryptor.provider-class-name')):
        raise PreflightBlocked('local encrypted configuration parameters unsupported',
                               category='ENCRYPTED_CONFIG_UNSUPPORTED')
    try:
        import base64
        from cryptography.hazmat.primitives import padding
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
        iterations = context.get('jasypt.encryptor.key-obtention-iterations', 1000)
        require((type(iterations) is int or isinstance(iterations, str) and iterations.isdecimal())
                and 1 <= int(iterations) <= 1_000_000, 'bounded encryption iteration count required')
        require(resolved.endswith(')') and len(resolved) <= MAX_DOCUMENT,
                'bounded encrypted configuration required')
        secret = _resolve(context.get('jasypt.encryptor.password'), context, env)
        require(0 < len(secret) <= 4096 and secret.isascii(), 'supported local encryption secret required')
        raw = base64.b64decode(resolved[4:-1], validate=True)
        require(len(raw) >= 48 and (len(raw)-32) % 16 == 0,
                'encrypted configuration salt/IV/cipher shape unavailable')
        key = hashlib.pbkdf2_hmac('sha512', secret.encode('ascii'), raw[:16], int(iterations), dklen=32)
        decryptor = Cipher(algorithms.AES(key), modes.CBC(raw[16:32])).decryptor()
        padded = decryptor.update(raw[32:]) + decryptor.finalize()
        unpadder = padding.PKCS7(128).unpadder()
        return (unpadder.update(padded) + unpadder.finalize()).decode('utf-8')
    except ImportError:
        raise PreflightBlocked('local encrypted configuration dependency unavailable',
                               category='DEPENDENCY_MISSING') from None
    except Exception:
        raise PreflightBlocked('local encrypted configuration decryption failed; contents suppressed',
                               category='ENCRYPTED_CONFIG_DECRYPTION_FAILED') from None


def validate_jdbc(name, datasource):
    require(name in SCHEMAS and isinstance(datasource, dict), 'unapproved datasource')
    try:
        url = datasource.get('url')
        require(isinstance(url, str) and url.startswith('jdbc:mysql://'), 'MySQL JDBC URL required')
        parsed = urlsplit(url[5:])
        require(parsed.hostname in ('localhost', '127.0.0.1') and parsed.port == 3306 and
                parsed.path == '/' + SCHEMAS[name] and not parsed.username and not parsed.password and
                not parsed.fragment, 'datasource is not approved dev loopback schema')
        from urllib.parse import parse_qsl
        for key, value in parse_qsl(parsed.query, keep_blank_values=True):
            require(key.lower() not in ('user', 'password', 'host', 'port', 'database', 'socketfactory',
                                        'socksproxyhost', 'socksproxyport'), 'JDBC target/credential override rejected')
    except (ValueError, TypeError):
        raise PreflightBlocked('datasource target parse failed; contents suppressed') from None

@dataclass(frozen=True)
class LocalSettings:
    datasources: dict = field(repr=False)
    summary: dict = field(repr=False)

    def validate(self):
        require('master' in self.datasources, 'master datasource configuration required')
        for name, datasource in self.datasources.items():
            validate_jdbc(name, datasource)
            require(isinstance(datasource.get('username'), str) and bool(datasource['username']) and
                    isinstance(datasource.get('password'), str), 'explicit datasource credentials required')
            if datasource['password'].startswith('ENC('):
                raise PreflightBlocked('local encrypted datasource credential unresolved',
                                       category='ENCRYPTED_CONFIG_UNRESOLVED')
        return self

def settings_from_files(root=ROOT, *, config_dir=None, environ=None):
    root = Path(root).resolve()
    approved = (root / 'ruoyi-admin/src/main/resources', root / 'ruoyi-admin/target/classes')
    base = Path(config_dir).resolve() if config_dir is not None else approved[0]
    require(base in approved, 'explicit source or target resource directory only')
    env = os.environ if environ is None else environ
    context, fingerprints = {}, {}
    for name in ('application.yml', 'application-dev.yml', 'application-secret.yml'):
        path = base / name
        if not path.is_file():
            require(name == 'application-secret.yml', 'required local configuration unavailable')
            continue
        require(path.stat().st_size <= MAX_DOCUMENT, 'bounded configuration required')
        raw = path.read_bytes()
        fingerprints[name] = hashlib.sha256(raw).hexdigest()
        for document in parse_config_text(raw.decode('utf-8-sig')):
            context.update(_flatten(document))
    prefix = 'spring.datasource.dynamic.datasource.'
    datasources = {}
    for name in SCHEMAS:
        fields = {}
        for key in ('url', 'username', 'password'):
            config_key = prefix + name + '.' + key
            require(config_key in context, 'complete four-datasource configuration required')
            fields[key] = configuration_value(context[config_key], context, env)
        datasources[name] = fields
    summary = {'sourceKind':'source_resources' if base == approved[0] else 'target_resources',
               'filesSha256':fingerprints,
               'encryptedValuesResolvedInMemory':any(isinstance(v,str) and v.startswith('ENC(') for v in context.values()),
               'datasources':{name:{'jdbcBase':'jdbc:mysql://127.0.0.1:3306/' + SCHEMAS[name],
                                  'usernamePresent':bool(v['username']), 'passwordPresent':bool(v['password']),
                                  'credentialValuesNeverReported':True}
                              for name, v in datasources.items()}, 'notEffectiveRuntimeProof':True}
    return LocalSettings(datasources, summary).validate()

def metadata_gaps(metadata):
    gaps = []
    for schema, tables in TABLES.items():
        for table in tables:
            key = schema + '.' + table
            if key not in metadata:
                gaps.append(key + ': table unavailable')
                continue
            entry = metadata[key]
            if str(entry.get('engine', '')).upper() != 'INNODB':
                gaps.append(key + ': engine must be InnoDB')
            columns = entry.get('columns', {})
            if 'tenant_id' not in columns:
                gaps.append(key + ': tenant column unavailable')
            if table in NATIVE_COLUMNS:
                # 共用迁移守卫的原生18,4合同；结构匹配绝不等于来源认证或迁移授权。
                gaps.extend(native_precision_gaps(key, columns))
                continue
            for column in DECIMAL_FIELDS.get(key, ()):
                spec = columns.get(column, {})
                if spec.get('type') != 'decimal' or type(spec.get('precision')) is not int or type(spec.get('scale')) is not int:
                    gaps.append(key + '.' + column + ': native DECIMAL required')
    return sorted(gaps)

def certified_index_gaps(rows):
    """只读核对当前写入方的永久回执唯一键契约。"""
    expected={'PRIMARY':['tenant_id','request_key'],
              'uk_certified_purchase_requisition':['tenant_id','requisition_id']}
    observed={}
    valid=isinstance(rows,(list,tuple)) and len(rows)==4
    for row in rows if valid else ():
        if not isinstance(row,(list,tuple)) or len(row)!=5:
            valid=False;break
        name,non_unique,sequence,column,prefix=row
        if name not in expected or type(non_unique) is not int or non_unique!=0 or prefix is not None:
            valid=False;break
        columns=observed.setdefault(name,[])
        if type(sequence) is not int or sequence!=len(columns)+1:
            valid=False;break
        columns.append(column)
    return [] if valid and observed==expected else ['certified_purchase_receipt_unique_keys_not_proven']

def _db_decimal(value):
    require(type(value) is int or isinstance(value, Decimal), 'non-native database numeric result')
    return format(Decimal(value), 'f')



def _read_schema_metadata(cursor, table_groups):
    """只读取批准库的原生列和唯一索引；结构观测不认证业务来源或围栏 DDL。"""
    require(isinstance(table_groups, dict) and bool(table_groups), 'approved table groups required')
    for schema, tables in table_groups.items():
        require(schema in SCHEMAS.values() and isinstance(tables, (tuple, list)) and
                0 < len(tables) <= 100 and
                all(isinstance(table, str) and re.fullmatch(r'[a-z][a-z0-9_]*', table) for table in tables) and
                len(set(tables)) == len(tables),
                'bounded approved schema and table names required')
    metadata, indexes = {}, {}
    for schema, tables in table_groups.items():
        marks = ','.join(['%s'] * len(tables))
        parameters = (schema, *tables)
        cursor.execute('SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s '
                       "AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME IN (" + marks + ')', parameters)
        for table, engine in cursor.fetchall():
            key = schema + '.' + table
            require(table in tables and key not in metadata, 'unexpected or duplicate table metadata')
            metadata[key] = {'engine':engine, 'columns':{}}
            indexes[key] = []
        cursor.execute('SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,NUMERIC_PRECISION,NUMERIC_SCALE,'
                       'IS_NULLABLE,CHARACTER_MAXIMUM_LENGTH,COLUMN_TYPE,COLLATION_NAME,'
                       'DATETIME_PRECISION,EXTRA,GENERATION_EXPRESSION '
                       'FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME IN (' + marks +
                       ') ORDER BY TABLE_NAME,ORDINAL_POSITION', parameters)
        for table, column, kind, precision, scale, nullable, length, column_type, collation, datetime_precision, extra, generation in cursor.fetchall():
            key = schema + '.' + table
            require(key in metadata and isinstance(column, str) and column not in metadata[key]['columns'],
                    'unexpected or duplicate column metadata')
            metadata[key]['columns'][column] = {'type':kind, 'precision':precision, 'scale':scale,
                                               'nullable':nullable, 'characterMaximumLength':length,
                                               'columnType':column_type, 'collation':collation,
                                               'datetimePrecision':datetime_precision, 'extra':extra,
                                               'generationExpression':generation}
        cursor.execute('SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,INDEX_TYPE '
                       'FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=%s AND TABLE_NAME IN (' + marks +
                       ') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX', parameters)
        for table, index, non_unique, sequence, column, prefix, index_type in cursor.fetchall():
            key = schema + '.' + table
            require(key in indexes, 'unexpected index table metadata')
            indexes[key].append((index, non_unique, sequence, column, prefix, index_type))
    return metadata, indexes

def database_evidence(settings, tenant_id, *, connector=None):
    settings.validate()
    strict_id(tenant_id, tenant=True)
    if connector is None:
        try:
            import pymysql
            connector=pymysql.connect
        except ImportError:
            return {'status':'BLOCKED','diagnosticCategory':'DEPENDENCY_MISSING','actualTargetVerified':False,
                    'gaps':['readonly_database_driver_missing'],'businessWrites':0}
    connection = None
    result = {'status':'BLOCKED', 'transaction':'REPEATABLE READ / CONSISTENT SNAPSHOT / READ ONLY',
              'tenantSha256':digest(tenant_id), 'businessWrites':0,'actualTargetVerified':False,
              'backendDatasourceBindingVerified':False}
    try:
        master = settings.datasources['master']
        connection = connector(host='127.0.0.1', port=3306, user=master['username'], password=master['password'],
                               database='ry-vue', autocommit=False, connect_timeout=5,
                               read_timeout=15, write_timeout=15)
        with connection.cursor() as cursor:
            cursor.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
            cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            cursor.execute('SELECT VERSION(),@@port,@@server_uuid,@@hostname,@@read_only,DATABASE()')
            version,port,uuid,hostname,server_readonly,current_schema=cursor.fetchone()
            if type(port) is not int or port!=3306 or current_schema!='ry-vue':
                raise PreflightBlocked('actual database target mismatch',category='TARGET_MISMATCH')
            cursor.execute('SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN (%s,%s,%s,%s) ORDER BY SCHEMA_NAME',tuple(SCHEMAS.values()))
            actual_schemas=sorted(row[0] for row in cursor.fetchall())
            if actual_schemas!=sorted(SCHEMAS.values()):
                raise PreflightBlocked('actual approved schemas unavailable',category='SCHEMA_MISSING')
            result.update({'currentSchema':current_schema,'actualSchemas':actual_schemas,'actualTargetVerified':True,
                           'targetVerificationSource':'live_mysql_identity_and_information_schema'})
            result.update({'version':str(version), 'port':port, 'serverUuidSha256':digest(uuid),
                           'hostnameSha256':digest(hostname), 'serverReadOnly':bool(server_readonly),
                           'fingerprint':digest(str(uuid) + ':3306:ry-vue,ry-erp,ry-mes,ry-iot')})
            cursor.execute('SHOW GRANTS')
            grants = [str(row[0]) for row in cursor.fetchall()]
            from inventory_local3306 import grants_select_only
            result['accountPosture'] = 'SELECT_ONLY' if grants_select_only(grants) else 'PRIVILEGED_ACCOUNT_READ_ONLY_TRANSACTION'
            metadata, indexes = _read_schema_metadata(cursor, TABLES)
            result['metadata'] = metadata
            stock_precision_gaps = []
            for stock_table in NATIVE_COLUMNS:
                key = 'ry-erp.' + stock_table
                stock_precision_gaps.extend(native_precision_gaps(key, metadata.get(key, {}).get('columns', {})))
            result['stockNativePrecision'] = {
                'status':'BLOCKED' if stock_precision_gaps else 'NATIVE_METADATA_MATCH',
                'requiredPrecision':18, 'requiredScale':4, 'gaps':stock_precision_gaps,
                'sourceCertification':'NOT_PERFORMED',
                'migrationGuard':'stock_native_precision_migration.py',
                'requiresIndependentReviewBackupMaintenanceAndSignedExecution':True}
            procurement_schema = certified_schema_evidence(metadata, indexes)
            result['procurementSchema'] = procurement_schema
            result['certifiedReceiptIndexContract'] = ('LIVE_METADATA_MATCH'
                if procurement_schema['status'] == 'SCHEMA_METADATA_MATCH' else 'UNVERIFIED')
            gaps = sorted(set(metadata_gaps(metadata) + procurement_schema['gaps']))
            result['gaps'] = gaps
            if gaps:
                result['reason'] = 'schema_engine_or_native_precision_not_verified'
                result['diagnosticCategory']='SCHEMA_OR_PRECISION_UNVERIFIED' if any('native DECIMAL' in gap or 'engine must' in gap for gap in gaps) else 'SCHEMA_UNVERIFIED'
                return result
            cursor.execute("SELECT COUNT(*) FROM `ry-vue`.`sys_tenant` WHERE tenant_id=%s AND status='0' AND del_flag='0'", (tenant_id,))
            active_count = cursor.fetchone()[0]
            result['activeTenantCount'] = active_count
            require(active_count == 1, 'exactly one active tenant required')
            counts = {}
            for schema, tables in TABLES.items():
                for table in tables:
                    key = schema + '.' + table
                    where = " AND del_flag='0'" if 'del_flag' in metadata[key]['columns'] else ''
                    cursor.execute(f'SELECT COUNT(*) FROM `{schema}`.`{table}` WHERE tenant_id=%s' + where, (tenant_id,))
                    counts[key] = cursor.fetchone()[0]
            result['tenantRowCounts'] = counts
            cursor.execute("SELECT COUNT(*),COALESCE(SUM(stock_quantity),0),COALESCE(SUM(reserved_quantity),0) "
                           "FROM `ry-erp`.`wm_material_stock` WHERE tenant_id=%s AND del_flag='0'", (tenant_id,))
            stock_count, quantity, reserved = cursor.fetchone()
            result['stockAggregate'] = {'rows':stock_count, 'quantity':_db_decimal(quantity), 'reserved':_db_decimal(reserved),
                                       'notAvailableBalanceOrSourceProof':True, 'missingRowsDoNotProveZero':True}
            cursor.execute("SELECT COUNT(*) FROM `ry-erp`.`wm_material_stock_tx` WHERE tenant_id=%s AND del_flag='0' "
                           "AND (source_doc_type IS NULL OR source_doc_type='' OR source_doc_id IS NULL)", (tenant_id,))
            missing_sources = cursor.fetchone()[0]
            result['stockOrigin'] = {'transactionRowsWithMissingSource':missing_sources,
                'status':'EMPTY_OBSERVED' if stock_count == 0 else 'UNVERIFIED',
                'requiresFullSourceLedgerAndReceiptReconciliation':True,
                'presenceOfTransactionsDoesNotProveStockOrigin':True}
            # 空快照只表示未观察到行；没有来源范围/完整性证明，不能认定可靠库存零。
            result['gaps'].append('stock_balance_source_reconciliation_not_established' if stock_count
                                  else 'empty_stock_snapshot_completeness_not_established')
            result['status'] = 'OBSERVED'
        return result
    except Exception as error:
        errno = error.args[0] if error.args and type(error.args[0]) is int else None
        return {**result, 'status':'BLOCKED', 'reason':'database_probe_failed_details_suppressed',
                'mysqlErrorCode':errno,'diagnosticCategory':diagnostic_category(error),
                'actualTargetVerified':False,'gaps':['live_database_metadata_precision_and_stock_origin_unverified']}
    finally:
        if connection is not None:
            cleanup_failed=False
            for cleanup in (connection.rollback,connection.close):
                try:cleanup()
                except Exception:cleanup_failed=True
            if cleanup_failed:
                result.update({'status':'BLOCKED','diagnosticCategory':'READONLY_CLEANUP_FAILED'})
                result.setdefault('gaps',[]).append('readonly_database_connection_cleanup_unverified')

def _file_digest(path):
    before = path.stat()
    result = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda:handle.read(1024 * 1024), b''):
            result.update(block)
    after = path.stat()
    require((before.st_size, before.st_mtime_ns) == (after.st_size, after.st_mtime_ns), 'artifact changed during inspection')
    return {'sha256':result.hexdigest(), 'size':after.st_size,
            'mtime':datetime.fromtimestamp(after.st_mtime, LOCAL_TIMEZONE).isoformat(), 'notLoadedArtifactProof':True}

def process_evidence(root=ROOT):
    root = Path(root)
    result = {'observedAt':datetime.now(LOCAL_TIMEZONE).isoformat(), 'ports':{}, 'listeners':[],
              'notLoadedHeadAttestation':True, 'targetSnapshots':{}}
    for port in (3306, 8080):
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=2):
                result['ports'][str(port)] = 'REACHABLE'
        except OSError:
            result['ports'][str(port)] = 'UNREACHABLE'
    if os.name == 'nt':
        # 命令行仅在内存中捕获；绝不保存或打印。
        script = "$ErrorActionPreference='Stop'; $items=@(); foreach($c in (Get-NetTCPConnection -State Listen -LocalPort 8080,3306)){ $p=Get-CimInstance Win32_Process -Filter ('ProcessId='+$c.OwningProcess); $items += [PSCustomObject]@{port=$c.LocalPort;pid=$p.ProcessId;name=$p.Name;startedAt=([DateTimeOffset]$p.CreationDate).ToString('o');commandLine=$p.CommandLine} }; ConvertTo-Json -InputObject $items -Compress"
        try:
            completed = subprocess.run(['powershell', '-NoProfile', '-Command', script],
                                       capture_output=True, text=True, timeout=15, check=False)
            require(completed.returncode == 0 and len(completed.stdout) < MAX_DOCUMENT, 'process inspection unavailable')
            entries = json.loads(completed.stdout)
            for entry in entries:
                command = entry.get('commandLine') or ''
                evidence = {key:entry[key] for key in ('port', 'pid', 'name', 'startedAt')}
                evidence['commandLineSha256'] = digest(command)
                evidence['devArgumentObserved'] = bool(re.search(r'(?:--|-D)spring.profiles.active=dev(?:\s|$)', command))
                evidence['decisionDemoArgumentObserved'] = 'decision-demo' in command
                arg_files = []
                for quoted, plain in re.findall(r'@(?:"([^"\r\n]+)"|([^\s]+))', command):
                    path = Path(quoted or plain)
                    if path.is_file() and path.stat().st_size <= MAX_DOCUMENT:
                        text = path.read_text(encoding='utf-8-sig')
                        arg_files.append({'sha256':digest(text), 'hasTargetClasses':bool(re.search(r'target[/\\]classes', text)),
                                          'notLoadedArtifactProof':True})
                evidence['argumentFiles'] = arg_files
                result['listeners'].append(evidence)
        except (OSError, ValueError, KeyError, TypeError, PreflightBlocked, subprocess.SubprocessError):
            result['processInspection'] = 'UNAVAILABLE_DETAILS_SUPPRESSED'
    candidates = ('ruoyi-admin/target/ruoyi-admin.jar',
                  'ruoyi-admin/target/classes/application.yml',
                  'ruoyi-admin/target/classes/application-dev.yml',
                  'ruoyi-modules/ruoyi-erp/target/classes/com/nexus/erp/pu/service/impl/PuPurchaseRequisitionServiceImpl.class',
                  'ruoyi-modules/ruoyi-erp/target/classes/com/nexus/erp/wm/service/impl/WmMaterialReceiveExecutorServiceImpl.class')
    for relative in candidates:
        path = root / relative
        if path.is_file():
            try:
                result['targetSnapshots'][relative] = _file_digest(path)
            except (OSError, PreflightBlocked):
                result['targetSnapshots'][relative] = {'status':'UNSTABLE_OR_UNAVAILABLE'}
    return result

def readiness(target, credentials, expected_head, authentication, database, process, *, attestor=None):
    target.validate()
    gaps, categories = [], set()
    if authentication.get('status') != 'VERIFIED':
        gaps.append('fresh_authenticated_tenant_principal_context_missing')
        categories.add(authentication.get('diagnosticCategory','AUTH_CONTEXT_UNVERIFIED'))
        categories.update(authentication.get('failureCategories', []))
    if database.get('status') != 'OBSERVED':
        gaps.append('live_dev3306_database_metadata_not_verified')
        categories.add(database.get('diagnosticCategory','DATABASE_UNVERIFIED'))
    database_gaps=database.get('gaps',[])
    gaps.extend(database_gaps)
    if any('native DECIMAL' in gap or 'engine must' in gap for gap in database_gaps):
        categories.add('SCHEMA_OR_PRECISION_UNVERIFIED')
    if any(gap in database_gaps for gap in ('stock_balance_source_reconciliation_not_established',
                                             'empty_stock_snapshot_completeness_not_established')):
        categories.add('STOCK_SOURCE_UNVERIFIED')
    loaded={'status':'BLOCKED','businessWriteAuthorized':False}
    if not isinstance(attestor, RuntimeAuthority):
        gaps.append('trusted_loaded_build_and_effective_target_attestation_missing')
        categories.add('BUILD_ATTESTATION_MISSING')
        loaded['diagnosticCategory']='BUILD_ATTESTATION_MISSING'
    elif authentication.get('status')!='VERIFIED' or not isinstance(credentials,Credentials):
        gaps.append('loaded_runtime_attestation_requires_fresh_authenticated_context')
        categories.add('BUILD_ATTESTATION_AUTH_CONTEXT_UNVERIFIED')
        loaded['diagnosticCategory']='BUILD_ATTESTATION_AUTH_CONTEXT_UNVERIFIED'
    else:
        try:
            require(isinstance(expected_head,str) and re.fullmatch(r'[0-9a-f]{40}',expected_head) is not None,
                    'exact final committed Git HEAD required')
            require(attestor.require_current(target=target,expected_head=expected_head,
                    tenant_id=credentials.tenant_id,principal_sha256=authentication['principalSha256']) is None,
                    'runtime authority must enforce, not return a flag')
            from procurement_local_runtime import LocalDevelopmentRuntimeAuthority
            if isinstance(attestor,LocalDevelopmentRuntimeAuthority):
                loaded.update(attestor.evidence())
                loaded['businessWriteAuthorized']=False
            else:
                loaded.update({'status':'VERIFIED','source':'independent_enforcing_runtime_authority'})
        except Exception as error:
            from procurement_runtime_attestation import ATTESTATION_CATEGORIES
            from procurement_local_runtime import LOCAL_RUNTIME_CATEGORIES
            category=diagnostic_category(error)
            category=category if isinstance(category,str) and category in ATTESTATION_CATEGORIES|LOCAL_RUNTIME_CATEGORIES else 'BUILD_ATTESTATION_REJECTED'
            gaps.append('trusted_loaded_build_and_effective_target_attestation_rejected')
            categories.add(category)
            loaded['diagnosticCategory']=category
    return {'status':'BLOCKED' if gaps else 'READ_ONLY_READY','gaps':sorted(set(gaps)),
            'diagnosticCategories':sorted(categories),'loadedRuntime':loaded,'businessWriteAuthorized':False,
            'requiresIndependentWriteAndLifecycleAuthority':True}

def inspect_local(*, root=ROOT, credential_path=None, config_dir=None, attestor=None, expected_head=None, runtime_check_mode='local-dev'):
    from procurement_local_runtime import RUNTIME_CHECK_MODES,create_runtime_authority
    if type(runtime_check_mode)is not str or runtime_check_mode not in RUNTIME_CHECK_MODES:
        raise PreflightBlocked('运行检查模式不受支持',category='RUNTIME_CHECK_MODE_UNSUPPORTED')
    target = RuntimeTarget().validate()
    observed = datetime.now(LOCAL_TIMEZONE)
    git = {'branch':subprocess.check_output(['git', 'branch', '--show-current'], cwd=root, text=True).strip(),
           'head':subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
           'commitDate':subprocess.check_output(['git', 'log', '-1', '--format=%cI'], cwd=root, text=True).strip()}
    dirty=subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True)
    git.update({'dirtyEntryCount':len(dirty.splitlines()),'workingTreeStateSha256':digest(dirty),
                'finalSourceCommitAndLoadedArtifactVerificationRequired':runtime_check_mode=='strict'})
    process = process_evidence(root)
    report = {'schemaVersion':1, 'observedAt':observed.isoformat(), 'mode':'READ_ONLY',
              'git':git, 'target':target.evidence(), 'process':process,'runtimeCheckMode':runtime_check_mode,
              'businessWrites':0, 'serviceLifecycleActions':0}
    credentials,auth=resolve_existing_authentication(target=target,path=credential_path)
    report['authentication'] = auth
    try:
        settings = settings_from_files(root, config_dir=config_dir)
        report['configuration'] = settings.summary
        if credentials is None:
            db = {'status':'BLOCKED', 'gaps':['explicit_tenant_context_missing_no_database_business_reads']}
        else:
            db = database_evidence(settings, credentials.tenant_id)
    except (PreflightBlocked,OSError,ImportError) as error:
        db={'status':'BLOCKED','gaps':['local_config_or_read_driver_unavailable_details_suppressed'],
            'diagnosticCategory':diagnostic_category(error),'actualTargetVerified':False}
    report['database'] = db
    if runtime_check_mode=='strict':
        from procurement_runtime_attestation import management_metadata_evidence
        report['management']=management_metadata_evidence(root=root)
    else:
        report['management']={'status':'NOT_REQUIRED_FOR_LOCAL_DEV','businessWriteAuthorized':False,'apiReadRequests':0}
    if attestor is None and credentials is not None:
        attestor=create_runtime_authority(mode=runtime_check_mode,credentials=credentials,root=root,config_dir=config_dir)
    report.update(readiness(target, credentials, expected_head or git['head'], auth, db, process, attestor=attestor))
    return report

def evidence_directory(now=None):
    now = now or datetime.now(LOCAL_TIMEZONE)
    return PRIVATE_ROOT / (now.strftime('%Y%m%d') + '-procurement-execution') / 'runtime'

def main(argv=None):
    class SafeParser(argparse.ArgumentParser):
        def error(self,message):raise PreflightBlocked('invalid CLI arguments; contents suppressed')
    parser = SafeParser(description='Task C read-only runtime preflight', exit_on_error=False)
    parser.add_argument('--credential-file')
    parser.add_argument('--config-dir')
    parser.add_argument('--expected-head')
    parser.add_argument('--runtime-check',choices=('local-dev','strict'),default='local-dev')
    try:
        args = parser.parse_args(argv)
        report = inspect_local(credential_path=args.credential_file, config_dir=args.config_dir,
                               expected_head=args.expected_head,runtime_check_mode=args.runtime_check)
        now = datetime.now(LOCAL_TIMEZONE)
        path = private_file(evidence_directory(now) / ('runtime-preflight-' + now.strftime('%Y%m%dT%H%M%S%f') + '.json'))
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('x', encoding='utf-8') as handle:
            json.dump(report, handle, ensure_ascii=True, indent=2, allow_nan=False)
            handle.write('\n'); handle.flush(); os.fsync(handle.fileno())
        # 终端只给出固定诊断及数值，完整证据留在私有目录；不输出远端本体或凭证摘要。
        auth = report.get('authentication', {})
        auth_summary = {key:auth.get(key) for key in ('status', 'businessCode', 'diagnosticCategory')}
        if auth.get('httpStatus') is not None:
            auth_summary['httpStatus'] = auth['httpStatus']
        if auth.get('candidateCount', 0) > 1:
            for key in ('failureCategories', 'businessCodeCounts', 'httpStatusCounts'):
                auth_summary[key] = auth.get(key, [])
        print(json.dumps({'status':report['status'], 'observedAt':report['observedAt'],
                          'evidencePath':str(path), 'gaps':report['gaps'],
                          'authentication':auth_summary, 'diagnosticCategories':report['diagnosticCategories'],
                          'businessWriteAuthorized':False,'runtimeCheckMode':report.get('runtimeCheckMode'),
                          'runtimeCheckStatus':report.get('loadedRuntime',{}).get('status')}, ensure_ascii=True))
        return 2 if report['status'] == 'BLOCKED' else 0
    except (PreflightBlocked, argparse.ArgumentError, OSError, ValueError, TypeError):
        print('{"status":"BLOCKED","reason":"preflight_input_or_evidence_failure_details_suppressed","businessWriteAuthorized":false}')
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
