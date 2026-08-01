#!/usr/bin/env python3
"""ERP 排班考勤流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 排班配置 ----
    d.container('st1', '① 排班配置', 140, 40, 960, 460)
    d.box('a1', '班次定义 Shift\n（打卡规则 PunchRule：位置/WiFi/蓝牙限制）', 'doc', 'st1', 330, 90, 280, 60)
    d.box('a2', '班组定义 → 关联排班计划 SchPlan\n（PlanTeam）', 'doc', 'st1', 330, 200, 280, 60)
    d.box('a3', '班组排班 TeamSchedule\n（班次 × 班组）', 'auto', 'st1', 330, 310, 280, 60)
    d.box('a4', '员工排班 EmployeeSchedule\n（员工 × 班组排班）', 'auto', 'st1', 330, 420, 280, 60)
    d.edge('e1', 'a1', 'a3', '绑定', anchors=(1.0, 0.5, 0.0, 0.5))
    d.edge('e2', 'a2', 'a3')
    d.edge('e3', 'a3', 'a4')
    # ---- ② 考勤执行 ----
    d.container('st2', '② 考勤执行（移动端打卡）', 140, 540, 960, 460)
    d.box('b1', '获取排班', 'auto', 'st2', 340, 590, 260, 60)
    d.box('b2', '获取位置 → 验证位置范围', 'auto', 'st2', 340, 700, 260, 60)
    d.box('b3', '上班打卡 / 下班打卡', 'decision', 'st2', 340, 810, 260, 72)
    d.box('b4', '生成考勤记录 Attendance\n（迟到 / 早退 / 加班）', 'auto', 'st2', 340, 930, 260, 60)
    d.edge('e4', 'a4', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e5', 'b1', 'b2')
    d.edge('e6', 'b2', 'b3')
    d.edge('e7', 'b3', 'b4', '打卡成功', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(610, 846)])
    d.reject('r1', 'b3', 'b2', 846, 760)
    # ---- ③ 产能日历 ----
    d.container('st3', '③ 产能日历', 140, 1040, 960, 360)
    d.box('c1', '产能日历 CapacityCalendar', 'doc', 'st3', 340, 1090, 260, 60)
    d.box('c2', '占用产能（工单下达）', 'auto', 'st3', 340, 1200, 260, 60)
    d.box('c3', '释放产能（工单关闭）', 'auto', 'st3', 340, 1310, 260, 60)
    d.edge('e8', 'c1', 'c2')
    d.edge('e9', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1440, [('doc', '单据 / 操作'), ('decision', '审批 / 判断'), ('auto', '系统自动')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../erp/排班考勤流程.drawio')
