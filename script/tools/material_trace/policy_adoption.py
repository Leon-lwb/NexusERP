"""独立原工单策略采纳契约：只生成新JSON与单ERP库待审SQL，不连接执行或修改原五表repair。"""

from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import re
from core import canonical, digest, require_hash, validate_artifact, validate_snapshot
from repair import _coverage
from schema_contract import SQL
from sql_writer import literal, _baseline, _guard_row, _raise_sql
from stock_conversion_sql import _audit_baseline

TABLE = "wm_work_order_policy_adoption"
KIND = "ORIGINAL_WORK_ORDER_POLICY_ADOPTION"
MESSAGE = "POLICY_ADOPTION_CAS：原工单、原策略证据或完整业务基线已变化"


def need(valid, message):
    if not valid:
        raise ValueError(message)


def sha(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def positive(value):
    need(
        isinstance(value, str)
        and re.fullmatch(r"[1-9][0-9]{0,18}", value)
        and int(value) <= 9223372036854775807,
        "必须明确提供正64位ID字符串",
    )
    return value


def source_columns():
    """从现行主DDL读取唯一固定来源表列，拒绝任意表名和部分投影。"""
    sql = (SQL / "ry-erp.sql").read_text(encoding="utf-8-sig")
    block = re.search(r"CREATE TABLE `prd_work_order` \([\s\S]*?\n\)[^;]*;", sql)
    need(block is not None, "缺少原工单结构契约")
    return dict(re.findall(r"^  `([a-z][a-z0-9_]*)` ([^\r\n]+)", block[0], re.M))


def fingerprint(*values):
    """与TraceHashes一致使用UTF16代码单元长度分帧，不能用Python字符数替代。"""
    return sha(
        "".join(
            "-1:" if v is None else str(len(str(v).encode("utf-16-le")) // 2) + ":" + str(v)
            for v in values
        )
    )


def prepare(snapshot, plan):
    try:
        return _prepare(snapshot, plan)
    except (KeyError, TypeError, IndexError) as invalid:
        raise ValueError("原策略采纳计划、审批或来源投影不完整") from invalid


def _prepare(snapshot, plan):
    validate_snapshot(snapshot)
    _coverage(snapshot)
    need(
        isinstance(snapshot.get("conversion_run_states"), list),
        "必须完整导出转换审计状态，不能漏过既有转换",
    )
    need(plan["schema_version"] == 1 and plan["kind"] == KIND, "原策略采纳契约版本无效")
    tenant = plan["tenant_id"]
    wo = positive(plan["erp_wo_id"])
    proposer = positive(plan["proposed_by"])
    approver = positive(plan["approved_by"])
    adoption_id = positive(plan["adoption_id"])
    need(proposer != approver, "申请人和独立批准人必须不同，离线工具不能自审批")
    need(
        isinstance(tenant, str)
        and re.fullmatch(r"[0-9]{6,20}", tenant)
        and snapshot["tenant_scope"] == [tenant],
        "原策略采纳只接收明确单租户完整快照",
    )
    need(
        isinstance(plan["run_id"], str) and re.fullmatch(r"[A-Za-z0-9_.:-]{1,64}", plan["run_id"]),
        "采纳运行ID无效",
    )
    command = plan["command_id"]
    need(isinstance(command, str) and 0 < len(command.strip()) <= 128, "采纳命令ID无效")
    intent = {k: deepcopy(v) for k, v in plan.items() if k not in ("approval", "census")}
    for name, kind in (("approval", "SIGNED_REVIEW"), ("census", "PHYSICAL_COUNT")):
        evidence = validate_artifact(plan[name])
        need(
            evidence["kind"] == kind
            and evidence["tenant_id"] == tenant
            and evidence.get("adoption") == intent
            and all(
                isinstance(evidence.get(k), str) and evidence[k].strip()
                for k in ("approved_by", "reference", "evidence_id")
            ),
            "必须有绑定完整原策略、来源行、审批时间窗和双方操作人的独立审批及实盘工件",
        )
    source_before = plan["source_before"]
    columns = source_columns()
    need(
        set(source_before) == set(columns)
        and all(v is None or isinstance(v, str) for v in source_before.values()),
        "原工单必须按主DDL完整逐列导出字符串或NULL",
    )
    need(
        all(source_before[k] is not None for k, d in columns.items() if "NOT NULL" in d),
        "原工单NOT NULL来源列不能伪造为空",
    )
    need(
        source_before["tenant_id"] == tenant
        and source_before["wo_id"] == wo
        and source_before["del_flag"] == "0"
        and source_before["wo_status"] in {"RELEASED", "IN_PROGRESS"}
        and digest(source_before) == require_hash(plan["source_before_hash"]),
        "仅同租户真实在途工单可导入采纳审批，完整行摘要必须一致",
    )
    wo_source = plan["wo_source_json"]
    need(
        isinstance(wo_source, str) and sha(wo_source) == require_hash(plan["expected_wo_hash"]),
        "真实WO/BOM/需求来源预览摘要不符",
    )
    origin = json.loads(wo_source)
    need(
        origin.get("tenantId") == tenant
        and origin.get("erpWoId") == wo
        and origin.get("rootMaterialId") == source_before["material_id"],
        "工单来源预览身份不符",
    )
    ids = origin.get("materialIds")
    need(
        isinstance(ids, list)
        and 0 < len(ids) <= 500
        and ids == sorted(set(ids), key=lambda v: int(positive(v)))
        and origin["rootMaterialId"] in ids,
        "必须明确完整WO物料范围，不能从当前策略推造",
    )

    def instant(value):
        need(isinstance(value, str), "时间必须是明确ISO Instant")
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        need(
            result.tzinfo is not None and result.microsecond % 1000 == 0,
            "审批或归档时间必须带偏移及毫秒精度",
        )
        return result

    approved = instant(plan["approved_at"])
    expires = instant(plan["expires_at"])
    need(expires > approved, "审批截止必须晚于批准，不能伪造历史生效")
    zone = plan["timestamp_zone"]
    need(
        isinstance(zone, str) and re.fullmatch(r"[+-](?:0[0-9]|1[0-4]):[0-5][0-9]", zone),
        "必须明确核实数据库/JDBC偏移",
    )
    target_zone = datetime.fromisoformat("2000-01-01T00:00:00" + zone).tzinfo

    def stamp(value):
        local = value.astimezone(target_zone)
        return local.strftime("%Y-%m-%d %H:%M:%S.") + f"{local.microsecond//1000:03d}"

    def millis(value):
        elapsed = value - datetime(1970, 1, 1, tzinfo=timezone.utc)
        return (elapsed.days * 86400 + elapsed.seconds) * 1000 + elapsed.microseconds // 1000

    source = plan["source_json"]
    need(
        isinstance(source, str) and sha(source) == require_hash(plan["source_hash"]),
        "原始归档JSON字节摘要不符",
    )
    archive = json.loads(source)
    need(
        archive.get("schemaVersion") == 1
        and archive.get("tenantId") == tenant
        and archive.get("erpWoId") == wo
        and isinstance(archive.get("materials"), list),
        "原始归档租户、工单或策略集合缺失",
    )
    materials = sorted(archive["materials"], key=lambda r: int(positive(r["materialId"])))
    need([r["materialId"] for r in materials] == ids, "原归档必须完整覆盖真实WO物料范围")
    for material in materials:
        need(
            material["origin"]
            in {
                "ORIGINAL_RELEASE_ARCHIVE",
                "IMMUTABLE_SOURCE_SNAPSHOT",
                "SIGNED_DHR_POLICY_SNAPSHOT",
            }
            and isinstance(material["reference"], str)
            and material["reference"].strip()
            and instant(material["capturedAt"]) <= approved,
            "必须有审批前真实原策略归档，禁止CURRENT_MASTER或未来证据",
        )
        require_hash(material["documentHash"])
        policy = material["policy"]
        need(
            set(policy) == {"batchManaged", "serialManaged", "allowMixedLot", "version", "stage"}
            and all(
                isinstance(policy[k], bool)
                for k in ("batchManaged", "serialManaged", "allowMixedLot")
            )
            and policy["stage"] in {"AUDIT", "STRICT"},
            "原策略必须逐项明确，不能默认补齐",
        )
        positive(policy["version"])
    request = fingerprint(
        "WO_POLICY_ADOPTION_V1",
        tenant,
        adoption_id,
        command,
        wo,
        plan["expected_wo_hash"],
        plan["source_hash"],
        proposer,
        approver,
        millis(approved),
        millis(expires),
    )
    need(request == require_hash(plan["request_hash"]), "独立审批的原请求摘要不符")
    decision_raw = plan["approval_json"]
    need(
        isinstance(decision_raw, str) and sha(decision_raw) == require_hash(plan["approval_hash"]),
        "原审批JSON字节摘要不符",
    )
    decision = json.loads(decision_raw)
    need(
        decision.get("decision") == "APPROVED"
        and decision.get("requestHash") == request
        and decision.get("approvedBy") == approver
        and instant(decision["approvedAt"]) == approved
        and isinstance(decision.get("reference"), str)
        and decision["reference"].strip(),
        "原审批未明确独立同意该份申请，工具不会生成批准结论",
    )
    need(
        not any(
            r["tenant_id"] == tenant and r["erp_wo_id"] == wo
            for r in snapshot["tables"]["wm_work_order_tracking_policy"]
        ),
        "WO已有原冻结策略，不能通过采纳覆盖",
    )
    rows = [
        dict(
            adoption_id=adoption_id,
            tenant_id=tenant,
            command_id=command,
            erp_wo_id=wo,
            expected_wo_hash=plan["expected_wo_hash"],
            source_json=source,
            source_hash=plan["source_hash"],
            approval_json=decision_raw,
            approval_hash=plan["approval_hash"],
            request_hash=request,
            proposed_by=proposer,
            approved_by=approver,
            approved_at=stamp(approved),
            expires_at=stamp(expires),
            status="APPROVED",
            version="1",
            applied_at=None,
            snapshot_hash=None,
        )
    ]
    bank = snapshot["tables"][TABLE]
    existing = [
        r
        for r in bank
        if r["tenant_id"] == tenant
        and (r["adoption_id"] == adoption_id or r["command_id"] == command)
    ]
    need(
        not existing or existing == rows, "原采纳申请已执行、版本或意图冲突，禁止覆盖审批及冻结结果"
    )
    need(
        not any(r["adoption_id"] == adoption_id for r in bank if r not in existing),
        "新采纳元数据ID已被使用",
    )
    before = deepcopy(snapshot)
    before["tables"][TABLE] = [r for r in bank if r not in existing]
    need(
        digest(before) == require_hash(plan["snapshot_hash"]),
        "完整业务基线已变化，必须重新导出审批，不能重用旧采纳",
    )
    after = deepcopy(before)
    after["tables"][TABLE].extend(rows)
    validate_snapshot(after)
    result = dict(
        schema_version=1,
        kind=KIND,
        status="ALREADY_PRESENT" if existing else "REVIEW_REQUIRED",
        plan=deepcopy(plan),
        rows=rows,
        before_snapshot=before,
        after_snapshot=after,
    )
    result["journal_hash"] = digest(result)
    return result


def _source_guard(plan):
    row = plan["source_before"]
    parts = [literal("{")]
    for i, key in enumerate(sorted(row)):
        if i:
            parts.append(literal(","))
        parts += [
            literal(canonical(key) + ":"),
            f"IF(`{key}` IS NULL,{literal('null')},JSON_QUOTE(CAST(`{key}` AS CHAR CHARACTER SET utf8mb4)))",
        ]
    parts.append(literal("}"))
    expression = "SHA2(CONCAT(" + ",".join(parts) + "),256)"
    return [
        "  SET v_hash=NULL;",
        f"  SELECT {expression} INTO v_hash FROM `prd_work_order` WHERE BINARY tenant_id=BINARY {literal(plan['tenant_id'])} AND wo_id={literal(plan['erp_wo_id'])} FOR UPDATE;",
        f"  IF v_hash IS NULL OR BINARY v_hash<>BINARY {literal(plan['source_before_hash'])} THEN {_raise_sql(MESSAGE)} END IF;",
    ]


def emit(snapshot, journal, *, target=None, backup_reference=None, allow_db_writes=False):
    need(
        digest({k: v for k, v in journal.items() if k != "journal_hash"})
        == journal.get("journal_hash"),
        "采纳日志摘要无效",
    )
    expected = prepare(journal["before_snapshot"], journal["plan"])
    need(
        {k: v for k, v in expected.items() if k not in ("status", "journal_hash")}
        == {k: v for k, v in journal.items() if k not in ("status", "journal_hash")},
        "采纳日志不能添加任意变更",
    )
    need(
        snapshot in (journal["before_snapshot"], journal["after_snapshot"]),
        "SQL输入必须是该采纳的完整前/后快照",
    )
    need(
        allow_db_writes
        and target == "erp:" + snapshot["target"]["erp_database"]
        and isinstance(backup_reference, str)
        and 0 < len(backup_reference.strip()) <= 255,
        "待审SQL必须明确指定同一ERP目标和备份引用；工具不会执行",
    )
    server = snapshot["server_ids"]["erp"]
    need(
        isinstance(server, str) and re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", server),
        "服务器UUID必须完整",
    )
    plan = journal["plan"]
    proc = "mt_policy_adopt_" + digest(plan)[:20]
    out = [
        "-- 仅待审导入APPROVED申请；原策略/独立审批/实盘与数据库及JDBC时区必须核实。",
        "-- target: " + target,
        "-- backup: " + backup_reference.replace("\r", " ").replace("\n", " "),
        "-- 仅导入采纳审批；实际ERP服务adoptApproved在业务事务中生效，不直接写策略库或改既往历史。",
        "SET NAMES utf8mb4;",
        f"DROP PROCEDURE IF EXISTS `{proc}`;",
        "DELIMITER $$",
        f"CREATE PROCEDURE `{proc}`() SQL SECURITY INVOKER",
        "adoption_block: BEGIN",
        "  DECLARE v_count BIGINT DEFAULT 0;",
        "  DECLARE v_hash CHAR(64) DEFAULT NULL;",
        "  DECLARE v_error VARCHAR(128) DEFAULT NULL;",
        "  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;",
        f"  IF DATABASE() IS NULL OR BINARY DATABASE()<>BINARY {literal(snapshot['target']['erp_database'])} OR BINARY @@server_uuid<>BINARY {literal(server)} THEN {_raise_sql(MESSAGE)} END IF;",
        f"  IF EXISTS (SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='{TABLE}') THEN {_raise_sql('未核实触发器副作用，拒绝采纳')} END IF;",
        "  SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;",
        "  START TRANSACTION;",
    ]
    approved = (
        datetime.fromisoformat(plan["approved_at"].replace("Z", "+00:00"))
        .astimezone(timezone.utc)
        .strftime("%Y-%m-%d %H:%M:%S.%f")[:23]
    )
    expires = (
        datetime.fromisoformat(plan["expires_at"].replace("Z", "+00:00"))
        .astimezone(timezone.utc)
        .strftime("%Y-%m-%d %H:%M:%S.%f")[:23]
    )
    out += [
        f"  IF UTC_TIMESTAMP(3)<{literal(approved)} OR UTC_TIMESTAMP(3)>={literal(expires)} THEN {_raise_sql('采纳批准未生效或已过期，须重新独立审批')} END IF;"
    ]
    out += _source_guard(plan)
    out += [
        f"  SELECT COUNT(*) INTO v_count FROM `{TABLE}` WHERE BINARY tenant_id=BINARY {literal(plan['tenant_id'])} AND (adoption_id={literal(plan['adoption_id'])} OR BINARY command_id=BINARY {literal(plan['command_id'])}) FOR UPDATE;",
        "  IF v_count>0 THEN",
    ]
    out += _baseline(journal["after_snapshot"], "erp", MESSAGE) + _audit_baseline(
        journal["after_snapshot"], "__POLICY_ADOPTION__"
    )
    out += ["    COMMIT; LEAVE adoption_block;", "  END IF;"]
    out += _baseline(journal["before_snapshot"], "erp", MESSAGE) + _audit_baseline(
        journal["before_snapshot"], "__POLICY_ADOPTION__"
    )
    for row in journal["rows"]:
        cols = sorted(row)
        out += [
            f"  INSERT INTO `{TABLE}` ("
            + ",".join("`" + k + "`" for k in cols)
            + ") VALUES ("
            + ",".join(literal(row[k]) for k in cols)
            + ");",
            f"  IF ROW_COUNT()<>1 THEN {_raise_sql(MESSAGE)} END IF;",
        ] + _guard_row(TABLE, row, MESSAGE)
    out += _baseline(journal["after_snapshot"], "erp", MESSAGE) + [
        "  COMMIT;",
        "END$$",
        "DELIMITER ;",
        f"CALL `{proc}`();",
        f"DROP PROCEDURE `{proc}`;",
        "",
    ]
    return "\n".join(out)
