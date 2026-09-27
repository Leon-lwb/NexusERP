"""生成基线 SQL 的离线契约测试，不连接 MySQL。"""
import json
import hashlib
import importlib.util
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class DemoSqlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases = [json.loads(line) for line in (ROOT / 'cases.jsonl').read_text(encoding='utf-8').splitlines()]
        cls.master = (ROOT / 'seed/demo-master-data.sql').read_text(encoding='utf-8')
        cls.business = (ROOT / 'seed/demo-business-data.sql').read_text(encoding='utf-8')
        cls.reset = (ROOT / 'seed/reset-demo-data.sql').read_text(encoding='utf-8')

    def test_approved_eighteen_revisions_match_current_golden_and_case_expectations(self):
        proposal = json.loads((ROOT / 'reports/2026-09-27-golden-revision-proposal.json').read_text(encoding='utf-8'))
        expected = {x['caseId']: x for x in map(json.loads, (ROOT / 'expected-results.jsonl').read_text(encoding='utf-8').splitlines())}
        cases = {x['caseId']: x for x in self.cases}
        self.assertEqual(18, len(proposal['revisions']))
        for rev in proposal['revisions']:
            for rows in (expected, cases):
                for field, value in rev['proposedGolden'].items():
                    self.assertCountEqual(value, rows[rev['caseId']][field]) if field == 'requiredActions' else self.assertEqual(value, rows[rev['caseId']][field], (rev['caseId'], field))

    def test_six_negative_cases_have_exact_evidence_codes(self):
        codes = {26: 'ROUTING_EVIDENCE_MISSING', 27: 'ROUTING_EVIDENCE_MISSING',
                 28: 'ROUTING_EVIDENCE_MISSING', 38: 'SCHEDULE_EVIDENCE_MISSING',
                 39: 'SHORTAGE_SIMULATION_INCOMPLETE', 40: 'CAPACITY_EVIDENCE_MISSING'}
        expected = {x['caseId']: x for x in map(json.loads, (ROOT / 'expected-results.jsonl').read_text(encoding='utf-8').splitlines())}
        for case in self.cases:
            if int(case['caseId'][-3:]) in codes:
                code = codes[int(case['caseId'][-3:])]
                self.assertEqual(code, case.get('expectedFailureCode'))
                self.assertEqual(code, expected[case['caseId']].get('expectedFailureCode'))

    def test_v1_archive_and_v2_approval_preserve_inputs_and_unapproved_expectations(self):
        archive = ROOT / 'golden/versions/v1'
        self.assertTrue((archive / 'cases.jsonl').exists(), 'Immutable v1 archive must exist')
        self.assertEqual('f5f0d089e51130a84156fb77c441dd53aa433fb9dad87ba0abf08bdee32fd3d3',
                         hashlib.sha256((archive / 'cases.jsonl').read_bytes()).hexdigest())
        self.assertEqual('e05859947ea468cc14dd5cb99c55f3ced0f0b62370bc1361dbb37da1587275c8',
                         hashlib.sha256((archive / 'expected-results.jsonl').read_bytes()).hexdigest())
        proposal = json.loads((ROOT / 'reports/2026-09-27-golden-revision-proposal.json').read_text(encoding='utf-8'))
        allowed = {x['caseId'] for x in proposal['revisions']}
        for name in ('cases.jsonl', 'expected-results.jsonl'):
            old = [json.loads(x) for x in (archive / name).read_text(encoding='utf-8').splitlines()]
            new = [json.loads(x) for x in (ROOT / name).read_text(encoding='utf-8').splitlines()]
            self.assertEqual(40, len(old)); self.assertEqual(40, len(new))
            for before, after in zip(old, new):
                omit = {'expectedFailureCode'}
                if before['caseId'] in allowed: omit |= {'recommendedScenarioCode', 'requiredActions'}
                self.assertEqual({k:v for k,v in before.items() if k not in omit},
                                 {k:v for k,v in after.items() if k not in omit})
        approval_path = ROOT / 'golden/2026-09-27-v2-approval.json'
        self.assertTrue(approval_path.exists())
        approval = json.loads(approval_path.read_text(encoding='utf-8'))
        self.assertEqual('APPLIED', approval['status']); self.assertEqual(18, approval['approvedRevisionsCount'])
        for name, sha in approval['activeHashes'].items():
            self.assertEqual(sha, hashlib.sha256((ROOT / name).read_bytes()).hexdigest())

    def test_active_v2_lf_and_binary_v1_attributes_are_cross_checkout_stable(self):
        self.assertTrue((ROOT / '.gitattributes').exists())
        for name in ('cases.jsonl', 'expected-results.jsonl'):
            self.assertNotIn(b'\r', (ROOT / name).read_bytes(), 'v2 must be canonical LF on every OS')
        import subprocess, shutil
        repo = ROOT.parents[3]
        if not (repo / '.git').exists() or not shutil.which('git'): return
        for name in ('cases.jsonl', 'expected-results.jsonl'):
            path = ROOT / 'golden/versions/v1' / name
            raw = subprocess.run(['git', 'hash-object', '--no-filters', str(path)], cwd=repo,
                capture_output=True, text=True)
            if raw.returncode == 0:
                filtered = subprocess.run(['git', 'hash-object', '--path='+path.relative_to(repo).as_posix(), str(path)],
                    cwd=repo, capture_output=True, text=True, check=True)
                self.assertEqual(raw.stdout.strip(), filtered.stdout.strip(), 'Historical bytes must not be newline-normalized')

    def test_generator_exactly_reproduces_active_v2_without_database_access(self):
        spec = importlib.util.spec_from_file_location('golden_builder', ROOT / 'scripts/build-fixtures.py')
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        for n in range(1, 41):
            case, expected = module.sample(n)
            self.assertEqual(self.cases[n-1], case)
            active = [json.loads(x) for x in (ROOT / 'expected-results.jsonl').read_text(encoding='utf-8').splitlines()]
            self.assertEqual(active[n-1], expected)

    def test_all_rows_are_schema_guarded(self):
        for sql in (self.master, self.business):
            statements = re.findall(r'INSERT INTO .*?;', sql, re.S)
            self.assertGreater(len(statements), 40)
            self.assertTrue(all("DATABASE() = 'nexus_ai_decision_demo_erp'" in stmt for stmt in statements))
        for stmt in re.findall(r'DELETE FROM .*?;', self.reset, re.S):
            self.assertIn("DATABASE() = 'nexus_ai_decision_demo_erp'", stmt)
            self.assertIn("`tenant_id` = '000000'", stmt)
            self.assertIn("LIKE 'DEMO-AI-DECISION-%'", stmt)

    def test_every_multi_shortage_case_has_two_distinct_component_demands(self):
        for case in self.cases[11:15]:
            code = case['bizCode']
            self.assertIn(code + '-COMP-2', self.business, code)
            self.assertIn(code + '-COMP-2', self.master)

    def test_shortage_day_matches_the_seeded_requirement_date(self):
        for case in self.cases:
            if case['facts']['shortageQty'] > 0:
                self.assertEqual(7, case['facts']['daysUntilFirstShortage'], case['caseId'])
    def test_on_time_purchase_leaves_the_declared_component_gap(self):
        purchase_lines = re.findall(r'INSERT INTO `pu_purchase_order_line` .*?;', self.business, re.S)
        for case in self.cases:
            facts = case['facts']
            if facts['shortageQty'] > 0 and facts['purchaseArrivalDelayDays'] == 0:
                po = next(row for row in purchase_lines if f"'{case['bizCode']}-COMP'" in row)
                self.assertRegex(po, rf"'{case['bizCode']}-COMP', 'Demo component \d+', 1, 0")
    def test_supplier_samples_match_the_advertised_ontime_rate(self):
        samples = re.findall(r"DATE_FORMAT\(CURDATE\(\), '%Y-%m'\), (\d+), (\d+), (\d+),",
                             self.business)
        self.assertEqual(40, len(samples))
        for rate, total, on_time in samples:
            self.assertEqual(int(rate) * int(total), int(on_time) * 100)

    def test_multi_shortage_mrp_header_agrees_with_two_details(self):
        for case in self.cases[11:15]:
            code = case['bizCode']
            rows = re.findall(r'INSERT INTO `prd_material_requirement` .*?;', self.business, re.S)
            header = next(row for row in rows if code + '-MRP' in row)
            self.assertIn('total_material_types`', header)
            self.assertIn(f"'{code}-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 2,", header)
    def test_missing_evidence_cases_are_backed_by_real_missing_rows(self):
        self.assertFalse(self.cases[37]['facts']['currentSchedulePresent'])
        self.assertFalse(self.cases[38]['facts']['mrpComplete'])
        for number, table, suffix in ((38, 'prd_schedule_plan', '-SCHEDULE'),
                                      (39, 'prd_material_requirement', '-MRP'),
                                      (40, 'prd_capacity_calendar', '-ST')):
            code = f'DEMO-AI-DECISION-{number:03d}' + suffix
            rows = re.findall(rf'INSERT INTO `{table}` .*?;', self.business, re.S)
            self.assertFalse(any(code in row for row in rows), f'{code} must be absent from {table}')

    def test_saved_task_duration_agrees_with_its_persisted_date_range(self):
        rows = re.findall(r'INSERT INTO `prd_schedule_task` .*?;', self.business, re.S)
        for case in self.cases:
            if not case['facts']['currentSchedulePresent']:
                continue
            row = next(row for row in rows if f"'{case['bizCode']}'" in row)
            end = 14 + case['facts']['tardinessDays']
            self.assertRegex(row, rf"INTERVAL 3 DAY\), DATE_ADD\(CURDATE\(\), INTERVAL {end} DAY\), {end - 2},")
    def test_renderer_rejects_text_for_integer_column_before_reaching_mysql(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('demo_seed', ROOT / 'scripts/build-seed.py')
        renderer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(renderer)
        with self.assertRaisesRegex(ValueError, 'status'):
            renderer.insert([], 'prd_material_requirement_detail', 'detail_id',
                            {'detail_id': 1, 'tenant_id': '000000', 'status': 'CONFIRMED'})
        renderer.insert([], 'prd_material_requirement_detail', 'detail_id',
                        {'detail_id': 1, 'tenant_id': '000000', 'status': 1})
    def test_saved_schedule_tasks_have_real_route_order(self):
        rows = re.findall(r'INSERT INTO `prd_schedule_task` \((.*?)\)\s*SELECT (.*?)\s*WHERE ',
                          self.business, re.S)
        self.assertEqual(39, len(rows))  # 案例 038 刻意不提供已保存基线。
        self.assertTrue(all('`seq_num`' in cols and '`step_id`' in cols for cols, _ in rows))
    def test_saved_tasks_have_complete_observed_resource_calendar_coverage(self):
        calendar_rows = re.findall(r'INSERT INTO `prd_capacity_calendar` .*?;', self.business, re.S)
        for case in self.cases:
            if not case['facts']['currentSchedulePresent'] or not case['facts']['capacityComplete']:
                continue
            owned = [row for row in calendar_rows if f"'{case['bizCode']}-ST'" in row]
            days = {int(day) for row in owned for day in
                    re.findall(r'`calendar_date`.*?INTERVAL (\d+) DAY', row, re.S)}
            end = 14 + case['facts']['tardinessDays']
            self.assertTrue(set(range(3, end + 1)).issubset(days),
                            f"{case['caseId']} missing calendar dates between day 3 and day {end}")
            self.assertEqual(len(days), len(owned), f"{case['caseId']} has duplicate calendar dates")
    def test_capacity_rows_set_hours_used_by_actual_aps_assembler(self):
        rows = re.findall(r'INSERT INTO `prd_capacity_calendar` \((.*?)\)\s*SELECT (.*?)\s*WHERE ',
                          self.business, re.S)
        self.assertGreater(len(rows), 30)
        self.assertTrue(all('`remaining_hours`' in cols for cols, _ in rows))
        self.assertTrue(all(re.search(r'`remaining_hours`', cols) for cols, _ in rows))


if __name__ == '__main__':
    unittest.main()