#!/usr/bin/env python3
"""财务 报表与期末关账流程（F5）— 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 报表生成 ----
    d.container('st1', '① 报表生成', 140, 40, 960, 520)
    d.box('a1', '科目余额汇总\ngl_account_balance', 'auto', 'st1', 300, 90, 340, 60)
    d.box('a2', '三大报表\n资产负债表 / 利润表 / 现金流量表', 'doc', 'st1', 300, 200, 340, 60)
    d.box('a3', '报表抵减科目修复\n（跨模块核对）', 'auto', 'st1', 300, 310, 340, 60)
    d.box('a4', '报表输出（前端 gl/report）', 'auto', 'st1', 300, 420, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4')
    # ---- ② 月结 ----
    d.container('st2', '② 月结流程', 140, 600, 960, 520)
    d.box('b1', '月末检查\n（未过账凭证 / 未核销发票 / 在制品）', 'decision', 'st2', 300, 650, 340, 72)
    d.box('b2', '结转损益\n（损益科目 → 本年利润）', 'auto', 'st2', 300, 770, 340, 60)
    d.box('b3', '月结关账\n（期间锁定）', 'auto', 'st2', 300, 880, 340, 60)
    d.box('b4', '下一期间开账', 'auto', 'st2', 300, 990, 340, 60)
    d.edge('e4', 'b1', 'b2', '检查通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 686), (650, 800)])
    d.edge('e5', 'b2', 'b3')
    d.edge('e6', 'b3', 'b4')
    # ---- ③ 年结 ----
    d.container('st3', '③ 年结', 140, 1160, 960, 420)
    d.box('c1', '年末 12 月月结完成', 'auto', 'st3', 300, 1210, 340, 60)
    d.box('c2', '年结：余额结转\n本年利润 → 未分配利润', 'auto', 'st3', 300, 1320, 340, 60)
    d.box('c3', '新年度期初余额初始化', 'auto', 'st3', 300, 1430, 340, 60)
    d.edge('e7', 'b4', 'c1', '', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e8', 'c1', 'c2')
    d.edge('e9', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1620, [('doc', '单据 / 操作'), ('decision', '检查'), ('auto', '系统自动')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../fin/报表与期末关账流程.drawio')
