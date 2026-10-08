"""Fixed authorized SOP target regressions; fake cursors/settings, no live I/O.

S3Settings is mocked only at batch's constructor boundary because storage.py is
separately owned and may still enforce the earlier local target during this task.
All configuration gates must therefore be enforced by batch itself.
"""
import copy
import hashlib
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import batch
from test_batch import fixture, artifacts, applied

ENDPOINT = '159.75.14.226:9005'
BUCKET = 'sop'
CONFIG_KEY = 'sop'


def config(**changes):
    row = {
        'oss_config_id': 3200000000000000001,
        'tenant_id': batch.TENANT,
        'config_key': CONFIG_KEY,
        'endpoint': ENDPOINT,
        'bucket_name': BUCKET,
        'access_key': 'offline-access',
        'secret_key': 'offline-secret',
        'region': '',
        'status': '1',
        'access_policy': '0',
        'is_https': 'N',
        'prefix': '',
        'domain': '',
    }
    row.update(changes)
    return row


class ContextConnection:
    """Return synthetic query results and reject every non-SELECT operation."""
    def __init__(self, configs=None):
        self.configs = copy.deepcopy([config()] if configs is None else configs)
        self.default_config = config(oss_config_id=1, config_key='minio',
                                     endpoint='127.0.0.1:9005', bucket_name='ruoyi', status='0')
        self.statements = []

    def cursor(self):
        return ContextCursor(self)


class ContextCursor:
    def __init__(self, connection):
        self.connection = connection
        self.query = ''

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def execute(self, query, args=None):
        if not query.lstrip().upper().startswith('SELECT '):
            raise AssertionError('Context must never write or switch the default config')
        self.query = query
        self.connection.statements.append((query, args))

    def fetchone(self):
        if '@@server_uuid' not in self.query:
            raise AssertionError('Unexpected context identity query')
        return {'server_uuid': 'offline-server-uuid', 'version': '5.7.44-offline'}

    def fetchall(self):
        if 'information_schema.TABLES' in self.query:
            return [{'TABLE_SCHEMA': schema, 'TABLE_NAME': name, 'ENGINE': 'InnoDB'}
                    for schema, names in [('ry-vue', ('sys_oss', 'sys_oss_config', 'es_signature')),
                                          ('ry-mes', ('sop_document', 'sop_version', 'sop_process_binding', 'sop_view_log'))]
                    for name in names]
        if '`sys_oss_config`' in self.query:
            return copy.deepcopy(self.connection.configs)
        if '`sys_dict_data`' in self.query:
            return [{'dict_value': value} for value in ('PRODUCTION', 'INSPECTION', 'CLEAN')]
        raise AssertionError('Unexpected context query')


class PrivateTargetBatchTests(unittest.TestCase):
    def accepted_context(self, row=None):
        conn = ContextConnection([config() if row is None else row])
        settings = object()
        with patch.object(batch, 'S3Settings', return_value=settings) as constructor:
            try:
                context, actual_settings = batch.read_context(conn)
            except batch.SafetyError as error:
                self.fail('Authorized independent private SOP config was rejected: ' + str(error))
        self.assertIs(settings, actual_settings)
        return context, conn, constructor

    def assert_config_rejected(self, rows, category):
        conn = ContextConnection(rows)
        before = copy.deepcopy((conn.configs, conn.default_config))
        with patch.object(batch, 'S3Settings') as constructor:
            with self.assertRaisesRegex(batch.SafetyError, '^' + category + '$'):
                batch.read_context(conn)
        constructor.assert_not_called()
        self.assertEqual(before, (conn.configs, conn.default_config))
        self.assertTrue(all(sql.startswith('SELECT ') for sql, _ in conn.statements))

    def test_all_57_oss_rows_select_sop_service_not_default_minio(self):
        source = fixture()
        rows = batch.plan_rows(source, artifacts(source))
        self.assertEqual(57, len(rows))
        self.assertEqual({CONFIG_KEY}, {row['oss']['service'] for row in rows})

    def test_all_57_oss_urls_use_exact_authorized_remote_private_bucket(self):
        source = fixture()
        items = artifacts(source)
        rows = batch.plan_rows(source, items)
        self.assertEqual(57, len(rows))
        for row, item in zip(rows, items):
            with self.subTest(sop_id=row['sopId']):
                self.assertEqual('http://' + ENDPOINT + '/' + BUCKET + '/' + item['objectKey'], row['oss']['url'])
                self.assertEqual(item['objectKey'], row['oss']['file_name'])

    def test_artifact_metadata_cannot_redirect_the_immutable_planning_target(self):
        source = fixture()
        items = artifacts(source)
        for item in items:
            item.update(endpoint='other-remote:9005', bucket='ruoyi', configKey='minio',
                        service='minio', url='http://other-remote:9005/ruoyi/file.pdf')
        for row in batch.plan_rows(source, items):
            self.assertEqual(CONFIG_KEY, row['oss']['service'])
            self.assertEqual('http://' + ENDPOINT + '/' + BUCKET + '/' + row['oss']['file_name'], row['oss']['url'])

    def test_context_query_scopes_tenant_and_independent_sop_key(self):
        conn = ContextConnection()
        with patch.object(batch, 'S3Settings'):
            try:
                batch.read_context(conn)
            except batch.SafetyError:
                pass  # Inspect the query even before the new status gate exists.
        queries = [(sql, args) for sql, args in conn.statements if '`sys_oss_config`' in sql]
        self.assertEqual(1, len(queries))
        sql, args = queries[0]
        self.assertIn('tenant_id=%s', sql)
        self.assertEqual(batch.TENANT, args[0])
        literal = re.search(r"config_key\s*=\s*'([^']+)'", sql)
        if literal:
            self.assertEqual(CONFIG_KEY, literal.group(1))
            self.assertEqual((batch.TENANT,), args)
        else:
            self.assertIn('config_key=%s', sql)
            self.assertEqual((batch.TENANT, CONFIG_KEY), args)

    def test_context_accepts_private_nondefault_status_one(self):
        context, conn, constructor = self.accepted_context()
        self.assertEqual(ENDPOINT, context['endpoint'])
        self.assertEqual(BUCKET, context['bucket'])
        self.assertEqual('PRIVATE', context['accessPolicy'])
        self.assertEqual(CONFIG_KEY, context['configKey'])
        constructor.assert_called_once_with(ENDPOINT, BUCKET, 'offline-access', 'offline-secret', 'us-east-1')
        self.assertEqual('1', conn.configs[0]['status'])
        self.assertEqual('0', conn.default_config['status'])

    def test_status_zero_is_rejected_not_promoted_or_made_default(self):
        self.assert_config_rejected([config(status='0')], 'EXPECTED_PRIVATE_SOP_CONFIG_REQUIRED')

    def test_other_or_coerced_statuses_are_rejected(self):
        for status in ('2', '', None, 1, True):
            with self.subTest(status=status):
                self.assert_config_rejected([config(status=status)], 'EXPECTED_PRIVATE_SOP_CONFIG_REQUIRED')

    def test_missing_or_duplicate_sop_config_is_rejected(self):
        for rows in ([], [config(), config(oss_config_id=3200000000000000002)]):
            with self.subTest(count=len(rows)):
                self.assert_config_rejected(rows, 'UNIQUE_SOP_CONFIG_REQUIRED')

    def test_wrong_config_key_or_tenant_is_rejected_even_if_returned_by_cursor(self):
        for changes in ({'config_key': 'minio'}, {'config_key': 'other'}, {'tenant_id': 'FOREIGN'}):
            with self.subTest(changes=changes):
                self.assert_config_rejected([config(**changes)], 'EXPECTED_PRIVATE_SOP_CONFIG_REQUIRED')

    def test_local_or_arbitrary_remote_endpoints_and_other_buckets_are_rejected(self):
        for changes in ({'endpoint': '127.0.0.1:9005'}, {'endpoint': 'localhost:9005'},
                        {'endpoint': '159.75.14.227:9005'}, {'endpoint': '159.75.14.226:9000'},
                        {'endpoint': 'http://' + ENDPOINT}, {'endpoint': ENDPOINT + '/'},
                        {'bucket_name': 'ruoyi'}, {'bucket_name': 'SOP'}, {'bucket_name': 'other'}):
            with self.subTest(changes=changes):
                self.assert_config_rejected([config(**changes)], 'EXPECTED_SOP_STORAGE_TARGET_REQUIRED')

    def test_public_policy_https_prefix_or_domain_is_rejected_without_mutation(self):
        for changes in ({'access_policy': '1'}, {'access_policy': '2'}, {'access_policy': 0},
                        {'is_https': 'Y'}, {'prefix': 'mes'}, {'domain': 'https://other-remote'},
                        {'prefix': 0}, {'domain': False}):
            with self.subTest(changes=changes):
                self.assert_config_rejected([config(**changes)], 'EXPECTED_PRIVATE_SOP_CONFIG_REQUIRED')

    def test_only_empty_or_us_east_1_region_is_accepted_and_normalized(self):
        for region in ('', None, 'us-east-1'):
            with self.subTest(region=region):
                _, _, constructor = self.accepted_context(config(region=region, prefix=None, domain=None))
                constructor.assert_called_once_with(ENDPOINT, BUCKET, 'offline-access', 'offline-secret', 'us-east-1')
        for region in ('cn-north-1', 'us-east-2', 'US-EAST-1', ' ', 0):
            with self.subTest(region=region):
                self.assert_config_rejected([config(region=region)], 'EXPECTED_SOP_REGION_REQUIRED')

    def test_context_fingerprints_config_identity_without_credentials(self):
        context, _, _ = self.accepted_context()
        self.assertEqual(batch.canonical_digest(config()['oss_config_id']), context['configIdFingerprint'])
        encoded = json.dumps(context)
        for forbidden in ('offline-access', 'offline-secret', 'access_key', 'secret_key', str(config()['oss_config_id'])):
            self.assertNotIn(forbidden, encoded)
        self.assertEqual(CONFIG_KEY, context['configKey'])

    def test_recreated_config_invalidates_the_context_fingerprint(self):
        first, _, _ = self.accepted_context()
        recreated, _, _ = self.accepted_context(config(oss_config_id=3200000000000000002))
        self.assertNotEqual(first['configIdFingerprint'], recreated['configIdFingerprint'])
        self.assertFalse(batch._same(first, recreated))
        as_string, _, _ = self.accepted_context(config(oss_config_id=str(config()['oss_config_id'])))
        self.assertEqual(first, as_string)

    def test_context_reads_never_change_status_or_the_existing_default(self):
        context, conn, _ = self.accepted_context()
        self.assertEqual(CONFIG_KEY, context['configKey'])
        self.assertTrue(all(sql.startswith('SELECT ') for sql, _ in conn.statements))
        self.assertFalse(any('changeStatus' in sql or 'UPDATE' in sql.upper() for sql, _ in conn.statements))
        self.assertEqual(config(), conn.configs[0])
        self.assertEqual(config(oss_config_id=1, config_key='minio', endpoint='127.0.0.1:9005',
                                bucket_name='ruoyi', status='0'), conn.default_config)

    def test_reviewed_manifest_cannot_reuse_old_target_or_recreated_config_context(self):
        context, _, _ = self.accepted_context()
        old_target = {**context, 'endpoint': '127.0.0.1:9005', 'bucket': 'ruoyi', 'configKey': 'minio'}
        old_identity = {**context, 'configIdFingerprint': batch.canonical_digest(3200000000000000002)}
        for stale_context in (old_target, old_identity):
            with self.subTest(context=stale_context), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                source = fixture()
                manifest = {'batch': batch.BATCH, 'tenantId': batch.TENANT,
                            'sourceDigest': batch.canonical_digest(source),
                            'context': stale_context, 'artifacts': artifacts(source)}
                batch.save_private_json(directory / 'backup.json', source)
                batch.save_private_json(directory / 'manifest.json', manifest)
                approved = hashlib.sha256((directory / 'manifest.json').read_bytes()).hexdigest()
                with patch.object(batch, 'validate_expected_pdf') as inspect:
                    with self.assertRaisesRegex(batch.SafetyError, '^DATABASE_OR_MINIO_IDENTITY_CHANGED$'):
                        batch._load_prepared(directory, context, approved)
                inspect.assert_not_called()

    def test_database_write_uses_sop_service_and_never_changes_config_or_default(self):
        source = fixture()
        rows = batch.plan_rows(source, artifacts(source))
        statements = []
        allowed = ('SELECT GET_LOCK(', 'SELECT RELEASE_LOCK(',
                   'SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ',
                   'INSERT INTO `ry-vue`.`sys_oss` ', 'INSERT INTO `ry-mes`.`sop_version` ',
                   'UPDATE `ry-mes`.`sop_document` ', 'SELECT * FROM `ry-vue`.`sys_oss` ')

        class Cursor:
            rowcount = 1
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def execute(self, sql, args=None):
                if not sql.startswith(allowed):
                    raise AssertionError('Unexpected write or config/default mutation')
                statements.append((sql, args))
            def fetchone(self): return {'acquired': 1}
            def fetchall(self): return [copy.deepcopy(row['oss']) for row in rows]

        class Connection:
            commits = 0
            def cursor(self): return Cursor()
            def begin(self): pass
            def commit(self): self.commits += 1
            def rollback(self): pass

        conn = Connection()
        with patch.object(batch, 'read_snapshot', side_effect=[source, applied(source, rows)]):
            batch.write_database(conn, source, rows)
        self.assertEqual(1, conn.commits)
        inserts = [args for sql, args in statements if sql.startswith('INSERT INTO `ry-vue`.`sys_oss` ')]
        self.assertEqual(57, len(inserts))
        self.assertEqual({CONFIG_KEY}, {args[7] for args in inserts})
        self.assertTrue(all(args[5].startswith('http://' + ENDPOINT + '/' + BUCKET + '/') for args in inserts))
        self.assertFalse(any('sys_oss_config' in sql or 'changeStatus' in sql for sql, _ in statements))
        self.assertEqual(source, fixture())


if __name__ == '__main__':
    unittest.main()
