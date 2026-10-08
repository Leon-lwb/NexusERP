"""库存精度及历史保全的离线 SQL 合同；不连接数据库。"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
INSTALL = ROOT / 'script/sql/ry-erp.sql'


def table(sql, name):
    match = re.search(r'CREATE TABLE `' + name + r'`\s*\((.*?)\) ENGINE', sql, re.S)
    if not match:
        raise AssertionError('Missing table: ' + name)
    return match.group(1)


class InventoryPrecisionMigrationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.install = INSTALL.read_text(encoding='utf-8')

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

    def test_reservation_evidence_does_not_default_to_certified_history(self):
        body = table(self.install, 'wm_stock_reservation_event')
        self.assertIn("`quantity_origin` varchar(32) NOT NULL DEFAULT 'UNPROVEN'", body)
        self.assertIn('`quantity_contract_version` int(11) NULL DEFAULT NULL', body)
        self.assertIn('`quantity_evidence_hash` char(64) NULL DEFAULT NULL', body)
        for name in ('owner_mrp_detail_id', 'related_stock_tx_id'):
            self.assertIn(f'`{name}` bigint(20) NULL DEFAULT NULL', body)

    def test_stock_and_tx_unit_and_source_metadata_remains_unknown(self):
        for name in ('wm_material_stock', 'wm_material_stock_tx'):
            body = table(self.install, name)
            self.assertRegex(body, r'`base_uom_id` varchar\(64\)[^\n]* NULL DEFAULT NULL')
            self.assertIn('`material_id` bigint(20) NOT NULL', body)
        stock = table(self.install, 'wm_material_stock')
        self.assertIn('`quantity_source_at` datetime(6) NULL DEFAULT NULL', stock)
        tx = table(self.install, 'wm_material_stock_tx')
        for name in ('source_doc_id', 'source_doc_line_id'):
            self.assertIn(f'`{name}` bigint(20) NULL DEFAULT NULL', tx)
        self.assertIn('`material_stock_id` bigint(20) NOT NULL', tx)

    def test_exact_transaction_sequence_is_unique_per_tenant_and_stock(self):
        body = table(self.install, 'wm_material_stock_tx')
        self.assertRegex(body, r'UNIQUE (?:INDEX|KEY) `uk_stock_tx_quantity_version`\(`tenant_id`, `material_stock_id`, `quantity_version_after`\)')

    def test_new_reservation_ledger_is_empty_and_requires_explicit_source_and_owner(self):
        name = 'wm_stock_reservation_event'
        body = table(self.install, name)
        self.assertNotRegex(self.install, rf'(?im)^\s*(?:INSERT|REPLACE)\s+INTO\s+`{name}`')
        self.assertIn('PRIMARY KEY (`event_id`)', body)
        for column in ('tenant_id', 'stock_id', 'material_id', 'base_uom_id', 'quantity_uom_version',
                       'owner_doc_type', 'owner_doc_id', 'owner_doc_line_id', 'source_doc_type',
                       'source_doc_id', 'source_doc_line_id', 'source_event_key',
                       'reservation_version_before', 'reservation_version_after'):
            line = next(line for line in body.splitlines() if line.strip().startswith('`' + column + '`'))
            self.assertIn('NOT NULL', line)
            self.assertNotRegex(line, r'\bDEFAULT\b')


if __name__ == '__main__':
    unittest.main()
