"""新增出生与收货请求表的离线SQL合同，不接触真实数据库。"""
import re
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[4]

class FormalEntrySchemaTests(unittest.TestCase):
    def test_birth_and_receipt_incrementals_only_create_empty_new_tables(self):
        cases={'2026-10-04-certified-source-birth-ry-erp.sql':'erp_certified_source_birth',
               '2026-10-04-certified-receive-command-ry-erp.sql':'wm_certified_receive_command'}
        for file,table in cases.items():
            with self.subTest(table=table):
                raw=(ROOT/'script/sql/update'/file).read_text(encoding='utf-8-sig')
                sql=re.sub(r'--[^\n]*','',raw).strip()
                self.assertTrue(re.match(r'CREATE TABLE\s+`'+table+r'`\s*\(',sql))
                self.assertEqual(1,sql.count(';'));self.assertRegex(sql,r'(?i)ENGINE\s*=\s*InnoDB')
                self.assertNotRegex(sql,r'(?i)\b(DROP|UPDATE|INSERT|DELETE|TRUNCATE|ALTER)\s+(TABLE|INTO|FROM|`\w+`)')
    def test_receive_request_identity_and_native_quantity_are_permanent(self):
        sql=(ROOT/'script/sql/update/2026-10-04-certified-receive-command-ry-erp.sql').read_text(encoding='utf-8-sig')
        self.assertIn('PRIMARY KEY (`tenant_id`,`request_key`)',sql)
        self.assertIn('`quantity` decimal(18,4) NOT NULL',sql)
        for name in ('actor_id','order_id','purchase_line_id','receive_id','line_id','base_uom_id','arrival_date'):
            self.assertRegex(sql,r'`'+name+r'`\s+[^\n]+NOT NULL')
    def test_fresh_install_contains_both_exact_incremental_table_definitions(self):
        master=(ROOT/'script/sql/ry-erp.sql').read_text(encoding='utf-8-sig')
        for file in ('2026-10-04-certified-source-birth-ry-erp.sql','2026-10-04-certified-receive-command-ry-erp.sql'):
            raw=(ROOT/'script/sql/update'/file).read_text(encoding='utf-8-sig')
            sql=re.sub(r'--[^\n]*','',raw).strip()
            self.assertTrue(sql in master,'新增结构必须同步新装定义，不能只留增量：'+file)

if __name__=='__main__':unittest.main()
