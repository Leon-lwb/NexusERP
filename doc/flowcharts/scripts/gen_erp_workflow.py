#!/usr/bin/env python3
"""ERP 审批流统一流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 提交审批 ----
    d.container('st1', '① 提交审批', 140, 40, 960, 400)
    d.box('a1', '业务单据（9 类可审批单据）\nPU：申请/订单/退货\nWM：入库/出库/调拨/盘点\nPRD：工单　SO：订单/退货', 'doc', 'st1', 300, 90, 340, 70)
    d.box('a2', '前端选择审批流 + 点击"提交审批"\nPOST /{id}/submit?selectFlowId=', 'doc', 'st1', 300, 210, 340, 60)
    d.box('a3', 'Service.submit() →\nprocessService.startProcessByDataId()', 'auto', 'st1', 300, 320, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    # ---- ② Flowable 审批执行 ----
    d.container('st2', '② Flowable 审批执行', 140, 480, 960, 420)
    d.box('b1', '发起流程实例', 'auto', 'st2', 340, 530, 260, 60)
    d.box('b2', '审批节点执行\n（多级审批 / 并行审批 / 代理审批）', 'decision', 'st2', 330, 640, 280, 72)
    d.box('b3', '审批通过', 'auto', 'st2', 340, 760, 260, 60)
    d.box('b4', '审批驳回', 'end', 'st2', 380, 860, 180, 50)
    d.edge('e3', 'a3', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e4', 'b1', 'b2')
    d.edge('e5', 'b2', 'b3', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(620, 676)])
    d.edge('e6', 'b2', 'b4', '驳回', anchors=(0.0, 0.5, 0.5, 0.0))
    # ---- ③ 回调联动 ----
    d.container('st3', '③ 状态回调联动', 140, 940, 960, 360)
    d.box('c1', 'afterFlowHandle() 同步业务状态', 'auto', 'st3', 330, 990, 280, 60)
    d.box('c2', 'APPROVED → 业务流转（如发MQ）\nREJECTED → 退回原状态', 'auto', 'st3', 330, 1100, 280, 70)
    d.box('c3', '前端"查看流程"\nGET /{id}/proc 实时进度', 'auto', 'st3', 330, 1220, 280, 60)
    d.edge('e7', 'b3', 'c1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e8', 'c1', 'c2')
    d.edge('e9', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1340, [('doc', '单据 / 操作'), ('decision', '审批 / 判断'), ('auto', '系统自动'), ('end', '结束 / 驳回')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../erp/审批流流程.drawio')
