#!/usr/bin/env python3
"""财务 AR/AP 核销流程（F1）— 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 销售开票（AR） ----
    d.container('st1', '① 销售开票（AR）', 140, 40, 960, 520)
    d.box('a1', '销售订单履行事件\nSalesOrderFulfilledEvent 监听器', 'auto', 'st1', 300, 90, 340, 60)
    d.box('a2', '自动生成发票草稿 so_invoice\n（统一状态机）', 'doc', 'st1', 300, 200, 340, 60)
    d.box('a3', '发票状态机\nDRAFT→ISSUED→VERIFIED\n→CANCELLED', 'state', 'st1', 300, 310, 340, 60)
    d.box('a4', '发票过账 → 生成 AR 子账\n（统驭科目）', 'auto', 'st1', 300, 420, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4', 'ISSUED', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 340), (650, 450)])
    # ---- ② 采购进项（AP） ----
    d.container('st2', '② 采购进项发票（AP）', 140, 600, 960, 520)
    d.box('b1', '三单匹配\nPO + GR（收货）+ Invoice', 'decision', 'st2', 300, 650, 340, 72)
    d.box('b2', 'pu_invoice（镜像 so）\n含认证子状态机\nUNCERTIFIED→CERTIFIED→DEDUCTED', 'doc', 'st2', 300, 770, 340, 70)
    d.box('b3', '发票过账 → 生成 AP 子账\n（退货 → 进项转出 TRANSFERRED）', 'auto', 'st2', 300, 890, 340, 60)
    d.edge('e4', 'b1', 'b2', '匹配通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 686), (650, 800)])
    d.edge('e5', 'b2', 'b3', '过账', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- ③ 收付款与核销 ----
    d.container('st3', '③ 收付款与核销', 140, 1160, 960, 560)
    d.box('c1', '收款（AR）FinArApReceipt\n付款（AP）FinArApPayment', 'doc', 'st3', 300, 1210, 340, 60)
    d.box('c2', '支付回调 PAY_RECEIVED_CALLBACK\n→ 自动收款 / 核销', 'auto', 'st3', 300, 1320, 340, 60)
    d.box('c3', '核销 FinArApSettlement\n（发票 ↔ 收付款匹配）', 'auto', 'st3', 300, 1430, 340, 60)
    d.box('c4', '账龄分析 / 对账', 'auto', 'st3', 300, 1540, 340, 60)
    d.edge('e6', 'a4', 'c1', '', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e7', 'b3', 'c1', '', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e8', 'c1', 'c2')
    d.edge('e9', 'c2', 'c3')
    d.edge('e10', 'c3', 'c4')
    # ---- 图例 ----
    d.legend(140, 1760, [('doc', '单据 / 操作'), ('decision', '匹配判断'), ('auto', '系统自动'), ('state', '状态')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../fin/应收应付核销流程.drawio')
