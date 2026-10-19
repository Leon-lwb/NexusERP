"""静态契约测试不建立数据库连接，也不把文本检查当成 MySQL 验收。"""

import json
import re
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / "script/sql"


def text(p):
    return p.read_text(encoding="utf-8-sig") if p.exists() else ""


def table(s, n):
    m = re.search(r"CREATE TABLE(?: IF NOT EXISTS)? `" + n + r"` \([\s\S]*?\n\)[^;]*;", s)
    return m[0] if m else ""


JAVA_LITERALS = re.compile(
    r'//[^\n]*|/\*[\s\S]*?\*/|(?P<block>"""[\s\S]*?""")|(?P<string>"(?:\\.|[^"\\])*")'
)


def java_string_constants(source):
    """提取真实字符串，合并相邻常量拼接，忽略注释中的伪 SQL。

    只合并由加号直接连接的字面量；变量及方法调用保持分隔，避免猜测动态表达式。
    """
    current = None
    previous_end = 0
    for token in JAVA_LITERALS.finditer(source):
        if token.group("block"):
            value = textwrap.dedent(token.group()[3:-3])
        elif token.group("string"):
            value = json.loads(token.group())
        else:
            continue
        separator = JAVA_LITERALS.sub("", source[previous_end : token.start()]).strip()
        if current is not None and separator == "+":
            current += value
        else:
            if current is not None:
                yield current
            current = value
        previous_end = token.end()
    if current is not None:
        yield current


class SqlContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.erp = text(SQL / "update/2026-10-18-material-trace-erp.sql")
        cls.mes = text(SQL / "update/2026-10-18-material-trace-mes.sql")

    def test_registry_exact_columns_and_keys(self):
        expected = {
            "wm_serial_reservation": "reservation_id tenant_id command_id fingerprint material_id quantity rule_id rule_version rule_snapshot source_type source_id cycle_key biz_date status created_at updated_at",
            "wm_serial_sequence": "sequence_id tenant_id namespace rule_key cycle_key current_value version created_at updated_at",
            "wm_sn_alias": "alias_id tenant_id identity_id source_type source_id material_id alias_code command_id created_at",
        }
        for n, cols in expected.items():
            s = table(self.erp, n)
            self.assertTrue(s, n)
            self.assertEqual(cols.split(), re.findall(r"^  `([^`]+)`", s, re.M))
        self.assertIn("(`tenant_id`,`command_id`)", table(self.erp, "wm_serial_reservation"))
        self.assertIn(
            "(`tenant_id`,`namespace`,`rule_key`,`cycle_key`)",
            table(self.erp, "wm_serial_sequence"),
        )
        self.assertIn("COLLATE utf8mb4_bin", table(self.erp, "wm_sn_alias"))

    def test_migrations_are_additive_and_mysql57(self):
        for s in (self.erp, self.mes):
            self.assertTrue(s)
            s = re.sub(r"(?m)^--.*$", "", s)
            self.assertNotRegex(s, r"(?i)(DROP TABLE|TRUNCATE TABLE|DELETE FROM|UPDATE `)")
            self.assertNotRegex(
                s, r"(?i)(ADD COLUMN IF NOT EXISTS|utf8mb4_0900|CHECK \(|SKIP LOCKED)"
            )
            self.assertIn("information_schema.COLUMNS", s)
            self.assertIn("information_schema.STATISTICS", s)
            self.assertIn("@material_trace_expected_schema", s)
            self.assertIn("@material_trace_backup_reference", s)

    def test_active_sn_only_version_two(self):
        s = text(SQL / "ry-erp.sql")
        stock = table(s, "wm_material_stock")
        self.assertIn(
            "CASE WHEN tracking_version >= 2 AND stock_quantity > 0 THEN sn_id ELSE NULL END", stock
        )
        self.assertIn("(`tenant_id`,`active_sn_id`)", stock)
        self.assertIn("SIGNAL SQLSTATE", self.erp)

    def test_legacy_default_only_not_historical_updates(self):
        self.assertIn("ALTER COLUMN `status` SET DEFAULT 'REGISTERED'", self.erp)
        self.assertNotRegex(self.erp, r"(?i)SET `?status`?\s*=")
        for term in (
            "allow_mixed_lot",
            "tracking_stage",
            "registration_command",
            "rule_snapshot",
            "rule_version",
        ):
            self.assertIn(term, self.erp)

    def test_stock_and_transaction_sn_length(self):
        s = text(SQL / "ry-erp.sql")
        for n in ("wm_material_stock", "wm_material_stock_tx"):
            self.assertRegex(table(s, n), r"`sn_code` varchar\(128\)")

    def test_mes_identity_and_nullable_workorder(self):
        s = table(text(SQL / "ry-mes.sql"), "pro_sn")
        for term in ("identity_id", "product_lot_id", "identity_version", "handoff_status"):
            self.assertIn(term, s)
        self.assertRegex(s, r"`mes_wo_id` bigint\(20\) DEFAULT NULL")
        self.assertIn("(`tenant_id`,`identity_id`)", s)

    def test_component_ledger_and_mixed_lot_guard(self):
        s = table(self.mes, "lt_sn_consumption")
        for (
            c
        ) in "identity_id local_sn_id component_material_id component_lot_id quantity wo_consumption_id process_id operation_ref entry_type original_entry_id policy_version created_by created_at".split():
            self.assertIn("`" + c + "`", s)
        self.assertIn("(`tenant_id`,`command_id`,`line_no`)", s)
        self.assertIn(
            "PRIMARY KEY (`tenant_id`,`identity_id`,`component_material_id`)",
            table(self.mes, "lt_sn_material_guard"),
        )

    def test_manifest_maps_common_contract(self):
        for n, cols in {
            "pro_completion_manifest": "manifest_id revision tenant_id mes_wo_id erp_wo_id material_id line_count good_qty scrap_qty content_hash status",
            "pro_completion_manifest_line": "line_no identity_id local_sn_id sn_code batch_id local_lot_id material_id quantity result source_version",
        }.items():
            s = table(self.mes, n)
            self.assertTrue(s, n)
            for c in cols.split():
                self.assertIn("`" + c + "`", s)

    def test_outbox_inbox_stream_lease_and_payload(self):
        columns = "event_id tenant_id source_module aggregate_type aggregate_id aggregate_version event_type schema_version payload content_hash status attempts next_attempt_at lease_owner lease_until last_error result_payload created_at updated_at applied_at".split()
        for ds, s in (("wm", self.erp), ("mes", self.mes)):
            for kind in ("outbox", "inbox"):
                t = table(s, ds + "_trace_" + kind)
                self.assertTrue(t)
                for c in columns:
                    self.assertIn("`" + c + "`", t)
                self.assertIn(
                    "(`tenant_id`,`source_module`,`aggregate_type`,`aggregate_id`,`aggregate_version`)",
                    t,
                )

    def test_journals_on_both_datasources(self):
        for ds, s in (("wm", self.erp), ("mes", self.mes)):
            self.assertTrue(table(s, ds + "_trace_repair_run"))
            t = table(s, ds + "_trace_repair_item")
            for c in (
                "run_id",
                "target_table",
                "target_key",
                "before_json",
                "after_json",
                "evidence_json",
                "before_hash",
                "after_hash",
                "evidence_hash",
                "status",
                "version",
            ):
                self.assertIn("`" + c + "`", t)

    def test_new_table_ddl_install_matches_incremental(self):
        for fn, s in (("ry-erp.sql", self.erp), ("ry-mes.sql", self.mes)):
            names = re.findall(r"CREATE TABLE IF NOT EXISTS `([^`]+)`", s)
            self.assertGreater(len(names), 8)
            for n in names:
                self.assertEqual(table(s, n), table(text(SQL / fn), n), n)

    def test_generated_column_and_existing_schema_drift_are_rejected(self):
        self.assertIn("GENERATION_EXPRESSION", self.erp)
        self.assertIn("STORED GENERATED", self.erp)
        for name in ("wm_serial_reservation", "wm_serial_sequence", "wm_sn_alias"):
            self.assertIn("表" + name + "索引偏离契约", self.erp)
            self.assertIn("表" + name + "字段定义偏离契约", self.erp)

    def test_preflight_scripts_only_inspect(self):
        for ds in ("erp", "mes"):
            s = text(SQL / ("update/2026-10-18-material-trace-preflight-" + ds + ".sql"))
            self.assertTrue(s)
            executable = re.sub(r"(?m)^--.*$", "", s)
            self.assertNotRegex(
                executable,
                r"(?i)\b(ALTER|CREATE|DROP|INSERT|UPDATE|DELETE|TRUNCATE|REPLACE)\s+(TABLE|INTO|FROM|PROCEDURE|`)",
            )
            self.assertIn("information_schema", s)

    def test_rendering_has_no_schema_diff_on_second_run(self):
        import importlib.util

        spec = importlib.util.spec_from_file_location(
            "trace_schema_contract", ROOT / "script/tools/material_trace/schema_contract.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for ds, content in (("erp", self.erp), ("mes", self.mes)):
            self.assertEqual(content, module.migration(ds))

    def test_label_snapshot_columns_and_legacy_results(self):
        install = text(SQL / "ry-mes.sql")
        template = table(install, "mes_label_template")
        log = table(install, "mes_label_print_log")
        self.assertRegex(template, r"`template_revision` bigint\(20\) NOT NULL DEFAULT 1")
        for c in ("template_revision", "original_print_log_id"):
            self.assertRegex(log, r"`" + c + r"` bigint\(20\) DEFAULT NULL")
        for c in ("template_snapshot", "field_snapshot", "rendered_payload"):
            self.assertRegex(log, r"`" + c + r"` longtext")
        for c in ("content_hash", "payload_hash"):
            self.assertRegex(log, r"`" + c + r"` char\(64\)")
        for c in ("payload_charset", "snapshot_mode"):
            self.assertRegex(log, r"`" + c + r"` varchar\(32\)")
        self.assertRegex(log, r"`reprint_reason` varchar\(500\)")
        self.assertRegex(log, r"`retry_count` int\(11\) DEFAULT NULL")
        self.assertRegex(log, r"`sn_code` varchar\(128\)")
        self.assertIn("SENT", log)
        self.assertIn("SUCCESS", log)

    def test_alias_command_is_unique(self):
        self.assertIn(
            "UNIQUE KEY `uk_sn_alias_command` (`tenant_id`,`command_id`)",
            table(self.erp, "wm_sn_alias"),
        )

    def test_jdbc_journal_ack_null_retry_and_identifier_lengths(self):
        for prefix, s in (("wm", self.erp), ("mes", self.mes)):
            for kind in ("outbox", "inbox"):
                block = table(s, prefix + "_trace_" + kind)
                self.assertRegex(block, r"`next_attempt_at` datetime DEFAULT NULL")
                self.assertRegex(block, r"`aggregate_type` varchar\(64\)")
                self.assertRegex(block, r"`event_id` varchar\(64\)")
                self.assertIn("`result_payload` longtext", block)

    def test_allowlist_columns_match_install_schema(self):
        import sys

        sys.path.insert(0, str(ROOT / "script/tools/material_trace"))
        from metadata import KEYS, COLUMNS

        for name in KEYS:
            ds = "mes" if name.startswith(("pro_", "lt_", "mes_")) else "erp"
            block = table(text(SQL / ("ry-" + ds + ".sql")), name)
            self.assertTrue(block, name)
            actual = {
                a: b
                for a, b in re.findall(r"^  `([^`]+)` ([a-z]+(?:\(\d+(?:,\d+)?\))?)", block, re.M)
            }
            self.assertEqual(actual, COLUMNS[name], name)

    def test_durable_label_task_is_frozen_and_has_claim_indexes(self):
        block = table(self.mes, "mes_label_print_task")
        self.assertTrue(block, "MES必须有持久化打印任务，不能只在afterCommit内存发送")
        for name in (
            "task_id",
            "tenant_id",
            "task_key",
            "request_fingerprint",
            "frozen_record",
            "record_hash",
            "status",
            "attempt_count",
            "lease_token",
            "lease_until",
            "sending_started_at",
            "completed_at",
        ):
            self.assertIn("`" + name + "`", block)
        self.assertIn("UNIQUE KEY `uk_label_task_key` (`tenant_id`,`task_key`)", block)
        self.assertIn("`tenant_id`,`status`,`lease_until`", block)
        self.assertEqual(block, table(text(SQL / "ry-mes.sql"), "mes_label_print_task"))

    def test_audit_run_only_stores_tenant_scoped_artifact(self):
        block = table(self.erp, "wm_trace_audit_run")
        self.assertTrue(block)
        for name in (
            "audit_id",
            "tenant_id",
            "material_id",
            "status",
            "summary",
            "report_json",
            "content_hash",
            "created_at",
        ):
            self.assertIn("`" + name + "`", block)
        self.assertIn("DEFAULT 'REVIEW_REQUIRED'", block)
        self.assertIn("(`tenant_id`,`created_at`)", block)
        self.assertEqual(block, table(text(SQL / "ry-erp.sql"), "wm_trace_audit_run"))

    def test_label_retry_column_replaces_entire_comment_line(self):
        log = table(text(SQL / "ry-mes.sql"), "mes_label_print_log")
        line = next(line for line in log.splitlines() if "`retry_count`" in line)
        self.assertRegex(line, r"^  `retry_count` int\(11\) DEFAULT NULL COMMENT '[^']*',$")

    def test_completion_material_summary_preserves_unknown_legacy_manifests(self):
        block = table(self.mes, "pro_completion_manifest")
        self.assertRegex(block, r"`material_line_count` bigint\(20\) DEFAULT NULL")
        self.assertRegex(
            block, r"`material_hash` char\(64\) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL"
        )
        for name in ("material_line_count", "material_hash"):
            alter = "ALTER TABLE `pro_completion_manifest` ADD COLUMN `" + name + "`"
            self.assertIn(alter, self.mes)
            self.assertLess(
                self.mes.index("CREATE TABLE IF NOT EXISTS `pro_completion_manifest`"),
                self.mes.index(alter),
            )
            self.assertLess(
                self.mes.index(alter), self.mes.index("表pro_completion_manifest字段不完整")
            )
        self.assertNotRegex(self.mes, r"(?i)UPDATE\s+`?pro_completion_manifest")

    def test_completion_material_allocation_maps_actual_consumption_record(self):
        block = table(self.mes, "pro_completion_material_allocation")
        expected = "tenant_id manifest_id revision allocation_id material_id reservation_id reservation_line_id posting_group_id identity_id source_stock_id erp_batch_id quantity source_version evidence_hash source_event_id".split()
        self.assertEqual(expected, re.findall(r"^  `([^`]+)`", block, re.M))
        self.assertIn("PRIMARY KEY (`tenant_id`,`manifest_id`,`revision`,`allocation_id`)", block)
        self.assertIn("lt_wo_consumption.consumption_id", block)
        self.assertRegex(block, r"`quantity` decimal\(18,4\) NOT NULL")
        for name in (
            "reservation_id",
            "reservation_line_id",
            "posting_group_id",
            "identity_id",
            "source_stock_id",
            "erp_batch_id",
        ):
            self.assertRegex(block, r"`" + name + r"` bigint\(20\) DEFAULT NULL")
        for name in ("source_version", "evidence_hash", "source_event_id"):
            self.assertRegex(block, r"`" + name + r"` [^\n]+NOT NULL")

    def test_sn_only_allocation_keeps_batch_nullable_in_install_and_migration(self):
        for name in ("lt_lot_allocation", "pro_completion_material_allocation"):
            for sql in (self.mes, text(SQL / "ry-mes.sql")):
                self.assertRegex(table(sql, name), r"`erp_batch_id` bigint\(20\) DEFAULT NULL")
        self.assertIn(
            "ALTER TABLE `lt_lot_allocation` MODIFY COLUMN `erp_batch_id` bigint(20) DEFAULT NULL",
            self.mes,
        )
        self.assertIn("COLUMN_NAME='erp_batch_id' AND IS_NULLABLE='NO'", self.mes)
        self.assertRegex(self.mes, r"SELECT COLUMN_TYPE FROM [^\n]+[)] LIKE '%unsigned%'")

    def test_erp_material_reservation_and_actual_consumption_match_jdbc_contracts(self):
        expected = {
            "wm_trace_material_reservation": "reservation_id tenant_id command_id erp_wo_id mes_wo_id requirement_detail_id material_id policy_version quantity content_hash status",
            "wm_trace_material_reservation_line": "reservation_line_id tenant_id reservation_id line_no source_stock_id identity_id erp_batch_id valuation_group_id quantity consumed_quantity version",
            "wm_trace_material_consumption": "consumption_id tenant_id manifest_id manifest_revision allocation_id erp_wo_id material_id reservation_id reservation_line_id posting_group_id identity_id source_stock_id erp_batch_id quantity source_version evidence_hash source_event_id",
        }
        for name, columns in expected.items():
            block = table(self.erp, name)
            self.assertEqual(columns.split(), re.findall(r"^  `([^`]+)`", block, re.M), name)
            self.assertRegex(block, r"`quantity` decimal\(18,4\) NOT NULL")
        header = table(self.erp, "wm_trace_material_reservation")
        self.assertIn(
            "UNIQUE KEY `uk_material_reservation_command` (`tenant_id`,`command_id`)", header
        )
        self.assertIn("(`tenant_id`,`erp_wo_id`,`status`)", header)
        self.assertIn("(`tenant_id`,`requirement_detail_id`,`status`)", header)
        line = table(self.erp, "wm_trace_material_reservation_line")
        self.assertIn("(`tenant_id`,`reservation_id`,`line_no`)", line)
        self.assertIn("`source_stock_id` bigint(20) NOT NULL", line)
        for name in ("wm_trace_material_reservation_line", "wm_trace_material_consumption"):
            self.assertRegex(table(self.erp, name), r"`erp_batch_id` bigint\(20\) DEFAULT NULL")
        consumption = table(self.erp, "wm_trace_material_consumption")
        self.assertIn(
            "UNIQUE KEY `uk_material_consumption_allocation` (`tenant_id`,`manifest_id`,`manifest_revision`,`allocation_id`)",
            consumption,
        )
        self.assertIn("lt_wo_consumption.consumption_id", consumption)

    def test_coordinated_schema_has_thirty_new_tables(self):
        self.assertEqual(18, len(re.findall(r"CREATE TABLE IF NOT EXISTS `", self.erp)))
        self.assertEqual(12, len(re.findall(r"CREATE TABLE IF NOT EXISTS `", self.mes)))

    def test_product_lot_output_freezes_actual_source_without_inventing_batch_columns(self):
        expected = "output_id tenant_id command_id line_no mes_wo_id card_id step_record_id lot_id source_allocation_id material_id quantity result source_version created_by created_at".split()
        for sql in (self.mes, text(SQL / "ry-mes.sql")):
            block = table(sql, "pro_product_lot_output")
            self.assertEqual(expected, re.findall(r"^  `([^`]+)`", block, re.M))
            self.assertIn("PRIMARY KEY (`output_id`)", block)
            self.assertIn(
                "UNIQUE KEY `uk_product_output_command` (`tenant_id`,`command_id`,`line_no`)", block
            )
            self.assertIn("UNIQUE KEY `uk_product_output_lot` (`tenant_id`,`lot_id`)", block)
            self.assertIn("(`tenant_id`,`source_allocation_id`)", block)
            self.assertIn("(`tenant_id`,`step_record_id`)", block)
            self.assertRegex(block, r"`quantity` decimal\(18,4\) NOT NULL")
            self.assertRegex(block, r"`source_allocation_id` bigint\(20\) NOT NULL")
            self.assertNotIn("`erp_batch_id`", block)

    def test_manual_retirement_contract_keeps_complete_immutable_command_audit(self):
        expected = "operation_id tenant_id command_id fingerprint action identity_id reservation_id actor_id reason before_version after_version before_status after_status reservation_status source_type source_id before_json after_json result_json created_at".split()
        for sql in (self.erp, text(SQL / "ry-erp.sql")):
            block = table(sql, "wm_serial_lifecycle_command")
            self.assertEqual(expected, re.findall(r"^  `([^`]+)`", block, re.M))
            self.assertIn("PRIMARY KEY (`operation_id`)", block)
            self.assertIn(
                "UNIQUE KEY `uk_serial_lifecycle_command` (`tenant_id`,`command_id`)", block
            )
            self.assertIn(
                "KEY `idx_serial_lifecycle_identity` (`tenant_id`,`identity_id`,`created_at`)",
                block,
            )
            self.assertRegex(
                block, r"`fingerprint` char\(64\) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"
            )
            self.assertRegex(block, r"`source_id` bigint\(20\) DEFAULT NULL")
            for name in ("before_json", "after_json", "result_json"):
                self.assertIn("`" + name + "` longtext NOT NULL", block)
            self.assertNotIn("`updated_at`", block)

    def test_every_new_business_table_requires_a_complete_read_only_snapshot_contract(self):
        import sys

        sys.path.insert(0, str(ROOT / "script/tools/material_trace"))
        from core import COLUMNS, REQUIRED_TABLES, WRITABLE
        from schema_contract import TABLES

        independent_audit = {
            "wm_trace_audit_run",
            "wm_trace_stock_conversion_run",
            "wm_trace_repair_run",
            "wm_trace_repair_item",
            "mes_trace_repair_run",
            "mes_trace_repair_item",
        }
        for ds, definitions in TABLES.items():
            for name, ddl in definitions.items():
                if name in independent_audit:
                    continue
                with self.subTest(table=name):
                    self.assertIn(name, REQUIRED_TABLES)
                    self.assertNotIn(name, WRITABLE)
                    self.assertEqual(
                        set(re.findall(r"^  `([^`]+)`", ddl, re.M)), set(COLUMNS[name])
                    )

    def test_current_trace_service_insert_columns_exist_in_generated_install_schema(self):
        checked = 0
        for ds in ("erp", "mes"):
            install = text(SQL / ("ry-" + ds + ".sql"))
            definitions = {
                m[1]: set(re.findall(r"^  `([^`]+)`", m[0], re.M))
                for m in re.finditer(
                    r"CREATE TABLE(?: IF NOT EXISTS)? `([^`]+)` \([\s\S]*?\n\)[^;]*;", install
                )
            }
            package = ROOT / ("ruoyi-modules/ruoyi-" + ds + "/src/main/java/com/nexus/" + ds)
            paths = set((package / "trace").glob("*.java"))
            if ds == "erp":
                paths = (
                    set((package / "wm/trace").glob("*.java"))
                    | set((package / "wm/service").glob("WmTrace*.java"))
                    | set((package / "wm/service").glob("WmSerial*.java"))
                    | set(package.rglob("*Cancel*.java"))
                )
            for path in sorted(paths):
                for literal in java_string_constants(text(path)):
                    if not re.match(r"^\s*INSERT\s+INTO\s+[a-z][a-z0-9_]*\b", literal, re.I):
                        continue
                    match = re.match(
                        r"^\s*INSERT\s+INTO\s+([a-z][a-z0-9_]*)\s*\(([^)]+)\)",
                        literal,
                        re.I,
                    )
                    with self.subTest(source=path.name, sql=literal):
                        self.assertIsNotNone(match, "写入语句必须包含完整的静态列清单")
                        checked += 1
                        columns = {column.strip(" `\r\n\t") for column in match[2].split(",")}
                        self.assertIn(match[1], definitions)
                        self.assertFalse(
                            columns - definitions[match[1]],
                            str(sorted(columns - definitions[match[1]])),
                        )
        self.assertGreater(checked, 0)

    def test_actual_consumption_source_allocation_is_nullable_and_indexed(self):
        block = table(text(SQL / "ry-mes.sql"), "lt_wo_consumption")
        self.assertRegex(block, r"`source_allocation_id` bigint\(20\) DEFAULT NULL")
        self.assertIn(
            "KEY `idx_wo_consumption_source_allocation` (`tenant_id`,`source_allocation_id`)", block
        )
        self.assertIn(
            "ALTER TABLE `lt_wo_consumption` ADD COLUMN `source_allocation_id` bigint(20) DEFAULT NULL",
            self.mes,
        )
        self.assertNotRegex(self.mes, r"(?i)UPDATE\s+`?lt_wo_consumption")

    def test_posting_proof_freezes_actual_source_hash_and_policy_without_legacy_backfill(self):
        for sql in (self.erp, text(SQL / "ry-erp.sql")):
            block = table(sql, "wm_trace_posting_group")
            self.assertRegex(
                block, r"`source_hash` char\(64\) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"
            )
            self.assertRegex(block, r"`policy_version` bigint\(20\) NOT NULL")
        self.assertNotIn("ALTER TABLE `wm_trace_posting_group` ADD COLUMN", self.erp)
        self.assertNotIn("UPDATE `wm_trace_posting_group`", self.erp)
        self.assertIn("表wm_trace_posting_group字段不完整", self.erp)

    def test_stock_conversion_audit_is_separate_and_keeps_frozen_journal_on_revert(self):
        block = table(self.erp, "wm_trace_stock_conversion_run")
        for name in (
            "run_id",
            "tenant_id",
            "material_id",
            "target_database",
            "target_environment",
            "backup_reference",
            "cutover_at",
            "plan_hash",
            "before_snapshot_hash",
            "after_snapshot_hash",
            "bridge_hash",
            "journal_json",
            "journal_hash",
            "revert_hash",
            "status",
            "version",
            "created_at",
            "updated_at",
        ):
            self.assertIn("`" + name + "`", block)
        self.assertIn("PRIMARY KEY (`run_id`)", block)
        self.assertRegex(block, r"`journal_json` longtext NOT NULL")
        self.assertRegex(
            block, r"`revert_hash` char\(64\) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL"
        )
        self.assertIn("(`tenant_id`,`material_id`,`created_at`)", block)

    def test_work_order_policy_bank_matches_writer_and_is_complete_read_only_source(self):
        from core import COLUMNS, KEYS, REQUIRED_TABLES, READ_ONLY_SOURCE_TABLES, WRITABLE
        from schema_contract import TABLES

        name = "wm_work_order_tracking_policy"
        columns = "snapshot_id tenant_id erp_wo_id material_id snapshot_version policy_version policy_json policy_hash source_json source_hash snapshot_hash provenance evidence_ref frozen_at effective_from actor_id".split()
        self.assertIn(name, TABLES["erp"])
        for sql in (self.erp, text(SQL / "ry-erp.sql")):
            block = table(sql, name)
            self.assertEqual(columns, re.findall(r"^  `([^`]+)`", block, re.M))
            self.assertIn(
                "UNIQUE KEY `uk_wo_tracking_policy` (`tenant_id`,`erp_wo_id`,`material_id`)", block
            )
            self.assertIn("`actor_id` bigint(20) DEFAULT NULL", block)
            for field in ("frozen_at", "effective_from"):
                self.assertIn("`" + field + "` datetime(3) NOT NULL", block)
            for field in ("policy_hash", "source_hash", "snapshot_hash"):
                self.assertIn(
                    "`" + field + "` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", block
                )
            self.assertNotIn("DEFAULT 'ERP_RELEASE'", block)
        self.assertEqual(("tenant_id", "snapshot_id"), KEYS[name])
        self.assertEqual(set(columns), set(COLUMNS[name]))
        self.assertIn(name, REQUIRED_TABLES)
        self.assertIn(name, READ_ONLY_SOURCE_TABLES)
        self.assertNotIn(name, WRITABLE)

    def test_strict_tenant_constraints_are_separate_guarded_and_never_backfill(self):
        import schema_contract

        render = getattr(schema_contract, "strict_constraints", None)
        self.assertIsNotNone(render, "需要独立的清理后严格约束生成入口")
        sql = render()
        self.assertEqual(
            sql, text(SQL / "update/2026-10-19-material-trace-strict-constraints-erp.sql")
        )
        for token in (
            "@material_trace_expected_schema",
            "@material_trace_expected_server_uuid",
            "@material_trace_backup_reference",
            "@material_trace_maintenance_reference",
            "TENANT_CLEANUP_VERIFIED",
            "GENERATION_EXPRESSION",
            "NON_UNIQUE",
        ):
            self.assertIn(token, sql)
        first = sql.index("ALTER TABLE")
        for name in ("wm_sn", "wm_material_stock"):
            self.assertIn("FROM `" + name + "` WHERE tenant_id IS NULL", sql[:first])
            self.assertIn(
                "ALTER TABLE `"
                + name
                + "` MODIFY COLUMN `tenant_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL",
                sql,
            )
        self.assertNotRegex(sql, r"(?i)(UPDATE `|INSERT INTO|DELETE FROM|CHECK \()")
        self.assertNotIn("DEFAULT '000000'", sql)

    def test_policy_adoption_approval_matches_actual_service_contract(self):
        from core import COLUMNS, KEYS, READ_ONLY_SOURCE_TABLES, WRITABLE

        name = "wm_work_order_policy_adoption"
        fields = "adoption_id tenant_id command_id erp_wo_id expected_wo_hash source_json source_hash approval_json approval_hash request_hash proposed_by approved_by approved_at expires_at status version applied_at snapshot_hash".split()
        for sql in (self.erp, text(SQL / "ry-erp.sql")):
            block = table(sql, name)
            self.assertEqual(fields, re.findall(r"^  `([^`]+)`", block, re.M))
            self.assertIn(
                "UNIQUE KEY `uk_wo_policy_adoption_command` (`tenant_id`,`command_id`)", block
            )
            self.assertIn("`applied_at` datetime(3) DEFAULT NULL", block)
        self.assertEqual(set(fields), set(COLUMNS[name]))
        self.assertEqual(("tenant_id", "adoption_id"), KEYS[name])
        self.assertIn(name, READ_ONLY_SOURCE_TABLES)
        self.assertNotIn(name, WRITABLE)

    def test_barcode_primary_object_is_nullable_and_a_complete_read_only_source(self):
        from core import COLUMNS, KEYS, READ_ONLY_SOURCE_TABLES, WRITABLE

        for sql in (self.erp, text(SQL / "ry-erp.sql")):
            self.assertRegex(
                sql, r"`primary_object_type` varchar\(16\)(?: COLLATE utf8mb4_bin)? DEFAULT NULL"
            )
        self.assertNotRegex(self.erp, r"(?i)UPDATE\s+`?wm_barcode")
        self.assertEqual(("tenant_id", "barcode_id"), KEYS["wm_barcode"])
        self.assertEqual(
            set(re.findall(r"^  `([^`]+)`", table(text(SQL / "ry-erp.sql"), "wm_barcode"), re.M)),
            set(COLUMNS["wm_barcode"]),
        )
        self.assertIn("wm_barcode", READ_ONLY_SOURCE_TABLES)
        self.assertNotIn("wm_barcode", WRITABLE)
