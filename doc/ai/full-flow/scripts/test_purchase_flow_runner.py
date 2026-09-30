"""离线契约测试；绝不连接 MySQL 或发送修改数据的 HTTP 请求。"""
import sys
import unittest
from copy import deepcopy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from purchase_flow_runner import (GateClosed, ReadOnlyDB, SQL, read_only_preflight,
                                  propose_requisition, verify_approval,
                                  recover_requisition, verify_supply_chain,
                                  receive_edit_gate)

PLAN = '2104503668382289922'
WO = '2104510684576272385'
REQ = '2104504444444444444'
DETAIL = '2104505555555555555'
VENDOR = '2104506666666666666'


class FakeDb:
    def __init__(self):
        self.calls = []
        self.tables = {
            'plan': [{'plan_id': PLAN, 'plan_no': 'PP202609290001', 'status': 'RELEASED', 'tenant_id': '000000', 'plan_name': '电机总成生产计划'}],
            'work': [{'wo_id': WO, 'wo_no': 'WO202609290001', 'plan_id': PLAN, 'source_id': REQ, 'source_type': 'MATERIAL_REQUIREMENT', 'tenant_id': '000000'}],
            'requirement': [{'requirement_id': REQ, 'plan_id': PLAN, 'status': 'ISSUED', 'tenant_id': '000000'}],
            'details': [{'detail_id': DETAIL, 'requirement_id': REQ, 'material_id': 139, 'required_quantity': '5.5000', 'required_date': '2026-10-09', 'tenant_id': '000000'}],
            'stocks': [],
            'vendor_links': [{'relation_id': '2120000000000000011', 'material_id': 139, 'vendor_id': VENDOR, 'min_order_qty': '1', 'lead_time': 2, 'status': 1, 'effective_date': '2026-01-01', 'expiry_date': '2027-01-01', 'tenant_id': '000000'}],
            'vendor': [{'vendor_id': VENDOR, 'vendor_code': 'VN0007', 'vendor_name': '华东精密器材有限公司', 'status': 'ACTIVE', 'tenant_id': '000000'}],
            'existing_requisitions': [], 'open_orders': [],
        }

    def rows(self, name, params):
        self.calls.append((name, params))
        return deepcopy(self.tables[name])


class FakeClient:
    def __init__(self):
        self.calls = []
        self.data = {
            '/system/user/getInfo': {'data': {'user': {'userId': '2104503333333333333', 'tenantId': '000000'}}},
            f'/erp/prd/plan/{PLAN}': {'data': {'planId': PLAN, 'planNo': 'PP202609290001', 'status': 'RELEASED'}},
            f'/erp/prd/work/{WO}': {'data': {'woId': WO, 'woNo': 'WO202609290001', 'planId': PLAN, 'sourceId': REQ, 'sourceType': 'MATERIAL_REQUIREMENT'}},
            f'/erp/prd/requirement/{REQ}': {'data': {'requirementId': REQ, 'planId': PLAN, 'status': 'ISSUED'}},
            '/erp/bd/material/139': {'data': {'materialId': 139, 'materialCode': 'M00139', 'materialName': '电机定子', 'status': 'ACTIVE'}},
            f'/erp/bd/vendor/{VENDOR}': {'data': {'vendorId': VENDOR, 'vendorCode': 'VN0007', 'vendorName': '华东精密器材有限公司', 'status': 'ACTIVE'}},
        }

    def get(self, path, query=None):
        self.calls.append((path, query))
        return deepcopy(self.data[path])

    def post(self, *args, **kwargs):
        raise AssertionError('write prohibited')

    def put(self, *args, **kwargs):
        raise AssertionError('write prohibited')


class PurchaseContracts(unittest.TestCase):
    def setUp(self):
        self.db, self.client = FakeDb(), FakeClient()

    def preflight(self):
        return read_only_preflight(self.client, self.db, tenant_id='000000', plan_id=PLAN,
                                   work_order_id=WO, material_ids=('139',), as_of='2026-09-29')

    def test_all_requests_are_read_only_and_tenant_bound(self):
        snapshot = self.preflight()
        self.assertTrue(snapshot['readOnly'])
        self.assertFalse(snapshot['writeEligible'])
        self.assertEqual('5.5000', snapshot['shortages'][0]['shortage'])
        self.assertEqual(DETAIL, snapshot['shortages'][0]['sourceDetailId'])
        self.assertTrue(all('000000' in [str(v) for v in values] for _, values in self.db.calls))
        self.assertTrue(all(path.startswith(('/system/', '/erp/')) for path, _ in self.client.calls))

    def test_long_ids_must_remain_exact_decimal_strings(self):
        with self.assertRaises(GateClosed):
            read_only_preflight(self.client, self.db, tenant_id='000000', plan_id=int(PLAN), work_order_id=WO, material_ids=('139',), as_of='2026-09-29')
        self.assertEqual([], self.client.calls)
        self.assertEqual([], self.db.calls)

    def test_identity_and_row_tenant_both_must_match(self):
        self.client.data['/system/user/getInfo']['data']['user']['tenantId'] = '999999'
        with self.assertRaises(GateClosed): self.preflight()
        self.db.tables['plan'][0]['tenant_id'] = '999999'
        self.client.data['/system/user/getInfo']['data']['user']['tenantId'] = '000000'
        with self.assertRaises(GateClosed): self.preflight()

    def test_plan_work_requirement_material_source_must_match(self):
        self.db.tables['work'][0]['source_id'] = '123'
        with self.assertRaises(GateClosed): self.preflight()
        self.db.tables['work'][0]['source_id'] = REQ
        self.db.tables['details'][0]['material_id'] = 140
        with self.assertRaises(GateClosed): self.preflight()

    def test_stock_frozen_excluded_and_reservations_counted(self):
        self.db.tables['stocks'] = [
            {'stock_id': '400', 'material_id': 139, 'tenant_id': '000000', 'stock_quantity': '2.0000', 'reserved_quantity': '1.2500', 'frozen_flag': 0},
            {'stock_id': '401', 'material_id': 139, 'tenant_id': '000000', 'stock_quantity': '3.0', 'reserved_quantity': '0', 'frozen_flag': 1},
        ]
        self.assertEqual('4.7500', self.preflight()['shortages'][0]['shortage'])

    def test_existing_requisition_or_open_supply_blocks_duplicate(self):
        self.db.tables['existing_requisitions'] = [{'requisition_id': '2109999999999999999', 'line_id': '2120000000000000012', 'material_id': 139, 'source_id': DETAIL, 'source_type': 'MATERIAL_REQUIREMENT', 'tenant_id': '000000'}]
        with self.assertRaises(GateClosed): self.preflight()
        self.db.tables['existing_requisitions'] = []
        self.db.tables['open_orders'] = [{'order_id': '2108888888888888888', 'material_id': 139, 'tenant_id': '000000'}]
        with self.assertRaises(GateClosed): self.preflight()

    def test_valid_vendor_must_be_active_and_link_current(self):
        self.db.tables['vendor_links'][0]['status'] = 0
        with self.assertRaises(GateClosed): self.preflight()
        self.db.tables['vendor_links'][0]['status'] = 1
        self.db.tables['vendor'][0]['status'] = 'FROZEN'
        with self.assertRaises(GateClosed): self.preflight()
        self.db.tables['vendor'][0]['status'] = 'ACTIVE'
        self.db.tables['vendor_links'][0]['expiry_date'] = '2026-09-01'
        with self.assertRaises(GateClosed): self.preflight()

    def test_minimum_order_not_inflated_or_fake_delivery_date(self):
        snap = self.preflight()
        self.db.tables['vendor_links'][0]['min_order_qty'] = '8'
        with self.assertRaises(GateClosed): propose_requisition(snap | {'vendors': self.db.tables['vendor_links']}, vendor_id=VENDOR)
        with self.assertRaises(GateClosed): propose_requisition(snap, vendor_id=VENDOR, confirmed_supply_date='2026-10-10')

    def test_intent_has_no_identifier_and_uses_real_shortage(self):
        intent = propose_requisition(self.preflight(), vendor_id=VENDOR)
        self.assertNotIn('requisitionId', intent['body'])
        self.assertNotIn('requisitionNo', intent['body'])
        self.assertEqual(DETAIL, intent['body']['puPurchaseRequisitionLineList'][0]['sourceId'])
        self.assertEqual('5.5000', intent['body']['puPurchaseRequisitionLineList'][0]['applyQuantity'])
        self.assertFalse(intent['sendable'])
        self.assertIn('external_supply_evidence', intent['missingGates'])

    def test_approval_requires_flow_process_completed_and_unchanged_data(self):
        before = {'requisitionId': '2107777777777777777', 'status': 'DRAFT', 'requisitionNo': 'PR202609290001', 'puPurchaseRequisitionLineList': [{'materialId': '139', 'sourceId': DETAIL, 'applyQuantity': '5.5000'}]}
        after = deepcopy(before); after['status'] = 'APPROVED'
        path = f"/erp/pu/requisition/{before['requisitionId']}"
        self.client.data[path] = {'data': after}
        self.client.data[path + '/proc'] = {'data': '20001234'}
        self.client.data['/workflow/process/iscompleted'] = {'data': True}
        self.assertEqual('20001234', verify_approval(self.client, 'requisition', before)['processId'])
        self.client.data[path]['data']['puPurchaseRequisitionLineList'][0]['applyQuantity'] = '8'
        with self.assertRaises(GateClosed): verify_approval(self.client, 'requisition', before)
        self.client.data[path]['data'] = after
        self.client.data[path + '/proc'] = {'data': None}
        with self.assertRaises(GateClosed): verify_approval(self.client, 'requisition', before)

    def test_recovery_rejects_ambiguous_or_cross_tenant_results(self):
        req_id, line_id = '2107777777777777777', '2107777777777777778'
        self.db.tables['existing_requisitions'] = [{'requisition_id': req_id,
            'requisition_no': 'PR202609290001', 'status': 'DRAFT', 'line_id': line_id,
            'material_id': 139, 'source_id': DETAIL, 'source_type': 'MATERIAL_REQUIREMENT',
            'tenant_id': '000000'}]
        self.client.data[f'/erp/pu/requisition/{req_id}'] = {'data': {
            'requisitionId': req_id, 'requisitionNo': 'PR202609290001', 'status': 'DRAFT',
            'puPurchaseRequisitionLineList': [{'lineId': line_id, 'materialId': 139,
                'sourceId': DETAIL, 'sourceType': 'MATERIAL_REQUIREMENT', 'applyQuantity': '5.5000'}]}}
        candidate = recover_requisition(self.client, self.db, '000000', DETAIL, '139', expected_quantity='5.5000')
        self.assertEqual(req_id, candidate)
        self.db.tables['existing_requisitions'] *= 2
        with self.assertRaises(GateClosed): recover_requisition(self.client, self.db, '000000', DETAIL, '139', expected_quantity='5.5000')
        self.db.tables['existing_requisitions'] = self.db.tables['existing_requisitions'][:1]
        self.client.data[f'/erp/pu/requisition/{req_id}']['data']['puPurchaseRequisitionLineList'][0]['applyQuantity'] = '50'
        with self.assertRaises(GateClosed): recover_requisition(self.client, self.db, '000000', DETAIL, '139', expected_quantity='5.5000')

    def chain(self):
        """GET 明细 VO 字段：采购订单行有 requisitionLineId；到货通知没有 orderLineId；
        收货 VO 有 sourceDocLineId，没有 purchaseLineId，但实体有该字段。"""
        req = {'requisitionId': '2107777777777777777', 'requisitionNo': 'PR202609290001',
               'status': 'ORDERED', 'puPurchaseRequisitionLineList': [
                   {'lineId': '2107777777777777781', 'requisitionId': '2107777777777777777',
                    'materialId': '139', 'materialCode': 'M00139', 'sourceType': 'MATERIAL_REQUIREMENT',
                    'sourceId': DETAIL, 'applyQuantity': '5.5000'}]}
        order = {'orderId': '2107777777777777778', 'orderNo': 'PO202609290001',
                 'requisitionId': req['requisitionId'], 'requisitionNo': req['requisitionNo'],
                 'vendorId': VENDOR, 'expectedArrivalDate': '2026-10-10', 'status': 'RECEIVED',
                 'lineList': [{'lineId': '2107777777777777782', 'orderId': '2107777777777777778',
                               'requisitionLineId': '2107777777777777781', 'materialId': '139',
                               'materialCode': 'M00139', 'orderQuantity': '5.5000',
                               'receivedQuantity': '0'}]}
        notice = {'noticeId': '2107777777777777779', 'noticeCode': 'AN202609290001',
                  'purOrderCode': order['orderNo'], 'vendorId': VENDOR, 'status': 'APPROVED',
                  'wmArrivalNoticeLineList': [{'lineId': '2107777777777777783',
                                               'noticeId': '2107777777777777779',
                                               'materialId': '139', 'materialCode': 'M00139',
                                               'quantityArrival': '5.5000',
                                               'quantityQuanlified': '5.5000'}]}
        receive = {'receiveId': '2107777777777777780', 'receiveCode': 'RC202609290001',
                   'noticeId': notice['noticeId'], 'noticeCode': notice['noticeCode'],
                   'purId': order['orderId'], 'purCode': order['orderNo'],
                   'vendorId': VENDOR, 'status': 'DRAFT',
                   'wmMaterialReceiveLineList': [{'lineId': '2107777777777777784',
                                                  'receiveId': '2107777777777777780',
                                                  'sourceDocLineId': None, 'materialId': '139',
                                                  'materialCode': 'M00139', 'quantityReceived': '0'}]}
        return req, order, notice, receive

    def test_supply_chain_must_have_server_codes_and_exact_links(self):
        req, order, notice, receive = self.chain()
        self.assertEqual('pending_physical_receipt', verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)['gate'])
        receive['purId'] = req['requisitionId']
        with self.assertRaises(GateClosed): verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)

    def test_supply_chain_rejects_four_unrelated_child_materials(self):
        req, order, notice, receive = self.chain()
        order['lineList'][0]['materialId'] = '140'
        notice['wmArrivalNoticeLineList'][0]['materialId'] = '145'
        receive['wmMaterialReceiveLineList'][0]['materialId'] = '262'
        with self.assertRaises(GateClosed): verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)

    def test_supply_chain_rejects_missing_or_ambiguous_children(self):
        for doc, field in ((0, 'puPurchaseRequisitionLineList'), (1, 'lineList'),
                           (2, 'wmArrivalNoticeLineList'), (3, 'wmMaterialReceiveLineList')):
            chain = list(self.chain()); chain[doc].pop(field)
            with self.subTest(missing=field), self.assertRaises(GateClosed):
                verify_supply_chain(*chain, vendor_id=VENDOR)
            chain = list(self.chain()); chain[doc][field] = []
            with self.subTest(empty=field), self.assertRaises(GateClosed):
                verify_supply_chain(*chain, vendor_id=VENDOR)
            chain = list(self.chain()); chain[doc][field].append(deepcopy(chain[doc][field][0]))
            with self.subTest(duplicate=field), self.assertRaises(GateClosed):
                verify_supply_chain(*chain, vendor_id=VENDOR)

    def test_supply_chain_rejects_broken_parent_and_source_links(self):
        for doc, field, value in ((0, 'requisitionId', '999'), (0, 'sourceId', '999.5'),
                                  (0, 'sourceType', 'MANUAL'), (1, 'orderId', '999'),
                                  (1, 'requisitionLineId', '999'), (2, 'noticeId', '999'),
                                  (2, 'orderLineId', '999'), (3, 'receiveId', '999'),
                                  (3, 'sourceDocLineId', '999'), (3, 'purchaseLineId', '999')):
            chain = list(self.chain())
            key = ('puPurchaseRequisitionLineList', 'lineList', 'wmArrivalNoticeLineList',
                   'wmMaterialReceiveLineList')[doc]
            chain[doc][key][0][field] = value
            with self.subTest(field=field, doc=doc), self.assertRaises(GateClosed):
                verify_supply_chain(*chain, vendor_id=VENDOR)

    def test_supply_chain_rejects_unmatched_and_overreceived_quantities(self):
        for doc, field, amount in ((0, 'applyQuantity', None), (1, 'orderQuantity', '6'),
                                   (1, 'receivedQuantity', '6'), (2, 'quantityArrival', '6'),
                                   (2, 'quantityArrival', None), (2, 'quantityQuanlified', '6'),
                                   (3, 'quantityReceived', '6'), (3, 'quantityReceived', None)):
            chain = list(self.chain())
            key = ('puPurchaseRequisitionLineList', 'lineList', 'wmArrivalNoticeLineList',
                   'wmMaterialReceiveLineList')[doc]
            chain[doc][key][0][field] = amount
            with self.subTest(field=field, doc=doc, amount=amount), self.assertRaises(GateClosed):
                verify_supply_chain(*chain, vendor_id=VENDOR)

    def test_supply_chain_rejects_unexplained_reduction_from_converted_requisition(self):
        req, order, notice, receive = self.chain()
        order['lineList'][0]['orderQuantity'] = '4'
        notice['wmArrivalNoticeLineList'][0]['quantityArrival'] = '4'
        notice['wmArrivalNoticeLineList'][0]['quantityQuanlified'] = '4'
        with self.assertRaises(GateClosed):
            verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)
        req['puPurchaseRequisitionLineList'][0]['approvedQuantity'] = '4'
        self.assertFalse(verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)['writeEligible'])

    def test_supply_chain_normalizes_only_safe_small_parent_ids(self):
        req, order, notice, receive = self.chain()
        req['requisitionId'] = 7
        req['puPurchaseRequisitionLineList'][0]['requisitionId'] = '7'
        order['requisitionId'] = '7'
        self.assertEqual('pending_physical_receipt',
                         verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)['gate'])
        req['requisitionId'] = int('2107777777777777777')
        with self.assertRaises(GateClosed):
            verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)

    def test_supply_chain_is_a_pure_read_only_check_of_supplied_vo_snapshots(self):
        chain = self.chain()
        frozen = deepcopy(chain)
        result = verify_supply_chain(*chain, vendor_id=VENDOR)
        self.assertEqual(frozen, chain)
        self.assertFalse(result['stockPosted'])
        self.assertFalse(result['writeEligible'])
        for doc, key in ((0, 'puPurchaseRequisitionLineList'), (1, 'lineList'),
                         (2, 'wmArrivalNoticeLineList'), (3, 'wmMaterialReceiveLineList')):
            broken = list(self.chain())
            broken[doc][key][0]['lineId'] = int('2107777777777777784')
            with self.subTest(unsafe_line_id=doc), self.assertRaises(GateClosed):
                verify_supply_chain(*broken, vendor_id=VENDOR)

    def test_supply_chain_rejects_order_above_approved_amount(self):
        req, order, notice, receive = self.chain()
        req['puPurchaseRequisitionLineList'][0]['approvedQuantity'] = '3'
        with self.assertRaises(GateClosed):
            verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)

    def test_supply_chain_rejects_receipt_above_qualified_arrival(self):
        req, order, notice, receive = self.chain()
        notice['wmArrivalNoticeLineList'][0]['quantityQuanlified'] = '3'
        receive['wmMaterialReceiveLineList'][0]['quantityReceived'] = '4'
        with self.assertRaises(GateClosed):
            verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)

    def test_supply_chain_rejects_receive_details_above_line_or_wrong_parent(self):
        for field, wrong in (('quantity', '5'), ('lineId', '2107777777777777783'),
                             ('materialId', '140')):
            req, order, notice, receive = self.chain()
            receive['wmMaterialReceiveLineList'][0]['quantityReceived'] = '2'
            detail = {'detailId': '2107777777777777785', 'lineId': '2107777777777777784',
                      'receiveId': '2107777777777777780', 'materialId': '139', 'quantity': '2'}
            detail[field] = wrong
            receive['wmMaterialReceiveLineList'][0]['wmMaterialReceiveLineDetailList'] = [detail]
            with self.subTest(field=field), self.assertRaises(GateClosed):
                verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)

    def test_supply_chain_accepts_explicit_links_and_partial_receipt_limits(self):
        req, order, notice, receive = self.chain()
        notice['wmArrivalNoticeLineList'][0]['quantityArrival'] = '4'
        notice['wmArrivalNoticeLineList'][0]['quantityQuanlified'] = '3'
        receive['wmMaterialReceiveLineList'][0]['quantityReceived'] = '2'
        receive['wmMaterialReceiveLineList'][0]['sourceDocLineId'] = notice['wmArrivalNoticeLineList'][0]['lineId']
        receive['wmMaterialReceiveLineList'][0]['purchaseLineId'] = order['lineList'][0]['lineId']
        self.assertEqual('pending_physical_receipt',
                         verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)['gate'])

    def test_supply_chain_accepts_unambiguous_two_material_lines_and_valid_shelf_detail(self):
        req, order, notice, receive = self.chain()
        req_line = deepcopy(req['puPurchaseRequisitionLineList'][0])
        po_line = deepcopy(order['lineList'][0])
        notice_line = deepcopy(notice['wmArrivalNoticeLineList'][0])
        receipt_line = deepcopy(receive['wmMaterialReceiveLineList'][0])
        req_line.update(lineId='2107777777777777786', materialId='140', materialCode='M00140',
                        sourceId='2107777777777777787', applyQuantity='2')
        po_line.update(lineId='2107777777777777788', requisitionLineId=req_line['lineId'],
                       materialId='140', materialCode='M00140', orderQuantity='2')
        notice_line.update(lineId='2107777777777777789', materialId='140',
                           materialCode='M00140', quantityArrival='2', quantityQuanlified='2')
        receipt_line.update(lineId='2107777777777777790', materialId='140',
                            materialCode='M00140', quantityReceived='1',
                            sourceDocLineId=notice_line['lineId'])
        req['puPurchaseRequisitionLineList'].append(req_line)
        order['lineList'].append(po_line)
        notice['wmArrivalNoticeLineList'].append(notice_line)
        receive['wmMaterialReceiveLineList'].append(receipt_line)
        receipt_line['wmMaterialReceiveLineDetailList'] = [{
            'detailId': '2107777777777777791', 'receiveId': receive['receiveId'],
            'lineId': receipt_line['lineId'], 'materialId': '140', 'quantity': '1'}]
        proof = verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)
        self.assertEqual(2, proof['childMaterialsVerified'])
        self.assertFalse(proof['writeEligible'])
        receive['wmMaterialReceiveLineList'][0]['materialId'] = '141'
        with self.assertRaises(GateClosed):
            verify_supply_chain(req, order, notice, receive, vendor_id=VENDOR)

    def test_receive_edit_contract_is_closed_until_line_mapping_fix(self):
        with self.assertRaises(GateClosed): receive_edit_gate({'purchaseLineId': '2107777777777777781'}, {'sourceDocLineId': '2107777777777777781'})

    def test_approval_freezes_header_source_and_purchase_prices(self):
        before = {'orderId': '2107777777777777778', 'orderNo': 'PO202609290001',
                  'status': 'DRAFT', 'vendorId': VENDOR, 'expectedArrivalDate': '2026-10-10',
                  'requisitionId': '2107777777777777777', 'currency': 'CNY',
                  'lineList': [{'materialId': 139, 'requisitionLineId': DETAIL,
                                'orderQuantity': '5.5000', 'unitPrice': '10.00'}]}
        after = deepcopy(before); after['status'] = 'APPROVED'
        path = f"/erp/pu/order/{before['orderId']}"
        self.client.data[path] = {'data': after}
        self.client.data[path + '/proc'] = {'data': '20001234'}
        self.client.data['/workflow/process/iscompleted'] = {'data': True}
        self.assertTrue(verify_approval(self.client, 'order', before)['approved'])
        self.client.data[path]['data']['lineList'][0]['unitPrice'] = '100.00'
        with self.assertRaises(GateClosed): verify_approval(self.client, 'order', before)
        self.client.data[path]['data'] = deepcopy(after)
        self.client.data[path]['data']['currency'] = 'USD'
        with self.assertRaises(GateClosed): verify_approval(self.client, 'order', before)

    def test_preflight_rejects_api_work_source_mismatch(self):
        self.client.data[f'/erp/prd/work/{WO}']['data']['sourceType'] = 'SALES_ORDER'
        with self.assertRaises(GateClosed): self.preflight()

    def test_approved_arrival_is_not_physical_receipt(self):
        # 到货回调会提前将采购订单标记为 RECEIVED；必须阻止重复订购，直到
        # 收货过账与库存及供应事实完成核对。
        self.assertIn("'RECEIVED'", SQL['open_orders'])
    def test_business_quantity_and_supplier_id_are_not_fabricated(self):
        snap = self.preflight()
        self.assertEqual('1', snap['vendors'][0]['minOrderQty'])
        snap['vendors'][0]['minOrderQty'] = '8'
        with self.assertRaises(GateClosed): propose_requisition(snap, vendor_id=VENDOR)
        with self.assertRaises(GateClosed): propose_requisition(self.preflight(), vendor_id='2100000000000009999')
        self.db.tables['stocks'][0:0] = [{'stock_id': '400', 'material_id': 139, 'tenant_id': '000000',
                                          'stock_quantity': '7', 'reserved_quantity': '0', 'frozen_flag': 0}]
        with self.assertRaises(GateClosed): self.preflight()
    def test_db_reader_rejects_non_select_and_never_logs_secrets(self):
        class Cursor:
            def execute(self, statement, params):
                self.statement, self.params = statement, params
            def fetchall(self): return []
            def __enter__(self): return self
            def __exit__(self, *exc): pass
        class Connection:
            def __init__(self): self.cursor_ = Cursor()
            def cursor(self): return self.cursor_
        db = ReadOnlyDB(Connection())
        db.rows('plan', (PLAN, '000000'))
        self.assertTrue(db.connection.cursor_.statement.startswith('SELECT '))
        self.assertEqual((PLAN, '000000'), db.connection.cursor_.params)
        with self.assertRaises(GateClosed): db.rows('DELETE FROM pu_purchase_order', ())


if __name__ == '__main__':
    unittest.main()
