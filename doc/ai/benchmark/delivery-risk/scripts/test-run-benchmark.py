"""PowerShell 基线运行器的离线 HTTP 契约测试，不访问 ERP 或 MySQL。"""
import json
import os
import shutil
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

RUNNER = Path(__file__).with_name('run-benchmark.ps1')
SHELL = shutil.which('pwsh') or shutil.which('powershell')


class FakeDecisionApi(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def write_json(self, body):
        data = json.dumps({'code': 200, 'data': body}, ensure_ascii=False).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path != '/ai/decision/delivery-risk/run':
            self.send_error(404)
            return
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        self.server.requests.append((body, self.headers.get('Authorization'), self.headers.get('clientid')))
        self.write_json({'runId': str(body['bizId'])})

    def do_GET(self):
        run_id = self.path.rsplit('/', 1)[-1]
        if self.path.startswith('/erp/prd/work/'):
            self.server.work_checks.append((run_id, self.headers.get('clientid')))
            if run_id not in self.server.work_orders:
                self.send_error(404)
                return
            self.write_json({'woId': run_id, 'woNo': self.server.work_orders[run_id]})
            return
        if not self.path.startswith('/ai/decision/run/') or run_id not in self.server.states:
            self.send_error(404)
            return
        self.write_json(self.server.states[run_id])


@unittest.skipUnless(SHELL, 'PowerShell is required')
class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), FakeDecisionApi)
        self.server.states = {}
        self.server.requests = []
        self.server.work_orders = {}
        self.server.work_checks = []
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.addCleanup(self.thread.join, 2)
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.work = Path(self.dir.name)

    def run_fixture(self, rows, states, expected=None, timeout='2', work_orders=None):
        self.server.states = states
        self.server.work_orders = ({row['bizId']: row['bizCode'] for row in rows}
                                   if work_orders is None else work_orders)
        cases = self.work / 'cases.jsonl'
        expected_file = self.work / 'expected-results.jsonl'
        cases.write_text(''.join(json.dumps(x) + '\n' for x in rows), encoding='utf-8')
        expected_file.write_text(''.join(json.dumps(x) + '\n' for x in (expected or rows)), encoding='utf-8')
        report = self.work / 'report.md'
        env = dict(os.environ, AI_DECISION_BENCHMARK_TOKEN='TOKEN_MUST_NOT_LEAK')
        result = subprocess.run([SHELL, '-NoProfile', '-File', str(RUNNER),
            '-BaseUrl', f'http://127.0.0.1:{self.server.server_port}', '-Cases', str(cases),
            '-Expected', str(expected_file), '-Output', str(report), '-ClientId', 'a' * 32, '-TimeoutSeconds', timeout,
            '-PollIntervalMilliseconds', '25'], env=env, capture_output=True, text=True,
            encoding='utf-8', timeout=20)
        return result, report.read_text(encoding='utf-8') if report.exists() else ''

    def sample(self, ident, risk='LOW', status='SUCCEEDED'):
        return dict(caseId=f'DELIVERY-{ident:03d}', bizType='WORK_ORDER',
            bizId=str(9007199254745000 + ident), bizCode=f'DEMO-AI-DECISION-{ident:03d}',
            horizonDays=30, expectedStatus=status, expectedRiskLevel=risk,
            expectedConfidence='HIGH', expectedRootCauses=[], recommendedScenarioCode='BASELINE_CURRENT',
            requiredActions=[], forbiddenActions=['AUTO_APPROVE'])

    def response(self, case, status='SUCCEEDED', biz_id=None):
        result = dict(rootCauseCodes=case['expectedRootCauses'], riskLevel=case['expectedRiskLevel'],
                      recommendedScenarioCode=case['recommendedScenarioCode'])
        return dict(run=dict(bizType='WORK_ORDER', bizId=biz_id or case['bizId'],
            bizCode=case['bizCode'], status=status, riskLevel=case['expectedRiskLevel'],
            confidenceLevel='HIGH', recommendedScenarioCode=case['recommendedScenarioCode'],
            resultJson=json.dumps(result)), actions=[])

    def test_client_id_is_required_before_any_request(self):
        case = self.sample(1)
        self.server.states = {case['bizId']: self.response(case)}
        cases = self.work / 'client-id-cases.jsonl'
        expected = self.work / 'client-id-expected.jsonl'
        cases.write_text(json.dumps(case) + '\n', encoding='utf-8')
        expected.write_text(json.dumps(case) + '\n', encoding='utf-8')
        env = dict(os.environ, AI_DECISION_BENCHMARK_TOKEN='TOKEN_MUST_NOT_LEAK')
        env.pop('AI_DECISION_BENCHMARK_CLIENT_ID', None)
        result = subprocess.run([SHELL, '-NoProfile', '-File', str(RUNNER),
            '-BaseUrl', f'http://127.0.0.1:{self.server.server_port}',
            '-Cases', str(cases), '-Expected', str(expected)], env=env,
            capture_output=True, text=True, encoding='utf-8', timeout=20)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.server.requests)
        self.assertNotIn('TOKEN_MUST_NOT_LEAK', result.stdout + result.stderr)

    def test_missing_run_code_is_verified_against_independent_erp_work_order(self):
        case = self.sample(1)
        detail = self.response(case)
        detail['run'].pop('bizCode')
        proc, report = self.run_fixture([case], {case['bizId']: detail})
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn('1/1', report)
        self.assertEqual([(case['bizId'], 'a' * 32)], self.server.work_checks)

    def test_wrong_erp_work_order_code_must_fail_before_starting_decision(self):
        case = self.sample(1)
        proc, report = self.run_fixture([case], {case['bizId']: self.response(case)},
                                        work_orders={case['bizId']: 'WRONG-CODE'})
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('0/1', report)
        self.assertEqual([], self.server.requests)

    def test_nonempty_wrong_run_code_is_rejected_even_when_erp_matches(self):
        case = self.sample(1)
        detail = self.response(case)
        detail['run']['bizCode'] = 'WRONG-RUN-CODE'
        proc, report = self.run_fixture([case], {case['bizId']: detail})
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('0/1', report)
        self.assertEqual(1, len(self.server.requests))

    def test_missing_erp_work_order_never_starts_a_decision(self):
        case = self.sample(1)
        proc, report = self.run_fixture([case], {case['bizId']: self.response(case)}, work_orders={})
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('0/1', report)
        self.assertEqual([], self.server.requests)

    def test_exact_biz_id_and_pass_rate_with_no_token_leak(self):
        ok, wrong = self.sample(1), self.sample(2)
        states = {ok['bizId']: self.response(ok), wrong['bizId']: self.response(wrong, biz_id='42')}
        proc, report = self.run_fixture([ok, wrong], states)
        self.assertEqual(1, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn('DELIVERY-001', report)
        self.assertIn('DELIVERY-002', report)
        self.assertIn('1/2', report)
        self.assertIn('P50', report)
        self.assertIn('P95', report)
        self.assertNotIn('TOKEN_MUST_NOT_LEAK', proc.stdout + proc.stderr + report)
        self.assertTrue(all(token == 'Bearer TOKEN_MUST_NOT_LEAK' and cid == 'a' * 32
                            for _, token, cid in self.server.requests))
        self.assertTrue(all(isinstance(body['bizId'], str) for body, _, _ in self.server.requests))

    def test_ready_sse_event_is_not_an_acceptable_run_status(self):
        case = self.sample(3)
        proc, report = self.run_fixture([case], {case['bizId']: self.response(case, 'AI_DECISION_READY')})
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('0/1', report)
        self.assertNotIn('| PASS |', report)

    def test_negative_case_requires_expected_failure_pattern_not_arbitrary_error(self):
        case = self.sample(26, risk=None, status='FAILED')
        case.update(expectedConfidence='LOW', recommendedScenarioCode=None,
                    expectedFailurePattern='routing|工艺', expectedFailureCode='ROUTING_EVIDENCE_MISSING')
        failing = dict(run=dict(bizType=case['bizType'], bizId=case['bizId'], bizCode=case['bizCode'],
                                status='FAILED', errorMessage='DB connection refused'), actions=[])
        proc, report = self.run_fixture([case], {case['bizId']: failing})
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('0/1', report)
        self.assertNotIn('| PASS |', report)

    def test_expected_evidence_rejection_counts_only_with_matching_failure(self):
        case = self.sample(26, risk=None, status='FAILED')
        case.update(expectedConfidence='LOW', recommendedScenarioCode=None,
                    expectedFailurePattern='routing|工艺', expectedFailureCode='ROUTING_EVIDENCE_MISSING')
        failing = dict(run=dict(bizType=case['bizType'], bizId=case['bizId'], bizCode=case['bizCode'],
                                status='FAILED', errorMessage='缺少工艺 routing 数据', errorCode='ROUTING_EVIDENCE_MISSING'), actions=[])
        proc, report = self.run_fixture([case], {case['bizId']: failing})
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn('1/1', report)
        self.assertIn('预期证据拒绝', report)
    def evidence_rejection_fixture(self):
        case = self.sample(26, risk=None, status='FAILED')
        case.update(expectedConfidence='LOW', recommendedScenarioCode=None,
                    expectedFailurePattern='routing|工艺', expectedFailureCode='ROUTING_EVIDENCE_MISSING')
        detail = dict(run=dict(bizType=case['bizType'], bizId=case['bizId'], bizCode=case['bizCode'],
                              status='FAILED', errorMessage='缺少工艺 routing 数据', errorCode='ROUTING_EVIDENCE_MISSING'), actions=[])
        return case, detail

    def test_matching_evidence_message_with_wrong_or_missing_code_never_passes(self):
        for code in ('DECISION_NODE_FAILED', None, 'routing_evidence_missing'):
            case, detail = self.evidence_rejection_fixture()
            if code is None: detail['run'].pop('errorCode')
            else: detail['run']['errorCode'] = code
            proc, report = self.run_fixture([case], {case['bizId']: detail})
            self.assertNotEqual(0, proc.returncode)
            self.assertIn('0/1', report)

    def test_known_rejection_cannot_publish_a_risk_or_action(self):
        for field in ('riskLevel', 'riskScore', 'recommendedScenarioCode', 'resultJson', 'action'):
            case, detail = self.evidence_rejection_fixture()
            if field == 'action': detail['actions'] = [dict(actionType='CREATE_PURCHASE_REQUISITION_DRAFT')]
            else: detail['run'][field] = 'LOW' if field == 'riskLevel' else 0 if field == 'riskScore' else 'APS_CR' if field == 'recommendedScenarioCode' else '{}'
            proc, report = self.run_fixture([case], {case['bizId']: detail})
            self.assertNotEqual(0, proc.returncode)
            self.assertIn('0/1', report)

    def test_generic_error_code_is_never_an_allowed_negative_golden(self):
        case, detail = self.evidence_rejection_fixture()
        case['expectedFailureCode'] = detail['run']['errorCode'] = 'DECISION_NODE_FAILED'
        proc, report = self.run_fixture([case], {case['bizId']: detail})
        self.assertNotEqual(0, proc.returncode)
        self.assertEqual([], self.server.requests)

    def test_known_code_still_requires_the_original_failure_message_pattern(self):
        case, detail = self.evidence_rejection_fixture()
        detail['run']['errorMessage'] = 'DB connection refused'
        proc, report = self.run_fixture([case], {case['bizId']: detail})
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('0/1', report)

    def test_case_code_must_match_its_case_id_before_any_http_request(self):
        case = self.sample(4)
        case['bizCode'] = 'DEMO-AI-DECISION-999'
        proc, report = self.run_fixture([case], {case['bizId']: self.response(case)})
        self.assertNotEqual(0, proc.returncode)
        self.assertFalse(self.server.requests)
        self.assertNotIn('| PASS |', report)
    def test_unreachable_run_is_reported_failed_and_not_as_pass(self):
        case = self.sample(1)
        proc, report = self.run_fixture([case], {case['bizId']: self.response(case, 'RUNNING')}, timeout='1')
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('0/1', report)
        self.assertIn('TIMEOUT', report)


if __name__ == '__main__':
    unittest.main()