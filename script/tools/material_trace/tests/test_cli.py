import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from fixtures import snapshot,plan,complete_row
from core import digest,load
TOOL=Path(__file__).resolve().parents[1]/"cli.py"
class CliTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TOOL.exists(),"需要实际可运行的离线CLI")
        self.temp=tempfile.TemporaryDirectory(dir=Path(__file__).parent)
        self.addCleanup(self.temp.cleanup)
        self.work=Path(self.temp.name)
        self.s=snapshot();self.p=plan(self.s)
        (self.work/"snapshot.json").write_text(json.dumps(self.s),encoding="utf-8")
        (self.work/"plan.json").write_text(json.dumps(self.p),encoding="utf-8")
    def run_tool(self,*args):
        return subprocess.run([sys.executable,"-B",str(TOOL),*map(str,args)],cwd=self.work,capture_output=True,text=True,encoding="utf-8")
    def test_default_is_help_without_connection(self):
        r=self.run_tool();self.assertEqual(0,r.returncode,r.stderr);self.assertIn("audit",r.stdout)
    def test_audit_is_read_only(self):
        before=(self.work/"snapshot.json").read_bytes()
        r=self.run_tool("audit","--snapshot","snapshot.json","--output","audit.json")
        self.assertEqual(0,r.returncode,r.stderr)
        self.assertEqual(before,(self.work/"snapshot.json").read_bytes())
        self.assertEqual(digest(self.s),load(self.work/"audit.json")["snapshot_hash"])
    def test_cli_offline_apply_revert_roundtrip(self):
        r=self.run_tool("apply","--snapshot","snapshot.json","--plan","plan.json","--output","after.json","--journal-output","journal.json")
        self.assertEqual(0,r.returncode,r.stderr)
        r=self.run_tool("revert","--snapshot","after.json","--journal","journal.json","--output","restored.json","--journal-output","reverted.json")
        self.assertEqual(0,r.returncode,r.stderr)
        self.assertEqual(self.s,load(self.work/"restored.json"))
    def test_existing_output_is_not_overwritten(self):
        r=self.run_tool("audit","--snapshot","snapshot.json","--output","snapshot.json")
        self.assertNotEqual(0,r.returncode);self.assertEqual(self.s,load(self.work/"snapshot.json"))
    def test_emit_without_write_flags_creates_nothing(self):
        self.run_tool("apply","--snapshot","snapshot.json","--plan","plan.json","--output","after.json","--journal-output","journal.json")
        r=self.run_tool("emit-sql","--snapshot","snapshot.json","--journal","journal.json","--output","apply.sql")
        self.assertNotEqual(0,r.returncode);self.assertFalse((self.work/"apply.sql").exists())
    def test_revert_sql_uses_new_business_guard(self):
        self.run_tool("apply","--snapshot","snapshot.json","--plan","plan.json","--output","after.json","--journal-output","journal.json")
        r=self.run_tool("emit-sql","--mode","revert","--snapshot","after.json","--journal","journal.json","--target","mes:fixture_mes","--backup-reference","synthetic-backup","--allow-db-writes","--output","revert.sql")
        self.assertEqual(0,r.returncode,r.stderr)
        sql=(self.work/"revert.sql").read_text(encoding="utf-8")
        self.assertIn("@@server_uuid",sql);self.assertIn("status='REVERTED'",sql)
    def test_json_duplicate_and_nan_rejected(self):
        for raw in ('{"schema_version":1,"schema_version":1}','{"quantity":NaN}','{"quantity":1.2}'):
            (self.work/"bad.json").write_text(raw,encoding="utf-8")
            with self.assertRaises(ValueError):load(self.work/"bad.json")
    def test_unknown_write_column_refused_before_creating_outputs(self):
        self.p["items"][0]["changes"]["good_qty"]="1.0000"
        (self.work/"plan.json").write_text(json.dumps(self.p),encoding="utf-8")
        r=self.run_tool("apply","--snapshot","snapshot.json","--plan","plan.json","--output","after.json","--journal-output","journal.json")
        self.assertNotEqual(0,r.returncode);self.assertFalse((self.work/"after.json").exists());self.assertFalse((self.work/"journal.json").exists())

    def test_new_durable_task_blocks_cli_revert_and_sql_without_creating_outputs(self):
        r=self.run_tool("apply","--snapshot","snapshot.json","--plan","plan.json","--output","after.json","--journal-output","journal.json")
        self.assertEqual(0,r.returncode,r.stderr)
        changed=load(self.work/"after.json")
        changed["tables"]["mes_label_print_task"].append(complete_row("mes_label_print_task",{
            "tenant_id":"T1","task_id":"1001","sn_id":"201","template_id":"1","template_revision":"1",
            "task_key":"a"*64,"request_fingerprint":"b"*64,"print_source":"AUTO","status":"PENDING",
            "attempt_count":"0","frozen_record":"{}","record_hash":digest({}),
            "created_at":"2026-10-18 00:00:00","updated_at":"2026-10-18 00:00:00"
        }))
        (self.work/"changed.json").write_text(json.dumps(changed),encoding="utf-8")
        r=self.run_tool("revert","--snapshot","changed.json","--journal","journal.json","--output","restored.json","--journal-output","reverted.json")
        self.assertEqual(2,r.returncode,r.stderr);self.assertIn("COMPENSATION_REQUIRED",r.stderr)
        self.assertFalse((self.work/"restored.json").exists());self.assertFalse((self.work/"reverted.json").exists())
        r=self.run_tool("emit-sql","--mode","revert","--snapshot","changed.json","--journal","journal.json","--target","mes:fixture_mes","--backup-reference","synthetic-backup","--allow-db-writes","--output","revert.sql")
        self.assertEqual(2,r.returncode,r.stderr);self.assertIn("COMPENSATION_REQUIRED",r.stderr)
        self.assertFalse((self.work/"revert.sql").exists())
