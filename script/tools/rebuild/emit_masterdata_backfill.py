# -*- coding: utf-8 -*-
"""
生成「物料主数据补齐」SQL（p5_masterdata_backfill.sql）。

补齐 / 修正内容：
  1. 新增分类节点（外购件与机电、轴承、密封件、光学件、光伏材料 …）
  2. 修正误挂分类（原材料按 raw_table 显式映射；半成品按 taxonomy.SEMI_CAT）
  3. 半成品分类变更后同步替换其 BOM 的「分类模板行」
  4. 原材料：standard_cost / reference_price / weight / volume
  5. **按目标成本标定**：缩放 BOM 用量与标准工时，使
        材料成本 ≈ 售价 × 材料占比，工序成本 ≈ 售价 × 工序占比
     （占比按产品行业类别给定，见 RATIO）。起因：
      - 半成品模板是一刀切（一个 M12 螺母也消耗 1.5kg 钢）
      - bd_routing_step.standard_hours 是工序级默认工时，被逐件套用
        （每步 工时 × (50×2 + 100×1)，导致螺栓组套工序成本也要 ¥770）
  6. 半成品 / 产成品：卷积得到的 standard_cost，以及卷积得到的 weight / volume
  7. bd_bom.routing_id ← 按 parent_material_id 关联 bd_routing
     （原 137 张 BOM 全为 NULL，成本卷积取不到工艺路线、工序成本恒为 0）

卷积口径与 BdBomServiceImpl.costRollup 一致：
    标准成本 = Σ(叶子料 累计用量 × 标准成本) + Σ(工序 标准工时 × (人工费率×人数 + 机器费率×机台数))

用法：
    python emit_masterdata_backfill.py [输出路径]
"""
import subprocess
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import taxonomy  # noqa: E402

DB_CAT_PREFIX = 117          # 新分类 ID 起始（现有 1..116）
BY = 1
DEPT = 103

# 无 BOM 的半成品（叶子件）需直接给定：固化在 SPI Flash 里的固件包
NO_BOM_LEAF = {
    "网关固件 FW-GW1000": dict(cost=Decimal("150.00"), weight=Decimal("0.0020"),
                              vol=Decimal("0.000001")),
}

# 目标成本占比（按产成品二级分类）：(材料占售价比, 工序占售价比)，余下为毛利
RATIO = {
    "FIN_01": (Decimal("0.52"), Decimal("0.20")),   # 金属制品与五金
    "FIN_02": (Decimal("0.62"), Decimal("0.14")),   # 机械设备
    "FIN_03": (Decimal("0.68"), Decimal("0.12")),   # 电子电气
    "FIN_04": (Decimal("0.60"), Decimal("0.16")),   # 汽车与交通
    "FIN_05": (Decimal("0.42"), Decimal("0.23")),   # 医疗器械
    "FIN_07": (Decimal("0.55"), Decimal("0.17")),   # 消费品与轻工
    "FIN_08": (Decimal("0.48"), Decimal("0.22")),   # 塑料橡胶与复合材料
    "FIN_09": (Decimal("0.72"), Decimal("0.10")),   # 新能源与电气设备
    "FIN_10": (Decimal("0.58"), Decimal("0.16")),   # 其他离散装配
}
# 只缩放宽泛模板行；产品专属料（如机床床身 1250kg 铸铁、机架 42kg 型钢）已按件定过量，不动。
# 上下限放宽到 [0.02, 30]：模板值本就是占位值，无论放大缩小都在合理语义内。
SCALE_MIN, SCALE_MAX = Decimal("0.02"), Decimal("30.0")


def sql(q):
    r = subprocess.run(["mysql", "-uroot", "-proot", "--default-character-set=utf8mb4",
                        "-N", "-B", "-D", "ry-erp", "-e", q], capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.decode("utf-8", "replace"))
    return r.stdout.decode("utf-8", "replace")


def rows(q):
    return [l.split("\t") for l in sql(q).splitlines() if l.strip()]


def num(x):
    return Decimal(str(x))


def d(v, places):
    return format(Decimal(v).quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP), "f")


# ================================================================ 读取现状
cats, cat_by_id = {}, {}
for r in rows("SELECT category_id, category_code, category_name, parent_id, level_num "
              "FROM bd_material_category WHERE del_flag='0';"):
    c = dict(id=int(r[0]), code=r[1], name=r[2], parent=int(r[3]), level=int(r[4]))
    cats[c["code"]] = c
    cat_by_id[c["id"]] = c

mats, mat_sets = {}, {}
for r in rows("SELECT m.material_id, m.material_name, m.material_type, m.category_id, "
              "IFNULL(u.uom_name,''), IFNULL(m.material_spec,''), IFNULL(m.standard_cost,0), "
              "IFNULL(m.reference_price,0), IFNULL(m.weight,0), IFNULL(m.volume,0) "
              "FROM bd_material m LEFT JOIN bd_unit_of_measure u ON u.uom_id=m.base_uom_id "
              "WHERE m.del_flag='0' AND m.tenant_id='000000';"):
    mats[int(r[0])] = dict(id=int(r[0]), name=r[1], mtype=int(r[2]), cat=int(r[3]), uom=r[4],
                           spec=r[5], cost=num(r[6]), price=num(r[7]), weight=num(r[8]),
                           vol=num(r[9]))
mid_by_name = {m["name"]: m["id"] for m in mats.values()}
cat_code_of_material = {m["id"]: cat_by_id[m["cat"]]["code"] for m in mats.values()}


def set_mat(mid, **kw):
    mat_sets.setdefault(mid, {}).update(kw)
    mats[mid].update(**kw)


boms = {int(r[0]): dict(id=int(r[0]), parent=int(r[1])) for r in
        rows("SELECT bom_id, parent_material_id FROM bd_bom WHERE del_flag='0';")}
bom_by_parent = {b["parent"]: b for b in boms.values()}

details = {}
for r in rows("SELECT detail_id, bom_id, child_material_id, quantity_per_parent, source_type, "
              "loss_rate, operation_step, lead_time, effective_date, expiry_date, sort_order, "
              "status, remark, supply_type FROM bd_bom_detail WHERE del_flag='0' "
              "ORDER BY bom_id, sort_order;"):
    details.setdefault(int(r[1]), []).append(dict(
        detail_id=int(r[0]), bom_id=int(r[1]), child=int(r[2]), qty=num(r[3]), source_type=r[4],
        loss=r[5] or "0", op=r[6] or "NULL", lead=r[7] or "NULL", eff=r[8] or "NULL",
        exp=r[9] or "NULL", sort=int(r[10]), status=r[11] or "1", remark=r[12] or "NULL",
        supply=r[13] or "NULL", qty_old=num(r[3])))

rt_steps = {}
for r in rows("SELECT step_id, routing_id, standard_hours, labor_count, machine_rate, labor_rate, "
              "machine_count FROM bd_routing_step;"):
    rt_steps.setdefault(int(r[1]), []).append(
        dict(step_id=int(r[0]), h=num(r[2]), lc=int(r[3]), mr=num(r[4]), lr=num(r[5]),
             mc=int(r[6]), h_old=num(r[2])))

routing_by_material = {int(r[0]): int(r[1]) for r in
                       rows("SELECT material_id, routing_id FROM bd_routing WHERE del_flag='0' "
                            "AND material_id IS NOT NULL;")}
max_det_id = max(dd["detail_id"] for v in details.values() for dd in v) if details else 0

# ================================================================ 1. 新增分类
out = ["-- ============================================================",
       "-- p5 · 物料主数据补齐：分类修正 + 成本/重量/体积 + BOM↔工艺路线 + 目标成本标定",
       "-- 生成器：script/tools/rebuild/emit_masterdata_backfill.py",
       "-- ============================================================", ""]


def path_of(cid):
    ids, names, cur, guard = [], [], cid, 0
    while cur and guard < 20:
        c = cat_by_id.get(cur)
        if not c:
            break
        ids.append(str(cur))
        names.append(c["name"])
        cur = c["parent"]
        guard += 1
    ids.reverse()
    names.reverse()
    return ids, names


new_cats, next_id = {}, DB_CAT_PREFIX
for code, name, parent_code in taxonomy.CATEGORY_ADD:
    if code in cats:
        continue
    parent = cats[parent_code]["id"] if parent_code else 0
    c = dict(id=next_id, code=code, name=name, parent=parent)
    new_cats[code] = c
    cats[code] = c
    cat_by_id[next_id] = c
    next_id += 1

out.append("-- ---------- 1. 新增分类节点 %d 个 ----------" % len(new_cats))
if new_cats:
    out.append("INSERT INTO `ry-erp`.bd_material_category")
    out.append("  (category_id, tenant_id, parent_id, category_code, category_name, category_desc,")
    out.append("   level_num, path_ids, path_names, sort_order, status, remark,")
    out.append("   create_dept, create_by, create_time, del_flag) VALUES")
    vals = []
    for i, (code, c) in enumerate(new_cats.items(), start=1):
        ids, names = path_of(c["id"])
        c.update(level=len(ids), path_ids=",".join(ids), path_names=",".join(names), sort=200 + i)
        vals.append("(%d,'000000',%d,'%s','%s',NULL,%d,'%s','%s',%d,1,NULL,%d,%d,NOW(),'0')"
                    % (c["id"], c["parent"], code, c["name"], c["level"], c["path_ids"],
                       c["path_names"], c["sort"], DEPT, BY))
    out.append(",\n".join(vals) + ";")
out.append("")

# ================================================================ 1.5 高值外购总成
# 5 个产品的 BOM 缺真正占成本大头的外购总成，标准成本只有售价的一两成。
# 补齐：新增物料 → 挂到对应产成品 BOM → 标定时作为固定成本不缩放。
uom_id_by_name = {r[1]: int(r[0]) for r in
                  rows("SELECT uom_id, uom_name FROM bd_unit_of_measure;")}
mid_by_name = {m["name"]: m["id"] for m in mats.values()}
next_mat_id = max(mats) + 1
det_seq = max_det_id
comp_new_mats, comp_new_dets, comp_pairs = [], [], []
seen_comp = set()
for pname, items in taxonomy.COMPONENT_BOM.items():
    pm = mid_by_name.get(pname)
    if pm is None:
        raise SystemExit("COMPONENT_BOM 里的产品不存在：%r" % pname)
    bom = bom_by_parent.get(pm)
    if bom is None:
        raise SystemExit("产品 %r 没有 BOM，无法挂外购总成" % pname)
    have = {dd["child"] for dd in details[bom["id"]]}
    for order, (cname, cspec, cuom, cqty) in enumerate(items, start=1):
        if cname in mid_by_name:
            cid = mid_by_name[cname]
        else:
            cid = next_mat_id
            next_mat_id += 1
            cat, cost, w, v = taxonomy.RAW_TABLE[cname]
            u = uom_id_by_name.get(cuom, uom_id_by_name["件"])
            newm = dict(id=cid, name=cname, mtype=1, cat=cats[cat]["id"], uom=cuom, spec=cspec,
                        cost=num(cost), price=num(cost) * Decimal("1.08"), weight=num(w),
                        vol=num(v))
            mats[cid] = newm
            mid_by_name[cname] = cid
            cat_code_of_material[cid] = cat
            comp_new_mats.append(dict(id=cid, name=cname, spec=cspec, uom=u, cat=cats[cat]["id"],
                                      cost=num(cost), weight=num(w), vol=num(v)))
        if cid in have:
            continue
        have.add(cid)
        det_seq += 1
        dd = dict(detail_id=det_seq, bom_id=bom["id"], child=cid, qty=num(cqty),
                  source_type="PROCUREMENT", loss="0", op="NULL", lead="NULL", eff="NULL",
                  exp="NULL", sort=900 + order, status="1", remark="NULL",
                  supply="SELF_PROVIDED", qty_old=num(cqty))
        details.setdefault(bom["id"], []).append(dd)
        comp_new_dets.append(dd)
        comp_pairs.append((cname, pname, cqty))
        seen_comp.add(cname)

out.append("-- ---------- 1.5 高值外购总成补齐（%d 个物料 / %d 条 BOM 明细） ----------"
           % (len(comp_new_mats), len(comp_new_dets)))
if comp_new_mats:
    out.append("-- 这 5 个产品的 BOM 只写了钢材与通用件，缺占成本大头的外购总成，")
    out.append("-- 导致标准成本只有售价的一两成。补齐后才可能算进目标毛利区间。")
    out.append("INSERT INTO `ry-erp`.bd_material")
    out.append("  (material_id, tenant_id, material_code, material_name, material_spec, material_desc,")
    out.append("   category_id, base_uom_id, purchase_uom_id, inventory_uom_id, sale_uom_id,")
    out.append("   material_type, source_type, is_batch_manage, is_serial_manage, safety_stock,")
    out.append("   min_stock, max_stock, standard_cost, reference_price, status, quality_status,")
    out.append("   shelf_life, weight, volume, remark, create_by, create_dept, del_flag,")
    out.append("   is_phantom, inspection_required) VALUES")
    vals = []
    for m in comp_new_mats:
        vals.append("(%d,'000000','IF20260914%03d','%s','%s',NULL,%d,%d,%d,%d,%d,"
                    "1,'PROCUREMENT',0,0,0,0,0,%s,%s,1,NULL,0,%s,%s,NULL,%d,%d,'0',0,1)"
                    % (m["id"], m["id"], m["name"], m["spec"], m["cat"], m["uom"], m["uom"],
                       m["uom"], m["uom"], d(m["cost"], 4),
                       d((m["cost"] * Decimal("1.08")).quantize(Decimal("0.01")), 4),
                       d(m["weight"], 4), d(m["vol"], 9), BY, DEPT))
    out.append(",\n".join(vals) + ";")
if comp_new_dets:
    out.append("INSERT INTO `ry-erp`.bd_bom_detail")
    out.append("  (detail_id, tenant_id, bom_id, child_material_id, quantity_per_parent, source_type,")
    out.append("   loss_rate, operation_step, lead_time, effective_date, expiry_date, sort_order,")
    out.append("   status, remark, create_by, create_dept, del_flag, supply_type) VALUES")
    vals = ["(%d,'000000',%d,%d,%s,'PROCUREMENT',0,NULL,NULL,NULL,NULL,%d,1,NULL,%d,%d,'0','SELF_PROVIDED')"
            % (dd["detail_id"], dd["bom_id"], dd["child"], d(dd["qty"], 6), dd["sort"], BY, DEPT)
            for dd in comp_new_dets]
    out.append(",\n".join(vals) + ";")
    out.append("")
    print("   外购总成挂载：" + "；".join("%s → %s ×%s" % (c, p_, q) for c, p_, q in comp_pairs))
    # 物料编码手工编到了 IF20260914<max>，把自动编码计数器种子顶上去，
    # 否则下次「自动生成编码」从 1 开始，会直接撞上已有物料编码。
    mc = max(m["id"] for m in comp_new_mats)
    out.append("-- 同步 MATERIAL_CODE 自动编码计数器（rule_id=236），避免自动生成编码撞号")
    out.append("UPDATE `ry-vue`.sys_auto_code_result SET gen_date = '20260914',")
    out.append("  last_result = 'IF20260914%03d', last_serial_no = %d" % (mc, mc))
    out.append("  WHERE rule_id = 236 AND (last_serial_no IS NULL OR last_serial_no < %d);" % mc)
    out.append("INSERT INTO `ry-vue`.sys_auto_code_result")
    out.append("  (code_id, tenant_id, rule_id, gen_date, gen_index, last_result, last_serial_no,")
    out.append("   last_input_char, remark)")
    out.append("SELECT 999999999999999236, '000000', 236, '20260914', NULL, 'IF20260914%03d', %d,"
               " NULL, '物料主数据补齐后同步计数'" % (mc, mc))
    out.append("FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ry-vue`.sys_auto_code_result WHERE rule_id = 236);")
out.append("")

# ================================================================ 2. 分类修正
raw_changes, raw_unknown, semi_changes = [], [], []
for m in mats.values():
    if m["mtype"] == 1:
        if m["name"] not in taxonomy.RAW_TABLE:
            raw_unknown.append(m["name"])
            continue
        tgt = taxonomy.RAW_TABLE[m["name"]][0]
        if cats[tgt]["id"] != m["cat"]:
            raw_changes.append((m, cats[tgt]["id"]))
    elif m["mtype"] == 2:
        tgt = taxonomy.SEMI_CAT.get(m["name"])
        if tgt and cats[tgt]["id"] != m["cat"]:
            semi_changes.append((m, cats[tgt]["id"]))

if raw_unknown:
    raise SystemExit("以下原材料未登记在 raw_table.RAW_TABLE：\n  " + "\n  ".join(raw_unknown))
if len(taxonomy.SEMI_CAT) != sum(1 for m in mats.values() if m["mtype"] == 2):
    raise SystemExit("taxonomy.SEMI_CAT 条数与库中半成品数不一致，请先核对")

all_changes = raw_changes + semi_changes
out.append("-- ---------- 2. 分类修正（原材料 %d / 半成品 %d） ----------"
           % (len(raw_changes), len(semi_changes)))
if all_changes:
    out.append("UPDATE `ry-erp`.bd_material SET category_id = CASE material_id")
    for m, cid in all_changes:
        out.append("  WHEN %d THEN %d" % (m["id"], cid))
    out.append("  ELSE category_id END WHERE material_id IN (%s);"
               % ",".join(str(m["id"]) for m, _ in all_changes))
# 分类变了，重算内存里的分类码
for m, cid in all_changes:
    cat_code_of_material[m["id"]] = cat_by_id[cid]["code"]
out.append("")

# ================================================================ 3. 半成品 BOM 模板换行
TEMPLATE_MATS = {n for tpl in taxonomy.SEMI_RAW_TEMPLATE.values() for n, _s, _u, _q in tpl}
TEMPLATE_MATS |= {n for n, _s, _u, _q in taxonomy.SEMI_ALUMINIUM_TEMPLATE}

removed_ids, qty_updates, new_rows = [], [], []
# det_seq 已在 §1.5 从 max_det_id 起算，这里必须接着用，不能重置（否则与外购总成明细 ID 撞车）
for m, _cid in semi_changes:
    bom = bom_by_parent.get(m["id"])
    if not bom:
        continue
    old = details.get(bom["id"], [])
    keep = [dd for dd in old if mats[dd["child"]]["name"] not in TEMPLATE_MATS]
    removed_ids.extend(dd["detail_id"] for dd in old if dd not in keep)
    by_child = {dd["child"]: dd for dd in keep}
    order = list(keep)
    for cname, _spec, _uom, qty in taxonomy.semi_template(m["name"]):
        cid = mid_by_name[cname]
        if cid in by_child:
            dd = by_child[cid]
            dd["qty"] = dd["qty"] + num(qty)
            qty_updates.append((dd["detail_id"], dd["qty"]))
        else:
            det_seq += 1
            dd = dict(detail_id=det_seq, bom_id=bom["id"], child=cid, qty=num(qty),
                      source_type="PROCUREMENT", loss="0", op="NULL", lead="NULL", eff="NULL",
                      exp="NULL", sort=0, status="1", remark="NULL", supply="SELF_PROVIDED",
                      qty_old=num(qty))
            by_child[cid] = dd
            order.append(dd)
            new_rows.append(dd)
    for i, dd in enumerate(order, start=1):
        dd["sort"] = i * 10
    details[bom["id"]] = order

out.append("-- ---------- 3. 半成品 BOM 模板行替换（涉及 %d 张 BOM） ----------" % len(semi_changes))
if removed_ids:
    # 被删掉的模板行若有替代料引用，一并清除，避免留下悬挂外键
    out.append("DELETE FROM `ry-erp`.bd_bom_alternative WHERE detail_id IN (%s);"
               % ",".join(str(i) for i in removed_ids))
    out.append("DELETE FROM `ry-erp`.bd_bom_detail WHERE detail_id IN (%s);"
               % ",".join(str(i) for i in removed_ids))
if new_rows:
    out.append("INSERT INTO `ry-erp`.bd_bom_detail")
    out.append("  (detail_id, tenant_id, bom_id, child_material_id, quantity_per_parent, source_type,")
    out.append("   loss_rate, operation_step, lead_time, effective_date, expiry_date, sort_order,")
    out.append("   status, remark, create_by, create_dept, del_flag, supply_type) VALUES")
    vals = ["(%d,'000000',%d,%d,%s,'%s',%s,%s,%s,%s,%s,%d,%s,%s,%d,%d,'0','%s')"
            % (dd["detail_id"], dd["bom_id"], dd["child"], d(dd["qty"], 6), dd["source_type"],
               dd["loss"], dd["op"], dd["lead"], dd["eff"], dd["exp"], dd["sort"], dd["status"],
               dd["remark"], BY, DEPT, dd["supply"]) for dd in new_rows]
    out.append(",\n".join(vals) + ";")
out.append("")

# ================================================================ 4. 原材料成本/重量/体积
for m in list(mats.values()):
    if m["mtype"] == 1:
        cost, w, v = taxonomy.raw_cost(m["name"])
        cost = num(cost)
        set_mat(m["id"], cost=cost, price=(cost * Decimal("1.08")).quantize(Decimal("0.01")),
                weight=num(w), vol=num(v))
for name, v in NO_BOM_LEAF.items():
    if name in mid_by_name:
        set_mat(mid_by_name[name], cost=v["cost"], price=v["cost"] * Decimal("1.15"),
                weight=v["weight"], vol=v["vol"])

# ================================================================ 5. 目标成本标定
semi_boms_of = {}            # 产成品 material_id -> [半成品 bom_id]
semi_owner = {}
for m in mats.values():
    if m["mtype"] != 3:
        continue
    bom = bom_by_parent.get(m["id"])
    if not bom:
        continue
    sb = []
    for dd in details[bom["id"]]:
        c = mats[dd["child"]]
        if c["mtype"] == 2 and c["id"] in bom_by_parent:
            sb.append(bom_by_parent[c["id"]]["id"])
            if c["id"] in semi_owner and semi_owner[c["id"]] != m["id"]:
                raise SystemExit("半成品 %s 被多个产成品共用，标定会重复缩放" % c["name"])
            semi_owner[c["id"]] = m["id"]
    semi_boms_of[m["id"]] = sb


def leaf_rows(bom_id, mult, seen):
    """[(叶子物料id, 累计用量, 是否由模板行产生)]

    只有「路径最后一行」是不是模板行有意义：FG→半成品那一行用量恒为 1 且不缩放，
    所以半成品内部的叶子是否随缩放变动，取决于半成品那一行本身。
    """
    res = []
    for dd in details.get(bom_id, []):
        cid = dd["child"]
        q = dd["qty"] * mult
        sub = bom_by_parent.get(cid)
        if sub and cid not in seen:
            res.extend(leaf_rows(sub["id"], q, seen | {cid}))
        else:
            res.append((cid, q, mats[cid]["mtype"] == 1
                        and mats[cid]["name"] in TEMPLATE_MATS))
    return res


def is_tpl_row(dd):
    c = mats[dd["child"]]
    return c["mtype"] == 1 and c["name"] in TEMPLATE_MATS


calib = []
for m in mats.values():
    if m["mtype"] != 3:
        continue
    bom = bom_by_parent.get(m["id"])
    if not bom or not m["price"]:
        continue
    leaves = leaf_rows(bom["id"], Decimal(1), set())
    FIX = taxonomy.FIXED_MATERIALS
    # 三档：① 高值外购总成=真实采购件，恒定不缩放 ② 模板占位行 ③ 其它按件定过量行
    fixed = sum((q * mat_sets[cid]["cost"] for cid, q, _t in leaves
                 if mats[cid]["name"] in FIX), Decimal(0))
    tpl_cost = sum((q * mat_sets[cid]["cost"] for cid, q, t in leaves
                    if t and mats[cid]["name"] not in FIX), Decimal(0))
    other = sum((q * mat_sets[cid]["cost"] for cid, q, t in leaves
                 if not t and mats[cid]["name"] not in FIX), Decimal(0))
    fin2 = cat_code_of_material[m["id"]][:6]
    r_mat, r_rt = RATIO.get(fin2, (Decimal("0.58"), Decimal("0.16")))
    tgt_mat = m["price"] * r_mat
    cur_mat = fixed + tpl_cost + other
    if cur_mat > tgt_mat:
        # 用量整体偏大：把非固定行等比缩小（缩小永远安全）
        s_mat = max((tgt_mat - fixed) / (tpl_cost + other), SCALE_MIN) \
            if (tpl_cost + other) > 0 else Decimal(1)
        rows_to_scale = [dd for bid in [bom["id"]] + semi_boms_of.get(m["id"], [])
                         for dd in details.get(bid, [])
                         if mats[dd["child"]]["mtype"] == 1
                         and mats[dd["child"]]["name"] not in FIX]
    else:
        # 用量整体偏小：只放大模板占位行。产品专属料（机床床身 1250kg 铸铁、
        # 机架 42kg 型钢等）已按件定过量，放大它们会得到物理上荒唐的值。
        s_mat = min((tgt_mat - fixed - other) / tpl_cost, SCALE_MAX) if tpl_cost > 0 else Decimal(1)
        rows_to_scale = [dd for bid in [bom["id"]] + semi_boms_of.get(m["id"], [])
                         for dd in details.get(bid, [])
                         if is_tpl_row(dd) and mats[dd["child"]]["name"] not in FIX]
    clamped = (cur_mat > tgt_mat and s_mat == SCALE_MIN) or \
              (cur_mat <= tgt_mat and s_mat == SCALE_MAX)

    touched = []
    for dd in rows_to_scale:
        if s_mat == 1:
            break
        dd["qty"] = (dd["qty"] * s_mat).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)
        if dd["qty"] <= 0:
            dd["qty"] = Decimal("0.000001")
        touched.append(dd)

    # 工序工时按目标工序成本等比缩放
    rt = routing_by_material.get(m["id"])
    s_rt, tgt_rt = Decimal(1), Decimal(0)
    if rt and rt_steps.get(rt):
        cur_rt = sum((s["h"] * (s["lr"] * s["lc"] + s["mr"] * s["mc"])
                      for s in rt_steps[rt]), Decimal(0))
        tgt_rt = m["price"] * r_rt
        if cur_rt > 0:
            s_rt = tgt_rt / cur_rt
            for s in rt_steps[rt]:
                s["h"] = (s["h"] * s_rt).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
                if s["h"] <= 0:
                    s["h"] = Decimal("0.0001")
    calib.append(dict(m=m, s_mat=s_mat, clamped=clamped, s_rt=s_rt, fixed=fixed,
                      tgt_mat=tgt_mat, tgt_rt=tgt_rt, rows=len(touched), ratio=(r_mat, r_rt)))

# ================================================================ 6. 半成品卷积
pending = [m for m in mats.values() if m["mtype"] == 2 and m["id"] not in mat_sets]
while pending:
    progressed, still = False, []
    for m in pending:
        bom = bom_by_parent.get(m["id"])
        if not bom:
            continue
        if any(dd["child"] not in mat_sets for dd in details[bom["id"]]):
            still.append(m)
            continue
        w = sum((dd["qty"] * mat_sets[dd["child"]]["weight"] for dd in details[bom["id"]]), Decimal(0))
        v = sum((dd["qty"] * mat_sets[dd["child"]]["vol"] for dd in details[bom["id"]]), Decimal(0))
        cost = sum((dd["qty"] * mat_sets[dd["child"]]["cost"] for dd in details[bom["id"]]), Decimal(0))
        set_mat(m["id"], cost=cost, price=(cost * Decimal("1.10")).quantize(Decimal("0.01")),
                weight=w, vol=v)
        progressed = True
    if not progressed:
        raise SystemExit("半成品卷积无法收敛：%s" % [x["name"] for x in still])
    pending = still

# ================================================================ 7. 产成品卷积
fg_rows = []
for c in calib:
    m = c["m"]
    bom = bom_by_parent[m["id"]]
    mat_cost = sum((q * mat_sets[cid]["cost"]
                    for cid, q, _t in leaf_rows(bom["id"], Decimal(1), set())), Decimal(0))
    w = sum((dd["qty"] * mat_sets[dd["child"]]["weight"] for dd in details[bom["id"]]), Decimal(0))
    v = sum((dd["qty"] * mat_sets[dd["child"]]["vol"] for dd in details[bom["id"]]), Decimal(0))
    rt = routing_by_material.get(m["id"])
    rc = sum((s["h"] * (s["lr"] * s["lc"] + s["mr"] * s["mc"]) for s in rt_steps.get(rt, [])),
             Decimal(0)) if rt else Decimal(0)
    total = mat_cost + rc
    set_mat(m["id"], cost=total, price=m["price"], weight=w, vol=v)
    fg_rows.append(dict(name=m["name"], mat=mat_cost, rt=rc, total=total, price=m["price"]))

# ================================================================ 输出 SQL
out.append("-- ---------- 4. 标准成本 / 参考价 / 重量 / 体积 ----------")
for mtype, label in ((1, "原材料"), (2, "半成品"), (3, "产成品")):
    ids = [m["id"] for m in mats.values() if m["mtype"] == mtype and m["id"] in mat_sets]
    if not ids:
        continue
    out.append("-- %s %d 条" % (label, len(ids)))
    for field, fmt, key in (("standard_cost", 4, "cost"), ("reference_price", 4, "price"),
                            ("weight", 4, "weight"), ("volume", 9, "vol")):
        out.append("UPDATE `ry-erp`.bd_material SET %s = CASE material_id" % field)
        for mid in ids:
            out.append("  WHEN %d THEN %s" % (mid, d(mat_sets[mid][key], fmt)))
        out.append("  ELSE %s END WHERE material_id IN (%s);"
                   % (field, ",".join(str(i) for i in ids)))
out.append("")

out.append("-- ---------- 5. 目标成本标定：BOM 用量缩放 ----------")
scaled = [dd for v in details.values() for dd in v
          if abs(dd["qty"] - dd["qty_old"]) > Decimal("0.0000005")]
out.append("-- 共 %d 行用量被缩放" % len(scaled))
cur_bom = None
for dd in sorted(scaled, key=lambda x: (x["bom_id"], x["sort"])):
    if dd["bom_id"] != cur_bom:
        cur_bom = dd["bom_id"]
        out.append("-- BOM %d (%s)" % (cur_bom, mats[boms[cur_bom]["parent"]]["name"]))
    out.append("UPDATE `ry-erp`.bd_bom_detail SET quantity_per_parent = %s WHERE detail_id = %d;"
               % (d(dd["qty"], 6), dd["detail_id"]))
out.append("")

out.append("-- ---------- 6. 目标成本标定：标准工时缩放 ----------")
scaled_steps = [s for v in rt_steps.values() for s in v
                if abs(s["h"] - s["h_old"]) > Decimal("0.00005")]
out.append("-- 共 %d 步工时被缩放" % len(scaled_steps))
out.append("UPDATE `ry-erp`.bd_routing_step SET standard_hours = CASE step_id")
for s in scaled_steps:
    out.append("  WHEN %d THEN %s" % (s["step_id"], d(s["h"], 4)))
out.append("  ELSE standard_hours END WHERE step_id IN (%s);"
           % ",".join(str(s["step_id"]) for s in scaled_steps))
out.append("")

linked = [(b["id"], routing_by_material[b["parent"]]) for b in boms.values()
          if b["parent"] in routing_by_material]
out.append("-- ---------- 7. BOM → 工艺路线关联（工序成本来源，原全为 NULL） ----------")
out.append("-- 关联 %d 张 BOM" % len(linked))
out.append("UPDATE `ry-erp`.bd_bom SET routing_id = CASE bom_id")
for bid, rid in linked:
    out.append("  WHEN %d THEN %d" % (bid, rid))
out.append("  ELSE routing_id END WHERE bom_id IN (%s);" % ",".join(str(b) for b, _ in linked))
out.append("")

# ================================================================ 8. MES MBOM 重挂
out.append("-- ---------- 8. MES MBOM 重新指向产成品 BOM ----------")
out.append("-- 原生成器在生成完各半成品 BOM 后才读全局 bom_id，导致 mes_mbom 37/37 全挂在半成品上。")
out.append("-- 经 routing_id 找回对应产成品，再取其 BOM。")
out.append("UPDATE `ry-mes`.mes_mbom mb")
out.append("JOIN `ry-erp`.bd_routing r ON r.routing_id = mb.routing_id AND r.del_flag='0'")
out.append("JOIN `ry-erp`.bd_bom b ON b.parent_material_id = r.material_id AND b.del_flag='0'")
out.append("SET mb.bom_id = b.bom_id;")
out.append("")

# ================================================================ 汇总
print("新增分类节点    :", len(new_cats))
print("原材料分类变更  :", len(raw_changes))
print("半成品分类变更  :", len(semi_changes))
print("半成品 BOM 重写 : 删 %d 行 / 增 %d 行 / 合并 %d 行"
      % (len(removed_ids), len(new_rows), len(qty_updates)))
print("BOM 用量缩放    :", len(scaled), "行")
print("工时缩放        :", len(scaled_steps), "步")
print("BOM 关联路线    :", len(linked), "/", len(boms))
print()
print("标定前 → 标定后（抽样）：")
print("   %-22s %10s %10s %10s %8s %9s %8s"
      % ("物料", "材料成本", "工序成本", "标准成本", "毛利率", "目标材料", "缩放系数"))
for c, r in zip(calib, fg_rows):
    gm = (r["price"] - r["total"]) / r["price"] * 100
    print("   %-22s %10s %10s %10s %7.1f%% %9s %7.4f%s"
          % (r["name"], d(r["mat"], 2), d(r["rt"], 2), d(r["total"], 2), float(gm),
             d(c["tgt_mat"], 0), float(c["s_mat"]), "  <<夹逼" if c["clamped"] else ""))
gms = sorted(float((r["price"] - r["total"]) / r["price"] * 100) for r in fg_rows)
print()
print("产成品毛利率: 最小 %.1f%% / 中位 %.1f%% / 最大 %.1f%%"
      % (gms[0], gms[len(gms) // 2], gms[-1]))
print("用量缩放系数: 最小 %.4f / 中位 %.4f / 最大 %.4f"
      % (min(float(c["s_mat"]) for c in calib),
         sorted(float(c["s_mat"]) for c in calib)[len(calib) // 2],
         max(float(c["s_mat"]) for c in calib)))

target = Path(sys.argv[1]) if len(sys.argv) > 1 else \
    HERE.parents[2] / "backup-20260914" / "p5_masterdata_backfill.sql"
target.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
print("\nSQL 已写出:", target, "(%d 行)" % len(out))
