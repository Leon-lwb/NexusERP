"""有证据的既有行修复与严格回退。仅在内存构造新JSON，无数据库执行入口。"""
from collections import Counter
from copy import deepcopy
import re
from audit import audit
from core import (KEYS, REQUIRED_TABLES, WRITABLE, canonical, datasource, digest, find,
                  number, require_hash, row_key, validate_snapshot, validate_target, validate_artifact)

def _token(value):
    if not isinstance(value,str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}",value):
        raise ValueError("运行或修复项ID必须为64位以内ASCII标识")

def _coverage(snapshot):
    if not REQUIRED_TABLES <= set(snapshot.get("complete_tables",[])) & set(snapshot["tables"]):
        raise ValueError("修复及回退需要完整声明的基础对账表，禁止不完整快照")
    if not snapshot.get("captured_at") or set(snapshot.get("watermarks",{}))!={"erp","mes"}:
        raise ValueError("修复必须提供两个数据源的明确导出边界")
    tenants=snapshot.get("tenant_scope")
    if not isinstance(tenants,list) or not tenants or len(tenants)!=len(set(tenants)) or any(not isinstance(t,str) or not t or len(t)>20 for t in tenants):
        raise ValueError("修复必须显式声明完整租户范围")
    if any(r["tenant_id"] not in tenants for rows in snapshot["tables"].values() for r in rows):
        raise ValueError("导出行越过显式租户范围")

def _evidence(snapshot,item):
    candidates=[e for e in snapshot.get("evidence",[]) if e.get("evidence_id")==item.get("evidence_id")]
    if len(candidates)!=1:raise ValueError("修复必须引用唯一既有证据")
    e=candidates[0]
    if e.get("kind") not in {"SOURCE_DOCUMENT","SIGNED_REVIEW","PHYSICAL_COUNT"}:
        raise ValueError("证据类型不受支持，同码同物料不是关联依据")
    if e.get("tenant_id")!=item["key"]["tenant_id"]:raise ValueError("证据租户与目标租户不同")
    if not e.get("reference") or not e.get("approved_by"):raise ValueError("证据必须保留原凭据引用与审核人")
    validate_artifact(e)
    assertion={"table":item["table"],"key":item["key"],"changes":item["changes"]}
    if sum(a==assertion for a in e.get("assertions",[]))!=1:raise ValueError("证据未明确授权该主键与精确变更值")
    if not isinstance(e.get("source_rows"),list) or not e["source_rows"]:raise ValueError("证据缺少可复核来源行")
    sources=[]
    for source in e["source_rows"]:
        if source.get("table") not in snapshot["complete_tables"]:raise ValueError("证据来源表未完整导出")
        r=find(snapshot,source["table"],source["key"])
        if r["tenant_id"]!=item["key"]["tenant_id"]:raise ValueError("证据来源跨租户")
        if digest(r)!=require_hash(source.get("row_hash")):raise ValueError("证据来源行摘要CAS失配")
        sources.append((source["table"],row_key(source["table"],r)))
    return e,sources

def _source(sources,table,key):
    if (table,key) not in sources:raise ValueError("目标身份或批次没有精确来源凭据，禁止同码推断")

def _reference(snapshot,sources,table,key,material):
    r=find(snapshot,table,key)
    if r.get("material_id")!=material or r.get("del_flag")=="1":raise ValueError("目标身份或批次跨物料或已删除")
    _source(sources,table,key)
    return r

def _validate_change(snapshot,item,before,after,sources):
    table=item["table"];tenant=before["tenant_id"];material=before.get("material_id");changes=item["changes"]
    for version in ("identity_version","allocation_version","version"):
        if version in changes and int(changes[version])!=int(before.get(version) or "1")+1:
            raise ValueError("修复版本必须恰好递增1")
    if table=="pro_sn":
        if "identity_id" in changes:
            if not after.get("identity_id"):raise ValueError("修复不能解除正式身份以绕过约束")
            _reference(snapshot,sources,"wm_sn",{"tenant_id":tenant,"sn_id":after["identity_id"]},material)
        if "product_lot_id" in changes:
            if not after.get("product_lot_id"):raise ValueError("修复不能移除批次证据")
            _reference(snapshot,sources,"lt_lot",{"tenant_id":tenant,"lot_id":after["product_lot_id"]},material)
        if set(changes)&{"identity_id","product_lot_id"} and "identity_version" not in changes:
            raise ValueError("身份映射修复必须递增identity_version")
    elif table=="lt_lot":
        if "erp_batch_id" in changes:
            if not after.get("erp_batch_id"):raise ValueError("修复不能移除已知批次")
            _reference(snapshot,sources,"wm_batch",{"tenant_id":tenant,"batch_id":after["erp_batch_id"]},material)
            if "allocation_version" not in changes:raise ValueError("批次修复必须递增allocation_version")
    elif table in ("wm_material_stock","wm_material_stock_tx"):
        if "sn_id" in changes or "sn_code" in changes or int(after.get("tracking_version") or "1")>=2:
            if not after.get("sn_id"):raise ValueError("升级的SN库存必须引用既有身份")
            n=_reference(snapshot,sources,"wm_sn",{"tenant_id":tenant,"sn_id":after["sn_id"]},material)
            if n.get("sn_code")!=after.get("sn_code"):raise ValueError("库存正式原码与身份不一致，不能补造编码")
            if n.get("batch_id")!=after.get("batch_id"):raise ValueError("身份与库存批次不一致")
            quantity="stock_quantity" if table=="wm_material_stock" else "tx_quantity"
            if number(after,quantity)!=1:raise ValueError("SN库存或事务必须是已确认的单件，不能自动拆分历史多件库存")
        if "tracking_version" in changes and not (int(before.get("tracking_version") or "1")==1 and changes["tracking_version"]=="2"):
            raise ValueError("追踪升级仅支持经凭据核对的1到2")
        if table=="wm_material_stock" and "version" not in changes:raise ValueError("库存修复必须递增乐观锁version")
        for field,target,pk in (("valuation_group_id","wm_valuation_group","valuation_group_id"),("posting_group_id","wm_trace_posting_group","posting_group_id")):
            if field in changes:
                if not after.get(field):raise ValueError("不能解除计价或过账证明")
                _reference(snapshot,sources,target,{"tenant_id":tenant,pk:after[field]},material)
        if "active_sn_id" in after:
            after["active_sn_id"]=after.get("sn_id") if int(after.get("tracking_version") or "1")>=2 and number(after,"stock_quantity")>0 else None
    elif table=="wm_sn":
        if "registration_command" in changes:
            # 发号事实只能由注册器建立。历史工具不允许伪装成新发号命令。
            raise ValueError("历史工具不能补造registration_command，须通过原注册器恢复已冻结命令")


def _errors(report):
    return Counter((f["code"],canonical(f.get("key")),f.get("tenant_id"),f.get("material_id")) for f in report["findings"] if f["severity"]=="ERROR")

def _seal(journal):
    journal["journal_hash"]=digest({k:v for k,v in journal.items() if k!="journal_hash"})
    return journal

def validate_journal(journal):
    require_hash(journal.get("journal_hash"))
    if digest({k:v for k,v in journal.items() if k!="journal_hash"})!=journal["journal_hash"]:raise ValueError("修复日志摘要不匹配")
    if digest(journal.get("plan"))!=journal.get("plan_hash"):raise ValueError("修复日志计划摘要不匹配")
    validate_target(journal.get("target"))
    _token(journal.get("run_id"))
    if journal.get("status") not in ("APPLIED","REVERTED"):raise ValueError("修复日志状态无效")
    expected_version=1 if journal["status"]=="APPLIED" else 2
    if journal.get("version")!=expected_version:raise ValueError("修复日志版本无效")
    for item in journal.get("items",[]):
        if item.get("table") not in WRITABLE:raise ValueError("日志目标不在修复白名单")
        if digest(item.get("before"))!=item.get("before_hash") or digest(item.get("after"))!=item.get("after_hash") or digest(item.get("evidence"))!=item.get("evidence_hash"):
            raise ValueError("修复日志行镜像或证据摘要不匹配")
        if set(item["before"])!=set(item["after"]) or row_key(item["table"],item["before"])!=item.get("key") or row_key(item["table"],item["after"])!=item.get("key"):
            raise ValueError("修复日志包含主键或列集变化")
        changed={k for k in item["after"] if item["after"][k]!=item["before"][k]}
        if changed-WRITABLE[item["table"]]-{"active_sn_id"}:raise ValueError("日志包含白名单外变更")
    return journal

def apply(snapshot,plan,journal=None):
    validate_snapshot(snapshot);_coverage(snapshot)
    if journal is not None:
        validate_journal(journal)
        if digest(plan)!=journal["plan_hash"] or plan.get("run_id")!=journal["run_id"]:raise ValueError("幂等运行ID对应的计划不同")
        if journal["status"]!="APPLIED":raise ValueError("已回退运行不能重用同一run_id")
        if digest(snapshot)!=journal["after_snapshot_hash"]:raise ValueError("幂等重放的快照CAS已变化")
        return deepcopy(snapshot),deepcopy(journal)
    if not isinstance(plan,dict) or plan.get("schema_version")!=1:raise ValueError("修复计划版本无效")
    validate_target(plan.get("target"))
    if plan["target"]!=snapshot["target"]:raise ValueError("计划目标环境或数据源身份不同")
    if digest(snapshot)!=require_hash(plan.get("snapshot_hash")):raise ValueError("修复前完整快照CAS失配")
    _token(plan.get("run_id"))
    if not isinstance(plan.get("items"),list) or not plan["items"]:raise ValueError("修复计划必须有非零项目")
    after=deepcopy(snapshot);items=[];seen=set();ids=set()
    for item in plan["items"]:
        table=item.get("table")
        if table not in WRITABLE:raise ValueError("目标表不在修复白名单")
        if item.get("datasource")!=datasource(table):raise ValueError("修复项数据源与目标表不同")
        _token(item.get("item_id"))
        if item["item_id"] in ids:raise ValueError("修复项ID重复")
        ids.add(item["item_id"])
        if not isinstance(item.get("changes"),dict) or not item["changes"] or set(item["changes"])-WRITABLE[table]:raise ValueError("变更列不在修复白名单")
        before=find(snapshot,table,item.get("key",{}))
        target=(table,canonical(item["key"]))
        if target in seen:raise ValueError("同一计划重复修改目标主键")
        seen.add(target)
        if digest(before)!=require_hash(item.get("before_hash")):raise ValueError("修复前行摘要CAS失配")
        if any(c not in before for c in item["changes"]):raise ValueError("变更字段没有导出前值，快照不完整")
        evidence,sources=_evidence(snapshot,item)
        new=deepcopy(before);new.update(item["changes"])
        _validate_change(snapshot,item,before,new,sources)
        if new==before:raise ValueError("空修复不能形成运行日志")
        dest=find(after,table,item["key"]);dest.clear();dest.update(new)
        items.append({"item_id":item["item_id"],"datasource":datasource(table),"table":table,"key":deepcopy(item["key"]),"before":deepcopy(before),"after":new,"evidence":deepcopy(evidence),"before_hash":digest(before),"after_hash":digest(new),"evidence_hash":digest(evidence),"status":"APPLIED","version":1})
    validate_snapshot(after)
    final_report=audit(after)
    affected={(item["table"],canonical(item["key"])) for item in items}
    for item in items:
        for field,table,pk in (("valuation_group_id","wm_valuation_group","valuation_group_id"),("posting_group_id","wm_trace_posting_group","posting_group_id")):
            if field in item["after"] and item["after"][field]:
                affected.add((table,canonical({"tenant_id":item["key"]["tenant_id"],pk:item["after"][field]})))
    if any(f["severity"]=="ERROR" and (f.get("table"),canonical(f.get("key"))) in affected for f in final_report["findings"]):
        raise ValueError("修复目标或所引用证明仍存在身份、数量或金额守恒冲突")
    introduced=_errors(final_report)-_errors(audit(snapshot))
    if introduced:raise ValueError("修复引入新的身份、数量、金额或唯一性冲突："+",".join(sorted({k[0] for k in introduced})))
    result={"schema_version":1,"run_id":plan["run_id"],"target":deepcopy(plan["target"]),"plan":deepcopy(plan),"plan_hash":digest(plan),"before_snapshot_hash":digest(snapshot),"after_snapshot_hash":digest(after),"status":"APPLIED","version":1,"items":items}
    return after,_seal(result)

def revert(snapshot,journal):
    validate_snapshot(snapshot);_coverage(snapshot);validate_journal(journal)
    if snapshot["target"]!=journal["target"]:raise ValueError("回退目标不同")
    if journal["status"]=="REVERTED":
        if digest(snapshot)!=journal["before_snapshot_hash"]:raise ValueError("COMPENSATION_REQUIRED：已回退后出现新业务，禁止覆盖")
        return deepcopy(snapshot),deepcopy(journal)
    if digest(snapshot)!=journal["after_snapshot_hash"]:
        raise ValueError("COMPENSATION_REQUIRED：导出范围出现新业务或证据变化，必须原凭据冲正补偿，不得覆盖历史")
    before=deepcopy(snapshot)
    for item in journal["items"]:
        row=find(before,item["table"],item["key"])
        if digest(row)!=item["after_hash"]:raise ValueError("COMPENSATION_REQUIRED：回退行CAS失配")
        row.clear();row.update(deepcopy(item["before"]))
    if digest(before)!=journal["before_snapshot_hash"]:raise ValueError("修复日志无法恢复原始快照摘要")
    reconstructed_after,reconstructed_journal=apply(before,journal["plan"])
    if reconstructed_after!=snapshot or reconstructed_journal!=journal:
        raise ValueError("修复日志不能由原始批准计划及证据重现")
    result=deepcopy(journal);result.update(status="REVERTED",version=2)
    for item in result["items"]:item.update(status="REVERTED",version=2)
    return before,_seal(result)
