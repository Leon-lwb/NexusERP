"""Explicitly scoped, backed-up materialization of 57 local SOP reference revisions.

Default mode is read-only audit. Database credentials come from process environment.
No old versions, signatures, view logs, bindings or archived documents are written.
"""
from dataclasses import dataclass, field
import argparse
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time

from content import PROFILES
from pdf_document import render_pdf, inspect_pdf, validate_expected_pdf
from storage import SafetyError, require, S3Settings, S3Transport, verify_object_bytes
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'rebuild'))
from sop_types import sop_type_for_process
from catalog import PROCESSES

TENANT = '000000'
BATCH = 'NEXUS_SOP_REFERENCE_20261008'
VERSION = 'V1.1'
# Separately authorized private, nondefault target; never fall back to minio.
SOP_CONFIG_KEY = 'sop'
SOP_ENDPOINT = '159.75.14.226:9005'
SOP_BUCKET = 'sop'
SOP_REGION = 'us-east-1'
IDS = set(range(1, 58))
CATALOG = {i: (p[0], p[1]) for i, p in enumerate(PROCESSES, 1)}
HISTORY_KEYS = ('viewDigest', 'signatureDigest', 'otherDocumentDigest', 'otherVersionDigest')
DOCUMENT_COLUMNS = frozenset('sop_id tenant_id sop_code sop_name sop_type current_version status file_oss_id file_type description approval_by approval_time approval_remark archive_reason remark create_dept create_by create_time update_by update_time del_flag'.split())
VERSION_COLUMNS = frozenset('version_id tenant_id sop_id version_no file_oss_id file_type file_hash change_log published_time published_by status remark create_dept create_by create_time update_by update_time del_flag'.split())
BINDING_COLUMNS = frozenset('binding_id tenant_id process_id process_code process_name process_category sop_id version_no is_default sort_order remark create_dept create_by create_time update_by update_time del_flag'.split())


@dataclass(frozen=True)
class DBSettings:
    host: str = '127.0.0.1'
    port: int = 3306
    username: str = field(default='', repr=False)
    password: str = field(default='', repr=False)


def load_db_settings(environ=None):
    env = os.environ if environ is None else environ
    host = env.get('NEXUS_SOP_DB_HOST', '127.0.0.1')
    require(host in ('127.0.0.1', 'localhost'), 'LOCAL_DATABASE_REQUIRED')
    require(env.get('NEXUS_SOP_DB_PORT', '3306') == '3306', 'DATABASE_PORT_REJECTED')
    user, password = env.get('NEXUS_SOP_DB_USER', ''), env.get('NEXUS_SOP_DB_PASSWORD', '')
    require(bool(user) and bool(password), 'DATABASE_CREDENTIALS_MISSING')
    return DBSettings(host, 3306, user, password)


def canonical_digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, default=str, separators=(',', ':')).encode()).hexdigest()


def _same(left, right):
    return canonical_digest(left) == canonical_digest(right)


def _exact_id(value):
    require(type(value) is int or isinstance(value, str) and re.fullmatch(r'[1-9][0-9]*', value) is not None, 'EXACT_INTEGER_ID_REQUIRED')
    integer = int(value)
    require(0 < integer <= 2**63 - 1, 'ID_RANGE_REJECTED')
    return integer


def validate_source(snapshot):
    docs, versions, bindings = (snapshot.get(k, []) for k in ('documents', 'versions', 'bindings'))
    require(len(docs) == len(versions) == len(bindings) == 57, 'EXACT_57_SOURCE_ROWS_REQUIRED')
    for collection, key in ((docs, 'sop_id'), (versions, 'sop_id'), (bindings, 'sop_id')):
        require({r.get(key) for r in collection} == IDS, 'SOURCE_ID_COVERAGE_REJECTED')
        require(all(r.get('tenant_id') == TENANT and r.get('del_flag') == '0' for r in collection), 'SOURCE_TENANT_OR_DELETION_REJECTED')
    for collection, columns, primary in ((docs,DOCUMENT_COLUMNS,'sop_id'), (versions,VERSION_COLUMNS,'version_id'), (bindings,BINDING_COLUMNS,'binding_id')):
        require(all(set(r) == columns for r in collection), 'PERSISTED_SOURCE_SCHEMA_REJECTED')
        require({_exact_id(r[primary]) for r in collection} == IDS, 'LEGACY_PRIMARY_KEYS_REJECTED')
    require(1 in snapshot.get('viewedSopIds', []), 'SIGNED_SOP_ONE_PROTECTION_REQUIRED')
    for doc in docs:
        i = doc['sop_id']; _, name = CATALOG[i]
        require(doc.get('sop_code') == f'SOP20260914{i:03}' and doc.get('sop_name') == name + '作业指导书', 'SOURCE_DOCUMENT_IDENTITY_CHANGED')
        require(doc.get('sop_type') == 'PROCESS' and doc.get('status') == 'PUBLISHED', 'SOURCE_STATE_CHANGED')
        require(doc.get('file_oss_id') == 0 and doc.get('current_version') == 'V1.0' and doc.get('file_type') == 'PDF', 'SOURCE_FILE_OR_VERSION_CHANGED')
    for version in versions:
        require(version.get('version_no') == 'V1.0' and version.get('status') == 'CURRENT', 'SOURCE_VERSION_CHANGED')
        require(version.get('file_oss_id') == 0 and not version.get('file_hash') and version.get('file_type') == 'PDF', 'SOURCE_VERSION_BODY_CHANGED')
    for binding in bindings:
        expected_code, expected_name = CATALOG[binding['sop_id']]
        require(binding.get('process_code') == expected_code and binding.get('process_name') == expected_name, 'SOURCE_PROCESS_BINDING_CHANGED')
    require(set(snapshot.get('viewedSopIds', [])) <= IDS, 'VIEW_IDENTITY_OUT_OF_SCOPE')
    require(all(isinstance(snapshot.get(k), str) and len(snapshot[k]) == 64 for k in HISTORY_KEYS), 'HISTORY_DIGEST_MISSING')


def plan_rows(snapshot, artifacts):
    validate_source(snapshot)
    require(len(artifacts) == 57 and {_exact_id(a['sopId']) for a in artifacts} == IDS, 'ARTIFACT_COVERAGE_REJECTED')
    identities = [_exact_id(a[k]) for a in artifacts for k in ('ossId', 'versionId')]
    require(len(set(identities)) == 114, 'ARTIFACT_ID_COLLISION')
    require(not (set(identities) & {r['version_id'] for r in snapshot['versions']}), 'LEGACY_ID_COLLISION')
    docs = {d['sop_id']: d for d in snapshot['documents']}
    viewed = set(snapshot['viewedSopIds'])
    result = []
    for a in sorted(artifacts, key=lambda r: _exact_id(r['sopId'])):
        i = _exact_id(a['sopId']); d = docs[i]; code, _ = CATALOG[i]
        for hash_key in ('sha256', 'sm3'):
            require(isinstance(a.get(hash_key), str) and re.fullmatch(r'[a-f0-9]{64}', a[hash_key]) is not None, 'ARTIFACT_HASH_REJECTED')
        expected_key = f'mes/sop/{TENANT}/reference-20261008/{d["sop_code"]}/{VERSION}/{a["sha256"]}.pdf'
        require(a.get('objectKey') == expected_key and a.get('versionNo') == VERSION, 'ARTIFACT_OBJECT_SCOPE_REJECTED')
        require(a.get('sopCode') == d['sop_code'] and a.get('originalName') == d['sop_name'] + '_V1.1_待审核.pdf', 'ARTIFACT_METADATA_REJECTED')
        require(type(a.get('fileSize')) is int and 1000 < a['fileSize'] <= S3Transport.MAX_BYTES, 'ARTIFACT_SIZE_REJECTED')
        require(a.get('pages', 0) >= 4 and a.get('textCharacters', 0) >= 1900, 'ARTIFACT_CONTENT_REJECTED')
        relative = Path(a.get('path', ''))
        require(not relative.is_absolute() and len(relative.parts) == 2 and relative.parts[0] == 'pdf' and relative.name == d['sop_code'] + '.pdf', 'ARTIFACT_PATH_REJECTED')
        oss = {'oss_id': _exact_id(a['ossId']), 'tenant_id': TENANT, 'file_name': a['objectKey'],
               'original_name': a['originalName'], 'file_suffix': '.pdf',
               'url': 'http://' + SOP_ENDPOINT + '/' + SOP_BUCKET + '/' + a['objectKey'], 'service': SOP_CONFIG_KEY,
               'ext1': json.dumps({'fileSize': a['fileSize'], 'contentType': 'application/pdf'}, separators=(',', ':'))}
        version = {'version_id': _exact_id(a['versionId']), 'tenant_id': TENANT, 'sop_id': i,
                   'version_no': VERSION, 'file_oss_id': oss['oss_id'], 'file_type': 'PDF', 'file_hash': a['sm3'],
                   'change_log': '新增工序级中文参考 PDF，示例/待审核；不替换原版本或历史签名。',
                   'status': 'PENDING', 'remark': BATCH + '；未经现场审批不得用于生产', 'del_flag': '0'}
        result.append({'sopId': i, 'oss': oss, 'version': version,
                       'documentChanges': {'status': 'DRAFT', 'sop_type': d['sop_type'] if i in viewed else sop_type_for_process(code)}})
    return result


def verify_applied(before, after, rows):
    require(_same(before['bindings'], after['bindings']), 'PROCESS_BINDINGS_CHANGED')
    for key in HISTORY_KEYS:
        require(before[key] == after[key], 'HISTORICAL_AUDIT_CHANGED')
    require(before['viewedSopIds'] == after['viewedSopIds'], 'VIEW_IDENTITIES_CHANGED')
    old_versions = {v['version_id']: v for v in before['versions']}
    new_versions = {v['version_id']: v for v in after['versions']}
    require(len(new_versions) == 114 and len(after['versions']) == 114, 'VERSION_COUNT_MISMATCH')
    require(set(new_versions) == set(old_versions) | {r['version']['version_id'] for r in rows}, 'POST_VERSION_ID_SET_REJECTED')
    for identity, old in old_versions.items():
        require(identity in new_versions and _same(old, new_versions[identity]), 'OLD_VERSION_MODIFIED')
    docs = {d['sop_id']: d for d in after['documents']}
    require(len(docs) == 57 and set(docs) == IDS, 'DOCUMENT_COUNT_MISMATCH')
    for old, row in zip(sorted(before['documents'], key=lambda r: r['sop_id']), rows):
        d = docs[row['sopId']]
        for key, value in old.items():
            if key not in ('status', 'sop_type', 'update_time'):
                require(_same(value, d.get(key)), 'OLD_DOCUMENT_REFERENCE_MODIFIED')
        require(all(d.get(k) == v for k, v in row['documentChanges'].items()), 'DRAFT_DOCUMENT_STATE_MISMATCH')
        expected = row['version']; actual = new_versions.get(expected['version_id'], {})
        require(all(_same(actual.get(k), v) for k, v in expected.items()), 'NEW_PENDING_VERSION_MISMATCH')
        require(actual.get('published_time') is None and actual.get('published_by') is None, 'NEW_VERSION_FALSE_PUBLICATION')


def classify_existing_batch(snapshot, rows):
    expected = {r['version']['version_id'] for r in rows}
    present = {r['version_id'] for r in snapshot['versions']} & expected
    if not present:
        validate_source(snapshot)
        return 'new'
    require(present == expected, 'PARTIAL_BATCH_REJECTED')
    # The strict comparison against the original immutable backup is done by verify_applied.
    require(all(v.get('status') == 'PENDING' and v.get('version_no') == VERSION for v in snapshot['versions'] if v['version_id'] in expected),
            'BATCH_STATE_CHANGED')
    return 'applied'


def safe_output_dir(repo_root, directory):
    root = Path(repo_root).resolve()
    base = root / '.codex'
    candidate = Path(directory)
    resolved = candidate.resolve()
    require(resolved.is_relative_to(base) and resolved != base, 'PRIVATE_ARTIFACT_DIRECTORY_REQUIRED')
    cursor = candidate
    while cursor != root and cursor != cursor.parent:
        require(not cursor.is_symlink(), 'ARTIFACT_SYMLINK_REJECTED')
        cursor = cursor.parent
    resolved.mkdir(parents=True, exist_ok=True)
    return resolved


def save_private_json(path, value):
    path = Path(path)
    require(not path.exists() and not path.is_symlink(), 'BACKUP_OR_MANIFEST_ALREADY_EXISTS')
    path.parent.mkdir(parents=True, exist_ok=True)
    data = (json.dumps(value, ensure_ascii=False, sort_keys=True, default=str, indent=2) + '\n').encode('utf-8')
    with path.open('xb') as file:
        file.write(data)
        file.flush()
        os.fsync(file.fileno())


def read_private_document(path):
    path = Path(path)
    require(not path.is_symlink() and path.is_file() and path.stat().st_size <= 4 * 1024 * 1024, 'PRIVATE_DOCUMENT_REJECTED')
    data = path.read_bytes()
    require(len(data) <= 4 * 1024 * 1024, 'PRIVATE_DOCUMENT_TOO_LARGE')
    return data


def decode_private_document(data):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    try:
        document = json.loads(data.decode('utf-8-sig'), object_pairs_hook=unique)
        require(isinstance(document, dict), 'PRIVATE_JSON_OBJECT_REQUIRED')
        return document
    except SafetyError:
        raise
    except Exception:
        raise SafetyError('PRIVATE_JSON_INVALID') from None


def load_manifest(path):
    return decode_private_document(read_private_document(path))


def connect_database(settings):
    import pymysql
    try:
        return pymysql.connect(host=settings.host, port=settings.port, user=settings.username, password=settings.password,
                               database='ry-vue', charset='utf8mb4', cursorclass=pymysql.cursors.DictCursor,
                               autocommit=False, connect_timeout=8, read_timeout=30, write_timeout=30)
    except Exception:
        raise SafetyError('DATABASE_CONNECTION_UNAVAILABLE') from None


def read_snapshot(conn, lock=False):
    with conn.cursor() as c:
        suffix = ' FOR UPDATE' if lock else ''
        c.execute('SELECT * FROM `ry-mes`.`sop_document` WHERE tenant_id=%s AND sop_id BETWEEN 1 AND 57 ORDER BY sop_id' + suffix, (TENANT,))
        docs = c.fetchall()
        c.execute('SELECT * FROM `ry-mes`.`sop_version` WHERE tenant_id=%s AND sop_id BETWEEN 1 AND 57 ORDER BY version_id' + suffix, (TENANT,))
        versions = c.fetchall()
        c.execute('SELECT * FROM `ry-mes`.`sop_process_binding` WHERE tenant_id=%s AND sop_id BETWEEN 1 AND 57 ORDER BY binding_id' + suffix, (TENANT,))
        bindings = c.fetchall()
        c.execute('SELECT * FROM `ry-mes`.`sop_view_log` WHERE tenant_id=%s ORDER BY log_id' + suffix, (TENANT,))
        views = c.fetchall()
        viewed = sorted({r['sop_id'] for r in views if r['sop_id'] in IDS})
        c.execute("SELECT * FROM `ry-vue`.`es_signature` WHERE tenant_id=%s AND biz_type='SOP_VIEW' ORDER BY signature_id" + suffix, (TENANT,))
        signatures = c.fetchall()
        viewed = sorted(set(viewed) | {1} | {_exact_id(r['biz_id']) for r in signatures if r.get('biz_id') is not None and str(r['biz_id']).isdigit() and int(r['biz_id']) in IDS})
        c.execute('SELECT * FROM `ry-mes`.`sop_document` WHERE tenant_id=%s AND sop_id NOT BETWEEN 1 AND 57 ORDER BY sop_id' + suffix, (TENANT,))
        other_docs = c.fetchall()
        c.execute('SELECT * FROM `ry-mes`.`sop_version` WHERE tenant_id=%s AND sop_id NOT BETWEEN 1 AND 57 ORDER BY version_id' + suffix, (TENANT,))
        other_versions = c.fetchall()
    # Personal fields in signature/view rows are never exported, only hashed in memory.
    return {'documents': docs, 'versions': versions, 'bindings': bindings, 'viewedSopIds': viewed,
            'viewDigest': canonical_digest(views), 'signatureDigest': canonical_digest(signatures),
            'otherDocumentDigest': canonical_digest(other_docs), 'otherVersionDigest': canonical_digest(other_versions)}


def read_context(conn):
    with conn.cursor() as c:
        c.execute('SELECT @@server_uuid AS server_uuid, VERSION() AS version')
        identity = c.fetchone()
        require(str(identity['version']).startswith('5.7.'), 'EXPECTED_MYSQL57_REQUIRED')
        c.execute("SELECT TABLE_SCHEMA,TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE (TABLE_SCHEMA='ry-vue' AND TABLE_NAME IN ('sys_oss','sys_oss_config','es_signature')) OR (TABLE_SCHEMA='ry-mes' AND TABLE_NAME IN ('sop_document','sop_version','sop_process_binding','sop_view_log'))")
        engines = c.fetchall()
        require(len(engines) == 7 and all(r['ENGINE'] == 'InnoDB' for r in engines), 'TRANSACTIONAL_TABLES_REQUIRED')
        c.execute("SELECT * FROM `ry-vue`.`sys_oss_config` WHERE tenant_id=%s AND config_key=%s", (TENANT, SOP_CONFIG_KEY))
        configs = c.fetchall()
        require(len(configs) == 1, 'UNIQUE_SOP_CONFIG_REQUIRED')
        cfg = configs[0]
        require(cfg.get('tenant_id') == TENANT and cfg.get('config_key') == SOP_CONFIG_KEY
                and cfg.get('status') == '1' and cfg.get('access_policy') == '0' and cfg.get('is_https') == 'N'
                and cfg.get('prefix') in (None, '') and cfg.get('domain') in (None, ''),
                'EXPECTED_PRIVATE_SOP_CONFIG_REQUIRED')
        require(cfg.get('endpoint') == SOP_ENDPOINT and cfg.get('bucket_name') == SOP_BUCKET,
                'EXPECTED_SOP_STORAGE_TARGET_REQUIRED')
        require(cfg.get('region') in (None, '', SOP_REGION), 'EXPECTED_SOP_REGION_REQUIRED')
        config_id = _exact_id(cfg.get('oss_config_id'))
        s3 = S3Settings(SOP_ENDPOINT, SOP_BUCKET, cfg['access_key'], cfg['secret_key'], SOP_REGION)
        c.execute("SELECT dict_value FROM `ry-vue`.`sys_dict_data` WHERE tenant_id=%s AND dict_type='mes_sop_type'", (TENANT,))
        require({'PRODUCTION', 'INSPECTION', 'CLEAN'} <= {r['dict_value'] for r in c.fetchall()}, 'REQUIRED_SOP_DICTIONARY_VALUES_MISSING')
    public_context = {'serverFingerprint': canonical_digest(identity['server_uuid']), 'databaseVersion': identity['version'],
                      'schemas': ['ry-vue', 'ry-mes'], 'endpoint': SOP_ENDPOINT, 'bucket': SOP_BUCKET, 'accessPolicy': 'PRIVATE',
                      'configKey': SOP_CONFIG_KEY, 'configIdFingerprint': canonical_digest(config_id)}
    return public_context, s3


def _read_new_oss(conn, rows):
    ids = [r['oss']['oss_id'] for r in rows]
    with conn.cursor() as c:
        c.execute('SELECT * FROM `ry-vue`.`sys_oss` WHERE tenant_id=%s AND oss_id IN (' + ','.join(['%s'] * len(ids)) + ') ORDER BY oss_id', (TENANT, *ids))
        result = c.fetchall()
    require(len(result) == 57, 'OSS_METADATA_COUNT_MISMATCH')
    by_id = {r['oss_id']: r for r in result}
    for row in rows:
        expected = row['oss']; actual = by_id.get(expected['oss_id'], {})
        require(all(_same(actual.get(k), v) for k, v in expected.items()), 'OSS_METADATA_MISMATCH')
    return result


def write_database(conn, backup, rows):
    acquired = False
    commit_attempted = False
    committed = False
    try:
        with conn.cursor() as c:
            c.execute('SELECT GET_LOCK(%s,0) AS acquired', (BATCH,))
            acquired = c.fetchone()['acquired'] == 1
            require(acquired, 'SOP_BATCH_LOCK_UNAVAILABLE')
        with conn.cursor() as c:
            c.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        conn.begin()
        current = read_snapshot(conn, lock=True)
        require(_same(backup, current), 'SOURCE_CHANGED_SINCE_BACKUP')
        validate_source(current)
        with conn.cursor() as c:
            now = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).replace(tzinfo=None)
            oss_fields = ('oss_id','tenant_id','file_name','original_name','file_suffix','url','ext1','service')
            version_fields = ('version_id','tenant_id','sop_id','version_no','file_oss_id','file_type','file_hash','change_log','status','remark','del_flag')
            for row in rows:
                c.execute('INSERT INTO `ry-vue`.`sys_oss` (`' + '`,`'.join(oss_fields) + '`,create_time) VALUES (' + ','.join(['%s'] * (len(oss_fields) + 1)) + ')', tuple(row['oss'][f] for f in oss_fields) + (now,))
                require(c.rowcount == 1, 'OSS_INSERT_COUNT_REJECTED')
                c.execute('INSERT INTO `ry-mes`.`sop_version` (`' + '`,`'.join(version_fields) + '`,create_time) VALUES (' + ','.join(['%s'] * (len(version_fields) + 1)) + ')', tuple(row['version'][f] for f in version_fields) + (now,))
                require(c.rowcount == 1, 'VERSION_INSERT_COUNT_REJECTED')
                changes = row['documentChanges']
                c.execute("UPDATE `ry-mes`.`sop_document` SET status=%s,sop_type=%s,update_time=%s WHERE tenant_id=%s AND sop_id=%s AND status='PUBLISHED' AND sop_type='PROCESS' AND current_version='V1.0' AND file_oss_id=0 AND del_flag='0'", (changes['status'], changes['sop_type'], now, TENANT, row['sopId']))
                require(c.rowcount == 1, 'DOCUMENT_UPDATE_COUNT_REJECTED')
        after = read_snapshot(conn)
        verify_applied(backup, after, rows)
        _read_new_oss(conn, rows)
        commit_attempted = True
        conn.commit()
        committed = True
    except Exception as error:
        if commit_attempted:
            raise SafetyError('DATABASE_COMMIT_OUTCOME_UNKNOWN') from None
        try:
            conn.rollback()
        except Exception:
            raise SafetyError('DATABASE_PRECOMMIT_FAILURE_ROLLBACK_UNCONFIRMED') from None
        if isinstance(error, SafetyError):
            raise
        raise SafetyError('DATABASE_WRITE_FAILED_ROLLED_BACK') from None
    finally:
        if acquired:
            try:
                with conn.cursor() as c:
                    c.execute('SELECT RELEASE_LOCK(%s)', (BATCH,))
            except Exception:
                # Closing the connection releases its advisory lock; cleanup is not rollback evidence.
                if committed:
                    print(json.dumps({'warningCategory':'POST_COMMIT_LOCK_CLEANUP_FAILED','commitConfirmed':True}))


def _new_ids():
    # A reserved node namespace; database uniqueness is checked before applying.
    elapsed_ms = int(time.time() * 1000) - 1288834974657
    base = (elapsed_ms << 22) | (1023 << 12)
    require(0 < base and base + 114 <= 2**63 - 1, 'SNOWFLAKE_CLOCK_REJECTED')
    return iter(range(base, base + 114))


def generate_artifacts(snapshot, directory, context):
    validate_source(snapshot)
    require(not (directory / 'backup.json').exists() and not (directory / 'manifest.json').exists(), 'EXISTING_BATCH_REQUIRES_VERIFY')
    save_private_json(directory / 'backup.json', snapshot)
    ids = _new_ids()
    items = []
    for d in sorted(snapshot['documents'], key=lambda row: row['sop_id']):
        code, _ = CATALOG[d['sop_id']]
        filename = d['sop_code'] + '.pdf'
        info = render_pdf({**d, 'process_code': code}, directory / 'pdf' / filename)
        key = f'mes/sop/{TENANT}/reference-20261008/{d["sop_code"]}/{VERSION}/{info["sha256"]}.pdf'
        items.append({**info, 'sopId': str(d['sop_id']), 'sopCode': d['sop_code'], 'versionNo': VERSION,
                      'ossId': str(next(ids)), 'versionId': str(next(ids)), 'objectKey': key,
                      'originalName': d['sop_name'] + '_V1.1_待审核.pdf', 'path': 'pdf/' + filename})
    manifest = {'batch': BATCH, 'tenantId': TENANT, 'sourceDigest': canonical_digest(snapshot), 'context': context, 'artifacts': items}
    plan_rows(snapshot, items)
    save_private_json(directory / 'manifest.json', manifest)
    return manifest


def verify_approval_digest(path, approved_digest):
    require(isinstance(approved_digest, str) and re.fullmatch(r'[a-f0-9]{64}', approved_digest) is not None, 'EXTERNAL_MANIFEST_APPROVAL_REQUIRED')
    data = read_private_document(path)
    require(hashlib.sha256(data).hexdigest() == approved_digest, 'APPROVED_MANIFEST_DIGEST_MISMATCH')
    return data


def _load_prepared(directory, context, approved_digest):
    manifest_bytes = verify_approval_digest(directory / 'manifest.json', approved_digest)
    backup = load_manifest(directory / 'backup.json')
    manifest = decode_private_document(manifest_bytes)
    require(set(manifest) == {'batch','tenantId','sourceDigest','context','artifacts'}, 'MANIFEST_SHAPE_REJECTED')
    require(manifest['batch'] == BATCH and manifest['tenantId'] == TENANT and manifest['sourceDigest'] == canonical_digest(backup), 'MANIFEST_BACKUP_MISMATCH')
    require(_same(manifest['context'], context), 'DATABASE_OR_MINIO_IDENTITY_CHANGED')
    rows = plan_rows(backup, manifest['artifacts'])
    for a in manifest['artifacts']:
        path = directory / a['path']
        require(not path.is_symlink() and path.resolve().is_relative_to(directory), 'LOCAL_ARTIFACT_SCOPE_REJECTED')
        doc = next(d for d in backup['documents'] if d['sop_id'] == int(a['sopId']))
        try:
            info = validate_expected_pdf(path, {**doc, 'process_code': CATALOG[doc['sop_id']][0]})
        except ValueError:
            raise SafetyError('LOCAL_PDF_IDENTITY_OR_CONTENT_MISMATCH') from None
        require(all(info[k] == a[k] for k in ('sha256','sm3','fileSize','pages','textCharacters')), 'LOCAL_ARTIFACT_CHANGED')
    return backup, manifest, rows


def run(mode, repo_root, artifact_dir, approved_digest=None):
    require(mode in ('audit','generate','apply','verify'), 'MODE_REJECTED')
    settings = load_db_settings()
    conn = connect_database(settings)
    try:
        with conn.cursor() as c:
            c.execute('START TRANSACTION READ ONLY')
        context, s3settings = read_context(conn)
        current = read_snapshot(conn)
        if mode == 'audit':
            validate_source(current)
            return {'batch': BATCH, 'mode': mode, 'sourceDocuments': 57, 'missingCurrentFiles': 57,
                    'signedOrViewedSopCount': len(current['viewedSopIds']), 'writePerformed': False, 'context': context}
        directory = safe_output_dir(repo_root, artifact_dir)
        if mode == 'generate':
            manifest = generate_artifacts(current, directory, context)
            return {'batch': BATCH, 'mode': mode, 'generatedPdfs': 57, 'totalPages': sum(a['pages'] for a in manifest['artifacts']), 'databaseWritten': False}
        backup, manifest, rows = _load_prepared(directory, context, approved_digest)
        existing = classify_existing_batch(current, rows)
        if existing == 'applied':
            verify_applied(backup, current, rows)
            _read_new_oss(conn, rows)
        else:
            require(mode == 'apply' and _same(backup, current), 'SOURCE_CHANGED_OR_BATCH_NOT_APPLIED')
            with conn.cursor() as c:
                identities = [v for r in rows for v in (r['oss']['oss_id'], r['version']['version_id'])]
                marks = ','.join(['%s'] * len(identities))
                for table, pk in (('`ry-vue`.`sys_oss`','oss_id'), ('`ry-mes`.`sop_version`','version_id')):
                    c.execute('SELECT COUNT(*) AS count FROM ' + table + ' WHERE ' + pk + ' IN (' + marks + ')', tuple(identities))
                    require(c.fetchone()['count'] == 0, 'LIVE_ID_COLLISION')
        conn.rollback()  # Finish the read-only transaction before any database mutation.
        transport = S3Transport(s3settings)
        transport.preflight_privacy(manifest['artifacts'])
        for index, a in enumerate(manifest['artifacts'], 1):
            if mode == 'apply' and existing == 'new':
                transport.upload_immutable(a, (directory / a['path']).read_bytes())
            else:
                transport.verify(a)
            print(json.dumps({'progress': index, 'total': 57, 'operation': 'uploaded_and_verified' if existing == 'new' else 'verified'}, ensure_ascii=False), flush=True)
        if mode == 'apply' and existing == 'new':
            try:
                write_database(conn, backup, rows)
            except SafetyError as error:
                if str(error) != 'DATABASE_COMMIT_OUTCOME_UNKNOWN':
                    raise
                # A lost acknowledgement is reconciled read-only on a NEW connection, never retried blindly.
                old_connection = conn
                try:
                    conn = connect_database(settings)
                    with conn.cursor() as c:
                        c.execute('START TRANSACTION READ ONLY')
                    fresh_context, _ = read_context(conn)
                    require(_same(context, fresh_context), 'COMMIT_RECONCILIATION_TARGET_CHANGED')
                    fresh = read_snapshot(conn)
                    require(classify_existing_batch(fresh, rows) == 'applied', 'COMMIT_OUTCOME_NOT_CONFIRMED_NO_AUTO_RETRY')
                    verify_applied(backup, fresh, rows)
                    _read_new_oss(conn, rows)
                    conn.rollback()
                    print(json.dumps({'commitAcknowledgementLost':True,'freshReadConfirmedCommit':True}))
                except Exception:
                    raise SafetyError('DATABASE_COMMIT_OUTCOME_UNKNOWN_REQUIRES_MANUAL_VERIFY') from None
                finally:
                    try: old_connection.close()
                    except Exception: pass
        with conn.cursor() as c:
            c.execute('START TRANSACTION READ ONLY')
        verify_applied(backup, read_snapshot(conn), rows)
        _read_new_oss(conn, rows)
        result = {'batch': BATCH, 'mode': mode, 'verifiedPdfs': 57, 'ossMetadataRows': 57, 'newPendingVersions': 57,
                  'draftDocuments': 57, 'oldVersionsUnchanged': 57, 'historicalViewsAndSignaturesUnchanged': True,
                  'otherDocumentsAndVersionsUnchanged': True, 'bindingsUnchanged': True,
                  'signedMetadataTypePreserved': len(backup['viewedSopIds']), 'automaticPublication': False,
                  'idempotentReplay': existing == 'applied', 'totalPages': sum(a['pages'] for a in manifest['artifacts'])}
        if mode == 'apply' and not (directory / 'applied.json').exists():
            save_private_json(directory / 'applied.json', result)
        return result
    finally:
        try: conn.rollback()
        except Exception: pass
        try: conn.close()
        except Exception: pass


def main(argv=None):
    parser = argparse.ArgumentParser(description='Scoped local SOP reference documents; default audit does not write database/storage')
    parser.add_argument('--mode', choices=('audit','generate','apply','verify'), default='audit')
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--artifact-dir', type=Path)
    parser.add_argument('--approved-manifest-sha256', help='SHA-256 recorded independently after PDF review; not read from mutable batch files')
    args = parser.parse_args(argv)
    directory = args.artifact_dir or args.repo_root / '.codex' / 'sop-reference-20261008'
    try:
        result = run(args.mode, args.repo_root, directory, args.approved_manifest_sha256)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except SafetyError as error:
        print(json.dumps({'errorCategory': str(error), 'mode': args.mode, 'detailsSuppressed': True}))
        return 1
    except Exception:
        print(json.dumps({'errorCategory': 'UNEXPECTED_FAILURE', 'mode': args.mode, 'detailsSuppressed': True}))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
