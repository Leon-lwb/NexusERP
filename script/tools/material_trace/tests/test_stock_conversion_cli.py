"""实际CLI闭环，只在测试目录创建合成JSON和待审SQL。"""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from conversion_fixtures import inventory, conversion_plan
from core import load

TOOL = Path(__file__).resolve().parents[1] / "cli.py"


class StockConversionCliTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path(__file__).parent)
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        self.s = inventory(batch=True)
        self.p = conversion_plan(self.s)
        self.save("before.json", self.s)
        self.save("plan.json", self.p)

    def save(self, name, value):
        (self.work / name).write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def run_tool(self, *args):
        return subprocess.run(
            [sys.executable, "-B", str(TOOL), *args],
            cwd=self.work,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )

    def apply(self):
        r = self.run_tool(
            "convert-apply",
            "--snapshot",
            "before.json",
            "--plan",
            "plan.json",
            "--output",
            "after.json",
            "--journal-output",
            "journal.json",
        )
        self.assertEqual(0, r.returncode, r.stderr)

    def test_check_and_apply_use_real_cli_and_preserve_original_transactions(self):
        r = self.run_tool(
            "convert-check",
            "--snapshot",
            "before.json",
            "--plan",
            "plan.json",
            "--output",
            "check.json",
        )
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertTrue(load(self.work / "check.json")["ready"])
        self.apply()
        after = load(self.work / "after.json")
        self.assertEqual(
            self.s["tables"]["wm_material_stock_tx"], after["tables"]["wm_material_stock_tx"]
        )
        self.assertEqual(4, len(after["tables"]["wm_material_stock"]))

    def test_safe_apply_revert_sql_and_json_keep_original_audit(self):
        self.apply()
        for mode, file in (("apply", "before.json"), ("revert", "after.json")):
            r = self.run_tool(
                "convert-emit-sql",
                "--mode",
                mode,
                "--snapshot",
                file,
                "--journal",
                "journal.json",
                "--target",
                "erp:fixture_erp",
                "--backup-reference",
                "SYNTH-BACKUP",
                "--allow-db-writes",
                "--output",
                mode + ".sql",
            )
            self.assertEqual(0, r.returncode, r.stderr)
            self.assertIn(
                "wm_trace_stock_conversion_run",
                (self.work / (mode + ".sql")).read_text(encoding="utf-8"),
            )
        r = self.run_tool(
            "convert-revert",
            "--snapshot",
            "after.json",
            "--journal",
            "journal.json",
            "--output",
            "restored.json",
            "--journal-output",
            "reverted.json",
            "--compensation-output",
            "compensation.json",
        )
        self.assertEqual(0, r.returncode, r.stderr)
        restored = load(self.work / "restored.json")
        self.assertEqual(self.s["tables"], restored["tables"])
        self.assertEqual("REVERTED", restored["conversion_run_states"][0]["status"])

    def test_new_print_task_emits_compensation_only_and_no_restore_or_sql(self):
        self.apply()
        after = load(self.work / "after.json")
        after["tables"]["mes_label_print_task"].append({"tenant_id": "T1", "task_id": "88"})
        self.save("changed.json", after)
        r = self.run_tool(
            "convert-revert",
            "--snapshot",
            "changed.json",
            "--journal",
            "journal.json",
            "--output",
            "restored.json",
            "--journal-output",
            "reverted.json",
            "--compensation-output",
            "compensation.json",
        )
        self.assertEqual(3, r.returncode, r.stderr)
        self.assertTrue(load(self.work / "compensation.json")["compensation_tasks"])
        self.assertFalse((self.work / "restored.json").exists())
        self.assertFalse((self.work / "reverted.json").exists())
        r = self.run_tool(
            "convert-emit-sql",
            "--mode",
            "revert",
            "--snapshot",
            "changed.json",
            "--journal",
            "journal.json",
            "--target",
            "erp:fixture_erp",
            "--backup-reference",
            "SYNTH-BACKUP",
            "--allow-db-writes",
            "--output",
            "unsafe.sql",
            "--compensation-output",
            "sql-compensation.json",
        )
        self.assertEqual(3, r.returncode, r.stderr)
        self.assertFalse((self.work / "unsafe.sql").exists())
        self.assertEqual(
            "COMPENSATION_REQUIRED", load(self.work / "sql-compensation.json")["status"]
        )

    def test_reserved_stock_outputs_difference_tasks_and_cannot_apply(self):
        self.s["tables"]["wm_material_stock"][0]["reserved_quantity"] = "1.0000"
        self.p = conversion_plan(self.s)
        self.save("before.json", self.s)
        self.save("plan.json", self.p)
        r = self.run_tool(
            "convert-check",
            "--snapshot",
            "before.json",
            "--plan",
            "plan.json",
            "--output",
            "difference.json",
        )
        self.assertEqual(3, r.returncode, r.stderr)
        self.assertTrue(load(self.work / "difference.json")["tasks"])
        r = self.run_tool(
            "convert-apply",
            "--snapshot",
            "before.json",
            "--plan",
            "plan.json",
            "--output",
            "after.json",
            "--journal-output",
            "journal.json",
        )
        self.assertNotEqual(0, r.returncode)
        self.assertFalse((self.work / "after.json").exists())

    def test_existing_output_and_input_files_are_never_overwritten(self):
        original = (self.work / "before.json").read_bytes()
        r = self.run_tool(
            "convert-apply",
            "--snapshot",
            "before.json",
            "--plan",
            "plan.json",
            "--output",
            "before.json",
            "--journal-output",
            "journal.json",
        )
        self.assertNotEqual(0, r.returncode)
        self.assertEqual(original, (self.work / "before.json").read_bytes())
        self.assertFalse((self.work / "journal.json").exists())

    def test_review_artifacts_match_current_converter_and_guarded_sql(self):
        import stock_converter, stock_conversion_sql

        root = TOOL.parent / "examples" / "stock-conversion"
        before = load(root / "before.json")
        plan = load(root / "plan.json")
        after, journal = stock_converter.apply(before, plan)
        self.assertEqual(after, load(root / "after.json"))
        self.assertEqual(journal, load(root / "journal.json"))
        for mode, baseline in (("apply", before), ("revert", after)):
            expected = stock_conversion_sql.emit(
                baseline,
                journal,
                target="erp:fixture_erp",
                backup_reference="SYNTHETIC-BACKUP-ONLY",
                allow_db_writes=True,
                mode=mode,
            )
            self.assertEqual(expected, (root / (mode + ".sql")).read_text(encoding="utf-8"))
        self.assertEqual("COMPENSATION_REQUIRED", load(root / "compensation.json")["status"])
