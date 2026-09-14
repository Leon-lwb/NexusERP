"""Measure how much of the main init scripts is backup-table bloat."""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

SQL_DIR = Path(r"D:\workspace\nexus-erp\script\sql")
FILES = ["ry-vue.sql", "ry-erp.sql", "ry-mes.sql", "ry-iot.sql", "app.sql"]

# a "-- Table structure for X" block, up to the next such header (or EOF)
HEADER = re.compile(r"^--\s*Table structure for (\S+)\s*$", re.M)

for name in FILES:
    p = SQL_DIR / name
    if not p.exists():
        continue
    text = p.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()

    marks = [(m.start(), m.group(1), text[:m.start()].count("\n")) for m in HEADER.finditer(text)]
    bak_tables, bak_lines = [], 0
    for i, (pos, table, lineno) in enumerate(marks):
        if not table.startswith("bak_"):
            continue
        end = marks[i + 1][2] if i + 1 < len(marks) else len(lines)
        bak_tables.append(table)
        bak_lines += end - lineno

    prefix: dict[str, int] = {}
    for t in bak_tables:
        key = "_".join(t.split("_")[:2])
        prefix[key] = prefix.get(key, 0) + 1

    mb = p.stat().st_size / 1024 / 1024
    pct = 100.0 * bak_lines / max(1, len(lines))
    print(f"{name:<14} {mb:>6.2f} MB  {len(lines):>6} lines   "
          f"bak tables={len(bak_tables):>4}  bak lines={bak_lines:>6} ({pct:>4.1f}%)")
    for k, n in sorted(prefix.items()):
        print(f"      {k}_*  {n}")
