"""Prove (or disprove) the MinIO credentials with a raw AWS SigV4 PUT.

Isolates "server rejects our credentials" from "the Java upload path is broken",
which is otherwise indistinguishable behind the mall's catch-all error message.

SigV4 implemented with the stdlib only -- the venv has no pip and no boto3.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import hmac
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

ENDPOINT = "159.75.14.226:9005"
ACCESS_KEY = "minio"
SECRET_KEY = "minio123"
BUCKET = "shop"
REGION = "us-east-1"
KEY = "nexusimage/_t2_selftest.txt"
BODY = b"nexus-erp T2 oss selftest\n"

host = ENDPOINT
now = dt.datetime.now(dt.timezone.utc)
amzdate = now.strftime("%Y%m%dT%H%M%SZ")
datestamp = now.strftime("%Y%m%d")
payload_hash = hashlib.sha256(BODY).hexdigest()

canonical_uri = f"/{BUCKET}/{KEY}"
canonical_headers = f"host:{host}\nx-amz-content-sha256:{payload_hash}\nx-amz-date:{amzdate}\n"
signed_headers = "host;x-amz-content-sha256;x-amz-date"
canonical_request = "\n".join(["PUT", canonical_uri, "", canonical_headers, signed_headers, payload_hash])

scope = f"{datestamp}/{REGION}/s3/aws4_request"
string_to_sign = "\n".join(["AWS4-HMAC-SHA256", amzdate, scope,
                            hashlib.sha256(canonical_request.encode()).hexdigest()])


def sign(k: bytes, msg: str) -> bytes:
    return hmac.new(k, msg.encode(), hashlib.sha256).digest()


signing_key = sign(sign(sign(sign(("AWS4" + SECRET_KEY).encode(), datestamp), REGION), "s3"), "aws4_request")
signature = hmac.new(signing_key, string_to_sign.encode(), hashlib.sha256).hexdigest()

auth = (f"AWS4-HMAC-SHA256 Credential={ACCESS_KEY}/{scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}")

url = f"http://{ENDPOINT}{canonical_uri}"
req = urllib.request.Request(url, data=BODY, method="PUT", headers={
    "Host": host,
    "x-amz-content-sha256": payload_hash,
    "x-amz-date": amzdate,
    "Authorization": auth,
    "Content-Type": "text/plain",
})

print(f"PUT {url}")
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        print("  -> HTTP", r.status, dict(r.headers).get("ETag"))
except urllib.error.HTTPError as e:
    print("  -> HTTP", e.code, e.read()[:500])

# read it back (anonymous GET, as the browser/C-end would)
try:
    with urllib.request.urlopen(f"http://{ENDPOINT}/{BUCKET}/{KEY}", timeout=30) as r:
        data = r.read()
        print("anon GET ->", r.status, len(data), "bytes", "MATCH" if data == BODY else "MISMATCH")
except urllib.error.HTTPError as e:
    print("anon GET -> HTTP", e.code, e.read()[:300])
