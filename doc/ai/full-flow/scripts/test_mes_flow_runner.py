import re
"""ERP 来源工单和流转卡的 MES 验收契约测试；校验失败即关闭通路。"""
import sys
import json
import os
import tempfile
from contextlib import redirect_stdout, redirect_stderr
from io import StringIO
from unittest.mock import patch
import unittest
from copy import deepcopy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import mes_flow_runner as mes_script

from mes_flow_runner import (
    MesFlowError,
    check_erp_origin,
    resolve_mes_order,
    plan_card_transition,
    poll_mes_order,
    expected_from_manifest,
    assert_routing_step_identity,
    run_mes_action, plan_sn_batch, plan_andon_call, plan_ipqc_inspection,
)


ERP = {
    'woId': '2104510684576272385', 'woNo': 'SCGD202609280001',
    'materialId': '1', 'plannedQty': '10.0000', 'woStatus': 'RELEASED',
}
MES = {
    'mesWoId': '2104510684576272386', 'erpWoId': ERP['woId'],
    'woNo': ERP['woNo'], 'materialId': ERP['materialId'],
    'plannedQty': '10.0000', 'woStatus': 'CREATED',
    'steps': [{'stepId': '81', 'mesWoId': '2104510684576272386', 'sequenceNum': 10,
               'processCode': 'P001'}],
}


class MesAcceptanceContractTest(unittest.TestCase):
    def test_manifest_and_local_intent_provide_real_quantity_without_hardcoding(self):
        manifest = {'tenantId': '000000', 'records': [{
            'tenantId': '000000', 'table': 'prd_work_order',
            'entityId': ERP['woId'], 'businessCode': ERP['woNo'],
            'parentRefs': {'planId': '19', 'materialId': '1'}
        }]}
        intent = {'tenantId': '000000', 'planId': '19', 'plannedQty': '10.0000'}
        self.assertEqual(expected_from_manifest(manifest, intent)['plannedQty'], '10.0000')
        with self.assertRaises(MesFlowError):
            expected_from_manifest(manifest, dict(intent, plannedQty='5.4292', planId='wrong'))
        with self.assertRaises(MesFlowError):
            expected_from_manifest(dict(manifest, tenantId='999999'), intent)

    def test_only_real_released_erp_order_can_be_used(self):
        self.assertEqual(check_erp_origin({'code': 200, 'data': ERP}, ERP)['woId'], ERP['woId'])
        for status in ('CREATED', 'CANCELLED', 'CLOSED'):
            with self.subTest(status=status), self.assertRaises(MesFlowError):
                check_erp_origin({'code': 200, 'data': dict(ERP, woStatus=status)}, ERP)
        with self.assertRaises(MesFlowError):
            check_erp_origin({'code': 200, 'data': dict(ERP, plannedQty='5.4292')}, ERP)

    def test_backend_small_integer_material_id_is_exact_and_safe(self):
        self.assertEqual(check_erp_origin({'code': 200, 'data': dict(ERP, materialId=1)}, ERP)['materialId'], 1)
        self.assertEqual(resolve_mes_order([dict(MES, materialId=1)], ERP)['materialId'], 1)
        with self.assertRaises(MesFlowError):
            check_erp_origin({'code': 200, 'data': dict(ERP, woId=2104510684576272385)}, ERP)

    def test_reject_missing_duplicate_or_ambiguous_mes_origin(self):
        self.assertEqual(resolve_mes_order([MES], ERP)['mesWoId'], MES['mesWoId'])
        for rows in ([], [MES, dict(MES, mesWoId='2104510684576272387')],
                     [dict(MES, erpWoId='99')], [dict(MES, plannedQty='5.4292')],
                     [dict(MES, woNo='WRONG')], [dict(MES, steps=[])]):
            with self.subTest(rows=rows), self.assertRaises(MesFlowError):
                resolve_mes_order(rows, ERP)

    def test_poll_has_explicit_attempt_limit_and_no_mutation(self):
        calls = []
        def lookup():
            calls.append('read')
            return [MES] if len(calls) == 3 else []
        result = poll_mes_order(lookup, ERP, attempts=3, sleep=lambda _: None)
        self.assertEqual(result['mesWoId'], MES['mesWoId'])
        self.assertEqual(calls, ['read'] * 3)
        with self.assertRaises(MesFlowError):
            poll_mes_order(lambda: [], ERP, attempts=2, sleep=lambda _: None)

    def test_mes_work_order_routing_steps_match_read_only_erp_source(self):
        source = [{'stepId': 10, 'sequenceNum': 10, 'processId': 18, 'processCode': 'P001'}]
        target = [{'stepId': '81', 'mesWoId': MES['mesWoId'], 'sequenceNum': 10,
                   'processId': 18, 'processCode': 'P001'}]
        self.assertEqual(assert_routing_step_identity(source, target, MES['mesWoId']), 1)
        with self.assertRaises(MesFlowError):
            assert_routing_step_identity(source, [dict(target[0], processCode='P999')], MES['mesWoId'])
        with self.assertRaises(MesFlowError):
            assert_routing_step_identity(source, target + target, MES['mesWoId'])

    def test_reuse_exact_existing_card_not_duplicate_and_reject_overrun(self):
        matching = {'cardId': '2104510684576272388', 'mesWoId': MES['mesWoId'],
                    'plannedQty': '10.0000', 'cardStatus': 'RELEASED',
                    'steps': [{'stepRecordId': '82', 'cardId': '2104510684576272388', 'sequenceNum': 10}]}
        created = plan_card_transition(dict(MES, woStatus='RELEASED'), [], '10')
        self.assertEqual(created['action'], 'create')
        self.assertEqual(plan_card_transition(dict(MES, woStatus='RELEASED'),
                        [matching], '10')['action'], 'reuse')
        for qty, cards in (('11', []), ('10', [dict(matching, plannedQty='9')]),
                           ('10', [matching, matching])):
            with self.subTest(qty=qty, cards=cards), self.assertRaises(MesFlowError):
                plan_card_transition(dict(MES, woStatus='RELEASED'), cards, qty)



class SidecarPlanTest(unittest.TestCase):
    """每次修改数据前，都必须取得可信的只读 API 事实。"""
    @staticmethod
    def _sql_without_documentation(sql):
        """仅处理固定只读断言样本的文档注释，不作为通用 SQL 解析器或执行授权。"""
        return re.sub(r"/\*(?![!+]).*?\*/", "", sql, flags=re.S)

    def test_sql_documentation_filter_preserves_executable_content(self):
        select = "SELECT ?;"
        self.assertEqual(self._sql_without_documentation("/** 只读核验说明。 */\n" + select).strip(), select)
        for executable in ("UPDATE `sample`;", "/*!50700 UPDATE `sample` SET `version` = 2 */;", "/*+ 提示 */ SELECT ?;"):
            self.assertEqual(self._sql_without_documentation(executable), executable)

    def setUp(self):
        self.client = FakeMesApi()
        self.expected = dict(ERP, tenantId='000000', planId='19')

    def test_no_request_without_released_erp_exactly_one_mes_and_tenant(self):
        for defect in ('erp', 'missing', 'duplicate', 'tenant', 'routing'):
            with self.subTest(defect=defect):
                api = FakeMesApi()
                if defect == 'erp': api.erp['woStatus'] = 'CREATED'
                if defect == 'missing': api.mes_rows = []
                if defect == 'duplicate': api.mes_rows.append(dict(api.mes))
                if defect == 'tenant': api.tenant = '999999'
                if defect == 'routing': api.routing[0]['processCode'] = 'OTHER'
                writes = []
                with self.assertRaises(MesFlowError):
                    run_mes_action(api, self.expected, 'release-wo', execute=True, sender=lambda *a: writes.append(a))
                self.assertEqual(writes, [])

    def test_explicit_flag_and_sender_required_before_write(self):
        self.assertEqual(run_mes_action(self.client, self.expected, 'release-wo')['outcome'], 'dry-run')
        self.assertEqual(self.client.writes, [])
        with self.assertRaises(MesFlowError):
            run_mes_action(self.client, self.expected, 'release-wo', execute=True)
        self.assertEqual(self.client.writes, [])

    def test_release_rechecks_new_status_and_recovers_without_repeat_put(self):
        result = run_mes_action(self.client, self.expected, 'release-wo', execute=True, sender=self.client.send)
        self.assertEqual(result['outcome'], 'applied')
        self.assertEqual(self.client.mes['woStatus'], 'RELEASED')
        self.assertEqual(run_mes_action(self.client, self.expected, 'release-wo', execute=True, sender=self.client.send)['outcome'], 'reused')
        self.assertEqual(len(self.client.writes), 1)

    def test_no_write_when_identity_or_quantity_is_wrong_or_pagination_is_ambiguous(self):
        for field, value in (('woNo', 'WRONG'), ('plannedQty', '5.4292'), ('mesWoId', 2104510684576272386)):
            with self.subTest(field=field):
                api = FakeMesApi()
                api.mes[field] = value
                with self.assertRaises(MesFlowError):
                    run_mes_action(api, self.expected, 'release-wo', execute=True, sender=api.send)
                self.assertFalse(api.writes)
        api = FakeMesApi()
        api.mes_total = 101
        with self.assertRaises(MesFlowError):
            run_mes_action(api, self.expected, 'release-wo', execute=True, sender=api.send)

    def test_card_allocation_requires_exact_plan_and_reuses_single_card(self):
        self.client.mes['woStatus'] = 'RELEASED'
        planned = run_mes_action(self.client, self.expected, 'create-card', {'quantity': '10'})
        self.assertEqual(planned['outcome'], 'dry-run')
        with self.assertRaises(MesFlowError):
            run_mes_action(self.client, self.expected, 'create-card', {'quantity': '11'}, execute=True, sender=self.client.send)
        self.assertEqual(run_mes_action(self.client, self.expected, 'create-card', {'quantity': '10'}, execute=True, sender=self.client.send)['outcome'], 'applied')
        self.assertEqual(run_mes_action(self.client, self.expected, 'create-card', {'quantity': '10'}, execute=True, sender=self.client.send)['outcome'], 'reused')
        self.assertEqual(len(self.client.writes), 1)

    def test_dispatch_requires_real_operator_step_order_and_no_duplicate(self):
        self.client.ready_card()
        for options in ({'operatorId': '0'}, {'operatorId': 2104510684576272385}, {'operatorId': '42', 'quantity': '11'}):
            with self.subTest(options=options), self.assertRaises(MesFlowError):
                run_mes_action(self.client, self.expected, 'dispatch', options, execute=True, sender=self.client.send)
        self.assertEqual(run_mes_action(self.client, self.expected, 'dispatch', {'operatorId': '42', 'stepRecordId': '82'}, execute=True, sender=self.client.send)['outcome'], 'applied')
        self.assertEqual(run_mes_action(self.client, self.expected, 'dispatch', {'operatorId': '42', 'stepRecordId': '82'}, execute=True, sender=self.client.send)['outcome'], 'reused')
        self.assertEqual(len(self.client.writes), 1)

    def test_start_dispatch_and_entry_modes_are_exclusive(self):
        self.client.ready_card()
        self.client.ready_dispatch()
        self.assertEqual(run_mes_action(self.client, self.expected, 'start-dispatch', {'dispatchId': self.client.dispatch['dispatchId']}, execute=True, sender=self.client.send)['outcome'], 'applied')
        with self.assertRaises(MesFlowError):
            run_mes_action(self.client, self.expected, 'entry', {'operatorId': '42'}, execute=True, sender=self.client.send)

    def test_bad_readback_does_not_claim_success_or_retry_a_mutation(self):
        api = FakeMesApi()
        sender = lambda *args, **kwargs: {'code': 200}
        with self.assertRaises(MesFlowError):
            run_mes_action(api, self.expected, 'release-wo', execute=True, sender=sender)

    def test_bad_report_quantities_stop_before_write(self):
        self.client.ready_card()
        self.client.ready_dispatch()
        self.client.dispatch['dispatchStatus'] = 'IN_PROGRESS'
        self.client.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'
        for good, defect, scrap in (('11', '0', '0'), ('-1', '0', '0'), ('5', '4', '2'), ('0', '0', '0')):
            with self.subTest(good=good, defect=defect, scrap=scrap), self.assertRaises(MesFlowError):
                run_mes_action(self.client, self.expected, 'finish-dispatch', {'goodQty': good, 'defectQty': defect, 'scrapQty': scrap}, execute=True, sender=self.client.send)
        self.assertFalse(self.client.writes)

    def test_sn_integral_batch_exact_ids_and_no_overproduction(self):
        self.client.ready_card()
        self.assertEqual(plan_sn_batch(self.client.card, [], '10')['quantity'], 10)
        with self.assertRaises(MesFlowError): plan_sn_batch(self.client.card, [], '10.5')
        existing = [{'snId': '2104510684576272390', 'cardId': self.client.card['cardId'], 'mesWoId': MES['mesWoId'], 'materialId': ERP['materialId'], 'snCode': f'SN-{self.client.card["cardNo"]}-0001', 'snStatus': 'IN_PRODUCTION'}]
        with self.assertRaises(MesFlowError): plan_sn_batch(self.client.card, existing, '10')

    def test_scrapped_sn_cannot_be_reused_as_valid_batch(self):
        card = {'cardId': '2104510684576272388', 'cardNo': 'LZK202609280001',
                'mesWoId': MES['mesWoId'], 'materialId': ERP['materialId'], 'plannedQty': '1'}
        sn = {'snId': '2104510684576272390', 'snCode': 'SN-LZK202609280001-0001',
              'cardId': card['cardId'], 'mesWoId': card['mesWoId'],
              'materialId': card['materialId'], 'snStatus': 'SCRAPPED'}
        with self.assertRaises(MesFlowError):
            plan_sn_batch(card, [sn], '1')

    def test_andon_requires_real_reason_and_rejects_duplicate_active_calls(self):
        self.client.ready_card()
        with self.assertRaises(MesFlowError):
            run_mes_action(self.client, self.expected, 'andon-call', {'callType': 'QUALITY', 'callReason': ''}, execute=True, sender=self.client.send)
        self.assertEqual(plan_andon_call(self.client, self.client.mes, self.client.card, 'QUALITY', '现场焊点异常')['action'], 'call')
        self.client.andons = [{'andonId': '2104510684576272392', 'mesWoId': MES['mesWoId'], 'cardId': self.client.card['cardId'], 'stepRecordId': '82', 'callType': 'QUALITY', 'callReason': '现场焊点异常', 'andonStatus': 'CALLED', 'source': 'MANUAL', 'notifyTime': '2026-09-29 09:00:00', 'callerId': '42'}]
        self.assertEqual(plan_andon_call(self.client, self.client.mes, self.client.card, 'QUALITY', '现场焊点异常')['action'], 'reuse')
        with self.assertRaises(MesFlowError):
            plan_andon_call(self.client, self.client.mes, self.client.card, 'MATERIAL', '材料异常')

    def test_qc_inspection_cannot_fabricate_measurements(self):
        self.client.ready_card()
        ipqc = {'ipqcId': '2104510684576272393', 'mesWoId': MES['mesWoId'], 'cardId': self.client.card['cardId'], 'stepRecordId': '82', 'processId': '18', 'materialId': ERP['materialId'], 'status': 'PENDING', 'sampleQty': '10'}
        with self.assertRaises(MesFlowError):
            plan_ipqc_inspection(ipqc, [], {'result': 'UNQUALIFIED'})
        with self.assertRaises(MesFlowError):
            plan_ipqc_inspection(ipqc, [{'detailId': '21', 'checkId': ipqc['ipqcId']}], {'measurements': []})

    def test_sql_assertions_contain_only_bound_read_queries(self):
        from pathlib import Path
        sql = (Path(__file__).resolve().parents[1] / 'assertions' / 'mes_assertions.sql').read_text(encoding='utf-8')
        sql = self._sql_without_documentation(sql)
        statements = [line.strip().upper() for line in sql.splitlines() if line.strip() and not line.strip().startswith('--')]
        self.assertGreaterEqual(len(statements), 6)
        self.assertTrue(all(line.startswith('SELECT ') and '?' in line for line in statements))
        self.assertIn('ERP_WO_ID', sql.upper())


class FakeMesApi:
    def __init__(self):
        self.erp = dict(ERP, planId='19')
        self.mes = dict(MES, routingId='18', steps=[dict(MES['steps'][0], processId='18')])
        self.mes_rows = [self.mes]
        self.mes_total = 1
        self.routing = [{'stepId': '10', 'sequenceNum': 10, 'processId': '18', 'processCode': 'P001'}]
        self.tenant = '000000'
        self.operator = {'userId': '42', 'tenantId': '000000', 'status': '0', 'nickName': '车间操作员'}
        self.cards = []
        self.card = None
        self.dispatch = None
        self.andons = []
        self.writes = []

    def ready_card(self):
        self.mes['woStatus'] = 'RELEASED'
        self.card = {'cardId': '2104510684576272388', 'cardNo': 'LZK202609280001',
                     'mesWoId': self.mes['mesWoId'], 'plannedQty': '10', 'cardStatus': 'RELEASED',
                     'materialId': ERP['materialId'], 'steps': [{'stepRecordId': '82', 'cardId': '2104510684576272388', 'mesWoId': self.mes['mesWoId'], 'woStepId': '81', 'sequenceNum': 10, 'processId': '18', 'processCode': 'P001', 'plannedQty': '10', 'stepStatus': 'PENDING'}]}
        self.cards = [self.card]

    def ready_dispatch(self):
        self.dispatch = {'dispatchId': '2104510684576272389', 'mesWoId': self.mes['mesWoId'], 'cardId': self.card['cardId'], 'stepRecordId': '82', 'operatorId': '42', 'plannedQty': '10', 'dispatchStatus': 'PENDING'}

    def get(self, path, query=None):
        if path == '/system/user/getInfo': return {'code': 200, 'data': {'user': {'tenantId': self.tenant}}}
        if path.startswith('/system/user/'): return {'code': 200, 'data': {'user': self.operator}}
        if path.startswith('/erp/prd/work/'): return {'code': 200, 'data': dict(self.erp)}
        if path.startswith('/erp/bd/routing/steps/'): return {'code': 200, 'data': self.routing}
        if path == '/mes/pro/work/list': return {'code': 200, 'rows': deepcopy(self.mes_rows), 'total': self.mes_total}
        if path.startswith('/mes/pro/work/'): return {'code': 200, 'data': deepcopy(self.mes)}
        if path.startswith('/mes/pro/flow-card/by-wo/'): return {'code': 200, 'data': deepcopy(self.cards)}
        if path.startswith('/mes/pro/flow-card/'): return {'code': 200, 'data': deepcopy(self.card)}
        if path.startswith('/mes/pro/dispatch/by-step/'): return {'code': 200, 'data': [self.dispatch] if self.dispatch else []}
        if path.startswith('/mes/pro/dispatch/'): return {'code': 200, 'data': deepcopy(self.dispatch)}
        if path == '/mes/pro/andon/list': return {'code': 200, 'rows': list(self.andons), 'total': len(self.andons)}
        if path == '/mes/pro/sn/list': return {'code': 200, 'rows': list(self.sn_rows), 'total': self.sn_total}
        if path.startswith('/mes/pro/andon/'): return {'code': 200, 'data': dict(self.andon)}
        if path.startswith('/mes/qc/ipqc/') and path.endswith('/details'): return {'code': 200, 'data': list(self.qc_details)}
        if path.startswith('/mes/qc/ipqc/'): return {'code': 200, 'data': dict(self.ipqc)}
        if path.startswith('/mes/dc/collection-point/'): return {'code': 200, 'data': dict(self.point)}
        if path == '/mes/dc/data-record/list': return {'code': 200, 'rows': list(self.records), 'total': len(self.records)}
        if path.startswith('/mes/dc/data-record/'): return {'code': 200, 'data': dict(self.record)}
        raise AssertionError(f'unexpected GET: {path}')

    def send(self, method, path, *, query=None, payload=None):
        self.writes.append((method, path, query, payload))
        if path.startswith('/mes/pro/work/release/'): self.mes['woStatus'] = 'RELEASED'; return {'code': 200}
        if path == '/mes/pro/flow-card': self.ready_card(); self.card['cardStatus'] = 'CREATED'; return {'code': 200, 'data': deepcopy(self.card)}
        if path == '/mes/pro/dispatch': self.ready_dispatch(); return {'code': 200, 'data': deepcopy(self.dispatch)}
        if path.startswith('/mes/pro/dispatch/start/'): self.dispatch['dispatchStatus'] = 'IN_PROGRESS'; self.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'; self.card['cardStatus'] = 'IN_PROGRESS'; self.mes['woStatus'] = 'IN_PROGRESS'; return {'code': 200}
        if path.startswith('/mes/pro/dispatch/complete/'):
            self.dispatch.update(dispatchStatus='COMPLETED', goodQty=query['goodQty'], defectQty=query['defectQty'], scrapQty=query['scrapQty'])
            self.card['steps'][0].update(stepStatus='COMPLETED', goodQty=query['goodQty'], defectQty=query['defectQty'], scrapQty=query['scrapQty'])
            self.card.update(cardStatus='COMPLETED', completedQty=query['goodQty'], scrapQty=query['scrapQty'])
            self.mes['woStatus'] = 'COMPLETED'
            self.mes['sourceType'] = 'ERP'
            self.mes['actualEndDate'] = '2026-09-29 11:18:00'
            self.mes['completedQty'] = query['goodQty']
            self.mes['scrapQty'] = query['scrapQty']
            return {'code': 200}
        if path == '/mes/pro/flow-card/entry':
            self.card['steps'][0].update(stepStatus='IN_PROGRESS', entryCheckResult='PASS', operatorId=payload['operatorId'])
            self.card['cardStatus'] = 'IN_PROGRESS'
            self.mes['woStatus'] = 'IN_PROGRESS'
            return {'code': 200, 'data': {'passed': True}}
        if path == '/mes/pro/flow-card/exit':
            self.card['steps'][0].update(stepStatus='COMPLETED', exitCheckResult='PASS', goodQty=payload['goodQty'], defectQty=payload['defectQty'], scrapQty=payload['scrapQty'])
            self.card.update(cardStatus='COMPLETED', completedQty=payload['goodQty'], scrapQty=payload['scrapQty'])
            self.mes['woStatus'] = 'COMPLETED'
            self.mes['sourceType'] = 'ERP'
            self.mes['actualEndDate'] = '2026-09-29 11:18:00'
            self.mes['completedQty'] = payload['goodQty']
            self.mes['scrapQty'] = payload['scrapQty']
            return {'code': 200, 'data': {'passed': True}}
        if path == '/mes/pro/sn/generate':
            self.sn_rows = [{'snId': str(2104510684576272400 + seq), 'snCode': f'SN-{self.card["cardNo"]}-{seq:04d}', 'cardId': self.card['cardId'], 'mesWoId': self.mes['mesWoId'], 'materialId': self.card['materialId'], 'snStatus': 'IN_PRODUCTION'} for seq in range(1, int(query['quantity']) + 1)]
            self.sn_total = len(self.sn_rows)
            return {'code': 200, 'data': self.sn_rows}
        if path == '/mes/dc/data-record':
            self.record = {'recordId': '2104510684576272391', **payload}
            self.records = [self.record]
            return {'code': 200, 'data': dict(self.record)}
        if path == '/mes/pro/andon/call':
            self.andon = {'andonId': '2104510684576272392', 'mesWoId': query['mesWoId'], 'cardId': query['cardId'], 'stepRecordId': query['stepRecordId'], 'callType': query['callType'], 'callReason': query['callReason'], 'source': 'MANUAL', 'andonStatus': 'CALLED', 'notifyTime': '2026-09-29 09:00:00', 'callerId': '42', 'callTime': '2026-09-29 08:59:00'}
            self.andons = [self.andon]
            return {'code': 200, 'data': dict(self.andon)}
        if path.startswith('/mes/pro/andon/respond/'):
            self.andon.update(andonStatus='RESPONDED', responderId='42', respondTime='2026-09-29 09:01:00')
            return {'code': 200}
        if path.startswith('/mes/pro/andon/resolve/'):
            self.andon.update(andonStatus='RESOLVED', resolveTime='2026-09-29 09:02:00')
            return {'code': 200}
        if path.startswith('/mes/qc/ipqc/inspect-details/'):
            self.ipqc.update(status='COMPLETED', inspectResult='QUALIFIED', inspectTime='2026-09-29 09:05:00', inspectorId='42')
            self.qc_details[0]['measuredValue'] = payload['details'][0]['measuredValue']
            return {'code': 200}
        raise AssertionError(f'unexpected write: {path}')


class SidecarEdgeCaseTest(unittest.TestCase):
    def setUp(self):
        self.api = FakeMesApi()
        self.expected = dict(ERP, tenantId='000000', planId='19')
        self.api.ready_card()

    def run_action(self, action, options=None, *, sender=None):
        params = dict(options or {})
        if action in ('dispatch', 'entry', 'exit', 'collect'):
            params.setdefault('stepRecordId', '82')
        return run_mes_action(self.api, self.expected, action, params, execute=True, sender=sender or self.api.send)

    def test_dispatch_rejects_nonexistent_or_inactive_human_operator(self):
        for user in (None, {'userId': '42', 'status': '1', 'tenantId': '000000'},
                     {'userId': '42', 'status': '0', 'tenantId': '999999'}):
            with self.subTest(user=user):
                self.api.operator = user
                with self.assertRaises(MesFlowError):
                    self.run_action('dispatch', {'stepRecordId': '82', 'operatorId': '42'})
                self.assertFalse(self.api.writes)

    def test_no_step_action_may_guess_current_step_on_resume(self):
        self.api.point = {'pointId': '21', 'processId': '18', 'dataType': 'NUMERIC', 'status': 'ACTIVE'}
        self.api.records = []
        cases = [('dispatch', {'operatorId': '42'}),
                 ('entry', {'operatorId': '42'}),
                 ('collect', {'pointId': '21', 'numericValue': '187.2', 'collectTime': '2026-09-29 09:00:00'})]
        for action, opts in cases:
            with self.subTest(action=action), self.assertRaises(MesFlowError):
                run_mes_action(self.api, self.expected, action, opts, execute=True, sender=self.api.send)
        self.assertFalse(self.api.writes)

    def test_bad_card_step_origin_rejected_before_mutation(self):
        self.api.card['steps'][0]['woStepId'] = '9'
        with self.assertRaises(MesFlowError): self.run_action('release-card')
        self.assertEqual(self.api.writes, [])

    def test_does_not_send_unsupported_action_even_with_valid_origin(self):
        with self.assertRaises(MesFlowError): self.run_action('ipqc-create')
        self.assertEqual(self.api.writes, [])

    def test_requires_real_dispatch_id_not_guess_first_row(self):
        self.api.ready_dispatch()
        with self.assertRaises(MesFlowError): self.run_action('start-dispatch')
        self.assertEqual(self.api.writes, [])

    def test_finish_dispatch_stops_if_missing_or_incomplete_readback(self):
        self.api.ready_dispatch()
        self.api.dispatch['dispatchStatus'] = 'IN_PROGRESS'
        self.api.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'
        with self.assertRaises(MesFlowError):
            self.run_action('finish-dispatch', {'dispatchId': self.api.dispatch['dispatchId'], 'goodQty': '10'}, sender=lambda *a, **k: {'code': 200})
        self.assertFalse(self.api.writes)

    def test_dispatch_defect_needs_separate_ncr_path_before_real_report(self):
        self.api.ready_dispatch()
        self.api.dispatch['dispatchStatus'] = 'IN_PROGRESS'
        self.api.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'
        opts = {'dispatchId': self.api.dispatch['dispatchId'], 'goodQty': '9', 'defectQty': '1'}
        with self.assertRaises(MesFlowError):
            self.run_action('finish-dispatch', opts)
        self.assertFalse(self.api.writes)

    def test_sn_requires_complete_list_and_actual_readback(self):
        self.api.sn_rows = []
        self.api.sn_total = 0
        with self.assertRaises(MesFlowError):
            self.run_action('sn-generate', {'quantity': '10'}, sender=lambda *a, **k: {'code': 200, 'data': []})
        self.assertEqual(self.api.writes, [])

    def test_entry_requires_real_operator_and_passed_readback(self):
        with self.assertRaises(MesFlowError):
            self.run_action('entry', {'operatorId': '42'}, sender=lambda *a, **k: {'code': 200, 'data': {'passed': False}})
        self.assertEqual(self.api.writes, [])

    def test_exit_exact_quantities_and_failure_does_not_claim_success(self):
        self.api.card['steps'][0].update(stepStatus='IN_PROGRESS', entryCheckResult='PASS')
        with self.assertRaises(MesFlowError):
            self.run_action('exit', {'goodQty': '9', 'defectQty': '0', 'scrapQty': '0'})
        with self.assertRaises(MesFlowError):
            self.run_action('exit', {'goodQty': '10'}, sender=lambda *a, **k: {'code': 200, 'data': {'passed': False}})
        self.assertEqual(self.api.writes, [])

    def test_collect_only_real_point_and_source_and_exact_snapshot(self):
        self.api.point = {'pointId': '21', 'processId': '18', 'dataType': 'NUMERIC', 'status': 'ACTIVE'}
        self.api.records = []
        intent = {'pointId': '21', 'numericValue': '187.2', 'collectTime': '2026-09-29 09:00:00'}
        self.assertEqual(run_mes_action(self.api, self.expected, 'collect', dict(intent, stepRecordId='82'))['outcome'], 'dry-run')
        for change in ({'source': 'IoT'}, {'numericValue': '-1'}, {'pointId': 2104510684576272385}, {'collectTime': ''}):
            with self.subTest(change=change), self.assertRaises(MesFlowError):
                self.run_action('collect', {**intent, **change})
        self.assertFalse(self.api.writes)

    def test_quality_inspection_requires_all_details_and_refuses_fake_values(self):
        self.api.ipqc = {'ipqcId': '2104510684576272393', 'mesWoId': MES['mesWoId'], 'cardId': self.api.card['cardId'], 'stepRecordId': '82', 'processId': '18', 'materialId': ERP['materialId'], 'status': 'PENDING', 'sampleQty': '10'}
        self.api.qc_details = [{'detailId': '21', 'checkId': self.api.ipqc['ipqcId'], 'measuredValue': None}]
        with self.assertRaises(MesFlowError):
            self.run_action('ipqc-inspect', {'ipqcId': self.api.ipqc['ipqcId'], 'measurements': [{'detailId': '22', 'measuredValue': '1.1'}]})
        self.assertFalse(self.api.writes)

    def test_andon_response_must_have_audited_operator_and_resolve_time(self):
        self.api.andon = {'andonId': '2104510684576272392', 'mesWoId': MES['mesWoId'], 'cardId': self.api.card['cardId'], 'source': 'MANUAL', 'andonStatus': 'CALLED'}
        with self.assertRaises(MesFlowError):
            self.run_action('andon-respond', {'andonId': self.api.andon['andonId']}, sender=lambda *a, **k: {'code': 200})
        self.assertEqual(self.api.writes, [])
    def test_finished_dispatch_readback_and_replay_are_single_write(self):
        self.api.ready_dispatch()
        self.api.dispatch['dispatchStatus'] = 'IN_PROGRESS'
        self.api.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'
        opts = {'dispatchId': self.api.dispatch['dispatchId'], 'goodQty': '10'}
        self.assertEqual(self.run_action('finish-dispatch', opts)['outcome'], 'applied')
        self.assertEqual(self.run_action('finish-dispatch', opts)['outcome'], 'reused')
        self.assertEqual(len(self.api.writes), 1)

    def test_entry_then_exit_use_real_result_and_do_not_duplicate(self):
        opts = {'operatorId': '42'}
        self.assertEqual(self.run_action('entry', opts)['outcome'], 'applied')
        self.assertEqual(self.run_action('entry', opts)['outcome'], 'reused')
        self.assertEqual(self.run_action('exit', {'goodQty': '10'})['outcome'], 'applied')
        self.assertEqual(self.run_action('exit', {'goodQty': '10'})['outcome'], 'reused')
        self.assertEqual(len(self.api.writes), 2)

    def test_sn_generation_and_manual_collection_resume_from_get(self):
        self.api.sn_rows = []
        self.api.sn_total = 0
        self.assertEqual(self.run_action('sn-generate', {'quantity': '10'})['outcome'], 'applied')
        self.assertEqual(self.run_action('sn-generate', {'quantity': '10'})['outcome'], 'reused')
        self.api.point = {'pointId': '21', 'processId': '18', 'dataType': 'NUMERIC', 'status': 'ACTIVE'}
        self.api.records = []
        opts = {'pointId': '21', 'numericValue': '187.2', 'collectTime': '2026-09-29 09:00:00'}
        self.assertEqual(self.run_action('collect', opts)['outcome'], 'applied')
        self.assertEqual(self.run_action('collect', opts)['outcome'], 'reused')
        self.assertEqual(len(self.api.writes), 2)

    def test_andon_full_audited_lifecycle_requires_no_duplicate_calls(self):
        opts = {'callType': 'QUALITY', 'callReason': '现场焊点异常'}
        self.assertEqual(self.run_action('andon-call', opts)['outcome'], 'applied')
        self.assertEqual(self.run_action('andon-call', opts)['outcome'], 'reused')
        self.assertEqual(self.run_action('andon-respond', {'andonId': self.api.andon['andonId']})['outcome'], 'applied')
        self.assertEqual(self.run_action('andon-resolve', {'andonId': self.api.andon['andonId'], 'remark': '返修台位复检完成'})['outcome'], 'applied')
        self.assertEqual(len(self.api.writes), 3)

    def test_ipqc_after_completed_card_can_be_inspected_from_prior_step(self):
        self.api.card.update(cardStatus='COMPLETED', completedQty='10', scrapQty='0')
        self.api.card['steps'][0].update(stepStatus='COMPLETED', goodQty='10', scrapQty='0')
        self.api.mes.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                            sourceType='ERP', actualEndDate='2026-09-29 11:18:00')
        self.api.ipqc = {'ipqcId': '2104510684576272393', 'mesWoId': MES['mesWoId'], 'cardId': self.api.card['cardId'], 'stepRecordId': '82', 'processId': '18', 'materialId': ERP['materialId'], 'status': 'PENDING', 'sampleQty': '10'}
        self.api.qc_details = [{'detailId': '21', 'checkId': self.api.ipqc['ipqcId'], 'measuredValue': None}]
        opts = {'ipqcId': self.api.ipqc['ipqcId'], 'measurements': [{'detailId': '21', 'measuredValue': '1.1'}]}
        self.assertEqual(self.run_action('ipqc-inspect', opts)['outcome'], 'applied')
        self.assertEqual(self.run_action('ipqc-inspect', opts)['outcome'], 'reused')
        self.assertEqual(len(self.api.writes), 1)

    def test_reuse_completed_card_does_not_send_second_creation(self):
        self.api.card.update(cardStatus='COMPLETED', completedQty='10', scrapQty='0')
        self.api.mes.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                            sourceType='ERP', actualEndDate='2026-09-29 11:18:00')
        self.api.card['steps'][0].update(stepStatus='COMPLETED', goodQty='10', scrapQty='0')
        self.assertEqual(self.run_action('create-card', {'quantity': '10'})['outcome'], 'reused')
        self.assertFalse(self.api.writes)

    def test_completed_order_must_not_reuse_created_card(self):
        self.api.ready_card()
        self.api.mes['woStatus'] = 'COMPLETED'
        self.api.card['cardStatus'] = 'CREATED'
        with self.assertRaises(MesFlowError):
            self.run_action('create-card', {'quantity': '10'})
        self.assertFalse(self.api.writes)

    def test_andon_without_first_notification_cannot_be_reused_as_success(self):
        self.api.andons = [{'andonId': '2104510684576272392', 'mesWoId': MES['mesWoId'],
            'cardId': self.api.card['cardId'], 'stepRecordId': '82', 'callType': 'QUALITY',
            'callReason': '现场焊点异常', 'source': 'MANUAL', 'andonStatus': 'CALLED'}]
        with self.assertRaises(MesFlowError):
            self.run_action('andon-call', {'callType': 'QUALITY', 'callReason': '现场焊点异常'})

    def test_andon_from_other_step_cannot_be_reused(self):
        self.api.andons = [{'andonId': '2104510684576272392', 'mesWoId': MES['mesWoId'],
            'cardId': self.api.card['cardId'], 'stepRecordId': '9', 'callType': 'QUALITY',
            'callReason': '现场焊点异常', 'source': 'MANUAL', 'andonStatus': 'CALLED'}]
        with self.assertRaises(MesFlowError):
            self.run_action('andon-call', {'callType': 'QUALITY', 'callReason': '现场焊点异常'})
        self.assertFalse(self.api.writes)

    def test_final_card_report_totals_must_match_step(self):
        self.api.ready_dispatch()
        self.api.dispatch['dispatchStatus'] = 'IN_PROGRESS'
        self.api.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'
        def wrong_total(method, path, *, query=None, payload=None):
            result = self.api.send(method, path, query=query, payload=payload)
            self.api.card['completedQty'] = '9'
            return result
        with self.assertRaises(MesFlowError):
            self.run_action('finish-dispatch', {'dispatchId': self.api.dispatch['dispatchId'], 'goodQty': '10'}, sender=wrong_total)
        self.assertEqual(len(self.api.writes), 1)

    def test_ipqc_record_must_match_exact_routing_step_process_and_material(self):
        self.api.ipqc = {'ipqcId': '2104510684576272393', 'mesWoId': MES['mesWoId'], 'cardId': self.api.card['cardId'], 'stepRecordId': '82', 'processId': '18', 'materialId': ERP['materialId'], 'status': 'PENDING'}
        self.api.qc_details = [{'detailId': '21', 'checkId': self.api.ipqc['ipqcId']}]
        options = {'ipqcId': self.api.ipqc['ipqcId'], 'measurements': [{'detailId': '21', 'measuredValue': '1.1'}]}
        for key, wrong in (('stepRecordId', '9'), ('processId', '9'), ('materialId', '9')):
            self.api.ipqc[key] = wrong
            with self.subTest(field=key), self.assertRaises(MesFlowError):
                self.run_action('ipqc-inspect', options)
            self.api.ipqc[key] = '82' if key == 'stepRecordId' else ('18' if key == 'processId' else ERP['materialId'])
        self.assertFalse(self.api.writes)

    def test_ipqc_detail_wrong_owner_refuses_submitted_measurements(self):
        ipqc = {'ipqcId': '2104510684576272393', 'status': 'PENDING'}
        with self.assertRaises(MesFlowError):
            plan_ipqc_inspection(ipqc, [{'detailId': '21', 'checkId': '999'}],
                                 {'measurements': [{'detailId': '21', 'measuredValue': '1.1'}]})

    def test_ipqc_only_measured_template_details_can_be_submitted(self):
        self.api.ipqc = {'ipqcId': '2104510684576272393', 'mesWoId': MES['mesWoId'], 'cardId': self.api.card['cardId'], 'stepRecordId': '82', 'processId': '18', 'materialId': ERP['materialId'], 'status': 'PENDING', 'sampleQty': '10'}
        self.api.qc_details = [{'detailId': '21', 'checkId': self.api.ipqc['ipqcId'], 'measuredValue': None}]
        opts = {'ipqcId': self.api.ipqc['ipqcId'], 'measurements': [{'detailId': '21', 'measuredValue': '1.1'}]}
        self.assertEqual(self.run_action('ipqc-inspect', opts)['outcome'], 'applied')
        self.assertEqual(self.run_action('ipqc-inspect', opts)['outcome'], 'reused')
        self.assertEqual(len(self.api.writes), 1)


class MesCliSafetyTest(unittest.TestCase):
    def test_direct_transport_cannot_write_without_verified_transition(self):
        class TrapClient:
            def _build_request(self, *args):
                raise AssertionError('request construction must not occur before guard')
        with self.assertRaises(MesFlowError):
            mes_script.MesWriteTransport(TrapClient()).send('PUT', '/mes/pro/work/release/81')

    def test_default_dry_run_and_explicit_ack_guard(self):
        api = FakeMesApi()
        manifest = {'tenantId': '000000', 'records': [{'table': 'prd_work_order',
            'entityId': ERP['woId'], 'businessCode': ERP['woNo'], 'tenantId': '000000',
            'parentRefs': {'materialId': '1', 'planId': '19'}}]}
        intent = {'tenantId': '000000', 'planId': '19', 'plannedQty': '10'}
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
            (root / 'work-order-manual-intent.json').write_text(json.dumps(intent), encoding='utf-8')
            with patch.object(mes_script, 'ApiClient', return_value=api), \
                 patch.dict(os.environ, {'NEXUS_FULL_FLOW_TOKEN': 'only-process-token', 'NEXUS_FULL_FLOW_CLIENT_ID': 'only-process-client'}):
                def run(*argv):
                    stdout, stderr = StringIO(), StringIO()
                    with patch.object(sys, 'argv', ['mes_flow_runner.py', '--manifest', str(root / 'manifest.json'), *argv]), \
                         redirect_stdout(stdout), redirect_stderr(stderr):
                        code = mes_script.main()
                    return code, stdout.getvalue(), stderr.getvalue()
                code, output, error = run('--action', 'release-wo')
                self.assertEqual((code, json.loads(output)['outcome']), (0, 'dry-run'))
                self.assertEqual(api.writes, [])
                code, output, error = run('--action', 'release-wo', '--execute')
                self.assertNotEqual(code, 0)
                self.assertEqual(api.writes, [])
                self.assertNotIn('only-process-token', output + error)
                with patch.object(mes_script.MesWriteTransport, 'send', api.send):
                    code, output, error = run('--action', 'release-wo', '--execute', '--acknowledge-mes-writes')
                self.assertEqual((code, json.loads(output)['outcome']), (0, 'applied'))
                self.assertEqual(len(api.writes), 1)


class ErpAsyncCompletionContractTest(unittest.TestCase):
    """MES 的提交可能早于 ERP 独立消费者事务的提交。"""

    def setUp(self):
        self.api = FakeMesApi()
        self.api.ready_card()
        self.api.ready_dispatch()
        self.api.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'
        self.api.card['cardStatus'] = 'IN_PROGRESS'
        self.api.dispatch['dispatchStatus'] = 'IN_PROGRESS'
        self.api.mes['woStatus'] = 'IN_PROGRESS'
        self.expected = dict(ERP, tenantId='000000', planId='19')
        self.options = {'dispatchId': self.api.dispatch['dispatchId'], 'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'}

    def after_mes_commit(self, method, path, *, query=None, payload=None):
        result = self.api.send(method, path, query=query, payload=payload)
        self.api.mes.update(sourceType='ERP', actualEndDate='2026-09-29 11:18:00')
        return result

    def test_final_dispatch_async_erp_completed_proves_both_without_duplicate(self):
        def sender(method, path, *, query=None, payload=None):
            result = self.after_mes_commit(method, path, query=query, payload=payload)
            self.api.erp.update(woStatus='COMPLETED', completedQty='10.0000', scrapQty='0.0000',
                                actualEndDate='2026-09-29 11:18:02')
            return result
        result = run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                execute=True, sender=sender)
        self.assertEqual(result['outcome'], 'applied')
        self.assertTrue(result['mesCompleted'])
        self.assertTrue(result['erpCompletionVerified'])
        self.assertEqual(result['erpStatus'], 'COMPLETED')
        replay = run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                execute=True, sender=sender)
        self.assertEqual(replay['outcome'], 'reused')
        self.assertTrue(replay['erpCompletionVerified'])
        self.assertEqual(len(self.api.writes), 1)

    def test_transient_in_progress_only_proves_mes_until_erp_catches_up(self):
        def sender(method, path, *, query=None, payload=None):
            result = self.after_mes_commit(method, path, query=query, payload=payload)
            self.api.erp.update(woStatus='IN_PROGRESS', completedQty='0.0000', scrapQty='0.0000')
            return result
        result = run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                execute=True, sender=sender)
        self.assertTrue(result['mesCompleted'])
        self.assertFalse(result['erpCompletionVerified'])
        self.assertEqual(result['erpStatus'], 'IN_PROGRESS')
        self.assertEqual(len(self.api.writes), 1)
        self.api.erp.update(woStatus='COMPLETED', completedQty='10.0000', scrapQty='0.0000',
                            actualEndDate='2026-09-29 11:18:02')
        replay = run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                execute=True, sender=sender)
        self.assertEqual(replay['outcome'], 'reused')
        self.assertTrue(replay['erpCompletionVerified'])
        self.assertEqual(len(self.api.writes), 1)

    def test_missing_completion_provenance_never_claims_success_after_write(self):
        for bad in ({'sourceType': 'MANUAL'}, {'actualEndDate': None},
                    {'completedQty': '9'}, {'scrapQty': '1'}):
            with self.subTest(bad=bad):
                api = FakeMesApi()
                api.ready_card()
                api.ready_dispatch()
                api.card['steps'][0]['stepStatus'] = 'IN_PROGRESS'
                api.dispatch['dispatchStatus'] = 'IN_PROGRESS'
                api.card['cardStatus'] = 'IN_PROGRESS'
                api.mes['woStatus'] = 'IN_PROGRESS'
                def sender(method, path, *, query=None, payload=None):
                    result = api.send(method, path, query=query, payload=payload)
                    api.mes.update({'sourceType': 'ERP', 'actualEndDate': '2026-09-29 11:18:00', **bad})
                    api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                                   actualEndDate='2026-09-29 11:18:02')
                    return result
                with self.assertRaises(MesFlowError):
                    run_mes_action(api, self.expected, 'finish-dispatch', self.options,
                                   execute=True, sender=sender)
                self.assertEqual(len(api.writes), 1)  # 校验失败即关闭通路，不重试结果不确定的操作。

    def test_station_exit_async_completed_erp_proves_final_step_and_replays(self):
        self.api.dispatch = None  # 工位进站或出站模式没有派工。
        self.api.card['steps'][0]['entryCheckResult'] = 'PASS'
        options = {'stepRecordId': '82', 'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'}
        def sender(method, path, *, query=None, payload=None):
            result = self.after_mes_commit(method, path, query=query, payload=payload)
            self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                                actualEndDate='2026-09-29 11:18:02')
            return result
        result = run_mes_action(self.api, self.expected, 'exit', options, execute=True, sender=sender)
        self.assertTrue(result['erpCompletionVerified'])
        replay = run_mes_action(self.api, self.expected, 'exit', options, execute=True, sender=sender)
        self.assertEqual(replay['outcome'], 'reused')
        self.assertEqual(len(self.api.writes), 1)
        with self.assertRaises(MesFlowError):
            run_mes_action(self.api, self.expected, 'exit', dict(options, goodQty='9', defectQty='1'),
                           execute=True, sender=sender)
        self.assertEqual(len(self.api.writes), 1)

    def test_explicit_foreign_tenant_in_completion_readback_is_rejected(self):
        self.after_mes_commit('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                              query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                            actualEndDate='2026-09-29 11:18:02')
        self.api.writes.clear()
        for area in ('erp', 'mes', 'card'):
            with self.subTest(area=area):
                api = deepcopy(self.api)
                getattr(api, area)['tenantId'] = '999999'
                with self.assertRaises(MesFlowError):
                    run_mes_action(api, self.expected, 'finish-dispatch', self.options,
                                   execute=True, sender=api.send)
                self.assertEqual(api.writes, [])

    def test_other_erp_states_are_rejected_despite_valid_mes_completion(self):
        self.after_mes_commit('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                              query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.writes.clear()
        self.api.erp.update(completedQty='10', scrapQty='0', actualEndDate='2026-09-29 11:18:02')
        for state in ('CREATED', 'APPROVED', 'APPROVING', 'CLOSED', 'CANCELLED', None, 'UNKNOWN'):
            with self.subTest(state=state):
                self.api.erp['woStatus'] = state
                with self.assertRaises(MesFlowError):
                    run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                   execute=True, sender=self.api.send)
        self.assertFalse(self.api.writes)

    def test_advanced_erp_still_rejects_duplicated_mes_and_wrong_routing(self):
        self.after_mes_commit('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                              query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                            actualEndDate='2026-09-29 11:18:02')
        self.api.writes.clear()
        for defect in ('duplicate', 'card-owner', 'step-owner', 'routing'):
            with self.subTest(defect=defect):
                api = deepcopy(self.api)
                if defect == 'duplicate':
                    api.mes_rows.append(deepcopy(api.mes)); api.mes_total = 2
                if defect == 'card-owner': api.card['mesWoId'] = '2104510684576272399'
                if defect == 'step-owner': api.card['steps'][0]['cardId'] = '2104510684576272399'
                if defect == 'routing': api.routing[0]['processCode'] = 'WRONG'
                with self.assertRaises(MesFlowError):
                    run_mes_action(api, self.expected, 'finish-dispatch', self.options,
                                   execute=True, sender=api.send)
                self.assertFalse(api.writes)

    def test_terminal_id_substitution_after_write_does_not_prove_original_mes(self):
        def sender(method, path, *, query=None, payload=None):
            result = self.after_mes_commit(method, path, query=query, payload=payload)
            self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                                actualEndDate='2026-09-29 11:18:02')
            new_id = '2104510684576272399'
            self.api.mes['mesWoId'] = new_id
            self.api.mes['steps'][0]['mesWoId'] = new_id
            self.api.card['mesWoId'] = new_id
            self.api.card['steps'][0]['mesWoId'] = new_id
            self.api.dispatch['mesWoId'] = new_id
            return result
        with self.assertRaises(MesFlowError):
            run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                           execute=True, sender=sender)
        self.assertEqual(len(self.api.writes), 1)

    def test_completed_erp_requires_exact_reported_good_scrap_end_time(self):
        # ERP 实际值不一致时，即使 MES 来源证据完整也仍不足以证明完成。
        self.api.send('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                      query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.mes.update(sourceType='ERP', actualEndDate='2026-09-29 11:18:00')
        self.api.writes.clear()
        self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                            actualEndDate='2026-09-29 11:18:02')
        for field, value in (('completedQty', '9'), ('scrapQty', '1'),
                             ('actualEndDate', None), ('completedQty', None)):
            with self.subTest(field=field, value=value):
                old = self.api.erp[field]
                self.api.erp[field] = value
                with self.assertRaises(MesFlowError):
                    run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                   execute=True, sender=self.api.send)
                self.api.erp[field] = old
                self.assertEqual(self.api.writes, [])

    def test_in_progress_erp_must_not_claim_partial_completed_actuals_as_gap(self):
        self.api.send('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                      query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.mes.update(sourceType='ERP', actualEndDate='2026-09-29 11:18:00')
        self.api.writes.clear()
        self.api.erp.update(woStatus='IN_PROGRESS', completedQty='10', scrapQty='0')
        with self.assertRaises(MesFlowError):
            run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                           execute=True, sender=self.api.send)
        self.assertFalse(self.api.writes)

    def test_read_only_inspection_reports_completed_status_from_verified_origin(self):
        self.api.send('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                      query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.mes.update(sourceType='ERP', actualEndDate='2026-09-29 11:18:00')
        self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                            actualEndDate='2026-09-29 11:18:02')
        self.api.writes.clear()
        result = mes_script.inspect_live(self.api, ERP['woId'], self.expected)
        self.assertEqual(result['erpStatus'], 'COMPLETED')
        self.assertTrue(result['erpCompletionVerified'])
        self.assertEqual(self.api.writes, [])

    def test_mes_completion_claim_requires_provenance_even_while_erp_released(self):
        self.api.send('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                      query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.writes.clear()
        for change in ({'sourceType': 'MANUAL'}, {'actualEndDate': None}):
            with self.subTest(change=change), self.assertRaises(MesFlowError):
                self.api.mes.update(change)
                run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                               execute=True, sender=self.api.send)
        self.assertEqual(self.api.writes, [])

    def test_read_only_cli_returns_completion_proof_without_mutating_transport(self):
        self.after_mes_commit('PUT', '/mes/pro/dispatch/complete/' + self.api.dispatch['dispatchId'],
                              query={'goodQty': '10', 'defectQty': '0', 'scrapQty': '0'})
        self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                            actualEndDate='2026-09-29 11:18:02')
        self.api.writes.clear()
        manifest = {'tenantId': '000000', 'records': [{'table': 'prd_work_order',
            'entityId': ERP['woId'], 'businessCode': ERP['woNo'], 'tenantId': '000000',
            'parentRefs': {'materialId': '1', 'planId': '19'}}]}
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
            (root / 'work-order-manual-intent.json').write_text(json.dumps(
                {'tenantId': '000000', 'planId': '19', 'plannedQty': '10'}), encoding='utf-8')
            output = StringIO()
            with patch.object(sys, 'argv', ['mes_flow_runner.py', '--manifest', str(root / 'manifest.json')]), \
                 patch.object(mes_script, 'ApiClient', return_value=self.api), \
                 patch.dict(os.environ, {'NEXUS_FULL_FLOW_TOKEN': 'process-only', 'NEXUS_FULL_FLOW_CLIENT_ID': 'process-only'}), \
                 patch.object(mes_script.MesWriteTransport, 'send', side_effect=AssertionError('read-only must not write')), \
                 redirect_stdout(output):
                self.assertEqual(mes_script.main(), 0)
            result = json.loads(output.getvalue())
            self.assertTrue(result['erpCompletionVerified'])
            self.assertEqual(result['erpStatus'], 'COMPLETED')
            self.assertEqual(self.api.writes, [])

    def test_actual_good_and_scrap_can_complete_but_cannot_be_swapped(self):
        self.api.dispatch = None
        self.api.card['steps'][0]['entryCheckResult'] = 'PASS'
        options = {'stepRecordId': '82', 'goodQty': '9', 'defectQty': '0', 'scrapQty': '1'}
        def sender(method, path, *, query=None, payload=None):
            result = self.after_mes_commit(method, path, query=query, payload=payload)
            self.api.erp.update(woStatus='COMPLETED', completedQty='9', scrapQty='1',
                                actualEndDate='2026-09-29 11:18:02')
            return result
        result = run_mes_action(self.api, self.expected, 'exit', options, execute=True, sender=sender)
        self.assertTrue(result['erpCompletionVerified'])
        self.assertEqual(len(self.api.writes), 1)
        self.api.erp.update(completedQty='10', scrapQty='0')
        with self.assertRaises(MesFlowError):
            run_mes_action(self.api, self.expected, 'exit', options, execute=True, sender=sender)
        self.assertEqual(len(self.api.writes), 1)

    def test_bare_erp_state_check_cannot_bypass_completion_provenance(self):
        for status in ('IN_PROGRESS', 'COMPLETED'):
            with self.subTest(status=status), self.assertRaises(MesFlowError):
                check_erp_origin({'code': 200, 'data': dict(ERP, woStatus=status,
                                  completedQty='10', scrapQty='0')}, self.expected)
        # 任何标志都不能将单一状态值变为完工证明。
        self.assertNotIn('allow_async_completion', __import__('inspect').signature(check_erp_origin).parameters)

    def test_advanced_erp_without_completed_mes_cannot_authorize_write_or_reuse(self):
        for status in ('IN_PROGRESS', 'COMPLETED'):
            with self.subTest(status=status):
                self.api.erp.update(woStatus=status, completedQty='10', scrapQty='0',
                                    actualEndDate='2026-09-29 11:18:02')
                with self.assertRaises(MesFlowError):
                    run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                   execute=True, sender=self.api.send)
                self.assertEqual(self.api.writes, [])

    def test_advanced_erp_rejects_created_wrong_tenant_id_and_plan(self):
        for field, value in (('woStatus', 'CREATED'), ('woId', '2104510684576272399'),
                             ('materialId', '99'), ('plannedQty', '11'), ('planId', '99')):
            with self.subTest(field=field):
                self.api.mes.update(woStatus='COMPLETED', sourceType='ERP',
                                    actualEndDate='2026-09-29 11:18:00', completedQty='10', scrapQty='0')
                self.api.card.update(cardStatus='COMPLETED', completedQty='10', scrapQty='0')
                self.api.card['steps'][0].update(stepStatus='COMPLETED', goodQty='10', scrapQty='0')
                self.api.dispatch.update(dispatchStatus='COMPLETED', goodQty='10', defectQty='0', scrapQty='0')
                self.api.erp.update(woStatus='COMPLETED', completedQty='10', scrapQty='0',
                                    actualEndDate='2026-09-29 11:18:02')
                old = self.api.erp[field]
                self.api.erp[field] = value
                with self.assertRaises(MesFlowError):
                    run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                                   execute=True, sender=self.api.send)
                self.api.erp[field] = old
                self.assertEqual(self.api.writes, [])
        self.api.tenant = '999999'
        with self.assertRaises(MesFlowError):
            run_mes_action(self.api, self.expected, 'finish-dispatch', self.options,
                           execute=True, sender=self.api.send)
        self.assertEqual(self.api.writes, [])
if __name__ == '__main__':
    unittest.main()
