import importlib.util,unittest,copy
from pathlib import Path
class BackfillGuardTest(unittest.TestCase):
    def guard(self):
        f=Path(__file__).with_name('backfill-requisition-metadata.py');self.assertTrue(f.exists(),'Missing guarded backfill')
        s=importlib.util.spec_from_file_location('backfill',f);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m.validate
    def facts(self):
        return {'header':{'id':'11','tenant':'000000','status':'DRAFT','source':'AI_DECISION','version':0,'applicant':'1','creator':'1','run':'22','action':'33','key':'k'},
            'action':{'id':'33','run':'22','approval':'APPROVED','execution':'SUCCEEDED','approvedBy':'1','resultId':'11','key':'k'},
            'user':{'id':'1','tenant':'000000','status':'0'},'runStatus':'SUCCEEDED',
            'lines':[{'id':'44','tenant':'000000','material':'55','qty':'19.0000','gap':'19','date':'2026-10-03','source':'AI_DECISION','sourceId':'22','approved':None}],
            'intent':[{'material':'55','qty':'19','gap':'19','date':'2026-10-03'}],
            'materials':[{'id':'55','tenant':'000000','status':1,'code':'M-55','name':'Material'}]}
    def test_only_existing_approved_created_draft_with_unchanged_numeric_intent_is_eligible(self):self.guard()(self.facts(),'11',0)
    def test_state_identity_provenance_and_quantity_changes_are_rejected(self):
        v=self.guard()
        for group,key,value in [('header','status','APPROVING'),('header','version',1),('header','tenant','910000'),('header','key','other'),('header','creator','2'),('action','execution','EXECUTING'),('action','resultId','99'),('user','status','1')]:
            f=self.facts();f[group][key]=value
            with self.assertRaises(ValueError,msg=str((group,key))):v(f,'11',0)
        for key,value in [('qty','20'),('gap','18'),('approved','19'),('sourceId','23')]:
            f=self.facts();f['lines'][0][key]=value
            with self.assertRaises(ValueError,msg=key):v(f,'11',0)
        f=self.facts();f['materials'][0]['tenant']='910000'
        with self.assertRaises(ValueError):v(f,'11',0)
    def test_invalid_ids_and_versions_are_not_executable_sql(self):
        for id,version in [('11;DROP',0),(None,0),('11',True),('11',-1)]:
            with self.assertRaises(ValueError):self.guard()(self.facts(),id,version)
if __name__=='__main__':unittest.main()
