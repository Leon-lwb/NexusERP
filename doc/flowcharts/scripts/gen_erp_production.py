#!/usr/bin/env python3
"""ERP 生产制造流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 计划与物料需求 ----
    d.container('st1', '① 生产计划与物料需求（MRP）', 140, 40, 960, 560)
    d.box('a1', '销售订单行\n（含 linkedMoId）', 'doc', 'st1', 340, 90, 260, 60)
    d.box('a2', '创建生产计划 ProductionPlan', 'doc', 'st1', 340, 200, 260, 60)
    d.box('a3', 'BOM 树计算\n（当前 BOM + 版本）', 'auto', 'st1', 340, 310, 260, 60)
    d.box('a4', '生成物料需求 MaterialRequirement\n（导入 BOM 明细）', 'auto', 'st1', 340, 420, 260, 60)
    d.box('a5', '生成生产工单 WorkOrder\n（含 BOM / 工艺路线信息）', 'auto', 'st1', 340, 530, 260, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4')
    d.edge('e4', 'a4', 'a5')
    # ---- ② 工单与领料 ----
    d.container('st2', '② 工单下达与领料', 140, 640, 960, 560)
    d.box('b1', '工单审批（Flowable）', 'decision', 'st2', 340, 690, 260, 72)
    d.box('b2', '下达工单 → 开始生产\n（MES 同步工单）', 'auto', 'st2', 340, 810, 260, 60)
    d.box('b3', '生产领料 ProductionIssue\n（根据 BOM 自动生成，支持超领/欠领）', 'doc', 'st2', 340, 920, 260, 60)
    d.box('b4', '扣减库存（出库过账）', 'auto', 'st2', 340, 1030, 260, 60)
    d.edge('e5', 'a5', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.reject('r1', 'b1', 'a5', 726, 590)
    d.edge('e6', 'b1', 'b2', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(610, 726)])
    d.edge('e7', 'b2', 'b3')
    d.edge('e8', 'b3', 'b4')
    # ---- ③ 生产执行与完工 ----
    d.container('st3', '③ 生产执行与完工', 140, 1240, 960, 620)
    d.box('c1', '生产报工 WorkReport\n（MES 工序执行）', 'auto', 'st3', 340, 1290, 260, 60)
    d.box('c2', '完工 → 生产入库 ProductionRecpt\n（增加库存）', 'auto', 'st3', 340, 1400, 260, 60)
    d.box('c3', '关闭工单', 'end', 'st3', 380, 1510, 180, 50)
    d.box('c4', '若启用倒冲：\nMQ ProductionBackflushConsumer\n倒冲领料 + 成品入库过账', 'auto', 'st3', 300, 1610, 340, 70)
    d.edge('e9', 'b4', 'c1')
    d.edge('e10', 'c1', 'c2')
    d.edge('e11', 'c2', 'c3')
    d.edge('e12', 'c2', 'c4', '倒冲模式', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(790, 1460), (790, 1645)])
    # ---- ④ 委外加工 ----
    d.container('st4', '④ 委外加工（Subcontract）', 140, 1900, 960, 500)
    d.box('d1', '委外单 → 下达', 'doc', 'st4', 340, 1950, 260, 60)
    d.box('d2', '发料 SubcontractIssueStrategy', 'auto', 'st4', 340, 2060, 260, 60)
    d.box('d3', '供应商加工', 'state', 'st4', 380, 2170, 180, 50)
    d.box('d4', '委外收货 SubcontractReceiveStrategy\n→ MQ 委外库存消费者\n更新委外订单状态 ISSUE→IN_PROGRESS→COMPLETED', 'auto', 'st4', 300, 2270, 340, 70)
    d.edge('e13', 'a5', 'd1', '委外', anchors=(1.0, 0.5, 0.0, 0.5),
           waypoints=[(1300, 560), (1300, 1980), (610, 1980), (610, 1950)])
    d.edge('e14', 'd1', 'd2')
    d.edge('e15', 'd2', 'd3')
    d.edge('e16', 'd3', 'd4')
    # ---- ⑤ 返工 ----
    d.container('st5', '⑤ 返工（Rework）', 140, 2440, 960, 460)
    d.box('f1', '返工单 → 下达', 'doc', 'st5', 340, 2490, 260, 60)
    d.box('f2', '发料 ReworkIssueStrategy', 'auto', 'st5', 340, 2600, 260, 60)
    d.box('f3', '返工执行\n（完成 → 收料）', 'state', 'st5', 360, 2710, 220, 50)
    d.box('f4', '返工入库 ReworkReceiveStrategy\n→ MQ 返工库存消费者\n回写报废数量到来源工单', 'auto', 'st5', 300, 2810, 340, 70)
    d.edge('e17', 'a5', 'f1', '返工', anchors=(0.0, 0.5, 0.0, 0.5),
           waypoints=[(190, 560), (190, 2520), (400, 2520), (400, 2490)])
    d.edge('e18', 'f1', 'f2')
    d.edge('e19', 'f2', 'f3')
    d.edge('e20', 'f3', 'f4')
    # ---- 图例 ----
    d.legend(140, 2940, [('doc', '单据 / 操作'), ('decision', '审批 / 判断'), ('auto', '系统自动'), ('state', '状态 / 虚线 = 驳回')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../erp/生产制造流程.drawio')
