"""Explicit remote SOP bucket transport; no deletion, policy/ACL changes or redirects."""
from dataclasses import dataclass, field
import datetime as dt
import hashlib
import json
import fnmatch
import hmac
import re
import time
import urllib.error
import urllib.parse
import urllib.request


class SafetyError(RuntimeError):
    """Only fixed diagnostic categories, never raw service bodies/credentials."""


def require(condition, category):
    if not condition:
        raise SafetyError(category)


@dataclass(frozen=True)
class S3Settings:
    endpoint: str
    bucket: str
    access_key: str = field(repr=False)
    secret_key: str = field(repr=False)
    region: str = 'us-east-1'

    def __post_init__(self):
        require(self.endpoint in ('159.75.14.226:9005', '127.0.0.1:9005', 'localhost:9005'), 'MINIO_AUTHORIZED_TARGET_REQUIRED')
        require(self.bucket in ('sop', 'ruoyi') and bool(self.access_key) and bool(self.secret_key), 'MINIO_CONFIG_REJECTED')
        require(self.region == 'us-east-1', 'MINIO_REGION_REJECTED')


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise SafetyError('MINIO_REDIRECT_FORBIDDEN')


def _sign(key, value):
    return hmac.new(key, value.encode('utf-8'), hashlib.sha256).digest()


class S3Transport:
    MAX_BYTES = 16 * 1024 * 1024

    def __init__(self, settings, opener=None):
        self.settings = settings
        self._privacy_keys = frozenset()
        self.opener = opener or urllib.request.build_opener(urllib.request.ProxyHandler({}), _NoRedirect()).open

    def _request(self, method, key, body=b'', extra=None, anonymous=False, bucket_policy=False, bucket_root=False, policy_bucket=None):
        attempts = 3 if method in ('HEAD', 'GET') else 1
        for attempt in range(attempts):
            try:
                return self._request_once(method, key, body, extra, anonymous=anonymous, bucket_policy=bucket_policy,
                                          bucket_root=bucket_root, policy_bucket=policy_bucket)
            except SafetyError as error:
                if str(error) != 'MINIO_TRANSPORT_UNAVAILABLE' or attempt + 1 == attempts:
                    raise
                time.sleep(0.25 * (attempt + 1))

    def _request_once(self, method, key, body=b'', extra=None, anonymous=False, bucket_policy=False, bucket_root=False, policy_bucket=None):
        require(method in ('HEAD', 'GET', 'PUT'), 'MINIO_METHOD_FORBIDDEN')
        require(not (bucket_policy and bucket_root), 'MINIO_REQUEST_MODE_REJECTED')
        require(policy_bucket is None or bucket_policy and method == 'GET' and policy_bucket == 'ruoyi', 'SHARED_BUCKET_READ_ONLY')
        bucket = policy_bucket or self.settings.bucket
        if bucket_root:
            require(key == '' and method in ('HEAD','PUT') and not anonymous and not extra and not body, 'BUCKET_ROOT_OPERATION_REJECTED')
        elif bucket_policy:
            require(method == 'GET' and key == '' and not body, 'BUCKET_POLICY_READ_ONLY')
        else:
            require(re.fullmatch(r'mes/sop/000000/reference-20261008/[A-Za-z0-9_-]+/V1\.1/[a-f0-9]{64}\.pdf', key) is not None, 'MINIO_OBJECT_SCOPE_REJECTED')
        if method == 'PUT':
            require(bucket == 'sop' and self.settings.endpoint == '159.75.14.226:9005', 'WRITE_ONLY_AUTHORIZED_REMOTE_SOP_BUCKET')
        require(len(body) <= self.MAX_BYTES, 'MINIO_BODY_TOO_LARGE')
        cfg = self.settings
        uri = '/' + bucket if bucket_policy or bucket_root else '/' + bucket + '/' + urllib.parse.quote(key, safe='/~')
        query = 'policy=' if bucket_policy else ''
        headers = dict(extra or {})
        if not anonymous:
            now = dt.datetime.now(dt.timezone.utc)
            amzdate = now.strftime('%Y%m%dT%H%M%SZ')
            date = now.strftime('%Y%m%d')
            payload_hash = hashlib.sha256(body).hexdigest()
            headers.update({'host': cfg.endpoint, 'x-amz-content-sha256': payload_hash, 'x-amz-date': amzdate})
            headers = {k.lower(): str(v).strip() for k, v in headers.items()}
            signed = ';'.join(sorted(headers))
            canonical_headers = ''.join(k + ':' + headers[k] + '\n' for k in sorted(headers))
            canonical = '\n'.join([method, uri, query, canonical_headers, signed, payload_hash])
            scope = date + '/' + cfg.region + '/s3/aws4_request'
            to_sign = '\n'.join(['AWS4-HMAC-SHA256', amzdate, scope, hashlib.sha256(canonical.encode()).hexdigest()])
            signing_key = _sign(_sign(_sign(_sign(('AWS4' + cfg.secret_key).encode(), date), cfg.region), 's3'), 'aws4_request')
            signature = hmac.new(signing_key, to_sign.encode(), hashlib.sha256).hexdigest()
            headers['authorization'] = f'AWS4-HMAC-SHA256 Credential={cfg.access_key}/{scope}, SignedHeaders={signed}, Signature={signature}'
        request = urllib.request.Request('http://' + cfg.endpoint + uri + ('?policy' if bucket_policy else ''), data=body if method == 'PUT' else None, method=method, headers=headers)
        try:
            with self.opener(request, timeout=90 if method == 'PUT' else 20) as response:
                status = response.status
                response_headers = {k.lower(): v for k, v in response.headers.items()}
                data = response.read(self.MAX_BYTES + 1) if method == 'GET' else b''
                require(len(data) <= self.MAX_BYTES, 'MINIO_RESPONSE_TOO_LARGE')
                require(status in (200, 201, 204), 'MINIO_HTTP_REJECTED')
                return status, response_headers, data
        except urllib.error.HTTPError as error:
            if bucket_policy and error.code == 404:
                body = error.read(4096)
                if re.search(rb'<Code>\s*NoSuchBucketPolicy\s*</Code>', body):
                    return 404, {'no-bucket-policy':'true'}, b''
                raise SafetyError('MINIO_BUCKET_POLICY_UNCONFIRMED') from None
            if (method == 'HEAD' and error.code == 404) or (anonymous and error.code in (403, 404)) or (method == 'PUT' and error.code == 412):
                return error.code, {}, b''
            raise SafetyError('MINIO_HTTP_' + str(error.code)) from None
        except SafetyError:
            raise
        except Exception:
            raise SafetyError('MINIO_TRANSPORT_UNAVAILABLE') from None

    def verify(self, artifact, *, private=True):
        status, headers, _ = self._request('HEAD', artifact['objectKey'])
        require(status == 200, 'MINIO_OBJECT_MISSING')
        require(headers.get('content-type', '').split(';')[0] == 'application/pdf', 'MINIO_PDF_TYPE_MISMATCH')
        require(headers.get('content-length') == str(artifact['fileSize']), 'MINIO_SIZE_MISMATCH')
        require(headers.get('x-amz-meta-sha256') == artifact['sha256'], 'MINIO_METADATA_HASH_MISMATCH')
        _, _, data = self._request('GET', artifact['objectKey'])
        verify_object_bytes(data, artifact)
        if private:
            for method in ('HEAD','GET'):
                anonymous_status, _, _ = self._request(method, artifact['objectKey'], anonymous=True)
                require(anonymous_status == 403, 'MINIO_OBJECT_NOT_PRIVATE')
        return data

    def preflight_privacy(self, artifacts):
        # Authenticated policy read is required BEFORE any object PUT. Database flags are not proof.
        keys = frozenset(a['objectKey'] for a in artifacts)
        require(bool(keys), 'EMPTY_PRIVACY_SCOPE')
        status, headers, data = self._request('GET', '', bucket_policy=True)
        if status == 404:
            require(headers.get('no-bucket-policy') == 'true', 'MINIO_BUCKET_POLICY_UNCONFIRMED')
        else:
            try:
                policy = json.loads(data)
                statements = policy['Statement']
                require(isinstance(statements, list), 'MINIO_POLICY_SHAPE_REJECTED')
                def values(v):
                    if isinstance(v,str):return [v]
                    if isinstance(v,list):return v
                    if isinstance(v,dict):return [item for vv in v.values() for item in values(vv)]
                    raise SafetyError('MINIO_POLICY_SHAPE_REJECTED')
                for statement in statements:
                    if statement.get('Effect') != 'Allow':continue
                    principals = values(statement.get('Principal',[]))
                    require('NotPrincipal' not in statement, 'MINIO_COMPLEX_PUBLIC_POLICY_REJECTED')
                    if '*' not in principals:continue
                    require('NotAction' not in statement and 'NotResource' not in statement, 'MINIO_COMPLEX_PUBLIC_POLICY_REJECTED')
                    actions = values(statement.get('Action',[]))
                    resources = values(statement.get('Resource',[]))
                    require(bool(actions) and bool(resources), 'MINIO_POLICY_SHAPE_REJECTED')
                    # Any anonymous Allow on our reserved scope is unacceptable, including mutation/tagging/ACL rights.
                    object_scope = any(fnmatch.fnmatchcase('arn:aws:s3:::' + self.settings.bucket + '/' + key, resource) for key in keys for resource in resources)
                    bucket_scope = any(fnmatch.fnmatchcase('arn:aws:s3:::' + self.settings.bucket, resource) for resource in resources)
                    require(not object_scope and not bucket_scope, 'MINIO_ANONYMOUS_POLICY_EXPOSES_BATCH')
            except SafetyError:raise
            except Exception:raise SafetyError('MINIO_POLICY_INVALID') from None
        self._privacy_keys = keys

    def ensure_private_bucket(self):
        require(self.settings.endpoint == '159.75.14.226:9005' and self.settings.bucket == 'sop', 'CREATE_ONLY_AUTHORIZED_SOP_BUCKET')
        status, _, _ = self._request('HEAD', '', bucket_root=True)
        created = status == 404
        if created:
            # Empty-body S3 CreateBucket defaults to private; never add public ACL or policy.
            self._request('PUT', '', bucket_root=True)
            status, _, _ = self._request('HEAD', '', bucket_root=True)
        require(status == 200, 'SOP_BUCKET_CREATION_UNCONFIRMED')
        policy_status, policy_headers, _ = self._request('GET', '', bucket_policy=True)
        require(policy_status == 404 and policy_headers.get('no-bucket-policy') == 'true', 'SOP_BUCKET_REQUIRES_DEFAULT_PRIVATE_NO_POLICY')
        self.preflight_privacy([{'objectKey':'mes/sop/000000/reference-20261008/PRIVACY_PREFLIGHT/V1.1/' + '0' * 64 + '.pdf'}])
        return {'bucket':'sop','endpoint':self.settings.endpoint,'bucketCreated':created,'policyPrivate':True}

    def shared_bucket_policy_digest(self):
        status, headers, data = self._request('GET', '', bucket_policy=True, policy_bucket='ruoyi')
        require(status == 200 or status == 404 and headers.get('no-bucket-policy') == 'true', 'SHARED_POLICY_AUDIT_UNCONFIRMED')
        return hashlib.sha256(data if status == 200 else b'NoSuchBucketPolicy').hexdigest()

    def upload_immutable(self, artifact, data):
        require(artifact['objectKey'] in self._privacy_keys, 'MINIO_PRIVACY_PREFLIGHT_REQUIRED')
        verify_object_bytes(data, artifact)
        status, _, _ = self._request('HEAD', artifact['objectKey'])
        if status == 404:
            self._request('PUT', artifact['objectKey'], data,
                          {'content-type': 'application/pdf', 'if-none-match': '*', 'x-amz-meta-sha256': artifact['sha256'],
                           'x-amz-meta-sm3': artifact['sm3'], 'x-amz-meta-origin': 'NEXUS_SOP_REFERENCE_20261008'})
        self.verify(artifact)


def verify_object_bytes(data, artifact):
    require(isinstance(data, bytes) and data.startswith(b'%PDF-'), 'OBJECT_NOT_PDF')
    require(len(data) == artifact['fileSize'], 'OBJECT_SIZE_MISMATCH')
    require(hashlib.sha256(data).hexdigest() == artifact['sha256'], 'OBJECT_SHA256_MISMATCH')
    require(hashlib.new('sm3', data).hexdigest() == artifact['sm3'], 'OBJECT_SM3_MISMATCH')
