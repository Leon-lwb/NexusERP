"""Offline regressions for SOP classification and unattached initialization seeds.

Read the generator as AST only; never import or execute its rebuild code.
"""
from __future__ import annotations

import ast
import importlib.util
import re
import subprocess
import sys
import unittest
from pathlib import Path

REBUILD_DIR = Path(__file__).resolve().parent
ROOT = REBUILD_DIR.parents[2]
TYPE_MODULE = REBUILD_DIR / "sop_types.py"
GENERATOR = REBUILD_DIR / "generate.py"
MAIN_SQL = ROOT / "script/sql/ry-mes.sql"
SEED_IDS = set(range(1, 58))
PLACEHOLDER_CHANGE_LOG = "初始化草稿占位，需上传真实文件后提交审批"
INSPECTION_CODES = {
    "FUNC_TEST", "AGING", "WP_TEST", "PRECISION", "BALANCE", "ICT",
    "WATER_PROOF", "QC",
}
PRODUCTION_CODES = {
    "LASER", "PLASMA", "SHEAR", "SAW", "PUNCH", "BEND", "ROLL", "DRAW",
    "FORGE", "TURN", "MILL", "DRILL", "GRIND", "BORE", "HOB", "SHAPE",
    "WEDM", "EDM", "CAST", "DIE_CAST", "MOLD", "BLOW", "TIG", "MIG",
    "SPOT", "LASER_WELD", "QUENCH", "TEMPER", "CARBURIZE", "AGE", "PLATE",
    "ANODIZE", "POWDER", "PAINT", "SCREEN", "POLISH", "SMT", "INSERT",
    "WAVE", "SOLDER_MANUAL", "WINDING", "VARNISH", "FLASH", "ASM",
    "PRESS_FIT", "DISPENSE", "PKG", "DEBURR",
}
EXPECTED_TYPES = {
    **dict.fromkeys(PRODUCTION_CODES, "PRODUCTION"),
    **dict.fromkeys(INSPECTION_CODES, "INSPECTION"),
    "CLEAN": "CLEAN",
}
DOCUMENT_FIELDS = (
    "sop_id", "tenant_id", "sop_code", "sop_name", "sop_type",
    "current_version", "status", "file_oss_id", "file_type", "description",
    "approval_by", "approval_time", "approval_remark", "archive_reason",
    "remark", "create_by", "create_dept", "create_time",
)
VERSION_FIELDS = (
    "version_id", "tenant_id", "sop_id", "version_no", "file_oss_id",
    "file_type", "change_log", "published_time", "published_by", "status",
    "remark", "create_by", "create_dept", "create_time",
)
ARCHIVED_DOCUMENT = (
    "INSERT INTO `sop_document` VALUES (2100507000309788673, '000000', "
    "'SOP20260917001', '测试', 'PRODUCTION', '1.0', 'ARCHIVED', "
    "2100506990969073665, 'PDF', '', 1, '2026-09-17 16:48:30', NULL, "
    "NULL, '', 103, 1, '2026-09-17 16:47:49', 1, '2026-09-17 16:47:49', '0');"
)
ARCHIVED_VERSION = (
    "INSERT INTO `sop_version` VALUES (2100507000326565889, '000000', "
    "2100507000309788673, '1.0', 2100506990969073665, 'PDF', NULL, '', "
    "'2026-09-17 16:48:30', 1, 'CURRENT', NULL, 103, 1, "
    "'2026-09-17 16:47:49', 1, '2026-09-17 16:47:49', '0');"
)


def catalog_processes():
    tree = ast.parse((REBUILD_DIR / "catalog.py").read_text(encoding="utf-8"))
    assignment = next(
        node for node in tree.body
        if isinstance(node, ast.Assign)
        and any(isinstance(target, ast.Name) and target.id == "PROCESSES"
                for target in node.targets)
    )
    return ast.literal_eval(assignment.value)


def sop_section(source):
    start = source.index("# 第十一部分：标准作业程序。")
    end = source.index("# ---------------------------------------------------------------- 12. 缺陷代码库", start)
    return source[start:end]


def generator_fields(section, collection, columns):
    tree = ast.parse(section)
    values = next(
        node.args[0].args[0].elts for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and isinstance(node.func.value, ast.Name)
        and node.func.value.id == collection
        and node.func.attr == "append"
    )
    if len(values) != len(columns):
        raise AssertionError(f"Unexpected {collection} field count: {len(values)}")
    return dict(zip(columns, values))


def sql_rows(sql, table):
    schema = re.search(rf"CREATE TABLE `{table}`  \((.*?)\) ENGINE", sql, re.S)
    columns = re.findall(r"^  `([^`]+)`", schema.group(1), re.M)
    rows = []
    for payload in re.findall(rf"^INSERT INTO `{table}` VALUES \((.*)\);$", sql, re.M):
        def python_token(match):
            token = match.group(0)
            if token == "NULL":
                return "None"
            return repr(token[1:-1].replace("''", "'").replace("\\'", "'"))

        values = ast.literal_eval("[" + re.sub(
            r"'(?:\\.|''|[^'])*'|\bNULL\b", python_token, payload
        ) + "]")
        if len(values) != len(columns):
            raise AssertionError(f"Unexpected {table} field count: {len(values)}")
        rows.append(dict(zip(columns, values)))
    return rows


class SopTypeTests(unittest.TestCase):
    def classifier(self):
        self.assertTrue(TYPE_MODULE.is_file(), "Missing pure SOP classifier module")
        spec = importlib.util.spec_from_file_location("sop_types", TYPE_MODULE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.sop_type_for_process

    def test_classifies_all_57_catalog_processes(self):
        processes = catalog_processes()
        codes = [process[0] for process in processes]
        self.assertEqual(len(codes), 57)
        self.assertEqual(len(set(codes)), 57)
        self.assertEqual(set(codes), set(EXPECTED_TYPES))
        classify = self.classifier()
        for code in codes:
            with self.subTest(process_code=code):
                self.assertEqual(classify(code), EXPECTED_TYPES[code])

    def test_unknown_or_invalid_process_codes_fail_closed(self):
        classify = self.classifier()
        for code in ("UNKNOWN", "LASRE", "PROCESS", "", "laser", " QC",
                     "CLEAN ", None, 0, []):
            with self.subTest(process_code=code):
                with self.assertRaises(ValueError):
                    classify(code)

    def test_import_is_side_effect_free(self):
        self.assertTrue(TYPE_MODULE.is_file(), "Missing pure SOP classifier module")
        # Audit the real import in a fresh interpreter; reads are allowed, writes,
        # network access, process launches and filesystem mutations are not.
        code = """
import os
import sys

def reject_side_effects(event, args):
    forbidden = {"subprocess.Popen", "os.system", "os.remove", "os.rename",
                 "os.mkdir", "os.rmdir", "os.link", "os.symlink",
                 "os.truncate", "os.chmod", "os.chdir"}
    if event.startswith("socket.") or event in forbidden:
        raise RuntimeError("Import side effect: " + event)
    if event == "open" and args[2] & (os.O_WRONLY | os.O_RDWR | os.O_CREAT |
                                     os.O_TRUNC | os.O_APPEND):
        raise RuntimeError("Import must not write files")

sys.addaudithook(reject_side_effects)
from script.tools.rebuild.sop_types import sop_type_for_process
assert sop_type_for_process("LASER") == "PRODUCTION"
"""
        result = subprocess.run(
            [sys.executable, "-B", "-c", code], cwd=ROOT,
            capture_output=True, text=True, check=False, timeout=10,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(result.stderr, "")


class GeneratorSopSeedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = GENERATOR.read_text(encoding="utf-8")
        cls.section = sop_section(cls.source)
        cls.document = generator_fields(cls.section, "sop_vals", DOCUMENT_FIELDS)
        cls.version = generator_fields(cls.section, "ver_vals", VERSION_FIELDS)

    def assert_literal(self, fields, name, expected):
        node = fields[name]
        self.assertIsInstance(node, ast.Constant, name)
        self.assertEqual(node.value, expected, name)

    def test_sop_section_imports_shared_classifier(self):
        imports = [node for node in ast.parse(self.section).body
                   if isinstance(node, ast.ImportFrom)]
        self.assertTrue(any(
            node.module == "sop_types"
            and any(alias.name == "sop_type_for_process" for alias in node.names)
            for node in imports
        ), "SOP generation must import the shared classifier")

    def test_sop_document_uses_process_classifier(self):
        self.assertEqual(
            ast.dump(self.document["sop_type"]),
            ast.dump(ast.parse("sop_type_for_process(pcode)", mode="eval").body),
        )

    def test_sop_document_is_draft_without_current_attachment(self):
        self.assert_literal(self.document, "status", "DRAFT")
        for field in ("current_version", "file_oss_id", "file_type"):
            self.assert_literal(self.document, field, None)

    def test_sop_document_has_no_approval_claims(self):
        for field in ("approval_by", "approval_time", "approval_remark"):
            self.assert_literal(self.document, field, None)
        self.assertNotIn("初始发布", self.section)

    def test_sop_version_is_unbound_pending_without_publication(self):
        self.assert_literal(self.version, "status", "PENDING")
        self.assert_literal(self.version, "version_no", "V1.0")
        self.assert_literal(self.version, "file_oss_id", 0)
        for field in ("file_type", "published_time", "published_by"):
            self.assert_literal(self.version, field, None)

    def test_sop_version_explains_unbound_placeholder(self):
        self.assert_literal(self.version, "change_log", PLACEHOLDER_CHANGE_LOG)

    def test_unrelated_erp_process_enums_are_retained(self):
        outside_sop = self.source.replace(self.section, "", 1)
        self.assertEqual(outside_sop.count('"PROCESS"'), 4)
        self.assertIn('def emit_bom(parent_name, children, bom_name, src_default="PROCESS"):',
                      outside_sop)
        self.assertIn('src = "PROCESS" if cmeta["mtype"] in (2, 3) else "PROCUREMENT"',
                      outside_sop)


class MainSqlSopSeedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = MAIN_SQL.read_text(encoding="utf-8")
        cls.documents = sql_rows(cls.sql, "sop_document")
        cls.versions = sql_rows(cls.sql, "sop_version")
        cls.bindings = sql_rows(cls.sql, "sop_process_binding")

    def seeds(self, rows, id_field):
        seeds = {row[id_field]: row for row in rows if row[id_field] in SEED_IDS}
        self.assertEqual(len(seeds), 57)
        self.assertEqual(set(seeds), SEED_IDS)
        for row in seeds.values():
            self.assertEqual(row["tenant_id"], "000000")
        return seeds

    def test_all_57_seed_types_match_bound_catalog_process(self):
        documents = self.seeds(self.documents, "sop_id")
        bindings = self.seeds(self.bindings, "sop_id")
        self.assertEqual({row["process_code"] for row in bindings.values()},
                         {process[0] for process in catalog_processes()})
        for sop_id, document in documents.items():
            code = bindings[sop_id]["process_code"]
            self.assertEqual(document["sop_type"], EXPECTED_TYPES[code], code)
            self.assertEqual(document["sop_name"], bindings[sop_id]["process_name"] + "作业指导书")

    def test_all_57_seed_documents_are_unattached_drafts(self):
        for sop_id, document in self.seeds(self.documents, "sop_id").items():
            self.assertEqual(document["status"], "DRAFT", sop_id)
            for field in ("current_version", "file_oss_id", "file_type"):
                self.assertIsNone(document[field], f"SOP {sop_id}: {field}")

    def test_all_57_seed_documents_have_no_approval_claims(self):
        for sop_id, document in self.seeds(self.documents, "sop_id").items():
            for field in ("approval_by", "approval_time", "approval_remark"):
                self.assertIsNone(document[field], f"SOP {sop_id}: {field}")

    def test_all_57_seed_versions_are_unbound_pending(self):
        versions = self.seeds(self.versions, "version_id")
        self.assertEqual({row["sop_id"] for row in versions.values()}, SEED_IDS)
        for version_id, version in versions.items():
            self.assertEqual(version["version_id"], version["sop_id"])
            self.assertEqual(version["version_no"], "V1.0")
            self.assertEqual(version["status"], "PENDING", version_id)
            self.assertIs(type(version["file_oss_id"]), int)
            self.assertEqual(version["file_oss_id"], 0, version_id)
            for field in ("file_type", "file_hash", "published_time", "published_by"):
                self.assertIsNone(version[field], f"Version {version_id}: {field}")

    def test_all_57_seed_versions_explain_unbound_placeholder(self):
        for version_id, version in self.seeds(self.versions, "version_id").items():
            self.assertEqual(version["change_log"], PLACEHOLDER_CHANGE_LOG, version_id)

    def test_sop_version_file_id_preserves_existing_not_null_schema(self):
        schema = re.search(r"CREATE TABLE `sop_version`  \((.*?)\) ENGINE", self.sql, re.S)
        self.assertIsNotNone(schema)
        self.assertRegex(schema.group(1),
                         r"(?m)^  `file_oss_id` bigint\(\d+\) NOT NULL\b")

    def test_archived_genuine_document_and_version_are_untouched(self):
        self.assertEqual(len(self.documents), 58)
        self.assertEqual(len(self.versions), 58)
        lines = self.sql.splitlines()
        self.assertEqual(lines.count(ARCHIVED_DOCUMENT), 1)
        self.assertEqual(lines.count(ARCHIVED_VERSION), 1)


if __name__ == "__main__":
    unittest.main()
