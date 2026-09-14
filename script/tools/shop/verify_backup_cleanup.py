"""Post-cleanup verification: no bak_ tables anywhere + report table counts."""
import sys

import pymysql

sys.stdout.reconfigure(encoding="utf-8")
c = pymysql.connect(host="127.0.0.1", user="root", password="root", charset="utf8mb4",
                    cursorclass=pymysql.cursors.DictCursor, autocommit=True)
with c, c.cursor() as cur:
    cur.execute("""SELECT table_schema AS db, COUNT(*) AS n
                   FROM information_schema.tables
                   WHERE table_name LIKE 'bak\\_%'
                   GROUP BY table_schema""")
    rows = cur.fetchall()
    print("remaining bak_* tables:", rows if rows else "NONE")

    print("\ntable counts for the ERP system databases:")
    for db in ("ry-vue", "ry-erp", "ry-mes", "ry-iot"):
        cur.execute("SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema=%s", (db,))
        print(f"  {db:<8} {cur.fetchone()['n']}")

    # protected 3D-demo data, one more time
    print("\n3D demo data (handover §6.1 hard metrics):")
    cur.execute("USE `ry-erp`")
    for t in ("wm_warehouse", "wm_storage_area", "wm_storage_location", "wm_material_stock"):
        cur.execute(f"SELECT COUNT(*) n FROM `{t}` WHERE warehouse_id = '9900000001'")
        print(f"  {t:<24} {cur.fetchone()['n']}")
