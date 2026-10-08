"""Offline tests: no database or object-storage writes."""
import importlib.util
import pathlib
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'rebuild'))
from catalog import PROCESSES
from content import PROFILES, SOURCES, build_html, profile_for
from pdf_document import render_pdf, inspect_pdf


class ContentTests(unittest.TestCase):
    def test_all_57_processes_have_distinct_complete_profiles(self):
        codes = {p[0] for p in PROCESSES}
        self.assertEqual(57, len(codes))
        self.assertEqual(codes, set(PROFILES))
        fingerprints = set()
        for code, name, *_ in PROCESSES:
            p = profile_for(code)
            self.assertEqual(name, p['name'])
            self.assertGreaterEqual(len(p['steps']), 4)
            self.assertGreaterEqual(len(p['quality']), 2)
            self.assertGreaterEqual(len(p['parameters']), 4)
            self.assertGreaterEqual(len(p['risks']), 2)
            self.assertTrue(all(s in SOURCES for s in p['sources']))
            fingerprints.add(tuple(p['steps']))
        self.assertEqual(57, len(fingerprints))

    def test_every_document_is_draft_with_original_text_and_sources(self):
        for code, name, *_ in PROCESSES:
            html = build_html({'sop_id': 1, 'sop_code': 'SOP_TEST', 'sop_name': name + '作业指导书', 'process_code': code}, 'V1.1')
            for expected in ['示例 / 待审核', '未经现场审批不得用于生产', 'MES', '关键参数', '异常', '公开参考资料', '2026-10-08', code, name]:
                self.assertIn(expected, html)
            self.assertGreater(len(html), 4500)
            self.assertNotIn('已批准', html)
            self.assertNotIn('TODO', html)

    def test_unknown_process_fails_closed(self):
        with self.assertRaises(ValueError):
            profile_for('UNKNOWN')

    def test_html_escapes_business_metadata(self):
        html = build_html({'sop_id': 1, 'sop_code': '<img src=x onerror=x>', 'sop_name': '<script>alert(1)</script>', 'process_code': 'LASER'}, 'V1.1')
        self.assertNotIn('<script>', html)
        self.assertIn('&lt;script&gt;', html)

    def test_sources_are_primary_and_have_review_dates(self):
        for source in SOURCES.values():
            self.assertTrue(source['url'].startswith('https://'))
            self.assertIn(source['host'], {'www.osha.gov', 'www.hse.gov.uk', 'www.nist.gov', 'www.esda.org', 'csrc.nist.gov'})
            self.assertEqual('2026-10-08', source['reviewed'])


class PdfTests(unittest.TestCase):
    def test_chinese_multipage_pdf_is_readable_and_hashed(self):
        with tempfile.TemporaryDirectory() as d:
            path = pathlib.Path(d) / 'laser.pdf'
            info = render_pdf({'sop_id': 1, 'sop_code': 'SOP_TEST', 'sop_name': '激光切割作业指导书', 'process_code': 'LASER'}, path)
            self.assertTrue(path.read_bytes().startswith(b'%PDF-'))
            self.assertGreaterEqual(info['pages'], 4)
            self.assertGreater(info['textCharacters'], 1900)
            self.assertEqual(64, len(info['sha256']))
            self.assertEqual(64, len(info['sm3']))
            checked = inspect_pdf(path)
            self.assertEqual(info['sha256'], checked['sha256'])
            self.assertIn('激光切割', checked['text'])
            self.assertIn('未经现场审批不得用于生产', checked['text'])
            self.assertIn('SOP_TEST', checked['text'])
            self.assertIn('V1.1', checked['text'])

    def test_all_sections_survive_pdf_layout(self):
        with tempfile.TemporaryDirectory() as d:
            path = pathlib.Path(d) / 'qc.pdf'
            render_pdf({'sop_id': 54, 'sop_code': 'SOP_TEST_QC', 'sop_name': '质检作业指导书', 'process_code': 'QC'}, path)
            text = inspect_pdf(path)['text']
            for expected in ['作业步骤', '关键参数', '质量控制', '异常处置', '现场审核', '公开参考资料', '批次', '校准']:
                self.assertIn(expected, text)


if __name__ == '__main__':
    unittest.main()
