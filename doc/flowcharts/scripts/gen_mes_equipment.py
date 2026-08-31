#!/usr/bin/env python3
"""MES 设备管理流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 设备台账 ----
    d.container('st1', '① 设备台账', 140, 40, 960, 460)
    d.box('a1', '设备档案 EqEquipment\n（编码/名称/型号/制造商）', 'doc', 'st1', 330, 90, 280, 60)
    d.box('a2', '设备状态\n正常 / 保养中 / 故障 / 报废', 'state', 'st1', 330, 200, 280, 60)
    d.box('a3', '点检项目 & 标准\n（inspection_items JSON）', 'doc', 'st1', 330, 310, 280, 60)
    d.box('a4', '关联 IoT 设备\n（iot_device_id 预留，不建外键）', 'auto', 'st1', 330, 420, 280, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4')
    # ---- ② 保养计划 ----
    d.container('st2', '② 保养计划', 140, 540, 960, 420)
    d.box('b1', '保养计划 EqMaintenancePlan\n（日/周/月/季/年）', 'doc', 'st2', 330, 590, 280, 60)
    d.box('b2', '保养周期（天）\n下次保养日期自动计算', 'auto', 'st2', 330, 700, 280, 60)
    d.box('b3', '到期提醒 → 执行保养\n（记录保养历史）', 'auto', 'st2', 330, 810, 280, 60)
    d.edge('e4', 'b1', 'b2')
    d.edge('e5', 'b2', 'b3')
    # ---- ③ 维修管理 ----
    d.container('st3', '③ 维修管理', 140, 1000, 960, 520)
    d.box('c1', '故障报修\n（安灯 EQUIPMENT 联动）', 'doc', 'st3', 330, 1050, 280, 60)
    d.box('c2', '维修工单 EqRepair\n（派工）', 'doc', 'st3', 330, 1160, 280, 60)
    d.box('c3', '执行维修 → 完成', 'auto', 'st3', 330, 1270, 280, 60)
    d.box('c4', '记录停机时长 + 维修成本', 'auto', 'st3', 330, 1380, 280, 60)
    d.edge('e6', 'c1', 'c2')
    d.edge('e7', 'c2', 'c3')
    d.edge('e8', 'c3', 'c4')
    # ---- 图例 ----
    d.legend(140, 1560, [('doc', '单据 / 操作'), ('auto', '系统自动'), ('state', '状态')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../mes/设备管理流程.drawio')
