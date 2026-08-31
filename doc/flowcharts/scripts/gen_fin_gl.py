#!/usr/bin/env python3
"""财务 GL 总账流程（F0）— 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 基础配置 ----
    d.container('st1', '① 基础配置', 140, 40, 960, 460)
    d.box('a1', '科目表 gl_account\n（统驭科目：AR/AP/存货等）', 'doc', 'st1', 300, 90, 340, 60)
    d.box('a2', '会计期间 gl_period\n（开账 / 月度）', 'doc', 'st1', 300, 200, 340, 60)
    d.box('a3', '汇率 gl_currency_rate\n（多币种）', 'doc', 'st1', 300, 310, 340, 60)
    d.box('a4', '期初余额初始化\ngl_account_balance', 'auto', 'st1', 300, 420, 340, 60)
    d.edge('e1', 'a1', 'a4', '', anchors=(0.5, 1.0, 0.0, 0.5), waypoints=[(470, 150), (300, 150), (300, 420)])
    d.edge('e2', 'a2', 'a4', '', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e3', 'a3', 'a4', '', anchors=(0.5, 1.0, 1.0, 0.5), waypoints=[(470, 370), (650, 370), (650, 420)])
    # ---- ② 凭证处理 ----
    d.container('st2', '② 凭证处理', 140, 540, 960, 600)
    d.box('b1', '业务过账（Poster 接口）\nAR Poster / AP Poster / 存货 Poster\n→ 生成记账凭证 gl_voucher', 'auto', 'st2', 300, 590, 340, 70)
    d.box('b2', '手工凭证录入\n（借贷平衡校验）', 'doc', 'st2', 300, 710, 340, 60)
    d.box('b3', '凭证行 gl_voucher_line\n（科目 + 金额 + 辅助核算）', 'auto', 'st2', 300, 820, 340, 60)
    d.box('b4', '凭证审核 → 过账', 'decision', 'st2', 300, 930, 340, 72)
    d.box('b5', '更新科目余额\ngl_account_balance', 'auto', 'st2', 300, 1050, 340, 60)
    d.edge('e4', 'a4', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e5', 'b1', 'b3')
    d.edge('e6', 'b2', 'b3')
    d.edge('e7', 'b3', 'b4')
    d.reject('r1', 'b4', 'b3', 966, 880)
    d.edge('e8', 'b4', 'b5', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 966), (650, 1080)])
    # ---- ③ 试算与报表 ----
    d.container('st3', '③ 试算与报表', 140, 1180, 960, 460)
    d.box('c1', '试算平衡表\n（借贷合计校验）', 'auto', 'st3', 300, 1230, 340, 60)
    d.box('c2', '科目余额表 / 明细账', 'auto', 'st3', 300, 1340, 340, 60)
    d.box('c3', '三大报表（资产负债表等）', 'auto', 'st3', 300, 1450, 340, 60)
    d.edge('e9', 'b5', 'c1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e10', 'c1', 'c2')
    d.edge('e11', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1680, [('doc', '单据 / 操作'), ('decision', '审核'), ('auto', '系统自动')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../fin/GL总账流程.drawio')
