# -*- coding: utf-8 -*-
"""
物料成本数据体检：BOM 成本卷积能否算、算出来是否与主数据一致。

校验四件事：
  1. 全量物料的 standard_cost / reference_price / weight / volume 是否齐全
  2. 接口 /erp/bd/bom/costRollup/{bomId} 的合计 == bd_material.standard_cost（标准成本口径统一）
  3. 产成品的工序成本是否可算（routingCostAvailable）——依赖 bd_bom.routing_id 已关联
  4. mes_mbom.bom_id 是否指向产成品 BOM

用法：
    python script/tools/verify_cost_rollup.py [--report 输出路径]
退出码 0 = 全部通过，1 = 存在不一致。
"""
import argparse
import json
import subprocess
import sys
import urllib.request
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = []


def p(s=""):
    OUT.append(str(s))


def sql(q, db="ry-erp"):
    r = subprocess.run(["mysql", "-uroot", "-proot", "--default-character-set=utf8mb4",
                        "-N", "-B", "-D", db, "-e", q], capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.decode("utf-8", "replace"))
    return r.stdout.decode("utf-8", "replace")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    ap.add_argument("--token")
    ap.add_argument("--clientid")
    ap.add_argument("--report")
    a = ap.parse_args()
    tok = a.token or (ROOT / "backup-20260914" / "_tok.txt").read_text(encoding="utf-8-sig").strip()
    cid = a.clientid or (ROOT / "backup-20260914" / "_cid.txt").read_text(encoding="utf-8-sig").strip()

    def get(path):
        req = urllib.request.Request(a.base.rstrip("/") + path)
        req.add_header("Authorization", "Bearer " + tok)
        req.add_header("clientid", cid)
        with urllib.request.urlopen(req, timeout=180) as resp:
            return json.loads(resp.read().decode("utf-8"))

    # ---- 1. 字段齐全度
    p("## 1. 物料成本字段齐全度")
    ok1 = True
    for r in [l.split("\t") for l in sql(
            "SELECT material_type, COUNT(*), SUM(standard_cost IS NULL OR standard_cost=0), "
            "SUM(reference_price IS NULL OR reference_price=0), SUM(weight IS NULL OR weight=0), "
            "SUM(volume IS NULL OR volume=0) FROM bd_material "
            "WHERE del_flag='0' AND tenant_id='000000' GROUP BY material_type;").splitlines() if l.strip()]:
        t, n, c, pr, w, v = (int(x) for x in r)
        label = {1: "原材料", 2: "半成品", 3: "产成品"}.get(t, f"类型{t}")
        bad = c or pr or w or v
        ok1 = ok1 and not bad
        p(f"   {label:<5} {n:>4} 条   缺成本 {c} / 缺参考价 {pr} / 缺重量 {w} / 缺体积 {v}"
          + ("   ✅" if not bad else "   ✗"))

    # ---- 2/3. 卷积一致性与工序成本
    p()
    p("## 2. 接口 costRollup vs bd_material.standard_cost")
    rows = [l.split("\t") for l in sql(
        "SELECT b.bom_id, m.material_name, m.standard_cost, m.reference_price FROM bd_bom b "
        "JOIN bd_material m ON m.material_id=b.parent_material_id "
        "WHERE b.del_flag='0' AND m.material_type=3 ORDER BY b.bom_id;").splitlines() if l.strip()]
    ok2 = ok3 = True
    bad = []
    routing_ok = 0
    gms = []
    for bom_id, name, std, price in rows:
        d = get(f"/erp/bd/bom/costRollup/{bom_id}")["data"]
        api = Decimal(str(d["totalCostPerUnit"]))
        diff = abs(api - Decimal(str(std)))
        if diff > Decimal("0.01"):
            ok2 = False
            bad.append((name, api, Decimal(str(std)), diff))
        if d.get("routingCostAvailable"):
            routing_ok += 1
        else:
            ok3 = False
        if Decimal(str(price)) > 0:
            gms.append((float((Decimal(str(price)) - api) / Decimal(str(price)) * 100), name))
    p(f"   比对 {len(rows)} 个产成品，不一致 {len(bad)} 个" + ("   ✅" if ok2 else "   ✗"))
    for name, api, std, diff in bad:
        p(f"     ✗ {name}: 接口 {api} / 主数据 {std} / 差异 {diff}")
    p(f"   工序成本可算(routingCostAvailable=true): {routing_ok}/{len(rows)}"
      + ("   ✅" if ok3 else "   ✗"))
    gms.sort()
    if gms:
        p(f"   毛利率: 最小 {gms[0][0]:.1f}% ({gms[0][1]}) / 中位 {gms[len(gms)//2][0]:.1f}% "
          f"/ 最大 {gms[-1][0]:.1f}% ({gms[-1][1]})")

    # ---- 4. MES MBOM
    p()
    p("## 3. mes_mbom 是否指向产成品 BOM")
    r = sql("SELECT SUM(m.material_type=3), SUM(m.material_type=2), COUNT(*) FROM `ry-mes`.mes_mbom mb "
            "LEFT JOIN `ry-erp`.bd_bom b ON b.bom_id=mb.bom_id "
            "LEFT JOIN `ry-erp`.bd_material m ON m.material_id=b.parent_material_id;",
            db="ry-mes").split("\t")
    fg, semi, tot = (int(x) if x != "NULL" else 0 for x in r)
    ok4 = (fg == tot and tot > 0)
    p(f"   指向产成品 {fg} / 指向半成品 {semi} / 总数 {tot}" + ("   ✅" if ok4 else "   ✗"))

    p()
    p("## 结论")
    for name, ok in (("成本字段齐全", ok1), ("卷积与主数据一致", ok2),
                     ("工序成本可算", ok3), ("MES MBOM 指向正确", ok4)):
        p(f"   {'✅ PASS' if ok else '✗ FAIL'}  {name}")
    text = "\n".join(OUT)
    if a.report:
        Path(a.report).write_text(text, encoding="utf-8", newline="\n")
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass
    print(text)
    return 0 if all((ok1, ok2, ok3, ok4)) else 1


if __name__ == "__main__":
    sys.exit(main())
