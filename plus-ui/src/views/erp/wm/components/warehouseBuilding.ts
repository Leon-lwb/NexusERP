import * as THREE from 'three';

/** 内容包围盒（与 3d.vue 中 bounds 同构） */
export interface ContentBounds {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
  minZ: number;
  maxZ: number;
}

/** 仓库厂房构建结果 */
export interface WarehouseBuilding {
  /** 整栋建筑（地台+墙体+屋顶），挂入 scene */
  group: THREE.Group;
  /** 人字屋顶网格（单 Mesh），供俯视自动隐藏 */
  roof: THREE.Mesh;
  /** 参与遮挡拾取的建筑网格（不含库位 locationMeshes） */
  pickMeshes: THREE.Mesh[];
  /** 建筑外轮廓包围盒（含地台外挑与屋顶起坡），用于取景 */
  envelope: ContentBounds;
}

// —— 尺寸可调常量（米 / 弧度）——
const MARGIN = 2; // 占地边距
const MIN_WALL_H = 4; // 最低墙高
const WALL_CLEARANCE = 1.5; // 墙顶超出内容最高点的净距
const WALL_T = 0.2; // 墙厚
const SLAB_H = 0.2; // 地台厚
const SLAB_OVER = 0.5; // 地台外挑
const ROOF_OVER = 0.6; // 屋顶出檐
const ROOF_PITCH = THREE.MathUtils.degToRad(28); // 屋面坡角
const DOOR_W = 3; // 门洞宽
const DOOR_H = 3.2; // 门洞高
const WIN_H = 1.0; // 窗带高
const WIN_TOP_GAP = 1.1; // 窗带中心距墙顶

/** 包围盒是否有效（空仓库时为 Infinite，跳过建筑） */
const isValid = (b: ContentBounds): boolean =>
  isFinite(b.minX) && isFinite(b.maxX) && isFinite(b.minY) && isFinite(b.maxY) && isFinite(b.minZ) && isFinite(b.maxZ);

/**
 * 由内容包围盒自动包络出仓库厂房（写实风：地台+实墙+门洞+窗带+人字屋顶）。
 * 纯几何构建、不依赖 Vue；建筑为非交互背景，但其网格参与遮挡拾取（看得到才点得到）。
 */
export function buildWarehouseBuilding(bounds: ContentBounds): WarehouseBuilding | null {
  if (!isValid(bounds)) return null;

  const cx = (bounds.minX + bounds.maxX) / 2;
  const cz = (bounds.minZ + bounds.maxZ) / 2;
  const width = bounds.maxX - bounds.minX + MARGIN * 2;
  const depth = bounds.maxZ - bounds.minZ + MARGIN * 2;
  const wallH = Math.max(bounds.maxY, MIN_WALL_H) + WALL_CLEARANCE;

  const group = new THREE.Group();
  const pickMeshes: THREE.Mesh[] = [];
  const add = (m: THREE.Mesh) => {
    group.add(m);
    pickMeshes.push(m);
  };

  const slabMat = new THREE.MeshStandardMaterial({ color: 0xb7bcc3, roughness: 1 });
  const wallMat = new THREE.MeshStandardMaterial({ color: 0xe9ebee, roughness: 0.95 });
  const roofMat = new THREE.MeshStandardMaterial({ color: 0x7086a0, roughness: 0.6, metalness: 0.25 });
  const winMat = new THREE.MeshStandardMaterial({ color: 0x9fd0e8, roughness: 0.2, metalness: 0.1, transparent: true, opacity: 0.45 });
  const doorMat = new THREE.MeshStandardMaterial({ color: 0x8b939c, roughness: 0.8 });

  // 地台（顶面抬 1cm，避免与地面网格共面 z-fighting）
  const slab = new THREE.Mesh(new THREE.BoxGeometry(width + SLAB_OVER * 2, SLAB_H, depth + SLAB_OVER * 2), slabMat);
  slab.position.set(cx, 0.01 - SLAB_H / 2, cz);
  add(slab);

  // 两侧实墙（X 向，沿 Z 跨深）
  for (const sx of [-1, 1]) {
    const w = new THREE.Mesh(new THREE.BoxGeometry(WALL_T, wallH, depth), wallMat);
    w.position.set(cx + sx * (width / 2 - WALL_T / 2), wallH / 2, cz);
    add(w);
  }
  // 背墙（-Z，无门）
  const back = new THREE.Mesh(new THREE.BoxGeometry(width, wallH, WALL_T), wallMat);
  back.position.set(cx, wallH / 2, cz - (depth / 2 - WALL_T / 2));
  add(back);

  // 前墙（+Z，中段留门洞：左段 + 右段 + 门楣）
  const doorW = Math.min(DOOR_W, width * 0.4);
  const doorH = Math.min(DOOR_H, wallH - 0.8);
  const frontZ = cz + (depth / 2 - WALL_T / 2);
  const sideW = (width - doorW) / 2;
  for (const sx of [-1, 1]) {
    const seg = new THREE.Mesh(new THREE.BoxGeometry(sideW, wallH, WALL_T), wallMat);
    seg.position.set(cx + (sx * (width + doorW)) / 4, wallH / 2, frontZ);
    add(seg);
  }
  const lintelH = wallH - doorH;
  const lintel = new THREE.Mesh(new THREE.BoxGeometry(doorW, lintelH, WALL_T), wallMat);
  lintel.position.set(cx, doorH + lintelH / 2, frontZ);
  add(lintel);
  // 卷帘门板（略凹入门洞，写实闭合门）
  const door = new THREE.Mesh(new THREE.BoxGeometry(doorW - 0.1, doorH - 0.05, 0.06), doorMat);
  door.position.set(cx, doorH / 2, frontZ - 0.05);
  add(door);

  // 窗带（两侧墙 + 背墙，近墙顶玻璃长条，贴面不开孔）
  const winY = wallH - WIN_TOP_GAP;
  for (const sx of [-1, 1]) {
    const win = new THREE.Mesh(new THREE.BoxGeometry(0.06, WIN_H, depth - 0.6), winMat);
    win.position.set(cx + sx * (width / 2 + 0.03), winY, cz);
    add(win);
  }
  const winBack = new THREE.Mesh(new THREE.BoxGeometry(width - 0.6, WIN_H, 0.06), winMat);
  winBack.position.set(cx, winY, cz - (depth / 2 + 0.03));
  add(winBack);

  // 人字屋顶（三角形截面挤出成三棱柱，含前后山墙；屋脊沿较长轴）
  const ridgeAlongX = width >= depth;
  const spanAcross = ridgeAlongX ? depth : width;
  const ridgeLen = (ridgeAlongX ? width : depth) + ROOF_OVER * 2;
  const base = spanAcross + ROOF_OVER * 2;
  const rise = (spanAcross / 2 + ROOF_OVER) * Math.tan(ROOF_PITCH);
  const tri = new THREE.Shape();
  tri.moveTo(-base / 2, 0);
  tri.lineTo(base / 2, 0);
  tri.lineTo(0, rise);
  tri.closePath();
  const roofGeo = new THREE.ExtrudeGeometry(tri, { depth: ridgeLen, bevelEnabled: false });
  roofGeo.center();
  const roof = new THREE.Mesh(roofGeo, roofMat);
  roof.rotation.y = ridgeAlongX ? Math.PI / 2 : 0;
  roof.position.set(cx, wallH + rise / 2, cz);
  add(roof);

  const envelope: ContentBounds = {
    minX: cx - (width / 2 + SLAB_OVER),
    maxX: cx + (width / 2 + SLAB_OVER),
    minY: -SLAB_H,
    maxY: wallH + rise,
    minZ: cz - (depth / 2 + SLAB_OVER),
    maxZ: cz + (depth / 2 + SLAB_OVER)
  };
  return { group, roof, pickMeshes, envelope };
}
