#!/usr/bin/env python3
"""MES 三检制流程（IQC/IPQC/OQC）— 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① IQC 来料检验 ----
    d.container('st1', '① IQC 来料检验（采购物料入库前）', 140, 40, 960, 520)
    d.box('a1', '收货单审批通过（ERP）\n→ MQ 镜像触发', 'auto', 'st1', 300, 90, 340, 60)
    d.box('a2', 'IQC 检验（QcIqc）\n需检物料 → 检验录单', 'doc', 'st1', 300, 200, 340, 60)
    d.box('a3', '判定合格 QUALIFIED', 'auto', 'st1', 300, 310, 340, 60)
    d.box('a4', '判定不合格 UNQUALIFIED\n（录 defect_info 不良明细）', 'end', 'st1', 300, 420, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3', '合格', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 230), (650, 340)])
    d.edge('e3', 'a2', 'a4', '不合格', anchors=(0.0, 0.5, 0.5, 0.0), waypoints=[(290, 230), (290, 450)])
    # ---- ② IPQC 过程检验 ----
    d.container('st2', '② IPQC 过程检验（生产过程中抽检）', 140, 600, 960, 520)
    d.box('b1', '关键工序报工 completeWo\n（门禁校验）', 'auto', 'st2', 300, 650, 340, 60)
    d.box('b2', 'IPQC 检验（QcIpqc）\n抽检 / 全检', 'doc', 'st2', 300, 760, 340, 60)
    d.box('b3', '合格 → 放行继续生产', 'auto', 'st2', 300, 870, 340, 60)
    d.box('b4', '不合格 → 拦截\n（NCR 自动建单）', 'end', 'st2', 300, 980, 340, 60)
    d.edge('e4', 'b1', 'b2')
    d.edge('e5', 'b2', 'b3', '合格', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 790), (650, 900)])
    d.edge('e6', 'b2', 'b4', '不合格', anchors=(0.0, 0.5, 0.5, 0.0), waypoints=[(290, 790), (290, 1010)])
    # ---- ③ OQC 出货检验 ----
    d.container('st3', '③ OQC 出货检验（成品出货前）', 140, 1160, 960, 520)
    d.box('c1', '销售出库审批（ERP）\n→ MQ 触发', 'auto', 'st3', 300, 1210, 340, 60)
    d.box('c2', 'OQC 检验（QcOqc）\n出货抽检', 'doc', 'st3', 300, 1320, 340, 60)
    d.box('c3', '合格 → 放行出库', 'auto', 'st3', 300, 1430, 340, 60)
    d.box('c4', '不合格 → 拦截出库\n（NCR 自动建单）', 'end', 'st3', 300, 1540, 340, 60)
    d.edge('e7', 'c1', 'c2')
    d.edge('e8', 'c2', 'c3', '合格', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 1350), (650, 1460)])
    d.edge('e9', 'c2', 'c4', '不合格', anchors=(0.0, 0.5, 0.5, 0.0), waypoints=[(290, 1350), (290, 1570)])
    # ---- ④ 不良处理 ----
    d.container('st4', '④ 不良品处理（三检共用）', 140, 1720, 960, 420)
    d.box('d1', 'defect_info 记录\n不良类型：SCRATCH/CRACK/DIMENSION/OTHER\n处置方式：SCRAP/REWORK/ACCEPT/RETURN', 'doc', 'st4', 300, 1770, 340, 70)
    d.box('d2', 'IQC 联动 ERP\nMQ 通知 → 收货单质检状态\nQUALIFIED 允许入库 / UNQUALIFIED 不良处理', 'auto', 'st4', 300, 1900, 340, 70)
    d.box('d3', 'NCR 不合格品单自动创建\n（OPEN→REVIEW→DISPOSITION→CLOSED）', 'auto', 'st4', 300, 2020, 340, 70)
    d.edge('e10', 'd1', 'd2')
    d.edge('e11', 'd2', 'd3')
    # ---- 图例 ----
    d.legend(140, 2180, [('doc', '单据 / 操作'), ('auto', '系统自动'), ('end', '结束 / 拦截')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../mes/三检制流程.drawio')
