# -*- coding: utf-8 -*-
"""按 catalog.py + products.py 生成重建 SQL。

用法:  python generate.py > rebuild.sql
"""
from __future__ import annotations

import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from catalog import (CATEGORIES, CLIENTS, NEW_UOMS, PROCESSES, UOM,
                     VENDORS, WAREHOUSES, WORKSHOPS)
from products import PRODUCTS
import taxonomy
from taxonomy import (CATEGORY_ADD, RAW_TABLE, raw_leaf, raw_cost, semi_leaf,
                      semi_template)

# 分类树 = catalog 里的主干 + taxonomy 里为「外购件与机电」等补充的节点。
# 补充节点必须并入，否则原材料会因为没有合适叶子而落回某个兜底分类。
CATEGORIES = list(CATEGORIES) + [c for c in CATEGORY_ADD
                                 if c[0] not in {x[0] for x in CATEGORIES}]

TENANT = "000000"
NOW = datetime.now()
TODAY = NOW.strftime("%Y%m%d")
STAMP = NOW.strftime("%Y%m%d%H%M%S")
BY, DEPT = 1, 103

def q(v) -> str:
    """SQL 字面量。"""
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"

def row(values) -> str:
    return "(" + ", ".join(q(v) for v in values) + ")"

class Counter:
    """记录每条规则已发到第几号，用于回写 sys_auto_code_result。"""
    def __init__(self):
        self.state: dict[str, tuple[int, str]] = {}

    def issue(self, rule: str, n: int, last: str):
        self.state[rule] = (n, last)

    def bump(self, rule: str, n: int, last: str):
        cur, _ = self.state.get(rule, (0, ""))
        if n > cur:
            self.state[rule] = (n, last)

C = Counter()
out: list[str] = []
def w(s: str = ""):
    out.append(s)

# =====================================================================
w("-- " + "=" * 74)
w("--  Nexus-ERP 生产基础资料重建脚本")
w(f"--  生成时间: {NOW:%Y-%m-%d %H:%M:%S}   目标库: ry-erp / ry-mes / ry-iot")
w("--  设计: 离散型制造业九大类 36 个产成品（已移除航空航天军工）")
w("--  ⚠️ 前置: 必须先执行 p2_cleanup_all.sql 完成清理与计数器重置")
w("-- " + "=" * 74)
w()
w("SET SQL_SAFE_UPDATES = 0;")
w()
w("-- ============ 0. 前置清理（保证脚本可重复执行） ============")
w("-- 注意：wm_warehouse / wm_storage_area / wm_storage_location / wm_material_stock")
w("--       必须排除 3D 示例仓库 9900000001")
w("DELETE FROM `ry-erp`.bd_unit_of_measure       WHERE uom_id > 54;")
for t in ("bd_material_category", "bd_process", "bd_workshop", "bd_workstation",
          "bd_material", "bd_bom_alternative", "bd_bom_detail", "bd_bom", "bd_routing_step", "bd_routing",
          "bd_vendor", "bd_material_vendor", "bd_client", "bd_client_credit",
          "so_price_list_line", "so_price_list", "gl_period", "sch_holiday"):
    w(f"DELETE FROM `ry-erp`.{t};")
w("DELETE FROM `ry-erp`.wm_warehouse        WHERE warehouse_id <> 9900000001;")
w("DELETE FROM `ry-erp`.wm_storage_area     WHERE warehouse_id <> 9900000001;")
w("DELETE FROM `ry-erp`.wm_storage_location WHERE warehouse_id <> 9900000001;")
w("DELETE FROM `ry-erp`.wm_material_stock   WHERE warehouse_id <> 9900000001;")
for t in ("mes_mbom_line", "mes_mbom", "eq_maintenance_plan", "eq_equipment",
          "sop_process_binding", "sop_version", "sop_document",
          "qc_inspect_item", "qc_inspect_template", "mes_test_item",
          "mes_firmware", "ncr_defect_code", "tg_tooling"):
    w(f"DELETE FROM `ry-mes`.{t};")
w("DELETE FROM `ry-iot`.iot_device;")
w("DELETE FROM `ry-iot`.iot_product;")
w("-- 这两张是脚本自建的新表，先 DROP 保证可重复执行")
w("DROP TABLE IF EXISTS `ry-erp`.sch_worker_skill;")
w("DROP TABLE IF EXISTS `ry-erp`.sch_worker_certificate;")
w("-- 清掉本脚本上一轮回写的计数器（避免主键/唯一冲突）")
w("DELETE FROM `ry-vue`.sys_auto_code_result WHERE code_id >= 9000000001;")
w()

# ---------------------------------------------------------------- 1. 计量单位
w("-- ============ 1. 计量单位（新增 14 条，复用原 54 条） ============")
vals = []
for uid, code, name in NEW_UOMS:
    vals.append(row([uid, TENANT, code, name, 1, None, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_unit_of_measure")
w("  (uom_id, tenant_id, uom_code, uom_name, status, remark, create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()

# ---------------------------------------------------------------- 2. 物料分类
w("-- ============ 2. 物料分类（九大类树） ============")
cat_id: dict[str, int] = {}
cat_name: dict[str, str] = {}
for i, (code, name, _parent) in enumerate(CATEGORIES, start=1):
    cat_id[code] = i
    cat_name[code] = name

def path_of(code: str):
    ids, names, cur, lvl = [], [], code, 0
    while cur:
        lvl += 1
        ids.append(str(cat_id[cur]))
        names.append(cat_name[cur])
        parent = next((p for c, _n, p in CATEGORIES if c == cur), None)
        cur = parent
    ids.reverse(); names.reverse()
    return lvl, ",".join(ids), ",".join(names)

vals = []
for i, (code, name, parent) in enumerate(CATEGORIES, start=1):
    lvl, pids, pnames = path_of(code)
    pid = cat_id[parent] if parent else 0
    vals.append(row([i, TENANT, pid, code, name, None, lvl, pids, pnames, i, 1, None,
                     BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_material_category")
w("  (category_id, tenant_id, parent_id, category_code, category_name, category_desc,")
w("   level_num, path_ids, path_names, sort_order, status, remark,")
w("   create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()

# ---------------------------------------------------------------- 3. 工序定义
w("-- ============ 3. 工序定义（%d 条） ============" % len(PROCESSES))
proc_id: dict[str, int] = {}
vals = []
for i, (code, name, cate, hrs, key, qual) in enumerate(PROCESSES, start=1):
    proc_id[code] = i
    vals.append(row([i, TENANT, code, name, None, cate, None, None, None,
                     hrs, 0.1, 2, 1, key, 0, qual, 1, None, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_process")
w("  (process_id, tenant_id, process_code, process_name, process_desc, process_category,")
w("   workstation_id, workstation_code, workstation_name, standard_hours, setup_hours,")
w("   labor_count, machine_count, is_key_process, is_outsource, quality_required,")
w("   status, remark, create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()

# ---------------------------------------------------------------- 4. 车间 / 工位
w("-- ============ 4. 车间（%d）与工位 ============" % len(WORKSHOPS))
shop_id, shop_name_by = {}, {}
vals = []
for i, (code, name, _ws) in enumerate(WORKSHOPS, start=1):
    shop_id[code] = i
    shop_name_by[code] = name
    vals.append(row([i, TENANT, code, name, None, 1, "admin", None, None,
                     1000, 1, i, None, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_workshop")
w("  (workshop_id, tenant_id, workshop_code, workshop_name, workshop_desc, manager_id,")
w("   manager_name, dept_id, dept_name, capacity, status, sort_order, remark,")
w("   create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()

ws_id, ws_full = {}, {}
vals, n = [], 0
for code, _name, stations in WORKSHOPS:
    for suffix, cap in stations:
        n += 1
        wcode = "GZZ%04d" % n
        wname = suffix
        ws_id[(code, suffix)] = n
        ws_full[(code, suffix)] = wcode
        vals.append(row([n, TENANT, wcode, wname, shop_id[code], code, shop_name_by[code],
                         None, None, 2, cap, 1, None, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_workstation")
w("  (workstation_id, tenant_id, workstation_code, workstation_name, workshop_id,")
w("   workshop_code, workshop_name, workstation_desc, equipment_id, operator_count,")
w("   standard_capacity, status, remark, create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()
C.issue("WORKSHOP_CODE", len(WORKSHOPS), "CJ%03d" % len(WORKSHOPS))
C.issue("WORKSTATION_CODE", n, "GZZ%04d" % n)

# 工序 → 车间 映射（用于按工序推导工位）
PROC_CAT_WORKSHOP = {
    "下料": "CJ001", "成形": "CJ003", "机加": "CJ002", "铸造注塑": "CJ006",
    "焊接": "CJ005", "热处理": "CJ008", "表面": "CJ009", "电子": "CJ010",
    "装配": "CJ012", "测试": "CJ013", "通用": "CJ014",
}
proc_cat_of = {c: cate for c, _n, cate, *_ in PROCESSES}
proc_name_of = {c: n_ for c, n_, *_ in PROCESSES}
proc_hours_of = {c: h for c, _n, _cate, h, *_ in PROCESSES}


def route_step(t):
    """route 元素支持 (code, ws) 或 (code, ws, hours)；工时缺省取工序标准工时。"""
    pcode, wskey = t[0], t[1]
    hrs = t[2] if len(t) > 2 else proc_hours_of.get(pcode, 0.30)
    return pcode, wskey, hrs
_ws_by_name = {s: (c, s) for c, _n, sts in WORKSHOPS for s, _cap in sts}
_ws_by_workshop: dict[str, list] = {}
for c, _n, sts in WORKSHOPS:
    for s, _cap in sts:
        _ws_by_workshop.setdefault(c, []).append((c, s))


def resolve_station(pcode: str, wskey):
    """优先用显式指定的工位名；否则按工序类别映射到车间，取该车间第一个工位。"""
    if wskey and wskey in _ws_by_name:
        return _ws_by_name[wskey]
    wcode = PROC_CAT_WORKSHOP.get(proc_cat_of.get(pcode, "通用"), "CJ012")
    cand = _ws_by_workshop.get(wcode) or _ws_by_workshop["CJ012"]
    return cand[0]

# ---------------------------------------------------------------- 5. 仓库/库区/库位
w("-- ============ 5. 仓库 / 库区 / 库位 ============")
wh_id, ar_id, loc_id = {}, {}, {}
vals, whn = [], 0
for code, name, _areas in WAREHOUSES:
    whn += 1
    wh_id[code] = whn
    vals.append(row([whn, TENANT, code, name, "浙江省杭州市余杭区智造园区", 2000.00,
                     "admin", 1, None, 1, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.wm_warehouse")
w("  (warehouse_id, tenant_id, warehouse_code, warehouse_name, location, area,")
w("   manager, manager_id, remark, status, create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()

vals, an = [], 0
for _code, _name, areas in WAREHOUSES:
    for acode, aname, _locs in areas:
        an += 1
        ar_id[acode] = an
        vals.append(row([an, acode, aname, wh_id[_code], 200.000, 1, 1, 0, None,
                         TENANT, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.wm_storage_area")
w("  (area_id, area_code, area_name, warehouse_id, area, location_flag, enable_flag,")
w("   frozen_flag, remark, tenant_id, create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()

vals, ln = [], 0
for wcode, _wname, areas in WAREHOUSES:
    for acode, _aname, locs in areas:
        for lname in locs:
            ln += 1
            vals.append(row([ln, "L%03d" % ln, lname, wh_id[wcode], ar_id[acode], None,
                             6.0, 1000.0, 1, 1, 1, 0, None, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.wm_storage_location")
w("  (location_id, location_code, location_name, warehouse_id, area_id, category_id,")
w("   area, max_loa, product_mixing, batch_mixing, enable_flag, frozen_flag, remark,")
w("   create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()
C.issue("WAREHOUSE_CODE", whn, "WH%03d" % whn)
C.issue("WM_AREA_CODE", an, "A%04d" % an)
C.issue("WM_LOCATION_CODE", ln, "L%03d" % ln)
w(f"-- 仓库 {whn} / 库区 {an} / 库位 {ln}")
w()

# ---------------------------------------------------------------- 6. 供应商 / 客户
w("-- ============ 6. 供应商与客户 ============")
ven_id, vals = {}, []
for i, (name, ind_code, cat, desc) in enumerate(VENDORS, start=1):
    ven_id[name] = i
    # region 固定 2=华东、省市区固定 浙江省/杭州市/余杭区 —— 与上面的硬编码 address 一致
    vals.append(row([i, TENANT, "V%05d" % i, name, None, "浙江省杭州市余杭区",
                     None, "0571-8888%04d" % i, None, None, None, None, "VAT_SPECIAL",
                     ind_code, "2", cat, "ACTIVE", desc,
                     330000, 330100, 330110, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_vendor")
w("  (vendor_id, tenant_id, vendor_code, vendor_name, tax_id, address, contact_person,")
w("   phone, bank_account, invoice_title, invoice_address_phone, invoice_bank_name,")
w("   default_invoice_type, industry, region, category, status, remark,")
w("   province_area_id, city_area_id, district_area_id, create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()
C.issue("VENDOR_CODE", len(VENDORS), "V%05d" % len(VENDORS))

cli_id, credit, vals = {}, [], []
for i, (name, ind, region, credit_amt) in enumerate(CLIENTS, start=1):
    cli_id[name] = i
    vals.append(row([i, TENANT, "C%05d" % i, name, None, None, None, "企业客户",
                     None, f"{region}区产业大道 {i} 号", None, None,
                     "0571-6666%04d" % i, f"联系人{i}", "1380000%04d" % i, None,
                     None, None, None, None, None, None, None, 1,
                     None, BY, DEPT, "0"]))
    credit.append(row([i, TENANT, i, "C%05d" % i, name, None, credit_amt, "30",
                       "A" if credit_amt >= 700000 else "B", 90, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_client")
w("  (client_id, tenant_id, client_code, client_name, client_nick, client_en, client_desc,")
w("   client_type, client_logo, address, web_site, email, tel, contact1, contact1_tel,")
w("   contact1_emal, contact2, contact2_tel, contact2_emal, credit_code, tax_id,")
w("   invoice_title, invoice_bank_name, default_invoice_type, remark,")
w("   create_by, create_dept, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()
w("INSERT INTO `ry-erp`.bd_client_credit")
w("  (credit_id, tenant_id, client_id, client_code, client_name, client_en, client_credit,")
w("   client_time, client_level, client_score, create_by, create_dept, del_flag) VALUES")
w(",\n".join(credit) + ";")
w()
C.issue("CLIENT_CODE", len(CLIENTS), "C%05d" % len(CLIENTS))

# ---------------------------------------------------------------- 7. 物料

# ---------------------------------------------------------------- 7. 物料
w("-- ============ 7. 物料（产成品 → 半成品 → 原材料 三层，全部挂最低层叶子分类） ============")

mat_id: dict[str, int] = {}
mat_meta: dict[str, dict] = {}


def add_material(name, spec, cat_code, mtype, unit, price, cost, desc=None,
                 batch=0, serial=0, weight=0.0, volume=0.0):
    if name in mat_id:
        return mat_id[name]
    # 原材料分类与单价/单重/单体积统一取自 taxonomy.RAW_TABLE（采购数据表）。
    # 原先就地传 0.0，导致所有叶子的 standard_cost=0，BOM 成本卷积结果恒为 0。
    if mtype == 1 and name in RAW_TABLE:
        cat_code, _c, _w, _v = RAW_TABLE[name]
        cost, weight, volume = float(_c), float(_w), float(_v)
        price = round(cost * 1.08, 2)
    mid = len(mat_id) + 1
    mat_id[name] = mid
    mat_meta[name] = dict(id=mid, code="IF%s%03d" % (TODAY, mid), name=name, spec=spec,
                          cat=cat_code, mtype=mtype, unit=unit, price=price, cost=cost,
                          desc=desc, batch=batch, serial=serial, weight=weight, volume=volume)
    return mid


# raw_leaf / semi_leaf 已改由 taxonomy 提供：显式全量映射 + 未命中即报错。
# 原先的本地关键字规则兜底是 `return "CH_LUBE"`，会把伺服电机、谐波减速器、
# 单晶硅电池片这类匹配不上的物料统统塞进「润滑剂」（实测 45 条）。
def _fmt_leaf(name):
    return raw_leaf(name) + " / " + semi_leaf(name)

# 7.1 产成品
for p in PRODUCTS:
    add_material(p["name"], p["spec"], p["cat"], 3, p["unit"], p["price"], p["cost"],
                 p.get("desc"), batch=0, serial=1, weight=0.0)

# 7.2 半成品（挂叶子分类）
for p in PRODUCTS:
    for sname, sspec, sunit, _q in p["semi"]:
        add_material(sname, sspec, semi_leaf(sname), 2, sunit, 0.0, 0.0, batch=1)

# 7.3 原材料（挂叶子分类）
for p in PRODUCTS:
    for rname, rspec, runit, _q in p["raw"]:
        add_material(rname, rspec, raw_leaf(rname), 1, runit, 0.0, 0.0, batch=1)

# 7.4 替代料（与主料同规格等级的备选物料，供 bd_bom_alternative 引用）
SUBSTITUTES = [
    ("合金钢棒料 40Cr", "合金钢棒料 42CrMo", "Φ20mm", "千克", "FULL"),
    ("合金钢棒料 40Cr", "合金钢棒料 40CrNiMo", "Φ20mm", "千克", "PARTIAL"),
    ("冷轧钢板 SPCC", "冷轧钢板 DC01", "1.5mm", "千克", "FULL"),
    ("冷轧钢板 SPCC", "冷轧钢板 DC03", "1.5mm", "千克", "INTERCHANGEABLE"),
    ("冷轧钢板 DC01", "冷轧钢板 DC03", "1.2mm", "千克", "FULL"),
    ("热轧型钢 Q235", "热轧型钢 Q345", "50×50×5 角钢", "千克", "FULL"),
    ("铸铁 HT250", "铸铁 HT300", "HT250", "千克", "PARTIAL"),
    ("钢管 Q235", "钢管 Q345", "Φ22×1.5mm", "千克", "FULL"),
    ("ABS 工程塑料", "PC/ABS 合金", "注塑级", "千克", "PARTIAL"),
    ("PC 工程塑料", "PC/ABS 合金", "透明 阻燃 V0", "千克", "INTERCHANGEABLE"),
    ("POM 工程塑料", "PA66 工程塑料", "POM 注塑级", "千克", "PARTIAL"),
    ("食品级硅胶混炼胶", "医用级硅胶混炼胶", "硬度 50 Shore A", "千克", "FULL"),
    ("主控芯片 MC32F006", "主控芯片 GD32F103", "SOP-16 32位MCU", "颗", "FULL"),
    ("主控芯片 MC32F006", "主控芯片 STM32F103", "SOP-16 32位MCU", "颗", "INTERCHANGEABLE"),
    ("蓝牙模块 BLE50", "蓝牙模块 BLE52", "BLE 5.0 贴片模块", "颗", "FULL"),
    ("充电管理IC TP4056", "充电管理IC TP4057", "SOT-23-5 线性充电", "颗", "FULL"),
    ("马达驱动IC HR8833", "马达驱动IC DRV8833", "SOP-8 H桥驱动", "颗", "FULL"),
    ("MOS 管 IRF840", "MOS 管 IRF840N", "IRF840", "颗", "FULL"),
    ("电解电容 470μF/25V", "电解电容 470μF/35V", "470μF/25V", "颗", "INTERCHANGEABLE"),
    ("钕铁硼磁钢", "钕铁硼磁钢 N38", "N35 弧形", "颗", "PARTIAL"),
    ("轴承 608", "轴承 608-2RS", "GB/T276 608", "个", "FULL"),
    ("轴承 6308", "轴承 6308-2RS", "GB/T276 6308", "个", "FULL"),
    ("轴承 6212", "轴承 6212-2RS", "GB/T276 6212", "个", "FULL"),
    ("磷酸铁锂电芯 32700", "磷酸铁锂电芯 32700A", "3.2V 6Ah", "颗", "FULL"),
    ("锂聚合物电池", "锂聚合物电池 高容量版", "3.7V 180mAh", "个", "PARTIAL"),
    ("静电粉末涂料", "静电粉末涂料 户外级", "RAL7035", "千克", "INTERCHANGEABLE"),
    ("润滑油 ISO VG46", "润滑油 ISO VG68", "VG46", "升", "PARTIAL"),
    ("硅钢片 50W470", "硅钢片 50W600", "0.5mm", "千克", "PARTIAL"),
    ("漆包线 QZ-2", "漆包线 QZ-2/155", "Φ0.35mm", "千克", "FULL"),
    ("环氧树脂", "环氧树脂 中温快固", "中温固化", "千克", "FULL"),
    ("单晶硅电池片", "单晶硅电池片 A 级", "182mm 半片", "片", "PARTIAL"),
    ("包装纸盒 小号", "包装纸盒 小号 加强", "150×100×60mm", "个", "INTERCHANGEABLE"),
]
SUB_ALT_ONLY = {alt for _m, alt, _sp, _u, _t in SUBSTITUTES}
for _main, alt, sp, u, _t in SUBSTITUTES:
    add_material(alt, sp, raw_leaf(alt), 1, u, 0.0, 0.0, batch=1)

vals = []
for name, m in mat_meta.items():
    u = UOM.get(m["unit"], 26)
    vals.append(row([m["id"], TENANT, m["code"], m["name"], m["spec"], m["desc"],
                     cat_id[m["cat"]], u, u, u, u, m["mtype"], "PROCESS",
                     m["batch"], m["serial"], 0, 0, 0, m["cost"], m["price"],
                     1, None, 0, m["weight"], m["volume"], None,
                     BY, DEPT, "0", 0, 1 if m["mtype"] == 3 else 0]))
w("-- 物料共 %d 条（产成品 %d / 半成品 %d / 原材料含替代料 %d）"
  % (len(mat_meta), len(PRODUCTS),
     sum(len(p["semi"]) for p in PRODUCTS),
     len(mat_meta) - len(PRODUCTS) - sum(len(p["semi"]) for p in PRODUCTS)))
# ---------------------------------------------------------------- 8. BOM + 工艺路线
w("-- ============ 8. BOM 与工艺路线 ============")
bom_vals, det_vals, rt_vals, step_vals = [], [], [], []
# 产成品 BOM → 工艺路线。工艺路线在 BOM 之后生成，故先登记、最后统一回写
# bd_bom.routing_id；缺这个关联时 costRollup 取不到工序，工序成本恒为 0。
bom_routing: dict[int, int] = {}
bom_id, rt_id, det_id, step_id = 0, 0, 0, 0
mbom_vals, mbom_line_vals = [], []
mbom_id, mbom_line_id = 0, 0

# 半成品 BOM 的原材料模板已迁到 taxonomy（与分类修正、回填脚本共用同一份定义）
SEMI_RAW_TEMPLATE = taxonomy.SEMI_RAW_TEMPLATE
# 在产成品层级直接消耗的物料（包装/辅料），不往下沉到半成品
FG_LEVEL_KW = ("纸盒", "彩盒", "纸箱", "塑盒", "包装", "说明书", "标签",
               "润滑油", "涂料", "粉末", "漆", "胶", "溶剂", "氧化剂",
               "焊丝", "密封", "紧固件包", "连接件包")

alt_vals = []          # 物料清单替代料表 bd_bom_alternative。
alt_id = 0
# 主料 → [(替代料, 替代类型)]
SUB_MAP = {}
for _m, _a, _sp, _u, _t in SUBSTITUTES:
    SUB_MAP.setdefault(_m, []).append((_a, _t))

def emit_bom(parent_name, children, bom_name, src_default="PROCESS"):
    """生成一条 BOM 及其明细，返回 bom_id。children: [(物料名, 用量)]"""
    global bom_id, det_id, alt_id
    bom_id += 1
    bid = bom_id
    bom_vals.append(row([bid, TENANT, mat_id[parent_name], "1.0", bom_name,
                         src_default, "MBOM", None, NOW.strftime("%Y-%m-%d"), None,
                         1, 1, None, BY, DEPT, "0"]))
    for order, (cname, qty) in enumerate(children, start=1):
        det_id += 1
        did = det_id
        cmeta = mat_meta[cname]
        src = "PROCESS" if cmeta["mtype"] in (2, 3) else "PROCUREMENT"
        det_vals.append(row([did, TENANT, bid, mat_id[cname], qty, src, 0.0,
                             order, 0, None, None, order, 1, None,
                             BY, DEPT, "0", "SELF_PROVIDED"]))
        # 替代料
        for alt_name, alt_type in SUB_MAP.get(cname, []):
            alt_id += 1
            alt_vals.append(row([alt_id, TENANT, did, bid, mat_id[alt_name],
                                 mat_meta[alt_name]["code"], alt_name,
                                 qty, len(alt_vals) % 3 + 1, alt_type,
                                 None, None, 1, "可替代主料", BY, DEPT, NOW,
                                 None, NOW, "0"]))
    return bid

for p in PRODUCTS:
    pid = mat_id[p["name"]]
    semis = [(n, q) for (n, _sp, _u, q) in p["semi"]]
    raws = [(n, q) for (n, _sp, _u, q) in p["raw"]]

    # 拆分：产成品层级直耗 vs 下沉到半成品。
    # 高值外购总成必须留在产成品层——它们是总装时才装上去的，下沉到半成品会挂错位置。
    comp_names = {t[0] for t in taxonomy.COMPONENT_BOM.get(p["name"], [])}
    fg_raws = [r for r in raws if any(k in r[0] for k in FG_LEVEL_KW) or r[0] in comp_names]
    down_raws = [r for r in raws if r not in fg_raws]

    # 下沉料按顺序摊到各半成品
    bomed = [n for n, _q in semis
             if not any(k in n for k in ("固件", "FW-", "软件", "配置包"))]
    sink = {n: [] for n, _q in semis}
    if bomed:
        for idx, r in enumerate(down_raws):
            sink[bomed[idx % len(bomed)]].append(r)

    # ---- 一级：产成品 BOM = 半成品 + 产成品层级直耗料 ----
    # 必须在这里接住产出 ID：后面还要生成各半成品的 BOM，全局 bom_id 会一路增长，
    # 再读 bom_id 拿到的是最后一个半成品的 BOM（MES MBOM 曾因此 37/37 全挂错）。
    fg_children = list(semis) + fg_raws
    fg_bom = emit_bom(p["name"], fg_children, p["name"] + "标准版BOM")

    # ---- 二级：半成品 BOM = 模板原材料 + 摊入的产品原材料 ----
    for sname, _q in semis:
        # 固件/软件/配置类不是物理件，不生成 BOM
        if any(k in sname for k in ("固件", "FW-", "软件", "配置包")):
            continue
        tmpl = semi_template(sname)
        ch = [(t[0], t[3]) for t in tmpl] + sink.get(sname, [])
        for t in tmpl:
            add_material(t[0], t[1], raw_leaf(t[0]), 1, t[2], 0.0, 0.0, batch=1)
        emit_bom(sname, ch, sname + "组件BOM")

    # ---- 工艺路线（不变） ----
    rt_id += 1
    rcode = "RT%s%03d" % (TODAY, rt_id)
    total_h = sum(route_step(t)[2] for t in p["route"])
    rt_vals.append(row([rt_id, TENANT, rcode, p["name"] + "装配工艺路线V1.0", None,
                        pid, mat_meta[p["name"]]["code"], p["name"], "1.0", 1,
                        len(p["route"]), total_h, NOW.strftime("%Y-%m-%d"), None, 1, None,
                        BY, DEPT, "0"]))
    for seq, t in enumerate(p["route"], start=1):
        step_id += 1
        pcode, wskey, hrs = route_step(t)
        wk = resolve_station(pcode, wskey)
        wsx, wsy = wk
        step_vals.append(row([step_id, TENANT, rt_id, seq * 10, proc_id[pcode], pcode,
                              proc_name_of[pcode], pcode,
                              ws_id[wk], ws_full[wk], wsy, hrs, 0.1, 2, 1,
                              50.0, 100.0, 1 if pcode in ("FUNC_TEST", "QC", "WP_TEST",
                                                          "PRECISION", "ICT", "WATER_PROOF") else 0,
                              None, None, None, 0,
                              1 if pcode in ("FUNC_TEST", "QC", "WP_TEST") else 0,
                              0.1, 0.05, None, None, BY, DEPT, NOW]))
    # ---- MES MBOM（只挂产成品层，避免与半成品 MBOM 重复） ----
    fg_bom_id = fg_bom
    bom_routing[fg_bom_id] = rt_id
    mbom_id += 1
    mbom_vals.append(row([mbom_id, TENANT, fg_bom_id, rt_id, "1.0", "ACTIVE",
                          p["name"] + "制造BOM（关联ERP BOM#%d）" % fg_bom_id, BY, DEPT, NOW]))
    for order, (cname, qty) in enumerate(fg_children, start=1):
        mbom_line_id += 1
        mbom_line_vals.append(row([mbom_line_id, TENANT, mbom_id, fg_bom_id, None,
                                   mat_id[cname], qty, 0, None, None, None,
                                   None, 0, order, None, BY, DEPT, NOW]))

# ---- 物料 INSERT：放在此处以保证半成品模板原材料 / 替代料也已登记 ----
mat_vals = []
for _name, m in mat_meta.items():
    u = UOM.get(m["unit"], 26)
    mat_vals.append(row([m["id"], TENANT, m["code"], m["name"], m["spec"], m["desc"],
                         cat_id[m["cat"]], u, u, u, u, m["type"] if "type" in m else m["mtype"],
                         "PROCESS", m["batch"], m["serial"], 0, 0, 0, m["cost"], m["price"],
                         1, None, 0, m["weight"], m["volume"], None,
                         BY, DEPT, "0", 0, 1 if m["mtype"] == 3 else 0]))
w("-- ============ 7.5 物料落库（%d 条：产成品 %d / 半成品 %d / 原材料含替代料 %d） ============"
  % (len(mat_meta), len(PRODUCTS),
     sum(len(_p["semi"]) for _p in PRODUCTS),
     len(mat_meta) - len(PRODUCTS) - sum(len(_p["semi"]) for _p in PRODUCTS)))
w("INSERT INTO `ry-erp`.bd_material")
w("  (material_id, tenant_id, material_code, material_name, material_spec, material_desc,")
w("   category_id, base_uom_id, purchase_uom_id, inventory_uom_id, sale_uom_id,")
w("   material_type, source_type, is_batch_manage, is_serial_manage, safety_stock,")
w("   min_stock, max_stock, standard_cost, reference_price, status, quality_status,")
w("   shelf_life, weight, volume, remark, create_by, create_dept, del_flag,")
w("   is_phantom, inspection_required) VALUES")
w(",\n".join(mat_vals) + ";")
w()

w("INSERT INTO `ry-erp`.bd_bom")
w("  (bom_id, tenant_id, parent_material_id, bom_version, bom_name, source_type, bom_type,")
w("   routing_id, effective_date, expiry_date, is_current, status, remark,")
w("   create_by, create_dept, del_flag) VALUES")
w(",\n".join(bom_vals) + ";")
w()
if bom_routing:
    w("-- 产成品 BOM 关联工艺路线（%d 张）——costRollup 的工序成本来源" % len(bom_routing))
    w("UPDATE `ry-erp`.bd_bom SET routing_id = CASE bom_id")
    for _bid, _rid in sorted(bom_routing.items()):
        w("  WHEN %d THEN %d" % (_bid, _rid))
    w("  ELSE routing_id END WHERE bom_id IN (%s);"
      % ",".join(str(b) for b in sorted(bom_routing)))
    w()
w("INSERT INTO `ry-erp`.bd_bom_detail")
w("  (detail_id, tenant_id, bom_id, child_material_id, quantity_per_parent, source_type,")
w("   loss_rate, operation_step, lead_time, effective_date, expiry_date, sort_order,")
w("   status, remark, create_by, create_dept, del_flag, supply_type) VALUES")
w(",\n".join(det_vals) + ";")
w()
w("-- ============ 8.1 BOM 替代料（bd_bom_alternative） ============")
w("-- 共 %d 条，覆盖 %d 种主料的替代关系" % (len(alt_vals), len(SUB_MAP)))
w("INSERT INTO `ry-erp`.bd_bom_alternative")
w("  (alternative_id, tenant_id, detail_id, bom_id, alt_material_id, alt_material_code,")
w("   alt_material_name, alt_quantity, priority, substitution_type, effective_date,")
w("   expiry_date, status, remark, create_dept, create_by, create_time,")
w("   update_by, update_time, del_flag) VALUES")
w(",\n".join(alt_vals) + ";")
w()

w("-- ============ 8.2 物料-供应商关系（bd_material_vendor） ============")
# 按原材料叶子分类匹配供应商行业
VENDOR_BY_CAT = {
    "METAL_PLATE": 0, "METAL_PROFILE": 0, "METAL_TUBE": 0, "METAL_BAR": 0, "METAL_WIRE": 0,
    "HW_SCREW": 1, "HW_NUT": 1, "HW_WASHER": 1, "HW_PIN": 1,
    "PL_ABS": 2, "PL_PC": 2, "PL_POM": 2, "PL_PA": 2, "PL_PP": 2,
    "EL_CHIP": 3, "EL_PASSIVE": 3, "EL_PCB": 3, "EL_CONN": 4,
    "BT_CELL": 5, "BT_PACK": 5,
    "CH_PAINT": 7, "CH_SOLVENT": 7, "CH_GLUE": 7, "CH_LUBE": 7,
    "RB_SILICONE": 8, "RB_CFRP": 8,
    "PK_BOX": 9, "PK_FOAM": 9,
}
mv_vals, mv_id = [], 0
vendor_list = [(i + 1, v[0]) for i, v in enumerate(VENDORS)]
for name, m in mat_meta.items():
    if m["mtype"] != 1:          # 只给原材料建供应关系
        continue
    vidx = VENDOR_BY_CAT.get(m["cat"], 0)
    for pri, vid in enumerate([vendor_list[vidx][0],
                               vendor_list[(vidx + 1) % len(vendor_list)][0]], start=1):
        mv_id += 1
        vcode = "V%05d" % vid
        vname = dict(vendor_list)[vid]
        mv_vals.append(row([mv_id, TENANT, m["id"], vid, vcode, vname,
                            7 + (m["id"] % 14), 10 + (m["id"] % 90) * 10,
                            round(3.5 + (m["id"] % 60) * 0.85, 2), "CNY",
                            1 if pri == 1 else 0, pri,
                            None, None, 1, "主供" if pri == 1 else "备供",
                            BY, DEPT, "0"]))
w("-- 共 %d 条（每个原材料 1 主供 + 1 备供）" % len(mv_vals))
w("INSERT INTO `ry-erp`.bd_material_vendor")
w("  (relation_id, tenant_id, material_id, vendor_id, vendor_code, vendor_name,")
w("   lead_time, min_order_qty, price, currency, is_default, priority,")
w("   effective_date, expiry_date, status, remark,")
w("   create_by, create_dept, del_flag) VALUES")
w(",\n".join(mv_vals) + ";")
w()

w("INSERT INTO `ry-erp`.bd_routing")
w("  (routing_id, tenant_id, routing_code, routing_name, routing_desc, material_id,")
w("   material_code, material_name, routing_version, is_current, total_steps, total_hours,")
w("   effective_date, expiry_date, status, remark, create_by, create_dept, del_flag) VALUES")
w(",\n".join(rt_vals) + ";")
w()
w("INSERT INTO `ry-erp`.bd_routing_step")
w("  (step_id, tenant_id, routing_id, sequence_num, process_id, process_code, process_name,")
w("   step_name, workstation_id, workstation_code, workstation_name, standard_hours,")
w("   setup_hours, labor_count, machine_count, labor_rate, machine_rate, is_key_process,")
w("   firmware_id, firmware_code, firmware_name, is_outsource, quality_required,")
w("   wait_time, transfer_time, description, remark, create_by, create_dept, create_time) VALUES")
w(",\n".join(step_vals) + ";")
w()
C.issue("ROUTING_CODE", rt_id, "RT%s%03d" % (TODAY, rt_id))
w(f"-- BOM {bom_id} / 明细 {det_id} / 工艺路线 {rt_id} / 工序步骤 {step_id}")
w()

w("INSERT INTO `ry-mes`.mes_mbom")
w("  (mbom_id, tenant_id, bom_id, routing_id, mbom_version, mbom_status, remark,")
w("   create_dept, create_by, create_time) VALUES")
w(",\n".join(mbom_vals) + ";")
w()
w("INSERT INTO `ry-mes`.mes_mbom_line")
w("  (mbom_line_id, tenant_id, mbom_id, bom_id, bom_detail_id, child_material_id,")
w("   quantity_per_parent, loss_rate, routing_step_id, process_id, workstation_id,")
w("   operation_content, backflush_flag, sort_order, remark, create_dept, create_by, create_time) VALUES")
w(",\n".join(mbom_line_vals) + ";")
w()
w(f"-- MES MBOM {mbom_id} / 明细 {mbom_line_id}")
w()

# ---------------------------------------------------------------- 9. 设备（MES）
w("-- ============ 9. 设备台账（MES） ============")
eq_by_ws: dict[int, int] = {}
eq_vals, eqn = [], 0
EQ_TYPE = {"激光切割机": "LASER", "等离子切割机": "PLASMA", "剪板机": "SHEAR", "锯床": "SAW",
           "数控车床A": "CNC", "数控车床B": "CNC", "加工中心A": "CNC", "铣床": "MILL",
           "磨床": "GRIND", "钻床": "DRILL", "冲床A": "PUNCH", "冲床B": "PUNCH",
           "折弯机": "BEND", "油压机": "PRESS", "卷圆工位": "ROLL", "冲孔工位": "PUNCH",
           "氩弧焊工位": "WELDING", "二保焊工位": "WELDING", "点焊工位": "WELDING",
           "打磨工位": "GRIND", "注塑机A": "INJECT", "注塑机B": "INJECT", "注塑机C": "INJECT",
           "去毛刺工位": "DEBURR", "熔炼炉": "FURNACE", "压铸机": "DIE_CAST",
           "清砂工位": "CLEAN", "淬火炉": "FURNACE", "回火炉": "FURNACE", "渗碳炉": "FURNACE",
           "电镀线": "PLATING", "阳极氧化线": "ANODIZE", "喷塑线": "COATING", "喷漆房": "COATING",
           "SMT贴片线": "SMT", "插件线": "INSERT", "波峰焊": "WAVE", "手工焊工位": "SOLDERING",
           "绕线机": "WINDING", "浸漆工位": "VARNISH", "动平衡机": "BALANCE",
           "装配线A": "ASSEMBLY", "装配线B": "ASSEMBLY", "调试工位": "DEBUG",
           "功能测试工位": "TEST", "老化房": "AGING", "气密测试台": "TEST",
           "精度检验台": "INSPECTION", "包装线A": "PACKAGING", "包装线B": "PACKAGING",
           "打包工位": "PACKAGING", "电火花机": "EDM", "线切割机": "WEDM", "抛光工位": "POLISH"}
for wcode, (_sname, stations) in [(c, (n_, s)) for c, n_, s in
                                   [(c, n_, s) for c, n_, s in WORKSHOPS]]:
    for suffix, _cap in stations:
        eqn += 1
        eq_by_ws[ws_id[(wcode, suffix)]] = eqn
        eq_vals.append(row([eqn, TENANT, "EQ%04d" % eqn, suffix,
                            EQ_TYPE.get(suffix, "GENERAL"), "MODEL-%s" % EQ_TYPE.get(suffix, "GEN"),
                            "浙江智能装备制造有限公司", "SN%s%04d" % (TODAY, eqn),
                            ws_id[(wcode, suffix)], ws_full[(wcode, suffix)],
                            shop_name_by[wcode], None, None, "NORMAL", None, None, None,
                            "由工位台账自动生成", BY, DEPT, NOW]))
w(f"-- 设备 {eqn} 台（与工位 1:1 对应）")
w("INSERT INTO `ry-mes`.eq_equipment")
w("  (equipment_id, tenant_id, equipment_code, equipment_name, equipment_type, model,")
w("   manufacturer, serial_no, workcenter_id, workcenter_code, location, purchase_date,")
w("   warranty_date, status, inspection_items, last_inspection, iot_device_id, remark,")
w("   create_by, create_dept, create_time) VALUES")
w(",\n".join(eq_vals) + ";")
w()

# ---------------------------------------------------------------- 10. IoT 产品与设备
w("-- ============ 10. IoT 产品模板与设备实例 ============")
IOT_PRODS = [("TEMP-SENSOR", "温度传感器模组", "SENSOR", "MQTT"),
             ("VIB-SENSOR", "振动传感器模组", "SENSOR", "MQTT"),
             ("POWER-METER", "电能表模组", "METER", "MQTT"),
             ("SPEED-SENSOR", "转速传感器模组", "SENSOR", "MQTT"),
             ("VISION-CAM", "工业视觉相机", "CAMERA", "HTTP")]
vals = []
for i, (code, name, ptype, proto) in enumerate(IOT_PRODS, start=1):
    vals.append(row([i, TENANT, code, name, ptype, proto, name + " 数据采集模板",
                     1, None, BY, DEPT, NOW, "0"]))
w("INSERT INTO `ry-iot`.iot_product")
w("  (product_id, tenant_id, product_code, product_name, product_type, protocol_type,")
w("   description, status, remark, create_by, create_dept, create_time, del_flag) VALUES")
w(",\n".join(vals) + ";")
w()

IOT_MAP = {"CNC": 4, "MILL": 4, "GRIND": 2, "FURNACE": 1, "INJECT": 1,
           "ASSEMBLY": 5, "TEST": 3, "WELDING": 1, "SMT": 3, "PACKAGING": 3}
dev_vals, devn, dev_first = [], 0, None
for wsx, eqn in sorted(eq_by_ws.items()):
    eqname = [s for (c, s) in ws_id if ws_id[(c, s)] == wsx][0]
    etype = EQ_TYPE.get(eqname, "GENERAL")
    pid = IOT_MAP.get(etype)
    if not pid or devn >= 60:
        continue
    devn += 1
    if dev_first is None:
        dev_first = devn
    dev_vals.append(row([devn, TENANT, "DEV%04d" % devn, "%s-采集点" % eqname, pid,
                         IOT_PRODS[pid - 1][1], "MQTT", "OFFLINE", None,
                         "SEC%s%04d" % (TODAY, devn), "192.168.10.%d" % (100 + devn % 150),
                         "V1.0.0", shop_name_by.get([c for (c, s) in ws_id if ws_id[(c, s)] == wsx][0], ""),
                         eqn, "ACTIVE", None, BY, DEPT, NOW, "0"]))
w(f"-- IoT 设备 {devn} 台（挂在对应设备上，含 mes_equipment_id 反向关联）")
w("INSERT INTO `ry-iot`.iot_device")
w("  (device_id, tenant_id, device_code, device_name, product_id, product_name,")
w("   protocol_type, connection_status, last_online_time, device_secret, ip_address,")
w("   firmware_version, location, mes_equipment_id, status, remark,")
w("   create_by, create_dept, create_time, del_flag) VALUES")
w(",\n".join(dev_vals) + ";")
w()
# 回填 eq_equipment.iot_device_id
w("-- 回填设备与 IoT 设备的一对一关联")
for wsx, eqn in sorted(eq_by_ws.items()):
    eqname = [s for (c, s) in ws_id if ws_id[(c, s)] == wsx][0]
    pid = IOT_MAP.get(EQ_TYPE.get(eqname, "GENERAL"))
    if not pid:
        continue
    dev = next((int(v.split(",")[0].strip("(")) for v in dev_vals
                if "'%s-采集点'" % eqname in v), None)
    if dev:
        w("UPDATE `ry-mes`.eq_equipment SET iot_device_id=%d WHERE equipment_id=%d;" % (dev, eqn))
w()

# 第十一部分：标准作业程序。
from sop_types import sop_type_for_process

# 未附真实文件的初始化种子不能声称已审批或发布。
w("-- ============ 11. SOP 文档与工序绑定 ============")
sop_vals, ver_vals, bind_vals = [], [], []
sopn, vern, bindn = 0, 0, 0
for pcode, pname, pcate, *_rest in PROCESSES:
    sopn += 1
    sop_vals.append(row([sopn, TENANT, "SOP%s%03d" % (TODAY, sopn),
                         pname + "作业指导书", sop_type_for_process(pcode), None, "DRAFT",
                         None, None, pname + "标准作业流程与关键控制点",
                         None, None, None, None, None, BY, DEPT, NOW]))
    vern += 1
    ver_vals.append(row([vern, TENANT, sopn, "V1.0", 0, None,
                         "初始化草稿占位，需上传真实文件后提交审批", None, None,
                         "PENDING", None, BY, DEPT, NOW]))
    bindn += 1
    bind_vals.append(row([bindn, TENANT, proc_id[pcode], pcode, pname, pcate,
                          sopn, 1, 1, bindn, None, BY, DEPT, NOW]))
w(f"-- SOP {sopn} 份 / 版本 {vern} / 工序绑定 {bindn}（覆盖全部工序）")
w("INSERT INTO `ry-mes`.sop_document")
w("  (sop_id, tenant_id, sop_code, sop_name, sop_type, current_version, status,")
w("   file_oss_id, file_type, description, approval_by, approval_time, approval_remark,")
w("   archive_reason, remark, create_by, create_dept, create_time) VALUES")
w(",\n".join(sop_vals) + ";")
w()
w("INSERT INTO `ry-mes`.sop_version")
w("  (version_id, tenant_id, sop_id, version_no, file_oss_id, file_type, change_log,")
w("   published_time, published_by, status, remark, create_by, create_dept, create_time) VALUES")
w(",\n".join(ver_vals) + ";")
w()
w("INSERT INTO `ry-mes`.sop_process_binding")
w("  (binding_id, tenant_id, process_id, process_code, process_name, process_category,")
w("   sop_id, version_no, is_default, sort_order, remark, create_by, create_dept, create_time) VALUES")
w(",\n".join(bind_vals) + ";")
w()
C.issue("MES_SOP_CODE", sopn, "SOP%s%03d" % (TODAY, sopn))

# ---------------------------------------------------------------- 12. 缺陷代码库
w("-- ============ 12. 缺陷代码库（NCR） ============")
DEFECTS = [
    ("尺寸超差", [("外径超上差", 1), ("外径超下差", 1), ("长度超差", 1), ("同轴度超差", 1),
                  ("平面度超差", 1), ("孔位偏移", 1)]),
    ("外观缺陷", [("划痕", 2), ("碰伤", 2), ("凹坑", 2), ("毛刺未清", 2), ("锈蚀", 2),
                  ("色差", 2), ("流痕", 2), ("缩水", 2)]),
    ("材料缺陷", [("材质不符", 3), ("硬度不足", 3), ("夹杂", 3), ("气孔", 3), ("裂纹", 3)]),
    ("焊接缺陷", [("未焊透", 4), ("咬边", 4), ("气孔", 4), ("焊瘤", 4), ("焊缝偏移", 4)]),
    ("电镀涂装缺陷", [("镀层起泡", 5), ("镀层脱落", 5), ("镀层厚度不足", 5), ("喷涂流挂", 5),
                      ("喷涂漏底", 5), ("附着力不足", 5)]),
    ("注塑缺陷", [("欠注", 6), ("飞边", 6), ("熔接痕", 6), ("银纹", 6), ("变形", 6)]),
    ("电子功能缺陷", [("不开机", 7), ("功能失效", 7), ("通信异常", 7), ("参数漂移", 7),
                      ("短路", 7), ("虚焊", 7), ("固件版本错误", 7)]),
    ("装配缺陷", [("漏装", 8), ("错装", 8), ("装配不到位", 8), ("扭矩不足", 8), ("间隙超差", 8)]),
    ("包装标识缺陷", [("标签错误", 9), ("包装破损", 9), ("缺少附件", 9), ("说明书缺失", 9),
                      ("条码无法识别", 9)]),
]
dc_vals, dcn = [], 0
for i, (parent, subs) in enumerate(DEFECTS, start=1):
    dcn += 1
    root = dcn
    dc_vals.append(row([dcn, TENANT, 0, "DC%02d" % i, parent, 1, 1, i, None, BY, DEPT, NOW]))
    for j, (sname, _lvl) in enumerate(subs, start=1):
        dcn += 1
        dc_vals.append(row([dcn, TENANT, root, "DC%02d%02d" % (i, j), sname, 2, 1,
                            i * 100 + j, None, BY, DEPT, NOW]))
w(f"-- 缺陷代码 {dcn} 条（{len(DEFECTS)} 个大类）")
w("INSERT INTO `ry-mes`.ncr_defect_code")
w("  (code_id, tenant_id, parent_id, code, name, level, enabled, sort_order, remark,")
w("   create_by, create_dept, create_time) VALUES")
w(",\n".join(dc_vals) + ";")
w()

# ---------------------------------------------------------------- 13. 质检模板 / 检验项 / 测试项
w("-- ============ 13. 质检模板、检验项、测试项 ============")
tpl_vals, item_vals = [], []
tpln, itemn = 0, 0
GEN_ITEMS = [("外观", "VISUAL", None, None, None),
             ("尺寸", "DIMENSION", "mm", 0.02, -0.02),
             ("硬度", "NUMBER", "HRC", 62.0, 55.0),
             ("功能", "BOOLEAN", None, None, None),
             ("绝缘电阻", "NUMBER", "MΩ", None, 100.0),
             ("耐压", "NUMBER", "V", None, 1500.0)]
for p in PRODUCTS:
    tpln += 1
    tpl_vals.append(row([tpln, TENANT, "QC%s%03d" % (TODAY, tpln),
                         p["name"] + "成品检验规范", "OQC",
                         mat_id[p["name"]], mat_meta[p["name"]]["code"], p["name"],
                         1, 1, "按产品族通用检验要求生成", BY, DEPT, NOW, "0"]))
    for sn, (iname, dtype, unit, usl, lsl) in enumerate(GEN_ITEMS, start=1):
        itemn += 1
        item_vals.append(row([itemn, TENANT, tpln, "IT%04d" % itemn, iname, dtype, unit,
                              "RANGE" if usl is not None else "BOOLEAN",
                              None, lsl, usl, None, None, None, None,
                              "MAJOR" if iname in ("功能", "尺寸") else "MINOR",
                              sn, 1, None, BY, DEPT, NOW]))
w(f"-- 质检模板 {tpln} / 检验项 {itemn}")
w("INSERT INTO `ry-mes`.qc_inspect_template")
w("  (template_id, tenant_id, template_code, template_name, template_type, material_id,")
w("   material_code, material_name, version_no, status, remark,")
w("   create_by, create_dept, create_time, del_flag) VALUES")
w(",\n".join(tpl_vals) + ";")
w()
w("INSERT INTO `ry-mes`.qc_inspect_item")
w("  (item_id, tenant_id, template_id, item_code, item_name, data_type, unit, standard_type,")
w("   nominal_value, lsl_value, usl_value, tol_upper, tol_lower, aql_level, sample_plan_code,")
w("   criticality, seq_num, enabled, remark, create_by, create_dept, create_time) VALUES")
w(",\n".join(item_vals) + ";")
w()

test_vals, testn = [], 0
for p in PRODUCTS:
    if p["cat"] not in ("FIN_0301", "FIN_0302", "FIN_0303", "FIN_0304", "FIN_0305",
                        "FIN_0306", "FIN_0307", "FIN_0703", "FIN_0708", "FIN_0902",
                        "FIN_0903", "FIN_0901", "FIN_1001", "FIN_1002", "FIN_1003",
                        "FIN_1004", "FIN_1006"):
        continue
    testn += 1
    test_vals.append(row([testn, TENANT, "TI%s%03d" % (TODAY, testn),
                          p["name"] + "出厂测试项", mat_id[p["name"]], 0, "NUMBER",
                          None, None, None, None, None, 1, testn, DEPT, BY, NOW,
                          None, NOW, "0", None]))
w(f"-- 测试项 {testn}（电子/电气类产品）")
w("INSERT INTO `ry-mes`.mes_test_item")
w("  (test_item_id, tenant_id, item_code, item_name, material_id, content_ref_id, data_type,")
w("   unit, upper_limit, lower_limit, target_value, pass_enum, is_required, sort_order,")
w("   create_dept, create_by, create_time, update_by, update_time, del_flag, remark) VALUES")
w(",\n".join(test_vals) + ";")
w()
C.issue("MES_TEST_NO", testn, "TI%s%03d" % (TODAY, testn))

# ---------------------------------------------------------------- 14. 固件（电子产品）
w("-- ============ 14. 固件档案（电子产品） ============")
fw_vals, fwn = [], 0
for p in PRODUCTS:
    if "PCBA" not in p["name"] and p["cat"] not in (
            "FIN_0302", "FIN_0303", "FIN_0304", "FIN_0305", "FIN_0307",
            "FIN_0703", "FIN_0708", "FIN_0902", "FIN_0903", "FIN_1001",
            "FIN_1002", "FIN_1003", "FIN_1004", "FIN_1006"):
        continue
    fwn += 1
    fw_vals.append(row([fwn, TENANT, "FW%s%03d" % (TODAY, fwn),
                        p["name"] + "主控固件", mat_id[p["name"]],
                        mat_meta[p["name"]]["code"], p["name"], "V1.0.0", "BUILD%03d" % fwn,
                        None, "md5%028d" % fwn, 262144, "初始版本固件",
                        1, 1, None, 1, NOW, BY, DEPT, NOW, None, NOW, "0", None]))
w(f"-- 固件 {fwn} 份")
w("INSERT INTO `ry-mes`.mes_firmware")
w("  (firmware_id, tenant_id, firmware_code, firmware_name, material_id, material_code,")
w("   material_name, version, build_no, oss_id, file_md5, file_size, release_note,")
w("   status, is_default, select_flow_id, published_by, publish_time,")
w("   create_by, create_dept, create_time, update_by, update_time, del_flag, remark) VALUES")
w(",\n".join(fw_vals) + ";")
w()
C.issue("MES_FW_CODE", fwn, "FW%s%03d" % (TODAY, fwn))

# ---------------------------------------------------------------- 15. 价格表
w("-- ============ 15. 销售价目表 ============")
w("INSERT INTO `ry-erp`.so_price_list")
w("  (price_list_id, tenant_id, list_code, list_name, list_type, client_id, currency,")
w("   priority, valid_from, valid_to, status, create_by, create_dept, create_time) VALUES")
w("  (1, '%s', 'PL%s001', '2026 年度标准零售价目表', 'BASE', NULL, 'CNY', 10," % (TENANT, TODAY))
w("   '%s-01-01', '%s-12-31', 'ACTIVE', %d, %d, NOW());" % (NOW.year, NOW.year + 1, BY, DEPT))
w()
pl_vals = []
for i, p in enumerate(PRODUCTS, start=1):
    pl_vals.append(row([i, TENANT, 1, mat_id[p["name"]], p["price"], 1, 9999, 100.00, BY, DEPT, NOW]))
w(f"-- 价目表行 {len(pl_vals)}（36 个产成品标准售价）")
w("INSERT INTO `ry-erp`.so_price_list_line")
w("  (line_id, tenant_id, price_list_id, material_id, unit_price, min_qty, max_qty,")
w("   discount_rate, create_dept, create_by, create_time) VALUES")
w(",\n".join(pl_vals) + ";")
w()

# ---------------------------------------------------------------- 16. 会计期间 / 节假日
w("-- ============ 16. 会计期间与节假日 ============")
per_vals = []
for m in range(1, 13):
    import calendar
    last = calendar.monthrange(NOW.year, m)[1]
    per_vals.append(row([m, TENANT, NOW.year, m, "%d年%02d期" % (NOW.year, m),
                         "%d-%02d-01" % (NOW.year, m), "%d-%02d-%02d" % (NOW.year, m, last),
                         "OPEN", 0, 0, None, 0, None, None, 1, BY, DEPT, NOW]))
w("INSERT INTO `ry-erp`.gl_period")
w("  (period_id, tenant_id, fiscal_year, period_no, period_name, start_date, end_date,")
w("   status, closing_step, closed_by, closed_time, closed_voucher_count,")
w("   closing_voucher_id, revaluation_voucher_id, version,")
w("   create_by, create_dept, create_time) VALUES")
w(",\n".join(per_vals) + ";")
w()

HOLIDAYS = [("%d-01-01" % NOW.year, "元旦", "LEGAL"), ("%d-05-01" % NOW.year, "劳动节", "LEGAL"),
            ("%d-10-01" % NOW.year, "国庆节", "LEGAL")]
hol_vals = []
for i, (d, n_, t) in enumerate(HOLIDAYS, start=1):
    hol_vals.append(row([i, TENANT, d, n_, t, NOW.year, 0, None, BY, DEPT, NOW]))
w("INSERT INTO `ry-erp`.sch_holiday")
w("  (holiday_id, tenant_id, holiday_date, holiday_name, holiday_type, year,")
w("   is_working_day, remark, create_by, create_dept, create_time) VALUES")
w(",\n".join(hol_vals) + ";")
w()

# ---------------------------------------------------------------- 17. 工人档案完善
w("-- ============ 17. 工人档案完善（重挂车间 + 技能矩阵 + 资质 + 工时单价） ============")
# 17.1 给 sch_worker 加列（若不存在）
w("-- 17.1 新增列（幂等）")


def add_column(col, ddl, comment):
    w("SET @s := IF((SELECT COUNT(*) FROM information_schema.COLUMNS "
      "WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='sch_worker' "
      "AND COLUMN_NAME='%s')=0," % col)
    w("  'ALTER TABLE `ry-erp`.sch_worker ADD COLUMN `%s` %s "
      "COMMENT ''%s''', 'DO 0');" % (col, ddl, comment))
    w("PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;")


add_column("worker_code", "VARCHAR(64) DEFAULT NULL", "员工编号")
add_column("labor_rate", "DECIMAL(10,2) DEFAULT 0", "工时单价元每小时")
add_column("id_card", "VARCHAR(32) DEFAULT NULL", "身份证号")
add_column("birth_date", "DATE DEFAULT NULL", "出生日期")
add_column("education", "VARCHAR(32) DEFAULT NULL", "学历")
add_column("emergency_contact", "VARCHAR(64) DEFAULT NULL", "紧急联系人")
add_column("emergency_phone", "VARCHAR(32) DEFAULT NULL", "紧急联系电话")
w()

# 17.2 技能矩阵表
w("-- 17.2 技能矩阵表 sch_worker_skill")
w("""CREATE TABLE IF NOT EXISTS `ry-erp`.sch_worker_skill (
  skill_id       BIGINT       NOT NULL COMMENT '技能记录ID',
  tenant_id      VARCHAR(20)  DEFAULT '000000' COMMENT '租户ID',
  worker_id      BIGINT       NOT NULL COMMENT '工人档案ID',
  worker_name    VARCHAR(64)  DEFAULT NULL COMMENT '工人姓名',
  process_id     BIGINT       NOT NULL COMMENT '工序ID',
  process_code   VARCHAR(64)  DEFAULT NULL COMMENT '工序编码',
  process_name   VARCHAR(255) DEFAULT NULL COMMENT '工序名称',
  skill_level    VARCHAR(32)  DEFAULT NULL COMMENT '熟练等级 NOVICE/QUALIFIED/SKILLED/EXPERT',
  skill_score    INT          DEFAULT 0 COMMENT '技能评分 0-100',
  certified      TINYINT(1)   DEFAULT 0 COMMENT '是否已认证',
  years_experience DECIMAL(4,1) DEFAULT 0 COMMENT '经验年限',
  remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  create_dept    BIGINT       DEFAULT NULL,
  create_by      BIGINT       DEFAULT NULL,
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag       CHAR(1)      DEFAULT '0',
  PRIMARY KEY (skill_id),
  KEY idx_worker (worker_id),
  KEY idx_process (process_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工人技能矩阵';""")
w()
w("-- 17.3 资质证书表 sch_worker_certificate")
w("""CREATE TABLE IF NOT EXISTS `ry-erp`.sch_worker_certificate (
  cert_id        BIGINT       NOT NULL COMMENT '证书ID',
  tenant_id      VARCHAR(20)  DEFAULT '000000' COMMENT '租户ID',
  worker_id      BIGINT       NOT NULL COMMENT '工人档案ID',
  worker_name    VARCHAR(64)  DEFAULT NULL COMMENT '工人姓名',
  cert_code      VARCHAR(64)  DEFAULT NULL COMMENT '证书编号',
  cert_name      VARCHAR(128) NOT NULL COMMENT '证书名称',
  cert_type      VARCHAR(32)  DEFAULT NULL COMMENT '证书类型',
  issuing_authority VARCHAR(128) DEFAULT NULL COMMENT '发证机构',
  issue_date     DATE         DEFAULT NULL COMMENT '发证日期',
  expiry_date    DATE         DEFAULT NULL COMMENT '有效期至',
  status         VARCHAR(20)  DEFAULT 'VALID' COMMENT '状态 VALID/EXPIRING/EXPIRED',
  remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  create_dept    BIGINT       DEFAULT NULL,
  create_by      BIGINT       DEFAULT NULL,
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag       CHAR(1)      DEFAULT '0',
  PRIMARY KEY (cert_id),
  KEY idx_worker (worker_id),
  KEY idx_status (status),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工人资质证书';""")
w()

# 17.4 回填 sch_worker：车间 / 工号 / 工时单价
w("-- 17.4 重挂车间 + 回填工号与工时单价（37 名工人均分到 15 个车间）")
w("UPDATE `ry-erp`.sch_worker SET worker_code = CONCAT('SCZX%s', LPAD(worker_id, 3, '0')) WHERE worker_code IS NULL;" % TODAY)
shop_ids = list(range(1, len(WORKSHOPS) + 1))
for i, sid in enumerate(shop_ids):
    code = [c for c, _n, _w in WORKSHOPS][i]
    w("UPDATE `ry-erp`.sch_worker SET workshop_id=%d, workshop_name='%s' "
      "WHERE (worker_id %% %d) = %d AND del_flag='0';"
      % (sid, shop_name_by[code], len(shop_ids), i))
w("UPDATE `ry-erp`.sch_worker SET labor_rate = CASE "
  "WHEN skill_level IN ('高级技师','EXPERT') THEN 65.00 "
  "WHEN skill_level IN ('技师','SKILLED') THEN 50.00 "
  "WHEN skill_level IN ('高级工','QUALIFIED') THEN 42.00 ELSE 35.00 END "
  "WHERE labor_rate IS NULL OR labor_rate = 0;")
w("UPDATE `ry-erp`.sch_worker SET education='中专' WHERE education IS NULL;")
w("UPDATE `ry-erp`.sch_worker SET emergency_contact='待补充' WHERE emergency_contact IS NULL;")
w()

# 17.5 生成技能矩阵（每人 2~4 项工序技能）
w("-- 17.5 技能矩阵数据")
workers = list(range(1, 38))
proc_list = [(proc_id[c], c, n_) for c, n_, *_ in PROCESSES]
skill_vals, skn = [], 0
LEVELS = ["QUALIFIED", "SKILLED", "EXPERT", "NOVICE"]
for wi, wid in enumerate(workers):
    for k in range(3):
        idx = (wi * 3 + k) % len(proc_list)
        pidx, pcode, pname = proc_list[idx]
        skn += 1
        lvl = LEVELS[(wi + k) % len(LEVELS)]
        skill_vals.append(row([skn, TENANT, wid, "工人%02d" % wid, pidx, pcode, pname,
                               lvl, 60 + (wi * 7 + k * 11) % 40, 1 if lvl != "NOVICE" else 0,
                               round(1.0 + (wi + k) % 12, 1), None, BY, DEPT, NOW]))
w("INSERT INTO `ry-erp`.sch_worker_skill")
w("  (skill_id, tenant_id, worker_id, worker_name, process_id, process_code, process_name,")
w("   skill_level, skill_score, certified, years_experience, remark,")
w("   create_dept, create_by, create_time) VALUES")
w(",\n".join(skill_vals) + ";")
w()

# 17.6 资质证书（焊工/无损检测/叉车等特种作业）
w("-- 17.6 资质证书（特种作业持证上岗）")
CERT_DEFS = [(5, "熔化焊接与热切割作业证", "SPECIAL"), (6, "焊接作业资格证", "SPECIAL"),
             (3, "起重机械操作证", "SPECIAL"), (4, "叉车司机证", "SPECIAL")]
cert_vals, certn = [], 0
for wid in workers[:12]:
    cd = CERT_DEFS[wid % len(CERT_DEFS)]
    certn += 1
    cert_vals.append(row([certn, TENANT, wid, "工人%02d" % wid, "CERT%s%03d" % (TODAY, certn),
                          cd[1], cd[2], "浙江省应急管理厅",
                          "%d-03-15" % (NOW.year - 2), "%d-03-14" % (NOW.year + 4),
                          "VALID", None, BY, DEPT, NOW]))
w("INSERT INTO `ry-erp`.sch_worker_certificate")
w("  (cert_id, tenant_id, worker_id, worker_name, cert_code, cert_name, cert_type,")
w("   issuing_authority, issue_date, expiry_date, status, remark,")
w("   create_dept, create_by, create_time) VALUES")
w(",\n".join(cert_vals) + ";")
w()
C.issue("SCH_WORKER_CODE", 37, "SCZX%s037" % TODAY)

# ---------------------------------------------------------------- 18. 编码计数器回写
w("-- ============ 18. 编码计数器回写（保证引擎续号正确） ============")
RULE_IDS = {
    "CLIENT_CODE": 207, "VENDOR_CODE": 208, "WORKSHOP_CODE": 209, "WAREHOUSE_CODE": 210,
    "WM_LOCATION_CODE": 211, "WM_AREA_CODE": 212, "WORKSTATION_CODE": 216,
    "MATERIAL_CODE": 236, "SCH_WORKER_CODE": 269, "MES_SOP_CODE": 286,
    "MES_FW_CODE": 289, "MES_TEST_NO": 291, "ROUTING_CODE": 301,
}
NEVER_RESET = {"CLIENT_CODE", "VENDOR_CODE", "WORKSHOP_CODE", "WAREHOUSE_CODE",
               "WM_LOCATION_CODE", "WM_AREA_CODE", "WORKSTATION_CODE", "SCH_WORKER_CODE"}
cvals, cid = [], 9000000001
for rule, (serial, last) in sorted(C.state.items()):
    rid = RULE_IDS.get(rule)
    if not rid:
        print("WARN: 未找到规则ID", rule, file=sys.stderr)
        continue
    gd = "20260101000000" if rule in NEVER_RESET else STAMP
    cvals.append(row([cid, TENANT, rid, gd, serial, last, serial, None, "", 0, 0,
                      BY, DEPT, NOW, None, NOW, "0"]))
    cid += 1
w(f"-- 回写 {len(cvals)} 条计数器（永不重置规则用 2026 年基准日期，日规则用今日时间戳）")
w("INSERT INTO `ry-vue`.sys_auto_code_result")
w("  (code_id, tenant_id, rule_id, gen_date, gen_index, last_result, last_serial_no,")
w("   last_input_char, remark, attr1, attr2, create_dept, create_by, create_time,")
w("   update_by, update_time, del_flag) VALUES")
w(",\n".join(cvals) + ";")
w()

w("SET SQL_SAFE_UPDATES = 1;")
w()
w("-- ============ 校验 ============")
w("SELECT 'bd_material' t, COUNT(*) c FROM `ry-erp`.bd_material")
w("UNION ALL SELECT 'bd_bom', COUNT(*) FROM `ry-erp`.bd_bom")
w("UNION ALL SELECT 'bd_bom_detail', COUNT(*) FROM `ry-erp`.bd_bom_detail")
w("UNION ALL SELECT 'bd_routing', COUNT(*) FROM `ry-erp`.bd_routing")
w("UNION ALL SELECT 'bd_routing_step', COUNT(*) FROM `ry-erp`.bd_routing_step")
w("UNION ALL SELECT 'bd_material_category', COUNT(*) FROM `ry-erp`.bd_material_category")
w("UNION ALL SELECT 'bd_process', COUNT(*) FROM `ry-erp`.bd_process")
w("UNION ALL SELECT 'bd_workshop', COUNT(*) FROM `ry-erp`.bd_workshop")
w("UNION ALL SELECT 'bd_workstation', COUNT(*) FROM `ry-erp`.bd_workstation")
w("UNION ALL SELECT 'wm_warehouse', COUNT(*) FROM `ry-erp`.wm_warehouse")
w("UNION ALL SELECT 'wm_storage_area', COUNT(*) FROM `ry-erp`.wm_storage_area")
w("UNION ALL SELECT 'wm_storage_location', COUNT(*) FROM `ry-erp`.wm_storage_location")
w("UNION ALL SELECT 'bd_vendor', COUNT(*) FROM `ry-erp`.bd_vendor")
w("UNION ALL SELECT 'bd_client', COUNT(*) FROM `ry-erp`.bd_client")
w("UNION ALL SELECT 'sch_worker_skill', COUNT(*) FROM `ry-erp`.sch_worker_skill")
w("UNION ALL SELECT 'mes_mbom', COUNT(*) FROM `ry-mes`.mes_mbom")
w("UNION ALL SELECT 'eq_equipment', COUNT(*) FROM `ry-mes`.eq_equipment")
w("UNION ALL SELECT 'sop_document', COUNT(*) FROM `ry-mes`.sop_document")
w("UNION ALL SELECT 'ncr_defect_code', COUNT(*) FROM `ry-mes`.ncr_defect_code")
w("UNION ALL SELECT 'iot_device', COUNT(*) FROM `ry-iot`.iot_device")
w("UNION ALL SELECT 'auto_code_result', COUNT(*) FROM `ry-vue`.sys_auto_code_result;")

if len(sys.argv) > 1:
    # 直接由 Python 写文件，避免 PowerShell 重定向引入 BOM / UTF-16
    Path(sys.argv[1]).write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(f"written: {sys.argv[1]}  ({len(out)} lines)", file=sys.stderr)
else:
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))
