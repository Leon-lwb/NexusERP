"""Signed S3 requests and privacy/integrity rejection tests (fake HTTP only)."""
import hashlib
import io
import pathlib
import sys
import unittest
import urllib.error
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from storage import S3Settings, S3Transport, SafetyError

DATA = b'%PDF-1.7\nReference test content'
ARTIFACT = {'objectKey': 'mes/sop/000000/reference-20261008/SOP_TEST/V1.1/' + hashlib.sha256(DATA).hexdigest() + '.pdf',
            'sha256': hashlib.sha256(DATA).hexdigest(), 'sm3': hashlib.new('sm3', DATA).hexdigest(), 'fileSize': len(DATA)}


class Response:
    status = 200
    def __init__(self, data=b'', headers=None):
        self.data = data
        self.headers = headers or {'content-type': 'application/pdf', 'content-length': str(len(DATA)), 'x-amz-meta-sha256': ARTIFACT['sha256']}
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, limit): return self.data[:limit]


class StorageTests(unittest.TestCase):
    def config(self): return S3Settings('159.75.14.226:9005', 'sop', 'private-access', 'private-secret')

    def test_only_local_endpoint_private_bucket_and_region_allowed(self):
        for args in [('remote:9005','ruoyi','a','s'),('127.0.0.1:9005','public','a','s'),('159.75.14.226:9005','sop','','s')]:
            with self.assertRaises(SafetyError): S3Settings(*args)
        with self.assertRaises(SafetyError): S3Settings('159.75.14.226:9005','sop','a','s','remote-region')

    def test_signed_request_stays_in_scope_and_never_puts_secret_in_url(self):
        captured = []
        def opener(req, timeout): captured.append(req); return Response()
        t = S3Transport(self.config(), opener)
        t._request('HEAD', ARTIFACT['objectKey'])
        req = captured[0]
        self.assertTrue(req.full_url.startswith('http://159.75.14.226:9005/sop/mes/sop/000000/'))
        self.assertNotIn('private-access', req.full_url)
        self.assertNotIn('private-secret', req.full_url)
        self.assertIn('AWS4-HMAC-SHA256', req.headers['Authorization'])
        self.assertNotIn('private-secret', req.headers['Authorization'])
        for method,key in [('DELETE',ARTIFACT['objectKey']),('PUT','../outside'),('GET','other-tenant/file.pdf')]:
            with self.assertRaises(SafetyError): t._request(method,key)

    def test_immutable_put_is_conditional_and_existing_object_not_overwritten(self):
        t = S3Transport(self.config())
        t._privacy_keys = frozenset({ARTIFACT['objectKey']})  # Preflight is separately tested.
        with patch.object(t, '_request', side_effect=[(404,{},b''),(200,{},b'')]) as request, patch.object(t, 'verify') as verify:
            t.upload_immutable(ARTIFACT,DATA)
            self.assertEqual('PUT',request.call_args_list[1].args[0])
            self.assertEqual('*',request.call_args_list[1].args[3]['if-none-match'])
            self.assertEqual(ARTIFACT['sha256'],request.call_args_list[1].args[3]['x-amz-meta-sha256'])
            verify.assert_called_once_with(ARTIFACT)
        with patch.object(t, '_request', return_value=(200,{},b'')) as request, patch.object(t,'verify'):
            t.upload_immutable(ARTIFACT,DATA)
            self.assertEqual(['HEAD'],[call.args[0] for call in request.call_args_list])

    def test_verify_reads_full_bytes_and_checks_anonymous_denial(self):
        headers = {'content-type':'application/pdf','content-length':str(len(DATA)),'x-amz-meta-sha256':ARTIFACT['sha256']}
        t = S3Transport(self.config())
        with patch.object(t,'_request',side_effect=[(200,headers,b''),(200,{},DATA),(403,{},b''),(403,{},b'')]):
            self.assertEqual(DATA,t.verify(ARTIFACT))
        with patch.object(t,'_request',side_effect=[(200,headers,b''),(200,{},DATA),(200,{},b'')]):
            with self.assertRaises(SafetyError):t.verify(ARTIFACT)

    def test_metadata_content_type_size_or_download_mismatch_reject(self):
        good={'content-type':'application/pdf','content-length':str(len(DATA)),'x-amz-meta-sha256':ARTIFACT['sha256']}
        for key,value in [('content-type','text/html'),('content-length','1'),('x-amz-meta-sha256','bad')]:
            headers={**good,key:value};t=S3Transport(self.config())
            with patch.object(t,'_request',return_value=(200,headers,b'')),self.assertRaises(SafetyError):t.verify(ARTIFACT)
        t=S3Transport(self.config())
        with patch.object(t,'_request',side_effect=[(200,good,b''),(200,{},DATA+b'corruption')]),self.assertRaises(SafetyError):t.verify(ARTIFACT)

    def test_service_errors_do_not_echo_body_or_credentials(self):
        def opener(req,timeout):raise urllib.error.HTTPError(req.full_url,403,'private-secret',{},io.BytesIO(b'private-secret'))
        with self.assertRaises(SafetyError) as error:S3Transport(self.config(),opener)._request('GET',ARTIFACT['objectKey'])
        self.assertEqual('MINIO_HTTP_403',str(error.exception))
        self.assertNotIn('private-secret',str(error.exception))

    def test_network_or_redirect_errors_are_suppressed(self):
        def opener(req,timeout):raise OSError('private-secret')
        with self.assertRaises(SafetyError) as error:S3Transport(self.config(),opener)._request('GET',ARTIFACT['objectKey'])
        self.assertEqual('MINIO_TRANSPORT_UNAVAILABLE',str(error.exception))


if __name__ == '__main__':unittest.main()
