import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

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
  /** 整栋建筑（地台+墙体+门窗+屋顶），挂入 scene */
  group: THREE.Group;
  /** 屋顶整体（屋面+屋脊盖板+封檐板+通风器），供俯视自动隐藏 */
  roof: THREE.Object3D;
  /** 参与遮挡拾取的建筑网格（不含库位 locationMeshes） */
  pickMeshes: THREE.Mesh[];
  /** 建筑外轮廓包围盒（含地台外挑/卸货平台/屋顶起坡），用于取景 */
  envelope: ContentBounds;
}

export interface BuildingOptions {
  /** 山墙标牌文字（如 "WH-3D-DEMO 3D示例仓库"），缺省不渲染 */
  label?: string;
}

// —— 尺寸可调常量（米 / 弧度）——
const MARGIN = 2.5; // 占地边距（预留卸货作业面）
const MIN_WALL_H = 4.5; // 最低墙高
const WALL_CLEARANCE = 1.8; // 墙顶超出内容最高点的净距
const WALL_T = 0.2; // 墙厚
const SLAB_H = 0.2; // 地台厚
const SLAB_OVER = 0.5; // 地台外挑
const WAINSCOT_H = 1.1; // 混凝土勒脚高
const RIB_STEP = 0.8; // 彩钢竖楞间距
const RIB_W = 0.05; // 竖楞宽
const RIB_D = 0.035; // 竖楞凸出
const DOCK_W = 3.2; // 卷帘门宽
const DOCK_H = 3.6; // 卷帘门高
const DOCK_GAP = 1.4; // 双门间距
const CANOPY_D = 1.3; // 雨棚出挑
const CANOPY_T = 0.1; // 雨棚板厚
const PLAT_H = 0.25; // 卸货平台高
const PLAT_D = 1.6; // 卸货平台深
const WIN_H = 1.1; // 条窗高
const WIN_TOP_GAP = 1.4; // 条窗中心距墙顶
const WIN_FRAME_STEP = 2.0; // 窗框竖梃间距
const ROOF_OVER = 0.7; // 屋顶出檐
const ROOF_PITCH = THREE.MathUtils.degToRad(18); // 屋面坡角
const VENT_COUNT = 4; // 屋脊通风器数量

/** 包围盒是否有效（空仓库时为 Infinite，跳过建筑） */
const isValid = (b: ContentBounds): boolean =>
  isFinite(b.minX) && isFinite(b.maxX) && isFinite(b.minY) && isFinite(b.maxY) && isFinite(b.minZ) && isFinite(b.maxZ);

/** 深灰底白字标牌纹理（山墙仓库标识） */
const makeSignTexture = (text: string): THREE.CanvasTexture => {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 112;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#37414f';
  ctx.fillRect(0, 0, 512, 112);
  ctx.fillStyle = '#f3c614';
  ctx.fillRect(0, 0, 512, 6);
  ctx.fillRect(0, 106, 512, 6);
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 52px "Helvetica Neue", Arial, "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, 256, 58);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
};

/**
 * 由内容包围盒自动包络出仓库厂房（工业写实风）。
 * 结构：混凝土地台 + 两段式墙体（勒脚 + 彩钢竖楞墙）+ 双卷帘门（门框/横楞门板/雨棚/卸货平台/坡道/警示条）
 *      + 带框高位条窗 + 人行门 + 人字屋顶（屋脊盖板/封檐板/屋脊通风器）+ 山墙标牌。
 * 纯几何构建、不依赖 Vue；建筑为非交互背景，但其网格参与遮挡拾取（看得到才点得到）。
 * 屋顶不投影（避免把室内全部压暗），全部构件只接收货架/货物投影。
 */
export function buildWarehouseBuilding(bounds: ContentBounds, opts: BuildingOptions = {}): WarehouseBuilding | null {
  if (!isValid(bounds)) return null;

  const cx = (bounds.minX + bounds.maxX) / 2;
  const cz = (bounds.minZ + bounds.maxZ) / 2;
  const width = bounds.maxX - bounds.minX + MARGIN * 2;
  const depth = bounds.maxZ - bounds.minZ + MARGIN * 2;
  const wallH = Math.max(bounds.maxY, MIN_WALL_H) + WALL_CLEARANCE;

  const group = new THREE.Group();
  const pickMeshes: THREE.Mesh[] = [];
  const add = (m: THREE.Mesh, pick = true) => {
    m.receiveShadow = true;
    group.add(m);
    if (pick) pickMeshes.push(m);
  };

  // —— 材质 ——
  const slabMat = new THREE.MeshStandardMaterial({ color: 0xb7bcc3, roughness: 1 });
  const wainscotMat = new THREE.MeshStandardMaterial({ color: 0xb3aa9c, roughness: 0.95 });
  const wallMat = new THREE.MeshStandardMaterial({ color: 0xeceff3, roughness: 0.7, metalness: 0.12 });
  const ribMat = new THREE.MeshStandardMaterial({ color: 0xd4dae2, roughness: 0.7, metalness: 0.2 });
  const roofMat = new THREE.MeshStandardMaterial({ color: 0x6e849c, roughness: 0.55, metalness: 0.3 });
  const ridgeMat = new THREE.MeshStandardMaterial({ color: 0x5a6d82, roughness: 0.6, metalness: 0.25 });
  const trimMat = new THREE.MeshStandardMaterial({ color: 0xf4f6f8, roughness: 0.8 });
  const glassMat = new THREE.MeshStandardMaterial({ color: 0x9fd0e8, roughness: 0.12, metalness: 0.55, transparent: true, opacity: 0.5 });
  const frameMat = new THREE.MeshStandardMaterial({ color: 0x5f6b7a, roughness: 0.6, metalness: 0.4 });
  const doorMat = new THREE.MeshStandardMaterial({ color: 0x97a1ac, roughness: 0.75, metalness: 0.3 });
  const doorRibMat = new THREE.MeshStandardMaterial({ color: 0x7f8a96, roughness: 0.75, metalness: 0.35 });
  const dockMat = new THREE.MeshStandardMaterial({ color: 0xcdd2d8, roughness: 0.9 });
  const yellowMat = new THREE.MeshStandardMaterial({ color: 0xf3c614, roughness: 0.6 });
  const ventMat = new THREE.MeshStandardMaterial({ color: 0x8d99a6, roughness: 0.4, metalness: 0.5 });

  const box = (sx: number, sy: number, sz: number, x: number, y: number, z: number): THREE.BoxGeometry => {
    const g = new THREE.BoxGeometry(sx, sy, sz);
    g.translate(x, y, z);
    return g;
  };
  const merged = (geos: THREE.BufferGeometry[], mat: THREE.Material, pick = true): THREE.Mesh | null => {
    if (geos.length === 0) return null;
    const geo = mergeGeometries(geos, false);
    geos.forEach((g) => g.dispose());
    if (!geo) return null;
    const mesh = new THREE.Mesh(geo, mat);
    add(mesh, pick);
    return mesh;
  };

  // —— 卷帘门布置（前墙 +Z）：宽度足够开双门，否则单门 ——
  const doorW = Math.min(DOCK_W, width * 0.3);
  const doorH = Math.min(DOCK_H, wallH - 1.0);
  const twoDocks = width >= doorW * 2 + DOCK_GAP + 4;
  const dockCenters = twoDocks ? [cx - doorW / 2 - DOCK_GAP / 2, cx + doorW / 2 + DOCK_GAP / 2] : [cx];
  const openings: [number, number][] = dockCenters.map((x0) => [x0 - doorW / 2, x0 + doorW / 2]);
  // 前墙下部实心段（勒脚与门洞之间的墙体）
  const frontSegs: [number, number][] = [];
  let cur = cx - width / 2;
  for (const [a, b] of openings) {
    frontSegs.push([cur, a]);
    cur = b;
  }
  frontSegs.push([cur, cx + width / 2]);
  const spanW = openings[openings.length - 1][1] - openings[0][0]; // 门组总宽

  // —— 地台（顶面抬 1cm，避免与地面网格共面 z-fighting）——
  add(new THREE.Mesh(box(width + SLAB_OVER * 2, SLAB_H, depth + SLAB_OVER * 2, cx, 0.01 - SLAB_H / 2, cz), slabMat));

  // —— 墙体（两段式：勒脚 0~WAINSCOT_H + 彩钢墙 WAINSCOT_H~wallH）——
  const wainscotGeos: THREE.BoxGeometry[] = [];
  const steelGeos: THREE.BoxGeometry[] = [];
  const backZ = cz - depth / 2 + WALL_T / 2;
  const frontZ = cz + depth / 2 - WALL_T / 2;
  // 背墙（-Z，无门）
  wainscotGeos.push(box(width, WAINSCOT_H, WALL_T, cx, WAINSCOT_H / 2, backZ));
  steelGeos.push(box(width, wallH - WAINSCOT_H, WALL_T, cx, WAINSCOT_H + (wallH - WAINSCOT_H) / 2, backZ));
  // 侧墙（±X）
  for (const sx of [-1, 1]) {
    const x = cx + sx * (width / 2 - WALL_T / 2);
    wainscotGeos.push(box(WALL_T, WAINSCOT_H, depth, x, WAINSCOT_H / 2, cz));
    steelGeos.push(box(WALL_T, wallH - WAINSCOT_H, depth, x, WAINSCOT_H + (wallH - WAINSCOT_H) / 2, cz));
  }
  // 前墙（+Z）：勒脚段 + 门顶以下中段按开洞切分，门顶以上整跨
  for (const [a, b] of frontSegs) {
    const segW = b - a;
    if (segW <= 0.01) continue;
    wainscotGeos.push(box(segW, WAINSCOT_H, WALL_T, (a + b) / 2, WAINSCOT_H / 2, frontZ));
    if (doorH > WAINSCOT_H + 0.05) {
      steelGeos.push(box(segW, doorH - WAINSCOT_H, WALL_T, (a + b) / 2, WAINSCOT_H + (doorH - WAINSCOT_H) / 2, frontZ));
    }
  }
  const topBase = Math.max(doorH, WAINSCOT_H);
  steelGeos.push(box(width, wallH - topBase, WALL_T, cx, topBase + (wallH - topBase) / 2, frontZ));

  // —— 彩钢竖楞（贴外墙，到窗带下沿止；前墙仅实心段）——
  const ribGeos: THREE.BoxGeometry[] = [];
  const ribTop = wallH - WIN_TOP_GAP - WIN_H - 0.05;
  if (ribTop - WAINSCOT_H > 0.3) {
    const ribH = ribTop - WAINSCOT_H;
    const ribY = WAINSCOT_H + ribH / 2;
    for (let x = cx - width / 2 + 0.5; x <= cx + width / 2 - 0.4; x += RIB_STEP) {
      ribGeos.push(box(RIB_W, ribH, RIB_D, x, ribY, cz - depth / 2 - RIB_D / 2)); // 背墙
    }
    for (const sx of [-1, 1]) {
      for (let z = cz - depth / 2 + 0.5; z <= cz + depth / 2 - 0.4; z += RIB_STEP) {
        ribGeos.push(box(RIB_D, ribH, RIB_W, cx + sx * (width / 2 + RIB_D / 2), ribY, z)); // 侧墙
      }
    }
    for (const [a, b] of frontSegs) {
      for (let x = a + 0.5; x <= b - 0.4; x += RIB_STEP) {
        ribGeos.push(box(RIB_W, ribH, RIB_D, x, ribY, cz + depth / 2 + RIB_D / 2)); // 前墙实心段
      }
    }
  }

  // —— 高位条窗（侧墙 + 背墙：整条玻璃 + 金属框梃）——
  const winY = wallH - WIN_TOP_GAP;
  const frameGeos: THREE.BoxGeometry[] = [];
  for (const sx of [-1, 1]) {
    const x = cx + sx * (width / 2 + 0.03);
    add(new THREE.Mesh(box(0.05, WIN_H, depth - 0.8, x, winY, cz), glassMat));
    frameGeos.push(box(0.08, 0.06, depth - 0.7, x, winY - WIN_H / 2, cz));
    frameGeos.push(box(0.08, 0.06, depth - 0.7, x, winY + WIN_H / 2, cz));
    for (let z = cz - (depth - 0.8) / 2 + WIN_FRAME_STEP; z < cz + (depth - 0.8) / 2; z += WIN_FRAME_STEP) {
      frameGeos.push(box(0.09, WIN_H + 0.1, 0.07, x, winY, z));
    }
  }
  {
    const z = cz - depth / 2 - 0.03;
    add(new THREE.Mesh(box(width - 0.8, WIN_H, 0.05, cx, winY, z), glassMat));
    frameGeos.push(box(width - 0.7, 0.06, 0.08, cx, winY - WIN_H / 2, z));
    frameGeos.push(box(width - 0.7, 0.06, 0.08, cx, winY + WIN_H / 2, z));
    for (let x = cx - (width - 0.8) / 2 + WIN_FRAME_STEP; x < cx + (width - 0.8) / 2; x += WIN_FRAME_STEP) {
      frameGeos.push(box(0.07, WIN_H + 0.1, 0.09, x, winY, z));
    }
  }

  // —— 卷帘门（门框 + 横楞门板）——
  const doorRibGeos: THREE.BoxGeometry[] = [];
  for (const dc of dockCenters) {
    add(new THREE.Mesh(box(doorW - 0.12, doorH - 0.06, 0.06, dc, doorH / 2, frontZ + 0.06), doorMat));
    for (let y = 0.3; y < doorH - 0.15; y += 0.36) {
      doorRibGeos.push(box(doorW - 0.2, 0.05, 0.03, dc, y, frontZ + 0.1));
    }
    frameGeos.push(box(0.12, doorH + 0.12, 0.14, dc - doorW / 2 - 0.06, (doorH + 0.12) / 2, frontZ + 0.05));
    frameGeos.push(box(0.12, doorH + 0.12, 0.14, dc + doorW / 2 + 0.06, (doorH + 0.12) / 2, frontZ + 0.05));
    frameGeos.push(box(doorW + 0.24, 0.12, 0.14, dc, doorH + 0.06, frontZ + 0.05));
  }

  // —— 雨棚（门组上方整体出挑）——
  add(new THREE.Mesh(box(spanW + 1.6, CANOPY_T, CANOPY_D, cx, doorH + 0.3, cz + depth / 2 + CANOPY_D / 2 - 0.1), doorMat));

  // —— 卸货平台（门前地台 + 黄黑警示边 + 两端坡道）——
  const platW = spanW + 2.4;
  const platZ = cz + depth / 2 + PLAT_D / 2;
  add(new THREE.Mesh(box(platW, PLAT_H, PLAT_D, cx, PLAT_H / 2, platZ), dockMat));
  add(new THREE.Mesh(box(platW, 0.02, 0.1, cx, PLAT_H + 0.01, cz + depth / 2 + PLAT_D - 0.06), yellowMat), false);
  for (const sx of [-1, 1]) {
    add(new THREE.Mesh(box(0.1, 0.02, PLAT_D - 0.1, cx + sx * (platW / 2 - 0.06), PLAT_H + 0.01, platZ), yellowMat), false);
  }
  for (const sx of [-1, 1]) {
    const slopeLen = Math.hypot(PLAT_D, PLAT_H);
    const ramp = new THREE.Mesh(new THREE.BoxGeometry(0.9, 0.06, slopeLen), dockMat);
    ramp.rotation.x = Math.atan2(PLAT_H, PLAT_D);
    ramp.position.set(cx + sx * (platW / 2 + 0.5), PLAT_H / 2, platZ);
    add(ramp, false);
  }

  // —— 人行门（左侧墙外贴面）——
  add(new THREE.Mesh(box(0.08, 2.1, 1.0, cx - width / 2 - 0.05, 1.05, cz - depth / 4), doorRibMat));
  frameGeos.push(box(0.1, 0.1, 1.2, cx - width / 2 - 0.06, 2.15, cz - depth / 4));
  frameGeos.push(box(0.1, 2.2, 0.1, cx - width / 2 - 0.06, 1.05, cz - depth / 4 - 0.55));
  frameGeos.push(box(0.1, 2.2, 0.1, cx - width / 2 - 0.06, 1.05, cz - depth / 4 + 0.55));

  // —— 屋顶组（屋面 + 屋脊盖板 + 封檐板 + 屋脊通风器，俯视整体隐藏）——
  const roofGroup = new THREE.Group();
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
  roof.receiveShadow = true;
  roofGroup.add(roof);
  pickMeshes.push(roof);

  // 屋脊盖板
  const ridgeCap = ridgeAlongX ? box(ridgeLen, 0.12, 0.55, cx, wallH + rise + 0.05, cz) : box(0.55, 0.12, ridgeLen, cx, wallH + rise + 0.05, cz);
  const capMesh = new THREE.Mesh(ridgeCap, ridgeMat);
  capMesh.receiveShadow = true;
  roofGroup.add(capMesh);
  pickMeshes.push(capMesh);

  // 封檐板（檐口水平条 + 山墙斜边条，每坡一条：先绕自身中心旋转再平移）
  const trimGeos: THREE.BufferGeometry[] = [];
  const halfSpan = spanAcross / 2 + ROOF_OVER;
  const slopeLen = Math.hypot(halfSpan, rise);
  if (ridgeAlongX) {
    for (const sz of [-1, 1]) {
      trimGeos.push(box(ridgeLen, 0.28, 0.1, cx, wallH + 0.05, cz + sz * (depth / 2 + ROOF_OVER)));
    }
    for (const sx of [-1, 1]) {
      const x = cx + sx * (width / 2 + ROOF_OVER - 0.04);
      for (const sz of [-1, 1]) {
        const g = new THREE.BoxGeometry(0.1, 0.3, slopeLen);
        g.rotateX(sz * ROOF_PITCH);
        g.translate(x, wallH + rise / 2, cz + sz * (halfSpan / 2));
        trimGeos.push(g);
      }
    }
  } else {
    for (const sx of [-1, 1]) {
      trimGeos.push(box(0.1, 0.28, ridgeLen, cx + sx * (width / 2 + ROOF_OVER), wallH + 0.05, cz));
    }
    for (const sz of [-1, 1]) {
      const z = cz + sz * (depth / 2 + ROOF_OVER - 0.04);
      for (const sx of [-1, 1]) {
        const g = new THREE.BoxGeometry(slopeLen, 0.3, 0.1);
        g.rotateZ(-sx * ROOF_PITCH);
        g.translate(cx + sx * (halfSpan / 2), wallH + rise / 2, z);
        trimGeos.push(g);
      }
    }
  }
  const trimMesh = merged(trimGeos, trimMat, false);
  if (trimMesh) roofGroup.add(trimMesh);

  // 屋脊通风器（底座 + 筒 + 锥帽，沿屋脊均布）
  const ventGeos: THREE.BufferGeometry[] = [];
  for (let i = 1; i <= VENT_COUNT; i++) {
    const t = -ridgeLen / 2 + (ridgeLen * i) / (VENT_COUNT + 1);
    const vx = ridgeAlongX ? cx + t : cx;
    const vz = ridgeAlongX ? cz : cz + t;
    const vy = wallH + rise;
    ventGeos.push(box(0.55, 0.18, 0.55, vx, vy + 0.09, vz));
    const cyl = new THREE.CylinderGeometry(0.2, 0.2, 0.28, 12);
    cyl.translate(vx, vy + 0.32, vz);
    ventGeos.push(cyl);
    const cone = new THREE.ConeGeometry(0.3, 0.16, 12);
    cone.translate(vx, vy + 0.54, vz);
    ventGeos.push(cone);
  }
  const ventMesh = merged(ventGeos, ventMat, false);
  if (ventMesh) roofGroup.add(ventMesh);

  group.add(roofGroup);

  // —— 山墙标牌（左墙外侧，仓库编码·名称）——
  if (opts.label) {
    const sign = new THREE.Mesh(new THREE.PlaneGeometry(5, 1.1), new THREE.MeshBasicMaterial({ map: makeSignTexture(opts.label) }));
    sign.rotation.y = -Math.PI / 2;
    sign.position.set(cx - width / 2 - 0.08, wallH - 1.6, cz);
    add(sign);
  }

  // —— 合并墙体/竖楞/框梃 ——
  merged(wainscotGeos, wainscotMat);
  merged(steelGeos, wallMat);
  merged(ribGeos, ribMat);
  merged(frameGeos, frameMat);
  merged(doorRibGeos, doorRibMat);

  const envelope: ContentBounds = {
    minX: cx - (width / 2 + SLAB_OVER),
    maxX: cx + (width / 2 + SLAB_OVER),
    minY: -SLAB_H,
    maxY: wallH + rise + 0.35,
    minZ: cz - (depth / 2 + SLAB_OVER),
    maxZ: cz + depth / 2 + Math.max(SLAB_OVER, PLAT_D + 0.6)
  };
  return { group, roof: roofGroup, pickMeshes, envelope };
}
