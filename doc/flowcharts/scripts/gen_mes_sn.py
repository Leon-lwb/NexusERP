#!/usr/bin/env python3
"""MES SN 追溯体系流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① SN 分配 ----
    d.container('st1', '① SN 分配与关联', 140, 40, 960, 420)
    d.box('a1', '产品 → 分配唯一 SN 码\n（编码规则：前缀+日期+流水）', 'doc', 'st1', 330, 90, 280, 60)
    d.box('a2', 'SN 与流转卡关联\n（生产履历绑定）', 'auto', 'st1', 330, 200, 280, 60)
    d.box('a3', 'SN 父子关系\n（组装追溯：父 SN ← 子 SN）', 'auto', 'st1', 330, 310, 280, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    # ---- ② SN 生命周期 ----
    d.container('st2', '② SN 生命周期', 140, 500, 960, 460)
    d.box('b1', 'IN_PRODUCTION 生产中\n（工序流转记录）', 'auto', 'st2', 330, 550, 280, 60)
    d.box('b2', 'COMPLETED 已完成\n（流转卡完工）', 'auto', 'st2', 330, 660, 280, 60)
    d.box('b3', 'SCRAPPED 已报废', 'end', 'st2', 340, 770, 180, 50)
    d.box('b4', 'SHIPPED 已发货\n（销售出库）', 'auto', 'st2', 330, 860, 280, 60)
    d.edge('e3', 'a3', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e4', 'b1', 'b2')
    d.edge('e5', 'b1', 'b3', '报废', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(620, 580), (620, 790), (530, 790)])
    d.edge('e6', 'b2', 'b4', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- ③ 追溯查询 ----
    d.container('st3', '③ 追溯查询', 140, 1000, 960, 420)
    d.box('c1', '前向追溯：SN → 工序 → 物料\n→ 设备 → 操作员', 'auto', 'st3', 330, 1050, 280, 60)
    d.box('c2', '后向追溯：物料 → SN → 工序 → 产品', 'auto', 'st3', 330, 1160, 280, 60)
    d.box('c3', '前端：树图 / 表格双视图', 'auto', 'st3', 330, 1270, 280, 60)
    d.edge('e7', 'c1', 'c3', '查询', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e8', 'c2', 'c3', '查询', anchors=(0.5, 1.0, 0.5, 0.0))
    # ---- 图例 ----
    d.legend(140, 1460, [('doc', '单据 / 操作'), ('auto', '系统自动'), ('end', '结束')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../mes/SN追溯体系流程.drawio')
