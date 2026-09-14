"""Determine why the C-end category order comes out reversed.

Runs the exact predicate the backend uses (type=1 AND status=1, NO order by),
shows the index set, and compares with sort ASC.
"""
import sys

import pymysql

sys.stdout.reconfigure(encoding="utf-8")
con = pymysql.connect(host="127.0.0.1", user="root", password="root", database="crmeb",
                      charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)
with con, con.cursor() as cur:
    cur.execute("SHOW INDEX FROM eb_category")
    print("=== indexes ===")
    for r in cur.fetchall():
        print(f'  {r["Key_name"]:<12} seq={r["Seq_in_index"]} col={r["Column_name"]:<10} card={r["Cardinality"]}')

    print("\n=== backend predicate, NO order by (what the API actually returns) ===")
    cur.execute("SELECT id, sort, name FROM eb_category WHERE type=1 AND status=1")
    for r in cur.fetchall():
        print(f'  id={r["id"]} sort={r["sort"]:<3} {r["name"]}')

    print("\n=== same rows ORDER BY sort ASC (intended) ===")
    cur.execute("SELECT id, sort, name FROM eb_category WHERE type=1 AND status=1 ORDER BY sort ASC")
    for r in cur.fetchall():
        print(f'  id={r["id"]} sort={r["sort"]:<3} {r["name"]}')

    print("\n=== EXPLAIN of the backend predicate ===")
    cur.execute("EXPLAIN SELECT id, sort, name FROM eb_category WHERE type=1 AND status=1")
    for r in cur.fetchall():
        print("  ", r)
