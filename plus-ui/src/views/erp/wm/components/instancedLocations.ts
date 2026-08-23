import * as THREE from 'three';
import type { WarehouseArea3dVO, WarehouseLocation3dVO } from '@/api/erp/wm/warehouse/types';

/** 库位着色模式：status 按状态 / area 按库区 / occupancy 按占用率 */
export type LocationColorMode = 'status' | 'area' | 'occupancy';

/** 库位条目：instanceId 与 entries 数组下标严格对齐（拾取入口） */
export interface LocationEntry {
  loc: WarehouseLocation3dVO;
  area: WarehouseArea3dVO;
  /** 库位中心（底面 y + 高/2，与旧版 Mesh 语义一致） */
  center: THREE.Vector3;
  /** 库位尺寸（宽×高×深） */
  size: THREE.Vector3;
  /** 库位旋转四元数（rotationX/Y/Z 角度值转弧度） */
  quaternion: THREE.Quaternion;
  /** 货物填充高度比（空位 0 隐藏；占用 0.15~0.95 按库存量归一化） */
  fill: number;
}

export interface InstancedLocations {
  /** 挂入 scene 的根组 */
  root: THREE.Group;
  /** 拾取目标（库位格实例网格），需与建筑遮挡网格一起参与 raycast */
  pickTarget: THREE.InstancedMesh | null;
  /** 库位条目（instanceId 下标） */
  entries: LocationEntry[];
  /** 按当前着色模式重设货物颜色（仅改 instanceColor，不重建矩阵） */
  recolor(mode: LocationColorMode): void;
  /** 状态过滤：statuses 为 null 清除过滤（恢复全部） */
  setStatusFilter(statuses: string[] | null): void;
  /** 库区显隐：隐藏该区全部库位实例并重建轮廓线 */
  setAreaVisible(areaId: number, visible: boolean): void;
  /** 悬停高亮：index 传 null 清除 */
  highlight(index: number | null): void;
  dispose(): void;
}

// 默认状态色（库位未配置时兜底，与图例一致）
export const LOCATION_DEFAULT_COLORS: Record<string, number> = {
  EMPTY: 0x909399,
  OCCUPIED: 0x409eff,
  WARNING: 0xe6a23c,
  LOCKED: 0xf56c6c
};

// 货物盒占库位格的比例（保留边距，露出库位格与钢架）
const GOODS_MARGIN = 0.85;
// 货物填充高度区间：过低看不见，过高顶满库位格
const FILL_MIN = 0.15;
const FILL_MAX = 0.95;
// 托盘厚度（米）
const PALLET_H = 0.1;
// 轮廓线缺省色（borderColor 解析失败时兜底）
const EDGE_FALLBACK = 0x9aa5b1;

const num = (v: unknown, dft: number): number => {
  const n = Number(v);
  return v == null || Number.isNaN(n) ? dft : n;
};

const toRad = (deg?: number) => (deg ? THREE.MathUtils.degToRad(deg) : 0);

const parseColor = (v: unknown, fallback: number): number => {
  if (!v) return fallback;
  try {
    return new THREE.Color(v as string).getHex();
  } catch {
    return fallback;
  }
};

/** 占用率渐变色：0→绿, 1→红（按库存量归一化到仓库最大值） */
const occupancyGradient = (rate: number): number => {
  const hue = (1 - Math.min(Math.max(rate, 0), 1)) * 120; // 120(绿)→0(红)
  return new THREE.Color().setHSL(hue / 360, 0.7, 0.5).getHex();
};

/** 库区色：优先 area.color，缺省按 areaId 哈希取色 */
const areaColorHex = (area: WarehouseArea3dVO | undefined): number => {
  if (area?.color) {
    const c = parseColor(area.color, NaN);
    if (!Number.isNaN(c)) return c;
  }
  const hue = ((area?.areaId || 0) * 47) % 360;
  return new THREE.Color().setHSL(hue / 360, 0.6, 0.55).getHex();
};

/** 库位状态色：优先库位配置四色，缺省默认色 */
const statusColor = (loc: WarehouseLocation3dVO, status: string): number => {
  const map: Record<string, unknown> = {
    EMPTY: loc.colorEmpty,
    OCCUPIED: loc.colorOccupied,
    WARNING: loc.colorWarning,
    LOCKED: loc.colorLocked
  };
  const c = parseColor(map[status], NaN);
  return Number.isNaN(c) ? (LOCATION_DEFAULT_COLORS[status] ?? 0x909399) : c;
};

// 单位立方体 8 角点与 12 条棱（轮廓线合并用）
const CUBE_CORNERS: THREE.Vector3[] = [
  new THREE.Vector3(-0.5, -0.5, -0.5),
  new THREE.Vector3(0.5, -0.5, -0.5),
  new THREE.Vector3(0.5, 0.5, -0.5),
  new THREE.Vector3(-0.5, 0.5, -0.5),
  new THREE.Vector3(-0.5, -0.5, 0.5),
  new THREE.Vector3(0.5, -0.5, 0.5),
  new THREE.Vector3(0.5, 0.5, 0.5),
  new THREE.Vector3(-0.5, 0.5, 0.5)
];
const CUBE_EDGES: [number, number][] = [
  [0, 1],
  [1, 2],
  [2, 3],
  [3, 0],
  [4, 5],
  [5, 6],
  [6, 7],
  [7, 4],
  [0, 4],
  [1, 5],
  [2, 6],
  [3, 7]
];

/**
 * InstancedMesh 库位渲染器（写实升级版）：
 *  - cells：库位"幽灵格"（拾取目标，极淡半透明，空位呈现格子轮廓感）
 *  - goods：货物盒（可见主体，按库存量填充高度，instanceColor 承载三种着色模式）
 *  - pallets：托盘薄板（仅占用库位）
 *  - edges：全部库位轮廓合并为单个 LineSegments（vertexColors 按库位 borderColor）
 *  - highlight：共享高亮盒（悬停时移动到对应库位，替代逐 Mesh emissive）
 *
 * 隐藏实现：实例矩阵 scale 置 0（零缩放实例不产生 raycast 命中），轮廓线按可见集重建。
 */
export function createInstancedLocations(areas: WarehouseArea3dVO[], initialMode: LocationColorMode): InstancedLocations {
  const root = new THREE.Group();
  let colorMode = initialMode;

  // —— 收集库位条目（第一遍取仓库最大库存量，第二遍算填充高度）——
  const entries: LocationEntry[] = [];
  let maxQty = 0;
  for (const area of areas) {
    for (const loc of area.locations || []) {
      const qty = Number(loc.stockQuantity) || 0;
      if (qty > maxQty) maxQty = qty;
      const w = num(loc.width, 1);
      const h = num(loc.height, 1);
      const d = num(loc.locationDepth, 1);
      const x = num(loc.posX, 0);
      const y = num(loc.posY, 0);
      const z = num(loc.posZ, 0);
      entries.push({
        loc,
        area,
        center: new THREE.Vector3(x, y + h / 2, z),
        size: new THREE.Vector3(w, h, d),
        quaternion: new THREE.Quaternion().setFromEuler(new THREE.Euler(toRad(loc.rotationX), toRad(loc.rotationY), toRad(loc.rotationZ))),
        fill: 0
      });
    }
  }
  for (const e of entries) {
    const occupied = (e.loc.status || 'EMPTY') !== 'EMPTY';
    if (!occupied) continue;
    const qty = Number(e.loc.stockQuantity) || 0;
    const rate = maxQty > 0 ? qty / maxQty : 1;
    e.fill = THREE.MathUtils.clamp(rate, FILL_MIN, FILL_MAX);
  }

  // —— 三层实例网格（共享单位立方体，矩阵承载尺寸/位置/旋转）——
  const count = entries.length;
  const unitBox = new THREE.BoxGeometry(1, 1, 1);
  const cellMat = new THREE.MeshStandardMaterial({ color: 0xc4ccd6, transparent: true, opacity: 0.07, roughness: 1, depthWrite: false });
  const goodsMat = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.55, metalness: 0.05 });
  const palletMat = new THREE.MeshStandardMaterial({ color: 0x8a6d4f, roughness: 0.9 });

  let cells: THREE.InstancedMesh | null = null;
  let goods: THREE.InstancedMesh | null = null;
  let pallets: THREE.InstancedMesh | null = null;
  let edgeLines: THREE.LineSegments | null = null;
  const edgeMat = new THREE.LineBasicMaterial({ vertexColors: true });

  if (count > 0) {
    cells = new THREE.InstancedMesh(unitBox, cellMat, count);
    goods = new THREE.InstancedMesh(unitBox, goodsMat, count);
    pallets = new THREE.InstancedMesh(unitBox, palletMat, count);
    for (const im of [cells, goods, pallets]) {
      im.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
      root.add(im);
    }
    goods.castShadow = true;
    goods.receiveShadow = true;
    pallets.castShadow = true;
  }

  // —— 悬停高亮（共享一个高亮盒，随悬停移动）——
  const highlightMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.18 });
  const highlightBox = new THREE.Mesh(unitBox, highlightMat);
  const highlightEdges = new THREE.LineSegments(new THREE.EdgesGeometry(unitBox), new THREE.LineBasicMaterial({ color: 0xffffff }));
  const highlightGroup = new THREE.Group();
  highlightGroup.add(highlightBox, highlightEdges);
  highlightGroup.visible = false;
  root.add(highlightGroup);

  // —— 可见性状态：库区显隐 + 状态过滤 ——
  const areaVisible = new Map<number, boolean>();
  let statusFilter: string[] | null = null;

  const tmpMatrix = new THREE.Matrix4();
  const zeroMatrix = new THREE.Matrix4().makeScale(0, 0, 0);
  const tmpScale = new THREE.Vector3();
  const tmpPos = new THREE.Vector3();
  const tmpColor = new THREE.Color();

  /** 是否参与渲染（库区隐藏或状态过滤均整格隐藏） */
  const isVisible = (e: LocationEntry): boolean =>
    areaVisible.get(e.area.areaId) !== false && (!statusFilter || statusFilter.includes(e.loc.status || 'EMPTY'));

  /** 重算全部实例矩阵（库位格/货物/托盘）并重建轮廓线 */
  const applyVisibility = () => {
    if (!cells || !goods || !pallets) return;
    entries.forEach((e, i) => {
      const show = isVisible(e);
      // 库位格
      tmpMatrix.compose(e.center, e.quaternion, e.size);
      cells!.setMatrixAt(i, show ? tmpMatrix : zeroMatrix);
      // 货物盒（底对齐，填充高度按 fill）
      const gh = e.size.y * e.fill;
      if (show && gh > 0) {
        tmpScale.set(e.size.x * GOODS_MARGIN, gh, e.size.z * GOODS_MARGIN);
        tmpPos.set(e.center.x, e.center.y - e.size.y / 2 + gh / 2, e.center.z);
        tmpMatrix.compose(tmpPos, e.quaternion, tmpScale);
      } else {
        tmpMatrix.copy(zeroMatrix);
      }
      goods!.setMatrixAt(i, tmpMatrix);
      // 托盘（贴底薄板，仅占用库位）
      if (show && e.fill > 0) {
        tmpScale.set(e.size.x * GOODS_MARGIN, Math.min(PALLET_H, e.size.y * 0.5), e.size.z * GOODS_MARGIN);
        tmpPos.set(e.center.x, e.center.y - e.size.y / 2 + tmpScale.y / 2, e.center.z);
        tmpMatrix.compose(tmpPos, e.quaternion, tmpScale);
      } else {
        tmpMatrix.copy(zeroMatrix);
      }
      pallets!.setMatrixAt(i, tmpMatrix);
    });
    cells.instanceMatrix.needsUpdate = true;
    goods.instanceMatrix.needsUpdate = true;
    pallets.instanceMatrix.needsUpdate = true;
    cells.computeBoundingSphere();
    rebuildEdges();
  };

  /** 轮廓线：可见且 showBorder 的库位合并为单个 LineSegments（含旋转） */
  const rebuildEdges = () => {
    if (edgeLines) {
      edgeLines.geometry.dispose();
      root.remove(edgeLines);
      edgeLines = null;
    }
    const positions: number[] = [];
    const colors: number[] = [];
    const v = new THREE.Vector3();
    for (const e of entries) {
      if (!isVisible(e) || Number(e.loc.showBorder ?? 1) !== 1) continue;
      const color = parseColor(e.loc.borderColor, EDGE_FALLBACK);
      tmpMatrix.compose(e.center, e.quaternion, e.size);
      for (const [a, b] of CUBE_EDGES) {
        v.copy(CUBE_CORNERS[a]).applyMatrix4(tmpMatrix);
        positions.push(v.x, v.y, v.z);
        colors.push(((color >> 16) & 255) / 255, ((color >> 8) & 255) / 255, (color & 255) / 255);
        v.copy(CUBE_CORNERS[b]).applyMatrix4(tmpMatrix);
        positions.push(v.x, v.y, v.z);
        colors.push(((color >> 16) & 255) / 255, ((color >> 8) & 255) / 255, (color & 255) / 255);
      }
    }
    if (positions.length === 0) return;
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    geo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
    edgeLines = new THREE.LineSegments(geo, edgeMat);
    root.add(edgeLines);
  };

  const computeColor = (e: LocationEntry): number => {
    if (colorMode === 'area') return areaColorHex(e.area);
    if (colorMode === 'occupancy') {
      const qty = Number(e.loc.stockQuantity) || 0;
      return qty <= 0 ? LOCATION_DEFAULT_COLORS.EMPTY : occupancyGradient(maxQty > 0 ? qty / maxQty : 1);
    }
    return statusColor(e.loc, e.loc.status || 'EMPTY');
  };

  // —— 初始构建 ——
  applyVisibility();
  if (goods) {
    entries.forEach((e, i) => goods!.setColorAt(i, tmpColor.setHex(computeColor(e))));
    if (goods.instanceColor) goods.instanceColor.needsUpdate = true;
  }

  return {
    root,
    pickTarget: cells,
    entries,
    recolor(mode: LocationColorMode) {
      colorMode = mode;
      if (!goods) return;
      entries.forEach((e, i) => goods!.setColorAt(i, tmpColor.setHex(computeColor(e))));
      if (goods.instanceColor) goods.instanceColor.needsUpdate = true;
    },
    setStatusFilter(statuses: string[] | null) {
      statusFilter = statuses && statuses.length > 0 ? statuses : null;
      applyVisibility();
    },
    setAreaVisible(areaId: number, visible: boolean) {
      areaVisible.set(areaId, visible);
      applyVisibility();
    },
    highlight(index: number | null) {
      if (index === null || index < 0 || index >= entries.length) {
        highlightGroup.visible = false;
        return;
      }
      const e = entries[index];
      tmpScale.copy(e.size).multiplyScalar(1.04);
      highlightGroup.position.copy(e.center);
      highlightGroup.quaternion.copy(e.quaternion);
      highlightGroup.scale.copy(tmpScale);
      highlightGroup.visible = true;
    },
    dispose() {
      highlightGroup.visible = false;
      if (edgeLines) {
        edgeLines.geometry.dispose();
        root.remove(edgeLines);
        edgeLines = null;
      }
      root.remove(highlightGroup);
      highlightEdges.geometry.dispose();
      highlightMat.dispose();
      (highlightEdges.material as THREE.Material).dispose();
      for (const im of [cells, goods, pallets]) {
        if (im) root.remove(im);
      }
      unitBox.dispose();
      cellMat.dispose();
      goodsMat.dispose();
      palletMat.dispose();
      edgeMat.dispose();
    }
  };
}
