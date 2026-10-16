#!/usr/bin/env python3
"""Generate the static NexusERP web manual from the verified DOCX manual."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import html
import json
import re
import shutil
from datetime import datetime
from pathlib import Path
from typing import Iterable

from docx import Document
from docx.document import Document as DocumentObject
from docx.oxml.ns import qn
from docx.table import Table
from docx.text.paragraph import Paragraph
from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_SOURCE = ROOT / "doc/system/user-manual/NexusERP_用户操作手册.docx"
DEFAULT_OUTPUT = ROOT / "plus-ui/public/manual"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def iter_blocks(document: DocumentObject) -> Iterable[Paragraph | Table]:
    for child in document.element.body.iterchildren():
        if child.tag == qn("w:p"):
            yield Paragraph(child, document)
        elif child.tag == qn("w:tbl"):
            yield Table(child, document)


def rich_text(paragraph: Paragraph) -> str:
    pieces: list[str] = []
    for run in paragraph.runs:
        value = html.escape(run.text, quote=False)
        value = value.replace("\t", "<span class=\"text-tab\"></span>")
        value = value.replace("\n", "<br>")
        if not value:
            continue
        if run.bold:
            value = f"<strong>{value}</strong>"
        if run.italic:
            value = f"<em>{value}</em>"
        if run.underline:
            value = f"<u>{value}</u>"
        pieces.append(value)
    return "".join(pieces) or html.escape(paragraph.text, quote=False)


def paragraph_level(paragraph: Paragraph) -> int:
    values = paragraph._p.xpath("./w:pPr/w:numPr/w:ilvl/@w:val")
    return int(values[0]) if values else 0


def heading_id(text: str, used: set[str], sequence: int) -> str:
    normalized = text.strip()
    patterns = (
        (r"^第\s*(\d+)\s*章", lambda match: f"chapter-{match.group(1)}"),
        (r"^附录\s*([A-Za-z])", lambda match: f"appendix-{match.group(1).lower()}"),
        (r"^([A-Za-z]?\d+(?:\.\d+){1,2})", lambda match: "section-" + match.group(1).lower().replace(".", "-")),
        (r"^([A-Za-z])\.(\d+(?:\.\d+)*)", lambda match: "section-" + match.group(1).lower() + "-" + match.group(2).replace(".", "-")),
    )
    candidate = ""
    for pattern, formatter in patterns:
        match = re.match(pattern, normalized)
        if match:
            candidate = formatter(match)
            break
    if not candidate:
        candidate = f"section-{sequence}"
    base = candidate
    suffix = 2
    while candidate in used:
        candidate = f"{base}-{suffix}"
        suffix += 1
    used.add(candidate)
    return candidate


def table_html(table: Table) -> str:
    rows: list[str] = []
    for row_index, row in enumerate(table.rows):
        cells = []
        tag = "th" if row_index == 0 else "td"
        for cell in row.cells:
            values = []
            for paragraph in cell.paragraphs:
                text = rich_text(paragraph).strip()
                if text:
                    values.append(text)
            cells.append(f"<{tag}>{'<br>'.join(values)}</{tag}>")
        rows.append(f"<tr>{''.join(cells)}</tr>")
    if not rows:
        return ""
    head = rows[0]
    body = "".join(rows[1:])
    return (
        '<div class="table-scroll"><table>'
        f"<thead>{head}</thead><tbody>{body}</tbody>"
        "</table></div>"
    )


def image_parts(paragraph: Paragraph):
    namespace = {
        "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
        "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    }
    parts = []
    for blip in paragraph._p.xpath(".//a:blip"):
        relation_id = blip.get("{%s}embed" % namespace["r"])
        if relation_id:
            parts.append(paragraph.part.related_parts[relation_id])
    return parts


def write_image(part, assets: Path, number: int) -> dict:
    suffix = Path(str(part.partname)).suffix.lower() or ".png"
    filename = f"diagram-{number:02d}{suffix}"
    target = assets / filename
    target.write_bytes(part.blob)
    width = height = None
    try:
        with Image.open(target) as image:
            width, height = image.size
    except Exception:
        pass
    return {
        "src": f"assets/{filename}",
        "filename": filename,
        "bytes": target.stat().st_size,
        "sha256": sha256_file(target),
        "width": width,
        "height": height,
    }


def figure_html(images: list[dict], caption: str = "") -> str:
    escaped_caption = html.escape(caption.strip() or "流程图", quote=True)
    blocks = []
    for image in images:
        size = ""
        if image["width"] and image["height"]:
            size = f' width="{image["width"]}" height="{image["height"]}"'
        blocks.append(
            '<button class="diagram-button" type="button" '
            f'data-src="{html.escape(image["src"], quote=True)}" '
            f'data-caption="{escaped_caption}" aria-label="查看大图：{escaped_caption}">'
            f'<img src="{html.escape(image["src"], quote=True)}" alt="{escaped_caption}" '
            f'loading="lazy" decoding="async"{size}>'
            "</button>"
        )
    caption_html = f"<figcaption>{html.escape(caption)}</figcaption>" if caption else ""
    return f'<figure>{"".join(blocks)}{caption_html}</figure>'


def render_cover(lines: list[str]) -> str:
    useful = [line.strip() for line in lines if line.strip()]
    brand = useful[0] if useful else "NexusERP"
    title = useful[1] if len(useful) > 1 else "用户操作手册"
    subtitle = useful[2] if len(useful) > 2 else "ERP · MES · IoT · AI"
    metadata = useful[3:7]
    boundary = useful[7] if len(useful) > 7 else ""
    meta_html = "".join(f"<span>{html.escape(item)}</span>" for item in metadata)
    boundary_html = f"<p>{html.escape(boundary)}</p>" if boundary else ""
    return f"""
      <header class="manual-cover" id="top">
        <div class="cover-brand">{html.escape(brand)}</div>
        <h1>{html.escape(title)}</h1>
        <div class="cover-subtitle">{html.escape(subtitle)}</div>
        <div class="cover-meta">{meta_html}</div>
        {boundary_html}
      </header>
    """.strip()


def navigation_html(headings: list[dict]) -> str:
    items = []
    for item in headings:
        if item["level"] > 2:
            continue
        class_name = f"nav-level-{item['level']}"
        items.append(
            f'<a class="{class_name}" href="#{item["id"]}" '
            f'data-search="{html.escape(item["text"].lower(), quote=True)}">'
            f'{html.escape(item["text"])}</a>'
        )
    return "".join(items)


def page_template(*, article: str, cover: str, navigation: str, source_sha256: str, generated_at: str, title: str, version: str, review_date: str) -> str:
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="source-sha256" content="{source_sha256}">
  <meta name="generated-at" content="{html.escape(generated_at, quote=True)}">
  <link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='10' fill='%23087f72'/%3E%3Cpath d='M16 47V17h8l16 19V17h8v30h-8L24 28v19z' fill='white'/%3E%3C/svg%3E">
  <title>{html.escape(title)}</title>
  <style>
    :root {{
      color-scheme: light;
      --bg: #f4f6f7;
      --surface: #ffffff;
      --text: #1c262b;
      --muted: #637178;
      --line: #d9e0e3;
      --accent: #087f72;
      --accent-soft: #e7f5f2;
      --chapter: #a44a22;
      --chapter-soft: #fff2eb;
      --mark: #ffe58f;
      --sidebar: 318px;
      font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Arial, sans-serif;
    }}
    * {{ box-sizing: border-box; }}
    html {{ scroll-behavior: smooth; scroll-padding-top: 76px; }}
    body {{ margin: 0; background: var(--bg); color: var(--text); line-height: 1.75; }}
    button, input {{ font: inherit; }}
    .topbar {{
      position: fixed; inset: 0 0 auto 0; height: 58px; z-index: 40;
      display: flex; align-items: center; gap: 14px; padding: 0 22px;
      background: rgba(255,255,255,.96); border-bottom: 1px solid var(--line);
      backdrop-filter: blur(10px);
    }}
    .menu-button {{
      display: none; width: 38px; height: 38px; border: 1px solid var(--line);
      border-radius: 6px; background: var(--surface); cursor: pointer;
    }}
    .menu-button span {{ display: block; width: 17px; height: 2px; margin: 4px auto; background: var(--text); }}
    .topbar-title {{ font-size: 15px; font-weight: 700; }}
    .topbar-version {{ color: var(--muted); font-size: 12px; }}
    .sidebar {{
      position: fixed; left: 0; top: 58px; bottom: 0; z-index: 30;
      width: var(--sidebar); overflow: auto; padding: 20px 18px 28px;
      background: var(--surface); border-right: 1px solid var(--line);
    }}
    .search-box {{ position: sticky; top: 0; z-index: 2; padding-bottom: 14px; background: var(--surface); }}
    .search-row {{ display: grid; grid-template-columns: 1fr auto; gap: 8px; }}
    .search-row input {{
      min-width: 0; height: 38px; padding: 0 11px; border: 1px solid #b9c5ca;
      border-radius: 6px; color: var(--text); background: #fff; outline: none;
    }}
    .search-row input:focus {{ border-color: var(--accent); box-shadow: 0 0 0 3px rgba(8,127,114,.12); }}
    .search-row button {{
      height: 38px; padding: 0 12px; border: 0; border-radius: 6px;
      color: #fff; background: var(--accent); cursor: pointer;
    }}
    .search-status {{ min-height: 20px; margin-top: 5px; color: var(--muted); font-size: 12px; }}
    .manual-nav {{ display: flex; flex-direction: column; gap: 2px; }}
    .manual-nav a {{
      display: block; padding: 7px 9px; border-left: 3px solid transparent;
      color: #334047; text-decoration: none; font-size: 13px; line-height: 1.45;
    }}
    .manual-nav a:hover {{ background: #f1f5f5; color: var(--accent); }}
    .manual-nav a.active {{ border-left-color: var(--accent); background: var(--accent-soft); color: #075e55; font-weight: 700; }}
    .manual-nav .nav-level-1 {{ margin-top: 8px; font-weight: 700; }}
    .manual-nav .nav-level-2 {{ padding-left: 22px; color: #5c686e; }}
    .page {{ margin-left: var(--sidebar); padding: 92px 42px 72px; }}
    .document {{ width: min(1080px, 100%); margin: 0 auto; }}
    .manual-cover {{
      min-height: 480px; display: flex; flex-direction: column; justify-content: center;
      padding: 48px 56px; margin-bottom: 52px; background: var(--surface);
      border-top: 5px solid var(--accent); border-bottom: 1px solid var(--line);
    }}
    .cover-brand {{ color: var(--accent); font-size: 20px; font-weight: 800; }}
    .manual-cover h1 {{ margin: 18px 0 4px; font-size: 46px; line-height: 1.2; font-weight: 800; }}
    .cover-subtitle {{ color: var(--chapter); font-size: 18px; font-weight: 700; }}
    .cover-meta {{ display: grid; gap: 5px; margin-top: 42px; color: #4e5c63; font-size: 14px; }}
    .manual-cover p {{ max-width: 720px; margin: 28px 0 0; padding-top: 18px; border-top: 1px solid var(--line); color: var(--muted); }}
    article {{ padding: 0 56px 80px; overflow-x: clip; background: var(--surface); border-top: 1px solid var(--line); }}
    article h1, article h2, article h3 {{ position: relative; letter-spacing: 0; }}
    article h1 {{
      margin: 64px -56px 28px; padding: 19px 56px 17px;
      border-top: 1px solid #edd3c4; border-bottom: 1px solid #edd3c4;
      color: #7e351a; background: var(--chapter-soft); font-size: 28px; line-height: 1.35;
    }}
    article h1:first-child {{ margin-top: 0; }}
    article h2 {{ margin: 42px 0 17px; padding-bottom: 9px; border-bottom: 2px solid var(--accent-soft); font-size: 22px; color: #173f3b; }}
    article h3 {{ margin: 30px 0 12px; font-size: 17px; color: #32454d; }}
    article p {{ margin: 10px 0; text-align: justify; }}
    article ul, article ol {{ margin: 10px 0 18px; padding-left: 28px; }}
    article li {{ margin: 5px 0; padding-left: 4px; }}
    article strong {{ color: #1d4e49; }}
    .text-tab {{ display: inline-block; width: 1.8em; }}
    .table-scroll {{ width: 100%; margin: 18px 0 28px; overflow-x: auto; border: 1px solid var(--line); }}
    table {{ width: 100%; min-width: 620px; border-collapse: collapse; background: #fff; font-size: 13px; line-height: 1.55; }}
    th, td {{ padding: 10px 12px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; }}
    th:last-child, td:last-child {{ border-right: 0; }}
    tbody tr:last-child td {{ border-bottom: 0; }}
    th {{ color: #174b45; background: #eaf4f2; font-weight: 700; }}
    tbody tr:nth-child(even) {{ background: #f8faf9; }}
    figure {{ margin: 28px 0 32px; padding: 14px; border: 1px solid var(--line); background: #fbfcfc; }}
    .diagram-button {{ display: block; width: 100%; padding: 0; border: 0; background: transparent; cursor: zoom-in; }}
    figure img {{ display: block; width: 100%; height: auto; max-height: 760px; object-fit: contain; }}
    figcaption {{ margin-top: 12px; color: #4f5d63; text-align: center; font-size: 13px; font-weight: 700; }}
    mark {{ padding: 0 2px; color: inherit; background: var(--mark); }}
    .back-top {{
      position: fixed; right: 24px; bottom: 24px; width: 42px; height: 42px;
      border: 1px solid #aebbc0; border-radius: 50%; background: #fff; color: var(--text);
      box-shadow: 0 5px 18px rgba(18,35,43,.14); cursor: pointer;
    }}
    dialog {{ width: min(96vw, 1500px); max-width: none; max-height: 94vh; padding: 48px 18px 18px; border: 0; border-radius: 8px; background: #fff; }}
    dialog::backdrop {{ background: rgba(16,24,28,.78); }}
    dialog img {{ display: block; max-width: 100%; max-height: calc(94vh - 100px); margin: auto; object-fit: contain; }}
    .dialog-caption {{ margin: 10px 0 0; text-align: center; color: var(--muted); }}
    .dialog-close {{ position: absolute; top: 8px; right: 10px; width: 34px; height: 34px; border: 0; border-radius: 50%; background: #edf1f2; cursor: pointer; font-size: 24px; line-height: 1; }}
    .sidebar-overlay {{ display: none; }}
    @media (max-width: 980px) {{
      :root {{ --sidebar: 300px; }}
      .menu-button {{ display: block; }}
      .sidebar {{ transform: translateX(-101%); transition: transform .2s ease; box-shadow: 12px 0 30px rgba(22,38,44,.15); }}
      body.sidebar-open .sidebar {{ transform: translateX(0); }}
      .sidebar-overlay {{ position: fixed; inset: 58px 0 0; z-index: 20; background: rgba(20,31,36,.38); }}
      body.sidebar-open .sidebar-overlay {{ display: block; }}
      .page {{ margin-left: 0; padding: 82px 20px 60px; }}
      .manual-cover {{ min-height: 420px; padding: 38px 30px; }}
      article {{ padding: 0 30px 64px; }}
      article h1 {{ margin-left: -30px; margin-right: -30px; padding-left: 30px; padding-right: 30px; }}
    }}
    @media (max-width: 560px) {{
      .topbar {{ padding: 0 13px; }}
      .topbar-version {{ display: none; }}
      .page {{ padding: 72px 0 50px; }}
      .manual-cover {{ min-height: 360px; margin-bottom: 16px; padding: 32px 20px; border-top-width: 4px; }}
      .manual-cover h1 {{ font-size: 34px; }}
      article {{ padding: 0 18px 54px; }}
      article h1 {{ margin: 46px -18px 24px; padding: 16px 18px; font-size: 24px; }}
      article h2 {{ font-size: 20px; }}
      figure {{ margin-left: -10px; margin-right: -10px; padding: 8px; }}
      .back-top {{ right: 14px; bottom: 14px; }}
    }}
    @media print {{
      .topbar, .sidebar, .sidebar-overlay, .back-top, dialog {{ display: none !important; }}
      body {{ background: #fff; }}
      .page {{ margin: 0; padding: 0; }}
      .document {{ width: 100%; }}
      .manual-cover {{ min-height: 90vh; break-after: page; }}
      article {{ padding: 0; border: 0; }}
      article h1 {{ margin-left: 0; margin-right: 0; padding-left: 0; padding-right: 0; break-before: page; background: none; }}
      figure, .table-scroll {{ break-inside: avoid; }}
      .diagram-button {{ cursor: default; }}
    }}
  </style>
</head>
<body>
  <header class="topbar">
    <button class="menu-button" id="menu-button" type="button" aria-label="打开目录" aria-expanded="false"><span></span><span></span><span></span></button>
    <div class="topbar-title">NexusERP 用户操作手册</div>
    <div class="topbar-version">V{html.escape(version)} · 核对日期 {html.escape(review_date)}</div>
  </header>
  <aside class="sidebar" id="sidebar">
    <div class="search-box">
      <form class="search-row" id="search-form">
        <input id="manual-search" type="search" placeholder="搜索手册内容" autocomplete="off" aria-label="搜索手册内容">
        <button type="submit">搜索</button>
      </form>
      <div class="search-status" id="search-status" aria-live="polite"></div>
    </div>
    <nav class="manual-nav" aria-label="手册目录">{navigation}</nav>
  </aside>
  <button class="sidebar-overlay" id="sidebar-overlay" type="button" aria-label="关闭目录"></button>
  <main class="page">
    <div class="document">
      {cover}
      <article id="manual-content">{article}</article>
    </div>
  </main>
  <button class="back-top" id="back-top" type="button" aria-label="返回顶部">↑</button>
  <dialog id="diagram-dialog">
    <button class="dialog-close" type="button" aria-label="关闭">×</button>
    <img alt="">
    <p class="dialog-caption"></p>
  </dialog>
  <script>
    (() => {{
      const body = document.body;
      const menuButton = document.querySelector('#menu-button');
      const overlay = document.querySelector('#sidebar-overlay');
      const nav = document.querySelector('.manual-nav');
      const searchForm = document.querySelector('#search-form');
      const searchInput = document.querySelector('#manual-search');
      const searchStatus = document.querySelector('#search-status');
      const content = document.querySelector('#manual-content');
      const dialog = document.querySelector('#diagram-dialog');

      const closeSidebar = () => {{
        body.classList.remove('sidebar-open');
        menuButton.setAttribute('aria-expanded', 'false');
      }};
      menuButton.addEventListener('click', () => {{
        const open = body.classList.toggle('sidebar-open');
        menuButton.setAttribute('aria-expanded', String(open));
      }});
      overlay.addEventListener('click', closeSidebar);
      nav.addEventListener('click', (event) => {{ if (event.target.closest('a')) closeSidebar(); }});

      const clearMarks = () => {{
        content.querySelectorAll('mark[data-manual-search]').forEach((mark) => mark.replaceWith(document.createTextNode(mark.textContent)));
        content.normalize();
      }};
      const search = () => {{
        clearMarks();
        const query = searchInput.value.trim();
        if (!query) {{ searchStatus.textContent = ''; return; }}
        const lowered = query.toLocaleLowerCase('zh-CN');
        const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT, {{
          acceptNode(node) {{
            if (!node.nodeValue.trim() || node.parentElement.closest('script,style,button')) return NodeFilter.FILTER_REJECT;
            return node.nodeValue.toLocaleLowerCase('zh-CN').includes(lowered) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
          }}
        }});
        const nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);
        let matches = 0;
        nodes.forEach((node) => {{
          const source = node.nodeValue;
          const lowerSource = source.toLocaleLowerCase('zh-CN');
          const fragment = document.createDocumentFragment();
          let start = 0;
          let index = lowerSource.indexOf(lowered);
          while (index >= 0) {{
            fragment.append(document.createTextNode(source.slice(start, index)));
            const mark = document.createElement('mark');
            mark.dataset.manualSearch = 'true';
            mark.textContent = source.slice(index, index + query.length);
            fragment.append(mark);
            matches += 1;
            start = index + query.length;
            index = lowerSource.indexOf(lowered, start);
          }}
          fragment.append(document.createTextNode(source.slice(start)));
          node.replaceWith(fragment);
        }});
        searchStatus.textContent = matches ? `找到 ${{matches}} 处` : '未找到匹配内容';
        content.querySelector('mark[data-manual-search]')?.scrollIntoView({{ behavior: 'smooth', block: 'center' }});
      }};
      searchForm.addEventListener('submit', (event) => {{ event.preventDefault(); search(); }});
      searchInput.addEventListener('search', () => {{ if (!searchInput.value) {{ clearMarks(); searchStatus.textContent = ''; }} }});

      const links = new Map([...nav.querySelectorAll('a')].map((link) => [link.getAttribute('href').slice(1), link]));
      const observer = new IntersectionObserver((entries) => {{
        entries.filter((entry) => entry.isIntersecting).forEach((entry) => {{
          nav.querySelectorAll('a.active').forEach((link) => link.classList.remove('active'));
          links.get(entry.target.id)?.classList.add('active');
        }});
      }}, {{ rootMargin: '-70px 0px -72% 0px', threshold: 0 }});
      content.querySelectorAll('h1[id],h2[id]').forEach((heading) => observer.observe(heading));

      document.querySelector('#back-top').addEventListener('click', () => window.scrollTo({{ top: 0, behavior: 'smooth' }}));
      content.addEventListener('click', (event) => {{
        const button = event.target.closest('.diagram-button');
        if (!button) return;
        dialog.querySelector('img').src = button.dataset.src;
        dialog.querySelector('img').alt = button.dataset.caption;
        dialog.querySelector('.dialog-caption').textContent = button.dataset.caption;
        dialog.showModal();
      }});
      dialog.querySelector('.dialog-close').addEventListener('click', () => dialog.close());
      dialog.addEventListener('click', (event) => {{ if (event.target === dialog) dialog.close(); }});
    }})();
  </script>
</body>
</html>
"""


def generate_manual_html(source: Path | str, output: Path | str) -> dict:
    source = Path(source).resolve(strict=True)
    output = Path(output).resolve()
    if source.suffix.lower() != ".docx":
        raise ValueError("source must be a DOCX file")
    if output.exists() and any(output.iterdir()):
        raise ValueError(f"output directory must be empty: {output}")
    output.mkdir(parents=True, exist_ok=True)
    assets = output / "assets"
    assets.mkdir()

    document = Document(source)
    cover_lines: list[str] = []
    headings: list[dict] = []
    article_parts: list[str] = []
    used_ids: set[str] = set()
    image_manifest: list[dict] = []
    pending_images: list[dict] = []
    list_type: str | None = None
    list_level = 0
    seen_first_heading = False
    table_count = 0

    def close_list():
        nonlocal list_type
        if list_type:
            article_parts.append(f"</{list_type}>")
            list_type = None

    def flush_figure(caption: str = ""):
        nonlocal pending_images
        if pending_images:
            close_list()
            article_parts.append(figure_html(pending_images, caption))
            pending_images = []

    for block in iter_blocks(document):
        if isinstance(block, Table):
            if not seen_first_heading:
                continue
            flush_figure()
            close_list()
            article_parts.append(table_html(block))
            table_count += 1
            continue

        paragraph = block
        style = paragraph.style.name if paragraph.style else "Normal"
        text = paragraph.text.strip()

        if not seen_first_heading and style != "Heading 1":
            if text:
                cover_lines.append(text)
            continue
        if style == "Heading 1":
            seen_first_heading = True
        if style.lower().startswith("toc ") or (style == "Heading 1" and text == "目录"):
            continue

        parts = image_parts(paragraph)
        if parts:
            flush_figure()
            for part in parts:
                image = write_image(part, assets, len(image_manifest) + 1)
                image_manifest.append(image)
                pending_images.append(image)
            continue
        if style == "Caption":
            flush_figure(text)
            continue
        if pending_images:
            flush_figure()

        if not text:
            continue
        if style in ("Heading 1", "Heading 2", "Heading 3"):
            close_list()
            level = int(style[-1])
            identifier = heading_id(text, used_ids, len(headings) + 1)
            kind = "chapter" if re.match(r"^第\s*\d+\s*章", text) else "section"
            if text.startswith("附录"):
                kind = "appendix"
            headings.append({"id": identifier, "text": text, "level": level, "kind": kind})
            article_parts.append(
                f'<h{level} id="{identifier}" data-kind="{kind}">{html.escape(text)}</h{level}>'
            )
            continue
        if style in ("List Number", "List Bullet"):
            target_type = "ol" if style == "List Number" else "ul"
            level = paragraph_level(paragraph)
            if list_type != target_type or list_level != level:
                close_list()
                list_type = target_type
                list_level = level
                article_parts.append(f'<{target_type} data-level="{level}">')
            article_parts.append(f"<li>{rich_text(paragraph)}</li>")
            continue
        close_list()
        article_parts.append(f'<p class="manual-paragraph">{rich_text(paragraph)}</p>')

    flush_figure()
    close_list()

    source_hash = sha256_file(source)
    generated_at = datetime.now().astimezone().isoformat(timespec="seconds")
    chapter_count = sum(item["kind"] == "chapter" for item in headings)
    version = document.core_properties.version or "unknown"
    title = document.core_properties.title or "NexusERP 用户操作手册"
    review_date = next((re.search(r"20\d{2}-\d{2}-\d{2}", line).group(0)
                        for line in cover_lines
                        if "核对日期" in line and re.search(r"20\d{2}-\d{2}-\d{2}", line)), "见正文")
    page = page_template(
        article="".join(article_parts),
        cover=render_cover(cover_lines),
        navigation=navigation_html(headings),
        source_sha256=source_hash,
        generated_at=generated_at,
        title=title, version=version, review_date=review_date,
    )
    index = output / "index.html"
    index.write_text(page, encoding="utf-8", newline="\n")
    with (output / "index.html.gz").open("wb") as raw:
        with gzip.GzipFile(filename="index.html", mode="wb", fileobj=raw, mtime=0, compresslevel=9) as compressed:
            compressed.write(index.read_bytes())

    result = {
        "source": str(source),
        "source_sha256": source_hash,
        "generated_at": generated_at,
        "version": version,
        "review_date": review_date,
        "chapters": chapter_count,
        "headings": len(headings),
        "tables": table_count,
        "images": len(image_manifest),
        "index_bytes": index.stat().st_size,
        "gzip_bytes": (output / "index.html.gz").stat().st_size,
        "assets_bytes": sum(item["bytes"] for item in image_manifest),
        "assets": image_manifest,
    }
    (output / "manifest.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    arguments = parser.parse_args()
    result = generate_manual_html(arguments.input, arguments.output)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
