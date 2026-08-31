#!/usr/bin/env python3
"""IoT 数据管道流程（MQTT → Redis Streams → MySQL/SSE）— 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 设备接入 ----
    d.container('st1', '① 设备接入（MQTT）', 140, 40, 960, 500)
    d.box('a1', '设备注册 IotDevice\n（deviceCode + deviceSecret 认证）', 'doc', 'st1', 300, 90, 340, 60)
    d.box('a2', 'MQTT Broker 内嵌（Moquette）\n端口 1883 / QoS 1', 'auto', 'st1', 300, 200, 340, 60)
    d.box('a3', 'Topic 规范\niot/{tenantId}/{deviceId}/property/post\n（属性上报 / set 设置 / event 事件）', 'doc', 'st1', 300, 310, 340, 70)
    d.box('a4', '遗嘱消息 → 设备离线\n更新 iot_device.connection_status = OFFLINE', 'auto', 'st1', 300, 430, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4')
    # ---- ② 数据管道 ----
    d.container('st2', '② 数据管道（Redis Streams）', 140, 580, 960, 560)
    d.box('b1', 'MqttMessageListener 接收\n解析 Topic + 提取属性', 'auto', 'st2', 300, 630, 340, 60)
    d.box('b2', 'XADD iot:data:stream\n（device_id + property_code + value）', 'auto', 'st2', 300, 740, 340, 60)
    d.box('b3', 'StreamConsumer XREADGROUP\n（GROUP iot-consumers BLOCK 1000）', 'auto', 'st2', 300, 850, 340, 60)
    d.box('b4', '写入 iot_device_data 表\n（MySQL 批量 INSERT）', 'auto', 'st2', 300, 960, 340, 60)
    d.box('b5', 'HSET iot:latest:{deviceId}\nSET iot:status:{deviceId} ONLINE EX 3600', 'auto', 'st2', 300, 1070, 340, 60)
    d.edge('e4', 'a3', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e5', 'b1', 'b2')
    d.edge('e6', 'b2', 'b3')
    d.edge('e7', 'b3', 'b4')
    d.edge('e8', 'b4', 'b5')
    # ---- ③ 实时推送 ----
    d.container('st3', '③ 实时推送（SSE）', 140, 1180, 960, 420)
    d.box('c1', '告警阈值检查\n（硬编码 / 温度 > 80 等）', 'decision', 'st3', 300, 1230, 340, 72)
    d.box('c2', 'XACK 确认消息', 'auto', 'st3', 300, 1350, 340, 60)
    d.box('c3', 'SseMessageUtils.publishMessage\n→ 前端 /iot/device/{id}/realtime\nEventSource 实时展示', 'auto', 'st3', 300, 1460, 340, 60)
    d.edge('e9', 'b5', 'c1')
    d.edge('e10', 'b4', 'c2', 'XACK', anchors=(1.0, 0.5, 0.0, 0.5),
           waypoints=[(1300, 990), (1300, 1380), (650, 1380), (650, 1350)])
    d.edge('e11', 'c1', 'c3', '超阈值', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 1266), (650, 1490)])
    # ---- 图例 ----
    d.legend(140, 1640, [('doc', '单据 / 操作'), ('decision', '判断'), ('auto', '系统自动')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../iot/数据管道流程.drawio')
