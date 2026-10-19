"""Closed document expansion must stop on changed SQL instead of smoking a safe prefix."""
import unittest
from harness import ROOT,ValidationError
from document_sql import DOC,closed_specs,document_statements,compact_java

class DocumentSqlTests(unittest.TestCase):
    def setUp(self):
        self.documents=(ROOT/DOC).read_text(encoding="utf-8-sig")
        self.guard=(ROOT/"ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/WmBarcodeIdentityGuard.java").read_text(encoding="utf-8-sig")
    def test_unknown_document_identifier_expression_is_rejected(self):
        changed=self.documents.replace('"wm_transfer"','request.table()',1)
        with self.assertRaises(ValidationError):closed_specs(changed)
    def test_an_extra_owner_guard_predicate_is_not_silently_omitted(self):
        changed=self.guard.replace('" FOR UPDATE"','" AND unexpected_assertion=? FOR UPDATE"',1)
        with self.assertRaises(ValidationError):document_statements(changed,self.documents,"document_binding")
    def test_source_id_filter_changes_must_be_reviewed(self):
        changed=self.documents.replace('args.add(request.sourceDocId());','args.add(request.rawCode());',1)
        with self.assertRaises(ValidationError):document_statements(changed,changed,"document_resolver")
    def test_java_whitespace_normalization_preserves_sql_literal_spaces(self):
        self.assertNotEqual(compact_java('"SELECT "+spec.id()'),compact_java('"SELECT"+spec.id()'))
        changed=self.guard.replace('"SELECT "+spec.id()','"SELECT"+spec.id()',1)
        with self.assertRaises(ValidationError):document_statements(changed,self.documents,"document_binding")

if __name__=="__main__":unittest.main()
