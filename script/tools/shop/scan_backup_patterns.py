"""Scan every schema for backup-looking tables using several naming conventions.

The earlier recon only matched `bak_%` (prefix), which missed tables named
`<table>_bak_<date>` (suffix).  Broaden the net so the cleanup is actually complete.
"""
from __future__ import annotations

import sys

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

PATTERNS = [
    ("prefix bak_", "table_name LIKE 'bak\\_%'"),
    ("suffix _bak_<date>", "table_name LIKE '%\\_bak\\_%'"),
    ("suffix _bak", "table_name LIKE '%\\_bak'"),
    ("contains _bak", "table_name LIKE '%\\_bak%'"),
    ("contains bak (any)", "table_name LIKE '%bak%'"),
    ("contains backup", "table_name LIKE '%backup%'"),
    ("suffix _old", "table_name LIKE '%\\_old'"),
    ("suffix _copy", "table_name LIKE '%\\_copy%'"),
    ("suffix _yyyymmdd", "table_name REGEXP '_[0-9]{8}$'"),
]

con = pymysql.connect(host="127.0.0.1", user="root", password="root", charset="utf8mb4",
                      cursorclass=pymysql.cursors.DictCursor, autocommit=True)

found: dict[str, set[str]] = {}
with con, con.cursor() as cur:
    cur.execute("""SELECT table_schema AS db, table_name AS t
                   FROM information_schema.tables
                   WHERE table_schema NOT IN
                         ('information_schema','performance_schema','mysql','sys')""")
    allrows = cur.fetchall()

for label, _ in PATTERNS:
    found[label] = set()

import re
regexes = {
    "prefix bak_": re.compile(r"^bak_"),
    "suffix _bak_<date>": re.compile(r"_bak_\d{6,8}$"),
    "suffix _bak": re.compile(r"_bak$"),
    "contains _bak": re.compile(r"_bak"),
    "contains bak (any)": re.compile(r"bak", re.I),
    "contains backup": re.compile(r"backup", re.I),
    "suffix _old": re.compile(r"_old$"),
    "suffix _copy": re.compile(r"_copy"),
    "suffix _yyyymmdd": re.compile(r"_\d{8}$"),
}

for r in allrows:
    key = f'{r["db"]}.{r["t"]}'
    for label, rx in regexes.items():
        if rx.search(r["t"]):
            found[label].add(key)

for label, _ in PATTERNS:
    s = found[label]
    if s:
        print(f"=== {label}: {len(s)} ===")
        for k in sorted(s):
            print("   ", k)
        print()

# the strongest signal: prefix bak_ or _bak<date>
strong = found["prefix bak_"] | found["suffix _bak_<date>"] | found["suffix _bak"]
print(f"STRONG backup-table signals (prefix bak_ / suffix _bak[_date]): {len(strong)}")
for k in sorted(strong):
    print("   ", k)
