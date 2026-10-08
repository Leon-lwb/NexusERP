"""任务B：原生库存精度迁移离线守卫测试，不接触MySQL或共享Maven。"""
import copy
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import hashlib
import importlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

ROOT = Path(__file__).resolve().parents[4]
NOW = datetime(2026, 10, 3, 8, tzinfo=timezone.utc)
SCOPE = [{'table':'wm_material_stock_tx', 'columns':['tx_quantity','before_quantity','after_quantity'], 'targetType':'DECIMAL(18,4)'},
         {'table':'wm_material_stock', 'columns':['stock_quantity','reserved_quantity'], 'targetType':'DECIMAL(18,4)'}]


def digest(data):
    return hashlib.sha256(data).hexdigest()


class FixtureStore:
    """私有内存夹具，只记录执行边界，不能当成MySQL运行验收。"""
    def __init__(self):
        self.identity = {'schema': 'ry-erp', 'host': '127.0.0.1', 'port': 3306,
                         'serverUuid': '11111111-1111-1111-1111-111111111111', 'serverId': 71,
                         'serverHostnameSha256': digest(b'fixture-server'), 'version': '5.7.44',
                         'readOnly': 1, 'superReadOnly': 0, 'replicationApplierCount': 0, 'eventScheduler': 'OFF',
                         'replicationInventory':{'status':'COMPLETE','activeApplierCount':0,'inventorySha256':digest(b'fixture-replication')}}
        def column(name, kind='varchar', precision=None, scale=None):
            return {'name': name, 'type': kind, 'precision': precision, 'scale': scale,
                    'nullable': True, 'default': None, 'comment': '原列注释', 'extra': '',
                    'columnType': kind if kind != 'decimal' else 'decimal(18,4)', 'generationExpression':''}
        self.metadata = {
            'wm_material_stock': {'engine': 'InnoDB', 'primaryKey': ['stock_id'], 'indexes': [],
                'columns': [column('stock_id', 'bigint'), column('stock_quantity', 'double', 22),
                            column('reserved_quantity', 'double', 22), column('quantity_origin'),
                            column('quantity_contract_version', 'int'), column('base_uom_id'),
                            column('quantity_source_head_tx_id', 'bigint'), column('version', 'int'),
                            column('stock_quantity_exact', 'decimal', 18, 4)]},
            'wm_material_stock_tx': {'engine': 'InnoDB', 'primaryKey': ['tx_id'], 'indexes': [],
                'columns': [column('tx_id', 'bigint'), column('tx_quantity', 'decimal', 18, 4),
                            column('before_quantity', 'decimal', 18, 4), column('after_quantity', 'decimal', 18, 4),
                            column('quantity_origin'), column('source_doc_id', 'bigint')]}}
        for item in self.metadata.values():
            item.update(tableOptions=['utf8mb4_unicode_ci','Dynamic',''],foreignKeys=[],inboundForeignKeys=[],triggers=[],
                        tableDefinitionOptions='ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
                        physicalStorage={'rowFormat':'Dynamic','spaceType':'Single','zipPageSize':0,'pageSize':16384})
        self.rows = {
            'wm_material_stock': [{'stock_id': 1, 'stock_quantity': 1.25, 'reserved_quantity': 0.5,
                                  'quantity_origin': 'UNPROVEN', 'quantity_contract_version': None,
                                  'base_uom_id': None, 'quantity_source_head_tx_id': None,
                                  'version': 9, 'stock_quantity_exact': None}],
            'wm_material_stock_tx': [{'tx_id': 3, 'tx_quantity': Decimal('1.2500'),
                                     'before_quantity': Decimal('0.0000'), 'after_quantity': Decimal('1.2500'),
                                     'quantity_origin': 'UNPROVEN', 'source_doc_id': None}]}
        self.events = []
        self.alter_calls = []
        self.lock_hook = None
        self.after_hook = None
        self.alter_error = False
        self.fail_on_table = None
        self.apply_then_fail_on_table = None
        self.interrupt_on_table = None
        self.rollback_calls = 0

    def observe_identity(self):
        self.events.append('identity')
        return copy.deepcopy(self.identity)

    def describe(self):
        return copy.deepcopy(self.metadata)

    def iter_rows(self, table):
        return iter(copy.deepcopy(self.rows[table]))

    def begin_readonly(self):
        self.events.append('readonly')

    def configure_execution(self):
        self.events.append('configure')

    def lock(self):
        self.events.append('lock')
        if self.lock_hook:
            hook, self.lock_hook = self.lock_hook, None
            hook(self)

    def unlock(self):
        self.events.append('unlock')

    def alter(self, sql):
        self.events.append('alter')
        self.alter_calls.append(sql)
        table = 'wm_material_stock_tx' if '`wm_material_stock_tx`' in sql else 'wm_material_stock'
        if self.alter_error or self.fail_on_table == table:
            raise RuntimeError('秘密数据库错误不应外泄')
        names = ('tx_quantity','before_quantity','after_quantity') if table.endswith('_tx') else ('stock_quantity','reserved_quantity')
        for col in self.metadata[table]['columns']:
            if col['name'] in names and ('MODIFY COLUMN `' + col['name'] + '`' in sql or not sql.startswith('ALTER')):
                suffix = ' unsigned zerofill' if 'zerofill' in col['columnType'] else ' unsigned' if 'unsigned' in col['columnType'] else ''
                col.update(type='decimal', columnType='decimal(18,4)' + suffix, precision=18, scale=4)
                if col['default'] is not None:
                    col['default'] = format(Decimal(col['default']), '.4f')
        if 'ROW_FORMAT=' in sql:
            wanted=sql.split('ROW_FORMAT=',1)[1].split(',',1)[0]
            self.metadata[table]['tableOptions'][1]=wanted.capitalize()
            self.metadata[table]['tableOptions'][2]='row_format='+wanted
            self.metadata[table]['physicalStorage']['rowFormat']=wanted.capitalize()
            self.metadata[table]['tableDefinitionOptions']=self.metadata[table]['tableDefinitionOptions'].split(' ROW_FORMAT=',1)[0]+' ROW_FORMAT='+wanted
        for row in self.rows[table]:
            for name in names:
                if row[name] is not None:
                    value = Decimal.from_float(row[name]) if type(row[name]) is float else row[name]
                    row[name] = value.quantize(Decimal('0.0001'))
        if self.after_hook:
            self.after_hook(self)
        if self.interrupt_on_table == table:
            raise KeyboardInterrupt('模拟DDL已提交但客户端中断')
        if self.apply_then_fail_on_table == table:
            raise RuntimeError('模拟DDL已提交但响应丢失')

    def close(self):
        self.events.append('close')


class NativeStockPrecisionMigrationTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('stock_native_precision_migration'),
                             '必须交付任务B真实结构迁移执行器')
        self.m = importlib.import_module('stock_native_precision_migration')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.directory = Path(self.tmp.name)
        # 只生成算法所需的占位模板，不复制/恢复退役增量，也不是可执行迁移授权。
        self.sql = self.directory / 'synthetic-native-precision.sql'
        blocks = ['/* NEXUS_NATIVE_STOCK_PRECISION_DDL_BEGIN ' + table + '\n' +
                  self.m.EXPECTED_TEMPLATES[table] + '\nNEXUS_NATIVE_STOCK_PRECISION_DDL_END */'
                  for table in self.m.STEP_ORDER]
        self.sql.write_text('\n'.join(blocks) +
                            "\nSIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'OFFLINE_FIXTURE_ONLY';\n",
                            encoding='utf-8')
        sql_path = patch.object(self.m, 'SQL_PATH', self.sql)
        sql_path.start()
        self.addCleanup(sql_path.stop)
        self.store = FixtureStore()
        self.spent=self.directory/'spent'
        self.private_keys = {role: Ed25519PrivateKey.generate() for role in ('backup', 'review', 'execution')}
        material = {}
        for role, key in self.private_keys.items():
            public = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
            material[role] = (public, digest(public))
        self.keys = self.m.AuthorityKeys(material)
        self.target = {k: v for k, v in self.store.identity.items() if k not in ('readOnly', 'superReadOnly', 'replicationApplierCount', 'eventScheduler', 'replicationInventory')}
        self.backup_file = self.directory / 'backup.dump'
        self.restore_file = self.directory / 'restore.receipt'
        self.fence_file = self.directory / 'fence.receipt'
        for path in (self.backup_file, self.restore_file, self.fence_file):
            path.write_bytes(('私有夹具-' + path.name).encode())
        self.files = self.m.EvidenceFiles(self.backup_file, self.restore_file, self.fence_file)
        self.maintenance = {'kind': 'stock-native-maintenance-v3', 'target': self.target,
            'windowStart': (NOW - timedelta(minutes=20)).isoformat(),
            'windowEnd': (NOW + timedelta(minutes=20)).isoformat(), 'ticketId': 'fixture-maintenance', 'scope':copy.deepcopy(SCOPE),
            'nullPolicy': 'PRESERVE_NULL_UNKNOWN', 'fenceStrategy': 'GLOBAL_READ_ONLY_PRIVILEGED_WRITERS_FENCED',
            'fenceEvidenceSha256': digest(self.fence_file.read_bytes()), **self.m.artifact_hashes()}
        self.refresh_backup()
        self.connect_calls = 0

    def refresh_backup(self):
        snapshot = self.m.capture_snapshot(self.store, self.maintenance['nullPolicy'])
        self.backup = {'kind': 'stock-native-backup-v3', 'target': self.target, 'backupId': 'fixture-backup', 'scope':copy.deepcopy(SCOPE),
            'capturedAt': (NOW - timedelta(minutes=10)).isoformat(),
            'restoredAt': (NOW - timedelta(minutes=5)).isoformat(),
            'restoredServerUuid': '22222222-2222-2222-2222-222222222222',
            'backupSha256': digest(self.backup_file.read_bytes()), 'backupBytes': self.backup_file.stat().st_size,
            'restoreEvidenceSha256': digest(self.restore_file.read_bytes()),
            'sourceSnapshotSha256': snapshot['snapshotSha256']}

    def sign(self, role, payload):
        raw = self.m.canonical(payload)
        return self.m.SignedDocument(raw, self.private_keys[role].sign(raw))

    def factory(self, target):
        self.connect_calls += 1
        self.assertEqual(self.target, target)
        return self.store

    def precheck(self):
        return self.m.precheck(store_factory=self.factory, maintenance=self.sign('review', self.maintenance),
            backup=self.sign('backup', self.backup), keys=self.keys, files=self.files, now=lambda: NOW)

    def grant(self, plan):
        review = self.sign('review', plan)
        payload = {'kind': 'stock-native-execution-v3', 'target': self.target,
            'reviewedPlanSha256': digest(review.payload), 'reviewSignatureSha256': digest(review.signature),
            'nonce': 'a' * 32, 'issuedAt': NOW.isoformat(), 'action':'EXECUTE_ONCE',
            'scope':copy.deepcopy(SCOPE), 'stepsSha256':digest(self.m.canonical(plan['steps'])),
            'expiresAt': (NOW + timedelta(minutes=10)).isoformat(), 'operatorSha256': self.m.operator_digest()}
        payload.update(self.m.inspect_plan_history(plan=plan,reviewed_plan=review,keys=self.keys,receipt_dir=self.spent))
        return review, payload

    def execute(self, plan=None, *, review=None, grant=None, now=None):
        if plan is None and review is None:
            plan = self.precheck()
        if review is None:
            review, payload = self.grant(plan)
            grant = self.sign('execution', payload)
        return self.m.execute_once(store_factory=self.factory, maintenance=self.sign('review', self.maintenance),
            backup=self.sign('backup', self.backup), reviewed_plan=review, execution=grant,
            keys=self.keys, files=self.files, receipt_dir=self.spent, now=now or (lambda: NOW))

    def stock_sql(self, plan):
        return next(step['ddl'] for step in plan['steps'] if step['table']=='wm_material_stock')

    def legacy_tx(self):
        for column in self.store.metadata['wm_material_stock_tx']['columns']:
            if column['name'] in ('tx_quantity','before_quantity','after_quantity'):
                column.update(precision=12, scale=2, columnType='decimal(12,2)')
        self.refresh_backup()

    def recovery(self, plan, review, parent, *, prefix=None, current=NOW, nonce='b'*32, signed_grant=None):
        snapshot = self.m.capture_snapshot(self.store, self.maintenance['nullPolicy'])
        if prefix is None:
            prefix = self.m.match_checkpoint(plan, snapshot)
        parent_data = json.loads(parent.payload)
        journal = self.spent / (parent_data['nonce'] + '.events.jsonl')
        maintenance = {**self.maintenance, 'kind':'stock-native-recovery-maintenance-v3',
            'originalReviewedPlanSha256':digest(review.payload), 'backupReceiptSha256':plan['backupReceiptSha256'],
            'windowStart':(current-timedelta(minutes=1)).isoformat(), 'windowEnd':(current+timedelta(minutes=20)).isoformat()}
        signed_maintenance = self.sign('review', maintenance)
        payload = {'kind':'stock-native-execution-v3', 'target':self.target, 'action':'RESUME_ONCE',
            'scope':copy.deepcopy(SCOPE), 'stepsSha256':digest(self.m.canonical(plan['steps'])),
            'reviewedPlanSha256':digest(review.payload), 'reviewSignatureSha256':digest(review.signature),
            'nonce':nonce, 'issuedAt':current.isoformat(), 'expiresAt':(current+timedelta(minutes=10)).isoformat(),
            'operatorSha256':self.m.operator_digest(), 'parentExecutionSha256':digest(parent.payload),
            'parentJournalSha256':digest(journal.read_bytes()), 'completedPrefix':prefix,
            'observedSnapshotSha256':snapshot['snapshotSha256'],
            'remainingStepsSha256':digest(self.m.canonical(plan['steps'][prefix:])),
            'recoveryMaintenanceSha256':digest(signed_maintenance.payload)}
        payload.update(self.m.inspect_plan_history(plan=plan,reviewed_plan=review,keys=self.keys,receipt_dir=self.spent))
        grant = signed_grant or self.sign('execution',payload)
        self.last_recovery_grant=grant
        return self.m.resume_once(store_factory=self.factory, maintenance=self.sign('review',self.maintenance),
            backup=self.sign('backup',self.backup), reviewed_plan=review, parent_execution=parent,
            recovery_maintenance=signed_maintenance, execution=grant, keys=self.keys, files=self.files,
            receipt_dir=self.spent, now=lambda:current)

    def blocked(self, call, code=None):
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            call()
        if code:
            self.assertEqual(code, caught.exception.code)
        self.assertEqual([], self.store.alter_calls)
        return caught.exception

    def test_sql_direct_execution_only_signals_and_real_alter_is_in_guarded_template(self):
        sql = self.sql.read_text(encoding='utf-8')
        outside = self.m.executable_sql_outside_template(sql)
        self.assertNotRegex(outside.upper(), r'\b(ALTER|UPDATE|INSERT|DELETE|DROP|CREATE|REPLACE|CAST|ROUND)\b')
        self.assertRegex(outside, r"SIGNAL SQLSTATE '45000'")
        plan = self.precheck()
        self.assertEqual(1, self.stock_sql(plan).upper().count('ALTER TABLE'))
        self.assertIn('`ry-erp`.`wm_material_stock`', self.stock_sql(plan))
        self.assertNotIn('wm_material_stock_tx', self.stock_sql(plan))

    def test_exact_binary_values_and_range_are_checked_without_rounding(self):
        for value in (0.0, -0.0, 1.25, 0.0625, -1.125, 99999999999999.0):
            with self.subTest(value=value):
                self.assertEqual(Decimal.from_float(value),
                    self.m.exact_quantity(value, 'double', 'REJECT_NULL', True))
        for value in (0.1, 1.00001, 1e14, float('nan'), float('inf'), 5e-324):
            with self.subTest(value=value), self.assertRaises(self.m.MigrationBlocked):
                self.m.exact_quantity(value, 'double', 'REJECT_NULL', True)

    def test_decimal_extremes_trailing_zeros_and_fraction_are_checked(self):
        for value in ('99999999999999.9999', '-99999999999999.9999', '1.2500000'):
            self.assertEqual(Decimal(value), self.m.exact_quantity(Decimal(value), 'decimal', 'REJECT_NULL', True))
        for value in ('100000000000000', '1.00001', 'NaN', 'Infinity'):
            with self.assertRaises(self.m.MigrationBlocked):
                self.m.exact_quantity(Decimal(value), 'decimal', 'REJECT_NULL', True)

    def test_float_text_or_fake_decimal_cannot_prove_native_double(self):
        for value in ('1.25', Decimal('1.25'), 1):
            with self.assertRaises(self.m.MigrationBlocked):
                self.m.exact_quantity(value, 'double', 'PRESERVE_NULL_UNKNOWN', True)
        with self.assertRaises(self.m.MigrationBlocked):
            self.m.exact_quantity(1.25, 'float', 'REJECT_NULL', True)

    def test_null_requires_explicit_policy_and_original_nullable_column(self):
        self.assertIsNone(self.m.exact_quantity(None, 'double', 'PRESERVE_NULL_UNKNOWN', True))
        for policy, nullable in (('', True), ('REJECT_NULL', True), ('PRESERVE_NULL_UNKNOWN', False)):
            with self.assertRaises(self.m.MigrationBlocked):
                self.m.exact_quantity(None, 'double', policy, nullable)

    def test_empty_or_null_stock_is_not_certified_zero(self):
        self.store.rows['wm_material_stock'] = []
        self.refresh_backup()
        plan = self.precheck()
        self.assertEqual(0, plan['stockRows'])
        self.assertEqual('NOT_PERFORMED', plan['sourceCertification'])
        self.assertTrue(plan['missingStockDoesNotProveZero'])

    def test_null_and_uncertified_history_survive_actual_alter(self):
        self.store.rows['wm_material_stock'][0]['reserved_quantity'] = None
        self.refresh_backup()
        original = copy.deepcopy(self.store.rows)
        result = self.execute()
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', result['status'])
        self.assertEqual('NOT_PERFORMED', result['sourceCertification'])
        self.assertIsNone(self.store.rows['wm_material_stock'][0]['reserved_quantity'])
        for name in original['wm_material_stock'][0]:
            if name not in ('stock_quantity', 'reserved_quantity'):
                self.assertEqual(original['wm_material_stock'][0][name], self.store.rows['wm_material_stock'][0][name])
        self.assertEqual(original['wm_material_stock_tx'], self.store.rows['wm_material_stock_tx'])
        self.assertEqual(1, len(self.store.alter_calls))
        self.assertEqual(0, self.store.rollback_calls)
        self.assertLess(self.store.events.index('lock'), self.store.events.index('alter'))
        self.assertIn('unlock', self.store.events)

    def test_all_rows_all_tenants_and_deleted_rows_are_checked(self):
        row = copy.deepcopy(self.store.rows['wm_material_stock'][0])
        row.update(stock_id=2, stock_quantity=0.1)
        self.store.rows['wm_material_stock'].append(row)
        self.blocked(self.precheck, 'LOSSY_VALUE')

    def test_missing_identity_window_backup_or_fence_rejects_before_connect(self):
        for key in ('target', 'windowStart', 'windowEnd', 'nullPolicy', 'ticketId', 'fenceEvidenceSha256'):
            with self.subTest(key=key):
                good = self.maintenance
                self.maintenance = {k: v for k, v in good.items() if k != key}
                self.blocked(self.precheck)
                self.assertEqual(0, self.connect_calls)
                self.maintenance = good
        for key in ('backupSha256', 'restoreEvidenceSha256', 'sourceSnapshotSha256', 'restoredAt'):
            with self.subTest(key=key):
                good = self.backup
                self.backup = {k: v for k, v in good.items() if k != key}
                self.blocked(self.precheck)
                self.assertEqual(0, self.connect_calls)
                self.backup = good

    def test_edited_json_without_detached_signature_is_rejected(self):
        good = self.sign('review', self.maintenance)
        altered = self.m.SignedDocument(good.payload.replace(b'fixture-maintenance', b'forged-maintenance'), good.signature)
        self.blocked(lambda: self.m.precheck(store_factory=self.factory, maintenance=altered,
            backup=self.sign('backup', self.backup), keys=self.keys, files=self.files, now=lambda: NOW), 'SIGNATURE_REJECTED')
        self.assertEqual(0, self.connect_calls)

    def test_distinct_authorities_and_pinned_fingerprints_are_required(self):
        public = self.private_keys['review'].public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        with self.assertRaises(self.m.MigrationBlocked):
            self.m.AuthorityKeys({role: (public, digest(public)) for role in self.private_keys})
        material = dict(self.keys.material)
        material['review'] = (public, 'f' * 64)
        with self.assertRaises(self.m.MigrationBlocked):
            self.m.AuthorityKeys(material)

    def test_backup_restore_and_fence_actual_files_must_match_reviewed_receipts(self):
        for path in (self.backup_file, self.restore_file, self.fence_file):
            with self.subTest(path=path.name):
                good = path.read_bytes()
                path.write_bytes(b'changed')
                self.blocked(self.precheck, 'EVIDENCE_MISMATCH')
                self.assertEqual(0, self.connect_calls)
                path.write_bytes(good)

    def test_backup_must_cover_same_complete_original_snapshot(self):
        self.backup['sourceSnapshotSha256'] = 'b' * 64
        self.blocked(self.precheck, 'BACKUP_SNAPSHOT_MISMATCH')

    def test_expired_window_or_wrong_live_server_or_mysql8_is_blocked(self):
        self.maintenance['windowEnd'] = (NOW - timedelta(seconds=1)).isoformat()
        self.blocked(self.precheck, 'MAINTENANCE_WINDOW_REJECTED')
        self.maintenance['windowEnd'] = (NOW + timedelta(minutes=20)).isoformat()
        for field, value in (('serverUuid', '3' * 36), ('schema', 'ry-vue'), ('version', '8.0.39'), ('port', 3307)):
            with self.subTest(field=field):
                good = self.store.identity[field]
                self.store.identity[field] = value
                self.blocked(self.precheck, 'TARGET_MISMATCH')
                self.store.identity[field] = good

    def test_sql_or_executor_artifact_drift_is_not_approved(self):
        self.maintenance['executorSha256'] = 'd' * 64
        self.blocked(self.precheck, 'ARTIFACT_MISMATCH')
        self.assertEqual(0, self.connect_calls)

    def test_stock_tx_already_decimal_is_not_migrated_and_wrong_tx_schema_blocks(self):
        tx = self.store.metadata['wm_material_stock_tx']['columns'][1]
        tx['scale'] = 2
        self.blocked(self.precheck, 'TX_NATIVE_PRECISION_REQUIRED')

    def test_source_defaults_and_nullability_are_preserved_not_zeroed(self):
        col = self.store.metadata['wm_material_stock']['columns'][1]
        col.update(nullable=False, default='1.25')
        self.refresh_backup()
        plan = self.precheck()
        self.assertIn('`stock_quantity` DECIMAL(18,4) NOT NULL DEFAULT 1.2500', self.stock_sql(plan))
        self.assertIn('`reserved_quantity` DECIMAL(18,4) NULL DEFAULT NULL', self.stock_sql(plan))
        self.execute(plan)
        self.assertFalse(col['nullable'])
        self.assertEqual(Decimal('1.25'), Decimal(col['default']))

    def test_lossy_default_is_blocked_even_when_rows_are_empty(self):
        self.store.metadata['wm_material_stock']['columns'][1]['default'] = '0.1'
        self.store.rows['wm_material_stock'] = []
        self.blocked(self.precheck, 'LOSSY_VALUE')

    def test_all_native_columns_are_observed_not_rewritten_or_certified(self):
        self.store.alter('夹具初始化，不是SQL执行')
        self.store.alter_calls.clear()
        self.refresh_backup()
        plan = self.precheck()
        self.assertEqual('ALREADY_NATIVE_SOURCE_UNCERTIFIED', plan['status'])
        self.blocked(lambda: self.execute(plan), 'ALREADY_NATIVE')

    def test_concurrent_row_version_null_or_schema_change_rejects_under_lock(self):
        plan = self.precheck()
        for change in (
            lambda s: s.rows['wm_material_stock'][0].update(stock_quantity=2.5),
            lambda s: s.rows['wm_material_stock'][0].update(version=10),
            lambda s: s.rows['wm_material_stock'][0].update(quantity_origin='NATIVE_V1'),
            lambda s: s.rows['wm_material_stock'][0].update(reserved_quantity=None),
            lambda s: s.metadata['wm_material_stock']['columns'][1].update(comment='并发修改'),
            lambda s: s.rows['wm_material_stock_tx'][0].update(source_doc_id=100)):
            original_meta, original_rows = copy.deepcopy(self.store.metadata), copy.deepcopy(self.store.rows)
            with self.subTest(change=change):
                self.store.lock_hook = change
                self.blocked(lambda: self.execute(plan), 'SNAPSHOT_CHANGED')
                self.assertIn('unlock', self.store.events)
            self.store.metadata, self.store.rows = original_meta, original_rows

    def test_global_readonly_fence_missing_is_rejected(self):
        plan = self.precheck()
        self.store.identity['readOnly'] = 0
        self.blocked(lambda: self.execute(plan), 'WRITER_FENCE_MISSING')

    def test_review_and_execution_signatures_cannot_be_interchanged_or_reused(self):
        plan = self.precheck()
        review, payload = self.grant(plan)
        wrong_grant = self.sign('review', payload)
        self.blocked(lambda: self.execute(review=review, grant=wrong_grant), 'SIGNATURE_REJECTED')
        payload['reviewedPlanSha256'] = 'b' * 64
        self.blocked(lambda: self.execute(review=review, grant=self.sign('execution', payload)), 'EXECUTION_GRANT_REJECTED')

    def test_independent_grant_expires_and_operator_binding_is_required(self):
        review, payload = self.grant(self.precheck())
        for change in ({'expiresAt': (NOW - timedelta(seconds=1)).isoformat()}, {'operatorSha256': 'e' * 64}):
            good = copy.deepcopy(payload)
            good.update(change)
            self.blocked(lambda: self.execute(review=review, grant=self.sign('execution', good)), 'EXECUTION_GRANT_REJECTED')

    def test_once_receipt_prevents_same_nonce_replay_before_any_second_ddl(self):
        plan = self.precheck()
        review, payload = self.grant(plan)
        grant = self.sign('execution', payload)
        result = self.execute(review=review, grant=grant)
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', result['status'])
        self.store.alter_calls.clear()
        self.blocked(lambda: self.execute(review=review, grant=grant), 'EXECUTION_ALREADY_CONSUMED')

    def test_window_is_rechecked_immediately_before_implicit_commit_ddl(self):
        plan = self.precheck()
        self.store.lock_hook = lambda s: None
        clock_calls = []
        def clock():
            clock_calls.append(1)
            return NOW if len(clock_calls) < 4 else NOW + timedelta(hours=1)
        self.blocked(lambda: self.execute(plan, now=clock), 'MAINTENANCE_WINDOW_REJECTED')

    def test_ddl_error_is_uncertain_not_rolled_back_and_grant_is_spent(self):
        plan = self.precheck()
        review, payload = self.grant(plan)
        grant = self.sign('execution', payload)
        self.store.alter_error = True
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.execute(review=review, grant=grant)
        self.assertEqual('DDL_OUTCOME_UNCERTAIN', caught.exception.code)
        self.assertTrue(caught.exception.ddl_attempted)
        self.assertEqual(0, self.store.rollback_calls)
        receipts = list((self.spent).glob('*.json'))
        self.assertEqual(1, len(receipts))
        self.assertEqual('DDL_OUTCOME_UNCERTAIN', json.loads(receipts[0].read_text())['status'])
        self.assertNotIn('秘密', receipts[0].read_text())

    def test_post_ddl_any_auth_unit_version_or_history_mutation_blocks_without_rollback(self):
        for mutation in (
            lambda s: s.rows['wm_material_stock'][0].update(quantity_origin='NATIVE_V1'),
            lambda s: s.rows['wm_material_stock'][0].update(base_uom_id='forged'),
            lambda s: s.rows['wm_material_stock'][0].update(version=10),
            lambda s: s.rows['wm_material_stock'][0].update(quantity_source_head_tx_id=3),
            lambda s: s.rows['wm_material_stock'][0].update(stock_quantity=Decimal('2.0000')),
            lambda s: s.rows['wm_material_stock_tx'][0].update(source_doc_id=200)):
            with self.subTest(mutation=mutation):
                self.store = FixtureStore()
                self.spent=self.directory/digest(str(mutation).encode())[:16]
                self.refresh_backup()
                plan = self.precheck()
                review, payload = self.grant(plan)
                payload['nonce'] = digest(str(mutation).encode())[:32]
                self.store.after_hook = mutation
                with self.assertRaises(self.m.MigrationBlocked) as caught:
                    self.execute(review=review, grant=self.sign('execution', payload))
                self.assertEqual('POST_DDL_PRESERVATION_FAILED', caught.exception.code)
                self.assertTrue(caught.exception.ddl_attempted)
                self.assertEqual(0, self.store.rollback_calls)

    def test_binary_exact_but_shortest_decimal_conversion_ambiguous_is_blocked(self):
        value = 99999999999999.125
        self.assertEqual(Decimal('99999999999999.125'), Decimal.from_float(value))
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.m.exact_quantity(value, 'double', 'REJECT_NULL', True)
        self.assertEqual('MYSQL_DOUBLE_CONVERSION_UNPROVEN', caught.exception.code)

    def test_negative_zero_remains_numeric_zero_without_false_postcondition_failure(self):
        self.store.rows['wm_material_stock'][0]['stock_quantity'] = -0.0
        self.refresh_backup()
        def mysql_zero(store):
            store.rows['wm_material_stock'][0]['stock_quantity'] = Decimal('0.0000')
        self.store.after_hook = mysql_zero
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', self.execute()['status'])

    def test_unsigned_or_zerofill_source_cannot_silently_drop_domain_constraints(self):
        for column_type in ('double unsigned', 'double zerofill'):
            self.store.metadata['wm_material_stock']['columns'][1]['columnType'] = column_type
            self.blocked(self.precheck, 'SOURCE_ATTRIBUTES_UNSUPPORTED')

    def test_extra_metadata_constraints_change_under_lock_is_rejected(self):
        self.refresh_backup()
        plan = self.precheck()
        self.store.lock_hook = lambda s: s.metadata['wm_material_stock'].update(foreignKeys=[['changed-reference']])
        self.blocked(lambda: self.execute(plan), 'DEPENDENCY_CLOSURE_UNSUPPORTED')

    def test_cleanup_errors_do_not_leak_or_claim_success_and_both_cleanups_run(self):
        plan = self.precheck()
        calls = []
        def broken_unlock():
            calls.append('unlock')
            raise RuntimeError('秘密令牌')
        def broken_close():
            calls.append('close')
            raise RuntimeError('秘密密码')
        self.store.unlock = broken_unlock
        self.store.close = broken_close
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.execute(plan)
        self.assertTrue(caught.exception.ddl_attempted)
        self.assertEqual('DDL_OUTCOME_UNCERTAIN', caught.exception.code)
        self.assertNotIn('秘密', str(caught.exception))
        self.assertEqual(['unlock', 'close'], calls)
        receipt = next((self.spent).glob('*.json'))
        self.assertEqual('DDL_OUTCOME_UNCERTAIN', json.loads(receipt.read_text())['status'])

    def test_replicating_or_live_event_writer_fence_cannot_be_signed_away(self):
        plan = self.precheck()
        for key, value in (('replicationApplierCount', 1), ('eventScheduler', 'ON')):
            with self.subTest(key=key):
                self.store.identity[key] = value
                self.blocked(lambda: self.execute(plan), 'WRITER_FENCE_MISSING')
                self.store.identity[key] = 0 if key == 'replicationApplierCount' else 'OFF'

    def test_adapter_rows_use_binary_prepared_protocol_and_no_tenant_or_delete_filter(self):
        calls = []
        class Cursor:
            description = [('stock_id',), ('stock_quantity',), ('reserved_quantity',)]
            def execute(self, sql):
                calls.append(sql)
            def __iter__(self):
                return iter([(1, 1.25, 0.5)])
            def close(self):
                calls.append('close')
        class Connection:
            def cursor(self, **kwargs):
                calls.append(kwargs)
                return Cursor()
        adapter = object.__new__(self.m.MySQLStore)
        adapter.connection = Connection()
        rows = list(adapter.iter_rows('wm_material_stock'))
        self.assertIs(type(rows[0]['stock_quantity']), float)
        self.assertEqual({'prepared': True}, calls[0])
        self.assertEqual('SELECT * FROM `ry-erp`.`wm_material_stock` ORDER BY `stock_id`', calls[1])

    def test_cli_bool_shortcuts_and_secret_arguments_fail_without_connect_or_echo(self):
        from contextlib import redirect_stdout, redirect_stderr
        from unittest.mock import patch
        import io
        for args in (['--apply'], ['--verified'], ['--password', '秘密密码']):
            out, err = io.StringIO(), io.StringIO()
            with patch.object(self.m, 'MySQLStore') as connect, redirect_stdout(out), redirect_stderr(err):
                code = self.m.main(args)
            self.assertEqual(2, code)
            self.assertEqual('BLOCKED', json.loads(out.getvalue())['status'])
            self.assertNotIn('秘密', out.getvalue() + err.getvalue())
            connect.assert_not_called()

    def test_explicit_nonreplicating_server_id_zero_is_not_missing_identity(self):
        self.store.identity['serverId'] = 0
        self.target['serverId'] = 0
        self.refresh_backup()
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', self.execute()['status'])

    def test_stock_decimal_metadata_must_match_its_native_column_type(self):
        column = self.store.metadata['wm_material_stock']['columns'][1]
        column.update(type='decimal', columnType='decimal(12,2)', precision=None, scale=2)
        self.store.rows['wm_material_stock'][0]['stock_quantity'] = Decimal('1.25')
        self.blocked(self.precheck, 'METADATA_REQUIRED')

    def test_nul_in_original_comment_cannot_break_single_statement_ddl(self):
        self.store.metadata['wm_material_stock']['columns'][1]['comment'] = '原注释\x00额外SQL'
        self.blocked(self.precheck, 'SOURCE_ATTRIBUTES_UNSUPPORTED')

    def test_snapshot_changed_before_lock_is_not_replaced_by_a_fresh_unreviewed_plan(self):
        plan = self.precheck()
        self.store.rows['wm_material_stock'][0]['version'] = 10
        self.blocked(lambda: self.execute(plan), 'SNAPSHOT_CHANGED')
        self.assertNotIn('lock', self.store.events)

    def test_duplicate_rows_and_omitted_columns_are_not_full_snapshots(self):
        original = copy.deepcopy(self.store.rows['wm_material_stock'])
        self.store.rows['wm_material_stock'].append(copy.deepcopy(original[0]))
        self.blocked(self.precheck, 'ROW_ORDER_OR_DUPLICATE_REJECTED')
        self.store.rows['wm_material_stock'] = original
        self.store.rows['wm_material_stock'][0].pop('quantity_contract_version')
        self.blocked(self.precheck, 'FULL_ROW_SNAPSHOT_REQUIRED')

    def test_backup_bytes_changed_after_lock_is_rejected_without_ddl(self):
        plan = self.precheck()
        self.store.lock_hook = lambda s: self.backup_file.write_bytes(b'changed-after-lock')
        self.blocked(lambda: self.execute(plan), 'EVIDENCE_MISMATCH')

    def test_trust_material_is_frozen_after_out_of_band_pin_validation(self):
        material = dict(self.keys.material)
        authorities = self.m.AuthorityKeys(material)
        original = material['review']
        material['review'] = material['execution']
        self.assertEqual(original, authorities.material['review'])
        with self.assertRaises(TypeError):
            authorities.material['review'] = material['execution']

    def test_recorded_native_tx12_2_is_actually_widened_but_history_is_not_certified(self):
        self.legacy_tx()
        original_rows = copy.deepcopy(self.store.rows)
        plan = self.precheck()
        self.assertEqual(SCOPE, plan['scope'])
        self.assertEqual(['wm_material_stock_tx','wm_material_stock'], [step['table'] for step in plan['steps']])
        self.assertEqual(2, sum(step['ddl'] is not None for step in plan['steps']))
        result = self.execute(plan)
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', result['status'])
        self.assertEqual([], result['remainingSourcePrecisionGaps'])
        self.assertEqual('NOT_PERFORMED', result['sourceCertification'])
        self.assertFalse(result['historicalPrecisionRecovered'])
        self.assertEqual(2, len(self.store.alter_calls))
        for table, columns in self.m.NATIVE_COLUMNS.items():
            for column in self.store.metadata[table]['columns']:
                if column['name'] in columns:
                    self.assertEqual((18,4), (column['precision'],column['scale']))
            self.assertEqual(original_rows[table], self.store.rows[table])

    def test_even_all_native_goal_columns_do_not_certify_history_after_migration(self):
        result = self.execute()
        self.assertEqual([], result['remainingSourcePrecisionGaps'])
        self.assertEqual('NOT_PERFORMED', result['sourceCertification'])

    def test_mixed_stock_native_and_double_changes_only_unproven_native_type(self):
        column = self.store.metadata['wm_material_stock']['columns'][1]
        column.update(type='decimal', columnType='decimal(18,4)', precision=18, scale=4)
        self.store.rows['wm_material_stock'][0]['stock_quantity'] = Decimal('1.2500')
        self.refresh_backup()
        plan = self.precheck()
        self.assertNotIn('MODIFY COLUMN `stock_quantity`', self.stock_sql(plan))
        self.assertIn('MODIFY COLUMN `reserved_quantity`', self.stock_sql(plan))
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', self.execute(plan)['status'])

    def test_operator_binding_uses_os_token_not_mutable_user_environment(self):
        from unittest.mock import patch
        import os
        before = self.m.operator_digest()
        with patch.dict(os.environ, {name: 'forged-identity' for name in
                        ('USER', 'USERNAME', 'LOGNAME', 'LNAME', 'USERDOMAIN')}):
            self.assertEqual(before, self.m.operator_digest())

    def test_old_v1_or_incomplete_five_column_authorization_is_rejected_before_connect(self):
        good = copy.deepcopy(self.maintenance)
        for change in ({'kind':'stock-native-maintenance-v1'}, {'scope':SCOPE[1:]},
                       {'scope':[{'table':'wm_material_stock_tx','columns':['tx_quantity'],'targetType':'DECIMAL(18,4)'}, SCOPE[1]]}):
            self.maintenance = {**good, **change}
            self.blocked(self.precheck)
            self.assertEqual(0, self.connect_calls)
        self.maintenance = good
        plan = self.precheck()
        review, grant = self.grant(plan)
        grant['kind'] = 'stock-native-execution-v1'
        before = self.connect_calls
        self.blocked(lambda:self.execute(review=review,grant=self.sign('execution',grant)))
        self.assertEqual(before, self.connect_calls)

    def test_tx_null_lossy_overrange_or_narrowing_source_blocks_before_any_ddl(self):
        self.legacy_tx()
        for amount in (Decimal('100000000000000'), Decimal('1.00001')):
            self.store.rows['wm_material_stock_tx'][0]['tx_quantity'] = amount
            self.blocked(self.precheck, 'LOSSY_VALUE')
        self.store.rows['wm_material_stock_tx'][0]['tx_quantity'] = None
        self.maintenance['nullPolicy'] = 'REJECT_NULL'
        self.blocked(self.precheck, 'NULL_NOT_ALLOWED')

    def test_tx_default_nullability_comment_and_unsigned_zerofill_are_preserved(self):
        self.legacy_tx()
        col = self.store.metadata['wm_material_stock_tx']['columns'][1]
        col.update(nullable=False, default='1.25', comment="流水原注释'保留", columnType='decimal(12,2) unsigned zerofill')
        self.refresh_backup()
        plan = self.precheck()
        ddl = plan['steps'][0]['ddl']
        self.assertIn('DECIMAL(18,4) UNSIGNED ZEROFILL NOT NULL DEFAULT 1.2500', ddl)
        self.assertIn("COMMENT '流水原注释''保留'", ddl)
        self.execute(plan)
        self.assertEqual('decimal(18,4) unsigned zerofill', col['columnType'])
        self.assertEqual("流水原注释'保留", col['comment'])
        self.assertFalse(col['nullable'])

    def test_signed_plan_and_grant_explicitly_bind_two_steps_not_a_stock_sql_alias(self):
        self.legacy_tx()
        plan = self.precheck()
        review, payload = self.grant(plan)
        for change in ({'scope':SCOPE[1:]}, {'stepsSha256':'f'*64}):
            changed = {**payload, **change}
            self.blocked(lambda:self.execute(review=review,grant=self.sign('execution',changed)), 'EXECUTION_GRANT_REJECTED')

    def test_second_table_failure_records_applied_prefix_and_uncertain_step(self):
        self.legacy_tx()
        plan = self.precheck()
        self.store.fail_on_table = 'wm_material_stock'
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.execute(plan)
        self.assertEqual('PARTIAL_APPLIED_DDL_OUTCOME_UNCERTAIN', caught.exception.code)
        report = json.loads(next((self.spent).glob('*.json')).read_text())
        self.assertEqual(1, report['verifiedPrefix'])
        self.assertEqual([0], report['knownAppliedSteps'])
        self.assertEqual(1, report['uncertainStep'])
        self.assertEqual(0, self.store.rollback_calls)
        journal = next((self.spent).glob('*.events.jsonl'))
        events = self.m.read_journal(journal)
        self.assertTrue(any(event['event']=='STEP_VERIFIED' and event['stepIndex']==0 for event in events))
        self.assertTrue(any(event['event']=='DDL_INTENT' and event['stepIndex']==1 for event in events))

    def test_journal_intent_is_durable_before_each_ddl_not_after_response(self):
        self.legacy_tx()
        seen = []
        original = self.store.alter
        def inspect(sql):
            events = self.m.read_journal(next((self.spent).glob('*.events.jsonl')))
            self.assertEqual('DDL_INTENT', events[-1]['event'])
            seen.append(events[-1]['stepIndex'])
            original(sql)
        self.store.alter = inspect
        self.execute()
        self.assertEqual([0,1], seen)

    def test_midpoint_row_or_unplanned_column_change_prevents_second_ddl(self):
        self.legacy_tx()
        plan = self.precheck()
        self.store.after_hook = lambda store:store.rows['wm_material_stock_tx'][0].update(quantity_origin='NATIVE_V1')
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.execute(plan)
        self.assertEqual('POST_DDL_PRESERVATION_FAILED', caught.exception.code)
        self.assertEqual(1, len(self.store.alter_calls))

    def test_resume_requires_new_signed_grant_and_only_runs_exact_remaining_step(self):
        self.legacy_tx()
        plan = self.precheck()
        review, payload = self.grant(plan)
        parent = self.sign('execution',payload)
        self.store.fail_on_table = 'wm_material_stock'
        with self.assertRaises(self.m.MigrationBlocked):
            self.execute(review=review,grant=parent)
        self.store.fail_on_table = None
        self.store.alter_calls.clear()
        result = self.recovery(plan, review, parent, current=NOW+timedelta(hours=2))
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', result['status'])
        self.assertEqual(2, result['verifiedPrefix'])
        self.assertEqual(1, len(self.store.alter_calls))
        self.assertNotIn('`wm_material_stock_tx`', self.store.alter_calls[0])
        self.assertEqual('NOT_PERFORMED', result['sourceCertification'])

    def test_lost_ack_or_process_interruption_after_first_ddl_recovers_without_reapplying_it(self):
        for mode in ('apply_then_fail_on_table','interrupt_on_table'):
            with self.subTest(mode=mode):
                self.store = FixtureStore()
                self.spent=self.directory/mode
                self.legacy_tx()
                plan = self.precheck()
                review, payload = self.grant(plan)
                payload['nonce'] = digest(mode.encode())[:32]
                parent = self.sign('execution',payload)
                setattr(self.store,mode,'wm_material_stock_tx')
                with self.assertRaises((self.m.MigrationBlocked,KeyboardInterrupt)):
                    self.execute(review=review,grant=parent)
                setattr(self.store,mode,None)
                self.store.alter_calls.clear()
                result = self.recovery(plan, review, parent, nonce=digest((mode+'resume').encode())[:32])
                self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED', result['status'])
                self.assertEqual(1, len(self.store.alter_calls))

    def test_partial_schema_not_matching_signed_checkpoint_refuses_recovery(self):
        self.legacy_tx()
        plan = self.precheck()
        review, payload = self.grant(plan)
        parent = self.sign('execution',payload)
        self.store.fail_on_table = 'wm_material_stock'
        with self.assertRaises(self.m.MigrationBlocked):
            self.execute(review=review,grant=parent)
        self.store.fail_on_table = None
        self.store.metadata['wm_material_stock_tx']['columns'][1].update(precision=12,scale=2,columnType='decimal(12,2)')
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.recovery(plan,review,parent)
        self.assertEqual('RECOVERY_STATE_NOT_A_SIGNED_CHECKPOINT', caught.exception.code)

    def test_recovery_live_values_cannot_be_rebased_by_editing_journal_or_json(self):
        self.legacy_tx()
        plan = self.precheck()
        review,payload = self.grant(plan)
        parent = self.sign('execution',payload)
        self.store.fail_on_table = 'wm_material_stock'
        with self.assertRaises(self.m.MigrationBlocked):
            self.execute(review=review,grant=parent)
        self.store.fail_on_table = None
        self.store.rows['wm_material_stock_tx'][0]['tx_quantity'] = Decimal('2.0000')
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.recovery(plan,review,parent)
        self.assertEqual('RECOVERY_STATE_NOT_A_SIGNED_CHECKPOINT', caught.exception.code)

    def interrupted_pair(self):
        self.legacy_tx()
        plan=self.precheck()
        review,payload=self.grant(plan)
        parent=self.sign('execution',payload)
        self.store.fail_on_table='wm_material_stock'
        with self.assertRaises(self.m.MigrationBlocked):
            self.execute(review=review,grant=parent)
        self.store.fail_on_table=None
        self.store.alter_calls.clear()
        return plan,review,parent

    def test_parent_journal_edit_after_recovery_validation_blocks_before_next_ddl(self):
        plan,review,parent=self.interrupted_pair()
        path=self.spent/('a'*32+'.events.jsonl')
        self.store.lock_hook=lambda store:path.write_bytes(path.read_bytes()+b'edited')
        self.blocked(lambda:self.recovery(plan,review,parent),'PARENT_JOURNAL_CHANGED')

    def test_edited_journal_is_not_authority_even_with_a_new_execution_signature(self):
        plan,review,parent=self.interrupted_pair()
        path=self.spent/('a'*32+'.events.jsonl')
        rows=path.read_bytes().splitlines()
        event=json.loads(rows[1])
        event['stepIndex']=1
        rows[1]=self.m.canonical(event)
        path.write_bytes(b'\n'.join(rows)+b'\n')
        self.blocked(lambda:self.recovery(plan,review,parent),'JOURNAL_INTEGRITY_REJECTED')

    def test_old_execution_signature_cannot_authorize_resume_or_nonce_reuse(self):
        plan,review,parent=self.interrupted_pair()
        before=self.connect_calls
        self.blocked(lambda:self.recovery(plan,review,parent,signed_grant=parent),'RECOVERY_GRANT_REJECTED')
        self.assertEqual(before,self.connect_calls)

    def test_resume_rechecks_snapshot_after_lock_and_preserves_recovery_nonce(self):
        plan,review,parent=self.interrupted_pair()
        self.store.lock_hook=lambda store:store.rows['wm_material_stock'][0].update(version=100)
        self.blocked(lambda:self.recovery(plan,review,parent),'SNAPSHOT_CHANGED')

    def test_lost_ack_after_second_ddl_can_confirm_exact_goal_without_reissuing_ddl(self):
        self.legacy_tx()
        plan=self.precheck()
        review,payload=self.grant(plan)
        parent=self.sign('execution',payload)
        self.store.apply_then_fail_on_table='wm_material_stock'
        with self.assertRaises(self.m.MigrationBlocked):
            self.execute(review=review,grant=parent)
        self.store.apply_then_fail_on_table=None
        self.store.alter_calls.clear()
        result=self.recovery(plan,review,parent)
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED',result['status'])
        self.assertEqual(2,result['verifiedPrefix'])
        self.assertEqual([],self.store.alter_calls)
        self.assertFalse(result['historicalPrecisionRecovered'])

    def test_decimal_scale_narrowing_is_not_disguised_as_native_widening(self):
        column=self.store.metadata['wm_material_stock_tx']['columns'][1]
        column.update(precision=14,scale=6,columnType='decimal(14,6)')
        self.blocked(self.precheck,'DECIMAL_CHANGE_IS_NOT_WIDENING')

    def test_tx_unknown_null_is_preserved_and_old_flags_and_unit_versions_are_not_filled(self):
        self.legacy_tx()
        self.store.rows['wm_material_stock_tx'][0]['before_quantity']=None
        self.refresh_backup()
        result=self.execute()
        self.assertIsNone(self.store.rows['wm_material_stock_tx'][0]['before_quantity'])
        self.assertEqual('UNPROVEN',self.store.rows['wm_material_stock_tx'][0]['quantity_origin'])
        self.assertIsNone(self.store.rows['wm_material_stock'][0]['quantity_contract_version'])
        self.assertIsNone(self.store.rows['wm_material_stock'][0]['base_uom_id'])
        self.assertEqual('NOT_PERFORMED',result['sourceCertification'])

    def test_cli_resume_dispatch_exists_and_missing_recovery_authority_never_connects(self):
        from contextlib import redirect_stdout
        from unittest.mock import patch
        import io
        root=self.directory
        args=['resume-once']
        for role,(public,pin) in self.keys.material.items():
            keyfile=root/(role+'.pub')
            keyfile.write_bytes(public)
            args+=['--'+role+'-public-key',str(keyfile),'--'+role+'-key-sha256',pin]
        for name,role,payload in (('maintenance','review',self.maintenance),('backup-receipt','backup',self.backup)):
            signed=self.sign(role,payload)
            file=root/(name+'.json')
            signature=root/(name+'.sig')
            file.write_bytes(signed.payload)
            signature.write_bytes(signed.signature)
            args+=['--'+name,str(file),'--'+name+'-signature',str(signature)]
        args+=['--backup-artifact',str(self.backup_file),'--restore-evidence',str(self.restore_file),
               '--fence-evidence',str(self.fence_file),'--output',str(root/'result.json')]
        stream=io.StringIO()
        with patch.object(self.m,'PRIVATE_ROOT',root),patch.object(self.m,'MySQLStore') as connect,redirect_stdout(stream):
            code=self.m.main(args)
        self.assertEqual(2,code)
        self.assertEqual('RECOVERY_AUTHORITY_REQUIRED',json.loads(stream.getvalue())['reason'])
        connect.assert_not_called()

    def test_missing_execution_scope_and_overwide_ddl_cannot_expand_authority(self):
        self.legacy_tx()
        plan=self.precheck()
        review,payload=self.grant(plan)
        missing=dict(payload)
        missing.pop('scope')
        self.blocked(lambda:self.execute(review=review,grant=self.sign('execution',missing)),'EXECUTION_GRANT_REJECTED')
        altered=copy.deepcopy(plan)
        altered['steps'][0]['ddl']='ALTER TABLE `ry-erp`.`wm_material_stock_tx` DROP COLUMN `quantity_origin`;'
        forged_review=self.sign('review',altered)
        _,new_payload=self.grant(altered)
        self.blocked(lambda:self.execute(review=forged_review,grant=self.sign('execution',new_payload)),'REVIEWED_PLAN_REJECTED')

    def test_recovery_from_original_checkpoint_zero_reexecutes_only_with_new_full_scope_grant(self):
        self.legacy_tx()
        plan=self.precheck()
        review,payload=self.grant(plan)
        parent=self.sign('execution',payload)
        self.store.fail_on_table='wm_material_stock_tx'
        with self.assertRaises(self.m.MigrationBlocked):
            self.execute(review=review,grant=parent)
        self.store.fail_on_table=None
        self.store.alter_calls.clear()
        result=self.recovery(plan,review,parent)
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED',result['status'])
        self.assertEqual(2,len(self.store.alter_calls))

    def test_repeat_interruption_can_resume_from_latest_signed_attempt_without_replay(self):
        plan,review,parent=self.interrupted_pair()
        self.store.fail_on_table='wm_material_stock'
        with self.assertRaises(self.m.MigrationBlocked):
            self.recovery(plan,review,parent)
        latest=self.spent/('b'*32+'.events.jsonl')
        events=self.m.read_journal(latest)
        self.assertEqual(1,events[0]['startPrefix'])
        self.assertTrue(any(event['event']=='DDL_INTENT' and event['stepIndex']==1 for event in events))
        self.store.fail_on_table=None
        self.store.alter_calls.clear()
        result=self.recovery(plan,review,self.last_recovery_grant,nonce='c'*32)
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED',result['status'])
        self.assertEqual(1,len(self.store.alter_calls))

    def test_final_readback_cannot_report_success_after_maintenance_window_expires(self):
        plan=self.precheck()
        goals=[]
        original=self.store.iter_rows
        def rows(table):
            columns=self.store.metadata['wm_material_stock']['columns']
            if table=='wm_material_stock' and columns[1]['type']=='decimal':
                goals.append(1)
            return original(table)
        self.store.iter_rows=rows
        def clock():
            return NOW if len(goals)<2 else NOW+timedelta(hours=1)
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.execute(plan,now=clock)
        self.assertTrue(caught.exception.ddl_attempted)
        self.assertEqual(0,self.store.rollback_calls)

    def test_review_probe_group_applier_running_but_show_slave_empty_is_not_zero(self):
        class Cursor:
            def execute(self,sql):pass
            def fetchall(self):return []
            def close(self):pass
        class Connection:
            def cursor(self,**kw):return Cursor()
        adapter=object.__new__(self.m.MySQLStore)
        adapter.connection=Connection()
        def query(sql,args=None):
            if 'performance_schema' in sql and 'applier_status' in sql:
                return [('group_replication_applier','ON')]
            return []
        adapter.query=query
        with self.assertRaises(self.m.MigrationBlocked):
            adapter.replication_appliers()

    def test_review_probe_nonquantity_stored_generated_version_is_rejected_before_ddl(self):
        self.legacy_tx()
        column=copy.deepcopy(self.store.metadata['wm_material_stock_tx']['columns'][0])
        column.update(name='version',type='int',extra='STORED GENERATED',
                      generationExpression='char_length(cast(`tx_quantity` as char))')
        self.store.metadata['wm_material_stock_tx']['columns'].append(column)
        self.store.rows['wm_material_stock_tx'][0]['version']=4
        self.blocked(self.precheck,'GENERATED_COLUMNS_UNSUPPORTED')

    def test_review_probe_implicit_compact_row_format_is_explicit_in_copy_ddl(self):
        self.legacy_tx()
        for table in ('wm_material_stock_tx','wm_material_stock'):
            self.store.metadata[table]['tableOptions']=['utf8mb4_unicode_ci','Compact','']
            self.store.metadata[table]['physicalStorage']['rowFormat']='Compact'
        self.refresh_backup()
        plan=self.precheck()
        for step in plan['steps']:
            self.assertIn('ROW_FORMAT=COMPACT',step['ddl'])

    def test_review_probe_ancestor_a0_cannot_bypass_later_b2_highwater_after_restore(self):
        self.legacy_tx()
        original_meta=copy.deepcopy(self.store.metadata)
        original_rows=copy.deepcopy(self.store.rows)
        plan=self.precheck()
        review,payload=self.grant(plan)
        parent=self.sign('execution',payload)
        self.store.apply_then_fail_on_table='wm_material_stock_tx'
        with self.assertRaises(self.m.MigrationBlocked):
            self.execute(review=review,grant=parent)
        self.store.apply_then_fail_on_table=None
        result=self.recovery(plan,review,parent)
        self.assertEqual(2,result['verifiedPrefix'])
        self.store.metadata=original_meta
        self.store.rows=original_rows
        self.store.alter_calls.clear()
        self.blocked(lambda:self.recovery(plan,review,parent,nonce='c'*32),'PLAN_PARENT_NOT_HEAD')

    def test_v2_maintenance_or_execution_signature_cannot_be_reused_under_v3(self):
        good=self.maintenance
        self.maintenance={**good,'kind':'stock-native-maintenance-v2'}
        self.blocked(self.precheck,'LEGACY_AUTHORIZATION_REJECTED')
        self.assertEqual(0,self.connect_calls)
        self.maintenance=good
        plan=self.precheck()
        review,payload=self.grant(plan)
        payload['kind']='stock-native-execution-v2'
        before=self.connect_calls
        self.blocked(lambda:self.execute(review=review,grant=self.sign('execution',payload)),'EXECUTION_GRANT_REJECTED')
        self.assertEqual(before,self.connect_calls)

    def test_latest_b2_also_cannot_restore_below_plan_monotonic_highwater(self):
        plan,review,parent=self.interrupted_pair()
        baseline_meta=copy.deepcopy(plan['baselineMetadata'])
        baseline_rows=copy.deepcopy(self.store.rows)
        baseline_rows['wm_material_stock_tx'][0].update(tx_quantity=Decimal('1.2500'),before_quantity=Decimal('0.0000'),after_quantity=Decimal('1.2500'))
        result=self.recovery(plan,review,parent)
        self.assertEqual(2,result['verifiedPrefix'])
        latest=self.last_recovery_grant
        self.store.metadata=baseline_meta
        self.store.rows=baseline_rows
        self.store.alter_calls.clear()
        self.blocked(lambda:self.recovery(plan,review,latest,nonce='c'*32),'PLAN_HIGH_WATERMARK_VIOLATION')

    def test_missing_plan_chain_log_or_unindexed_orphan_refuses_recovery(self):
        plan,review,parent=self.interrupted_pair()
        journal=self.spent/('a'*32+'.events.jsonl')
        original=journal.read_bytes()
        journal.unlink()
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.m.inspect_plan_history(plan=plan,reviewed_plan=review,keys=self.keys,receipt_dir=self.spent)
        self.assertEqual('PLAN_HISTORY_INCOMPLETE',caught.exception.code)
        journal.write_bytes(original)
        orphan=self.spent/('d'*32+'.events.jsonl')
        orphan.write_bytes(original)
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.m.inspect_plan_history(plan=plan,reviewed_plan=review,keys=self.keys,receipt_dir=self.spent)
        self.assertEqual('PLAN_HISTORY_INCOMPLETE',caught.exception.code)

    def test_same_plan_native_file_lock_blocks_parallel_resume(self):
        plan,review,parent=self.interrupted_pair()
        with self.m.plan_lock(self.spent,digest(review.payload)):
            self.blocked(lambda:self.recovery(plan,review,parent),'PLAN_LOCK_BUSY')

    def replication_adapter(self,**changes):
        adapter=object.__new__(self.m.MySQLStore)
        facts={'enabled':[(1,)],'plugins':[],'variables':[],
            'instruments':[{'NAME':name,'ENABLED':'YES'} for name in
                ('thread/sql/slave_sql','thread/sql/slave_worker','thread/sql/slave_io')],
            'configuration':[],'connections':[],'appliers':[],'coordinators':[],'workers':[],
            'threads':[],'ordinary':[],'members':[],'stats':[]}
        facts.update(changes)
        queries=[]
        def query(sql,args=None):
            queries.append(sql)
            if 'performance_schema' in sql:return facts['enabled']
            if 'server_uuid' in sql:return [(self.target['serverUuid'],)]
            raise AssertionError('未预期原生复制查询')
        def dictionary(sql,args=None):
            queries.append(sql)
            names={'information_schema.PLUGINS':'plugins','SHOW GLOBAL VARIABLES':'variables',
                   'setup_instruments':'instruments','replication_connection_configuration':'configuration',
                   'replication_connection_status':'connections','replication_applier_status_by_coordinator':'coordinators',
                   'replication_applier_status_by_worker':'workers','replication_applier_status':'appliers',
                   'performance_schema.threads':'threads','SHOW SLAVE STATUS':'ordinary',
                   'replication_group_members':'members','replication_group_member_stats':'stats'}
            for token,name in names.items():
                if token in sql:
                    value=facts[name]
                    if isinstance(value,Exception):raise value
                    return copy.deepcopy(value)
            raise AssertionError('未预期原生复制清点')
        adapter.query=query
        adapter.dictionary_query=dictionary
        return adapter,queries

    def test_ordinary_57_without_group_plugin_is_known_absent_not_unknown_or_disabled(self):
        adapter,queries=self.replication_adapter()
        self.assertEqual(0,adapter.replication_appliers())
        self.assertEqual('NOT_INSTALLED',adapter.replication_inventory['groupState'])
        self.assertEqual('COMPLETE',adapter.replication_inventory['status'])
        self.assertTrue(any('replication_applier_status_by_worker' in sql for sql in queries))
        self.assertTrue(any('information_schema.PLUGINS' in sql for sql in queries))
        self.assertTrue(any('performance_schema.threads' in sql for sql in queries))

    def test_group_or_parallel_worker_fact_overrides_empty_show_slave_status(self):
        channel={'CHANNEL_NAME':'group_replication_applier'}
        for facts in ({'appliers':[{**channel,'SERVICE_STATE':'ON'}]},
                      {'workers':[{**channel,'WORKER_ID':1,'THREAD_ID':81,'SERVICE_STATE':'ON'}]},
                      {'coordinators':[{**channel,'THREAD_ID':82,'SERVICE_STATE':'ON'}]}):
            base={'configuration':[channel],'connections':[{**channel,'SERVICE_STATE':'OFF'}],
                  'appliers':[{**channel,'SERVICE_STATE':'OFF'}]}
            base.update(facts)
            adapter,_=self.replication_adapter(**base)
            with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.replication_appliers()
            self.assertEqual('WRITER_FENCE_MISSING',caught.exception.code)

    def test_disabled_ps_or_instrument_permission_or_group_status_is_unknown_not_zero(self):
        examples=({'enabled':[(0,)]},{'workers':RuntimeError('私有拒绝读取')},
                  {'instruments':[{'NAME':'thread/sql/slave_sql','ENABLED':'NO'}]},
                  {'plugins':[{'PLUGIN_NAME':'group_replication','PLUGIN_STATUS':'DISABLED'}]},
                  {'plugins':[{'PLUGIN_NAME':'group_replication','PLUGIN_STATUS':'ACTIVE'}],
                   'variables':[{'Variable_name':'group_replication_start_on_boot','Value':'OFF'}],'members':[]})
        for facts in examples:
            with self.subTest(facts=facts):
                adapter,_=self.replication_adapter(**facts)
                with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.replication_appliers()
                self.assertIn(caught.exception.code,('REPLICATION_INVENTORY_UNKNOWN','GROUP_REPLICATION_STATUS_UNKNOWN'))
                self.assertNotIn('私有',str(caught.exception))

    def test_all_generation_metadata_and_external_dependency_closure_are_mandatory(self):
        column=self.store.metadata['wm_material_stock']['columns'][-2]
        expression=column.pop('generationExpression')
        self.blocked(self.precheck,'GENERATION_METADATA_UNKNOWN')
        column['generationExpression']=expression
        self.store.metadata['wm_material_stock']['inboundForeignKeys']=[['other','generated_dependency']]
        self.blocked(self.precheck,'DEPENDENCY_CLOSURE_UNSUPPORTED')

    def test_unknown_compressed_general_tablespace_and_default_options_are_rejected(self):
        good=copy.deepcopy(self.store.metadata['wm_material_stock']['physicalStorage'])
        for change in ({'spaceType':'General'},{'zipPageSize':8192},{'rowFormat':'Compressed'}):
            self.store.metadata['wm_material_stock']['physicalStorage']={**good,**change}
            self.blocked(self.precheck,'STORAGE_ATTRIBUTES_UNSUPPORTED')
        self.store.metadata['wm_material_stock']['physicalStorage']=good
        self.store.metadata['wm_material_stock']['tableOptions'][2]="compression='zlib'"
        self.blocked(self.precheck,'STORAGE_ATTRIBUTES_UNSUPPORTED')

    def test_adapter_refuses_dependency_visibility_not_proven_by_native_global_grants(self):
        adapter=object.__new__(self.m.MySQLStore)
        adapter.target=self.target
        adapter.query=lambda sql,args=None:[('GRANT SELECT ON `ry-erp`.* TO local_fixture',)] if sql=='SHOW GRANTS' else []
        with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.describe()
        self.assertEqual('DEPENDENCY_VISIBILITY_UNKNOWN',caught.exception.code)

    def test_adapter_native_column_expressions_storage_and_inbound_dependencies_are_read(self):
        queries=[]
        adapter=object.__new__(self.m.MySQLStore)
        adapter.target=self.target
        def query(sql,args=None):
            queries.append(sql)
            if sql=='SHOW GRANTS':return [('GRANT ALL PRIVILEGES ON *.* TO fixture',)]
            if 'information_schema.TABLES' in sql:return [('InnoDB','utf8mb4_unicode_ci','Compact','')]
            if 'information_schema.COLUMNS' in sql:return [('stock_id','bigint',19,0,'NO',None,'库存','', 'bigint(20)','')]
            if 'INNODB_SYS_TABLES' in sql:
                return [('Compact','Single',0,16384,'Single',0)] if args in (
                    ('ry@002derp/wm_material_stock',),('ry@002derp/wm_material_stock_tx',)) else []
            if 'innodb_file_per_table' in sql:return [(1,)]
            if sql.startswith('SHOW CREATE TABLE'):return [(args or 'fixture', 'CREATE TABLE `fixture` (`stock_id` bigint) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')]
            return []
        adapter.query=query
        metadata=adapter.describe()
        self.assertTrue(any('GENERATION_EXPRESSION' in sql for sql in queries))
        self.assertTrue(any('REFERENCED_TABLE_SCHEMA=%s AND REFERENCED_TABLE_NAME=%s' in sql for sql in queries))
        self.assertEqual('',metadata['wm_material_stock']['columns'][0]['generationExpression'])
        self.assertEqual('Compact',metadata['wm_material_stock']['physicalStorage']['rowFormat'])

    def test_execution_history_binding_cannot_be_missing_or_stale_despite_new_signature(self):
        plan=self.precheck()
        review,payload=self.grant(plan)
        for field in ('planHistorySha256','planHeadExecutionSha256','planHighWatermark'):
            changed=dict(payload)
            changed.pop(field)
            self.blocked(lambda:self.execute(review=review,grant=self.sign('execution',changed)),'EXECUTION_GRANT_REJECTED')
        changed={**payload,'planHistorySha256':'f'*64}
        self.blocked(lambda:self.execute(review=review,grant=self.sign('execution',changed)),'PLAN_HISTORY_GRANT_REJECTED')

    def test_real_mysql_adapter_cursor_group_probe_records_native_sql_not_fixture_counts(self):
        seen=[]
        class Cursor:
            with_rows=True
            def __init__(cursor,dictionary):cursor.dictionary=dictionary
            def execute(cursor,sql,args=None):
                seen.append(sql)
                if sql.startswith('SELECT DATABASE()'):
                    cursor.rows=[('ry-erp',3306,self.target['serverUuid'],71,'fixture-server','5.7.44',1,0,'OFF')]
                elif sql=='SELECT @@GLOBAL.performance_schema':cursor.rows=[(1,)]
                elif 'information_schema.PLUGINS' in sql:cursor.rows=[{'PLUGIN_NAME':'group_replication','PLUGIN_STATUS':'ACTIVE'}]
                elif sql.startswith('SHOW GLOBAL VARIABLES'):cursor.rows=[{'Variable_name':'group_replication_start_on_boot','Value':'OFF'}]
                elif 'setup_instruments' in sql:cursor.rows=[{'NAME':name,'ENABLED':'YES'} for name in
                    ('thread/sql/slave_sql','thread/sql/slave_worker','thread/sql/slave_io','thread/group_rpl/THD_applier_module')]
                elif 'replication_connection_configuration' in sql:cursor.rows=[{'CHANNEL_NAME':'group_replication_applier'}]
                elif 'replication_connection_status' in sql:cursor.rows=[{'CHANNEL_NAME':'group_replication_applier','SERVICE_STATE':'OFF'}]
                elif sql.endswith('performance_schema.replication_applier_status'):cursor.rows=[{'CHANNEL_NAME':'group_replication_applier','SERVICE_STATE':'ON'}]
                else:cursor.rows=[]
            def fetchall(cursor):return cursor.rows
            def close(cursor):pass
        class Connection:
            def cursor(connection,**kwargs):return Cursor(kwargs.get('dictionary',False))
        adapter=object.__new__(self.m.MySQLStore)
        adapter.connection=Connection()
        adapter.target=self.target
        with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.observe_identity()
        self.assertEqual('WRITER_FENCE_MISSING',caught.exception.code)
        self.assertIn('SHOW SLAVE STATUS',seen)
        self.assertIn('SELECT CHANNEL_NAME,SERVICE_STATE FROM performance_schema.replication_applier_status',seen)
        self.assertTrue(any('replication_applier_status_by_worker' in sql for sql in seen))

    def test_validate_metadata_directly_rejects_nonnumeric_stored_generated_before_copy(self):
        self.legacy_tx()
        column=copy.deepcopy(self.store.metadata['wm_material_stock_tx']['columns'][0])
        column.update(name='version',type='int',columnType='int(11)',nullable=False,default=None,
                      extra='STORED GENERATED',generationExpression='CHAR_LENGTH(CAST(tx_quantity AS CHAR))')
        self.store.metadata['wm_material_stock_tx']['columns'].append(column)
        self.store.rows['wm_material_stock_tx'][0]['version']=4
        with self.assertRaises(self.m.MigrationBlocked) as caught:self.m.validate_metadata(self.store.metadata)
        self.assertEqual('GENERATED_COLUMNS_UNSUPPORTED',caught.exception.code)
        self.assertEqual([],self.store.alter_calls)

    def test_explicit_create_options_change_is_modeled_not_erased_by_checkpoint_hash(self):
        for table in ('wm_material_stock_tx','wm_material_stock'):
            item=self.store.metadata[table]
            item['tableOptions']=['utf8mb4_general_ci','Compact','']
            item['physicalStorage']['rowFormat']='Compact'
            item['tableDefinitionOptions']='ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci'
        original=self.m.capture_snapshot(self.store,'PRESERVE_NULL_UNKNOWN')
        item=self.store.metadata['wm_material_stock_tx']
        item['tableOptions'][2]='row_format=compact'
        item['tableDefinitionOptions']+=' ROW_FORMAT=COMPACT'
        explicit=self.m.capture_snapshot(self.store,'PRESERVE_NULL_UNKNOWN')
        self.assertNotEqual(original['schemaSha256'],explicit['schemaSha256'])
        self.assertNotEqual(original['snapshotSha256'],explicit['snapshotSha256'])

    def test_original_minimal_a0_b2_restore0_c2h_probe_rejects_ancestor_with_no_alter(self):
        self.legacy_tx()
        plan=self.precheck()
        review,payload=self.grant(plan)
        a=self.sign('execution',payload)
        cp0=copy.deepcopy((self.store.metadata,self.store.rows))
        self.store.apply_then_fail_on_table=self.m.TX
        with self.assertRaises(self.m.MigrationBlocked):self.execute(review=review,grant=a)
        self.store.apply_then_fail_on_table=None
        b=self.recovery(plan,review,a,nonce='b'*32,current=NOW+timedelta(hours=1))
        self.assertEqual(2,b['verifiedPrefix'])
        self.store.metadata,self.store.rows=copy.deepcopy(cp0)
        self.store.alter_calls.clear()
        self.blocked(lambda:self.recovery(plan,review,a,nonce='c'*32,current=NOW+timedelta(hours=2)), 'PLAN_PARENT_NOT_HEAD')

    def test_truncated_plan_index_and_removed_child_logs_cannot_reset_native_head(self):
        plan,review,parent=self.interrupted_pair()
        index=self.spent/('.plan-'+digest(review.payload)+'.history.jsonl')
        before=index.read_bytes()
        self.recovery(plan,review,parent)
        index.write_bytes(before)
        (self.spent/('b'*32+'.events.jsonl')).unlink()
        (self.spent/('b'*32+'.json')).unlink()
        with self.assertRaises(self.m.MigrationBlocked) as caught:
            self.m.inspect_plan_history(plan=plan,reviewed_plan=review,keys=self.keys,receipt_dir=self.spent)
        self.assertEqual('PLAN_HISTORY_INCOMPLETE',caught.exception.code)

    def test_active_group_plugin_without_registered_group_thread_visibility_is_unknown(self):
        adapter,_=self.replication_adapter(
            plugins=[{'PLUGIN_NAME':'group_replication','PLUGIN_STATUS':'ACTIVE'}],
            variables=[{'Variable_name':'group_replication_start_on_boot','Value':'OFF'}],
            members=[{'CHANNEL_NAME':'group_replication_applier','MEMBER_ID':self.target['serverUuid'],'MEMBER_STATE':'OFFLINE'}])
        with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.replication_appliers()
        self.assertEqual('GROUP_REPLICATION_STATUS_UNKNOWN',caught.exception.code)

    def test_describe_captures_native_generated_expression_without_clearing_it(self):
        adapter=object.__new__(self.m.MySQLStore)
        adapter.target=self.target
        expression='CHAR_LENGTH(CAST(tx_quantity AS CHAR))'
        def query(sql,args=None):
            table=args[1] if args and len(args)>1 else 'wm_material_stock'
            primary='tx_id' if table=='wm_material_stock_tx' else 'stock_id'
            if sql=='SHOW GRANTS':return [('GRANT ALL PRIVILEGES ON *.* TO fixture',)]
            if 'information_schema.TABLES' in sql:return [('InnoDB','utf8mb4_general_ci','Compact','')]
            if 'information_schema.COLUMNS' in sql:
                rows=[(primary,'bigint',19,0,'NO',None,'原主键','', 'bigint(20)','')]
                if table=='wm_material_stock_tx':rows.append(('version','int',10,0,'NO',None,'原版本','STORED GENERATED','int(11)',expression))
                return rows
            if 'information_schema.STATISTICS' in sql:return [('PRIMARY',0,1,primary,None,'BTREE')]
            if 'INNODB_SYS_TABLES' in sql:
                return [('Compact','Single',0,16384,'Single',0)] if args in (
                    ('ry@002derp/wm_material_stock',),('ry@002derp/wm_material_stock_tx',)) else []
            if 'innodb_file_per_table' in sql:return [(1,)]
            if sql.startswith('SHOW CREATE TABLE'):return [('fixture','CREATE TABLE `fixture` (`id` bigint) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')]
            return []
        adapter.query=query
        metadata=adapter.describe()
        column=metadata['wm_material_stock_tx']['columns'][-1]
        self.assertEqual(expression,column['generationExpression'])
        self.assertEqual('STORED GENERATED',column['extra'])

    def test_compact_copy_checkpoint_matches_native_lowercase_explicit_create_options(self):
        self.legacy_tx()
        self.store.serverDefaultRowFormat='Dynamic'
        for item in self.store.metadata.values():
            item['tableOptions']=['utf8mb4_general_ci','Compact','']
            item['physicalStorage']['rowFormat']='Compact'
            item['tableDefinitionOptions']='ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci'
        self.refresh_backup()
        plan=self.precheck()
        original=self.store.alter
        def alter(sql):
            self.assertIn('ROW_FORMAT=COMPACT',sql)
            original(sql)
            table='wm_material_stock_tx' if '`wm_material_stock_tx`' in sql else 'wm_material_stock'
            self.store.metadata[table]['tableOptions'][2]='row_format=compact'
        self.store.alter=alter
        result=self.execute(plan)
        self.assertEqual('MIGRATED_SOURCE_UNCERTIFIED',result['status'])
        self.assertEqual(plan['checkpoints'][-1],result['afterSnapshotSha256'])
        self.assertTrue(all(item['tableOptions'][1]=='Compact' and item['tableOptions'][2]=='row_format=compact'
                            for item in self.store.metadata.values()))

    def native_directory_adapter(self,*,catalogue=None,schema='ry-erp'):
        # 真正调用MySQLStore.query/describe；目录行只按实际SQL参数返回，不按SQL子串放行。
        seen=[]
        expected={'ry@002derp/wm_material_stock':[('Compact','Single',0,16384,'Single',0)],
                  'ry@002derp/wm_material_stock_tx':[('Compact','Single',0,16384,'Single',0)]}
        rows=expected if catalogue is None else catalogue
        tables=('wm_material_stock','wm_material_stock_tx')
        class Cursor:
            with_rows=True
            def execute(cursor,sql,args=None):
                seen.append((sql,args))
                if sql=='SHOW GRANTS':cursor.rows=[('GRANT ALL PRIVILEGES ON *.* TO fixture',)]
                elif 'information_schema.TABLES' in sql:
                    cursor.rows=[('InnoDB','utf8mb4_general_ci','Compact','')] if args in [('ry-erp',table) for table in tables] else []
                elif 'information_schema.COLUMNS' in sql:
                    if args not in [('ry-erp',table) for table in tables]:cursor.rows=[]
                    else:
                        cursor.rows=[(col['name'],col['type'],col['precision'],col['scale'],'YES' if col['nullable'] else 'NO',
                                      col['default'],col['comment'],col['extra'],col['columnType'],col['generationExpression'])
                                     for col in self.store.metadata[args[1]]['columns']]
                elif 'information_schema.STATISTICS' in sql:
                    cursor.rows=[('PRIMARY',0,1,'stock_id' if args[1]=='wm_material_stock' else 'tx_id',None,'BTREE')]
                elif 'INNODB_SYS_TABLES' in sql:
                    # 旧ry-erp/...、未知大小写或Windows目录变体没有精确native映射，必须零行。
                    cursor.rows=copy.deepcopy(rows.get(args[0],[])) if isinstance(args,tuple) and len(args)==1 else []
                elif sql=='SELECT @@GLOBAL.innodb_file_per_table':cursor.rows=[(1,)]
                elif sql in [f'SHOW CREATE TABLE `ry-erp`.`{table}`' for table in tables]:
                    cursor.rows=[('fixture','CREATE TABLE `fixture` (`id` bigint) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')]
                elif 'KEY_COLUMN_USAGE' in sql or 'information_schema.TRIGGERS' in sql:cursor.rows=[]
                else:raise AssertionError('未预期的native目录查询')
            def fetchall(cursor):return cursor.rows
            def close(cursor):pass
        class Connection:
            def cursor(connection,**kwargs):return Cursor()
        adapter=object.__new__(self.m.MySQLStore)
        adapter.connection=Connection()
        adapter.target={**self.target,'schema':schema}
        return adapter,seen

    def test_native_directory_encoding_real_cursor_binds_exact_two_reviewed_internal_names(self):
        adapter,seen=self.native_directory_adapter()
        metadata=adapter.describe()
        directory=[(sql,args) for sql,args in seen if 'INNODB_SYS_TABLES' in sql]
        self.assertEqual([('ry@002derp/wm_material_stock',),('ry@002derp/wm_material_stock_tx',)],
                         [args for sql,args in directory])
        self.assertTrue(all('WHERE BINARY t.NAME=BINARY %s' in sql for sql,args in directory))
        self.assertEqual('Compact',metadata['wm_material_stock']['physicalStorage']['rowFormat'])
        self.assertEqual('Compact',metadata['wm_material_stock_tx']['physicalStorage']['rowFormat'])
        self.assertFalse(any(args and isinstance(args,tuple) and str(args[0]).startswith('ry-erp/') for sql,args in directory))

    def test_native_directory_unknown_schema_or_case_is_not_guessed_even_on_windows(self):
        for schema in ('RY-ERP','Ry-Erp','ry–erp','ry_erp','ry%002derp','ry@002derp'):
            with self.subTest(schema=schema):
                adapter,seen=self.native_directory_adapter(schema=schema)
                with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.describe()
                self.assertEqual('NATIVE_DIRECTORY_NAME_UNSUPPORTED',caught.exception.code)
                self.assertEqual([],seen)

    def test_native_directory_empty_multiple_or_windows_case_variant_rows_still_refuse(self):
        row=('Compact','Single',0,16384,'Single',0)
        for table in ('wm_material_stock','wm_material_stock_tx'):
            for native_rows in ([],[row,row]):
                catalogue={'ry@002derp/wm_material_stock':[row],'ry@002derp/wm_material_stock_tx':[row]}
                catalogue['ry@002derp/'+table]=native_rows
                adapter,seen=self.native_directory_adapter(catalogue=catalogue)
                with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.describe()
                self.assertEqual('STORAGE_ATTRIBUTES_UNKNOWN',caught.exception.code)
        for unreviewed in ('RY@002DERP/wm_material_stock','ry@002derp/Wm_Material_Stock','ry@002derp\\wm_material_stock'):
            adapter,_=self.native_directory_adapter(catalogue={unreviewed:[row]})
            with self.assertRaises(self.m.MigrationBlocked) as caught:adapter.describe()
            self.assertEqual('STORAGE_ATTRIBUTES_UNKNOWN',caught.exception.code)

    def test_native_contract_for_formal_preflight_requires_exact_width_and_scale(self):
        for precision, scale in ((18, 4), (28, 12), (18, 2), (12, 4)):
            columns = {name: {'type': 'decimal', 'precision': precision, 'scale': scale}
                       for name in ('stock_quantity', 'reserved_quantity')}
            gaps = self.m.native_precision_gaps('ry-erp.wm_material_stock', columns)
            self.assertEqual(not (precision == 18 and scale == 4), bool(gaps))


if __name__ == '__main__':
    unittest.main()
