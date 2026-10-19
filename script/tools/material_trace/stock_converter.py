"""有physical census的当前库存转换。独立于五表repair；零数据库/网络依赖。"""
from copy import deepcopy
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
import re
from core import (COLUMNS, KEYS, REQUIRED_TABLES, canonical, digest, find, number, row_key,
                  validate_artifact, validate_snapshot, validate_target, require_hash)
from repair import _coverage

KIND = "CURRENT_STOCK_TO_SN"
DIMENSIONS = ("tenant_id", "material_id", "warehouse_id", "area_id", "location_id", "batch_id", "vendor_id", "wo_id", "base_uom_id")
INTENT = ("run_id", "target", "snapshot_hash", "tenant_id", "material_id", "cutover_at", "expected_policy_version", "policy_before_hash", "sources")
ZERO = Decimal("0")
STATE_FIELDS = {"run_id","tenant_id","material_id","status","version","plan_hash","bridge_hash"}


class ConversionBlocked(ValueError):
    def __init__(self, code, detail, key=None):
        super().__init__(code + "：" + detail)
        self.task = {"code": code, "detail": detail, "key": key, "action": "REVIEW_ORIGINAL_DOCUMENT_BEFORE_CONVERSION"}


class CompensationRequired(ValueError):
    def __init__(self, report):
        super().__init__("COMPENSATION_REQUIRED：有新业务或完整基线变化，禁止直接恢复")
        self.report = report


def need(valid, code, detail, key=None):
    if not valid: raise ConversionBlocked(code, detail, key)


def intent(plan):
    return {name: deepcopy(plan[name]) for name in INTENT}


def _nonblank(value):
    return isinstance(value,str) and bool(value.strip())


def _census(plan):
    evidence=validate_artifact(plan["census"])
    need(evidence["kind"] == "PHYSICAL_COUNT" and evidence["tenant_id"] == plan["tenant_id"]
         and evidence.get("conversion") == intent(plan) and evidence.get("observed_at") == plan["cutover_at"]
         and evidence.get("inventory_state") == "AVAILABLE_ONLY"
         and all(_nonblank(evidence.get(k)) for k in ("approved_by","reference","evidence_id","movement_freeze_reference")),
         "CENSUS_REQUIRED","必须有同租户非空实盘审批、停写引用及精确转换断言")
    history=evidence.get("history_boundary",{})
    need(history.get("through") == plan["cutover_at"] and history.get("coverage") in {"COMPLETE_AGGREGATE","GAPPED_AGGREGATE"}
         and _nonblank(history.get("reference")),"HISTORY_BOUNDARY","原聚合流水边界和历史缺口须有明确原引用")
    return evidence


def _id(value):
    need(isinstance(value, str) and re.fullmatch(r"[1-9][0-9]{0,18}", value) is not None
         and int(value) <= 9223372036854775807, "INVALID_ID", "ID必须是已有正式身份或明确提供的正64位元数据ID")
    return value


def _next(value, limit=9223372036854775807):
    need(isinstance(value,str) and value.isdigit() and 0 <= int(value) < limit, "VERSION_UNKNOWN", "原版本未知或不能安全递增")
    return str(int(value) + 1)


def _scope(row):
    return tuple(row.get(name) for name in ("tenant_id", "material_id", "warehouse_id", "location_id", "batch_id"))


def _ledger(snapshot, tenant, material):
    return [r for r in snapshot["tables"]["wm_material_stock_tx"] if r["tenant_id"] == tenant and r["material_id"] == material]


def _full(snapshot):
    validate_snapshot(snapshot); _coverage(snapshot)
    need("wm_trace_stock_conversion_run" not in snapshot["tables"],"CONVERSION_AUDIT_SCOPE","完整转换日志单独归档，原运行状态须放conversion_run_states避免循环摘要")
    states=snapshot.get("conversion_run_states")
    need(isinstance(states,list),"CONVERSION_AUDIT_SCOPE","必须显式导出转换运行状态，空列表也不能省略")
    seen=set()
    for state in states:
        need(isinstance(state,dict) and set(state) == STATE_FIELDS and all(isinstance(v,str) for v in state.values()),"CONVERSION_AUDIT_SCOPE","转换审计状态必须完整")
        need(state["tenant_id"] in snapshot["tenant_scope"] and (state["tenant_id"],state["run_id"]) not in seen
             and state["status"] in {"APPLIED","REVERTED"} and state["version"] == ("1" if state["status"] == "APPLIED" else "2"),"CONVERSION_AUDIT_SCOPE","转换审计状态重复、越界或版本未知")
        require_hash(state["plan_hash"]);require_hash(state["bridge_hash"]);seen.add((state["tenant_id"],state["run_id"]))
    _,bridge_findings=bridge_effects(snapshot)
    need(not any(f["severity"] == "ERROR" for f in bridge_findings),"CONVERSION_AUDIT_SCOPE","已有实盘桥不可验证，禁止再次转换")
    active={(r["tenant_id"],r["run_id"],r["plan_hash"],r["bridge_hash"]) for r in states if r["status"] == "APPLIED"}
    bridges={(r["tenant_id"],r["run_id"],r["plan_hash"],r["bridge_hash"]) for r in snapshot.get("stock_conversion_bridges",[])}
    need(active == bridges,"CONVERSION_AUDIT_SCOPE","已应用转换状态与完整实盘桥必须逐一对应")
    for table, rows in snapshot["tables"].items():
        for row in rows:
            need(set(row) == set(COLUMNS[table]), "INCOMPLETE_PROJECTION", "转换及SQL基线必须导出全部列：" + table)


def _validated(snapshot, plan):
    _full(snapshot)
    need(isinstance(plan,dict) and plan.get("schema_version") == 1 and plan.get("converter") == KIND, "INVALID_PLAN", "需要独立CURRENT_STOCK_TO_SN契约")
    validate_target(plan.get("target"))
    need(plan["target"] == snapshot["target"] and digest(snapshot) == plan.get("snapshot_hash"), "BASELINE_CAS", "转换前完整快照/目标身份已变化")
    need(re.fullmatch(r"[A-Za-z0-9_.-]{1,64}",plan.get("run_id", "")) is not None, "INVALID_RUN", "运行ID必须为明确的ASCII标识")
    tenant, material = plan["tenant_id"], _id(plan["material_id"])
    need(tenant in snapshot["tenant_scope"], "TENANT_SCOPE", "census租户必须在完整导出范围内")
    cutoff = datetime.fromisoformat(plan["cutover_at"])
    need(len(plan["cutover_at"]) == 19 and cutoff >= datetime.fromisoformat(snapshot["captured_at"]), "INVALID_CUTOVER", "census切点不得早于导出边界")
    evidence = _census(plan)
    history = evidence.get("history_boundary", {})
    ledger = _ledger(snapshot, tenant, material)
    need(history.get("through") == plan["cutover_at"] and history.get("coverage") in {"COMPLETE_AGGREGATE", "GAPPED_AGGREGATE"}
         and _nonblank(history.get("reference")) and history.get("ledger_hash") == digest(ledger), "HISTORY_BOUNDARY", "原聚合流水及历史缺口必须在census切点冻结，不得制造逐件历史")
    for tx in ledger:
        need(tx.get("tx_date") is not None and datetime.fromisoformat(tx["tx_date"]) <= cutoff, "HISTORY_BOUNDARY", "原流水时间未知或在census之后，须重新导出")
    policy = find(snapshot,"bd_material",{"tenant_id":tenant,"material_id":material})
    need(digest(policy) == plan["policy_before_hash"] and policy["tracking_version"] == plan["expected_policy_version"], "POLICY_CAS", "物料策略前行摘要/版本已变化")
    need(policy.get("status") == "1" and policy.get("del_flag") == "0" and policy.get("tracking_stage") == "AUDIT"
         and policy.get("is_batch_manage") in {"0","1"} and policy.get("is_serial_manage") in {"0","1"}, "POLICY_GATE", "只能在已启用且规则明确的AUDIT物料转换，STRICT须另行批准")
    _next(policy["tracking_version"])
    stocks = [r for r in snapshot["tables"]["wm_material_stock"] if r["tenant_id"] == tenant and r["material_id"] == material]
    for r in stocks:
        need(number(r,"stock_quantity") >= 0 and number(r,"total_cost") >= 0, "NEGATIVE_BALANCE", "负数量/金额须先走原业务处理", row_key("wm_material_stock",r))
        need(number(r,"stock_quantity") != 0 or number(r,"total_cost") == 0,"ZERO_QUANTITY_COST_REVIEW","零库存仍有金额，不能借身份转换掩盖原成本残留",row_key("wm_material_stock",r))
    for group in snapshot["tables"]["wm_valuation_group"]:
        if group["tenant_id"] != tenant or group["material_id"] != material:continue
        members=[r for r in snapshot["tables"]["wm_material_stock"] if r["tenant_id"] == tenant and r.get("valuation_group_id") == group["valuation_group_id"]]
        stock_ids={r["stock_id"] for r in members}
        effects=[tx for tx in snapshot["tables"]["wm_material_stock_tx"] if tx["tenant_id"] == tenant and tx.get("material_stock_id") in stock_ids and tx.get("tracking_version") == "2" and tx.get("del_flag") == "0"]
        quantity=number(group,"quantity")+sum((number(tx,"tx_flag")*number(tx,"tx_quantity") for tx in effects),ZERO)
        amount=number(group,"amount")+sum((number(tx,"tx_flag")*number(tx,"tx_amount") for tx in effects),ZERO)
        need(all(r["material_id"] == material for r in members) and quantity == sum((number(r,"stock_quantity") for r in members),ZERO)
             and amount == sum((number(r,"total_cost") for r in members),ZERO),"VALUATION_GROUP_REVIEW","原计价组基线及v2实际流水净效果与当前库存数量/金额不符；孤立余额不能被转换掩盖，0/0历史组保留",row_key("wm_valuation_group",group))
    positive = [r for r in stocks if number(r,"stock_quantity") > 0]
    sources = plan.get("sources")
    need(isinstance(sources,list) and sources and len({r["stock_id"] for r in sources}) == len(sources)
         and {r["stock_id"] for r in positive} == {r["stock_id"] for r in sources}, "MATERIAL_SCOPE_INCOMPLETE", "启用SN必须覆盖该租户物料全部正库存，不能只挑可拆行")
    identities = {(r["tenant_id"],r["sn_id"]): r for r in snapshot["tables"]["wm_sn"]}
    seen_sn, seen_stock, seen_group = set(), set(), set()
    current_ids={r["stock_id"] for r in snapshot["tables"]["wm_material_stock"]}
    group_ids={r["valuation_group_id"] for r in snapshot["tables"]["wm_valuation_group"]}
    for identity in identities.values():
        if identity["tenant_id"] != tenant or identity["material_id"] != material or identity.get("del_flag") != "0" or identity.get("status") != "IN_STOCK":continue
        current=[r for r in snapshot["tables"]["wm_material_stock"] if r["tenant_id"] == tenant and r.get("sn_id") == identity["sn_id"] and r.get("del_flag") == "0" and number(r,"stock_quantity") > 0]
        need(len(current) == 1 and current[0]["material_id"] == material and identity.get("stock_id") in {None,current[0]["stock_id"]},
             "IN_STOCK_IDENTITY_REVIEW", "在库身份必须有且仅一条同物料有效正库存；缺失、重复或位置冲突须核实原业务，不允许转换后遗留在库声明",row_key("wm_sn",identity))
    prepared=[]
    for source in sorted(sources,key=lambda r:int(_id(r["stock_id"]))):
        old=find(snapshot,"wm_material_stock",{"tenant_id":tenant,"stock_id":source["stock_id"]})
        key=row_key("wm_material_stock",old); qty=number(old,"stock_quantity"); amount=number(old,"total_cost")
        need(old["material_id"] == material and digest(old) == source["before_hash"], "SOURCE_CAS", "原库存完整行摘要、租户或物料不符",key)
        need(qty > 0 and qty == qty.to_integral_value(), "INTEGER_QUANTITY_REQUIRED", "只能转换正整数基本单位，每件1",key)
        need(qty == 1 or old.get("sn_id") is None, "LEGACY_MULTI_SN_REVIEW", "历史多件共享SN缺少唯一逐件归属证据，禁止自动挑选继承身份；保留原库存、旧SN状态位置和聚合流水，先核实真实业务及逐件实盘差异",key)
        need(old.get("del_flag") == "0" and old.get("tracking_version") == "1" and old.get("valuation_group_id") is None, "LEGACY_POOL_REVIEW", "首版只接收尚无计价组的历史库存，已转换/混合池须独立核实",key)
        need(number(old,"reserved_quantity") == 0 and old.get("frozen_flag") == "0" and old.get("wo_id") is None, "BUSINESS_DOCUMENT_REQUIRED", "预留、冻结或工单/在制库存不能凭数量均分，先按真实业务单据处理",key)
        need(old.get("warehouse_id") is not None and old.get("location_id") is not None and old.get("base_uom_id") is not None, "STORAGE_SCOPE_UNKNOWN", "仓库、储位及基本单位必须明确",key)
        need(policy.get("base_uom_id") is not None and old["base_uom_id"] == policy["base_uom_id"],"UNIT_SCOPE_REVIEW","原库存基本单位与物料策略不一致，不能隐式换算为每件1",key)
        peers=[r for r in stocks if _scope(r) == _scope(old)]
        need(sum(number(r,"stock_quantity") > 0 for r in peers) == 1, "COST_SCOPE_REVIEW", "同查池范围存在多条正库存，不推定同一计价范围或自动混并",key)
        for peer in peers:
            need(all(peer.get(f) == old.get(f) for f in ("vendor_id","area_id","wo_id","base_uom_id"))
                 and peer.get("valuation_group_id") is None and peer.get("tracking_version") == "1"
                 and number(peer,"reserved_quantity") == 0 and (number(peer,"stock_quantity") > 0 or number(peer,"total_cost") == 0), "COST_SCOPE_REVIEW", "零库存旧行也被实际查池使用，必须有相同已核实范围且无未知金额或预留",key)
            _next(peer["version"],2147483647)
            peer_history=[tx for tx in snapshot["tables"]["wm_material_stock_tx"] if tx["tenant_id"] == tenant and tx.get("material_stock_id") == peer["stock_id"]]
            need(all(tx["material_id"] == material and tx.get("tracking_version") == "1" for tx in peer_history),"LEGACY_POOL_REVIEW","原行存在跨物料、未知版本或v2流水，不能挂到新的实盘基线池",key)
        cost=source["cost_scope"]
        need(cost.get("method") == "MOVING_AVERAGE" and _nonblank(cost.get("source_reference"))
             and cost.get("dimensions") == {f:old.get(f) for f in DIMENSIONS}
             and cost.get("unit_cost") == old["unit_cost"], "COST_SCOPE_REVIEW", "必须有明确原移动平均范围凭据，不能按SN重定价",key)
        unit=number(old,"unit_cost")
        need(unit >= 0 and unit == (amount/qty).quantize(Decimal("0.000001"),rounding=ROUND_HALF_UP), "UNIT_COST_REVIEW", "原单位成本与余额范围不一致，待核实而非重算",key)
        group=_id(source["valuation_group_id"])
        need(group not in group_ids and group not in seen_group, "ID_COLLISION", "新计价组ID已存在或重复",key);seen_group.add(group)
        targets=source["targets"]
        need(isinstance(targets,list) and len(targets) == int(qty), "CENSUS_COUNT", "实盘正式SN集合必须逐件等于原整数数量",key)
        selected=[]
        for target in sorted(targets,key=lambda t:int(_id(t["identity_id"]))):
            sn=_id(target["identity_id"]); new_stock=_id(target["stock_id"])
            identity=identities.get((tenant,sn))
            need(identity is not None and identity["material_id"] == material and identity.get("del_flag") == "0"
                 and _nonblank(identity.get("sn_code")) and digest(identity) == target["identity_hash"], "REGISTERED_IDENTITY_REQUIRED", "只能引用同租户同物料已有正式SN及精确前行摘要",key)
            need(sn not in seen_sn and new_stock not in current_ids and new_stock not in seen_stock, "ID_COLLISION", "正式SN重复或新库存ID已存在",key)
            seen_sn.add(sn);seen_stock.add(new_stock);_next(identity["identity_version"])
            need(sum(r["tenant_id"] == tenant and r.get("sn_code") == identity["sn_code"] for r in snapshot["tables"]["wm_sn"]) == 1, "DUPLICATE_CANONICAL_CODE", "正式原码不唯一",key)
            active=[r for r in snapshot["tables"]["wm_material_stock"] if r["tenant_id"] == tenant and r.get("sn_id") == sn and number(r,"stock_quantity") > 0]
            original = old.get("sn_id") == sn
            need((original and identity["status"] == "IN_STOCK" and active == [old] and identity.get("stock_id") in {None,old["stock_id"]} and old.get("sn_code") == identity["sn_code"])
                 or (not original and not active and identity["status"] in {"REGISTERED","CREATED"} and identity.get("stock_id") is None), "IDENTITY_LIFECYCLE_REVIEW", "身份已在库、出库、在制、报废或位置不明，必须先核实真实业务生命周期",key)
            need(identity.get("batch_id") is None or identity.get("batch_id") == old.get("batch_id"), "BATCH_MISMATCH", "实盘SN与原批次不符，不能改批次规则",key)
            selected.append((target,identity))
        need(old.get("sn_id") is None or old["sn_id"] in {t[0]["identity_id"] for t in selected}, "LEGACY_IDENTITY_MISSING", "历史多件SN原身份必须在真实实盘集合中保留",key)
        if policy["is_batch_manage"] == "1" or old.get("batch_id") is not None:
            batch=find(snapshot,"wm_batch",{"tenant_id":tenant,"batch_id":old.get("batch_id")})
            need(batch["material_id"] == material and batch.get("del_flag") == "0", "BATCH_MISMATCH", "批次策略必须保留正式同物料批次",key)
        prepared.append((source,old,peers,selected))
    source_ids={r["stock_id"] for source,old,peers,selected in prepared for r in peers}
    headers={r["reservation_id"]:r for r in snapshot["tables"]["wm_trace_material_reservation"] if r["tenant_id"] == tenant}
    for line in snapshot["tables"]["wm_trace_material_reservation_line"]:
        if line["tenant_id"] == tenant and line.get("source_stock_id") in source_ids:
            header=headers.get(line["reservation_id"])
            need(header is not None and header.get("material_id") == material and header.get("status") == "CONSUMED"
                 and number(line,"quantity") > 0 and 0 <= number(line,"consumed_quantity") <= number(line,"quantity"),
                 "BUSINESS_DOCUMENT_REQUIRED", "原预留须有已核实CONSUMED终态及合法数量；取消/未知状态必须先核实真实释放凭据，不能均摊到SN")
    for table in ("wm_trace_outbox","wm_trace_inbox","mes_trace_outbox","mes_trace_inbox"):
        need(not any(r["tenant_id"] == tenant and r.get("status") != "APPLIED" for r in snapshot["tables"][table]), "MESSAGE_WINDOW_REQUIRED", "跨库消息尚未应用，必须先核实停写和消息边界")
    groups={r["posting_group_id"]:r for r in snapshot["tables"]["wm_trace_posting_group"] if r["tenant_id"] == tenant}
    for line in snapshot["tables"]["wm_document_sn"]:
        if line["tenant_id"] == tenant and (line.get("source_stock_id") in source_ids or line.get("identity_id") in seen_sn):
            need(groups.get(line.get("posting_group_id"),{}).get("status") == "POSTED", "BUSINESS_DOCUMENT_REQUIRED", "存在未完成冻结单据选择，先处理原业务单据")
    need(not any(b.get("run_id") == plan["run_id"] for b in snapshot["conversion_run_states"]), "RUN_REUSED", "已应用或已回退的转换运行ID不能重复使用")
    return policy,prepared


def assess(snapshot, plan):
    try:
        _validated(snapshot,plan)
        tasks=[]
    except ConversionBlocked as error: tasks=[error.task]
    except (ValueError,KeyError,TypeError,ArithmeticError) as error:
        tasks=[{"code":"INVALID_CENSUS_OR_EXPORT","detail":str(error),"action":"REVIEW_ORIGINAL_EVIDENCE"}]
    return {"schema_version":1,"converter":KIND,"ready":not tasks,"tasks":tasks,
            "strict_gate":"AUDIT_ONLY; STRICT须另行核实全部当前库存、未完成单据及历史缺口"}


def _amounts(amount, ids):
    ticks=int(amount*10000); each,remainder=divmod(ticks,len(ids))
    return {sn:f"{Decimal(each+(i<remainder))/10000:.4f}" for i,sn in enumerate(sorted(ids,key=int))}


def _seal(value):
    value["journal_hash"]=digest({k:v for k,v in value.items() if k != "journal_hash"});return value


def _totals(snapshot,tenant,material):
    rows=[r for r in snapshot["tables"]["wm_material_stock"] if r["tenant_id"] == tenant and r["material_id"] == material]
    return {"quantity":f"{sum((number(r,'stock_quantity') for r in rows),ZERO):.4f}","amount":f"{sum((number(r,'total_cost') for r in rows),ZERO):.4f}"}


def _build(snapshot, plan):
    policy,prepared=_validated(snapshot,plan)
    after=deepcopy(snapshot);items=[];sources=[]
    def change(table,before,new):
        key=row_key(table,new)
        if before is None: after["tables"][table].append(deepcopy(new))
        else:
            dest=find(after,table,key);dest.clear();dest.update(deepcopy(new))
        items.append({"item_id":str(len(items)+1),"table":table,"key":key,"action":"INSERT" if before is None else "UPDATE","before":deepcopy(before),"after":deepcopy(new),"before_hash":digest(before),"after_hash":digest(new)})
    for source,old,peers,selected in prepared:
        qty=number(old,"stock_quantity");amount=number(old,"total_cost");group=source["valuation_group_id"]
        costs=_amounts(amount,[t["identity_id"] for t,n in selected])
        pool=dict.fromkeys(COLUMNS["wm_valuation_group"])
        pool.update(valuation_group_id=group,tenant_id=plan["tenant_id"],material_id=plan["material_id"],warehouse_id=old["warehouse_id"],batch_id=old["batch_id"],source_stock_id=old["stock_id"],quantity=f"{qty:.4f}",amount=f"{amount:.4f}",unit_cost=old["unit_cost"],valuation_method="MOVING_AVERAGE",policy_version=_next(policy["tracking_version"]),created_at=plan["cutover_at"],valuation_snapshot=canonical({"conversion_run":plan["run_id"],"cutover_at":plan["cutover_at"],"source_hash":source["before_hash"],"cost_scope":source["cost_scope"],"census_hash":plan["census"]["artifact_sha256"]}))
        change("wm_valuation_group",None,pool)
        for peer in sorted(peers,key=lambda r:int(r["stock_id"])):
            cleared=deepcopy(peer);cleared.update(stock_quantity="0.0000",total_cost="0.0000",tracking_version="2",valuation_group_id=group,version=_next(peer["version"],2147483647),active_sn_id=None)
            change("wm_material_stock",peer,cleared)
        targets=[]
        for target,identity in selected:
            sn=identity["sn_id"]; current=deepcopy(old)
            current.update(stock_id=target["stock_id"],sn_id=sn,sn_code=identity["sn_code"],stock_quantity="1.0000",reserved_quantity="0.0000",total_cost=costs[sn],tracking_version="2",valuation_group_id=group,active_sn_id=sn,version="0",create_time=plan["cutover_at"],update_time=plan["cutover_at"])
            change("wm_material_stock",None,current)
            located=deepcopy(identity)
            for field in ("batch_id","batch_code","warehouse_id","warehouse_code","warehouse_name","location_id","location_code"): located[field]=old.get(field)
            located.update(stock_id=current["stock_id"],status="IN_STOCK",identity_version=_next(identity["identity_version"]))
            change("wm_sn",identity,located)
            targets.append({"stock_id":current["stock_id"],"identity_id":sn,"quantity":"1.0000","amount":costs[sn]})
        sources.append({"source_before":deepcopy(old),"source_before_hash":digest(old),"source_after_hash":digest(find(after,"wm_material_stock",row_key("wm_material_stock",old))),"valuation_group_id":group,"targets":targets})
    enabled=deepcopy(policy);enabled.update(is_serial_manage="1",tracking_version=_next(policy["tracking_version"]))
    change("bd_material",policy,enabled)
    bridge={"schema_version":1,"run_id":plan["run_id"],"tenant_id":plan["tenant_id"],"material_id":plan["material_id"],"cutover_at":plan["cutover_at"],"plan":deepcopy(plan),"plan_hash":digest(plan),"sources":sources,"ledger_hash":digest(_ledger(snapshot,plan["tenant_id"],plan["material_id"])),"history_gap":"CENSUS_STARTS_CURRENT_IDENTITY_ONLY; ORIGINAL_AGGREGATE_TRANSACTIONS_RETAINED"}
    bridge["bridge_hash"]=digest(bridge)
    after.setdefault("stock_conversion_bridges",[]).append(bridge)
    after["conversion_run_states"].append(_state(plan,bridge["bridge_hash"],"APPLIED"))
    validate_snapshot(after)
    need(_totals(snapshot,plan["tenant_id"],plan["material_id"]) == _totals(after,plan["tenant_id"],plan["material_id"]),"CONSERVATION_FAILURE","转换前后实际整物料数量和金额必须精确相等")
    return after,items,bridge


def _state(plan,bridge_hash,status):
    return {"run_id":plan["run_id"],"tenant_id":plan["tenant_id"],"material_id":plan["material_id"],"status":status,"version":"1" if status == "APPLIED" else "2","plan_hash":digest(plan),"bridge_hash":bridge_hash}


def restored_snapshot(journal):
    restored=deepcopy(journal["before_snapshot"])
    restored["conversion_run_states"].append(_state(journal["plan"],journal["bridge_hash"],"REVERTED"))
    return restored


def _journal(snapshot,plan,after,items,bridge):
    journal={"schema_version":1,"converter":KIND,"run_id":plan["run_id"],"target":deepcopy(snapshot["target"]),"tenant_id":plan["tenant_id"],"material_id":plan["material_id"],"plan":deepcopy(plan),"plan_hash":digest(plan),"before_snapshot":deepcopy(snapshot),"before_snapshot_hash":digest(snapshot),"after_snapshot_hash":digest(after),"bridge_hash":bridge["bridge_hash"],"items":items,"status":"APPLIED","version":1,"quantity_net_change":"0.0000","amount_net_change":"0.0000","accounting_vouchers":[],"strict_enabled":False}
    journal["balances"]={"before":_totals(snapshot,plan["tenant_id"],plan["material_id"]),"after":_totals(after,plan["tenant_id"],plan["material_id"])}
    journal["quantity_net_change"]=f"{Decimal(journal['balances']['after']['quantity'])-Decimal(journal['balances']['before']['quantity']):.4f}"
    journal["amount_net_change"]=f"{Decimal(journal['balances']['after']['amount'])-Decimal(journal['balances']['before']['amount']):.4f}"
    journal["restored_snapshot_hash"]=digest(restored_snapshot(journal))
    return _seal(journal)


def validate_journal(journal):
    need(isinstance(journal,dict) and journal.get("converter") == KIND and journal.get("status") in {"APPLIED","REVERTED"}, "INVALID_JOURNAL", "需要原转换完整日志")
    before=journal["before_snapshot"];plan=journal["plan"]
    after,items,bridge=_build(before,plan)
    expected=_journal(before,plan,after,items,bridge)
    if journal["status"] == "REVERTED":
        expected.update(status="REVERTED",version=2);_seal(expected)
    need(journal == expected,"JOURNAL_CAS","完整日志不能由批准的census和前快照重现，禁止重封日志绕过")
    return after



def _comparable_snapshot(snapshot):
    """只忽略表行及运行/桥列表顺序；所有行值、导出边界和证据仍完整比较，冻结摘要不改写。"""
    try:
        state=deepcopy(snapshot)
        for table,rows in state["tables"].items():
            state["tables"][table]=sorted(rows,key=lambda row:canonical(row_key(table,row)))
        for field in ("conversion_run_states","stock_conversion_bridges"):
            if field in state:state[field]=sorted(state[field],key=lambda row:canonical((row["tenant_id"],row["run_id"])))
        return digest(state)
    except (KeyError,TypeError,ValueError):
        return None


def _same_snapshot(current, expected):
    current_hash=_comparable_snapshot(current)
    return current_hash is not None and current_hash == _comparable_snapshot(expected)


def apply(snapshot, plan, journal=None):
    if journal is not None:
        expected=validate_journal(journal)
        need(journal["status"] == "APPLIED" and digest(plan) == journal["plan_hash"] and _same_snapshot(snapshot,expected), "REPLAY_CAS", "重跑必须同一计划和原转换后完整快照")
        return deepcopy(snapshot),deepcopy(journal)
    after,items,bridge=_build(snapshot,plan)
    return after,_journal(snapshot,plan,after,items,bridge)


def _compensation(snapshot,journal,expected):
    tasks=[]
    for table in sorted(set(expected.get("tables",{}))|set(snapshot.get("tables",{}))):
        if table not in KEYS:
            tasks.append({"table":table,"action":"REVIEW_UNKNOWN_SOURCE"});continue
        previous={canonical(row_key(table,r)):r for r in expected["tables"].get(table,[])}
        current={canonical(row_key(table,r)):r for r in snapshot["tables"].get(table,[])}
        for key in sorted(set(previous)|set(current)):
            if previous.get(key) != current.get(key):
                tasks.append({"table":table,"key":__import__("json").loads(key),"expected_hash":digest(previous.get(key)),"current_hash":digest(current.get(key)),"action":"REVIEW_ORIGINAL_DOCUMENT_AND_COMPENSATE; NO_DIRECT_RESTORE"})
    previous_states={(r["tenant_id"],r["run_id"]):r for r in expected.get("conversion_run_states",[])}
    current_states={(r["tenant_id"],r["run_id"]):r for r in snapshot.get("conversion_run_states",[])}
    for key in sorted(set(previous_states)|set(current_states)):
        if previous_states.get(key) != current_states.get(key):
            tasks.append({"table":"wm_trace_stock_conversion_run","key":{"tenant_id":key[0],"run_id":key[1]},"expected_hash":digest(previous_states.get(key)),"current_hash":digest(current_states.get(key)),"action":"REVIEW_CONVERSION_AUDIT_STATE_AND_COMPENSATE; NO_DIRECT_RESTORE"})
    if not tasks: tasks.append({"action":"REVIEW_EXPORT_BOUNDARY_OR_EVIDENCE_CHANGE; NO_DIRECT_RESTORE"})
    return {"schema_version":1,"converter":KIND,"status":"COMPENSATION_REQUIRED","run_id":journal["run_id"],"expected_snapshot_hash":journal["after_snapshot_hash"],"current_snapshot_hash":digest(snapshot),"compensation_tasks":tasks,"sql_executable":False}


def revert(snapshot,journal):
    expected=validate_journal(journal)
    if journal["status"] == "REVERTED":
        need(_same_snapshot(snapshot,restored_snapshot(journal)),"REVERT_CAS","已回退日志对应的基线变化")
        return deepcopy(snapshot),deepcopy(journal)
    if not _same_snapshot(snapshot,expected):
        raise CompensationRequired(_compensation(snapshot,journal,expected))
    restored=restored_snapshot(journal);rev=deepcopy(journal);rev.update(status="REVERTED",version=2)
    return restored,_seal(rev)


def bridge_effects(snapshot):
    """仅验证过的实盘桥解释旧库存行清零；不补全逐件历史或调整总账差额。"""
    effects,findings={},[]
    for bridge in snapshot.get("stock_conversion_bridges",[]):
        try:
            need(bridge.get("bridge_hash") == digest({k:v for k,v in bridge.items() if k != "bridge_hash"}), "BRIDGE_HASH", "桥摘要不匹配")
            plan=bridge["plan"];census=_census(plan)
            need(digest(plan) == bridge["plan_hash"] and census.get("kind") == "PHYSICAL_COUNT" and census.get("conversion") == intent(plan), "BRIDGE_CENSUS", "桥缺少原实盘冻结断言")
            need(all(bridge[k] == plan[k] for k in ("run_id","tenant_id","material_id","cutover_at")), "BRIDGE_SCOPE", "桥范围不同")
            source_plans={r["stock_id"]:r for r in plan["sources"]}
            need(len(source_plans) == len(plan["sources"]) == len(bridge["sources"]), "BRIDGE_SCOPE", "来源集合不完整")
            cutoff=datetime.fromisoformat(bridge["cutover_at"])
            history=[r for r in _ledger(snapshot,bridge["tenant_id"],bridge["material_id"]) if r.get("tx_date") is not None and datetime.fromisoformat(r["tx_date"]) <= cutoff]
            need(digest(history) == bridge["ledger_hash"] == census["history_boundary"]["ledger_hash"], "BRIDGE_HISTORY", "原流水或导出边界变化，不能隐去旧差额")
            local={}
            for source in bridge["sources"]:
                old=source["source_before"];source_plan=source_plans[old["stock_id"]]
                key=(old["tenant_id"],old["stock_id"]);qty=number(old,"stock_quantity")
                need(set(old) == set(COLUMNS["wm_material_stock"]) and old["tenant_id"] == bridge["tenant_id"] and old["material_id"] == bridge["material_id"]
                     and digest(old) == source["source_before_hash"] == source_plan["before_hash"] and qty > 0 and qty == qty.to_integral_value(), "BRIDGE_SOURCE", "原完整库存/数量或摘要无效")
                actual=find(snapshot,"wm_material_stock",row_key("wm_material_stock",old))
                need(digest(actual) == source["source_after_hash"] and number(actual,"stock_quantity") == 0 and number(actual,"total_cost") == 0
                     and actual.get("sn_code") == old.get("sn_code") and actual.get("sn_id") == old.get("sn_id"), "BRIDGE_CURRENT_SOURCE", "清零旧行已变更，不能套用历史桥")
                expected={(r["identity_id"],r["stock_id"]) for r in source_plan["targets"]}
                targets=source["targets"]
                need(len(expected) == len(targets) == int(qty) and expected == {(r["identity_id"],r["stock_id"]) for r in targets}, "BRIDGE_TARGETS", "实盘逐件集合不符")
                amounts=_amounts(number(old,"total_cost"),[r["identity_id"] for r in targets])
                need(all(r["quantity"] == "1.0000" and r["amount"] == amounts[r["identity_id"]] for r in targets)
                     and source["valuation_group_id"] == source_plan["valuation_group_id"], "BRIDGE_AMOUNT", "桥数量金额或原池不守恒")
                need(key not in effects and key not in local,"BRIDGE_DUPLICATE","不能重复套用同一旧库存桥")
                local[key]={"quantity":qty,"cutover_at":bridge["cutover_at"]}
            effects.update(local)
            findings.append({"code":"CENSUS_IDENTITY_HISTORY_BOUNDARY","severity":"INFO","tenant_id":bridge["tenant_id"],"material_id":bridge["material_id"],"detail":"当前SN身份从批准实盘切点开始；原聚合流水保留，未重建历史逐件流转，不产生库存收益或会计凭证","run_id":bridge["run_id"]})
        except (ValueError,KeyError,TypeError,ArithmeticError):
            findings.append({"code":"INVALID_STOCK_CONVERSION_BRIDGE","severity":"ERROR","detail":"实盘桥摘要、来源行、逐件守恒或原流水不符，不能抹平旧差额"})
    return effects,findings
