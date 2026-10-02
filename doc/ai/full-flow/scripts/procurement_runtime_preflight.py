"""Read-only Task C runtime evidence; NEVER grants business-write permission.

Only bounded GET getInfo and tenant-scoped SELECTs in a READ ONLY snapshot.
No login, migrations or service lifecycle. Config, target hashes and pid are NOT
loaded-build attestation. RuntimeAuthority is an external enforcing port; there
is intentionally no authority implementation or --apply flag in this module.
"""
from __future__ import annotations
from abc import ABC, abstractmethod
import argparse
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[4]
PRIVATE_ROOT = ROOT / '.codex' / 'full-flow-20260928'
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
DECIMAL_FIELDS = {
    'ry-erp.wm_material_stock': ('stock_quantity', 'reserved_quantity'),
    'ry-erp.wm_material_stock_tx': ('tx_quantity', 'before_quantity', 'after_quantity'),
    'ry-erp.pu_purchase_requisition_line': ('apply_quantity',),
    'ry-erp.pu_purchase_order_line': ('order_quantity', 'received_quantity'),
    'ry-erp.prd_material_requirement_detail': ('required_quantity',),
}

class PreflightBlocked(RuntimeError):
    """Fixed diagnostic; never wrap raw remote/config exceptions."""
    def __init__(self,message,*,category='UNVERIFIED'):
        super().__init__(message)
        self.category=category

def diagnostic_category(error):
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
            raise PreflightBlocked('HTTP request rejected; body suppressed',category='AUTH_FAILED' if error.code==401 else 'READ_PERMISSION_DENIED' if error.code==403 else 'HTTP_REJECTED') from None
        except (URLError, TimeoutError, OSError) as error:
            if request.get_method() == 'GET' and attempt + 1 < attempts:
                continue
            raise PreflightBlocked('HTTP transport unavailable; details suppressed',category=diagnostic_category(error)) from None
        except Exception as error:
            if isinstance(error,PreflightBlocked):raise
            raise PreflightBlocked('HTTP response unavailable; details suppressed',category=diagnostic_category(error)) from None

def response_id_strings(value,*,identity=False):
    """Only fresh raw-JSON integer IDs may normalize to strings, never floats.

    Python JSON integers retain all digits; do not pass JS-rounded IDs here.
    Non-ID numbers such as counts remain integers and quantities remain Decimal.
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
    """Authentication GET only. GET order/generate is forbidden."""
    def __init__(self, target, credentials, *, opener=None):
        self.target, self.credentials = target.validate(), credentials.validate()
        self._opener = opener or local_opener()

    def get(self, path='/system/user/getInfo'):
        require(safe_path(path) == '/system/user/getInfo', 'preflight permits getInfo only')
        request = Request(self.target.base_url.rstrip('/') + path,
                          headers=self.credentials.headers(), method='GET')
        return response_id_strings(request_document(request,self._opener,attempts=3))

def authentication_evidence(client, credentials):
    result = {**credentials.evidence(), 'status':'BLOCKED'}
    try:
        document = client.get('/system/user/getInfo')
        code = document.get('code') if isinstance(document, dict) else None
        result['businessCode'] = code if type(code) is int else None
        if type(code) is not int or code != 200:
            result['reason'] = 'authentication_not_successful'
            result['diagnosticCategory']='AUTH_FAILED'
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
                       'observedAt':datetime.now().astimezone().isoformat()})
        return result
    except Exception as error:
        result['reason'] = 'authentication_probe_unavailable_or_invalid'
        result['diagnosticCategory']=diagnostic_category(error)
        return result

class RuntimeAuthority(ABC):
    """External trust root, NOT a report parser or flag.

    Must independently verify LIVE process/startup, loaded artifact digest/HEAD,
    effective dev profile and backend datasource UUID/schema, principal/tenant,
    freshness and runtime overrides. Recheck EACH action/recovery; ambiguity raises.
    expected_head MUST be the final committed source baseline, not the starting
    task HEAD or process timestamp. Validate the actual LOADED artifact, never
    only the current on-disk target. Return exactly None on success; no issuer here.
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
            fields[key] = _resolve(context[config_key], context, env)
        datasources[name] = fields
    summary = {'sourceKind':'source_resources' if base == approved[0] else 'target_resources',
               'filesSha256':fingerprints,
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
            for column in DECIMAL_FIELDS.get(key, ()):
                spec = columns.get(column, {})
                if spec.get('type') != 'decimal' or type(spec.get('precision')) is not int or type(spec.get('scale')) is not int:
                    gaps.append(key + '.' + column + ': native DECIMAL required')
    return sorted(gaps)

def certified_index_gaps(rows):
    """Mirror current writer's permanent receipt unique-key contract, read-only."""
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
              'tenantSha256':digest(tenant_id), 'businessWrites':0,'actualTargetVerified':False}
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
            metadata = {}
            for schema, tables in TABLES.items():
                marks = ','.join(['%s'] * len(tables))
                cursor.execute('SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s '
                               'AND TABLE_TYPE=\'BASE TABLE\' AND TABLE_NAME IN (' + marks + ')', (schema, *tables))
                for table, engine in cursor.fetchall():
                    require(table in tables, 'unexpected table metadata')
                    metadata[schema + '.' + table] = {'engine':engine, 'columns':{}}
                cursor.execute('SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,NUMERIC_PRECISION,NUMERIC_SCALE '
                               'FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME IN (' + marks +
                               ') ORDER BY TABLE_NAME,ORDINAL_POSITION', (schema, *tables))
                for table, column, kind, precision, scale in cursor.fetchall():
                    key = schema + '.' + table
                    require(key in metadata and isinstance(column, str), 'unexpected column metadata')
                    metadata[key]['columns'][column] = {'type':kind, 'precision':precision, 'scale':scale}
            result['metadata'] = metadata
            gaps = metadata_gaps(metadata)
            result['gaps'] = gaps
            if gaps:
                result['reason'] = 'schema_engine_or_native_precision_not_verified'
                return result
            cursor.execute('SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART '
                           'FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s '
                           'AND NON_UNIQUE=0 ORDER BY INDEX_NAME,SEQ_IN_INDEX',
                           ('ry-erp','pu_certified_purchase_command'))
            index_gaps=certified_index_gaps(cursor.fetchall())
            result['gaps'].extend(index_gaps)
            result['certifiedReceiptIndexContract']='UNVERIFIED' if index_gaps else 'LIVE_METADATA_MATCH'
            if index_gaps:
                result.update({'status':'BLOCKED','diagnosticCategory':'SCHEMA_UNVERIFIED'})
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
                                       'notAvailableBalanceOrSourceProof':True}
            cursor.execute("SELECT COUNT(*) FROM `ry-erp`.`wm_material_stock_tx` WHERE tenant_id=%s AND del_flag='0' "
                           "AND (source_doc_type IS NULL OR source_doc_type='' OR source_doc_id IS NULL)", (tenant_id,))
            missing_sources = cursor.fetchone()[0]
            result['stockOrigin'] = {'transactionRowsWithMissingSource':missing_sources,
                'status':'EMPTY_OBSERVED' if stock_count == 0 else 'UNVERIFIED',
                'requiresFullSourceLedgerAndReceiptReconciliation':stock_count != 0,
                'presenceOfTransactionsDoesNotProveStockOrigin':True}
            if stock_count:
                result['gaps'].append('stock_balance_source_reconciliation_not_established')
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
            'mtime':datetime.fromtimestamp(after.st_mtime).astimezone().isoformat(), 'notLoadedArtifactProof':True}

def process_evidence(root=ROOT):
    root = Path(root)
    result = {'observedAt':datetime.now().astimezone().isoformat(), 'ports':{}, 'listeners':[],
              'notLoadedHeadAttestation':True, 'targetSnapshots':{}}
    for port in (3306, 8080):
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=2):
                result['ports'][str(port)] = 'REACHABLE'
        except OSError:
            result['ports'][str(port)] = 'UNREACHABLE'
    if os.name == 'nt':
        # Command lines are captured in memory ONLY; never save/print them.
        script = "$ErrorActionPreference='Stop'; $items=@(); foreach($c in (Get-NetTCPConnection -State Listen -LocalPort 8080,3306)){ $p=Get-CimInstance Win32_Process -Filter ('ProcessId='+$c.OwningProcess); $items += [PSCustomObject]@{port=$c.LocalPort;pid=$p.ProcessId;name=$p.Name;startedAt=$p.CreationDate.ToString('o');commandLine=$p.CommandLine} }; ConvertTo-Json -InputObject $items -Compress"
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
    if database.get('status') != 'OBSERVED':
        gaps.append('live_dev3306_database_metadata_not_verified')
        categories.add(database.get('diagnosticCategory','DATABASE_UNVERIFIED'))
    database_gaps=database.get('gaps',[])
    gaps.extend(database_gaps)
    if any('native DECIMAL' in gap or 'engine must' in gap for gap in database_gaps):
        categories.add('SCHEMA_OR_PRECISION_UNVERIFIED')
    if 'stock_balance_source_reconciliation_not_established' in database_gaps:
        categories.add('STOCK_SOURCE_UNVERIFIED')
    if not isinstance(attestor, RuntimeAuthority):
        gaps.append('trusted_loaded_build_and_effective_target_attestation_missing')
        categories.add('BUILD_ATTESTATION_MISSING')
    elif not gaps:
        try:
            require(re.fullmatch(r'[0-9a-f]{40}',expected_head) is not None,'exact final committed Git HEAD required')
            require(attestor.require_current(target=target,expected_head=expected_head,
                    tenant_id=credentials.tenant_id,principal_sha256=authentication['principalSha256']) is None,
                    'runtime authority must enforce, not return a flag')
        except Exception:
            gaps.append('trusted_loaded_build_and_effective_target_attestation_rejected')
            categories.add('BUILD_ATTESTATION_REJECTED')
    return {'status':'BLOCKED' if gaps else 'READ_ONLY_READY','gaps':sorted(set(gaps)),
            'diagnosticCategories':sorted(categories),'businessWriteAuthorized':False,
            'requiresIndependentWriteAndLifecycleAuthority':True}

def inspect_local(*, root=ROOT, credential_path=None, config_dir=None, attestor=None):
    target = RuntimeTarget().validate()
    observed = datetime.now().astimezone()
    git = {'branch':subprocess.check_output(['git', 'branch', '--show-current'], cwd=root, text=True).strip(),
           'head':subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
           'commitDate':subprocess.check_output(['git', 'log', '-1', '--format=%cI'], cwd=root, text=True).strip()}
    dirty=subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True)
    git.update({'dirtyEntryCount':len(dirty.splitlines()),'workingTreeStateSha256':digest(dirty),
                'finalSourceCommitAndLoadedArtifactVerificationRequired':True})
    process = process_evidence(root)
    report = {'schemaVersion':1, 'observedAt':observed.isoformat(), 'mode':'READ_ONLY',
              'git':git, 'target':target.evidence(), 'process':process,
              'businessWrites':0, 'serviceLifecycleActions':0}
    credentials = None
    try:
        credentials = load_credentials(target=target, path=credential_path)
        auth = authentication_evidence(ReadOnlyClient(target, credentials), credentials)
    except (PreflightBlocked,OSError) as error:
        auth={'status':'BLOCKED','reason':'explicit_authentication_context_missing_or_invalid',
              'diagnosticCategory':diagnostic_category(error)}
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
    report.update(readiness(target, credentials, git['head'], auth, db, process, attestor=attestor))
    return report

def evidence_directory(now=None):
    now = now or datetime.now().astimezone()
    return PRIVATE_ROOT / (now.strftime('%Y%m%d') + '-unified-procurement') / 'runtime'

def main(argv=None):
    class SafeParser(argparse.ArgumentParser):
        def error(self,message):raise PreflightBlocked('invalid CLI arguments; contents suppressed')
    parser = SafeParser(description='Task C read-only runtime preflight', exit_on_error=False)
    parser.add_argument('--credential-file')
    parser.add_argument('--config-dir')
    try:
        args = parser.parse_args(argv)
        report = inspect_local(credential_path=args.credential_file, config_dir=args.config_dir)
        now = datetime.now().astimezone()
        path = private_file(evidence_directory(now) / ('runtime-preflight-' + now.strftime('%Y%m%dT%H%M%S%f') + '.json'))
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('x', encoding='utf-8') as handle:
            json.dump(report, handle, ensure_ascii=True, indent=2, allow_nan=False)
            handle.write('\n'); handle.flush(); os.fsync(handle.fileno())
        print(json.dumps({'status':report['status'], 'observedAt':report['observedAt'],
                          'evidencePath':str(path), 'gaps':report['gaps'],'diagnosticCategories':report['diagnosticCategories'], 'businessWriteAuthorized':False}, ensure_ascii=True))
        return 2 if report['status'] == 'BLOCKED' else 0
    except (PreflightBlocked, argparse.ArgumentError, OSError, ValueError, TypeError):
        print('{"status":"BLOCKED","reason":"preflight_input_or_evidence_failure_details_suppressed","businessWriteAuthorized":false}')
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
