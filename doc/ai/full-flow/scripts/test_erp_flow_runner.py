import re
import io
import json
import tempfile
import unittest
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
