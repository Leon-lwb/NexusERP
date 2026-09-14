"""Verify the category rework end-to-end against the live C-end API.

Checks, per the manifest:
  1. top-level order matches the intended display order
  2. every name equals the shortened name
  3. every `extra` is rewritten to an absolute CDN url (NexusFrontImagePathFilter)
  4. every icon URL is actually downloadable
  5. child order within each parent matches the intended order
Exit code 0 = all green.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

WORK = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work")
manifest = json.loads((WORK / "category_manifest.json").read_text(encoding="utf-8"))
tops = [m for m in manifest if m["level"] == "top"]
leaves = [m for m in manifest if m["level"] == "leaf"]

fails: list[str] = []


def check(cond, msg):
    print(("  OK   " if cond else "  FAIL ") + msg)
    if not cond:
        fails.append(msg)


with urllib.request.urlopen("http://localhost:20400/api/front/category", timeout=30) as r:
    data = json.loads(r.read().decode("utf-8"))["data"]

print("=== 1. top-level order ===")
got = [t["name"] for t in data]
want = [m["new_name"] for m in tops]
check(got == want, f"order == intended: {got}")
for i, (n, s) in enumerate(zip(got, [t["sort"] for t in data]), 1):
    print(f"  {i}. sort={s:<2} {n}")

print("\n=== 2/3. names + extra (icon url) ===")
bad_name, bad_extra = [], []
for t in data:
    if t["name"] in ("", None):
        bad_name.append(t["id"])
    if not str(t.get("extra", "")).startswith("http"):
        bad_extra.append((t["id"], t["name"], t.get("extra")))
    for c in t.get("child") or []:
        if not str(c.get("extra", "")).startswith("http"):
            bad_extra.append((c["id"], c["name"], c.get("extra")))
check(not bad_name, "all category names non-empty")
check(not bad_extra, f"all category icons rewritten to absolute url ({len(bad_extra)} bad)")
for b in bad_extra[:5]:
    print("     bad:", b)
print(f"  sample: {data[0]['name']} -> {data[0]['extra']}")
print(f"          {data[0]['child'][0]['name']} -> {data[0]['child'][0]['extra']}")

print("\n=== 4. child order within each parent ===")
order_bad = []
for t in data:
    want_kids = [m["new_name"] for m in leaves if m["pid"] == t["id"]]
    got_kids = [c["name"] for c in (t.get("child") or [])]
    if got_kids != want_kids:
        order_bad.append((t["name"], want_kids, got_kids))
check(not order_bad, f"child order matches intended ({len(order_bad)} mismatches)")
for b in order_bad[:3]:
    print("     mismatch:", b)

print("\n=== 5. icons downloadable ===")
urls = []
for t in data:
    if str(t.get("extra", "")).startswith("http"):
        urls.append((t["name"], t["extra"]))
    for c in t.get("child") or []:
        if str(c.get("extra", "")).startswith("http"):
            urls.append((c["name"], c["extra"]))
bad_icon = []
for name, url in urls:
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as resp:
                n = len(resp.read())
                if resp.status == 200 and n > 500:
                    break
                if attempt == 2:
                    bad_icon.append((name, resp.status, n))
        except Exception as e:
            if attempt == 2:
                bad_icon.append((name, str(e)[:50], 0))
check(len(urls) == 46, f"46 icon urls present (got {len(urls)})")
check(not bad_icon, f"all icons downloadable ({len(bad_icon)} bad)")
for b in bad_icon[:5]:
    print("     bad icon:", b)

print("\n" + "=" * 56)
print("RESULT:", "ALL CHECKS PASSED" if not fails else f"{len(fails)} FAILURES")
sys.exit(1 if fails else 0)
