"""采购统一写、原生IQC出生与认证收货的只读V2结构合同。

只消费调用者已取得的information_schema目录行；不连接数据库、不读业务行、
不读取配置/认证材料、不执行DDL，也不授予任何写入或来源认证资格。
此合同补充而不替代既有preflight：基础业务表全集、真实租户/权限、
当前来源、历史NULL、已加载构建及事务内定义固定仍须由各自门禁独立验证。

metadata以schema+'.'+table为键；entry包含engine、columns及可选indexes。
列字段：type、precision、scale、nullable、characterMaximumLength、columnType、
collation、datetimePrecision、extra、generationExpression。
索引可以单独传入同键映射，也可以放在entry['indexes']；每行是
(INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, INDEX_TYPE)。
查询API仅返回SQL/参数。metadata_shape接受表2字段、列12字段、索引7字段的
普通cursor元组行；调用者负责使用同一受控连接取得目录，不能上传DTO冒充目录事实。
"""
from __future__ import annotations

from collections.abc import Mapping
import re


class SchemaMetadataError(ValueError):
    """目录输入错误只使用固定消息，绝不回显原始值或异常。"""

    def __init__(self):
        super().__init__("采购结构元数据输入无效，详细内容已抑制")


def _column(kind, *, nullable=False, length=None, precision=None, scale=None,
            collation=None, datetime_precision=None, unsigned=False):
    """声明关键列；nullable允许历史可空定义，不证明任何行已具备事实。"""
    return {"type": kind, "nullable": nullable, "length": length, "precision": precision,
            "scale": scale, "collation": collation, "datetimePrecision": datetime_precision,
            "unsigned": unsigned}


def _columns(*groups):
    """同型列分组只用于显式声明，不从SQL或现存数据自动扩展必需集。"""
    return {name: spec.copy() for spec, names in groups for name in names.split()}


_B = _column("bigint")
_BN = _column("bigint", nullable=True)
_I = _column("int")
_IN = _column("int", nullable=True)
_D = _column("datetime", datetime_precision=0)
_DN = _column("datetime", nullable=True, datetime_precision=0)
_D3 = _column("datetime", datetime_precision=3)
_Q = _column("decimal", precision=18, scale=4)
_QN = _column("decimal", nullable=True, precision=18, scale=4)
_IQC_Q = _column("decimal", precision=14, scale=4)
_MN = _column("decimal", nullable=True, precision=18, scale=2)


def _varchar(length, *, nullable=False, collation=None):
    """字符串长度参与永久身份合同；指定二进制排序规则时必须精确满足。"""
    return _column("varchar", nullable=nullable, length=length, collation=collation)


def _hash(*, nullable=False, collation=None):
    """只检查摘要存储列，不验证摘要内容或历史证据。"""
    return _column("char", nullable=nullable, length=64, collation=collation)


# 必需集人工对应PuCertifiedPurchase*Mapper、分配账本/事件Mapper与真实全新DDL。
# 2026-09-30的P1迁移只增可信条款列，不从旧MOQ或当前单位回填可信事实。
# 既有表只声明关键迁移列和最小身份，不克隆业务表全部列或建立新的信任根。
COLUMN_CONTRACTS = {
    "ry-erp.bd_material_vendor": _columns(
        (_B, "relation_id material_id vendor_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_BN, "order_uom_id moq_uom_id"), (_QN, "verified_moq_qty order_multiple units_per_pack"),
        (_column("tinyint", nullable=True), "allow_split"), (_varchar(64, nullable=True), "terms_version"),
        (_DN, "terms_effective_at terms_expires_at terms_verified_at")),
    "ry-erp.bd_unit_of_measure": _columns(
        (_B, "uom_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_column("decimal", nullable=True, precision=18, scale=6), "conversion_rate"),
        (_column("tinyint", nullable=True, unsigned=True), "quantity_scale"),
        (_varchar(64, nullable=True), "conversion_version"), (_DN, "conversion_verified_at")),
    "ry-erp.pu_purchase_requisition": _columns(
        (_B, "requisition_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_BN, "decision_run_id decision_action_id"), (_varchar(128, nullable=True), "decision_idempotency_key"),
        (_I, "version")),
    "ry-erp.pu_purchase_requisition_line": _columns(
        (_B, "line_id"), (_varchar(20, nullable=True), "tenant_id"), (_BN, "source_id"),
        (_QN, "apply_quantity approved_quantity")),
    "ry-erp.pu_purchase_order": _columns(
        (_B, "order_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_IN, "certified_version"), (_BN, "certified_terms_id"),
        (_column("decimal", nullable=True, precision=18, scale=6), "exchange_rate"),
        (_MN, "total_amount amount_excluding_tax tax_amount")),
    "ry-erp.pu_purchase_order_line": _columns(
        (_B, "line_id"), (_varchar(20, nullable=True), "tenant_id"), (_BN, "requisition_line_id"),
        (_QN, "order_quantity received_quantity returned_quantity unit_price unit_price_excluding_tax"),
        (_column("decimal", nullable=True, precision=10, scale=4), "tax_rate"),
        (_MN, "line_amount line_amount_excluding_tax line_tax_amount")),
    "ry-erp.pu_requirement_material_guard": _columns(
        (_varchar(20), "tenant_id"), (_B, "material_id"), (_I, "version"), (_DN, "update_time")),
    "ry-erp.pu_requirement_allocation_ledger": _columns(
        (_B, "allocation_id material_id detail_id requisition_line_id base_uom_id"),
        (_varchar(20), "tenant_id"), (_varchar(32), "source_type"), (_I, "allocation_no version"),
        (_BN, "order_line_id"), (_Q, "covered_demand_base_qty occupied_base_qty fulfilled_base_qty released_base_qty"),
        (_varchar(40), "allocation_status"), (_varchar(128), "allocation_key"),
        (_varchar(128, nullable=True), "decision_idempotency_key"),
        (_D, "create_time"), (_DN, "transfer_time released_at update_time"),
        (_varchar(255, nullable=True), "release_reason")),
    "ry-erp.pu_requirement_allocation_event": _columns(
        (_B, "event_id allocation_id requisition_line_id"), (_varchar(20), "tenant_id"),
        # 全新DDL为NOT NULL；2026-10-02增量为NULL兼容，两者都不能证明历史因果序列。
        (_IN, "event_sequence"), (_varchar(128), "event_key"), (_varchar(40), "event_type to_status"),
        (_varchar(40, nullable=True), "from_status"), (_Q, "occupied_base_before occupied_base_after"),
        (_BN, "order_line_id actor_id"), (_D, "occurred_at")),
    "ry-erp.pu_certified_purchase_command": _columns(
        (_varchar(20, collation="utf8mb4_bin"), "tenant_id"),
        (_varchar(128, collation="utf8mb4_bin"), "request_key"),
        (_hash(collation="ascii_bin"), "request_hash source_fingerprint result_fingerprint catalog_fingerprint"),
        (_B, "actor_id material_id vendor_id requisition_id"),
        (_varchar(32, collation="ascii_bin"), "entry_type"), (_column("date"), "window_end"),
        (_varchar(64), "requisition_no"), (_varchar(20, collation="ascii_bin"), "command_status"),
        (_D, "create_time update_time")),
    "ry-erp.pu_certified_purchase_order_terms": _columns(
        (_B, "quote_id order_id entered_by vendor_id material_id base_uom_id"),
        (_varchar(20, collation="utf8mb4_bin"), "tenant_id"), (_I, "quote_version"),
        (_varchar(128, collation="utf8mb4_bin"), "request_key source_request_key"),
        (_hash(collation="utf8mb4_bin"), "request_hash source_fingerprint catalog_fingerprint order_fingerprint evidence_sha256 quote_hash"),
        (_D3, "entered_at"), (_Q, "quantity unit_price_excluding_tax"),
        (_column("decimal", precision=10, scale=4), "tax_rate"),
        (_varchar(16, collation="utf8mb4_bin"), "currency"),
        (_column("decimal", precision=18, scale=6), "exchange_rate"),
        (_column("date"), "promised_arrival_date valid_from valid_until"),
        (_varchar(128), "supplier_quote_reference supplier_contact"),
        (_varchar(255), "evidence_reference payment_terms delivery_terms"),
        (_varchar(64, collation="utf8mb4_bin"), "rounding_policy")),
    "ry-erp.pu_certified_purchase_order_terms_confirmation": _columns(
        (_B, "confirmation_id quote_id actor_id"), (_varchar(20, collation="utf8mb4_bin"), "tenant_id"),
        (_hash(collation="utf8mb4_bin"), "quote_hash request_hash"),
        (_varchar(128, collation="utf8mb4_bin"), "request_key"),
        (_varchar(64, collation="utf8mb4_bin"), "permission_code"),
        (_D3, "confirmed_at"), (_varchar(500), "review_note")),
    "ry-erp.pu_certified_purchase_order_operation": _columns(
        (_B, "command_id order_id actor_id"), (_varchar(20, collation="utf8mb4_bin"), "tenant_id"),
        (_varchar(128, collation="utf8mb4_bin"), "request_key"),
        (_hash(collation="utf8mb4_bin"), "request_hash result_fingerprint"),
        (_varchar(32, collation="utf8mb4_bin"), "operation result_status"),
        (_I, "expected_version result_version"), (_BN, "quote_id"),
        (_hash(nullable=True, collation="utf8mb4_bin"), "quote_hash"),
        (_column("date", nullable=True), "expected_arrival_date"),
        (_MN, "total_amount amount_excluding_tax tax_amount"), (_D3, "occurred_at")),
    # 收货/库存原单位列实际为VARCHAR，不能把出生登记的BIGINT类型机械套在旧业务表。
    "ry-erp.wm_material_receive_line": _columns(
        (_B, "line_id material_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_BN, "receive_id purchase_line_id"), (_varchar(64, nullable=True), "base_uom_id"),
        (_Q, "quantity_received")),
    "ry-erp.wm_material_receive_detail": _columns(
        (_B, "detail_id material_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_BN, "receive_id line_id"), (_varchar(64, nullable=True), "base_uom_id"), (_Q, "quantity")),
    # 原生两表五列必须18,4；shadow/exact列绝不能替代原业务列，不执行真实精度迁移。
    "ry-erp.wm_material_stock": _columns(
        (_B, "stock_id material_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_varchar(64, nullable=True), "base_uom_id"), (_QN, "stock_quantity reserved_quantity"),
        (_I, "version"), (_column("tinyint"), "frozen_flag"),
        (_column("decimal", nullable=True, precision=14, scale=6), "unit_cost"),
        (_column("decimal", nullable=True, precision=14, scale=4), "total_cost")),
    "ry-erp.wm_material_stock_tx": _columns(
        (_B, "tx_id material_id material_stock_id"), (_varchar(20, nullable=True), "tenant_id"),
        (_varchar(64, nullable=True), "base_uom_id source_doc_type"), (_BN, "source_doc_id source_doc_line_id"),
        (_QN, "tx_quantity before_quantity after_quantity"),
        (_column("decimal", nullable=True, precision=14, scale=6), "unit_cost"),
        (_column("decimal", nullable=True, precision=14, scale=4), "tx_amount")),
    "ry-erp.wm_certified_receipt_posting": _columns(
        (_varchar(20), "tenant_id"), (_B, "receive_id material_id actor_id"),
        (_hash(), "source_fingerprint"), (_Q, "total_quantity"), (_D, "posted_at"),
        (_varchar(16), "notification_status")),
    # V1证据表加出生引用是2026-10-03增量：旧NULL不改为NOT NULL，不推断旧检验单位。
    "ry-erp.wm_certified_iqc_evidence": _columns(
        (_B, "evidence_id inspection_id receive_id material_id base_uom_id inspector_id"),
        (_varchar(20, collation="ascii_bin"), "tenant_id"), (_varchar(64), "inspection_code"),
        (_column("tinyint", unsigned=True), "quantity_scale"), (_IQC_Q, "inspected_quantity"),
        (_varchar(16, collation="ascii_bin"), "inspect_result"),
        (_varchar(48, collation="ascii_bin"), "source_kind"),
        (_varchar(96, collation="ascii_bin"), "source_version"),
        (_hash(collation="ascii_bin"), "actual_body_sha256 receipt_scope_sha256 evidence_sha256"),
        (_varchar(128, collation="ascii_bin"), "first_message_id"),
        (_D3, "source_created_at completed_at observed_at certified_at"),
        (_column("datetime", nullable=True, datetime_precision=3), "source_updated_at"),
        (_BN, "birth_id"), (_hash(nullable=True), "birth_sha256")),
    "ry-erp.wm_receipt_iqc_birth": _columns(
        (_B, "birth_id receive_id material_id base_uom_id captured_at vendor_id"),
        (_varchar(20, collation="ascii_bin"), "tenant_id"), (_column("tinyint", unsigned=True), "quantity_scale"),
        (_IQC_Q, "quantity"), (_hash(), "receipt_scope_sha256 birth_sha256"),
        (_varchar(64), "receive_code material_code"), (_varchar(255), "material_name vendor_name")),
    # IqcNativeSchema及QcReceiptIqcBirthMapper所需原生身份/数量，不复制整个检验业务表。
    "ry-mes.qc_iqc": _columns(
        (_B, "iqc_id material_id"), (_varchar(20), "tenant_id"), (_varchar(64), "iqc_no"),
        (_BN, "receive_id"), (_IQC_Q, "quantity"),
        (_column("decimal", nullable=True, precision=14, scale=4), "sample_qty"),
        (_column("datetime", nullable=True, datetime_precision=(0, 1, 2, 3)), "create_time")),
    "ry-mes.qc_inspect_detail": _columns(
        (_B, "detail_id check_id"), (_varchar(20), "tenant_id"), (_varchar(16), "check_type")),
    "ry-mes.qc_receipt_iqc_birth": _columns(
        (_B, "iqc_id birth_id receive_id material_id base_uom_id captured_at vendor_id"),
        (_varchar(20), "tenant_id"), (_I, "quantity_scale"), (_IQC_Q, "quantity"),
        (_varchar(64), "receipt_scope_sha256 birth_sha256 receive_code material_code binding_sha256"),
        (_varchar(255), "material_name vendor_name"), (_D3, "bound_at")),
}


# 主键须真实为PRIMARY，其他永久唯一键按完整有序列匹配，可接受等价重命名。
# command写入方对两个唯一索引名称及全集有严格核验，单独保持同一合同。
INDEX_CONTRACTS = {
    "ry-erp.bd_material_vendor": {"PRIMARY": ("relation_id",)},
    "ry-erp.bd_unit_of_measure": {"PRIMARY": ("uom_id",)},
    "ry-erp.pu_purchase_requisition": {"PRIMARY": ("requisition_id",),
        "uk_pur_decision_idem": ("tenant_id", "decision_idempotency_key")},
    "ry-erp.pu_purchase_requisition_line": {"PRIMARY": ("line_id",)},
    "ry-erp.pu_purchase_order": {"PRIMARY": ("order_id",)},
    "ry-erp.pu_purchase_order_line": {"PRIMARY": ("line_id",)},
    "ry-erp.pu_requirement_material_guard": {"PRIMARY": ("tenant_id", "material_id")},
    "ry-erp.pu_requirement_allocation_ledger": {"PRIMARY": ("allocation_id",),
        "uk_pral_tenant_key": ("tenant_id", "allocation_key"),
        "uk_pral_tenant_req_detail_no": ("tenant_id", "requisition_line_id", "detail_id", "allocation_no")},
    "ry-erp.pu_requirement_allocation_event": {"PRIMARY": ("event_id",),
        "uk_prae_tenant_event_key": ("tenant_id", "event_key"),
        "uk_prae_tenant_allocation_sequence": ("tenant_id", "allocation_id", "event_sequence")},
    "ry-erp.pu_certified_purchase_command": {"PRIMARY": ("tenant_id", "request_key"),
        "uk_certified_purchase_requisition": ("tenant_id", "requisition_id")},
    "ry-erp.pu_certified_purchase_order_terms": {"PRIMARY": ("quote_id",),
        "uk_quote_request": ("tenant_id", "request_key"),
        "uk_order_quote_version": ("tenant_id", "order_id", "quote_version")},
    "ry-erp.pu_certified_purchase_order_terms_confirmation": {"PRIMARY": ("confirmation_id",),
        "uk_confirmation_request": ("tenant_id", "request_key"),
        "uk_quote_confirmation": ("tenant_id", "quote_id")},
    "ry-erp.pu_certified_purchase_order_operation": {"PRIMARY": ("command_id",),
        "uk_operation_request": ("tenant_id", "request_key"),
        "uk_order_operation": ("tenant_id", "order_id", "operation")},
    "ry-erp.wm_material_receive_line": {"PRIMARY": ("line_id",)},
    "ry-erp.wm_material_receive_detail": {"PRIMARY": ("detail_id",)},
    "ry-erp.wm_material_stock": {"PRIMARY": ("stock_id",)},
    "ry-erp.wm_material_stock_tx": {"PRIMARY": ("tx_id",)},
    "ry-erp.wm_certified_receipt_posting": {"PRIMARY": ("tenant_id", "receive_id")},
    "ry-erp.wm_certified_iqc_evidence": {"PRIMARY": ("evidence_id",),
        "uk_certified_iqc_inspection": ("tenant_id", "inspection_id"),
        "uk_certified_iqc_scope": ("tenant_id", "receive_id", "material_id"),
        "uk_certified_iqc_message": ("tenant_id", "first_message_id")},
    "ry-erp.wm_receipt_iqc_birth": {"PRIMARY": ("birth_id",),
        "uk_iqc_birth_scope": ("tenant_id", "receive_id", "material_id")},
    "ry-mes.qc_iqc": {"PRIMARY": ("iqc_id",), "uk_iqc_no": ("tenant_id", "iqc_no"),
        "uk_iqc_receive_material": ("tenant_id", "receive_id", "material_id")},
    "ry-mes.qc_inspect_detail": {"PRIMARY": ("detail_id",)},
    "ry-mes.qc_receipt_iqc_birth": {"PRIMARY": ("iqc_id",),
        "uk_native_iqc": ("tenant_id", "iqc_id"), "uk_native_birth": ("tenant_id", "birth_id"),
        "uk_native_scope": ("tenant_id", "receive_id", "material_id")},
}

TABLES = {schema: tuple(sorted(key.split(".", 1)[1] for key in COLUMN_CONTRACTS
                               if key.startswith(schema + ".")))
          for schema in ("ry-erp", "ry-mes")}
_COLUMN_FIELDS = ("type", "precision", "scale", "nullable", "characterMaximumLength",
                  "columnType", "collation", "datetimePrecision", "extra", "generationExpression")
_COMMAND = "ry-erp.pu_certified_purchase_command"


def _column_matches(observed, expected):
    """核验原生类型与目录完整性；不转换数值字符串，不接受生成列替代业务原字段。"""
    if not isinstance(observed, Mapping) or not all(field in observed for field in _COLUMN_FIELDS):
        return False
    if observed["nullable"] not in ("YES", "NO") or (not expected["nullable"] and observed["nullable"] != "NO"):
        return False
    if not isinstance(observed["type"], str) or observed["type"].lower() != expected["type"]:
        return False
    for field in ("precision", "scale", "characterMaximumLength", "datetimePrecision"):
        if observed[field] is not None and type(observed[field]) is not int:
            return False
    if not isinstance(observed["extra"], str) or not isinstance(observed["generationExpression"], str):
        return False
    if "generated" in observed["extra"].lower() or observed["generationExpression"].strip():
        return False
    if not isinstance(observed["columnType"], str):
        return False
    native_type = re.sub(r"\s+", "", observed["columnType"].lower())
    kind = expected["type"]
    if kind in ("bigint", "int", "tinyint"):
        suffix = "unsigned" if expected["unsigned"] else ""
        return re.fullmatch(kind + r"(?:\([1-9][0-9]*\))?" + suffix, native_type) is not None
    if kind == "decimal":
        precision, scale = expected["precision"], expected["scale"]
        return (observed["precision"] == precision and observed["scale"] == scale
                and native_type == f"decimal({precision},{scale})")
    if kind in ("varchar", "char"):
        collation = observed["collation"]
        return (observed["characterMaximumLength"] == expected["length"]
                and native_type == f"{kind}({expected['length']})"
                and isinstance(collation, str) and bool(collation)
                and (expected["collation"] is None or collation == expected["collation"]))
    if kind == "datetime":
        precision = observed["datetimePrecision"]
        allowed = expected["datetimePrecision"]
        allowed = allowed if isinstance(allowed, tuple) else (allowed,)
        return (type(precision) is int and precision in allowed
                and native_type in (("datetime", "datetime(0)") if precision == 0 else (f"datetime({precision})",)))
    return kind == "date" and native_type == "date"


def _index_groups(rows):
    """目录索引行可乱序；重复序号、伪布尔数值、缺失字段一律拒绝。"""
    if not isinstance(rows, (list, tuple)):
        return None
    groups = {}
    for row in rows:
        if not isinstance(row, (list, tuple)) or len(row) != 6:
            return None
        name, non_unique, sequence, column, prefix, kind = row
        if (not isinstance(name, str) or not name or type(non_unique) is not int or non_unique not in (0, 1)
                or type(sequence) is not int or sequence < 1 or not isinstance(column, str) or not column
                or (prefix is not None and (type(prefix) is not int or prefix < 1))
                or not isinstance(kind, str) or not kind):
            return None
        parts = groups.setdefault(name, {})
        if sequence in parts:
            return None
        parts[sequence] = (non_unique, column, prefix, kind.upper())
    return groups


def _indexes_match(parts, columns):
    """只接受完整、无前缀、连续列序的原生BTREE唯一索引，不接受更长超集。"""
    return (set(parts) == set(range(1, len(columns) + 1))
            and all(parts[n] == (0, column, None, "BTREE") for n, column in enumerate(columns, 1)))


def metadata_gaps(metadata, indexes=None):
    """返回有序固定诊断；额外业务行/认证布尔不参与任何结构或来源结论。"""
    if not isinstance(metadata, Mapping):
        return ["procurement_schema_metadata_invalid"]
    if indexes is not None and not isinstance(indexes, Mapping):
        return ["procurement_schema_index_metadata_invalid"]
    gaps = []
    for key, expected_columns in sorted(COLUMN_CONTRACTS.items()):
        if key not in metadata:
            gaps.append(key + ": table unavailable")
            continue
        entry = metadata[key]
        if not isinstance(entry, Mapping):
            gaps.append(key + ": table metadata invalid")
            continue
        engine = entry.get("engine")
        if not isinstance(engine, str) or engine.upper() != "INNODB":
            gaps.append(key + ": engine must be InnoDB")
        columns = entry.get("columns")
        if not isinstance(columns, Mapping):
            gaps.append(key + ": column metadata invalid")
        else:
            for name, expected in sorted(expected_columns.items()):
                if name not in columns:
                    gaps.append(key + "." + name + ": column unavailable")
                elif not _column_matches(columns[name], expected):
                    gaps.append(key + "." + name + ": column contract mismatch")
        rows = indexes.get(key) if indexes is not None else entry.get("indexes")
        grouped = _index_groups(rows)
        if grouped is None:
            gaps.append(key + ": index metadata invalid")
            continue
        expected_keys = INDEX_CONTRACTS[key]
        for name, required_columns in sorted(expected_keys.items()):
            candidates = ([grouped[name]] if name in grouped else []) if name == "PRIMARY" or key == _COMMAND else grouped.values()
            if not any(_indexes_match(parts, required_columns) for parts in candidates):
                gaps.append(key + "." + name + ": full unique BTREE key unavailable")
        if key == _COMMAND:
            unique_names = {name for name, parts in grouped.items() if any(part[0] == 0 for part in parts.values())}
            if unique_names != set(expected_keys):
                gaps.append(key + ": permanent command unique key set mismatch")
    return sorted(set(gaps))


def schema_evidence(metadata, indexes=None):
    """脱敏且可JSON化的结构观察；不报告原始目录、租户值、数量或任何授信字段。"""
    gaps = metadata_gaps(metadata, indexes)
    return {"schemaVersion": 2, "status": "BLOCKED" if gaps else "SCHEMA_METADATA_MATCH",
            "scope": "PROCUREMENT_IQC_V2_SCHEMA_ONLY", "gaps": gaps,
            "requiredTables": {schema: list(tables) for schema, tables in TABLES.items()},
            "sourceCertification": "NOT_PERFORMED", "historicalDataCertification": "NOT_PERFORMED",
            "runtimeWriteChain": "NOT_PROVEN", "businessWriteAuthorized": False,
            "ddlAuthorized": False, "businessWrites": 0}


def _query(schema, fields, catalog, *, base_tables=False, order):
    """只生成白名单目录SELECT与独立绑定参数，不接受任意schema或表名。"""
    if not isinstance(schema, str) or schema not in TABLES:
        raise SchemaMetadataError()
    tables = TABLES[schema]
    marks = ",".join("%s" for _ in tables)
    sql = (f"SELECT {fields} FROM information_schema.{catalog} WHERE TABLE_SCHEMA=%s "
           + ("AND TABLE_TYPE='BASE TABLE' " if base_tables else "")
           + f"AND TABLE_NAME IN ({marks}) ORDER BY {order}")
    return sql, (schema, *tables)


def table_metadata_query(schema):
    """表查询返回(TABLE_NAME, ENGINE)，视图不能充当必需业务表。"""
    return _query(schema, "TABLE_NAME,ENGINE", "TABLES", base_tables=True, order="TABLE_NAME")


def column_metadata_query(schema):
    """列查询返回12字段；新增原生可空、长度、完整类型、排序规则及生成列信息。"""
    return _query(schema, "TABLE_NAME,COLUMN_NAME,DATA_TYPE,NUMERIC_PRECISION,NUMERIC_SCALE,IS_NULLABLE,"
                  "CHARACTER_MAXIMUM_LENGTH,COLUMN_TYPE,COLLATION_NAME,DATETIME_PRECISION,EXTRA,GENERATION_EXPRESSION",
                  "COLUMNS", order="TABLE_NAME,ORDINAL_POSITION")


def index_metadata_query(schema):
    """索引查询返回7字段；保留非唯一行以拒绝用同名非唯一索引冒充。"""
    return _query(schema, "TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,INDEX_TYPE",
                  "STATISTICS", order="TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX")


def metadata_shape(schema, table_rows, column_rows, index_rows):
    """规范普通cursor行；重复身份/列/索引部分固定报错，不包裹原始数据库异常。"""
    if (not isinstance(schema, str) or schema not in TABLES
            or any(not isinstance(rows, (list, tuple)) for rows in (table_rows, column_rows, index_rows))):
        raise SchemaMetadataError()
    result = {}
    for row in table_rows:
        if not isinstance(row, (list, tuple)) or len(row) != 2:
            raise SchemaMetadataError()
        table, engine = row
        if not isinstance(table, str) or table not in TABLES[schema] or schema + "." + table in result:
            raise SchemaMetadataError()
        result[schema + "." + table] = {"engine": engine, "columns": {}, "indexes": []}
    for row in column_rows:
        if not isinstance(row, (list, tuple)) or len(row) != 12:
            raise SchemaMetadataError()
        table, column, *values = row
        if not isinstance(table, str) or schema + "." + table not in result or not isinstance(column, str) or not column:
            raise SchemaMetadataError()
        columns = result[schema + "." + table]["columns"]
        if column in columns:
            raise SchemaMetadataError()
        columns[column] = dict(zip(_COLUMN_FIELDS, values))
    seen = set()
    for row in index_rows:
        if not isinstance(row, (list, tuple)) or len(row) != 7:
            raise SchemaMetadataError()
        table, *part = row
        if not isinstance(table, str) or schema + "." + table not in result or _index_groups([part]) is None:
            raise SchemaMetadataError()
        entry = result[schema + "." + table]
        if part[3] not in entry["columns"]:
            raise SchemaMetadataError()
        identity = (table, part[0], part[2])
        if identity in seen:
            raise SchemaMetadataError()
        seen.add(identity)
        entry["indexes"].append(tuple(part))
    return result
