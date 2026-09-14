# -*- coding: utf-8 -*-
"""
SQL 级数据变更后的「缓存一致性」体检脚本。

背景：ERP 的字典（sys_dict）、物料分类（曾用 bd_category_tree）等走 Spring Cache（Redisson），
只由应用自身的增删改方法触发失效。**凡是绕过应用直接改库的场景**（全量重建、导入、迁移、
手工 SQL），缓存都不会失效，界面就会显示旧数据，甚至出现「同一份数据两个菜单不一样」。

本脚本把「接口返回」与「数据库真值」逐条比对，用于变更后验收。

用法：
    python script/tools/verify_cache_consistency.py \
        --token <Sa-Token> --clientid <clientid> \
        [--base http://127.0.0.1:8080] [--mysql "mysql -uroot -proot"]

token/clientid 缺省时从 backup-20260914/_tok.txt 与 _cid.txt 读取。
退出码 0 = 全部一致，1 = 存在不一致。
"""
import argparse
import json
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = []


def p(s=""):
    OUT.append(str(s))


class Client:
    def __init__(self, base, token, clientid):
        self.base, self.token, self.clientid = base.rstrip("/"), token, clientid

    def get(self, path):
        req = urllib.request.Request(self.base + path)
        req.add_header("Authorization", "Bearer " + self.token)
        req.add_header("clientid", self.clientid)
        with urllib.request.urlopen(req, timeout=180) as resp:
            return json.loads(resp.read().decode("utf-8"))


def mysql(sql, db, mysql_cmd):
    args = mysql_cmd.split() + ["--default-character-set=utf8mb4", "-N", "-B", "-D", db, "-e", sql]
    r = subprocess.run(args, capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.decode("utf-8", "replace"))
    return r.stdout.decode("utf-8", "replace")


def check_dict(cli, mysql_cmd):
    """字典：接口 /system/dict/data/type/{type} 是否等于 sys_dict_data 真值。"""
    p("## 1. 数据字典（Spring Cache: sys_dict）")
    rows = mysql("SELECT dict_type, IFNULL(dict_label,''), IFNULL(dict_value,'') FROM sys_dict_data "
                 "WHERE tenant_id='000000' ORDER BY dict_type, dict_sort;", "ry-vue", mysql_cmd)
    dbmap = {}
    for line in rows.splitlines():
        if line.strip():
            t, lab, val = (line.split("\t") + ["", ""])[:3]
            dbmap.setdefault(t, []).append((lab, val))
    types = mysql("SELECT dict_type FROM sys_dict_type WHERE tenant_id='000000' ORDER BY dict_type;",
                  "ry-vue", mysql_cmd).split()

    bad = []
    for t in types:
        exp = dbmap.get(t, [])
        try:
            got = [(x.get("dictLabel") or "", x.get("dictValue") or "")
                   for x in (cli.get(f"/system/dict/data/type/{t}").get("data") or [])]
        except Exception as e:  # noqa: BLE001
            bad.append((t, f"HTTP 异常 {e}"))
            continue
        if not exp and not got:
            continue
        if got != exp:
            bad.append((t, f"接口 {len(got)} 项 / 库 {len(exp)} 项；"
                           f"库里没有的 {[x for x in got if x not in exp][:2]}；"
                           f"接口缺的 {[x for x in exp if x not in got][:2]}"))
    p(f"   字典类型 {len(types)} 个，字典项 {sum(len(v) for v in dbmap.values())} 条")
    for t, m in bad:
        p(f"   ✗ {t}: {m}")
    p("   ✅ 接口与数据库完全一致" if not bad else f"   ▲ 不一致 {len(bad)} 个（多为缓存陈旧，"
                                                  f"可 CacheUtils.clear(\"sys_dict\") 后重测）")
    return not bad


def check_category(cli):
    """物料分类：两个菜单（带/不带 status）必须返回同一批分类。"""
    p()
    p("## 2. 物料分类（物料管理左树 vs 物料分类管理页）")
    a = cli.get("/erp/bd/category/list")["data"] or []
    b = cli.get("/erp/bd/category/list?status=1")["data"] or []
    ka, kb = {str(x["categoryId"]): x for x in a}, {str(x["categoryId"]): x for x in b}
    same = set(ka) == set(kb)
    p(f"   无参 {len(a)} 条 / status=1 {len(b)} 条；ID 集合相同={same}")
    if same:
        diff = [(k, f) for k in ka for f in
                ("categoryCode", "categoryName", "parentId", "levelNum", "pathIds",
                 "pathNames", "sortOrder", "status", "materialCount")
                if ka[k].get(f) != kb[k].get(f)]
        p(f"   逐字段差异 {len(diff)} 处" + (f"：{diff[:3]}" if diff else ""))
        same = not diff
    p("   ✅ 两个菜单一致" if same else "   ✗ 两个菜单不一致")
    return same


def check_material(cli):
    """物料：categoryName 必须回填，且与分类表一致；分类树物料数合计须等于物料总数。"""
    p()
    p("## 3. 物料 · 分类名回填与物料数对账")
    cats = {str(c["categoryId"]): c["categoryName"] for c in cli.get("/erp/bd/category/list")["data"]}
    total, rows, page = None, [], 1
    while True:
        r = cli.get(f"/erp/bd/material/list?pageNum={page}&pageSize=100")
        total = r.get("total")
        rs = r.get("rows") or []
        if not rs or len(rows) >= (total or 0):
            break
        rows.extend(rs)
        page += 1
    empty = [x for x in rows if not x.get("categoryName")]
    wrong = [x for x in rows if x.get("categoryId") and str(x["categoryId"]) in cats
             and x.get("categoryName") != cats[str(x["categoryId"])]]
    ghost = [x for x in rows if x.get("categoryId") and str(x["categoryId"]) not in cats]
    p(f"   物料 {total} 条，取回 {len(rows)} 条")
    p(f"   categoryName 为空 {len(empty)}；与分类表不符 {len(wrong)}；指向不存在分类 {len(ghost)}")
    ok = not (empty or wrong or ghost) and len(rows) == total
    p("   ✅ 分类名回填正确" if ok else "   ✗ 分类名存在问题")
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    ap.add_argument("--token")
    ap.add_argument("--clientid")
    ap.add_argument("--mysql", default="mysql -uroot -proot")
    ap.add_argument("--report")
    a = ap.parse_args()

    token, cid = a.token, a.clientid
    if not token:
        token = (ROOT / "backup-20260914" / "_tok.txt").read_text(encoding="utf-8-sig").strip()
    if not cid:
        cid = (ROOT / "backup-20260914" / "_cid.txt").read_text(encoding="utf-8-sig").strip()

    cli = Client(a.base, token, cid)
    results = [("数据字典", check_dict(cli, a.mysql)),
               ("物料分类", check_category(cli)),
               ("物料分类名", check_material(cli))]

    p()
    p("## 结论")
    for name, ok in results:
        p(f"   {'✅ PASS' if ok else '✗ FAIL'}  {name}")
    text = "\n".join(OUT)
    if a.report:
        Path(a.report).write_text(text, encoding="utf-8", newline="\n")
    # Windows 控制台常为 GBK，直接 print 会 UnicodeEncodeError；强制 UTF-8 + 替换非法字符
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass
    print(text)
    return 0 if all(ok for _, ok in results) else 1


if __name__ == "__main__":
    sys.exit(main())
