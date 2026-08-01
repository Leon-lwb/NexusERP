#!/usr/bin/env python3
"""MES NCR 不合格品与返工流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 触发点 ----
    d.container('st1', '① 自动触发点', 140, 40, 960, 520)
    d.box('a1', 'IQC 检验 UNQUALIFIED\n（inspectResult）', 'auto', 'st1', 300, 90, 340, 60)
    d.box('a2', 'IPQC 检验 UNQUALIFIED', 'auto', 'st1', 300, 200, 340, 60)
    d.box('a3', 'OQC 检验 UNQUALIFIED', 'auto', 'st1', 300, 310, 340, 60)
    d.box('a4', '流转卡出站 exit()\ndefectQty > 0（报工不良）', 'auto', 'st1', 300, 420, 340, 60)
    d.box('a5', 'autoCreateNcr()\n（CAS 更新成功后同事务建单，建单失败回滚检验）', 'auto', 'st1', 660, 90, 260, 390)
    d.edge('e1', 'a1', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.154))
    d.edge('e2', 'a2', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.385))
    d.edge('e3', 'a3', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.615))
    d.edge('e4', 'a4', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.846))
    # ---- ② NCR 状态机 ----
    d.container('st2', '② NCR 状态机', 140, 600, 960, 520)
    d.box('b1', 'OPEN 已创建\n（可选触发安灯 QUALITY）', 'auto', 'st2', 330, 650, 280, 60)
    d.box('b2', 'REVIEW 评审\n（8D 报告 / 缺陷代码）', 'auto', 'st2', 330, 760, 280, 60)
    d.box('b3', 'DISPOSITION 处置\n（隔离 QUARANTINED）', 'decision', 'st2', 330, 870, 280, 72)
    d.box('b4', 'REWORK 返工中\n（派生返工工单）', 'auto', 'st2', 330, 980, 280, 60)
    d.box('b5', 'CLOSED 关闭', 'end', 'st2', 370, 1090, 180, 50)
    d.edge('e5', 'a5', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e6', 'b1', 'b2')
    d.edge('e7', 'b2', 'b3')
    d.edge('e8', 'b3', 'b4', '处置 REWORK', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(620, 906)])
    d.edge('e9', 'b3', 'b5', 'SCRAP/ACCEPT', anchors=(0.0, 0.5, 0.5, 0.0), waypoints=[(320, 906), (320, 1110)])
    d.edge('e10', 'b4', 'b5', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- ③ 返工派生 ----
    d.container('st3', '③ 返工工单派生（复用流转卡全链路）', 140, 1160, 960, 420)
    d.box('c1', '返工工单派生\n（source_type=REWORK，关联原工单）', 'auto', 'st3', 330, 1210, 280, 60)
    d.box('c2', 'createFlowCard → releaseCard\n→ entry / exit / dispatch', 'auto', 'st3', 330, 1320, 280, 60)
    d.box('c3', 'completeCard → completeWo\n（返工完成）', 'auto', 'st3', 330, 1430, 280, 60)
    d.edge('e11', 'b4', 'c1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e12', 'c1', 'c2')
    d.edge('e13', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1620, [('doc', '单据 / 操作'), ('decision', '审批 / 判断'), ('auto', '系统自动'), ('end', '结束')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../mes/NCR不合格品与返工流程.drawio')
