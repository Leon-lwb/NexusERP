"""Bounded transport-only read retries; never retry writes or security/integrity rejections."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parent))
from storage import S3Settings,S3Transport,SafetyError

KEY='mes/sop/000000/reference-20261008/SOP_TEST/V1.1/'+'a'*64+'.pdf'

class ReadRetryTests(unittest.TestCase):
    def target(self):return S3Transport(S3Settings('159.75.14.226:9005','sop','private-access','private-secret'))
    def test_read_retries_only_transport_failures_with_fixed_limit(self):
        for method in ['HEAD','GET']:
            t=self.target()
            with patch.object(t,'_request_once',side_effect=[SafetyError('MINIO_TRANSPORT_UNAVAILABLE'),(200,{},b'data')]) as call,patch('storage.time.sleep'):
                self.assertEqual((200,{},b'data'),t._request(method,KEY))
                self.assertEqual(2,call.call_count)
                self.assertTrue(all(c.args[0]==method for c in call.call_args_list))
            with patch.object(t,'_request_once',side_effect=SafetyError('MINIO_TRANSPORT_UNAVAILABLE')) as call,patch('storage.time.sleep'),self.assertRaises(SafetyError):
                t._request(method,KEY)
            self.assertEqual(3,call.call_count)

    def test_put_never_retries_even_when_transport_unavailable(self):
        t=self.target()
        with patch.object(t,'_request_once',side_effect=SafetyError('MINIO_TRANSPORT_UNAVAILABLE')) as call,patch('storage.time.sleep') as sleep,self.assertRaises(SafetyError):
            t._request('PUT',KEY,b'%PDF-test')
        self.assertEqual(1,call.call_count);sleep.assert_not_called()

    def test_scope_auth_integrity_and_privacy_errors_are_never_retried(self):
        for category in ['MINIO_HTTP_403','MINIO_OBJECT_SCOPE_REJECTED','MINIO_OBJECT_NOT_PRIVATE','OBJECT_SHA256_MISMATCH']:
            t=self.target()
            with patch.object(t,'_request_once',side_effect=SafetyError(category)) as call,patch('storage.time.sleep') as sleep,self.assertRaises(SafetyError) as caught:
                t._request('GET',KEY)
            self.assertEqual(category,str(caught.exception));self.assertEqual(1,call.call_count);sleep.assert_not_called()

    def test_anonymous_access_denial_is_success_not_retry_or_bypass(self):
        t=self.target()
        with patch.object(t,'_request_once',return_value=(403,{},b'')) as call:
            self.assertEqual(403,t._request('GET',KEY,anonymous=True)[0])
        self.assertEqual(1,call.call_count)

if __name__=='__main__':unittest.main()
