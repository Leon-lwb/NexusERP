"""采购补货链只读取证与下一步意向（**永不发送写请求**）。

任务 3 第 5 步。 本工具仅做 GET/参数化 SELECT 和离线校验；没有 POST/PUT/DELETE、审批或
人工到货确认实现。密码与 Token 只从当前进程环境读取，不写报告。产生的意向不可直接发送。
调用前由运营方确认备份、审批人、供货承诺；不能用本工具替代外部供应/现场收货事实。

操作顺序（仅供授权操作人员，在另一明确授权的业务入口中执行，本脚本不可执行）：
1. 每次动作前重跑只读预检；按 MRP detailId/tenant 查是否已有采购申请、在途单。
2. 用 propose_requisition 只看意向；按真实供货承诺/有效供应商分组，在界面创建申请。
   POST /erp/pu/requisition 返回 Void；超时后只能经 recover_requisition 的 SQL+API 双读回，
   不得盲目重发；编号必须来自服务端。所选供应商不是该申请 BO 的字段。
3. 人工选择已发布的 Flowable 模型 selectFlowId 后提交申请；冻结申请快照，GET
   /erp/pu/requisition/{id}/proc + /workflow/process/iscompleted 及业务状态 APPROVED 核验，
   不调用 /workflow/task/complete 自动代替真实审批人。
4. PUT /erp/pu/requisition/convert/{id} 取 orderId（申请 ORDERED、订单 DRAFT）；
   该订单缺供应商及预计到货日。用实际物料供应商关系/外部承诺人工填写，真实报价与延期
   证据不能由脚本生成；再次冻结订单快照，提交 Flowable 并核验 APPROVED。
5. POST /erp/pu/order/receive/{orderId} 取 noticeId 而非 receiveId。到货通知 DRAFT，
   无实到不应声称已收；真实到货后由审批人提交到货通知并完成 Flowable，审批通过联动
   DRAFT 收货单；GET /erp/wm/receive/list 按 noticeId 唯一查 receiveId 与服务端编号。
6. 目前 PUT /erp/wm/receive 删除重建行但 BO 不含 purchaseLineId：严禁在修复前编辑收货
   单并继续过账。修复、回归后由仓库按实收填写库位/批次/数量，需检物料必须等 MES IQC
   合格；经人工审批后仅 APPROVED 可 shelf，仅 SHELVED 可 execute，入库过账再重读库存
   交易及采购订单回写。不得用通知计划量冒充实收，也不得直接改库存或审批状态。
7. 每一步中断时冻结现场、按租户/来源行/服务端编号恢复，检查物料、数量、关联和
   Flowable 历史；任何歧义停止，不重复创建，也不自动扩大缺口数量。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from erp_flow_runner import ApiClient  # 复用本仓库只允许 GET 的传输，禁止重定向或修改数据。


class GateClosed(RuntimeError):
    """业务证据不足，不允许任何数据修改。"""


# 查询必须固定、参数化且按租户限定；绝不拼接表名或 ID。
# ERP 数据库为 ry-erp，而非 ry-vue/ry-mes/ry-iot；SELECT 列须与 ry-erp.sql 核对。
SQL = {
    'plan': 'SELECT plan_id,plan_no,plan_name,status,tenant_id FROM prd_production_plan WHERE plan_id=%s AND tenant_id=%s AND del_flag=\'0\'',
    'work': 'SELECT wo_id,wo_no,plan_id,source_id,source_type,tenant_id FROM prd_work_order WHERE wo_id=%s AND tenant_id=%s AND del_flag=\'0\'',
    'requirement': 'SELECT requirement_id,plan_id,status,tenant_id FROM prd_material_requirement WHERE requirement_id=%s AND tenant_id=%s AND del_flag=\'0\'',
    'details': 'SELECT detail_id,requirement_id,material_id,required_quantity,required_date,tenant_id FROM prd_material_requirement_detail WHERE requirement_id=%s AND tenant_id=%s AND del_flag=\'0\'',
    'stocks': 'SELECT stock_id,material_id,stock_quantity,reserved_quantity,frozen_flag,tenant_id FROM wm_material_stock WHERE material_id=%s AND tenant_id=%s AND del_flag=\'0\'',
    'vendor_links': 'SELECT relation_id,material_id,vendor_id,lead_time,min_order_qty,effective_date,expiry_date,status,tenant_id FROM bd_material_vendor WHERE material_id=%s AND tenant_id=%s AND del_flag=\'0\'',
    'vendor': 'SELECT vendor_id,vendor_code,vendor_name,status,tenant_id FROM bd_vendor WHERE vendor_id=%s AND tenant_id=%s AND del_flag=\'0\'',
    'existing_requisitions': 'SELECT r.requisition_id,r.requisition_no,r.status,l.line_id,l.material_id,l.source_id,l.source_type,l.tenant_id FROM pu_purchase_requisition_line l JOIN pu_purchase_requisition r ON r.requisition_id=l.requisition_id AND r.tenant_id=l.tenant_id WHERE l.material_id=%s AND l.source_id=%s AND l.tenant_id=%s AND r.status NOT IN (\'CANCELLED\',\'CLOSED\') AND l.del_flag=\'0\' AND r.del_flag=\'0\'',
    'open_orders': 'SELECT o.order_id,l.material_id,o.tenant_id FROM pu_purchase_order_line l JOIN pu_purchase_order o ON o.order_id=l.order_id AND o.tenant_id=l.tenant_id WHERE l.material_id=%s AND o.tenant_id=%s AND o.status IN (\'DRAFT\',\'SUBMITTED\',\'APPROVING\',\'APPROVED\',\'PARTIALLY_RECEIVED\',\'RECEIVED\',\'ARRIVED\') AND l.del_flag=\'0\' AND o.del_flag=\'0\'',
}
ID_RE = re.compile(r'[1-9][0-9]*\Z')
CODE_RE = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}\Z')


def exact_id(value, field):
    """绝不接受对 JSON/JS 不安全的整数 ID，因为它们可能已经被舍入。"""
    if type(value) is int and 0 < value <= 2**53 - 1:
        return str(value)
    if isinstance(value, str) and ID_RE.fullmatch(value):
        return value
    raise GateClosed(f'{field}: invalid or unsafe ID')


def positive_number(value, label, allow_zero=False):
    try:
        if isinstance(value, bool) or value is None:
            raise ValueError()
        n = Decimal(str(value))
        if not n.is_finite() or (n < 0 if allow_zero else n <= 0):
            raise ValueError()
        return n
    except (InvalidOperation, ValueError, TypeError):
        raise GateClosed(f'{label}: quantity invalid') from None


def day(value, label):
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    if isinstance(value, str):
        try:
            return date.fromisoformat(value[:10]) if re.match(r'^\d{4}-\d{2}-\d{2}(?:$|[ T])', value) else _bad_date(label)
        except ValueError:
            pass
    raise GateClosed(f'{label}: invalid date')


def _bad_date(label):
    raise GateClosed(f'{label}: invalid date')


def one(rows, label, tenant):
    if not isinstance(rows, list) or len(rows) != 1 or not isinstance(rows[0], dict):
        raise GateClosed(f'{label}: expected exactly one tenant-scoped row')
    return scoped(rows[0], label, tenant)


def scoped(row, label, tenant):
    if not isinstance(row, dict) or row.get('tenant_id') != tenant:
        raise GateClosed(f'{label}: tenant mismatch')
    return row


def api_data(client, path):
    response = client.get(path)
    data = response.get('data') if isinstance(response, dict) and response.get('code', 200) == 200 else None
    if not isinstance(data, dict):
        raise GateClosed(f'{path}: missing API detail')
    return data


class ReadOnlyDB:
    """适配已处于 START TRANSACTION READ ONLY 事务中的 MySQL 连接。"""
    def __init__(self, connection):
        self.connection = connection

    def rows(self, name, params):
        if name not in SQL or not isinstance(params, tuple) or len(params) != SQL[name].count('%s'):
            raise GateClosed('Unknown or unbound read-only query')
        with self.connection.cursor() as cursor:
            cursor.execute(SQL[name], params)
            return list(cursor.fetchall())


def _same(db_row, api_row, db_field, api_field, label):
    if exact_id(db_row.get(db_field), label) != exact_id(api_row.get(api_field), label):
        raise GateClosed(f'{label}: API/SQL ID mismatch')


def read_only_preflight(client, db, *, tenant_id, plan_id, work_order_id, material_ids, as_of):
    """重新读取数据库及 API；缺少证明时关闭全部写入守卫。

    快照仅临时存在，不是持久化清单；每次实际动作前必须立即重跑。
    若读取后数据库发生并发变化，仍必须重新读回。
    """
    if not isinstance(tenant_id, str) or not re.fullmatch(r'[0-9]{6,20}', tenant_id):
        raise GateClosed('Invalid tenant')
    plan_id, work_order_id = exact_id(plan_id, 'plan'), exact_id(work_order_id, 'work order')
    if (not isinstance(material_ids, (tuple, list)) or not material_ids
            or any(not isinstance(i, str) for i in material_ids)):
        raise GateClosed('Explicit material IDs required as strings')
    wanted = [exact_id(i, 'material') for i in material_ids]
    if len(wanted) != len(set(wanted)):
        raise GateClosed('Duplicate material ID')
    today = day(as_of, 'snapshot date')
    identity = api_data(client, '/system/user/getInfo').get('user')
    if not isinstance(identity, dict) or identity.get('tenantId') != tenant_id:
        raise GateClosed('Authenticated tenant mismatch')
    exact_id(identity.get('userId'), 'authenticated user')
    plan = one(db.rows('plan', (plan_id, tenant_id)), 'plan', tenant_id)
    work = one(db.rows('work', (work_order_id, tenant_id)), 'work', tenant_id)
    _same(plan, work, 'plan_id', 'plan_id', 'plan/work')
    if plan.get('status') != 'RELEASED' or str(work.get('source_type')) != 'MATERIAL_REQUIREMENT':
        raise GateClosed('Plan/work not released or not MRP sourced')
    req_id = exact_id(work.get('source_id'), 'MRP requirement')
    req = one(db.rows('requirement', (req_id, tenant_id)), 'requirement', tenant_id)
    _same(req, plan, 'plan_id', 'plan_id', 'requirement/plan')
    for label, path, row, field, code_field, api_code in (
        ('plan', f'/erp/prd/plan/{plan_id}', plan, 'plan_id', 'plan_no', 'planNo'),
        ('work', f'/erp/prd/work/{work_order_id}', work, 'wo_id', 'wo_no', 'woNo'),
        ('requirement', f'/erp/prd/requirement/{req_id}', req, 'requirement_id', None, None)):
        detail = api_data(client, path)
        api_id = {'plan': 'planId', 'work': 'woId', 'requirement': 'requirementId'}[label]
        _same(row, detail, field, api_id, label)
        if code_field and (not isinstance(row.get(code_field), str) or not CODE_RE.fullmatch(row[code_field])
                           or detail.get(api_code) != row[code_field]):
            raise GateClosed(f'{label}: server code mismatch')
        if detail.get('status' if label != 'work' else 'woStatus') is not None and label == 'plan' and detail['status'] != 'RELEASED':
            raise GateClosed('Plan API status mismatch')
        if label == 'work':
            _same(work, detail, 'plan_id', 'planId', 'work/API plan')
            _same(work, detail, 'source_id', 'sourceId', 'work/API source')
            if detail.get('sourceType') != work['source_type']:
                raise GateClosed('Work source type mismatch')
        if label == 'requirement':
            _same(req, detail, 'plan_id', 'planId', 'requirement/API plan')
    details = db.rows('details', (req_id, tenant_id))
    if not isinstance(details, list) or not details:
        raise GateClosed('MRP requirement details missing')
    by_mat = {}
    for row in details:
        scoped(row, 'MRP line', tenant_id)
        _same(row, req, 'requirement_id', 'requirement_id', 'MRP line source')
        mat_id = exact_id(row.get('material_id'), 'MRP material')
        if mat_id in wanted:
            if mat_id in by_mat:
                raise GateClosed('Multiple MRP lines per material; cannot deduplicate')
            by_mat[mat_id] = row
    if set(wanted) != set(by_mat):
        raise GateClosed('MRP material missing or mismatched')
    shortages, vendor_links = [], []
    for mat_id in wanted:
        row = by_mat[mat_id]
        source_id = exact_id(row.get('detail_id'), 'MRP detail')
        material = api_data(client, f'/erp/bd/material/{mat_id}')
        if exact_id(material.get('materialId'), 'material') != mat_id or not material.get('materialCode') or not material.get('materialName'):
            raise GateClosed('Material identity/name missing')
        required = positive_number(row.get('required_quantity'), 'required')
        available = Decimal(0)
        for stock in db.rows('stocks', (mat_id, tenant_id)):
            scoped(stock, 'stock', tenant_id)
            if exact_id(stock.get('material_id'), 'stock material') != mat_id:
                raise GateClosed('Stock material mismatch')
            exact_id(stock.get('stock_id'), 'stock row')
            quantity = positive_number(stock.get('stock_quantity'), 'stock', True)
            reserved = positive_number(stock.get('reserved_quantity'), 'reserved', True)
            if stock.get('frozen_flag') in (0, '0'):
                available += max(Decimal(0), quantity - reserved)
            elif stock.get('frozen_flag') not in (1, '1'):
                raise GateClosed('Unknown stock freeze state')
        shortage = max(Decimal(0), required - available)
        if shortage == 0:
            raise GateClosed('No shortage for selected material; do not overpurchase')
        for existing in db.rows('existing_requisitions', (mat_id, source_id, tenant_id)):
            scoped(existing, 'existing requisition', tenant_id)
            # 暂停而非乐观地重复创建；通过 recover_requisition 消除歧义。
            raise GateClosed('Existing requisition found; recover and read back before any retry')
        for existing in db.rows('open_orders', (mat_id, tenant_id)):
            scoped(existing, 'open order', tenant_id)
            raise GateClosed('Existing supply found; reconcile receipts and shortages first')
        matched = []
        for link in db.rows('vendor_links', (mat_id, tenant_id)):
            scoped(link, 'supplier relation', tenant_id)
            if exact_id(link.get('material_id'), 'supplier material') != mat_id:
                raise GateClosed('Supplier relation material mismatch')
            vendor_id = exact_id(link.get('vendor_id'), 'vendor')
            if link.get('status') not in (1, '1') or (link.get('effective_date') and day(link['effective_date'], 'supplier start') > today) or (link.get('expiry_date') and day(link['expiry_date'], 'supplier expiry') < today):
                continue
            vendor = one(db.rows('vendor', (vendor_id, tenant_id)), 'vendor', tenant_id)
            vendor_api = api_data(client, f'/erp/bd/vendor/{vendor_id}')
            _same(vendor, vendor_api, 'vendor_id', 'vendorId', 'vendor/API')
            if vendor.get('status') != 'ACTIVE' or vendor_api.get('status') != 'ACTIVE' or vendor.get('vendor_code') != vendor_api.get('vendorCode') or vendor.get('vendor_name') != vendor_api.get('vendorName'):
                raise GateClosed('Vendor not active or vendor data mismatched')
            matched.append({'materialId': mat_id, 'vendorId': vendor_id,
                            'minOrderQty': str(positive_number(link.get('min_order_qty') or 0, 'minimum', True)),
                            'leadTime': link.get('lead_time'), 'relationId': exact_id(link.get('relation_id'), 'relation')})
        if not matched:
            raise GateClosed('No currently active supplier relation for material')
        vendor_links.extend(matched)
        shortages.append({'materialId': mat_id, 'materialCode': material['materialCode'],
                          'materialName': material['materialName'], 'sourceDetailId': source_id,
                          'requirementId': req_id, 'required': str(required),
                          'available': str(available), 'shortage': str(shortage),
                          'requiredDate': day(row.get('required_date'), 'MRP required date').isoformat()})
    return {'readOnly': True, 'writeEligible': False, 'tenantId': tenant_id,
            'asOf': today.isoformat(), 'planId': plan_id, 'workOrderId': work_order_id,
            'requirementId': req_id, 'planName': plan.get('plan_name'),
            'shortages': shortages, 'vendors': vendor_links,
            'missingGates': ['external_supply_evidence', 'human_approver', 'physical_delivery', 'receive_line_mapping_fix']}


def propose_requisition(snapshot, *, vendor_id, confirmed_supply_date=None):
    """仅为采购申请请求体模板，绝不可发送；数量取已观测的净缺口。

    供应商意向与申请请求体分离，因为 BO 没有 vendorId。
    承诺日期只有具备真实外部证据时，才可单独记录。
    """
    vendor_id = exact_id(vendor_id, 'chosen supplier')
    if not isinstance(snapshot, dict) or snapshot.get('readOnly') is not True or snapshot.get('writeEligible') is not False:
        raise GateClosed('A fresh read-only snapshot is required')
    if confirmed_supply_date is not None:
        raise GateClosed('External supply promise cannot be confirmed by this tool')
    lines = []
    for item in snapshot.get('shortages', []):
        mat = exact_id(item.get('materialId'), 'material')
        matches = [v for v in snapshot.get('vendors', []) if v.get('materialId') == mat and v.get('vendorId') == vendor_id]
        if len(matches) != 1:
            raise GateClosed('Supplier must match each shortlisted material exactly once')
        quantity = positive_number(item.get('shortage'), 'shortage')
        if quantity < positive_number(matches[0].get('minOrderQty'), 'supplier MOQ', True):
            raise GateClosed('Minimum order quantity exceeds gap; explicit business review required')
        lines.append({'materialId': mat, 'materialCode': item['materialCode'], 'materialName': item['materialName'],
                      'applyQuantity': str(quantity), 'requiredDate': item['requiredDate'],
                      'sourceType': 'MATERIAL_REQUIREMENT', 'sourceId': exact_id(item['sourceDetailId'], 'MRP detail')})
    if not lines:
        raise GateClosed('No shortage to purchase')
    return {'sendable': False, 'missingGates': list(snapshot['missingGates']),
            'supplierSelection': vendor_id,
            'endpointForOperatorReview': 'POST /erp/pu/requisition',
            'body': {'requisitionName': '生产备料采购申请', 'sourceType': 'MATERIAL_REQUIREMENT',
                     'requiredDate': min(line['requiredDate'] for line in lines),
                     'puPurchaseRequisitionLineList': lines}}


def _freeze_lines(entity, key):
    lines = entity.get(key)
    if not isinstance(lines, list) or not lines:
        raise GateClosed('Cannot prove nonempty frozen lines')
    normalized = []
    for line in lines:
        if not isinstance(line, dict):
            raise GateClosed('Line shape invalid')
        mat = exact_id(line.get('materialId'), 'frozen material')
        if key == 'puPurchaseRequisitionLineList':
            normalized.append((mat, exact_id(line.get('sourceId'), 'MRP source'),
                               positive_number(line.get('applyQuantity'), 'frozen qty')))
        elif key == 'lineList':
            normalized.append((mat, exact_id(line.get('requisitionLineId'), 'requisition line'),
                               positive_number(line.get('orderQuantity'), 'ordered qty')))
        else:
            raise GateClosed('Unknown line contract')
    if len(set(normalized)) != len(normalized):
        raise GateClosed('Duplicate business line')
    return sorted(normalized)


APPROVAL_ENDPOINTS = {
    'requisition': ('/erp/pu/requisition', 'requisitionId', 'requisitionNo', 'puPurchaseRequisitionLineList'),
    'order': ('/erp/pu/order', 'orderId', 'orderNo', 'lineList'),
    'arrival': ('/erp/wm/arrivalnotice', 'noticeId', 'noticeCode', 'wmArrivalNoticeLineList'),
    'receive': ('/erp/wm/receive', 'receiveId', 'receiveCode', 'wmMaterialReceiveLineList'),
}


def verify_approval(client, kind, frozen_before):
    """仅观察人工 Flowable 审批，不提供批准或完成审批的接口。"""
    if kind not in APPROVAL_ENDPOINTS or not isinstance(frozen_before, dict):
        raise GateClosed('Unknown approval domain')
    base, id_field, code_field, line_field = APPROVAL_ENDPOINTS[kind]
    doc_id = exact_id(frozen_before.get(id_field), 'document')
    path = f'{base}/{doc_id}'
    current = api_data(client, path)
    if exact_id(current.get(id_field), 'approved document') != doc_id or current.get('status') != 'APPROVED':
        raise GateClosed('Human approval not completed')
    if current.get(code_field) != frozen_before.get(code_field) or not CODE_RE.fullmatch(str(current.get(code_field, ''))):
        raise GateClosed('Server-generated document code changed')
    if kind in ('requisition', 'order'):
        _freeze_lines(frozen_before, line_field)
        _freeze_lines(current, line_field)
    elif not isinstance(current.get(line_field), list) or not current[line_field]:
        raise GateClosed('Approval lines missing')
    # 检查每个已冻结的表头和行字段，不能只检查汇总数量：
    # 价格、币种、来源和描述同样是审批关键字段。
    ignored = {id_field, code_field, 'status', 'createTime', 'updateTime',
               'createBy', 'updateBy', 'procInsId', 'actStatus'}
    if any(current.get(field) != value for field, value in frozen_before.items()
           if field not in ignored):
        raise GateClosed('Approved data changed after freeze')
    proc = client.get(path + '/proc')
    proc_id = proc.get('data') if isinstance(proc, dict) and proc.get('code', 200) == 200 else None
    if not isinstance(proc_id, str) or not ID_RE.fullmatch(proc_id):
        raise GateClosed('Flowable process instance missing')
    completed = client.get('/workflow/process/iscompleted', {'procInsId': proc_id})
    if not isinstance(completed, dict) or completed.get('code', 200) != 200 or completed.get('data') is not True:
        raise GateClosed('Flowable instance not completed')
    return {'documentId': doc_id, 'processId': proc_id, 'approved': True}


def recover_requisition(client, db, tenant_id, detail_id, material_id, *, expected_quantity):
    """POST 超时后，必须同时具备唯一的数据库来源证据和匹配的 API 明细。

    候选记录不是重试许可：尝试任何后续状态转换前，
    调用方必须重新获取状态及全部来源行。绝不重发 POST。
    """
    mat, detail = exact_id(material_id, 'material'), exact_id(detail_id, 'MRP detail')
    qty = positive_number(expected_quantity, 'shortage to recover')
    rows = db.rows('existing_requisitions', (mat, detail, tenant_id))
    if not isinstance(rows, list) or len(rows) != 1:
        raise GateClosed('Requisition missing or ambiguous; manual reconcile required')
    row = scoped(rows[0], 'recovered requisition', tenant_id)
    if (exact_id(row.get('material_id'), 'material') != mat
            or exact_id(row.get('source_id'), 'MRP detail') != detail
            or row.get('source_type') != 'MATERIAL_REQUIREMENT'):
        raise GateClosed('Requisition provenance mismatch')
    line_id = exact_id(row.get('line_id'), 'requisition line')
    req_id = exact_id(row.get('requisition_id'), 'requisition')
    code = row.get('requisition_no')
    if not isinstance(code, str) or not CODE_RE.fullmatch(code):
        raise GateClosed('Cannot recover unnumbered requisition')
    current = api_data(client, f'/erp/pu/requisition/{req_id}')
    if (exact_id(current.get('requisitionId'), 'requisition') != req_id
            or current.get('requisitionNo') != code
            or current.get('status') != row.get('status')):
        raise GateClosed('Requisition SQL/API mismatch')
    found = [line for line in current.get('puPurchaseRequisitionLineList', [])
             if exact_id(line.get('lineId'), 'requisition line') == line_id]
    if len(found) != 1 or exact_id(found[0].get('materialId'), 'material') != mat or exact_id(found[0].get('sourceId'), 'MRP detail') != detail or found[0].get('sourceType') != 'MATERIAL_REQUIREMENT' or positive_number(found[0].get('applyQuantity'), 'recovered quantity') != qty:
        raise GateClosed('Recovered line source/quantity differs from frozen shortage')
    return req_id

def _chain_lines(document, collection, parent_field, parent_id):
    """拒绝不完整或有歧义的明细 VO；按物料回退匹配要求 1:1 对应。"""
    parent_id = exact_id(parent_id, f'{collection} document')
    lines = document.get(collection)
    if not isinstance(lines, list) or not lines:
        raise GateClosed(f'{collection}: child rows absent')
    by_material, seen_lines = {}, set()
    for row in lines:
        if not isinstance(row, dict):
            raise GateClosed(f'{collection}: invalid child row')
        line_id = exact_id(row.get('lineId'), f'{collection} line')
        material_id = exact_id(row.get('materialId'), f'{collection} material')
        if exact_id(row.get(parent_field), f'{collection} parent') != parent_id:
            raise GateClosed(f'{collection}: parent mismatch')
        if line_id in seen_lines or material_id in by_material:
            raise GateClosed(f'{collection}: ambiguous duplicate line/material')
        seen_lines.add(line_id)
        by_material[material_id] = row
    return by_material


def verify_supply_chain(req, order, notice, receive, *, vendor_id):
    """核验包含子项的 GET 明细 VO，绝不据此推断实物已收货。

    采购订单行的 requisitionLineId 是真实外键。到货通知 VO 没有 orderLineId，
    收货 VO 也不包含实体的 purchaseLineId；生成的收货行可能
    没有 sourceDocLineId。此时只有跨四份完整单据的
    唯一物料匹配，才能建立临时对应关系。
    若存在显式外键，则必须一致；本辅助函数不确立任何真实库存、IQC 或 Flowable
    证据。
    """
    vendor_id = exact_id(vendor_id, 'supplier')
    specs = ((req, 'requisitionId', 'requisitionNo', 'ORDERED'),
             # 到货通知审批会在实物收货前将采购订单状态设为 RECEIVED。
             (order, 'orderId', 'orderNo', 'RECEIVED'),
             (notice, 'noticeId', 'noticeCode', 'APPROVED'),
             (receive, 'receiveId', 'receiveCode', 'DRAFT'))
    for row, key, code, status in specs:
        if not isinstance(row, dict) or not CODE_RE.fullmatch(str(row.get(code, ''))):
            raise GateClosed('Missing server-generated business number')
        exact_id(row.get(key), key)
        if row.get('status') != status:
            raise GateClosed('Purchase chain status mismatch')
    if (exact_id(order.get('requisitionId'), 'order origin') != exact_id(req['requisitionId'], 'requisition')
            or exact_id(notice.get('vendorId'), 'notice supplier') != vendor_id
            or exact_id(order.get('vendorId'), 'order supplier') != vendor_id
            or notice.get('purOrderCode') != order['orderNo']
            or exact_id(receive.get('noticeId'), 'receive origin') != exact_id(notice['noticeId'], 'arrival notice')
            or exact_id(receive.get('purId'), 'receive PO') != exact_id(order['orderId'], 'PO')
            or not order.get('expectedArrivalDate')):
        raise GateClosed('Purchase source/supplier/date chain mismatch')
    for row, field, expected in ((order, 'requisitionNo', req['requisitionNo']),
                                 (receive, 'noticeCode', notice['noticeCode']),
                                 (receive, 'purCode', order['orderNo']),
                                 (receive, 'vendorId', vendor_id)):
        if row.get(field) is not None and str(row[field]) != expected:
            raise GateClosed(f'{field}: header reference mismatch')
    req_lines = _chain_lines(req, 'puPurchaseRequisitionLineList', 'requisitionId', req['requisitionId'])
    order_lines = _chain_lines(order, 'lineList', 'orderId', order['orderId'])
    notice_lines = _chain_lines(notice, 'wmArrivalNoticeLineList', 'noticeId', notice['noticeId'])
    receive_lines = _chain_lines(receive, 'wmMaterialReceiveLineList', 'receiveId', receive['receiveId'])
    if not (req_lines.keys() == order_lines.keys() == notice_lines.keys() == receive_lines.keys()):
        raise GateClosed('Purchase chain child material sets differ')
    for material, req_line in req_lines.items():
        po_line = order_lines[material]
        notice_line = notice_lines[material]
        receive_line = receive_lines[material]
        if req_line.get('sourceType') != 'MATERIAL_REQUIREMENT':
            raise GateClosed('Requisition line lacks MRP provenance')
        exact_id(req_line.get('sourceId'), 'MRP detail source')
        if exact_id(po_line.get('requisitionLineId'), 'PO requisition line') != exact_id(req_line['lineId'], 'requisition line'):
            raise GateClosed('PO requisition line foreign key mismatch')
        for line in (po_line, notice_line, receive_line):
            if (line.get('materialCode') is not None and req_line.get('materialCode') is not None
                    and line['materialCode'] != req_line['materialCode']):
                raise GateClosed('Material code changed along purchase chain')
        # WmArrivalNoticeLineVo 不提供 orderLineId；若未来 API
        # 提供该字段，必须检查，而非忽略相互矛盾的来源证据。
        if notice_line.get('orderLineId') is not None and exact_id(notice_line['orderLineId'], 'notice PO line') != exact_id(po_line['lineId'], 'PO line'):
            raise GateClosed('Notice PO line foreign key mismatch')
        if receive_line.get('sourceDocLineId') is not None and exact_id(receive_line['sourceDocLineId'], 'receive notice line') != exact_id(notice_line['lineId'], 'notice line'):
            raise GateClosed('Receive notice line foreign key mismatch')
        if receive_line.get('purchaseLineId') is not None and exact_id(receive_line['purchaseLineId'], 'receive PO line') != exact_id(po_line['lineId'], 'PO line'):
            raise GateClosed('Receive PO line foreign key mismatch')

        requested = positive_number(req_line.get('applyQuantity'), 'requisition quantity')
        approved = (positive_number(req_line['approvedQuantity'], 'approved requisition quantity', True)
                    if req_line.get('approvedQuantity') is not None else requested)
        ordered = positive_number(po_line.get('orderQuantity'), 'order quantity')
        already_received = positive_number(po_line.get('receivedQuantity'), 'PO received quantity', True)
        arrival = positive_number(notice_line.get('quantityArrival'), 'arrival quantity')
        draft_received = positive_number(receive_line.get('quantityReceived'), 'receipt quantity', True)
        notice_received = (positive_number(notice_line['receivedQty'], 'notice received quantity', True)
                           if notice_line.get('receivedQty') is not None else 0)
        # 此处是单次转换路径：无依据的采购订单数量变化
        # 不能被伪装成部分交货。下文允许部分到货或收货，
        # 但减少采购数量必须具有明确的已审批数量。
        if (approved > requested or ordered != approved or already_received > ordered
                or arrival > ordered - already_received
                or notice_received + draft_received > arrival
                or draft_received > ordered - already_received):
            raise GateClosed('Purchase child quantities exceed their source or remaining balance')
        if notice_line.get('quantityQuanlified') is not None:
            qualified = positive_number(notice_line['quantityQuanlified'], 'notice qualified quantity', True)
            if qualified > arrival or notice_received + draft_received > qualified:
                raise GateClosed('Receipt exceeds qualified arrival quantity')
        if 'wmMaterialReceiveLineDetailList' in receive_line:
            details = receive_line['wmMaterialReceiveLineDetailList']
            if not isinstance(details, list):
                raise GateClosed('Receive line details missing/invalid')
            detail_ids, detail_quantity = set(), 0
            for detail in details:
                if not isinstance(detail, dict):
                    raise GateClosed('Receive shelf detail invalid')
                detail_id = exact_id(detail.get('detailId'), 'shelf detail')
                if detail_id in detail_ids:
                    raise GateClosed('Duplicate receive detail')
                detail_ids.add(detail_id)
                if (exact_id(detail.get('lineId'), 'shelf parent line') != exact_id(receive_line['lineId'], 'receipt line')
                        or exact_id(detail.get('receiveId'), 'shelf parent receipt') != exact_id(receive['receiveId'], 'receipt')
                        or exact_id(detail.get('materialId'), 'shelf material') != material):
                    raise GateClosed('Shelf detail lineage mismatch')
                detail_quantity += positive_number(detail.get('quantity'), 'shelf detail quantity')
            if detail_quantity > draft_received:
                raise GateClosed('Shelf detail quantity exceeds receipt line')
    return {'gate': 'pending_physical_receipt', 'stockPosted': False,
            'writeEligible': False, 'childMaterialsVerified': len(req_lines),
            'unproven': ['arrival_order_line_fk_not_in_vo',
                         'receipt_purchase_line_fk_not_in_vo', 'physical_receipt_and_stock']}


def receive_edit_gate(source_line, bo_line):
    """2026-09-29：UPDATE 会删除明细行，且 BO 不包含 purchaseLineId；禁止绕过。"""
    raise GateClosed('Receive edit loses purchaseLineId; fix and regression-test production mapping first')


def main(argv=None):
    parser = argparse.ArgumentParser(description='采购链只读预检；无任何业务写权限')
    parser.add_argument('--online-readonly', action='store_true', help='仅 GET + MySQL 只读事务，绝不写库')
    parser.add_argument('--tenant-id', default='000000')
    parser.add_argument('--plan-id', required=True)
    parser.add_argument('--work-order-id', required=True)
    parser.add_argument('--material-id', action='append', required=True, help='repeat for every MRP material ID')
    parser.add_argument('--db-host', default='127.0.0.1')
    parser.add_argument('--db-name', default='ry-erp')
    args = parser.parse_args(argv)
    if not args.online_readonly:
        parser.error('Online read must be explicitly requested; otherwise run offline unittest')
    if args.db_host not in ('127.0.0.1', 'localhost') or args.db_name != 'ry-erp':
        parser.error('Only local ERP datasource is allowed')
    if not all(os.getenv(k) for k in ('NEXUS_FULL_FLOW_TOKEN', 'NEXUS_FULL_FLOW_CLIENT_ID', 'NEXUS_READONLY_DB_USER', 'NEXUS_READONLY_DB_PASSWORD')):
        parser.error('Missing process-only authentication environment; no secrets accepted in CLI arguments')
    try:
        import pymysql
        conn = pymysql.connect(host=args.db_host, port=3306, user=os.environ['NEXUS_READONLY_DB_USER'],
                               password=os.environ['NEXUS_READONLY_DB_PASSWORD'], database=args.db_name,
                               read_timeout=8, write_timeout=8, connect_timeout=5,
                               cursorclass=pymysql.cursors.DictCursor, autocommit=False)
        try:
            with conn.cursor() as cursor:
                cursor.execute('START TRANSACTION READ ONLY')
            result = read_only_preflight(ApiClient(), ReadOnlyDB(conn), tenant_id=args.tenant_id,
                                         plan_id=args.plan_id, work_order_id=args.work_order_id,
                                         material_ids=tuple(args.material_id), as_of=date.today())
            # 绝不记录 HTTP 响应体、密码、Token、传输异常或供应商私密数据。
            print(json.dumps({'readOnly': True, 'writeEligible': False,
                              'materialsWithShortage': len(result['shortages']),
                              'missingGates': result['missingGates']}, ensure_ascii=False))
            return 0
        finally:
            conn.rollback()
            conn.close()
    except Exception:
        print('Read-only evidence incomplete or backend unavailable (credentials/response suppressed)', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
