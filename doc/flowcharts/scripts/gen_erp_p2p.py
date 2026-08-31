#!/usr/bin/env python3
"""ERP 采购到付款流程 — 使用 flowchart_lib 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- Stage 1：采购申请 ----
    d.container('st1', '① 采购申请', 140, 40, 960, 400)
    d.box('a1', '开始', 'start', 'st1', 440, 90, 60, 40)
    d.box('a2', '创建采购申请 PuPurchaseRequisition\n来源：MRP物料需求 / 低库存预警 / 手工', 'doc', 'st1', 340, 170, 260, 60)
    d.box('a3', '审批流\nFlowable', 'decision', 'st1', 390, 280, 160, 72)
    d.box('a4', '采购申请\nAPPROVED', 'auto', 'st1', 410, 370, 120, 50)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(560, 316)])
    d.reject('r1', 'a3', 'a2', 316, 230)
    d.edge('e4', 'a4', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- Stage 2：采购订单 ----
    d.container('st2', '② 采购订单', 140, 480, 960, 500)
    d.box('b1', '转采购订单 PuPurchaseOrder\n支持合并 / 拆分', 'doc', 'st2', 340, 530, 260, 60)
    d.box('b2', '审批流\nFlowable', 'decision', 'st2', 390, 640, 160, 72)
    d.box('b3', '采购订单\nAPPROVED', 'auto', 'st2', 410, 770, 120, 50)
    d.box('b4', 'PO状态机：\nDRAFT→APPROVING→APPROVED\n→PARTIALLY_RECEIVED→RECEIVED', 'state', 'st2', 340, 880, 260, 60)
    d.edge('e5', 'b1', 'b2')
    d.edge('e6', 'b2', 'b3', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(560, 676)])
    d.reject('r2', 'b2', 'b1', 676, 590)
    d.edge('e7', 'b3', 'b4', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- Stage 3：收货与入库 ----
    d.container('st3', '③ 收货与入库', 140, 1030, 960, 1210)
    d.box('c1', '自动生成到货通知\nWmArrivalNotice', 'doc', 'st3', 340, 1090, 260, 60)
    d.box('c2', '到货通知审批', 'decision', 'st3', 390, 1200, 160, 72)
    d.box('c3', '自动创建物料入库单 WmMaterialReceive\n（IQC需检物料→检验后再入库）', 'doc', 'st3', 310, 1320, 320, 70)
    d.box('c4', '入库单审批', 'decision', 'st3', 390, 1440, 160, 72)
    d.box('c5', 'APPROVED 待上架\n（不再直接发 StockInMessage）', 'state', 'st3', 340, 1560, 260, 60)
    d.box('c6', '上架：添加上架明细（分配库位）→ 确认上架 confirmShelf\n记录上架人 shelfBy + 上架时间 shelfTime', 'doc', 'st3', 280, 1670, 380, 70)
    d.box('c7', 'SHELVED 待入库', 'state', 'st3', 370, 1780, 200, 50)
    d.box('c8', '执行入库 executeReceive\n① 防重校验 hasPosted ② 虚拟库位扣减 + 目标库位增加\n③ 入库单状态→FINISHED ④ 到货通知状态→FINISHED ⑤ 生成条码', 'auto', 'st3', 280, 1880, 380, 90)
    d.box('c9', '事务提交后 WmMqSendService @Async\n发送 PurchaseReceiveWritebackMessage', 'auto', 'st3', 290, 2010, 360, 60)
    d.box('c10', 'PurchaseReceiveWritebackConsumer\nTenantHelper.dynamic 恢复租户上下文\n① 按物料ID/编码匹配收货行与采购行 ② 累加已收货数量\n③ 采购订单状态→PARTIALLY_RECEIVED / RECEIVED', 'auto', 'st3', 260, 2120, 420, 80)
    d.edge('e8', 'b4', 'c1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e9', 'c1', 'c2')
    d.edge('e10', 'c2', 'c3', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(560, 1236)])
    d.reject('r3', 'c2', 'c1', 1236, 1150)
    d.edge('e11', 'c3', 'c4')
    d.edge('e12', 'c4', 'c5', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(560, 1476)])
    d.reject('r4', 'c4', 'c3', 1476, 1390)
    d.edge('e13', 'c5', 'c6')
    d.edge('e14', 'c6', 'c7')
    d.edge('e15', 'c7', 'c8')
    d.edge('e16', 'c8', 'c9')
    d.edge('e17', 'c9', 'c10')
    # ---- Stage 4：付款 / 退货 ----
    d.container('st4', '④ 付款 / 退货', 140, 2290, 960, 390)
    d.box('d1', '发票校验（财务）', 'auto', 'st4', 400, 2360, 140, 50)
    d.box('d2', '付款申请 → 付款执行\n支付回调 PAY_RECEIVED_CALLBACK\n→ 自动收款 / 核销', 'end', 'st4', 350, 2470, 240, 70)
    d.box('d3', '采购退货 PuPurchaseReturn\n[审批流]', 'doc', 'st4', 680, 2360, 200, 60)
    d.box('d4', '确认 → 执行退货出库\n→ 扣减库存', 'end', 'st4', 680, 2480, 200, 60)
    d.edge('e18', 'c10', 'd1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e19', 'd1', 'd2', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e20', 'c3', 'd3', '如需退货', anchors=(1.0, 0.5, 0.0, 0.5), color='#b85450', dashed=True,
           waypoints=[(1300, 1355), (1300, 2630), (620, 2630), (620, 2390)])
    d.edge('e21', 'd3', 'd4', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- 图例 ----
    d.legend(140, 2720, [('doc', '单据 / 操作'), ('decision', '审批 / 判断'), ('auto', '系统自动'), ('state', '状态 / 虚线 = 驳回·退货')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../erp/采购到付款.drawio')
