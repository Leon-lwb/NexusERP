"""纯恢复安全测试，不连接数据库或读取凭据。"""
import importlib.util,unittest
from pathlib import Path
class RepairSafetyTest(unittest.TestCase):
    def guard(self):
        file=Path(__file__).with_name('repair-demo-execution.py')
        self.assertTrue(file.exists(),'Missing guarded repair implementation')
        s=importlib.util.spec_from_file_location('demo_repair',file)
        m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m.validate_recovery
    def facts(self):
        return {'run':{'id':'12','tenant':'000000','status':'APPROVED','version':7,'flowId':'34'},
            'flow':{'id':'34','tenant':'000000','status':'FAILED','runId':'12',
                'flowKey':'system_delivery_risk_execution_v1_000000','nodeTypeError':1,'succeeded':0,'failed':0},
            'actions':[{'id':'56','type':'CREATE_PURCHASE_REQUISITION_DRAFT','approval':'APPROVED',
                'execution':'NOT_STARTED','resultBizId':None,'resultJson':None}],
            'nodeRows':0,'businessRows':0,'otherExecutions':0}
    def test_only_confirmed_pre_action_schema_failure_may_be_marked_stale(self):
        self.guard()(self.facts(),'12')
    def test_every_missing_or_unsafe_boundary_fails_closed(self):
        validate=self.guard()
        paths=[('run','status','EXECUTING'),('run','tenant','910000'),('run','id','13'),
            ('run','flowId','35'),('flow','status','RUNNING'),('flow','runId','13'),
            ('flow','tenant','910000'),('flow','flowKey','user_flow'),('flow','nodeTypeError',0),
            ('flow','succeeded',1),('flow','failed',1),('actions',None,[])]
        for group,key,value in paths:
            f=self.facts()
            if key is None:f[group]=value
            else:f[group][key]=value
            with self.assertRaises(ValueError,msg=str((group,key))):validate(f,'12')
        for key in ['nodeRows','businessRows','otherExecutions']:
            for value in [1,False,'0']:
                f=self.facts();f[key]=value
                with self.assertRaises(ValueError,msg=key):validate(f,'12')
    def test_any_action_progress_results_or_unreviewed_type_are_rejected(self):
        validate=self.guard()
        for key,value in [('execution','EXECUTING'),('execution','SUCCEEDED'),('resultBizId','99'),
            ('resultJson','{}'),('approval','PROPOSED'),('type','SEND_EXPEDITE_NOTICE')]:
            f=self.facts();f['actions'][0][key]=value
            with self.assertRaises(ValueError,msg=key):validate(f,'12')
    def test_identifier_injection_null_or_boolean_cannot_select_a_recovery_target(self):
        validate=self.guard()
        for id in ['0','12; DROP TABLE',None,True,'-1']:
            with self.assertRaises(ValueError):validate(self.facts(),id)
if __name__=='__main__':unittest.main()
