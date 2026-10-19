"""验证归属守卫与查询提取规则，不启动容器。"""

import unittest
from pathlib import Path
from harness import (
    ValidationError,
    extract_tables,
    verify_ownership,
    scoped_output,
    ident,
    sql_value,
    final_mysql_ready,
    blocking_transaction,
    local_docker_endpoint,
    cleanup_owned,
    SchemaSnapshot,
)


class HarnessSafetyTests(unittest.TestCase):
    def profile(self):
        return {
            "Id": "owned-id",
            "Name": "/nexus-mtrace-mysql57-test",
            "Image": "sha256:owned-image",
            "Config": {
                "Labels": {
                    "com.nexus.material-trace.mysql57.run-id": "token",
                    "com.nexus.material-trace.mysql57.workspace": "workspace",
                }
            },
            "HostConfig": {
                "NetworkMode": "none",
                "PortBindings": {},
                "Binds": None,
                "Tmpfs": {"/var/lib/mysql": "rw,size=768m"},
            },
            "Mounts": [],
        }

    def check(self, profile):
        return verify_ownership(
            profile,
            "owned-id",
            "nexus-mtrace-mysql57-test",
            "token",
            "workspace",
            "sha256:owned-image",
        )

    def test_snapshot_freezes_the_product_writer_and_the_executed_harness(self):
        snapshot = SchemaSnapshot()
        for path in [
            "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/" + name + ".java"
            for name in (
                "MesProductLotOutputWriter",
                "MesCompletionCancellationService",
                "MesTraceStore",
            )
        ] + [
            "script/tools/material_trace/tests_mysql57/" + name + ".py"
            for name in ("harness", "java_sql", "reader_cases", "extended_cases", "run")
        ]:
            with self.subTest(path=path):
                self.assertTrue(path in snapshot.hashes, path)
        self.assertIn("pro_product_lot_output", snapshot.current["mes"])
        cancellation = [
            "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/" + name + ".java"
            for name in ("ErpCompletionCancellationService", "ErpCompletionCancellationHandler")
        ]
        cancellation += [
            "ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/" + name + ".java"
            for name in (
                "CompletionCancellationRequest",
                "CompletionCancellationResult",
                "CompletionCancellationProof",
                "port/CompletionCancellationPort",
                "jdbc/JdbcTraceJournal",
            )
        ]
        for path in cancellation:
            with self.subTest(path=path):
                self.assertTrue(path in snapshot.hashes, path)

    def test_strict_entry_is_frozen_and_matches_the_owner_generator(self):
        snapshot = SchemaSnapshot()
        path = "script/sql/update/2026-10-19-material-trace-strict-constraints-erp.sql"
        self.assertIn(
            path, snapshot.hashes, "A changing strict-phase entry must invalidate final evidence"
        )
        self.assertEqual(snapshot.contract["strict_constraints"](), snapshot.text(path))
        self.assertIn("script/tools/material_trace/tests_mysql57/strict_cases.py", snapshot.hashes)

    def test_snapshot_freezes_recall_and_canonical_identity_contracts(self):
        snapshot = SchemaSnapshot()
        common = "ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/"
        required = [
            common + name + ".java"
            for name in (
                "RecallSubjectSnapshot",
                "TraceRecallSnapshots",
                "IdentityTracePart",
                "BatchTracePart",
                "SerialIdentity",
                "WorkOrderTraceImpact",
                "TraceReadValues",
                "TraceCursor",
                "port/IdentityTraceReadPort",
                "port/SerialIdentityPort",
            )
        ]
        required += [
            "ruoyi-admin/src/main/java/com/nexus/web/service/MaterialIdentityTraceService.java",
            "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpTraceContext.java",
            "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesTraceContext.java",
        ]
        for path in required:
            with self.subTest(path=path):
                self.assertTrue(path in snapshot.hashes, path)

    def test_barcode_persistence_and_api_types_participate_in_final_source_drift_checks(self):
        snapshot = SchemaSnapshot()
        base = "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/"
        for suffix in (
            "service/impl/WmBarcodeServiceImpl.java",
            "domain/WmBarcode.java",
            "domain/bo/WmBarcodeBo.java",
            "domain/vo/WmBarcodeVo.java",
        ):
            with self.subTest(source=suffix):
                self.assertIn(base + suffix, snapshot.hashes)

    def test_row_lock_observer_accepts_only_its_declared_business_table(self):
        from types import SimpleNamespace
        from run import Checks

        table = "wm_work_order_tracking_policy"
        status = (
            "---TRANSACTION 10, ACTIVE 1 sec\nLOCK WAIT\nMySQL thread id 42, query id 9 updating\nUPDATE `synthetic`.`"
            + table
            + "` SET snapshot_id=snapshot_id\nRECORD LOCKS"
        )
        fake = SimpleNamespace(
            dbs={"legacy_erp": "synthetic"},
            query=lambda *args: SimpleNamespace(rows=[[status]]),
            require=Checks.require,
        )
        evidence = Checks.wait_for_lock(fake, 42, expected_table=table)
        self.assertEqual(42, evidence["requesting_connection"])
        with self.assertRaises(ValidationError):
            Checks.wait_for_lock(fake, 42)

    def test_readiness_rejects_the_entrypoint_temporary_skip_networking_server(self):
        self.assertFalse(final_mysql_ready(0, "1\n"))
        self.assertFalse(final_mysql_ready(1, ""))
        self.assertTrue(final_mysql_ready(0, "0\n"))

    def test_only_the_requested_server_thread_can_satisfy_a_row_lock_observation(self):
        status = "---TRANSACTION 10, ACTIVE 1 sec\nLOCK WAIT 1 lock struct(s)\nMySQL thread id 42, query id 9 updating\nUPDATE wm_serial_sequence SET current_value=1\nRECORD LOCKS index PRIMARY\n---TRANSACTION 11, ACTIVE 2 sec\nMySQL thread id 43, query id 10"
        self.assertIsNotNone(blocking_transaction(status, 42))
        self.assertIsNone(blocking_transaction(status, 43))
        self.assertIsNone(blocking_transaction(status, 99))

    def test_remote_docker_endpoints_are_rejected_before_contacting_the_daemon(self):
        self.assertTrue(local_docker_endpoint("npipe:////./pipe/docker_engine"))
        self.assertTrue(local_docker_endpoint("unix:///var/run/docker.sock"))
        self.assertTrue(local_docker_endpoint("tcp://127.0.0.1:2375"))
        for endpoint in (
            "ssh://production-host",
            "tcp://example.invalid:2376",
            "https://example.invalid",
        ):
            self.assertFalse(local_docker_endpoint(endpoint))

    def test_owned_cleanup_still_runs_when_optional_log_capture_fails(self):
        class FakeOwnedSandbox:
            cid = "owned"
            removed = False

            def verify(self):
                return {}

            def command(self, _args):
                raise OSError("synthetic log capture failure")

            def cleanup(self):
                self.removed = True

        box = FakeOwnedSandbox()
        report = {"container": {}}
        cleanup_owned(box, Path(__file__).resolve().parent, report)
        self.assertTrue(box.removed)
        self.assertTrue(report["container"]["removed_after_ownership_verification"])
        self.assertIn("synthetic log capture failure", report["diagnostic_error"])

    def test_owned_private_tmpfs_profile_is_accepted(self):
        self.assertTrue(self.check(self.profile()))

    def test_cleanup_rejects_wrong_owner_name_id_or_image(self):
        for key, value in [("Id", "another"), ("Name", "/existing-mysql"), ("Image", "another")]:
            with self.subTest(key=key):
                p = self.profile()
                p[key] = value
                with self.assertRaises(ValidationError):
                    self.check(p)
        p = self.profile()
        p["Config"]["Labels"] = {}
        with self.assertRaises(ValidationError):
            self.check(p)

    def test_private_container_cannot_bind_ports_or_existing_storage(self):
        for update in [
            {"NetworkMode": "bridge"},
            {"PortBindings": {"3306/tcp": [{"HostPort": "3306"}]}},
            {"Binds": ["existing:/var/lib/mysql"]},
            {"Tmpfs": {}},
        ]:
            p = self.profile()
            p["HostConfig"].update(update)
            with self.assertRaises(ValidationError):
                self.check(p)
        p = self.profile()
        p["Mounts"] = [{"Type": "volume", "Name": "existing"}]
        with self.assertRaises(ValidationError):
            self.check(p)

    def test_only_create_table_ddl_is_extracted_from_install_file(self):
        script = "DROP TABLE IF EXISTS `a`; CREATE TABLE `a` (`id` bigint, `note` text COMMENT 'semi;colon'); INSERT INTO `a` VALUES(1,'real seed is excluded'); SET @x=1;"
        tables = extract_tables(script)
        self.assertEqual(["a"], list(tables))
        self.assertNotIn("INSERT", tables["a"])
        self.assertIn("semi;colon", tables["a"])

    def test_extraction_handles_comments_and_escaped_quotes(self):
        script = "-- comment;\nCREATE TABLE IF NOT EXISTS `b` (`s` varchar(30) COMMENT 'it''s;quoted'); /* ignored; */ # line;\nINSERT INTO b VALUES ('x');"
        self.assertEqual(["b"], list(extract_tables(script)))

    def test_empty_duplicate_qualified_or_unterminated_install_is_rejected(self):
        for script in [
            "SELECT 1;",
            "CREATE TABLE a(id int); CREATE TABLE a(id int);",
            "CREATE TABLE existing.a(id int);",
            "CREATE TABLE a(s text COMMENT 'bad);",
        ]:
            with self.subTest(script=script), self.assertRaises(ValidationError):
                extract_tables(script)

    def test_output_and_identifiers_cannot_escape_the_new_validation_directory(self):
        root = Path(__file__).resolve().parent
        self.assertEqual(root / "results" / "safe.json", scoped_output(root, "results/safe.json"))
        with self.assertRaises(ValidationError):
            scoped_output(root, "../schema_contract.py")
        self.assertEqual("`mt57_erp`", ident("mt57_erp"))
        with self.assertRaises(ValidationError):
            ident("erp; DROP DATABASE existing")

    def test_synthetic_values_are_sql_literals_not_command_fragments(self):
        self.assertEqual("NULL", sql_value(None))
        self.assertEqual("42", sql_value(42))
        value = sql_value("x'; DROP TABLE a; --")
        self.assertTrue(value.startswith("_utf8mb4 0x"))
        self.assertNotIn("DROP TABLE", value)


class FrozenSourceTests(unittest.TestCase):
    def test_source_bytes_are_indexed_with_flat_windows_safe_names(self):
        import tempfile, hashlib, json
        from types import SimpleNamespace
        from run import Checks

        relative = "ruoyi-common/" + "nested/" * 20 + "CompletionCancellationRequest.java"
        data = b"synthetic frozen source"
        digest = hashlib.sha256(data).hexdigest()
        snapshot = SimpleNamespace(
            raw={relative: data}, hashes={relative: digest}, baseline_ref="SYNTHETIC_BASELINE"
        )
        box = SimpleNamespace(name="SYNTHETIC_NO_CONTAINER", token="SYNTHETIC")
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            Checks(box, snapshot, output)
            index = json.loads((output / "source-snapshot-index.json").read_text(encoding="utf-8"))
            entry = index[relative]
            self.assertEqual(digest, entry["sha256"])
            self.assertEqual(data, (output / entry["file"]).read_bytes())
            self.assertEqual(2, len(Path(entry["file"]).parts))
            self.assertLessEqual(len(Path(entry["file"]).name), 52)


if __name__ == "__main__":
    unittest.main()
