"""按租户和日期限定的 APS 单据编号契约；不访问或写入数据库。"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
INITIAL = ROOT / 'script/sql/ry-erp.sql'
MIGRATION = ROOT / 'doc/ai/full-flow/scripts/2026-09-30-aps-plan-number-sequence.sql'


class ApsPlanNumberSequenceContractTest(unittest.TestCase):
    def test_existing_installation_reserves_deleted_numbers_and_seeds_prior_maxima(self):
        sql = MIGRATION.read_text(encoding='utf-8')
        self.assertNotRegex(sql, r'(?im)^\s*(?:DROP|DELETE|UPDATE|TRUNCATE)\s')
        self.assertRegex(sql, r'(?is)CREATE\s+TABLE\s+`ry-erp`\.`prd_schedule_no_sequence`')
        self.assertRegex(sql, r'(?is)INSERT\s+INTO\s+`ry-erp`\.`prd_schedule_no_sequence`\s*\([^)]*tenant_id[^)]*plan_date[^)]*last_seq[^)]*\)')
        self.assertRegex(sql, r'(?is)MAX\s*\(\s*CAST\s*\(\s*SUBSTRING\s*\(\s*plan_no\s*,\s*12\s*,\s*4\s*\)')
        self.assertRegex(sql, r'(?is)FROM\s+`ry-erp`\.`prd_schedule_plan`')
        self.assertNotRegex(sql, r'(?is)FROM\s+`ry-erp`\.`prd_schedule_plan`[^;]*\bdel_flag\s*=')
        self.assertRegex(sql, r'(?is)MODIFY\s+COLUMN\s+`tenant_id`\s+varchar\(20\).*?NOT\s+NULL')
        self.assertRegex(sql, r'(?is)ALTER\s+TABLE\s+`ry-erp`\.`prd_schedule_plan`[\s\S]*?ADD\s+UNIQUE\s+(?:INDEX|KEY)\s+`uk_psp_tenant_plan_no`\s*\(\s*`tenant_id`\s*,\s*`plan_no`\s*\)')

    def test_new_installation_uses_identical_unique_number_reservation(self):
        sql = INITIAL.read_text(encoding='utf-8')
        plan = re.search(r'(?is)CREATE TABLE `prd_schedule_plan`\s*\((.*?)\) ENGINE', sql)
        seq = re.search(r'(?is)CREATE TABLE `prd_schedule_no_sequence`\s*\((.*?)\) ENGINE', sql)
        self.assertIsNotNone(plan)
        self.assertIsNotNone(seq)
        self.assertRegex(plan.group(1), r'`tenant_id`\s+varchar\(20\).*?NOT NULL')
        self.assertRegex(plan.group(1), r'UNIQUE INDEX `uk_psp_tenant_plan_no`\s*\(`tenant_id`,\s*`plan_no`\)')
        self.assertRegex(seq.group(1), r'`tenant_id`\s+varchar\(20\).*?NOT NULL')
        self.assertRegex(seq.group(1), r'`plan_date`\s+date\s+NOT NULL')
        self.assertRegex(seq.group(1), r'`last_seq`\s+int\(11\)\s+NOT NULL')
        self.assertRegex(seq.group(1), r'PRIMARY KEY\s*\(`tenant_id`,\s*`plan_date`\)')


if __name__ == '__main__':
    unittest.main()
