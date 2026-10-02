"""Task C：40 份惰性创建意向与外部明确授权的逐步协调器。

无默认传输、凭证、SQL、审批、硬件事实或持久化。
准备 JSON 只是一份请求计划，绝不是已创建业务记录的 Manifest。
"""
from abc import ABC, abstractmethod
import argparse
from copy import deepcopy
from datetime import date, datetime, timedelta
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import threading
from uuid import UUID, uuid4, uuid5

import formal_manufacturing_fixture as fixture
from erp_flow_runner import (ApiClient, ApiError, Manifest, MANIFEST_DIR, PreflightError,
                             _api_id, _rows, read_only_preflight)
from inventory_local3306 import validate_target
from purchase_flow_runner import GateClosed, api_data, positive_number

LOCAL_DIR = MANIFEST_DIR
COUNT = 40
ROLES = ('so', 'plan', 'mrp', 'work')
# 实际控制器、表及 VO/SQL 字段，不另造端点或表结构。
SOURCES = {
    'so': ('so_sales_order_header', '/erp/so/orderheader', 'soId', 'soNo'),
    'plan': ('prd_production_plan', '/erp/prd/plan', 'planId', 'planNo'),
    'mrp': ('prd_material_requirement', '/erp/prd/requirement', 'requirementId', 'requirementNo'),
    'work': ('prd_work_order', '/erp/prd/work', 'woId', 'woNo'),
}
LIVE_GATES = ('target_tenant_preflight', 'external_write_authority',
              'independent_created_readbacks', 'inventory_contract')
SPEC_FIELDS = {'batchId', 'tenantId', 'asOfDate', 'fixtureInputs', 'masterReadbacks',
               'masterBaseline', 'preflight', 'quantity', 'cases'}
CASE_FIELDS = {'orderDate', 'startDate', 'endDate', 'requestedDate', 'priceQuote', 'planName'}

# 与 ry-erp.sql 的真实原生列对应；VO 关联显示名不能充当物料表证据。
MASTER_NATIVE_COLUMNS = {
    'bd_material': frozenset(('material_id tenant_id material_code material_name material_spec material_desc category_id '
        'base_uom_id purchase_uom_id inventory_uom_id sale_uom_id material_type source_type is_batch_manage is_serial_manage '
        'safety_stock min_stock max_stock standard_cost reference_price status quality_status shelf_life weight volume '
        'remark attr1 attr2 attr3 attr4 create_dept create_by create_time update_by update_time del_flag is_phantom '
        'inspection_required').split()),
    'bd_bom': frozenset(('bom_id tenant_id parent_material_id bom_version bom_name source_type bom_type routing_id '
        'effective_date expiry_date is_current status remark attr1 attr2 attr3 attr4 create_dept create_by create_time '
        'update_by update_time del_flag').split()),
    'bd_routing': frozenset(('routing_id tenant_id routing_code routing_name routing_desc material_id material_code '
        'material_name routing_version is_current total_steps total_hours effective_date expiry_date status remark '
        'attr1 attr2 attr3 attr4 create_dept create_by create_time update_by update_time del_flag').split()),
    'bd_client': frozenset(('client_id tenant_id client_code client_name client_nick client_en client_desc client_type '
        'client_logo address web_site email tel contact1 contact1_tel contact1_emal contact2 contact2_tel contact2_emal '
        'credit_code tax_id invoice_title invoice_bank_name invoice_bank_account invoice_address_phone default_invoice_type '
        'remark attr1 attr2 attr3 attr4 province_area_id city_area_id district_area_id street_id address_lat address_lng '
        'create_dept create_by create_time update_by update_time del_flag sales_rep_id sales_rep_name dept_id dept_name').split()),
    'bd_unit_of_measure': frozenset(('uom_id tenant_id uom_code uom_name uom_symbol category is_base primary_id primary_name '
        'conversion_rate status sort_order remark create_dept create_by create_time update_by update_time del_flag '
        'quantity_scale conversion_version conversion_verified_at').split()),
    'bd_material_category': frozenset(('category_id tenant_id parent_id category_code category_name category_desc level_num '
        'path_ids path_names sort_order status remark attr1 attr2 attr3 attr4 create_dept create_by create_time update_by '
        'update_time del_flag').split()),
    'bd_process': frozenset(('process_id tenant_id process_code process_name process_desc process_category workstation_id '
        'workstation_code workstation_name standard_hours setup_hours labor_count machine_count is_key_process is_outsource '
        'quality_required status remark attr1 attr2 attr3 attr4 create_dept create_by create_time update_by update_time del_flag').split()),
    'bd_workstation': frozenset(('workstation_id tenant_id workstation_code workstation_name workshop_id workshop_code '
        'workshop_name workstation_desc equipment_id operator_count standard_capacity status remark attr1 attr2 attr3 attr4 '
        'create_dept create_by create_time update_by update_time del_flag').split()),
    'bd_workshop': frozenset(('workshop_id tenant_id workshop_code workshop_name workshop_desc manager_id manager_name dept_id '
        'dept_name capacity status sort_order remark attr1 attr2 attr3 attr4 create_dept create_by create_time update_by '
        'update_time del_flag').split()),
    'bd_routing_step': frozenset(('step_id tenant_id routing_id sequence_num process_id process_code process_name step_name '
        'workstation_id workstation_code workstation_name standard_hours setup_hours labor_count machine_count labor_rate '
        'machine_rate is_key_process firmware_id firmware_code firmware_name is_outsource quality_required wait_time '
        'transfer_time description remark create_dept create_by create_time update_by update_time').split()),
    'bd_bom_detail': frozenset(('detail_id tenant_id bom_id child_material_id quantity_per_parent source_type loss_rate '
        'operation_step lead_time effective_date expiry_date sort_order status remark attr1 attr2 attr3 attr4 create_dept '
        'create_by create_time update_by update_time del_flag supply_type').split()),
}



class BatchContractError(ValueError):
    """事实缺失或不一致；异常消息绝不回显输入原始投影。"""


def require(condition, label):
    if not condition:
        raise BatchContractError(label)


def _json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False)


def _snake(field):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', field).lower()


def _equal(field, left, right):
    require(left is not None and right is not None, 'missing source field')
    if field.endswith('Id'):
        return _api_id(left, 'source') == _api_id(right, 'source')
    if field.endswith('Date') or field in ('createTime', 'updateTime'):
        return fixture._boundary_day(left, 'source date') == fixture._boundary_day(right, 'source date')
    if field in ('orderedQty', 'plannedQty', 'totalQuantity', 'requiredQuantity', 'totalRequiredQuantity',
                 'unitPrice', 'quantityPerParent', 'lossRate', 'stockQuantity', 'reservedQuantity',
                 'minOrderQty', 'safetyStock', 'standardHours', 'setupHours', 'conversionRate'):
        return fixture._decimal(left, 'source quantity') == fixture._decimal(right, 'source quantity')
    if field == 'status' and isinstance(right, str) and not right.isdecimal():
        return left == right
    if field in ('status', 'isCurrent', 'isPhantom', 'materialType', 'operationStep', 'sortOrder',
                 'frozenFlag', 'leadTime'):
        return fixture._integer(left, 'source enum') == fixture._integer(right, 'source enum')
    return left == right


def _match(row, expected):
    require(isinstance(row, dict), 'independent row missing')
    for field, value in expected.items():
        require(_equal(field, row.get(field), value), 'source contract mismatch')


def _master_contract(spec, *, master=None, baseline=None):
    inputs = spec['fixtureInputs']
    contract = fixture.build_fixture(**inputs)
    require(contract['masterDataReady'], 'workstation readbacks required')
    require(inputs['as_of_date'] == spec['asOfDate'], 'fixture read date mismatch')
    rows = master if master is not None else spec['masterReadbacks']
    before = baseline if baseline is not None else spec['masterBaseline']
    for request in contract['requests']:
        resolved = fixture.resolve_new_references(request, created_records=rows, preexisting_ids=before)
        fixture.assert_request_ready(resolved)
        if request['key'] in rows:
            expected = {k: v for k, v in resolved['body'].items()
                        if k not in ('materialCode', 'routingCode', 'steps')}
            _match(rows[request['key']], expected)
    customer = rows['customer']
    fixture._id(customer.get('clientId'), 'customer')
    fixture._normal_name(customer.get('clientName'), 'customer name')
    fixture._normal_name(customer.get('clientCode'), 'customer code')
    return contract


def _quantity(spec):
    contract = spec['quantity']
    finished = spec['masterReadbacks']['material.finished']
    if contract.get('kind') == 'SOFTWARE_PLANNED':
        require(set(contract) == {'kind', 'value', 'uomId', 'lotMultiple'}, 'explicit software lot contract required')
        count = fixture._integer(contract['value'], 'planned quantity', minimum=1)
        multiple = fixture._integer(contract['lotMultiple'], 'software lot multiple', minimum=1)
        unit = fixture._id(contract['uomId'], 'planned UOM')
        require(unit == fixture._id(finished['baseUomId'], 'finished UOM') and count % multiple == 0,
                'software lot/UOM mismatch')
        return str(count), {'kind': 'SOFTWARE_PLANNED', 'value': str(count), 'uomId': unit,
                            'lotMultiple': multiple, 'physicalObservation': False}
    # 旧 minOrderQty 缺规范单位/条款，注记不能用于本批次定量。
    raise BatchContractError('explicit SOFTWARE_PLANNED quantity required; legacy MOQ is not authority')


def _dates(case, as_of):
    parsed = {k: fixture._day(case.get(k), 'planned date')
              for k in ('orderDate', 'startDate', 'endDate', 'requestedDate')}
    require(fixture._day(as_of, 'as of') <= parsed['orderDate'] <= parsed['startDate'] <=
            parsed['endDate'] <= parsed['requestedDate'], 'planned dates out of order')


def _quote(spec, case, quantity):
    quote = case['priceQuote']
    require(set(quote) == {'path', 'value', 'readDate', 'clientId', 'materialId', 'qty', 'orderDate'},
            'explicit sales price readback required')
    require(quote['path'] == '/erp/so/orderheader/resolvePrice' and quote['readDate'] == spec['asOfDate'],
            'sales price source/date mismatch')
    require(quote['clientId'] == spec['masterReadbacks']['customer']['clientId'] and
            quote['materialId'] == spec['masterReadbacks']['material.finished']['materialId'] and
            quote['orderDate'] == case['orderDate'] and
            fixture._decimal(quote['qty'], 'price quantity') == fixture._decimal(quantity, 'planned quantity'),
            'price scope mismatch')
    value = fixture._decimal(quote['value'], 'price readback')
    # resolvePrice 的零可能是缺少报价的兜底；本工具没有零报价证据签发方。
    require(value > 0, 'positive applicable sales price evidence required')
    return str(value)


def _requests(spec, case, intent, quantity, price):
    master = spec['masterReadbacks']
    finished, customer = master['material.finished'], master['customer']
    remark = '业务创建意向 ' + intent  # BO remark 是意向元数据，不是服务器 ID 或业务编号。
    line = {'materialId': finished['materialId'], 'materialName': finished['materialName'],
            'orderedQty': quantity, 'unit': '套', 'unitPrice': price, 'requestedDate': case['requestedDate']}
    return [
        {'method': 'POST', 'path': '/erp/so/orderheader', 'body': {
            'soNo': '', 'clientId': customer['clientId'], 'clientName': customer['clientName'],
            'orderDate': case['orderDate'], 'requestedDate': case['requestedDate'], 'remark': remark,
            'soSalesOrderLineList': [line]}},
        {'method': 'POST', 'path': '/erp/prd/plan', 'body': {
            'planNo': '', 'planName': fixture._normal_name(case.get('planName', fixture.FINISHED_NAME + '生产安排'), 'plan name'),
            'planDate': case['orderDate'], 'startDate': case['startDate'], 'endDate': case['endDate'],
            'status': 'DRAFT', 'totalQuantity': quantity, 'bomId': master['bom']['bomId'],
            'bomVersion': master['bom']['bomVersion'], 'orderLineId': '$case:so.lineId',
            'referenceNo': '$case:so.soNo', 'remark': remark}},
        {'method': 'POST', 'path': '/erp/prd/plan/generateMaterialRequirementsWithDetails/$case:plan.planId', 'body': None},
        {'method': 'POST', 'path': '/erp/prd/plan/generateWorkOrders/$case:plan.planId', 'body': ['$case:mrp.requirementId']},
    ]


def prepare_batch(spec):
    """仅离线准备；即使输入完整也不授予写权限、不证明业务创建。"""
    require(isinstance(spec, dict) and set(spec) <= SPEC_FIELDS, 'unsupported batch input fields')
    spec = deepcopy(spec)
    try:
        namespace = UUID(spec.get('batchId', str(uuid4())))
    except (ValueError, TypeError, AttributeError):
        raise BatchContractError('batch identity must be a UUID') from None
    entries = spec.get('cases', [{} for _ in range(COUNT)])
    require(isinstance(entries, list) and len(entries) == COUNT and
            all(isinstance(c, dict) and set(c) <= CASE_FIELDS for c in entries), 'exactly 40 independent case inputs required')
    blockers, master_fixture, quantity, quantity_source = [], None, None, None
    try:
        require(isinstance(spec['tenantId'], str) and re.fullmatch(r'[0-9]{6,20}', spec['tenantId']), 'tenant required')
        fixture._day(spec['asOfDate'], 'as of')
        master_fixture = _master_contract(spec)
    except (KeyError, TypeError, ValueError, PreflightError):
        blockers.append('fresh_master_data')
    try:
        quantity, quantity_source = _quantity(spec)
    except (KeyError, TypeError, ValueError, PreflightError):
        blockers.append('quantity_source')
    cases = []
    for ordinal, entry in enumerate(entries, 1):
        missing = list(blockers)
        intent = str(uuid5(namespace, 'business-intent-' + str(ordinal)))
        requests = [None] * len(ROLES)
        try:
            _dates(entry, spec['asOfDate'])
        except (KeyError, TypeError, ValueError):
            missing.append('planned_dates')
        try:
            price = _quote(spec, entry, quantity)
        except (KeyError, TypeError, ValueError):
            missing.append('sales_price_readback')
        if not missing:
            requests = _requests(spec, entry, intent, quantity, price)
        cases.append({'ordinal': ordinal, 'intentId': intent, 'status': 'BLOCKED', 'readyForExecution': False,
                      'plannedQuantity': quantity, 'inputBlockers': missing, 'blockers': missing + list(LIVE_GATES),
                      'steps': [{'role': role, 'request': request, 'status': 'BLOCKED'}
                                for role, request in zip(ROLES, requests)]})
    return {'version': 1, 'batchId': str(namespace), 'tenantId': spec.get('tenantId'), 'status': 'BLOCKED',
            'cases': cases, 'quantitySource': quantity_source, 'masterFixture': master_fixture,
            'sourceCompleteCases': 0, 'physicalAcceptance': False, 'slaAccepted': False, 'procurementReady': False}


class ExternalWriteAuthority(ABC):
    """外部权威必须将实际客户端/主体绑定到环境与租户，
    逐意向批准原请求的确切摘要，拒绝自动 POST 重试、远端/生产环境和过期授权。

    本模块不签发权限。授权成功须返回 None，否则抛异常；
    调用方的 verified/apply 布尔值不是授权凭证。
    """
    @abstractmethod
    def require_authorized(self, *, business_client, target, tenant_id, intent_id, request_sha256, retry):
        raise PreflightError('external write authorization required')

    def require_retry_safe(self, **scope):
        """四种写请求的重试均须独立证明首个请求已结束且未提交/不会再提交。"""
        raise PreflightError('ambiguous non-idempotent create cannot be retried')


class ScopedBusinessReads(ABC):
    """集成用的独立只读能力，不创建 SQL 或数据库连接。

    inventory()：新获取的 inventory_local3306 报告。
    master_baseline()：主数据创建前的完整原始 ID 审计。
    rows()：按租户限定的完整原生 snake_case 表列，含 tenant_id/create_time，
    不得由 API 投影复制而来。读适配器负责快照一致性及完整性。
    """
    @abstractmethod
    def inventory(self):
        raise NotImplementedError

    @abstractmethod
    def master_baseline(self, *, tenant_id):
        raise NotImplementedError

    @abstractmethod
    def rows(self, table, *, tenant_id):
        raise NotImplementedError


def _business_shape(request, intent_id):
    """仅允许固定 CREATE/生成器请求子集，不允许审批或编号覆盖。"""
    require(isinstance(request, dict) and set(request) == {'method', 'path', 'body'}, 'unexpected request fields')
    body, path = request['body'], request['path']
    if path == '/erp/so/orderheader':
        require(isinstance(body, dict) and set(body) == {'soNo', 'clientId', 'clientName', 'orderDate',
                'requestedDate', 'remark', 'soSalesOrderLineList'} and body['soNo'] == '', 'SO server numbering required')
        lines = body['soSalesOrderLineList']
        require(isinstance(lines, list) and len(lines) == 1 and isinstance(lines[0], dict) and
                set(lines[0]) == {'materialId', 'materialName', 'orderedQty', 'unit', 'unitPrice', 'requestedDate'},
                'bounded SO line payload required')
        fixture._integer(lines[0]['orderedQty'], 'planned quantity', minimum=1)
        fixture._id(lines[0]['materialId'], 'material')
        require(lines[0]['unit'] == '套' and fixture._decimal(lines[0]['unitPrice'], 'read price') > 0,
                'verified kit unit/price required')
    elif path == '/erp/prd/plan':
        require(isinstance(body, dict) and set(body) == {'planNo', 'planName', 'planDate', 'startDate', 'endDate',
                'status', 'totalQuantity', 'bomId', 'bomVersion', 'orderLineId', 'referenceNo', 'remark'} and
                body['planNo'] == '' and body['status'] == 'DRAFT', 'plan server numbering/draft payload required')
        fixture._integer(body['totalQuantity'], 'planned quantity', minimum=1)
        fixture._id(body['orderLineId'], 'source line')
        fixture._id(body['bomId'], 'BOM')
        fixture._normal_name(body['planName'], 'plan name')
    elif '/generateWorkOrders/' in path:
        require(isinstance(body, list) and len(body) == 1, 'exactly one independent MRP source required')
        fixture._id(body[0], 'MRP source')
    else:
        require(body is None, 'MRP generator takes no invented body')
    if isinstance(body, dict):
        require(body['remark'] == '业务创建意向 ' + intent_id, 'original intent identity mismatch')


class BusinessWriteCapability:
    """已有独立写授权的业务客户端 post 方法的窄范围门面。

    不创建 HTTP 客户端、不绕过原有鉴权；拒绝 ApiClient 及其子类、布尔开关
    和任意 send 回调。原业务客户端必须保留自身授权守卫。
    """
    def __init__(self, business_client, *, authority):
        require(not isinstance(business_client, ApiClient) and callable(getattr(business_client, 'post', None)),
                'independent authorized business client required')
        require(isinstance(authority, ExternalWriteAuthority), 'external authority type required')
        self._client, self._authority = business_client, authority

    def authorize(self, request, *, tenant_id, intent_id, target, retry=False):
        require(request['method'] == 'POST' and (request['path'] in ('/erp/so/orderheader', '/erp/prd/plan') or
                re.fullmatch(r'/erp/prd/plan/(generateMaterialRequirementsWithDetails|generateWorkOrders)/[1-9][0-9]*',
                             request['path'])), 'unsupported business operation')
        require(target == 'localhost TCP 3306 / dev', 'write target mismatch')
        require(type(retry) is bool, 'retry marker must not grant permission')
        _business_shape(request, intent_id)
        scope = {'business_client': self._client, 'tenant_id': tenant_id, 'intent_id': intent_id, 'target': target,
                 'request_sha256': hashlib.sha256(_json(request).encode('utf-8')).hexdigest(), 'retry': retry}
        require(self._authority.require_authorized(**scope) is None, 'authority must enforce, not return a flag')
        # 四种服务端写入口都可能有尚未提交的首个请求；查无记录不是重试证明。
        if retry:
            require(self._authority.require_retry_safe(**scope) is None, 'noncommit proof required')

    def send(self, request, *, tenant_id, intent_id, target, retry=False, on_dispatch=None):
        """回调只记录派发边界，不授予权限；授权拒绝发生在回调和真实 POST 之前。"""
        require(on_dispatch is None or callable(on_dispatch), 'dispatch observer must be callable')
        self.authorize(request, tenant_id=tenant_id, intent_id=intent_id, target=target, retry=retry)
        path, payload = request['path'], deepcopy(request['body'])
        if on_dispatch is not None:
            on_dispatch()
        return self._client.post(path, payload=payload)


class BatchCoordinator:
    """每次仅推进一个案例，至多一个 POST；没有全批执行或 apply 开关。

    State 和 Manifest 只在内存中。原请求/基线丢失即停止；新协调器不收养
    既有来源。这是单协调器集成边界，不是分布式事务平台。
    """
    def __init__(self, spec, *, manifest, reader=None, scoped_reads=None, writer=None):
        self._spec = deepcopy(spec)
        self._plan = prepare_batch(spec)
        require(isinstance(manifest, Manifest) and manifest.tenant_id == self._plan['tenantId'],
                'manifest tenant mismatch')
        require(writer is None or isinstance(writer, BusinessWriteCapability), 'write capability type required')
        require(scoped_reads is None or isinstance(scoped_reads, ScopedBusinessReads), 'independent read port required')
        self._manifest, self._reader, self._reads, self._writer = manifest, reader, scoped_reads, writer
        self._states = [{'next': 0, 'baselines': None, 'cutoff': None, 'sources': {},
                         'requests': {}, 'attempted': set(), 'accepted': None} for _ in range(COUNT)]
        self._lock = threading.RLock()

    def summary(self):
        with self._lock:
            count = sum(state['accepted'] is not None for state in self._states)
            return {'status': 'SOURCE_COMPLETE' if count == COUNT else 'BLOCKED',
                    'plannedCases': COUNT, 'readyCases': 0, 'sourceCompleteCases': count,
                    'blockedCases': COUNT - count, 'physicalAcceptance': False, 'slaAccepted': False, 'procurementReady': False}

    def _db(self, table):
        rows = self._reads.rows(table, tenant_id=self._manifest.tenant_id)
        require(isinstance(rows, list) and all(isinstance(r, dict) and r.get('tenant_id') == self._manifest.tenant_id
                and r.get('del_flag', '0') in (0, '0') for r in rows), 'complete tenant-scoped read required')
        return rows

    def _db_one(self, table, id_field, entity_id):
        matches = [r for r in self._db(table) if fixture._id(r.get(_snake(id_field)), 'scoped ID') == entity_id]
        require(len(matches) == 1, 'independent source absent or ambiguous')
        return matches[0]

    def _data(self, path):
        row = api_data(self._reader, path)  # 复用实际采购 Runner 的严格详情契约。
        require('tenantId' not in row or row['tenantId'] == self._manifest.tenant_id, 'API tenant mismatch')
        return row

    def _proof(self, table, id_field, row, fields):
        entity_id = _api_id(row.get(id_field), 'readback')
        db = self._db_one(table, id_field, entity_id)
        for field in set(fields) | {id_field}:
            if table in MASTER_NATIVE_COLUMNS:
                require(_snake(field) in MASTER_NATIVE_COLUMNS[table], 'derived VO field is not a native table proof')
            require(_equal(field, row.get(field), db.get(_snake(field))), 'independent read disagreement')
        return db

    def _lists(self, role):
        table, base, id_field, _ = SOURCES[role]
        rows = _rows(self._reader, base + '/list')
        require(all(isinstance(r, dict) and ('tenantId' not in r or r['tenantId'] == self._manifest.tenant_id)
                    for r in rows), 'API list tenant mismatch')
        ids = [_api_id(r.get(id_field), 'API list') for r in rows]
        db_ids = [fixture._id(r.get(_snake(id_field)), 'scoped ID') for r in self._db(table)]
        require(len(ids) == len(set(ids)) and len(db_ids) == len(set(db_ids)) and set(ids) == set(db_ids),
                'incomplete or duplicate source baseline')
        return rows

    def _preflight(self):
        inventory = self._reads.inventory()
        require(isinstance(inventory, dict) and inventory.get('target') == 'localhost TCP 3306 / dev',
                'actual target must be dev localhost TCP 3306')
        validate_target('localhost', 3306, 'dev', 'ry-erp')
        # 此确认仅允许只读审查，绝不授予业务写权限。
        review = read_only_preflight(self._reader, inventory, tenant_id=self._manifest.tenant_id,
                                    human_ack=True, **self._spec['preflight'])
        require(review['writeEligible'] is False and review['readOnlyReview'] is True, 'unexpected preflight contract')
        require(inventory['snapshot']['db_date'] == self._spec['asOfDate'] == date.today().isoformat(),
                'batch facts must be freshly read')
        return inventory

    def _live_master(self):
        original = self._spec['masterReadbacks']
        current = {}
        for key, expected in original.items():
            if key.startswith('material.'):
                table, base, id_field = 'bd_material', '/erp/bd/material', 'materialId'
            elif key in ('routing', 'bom'):
                table, base, id_field = 'bd_' + key, '/erp/bd/' + key, key + 'Id'
            elif key == 'customer':
                table, base, id_field = 'bd_client', '/erp/bd/client', 'clientId'
            else:
                raise BatchContractError('unknown master role')
            row = self._data(base + '/' + _api_id(expected[id_field], 'master'))
            # 原生字段按真实表列核对；派生单位/分类名随后由独立关联行认证。
            fields = [k for k, v in expected.items() if not isinstance(v, (dict, list)) and v is not None]
            native = [k for k in fields if _snake(k) in MASTER_NATIVE_COLUMNS[table]]
            self._proof(table, id_field, row, native)
            _match(row, {k: expected[k] for k in fields})
            current[key] = row
        baseline = self._reads.master_baseline(tenant_id=self._manifest.tenant_id)
        require(isinstance(baseline, dict) and all(set(baseline[k]) == set(self._spec['masterBaseline'][k])
                for k in ('material', 'routing', 'bom')), 'original pre-master baseline required')
        _master_contract(self._spec, master=current, baseline=baseline)
        catalog = self._reader.get('/erp/bd/measure/all')
        require(isinstance(catalog, dict) and catalog.get('code', 200) == 200, 'live UOM catalog required')
        fixture._verify_units(self._spec['fixtureInputs']['units'], catalog.get('data'))
        self._material_dimensions(current, catalog['data'])
        self._manufacturing_contract(current)
        return current



    def _material_dimensions(self, master, catalog):
        """物料显示名须经单位、类别真实原生行及租户绑定独立核验。"""
        units = {}
        for role in ('finished', 'piece', 'box'):
            expected = self._spec['fixtureInputs']['units'][role]
            unit_id = fixture._id(expected['uomId'], 'selected UOM')
            matches = [r for r in catalog if _api_id(r.get('uomId'), 'UOM') == unit_id]
            require(len(matches) == 1, 'independent UOM catalog identity ambiguous')
            row = matches[0]
            fields = [k for k, v in expected.items() if v is not None and
                      _snake(k) in MASTER_NATIVE_COLUMNS['bd_unit_of_measure']]
            db = self._proof('bd_unit_of_measure', 'uomId', row, fields)
            # 基本单位的 primary_id=NULL 是明确元数据，不是数量未知后补零。
            require('primary_id' in db and db['primary_id'] == row.get('primaryId'), 'independent UOM primary mismatch')
            units[unit_id] = row
        # 派生套件单位可能指向未被 piece/box 选中的额外基本单位，仍须独立认证。
        primary_ids = {fixture._id(row['primaryId'], 'primary UOM') for row in units.values()
                       if row.get('primaryId') is not None}
        for primary_id in primary_ids - units.keys():
            matches = [r for r in catalog if _api_id(r.get('uomId'), 'primary UOM') == primary_id]
            require(len(matches) == 1, 'independent primary UOM ambiguous')
            primary = matches[0]
            self._proof('bd_unit_of_measure', 'uomId', primary, ('uomName', 'status', 'isBase', 'category'))
            db = self._db_one('bd_unit_of_measure', 'uomId', primary_id)
            require('primary_id' in db and db['primary_id'] is None and primary.get('primaryId') is None,
                    'primary UOM must be a native base')
            require(fixture._integer(primary.get('status'), 'primary status') == 1 and
                    fixture._integer(primary.get('isBase'), 'primary base') == 1, 'inactive primary UOM')
        for key, material in master.items():
            if not key.startswith('material.'):
                continue
            for field in ('baseUomId', 'purchaseUomId', 'inventoryUomId', 'saleUomId'):
                require(fixture._id(material[field], 'material UOM') in units, 'material unit lacks independent proof')
            base = units[fixture._id(material['baseUomId'], 'base UOM')]
            require(material.get('baseUomName') == base['uomName'], 'derived material UOM name mismatch')
            category_id = fixture._id(material['categoryId'], 'material category')
            category = self._data('/erp/bd/category/' + category_id)
            self._proof('bd_material_category', 'categoryId', category, ('categoryName', 'status'))
            require(_api_id(category.get('categoryId'), 'category') == category_id and
                    fixture._integer(category.get('status'), 'category status') == 1, 'inactive or mismatched category')
            if 'categoryName' in material:
                require(material['categoryName'] == category['categoryName'], 'derived material category name mismatch')

    def _manufacturing_contract(self, master):
        """核验新鲜的选定工序/工位与 1:1 BOM 输入，不认证实物产能。"""
        for group, table, base, field in (
            ('categories', 'bd_material_category', '/erp/bd/category', 'categoryId'),
            ('processes', 'bd_process', '/erp/bd/process', 'processId'),
            ('workstations', 'bd_workstation', '/erp/bd/workstation', 'workstationId'),
            ('workshops', 'bd_workshop', '/erp/bd/workshop', 'workshopId')):
            for expected in self._spec['fixtureInputs'][group].values():
                row = self._data(base + '/' + fixture._id(expected[field], 'selected master'))
                fields = [k for k, v in expected.items() if v is not None]
                _match(row, {k: expected[k] for k in fields})
                self._proof(table, field, row, fields)
        routing_id = master['routing']['routingId']
        response = self._reader.get('/erp/bd/routing/steps/' + routing_id)
        require(isinstance(response, dict) and response.get('code', 200) == 200 and
                isinstance(response.get('data'), list) and len(response['data']) == 3, 'live routing steps required')
        live_steps = sorted(response['data'], key=lambda r: fixture._integer(r.get('sequenceNum'), 'sequence'))
        template = self._plan['masterFixture']['requests'][5]
        resolved = fixture.resolve_new_references(template, created_records=master,
                                                   preexisting_ids=self._spec['masterBaseline'])
        seen = set()
        for expected, row in zip(resolved['body']['steps'], live_steps):
            _match(row, expected)
            require(_api_id(row.get('routingId'), 'step routing') == routing_id, 'routing step association mismatch')
            step_id = _api_id(row.get('stepId'), 'step')
            require(step_id not in seen, 'duplicate routing step')
            seen.add(step_id)
            self._proof('bd_routing_step', 'stepId', row, set(expected) | {'routingId'})
        rows = _rows(self._reader, '/erp/bd/bom/detail/list', {'bomId': master['bom']['bomId']})
        require(len(rows) == 4, 'four independent BOM components required')
        seen = set()
        for template in self._plan['masterFixture']['requests'][7:]:
            expected = fixture.resolve_new_references(template, created_records=master,
                                                       preexisting_ids=self._spec['masterBaseline'])['body']
            matches = [r for r in rows if _api_id(r.get('childMaterialId'), 'BOM child') == expected['childMaterialId']]
            require(len(matches) == 1, 'BOM component ambiguous')
            row = matches[0]
            _match(row, expected)
            fixture._integer(row.get('leadTime'), 'BOM lead time')  # 缺失不能默认补零。
            detail_id = _api_id(row.get('detailId'), 'BOM detail')
            require(detail_id not in seen, 'duplicate BOM detail')
            seen.add(detail_id)
            self._proof('bd_bom_detail', 'detailId', row, set(expected) | {'leadTime'})

    def _price(self, entry, quantity):
        quote = entry['priceQuote']
        query = {k: quote[k] for k in ('clientId', 'materialId', 'qty', 'orderDate')}
        response = self._reader.get('/erp/so/orderheader/resolvePrice', query)
        require(isinstance(response, dict) and response.get('code', 200) == 200, 'live price read failed')
        require(fixture._decimal(response.get('data'), 'live price') > 0, 'unproven zero live quote')
        require(fixture._decimal(response.get('data'), 'live price') == fixture._decimal(quote['value'], 'quote'),
                'sales price changed')
        _quote(self._spec, entry, quantity)

    def _stock(self, master):
        rows = _rows(self._reader, '/erp/wm/stock/list')
        db_rows = self._db('wm_material_stock')
        for component in ('bolt', 'nut', 'washer', 'box'):
            material = master['material.' + component]
            material_id = _api_id(material['materialId'], 'component')
            api = [r for r in rows if _api_id(r.get('materialId'), 'stock material') == material_id]
            db = [r for r in db_rows if fixture._id(r.get('material_id'), 'stock material') == material_id]
            # 空库存证据为未知，绝不是已核验的零。
            require(bool(api) and bool(db), 'unknown stock is not zero')
            api_ids = [_api_id(r.get('stockId'), 'stock') for r in api]
            db_ids = [fixture._id(r.get('stock_id'), 'stock') for r in db]
            require(len(api_ids) == len(set(api_ids)) and len(db_ids) == len(set(db_ids)) and set(api_ids) == set(db_ids),
                    'stock coverage incomplete')
            # 安全库存是强制库存门禁事实，不依赖原输入是否选择了可选 VO 字段。
            self._proof('bd_material', 'materialId', material, ('safetyStock',))
            require(fixture._decimal(material.get('safetyStock'), 'safety stock') >= 0, 'unknown safety stock')
            for row in api:
                record = self._proof('wm_material_stock', 'stockId', row,
                                     ('materialId', 'stockQuantity', 'reservedQuantity', 'frozenFlag', 'baseUomName'))
                require(fixture._id(record.get('base_uom_id'), 'stock UOM') == fixture._id(material['baseUomId'], 'material UOM')
                        and row.get('baseUomName') == material['baseUomName'], 'stock unit mismatch')
                for field in ('stockQuantity', 'reservedQuantity'):
                    value = fixture._decimal(row.get(field), 'stock quantity')
                    positive_number(value, 'stock quantity', allow_zero=True)
                require(fixture._integer(row.get('frozenFlag'), 'freeze state') in (0, 1), 'unknown stock freeze state')
        # 不构造可用量/缺料 KPI、采购数量或实物事实。

    def _baseline(self, state, inventory):
        baselines = {}
        for role, (_, _, id_field, _) in SOURCES.items():
            baselines[role] = {_api_id(r[id_field], 'baseline') for r in self._lists(role)}
        for table, key in (('so_sales_order_line', 'lineId'), ('prd_material_requirement_detail', 'detailId')):
            rows = self._db(table)
            ids = [fixture._id(r.get(_snake(key)), 'baseline') for r in rows]
            require(len(ids) == len(set(ids)), 'duplicate detail baseline')
            baselines[table] = set(ids)
        cutoff = datetime.fromisoformat(inventory['snapshot']['db_timestamp'])
        state.update(baselines=baselines, cutoff=cutoff)

    def _candidate(self, role, state, intent):
        rows = self._lists(role)
        if role == 'so':
            matches = [r for r in rows if r.get('remark') == '业务创建意向 ' + intent]
        elif role == 'plan':
            # 真实计划 VO 没有 remark；从独立原表找到意向，再与 API 的公开 ID 对照。
            native_ids = {fixture._id(r.get('plan_id'), 'plan intent') for r in self._db('prd_production_plan')
                          if r.get('remark') == '业务创建意向 ' + intent}
            matches = [r for r in rows if _api_id(r.get('planId'), 'plan') in native_ids]
        elif role == 'mrp':
            plan_id = state['sources']['plan']['row']['planId']
            # 只排除独立原表也确认无计划的手工 MRP，不能由 API 的 NULL 隐藏真实关联。
            native = {fixture._id(r.get('requirement_id'), 'MRP identity'): r
                      for r in self._db('prd_material_requirement')}
            matches = []
            for row in rows:
                db = native[_api_id(row.get('requirementId'), 'MRP identity')]
                require('plan_id' in db, 'native MRP association missing')
                if row.get('planId') is None:
                    require(db['plan_id'] is None, 'unknown API MRP association conflicts with native row')
                    continue
                require(_equal('planId', row['planId'], db['plan_id']), 'MRP plan association disagreement')
                if _api_id(row['planId'], 'MRP plan') == plan_id:
                    matches.append(row)
        else:
            requirement_id = state['sources']['mrp']['row']['requirementId']
            matches = [r for r in rows if r.get('sourceType') == 'MATERIAL_REQUIREMENT' and
                       _api_id(r.get('sourceId'), 'work source') == requirement_id]
        require(len(matches) <= 1, 'ambiguous business source')
        return matches[0] if matches else None

    def _resolve(self, template, sources):
        substitutions = {}
        for role, source in sources.items():
            for field, value in source['row'].items():
                if field.endswith('Id') or field.endswith('No'):
                    substitutions['$case:' + role + '.' + field] = value
            if role == 'so':
                substitutions['$case:so.lineId'] = source['line']['lineId']

        def resolve(value):
            if isinstance(value, dict):
                return {k: resolve(v) for k, v in value.items()}
            if isinstance(value, list):
                return [resolve(v) for v in value]
            if isinstance(value, str):
                for token, result in substitutions.items():
                    value = value.replace(token, result)
                require('$case:' not in value, 'unresolved source reference')
            return value
        return resolve(deepcopy(template))

    def _source(self, role, row, state, case, master):
        table, base, id_field, code_field = SOURCES[role]
        entity_id = _api_id(row.get(id_field), 'server source')
        fixture._id(entity_id, 'server source')
        require(entity_id not in state['baselines'][role], 'source existed before this case')
        row = self._data(base + '/' + entity_id)
        require(_api_id(row.get(id_field), 'detail') == entity_id, 'detail/list source mismatch')
        code = row.get(code_field)
        fixture._normal_name(code, 'server business number')
        quantity = case['plannedQuantity']
        entry = self._spec['cases'][case['ordinal'] - 1]
        finished, customer = master['material.finished'], master['customer']
        parents, line = {}, None
        if role == 'so':
            expected = {'clientId': customer['clientId'], 'clientName': customer['clientName'],
                        'orderDate': entry['orderDate'], 'requestedDate': entry['requestedDate'],
                        'remark': '业务创建意向 ' + case['intentId'], 'orderStatus': 'DRAFT'}
            lines = row.get('soSalesOrderLineList')
            require(isinstance(lines, list) and len(lines) == 1, 'exactly one independent SO line required')
            line_id = _api_id(lines[0].get('lineId'), 'SO line')
            require(line_id not in state['baselines']['so_sales_order_line'], 'SO line pre-existed')
            line = self._data('/erp/so/orderheader/listLineById/' + line_id)
            line_expected = {'lineId': line_id, 'soId': entity_id, 'materialId': finished['materialId'],
                             'orderedQty': quantity, 'unit': '套', 'unitPrice': entry['priceQuote']['value'],
                             'requestedDate': entry['requestedDate']}
            _match(lines[0], line_expected)
            _match(line, line_expected)
            self._proof('so_sales_order_line', 'lineId', line, line_expected)
            parents = {'clientId': customer['clientId']}
        elif role == 'plan':
            so = state['sources']['so']
            expected = {'orderLineId': so['line']['lineId'], 'referenceNo': so['row']['soNo'],
                        'bomId': master['bom']['bomId'], 'bomVersion': master['bom']['bomVersion'],
                        'totalQuantity': quantity, 'planDate': entry['orderDate'], 'startDate': entry['startDate'],
                        'endDate': entry['endDate'], 'status': 'DRAFT'}
            parents = {'soId': so['row']['soId'], 'lineId': so['line']['lineId'], 'bomId': master['bom']['bomId']}
        elif role == 'mrp':
            plan = state['sources']['plan']['row']
            expected = {'planId': plan['planId'], 'planNo': plan['planNo'], 'bomId': master['bom']['bomId'],
                        'materialId': finished['materialId'], 'sourceType': 'PROCESS', 'status': 'DRAFT',
                        'requirementDate': self._spec['asOfDate'],
                        # 组件毛需求合计，不是成品数量、缺口或实物产量。
                        'totalRequiredQuantity': str(Decimal(quantity) * 4)}
            parents = {'planId': plan['planId'], 'bomId': master['bom']['bomId'], 'materialId': finished['materialId']}
            details = row.get('prdMaterialRequirementDetailList')
            require(isinstance(details, list) and len(details) == 4, 'MRP must contain four distinct component demands')
            components = {master['material.' + c]['materialId']: master['material.' + c]
                          for c in ('bolt', 'nut', 'washer', 'box')}
            require({_api_id(d.get('materialId'), 'MRP component') for d in details} == set(components),
                    'MRP component source mismatch')
            bom_rows = _rows(self._reader, '/erp/bd/bom/detail/list', {'bomId': master['bom']['bomId']})
            require(len(bom_rows) == 4, 'independent BOM detail contract missing')
            seen = set()
            for detail in details:
                detail_id = _api_id(detail.get('detailId'), 'MRP detail')
                require(detail_id not in seen and detail_id not in state['baselines']['prd_material_requirement_detail'],
                        'MRP detail source reused')
                seen.add(detail_id)
                material_id = _api_id(detail['materialId'], 'MRP component')
                matches = [d for d in bom_rows if _api_id(d.get('childMaterialId'), 'BOM child') == material_id]
                require(len(matches) == 1, 'BOM component ambiguous')
                bom = matches[0]
                self._proof('bd_bom_detail', 'detailId', bom,
                            ('bomId', 'childMaterialId', 'quantityPerParent', 'lossRate', 'leadTime'))
                require(fixture._decimal(bom.get('quantityPerParent'), 'BOM quantity') == 1 and
                        fixture._decimal(bom.get('lossRate'), 'BOM loss') == 0, 'BOM lot contract changed')
                lead_time = fixture._integer(bom.get('leadTime'), 'BOM lead time')
                required_date = (datetime.combine(fixture._boundary_day(plan['endDate'], 'plan end'), datetime.min.time()) -
                                 timedelta(hours=lead_time)).date().isoformat()
                facts = {'requirementId': entity_id, 'materialId': material_id,
                         'requiredQuantity': quantity, 'unit': fixture._id(components[material_id]['baseUomId'], 'MRP unit'),
                         'requiredDate': required_date}
                _match(detail, facts)
                self._proof('prd_material_requirement_detail', 'detailId', detail, facts)
        else:
            plan, mrp = state['sources']['plan']['row'], state['sources']['mrp']['row']
            expected = {'planId': plan['planId'], 'sourceId': mrp['requirementId'],
                        'sourceType': 'MATERIAL_REQUIREMENT', 'sourceNo': mrp['requirementNo'],
                        'bomId': master['bom']['bomId'], 'materialId': finished['materialId'],
                        'plannedQty': quantity, 'clientId': customer['clientId'], 'woStatus': 'CREATED',
                        # 实际生成器写服务端当前时间，不是计划开始日。
                        'planStartDate': self._spec['asOfDate']}
            parents = {'planId': plan['planId'], 'requirementId': mrp['requirementId'],
                       'bomId': master['bom']['bomId'], 'materialId': finished['materialId'],
                       'clientId': customer['clientId']}
        _match(row, expected)
        db = self._proof(table, id_field, row, set(expected) | {code_field})
        if role == 'plan':
            require(db.get('remark') == '业务创建意向 ' + case['intentId'], 'native plan intent mismatch')
        created = datetime.fromisoformat(db['create_time'])
        # 原生 create_time 为秒精度 DATETIME，NOW(6) 基线与其存储区间比较。
        # 同秒只能说明区间相容，仍必须通过此前原始 ID 全集排除既有来源。
        require(created.microsecond == 0 and created + timedelta(seconds=1) > state['cutoff'],
                'source creation predates case baseline storage interval')
        for other in self._states:
            if other is state or role not in other['sources']:
                continue
            previous = other['sources'][role]
            require(previous['row'][id_field] != entity_id and previous['row'][code_field] != code,
                    'business source reused across independent intents')
            if role == 'so':
                require(previous['line']['lineId'] != line['lineId'], 'SO line reused across cases')
        result = {'row': deepcopy(row), 'parents': parents}
        if line is not None:
            result['line'] = deepcopy(line)
        return result

    def _record(self, sources):
        # 冲突检查在内存原子完成，绝不重写历史 Manifest 证据。
        staged = Manifest(self._manifest.tenant_id)
        staged.records = deepcopy(self._manifest.records)
        for role, source in sources.items():
            table, _, id_field, code_field = SOURCES[role]
            staged.record_verified('erp', table, source['row'][id_field], source['row'][code_field],
                                   source['parents'], tenant_id=self._manifest.tenant_id)
        self._manifest.records[:] = staged.records

    def _reconstruct(self, state, case, master):
        for role in ROLES[:state['next']]:
            old = state['sources'][role]
            state['sources'][role] = self._source(role, old['row'], state, case, master)

    def _accept(self, state):
        require(set(state['sources']) == set(ROLES), 'full independent source tuple missing')
        source_tuple = (self._manifest.tenant_id,) + tuple(
            (role, state['sources'][role]['row'][SOURCES[role][2]],
             state['sources'][role]['row'][SOURCES[role][3]]) for role in ROLES) + (
             ('line', state['sources']['so']['line']['lineId']),)
        require(all(other is state or other['accepted'] != source_tuple for other in self._states),
                'duplicate complete source tuple')
        state['accepted'] = source_tuple

    def step(self, ordinal):
        """仅返回摘要；绝不回传凭证、原始 ID 或 API 错误响应。"""
        require(type(ordinal) is int and 1 <= ordinal <= COUNT, 'case ordinal must be 1..40')
        with self._lock:
            state, case = self._states[ordinal - 1], self._plan['cases'][ordinal - 1]
            if (self._writer is None or self._reader is None or self._reads is None or
                    'batchId' not in self._spec or case['inputBlockers']):
                return {'status': 'BLOCKED', 'caseOrdinal': ordinal, 'reason': 'facts_or_external_capability_missing'}
            if self._manifest.tenant_id != self._plan['tenantId']:
                return {'status': 'BLOCKED', 'caseOrdinal': ordinal, 'reason': 'manifest_tenant_binding_changed'}
            state['accepted'] = None  # 重验失败撤销完成计数，不删除历史证据。
            sent = False
            try:
                inventory = self._preflight()
                master = self._live_master()
                self._price(self._spec['cases'][ordinal - 1], case['plannedQuantity'])
                if state['baselines'] is None:
                    self._baseline(state, inventory)
                self._reconstruct(state, case, master)
                self._record(state['sources'])  # 下一次写入前先拒绝历史来源冲突。
                if state['next'] == len(ROLES):
                    self._record(state['sources'])
                    self._accept(state)
                    return {'status': 'SOURCE_COMPLETE', 'caseOrdinal': ordinal}
                role = ROLES[state['next']]
                if role in ('mrp', 'work'):
                    self._stock(master)
                if role not in state['requests']:
                    state['requests'][role] = self._resolve(case['steps'][state['next']]['request'], state['sources'])
                original = state['requests'][role]
                candidate = self._candidate(role, state, case['intentId'])
                if candidate is not None:
                    require(role in state['attempted'], 'unexpected source is not this coordinator creation')
                    self._writer.authorize(original, tenant_id=self._manifest.tenant_id, intent_id=case['intentId'],
                                           target=inventory['target'])
                else:
                    retry = role in state['attempted']

                    def record_dispatch():
                        nonlocal sent
                        # 进入实际 POST 边界后，任何异常类型都不能撤销未决尝试记录。
                        state['attempted'].add(role)
                        sent = True

                    response = self._writer.send(original, tenant_id=self._manifest.tenant_id, intent_id=case['intentId'],
                                                 target=inventory['target'], retry=retry, on_dispatch=record_dispatch)
                    require(isinstance(response, dict) and response.get('code') == 200, 'business response not successful')
                    candidate = self._candidate(role, state, case['intentId'])
                    if candidate is None:
                        return {'status': 'UNCERTAIN', 'caseOrdinal': ordinal, 'reason': 'await_independent_readback'}
                source = self._source(role, candidate, state, case, master)
                staged_sources = {**state['sources'], role: source}
                self._record(staged_sources)
                state['sources'] = staged_sources
                state['next'] += 1
                if state['next'] == len(ROLES):
                    self._reconstruct(state, case, master)
                    self._record(state['sources'])
                    self._accept(state)
                    return {'status': 'SOURCE_COMPLETE', 'caseOrdinal': ordinal}
                return {'status': 'CREATED_READBACK', 'caseOrdinal': ordinal, 'step': role}
            except (ApiError, TimeoutError, OSError):
                return {'status': 'UNCERTAIN' if sent else 'BLOCKED', 'caseOrdinal': ordinal,
                        'reason': 'transport_or_readback_unresolved'}
            except (BatchContractError, fixture.FixtureContractError, PreflightError, GateClosed, ValueError, KeyError, TypeError):
                return {'status': 'UNCERTAIN' if sent else 'BLOCKED', 'caseOrdinal': ordinal, 'reason': 'read_or_contract_gate'}


def _local_path(path):
    resolved = Path(path).resolve()
    require(resolved.parent == LOCAL_DIR.resolve() and resolved.suffix == '.json',
            'explicit JSON file in the repository ignored local directory required')
    return resolved


def prepare_from_file(path):
    path = _local_path(path)
    require(path.is_file() and path.stat().st_size <= 2_000_000, 'bounded explicit input file required')
    return prepare_batch(json.loads(path.read_text(encoding='utf-8-sig')))


def main(argv=None):
    # 无 execute CLI 或授权开关；输入/输出错误不回显路径或 ID。
    parser = argparse.ArgumentParser(add_help=False, exit_on_error=False)
    parser.add_argument('--input')
    parser.add_argument('--output')
    try:
        args, unknown = parser.parse_known_args(argv)
        require(args.input is not None and not unknown, 'explicit local input required')
        plan = prepare_from_file(args.input)
        if args.output:
            output = _local_path(args.output)
            # 不建目录、不覆盖文件、不写版本化业务快照、不隐式持久化。
            with output.open('x', encoding='utf-8') as handle:
                handle.write(_json(plan) + '\n')
        print(_json({'status': 'BLOCKED', 'plannedCases': len(plan['cases']), 'readyCases': 0,
                     'sourceCompleteCases': 0, 'blockedCases': len(plan['cases']),
                     'physicalAcceptance': False, 'slaAccepted': False, 'procurementReady': False}))
        return 0
    except (BatchContractError, argparse.ArgumentError, OSError, ValueError, TypeError):
        print(_json({'status': 'BLOCKED', 'plannedCases': 0, 'readyCases': 0, 'sourceCompleteCases': 0}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
