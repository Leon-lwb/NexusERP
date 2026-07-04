<template>
  <div class="app-container">
    <!-- 顶部工具栏 -->
    <el-card shadow="hover" class="mb8">
      <div class="wm3d-toolbar">
        <div class="wm3d-toolbar-left">
          <el-button link icon="ArrowLeft" @click="goBack">返回仓库列表</el-button>
          <el-divider direction="vertical" />
          <span class="wm3d-title">
            <strong>{{ viewData.warehouseName || '加载中...' }}</strong>
            <span class="wm3d-code">{{ viewData.warehouseCode }}</span>
          </span>
        </div>
        <div class="wm3d-toolbar-center">
          <span class="wm3d-legend"><i class="wm3d-dot" style="background: #909399"></i>空置 {{ viewData.emptyLocations ?? 0 }}</span>
          <span class="wm3d-legend"><i class="wm3d-dot" style="background: #409eff"></i>占用 {{ viewData.occupiedLocations ?? 0 }}</span>
          <span class="wm3d-legend"><i class="wm3d-dot" style="background: #e6a23c"></i>预警 {{ viewData.warningLocations ?? 0 }}</span>
          <span class="wm3d-legend"><i class="wm3d-dot" style="background: #f56c6c"></i>锁定 {{ viewData.lockedLocations ?? 0 }}</span>
          <el-divider direction="vertical" />
          <span class="wm3d-total">共 {{ viewData.totalLocations ?? 0 }} 个库位</span>
        </div>
        <div class="wm3d-toolbar-right">
          <el-select v-model="colorMode" style="width: 120px" @change="recolorLocations" title="着色模式">
            <el-option label="按状态" value="status" />
            <el-option label="按库区" value="area" />
            <el-option label="按占用率" value="occupancy" />
          </el-select>
          <el-button type="primary" plain icon="Refresh" @click="resetView" :disabled="!ready">重置视角</el-button>
        </div>
      </div>
    </el-card>

    <!-- 3D 画布 -->
    <el-card shadow="hover" v-loading="loading" element-loading-text="加载 3D 数据...">
      <div ref="containerRef" class="wm3d-canvas" />
      <!-- 悬停浮窗 -->
      <div v-show="tooltip.visible" class="wm3d-tooltip" :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }">
        <div class="wm3d-tooltip-title">{{ tooltip.title }}</div>
        <div class="wm3d-tooltip-row">
          <span>状态</span>
          <span :class="'wm3d-status wm3d-status-' + (tooltip.status || '').toLowerCase()">{{ statusLabel(tooltip.status) }}</span>
        </div>
        <div class="wm3d-tooltip-row">
          <span>库区</span><span>{{ tooltip.areaName }}</span>
        </div>
        <div class="wm3d-tooltip-row">
          <span>在库数量</span><span>{{ tooltip.stockQuantity }}</span>
        </div>
        <div class="wm3d-tooltip-row">
          <span>已预留</span><span>{{ tooltip.reservedQuantity }}</span>
        </div>
        <div class="wm3d-tooltip-row">
          <span>库存记录</span><span>{{ tooltip.stockCount }} 条</span>
        </div>
      </div>
    </el-card>

    <!-- 库位库存明细弹窗 -->
    <WmLocationStockDialog v-model="stockDialog.visible" :location-id="stockDialog.locationId" :location-name="stockDialog.locationName" />
  </div>
</template>

<script setup lang="ts" name="Warehouse3d">
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { getWarehouse3d } from '@/api/erp/wm/warehouse';
import type { Warehouse3dVO, WarehouseArea3dVO, WarehouseLocation3dVO } from '@/api/erp/wm/warehouse/types';
import WmLocationStockDialog from '../components/WmLocationStockDialog.vue';
import { autoLayoutRack } from '../components/rackLayout';

const { proxy } = getCurrentInstance() as any;
const route = useRoute();
const warehouseId = route.params.warehouseId as string;

const containerRef = ref<HTMLElement>();
const viewData = ref<Warehouse3dVO>({} as Warehouse3dVO);
const loading = ref(true);
const ready = ref(false);

// 悬停浮窗状态
const tooltip = reactive({
  visible: false,
  x: 0,
  y: 0,
  title: '',
  status: '',
  areaName: '',
  stockQuantity: 0,
  reservedQuantity: 0,
  stockCount: 0
});

// 着色模式：status 按状态 / area 按库区 / occupancy 按占用率
const colorMode = ref<'status' | 'area' | 'occupancy'>('status');
const maxStockQuantity = ref(0);
// 库位库存明细弹窗
const stockDialog = reactive({
  visible: false,
  locationId: undefined as number | string | undefined,
  locationName: ''
});

// three.js 资源（模块级，非响应式）
let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let renderer: THREE.WebGLRenderer | null = null;
let controls: OrbitControls | null = null;
let raycaster: THREE.Raycaster | null = null;
let pointer: THREE.Vector2 | null = null;
let rafId = 0;
let cameraAnimId = 0;
let singleClickTimer: ReturnType<typeof setTimeout> | null = null;
let pointerDownPos: { x: number; y: number } | null = null;
let hoveredMesh: THREE.Mesh | null = null;
const locationMeshes: THREE.Mesh[] = [];
// 缓存初始视角用于重置
let initialTarget: THREE.Vector3 | null = null;
let initialPosition: THREE.Vector3 | null = null;

// 默认状态色（库位未配置时兜底）
const DEFAULT_COLORS: Record<string, number> = {
  EMPTY: 0x909399,
  OCCUPIED: 0x409eff,
  WARNING: 0xe6a23c,
  LOCKED: 0xf56c6c
};

const STATUS_LABELS: Record<string, string> = {
  EMPTY: '空置',
  OCCUPIED: '占用',
  WARNING: '预警',
  LOCKED: '锁定'
};

const statusLabel = (s?: string) => (s ? STATUS_LABELS[s] || s : '');

const goBack = () => proxy.$router.back();

/** 加载聚合数据并初始化场景 */
const loadData = async () => {
  loading.value = true;
  try {
    const res = await getWarehouse3d(warehouseId);
    viewData.value = res.data;
    await nextTick();
    initScene();
    ready.value = true;
  } catch (e) {
    proxy.$modal.msgError('加载 3D 数据失败');
  } finally {
    loading.value = false;
  }
};

/** 初始化 three.js 场景 */
const initScene = () => {
  if (!containerRef.value) return;
  disposeScene();
  const container = containerRef.value;
  const width = container.clientWidth;
  const height = container.clientHeight;

  scene = new THREE.Scene();
  scene.background = new THREE.Color(0xf0f2f5);

  // near/far 收紧，提升深度缓冲精度，减少远处 z-fighting（场景仅数十米，far 1000 足够）
  camera = new THREE.PerspectiveCamera(60, width / height, 0.5, 1000);
  camera.position.set(20, 18, 20);

  renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(width, height);
  renderer.setPixelRatio(window.devicePixelRatio);
  container.appendChild(renderer.domElement);

  // 灯光
  const ambient = new THREE.AmbientLight(0xffffff, 0.7);
  scene.add(ambient);
  const dir = new THREE.DirectionalLight(0xffffff, 0.8);
  dir.position.set(20, 30, 20);
  scene.add(dir);

  // 地面网格 + 地板
  const grid = new THREE.GridHelper(80, 80, 0xc0c4cc, 0xdcdfe6);
  grid.position.y = 0;
  scene.add(grid);
  const planeGeo = new THREE.PlaneGeometry(80, 80);
  const planeMat = new THREE.MeshStandardMaterial({ color: 0xeef0f4, roughness: 1 });
  const plane = new THREE.Mesh(planeGeo, planeMat);
  plane.rotation.x = -Math.PI / 2;
  plane.position.y = -0.01;
  scene.add(plane);

  // 建库区 + 库位
  const bounds = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity, minZ: Infinity, maxZ: -Infinity };
  const areas = viewData.value.areas || [];
  for (const area of areas) {
    // 货架自动排版：手填坐标优先，缺省才按 layer/col/row 计算（建 mesh 前补全坐标）
    autoLayoutRack(area.locations || [], area);
    addAreaMesh(area, bounds);
    const locs = area.locations || [];
    for (const loc of locs) {
      addLocationMesh(loc, area, bounds);
    }
  }

  // OrbitControls
  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.maxPolarAngle = Math.PI / 2 - 0.05;

  // 取景：聚焦到数据包围盒
  frameBounds(bounds);

  raycaster = new THREE.Raycaster();
  pointer = new THREE.Vector2();

  // 悬停监听
  container.addEventListener('pointermove', onPointerMove);
  container.addEventListener('pointerleave', onPointerLeave);
  // 点击钻取（pointerdown/up 区分拖拽）+ 双击聚焦
  container.addEventListener('pointerdown', onPointerDown);
  container.addEventListener('pointerup', onPointerUp);
  container.addEventListener('dblclick', onDblClick);

  animate();
};

/** 添加库区半透明立方体 + 描边 */
const addAreaMesh = (area: WarehouseArea3dVO, bounds: any) => {
  const w = num(area.areaWidth, 6);
  const h = num(area.areaHeight, 3);
  const d = num(area.areaLength, 4);
  const x = num(area.posX, 0);
  const y = num(area.posY, 0) + h / 2;
  const z = num(area.posZ, 0);

  const geo = new THREE.BoxGeometry(w, h, d);
  const color = area.color ? new THREE.Color(area.color) : new THREE.Color(0x409eff);
  const mat = new THREE.MeshStandardMaterial({
    color: color,
    transparent: true,
    opacity: num(area.opacity, 0.12),
    roughness: 0.9,
    // 半透明外壳不写入深度缓冲，避免库区面遮挡其内部库位（转动视角时库位被隐藏）
    depthWrite: false
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.position.set(x, y, z);
  applyRotation(mesh, area.rotationX, area.rotationY, area.rotationZ);
  mesh.userData = { type: 'area', area };
  scene!.add(mesh);

  // 描边
  const edges = new THREE.EdgesGeometry(geo);
  const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: color }));
  line.position.copy(mesh.position);
  line.rotation.copy(mesh.rotation);
  scene!.add(line);

  expandBounds(bounds, x, y, z, w, h, d);
};

/** 添加库位立方体（按状态着色） */
const addLocationMesh = (loc: WarehouseLocation3dVO, area: WarehouseArea3dVO, bounds: any) => {
  const w = num(loc.width, 1);
  const h = num(loc.height, 1);
  const d = num(loc.locationDepth, 1);
  const x = num(loc.posX, 0);
  const y = num(loc.posY, 0) + h / 2;
  const z = num(loc.posZ, 0);

  const status = loc.status || 'EMPTY';
  const colorHex = statusColor(loc, status);
  // 库位略小于格子（保留 2% 间隙），消除相邻库位共面导致的 z-fighting（转动时闪烁/晃动）
  const GAP = 0.98;
  const geo = new THREE.BoxGeometry(w * GAP, h * GAP, d * GAP);
  const mat = new THREE.MeshStandardMaterial({
    color: colorHex,
    transparent: true,
    opacity: num(loc.opacity, 0.85),
    roughness: 0.6,
    metalness: 0.1,
    emissive: 0x000000
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.position.set(x, y, z);
  applyRotation(mesh, loc.rotationX, loc.rotationY, loc.rotationZ);
  mesh.userData = { type: 'location', loc, area, areaName: area.areaName };
  scene!.add(mesh);
  locationMeshes.push(mesh);

  expandBounds(bounds, x, y, z, w, h, d);
};

/** 取库位状态色：优先库位配置，缺省用默认色 */
const statusColor = (loc: WarehouseLocation3dVO, status: string): number => {
  const map: Record<string, string | undefined> = {
    EMPTY: loc.colorEmpty,
    OCCUPIED: loc.colorOccupied,
    WARNING: loc.colorWarning,
    LOCKED: loc.colorLocked
  };
  const c = map[status];
  if (c) {
    try {
      return new THREE.Color(c).getHex();
    } catch {
      // 颜色解析失败兜底
    }
  }
  return DEFAULT_COLORS[status] ?? 0x909399;
};

/** 应用旋转（角度值） */
const applyRotation = (mesh: THREE.Mesh, rx?: number, ry?: number, rz?: number) => {
  mesh.rotation.set(toRad(rx), toRad(ry), toRad(rz));
};

const toRad = (deg?: number) => (deg ? THREE.MathUtils.degToRad(deg) : 0);

const num = (v: any, dft: number) => {
  const n = Number(v);
  return isNaN(n) || v === null || v === undefined ? dft : n;
};

/** 扩展包围盒 */
const expandBounds = (b: any, x: number, y: number, z: number, w: number, h: number, d: number) => {
  b.minX = Math.min(b.minX, x - w / 2);
  b.maxX = Math.max(b.maxX, x + w / 2);
  b.minY = Math.min(b.minY, y - h / 2);
  b.maxY = Math.max(b.maxY, y + h / 2);
  b.minZ = Math.min(b.minZ, z - d / 2);
  b.maxZ = Math.max(b.maxZ, z + d / 2);
};

/** 根据包围盒聚焦相机 */
const frameBounds = (b: any) => {
  if (!isFinite(b.minX) || !camera || !controls) return;
  const cx = (b.minX + b.maxX) / 2;
  const cy = (b.minY + b.maxY) / 2;
  const cz = (b.minZ + b.maxZ) / 2;
  const sizeX = b.maxX - b.minX;
  const sizeY = b.maxY - b.minY;
  const sizeZ = b.maxZ - b.minZ;
  const maxDim = Math.max(sizeX, sizeY, sizeZ, 10);
  const target = new THREE.Vector3(cx, cy, cz);
  const position = new THREE.Vector3(cx + maxDim * 1.5, cy + maxDim * 1.2, cz + maxDim * 1.5);
  camera.position.copy(position);
  controls.target.copy(target);
  controls.update();
  initialTarget = target.clone();
  initialPosition = position.clone();
};

/** 重置视角到初始取景位 */
const resetView = () => {
  if (!camera || !controls || !initialTarget || !initialPosition) return;
  camera.position.copy(initialPosition);
  controls.target.copy(initialTarget);
  controls.update();
};

/** 悬停检测 */
const onPointerMove = (e: PointerEvent) => {
  if (!renderer || !camera || !raycaster || !pointer) return;
  const rect = renderer.domElement.getBoundingClientRect();
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
  raycaster.setFromCamera(pointer, camera);
  const hits = raycaster.intersectObjects(locationMeshes, false);
  if (hits.length > 0) {
    const mesh = hits[0].object as THREE.Mesh;
    if (hoveredMesh !== mesh) {
      if (hoveredMesh) (hoveredMesh.material as THREE.MeshStandardMaterial).emissive.setHex(0x000000);
      hoveredMesh = mesh;
      (mesh.material as THREE.MeshStandardMaterial).emissive.setHex(0x333333);
    }
    const ud = mesh.userData;
    const loc = ud.loc as WarehouseLocation3dVO;
    tooltip.title = loc.locationName || loc.locationCode;
    tooltip.status = loc.status || '';
    tooltip.areaName = ud.areaName || '';
    tooltip.stockQuantity = loc.stockQuantity ?? 0;
    tooltip.reservedQuantity = loc.reservedQuantity ?? 0;
    tooltip.stockCount = loc.stockCount ?? 0;
    tooltip.x = e.clientX - rect.left + 14;
    tooltip.y = e.clientY - rect.top + 14;
    tooltip.visible = true;
  } else {
    clearHover();
  }
};

const onPointerLeave = () => {
  clearHover();
};

const clearHover = () => {
  if (hoveredMesh) {
    (hoveredMesh.material as THREE.MeshStandardMaterial).emissive.setHex(0x000000);
    hoveredMesh = null;
  }
  tooltip.visible = false;
};

/** 计算库位在当前着色模式下的颜色 */
const computeColor = (loc: WarehouseLocation3dVO, area: WarehouseArea3dVO | undefined): number => {
  if (colorMode.value === 'area') return areaColorHex(area);
  if (colorMode.value === 'occupancy') return occupancyGradient(Number(loc.stockQuantity) || 0);
  return statusColor(loc, loc.status || 'EMPTY');
};

/** 占用率渐变色：0→绿, 0.5→黄, 1→红（按库存量归一化到仓库最大值） */
const occupancyGradient = (qty: number): number => {
  if (qty <= 0) return 0x909399;
  const max = maxStockQuantity.value || qty;
  const rate = Math.min(qty / max, 1);
  const hue = (1 - rate) * 120; // 120(绿)→0(红)
  return new THREE.Color().setHSL(hue / 360, 0.7, 0.5).getHex();
};

/** 库区色：优先 area.color，缺省按 areaId 哈希取色 */
const areaColorHex = (area: WarehouseArea3dVO | undefined): number => {
  if (area?.color) {
    try {
      return new THREE.Color(area.color).getHex();
    } catch {
      // 解析失败走哈希
    }
  }
  const hue = ((area?.areaId || 0) * 47) % 360;
  return new THREE.Color().setHSL(hue / 360, 0.6, 0.55).getHex();
};

/** 按当前着色模式重绘所有库位颜色（仅改 material.color，不重建几何体） */
const recolorLocations = () => {
  if (colorMode.value === 'occupancy') {
    let max = 0;
    for (const area of viewData.value.areas || []) {
      for (const loc of area.locations || []) {
        const q = Number(loc.stockQuantity) || 0;
        if (q > max) max = q;
      }
    }
    maxStockQuantity.value = max;
  }
  for (const mesh of locationMeshes) {
    const ud = mesh.userData;
    (mesh.material as THREE.MeshStandardMaterial).color.setHex(computeColor(ud.loc, ud.area));
  }
};

/** 相机位姿平滑过渡（easeOutCubic，500ms） */
const animateCamera = (fromPos: THREE.Vector3, toPos: THREE.Vector3, toTarget: THREE.Vector3) => {
  if (!camera || !controls) return;
  if (cameraAnimId) cancelAnimationFrame(cameraAnimId);
  const start = performance.now();
  const dur = 500;
  const step = (now: number) => {
    const t = Math.min((now - start) / dur, 1);
    const e = 1 - Math.pow(1 - t, 3);
    camera!.position.lerpVectors(fromPos, toPos, e);
    controls!.target.lerp(toTarget, e);
    controls!.update();
    cameraAnimId = t < 1 ? requestAnimationFrame(step) : 0;
  };
  cameraAnimId = requestAnimationFrame(step);
};

/** 双击聚焦：相机沿当前视线方向退到距库位 6 单位处，target 移到库位中心 */
const focusLocation = (loc: WarehouseLocation3dVO) => {
  const mesh = locationMeshes.find((m) => m.userData.loc === loc);
  if (!mesh || !camera || !controls) return;
  const center = mesh.position.clone();
  const dir = camera.position.clone().sub(controls.target);
  dir.normalize().multiplyScalar(Math.min(dir.length() || 6, 6));
  animateCamera(camera.position.clone(), center.clone().add(dir), center);
};

/** pointerdown 记录起点，用于区分点击与拖拽 */
const onPointerDown = (e: PointerEvent) => {
  pointerDownPos = { x: e.clientX, y: e.clientY };
};

/** pointerup：位移 < 5px 判定为点击（避开 OrbitControls 拖拽），延迟 250ms 触发以避让双击 */
const onPointerUp = (e: PointerEvent) => {
  if (!pointerDownPos || !renderer || !camera || !raycaster || !pointer) return;
  const dx = e.clientX - pointerDownPos.x;
  const dy = e.clientY - pointerDownPos.y;
  pointerDownPos = null;
  if (Math.hypot(dx, dy) >= 5) return;
  const rect = renderer.domElement.getBoundingClientRect();
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
  raycaster.setFromCamera(pointer, camera);
  const hits = raycaster.intersectObjects(locationMeshes, false);
  if (hits.length === 0) return;
  const loc = hits[0].object.userData.loc as WarehouseLocation3dVO;
  if (singleClickTimer) clearTimeout(singleClickTimer);
  singleClickTimer = setTimeout(() => {
    stockDialog.locationId = loc.locationId;
    stockDialog.locationName = loc.locationName || loc.locationCode;
    stockDialog.visible = true;
    singleClickTimer = null;
  }, 250);
};

/** 双击库位：取消待执行的点击钻取，相机平滑聚焦 */
const onDblClick = (e: MouseEvent) => {
  if (singleClickTimer) {
    clearTimeout(singleClickTimer);
    singleClickTimer = null;
  }
  if (!renderer || !camera || !raycaster || !pointer) return;
  const rect = renderer.domElement.getBoundingClientRect();
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
  raycaster.setFromCamera(pointer, camera);
  const hits = raycaster.intersectObjects(locationMeshes, false);
  if (hits.length === 0) return;
  focusLocation(hits[0].object.userData.loc as WarehouseLocation3dVO);
};

/** 渲染循环 */
const animate = () => {
  rafId = requestAnimationFrame(animate);
  if (controls) controls.update();
  if (renderer && scene && camera) renderer.render(scene, camera);
};

/** 窗口尺寸变化 */
const onResize = () => {
  if (!containerRef.value || !renderer || !camera) return;
  const w = containerRef.value.clientWidth;
  const h = containerRef.value.clientHeight;
  renderer.setSize(w, h);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
};

/** 释放 three.js 资源 */
const disposeScene = () => {
  if (rafId) {
    cancelAnimationFrame(rafId);
    rafId = 0;
  }
  if (containerRef.value && renderer) {
    containerRef.value.removeEventListener('pointermove', onPointerMove);
    containerRef.value.removeEventListener('pointerleave', onPointerLeave);
    containerRef.value.removeEventListener('pointerdown', onPointerDown);
    containerRef.value.removeEventListener('pointerup', onPointerUp);
    containerRef.value.removeEventListener('dblclick', onDblClick);
  }
  if (singleClickTimer) {
    clearTimeout(singleClickTimer);
    singleClickTimer = null;
  }
  if (cameraAnimId) {
    cancelAnimationFrame(cameraAnimId);
    cameraAnimId = 0;
  }
  hoveredMesh = null;
  locationMeshes.length = 0;
  if (scene) {
    scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry?.dispose();
        const mat = obj.material;
        if (Array.isArray(mat)) mat.forEach((m) => m.dispose());
        else mat?.dispose();
      } else if (obj instanceof THREE.LineSegments) {
        obj.geometry?.dispose();
        (obj.material as THREE.Material)?.dispose();
      }
    });
  }
  if (renderer) {
    renderer.dispose();
    if (renderer.domElement && renderer.domElement.parentNode) {
      renderer.domElement.parentNode.removeChild(renderer.domElement);
    }
  }
  scene = null;
  camera = null;
  renderer = null;
  controls = null;
  raycaster = null;
  pointer = null;
  initialTarget = null;
  initialPosition = null;
};

onMounted(() => {
  window.addEventListener('resize', onResize);
  loadData();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  disposeScene();
});
</script>

<style lang="scss" scoped>
.wm3d-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;

  .wm3d-toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .wm3d-toolbar-center {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .wm3d-toolbar-right {
    display: flex;
    align-items: center;
  }

  .wm3d-title {
    font-size: 15px;

    .wm3d-code {
      margin-left: 8px;
      color: #909399;
      font-size: 13px;
    }
  }

  .wm3d-legend {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: #606266;

    .wm3d-dot {
      display: inline-block;
      width: 10px;
      height: 10px;
      border-radius: 2px;
    }
  }

  .wm3d-total {
    font-size: 13px;
    color: #909399;
  }
}

.wm3d-canvas {
  width: 100%;
  height: calc(100vh - 200px);
  min-height: 480px;
  position: relative;
  overflow: hidden;
  border-radius: 4px;
}

.wm3d-tooltip {
  position: absolute;
  pointer-events: none;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.8;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  z-index: 10;
  min-width: 180px;

  .wm3d-tooltip-title {
    font-weight: 600;
    color: #303133;
    margin-bottom: 4px;
    border-bottom: 1px solid #ebeef5;
    padding-bottom: 4px;
  }

  .wm3d-tooltip-row {
    display: flex;
    justify-content: space-between;
    color: #606266;

    span:first-child {
      color: #909399;
    }

    span:last-child {
      font-weight: 500;
    }
  }

  .wm3d-status {
    padding: 0 6px;
    border-radius: 3px;
    color: #fff;
    font-size: 12px;
  }

  .wm3d-status-empty {
    background: #909399;
  }
  .wm3d-status-occupied {
    background: #409eff;
  }
  .wm3d-status-warning {
    background: #e6a23c;
  }
  .wm3d-status-locked {
    background: #f56c6c;
  }
}
</style>
