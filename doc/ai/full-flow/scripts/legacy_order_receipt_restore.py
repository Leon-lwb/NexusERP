"""R2：仅据原 COMMITTED 回执证明七个旧 DRAFT/NULL 订单的出生身份。

本模式不认证历史 source/catalog 的完整动态前像、不证明当前 freshness、
不认证当前 actor 权限，也不授予数据库写权限。正常报价/确认/批准来源守卫不变。
旧 legacy_order_version_backfill 的公开 full-preimage 入口仍保留原拒绝策略。

snapshot 顶层与 orders 严格沿用旧工具；fingerprintEvidence 精确包含：
  policy, nativeAction, nativeRun, nativeRequisition, rootWorkOrder,
  requirements, mrpDetails, contract。各投影白名单见本文件 *_FIELDS。
原 payload_json/result_json 保留原生 JSON 字符串，不转换、补造或重签原动作。
ID 为规范 Java long 十进制字符串，金额/数量为精确 DECIMAL 文本；NULL 不默认 0。
仅两个已审查 update_time DATETIME ON UPDATE 列获准；固定头 CAS 显式自赋时间，
锁内所有原生 41/28 列、源链、事件、空集和 master 原动作快照前后仅版本可变化。

port 的五方法沿用旧工具。调用方必须独占 ERP RR/InnoDB 事务、加物料 guard、
完整来源/含删除历史的下游谓词锁及元数据 MDL；master 只读快照必须独立锁定，
不能宣称跨库 ACID。这个纯工具不含 DB 驱动、网络、配置读取或 --execute CLI。

函数：plan_restore(snapshot, ids), write_backup(snapshot, ids, path),
RestoreExecutor(port, *, history_dir).execute(snapshot, ids, backup_path)。
history_dir 必须是后续真实适配器固定的持久目录，不可更换以规避 UNKNOWN。
所有尝试先排他 fsync 意图；PENDING/COMMITTED/ROLLED_BACK/UNKNOWN 均禁止重试。
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
from typing import Protocol

import legacy_order_version_backfill as _legacy

_POLICY = "ORIGINAL_COMMITTED_RECEIPT_BIRTH_ONLY"
_SQL = ("UPDATE pu_purchase_order SET certified_version=0,update_time=update_time WHERE tenant_id=? "
        "AND order_id=? AND certified_version IS NULL AND status='DRAFT'")
_PROOF_FIELDS = frozenset("policy nativeAction nativeRun nativeRequisition rootWorkOrder requirements mrpDetails contract".split())
_ACTION_FIELDS = frozenset("id tenant_id run_id action_type target_biz_type target_biz_id target_biz_code purchase_payload_version payload_json approval_required approval_status execution_status permission_code executor_code idempotency_key approved_by approved_at started_at finished_at result_biz_type result_biz_id result_biz_code result_json last_error_code last_error_message del_flag".split())
_RUN_FIELDS = frozenset("id tenant_id biz_type biz_id biz_code status decision_contract_version started_at finished_at error_code error_message del_flag".split())
_NATIVE_REQ_FIELDS = frozenset("tenant_id requisition_id decision_run_id decision_action_id decision_idempotency_key create_by del_flag".split())
_WORK_FIELDS = frozenset("tenant_id wo_id wo_no plan_id source_type source_id del_flag".split())
_REQUIREMENT_FIELDS = frozenset("tenant_id requirement_id plan_id del_flag".split())
_DETAIL_FIELDS = frozenset("tenant_id detail_id requirement_id material_id unit required_date del_flag".split())
_PAYLOAD_FIELDS = frozenset("purchase_payload_version runId materialId vendorId windowEnd requirement_detail_ids frozenLines expectedSourceFingerprint".split()) | _legacy.CONTRACT_FIELDS
_FROZEN_FIELDS = frozenset("detailId baseUomId coveredDemandBase orderUomId requestedOrderQty requiredDate".split())
_ENVELOPE_FIELDS = frozenset("success retryable resultBizType resultBizId resultBizCode result errorCode errorMessage".split())
_RESULT_FIELDS = frozenset("purchase_payload_version runId actionId tenantId requestKey materialId vendorId sourceType sourceFingerprint payloadFingerprint status versionMarker beforeState afterState replayed lines".split())
_RESULT_LINE_FIELDS = frozenset("lineId requirementDetailId materialId baseUomId orderUomId coveredDemandBase requestedOrderQty requiredDate".split())
_ACTION_TYPE = "CREATE_PURCHASE_REQUISITION_DRAFT"
_BIZ_TYPE = "PU_PURCHASE_REQUISITION"
_REASONS = _legacy._INTERNAL_REASONS | frozenset("RECEIPT_POLICY ORIGINAL_ACTION ORIGINAL_RUN ORIGINAL_REQUISITION ORIGINAL_PAYLOAD ORIGINAL_RESULT ORIGINAL_ROOT ORIGINAL_MRP RECEIPT_CAUSAL_TIME RECEIPT_HASH RECEIPT_COVERAGE SHARED_NATIVE_DRIFT HISTORY_NO_RETRY HISTORY_WRITE_FAILED HISTORY_MISSING HISTORY_PATH_UNSAFE".split())


class RestoreRefused(_legacy.BackfillRefused):
    """固定安全原因，不打印原业务值、路径、驱动异常或原始 JSON。"""
    def __init__(self, code):
        self.code = code if type(code) is str and code in _REASONS else "UNTRUSTED_REASON"
        ValueError.__init__(self, "原提交回执恢复拒绝：" + self.code)


def _require(condition, code):
    if not condition:
        raise RestoreRefused(code)


def _shape(value, keys, code):
    _require(type(value) is dict and set(value) == set(keys), code)
    return value


def _parse_json(text):
    _require(type(text) is str and 0 < len(text) <= 1048576, "INVALID_JSON_EVIDENCE")
    try:
        value = json.loads(text, object_pairs_hook=_legacy._no_duplicate_keys)
        _legacy._json_bytes(value)
        return value
    except _legacy.BackfillRefused:
        raise
    except (ValueError, UnicodeError, RecursionError):
        raise RestoreRefused("INVALID_JSON_EVIDENCE") from None


def _day(value):
    result = _legacy._day(value)
    _require(1000 <= result.year <= 9999, "INVALID_NATIVE_DATE")
    return result


def _metadata(metadata):
    _legacy._metadata_structure(metadata)
    _require(metadata["columns"]["pu_purchase_order.certified_version"]["nullable"] == "YES", "NATIVE_COLUMN_CONTRACT")
    _require(type(metadata["orderUpdateTriggers"]) is list and not metadata["orderUpdateTriggers"], "ORDER_UPDATE_TRIGGER")
    rows = metadata["orderAutomaticUpdates"]
    _require(type(rows) is list and len(rows) == 2, "ORDER_AUTOMATIC_UPDATE")
    seen = set()
    for row in rows:
        _shape(row, ("tableName", "columnName", "dataType", "extra"), "ORDER_AUTOMATIC_UPDATE")
        table = row["tableName"]
        _require(type(table) is str and table in ("pu_purchase_order", "pu_purchase_order_line")
                 and table not in seen and row["columnName"] == "update_time"
                 and row["dataType"] == "datetime" and row["extra"] == "on update CURRENT_TIMESTAMP",
                 "ORDER_AUTOMATIC_UPDATE")
        seen.add(table)


def _payload(action, command, req_lines, root_unit):
    payload = _shape(_parse_json(action["payload_json"]), _PAYLOAD_FIELDS, "ORIGINAL_PAYLOAD")
    _require(type(payload["purchase_payload_version"]) is int and payload["purchase_payload_version"] == 2,
             "ORIGINAL_PAYLOAD")
    for field in ("runId", "materialId", "vendorId", "supplierTermsId", "baseUomId", "orderUomId"):
        _legacy._id(payload[field])
    for field in _legacy.CONTRACT_FIELDS:
        _legacy._text(payload[field])
        _require(not any(128 <= ord(c) <= 159 for c in payload[field]), "ORIGINAL_PAYLOAD")
    _require(payload["runId"] == action["run_id"] and payload["materialId"] == command["material_id"]
             and payload["vendorId"] == command["vendor_id"] and payload["windowEnd"] == command["window_end"]
             and payload["baseUomId"] == payload["orderUomId"] == "26", "ORIGINAL_PAYLOAD")
    _require(payload["baseUomVersion"] == payload["orderUomVersion"] == payload["conversionVersion"]
             == root_unit["conversion_version"], "FROZEN_ROOT_MISMATCH")
    _require(_legacy._digest(payload["expectedSourceFingerprint"]) == command["source_fingerprint"], "RECEIPT_HASH")
    window = _day(payload["windowEnd"])
    rows = sorted(req_lines.values(), key=lambda r: int(r["source_id"]))
    expected_ids = [r["source_id"] for r in rows]
    details = payload["requirement_detail_ids"]
    _require(type(details) is list and 0 < len(details) <= 1000, "ORIGINAL_PAYLOAD")
    for detail in details:
        _legacy._id(detail)
    _require(details == expected_ids and len(set(details)) == len(details), "ORIGINAL_PAYLOAD")
    frozen = payload["frozenLines"]
    _require(type(frozen) is list and len(frozen) == len(rows), "ORIGINAL_PAYLOAD")
    for line, row in zip(frozen, rows):
        _shape(line, _FROZEN_FIELDS, "ORIGINAL_PAYLOAD")
        _require(line["detailId"] == row["source_id"] and line["baseUomId"] == line["orderUomId"] == row["unit"],
                 "ORIGINAL_PAYLOAD")
        covered = _legacy._quantity(line["coveredDemandBase"], root_unit["quantity_scale"])
        ordered = _legacy._quantity(line["requestedOrderQty"], root_unit["quantity_scale"])
        _require(covered > 0 and covered == ordered == _legacy._decimal(row["apply_quantity"])
                 and line["requiredDate"] == row["required_date"] and _day(line["requiredDate"]) <= window,
                 "FROZEN_QUANTITY_OR_DATE")
    return payload, rows


def _payload_hash(action, payload, actor):
    fields = ["certified-purchase:v2", _legacy.TENANT, actor, action["id"], action["idempotency_key"],
              "WORK_ORDER", action["target_biz_id"]]
    for key in sorted(_PAYLOAD_FIELDS - {"frozenLines", "requirement_detail_ids"}):
        fields.extend((key, str(payload[key])))
    for line in payload["frozenLines"]:
        fields.extend((line["detailId"], line["baseUomId"], _legacy._number(line["coveredDemandBase"]),
                       line["orderUomId"], _legacy._number(line["requestedOrderQty"]), line["requiredDate"]))
    return _legacy._hash(*fields)


def _original_result(action, payload, command, payload_fp, rows):
    result = _shape(_parse_json(action["result_json"]), _ENVELOPE_FIELDS, "ORIGINAL_RESULT")
    _require(type(action["result_biz_code"]) is str
             and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", action["result_biz_code"]) is not None, "ORIGINAL_RESULT")
    _require(result["success"] is True and result["retryable"] is False and result["errorCode"] is None
             and result["errorMessage"] is None and result["resultBizType"] == _BIZ_TYPE
             and result["resultBizId"] == action["result_biz_id"] == command["requisition_id"]
             and result["resultBizCode"] == action["result_biz_code"] == command["requisition_no"], "ORIGINAL_RESULT")
    details = _shape(result["result"], _RESULT_FIELDS, "ORIGINAL_RESULT")
    _require(type(details["replayed"]) is bool and type(details["purchase_payload_version"]) is int,
             "ORIGINAL_RESULT")
    expected = dict(purchase_payload_version=2, runId=action["run_id"], actionId=action["id"], tenantId=_legacy.TENANT,
        requestKey=command["request_key"], materialId=command["material_id"], vendorId=command["vendor_id"],
        sourceType="AI_DECISION", sourceFingerprint=payload["expectedSourceFingerprint"], payloadFingerprint=payload_fp,
        status="DRAFT", versionMarker="v0", beforeState="COMMITTED" if details["replayed"] else "ABSENT",
        afterState="DRAFT", replayed=details["replayed"], lines=[])
    for row in rows:
        qty = _legacy._number(row["apply_quantity"])
        expected["lines"].append(dict(lineId=row["line_id"], requirementDetailId=row["source_id"],
            materialId=row["material_id"], baseUomId=row["unit"], orderUomId=row["unit"],
            coveredDemandBase=qty, requestedOrderQty=qty, requiredDate=row["required_date"]))
    _require(type(details["lines"]) is list and len(details["lines"]) == len(rows), "ORIGINAL_RESULT")
    for line in details["lines"]:
        _shape(line, _RESULT_LINE_FIELDS, "ORIGINAL_RESULT")
    _require(details == expected, "ORIGINAL_RESULT")


def _receipt(order, req_lines):
    proof = _shape(order["fingerprintEvidence"], _PROOF_FIELDS, "RECEIPT_POLICY")
    _require(proof["policy"] == _POLICY, "RECEIPT_POLICY")
    action = _shape(proof["nativeAction"], _ACTION_FIELDS, "ORIGINAL_ACTION")
    run = _shape(proof["nativeRun"], _RUN_FIELDS, "ORIGINAL_RUN")
    command = order["command"]
    for row in (action, run):
        _require(row["tenant_id"] == _legacy.TENANT and row["del_flag"] == "0", "ORIGINAL_ACTION")
        _legacy._id(row["id"])
    for field in ("run_id", "target_biz_id", "approved_by", "result_biz_id"):
        _legacy._id(action[field])
    _require(action["action_type"] == _ACTION_TYPE and action["target_biz_type"] == "WORK_ORDER"
             and type(action["purchase_payload_version"]) is int and action["purchase_payload_version"] == 2
             and action["approval_required"] == "Y" and action["approval_status"] == "APPROVED"
             and action["execution_status"] == "SUCCEEDED" and action["permission_code"] == "pu:requisition:add"
             and action["executor_code"] == "purchaseRequisitionDraft" and action["last_error_code"] is None
             and action["last_error_message"] is None and action["result_biz_type"] == _BIZ_TYPE, "ORIGINAL_ACTION")
    _require(run["id"] == action["run_id"] and run["biz_type"] == "WORK_ORDER" and run["status"] == "SUCCEEDED"
             and type(run["decision_contract_version"]) is int and run["decision_contract_version"] == 2
             and run["biz_id"] == action["target_biz_id"] and run["biz_code"] == action["target_biz_code"]
             and run["error_code"] is None and run["error_message"] is None, "ORIGINAL_RUN")
    _legacy._text(action["target_biz_code"], 64)
    nr = _shape(proof["nativeRequisition"], _NATIVE_REQ_FIELDS, "ORIGINAL_REQUISITION")
    _require(nr == dict(tenant_id=_legacy.TENANT, requisition_id=command["requisition_id"], decision_run_id=run["id"],
        decision_action_id=action["id"], decision_idempotency_key=command["request_key"], create_by=command["actor_id"],
        del_flag="0"), "ORIGINAL_REQUISITION")
    _require(_legacy._time(run["started_at"]) <= _legacy._time(action["approved_at"])
             <= _legacy._time(action["started_at"]) <= _legacy._time(order["requisition"]["head"]["create_time"])
             <= _legacy._time(action["finished_at"]) <= _legacy._time(run["finished_at"]), "RECEIPT_CAUSAL_TIME")
    for event in order["events"]:
        if event["event_type"] == "CREATE":
            _require(_legacy._time(action["started_at"]) <= _legacy._time(event["occurred_at"])
                     <= _legacy._time(action["finished_at"]), "RECEIPT_CAUSAL_TIME")
    root = _shape(proof["rootWorkOrder"], _WORK_FIELDS, "ORIGINAL_ROOT")
    for field in ("wo_id", "plan_id", "source_id"):
        _legacy._id(root[field])
    _require(root["tenant_id"] == _legacy.TENANT and root["del_flag"] == "0" and root["wo_id"] == run["biz_id"]
             and root["wo_no"] == run["biz_code"] and root["source_type"] in ("MATERIAL_REQUIREMENT", "PLAN"), "ORIGINAL_ROOT")
    requirements = _legacy._rows(proof["requirements"], _REQUIREMENT_FIELDS, "requirement_id")
    details = _legacy._rows(proof["mrpDetails"], _DETAIL_FIELDS, "detail_id")
    by_detail = {r["source_id"]: r for r in req_lines.values()}
    _require(set(details) == set(by_detail), "ORIGINAL_MRP")
    referenced = set()
    for detail, native in details.items():
        row = by_detail[detail]
        rid = _legacy._id(native["requirement_id"])
        _require(rid in requirements and native["material_id"] == row["material_id"] and native["unit"] == row["unit"]
                 and native["required_date"] == row["required_date"] and native["del_flag"] == "0", "ORIGINAL_MRP")
        requirement = requirements[rid]
        _require(requirement["plan_id"] == root["plan_id"] and requirement["del_flag"] == "0"
                 and root["source_id"] == (rid if root["source_type"] == "MATERIAL_REQUIREMENT" else root["plan_id"]), "ORIGINAL_ROOT")
        referenced.add(rid)
    _require(referenced == set(requirements), "ORIGINAL_MRP")
    for field in ("request_hash", "source_fingerprint", "catalog_fingerprint", "result_fingerprint"):
        _legacy._digest(command[field])
    payload, rows = _payload(action, command, req_lines, order["rootUnit"])
    key = "AI_PURCHASE_V2:" + _legacy._hash(_legacy.TENANT, run["id"], _ACTION_TYPE, "WORK_ORDER", root["wo_id"],
                                             payload["materialId"], payload["vendorId"])
    _require(key == action["idempotency_key"] == command["request_key"], "RECEIPT_HASH")
    contract = _shape(proof["contract"], ("payloadFingerprint", "metadata", "rootWorkOrderId"), "RECEIPT_HASH")
    _shape(contract["metadata"], _legacy.CONTRACT_FIELDS, "RECEIPT_HASH")
    _require(contract["metadata"] == {k: payload[k] for k in _legacy.CONTRACT_FIELDS}
             and contract["rootWorkOrderId"] == root["wo_id"], "RECEIPT_HASH")
    payload_fp = _payload_hash(action, payload, command["actor_id"])
    _require(_legacy._digest(contract["payloadFingerprint"]) == payload_fp, "RECEIPT_HASH")
    fields = [payload_fp]
    for k, v in sorted(contract["metadata"].items()):
        fields.extend((k, v))
    fields.extend(("WORK_ORDER", root["wo_id"]))
    frozen, results = [], []
    for row in rows:
        n, day = _legacy._number(row["apply_quantity"]), row["required_date"]
        frozen.append(_legacy._hash(row["source_id"], row["unit"], n, row["unit"], n, day))
        results.append(_legacy._hash(row["line_id"], row["source_id"], row["material_id"], row["unit"], n, day))
    request_hash = _legacy._hash(_legacy.TENANT, command["actor_id"], command["entry_type"], key,
        command["material_id"], command["vendor_id"], command["window_end"], "".join("," + r["source_id"] for r in rows),
        payload["expectedSourceFingerprint"], run["id"], action["id"], _legacy._hash(*frozen), _legacy._hash(*fields))
    _require(request_hash == command["request_hash"] and _legacy._hash(*results) == command["result_fingerprint"], "RECEIPT_HASH")
    _original_result(action, payload, command, payload_fp, rows)


def _plan_restore(snapshot, expected_order_ids):
    ids = _legacy._scope(expected_order_ids)
    _shape(snapshot, ("environment", "authorization", "metadata", "orders"), "SNAPSHOT_SHAPE")
    env = _shape(snapshot["environment"], ("host", "transport", "port", "schema", "profile", "tenant"), "SNAPSHOT_SHAPE")
    _require(env["host"] in ("localhost", "127.0.0.1") and env["transport"] == "TCP"
             and type(env["port"]) is int and env["port"] == 3306 and env["schema"] == "ry-erp"
             and env["profile"] == "dev" and env["tenant"] == _legacy.TENANT, "ENVIRONMENT_OUT_OF_SCOPE")
    auth = _shape(snapshot["authorization"], ("scope", "allowedOrderIds", "allowedChange", "production"), "SNAPSHOT_SHAPE")
    _require(auth["scope"] == "LOCAL_SOFTWARE_BUSINESS_DATA_COMPLETION" and auth["production"] is False
             and auth["allowedChange"] == "certified_version:NULL->0"
             and _legacy._scope(auth["allowedOrderIds"]) == ids, "AUTHORIZATION_OUT_OF_SCOPE")
    _metadata(snapshot["metadata"])
    _require(type(snapshot["orders"]) is list and len(snapshot["orders"]) == 7, "EXACT_SEVEN_SCOPE")
    orders, seen, shared = {}, {}, {}
    for order in snapshot["orders"]:
        oid, req_lines = _legacy._order_structure(order, ids, snapshot["metadata"])
        for row in (order["nativeRows"]["head"], *order["nativeRows"]["lines"]):
            _require(all(type(value) in (str, int, type(None)) for value in row.values()), "NATIVE_FULL_ROW_MISSING")
        _require(oid not in orders and len(req_lines) == len(order["lines"]) == len(order["ledger"]) == 1
                 and len(order["events"]) == 3, "RECEIPT_COVERAGE")
        _receipt(order, req_lines)
        orders[oid] = order
        for kind, values in (("req", [order["head"]["requisition_id"]]), ("key", [order["command"]["request_key"]]),
            ("action", [order["fingerprintEvidence"]["nativeAction"]["id"]]),
            ("allocation", [r["allocation_id"] for r in order["ledger"]]),
            ("line", [r["line_id"] for r in order["lines"]]), ("reqLine", list(req_lines)),
            ("detail", [r["source_id"] for r in req_lines.values()]), ("event", [r["event_id"] for r in order["events"]])):
            used = seen.setdefault(kind, set())
            _require(not used.intersection(values), "DUPLICATE_SOURCE")
            used.update(values)
        proof = order["fingerprintEvidence"]
        shared_rows = [("nativeRun", proof["nativeRun"], "id"),
                       ("rootWorkOrder", proof["rootWorkOrder"], "wo_id"), ("rootUnit", order["rootUnit"], "uom_id")]
        shared_rows.extend(("requirement", row, "requirement_id") for row in proof["requirements"])
        for kind, row, field in shared_rows:
            identity = (kind, row[field])
            _require(identity not in shared or shared[identity] == row, "SHARED_NATIVE_DRIFT")
            shared[identity] = row
    _require(set(orders) == set(ids), "EXACT_SEVEN_SCOPE")
    snapshot_sha = _legacy._sha(snapshot)
    plan = dict(status="PLANNED", policy=_POLICY, snapshotSha256=snapshot_sha,
        historicalSourcePreimageCertified=False, currentFreshnessCertified=False, actorPermissionCertified=False,
        statements=[dict(sql=_SQL, bindings=[_legacy.TENANT, oid], proofSha256=_legacy._sha(orders[oid])) for oid in ids],
        backup=dict(format="canonical-json-utf8", sha256=snapshot_sha), retryAllowed=False)
    plan["planSha256"] = _legacy._sha(plan)
    return plan


def plan_restore(snapshot: dict, expected_order_ids) -> dict:
    """纯计算、全有或全无，仅签发原回执出生的限定计划；不执行 I/O。"""
    try:
        return _plan_restore(snapshot, expected_order_ids)
    except _legacy.BackfillRefused:
        raise
    except (TypeError, KeyError, IndexError, ValueError, UnicodeError, OverflowError, RecursionError):
        raise RestoreRefused("MALFORMED_SNAPSHOT") from None

def _create_durable(path, raw):
    """排他写入并 fsync；失败残留文件刻意保留，绝不把失败重标为 NEW。"""
    with path.open("xb") as file:
        _require(file.write(raw) == len(raw), "HISTORY_WRITE_FAILED")
        file.flush()
        os.fsync(file.fileno())


def write_backup(snapshot: dict, expected_order_ids, path: Path) -> dict:
    """完整证据的规范 UTF-8 字节备份：排他创建，等同旧文件需重核字节/哈希。"""
    plan = plan_restore(snapshot, expected_order_ids)
    raw = _legacy._json_bytes(snapshot)
    path = Path(path)
    _require(not path.is_symlink(), "BACKUP_CONFLICT")
    try:
        _create_durable(path, raw)
    except FileExistsError:
        try:
            _require(not path.is_symlink() and path.read_bytes() == raw, "BACKUP_CONFLICT")
            # 上次 fsync 失败留下的相同文件，也必须本次真正完成持久化。
            with path.open("r+b") as file:
                os.fsync(file.fileno())
        except OSError:
            raise RestoreRefused("BACKUP_CONFLICT") from None
    except OSError:
        raise RestoreRefused("BACKUP_WRITE_FAILED") from None
    try:
        _require(path.read_bytes() == raw and hashlib.sha256(raw).hexdigest() == plan["snapshotSha256"], "BACKUP_CONFLICT")
    except OSError:
        raise RestoreRefused("BACKUP_WRITE_FAILED") from None
    return plan["backup"]


class RestorePort(Protocol):
    """仅描述注入事务；不实现连接、不读取配置、不签发 actor 权限。"""
    def begin(self) -> None: ...
    def read_locked(self, expected_order_ids: tuple[str, ...]) -> dict: ...
    def cas_set_birth_zero(self, sql: str, bindings: tuple[str, str]) -> int: ...
    def commit(self) -> None: ...
    def rollback(self) -> None: ...


class RestoreExecutor:
    """排他持久 history 门禁＋一次事务；UNKNOWN 永不回滚冒称确定或自动重试。"""
    def __init__(self, port: RestorePort, *, history_dir: Path):
        self.port = port
        self.history_dir = Path(history_dir)
        self.status = "NEW"

    def _reserve(self, ids, receipt):
        # localhost 和 127.0.0.1 是同一受控作用域，换备份/排列/主机别名不能换键。
        scope_hash = _legacy._sha(dict(policy=_POLICY, host="local", profile="dev", port=3306,
                                      schema="ry-erp", tenant=_legacy.TENANT, orderIds=list(ids)))
        directory = self.history_dir
        _require(not any(p.is_symlink() for p in (directory, *directory.parents)), "HISTORY_PATH_UNSAFE")
        attempt = directory / ("receipt-" + scope_hash + ".attempt.json")
        outcome = directory / ("receipt-" + scope_hash + ".outcome.json")
        try:
            directory.mkdir(parents=True, exist_ok=True)
            # 无论原内容是否完整可读，只要有历史就拒绝；不接受客户端 NEW/verified。
            _require(not attempt.exists() and not attempt.is_symlink()
                     and not outcome.exists() and not outcome.is_symlink(), "HISTORY_NO_RETRY")
            _create_durable(attempt, _legacy._json_bytes(dict(receipt, status="PENDING")))
        except FileExistsError:
            raise RestoreRefused("HISTORY_NO_RETRY") from None
        except OSError:
            raise RestoreRefused("HISTORY_WRITE_FAILED") from None
        return outcome

    def _finish(self, receipt, outcome_path, status, reason=None):
        result = dict(receipt, status=status)
        if reason is not None:
            result["reason"] = reason
        if status == "COMMITTED":
            result["changedRows"] = 7
        try:
            _create_durable(outcome_path, _legacy._json_bytes(result))
        except BaseException:
            # PENDING 意图仍永久阻止重试；末次日志不持久则也不报告确定成功。
            self.status = "UNKNOWN"
            return dict(receipt, status="UNKNOWN", reason="HISTORY_WRITE_FAILED")
        self.status = status
        return result

    def execute(self, snapshot: dict, expected_order_ids, backup_path: Path) -> dict:
        """先核完整证据/备份/历史，再锁内全重读、七 CAS、全字段对账、一次提交。"""
        _require(self.status == "NEW", "ONE_SHOT_NO_RETRY")
        self.status = "VALIDATING"
        try:
            snapshot = copy.deepcopy(snapshot)
            plan = plan_restore(snapshot, expected_order_ids)
            ids = _legacy._scope(expected_order_ids)
            path = Path(backup_path)
            _require(not path.is_symlink(), "BACKUP_CONFLICT")
            try:
                raw = path.read_bytes()
            except OSError:
                raise RestoreRefused("BACKUP_MISSING") from None
            _require(raw == _legacy._json_bytes(snapshot)
                     and hashlib.sha256(raw).hexdigest() == plan["snapshotSha256"], "BACKUP_CONFLICT")
            receipt = dict(policy=_POLICY, snapshotSha256=plan["snapshotSha256"], planSha256=plan["planSha256"],
                historicalSourcePreimageCertified=False, currentFreshnessCertified=False,
                actorPermissionCertified=False, retryAllowed=False)
            outcome_path = self._reserve(ids, receipt)
        except _legacy.BackfillRefused:
            self.status = "REJECTED"
            raise
        except (TypeError, ValueError, OSError, UnicodeError, RecursionError):
            self.status = "REJECTED"
            raise RestoreRefused("MALFORMED_SNAPSHOT") from None
        self.status = "RUNNING"
        try:
            _require(self.port.begin() is None, "PORT_BEGIN_CONTRACT")
            locked = self.port.read_locked(ids)
            _require(plan_restore(locked, ids) == plan, "LOCKED_SNAPSHOT_DRIFT")
            for statement in plan["statements"]:
                rowcount = self.port.cas_set_birth_zero(_SQL, tuple(statement["bindings"]))
                _require(type(rowcount) is int and rowcount == 1, "ROWCOUNT_NOT_ONE")
            expected = copy.deepcopy(snapshot)
            for order in expected["orders"]:
                order["nativeVersion"] = 0
                order["nativeRows"]["head"]["certified_version"] = 0
            _require(_legacy._json_bytes(self.port.read_locked(ids)) == _legacy._json_bytes(expected), "POSTWRITE_DRIFT")
        except BaseException as failure:
            reason = (failure.code if isinstance(failure, _legacy.BackfillRefused)
                      and type(failure.code) is str and failure.code in _REASONS else "PORT_EXCEPTION")
            try:
                _require(self.port.rollback() is None, "PORT_ROLLBACK_CONTRACT")
            except BaseException:
                return self._finish(receipt, outcome_path, "UNKNOWN", "ROLLBACK_OUTCOME_UNKNOWN")
            return self._finish(receipt, outcome_path, "ROLLED_BACK", reason)
        try:
            _require(self.port.commit() is None, "PORT_COMMIT_CONTRACT")
        except BaseException:
            # 即使随后 rollback 成功也不能推定 COMMIT 未生效，所以此处绝不 rollback。
            return self._finish(receipt, outcome_path, "UNKNOWN", "COMMIT_OUTCOME_UNKNOWN")
        return self._finish(receipt, outcome_path, "COMMITTED")


def main(argv=None) -> int:
    """默认且唯一 CLI 模式是离线计划/排他备份；无连接参数、执行或重试开关。"""
    parser = argparse.ArgumentParser(description="七个旧单原回执出生元数据恢复：纯离线计划和备份")
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--backup", required=True, type=Path)
    parser.add_argument("--expected-order-id", action="append", default=[])
    args = parser.parse_args(argv)
    try:
        snapshot = json.loads(args.snapshot.read_text(encoding="utf-8-sig"), object_pairs_hook=_legacy._no_duplicate_keys)
        plan = plan_restore(snapshot, args.expected_order_id)
        write_backup(snapshot, args.expected_order_id, args.backup)
    except _legacy.BackfillRefused as failure:
        print(json.dumps(dict(status="REFUSED", reason=failure.code, retryAllowed=False)))
        return 2
    except (OSError, ValueError, UnicodeError, RecursionError):
        print(json.dumps(dict(status="REFUSED", reason="INVALID_OR_UNREADABLE_SNAPSHOT", retryAllowed=False)))
        return 2
    print(json.dumps(plan, ensure_ascii=True, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
