import type { WarehouseArea3dVO, WarehouseLocation3dVO } from '@/api/erp/wm/warehouse/types';

const num = (v: unknown, dft: number): number => {
  const n = Number(v);
  return v == null || Number.isNaN(n) ? dft : n;
};

/** 库位是否已手填坐标（任一轴非 0 视为手填，优先于自动排版） */
const hasManualCoord = (loc: WarehouseLocation3dVO): boolean => num(loc.posX, 0) !== 0 || num(loc.posY, 0) !== 0 || num(loc.posZ, 0) !== 0;

/**
 * 货架自动排版：同库区内按 rackId 分组，layer/col/row → (y,x,z)
 *  - col  → X（列，水平宽度方向，组内居中）
 *  - layer→ Y（层，垂直方向，从 0 起）
 *  - row  → Z（排，纵深方向，组内居中）
 *  - 多个 rackId 沿 Z 方向并列，间以通道（AISLE）
 *
 * 坐标系与前端渲染一致：posX/posZ 为世界坐标中心，posY 为底面（前端对 y 补 h/2）。
 * 居中公式 pos = (idx - (max+1)/2) * cell 与演示种子手填坐标一致，可互验。
 *
 * 手填坐标（任一轴非 0）优先，自动排版仅补缺省。cellSize 默认从库位尺寸推导。
 */
export function autoLayoutRack(locations: WarehouseLocation3dVO[], area: WarehouseArea3dVO, cellSize?: { w?: number; h?: number; d?: number }): void {
  if (!locations || locations.length === 0) return;
  const ox = num(area.posX, 0);
  const oy = num(area.posY, 0);
  const oz = num(area.posZ, 0);
  const w = cellSize?.w ?? num(locations[0].width, 1.2);
  const h = cellSize?.h ?? num(locations[0].height, 1.2);
  const d = cellSize?.d ?? num(locations[0].locationDepth, 1.2);
  const AISLE = 1.5; // 货架间通道（米）

  // 按 rackId 分组（null → __default__）
  const groups = new Map<string, WarehouseLocation3dVO[]>();
  for (const loc of locations) {
    const key = loc.rackId || '__default__';
    let g = groups.get(key);
    if (!g) {
      g = [];
      groups.set(key, g);
    }
    g.push(loc);
  }

  let rackIdx = 0;
  for (const [, locs] of groups) {
    let maxCol = 1;
    let maxLayer = 1;
    let maxRow = 1;
    for (const loc of locs) {
      maxCol = Math.max(maxCol, num(loc.col, 1));
      maxLayer = Math.max(maxLayer, num(loc.layer, 1));
      maxRow = Math.max(maxRow, num(loc.rowNum, 1));
    }
    const rackOffsetZ = rackIdx * (maxRow * d + AISLE);
    for (const loc of locs) {
      if (hasManualCoord(loc)) continue;
      const col = num(loc.col, 1);
      const layer = num(loc.layer, 1);
      const row = num(loc.rowNum, 1);
      loc.posX = ox + (col - (maxCol + 1) / 2) * w;
      loc.posY = oy + (layer - 1) * h;
      loc.posZ = oz + rackOffsetZ + (row - (maxRow + 1) / 2) * d;
    }
    rackIdx++;
  }
}
