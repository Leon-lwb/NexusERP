import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';
import type { WarehouseArea3dVO, WarehouseLocation3dVO } from '@/api/erp/wm/warehouse/types';

/** 一排货架的结构包围盒（由该排库位实际坐标推导） */
export interface RackBounds {
  xMin: number;
  xMax: number;
  zMin: number;
  zMax: number;
  topY: number;
}

/** 同库区按 rackId 分组（null → __default__，与 rackLayout.ts 口径一致） */
export function groupLocationsByRack(locations: WarehouseLocation3dVO[]): Map<string, WarehouseLocation3dVO[]> {
  const groups = new Map<string, WarehouseLocation3dVO[]>();
  for (const loc of locations || []) {
    const key = loc.rackId || '__default__';
    let g = groups.get(key);
    if (!g) {
      g = [];
      groups.set(key, g);
    }
    g.push(loc);
  }
  return groups;
}

/** 由库位实际坐标计算单排货架包围盒 */
export function rackBounds(locs: WarehouseLocation3dVO[]): RackBounds | null {
  if (!locs || locs.length === 0) return null;
  const num = (v: unknown, dft: number) => {
    const n = Number(v);
    return v == null || Number.isNaN(n) ? dft : n;
  };
  const b: RackBounds = { xMin: Infinity, xMax: -Infinity, zMin: Infinity, zMax: -Infinity, topY: 0 };
  for (const l of locs) {
    const w = num(l.width, 1);
    const d = num(l.locationDepth, 1);
    const x = num(l.posX, 0);
    const y = num(l.posY, 0);
    const z = num(l.posZ, 0);
    b.xMin = Math.min(b.xMin, x - w / 2);
    b.xMax = Math.max(b.xMax, x + w / 2);
    b.zMin = Math.min(b.zMin, z - d / 2);
    b.zMax = Math.max(b.zMax, z + d / 2);
    b.topY = Math.max(b.topY, y + num(l.height, 1));
  }
  return b;
}

export interface RackFrameBuild {
  /** 每个含货架的库区一个 Group（key: areaId），供库区显隐 */
  groups: Map<number, THREE.Group>;
  /** 遮挡拾取网格（参与 raycast 但不响应业务交互，"看得到才点得到"） */
  pickMeshes: THREE.Mesh[];
  dispose(): void;
}

// 结构件尺寸（米）
const POST_SIZE = 0.07; // 立柱截面
const BEAM_H = 0.06; // 横梁高
const BEAM_D = 0.06; // 横梁深
const PLATE_SIZE = 0.16; // 底脚板边长
const PLATE_H = 0.02; // 底脚板厚
const OVER = 0.03; // 结构相对库位外沿的外扩
const GROUND_LAYER_MAX = 0.15; // 层底低于该值视为地面层，不设承重横梁（落地存放）

/**
 * 货架钢结构建模：对 layoutType=RACK 的库区，按 rackId 分组，
 * 由每排库位实际坐标（autoLayoutRack 排版后）推导立柱/横梁/底脚板。
 * 每库区合并为 2 个 Mesh（立柱色 + 横梁色），控制 draw call。
 */
export function buildRackFrames(areas: WarehouseArea3dVO[]): RackFrameBuild {
  const groups = new Map<number, THREE.Group>();
  const pickMeshes: THREE.Mesh[] = [];
  const postMat = new THREE.MeshStandardMaterial({ color: 0x4a6fa5, metalness: 0.45, roughness: 0.45 });
  const beamMat = new THREE.MeshStandardMaterial({ color: 0xe8963c, metalness: 0.35, roughness: 0.5 });

  const boxAt = (sx: number, sy: number, sz: number, x: number, y: number, z: number): THREE.BoxGeometry => {
    const g = new THREE.BoxGeometry(sx, sy, sz);
    g.translate(x, y, z);
    return g;
  };

  for (const area of areas) {
    if ((area.layoutType || '').toUpperCase() !== 'RACK') continue;
    const locs = area.locations || [];
    if (locs.length === 0) continue;

    const postGeos: THREE.BoxGeometry[] = [];
    const beamGeos: THREE.BoxGeometry[] = [];

    for (const rackLocs of groupLocationsByRack(locs).values()) {
      const b = rackBounds(rackLocs);
      if (!b || !isFinite(b.xMin)) continue;
      const num = (v: unknown, dft: number) => {
        const n = Number(v);
        return v == null || Number.isNaN(n) ? dft : n;
      };

      // 列中心去重排序 → 立柱定位（相邻列中线 + 端部外扩）
      const centers = [...new Set(rackLocs.map((l) => num(l.posX, 0)))].sort((a, c) => a - c);
      const w0 = num(rackLocs[0].width, 1);
      const xEdges: number[] = [];
      for (let i = 0; i < centers.length - 1; i++) xEdges.push((centers[i] + centers[i + 1]) / 2);
      xEdges.unshift(centers[0] - w0 / 2 - OVER);
      xEdges.push(centers[centers.length - 1] + w0 / 2 + OVER);

      const zPosts = [b.zMin - OVER, b.zMax + OVER];
      const rackW = xEdges[xEdges.length - 1] - xEdges[0];
      const rackCx = (xEdges[0] + xEdges[xEdges.length - 1]) / 2;
      const postH = b.topY + BEAM_H;

      // 立柱 + 底脚板
      for (const x of xEdges) {
        for (const z of zPosts) {
          postGeos.push(boxAt(POST_SIZE, postH, POST_SIZE, x, postH / 2, z));
          postGeos.push(boxAt(PLATE_SIZE, PLATE_H, PLATE_SIZE, x, PLATE_H / 2, z));
        }
      }
      // 承重横梁：层底托梁（顶面贴合层底，货物/托盘落其上）；地面层不设
      const layers = [...new Set(rackLocs.map((l) => num(l.posY, 0)))].sort((a, c) => a - c);
      for (const z of zPosts) {
        for (const y of layers) {
          if (y < GROUND_LAYER_MAX) continue;
          beamGeos.push(boxAt(rackW, BEAM_H, BEAM_D, rackCx, y - BEAM_H / 2, z));
        }
        // 顶部封口横梁
        beamGeos.push(boxAt(rackW, BEAM_H, BEAM_D, rackCx, b.topY + BEAM_H / 2, z));
      }
    }

    if (postGeos.length === 0) continue;
    const group = new THREE.Group();
    const postGeo = mergeGeometries(postGeos, false);
    const beamGeo = mergeGeometries(beamGeos, false);
    postGeos.forEach((g) => g.dispose());
    beamGeos.forEach((g) => g.dispose());
    if (postGeo) {
      const mesh = new THREE.Mesh(postGeo, postMat);
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      group.add(mesh);
      pickMeshes.push(mesh);
    }
    if (beamGeo) {
      const mesh = new THREE.Mesh(beamGeo, beamMat);
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      group.add(mesh);
      pickMeshes.push(mesh);
    }
    groups.set(area.areaId, group);
  }

  return {
    groups,
    pickMeshes,
    dispose() {
      for (const group of groups.values()) {
        group.traverse((obj) => {
          if (obj instanceof THREE.Mesh) obj.geometry?.dispose();
        });
        group.clear();
      }
      groups.clear();
      pickMeshes.length = 0;
      postMat.dispose();
      beamMat.dispose();
    }
  };
}
