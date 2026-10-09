"""Task4：拒绝未知状态、纯离线、严格限定七条旧订单的出生版本补缀计划器。

不包含数据库驱动、连接、网络、将未知版本默认读为零的逻辑或可执行数据库操作的 CLI。
``main`` 只校验 JSON、保存原始证据备份并输出计划。
只有经独立审查的调用方才能注入事务 port。
任何 SQL 均不得修改金额、状态、数量、update_time 或任何指纹。

严格 snapshot 合同（全部键详见 *_FIELDS/COLUMN_CONTRACT）：
  environment: {host: localhost|127.0.0.1, transport: TCP, port: 3306（整数）,
                schema: ry-erp, profile: dev, tenant: 000000}
  authorization: {scope: LOCAL_SOFTWARE_BUSINESS_DATA_COMPLETION,
                  allowedOrderIds: [恰好七个十进制字符串 ID],
                  allowedChange: certified_version:NULL->0, production: false}
  metadata: {tables: {必需的原生表: InnoDB},
             columns: {table.column: {dataType,precision,scale,maxLength,nullable}},
             uniqueKeys: {table: [{indexName,nonUnique:0,
                                   parts:[{columnName,sequence,subPart:null}]}]},
             nativeColumns: {pu_purchase_order: [全部原生列名],
                             pu_purchase_order_line: [全部原生列名]},
             orderUpdateTriggers: [], orderAutomaticUpdates: []}
  orders: [恰好七条 {
    nativeVersion: null, head: {...HEAD_FIELDS}, lines: [{...LINE_FIELDS}],
    requisition: {head: {...REQ_HEAD_FIELDS}, lines: [{...REQ_LINE_FIELDS}]},
    command: {...COMMAND_FIELDS}, ledger: [{...LEDGER_FIELDS}],
    events: [{...EVENT_FIELDS}], mrp: [{...MRP_FIELDS}], rootUnit: {...ROOT_FIELDS},
    absence: {ABSENCE 中的每个域: []},
    fingerprintEvidence: {expectedSourceFingerprint: 摘要|null, runId,actionId,
        contract: {payloadFingerprint,metadata: 九个 CONTRACT_FIELDS 字段,rootWorkOrderId},
        sourceCanonicalFields: [11 个原始 Java canonical 字符串],
        catalogCanonicalFields: [10 个原始 Java canonical 字符串],
        immutableVersionBindings: [{kind,table,rowId,column,value}, ...]},
    nativeRows: {head: 全部未遮蔽的原生头列（含 certified_version）,
                 lines: [全部未遮蔽的原生行列]}
  }]

ID 必须是规范的正十进制字符串，不超过有符号 Java long 上限，绝不接受浮点数；
所有金额和数量必须是非负的普通 DECIMAL 文本，不能使用 JSON 数值。
金额可为 null 或真实零值；收货／退货／开票数量必须明确存在且为零。
未知、缺失、额外的投影键及未知来源一律拒绝。
原生订单头／行必须精确匹配已审查的 41／28 列基线；
contract_id、contract_no 及不透明的 attr1..4 必须全部为 NULL。全部原生行
字节（包括 update_time）都要备份，并在 CAS 写入后进行比较。
原生根单位 conversion_version 是 VARCHAR 文本，绝不是默认整数值。
MRP 行将当前原生身份绑定到原始冻结的基本单位覆盖量，
不是重算后的当前净需求量。适配器必须复制完整、未经隐藏或省略的
行、行数及覆盖关系、原生元数据、完整唯一索引（不得使用前缀索引），
以及原始不可变指纹前像。不透明摘要本身不等于前像；
不能从当前占用量合成缺失证据。空 absence 数组必须表示完整原生读取成功，
并包含已删除／历史行的检查，不能表示 API 失败、过滤后的局部查询，
也不能仅凭客户端 verified=true 标志充当证明。

已审阅的当前 Java 合同：workflow 的 ``load``／来源守卫；
PuCertifiedPurchaseWriteService 的请求／结果摘要；
ErpPurchaseSourceCertificationService 的 source／catalog canonical 摘要；原始 CREATE/APPROVE/TRANSFER 历史。
请求／结果摘要公式不包含订单 certified_version。新建
原生订单明确写入 DRAFT/v0；workflow 的 orderFingerprint 也不包含
版本。这不证明任意历史都兼容：任何原始不可变依赖
只要绑定目标 certified_version，就必须拒绝。原始
指纹／前像始终保持不变，包括七条 CAS 写入之后。
此前审阅的私有只读快照缺少请求摘要、完整事件／金额／下游空集证据；
直接提供这些摘要应被拒绝，不能静默补齐。

port 合同（所有调用共享同一个独占持有的本机 ERP 事务）：
  begin() -> None：可重复读或更强的隔离级别，使用可回滚的 InnoDB；
  read_locked(ids: tuple[str,...]) -> 严格限定这七条的完整 snapshot：
    原生列、身份、command、根单位、申请、账本、事件、
    MRP、元数据以及全部下游空集谓词，都要加锁以防并发插入。
    结构和证据顺序必须与原始备份一致，不包含易变的读取时钟。
    第一次调用必须匹配备份字节摘要；CAS 后的第二次调用只允许
    nativeVersion/nativeRows.head.certified_version 从 NULL 变为整数 0。
  cas_set_birth_zero(sql: str, bindings: tuple[tenant,order_id]) -> int：
    必须执行传入的精确固定语句；原生 rowcount 必须为整数 1。
  commit() -> None; rollback() -> None。
适配器不得经由 IFNULL workflow 查询，也不得在构造证据时修改数据。
必须在 begin 之前独立持久化以 snapshotSha256 为键的
执行意图／结果。只有调用方核对这份持久历史之后，才能
显式传入 prior_outcome=NEW；遗漏或未知的历史
必须拒绝。重启时通过 prior_outcome 传入已保存的结果；
UNKNOWN 绝不能重试，也不能以新意图重新创建执行。本模块
不实现外部持久结果存储，也不宣称数据库操作已经成功。
BackfillExecutor 只允许一次尝试，即使拒绝／回滚后也不能复用；commit/rollback
异常返回 UNKNOWN，不能用后续回滚或自动重试掩盖不确定结果。
备份 JSON 属于私有资料；计划／回执只包含不透明摘要、固定 SQL、
绑定值及固定原因码。绝不记录异常消息原文或原始证据。
"""
from __future__ import annotations

import argparse
import copy
from datetime import date, datetime
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import struct
from typing import Protocol

TENANT = "000000"
SQL = ("UPDATE pu_purchase_order SET certified_version=0 WHERE tenant_id=? "
       "AND order_id=? AND certified_version IS NULL AND status='DRAFT'")
ABSENCE = frozenset(("terms", "quoteConfirmation", "orderOperation", "arrival", "receive",
                    "return", "stockTransaction", "birth", "invoice", "flowable",
                    "extraOrderLines", "extraAllocations"))
REQUIRED_TABLES = frozenset(("pu_purchase_order", "pu_purchase_order_line", "pu_purchase_requisition",
    "pu_purchase_requisition_line", "pu_certified_purchase_command", "pu_requirement_allocation_ledger",
    "pu_requirement_allocation_event", "pu_certified_purchase_order_terms",
    "pu_certified_purchase_order_terms_confirmation", "pu_certified_purchase_order_operation",
    "wm_arrival_notice", "wm_arrival_notice_line", "wm_material_receive", "wm_material_receive_line",
    "wm_material_stock_tx", "pu_purchase_return", "pu_purchase_return_line", "wm_receipt_iqc_birth",
    "bd_unit_of_measure", "prd_material_requirement_detail"))
UNIQUE_KEYS = {
    "pu_certified_purchase_command": (("tenant_id", "request_key"),),
    "pu_requirement_allocation_ledger": (("tenant_id", "allocation_key"),),
    "pu_requirement_allocation_event": (("tenant_id", "event_key"), ("tenant_id", "allocation_id", "event_sequence")),
    "pu_certified_purchase_order_terms": (("quote_id",), ("tenant_id", "request_key"), ("tenant_id", "order_id", "quote_version")),
    "pu_certified_purchase_order_terms_confirmation": (("confirmation_id",), ("tenant_id", "request_key"), ("tenant_id", "quote_id")),
    "pu_certified_purchase_order_operation": (("command_id",), ("tenant_id", "request_key"), ("tenant_id", "order_id", "operation")),
}
# 对齐原生来源守卫的完整订单列合同，并包含物理影响来源的
# DECIMAL 合同。历史版本列允许 NULL 是刻意保留的要求。
COLUMN_CONTRACT: dict[str, tuple[str, int, int, int, bool]] = {}


def _columns(table, dtype, precision, scale, length, not_null, names):
    for name in names.split(","):
        COLUMN_CONTRACT[table + "." + name] = (dtype, precision, scale, length, not_null)


_columns("pu_certified_purchase_order_terms", "bigint", 0, 0, 0, True, "quote_id,order_id,entered_by,vendor_id,material_id,base_uom_id")
_columns("pu_certified_purchase_order_terms", "int", 0, 0, 0, True, "quote_version")
_columns("pu_certified_purchase_order_terms", "datetime", 0, 0, 0, True, "entered_at")
_columns("pu_certified_purchase_order_terms", "decimal", 18, 4, 0, True, "quantity,unit_price_excluding_tax")
_columns("pu_certified_purchase_order_terms", "decimal", 10, 4, 0, True, "tax_rate")
_columns("pu_certified_purchase_order_terms", "decimal", 18, 6, 0, True, "exchange_rate")
_columns("pu_certified_purchase_order_terms", "date", 0, 0, 0, True, "promised_arrival_date,valid_from,valid_until")
_columns("pu_certified_purchase_order_terms", "varchar", 0, 0, 128, True, "supplier_quote_reference,supplier_contact,request_key,source_request_key")
_columns("pu_certified_purchase_order_terms", "varchar", 0, 0, 255, True, "evidence_reference,payment_terms,delivery_terms")
_columns("pu_certified_purchase_order_terms", "varchar", 0, 0, 20, True, "tenant_id")
_columns("pu_certified_purchase_order_terms", "varchar", 0, 0, 16, True, "currency")
_columns("pu_certified_purchase_order_terms", "varchar", 0, 0, 64, True, "rounding_policy")
_columns("pu_certified_purchase_order_terms", "char", 0, 0, 64, True, "request_hash,source_fingerprint,catalog_fingerprint,order_fingerprint,evidence_sha256,quote_hash")
_columns("pu_certified_purchase_order_terms_confirmation", "bigint", 0, 0, 0, True, "confirmation_id,quote_id,actor_id")
_columns("pu_certified_purchase_order_terms_confirmation", "datetime", 0, 0, 0, True, "confirmed_at")
_columns("pu_certified_purchase_order_terms_confirmation", "varchar", 0, 0, 500, True, "review_note")
_columns("pu_certified_purchase_order_terms_confirmation", "varchar", 0, 0, 20, True, "tenant_id")
_columns("pu_certified_purchase_order_terms_confirmation", "varchar", 0, 0, 128, True, "request_key")
_columns("pu_certified_purchase_order_terms_confirmation", "varchar", 0, 0, 64, True, "permission_code")
_columns("pu_certified_purchase_order_terms_confirmation", "char", 0, 0, 64, True, "quote_hash,request_hash")
_columns("pu_certified_purchase_order_operation", "bigint", 0, 0, 0, True, "command_id,order_id,actor_id")
_columns("pu_certified_purchase_order_operation", "int", 0, 0, 0, True, "expected_version,result_version")
_columns("pu_certified_purchase_order_operation", "bigint", 0, 0, 0, False, "quote_id")
_columns("pu_certified_purchase_order_operation", "date", 0, 0, 0, False, "expected_arrival_date")
_columns("pu_certified_purchase_order_operation", "decimal", 18, 2, 0, False, "total_amount,amount_excluding_tax,tax_amount")
_columns("pu_certified_purchase_order_operation", "datetime", 0, 0, 0, True, "occurred_at")
_columns("pu_certified_purchase_order_operation", "varchar", 0, 0, 20, True, "tenant_id")
_columns("pu_certified_purchase_order_operation", "varchar", 0, 0, 128, True, "request_key")
_columns("pu_certified_purchase_order_operation", "varchar", 0, 0, 32, True, "operation,result_status")
_columns("pu_certified_purchase_order_operation", "char", 0, 0, 64, True, "request_hash,result_fingerprint")
_columns("pu_certified_purchase_order_operation", "char", 0, 0, 64, False, "quote_hash")
_columns("pu_purchase_order", "bigint", 0, 0, 0, False, "order_id,requisition_id,vendor_id,select_flow_id,certified_terms_id")
_columns("pu_purchase_order", "varchar", 0, 0, 64, False, "order_no")
_columns("pu_purchase_order", "datetime", 0, 0, 0, False, "expected_arrival_date")
_columns("pu_purchase_order", "varchar", 0, 0, 16, False, "currency")
_columns("pu_purchase_order", "decimal", 18, 6, 0, False, "exchange_rate")
_columns("pu_purchase_order", "decimal", 18, 2, 0, False, "total_amount,amount_excluding_tax,tax_amount,discount_amount,freight_amount")
_columns("pu_purchase_order", "decimal", 10, 2, 0, False, "discount_rate")
_columns("pu_purchase_order", "varchar", 0, 0, 255, False, "payment_terms,delivery_terms")
_columns("pu_purchase_order", "varchar", 0, 0, 32, False, "status")
_columns("pu_purchase_order", "varchar", 0, 0, 20, False, "tenant_id")
_columns("pu_purchase_order", "char", 0, 0, 1, False, "del_flag")
_columns("pu_purchase_order", "int", 0, 0, 0, False, "certified_version")
_columns("pu_purchase_order_line", "bigint", 0, 0, 0, False, "line_id,order_id,requisition_line_id,material_id")
_columns("pu_purchase_order_line", "varchar", 0, 0, 32, False, "unit,receive_status")
_columns("pu_purchase_order_line", "decimal", 18, 4, 0, False, "order_quantity,received_quantity,invoiced_qty,returned_quantity,unit_price,unit_price_excluding_tax")
_columns("pu_purchase_order_line", "decimal", 10, 4, 0, False, "tax_rate")
_columns("pu_purchase_order_line", "decimal", 18, 2, 0, False, "line_amount,line_amount_excluding_tax,line_tax_amount")
_columns("pu_purchase_order_line", "varchar", 0, 0, 20, False, "tenant_id")
_columns("pu_purchase_order_line", "char", 0, 0, 1, False, "del_flag")
for _table, _names in (("wm_material_receive_line", "quantity_received"),
                       ("wm_material_stock_tx", "tx_quantity,before_quantity,after_quantity"),
                       ("pu_purchase_return_line", "return_quantity")):
    _columns(_table, "decimal", 18, 4, 0, False, _names)

HEAD_MONEY = "total_amount amount_excluding_tax tax_amount discount_amount freight_amount discount_rate".split()
LINE_MONEY = "unit_price unit_price_excluding_tax tax_rate line_amount line_amount_excluding_tax line_tax_amount".split()
HEAD_FIELDS = frozenset("tenant_id order_id order_no requisition_id vendor_id status del_flag certified_terms_id expected_arrival_date select_flow_id currency exchange_rate payment_terms delivery_terms create_time update_time".split() + HEAD_MONEY)
LINE_FIELDS = frozenset("tenant_id order_id line_id requisition_line_id material_id unit order_quantity received_quantity returned_quantity invoiced_qty receive_status del_flag".split() + LINE_MONEY)
REQ_HEAD_FIELDS = frozenset("tenant_id requisition_id requisition_no source_type status version del_flag request_key select_flow_id create_time".split())
REQ_LINE_FIELDS = frozenset("tenant_id requisition_id line_id material_id unit apply_quantity approved_quantity source_type source_id required_date del_flag".split())
COMMAND_FIELDS = frozenset("tenant_id request_key command_status actor_id entry_type material_id vendor_id window_end requisition_id requisition_no request_hash source_fingerprint catalog_fingerprint result_fingerprint".split())
LEDGER_FIELDS = frozenset("tenant_id allocation_id material_id detail_id requisition_line_id order_line_id source_type allocation_no base_uom_id covered_demand_base_qty occupied_base_qty fulfilled_base_qty released_base_qty version allocation_status allocation_key decision_idempotency_key release_reason create_time update_time transfer_time".split())
EVENT_FIELDS = frozenset("tenant_id event_id allocation_id event_sequence event_key event_type from_status to_status occupied_base_before occupied_base_after requisition_line_id order_line_id actor_id occurred_at".split())
MRP_FIELDS = frozenset("tenant_id detail_id material_id base_uom_id covered_demand_base_qty del_flag".split())
ROOT_FIELDS = frozenset("tenant_id uom_id is_base primary_id quantity_scale conversion_rate conversion_version conversion_verified_at del_flag".split())
CONTRACT_FIELDS = frozenset("materialVersion vendorVersion supplierTermsId supplierTermsVersion baseUomId baseUomVersion orderUomId orderUomVersion conversionVersion".split())
ORDER_FIELDS = frozenset("nativeVersion head lines requisition command ledger events mrp rootUnit absence fingerprintEvidence nativeRows".split())

# 基线来自获准读取的 2026-10-09 原生元数据快照，复制的仅是列名。
# 出现未知新增列时，必须重新进行独立审查。
NATIVE_COLUMNS = {
    "pu_purchase_order": frozenset("order_id order_no requisition_id requisition_no vendor_id vendor_code vendor_name order_date expected_arrival_date currency exchange_rate total_amount amount_excluding_tax tax_amount discount_amount freight_amount discount_rate payment_terms delivery_terms status remark attr1 attr2 attr3 attr4 tenant_id create_dept create_by create_time update_by update_time del_flag select_flow_id contract_id contract_no dept_id dept_name buyer_id buyer_name certified_version certified_terms_id".split()),
    "pu_purchase_order_line": frozenset("line_id order_id requisition_line_id material_id material_code material_name material_spec unit order_quantity received_quantity invoiced_qty returned_quantity receive_status unit_price unit_price_excluding_tax tax_rate line_amount line_amount_excluding_tax line_tax_amount sort_order remark tenant_id create_dept create_by create_time update_by update_time del_flag".split()),
}





_INTERNAL_REASONS = frozenset(
    "AI_DECISION APPROVE AUTHORIZATION_OUT_OF_SCOPE BACKUP_CONFLICT BACKUP_MISSING BACKUP_WRITE_FAILED CAUSAL_EVENT_COVERAGE CAUSAL_EVENT_IDENTITY CAUSAL_QUANTITY_DRIFT CAUSAL_TIME_DRIFT CERTIFIED_ROOT_CONTRACT CNY COMMITTED COMMIT_OUTCOME_UNKNOWN CREATE CREATE_ACTOR_MISMATCH CURRENCY_CONTRACT DECIMAL_PRECISION DECIMAL_TEXT_REQUIRED DIGEST_MISSING_OR_INVALID DOWNSTREAM_UNKNOWN DRAFT DUPLICATE_JSON_KEY DUPLICATE_SOURCE ENVIRONMENT_OUT_OF_SCOPE EXACT_ID_OR_TENANT EXACT_SEVEN_SCOPE EXISTING_OR_UNKNOWN_DOWNSTREAM EXISTING_TERMS_OR_FLOW FINGERPRINT_PREIMAGE FROZEN_QUANTITY_OR_DATE FROZEN_ROOT_MISMATCH ID_FORMAT IMMUTABLE_VERSION_CONFLICT INCOMPLETE_OR_INVALID_PROOF INVALID_JSON_EVIDENCE INVALID_NATIVE_DATE INVALID_NATIVE_TIME INVALID_OR_UNREADABLE_SNAPSHOT LEDGER_COVERAGE LEDGER_IDENTITY LEDGER_QUANTITY_DRIFT LOCAL_SOFTWARE_BUSINESS_DATA_COMPLETION LOCKED_SNAPSHOT_DRIFT MALFORMED_SNAPSHOT MRP MRP_DETAIL MRP_IDENTITY_OR_QUANTITY NATIVE_CAUSAL_TIME NATIVE_COLUMN_CONTRACT NATIVE_CREATION_IDENTITY NATIVE_DECIMAL_CONTRACT NATIVE_FULL_ROW_MISSING NATIVE_METADATA_UNKNOWN NATIVE_PROJECTION_MISMATCH NATIVE_ROW_COLUMNS NATIVE_UNIQUE_KEYS NATIVE_VERSION_NOT_NULL NEW NO NON_JSON_OR_FLOAT_EVIDENCE NON_TRANSACTIONAL_SOURCE NOT_ORIGINAL_DRAFT NOT_UNPRICED_OR_ZERO ONE_SHOT_NO_RETRY ORDERED ORDER_AUTOMATIC_UPDATE ORDER_COMMITTED_NOT_TIMELY_SUPPLY ORDER_LINE_IDENTITY ORDER_UPDATE_TRIGGER ORIGINAL_CANONICAL_PREIMAGE_MISSING ORIGINAL_FINGERPRINT_MISMATCH PENDING PERMANENT_COMMAND_CONTRACT PLANNED PORT_BEGIN_CONTRACT PORT_COMMIT_CONTRACT PORT_EXCEPTION PORT_ROLLBACK_CONTRACT POSTWRITE_DRIFT PRIOR_OUTCOME_NO_RETRY QUANTITY_DRIFT REFUSED REJECTED REQUEST_INTENT REQUISITION_CONTRACT REQUISITION_LINE_IDENTITY ROLLBACK_OUTCOME_UNKNOWN ROLLED_BACK ROOT_QUANTITY_SCALE ROWCOUNT_NOT_ONE ROW_COVERAGE RUNNING SNAPSHOT_SHAPE TCP TENANT_MISMATCH TRANSFER_TO_ORDER UNKNOWN UNKNOWN_EXTRA_BUSINESS_SOURCE UNKNOWN_IMMUTABLE_DEPENDENCIES UNTRUSTED_REASON VALIDATING WORK_ORDER YES".split()
)


class BackfillRefused(ValueError):
    """仅使用固定、安全的原因码，绝不包含私有字段值或驱动异常原文。"""
    def __init__(self, code: str):
        self.code = code if type(code) is str and code in _INTERNAL_REASONS else "UNTRUSTED_REASON"
        super().__init__("Legacy birth backfill refused: " + self.code)


def _require(condition, code="INCOMPLETE_OR_INVALID_PROOF"):
    if not condition:
        raise BackfillRefused(code)


def _shape(value, keys, code="SNAPSHOT_SHAPE"):
    _require(type(value) is dict and set(value) == set(keys), code)
    return value


def _text(value, max_length=256):
    _require(type(value) is str and 0 < len(value) <= max_length and value == value.strip()
             and not any(ord(c) < 32 or ord(c) == 127 for c in value))
    return value


def _id(value):
    _require(type(value) is str and re.fullmatch(r"[1-9][0-9]{0,18}", value) is not None
             and int(value) <= 9223372036854775807, "ID_FORMAT")
    return value


def _int(value, minimum=0, maximum=2147483646):
    _require(type(value) is int and minimum <= value <= maximum)
    return value


def _decimal(value, precision=18, scale=4):
    _require(type(value) is str and re.fullmatch(r"(?:0|[1-9][0-9]*)(?:\.[0-9]+)?", value) is not None,
             "DECIMAL_TEXT_REQUIRED")
    number = Decimal(value)
    whole, _, fraction = value.partition(".")
    _require(len(whole.lstrip("0")) <= precision - scale and len(fraction) <= scale, "DECIMAL_PRECISION")
    return number


def _quantity(value, root_scale):
    # 原生 DECIMAL(18,4) 即使面对整数根单位，也会返回 "22.0000"。
    # 为摘要保留原始字节；按 Java 守卫比较数值本身的小数位数。
    number = _decimal(value, 18, 4)
    fraction = value.partition(".")[2].rstrip("0")
    _require(len(fraction) <= root_scale, "ROOT_QUANTITY_SCALE")
    return number


def _zero(value, nullable=False, precision=18, scale=4):
    if nullable and value is None:
        return
    _require(_decimal(value, precision, scale) == 0, "NOT_UNPRICED_OR_ZERO")


def _day(value):
    _require(type(value) is str and re.fullmatch(r"\d{4}-\d{2}-\d{2}", value) is not None)
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise BackfillRefused("INVALID_NATIVE_DATE") from None


def _time(value):
    _require(type(value) is str and re.fullmatch(r"\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.[0-9]{1,6})?", value) is not None)
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        raise BackfillRefused("INVALID_NATIVE_TIME") from None


def _digest(value):
    _require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None, "DIGEST_MISSING_OR_INVALID")
    return value


def _hash(*fields):
    h = hashlib.sha256()
    for field in fields:
        _require(type(field) is str, "FINGERPRINT_PREIMAGE")
        raw = field.encode("utf-8")
        h.update(struct.pack(">I", len(raw)))
        h.update(raw)
    return h.hexdigest()


def _number(value):
    text = format(_decimal(value), "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def _json_bytes(value):
    def check(node):
        _require(type(node) in (dict, list, str, int, bool, type(None)), "NON_JSON_OR_FLOAT_EVIDENCE")
        if type(node) is dict:
            _require(all(type(k) is str for k in node), "NON_JSON_OR_FLOAT_EVIDENCE")
            for v in node.values():
                check(v)
        elif type(node) is list:
            for v in node:
                check(v)
    check(value)
    try:
        return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    except (ValueError, UnicodeError):
        raise BackfillRefused("INVALID_JSON_EVIDENCE") from None


def _sha(value):
    return hashlib.sha256(_json_bytes(value)).hexdigest()


def _scope(expected_order_ids):
    _require(type(expected_order_ids) in (list, tuple) and len(expected_order_ids) == 7, "EXACT_SEVEN_SCOPE")
    ids = tuple(_id(v) for v in expected_order_ids)
    _require(len(set(ids)) == 7, "EXACT_SEVEN_SCOPE")
    return tuple(sorted(ids, key=int))


def _rows(rows, fields, identity):
    _require(type(rows) is list and 0 < len(rows) <= 1000, "ROW_COVERAGE")
    by_id = {}
    for row in rows:
        _shape(row, fields)
        _require(row["tenant_id"] == TENANT, "TENANT_MISMATCH")
        key = _id(row[identity])
        _require(key not in by_id, "DUPLICATE_SOURCE")
        by_id[key] = row
    return by_id


def _metadata_structure(metadata):
    """只验证共同物理结构；自动更新许可由各自入口的明确策略决定。"""
    _shape(metadata, ("tables", "columns", "uniqueKeys", "nativeColumns", "orderUpdateTriggers", "orderAutomaticUpdates"))
    _shape(metadata["tables"], REQUIRED_TABLES, "NATIVE_METADATA_UNKNOWN")
    _require(all(v == "InnoDB" for v in metadata["tables"].values()), "NON_TRANSACTIONAL_SOURCE")
    _shape(metadata["columns"], COLUMN_CONTRACT, "NATIVE_COLUMN_CONTRACT")
    for key, spec in COLUMN_CONTRACT.items():
        column = _shape(metadata["columns"][key], ("dataType", "precision", "scale", "maxLength", "nullable"))
        dtype, precision, scale, length, not_null = spec
        _require(column["dataType"] == dtype and column["nullable"] in ("YES", "NO"), "NATIVE_COLUMN_CONTRACT")
        for value in (column["precision"], column["scale"], column["maxLength"]):
            _int(value, 0, 65535)
        _require(not not_null or column["nullable"] == "NO", "NATIVE_COLUMN_CONTRACT")
        if dtype == "decimal":
            _require((column["precision"], column["scale"]) == (precision, scale), "NATIVE_DECIMAL_CONTRACT")
        if length:
            _require(column["maxLength"] == length, "NATIVE_COLUMN_CONTRACT")
    _shape(metadata["uniqueKeys"], UNIQUE_KEYS)
    for table, required in UNIQUE_KEYS.items():
        indexes = metadata["uniqueKeys"][table]
        _require(type(indexes) is list and len(indexes) > 0, "NATIVE_UNIQUE_KEYS")
        found = set()
        names_seen = set()
        for index in indexes:
            _shape(index, ("indexName", "nonUnique", "parts"), "NATIVE_UNIQUE_KEYS")
            name = _text(index["indexName"], 64)
            _require(name not in names_seen and type(index["nonUnique"]) is int
                     and index["nonUnique"] == 0, "NATIVE_UNIQUE_KEYS")
            names_seen.add(name)
            parts = index["parts"]
            _require(type(parts) is list and len(parts) > 0, "NATIVE_UNIQUE_KEYS")
            columns = []
            for sequence, part in enumerate(parts, 1):
                _shape(part, ("columnName", "sequence", "subPart"), "NATIVE_UNIQUE_KEYS")
                _require(type(part["sequence"]) is int and part["sequence"] == sequence
                         and part["subPart"] is None, "NATIVE_UNIQUE_KEYS")
                columns.append(_text(part["columnName"], 64))
            _require(len(columns) == len(set(columns)), "NATIVE_UNIQUE_KEYS")
            found.add(tuple(columns))
        _require(set(required).issubset(found), "NATIVE_UNIQUE_KEYS")
    _shape(metadata["nativeColumns"], ("pu_purchase_order", "pu_purchase_order_line"))
    for table, required in (("pu_purchase_order", HEAD_FIELDS | {"certified_version"}), ("pu_purchase_order_line", LINE_FIELDS)):
        names = metadata["nativeColumns"][table]
        _require(type(names) is list, "NATIVE_ROW_COLUMNS")
        for name in names:
            _text(name, 64)
        _require(len(names) == len(set(names)) and set(names) == NATIVE_COLUMNS[table]
                 and required.issubset(set(names)), "NATIVE_ROW_COLUMNS")


def _metadata(metadata):
    """原 full-preimage 策略不变：仍拒绝所有触发器和自动更新列。"""
    _metadata_structure(metadata)
    _require(type(metadata["orderUpdateTriggers"]) is list and not metadata["orderUpdateTriggers"], "ORDER_UPDATE_TRIGGER")
    _require(type(metadata["orderAutomaticUpdates"]) is list and not metadata["orderAutomaticUpdates"], "ORDER_AUTOMATIC_UPDATE")


def _fingerprints(order, ids, req_lines):
    command, proof = order["command"], order["fingerprintEvidence"]
    _shape(proof, ("expectedSourceFingerprint", "runId", "actionId", "contract", "sourceCanonicalFields",
                   "catalogCanonicalFields", "immutableVersionBindings"))
    for field in ("request_hash", "source_fingerprint", "catalog_fingerprint", "result_fingerprint"):
        _digest(command[field])
    bindings = proof["immutableVersionBindings"]
    _require(type(bindings) is list, "UNKNOWN_IMMUTABLE_DEPENDENCIES")
    for binding in bindings:
        _shape(binding, ("kind", "table", "rowId", "column", "value"))
        _require(binding["kind"] in ("request", "result", "source", "catalog"))
        _text(binding["table"], 64)
        _text(binding["column"], 64)
        _id(binding["rowId"])
        _require(not (binding["table"] == "pu_purchase_order" and binding["column"] == "certified_version"
                      and binding["rowId"] in ids), "IMMUTABLE_VERSION_CONFLICT")
    for name, count, stored in (("sourceCanonicalFields", 11, "source_fingerprint"),
                                ("catalogCanonicalFields", 10, "catalog_fingerprint")):
        fields = proof[name]
        _require(type(fields) is list and len(fields) == count and all(type(s) is str and s for s in fields),
                 "ORIGINAL_CANONICAL_PREIMAGE_MISSING")
        _require(_hash(*fields) == command[stored], "ORIGINAL_FINGERPRINT_MISMATCH")
    expected = proof["expectedSourceFingerprint"]
    if expected is not None:
        _require(_digest(expected) == command["source_fingerprint"], "ORIGINAL_FINGERPRINT_MISMATCH")
    run, action = _id(proof["runId"]), _id(proof["actionId"])
    contract = _shape(proof["contract"], ("payloadFingerprint", "metadata", "rootWorkOrderId"))
    fields = [_digest(contract["payloadFingerprint"])]
    _shape(contract["metadata"], CONTRACT_FIELDS)
    for key, value in sorted(contract["metadata"].items()):
        fields.extend((key, _text(value)))
    for key in ("baseUomId", "orderUomId"):
        _require(contract["metadata"][key] == "26", "FROZEN_ROOT_MISMATCH")
    for key in ("baseUomVersion", "orderUomVersion", "conversionVersion"):
        _require(contract["metadata"][key] == order["rootUnit"]["conversion_version"], "FROZEN_ROOT_MISMATCH")
    if contract["rootWorkOrderId"] is not None:
        fields.extend(("WORK_ORDER", _id(contract["rootWorkOrderId"])))
    sorted_lines = sorted(req_lines.values(), key=lambda r: int(r["source_id"]))
    frozen, result = [], []
    for row in sorted_lines:
        n, day = _number(row["apply_quantity"]), row["required_date"]
        frozen.append(_hash(row["source_id"], row["unit"], n, row["unit"], n, day))
        result.append(_hash(row["line_id"], row["source_id"], row["material_id"], row["unit"], n, day))
    request_hash = _hash(TENANT, command["actor_id"], command["entry_type"], command["request_key"],
        command["material_id"], command["vendor_id"], command["window_end"],
        "".join("," + r["source_id"] for r in sorted_lines), expected if expected is not None else "",
        run, action, _hash(*frozen), _hash(*fields))
    _require(request_hash == command["request_hash"] and _hash(*result) == command["result_fingerprint"],
             "ORIGINAL_FINGERPRINT_MISMATCH")


def _order_structure(order, ids, metadata):
    """共享完整原生结构/金额/因果门禁，不签发任何指纹来源证明。"""
    _shape(order, ORDER_FIELDS)
    _require(order["nativeVersion"] is None, "NATIVE_VERSION_NOT_NULL")
    head, command = _shape(order["head"], HEAD_FIELDS), _shape(order["command"], COMMAND_FIELDS)
    oid = _id(head["order_id"])
    _require(oid in ids and head["tenant_id"] == TENANT and command["tenant_id"] == TENANT, "EXACT_ID_OR_TENANT")
    req, vendor, material = _id(head["requisition_id"]), _id(head["vendor_id"]), _id(command["material_id"])
    _text(head["order_no"], 64)
    _require(head["status"] == "DRAFT" and head["del_flag"] == "0", "NOT_ORIGINAL_DRAFT")
    for field in ("certified_terms_id", "expected_arrival_date", "select_flow_id", "payment_terms", "delivery_terms"):
        _require(head[field] is None, "EXISTING_TERMS_OR_FLOW")
    _require(head["currency"] == "CNY" and _decimal(head["exchange_rate"], 18, 6) == 1, "CURRENCY_CONTRACT")
    for field in HEAD_MONEY:
        _zero(head[field], True, 10 if field == "discount_rate" else 18, 2)
    order_created, order_updated = _time(head["create_time"]), _time(head["update_time"])
    _require(order_updated >= order_created)
    _shape(order["absence"], ABSENCE, "DOWNSTREAM_UNKNOWN")
    _require(all(type(rows) is list and not rows for rows in order["absence"].values()), "EXISTING_OR_UNKNOWN_DOWNSTREAM")
    requisition = _shape(order["requisition"], ("head", "lines"))
    rh = _shape(requisition["head"], REQ_HEAD_FIELDS)
    _require(rh["tenant_id"] == TENANT and rh["requisition_id"] == req and rh["status"] == "ORDERED"
             and type(rh["version"]) is int and rh["version"] == 2 and rh["source_type"] == "AI_DECISION"
             and rh["del_flag"] == "0" and rh["select_flow_id"] is None, "REQUISITION_CONTRACT")
    _text(rh["requisition_no"], 64)
    key = _text(rh["request_key"], 128)
    _require(command["command_status"] == "COMMITTED" and command["entry_type"] == rh["source_type"]
             and command["requisition_id"] == req and command["requisition_no"] == rh["requisition_no"]
             and command["vendor_id"] == vendor and command["request_key"] == key, "PERMANENT_COMMAND_CONTRACT")
    _id(command["actor_id"])
    window = _day(command["window_end"])
    unit = _shape(order["rootUnit"], ROOT_FIELDS)
    _require(unit["tenant_id"] == TENANT and unit["uom_id"] == "26" and type(unit["is_base"]) is int
             and unit["is_base"] == 1 and unit["primary_id"] in (None, "26") and unit["del_flag"] == "0"
             and _decimal(unit["conversion_rate"], 18, 6) == 1, "CERTIFIED_ROOT_CONTRACT")
    scale = _int(unit["quantity_scale"], 0, 4)
    _text(unit["conversion_version"], 64)
    _time(unit["conversion_verified_at"])
    req_lines = _rows(requisition["lines"], REQ_LINE_FIELDS, "line_id")
    lines = _rows(order["lines"], LINE_FIELDS, "line_id")
    ledgers = _rows(order["ledger"], LEDGER_FIELDS, "allocation_id")
    mrp = _rows(order["mrp"], MRP_FIELDS, "detail_id")
    events = _rows(order["events"], EVENT_FIELDS, "event_id")
    _require(len(lines) == len(req_lines) == len(ledgers) == len(mrp) and len(events) == 3 * len(lines), "ROW_COVERAGE")
    by_req, by_detail, ledger_by_req = {}, {}, {}
    for rl in req_lines.values():
        detail = _id(rl["source_id"])
        _require(detail not in by_detail and rl["requisition_id"] == req and rl["material_id"] == material
                 and rl["unit"] == "26" and rl["source_type"] == "MRP" and rl["del_flag"] == "0", "REQUISITION_LINE_IDENTITY")
        qty = _quantity(rl["apply_quantity"], scale)
        _require(qty > 0 and _quantity(rl["approved_quantity"], scale) == qty and _day(rl["required_date"]) <= window,
                 "FROZEN_QUANTITY_OR_DATE")
        by_detail[detail] = rl
    for line in lines.values():
        rid = _id(line["requisition_line_id"])
        _require(rid in req_lines and rid not in by_req and line["order_id"] == oid
                 and line["material_id"] == material and line["unit"] == "26" and line["del_flag"] == "0"
                 and line["receive_status"] == "PENDING", "ORDER_LINE_IDENTITY")
        _require(_quantity(line["order_quantity"], scale) == _quantity(req_lines[rid]["apply_quantity"], scale), "QUANTITY_DRIFT")
        for field in ("received_quantity", "returned_quantity", "invoiced_qty"):
            _zero(line[field])
        for field in LINE_MONEY:
            _zero(line[field], True, 10 if field == "tax_rate" else 18, 2 if "amount" in field else 4)
        by_req[rid] = line
    _require(set(by_req) == set(req_lines) and set(by_detail) == set(mrp), "ROW_COVERAGE")
    for detail, row in mrp.items():
        _require(row["material_id"] == material and row["base_uom_id"] == "26" and row["del_flag"] == "0"
                 and _quantity(row["covered_demand_base_qty"], scale) == _quantity(by_detail[detail]["apply_quantity"], scale), "MRP_IDENTITY_OR_QUANTITY")
    for ledger in ledgers.values():
        rid = _id(ledger["requisition_line_id"])
        _require(rid in req_lines and rid not in ledger_by_req, "LEDGER_COVERAGE")
        rl, line = req_lines[rid], by_req[rid]
        allocation_key = _hash(TENANT, key, rl["source_id"])
        _require(ledger["material_id"] == material and ledger["detail_id"] == rl["source_id"]
                 and ledger["order_line_id"] == line["line_id"] and ledger["source_type"] == "MRP_DETAIL"
                 and type(ledger["allocation_no"]) is int and ledger["allocation_no"] == 1
                 and ledger["base_uom_id"] == "26" and ledger["decision_idempotency_key"] == key
                 and ledger["allocation_key"] == allocation_key and ledger["allocation_status"] == "ORDER_COMMITTED_NOT_TIMELY_SUPPLY"
                 and type(ledger["version"]) is int and ledger["version"] == 2 and ledger["release_reason"] is None, "LEDGER_IDENTITY")
        qty = _quantity(rl["apply_quantity"], scale)
        _require(_quantity(ledger["covered_demand_base_qty"], scale) == qty
                 and _quantity(ledger["occupied_base_qty"], scale) == qty, "LEDGER_QUANTITY_DRIFT")
        _zero(ledger["fulfilled_base_qty"])
        _zero(ledger["released_base_qty"])
        history = sorted((e for e in events.values() if e["allocation_id"] == ledger["allocation_id"]),
                         key=lambda e: _int(e["event_sequence"]))
        _require(len(history) == 3, "CAUSAL_EVENT_COVERAGE")
        previous_time = _time(rh["create_time"])
        for seq, event in enumerate(history):
            kind = ("CREATE", "APPROVE", "TRANSFER_TO_ORDER")[seq]
            event_key = "CREATE:" + allocation_key if seq == 0 else "LIFECYCLE:" + _hash(TENANT, allocation_key, kind, req if seq == 1 else line["line_id"])
            _require(event["event_sequence"] == seq and event["event_type"] == kind and event["event_key"] == event_key
                     and event["requisition_line_id"] == rid and event["order_line_id"] == (line["line_id"] if seq == 2 else None)
                     and event["from_status"] == (None if seq == 0 else "REQUEST_INTENT")
                     and event["to_status"] == ("ORDER_COMMITTED_NOT_TIMELY_SUPPLY" if seq == 2 else "REQUEST_INTENT"), "CAUSAL_EVENT_IDENTITY")
            _id(event["actor_id"])
            if seq == 0:
                _require(event["actor_id"] == command["actor_id"], "CREATE_ACTOR_MISMATCH")
            _require(_quantity(event["occupied_base_before"], scale) == (Decimal(0) if seq == 0 else qty)
                     and _quantity(event["occupied_base_after"], scale) == qty, "CAUSAL_QUANTITY_DRIFT")
            at = _time(event["occurred_at"])
            _require(at >= previous_time, "CAUSAL_TIME_DRIFT")
            previous_time = at
        _require(_time(ledger["create_time"]) <= _time(history[0]["occurred_at"])
                 and _time(history[1]["occurred_at"]) <= order_created <= _time(history[2]["occurred_at"])
                 and _time(history[1]["occurred_at"]) <= _time(ledger["update_time"]) <= _time(history[2]["occurred_at"])
                 and _time(ledger["transfer_time"]) <= _time(history[2]["occurred_at"])
                 and _time(ledger["transfer_time"]) >= order_created, "NATIVE_CAUSAL_TIME")
        ledger_by_req[rid] = ledger
    _require(set(ledger_by_req) == set(req_lines), "LEDGER_COVERAGE")
    native = _shape(order["nativeRows"], ("head", "lines"))
    _shape(native["head"], metadata["nativeColumns"]["pu_purchase_order"], "NATIVE_FULL_ROW_MISSING")
    _require(native["head"]["certified_version"] is None and all(native["head"][k] == v for k, v in head.items()), "NATIVE_PROJECTION_MISMATCH")
    _require(all(native["head"][field] is None for field in ("contract_id", "contract_no", "attr1", "attr2", "attr3", "attr4")),
             "UNKNOWN_EXTRA_BUSINESS_SOURCE")
    transfer_actors = {e["actor_id"] for e in events.values() if e["event_type"] == "TRANSFER_TO_ORDER"}
    _require(len(transfer_actors) == 1 and native["head"]["create_by"] in transfer_actors
             and native["head"]["requisition_no"] == rh["requisition_no"], "NATIVE_CREATION_IDENTITY")
    _require(type(native["lines"]) is list and len(native["lines"]) == len(lines), "NATIVE_FULL_ROW_MISSING")
    found = set()
    for row in native["lines"]:
        _shape(row, metadata["nativeColumns"]["pu_purchase_order_line"], "NATIVE_FULL_ROW_MISSING")
        lid = _id(row["line_id"])
        _require(lid in lines and lid not in found and all(row[k] == v for k, v in lines[lid].items()), "NATIVE_PROJECTION_MISMATCH")
        _require(row["create_by"] == native["head"]["create_by"], "NATIVE_CREATION_IDENTITY")
        found.add(lid)
    return oid, req_lines


def _order(order, ids, metadata):
    """原入口仍要求完整历史 source/catalog 前像，不接受回执替代。"""
    oid, req_lines = _order_structure(order, ids, metadata)
    _fingerprints(order, ids, req_lines)
    return oid


def _plan_backfill(snapshot: dict, expected_order_ids) -> dict:
    """内部校验；公共入口将格式错误的 Python 值转为安全的拒绝原因。"""
    ids = _scope(expected_order_ids)
    _shape(snapshot, ("environment", "authorization", "metadata", "orders"))
    env = _shape(snapshot["environment"], ("host", "transport", "port", "schema", "profile", "tenant"))
    _require(env["host"] in ("localhost", "127.0.0.1") and env["transport"] == "TCP"
             and type(env["port"]) is int and env["port"] == 3306 and env["schema"] == "ry-erp"
             and env["profile"] == "dev" and env["tenant"] == TENANT, "ENVIRONMENT_OUT_OF_SCOPE")
    auth = _shape(snapshot["authorization"], ("scope", "allowedOrderIds", "allowedChange", "production"))
    _require(auth["scope"] == "LOCAL_SOFTWARE_BUSINESS_DATA_COMPLETION" and auth["production"] is False
             and auth["allowedChange"] == "certified_version:NULL->0" and _scope(auth["allowedOrderIds"]) == ids,
             "AUTHORIZATION_OUT_OF_SCOPE")
    _metadata(snapshot["metadata"])
    _require(type(snapshot["orders"]) is list and len(snapshot["orders"]) == 7, "EXACT_SEVEN_SCOPE")
    orders, reqs, requests, allocations, line_ids, event_ids = {}, set(), set(), set(), set(), set()
    for order in snapshot["orders"]:
        oid = _order(order, ids, snapshot["metadata"])
        _require(oid not in orders and order["head"]["requisition_id"] not in reqs
                 and order["command"]["request_key"] not in requests, "DUPLICATE_SOURCE")
        orders[oid] = order
        reqs.add(order["head"]["requisition_id"])
        requests.add(order["command"]["request_key"])
        for rows, field, seen in ((order["ledger"], "allocation_id", allocations),
                                  (order["lines"], "line_id", line_ids), (order["events"], "event_id", event_ids)):
            for row in rows:
                _require(row[field] not in seen, "DUPLICATE_SOURCE")
                seen.add(row[field])
    _require(set(orders) == set(ids), "EXACT_SEVEN_SCOPE")
    snapshot_sha = _sha(snapshot)
    plan = dict(status="PLANNED", snapshotSha256=snapshot_sha,
                statements=[dict(sql=SQL, bindings=[TENANT, oid], proofSha256=_sha(orders[oid])) for oid in ids],
                backup=dict(format="canonical-json-utf8", sha256=snapshot_sha), retryAllowed=False)
    plan["planSha256"] = _sha(plan)
    return plan


def plan_backfill(snapshot: dict, expected_order_ids) -> dict:
    """纯离线、全有或全无校验；拒绝时抛出 BackfillRefused，绝不执行 I/O。"""
    try:
        return _plan_backfill(snapshot, expected_order_ids)
    except BackfillRefused:
        raise
    except (TypeError, KeyError, IndexError, ValueError, UnicodeError, OverflowError, RecursionError):
        raise BackfillRefused("MALFORMED_SNAPSHOT") from None


def write_backup(snapshot: dict, expected_order_ids, path: Path) -> dict:
    """由 main 在执行前调用：排他创建并持久化备份，绝不替换已有文件。"""
    plan = plan_backfill(snapshot, expected_order_ids)
    raw = _json_bytes(snapshot)
    path = Path(path)
    try:
        with path.open("xb") as file:
            file.write(raw)
            file.flush()
            os.fsync(file.fileno())
    except FileExistsError:
        try:
            _require(not path.is_symlink() and path.read_bytes() == raw, "BACKUP_CONFLICT")
        except OSError:
            raise BackfillRefused("BACKUP_CONFLICT") from None
    except OSError:
        raise BackfillRefused("BACKUP_WRITE_FAILED") from None
    return plan["backup"]


class BackfillPort(Protocol):
    def begin(self) -> None: ...
    def read_locked(self, expected_order_ids: tuple[str, ...]) -> dict: ...
    def cas_set_birth_zero(self, sql: str, bindings: tuple[str, str]) -> int: ...
    def commit(self) -> None: ...
    def rollback(self) -> None: ...


class BackfillExecutor:
    """只使用注入的 port；只允许一次尝试，不建立连接或自动重试。"""
    def __init__(self, port: BackfillPort, *, prior_outcome: str | None = None):
        _require(prior_outcome == "NEW", "PRIOR_OUTCOME_NO_RETRY")
        self.port = port
        self.status = "NEW"

    def execute(self, snapshot: dict, expected_order_ids, backup_path: Path) -> dict:
        _require(self.status == "NEW", "ONE_SHOT_NO_RETRY")
        self.status = "VALIDATING"
        try:
            snapshot = copy.deepcopy(snapshot)
            plan = plan_backfill(snapshot, expected_order_ids)
            ids = _scope(expected_order_ids)
            backup_path = Path(backup_path)
            _require(not backup_path.is_symlink(), "BACKUP_CONFLICT")
            try:
                raw = backup_path.read_bytes()
            except OSError:
                raise BackfillRefused("BACKUP_MISSING") from None
            _require(hashlib.sha256(raw).hexdigest() == plan["snapshotSha256"] and raw == _json_bytes(snapshot), "BACKUP_CONFLICT")
        except BackfillRefused:
            self.status = "REJECTED"
            raise
        receipt = dict(snapshotSha256=plan["snapshotSha256"], planSha256=plan["planSha256"], retryAllowed=False)
        self.status = "RUNNING"
        try:
            _require(self.port.begin() is None, "PORT_BEGIN_CONTRACT")
            locked = self.port.read_locked(ids)
            locked_plan = plan_backfill(locked, ids)
            _require(locked_plan == plan, "LOCKED_SNAPSHOT_DRIFT")
            for statement in plan["statements"]:
                rowcount = self.port.cas_set_birth_zero(SQL, tuple(statement["bindings"]))
                _require(type(rowcount) is int and rowcount == 1, "ROWCOUNT_NOT_ONE")
            expected = copy.deepcopy(snapshot)
            for order in expected["orders"]:
                order["nativeVersion"] = 0
                order["nativeRows"]["head"]["certified_version"] = 0
            _require(_json_bytes(self.port.read_locked(ids)) == _json_bytes(expected), "POSTWRITE_DRIFT")
        except BaseException as failure:
            reason = (failure.code if isinstance(failure, BackfillRefused)
                      and failure.code in _INTERNAL_REASONS else "PORT_EXCEPTION")
            try:
                _require(self.port.rollback() is None, "PORT_ROLLBACK_CONTRACT")
            except BaseException:
                self.status = "UNKNOWN"
                return dict(receipt, status=self.status, reason="ROLLBACK_OUTCOME_UNKNOWN")
            self.status = "ROLLED_BACK"
            return dict(receipt, status=self.status, reason=reason)
        # COMMIT 抛异常时，事务可能已持久化。不得以随后的回滚冒称结果确定，
        # 也不得换请求键或执行器实例重试。
        try:
            _require(self.port.commit() is None, "PORT_COMMIT_CONTRACT")
        except BaseException:
            self.status = "UNKNOWN"
            return dict(receipt, status=self.status, reason="COMMIT_OUTCOME_UNKNOWN")
        self.status = "COMMITTED"
        return dict(receipt, status=self.status, changedRows=7)


def _no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        _require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def main(argv=None) -> int:
    """仅离线运行；不提供 --execute，不接收驱动凭据，也不隐式发现 ID。"""
    parser = argparse.ArgumentParser(description="Offline exact-seven NULL birth-version evidence planner (no DB execution)")
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--backup", required=True, type=Path)
    parser.add_argument("--expected-order-id", action="append", default=[])
    args = parser.parse_args(argv)
    try:
        snapshot = json.loads(args.snapshot.read_text(encoding="utf-8-sig"), object_pairs_hook=_no_duplicate_keys)
        plan = plan_backfill(snapshot, args.expected_order_id)
        write_backup(snapshot, args.expected_order_id, args.backup)
    except BackfillRefused as failure:
        print(json.dumps(dict(status="REFUSED", reason=failure.code, retryAllowed=False)))
        return 2
    except (OSError, ValueError, UnicodeError):
        print(json.dumps(dict(status="REFUSED", reason="INVALID_OR_UNREADABLE_SNAPSHOT", retryAllowed=False)))
        return 2
    print(json.dumps(plan, ensure_ascii=True, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
