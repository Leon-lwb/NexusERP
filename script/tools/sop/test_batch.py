"""Safety gates and transaction fault tests; never contact live services."""
import copy
import dataclasses
import hashlib
import json
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'rebuild'))
from catalog import PROCESSES
from batch import (SafetyError, DBSettings, S3Settings, validate_source, plan_rows,
                   canonical_digest, verify_applied, write_database, load_db_settings,
                   safe_output_dir, verify_object_bytes, load_manifest, save_private_json,
                   classify_existing_batch)


def fixture():
    documents = []
    versions = []
    bindings = []
    for i, (code, name, *_rest) in enumerate(PROCESSES, 1):
        documents.append({'sop_id': i, 'tenant_id': '000000', 'sop_code': f'SOP20260914{i:03}',
                          'sop_name': name + '作业指导书', 'sop_type': 'PROCESS', 'current_version': 'V1.0',
                          'status': 'PUBLISHED', 'file_oss_id': 0, 'file_type': 'PDF', 'del_flag': '0'})
        versions.append({'version_id': i, 'tenant_id': '000000', 'sop_id': i, 'version_no': 'V1.0',
                         'file_oss_id': 0, 'file_hash': None, 'file_type': 'PDF', 'status': 'CURRENT', 'del_flag': '0'})
        bindings.append({'binding_id': i, 'tenant_id': '000000', 'sop_id': i, 'process_code': code,
                         'process_name': name, 'version_no': '1', 'del_flag': '0'})
    document_fields = 'sop_id tenant_id sop_code sop_name sop_type current_version status file_oss_id file_type description approval_by approval_time approval_remark archive_reason remark create_dept create_by create_time update_by update_time del_flag'.split()
    version_fields = 'version_id tenant_id sop_id version_no file_oss_id file_type file_hash change_log published_time published_by status remark create_dept create_by create_time update_by update_time del_flag'.split()
    binding_fields = 'binding_id tenant_id process_id process_code process_name process_category sop_id version_no is_default sort_order remark create_dept create_by create_time update_by update_time del_flag'.split()
    for collection, fields in [(documents,document_fields),(versions,version_fields),(bindings,binding_fields)]:
        for row in collection:
            for field in fields: row.setdefault(field,None)
    return {'documents': documents, 'versions': versions, 'bindings': bindings,
            'viewedSopIds': [1], 'viewDigest': 'v' * 64, 'signatureDigest': 's' * 64,
            'otherDocumentDigest': 'o' * 64, 'otherVersionDigest': 'q' * 64}


def artifacts(snapshot):
    result = []
    for d in snapshot['documents']:
        i = d['sop_id']
        result.append({'sopId': str(i), 'ossId': str(3000000000000000000 + i),
                       'versionId': str(3100000000000000000 + i), 'versionNo': 'V1.1',
                       'sopCode': d['sop_code'], 'originalName': d['sop_name'] + '_V1.1_待审核.pdf',
                       'objectKey': f'mes/sop/000000/reference-20261008/{d["sop_code"]}/V1.1/' + 'a' * 64 + '.pdf',
                       'sha256': 'a' * 64, 'sm3': 'b' * 64, 'fileSize': 50000, 'pages': 5, 'textCharacters': 3000,
                       'path': f'pdf/{d["sop_code"]}.pdf'})
    return result


def applied(snapshot, rows):
    result = copy.deepcopy(snapshot)
    for d, row in zip(result['documents'], rows):
        d.update(row['documentChanges'])
    result['versions'] += [row['version'] for row in rows]
    return result


class GateTests(unittest.TestCase):
    def test_exact_57_baseline_accepts(self):
        s = fixture()
        validate_source(s)
        self.assertEqual(57, len(s['documents']))

    def test_foreign_tenant_rejects_in_each_business_table(self):
        for table in ('documents', 'versions', 'bindings'):
            s = fixture()
            s[table][0]['tenant_id'] = 'FOREIGN'
            with self.subTest(table=table), self.assertRaises(SafetyError):
                validate_source(s)

    def test_missing_extra_duplicate_or_changed_documents_reject(self):
        mutations = [lambda s: s['documents'].pop(), lambda s: s['documents'].append(copy.deepcopy(s['documents'][0])),
                     lambda s: s['documents'][0].update(status='ARCHIVED'),
                     lambda s: s['documents'][0].update(file_oss_id=999),
                     lambda s: s['documents'][0].update(current_version='V2.0'),
                     lambda s: s['documents'][0].update(sop_name='Unexpected')]
        for mutate in mutations:
            s = fixture(); mutate(s)
            with self.assertRaises(SafetyError): validate_source(s)

    def test_old_version_hash_or_pending_version_rejects(self):
        for change in [{'file_hash': 'c' * 64}, {'status': 'PENDING'}, {'file_oss_id': 999}]:
            s = fixture(); s['versions'][0].update(change)
            with self.assertRaises(SafetyError): validate_source(s)

    def test_invalid_binding_unknown_code_or_view_identity_rejects(self):
        for mutate in [lambda s: s['bindings'][0].update(process_code='UNKNOWN'),
                       lambda s: s['bindings'][0].update(sop_id=2),
                       lambda s: s.update(viewedSopIds=[999])]:
            s = fixture(); mutate(s)
            with self.assertRaises(SafetyError): validate_source(s)

    def test_new_versions_only_and_signed_metadata_unchanged(self):
        s = fixture(); rows = plan_rows(s, artifacts(s))
        self.assertEqual(57, len(rows))
        self.assertEqual('PROCESS', rows[0]['documentChanges']['sop_type'])
        for row, old in zip(rows, s['documents']):
            self.assertEqual('DRAFT', row['documentChanges']['status'])
            self.assertEqual({'status', 'sop_type'}, set(row['documentChanges']))
            self.assertEqual('PENDING', row['version']['status'])
            self.assertEqual('V1.1', row['version']['version_no'])
            self.assertNotIn('approval_time', row['documentChanges'])
            self.assertEqual(old['sop_id'], row['version']['sop_id'])
        self.assertEqual(s['versions'], fixture()['versions'])
        self.assertEqual('INSPECTION', rows[53]['documentChanges']['sop_type'])
        self.assertEqual('CLEAN', rows[55]['documentChanges']['sop_type'])

    def test_ids_overflow_collision_hash_and_missing_artifact_reject(self):
        for modify in [lambda a: a.pop(), lambda a: a[0].update(ossId='9223372036854775808'),
                       lambda a: a[1].update(versionId=a[0]['versionId']),
                       lambda a: a[0].update(sm3='not-a-hash'),
                       lambda a: a[0].update(objectKey='../unsafe'),
                       lambda a: a[0].update(versionNo='V1.0')]:
            s = fixture(); a = artifacts(s); modify(a)
            with self.assertRaises(SafetyError): plan_rows(s, a)

    def test_verify_requires_history_signatures_archive_bindings_unchanged(self):
        s = fixture(); rows = plan_rows(s, artifacts(s)); now = applied(s, rows)
        verify_applied(s, now, rows)
        for key in ['viewDigest', 'signatureDigest', 'otherDocumentDigest', 'otherVersionDigest']:
            changed = copy.deepcopy(now); changed[key] = 'changed'
            with self.assertRaises(SafetyError): verify_applied(s, changed, rows)
        for change in [('versions', 'file_oss_id'), ('bindings', 'version_no')]:
            changed = copy.deepcopy(now); changed[change[0]][0][change[1]] = 999
            with self.assertRaises(SafetyError): verify_applied(s, changed, rows)

    def test_private_manifest_cannot_escape_or_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d)
            directory = safe_output_dir(root, root / '.codex' / 'sop-test')
            self.assertTrue(directory.is_relative_to(root / '.codex'))
            with self.assertRaises(SafetyError): safe_output_dir(root, root / 'public')
            file = directory / 'manifest.json'
            save_private_json(file, {'batch': 'ok'})
            with self.assertRaises(SafetyError): save_private_json(file, {'batch': 'overwrite'})
            self.assertEqual({'batch': 'ok'}, load_manifest(file))
            file.write_text('{"key":1,"key":2}', encoding='utf-8')
            with self.assertRaises(SafetyError): load_manifest(file)

    def test_settings_do_not_echo_credentials_and_remote_targets_reject(self):
        settings = load_db_settings({'NEXUS_SOP_DB_USER': 'private-user', 'NEXUS_SOP_DB_PASSWORD': 'private-password'})
        self.assertNotIn('private-user', repr(settings))
        self.assertNotIn('private-password', repr(settings))
        with self.assertRaises(SafetyError): load_db_settings({'NEXUS_SOP_DB_HOST': 'remote', 'NEXUS_SOP_DB_USER': 'u', 'NEXUS_SOP_DB_PASSWORD': 'p'})
        with self.assertRaises(SafetyError): load_db_settings({})
        config = S3Settings('127.0.0.1:9005', 'ruoyi', 'private-access', 'private-secret', 'us-east-1')
        self.assertNotIn('private-secret', repr(config))

    def test_download_bytes_must_match_both_hashes_length_and_pdf_magic(self):
        data = b'%PDF-1.7\nTest reference content'
        a = {'fileSize': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'sm3': hashlib.new('sm3', data).hexdigest()}
        verify_object_bytes(data, a)
        for damaged in [data + b'x', data.replace(b'Test', b'test'), b'XXXX-' + data[5:]]:
            with self.assertRaises(SafetyError): verify_object_bytes(damaged, a)

    def test_idempotency_only_accepts_complete_matching_batch(self):
        s = fixture(); rows = plan_rows(s, artifacts(s)); now = applied(s, rows)
        self.assertEqual('new', classify_existing_batch(s, rows))
        self.assertEqual('applied', classify_existing_batch(now, rows))
        now['versions'].pop()
        with self.assertRaises(SafetyError): classify_existing_batch(now, rows)


class TransactionTests(unittest.TestCase):
    def test_db_failure_rolls_back_no_commit_and_never_updates_old_versions(self):
        s = fixture(); rows = plan_rows(s, artifacts(s))
        class Cursor:
            rowcount = 1
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def execute(self, sql, args=None):
                conn.statements.append(sql)
                if sql.startswith('INSERT INTO `ry-mes`.`sop_version`'):
                    raise RuntimeError('private-password must never be emitted')
            def fetchone(self): return {'acquired': 1}
        class Connection:
            statements = []
            commits = 0
            rollbacks = 0
            def cursor(self): return Cursor()
            def begin(self): pass
            def commit(self): self.commits += 1
            def rollback(self): self.rollbacks += 1
        conn = Connection()
        with patch('batch.read_snapshot', return_value=s), self.assertRaises(SafetyError) as error:
            write_database(conn, s, rows)
        self.assertEqual(0, conn.commits)
        self.assertGreaterEqual(conn.rollbacks, 1)
        self.assertNotIn('private-password', str(error.exception))
        self.assertFalse(any(sql.startswith('UPDATE `ry-mes`.`sop_version`') or 'DELETE' in sql or 'DROP' in sql for sql in conn.statements))

    def test_stale_snapshot_prevents_any_insert(self):
        s = fixture(); rows = plan_rows(s, artifacts(s)); stale = copy.deepcopy(s); stale['documents'][0]['status'] = 'DRAFT'
        class Cursor:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def execute(self, sql, args=None): conn.statements.append(sql)
            def fetchone(self): return {'acquired': 1}
        class Connection:
            statements = []
            def cursor(self): return Cursor()
            def begin(self): pass
            def rollback(self): pass
        conn = Connection()
        with patch('batch.read_snapshot', return_value=stale), self.assertRaises(SafetyError): write_database(conn, s, rows)
        self.assertFalse(any(sql.startswith('INSERT') for sql in conn.statements))


if __name__ == '__main__': unittest.main()
