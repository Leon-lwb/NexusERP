"""在真实数据库中验证库存工单策略原档的存储与加锁约束。"""

import hashlib
import json
import re
from concurrent.futures import ThreadPoolExecutor
from harness import sql_value
from java_sql import bind_sql
from reader_cases import TENANT, OTHER_TENANT, statements_for
from business_reader_cases import execute

OWNER = "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/PrdWorkOrderTrackingPolicyService.java"
TABLE = "wm_work_order_tracking_policy"


def seed(c):
    schema = c.dbs["legacy_erp"]
    wo, material, other = [c.uid() for _ in range(3)]
    policy = json.dumps(
        {
            "batchManaged": True,
            "serialManaged": True,
            "allowMixedLot": False,
            "version": "9007199254740993",
            "stage": "STRICT",
        },
        separators=(",", ":"),
    )
    source = json.dumps(
        {"erpWoId": str(wo), "materials": [str(material), str(other)]}, separators=(",", ":")
    )
    values = {
        "snapshot_id": c.uid(),
        "tenant_id": TENANT,
        "erp_wo_id": wo,
        "material_id": material,
        "snapshot_version": 1,
        "policy_version": 9007199254740993,
        "policy_json": policy,
        "policy_hash": hashlib.sha256(policy.encode()).hexdigest(),
        "source_json": source,
        "source_hash": hashlib.sha256(source.encode()).hexdigest(),
        "snapshot_hash": "c" * 64,
        "provenance": "ERP_RELEASE",
        "evidence_ref": "SYNTH-ERP-RELEASE/" + str(wo),
        "frozen_at": "2026-10-19 03:04:05.123",
        "effective_from": "2026-10-19 03:04:05.123",
        "actor_id": None,
    }
    c.insert(schema, TABLE, values)
    c.insert(schema, TABLE, {**values, "snapshot_id": c.uid(), "material_id": other})
    return schema, wo, material, other, values


def bank_constraints(c):
    schema, wo, material, other, values = seed(c)
    duplicate = c.insert(schema, TABLE, {**values, "snapshot_id": c.uid()}, error=1062)
    c.insert(schema, TABLE, {**values, "snapshot_id": c.uid(), "tenant_id": OTHER_TENANT})
    null_tenant = c.insert(
        schema, TABLE, {**values, "snapshot_id": c.uid(), "tenant_id": None}, error=1048
    )
    statements = [q for q in statements_for(c, OWNER) if "FROM " + TABLE + " WHERE" in q["sql"]]
    c.require(
        {q["variant"] for q in statements} == {"lock_true", "lock_false"},
        "Policy bank must execute both current read and locked-read SQL",
    )
    evidence = []
    for query in statements:
        rows, item = execute(c, schema, query["sql"], ["tenant", "erpWoId"], [TENANT, wo])
        c.require(len(rows) == 2, "Policy bank crossed tenant or lost one material")
        evidence.append(item)
    row = c.query(
        schema,
        "SELECT policy_version,policy_json,policy_hash,source_json,source_hash,DATE_FORMAT(frozen_at,'%Y-%m-%d %H:%i:%s.%f'),DATE_FORMAT(effective_from,'%Y-%m-%d %H:%i:%s.%f') FROM "
        + TABLE
        + " WHERE tenant_id="
        + sql_value(TENANT)
        + " AND erp_wo_id="
        + str(wo)
        + " AND material_id="
        + str(material)
        + ";",
    ).first
    c.require(
        row[:5]
        == [
            str(values["policy_version"]),
            values["policy_json"],
            values["policy_hash"],
            values["source_json"],
            values["source_hash"],
        ],
        "Policy source/version/hash lost precision",
    )
    c.require(
        row[5:] == ["2026-10-19 03:04:05.123000"] * 2,
        "Policy bank truncated CURRENT_TIMESTAMP(3) evidence precision",
    )
    empty_query = next(q["sql"] for q in statements if q["variant"] == "lock_false")
    c.require(
        not c.query(schema, bind_sql(empty_query, [TENANT, c.uid()])).rows,
        "Absent legacy bank was reconstructed as current master policy",
    )
    return {
        "table": TABLE,
        "work_order_id": str(wo),
        "materials": list(map(str, [material, other])),
        "duplicate_error": duplicate.errors,
        "null_tenant_error": null_tenant.errors,
        "actual_queries": evidence,
        "frozen_row": row,
        "boundary": "Actual bank storage and source SELECTs; JSON/group hash semantic verification, legacy UNKNOWN projection and adoption authorization are Java responsibilities",
    }


def bank_row_lock(c):
    schema, wo, material, other, values = seed(c)
    query = next(
        q["sql"]
        for q in statements_for(c, OWNER)
        if "FROM " + TABLE + " WHERE" in q["sql"] and q["variant"] == "lock_true"
    )
    a, b = c.box.session(schema), c.box.session(schema)
    predicate = (
        "tenant_id="
        + sql_value(TENANT)
        + " AND erp_wo_id="
        + str(wo)
        + " AND material_id="
        + str(material)
    )
    pool = ThreadPoolExecutor(max_workers=1)
    try:
        a.execute("START TRANSACTION;")
        c.require(
            len(a.execute(bind_sql(query, [TENANT, wo])).rows) == 2,
            "Locked bank read did not see both policies",
        )
        b.execute("START TRANSACTION;")
        pending = pool.submit(
            b.execute,
            "UPDATE "
            + TABLE
            + " SET snapshot_id=snapshot_id WHERE "
            + predicate
            + "; SELECT ROW_COUNT();",
        )
        wait = c.wait_for_lock(b.connection_id, pending, expected_table=TABLE)
        a.execute("COMMIT;")
        pending.result(timeout=12)
        b.execute("COMMIT;")
        c.require(
            c.query(schema, "SELECT policy_hash FROM " + TABLE + " WHERE " + predicate + ";").scalar
            == values["policy_hash"],
            "Lock probe changed immutable evidence",
        )
        return {
            "connections": [a.connection_id, b.connection_id],
            "lock_wait_evidence": wait,
            "actual_locked_sql": bind_sql(query, [TENANT, wo]),
            "evidence_unchanged": True,
            "boundary": "Two real MySQL connections; no-op SQL contender tests row locking, not Java release/adoption transaction integration",
        }
    finally:
        a.close()
        b.close()
        pool.shutdown(wait=True)


ADOPTION = "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/PrdWorkOrderPolicyAdoptionService.java"
ADOPTION_TABLE = "wm_work_order_policy_adoption"


def approval_seed(c):
    schema = c.dbs["legacy_erp"]
    wo, approval = c.uid(), c.uid()
    source = json.dumps(
        {"erpWoId": str(wo), "materialId": "9007199254740993", "synthetic": True},
        separators=(",", ":"),
    )
    approval_json = json.dumps({"syntheticApprovalId": str(approval)}, separators=(",", ":"))
    values = {
        "adoption_id": approval,
        "tenant_id": TENANT,
        "command_id": "SYNTH-ADOPT-" + str(approval),
        "erp_wo_id": wo,
        "expected_wo_hash": "a" * 64,
        "source_json": source,
        "source_hash": hashlib.sha256(source.encode()).hexdigest(),
        "approval_json": approval_json,
        "approval_hash": hashlib.sha256(approval_json.encode()).hexdigest(),
        "request_hash": "b" * 64,
        "proposed_by": 9007199254740992,
        "approved_by": 9007199254740993,
        "approved_at": "2026-10-19 03:00:00.123",
        "expires_at": "2026-10-20 03:00:00.123",
        "status": "APPROVED",
        "version": 9007199254740993,
        "applied_at": None,
        "snapshot_hash": None,
    }
    c.insert(schema, ADOPTION_TABLE, values)
    c.insert(
        schema,
        "prd_work_order",
        {
            "wo_id": wo,
            "tenant_id": TENANT,
            "material_id": 201,
            "wo_status": "IN_PROGRESS",
            "del_flag": "0",
        },
    )
    return schema, values


def adoption_update(c):
    source = c.snapshot.text(ADOPTION)
    match = re.search(r'jdbc\.update\(("UPDATE wm_work_order_policy_adoption[^"\r\n]+")', source)
    c.require(match is not None, "Inspect changed adoption CAS source")
    sql = json.loads(match[1])
    c.require(
        sql.count("?") == 5
        and "status='APPROVED' AND version=?" in sql
        and "version=version+1" in sql,
        "Adoption CAS no longer matches declared parameter order/state/version",
    )
    return sql


def approval_storage_and_cas(c):
    schema, values = approval_seed(c)
    approval = values["adoption_id"]
    wo = values["erp_wo_id"]
    duplicate = c.insert(schema, ADOPTION_TABLE, {**values, "adoption_id": c.uid()}, error=1062)
    c.insert(schema, ADOPTION_TABLE, {**values, "adoption_id": c.uid(), "tenant_id": OTHER_TENANT})
    null_tenant = c.insert(
        schema, ADOPTION_TABLE, {**values, "adoption_id": c.uid(), "tenant_id": None}, error=1048
    )
    evidence = []
    for query in statements_for(c, ADOPTION):
        target = wo if "FROM prd_work_order" in query["sql"] else approval
        rows, item = execute(
            c,
            schema,
            query["sql"],
            ["tenant", "workOrderId" if target == wo else "adoptionId"],
            [TENANT, target],
        )
        c.require(len(rows) == 1, "Actual adoption SELECT lost its one real owner row")
        c.require(
            not c.query(schema, bind_sql(query["sql"], [OTHER_TENANT, target])).rows,
            "Adoption SELECT crossed tenant",
        )
        evidence.append(item)
    update = adoption_update(c)
    roles = ["appliedAt", "snapshotHash", "tenant", "adoptionId", "expectedVersion"]
    args = ["2026-10-19 03:04:05.123", "c" * 64, TENANT, approval, values["version"]]
    session = c.box.session(schema)
    try:
        session.execute("START TRANSACTION;")
        changed = session.execute(bind_sql(update, args) + " SELECT ROW_COUNT();").scalar
        c.require(changed == "1", "Approved source CAS did not update exactly once")
        session.execute("ROLLBACK;")
        current = c.query(
            schema,
            "SELECT status,version,applied_at,snapshot_hash FROM "
            + ADOPTION_TABLE
            + " WHERE adoption_id="
            + str(approval)
            + ";",
        ).first
        c.require(
            current == ["APPROVED", str(values["version"]), "NULL", "NULL"],
            "Rollback retained a half-applied adoption",
        )
        session.execute("START TRANSACTION;")
        session.execute(bind_sql(update, args))
        session.execute("COMMIT;")
        repeated = session.execute(bind_sql(update, args) + " SELECT ROW_COUNT();").scalar
        c.require(repeated == "0", "A stale adoption command rewrote the committed state")
    finally:
        session.close()
    actual = c.query(
        schema,
        "SELECT status,version,proposed_by,approved_by,source_json,source_hash,DATE_FORMAT(applied_at,'%Y-%m-%d %H:%i:%s.%f'),snapshot_hash FROM "
        + ADOPTION_TABLE
        + " WHERE adoption_id="
        + str(approval)
        + ";",
    ).first
    c.require(
        actual
        == [
            "APPLIED",
            str(values["version"] + 1),
            str(values["proposed_by"]),
            str(values["approved_by"]),
            values["source_json"],
            values["source_hash"],
            "2026-10-19 03:04:05.123000",
            "c" * 64,
        ],
        "Adoption state/long IDs/JSON/timestamp/hash lost precision",
    )
    return {
        "adoption_id": str(approval),
        "duplicate_command_error": duplicate.errors,
        "null_tenant_error": null_tenant.errors,
        "actual_selects": evidence,
        "cas": {
            "sql": update,
            "bound_sql": bind_sql(update, args),
            "parameter_bindings": [
                {"ordinal": i, "role": role, "value": value}
                for i, (role, value) in enumerate(zip(roles, args), 1)
            ],
            "rollback_preserved_approved": True,
            "stale_affected": repeated,
        },
        "stored_row": actual,
        "boundary": "Actual SQL storage/CAS/rollback only. Independently approved archival evidence, actor permission and bank group hash validation remain Java/service responsibilities",
    }


def approval_two_connection_cas(c):
    schema, values = approval_seed(c)
    query = next(q["sql"] for q in statements_for(c, ADOPTION) if q["variant"] == "lock_true")
    update = adoption_update(c)
    args = ["2026-10-19 03:04:05.123", "d" * 64, TENANT, values["adoption_id"], values["version"]]
    a, b = c.box.session(schema), c.box.session(schema)
    pool = ThreadPoolExecutor(max_workers=1)
    try:
        a.execute("START TRANSACTION;")
        c.require(
            len(a.execute(bind_sql(query, [TENANT, values["adoption_id"]])).rows) == 1,
            "Adoption lock did not see the approved source row",
        )
        b.execute("START TRANSACTION;")
        pending = pool.submit(b.execute, bind_sql(update, args) + " SELECT ROW_COUNT();")
        wait = c.wait_for_lock(b.connection_id, pending, expected_table=ADOPTION_TABLE)
        c.require(
            a.execute(bind_sql(update, args) + " SELECT ROW_COUNT();").scalar == "1",
            "First adoption CAS did not win",
        )
        a.execute("COMMIT;")
        stale = pending.result(timeout=12).scalar
        b.execute("COMMIT;")
        c.require(stale == "0", "Concurrent stale adoption also consumed the approval")
        final = c.query(
            schema,
            "SELECT status,version FROM "
            + ADOPTION_TABLE
            + " WHERE adoption_id="
            + str(values["adoption_id"])
            + ";",
        ).first
        c.require(
            final == ["APPLIED", str(values["version"] + 1)],
            "Concurrent CAS produced the wrong terminal version",
        )
        return {
            "connections": [a.connection_id, b.connection_id],
            "actual_locked_sql": bind_sql(query, [TENANT, values["adoption_id"]]),
            "actual_update_sql": bind_sql(update, args),
            "lock_wait_evidence": wait,
            "stale_affected": stale,
            "final": final,
            "boundary": "Two real MySQL connections and actual source CAS; no Java approval authorization, bank writes or HTTP integration",
        }
    finally:
        a.close()
        b.close()
        pool.shutdown(wait=True)


def run_policy_cases(c):
    c.case(
        "actual_work_order_policy_bank_storage_precision_tenant",
        lambda: bank_constraints(c),
        ["migration_erp"],
    )
    c.case(
        "actual_work_order_policy_bank_two_connection_lock",
        lambda: bank_row_lock(c),
        ["migration_erp"],
    )
    c.case(
        "actual_work_order_policy_adoption_storage_cas_rollback",
        lambda: approval_storage_and_cas(c),
        ["migration_erp"],
    )
    c.case(
        "actual_work_order_policy_adoption_two_connection_cas",
        lambda: approval_two_connection_cas(c),
        ["migration_erp"],
    )
