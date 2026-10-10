"""离线盘点回归测试；所有来源/运行数据均为合成快照，不代表真实验收。"""
from copy import deepcopy
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
try:
    import formal_case_readiness as readiness
except ModuleNotFoundError as error:
    if error.name != 'formal_case_readiness':
        raise
    readiness = None

BENCHMARK = HERE.parents[1] / 'benchmark' / 'delivery-risk'
TENANT, SHA, BUILD = '000000', 'a' * 40, 'unittest-build-only'


def response(data):
    return {'code': 200, 'data': data}


def fixture():
    """复用真实 benchmark 契约；模拟业务事实字段只测试算法，不宣称 API 提供。"""
    cases = [json.loads(line) for line in (BENCHMARK / 'cases.jsonl').read_text(encoding='utf-8').splitlines()]
    expected = [json.loads(line) for line in (BENCHMARK / 'expected-results.jsonl').read_text(encoding='utf-8').splitlines()]
    sources, runs = [], []
    for index, case in enumerate(cases, 1):
        biz_id, run_id = str(9007199254747000 + index), str(9007199254749000 + index)
        code = f'WO20261008{index:04d}'
        scope = {'caseId': case['caseId'], 'tenantId': TENANT, 'commitSha': SHA, 'buildId': BUILD}
        path = f'/erp/prd/work/{biz_id}'
        work = {'woId': biz_id, 'woNo': code, 'tenantId': TENANT, 'woStatus': 'RELEASED',
                'createTime': '2026-10-08 09:00:00', **case['facts']}
        sources.append({**scope, 'bizType': 'WORK_ORDER', 'bizId': biz_id, 'bizCode': code,
                        'baselineIds': [], 'baselineAt': '2026-10-08T08:00:00+08:00',
                        'capturedAt': '2026-10-08T10:00:00+08:00', 'readbacks': {path: response(work)},
                        'measurements': {key: {'path': path, 'field': key} for key in case['facts']}})
        row = {'id': run_id, 'tenantId': TENANT, 'bizType': 'WORK_ORDER', 'bizId': biz_id,
               'bizCode': code, 'status': case['expectedStatus'], 'riskLevel': case['expectedRiskLevel'],
               'confidenceLevel': case['expectedConfidence'], 'recommendedScenarioCode': case['recommendedScenarioCode'],
               'riskScore': '10', 'inputSnapshotHash': f'snapshot-{index}',
               'analysisFlowInstanceId': str(9007199254750000 + index)}
        evidence = [{'id': str(9007199254780000 + index), 'runId': run_id, 'tenantId': TENANT,
                     'evidenceKey': f'ERP:WORK_ORDER:{biz_id}:planned_qty',
                     'sourceDomain': 'ERP', 'sourceType': 'WORK_ORDER', 'sourceId': biz_id, 'sourceCode': code,
                     'metricCode': 'planned_qty', 'metricName': 'Synthetic planned quantity',
                     'metricValue': '10', 'valueType': 'NUMBER', 'observedAt': '2026-10-08 09:00:00',
                     'dataVersion': f'snapshot-{index}', 'verifiable': 'Y', 'derivedFromJson': '[]'}]
        actions = [{'id': str(9007199254760000 + index * 10 + j), 'runId': run_id, 'tenantId': TENANT,
                    'actionType': action_type, 'executionStatus': 'NOT_STARTED', 'approvalStatus': 'PROPOSED',
                    'idempotencyKey': f'action-{index}-{j}', 'payloadJson': json.dumps({'targetUserId': '101'})}
                   for j, action_type in enumerate(case['requiredActions'])]
        if case['expectedStatus'] == 'FAILED':
            row['errorCode'] = case['expectedFailureCode']
            row['resultJson'] = None
            evidence = []
        else:
            row['resultJson'] = json.dumps({'rootCauseCodes': case['expectedRootCauses'], 'riskScore': 10,
                'riskLevel': case['expectedRiskLevel'], 'recommendedScenarioCode': case['recommendedScenarioCode'],
                'scenarios': [{'scenarioCode': case['recommendedScenarioCode'] or 'BASELINE_CURRENT',
                               'inputHash': 'input', 'evidenceKeys': [f'snapshot:snapshot-{index}']}],
                'narrative': {'executiveSummary': '合成测试，非业务证明'}})
        run = {**scope, 'runId': run_id, 'elapsedMs': index * 100,
               'detail': response({'run': row, 'actions': actions, 'evidence': evidence}),
               'evidence': response(deepcopy(evidence)),
               'timeline': response([{'type': 'NODE', 'code': 'context', 'time': 1, 'status': 'SUCCEEDED'}])}
        if actions:
            execution = deepcopy(run['detail'])
            execution['data']['run']['status'] = 'SUCCEEDED'
            targets = []
            for j, action in enumerate(execution['data']['actions']):
                action.update(executionStatus='SUCCEEDED', approvalStatus='APPROVED')
                target_id = str(9007199254770000 + index * 10 + j)
                common = {'tenantId': TENANT, 'decisionRunId': run_id, 'decisionActionId': action['id'],
                          'decisionIdempotencyKey': action['idempotencyKey'], 'sourceType': 'AI_DECISION'}
                if action['actionType'] == 'CREATE_SCHEDULE_PLAN_DRAFT':
                    action.update(resultBizType='PRD_SCHEDULE_PLAN', resultBizId=target_id, resultBizCode='APS' + target_id)
                    target = {**common, 'planId': target_id, 'planNo': action['resultBizCode'], 'status': 'COMPLETED'}
                    del target['sourceType']
                elif action['actionType'] == 'CREATE_PURCHASE_REQUISITION_DRAFT':
                    action.update(resultBizType='PU_PURCHASE_REQUISITION', resultBizId=target_id, resultBizCode='PR' + target_id)
                    target = {**common, 'requisitionId': target_id, 'requisitionNo': action['resultBizCode'], 'status': 'DRAFT'}
                else:
                    action.update(resultBizType='SYS_NOTIFICATION', resultBizId=None, resultBizCode=None)
                    target = {'notificationId': target_id, 'tenantId': TENANT, 'userId': '101', 'category': 'ai_decision',
                              'refType': 'ai_decision_run', 'refId': run_id,
                              'extJson': json.dumps({'idempotencyKey': action['idempotencyKey']})}
                targets.append({'actionId': action['id'], 'row': target})
            run['execution'] = {'detail': execution, 'readbacks': targets}
        runs.append(run)
    return cases, expected, {'sources': sources, 'runs': runs}


class ReadinessTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(readiness, '缺少纯离线 readiness 实现：应先看到 RED')
        self.cases, self.expected, self.evidence = fixture()

    def report(self, **kwargs):
        return readiness.build_readiness(self.cases, self.expected, self.evidence,
            tenant_id=TENANT, commit_sha=SHA, build_id=BUILD, **kwargs)

    def test_full_current_build_separates_analysis_refusal_and_execution(self):
        before = deepcopy((self.cases, self.expected, self.evidence))
        report = self.report()
        self.assertEqual(report['plannedCases'], 40)
        self.assertEqual(report['independentCreatedSources'], 40)
        self.assertEqual(report['analysableCases'], 40)
        self.assertEqual(report['analysisPassedCases'], 34)
        self.assertEqual(report['expectedRejectedCases'], 6)
        self.assertEqual(report['executionRequiredCases'], 29)
        self.assertEqual(report['executionClosedCases'], 29)
        self.assertTrue(report['officialFullReady'])
        self.assertTrue(report['performance']['signingEligible'])
        self.assertEqual(report['performance']['sampleCount'], 40)
        self.assertEqual(report['performance']['denominator'], 40)
        self.assertEqual(report['performance']['p50Ms'], 2000)
        self.assertEqual(report['performance']['p95Ms'], 3800)
        self.assertFalse(report['productionAcceptance'])
        self.assertFalse(report['competitionSubmissionAccepted'])
        self.assertFalse(report['performance']['slaAccepted'])
        self.assertEqual(before, (self.cases, self.expected, self.evidence))

    def test_plan_only_is_not_created_or_ready(self):
        self.evidence = {'sources': [], 'runs': []}
        report = self.report()
        self.assertEqual(report['plannedCases'], 40)
        for field in ('independentCreatedSources', 'analysableCases', 'analysisPassedCases', 'expectedRejectedCases', 'executionClosedCases'):
            self.assertEqual(report[field], 0)
        self.assertFalse(report['officialFullReady'])
        self.assertIsNone(report['performance']['p95Ms'])

    def test_missing_one_source_or_run_never_full_ready(self):
        for key in ('sources', 'runs'):
            with self.subTest(key=key):
                original = self.evidence[key]
                self.evidence[key] = original[:-1]
                report = self.report()
                self.assertFalse(report['officialFullReady'])
                self.assertFalse(report['performance']['signingEligible'])
                self.assertEqual(report['performance']['denominator'], 40)
                self.evidence[key] = original

    def test_duplicate_business_source_with_distinct_run_ids_is_one_source(self):
        original = self.evidence['sources'][0]
        duplicate = deepcopy(original)
        duplicate['caseId'] = self.evidence['sources'][1]['caseId']
        self.evidence['sources'][1] = duplicate
        report = self.report()
        self.assertEqual(report['independentCreatedSources'], 39)
        self.assertLess(report['analysableCases'], 40)
        self.assertFalse(report['officialFullReady'])
        self.assertIn('DUPLICATE_BUSINESS_SOURCE', report['cases'][0]['blockers'])

    def test_duplicate_case_ids_in_each_input_are_input_errors(self):
        for collection in (self.cases, self.expected, self.evidence['sources'], self.evidence['runs']):
            with self.subTest(collection=collection[0].keys()):
                collection.append(deepcopy(collection[0]))
                with self.assertRaises(ValueError):
                    self.report()
                collection.pop()

    def test_unknown_case_and_unmatched_expectations_are_errors(self):
        self.evidence['runs'][0]['caseId'] = 'DELIVERY-999'
        with self.assertRaises(ValueError):
            self.report()
        self.evidence['runs'][0]['caseId'] = self.cases[0]['caseId']
        self.expected.pop()
        with self.assertRaises(ValueError):
            self.report()

    def test_empty_cases_wrong_types_and_invalid_target_are_errors(self):
        for cases, expected, target in (([], [], 40), ({}, self.expected, 40), (self.cases, self.expected, 0),
                                        (self.cases, self.expected, True), (self.cases, self.expected, 39)):
            with self.subTest(target=target, kind=type(cases)), self.assertRaises(ValueError):
                readiness.build_readiness(cases, expected, self.evidence,
                    tenant_id=TENANT, commit_sha=SHA, build_id=BUILD, target_count=target)

    def test_small_target_is_diagnostic_not_official(self):
        self.cases, self.expected = self.cases[:1], self.expected[:1]
        self.evidence = {key: value[:1] for key, value in self.evidence.items()}
        report = self.report(target_count=1)
        self.assertTrue(report['analysisReady'])
        self.assertFalse(report['officialFullReady'])
        self.assertFalse(report['performance']['signingEligible'])

    def test_thirty_nine_cases_with_default_target_cannot_issue(self):
        self.cases.pop(); self.expected.pop()
        self.evidence = {key: value[:-1] for key, value in self.evidence.items()}
        report = self.report()
        self.assertEqual(report['plannedCases'], 39)
        self.assertEqual(report['performance']['denominator'], 40)
        self.assertFalse(report['officialFullReady'])
        self.assertFalse(report['performance']['signingEligible'])

    def test_cross_tenant_stale_sha_and_mixed_build_are_blocked(self):
        for key in ('sources', 'runs'):
            for field, value in (('tenantId', '000001'), ('commitSha', 'b' * 40), ('buildId', 'old-build')):
                with self.subTest(key=key, field=field):
                    original = self.evidence[key][0][field]
                    self.evidence[key][0][field] = value
                    report = self.report()
                    self.assertFalse(report['officialFullReady'])
                    self.assertFalse(report['performance']['signingEligible'])
                    self.assertEqual(report['performance']['sampleCount'], 39)
                    self.evidence[key][0][field] = original

    def test_number_ids_placeholder_codes_and_cross_tenant_readbacks_are_not_created(self):
        source = self.evidence['sources'][0]
        path = next(iter(source['readbacks']))
        work = source['readbacks'][path]['data']
        for row, field, value in ((source, 'bizId', int(source['bizId'])), (source, 'bizCode', 'DEMO-WO-1'),
                                  (work, 'woId', int(source['bizId'])), (work, 'tenantId', '000001'),
                                  (work, 'woStatus', 'MYSTERY')):
            with self.subTest(field=field):
                original = row[field]; row[field] = value
                self.assertEqual(self.report()['independentCreatedSources'], 39)
                row[field] = original

    def test_http_200_without_native_business_data_is_not_a_source(self):
        source = self.evidence['sources'][0]
        source['readbacks'][next(iter(source['readbacks']))] = {'code': 200}
        self.assertEqual(self.report()['independentCreatedSources'], 39)

    def test_baseline_and_creation_time_are_mandatory_and_consistent(self):
        source = self.evidence['sources'][0]
        for field, value in (('baselineIds', [source['bizId']]), ('baselineIds', None),
                             ('baselineAt', '2026-10-09T08:00:00+08:00'), ('capturedAt', 'bad')):
            with self.subTest(field=field):
                original = source[field]; source[field] = value
                self.assertEqual(self.report()['independentCreatedSources'], 39)
                source[field] = original
        work = next(iter(source['readbacks'].values()))['data']
        for value in ('2026-10-07 09:00:00', '2026-10-09 09:00:00', None):
            work['createTime'] = value
            self.assertEqual(self.report()['independentCreatedSources'], 39)

    def test_created_work_order_and_missing_facts_are_not_analysable(self):
        source = self.evidence['sources'][0]
        work = next(iter(source['readbacks'].values()))['data']
        for key, value in (('woStatus', 'CREATED'), ('tardinessDays', 999)):
            original = work[key]; work[key] = value
            report = self.report()
            self.assertEqual(report['independentCreatedSources'], 40)
            self.assertEqual(report['analysableCases'], 39)
            self.assertFalse(report['officialFullReady'])
            work[key] = original
        source['measurements'] = {}
        self.assertEqual(self.report()['analysableCases'], 39)

    def test_unknown_pending_and_unexpected_failure_keep_timing_denominator(self):
        row = self.evidence['runs'][0]['detail']['data']['run']
        for status in ('MYSTERY', 'RUNNING', 'FAILED', 'REJECTED'):
            with self.subTest(status=status):
                row['status'] = status
                report = self.report()
                self.assertEqual(report['analysisPassedCases'], 33)
                self.assertEqual(report['performance']['sampleCount'], 40)
                self.assertEqual(report['performance']['failedOrUnknownCases'], 1)
                self.assertEqual(report['performance']['denominator'], 40)
                self.assertFalse(report['performance']['signingEligible'])

    def test_failure_refusal_requires_declared_exact_code_no_actions_and_no_result(self):
        run = self.evidence['runs'][25]
        row = run['detail']['data']['run']
        for field, value in (('errorCode', 'OTHER'), ('riskLevel', 'LOW'), ('resultJson', '{}')):
            with self.subTest(field=field):
                original = row.get(field); row[field] = value
                self.assertEqual(self.report()['expectedRejectedCases'], 5)
                row[field] = original
        run['detail']['data']['actions'] = [deepcopy(self.evidence['runs'][5]['detail']['data']['actions'][0])]
        self.assertEqual(self.report()['expectedRejectedCases'], 5)

    def test_success_requires_result_evidence_timeline_and_matching_native_identity(self):
        run = self.evidence['runs'][0]
        for field, value in (('detail', response({})), ('evidence', response([])), ('timeline', response([]))):
            with self.subTest(field=field):
                original = run[field]; run[field] = value
                self.assertEqual(self.report()['analysisPassedCases'], 33)
                run[field] = original
        run['detail']['data']['run']['bizId'] = '1'
        self.assertFalse(self.report()['officialFullReady'])

    def test_duplicate_run_id_never_counts_as_independent_observation(self):
        self.evidence['runs'][1]['runId'] = self.evidence['runs'][0]['runId']
        report = self.report()
        self.assertFalse(report['officialFullReady'])
        self.assertLess(report['performance']['sampleCount'], 40)

    def test_zero_missing_boolean_string_nonfinite_and_negative_timings_do_not_issue(self):
        run = self.evidence['runs'][0]
        for timing in (0, None, True, '100', float('nan'), float('inf'), -1):
            with self.subTest(timing=timing):
                run['elapsedMs'] = timing
                report = self.report()
                self.assertEqual(report['analysisPassedCases'], 34)
                self.assertEqual(report['performance']['sampleCount'], 39)
                self.assertEqual(report['performance']['untimedCases'], 1)
                self.assertFalse(report['performance']['signingEligible'])

    def test_analysis_pass_and_percentiles_are_not_execution_closure(self):
        for run in self.evidence['runs']:
            run.pop('execution', None)
        report = self.report()
        self.assertTrue(report['analysisReady'])
        self.assertTrue(report['performance']['signingEligible'])
        self.assertEqual(report['executionClosedCases'], 0)
        self.assertFalse(report['officialFullReady'])

    def test_execution_requires_approved_success_and_independent_matching_target(self):
        run = self.evidence['runs'][5]
        execution = run['execution']
        action = execution['detail']['data']['actions'][0]
        target = next(entry['row'] for entry in execution['readbacks'] if 'decisionRunId' in entry['row'])
        for row, field, value in ((action, 'approvalStatus', 'PENDING_APPROVAL'),
                                  (action, 'executionStatus', 'FAILED'), (target, 'tenantId', '000001'),
                                  (target, 'decisionRunId', '1'), (target, 'decisionActionId', '1')):
            with self.subTest(field=field):
                original = row[field]; row[field] = value
                self.assertEqual(self.report()['executionClosedCases'], 28)
                self.assertFalse(self.report()['officialFullReady'])
                row[field] = original
        execution['readbacks'] = []
        self.assertEqual(self.report()['executionClosedCases'], 28)

    def test_internal_notification_acceptance_is_not_persisted_or_external_delivery(self):
        run = next(run for run in self.evidence['runs'] if any(
            a['actionType'] == 'SEND_EXPEDITE_NOTICE' for a in run['detail']['data']['actions']))
        execution = run['execution']
        execution['readbacks'] = [entry for entry in execution['readbacks'] if 'notificationId' not in entry['row']]
        self.assertEqual(self.report()['executionClosedCases'], 28)
        self.assertFalse(self.report()['officialFullReady'])

    def test_final_payload_must_match_frozen_parsed_object(self):
        run = self.evidence['runs'][5]
        action = next(a for a in run['execution']['detail']['data']['actions']
                      if a['actionType'] == 'SEND_EXPEDITE_NOTICE')
        frozen = action['payloadJson']
        action['payloadJson'] = json.dumps({'targetUserId': '999'})
        report = self.report()
        self.assertFalse(report['officialFullReady'])
        self.assertEqual(report['executionClosedCases'], 28)
        self.assertTrue(report['analysisReady'])
        action['payloadJson'] = frozen
        self.assertTrue(self.report()['officialFullReady'])
        for action_type in ('SEND_EXPEDITE_NOTICE', 'CREATE_SCHEDULE_PLAN_DRAFT', 'CREATE_PURCHASE_REQUISITION_DRAFT'):
            run = next(r for r in self.evidence['runs'] if any(
                a['actionType'] == action_type for a in r['detail']['data']['actions']))
            prior = next(a for a in run['detail']['data']['actions'] if a['actionType'] == action_type)
            final = next(a for a in run['execution']['detail']['data']['actions'] if a['id'] == prior['id'])
            prior['payloadJson'] = '{"targetUserId":"101","context":{"a":1,"b":true}}'
            final['payloadJson'] = '{ "context": {"b":true, "a":1}, "targetUserId":"101" }'
            self.assertTrue(self.report()['officialFullReady'], 'key order and whitespace are not payload changes')
            canonical = final['payloadJson']
            for changed in ('{"targetUserId":"999"}', '[]', '{', canonical.replace('true', '1'),
                            '{"targetUserId":"101","targetUserId":"999"}', '{"value":NaN}',
                            '[' * 1100 + ']' * 1100):
                with self.subTest(action=action_type, payload=changed[:80]):
                    final['payloadJson'] = changed
                    self.assertFalse(self.report()['officialFullReady'])
                    self.assertEqual(self.report()['executionClosedCases'], 28)
            final['payloadJson'] = canonical
        self.assertTrue(self.report()['officialFullReady'])

    def test_success_requires_nonzero_decimal_string_flow_instance_id(self):
        row = self.evidence['runs'][0]['detail']['data']['run']
        original = row['analysisFlowInstanceId']
        row['analysisFlowInstanceId'] = ''
        self.assertFalse(self.report()['officialFullReady'])
        for value in ('', None, '0', '01', 'not-an-id', 1, True):
            with self.subTest(flow_id=value):
                row['analysisFlowInstanceId'] = value
                report = self.report()
                self.assertEqual(report['analysisPassedCases'], 33)
                self.assertEqual(report['expectedRejectedCases'], 6)
                self.assertEqual(report['performance']['sampleCount'], 40)
                self.assertFalse(report['performance']['signingEligible'])
                self.assertIn('ANALYSIS_RESULT_UNVERIFIED', report['cases'][0]['blockers'])
        row['analysisFlowInstanceId'] = original
        self.assertTrue(self.report()['officialFullReady'])

    def test_persisted_evidence_requires_native_values_and_rooted_sources(self):
        run = self.evidence['runs'][0]
        base = deepcopy(run['evidence']['data'][0])

        def use(items):
            run['detail']['data']['evidence'] = deepcopy(items)
            run['evidence']['data'] = deepcopy(items)

        use([{'runId': run['runId'], 'tenantId': TENANT, 'evidenceKey': 'stub',
              'sourceDomain': 'ERP', 'sourceType': 'WORK_ORDER'}])
        self.assertFalse(self.report()['officialFullReady'])
        for field, value in (('id', None), ('id', 1), ('runId', '1'), ('tenantId', '000001'),
                             ('evidenceKey', 'stub'), ('sourceDomain', ''), ('sourceType', ''),
                             ('sourceId', None), ('sourceId', '0'), ('metricCode', ''), ('metricName', ''),
                             ('valueType', None), ('valueType', 'UNKNOWN'), ('metricValue', None),
                             ('metricValue', ''), ('metricValue', 'NaN'), ('metricValue', '1_0'),
                             ('metricValue', ' 10 '), ('metricValue', 10),
                             ('observedAt', 'bad'), ('observedAt', None), ('verifiable', 'UNKNOWN'),
                             ('verifiable', 'N'), ('derivedFromJson', '{}'),
                             ('derivedFromJson', '[' * 1100 + ']' * 1100)):
            with self.subTest(field=field, value=value[:80] if type(value) is str else value):
                item = {**base, field: value}
                if field == 'sourceId' and value is None:
                    item['sourceCode'] = None
                use([item])
                report = self.report()
                self.assertFalse(report['officialFullReady'])
                self.assertEqual(report['analysisPassedCases'], 33)
                self.assertEqual(report['performance']['sampleCount'], 40)
        capacity = {**base, 'sourceType': 'CAPACITY', 'sourceId': None, 'sourceCode': 'SYNTHETIC_WC_DAY',
                    'metricCode': 'available_hours', 'metricName': 'Synthetic capacity',
                    'evidenceKey': 'ERP:CAPACITY:SYNTHETIC_WC_DAY:available_hours', 'derivedFromJson': '[]'}
        use([capacity])
        self.assertTrue(self.report()['officialFullReady'], 'ERP aggregate sourceCode is a stable source without parents')
        for field in ('sourceCode', 'dataVersion', 'observedAt', 'metricValue', 'valueType', 'metricCode', 'metricName'):
            with self.subTest(capacity_missing=field):
                use([{**capacity, field: None}])
                self.assertFalse(self.report()['officialFullReady'], 'empty parents alone cannot prove a source')
        # Only synthetic field-shape coverage of the supplied cardinalities, never a database attestation.
        roots = []
        for i in range(253):
            kind = 'CAPACITY' if i < 116 else 'WORK_ORDER'
            source_id = None if i < 116 else str(9007199254795000 + i)
            code = f'SYNTHETIC_SOURCE_{i}'
            metric = 'available_hours' if i < 116 else 'planned_qty'
            roots.append({**base, 'id': str(9007199254790000 + i), 'sourceType': kind,
                          'sourceId': source_id, 'sourceCode': code, 'metricCode': metric,
                          'metricName': 'Synthetic root', 'derivedFromJson': '[]',
                          'evidenceKey': f'ERP:{kind}:{source_id or code}:{metric}'})
        batch = deepcopy(roots)
        for i in range(56):
            kind = 'SHORTAGE' if i % 2 else 'CALC'
            metric = 'max_gap' if i % 2 else 'risk_score'
            code = f'SYNTHETIC_DERIVED_{i}'
            batch.append({**base, 'id': str(9007199254800000 + i), 'sourceDomain': 'DERIVED',
                          'sourceType': kind, 'sourceId': None, 'sourceCode': code, 'metricCode': metric,
                          'metricName': 'Synthetic derived value', 'evidenceKey': f'DERIVED:{kind}:{code}:{metric}',
                          'derivedFromJson': json.dumps([item['evidenceKey'] for item in roots])})
        self.assertEqual(len(batch), 309)
        use(batch)
        self.assertTrue(self.report()['officialFullReady'], '116 null-ID ERP roots and 253-key derived lineage are valid')
        use([{**item, 'dataVersion': 'other-snapshot'} for item in batch])
        self.assertFalse(self.report()['officialFullReady'], 'internally consistent evidence must still bind the run snapshot')
        derived = {**base, 'id': '9007199254781001', 'sourceDomain': 'DERIVED', 'sourceType': 'CALC',
                   'sourceId': None, 'sourceCode': 'RISK', 'metricCode': 'risk_score', 'metricName': 'Synthetic risk',
                   'evidenceKey': 'DERIVED:CALC:RISK:risk_score', 'derivedFromJson': json.dumps([base['evidenceKey']])}
        rag = {**base, 'id': '9007199254782001', 'sourceDomain': 'RAG', 'sourceType': 'DOC',
               'sourceId': None, 'sourceCode': '9007199254783001', 'metricCode': 'content', 'metricName': 'Excerpt',
               'evidenceKey': 'RAG:DOC:9007199254783001:content', 'valueType': 'TEXT',
               'metricValue': 'Synthetic document excerpt, not field evidence', 'dataVersion': 'doc-v1',
               'observedAt': None, 'derivedFromJson': '[]', 'normative': 'Y',
               'displayPath': 'knowledge:101/document:102/chunk:9007199254783001'}
        use([base, derived, rag])
        self.assertTrue(self.report()['officialFullReady'], 'sourceCode and rooted parents replace sourceId')
        for parents in (None, '{}', '[]', '["missing"]', '[""]', json.dumps([derived['evidenceKey']]),
                        json.dumps([base['evidenceKey'], base['evidenceKey']]), json.dumps([rag['evidenceKey']])):
            with self.subTest(parents=parents):
                use([base, {**derived, 'derivedFromJson': parents}, rag])
                self.assertFalse(self.report()['officialFullReady'])
        use([base, {**derived, 'id': base['id']}, rag])
        self.assertFalse(self.report()['officialFullReady'], 'persisted evidence IDs must be unique')
        use([base, {**derived, 'dataVersion': 'other-snapshot'}, rag])
        self.assertFalse(self.report()['officialFullReady'], 'numeric parents must share the snapshot version')
        other = {**derived, 'id': '9007199254781002', 'sourceCode': 'OTHER',
                 'evidenceKey': 'DERIVED:CALC:OTHER:risk_score', 'derivedFromJson': json.dumps([derived['evidenceKey']])}
        use([base, {**derived, 'derivedFromJson': json.dumps([other['evidenceKey']])}, other])
        self.assertFalse(self.report()['officialFullReady'], 'cyclic lineage has no business root')
        use([base])
        self.assertTrue(self.report()['officialFullReady'])

    def test_malformed_expectations_and_scope_rejected(self):
        for field, value in (('expectedStatus', 'PASS'), ('requiredActions', ['AUTO_APPROVE']), ('expectedRiskLevel', None)):
            original = self.cases[0][field]; self.cases[0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.report()
            self.cases[0][field] = original
        for sha in ('HEAD', 'a' * 7, '', None):
            with self.subTest(sha=sha), self.assertRaises(ValueError):
                readiness.build_readiness(self.cases, self.expected, self.evidence,
                    tenant_id=TENANT, commit_sha=sha, build_id=BUILD)

    def test_schedule_native_readback_has_no_invented_source_type(self):
        for run in self.evidence['runs']:
            for entry in run.get('execution', {}).get('readbacks', []):
                if 'planId' in entry['row']:
                    entry['row'].pop('sourceType', None)
        self.assertTrue(self.report()['officialFullReady'])

    def test_unknown_native_status_is_not_echoed_as_untrusted_content(self):
        self.evidence['runs'][0]['detail']['data']['run']['status'] = 'UNTRUSTED_RESPONSE_CONTENT'
        report = self.report()
        self.assertEqual(report['cases'][0]['observedStatus'], 'UNKNOWN')
        self.assertNotIn('UNTRUSTED_RESPONSE_CONTENT', json.dumps(report))

    def test_unknown_approval_and_node_statuses_do_not_pass(self):
        action = self.evidence['runs'][5]['detail']['data']['actions'][0]
        node = self.evidence['runs'][0]['timeline']['data'][0]
        for row, key in ((action, 'approvalStatus'), (node, 'status')):
            with self.subTest(key=key):
                original = row[key]; row[key] = 'UNKNOWN'
                report = self.report()
                self.assertEqual(report['analysisPassedCases'], 33)
                self.assertFalse(report['performance']['signingEligible'])
                row[key] = original

    def test_duplicate_action_id_is_not_independent_persisted_evidence(self):
        actions = self.evidence['runs'][5]['detail']['data']['actions']
        actions[1]['id'] = actions[0]['id']
        self.assertEqual(self.report()['analysisPassedCases'], 33)

    def test_nested_result_json_rejects_duplicate_keys_and_nonfinite_values(self):
        row = self.evidence['runs'][0]['detail']['data']['run']
        original = row['resultJson']
        for tail in (', "riskLevel": "LOW"}', ', "extra": NaN}'):
            with self.subTest(tail=tail):
                row['resultJson'] = original[:-1] + tail
                self.assertEqual(self.report()['analysisPassedCases'], 33)

    def test_unknown_source_and_run_envelope_fields_fail_closed(self):
        for key in ('sources', 'runs'):
            with self.subTest(key=key):
                self.evidence[key][0]['verified'] = True
                self.assertFalse(self.report()['officialFullReady'])
                del self.evidence[key][0]['verified']

    def test_fact_selector_missing_snapshot_or_cross_tenant_never_falls_back(self):
        source = self.evidence['sources'][0]
        source['measurements']['tardinessDays']['path'] = '/mes/not-captured'
        with patch('ai_decision_runner.DecisionClient', side_effect=AssertionError('no client')):
            self.assertEqual(self.report()['analysableCases'], 39)

    def test_native_response_boolean_code_is_not_http_success(self):
        self.evidence['runs'][0]['evidence']['code'] = True
        self.assertFalse(self.report()['officialFullReady'])

    def test_cli_rejects_url_and_unc_input_before_opening(self):
        for name in ('https://invalid.example/cases.jsonl', '//server/share/cases.jsonl', r'\\server\share\cases.jsonl'):
            with self.subTest(name=name), patch('pathlib.Path.open', side_effect=AssertionError('must reject network path')), \
                 patch('sys.stdout', io.StringIO()) as stdout:
                status = readiness.main(['--cases', name, '--expected', str(BENCHMARK / 'expected-results.jsonl'),
                    '--evidence', '-', '--tenant-id', TENANT, '--commit-sha', SHA, '--build-id', BUILD])
                self.assertEqual(status, 2)
                self.assertEqual(json.loads(stdout.getvalue())['status'], 'INPUT_ERROR')

    def test_cli_missing_file_is_structured_error_and_never_falls_back(self):
        stdout = io.StringIO()
        with patch('sys.stdout', stdout):
            status = readiness.main(['--cases', str(HERE / 'definitely-absent-cases.jsonl'),
                '--expected', str(BENCHMARK / 'expected-results.jsonl'), '--evidence', '-',
                '--tenant-id', TENANT, '--commit-sha', SHA, '--build-id', BUILD])
        self.assertEqual(status, 2)
        report = json.loads(stdout.getvalue())
        self.assertEqual(report['status'], 'INPUT_ERROR')
        self.assertFalse(report['officialFullReady'])

    def test_cli_stdin_offline_report_does_not_write_or_create_client(self):
        stdout = io.StringIO()
        with patch('sys.stdin', io.StringIO(json.dumps(self.evidence))), patch('sys.stdout', stdout), \
             patch('pathlib.Path.write_text', side_effect=AssertionError('no writes')), \
             patch('ai_decision_runner.DecisionClient', side_effect=AssertionError('no client')):
            status = readiness.main(['--cases', str(BENCHMARK / 'cases.jsonl'),
                '--expected', str(BENCHMARK / 'expected-results.jsonl'), '--evidence', '-',
                '--tenant-id', TENANT, '--commit-sha', SHA, '--build-id', BUILD])
        self.assertEqual(status, 0)
        self.assertTrue(json.loads(stdout.getvalue())['officialFullReady'])

    def test_cli_duplicate_json_keys_and_nonfinite_values_are_rejected(self):
        for text in ('{"sources": [], "sources": [], "runs": []}', '{"sources": [], "runs": [], "bad": NaN}'):
            with self.subTest(text=text), patch('sys.stdin', io.StringIO(text)), patch('sys.stdout', io.StringIO()) as stdout:
                status = readiness.main(['--cases', str(BENCHMARK / 'cases.jsonl'),
                    '--expected', str(BENCHMARK / 'expected-results.jsonl'), '--evidence', '-',
                    '--tenant-id', TENANT, '--commit-sha', SHA, '--build-id', BUILD])
                self.assertEqual(status, 2)
                self.assertEqual(json.loads(stdout.getvalue())['status'], 'INPUT_ERROR')


if __name__ == '__main__':
    unittest.main()
