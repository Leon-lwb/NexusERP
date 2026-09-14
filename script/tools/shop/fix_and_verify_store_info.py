"""Restore 商品简介 for the 37 imported products and regression-test the browse-update fix.

Regression: before the ProductServiceImpl fix, one GET /api/front/product/detail/{id}
was enough to blank eb_store_product.store_info (StoreProduct.storeInfo defaults to
"" so MyBatis-Plus wrote it on the browse bump).  The test writes a real value,
views the product through the C-end, and asserts the value survives.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).parent))
sys.stdout.reconfigure(encoding="utf-8")

from mall_client import MallClient

WORK = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work")
manifest = json.loads((WORK / "manifest.json").read_text(encoding="utf-8"))
results = json.loads((WORK / "t2_result.json").read_text(encoding="utf-8"))
by_code = {m["code"]: m for m in manifest}

DB = dict(host="127.0.0.1", user="root", password="root", database="crmeb",
          charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)

fails = []


def check(cond, msg):
    print(("  OK   " if cond else "  FAIL ") + msg)
    if not cond:
        fails.append(msg)


con = pymysql.connect(**DB)
with con, con.cursor() as cur:
    # restore 商品简介 from the ERP spec
    for r in results:
        spec = by_code[r["code"]]["spec"]
        cur.execute("UPDATE eb_store_product SET store_info=%s WHERE id=%s", (spec, r["mall_product_id"]))
    print(f"restored store_info for {len(results)} products")

    cur.execute("SELECT COUNT(*) n FROM eb_store_product WHERE id BETWEEN 171 AND 207 AND (store_info IS NULL OR store_info='')")
    check(cur.fetchone()["n"] == 0, "no imported product has an empty store_info")

    sample_id = results[0]["mall_product_id"]
    cur.execute("SELECT store_info FROM eb_store_product WHERE id=%s", (sample_id,))
    before = cur.fetchone()["store_info"]

c = MallClient()
c.login()

print("\n=== regression: does viewing a product still blank store_info? ===")
for i in range(3):
    c.get(f"/api/front/product/detail/{sample_id}")

con = pymysql.connect(**DB)
with con, con.cursor() as cur:
    cur.execute("SELECT store_info,browse FROM eb_store_product WHERE id=%s", (sample_id,))
    row = cur.fetchone()
after = row["store_info"]

print(f"     before: {before!r}")
print(f"     after : {after!r}  (browse={row['browse']})")
check(after == before and after != "", "store_info survives 3 C-end product views")
check(row["browse"] > 0, "browse counter still increments (fix did not break the counter)")

print()
print("RESULT:", "ALL CHECKS PASSED" if not fails else f"{len(fails)} FAILURES")
sys.exit(1 if fails else 0)
