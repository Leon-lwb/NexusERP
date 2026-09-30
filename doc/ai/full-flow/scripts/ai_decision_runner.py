"""仅用于本地的工业决策验收，校验失败即关闭通路（绝不向 ERP 或 AI 表写入种子数据）。

历史 40 个案例的预期值是契约，并非本地 ERP 中存在
40 个匹配工单的证据。映射后的案例只有在独立读取按租户和工单限定的实时 API 数据、
逐项读回并核验所有声明的历史事实后，才可发送 POST。
若已部署 API 未提供这些事实，该案例仍为 UNVERIFIED_FACTS。
禁止自动审批、修改来源事实，或用重复工单替代其他案例。
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
import json
from math import ceil, isfinite
import os
from pathlib import Path
import re
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

HERE = Path(__file__).resolve().parent
BENCHMARK = HERE.parents[1] / 'benchmark' / 'delivery-risk'
DIGITS = re.compile(r'[1-9][0-9]*\Z')
SAFE_PATH = re.compile(r'/[A-Za-z0-9_./-]+\Z')
CODE = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}\Z')
FORBIDDEN_CODE = re.compile(r'(?i)(?:DEMO|TEST|MOCK)[_-]')
FINISHED = frozenset({'WAITING_APPROVAL', 'SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED', 'SUPERSEDED'})
PENDING = frozenset({'CREATED', 'RUNNING', 'APPROVED', 'EXECUTING', 'ROLLING_BACK'})


class ContractError(ValueError):
    """输入、实时来源或结果违反契约；消息不包含响应体。"""


class ApiFailure(RuntimeError):
    """HTTP 或传输错误；不包含凭证、查询值或服务端响应体。"""


class DuplicateRunError(ContractError):
    """服务端为两个不同工单返回了同一个运行 ID。"""


def strict_id(value, label='ID'):
    # 业务 ID 禁止使用 JavaScript Number，包括所谓安全整数。
    if type(value) is not str or not DIGITS.fullmatch(value):
        raise ContractError(f'{label} must be a nonzero decimal STRING')
    return value


def _code(value):
    if type(value) is not str or not CODE.fullmatch(value) or FORBIDDEN_CODE.search(value):
        raise ContractError('business code is missing, malformed, or a placeholder')
    return value


@dataclass(frozen=True)
class LocalDecisionCase:
    case_id: str
    work_order_id: str
    work_order_code: str
    tenant_id: str
    facts: dict
    expected_status: str
    expected_failure_code: str | None
    golden: dict
    measurements: dict


def _read_jsonl(path):
    with Path(path).open(encoding='utf-8') as source:
        return [json.loads(line) for line in source if line.strip()]


def load_contracts(cases_path=BENCHMARK / 'cases.jsonl',
                   expected_path=BENCHMARK / 'expected-results.jsonl'):
    cases, expected = _read_jsonl(cases_path), _read_jsonl(expected_path)
    if len(cases) != 40 or len(expected) != 40:
        raise ContractError('golden fixture must contain exactly 40 cases and 40 expectations')
    result = []
    for i, (case, want) in enumerate(zip(cases, expected), 1):
        cid = f'DELIVERY-{i:03d}'
        if not isinstance(case, dict) or not isinstance(want, dict) or case.get('caseId') != cid or want.get('caseId') != cid:
            raise ContractError('case IDs must be unique, consecutive, and match the golden file')
        keys = ('bizId', 'bizCode', 'expectedStatus', 'expectedRiskLevel', 'expectedConfidence',
                'expectedRootCauses', 'recommendedScenarioCode', 'requiredActions', 'forbiddenActions')
        if any(case.get(key) != want.get(key) for key in keys):
            raise ContractError(f'{cid}: historical expectation differs from golden file')
        if case.get('bizType') != 'WORK_ORDER' or not isinstance(case.get('facts'), dict) or not case['facts']:
            raise ContractError(f'{cid}: missing historical work-order facts')
        if case.get('expectedStatus') not in ('SUCCEEDED', 'WAITING_APPROVAL', 'FAILED'):
            raise ContractError(f'{cid}: invalid golden status')
        if case['expectedStatus'] == 'FAILED':
            if (case.get('expectedRiskLevel') is not None or case.get('recommendedScenarioCode') is not None
                or case.get('requiredActions') or not case.get('expectedFailureCode')
                or case.get('expectedFailureCode') != want.get('expectedFailureCode')):
                raise ContractError(f'{cid}: failed golden case must not publish a risk, scenario, or action')
        elif (not case.get('expectedRiskLevel') or case.get('expectedFailureCode')):
            raise ContractError(f'{cid}: incomplete success golden')
        result.append(LocalDecisionCase(cid, '', '', '', dict(case['facts']), case['expectedStatus'],
                                        case.get('expectedFailureCode'), dict(want), {}))
    return result


def map_cases(contracts, manifest, mapping):
    """只接受明确的 1:1 分配；被 Git 忽略的清单不代表任何案例事实。"""
    if not isinstance(manifest, dict) or not isinstance(manifest.get('records'), list):
        raise ContractError('invalid local manifest')
    tenant = manifest.get('tenantId')
    if type(tenant) is not str or not re.fullmatch(r'[A-Za-z0-9_-]{1,20}', tenant):
        raise ContractError('invalid manifest tenant')
    if not isinstance(mapping, list) or len(mapping) > 40:
        raise ContractError('invalid local case mapping')
    keys = {case.case_id: case for case in contracts}
    if len(keys) != 40:
        raise ContractError('historical case set is incomplete')
    records = {}
    for row in manifest['records']:
        if not isinstance(row, dict) or row.get('table') != 'prd_work_order' or row.get('domain') != 'erp':
            continue
        entity_id = strict_id(row.get('entityId'), 'manifest work-order ID')
        code = _code(row.get('businessCode'))
        if row.get('tenantId') != tenant or entity_id in records:
            raise ContractError('cross-tenant or repeated manifest work order')
        records[entity_id] = code
    assigned, seen_work = {}, set()
    for item in mapping:
        if not isinstance(item, dict) or item.get('caseId') not in keys or item['caseId'] in assigned:
            raise ContractError('unknown or duplicate case assignment')
        cid = item['caseId']
        wid, code = strict_id(item.get('workOrderId'), 'work-order ID'), _code(item.get('workOrderCode'))
        if item.get('tenantId') != tenant or wid in seen_work or records.get(wid) != code:
            raise ContractError('mapping has wrong tenant, duplicate work order, or unregistered code')
        golden = keys[cid]
        # 用户提供的事实不能替换历史基准预期，也不能更改其标注。
        if 'facts' in item and item['facts'] != golden.facts:
            raise ContractError('mapping facts differ from historical fixture')
        measurements = item.get('measurements', {})
        if not isinstance(measurements, dict):
            raise ContractError('invalid fact measurements')
        seen_work.add(wid)
        assigned[cid] = LocalDecisionCase(cid, wid, code, tenant, golden.facts,
                                            golden.expected_status, golden.expected_failure_code,
                                            golden.golden, measurements)
    return [assigned[case.case_id] for case in contracts if case.case_id in assigned]


def propose_mapping(contracts, manifest):
    """不能从清单 ID 或历史 DEMO ID 推导任何业务事实。

    必须由外部提供明确的案例与工单对应关系，
    并附上实时事实来源描述；此辅助函数绝不自动分配案例。
    """
    map_cases(contracts, manifest, [])  # 拒绝格式错误或属于其他租户的清单。
    return []


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


class DecisionClient:
    """仅允许回环地址，禁用重定向和代理；JWT/clientid 只从进程环境读取。"""
    def __init__(self, base_url='http://127.0.0.1:8080', *, opener=None):
        parsed = urlsplit(base_url)
        if (parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost')
            or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/')):
            raise ValueError('Decision API must use direct local loopback HTTP')
        self._base = base_url.rstrip('/')
        self._opener = opener or build_opener(ProxyHandler({}), _NoRedirect()).open

    def _request(self, method, path, body=None):
        if (method not in ('GET', 'POST') or not isinstance(path, str) or not SAFE_PATH.fullmatch(path)
            or '//' in path or any(part in ('.', '..') for part in path.split('/'))):
            raise ValueError('invalid API method or path')
        # 禁止通过任意路径调用会修改数据的方法。
        if method == 'POST' and not re.fullmatch(
            r'/ai/decision/(?:delivery-risk/run|run/[1-9][0-9]*/(?:approve|reject|recalculate)|action/[1-9][0-9]*/retry)', path):
            raise ValueError('mutation endpoint not allowlisted')
        token, client_id = os.environ.get('NEXUS_FULL_FLOW_TOKEN'), os.environ.get('NEXUS_FULL_FLOW_CLIENT_ID')
        if not token or not client_id:
            raise ContractError('authentication env is missing (no credential is printed)')
        headers = {'Authorization': 'Bearer ' + token, 'clientid': client_id, 'Accept': 'application/json'}
        tenant = os.environ.get('NEXUS_FULL_FLOW_TENANT_ID')
        if tenant:
            if not re.fullmatch(r'[A-Za-z0-9_-]{1,20}', tenant):
                raise ContractError('invalid tenant header')
            headers['tenant-id'] = tenant
        payload = None
        if method == 'POST':
            payload = json.dumps(body if body is not None else {}, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        return Request(self._base + path, data=payload, headers=headers, method=method)

    def _send(self, method, path, body=None):
        request = self._request(method, path, body)
        try:
            with self._opener(request, timeout=15) as response:
                if response.status != 200:
                    raise ApiFailure(f'{method}: unexpected HTTP status')
                raw = response.read(2_000_001)
        except HTTPError as error:
            raise ApiFailure(f'{method}: HTTP {error.code}') from None
        except (URLError, TimeoutError, OSError):
            raise ApiFailure(f'{method}: local service unavailable') from None
        if len(raw) > 2_000_000:
            raise ApiFailure(f'{method}: oversized API response')
        try:
            document = json.loads(raw)
        except (ValueError, UnicodeError):
            raise ApiFailure(f'{method}: non-JSON response') from None
        if not isinstance(document, dict) or document.get('code') != 200:
            # 不读取服务端错误响应体，因为其中可能包含凭证片段或个人信息。
            raise ApiFailure(f'{method}: business or permission failure')
        return document

    def get(self, path):
        return self._send('GET', path)

    def post(self, path, body=None):
        return self._send('POST', path, body)

    def export(self, run_id):
        """可选的只读导出；调用方不得记录 Markdown 内容，因为其中包含业务事实。"""
        run_id = strict_id(run_id)
        request = self._request('GET', f'/ai/decision/run/{run_id}/export')
        try:
            with self._opener(request, timeout=15) as response:
                if response.status != 200:
                    raise ApiFailure('export: unexpected HTTP status')
                content = response.read(500_001)
        except (HTTPError, URLError, TimeoutError, OSError):
            raise ApiFailure('export: local service unavailable') from None
        if len(content) > 500_000:
            raise ApiFailure('export: oversized markdown')
        return content.decode('utf-8')



def _data(response, label):
    value = response.get('data') if isinstance(response, dict) else None
    if not isinstance(value, dict):
        raise ContractError(f'{label}: malformed data')
    return value


def _match(actual, expected):
    if type(expected) is bool:
        return type(actual) is bool and actual == expected
    if type(expected) in (int, float) and type(actual) in (int, float, str):
        try:
            return Decimal(str(actual)) == Decimal(str(expected))
        except InvalidOperation:
            return False
    return type(actual) is type(expected) and actual == expected


def _fact_proofs(client, case, work):
    """除非每项基准指标都与实时读回结果一致，否则拒绝用户编写的断言。

    有效度量为 {path,field}；field 必须指向由 woId/workOrderId 和 tenantId
    绑定的文档中的对应基准指标。对于派生工业指标，
    若没有相应的独立实时 API 字段，就不能通过此守卫。
    """
    if set(case.measurements) != set(case.facts):
        raise ContractError('missing independently verifiable fact measurements')
    cache = {f'/erp/prd/work/{case.work_order_id}': work}
    for name, expected in case.facts.items():
        proof = case.measurements[name]
        if not isinstance(proof, dict) or set(proof) != {'path', 'field'} or proof['field'] != name:
            raise ContractError('invalid fact-source selector')
        path = proof['path']
        if (type(path) is not str or not SAFE_PATH.fullmatch(path)
            or not path.startswith(('/erp/', '/mes/', '/iot/')) or '..' in path.split('/') or '//' in path):
            raise ContractError('fact proof must reference a read-only business API')
        if path.startswith('/erp/prd/work/') and path != f'/erp/prd/work/{case.work_order_id}':
            raise ContractError('fact source must be the mapped work order')
        if path not in cache:
            cache[path] = _data(client.get(path), 'fact source')
        source = cache[path]
        if (source.get('tenantId', case.tenant_id if path == f'/erp/prd/work/{case.work_order_id}' else None) != case.tenant_id
            or not any(source.get(field) == case.work_order_id for field in ('woId', 'workOrderId'))
            or not _match(source.get(name), expected)):
            raise ContractError('live business fact differs from historical expectation')


def _assert_case(case, detail, evidence_doc, timeline_doc, run_id):
    view = _data(detail, 'decision detail')
    row, actions, detail_evidence = view.get('run'), view.get('actions'), view.get('evidence')
    if not isinstance(row, dict) or not isinstance(actions, list) or not isinstance(detail_evidence, list):
        raise ContractError('decision detail incomplete')
    for field, expected in (('id', run_id), ('tenantId', case.tenant_id), ('bizType', 'WORK_ORDER'),
                            ('bizId', case.work_order_id), ('bizCode', case.work_order_code)):
        if row.get(field) != expected or (field in ('id', 'bizId') and type(row.get(field)) is not str):
            raise ContractError('decision tenant/business/run mismatch')
    status = row.get('status')
    if status != case.expected_status:
        raise ContractError('decision status differs from golden')
    evidence, timeline = evidence_doc.get('data'), timeline_doc.get('data')
    if not isinstance(evidence, list) or not isinstance(timeline, list) or detail_evidence != evidence:
        raise ContractError('evidence or timeline read-back differs from run detail')
    action_types = []
    for action in actions:
        if not isinstance(action, dict) or action.get('tenantId') != case.tenant_id or action.get('runId') != run_id:
            raise ContractError('action tenant/run mismatch')
        strict_id(action.get('id'), 'action ID')
        action_types.append(action.get('actionType'))
        if action.get('executionStatus') != 'NOT_STARTED':
            raise ContractError('ordinary case regression must not execute any action')
    if status == 'FAILED':
        if (row.get('errorCode') != case.expected_failure_code or row.get('riskLevel') is not None
            or row.get('recommendedScenarioCode') is not None or actions or row.get('resultJson')):
            raise ContractError('failed case published a result or wrong refusal code')
        return {'status': status, 'errorCode': row['errorCode']}
    if (row.get('riskLevel') != case.golden['expectedRiskLevel']
        or row.get('confidenceLevel') != case.golden['expectedConfidence']
        or row.get('recommendedScenarioCode') != case.golden['recommendedScenarioCode']
        or not isinstance(row.get('inputSnapshotHash'), str) or not row['inputSnapshotHash']
        or type(row.get('analysisFlowInstanceId')) is not str):
        raise ContractError('risk, confidence, recommendation, or snapshot/Flow identity mismatch')
    try:
        result = json.loads(row['resultJson'])
    except (KeyError, TypeError, ValueError):
        raise ContractError('decision result is missing or invalid') from None
    scenarios = result.get('scenarios') if isinstance(result, dict) else None
    try:
        risk_score = Decimal(str(result.get('riskScore')))
        saved_score = Decimal(str(row.get('riskScore')))
    except (InvalidOperation, ValueError, TypeError, AttributeError):
        raise ContractError('invalid deterministic risk score') from None
    if (not risk_score.is_finite() or risk_score != saved_score or not isinstance(scenarios, list) or not scenarios
        or result.get('rootCauseCodes') != case.golden['expectedRootCauses']
        or result.get('recommendedScenarioCode') != case.golden['recommendedScenarioCode']
        or result.get('riskLevel') != case.golden['expectedRiskLevel']
        or not isinstance(result.get('narrative'), dict)
        or not isinstance(result['narrative'].get('executiveSummary'), str)
        or not result['narrative']['executiveSummary'].strip()
        or any(not isinstance(item, dict) or not item.get('inputHash')
               or 'snapshot:' + row['inputSnapshotHash'] not in (item.get('evidenceKeys') or [])
               for item in scenarios)
        or (row['recommendedScenarioCode'] is not None and not any(
               item.get('scenarioCode') == row['recommendedScenarioCode'] for item in scenarios))):
        raise ContractError('decision result is incomplete or disagrees with deterministic golden')
    if (set(action_types) != set(case.golden['requiredActions']) or len(action_types) != len(set(action_types))
        or set(action_types) & set(case.golden['forbiddenActions'])):
        raise ContractError('decision action types contradict golden policy')
    if not evidence or not any(t.get('type') == 'NODE' and t.get('time') is not None for t in timeline if isinstance(t, dict)):
        raise ContractError('persisted evidence or Flow timeline missing')
    keys = set()
    for item in evidence:
        if not isinstance(item, dict) or item.get('tenantId') != case.tenant_id or item.get('runId') != run_id:
            raise ContractError('evidence tenant/run mismatch')
        key = item.get('evidenceKey')
        if not isinstance(key, str) or not key or key in keys or not item.get('sourceDomain') or not item.get('sourceType'):
            raise ContractError('missing/duplicate/source-less evidence')
        if item.get('sourceId') is not None:
            strict_id(item['sourceId'], 'evidence source ID')
        if item.get('valueType') == 'NUMBER':
            try:
                numeric = Decimal(str(item.get('metricValue')))
            except (InvalidOperation, ValueError, TypeError):
                raise ContractError('numeric evidence value is invalid') from None
            if (not numeric.is_finite() or item.get('verifiable') != 'Y'
                or (item.get('sourceDomain') != 'DERIVED' and not item.get('dataVersion'))
                or not item.get('metricCode')
                or item.get('sourceId') is None and not item.get('sourceCode')):
                raise ContractError('number has no independently traceable evidence')
        keys.add(key)
    for item in evidence:
        if item.get('sourceDomain') == 'DERIVED' and item.get('valueType') == 'NUMBER':
            try:
                parents = json.loads(item.get('derivedFromJson') or '[]')
            except ValueError:
                raise ContractError('invalid derived evidence lineage') from None
            if not isinstance(parents, list) or not parents or not set(parents) <= keys:
                raise ContractError('derived numeric fact missing lineage')
    result_keys = {e.get('evidenceKey') for e in result.get('evidence', []) if isinstance(e, dict)}
    if result_keys and not result_keys <= keys:
        raise ContractError('result cites non-persisted evidence')
    return {'status': status, 'riskLevel': row['riskLevel'], 'confidence': row['confidenceLevel'],
            'rootCauses': list(result['rootCauseCodes']), 'scenario': row['recommendedScenarioCode'],
            'actions': action_types, 'evidenceCount': len(evidence), 'flowNodes':
            sum(1 for node in timeline if isinstance(node, dict) and node.get('type') == 'NODE')}


class DecisionRunner:
    def __init__(self, client, contracts, manifest, mapping, *, clock=time.monotonic, sleep=time.sleep):
        self.client, self.contracts, self.manifest = client, contracts, manifest
        self.mapping = {case.case_id: case for case in map_cases(contracts, manifest, mapping)}
        self.clock, self.sleep = clock, sleep

    def _identity(self):
        identity = _data(self.client.get('/system/user/getInfo'), 'authenticated identity')
        user = identity.get('user')
        if (not isinstance(user, dict) or user.get('tenantId') != self.manifest['tenantId']
            or type(user.get('userId')) is not str):
            raise ContractError('authenticated tenant/user mismatch')
        strict_id(user['userId'], 'authenticated user ID')

    def _work_order(self, case):
        work = _data(self.client.get(f'/erp/prd/work/{case.work_order_id}'), 'ERP work order')
        if (type(work.get('woId')) is not str or work['woId'] != case.work_order_id
            or work.get('woNo') != case.work_order_code
            or work.get('tenantId', case.tenant_id) != case.tenant_id
            or work.get('woStatus') not in ('RELEASED', 'IN_PROGRESS')):
            raise ContractError('work-order tenant, code, ID or production state is unverified')
        return work

    def _execute(self, case, timeout, on_accepted):
        started = self.clock()
        accepted = _data(self.client.post('/ai/decision/delivery-risk/run',
                                          {'bizType': 'WORK_ORDER', 'bizId': case.work_order_id,
                                           'horizonDays': 30}), 'decision acceptance')
        run_id = strict_id(accepted.get('runId'), 'decision run ID')
        on_accepted(run_id)  # POST 未返回可接受的运行 ID 时，不能计为已执行案例。
        if accepted.get('status') not in PENDING | FINISHED:
            raise ContractError('unsupported accepted run status')
        path = f'/ai/decision/run/{run_id}'
        while True:
            detail = self.client.get(path)
            view = _data(detail, 'decision detail')
            row = view.get('run')
            status = row.get('status') if isinstance(row, dict) else None
            if status in FINISHED:
                break
            if status not in PENDING:
                raise ContractError('invalid Decision status (SSE event is not a run state)')
            if self.clock() - started >= timeout:
                raise ApiFailure('decision timed out before an admissible result')
            self.sleep(min(2, max(.1, timeout - (self.clock() - started))))
        evidence = self.client.get(path + '/evidence')
        timeline = self.client.get(path + '/timeline')
        outcome = _assert_case(case, detail, evidence, timeline, run_id)
        outcome['elapsedMs'] = round((self.clock() - started) * 1000)
        # 公开报告刻意省略业务 ID 和运行 ID；这些 ID 仅保留在内存中。
        return outcome

    def _control_view(self, case_id, run_id):
        """重新读回身份、ERP 来源及运行归属前，禁止 CONTROL 写入。"""
        if case_id not in self.mapping:
            raise ContractError('control requires an assigned local business case')
        case = self.mapping[case_id]
        run_id = strict_id(run_id, 'control run ID')
        self._identity()
        self._work_order(case)
        view = _data(self.client.get(f'/ai/decision/run/{run_id}'), 'control run')
        row, actions = view.get('run'), view.get('actions')
        if (not isinstance(row, dict) or not isinstance(actions, list)
            or row.get('id') != run_id or row.get('tenantId') != case.tenant_id
            or row.get('bizType') != 'WORK_ORDER' or row.get('bizId') != case.work_order_id
            or row.get('bizCode') != case.work_order_code):
            raise ContractError('control run is not bound to the mapped tenant/business')
        if any(not isinstance(action, dict) or action.get('tenantId') != case.tenant_id
               or action.get('runId') != run_id for action in actions):
            raise ContractError('control action tenant/run mismatch')
        return row, actions

    def control_approve(self, case_id, run_id, version, approved_ids, expected_hash,
                        *, rejected_ids=None, confirm=False):
        """仅限 CONTROL；每个动作都须有明确决策，run() 绝不调用此函数。"""
        if not confirm or type(version) is not int or version < 0 or not isinstance(expected_hash, str) or not expected_hash:
            raise ContractError('approval needs explicit consent, run version and snapshot hash')
        if not isinstance(approved_ids, list) or not approved_ids or not isinstance(rejected_ids, list):
            raise ContractError('approval requires explicit approved and rejected action lists')
        ids = [strict_id(value, 'action ID') for value in approved_ids]
        denied = [strict_id(value, 'action ID') for value in rejected_ids]
        if len(set(ids + denied)) != len(ids + denied):
            raise ContractError('duplicate or overlapping action IDs')
        row, actions = self._control_view(case_id, run_id)
        valid = {action.get('id') for action in actions
                 if action.get('executionStatus') == 'NOT_STARTED'
                 and action.get('approvalStatus') in ('PROPOSED', 'PENDING_APPROVAL')}
        if (row.get('status') != 'WAITING_APPROVAL' or row.get('version') != version
            or row.get('inputSnapshotHash') != expected_hash
            or len(valid) != len(actions) or set(ids + denied) != valid):
            raise ContractError('approval is stale, wrong-version, or incomplete action selection')
        return self.client.post(f'/ai/decision/run/{run_id}/approve',
                                {'runVersion': version, 'approvedActionIds': ids,
                                 'rejectedActionIds': denied, 'expectedSnapshotHash': expected_hash})

    def control_reject(self, case_id, run_id, reason, *, confirm=False):
        if not confirm or not isinstance(reason, str) or not 1 <= len(reason.strip()) <= 500:
            raise ContractError('rejection needs explicit consent and a reason')
        row, _ = self._control_view(case_id, run_id)
        if row.get('status') != 'WAITING_APPROVAL':
            raise ContractError('rejection requires a current waiting run')
        return self.client.post(f'/ai/decision/run/{run_id}/reject', {'reason': reason.strip()})

    def control_recalculate(self, case_id, run_id, *, confirm=False):
        if not confirm:
            raise ContractError('recalculation needs explicit consent')
        row, _ = self._control_view(case_id, run_id)
        if row.get('status') != 'STALE':
            raise ContractError('recalculation requires an explicitly stale run')
        return self.client.post(f'/ai/decision/run/{run_id}/recalculate', {})

    def control_retry(self, case_id, run_id, action_id, *, confirm=False):
        if not confirm:
            raise ContractError('retry needs explicit consent')
        action_id = strict_id(action_id, 'action ID')
        row, actions = self._control_view(case_id, run_id)
        if (row.get('status') not in ('FAILED', 'PARTIAL_SUCCESS')
            or not any(action.get('id') == action_id and action.get('executionStatus') == 'FAILED'
                       for action in actions)):
            raise ContractError('retry requires a failed action of the verified run')
        return self.client.post(f'/ai/decision/action/{action_id}/retry', {})

    def export(self, case_id, run_id):
        self._control_view(case_id, run_id)
        return self.client.export(run_id)

    def run(self, *, execute=False, timeout=120):
        if (type(execute) is not bool or type(timeout) not in (int, float)
            or not isfinite(timeout) or not 0 <= timeout <= 3600):
            raise ValueError('invalid execution arguments')
        results = []
        seen_runs = set()

        def on_accepted(run_id):
            if run_id in seen_runs:
                raise DuplicateRunError('one run ID cannot represent two distinct cases')
            seen_runs.add(run_id)

        for case in self.contracts:
            mapped = self.mapping.get(case.case_id)
            if not mapped:
                results.append({'caseId': case.case_id, 'status': 'UNMAPPED'})
                continue
            if not execute:
                results.append({'caseId': case.case_id, 'status': 'READ_ONLY'})
                continue
            try:
                self._identity()
            except (ContractError, ApiFailure):
                results.append({'caseId': case.case_id, 'status': 'AUTH_UNAVAILABLE'})
                continue
            try:
                work = self._work_order(mapped)
            except ContractError:
                results.append({'caseId': case.case_id, 'status': 'UNVERIFIED_WORK_ORDER'})
                continue
            except ApiFailure:
                results.append({'caseId': case.case_id, 'status': 'PRECHECK_API_FAILURE'})
                continue
            try:
                _fact_proofs(self.client, mapped, work)
            except ContractError:
                results.append({'caseId': case.case_id, 'status': 'UNVERIFIED_FACTS'})
                continue
            except ApiFailure:
                results.append({'caseId': case.case_id, 'status': 'PRECHECK_API_FAILURE'})
                continue
            # 只有通过只读事实核验后，才可进入 POST 边界。
            # 此后即使断言失败，也仍代表已经真实发起了一次运行。
            accepted_ids = []
            try:
                def record_accepted(run_id):
                    on_accepted(run_id)
                    accepted_ids.append(run_id)

                outcome = self._execute(mapped, timeout, record_accepted)
                results.append({'caseId': case.case_id, **outcome, 'observedStatus': outcome['status'],
                                'status': 'PASS', 'accepted': True})
            except DuplicateRunError:
                results.append({'caseId': case.case_id, 'status': 'DUPLICATE_RUN_ID', 'accepted': False})
            except ContractError:
                results.append({'caseId': case.case_id, 'status': 'ASSERTION_FAILED', 'accepted': bool(accepted_ids)})
            except ApiFailure:
                results.append({'caseId': case.case_id, 'status': 'API_FAILURE', 'accepted': bool(accepted_ids)})
        passed = [entry['elapsedMs'] for entry in results if entry['status'] == 'PASS']

        def percentile(values, p):
            if not values:
                return None
            ordered = sorted(values)
            return ordered[min(len(ordered) - 1, ceil(len(ordered) * p) - 1)]

        return {'total': len(self.contracts), 'mapped': len(self.mapping),
                'unmapped': sum(x['status'] == 'UNMAPPED' for x in results),
                'postAttempts': sum(x['status'] in ('PASS', 'ASSERTION_FAILED', 'API_FAILURE', 'DUPLICATE_RUN_ID') for x in results),
                'executed': sum(x.get('accepted', False) for x in results),
                'passed': len(passed), 'failed': sum(x['status'] in ('ASSERTION_FAILED', 'API_FAILURE', 'DUPLICATE_RUN_ID') for x in results),
                'p50Ms': percentile(passed, .50), 'p95Ms': percentile(passed, .95),
                'cases': results, 'controls': 'NOT_RUN_SEPARATE_FROM_GOLDEN'}


def main(argv=None):
    parser = argparse.ArgumentParser(description='Read-only by default; writes require --execute AND independent fact proofs')
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--mapping', type=Path, help='Explicit one-to-one case assignment; never inferred from historical DEMO IDs')
    parser.add_argument('--cases', type=Path, default=BENCHMARK / 'cases.jsonl')
    parser.add_argument('--expected', type=Path, default=BENCHMARK / 'expected-results.jsonl')
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--execute', action='store_true', help='POST only for independently verified cases; no approval')
    parser.add_argument('--timeout', type=float, default=120)
    parser.add_argument('--output', type=Path, help='Sanitized JSON results in ignored .codex/full-flow-20260928 only')
    args = parser.parse_args(argv)
    try:
        if args.output:
            ignored = HERE.parents[3] / '.codex' / 'full-flow-20260928'
            if args.output.resolve().parent != ignored.resolve() or args.output.suffix != '.json':
                raise ContractError('output must be a JSON file inside ignored .codex/full-flow-20260928')
        contracts = load_contracts(args.cases, args.expected)
        manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
        mapping = json.loads(args.mapping.read_text(encoding='utf-8')) if args.mapping else []
        report = DecisionRunner(DecisionClient(args.base_url), contracts, manifest, mapping).run(
            execute=args.execute, timeout=args.timeout)
        text = json.dumps(report, ensure_ascii=False, indent=2)
        if args.output:
            args.output.write_text(text + '\n', encoding='utf-8')
        print(text)
        return 0 if not args.execute or report['passed'] == 40 else 2
    except (OSError, ValueError, ApiFailure, KeyError):
        print('Decision runner stopped: local input, contract, or service unavailable (details suppressed)')
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
