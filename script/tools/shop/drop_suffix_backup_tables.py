"""Drop the backup tables that the prefix-only pass missed (suffix naming convention).

The first cleanup matched only `bak_%`.  A broader scan found three more backups in
ry-vue using the `<table>_bak_<date>` convention:

    ai_provider_bak_20260820
    sys_menu_bak_20260629
    sys_role_menu_bak_20260629

Deliberately NOT touched (checked, they are not backups):
    ry-vue.wf_copy / ruoyi-nbcio-plus.wf_copy   -> Flowable workflow tables (wf_ prefix)
    jsc.ads_cyqs_nyhj_v2_nysc_nyjygl_bak        -> belongs to the unrelated `jsc` schema

Uses an explicit allowlist so a pattern change can never widen the blast radius.
"""
from __future__ import annotations

import sys

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

ALLOWLIST = [
    ("ry-vue", "ai_provider_bak_20260820"),
    ("ry-vue", "sys_menu_bak_20260629"),
    ("ry-vue", "sys_role_menu_bak_20260629"),
]

con = pymysql.connect(host="127.0.0.1", user="root", password="root", charset="utf8mb4",
                      cursorclass=pymysql.cursors.DictCursor, autocommit=True)

with con, con.cursor() as cur:
    print("=== to drop ===")
    for db, t in ALLOWLIST:
        cur.execute("""SELECT table_rows AS approx_rows,
                              ROUND((data_length+index_length)/1024,1) AS kb
                       FROM information_schema.tables
                       WHERE table_schema=%s AND table_name=%s""", (db, t))
        info = cur.fetchone()
        if not info:
            print(f"  {db}.{t}  -- not present, skip")
            continue
        cur.execute(f"SELECT COUNT(*) n FROM `{db}`.`{t}`")
        exact = cur.fetchone()["n"]
        print(f"  {db}.{t:<32} rows={exact:<6} {info['kb']} KB")
        cur.execute(f"DROP TABLE `{db}`.`{t}`")
        print(f"     dropped")

    print("\n=== verification ===")
    cur.execute("""SELECT table_schema AS db, table_name AS t
                   FROM information_schema.tables
                   WHERE table_name LIKE '%bak%' OR table_name LIKE '%backup%'""")
    left = cur.fetchall()
    print("  remaining tables containing 'bak'/'backup':")
    for r in left:
        print(f"    {r['db']}.{r['t']}")
    if not left:
        print("    NONE")

    for db in ("ry-vue", "ry-erp", "ry-mes", "ry-iot"):
        cur.execute("SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema=%s", (db,))
        print(f"  {db:<8} tables: {cur.fetchone()['n']}")
