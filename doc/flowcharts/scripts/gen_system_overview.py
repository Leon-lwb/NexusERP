#!/usr/bin/env python3
"""系统总览：跨模块业务流转总图 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 基础数据层 ----
    d.container('st1', '① BD 基础数据层', 140, 40, 1100, 320)
    d.box('a1', '物料\nBdMaterial', 'doc', 'st1', 200, 90, 130, 50)
    d.box('a2', 'BOM\n（多版本）', 'doc', 'st1', 360, 90, 130, 50)
    d.box('a3', '客户\nBdClient', 'doc', 'st1', 520, 90, 130, 50)
    d.box('a4', '供应商\nBdVendor', 'doc', 'st1', 680, 90, 130, 50)
    d.box('a5', '计量单位\nBdUom', 'doc', 'st1', 840, 90, 130, 50)
    d.box('a6', '车间 / 工位\nBdWorkshop', 'doc', 'st1', 200, 200, 130, 50)
    d.box('a7', '序列号规则\nBdSerialNumberRule', 'doc', 'st1', 360, 200, 130, 50)
    d.box('a8', '客户信用\nBdClientCredit', 'doc', 'st1', 520, 200, 130, 50)
    d.box('a9', '价格表 / 编码规则', 'doc', 'st1', 680, 200, 130, 50)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a3', 'a8')
    # ---- ② 业务模块 ----
    d.container('st2', '② 业务模块（SO / PU / PRD / SCH / MES）', 140, 420, 1100, 520)
    d.box('b1', 'SO 销售管理\n客户订单/报价/销售订单\n销售退货/价格表/信用额度', 'doc', 'st2', 200, 470, 200, 100)
    d.box('b2', 'PU 采购管理\n采购申请/采购订单\n采购退货/供应商评估', 'doc', 'st2', 450, 470, 200, 100)
    d.box('b3', 'PRD 生产管理\n生产计划/物料需求/工单\n报工/返工/委外', 'doc', 'st2', 700, 470, 200, 100)
    d.box('b4', 'SCH 排班考勤\n排班计划/班次/班组\n员工排班/考勤', 'doc', 'st2', 200, 620, 200, 100)
    d.box('b5', 'MES 制造执行\n工单/流转卡/报工\n安灯/SN/三检/设备', 'doc', 'st2', 450, 620, 200, 100)
    d.box('b6', 'AI 智能助手\nAgent/知识库/Text2SQL', 'doc', 'st2', 700, 620, 200, 100)
    d.edge('e3', 'a1', 'b1', '', anchors=(0.5, 1.0, 0.25, 0.0))
    d.edge('e4', 'a1', 'b2', '', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e5', 'a1', 'b3', '', anchors=(0.5, 1.0, 0.75, 0.0))
    # ---- ③ 仓储与集成 ----
    d.container('st3', '③ WM 仓储与跨模块集成', 140, 1000, 1100, 480)
    d.box('c1', 'WM 仓储管理\n库存（乐观锁）/ 12种策略\n入库/出库/调拨/盘点\n批次/序列号/追溯/预警', 'doc', 'st3', 200, 1050, 300, 120)
    d.box('c2', 'MQ 消息队列\n生产倒冲/采购回写/入库过账\n返工/委外库存', 'auto', 'st3', 560, 1050, 300, 120)
    d.box('c3', 'IoT 物联网\n设备数据/MQTT/实时看板', 'auto', 'st3', 200, 1230, 300, 100)
    d.box('c4', 'IM 即时通讯\n系统通知/消息推送', 'auto', 'st3', 560, 1230, 300, 100)
    d.box('c5', '财务 Fin\nGL/AR-AP/报表/固定资产', 'auto', 'st3', 900, 1050, 300, 120)
    d.edge('e6', 'b1', 'c1', '', anchors=(0.5, 1.0, 0.25, 0.0))
    d.edge('e7', 'b2', 'c1', '', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e8', 'b3', 'c1', '', anchors=(0.5, 1.0, 0.75, 0.0))
    d.edge('e9', 'c1', 'c2', '', anchors=(1.0, 0.5, 0.0, 0.5))
    d.edge('e10', 'c1', 'c5', '', anchors=(0.5, 1.0, 0.5, 0.0), waypoints=[(350, 1170), (160, 1170), (160, 1420), (1050, 1420), (1050, 1170)])
    # ---- 图例 ----
    d.legend(140, 1540, [('doc', '业务模块'), ('auto', '集成能力')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../系统总览-跨模块流转.drawio')
