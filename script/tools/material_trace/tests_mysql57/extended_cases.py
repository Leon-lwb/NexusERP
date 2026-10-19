"""Additional real-MySQL contracts; SQL simulation does not invoke Java services."""
from __future__ import annotations
import ast
import json
import re
from concurrent.futures import ThreadPoolExecutor
from harness import SqlFailure, ValidationError, ident, sql_value
from reader_cases import run_reader_cases

TENANT, OTHER_TENANT = "901001", "901002"
READER = "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesIdentityTraceReader.java"
SERIAL = "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpSerialIdentityService.java"


def extract_forward_query(source):
    body = re.search(r"\baffectedByIdentity\s*\([^)]*\)\s*\{(.*?)\n\s*\}", source, re.S)
    queries = re.findall(r'jdbc\.queryForList\("((?:\\.|[^"\\])*)"\s*[,)]', body[1]) if body else []
    if len(queries) != 1:
        raise ValidationError("Expected exactly one literal JDBC query in affectedByIdentity; inspect current reader")
    query = json.loads('"' + queries[0] + '"')
    if not query.startswith("SELECT DISTINCT net.identity_id") or "$.source.identityId" not in query:
        raise ValidationError("MES identity-forward query changed shape; inspect the real SQL before adapting the test")
    return query


def bind_forward_query(query, tenant, component, after, limit):
    if query.count("?") != 4:
        raise ValidationError("MES identity-forward parameter count changed")
    pieces = query.split("?")
    values = (tenant, after, str(component), limit + 1)
    return "".join(piece + sql_value(value) for piece, value in zip(pieces, values)) + pieces[-1] + ";"


def canonical_collision(c, deleted=False):
    schema = c.dbs["legacy_erp"]
    prefix = "SYNTHCOLL" + str(c.uid()) + "-"
    old_id, old_material, new_material, first_rule, second_rule, sid = [c.uid() for _ in range(6)]
    code = prefix + "000001"
    old = {"sn_id": old_id, "tenant_id": TENANT, "sn_code": code, "material_id": old_material,
           "rule_id": first_rule, "del_flag": "2" if deleted else "0"}
    c.insert(schema, "wm_sn", old)
    c.insert(schema, "wm_sn", {**old, "sn_id": c.uid(), "tenant_id": OTHER_TENANT, "sn_code": prefix + "999999"})
    seq = {"sequence_id": sid, "tenant_id": TENANT, "namespace": "SERIAL", "rule_key": "RULE_" + str(second_rule),
           "cycle_key": "NONE", "current_value": 0, "version": 0}
    c.insert(schema, "wm_serial_sequence", seq)
    candidate = {**old, "sn_id": c.uid(), "material_id": new_material, "rule_id": second_rule, "del_flag": "0"}
    conn = c.box.session(schema)
    predicate = "tenant_id=" + sql_value(TENANT) + " AND sequence_id=" + str(sid)
    try:
        conn.execute("START TRANSACTION;")
        conn.execute("UPDATE wm_serial_sequence SET current_value=1,version=version+1 WHERE " + predicate + " AND current_value=0;")
        failure = conn.execute(c.insert_sql(schema, "wm_sn", candidate), error=1062)
        conn.execute("ROLLBACK;")
        c.require(c.query(schema, "SELECT current_value,version FROM wm_serial_sequence WHERE " + predicate + ";").first == ["0", "0"], "Failed code reservation was not rolled back")
        occupied = c.query(schema, "SELECT sn_code,material_id,del_flag FROM wm_sn WHERE tenant_id=" + sql_value(TENANT) + " AND sn_code LIKE " + sql_value(prefix + "%") + ";").rows
        c.require(occupied == [[code, str(old_material), old["del_flag"]]], "Historical/prefix scan ignored occupied codes or crossed tenants")
        maximum = max(int(row[0][len(prefix):]) for row in occupied)
        c.require(maximum == 1, "History scan failed to include other material/rule or deleted code")
        conn.execute("START TRANSACTION;")
        conn.execute("SELECT current_value FROM wm_serial_sequence WHERE " + predicate + " FOR UPDATE;")
        affected = conn.execute("UPDATE wm_serial_sequence SET current_value=2,version=version+1 WHERE " + predicate + " AND current_value=0; SELECT ROW_COUNT();").scalar
        c.require(affected == "1", "Fresh transaction could not reserve past the occupied code")
        conn.execute(c.insert_sql(schema, "wm_sn", {**candidate, "sn_id": c.uid(), "sn_code": prefix + "000002"}))
        conn.execute("COMMIT;")
        saved = c.query(schema, "SELECT sn_id,material_id,rule_id,del_flag FROM wm_sn WHERE tenant_id=" + sql_value(TENANT) + " AND sn_code=" + sql_value(code) + ";").first
        c.require(saved == [str(old_id), str(old_material), str(first_rule), old["del_flag"]], "Existing canonical identity was reused or changed")
        return {"mysql_duplicate_error": failure.errors, "existing_del_flag": old["del_flag"],
                "materials": [str(old_material), str(new_material)], "rules": [str(first_rule), str(second_rule)],
                "history_scan": occupied, "new_sequence_final": [2, 1],
                "boundary": "SQL collision/rollback/skip demonstration; Java collision retry and rule rendering are not executed"}
    finally:
        conn.close()


def json_functions(c):
    schema = c.dbs["legacy_mes"]
    samples = [("sql_null", None, "SQL_NULL"), ("missing", "{}", "SQL_NULL"),
               ("json_null", '{"source":{"identityId":null}}', "6E756C6C"),
               ("empty", '{"source":{"identityId":""}}', "")]
    for identity in ("9007199254740992", "9007199254740993", "9223372036854775807"):
        for as_number in (False, True):
            value = int(identity) if as_number else identity
            samples.append((identity + ("_number" if as_number else "_string"), json.dumps({"source": {"identityId": value}}), identity.encode().hex().upper()))
    results = []
    for name, payload, expected in samples:
        expr = "JSON_UNQUOTE(JSON_EXTRACT(" + sql_value(payload) + ",'$.source.identityId'))"
        actual = c.query(schema, "SELECT COALESCE(HEX(" + expr + "),'SQL_NULL'),COALESCE(" + expr + ",'[SQL NULL]');").first
        c.require(actual[0] == expected, "MySQL JSON extraction changed identity/null semantics: " + name + " " + str(actual))
        results.append({"sample": name, "hex": actual[0], "value": actual[1]})
    invalid = c.query(schema, "SELECT JSON_UNQUOTE(JSON_EXTRACT('synthetic-not-json','$.source.identityId'));", error=3141)
    c.report["constraint_boundaries"].append({"table": "lt_lot_allocation", "type": "longtext_json_requires_application_validation",
        "mysql_error": 3141, "meaning": "Invalid LONGTEXT JSON raises an extraction error; JSON null unquotes to text null, missing path is SQL NULL"})
    return {"samples": results, "invalid_json_errors": invalid.errors,
            "boundary": "Exact MySQL functions; API serialization and Java binding are outside this smoke test"}


def missing_sequence_initialization(c):
    """Barrier both missing-row locking reads before either first INSERT."""
    created = c.new_schema("missing_sequence_erp", "erp", subset=True)
    schema = c.dbs["missing_sequence_erp"]
    token = "SYNTHGAP" + str(c.uid())
    materials = [c.uid(), c.uid()]
    seed_sql = []
    def seed(table, values):
        statement = c.insert_sql(schema, table, values)
        c.query(schema, statement); seed_sql.append(statement)
    for material in materials:
        seed("bd_material", {"material_id": material, "tenant_id": TENANT,
            "material_code": "SYNTHMAT" + str(material), "status": 1, "del_flag": "0"})
    # Keep reservation-command gap locks disjoint, so this case isolates the missing sequence.
    for suffix in ("0", "2", "4"):
        seed("wm_serial_reservation", {"reservation_id": c.uid(), "tenant_id": TENANT,
            "command_id": token + suffix, "material_id": materials[0]})
    commands = [token + "1", token + "3"]
    reservation_ids = [c.uid(), c.uid()]
    reservation_sql = [c.insert_sql(schema, "wm_serial_reservation", {
        "reservation_id": reservation_ids[i], "tenant_id": TENANT, "command_id": commands[i],
        "material_id": materials[i], "quantity": 2, "fingerprint": "a" * 64,
        "cycle_key": "2026-10-18", "biz_date": "2026-10-18"}) for i in range(2)]
    predicate = "tenant_id=" + sql_value(TENANT) + " AND namespace='SERIAL' AND rule_key='BUILTIN_SN_V1' AND cycle_key='2026-10-18'"
    select = "SELECT sequence_id,current_value,version FROM wm_serial_sequence WHERE " + predicate + " FOR UPDATE;"
    sequence_ids = [c.uid(), c.uid()]
    initializers = [c.insert_sql(schema, "wm_serial_sequence", {"sequence_id": sequence_ids[i],
        "tenant_id": TENANT, "namespace": "SERIAL", "rule_key": "BUILTIN_SN_V1", "cycle_key": "2026-10-18",
        "current_value": 0, "version": 0}).rstrip(";") + " ON DUPLICATE KEY UPDATE rule_key=rule_key;" for i in range(2)]
    a, b = c.box.session(schema), c.box.session(schema)
    sessions = [a, b]
    timeline = ["-- Missing sequence reproduction. Use two real connections in the owned sandbox.",
                "-- First import missing_sequence_erp-create-only.sql into a fresh owned sandbox schema.",
                "USE " + ident(schema) + ";"] + seed_sql
    pool = ThreadPoolExecutor(max_workers=2)
    try:
        missing = []
        for i, conn in enumerate(sessions):
            begin = ("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION;\n"
                "SELECT reservation_id FROM wm_serial_reservation WHERE tenant_id=" + sql_value(TENANT) + " AND command_id=" + sql_value(commands[i]) + " FOR UPDATE;\n"
                "SELECT material_id FROM bd_material WHERE tenant_id=" + sql_value(TENANT) + " AND material_id=" + str(materials[i]) + " AND del_flag='0' FOR UPDATE;")
            start = conn.execute(begin)
            c.require(start.rows == [[str(materials[i])]], "Separate material lock or absent command precondition differs")
            conn.execute(reservation_sql[i])
            observed = conn.execute(select).rows
            c.require(not observed, "Sequence was already initialized; this must exercise first-row gap locks")
            missing.append(observed)
            timeline.extend(["-- SESSION " + str(i + 1), begin, reservation_sql[i], select])
        pending_a = pool.submit(a.execute, initializers[0])
        wait = c.wait_for_lock(a.connection_id, pending_a)
        c.require("insert intention waiting" in wait["transaction_excerpt"].lower(), "First insert did not wait on an insert-intention/gap lock")
        timeline.extend(["-- SESSION 1 async: must wait on SESSION 2's missing-key gap lock", initializers[0],
                         "-- SESSION 2: closes wait cycle; one transaction must receive 1213", initializers[1]])
        pending_b = pool.submit(b.execute, initializers[1])
        results = []
        for future in (pending_a, pending_b):
            try:
                result = future.result(timeout=12)
                results.append({"ok": True, "rows": result.rows})
            except SqlFailure as exc:
                c.require(bool(re.search(r"ERROR\s+1213\b", exc.output)), "Unexpected initialization SQL error: " + exc.output)
                results.append({"ok": False, "mysql_error": exc.output})
        c.require(sum(result["ok"] for result in results) == 1, "Expected exactly one deadlock victim and one initializer")
        winner = next(i for i, result in enumerate(results) if result["ok"])
        loser = 1 - winner
        status = c.query(schema, "SHOW ENGINE INNODB STATUS;").rows
        native = "\n".join("\t".join(row) for row in status)
        deadlock = native.split("LATEST DETECTED DEADLOCK", 1)[-1].split("TRANSACTIONS", 1)[0]
        c.require("wm_serial_sequence" in deadlock and "WE ROLL BACK TRANSACTION" in deadlock, "Native deadlock did not identify the sequence initialization")
        c.require(all("MySQL thread id " + str(conn.connection_id) in deadlock for conn in sessions), "Deadlock is not from the two test connections")
        (c.output / "sequence-gap-deadlock.txt").write_text(deadlock, encoding="utf-8")
        ranges = []
        def allocate(i):
            conn = sessions[i]
            seq_id, current, version = conn.execute(select).first
            current = int(current); end = current + 2
            cas = ("UPDATE wm_serial_sequence SET current_value=" + str(end) + ",version=version+1 WHERE tenant_id=" + sql_value(TENANT)
                + " AND sequence_id=" + seq_id + " AND current_value=" + str(current) + "; SELECT ROW_COUNT();")
            c.require(conn.execute(cas).scalar == "1", "Current-value CAS failed under the sequence lock")
            timeline.extend(["-- SESSION " + str(i + 1) + " sequence lock / CAS", select, cas])
            for value in range(current + 1, end + 1):
                insert = c.insert_sql(schema, "wm_sn", {"sn_id": c.uid(), "tenant_id": TENANT,
                    "sn_code": token + "SN" + str(value).zfill(6), "material_id": materials[i],
                    "registration_command": commands[i], "status": "REGISTERED", "del_flag": "0"})
                conn.execute(insert)
                timeline.extend(["-- SESSION " + str(i + 1), insert])
            conn.execute("COMMIT;")
            ranges.append({"material_id": str(materials[i]), "range": [current + 1, end], "command_id": commands[i]})
            timeline.extend(["-- SESSION " + str(i + 1), "COMMIT;"])
        allocate(winner)
        # Explicitly end the failed transaction and rerun from the command/material locks.
        sessions[loser].execute("ROLLBACK; START TRANSACTION;")
        absent = sessions[loser].execute("SELECT reservation_id FROM wm_serial_reservation WHERE tenant_id=" + sql_value(TENANT)
            + " AND command_id=" + sql_value(commands[loser]) + " FOR UPDATE;").rows
        c.require(not absent, "Deadlock victim's reservation survived transaction rollback")
        sessions[loser].execute("SELECT material_id FROM bd_material WHERE tenant_id=" + sql_value(TENANT)
            + " AND material_id=" + str(materials[loser]) + " FOR UPDATE;")
        sessions[loser].execute(reservation_sql[loser])
        sessions[loser].execute(initializers[loser])
        timeline.extend(["-- SESSION " + str(loser + 1) + " retry in a NEW transaction", "ROLLBACK; START TRANSACTION;", reservation_sql[loser], initializers[loser]])
        allocate(loser)
        final = c.query(schema, "SELECT current_value,version FROM wm_serial_sequence WHERE " + predicate + ";").first
        rows = c.query(schema, "SELECT material_id,sn_code FROM wm_sn WHERE tenant_id=" + sql_value(TENANT) + " ORDER BY sn_code;").rows
        c.require(final == ["4", "2"] and len(rows) == 4 and len({row[1] for row in rows}) == 4, "Retry allocated overlapping or incomplete identities")
        c.require({row[0] for row in rows} == set(map(str, materials)), "Cross-material reservation was not exercised")
        (c.output / "sequence-gap-init-reproduction.sql").write_text("\n".join(timeline), encoding="utf-8")
        return {"schema": created, "connections": [a.connection_id, b.connection_id], "initial_missing_reads": missing,
            "material_ids": list(map(str, materials)), "initialization_results": results, "lock_wait_evidence": wait,
            "deadlock_victim_connection": sessions[loser].connection_id, "victim_reservation_rolled_back": True,
            "java_source_for_comparison": SERIAL, "java_source_sha256": c.snapshot.hashes[SERIAL],
            "new_transaction_retry": True, "ranges": ranges, "final_sequence": final,
            "boundary": "Historic missing-key locking variant: real deadlock plus explicit fresh-transaction retry; current service algorithm is tested separately"}
    finally:
        a.close(); b.close(); pool.shutdown(wait=True)


def reader_query_compiles(c):
    query = extract_forward_query(c.snapshot.text(READER))
    statement = bind_forward_query(query, TENANT, "9007199254740993", 0, 20)
    failures = []
    for key in ("fresh_mes", "legacy_mes"):
        try:
            c.query(c.dbs[key], statement)
        except SqlFailure as exc:
            failures.append(exc)
            probes = []
            minimal = ["SELECT w.source_allocation_id FROM lt_wo_consumption w LIMIT 0;",
                "SELECT c.entry_id FROM lt_sn_consumption c JOIN lt_wo_consumption w ON w.tenant_id=c.tenant_id AND w.consumption_id=c.wo_consumption_id LIMIT 0;"]
            for sql in minimal:
                try:
                    c.query(c.dbs[key], sql)
                    probes.append({"sql": sql, "status": "PASS"})
                except SqlFailure as probe:
                    probes.append({"sql": sql, "status": "FAIL", "native_mysql_output": probe.output})
            collations = c.query(c.dbs[key], "SELECT TABLE_NAME,COLUMN_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME='tenant_id' AND TABLE_NAME IN ('lt_sn_consumption','lt_wo_consumption','lt_lot_allocation') ORDER BY TABLE_NAME;").rows
            finding = {"java_source": READER, "java_source_sha256": c.snapshot.hashes[READER], "schema": c.dbs[key],
                "sql": statement, "native_mysql_output": exc.output, "minimal_probes": probes, "tenant_collations": collations,
                "owner_action": "Align generator, installation and incremental DDL with the actual reader SQL; validation never alters owner DDL"}
            c.report.setdefault("sql_contract_findings", []).append(finding)
            (c.output / (key + "-reader-contract-reproduction.sql")).write_text("USE " + ident(c.dbs[key]) + ";\n" + "\n".join(minimal) + "\n" + statement, encoding="utf-8")
    if failures:
        raise failures[0]
    return {"schemas": [c.dbs[key] for key in ("fresh_mes", "legacy_mes")], "query_compiles": True,
        "java_source_sha256": c.snapshot.hashes[READER]}


def identity_forward_reader(c):
    schema = c.dbs["legacy_mes"]
    query = extract_forward_query(c.snapshot.text(READER))
    component, adjacent = "9007199254740993", "9007199254740992"
    products = [9007199254741101 + i for i in range(10)]
    lot, work_order = c.uid(), c.uid()
    fixtures = []
    def add(product, payload, consume="1.2500", reverse=None, tenant=TENANT, allocation_tenant=None, work_tenant=None):
        allocation, consumption = c.uid(), c.uid()
        allocation_tenant = tenant if allocation_tenant is None else allocation_tenant
        work_tenant = tenant if work_tenant is None else work_tenant
        c.insert(schema, "lt_lot_allocation", {"allocation_id": allocation, "tenant_id": allocation_tenant,
            "command_id": "SYNTH-ALLOC-" + str(allocation), "line_no": 1, "lot_id": lot,
            "erp_batch_id": None, "material_id": 301, "quantity": consume,
            "source_type": "ERP_SN", "source_id": c.uid(), "evidence_json": json.dumps(payload), "evidence_hash": "b" * 64})
        c.insert(schema, "lt_wo_consumption", {"consumption_id": consumption, "tenant_id": work_tenant,
            "wo_id": work_order, "lot_id": lot, "material_id": 301, "consumed_qty": consume,
            "source_allocation_id": allocation})
        entry = c.uid()
        row = {"entry_id": entry, "tenant_id": tenant, "command_id": "SYNTH-CONSUME-" + str(entry), "line_no": 1,
            "identity_id": product, "local_sn_id": product + 1000, "component_material_id": 301,
            "component_lot_id": lot, "quantity": consume, "wo_consumption_id": consumption,
            "operation_ref": "SYNTH-OP-" + str(entry), "entry_type": "CONSUME", "original_entry_id": None}
        c.insert(schema, "lt_sn_consumption", row)
        if reverse is not None:
            c.insert(schema, "lt_sn_consumption", {**row, "entry_id": c.uid(), "command_id": "SYNTH-REVERSE-" + str(entry),
                "entry_type": "REVERSE", "original_entry_id": entry, "quantity": reverse})
        fixtures.append({"product": str(product), "tenant": tenant, "source_identity": payload.get("source", {}).get("identityId"),
            "lot_id": str(lot), "erp_batch_id": None, "reverse": reverse,
            "allocation_tenant": allocation_tenant, "work_tenant": work_tenant})
    source = {"source": {"identityId": component}}
    add(products[0], source)
    add(products[1], {"source": {"identityId": adjacent}})
    add(products[2], source, consume="2.0000", reverse="2.0000")
    add(products[3], source, consume="3.0000", reverse="1.0000")
    add(products[3], source, consume="0.5000")  # DISTINCT across separate source consumptions.
    add(products[4], {})
    add(products[5], {"source": {"identityId": None}})
    add(products[6], {"source": {"identityId": ""}})
    add(products[7], source, tenant=OTHER_TENANT)
    add(products[8], source, allocation_tenant=OTHER_TENANT)
    add(products[9], source, work_tenant=OTHER_TENANT)
    def read(tenant, identity, after=0, limit=20):
        return [row[0] for row in c.query(schema, bind_forward_query(query, tenant, identity, after, limit)).rows]
    exact, nearby = read(TENANT, component), read(TENANT, adjacent)
    c.require(exact == [str(products[0]), str(products[3])], "SN-only forward query broadened across a lot, reversal or tenant boundary: " + str(exact))
    c.require(nearby == [str(products[1])], "Adjacent long component ID was rounded or coalesced")
    c.require(read(TENANT, component, products[0]) == [str(products[3])], "Numeric identity cursor lost precision")
    c.require(read(TENANT, component, limit=1) == exact, "Reader limit+1 contract differs")
    c.require(read(OTHER_TENANT, component) == [str(products[7])], "Joined tenant predicates are not consistent")
    c.require(not read(TENANT, "9223372036854775807"), "Unknown exact component unexpectedly broadened")
    (c.output / "mes-identity-forward-query.sql").write_text(bind_forward_query(query, TENANT, component, 0, 20), encoding="utf-8")
    return {"java_source": READER, "java_source_sha256": c.snapshot.hashes[READER], "query": query,
        "component": component, "adjacent_component": adjacent, "selected": exact, "adjacent_selected": nearby,
        "fixtures": fixtures, "boundary": "Actual Java SQL executed in MySQL; Java code, JDBC serialization and HTTP routing not executed"}


def material_reservation_constraints(c):
    schema = c.dbs["legacy_erp"]
    reservation, line = c.uid(), c.uid()
    header = {"reservation_id": reservation, "tenant_id": TENANT, "command_id": "SYNTH-MATERIAL-" + str(reservation),
        "erp_wo_id": c.uid(), "mes_wo_id": c.uid(), "requirement_detail_id": c.uid(), "material_id": 301,
        "policy_version": 2, "quantity": "12.3456", "content_hash": "a" * 64}
    c.insert(schema, "wm_trace_material_reservation", header)
    c.insert(schema, "wm_trace_material_reservation", {**header, "reservation_id": c.uid()}, error=1062)
    c.insert(schema, "wm_trace_material_reservation", {**header, "reservation_id": c.uid(), "tenant_id": OTHER_TENANT})
    values = {"reservation_line_id": line, "tenant_id": TENANT, "reservation_id": reservation, "line_no": 1,
        "source_stock_id": c.uid(), "identity_id": 9007199254740993, "erp_batch_id": None,
        "quantity": "12.3456"}
    c.insert(schema, "wm_trace_material_reservation_line", values)
    c.insert(schema, "wm_trace_material_reservation_line", {**values, "reservation_line_id": c.uid()}, error=1062)
    c.insert(schema, "wm_trace_material_reservation_line", {**values, "reservation_line_id": c.uid(), "tenant_id": OTHER_TENANT})
    actual = c.query(schema, "SELECT identity_id,erp_batch_id,quantity,consumed_quantity,version FROM wm_trace_material_reservation_line WHERE reservation_line_id=" + str(line) + ";").first
    c.require(actual == ["9007199254740993", "NULL", "12.3456", "0.0000", "1"], "Actual reservation defaults/precision differ")
    return {"duplicate_errors": [1062, 1062], "cross_tenant_keys": "independent", "sn_only_line": actual}


def consumption_snapshot_constraints(c, mes=False):
    ds = "mes" if mes else "erp"; schema = c.dbs["legacy_" + ds]
    table = "pro_completion_material_allocation" if mes else "wm_trace_material_consumption"
    revision = "revision" if mes else "manifest_revision"
    row = {"tenant_id": TENANT, "manifest_id": c.uid(), revision: 1, "allocation_id": c.uid(),
        "material_id": 301, "identity_id": 9007199254740993, "erp_batch_id": None,
        "quantity": "7.1234", "source_version": 2, "evidence_hash": "c" * 64, "source_event_id": "SYNTH-EVIDENCE-" + str(c.uid())}
    if not mes: row.update(consumption_id=c.uid(), erp_wo_id=c.uid())
    c.insert(schema, table, row)
    duplicate = {**row}
    if not mes: duplicate["consumption_id"] = c.uid()
    c.insert(schema, table, duplicate, error=1062)
    other = {**row, "tenant_id": OTHER_TENANT}
    if not mes: other["consumption_id"] = c.uid()
    c.insert(schema, table, other)
    revised = {**row, revision: 2}
    if not mes: revised["consumption_id"] = c.uid()
    c.insert(schema, table, revised)
    actual = c.query(schema, "SELECT identity_id,erp_batch_id,quantity FROM " + ident(table)
        + " WHERE tenant_id=" + sql_value(TENANT) + " AND manifest_id=" + str(row["manifest_id"]) + " AND " + revision + "=1;").first
    c.require(actual == ["9007199254740993", "NULL", "7.1234"], "Frozen consumption lost string ID/decimal precision or invented batch")
    return {"table": table, "duplicate_error": 1062, "revision_and_tenant_keys": "independent", "sn_only_snapshot": actual,
        "boundary": "DDL key/null/precision contract; authorization, stock sufficiency and snapshot immutability require application enforcement"}


def run_extended(c):
    c.case("sequence_absent_gap_lock_regression_and_fresh_transaction_retry", lambda: missing_sequence_initialization(c), ["migration_erp"])
    c.case("current_service_absent_sequence_two_materials", lambda: current_missing_sequence_initialization(c), ["migration_erp"])
    c.case("current_first_reservation_history_scan_gap_deadlock_retry", lambda: current_missing_sequence_initialization(c, history_scan=True), ["migration_erp"])
    c.case("serial_collision_savepoint_burns_window_and_rolls_back_half_batch", lambda: collision_window_savepoint(c), ["migration_erp"])
    for deleted in (False, True):
        name = "serial_prefix_collision_" + ("historical_deleted" if deleted else "existing_canonical")
        c.case(name, lambda deleted=deleted: canonical_collision(c, deleted), ["migration_erp"])
    c.case("mes_json_identity_null_and_exact_long_ids", lambda: json_functions(c), ["migration_mes"])
    c.case("mes_actual_identity_forward_sql_compiles", lambda: reader_query_compiles(c), ["install_mes", "migration_mes"])
    c.case("mes_actual_identity_forward_query_sn_only_reversal_tenant_cursor", lambda: identity_forward_reader(c), ["mes_actual_identity_forward_sql_compiles"])
    c.case("erp_posting_group_source_hash_policy_contract", lambda: posting_group_source_policy_contract(c), ["install_erp", "migration_erp"])
    c.case("approved_thirty_table_export_matches_physical_schema", lambda: export_thirty_table_contract(c), ["install_erp", "migration_erp", "install_mes", "migration_mes"])
    c.case("erp_actual_material_reservation_header_line_constraints", lambda: material_reservation_constraints(c), ["migration_erp"])
    c.case("erp_actual_material_consumption_constraints", lambda: consumption_snapshot_constraints(c), ["migration_erp"])
    c.case("mes_frozen_material_allocation_constraints", lambda: consumption_snapshot_constraints(c, mes=True), ["migration_mes"])
    run_reader_cases(c)
    from strict_cases import run_strict_cases
    run_strict_cases(c)


def current_missing_sequence_initialization(c, history_scan=False):
    source = c.snapshot.text(SERIAL)
    read = re.search(r'var priorSequence=one\("([^"\n]+)"', source)
    historical = re.search(r'jdbc\.queryForList\("(SELECT sn_code FROM wm_sn WHERE tenant_id=\? AND sn_code LIKE \?[^"\n]*)"', source)
    c.require(read is not None and read[1].count("?") == 3, "Inspect current serial service initial-read contract")
    c.require("FOR UPDATE" not in read[1].upper(), "Current service reverted to gap-locking its initially missing sequence; see deadlock regression")
    key = "current_history_scan_erp" if history_scan else "current_initial_sequence_erp"
    created = c.new_schema(key, "erp", subset=True); schema = c.dbs[key]
    token = "SYNTHINIT" + str(c.uid()); rule_key = "9" * 64; cycle = "2026-10-19"
    pieces = read[1].split("?")
    initial_read = "".join(piece + sql_value(value) for piece, value in zip(pieces, (TENANT, rule_key, cycle))) + pieces[-1] + ";"
    history_sql = None
    if history_scan:
        c.require(historical is not None and historical[1].count("?") == 2, "Inspect current historicalMaximum SQL")
        pieces = historical[1].split("?")
        history_sql = pieces[0] + sql_value(TENANT) + pieces[1] + sql_value(token + "%") + pieces[2] + ";"
    materials, commands = [c.uid(), c.uid()], [token + "1", token + "3"]
    for material in materials:
        c.insert(schema, "bd_material", {"material_id": material, "tenant_id": TENANT,
            "material_code": "SYNTHMAT" + str(material), "status": 1, "del_flag": "0"})
    for suffix in ("0", "2", "4"):
        c.insert(schema, "wm_serial_reservation", {"reservation_id": c.uid(), "tenant_id": TENANT,
            "command_id": token + suffix, "material_id": materials[0]})
    reservations = [c.insert_sql(schema, "wm_serial_reservation", {"reservation_id": c.uid(), "tenant_id": TENANT,
        "command_id": commands[i], "material_id": materials[i], "quantity": 2}) for i in range(2)]
    initializers = [c.insert_sql(schema, "wm_serial_sequence", {"sequence_id": c.uid(), "tenant_id": TENANT,
        "namespace": "SERIAL", "rule_key": rule_key, "cycle_key": cycle, "current_value": 0, "version": 0}).rstrip(";")
        + " ON DUPLICATE KEY UPDATE rule_key=rule_key;" for _ in range(2)]
    predicate = "tenant_id=" + sql_value(TENANT) + " AND namespace='SERIAL' AND rule_key=" + sql_value(rule_key) + " AND cycle_key=" + sql_value(cycle)
    a, b = c.box.session(schema), c.box.session(schema)
    sessions = [a, b]; pool = ThreadPoolExecutor(max_workers=1); ranges = []; deadlock_errors = []
    def begin(i):
        conn = sessions[i]
        conn.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION;")
        command_read = conn.execute("SELECT reservation_id FROM wm_serial_reservation WHERE tenant_id=" + sql_value(TENANT)
            + " AND command_id=" + sql_value(commands[i]) + " FOR UPDATE;").rows
        c.require(not command_read, "Reservation command already existed or rollback was incomplete")
        conn.execute("SELECT material_id FROM bd_material WHERE tenant_id=" + sql_value(TENANT) + " AND material_id=" + str(materials[i]) + " AND del_flag='0' FOR UPDATE;")
        conn.execute(reservations[i])
        return conn.execute(initial_read).rows
    def allocate(i):
        conn = sessions[i]
        sid, current = conn.execute("SELECT sequence_id,current_value FROM wm_serial_sequence WHERE " + predicate + " FOR UPDATE;").first
        start = int(current); end = start + 2
        changed = conn.execute("UPDATE wm_serial_sequence SET current_value=" + str(end) + ",version=version+1 WHERE tenant_id=" + sql_value(TENANT)
            + " AND sequence_id=" + sid + " AND current_value=" + current + "; SELECT ROW_COUNT();").scalar
        c.require(changed == "1", "Current service SQL failed current-value CAS")
        codes = [token + str(value).zfill(6) for value in range(start + 1, end + 1)]
        occupied = conn.execute("SELECT sn_code FROM wm_sn WHERE tenant_id=" + sql_value(TENANT)
            + " AND sn_code IN (" + ",".join(map(sql_value, codes)) + ") ORDER BY sn_code FOR UPDATE;").rows
        c.require(not occupied, "Unexpected occupied synthetic prefix")
        conn.execute("SAVEPOINT sn_window;")
        for code in codes:
            conn.execute(c.insert_sql(schema, "wm_sn", {"sn_id": c.uid(), "tenant_id": TENANT, "material_id": materials[i],
                "sn_code": code, "registration_command": commands[i], "del_flag": "0"}))
        conn.execute("RELEASE SAVEPOINT sn_window; COMMIT;")
        ranges.append({"material_id": str(materials[i]), "range": [start + 1, end]})
    def deadlock(exc):
        c.require(bool(re.search(r"ERROR\s+1213\b", exc.output)), "Unexpected first-reservation failure: " + exc.output)
        deadlock_errors.append(exc.output)
    try:
        for i, conn in enumerate(sessions):
            c.require(not begin(i), "Initially missing shared sequence was preinitialized")
            if history_sql:
                c.require(not conn.execute(history_sql).rows, "Synthetic initial history was not empty")
        a.execute(initializers[0])
        pending = pool.submit(b.execute, initializers[1])
        wait = c.wait_for_lock(b.connection_id, pending)
        victim = None
        try:
            allocate(0)
        except SqlFailure as exc:
            deadlock(exc); victim = 0
            pending.result(timeout=12); allocate(1)
        else:
            try:
                pending.result(timeout=12)
            except SqlFailure as exc:
                deadlock(exc); victim = 1
            else:
                allocate(1)
        if victim is not None:
            native = "\n".join("\t".join(row) for row in c.query(schema, "SHOW ENGINE INNODB STATUS;").rows)
            excerpt = native.split("LATEST DETECTED DEADLOCK", 1)[-1].split("TRANSACTIONS", 1)[0]
            c.require("wm_sn" in excerpt and "wm_serial_sequence" in excerpt, "Current-path deadlock did not involve history SN and sequence locks")
            (c.output / "current-history-prefix-deadlock.txt").write_text(excerpt, encoding="utf-8")
            sessions[victim].execute("ROLLBACK;")
            c.require(bool(begin(victim)), "Retry did not observe winner's committed sequence")
            sessions[victim].execute(initializers[victim])
            allocate(victim)
        if history_sql and "FOR UPDATE" in historical[1].upper():
            c.require(len(deadlock_errors) == 1, "Current locking history scan did not exercise the expected gap-lock cycle")
        elif not history_scan:
            c.require(not deadlock_errors, "Sequence-only primitive unexpectedly deadlocked")
        final = c.query(schema, "SELECT current_value,version FROM wm_serial_sequence WHERE " + predicate + ";").first
        counts = c.query(schema, "SELECT material_id,COUNT(*) FROM wm_sn GROUP BY material_id ORDER BY material_id;").rows
        c.require(final == ["4", "2"] and counts == [[str(material), "2"] for material in sorted(materials)], "Concurrent initialization produced overlapping/incomplete identities")
        return {"schema": created, "connections": [a.connection_id, b.connection_id], "both_initial_reads_empty": True,
            "actual_initial_read": read[1], "actual_history_scan": historical[1] if history_scan else None,
            "java_source_sha256": c.snapshot.hashes[SERIAL], "unique_index_wait": wait,
            "deadlock_errors": deadlock_errors, "fresh_transaction_retry": victim is not None,
            "ranges": ranges, "final_sequence": final,
            "boundary": "Current SQL lock order, historical scan when requested, upsert/CAS and explicit retry executed; Java TransactionTemplate itself is not invoked"}
    finally:
        a.close(); b.close(); pool.shutdown(wait=True)


def collision_window_savepoint(c):
    schema, sid, _ = c.sequence()
    prefix = "SYNTHWINDOW" + str(c.uid()) + "-"
    old_id, old_material, material = c.uid(), c.uid(), c.uid()
    c.insert(schema, "wm_sn", {"sn_id": old_id, "tenant_id": TENANT, "sn_code": prefix + "000002",
        "material_id": old_material, "rule_id": c.uid(), "del_flag": "2"})
    conn = c.box.session(schema)
    predicate = "tenant_id=" + sql_value(TENANT) + " AND sequence_id=" + str(sid)
    def serial(value):
        return c.insert_sql(schema, "wm_sn", {"sn_id": c.uid(), "tenant_id": TENANT,
            "sn_code": prefix + str(value).zfill(6), "material_id": material, "rule_id": c.uid(), "del_flag": "0"})
    try:
        conn.execute("START TRANSACTION; SELECT current_value FROM wm_serial_sequence WHERE " + predicate + " FOR UPDATE;")
        changed = conn.execute("UPDATE wm_serial_sequence SET current_value=3,version=version+1 WHERE " + predicate + " AND current_value=0; SELECT ROW_COUNT();").scalar
        c.require(changed == "1", "First window CAS did not advance")
        conn.execute("SAVEPOINT sn_window;")
        conn.execute(serial(1))
        duplicate = conn.execute(serial(2), error=1062)
        conn.execute("ROLLBACK TO SAVEPOINT sn_window; RELEASE SAVEPOINT sn_window;")
        c.require(conn.execute("SELECT current_value FROM wm_serial_sequence WHERE " + predicate + ";").scalar == "3", "Savepoint rollback accidentally recycled the occupied window")
        c.require(conn.execute("SELECT COUNT(*) FROM wm_sn WHERE tenant_id=" + sql_value(TENANT) + " AND sn_code=" + sql_value(prefix + "000001") + ";").scalar == "0", "Partially inserted batch survived savepoint rollback")
        occupied = conn.execute("SELECT sn_id,del_flag FROM wm_sn WHERE tenant_id=" + sql_value(TENANT) + " AND sn_code=" + sql_value(prefix + "000002") + " FOR UPDATE;").first
        c.require(occupied == [str(old_id), "2"], "Deleted canonical identity was replaced")
        changed = conn.execute("UPDATE wm_serial_sequence SET current_value=6,version=version+1 WHERE " + predicate + " AND current_value=3; SELECT ROW_COUNT();").scalar
        c.require(changed == "1", "Retry window CAS did not advance")
        for value in (4, 5, 6): conn.execute(serial(value))
        conn.execute("COMMIT;")
        actual = c.query(schema, "SELECT sn_code FROM wm_sn WHERE tenant_id=" + sql_value(TENANT) + " AND material_id=" + str(material) + " ORDER BY sn_code;").rows
        c.require(actual == [[prefix + str(value).zfill(6)] for value in (4, 5, 6)], "Collision retry reused a burned slot or retained half a batch")
        return {"duplicate_errors": duplicate.errors, "burned_window": [1, 3], "committed_window": [4, 6],
            "old_deleted_identity_preserved": True, "half_batch_rolled_back": True,
            "boundary": "Real MySQL savepoint/unique-index fallback, not Java exception translation or transaction manager integration"}
    finally:
        conn.close()


def posting_group_source_policy_contract(c):
    evidence = []
    for prefix in ("fresh_", "legacy_"):
        schema = c.dbs[prefix + "erp"]
        c.query(schema, "SELECT source_hash,policy_version FROM wm_trace_posting_group LIMIT 0;")
        rows = c.query(schema, "SELECT COLUMN_NAME,DATA_TYPE,IS_NULLABLE,COALESCE(CHARACTER_MAXIMUM_LENGTH,0),COALESCE(CHARACTER_SET_NAME,''),COALESCE(COLLATION_NAME,'') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND COLUMN_NAME IN ('source_hash','policy_version') ORDER BY COLUMN_NAME;").rows
        fields = {row[0]: row[1:] for row in rows}
        c.require(fields.get("source_hash") == ["char", "NO", "64", "ascii", "ascii_bin"], "Posting source_hash must be CHAR(64) ascii_bin NOT NULL: " + str(rows))
        c.require(fields.get("policy_version", [])[:2] == ["bigint", "NO"], "Posting policy_version must be BIGINT NOT NULL: " + str(rows))
        posting = c.uid()
        c.insert(schema, "wm_trace_posting_group", {"posting_group_id": posting, "tenant_id": TENANT,
            "source_hash": "a" * 64, "policy_version": 9007199254740993})
        actual = c.query(schema, "SELECT source_hash,policy_version,source_hash=" + sql_value("A" * 64)
            + " FROM wm_trace_posting_group WHERE posting_group_id=" + str(posting) + ";").first
        c.require(actual == ["a" * 64, "9007199254740993", "0"], "Posting source hash collation or policy ID precision differs")
        for field in ("source_hash", "policy_version"):
            values = {"tenant_id": TENANT, "source_hash": "b" * 64, "policy_version": 2, field: None}
            c.insert(schema, "wm_trace_posting_group", values, error=1048)
        evidence.append({"schema": schema, "columns": rows, "string_policy_version": actual[1], "null_errors": [1048, 1048]})
    return {"databases": evidence, "boundary": "Physical persistence fields; source hash computation and policy authorization are not executed"}


def export_thirty_table_contract(c):
    core = "script/tools/material_trace/core.py"
    metadata = "script/tools/material_trace/metadata.py"
    assignments = {target.id: node.value for node in ast.parse(c.snapshot.text(core)).body
        if isinstance(node, ast.Assign) for target in node.targets if isinstance(target, ast.Name)}
    union = assignments.get("REQUIRED_TABLES")
    c.require(isinstance(union, ast.BinOp) and isinstance(union.op, ast.BitOr)
        and isinstance(union.right, ast.Name) and union.right.id == "READ_ONLY_SOURCE_TABLES", "Inspect changed export scope expression")
    required = ast.literal_eval(union.left) | ast.literal_eval(assignments["READ_ONLY_SOURCE_TABLES"])
    c.require(len(required) == 35 and {"wm_work_order_tracking_policy","wm_work_order_policy_adoption","wm_barcode"}.issubset(required), "Expected the approved 35-table export including policy bank, adoption and barcode, got " + str(len(required)))
    namespace = {"__builtins__": {}}
    exec(compile(c.snapshot.text(metadata), metadata, "exec"), namespace)
    expected, keys = namespace["COLUMNS"], namespace["KEYS"]
    checked = []
    for table in sorted(required):
        c.require(table in expected and table in keys, "Export table missing column/key contract: " + table)
        ds = "mes" if table.startswith(("pro_", "lt_", "mes_")) else "erp"
        for prefix in ("fresh_", "legacy_"):
            schema = c.dbs[prefix + ds]
            actual = {row[0]: re.match(r"[a-z]+(?:\(\d+(?:,\d+)?\))?", row[6])[0] for row in c.columns(schema, table)}
            c.require(actual == expected[table], "Export columns drift from physical schema: " + schema + "." + table
                + " missing=" + str(sorted(set(expected[table]) - set(actual)))
                + " extra=" + str(sorted(set(actual) - set(expected[table])))
                + " types=" + str({name: [expected[table][name], actual[name]] for name in actual.keys() & expected[table].keys() if actual[name] != expected[table][name]}))
        checked.append({"table": table, "columns": len(expected[table]), "keys": list(keys[table])})
    return {"required_tables": len(required), "physical_table_comparisons": len(required) * 2,
        "tables": checked, "source_sha256": {name: c.snapshot.hashes[name] for name in (core, metadata)},
        "boundary": "Export table/key/column base-type compatibility; not a real business-data export, converter execution or repair apply"}
