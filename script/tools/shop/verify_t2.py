"""T2 regression: verify the published catalog end-to-end.

Checks (admin API + C-end API + DB + actual image fetch):
  1. category tree: 9 Nexus top-level categories with their 品类 children
  2. admin product list: 37 Nexus products on the shelf, 7 legacy ones off
  3. DB: price/cost/category/stock/unit for every published product
  4. C-end detail for a sample: /api/front/product/detail/{id}
  5. C-end category feed: /api/front/category
  6. every product image actually downloadable from MinIO
Exit code 0 = all green.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).parent))
sys.stdout.reconfigure(encoding="utf-8")

from mall_client import MallClient

WORK = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work")
RESULT = WORK / "t2_result.json"
MINIO = "http://159.75.14.226:9005/shop/"

fails: list[str] = []


def check(cond: bool, msg: str) -> None:
    print(("  OK   " if cond else "  FAIL ") + msg)
    if not cond:
        fails.append(msg)


def main() -> None:
    results = json.loads(RESULT.read_text(encoding="utf-8"))
    c = MallClient()
    c.login()

    # ---- 1. category tree ------------------------------------------------------
    print("=== 1. category tree ===")
    tree = c.get("/api/admin/category/list/tree", type=1, status=-1)
    nexus_top = [t for t in tree if t["name"] in {r["top"] for r in results}]
    check(len(nexus_top) == 9, f"9 Nexus top-level categories present (got {len(nexus_top)})")
    n_children = sum(len(t.get("child") or []) for t in nexus_top)
    check(n_children == 37, f"37 品类 children present (got {n_children})")
    for t in nexus_top:
        print(f'     {t["name"]:<12} id={t["id"]:<5} children={len(t.get("child") or [])}')

    # ---- 2. admin product list -------------------------------------------------
    print("\n=== 2. admin product list ===")
    nexus_ids = {r["mall_product_id"] for r in results}
    legacy_ids = {100, 101, 102, 159, 167, 168, 170}
    con = pymysql.connect(host="127.0.0.1", user="root", password="root", database="crmeb",
                          charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)
    cur = con.cursor()
    cur.execute("SELECT id,is_show,is_del,price,stock FROM eb_store_product")
    rows = {r["id"]: r for r in cur.fetchall()}

    on = [i for i in nexus_ids if rows.get(i, {}).get("is_show") == 1 and rows[i]["is_del"] == 0]
    check(len(on) == 37, f"37 Nexus products are on the shelf (got {len(on)})")
    off = [i for i in legacy_ids if rows.get(i, {}).get("is_show") == 0]
    check(len(off) == 7, f"7 legacy e-commerce products are off the shelf (got {len(off)})")

    # ---- 3. DB field integrity -------------------------------------------------
    print("\n=== 3. published product fields ===")
    bad = []
    for r in results:
        cur.execute("""SELECT p.id,p.store_name,p.price,p.ot_price,p.cost,p.stock,p.unit_name,
                              p.cate_id,p.temp_id,p.spec_type,p.image,p.bar_code,
                              (SELECT COUNT(*) FROM eb_store_product_attr_value v
                                WHERE v.product_id=p.id AND v.is_del=0) AS sku_cnt
                       FROM eb_store_product p WHERE p.id=%s""", (r["mall_product_id"],))
        p = cur.fetchone()
        want_price = round(r["price"], 2)
        want_ot = round(r["price"] * 1.2, 2)
        if (float(p["price"]) != want_price
                or float(p["ot_price"]) != want_ot
                or float(p["cost"]) != round(r["cost"], 2)
                or p["stock"] != 100
                or p["unit_name"] != r["uom"]
                or str(p["cate_id"]) != str(r["cate_id"])
                or p["temp_id"] != 1
                or p["spec_type"] != 0
                or p["sku_cnt"] != 1
                or p["bar_code"] != r["code"]):
            bad.append((p["id"], p["store_name"], p))
    check(not bad, f"all 37 products have correct price/ot_price/cost/stock/unit/cate/temp/spec/sku/bar_code")
    for b in bad[:5]:
        print("     bad:", b)

    # ---- 4. C-end detail -------------------------------------------------------
    print("\n=== 4. C-end product detail ===")
    sample = results[0]
    d = c.get(f'/api/front/product/detail/{sample["mall_product_id"]}')
    pi = d.get("productInfo") or {}
    check(pi.get("storeName") == sample["name"], f'productInfo.storeName == {sample["name"]}')
    print(f'     image field: {pi.get("image")!r}')
    print(f'     price/otPrice/cost/stock: {pi.get("price")}/{pi.get("otPrice")}/{pi.get("cost")}/{pi.get("stock")}')
    print(f'     unitName={pi.get("unitName")!r} cateId={pi.get("cateId")!r} barCode={pi.get("barCode")!r}')
    check(bool(pi.get("image")), "detail returns a non-empty image")
    check(float(pi.get("price") or 0) == round(sample["price"], 2), "detail price matches ERP reference_price")
    # store_info is deliberately NOT part of the C-end payload: StoreProductServiceImpl
    # .getH5Detail() selects an explicit column list that omits it.  Assert that
    # contract instead of expecting a value, and check the column itself via the DB.
    check("storeInfo" not in pi or pi.get("storeInfo") in ("", None),
          "C-end detail follows the curated column list (store_info not exposed)")
    cur.execute("SELECT COUNT(*) n FROM eb_store_product WHERE id BETWEEN 171 AND 207 "
                "AND (store_info IS NULL OR store_info='')")
    check(cur.fetchone()["n"] == 0, "store_info is populated in the DB for all 37 (browse-update fix holds)")
    check(len(d.get("productAttr") or []) == 1, "detail exposes exactly 1 spec attribute")
    check(isinstance(d.get("productValue"), dict) and len(d["productValue"]) >= 1,
          "detail exposes at least 1 spec value (single SKU)")

    # ---- 5. C-end category feed ------------------------------------------------
    print("\n=== 5. C-end category feed ===")
    cats = c.get("/api/front/category")
    names = {x.get("name") for x in (cats or [])}
    missing = {r["top"] for r in results} - names
    check(not missing, f"all 9 Nexus categories visible on C-end (missing {missing or 'none'})")

    # ---- 6. images fetchable ---------------------------------------------------
    print("\n=== 6. image reachability (MinIO shop bucket) ===")
    bad_img = []
    for r in results:
        url = MINIO + r["image"]
        for attempt in range(3):
            try:
                with urllib.request.urlopen(url, timeout=45) as resp:
                    n = len(resp.read())
                    if resp.status == 200 and n >= 1000:
                        break
                    if attempt == 2:
                        bad_img.append((r["code"], resp.status, n))
            except urllib.error.HTTPError as e:
                if attempt == 2:
                    bad_img.append((r["code"], e.code, 0))
            except Exception as e:                      # remote MinIO: retry transient resets
                if attempt == 2:
                    bad_img.append((r["code"], str(e)[:60], 0))
    check(not bad_img, f"all 37 images downloadable from MinIO ({len(bad_img)} bad)")
    for b in bad_img[:5]:
        print("     bad image:", b)

    con.close()
    print("\n" + ("=" * 60))
    print(f"RESULT: {'ALL CHECKS PASSED' if not fails else str(len(fails)) + ' FAILURES'}")
    for f in fails:
        print("  -", f)
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
