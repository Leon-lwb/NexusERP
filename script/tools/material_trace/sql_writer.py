"""只导出待审MySQL 5.7 SQL。没有连接器、口令参数或数据库执行路径。"""
from copy import deepcopy
import re
from core import COLUMNS, KEYS, canonical, datasource, digest, identifier, row_key, validate_snapshot
from repair import apply, revert, validate_journal


def literal(value):
    """UTF8十六进制字面量保持coercibility=4，避免与列排序规则冲突且兼容NO_BACKSLASH_ESCAPES。"""
    if value is None:return "NULL"
    encoded=str(value).encode("utf-8").hex()
    return "_utf8mb4 0x"+encoded if encoded else "_utf8mb4 X''"


def _raise_sql(message):
    return "SET v_error="+literal(message)+"; SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_error;"


def row_hash_sql(table, row):
    if set(row)!=set(COLUMNS[table]):
        raise ValueError("SQL导出必须包含白名单表的全部列，不接受部分投影CAS")
    parts=[literal("{")]
    for i,name in enumerate(sorted(row)):
        if i:parts.append(literal(","))
        parts.append(literal(canonical(name)+":"))
        parts.append(f"IF(`{name}` IS NULL,{literal('null')},JSON_QUOTE(CAST(`{name}` AS CHAR CHARACTER SET utf8mb4)))")
    parts.append(literal("}"))
    return "SHA2(CONCAT("+",".join(parts)+"),256)"


def _where(table,key):
    if set(key)!=set(KEYS[table]):raise ValueError("SQL目标主键不完整")
    return " AND ".join("BINARY `"+k+"` <=> BINARY "+literal(key[k]) for k in KEYS[table])


def _guard_row(table,row, message):
    condition=_where(table,row_key(table,row))
    expression=row_hash_sql(table,row)
    return ["  SET v_hash=NULL;",f"  SELECT {expression} INTO v_hash FROM `{table}` WHERE {condition} FOR UPDATE;",
            f"  IF v_hash IS NULL OR BINARY v_hash <> BINARY {literal(digest(row))} THEN",
            f"    {_raise_sql(message)}","  END IF;"]


def _baseline(snapshot,ds,message):
    lines=[]
    tenants=",".join(literal(x) for x in sorted(snapshot["tenant_scope"]))
    for table,rows in sorted(snapshot["tables"].items()):
        if datasource(table)!=ds:continue
        if table not in snapshot["complete_tables"]:raise ValueError("SQL导出必须声明完整表范围")
        # SERIALIZABLE加完整范围锁防止导出租户范围出现新行；每行再以规范摘要CAS。
        lines += [f"  SELECT COUNT(*) INTO v_count FROM `{table}` WHERE tenant_id IN ({tenants}) FOR UPDATE;",
                  f"  IF v_count <> {len(rows)} THEN",f"    {_raise_sql(message)}","  END IF;"]
        for row in sorted(rows,key=lambda r:canonical(row_key(table,r))):lines += _guard_row(table,row,message)
    return lines


def _update(table,current,desired,message):
    changed=[k for k in sorted(current) if current[k]!=desired[k] and k!="active_sn_id"]
    # 避免带ON UPDATE的旧表自动更新时间导致历史行前后镜像不一致。
    preserved=[k for k in ("update_time","updated_at") if k in current and k not in changed]
    assignments=[f"`{k}`={literal(desired[k])}" for k in changed]+[f"`{k}`=`{k}`" for k in preserved]
    condition=_where(table,row_key(table,current))
    expression=row_hash_sql(table,current)
    return [f"  UPDATE `{table}` SET "+",".join(assignments)+f" WHERE {condition} AND BINARY {expression}=BINARY {literal(digest(current))};",
            "  IF ROW_COUNT() <> 1 THEN",f"    {_raise_sql(message)}","  END IF;"]+_guard_row(table,desired,message)


def emit(snapshot,journal,*,target=None,backup_reference=None,allow_db_writes=False,mode="apply"):
    """需要显式写授权标记才输出DML，但无论参数为何都不执行SQL。"""
    validate_snapshot(snapshot);validate_journal(journal)
    if not allow_db_writes or not target or not backup_reference:
        raise ValueError("生成写SQL必须显式给出--allow-db-writes、--target及--backup-reference")
    if not isinstance(backup_reference,str) or not backup_reference.strip() or len(backup_reference)>255:
        raise ValueError("备份引用必须为1到255位非空文本")
    if mode not in ("apply","revert"):raise ValueError("仅支持apply或revert")
    pieces=target.split(":")
    if len(pieces)!=2 or pieces[0] not in ("erp","mes"):raise ValueError("目标格式必须为erp:库名或mes:库名")
    ds,database=pieces;identifier(database)
    if database!=snapshot["target"][ds+"_database"]:raise ValueError("显式目标与导出环境不一致")
    server_id=snapshot.get("server_ids",{}).get(ds)
    if not isinstance(server_id,str) or not re.fullmatch(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}",server_id):
        raise ValueError("SQL导出必须绑定导出时核实的server_uuid")
    local=[i for i in journal["items"] if i["datasource"]==ds]
    if not local:raise ValueError("目标数据源没有本次修复项")
    if journal["status"]!="APPLIED":raise ValueError("SQL导出需要原始APPLIED日志")
    if mode=="apply":
        other,reconstructed=apply(snapshot,journal["plan"])
        if reconstructed!=journal:raise ValueError("修复日志不能由批准计划和基线重现")
        replay_baseline=other
        result_journal=journal
    else:
        other,result_journal=revert(snapshot,journal)
        replay_baseline=other
    prefix="wm" if ds=="erp" else "mes"
    run_table=prefix+"_trace_repair_run";item_table=prefix+"_trace_repair_item"
    proc="material_trace_"+mode+"_"+digest(journal["run_id"])[:16]
    rid=literal(journal["run_id"]);ph=literal(journal["plan_hash"])
    requested_status="APPLIED" if mode=="apply" else "REVERTED"
    requested_hash=literal(result_journal["journal_hash"])
    cas_message="修复前摘要CAS失配" if mode=="apply" else "COMPENSATION_REQUIRED：新业务或行摘要已变化"
    out=["-- 离线生成的待审SQL，本工具从不连接数据库。",
         "-- 已要求显式目标和备份引用；另需人工核实停写窗口、导出范围、两库证据及执行授权。",
         "-- 只对一个数据源提交。跨库证据在离线阶段核验，禁止将本文件描述为跨库原子修复。",
         "-- 若后续业务或凭据发生变化，回退必须改走冲正补偿，禁止强制导入旧备份。",
         "-- 显式目标："+target,
         "-- 备份引用："+backup_reference.replace("\r"," ").replace("\n"," "),
         "SET NAMES utf8mb4;", f"DROP PROCEDURE IF EXISTS `{proc}`;", "DELIMITER $$",
         f"CREATE PROCEDURE `{proc}`() SQL SECURITY INVOKER", "repair_block: BEGIN",
         "  DECLARE v_count BIGINT DEFAULT 0;", "  DECLARE v_hash CHAR(64) DEFAULT NULL;",
         "  DECLARE v_error VARCHAR(128) DEFAULT NULL;",
         "  DECLARE v_status VARCHAR(24) DEFAULT NULL;", "  DECLARE v_plan CHAR(64) DEFAULT NULL;",
         "  DECLARE v_journal CHAR(64) DEFAULT NULL;", "  DECLARE v_version BIGINT DEFAULT 0;",
         "  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;",
         f"  IF DATABASE() IS NULL OR BINARY DATABASE() <> BINARY {literal(database)} OR BINARY @@server_uuid <> BINARY {literal(server_id)} THEN",
         "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '服务器UUID或显式目标库不符';", "  END IF;",
         "  -- 自定义触发器可能引入未记录的副作用，必须先单独审查。",
         "  IF EXISTS (SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE IN ("+",".join(literal(i["table"]) for i in local)+")) THEN",
         "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '目标表存在未覆盖触发器，禁止自动写入';", "  END IF;",
         "  SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "  START TRANSACTION;",
         f"  SELECT COUNT(*) INTO v_count FROM `{run_table}` WHERE run_id={rid} FOR UPDATE;",
         "  IF v_count > 0 THEN",
         f"    SELECT status,plan_hash,journal_hash,version INTO v_status,v_plan,v_journal,v_version FROM `{run_table}` WHERE run_id={rid} FOR UPDATE;",
         f"    IF BINARY v_plan <> BINARY {ph} THEN", "      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '运行ID已对应其他批准计划';", "    END IF;",
         f"    IF v_status='{requested_status}' AND BINARY v_journal=BINARY {requested_hash} AND v_version={1 if mode=='apply' else 2} THEN"]
    out += ["  "+line for line in _baseline(replay_baseline,ds,cas_message)]
    out += ["      COMMIT;", "      LEAVE repair_block;", "    END IF;"]
    if mode=="apply":
        out += ["    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '运行状态不允许重复应用或复用回退运行';", "  END IF;"]
        cols=["run_id","target_database","target_environment","backup_reference","plan_hash","before_snapshot_hash","after_snapshot_hash","journal_hash","status","version"]
        vals=[journal["run_id"],database,journal["target"]["environment"],backup_reference,journal["plan_hash"],journal["before_snapshot_hash"],journal["after_snapshot_hash"],journal["journal_hash"],"PLANNED","1"]
        out += [f"  INSERT INTO `{run_table}` ("+",".join("`"+x+"`" for x in cols)+") VALUES ("+",".join(literal(x) for x in vals)+");"]
    else:
        out += [f"    IF v_status <> 'APPLIED' OR v_version <> 1 OR BINARY v_journal <> BINARY {literal(journal['journal_hash'])} THEN",
                "      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '原修复日志状态或摘要CAS失配';", "    END IF;", "  ELSE",
                "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '原修复运行不存在，禁止盲目回退';", "  END IF;"]
    out += _baseline(snapshot,ds,cas_message)
    for item in local:
        if mode=="apply":
            columns=["run_id","item_id","tenant_id","target_table","target_key","before_json","after_json","evidence_json","before_hash","after_hash","evidence_hash","status","version"]
            vals=[journal["run_id"],item["item_id"],item["key"]["tenant_id"],item["table"],canonical(item["key"]),canonical(item["before"]),canonical(item["after"]),canonical(item["evidence"]),item["before_hash"],item["after_hash"],item["evidence_hash"],"APPLIED","1"]
            out += [f"  INSERT INTO `{item_table}` ("+",".join("`"+x+"`" for x in columns)+") VALUES ("+",".join(literal(x) for x in vals)+");"]
            out += _update(item["table"],item["before"],item["after"],cas_message)
        else:
            out += [f"  SELECT COUNT(*) INTO v_count FROM `{item_table}` WHERE run_id={rid} AND item_id={literal(item['item_id'])} AND status='APPLIED' AND version=1 AND BINARY before_hash=BINARY {literal(item['before_hash'])} AND BINARY after_hash=BINARY {literal(item['after_hash'])} AND BINARY evidence_hash=BINARY {literal(item['evidence_hash'])} FOR UPDATE;",
                    "  IF v_count <> 1 THEN", "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '修复项日志CAS失配';", "  END IF;"]
            out += _update(item["table"],item["after"],item["before"],cas_message)
            out += [f"  UPDATE `{item_table}` SET status='REVERTED',version=2 WHERE run_id={rid} AND item_id={literal(item['item_id'])} AND status='APPLIED' AND version=1;",
                    "  IF ROW_COUNT() <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '修复项状态CAS失配'; END IF;"]
    before_status="PLANNED" if mode=="apply" else "APPLIED"
    out += [f"  UPDATE `{run_table}` SET status='{requested_status}',version={1 if mode=='apply' else 2},journal_hash={requested_hash} WHERE run_id={rid} AND status='{before_status}' AND version=1 AND BINARY plan_hash=BINARY {ph};",
            "  IF ROW_COUNT() <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '修复运行状态CAS失配'; END IF;", "  COMMIT;", "END$$", "DELIMITER ;", f"CALL `{proc}`();", f"DROP PROCEDURE `{proc}`;", ""]
    return "\n".join(out)
