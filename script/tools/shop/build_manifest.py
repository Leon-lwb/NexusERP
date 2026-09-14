"""Build the T1 image-generation manifest for the 37 ERP finished goods.

Emits doc/assets/_t1_work/manifest.json with one record per product:
    {id, code, name, spec, uom, category, price, cost, prompt}

The prompt follows the template proven in the 2026-09-14 session (plan doc 5.4),
and always embeds the material code so generated files can be mapped back to
products by matching the code inside `dsh-imagegen/index.json`.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

WORK = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work")

TEMPLATE = (
    "专业电商产品主图摄影：{name}（型号 {code}），{spec}。"
    "纯白色无缝背景，柔和均匀的棚拍灯光，产品细节清晰锐利，"
    "无logo，无文字，无任何标注，纯净的电商主图风格，"
    "居中构图，留白充足，正方形画幅，2K 高清。"
)


def main() -> None:
    conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="root",
                           database="ry-erp", charset="utf8mb4",
                           cursorclass=pymysql.cursors.DictCursor)
    with conn, conn.cursor() as cur:
        cur.execute("""
            SELECT m.material_id, m.material_code, m.material_name, m.material_spec,
                   m.reference_price, m.standard_cost,
                   cat.category_name, parent.category_name AS top_name,
                   u.uom_name
            FROM bd_material m
            LEFT JOIN bd_material_category cat    ON cat.category_id = m.category_id
            LEFT JOIN bd_material_category parent ON parent.category_id = cat.parent_id
            LEFT JOIN bd_unit_of_measure u        ON u.uom_id = m.base_uom_id
            WHERE m.material_type = 3
            ORDER BY m.material_id
        """)
        rows = cur.fetchall()

    manifest = []
    for r in rows:
        spec = (r["material_spec"] or "").strip().rstrip("。")
        manifest.append({
            "id": r["material_id"],
            "code": r["material_code"],
            "name": r["material_name"],
            "spec": spec,
            "uom": r["uom_name"],
            "category": r["category_name"],
            "top": r["top_name"],
            "price": float(r["reference_price"]),
            "cost": float(r["standard_cost"]),
            "prompt": TEMPLATE.format(name=r["material_name"], code=r["material_code"], spec=spec),
        })

    WORK.mkdir(parents=True, exist_ok=True)
    out = WORK / "manifest.json"
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"wrote {out}  ({len(manifest)} products)")
    for m in manifest:
        print(f'  {m["id"]:>3} {m["code"]} {m["name"]}  [{m["top"]}/{m["category"]}]  ¥{m["price"]:g}')


if __name__ == "__main__":
    main()
