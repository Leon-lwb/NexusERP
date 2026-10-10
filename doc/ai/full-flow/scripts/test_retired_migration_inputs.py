"""Retired migration inputs fail closed offline, before any database access."""
from contextlib import redirect_stdout
import importlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch


class RetiredStockMigrationInputTests(unittest.TestCase):
    def setUp(self):
        self.m = importlib.import_module('stock_native_precision_migration')
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.missing = Path(temporary.name) / 'retired.sql'
        patched = patch.object(self.m, 'SQL_PATH', self.missing)
        patched.start()
        self.addCleanup(patched.stop)

    def assert_retired(self, action):
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            action()
        self.assertEqual('MIGRATION_RETIRED', caught.exception.code)
        self.assertFalse(caught.exception.ddl_attempted)

    def test_missing_source_digest_is_classified_as_retired(self):
        self.assert_retired(self.m.artifact_hashes)

    def test_missing_template_is_classified_as_retired(self):
        self.assert_retired(self.m.sql_template)

    def test_all_entry_points_reject_before_store_factory(self):
        factory = Mock(side_effect=AssertionError('database must not be opened'))
        common = dict(store_factory=factory, maintenance=None, backup=None, keys=None, files=None)
        for entry, extra in (
            (self.m.precheck, {}),
            (self.m.execute_once, dict(reviewed_plan=None, execution=None, receipt_dir=self.missing.parent)),
            (self.m.resume_once, dict(reviewed_plan=None, parent_execution=None,
                 recovery_maintenance=None, execution=None, receipt_dir=self.missing.parent)),
        ):
            with self.subTest(entry=entry.__name__):
                self.assert_retired(lambda: entry(**common, **extra))
        factory.assert_not_called()

    def test_direct_adapter_rejects_before_driver_or_credentials(self):
        self.assert_retired(lambda: self.m.MySQLStore(None))

    def test_cli_reports_retired_without_creating_output_or_connecting(self):
        stream = io.StringIO()
        with patch.object(self.m, 'MySQLStore') as connect, redirect_stdout(stream):
            result = self.m.main(['precheck'])
        connect.assert_not_called()
        self.assertEqual(2, result)
        report = json.loads(stream.getvalue())
        self.assertEqual('MIGRATION_RETIRED', report['reason'])
        self.assertFalse(report['ddlAttempted'])
        self.assertEqual('NOT_PERFORMED', report['sourceCertification'])
        self.assertEqual([], list(self.missing.parent.iterdir()))


class RetiredProcurementMigrationInputTests(unittest.TestCase):
    def setUp(self):
        self.m = importlib.import_module('procurement_additive_migration')
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        patched = patch.object(self.m, 'ROOT', Path(temporary.name))
        patched.start()
        self.addCleanup(patched.stop)

    def assert_retired(self, action):
        with self.assertRaises(self.m.AdditiveMigrationBlocked) as caught:
            action()
        self.assertEqual('MIGRATION_RETIRED', caught.exception.category)

    def test_missing_fixed_source_is_classified_not_file_not_found(self):
        for name, _ in self.m.INPUTS:
            with self.subTest(name=name):
                self.assert_retired(lambda: self.m.read_source(name))

    def test_missing_inputs_reject_loader_and_plan_without_main_dump_fallback(self):
        self.assert_retired(self.m.load_operations)
        self.assert_retired(lambda: self.m.plan_operations({}))

    def test_missing_inputs_reject_execution_before_store_or_journal(self):
        store, journal = Mock(), Mock()
        self.assert_retired(lambda: self.m.execute_operations(store, [], journal))
        self.assertEqual([], store.mock_calls)
        journal.assert_not_called()

    def test_direct_adapter_rejects_before_any_database_cursor(self):
        connection = Mock()
        store = self.m.MysqlAdditiveStore(connection, 'a' * 64)
        for action in (store.initial, lambda: store.execute(None)):
            with self.subTest(action=action):
                self.assert_retired(action)
        self.assertEqual([], connection.mock_calls)


if __name__ == '__main__':
    unittest.main()
