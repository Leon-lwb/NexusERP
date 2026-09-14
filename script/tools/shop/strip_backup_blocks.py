"""Strip backup-table blocks out of the main init scripts.

Why: `script/sql/ry-erp.sql` / `ry-mes.sql` / `ry-iot.sql` / `ry-vue.sql` were
overwritten by a full Navicat dump that INCLUDED the bak_* tables, e.g. 52.7% of
ry-erp.sql is backup data.  A fresh import would therefore recreate all 203 backup
tables that we just dropped from the live databases.

Block layout (Navicat):
    -- ----------------------------
    -- Table structure for <table>
    -- ----------------------------
    DROP TABLE IF EXISTS ... ; CREATE TABLE ... ;
    -- ----------------------------
    -- Records of <table>
    -- ----------------------------
    INSERT ... ;
    (then the next table's "Table structure" header)

A table's block therefore spans from the `-- ----` line just above its
"Table structure" header up to the line just before the next block's `-- ----`.

Operates on BYTES (split on \\n, rejoin) so retained content is byte-identical.

Invariant verified after writing: the set of remaining "Table structure" names must
equal the live database's table set exactly.

Run: python strip_backup_blocks.py [--dry-run]
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

HEADER = re.compile(rb"^-- Table structure for (\S+)\r?$")
RULE = re.compile(rb"^-- -+\r?$")


def is_backup(name: str) -> bool:
    return name.startswith("bak_") or bool(re.search(r"_bak_\d{6,8}$", name))


def block_ranges(lines: list[bytes]) -> list[tuple[int, str]]:
    """Return (start_index, table_name) for every table-structure header."""
    out = []
    for i, ln in enumerate(lines):
        m = HEADER.match(ln)
        if m:
            start = i
            if i > 0 and RULE.match(lines[i - 1]):
                start = i - 1
            out.append((start, m.group(1).decode("utf-8", "replace")))
    return out


def main() -> None:
    dry = "--dry-run" in sys.argv
    con = pymysql.connect(host="127.0.0.1", user="root", password="root", charset="utf8mb4",
                          cursorclass=pymysql.cursors.DictCursor, autocommit=True)
    live: dict[str, set[str]] = {}
    with con, con.cursor() as cur:
        for _, db in PAIRS:
            cur.execute("SELECT table_name AS t FROM information_schema.tables WHERE table_schema=%s", (db,))
            live[db] = {r["t"] for r in cur.fetchall()}

    for fname, db in PAIRS:
        p = SQL_DIR / fname
        raw = p.read_bytes()
        lines = raw.split(b"\n")
        blocks = block_ranges(lines)

        # determine the line range of each block: [start, next_start)
        ranges = []
        for idx, (start, name) in enumerate(blocks):
            end = blocks[idx + 1][0] if idx + 1 < len(blocks) else len(lines)
            ranges.append((start, end, name))

        drop = [r for r in ranges if is_backup(r[2])]
        keep = [r for r in ranges if not is_backup(r[2])]

        removed_lines = sum(e - s for s, e, _ in drop)
        keep_mask_preview = [True] * len(lines)
        for s, e, _ in drop:
            for i in range(s, e):
                keep_mask_preview[i] = False
        new_size = len(b"\n".join(ln for i, ln in enumerate(lines) if keep_mask_preview[i]))
        drop_names = sorted(n for _, _, n in drop)
        keep_names = sorted(n for _, _, n in keep)

        print(f"=== {fname} ({db}) ===")
        print(f"   blocks: {len(ranges)} -> keep {len(keep)}, drop {len(drop)}")
        print(f"   lines : {len(lines)} -> {len(lines)-removed_lines} (remove {removed_lines}, "
              f"{100.0*removed_lines/len(lines):.1f}%)")
        print(f"   size  : {len(raw)/1024/1024:.2f} MB -> {new_size/1024/1024:.2f} MB")

        missing = sorted(live[db] - set(keep_names))
        extra = sorted(set(keep_names) - live[db])
        print(f"   after strip vs live db: live={len(live[db])} script={len(keep_names)} "
              f"missing={len(missing)} extra={len(extra)}")
        if missing or extra:
            print(f"     MISSING {missing[:8]}")
            print(f"     EXTRA   {extra[:8]}")
        if len(drop) != len(drop_names):
            print("     (note: duplicate names present)")

        if dry:
            print()
            continue

        # rebuild: drop the byte ranges, keep everything else byte-for-byte
        keep_mask = [True] * len(lines)
        for s, e, name in drop:
            for i in range(s, e):
                keep_mask[i] = False
        out = [ln for i, ln in enumerate(lines) if keep_mask[i]]
        p.write_bytes(b"\n".join(out))

        # ---- verify by re-parsing the written file ----
        re_lines = p.read_bytes().split(b"\n")
        re_names = sorted(n for _, n in block_ranges(re_lines))
        ok = (re_names == sorted(live[db])) and not any(is_backup(n) for n in re_names)
        print(f"   REWRITTEN -> {p.stat().st_size/1024/1024:.2f} MB, "
              f"{len(re_lines)} lines, tables={len(re_names)}  {'OK' if ok else 'MISMATCH!'}")
        print()


if __name__ == "__main__":
    main()
