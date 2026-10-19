"""当前库存转换专用合成census，不是实际业务凭据。"""
from copy import deepcopy
from decimal import Decimal, ROUND_HALF_UP
from fixtures import snapshot, complete_row
from core import digest


def inventory(quantity="3.0000", amount="10.0000", serial=False, batch=False):
    s = snapshot()
    s["conversion_run_states"] = []
    m=s["tables"]["bd_material"][0]
    m.update(is_serial_manage="1" if serial else "0", is_batch_manage="1" if batch else "0", status="1", del_flag="0", base_uom_id="1", tracking_stage="AUDIT")
    old=s["tables"]["wm_material_stock"][0]
    unit=str((Decimal(amount)/Decimal(quantity)).quantize(Decimal("0.000001"),rounding=ROUND_HALF_UP))
    old.update(stock_quantity=quantity,total_cost=amount,unit_cost=unit,reserved_quantity="0.0000",sn_id="101" if serial else None,sn_code="SYNTH-001" if serial else "LEGACY-LOT-CODE",batch_id="301" if batch else None,batch_code="SYNTH-B1" if batch else None,warehouse_id="1",warehouse_code="W1",warehouse_name="SYNTH-W",area_id="2",location_id="3",location_code="L3",vendor_id="4",base_uom_id="1",wo_id=None,frozen_flag="0",tracking_version="1",active_sn_id=None)
    tx=s["tables"]["wm_material_stock_tx"][0]
    tx.update(tx_quantity=quantity,before_quantity="0.0000",after_quantity=quantity,tx_amount=amount,unit_cost=unit,tx_date="2026-10-18 00:00:00",del_flag="0",sn_id=old["sn_id"],sn_code=old["sn_code"],batch_id=old["batch_id"])
    s["tables"]["wm_sn"]=[]
    for i in range(int(Decimal(quantity))):
        s["tables"]["wm_sn"].append(complete_row("wm_sn",dict(tenant_id="T1",sn_id=str(101+i),material_id="10",sn_code=f"SYNTH-{i+1:03d}",status="IN_STOCK" if serial and i==0 else "REGISTERED",stock_id="401" if serial and i==0 else None,batch_id=old["batch_id"],batch_code=old["batch_code"],identity_version="1",del_flag="0",registration_command=f"SYNTH-REGISTER-{i+1}")))
    # 原MES映射与批次不由当前库存转换伪造；此夹具只保留本次ERP库存范围。
    s["tables"]["pro_sn"]=[];s["tables"]["lt_lot"]=[]
    for row in s["tables"]["wm_batch"]:row["del_flag"]="0"
    return s


def conversion_plan(s):
    m=s["tables"]["bd_material"][0]; old=s["tables"]["wm_material_stock"][0]
    scope={k:old[k] for k in ("tenant_id","material_id","warehouse_id","area_id","location_id","batch_id","vendor_id","wo_id","base_uom_id")}
    identities=sorted(s["tables"]["wm_sn"],key=lambda r:int(r["sn_id"]))
    sources=[{"stock_id":old["stock_id"],"before_hash":digest(old),"valuation_group_id":"801","cost_scope":{"method":"MOVING_AVERAGE","source_reference":"synthetic://cost-scope/401","unit_cost":old["unit_cost"],"dimensions":scope},"targets":[{"identity_id":r["sn_id"],"identity_hash":digest(r),"stock_id":str(901+i)} for i,r in enumerate(identities)]}]
    p={"schema_version":1,"converter":"CURRENT_STOCK_TO_SN","run_id":"SYNTH-CONVERT-1","target":deepcopy(s["target"]),"tenant_id":"T1","material_id":"10","snapshot_hash":digest(s),"policy_before_hash":digest(m),"expected_policy_version":m["tracking_version"],"cutover_at":"2026-10-19 00:00:00","sources":sources}
    census={"evidence_id":"SYNTH-CENSUS-1","kind":"PHYSICAL_COUNT","reference":"synthetic://physical-census/1","approved_by":"synthetic-reviewer","tenant_id":"T1","observed_at":p["cutover_at"],"movement_freeze_reference":"synthetic://freeze/1","inventory_state":"AVAILABLE_ONLY","history_boundary":{"through":p["cutover_at"],"coverage":"COMPLETE_AGGREGATE","reference":"synthetic://original-ledger/1","ledger_hash":digest(s["tables"]["wm_material_stock_tx"])}}
    p["census"]=census
    return reseal(p)


def reseal(p):
    from stock_converter import intent
    census=p["census"];census["conversion"]=intent(p)
    census["artifact"]={k:deepcopy(v) for k,v in census.items() if k not in ("evidence_id","artifact_sha256","artifact")}
    census["artifact_sha256"]=digest(census["artifact"])
    return p


def two_scopes():
    s=inventory();old=s["tables"]["wm_material_stock"][0]
    second={**old,"stock_id":"402","stock_quantity":"2.0000","total_cost":"5.0000","unit_cost":"2.500000","location_id":"5","vendor_id":"6","sn_code":"LEGACY-SECOND"}
    s["tables"]["wm_material_stock"].append(second)
    tx=s["tables"]["wm_material_stock_tx"][0]
    s["tables"]["wm_material_stock_tx"].append({**tx,"tx_id":"502","material_stock_id":"402","tx_quantity":"2.0000","after_quantity":"2.0000","tx_amount":"5.0000","unit_cost":"2.500000","location_id":"5","sn_code":"LEGACY-SECOND"})
    for i in (104,105):
        s["tables"]["wm_sn"].append(complete_row("wm_sn",dict(tenant_id="T1",sn_id=str(i),material_id="10",sn_code=f"SYNTH-{i}",status="REGISTERED",identity_version="1",del_flag="0",registration_command=f"SYNTH-REGISTER-{i}")))
    p=conversion_plan(s);targets=p["sources"][0]["targets"]
    p["sources"][0]["targets"]=targets[:3]
    p["sources"].append({"stock_id":"402","before_hash":digest(second),"valuation_group_id":"802","cost_scope":{"method":"MOVING_AVERAGE","source_reference":"synthetic://cost-scope/402","unit_cost":second["unit_cost"],"dimensions":{k:second[k] for k in ("tenant_id","material_id","warehouse_id","area_id","location_id","batch_id","vendor_id","wo_id","base_uom_id")}},"targets":targets[3:]})
    return s,reseal(p)
