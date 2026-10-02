"""仅使用合成传输；这些断言不会创建真实业务记录。"""
from copy import deepcopy
from datetime import date
import io
import json
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch
from uuid import UUID

from erp_flow_runner import ApiClient, Manifest, PreflightError
import formal_manufacturing_fixture as fixture
from test_formal_manufacturing_fixture import readings, created_records, baseline, moq_input

try:
    import formal_case_batch as batch
except ModuleNotFoundError as exc:
    if exc.name != 'formal_case_batch':
        raise
    batch = None

DAY = date.today().isoformat()
TENANT = '000001'
BATCH_ID = '71934460-44bf-4a2c-91b2-6f507fd5e3a2'
ROOT = Path(__file__).resolve().parents[4]
# 测试只读权威 DDL，独立读端不再把 VO 派生字段伪装成原生列。
_DDL = (ROOT / 'script/sql/ry-erp.sql').read_text(encoding='utf-8')
NATIVE_COLUMNS = {
    table: set(re.findall(r'^\s*`(\w+)`', definition, re.M))
    for table, definition in re.findall(r'CREATE TABLE `(\w+)`\s*\((.*?)\n\) ENGINE', _DDL, re.S)
}


def specification():
    facts = readings()
    facts['as_of_date'] = DAY
    facts['dates'] = dict.fromkeys(facts['dates'], DAY)
    master_fixture = fixture.build_fixture(**facts)
    master = created_records(master_fixture)
    for request in master_fixture['requests']:
        resolved = fixture.resolve_new_references(request, created_records=master,
                                                   preexisting_ids=baseline())
        fixture.assert_request_ready(resolved)
        if request['key'] in master:
            master[request['key']].update({k: v for k, v in resolved['body'].items()
                                         if k not in ('materialCode', 'routingCode')})
    for key, row in master.items():
        if key.startswith('material.'):
            row['baseUomName'] = ('套' if key.endswith('finished') else '件')
            row['categoryName'] = facts['categories']['finished' if key.endswith('finished') else
                                      'box' if key.endswith('box') else 'piece']['categoryName']
            row['safetyStock'] = '0'  # 明确的合成来源，不是运行默认值。
    master['customer'] = {'clientId': '101', 'clientName': '华东机电', 'clientCode': 'C101'}
    cases = []
    for _ in range(40):
        cases.append({'orderDate': DAY, 'startDate': DAY, 'endDate': DAY, 'requestedDate': DAY,
                      'priceQuote': {'path': '/erp/so/orderheader/resolvePrice', 'value': '18.2500',
                          'readDate': DAY, 'clientId': '101',
                          'materialId': master['material.finished']['materialId'],
                          'qty': '600', 'orderDate': DAY}})
    return {'batchId': BATCH_ID, 'tenantId': TENANT, 'asOfDate': DAY,
            'fixtureInputs': facts, 'masterReadbacks': master, 'masterBaseline': baseline(),
            'preflight': {'client_code': 'C101', 'bom_id': master['bom']['bomId'],
                          'routing_code': master['routing']['routingCode'],
                          'warehouse_code': 'W101', 'process_keys': ['sales']},
            'quantity': {'kind': 'SOFTWARE_PLANNED', 'value': '600',
                         'uomId': facts['units']['finished']['uomId'], 'lotMultiple': '1'},
            'cases': cases}


def prepare(spec):
    # 缺实现时产生真实 RED 断言，不以导入错误/跳过替代。
    return batch.prepare_batch(spec) if batch else {'cases': [], 'status': 'UNIMPLEMENTED'}


def snake(row):
    return {re.sub(r'(?<!^)(?=[A-Z])', '_', k).lower(): deepcopy(v) for k, v in row.items()}


class Authority(batch.ExternalWriteAuthority if batch else object):
    def __init__(self):
        self.calls = []
        self.retry_safe = False
        self.retry_checks = []
        self.deny = False

    def require_authorized(self, **scope):
        self.calls.append({k: (v if k == 'business_client' else deepcopy(v)) for k, v in scope.items()})
        if self.deny:
            raise PreflightError('external authority absent')
        return None

    def require_retry_safe(self, **scope):
        self.retry_checks.append({k: (v if k == 'business_client' else deepcopy(v)) for k, v in scope.items()})
        if not self.retry_safe:
            raise PreflightError('previous non-idempotent outcome unresolved')
        return None


class SyntheticSystem(batch.ScopedBusinessReads if batch else object):
    """内存 API 与独立租户只读替身，不使用 HTTP/SQL。"""
    def __init__(self, spec):
        self.spec = deepcopy(spec)
        self.gets, self.posts, self.db_reads = [], [], []
        self.details, self.tables = {}, {}
        self.timeout = False
        self.omit_create = False
        self.change_after = None
        self.next_id = 9007199254750000
        self.target = 'localhost TCP 3306 / dev'
        self.inventory_day = DAY
        self.cutoff = DAY + ' 00:00:00'
        self.auth_tenant = TENANT
        self._put_master()

    def add(self, table, base, id_field, row):
        row = deepcopy(row)
        row['tenantId'] = TENANT
        row.setdefault('createTime', DAY + ' 12:00:00')
        self.details[base + '/' + row[id_field]] = row
        projection = snake(row)
        self.tables.setdefault(table, []).append({k: v for k, v in projection.items() if k in NATIVE_COLUMNS[table]})
        return row

    def _put_master(self):
        for key, row in self.spec['masterReadbacks'].items():
            if key.startswith('material.'):
                self.add('bd_material', '/erp/bd/material', 'materialId', row)
            elif key == 'routing':
                self.add('bd_routing', '/erp/bd/routing', 'routingId', row)
            elif key == 'bom':
                self.add('bd_bom', '/erp/bd/bom', 'bomId', row)
            elif key == 'customer':
                self.add('bd_client', '/erp/bd/client', 'clientId', row)
        self.add('wm_warehouse', '/erp/wm/warehouse', 'warehouseId',
                 {'warehouseId': '102', 'warehouseCode': 'W101', 'status': 1})
        for group, table, base, field in (
            ('categories', 'bd_material_category', '/erp/bd/category', 'categoryId'),
            ('processes', 'bd_process', '/erp/bd/process', 'processId'),
            ('workstations', 'bd_workstation', '/erp/bd/workstation', 'workstationId'),
            ('workshops', 'bd_workshop', '/erp/bd/workshop', 'workshopId')):
            for row in self.spec['fixtureInputs'][group].values():
                self.add(table, base, field, row)
        for row in self.spec['fixtureInputs']['unit_catalog']:
            self.add('bd_unit_of_measure', '/erp/bd/measure', 'uomId', row)
        self.routing_steps = []
        for i, original in enumerate(self.spec['masterReadbacks']['routing']['steps']):
            row = deepcopy(original)
            row.update(stepId=str(900 + i), routingId=self.spec['masterReadbacks']['routing']['routingId'])
            self.routing_steps.append(self.add('bd_routing_step', '/synthetic/routing-step', 'stepId', row))
        self.bom_details = []
        for i, request in enumerate(fixture.build_fixture(**self.spec['fixtureInputs'])['requests'][7:]):
            row = fixture.resolve_new_references(request, created_records=self.spec['masterReadbacks'],
                                                  preexisting_ids=self.spec['masterBaseline'])['body']
            row.update(detailId=str(700 + i), parentMaterialId=self.spec['masterReadbacks']['material.finished']['materialId'],
                       baseUomId='26', baseUomName='件', leadTime=0)
            self.add('bd_bom_detail', '/erp/bd/bom/detail', 'detailId', row)
            self.bom_details.append(row)
            self.add('wm_material_stock', '/erp/wm/stock', 'stockId',
                     {'stockId': str(800 + i), 'materialId': row['childMaterialId'],
                      'baseUomId': '26', 'baseUomName': '件', 'stockQuantity': '0',
                      'reservedQuantity': '0', 'frozenFlag': 0})
        for table in ('so_sales_order_header', 'so_sales_order_line', 'prd_production_plan',
                      'prd_material_requirement', 'prd_material_requirement_detail', 'prd_work_order'):
            self.tables.setdefault(table, [])

    def inventory(self):
        erp_columns = {'bd_client': ['tenant_id', 'client_code', 'client_id'],
                       'wm_warehouse': ['tenant_id', 'warehouse_code', 'warehouse_id'],
                       'bd_bom': ['tenant_id', 'bom_id'],
                       'bd_routing': ['tenant_id', 'routing_code', 'routing_id'],
                       'bd_material': ['tenant_id', 'material_id']}
        candidates = {t: [[row.get(c) for c in cols] for row in self.tables[t]]
                      for t, cols in erp_columns.items()}
        return {'target': self.target, 'missing': [], 'usable_for_write_phase': False,
                'snapshot': {'verified': True, 'db_date': self.inventory_day,
                             'db_timestamp': self.cutoff, 'grants': 'admin read-only synthetic'},
                'schemas': {'ry-erp': {'candidate_columns': erp_columns, 'candidates': candidates},
                           'ry-vue': {'candidate_columns': {'sys_tenant': ['tenant_id'],
                                                          'sys_user': ['tenant_id', 'user_id']},
                                      'candidates': {'sys_tenant': [[TENANT]], 'sys_user': [[TENANT, '42']]}}}}

    def master_baseline(self, *, tenant_id):
        self.db_reads.append(('master-baseline', tenant_id))
        return deepcopy(self.spec['masterBaseline'])

    def rows(self, table, *, tenant_id):
        self.db_reads.append((table, tenant_id))
        return deepcopy(self.tables[table])

    @staticmethod
    def api_projection(path, row):
        if not isinstance(row, dict):
            return row
        if path.startswith('/erp/prd/plan'):
            # 计划响应只使用真实 VO 声明列，绝不把请求体的 remark/tenantId 复制成返回字段。
            source = (Path(__file__).resolve().parents[4] / 'ruoyi-modules/ruoyi-erp/src/main/java/'
                      'com/nexus/erp/prd/domain/vo/PrdProductionPlanVo.java').read_text(encoding='utf-8')
            fields = set(re.findall(r'private\s+[\w<>]+\s+(\w+)\s*;', source))
            return {key: value for key, value in row.items() if key in fields}
        return deepcopy(row)

    def get(self, path, query=None):
        self.gets.append((path, deepcopy(query)))
        if path == '/system/user/getInfo':
            return {'code': 200, 'data': {'user': {'tenantId': self.auth_tenant, 'userId': '42'}}}
        if path == '/workflow/deploy/publishList':
            return {'rows': [{'processKey': 'sales', 'version': 1, 'suspended': False,
                             'definitionId': 'sales:1:101', 'deploymentId': '101'}], 'total': 1}
        if path == '/erp/bd/measure/all':
            return {'code': 200, 'data': deepcopy(self.spec['fixtureInputs']['unit_catalog'])}
        if path == '/erp/so/orderheader/resolvePrice':
            return {'code': 200, 'data': '18.2500'}
        if '/verify/' in path:
            return {'code': 200, 'data': 'OK'}
        if path.startswith('/erp/bd/routing/steps/'):
            return {'code': 200, 'data': deepcopy(self.routing_steps)}
        if '/listLineById/' in path:
            wanted = path.rsplit('/', 1)[-1]
            matches = [row for row in self.details.values() if row.get('lineId') == wanted]
            return {'code': 200, 'data': deepcopy(matches[0]) if len(matches) == 1 else None}
        if path.endswith('/list'):
            base = path[:-5]
            rows = [row for key, row in self.details.items() if key.rsplit('/', 1)[0] == base]
            for field, value in (query or {}).items():
                if field not in ('pageNum', 'pageSize'):
                    rows = [row for row in rows if row.get(field) == value]
            start = ((query or {}).get('pageNum', 1) - 1) * 100
            return {'code': 200, 'rows': [self.api_projection(path, row) for row in rows[start:start + 100]], 'total': len(rows)}
        return {'code': 200, 'data': self.api_projection(path, self.details.get(path))}

    def _id(self):
        self.next_id += 1
        return str(self.next_id)

    def post(self, path, payload=None):
        self.posts.append((path, deepcopy(payload)))
        if not self.omit_create:
            if path == '/erp/so/orderheader':
                row = deepcopy(payload)
                row.update(soId=self._id(), soNo='SO' + self._id(), orderStatus='DRAFT')
                line = deepcopy(row['soSalesOrderLineList'][0])
                line.update(lineId=self._id(), soId=row['soId'], lineNo='1')
                self.add('so_sales_order_line', '/erp/so/orderheader/listLineById', 'lineId', line)
                row['soSalesOrderLineList'] = [line]
                self.add('so_sales_order_header', path, 'soId', row)
            elif path == '/erp/prd/plan':
                row = deepcopy(payload)
                row.update(planId=self._id(), planNo='PP' + self._id())
                self.add('prd_production_plan', path, 'planId', row)
            elif '/generateMaterialRequirementsWithDetails/' in path:
                plan_id = path.rsplit('/', 1)[-1]
                plan = self.details['/erp/prd/plan/' + plan_id]
                existing = [r for r in self.tables['prd_material_requirement'] if r['plan_id'] == plan_id]
                if not existing:
                    row = {'requirementId': self._id(), 'requirementNo': 'MR' + self._id(),
                           'planId': plan_id, 'planNo': plan['planNo'], 'bomId': plan['bomId'],
                           'materialId': self.spec['masterReadbacks']['material.finished']['materialId'],
                           'sourceType': 'PROCESS', 'status': 'DRAFT', 'requirementDate': DAY,
                           'totalRequiredQuantity': str(int(plan['totalQuantity']) * 4),
                           'prdMaterialRequirementDetailList': []}
                    for component in self.bom_details:
                        detail = {'detailId': self._id(), 'requirementId': row['requirementId'],
                                  'materialId': component['childMaterialId'], 'unit': '26',
                                  'requiredQuantity': plan['totalQuantity'], 'requiredDate': plan['endDate']}
                        self.add('prd_material_requirement_detail', '/synthetic/requirement-detail', 'detailId', detail)
                        row['prdMaterialRequirementDetailList'].append(detail)
                    self.add('prd_material_requirement', '/erp/prd/requirement', 'requirementId', row)
            elif '/generateWorkOrders/' in path:
                plan_id = path.rsplit('/', 1)[-1]
                plan = self.details['/erp/prd/plan/' + plan_id]
                req_id = payload[0]
                if not any(r['source_id'] == req_id for r in self.tables['prd_work_order']):
                    row = {'woId': self._id(), 'woNo': 'WO' + self._id(), 'planId': plan_id,
                           'sourceType': 'MATERIAL_REQUIREMENT', 'sourceId': req_id,
                           'sourceNo': self.details['/erp/prd/requirement/' + req_id]['requirementNo'],
                           'bomId': plan['bomId'], 'materialId': self.spec['masterReadbacks']['material.finished']['materialId'],
                           'plannedQty': plan['totalQuantity'], 'clientId': '101', 'woStatus': 'CREATED',
                           'planStartDate': DAY}
                    self.add('prd_work_order', '/erp/prd/work', 'woId', row)
            else:
                raise AssertionError('Invented or out-of-scope endpoint')
            if self.change_after:
                self.change_after(self, path)
        if self.timeout:
            raise TimeoutError('synthetic response lost')
        return {'code': 200, 'data': None}  # 实际 CREATE/生成器响应不提供新 ID。


class PlannerTests(unittest.TestCase):
    def test_generates_40_independent_creation_intents_not_analysis_repeats(self):
        plan = prepare(specification())
        self.assertEqual(len(plan['cases']), 40)
        self.assertEqual(len({c['intentId'] for c in plan['cases']}), 40)
        self.assertEqual([c['ordinal'] for c in plan['cases']], list(range(1, 41)))
        for case in plan['cases']:
            UUID(case['intentId'])
            self.assertEqual([s['role'] for s in case['steps']], ['so', 'plan', 'mrp', 'work'])
            self.assertEqual(case['status'], 'BLOCKED')
            self.assertFalse(case['readyForExecution'])
        self.assertEqual(plan['sourceCompleteCases'], 0)

    def test_normal_names_server_numbering_and_intent_metadata_not_fake_ids(self):
        plan = prepare(specification())
        self.assertEqual(len(plan['cases']), 40)
        for case in plan['cases']:
            so, production, mrp, work = [s['request'] for s in case['steps']]
            self.assertEqual(so['body']['soNo'], '')
            self.assertEqual(production['body']['planNo'], '')
            self.assertNotIn('soId', so['body'])
            self.assertNotIn('planId', production['body'])
            fixture._normal_name(production['body']['planName'], 'planName')
            self.assertIn(case['intentId'], so['body']['remark'])
            self.assertNotIn('unitPrice', production['body'])
            self.assertEqual(work['body'], ['$case:mrp.requirementId'])

    def test_default_is_inert_even_without_any_facts(self):
        with patch('urllib.request.urlopen', side_effect=AssertionError('HTTP')), \
             patch.object(Manifest, 'save', side_effect=AssertionError('filesystem')):
            plan = prepare({'batchId': BATCH_ID})
        self.assertEqual(len(plan['cases']), 40)
        for case in plan['cases']:
            for missing in ('quantity_source', 'fresh_master_data', 'target_tenant_preflight',
                            'external_write_authority', 'inventory_contract'):
                self.assertIn(missing, case['blockers'])
            self.assertFalse(case['readyForExecution'])

    def test_never_upgrades_readiness_with_caller_flags(self):
        spec = specification()
        spec.update(verified=True, writeEnabled=True, created=True)
        if batch is None:
            self.fail('formal_case_batch is not implemented')
        with self.assertRaises(batch.BatchContractError):
            batch.prepare_batch(spec)

    def test_rejects_non_40_input_and_duplicate_client_intent_cannot_be_supplied(self):
        if batch is None:
            self.fail('formal_case_batch is not implemented')
        for count in (0, 1, 39, 41):
            spec = specification()
            spec['cases'] = spec['cases'][:count] if count < 40 else spec['cases'] + [{}]
            with self.subTest(count=count), self.assertRaises(batch.BatchContractError):
                batch.prepare_batch(spec)
        spec = specification()
        spec['cases'][1]['intentId'] = 'caller-verified-source'
        with self.assertRaises(batch.BatchContractError):
            batch.prepare_batch(spec)

    def test_invalid_discrete_quantity_blocks_without_rounding_or_default_510(self):
        for value in (None, '5.4292', 5.4292, True, 'NaN', '-1', '0'):
            spec = specification()
            spec['quantity']['value'] = value
            plan = prepare(spec)
            self.assertEqual(len(plan['cases']), 40)
            self.assertTrue(all('quantity_source' in c['blockers'] for c in plan['cases']))
            self.assertTrue(all(c['plannedQuantity'] is None for c in plan['cases']))

    def test_legacy_moq_annotations_and_default_lots_cannot_size_40_cases(self):
        spec = specification()
        contract = moq_input(fixture.build_fixture(**spec['fixtureInputs']))
        for read in contract['moq_reads'].values():
            read['readDate'] = DAY
        contract.update(as_of_date=DAY, candidate_lots=[600, 1000], kit_lot_multiple=1,
                        component_readbacks={c: deepcopy(spec['masterReadbacks']['material.' + c])
                                             for c in ('bolt', 'nut', 'washer', 'box')})
        spec['quantity'] = {'kind': 'NEW_COMPONENT_MOQ', 'contract': contract}
        plan = prepare(spec)
        self.assertEqual(len(plan['cases']), 40)
        self.assertEqual(fixture.decide_lot_quantity(**contract)['finishedQuantity'], 600)
        self.assertIsNone(plan['cases'][0]['plannedQuantity'])
        self.assertIn('quantity_source', plan['cases'][0]['blockers'])
        self.assertFalse(plan['procurementReady'])
        contract['moq_reads']['box']['row']['minOrderQty'] = None
        self.assertIn('quantity_source', prepare(spec)['cases'][0]['blockers'])

    def test_old_master_and_missing_workstations_do_not_become_fresh_facts(self):
        for mutation in ('old', 'stations'):
            spec = specification()
            if mutation == 'old':
                spec['masterBaseline']['material'] += (spec['masterReadbacks']['material.finished']['materialId'],)
            else:
                spec['fixtureInputs'].pop('workstations')
                spec['fixtureInputs'].pop('workshops')
            plan = prepare(spec)
            self.assertEqual(len(plan['cases']), 40)
            self.assertIn('fresh_master_data', plan['cases'][0]['blockers'])

    def test_missing_dates_or_price_quote_are_blockers_not_price_or_capacity_assumptions(self):
        spec = specification()
        spec['cases'][0].pop('endDate')
        spec['cases'][1].pop('priceQuote')
        plan = prepare(spec)
        self.assertEqual(len(plan['cases']), 40)
        self.assertIn('planned_dates', plan['cases'][0]['blockers'])
        self.assertIn('sales_price_readback', plan['cases'][1]['blockers'])
        self.assertFalse(plan['physicalAcceptance'])
        self.assertFalse(plan['slaAccepted'])

    def test_request_fields_and_object_methods_match_actual_controllers_bos(self):
        plan = prepare(specification())
        self.assertEqual(len(plan['cases']), 40)
        base = ROOT / 'ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp'
        for role, bo, controller in (
            ('so', 'so/domain/bo/SoSalesOrderHeaderBo.java', 'so/controller/SoSalesOrderHeaderController.java'),
            ('plan', 'prd/domain/bo/PrdProductionPlanBo.java', 'prd/controller/PrdProductionPlanController.java')):
            step = next(s for s in plan['cases'][0]['steps'] if s['role'] == role)
            text = (base / bo).read_text(encoding='utf-8')
            fields = set(re.findall(r'private\s+[\w<>\[\]]+\s+(\w+)\s*;', text))
            self.assertLessEqual(set(step['request']['body']), fields)
            self.assertIn('@PostMapping', (base / controller).read_text(encoding='utf-8'))
        line_text = (base / 'so/domain/bo/SoSalesOrderLineBo.java').read_text(encoding='utf-8')
        fields = set(re.findall(r'private\s+[\w<>\[\]]+\s+(\w+)\s*;', line_text))
        self.assertLessEqual(set(plan['cases'][0]['steps'][0]['request']['body']['soSalesOrderLineList'][0]), fields)
        for role, method in (('mrp', 'generateMaterialRequirementsWithDetails'), ('work', 'generateWorkOrders')):
            step = next(s for s in plan['cases'][0]['steps'] if s['role'] == role)
            self.assertEqual(step['request']['method'], 'POST')
            self.assertIn('/' + method + '/', step['request']['path'])
            text = (base / 'prd/controller/PrdProductionPlanController.java').read_text(encoding='utf-8')
            self.assertIn('@PostMapping("/' + method + '/{planId}")', text)


class CoordinatorTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(batch, 'formal_case_batch coordinator is missing')
        self.spec = specification()
        self.system = SyntheticSystem(self.spec)
        self.authority = Authority()
        self.manifest = Manifest(TENANT)
        self.writer = batch.BusinessWriteCapability(self.system, authority=self.authority)
        self.coordinator = batch.BatchCoordinator(self.spec, manifest=self.manifest,
                            reader=self.system, scoped_reads=self.system, writer=self.writer)

    def finish(self, ordinal=1):
        outcomes = [self.coordinator.step(ordinal) for _ in range(4)]
        self.assertEqual([o['status'] for o in outcomes], ['CREATED_READBACK'] * 3 + ['SOURCE_COMPLETE'])
        return outcomes[-1]

    def test_without_capability_default_does_not_even_read(self):
        coordinator = batch.BatchCoordinator(self.spec, manifest=self.manifest, reader=self.system,
                                             scoped_reads=self.system)
        outcome = coordinator.step(1)
        self.assertEqual(outcome['status'], 'BLOCKED')
        self.assertEqual(self.system.gets + self.system.posts + self.system.db_reads, [])
        self.assertEqual(self.manifest.records, [])

    def test_readonly_client_and_booleans_cannot_be_promoted(self):
        readonly = ApiClient(apply=True, preflight_approved=True, opener=lambda r: self.fail('HTTP'))
        for authority in (True, False, {'verified': True}, self.authority):
            with self.assertRaises(batch.BatchContractError):
                batch.BusinessWriteCapability(readonly, authority=authority)
        with self.assertRaises(batch.BatchContractError):
            batch.BusinessWriteCapability(self.system, authority=True)
        with self.assertRaises(PreflightError):
            readonly.post('/erp/so/orderheader', {})

    def test_advance_only_one_case_one_real_object_post_method_per_step(self):
        outcome = self.coordinator.step(1)
        self.assertEqual(outcome['status'], 'CREATED_READBACK')
        self.assertEqual(len(self.system.posts), 1)
        self.assertEqual(self.system.posts[0][0], '/erp/so/orderheader')
        self.assertEqual(len(self.manifest.records), 1)
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 0)
        self.assertTrue(all(tenant == TENANT for _, tenant in self.system.db_reads))
        self.assertEqual(self.authority.calls[0]['tenant_id'], TENANT)

    def test_complete_tuple_reconstructed_from_independent_reads_and_manifest(self):
        self.finish()
        self.assertEqual(len(self.manifest.records), 4)
        self.assertEqual({r['table'] for r in self.manifest.records},
                         {'so_sales_order_header', 'prd_production_plan', 'prd_material_requirement', 'prd_work_order'})
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 1)
        self.assertEqual(self.coordinator.summary()['plannedCases'], 40)
        self.assertFalse(self.coordinator.summary()['slaAccepted'])
        self.assertFalse(self.coordinator.summary()['physicalAcceptance'])
        self.assertEqual(len(self.system.posts), 4)
        self.assertEqual(self.coordinator.step(1)['status'], 'SOURCE_COMPLETE')
        self.assertEqual(len(self.system.posts), 4)

    def test_40_distinct_software_source_tuples_not_40_analyses(self):
        for ordinal in range(1, 41):
            self.finish(ordinal)
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 40)
        self.assertEqual(len(self.manifest.records), 160)
        self.assertEqual(len({r['entityId'] for r in self.manifest.records}), 160)
        self.assertEqual(len(self.system.posts), 160)
        for table in ('so_sales_order_header', 'prd_production_plan', 'prd_material_requirement', 'prd_work_order'):
            self.assertEqual(sum(r['table'] == table for r in self.manifest.records), 40)

    def test_manifest_alone_or_caller_flags_never_count_as_completed_sources(self):
        self.manifest.add('erp', 'prd_work_order', '55', 'WO55', {'planId': '66'})
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 0)
        self.assertEqual(self.coordinator.summary()['readyCases'], 0)

    def test_wrong_target_stale_inventory_or_wrong_tenant_blocks_before_writing(self):
        for attribute, value in (('target', 'localhost TCP 3307 / dev'),
                                 ('target', 'localhost TCP 3306 / prod'),
                                 ('inventory_day', '2026-01-01'), ('auth_tenant', '000002')):
            with self.subTest(attribute=attribute, value=value):
                system = SyntheticSystem(self.spec)
                setattr(system, attribute, value)
                coordinator = batch.BatchCoordinator(self.spec, manifest=Manifest(TENANT), reader=system,
                    scoped_reads=system, writer=batch.BusinessWriteCapability(system, authority=self.authority))
                self.assertEqual(coordinator.step(1)['status'], 'BLOCKED')
                self.assertEqual(system.posts, [])

    def test_missing_or_wrong_scoped_tenant_evidence_not_accepted(self):
        for change in ('empty', 'foreign'):
            system = SyntheticSystem(self.spec)
            if change == 'empty':
                system.tables['bd_client'] = []
            else:
                system.tables['bd_client'][0]['tenant_id'] = '000002'
            coordinator = batch.BatchCoordinator(self.spec, manifest=Manifest(TENANT), reader=system,
                scoped_reads=system, writer=batch.BusinessWriteCapability(system, authority=self.authority))
            self.assertEqual(coordinator.step(1)['status'], 'BLOCKED')
            self.assertEqual(system.posts, [])

    def test_unknown_stock_empty_null_reserved_freeze_or_unit_is_not_zero(self):
        for field, value in (('rows', []), ('stock_quantity', None), ('reserved_quantity', None),
                             ('frozen_flag', None), ('base_uom_id', '99')):
            with self.subTest(field=field):
                self.setUp()
                self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
                self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
                if field == 'rows':
                    self.system.tables['wm_material_stock'] = []
                    self.system.details = {k: v for k, v in self.system.details.items() if not k.startswith('/erp/wm/stock/')}
                else:
                    self.system.tables['wm_material_stock'][0][field] = value
                outcome = self.coordinator.step(1)
                self.assertEqual(outcome['status'], 'BLOCKED')
                self.assertEqual(len(self.system.posts), 2)
                self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 0)

    def test_known_stock_zero_is_allowed_but_no_arrival_or_sensor_facts_are_written(self):
        self.finish()
        self.assertTrue(any(t == 'wm_material_stock' for t, _ in self.system.db_reads))
        self.assertTrue(all(path.startswith(('/erp/so/orderheader', '/erp/prd/plan')) for path, _ in self.system.posts))
        self.assertFalse(any('approve' in path or 'complete' in path or 'receive' in path for path, _ in self.system.posts))

    def test_authority_rechecked_on_every_step_and_denial_does_not_send(self):
        self.coordinator.step(1)
        self.authority.deny = True
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(len(self.system.posts), 1)
        self.assertEqual(len(self.authority.calls), 2)

    def test_readback_mismatch_does_not_register_created_entity(self):
        def corrupt(system, path):
            if path == '/erp/so/orderheader':
                row = next(v for k, v in system.details.items() if k.startswith(path + '/') and '/listLineById/' not in k)
                row['soSalesOrderLineList'][0]['orderedQty'] = '601'
        self.system.change_after = corrupt
        self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
        self.assertEqual(self.manifest.records, [])

    def test_preexisting_source_or_incomplete_api_baseline_is_rejected(self):
        request = prepare(self.spec)['cases'][0]['steps'][0]['request']
        self.system.post(request['path'], request['body'])
        self.system.posts.clear()
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.manifest.records, [])
        self.assertEqual(self.system.posts, [])

    def test_lost_response_recovers_by_readback_without_second_create(self):
        self.system.timeout = True
        self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
        self.assertEqual(self.manifest.records, [])
        self.system.timeout = False
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(len(self.system.posts), 1)

    def test_non_idempotent_retry_requires_external_noncommit_proof_and_exact_original_body(self):
        self.system.timeout = self.system.omit_create = True
        self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
        original = deepcopy(self.system.posts[0])
        self.system.timeout = self.system.omit_create = False
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(len(self.system.posts), 1)
        self.authority.retry_safe = True
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(self.system.posts, [original, original])

    def test_generator_retry_requires_proof_and_reuses_same_frozen_original_request(self):
        self.coordinator.step(1)
        self.coordinator.step(1)
        self.system.timeout = self.system.omit_create = True
        self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
        original = deepcopy(self.system.posts[-1])
        self.system.timeout = self.system.omit_create = False
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(len(self.system.posts), 3)
        self.authority.retry_safe = True
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(self.system.posts[-1], original)

    def test_manifest_source_conflict_rejects_atomically(self):
        predicted_so_id = str(self.system.next_id + 1)
        self.manifest.add('erp', 'so_sales_order_header', predicted_so_id, 'OLD', {'clientId': '101'})
        before = deepcopy(self.manifest.records)
        self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
        self.assertEqual(self.manifest.records, before)

    def test_later_mutated_parent_link_blocks_completion(self):
        for _ in range(3):
            self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        row = next(v for k, v in self.system.details.items() if k.startswith('/erp/prd/plan/'))
        row['orderLineId'] = '999'
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(len(self.system.posts), 3)
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 0)

    def test_no_manifest_save_or_http_transport_created_implicitly(self):
        with patch.object(Manifest, 'save', side_effect=AssertionError('implicit save')), \
             patch('urllib.request.build_opener', side_effect=AssertionError('implicit HTTP')):
            self.finish()


class CliTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(batch, 'formal_case_batch offline CLI is missing')

    def test_requires_explicit_input_in_ignored_local_directory(self):
        with self.assertRaises(batch.BatchContractError):
            batch.prepare_from_file(ROOT / 'AGENTS.md')
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            self.assertEqual(batch.main([]), 2)
        self.assertNotIn(TENANT, output.getvalue())
        self.assertNotIn(BATCH_ID, output.getvalue())

    def test_cli_read_only_by_default_prints_counts_not_raw_projection(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(batch, 'LOCAL_DIR', Path(temp)):
            path = Path(temp) / 'input.json'
            path.write_text(json.dumps(specification()), encoding='utf-8')
            before = list(Path(temp).iterdir())
            with patch('sys.stdout', new_callable=io.StringIO) as output:
                self.assertEqual(batch.main(['--input', str(path)]), 0)
            self.assertEqual(list(Path(temp).iterdir()), before)
            summary = json.loads(output.getvalue())
            self.assertEqual(summary['plannedCases'], 40)
            self.assertEqual(summary['sourceCompleteCases'], 0)
            self.assertNotIn(BATCH_ID, output.getvalue())
            self.assertNotIn('900719', output.getvalue())
            self.assertNotIn('18.2500', output.getvalue())

    def test_optional_artifact_is_exclusive_local_and_inert(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(batch, 'LOCAL_DIR', Path(temp)):
            path, output = Path(temp) / 'input.json', Path(temp) / 'prepared.json'
            path.write_text(json.dumps(specification()), encoding='utf-8')
            with patch('sys.stdout', new_callable=io.StringIO):
                self.assertEqual(batch.main(['--input', str(path), '--output', str(output)]), 0)
            artifact = json.loads(output.read_text(encoding='utf-8'))
            self.assertEqual(len(artifact['cases']), 40)
            self.assertEqual(artifact['sourceCompleteCases'], 0)
            before = output.read_bytes()
            with patch('sys.stdout', new_callable=io.StringIO):
                self.assertEqual(batch.main(['--input', str(path), '--output', str(output)]), 2)
            self.assertEqual(output.read_bytes(), before)



class SourceContractRegressionTests(unittest.TestCase):
    setUp = CoordinatorTests.setUp
    finish = CoordinatorTests.finish
    def test_actual_mrp_generator_uses_base_unit_id_not_display_name(self):
        source = ROOT / 'ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdProductionPlanServiceImpl.java'
        text = source.read_text(encoding='utf-8')
        self.assertIn('String.valueOf(material.getBaseUomId())', text)
        self.assertIn('detail.setUnit(unit)', text)
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        mrp = self.coordinator._states[0]['sources']['mrp']['row']
        self.assertTrue(all(d['unit'] == '26' for d in mrp['prdMaterialRequirementDetailList']))

    def test_live_routing_hours_or_station_changes_block_before_any_write(self):
        self.system.routing_steps[0]['standardHours'] = '99'
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])

    def test_live_bom_component_or_loss_change_blocks_before_any_write(self):
        row = next(v for k, v in self.system.details.items() if k.startswith('/erp/bd/bom/detail/'))
        row['lossRate'] = '0.2'
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])

    def test_unknown_lead_time_is_blocked_before_mrp_creation(self):
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        row = next(v for k, v in self.system.details.items() if k.startswith('/erp/bd/bom/detail/'))
        row['leadTime'] = None
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(len(self.system.posts), 2)

    def test_finished_case_readback_mutation_revokes_count_without_new_post(self):
        self.finish()
        row = next(v for k, v in self.system.details.items() if k.startswith('/erp/prd/work/'))
        row['plannedQty'] = '5.4292'
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 0)
        self.assertEqual(len(self.system.posts), 4)

    def test_foreign_tenant_readback_missing_proof_or_wrong_date_not_accepted(self):
        for field, value in (('tenant_id', '000002'), ('client_id', '999'), ('create_time', '2026-01-01 00:00:00')):
            self.setUp()
            def corrupt(system, path):
                if path == '/erp/so/orderheader':
                    system.tables['so_sales_order_header'][0][field] = value
            self.system.change_after = corrupt
            self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
            self.assertEqual(self.manifest.records, [])
            self.assertEqual(len(self.system.posts), 1)

    def test_second_intent_cannot_reuse_first_complete_server_context(self):
        self.finish()
        original = deepcopy(self.system.tables['so_sales_order_header'][0])
        def reuse(system, path):
            if path == '/erp/so/orderheader':
                latest = next(v for k, v in reversed(list(system.details.items()))
                              if k.startswith(path + '/') and '/listLineById/' not in k)
                latest['soId'] = original['so_id']
        self.system.change_after = reuse
        self.assertEqual(self.coordinator.step(2)['status'], 'UNCERTAIN')
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 1)

    def test_manifest_changed_parent_refs_rejects_without_rewriting_evidence(self):
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.manifest.records[0]['parentRefs']['clientId'] = '999'
        before = deepcopy(self.manifest.records)
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.manifest.records, before)
        self.assertEqual(len(self.system.posts), 1)

    def test_price_change_prevents_creation_without_a_guessed_zero(self):
        get = self.system.get
        def changed(path, query=None):
            return {'code': 200, 'data': '18.26'} if path.endswith('/resolvePrice') else get(path, query)
        self.system.get = changed
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])


    def test_transport_sender_itself_enforces_external_authority(self):
        self.assertTrue(callable(getattr(self.writer, 'send', None)), 'sender must enforce authority atomically')
        request = prepare(self.spec)['cases'][0]['steps'][0]['request']
        self.authority.deny = True
        with self.assertRaises(PreflightError):
            self.writer.send(request, tenant_id=TENANT, intent_id=prepare(self.spec)['cases'][0]['intentId'], target=self.system.target)
        self.assertEqual(self.system.posts, [])

    def test_transport_has_no_caller_idempotent_upgrade_for_create_retry(self):
        self.assertTrue(callable(getattr(self.writer, 'send', None)), 'bounded sender is missing')
        request = prepare(self.spec)['cases'][0]['steps'][0]['request']
        with self.assertRaises(PreflightError):
            self.writer.send(request, tenant_id=TENANT, intent_id=prepare(self.spec)['cases'][0]['intentId'], target=self.system.target, retry=True)
        with self.assertRaises(TypeError):
            self.writer.send(request, tenant_id=TENANT, intent_id=prepare(self.spec)['cases'][0]['intentId'], target=self.system.target,
                             retry=True, idempotent=True)
        self.assertEqual(self.system.posts, [])

    def test_blanket_verified_authority_return_is_not_a_grant(self):
        self.authority.require_authorized = lambda **scope: True
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])

    def test_manifest_tenant_cannot_change_after_binding(self):
        self.manifest.tenant_id = '000002'
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])


    def test_transport_whitelist_rejects_caller_numbering_state_patch_and_multi_source_payload(self):
        self.assertTrue(callable(getattr(self.writer, 'send', None)))
        plan = prepare(self.spec)
        for fields in ({'soNo': 'client-generated-number'}, {'orderStatus': 'APPROVED'}):
            request = deepcopy(plan['cases'][0]['steps'][0]['request'])
            request['body'].update(fields)
            with self.assertRaises(batch.BatchContractError):
                self.writer.send(request, tenant_id=TENANT, intent_id=plan['cases'][0]['intentId'], target=self.system.target)
        request = {'method': 'POST', 'path': '/erp/prd/plan/generateWorkOrders/123', 'body': ['1', '2']}
        with self.assertRaises(batch.BatchContractError):
            self.writer.send(request, tenant_id=TENANT, intent_id=plan['cases'][0]['intentId'], target=self.system.target)
        self.assertEqual(self.system.posts, [])


    def test_execution_requires_explicit_stable_batch_identity_not_a_new_random_namespace(self):
        spec = deepcopy(self.spec)
        spec.pop('batchId')
        coordinator = batch.BatchCoordinator(spec, manifest=self.manifest, reader=self.system,
                                             scoped_reads=self.system, writer=self.writer)
        self.assertEqual(coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts + self.system.gets, [])

    def test_incomplete_api_baseline_blocks_and_can_recover_without_stale_partial_state(self):
        get = self.system.get
        def incomplete(path, query=None):
            if path == '/erp/prd/work/list':
                return {'total': 1, 'rows': []}
            return get(path, query)
        self.system.get = incomplete
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])
        self.assertIsNone(self.coordinator._states[0]['baselines'])
        self.system.get = get
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')



class SpecCorrectionTests(unittest.TestCase):
    setUp = CoordinatorTests.setUp
    finish = CoordinatorTests.finish

    def test_all_four_write_roles_require_external_retry_safety_proof(self):
        plan = prepare(self.spec)
        self.system.omit_create = True  # 仅检查发送授权，绝不依赖未创建的来源。
        for step in plan['cases'][0]['steps']:
            with self.subTest(role=step['role']):
                request = deepcopy(step['request'])
                request['path'] = request['path'].replace('$case:plan.planId', '123')
                if step['role'] == 'plan':
                    request['body'].update(orderLineId='124', referenceNo='SO125')
                elif step['role'] == 'work':
                    request['body'] = ['126']
                with self.assertRaises(PreflightError):
                    self.writer.send(request, tenant_id=TENANT, intent_id=plan['cases'][0]['intentId'],
                                     target=self.system.target, retry=True)
        self.assertEqual(self.system.posts, [])
        self.assertEqual(len(self.authority.retry_checks), 4)

    def test_inflight_generator_first_attempt_is_not_absence_or_retry_proof(self):
        for ordinal_step in (2, 3):
            with self.subTest(role=('mrp', 'work')[ordinal_step - 2]):
                self.setUp()
                for _ in range(ordinal_step):
                    self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
                self.system.timeout = self.system.omit_create = True
                self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
                original = deepcopy(self.system.posts[-1])
                self.system.timeout = self.system.omit_create = False
                self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
                self.assertEqual(len(self.system.posts), ordinal_step + 1)
                self.authority.retry_safe = True
                expected = 'CREATED_READBACK' if ordinal_step == 2 else 'SOURCE_COMPLETE'
                self.assertEqual(self.coordinator.step(1)['status'], expected)
                self.assertEqual(self.system.posts[-1], original)
                self.assertEqual(self.authority.retry_checks[-1]['request_sha256'],
                                 self.authority.calls[-1]['request_sha256'])

    def test_dispatched_post_contract_errors_keep_original_retry_obligation(self):
        # 四类实际 POST 派发后都可能出现本地包装错误；异常名称不能证明服务器未提交。
        for index, role in enumerate(batch.ROLES):
            for error_type in (PreflightError, batch.BatchContractError):
                with self.subTest(role=role, error_type=error_type.__name__):
                    self.setUp()
                    for _ in range(index):
                        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
                    original_post = self.system.post
                    self.system.omit_create = True

                    def dispatched_then_invalid(path, payload=None):
                        original_post(path, payload=payload)
                        raise error_type('response wrapper failed after dispatch')

                    self.system.post = dispatched_then_invalid
                    self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
                    frozen_request = deepcopy(self.system.posts[-1])
                    self.assertIn(role, self.coordinator._states[0]['attempted'])
                    self.system.post = original_post
                    self.system.omit_create = False
                    self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
                    self.assertEqual(len(self.system.posts), index + 1)
                    self.assertTrue(self.authority.retry_checks)
                    self.authority.retry_safe = True
                    expected = 'SOURCE_COMPLETE' if role == 'work' else 'CREATED_READBACK'
                    self.assertEqual(self.coordinator.step(1)['status'], expected)
                    self.assertEqual(len(self.system.posts), index + 2)
                    self.assertEqual(self.system.posts[-1], frozen_request)

    def test_clear_predispatch_authority_denial_creates_no_uncertain_attempt(self):
        # 明确发生在实际 POST 之前的授权拒绝，不伪造一次未决派发。
        self.authority.deny = True
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])
        self.assertEqual(self.coordinator._states[0]['attempted'], set())
        self.authority.deny = False
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(self.authority.retry_checks, [])

    def test_default_zero_price_quote_is_not_a_verified_offer(self):
        for zero in ('0', '0.0000'):
            with self.subTest(zero=zero):
                spec = deepcopy(self.spec)
                spec['cases'][0]['priceQuote']['value'] = zero
                plan = prepare(spec)
                self.assertIn('sales_price_readback', plan['cases'][0]['blockers'])
                self.assertIsNone(plan['cases'][0]['steps'][0]['request'])

    def test_zero_price_readback_equality_cannot_authorize_a_create(self):
        spec = deepcopy(self.spec)
        spec['cases'][0]['priceQuote']['value'] = '0'
        system = SyntheticSystem(spec)
        get = system.get
        system.get = lambda path, query=None: ({'code': 200, 'data': '0.0000'}
            if path.endswith('/resolvePrice') else get(path, query))
        coordinator = batch.BatchCoordinator(spec, manifest=Manifest(TENANT), reader=system,
            scoped_reads=system, writer=batch.BusinessWriteCapability(system, authority=self.authority))
        self.assertEqual(coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(system.posts, [])

    def test_zero_price_cannot_bypass_coordinator_through_send(self):
        plan = prepare(self.spec)
        request = deepcopy(plan['cases'][0]['steps'][0]['request'])
        request['body']['soSalesOrderLineList'][0]['unitPrice'] = '0'
        with self.assertRaises(batch.BatchContractError):
            self.writer.send(request, tenant_id=TENANT, intent_id=plan['cases'][0]['intentId'],
                             target=self.system.target)
        self.assertEqual(self.system.posts, [])

    def test_native_table_readbacks_accept_derived_material_names_without_invented_columns(self):
        material = self.system.tables['bd_material'][0]
        self.assertNotIn('base_uom_name', material)
        self.assertNotIn('category_name', material)
        for table, rows in self.system.tables.items():
            for row in rows:
                self.assertLessEqual(set(row), NATIVE_COLUMNS[table])
        self.finish()
        self.assertTrue(any(table == 'bd_unit_of_measure' for table, _ in self.system.db_reads))
        self.assertTrue(any(table == 'bd_material_category' for table, _ in self.system.db_reads))

    def test_material_unit_name_needs_independent_unit_tenant_id_and_active_read(self):
        for mutation in ('missing', 'foreign', 'name', 'id', 'inactive'):
            with self.subTest(mutation=mutation):
                self.setUp()
                if mutation == 'missing':
                    self.system.tables['bd_unit_of_measure'] = []
                else:
                    row = self.system.tables['bd_unit_of_measure'][0]
                    if mutation == 'foreign':
                        row['tenant_id'] = '000002'
                    elif mutation == 'name':
                        row['uom_name'] = '吨'
                    elif mutation == 'id':
                        row['uom_id'] = '999'
                    else:
                        row['status'] = 0
                self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
                self.assertEqual(self.system.posts, [])

    def test_material_category_name_needs_independent_exact_category_binding(self):
        self.system.tables['bd_material_category'][0]['category_name'] = '无关分类'
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])

    def test_master_native_column_allowlists_are_checked_against_authoritative_ddl(self):
        allowlists = getattr(batch, 'MASTER_NATIVE_COLUMNS', None)
        self.assertIsNotNone(allowlists, 'independent master native-column allowlists missing')
        for table, columns in allowlists.items():
            self.assertLessEqual(set(columns), NATIVE_COLUMNS[table])
        self.assertNotIn('base_uom_name', allowlists['bd_material'])
        self.assertNotIn('category_name', allowlists['bd_material'])

    def test_same_second_new_record_after_microsecond_cutoff_is_not_rejected(self):
        self.system.cutoff = DAY + ' 12:00:00.654321'
        self.finish()
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 1)

    def test_previous_second_record_is_rejected_with_fractional_cutoff(self):
        self.system.cutoff = DAY + ' 12:00:00.654321'
        def earlier(system, path):
            if path == '/erp/so/orderheader':
                system.tables['so_sales_order_header'][-1]['create_time'] = DAY + ' 11:59:59'
        self.system.change_after = earlier
        self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
        self.assertEqual(self.manifest.records, [])

    def test_original_baseline_id_exclusion_remains_strict_in_the_same_second(self):
        self.system.cutoff = DAY + ' 12:00:00.654321'
        request = prepare(self.spec)['cases'][0]['steps'][0]['request']
        self.system.post(request['path'], request['body'])
        self.system.posts.clear()
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(self.system.posts, [])
        self.assertEqual(self.manifest.records, [])


class QualityContractTests(unittest.TestCase):
    setUp = CoordinatorTests.setUp
    finish = CoordinatorTests.finish

    def test_real_plan_vo_without_remark_uses_independent_native_intent(self):
        # 真实计划 VO 不含 remark；意向只能从独立原表行定位，不能伪造 API 字段。
        original_get = self.system.get

        def actual_plan_vo(path, query=None):
            response = original_get(path, query)
            if path.startswith('/erp/prd/plan/'):
                for row in response.get('rows', []):
                    row.pop('remark', None)
                if isinstance(response.get('data'), dict):
                    response['data'].pop('remark', None)
            return response

        self.system.get = actual_plan_vo
        self.finish()
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 1)
        self.assertEqual(len(self.system.posts), 4)

    def test_unrelated_manual_null_plan_mrp_does_not_block_new_case(self):
        self.system.add('prd_material_requirement', '/erp/prd/requirement', 'requirementId',
                        {'requirementId': '7701', 'requirementNo': 'MR7701', 'planId': None,
                         'status': 'DRAFT', 'sourceType': 'MANUAL'})
        self.finish()
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 1)
        self.assertEqual(len(self.system.posts), 4)

    def test_null_api_plan_is_unassociated_only_when_native_plan_is_null(self):
        row = self.system.add('prd_material_requirement', '/erp/prd/requirement', 'requirementId',
                              {'requirementId': '7701', 'requirementNo': 'MR7701', 'planId': None,
                               'status': 'DRAFT', 'sourceType': 'MANUAL'})
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        self.system.tables['prd_material_requirement'][0]['plan_id'] = (
            self.coordinator._states[0]['sources']['plan']['row']['planId'])
        self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
        self.assertEqual(len(self.system.posts), 2)
        self.assertIsNone(row['planId'])

    def test_missing_native_plan_intent_is_not_replaced_by_api_remark(self):
        self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
        def changed(system, path):
            if path == '/erp/prd/plan':
                system.tables['prd_production_plan'][-1]['remark'] = None
        self.system.change_after = changed
        self.assertEqual(self.coordinator.step(1)['status'], 'UNCERTAIN')
        self.assertEqual(len(self.manifest.records), 1)
        self.assertEqual(len(self.system.posts), 2)
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 0)

    def test_safety_stock_requires_native_proof_despite_optional_original_field(self):
        for original in ('omitted', 'null'):
            for native in (None, '1'):
                with self.subTest(original=original, native=native):
                    self.setUp()
                    material = self.spec['masterReadbacks']['material.bolt']
                    if original == 'omitted':
                        material.pop('safetyStock')
                    else:
                        material['safetyStock'] = None
                    material_id = material['materialId']
                    self.system.details['/erp/bd/material/' + material_id]['safetyStock'] = '0'
                    for row in self.system.tables['bd_material']:
                        if row['material_id'] == material_id:
                            row['safety_stock'] = native
                    self.coordinator = batch.BatchCoordinator(self.spec, manifest=self.manifest,
                        reader=self.system, scoped_reads=self.system, writer=self.writer)
                    self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
                    self.assertEqual(self.coordinator.step(1)['status'], 'CREATED_READBACK')
                    self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
                    self.assertEqual(len(self.system.posts), 2)

    def separate_primary_system(self):
        primary = {'uomId': '27', 'uomName': '个', 'isBase': 1, 'primaryId': None,
                   'conversionRate': None, 'category': '数量', 'status': 1}
        selected = self.spec['fixtureInputs']['units']['finished']
        selected.update(primaryId='27', primaryName='个')
        for row in self.spec['fixtureInputs']['unit_catalog']:
            if row['uomId'] == selected['uomId']:
                row.update(selected)
        self.spec['fixtureInputs']['unit_catalog'].append(primary)
        self.system = SyntheticSystem(self.spec)
        self.writer = batch.BusinessWriteCapability(self.system, authority=self.authority)
        self.coordinator = batch.BatchCoordinator(self.spec, manifest=self.manifest,
            reader=self.system, scoped_reads=self.system, writer=self.writer)

    def test_separate_derived_unit_primary_has_positive_independent_proof(self):
        self.separate_primary_system()
        self.finish()
        self.assertEqual(self.coordinator.summary()['sourceCompleteCases'], 1)

    def test_separate_primary_missing_foreign_or_inactive_native_row_blocks(self):
        for invalid in ('missing', 'tenant', 'status', 'category', 'is_base', 'uom_name'):
            with self.subTest(invalid=invalid):
                self.setUp()
                self.separate_primary_system()
                rows = self.system.tables['bd_unit_of_measure']
                row = next(r for r in rows if r['uom_id'] == '27')
                if invalid == 'missing':
                    rows.remove(row)
                else:
                    field, value = {'tenant': ('tenant_id', '000002'), 'status': ('status', 0),
                                    'category': ('category', '质量'), 'is_base': ('is_base', 0),
                                    'uom_name': ('uom_name', '未知')}[invalid]
                    row[field] = value
                self.assertEqual(self.coordinator.step(1)['status'], 'BLOCKED')
                self.assertEqual(self.system.posts, [])


if __name__ == '__main__':
    unittest.main()
