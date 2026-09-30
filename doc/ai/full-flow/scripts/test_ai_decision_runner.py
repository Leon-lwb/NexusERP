"""本地工业决策执行器的离线预期失败契约；校验失败即关闭通路。"""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ai_decision_runner', HERE / 'ai_decision_runner.py')
runner = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = runner
spec.loader.exec_module(runner)


class FakeClient:
    def __init__(self, tenant='000000', work=None, detail=None, evidence=None, timeline=None):
        self.tenant = tenant
        self.work = work or {}
        self.detail = detail
        self.evidence = evidence
        self.timeline = timeline
        self.calls = []

    def get(self, path):
        self.calls.append(('GET', path))
        if path == '/system/user/getInfo':
            return {'code': 200, 'data': {'user': {'tenantId': self.tenant, 'userId': '9007199254748001'}}}
        if path.startswith('/erp/prd/work/'):
            return {'code': 200, 'data': self.work}
        if path.endswith('/evidence'):
            return {'code': 200, 'data': self.evidence}
        if path.endswith('/timeline'):
            return {'code': 200, 'data': self.timeline}
        if path.startswith('/ai/decision/run/'):
            return {'code': 200, 'data': self.detail}
        raise AssertionError('unexpected read: ' + path)

    def post(self, path, body):
        self.calls.append(('POST', path, body))
        return {'code': 200, 'data': {'runId': '9007199254749001', 'status': 'RUNNING'}}


class DecisionRunnerOffline(unittest.TestCase):
    def setUp(self):
        self.here = HERE.parents[1] / 'benchmark' / 'delivery-risk'
        self.cases = runner.load_contracts(self.here / 'cases.jsonl', self.here / 'expected-results.jsonl')
        self.manifest = {'tenantId': '000000', 'records': [{'tenantId': '000000', 'domain': 'erp',
            'table': 'prd_work_order', 'entityId': '9007199254747001', 'businessCode': 'WO202609290001'}]}
        self.mapped = [{'caseId': 'DELIVERY-001', 'workOrderId': '9007199254747001',
                        'workOrderCode': 'WO202609290001', 'tenantId': '000000'}]
        self.work = {'woId': '9007199254747001', 'woNo': 'WO202609290001',
                     'tenantId': '000000', 'woStatus': 'RELEASED'}

    def test_load_forty_historical_cases_not_demo_business_mapping(self):
        self.assertEqual(len(self.cases), 40)
        self.assertEqual(self.cases[0].case_id, 'DELIVERY-001')
        self.assertEqual(self.cases[-1].expected_failure_code, 'CAPACITY_EVIDENCE_MISSING')

    def test_reject_missing_duplicate_or_corrupt_golden(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / 'cases.jsonl'
            golden = Path(tmp) / 'expected.jsonl'
            rows = [json.loads(line) for line in (self.here / 'cases.jsonl').read_text(encoding='utf-8').splitlines()]
            expected = [json.loads(line) for line in (self.here / 'expected-results.jsonl').read_text(encoding='utf-8').splitlines()]
            for broken in (rows[:-1], rows[:-1] + [rows[0]]):
                source.write_text('\n'.join(map(json.dumps, broken)), encoding='utf-8')
                golden.write_text('\n'.join(map(json.dumps, expected)), encoding='utf-8')
                with self.assertRaises(ValueError):
                    runner.load_contracts(source, golden)
            rows[0]['expectedRiskLevel'] = 'CRITICAL'
            source.write_text('\n'.join(map(json.dumps, rows)), encoding='utf-8')
            with self.assertRaises(ValueError):
                runner.load_contracts(source, golden)

    def test_failed_golden_must_have_no_risk_recommendation_or_actions(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / 'cases.jsonl'
            golden = Path(tmp) / 'expected.jsonl'
            rows = [json.loads(line) for line in (self.here / 'cases.jsonl').read_text(encoding='utf-8').splitlines()]
            golden.write_text((self.here / 'expected-results.jsonl').read_text(encoding='utf-8'), encoding='utf-8')
            for key, value in [('expectedRiskLevel', 'HIGH'), ('recommendedScenarioCode', 'APS_CR'),
                               ('requiredActions', ['AUTO_APPROVE'])]:
                edited = [dict(row) for row in rows]
                edited[25][key] = value
                source.write_text('\n'.join(map(json.dumps, edited)), encoding='utf-8')
                with self.subTest(key=key), self.assertRaises(ValueError):
                    runner.load_contracts(source, golden)

    def test_mapping_rejects_js_numbers_placeholder_codes_missing_and_duplicate_records(self):
        wrong = [dict(self.mapped[0], workOrderId=9007199254747001),
                 dict(self.mapped[0], workOrderCode='DEMO-AI-DECISION-001'),
                 dict(self.mapped[0], workOrderCode='WO0000'),
                 dict(self.mapped[0], tenantId='000001'),
                 dict(self.mapped[0], caseId='DELIVERY-041')]
        for entries in ([item] for item in wrong):
            with self.subTest(mapping=entries), self.assertRaises(ValueError):
                runner.map_cases(self.cases, self.manifest, entries)
        with self.assertRaises(ValueError):
            runner.map_cases(self.cases, self.manifest, self.mapped * 2)
        with self.assertRaises(ValueError):
            runner.map_cases(self.cases, self.manifest, [dict(self.mapped[0], workOrderId='9007199254747002')])

    def test_incomplete_or_reused_forty_mapping_never_qualifies_as_forty_real_cases(self):
        result = runner.map_cases(self.cases, self.manifest, self.mapped)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].case_id, 'DELIVERY-001')
        self.assertNotEqual(len(result), 40)
        with self.assertRaises(ValueError):
            runner.map_cases(self.cases, self.manifest, [dict(self.mapped[0], caseId='DELIVERY-002'), self.mapped[0]])

    def test_read_only_default_does_not_run_even_with_real_work_order(self):
        client = FakeClient(work=self.work)
        report = runner.DecisionRunner(client, self.cases, self.manifest, self.mapped).run()
        self.assertEqual(report['total'], 40)
        self.assertEqual(report['executed'], 0)
        self.assertEqual(report['mapped'], 1)
        self.assertEqual(report['passed'], 0)
        self.assertEqual(report['unmapped'], 39)
        self.assertEqual(report['p50Ms'], None)
        self.assertFalse(any(method == 'POST' for method, *_ in client.calls))

    def test_fails_closed_before_run_on_cross_tenant_numbered_id_code_or_unproven_facts(self):
        for work in (dict(self.work, tenantId='000001'), dict(self.work, woId=9007199254747001),
                     dict(self.work, woNo='WO202609290002'), dict(self.work, woStatus='CREATED')):
            client = FakeClient(work=work)
            report = runner.DecisionRunner(client, self.cases, self.manifest, self.mapped).run(execute=True)
            self.assertEqual(report['executed'], 0)
            self.assertFalse(any(method == 'POST' for method, *_ in client.calls))
        client = FakeClient(work=self.work)
        report = runner.DecisionRunner(client, self.cases, self.manifest, self.mapped).run(execute=True)
        self.assertEqual(report['executed'], 0)
        self.assertEqual(report['cases'][0]['status'], 'UNVERIFIED_FACTS')

    def test_observed_facts_must_be_read_back_from_tenant_and_work_order_scoped_api(self):
        measurements = {name: {'path': '/erp/prd/work/9007199254747001', 'field': name}
                        for name in self.cases[0].facts}
        mapped = [dict(self.mapped[0], measurements=measurements)]
        work = dict(self.work, **self.cases[0].facts)
        client = FakeClient(work=work)
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapped).run(execute=True)
        self.assertEqual(report['executed'], 1)
        self.assertEqual(sum(1 for method, *_ in client.calls if method == 'POST'), 1)
        client = FakeClient(work=dict(work, maxUtilization=90))
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapped).run(execute=True)
        self.assertEqual(report['executed'], 0)
        self.assertEqual(report['cases'][0]['status'], 'UNVERIFIED_FACTS')
        client = FakeClient(work=work)
        mapped[0]['measurements']['maxUtilization'] = {'path': '/erp/prd/work/9007199254747002', 'field': 'maxUtilization'}
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapped).run(execute=True)
        self.assertEqual(report['executed'], 0)

    def test_success_asserts_tenant_biz_snapshot_evidence_timeline_and_golden(self):
        case = self.cases[0]
        row = {'id': '9007199254749001', 'tenantId': '000000', 'bizType': 'WORK_ORDER',
               'bizId': '9007199254747001', 'bizCode': 'WO202609290001', 'status': 'SUCCEEDED',
               'riskLevel': 'LOW', 'confidenceLevel': 'HIGH', 'recommendedScenarioCode': 'BASELINE_CURRENT',
               'riskScore': '10', 'inputSnapshotHash': 'sha256-valid', 'analysisFlowInstanceId': '9007199254749999',
               'resultJson': json.dumps({'rootCauseCodes': [], 'riskScore': 10, 'riskLevel': 'LOW', 'recommendedScenarioCode': 'BASELINE_CURRENT',
                                         'scenarios': [{'scenarioCode': 'BASELINE_CURRENT', 'inputHash': 'hash',
                                                        'evidenceKeys': ['snapshot:sha256-valid']}],
                                         'actions': [], 'evidence': [{'evidenceKey': 'ERP:WORK_ORDER:x:qty'}],
                                         'narrative': {'executiveSummary': '按期交货'}})}
        evidence = [{'runId': row['id'], 'tenantId': '000000', 'evidenceKey': 'ERP:WORK_ORDER:x:qty',
                     'sourceDomain': 'ERP', 'sourceType': 'WORK_ORDER', 'sourceId': self.work['woId'],
                     'metricCode': 'planned_qty', 'metricValue': '10', 'valueType': 'NUMBER',
                     'dataVersion': 'v1', 'verifiable': 'Y'}]
        client = FakeClient(work=dict(self.work, **case.facts),
                            detail={'run': row, 'actions': [], 'evidence': evidence},
                            evidence=evidence, timeline=[{'type': 'NODE', 'code': 'context', 'status': 'SUCCEEDED', 'time': 1}])
        mapped = [dict(self.mapped[0], measurements={key: {'path': '/erp/prd/work/9007199254747001', 'field': key}
                                                    for key in case.facts})]
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapped).run(execute=True, timeout=0)
        self.assertEqual(report['passed'], 1)
        self.assertEqual(report['executed'], 1)
        self.assertEqual(report['p50Ms'] is not None, True)
        self.assertEqual(report['unmapped'], 39)
        client.detail['run']['riskLevel'] = 'HIGH'
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapped).run(execute=True, timeout=0)
        self.assertEqual(report['passed'], 0)
        self.assertEqual(report['cases'][0]['status'], 'ASSERTION_FAILED')

    def test_transport_rejects_redirects_nonlocal_urls_and_redacts_http_errors(self):
        with self.assertRaises(ValueError):
            runner.DecisionClient('http://10.152.240.20:80')
        with patch.dict(os.environ, {'NEXUS_FULL_FLOW_TOKEN': 'SENSITIVE_TOKEN',
                                     'NEXUS_FULL_FLOW_CLIENT_ID': 'SENSITIVE_CLIENT'}):
            client = runner.DecisionClient()
            req = client._request('GET', '/erp/prd/work/123')
            self.assertIn('SENSITIVE_TOKEN', req.get_header('Authorization'))
            self.assertEqual(req.get_header('Clientid'), 'SENSITIVE_CLIENT')
            with self.assertRaises(ValueError):
                client._request('POST', '/erp/prd/work/1', {})
            self.assertNotIn('SENSITIVE_TOKEN', repr(client))


    def test_unexpected_sse_ready_is_not_a_run_terminal_status(self):
        c = self.cases[0]
        mapping = [dict(self.mapped[0], measurements={k: {'path': '/erp/prd/work/9007199254747001', 'field': k}
                                                      for k in c.facts})]
        client = FakeClient(work=dict(self.work, **c.facts), detail={'run': {'status': 'AI_DECISION_READY'}})
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapping).run(execute=True, timeout=0)
        self.assertEqual(report['cases'][0]['status'], 'ASSERTION_FAILED')
        self.assertEqual(report['passed'], 0)
        self.assertEqual(report['executed'], 1)

    def test_failed_case_requires_exact_refusal_code_and_never_publishes_risk(self):
        case = self.cases[25]  # DELIVERY-026：刻意缺少工艺路线。
        row = {'id': '9007199254749001', 'tenantId': '000000', 'bizType': 'WORK_ORDER',
               'bizId': self.work['woId'], 'bizCode': self.work['woNo'], 'status': 'FAILED',
               'errorCode': case.expected_failure_code, 'riskLevel': None,
               'recommendedScenarioCode': None, 'resultJson': None}
        mapped = runner.LocalDecisionCase(case.case_id, self.work['woId'], self.work['woNo'], '000000',
                                          case.facts, 'FAILED', case.expected_failure_code, case.golden, {})
        detail = {'data': {'run': row, 'actions': [], 'evidence': []}}
        actual = runner._assert_case(mapped, detail, {'data': []}, {'data': []}, row['id'])
        self.assertEqual(actual['errorCode'], 'ROUTING_EVIDENCE_MISSING')
        for field, value in [('errorCode', 'NO_ROUTING'), ('riskLevel', 'HIGH'), ('resultJson', '{}')]:
            bad = dict(row, **{field: value})
            with self.subTest(field=field), self.assertRaises(runner.ContractError):
                runner._assert_case(mapped, {'data': {**detail['data'], 'run': bad}}, {'data': []}, {'data': []}, row['id'])

    def test_success_rejects_missing_snapshot_binding_and_invalid_numeric_evidence(self):
        case = self.cases[0]
        mapped = runner.LocalDecisionCase(case.case_id, self.work['woId'], self.work['woNo'], '000000',
                                          case.facts, case.expected_status, None, case.golden, {})
        row = {'id': '9007199254749001', 'tenantId': '000000', 'bizType': 'WORK_ORDER',
               'bizId': self.work['woId'], 'bizCode': self.work['woNo'], 'status': 'SUCCEEDED',
               'riskLevel': 'LOW', 'confidenceLevel': 'HIGH', 'recommendedScenarioCode': 'BASELINE_CURRENT',
               'riskScore': '10', 'inputSnapshotHash': 'sha256-a', 'analysisFlowInstanceId': '9007199254749999',
               'resultJson': json.dumps({'riskScore': 10, 'riskLevel': 'LOW', 'rootCauseCodes': [],
                                         'recommendedScenarioCode': 'BASELINE_CURRENT',
                                         'scenarios': [{'scenarioCode': 'BASELINE_CURRENT', 'inputHash': 'hash-a',
                                                        'evidenceKeys': ['snapshot:sha256-a']}],
                                         'evidence': [{'evidenceKey': 'ERP:WO:1:quantity'}],
                                         'narrative': {'executiveSummary': '交期正常'}})}
        ev = [{'runId': row['id'], 'tenantId': '000000', 'evidenceKey': 'ERP:WO:1:quantity',
               'sourceDomain': 'ERP', 'sourceType': 'WORK_ORDER', 'sourceId': self.work['woId'],
               'metricCode': 'planned_qty', 'metricValue': '10', 'valueType': 'NUMBER',
               'dataVersion': 'v1', 'verifiable': 'Y'}]
        timeline = {'data': [{'type': 'NODE', 'code': 'context', 'status': 'SUCCEEDED', 'time': 1}]}
        def check(r, e):
            return runner._assert_case(mapped, {'data': {'run': r, 'evidence': e, 'actions': []}},
                                       {'data': e}, timeline, row['id'])
        self.assertEqual(check(row, ev)['status'], 'SUCCEEDED')
        # DecisionEvidenceService.derivedNumber 持久化 dataVersion=None 及来源链。
        derived = {'runId': row['id'], 'tenantId': '000000', 'evidenceKey': 'DERIVED:CALC:risk_score:risk_score',
                   'sourceDomain': 'DERIVED', 'sourceType': 'CALC', 'sourceCode': 'risk_score',
                   'metricCode': 'risk_score', 'metricValue': '10', 'valueType': 'NUMBER',
                   'dataVersion': None, 'verifiable': 'Y', 'derivedFromJson': json.dumps([ev[0]['evidenceKey']])}
        self.assertEqual(check(row, ev + [derived])['status'], 'SUCCEEDED')
        bad_result = json.loads(row['resultJson'])
        bad_result['scenarios'][0]['evidenceKeys'] = []
        with self.assertRaises(runner.ContractError):
            check(dict(row, resultJson=json.dumps(bad_result)), ev)
        with self.assertRaises(runner.ContractError):
            check(row, [dict(ev[0], metricValue='not-a-number')])
        with self.assertRaises(runner.ContractError):
            check(row, [dict(ev[0], sourceId=9007199254747001)])

    def test_control_approval_must_verify_live_version_hash_and_action_before_post(self):
        row = {'id': '9007199254749001', 'status': 'WAITING_APPROVAL', 'tenantId': '000000',
               'bizType': 'WORK_ORDER', 'bizId': self.work['woId'], 'bizCode': self.work['woNo'],
               'version': 3, 'inputSnapshotHash': 'live-hash'}
        action = {'id': '9007199254749010', 'tenantId': '000000', 'runId': row['id'],
                  'executionStatus': 'NOT_STARTED', 'approvalStatus': 'PROPOSED'}
        client = FakeClient(work=self.work, detail={'run': row, 'actions': [action], 'evidence': []})
        control = runner.DecisionRunner(client, self.cases, self.manifest, self.mapped)
        for version, hsh, actions in [(2, 'live-hash', [action['id']]), (3, 'stale-hash', [action['id']]),
                                      (3, 'live-hash', ['9007199254749011'])]:
            with self.subTest(version=version, hsh=hsh, actions=actions), self.assertRaises(runner.ContractError):
                control.control_approve(self.cases[0].case_id, row['id'], version, actions, hsh,
                                        rejected_ids=[], confirm=True)
        self.assertFalse(any(method == 'POST' for method, *_ in client.calls))

    def test_read_only_api_work_order_vo_without_tenant_field_uses_server_authenticated_tenant(self):
        c = self.cases[0]
        work = dict(self.work, **c.facts)
        work.pop('tenantId')  # PrdWorkOrderVo 不提供 tenantId。
        mapping = [dict(self.mapped[0], measurements={k: {'path': '/erp/prd/work/9007199254747001', 'field': k}
                                                      for k in c.facts})]
        client = FakeClient(work=work)
        result = runner.DecisionRunner(client, self.cases, self.manifest, mapping).run(execute=True, timeout=0)
        self.assertEqual(result['executed'], 1)

    def test_preflight_auth_failure_is_not_misreported_as_missing_business_facts(self):
        client = FakeClient(tenant='000001', work=self.work)
        report = runner.DecisionRunner(client, self.cases, self.manifest, self.mapped).run(execute=True)
        self.assertEqual(report['cases'][0]['status'], 'AUTH_UNAVAILABLE')
        self.assertEqual(report['executed'], 0)

    def test_controls_do_not_mutate_without_explicit_confirmation(self):
        client = FakeClient(work=self.work)
        control = runner.DecisionRunner(client, self.cases, self.manifest, self.mapped)
        with self.assertRaises(runner.ContractError):
            control.control_reject(self.cases[0].case_id, '9007199254749001', '调查之后驳回')
        with self.assertRaises(runner.ContractError):
            control.control_recalculate(self.cases[0].case_id, '9007199254749001')
        with self.assertRaises(runner.ContractError):
            control.control_retry(self.cases[0].case_id, '9007199254749001', '9007199254749010')
        self.assertEqual(client.calls, [])

    def test_invalid_output_target_rejected_before_a_run_can_be_attempted(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = Path(tmp) / 'manifest.json'
            manifest.write_text(json.dumps(self.manifest), encoding='utf-8')
            with patch.object(runner, 'DecisionRunner') as guarded:
                result = runner.main(['--manifest', str(manifest), '--execute',
                                      '--output', str(HERE / 'tracked-report.json')])
                self.assertEqual(result, 2)
                guarded.assert_not_called()

    def test_control_approval_requires_explicit_selection_of_every_existing_action(self):
        row = {'id': '9007199254749001', 'status': 'WAITING_APPROVAL', 'tenantId': '000000',
               'bizType': 'WORK_ORDER', 'bizId': self.work['woId'], 'bizCode': self.work['woNo'],
               'version': 3, 'inputSnapshotHash': 'live-hash'}
        action_one = {'id': '9007199254749010', 'tenantId': '000000', 'runId': row['id'],
                      'executionStatus': 'NOT_STARTED', 'approvalStatus': 'PROPOSED'}
        action_two = dict(action_one, id='9007199254749011', approvalStatus='PENDING_APPROVAL')
        client = FakeClient(work=self.work, detail={'run': row, 'actions': [action_one, action_two], 'evidence': []})
        control = runner.DecisionRunner(client, self.cases, self.manifest, self.mapped)
        with self.assertRaises(runner.ContractError):
            control.control_approve(self.cases[0].case_id, row['id'], 3, [action_one['id']],
                                    'live-hash', rejected_ids=[], confirm=True)
        self.assertFalse(any(method == 'POST' for method, *_ in client.calls))
        control.control_approve(self.cases[0].case_id, row['id'], 3, [action_one['id']],
                                'live-hash', rejected_ids=[action_two['id']], confirm=True)
        posts = [entry for entry in client.calls if entry[0] == 'POST']
        self.assertEqual(len(posts), 1)
        self.assertEqual(posts[0][2]['approvedActionIds'], [action_one['id']])
        self.assertEqual(posts[0][2]['rejectedActionIds'], [action_two['id']])

    def test_post_rejected_by_permission_is_attempted_but_not_counted_as_executed(self):
        case = self.cases[0]
        mapping = [dict(self.mapped[0], measurements={key: {'path': '/erp/prd/work/9007199254747001', 'field': key}
                                                       for key in case.facts})]
        class RejectingClient(FakeClient):
            def post(self, path, body):
                self.calls.append(('POST', path, body))
                raise runner.ApiFailure('permission or service unavailable')
        client = RejectingClient(work=dict(self.work, **case.facts))
        result = runner.DecisionRunner(client, self.cases, self.manifest, mapping).run(execute=True)
        self.assertEqual(result['cases'][0]['status'], 'API_FAILURE')
        self.assertEqual(result['postAttempts'], 1)
        self.assertEqual(result['executed'], 0)
        self.assertEqual(result['passed'], 0)

    def test_duplicate_case_mapping_in_source_file_rejected_without_silent_override(self):
        case = self.cases[0]
        client = FakeClient(work=dict(self.work, **case.facts))
        with self.assertRaises(ValueError):
            runner.map_cases(self.cases, self.manifest, [self.mapped[0], dict(self.mapped[0])])
        self.assertEqual(client.calls, [])

    def test_temporarily_open_result_is_never_a_pass_and_carries_no_metrics(self):
        case = self.cases[0]
        mapping = [dict(self.mapped[0], measurements={key: {'path': '/erp/prd/work/9007199254747001', 'field': key}
                                                       for key in case.facts})]
        client = FakeClient(work=dict(self.work, **case.facts), detail={'run': {'status': 'STALE'}})
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapping).run(execute=True, timeout=0)
        self.assertEqual(report['passed'], 0)
        self.assertEqual(report['cases'][0]['status'], 'ASSERTION_FAILED')
        self.assertEqual(report['p50Ms'], None)

    def test_result_cannot_cite_evidence_not_persisted(self):
        case = self.cases[0]
        mapped = runner.LocalDecisionCase(case.case_id, self.work['woId'], self.work['woNo'], '000000',
                                          case.facts, 'SUCCEEDED', None, case.golden, {})
        row = {'id': '9007199254749001', 'tenantId': '000000', 'bizType': 'WORK_ORDER',
               'bizId': self.work['woId'], 'bizCode': self.work['woNo'], 'status': 'SUCCEEDED',
               'riskScore': '10', 'riskLevel': 'LOW', 'confidenceLevel': 'HIGH',
               'recommendedScenarioCode': 'BASELINE_CURRENT', 'inputSnapshotHash': 'snapshot-a',
               'analysisFlowInstanceId': '9007199254749999',
               'resultJson': json.dumps({'riskScore': 10, 'rootCauseCodes': [], 'riskLevel': 'LOW',
                 'recommendedScenarioCode': 'BASELINE_CURRENT', 'narrative': {'executiveSummary': '按期交付'},
                 'scenarios': [{'scenarioCode': 'BASELINE_CURRENT', 'inputHash': 'hash-a',
                                'evidenceKeys': ['snapshot:snapshot-a']}],
                 'evidence': [{'evidenceKey': 'ERP:WO:1:quantity'}, {'evidenceKey': 'missing:key'}]})}
        evidence = [{'runId': row['id'], 'tenantId': '000000', 'evidenceKey': 'ERP:WO:1:quantity',
                     'sourceDomain': 'ERP', 'sourceType': 'WORK_ORDER', 'sourceId': self.work['woId'],
                     'metricCode': 'planned_qty', 'metricValue': '10', 'valueType': 'NUMBER',
                     'dataVersion': '1', 'verifiable': 'Y'}]
        timeline = {'data': [{'type': 'NODE', 'code': 'context', 'status': 'SUCCEEDED', 'time': 1}]}
        with self.assertRaises(runner.ContractError):
            runner._assert_case(mapped, {'data': {'run': row, 'evidence': evidence, 'actions': []}},
                                {'data': evidence}, timeline, row['id'])

    def test_build_mapping_considers_only_created_manifest_work_orders_not_historical_demo(self):
        mapping = runner.propose_mapping(self.cases, self.manifest)
        self.assertEqual(mapping, [])  # 单个工单不足以证明历史 40 个案例的事实。
        self.assertEqual(len(self.manifest['records']), 1)

    def test_null_recommendation_case_can_keep_multiple_real_scenarios(self):
        case = self.cases[6]  # 历史 DELIVERY-007 的风险为 MEDIUM，但没有推荐方案。
        self.assertIsNone(case.golden['recommendedScenarioCode'])
        mapped = runner.LocalDecisionCase(case.case_id, self.work['woId'], self.work['woNo'], '000000',
                                          case.facts, 'WAITING_APPROVAL', None, case.golden, {})
        action = {'id': '9007199254749010', 'tenantId': '000000', 'runId': '9007199254749001',
                  'actionType': 'CREATE_PURCHASE_REQUISITION_DRAFT', 'executionStatus': 'NOT_STARTED'}
        result = {'riskScore': 45, 'riskLevel': 'MEDIUM', 'rootCauseCodes': case.golden['expectedRootCauses'],
                  'recommendedScenarioCode': None, 'narrative': {'executiveSummary': '发现物料风险'},
                  'scenarios': [{'scenarioCode': 'BASELINE_CURRENT', 'inputHash': 'hash-a',
                                 'evidenceKeys': ['snapshot:hash-1']}],
                  'evidence': [{'evidenceKey': 'ERP:WO:quantity'}]}
        row = {'id': action['runId'], 'tenantId': '000000', 'bizType': 'WORK_ORDER',
               'bizId': self.work['woId'], 'bizCode': self.work['woNo'], 'status': 'WAITING_APPROVAL',
               'riskScore': '45', 'riskLevel': 'MEDIUM', 'confidenceLevel': case.golden['expectedConfidence'],
               'recommendedScenarioCode': None, 'inputSnapshotHash': 'hash-1',
               'analysisFlowInstanceId': '9007199254749999', 'resultJson': json.dumps(result)}
        ev = [{'runId': row['id'], 'tenantId': '000000', 'evidenceKey': 'ERP:WO:quantity',
               'sourceDomain': 'ERP', 'sourceType': 'WORK_ORDER', 'sourceId': self.work['woId'],
               'metricCode': 'planned_qty', 'metricValue': '10', 'valueType': 'NUMBER',
               'dataVersion': 'v1', 'verifiable': 'Y'}]
        actual = runner._assert_case(mapped, {'data': {'run': row, 'actions': [action], 'evidence': ev}},
                                     {'data': ev}, {'data': [{'type': 'NODE', 'status': 'SUCCEEDED',
                                                             'code': 'context', 'time': 1}]}, row['id'])
        self.assertEqual(actual['status'], 'WAITING_APPROVAL')

    def test_bad_case_result_is_not_written_outside_ignored_report_tree(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = Path(tmp) / 'manifest.json'
            manifest.write_text(json.dumps(self.manifest), encoding='utf-8')
            to_write = Path(tmp) / 'unexpected.json'
            with patch.object(runner, 'DecisionRunner') as guarded:
                code = runner.main(['--manifest', str(manifest), '--execute', '--output', str(to_write)])
                self.assertEqual(code, 2)
                guarded.assert_not_called()
            self.assertFalse(to_write.exists())

    def test_missing_token_never_sends_any_http_request(self):
        opener = lambda req, timeout: self.fail('unexpected request')
        with patch.dict(os.environ, {}, clear=True):
            client = runner.DecisionClient(opener=opener)
            with self.assertRaises(runner.ContractError):
                client.get('/system/user/getInfo')

    def test_malicious_api_error_never_includes_token_or_response_body(self):
        from urllib.error import HTTPError
        from io import BytesIO
        secret = 'NEXUS_SECRET_INTERNAL_VALUE'
        def denied(req, timeout):
            raise HTTPError(req.full_url, 403, 'Bearer ' + secret, {}, BytesIO(secret.encode()))
        with patch.dict(os.environ, {'NEXUS_FULL_FLOW_TOKEN': secret,
                                     'NEXUS_FULL_FLOW_CLIENT_ID': secret}):
            api = runner.DecisionClient(opener=denied)
            with self.assertRaises(runner.ApiFailure) as ctx:
                api.get('/system/user/getInfo')
            self.assertNotIn(secret, str(ctx.exception))
            self.assertNotIn(secret, repr(api))

    def test_duplicate_accepted_run_cannot_count_twice_even_if_server_reuses_id(self):
        case1, case2 = self.cases[:2]
        record2 = dict(self.manifest['records'][0], entityId='9007199254747002', businessCode='WO202609290002')
        manifest = dict(self.manifest, records=self.manifest['records'] + [record2])
        mapping = [dict(self.mapped[0], caseId=case1.case_id, measurements={k: {
            'path': '/erp/prd/work/9007199254747001', 'field': k} for k in case1.facts}),
                   {'caseId': case2.case_id, 'workOrderId': record2['entityId'],
                    'workOrderCode': record2['businessCode'], 'tenantId': '000000',
                    'measurements': {k: {'path': '/erp/prd/work/9007199254747002', 'field': k}
                                     for k in case2.facts}}]
        class BothOrdersClient(FakeClient):
            def get(self, path):
                if path == '/erp/prd/work/9007199254747002':
                    self.calls.append(('GET', path))
                    return {'code': 200, 'data': dict(self.work, woId=record2['entityId'],
                                                    woNo=record2['businessCode'], **case2.facts)}
                return super().get(path)
        client = BothOrdersClient(work=dict(self.work, **case1.facts))
        report = runner.DecisionRunner(client, self.cases, manifest, mapping).run(execute=True, timeout=0)
        self.assertEqual(report['postAttempts'], 2)  # 只有服务端才能在 POST 后暴露其复用的 ID。
        self.assertEqual(report['executed'], 1)
        self.assertEqual(report['cases'][1]['status'], 'DUPLICATE_RUN_ID')

    def test_unreadable_live_business_api_is_not_reported_as_missing_business_facts(self):
        case = self.cases[0]
        mapping = [dict(self.mapped[0], measurements={key: {'path': '/erp/prd/work/9007199254747001', 'field': key}
                                                       for key in case.facts})]
        class NoWorkOrder(FakeClient):
            def get(self, path):
                if path.startswith('/erp/prd/work/'):
                    raise runner.ApiFailure('GET: HTTP 403')
                return super().get(path)
        client = NoWorkOrder(work=self.work)
        report = runner.DecisionRunner(client, self.cases, self.manifest, mapping).run(execute=True)
        self.assertEqual(report['cases'][0]['status'], 'PRECHECK_API_FAILURE')
        self.assertEqual(report['executed'], 0)
        self.assertEqual(report['postAttempts'], 0)


if __name__ == '__main__':
    unittest.main()
