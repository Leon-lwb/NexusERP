"""Keep the SQL smoke test attached to the real MES reader query."""
import unittest
from harness import ValidationError, sql_value
from extended_cases import extract_forward_query, bind_forward_query

QUERY = ("SELECT DISTINCT net.identity_id FROM (SELECT c.identity_id "
         "FROM lt_sn_consumption c WHERE c.tenant_id=? AND c.identity_id>? "
         "AND JSON_UNQUOTE(JSON_EXTRACT(a.evidence_json,'$.source.identityId'))=? "
         "GROUP BY c.identity_id HAVING SUM(c.quantity)>0) net ORDER BY net.identity_id LIMIT ?")
SOURCE = ('public TracePage<Long> affectedByIdentity(String tenant,Long component,long after,int limit){\n'
          'var rows=jdbc.queryForList("' + QUERY + '",Long.class,tenant,after,component.toString(),limit+1);\n'
          'return result;\n}\n@Override public void other(){jdbc.queryForList("SELECT 1");}')

class QueryContractTests(unittest.TestCase):
    def test_extracts_the_identity_forward_query_instead_of_an_adjacent_method(self):
        self.assertEqual(QUERY, extract_forward_query(SOURCE))

    def test_rejects_missing_or_ambiguous_reader_query_instead_of_running_stale_sql(self):
        for text in ('public void other(){}', SOURCE.replace('affectedByIdentity', 'renamed'),
                     SOURCE.replace('return result;', 'jdbc.queryForList("SELECT 2"); return result;')):
            with self.subTest(text=text), self.assertRaises(ValidationError):
                extract_forward_query(text)

    def test_binds_adjacent_long_identity_strings_exactly_and_applies_extra_paging_row(self):
        exact = bind_forward_query(QUERY, '901001', '9007199254740993', 9007199254741001, 5)
        adjacent = bind_forward_query(QUERY, '901001', '9007199254740992', 9007199254741001, 5)
        self.assertIn(sql_value('9007199254740993'), exact)
        self.assertNotEqual(exact, adjacent)
        self.assertIn('identity_id>9007199254741001', exact)
        self.assertTrue(exact.endswith('LIMIT 6;'))

    def test_rejects_query_parameter_drift(self):
        with self.assertRaises(ValidationError):
            bind_forward_query(QUERY.replace('LIMIT ?', 'LIMIT 5'), '901001', '9007199254740993', 0, 5)

if __name__ == '__main__':
    unittest.main()
