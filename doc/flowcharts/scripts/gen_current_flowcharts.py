#!/usr/bin/env python3
"""Render current, evidence-backed diagrams to editable draw.io and PNG."""

import argparse
import heapq
import json
import math
import os
import tempfile
import xml.etree.ElementTree as ET
from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = HERE.parent
DATE = "2026-10-12"
WIDTH = 1160
NODE_W, NODE_H, STEP = 300, 118, 174
COLS = (30, 430, 830)
KINDS = {
    "manual": ("#e4f1f5", "#39798d", "人工操作"),
    "auto": ("#e5f2e8", "#497c59", "系统处理"),
    "check": ("#fff4d7", "#a48026", "校验 / 审批"),
    "state": ("#f0f1f3", "#727980", "状态 / 分支"),
    "end": ("#e8ecef", "#4b5d69", "结果"),
}
EDGE_COLORS = {"normal": "#4b5d69", "exception": "#b94e4e", "async": "#39798d"}


@lru_cache(maxsize=24)
def font(size):
    candidates = [os.environ.get("NEXUS_DOC_FONT", ""),
                  str(Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts/msyh.ttc"),
                  "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"]
    for path in candidates:
        if path and Path(path).is_file():
            return ImageFont.truetype(path, size)
    raise RuntimeError("Chinese font required; set NEXUS_DOC_FONT to a CJK font file")


def wrap(text, face, width):
    lines = []
    for paragraph in text.split("\n"):
        line = ""
        for char in paragraph:
            if line and face.getlength(line + char) > width:
                lines.append(line.rstrip())
                line = char.lstrip()
            else:
                line += char
        lines.append(line.rstrip())
    return lines


def validate_spec(spec, check_sources=False):
    for key in ("id", "path", "title", "chapter", "summary", "sources", "nodes", "edges"):
        if not spec.get(key):
            raise ValueError(f"{spec.get('id')}: missing {key}")
    path = Path(spec["path"])
    if path.is_absolute() or ".." in path.parts or path.suffix != ".drawio":
        raise ValueError(f"Unsafe diagram path: {path}")
    ids, occupied = set(), set()
    for node in spec["nodes"]:
        if node["id"] in ids:
            raise ValueError(f"Duplicate node: {node['id']}")
        if node["kind"] not in KINDS or node["col"] not in range(3) or node["row"] not in range(9):
            raise ValueError(f"Invalid node: {node['id']}")
        slot = (node["col"], node["row"])
        if slot in occupied:
            raise ValueError(f"Node overlap: {slot}")
        ids.add(node["id"])
        occupied.add(slot)
    for edge in spec["edges"]:
        for key in ("from", "to"):
            if edge[key] not in ids:
                raise ValueError(f"Unknown edge endpoint: {edge[key]}")
        if edge.get("kind", "normal") not in EDGE_COLORS:
            raise ValueError(f"Unknown edge kind: {edge.get('kind')}")
    if check_sources:
        for source in spec["sources"]:
            if not (ROOT / source).is_file():
                raise ValueError(f"Missing source for {spec['id']}: {source}")


def load_specs():
    specs = []
    for path in sorted(HERE.glob("current_*.json")):
        specs.extend(json.loads(path.read_text(encoding="utf-8")))
    ids, paths = set(), set()
    for spec in specs:
        validate_spec(spec, check_sources=True)
        if spec["id"] in ids or spec["path"] in paths:
            raise ValueError(f"Duplicate diagram: {spec['id']}")
        ids.add(spec["id"])
        paths.add(spec["path"])
    return sorted(specs, key=lambda s: tuple(int(p) for p in s["chapter"].split(".")))


def rect(node, padding=0):
    return (node["x"] - padding, node["y"] - padding,
            node["x"] + NODE_W + padding, node["y"] + NODE_H + padding)


def intersects(a, b, box):
    x1, y1, x2, y2 = box
    if a[0] == b[0]:
        return x1 < a[0] < x2 and max(min(a[1], b[1]), y1) < min(max(a[1], b[1]), y2)
    return y1 < a[1] < y2 and max(min(a[0], b[0]), x1) < min(max(a[0], b[0]), x2)


def port(node, side):
    x, y = node["x"], node["y"]
    point = {"left": (x, y + NODE_H / 2), "right": (x + NODE_W, y + NODE_H / 2),
             "top": (x + NODE_W / 2, y), "bottom": (x + NODE_W / 2, y + NODE_H)}[side]
    dx, dy = {"left": (-22, 0), "right": (22, 0), "top": (0, -22), "bottom": (0, 22)}[side]
    return point, (point[0] + dx, point[1] + dy)


def route_edge(edge, nodes, height, previous=()):
    source, target = nodes[edge["from"]], nodes[edge["to"]]
    if edge.get("route") in ("left", "right") and target["row"] != source["row"]:
        sides = (edge["route"], edge["route"])
    elif edge.get('route') in ('left', 'right') and source['row'] == target['row']:
        sides = ('bottom', 'bottom')
    elif source["row"] == target["row"]:
        sides = ("right", "left") if source["col"] < target["col"] else ("left", "right")
    elif source["row"] < target["row"]:
        sides = ("bottom", "top")
    else:
        sides = ("left", "left")
    start, first = port(source, sides[0])
    end, last = port(target, sides[1])
    boxes = [rect(n, 12) for n in nodes.values()]
    xs = {12, WIDTH - 12, first[0], last[0]}
    ys = {94, height - 86, first[1], last[1]}
    for node in nodes.values():
        xs.update((node["x"] - 22, node["x"] + NODE_W + 22, node["x"] + NODE_W / 2))
        xs.update((node["x"] - 34, node["x"] + NODE_W + 34))
        ys.update((node["y"] - 22, node["y"] + NODE_H + 22, node["y"] + NODE_H / 2))
        ys.update((node["y"] - 34, node["y"] + NODE_H + 34))
    xs = {x for x in xs if 8 <= x <= WIDTH - 8}
    ys = {y for y in ys if 94 <= y <= height - 86}
    xs, ys = sorted(xs), sorted(ys)
    origin = (xs.index(first[0]), ys.index(first[1]), -1)
    queue = [(0, 0, origin)]
    costs, parents = {origin: 0}, {}
    destination = None
    while queue:
        _, cost, state = heapq.heappop(queue)
        if cost != costs.get(state):
            continue
        ix, iy, direction = state
        a = (xs[ix], ys[iy])
        if a == last:
            destination = state
            break
        for dx, dy, newdir in ((1, 0, 0), (-1, 0, 0), (0, 1, 1), (0, -1, 1)):
            nx, ny = ix + dx, iy + dy
            if not (0 <= nx < len(xs) and 0 <= ny < len(ys)):
                continue
            b = (xs[nx], ys[ny])
            if any(intersects(a, b, box) for box in boxes):
                continue
            nextstate = (nx, ny, newdir)
            nextcost = cost + abs(a[0] - b[0]) + abs(a[1] - b[1]) + (35 if direction not in (-1, newdir) else 0)
            for old in previous:
                for c, d in zip(old['points'], old['points'][1:]):
                    if a[0] == b[0] == c[0] == d[0]:
                        overlap = min(max(a[1], b[1]), max(c[1], d[1])) - max(min(a[1], b[1]), min(c[1], d[1]))
                    elif a[1] == b[1] == c[1] == d[1]:
                        overlap = min(max(a[0], b[0]), max(c[0], d[0])) - max(min(a[0], b[0]), min(c[0], d[0]))
                    else:
                        overlap = 0
                    nextcost += max(0, overlap) * 3
            if nextcost < costs.get(nextstate, math.inf):
                costs[nextstate] = nextcost
                parents[nextstate] = state
                heuristic = abs(b[0] - last[0]) + abs(b[1] - last[1])
                heapq.heappush(queue, (nextcost + heuristic, nextcost, nextstate))
    if destination is None:
        raise ValueError(f"No obstacle-free route: {edge}")
    points = []
    state = destination
    while True:
        points.append((xs[state[0]], ys[state[1]]))
        if state == origin:
            break
        state = parents[state]
    points = [start] + list(reversed(points)) + [end]
    compact = [points[0]]
    for i in range(1, len(points) - 1):
        a, b, c = compact[-1], points[i], points[i + 1]
        if a == b or (a[0] == b[0] == c[0]) or (a[1] == b[1] == c[1]):
            continue
        compact.append(b)
    compact.append(end)
    return {**edge, "points": compact, "sides": sides}


def layout_spec(spec):
    validate_spec(spec)
    nodes, text_errors = {}, []
    for original in spec["nodes"]:
        node = {**original, "x": COLS[original["col"]], "y": 112 + STEP * original["row"]}
        first_valid = None
        for size in (28, 27, 26, 25, 24, 23, 22):
            lines = wrap(node["text"], font(size), NODE_W - 48)
            if len(lines) * (size + 4) <= NODE_H - 12:
                if first_valid is None:
                    first_valid = (lines, size)
                if '\n' not in node['text'] or len(lines) == len(node['text'].split('\n')):
                    break
        else:
            if first_valid is None:
                text_errors.append(node["id"])
            else:
                lines, size = first_valid
        node.update(lines=lines, font_size=size)
        nodes[node["id"]] = node
    height = max(n["y"] + NODE_H for n in nodes.values()) + 116
    edges = []
    for edge in spec['edges']:
        edges.append(route_edge(edge, nodes, height, edges))
    layout = {"nodes": nodes, "edges": edges, "width": WIDTH, "height": height, "text_errors": text_errors}
    layout['labels'] = place_labels(layout)
    return layout


def boxes_overlap(a, b):
    return max(a[0], b[0]) < min(a[2], b[2]) and max(a[1], b[1]) < min(a[3], b[3])


def place_labels(layout):
    labels = []
    obstacles = [rect(node, 2) for node in layout['nodes'].values()]
    for index, edge in enumerate(layout['edges']):
        text = edge.get('label', '')
        if not text:
            continue
        candidates = []
        variants = [(21, [text]), (20, wrap(text, font(20), 86)), (18, wrap(text, font(18), 86))]
        for variant, (size, lines) in enumerate(variants):
            width = max(font(size).getlength(line) for line in lines) + 10
            height = len(lines) * (size + 3) + 4
            for a, b in zip(edge['points'], edge['points'][1:]):
                if math.dist(a, b) < 30:
                    continue
                fractions = [0.5, 0.25, 0.75]
                if a[0] == b[0]:
                    for node in layout['nodes'].values():
                        gap_y = node['y'] + NODE_H + (STEP - NODE_H) / 2
                        if min(a[1], b[1]) < gap_y < max(a[1], b[1]):
                            fractions.append((gap_y - a[1]) / (b[1] - a[1]))
                for fraction in fractions:
                    x = a[0] + fraction * (b[0] - a[0])
                    y = a[1] + fraction * (b[1] - a[1])
                    positions = [(x, y)]
                    if a[0] == b[0]:
                        positions += [(x + offset, y) for offset in (-48, -32, -16, 16, 32, 48)]
                    else:
                        positions += [(x, y + offset) for offset in (-height - 6, -height / 2 - 2, height / 2 + 2, height + 6)]
                    for cx, cy in positions:
                        box = (cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
                        if box[0] < 4 or box[2] > WIDTH - 4 or box[1] < 92 or box[3] > layout['height'] - 86:
                            continue
                        if any(boxes_overlap(box, other) for other in obstacles):
                            continue
                        if any(boxes_overlap(box, item['box']) for item in labels):
                            continue
                        score = variant * 500 + (0 if a[1] == b[1] else 20) + abs(fraction - 0.5) * 50 + abs(cx - x) + abs(cy - y)
                        candidates.append((score, box, size, lines))
        if not candidates:
            raise ValueError(f"No clear position for edge label: {text}")
        _, box, size, lines = min(candidates, key=lambda item: item[0])
        labels.append({'edge': index, 'text': text, 'lines': lines, 'font_size': size, 'box': box, 'kind': edge.get('kind', 'normal')})
    return labels


def geometry_errors(layout):
    errors = []
    for index, edge in enumerate(layout["edges"]):
        for a, b in zip(edge["points"], edge["points"][1:]):
            if a[0] != b[0] and a[1] != b[1]:
                errors.append(f"edge {index}: diagonal")
            for node in layout["nodes"].values():
                if intersects(a, b, rect(node)):
                    errors.append(f"edge {index}: penetrates {node['id']}")
    return errors


def xml_diagram(spec, layout):
    root = ET.Element("mxfile", host="app.diagrams.net", modified=f"{DATE}T00:00:00.000Z")
    diagram = ET.SubElement(root, "diagram", id=spec["id"], name=spec["title"])
    model = ET.SubElement(diagram, "mxGraphModel", grid="1", gridSize="10", page="1",
                          pageScale="1", pageWidth=str(WIDTH), pageHeight=str(layout["height"]), background="#ffffff")
    cells = ET.SubElement(model, "root")
    ET.SubElement(cells, "mxCell", id="0")
    ET.SubElement(cells, "mxCell", id="1", parent="0")
    title = ET.SubElement(cells, "mxCell", id="title", value=spec["title"], vertex="1", parent="1",
                          style="text;html=0;fontSize=34;fontStyle=1;align=left;fontFamily=Microsoft YaHei;")
    ET.SubElement(title, "mxGeometry", x="30", y="12", width="1100", height="48", **{"as": "geometry"})
    for node in layout["nodes"].values():
        fill, stroke, _ = KINDS[node["kind"]]
        shape = "shape=hexagon;" if node["kind"] == "check" else "rounded=1;arcSize=8;"
        style = f"{shape}whiteSpace=wrap;html=0;fillColor={fill};strokeColor={stroke};strokeWidth=2;fontColor=#25323a;fontSize={node['font_size']};fontFamily=Microsoft YaHei;spacing=12;"
        cell = ET.SubElement(cells, "mxCell", id="node_" + node["id"], value="\n".join(node["lines"]),
                             vertex="1", parent="1", style=style)
        ET.SubElement(cell, "mxGeometry", x=str(node["x"]), y=str(node["y"]), width=str(NODE_W), height=str(NODE_H), **{"as": "geometry"})
    anchors = {"left": (0, 0.5), "right": (1, 0.5), "top": (0.5, 0), "bottom": (0.5, 1)}
    for i, edge in enumerate(layout["edges"]):
        ex, ey = anchors[edge["sides"][0]]
        nx, ny = anchors[edge["sides"][1]]
        kind = edge.get("kind", "normal")
        style = f"edgeStyle=orthogonalEdgeStyle;rounded=0;html=0;endArrow=block;endFill=1;strokeWidth=2;strokeColor={EDGE_COLORS[kind]};fontSize=22;labelBackgroundColor=#ffffff;exitX={ex};exitY={ey};entryX={nx};entryY={ny};exitPerimeter=0;entryPerimeter=0;"
        if kind != "normal":
            style += "dashed=1;"
        cell = ET.SubElement(cells, "mxCell", id=f"edge_{i}", value="", style=style,
                             edge="1", parent="1", source="node_" + edge["from"], target="node_" + edge["to"])
        geom = ET.SubElement(cell, "mxGeometry", relative="1", **{"as": "geometry"})
        array = ET.SubElement(geom, "Array", **{"as": "points"})
        for x, y in edge["points"][1:-1]:
            ET.SubElement(array, "mxPoint", x=str(x), y=str(y))
    for i, label in enumerate(layout['labels']):
        x1, y1, x2, y2 = label['box']
        cell = ET.SubElement(cells, 'mxCell', id=f'label_{i}', value='\n'.join(label['lines']), vertex='1', parent='1',
                             style=f"text;html=0;align=center;verticalAlign=middle;fontSize={label['font_size']};fillColor=#ffffff;fontColor={EDGE_COLORS[label['kind']]};")
        ET.SubElement(cell, 'mxGeometry', x=str(x1), y=str(y1), width=str(x2 - x1), height=str(y2 - y1), **{'as': 'geometry'})
    legend = ET.SubElement(cells, "mxCell", id="legend", value="人工操作    系统处理    校验/审批    状态/结果    虚线：异步或异常分支", vertex="1", parent="1", style="text;html=0;align=center;fontSize=22;fontColor=#52616b;")
    ET.SubElement(legend, "mxGeometry", x="30", y=str(layout["height"] - 65), width="1100", height="40", **{"as": "geometry"})
    ET.indent(root, "  ")
    return ET.ElementTree(root)


def png_diagram(spec, layout, path):
    scale = 2
    image = Image.new("RGB", (WIDTH, layout["height"]), "white")
    draw = ImageDraw.Draw(image)
    draw.text((30, 15), spec["title"], font=font(34), fill="#25323a")
    draw.text((30, 64), f"NexusERP  |  {DATE}  |  手册 {spec['chapter']} 节", font=font(21), fill="#62717a")
    for edge in layout["edges"]:
        color = EDGE_COLORS[edge.get("kind", "normal")]
        for a, b in zip(edge["points"], edge["points"][1:]):
            if edge.get("kind", "normal") == "normal":
                draw.line((a, b), fill=color, width=3)
            else:
                length = math.dist(a, b)
                for start in range(0, int(length), 16):
                    r1, r2 = start / length, min(start + 9, length) / length
                    draw.line(((a[0] + (b[0] - a[0]) * r1, a[1] + (b[1] - a[1]) * r1),
                               (a[0] + (b[0] - a[0]) * r2, a[1] + (b[1] - a[1]) * r2)), fill=color, width=3)
        a, b = edge["points"][-2:]
        length = math.dist(a, b)
        dx, dy = (b[0] - a[0]) / length, (b[1] - a[1]) / length
        draw.polygon([b, (b[0] - 12 * dx + 5 * dy, b[1] - 12 * dy - 5 * dx),
                      (b[0] - 12 * dx - 5 * dy, b[1] - 12 * dy + 5 * dx)], fill=color)
    for node in layout["nodes"].values():
        fill, stroke, _ = KINDS[node["kind"]]
        x, y = node["x"], node["y"]
        if node["kind"] == "check":
            draw.polygon([(x + 16, y), (x + NODE_W - 16, y), (x + NODE_W, y + NODE_H / 2),
                          (x + NODE_W - 16, y + NODE_H), (x + 16, y + NODE_H), (x, y + NODE_H / 2)], fill=fill, outline=stroke, width=2)
        else:
            draw.rounded_rectangle(rect(node), radius=6, fill=fill, outline=stroke, width=2)
        face = font(node["font_size"])
        lineheight = node["font_size"] + 4
        starty = y + (NODE_H - len(node["lines"]) * lineheight) / 2
        for i, line in enumerate(node["lines"]):
            draw.text((x + (NODE_W - face.getlength(line)) / 2, starty + i * lineheight), line, font=face, fill="#25323a")
    for label in layout['labels']:
        x1, y1, x2, y2 = label['box']
        draw.rectangle(label['box'], fill='white')
        face = font(label['font_size'])
        for i, line in enumerate(label['lines']):
            draw.text(((x1 + x2 - face.getlength(line)) / 2, y1 - 1 + i * (label['font_size'] + 3)), line, font=face, fill=EDGE_COLORS[label['kind']])
    y = layout["height"] - 65
    for x, kind in zip((36, 254, 480, 720), ("manual", "auto", "check", "state")):
        fill, stroke, label = KINDS[kind]
        draw.rectangle((x, y + 3, x + 26, y + 25), fill=fill, outline=stroke)
        draw.text((x + 36, y - 3), label, font=font(21), fill="#52616b")
    draw.text((950, y - 3), "虚线: 异步/异常", font=font(20), fill="#52616b")
    rendered = image.resize((WIDTH * scale, layout['height'] * scale), Image.Resampling.LANCZOS)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix='.nexus-doc-', suffix='.png', delete=False) as stream:
            temporary = Path(stream.name)
            rendered.save(stream, format='PNG', dpi=(200, 200))
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def render_spec(spec, directory=OUT):
    layout = layout_spec(spec)
    errors = geometry_errors(layout) + layout["text_errors"]
    if errors:
        raise ValueError(f"{spec['id']}: {errors}")
    path = directory / spec["path"]
    path.parent.mkdir(parents=True, exist_ok=True)
    xml_diagram(spec, layout).write(path, encoding="utf-8", xml_declaration=True)
    preview = path.with_suffix(".preview.png")
    png_diagram(spec, layout, preview)
    return {"id": spec["id"], "path": spec["path"], "preview": preview.relative_to(directory).as_posix(),
            "chapter": spec["chapter"], "title": spec["title"], "width": WIDTH, "height": layout["height"],
            "nodes": len(layout["nodes"]), "edges": len(layout["edges"]), "geometry_errors": errors}


def write_indexes(specs):
    lines = [
        '# NexusERP 当前业务流程图集', '',
        '> 核对日期：2026-10-12；代码基线：`plus` / `fe59e4ae`。',
        '> 描述当前代码和页面，不代表生产部署、第三方服务就绪或人工端到端验收。', '',
        f'共 {len(specs)} 张流程图，每张提供可编辑 `.drawio` 和高分辨率 `.preview.png`，已嵌入 V4.0 用户手册。', '',
        '## 阅读入口', '',
        '- [Word 用户操作手册](../system/user-manual/NexusERP_用户操作手册.docx)',
        '- [逐图源码与使用条件](./来源与使用边界.md)',
        '- [生成清单与几何校验结果](./manifest.json)', '',
        '## 图集清单', '',
        '| 图号 | 手册章节 | 可编辑流程图 | PNG预览 |',
        '|------|----------|--------------|---------|',
    ]
    evidence = ['# 流程图来源与使用边界', '',
                '> 核对日期：2026-10-12；代码基线：`plus` / `fe59e4ae`。',
                '> 文件存在和代码实现不代表菜单已配置、角色已授权、生产已部署或现场已验收。', '']
    for i, spec in enumerate(specs, 1):
        preview = Path(spec['path']).with_suffix('.preview.png').as_posix()
        lines.append(f"| {i:02d} | {spec['chapter']} | [{spec['title']}](./{spec['path']}) | [预览](./{preview}) |")
        evidence.extend([f"## 图{i:02d} {spec['title']}", '', f"对应手册：{spec['chapter']} 节。", '', spec['summary'], '', '使用前：'])
        evidence.extend('- ' + text for text in spec.get('prerequisites', []))
        evidence.extend(['', '使用边界：'])
        evidence.extend('- ' + text for text in spec.get('caveats', []))
        evidence.extend(['', '代码依据：'])
        evidence.extend(f'- [{source}](../../{source})' for source in spec['sources'])
        evidence.append('')
    lines.extend(['', '## 维护与生成', '', '在仓库根目录执行：', '', '```powershell',
                  'python -X utf8 doc/flowcharts/scripts/gen_current_flowcharts.py --check',
                  'python -X utf8 doc/flowcharts/scripts/gen_current_flowcharts.py',
                  'python -X utf8 doc/flowcharts/scripts/gen_manual_v4.py',
                  'python -X utf8 -m unittest discover -s doc/flowcharts/scripts -p "test_*.py" -v',
                  '```', '',
                  '- 依赖：Python 3.10+、Pillow、python-docx；中文字体可由 `NEXUS_DOC_FONT` 指定。',
                  '- 完整回归包含Windows COM保护测试，需pywin32；非Windows可单独执行 `test_current_docs.py`。',
                  '- 当前事实源：`scripts/current_*.json` 与对应 `manual_ch*.py` / `manual_current*.py`，每图保留真实源码路径。',
                  '- 校验节点位置、连线端点、正交路径、不穿过节点、文字容纳、标签边界和源码存在性。该校验不等同于业务端到端测试，不沿用旧图的“validate score 0”结论。',
                  '- PNG 与 draw.io 使用同一布局。长连线的标签为独立可编辑文字节点；修改图后应同步维护图定义和重生成预览。',
                  '- 旧 `gen_erp_*` / `gen_mes_*` 等单图脚本及 `gen_manual.py` / `gen_manual_v3.py` 留作历史参考，不用于重建当前图集或手册。',
                  '- 本次未运行数据库脚本、启停业务服务、调用生产模型、上传真实文档或进行现场签署。', ''])
    (OUT / 'README.md').write_text('\n'.join(lines), encoding='utf-8')
    (OUT / '来源与使用边界.md').write_text('\n'.join(evidence), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Validate without writing outputs")
    args = parser.parse_args()
    results = []
    specs = load_specs()
    for spec in specs:
        if args.check:
            layout = layout_spec(spec)
            errors = geometry_errors(layout) + layout["text_errors"]
            if errors:
                raise ValueError(f"{spec['id']}: {errors}")
        else:
            results.append(render_spec(spec))
        print(f"OK {spec['id']} -> {spec['path']}")
    if not args.check:
        (OUT / "manifest.json").write_text(json.dumps({"date": DATE, "baseline": "plus/fe59e4ae", "diagrams": results}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        write_indexes(specs)
    print(f"Validated {len(specs)} diagrams")


if __name__ == "__main__":
    main()
