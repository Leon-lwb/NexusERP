"""T18 有界离线盘点：不联网、不读写数据库、不创建业务对象、不落盘报告。

接口：build_readiness(cases, expected, evidence, *, tenant_id, commit_sha,
                      build_id, target_count=40) -> dict；不会修改输入。
CLI 必须显式提供 --cases/--expected JSONL、--evidence JSON（可用 - 读 stdin），
以及 --tenant-id/--commit-sha/--build-id。仅向 stdout 输出 JSON，完整就绪退出 0，
未就绪或输入错误退出 2。没有默认文件、网络客户端、输出文件或执行开关。

输入规范（version 1，封装字段不是新增 API）：
* cases/expected 沿用 delivery-risk/cases.jsonl、expected-results.jsonl 字段。
  通过 caseId 配对，不将历史 DEMO bizId 当作已存在的业务来源。
* evidence = {"sources": [...], "runs": [...]}，各列表每 caseId 最多一条；
  重跑须由调用者明确选择一次，绝不按 runId 凑案例。允许缺证据以报告阻塞。
* source = {caseId, tenantId, commitSha, buildId, bizType:"WORK_ORDER", bizId,
  bizCode, baselineIds:[创建前本租户完整工单ID快照], baselineAt, capturedAt,
  readbacks:{"/erp/prd/work/<bizId>":{code:200,data:{woId,woNo,tenantId,
  woStatus,createTime,...}}, ...}, measurements:{事实名:{path,field}}}。
  baselineAt/capturedAt 必须有时区；原生 createTime 无时区时按 capturedAt 偏移解释。
  baselineIds 排除既有来源，创建时间与基线、回读时刻相容；秒精度沿用 batch 规则。
  所有业务ID必须为非零十进制字符串，编号不得为 DEMO/TEST/MOCK 占位。
  measurements 和回读事实沿用 runner 守卫；API 没有对应字段时必须保持未验证，
  不能自行增加 KPI、使用手写 verified/PASS 标记或把 HTTP 200 当作事实。
* run = {caseId, tenantId, commitSha, buildId, runId, elapsedMs,
  detail:{code:200,data:{run,actions,evidence}}, evidence:{code:200,data:[...]},
  timeline:{code:200,data:[...]}, execution?:{detail:最终详情响应,
  readbacks:[{actionId,row:独立持久化业务回读}]}}。
  detail/evidence/timeline 沿用 ai_decision_runner 的真实字段和纯断言。
  持久化证据另核对 ID、稳定来源键及 NUMBER/TEXT 值；sourceCode 可替代 sourceId，
  DERIVED 数值必须有同版本数值父证据且最终落到 ERP 根，不能用空 JSON 或循环自证。
  数值证据须有 observedAt，dataVersion 必须匹配运行 inputSnapshotHash；
  RAG 文本有文档版本，observedAt 可为空。ERP 聚合可凭 sourceCode 定位，空依赖列表不是来源证明。
  execution 必须绑定同一运行/快照及原动作，全部 APPROVED/SUCCEEDED；
  ERP 结果核对 PRD_SCHEDULE_PLAN / PU_PURCHASE_REQUISITION 的真实主键、编号、
  decisionRunId/decisionActionId/decisionIdempotencyKey 和状态。站内通知核对
  SysUserNotification 的 notificationId/userId/refType/refId/extJson；单独的
  acceptedInternally 不是持久化回读，更不证明供应商或外部送达。

plannedCases 仅为输入计划数；independentCreatedSources 按 (bizType,bizId) 去重，
重复来源涉及的所有案例禁止分析通过。analysableCases 还要求 RELEASED/IN_PROGRESS
且逐项事实可核验。analysisPassedCases 不含按声明代码失败且无动作的
expectedRejectedCases；executionClosedCases 仅计实际有动作并完成回读闭环者，
无动作的 SUCCEEDED 和预期拒绝均不计成功执行。

analysisReady 是目标集合的完整预期结果；officialFullReady 另外要求原 40 个
DELIVERY-001..040、40 唯一来源、同租户/SHA/build、所有应执行案例闭环及完整计时。
performance.signingEligible 仅为当前单构建全量 P50/P95 的可签发资格，不是 SLA 达标。
只统计明确 finite 且 >0 的数值 elapsedMs，nearest-rank 百分位；失败/未知的当前
计时仍纳入描述统计，分母始终 target_count，不能筛选成功后宣称全量。

信任边界：本工具只验证显式导出的证据一致性，不能证明导出文件真实、基线完整、
SHA 已部署或采集器可信；不自动核对 Git/运行服务，也不自动签名。生产验收、
现实竞赛提交、外部送达及 SLA 验收始终不在此工具认领范围。
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timedelta
from decimal import Decimal, InvalidOperation
import json
from math import ceil, isfinite
from pathlib import Path
import re
import sys

# 只复用纯校验器；不实例化 DecisionClient/DecisionRunner，不导入批量写入协调器。
from ai_decision_runner import FINISHED, PENDING, LocalDecisionCase, _assert_case, _code, _fact_proofs, strict_id


class ReadinessInputError(ValueError):
    """文件、计划或显式输入契约无效；不能产生通过报告。"""


OFFICIAL_CASES = {f'DELIVERY-{i:03d}' for i in range(1, 41)}
ACTIONS = {'CREATE_SCHEDULE_PLAN_DRAFT', 'CREATE_PURCHASE_REQUISITION_DRAFT', 'SEND_EXPEDITE_NOTICE'}
SOURCE_FIELDS = {'caseId', 'tenantId', 'commitSha', 'buildId', 'bizType', 'bizId', 'bizCode',
                 'baselineIds', 'baselineAt', 'capturedAt', 'readbacks', 'measurements'}
RUN_FIELDS = {'caseId', 'tenantId', 'commitSha', 'buildId', 'runId', 'elapsedMs', 'detail', 'evidence', 'timeline', 'execution'}
TIMELINE_STATES = {'RUN': FINISHED | PENDING, 'ANALYSIS': {'READY'},
                   'NODE': {'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED'},
                   'ACTION': {'NOT_STARTED', 'EXECUTING', 'SUCCEEDED', 'FAILED_PRECHECK', 'FAILED',
                              'SKIPPED', 'COMPENSATING', 'COMPENSATED', 'COMPENSATE_FAILED'}}
EXPECTATION_FIELDS = ('bizId', 'bizCode', 'expectedStatus', 'expectedRiskLevel', 'expectedConfidence',
                      'expectedRootCauses', 'recommendedScenarioCode', 'requiredActions', 'forbiddenActions')
EVIDENCE_ERRORS = (ValueError, TypeError, KeyError, AttributeError, OverflowError, RecursionError)


def _require(condition, label):
    if not condition:
        raise ReadinessInputError(label)


def _index(rows, label):
    _require(type(rows) is list, label + ' must be a list')
    result = {}
    for row in rows:
        _require(type(row) is dict, label + ' row must be an object')
        cid = row.get('caseId')
        _require(type(cid) is str and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,127}', cid), 'invalid caseId')
        _require(cid not in result, 'duplicate caseId')
        result[cid] = row
    return result


def _strings(values):
    return (type(values) is list and all(type(v) is str and v.strip() for v in values)
            and len(values) == len(set(values)))


def _contracts(cases, expected, target_count):
    plans, wants = _index(cases, 'cases'), _index(expected, 'expected')
    _require(0 < len(plans) <= target_count and plans.keys() == wants.keys(), 'empty or unmatched case set')
    for cid, case in plans.items():
        want = wants[cid]
        _require(all(k in case and k in want and case[k] == want[k] for k in EXPECTATION_FIELDS)
                 and case.get('expectedFailureCode') == want.get('expectedFailureCode'), 'expectations differ')
        strict_id(case['bizId'])
        _require(type(case['bizCode']) is str and bool(case['bizCode'].strip()), 'missing historical business code')
        _require(case.get('bizType') == 'WORK_ORDER' and type(case.get('facts')) is dict and case['facts'],
                 'current contract requires WORK_ORDER facts')
        _require(all(type(v) in (bool, int, float, str) and (type(v) is not float or isfinite(v))
                     for v in case['facts'].values()), 'invalid historical facts')
        _require(all(_strings(case[k]) for k in ('expectedRootCauses', 'requiredActions', 'forbiddenActions')),
                 'expectation arrays must be unique strings')
        _require(set(case['requiredActions']) <= ACTIONS
                 and not set(case['requiredActions']) & set(case['forbiddenActions']), 'unsupported actions')
        status = case['expectedStatus']
        _require(status in ('SUCCEEDED', 'WAITING_APPROVAL', 'FAILED')
                 and case['expectedConfidence'] in ('LOW', 'MEDIUM', 'HIGH'), 'unknown expected status/confidence')
        if status == 'FAILED':
            failure = case.get('expectedFailureCode')
            _require(type(failure) is str and re.fullmatch(r'[A-Z][A-Z0-9_]+', failure)
                     and case['expectedRiskLevel'] is None and case['recommendedScenarioCode'] is None
                     and not case['requiredActions'], 'invalid expected refusal')
        else:
            _require(case['expectedRiskLevel'] in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
                     and not case.get('expectedFailureCode'), 'incomplete expected analysis')
    return plans, wants


def _scope(row, tenant_id, commit_sha, build_id):
    _require(row.get('tenantId') == tenant_id and row.get('commitSha') == commit_sha
             and row.get('buildId') == build_id, 'scope mismatch')


def _data(document, kind=dict):
    _require(type(document) is dict and type(document.get('code')) is int and document['code'] == 200
             and type(document.get('data')) is kind, 'incomplete native readback')
    return document['data']


class _OfflineReads:
    """只查询调用者提供的内存快照；缺失路径绝不回退到网络。"""
    def __init__(self, documents):
        _require(type(documents) is dict, 'readbacks must be an object')
        self.documents = documents

    def get(self, path):
        document = self.documents[path]
        _data(document)
        return document


def _timestamp(value, zone=None):
    _require(type(value) is str, 'timestamp missing')
    stamp = datetime.fromisoformat(value)
    if stamp.tzinfo is None and zone is not None:
        stamp = stamp.replace(tzinfo=zone)
    _require(stamp.utcoffset() is not None, 'timestamp timezone missing')
    return stamp


def _source(case, want, source, scope):
    _require(set(source) <= SOURCE_FIELDS, 'unknown source envelope field')
    _scope(source, *scope)
    _require(source.get('bizType') == case['bizType'], 'unsupported source business type')
    biz_id, code = strict_id(source.get('bizId')), _code(source.get('bizCode'))
    baseline = source.get('baselineIds')
    _require(type(baseline) is list, 'creation baseline missing')
    _require(all(strict_id(v) for v in baseline) and len(baseline) == len(set(baseline))
             and biz_id not in baseline, 'source predates baseline')
    captured, started = _timestamp(source.get('capturedAt')), _timestamp(source.get('baselineAt'))
    _require(started <= captured, 'baseline after readback')
    reads = _OfflineReads(source.get('readbacks'))
    work = _data(reads.get('/erp/prd/work/' + biz_id))
    _require(type(work.get('woId')) is str and work['woId'] == biz_id and work.get('woNo') == code
             and work.get('tenantId') == scope[0] and work.get('delFlag', '0') in ('0', 0)
             and work.get('woStatus') in ('CREATED', 'RELEASED', 'IN_PROGRESS'), 'work source identity/state mismatch')
    created = _timestamp(work.get('createTime'), captured.tzinfo)
    _require(created + timedelta(seconds=1) > started and created <= captured, 'creation time outside baseline')
    local = LocalDecisionCase(case['caseId'], biz_id, code, scope[0], case['facts'], case['expectedStatus'],
                              case.get('expectedFailureCode'), want, source.get('measurements'))
    return local, work, reads


def _run_row(run, local, scope):
    _require(set(run) <= RUN_FIELDS, 'unknown run envelope field')
    _scope(run, *scope)
    run_id = strict_id(run.get('runId'))
    view = _data(run.get('detail'))
    row = view.get('run')
    _require(type(row) is dict and row.get('id') == run_id and row.get('tenantId') == local.tenant_id
             and row.get('bizType') == 'WORK_ORDER' and row.get('bizId') == local.work_order_id
             and row.get('bizCode') == local.work_order_code, 'run is not bound to source')
    return row


def _json_object(text):
    value = json.loads(text, object_pairs_hook=_unique_keys, parse_constant=_bad_constant)
    _require(type(value) is dict, 'expected JSON object')
    return value


def _strict_persisted_evidence(run, observed):
    """按 EvidenceItem/DecisionEvidenceService 的来源键和实际持久化字段校验。"""
    by_key, ids, parents = {}, set(), {}
    for item in _data(run.get('evidence'), list):
        _require(type(item) is dict, 'persisted evidence must be an object')
        eid = strict_id(item.get('id'))
        _require(eid not in ids and item.get('runId') == observed['id']
                 and item.get('tenantId') == observed['tenantId'], 'evidence ID/run/tenant mismatch')
        ids.add(eid)
        _require(all(type(item.get(k)) is str and item[k].strip() for k in
                     ('evidenceKey', 'sourceDomain', 'sourceType', 'metricCode', 'metricName', 'metricValue')),
                 'evidence source/metric/value missing')
        source = item.get('sourceId')
        if source is not None:
            strict_id(source)
        else:
            source = item.get('sourceCode')
            _require(type(source) is str and source.strip(), 'stable evidence source missing')
        key = item['evidenceKey']
        _require(key == ':'.join((item['sourceDomain'], item['sourceType'], source, item['metricCode']))
                 and key not in by_key, 'evidence key differs from stable source')
        _require(item.get('valueType') in ('NUMBER', 'TEXT') and item.get('verifiable') in ('Y', 'N'),
                 'unknown evidence value type/verifiability')
        stamp = item.get('observedAt')
        if stamp is not None:
            _require(type(stamp) is str, 'invalid evidence observation time')
            datetime.fromisoformat(stamp)  # 原生 LocalDateTime 无时区；RAG 生效日期允许缺失。
        if item['valueType'] == 'NUMBER':
            _require(item['sourceDomain'] in ('ERP', 'DERIVED') and item['verifiable'] == 'Y'
                     and stamp is not None and type(item.get('dataVersion')) is str and item['dataVersion'].strip()
                     and item['dataVersion'] == observed.get('inputSnapshotHash'),
                     'number has no versioned business observation')
            _require(re.fullmatch(r'[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[Ee][+-]?[0-9]+)?',
                                  item['metricValue']), 'invalid native decimal syntax')
            try:
                _require(Decimal(item['metricValue']).is_finite(), 'non-finite evidence value')
            except InvalidOperation:
                raise ReadinessInputError('invalid evidence number') from None
        if item['sourceDomain'] == 'RAG':
            _require(type(item.get('dataVersion')) is str and item['dataVersion'].strip(), 'RAG version missing')
        lineage = json.loads(item.get('derivedFromJson') or '[]', object_pairs_hook=_unique_keys,
                             parse_constant=_bad_constant)
        _require(_strings(lineage) and bool(lineage) == (item['sourceDomain'] == 'DERIVED'),
                 'invalid derived evidence parents')
        by_key[key], parents[key] = item, set(lineage)
    for key, lineage in parents.items():
        _require(lineage <= by_key.keys(), 'missing persisted evidence parent')
        if by_key[key]['valueType'] == 'NUMBER':
            _require(all(by_key[p]['valueType'] == 'NUMBER'
                         and by_key[p].get('dataVersion') == by_key[key].get('dataVersion') for p in lineage),
                     'numeric parents must share the business version')
    pending = {key for key, item in by_key.items() if item['sourceDomain'] == 'DERIVED'}
    rooted = set(by_key) - pending
    while pending:
        resolved = {key for key in pending if parents[key] <= rooted}
        _require(resolved, 'unrooted or cyclic derived evidence')
        rooted.update(resolved)
        pending.difference_update(resolved)


def _strict_analysis_documents(run, observed):
    """补足旧 runner 未校验的未知状态、重复动作ID和嵌套 JSON 歧义。"""
    actions = _data(run['detail'])['actions']
    _require(type(actions) is list, 'actions missing')
    ids = []
    for action in actions:
        ids.append(strict_id(action['id']))
        _require(action.get('approvalStatus') in ('PROPOSED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'NOT_AUTHORIZED'),
                 'unknown action approval status')
    _require(len(ids) == len(set(ids)), 'duplicate persisted action ID')
    timeline = _data(run.get('timeline'), list)
    for event in timeline:
        _require(type(event) is dict and event.get('status') in TIMELINE_STATES.get(event.get('type'), ()),
                 'unknown timeline type/status')
    _strict_persisted_evidence(run, observed)
    if observed.get('status') != 'FAILED':
        strict_id(observed.get('analysisFlowInstanceId'))
        _json_object(observed['resultJson'])
        _require(any(event.get('type') == 'NODE' and event.get('status') == 'SUCCEEDED'
                     and type(event.get('time')) in (int, float) and isfinite(event['time']) and event['time'] > 0
                     for event in timeline), 'no successful timed Flow node')


def _execution_closed(run, local):
    """核对真实动作和持久化目标；无动作分析成功、拒绝及事件接收均不冒充执行。"""
    execution = run['execution']
    original = _data(run['detail'])
    final = _data(execution['detail'])
    row, before = final['run'], original['run']
    _require(row.get('status') == 'SUCCEEDED' and all(row.get(k) == before.get(k) for k in
        ('id', 'tenantId', 'bizType', 'bizId', 'bizCode', 'inputSnapshotHash', 'resultJson', 'analysisFlowInstanceId')),
        'execution run/snapshot mismatch')
    originals = {a['id']: a for a in original['actions']}
    actions, targets = final['actions'], execution['readbacks']
    _require(type(actions) is list and type(targets) is list and len(actions) == len(originals) > 0
             and len(targets) == len(actions), 'incomplete execution actions/readbacks')
    target_map = {t['actionId']: t['row'] for t in targets}
    _require(len(target_map) == len(targets), 'duplicate action target')
    seen = set()
    for action in actions:
        aid = strict_id(action['id'])
        prior = originals[aid]
        _require(aid not in seen and action.get('tenantId') == local.tenant_id and action.get('runId') == before['id']
                 and action.get('actionType') == prior['actionType'] and action.get('approvalStatus') == 'APPROVED'
                 and action.get('executionStatus') == 'SUCCEEDED', 'execution action mismatch')
        seen.add(aid)
        payload, frozen = _json_object(action['payloadJson']), _json_object(prior['payloadJson'])
        # 比较解析后的对象，忽略键序/空白但保留 JSON 类型，不能将 true 当作数值 1。
        _require(json.dumps(payload, sort_keys=True, allow_nan=False) ==
                 json.dumps(frozen, sort_keys=True, allow_nan=False), 'execution payload differs from frozen action')
        key = action.get('idempotencyKey')
        _require(type(key) is str and key and key == prior.get('idempotencyKey'), 'missing action idempotency')
        target = target_map[aid]
        _require(type(target) is dict and target.get('tenantId') == local.tenant_id
                 and target.get('delFlag', '0') in ('0', 0), 'target tenant/deletion mismatch')
        action_type = action['actionType']
        if action_type == 'SEND_EXPEDITE_NOTICE':
            strict_id(target.get('notificationId'))
            _require(action.get('resultBizType') == 'SYS_NOTIFICATION' and target.get('category') == 'ai_decision'
                     and target.get('refType') == 'ai_decision_run' and target.get('refId') == before['id']
                     and strict_id(target.get('userId')) == strict_id(payload.get('targetUserId'))
                     and _json_object(target['extJson']).get('idempotencyKey') == key, 'notification not persisted')
        else:
            if action_type == 'CREATE_SCHEDULE_PLAN_DRAFT':
                biz_type, id_field, code_field, status = 'PRD_SCHEDULE_PLAN', 'planId', 'planNo', 'COMPLETED'
            else:
                biz_type, id_field, code_field, status = 'PU_PURCHASE_REQUISITION', 'requisitionId', 'requisitionNo', 'DRAFT'
            _require(action.get('resultBizType') == biz_type
                     and strict_id(action.get('resultBizId')) == strict_id(target.get(id_field))
                     and _code(action.get('resultBizCode')) == target.get(code_field) and target.get('status') == status
                     and (action_type != 'CREATE_PURCHASE_REQUISITION_DRAFT' or target.get('sourceType') == 'AI_DECISION')
                     and target.get('decisionRunId') == before['id']
                     and target.get('decisionActionId') == aid and target.get('decisionIdempotencyKey') == key,
                     'business target not independently bound')
    return True


def build_readiness(cases, expected, evidence, *, tenant_id, commit_sha, build_id, target_count=40):
    """纯函数：对明确目标构建的离线证据作保守盘点，不推断缺失事实或申请授权。"""
    _require(type(target_count) is int and target_count > 0, 'target_count must be positive')
    _require(type(tenant_id) is str and re.fullmatch(r'[0-9]{6,20}', tenant_id), 'explicit tenant required')
    _require(type(commit_sha) is str and re.fullmatch(r'[0-9a-f]{40}', commit_sha), 'full lowercase SHA required')
    _require(type(build_id) is str and re.fullmatch(r'[A-Za-z0-9_.-]{1,128}', build_id), 'explicit build required')
    scope = tenant_id, commit_sha, build_id
    plans, wants = _contracts(cases, expected, target_count)
    _require(type(evidence) is dict and set(evidence) == {'sources', 'runs'}, 'sources/runs bundle required')
    sources, runs = _index(evidence['sources'], 'sources'), _index(evidence['runs'], 'runs')
    _require(sources.keys() <= plans.keys() and runs.keys() <= plans.keys(), 'unknown evidence caseId')
    identities = Counter((s.get('bizType'), s.get('bizId')) for s in sources.values()
                         if type(s.get('bizType')) is str and type(s.get('bizId')) is str)
    run_ids = Counter(r.get('runId') for r in runs.values() if type(r.get('runId')) is str)
    rows, created, timings = [], set(), []
    for cid, case in plans.items():
        row = {'caseId': cid, 'sourceCreated': False, 'analysable': False, 'analysisOutcome': 'UNVERIFIED',
               'observedStatus': None, 'executionClosed': False, 'timingIncluded': False, 'blockers': []}
        rows.append(row)
        source, run = sources.get(cid), runs.get(cid)
        try:
            _require(source is not None, 'missing source')
            local, work, reads = _source(case, wants[cid], source, scope)
        except EVIDENCE_ERRORS:
            row['blockers'].append('SOURCE_UNVERIFIED')
            continue
        identity = source['bizType'], source['bizId']
        created.add(identity)
        row['sourceCreated'] = True
        if identities[identity] > 1:
            row['blockers'].append('DUPLICATE_BUSINESS_SOURCE')
            continue
        try:
            _require(work['woStatus'] in ('RELEASED', 'IN_PROGRESS'), 'work not released')
            _fact_proofs(reads, local, work)
            row['analysable'] = True
        except EVIDENCE_ERRORS:
            row['blockers'].append('FACTS_OR_RELEASE_UNVERIFIED')
        try:
            _require(run is not None, 'missing run')
            observed = _run_row(run, local, scope)
            _require(run_ids[run['runId']] == 1, 'run reused')
        except EVIDENCE_ERRORS:
            row['blockers'].append('RUN_SCOPE_OR_ID_UNVERIFIED')
            continue
        status = observed.get('status')
        row['observedStatus'] = status if type(status) is str and status in FINISHED | PENDING else 'UNKNOWN'
        timing = run.get('elapsedMs')
        if type(timing) in (int, float) and isfinite(timing) and timing > 0:
            timings.append(timing)
            row['timingIncluded'] = True
        else:
            row['blockers'].append('TIMING_MISSING_OR_INVALID')
        if not row['analysable']:
            continue
        try:
            _data(run.get('evidence'), list)
            _strict_analysis_documents(run, observed)
            _assert_case(local, run['detail'], run['evidence'], run['timeline'], run['runId'])
            _require(case['expectedStatus'] != 'FAILED' or 'execution' not in run, 'refusal has execution evidence')
            row['analysisOutcome'] = 'EXPECTED_REJECTION' if case['expectedStatus'] == 'FAILED' else 'ANALYSIS_PASSED'
        except EVIDENCE_ERRORS:
            row['blockers'].append('ANALYSIS_RESULT_UNVERIFIED')
            continue
        if case['requiredActions']:
            try:
                row['executionClosed'] = _execution_closed(run, local)
            except EVIDENCE_ERRORS:
                row['blockers'].append('EXECUTION_UNVERIFIED')
    passed = sum(r['analysisOutcome'] == 'ANALYSIS_PASSED' for r in rows)
    rejected = sum(r['analysisOutcome'] == 'EXPECTED_REJECTION' for r in rows)
    required = sum(bool(c['requiredActions']) for c in cases)
    closed = sum(r['executionClosed'] for r in rows)
    analysis_ready = len(plans) == target_count == len(created) == passed + rejected
    official_set = target_count == 40 and plans.keys() == OFFICIAL_CASES
    signing = official_set and analysis_ready and len(timings) == target_count
    full = signing and closed == required
    ordered = sorted(timings)

    def percentile(p):
        return ordered[ceil(len(ordered) * p) - 1] if ordered else None

    return {'schemaVersion': 1, 'status': 'FULL_READY' if full else 'BLOCKED', 'tenantId': tenant_id,
            'commitSha': commit_sha, 'buildId': build_id, 'targetCases': target_count, 'plannedCases': len(plans),
            'independentCreatedSources': len(created), 'analysableCases': sum(r['analysable'] for r in rows),
            'analysisPassedCases': passed, 'expectedRejectedCases': rejected, 'executionRequiredCases': required,
            'executionClosedCases': closed, 'analysisReady': analysis_ready, 'executionReady': analysis_ready and closed == required,
            'officialFullReady': full, 'performance': {'sampleCount': len(timings), 'denominator': target_count,
                'untimedCases': target_count - len(timings), 'failedOrUnknownCases': target_count - passed - rejected,
                'p50Ms': percentile(.50), 'p95Ms': percentile(.95), 'percentileMethod': 'nearest-rank',
                'signingEligible': signing, 'slaAccepted': False}, 'cases': rows,
            'productionAcceptance': False, 'competitionSubmissionAccepted': False}


def _unique_keys(pairs):
    result = {}
    for key, value in pairs:
        _require(key not in result, 'duplicate JSON key')
        result[key] = value
    return result


def _bad_constant(_):
    raise ReadinessInputError('non-finite JSON number')


def _local_path(name):
    # UNC、URL 和文件 URL 都不是本工具的离线文件契约；拒绝后才允许打开路径。
    normalized = str(name).strip().replace('\\', '/')
    _require(not normalized.startswith('//') and not re.match(r'^[A-Za-z][A-Za-z0-9+.-]+:', normalized),
             'only local filesystem paths are accepted')
    return Path(name)


def _jsonl(path):
    with _local_path(path).open(encoding='utf-8-sig') as source:
        return [_json_object(line) for line in source if line.strip()]


def main(argv=None):
    """仅本地显式文件/stdin 输入，stdout 报告；无网络或文件写入分支。"""
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for option in ('cases', 'expected', 'evidence', 'tenant-id', 'commit-sha', 'build-id'):
        parser.add_argument('--' + option, required=True)
    parser.add_argument('--target-count', type=int, default=40)
    args = parser.parse_args(argv)
    try:
        paths = [_local_path(args.cases), _local_path(args.expected)]
        evidence_path = None if args.evidence == '-' else _local_path(args.evidence)
        cases, expected = _jsonl(paths[0]), _jsonl(paths[1])
        text = sys.stdin.read() if evidence_path is None else evidence_path.read_text(encoding='utf-8-sig')
        report = build_readiness(cases, expected, _json_object(text), tenant_id=args.tenant_id,
                                commit_sha=args.commit_sha, build_id=args.build_id, target_count=args.target_count)
    except (OSError, UnicodeError) + EVIDENCE_ERRORS:
        report = {'status': 'INPUT_ERROR', 'officialFullReady': False,
                  'performance': {'signingEligible': False, 'slaAccepted': False},
                  'productionAcceptance': False, 'competitionSubmissionAccepted': False,
                  'error': 'Explicit local files or input contract invalid; no fallback or external access.'}
    print(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))
    return 0 if report['officialFullReady'] else 2


if __name__ == '__main__':
    raise SystemExit(main())
