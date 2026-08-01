#!/usr/bin/env python3
"""IoT 告警与设备心跳流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 告警检测 ----
    d.container('st1', '① 告警检测（StreamConsumer 内）', 140, 40, 960, 440)
    d.box('a1', '设备数据入库后\n检查 propertyCode 硬编码阈值', 'auto', 'st1', 300, 90, 340, 60)
    d.box('a2', '告警规则表（按需引入）\niot_alert_rule / iot_alert_log', 'doc', 'st1', 300, 200, 340, 60)
    d.box('a3', '触发告警\n（日志 + 记录）', 'auto', 'st1', 300, 310, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    # ---- ② 告警生命周期 ----
    d.container('st2', '② 告警生命周期', 140, 520, 960, 420)
    d.box('b1', 'ACTIVE 激活', 'auto', 'st2', 340, 570, 260, 60)
    d.box('b2', 'ACKNOWLEDGED 已确认\n（处理人确认）', 'auto', 'st2', 340, 680, 260, 60)
    d.box('b3', 'RESOLVED 已解决', 'auto', 'st2', 340, 790, 260, 60)
    d.edge('e3', 'a3', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e4', 'b1', 'b2')
    d.edge('e5', 'b2', 'b3')
    # ---- ③ 通知方式 ----
    d.container('st3', '③ 通知方式', 140, 980, 960, 360)
    d.box('c1', 'SSE 推送\nSseMessageUtils.publishMessage', 'auto', 'st3', 330, 1030, 280, 60)
    d.box('c2', '邮件通知\n（ruoyi-common-mail）', 'auto', 'st3', 330, 1140, 280, 60)
    d.edge('e6', 'b1', 'c1', '推送', anchors=(1.0, 0.5, 0.0, 0.5), waypoints=[(960, 600), (960, 1070), (620, 1070)])
    d.edge('e7', 'b1', 'c2', '邮件', anchors=(1.0, 0.5, 0.0, 0.5), waypoints=[(960, 620), (960, 1170), (620, 1170)])
    # ---- ④ 心跳检测 ----
    d.container('st4', '④ 设备心跳检测（SnailJob）', 140, 1380, 960, 420)
    d.box('d1', 'IotDeviceHeartbeatTask\n每 5 分钟检查 last_online_time', 'auto', 'st4', 300, 1430, 340, 60)
    d.box('d2', '超时 > 15 分钟\n→ 标记 OFFLINE', 'decision', 'st4', 300, 1540, 340, 72)
    d.box('d3', '更新 Redis 状态 + 数据库\nconnection_status', 'auto', 'st4', 300, 1660, 340, 60)
    d.edge('e8', 'd1', 'd2')
    d.edge('e9', 'd2', 'd3', '超时', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 1576)])
    # ---- 图例 ----
    d.legend(140, 1840, [('doc', '单据 / 操作'), ('decision', '判断'), ('auto', '系统自动')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../iot/告警与心跳流程.drawio')
