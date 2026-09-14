"""T2: publish the 37 rebuilt ERP finished goods into nexus-shop.

Pipeline:
  1. login
  2. ensure the Nexus category tree (9 大类 -> 品类, type=1)   [via admin API]
  3. create each product as SINGLE-spec + put it on the shelf  [via admin API]
  4. take the 7 pre-existing e-commerce products off the shelf [via admin API]
  5. repair site_url / localUploadUrl + a few cosmetic columns  [SQL, then cache flush]

Cache discipline: only step 5 touches the DB directly, and it is followed by a
Redis DB3 flush (the mall's business cache) + re-login, mirroring the ERP
project's rule that bypassing the app invalidates the cache.  Categories and
products always go through the API so the app evicts its own caches.

Contract facts verified against the running app's source:
  * POST /api/admin/store/product/save returns CommonResult<String> (no id),
    forces isShow=false, and NPEs on a null `content` -> send "".
    The new id is recovered by store_name.
  * specType=false avoids eb_store_product_attr_result entirely: that table is
    only read when specType is true (StoreProductServiceImpl:700-707), so the
    known "attr_result never written" defect cannot bite single-spec goods.
  * every attr entry must carry a non-empty optionList or save() throws
    "规格属性值不能为空".
  * /api/admin/category/save binds a DTO from FORM params (not JSON) and
    returns no id -> look the id up by (pid, name).
"""
from __future__ import annotations

import json
import socket
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).parent))
sys.stdout.reconfigure(encoding="utf-8")

from mall_client import MallClient

ERP = Path(r"D:\workspace\nexus-erp")
WORK = ERP / "doc" / "assets" / "_t1_work"
MANIFEST = WORK / "manifest.json"
UPLOADED = WORK / "uploaded.json"
RESULT = WORK / "t2_result.json"

MALL_DB = dict(host="127.0.0.1", user="root", password="root",
               database="crmeb", charset="utf8mb4",
               cursorclass=pymysql.cursors.DictCursor)

STOCK = 100                      # independent mall sellable pool (ERP stock is ~0)
MARKUP_OT = 1.2                  # 划线价 = reference_price * 1.2
SITE_URL = "http://127.0.0.1:20400"
LEGACY_OFFSHELF = [100, 101, 102, 159, 167, 168, 170]


def resp_command(sock: socket.socket, *args: str) -> bytes:
    out = [f"*{len(args)}\r\n".encode()]
    for a in args:
        b = a.encode("utf-8")
        out.append(f"${len(b)}\r\n".encode() + b + b"\r\n")
    sock.sendall(b"".join(out))
    return sock.recv(4096)


def flush_mall_cache() -> bytes:
    with socket.create_connection(("127.0.0.1", 6379), timeout=10) as s:
        resp_command(s, "AUTH", "ruoyi123")
        resp_command(s, "SELECT", "3")
        return resp_command(s, "FLUSHDB")


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    uploaded = json.loads(UPLOADED.read_text(encoding="utf-8"))

    c = MallClient()
    c.login()
    print("logged in\n")

    # autocommit matters: category/product rows are written by the app on its own
    # connection, and a long-lived REPEATABLE-READ transaction here would keep
    # reading a stale snapshot and conclude the row was never created.
    con = pymysql.connect(**MALL_DB, autocommit=True)
    cur = con.cursor()

    def find_category(pid: int, name: str) -> int | None:
        cur.execute("SELECT id FROM eb_category WHERE type=1 AND pid=%s AND name=%s AND status=1", (pid, name))
        r = cur.fetchone()
        return r["id"] if r else None

    def ensure_category(pid: int, name: str, sort: int) -> int:
        cid = find_category(pid, name)
        if cid:
            return cid
        c.post_form("/api/admin/category/save",
                    {"pid": pid, "name": name, "type": 1, "status": "true",
                     "sort": sort, "url": "", "extra": ""})
        cid = find_category(pid, name)
        if not cid:
            raise RuntimeError(f"category create failed: pid={pid} name={name}")
        return cid

    # ---- 1. category tree ------------------------------------------------------
    tops, seen = [], set()
    for m in manifest:
        if m["top"] not in seen:
            seen.add(m["top"])
            tops.append(m["top"])
    print(f"=== categories: {len(tops)} top-level ===")
    top_ids = {t: ensure_category(0, t, i) for i, t in enumerate(tops)}
    for t in tops:
        print(f'  {t:<16} -> {top_ids[t]}')

    leaf = {}
    for m in manifest:
        k = (m["top"], m["category"])
        if k not in leaf:
            leaf[k] = ensure_category(top_ids[m["top"]], m["category"], 0)
    print(f"  + {len(leaf)} leaf categories\n")

    # ---- 2. products -----------------------------------------------------------
    print("=== products ===")
    results = []
    for m in manifest:
        m["cate_id"] = leaf[(m["top"], m["category"])]
        key = uploaded[m["code"]]["key"]

        cur.execute("SELECT id FROM eb_store_product WHERE store_name=%s AND is_del=0", (m["name"],))
        r = cur.fetchone()
        if r:
            pid, status = r["id"], "exists"
        else:
            payload = build_payload(m, key)
            c.post("/api/admin/store/product/save", payload)
            cur.execute("SELECT id FROM eb_store_product WHERE store_name=%s AND is_del=0", (m["name"],))
            r = cur.fetchone()
            if not r:
                raise RuntimeError(f'save produced no row for {m["name"]}')
            pid, status = r["id"], "created"

        c.get(f"/api/admin/store/product/putOnShell/{pid}")
        results.append({**{k: m[k] for k in ("id", "code", "name", "top", "category",
                                             "price", "cost", "uom", "cate_id")},
                        "mall_product_id": pid, "image": key, "status": status})
        print(f'  {m["id"]:>3} {status:<8} id={pid:<6} {m["name"]}')

    # ---- 3. legacy products off the shelf --------------------------------------
    print("\n=== legacy e-commerce products off-shelf ===")
    for pid in LEGACY_OFFSHELF:
        try:
            c.get(f"/api/admin/store/product/offShell/{pid}")
            print(f"  offShell {pid} ok")
        except Exception as e:
            print(f"  offShell {pid} -> {e}")

    # ---- 4. direct SQL: site config + cosmetic columns --------------------------
    print("\n=== config / cosmetic SQL ===")
    for name in ("site_url", "localUploadUrl"):
        cur.execute("UPDATE eb_system_config SET value=%s WHERE name=%s", (SITE_URL, name))
    cur.execute("UPDATE eb_store_product SET temp_id=1 WHERE temp_id=2")
    print(f"  site_url/localUploadUrl = {SITE_URL}")
    print(f"  dangling temp_id=2 fixed: {cur.rowcount}")
    for m in manifest:
        cur.execute("UPDATE eb_store_product SET store_info=%s, bar_code=%s WHERE store_name=%s",
                    (m["spec"], m["code"], m["name"]))
    print(f"  store_info/bar_code backfilled: {cur.rowcount}")
    con.commit()
    con.close()

    print("\n=== flushing mall Redis DB3 (business cache) ===")
    print("  FLUSHDB ->", flush_mall_cache())

    RESULT.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\nwrote {RESULT}  ({len(results)} products)")


def build_payload(m, key):
    price = round(float(m["price"]), 2)
    cost = round(float(m["cost"]), 2)
    ot = round(price * MARKUP_OT, 2)
    return {
        "image": key,
        "sliderImage": json.dumps([key]),
        "storeName": m["name"],
        "keyword": f'{m["name"]},{m["code"]}',
        "cateId": str(m["cate_id"]),
        "unitName": m["uom"],
        "sort": 0,
        "isHot": False, "isBenefit": False, "isBest": False, "isNew": True, "isGood": False,
        "giveIntegral": 0,
        "isSub": False,
        "ficti": 0,
        "tempId": 1,
        "specType": False,
        "activity": [],
        "attr": [{
            "attrName": "规格",
            "attrValues": "默认",
            "isShowImage": False,
            # NOTE: ProductAttrOptionAddRequest.optionName is annotated
            # @JsonProperty("value"), so the wire key MUST be "value" --
            # sending "optionName" silently fails @NotBlank as "属性名不能为空".
            "optionList": [{"value": "默认", "sort": 0, "image": ""}],
        }],
        "attrValue": [{
            "productId": 0,
            "stock": STOCK,
            "suk": "默认",
            "price": price,
            "image": key,
            "cost": cost,
            "otPrice": ot,
            "weight": 0,
            "volume": 0,
            "brokerage": 0,
            "brokerageTwo": 0,
            "attrValue": json.dumps({"规格": "默认"}, ensure_ascii=False),
            "barCode": m["code"],
            "isDefault": True,
            "isShow": True,
            "quota": 0,
            "quotaShow": 0,
        }],
        "content": "",
        "couponIds": [],
        "guaranteeIds": "",
    }


if __name__ == "__main__":
    main()
