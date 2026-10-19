"""当前库存converter专用单库待审SQL；没有连接器或执行入口。"""
import re
from core import COLUMNS, canonical, digest, identifier, row_key
import stock_converter as converter
from sql_writer import literal, _baseline, _guard_row, _update, _where, row_hash_sql, _raise_sql

RUN_TABLE="wm_trace_stock_conversion_run"
MESSAGE="CONVERSION_BASELINE_CAS：原值或新业务已变化，必须核实补偿"


def _audit_baseline(snapshot, own_run):
    """转换审计状态作为额外source基线，保留已回退运行，不当作业务写表。"""
    tenants=",".join(literal(t) for t in sorted(snapshot["tenant_scope"]))
    states=[r for r in snapshot["conversion_run_states"] if r["run_id"] != own_run]
    out=["  -- conversion_run_states：同时核对其他已应用和已回退运行，不能漏导出。",
         f"  SELECT COUNT(*) INTO v_count FROM `{RUN_TABLE}` WHERE tenant_id IN ({tenants}) AND BINARY run_id<>BINARY {literal(own_run)} FOR UPDATE;",
         f"  IF v_count <> {len(states)} THEN {_raise_sql(MESSAGE)} END IF;"]
    for state in sorted(states,key=lambda r:(r["tenant_id"],r["run_id"])):
        predicate=" AND ".join("BINARY `"+name+"` <=> BINARY "+literal(value) for name,value in sorted(state.items()))
        out += [f"  SELECT COUNT(*) INTO v_count FROM `{RUN_TABLE}` WHERE {predicate} AND BINARY SHA2(journal_json,256)=BINARY journal_hash FOR UPDATE;",
                f"  IF v_count <> 1 THEN {_raise_sql(MESSAGE)} END IF;"]
    return out


def _insert(table,row):
    columns=[c for c in sorted(row) if c != "active_sn_id"]
    out=[f"  INSERT INTO `{table}` ("+",".join("`"+c+"`" for c in columns)+") VALUES ("+",".join(literal(row[c]) for c in columns)+");",
         f"  IF ROW_COUNT() <> 1 THEN {_raise_sql(MESSAGE)} END IF;"]
    return out+_guard_row(table,row,MESSAGE)


def _delete(table,row):
    return _guard_row(table,row,MESSAGE)+[
        f"  DELETE FROM `{table}` WHERE "+_where(table,row_key(table,row))+" AND BINARY "+row_hash_sql(table,row)+"=BINARY "+literal(digest(row))+";",
        f"  IF ROW_COUNT() <> 1 THEN {_raise_sql(MESSAGE)} END IF;"]


def emit(snapshot,journal,*,target=None,backup_reference=None,allow_db_writes=False,mode="apply"):
    """只接受独立converter完整日志，重建全部变更后才生成明确授权的ERP文件。"""
    expected=converter.validate_journal(journal)
    if mode not in {"apply","revert"} or journal["status"] != "APPLIED":raise ValueError("SQL需要原APPLIED转换日志及apply/revert模式")
    if not allow_db_writes or not isinstance(target,str) or not isinstance(backup_reference,str) or not backup_reference.strip() or len(backup_reference)>255:
        raise ValueError("输出SQL必须显式指定target、backup-reference与allow-db-writes；工具不会执行")
    pieces=target.split(":")
    if len(pieces)!=2 or pieces[0]!="erp":raise ValueError("转换只输出ERP单库SQL")
    database=pieces[1];identifier(database)
    if database!=snapshot["target"]["erp_database"] or snapshot["target"]!=journal["target"]:raise ValueError("目标库与批准快照不同")
    server=snapshot.get("server_ids",{}).get("erp")
    if not isinstance(server,str) or re.fullmatch(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}",server) is None:raise ValueError("必须提供核实后的server_uuid")
    if mode=="apply":
        if digest(snapshot)!=journal["before_snapshot_hash"]:raise ValueError("apply必须使用原完整前快照")
        replay=expected;result=journal
    else:
        replay,result=converter.revert(snapshot,journal)
    run=journal["run_id"];rid=literal(run);jh=literal(journal["journal_hash"]);ph=literal(journal["plan_hash"])
    desired="APPLIED" if mode=="apply" else "REVERTED"
    proc="stock_conversion_"+mode+"_"+digest(run)[:16]
    out=["-- 当前库存身份转换待审文件：只生成，不连接或执行数据库。",
         "-- 原流水保持原粒度；没有虚构收货、发货、收益或会计凭证。",
         "-- 必须核实两库停写/消息窗口、真实physical census与备份；DDL不作为业务事务回滚。",
         "-- target: "+target,"-- backup: "+backup_reference.replace("\r"," ").replace("\n"," "),
         "-- run: "+run+"; material: "+journal["material_id"]+"; quantity: "+journal["balances"]["before"]["quantity"]+"; amount: "+journal["balances"]["before"]["amount"]+"; net quantity/amount: 0.0000/0.0000",
         "SET NAMES utf8mb4;",f"DROP PROCEDURE IF EXISTS `{proc}`;","DELIMITER $$",
         f"CREATE PROCEDURE `{proc}`() SQL SECURITY INVOKER","conversion_block: BEGIN",
         "  DECLARE v_count BIGINT DEFAULT 0;","  DECLARE v_hash CHAR(64) DEFAULT NULL;","  DECLARE v_error VARCHAR(128) DEFAULT NULL;",
         "  DECLARE v_status VARCHAR(24) DEFAULT NULL;","  DECLARE v_version BIGINT DEFAULT NULL;","  DECLARE v_revert CHAR(64) DEFAULT NULL;",
         "  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;",
         f"  IF DATABASE() IS NULL OR BINARY DATABASE()<>BINARY {literal(database)} OR BINARY @@server_uuid<>BINARY {literal(server)} THEN",
         "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='显式目标库或服务器UUID不同';","  END IF;",
         "  IF EXISTS (SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE IN ('wm_material_stock','wm_sn','wm_valuation_group','bd_material','wm_trace_stock_conversion_run')) THEN",
         "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在未审查触发器副作用，禁止转换';","  END IF;",
         "  SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;","  START TRANSACTION;",
         f"  SELECT COUNT(*) INTO v_count FROM `{RUN_TABLE}` WHERE run_id={rid} FOR UPDATE;",
         "  IF v_count>0 THEN",
         f"    SELECT COUNT(*) INTO v_count FROM `{RUN_TABLE}` WHERE run_id={rid} AND BINARY tenant_id=BINARY {literal(journal['tenant_id'])} AND material_id={literal(journal['material_id'])} AND BINARY plan_hash=BINARY {ph} AND BINARY journal_hash=BINARY {jh} AND BINARY bridge_hash=BINARY {literal(journal['bridge_hash'])} AND BINARY before_snapshot_hash=BINARY {literal(journal['before_snapshot_hash'])} AND BINARY after_snapshot_hash=BINARY {literal(journal['after_snapshot_hash'])} AND BINARY SHA2(journal_json,256)=BINARY journal_hash FOR UPDATE;",
         "    IF v_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原转换日志、census或完整前后摘要不符'; END IF;",
         f"    SELECT status,version,revert_hash INTO v_status,v_version,v_revert FROM `{RUN_TABLE}` WHERE run_id={rid} FOR UPDATE;",
         f"    IF v_status='{desired}' AND v_version={1 if mode=='apply' else 2}"+(" THEN" if mode=="apply" else f" AND BINARY v_revert=BINARY {literal(result['journal_hash'])} THEN")]
    out += ["  "+line for line in _baseline(replay,"erp",MESSAGE)+_audit_baseline(replay,run)]
    out += ["      COMMIT;","      LEAVE conversion_block;","    END IF;"]
    if mode=="apply":
        out += ["    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='运行ID已存在或已回退，禁止重复转换';","  ELSE"]
        data={"run_id":run,"tenant_id":journal["tenant_id"],"material_id":journal["material_id"],"target_database":database,"target_environment":journal["target"]["environment"],"backup_reference":backup_reference,"cutover_at":journal["plan"]["cutover_at"],"plan_hash":journal["plan_hash"],"before_snapshot_hash":journal["before_snapshot_hash"],"after_snapshot_hash":journal["after_snapshot_hash"],"bridge_hash":journal["bridge_hash"],"journal_json":canonical({k:v for k,v in journal.items() if k!="journal_hash"}),"journal_hash":journal["journal_hash"],"status":"PREPARED","version":"1"}
        out += [f"    INSERT INTO `{RUN_TABLE}` ("+",".join("`"+k+"`" for k in data)+") VALUES ("+",".join(literal(v) for v in data.values())+");","  END IF;"]
    else:
        out += ["    IF v_status<>'APPLIED' OR v_version<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原运行状态不能回退'; END IF;","  ELSE",
                "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原转换运行不存在，禁止恢复';","  END IF;"]
    out += _baseline(snapshot,"erp",MESSAGE)+_audit_baseline(snapshot,run)
    if mode=="apply":
        for item in journal["items"]:
            if item["action"]=="INSERT":out += _insert(item["table"],item["after"])
            else:out += _update(item["table"],item["before"],item["after"],MESSAGE)
    else:
        # 先释放本次新SN正库存，再恢复原值，最后删除本次创建的计价组；旧流水从不删除。
        for item in reversed(journal["items"]):
            if item["action"]=="INSERT" and item["table"]=="wm_material_stock":out += _delete(item["table"],item["after"])
        for item in reversed(journal["items"]):
            if item["action"]=="UPDATE":out += _update(item["table"],item["after"],item["before"],MESSAGE)
        for item in reversed(journal["items"]):
            if item["action"]=="INSERT" and item["table"]=="wm_valuation_group":out += _delete(item["table"],item["after"])
    out += [f"  UPDATE `{RUN_TABLE}` SET status='{desired}',version={1 if mode=='apply' else 2}"+("" if mode=="apply" else ",revert_hash="+literal(result["journal_hash"]))+f" WHERE run_id={rid} AND status='{'PREPARED' if mode=='apply' else 'APPLIED'}' AND version=1 AND BINARY journal_hash=BINARY {jh};",
            "  IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='转换审计状态CAS失配'; END IF;",
            "  COMMIT;","END$$","DELIMITER ;",f"CALL `{proc}`();",f"DROP PROCEDURE `{proc}`;",""]
    return "\n".join(out)
