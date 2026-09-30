"""仅限 Task3a 的 HTTP 基础操作、持久化证据及失败即关闭通路的只读预检。

本模块不编排任何会修改数据的工作流动作。盘点写阶段的资格必须由
另一次明确授权的盘点运行确立；本模块绝不改变该资格。
"""

import hashlib
import json
import os
import re
import tempfile
import time
from datetime import date, datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


class PreflightError(RuntimeError):
    pass


class ApiError(RuntimeError):
    def __init__(self, method, path, status, category):
        safe_path = _safe_path(path)
        for name in ('NEXUS_FULL_FLOW_TOKEN', 'NEXUS_FULL_FLOW_CLIENT_ID'):
            secret = os.environ.get(name)
            if secret:
                safe_path = safe_path.replace(secret, '[redacted]')
        self.method, self.path, self.status, self.category = method, safe_path, status, category
        super().__init__(f'{method} {safe_path}: {category} (HTTP {status})')


def _safe_path(path):
    # 错误信息绝不回显查询参数值，例如 selectFlowId 或密码。
    return urlsplit(path).path


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None  # 绝不向任何重定向目标转发 Authorization/clientid。


class ApiClient:
    """经认证的本地只读传输；写权限必须由 Task3b 授予。"""
    def __init__(self, base_url='http://127.0.0.1:8080', *, opener=None,
                 sleep=time.sleep, apply=False, preflight_approved=False):
        parsed = urlsplit(base_url)
        if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.username or parsed.password or parsed.path not in ('', '/'):
            raise ValueError('Only local HTTP base URLs are supported')
        self._base_url = base_url.rstrip('/')
        # 同时禁用环境代理配置和 HTTP 重定向跟随。
        self._opener, self._sleep = opener or build_opener(ProxyHandler({}), _NoRedirect()).open, sleep
        # 忽略旧标志；Task3a 没有写入能力的签发方。
        # 保留旧参数仅为让旧调用方在修改数据的边界失败，而非初始化时失败。

    def get(self, path, query=None):
        return self._request('GET', path, query=query)

    def post(self, path, payload=None, query=None):
        raise PreflightError('Task3b independent write capability required; Task3a is read-only')

    def put(self, path, payload=None, query=None):
        raise PreflightError('Task3b independent write capability required; Task3a is read-only')

    def _build_request(self, method, path, payload=None, query=None):
        """只构建请求而不发送，用于离线检查请求体和请求头。"""
        if not isinstance(path, str) or not path.startswith('/') or path.startswith('//') or '?' in path or '#' in path:
            raise ValueError('Provide an absolute API path and separate query mapping')
        if any(segment in ('.', '..') for segment in path.split('/')):
            raise ValueError('Invalid API path')
        token = os.environ.get('NEXUS_FULL_FLOW_TOKEN')
        client_id = os.environ.get('NEXUS_FULL_FLOW_CLIENT_ID')
        if not token or not client_id:
            raise PreflightError('Missing token or client ID in environment')
        url = self._base_url + path + ('?' + urlencode(query) if query else '')
        headers = {'Authorization': 'Bearer ' + token, 'clientid': client_id, 'Accept': 'application/json'}
        body = None
        if method != 'GET' and payload is not None:
            body = json.dumps(payload, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        return Request(url, data=body, headers=headers, method=method)

    def _request(self, method, path, payload=None, query=None):
        if method != 'GET':
            raise PreflightError('Task3b independent write capability required; Task3a is read-only')
        request = self._build_request(method, path, payload, query)
        attempts = 3 if method == 'GET' else 1
        for attempt in range(attempts):
            try:
                with self._opener(request, timeout=10) as response:
                    document = json.loads(response.read())
                if not (200 <= getattr(response, 'status', 200) < 300):
                    raise ApiError(method, path, getattr(response, 'status', None), 'unexpected_status')
                if isinstance(document, dict) and 'code' in document and document['code'] != 200:
                    raise ApiError(method, path, 200, 'business_failure')
                return document
            except HTTPError as exc:
                # 不读取或存储服务端错误响应体；后端可能回显 JWT 或个人身份信息。
                if method == 'GET' and exc.code in (429, 502, 503, 504) and attempt < attempts - 1:
                    self._sleep(0.2 * (attempt + 1))
                    continue
                raise ApiError(method, path, exc.code, 'http_failure') from None
            except (URLError, TimeoutError) as exc:
                if method == 'GET' and attempt < attempts - 1:
                    self._sleep(0.2 * (attempt + 1))
                    continue
                raise ApiError(method, path, None, 'transport_failure') from None
            except (ValueError, UnicodeDecodeError):
                raise ApiError(method, path, 200, 'invalid_json') from None


# 所有清单路径必须位于此被 Git 忽略的目录树中。禁止自动
# 改为受 Git 跟踪的目录，也不使用全局状态或环境中隐含的凭证。
MANIFEST_DIR = Path(__file__).resolve().parents[4] / '.codex' / 'full-flow-20260928'
_ID = re.compile(r'^[0-9]+$')
_CODE = re.compile(r'^[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}$')
_KEY = re.compile(r'^[A-Za-z][A-Za-z0-9_]{0,63}$')


def _id(value):
    if not isinstance(value, str) or not _ID.fullmatch(value):
        raise ValueError('Entity and parent IDs must be unsigned decimal strings')
    return value


def _manifest_path(path):
    resolved = Path(path).resolve()
    if resolved.parent != MANIFEST_DIR.resolve() or resolved.suffix != '.json':
        raise ValueError('Manifest must be a JSON file in the repository ignored directory')
    return resolved


class Manifest:
    def __init__(self, tenant_id):
        self.tenant_id = _id(tenant_id)
        self.records = []
        self._source_digest = None  # None 表示此实例预期文件尚不存在。

    def add(self, domain, table, entity_id, business_code, parent_refs):
        if not all(isinstance(v, str) and _KEY.fullmatch(v) for v in (domain, table)):
            raise ValueError('Invalid domain/table')
        entity_id = _id(entity_id)
        if not isinstance(business_code, str) or not _CODE.fullmatch(business_code):
            raise ValueError('Invalid business code')
        if not isinstance(parent_refs, dict) or not all(isinstance(k, str) and _KEY.fullmatch(k) for k in parent_refs):
            raise ValueError('Invalid parent references')
        parents = {k: _id(v) for k, v in parent_refs.items()}
        if any(r['domain'] == domain and r['table'] == table and
               (r['entityId'] == entity_id or r['businessCode'] == business_code)
               for r in self.records):
            raise ValueError('Duplicate manifest entity or business code')
        now = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
        record = {'tenantId': self.tenant_id, 'domain': domain, 'table': table,
                  'entityId': entity_id, 'businessCode': business_code,
                  'parentRefs': parents, 'createdAt': now, 'updatedAt': now}
        self.records.append(record)
        return record

    def save(self, path):
        path = _manifest_path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        lock = path.with_suffix('.json.lock')
        # 原子的 O_EXCL 支持跨进程互斥；残留锁导致校验失败并关闭通路，绝不抢占。
        try:
            fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        except FileExistsError:
            raise PreflightError('Manifest locked; retry only after lock owner is resolved') from None
        temp = None
        try:
            os.close(fd)
            current = hashlib.sha256(path.read_bytes()).digest() if path.exists() else None
            if current != self._source_digest:
                raise PreflightError('Manifest changed since load; reload before saving')
            with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent,
                                             prefix='.manifest-', suffix='.tmp', delete=False) as handle:
                temp = Path(handle.name)
                json.dump({'tenantId': self.tenant_id, 'records': self.records}, handle, ensure_ascii=False, indent=2)
                handle.flush()
                os.fsync(handle.fileno())
            digest = hashlib.sha256(temp.read_bytes()).digest()
            os.replace(temp, path)
            self._source_digest = digest
        finally:
            if temp is not None and temp.exists():
                temp.unlink()
            lock.unlink()

    @classmethod
    def load(cls, path):
        raw = _manifest_path(path).read_bytes()
        data = json.loads(raw)
        manifest = cls(data['tenantId'])
        for record in data['records']:
            if set(record) != {'tenantId', 'domain', 'table', 'entityId', 'businessCode', 'parentRefs', 'createdAt', 'updatedAt'} or record['tenantId'] != manifest.tenant_id:
                raise ValueError('Invalid manifest record')
            added = manifest.add(record['domain'], record['table'], record['entityId'], record['businessCode'], record['parentRefs'])
            for field in ('createdAt', 'updatedAt'):
                if not isinstance(record[field], str) or not record[field]:
                    raise ValueError('Invalid timestamp')
                added[field] = record[field]
        manifest._source_digest = hashlib.sha256(raw).digest()
        return manifest


def _rows(client, path, query=None):
    """读取全部分页；拒绝不完整或伪成功的列表结果。"""
    results = []
    for page in range(1, 101):
        response = client.get(path, {**(query or {}), 'pageNum': page, 'pageSize': 100})
        if not isinstance(response, dict) or not isinstance(response.get('total'), int) or not isinstance(response.get('rows'), list):
            raise PreflightError(f'Invalid list result for {_safe_path(path)}')
        total = response['total']
        if total < 0 or len(response['rows']) > 100 or total < len(results) + len(response['rows']):
            raise PreflightError(f'Invalid pagination for {_safe_path(path)}')
        results.extend(response['rows'])
        if len(results) == total:
            return results
        if not response['rows']:
            raise PreflightError(f'Incomplete pagination for {_safe_path(path)}')
    raise PreflightError('Too many pages; preflight cannot prove uniqueness')


def _inventory_row(schema, table, tenant_id, field, value, *, id_field=None):
    if table in schema.get('unverified_candidates', []):
        raise PreflightError(f'{table} has unverified status in inventory')
    columns = schema.get('candidate_columns', {}).get(table, [])
    candidates = schema.get('candidates', {}).get(table, [])
    required = {'tenant_id', field} | ({id_field} if id_field else set())
    if not isinstance(columns, list) or not required.issubset(columns) or not isinstance(candidates, list):
        raise PreflightError(f'{table} lacks tenant-scoped ID evidence')
    matches = [dict(zip(columns, row)) for row in candidates if isinstance(row, list) and
               len(row) == len(columns) and row[columns.index('tenant_id')] == tenant_id and
               row[columns.index(field)] == value]
    if len(matches) != 1:
        raise PreflightError(f'{table} absent or ambiguous in inventory')
    if id_field and (not isinstance(matches[0][id_field], str) or
                     not _ID.fullmatch(matches[0][id_field])):
        raise PreflightError(f'{table} inventory ID is not a decimal string')
    return matches[0]


def _api_id(value, label):
    # 不超过 JS MAX_SAFE_INTEGER 的 JSON 整数是精确的；更大的数值 ID
    # 必须由 API 序列化为十进制字符串，以避免精度丢失。
    if type(value) is int and 0 < value <= 2**53 - 1:
        return str(value)
    if isinstance(value, str) and _ID.fullmatch(value):
        return value
    raise PreflightError(f'{label} ID is not a safe decimal ID')


def _detail(client, path, row, id_field):
    entity_id = _api_id(row.get(id_field), path)
    response = client.get(f'{path}/{entity_id}')
    detail = response.get('data') if isinstance(response, dict) else None
    if not isinstance(detail, dict) or _api_id(detail.get(id_field), path) != entity_id:
        raise PreflightError(f'{path} detail ID mismatch')
    return detail


def _main_date(value, label):
    # Java Date 的 JSON 格式为 yyyy-MM-dd HH:mm:ss；也接受 ISO 8601 日期或时间戳。
    if not isinstance(value, str):
        raise PreflightError(f'{label} date invalid')
    try:
        if re.fullmatch(r'\d{4}-\d{2}-\d{2}', value):
            return date.fromisoformat(value)
        if re.fullmatch(r'\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})?', value):
            return datetime.fromisoformat(value.replace('Z', '+00:00')).date()
    except ValueError:
        pass
    raise PreflightError(f'{label} date invalid')


def read_only_preflight(client, inventory, *, tenant_id, client_code, bom_id,
                        routing_code, warehouse_code, process_keys, human_ack=False):
    """只读检查；人工确认绝不会开启盘点写入守卫。

    管理员快照确认仅授权审查，不授权后续写入。
    仍必须重新盘点，并提供按租户限定的候选 ID；
    含未核验主数据的旧管理员快照即使有 human_ack 也不能通过。
    """
    tenant_id = _id(tenant_id)
    bom_id = _id(bom_id)
    if not isinstance(inventory, dict) or not isinstance(inventory.get('snapshot'), dict):
        raise PreflightError('Invalid inventory')
    snapshot = inventory['snapshot']
    if snapshot.get('verified') is not True or inventory.get('missing') or not snapshot.get('db_date'):
        raise PreflightError('Unverified/incomplete inventory')
    if snapshot['db_date'] != date.today().isoformat():
        raise PreflightError('Inventory snapshot is stale')
    if (inventory.get('usable_for_write_phase') is not True or
            str(snapshot.get('grants', '')).startswith('admin read-only')) and human_ack is not True:
        raise PreflightError('Inventory write gate closed; independent human acknowledgement required for read-only review')
    if not isinstance(process_keys, (list, tuple)) or not process_keys or not all(isinstance(k, str) and _KEY.fullmatch(k) for k in process_keys) or len(set(process_keys)) != len(process_keys):
        raise PreflightError('Explicit unique Flowable process keys required')
    schemas = inventory.get('schemas', {})
    erp, system = schemas.get('ry-erp', {}), schemas.get('ry-vue', {})
    _inventory_row(system, 'sys_tenant', tenant_id, 'tenant_id', tenant_id)
    # 联系任何实时 API 前，先检查盘点中的 ID。只有业务编码的旧快照
    # 不能确立归属，必须重新获取。
    targets = (('customer', '/erp/bd/client', 'bd_client', 'clientCode', 'client_code', client_code, 'clientId', 'client_id'),
               ('bom', '/erp/bd/bom', 'bd_bom', 'bomId', 'bom_id', bom_id, 'bomId', 'bom_id'),
               ('routing', '/erp/bd/routing', 'bd_routing', 'routingCode', 'routing_code', routing_code, 'routingId', 'routing_id'),
               ('warehouse', '/erp/wm/warehouse', 'wm_warehouse', 'warehouseCode', 'warehouse_code', warehouse_code, 'warehouseId', 'warehouse_id'))
    for _, _, table, _, field, value, _, primary in targets:
        _inventory_row(erp, table, tenant_id, field, value, id_field=primary)
    identity = client.get('/system/user/getInfo')
    user = identity.get('data', {}).get('user') if isinstance(identity, dict) else None
    if not isinstance(user, dict) or user.get('tenantId') != tenant_id:
        raise PreflightError('Authenticated tenant mismatch')
    user_id = _api_id(user.get('userId'), 'authenticated user')
    _inventory_row(system, 'sys_user', tenant_id, 'user_id', user_id)
    result = {'tenantId': tenant_id, 'userId': user_id, 'snapshotDate': snapshot['db_date']}
    for label, path, table, code_field, field, value, id_field, primary in targets:
        candidate = _inventory_row(erp, table, tenant_id, field, value, id_field=primary)
        expected_id = _api_id(candidate[primary], label)
        matches = [row for row in _rows(client, path + '/list', {code_field: value})
                   if isinstance(row, dict) and
                   (_api_id(row.get(code_field), label) == value if code_field == id_field else row.get(code_field) == value)]
        if len(matches) != 1 or _api_id(matches[0].get(id_field), label) != expected_id:
            raise PreflightError(f'{label} absent, ambiguous or ownership mismatch')
        detail = _detail(client, path, matches[0], id_field)
        if ((_api_id(detail.get(code_field), label) != value if code_field == id_field
             else detail.get(code_field) != value) or
                _api_id(detail.get(id_field), label) != expected_id):
            raise PreflightError(f'{label} detail mismatch')
        if 'tenantId' in detail and detail['tenantId'] != tenant_id:
            raise PreflightError(f'{label} tenant mismatch')
        result[label] = {id_field: expected_id, code_field: value}
        if label != 'customer' and detail.get('status') != 1:
            raise PreflightError(f'{label} not active')
        if label in ('bom', 'routing'):
            version_field = 'bomVersion' if label == 'bom' else 'routingVersion'
            version = detail.get(version_field)
            if not isinstance(version, str) or not version.strip():
                raise PreflightError(f'{label} version missing')
            result[label][version_field] = version
            if detail.get('isCurrent') != 1:
                raise PreflightError(f'{label} version not current')
            for date_field, before in (('effectiveDate', True), ('expiryDate', False)):
                field_date = detail.get(date_field)
                if field_date is not None:
                    parsed_date = _main_date(field_date, f'{label} {date_field}')
                    if (parsed_date > date.fromisoformat(snapshot['db_date']) if before
                            else parsed_date < date.fromisoformat(snapshot['db_date'])):
                        raise PreflightError(f'{label} outside effective period')
            material_field = 'parentMaterialId' if label == 'bom' else 'materialId'
            result[label]['materialId'] = _api_id(detail.get(material_field), label)
            if label == 'bom' and detail.get('routingId') is not None:
                result[label]['routingId'] = _api_id(detail['routingId'], 'BOM routing')
    bom, routing = result['bom'], result['routing']
    if bom['materialId'] != routing['materialId'] or ('routingId' in bom and bom['routingId'] != routing['routingId']):
        raise PreflightError('BOM/routing material or association mismatch')
    _inventory_row(erp, 'bd_material', tenant_id, 'material_id', bom['materialId'], id_field='material_id')
    bom_id, routing_id = bom['bomId'], routing['routingId']
    details = _rows(client, '/erp/bd/bom/detail/list', {'bomId': bom_id})
    if not details:
        raise PreflightError('BOM has no details')
    result['components'] = []
    for row in details:
        if not isinstance(row, dict) or _api_id(row.get('bomId'), 'BOM detail') != bom_id or row.get('status') != 1:
            raise PreflightError('BOM detail inactive or mismatched')
        # 旧行可能缺少冗余父级字段；经核验的 BOM 仍确定该明细的归属。
        if row.get('parentMaterialId') is not None and _api_id(row['parentMaterialId'], 'BOM detail parent') != bom['materialId']:
            raise PreflightError('BOM detail parent material mismatch')
        for field, is_start in (('effectiveDate', True), ('expiryDate', False)):
            bound = row.get(field)
            if bound is not None:
                if not isinstance(bound, str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}', bound):
                    raise PreflightError(f'BOM detail {field} invalid')
                try:
                    date.fromisoformat(bound)
                except ValueError as exc:
                    raise PreflightError(f'BOM detail {field} invalid') from exc
                if (bound > snapshot['db_date'] if is_start else bound < snapshot['db_date']):
                    raise PreflightError('BOM detail outside effective period')
        child_id = _api_id(row.get('childMaterialId'), 'BOM component')
        _inventory_row(erp, 'bd_material', tenant_id, 'material_id', child_id, id_field='material_id')
        result['components'].append(child_id)
    steps = client.get(f'/erp/bd/routing/steps/{routing_id}')
    if not isinstance(steps, dict) or not isinstance(steps.get('data'), list) or not steps['data']:
        raise PreflightError('Routing has no steps')
    for path in (f'/erp/bd/bom/verify/{bom_id}', f'/erp/bd/routing/verify/{routing_id}'):
        response = client.get(path)
        if not isinstance(response, dict) or response.get('data') != 'OK':
            raise PreflightError(f'{path} did not verify OK')
    result['flowDefinitions'] = {}
    for key in process_keys:
        matches = _rows(client, '/workflow/deploy/publishList', {'processKey': key})
        if not matches:
            raise PreflightError('Flowable definition absent')
        for row in matches:
            if (not isinstance(row, dict) or row.get('processKey') != key or
                    type(row.get('version')) is not int or row['version'] < 1):
                raise PreflightError('Flowable process key/version invalid')
        latest_version = max(row['version'] for row in matches)
        latest = [row for row in matches if row['version'] == latest_version]
        if len(latest) != 1:
            raise PreflightError('Flowable latest version ambiguous')
        definition = latest[0]
        version = definition['version']
        if (definition.get('suspended') is not False or
                not isinstance(definition.get('definitionId'), str) or not definition['definitionId'].strip() or
                not isinstance(definition.get('deploymentId'), str) or not definition['deploymentId'].strip()):
            raise PreflightError('Flowable latest version suspended or deployment missing')
        result['flowDefinitions'][key] = {'definitionId': definition['definitionId'],
                                          'deploymentId': definition['deploymentId'], 'version': version}
    result['readOnlyReview'] = True
    result['writeEligible'] = False
    return result
