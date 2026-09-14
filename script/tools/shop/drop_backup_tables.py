"""Drop the rebuild's backup tables from ry-erp / ry-mes / ry-iot.

User-confirmed: drop all 202 `bak_*` tables across the three databases, no export.

Safety guards:
  * only tables matching `bak\\_%` (LIKE 'bak\\_%', underscore escaped so it does not
    also match e.g. 'bakery...')
  * refuses to run if the discovered set contains anything not starting with 'bak_'
  * records the 3D-demo warehouse metrics before/after and asserts they are unchanged
    (handover §6.1 calls them the only irreversible-risk data: 1 仓库 / 4 库区 /
     104 库位 / 55 库存, all under warehouse 9900000001)

Run: python drop_backup_tables.py [--dry-run]
"""
from __future__ import annotations

import sys

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

DBS = ["ry-erp", "ry-mes", "ry-iot"]
DEMO_WAREHOUSE = "9900000001"


def connect(db: str | None = None):
    return pymysql.connect(host="127.0.0.1", user="root", password="root", database=db,
                           charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)


def demo_metrics(cur) -> dict:
    """Rows in the 3D-demo warehouse: warehouse/zone/bin/stock, discovered dynamically."""
    out = {}
    cur.execute("""SELECT table_name AS t FROM information_schema.columns
                   WHERE table_schema='ry-erp' AND column_name='warehouse_id'
                   ORDER BY table_name""")
    for r in cur.fetchall():
        t = r["t"]
        if t.startswith("bak_"):
            continue
        try:
            cur.execute(f"SELECT COUNT(*) n FROM `{t}` WHERE warehouse_id = %s", (DEMO_WAREHOUSE,))
            n = cur.fetchone()["n"]
            if n:
                out[t] = n
        except Exception:
            pass
    return out


def main() -> None:
    dry = "--dry-run" in sys.argv
    con = connect()

    with con, con.cursor() as cur:
        targets: dict[str, list[str]] = {}
        for db in DBS:
            cur.execute("""SELECT table_name AS t FROM information_schema.tables
                           WHERE table_schema=%s AND table_name LIKE 'bak\\_%%'
                           ORDER BY table_name""", (db,))
            targets[db] = [r["t"] for r in cur.fetchall()]

        # guard: everything selected must genuinely be a backup table
        bad = [t for ts in targets.values() for t in ts if not t.startswith("bak_")]
        if bad:
            raise SystemExit(f"refusing to run: unexpected tables selected: {bad}")

        before_counts = {}
        for db in DBS:
            cur.execute("SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema=%s", (db,))
            before_counts[db] = cur.fetchone()["n"]

        cur.execute("USE `ry-erp`")
        demo_before = demo_metrics(cur)

        print("=== tables to drop ===")
        for db in DBS:
            print(f"  {db:<8} {len(targets[db]):>4} tables   (db currently has {before_counts[db]} tables)")
        total = sum(len(v) for v in targets.values())
        print(f"  TOTAL    {total:>4}")

        print("\n=== 3D demo metrics BEFORE (must not change) ===")
        for t, n in sorted(demo_before.items()):
            print(f"  {t:<34} {n}")

        if dry:
            print("\nDRY RUN - nothing dropped")
            return

        print("\n=== dropping ===")
        dropped, failed = 0, []
        for db in DBS:
            for t in targets[db]:
                try:
                    cur.execute(f"DROP TABLE `{db}`.`{t}`")
                    dropped += 1
                except Exception as e:
                    failed.append((db, t, str(e)[:80]))
            print(f"  {db}: {len(targets[db])} requested")

        print(f"\ndropped {dropped}/{total}, failures {len(failed)}")
        for f in failed[:10]:
            print("   FAIL", f)

        # ---- verify ----
        print("\n=== verification ===")
        left = 0
        for db in DBS:
            cur.execute("""SELECT COUNT(*) n FROM information_schema.tables
                           WHERE table_schema=%s AND table_name LIKE 'bak\\_%%'""", (db,))
            n = cur.fetchone()["n"]
            left += n
            cur.execute("SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema=%s", (db,))
            after = cur.fetchone()["n"]
            print(f"  {db:<8} bak_* left={n}   tables {before_counts[db]} -> {after} "
                  f"(expected {before_counts[db] - len(targets[db])})")

        cur.execute("USE `ry-erp`")
        demo_after = demo_metrics(cur)
        print("\n=== 3D demo metrics AFTER ===")
        for t, n in sorted(demo_after.items()):
            same = "OK" if demo_before.get(t) == n else "CHANGED!"
            print(f"  {t:<34} {n}  {same}")

        ok = left == 0 and not failed and demo_before == demo_after
        print("\n" + "=" * 56)
        print("RESULT:", "CLEAN" if ok else "PROBLEM - review above")
        sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
