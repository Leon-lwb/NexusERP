import copy
import importlib
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixtures import snapshot, complete_row
try:
    audit = importlib.import_module("audit").audit
except ModuleNotFoundError:
    audit = None

class AuditTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(audit, "需要实现实际离线审计")
        self.s = snapshot()
    def codes(self):
        return {f["code"] for f in audit(self.s)["findings"]}
    def test_legacy_ambiguity_is_never_auto_linked(self):
        result=audit(self.s)
        self.assertIn("UNMAPPED_MES_IDENTITY",self.codes())
        self.assertIsNone(self.s["tables"]["pro_sn"][0]["identity_id"])
        self.assertFalse(result["strict_ready"][0]["ready"])
    def test_same_code_other_material_is_not_evidence(self):
        self.s["tables"]["wm_sn"][0]["material_id"]="11"
        self.assertIn("UNMAPPED_MES_IDENTITY",self.codes())
    def test_legacy_duplicate_stock_is_reported_without_upgrading(self):
        r=copy.deepcopy(self.s["tables"]["wm_material_stock"][0]);r["stock_id"]="402"
        self.s["tables"]["wm_material_stock"].append(r)
        self.assertIn("DUPLICATE_ACTIVE_IDENTITY",self.codes())
    def test_v2_quantity_must_be_one(self):
        self.s["tables"]["wm_material_stock"][0].update(tracking_version="2",stock_quantity="2.0000")
        self.assertIn("INVALID_SERIAL_QUANTITY",self.codes())
    def test_null_or_cross_tenant_identity_is_reported(self):
        self.s["tables"]["wm_material_stock"][0]["tenant_id"]="T2"
        self.assertIn("MISSING_ERP_IDENTITY",self.codes())
    def test_transaction_quantity_equation(self):
        self.s["tables"]["wm_material_stock_tx"][0]["after_quantity"]="2.0000"
        self.assertIn("TX_QUANTITY_MISMATCH",self.codes())
    def test_amount_uses_decimal_no_float_rounding(self):
        self.s["tables"]["wm_material_stock"][0].update(total_cost="0.3000")
        self.s["tables"]["wm_material_stock_tx"][0]["tx_amount"]="0.3000"
        report=audit(self.s)
        self.assertEqual("0.3000",report["totals"]["stock_amount"])
        self.assertEqual("0.3000",report["totals"]["tx_amount"])
    def test_opening_balance_is_not_assumed_zero(self):
        self.s["tables"]["wm_material_stock"].append({**self.s["tables"]["wm_material_stock"][0],"stock_id":"402","sn_id":None,"stock_quantity":"3.0000"})
        self.assertIn("QUANTITY_RECONCILIATION_MISMATCH",self.codes())
    def test_outstanding_component_consumption_is_reported(self):
        self.s["tables"]["lt_wo_consumption"]=[complete_row("lt_wo_consumption", {"tenant_id":"T1","consumption_id":"801","consumed_qty":"2.0000","lot_id":"701","material_id":"10"})]
        self.assertIn("UNALLOCATED_COMPONENT_CONSUMPTION",self.codes())
    def test_reverse_requires_original_same_identity(self):
        self.s["tables"]["lt_sn_consumption"]=[{"tenant_id":"T1","entry_id":"901","command_id":"C1","line_no":"1","identity_id":"101","component_material_id":"10","component_lot_id":"701","quantity":"1.0000","wo_consumption_id":"801","entry_type":"REVERSE","original_entry_id":"999"}]
        self.assertIn("INVALID_CONSUMPTION_REVERSAL",self.codes())
    def test_non_subcontract_null_workorder_is_invalid(self):
        self.s["tables"]["pro_sn"][0]["mes_wo_id"]=None
        self.assertIn("MISSING_MES_WORK_ORDER",self.codes())
    def test_incomplete_export_never_claims_ready(self):
        self.s["complete_tables"].remove("wm_material_stock_tx")
        self.assertIn("INCOMPLETE_EXPORT",self.codes())
        self.assertFalse(audit(self.s)["complete"])
    def test_valuation_group_is_reconciled(self):
        self.s["tables"]["wm_valuation_group"]=[{"tenant_id":"T1","valuation_group_id":"88","quantity":"1.0000","amount":"99.0000"}]
        self.s["tables"]["wm_material_stock"][0]["valuation_group_id"]="88"
        self.assertIn("VALUATION_GROUP_MISMATCH",self.codes())
    def test_manifest_line_count_and_quantity(self):
        self.s["tables"]["pro_completion_manifest"]=[complete_row("pro_completion_manifest",{"tenant_id":"T1","manifest_id":"99","line_count":"1","good_qty":"1.0000","scrap_qty":"0.0000"})]
        self.assertIn("MANIFEST_RECONCILIATION_MISMATCH",self.codes())
    def test_float_and_nan_are_rejected(self):
        self.s["tables"]["wm_material_stock"][0]["stock_quantity"]=1.1
        with self.assertRaises(ValueError): audit(self.s)

    def test_component_source_identity_and_lot_are_required(self):
        self.s["tables"]["lt_sn_consumption"]=[{"tenant_id":"T1","entry_id":"901","command_id":"C1","line_no":"1","identity_id":"999","local_sn_id":"998","component_material_id":"10","component_lot_id":"997","quantity":"1.0000","wo_consumption_id":"996","entry_type":"CONSUME","original_entry_id":None}]
        self.assertIn("INVALID_COMPONENT_SOURCE",self.codes())
    def test_transaction_last_balance_matches_stock(self):
        self.s["tables"]["wm_material_stock_tx"][0].update(before_quantity="1.0000",after_quantity="2.0000")
        self.assertIn("STOCK_TX_END_BALANCE_MISMATCH",self.codes())
    def test_alias_ambiguity_is_classified_without_guessing(self):
        self.s["tables"]["wm_sn_alias"]=[{"tenant_id":"T1","alias_id":"1","identity_id":"101","source_type":"SUPPLIER","source_id":"1","material_id":"10","alias_code":"SAME","command_id":"A1"},{"tenant_id":"T1","alias_id":"2","identity_id":"102","source_type":"SUPPLIER","source_id":"1","material_id":"10","alias_code":"SAME","command_id":"A2"}]
        self.s["tables"]["wm_sn"].append({**self.s["tables"]["wm_sn"][0],"sn_id":"102","sn_code":"SYNTH-002"})
        self.assertIn("AMBIGUOUS_EXTERNAL_ALIAS",self.codes())
        self.assertEqual("101",self.s["tables"]["wm_sn_alias"][0]["identity_id"])
    def test_mixed_lot_guard_blocks_two_positive_lots(self):
        self.s["tables"]["lt_sn_material_guard"]=[{"tenant_id":"T1","identity_id":"101","component_material_id":"10","selected_lot_id":"701","allow_mixed_lot":"0","policy_version":"1","version":"1"}]
        for i,lot in enumerate(("701","702")):
            self.s["tables"]["lt_sn_consumption"].append({"tenant_id":"T1","entry_id":str(900+i),"command_id":"C1","line_no":str(i+1),"identity_id":"101","local_sn_id":"201","component_material_id":"10","component_lot_id":lot,"quantity":"1.0000","wo_consumption_id":"801","entry_type":"CONSUME","original_entry_id":None})
        self.assertIn("MIXED_LOT_POLICY_VIOLATION",self.codes())
    def test_raw_event_hash_is_checked_without_reserialization(self):
        self.s["complete_tables"].append("wm_trace_outbox")
        self.s["tables"]["wm_trace_outbox"]=[{"event_id":"synthetic-event-1","tenant_id":"T1","source_module":"ERP","aggregate_type":"SN","aggregate_id":"101","aggregate_version":"1","event_type":"REGISTERED","schema_version":"1","payload":"{ \"id\": \"101\" }","content_hash":"0"*64,"status":"PENDING","attempts":"0","next_attempt_at":"2026-10-18 00:00:00","lease_owner":None,"lease_until":None,"last_error":None,"result_payload":None,"created_at":"2026-10-18 00:00:00","updated_at":"2026-10-18 00:00:00","applied_at":None}]
        self.assertIn("EVENT_CONTENT_HASH_MISMATCH",self.codes())

    def test_label_old_success_is_preserved_without_inventing_snapshot(self):
        self.s["tables"]["mes_label_print_log"]=[{"tenant_id":"T1","log_id":"1001","sn_id":"201","sn_code":"SYNTH-001","result":"SUCCESS","snapshot_mode":None}]
        report=audit(self.s)
        self.assertIn("LEGACY_LABEL_WITHOUT_SNAPSHOT",self.codes())
        self.assertEqual("SUCCESS",self.s["tables"]["mes_label_print_log"][0]["result"])
    def test_frozen_label_hash_is_verified(self):
        self.s["tables"]["mes_label_print_log"]=[{"tenant_id":"T1","log_id":"1001","sn_id":"201","sn_code":"SYNTH-001","result":"SENT","snapshot_mode":"FROZEN","template_snapshot":"{}","content_hash":"0"*64,"rendered_payload":"SYNTHETIC","payload_charset":"UTF-8","payload_hash":"0"*64}]
        self.assertIn("LABEL_CONTENT_HASH_MISMATCH",self.codes())
        self.assertIn("LABEL_PAYLOAD_HASH_MISMATCH",self.codes())

    def test_unrelated_evidence_cannot_be_used_to_balance_opening(self):
        from fixtures import add_evidence
        add_evidence(self.s)
        self.s["tables"]["wm_material_stock"][0]["stock_quantity"]="2.0000"
        self.s["opening_balances"]=[{"tenant_id":"T1","material_id":"10","quantity":"1.0000","amount":"0.0000","evidence_id":"SYNTH-E1"}]
        self.assertIn("UNVERIFIED_OPENING_BALANCE",self.codes())
        self.assertIn("QUANTITY_RECONCILIATION_MISMATCH",self.codes())

    def test_zero_quantity_cost_residue_is_explicit_and_blocks_strict(self):
        self.s["tables"]["wm_material_stock"][0].update(stock_quantity="0.0000",total_cost="1.0000")
        self.assertIn("ZERO_QUANTITY_COST_RESIDUE",self.codes())
        self.assertFalse(audit(self.s)["strict_ready"][0]["ready"])

    def test_in_stock_identity_without_positive_stock_is_explicit(self):
        self.s["tables"]["wm_material_stock"][0]["stock_quantity"]="0.0000"
        self.s["tables"]["wm_sn"][0]["status"]="IN_STOCK"
        self.assertIn("ORPHAN_IN_STOCK_IDENTITY",self.codes())

    def test_reverse_pool_checks_include_orphan_nonzero_and_allow_zero_zero(self):
        self.s["tables"]["wm_valuation_group"]=[complete_row("wm_valuation_group",dict(tenant_id="T1",valuation_group_id="800",material_id="10",quantity="1.0000",amount="1.0000"))]
        self.assertIn("VALUATION_GROUP_MISMATCH",self.codes())
        self.s["tables"]["wm_valuation_group"][0].update(quantity="0.0000",amount="0.0000")
        self.assertNotIn("VALUATION_GROUP_MISMATCH",self.codes())

    def test_legacy_untyped_barcode_is_a_gap_and_never_guessed_from_stock_pointer(self):
        self.s["tables"]["wm_barcode"]=[complete_row("wm_barcode",dict(tenant_id="T1",barcode_id="900",material_id="10",stock_id="401",primary_object_type=None))]
        self.assertIn("LEGACY_BARCODE_OBJECT_UNKNOWN",self.codes())
        self.assertIsNone(self.s["tables"]["wm_barcode"][0]["primary_object_type"])
