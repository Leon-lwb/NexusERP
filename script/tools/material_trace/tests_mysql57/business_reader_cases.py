"""使用非空夹具验证当前实际批次与工单追溯查询。"""

from decimal import Decimal
import json
from reader_cases import (
    TENANT,
    OTHER_TENANT,
    MES_READER,
    ERP_SERIAL,
    SPECS,
    statements_for,
    selected_query,
)
from java_sql import bind_sql

ERP_READER = SPECS[0][1]


def by_shape(c, path, shape):
    candidates = [
        q for q in statements_for(c, path) if shape in q["sql"] and q["variant"] == "static"
    ]
    c.require(len(candidates) == 1, "Actual query shape changed: " + path + " " + shape)
    return candidates[0]["sql"]


def execute(c, schema, query, roles, values):
    statement = bind_sql(query, values)
    rows = c.query(schema, statement).rows
    return rows, {
        "sql": query,
        "bound_sql": statement,
        "parameter_bindings": [
            {"ordinal": i, "role": role, "value": value}
            for i, (role, value) in enumerate(zip(roles, values), 1)
        ],
        "rows": rows,
    }


def columns(query, rows):
    fields = query.split(" FROM ", 1)[0].removeprefix("SELECT ").split(",")
    names = [part.strip().split()[-1].rsplit(".", 1)[-1] for part in fields]
    return [dict(zip(names, row)) for row in rows]


def batch_only_facts(c):
    schema = c.dbs["legacy_erp"]
    batch, material, issue, customer, order = [c.uid() for _ in range(5)]
    c.insert(
        schema,
        "wm_batch",
        {
            "batch_id": batch,
            "tenant_id": TENANT,
            "batch_code": "SYNTH-BATCH-" + str(batch),
            "material_id": material,
            "warehouse_id": 111,
            "quantity": "12.0000",
            "received_qty": "12.0000",
            "issued_qty": "3.0000",
            "del_flag": "0",
        },
    )
    stocks = []
    for tenant, warehouse, quantity, deleted in [
        (TENANT, 222, "5.0000", "0"),
        (TENANT, 333, "4.0000", "0"),
        (TENANT, 444, "0.0000", "0"),
        (TENANT, 555, "9.0000", "2"),
        (OTHER_TENANT, 666, "9.0000", "0"),
    ]:
        stock = c.uid()
        stocks.append(stock)
        c.insert(
            schema,
            "wm_material_stock",
            {
                "stock_id": stock,
                "tenant_id": tenant,
                "material_id": material,
                "batch_id": batch,
                "sn_id": None,
                "sn_code": None,
                "warehouse_id": warehouse,
                "location_id": warehouse + 1,
                "stock_quantity": quantity,
                "reserved_quantity": "0.0000",
                "unit_cost": "2.1234",
                "total_cost": "10.6170",
                "tracking_version": 2,
                "del_flag": deleted,
            },
        )
    c.insert(
        schema,
        "wm_sales_issue",
        {
            "issue_id": issue,
            "tenant_id": TENANT,
            "client_id": customer,
            "client_code": "SYNTH-CUSTOMER",
            "client_name": "Synthetic customer",
            "so_id": order,
            "so_no": "SYNTH-SO",
            "del_flag": "0",
        },
    )
    transactions = []
    for flag, kind, source, quantity in [
        (1, "PURCHASE_RECPT", c.uid(), "12.0000"),
        (-1, "WM_SALES_ISSUE", issue, "3.0000"),
        (0, "SYNTH_HISTORY", c.uid(), "1.0000"),
    ]:
        tx = c.uid()
        transactions.append(tx)
        c.insert(
            schema,
            "wm_material_stock_tx",
            {
                "tx_id": tx,
                "tenant_id": TENANT,
                "material_id": material,
                "batch_id": batch,
                "sn_id": None,
                "sn_code": None,
                "warehouse_id": 222,
                "tx_flag": flag,
                "tx_quantity": quantity,
                "unit_cost": "2.1234",
                "tx_amount": "6.3702",
                "source_doc_type": kind,
                "source_doc_id": source,
                "del_flag": "0",
            },
        )
    c.insert(
        schema,
        "wm_material_stock_tx",
        {
            "tx_id": c.uid(),
            "tenant_id": OTHER_TENANT,
            "material_id": material,
            "batch_id": batch,
            "sn_id": None,
            "tx_flag": -1,
            "tx_quantity": "99.0000",
            "del_flag": "0",
        },
    )
    source = selected_query(
        c, ERP_READER, "SELECT batch_id,batch_code,material_id,material_code,vendor_id"
    )
    stock = by_shape(c, ERP_READER, "FROM wm_material_stock WHERE tenant_id=? AND batch_id=?")
    movements = by_shape(c, ERP_READER, "WHERE t.tenant_id=? AND t.batch_id=?")
    source_rows, source_evidence = execute(c, schema, source, ["tenant", "batch"], [TENANT, batch])
    stock_rows, stock_evidence = execute(
        c, schema, stock, ["tenant", "batch", "after", "limit+1"], [TENANT, batch, 0, 20]
    )
    tx_rows, tx_evidence = execute(
        c, schema, movements, ["tenant", "batch", "after", "limit+1"], [TENANT, batch, 0, 20]
    )
    sr, st, mv = (
        columns(source, source_rows),
        columns(stock, stock_rows),
        columns(movements, tx_rows),
    )
    c.require(
        len(sr) == 1 and sr[0]["source_warehouse_id"] == "111",
        "Batch source warehouse lost its source-only meaning",
    )
    c.require(
        len(st) == 2
        and {r["warehouse_id"] for r in st} == {"222", "333"}
        and all(r["sn_id"] == "NULL" for r in st),
        "SN-less batch stock was omitted or crossed tenant/deletion/zero stock",
    )
    c.require(
        len(mv) == 3 and [r["direction"] for r in mv] == ["IN", "OUT", "UNKNOWN"],
        "Batch movement directions or tenant isolation changed",
    )
    outgoing = mv[1]
    c.require(
        outgoing["customer_id"] == str(customer)
        and outgoing["sales_order_id"] == str(order)
        and outgoing["customer_code"] == "SYNTH-CUSTOMER",
        "Batch-only customer/sales destination was omitted",
    )
    c.require(
        Decimal(outgoing["tx_amount"]) == Decimal("6.3702"),
        "Movement amount lost decimal precision",
    )
    serials = by_shape(c, ERP_READER, "SELECT sn_id FROM wm_sn WHERE tenant_id=? AND batch_id=?")
    c.require(
        not c.query(schema, bind_sql(serials, [TENANT, batch, 0, 20])).rows,
        "The batch-only fixture unexpectedly has SNs",
    )
    c.require(
        not c.query(schema, bind_sql(stock, [OTHER_TENANT, batch, stocks[-1], 20])).rows,
        "Batch stock cursor repeated the foreign row",
    )
    return {
        "batch_id": str(batch),
        "material_id": str(material),
        "has_serials": False,
        "source": source_evidence,
        "stock": stock_evidence,
        "movements": tx_evidence,
        "customer_id": str(customer),
        "boundary": "Actual source SELECTs over synthetic rows; no inventory write service or HTTP orchestration",
    }


def batch_multiple_lots(c):
    schema = c.dbs["legacy_mes"]
    batch, material, lot_a, lot_b = [c.uid() for _ in range(4)]
    for lot, tenant, deleted in [(lot_a, TENANT, "0"), (lot_b, TENANT, "0")]:
        c.insert(
            schema,
            "lt_lot",
            {
                "lot_id": lot,
                "tenant_id": tenant,
                "lot_code": "SYNTH-LOT-" + str(lot),
                "material_id": material,
                "quantity": "10.0000",
                "consumed_qty": "0.0000",
                "del_flag": deleted,
            },
        )
    allocations = []
    for lot in [lot_a, lot_a, lot_b]:
        allocation = c.uid()
        allocations.append(allocation)
        c.insert(
            schema,
            "lt_lot_allocation",
            {
                "allocation_id": allocation,
                "tenant_id": TENANT,
                "command_id": "SYNTH-MULTILOT-" + str(allocation),
                "line_no": 1,
                "lot_id": lot,
                "erp_batch_id": batch,
                "material_id": material,
                "quantity": "1.2500",
                "source_type": "PRODUCTION_OUTPUT",
                "source_id": c.uid(),
                "evidence_json": "{}",
                "evidence_hash": "b" * 64,
            },
        )
    wrong = c.uid()
    c.insert(
        schema,
        "lt_lot",
        {
            "lot_id": wrong,
            "tenant_id": OTHER_TENANT,
            "lot_code": "SYNTH-FOREIGN-" + str(wrong),
            "material_id": material,
            "quantity": "10.0000",
            "del_flag": "0",
        },
    )
    c.insert(
        schema,
        "lt_lot_allocation",
        {
            "allocation_id": c.uid(),
            "tenant_id": TENANT,
            "command_id": "SYNTH-FOREIGN-ALLOCATION",
            "line_no": 1,
            "lot_id": wrong,
            "erp_batch_id": batch,
            "material_id": material,
            "quantity": "2.0000",
            "source_type": "PRODUCTION_OUTPUT",
            "source_id": c.uid(),
            "evidence_json": "{}",
            "evidence_hash": "b" * 64,
        },
    )
    query = by_shape(c, MES_READER, "SELECT a.allocation_id,a.erp_batch_id,a.lot_id")
    first, e1 = execute(
        c, schema, query, ["tenant", "batch", "afterAllocation", "limit+1"], [TENANT, batch, 0, 2]
    )
    second, e2 = execute(
        c,
        schema,
        query,
        ["tenant", "batch", "afterAllocation", "limit+1"],
        [TENANT, batch, allocations[1], 2],
    )
    c.require(
        [r[0] for r in first + second] == list(map(str, allocations)),
        "Multiple allocation lines/child lots were lost or repeated at the allocation cursor",
    )
    c.require(
        {r[2] for r in first + second} == {str(lot_a), str(lot_b)},
        "ERP batch did not retain both MES child lots",
    )
    c.require(
        not c.query(schema, bind_sql(query, [OTHER_TENANT, batch, 0, 20])).rows,
        "MES lot trace crossed tenant",
    )
    return {
        "batch_id": str(batch),
        "child_lots": list(map(str, [lot_a, lot_b])),
        "allocation_ids": list(map(str, allocations)),
        "pages": [e1, e2],
        "boundary": "Actual LOTS SELECT and allocation cursor, no Java cursor-token serialization",
    }


def work_order_scopes(c):
    schema = c.dbs["legacy_mes"]
    material, wo, batch = [c.uid() for _ in range(3)]
    identity = str(c.uid())
    expected = []
    fixtures = []
    for index, (consumed, assigned, reversed_qty, deleted, tenant, source_identity) in enumerate(
        [
            ("5.0000", "2.0000", None, "0", TENANT, identity),
            ("2.0000", "2.0000", None, "0", TENANT, identity),
            ("3.0000", "3.0000", "1.0000", "0", TENANT, identity),
            ("5.0000", "0.0000", None, "2", TENANT, identity),
            ("5.0000", "0.0000", None, "0", OTHER_TENANT, identity),
            ("5.0000", "0.0000", None, "0", TENANT, str(int(identity) - 1)),
        ]
    ):
        lot, allocation, consumption = [c.uid() for _ in range(3)]
        c.insert(
            schema,
            "lt_lot_allocation",
            {
                "allocation_id": allocation,
                "tenant_id": tenant,
                "command_id": "SYNTH-WO-A-" + str(allocation),
                "line_no": 1,
                "lot_id": lot,
                "erp_batch_id": batch,
                "material_id": material,
                "quantity": consumed,
                "source_type": "ERP_SN",
                "source_id": c.uid(),
                "evidence_json": json.dumps({"source": {"identityId": source_identity}}),
                "evidence_hash": "b" * 64,
            },
        )
        c.insert(
            schema,
            "lt_wo_consumption",
            {
                "consumption_id": consumption,
                "tenant_id": tenant,
                "wo_id": wo,
                "lot_id": lot,
                "material_id": material,
                "consumed_qty": consumed,
                "source_allocation_id": allocation,
                "del_flag": deleted,
            },
        )
        if Decimal(assigned) > 0:
            entry = c.uid()
            row = {
                "entry_id": entry,
                "tenant_id": tenant,
                "command_id": "SYNTH-WO-E-" + str(entry),
                "line_no": 1,
                "identity_id": c.uid(),
                "local_sn_id": c.uid(),
                "component_material_id": material,
                "component_lot_id": lot,
                "quantity": assigned,
                "wo_consumption_id": consumption,
                "operation_ref": "SYNTH-WO-OP-" + str(entry),
                "entry_type": "CONSUME",
                "original_entry_id": None,
            }
            c.insert(schema, "lt_sn_consumption", row)
            if reversed_qty is not None:
                c.insert(
                    schema,
                    "lt_sn_consumption",
                    {
                        **row,
                        "entry_id": c.uid(),
                        "command_id": "SYNTH-WO-R-" + str(entry),
                        "entry_type": "REVERSE",
                        "original_entry_id": entry,
                        "quantity": reversed_qty,
                    },
                )
        fixtures.append(
            {
                "consumption_id": str(consumption),
                "consumed": consumed,
                "assigned": assigned,
                "reversed": reversed_qty,
                "tenant": tenant,
                "deleted": deleted,
                "source_identity": source_identity,
            }
        )
        if index in (0, 2):
            expected.append(consumption)
    serial = selected_query(c, MES_READER, "SELECT w.consumption_id", "serial_true")
    batch_query = selected_query(c, MES_READER, "SELECT w.consumption_id", "serial_false")
    serial_rows, se = execute(
        c,
        schema,
        serial,
        ["netTenant", "workTenant", "after", "allocationTenant", "sourceIdentityString", "limit+1"],
        [TENANT, TENANT, 0, TENANT, identity, 20],
    )
    batch_rows, be = execute(
        c,
        schema,
        batch_query,
        ["netTenant", "workTenant", "after", "allocationTenant", "batch", "limit+1"],
        [TENANT, TENANT, 0, TENANT, batch, 20],
    )
    c.require(
        [r[0] for r in serial_rows] == list(map(str, expected)),
        "SN-only unallocated scope mismatch: expected="
        + str(expected)
        + " actual="
        + str(serial_rows),
    )
    c.require(
        [r[0] for r in batch_rows]
        == list(map(str, expected + [int(fixtures[-1]["consumption_id"])])),
        "Batch work-order scope lost a partial/unassigned quantity",
    )
    c.require(
        [Decimal(r[4]) for r in serial_rows] == [Decimal("2"), Decimal("2")],
        "Reverse did not restore the unallocated quantity",
    )
    cursor = c.query(
        schema, bind_sql(serial, [TENANT, TENANT, expected[0], TENANT, identity, 20])
    ).rows
    c.require(
        [r[0] for r in cursor] == [str(expected[1])], "Unallocated work-order cursor repeated a row"
    )
    products = []
    for tenant, deleted in [(TENANT, "0"), (TENANT, "0"), (TENANT, "2"), (OTHER_TENANT, "0")]:
        product, local = [c.uid() for _ in range(2)]
        c.insert(
            schema,
            "pro_sn",
            {
                "sn_id": local,
                "tenant_id": tenant,
                "identity_id": product,
                "sn_code": "SYNTH-PRODUCT-" + str(product),
                "material_id": material,
                "mes_wo_id": wo,
                "del_flag": deleted,
            },
        )
        if tenant == TENANT and deleted == "0":
            products.append(product)
    output_batches = []
    for tenant, allocation_tenant in [
        (TENANT, TENANT),
        (TENANT, TENANT),
        (TENANT, OTHER_TENANT),
        (OTHER_TENANT, OTHER_TENANT),
    ]:
        allocation, output_batch = [c.uid() for _ in range(2)]
        c.insert(
            schema,
            "lt_lot_allocation",
            {
                "allocation_id": allocation,
                "tenant_id": allocation_tenant,
                "command_id": "SYNTH-OUTPUT-A-" + str(allocation),
                "line_no": 1,
                "lot_id": c.uid(),
                "erp_batch_id": output_batch,
                "material_id": material,
                "quantity": "1.0000",
                "source_type": "PRODUCTION_OUTPUT",
                "source_id": c.uid(),
                "evidence_json": "{}",
                "evidence_hash": "b" * 64,
            },
        )
        c.insert(
            schema,
            "pro_product_lot_output",
            {
                "output_id": c.uid(),
                "tenant_id": tenant,
                "command_id": "SYNTH-OUTPUT-" + str(allocation),
                "line_no": 1,
                "mes_wo_id": wo,
                "material_id": material,
                "source_allocation_id": allocation,
                "lot_id": c.uid(),
                "quantity": "1.0000",
                "result": "GOOD",
            },
        )
        if tenant == allocation_tenant == TENANT:
            output_batches.append(output_batch)
    identities = by_shape(
        c, MES_READER, "SELECT identity_id FROM pro_sn WHERE tenant_id=? AND mes_wo_id=?"
    )
    outputs = by_shape(c, MES_READER, "SELECT DISTINCT a.erp_batch_id FROM pro_product_lot_output")
    pi, ie = execute(
        c, schema, identities, ["tenant", "wo", "afterIdentity", "limit+1"], [TENANT, wo, 0, 20]
    )
    po, oe = execute(
        c, schema, outputs, ["tenant", "wo", "afterBatch", "limit+1"], [TENANT, wo, 0, 20]
    )
    c.require(
        [r[0] for r in pi] == list(map(str, products)),
        "Product identity scope crossed tenant/deletion or lost an SN",
    )
    c.require(
        [r[0] for r in po] == list(map(str, output_batches)),
        "Product batch scope crossed its exact allocation/tenant source",
    )
    return {
        "work_order_id": str(wo),
        "fixtures": fixtures,
        "unallocated_identity": se,
        "unallocated_batch": be,
        "product_identities": ie,
        "product_batches": oe,
        "boundary": "Actual four source SELECT variants over nonzero synthetic data; WO recall expansion/business authorization not invoked",
    }


def product_output_constraints(c):
    schema = c.dbs["legacy_mes"]
    output, lot, wo, material, allocation = [c.uid() for _ in range(5)]
    original = {
        "output_id": output,
        "tenant_id": TENANT,
        "command_id": "SYNTH-OUTPUT-COMMAND-" + str(output),
        "line_no": 1,
        "mes_wo_id": wo,
        "material_id": material,
        "source_allocation_id": allocation,
        "lot_id": lot,
        "quantity": "1.2500",
        "result": "GOOD",
    }
    c.insert(schema, "pro_product_lot_output", original)
    duplicate_command = c.insert(
        schema,
        "pro_product_lot_output",
        {**original, "output_id": c.uid(), "lot_id": c.uid()},
        error=1062,
    )
    duplicate_lot = c.insert(
        schema,
        "pro_product_lot_output",
        {**original, "output_id": c.uid(), "command_id": "SYNTH-OTHER-COMMAND-" + str(output)},
        error=1062,
    )
    c.insert(
        schema,
        "pro_product_lot_output",
        {**original, "output_id": c.uid(), "tenant_id": OTHER_TENANT},
    )
    rows = c.query(
        schema,
        "SELECT tenant_id,quantity,result,source_allocation_id FROM pro_product_lot_output WHERE lot_id="
        + str(lot)
        + " ORDER BY tenant_id;",
    ).rows
    c.require(
        len(rows) == 2
        and all(
            Decimal(row[1]) == Decimal("1.2500") and row[2] == "GOOD" and row[3] == str(allocation)
            for row in rows
        ),
        "Output precision/source identity or tenant scope changed",
    )
    return {
        "duplicate_command_error": duplicate_command.errors,
        "duplicate_lot_error": duplicate_lot.errors,
        "same_lot_other_tenant_allowed": True,
        "rows": rows,
        "boundary": "Physical uniqueness/precision only; source ownership, quality and positive quantity are enforced by Java",
    }


def canonical_identity_projection(c):
    erp, mes = c.dbs["legacy_erp"], c.dbs["legacy_mes"]
    identity, local, material, wo = [c.uid() for _ in range(4)]
    canonical = "SYNTH-CANONICAL-" + str(identity)
    historical = "SYNTH-HISTORICAL-" + str(local)
    c.insert(
        erp,
        "wm_sn",
        {
            "sn_id": identity,
            "tenant_id": TENANT,
            "sn_code": canonical,
            "material_id": material,
            "del_flag": "0",
        },
    )
    c.insert(
        mes,
        "pro_sn",
        {
            "sn_id": local,
            "tenant_id": TENANT,
            "identity_id": identity,
            "sn_code": historical,
            "material_id": material,
            "mes_wo_id": wo,
            "identity_version": 7,
            "del_flag": "0",
        },
    )
    formal = statements_for(c, ERP_SERIAL, "canonical_find")[0]["sql"]
    mapping = selected_query(
        c, MES_READER, "SELECT sn_id,sn_code,material_id,mes_wo_id,identity_version"
    )
    authority, authority_evidence = execute(
        c, erp, formal, ["tenant", "identity"], [TENANT, identity]
    )
    mapped, mapping_evidence = execute(c, mes, mapping, ["tenant", "identity"], [TENANT, identity])
    c.require(
        authority == [[str(identity), canonical, str(material), "NULL", "NULL"]],
        "Canonical ERP identity SQL lost the formal code/material",
    )
    c.require(
        mapped == [[str(local), historical, str(material), str(wo), "7"]],
        "MES mapping SQL lost its historical code or material snapshot",
    )
    c.require(
        authority[0][1] != mapped[0][1] and authority[0][2] == mapped[0][2],
        "Distinct historic/formal codes were merged or mapped across materials",
    )
    c.require(
        not c.query(erp, bind_sql(formal, [OTHER_TENANT, identity])).rows
        and not c.query(mes, bind_sql(mapping, [OTHER_TENANT, identity])).rows,
        "Canonical/mapped identity SQL crossed tenant",
    )
    return {
        "identity_id": str(identity),
        "local_sn_id": str(local),
        "formal": authority_evidence,
        "local_mapping": mapping_evidence,
        "boundary": "Actual ERP/MES lookup projections only; Java canonical assignment, mismatch rejection and RPC routing are separately tested by main",
    }


def orphan_in_stock_identity_readiness(c):
    schema = c.dbs["legacy_erp"]
    material, identity, stock = [c.uid() for _ in range(3)]
    path = "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/MaterialTrackingPolicyGuard.java"
    query = selected_query(c, path, "SELECT COUNT(*) FROM wm_sn n WHERE n.tenant_id=?")
    c.insert(
        schema,
        "wm_sn",
        {
            "sn_id": identity,
            "tenant_id": TENANT,
            "material_id": material,
            "sn_code": "SYNTH-ORPHAN-" + str(identity),
            "status": "IN_STOCK",
            "del_flag": "0",
        },
    )
    before, first = execute(c, schema, query, ["tenant", "material"], [TENANT, material])
    c.require(before == [["1"]], "Orphan IN_STOCK identity passed readiness")
    values = {
        "stock_id": stock,
        "tenant_id": TENANT,
        "material_id": material,
        "sn_id": identity,
        "stock_quantity": "1.0000",
        "reserved_quantity": "0.0000",
        "tracking_version": 2,
        "del_flag": "0",
    }
    c.insert(schema, "wm_material_stock", values)
    covered, second = execute(c, schema, query, ["tenant", "material"], [TENANT, material])
    c.require(covered == [["0"]], "A real same-tenant positive stock did not cover the identity")
    c.query(schema, "UPDATE wm_material_stock SET del_flag='2' WHERE stock_id=" + str(stock) + ";")
    c.insert(
        schema, "wm_material_stock", {**values, "stock_id": c.uid(), "tenant_id": OTHER_TENANT}
    )
    uncovered, third = execute(c, schema, query, ["tenant", "material"], [TENANT, material])
    c.require(uncovered == [["1"]], "Deleted/foreign inventory covered an orphan identity")
    return {
        "identity_id": str(identity),
        "material_id": str(material),
        "checks": [first, second, third],
        "boundary": "Actual reverse readiness COUNT; complete policy transition and valuation checks remain Java responsibilities",
    }


def readiness_zero_stock_value_and_orphan_groups(c):
    schema = c.dbs["legacy_erp"]
    material, stock, group = [c.uid() for _ in range(3)]
    path = "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/MaterialTrackingPolicyGuard.java"
    balances = selected_query(
        c,
        path,
        "SELECT COUNT(*) FROM wm_material_stock WHERE tenant_id=? AND material_id=? AND del_flag='0' AND (stock_quantity IS NULL",
    )
    groups = selected_query(c, path, "SELECT COUNT(*) FROM wm_valuation_group g LEFT JOIN")
    c.insert(
        schema,
        "wm_material_stock",
        {
            "stock_id": stock,
            "tenant_id": TENANT,
            "material_id": material,
            "sn_id": None,
            "stock_quantity": "0.0000",
            "reserved_quantity": "0.0000",
            "total_cost": "5.0000",
            "tracking_version": 1,
            "del_flag": "0",
        },
    )
    rows, zero_bad = execute(c, schema, balances, ["tenant", "material"], [TENANT, material])
    c.require(rows == [["1"]], "Zero stock retained nonzero book value without failing readiness")
    c.query(schema, "UPDATE wm_material_stock SET total_cost=0 WHERE stock_id=" + str(stock) + ";")
    rows, zero_good = execute(c, schema, balances, ["tenant", "material"], [TENANT, material])
    c.require(rows == [["0"]], "Settled zero stock should not retain a value error")
    c.insert(
        schema,
        "wm_valuation_group",
        {
            "valuation_group_id": group,
            "tenant_id": TENANT,
            "material_id": material,
            "quantity": "1.0000",
            "amount": "5.0000",
        },
    )
    roles = ["stockTenant", "stockMaterial", "groupTenant", "groupMaterial"]
    values = [TENANT, material, TENANT, material]
    rows, group_bad = execute(c, schema, groups, roles, values)
    c.require(rows == [["1"]], "Nonzero valuation group without stock passed reverse readiness")
    c.insert(
        schema,
        "wm_material_stock",
        {
            "stock_id": c.uid(),
            "tenant_id": OTHER_TENANT,
            "material_id": material,
            "valuation_group_id": group,
            "stock_quantity": "1.0000",
            "total_cost": "5.0000",
            "tracking_version": 1,
            "del_flag": "0",
        },
    )
    c.require(
        c.query(schema, bind_sql(groups, values)).rows == [["1"]],
        "Foreign tenant stock covered an orphan valuation group",
    )
    c.query(
        schema,
        "UPDATE wm_valuation_group SET quantity=0,amount=0 WHERE valuation_group_id="
        + str(group)
        + ";",
    )
    rows, group_good = execute(c, schema, groups, roles, values)
    c.require(rows == [["0"]], "Settled 0/0 historical valuation group should be allowed")
    return {
        "material_id": str(material),
        "zero_stock": [zero_bad, zero_good],
        "valuation_group": [group_bad, group_good],
        "boundary": "Actual current readiness COUNTs only; full material cutover authorization and concurrent inventory posting are not executed",
    }


def run_business_reader_cases(c):
    c.case(
        "actual_readiness_zero_stock_value_and_orphan_valuation_group",
        lambda: readiness_zero_stock_value_and_orphan_groups(c),
        ["migration_erp"],
    )
    from policy_cases import run_policy_cases

    run_policy_cases(c)
    c.case(
        "actual_readiness_orphan_in_stock_identity_reverse_check",
        lambda: orphan_in_stock_identity_readiness(c),
        ["migration_erp"],
    )
    c.case(
        "actual_canonical_erp_code_and_historical_mes_mapping",
        lambda: canonical_identity_projection(c),
        ["migration_erp", "migration_mes"],
    )
    c.case(
        "actual_batch_only_stock_source_movements_customer_no_sn",
        lambda: batch_only_facts(c),
        ["migration_erp"],
    )
    c.case(
        "actual_mes_batch_multiple_child_lots_allocation_pagination",
        lambda: batch_multiple_lots(c),
        ["migration_mes"],
    )
    c.case(
        "actual_unallocated_work_order_and_product_scopes",
        lambda: work_order_scopes(c),
        ["migration_mes"],
    )
    c.case(
        "actual_product_output_command_lot_tenant_constraints",
        lambda: product_output_constraints(c),
        ["migration_mes"],
    )
