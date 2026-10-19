"""Extraction must follow real Java literals and refuse unsupported SQL assembly."""
import unittest
from harness import ValidationError, sql_value
from java_sql import extract_selects, bind_sql, parameter_positions

class JavaSqlTests(unittest.TestCase):
    def test_ignores_commented_sql_and_keeps_escaped_java_literals(self):
        source = '// "SELECT ignored"\n/* "SELECT ignored2" */ jdbc.queryForList("SELECT \\"name\\" FROM t WHERE id=?", id);'
        rows = extract_selects(source)
        self.assertEqual([row['sql'] for row in rows], ['SELECT "name" FROM t WHERE id=?'])

    def test_text_block_subqueries_are_one_actual_statement(self):
        source = 'count("""\n    SELECT COUNT(*) FROM t\n    WHERE EXISTS(SELECT 1 FROM u WHERE u.id=t.id)\n    """, tenant);'
        rows = extract_selects(source)
        self.assertEqual(len(rows), 1)
        self.assertIn('EXISTS(SELECT 1', rows[0]['sql'])

    def test_expands_real_dynamic_in_at_one_two_and_five_hundred(self):
        source = 'jdbc.queryForList("SELECT id FROM t WHERE tenant=? AND id IN ("+String.join(",",Collections.nCopies(identities.size(),"?"))+")",args);'
        rows = extract_selects(source)
        self.assertEqual([row['sql'].count('?') for row in rows], [2, 3, 501])
        self.assertEqual([row['variant'] for row in rows], ['in_1', 'in_2', 'in_500'])

    def test_optional_serial_filter_is_executed_as_an_additional_sql_variant(self):
        source = 'String sql="SELECT SUM(qty) FROM t WHERE tenant=?";\nif(serial){sql+=" AND sn_id=? AND stock_id=?";}\n'
        rows = extract_selects(source)
        self.assertEqual([row['sql'].count('?') for row in rows], [1, 3])
        self.assertTrue(rows[1]['sql'].endswith(' AND sn_id=? AND stock_id=?'))

    def test_boolean_predicate_is_expanded_into_both_complete_sql_variants(self):
        source='public void read(boolean serial){\nString predicate=serial?"a.identity_id=?":"a.erp_batch_id=?";\nString sql="SELECT w.consumption_id FROM lt_wo_consumption w WHERE w.consumption_id>? AND "+predicate+" ORDER BY w.consumption_id LIMIT ?";\n}'
        queries=extract_selects(source)
        self.assertEqual(["serial_true","serial_false"],[q["variant"] for q in queries])
        self.assertEqual(["SELECT w.consumption_id FROM lt_wo_consumption w WHERE w.consumption_id>? AND a.identity_id=? ORDER BY w.consumption_id LIMIT ?","SELECT w.consumption_id FROM lt_wo_consumption w WHERE w.consumption_id>? AND a.erp_batch_id=? ORDER BY w.consumption_id LIMIT ?"],[q["sql"] for q in queries])
        self.assertTrue(all(len(parameter_positions(q["sql"]))==3 for q in queries))

    def test_actual_recall_reader_includes_unallocated_and_product_source_queries(self):
        from pathlib import Path
        root=Path(__file__).resolve().parents[4]
        source=(root/"ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesIdentityTraceReader.java").read_text(encoding="utf-8")
        queries=extract_selects(source)
        impact=[q for q in queries if q["sql"].startswith("SELECT w.consumption_id")]
        self.assertEqual({"serial_true","serial_false"},{q["variant"] for q in impact})
        self.assertTrue(all(q["sql"].endswith("ORDER BY w.consumption_id LIMIT ?") for q in impact))
        products=[q["sql"] for q in queries if "FROM pro_product_lot_output" in q["sql"]]
        self.assertEqual(1,len(products))
        self.assertIn("JOIN lt_lot_allocation",products[0])
        self.assertIn("a.allocation_id=o.source_allocation_id",products[0])
        self.assertNotIn("o.erp_batch_id",products[0])

    def test_unsupported_or_reassigned_predicate_never_silently_tests_one_branch(self):
        declaration='String predicate=serial?"a.identity_id=?":"a.erp_batch_id=?";'
        query='String sql="SELECT id FROM t WHERE "+predicate+" ORDER BY id";'
        for middle in ('/* '+declaration+' */',declaration+'predicate="changed=?";',declaration+declaration):
            with self.subTest(middle=middle),self.assertRaises(ValidationError):
                extract_selects('public void read(boolean serial){'+middle+query+'}')

    def test_inline_boolean_lock_literals_expand_both_complete_queries(self):
        source='public void read(boolean lock){jdbc.query("SELECT * FROM wm_work_order_tracking_policy WHERE tenant_id=? ORDER BY material_id"+(lock?" FOR UPDATE":""),mapper,tenant);}'
        queries=extract_selects(source)
        self.assertEqual(["lock_true","lock_false"],[q["variant"] for q in queries])
        self.assertEqual(["SELECT * FROM wm_work_order_tracking_policy WHERE tenant_id=? ORDER BY material_id FOR UPDATE","SELECT * FROM wm_work_order_tracking_policy WHERE tenant_id=? ORDER BY material_id"],[q["sql"] for q in queries])

    def test_inline_boolean_requires_a_declared_unmodified_boolean(self):
        query='jdbc.query("SELECT id FROM t"+(lock?" FOR UPDATE":""),mapper);'
        for prefix in ('public void read(){','public void read(boolean lock){lock=false;'):
            with self.subTest(prefix=prefix),self.assertRaises(ValidationError):extract_selects(prefix+query+'}')

    def test_unknown_dynamic_sql_fails_instead_of_silently_testing_a_prefix(self):
        with self.assertRaises(ValidationError):
            extract_selects('jdbc.queryForList("SELECT * FROM "+arbitraryTable,args);')

    def test_binder_ignores_quoted_question_marks_and_preserves_long_string_ids(self):
        query = "SELECT '?' text, `?` FROM t WHERE id=? AND evidence='a''?b'"
        bound = bind_sql(query, ['9007199254740993'])
        self.assertIn(sql_value('9007199254740993'), bound)
        self.assertIn("evidence='a''?b'", bound)
        self.assertNotEqual(bound, bind_sql(query, ['9007199254740992']))

    def test_binding_arity_drift_is_rejected(self):
        with self.assertRaises(ValidationError):
            bind_sql('SELECT ? FROM t WHERE id=?', [1])

if __name__ == '__main__':
    unittest.main()
