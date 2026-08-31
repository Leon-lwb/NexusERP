#!/usr/bin/env python3
"""ERP 仓储管理流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 入库通道 ----
    d.container('st1', '① 入库通道（收货 → 上架 → 存储）', 140, 40, 960, 900)
    d.box('a1', '采购入库 ReceiveInStrategy\n← 采购订单收货', 'doc', 'st1', 300, 90, 340, 60)
    d.box('a2', '生产完工入库 ProductionCompletionStrategy\n← 工单完工', 'doc', 'st1', 300, 200, 340, 60)
    d.box('a3', '委外收货 SubcontractReceiveStrategy\n← 委外完成', 'doc', 'st1', 300, 310, 340, 60)
    d.box('a4', '返工入库 ReworkReceiveStrategy\n← 返工完成', 'doc', 'st1', 300, 420, 340, 60)
    d.box('a5', '杂项入库 MiscRecptStrategy\n← 其他入库', 'doc', 'st1', 300, 530, 340, 60)
    d.box('a6', '到货登记 → 质检（IQC）→ 收货确认\n（需检物料先检验）', 'auto', 'st1', 300, 650, 340, 60)
    d.box('a7', '上架 PutawayStrategy\nNEAREST_EMPTY / CLASS_BASED / FIFO_LOCATION\n策略推荐库位', 'auto', 'st1', 300, 760, 340, 70)
    d.edge('e1', 'a1', 'a6')
    d.edge('e2', 'a2', 'a6')
    d.edge('e3', 'a3', 'a6')
    d.edge('e4', 'a4', 'a6')
    d.edge('e5', 'a5', 'a6')
    d.edge('e6', 'a6', 'a7')
    # ---- ② 出库通道 ----
    d.container('st2', '② 出库通道（拣货 → 出库）', 140, 980, 960, 720)
    d.box('b1', '销售出库 SalesOutStrategy\n← 销售订单履行', 'doc', 'st2', 300, 1030, 340, 60)
    d.box('b2', '生产领料 ProductionIssueStrategy\n← 工单领料', 'doc', 'st2', 300, 1140, 340, 60)
    d.box('b3', '委外发料 SubcontractIssueStrategy\n← 委外发料', 'doc', 'st2', 300, 1250, 340, 60)
    d.box('b4', '返工出库 ReworkIssueStrategy\n← 返工领料', 'doc', 'st2', 300, 1360, 340, 60)
    d.box('b5', '采购退货出库\n← 采购退货执行', 'doc', 'st2', 300, 1470, 340, 60)
    d.box('b6', '拣货 PickingStrategy\nFIFO / FEFO 策略推荐拣货库位', 'auto', 'st2', 300, 1580, 340, 60)
    d.box('b7', '拣货确认 → 出库过账（扣减库存）', 'auto', 'st2', 300, 1690, 340, 60)
    d.edge('e7', 'b1', 'b6')
    d.edge('e8', 'b2', 'b6')
    d.edge('e9', 'b3', 'b6')
    d.edge('e10', 'b4', 'b6')
    d.edge('e11', 'b5', 'b6')
    d.edge('e12', 'b6', 'b7')
    # ---- ③ 库内操作 ----
    d.container('st3', '③ 库内操作', 140, 1740, 960, 500)
    d.box('c1', '库存调拨 TransferStrategy\n仓间 / 库区间移动', 'doc', 'st3', 300, 1790, 340, 60)
    d.box('c2', '盘点 CountAdjustStrategy\n盘点计划 → 生成盘点行 → 过账调整', 'doc', 'st3', 300, 1900, 340, 60)
    d.box('c3', '盘点差异审批（Flowable）', 'decision', 'st3', 300, 2010, 340, 72)
    d.box('c4', '过账调整 → 库存修正', 'auto', 'st3', 300, 2130, 340, 60)
    d.edge('e13', 'c1', 'c1', '仓间移动', anchors=(1.0, 0.5, 0.0, 0.5))
    d.edge('e14', 'c2', 'c3')
    d.edge('e15', 'c3', 'c4', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 2046)])
    d.reject('r1', 'c3', 'c2', 2046, 1960)
    # ---- ④ 辅助能力 ----
    d.container('st4', '④ 辅助能力', 140, 2280, 960, 500)
    d.box('d1', '库存估值：移动平均法', 'auto', 'st4', 300, 2330, 340, 60)
    d.box('d2', '库存追溯：正向（原材料→成品）\n反向（成品→原材料）', 'auto', 'st4', 300, 2440, 340, 60)
    d.box('d3', '库存预警：定时任务自动检查', 'auto', 'st4', 300, 2550, 340, 60)
    d.box('d4', '库存对账：账面 vs 事务余额', 'auto', 'st4', 300, 2660, 340, 60)
    d.box('d5', '批次 / 序列号 / 条码：全生命周期管理', 'auto', 'st4', 300, 2770, 340, 60)
    d.edge('e16', 'd1', 'd2')
    d.edge('e17', 'd2', 'd3')
    d.edge('e18', 'd3', 'd4')
    d.edge('e19', 'd4', 'd5')
    # ---- 图例 ----
    d.legend(140, 2820, [('doc', '单据 / 操作'), ('decision', '审批 / 判断'), ('auto', '系统自动'), ('state', '状态')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../erp/仓储管理流程.drawio')
