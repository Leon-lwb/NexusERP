"""提取所有追溯读取查询及相关批次关联，使用真实数据库执行并验证。"""

from __future__ import annotations
from decimal import Decimal
import json
import re
from harness import SqlFailure, ValidationError, sql_value
from java_sql import extract_selects, parameter_positions, bind_sql

TENANT, OTHER_TENANT = "901001", "901002"
MES_READER = "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesIdentityTraceReader.java"
ERP_SOURCE = "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesErpSourceReader.java"
ERP_SERIAL = (
    "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpSerialIdentityService.java"
)
SPECS = [
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpIdentityTraceReader.java",
        False,
    ),
    ("mes", MES_READER, False),
    (
        "mes",
        "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesWorkOrderReferenceReader.java",
        False,
    ),
    ("erp", ERP_SOURCE, False),
    ("erp", ERP_SERIAL, "canonical_find"),
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/PrdWorkOrderTrackingPolicyService.java",
        False,
    ),
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/PrdWorkOrderPolicyAdoptionService.java",
        False,
    ),
    (
        "mes",
        "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesWorkOrderTrackingPolicyReader.java",
        False,
    ),
    (
        "mes",
        "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesPreparedWorkOrderPolicies.java",
        False,
    ),
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/MaterialTrackingPolicyGuard.java",
        False,
    ),
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpMaterialPolicyService.java",
        False,
    ),
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/WmBarcodeIdentityGuard.java",
        "document_binding",
    ),
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpDocumentCodeResolver.java",
        "document_resolver",
    ),
    (
        "erp",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpCompletionCancellationService.java",
        False,
    ),
    (
        "mes",
        "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesProductLotOutputWriter.java",
        False,
    ),
    (
        "mes",
        "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesCompletionCancellationService.java",
        False,
    ),
    ("mes", "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesTraceStore.java", False),
    (
        "mes",
        "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesProductionDetailFactsService.java",
        False,
    ),
] + [
    ("mes", "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/" + name + ".java", True)
    for name in (
        "MesMaterialTraceWriter",
        "MesCompletionManifestService",
        "MesTrackingUsageService",
    )
]


def statements_for(c, path, joins_only=False):
    source = c.snapshot.text(path)
    if joins_only == "canonical_find":
        method = re.search(r"(?m)^    public SerialIdentity find\(", source)
        c.require(method is not None, "Canonical identity lookup method changed: " + path)
        end = re.search(r"(?m)^    }", source[method.start() :])
        c.require(end is not None, "Canonical identity lookup method is incomplete: " + path)
        source = (
            "\n" * source[: method.start()].count("\n")
            + source[method.start() : method.start() + end.end()]
        )
    if joins_only in ("document_binding", "document_resolver"):
        from document_sql import DOC, document_statements

        return document_statements(source, c.snapshot.text(DOC), joins_only)
    queries = extract_selects(source)
    if joins_only is True:
        queries = [
            query for query in queries if re.search(r"\bJOIN\b|\bEXISTS\s*\(", query["sql"], re.I)
        ]
    c.require(bool(queries), "No actual Java SQL selected: " + path)
    return queries


def selected_query(c, path, prefix, variant="static"):
    selected = [
        query["sql"]
        for query in statements_for(c, path)
        if query["sql"].startswith(prefix) and query["variant"] == variant
    ]
    c.require(len(selected) == 1, "Inspect changed owner SQL shape: " + path + " " + prefix)
    return selected[0]


def smoke_parameters(sql):
    """使用类型明确的固定参数，后续数据用例保持实际业务参数顺序。"""
    result = []
    for ordinal, position in enumerate(parameter_positions(sql), 1):
        before, after = sql[:position], sql[position + 1 :]
        field = re.search(r"\b([A-Za-z_][\w.]*)\s*(=|>|>=|<|<=)\s*$", before)
        if re.search(r"\bLIMIT\s*$", before, re.I):
            role, value = "limit", 2
        elif before.rstrip().endswith(
            "JSON_UNQUOTE(JSON_EXTRACT(a.evidence_json,'$.source.identityId'))="
        ):
            role, value = "component_identity_string", "9007199254740993"
        elif re.match(r"\s+IS NULL", after, re.I):
            role, value = "nullable_batch_repeat", None
        elif re.search(r"\bIN\s*\([^()]*$", before, re.I):
            role, value = "identity_in", 9007199254740993 + ordinal
        elif field:
            role = field[1].rsplit(".", 1)[-1]
            if role == "tenant_id":
                value = TENANT
            elif field[2] in (">", ">=", "<", "<="):
                role, value = "cursor:" + role, 0
            elif role in (
                "event_id",
                "event_type",
                "aggregate_type",
                "source_doc_type",
                "status",
                "sn_code",
                "command_id",
                "client_key",
            ):
                value = "SYNTH_NO_MATCH_" + role.upper()
            elif role.endswith(("_code", "_no")):
                value = "SYNTH_NO_MATCH_" + role.upper()
            elif role == "del_flag":
                value = "0"
            elif role in ("revision", "manifest_revision", "aggregate_version", "schema_version"):
                value = 1
            else:
                value = 9007199254740993
        else:
            raise ValidationError(
                "Unknown SQL smoke parameter context at ordinal "
                + str(ordinal)
                + ": "
                + before[-100:]
            )
        result.append({"ordinal": ordinal, "role": role, "value": value})
    return result


def execute_reader_statement(c, ds, path, query):
    sql = query["sql"]
    count = len(parameter_positions(sql))
    bindings = smoke_parameters(sql)
    values = [binding["value"] for binding in bindings]
    statement = bind_sql(sql, values)
    outcomes, failures = [], []
    for prefix in ("fresh_", "legacy_"):
        schema = c.dbs[prefix + ds]
        try:
            result = c.query(schema, statement)
            plan = c.query(schema, "EXPLAIN " + statement).rows
            outcomes.append({"schema": schema, "returned_rows": len(result.rows), "explain": plan})
        except SqlFailure as exc:
            failures.append(exc)
            c.report.setdefault("reader_sql_findings", []).append(
                {
                    "source": path,
                    "line": query["line"],
                    "source_sha256": c.snapshot.hashes[path],
                    "schema": schema,
                    "sql": statement,
                    "native_mysql_output": exc.output,
                }
            )
    if failures:
        raise failures[0]
    return {
        "source": path,
        "source_sha256": c.snapshot.hashes[path],
        "line": query["line"],
        "variant": query["variant"],
        "sql": sql,
        "parameter_count": count,
        "parameter_bindings": bindings,
        "bound_sql": statement,
        "databases": outcomes,
        "boundary": "Actual source SQL plus EXPLAIN; mostly zero-match smoke parameters, not Java business orchestration",
    }


def binary_tenant_comparisons(c):
    schema = c.dbs["legacy_mes"]
    samples = [
        ("same", TENANT, TENANT, "1"),
        ("case", "TenantA", "tenanta", "0"),
        ("trailing_space", TENANT, TENANT + " ", "0"),
        ("utf8_80_bytes_same", "🚀" * 20, "🚀" * 20, "1"),
        ("utf8_80_bytes_last_character", "🚀" * 20, "🚀" * 19 + "🚁", "0"),
    ]
    results = []
    for name, left, right, expected in samples:
        actual = c.query(
            schema,
            "SELECT CAST("
            + sql_value(left)
            + " AS BINARY(80))=CAST("
            + sql_value(right)
            + " AS BINARY(80)),LENGTH(CAST("
            + sql_value(left)
            + " AS BINARY(80))); ",
        ).first
        c.require(
            actual == [expected, "80"],
            "Fixed binary comparison changed tenant semantics: " + name + " " + str(actual),
        )
        results.append({"sample": name, "comparison": actual[0], "cast_bytes": actual[1]})
    padded = c.query(
        schema,
        "SELECT CAST("
        + sql_value(TENANT)
        + " AS BINARY(80))=CAST(CONCAT("
        + sql_value(TENANT)
        + ",CHAR(0)) AS BINARY(80));",
    ).scalar
    c.require(padded == "1", "Inspect fixed BINARY padding behavior")
    c.report["constraint_boundaries"].append(
        {
            "type": "binary80_zero_padding",
            "table": "tenant_comparison",
            "observed_tenant_and_trailing_nul_equal": True,
            "meaning": "Fixed BINARY padding is exact for canonical tenant strings without NUL; trailing NUL is not distinguishable from padding",
            "boundary": "Tenant input validation and H2 behavior are not exercised",
        }
    )
    return {
        "comparisons": results,
        "trailing_nul_comparison": padded,
        "boundary": "MySQL 5.7 CAST behavior; synthetic 20-character utf8mb4 limit, not authentication validation",
    }


def sn_only_backward_and_lt_reads(c):
    schema = c.dbs["legacy_mes"]
    product = 9007199254741101
    original = c.query(
        schema,
        "SELECT entry_id,wo_consumption_id,local_sn_id,component_lot_id FROM lt_sn_consumption WHERE tenant_id="
        + sql_value(TENANT)
        + " AND identity_id="
        + str(product)
        + " AND entry_type='CONSUME' ORDER BY entry_id LIMIT 1;",
    ).first
    entry, consumption, local_sn, lot = original
    lot_code = "SYNTH-BACKWARD-" + str(c.uid())
    c.insert(
        schema,
        "lt_lot",
        {
            "lot_id": int(lot),
            "tenant_id": TENANT,
            "material_id": 301,
            "lot_code": lot_code,
            "erp_batch_id": None,
        },
    )
    query = selected_query(c, MES_READER, "SELECT c.entry_id")
    rows = c.query(schema, bind_sql(query, [TENANT, product, 0, 10])).rows
    c.require(
        len(rows) == 1
        and rows[0][0] == entry
        and rows[0][3:8] == [lot_code, "NULL", "9007199254740993", "1.2500", consumption],
        "Actual backward trace lost SN-only identity/batch/quantity: " + str(rows),
    )
    c.require(
        not c.query(schema, bind_sql(query, [OTHER_TENANT, product, 0, 10])).rows,
        "Backward trace crossed tenant",
    )
    c.require(
        not c.query(schema, bind_sql(query, [TENANT, product, int(entry), 10])).rows,
        "Backward entry cursor repeated a row",
    )
    paths = {path.split("/")[-1]: path for _, path, _ in SPECS}
    reverse = selected_query(
        c, paths["MesMaterialTraceWriter.java"], "SELECT c.wo_id FROM lt_sn_consumption"
    )
    wo = c.query(schema, bind_sql(reverse, [TENANT, int(entry)])).scalar
    manifest = selected_query(
        c, paths["MesCompletionManifestService.java"], "SELECT e.* FROM lt_sn_consumption"
    )
    entries = c.query(schema, bind_sql(manifest, [TENANT, int(wo)])).rows
    c.require(
        any(row[0] == entry for row in entries) and all(row[1] == TENANT for row in entries),
        "Actual completion consumption join crossed tenant or lost original entry",
    )
    return {
        "product_identity": str(product),
        "component_identity": "9007199254740993",
        "erp_batch": None,
        "trace_row": rows[0],
        "reverse_hint_work_order": wo,
        "completion_entries": len(entries),
        "boundary": "Actual SQL reads over synthetic SN-only consumption; Java reverse/manifest write methods are not invoked",
    }


def sn_only_erp_source_sum(c):
    schema = c.dbs["legacy_erp"]
    group, stock_a, stock_b = c.uid(), c.uid(), c.uid()
    identities = [9007199254740993, 9007199254740992]
    for tenant, batch, identity, stock, quantity in [
        (TENANT, None, identities[0], stock_a, "2.5000"),
        (TENANT, None, identities[1], stock_b, "1.0000"),
        (TENANT, 701, identities[0], stock_a, "8.0000"),
        (OTHER_TENANT, None, identities[0], stock_a, "9.0000"),
    ]:
        c.insert(
            schema,
            "wm_material_stock_tx",
            {
                "tx_id": c.uid(),
                "tenant_id": tenant,
                "posting_group_id": group,
                "batch_id": batch,
                "material_id": 301,
                "sn_id": identity,
                "material_stock_id": stock,
                "tx_flag": -1,
                "tx_quantity": quantity,
            },
        )
    base = selected_query(c, ERP_SOURCE, "SELECT COALESCE(SUM(tx_quantity)")
    serial = selected_query(c, ERP_SOURCE, "SELECT COALESCE(SUM(tx_quantity)", "sn_filter")
    values = [TENANT, group, None, None, 301]
    total = c.query(schema, bind_sql(base, values)).scalar
    exact = c.query(schema, bind_sql(serial, values + [identities[0], stock_a])).scalar
    wrong = c.query(schema, bind_sql(serial, values + [identities[0], stock_b])).scalar
    c.require(
        Decimal(total) == Decimal("3.5000")
        and Decimal(exact) == Decimal("2.5000")
        and Decimal(wrong) == 0,
        "Actual ERP source proof broadened SN-only identity/stock/batch/tenant",
    )
    return {
        "batch_is_null": True,
        "batch_only_total": total,
        "exact_sn_stock_total": exact,
        "wrong_stock_total": wrong,
        "boundary": "Actual posted-quantity SQL only; ERP authorization and Java event verification are not invoked",
    }


def batch_recall_explain(c):
    schema = c.dbs["legacy_mes"]
    query = selected_query(
        c,
        MES_READER,
        "SELECT DISTINCT net.identity_id FROM (SELECT c.identity_id,c.component_material_id",
    )
    c.require(
        "WHERE a.tenant_id=? AND CAST(a.tenant_id AS BINARY(80))" in query,
        "Actual batch recall no longer has an explicit allocation tenant predicate",
    )
    roles = ["tenant", "after", "tenant", "batch", "limit+1"]
    values = [TENANT, 0, TENANT, 701, 20]
    statement = bind_sql(query, values)
    return {
        "actual_query_plan": c.query(schema, "EXPLAIN " + statement).rows,
        "bound_sql": statement,
        "parameter_bindings": [
            {"ordinal": i, "role": role, "value": value}
            for i, (role, value) in enumerate(zip(roles, values), 1)
        ],
        "explicit_allocation_tenant_predicate": True,
        "boundary": "Actual current owner SQL; EXPLAIN only, no performance SLA",
    }


def run_reader_cases(c):
    sources = []
    for ds, path, joins_only in SPECS:
        queries = statements_for(c, path, joins_only)
        sources.append(
            {
                "source": path,
                "scope": (
                    "canonical find SELECT"
                    if joins_only == "canonical_find"
                    else (
                        "LT JOIN/EXISTS"
                        if joins_only is True
                        else (
                            "all SELECTs with closed document targets"
                            if joins_only in ("document_binding", "document_resolver")
                            else "all SELECTs"
                        )
                    )
                ),
                "variants": len(queries),
            }
        )
        label = path.split("/")[-1].removesuffix(".java")
        for index, query in enumerate(queries, 1):
            name = "actual_sql_" + label + "_" + str(index).zfill(2) + "_" + query["variant"]
            c.case(
                name,
                lambda ds=ds, path=path, query=query: execute_reader_statement(c, ds, path, query),
                ["install_" + ds, "migration_" + ds],
            )
    c.report["actual_reader_sql_coverage"] = sources
    from business_reader_cases import run_business_reader_cases

    run_business_reader_cases(c)
    c.case(
        "binary80_tenant_comparison_limits_and_padding",
        lambda: binary_tenant_comparisons(c),
        ["migration_mes"],
    )
    c.case(
        "actual_sn_only_backward_reverse_and_completion_reads",
        lambda: sn_only_backward_and_lt_reads(c),
        ["mes_actual_identity_forward_query_sn_only_reversal_tenant_cursor"],
    )
    c.case(
        "actual_erp_source_sn_only_posted_quantity",
        lambda: sn_only_erp_source_sum(c),
        ["migration_erp"],
    )
    c.case(
        "actual_batch_recall_binary_tenant_explain",
        lambda: batch_recall_explain(c),
        ["mes_actual_identity_forward_sql_compiles"],
    )
