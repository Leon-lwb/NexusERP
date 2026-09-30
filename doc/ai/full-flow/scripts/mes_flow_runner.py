"""MES 辅助工具默认只读；只有通过来源守卫后，才允许明确授权的单次 API 写入。

绝不执行 SQL 写入或记录凭证。离线测试期间不得使用 --execute。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import time
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Callable

from erp_flow_runner import ApiClient, ApiError


class MesFlowError(RuntimeError):
    """跨模块业务状态不安全、有歧义或不一致。"""


class MesOrderNotReady(MesFlowError):
    """ERP 事务已提交，但 RabbitMQ 的 MES 消费者尚未持久化。"""


def _id(value, label):
    if isinstance(value, int) and not isinstance(value, bool) and 0 < value <= 9007199254740991:
        return str(value)
    if not isinstance(value, str) or not re.fullmatch(r'[0-9]+', value) or int(value) <= 0:
        raise MesFlowError(f'{label} must be an exact positive ID (large IDs as strings)')
    return str(int(value))


def _qty(value, label):
    if isinstance(value, bool) or not isinstance(value, (str, int, Decimal)):
        raise MesFlowError(f'{label} must be an exact decimal quantity')
    try:
        quantity = Decimal(value)
    except InvalidOperation as exc:
        raise MesFlowError(f'{label} is not a decimal quantity') from exc
    if not quantity.is_finite() or quantity <= 0:
        raise MesFlowError(f'{label} must be positive and finite')
    return quantity


def _identity(row, expected, fields, label):
    for field in fields:
        actual, wanted = row.get(field), expected.get(field)
        if field.endswith('Id'):
            actual, wanted = _id(actual, f'{label} {field}'), _id(wanted, f'expected {field}')
        if actual != wanted:
            raise MesFlowError(f'{label} {field} does not match source order')
    if _qty(row.get('plannedQty'), f'{label} plannedQty') != _qty(expected.get('plannedQty'), 'expected plannedQty'):
        raise MesFlowError(f'{label} quantity differs from ERP order')


def _erp_origin_identity(response: dict, expected: dict) -> dict:
    """只读候选身份，绝不是写入或完工授权。"""
    order = _data(response, 'ERP work order')
    if 'tenantId' in order and order['tenantId'] != expected.get('tenantId'):
        raise MesFlowError('ERP detail explicitly belongs to another tenant')
    _identity(order, expected, ('woId', 'woNo', 'materialId'), 'ERP work order')
    if 'planId' in expected and _id(order.get('planId'), 'ERP production plan ID') != _id(expected['planId'], 'intended plan ID'):
        raise MesFlowError('ERP production plan differs from human-reviewed intent')
    return order


def check_erp_origin(response: dict, expected: dict, *, completion_snapshot=None) -> dict:
    """除非同租户的完整 MES 来源证据证明 MQ 已推进，否则必须已下发工单。"""
    order = _erp_origin_identity(response, expected)
    if order.get('woStatus') == 'RELEASED':
        return order
    if order.get('woStatus') not in ('IN_PROGRESS', 'COMPLETED') or not isinstance(completion_snapshot, dict):
        raise MesFlowError('ERP work order not released or MES completion not verified')
    if completion_snapshot.get('tenantId') != expected.get('tenantId') or not expected.get('tenantId'):
        raise MesFlowError('ERP completion proof has no matching authenticated tenant')
    # 绑定实际 ERP 读回结果，不使用调用方提供的其他 ERP 状态或产出。
    _assert_async_erp_completion(completion_snapshot | {'erp': order})
    return order


def resolve_mes_order(rows: list, erp: dict) -> dict:
    """要求唯一的、经 RabbitMQ 产生的 MES 工单，且具备来源身份和工艺路线步骤。"""
    if not isinstance(rows, list):
        raise MesFlowError('MES work order list missing')
    if not rows:
        raise MesOrderNotReady('MES work order has not been consumed')
    if len(rows) != 1 or not isinstance(rows[0], dict):
        raise MesFlowError('MES work order source duplicates or is ambiguous')
    mes = rows[0]
    _id(mes.get('mesWoId'), 'MES work order ID')
    _identity(mes, {'erpWoId': erp['woId'], 'woNo': erp['woNo'],
                    'materialId': erp['materialId'], 'plannedQty': erp['plannedQty']},
              ('erpWoId', 'woNo', 'materialId'), 'MES work order')
    if mes.get('woStatus') not in ('CREATED', 'RELEASED', 'IN_PROGRESS', 'PARTIAL_COMPLETED', 'COMPLETED'):
        raise MesFlowError('MES work order is in an unexpected state')
    steps = mes.get('steps')
    if not isinstance(steps, list) or not steps:
        raise MesFlowError('MES work order has no usable routing steps')
    sequence = []
    for step in steps:
        if not isinstance(step, dict) or not step.get('processCode'):
            raise MesFlowError('MES routing step is incomplete')
        _id(step.get('stepId'), 'MES routing step ID')
        if _id(step.get('mesWoId'), 'MES routing step owner') != mes['mesWoId']:
            raise MesFlowError('MES routing step has wrong owner')
        if type(step.get('sequenceNum')) is not int or step['sequenceNum'] <= 0:
            raise MesFlowError('MES routing sequence is invalid')
        sequence.append(step['sequenceNum'])
    if len(set(sequence)) != len(sequence) or sequence != sorted(sequence):
        raise MesFlowError('MES routing sequence is ambiguous or unsorted')
    return mes


def poll_mes_order(lookup: Callable[[], list], erp: dict, *, attempts=12, interval=0.5, sleep=time.sleep) -> dict:
    """有界的只读轮询；只有真正尚未被消费的工单才可重试。"""
    if not isinstance(attempts, int) or attempts < 1 or attempts > 60 or interval < 0:
        raise MesFlowError('MES poll budget invalid')
    for n in range(attempts):
        try:
            return resolve_mes_order(lookup(), erp)
        except MesOrderNotReady:
            if n == attempts - 1:
                raise
            sleep(interval)
    raise MesOrderNotReady('MES work order has not been consumed')


def plan_card_transition(mes: dict, cards: list, quantity) -> dict:
    """仅按准确的未分配数量创建；复用唯一匹配的已有流转卡。"""
    _id(mes.get('mesWoId'), 'MES work order ID')
    if mes.get('woStatus') not in ('RELEASED', 'IN_PROGRESS', 'PARTIAL_COMPLETED') and not (cards and mes.get('woStatus') == 'COMPLETED'):
        raise MesFlowError('MES work order must be released before creating cards')
    intended = _qty(quantity, 'flow card quantity')
    if intended > _qty(mes.get('plannedQty'), 'MES plannedQty'):
        raise MesFlowError('flow card quantity exceeds MES order plan')
    if not isinstance(cards, list):
        raise MesFlowError('existing flow cards not verified')
    if not cards:
        return {'action': 'create', 'mesWoId': mes['mesWoId'], 'quantity': str(intended)}
    if len(cards) != 1 or not isinstance(cards[0], dict):
        raise MesFlowError('multiple flow cards require manual allocation review')
    card = cards[0]
    if mes.get('woStatus') == 'COMPLETED' and card.get('cardStatus') != 'COMPLETED':
        raise MesFlowError('completed MES order cannot have an unfinished flow card')
    _id(card.get('cardId'), 'flow card ID')
    if _id(card.get('mesWoId'), 'flow card source') != mes['mesWoId'] or _qty(card.get('plannedQty'), 'flow card plannedQty') != intended:
        raise MesFlowError('existing flow card does not match requested allocation')
    if card.get('cardStatus') not in ('CREATED', 'RELEASED', 'IN_PROGRESS', 'COMPLETED'):
        raise MesFlowError('existing flow card has unexpected status')
    steps = card.get('steps')
    if not isinstance(steps, list) or not steps or any(not isinstance(s, dict) or
            _id(s.get('cardId'), 'flow card step owner') != card['cardId'] or
            not s.get('stepRecordId') for s in steps):
        raise MesFlowError('existing flow card lacks real step records')
    return {'action': 'reuse', 'mesWoId': mes['mesWoId'], 'quantity': str(intended),
            'cardId': card['cardId'], 'cardStatus': card['cardStatus']}


def expected_from_manifest(manifest: dict, intent: dict) -> dict:
    """将本地人工创建的 ERP 工单和数量绑定到同一租户及生产计划。"""
    if not isinstance(manifest, dict) or not isinstance(intent, dict):
        raise MesFlowError('business manifest/intent missing')
    tenant = manifest.get('tenantId')
    if not isinstance(tenant, str) or not re.fullmatch(r'[0-9]{6}', tenant) or intent.get('tenantId') != tenant:
        raise MesFlowError('acceptance manifest and intent tenant mismatch')
    records = manifest.get('records')
    if not isinstance(records, list):
        raise MesFlowError('acceptance manifest records missing')
    work_orders = [row for row in records if isinstance(row, dict) and row.get('table') == 'prd_work_order']
    if len(work_orders) != 1 or work_orders[0].get('tenantId') != tenant:
        raise MesFlowError('need one tenant-bound ERP work order')
    rec = work_orders[0]
    if not isinstance(rec.get('parentRefs'), dict) or _id(rec['parentRefs'].get('planId'), 'plan ID') != _id(intent.get('planId'), 'planned source ID'):
        raise MesFlowError('ERP production plan not linked to written intent')
    _qty(intent.get('plannedQty'), 'human-reviewed work order quantity')
    return {'woId': _id(rec.get('entityId'), 'ERP work order ID'),
            'woNo': rec.get('businessCode'), 'materialId': _id(rec['parentRefs'].get('materialId'), 'ERP material ID'),
            'plannedQty': str(intent['plannedQty']), 'tenantId': tenant,
            'planId': _id(intent['planId'], 'production plan ID')}


def assert_routing_step_identity(erp_steps: list, mes_steps: list, mes_wo_id: str) -> int:
    """检查持久化的 MES 步骤快照是否匹配权威 ERP 工艺路线顺序。"""
    if not isinstance(erp_steps, list) or not erp_steps or not isinstance(mes_steps, list) or len(erp_steps) != len(mes_steps):
        raise MesFlowError('ERP/MES routing step counts do not match')
    _id(mes_wo_id, 'MES order ID')
    seen = set()
    for source, target in zip(erp_steps, mes_steps):
        if not isinstance(source, dict) or not isinstance(target, dict):
            raise MesFlowError('ERP/MES routing step is invalid')
        _id(target.get('stepId'), 'MES routing step ID')
        if _id(target.get('mesWoId'), 'MES routing step owner') != _id(mes_wo_id, 'MES order ID'):
            raise MesFlowError('MES routing step owner mismatches')
        for key in ('sequenceNum', 'processId', 'processCode'):
            left, right = source.get(key), target.get(key)
            if key == 'processId':
                left, right = _id(left, 'ERP process ID'), _id(right, 'MES process ID')
            if not left or left != right:
                raise MesFlowError(f'ERP/MES routing step {key} differs')
        pair = (target['sequenceNum'], str(target['processId']))
        if pair in seen:
            raise MesFlowError('MES routing step duplicates')
        seen.add(pair)
    return len(seen)


def _data(response: dict, label: str) -> dict:
    if not isinstance(response, dict) or response.get('code') != 200 or not isinstance(response.get('data'), dict):
        raise MesFlowError(f'{label} API returned no verified detail')
    return response['data']


def inspect_live(client: ApiClient, erp_id: str, expected: dict) -> dict:
    """使用与实时动作相同的租户和来源守卫，执行只读完工检查。"""
    if _id(erp_id, 'ERP work order ID') != _id(expected.get('woId'), 'expected ERP work order ID'):
        raise MesFlowError('requested ERP work order differs from reviewed source')
    snapshot = load_verified_snapshot(client, expected)
    mes, cards = snapshot['mes'], snapshot['cards']
    planned = plan_card_transition(mes, cards, cards[0]['plannedQty']) if cards else None
    return {'erpWoId': _id(erp_id, 'ERP work order ID'), 'mesWoId': mes['mesWoId'],
            'state': mes['woStatus'], 'routingStepCount': len(mes['steps']),
            'cardAction': planned['action'] if planned else None,
            'cardCount': len(cards), **_completion_evidence(snapshot)}


def _rows(response, label):
    if not isinstance(response, dict) or response.get('code') != 200 or not isinstance(response.get('rows'), list):
        raise MesFlowError(f'{label} pagination unavailable')
    total = response.get('total')
    if type(total) is not int or total != len(response['rows']):
        raise MesFlowError(f'{label} pagination incomplete or ambiguous')
    return response['rows']


def _list(response, label):
    if not isinstance(response, dict) or response.get('code') != 200 or not isinstance(response.get('data'), list):
        raise MesFlowError(f'{label} list unavailable')
    return response['data']


def load_verified_snapshot(client, expected):
    """只有经过认证且相互匹配的 ERP/MES GET 读回结果，才可授权单次写入。"""
    tenant_id = expected.get('tenantId')
    if not isinstance(tenant_id, str) or not re.fullmatch(r'[0-9]{6}', tenant_id):
        raise MesFlowError('expected tenant ID is not a reviewed tenant')
    user = _data(client.get('/system/user/getInfo'), 'current user').get('user')
    if not isinstance(user, dict) or user.get('tenantId') != tenant_id:
        raise MesFlowError('authenticated tenant differs from ERP owner')
    erp_id = _id(expected.get('woId'), 'ERP work order ID')
    erp_response = client.get(f'/erp/prd/work/{erp_id}')
    erp = _erp_origin_identity(erp_response, expected)
    if erp.get('woStatus') not in ('RELEASED', 'IN_PROGRESS', 'COMPLETED'):
        raise MesFlowError('ERP state cannot originate a released MES order')
    mes_rows = _rows(client.get('/mes/pro/work/list', {'erpWoId': erp_id, 'pageNum': 1, 'pageSize': 100}), 'MES work order')
    if len(mes_rows) != 1:
        raise MesFlowError('ERP source must have exactly one RabbitMQ-derived MES record')
    mes_id = _id(mes_rows[0].get('mesWoId'), 'MES work order ID')
    mes = resolve_mes_order([_data(client.get(f'/mes/pro/work/{mes_id}'), 'MES work order')], erp)
    # 不能只相信筛选后的列表：须交叉核验两次独立的 API 读回结果。
    _identity(mes_rows[0], erp | {'erpWoId': erp_id}, ('erpWoId', 'woNo', 'materialId'), 'MES list row')
    if _id(mes.get('mesWoId'), 'MES detail ID') != mes_id:
        raise MesFlowError('MES list/detail IDs differ')
    routing_id = _id(mes.get('routingId'), 'MES routing ID')
    erp_steps = _list(client.get(f'/erp/bd/routing/steps/{routing_id}'), 'ERP routing steps')
    assert_routing_step_identity(erp_steps, mes['steps'], mes_id)
    cards = _list(client.get(f'/mes/pro/flow-card/by-wo/{mes_id}'), 'flow cards')
    if len(cards) > 1:
        raise MesFlowError('multiple existing flow cards require human allocation review')
    cards = [_data(client.get('/mes/pro/flow-card/' + _id(row.get('cardId'), 'flow card ID')), 'flow card') for row in cards]
    if cards:
        if mes['woStatus'] == 'COMPLETED' and cards[0].get('cardStatus') == 'COMPLETED':
            pass  # 仅做只读核验，绝不创建第二张流转卡。
        else:
            plan_card_transition(mes, cards, cards[0]['plannedQty'])
        _identity(cards[0], {'mesWoId': mes_id, 'materialId': erp['materialId'],
                              'plannedQty': cards[0]['plannedQty']}, ('mesWoId', 'materialId'), 'flow card')
        if _qty(cards[0]['plannedQty'], 'card quantity') != _qty(erp['plannedQty'], 'ERP order quantity'):
            raise MesFlowError('partial/overallocated card needs human review')
        assert_card_steps(mes['steps'], cards[0])
    # 标准 ERP/MES VO 不包含 tenantId；经认证且按租户限定的 GET 用于绑定
    # 归属。若扩展 VO 明确报告租户，该值必须一致。
    for row in [erp, mes, *mes_rows, *cards]:
        if 'tenantId' in row and row['tenantId'] != tenant_id:
            raise MesFlowError('completion readback explicitly belongs to another tenant')
    snapshot = {'erp': erp, 'mes': mes, 'cards': cards, 'tenantId': tenant_id}
    if mes['woStatus'] == 'COMPLETED':
        _assert_mes_completion_provenance(snapshot)
    check_erp_origin(erp_response, expected, completion_snapshot=snapshot)
    return snapshot



def _assert_mes_completion_provenance(snapshot):
    """要求具备 ERP 来源的 MES 终态工单、完整工艺路线和准确产出。"""
    erp, mes = snapshot['erp'], snapshot['mes']
    if mes.get('sourceType') != 'ERP' or mes.get('woStatus') != 'COMPLETED' or not mes.get('actualEndDate'):
        raise MesFlowError('MES completion lacks ERP source and actual completion time')
    if _id(mes.get('erpWoId'), 'MES completion source ID') != _id(erp.get('woId'), 'ERP work order ID'):
        raise MesFlowError('MES completion points to another ERP work order')
    card = _require_card(snapshot)
    if card.get('cardStatus') != 'COMPLETED':
        raise MesFlowError('MES completion lacks completed source flow card')
    _assert_aggregate_quantities(snapshot)
    good = _nonnegative(mes.get('completedQty'), 'MES completedQty')
    scrap = _nonnegative(mes.get('scrapQty'), 'MES scrapQty')
    if good + scrap != _qty(erp.get('plannedQty'), 'ERP plannedQty'):
        raise MesFlowError('MES actual output does not reconcile with ERP planned quantity')
    return good, scrap


def _assert_async_erp_completion(snapshot):
    """绝不能将 MQ 处理后的 ERP 中间状态报告为已完成。"""
    good, scrap = _assert_mes_completion_provenance(snapshot)
    erp = snapshot['erp']
    if erp['woStatus'] == 'IN_PROGRESS':
        # 消费者先提交 markInProgress，随后 complete() 才写入产出。
        if _nonnegative(erp.get('completedQty'), 'ERP pending completedQty') != 0 or _nonnegative(erp.get('scrapQty'), 'ERP pending scrapQty') != 0:
            raise MesFlowError('ERP in-progress quantity has no matching completed writeback')
    elif erp['woStatus'] == 'COMPLETED':
        if not erp.get('actualEndDate') or _nonnegative(erp.get('completedQty'), 'ERP completedQty') != good or _nonnegative(erp.get('scrapQty'), 'ERP scrapQty') != scrap:
            raise MesFlowError('ERP completion date/quantity differs from MES completed output')
    else:
        raise MesFlowError('unrecognized ERP completion stage')


def _completion_evidence(snapshot):
    status = snapshot['erp']['woStatus']
    mes_completed = snapshot['mes']['woStatus'] == 'COMPLETED'
    if mes_completed:
        _assert_mes_completion_provenance(snapshot)
    return {'erpStatus': status, 'mesCompleted': mes_completed,
            'erpCompletionVerified': mes_completed and status == 'COMPLETED'}


def assert_card_steps(wo_steps, card):
    """流转卡步骤必须按顺序引用匹配的已冻结工单步骤。"""
    steps = card.get('steps')
    if not isinstance(steps, list) or len(steps) != len(wo_steps):
        raise MesFlowError('card step count differs from MES routing')
    for source, step in zip(wo_steps, steps):
        if not isinstance(step, dict) or _id(step.get('cardId'), 'card step owner') != _id(card['cardId'], 'flow card ID'):
            raise MesFlowError('card step owner differs')
        _id(step.get('stepRecordId'), 'flow card step ID')
        if _id(step.get('woStepId'), 'work order step source') != _id(source.get('stepId'), 'work order step ID'):
            raise MesFlowError('card step not from work order routing')
        for field in ('sequenceNum', 'processCode'):
            if step.get(field) != source.get(field):
                raise MesFlowError(f'flow card step {field} differs from routing')
        if _id(step.get('processId'), 'flow card process ID') != _id(source.get('processId'), 'MES work order process ID'):
            raise MesFlowError('flow card process differs from routing')
        if _qty(step.get('plannedQty'), 'card step plannedQty') != _qty(card.get('plannedQty'), 'card plannedQty'):
            raise MesFlowError('card step quantity differs from card')
    return steps


def _require_card(snapshot):
    if len(snapshot['cards']) != 1:
        raise MesFlowError('exactly one complete flow card required')
    return snapshot['cards'][0]


def _current_step(card):
    steps = card['steps']
    active = [step for step in steps if step.get('stepStatus') != 'COMPLETED']
    if not active:
        raise MesFlowError('flow card has no unfinished step')
    if active[0]['stepStatus'] not in ('PENDING', 'IN_PROGRESS'):
        raise MesFlowError('current step has unsafe state')
    if any(step.get('stepStatus') != 'COMPLETED' for step in steps[:steps.index(active[0])]):
        raise MesFlowError('prior routing step not complete')
    if any(step.get('stepStatus') != 'PENDING' for step in active[1:]):
        raise MesFlowError('later routing step unexpectedly started')
    return active[0]


def _nonnegative(value, label):
    if isinstance(value, bool) or not isinstance(value, (str, int, Decimal)):
        raise MesFlowError(f'{label} must be an exact nonnegative quantity')
    try:
        d = Decimal(value)
    except InvalidOperation as exc:
        raise MesFlowError(f'{label} is invalid') from exc
    if not d.is_finite() or d < 0:
        raise MesFlowError(f'{label} must be nonnegative')
    return d


def _report(options, limit, *, exact=False):
    values = {name: _nonnegative(options.get(name, '0' if name != 'goodQty' else None), name)
              for name in ('goodQty', 'defectQty', 'scrapQty')}
    total = sum(values.values())
    if total <= 0 or total > _qty(limit, 'report capacity') or (exact and total != _qty(limit, 'report quantity')):
        raise MesFlowError('report quantity exceeds capacity or omits required output')
    return {name: str(value) for name, value in values.items()}


def _verified_operator(client, tenant_id, value):
    operator = _id(value, 'human operator ID')
    user = _data(client.get(f'/system/user/{operator}'), 'MES operator').get('user')
    if not isinstance(user, dict) or _id(user.get('userId'), 'operator user ID') != operator or user.get('tenantId') != tenant_id or str(user.get('status')) != '0':
        raise MesFlowError('operator is absent, inactive, or outside verified tenant')
    name = user.get('nickName') or user.get('userName')
    if not isinstance(name, str) or not name.strip():
        raise MesFlowError('operator has no real registered name')
    return operator, name


def _dispatches(client, step):
    rows = _list(client.get('/mes/pro/dispatch/by-step/' + _id(step['stepRecordId'], 'step record ID')), 'dispatches')
    if len(rows) > 1:
        raise MesFlowError('multiple dispatches require manual review')
    return rows


def plan_sn_batch(card, existing, quantity):
    qty = _qty(quantity, 'SN count')
    if qty != qty.to_integral_value() or qty > 1000 or qty != _qty(card.get('plannedQty'), 'card plan'):
        raise MesFlowError('SN generation needs integral, full-card quantity (max 1000)')
    if not isinstance(existing, list):
        raise MesFlowError('SN records not verified')
    if existing:
        if len(existing) != int(qty):
            raise MesFlowError('partial/excess SN batch requires manual review')
        ids = set()
        for index, sn in enumerate(sorted(existing, key=lambda x: x.get('snCode', '')), 1):
            sn_id = _id(sn.get('snId'), 'SN ID')
            if sn_id in ids or _id(sn.get('cardId'), 'SN card ID') != _id(card['cardId'], 'card ID'):
                raise MesFlowError('duplicate/wrong SN owner')
            ids.add(sn_id)
            if _id(sn.get('mesWoId'), 'SN order ID') != _id(card['mesWoId'], 'MES order ID') or _id(sn.get('materialId'), 'SN material ID') != _id(card['materialId'], 'card material ID'):
                raise MesFlowError('SN source mismatch')
            if sn.get('snStatus') != 'IN_PRODUCTION':
                raise MesFlowError('existing SN has been consumed, shipped, scrapped or has unknown state')
            if sn.get('snCode') != f'SN-{card["cardNo"]}-{index:04d}':
                raise MesFlowError('SN numbering does not match service rule')
        return {'action': 'reuse', 'quantity': int(qty)}
    return {'action': 'generate', 'quantity': int(qty)}


def plan_andon_call(client, mes, card, call_type, reason):
    if call_type not in ('QUALITY', 'MATERIAL', 'EQUIPMENT', 'OTHER') or not isinstance(reason, str) or not reason.strip() or len(reason) > 500:
        raise MesFlowError('Andon requires a real call type and reason')
    rows = _rows(client.get('/mes/pro/andon/list', {'mesWoId': mes['mesWoId'], 'cardId': card['cardId'], 'pageNum': 1, 'pageSize': 100}), 'Andon calls')
    active = [row for row in rows if row.get('andonStatus') in ('CALLED', 'RESPONDED')]
    if len(active) > 1:
        raise MesFlowError('multiple active Andon calls require human review')
    if active:
        old = active[0]
        if (old.get('callType'), old.get('callReason'), old.get('source')) != (call_type, reason, 'MANUAL'):
            raise MesFlowError('unrelated active Andon call cannot be duplicated')
        if _id(old.get('mesWoId'), 'Andon MES order') != _id(mes['mesWoId'], 'MES order') or _id(old.get('cardId'), 'Andon card') != _id(card['cardId'], 'card') or _id(old.get('stepRecordId'), 'Andon step') != _id(_current_step(card)['stepRecordId'], 'active step'):
            raise MesFlowError('active Andon association mismatch')
        if not old.get('notifyTime') or not old.get('callerId'):
            raise MesFlowError('active Andon lacks manual caller/notification audit')
        return {'action': 'reuse', 'andonId': _id(old.get('andonId'), 'Andon ID')}
    # 此前相同原因的已解决调用可能代表本次尝试写入；
    # 若调用方未提供唯一业务键，则拒绝重复写入。
    if any(row.get('callReason') == reason and row.get('callType') == call_type for row in rows):
        raise MesFlowError('prior call with same reason; new incident needs human review')
    return {'action': 'call'}


def plan_ipqc_inspection(ipqc, details, options):
    """绝不编造质量判定；必须提供全部真实测量明细。"""
    if not isinstance(ipqc, dict) or ipqc.get('status') not in ('PENDING', 'INSPECTING'):
        raise MesFlowError('IPQC is not awaiting real inspection')
    if not isinstance(details, list) or not details:
        raise MesFlowError('IPQC has no inspectable template details')
    measured = options.get('measurements') if isinstance(options, dict) else None
    if not isinstance(measured, list) or len(measured) != len(details):
        raise MesFlowError('every IPQC detail needs a measured result')
    wanted = {_id(row.get('detailId'), 'IPQC detail ID') for row in details}
    actual = set()
    if any(_id(row.get('checkId'), 'IPQC detail owner') != _id(ipqc.get('ipqcId'), 'IPQC owner') for row in details):
        raise MesFlowError('IPQC template detail belongs to another inspection')
    for row in measured:
        if not isinstance(row, dict) or not isinstance(row.get('measuredValue'), str) or not row['measuredValue'].strip():
            raise MesFlowError('IPQC detail has no actual measured value')
        actual.add(_id(row.get('detailId'), 'IPQC measured detail ID'))
    if actual != wanted or len(actual) != len(measured):
        raise MesFlowError('IPQC detail set differs from authoritative template')
    return {'details': measured, 'defectInfo': options.get('defectInfo'), 'remark': options.get('remark')}


def plan_mes_action(client, snapshot, action, options):
    """返回单个安全状态转换；复用操作不修改数据。"""
    mes = snapshot['mes']
    mes_id = _id(mes['mesWoId'], 'MES work order ID')
    if action == 'release-wo':
        if mes['woStatus'] == 'CREATED':
            return {'method': 'PUT', 'path': f'/mes/pro/work/release/{mes_id}', 'target': 'RELEASED'}
        if mes['woStatus'] in ('RELEASED', 'IN_PROGRESS', 'PARTIAL_COMPLETED', 'COMPLETED'):
            return {'reuse': True}
        raise MesFlowError('MES work order cannot be released')
    if action == 'create-card':
        intended = options.get('quantity')
        if _qty(intended, 'card quantity') != _qty(snapshot['erp']['plannedQty'], 'ERP quantity'):
            raise MesFlowError('acceptance card must use full ERP plan quantity')
        result = plan_card_transition(mes, snapshot['cards'], intended)
        if result['action'] == 'reuse':
            return {'reuse': True}
        return {'method': 'POST', 'path': '/mes/pro/flow-card', 'query': {'mesWoId': mes_id, 'quantity': str(_qty(intended, 'card quantity'))}, 'target': 'CREATED'}
    card = _require_card(snapshot)
    card_id = _id(card['cardId'], 'card ID')
    if action == 'release-card':
        if card['cardStatus'] == 'CREATED':
            return {'method': 'PUT', 'path': f'/mes/pro/flow-card/release/{card_id}', 'target': 'RELEASED'}
        if card['cardStatus'] in ('RELEASED', 'IN_PROGRESS', 'COMPLETED'):
            return {'reuse': True}
        raise MesFlowError('flow card cannot be released')
    if card['cardStatus'] not in (('RELEASED', 'IN_PROGRESS', 'COMPLETED') if action in ('finish-dispatch', 'exit', 'ipqc-inspect') else ('RELEASED', 'IN_PROGRESS')):
        raise MesFlowError('flow card is not in a runnable state')
    if action == 'andon-call':
        step = _current_step(card)
        reason = options.get('callReason')
        call_type = options.get('callType')
        call = plan_andon_call(client, mes, card, call_type, reason)
        if call['action'] == 'reuse':
            return {'reuse': True, 'andonId': call['andonId']}
        return {'method': 'POST', 'path': '/mes/pro/andon/call', 'query': {'mesWoId': mes_id, 'cardId': card_id,
                'stepRecordId': _id(step['stepRecordId'], 'step ID'), 'callType': call_type, 'callReason': reason}, 'target': 'CALLED'}
    if action in ('andon-respond', 'andon-resolve'):
        andon_id = _id(options.get('andonId'), 'Andon ID')
        andon = _data(client.get(f'/mes/pro/andon/{andon_id}'), 'Andon')
        if _id(andon.get('mesWoId'), 'Andon order') != mes_id or _id(andon.get('cardId'), 'Andon card') != card_id:
            raise MesFlowError('Andon is not attached to verified card')
        if andon.get('source') != 'MANUAL':
            raise MesFlowError('IoT-origin Andon needs its own ownership workflow')
        before, after = ('CALLED', 'RESPONDED') if action == 'andon-respond' else ('RESPONDED', 'RESOLVED')
        if andon.get('andonStatus') == after:
            if not (andon.get('respondTime') and andon.get('responderId')) or (after == 'RESOLVED' and not andon.get('resolveTime')):
                raise MesFlowError('Andon target status lacks audit trail')
            return {'reuse': True}
        if andon.get('andonStatus') != before:
            raise MesFlowError('Andon state transition not allowed')
        if action == 'andon-resolve' and (not isinstance(options.get('remark'), str) or not options['remark'].strip()):
            raise MesFlowError('Andon resolution must describe actual remediation')
        return {'method': 'PUT', 'path': f'/mes/pro/andon/{"respond" if action == "andon-respond" else "resolve"}/{andon_id}',
                'query': {'remark': options['remark']} if action == 'andon-resolve' else None, 'target': after}
    if action in ('dispatch', 'entry', 'exit', 'collect'):
        requested_step_id = _id(options.get('stepRecordId'), 'reviewed card step ID')
        step = next((row for row in card['steps'] if _id(row.get('stepRecordId'), 'card step ID') == requested_step_id), None)
        if step is None:
            raise MesFlowError('requested step is not part of verified flow card')
        if step.get('stepStatus') != 'COMPLETED' and _id(_current_step(card)['stepRecordId'], 'active step ID') != requested_step_id:
            raise MesFlowError('requested step is not the first unfinished routing step')
    else:
        step = card['steps'][0] if action == 'ipqc-inspect' else (card['steps'][-1] if action == 'finish-dispatch' and card['cardStatus'] == 'COMPLETED' else _current_step(card))
    step_id = _id(step['stepRecordId'], 'card step ID')
    if action == 'dispatch':
        operator, operator_name = _verified_operator(client, snapshot['tenantId'], options.get('operatorId'))
        if 'quantity' in options and _qty(options['quantity'], 'dispatch quantity') != _qty(step['plannedQty'], 'step quantity'):
            raise MesFlowError('dispatch must allocate full step quantity')
        rows = _dispatches(client, step)
        if rows:
            row = rows[0]
            for field, expected in (('mesWoId', mes_id), ('cardId', card_id), ('stepRecordId', step_id), ('operatorId', operator)):
                if _id(row.get(field), 'dispatch ' + field) != expected:
                    raise MesFlowError('existing dispatch identity differs')
            if _qty(row.get('plannedQty'), 'dispatch plan') != _qty(step['plannedQty'], 'step plan') or row.get('dispatchStatus') not in ('PENDING', 'IN_PROGRESS', 'COMPLETED'):
                raise MesFlowError('existing dispatch quantity/state differs')
            return {'reuse': True, 'dispatchId': _id(row.get('dispatchId'), 'dispatch ID')}
        if step.get('stepStatus') != 'PENDING':
            raise MesFlowError('cannot create dispatch for already started step')
        return {'method': 'POST', 'path': '/mes/pro/dispatch', 'query': {'cardId': card_id, 'stepRecordId': step_id,
                'operatorId': operator, 'operatorName': operator_name, 'quantity': str(_qty(step['plannedQty'], 'step quantity'))}, 'target': 'PENDING'}
    if action in ('start-dispatch', 'finish-dispatch'):
        dispatch_id = _id(options.get('dispatchId'), 'dispatch ID') if options.get('dispatchId') else None
        if action == 'finish-dispatch' and dispatch_id:
            existing = _data(client.get(f'/mes/pro/dispatch/{dispatch_id}'), 'dispatch')
            step = next((s for s in card['steps'] if _id(s.get('stepRecordId'), 'card step ID') == _id(existing.get('stepRecordId'), 'dispatch step ID')), None)
            if step is None:
                raise MesFlowError('dispatch step missing from card')
            step_id = _id(step['stepRecordId'], 'card step ID')
        rows = _dispatches(client, step)
        if len(rows) != 1 or _id(rows[0].get('dispatchId'), 'dispatch ID') != dispatch_id:
            raise MesFlowError('exactly one chosen dispatch for current step required')
        dispatch = _data(client.get(f'/mes/pro/dispatch/{dispatch_id}'), 'dispatch')
        for field, expected in (('mesWoId', mes_id), ('cardId', card_id), ('stepRecordId', step_id)):
            if _id(dispatch.get(field), 'dispatch ' + field) != expected:
                raise MesFlowError('dispatch does not belong to verified step')
        if action == 'start-dispatch':
            if dispatch.get('dispatchStatus') == 'IN_PROGRESS' and step.get('stepStatus') == 'IN_PROGRESS':
                return {'reuse': True}
            if dispatch.get('dispatchStatus') != 'PENDING' or step.get('stepStatus') != 'PENDING':
                raise MesFlowError('dispatch cannot start in current state')
            return {'method': 'PUT', 'path': f'/mes/pro/dispatch/start/{dispatch_id}', 'target': 'IN_PROGRESS'}
        qty = _report(options, dispatch['plannedQty'])
        if Decimal(qty['defectQty']) > 0 or Decimal(qty['scrapQty']) > 0:
            raise MesFlowError('dispatch report has no automatic NCR path; human quality decision required')
        if dispatch.get('dispatchStatus') == 'COMPLETED' and step.get('stepStatus') == 'COMPLETED':
            if all(_nonnegative(dispatch.get(k), 'saved dispatch ' + k) == Decimal(qty[k]) for k in qty):
                return {'reuse': True}
            raise MesFlowError('completed dispatch quantities differ from requested report')
        if dispatch.get('dispatchStatus') != 'IN_PROGRESS' or step.get('stepStatus') != 'IN_PROGRESS':
            raise MesFlowError('dispatch must be in progress before report')
        return {'method': 'PUT', 'path': f'/mes/pro/dispatch/complete/{dispatch_id}', 'query': qty, 'target': 'COMPLETED'}
    if action == 'entry':
        if _dispatches(client, step):
            raise MesFlowError('entry/exit station mode cannot mix with dispatch mode')
        operator, _ = _verified_operator(client, snapshot['tenantId'], options.get('operatorId'))
        if step.get('stepStatus') in ('IN_PROGRESS', 'COMPLETED') and step.get('entryCheckResult') == 'PASS':
            if _id(step.get('operatorId'), 'recorded operator ID') == operator:
                return {'reuse': True}
            raise MesFlowError('station entry recorded for another operator')
        if step.get('stepStatus') != 'PENDING':
            raise MesFlowError('station entry requires pending step')
        return {'method': 'POST', 'path': '/mes/pro/flow-card/entry', 'payload': {'cardId': card_id, 'stepRecordId': step_id, 'operatorId': operator}, 'target': 'IN_PROGRESS'}
    if action == 'exit':
        if _dispatches(client, step):
            raise MesFlowError('entry/exit station mode cannot mix with dispatch mode')
        if (step.get('stepStatus') != 'IN_PROGRESS' or step.get('entryCheckResult') != 'PASS') and not (step.get('stepStatus') == 'COMPLETED' and step.get('exitCheckResult') == 'PASS'):
            raise MesFlowError('station must pass entry before exit')
        quantities = _report(options, card['plannedQty'], exact=True)
        if step.get('stepStatus') == 'COMPLETED' and step.get('exitCheckResult') == 'PASS':
            if all(_nonnegative(step.get(k), 'recorded exit ' + k) == Decimal(quantities[k]) for k in quantities):
                return {'reuse': True}
            raise MesFlowError('recorded exit quantities differ')
        return {'method': 'POST', 'path': '/mes/pro/flow-card/exit', 'payload': {'cardId': card_id, 'stepRecordId': step_id, **quantities}, 'target': 'COMPLETED'}
    if action == 'sn-generate':
        # SN 列表接口按 cardId 筛选；显式读取全部分页，避免基于不完整结果重试。
        rows = _rows(client.get('/mes/pro/sn/list', {'cardId': card_id, 'pageNum': 1, 'pageSize': 1000}), 'card SN')
        batch = plan_sn_batch(card, rows, options.get('quantity'))
        if batch['action'] == 'reuse':
            return {'reuse': True}
        return {'method': 'POST', 'path': '/mes/pro/sn/generate', 'query': {'cardId': card_id, 'quantity': batch['quantity']}, 'target': 'generated'}
    if action == 'collect':
        from datetime import datetime
        point_id = _id(options.get('pointId'), 'collection point ID')
        point = _data(client.get(f'/mes/dc/collection-point/{point_id}'), 'collection point')
        if _id(point.get('pointId'), 'collection point ID') != point_id or point.get('status') != 'ACTIVE':
            raise MesFlowError('inactive or mismatched collection point')
        if point.get('processId') is not None and _id(point['processId'], 'collection process') != _id(step['processId'], 'step process'):
            raise MesFlowError('collection point belongs to another process')
        if options.get('source', 'MANUAL') != 'MANUAL':
            raise MesFlowError('IoT values must be ingested via actual MQTT, not manual MES API')
        stamp = options.get('collectTime')
        try:
            if not isinstance(stamp, str) or datetime.strptime(stamp, '%Y-%m-%d %H:%M:%S').strftime('%Y-%m-%d %H:%M:%S') != stamp:
                raise ValueError('not a full local timestamp')
        except ValueError as exc:
            raise MesFlowError('human-entered measurement needs exact collection timestamp') from exc
        data_type = point.get('dataType')
        if data_type == 'NUMERIC':
            value = _nonnegative(options.get('numericValue'), 'actual numeric reading')
            if value.as_tuple().exponent < -4 or value >= Decimal('10000000000'):
                raise MesFlowError('numeric reading cannot be silently rounded or overflow database')
            values = {'numericValue': str(value)}
        elif data_type in ('TEXT', 'ENUM'):
            text = options.get('textValue')
            if not isinstance(text, str) or not text.strip() or len(text) > 500:
                raise MesFlowError('text collection requires actual non-empty reading')
            values = {'textValue': text}
        else:
            raise MesFlowError('collection point data type unverified')
        rows = _rows(client.get('/mes/dc/data-record/list', {'pointId': point_id, 'cardId': card_id,
                     'pageNum': 1, 'pageSize': 1000}), 'collection records')
        matches = [r for r in rows if r.get('collectTime') == stamp and
                   _id(r.get('stepRecordId'), 'collection step ID') == step_id and
                   r.get('source') == 'MANUAL']
        if len(matches) > 1:
            raise MesFlowError('duplicate collection timestamps require review')
        if matches:
            saved = matches[0]
            if (data_type == 'NUMERIC' and _nonnegative(saved.get('numericValue'), 'saved reading') != value) or (data_type != 'NUMERIC' and saved.get('textValue') != values['textValue']):
                raise MesFlowError('existing collection reading differs from actual value')
            return {'reuse': True}
        if step.get('stepStatus') == 'COMPLETED':
            raise MesFlowError('cannot backdate missing collection on completed step')
        return {'method': 'POST', 'path': '/mes/dc/data-record', 'payload': {'pointId': point_id,
                'mesWoId': mes_id, 'cardId': card_id, 'stepRecordId': step_id,
                'collectTime': stamp, 'source': 'MANUAL', **values}, 'target': 'recorded'}
    if action == 'ipqc-inspect':
        ipqc_id = _id(options.get('ipqcId'), 'IPQC ID')
        ipqc = _data(client.get(f'/mes/qc/ipqc/{ipqc_id}'), 'IPQC')
        if _id(ipqc.get('mesWoId'), 'IPQC order') != mes_id or _id(ipqc.get('cardId'), 'IPQC card') != card_id:
            raise MesFlowError('IPQC source does not match MES card')
        ipqc_step_id = _id(ipqc.get('stepRecordId'), 'IPQC step ID')
        ipqc_step = next((row for row in card['steps'] if _id(row.get('stepRecordId'), 'flow card step ID') == ipqc_step_id), None)
        if ipqc_step is None or _id(ipqc.get('processId'), 'IPQC process ID') != _id(ipqc_step.get('processId'), 'routing process ID') or _id(ipqc.get('materialId'), 'IPQC material ID') != _id(card['materialId'], 'card material ID'):
            raise MesFlowError('IPQC step/process/material differs from verified routing')
        detail = _list(client.get(f'/mes/qc/ipqc/{ipqc_id}/details'), 'IPQC details')
        if ipqc.get('status') == 'COMPLETED':
            if ipqc.get('inspectResult') not in ('QUALIFIED', 'UNQUALIFIED') or not ipqc.get('inspectTime') or not ipqc.get('inspectorId'):
                raise MesFlowError('IPQC completed without inspector audit')
            measured = options.get('measurements')
            if not isinstance(measured, list) or len(measured) != len(detail):
                raise MesFlowError('IPQC already complete but measurements unavailable')
            if {(_id(r['detailId'], 'measurement detail ID'), r.get('measuredValue')) for r in measured} != {(_id(r['detailId'], 'recorded detail ID'), r.get('measuredValue')) for r in detail}:
                raise MesFlowError('recorded IPQC detail measurements differ')
            if ipqc.get('inspectResult') == 'UNQUALIFIED':
                _assert_ncr(client, 'IPQC', ipqc_id, mes_id, card_id)
            return {'reuse': True}
        payload = plan_ipqc_inspection(ipqc, detail, options)
        return {'method': 'PUT', 'path': f'/mes/qc/ipqc/inspect-details/{ipqc_id}', 'payload': payload, 'target': 'COMPLETED'}
    raise MesFlowError('unsupported action (IQC/OQC require independent procurement/sales evidence)')


def run_mes_action(client, expected, action, options=None, *, execute=False, sender=None):
    """绝不重试写入。失败后从来源 GET 重新开始，绝不推断成功。"""
    options = options if isinstance(options, dict) else {}
    snapshot = load_verified_snapshot(client, expected)
    plan = plan_mes_action(client, snapshot, action, options)
    if plan.get('reuse'):
        return {'outcome': 'reused', 'action': action, 'mesWoId': snapshot['mes']['mesWoId'],
                **_completion_evidence(snapshot)}
    if not execute:
        return {'outcome': 'dry-run', 'action': action, 'mesWoId': snapshot['mes']['mesWoId']}
    if sender is None or not callable(sender):
        raise MesFlowError('explicit local single-step write transport required')
    if action not in ('release-wo', 'create-card', 'release-card', 'dispatch', 'start-dispatch',
                       'finish-dispatch', 'entry', 'exit', 'sn-generate', 'collect',
                       'andon-call', 'andon-respond', 'andon-resolve', 'ipqc-inspect'):
        raise MesFlowError('action has no bounded readback and cannot write')
    if isinstance(getattr(sender, '__self__', None), MesWriteTransport):
        sender.__self__._arm(plan)  # 只有通过实时 GET 守卫后，才授权一个确定的请求。
    response = sender(plan['method'], plan['path'], query=plan.get('query'), payload=plan.get('payload'))
    if not isinstance(response, dict) or response.get('code') != 200:
        raise MesFlowError('write returned no verified business success; re-read before retry')
    # 每次状态转换后都从业务 API 重新获取数据；单凭成功的 HTTP 状态
    # 绝不能证明预期状态变化已经发生。
    refreshed = load_verified_snapshot(client, expected)
    check_mes_readback(client, snapshot, refreshed, action, options, plan, response)
    return {'outcome': 'applied', 'action': action, 'mesWoId': refreshed['mes']['mesWoId'],
            **_completion_evidence(refreshed)}


def check_mes_readback(client, before, after, action, options, plan, response):
    mes_id = after['mes']['mesWoId']
    if after['tenantId'] != before['tenantId'] or _id(mes_id, 'MES readback ID') != _id(before['mes']['mesWoId'], 'original MES work order ID'):
        raise MesFlowError('post-write snapshot changed tenant or original MES work order')
    if before['cards'] and _id(_require_card(after)['cardId'], 'readback flow card ID') != _id(_require_card(before)['cardId'], 'original flow card ID'):
        raise MesFlowError('post-write snapshot changed original flow card')
    erp_stages = {'RELEASED': 0, 'IN_PROGRESS': 1, 'COMPLETED': 2}
    if erp_stages[after['erp']['woStatus']] < erp_stages[before['erp']['woStatus']]:
        raise MesFlowError('ERP completion stage regressed after MES request')
    if action == 'release-wo':
        if after['mes']['woStatus'] != 'RELEASED':
            raise MesFlowError('MES release not reflected by business API')
    elif action == 'create-card':
        card = _require_card(after)
        if card['cardStatus'] != 'CREATED' or _qty(card['plannedQty'], 'created card') != _qty(options['quantity'], 'requested card'):
            raise MesFlowError('created card state/quantity not verified')
        if _id(_data(response, 'created card').get('cardId'), 'created card response ID') != _id(card['cardId'], 'readback card ID'):
            raise MesFlowError('created card response differs from readback')
    elif action == 'release-card':
        if _require_card(after)['cardStatus'] != 'RELEASED':
            raise MesFlowError('flow card release not reflected')
    elif action == 'dispatch':
        step = _current_step(_require_card(after))
        found = _dispatches(client, step)
        if len(found) != 1 or found[0].get('dispatchStatus') != 'PENDING':
            raise MesFlowError('created dispatch not reflected')
        if _id(_data(response, 'created dispatch').get('dispatchId'), 'created dispatch ID') != _id(found[0].get('dispatchId'), 'readback dispatch ID'):
            raise MesFlowError('created dispatch response differs from readback')
    elif action == 'start-dispatch':
        step = _current_step(_require_card(after))
        dispatch = _data(client.get('/mes/pro/dispatch/' + _id(options['dispatchId'], 'dispatch ID')), 'started dispatch')
        if dispatch.get('dispatchStatus') != 'IN_PROGRESS' or step.get('stepStatus') != 'IN_PROGRESS':
            raise MesFlowError('dispatch/step not started together')
    elif action in ('andon-call', 'andon-respond', 'andon-resolve'):
        andon_id = options.get('andonId') if action != 'andon-call' else _data(response, 'Andon call').get('andonId')
        andon = _data(client.get('/mes/pro/andon/' + _id(andon_id, 'Andon ID')), 'Andon readback')
        if _id(andon.get('mesWoId'), 'Andon order') != _id(mes_id, 'MES order ID') or _id(andon.get('cardId'), 'Andon card') != _id(_require_card(after)['cardId'], 'card ID') or andon.get('andonStatus') != plan['target']:
            raise MesFlowError('Andon source/status not reflected')
        if plan['target'] == 'CALLED' and not andon.get('notifyTime'):
            raise MesFlowError('Andon call notification not recorded')
        if plan['target'] in ('RESPONDED', 'RESOLVED') and not (andon.get('respondTime') and andon.get('responderId')):
            raise MesFlowError('Andon response lacks operator audit')
        if plan['target'] == 'RESOLVED' and not andon.get('resolveTime'):
            raise MesFlowError('Andon close time missing')
    elif action in ('entry', 'exit'):
        result = _data(response, 'station check result')
        if result.get('passed') is not True:
            raise MesFlowError('station entry/exit check did not pass')
        step_id = _id(_current_step(_require_card(before)).get('stepRecordId'), 'original step ID')
        step = next((r for r in _require_card(after)['steps'] if _id(r.get('stepRecordId'), 'readback step ID') == step_id), None)
        if not step:
            raise MesFlowError('station step disappeared after transition')
        if action == 'entry':
            if step.get('stepStatus') != 'IN_PROGRESS' or step.get('entryCheckResult') != 'PASS' or _id(step.get('operatorId'), 'actual operator ID') != _id(options['operatorId'], 'requested operator'):
                raise MesFlowError('station entry failed readback')
        else:
            qty = _report(options, _require_card(before)['plannedQty'], exact=True)
            if step.get('stepStatus') != 'COMPLETED' or step.get('exitCheckResult') != 'PASS' or any(_nonnegative(step.get(k), k) != Decimal(v) for k,v in qty.items()):
                raise MesFlowError('station exit quantity/status failed readback')
            _assert_aggregate_quantities(after)
            if Decimal(qty['defectQty']) > 0:
                _assert_ncr(client, 'PROCESS', step_id, mes_id, _require_card(after)['cardId'])
    elif action == 'finish-dispatch':
        chosen = _id(options.get('dispatchId'), 'dispatch ID')
        saved = _data(client.get(f'/mes/pro/dispatch/{chosen}'), 'completed dispatch')
        old = _current_step(_require_card(before))
        step_id = _id(old['stepRecordId'], 'original step ID')
        step = next((r for r in _require_card(after)['steps'] if _id(r.get('stepRecordId'), 'readback step ID') == step_id), None)
        qty = _report(options, saved.get('plannedQty'))
        if not step or saved.get('dispatchStatus') != 'COMPLETED' or _id(saved.get('cardId'), 'dispatch card ID') != _id(_require_card(after)['cardId'], 'card ID'):
            raise MesFlowError('dispatch not completed for source card')
        if any(_nonnegative(saved.get(k), 'dispatch ' + k) != Decimal(v) for k, v in qty.items()):
            raise MesFlowError('dispatch report quantities differ from request')
        if step.get('stepStatus') != 'COMPLETED' or any(_nonnegative(step.get(k), 'step ' + k) != Decimal(v) for k,v in qty.items()):
            raise MesFlowError('completed dispatch step quantities differ')
        _assert_aggregate_quantities(after)
    elif action == 'sn-generate':
        card = _require_card(after)
        rows = _rows(client.get('/mes/pro/sn/list', {'cardId': _id(card['cardId'], 'card ID'), 'pageNum': 1, 'pageSize': 1000}), 'card SN')
        if plan_sn_batch(card, rows, options.get('quantity'))['action'] != 'reuse':
            raise MesFlowError('SN batch not fully persisted')
        generated = _list(response, 'generated SN')
        if {_id(row.get('snId'), 'generated SN ID') for row in generated} != {_id(row.get('snId'), 'saved SN ID') for row in rows}:
            raise MesFlowError('generated SN IDs differ from database readback')
    elif action == 'collect':
        row = _data(response, 'created collection')
        rec_id = _id(row.get('recordId'), 'record ID')
        stored = _data(client.get(f'/mes/dc/data-record/{rec_id}'), 'collection readback')
        wanted = plan['payload']
        for field in ('pointId', 'mesWoId', 'cardId', 'stepRecordId'):
            if _id(stored.get(field), 'stored ' + field) != _id(wanted[field], 'requested ' + field):
                raise MesFlowError('collected record source mismatch')
        if stored.get('collectTime') != wanted['collectTime'] or stored.get('source') != 'MANUAL':
            raise MesFlowError('collection timestamp/source mismatch')
        if 'numericValue' in wanted and _nonnegative(stored.get('numericValue'), 'recorded value') != Decimal(wanted['numericValue']):
            raise MesFlowError('recorded numeric reading mismatch')
        if 'textValue' in wanted and stored.get('textValue') != wanted['textValue']:
            raise MesFlowError('recorded text reading mismatch')
        rows = _rows(client.get('/mes/dc/data-record/list', {'pointId': wanted['pointId'], 'cardId': wanted['cardId'], 'pageNum': 1, 'pageSize': 1000}), 'collection records')
        if sum(_id(r.get('recordId'), 'record ID') == rec_id for r in rows) != 1:
            raise MesFlowError('collected record missing/duplicated on list')
    elif action == 'ipqc-inspect':
        ipqc_id = _id(options.get('ipqcId'), 'IPQC ID')
        saved = _data(client.get(f'/mes/qc/ipqc/{ipqc_id}'), 'inspected IPQC')
        if saved.get('status') != 'COMPLETED' or saved.get('inspectResult') not in ('QUALIFIED', 'UNQUALIFIED') or not saved.get('inspectTime') or not saved.get('inspectorId'):
            raise MesFlowError('IPQC not completed with operator audit')
        entries = _list(client.get(f'/mes/qc/ipqc/{ipqc_id}/details'), 'inspected IPQC details')
        if {(_id(r.get('detailId'), 'saved detail ID'), r.get('measuredValue')) for r in entries} != {(_id(r.get('detailId'), 'requested detail ID'), r.get('measuredValue')) for r in plan['payload']['details']}:
            raise MesFlowError('IPQC measured details differ from actual submission')
        if saved['inspectResult'] == 'UNQUALIFIED':
            _assert_ncr(client, 'IPQC', ipqc_id, mes_id, _require_card(after)['cardId'])
    else:
        raise MesFlowError('write may have succeeded; action needs verified readback before continuing')



def _assert_aggregate_quantities(snapshot):
    card = _require_card(snapshot)
    if card.get('cardStatus') != 'COMPLETED':
        return  # 中间步骤：服务可能仍有其他未完成步骤。
    completed = [step for step in card['steps'] if step.get('stepStatus') == 'COMPLETED']
    if not completed or len(completed) != len(card['steps']):
        raise MesFlowError('completed flow card still has unfinished routing steps')
    # ProFlowCardServiceImpl.doCompleteCard 按最后一个已完成步骤重新计算，
    # 而报废数量跨全部工序求和，并非只取最后一步。
    final_good = _nonnegative(completed[-1].get('goodQty'), 'final step goodQty')
    total_scrap = sum(_nonnegative(step.get('scrapQty'), 'step scrapQty') for step in card['steps'])
    if _nonnegative(card.get('completedQty'), 'card completedQty') != final_good or _nonnegative(card.get('scrapQty'), 'card scrapQty') != total_scrap:
        raise MesFlowError('flow card quantity aggregation differs from completed steps')
    if snapshot['mes']['woStatus'] == 'COMPLETED':
        if _nonnegative(snapshot['mes'].get('completedQty'), 'MES completedQty') != final_good or _nonnegative(snapshot['mes'].get('scrapQty'), 'MES scrapQty') != total_scrap:
            raise MesFlowError('MES work order aggregation differs from single flow card')


def _assert_ncr(client, source_type, source_id, mes_id, card_id):
    rows = _rows(client.get('/mes/ncr/order/list', {'sourceType': source_type, 'sourceId': source_id,
                 'pageNum': 1, 'pageSize': 100}), 'source NCR')
    if len(rows) != 1 or _id(rows[0].get('woId'), 'NCR MES order') != _id(mes_id, 'MES order ID') or _id(rows[0].get('cardId'), 'NCR card ID') != _id(card_id, 'card ID'):
        raise MesFlowError('quality exception lacks one source-linked NCR')


class MesWriteTransport:
    """独立的本地写入传输，禁用重定向和重试；绝不记录 Token 或错误响应体。"""

    def __init__(self, readonly_client):
        self._client = readonly_client
        self._pending = None

    def _arm(self, plan):
        if self._pending is not None:
            raise MesFlowError('previous MES request outcome needs readback')
        self._pending = (plan['method'], plan['path'], plan.get('query'), plan.get('payload'))

    def send(self, method, path, *, query=None, payload=None):
        from urllib.error import HTTPError, URLError
        if method not in ('PUT', 'POST') or not path.startswith('/mes/'):
            raise MesFlowError('MES transport forbids non-MES writes')
        if self._pending != (method, path, query, payload):
            raise MesFlowError('MES write was not armed by fresh source/routing/quantity guards')
        self._pending = None  # 请求失败或结果有歧义时，绝不能自动重试。
        request = self._client._build_request(method, path, payload, query)
        try:
            with self._client._opener(request, timeout=10) as response:
                body = json.loads(response.read())
            if getattr(response, 'status', 200) != 200 or not isinstance(body, dict) or body.get('code') != 200:
                raise MesFlowError('MES write failed; re-read state before any retry')
            return body
        except (HTTPError, URLError, TimeoutError, ValueError, UnicodeDecodeError):
            raise MesFlowError('MES write outcome unknown; re-read state before any retry') from None
def _options_from_local_file(path, manifest_path):
    if path is None:
        return {}
    if not path.resolve().is_relative_to(manifest_path.resolve().parent):
        raise MesFlowError('options file must be colocated with the local manifest')
    data = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data, dict) or any(key in str(data).lower() for key in ('password', 'secret', 'token', 'clientid')):
        raise MesFlowError('options must contain only reviewed business facts, not credentials')
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description='MES one-step local acceptance; default is read-only')
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--action', choices=('release-wo', 'create-card', 'release-card',
                        'dispatch', 'start-dispatch', 'finish-dispatch', 'sn-generate',
                        'entry', 'exit', 'collect', 'andon-call', 'andon-respond',
                        'andon-resolve', 'ipqc-inspect'))
    parser.add_argument('--options-file', type=Path, help='human-reviewed business JSON beside manifest; no credentials')
    parser.add_argument('--execute', action='store_true', help='write exactly one MES action after fresh read-only guards')
    parser.add_argument('--acknowledge-mes-writes', action='store_true', help='explicit human instruction required with --execute')
    args = parser.parse_args()
    try:
        if args.execute and (not args.action or not args.acknowledge_mes_writes):
            raise MesFlowError('execute requires one action and explicit local write acknowledgment')
        if args.options_file and not args.action:
            raise MesFlowError('options file needs one named action')
        manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
        intent_path = args.manifest.resolve().parent / 'work-order-manual-intent.json'
        intent = json.loads(intent_path.read_text(encoding='utf-8'))
        expected = expected_from_manifest(manifest, intent)
        options = _options_from_local_file(args.options_file, args.manifest)
        if not os.environ.get('NEXUS_FULL_FLOW_TOKEN') or not os.environ.get('NEXUS_FULL_FLOW_CLIENT_ID'):
            raise MesFlowError('authenticated token and client ID must be process-local')
        client = ApiClient()
        if args.action:
            sender = MesWriteTransport(client).send if args.execute else None
            result = run_mes_action(client, expected, args.action, options,
                                    execute=args.execute, sender=sender)
        else:
            verified = load_verified_snapshot(client, expected)
            result = {'outcome': 'read-only', 'erpWoId': expected['woId'],
                      'mesWoId': verified['mes']['mesWoId'], 'state': verified['mes']['woStatus'],
                      'cardCount': len(verified['cards']), 'routingStepCount': len(verified['mes']['steps']),
                      **_completion_evidence(verified)}
        print(json.dumps({'ok': True, **result}, ensure_ascii=False))
        return 0
    except (MesFlowError, ApiError, ValueError, KeyError, OSError, json.JSONDecodeError) as exc:
        # 绝不回显原始异常、数据或 URL；HTTP 响应或选项可能含个人身份信息或 Token。
        print(json.dumps({'ok': False, 'category': type(exc).__name__}), file=__import__('sys').stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
