#!/usr/bin/env python3
"""ERP 销售到收款流程（Order-to-Cash）— 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 报价与订单 ----
    d.container('st1', '① 报价与订单', 140, 40, 960, 500)
    d.box('a1', '客户订单 / 询价\nCustomerOrder', 'doc', 'st1', 340, 90, 260, 60)
    d.box('a2', '创建报价单 Quotation\n价格引擎自动取价 + 折扣', 'doc', 'st1', 340, 200, 260, 60)
    d.box('a3', '确认报价 → 转销售订单 convertToOrder\nSalesOrder', 'auto', 'st1', 340, 310, 260, 60)
    d.box('a4', '信用检查 ICreditCheckService\n+ 定价引擎 IPricingEngineService', 'decision', 'st1', 340, 420, 260, 72)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4')
    # ---- ② 审批与确认 ----
    d.container('st2', '② 审批与确认', 140, 580, 960, 500)
    d.box('b1', '提交 Flowable 审批', 'decision', 'st2', 340, 630, 260, 72)
    d.box('b2', '确认订单\n（信用额度冻结 + 库存预留）', 'auto', 'st2', 340, 750, 260, 60)
    d.box('b3', '订单履行\n（行级 linkedMoId / linkedPoId）', 'auto', 'st2', 340, 860, 260, 60)
    d.box('b4', '关闭订单', 'end', 'st2', 340, 970, 200, 50)
    d.edge('e4', 'a4', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.reject('r1', 'b1', 'a3', 666, 370)
    d.edge('e5', 'b1', 'b2', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(610, 666)])
    d.edge('e6', 'b2', 'b3')
    d.edge('e7', 'b3', 'b4', '无待履行', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- ③ 出库与发货 ----
    d.container('st3', '③ 出库与发货', 140, 1120, 960, 620)
    d.box('c1', '销售出库 WmSalesIssue\n（拣货策略 FIFO / FEFO）', 'doc', 'st3', 330, 1170, 280, 60)
    d.box('c2', '出库审批（Flowable）', 'decision', 'st3', 340, 1280, 260, 72)
    d.box('c3', '拣货确认 → 出库过账\n（扣减库存）', 'auto', 'st3', 330, 1400, 280, 60)
    d.box('c4', '物流安排 → 发货确认', 'auto', 'st3', 340, 1510, 260, 60)
    d.box('c5', '如需退货\n→ 销售退货 Return [审批流] → 退货入库', 'state', 'st3', 300, 1630, 340, 60)
    d.edge('e8', 'b3', 'c1', '履行', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e9', 'c1', 'c2')
    d.reject('r2', 'c2', 'c1', 1316, 1230)
    d.edge('e10', 'c2', 'c3', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(610, 1316)])
    d.edge('e11', 'c3', 'c4')
    d.edge('e12', 'c4', 'c5')
    # ---- ④ 收款 ----
    d.container('st4', '④ 收款与发票', 140, 1780, 960, 460)
    d.box('d1', '销售开票 so_invoice\n（订单履行事件自动生成草稿）', 'doc', 'st4', 330, 1830, 280, 60)
    d.box('d2', '发票状态机\nDRAFT→ISSUED→VERIFIED\n→CANCELLED', 'state', 'st4', 330, 1940, 280, 60)
    d.box('d3', '收款核销 FinArApSettlement\n（支付回调自动核销）', 'auto', 'st4', 330, 2050, 280, 60)
    d.box('d4', '完成', 'end', 'st4', 370, 2160, 200, 50)
    d.edge('e13', 'c4', 'd1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e14', 'd1', 'd2')
    d.edge('e15', 'd2', 'd3')
    d.edge('e16', 'd3', 'd4')
    # ---- 图例 ----
    d.legend(140, 2280, [('doc', '单据 / 操作'), ('decision', '审批 / 判断'), ('auto', '系统自动'), ('state', '状态 / 虚线 = 驳回·退货')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../erp/销售到收款流程.drawio')
