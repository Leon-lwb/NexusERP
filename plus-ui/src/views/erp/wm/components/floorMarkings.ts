import * as THREE from 'three';
import { groupLocationsByRack, rackBounds } from './rackFrame';
import type { WarehouseArea3dVO } from '@/api/erp/wm/warehouse/types';

export interface FloorMarkingBuild {
  /** 每个库区一个 Group（key: areaId），供库区显隐联动 */
  groups: Map<number, THREE.Group>;
  dispose(): void;
}

// 地面各层 y 偏移（米），错层避免与地面网格/彼此共面闪烁
const Y_PATCH = 0.005;
const Y_BOUNDARY = 0.008;
const Y_AISLE = 0.008;
// 通道判定：相邻货架排间隙不小于该值才画通道线
const MIN_AISLE_GAP = 0.8;
// 通道虚线尺寸（米）
const AISLE_DASH_LEN = 0.35;
const AISLE_DASH_STEP = 0.65;
const AISLE_COLOR = 0xe6c34a;

const num = (v: unknown, dft: number): number => {
  const n = Number(v);
  return v == null || Number.isNaN(n) ? dft : n;
};

const parseColor = (v: unknown, fallback: number): number => {
  if (!v) return fallback;
  try {
    return new THREE.Color(v as string).getHex();
  } catch {
    return fallback;
  }
};

/**
 * 库区地面标线：地面色块（gridVisible 控制）+ 库区边界线 + 货架排间通道黄色虚线。
 * 通道线由相邻货架排（rackId 分组）包围盒间隙自动推导。
 */
export function buildFloorMarkings(areas: WarehouseArea3dVO[]): FloorMarkingBuild {
  const groups = new Map<number, THREE.Group>();
  const materials: THREE.Material[] = [];
  const geometries: THREE.BufferGeometry[] = [];

  for (const area of areas) {
    const w = num(area.areaWidth, 0);
    const l = num(area.areaLength, 0);
    if (w <= 0 || l <= 0) continue;
    const group = new THREE.Group();
    const x = num(area.posX, 0);
    const z = num(area.posZ, 0);
    const color = parseColor(area.color, 0x409eff);

    // 地面色块
    if (Number(area.gridVisible ?? 1) === 1) {
      const patchGeo = new THREE.PlaneGeometry(w, l);
      const patchMat = new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.16, depthWrite: false });
      geometries.push(patchGeo);
      materials.push(patchMat);
      const patch = new THREE.Mesh(patchGeo, patchMat);
      patch.rotation.x = -Math.PI / 2;
      patch.position.set(x, Y_PATCH, z);
      group.add(patch);
    }

    // 库区边界线
    const hx = w / 2;
    const hz = l / 2;
    const boundGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(x - hx, Y_BOUNDARY, z - hz),
      new THREE.Vector3(x + hx, Y_BOUNDARY, z - hz),
      new THREE.Vector3(x + hx, Y_BOUNDARY, z + hz),
      new THREE.Vector3(x - hx, Y_BOUNDARY, z + hz)
    ]);
    const boundMat = new THREE.LineBasicMaterial({ color });
    geometries.push(boundGeo);
    materials.push(boundMat);
    group.add(new THREE.LineLoop(boundGeo, boundMat));

    // 货架排间通道虚线（仅 RACK 布局且多排时存在）
    if ((area.layoutType || '').toUpperCase() === 'RACK') {
      const rackList = [...groupLocationsByRack(area.locations || []).values()]
        .map((locs) => ({ locs, b: rackBounds(locs) }))
        .filter((r) => r.b)
        .sort((a, b) => (a.b!.zMin + a.b!.zMax) / 2 - (b.b!.zMin + b.b!.zMax) / 2);
      const xMin = Math.min(...rackList.map((r) => r.b!.xMin)) - 0.5;
      const xMax = Math.max(...rackList.map((r) => r.b!.xMax)) + 0.5;
      const aislePoints: THREE.Vector3[] = [];
      for (let i = 0; i < rackList.length - 1; i++) {
        const gapZ = (rackList[i].b!.zMax + rackList[i + 1].b!.zMin) / 2;
        const gap = rackList[i + 1].b!.zMin - rackList[i].b!.zMax;
        if (gap < MIN_AISLE_GAP) continue;
        for (let px = xMin; px + AISLE_DASH_LEN <= xMax; px += AISLE_DASH_STEP) {
          aislePoints.push(new THREE.Vector3(px, Y_AISLE, gapZ), new THREE.Vector3(px + AISLE_DASH_LEN, Y_AISLE, gapZ));
        }
      }
      if (aislePoints.length > 0) {
        const aisleGeo = new THREE.BufferGeometry().setFromPoints(aislePoints);
        const aisleMat = new THREE.LineBasicMaterial({ color: AISLE_COLOR });
        geometries.push(aisleGeo);
        materials.push(aisleMat);
        group.add(new THREE.LineSegments(aisleGeo, aisleMat));
      }
    }

    if (group.children.length > 0) groups.set(area.areaId, group);
  }

  return {
    groups,
    dispose() {
      geometries.forEach((g) => g.dispose());
      materials.forEach((m) => m.dispose());
      geometries.length = 0;
      materials.length = 0;
      groups.forEach((g) => g.clear());
      groups.clear();
    }
  };
}
