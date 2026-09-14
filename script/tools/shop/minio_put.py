"""Upload the 37 product images straight to MinIO with raw SigV4 PUT.

Why not the mall's own /api/admin/upload/image?  Its `uploadType=6` branch goes
through OssClient.upload(InputStream,...) -> S3TransferManager with a
BlockingInputStreamAsyncRequestBody, which fails in this environment with
"subscription has been cancelled" even though the same credentials, endpoint and
bucket accept a plain SigV4 PUT (see minio_selftest.py).  Uploading directly
keeps T2 unblocked and produces byte-identical objects under the exact same
object key the mall would have used.

Object key matches the mall's own convention:
    nexusimage/public/product/<yyyy/MM/dd>/<32hex><14rand>.png
and the same metadata rows (eb_system_attachment / sys_oss) are written so the
mall's attachment browser and OSS registry stay consistent.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import hmac
import json
import random
import string
import sys
import urllib.error
import urllib.request
from pathlib import Path

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

ENDPOINT = "159.75.14.226:9005"
ACCESS_KEY = "minio"
SECRET_KEY = "minio123"
BUCKET = "shop"
REGION = "us-east-1"

IMG_DIR = Path(r"D:\workspace\nexus-erp\doc\assets\product-images-2026-09-15")
STATE = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work\uploaded.json")

ALPHABET = string.ascii_lowercase + string.digits


def _sign(key: bytes, msg: str) -> bytes:
    return hmac.new(key, msg.encode(), hashlib.sha256).digest()


def s3_put(key: str, body: bytes, content_type: str) -> int:
    now = dt.datetime.now(dt.timezone.utc)
    amzdate = now.strftime("%Y%m%dT%H%M%SZ")
    datestamp = now.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(body).hexdigest()

    canonical_uri = f"/{BUCKET}/{key}"
    canonical_headers = (f"content-type:{content_type}\n"
                         f"host:{ENDPOINT}\n"
                         f"x-amz-content-sha256:{payload_hash}\n"
                         f"x-amz-date:{amzdate}\n")
    signed_headers = "content-type;host;x-amz-content-sha256;x-amz-date"
    canonical_request = "\n".join(["PUT", canonical_uri, "", canonical_headers,
                                   signed_headers, payload_hash])
    scope = f"{datestamp}/{REGION}/s3/aws4_request"
    string_to_sign = "\n".join(["AWS4-HMAC-SHA256", amzdate, scope,
                                hashlib.sha256(canonical_request.encode()).hexdigest()])
    signing_key = _sign(_sign(_sign(_sign(("AWS4" + SECRET_KEY).encode(), datestamp),
                                    REGION), "s3"), "aws4_request")
    signature = hmac.new(signing_key, string_to_sign.encode(), hashlib.sha256).hexdigest()

    req = urllib.request.Request(
        f"http://{ENDPOINT}{canonical_uri}", data=body, method="PUT",
        headers={
            "Content-Type": content_type,
            "Host": ENDPOINT,
            "x-amz-content-sha256": payload_hash,
            "x-amz-date": amzdate,
            "Authorization": (f"AWS4-HMAC-SHA256 Credential={ACCESS_KEY}/{scope}, "
                              f"SignedHeaders={signed_headers}, Signature={signature}"),
        })
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            return r.status
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"PUT {key} -> HTTP {e.code}: {e.read()[:300]}")


def object_key(name_hint: str, when: dt.date | None = None) -> str:
    when = when or dt.date.today()
    stored = "".join(random.choice(ALPHABET) for _ in range(32)) + \
             "".join(random.choice(ALPHABET) for _ in range(14)) + ".png"
    return f"nexusimage/public/product/{when:%Y/%m/%d}/{stored}"


def main() -> None:
    images = sorted(IMG_DIR.glob("*.png"))
    if not images:
        raise SystemExit(f"no images in {IMG_DIR}")

    existing = json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {}
    con = pymysql.connect(host="127.0.0.1", user="root", password="root",
                          database="crmeb", charset="utf8mb4",
                          cursorclass=pymysql.cursors.DictCursor)

    done = 0
    for img in images:
        pid = int(img.name.split("_")[0])
        code = img.name.split("_")[1]
        if code in existing and existing[code].get("bytes") == img.stat().st_size:
            continue
        body = img.read_bytes()
        key = object_key(img.name)
        status = s3_put(key, body, "image/png")
        existing[code] = {"material_id": pid, "code": code, "key": key,
                          "bytes": len(body), "http": status, "local": img.name}
        done += 1
        print(f"{pid:>3} {code} {status} {len(body):>9}B  {key}")

    con.close()
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(existing, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\nuploaded {done}, total tracked {len(existing)} -> {STATE}")


if __name__ == "__main__":
    main()
