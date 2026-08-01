#!/usr/bin/env python3
"""ERP 委外与返工流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 委外流程 ----
    d.container('st1', '① 委外流程（Subcontract）', 140, 40, 960, 600)
    d.box('a1', '创建委外单\n（关联 BOM / 工艺）', 'doc', 'st1', 330, 90, 280, 60)
    d.box('a2', '下达委外单', 'auto', 'st1', 340, 200, 260, 60)
    d.box('a3', '发料 SubcontractIssueStrategy\n（库存出库）', 'auto', 'st1', 330, 310, 280, 60)
    d.box('a4', '供应商加工', 'state', 'st1', 360, 420, 220, 50)
    d.box('a5', '委外收货 SubcontractReceiveStrategy\n（库存入库）', 'auto', 'st1', 330, 510, 280, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4')
    d.edge('e4', 'a4', 'a5')
    # ---- ② 返工流程 ----
    d.container('st2', '② 返工流程（Rework）', 140, 680, 960, 560)
    d.box('b1', '创建返工单\n（关联原工单）', 'doc', 'st2', 330, 730, 280, 60)
    d.box('b2', '下达返工单', 'auto', 'st2', 340, 840, 260, 60)
    d.box('b3', '出库 ReworkIssueStrategy\n（返工领料）', 'auto', 'st2', 330, 950, 280, 60)
    d.box('b4', '执行返工作业', 'state', 'st2', 360, 1060, 220, 50)
    d.box('b5', '返工入库 ReworkReceiveStrategy\n（回写报废数量到来源工单）', 'auto', 'st2', 330, 1150, 280, 60)
    d.edge('e5', 'b1', 'b2')
    d.edge('e6', 'b2', 'b3')
    d.edge('e7', 'b3', 'b4')
    d.edge('e8', 'b4', 'b5')
    # ---- ③ MQ 联动 ----
    d.container('st3', '③ MQ 异步联动', 140, 1280, 960, 400)
    d.box('c1', 'REWORK_STOCK_QUEUE\n返工出库/入库 → 更新返工订单状态', 'auto', 'st3', 300, 1330, 340, 60)
    d.box('c2', 'SUBCONTRACT_STOCK_QUEUE\n委外发料/收货 → 更新委外订单状态\nISSUE→IN_PROGRESS, RECEIVE→COMPLETED', 'auto', 'st3', 300, 1440, 340, 70)
    d.box('c3', 'TenantHelper.dynamic() 恢复租户上下文\n事务提交后 WmMqSendService(@Async) 发送', 'auto', 'st3', 300, 1560, 340, 70)
    d.edge('e9', 'a3', 'c1', '发料', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(960, 330), (960, 1370), (650, 1370)])
    d.edge('e10', 'b3', 'c1', '出库', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(960, 990), (960, 1400), (650, 1400)])
    d.edge('e11', 'a5', 'c2', '收货', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(960, 540), (960, 1480), (650, 1480)])
    d.edge('e12', 'b5', 'c2', '入库', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(960, 1180), (960, 1510), (650, 1510)])
    d.edge('e13', 'c1', 'c3')
    d.edge('e14', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1720, [('doc', '单据 / 操作'), ('auto', '系统自动'), ('state', '状态')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../erp/委外与返工流程.drawio')
