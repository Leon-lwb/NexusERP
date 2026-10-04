"""本地追加DDL执行器离线测试；不连接实际数据库，不授予业务写入资格。"""
import copy
import importlib
import importlib.util
import unittest
from unittest.mock import patch
from test_procurement_schema_contract import fresh_metadata

NEW_TABLES=('ry-erp.pu_certified_purchase_command','ry-erp.pu_certified_purchase_order_terms',
 'ry-erp.pu_certified_purchase_order_terms_confirmation','ry-erp.pu_certified_purchase_order_operation',
 'ry-erp.wm_certified_iqc_evidence','ry-erp.wm_certified_receipt_posting','ry-erp.wm_receipt_iqc_birth','ry-mes.qc_receipt_iqc_birth')

def before_metadata():
    metadata=fresh_metadata()
    for name in NEW_TABLES:metadata.pop(name)
    for col in ('certified_version','certified_terms_id'):metadata['ry-erp.pu_purchase_order']['columns'].pop(col)
    entry=metadata['ry-erp.pu_requirement_allocation_event'];entry['columns'].pop('event_sequence')
    entry['indexes']=[part for part in entry['indexes'] if part[0]!='uk_prae_tenant_allocation_sequence']
    return metadata

class AdditiveMigrationTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('procurement_additive_migration'),'缺少固定范围追加DDL执行器')
        self.m=importlib.import_module('procurement_additive_migration')
    def test_twelve_frozen_operations_create_eight_tables_without_dml(self):
        operations=self.m.load_operations()
        self.assertEqual(12,len(operations))
        self.assertEqual(8,sum(op.kind=='CREATE' for op in operations))
        self.assertEqual(1,sum(op.schema=='ry-mes' for op in operations))
        self.assertTrue(all(op.sql.lstrip().startswith(('CREATE TABLE','ALTER TABLE')) for op in operations))
        self.assertEqual(operations,self.m.plan_operations(before_metadata()))
    def test_completed_schema_is_noop_not_a_request_to_replay_ddl(self):
        self.assertEqual([],self.m.plan_operations(fresh_metadata()))
    def test_partial_schema_is_not_silently_replayed(self):
        metadata=before_metadata();metadata[NEW_TABLES[0]]=fresh_metadata()[NEW_TABLES[0]]
        with self.assertRaises(self.m.AdditiveMigrationBlocked):self.m.plan_operations(metadata)
    def test_unknown_base_definition_is_rejected_before_any_ddl(self):
        for mutate in (lambda data:data.pop('ry-erp.pu_purchase_order'),
                       lambda data:data['ry-erp.pu_requirement_allocation_event'].update(engine='MyISAM'),
                       lambda data:data['ry-mes.qc_iqc']['columns']['quantity'].update(type='double')):
            metadata=before_metadata();mutate(metadata)
            with self.assertRaises(self.m.AdditiveMigrationBlocked):self.m.plan_operations(metadata)
    def test_sql_splitter_keeps_quoted_semicolons_and_strips_only_real_comments(self):
        sql="-- 注释\nCREATE TABLE t(c varchar(3) COMMENT '甲;乙--丙'); ALTER TABLE t ADD q int;"
        self.assertEqual(2,len(self.m.split_sql(sql)))
        self.assertIn("'甲;乙--丙'",self.m.split_sql(sql)[0])
        for bad in ("CREATE TABLE x(c int) /*执行注释*/;","CREATE TABLE x(c int) COMMENT '未闭合;"):
            with self.assertRaises(self.m.AdditiveMigrationBlocked):self.m.split_sql(bad)
    def test_source_hash_change_cannot_run_an_edited_migration(self):
        with patch.object(self.m,'read_source',return_value=b'CREATE TABLE injected(c int);'):
            with self.assertRaises(self.m.AdditiveMigrationBlocked):self.m.load_operations()
    def test_execute_records_each_attempt_before_ddl_and_verifies_after(self):
        operations=self.m.load_operations();calls=[];outer=self
        class Store:
            def initial(self):calls.append('initial');return before_metadata()
            def execute(self,op):calls.append(('execute',op.ordinal))
            def verify_step(self,op):calls.append(('verify',op.ordinal))
            def verify_final(self):calls.append('final');return {'schemaMatched':True,'sourceCertification':'NOT_PERFORMED'}
        def journal(event):calls.append(('journal',event['event'],event.get('ordinal')))
        result=self.m.execute_operations(Store(),operations,journal)
        self.assertEqual(12,result['ddlExecuted']);self.assertEqual('NOT_PERFORMED',result['sourceCertification'])
        for op in operations:
            self.assertLess(calls.index(('journal','DDL_ATTEMPT',op.ordinal)),calls.index(('execute',op.ordinal)))
            self.assertLess(calls.index(('execute',op.ordinal)),calls.index(('verify',op.ordinal)))
        self.assertEqual('final',calls[-2]);self.assertEqual('COMPLETED',calls[-1][1])
    def test_failure_is_uncertain_and_never_rolls_back_or_retries_committed_ddl(self):
        calls=[];operations=self.m.load_operations()
        class Store:
            def initial(self):return before_metadata()
            def execute(self,op):
                calls.append(op.ordinal)
                if op.ordinal==2:raise RuntimeError('secret-password private-backup')
            def verify_step(self,op):pass
            def verify_final(self):raise AssertionError('失败后不得做最终成功结论')
        events=[]
        with self.assertRaises(self.m.AdditiveMigrationBlocked) as caught:
            self.m.execute_operations(Store(),operations,events.append)
        self.assertEqual([1,2],calls)
        self.assertEqual('FAILED_UNCERTAIN',events[-1]['event'])
        self.assertNotIn('secret-password',str(caught.exception))
        self.assertNotIn('private-backup',str(events))
    def test_journal_failure_before_attempt_prevents_any_ddl(self):
        operations=self.m.load_operations();calls=[]
        class Store:
            def initial(self):return before_metadata()
            def execute(self,op):calls.append(op)
        def failed(event):raise OSError('private-path')
        with self.assertRaises(self.m.AdditiveMigrationBlocked):self.m.execute_operations(Store(),operations,failed)
        self.assertEqual([],calls)
    def test_arbitrary_or_reordered_operation_is_rejected_before_execution(self):
        operations=self.m.load_operations()
        for chosen in (operations[::-1],operations[:-1],[]):
            with self.subTest(count=len(chosen)),self.assertRaises(self.m.AdditiveMigrationBlocked):
                self.m.execute_operations(object(),chosen,lambda event:None)

class MysqlTargetBoundaryTests(unittest.TestCase):
    """真实驱动边界用离线连接重放；命名锁绝不是应用DML围栏。"""
    def setUp(self):self.m=importlib.import_module('procurement_additive_migration')
    def connection(self,**changes):
        outer=self
        class Cursor:
            def __enter__(self):return self
            def __exit__(self,*a):return False
            def execute(self,sql,params=None):self.sql=sql
            def fetchone(self):
                if 'IS_USED_LOCK' in self.sql:return (changes.get('lock_owner',71),71)
                return ('5.7.20-log',3306,'native-test-uuid',0,'ry-vue',0)
            def fetchall(self):return [(name,) for name in outer.m.SCHEMAS.values()]
        class Connection:
            host=changes.get('host','127.0.0.1');port=3306
            server_status=changes.get('server_status',2)
            def get_autocommit(self):return changes.get('autocommit',True)
            def cursor(self):return Cursor()
        return Connection()
    def test_only_fresh_owned_loopback_ddl_session_is_accepted(self):
        store=self.m.MysqlAdditiveStore(self.connection(),self.m.sha(b'native-test-uuid'))
        store.target()
        for changes in ({'host':'remote.example'},{'autocommit':False},{'server_status':3},{'lock_owner':72},{'lock_owner':None}):
            with self.subTest(changes=changes),self.assertRaises(self.m.AdditiveMigrationBlocked):
                self.m.MysqlAdditiveStore(self.connection(**changes),self.m.sha(b'native-test-uuid')).target()

    def test_new_nullable_certification_columns_are_preserved_at_each_step(self):
        queries=[]
        class Cursor:
            def __enter__(self):return self
            def __exit__(self,*a):return False
            def execute(self,sql,params=None):queries.append(sql);self.sql=sql
            def fetchone(self):return (1 if 'certified_version' in self.sql else 0,)
        class Connection:
            def cursor(self):return Cursor()
        store=self.m.MysqlAdditiveStore(Connection(),self.m.sha(b'native-test-uuid'))
        store.original_columns={key:('native_original_column',) for key in self.m.OLD_TABLES}
        store.original_rows={key:{'rows':1,'sha256':'a'*64} for key in self.m.OLD_TABLES}
        store.row_hash=lambda key,columns,metadata:store.original_rows[key]
        metadata=before_metadata()
        metadata['ry-erp.pu_purchase_order']['columns']['certified_version']=fresh_metadata()['ry-erp.pu_purchase_order']['columns']['certified_version']
        with self.assertRaises(self.m.AdditiveMigrationBlocked):store.verify_original(metadata)
        self.assertTrue(any('certified_version' in sql for sql in queries))

if __name__=='__main__':unittest.main()
