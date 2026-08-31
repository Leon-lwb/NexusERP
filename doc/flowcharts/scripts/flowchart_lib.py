#!/usr/bin/env python3
"""flowchart_lib.py — 通用 drawio 流程图生成库

统一规范（经验证通过，score=0 标准）：
- 正交边：edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto
- 回退/驳回线：红色虚线，走左走廊（容器内缩 50px，不贴边）
- 跨容器/跨阶段线：走右走廊或底部走廊，贴边内缩
- 节点坐标：页面绝对坐标（node() 内部转换为容器相对坐标）
- 所有坐标对齐 10px 网格
- 颜色：draw.io 官方色板
  doc=蓝(单据/操作) decision=黄(审批/判断) auto=绿(系统自动)
  state=灰虚框(状态) start/end=绿/红椭圆(开始/结束) text=文本
"""
# -*- coding: utf-8 -*-

X_HEADER = ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<mxfile host="drawio" version="31.1.5">\n'
            '  <diagram name="Page-1">\n'
            '    <mxGraphModel>\n'
            '      <root>\n'
            '        <mxCell id="0" />\n'
            '        <mxCell id="1" parent="0" />\n')

X_FOOTER = ('      </root>\n'
            '    </mxGraphModel>\n'
            '  </diagram>\n'
            '</mxfile>\n')

STYLE = {
    'start':    'ellipse;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;',
    'end':      'ellipse;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;',
    'decision': 'rhombus;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;',
    'doc':      'rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;',
    'auto':     'rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;',
    'state':    'rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#999999;dashed=1;',
    'text':     'text;html=1;align=left;verticalAlign=middle;',
}

# 走廊常量（每张图可在 draw() 时覆盖）
L_CORR = 190    # 左走廊（容器内缩 50px，容器左缘通常 140）
R_CORR = 1300   # 右走廊（容器外/容器内缩）


class Diagram:
    """一张流程图的画布：负责收集节点/容器/边，输出 .drawio XML"""

    def __init__(self, out_path, l_corr=L_CORR, r_corr=R_CORR):
        self.out = out_path
        self.l_corr = l_corr
        self.r_corr = r_corr
        self.cells = []
        self.edges = []
        self.conts = {}

    # ---- 基础工具 ----
    @staticmethod
    def esc(v):
        return (str(v).replace('&', '&amp;').replace('<', '&lt;')
                .replace('>', '&gt;').replace('"', '&quot;')
                .replace('\n', '&#xa;'))

    # ---- 元素 ----
    def container(self, cid, label, x, y, w, h, color='#d6b656', font='#7a5c00'):
        """泳道容器（带标题栏）"""
        self.conts[cid] = (x, y, w, h)
        style = (f'swimlane;startSize=34;whiteSpace=wrap;html=1;fillColor=none;'
                 f'strokeColor={color};fontColor={font};fontStyle=1;')
        self.cells.append(
            f'        <mxCell id="{cid}" value="{self.esc(label)}" style="{style}" vertex="1" parent="1">\n'
            f'          <mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry" />\n'
            f'        </mxCell>\n')

    def box(self, cid, label, ctype, parent, x, y, w, h):
        """节点（x,y 为页面绝对坐标，自动转容器相对）"""
        if parent != '1':
            cx, cy, _, _ = self.conts[parent]
            x, y = x - cx, y - cy
        self.cells.append(
            f'        <mxCell id="{cid}" value="{self.esc(label)}" style="{STYLE[ctype]}" vertex="1" parent="{parent}">\n'
            f'          <mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry" />\n'
            f'        </mxCell>\n')

    def edge(self, eid, src, tgt, label='', anchors=None, waypoints=None,
             color=None, dashed=False, exit_dx=0, exit_dy=0, entry_dx=0, entry_dy=0):
        """正交边；anchors=(exitX,exitY,entryX,entryY)；waypoints 为页面绝对坐标"""
        ex, ey, nx, ny = anchors if anchors else (0.5, 1.0, 0.5, 0.0)
        style = (f'edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;'
                 f'exitX={ex};exitY={ey};entryX={nx};entryY={ny};'
                 f'exitDx={exit_dx};exitDy={exit_dy};entryDx={entry_dx};entryDy={entry_dy};')
        if dashed:
            style += 'dashed=1;'
        if color:
            style += f'strokeColor={color};'
        if label:
            style += 'fontSize=11;labelBackgroundColor=#ffffff;'
        pts = ''
        if waypoints:
            pts = ('\n            <Array as="points">\n' +
                   ''.join(f'              <mxPoint x="{px}" y="{py}" />\n' for px, py in waypoints) +
                   '            </Array>\n')
        self.edges.append(
            f'        <mxCell id="{eid}" value="{self.esc(label)}" style="{style}" '
            f'edge="1" parent="1" source="{src}" target="{tgt}">\n'
            f'          <mxGeometry relative="1" as="geometry">{pts}          </mxGeometry>\n'
            f'        </mxCell>\n')

    def reject(self, eid, dnode, tnode, d_y, t_bottom_y):
        """驳回/回退：决策左出口 → 左走廊(内缩) → 目标下边缘进入"""
        self.edge(eid, dnode, tnode, '驳回', anchors=(0.0, 0.5, 0.5, 1.0),
                  waypoints=[(self.l_corr, d_y), (self.l_corr, t_bottom_y)],
                  color='#b85450', dashed=True)

    def legend(self, x, y, items, w=560, h=None):
        """图例；items=[(色板key, 文字)]"""
        h = h or (60 + 38 * len(items))
        self.container('lg', '图例', x, y, w, h, color='#999999', font='#333333')
        for i, (k, txt) in enumerate(items):
            iy = y + 50 + 38 * i
            self.box(f'lg{i}', '', k, 'lg', x + 20, iy, 30, 16)
            self.box(f'lg{i}t', txt, 'text', 'lg', x + 60, iy - 3, 200, 20)

    # ---- 输出 ----
    def write(self):
        out = X_HEADER
        for c in self.cells:
            out += c
        for e in self.edges:
            out += e
        out += X_FOOTER
        with open(self.out, 'w', encoding='utf-8') as f:
            f.write(out)
        print(f'written {self.out}: {len(self.cells)} cells, {len(self.edges)} edges')
