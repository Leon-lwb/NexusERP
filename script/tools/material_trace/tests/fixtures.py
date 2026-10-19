"""仅含合成数据；禁止替换为真实租户、序列号或业务备份。"""
from copy import deepcopy

def complete_row(table, values):
    """合成夹具显式包含全部导出列，未构造的合成旧字段保留NULL。"""
    from core import COLUMNS
    row = dict.fromkeys(COLUMNS[table])
    row.update(values)
    return row

def snapshot():
    result = {
        "schema_version": 1,
        "target": {"environment": "synthetic", "erp_database": "fixture_erp", "mes_database": "fixture_mes"},
        "tenant_scope": ["T1"],
        "server_ids": {"erp":"00000000-0000-0000-0000-000000000001","mes":"00000000-0000-0000-0000-000000000002"},
        "captured_at": "2026-10-18 00:00:00",
        "watermarks": {"erp": "fixture-erp-1", "mes": "fixture-mes-1"},
        "complete_tables": ["bd_material", "wm_sn", "wm_batch", "wm_sn_alias", "wm_material_stock", "wm_material_stock_tx", "pro_sn", "lt_lot", "lt_wo_consumption", "lt_sn_consumption", "lt_sn_material_guard", "lt_lot_allocation", "wm_valuation_group", "wm_trace_posting_group", "wm_document_sn", "pro_completion_manifest", "pro_completion_manifest_line", "wm_trace_outbox", "wm_trace_inbox", "mes_trace_outbox", "mes_trace_inbox", "mes_label_template", "mes_label_print_log"],
        "tables": {
            "bd_material": [{"tenant_id":"T1", "material_id":"10", "is_serial_manage":"1", "is_batch_manage":"1", "allow_mixed_lot":"1", "tracking_stage":"AUDIT", "tracking_version":"1"}],
            "wm_sn": [{"tenant_id":"T1", "sn_id":"101", "material_id":"10", "sn_code":"SYNTH-001", "batch_id":"301", "identity_version":"1", "registration_command":None, "status":"IN_STOCK", "del_flag":"0"}],
            "wm_batch": [{"tenant_id":"T1", "batch_id":"301", "material_id":"10", "batch_code":"SYNTH-B1"}],
            "wm_material_stock": [{"tenant_id":"T1", "stock_id":"401", "material_id":"10", "batch_id":"301", "sn_id":"101", "sn_code":"SYNTH-001", "stock_quantity":"1.0000", "reserved_quantity":"0.0000", "unit_cost":"12.345600", "total_cost":"12.3456", "tracking_version":"1", "valuation_group_id":None, "version":"1", "del_flag":"0"}],
            "wm_material_stock_tx": [{"tenant_id":"T1", "tx_id":"501", "material_id":"10", "batch_id":"301", "material_stock_id":"401", "sn_id":"101", "sn_code":"SYNTH-001", "tx_quantity":"1.0000", "tx_flag":"1", "before_quantity":"0.0000", "after_quantity":"1.0000", "unit_cost":"12.345600", "tx_amount":"12.3456", "tracking_version":"1", "posting_group_id":None}],
            "pro_sn": [{"tenant_id":"T1", "sn_id":"201", "identity_id":None, "material_id":"10", "sn_code":"SYNTH-001", "mes_wo_id":"601", "source":"MANUAL", "product_lot_id":"701", "identity_version":"1", "handoff_status":"NONE", "del_flag":"0"}],
            "lt_lot": [{"tenant_id":"T1", "lot_id":"701", "material_id":"10", "lot_code":"SYNTH-B1", "erp_batch_id":None, "allocation_version":"1", "quantity":"1.0000", "consumed_qty":"0.0000"}],
            "mes_label_template": [], "mes_label_print_log": [], "wm_trace_outbox": [], "wm_trace_inbox": [], "mes_trace_outbox": [], "mes_trace_inbox": [], "wm_sn_alias": [], "lt_wo_consumption": [], "lt_sn_consumption": [], "lt_sn_material_guard": [], "lt_lot_allocation": [], "wm_valuation_group": [], "wm_trace_posting_group": [], "wm_document_sn": [], "pro_completion_manifest": [], "pro_completion_manifest_line": []
        },
        "evidence": []
    }
    from core import COLUMNS, READ_ONLY_SOURCE_TABLES
    for table in sorted(READ_ONLY_SOURCE_TABLES):
        if table not in result["tables"]:
            result["complete_tables"].append(table)
            result["tables"][table] = []
    for table, rows in result["tables"].items():
        for row in rows:
            for column in COLUMNS[table]: row.setdefault(column,None)
    return result

def add_evidence(s, table="pro_sn", key=None, changes=None):
    from core import digest
    key = key or {"tenant_id":"T1", "sn_id":"201"}
    changes = changes or {"identity_id":"101", "identity_version":"2"}
    evidence = {"evidence_id":"SYNTH-E1", "kind":"SOURCE_DOCUMENT", "reference":"synthetic://immutable-handoff/1", "artifact_sha256":"a"*64, "approved_by":"synthetic-reviewer", "tenant_id":"T1", "assertions":[{"table":table,"key":deepcopy(key),"changes":deepcopy(changes)}], "source_rows":[{"table":"wm_sn","key":{"tenant_id":"T1","sn_id":"101"},"row_hash":digest(s["tables"]["wm_sn"][0])}]}
    evidence["artifact"]={k:deepcopy(v) for k,v in evidence.items() if k not in ("evidence_id","artifact_sha256")}
    evidence["artifact_sha256"]=digest(evidence["artifact"])
    s["evidence"].append(evidence)
    return evidence

def plan(s, table="pro_sn", key=None, changes=None):
    from core import digest
    key = key or {"tenant_id":"T1", "sn_id":"201"}
    changes = changes or {"identity_id":"101", "identity_version":"2"}
    add_evidence(s, table, key, changes)
    before = next(r for r in s["tables"][table] if all(r.get(k)==v for k,v in key.items()))
    return {"schema_version":1,"run_id":"SYNTH-REPAIR-1","target":deepcopy(s["target"]),"snapshot_hash":digest(s),"items":[{"item_id":"1","datasource":"mes" if table.startswith(("pro_","lt_","mes_")) else "erp","table":table,"key":key,"before_hash":digest(before),"changes":changes,"evidence_id":"SYNTH-E1"}]}
