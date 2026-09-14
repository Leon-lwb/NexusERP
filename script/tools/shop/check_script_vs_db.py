"""Confirm the init scripts still define the real business tables (bloated, not overwritten).

Compares the non-bak table blocks in each script against the live database, so we
know stripping the bak_ blocks is safe.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

SQL_DIR = Path(r"D:\workspace\nexus-erp\script\sql")
PAIRS = [("ry-vue.sql", "ry-vue"), ("ry-erp.sql", "ry-erp"),
         ("ry-mes.sql", "ry-mes"), ("ry-iot.sql", "ry-iot")]
HEADER = re.compile(r"^--\s*Table structure for (\S+)\s*$", re.M)

con = pymysql.connect(host="127.0.0.1", user="root", password="root", charset="utf8mb4",
                      cursorclass=pymysql.cursors.DictCursor, autocommit=True)

with con, con.cursor() as cur:
    for fname, db in PAIRS:
        p = SQL_DIR / fname
        text = p.read_text(encoding="utf-8", errors="replace")
        tables = [m.group(1) for m in HEADER.finditer(text)]
        bak = [t for t in tables if t.startswith("bak_")]
        real = [t for t in tables if not t.startswith("bak_")]

        cur.execute("SELECT table_name AS t FROM information_schema.tables WHERE table_schema=%s", (db,))
        live = {r["t"] for r in cur.fetchall()}

        missing_in_script = sorted(live - set(real))
        extra_in_script = sorted(set(real) - live)

        print(f"{fname}  ({db})")
        print(f"   table blocks: {len(tables)}  = {len(real)} real + {len(bak)} bak")
        print(f"   live db tables: {len(live)}")
        print(f"   live but NOT in script: {len(missing_in_script)} {missing_in_script[:6]}")
        print(f"   in script but NOT live: {len(extra_in_script)} {extra_in_script[:6]}")
        print()
