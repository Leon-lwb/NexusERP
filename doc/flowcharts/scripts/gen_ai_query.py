#!/usr/bin/env python3
"""AI 数据查询流程（Text2SQL）— 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 自然语言解析 ----
    d.container('st1', '① 自然语言解析', 140, 40, 960, 440)
    d.box('a1', '用户提问\n（如"本月销售额前10的客户"）', 'doc', 'st1', 300, 90, 340, 60)
    d.box('a2', 'ErpQueryAgent 理解意图\n（@SystemMessage 限定 ERP 域）', 'auto', 'st1', 300, 200, 340, 60)
    d.box('a3', 'QueryAllTablesTool\n（发现可用表）', 'auto', 'st1', 300, 310, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    # ---- ② SQL 生成与执行 ----
    d.container('st2', '② SQL 生成与安全执行', 140, 520, 960, 560)
    d.box('b1', 'QueryTableSchemaTool\n（获取表结构）', 'auto', 'st2', 300, 570, 340, 60)
    d.box('b2', '生成 SQL → ExecuteSqlQueryTool', 'auto', 'st2', 300, 680, 340, 60)
    d.box('b3', 'ErpQueryService 执行\n（@DS("erp") 切换到 ry-erp 库）', 'auto', 'st2', 300, 790, 340, 60)
    d.box('b4', '安全校验\n① 仅允许 SELECT\n② 危险关键词黑名单\n③ 表名前缀白名单 bd_/so_/pu_/prd_/wm_/sch_\n④ 自动注入 tenant_id 隔离', 'decision', 'st2', 300, 900, 340, 90)
    d.edge('e3', 'a3', 'b1')
    d.edge('e4', 'b1', 'b2')
    d.edge('e5', 'b2', 'b3')
    d.edge('e6', 'b3', 'b4')
    # ---- ③ 结果处理 ----
    d.container('st3', '③ 结果处理', 140, 1120, 960, 440)
    d.box('c1', 'SQL 超时 / 最大行数限制\n（sqlTimeout / sqlMaxRows）', 'auto', 'st3', 300, 1170, 340, 60)
    d.box('c2', '结果 → 表格 / 图表\n（ECharts AI_CHART 事件）', 'auto', 'st3', 300, 1280, 340, 60)
    d.box('c3', 'SSE 推送到前端渲染', 'auto', 'st3', 300, 1390, 340, 60)
    d.edge('e7', 'b4', 'c1', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 945), (650, 1200)])
    d.edge('e8', 'c1', 'c2')
    d.edge('e9', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1600, [('doc', '单据 / 操作'), ('decision', '安全校验'), ('auto', '系统自动')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../ai/AI数据查询流程.drawio')
