"""七列本地迁移的离线反例；不连接数据库、不签发业务来源。"""
import copy
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import importlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from test_stock_native_precision_migration import FixtureStore

TABLES=('wm_material_stock_tx','wm_material_stock','wm_material_receive_detail','wm_material_receive_line')


def fixture():
    base=FixtureStore();data=copy.deepcopy(base.metadata)
    for col in data['wm_material_stock_tx']['columns']:
        if col['name'] in ('tx_quantity','before_quantity','after_quantity'):
            col.update(precision=12,scale=2,columnType='decimal(12,2)')
    for table,primary,name in (('wm_material_receive_detail','detail_id','quantity'),
                              ('wm_material_receive_line','line_id','quantity_received')):
        item=copy.deepcopy(data['wm_material_stock'])
        item['primaryKey']=[primary]
        item['columns']=copy.deepcopy(item['columns'][:2])
        item['columns'][0]['name']=primary
        item['columns'][1]['name']=name
        item['columns'][1].update(precision=12,scale=2,columnType='double(12,2)')
        data[table]=item
    return data


class FakeStore:
    def __init__(self,m):
        self.m=m;self.metadata=fixture();self.rows=copy.deepcopy(FixtureStore().rows)
        self.rows['wm_material_receive_detail']=[{'detail_id':8,'quantity':None}]
        self.rows['wm_material_receive_line']=[{'line_id':9,'quantity_received':1.25}]
        self.events=[];self.fenced=False;self.fail_alter=False;self.fail_restore=False
        self.drift=False;self.fail_release=False;self.fail_fence=False;self.locked=False
    def validate_session(self):self.events.append('session')
    def identity(self):self.events.append('identity');return {'serverUuidSha256':'a'*64,'port':3306,'schema':'ry-erp'}
    def describe(self):return copy.deepcopy(self.metadata)
    def iter_rows(self,table):return iter(copy.deepcopy(self.rows[table]))
    def install_fence(self,journal):
        self.events.append('install');self.fenced=True
        if self.fail_fence:raise RuntimeError('私密数据库错误不能回显')
    def verify_fence(self):
        self.events.append('fence')
        if not self.fenced:raise self.m.LocalMigrationBlocked('WRITER_FENCE_UNPROVEN')
    def verify_restore(self,backup):
        self.events.append('restore')
        if self.fail_restore:raise self.m.LocalMigrationBlocked('BACKUP_RESTORE_MISMATCH')
        return {'restoredRows':{table:len(self.rows[table]) for table in TABLES},'status':'VERIFIED'}
    def lock(self):self.events.append('lock');self.locked=True
    def unlock(self):self.events.append('unlock');self.locked=False
    def alter(self,table,sql):
        self.events.append('alter:'+table)
        if self.fail_alter:raise RuntimeError('私密SQL和凭证')
        self.metadata=self.m.goal_metadata(self.metadata,(table,))
        for row in self.rows[table]:
            for col in self.m.NATIVE_COLUMNS[table]:
                value=row[col]
                if value is not None:row[col]=Decimal.from_float(value).quantize(Decimal('.0001')) if type(value)is float else value.quantize(Decimal('.0001'))
        self.locked=False
        if self.drift:self.rows['wm_material_stock'][0]['version']+=1
    def remove_fence(self,journal):
        self.events.append('remove')
        if self.fail_release:raise RuntimeError('私密触发器')
        self.fenced=False


class SevenNativeLocalTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('seven_native_precision_local'),'尚缺七列实际执行工具')
        self.m=importlib.import_module('seven_native_precision_local')
    def test_fixed_four_table_seven_column_contract(self):
        self.assertEqual(TABLES,self.m.STEP_ORDER)
        self.assertEqual(7,sum(map(len,self.m.NATIVE_COLUMNS.values())))
    def test_ddl_preserves_null_default_comment_and_storage(self):
        data=fixture();col=data['wm_material_receive_line']['columns'][1]
        col.update(default='1.25',comment="入库'量",nullable=False)
        sql=self.m.render_ddl(data,'wm_material_receive_line')
        self.assertIn("DECIMAL(18,4) NOT NULL DEFAULT 1.2500 COMMENT '入库''量'",sql)
        self.assertIn('ROW_FORMAT=DYNAMIC',sql);self.assertIn('ALGORITHM=COPY, LOCK=EXCLUSIVE',sql)
    def test_null_remains_null_and_unknown(self):
        store=FakeStore(self.m);snapshot=self.m.capture_snapshot(store)
        self.assertEqual(1,snapshot['tables']['wm_material_receive_detail']['nullCounts']['quantity'])
        self.assertEqual('NOT_PERFORMED',snapshot['sourceCertification'])
    def test_raw_nonquantity_flags_and_versions_participate_in_snapshot(self):
        store=FakeStore(self.m);before=self.m.capture_snapshot(store)
        store.rows['wm_material_stock'][0]['version']+=1
        self.assertNotEqual(before['rowsSha256'],self.m.capture_snapshot(store)['rowsSha256'])
    def test_native_float_never_text_or_round(self):
        for value in (.1,Decimal('0.10'),'0.10'):
            store=FakeStore(self.m);store.rows['wm_material_stock'][0]['stock_quantity']=value
            with self.subTest(valueType=type(value).__name__),self.assertRaises(self.m.LocalMigrationBlocked):self.m.capture_snapshot(store)
    def test_no_inbound_outbound_triggers_generated_or_partition(self):
        for key,value in (('foreignKeys',[['x']]),('inboundForeignKeys',[['x']]),('triggers',[['x']])):
            data=fixture();data['wm_material_stock'][key]=value
            with self.subTest(key=key),self.assertRaises(self.m.LocalMigrationBlocked):self.m.validate_metadata(data)
        data=fixture();data['wm_material_receive_line']['columns'][0]['generationExpression']='1+1'
        with self.assertRaises(self.m.LocalMigrationBlocked):self.m.validate_metadata(data)
        data=fixture();data['wm_material_stock']['tableDefinitionOptions']+=' PARTITION BY KEY(stock_id) PARTITIONS 2'
        with self.assertRaises(self.m.LocalMigrationBlocked):self.m.validate_metadata(data)
    def test_lossy_default_and_unknown_nullability_rejected(self):
        for attrs in ({'default':'0.1'},{'nullable':1},{'columnType':'float'},{'extra':'DEFAULT_GENERATED'}):
            data=fixture();data['wm_material_receive_detail']['columns'][1].update(attrs)
            with self.subTest(attrs=attrs),self.assertRaises(self.m.LocalMigrationBlocked):self.m.validate_metadata(data)
    def test_old_decimal_change_must_only_widen(self):
        data=fixture();col=data['wm_material_stock_tx']['columns'][1]
        col.update(type='decimal',precision=19,scale=4,columnType='decimal(19,4)')
        with self.assertRaises(self.m.LocalMigrationBlocked):self.m.validate_metadata(data)
    def test_completed_schema_noop_needs_no_fence(self):
        store=FakeStore(self.m)
        for table in TABLES:store.alter(table,self.m.render_ddl(store.metadata,table))
        store.events=[]
        with tempfile.TemporaryDirectory() as folder:
            record=self.m.execute_once(store,Path(folder))
        self.assertEqual('NOOP',record['status']);self.assertNotIn('install',store.events)
    def test_backup_roundtrip_preserves_raw_native_types(self):
        values=[None,0,1.25,Decimal('1.2500'),'中文',b'\x00\xff',date(2026,10,4),
                datetime(2026,10,4,12,1),timedelta(days=-1,seconds=1,microseconds=3)]
        for value in values:
            restored=self.m.decode_scalar(self.m.scalar(value))
            self.assertIs(type(value),type(restored));self.assertEqual(value,restored)
    def test_tampered_backup_blocks_before_restore_or_ddl(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'backup.json';path.write_text('{}',encoding='utf-8')
            with self.assertRaises(self.m.LocalMigrationBlocked):self.m.read_backup(path,'a'*64)
    def test_one_execute_fences_backups_restores_then_four_ddls(self):
        store=FakeStore(self.m)
        with tempfile.TemporaryDirectory() as folder:
            record=self.m.execute_once(store,Path(folder))
            events=self.m.read_journal(Path(folder)/'journal.jsonl')
        self.assertEqual('COMPLETED',record['status']);self.assertEqual(4,record['ddlCompleted'])
        self.assertEqual('NOT_PERFORMED',record['sourceCertification']);self.assertFalse(record['historicalPrecisionRecovered'])
        self.assertLess(store.events.index('install'),store.events.index('restore'))
        self.assertLess(store.events.index('restore'),store.events.index('alter:'+TABLES[0]))
        self.assertEqual(4,sum(x['event']=='DDL_ATTEMPT' for x in events))
        self.assertEqual(4,sum(x['event']=='DDL_VERIFIED' for x in events))
        self.assertFalse(store.fenced);self.assertEqual('COMPLETED',events[-1]['event'])
    def test_restore_failure_retains_fence_and_runs_no_alter(self):
        store=FakeStore(self.m);store.fail_restore=True
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(self.m.LocalMigrationBlocked):self.m.execute_once(store,Path(folder))
        self.assertTrue(store.fenced);self.assertFalse(any(x.startswith('alter:') for x in store.events))
    def test_install_failure_is_recorded_without_unsafe_cleanup(self):
        store=FakeStore(self.m);store.fail_fence=True
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(self.m.LocalMigrationBlocked):self.m.execute_once(store,Path(folder))
            events=self.m.read_journal(Path(folder)/'journal.jsonl')
        self.assertTrue(store.fenced);self.assertEqual('STOPPED_FENCE_RETAINED',events[-1]['event'])
    def test_ddl_uncertain_failure_never_retry_rollback_or_remove_fence(self):
        store=FakeStore(self.m);store.fail_alter=True
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(self.m.LocalMigrationBlocked) as caught:self.m.execute_once(store,Path(folder))
            raw=(Path(folder)/'journal.jsonl').read_text(encoding='utf-8')
        self.assertNotIn('私密',str(caught.exception));self.assertNotIn('私密',raw)
        self.assertTrue(caught.exception.ddl_attempted)
        self.assertEqual(1,sum(x.startswith('alter:') for x in store.events));self.assertTrue(store.fenced)
    def test_post_ddl_row_drift_halts_before_second_alter(self):
        store=FakeStore(self.m);store.drift=True
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(self.m.LocalMigrationBlocked) as caught:self.m.execute_once(store,Path(folder))
        self.assertTrue(caught.exception.ddl_attempted)
        self.assertEqual(1,sum(x.startswith('alter:') for x in store.events));self.assertTrue(store.fenced)
    def test_fence_removal_failure_is_not_completed(self):
        store=FakeStore(self.m);store.fail_release=True
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(self.m.LocalMigrationBlocked):self.m.execute_once(store,Path(folder))
            self.assertEqual('STOPPED_FENCE_RELEASE_UNPROVEN',self.m.read_journal(Path(folder)/'journal.jsonl')[-1]['event'])
    def test_journal_path_cannot_replay_existing_execution(self):
        store=FakeStore(self.m)
        with tempfile.TemporaryDirectory() as folder:
            self.m.execute_once(store,Path(folder))
            with self.assertRaises(self.m.LocalMigrationBlocked):self.m.execute_once(store,Path(folder))
    def test_journal_detects_mutation_and_truncation(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'journal.jsonl';journal=self.m.Journal(path)
            journal.append('FIRST');journal.append('SECOND');journal.close()
            data=path.read_text(encoding='utf-8');path.write_text(data.replace('SECOND','MUTANT'),encoding='utf-8')
            with self.assertRaises(self.m.LocalMigrationBlocked):self.m.read_journal(path)
    def replication_store(self,mutations=None):
        store=self.m.LocalMySQLStore.__new__(self.m.LocalMySQLStore)
        facts={'enabled':[(1,)],'consumers':[{'NAME':'global_instrumentation','ENABLED':'YES'},
            {'NAME':'thread_instrumentation','ENABLED':'YES'}],
            'lost':[('Performance_schema_thread_classes_lost','0'),('Performance_schema_thread_instances_lost','0')],
            'threads':[{'THREAD_ID':1,'NAME':'thread/sql/main','INSTRUMENTED':'YES'}],
            'systemUsers':[],'channels':[],'connections':[],'appliers':[],'coordinators':[],'workers':[],
            'slave':[],'plugins':[],'groupVars':[]}
        if mutations:facts.update(mutations)
        def query(sql,args=None):
            if sql=='SHOW GRANTS':return [('GRANT ALL PRIVILEGES ON *.* TO local_fixture',)]
            if sql=='SELECT @@GLOBAL.performance_schema':return facts['enabled']
            if sql.startswith('SHOW GLOBAL STATUS'):return facts['lost']
            raise AssertionError('未审查的观测')
        def dictionary(sql,args=None):
            mapping={'setup_consumers':'consumers','threads':'threads','PROCESSLIST':'systemUsers',
                'replication_connection_configuration':'channels','replication_connection_status':'connections',
                'replication_applier_status_by_coordinator':'coordinators','replication_applier_status_by_worker':'workers',
                'replication_applier_status':'appliers','PLUGINS':'plugins'}
            if sql=='SHOW SLAVE STATUS':return facts['slave']
            if sql=="SHOW GLOBAL VARIABLES LIKE 'group_replication%'":return facts['groupVars']
            for key,label in mapping.items():
                if key in sql:return facts[label]
            raise AssertionError('未审查的原生观测')
        store.query=query;store.dictionary_query=dictionary;return store
    def test_mysql57_thread_classes_not_in_setup_instruments(self):
        store=self.replication_store()
        self.assertEqual(0,store.replication_appliers())
        self.assertEqual('COMPLETE',store.replication_inventory['status'])
    def test_replication_visibility_gaps_are_never_guessed_zero(self):
        cases=[{'enabled':[(0,)]},{'consumers':[]},{'lost':[]},
            {'lost':[('Performance_schema_thread_classes_lost','1'),('Performance_schema_thread_instances_lost','0')]},
            {'systemUsers':[{'ID':1,'USER':'system user','COMMAND':'Connect'}]},
            {'threads':[{'THREAD_ID':1,'NAME':'thread/sql/slave_worker','INSTRUMENTED':'YES'}]},
            {'threads':[{'THREAD_ID':1,'NAME':'thread/sql/main','INSTRUMENTED':'NO'}]},
            {'channels':[{'CHANNEL_NAME':'hidden'}]}, {'appliers':[{'CHANNEL_NAME':'hidden','SERVICE_STATE':'OFF'}]},
            {'plugins':[{'PLUGIN_NAME':'group_replication','PLUGIN_STATUS':'ACTIVE'}]}]
        for changes in cases:
            with self.subTest(changes=changes),self.assertRaises(self.m.MigrationBlocked):self.replication_store(changes).replication_appliers()
    def test_batched_physical_and_foreign_closure_remains_complete(self):
        expected={'ry@002derp/'+table for table in TABLES}
        def store_with(foreign=None,physical=None,grants=None):
            store=self.m.LocalMySQLStore.__new__(self.m.LocalMySQLStore);store.calls=[]
            def query(sql,args=None):
                store.calls.append(sql)
                if sql=='SHOW GRANTS':return grants if grants is not None else [('GRANT ALL PRIVILEGES ON *.* TO local_fixture',)]
                if 'KEY_COLUMN_USAGE' in sql:return [] if foreign is None else foreign
                if 'INNODB_SYS_TABLES' in sql:return [(name,'Dynamic','Single',0,16384,'Single',0) for name in sorted(expected)] if physical is None else physical
                if sql=='SELECT @@GLOBAL.innodb_file_per_table':return [(1,)]
                raise AssertionError('未审查查询')
            store.query=query;return store
        store=store_with();inventory=store.storage_and_dependency_inventory()
        self.assertEqual(set(TABLES),set(inventory))
        self.assertEqual(1,sum('KEY_COLUMN_USAGE' in sql for sql in store.calls))
        self.assertEqual(1,sum('INNODB_SYS_TABLES' in sql for sql in store.calls))
        for attrs in ({'foreign':[('unexpected',)]},{'physical':[]},
            {'physical':[(name,'Dynamic','Single',0,16384,'Single',0) for name in sorted(expected)[:-1]]},
            {'grants':[('GRANT ALL PRIVILEGES ON `ry-erp`.* TO local_fixture',)]}):
            with self.subTest(attrs=attrs),self.assertRaises(self.m.LocalMigrationBlocked):store_with(**attrs).storage_and_dependency_inventory()
    def test_row_format_keeps_mysql57_position_before_table_comment(self):
        data=fixture();table='wm_material_stock_tx'
        prefix='ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci'
        comment="COMMENT='流水 ROW_FORMAT=REDUNDANT 文本'"
        data[table]['tableDefinitionOptions']=prefix+' '+comment
        expected=prefix+' ROW_FORMAT=DYNAMIC '+comment
        self.assertEqual(expected,self.m.goal_metadata(data,(table,))[table]['tableDefinitionOptions'])
        data[table]['tableDefinitionOptions']=expected;data[table]['tableOptions'][2]='row_format=DYNAMIC'
        self.assertEqual(expected,self.m.goal_metadata(data,(table,))[table]['tableDefinitionOptions'])
    def test_driver_none_must_match_native_server_null_bitmap(self):
        class Cursor:
            description=[('id',),('occurred_at',),('__nexus_np7_null_0',),('__nexus_np7_null_1',)]
            def __init__(self,row):self.row=row
            def execute(self,sql):self.sql=sql
            def __iter__(self):return iter([self.row])
            def close(self):pass
        class Connection:
            def __init__(self,row):self.row=row
            def cursor(self,prepared=False):
                if prepared is not True:raise AssertionError('不允许文本读取原数量')
                return Cursor(self.row)
        for actual_null in (False,True):
            store=self.m.LocalMySQLStore.__new__(self.m.LocalMySQLStore)
            store.connection=Connection((1,None,0,int(actual_null)))
            store.query=lambda sql,args=None:[('id',),('occurred_at',)]
            if actual_null:
                self.assertEqual([{'id':1,'occurred_at':None}],list(store.iter_named_rows('temporary','id')))
                store.query=lambda *args:(_ for _ in ()).throw(AssertionError('TEMPORARY表不进入I_S.COLUMNS'))
                self.assertEqual([{'id':1,'occurred_at':None}],list(store.iter_named_rows('temporary','id',columns=['id','occurred_at'])))
            else:
                with self.assertRaises(self.m.LocalMigrationBlocked) as caught:list(store.iter_named_rows('temporary','id'))
                self.assertEqual('NATIVE_NULL_IDENTITY_LOST',caught.exception.code)
    def test_target_and_fence_names_cannot_inject_sql(self):
        with self.assertRaises(self.m.LocalMigrationBlocked):self.m.fence_specs('nonce` OR 1=1')
        with self.assertRaises(self.m.LocalMigrationBlocked):self.m.render_ddl(fixture(),'unknown`')

if __name__=='__main__':unittest.main()
