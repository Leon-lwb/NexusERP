"""A4 multi-page reference PDFs with readable CJK text and content hashes."""
import hashlib
from pathlib import Path
import pymupdf
from content import build_sections, profile_for, DRAFT_WARNING
import re

CSS = """
body {font-family: sans-serif; font-size: 10.5pt; line-height: 1.48; color: #24364b;}
h1 {font-size: 24pt; line-height: 1.25; color: #173451; margin: 0 0 14pt;}
h2 {font-size: 15pt; color: #173451; margin: 12pt 0 7pt;}
p {margin: 7pt 0;} li {margin: 5pt 0;}
ul,ol {padding-left: 20pt;}
table {border-collapse: collapse; width: 100%; margin: 10pt 0; font-size: 9.5pt;}
th {background-color: #edf3f8; text-align: left;}
th,td {border: 0.6pt solid #c9d3de; padding: 7pt; vertical-align: top;}
.warning {border-left: 3pt solid #b77915; background-color: #fff6e5; padding: 10pt; color: #805016;}
.badge {font-size: 13pt; color: #a05a06;}
.note,.refs {font-size: 9pt; color: #586d80;}
"""


def inspect_pdf(path):
    data = Path(path).read_bytes()
    if not data.startswith(b'%PDF-'):
        raise ValueError('Artifact is not a PDF')
    with pymupdf.open(stream=data, filetype='pdf') as pdf:
        text = '\n'.join(page.get_text() for page in pdf)
        if len(pdf) < 4 or len(text) < 1900 or DRAFT_WARNING not in text:
            raise ValueError('Unreadable, incomplete or unmarked draft PDF')
        return {'pages': len(pdf), 'textCharacters': len(text), 'fileSize': len(data),
                'sha256': hashlib.sha256(data).hexdigest(), 'sm3': hashlib.new('sm3', data).hexdigest(), 'text': text}


def render_pdf(document, path, version='V1.1'):
    path = Path(path)
    if path.exists():
        raise FileExistsError('Refusing to overwrite an existing local PDF')
    pdf = pymupdf.open()
    sections = build_sections(document, version)
    try:
        for index, html in enumerate(sections, 1):
            page = pdf.new_page(width=595.28, height=841.89)
            page.draw_rect(pymupdf.Rect(0, 0, 595.28, 7), color=None, fill=(0.09, 0.20, 0.32))
            page.insert_text((42, 37), 'NEXUS  /  MES  /  REFERENCE SOP', fontname='helv', fontsize=9, color=(0.28, 0.39, 0.5))
            page.insert_text((42, 52), document['sop_code'] + '  /  ' + version + '  /  DRAFT', fontname='helv', fontsize=8, color=(0.48, 0.49, 0.5))
            spare, scale = page.insert_htmlbox(pymupdf.Rect(42, 72, 553, 773), html, css=CSS, scale_low=0.85)
            if spare < 0 or scale < 0.85:
                raise ValueError('PDF section exceeds readable page layout')
            page.draw_line(pymupdf.Point(42, 789), pymupdf.Point(553, 789), color=(0.76, 0.81, 0.86), width=0.5)
            page.insert_text((42, 808), 'REFERENCE ONLY - NOT RELEASED FOR PRODUCTION', fontsize=8, color=(0.60, 0.34, 0.04))
            page.insert_text((488, 808), f'{index} / {len(sections)}', fontsize=8, color=(0.28, 0.39, 0.5))
        pdf.set_metadata({'title': document['sop_name'] + ' - ' + version + ' - 待审核',
                          'subject': DRAFT_WARNING, 'author': 'Nexus-ERP reference document generator',
                          'creator': 'Nexus-ERP SOP reference materialization',
                          'creationDate': 'D:20261008000000+08\'00\'', 'modDate': 'D:20261008000000+08\'00\''})
        pdf.subset_fonts()
        path.parent.mkdir(parents=True, exist_ok=True)
        # Exclusive creation: never overwrite previously reviewed or uploaded artifacts.
        with path.open('xb') as out:
            out.write(pdf.tobytes(deflate=True, garbage=4, no_new_id=True))
    finally:
        pdf.close()
    result = inspect_pdf(path)
    result.pop('text')
    return result


def validate_expected_pdf(path, document, version='V1.1'):
    info = inspect_pdf(path)
    normalize = lambda value: re.sub(r'\s+', '', value)
    text = normalize(info['text'])
    p = profile_for(document['process_code'])
    expected = [document['sop_code'], document['sop_name'], document['process_code'], version, 'DRAFT',
                '示例 / 待审核', DRAFT_WARNING, '目的、边界与职责', '作业前准备', '工序作业步骤',
                '关键参数', '质量控制', 'MES 记录', '异常处置', '作业结束', '现场审核', '公开参考资料']
    expected += p['steps'] + p['quality'] + p['risks'] + p['parameters']
    if info['pages'] != 5 or any(normalize(value) not in text for value in expected):
        raise ValueError('PDF does not match its expected SOP identity and reviewed process content')
    return info
