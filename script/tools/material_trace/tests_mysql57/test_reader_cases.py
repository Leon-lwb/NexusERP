"""Parameter values must follow all actual tenant predicates and remain reproducible."""
import unittest
from types import SimpleNamespace
from harness import ROOT, SchemaSnapshot, ValidationError, sql_value
from reader_cases import smoke_parameters, batch_recall_explain, statements_for, MES_READER, TENANT, SPECS
from java_sql import bind_sql

class ReaderParameterTests(unittest.TestCase):
    def test_repeated_tenants_in_unallocated_scope_are_strings_not_numeric_zero(self):
        sql="SELECT w.consumption_id FROM lt_wo_consumption w WHERE EXISTS(SELECT 1 FROM lt_sn_consumption c WHERE c.tenant_id=?) AND w.tenant_id=? AND w.consumption_id>? AND EXISTS(SELECT 1 FROM lt_lot_allocation a WHERE a.tenant_id=? AND a.erp_batch_id=?) ORDER BY w.consumption_id LIMIT ?"
        params=smoke_parameters(sql)
        self.assertEqual([TENANT,TENANT,0,TENANT,9007199254740993,2],[p['value'] for p in params])
        self.assertEqual([1,2,3,4,5,6],[p['ordinal'] for p in params])
        self.assertEqual(['tenant_id','tenant_id','cursor:consumption_id','tenant_id','erp_batch_id','limit'],[p['role'] for p in params])

    def test_identity_json_uses_exact_string_and_null_batch_stays_null(self):
        sql="SELECT id FROM t WHERE tenant_id=? AND JSON_UNQUOTE(JSON_EXTRACT(a.evidence_json,'$.source.identityId'))=? AND (batch_id=? OR (batch_id IS NULL AND ? IS NULL)) LIMIT ?"
        params=smoke_parameters(sql)
        self.assertEqual('9007199254740993',params[1]['value'])
        self.assertIsNone(params[3]['value'])
        self.assertIn(sql_value('9007199254740993'),bind_sql(sql,[p['value'] for p in params]))

    def test_unknown_parameter_context_fails_instead_of_guessing_numeric_zero(self):
        with self.assertRaises(ValidationError):smoke_parameters('SELECT ? FROM t')

    def test_event_identity_and_types_are_bound_as_text(self):
        values=[p['value'] for p in smoke_parameters('SELECT * FROM t WHERE tenant_id=? AND event_id=? AND event_type=? AND aggregate_type=?')]
        self.assertTrue(all(isinstance(v,str) for v in values))

    def test_canonical_identity_find_covers_its_actual_sql_without_unrelated_writes(self):
        path="ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpSerialIdentityService.java"
        def require(ok,msg):
            if not ok:raise AssertionError(msg)
        c=SimpleNamespace(snapshot=SimpleNamespace(text=lambda name:(ROOT/name).read_text(encoding="utf-8")),require=require)
        queries=statements_for(c,path,"canonical_find")
        self.assertEqual(1,len(queries));self.assertIn("SELECT sn_id,sn_code,material_id,batch_id,batch_code",queries[0]["sql"])
        self.assertNotIn("FOR UPDATE",queries[0]["sql"])
        actual=(ROOT/path).read_text(encoding="utf-8").splitlines()[queries[0]["line"]-1]
        self.assertIn("sn_id,sn_code,material_id,batch_id,batch_code",actual)

    def test_adoption_both_lock_branches_are_frozen_and_executed_in_the_matrix(self):
        path="ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/PrdWorkOrderPolicyAdoptionService.java"
        self.assertIn(("erp",path,False),SPECS,"Adoption must not silently disappear from final SQL verification")
        snapshot=SchemaSnapshot()
        self.assertIn(path,snapshot.hashes,"Adoption implementation must participate in source drift checks")
        def require(ok,msg):
            if not ok:raise AssertionError(msg)
        queries=statements_for(SimpleNamespace(snapshot=snapshot,require=require),path)
        self.assertEqual(3,len(queries))
        approvals=[q for q in queries if "FROM wm_work_order_policy_adoption" in q["sql"]]
        self.assertEqual({"lock_true","lock_false"},{q["variant"] for q in approvals})
        for query in queries:
            bindings=smoke_parameters(query["sql"])
            self.assertEqual(TENANT,bindings[0]["value"])
            self.assertEqual(2,len(bindings))

    def test_barcode_document_sql_covers_all_closed_targets_and_id_branches(self):
        prefix="ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/"
        self.assertIn(("erp",prefix+"WmBarcodeIdentityGuard.java","document_binding"),SPECS)
        self.assertIn(("erp",prefix+"ErpDocumentCodeResolver.java","document_resolver"),SPECS)
        snapshot=SchemaSnapshot()
        self.assertIn(prefix+"WmBarcodeIdentityGuard.java",snapshot.hashes)
        def require(ok,msg):
            if not ok:raise AssertionError(msg)
        c=SimpleNamespace(snapshot=snapshot,require=require)
        guard=statements_for(c,prefix+"WmBarcodeIdentityGuard.java","document_binding")
        documents=[q for q in guard if q["variant"].startswith("document_")]
        self.assertEqual(6,len(documents));self.assertTrue(all(q["sql"].endswith("FOR UPDATE") for q in documents))
        resolver=statements_for(c,prefix+"ErpDocumentCodeResolver.java","document_resolver")
        self.assertEqual(19,len(resolver))
        direct=[q for q in resolver if q["variant"].endswith(("_id_false","_id_true"))]
        self.assertEqual(12,len(direct));self.assertTrue(all(q["sql"].endswith("LIMIT 101") for q in direct))
        self.assertEqual(6,len([q for q in resolver if q["variant"].endswith("_carrier")]))
        self.assertEqual({2,3},{len(smoke_parameters(q["sql"])) for q in direct})
        for query in direct:
            values=smoke_parameters(query["sql"])
            self.assertEqual(TENANT,values[0]["value"])
            self.assertIsInstance(values[1]["value"],str,"Raw document code must remain a string")

    def test_detail_facts_actual_queries_and_batch_entry_are_in_final_freeze(self):
        path="ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesProductionDetailFactsService.java"
        self.assertIn(("mes",path,False),SPECS,"The two actual PC detail fields must not bypass MySQL query verification")
        snapshot=SchemaSnapshot()
        required=[path,"script/tools/material_trace/examples/schema-handoff.json",
            "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmBatchWriteGuard.java",
            "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBatchServiceImpl.java"]
        required += ["ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/"+suffix+".java" for suffix in
            ("domain/vo/ProFlowCardVo","domain/vo/ProFlowCardStepVo","domain/vo/ProWorkOrderVo","service/impl/ProFlowCardServiceImpl","service/impl/ProWorkOrderServiceImpl")]
        for source in required:
            with self.subTest(source=source):self.assertIn(source,snapshot.hashes)
        def require(ok,msg):
            if not ok:raise AssertionError(msg)
        queries=statements_for(SimpleNamespace(snapshot=snapshot,require=require),path)
        self.assertGreater(len(queries),0)
        for query in queries:
            values=smoke_parameters(query["sql"])
            self.assertEqual(TENANT,values[0]["value"])
            self.assertTrue(query["sql"].startswith("SELECT "))
            self.assertFalse(query["sql"].rstrip().endswith("+"))

    def test_current_batch_explain_binds_both_explicit_tenants(self):
        queries=[]
        def require(ok,msg):
            if not ok:raise AssertionError(msg)
        c=SimpleNamespace(snapshot=SimpleNamespace(text=lambda path:(ROOT/path).read_text(encoding='utf-8')),dbs={'legacy_mes':'synthetic_mes'},require=require,query=lambda db,sql:queries.append(sql) or SimpleNamespace(rows=[['SYNTH_PLAN']]))
        result=batch_recall_explain(c)
        self.assertEqual(1,len(queries))
        self.assertEqual(2,queries[0].count(sql_value(TENANT)))
        self.assertTrue(result['explicit_allocation_tenant_predicate'])
        self.assertEqual(5,len(result['parameter_bindings']))

if __name__=='__main__':unittest.main()
