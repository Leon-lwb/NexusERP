#!/usr/bin/env node
/**
 * ERP 3D 示例仓库演示数据生成器（PLAN-2026-006）
 *
 * 产出：
 *   1. doc/erp/sql/test-data/erp-wm-3d-demo-seed.sql —— 可重复执行的种子脚本（SET NAMES + 清理段 + 幂等重插）
 *   2. script/sql/ry-erp.sql —— 就地替换初始数据中的旧 3D 演示块（1 仓库/2 库区/18 库位/10 库存 → 1/4/104/55）
 *
 * 布局（坐标单位米；前端渲染 posX/posZ 为世界坐标中心，posY 为底面）：
 *   A区 原料货架区 RACK  中心(-8,0,0) 12×8×4.4：3 排 RACK-A1/A2/A3，排中心 z=-2.5/0/+2.5（排深1.0，通道1.5）
 *       每排 5 列×4 层，库位 1.2×1.0×1.0；列 x=-8+(col-3)*1.2，层底 y=(layer-1)*1.0
 *   B区 成品货架区 RACK  中心(+8,0,0) 12×8×4.0：2 排 RACK-B1/B2，排中心 z=±1.35（排深1.2，通道1.5）
 *       每排 5 列×3 层，库位 1.2×1.2×1.2；列 x=8+(col-3)*1.2，层底 y=(layer-1)*1.2
 *   C区 拣选网格区 GRID  中心(0,0,-8) 10×6×2.0：5 列×2 行单层，库位 1.5×1.2×1.2；列 x=(col-3)*1.5，行 z=-8±0.6
 *   D区 散货堆放区 BULK  中心(0,0,+8) 10×6×3.0：2×2 地堆位，库位 3.0×1.5×2.0；x=±1.5，z=7/9
 *
 * 状态口径（与后端 3D 聚合接口运行时计算一致，静态 status/occupancy_rate 列同步写入便于列表页展示）：
 *   EMPTY=无库存 / OCCUPIED=有库存且预留<80% / WARNING=预留占比>=80% / LOCKED=库位冻结或任一库存冻结
 *
 * 用法：node erp-wm-3d-demo-datagen.mjs（幂等，可重复执行）
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..', '..', '..');
const SEED_PATH = join(HERE, 'erp-wm-3d-demo-seed.sql');
const RYERP_PATH = join(REPO, 'script', 'sql', 'ry-erp.sql');

const TENANT = '000000';
const WH_ID = 9900000001;
const TS_SEED = 'NOW()';
const TS_INIT = "'2026-08-22 10:00:00'";

// ---------------------------------------------------------------
// 基础数据定义
// ---------------------------------------------------------------
const MATS = {
  M001: { id: 9900000001, code: 'DEMO-M001', name: '粗铜线', spec: '2.5mm', uom: '千克', cost: 45, type: 1 },
  M002: { id: 9900000002, code: 'DEMO-M002', name: '铝制外壳', spec: 'GW系列', uom: '件', cost: 28, type: 1 },
  M003: { id: 9900000003, code: 'DEMO-M003', name: '螺丝紧固包', spec: 'M3/M5 混合装', uom: '包', cost: 8.5, type: 4 },
  P001: { id: 9900000004, code: 'DEMO-P001', name: '智能网关', spec: 'GW-1000', uom: '台', cost: 260, type: 3 },
  P002: { id: 9900000005, code: 'DEMO-P002', name: '传感器模组', spec: 'SN-200', uom: '个', cost: 120, type: 3 },
  F001: { id: 9900000006, code: 'DEMO-F001', name: '防静电包装箱', spec: '600×400mm', uom: '个', cost: 15, type: 4 }
};
const BATCH_DATE = { B20260801: '2026-08-01 08:30:00', B20260808: '2026-08-08 09:10:00', B20260815: '2026-08-15 14:40:00' };

const AREAS = [
  { id: 9900000011, code: 'A3D01', name: '原料货架区', layout: 'RACK', x: -8, z: 0, w: 12, l: 8, h: 4.4, color: '#409eff', walls: 0, cap: 600, loa: 500, labelSize: 0.3, remark: '3D示例·原料货架（3排×5列×4层）' },
  { id: 9900000012, code: 'B3D02', name: '成品货架区', layout: 'RACK', x: 8, z: 0, w: 12, l: 8, h: 4.0, color: '#67c23a', walls: 0, cap: 500, loa: 800, labelSize: 0.3, remark: '3D示例·成品货架（2排×5列×3层）' },
  { id: 9900000013, code: 'C3D03', name: '拣选网格区', layout: 'GRID', x: 0, z: -8, w: 10, l: 6, h: 2.0, color: '#e6a23c', walls: 1, cap: 500, loa: 300, labelSize: 0.35, remark: '3D示例·拣选位（5列×2行）' },
  { id: 9900000014, code: 'D3D04', name: '散货堆放区', layout: 'BULK', x: 0, z: 8, w: 10, l: 6, h: 3.0, color: '#909399', walls: 1, cap: 1000, loa: 2000, labelSize: 0.5, remark: '3D示例·地堆位（2×2）' }
];

// 库位定义：按库区遍历生成，编号 ID 段 9900000101+（A）/9900000201+（B）/9900000301+（C）/9900000401+（D）
const LOC_STATUS_COLORS = { EMPTY: '#909399', OCCUPIED: '#409eff', LOCKED: '#f56c6c', WARNING: '#e6a23c' };

/** @type {Map<string, any>} locationCode -> 库位记录 */
const LOCATIONS = new Map();
const addLoc = (rec) => {
  if (LOCATIONS.has(rec.code)) throw new Error(`库位编码重复: ${rec.code}`);
  LOCATIONS.set(rec.code, rec);
};

// A区：3 排 × 5 列 × 4 层（60 个）
for (let r = 1; r <= 3; r++) {
  for (let layer = 1; layer <= 4; layer++) {
    for (let col = 1; col <= 5; col++) {
      addLoc({
        id: 9900000101 + (r - 1) * 20 + (layer - 1) * 5 + (col - 1),
        code: `A${r}-L${layer}C${col}`,
        name: `A${r}排${layer}层${col}列`,
        area: AREAS[0],
        rackId: `RACK-A${r}`,
        layer, col, row: 1,
        x: -8 + (col - 3) * 1.2,
        y: (layer - 1) * 1.0,
        z: -2.5 + (r - 1) * 2.5,
        w: 1.2, h: 1.0, d: 1.0,
        type: 'STORAGE'
      });
    }
  }
}
// B区：2 排 × 5 列 × 3 层（30 个）
for (let r = 1; r <= 2; r++) {
  for (let layer = 1; layer <= 3; layer++) {
    for (let col = 1; col <= 5; col++) {
      addLoc({
        id: 9900000201 + (r - 1) * 15 + (layer - 1) * 5 + (col - 1),
        code: `B${r}-L${layer}C${col}`,
        name: `B${r}排${layer}层${col}列`,
        area: AREAS[1],
        rackId: `RACK-B${r}`,
        layer, col, row: 1,
        x: 8 + (col - 3) * 1.2,
        y: (layer - 1) * 1.2,
        z: r === 1 ? -1.35 : 1.35,
        w: 1.2, h: 1.2, d: 1.2,
        type: 'STORAGE'
      });
    }
  }
}
// C区：5 列 × 2 行单层（10 个，拣货位）
for (let row = 1; row <= 2; row++) {
  for (let col = 1; col <= 5; col++) {
    addLoc({
      id: 9900000301 + (row - 1) * 5 + (col - 1),
      code: `C-R${row}C${col}`,
      name: `C区${row}行${col}列`,
      area: AREAS[2],
      rackId: null,
      layer: 1, col, row,
      x: (col - 3) * 1.5,
      y: 0,
      z: -8 + (row - 1.5) * 1.2,
      w: 1.5, h: 1.2, d: 1.2,
      type: 'PICKING'
    });
  }
}
// D区：2×2 地堆位（4 个）
for (let row = 1; row <= 2; row++) {
  for (let col = 1; col <= 2; col++) {
    addLoc({
      id: 9900000401 + (row - 1) * 2 + (col - 1),
      code: `D-P${(row - 1) * 2 + col}`,
      name: `散货堆位${(row - 1) * 2 + col}`,
      area: AREAS[3],
      rackId: null,
      layer: 1, col, row,
      x: (col - 1.5) * 3,
      y: 0,
      z: 8 + (row - 1.5) * 2,
      w: 3.0, h: 1.5, d: 2.0,
      type: 'STORAGE'
    });
  }
}

// 库存分配（55 条，覆盖 49 库位）：
//   OCCUPIED 35 / WARNING 8（预留占比>=80%）/ LOCKED 6（A1-L2C3 库位级冻结，其余库存级冻结）/ EMPTY 55
//   多批次库位 6 个（stockCount=2）；数量梯度 30~800 喂饱"按占用率"着色
const LOC_FROZEN = new Set(['A1-L2C3']); // 库位级冻结演示（库存不冻结）
const STOCKS = [
  // ---- A区 原料货架区（28 条 / 25 库位）----
  { loc: 'A1-L1C1', mat: 'M001', batch: 'B20260801', qty: 480, rsv: 0 },
  { loc: 'A1-L1C2', mat: 'M001', batch: 'B20260808', qty: 260, rsv: 0 },
  { loc: 'A1-L1C3', mat: 'M001', batch: 'B20260815', qty: 200, rsv: 165 }, // WARNING 82.5%
  { loc: 'A1-L1C5', mat: 'M002', batch: 'B20260801', qty: 90, rsv: 0 },
  { loc: 'A1-L2C1', mat: 'M002', batch: 'B20260808', qty: 150, rsv: 60 },
  { loc: 'A1-L2C3', mat: 'M001', batch: 'B20260801', qty: 120, rsv: 0 }, // LOCKED：库位冻结
  { loc: 'A1-L3C2', mat: 'M002', batch: 'B20260815', qty: 75, rsv: 0 },
  { loc: 'A1-L3C4', mat: 'M003', batch: 'B20260808', qty: 300, rsv: 0 },
  { loc: 'A1-L4C1', mat: 'M003', batch: 'B20260815', qty: 400, rsv: 360 }, // WARNING 90%
  { loc: 'A1-L4C3', mat: 'M001', batch: 'B20260808', qty: 30, rsv: 0 },
  { loc: 'A2-L1C1', mat: 'M001', batch: 'B20260801', qty: 520, rsv: 0 },
  { loc: 'A2-L1C4', mat: 'M002', batch: 'B20260801', qty: 80, rsv: 70 }, // WARNING 87.5%
  { loc: 'A2-L2C2', mat: 'M003', batch: 'B20260808', qty: 260, rsv: 0 },
  { loc: 'A2-L2C5', mat: 'M001', batch: 'B20260815', qty: 180, rsv: 0 }, // 双批次
  { loc: 'A2-L2C5', mat: 'M001', batch: 'B20260801', qty: 90, rsv: 0 },
  { loc: 'A2-L3C1', mat: 'M002', batch: 'B20260808', qty: 60, rsv: 0, frozen: 1 }, // LOCKED：库存冻结
  { loc: 'A2-L3C3', mat: 'M001', batch: 'B20260801', qty: 310, rsv: 0 },
  { loc: 'A2-L4C2', mat: 'M003', batch: 'B20260815', qty: 145, rsv: 0 },
  { loc: 'A2-L4C5', mat: 'M001', batch: 'B20260808', qty: 220, rsv: 200 }, // WARNING 91%
  { loc: 'A3-L1C2', mat: 'M002', batch: 'B20260815', qty: 200, rsv: 0 },
  { loc: 'A3-L1C4', mat: 'M001', batch: 'B20260801', qty: 95, rsv: 0 },
  { loc: 'A3-L2C1', mat: 'M003', batch: 'B20260808', qty: 380, rsv: 0 }, // 双批次
  { loc: 'A3-L2C1', mat: 'M003', batch: 'B20260815', qty: 120, rsv: 0 },
  { loc: 'A3-L2C4', mat: 'M003', batch: 'B20260801', qty: 210, rsv: 0, frozen: 1 }, // LOCKED：库存冻结
  { loc: 'A3-L3C2', mat: 'M002', batch: 'B20260808', qty: 130, rsv: 45 },
  { loc: 'A3-L3C5', mat: 'M002', batch: 'B20260815', qty: 140, rsv: 115 }, // WARNING 82%
  { loc: 'A3-L4C3', mat: 'M001', batch: 'B20260801', qty: 240, rsv: 0 }, // 双批次
  { loc: 'A3-L4C3', mat: 'M001', batch: 'B20260808', qty: 85, rsv: 0 },
  // ---- B区 成品货架区（18 条 / 16 库位）----
  { loc: 'B1-L1C1', mat: 'P001', batch: 'B20260815', qty: 500, rsv: 0 },
  { loc: 'B1-L1C3', mat: 'P001', batch: 'B20260815', qty: 40, rsv: 34 }, // WARNING 85%
  { loc: 'B1-L1C5', mat: 'P002', batch: 'B20260801', qty: 210, rsv: 0 },
  { loc: 'B1-L2C1', mat: 'P001', batch: 'B20260808', qty: 88, rsv: 0 }, // 双批次
  { loc: 'B1-L2C1', mat: 'P001', batch: 'B20260815', qty: 60, rsv: 0 },
  { loc: 'B1-L2C2', mat: 'P002', batch: 'B20260808', qty: 260, rsv: 0 },
  { loc: 'B1-L2C4', mat: 'P001', batch: 'B20260815', qty: 120, rsv: 30 },
  { loc: 'B1-L3C1', mat: 'P002', batch: 'B20260808', qty: 180, rsv: 0 }, // 双批次
  { loc: 'B1-L3C1', mat: 'P002', batch: 'B20260815', qty: 90, rsv: 0 },
  { loc: 'B1-L3C5', mat: 'P001', batch: 'B20260801', qty: 75, rsv: 0, frozen: 1 }, // LOCKED：库存冻结
  { loc: 'B2-L1C2', mat: 'P001', batch: 'B20260808', qty: 340, rsv: 0 },
  { loc: 'B2-L1C5', mat: 'P002', batch: 'B20260815', qty: 150, rsv: 0 },
  { loc: 'B2-L2C1', mat: 'P002', batch: 'B20260808', qty: 120, rsv: 100 }, // WARNING 83%
  { loc: 'B2-L2C3', mat: 'P001', batch: 'B20260815', qty: 200, rsv: 0 },
  { loc: 'B2-L2C5', mat: 'P002', batch: 'B20260808', qty: 320, rsv: 0 },
  { loc: 'B2-L3C1', mat: 'P001', batch: 'B20260815', qty: 155, rsv: 20 },
  { loc: 'B2-L3C2', mat: 'P002', batch: 'B20260815', qty: 45, rsv: 0, frozen: 1 }, // LOCKED：库存冻结
  { loc: 'B2-L3C4', mat: 'P001', batch: 'B20260808', qty: 95, rsv: 0 },
  // ---- C区 拣选网格区（7 条 / 6 库位）----
  { loc: 'C-R1C1', mat: 'F001', batch: 'B20260815', qty: 450, rsv: 0 },
  { loc: 'C-R1C3', mat: 'M003', batch: 'B20260808', qty: 280, rsv: 0 },
  { loc: 'C-R1C5', mat: 'F001', batch: 'B20260808', qty: 60, rsv: 50 }, // WARNING 83%
  { loc: 'C-R2C1', mat: 'M003', batch: 'B20260801', qty: 150, rsv: 0 },
  { loc: 'C-R2C2', mat: 'M003', batch: 'B20260815', qty: 190, rsv: 0 }, // 双批次
  { loc: 'C-R2C2', mat: 'M003', batch: 'B20260808', qty: 100, rsv: 0 },
  { loc: 'C-R2C4', mat: 'F001', batch: 'B20260801', qty: 330, rsv: 0 },
  // ---- D区 散货堆放区（2 条 / 2 库位）----
  { loc: 'D-P1', mat: 'F001', batch: 'B20260808', qty: 800, rsv: 0 },
  { loc: 'D-P3', mat: 'F001', batch: 'B20260801', qty: 350, rsv: 0, frozen: 1 } // LOCKED：库存冻结
];

// ---------------------------------------------------------------
// 派生：库位运行时状态（与后端 WmWarehouseServiceImpl.applyStatus 同口径）
// ---------------------------------------------------------------
const stockAgg = new Map(); // locCode -> { qty, rsv, frozen, count }
for (const s of STOCKS) {
  let a = stockAgg.get(s.loc);
  if (!a) {
    a = { qty: 0, rsv: 0, frozen: 0, count: 0 };
    stockAgg.set(s.loc, a);
  }
  a.qty += s.qty;
  a.rsv += s.rsv;
  a.frozen = Math.max(a.frozen, s.frozen || 0);
  a.count += 1;
}
for (const loc of LOCATIONS.values()) {
  const agg = stockAgg.get(loc.code);
  if (!agg || agg.qty <= 0) loc.status = 'EMPTY';
  else if (LOC_FROZEN.has(loc.code) || agg.frozen === 1) loc.status = 'LOCKED';
  else if (agg.rsv / agg.qty >= 0.8) loc.status = 'WARNING';
  else loc.status = 'OCCUPIED';
  loc.qty = agg ? agg.qty : 0;
  loc.occupancy = Math.min(100, Math.round((loc.qty / loc.area.cap) * 10000) / 100);
}
const STATUS_INT = { EMPTY: 0, OCCUPIED: 1, LOCKED: 2, WARNING: 3 };

// 库存 ID：按数组顺序 9900003001+
const stockRows = STOCKS.map((s, i) => {
  const loc = LOCATIONS.get(s.loc);
  if (!loc) throw new Error(`库存引用了不存在的库位: ${s.loc}`);
  const m = MATS[s.mat];
  return {
    id: 9900003001 + i,
    loc, m,
    batch: s.batch,
    recpt: BATCH_DATE[s.batch],
    qty: s.qty,
    rsv: s.rsv,
    frozen: s.frozen || 0
  };
});

// ---------------------------------------------------------------
// SQL 渲染
// ---------------------------------------------------------------
const q = (v) => (v === null || v === undefined ? 'NULL' : `'${v}'`);
const n = (v, d = 2) => (v === null || v === undefined ? 'NULL' : Number(v).toFixed(d));

/** 种子脚本：显式列名 + 多行 VALUES */
const whCols = '(warehouse_id, warehouse_code, warehouse_name, location, area, manager, manager_id, status, remark, tenant_id, del_flag, create_by, create_time, update_by, update_time)';
const whVals = (ts) => `(${WH_ID}, 'WH-3D-DEMO', '3D示例仓库', '厂区一号库·3D可视化示例', 800.00, 'admin', 1, 1, '3D 仓库可视化示例数据（PLAN-2026-006）', '${TENANT}', '0', 1, ${ts}, 1, ${ts})`;

const areaCols = '(area_id, area_code, area_name, warehouse_id, area, location_flag, enable_flag, frozen_flag, remark, layout_type, pos_x, pos_y, pos_z, area_width, area_length, area_height, rotation_x, rotation_y, rotation_z, color, opacity, model_type, show_walls, show_label, grid_visible, tenant_id, del_flag, create_by, create_time, update_by, update_time)';
const areaVals = (a, ts) =>
  `(${a.id}, '${a.code}', '${a.name}', ${WH_ID}, ${n(a.w * a.l)}, 1, 1, 0, '${a.remark}', '${a.layout}', ${n(a.x)}, 0.00, ${n(a.z)}, ${n(a.w)}, ${n(a.l)}, ${n(a.h)}, 0.00, 0.00, 0.00, '${a.color}', 0.12, 'CUBE', ${a.walls}, 1, 1, '${TENANT}', '0', 1, ${ts}, 1, ${ts})`;

const locCols = '(location_id, location_code, location_name, warehouse_id, area_id, area, max_loa, product_mixing, batch_mixing, enable_flag, frozen_flag, location_type, rack_id, layer, col, row_num, pos_x, pos_y, pos_z, width, height, location_depth, rotation_x, rotation_y, rotation_z, color_empty, color_occupied, color_locked, color_warning, opacity, show_border, border_color, border_width, show_label, label_position, label_size, status, occupancy_rate, tenant_id, del_flag, create_by, create_time, update_by, update_time)';
const locVals = (l, ts) => {
  const frozen = LOC_FROZEN.has(l.code) ? 1 : 0;
  const rack = l.rackId ? `'${l.rackId}'` : 'NULL';
  return `(${l.id}, '${l.code}', '${l.name}', ${WH_ID}, ${l.area.id}, ${n(l.w * l.d)}, ${n(l.area.loa, 0)}, 1, 1, 1, ${frozen}, '${l.type}', ${rack}, ${l.layer}, ${l.col}, ${l.row}, ${n(l.x)}, ${n(l.y)}, ${n(l.z)}, ${n(l.w)}, ${n(l.h)}, ${n(l.d)}, 0.00, 0.00, 0.00, '${LOC_STATUS_COLORS.EMPTY}', '${LOC_STATUS_COLORS.OCCUPIED}', '${LOC_STATUS_COLORS.LOCKED}', '${LOC_STATUS_COLORS.WARNING}', 0.85, 1, '#9aa5b1', 0.02, 0, 'TOP', ${n(l.area.labelSize)}, ${STATUS_INT[l.status]}, ${n(l.occupancy)}, '${TENANT}', '0', 1, ${ts}, 1, ${ts})`;
};

const stockCols = '(stock_id, material_id, material_type, material_code, material_name, material_spec, base_uom_name, batch_code, warehouse_id, warehouse_code, warehouse_name, area_id, area_code, area_name, location_id, location_code, location_name, stock_quantity, reserved_quantity, frozen_flag, recpt_date, unit_cost, total_cost, tenant_id, del_flag, version, create_by, create_time, update_by, update_time)';
const stockVals = (s, ts) =>
  `(${s.id}, ${s.m.id}, ${s.m.type}, '${s.m.code}', '${s.m.name}', '${s.m.spec}', '${s.m.uom}', '${s.batch}', ${WH_ID}, 'WH-3D-DEMO', '3D示例仓库', ${s.loc.area.id}, '${s.loc.area.code}', '${s.loc.area.name}', ${s.loc.id}, '${s.loc.code}', '${s.loc.name}', ${n(s.qty)}, ${n(s.rsv)}, ${s.frozen}, '${s.recpt}', ${n(s.m.cost, 6)}, ${n(s.qty * s.m.cost, 4)}, '${TENANT}', '0', 1, 1, ${ts}, 1, ${ts})`;

const locsOf = (area) => [...LOCATIONS.values()].filter((l) => l.area === area).sort((a, b) => a.id - b.id);

const seedSql = `-- ============================================================
-- 脚本名称: erp-wm-3d-demo-seed.sql
-- 用途: 3D 仓库可视化示例数据（仓库/库区/库位/库存）
-- 对应文档: doc/erp/plans/archive/2026-08-22-erp-wm-3d-warehouse-visual-upgrade-plan.md（PLAN-2026-006）
-- 适用数据库: ry-erp
-- 特性: SET NAMES utf8mb4 + 第 0 节清理段，可重复执行（先删本仓库旧演示数据再重插）
-- 租户: 000000；ID 段 9900000001+（与雪花 ID 不冲突）
-- 数据规模: 1 仓库 / 4 库区 / 104 库位 / 55 库存（四状态全覆盖 + 6 个多批次库位）
-- 生成方式: node erp-wm-3d-demo-datagen.mjs 生成，请勿手改（重生成会覆盖）
-- 日期: 2026-08-22
-- ============================================================
SET NAMES utf8mb4;

-- 坐标系说明（前端渲染：posX/posZ 为世界坐标中心，posY 为底面，前端对 y 补 h/2）：
--   A区 原料货架区(RACK) 库区中心(-8,0,0) 尺寸 12×8×4.4
--       3 排货架 RACK-A1/A2/A3，排中心 z = -2.5 / 0 / +2.5（排深 1.0，排间通道 1.5 米）
--       每排 5 列×4 层，库位 1.2×1.0×1.0；列中心 x = -8+(col-3)*1.2，层底 y = (layer-1)*1.0
--   B区 成品货架区(RACK) 库区中心(8,0,0) 尺寸 12×8×4.0
--       2 排货架 RACK-B1/B2，排中心 z = ±1.35（排深 1.2，排间通道 1.5 米）
--       每排 5 列×3 层，库位 1.2×1.2×1.2；列中心 x = 8+(col-3)*1.2，层底 y = (layer-1)*1.2
--   C区 拣选网格区(GRID) 库区中心(0,0,-8) 尺寸 10×6×2.0
--       5 列×2 行单层，库位 1.5×1.2×1.2（PICKING 拣货位）；列中心 x = (col-3)*1.5，行中心 z = -8±0.6
--   D区 散货堆放区(BULK) 库区中心(0,0,8) 尺寸 10×6×3.0
--       2×2 地堆位，库位 3.0×1.5×2.0；中心 x = ±1.5，z = 7 / 9
-- 状态口径（3D 聚合接口按库存实时计算，静态 status/occupancy_rate 列与之保持一致便于列表页展示）：
--   EMPTY=无库存 / OCCUPIED=有库存且预留<80% / WARNING=预留占比>=80% / LOCKED=库位冻结或任一库存冻结
-- ============================================================

-- ---------------------------------------------------
-- 0. 清理旧演示数据（仅删本仓库 ID 段，可重复执行）
-- ---------------------------------------------------
DELETE FROM \`wm_material_stock\` WHERE \`warehouse_id\` = ${WH_ID};
DELETE FROM \`wm_storage_location\` WHERE \`warehouse_id\` = ${WH_ID};
DELETE FROM \`wm_storage_area\` WHERE \`warehouse_id\` = ${WH_ID};
DELETE FROM \`wm_warehouse\` WHERE \`warehouse_id\` = ${WH_ID};

-- ---------------------------------------------------
-- 1. 示例仓库（1 个）
-- ---------------------------------------------------
INSERT INTO \`wm_warehouse\` ${whCols} VALUES ${whVals(TS_SEED)};

-- ---------------------------------------------------
-- 2. 示例库区（4 个：货架区×2 / 网格区 / 散货区）
--    show_walls=0 的货架区由前端绘制钢架结构，不再渲染半透明外壳
-- ---------------------------------------------------
INSERT INTO \`wm_storage_area\` ${areaCols} VALUES
${AREAS.map((a) => areaVals(a, TS_SEED)).join(',\n')};

-- ---------------------------------------------------
-- 3. 示例库位（104 个；show_label=0 保持默认视图干净，前端"标签"开关可开）
-- ---------------------------------------------------
${AREAS.map((a) => `-- ${a.name}（${locsOf(a).length} 个）\nINSERT INTO \`wm_storage_location\` ${locCols} VALUES\n${locsOf(a).map((l) => locVals(l, TS_SEED)).join(',\n')};`).join('\n\n')}

-- ---------------------------------------------------
-- 4. 示例库存（55 条 / 49 库位：OCCUPIED 35 / WARNING 8 / LOCKED 6，多批次库位 6 个）
-- ---------------------------------------------------
INSERT INTO \`wm_material_stock\` ${stockCols} VALUES
${stockRows.map((s) => stockVals(s, TS_SEED)).join(',\n')};

-- =====================================================
-- 验证查询（执行后人工核对）：
--   库位/库存规模：SELECT (SELECT COUNT(*) FROM wm_storage_location WHERE warehouse_id=${WH_ID}) locs,
--     (SELECT COUNT(*) FROM wm_material_stock WHERE warehouse_id=${WH_ID}) stocks;
--   四状态分布（应与 3D 工具栏图例一致）：
--     SELECT l.location_code, COALESCE(SUM(s.stock_quantity),0) qty, COALESCE(SUM(s.reserved_quantity),0) rsv,
--            MAX(s.frozen_flag) frz, l.frozen_flag loc_frz
--     FROM wm_storage_location l LEFT JOIN wm_material_stock s ON s.location_id=l.location_id AND s.del_flag='0'
--     WHERE l.warehouse_id=${WH_ID} GROUP BY l.location_id, l.location_code, l.frozen_flag HAVING qty>0 ORDER BY l.location_code;
--   多批次库位（应 6 个）：SELECT location_id, COUNT(*) c FROM wm_material_stock
--     WHERE warehouse_id=${WH_ID} GROUP BY location_id HAVING c>=2;
-- =====================================================
`;

/** ry-erp.sql 初始数据行：全列按 DDL 顺序（与文件既有风格一致） */
const ryerpWarehouseRow = () =>
  `INSERT INTO \`wm_warehouse\` VALUES (${WH_ID}, 'WH-3D-DEMO', '3D示例仓库', '厂区一号库·3D可视化示例', 800.00, 'admin', 1, '3D 仓库可视化示例数据（PLAN-2026-006）', 1, NULL, NULL, NULL, NULL, '${TENANT}', NULL, '0', 1, ${TS_INIT}, 1, ${TS_INIT}, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);`;

const ryerpAreaRow = (a) =>
  `INSERT INTO \`wm_storage_area\` VALUES (${a.id}, '${a.code}', '${a.name}', ${WH_ID}, ${n(a.w * a.l)}, 1, 1, 0, '${a.remark}', NULL, NULL, 0, 0, '${TENANT}', NULL, '0', 1, ${TS_INIT}, 1, ${TS_INIT}, '${a.layout}', ${n(a.x)}, 0.00, ${n(a.z)}, ${n(a.w)}, ${n(a.l)}, ${n(a.h)}, 0.00, 0.00, 0.00, '${a.color}', 0.12, 'CUBE', NULL, ${a.walls}, 1, 1, NULL);`;

const ryerpLocRow = (l) => {
  const frozen = LOC_FROZEN.has(l.code) ? 1 : 0;
  return `INSERT INTO \`wm_storage_location\` VALUES (${l.id}, '${l.code}', '${l.name}', ${WH_ID}, ${l.area.id}, NULL, ${n(l.w * l.d)}, ${n(l.area.loa, 0)}, NULL, NULL, NULL, 1, 1, 1, ${frozen}, '', NULL, NULL, 0, 0, '${TENANT}', NULL, '0', 1, ${TS_INIT}, 1, ${TS_INIT}, '${l.type}', ${l.rackId ? `'${l.rackId}'` : 'NULL'}, ${l.layer}, ${l.col}, ${l.row}, ${n(l.x)}, ${n(l.y)}, ${n(l.z)}, ${n(l.w)}, ${n(l.h)}, ${n(l.d)}, 0.00, 0.00, 0.00, '${LOC_STATUS_COLORS.EMPTY}', '${LOC_STATUS_COLORS.OCCUPIED}', '${LOC_STATUS_COLORS.LOCKED}', '${LOC_STATUS_COLORS.WARNING}', 0.85, 1, '#9aa5b1', 0.02, 0, 'TOP', ${n(l.area.labelSize)}, ${STATUS_INT[l.status]}, ${n(l.occupancy)}, NULL, NULL, NULL);`;
};

const ryerpStockRow = (s) =>
  `INSERT INTO \`wm_material_stock\` VALUES (${s.id}, NULL, ${s.m.id}, ${s.m.type}, '${s.m.code}', '${s.m.name}', '${s.m.spec}', NULL, '${s.m.uom}', NULL, '${s.batch}', ${WH_ID}, 'WH-3D-DEMO', '3D示例仓库', ${s.loc.area.id}, '${s.loc.area.code}', '${s.loc.area.name}', ${s.loc.id}, '${s.loc.code}', '${s.loc.name}', NULL, NULL, NULL, NULL, ${n(s.qty)}, ${n(s.rsv)}, NULL, NULL, NULL, '${s.recpt}', NULL, ${s.frozen}, NULL, NULL, 0, 0, '${TENANT}', NULL, '0', NULL, 1, 1, ${TS_INIT}, 1, ${TS_INIT}, ${n(s.m.cost, 6)}, ${n(s.qty * s.m.cost, 4)}, NULL, NULL);`;

// ---------------------------------------------------------------
// 列数/值数自检（显式列名版按列清单比对；全列版按 DDL 列数比对），防止错位
// ---------------------------------------------------------------
const countList = (s) => s.replace(/^\(|\)$/g, '').split(',').length;
const countRow = (row) => row.replace(/^\(/, '').replace(/\);?$/, '').replace(/'[^']*'/g, "''").split(',').length;
const assertCount = (label, expected, row) => {
  const c = countRow(row);
  if (c !== expected) throw new Error(`${label}: 期望 ${expected} 个值, 实际 ${c}`);
};
assertCount('seed.wm_warehouse', countList(whCols), whVals(TS_SEED));
assertCount('seed.wm_storage_area', countList(areaCols), areaVals(AREAS[0], TS_SEED));
assertCount('seed.wm_storage_location', countList(locCols), locVals(LOCATIONS.get('A1-L1C1'), TS_SEED));
assertCount('seed.wm_material_stock', countList(stockCols), stockVals(stockRows[0], TS_SEED));
assertCount('init.wm_warehouse', 28, ryerpWarehouseRow());
assertCount('init.wm_storage_area', 38, ryerpAreaRow(AREAS[0]));
assertCount('init.wm_storage_location', 57, ryerpLocRow(LOCATIONS.get('A1-L1C1')));
assertCount('init.wm_material_stock', 49, ryerpStockRow(stockRows[0]));

// ---------------------------------------------------------------
// 写文件：种子脚本 + ry-erp.sql 就地替换
// ---------------------------------------------------------------
writeFileSync(SEED_PATH, seedSql, 'utf8');

/** 在行数组中找到连续匹配的前缀块并替换；返回替换行数 */
const replaceBlock = (lines, prefix, newLines, expectCounts, label) => {
  const idx = lines.findIndex((ln) => ln.startsWith(prefix));
  if (idx < 0) throw new Error(`ry-erp.sql 中未找到 ${label} 演示数据块`);
  let end = idx;
  while (end + 1 < lines.length && lines[end + 1].startsWith(prefix)) end++;
  const count = end - idx + 1;
  if (!expectCounts.includes(count)) throw new Error(`${label} 演示块行数 ${count} 不在预期 ${expectCounts} 内，中止替换`);
  lines.splice(idx, count, ...newLines);
  return count;
};

const ryerp = readFileSync(RYERP_PATH, 'utf8');
const lines = ryerp.split('\n');
const oldWh = replaceBlock(lines, 'INSERT INTO `wm_warehouse` VALUES (9900000001,', [ryerpWarehouseRow()], [1], 'wm_warehouse');
const oldArea = replaceBlock(lines, 'INSERT INTO `wm_storage_area` VALUES (990000', AREAS.map(ryerpAreaRow), [2, 4], 'wm_storage_area');
const oldLoc = replaceBlock(lines, 'INSERT INTO `wm_storage_location` VALUES (990000', [...AREAS.flatMap((a) => locsOf(a).map(ryerpLocRow))], [18, 104], 'wm_storage_location');
const oldStock = replaceBlock(lines, 'INSERT INTO `wm_material_stock` VALUES (990000', stockRows.map(ryerpStockRow), [10, 55], 'wm_material_stock');
writeFileSync(RYERP_PATH, lines.join('\n'), 'utf8');

// ---------------------------------------------------------------
// 汇总
// ---------------------------------------------------------------
const byStatus = { EMPTY: 0, OCCUPIED: 0, WARNING: 0, LOCKED: 0 };
for (const l of LOCATIONS.values()) byStatus[l.status]++;
console.log('=== 3D 示例仓库数据生成完成 ===');
console.log(`库区 ${AREAS.length} / 库位 ${LOCATIONS.size} / 库存 ${stockRows.length} 条`);
console.log(`状态分布: OCCUPIED=${byStatus.OCCUPIED} WARNING=${byStatus.WARNING} LOCKED=${byStatus.LOCKED} EMPTY=${byStatus.EMPTY}`);
const multi = [...stockAgg.entries()].filter(([, a]) => a.count >= 2);
console.log(`多批次库位: ${multi.length} 个 (${multi.map(([c]) => c).join(', ')})`);
console.log(`种子脚本: ${SEED_PATH}`);
console.log(`ry-erp.sql 替换: 仓库 ${oldWh} 行 / 库区 ${oldArea}→${AREAS.length} / 库位 ${oldLoc}→${LOCATIONS.size} / 库存 ${oldStock}→${stockRows.length}`);
