"""当前新装出生与收货请求表的离线 SQL 合同，不接触真实数据库。"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[4]


def table(sql, name):
    match = re.search(r'CREATE TABLE `' + name + r'`\s*\((.*?)\)\s*ENGINE\s*=\s*(\w+)', sql, re.S)
    if not match or match.group(2).upper() != 'INNODB':
        raise AssertionError('Missing InnoDB table: ' + name)
    return match.group(1)


class FormalEntrySchemaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.install = (ROOT / 'script/sql/ry-erp.sql').read_text(encoding='utf-8-sig')

    def test_birth_and_receipt_install_tables_start_empty_without_inferred_certification(self):
        for name in ('erp_certified_source_birth', 'wm_certified_receive_command'):
            with self.subTest(table=name):
                body = table(self.install, name)
                self.assertRegex(body, r'`tenant_id` varchar\(20\) NOT NULL')
                self.assertNotIn('AUTO_INCREMENT', body.upper())
                self.assertNotRegex(self.install, rf'(?im)^\s*(?:INSERT|REPLACE)\s+INTO\s+`{name}`')

    def test_receive_request_identity_and_native_quantity_are_permanent(self):
        body = table(self.install, 'wm_certified_receive_command')
        self.assertIn('PRIMARY KEY (`tenant_id`,`request_key`)', body)
        self.assertIn('`request_hash` char(64) NOT NULL', body)
        self.assertIn('`quantity` decimal(18,4) NOT NULL', body)
        for name in ('actor_id', 'order_id', 'purchase_line_id', 'receive_id', 'line_id',
                     'material_id', 'base_uom_id', 'arrival_date', 'warehouse_id', 'area_id', 'location_id'):
            self.assertRegex(body, r'`' + name + r'`\s+[^\n]+NOT NULL')
        for name in ('notice_id', 'notice_line_id'):
            self.assertIn(f'`{name}` bigint(20) NULL DEFAULT NULL', body)
        self.assertIn('UNIQUE KEY `uk_wm_certified_receive` (`tenant_id`,`receive_id`,`line_id`)', body)
        self.assertIn('KEY `idx_wm_certified_arrival_ref` (`tenant_id`,`notice_id`,`notice_line_id`)', body)
        self.assertIn('KEY `idx_wm_certified_order_line` (`tenant_id`,`order_id`,`purchase_line_id`)', body)

    def test_birth_evidence_binds_tenant_material_unit_and_storage(self):
        body = table(self.install, 'erp_certified_source_birth')
        self.assertIn('PRIMARY KEY (`birth_id`)', body)
        self.assertIn('UNIQUE KEY `uk_source_birth_material` (`tenant_id`, `material_id`)', body)
        for name in ('birth_id', 'material_id', 'category_id', 'base_uom_id', 'quantity_scale',
                     'conversion_version', 'unit_verified_at', 'warehouse_id', 'area_id', 'location_id',
                     'born_at', 'actor_id', 'scenario', 'initial_state'):
            self.assertRegex(body, r'`' + name + r'`\s+[^\n]+NOT NULL')
        for name in ('unit_fingerprint', 'storage_fingerprint', 'birth_fingerprint'):
            self.assertIn(f'`{name}` char(64) NOT NULL', body)
        self.assertIn('`quantity_scale` tinyint(3) unsigned NOT NULL', body)
        self.assertIn('EMPTY_AT_BIRTH', body)
        self.assertNotRegex(body, r'`initial_state`[^\n]*\bDEFAULT\b')


if __name__ == '__main__':
    unittest.main()
