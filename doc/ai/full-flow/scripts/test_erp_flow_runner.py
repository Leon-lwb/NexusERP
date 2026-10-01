import re
import io
import json
import hashlib
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from unittest.mock import patch
from urllib.error import HTTPError, URLError
from urllib.request import HTTPHandler

import erp_flow_runner
from erp_flow_runner import ApiClient, ApiError, Manifest, PreflightError, _api_id, read_only_preflight


class Response:
    status = 200
    def __init__(self, body):
        self.body = body
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def read(self): return json.dumps(self.body).encode()


class ClientTests(unittest.TestCase):
    @patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 'secret-token', 'NEXUS_FULL_FLOW_CLIENT_ID': 'secret-client'})
    def test_headers_query_and_json_array_body(self):
        calls = []
        def opener(request, timeout):
            calls.append(request)
            return Response({'code': 200, 'data': None})
        client = ApiClient(opener=opener)
        client.get('/erp/so/orderheader/list', {'coId': '9007199254740993'})
        calls.append(client._build_request('POST', '/erp/prd/plan/generateWorkOrders/9', [1, 2]))
        calls.append(client._build_request('PUT', '/erp/test/9', {'status': 'OK'}))
        self.assertIn('coId=9007199254740993', calls[0].full_url)
        self.assertEqual(calls[0].get_header('Authorization'), 'Bearer secret-token')
        self.assertEqual(calls[0].get_header('Clientid'), 'secret-client')
        self.assertEqual(calls[1].data, b'[1,2]')
        self.assertEqual([r.get_method() for r in calls], ['GET', 'POST', 'PUT'])

    def test_boolean_and_read_only_result_never_authorize_mutation(self):
        client = ApiClient(opener=lambda *a, **k: self.fail('mutation reached transport'),
                           apply=True, preflight_approved=True)
        for method in (client.post, client.put):
            with self.assertRaisesRegex(PreflightError, 'Task3b'):
                method('/erp/test', {'data': 1})
        review = PreflightTests().inventory()
        result = read_only_preflight(PreflightTests().client(), review,
            tenant_id='000001', client_code='C', bom_id='2', routing_code='R',
            warehouse_code='W', process_keys=['purchase'], human_ack=True)
        self.assertIs(result['readOnlyReview'], True)
        self.assertIs(result['writeEligible'], False)
        for method in ('post', 'put'):
            with self.assertRaises(PreflightError):
                getattr(ApiClient(opener=lambda *a, **k: self.fail('network'), apply=True,
                                  preflight_approved=result), method)('/erp/test')

    @patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 'secret', 'NEXUS_FULL_FLOW_CLIENT_ID': 'id'})
    def test_default_opener_redirect_handler_does_not_forward_authorization(self):
        # 内存中的 HTTPHandler 拦截默认 build_opener 链，不使用网络套接字。
        requests = []
        class OfflineHTTP(HTTPHandler):
            def http_open(self, request):
                requests.append(request)
                return self.parent.error('http', request, io.BytesIO(b''), 302, 'redirect',
                                         {'Location': 'http://127.0.0.1:8080/other'})
        real_build = erp_flow_runner.build_opener
        def offline_build(*handlers):
            return real_build(*handlers, OfflineHTTP())
        with patch.object(erp_flow_runner, 'build_opener', side_effect=offline_build):
            with self.assertRaises(ApiError): ApiClient(sleep=lambda _: None).get('/safe')
        self.assertEqual(len(requests), 1)
        self.assertEqual(requests[0].get_header('Authorization'), 'Bearer secret')

    @patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 'secret', 'NEXUS_FULL_FLOW_CLIENT_ID': 'id'})
    def test_redirect_is_rejected_without_forwarding_headers(self):
        calls = []
        def redirect(req, timeout):
            calls.append(req)
            class Redirect(Response): status = 302
            return Redirect({'code': 200, 'data': 'unsafe'})
        with self.assertRaises(ApiError): ApiClient(opener=redirect).get('/safe')
        self.assertEqual(len(calls), 1)

    @patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 'secret', 'NEXUS_FULL_FLOW_CLIENT_ID': 'id'})
    def test_http_302_does_not_retry_or_follow(self):
        calls = []
        def redirect(req, timeout):
            calls.append(req)
            raise HTTPError(req.full_url, 302, 'moved', {'Location': 'https://remote.invalid/'}, None)
        with self.assertRaises(ApiError): ApiClient(opener=redirect).get('/safe')
        self.assertEqual(len(calls), 1)

    def test_no_writes_without_both_explicit_gates(self):
        for options in ({}, {'apply': True}, {'preflight_approved': True}):
            with self.subTest(options=options):
                with patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 't', 'NEXUS_FULL_FLOW_CLIENT_ID': 'c'}):
                    client = ApiClient(opener=lambda *args: self.fail('network'), **options)
                    with self.assertRaises(PreflightError): client.post('/erp/test')
                    with self.assertRaises(PreflightError): client.put('/erp/test')

    @patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 'very-secret', 'NEXUS_FULL_FLOW_CLIENT_ID': 'another-secret'})
    def test_get_retries_transient_failure_but_post_does_not(self):
        calls = []
        def opener(req, timeout):
            calls.append(req.get_method())
            if len(calls) == 1: raise URLError('offline')
            return Response({'code': 200, 'data': []})
        client = ApiClient(opener=opener, sleep=lambda _: None, apply=True, preflight_approved=True)
        self.assertEqual(client.get('/list')['data'], [])
        self.assertEqual(calls, ['GET', 'GET'])
        calls.clear()
        with self.assertRaises(PreflightError): client.post('/create')
        self.assertEqual(calls, [])

    @patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 'very-secret', 'NEXUS_FULL_FLOW_CLIENT_ID': 'another-secret'})
    def test_http_and_business_errors_are_structured_and_redacted(self):
        def fail(req, timeout):
            raise HTTPError(req.full_url, 403, 'forbidden', {}, io.BytesIO(b'{"msg":"very-secret another-secret"}'))
        client = ApiClient(opener=fail, sleep=lambda _: None)
        with self.assertRaises(ApiError) as caught: client.get('/safe', {'token': 'very-secret'})
        self.assertEqual(caught.exception.status, 403)
        self.assertNotIn('very-secret', str(caught.exception))
        self.assertNotIn('another-secret', str(caught.exception))
        with self.assertRaises(ApiError) as leaked: client.get('/safe/very-secret')
        self.assertNotIn('very-secret', str(leaked.exception))
        self.assertNotIn('token=', str(caught.exception))
        self.assertEqual(caught.exception.method, 'GET')
        client = ApiClient(opener=lambda *a, **kw: Response({'code': 500, 'msg': 'very-secret'}), sleep=lambda _: None)
        with self.assertRaises(ApiError) as caught: client.get('/safe')
        self.assertNotIn('very-secret', str(caught.exception))


class ManifestTests(unittest.TestCase):
    def test_atomic_save_load_strings_parents_timestamps_and_no_secrets(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / '.codex' / 'full-flow-20260928' / 'manifest.json'
            manifest = Manifest('000001')
            manifest.add('so', 'so_customer_order', '9007199254740993', 'CO-TEST', {})
            manifest.add('so', 'so_sales_order_header', '9007199254740994', 'SO-TEST', {'coId': '9007199254740993'})
            with patch.object(erp_flow_runner, "MANIFEST_DIR", target.parent): manifest.save(target)
            with self.assertRaises(ValueError): manifest.save(target)
            with patch.object(erp_flow_runner, "MANIFEST_DIR", target.parent): loaded = Manifest.load(target)
            self.assertEqual(loaded.records[0]['entityId'], '9007199254740993')
            self.assertEqual(loaded.records[1]['parentRefs'], {'coId': '9007199254740993'})
            self.assertEqual(loaded.records[0]['tenantId'], '000001')
            self.assertIn('createdAt', loaded.records[0])
            self.assertIn('updatedAt', loaded.records[0])
            self.assertFalse(list(target.parent.glob('*.tmp')))
            with self.assertRaises(ValueError): loaded.add('so', 'so_customer_order', '9007199254740993', 'OTHER', {})
            with self.assertRaises(ValueError): loaded.add('so', 'so_sales_order_header', '11', 'SO-TEST', {})
            with self.assertRaises(ValueError): loaded.add('so', 'so_customer_order', '12', 'token=secret', {})
            with self.assertRaises(ValueError): loaded.add('so', 'so_customer_order', '13', 'OK', {'password': 'x'})
            with self.assertRaises(ValueError): loaded.add('so', 'so_customer_order', 9007199254740993, 'NEW', {})
            with self.assertRaises(ValueError): loaded.add('so', 'so_customer_order', '13', 'NEW', {'coId': 9007199254740993})
            with patch.object(erp_flow_runner, "MANIFEST_DIR", target.parent):
                with self.assertRaises(ValueError): loaded.save(Path(root) / 'tracked.json')

    def test_manifest_rejects_even_safe_numeric_ids_by_default(self):
        for tenant_id in (1, 42):
            with self.subTest(tenant_id=tenant_id), self.assertRaises(ValueError):
                Manifest(tenant_id)
        manifest = Manifest('000001')
        for entity_id, parents in ((1, {}), ('1', {'parentId': 2})):
            with self.subTest(entity_id=entity_id, parents=parents), self.assertRaises(ValueError):
                manifest.add('so', 'so_order', entity_id, 'ORDER', parents)
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'manifest.json'
            path.write_text(json.dumps({'tenantId': 1, 'records': []}), encoding='utf-8')
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                with self.assertRaises(ValueError): Manifest.load(path)

    def test_two_stale_loads_cannot_overwrite_first_save(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'manifest.json'
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                Manifest('000001').save(path)
                first, second = Manifest.load(path), Manifest.load(path)
                first.add('so', 'orders', '1', 'FIRST', {})
                second.add('so', 'orders', '2', 'SECOND', {})
                first.save(path)
                original = path.read_bytes()
                with self.assertRaises(PreflightError): second.save(path)
                self.assertEqual(path.read_bytes(), original)
                self.assertEqual([r['businessCode'] for r in Manifest.load(path).records], ['FIRST'])
                self.assertEqual(list(path.parent.glob('.manifest-*.tmp')), [])
                self.assertFalse(path.with_suffix('.json.lock').exists())

    def test_exclusive_lock_contention_fails_closed_without_artifacts(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'manifest.json'
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                Manifest('000001').save(path)
                original = path.read_bytes()
                lock = path.with_suffix('.json.lock')
                lock.write_text('occupied', encoding='utf-8')
                manifest = Manifest.load(path)
                manifest.add('so', 'orders', '1', 'SAFE', {})
                with self.assertRaises(PreflightError): manifest.save(path)
                self.assertEqual(path.read_bytes(), original)
                self.assertEqual(lock.read_text(encoding='utf-8'), 'occupied')
                self.assertEqual(list(path.parent.glob('.manifest-*.tmp')), [])
                lock.unlink()

    def test_failed_replace_preserves_manifest_and_cleans_temp_without_secrets(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'manifest.json'
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                Manifest('000001').save(path)
                original = path.read_bytes()
                manifest = Manifest.load(path)
                manifest.add('so', 'orders', '1', 'SAFE', {})
                with patch.dict('os.environ', {'NEXUS_FULL_FLOW_TOKEN': 'sensitive-token'}), \
                     patch.object(erp_flow_runner.os, 'replace', side_effect=OSError('disk failure')):
                    with self.assertRaises(OSError): manifest.save(path)
                self.assertEqual(path.read_bytes(), original)
                self.assertEqual(list(path.parent.glob('.manifest-*.tmp')), [])
                self.assertFalse(path.with_suffix('.json.lock').exists())
                self.assertNotIn(b'sensitive-token', b''.join(f.read_bytes() for f in path.parent.iterdir()))

    def test_load_rejects_tampered_duplicate(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / '.codex' / 'full-flow-20260928' / 'manifest.json'
            path.parent.mkdir(parents=True)
            record = {'domain': 'so', 'table': 'so_customer_order', 'entityId': '1', 'businessCode': 'A',
                      'tenantId': '000001', 'parentRefs': {}, 'createdAt': '2026-09-28T00:00:00Z', 'updatedAt': '2026-09-28T00:00:00Z'}
            path.write_text(json.dumps({'tenantId': '000001', 'records': [record, record]}), encoding='utf-8')
            with patch.object(erp_flow_runner, "MANIFEST_DIR", path.parent):
                with self.assertRaises(ValueError): Manifest.load(path)

    def test_load_rejects_invalid_root_or_records_container_without_mutation(self):
        """拒绝非法根和清单容器，保留原文与内存；合法空列表保留原始摘要。"""
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / '.codex' / 'full-flow-20260928' / 'manifest.json'
            path.parent.mkdir(parents=True)
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                valid_raw = b'{\n  "tenantId": "000001",\n  "records": []\n}\n'
                path.write_bytes(valid_raw)
                held = Manifest.load(path)
                self.assertEqual(held.tenant_id, '000001')
                self.assertEqual(held.records, [])
                self.assertEqual(held._source_digest, hashlib.sha256(valid_raw).digest())
                self.assertEqual(path.read_bytes(), valid_raw)
                retained = held.add('so', 'so_customer_order', '9007199254740993', 'CO-KEEP', {})
                before, records, digest = deepcopy(held.records), held.records, held._source_digest
                invalid_documents = (
                    ('root_empty_list', []),
                    ('root_nonempty_list', [{'tenantId': '000001', 'records': []}]),
                    ('root_empty_string', ''), ('root_nonempty_string', 'manifest'),
                    ('root_null', None), ('root_integer', 0), ('root_float', 1.5),
                    ('root_false', False), ('root_true', True),
                    ('records_missing', {'tenantId': '000001'}),
                ) + tuple(
                    (label, {'tenantId': '000001', 'records': container})
                    for label, container in (
                        ('records_empty_object', {}), ('records_nonempty_object', {'unexpected': 'value'}),
                        ('records_empty_string', ''), ('records_nonempty_string', 'not-a-list'),
                        ('records_null', None), ('records_integer', 0), ('records_float', 1.5),
                        ('records_false', False), ('records_true', True),
                    )
                )
                for label, document in invalid_documents:
                    with self.subTest(document=label):
                        raw = (json.dumps(document, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
                        path.write_bytes(raw)
                        load_error = None
                        try:
                            Manifest.load(path)
                        except Exception as error:
                            load_error = error
                        self.assertEqual(path.read_bytes(), raw)
                        self.assertIs(held.records, records)
                        self.assertEqual(held.records, before)
                        self.assertIs(held.records[0], retained)
                        self.assertEqual(held.tenant_id, '000001')
                        self.assertEqual(held._source_digest, digest)
                        self.assertEqual([entry.name for entry in path.parent.iterdir()], ['manifest.json'])
                        self.assertIsInstance(load_error, ValueError, '非法 JSON 根或 records 容器必须明确抛出 ValueError')


class ManifestVerifiedTests(unittest.TestCase):
    """约束独立读回后的清单恢复，不替代业务校验或签发写权限。"""

    @staticmethod
    def _verified_work_order():
        """构造字符串主键与完整父引用的离线已核验事实。"""
        return {
            'tenantId': '000001', 'domain': 'mes', 'table': 'pro_work_order',
            'entityId': '9007199254740993', 'businessCode': 'WO-VERIFIED-1',
            'parentRefs': {'erpWoId': '9007199254740901', 'materialId': '9007199254740902'},
        }

    def _record_verified(self, manifest, verified):
        """先断言恢复入口存在，再显式传递读回字段，不补全租户或猜主键。"""
        self.assertTrue(callable(getattr(manifest, 'record_verified', None)), '缺少可调用的 record_verified 恢复入口')
        return manifest.record_verified(
            verified['domain'], verified['table'], verified['entityId'],
            verified['businessCode'], verified['parentRefs'], tenant_id=verified['tenantId'],
        )

    def _existing_work_order(self):
        """保留两个不同的历史时间戳，便于发现恢复时的静默覆盖。"""
        verified = self._verified_work_order()
        manifest = Manifest(verified['tenantId'])
        record = manifest.add(
            verified['domain'], verified['table'], verified['entityId'],
            verified['businessCode'], verified['parentRefs'],
        )
        record['createdAt'] = '2026-09-28T00:00:00Z'
        record['updatedAt'] = '2026-09-29T01:02:03Z'
        return manifest, verified, record

    def test_record_verified_backfills_unlisted_identity_once_without_parent_alias(self):
        """未入清单的已核验事实仅新增一条证据，外部父引用不能回改清单。"""
        verified = self._verified_work_order()
        manifest = Manifest(verified['tenantId'])
        record = self._record_verified(manifest, verified)
        self.assertIs(record, manifest.records[0])
        for field, value in verified.items():
            self.assertEqual(record[field], value)
        self.assertEqual(record['createdAt'], record['updatedAt'])
        self.assertTrue(record['createdAt'])
        self.assertIsNot(record['parentRefs'], verified['parentRefs'])
        before = deepcopy(manifest.records)
        self.assertIs(self._record_verified(manifest, deepcopy(verified)), record)
        self.assertEqual(manifest.records, before)
        verified['parentRefs']['erpWoId'] = '9007199254740903'
        self.assertEqual(manifest.records, before)

    def test_record_verified_reuses_exact_record_without_refreshing_timestamps(self):
        """键值相同的父引用不依赖键顺序，复用同一对象且保留原始时间戳。"""
        manifest, verified, original = self._existing_work_order()
        before = deepcopy(manifest.records)
        verified['parentRefs'] = dict(reversed(list(verified['parentRefs'].items())))
        self.assertIs(self._record_verified(manifest, verified), original)
        self.assertEqual(manifest.records, before)
        self.assertIsNone(manifest._source_digest)

    def test_record_verified_reuses_after_save_reload_without_changing_file_or_digest(self):
        """保存重载后的完全匹配仍复用原记录，保留文件内容及其比较交换基线。"""
        manifest, verified, _ = self._existing_work_order()
        expected = deepcopy(manifest.records)
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'manifest.json'
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                manifest.save(path)
                before = path.read_bytes()
                loaded = Manifest.load(path)
                digest = loaded._source_digest
                self.assertIs(self._record_verified(loaded, verified), loaded.records[0])
                self.assertEqual(loaded.records, expected)
                self.assertEqual(loaded._source_digest, digest)
                self.assertEqual(path.read_bytes(), before)
                loaded.save(path)
                self.assertEqual(path.read_bytes(), before)
                self.assertEqual(Manifest.load(path).records, expected)
                self.assertFalse(path.with_suffix('.json.lock').exists())
                self.assertEqual(list(path.parent.glob('.manifest-*.tmp')), [])

    def test_record_verified_rejects_id_code_conflicts_without_mutating_saved_evidence(self):
        """同编码不同主键或同主键不同编码均失败，内存与持久化证据都不覆盖。"""
        manifest, verified, _ = self._existing_work_order()
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'manifest.json'
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                manifest.save(path)
                loaded = Manifest.load(path)
                before, digest, content = deepcopy(loaded.records), loaded._source_digest, path.read_bytes()
                for field, value in (
                    ('entityId', '9007199254740994'), ('businessCode', 'WO-VERIFIED-2'),
                ):
                    with self.subTest(field=field):
                        conflict = deepcopy(verified)
                        conflict[field] = value
                        with self.assertRaises(ValueError):
                            self._record_verified(loaded, conflict)
                        self.assertEqual(loaded.records, before)
                        self.assertEqual(loaded._source_digest, digest)
                        self.assertEqual(path.read_bytes(), content)
                loaded.save(path)
                self.assertEqual(path.read_bytes(), content)

    def test_record_verified_rejects_any_parent_reference_change_without_overwrite(self):
        """父引用的值、键、增删均须完全匹配，不允许覆盖旧归属。"""
        manifest, verified, _ = self._existing_work_order()
        before = deepcopy(manifest.records)
        for parents in (
            {'erpWoId': '9007199254740903', 'materialId': '9007199254740902'},
            {**verified['parentRefs'], 'planId': '9007199254740904'},
            {'erpWoId': '9007199254740901'},
            {'sourceWoId': '9007199254740901', 'materialId': '9007199254740902'},
            {},
        ):
            with self.subTest(parents=parents):
                conflict = {**verified, 'parentRefs': parents}
                with self.assertRaises(ValueError):
                    self._record_verified(manifest, conflict)
                self.assertEqual(manifest.records, before)
                self.assertIsNone(manifest._source_digest)

    def test_record_verified_rejects_other_tenant_for_new_and_existing_identity(self):
        """跨租户事实无论是否已入清单都拒绝，不按清单租户重新归属。"""
        for existing in (False, True):
            with self.subTest(existing=existing):
                manifest, verified, _ = self._existing_work_order()
                if not existing:
                    manifest = Manifest(verified['tenantId'])
                before = deepcopy(manifest.records)
                foreign = {**verified, 'tenantId': '000002'}
                with self.assertRaises(ValueError):
                    self._record_verified(manifest, foreign)
                self.assertEqual(manifest.tenant_id, '000001')
                self.assertEqual(manifest.records, before)
                self.assertIsNone(manifest._source_digest)

    def test_record_verified_rejects_foreign_tenant_record_in_memory(self):
        """旧记录租户与清单不一致时也不能被专用恢复入口复用或修正。"""
        manifest, verified, original = self._existing_work_order()
        original['tenantId'] = '000002'
        before = deepcopy(manifest.records)
        with self.assertRaises(ValueError):
            self._record_verified(manifest, verified)
        self.assertEqual(manifest.records, before)

    def test_record_verified_requires_explicit_verified_tenant(self):
        """缺少读回租户参数必须失败，不使用清单租户作为默认核验结果。"""
        manifest, verified, _ = self._existing_work_order()
        before = deepcopy(manifest.records)
        self.assertTrue(callable(getattr(manifest, 'record_verified', None)), '缺少可调用的 record_verified 恢复入口')
        with self.assertRaises(TypeError):
            manifest.record_verified(
                verified['domain'], verified['table'], verified['entityId'],
                verified['businessCode'], verified['parentRefs'],
            )
        self.assertEqual(manifest.records, before)

    def test_record_verified_validates_fields_before_new_or_duplicate_lookup(self):
        """新增与重复恢复都执行原有格式校验，数字主键不能被自动转为字符串。"""
        invalid_fields = (
            ('domain', ''), ('domain', 'mes/other'), ('domain', 1),
            ('table', 'pro/work/order'), ('table', False),
            ('entityId', 1), ('entityId', 9007199254740993), ('entityId', True),
            ('entityId', ''), ('entityId', '-1'),
            ('businessCode', ''), ('businessCode', 'WO INVALID'), ('businessCode', None),
            ('parentRefs', None), ('parentRefs', []), ('parentRefs', {'bad-key': '1'}),
            ('parentRefs', {'erpWoId': 1}), ('parentRefs', {'erpWoId': 9007199254740993}),
            ('parentRefs', {'erpWoId': True}), ('parentRefs', {'erpWoId': 'not-an-id'}),
            ('tenantId', 1), ('tenantId', True), ('tenantId', ''), ('tenantId', 'not-a-tenant'),
        )
        for existing in (False, True):
            for field, value in invalid_fields:
                with self.subTest(existing=existing, field=field, value=value):
                    manifest, verified, _ = self._existing_work_order()
                    if not existing:
                        manifest = Manifest(verified['tenantId'])
                    before = deepcopy(manifest.records)
                    invalid = deepcopy(verified)
                    invalid[field] = value
                    with self.assertRaises(ValueError):
                        self._record_verified(manifest, invalid)
                    self.assertEqual(manifest.records, before)
                    self.assertIsNone(manifest._source_digest)

    def test_record_verified_matches_domain_and_table_without_migrating_records(self):
        """相同主键和编码仍按领域及表归属匹配，不复用或迁移其他表的记录。"""
        verified = self._verified_work_order()
        manifest = Manifest(verified['tenantId'])
        originals = []
        for domain, table in (
            ('erp', 'pro_work_order'), ('mes', 'pro_flow_card'), ('mes', 'pro_work_order'),
        ):
            originals.append(manifest.add(
                domain, table, verified['entityId'], verified['businessCode'], verified['parentRefs'],
            ))
        before = deepcopy(manifest.records)
        for original in originals:
            fact = {**verified, 'domain': original['domain'], 'table': original['table']}
            self.assertIs(self._record_verified(manifest, fact), original)
        self.assertEqual(manifest.records, before)

    def test_add_still_rejects_exact_duplicate_before_and_after_verified_recovery(self):
        """严格新增接口在恢复前后都拒绝完全重复，幂等语义仅属于专用入口。"""
        manifest, verified, original = self._existing_work_order()
        before = deepcopy(manifest.records)
        with self.assertRaises(ValueError):
            manifest.add(
                verified['domain'], verified['table'], verified['entityId'],
                verified['businessCode'], verified['parentRefs'],
            )
        self.assertIs(self._record_verified(manifest, verified), original)
        with self.assertRaises(ValueError):
            manifest.add(
                verified['domain'], verified['table'], verified['entityId'],
                verified['businessCode'], verified['parentRefs'],
            )
        self.assertEqual(manifest.records, before)

    def test_record_verified_does_not_refresh_stale_file_cas_baseline(self):
        """旧实例即使重新核验并复用记录，也不能覆盖另一个实例已保存的新证据。"""
        manifest, verified, _ = self._existing_work_order()
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'manifest.json'
            with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                manifest.save(path)
                first, stale = Manifest.load(path), Manifest.load(path)
                digest = stale._source_digest
                another = {**verified, 'entityId': '9007199254740994', 'businessCode': 'WO-VERIFIED-2'}
                self._record_verified(first, another)
                first.save(path)
                content = path.read_bytes()
                self.assertIs(self._record_verified(stale, verified), stale.records[0])
                self.assertEqual(stale._source_digest, digest)
                with self.assertRaises(PreflightError):
                    stale.save(path)
                self.assertEqual(path.read_bytes(), content)
                self.assertEqual(Manifest.load(path).records, first.records)
                self.assertEqual(len(first.records), 2)
                self.assertFalse(path.with_suffix('.json.lock').exists())
                self.assertEqual(list(path.parent.glob('.manifest-*.tmp')), [])

    def test_independent_mes_readback_backfills_only_manifest_without_new_business_rows(self):
        """四类正式记录独立读回后仅回填清单；完整、部分及空清单恢复均无业务写入。"""
        specs = (
            ('pro_work_order', '/mes/pro/work', 'mesWoId', 'woNo',
             '9007199254740993', 'WO-VERIFIED-1', {'erpWoId': '9007199254740901'}),
            ('pro_flow_card', '/mes/pro/flow-card', 'cardId', 'cardNo',
             '9007199254740994', 'CARD-VERIFIED-1', {'mesWoId': '9007199254740993'}),
            ('pro_dispatch', '/mes/pro/dispatch', 'dispatchId', 'dispatchNo',
             '9007199254740995', 'DISPATCH-VERIFIED-1',
             {'mesWoId': '9007199254740993', 'cardId': '9007199254740994', 'stepRecordId': '9007199254740905'}),
            ('pro_sn', '/mes/pro/sn', 'snId', 'snCode',
             '9007199254740996', 'SN-VERIFIED-1',
             {'mesWoId': '9007199254740993', 'cardId': '9007199254740994'}),
        )
        rows = {
            base + '/' + entity_id: {
                'tenantId': '000001', id_field: entity_id, code_field: code, **parents,
            }
            for _, base, id_field, code_field, entity_id, code, parents in specs
        }

        class ReadBackApi:
            """独立保存离线详情，读取不引用清单，任何业务写请求立即失败。"""

            def __init__(self):
                """单独复制业务事实并记录全部请求，不连接真实服务。"""
                self.rows, self.calls, self.write_calls = deepcopy(rows), [], []

            def get(self, path):
                """返回详情副本，避免清单变更通过共享字典污染读回证据。"""
                self.calls.append(('GET', path))
                return {'code': 200, 'data': deepcopy(self.rows[path])}

            def post(self, *args, **kwargs):
                """恢复流程若尝试重新创建业务行则立即失败。"""
                self.write_calls.append('POST')
                raise AssertionError('恢复不得创建业务行')

            def put(self, *args, **kwargs):
                """恢复流程若尝试改写正式业务事实则立即失败。"""
                self.write_calls.append('PUT')
                raise AssertionError('恢复不得改写业务行')

        for initial_count in (4, 2, 0):
            with self.subTest(initial_count=initial_count), tempfile.TemporaryDirectory() as root:
                api = ReadBackApi()
                business_before = deepcopy(api.rows)
                manifest = Manifest('000001')
                for table, _, _, _, entity_id, code, parents in specs[:initial_count]:
                    record = manifest.add('mes', table, entity_id, code, parents)
                    record['createdAt'] = '2026-09-28T00:00:00Z'
                    record['updatedAt'] = '2026-09-29T01:02:03Z'
                existing_before = deepcopy(manifest.records)
                path = Path(root) / 'manifest.json'
                with patch.object(erp_flow_runner, 'MANIFEST_DIR', path.parent):
                    manifest.save(path)
                    manifest = Manifest.load(path)
                    recovered = None
                    for _ in range(2):
                        for table, base, id_field, code_field, entity_id, _, parents in specs:
                            data = api.get(base + '/' + entity_id)['data']
                            self.assertEqual(data['tenantId'], manifest.tenant_id)
                            self.assertEqual(data[id_field], entity_id)
                            self._record_verified(manifest, {
                                'tenantId': data['tenantId'], 'domain': 'mes', 'table': table,
                                'entityId': data[id_field], 'businessCode': data[code_field],
                                'parentRefs': {key: data[key] for key in parents},
                            })
                        self.assertEqual(len(manifest.records), 4)
                        self.assertEqual(manifest.records[:initial_count], existing_before)
                        if recovered is None:
                            recovered = deepcopy(manifest.records)
                        else:
                            self.assertEqual(manifest.records, recovered)
                        manifest.save(path)
                        manifest = Manifest.load(path)
                    self.assertEqual(manifest.records, recovered)
                self.assertEqual(api.rows, business_before)
                self.assertEqual(len(api.rows), 4)
                self.assertEqual(api.write_calls, [])
                self.assertEqual(api.calls, [('GET', base + '/' + entity_id)
                                            for _ in range(2) for _, base, _, _, entity_id, _, _ in specs])

class ApiIdTests(unittest.TestCase):
    def test_accepts_safe_positive_numeric_api_ids_and_canonicalizes(self):
        for value, expected in ((1, '1'), (9007199254740991, '9007199254740991'),
                                ('0008', '0008')):
            with self.subTest(value=value):
                self.assertEqual(_api_id(value, 'API'), expected)

    def test_rejects_unsafe_or_non_integer_api_ids(self):
        for value in (True, False, 0, -1, 1.0, 1e3, 9007199254740992,
                      '1e3', '-1', '+1', '', ' 1', None):
            with self.subTest(value=value), self.assertRaises(PreflightError):
                _api_id(value, 'API')


class PreflightTests(unittest.TestCase):
    def inventory(self, usable=True):
        def cand(columns, row): return {'candidate_columns': columns, 'candidates': row, 'unverified_candidates': []}
        erp = cand({'bd_client': ['tenant_id', 'client_code', 'client_id'], 'wm_warehouse': ['tenant_id', 'warehouse_code', 'warehouse_id'],
                    'bd_bom': ['tenant_id', 'bom_id'], 'bd_routing': ['tenant_id', 'routing_code', 'routing_id'],
                    'bd_material': ['tenant_id', 'material_id']},
                   {'bd_client': [['000001', 'C', '1']], 'wm_warehouse': [['000001', 'W', '4']],
                    'bd_bom': [['000001', '2']], 'bd_routing': [['000001', 'R', '3']],
                    'bd_material': [['000001', '8'], ['000001', '9']]})
        return {'usable_for_write_phase': usable, 'snapshot': {'verified': True, 'db_date': __import__('datetime').date.today().isoformat(), 'grants': 'admin read-only invocation, grants not least privilege verified'}, 'missing': [],
                'schemas': {'ry-erp': erp, 'ry-vue': {'candidate_columns': {'sys_tenant': ['tenant_id'], 'sys_user': ['tenant_id', 'user_id']},
                'candidates': {'sys_tenant': [['000001']], 'sys_user': [['000001', '42']]}}}}

    def client(self):
        class Fake:
            def __init__(self): self.calls = []
            def get(self, path, query=None):
                self.calls.append((path, query))
                if path == '/system/user/getInfo': return {'code': 200, 'data': {'user': {'tenantId': '000001', 'userId': 42}}}
                if path.startswith('/workflow/deploy/publishList'):
                    return {'total': 1, 'rows': [{'definitionId': 'flow:1:10', 'processKey': query['processKey'], 'suspended': False, 'version': 1, 'deploymentId': 'dep-1'}]}
                if path == '/erp/bd/bom/detail/list':
                    return {'total': 1, 'rows': [{'bomId': 2, 'childMaterialId': '9', 'parentMaterialId': 8, 'status': 1}]}
                if path in ('/erp/bd/bom/verify/2', '/erp/bd/routing/verify/3'): return {'code': 200, 'data': 'OK'}
                if path == '/erp/bd/routing/steps/3': return {'code': 200, 'data': [{'stepId': '5'}]}
                mapping = {'/erp/bd/client': ('clientId', 1, 'clientCode', 'C'),
                           '/erp/bd/bom': ('bomId', 2, 'bomVersion', 'v1'),
                           '/erp/bd/routing': ('routingId', 3, 'routingCode', 'R'),
                           '/erp/wm/warehouse': ('warehouseId', 4, 'warehouseCode', 'W')}
                for base, (key, value, code_key, code) in mapping.items():
                    row = {key: value, code_key: code, 'status': 1, 'parentMaterialId': 8, 'materialId': 8,
                           'isCurrent': 1, 'routingId': 3, 'bomVersion': 'v1', 'routingVersion': 'v1'}
                    if path == base + '/list': return {'total': 1, 'rows': [row]}
                    if path == base + '/' + str(value): return {'code': 200, 'data': row}
                raise AssertionError(path)
        return Fake()

    def test_old_admin_inventory_denies_even_with_ack(self):
        client = self.client()
        inventory = self.inventory(False)
        inventory['schemas']['ry-erp']['unverified_candidates'] = ['bd_client', 'wm_warehouse']
        with self.assertRaises(PreflightError): read_only_preflight(client, inventory,
            tenant_id='000001', client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)
        self.assertEqual(client.calls, [])

    def test_admin_snapshot_needs_independent_ack(self):
        client = self.client()
        with self.assertRaises(PreflightError): read_only_preflight(client, self.inventory(False), tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'])
        self.assertEqual(client.calls, [])

    def test_admin_grants_require_ack_even_if_inventory_flag_claims_usable(self):
        client = self.client()
        with self.assertRaises(PreflightError): read_only_preflight(client, self.inventory(True), tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'])
        self.assertEqual(client.calls, [])

    def test_resolves_and_confirms_all_dependencies_without_mutations(self):
        client = self.client()
        result = read_only_preflight(client, self.inventory(), tenant_id='000001', client_code='C',
             bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)
        self.assertEqual(result['customer']['clientId'], '1')
        self.assertEqual(result['userId'], '42')
        self.assertEqual(result['bom']['materialId'], '8')
        self.assertEqual(result['routing']['routingId'], '3')
        self.assertEqual(result['warehouse']['warehouseId'], '4')
        self.assertEqual(result['components'], ['9'])
        self.assertIn(('/erp/bd/routing/steps/3', None), client.calls)
        self.assertIn(('/erp/bd/routing/verify/3', None), client.calls)
        self.assertEqual(result['flowDefinitions']['purchase']['definitionId'], 'flow:1:10')
        self.assertEqual(result['bom']['bomVersion'], 'v1')
        self.assertTrue(all(path == '/system/user/getInfo' or path.startswith(('/erp/', '/workflow/')) for path, _ in client.calls))

    def test_legacy_bom_detail_without_redundant_parent_uses_verified_bom(self):
        client = self.client()
        get = client.get
        def without_redundant_parent(path, query=None):
            result = get(path, query)
            if path == '/erp/bd/bom/detail/list':
                result['rows'][0]['parentMaterialId'] = None
            return result
        client.get = without_redundant_parent
        result = read_only_preflight(client, self.inventory(), tenant_id='000001', client_code='C',
                                     bom_id='2', routing_code='R', warehouse_code='W',
                                     process_keys=['purchase'], human_ack=True)
        self.assertEqual(result['components'], ['9'])

    def test_bom_detail_explicit_wrong_parent_remains_rejected(self):
        client = self.client()
        get = client.get
        def wrong_parent(path, query=None):
            result = get(path, query)
            if path == '/erp/bd/bom/detail/list':
                result['rows'][0]['parentMaterialId'] = 99
            return result
        client.get = wrong_parent
        with self.assertRaises(PreflightError):
            read_only_preflight(client, self.inventory(), tenant_id='000001', client_code='C',
                                bom_id='2', routing_code='R', warehouse_code='W',
                                process_keys=['purchase'], human_ack=True)
    def test_numeric_inventory_candidate_id_is_not_accepted(self):
        inventory = self.inventory()
        inventory['schemas']['ry-erp']['candidates']['bd_client'][0][2] = 1
        with self.assertRaises(PreflightError):
            read_only_preflight(self.client(), inventory, tenant_id='000001', client_code='C',
                                bom_id='2', routing_code='R', warehouse_code='W',
                                process_keys=['purchase'], human_ack=True)

    def test_missing_inventory_id_blocks_without_http_even_with_ack(self):
        inventory = self.inventory(False)
        inventory['schemas']['ry-erp']['candidate_columns']['bd_client'] = ['tenant_id', 'client_code']
        inventory['schemas']['ry-erp']['candidates']['bd_client'] = [['000001', 'C']]
        client = self.client()
        with self.assertRaises(PreflightError): read_only_preflight(client, inventory, tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)
        self.assertEqual(client.calls, [])

    def test_rejects_list_entry_missing_id_as_preflight_error(self):
        client = self.client()
        original = client.get
        def missing_id(path, query=None):
            result = original(path, query)
            if path == '/erp/bd/client/list': result['rows'][0].pop('clientId')
            return result
        client.get = missing_id
        with self.assertRaises(PreflightError): read_only_preflight(client, self.inventory(), tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)
    def test_rejects_bom_routing_material_mismatch(self):
        client = self.client()
        original = client.get
        def mismatched(path, query=None):
            result = original(path, query)
            if path == '/erp/bd/routing/3': result['data']['materialId'] = '99'
            return result
        client.get = mismatched
        with self.assertRaises(PreflightError): read_only_preflight(client, self.inventory(), tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)
    def test_rejects_process_key_string_without_http(self):
        client = self.client()
        with self.assertRaises(PreflightError): read_only_preflight(client, self.inventory(), tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys='purchase', human_ack=True)
        self.assertEqual(client.calls, [])
    def test_rejects_ambiguous_api_result(self):
        client = self.client()
        original = client.get
        def duplicate(path, query=None):
            result = original(path, query)
            if path == '/erp/bd/client/list': result = {'rows': result['rows'] * 2, 'total': 2}
            return result
        client.get = duplicate
        with self.assertRaises(PreflightError): read_only_preflight(client, self.inventory(), tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)

    def test_rejects_component_not_in_inventory(self):
        client = self.client()
        inventory = self.inventory(False)
        inventory['schemas']['ry-erp']['candidates']['bd_material'] = [['000001', '8']]
        with self.assertRaises(PreflightError): read_only_preflight(client, inventory, tenant_id='000001',
            client_code='C', bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)

    def test_rejects_stale_version_and_unverified_component_status(self):
        for path, field, value in (('/erp/bd/bom/2', 'isCurrent', 0),
                                    ('/erp/bd/routing/3', 'routingVersion', ''),
                                    ('/workflow/deploy/publishList', 'version', None),
                                    ('/erp/bd/bom/detail/list', 'status', 0)):
            client = self.client()
            original = client.get
            def stale(p, query=None):
                response = original(p, query)
                if p == path:
                    row = response.get('data') if 'data' in response else response['rows'][0]
                    row[field] = value
                return response
            client.get = stale
            with self.subTest(path=path), self.assertRaises(PreflightError):
                read_only_preflight(client, self.inventory(False), tenant_id='000001', client_code='C',
                    bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)

    def preflight(self, client):
        return read_only_preflight(client, self.inventory(), tenant_id='000001',
                                   client_code='C', bom_id='2', routing_code='R',
                                   warehouse_code='W', process_keys=['purchase'], human_ack=True)

    def with_flow_rows(self, rows, *, truncate=False):
        client = self.client()
        original = client.get
        def get(path, query=None):
            if path == '/workflow/deploy/publishList':
                client.calls.append((path, query))
                page = query['pageNum']
                selected = rows[(page - 1) * 100:page * 100]
                return {'total': len(rows), 'rows': [] if truncate and page > 1 else selected}
            return original(path, query)
        client.get = get
        return client

    def flow(self, version, suspended=False):
        return {'definitionId': f'flow:{version}:10', 'deploymentId': f'dep-{version}',
                'processKey': 'purchase', 'version': version, 'suspended': suspended}

    def test_selects_unique_latest_published_version_even_with_suspended_older(self):
        for older_suspended in (False, True):
            with self.subTest(older_suspended=older_suspended):
                client = self.with_flow_rows([self.flow(1, older_suspended), self.flow(2)])
                self.assertEqual(self.preflight(client)['flowDefinitions']['purchase'],
                                 {'definitionId': 'flow:2:10', 'deploymentId': 'dep-2', 'version': 2})

    def test_rejects_suspended_latest_without_falling_back(self):
        client = self.with_flow_rows([self.flow(1), self.flow(2, True)])
        with self.assertRaises(PreflightError): self.preflight(client)

    def test_rejects_duplicate_latest_and_missing_fields(self):
        cases = [[self.flow(1), self.flow(2), self.flow(2)],
                 [self.flow(1), {**self.flow(2), 'definitionId': None}],
                 [self.flow(1), {**self.flow(2), 'deploymentId': None}],
                 [self.flow(1), {**self.flow(2), 'version': None}]]
        for rows in cases:
            with self.subTest(rows=rows), self.assertRaises(PreflightError):
                self.preflight(self.with_flow_rows(rows))

    def test_paginates_all_flow_versions_before_selecting_latest(self):
        rows = [self.flow(1)] * 100 + [self.flow(2)]
        client = self.with_flow_rows(rows)
        self.assertEqual(self.preflight(client)['flowDefinitions']['purchase']['version'], 2)
        self.assertIn(('/workflow/deploy/publishList', {'processKey': 'purchase', 'pageNum': 2, 'pageSize': 100}), client.calls)
        with self.assertRaises(PreflightError):
            self.preflight(self.with_flow_rows(rows, truncate=True))

    def test_bom_detail_dates_checked_against_snapshot_date(self):
        from datetime import date, timedelta
        today = date.today()
        cases = [(today - timedelta(days=1), None, False),
                 (today + timedelta(days=1), None, True),
                 (None, today - timedelta(days=1), True),
                 (None, today + timedelta(days=1), False),
                 (None, None, False),
                 (today, today, False),
                 ('not-a-date', None, True),
                 ('2026-02-30', None, True),
                 (None, '', True)]
        for effective, expiry, fails in cases:
            with self.subTest(effective=effective, expiry=expiry):
                client = self.client()
                original = client.get
                def get(path, query=None):
                    result = original(path, query)
                    if path == '/erp/bd/bom/detail/list':
                        result['rows'][0].update(effectiveDate=effective.isoformat() if isinstance(effective, date) else effective,
                                                 expiryDate=expiry.isoformat() if isinstance(expiry, date) else expiry)
                    return result
                client.get = get
                if fails:
                    with self.assertRaises(PreflightError): self.preflight(client)
                else:
                    self.assertEqual(self.preflight(client)['components'], ['9'])

    def test_main_bom_routing_reject_malformed_full_date_even_with_valid_prefix(self):
        for resource in ('/erp/bd/bom/2', '/erp/bd/routing/3'):
            for field, value in (('effectiveDate', '2026-09-01garbage'),
                                 ('expiryDate', '2026-12-01T99:99:99'),
                                 ('effectiveDate', '2026-02-30T00:00:00')):
                with self.subTest(resource=resource, field=field, value=value):
                    client = self.client()
                    original = client.get
                    def get(path, query=None):
                        result = original(path, query)
                        if path == resource: result['data'][field] = value
                        return result
                    client.get = get
                    with self.assertRaises(PreflightError): self.preflight(client)

    def test_main_bom_and_routing_accept_full_expected_date_formats(self):
        snapshot_date = self.inventory()['snapshot']['db_date']
        for resource in ('/erp/bd/bom/2', '/erp/bd/routing/3'):
            for value in (snapshot_date, snapshot_date + ' 00:00:00',
                          snapshot_date + 'T12:30:00Z', snapshot_date + 'T12:30:00.123+08:00'):
                with self.subTest(resource=resource, value=value):
                    client = self.client()
                    original = client.get
                    def get(path, query=None):
                        result = original(path, query)
                        if path == resource: result['data']['effectiveDate'] = value
                        return result
                    client.get = get
                    self.assertTrue(self.preflight(client)['readOnlyReview'])

    def test_rejects_missing_bom_details_or_flow_definition(self):
        for missing in ('/erp/bd/bom/detail/list', '/workflow/deploy/publishList'):
            client = self.client()
            original = client.get
            client.get = lambda path, query=None: {'rows': [], 'total': 0} if path == missing else original(path, query)
            with self.subTest(missing=missing), self.assertRaises(PreflightError):
                read_only_preflight(client, self.inventory(), tenant_id='000001', client_code='C',
                  bom_id='2', routing_code='R', warehouse_code='W', process_keys=['purchase'], human_ack=True)


class SqlAssertionTests(unittest.TestCase):
    @staticmethod
    def _sql_without_documentation(sql):
        """仅处理固定只读断言样本的文档注释，不作为通用 SQL 解析器或执行授权。"""
        return re.sub(r"/\*(?![!+]).*?\*/", "", sql, flags=re.S)

    def test_sql_documentation_filter_preserves_executable_content(self):
        select = "SELECT ?;"
        self.assertEqual(self._sql_without_documentation("/** 只读核验说明。 */\n" + select).strip(), select)
        for executable in ("UPDATE `sample`;", "/*!50700 UPDATE `sample` SET `version` = 2 */;", "/*+ 提示 */ SELECT ?;"):
            self.assertEqual(self._sql_without_documentation(executable), executable)

    def test_selected_columns_match_ry_erp_ddl(self):
        import re
        ddl = (Path(__file__).resolve().parents[4] / 'script/sql/ry-erp.sql').read_text(encoding='utf-8')
        sql = (Path(__file__).resolve().parent.parent / 'assertions/erp_assertions.sql').read_text(encoding='utf-8')
        sql = self._sql_without_documentation(sql)
        for statement in (x.strip() for x in sql.splitlines() if x.strip().startswith('SELECT ')):
            table = re.search(r'\bFROM\s+(\w+)', statement).group(1)
            match = re.search(r'CREATE TABLE `'+table+r'`\s*\((.*?)\) ENGINE', ddl, re.S)
            self.assertIsNotNone(match, table)
            columns = set(re.findall(r'^\s*`(\w+)`\s', match.group(1), re.M))
            selected = re.search(r'^SELECT\s+(.*?)\s+FROM', statement).group(1)
            refs = set(re.findall(r'[a-z][a-z0-9_]*', selected + ' ' + re.search(r'\bWHERE\s+(.*?);$', statement).group(1)))
            self.assertFalse((refs - {'count','sum','coalesce','as','and'}) - columns, statement)

    def test_all_executable_statements_are_selects_with_bound_parameters(self):
        sql = (Path(__file__).resolve().parent.parent / 'assertions' / 'erp_assertions.sql').read_text(encoding='utf-8')
        sql = self._sql_without_documentation(sql)
        statements = [line.strip() for line in sql.splitlines() if line.strip() and not line.lstrip().startswith('--')]
        self.assertGreaterEqual(len(statements), 4)
        for statement in statements:
            self.assertTrue(statement.startswith('SELECT '), statement)
            self.assertTrue(statement.endswith(';'))
            self.assertEqual(statement.count('?'), 2)
            self.assertIn('tenant_id = ?', statement)

if __name__ == '__main__': unittest.main()
