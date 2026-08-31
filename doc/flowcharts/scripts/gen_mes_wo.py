#!/usr/bin/env python3
"""生成 MES 工单下达与流转卡 手写 drawio XML（正交边 + 贴边走廊回退）

布局（页面绝对坐标系，MAIN_C=470 主流程中心）：
- 驳回/回退线：决策左出口 → 左边缘走廊(x=140) → 目标下边缘进入
- 工序循环（C5 完成 → 下道 → 回 C4）：右边缘走廊(x=1300) 内循环
- 取消/关闭：虚线贴边
"""
# -*- coding: utf-8 -*-
import sys

X = '<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="drawio" version="31.1.5">\n  <diagram name="Page-1">\n    <mxGraphModel>\n      <root>\n        <mxCell id="0" />\n        <mxCell id="1" parent="0" />\n'

CELLS, EDGES = [], []
L_CORR, R_CORR = 190, 1250          # 左走廊内缩50px(容器左缘140)；右走廊内缩50px(容器右缘1300)
CONTS = {}

def esc(v):
    return str(v).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace('\n', '&#xa;')

STYLE = {
    'start':   'ellipse;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;',
    'end':     'ellipse;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;',
    'decision':'rhombus;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;',
    'doc':     'rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;',
    'auto':    'rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;',
    'state':   'rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#999999;dashed=1;',
    'text':    'text;html=1;align=left;verticalAlign=middle;',
}

def container(cid, label, x, y, w, h):
    CONTS[cid] = (x, y, w, h)
    CELLS.append(f'        <mxCell id="{cid}" value="{esc(label)}" style="swimlane;startSize=34;whiteSpace=wrap;html=1;fillColor=none;strokeColor=#d6b656;fontColor=#7a5c00;fontStyle=1;" vertex="1" parent="1">\n'
                 f'          <mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry" />\n        </mxCell>\n')

def node(cid, label, ctype, parent, x, y, w, h):
    if parent != '1':
        cx, cy, _, _ = CONTS[parent]
        x, y = x - cx, y - cy
    CELLS.append(f'        <mxCell id="{cid}" value="{esc(label)}" style="{STYLE[ctype]}" vertex="1" parent="{parent}">\n'
                 f'          <mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry" />\n        </mxCell>\n')

def edge(eid, src, tgt, label='', anchors=None, waypoints=None, color=None, dashed=False, loop=False):
    ex, ey, nx, ny = anchors if anchors else (0.5, 1.0, 0.5, 0.0)
    style = (f'edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop={1 if loop else 0};jettySize=auto;html=1;'
             f'exitX={ex};exitY={ey};entryX={nx};entryY={ny};')
    if dashed: style += 'dashed=1;'
    if color: style += f'strokeColor={color};'
    if label: style += 'fontSize=11;labelBackgroundColor=#ffffff;'
    pts = ''
    if waypoints:
        pts = '\n            <Array as="points">\n' + ''.join(f'              <mxPoint x="{px}" y="{py}" />\n' for px, py in waypoints) + '            </Array>\n'
    EDGES.append(f'        <mxCell id="{eid}" value="{esc(label)}" style="{style}" edge="1" parent="1" source="{src}" target="{tgt}">\n'
                 f'          <mxGeometry relative="1" as="geometry">{pts}          </mxGeometry>\n        </mxCell>\n')

def reject(eid, dnode, tnode, d_y, t_bottom_y):
    edge(eid, dnode, tnode, '驳回', anchors=(0.0, 0.5, 0.5, 1.0),
         waypoints=[(L_CORR, d_y), (L_CORR, t_bottom_y)], color='#b85450', dashed=True)

# ============ 布局（绝对坐标） ============
# ---- Stage 1：ERP 工单审批下达 ----
container('st1', '① ERP 工单审批下达', 140, 40, 960, 400)
node('a1', 'ERP 生产工单 PrdWorkOrder\nDRAFT / 审批中', 'doc', 'st1', 340, 90, 260, 60)
node('a2', '审批流\nFlowable', 'decision', 'st1', 390, 200, 160, 72)
node('a3', 'ERP 工单 APPROVED\nafterFlowHandle() 发送 WorkOrderReleaseMessage\n→ RabbitMQ mes.workorder.queue', 'auto', 'st1', 280, 320, 380, 80)
edge('e1', 'a1', 'a2')
edge('e2', 'a2', 'a3', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(560, 236)])
reject('r1', 'a2', 'a1', 236, 150)
edge('e3', 'a3', 'b1', anchors=(0.5, 1.0, 0.5, 0.0))

# ---- Stage 2：MES 工单同步 ----
container('st2', '② MES 工单同步', 140, 480, 960, 560)
node('b1', 'MesWorkOrderSyncConsumer @DS(mes) 消费\n① 创建 pro_work_order\n② 按 ERP 工艺路线生成 pro_work_order_step', 'auto', 'st2', 280, 530, 380, 80)
node('b2', 'MES 工单\n（CREATED）', 'state', 'st2', 370, 660, 200, 50)
node('b3', 'MES 工单审批（复用 Flowable）\nsubmit() → processService.startProcessByDataId()', 'decision', 'st2', 340, 760, 260, 72)
node('b4', 'APPROVED → 工单 RELEASED 已下达\n（可创建流转卡）', 'auto', 'st2', 340, 880, 260, 60)
edge('e4', 'b1', 'b2')
edge('e5', 'b2', 'b3')
edge('e6', 'b3', 'b4', '通过', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(610, 796)])
reject('r2', 'b3', 'b2', 796, 710)
edge('e7', 'b4', 'c1', anchors=(0.5, 1.0, 0.5, 0.0))

# ---- Stage 3：流转卡引擎执行 ----
container('st3', '③ 流转卡引擎执行', 140, 1100, 1160, 1260)
node('c1', '创建流转卡 createFlowCard(woId, quantity)\n按工艺路线自动生成 pro_flow_card_step 工序记录', 'auto', 'st3', 300, 1150, 400, 70)
node('c2', 'CREATED 已创建', 'state', 'st3', 390, 1280, 220, 50)
node('c3', '下达 releaseCard\n→ RELEASED 已下达', 'auto', 'st3', 360, 1390, 280, 60)
node('c4', '开始第一道工序 startStep\n→ IN_PROGRESS', 'auto', 'st3', 340, 1500, 320, 60)
node('c5', '完成当前工序 completeStep\ngoodQty / defectQty / scrapQty', 'decision', 'st3', 330, 1620, 340, 72)
node('c6', '自动流转到下一道工序', 'auto', 'st3', 700, 1620, 200, 60)
node('c7', '流转卡 COMPLETED', 'end', 'st3', 390, 1750, 220, 50)
node('c8', '累计 pro_work_order.completed_qty', 'auto', 'st3', 370, 1870, 260, 50)
node('c9', '工单下所有流转卡完成？', 'decision', 'st3', 350, 1980, 300, 72)
node('c10', '继续生产其余流转卡', 'auto', 'st3', 680, 1980, 200, 60)
node('c11', 'MES 工单 COMPLETED\n→ MQ 通知 ERP', 'auto', 'st3', 360, 2110, 280, 60)
node('c12', 'CANCELLED', 'end', 'st3', 210, 1390, 140, 40)
node('c13', 'CLOSED', 'end', 'st3', 210, 1750, 140, 40)
edge('e8', 'c1', 'c2')
edge('e9', 'c2', 'c3')
edge('e10', 'c3', 'c4')
edge('e11', 'c4', 'c5')
edge('e12', 'c5', 'c6', '还有下道工序', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(760, 1656), (740, 1656)])
edge('e13', 'c6', 'c4', anchors=(1.0, 0.5, 0.5, 1.0), waypoints=[(R_CORR, 1650), (R_CORR, 1560), (500, 1560)])
edge('e14', 'c5', 'c7', '所有工序完成', anchors=(0.5, 1.0, 0.5, 0.0), waypoints=[(500, 1692)])
edge('e15', 'c7', 'c8')
edge('e16', 'c8', 'c9')
edge('e17', 'c9', 'c10', '否', anchors=(1.0, 0.5, 0.5, 0.0), waypoints=[(650, 2016), (780, 2016)])
edge('e18', 'c9', 'c11', '是', anchors=(0.5, 1.0, 0.5, 0.0), waypoints=[(500, 2052)])
edge('e19', 'c2', 'c12', '取消', anchors=(0.0, 0.5, 0.5, 0.0), color='#b85450', dashed=True,
     waypoints=[(280, 1305), (280, 1370)])
edge('e20', 'c7', 'c13', '关闭', anchors=(0.0, 0.5, 0.5, 0.0), color='#b85450', dashed=True,
     waypoints=[(280, 1775), (280, 1730)])

# ---- Stage 4：MES 完工 → ERP 联动 ----
container('st4', '④ MES 完工 → ERP 联动', 140, 2420, 960, 380)
node('d1', 'ErpWorkOrderCompletedConsumer\n更新 ERP 工单状态 + 完工入库', 'auto', 'st4', 300, 2470, 340, 70)
node('d2', 'ERP 工单完工\n倒冲领料 → 成品入库（MQ 异步）', 'end', 'st4', 350, 2600, 240, 70)
edge('e21', 'c11', 'd1', anchors=(0.5, 1.0, 0.5, 0.0))
edge('e22', 'd1', 'd2', anchors=(0.5, 1.0, 0.5, 0.0))

# ---- 图例 ----
container('lg', '图例', 140, 2840, 600, 200)
node('lg1', '', 'doc', 'lg', 160, 2880, 30, 16)
node('lg1t', '单据 / 操作', 'text', 'lg', 200, 2876, 120, 20)
node('lg2', '', 'decision', 'lg', 160, 2915, 30, 16)
node('lg2t', '审批 / 判断', 'text', 'lg', 200, 2912, 120, 20)
node('lg3', '', 'auto', 'lg', 160, 2950, 30, 16)
node('lg3t', '系统自动', 'text', 'lg', 200, 2947, 120, 20)
node('lg4', '', 'state', 'lg', 160, 2985, 30, 16)
node('lg4t', '状态 / 虚线 = 驳回·取消·关闭', 'text', 'lg', 200, 2982, 200, 20)

out = X
for c in CELLS: out += c
for e in EDGES: out += e
out += '      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n'

with open(sys.argv[1], 'w', encoding='utf-8') as f:
    f.write(out)
print(f'written {sys.argv[1]}: {len(CELLS)} cells, {len(EDGES)} edges')
