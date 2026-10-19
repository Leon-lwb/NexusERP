"""严格离线JSON格式、摘要与表列白名单；无网络或数据库依赖。"""

from copy import deepcopy
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import re

from metadata import KEYS, COLUMNS

WRITABLE = {
    "wm_sn": {"registration_command", "identity_version"},
    "wm_material_stock": {"sn_id", "sn_code", "tracking_version", "valuation_group_id", "version"},
    "wm_material_stock_tx": {"sn_id", "sn_code", "tracking_version", "posting_group_id"},
    "pro_sn": {"identity_id", "product_lot_id", "identity_version"},
    "lt_lot": {"erp_batch_id", "allocation_version"},
}
# 新业务来源必须完整读取，缺失不能等同于空表；来源账从不自动修复写入。
READ_ONLY_SOURCE_TABLES = {
    "mes_label_print_task",
    "pro_completion_material_allocation",
    "pro_product_lot_output",
    "lt_wo_consumption",
    "wm_trace_material_consumption",
    "wm_trace_material_reservation",
    "wm_trace_material_reservation_line",
    "wm_serial_reservation",
    "wm_serial_sequence",
    "wm_serial_lifecycle_command",
    "wm_work_order_tracking_policy",
    "wm_work_order_policy_adoption",
    "wm_barcode",
    # 取消终态由原清单和两库事件账承载，状态/冻结回执不允许部分投影。
    "wm_trace_outbox",
    "wm_trace_inbox",
    "mes_trace_outbox",
    "mes_trace_inbox",
    "pro_completion_manifest",
}
REQUIRED_TABLES = {
    "bd_material",
    "wm_sn",
    "wm_batch",
    "wm_sn_alias",
    "wm_material_stock",
    "wm_material_stock_tx",
    "pro_sn",
    "lt_lot",
    "lt_wo_consumption",
    "lt_sn_consumption",
    "lt_sn_material_guard",
    "lt_lot_allocation",
    "wm_valuation_group",
    "wm_trace_posting_group",
    "wm_document_sn",
    "pro_completion_manifest",
    "pro_completion_manifest_line",
    "wm_trace_outbox",
    "wm_trace_inbox",
    "mes_trace_outbox",
    "mes_trace_inbox",
    "mes_label_template",
    "mes_label_print_log",
} | READ_ONLY_SOURCE_TABLES


def canonical(value):
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    )


def digest(value):
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def require_hash(value):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("摘要必须为小写SHA256")
    return value


def validate_artifact(evidence):
    if not isinstance(evidence, dict) or evidence.get("kind") not in {
        "SOURCE_DOCUMENT",
        "SIGNED_REVIEW",
        "PHYSICAL_COUNT",
    }:
        raise ValueError("证据类型无效，同码或差额不是凭据")
    if (
        not evidence.get("reference")
        or not evidence.get("approved_by")
        or not evidence.get("tenant_id")
    ):
        raise ValueError("证据缺少原凭据引用、审核人或租户")
    require_hash(evidence.get("artifact_sha256"))
    expected = {
        k: v for k, v in evidence.items() if k not in ("evidence_id", "artifact_sha256", "artifact")
    }
    if (
        not isinstance(evidence.get("artifact"), dict)
        or evidence["artifact"] != expected
        or digest(evidence["artifact"]) != evidence["artifact_sha256"]
    ):
        raise ValueError("身份与批次证据的冻结凭据JSON或摘要不匹配")
    return evidence


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_-]{0,63}", value):
        raise ValueError("标识符不在白名单格式内")
    return value


def decimal(value):
    if not isinstance(value, str) or not re.fullmatch(r"-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?", value):
        raise ValueError("数量和金额必须为普通十进制字符串，禁止浮点、NaN或指数")
    result = Decimal(value)
    if not result.is_finite():
        raise ValueError("数值必须有限")
    return result


def number(row, column):
    if column not in row or row[column] is None:
        raise ValueError("离线行缺少非空数值列：" + column)
    return decimal(row[column])


def datasource(table):
    if table not in KEYS:
        raise ValueError("目标表不在白名单")
    return "mes" if table.startswith(("pro_", "lt_", "mes_")) else "erp"


def row_key(table, row):
    if table not in KEYS:
        raise ValueError("目标表不在白名单")
    result = {k: row.get(k) for k in KEYS[table]}
    if any(not isinstance(v, str) or not v for v in result.values()):
        raise ValueError("目标主键必须完整且包含租户字符串")
    return result


def find(snapshot, table, key):
    if table not in KEYS or set(key) != set(KEYS[table]):
        raise ValueError("目标主键或表不在白名单")
    hits = [
        r for r in snapshot["tables"].get(table, []) if all(r.get(k) == v for k, v in key.items())
    ]
    if len(hits) != 1:
        raise ValueError("目标主键未唯一定位既有身份或凭据，禁止生成历史SN")
    return hits[0]


def validate_target(target):
    if not isinstance(target, dict) or set(target) != {
        "environment",
        "erp_database",
        "mes_database",
    }:
        raise ValueError("必须显式指定环境及ERP/MES数据库身份")
    for k in ("erp_database", "mes_database"):
        identifier(target[k])
    if not isinstance(target["environment"], str) or not re.fullmatch(
        r"[A-Za-z0-9_.-]{1,128}", target["environment"]
    ):
        raise ValueError("环境标识无效，禁止口令或连接串")


def validate_snapshot(snapshot):
    if not isinstance(snapshot, dict) or snapshot.get("schema_version") != 1:
        raise ValueError("离线快照版本必须为1")
    validate_target(snapshot.get("target"))
    if not isinstance(snapshot.get("tables"), dict):
        raise ValueError("缺少表快照")
    if not isinstance(snapshot.get("complete_tables", []), list):
        raise ValueError("完整表声明必须为列表")
    for table, rows in snapshot["tables"].items():
        if table not in KEYS or not isinstance(rows, list):
            raise ValueError("快照表不在白名单或不是列表")
        seen = set()
        for row in rows:
            if not isinstance(row, dict) or set(row) - set(COLUMNS[table]):
                raise ValueError("快照列不在白名单")
            if table in READ_ONLY_SOURCE_TABLES and set(row) != set(COLUMNS[table]):
                raise ValueError(
                    "业务来源表必须完整导出全部列，禁止省略状态、租约或消耗量：" + table
                )
            key = canonical(row_key(table, row))
            if key in seen:
                raise ValueError("导出存在重复主键")
            seen.add(key)
            for col, value in row.items():
                if value is not None and not isinstance(value, str):
                    raise ValueError("行值必须为字符串或null，ID/数量/金额禁止JSON浮点或整数")
                typ = COLUMNS[table][col]
                if value is not None and typ.startswith(("bigint", "int", "tinyint", "decimal")):
                    n = decimal(value)
                    scale = re.search(r",(\d+)\)", typ)
                    limit = int(scale[1]) if scale else 0
                    if max(0, -n.as_tuple().exponent) > limit:
                        raise ValueError("数值精度超过列契约：" + col)
                if value is not None and typ.startswith(("varchar", "char")):
                    limit = int(re.search(r"\((\d+)\)", typ)[1])
                    if len(value) > limit:
                        raise ValueError("文本长度超过列契约：" + col)
    canonical(snapshot)
    return snapshot


def load(path):
    def pairs(items):
        d = {}
        for k, v in items:
            if k in d:
                raise ValueError("JSON存在重复字段")
            d[k] = v
        return d

    def invalid(_):
        raise ValueError("禁止JSON浮点及非有限值，数值必须导出为字符串")

    return json.loads(
        Path(path).read_text(encoding="utf-8-sig"),
        object_pairs_hook=pairs,
        parse_float=invalid,
        parse_constant=invalid,
    )


def write_new(path, value, *, text=False):
    """排他创建输出，避免覆盖原始证据或备份。"""
    with Path(path).open("x", encoding="utf-8", newline="\n") as f:
        f.write(
            value
            if text
            else json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
            + "\n"
        )
