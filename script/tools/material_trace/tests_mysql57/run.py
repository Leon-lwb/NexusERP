#!/usr/bin/env python3
"""仅在自建临时内存容器中验证数据库存储与锁，不接受既有数据库或容器参数。"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import sys
import time
import traceback
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from harness import (
    HERE,
    ROOT,
    DockerSandbox,
    SchemaSnapshot,
    SqlFailure,
    ValidationError,
    ident,
    sql_value,
    scoped_output,
    blocking_transaction,
    cleanup_owned,
)

from extended_cases import run_extended

sys.dont_write_bytecode = True
TENANT, OTHER_TENANT = "901001", "901002"


class Checks:
    def __init__(self, sandbox, snapshot, output):
        self.box, self.snapshot, self.output = sandbox, snapshot, output
        self.records, self.ok, self.dbs, self.connections, self.column_cache = [], set(), {}, {}, {}
        self.counter = 8000000000000000000
        snapshot_index = {}
        for index, (relative, data) in enumerate(snapshot.raw.items(), 1):
            # 使用扁平文件路径，避免工作区路径较长时超出操作系统路径长度限制。
            frozen = (
                output
                / "source-snapshot"
                / (str(index).zfill(3) + "-" + relative.rsplit("/", 1)[-1][-48:])
            )
            frozen.parent.mkdir(parents=True, exist_ok=True)
            frozen.write_bytes(data)
            snapshot_index[relative] = {
                "file": str(frozen.relative_to(output)).replace("\\", "/"),
                "sha256": snapshot.hashes[relative],
            }
        (output / "source-snapshot-index.json").write_text(
            json.dumps(snapshot_index, indent=2), encoding="utf-8"
        )
        (output / "source-snapshot-sha256.json").write_text(
            json.dumps(snapshot.hashes, indent=2), encoding="utf-8"
        )
        self.report = {
            "started_at": datetime.now(timezone.utc).isoformat(),
            "workspace": str(ROOT),
            "baseline_ref": snapshot.baseline_ref,
            "source_sha256": snapshot.hashes,
            "container": {"name": sandbox.name, "run_id": sandbox.token},
            "cases": self.records,
            "constraint_boundaries": [],
            "boundaries": [
                "SQL-level only; not Java/Spring transaction or RabbitMQ verification",
                "Two schemas in one isolated MySQL instance, separate producer/consumer transactions",
                "Tenant-aware indexes/predicates, not DB row-level security",
                "Business quantity/state/source/hash equivalence still requires application validation",
            ],
        }

    def uid(self):
        self.counter += 1
        return self.counter

    @staticmethod
    def require(condition, message):
        if not condition:
            raise ValidationError(message)

    def case(self, name, action, requires=()):
        record = {"name": name}
        self.records.append(record)
        missing = [needed for needed in requires if needed not in self.ok]
        if missing:
            record.update(status="BLOCKED", reason="Prerequisites failed: " + ", ".join(missing))
            print("BLOCKED", name, flush=True)
            return
        started = time.monotonic()
        try:
            evidence = action()
            record.update(status="PASS", evidence=evidence or {})
            self.ok.add(name)
            print("PASS", name, flush=True)
        except Exception as exc:
            record.update(status="FAIL", error=str(exc), traceback=traceback.format_exc())
            if isinstance(exc, SqlFailure):
                record.update(exc.evidence())
                failure = self.output / ("failure-" + name + ".sql")
                failure.write_text(exc.sql, encoding="utf-8")
                record["reproduction_sql"] = str(failure)
            print("FAIL", name, str(exc)[:600], flush=True)
        finally:
            record["seconds"] = round(time.monotonic() - started, 3)
            self.save()

    def save(self):
        self.output.mkdir(parents=True, exist_ok=True)
        self.report["counts"] = {
            s.lower(): sum(r.get("status") == s for r in self.records)
            for s in ("PASS", "FAIL", "BLOCKED")
        }
        self.report["counts"]["executed"] = (
            self.report["counts"]["pass"] + self.report["counts"]["fail"]
        )
        (self.output / "report.json").write_text(
            json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    def conn(self, schema):
        if schema not in self.connections:
            self.connections[schema] = self.box.session(schema)
        return self.connections[schema]

    def query(self, schema, sql, **kwargs):
        return self.conn(schema).execute(sql, **kwargs)

    def new_schema(self, key, ds, *, baseline=False, subset=False):
        schema = "mt57_" + re.sub("[^a-z0-9_]", "_", key.lower())
        self.box.run_sql(
            None,
            "CREATE DATABASE "
            + ident(schema)
            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
        )
        tables = self.snapshot.baseline[ds] if baseline else self.snapshot.current[ds]
        if subset:
            names = set(self.snapshot.contract["ADDITIONS"][ds]) | set(
                self.snapshot.contract["TABLES"][ds]
            )
            tables = {name: ddl for name, ddl in tables.items() if name in names}
        ddl = (
            "SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0;\n"
            + "\n".join(tables.values())
            + "\nSET FOREIGN_KEY_CHECKS=1;\n"
        )
        (self.output / (key + "-create-only.sql")).write_text(ddl, encoding="utf-8")
        self.box.run_sql(schema, ddl)
        self.dbs[key] = schema
        count = int(
            self.query(
                schema,
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();",
            ).scalar
        )
        self.require(count == len(tables), f"Expected {len(tables)} CREATE TABLEs, got {count}")
        return {"schema": schema, "tables": count, "seed_insert_statements_executed": 0}

    def columns(self, schema, table):
        key = (schema, table)
        if key not in self.column_cache:
            self.column_cache[key] = self.query(
                schema,
                "SELECT COLUMN_NAME,DATA_TYPE,IS_NULLABLE,COALESCE(CAST(COLUMN_DEFAULT AS CHAR),'__NULL__'),EXTRA,"
                "COALESCE(CHARACTER_MAXIMUM_LENGTH,0),COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA="
                + sql_value(schema)
                + " AND TABLE_NAME="
                + sql_value(table)
                + " ORDER BY ORDINAL_POSITION;",
            ).rows
        return self.column_cache[key]

    def insert_sql(self, schema, table, values=None):
        row = dict(values or {})
        for name, kind, nullable, default, extra, length, column_type in self.columns(
            schema, table
        ):
            if (
                name in row
                or "GENERATED" in extra
                or "auto_increment" in extra
                or nullable == "YES"
                or default != "__NULL__"
            ):
                continue
            serial = self.uid()
            if kind in ("bigint", "int", "smallint", "tinyint", "mediumint", "bit"):
                row[name] = serial if kind == "bigint" and name.endswith("_id") else 1
            elif kind in ("decimal", "double", "float"):
                row[name] = "1.000000"
            elif kind in ("datetime", "timestamp"):
                row[name] = "2026-10-18 01:02:03"
            elif kind == "date":
                row[name] = "2026-10-18"
            elif kind == "time":
                row[name] = "01:02:03"
            elif kind == "enum":
                row[name] = re.search(r"enum\('([^']*)'", column_type)[1]
            elif kind in ("text", "longtext", "mediumtext", "json"):
                row[name] = '{"synthetic":true}'
            else:
                text = hashlib.sha256(f"synthetic:{serial}".encode()).hexdigest()
                row[name] = text[: int(length)] if int(length) else text
        known = {column[0] for column in self.columns(schema, table)}
        self.require(set(row).issubset(known), f"Unknown fixture columns: {set(row) - known}")
        return (
            "INSERT INTO "
            + ident(table)
            + " ("
            + ",".join(map(ident, row))
            + ") VALUES ("
            + ",".join(map(sql_value, row.values()))
            + ");"
        )

    def insert(self, schema, table, values=None, **kwargs):
        return self.query(schema, self.insert_sql(schema, table, values), **kwargs)

    def metadata(self, schema, ds):
        names = sorted(
            set(self.snapshot.contract["ADDITIONS"][ds]) | set(self.snapshot.contract["TABLES"][ds])
        )
        selected = ",".join(map(sql_value, names))
        columns = self.query(
            schema,
            "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,"
            "COALESCE(CAST(COLUMN_DEFAULT AS CHAR),'__NULL__'),COALESCE(COLLATION_NAME,''),EXTRA,COALESCE(GENERATION_EXPRESSION,'') "
            "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("
            + selected
            + ") ORDER BY TABLE_NAME,COLUMN_NAME;",
        ).rows
        indexes = self.query(
            schema,
            "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COALESCE(SUB_PART,0) "
            "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("
            + selected
            + ") ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;",
        ).rows
        return {"columns": columns, "indexes": indexes}

    def migration(self, ds, schema, **kwargs):
        sql = self.snapshot.migration(
            ds, schema, **{k: v for k, v in kwargs.items() if k in ("backup", "expected")}
        )
        self.box.run_sql(schema, sql, error=kwargs.get("error"))
        self.column_cache = {}
        return {
            "schema": schema,
            "migration_source_sha256": self.snapshot.hashes[
                f"script/sql/update/2026-10-18-material-trace-{ds}.sql"
            ],
        }

    def legacy_seed(self, ds):
        schema = self.dbs["legacy_" + ds]
        if ds == "erp":
            self.insert(
                schema,
                "wm_sn",
                {
                    "sn_id": 101,
                    "tenant_id": TENANT,
                    "sn_code": "SYNTH-LEGACY",
                    "material_id": 201,
                    "status": "IN_STOCK",
                },
            )
            self.insert(
                schema,
                "wm_barcode",
                {
                    "barcode_id": 701,
                    "tenant_id": TENANT,
                    "barcode_code": "SYNTH-LEGACY-CARRIER",
                    "material_id": 201,
                    "stock_id": 301,
                },
            )
            for sid in (301, 302):
                self.insert(
                    schema,
                    "wm_material_stock",
                    {
                        "stock_id": sid,
                        "tenant_id": TENANT,
                        "material_id": 201,
                        "warehouse_id": sid,
                        "sn_id": 101,
                        "stock_quantity": "0.000000",
                    },
                )
        else:
            self.insert(
                schema,
                "pro_sn",
                {
                    "sn_id": 401,
                    "tenant_id": TENANT,
                    "sn_code": "SYNTH-LEGACY-MES",
                    "mes_wo_id": 501,
                    "material_id": 201,
                },
            )
            self.insert(
                schema,
                "mes_label_print_log",
                {
                    "log_id": 601,
                    "tenant_id": TENANT,
                    "sn_id": 401,
                    "template_id": 701,
                    "result": "SUCCESS",
                    "retry_count": 0,
                },
            )
        return {"synthetic_only": True}

    def legacy_preserved(self, ds):
        schema = self.dbs["legacy_" + ds]
        if ds == "erp":
            result = self.query(
                schema,
                "SELECT status,registration_command,identity_version FROM wm_sn WHERE sn_id=101;",
            ).first
            self.require(result == ["IN_STOCK", "NULL", "1"], f"Legacy SN changed: {result}")
            stocks = self.query(
                schema,
                "SELECT stock_id,tracking_version,active_sn_id,stock_quantity FROM wm_material_stock WHERE sn_id=101 ORDER BY stock_id;",
            ).rows
            self.require(
                len(stocks) == 2
                and all(r[1:3] == ["1", "NULL"] and r[3].strip("0.") == "" for r in stocks),
                "Legacy zero stock rows were upgraded/dropped",
            )
            barcode = self.query(
                schema, "SELECT primary_object_type FROM wm_barcode WHERE barcode_id=701;"
            ).scalar
            self.require(
                barcode == "NULL",
                "Legacy barcode primary subject was fabricated from its old stock reference",
            )
            return {
                "legacy_sn": result,
                "zero_history_rows": stocks,
                "legacy_barcode_primary_unchanged": barcode,
            }
        result = self.query(
            schema, "SELECT mes_wo_id,identity_id,identity_version FROM pro_sn WHERE sn_id=401;"
        ).first
        self.require(result == ["501", "NULL", "1"], f"Legacy MES SN changed: {result}")
        log = self.query(
            schema,
            "SELECT result,template_snapshot,field_snapshot,content_hash,snapshot_mode FROM mes_label_print_log WHERE log_id=601;",
        ).first
        self.require(
            log == ["SUCCESS", "NULL", "NULL", "NULL", "NULL"],
            f"Historical print evidence fabricated: {log}",
        )
        return {"legacy_sn": result, "legacy_print": log}

    def stock(self, identity, *, tenant=TENANT, qty="1.000000", tracking=2, **kwargs):
        schema = self.dbs["legacy_erp"]
        sid = self.uid()
        return self.insert(
            schema,
            "wm_material_stock",
            {
                "stock_id": sid,
                "tenant_id": tenant,
                "material_id": 201,
                "warehouse_id": sid,
                "sn_id": identity,
                "stock_quantity": qty,
                "tracking_version": tracking,
            },
            **kwargs,
        )

    def stock_unique(self):
        identity = self.uid()
        self.stock(identity)
        result = self.stock(identity, error=1062)
        self.require(
            any("uk_material_stock_active_sn" in line for line in result.errors),
            "Wrong index rejected duplicate",
        )
        return {"mysql_error": result.errors, "constraint": "uk_material_stock_active_sn"}

    def stock_zero(self):
        identity = self.uid()
        self.stock(identity, qty="0.000000")
        self.stock(identity, qty="0.000000")
        rows = self.query(
            self.dbs["legacy_erp"],
            f"SELECT active_sn_id,stock_quantity FROM wm_material_stock WHERE sn_id={identity};",
        ).rows
        self.require(
            len(rows) == 2 and all(r[0] == "NULL" for r in rows),
            "Zero rows must not occupy active identity",
        )
        return {"rows": rows}

    def stock_legacy_positive(self):
        identity = self.uid()
        self.stock(identity, tracking=1)
        self.stock(identity, tracking=1)
        rows = self.query(
            self.dbs["legacy_erp"],
            f"SELECT active_sn_id FROM wm_material_stock WHERE sn_id={identity};",
        ).rows
        self.require(rows == [["NULL"], ["NULL"]], "Version-1 history was silently upgraded")
        return {"rows": rows}

    def stock_tenants(self):
        identity = self.uid()
        self.stock(identity)
        self.stock(identity, tenant=OTHER_TENANT)
        schema = self.dbs["legacy_erp"]
        self.query(
            schema,
            f"UPDATE wm_material_stock SET stock_quantity=0 WHERE tenant_id={sql_value(TENANT)} AND sn_id={identity};",
        )
        rows = self.query(
            schema,
            f"SELECT tenant_id,stock_quantity,active_sn_id FROM wm_material_stock WHERE sn_id={identity} ORDER BY tenant_id;",
        ).rows
        self.require(
            len(rows) == 2 and rows[0][2] == "NULL" and rows[1][2] == str(identity),
            "Tenant-filtered update crossed scope",
        )
        return {
            "rows": rows,
            "boundary": "Tenant-aware SQL predicates/index; not DB row-level security",
        }

    def sn_unique(self, ds):
        schema = self.dbs["legacy_" + ds]
        table = "wm_sn" if ds == "erp" else "pro_sn"
        values = {
            "sn_id": self.uid(),
            "tenant_id": TENANT,
            "sn_code": "SYNTH-UNIQUE-" + str(self.uid()),
            "material_id": 201,
        }
        self.insert(schema, table, values)
        self.insert(schema, table, {**values, "sn_id": self.uid()}, error=1062)
        self.insert(schema, table, {**values, "sn_id": self.uid(), "tenant_id": OTHER_TENANT})
        return {"duplicate_error": 1062, "same_code_other_tenant": "accepted"}

    def mes_identity(self):
        schema = self.dbs["legacy_mes"]
        identity = self.uid()

        def insert(tenant, value, error=None):
            return self.insert(
                schema,
                "pro_sn",
                {
                    "sn_id": self.uid(),
                    "tenant_id": tenant,
                    "sn_code": "SYNTH-ID-" + str(self.uid()),
                    "identity_id": value,
                },
                error=error,
            )

        insert(TENANT, identity)
        insert(TENANT, identity, 1062)
        insert(OTHER_TENANT, identity)
        insert(TENANT, None)
        insert(TENANT, None)
        return {"identity_unique_per_tenant": True, "multiple_legacy_null_mappings": True}

    def nullable_tenant_stock_boundary(self):
        schema = self.dbs["legacy_erp"]
        nullable = next(
            row[2] for row in self.columns(schema, "wm_material_stock") if row[0] == "tenant_id"
        )
        if nullable == "NO":
            self.stock(self.uid(), tenant=None, error=1048)
            return {"tenant_not_null": True}
        identity = self.uid()
        self.stock(identity, tenant=None)
        self.stock(identity, tenant=None)
        count = self.query(
            schema,
            f"SELECT COUNT(*) FROM wm_material_stock WHERE tenant_id IS NULL AND sn_id={identity} AND active_sn_id={identity};",
        ).scalar
        self.require(count == "2", "Unexpected nullable-tenant unique-index behavior")
        sql = (
            "USE "
            + ident(schema)
            + ";\n"
            + "\n".join(
                [
                    self.insert_sql(
                        schema,
                        "wm_material_stock",
                        {
                            "stock_id": 8990000000000000001,
                            "tenant_id": None,
                            "warehouse_id": 8801,
                            "material_id": 201,
                            "sn_id": 8990000000000000000,
                            "tracking_version": 2,
                            "stock_quantity": "1.000000",
                        },
                    ),
                    self.insert_sql(
                        schema,
                        "wm_material_stock",
                        {
                            "stock_id": 8990000000000000002,
                            "tenant_id": None,
                            "warehouse_id": 8802,
                            "material_id": 201,
                            "sn_id": 8990000000000000000,
                            "tracking_version": 2,
                            "stock_quantity": "1.000000",
                        },
                    ),
                ]
            )
        )
        path = self.output / "boundary-null-tenant-active-stock.sql"
        path.write_text(sql, encoding="utf-8")
        finding = {
            "table": "wm_material_stock",
            "type": "nullable_legacy_tenant_boundary",
            "observed_duplicate_active_rows": 2,
            "meaning": "The active-SN unique index is tenant-scoped but does not enforce non-null authenticated tenant input",
            "owner_review": "Confirm application rejects NULL tenant; not a CREATE/migration syntax error",
            "reproduction_sql": str(path),
        }
        self.report["constraint_boundaries"].append(finding)
        return finding

    def nullable_tenant_sn_boundary(self, ds):
        schema = self.dbs["legacy_" + ds]
        table = "wm_sn" if ds == "erp" else "pro_sn"
        nullable = next(row[2] for row in self.columns(schema, table) if row[0] == "tenant_id")
        code = "SYNTH-NULL-TENANT-" + str(self.uid())
        row = {"sn_id": self.uid(), "tenant_id": None, "sn_code": code, "material_id": 201}
        if nullable == "NO":
            self.insert(schema, table, row, error=1048)
            return {"table": table, "tenant_not_null": True}
        self.insert(schema, table, row)
        self.insert(schema, table, {**row, "sn_id": self.uid()})
        count = self.query(
            schema,
            f"SELECT COUNT(*) FROM {table} WHERE tenant_id IS NULL AND sn_code={sql_value(code)};",
        ).scalar
        self.require(count == "2", "Unexpected nullable-tenant SN-code uniqueness behavior")
        path = self.output / ("boundary-null-tenant-" + table + ".sql")
        path.write_text(
            "USE "
            + ident(schema)
            + ";\n"
            + self.insert_sql(
                schema, table, {**row, "sn_id": self.uid(), "sn_code": code + "-REPRO"}
            )
            + "\n"
            + self.insert_sql(
                schema, table, {**row, "sn_id": self.uid(), "sn_code": code + "-REPRO"}
            ),
            encoding="utf-8",
        )
        finding = {
            "table": table,
            "type": "nullable_legacy_tenant_boundary",
            "observed_duplicate_sn_codes": 2,
            "owner_review": "Tenant comes from application auth; SQL index alone does not reject explicit NULL tenant",
            "reproduction_sql": str(path),
        }
        self.report["constraint_boundaries"].append(finding)
        return finding

    def new_tables_reject_null_tenant(self):
        errors = {}
        without_tenant = []
        for ds, table in (
            (ds, table) for ds in ("erp", "mes") for table in self.snapshot.contract["TABLES"][ds]
        ):
            schema = self.dbs["legacy_" + ds]
            if "tenant_id" not in {column[0] for column in self.columns(schema, table)}:
                without_tenant.append(table)
                continue
            self.insert(schema, table, {"tenant_id": None}, error=1048)
            errors[table] = 1048
        self.require(bool(errors), "No tenant columns tested")
        return {
            "not_null_errors": errors,
            "tables_without_tenant_column": without_tenant,
            "boundary": "Repair run artifacts have their own scope contract; no tenant NOT NULL assertion is made for tables without that column",
        }

    def sequence(self):
        schema = self.dbs["legacy_erp"]
        sid = self.uid()
        values = {
            "sequence_id": sid,
            "tenant_id": TENANT,
            "namespace": "SYNTH_INTERNAL_SN",
            "rule_key": str(sid),
            "cycle_key": "20261018",
            "current_value": 0,
            "version": 1,
        }
        self.insert(schema, "wm_serial_sequence", values)
        return schema, sid, values

    def sequence_scope(self):
        schema, sid, values = self.sequence()
        self.insert(schema, "wm_serial_sequence", {**values, "sequence_id": self.uid()}, error=1062)
        for changed in (
            {"tenant_id": OTHER_TENANT},
            {"namespace": "SYNTH_DOCUMENT"},
            {"cycle_key": "20261019"},
        ):
            self.insert(
                schema, "wm_serial_sequence", {**values, "sequence_id": self.uid(), **changed}
            )
        return {"same_scope_duplicate_error": 1062, "other_tenant_namespace_cycle": "independent"}

    def wait_for_lock(self, connection_id, pending=None, expected_table="wm_serial_sequence"):
        deadline = time.monotonic() + 4
        while time.monotonic() < deadline:
            rows = self.query(self.dbs["legacy_erp"], "SHOW ENGINE INNODB STATUS;").rows
            status = "\n".join("\t".join(row) for row in rows)
            block = blocking_transaction(status, connection_id)
            if block:
                self.require(
                    re.search(
                        r"(?<![A-Za-z0-9_])" + re.escape(expected_table) + r"(?![A-Za-z0-9_])",
                        block,
                    )
                    is not None,
                    "Observed a wait on the wrong business table",
                )
                return {
                    "source": "SHOW ENGINE INNODB STATUS",
                    "requesting_connection": connection_id,
                    "expected_table": expected_table,
                    "transaction_excerpt": block[:2200],
                }
            if pending is not None and pending.done():
                try:
                    state = {"completed_rows": pending.result().rows}
                except Exception as exc:
                    state = {"completed_error": str(exc)}
                raise ValidationError(
                    "Expected a blocked second connection, got " + json.dumps(state)
                )
            time.sleep(0.05)
        (self.output / ("unobserved-lock-thread-" + str(connection_id) + ".txt")).write_text(
            status, encoding="utf-8"
        )
        raise ValidationError(
            "No row-lock wait found for the exact server thread; see unobserved-lock diagnostic"
        )

    def sequence_cas(self):
        schema, sid, _ = self.sequence()
        a, b = self.box.session(schema), self.box.session(schema)
        predicate = f"tenant_id={sql_value(TENANT)} AND sequence_id={sid}"
        a.execute("START TRANSACTION;")
        b.execute("START TRANSACTION;")
        self.require(
            a.execute(
                f"SELECT current_value,version FROM wm_serial_sequence WHERE {predicate};"
            ).first
            == ["0", "1"],
            "Unexpected starting sequence",
        )
        self.require(
            b.execute(
                f"SELECT current_value,version FROM wm_serial_sequence WHERE {predicate};"
            ).first
            == ["0", "1"],
            "Sessions did not observe same CAS version",
        )
        a.execute(
            f"UPDATE wm_serial_sequence SET current_value=3,version=2 WHERE {predicate} AND version=1;"
        )
        pool = ThreadPoolExecutor(max_workers=1)
        try:
            pending = pool.submit(
                b.execute,
                f"UPDATE wm_serial_sequence SET current_value=2,version=2 WHERE {predicate} AND version=1; SELECT ROW_COUNT();",
            )
            wait = self.wait_for_lock(b.connection_id, pending)
            a.execute("COMMIT;")
            self.require(
                pending.result(timeout=12).scalar == "0", "Stale CAS allocated overlapping range"
            )
            b.execute("ROLLBACK; START TRANSACTION;")
            self.require(
                b.execute(
                    f"SELECT current_value,version FROM wm_serial_sequence WHERE {predicate};"
                ).first
                == ["3", "2"],
                "CAS retry did not see committed range",
            )
            self.require(
                b.execute(
                    f"UPDATE wm_serial_sequence SET current_value=5,version=3 WHERE {predicate} AND version=2; SELECT ROW_COUNT();"
                ).scalar
                == "1",
                "Fresh CAS retry failed",
            )
            b.execute("COMMIT;")
            self.require(
                self.query(
                    schema,
                    f"SELECT current_value,version FROM wm_serial_sequence WHERE {predicate};",
                ).first
                == ["5", "3"],
                "Final sequence differs",
            )
            return {
                "connections": [a.connection_id, b.connection_id],
                "lock_wait_evidence": wait,
                "stale_cas_affected": 0,
                "ranges": [[1, 3], [4, 5]],
                "final": [5, 3],
            }
        finally:
            a.close()
            b.close()
            pool.shutdown(wait=True)

    def sequence_lock(self, timeout=False):
        schema, sid, _ = self.sequence()
        a, b = self.box.session(schema), self.box.session(schema)
        predicate = f"tenant_id={sql_value(TENANT)} AND sequence_id={sid}"
        a.execute("START TRANSACTION;")
        locked = a.execute(
            f"SELECT current_value FROM wm_serial_sequence WHERE {predicate} FOR UPDATE;"
        )
        self.require(
            locked.rows == [["0"]],
            "FOR UPDATE did not select the expected sequence row: " + str(locked.rows),
        )
        try:
            if timeout:
                b.execute("SET SESSION innodb_lock_wait_timeout=1; START TRANSACTION;")
                error = b.execute(
                    f"UPDATE wm_serial_sequence SET current_value=current_value+1 WHERE {predicate};",
                    error=1205,
                )
                b.execute("ROLLBACK;")
                return {
                    "connections": [a.connection_id, b.connection_id],
                    "mysql_error": error.errors,
                }
            b.execute("START TRANSACTION;")
            with ThreadPoolExecutor(max_workers=1) as pool:
                pending = pool.submit(
                    b.execute,
                    f"UPDATE wm_serial_sequence SET current_value=current_value+1,version=version+1 WHERE {predicate}; SELECT ROW_COUNT();",
                )
                wait = self.wait_for_lock(b.connection_id, pending)
                a.execute("COMMIT;")
                self.require(
                    pending.result(timeout=12).scalar == "1",
                    "Lock contender did not proceed after commit",
                )
                b.execute("COMMIT;")
            return {
                "connections": [a.connection_id, b.connection_id],
                "lock_wait_evidence": wait,
                "after_commit": self.query(
                    schema,
                    f"SELECT current_value,version FROM wm_serial_sequence WHERE {predicate};",
                ).first,
            }
        finally:
            a.close()
            b.close()

    def event(self, source, suffix):
        return {
            "event_id": "SYNTH-" + suffix + "-" + str(self.uid()),
            "tenant_id": TENANT,
            "source_module": source.upper(),
            "aggregate_type": "SYNTH_SN",
            "aggregate_id": self.uid(),
            "aggregate_version": 1,
            "event_type": "SYNTH_IDENTITY_REGISTERED",
            "schema_version": 1,
            "payload": '{"synthetic":true,"quantity":"1.000000"}',
            "content_hash": "a" * 64,
            "status": "NEW",
            "attempts": 0,
        }

    def source_local_rollback(self, source):
        schema = self.dbs["legacy_" + source]
        prefix, table = ("wm", "wm_sn") if source == "erp" else ("mes", "pro_sn")
        event = self.event(source, "ROLLBACK")
        sid = self.uid()
        conn = self.box.session(schema)
        conn.execute("START TRANSACTION;")
        conn.execute(
            self.insert_sql(
                schema,
                table,
                {
                    "sn_id": sid,
                    "tenant_id": TENANT,
                    "sn_code": "SYNTH-BUS-" + str(sid),
                    "material_id": 201,
                },
            )
        )
        conn.execute(self.insert_sql(schema, prefix + "_trace_outbox", event))
        conn.execute("ROLLBACK;")
        business = self.query(schema, f"SELECT COUNT(*) FROM {table} WHERE sn_id={sid};").scalar
        journal = self.query(
            schema,
            f"SELECT COUNT(*) FROM {prefix}_trace_outbox WHERE event_id={sql_value(event['event_id'])};",
        ).scalar
        self.require(business == journal == "0", "Business and outbox did not roll back together")
        conn.close()
        return {"business_after_rollback": business, "outbox_after_rollback": journal}

    def delivery(self, source):
        target = "mes" if source == "erp" else "erp"
        src, dst = self.dbs["legacy_" + source], self.dbs["legacy_" + target]
        sp = "wm" if source == "erp" else "mes"
        tp = "wm" if target == "erp" else "mes"
        stable = "wm_sn" if source == "erp" else "pro_sn"
        dtable = "wm_sn" if target == "erp" else "pro_sn"
        producer, consumer = self.box.session(src), self.box.session(dst)
        event = self.event(source, "DELIVERY")
        source_id, target_id = self.uid(), self.uid()
        source_sql = self.insert_sql(
            src,
            stable,
            {
                "sn_id": source_id,
                "tenant_id": TENANT,
                "sn_code": "SYNTH-SRC-" + str(source_id),
                "material_id": 201,
            },
        )
        target_sql = self.insert_sql(
            dst,
            dtable,
            {
                "sn_id": target_id,
                "tenant_id": TENANT,
                "sn_code": "SYNTH-DST-" + str(target_id),
                "material_id": 201,
            },
        )
        producer.execute("START TRANSACTION;")
        producer.execute(source_sql)
        producer.execute(self.insert_sql(src, sp + "_trace_outbox", event))
        producer.execute("COMMIT;")
        inbox = {**event, "status": "WAITING"}
        consumer.execute("START TRANSACTION;")
        consumer.execute(self.insert_sql(dst, tp + "_trace_inbox", inbox))
        consumer.execute(target_sql)
        consumer.execute("ROLLBACK;")
        target_count = self.query(
            dst, f"SELECT COUNT(*) FROM {dtable} WHERE sn_id={target_id};"
        ).scalar
        inbox_count = self.query(
            dst,
            f"SELECT COUNT(*) FROM {tp}_trace_inbox WHERE event_id={sql_value(event['event_id'])};",
        ).scalar
        source_status = self.query(
            src,
            f"SELECT status FROM {sp}_trace_outbox WHERE event_id={sql_value(event['event_id'])};",
        ).scalar
        self.require(
            target_count == inbox_count == "0" and source_status == "NEW",
            "Consumer failure changed source or retained partial inbox/effect",
        )
        consumer.execute("START TRANSACTION;")
        consumer.execute(self.insert_sql(dst, tp + "_trace_inbox", inbox))
        consumer.execute(target_sql)
        consumer.execute(
            f"UPDATE {tp}_trace_inbox SET status='APPLIED',result_payload='{{}}',applied_at=CURRENT_TIMESTAMP WHERE tenant_id={sql_value(TENANT)} AND event_id={sql_value(event['event_id'])}; COMMIT;"
        )
        # 覆盖实际事件日志模式：重复插入保持原值，再加锁读取原接收结果。
        consumer.execute("START TRANSACTION;")
        duplicate_sql = (
            self.insert_sql(dst, tp + "_trace_inbox", inbox).rstrip(";")
            + " ON DUPLICATE KEY UPDATE event_id=event_id;"
        )
        consumer.execute(duplicate_sql)
        saved = consumer.execute(
            f"SELECT status,content_hash,result_payload FROM {tp}_trace_inbox WHERE tenant_id={sql_value(TENANT)} AND event_id={sql_value(event['event_id'])} FOR UPDATE;"
        ).first
        self.require(
            saved == ["APPLIED", "a" * 64, "{}"], "Duplicate did not return persisted result"
        )
        consumer.execute("COMMIT;")
        final_count = self.query(
            dst, f"SELECT COUNT(*) FROM {dtable} WHERE sn_id={target_id};"
        ).scalar
        self.require(final_count == "1", "Duplicate delivery changed applied business count")
        self.insert(
            dst,
            tp + "_trace_inbox",
            {**inbox, "event_id": "SYNTH-CONFLICT-" + str(self.uid())},
            error=1062,
        )
        producer.close()
        consumer.close()
        return {
            "source": src,
            "destination": dst,
            "source_status_after_consumer_rollback": source_status,
            "consumer_rollback_business_and_inbox": [target_count, inbox_count],
            "applied_effect_count": final_count,
            "duplicate_receipt": saved,
            "stream_version_conflict_error": 1062,
            "boundary": "SQL replay protocol only; Java hash validation/ACK/RabbitMQ not exercised",
        }

    def journal_tenants(self, ds, kind):
        schema = self.dbs["legacy_" + ds]
        table = ("wm" if ds == "erp" else "mes") + "_trace_" + kind
        event = self.event(ds, "TENANT-" + kind)
        self.insert(schema, table, event)
        self.insert(
            schema, table, {**event, "event_id": "SYNTH-DUP-" + str(self.uid())}, error=1062
        )
        self.insert(
            schema,
            table,
            {**event, "event_id": "SYNTH-OTHER-" + str(self.uid()), "tenant_id": OTHER_TENANT},
        )
        self.insert(schema, table, {**event, "tenant_id": OTHER_TENANT}, error=1062)
        return {"stream_unique_per_tenant": True, "event_id_globally_unique": True}

    def final_tables(self):
        required = [
            ("erp", "wm_trace_audit_run"),
            ("mes", "mes_label_print_task"),
            ("erp", "wm_trace_material_reservation"),
            ("erp", "wm_trace_material_reservation_line"),
            ("erp", "wm_trace_material_consumption"),
            ("mes", "pro_completion_material_allocation"),
        ]
        for ds, name in required:
            self.require(
                name in self.snapshot.contract["TABLES"][ds],
                "Mill final table missing from generator: " + name,
            )
            self.require(
                name in self.snapshot.current[ds], "Mill final table missing from install: " + name
            )
            self.require(
                name in self.snapshot.text(f"script/sql/update/2026-10-18-material-trace-{ds}.sql"),
                "Mill final table missing from migration: " + name,
            )
            for prefix in ("fresh_", "legacy_"):
                self.require(
                    bool(self.columns(self.dbs[prefix + ds], name)),
                    "Final table missing from database: " + name,
                )
        return {
            "tables": [name for _, name in required],
            "generator_install_migration_database": "present in all",
        }

    def print_tasks(self):
        schema = self.dbs["legacy_mes"]
        key = hashlib.sha256(b"synthetic-print-task").hexdigest()
        row = {
            "task_id": self.uid(),
            "tenant_id": TENANT,
            "task_key": key,
            "request_fingerprint": "b" * 64,
            "template_id": 701,
            "print_source": "TEST",
            "frozen_record": '{"synthetic":true}',
            "record_hash": "c" * 64,
        }
        self.insert(schema, "mes_label_print_task", row)
        self.insert(schema, "mes_label_print_task", {**row, "task_id": self.uid()}, error=1062)
        self.insert(
            schema,
            "mes_label_print_task",
            {**row, "task_id": self.uid(), "tenant_id": OTHER_TENANT},
        )
        actual = self.query(
            schema,
            "SELECT status,attempt_count,sn_id,template_revision FROM mes_label_print_task WHERE task_id="
            + str(row["task_id"])
            + ";",
        ).first
        self.require(
            actual == ["PENDING", "0", "NULL", "NULL"],
            "Print task defaults fabricate historical state",
        )
        return {
            "tenant_task_key_unique": True,
            "initial_state": actual,
            "not_tested": "printer transport and paper output",
        }

    def audit_artifact(self):
        schema = self.dbs["legacy_erp"]
        aid = self.uid()
        self.insert(
            schema,
            "wm_trace_audit_run",
            {
                "audit_id": aid,
                "tenant_id": TENANT,
                "material_id": 201,
                "status": "COMPLETE",
                "summary": "SYNTHETIC ONLY",
                "report_json": '{"synthetic":true}',
                "content_hash": "d" * 64,
            },
        )
        self.require(
            self.query(
                schema,
                f"SELECT COUNT(*) FROM wm_trace_audit_run WHERE audit_id={aid} AND tenant_id={sql_value(OTHER_TENANT)};",
            ).scalar
            == "0",
            "Audit predicate crossed tenant",
        )
        self.require(
            self.query(
                schema,
                f"SELECT report_json FROM wm_trace_audit_run WHERE audit_id={aid} AND tenant_id={sql_value(TENANT)};",
            ).scalar
            == '{"synthetic":true}',
            "Audit artifact changed",
        )
        return {"tenant_scoped_artifact": True, "content_type": "LONGTEXT JSON string"}

    def guard(self, ds, kind):
        if kind in ("missing_expected", "missing_backup", "wrong_expected"):
            schema = self.dbs["fresh_" + ds]
            sql = self.snapshot.migration(
                ds, schema, expected=kind != "missing_expected", backup=kind != "missing_backup"
            )
            if kind == "wrong_expected":
                sql = sql.replace(sql_value(schema), sql_value("mt57_not_the_selected_schema"), 1)
            output = self.box.run_sql(schema, sql, error=1644)
            return {"mysql_error": 1644, "reason": kind, "message": output[-1000:]}
        key = "guard_" + ds + "_" + kind
        self.new_schema(key, ds, subset=True)
        schema = self.dbs[key]
        if kind == "column_drift":
            table, column = (
                ("wm_serial_sequence", "current_value")
                if ds == "erp"
                else ("mes_label_print_task", "attempt_count")
            )
            self.query(
                schema, f"ALTER TABLE {table} MODIFY {column} varchar(16) NOT NULL DEFAULT '0';"
            )
        elif kind == "index_drift":
            sql = (
                "ALTER TABLE wm_material_stock DROP INDEX uk_material_stock_active_sn, ADD INDEX uk_material_stock_active_sn(tenant_id,sn_id);"
                if ds == "erp"
                else "ALTER TABLE mes_label_print_task DROP INDEX uk_label_task_key, ADD INDEX uk_label_task_key(tenant_id,task_key);"
            )
            self.query(schema, sql)
        elif kind == "active_duplicate":
            self.query(
                schema, "ALTER TABLE wm_material_stock DROP INDEX uk_material_stock_active_sn;"
            )
            for sid in (8101, 8102):
                self.insert(
                    schema,
                    "wm_material_stock",
                    {
                        "stock_id": sid,
                        "tenant_id": TENANT,
                        "warehouse_id": sid,
                        "material_id": 201,
                        "sn_id": 8100,
                        "tracking_version": 2,
                        "stock_quantity": "1.000000",
                    },
                )
        output = self.box.run_sql(schema, self.snapshot.migration(ds, schema), error=1644)
        return {"mysql_error": 1644, "test_injected_schema_drift": kind, "message": output[-1000:]}

    def run(self):
        self.case("mysql57_and_container_isolation", self.infrastructure)
        self.case("synthetic_literal_coercibility", self.literal_coercibility)
        for ds in ("erp", "mes"):
            self.case("generator_matches_" + ds, lambda ds=ds: self.generator(ds))
            self.case("install_" + ds, lambda ds=ds: self.new_schema("fresh_" + ds, ds))
            self.case(
                "baseline_" + ds, lambda ds=ds: self.new_schema("legacy_" + ds, ds, baseline=True)
            )
            self.case(
                "legacy_synthetic_" + ds, lambda ds=ds: self.legacy_seed(ds), ["baseline_" + ds]
            )
            self.case(
                "migration_" + ds,
                lambda ds=ds: self.migration(ds, self.dbs["legacy_" + ds]),
                ["legacy_synthetic_" + ds],
            )
            self.case(
                "legacy_preserved_" + ds,
                lambda ds=ds: self.legacy_preserved(ds),
                ["migration_" + ds],
            )
            self.case("migration_replay_" + ds, lambda ds=ds: self.replay(ds), ["migration_" + ds])
            self.case(
                "install_migration_metadata_parity_" + ds,
                lambda ds=ds: self.parity(ds),
                ["install_" + ds, "migration_" + ds],
            )
            self.case(
                "preflight_current_" + ds, lambda ds=ds: self.preflight(ds), ["migration_" + ds]
            )
            for kind in (
                "missing_expected",
                "missing_backup",
                "wrong_expected",
                "column_drift",
                "index_drift",
            ):
                self.case(
                    "guard_" + ds + "_" + kind,
                    lambda ds=ds, kind=kind: self.guard(ds, kind),
                    ["install_" + ds],
                )
        self.case(
            "guard_erp_existing_active_duplicates",
            lambda: self.guard("erp", "active_duplicate"),
            ["migration_erp"],
        )
        for name, fn in [
            ("active_sn_unique", self.stock_unique),
            ("multiple_zero_history_rows", self.stock_zero),
            ("legacy_v1_positive_rows_not_upgraded", self.stock_legacy_positive),
            ("stock_tenant_scope", self.stock_tenants),
            ("erp_sn_code_unique_and_tenant", lambda: self.sn_unique("erp")),
            ("sequence_scope_unique", self.sequence_scope),
            ("sequence_two_connection_cas", self.sequence_cas),
            ("sequence_for_update_blocks_then_releases", self.sequence_lock),
            ("sequence_lock_timeout", lambda: self.sequence_lock(timeout=True)),
        ]:
            self.case(name, fn, ["migration_erp"])
        self.case("mes_sn_code_unique_and_tenant", lambda: self.sn_unique("mes"), ["migration_mes"])
        self.case("mes_identity_unique_allows_legacy_null", self.mes_identity, ["migration_mes"])
        self.case(
            "nullable_legacy_tenant_active_index_boundary",
            self.nullable_tenant_stock_boundary,
            ["migration_erp"],
        )
        for ds in ("erp", "mes"):
            self.case(
                "nullable_legacy_tenant_sn_code_boundary_" + ds,
                lambda ds=ds: self.nullable_tenant_sn_boundary(ds),
                ["migration_" + ds],
            )
        self.case(
            "new_contract_tables_reject_null_tenant",
            self.new_tables_reject_null_tenant,
            ["migration_erp", "migration_mes"],
        )
        for ds in ("erp", "mes"):
            self.case(
                "business_outbox_local_rollback_" + ds,
                lambda ds=ds: self.source_local_rollback(ds),
                ["migration_" + ds],
            )
            self.case(
                "two_database_rollback_retry_replay_" + ds,
                lambda ds=ds: self.delivery(ds),
                ["migration_erp", "migration_mes"],
            )
            for kind in ("outbox", "inbox"):
                self.case(
                    "journal_tenant_stream_" + ds + "_" + kind,
                    lambda ds=ds, kind=kind: self.journal_tenants(ds, kind),
                    ["migration_" + ds],
                )
        self.case(
            "mill_final_tables_in_generator_install_migration_db",
            self.final_tables,
            ["install_erp", "install_mes", "migration_erp", "migration_mes"],
        )
        self.case("mill_print_task_unique_defaults", self.print_tasks, ["migration_mes"])
        self.case("mill_erp_audit_tenant_artifact", self.audit_artifact, ["migration_erp"])
        run_extended(self)
        self.case("final_schema_inputs_unchanged", lambda: {"unchanged": self.snapshot.unchanged()})

    def infrastructure(self):
        profile = self.box.verify()
        row = self.query(
            None, "SELECT VERSION(),@@version_comment,@@global.sql_mode,@@transaction_isolation;"
        ).first
        self.require(row[0].startswith("5.7."), "Actual server is not MySQL 5.7")
        self.report["mysql"] = {
            "version": row[0],
            "version_comment": row[1],
            "sql_mode": row[2],
            "transaction_isolation": row[3],
        }
        self.report["container"]["readiness_observations"] = self.box.readiness_observations
        self.report["container"]["local_docker_endpoint_kind"] = self.box.endpoint_kind
        self.report["container"].update(
            id=self.box.cid,
            image=self.box.image,
            network=profile["HostConfig"]["NetworkMode"],
            tmpfs=profile["HostConfig"]["Tmpfs"],
            port_bindings=profile["HostConfig"].get("PortBindings"),
        )
        return self.report["mysql"]

    def literal_coercibility(self):
        row = self.query(
            None,
            "SELECT COERCIBILITY(CONVERT(0x393031303031 USING utf8mb4)),COERCIBILITY("
            + sql_value(TENANT)
            + ");",
        ).first
        self.require(
            row == ["2", "4"],
            "Synthetic SQL value must have literal coercibility, not expression collation",
        )
        return {
            "old_convert_expression_coercibility": row[0],
            "safe_hex_literal_coercibility": row[1],
        }

    def generator(self, ds):
        for part, func in (("", "migration"), ("preflight-", "preflight")):
            actual = self.snapshot.text(
                f"script/sql/update/2026-10-18-material-trace-{part}{ds}.sql"
            )
            self.require(
                actual == self.snapshot.contract[func](ds),
                f"{ds} {part}DDL differs from generator; do not hand patch",
            )
        return {"new_tables": list(self.snapshot.contract["TABLES"][ds]), "matches": True}

    def replay(self, ds):
        schema = self.dbs["legacy_" + ds]
        before = self.metadata(schema, ds)
        self.migration(ds, schema)
        self.require(before == self.metadata(schema, ds), "Rerunning migration changed metadata")
        return {"metadata_unchanged": True}

    def parity(self, ds):
        fresh = self.metadata(self.dbs["fresh_" + ds], ds)
        migrated = self.metadata(self.dbs["legacy_" + ds], ds)
        if fresh != migrated:
            (self.output / (ds + "-metadata-difference.json")).write_text(
                json.dumps({"fresh": fresh, "migrated": migrated}, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            raise ValidationError(
                "Fresh vs migrated metadata differs; see metadata-difference.json"
            )
        return {"columns": len(fresh["columns"]), "index_columns": len(fresh["indexes"])}

    def preflight(self, ds):
        sql = self.snapshot.text(f"script/sql/update/2026-10-18-material-trace-preflight-{ds}.sql")
        output = self.box.run_sql(self.dbs["legacy_" + ds], sql)
        return {
            "output_lines": len(output.splitlines()),
            "output_sha256": hashlib.sha256(output.encode()).hexdigest(),
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        default="results/"
        + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        + "-"
        + uuid.uuid4().hex[:8],
    )
    args = parser.parse_args()
    output = scoped_output(HERE, args.output)
    output.mkdir(parents=True, exist_ok=True)
    snapshot = SchemaSnapshot()
    box = DockerSandbox()
    checks = Checks(box, snapshot, output)
    print("Dedicated container:", box.name, "(no ports; tmpfs; cached image only)", flush=True)
    try:
        box.start()
        checks.run()
    except Exception as exc:
        checks.case("infrastructure_or_runner_failure", lambda: (_ for _ in ()).throw(exc))
    finally:
        cleanup_owned(box, output, checks.report)
        checks.report["finished_at"] = datetime.now(timezone.utc).isoformat()
        checks.save()
        (HERE / "results/latest.json").write_text(
            json.dumps(checks.report, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    count = checks.report["counts"]
    print("RESULT", json.dumps(count), "cleanup=", box.removed, flush=True)
    print("REPORT", str(output / "report.json"), flush=True)
    return int(not count["executed"] or bool(count["fail"] or count["blocked"]) or not box.removed)


if __name__ == "__main__":
    raise SystemExit(main())
