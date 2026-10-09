"""Offline regression tests for a bounded number-only maintenance plan."""
import copy,unittest
from finish_numbering_repair import build_number_repairs

class NumberRepairTest(unittest.TestCase):
    patterns={"SO_ORDER_CODE":r"XSDD[0-9]{11}","PRD_PLAN_CODE":r"SCJH[0-9]{8}"}
    def fixture(self):
        cases=[{'soId':'100','soNo':'SO-CAP-V01','lineId':'101','planId':'200','planNo':'PP-CAP-V01','requirementCount':1}]
        allocations=[{'kind':'sales_order','id':'100','oldCode':'SO-CAP-V01','newCode':'XSDD20261008001','rule':'SO_ORDER_CODE'},
                     {'kind':'production_plan','id':'200','oldCode':'PP-CAP-V01','newCode':'SCJH20260032','rule':'PRD_PLAN_CODE'}]
        sale={'tenant_id':'000000','so_id':100,'so_no':'SO-CAP-V01','order_status':'CONFIRMED','total_amount':'100.00'}
        plan={'tenant_id':'000000','plan_id':200,'plan_no':'PP-CAP-V01','reference_no':'SO-CAP-V01','order_line_id':101,'status':'RELEASED','total_quantity':'22.00'}
        mrp={'tenant_id':'000000','requirement_id':300,'plan_id':200,'plan_no':'PP-CAP-V01','status':'DRAFT'}
        refs=[{'table':'so_sales_order_header','field':'so_no','primaryKey':['so_id'],'engine':'InnoDB','rows':[sale]},
              {'table':'prd_production_plan','field':'plan_no','primaryKey':['plan_id'],'engine':'InnoDB','rows':[plan]},
              {'table':'prd_production_plan','field':'reference_no','primaryKey':['plan_id'],'engine':'InnoDB','rows':[copy.deepcopy(plan)]},
              {'table':'prd_material_requirement','field':'plan_no','primaryKey':['requirement_id'],'engine':'InnoDB','rows':[mrp]}]
        return cases,allocations,refs
    def run_plan(self,change=None,extras=()):
        cases,a,r=self.fixture()
        if change:change(cases,a,r)
        return build_number_repairs(cases,a,r,allowed_extra_plan_ids=extras,code_patterns=self.patterns)
    def rejects(self,change,extras=()):
        with self.assertRaises(ValueError):self.run_plan(change,extras)
    def test_number_only_changes_and_source_references(self):
        rows=self.run_plan();self.assertEqual(3,len(rows))
        self.assertEqual({'so_no':'XSDD20261008001'},rows[0]['changes'])
        self.assertEqual({'plan_no':'SCJH20260032','reference_no':'XSDD20261008001'},rows[1]['changes'])
        self.assertEqual({'plan_no':'SCJH20260032'},rows[2]['changes'])
        self.assertEqual('RELEASED',rows[1]['before']['status'])
    def test_inputs_are_not_mutated(self):
        args=self.fixture();before=copy.deepcopy(args);build_number_repairs(*args,code_patterns=self.patterns);self.assertEqual(before,args)
    def test_foreign_tenant_is_refused(self):self.rejects(lambda c,a,r:r[0]['rows'][0].update(tenant_id='other'))
    def test_empty_cases_are_refused(self):self.rejects(lambda c,a,r:c.clear())
    def test_unknown_table_is_refused(self):self.rejects(lambda c,a,r:r[0].update(table='wm_stock'))
    def test_status_column_is_never_a_repair_target(self):self.rejects(lambda c,a,r:r[0].update(field='order_status'))
    def test_unexpected_primary_key_is_refused(self):self.rejects(lambda c,a,r:r[0].update(primaryKey=['tenant_id','so_id']))
    def test_nontransactional_table_is_refused(self):self.rejects(lambda c,a,r:r[0].update(engine='MyISAM'))
    def test_unrelated_parent_is_refused(self):self.rejects(lambda c,a,r:r[3]['rows'][0].update(plan_id=999))
    def test_sales_lineage_cannot_be_guessed_from_number(self):self.rejects(lambda c,a,r:r[2]['rows'][0].update(order_line_id=999))
    def test_extra_automatic_plan_must_be_allowlisted(self):
        def change(c,a,r):
            extra=copy.deepcopy(r[2]['rows'][0]);extra.update(plan_id=201,plan_no='SCJH20260020',status='DRAFT');r[2]['rows'].append(extra)
        self.rejects(change)
        rows=self.run_plan(change,('201',));self.assertEqual(4,len(rows));self.assertEqual({'reference_no':'XSDD20261008001'},rows[2]['changes'])
    def test_extra_plan_with_live_status_is_refused(self):
        def change(c,a,r):
            extra=copy.deepcopy(r[2]['rows'][0]);extra.update(plan_id=201,plan_no='SCJH20260020',status='RELEASED');r[2]['rows'].append(extra)
        self.rejects(change,('201',))
    def test_old_allocation_code_must_match_batch(self):self.rejects(lambda c,a,r:a[0].update(oldCode='SO-OTHER'))
    def test_wrong_native_rule_is_refused(self):self.rejects(lambda c,a,r:a[0].update(rule='PRD_PLAN_CODE'))
    def test_missing_allocation_is_refused(self):self.rejects(lambda c,a,r:a.pop())
    def test_duplicate_allocations_are_refused(self):self.rejects(lambda c,a,r:a.append(copy.deepcopy(a[0])))
    def test_duplicate_new_codes_are_refused(self):self.rejects(lambda c,a,r:a[1].update(newCode=a[0]['newCode']))
    def test_batch_labels_are_not_accepted_as_formal_codes(self):self.rejects(lambda c,a,r:a[0].update(newCode='SO-CAP-AGAIN'))
    def test_missing_sales_row_is_refused(self):self.rejects(lambda c,a,r:r[0]['rows'].clear())
    def test_missing_requirement_is_refused(self):self.rejects(lambda c,a,r:r[3]['rows'].clear())
    def test_float_identifiers_are_refused(self):self.rejects(lambda c,a,r:c[0].update(soId=100.0))
    def test_conflicting_row_snapshots_are_refused(self):self.rejects(lambda c,a,r:r[2]['rows'][0].update(total_quantity='23.00'))
    def test_unmatched_old_reference_is_refused(self):self.rejects(lambda c,a,r:r[3]['rows'][0].update(plan_no='PP-OTHER'))
    def test_lock_order_is_deterministic(self):
        cases,a,r=self.fixture();self.assertEqual(build_number_repairs(cases,a,r,code_patterns=self.patterns),build_number_repairs(cases,list(reversed(a)),list(reversed(r)),code_patterns=self.patterns))

    def test_configured_number_patterns_are_required(self):
        with self.assertRaises(ValueError):build_number_repairs(*self.fixture())

if __name__=='__main__':unittest.main()
