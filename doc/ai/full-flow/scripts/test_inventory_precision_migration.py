"""库存精度及历史保全的离线 SQL 合同；不连接数据库。"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
INSTALL = ROOT / 'script/sql/ry-erp.sql'
MIGRATION = ROOT / 'script/sql/update/2026-10-02-inventory-precision-quarantine-ry-erp.sql'


def table(sql, name):
    match = re.search(r'CREATE TABLE `' + name + r'`\s*\((.*?)\) ENGINE', sql, re.S)
    if not match:
        raise AssertionError('Missing table: ' + name)
    return match.group(1)


def statements(sql):
    return re.sub(r'--[^\n]*|/\*.*?\*/', '', sql, flags=re.S).strip()


class InventoryPrecisionMigrationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.install = INSTALL.read_text(encoding='utf-8')

    def migration(self):
        self.assertTrue(MIGRATION.is_file(), 'Additive inventory migration is missing')
        return MIGRATION.read_text(encoding='utf-8')

    def test_new_install_stock_quantities_are_native_decimal18_4(self):
        body = table(self.install, 'wm_material_stock')
        for name in ('stock_quantity', 'reserved_quantity'):
            self.assertRegex(body, rf'`{name}` decimal\(18, 4\) NULL DEFAULT NULL')

    def test_new_install_tx_quantities_are_native_decimal18_4(self):
        body = table(self.install, 'wm_material_stock_tx')
        for name in ('tx_quantity', 'before_quantity', 'after_quantity'):
            self.assertRegex(body, rf'`{name}` decimal\(18, 4\) NULL DEFAULT NULL')

    def test_new_install_receipt_line_raw_quantity_is_native_decimal18_4(self):
        body = table(self.install, 'wm_material_receive_line')
        column = next(line.strip() for line in body.splitlines() if line.strip().startswith('`quantity_received`'))
        self.assertRegex(column, r'^`quantity_received` decimal\(18, 4\) NOT NULL ')

    def test_new_install_receipt_detail_raw_quantity_is_native_decimal18_4(self):
        body = table(self.install, 'wm_material_receive_detail')
        column = next(line.strip() for line in body.splitlines() if line.strip().startswith('`quantity`'))
        self.assertRegex(column, r'^`quantity` decimal\(18, 4\) NOT NULL ')

    def test_no_default_version_or_origin_can_certify_new_or_old_writes(self):
        for name in ('wm_material_stock', 'wm_material_stock_tx'):
            body = table(self.install, name)
            self.assertIn("`quantity_origin` varchar(32) NOT NULL DEFAULT 'UNPROVEN'", body)
            self.assertIn('`quantity_contract_version` int(11) NULL DEFAULT NULL', body)
            self.assertIn('`quantity_uom_version` varchar(64) NULL DEFAULT NULL', body)
            self.assertIn('`quantity_evidence_hash` char(64) NULL DEFAULT NULL', body)

    def test_stock_shadow_values_and_quantity_and_reservation_versions_are_unknown(self):
        body = table(self.install, 'wm_material_stock')
        for name in ('stock_quantity_exact', 'reserved_quantity_exact'):
            self.assertIn(f'`{name}` decimal(18, 4) NULL DEFAULT NULL', body)
        for name in ('quantity_version', 'reservation_version', 'quantity_source_head_tx_id', 'reservation_head_event_id'):
            self.assertIn(f'`{name}` bigint(20) NULL DEFAULT NULL', body)

    def test_tx_shadows_and_sequence_and_request_identity_are_not_backfilled(self):
        body = table(self.install, 'wm_material_stock_tx')
        for name in ('tx_quantity_exact', 'before_quantity_exact', 'after_quantity_exact'):
            self.assertIn(f'`{name}` decimal(18, 4) NULL DEFAULT NULL', body)
        for name in ('quantity_version_before', 'quantity_version_after'):
            self.assertIn(f'`{name}` bigint(20) NULL DEFAULT NULL', body)
        self.assertIn('`source_event_key` varchar(64) NULL DEFAULT NULL', body)
        self.assertRegex(body, r'UNIQUE (?:INDEX|KEY) `uk_stock_tx_source_event`\(`tenant_id`, `source_event_key`\)')

    def test_reservation_events_bind_owner_stock_unit_source_and_exact_continuous_balance(self):
        body = table(self.install, 'wm_stock_reservation_event')
        for name in ('tenant_id', 'event_id', 'stock_id', 'material_id', 'base_uom_id', 'quantity_uom_version',
                     'owner_doc_type', 'owner_doc_id', 'owner_doc_line_id', 'owner_mrp_detail_id',
                     'source_doc_type', 'source_doc_id', 'source_doc_line_id', 'source_event_key',
                     'reservation_version_before', 'reservation_version_after', 'quantity_contract_version'):
            self.assertIn('`' + name + '`', body)
        for name in ('reserved_delta', 'before_reserved_quantity', 'after_reserved_quantity'):
            self.assertIn(f'`{name}` decimal(18, 4) NOT NULL', body)
        self.assertRegex(body, r'UNIQUE (?:INDEX|KEY) `uk_reservation_source_event`\(`tenant_id`, `source_event_key`\)')
        self.assertRegex(body, r'UNIQUE (?:INDEX|KEY) `uk_reservation_stock_version`\(`tenant_id`, `stock_id`, `reservation_version_after`\)')
        self.assertNotIn('`verified`', body)

    def test_migration_is_once_only_additive_no_data_rewrite_cast_or_precision_modify(self):
        sql = statements(self.migration())
        self.assertNotRegex(sql, r'(?i)\b(?:UPDATE|INSERT|DELETE|REPLACE|TRUNCATE|DROP|MODIFY|CHANGE|CAST|CONVERT|RENAME)\b')
        self.assertNotIn('IF NOT EXISTS', sql.upper(), 'Do not mask partial/repeated migration')
        self.assertTrue(sql.startswith('USE `ry-erp`;'))
        self.assertEqual(re.findall(r'(?i)ALTER TABLE `(\w+)`', sql), ['wm_material_stock', 'wm_material_stock_tx'])
        self.assertEqual(re.findall(r'(?i)CREATE TABLE `(\w+)`', sql), ['wm_stock_reservation_event'])

    def test_migration_adds_same_unknown_contract_columns_as_new_install(self):
        sql = self.migration()
        for name in ('wm_material_stock', 'wm_material_stock_tx'):
            added = re.search(r'ALTER TABLE `' + name + r'`(.*?);', sql, re.S)
            self.assertIsNotNone(added)
            body = table(self.install, name)
            for line in added.group(1).splitlines():
                column = re.search(r'ADD COLUMN (`[^`]+` .*?)(?:,)?$', line.strip())
                if column:
                    self.assertIn(column.group(1).rstrip(','), body)
        self.assertEqual(table(sql, 'wm_stock_reservation_event'), table(self.install, 'wm_stock_reservation_event'))

    def test_exact_transaction_sequence_is_unique_per_tenant_and_stock(self):
        body = table(self.install, 'wm_material_stock_tx')
        self.assertRegex(body, r'UNIQUE (?:INDEX|KEY) `uk_stock_tx_quantity_version`\(`tenant_id`, `material_stock_id`, `quantity_version_after`\)')
        self.assertIn('ADD UNIQUE INDEX `uk_stock_tx_quantity_version`', self.migration())

    def test_migration_documents_explicit_audit_backup_no_writer_or_cutover_authorization(self):
        sql = self.migration()
        for word in ('只读审计', '备份', '一次性', 'UNPROVEN', 'DOUBLE', 'BLOCKED', '新写服务', '切换', '不填零', '不猜单位'):
            self.assertIn(word, sql)


if __name__ == '__main__':
    unittest.main()
