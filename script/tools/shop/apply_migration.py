"""Apply a crmeb_upgrade SQL script and report affected rows per statement.

Strips `--` comment lines, splits on ';', executes in order.  Reports rows
affected so the idempotency guard (old value in WHERE) is observable.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

path = Path(sys.argv[1])
raw = path.read_text(encoding="utf-8")

# drop comment lines, then split
lines = [ln for ln in raw.splitlines() if not ln.strip().startswith("--")]
statements = [s.strip() for s in "\n".join(lines).split(";") if s.strip()]

con = pymysql.connect(host="127.0.0.1", user="root", password="root", database="crmeb",
                      charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)
print(f"applying {path.name}: {len(statements)} statement(s)")
total = 0
with con, con.cursor() as cur:
    for i, st in enumerate(statements, 1):
        cur.execute(st)
        total += cur.rowcount
        head = re.sub(r"\s+", " ", st)[:70]
        print(f"  [{i}] rows={cur.rowcount:<3} {head}...")

    print(f"\ntotal rows affected: {total}")
    print("\n=== resulting values ===")
    cur.execute("""SELECT id, name, value FROM eb_system_config
                   WHERE name IN ('ali_pay_notifu_url','ali_pay_return_url','ali_pay_quit_url',
                                  'ali_pay_recharge_return_url','ali_pay_recharge_quit_url')
                   ORDER BY name""")
    for r in cur.fetchall():
        print(f'  {r["id"]:>6}  {r["name"]:<30} {r["value"]}')
