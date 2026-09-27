"""SQL 校验为纯函数；这些测试不连接 MySQL 或读取凭据。"""
import unittest
from pathlib import Path
class ControlSqlBoundaryTest(unittest.TestCase):
    def validator(self):
        import importlib.util
        file=Path(__file__).with_name('control-db.py')
        spec=importlib.util.spec_from_file_location('control_db_boundary',file)
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        return module.validate_sql
    def test_known_control_select_and_explicit_mutation_are_allowed(self):
        validate=self.validator()
        for sql in ['SELECT COUNT(*) FROM `nexus_ai_decision_control_20260927_sys`.`ai_decision_run`',
                    'UPDATE `nexus_ai_decision_control_20260927_erp`.`pu_purchase_requisition` SET version=version+1 WHERE requisition_id=12']:
            validate(sql)
    def test_other_databases_unquoted_refs_batched_commands_and_root_capabilities_are_denied(self):
        validate=self.validator()
        for sql in [
          'UPDATE `nexus_ai_decision_demo_erp`.`prd_work_order` SET planned_qty=1',
          'SELECT * FROM nexus_ai_decision_control_20260927_sys.ai_decision_run',
          'SELECT * FROM `nexus_ai_decision_control_20260927_sys`.`ai_decision_run` JOIN nexus_ai_decision_demo_sys.sys_user ON 1=1',
          'SELECT * FROM `nexus_ai_decision_control_20260927_sys`.`ai_decision_run`, nexus_ai_decision_demo_sys.sys_user',
          'SELECT * FROM `nexus_ai_decision_control_20260927_sys`.`ai_decision_run`; SELECT 1',
          "SELECT * FROM `nexus_ai_decision_control_20260927_sys`.`ai_decision_run` INTO OUTFILE 'out'",
          'DROP TABLE `nexus_ai_decision_control_20260927_sys`.`ai_decision_run']:
            with self.assertRaises(RuntimeError,msg=sql):validate(sql)
if __name__=='__main__':unittest.main()
