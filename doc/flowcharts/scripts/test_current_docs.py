"""Focused regression tests for the current documentation pipeline."""

import importlib
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image
from docx import Document


class CurrentDiagramTests(unittest.TestCase):
    def setUp(self):
        self.tools = importlib.import_module("gen_current_flowcharts")
        self.spec = {
            "id": "test", "path": "system/test.drawio", "title": "Test process",
            "chapter": "1.2", "summary": "Current code, not deployment evidence.",
            "sources": ["AGENTS.md"], "prerequisites": ["A configured environment"],
            "caveats": ["Not production acceptance"],
            "nodes": [
                {"id": "start", "text": "Create request", "kind": "manual", "row": 0, "col": 1},
                {"id": "gate", "text": "Check permission", "kind": "check", "row": 1, "col": 1},
                {"id": "done", "text": "Finish", "kind": "end", "row": 2, "col": 1},
                {"id": "fix", "text": "Correct request", "kind": "state", "row": 1, "col": 2},
            ],
            "edges": [
                {"from": "start", "to": "gate"},
                {"from": "gate", "to": "done", "label": "Yes"},
                {"from": "gate", "to": "fix", "label": "No", "kind": "exception"},
                {"from": "fix", "to": "start", "route": "right", "kind": "exception"},
            ],
        }

    def test_schema_accepts_a_valid_graph(self):
        self.tools.validate_spec(self.spec)

    def test_unknown_edge_target_is_rejected(self):
        self.spec["edges"][0]["to"] = "missing"
        with self.assertRaisesRegex(ValueError, "missing"):
            self.tools.validate_spec(self.spec)

    def test_colliding_nodes_are_rejected(self):
        self.spec["nodes"][1]["row"] = 0
        with self.assertRaisesRegex(ValueError, "overlap"):
            self.tools.validate_spec(self.spec)

    def test_output_cannot_escape_the_diagram_directory(self):
        self.spec["path"] = "../../outside.drawio"
        with self.assertRaisesRegex(ValueError, "path"):
            self.tools.validate_spec(self.spec)

    def test_drawio_and_png_have_the_same_node_and_edge_counts(self):
        with tempfile.TemporaryDirectory() as folder:
            info = self.tools.render_spec(self.spec, Path(folder))
            root = ET.parse(Path(folder) / self.spec["path"]).getroot()
            cells = root.findall(".//mxCell")
            self.assertEqual(4, len([c for c in cells if c.get("vertex") == "1" and c.get("id", "").startswith("node_")]))
            self.assertEqual(4, len([c for c in cells if c.get("edge") == "1"]))
            with Image.open(Path(folder) / info["preview"]) as image:
                self.assertGreater(image.width, 1000)
                self.assertGreater(image.height, 400)
                self.assertGreater(len(image.resize((100, 100)).getcolors(10000)), 8)

    def test_routed_edges_never_pass_through_another_node(self):
        layout = self.tools.layout_spec(self.spec)
        self.assertEqual([], self.tools.geometry_errors(layout))

    def test_long_text_is_wrapped_without_overflow(self):
        self.spec["nodes"][0]["text"] = "Permission checks are required before a business action is executed"
        self.assertEqual([], self.tools.layout_spec(self.spec)["text_errors"])

    def test_edge_labels_stay_inside_canvas_and_clear_of_nodes(self):
        layout = self.tools.layout_spec(self.spec)
        self.assertTrue(layout.get('labels'))
        for label in layout['labels']:
            x1, y1, x2, y2 = label['box']
            self.assertGreaterEqual(x1, 0)
            self.assertLessEqual(x2, layout['width'])
            self.assertGreaterEqual(y1, 0)
            self.assertLessEqual(y2, layout['height'])
            for node in layout['nodes'].values():
                nx1, ny1, nx2, ny2 = self.tools.rect(node)
                self.assertFalse(max(x1, nx1) < min(x2, nx2) and max(y1, ny1) < min(y2, ny2))

    def test_source_paths_exist_for_every_current_diagram(self):
        specs = self.tools.load_specs()
        self.assertGreaterEqual(len(specs), 29)
        for spec in specs:
            self.tools.validate_spec(spec, check_sources=True)

    def test_chapter_hook_does_not_recurse_when_overview_helpers_are_replaced(self):
        builder = importlib.import_module('gen_manual_v4')
        doc = Document()
        chapters = builder.Chapters([])
        original = builder.base.h1
        try:
            builder.base.h1 = chapters.h1
            chapters.h1(doc, 'First chapter')
        finally:
            builder.base.h1 = original
        self.assertIn('First chapter', [p.text for p in doc.paragraphs])

    def test_chapter_after_an_existing_page_break_does_not_add_an_empty_page(self):
        builder = importlib.import_module('gen_manual_v4')
        doc = Document()
        doc.add_paragraph('Cover')
        doc.add_page_break()
        builder.Chapters([]).h1(doc, 'Chapter')
        self.assertEqual(1, len(doc.element.xpath('.//w:br[@w:type="page"]')))

    def test_chapter_break_is_attached_to_heading_not_an_empty_paragraph(self):
        builder = importlib.import_module('gen_manual_v4')
        doc = Document()
        doc.add_paragraph('Last paragraph of a full page')
        builder.Chapters([]).h1(doc, 'Next chapter')
        self.assertEqual([], doc.element.xpath('.//w:br[@w:type="page"]'))
        self.assertTrue(doc.paragraphs[-1].paragraph_format.page_break_before)

    def test_separate_operation_lists_restart_their_numbering(self):
        builder = importlib.import_module('gen_manual_v4')
        doc = Document()
        first = doc.add_paragraph('First', style='List Number')
        second = doc.add_paragraph('Second', style='List Number')
        doc.add_paragraph('Another operation')
        third = doc.add_paragraph('First again', style='List Number')
        builder.normalize_numbering(doc)
        ids = [p._p.xpath('./w:pPr/w:numPr/w:numId')[0].val for p in (first, second, third)]
        self.assertEqual(ids[0], ids[1])
        self.assertNotEqual(ids[0], ids[2])


class CurrentManualTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[3]
        cls.doc = Document(root / "doc/system/user-manual/NexusERP_用户操作手册.docx")
        cls.text = "\n".join(p.text for p in cls.doc.paragraphs)

    def test_manual_has_current_version_and_baseline(self):
        self.assertIn("V4.0", self.text)
        self.assertIn("2026-10-12", self.text)
        self.assertIn("fe59e4ae", self.text)

    def test_current_diagrams_are_embedded_not_external(self):
        self.assertGreaterEqual(len(self.doc.inline_shapes), 29)
        for shape in self.doc.inline_shapes:
            self.assertLessEqual(shape.width, self.doc.sections[0].page_width - self.doc.sections[0].left_margin - self.doc.sections[0].right_margin)

    def test_new_operational_chapters_are_present(self):
        for term in ("第21章", "第22章", "第23章", "工业决策", "认证收货", "Flow", "Moquette"):
            self.assertIn(term, self.text)

    def test_obsolete_public_site_is_not_a_login_instruction(self):
        self.assertNotIn("http://nexuserp.website/index", self.text)

    def test_tables_fit_the_body_width(self):
        section = self.doc.sections[0]
        limit = section.page_width - section.left_margin - section.right_margin
        for table in self.doc.tables:
            self.assertLessEqual(sum(column.width for column in table.columns), limit + 1000)

    def test_all_diagrams_have_a_caption_and_a_section(self):
        tools = importlib.import_module("gen_current_flowcharts")
        for spec in tools.load_specs():
            self.assertIn(spec["title"], self.text)
            self.assertIn(spec["chapter"], self.text)


class LayoutVerificationTests(unittest.TestCase):
    def setUp(self):
        self.tools = importlib.import_module('verify_document_layout')

    def test_invalid_or_outside_pdf_bounds_are_detected(self):
        page = (0, 0, 595, 842)
        self.assertFalse(self.tools.out_of_bounds((60, 60, 500, 750), page))
        self.assertTrue(self.tools.out_of_bounds((-10, 0, 500, 750), page))
        self.assertTrue(self.tools.out_of_bounds((60, 60, float('nan'), 750), page))

    def test_existing_or_nonempty_office_sessions_are_refused(self):
        for count, processes in ((1, {123}), (0, set())):
            with self.assertRaises(self.tools.VerificationError):
                self.tools.require_empty_new_instance(count, processes)
        self.tools.require_empty_new_instance(0, {123})

    def test_sampling_includes_real_picture_pages(self):
        pages = [{'page': i, 'image_count': int(i in (4, 8))} for i in range(1, 11)]
        chosen = self.tools.select_sample_pages(pages, 8)
        self.assertIn(4, chosen)
        self.assertIn(8, chosen)
        self.assertIn(1, chosen)
        self.assertIn(10, chosen)


if __name__ == "__main__":
    unittest.main()
