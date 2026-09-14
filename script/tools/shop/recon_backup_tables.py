"""Enumerate every backup table across the local MySQL instance, with sizes.

Also reports whether the logical dump that makes them recoverable still exists,
so we know dropping them is not a one-way door.
"""
from __future__ import annotations

import sys
from pathlib import Path

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

DBS = ["ry-vue", "ry-erp", "ry-mes", "ry-iot", "crmeb"]
DUMP = Path(r"D:\workspace\nexus-erp\backup-20260914\mysqldump_all_4db.sql")

con = pymysql.connect(host="127.0.0.1", user="root", password="root", charset="utf8mb4",
                      cursorclass=pymysql.cursors.DictCursor, autocommit=True)

grand_tables = 0
grand_mb = 0.0
per_db: dict[str, list[dict]] = {}

with con, con.cursor() as cur:
    for db in DBS:
        cur.execute("""SELECT table_name AS name, table_rows AS rows,
                              ROUND((data_length+index_length)/1024/1024, 2) AS mb
                       FROM information_schema.tables
                       WHERE table_schema=%s AND table_name LIKE 'bak%%'
                       ORDER BY table_name""", (db,))
        rows = cur.fetchall()
        if rows:
            per_db[db] = rows
            grand_tables += len(rows)
            grand_mb += sum(float(r["mb"] or 0) for r in rows)

for db, rows in per_db.items():
    mb = sum(float(r["mb"] or 0) for r in rows)
    print(f"=== {db}: {len(rows)} backup tables, {mb:.1f} MB ===")
    # group by prefix for a compact view
    groups: dict[str, list[dict]] = {}
    for r in rows:
        key = r["name"].rsplit("_", 1)[0] if r["name"].startswith("bak_") else r["name"]
        # bak_20260914_xxx -> bak_20260914 ; bak_p5_xxx -> bak_p5
        parts = r["name"].split("_")
        key = "_".join(parts[:2]) if len(parts) >= 2 else r["name"]
        groups.setdefault(key, []).append(r)
    for key in sorted(groups):
        g = groups[key]
        print(f'   {key+"_*":<24} {len(g):>4} tables  {sum(float(x["mb"] or 0) for x in g):>7.1f} MB')
    print()

print(f"TOTAL: {grand_tables} backup tables, {grand_mb:.1f} MB across {len(per_db)} database(s)")

print("\n=== rollback source ===")
if DUMP.exists():
    print(f"  {DUMP}  {DUMP.stat().st_size/1024/1024:.2f} MB  (mtime {DUMP.stat().st_mtime})")
else:
    print(f"  !! MISSING: {DUMP}")
