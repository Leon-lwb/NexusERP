#!/usr/bin/env python3
"""MES 安灯系统流程 — 共享库（hub 汇聚模式）"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 异常呼叫（4 类 → hub 汇聚） ----
    d.container('st1', '① 异常呼叫类型', 140, 40, 960, 560)
    d.box('a1', 'QUALITY\n质量异常', 'doc', 'st1', 240, 90, 180, 60)
    d.box('a2', 'MATERIAL\n物料短缺', 'doc', 'st1', 240, 200, 180, 60)
    d.box('a3', 'EQUIPMENT\n设备故障', 'doc', 'st1', 240, 310, 180, 60)
    d.box('a4', 'OTHER\n其他异常', 'doc', 'st1', 240, 420, 180, 60)
    d.box('a5', '操作员发起呼叫\n（车间看板 / 工位）', 'auto', 'st1', 620, 90, 220, 420)
    # 纯水平汇聚：entryY 按目标高度比例
    d.edge('e1', 'a1', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.071))
    d.edge('e2', 'a2', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.333))
    d.edge('e3', 'a3', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.595))
    d.edge('e4', 'a4', 'a5', '', anchors=(1.0, 0.5, 0.0, 0.857))
    # ---- ② 呼叫生命周期 ----
    d.container('st2', '② 呼叫生命周期', 140, 640, 960, 460)
    d.box('b1', '呼叫 CALLED\n（状态初始）', 'auto', 'st2', 340, 690, 260, 60)
    d.box('b2', '响应人响应 RESPONDED\n（接单）', 'auto', 'st2', 340, 800, 260, 60)
    d.box('b3', '问题解决 RESOLVED', 'auto', 'st2', 340, 910, 260, 60)
    d.box('b4', '取消 CANCELLED', 'end', 'st2', 360, 1020, 180, 50)
    d.edge('e5', 'a5', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e6', 'b1', 'b2')
    d.edge('e7', 'b2', 'b3')
    d.edge('e8', 'b1', 'b4', '取消', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(610, 720), (610, 1040), (530, 1040)])
    # ---- ③ 实时推送 ----
    d.container('st3', '③ 实时推送', 140, 1140, 960, 360)
    d.box('c1', 'SseMessageUtils.publishMessage()\n（呼叫/响应/解决 → 看板）', 'auto', 'st3', 330, 1190, 280, 60)
    d.box('c2', '前端安灯看板\nEventSource 接收 SSE 实时更新', 'auto', 'st3', 330, 1300, 280, 60)
    d.edge('e9', 'b3', 'c1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e10', 'c1', 'c2')
    # ---- 图例 ----
    d.legend(140, 1540, [('doc', '单据 / 操作'), ('auto', '系统自动'), ('end', '结束')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../mes/安灯系统流程.drawio')
