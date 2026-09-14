"""Apply the category rework: upload icons to MinIO and update eb_category.

Stores the RELATIVE object key in `eb_category.extra`, matching the platform
convention.  NexusFrontImagePathFilterConfig rewrites `nexusimage/...` ->
`<cdn>/nexusimage/...` on every /api/front/* response and is idempotent (it only
matches when the JSON value *starts* with nexusimage/), so absolute URLs would
also survive -- but relative keeps us consistent with the existing 8 categories
and with what the admin's NexusUpload writes.

Updates per category: name (shortened), sort (DESC convention), extra (icon).
Run: python apply_category_rework.py [--dry-run]
"""
from __future__ import annotations

import datetime as dt
import json
import random
import string
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).parent))
sys.stdout.reconfigure(encoding="utf-8")

from minio_put import ALPHABET, BUCKET, s3_put   # reuse the proven SigV4 PUT

WORK = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work")
MANIFEST = WORK / "category_manifest.json"
ICONS = Path(r"D:\workspace\nexus-erp\doc\assets\category-icons-2026-09-14")
RESULT = WORK / "category_result.json"

STORED = "category"          # object path segment, mirrors admin upload model="category"


def object_key() -> str:
    d = dt.date.today()
    stored = "".join(random.choice(ALPHABET) for _ in range(32)) + \
             "".join(random.choice(ALPHABET) for _ in range(14)) + ".png"
    return f"nexusimage/public/{STORED}/{d:%Y/%m/%d}/{stored}"


def main() -> None:
    dry = "--dry-run" in sys.argv
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    con = pymysql.connect(host="127.0.0.1", user="root", password="root", database="crmeb",
                          charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)
    results = []
    with con, con.cursor() as cur:
        for m in manifest:
            src = ICONS / f'{m["id"]}_{m["new_name"]}.png'
            if not src.exists():
                raise SystemExit(f"missing icon: {src}")

            body = src.read_bytes()
            if dry:
                key = "nexusimage/public/category/<dry-run>.png"
                status = "-"
            else:
                key = object_key()
                status = s3_put(key, body, "image/png")

            if not dry:
                cur.execute("UPDATE eb_category SET name=%s, sort=%s, extra=%s WHERE id=%s",
                            (m["new_name"], m["sort"], key, m["id"]))
                if cur.rowcount != 1:
                    raise SystemExit(f'update affected {cur.rowcount} rows for id={m["id"]}')

            results.append({**m, "extra": key, "bytes": len(body), "http": status})
            print(f'{m["id"]} sort={m["sort"]:<2} {m["old_name"]} -> {m["new_name"]:<6} {status} {key}')

    if not dry:
        RESULT.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"\nupdated {len(results)} categories -> {RESULT}")
    else:
        print(f"\nDRY RUN: {len(results)} categories would be updated")


if __name__ == "__main__":
    main()
