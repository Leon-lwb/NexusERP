# -*- coding: utf-8 -*-
"""校验生成的 rebuild.sql：逐条 INSERT 检查列数与值数是否一致。

用法: python validate_sql.py <sql文件>
"""
import re
import sys
from pathlib import Path


def split_top(s: str) -> list[str]:
    """按顶层逗号切分（忽略括号内与字符串内的逗号）。"""
    out, cur, depth, in_str, i = [], "", 0, False, 0
    while i < len(s):
        ch = s[i]
        if in_str:
            if ch == "'" and i + 1 < len(s) and s[i + 1] == "'":   # 转义单引号
                cur += "''"
                i += 2
                continue
            if ch == "'":
                in_str = False
            cur += ch
        else:
            if ch == "'":
                in_str = True
                cur += ch
            elif ch == "(":
                depth += 1
                cur += ch
            elif ch == ")":
                depth -= 1
                cur += ch
            elif ch == "," and depth == 0:
                out.append(cur.strip())
                cur = ""
            else:
                cur += ch
        i += 1
    if cur.strip():
        out.append(cur.strip())
    return out


def extract_groups(body: str) -> list[str]:
    """从一个 VALUES 子句中提取各 (...) 值组的内容。"""
    groups, cur, depth, in_str, i = [], "", 0, False, 0
    while i < len(body):
        ch = body[i]
        if in_str:
            if ch == "'" and i + 1 < len(body) and body[i + 1] == "'":
                cur += "''"
                i += 2
                continue
            if ch == "'":
                in_str = False
            cur += ch
        else:
            if ch == "'":
                in_str = True
                cur += ch
            elif ch == "(":
                depth += 1
                if depth == 1:
                    cur = ""
                else:
                    cur += ch
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    groups.append(cur)
                else:
                    cur += ch
            elif depth >= 1:
                cur += ch
        i += 1
    return groups


def main(path: str) -> int:
    txt = Path(path).read_text(encoding="utf-8")
    pat = re.compile(r"INSERT INTO\s+`([^`]+)`\.([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*VALUES\s*",
                     re.IGNORECASE)
    print(f"[dbg] len(txt)={len(txt)} count('INSERT INTO')={txt.count('INSERT INTO')} "
          f"regex_hits={len(pat.findall(txt))}")
    total = bad = 0
    for m in pat.finditer(txt):
        db, tbl, cols_raw = m.group(1), m.group(2), m.group(3)
        cols = [c.strip() for c in cols_raw.split(",") if c.strip()]
        body = txt[m.end():txt.find(";", m.end())]
        for gi, g in enumerate(extract_groups(body), start=1):
            vals = split_top(g)
            if len(vals) != len(cols):
                bad += 1
                print(f"MISMATCH {db}.{tbl}  row#{gi}: cols={len(cols)} vals={len(vals)}")
                print(f"   columns({len(cols)}): {cols}")
                print(f"   values ({len(vals)}): {vals}")
                break
        total += 1
    print(f"\n检查 INSERT {total} 条，不匹配 {bad} 条")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1]))
