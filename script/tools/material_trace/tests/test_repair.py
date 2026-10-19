import copy
import importlib
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixtures import snapshot, plan
try:
    repair=importlib.import_module("repair")
    from core import digest
except ModuleNotFoundError:
    repair=None

class RepairTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(repair,"需要实现证据修复与回退")
        self.s=snapshot(); self.p=plan(self.s)
    def test_apply_requires_explicit_source_document(self):
        after,j=repair.apply(self.s,self.p)
        self.assertEqual("101",after["tables"]["pro_sn"][0]["identity_id"])
        self.assertIsNone(self.s["tables"]["pro_sn"][0]["identity_id"])
        self.assertEqual("APPLIED",j["status"])
        self.assertEqual(digest(self.s["tables"]["pro_sn"][0]),j["items"][0]["before_hash"])
    def test_apply_is_idempotent(self):
        after,j=repair.apply(self.s,self.p)
        repeated,j2=repair.apply(after,self.p,j)
        self.assertEqual(after,repeated);self.assertEqual(j,j2)
    def test_roundtrip_restores_exact_snapshot(self):
        after,j=repair.apply(self.s,self.p)
        before,j2=repair.revert(after,j)
        self.assertEqual(self.s,before);self.assertEqual("REVERTED",j2["status"])
        self.assertEqual(2,j2["version"])
    def test_revert_is_idempotent(self):
        after,j=repair.apply(self.s,self.p);before,j2=repair.revert(after,j)
        self.assertEqual((before,j2),repair.revert(before,j2))
    def test_before_hash_cas(self):
        self.p["items"][0]["before_hash"]="b"*64
        with self.assertRaisesRegex(ValueError,"CAS"):repair.apply(self.s,self.p)
    def test_global_baseline_cas(self):
        self.s["tables"]["wm_material_stock"][0]["stock_quantity"]="2.0000"
        with self.assertRaisesRegex(ValueError,"快照|CAS"):repair.apply(self.s,self.p)
    def test_no_same_code_evidence(self):
        self.s["evidence"][0]["kind"]="SAME_CODE"
        self.p["snapshot_hash"]=digest(self.s)
        with self.assertRaisesRegex(ValueError,"证据"):repair.apply(self.s,self.p)
    def test_source_row_hash_is_verified(self):
        self.s["evidence"][0]["source_rows"][0]["row_hash"]="c"*64
        self.p["snapshot_hash"]=digest(self.s)
        with self.assertRaisesRegex(ValueError,"证据"):repair.apply(self.s,self.p)
    def test_cross_tenant_evidence_is_rejected(self):
        self.s["evidence"][0]["tenant_id"]="T2"
        self.p["snapshot_hash"]=digest(self.s)
        with self.assertRaisesRegex(ValueError,"租户"):repair.apply(self.s,self.p)
    def test_amount_and_quantity_columns_are_not_writable(self):
        self.p["items"][0]["changes"]["good_qty"]="2.0000"
        with self.assertRaisesRegex(ValueError,"白名单"):repair.apply(self.s,self.p)
    def test_unknown_table_rejected(self):
        self.p["items"][0]["table"]="sys_user"
        with self.assertRaisesRegex(ValueError,"白名单"):repair.apply(self.s,self.p)
    def test_identity_cannot_be_invented(self):
        self.p["items"][0]["changes"]["identity_id"]="999"
        self.s["evidence"][0]["assertions"][0]["changes"]["identity_id"]="999"
        self.p["snapshot_hash"]=digest(self.s)
        with self.assertRaisesRegex(ValueError,"身份"):repair.apply(self.s,self.p)
    def test_new_business_requires_compensation(self):
        after,j=repair.apply(self.s,self.p)
        after["tables"]["wm_material_stock_tx"].append({**after["tables"]["wm_material_stock_tx"][0],"tx_id":"502"})
        with self.assertRaisesRegex(ValueError,"COMPENSATION_REQUIRED"):repair.revert(after,j)
    def test_tampered_journal_is_rejected(self):
        after,j=repair.apply(self.s,self.p);j["items"][0]["before"]["sn_code"]="FORGED"
        with self.assertRaisesRegex(ValueError,"摘要|日志"):repair.revert(after,j)
    def test_duplicate_target_rejected_atomically(self):
        self.p["items"].append({**copy.deepcopy(self.p["items"][0]),"item_id":"2"})
        with self.assertRaisesRegex(ValueError,"重复"):repair.apply(self.s,self.p)
        self.assertIsNone(self.s["tables"]["pro_sn"][0]["identity_id"])
    def test_no_sql_identifiers_in_keys(self):
        self.p["items"][0]["key"]["tenant_id OR 1=1"]="T1"
        with self.assertRaisesRegex(ValueError,"主键|白名单"):repair.apply(self.s,self.p)
    def test_wrong_datasource_rejected(self):
        self.p["items"][0]["datasource"]="erp"
        with self.assertRaisesRegex(ValueError,"数据源"):repair.apply(self.s,self.p)
    def test_snapshot_source_completeness_required(self):
        self.s["complete_tables"].remove("wm_sn");self.p["snapshot_hash"]=digest(self.s)
        with self.assertRaisesRegex(ValueError,"完整"):repair.apply(self.s,self.p)
    def test_sql_emission_requires_target_and_backup(self):
        from sql_writer import emit
        after,j=repair.apply(self.s,self.p)
        for kwargs in ({},{"target":"mes:fixture_mes"},{"target":"erp:fixture_erp","backup_reference":"synthetic-backup","allow_db_writes":True}):
            with self.assertRaises(ValueError):emit(self.s,j,**kwargs)
    def test_sql_has_binary_cas_journal_and_no_execution(self):
        from sql_writer import emit
        after,j=repair.apply(self.s,self.p)
        sql=emit(self.s,j,target="mes:fixture_mes",backup_reference="synthetic-backup",allow_db_writes=True)
        for term in ("FOR UPDATE","SHA2(","before_hash","mes_trace_repair_run","mes_trace_repair_item","ROLLBACK","SIGNAL SQLSTATE","fixture_mes","synthetic-backup"):
            self.assertIn(term,sql)
        self.assertNotIn("CONNECT",sql.upper())

    def test_partial_row_cannot_emit_database_cas(self):
        from sql_writer import emit
        self.s=snapshot()
        self.s["tables"]["pro_sn"][0].pop("wo_no")
        self.p=plan(self.s)
        after,j=repair.apply(self.s,self.p)
        with self.assertRaisesRegex(ValueError,"全部列|投影"):
            emit(self.s,j,target="mes:fixture_mes",backup_reference="synthetic-backup",allow_db_writes=True)
    def test_sql_signal_uses_simple_variable_not_function(self):
        from sql_writer import emit
        after,j=repair.apply(self.s,self.p)
        sql=emit(self.s,j,target="mes:fixture_mes",backup_reference="synthetic-backup",allow_db_writes=True)
        self.assertNotRegex(sql,r"SIGNAL[^;]*MESSAGE_TEXT\s*=\s*CONVERT\(")
        self.assertIn("MESSAGE_TEXT=v_error",sql)
    def test_frozen_evidence_payload_hash_is_checked(self):
        self.s["evidence"][0]["artifact_sha256"]="0"*64
        self.p["snapshot_hash"]=digest(self.s)
        with self.assertRaisesRegex(ValueError,"证据|凭据"):
            repair.apply(self.s,self.p)
    def test_metadata_apply_preserves_every_quantity_cost_and_original_code(self):
        after,j=repair.apply(self.s,self.p)
        for table in self.s["tables"]:
            for old,new in zip(self.s["tables"][table],after["tables"][table]):
                for column in old:
                    if any(word in column for word in ("qty","quantity","amount","cost","sn_code","status")):
                        self.assertEqual(old[column],new[column])
    def test_registration_command_cannot_be_fabricated(self):
        self.s=snapshot();self.p=plan(self.s,table="wm_sn",key={"tenant_id":"T1","sn_id":"101"},changes={"registration_command":"FORGED","identity_version":"2"})
        with self.assertRaisesRegex(ValueError,"registration_command"):
            repair.apply(self.s,self.p)

    def test_forged_resealed_journal_cannot_bypass_plan(self):
        after,j=repair.apply(self.s,self.p)
        j["items"][0]["before"]["identity_version"]="17"
        j["items"][0]["before_hash"]=digest(j["items"][0]["before"])
        j["journal_hash"]=digest({k:v for k,v in j.items() if k!="journal_hash"})
        with self.assertRaises(ValueError):repair.revert(after,j)

    def test_new_label_attempt_requires_compensation_before_revert(self):
        after,j=repair.apply(self.s,self.p)
        after["tables"]["mes_label_print_log"].append({"tenant_id":"T1","log_id":"1001","sn_id":"201","sn_code":"SYNTH-001","result":"SENT","snapshot_mode":"LEGACY_DIRECT"})
        with self.assertRaisesRegex(ValueError,"COMPENSATION_REQUIRED"):
            repair.revert(after,j)

    def test_existing_bad_valuation_group_cannot_be_attached(self):
        self.s=snapshot()
        self.s["tables"]["wm_valuation_group"]=[{"tenant_id":"T1","valuation_group_id":"88","material_id":"10","warehouse_id":"1","quantity":"1.0000","amount":"99.0000"}]
        self.p=plan(self.s,table="wm_material_stock",key={"tenant_id":"T1","stock_id":"401"},changes={"valuation_group_id":"88","version":"2"})
        e=self.s["evidence"][0]
        e["source_rows"].append({"table":"wm_valuation_group","key":{"tenant_id":"T1","valuation_group_id":"88"},"row_hash":digest(self.s["tables"]["wm_valuation_group"][0])})
        e["artifact"]={k:copy.deepcopy(v) for k,v in e.items() if k not in ("evidence_id","artifact_sha256","artifact")};e["artifact_sha256"]=digest(e["artifact"])
        self.p["snapshot_hash"]=digest(self.s)
        with self.assertRaisesRegex(ValueError,"冲突|守恒"):
            repair.apply(self.s,self.p)
