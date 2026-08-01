#!/usr/bin/env python3
"""AI 对话与 Agent 编排流程 — 共享库"""
# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flowchart_lib import Diagram

def build(path):
    d = Diagram(path)
    # ---- ① 请求入口 ----
    d.container('st1', '① 请求入口', 140, 40, 960, 400)
    d.box('a1', '前端 AI 聊天抽屉\nAiChatDrawer.vue（Markdown 渲染）', 'doc', 'st1', 300, 90, 340, 60)
    d.box('a2', 'POST /ai/chat/send\nAiChatRequest（会话 + 消息）', 'doc', 'st1', 300, 200, 340, 60)
    d.box('a3', 'AiServiceFacade.chat()\n路由对话 → 创建会话 → 构建 Agent', 'auto', 'st1', 300, 310, 340, 60)
    d.edge('e1', 'a1', 'a2')
    d.edge('e2', 'a2', 'a3')
    # ---- ② Agent 编排（Supervisor） ----
    d.container('st2', '② Agent 编排（Supervisor 模式）', 140, 480, 960, 520)
    d.box('b1', 'ChatModelFactory 构建模型\n（按数据库配置 + 缓存）', 'auto', 'st2', 300, 530, 340, 60)
    d.box('b2', 'SupervisorAgent 编排\n（responseStrategy=LAST）', 'decision', 'st2', 300, 640, 340, 72)
    d.box('b3', 'ErpQueryAgent\n（SQL 查询）', 'auto', 'st2', 160, 760, 220, 60)
    d.box('b4', 'ErpReportAgent\n（报表图表）', 'auto', 'st2', 560, 760, 220, 60)
    d.box('b5', 'Tools 动态加载\nQueryAllTables / QueryTableSchema\nExecuteSqlQuery + 业务 Tool', 'doc', 'st2', 300, 880, 340, 70)
    d.edge('e3', 'a3', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e4', 'b1', 'b2')
    d.edge('e5', 'b2', 'b3', '查询', anchors=(0.0, 0.5, 0.5, 0.0), waypoints=[(290, 676), (270, 676), (270, 790)])
    d.edge('e6', 'b2', 'b4', '报表', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 676), (670, 676), (670, 790)])
    d.edge('e7', 'b3', 'b5', '', anchors=(0.5, 1.0, 0.0, 0.5), waypoints=[(270, 820), (270, 850)])
    d.edge('e8', 'b4', 'b5', '', anchors=(0.5, 1.0, 1.0, 0.5), waypoints=[(670, 820), (670, 850)])
    # ---- ③ 异步执行与推送 ----
    d.container('st3', '③ 异步执行与 SSE 推送', 140, 1040, 960, 440)
    d.box('c1', 'CompletableFuture.runAsync\n→ aiExecutor 专用线程池\nAiRequestContext 手动传播租户上下文', 'auto', 'st3', 300, 1090, 340, 80)
    d.box('c2', 'AiSseMessageHelper 编码\nTHINKING / TOOL_START / TOOL_END\nCONTENT / DONE / ERROR', 'auto', 'st3', 300, 1220, 340, 70)
    d.box('c3', 'Redis Pub/Sub 广播 → SSE\n前端实时接收（工具卡片 + 思考过程）', 'auto', 'st3', 300, 1340, 340, 70)
    d.edge('e9', 'b5', 'c1', anchors=(0.5, 1.0, 0.5, 0.0))
    d.edge('e10', 'c1', 'c2')
    d.edge('e11', 'c2', 'c3')
    # ---- 图例 ----
    d.legend(140, 1520, [('doc', '单据 / 操作'), ('decision', 'Agent 编排'), ('auto', '系统自动')])
    d.write()

if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else '../ai/AI对话与Agent编排流程.drawio')
