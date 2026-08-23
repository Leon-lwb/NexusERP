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
          <span
            v-for="st in STATUS_KEYS"
            :key="st"
            class="wm3d-legend wm3d-legend-btn"
            :class="{ 'is-active': statusFilter.includes(st) }"
            title="点击按状态筛选库位"
            @click="toggleStatusFilter(st)"
          >
            <i class="wm3d-dot" :style="{ background: LEGEND_COLORS[st] }"></i>{{ STATUS_LABELS[st] }} {{ statusCount(st) }}
          </span>
          <el-divider direction="vertical" />
          <span class="wm3d-total">共 {{ viewData.totalLocations ?? 0 }} 个库位</span>
          <el-tag v-if="statusFilter.length > 0" size="small" type="info" effect="plain" closable @close="clearStatusFilter">已过滤</el-tag>
        </div>
        <div class="wm3d-toolbar-right">
          <el-checkbox v-model="buildingVisible" title="仓库外壳显隐" style="margin-right: 8px" @change="onToggleBuilding">外壳</el-checkbox>
          <el-checkbox v-model="labelsVisible" title="标签显隐" style="margin-right: 8px" @change="onToggleLabels">标签</el-checkbox>
          <el-checkbox v-model="shadowsEnabled" title="阴影显隐" style="margin-right: 8px" @change="onToggleShadows">阴影</el-checkbox>
          <el-select v-model="colorMode" style="width: 110px" @change="recolorLocations" title="着色模式">
            <el-option label="按状态" value="status" />
            <el-option label="按库区" value="area" />
            <el-option label="按占用率" value="occupancy" />
          </el-select>
          <el-dropdown trigger="click" @command="applyViewPreset">
            <el-button plain class="wm3d-ctl-btn">
              视角<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="overview">全景</el-dropdown-item>
                <el-dropdown-item command="top">俯视</el-dropdown-item>
                <el-dropdown-item command="front">正视</el-dropdown-item>
                <el-dropdown-item command="side">侧视</el-dropdown-item>
                <el-dropdown-item command="iso">等轴测</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-popover placement="bottom-end" :width="300" trigger="click">
            <template #reference>
              <el-button plain class="wm3d-ctl-btn">库区</el-button>
            </template>
            <div class="wm3d-area-pop">
              <div class="wm3d-area-pop-head">
                <span>库区显隐</span>
                <span>
                  <el-button link type="primary" size="small" @click="setAllAreasVisible(true)">全选</el-button>
                  <el-button link type="info" size="small" @click="setAllAreasVisible(false)">全隐</el-button>
                </span>
              </div>
              <div v-if="areaList.length === 0" class="wm3d-area-pop-empty">暂无库区</div>
              <el-checkbox
                v-for="a in areaList"
                :key="a.areaId"
                class="wm3d-area-pop-item"
                :model-value="areaVisibleMap[a.areaId] !== false"
                @change="onAreaChange(a.areaId, $event)"
              >
                <i class="wm3d-dot" :style="{ background: a.color || '#409eff' }"></i>
                <span class="wm3d-area-pop-name">{{ a.areaName }}</span>
                <span class="wm3d-area-pop-count">{{ (a.locations || []).length }} 位</span>
              </el-checkbox>
            </div>
          </el-popover>
          <el-button type="primary" plain icon="Refresh" class="wm3d-ctl-btn" @click="resetView" :disabled="!ready">重置视角</el-button>
        </div>
      </div>
    </el-card>

    <!-- 3D 画布 -->
    <el-card shadow="hover" v-loading="loading" element-loading-text="加载 3D 数据...">
      <div ref="containerRef" class="wm3d-canvas">
        <!-- 悬停浮窗（置于画布内，坐标即画布相对坐标） -->
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
import type { Warehouse3dVO, WarehouseArea3dVO } from '@/api/erp/wm/warehouse/types';
import WmLocationStockDialog from '../components/WmLocationStockDialog.vue';
import { autoLayoutRack } from '../components/rackLayout';
import { buildWarehouseBuilding } from '../components/warehouseBuilding';
import { createInstancedLocations } from '../components/instancedLocations';
import type { InstancedLocations, LocationColorMode, LocationEntry } from '../components/instancedLocations';
import { buildRackFrames } from '../components/rackFrame';
import type { RackFrameBuild } from '../components/rackFrame';
import { buildFloorMarkings } from '../components/floorMarkings';
import type { FloorMarkingBuild } from '../components/floorMarkings';
import { buildSceneLabels } from '../components/sceneLabels';
import type { SceneLabels } from '../components/sceneLabels';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
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
const colorMode = ref<LocationColorMode>('status');
// 仓库厂房（外壳）显隐
const buildingVisible = ref(true);
// 标签 / 阴影显隐
const labelsVisible = ref(true);
const shadowsEnabled = ref(true);
// 状态过滤（图例点击，空数组 = 不过滤显示全部）
const statusFilter = ref<string[]>([]);
// 库区显隐状态（缺省视为显示）
const areaVisibleMap = reactive<Record<number, boolean>>({});
// 库位库存明细弹窗
const stockDialog = reactive({
  visible: false,
  locationId: undefined as number | string | undefined,
  locationName: ''
});

const STATUS_KEYS = ['EMPTY', 'OCCUPIED', 'WARNING', 'LOCKED'] as const;
const STATUS_LABELS: Record<string, string> = { EMPTY: '空置', OCCUPIED: '占用', WARNING: '预警', LOCKED: '锁定' };
const LEGEND_COLORS: Record<string, string> = { EMPTY: '#909399', OCCUPIED: '#409eff', WARNING: '#e6a23c', LOCKED: '#f56c6c' };

const areaList = computed(() => viewData.value.areas || []);

const statusCount = (st: string): number => {
  const map: Record<string, number | undefined> = {
    EMPTY: viewData.value.emptyLocations,
    OCCUPIED: viewData.value.occupiedLocations,
    WARNING: viewData.value.warningLocations,
    LOCKED: viewData.value.lockedLocations
  };
  return map[st] ?? 0;
};

const statusLabel = (s?: string) => (s ? STATUS_LABELS[s] || s : '');

const goBack = () => proxy.$router.back();

// three.js 资源（模块级，非响应式）
let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let renderer: THREE.WebGLRenderer | null = null;
let controls: OrbitControls | null = null;
let raycaster: THREE.Raycaster | null = null;
let pointer: THREE.Vector2 | null = null;
let dirLight: THREE.DirectionalLight | null = null;
let rafId = 0;
let cameraAnimId = 0;
let singleClickTimer: ReturnType<typeof setTimeout> | null = null;
let pointerDownPos: { x: number; y: number } | null = null;
// 渲染组件
let locationRenderer: InstancedLocations | null = null;
let rackFrames: RackFrameBuild | null = null;
let floorMarkings: FloorMarkingBuild | null = null;
let labels: SceneLabels | null = null;
// 库区主组（外壳 + 地面标线 + 货架 + 标签子组挂载其下），key: areaId
const areaGroups = new Map<number, THREE.Group>();
// 仓库厂房（建筑本体）资源
let buildingGroup: THREE.Group | null = null;
let roofMesh: THREE.Object3D | null = null;
// 遮挡拾取网格（厂房 + 货架钢结构，"看得到才点得到"）
let occluders: THREE.Mesh[] = [];
let roofHidden = false;
// 缓存初始视角用于重置
let initialTarget: THREE.Vector3 | null = null;
let initialPosition: THREE.Vector3 | null = null;

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

const num = (v: any, dft: number) => {
  const n = Number(v);
  return isNaN(n) || v === null || v === undefined ? dft : n;
};

const toRad = (deg?: number) => (deg ? THREE.MathUtils.degToRad(deg) : 0);

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
  // 光影：软阴影 + ACES 色调映射
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  container.appendChild(renderer.domElement);

  // 灯光：环境光 + 半球光补天空/地面色 + 主平行光（投影）
  scene.add(new THREE.AmbientLight(0xffffff, 0.35));
  scene.add(new THREE.HemisphereLight(0xdfe9f3, 0x9aa0a6, 0.45));
  dirLight = new THREE.DirectionalLight(0xffffff, 1.15);
  dirLight.castShadow = shadowsEnabled.value;
  dirLight.shadow.mapSize.set(2048, 2048);
  dirLight.shadow.bias = -0.0004;
  scene.add(dirLight);
  scene.add(dirLight.target);

  // 地面网格 + 地板
  const grid = new THREE.GridHelper(80, 80, 0xc0c4cc, 0xdcdfe6);
  grid.position.y = 0;
  scene.add(grid);
  const planeGeo = new THREE.PlaneGeometry(80, 80);
  const planeMat = new THREE.MeshStandardMaterial({ color: 0xeef0f4, roughness: 1 });
  const plane = new THREE.Mesh(planeGeo, planeMat);
  plane.rotation.x = -Math.PI / 2;
  plane.position.y = -0.01;
  plane.receiveShadow = true;
  scene.add(plane);

  // 库区：货架自动排版（手填坐标优先）→ 主组（外壳/标线/钢架/标签）
  const areas = viewData.value.areas || [];
  for (const area of areas) {
    autoLayoutRack(area.locations || [], area);
    const group = new THREE.Group();
    group.name = `area-${area.areaId}`;
    scene.add(group);
    areaGroups.set(area.areaId, group);
  }

  // 地面标线（色块/边界线/通道虚线）
  floorMarkings = buildFloorMarkings(areas);
  for (const [areaId, g] of floorMarkings.groups) {
    areaGroups.get(areaId)?.add(g);
  }

  // 货架钢结构（RACK 布局）
  rackFrames = buildRackFrames(areas);
  for (const [areaId, g] of rackFrames.groups) {
    areaGroups.get(areaId)?.add(g);
  }

  // 库区半透明外壳（showWalls=0 的货架区由钢架表达体积，不再渲染）
  for (const area of areas) {
    addAreaBox(area);
  }

  // 标签（库区名 + 库位编码 Sprite）
  labels = buildSceneLabels(areas);
  labels.setVisible(labelsVisible.value);
  scene.add(labels.root);

  // 库位 InstancedMesh 渲染器（幽灵格/货物/托盘 + 合并轮廓线 + 高亮盒）
  locationRenderer = createInstancedLocations(areas, colorMode.value);
  scene.add(locationRenderer.root);

  // 取景包围盒：库区占地 + 库位
  const bounds = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity, minZ: Infinity, maxZ: -Infinity };
  for (const area of areas) {
    const w = num(area.areaWidth, 0);
    const d = num(area.areaLength, 0);
    const h = num(area.areaHeight, 0);
    expandBounds(bounds, num(area.posX, 0), num(area.posY, 0) + h / 2, num(area.posZ, 0), w, h, d);
  }
  for (const e of locationRenderer.entries) {
    expandBounds(bounds, e.center.x, e.center.y, e.center.z, e.size.x, e.size.y, e.size.z);
  }

  // 仓库厂房：由内容包围盒自动包络（工业写实风：勒脚彩钢墙/卷帘门/卸货平台/条窗/屋脊通风器），
  // 其网格参与遮挡拾取但不响应业务交互；屋顶不投影避免压暗室内，仅接收投影（组件内设置）
  const whLabel = [viewData.value.warehouseCode, viewData.value.warehouseName].filter(Boolean).join(' ');
  const building = buildWarehouseBuilding(bounds, whLabel ? { label: whLabel } : {});
  if (building) {
    buildingGroup = building.group;
    roofMesh = building.roof;
    occluders.push(...building.pickMeshes);
    scene.add(buildingGroup);
    // 取景包围盒扩展到整栋厂房（含地台外挑与屋顶起坡）
    const e = building.envelope;
    expandBounds(bounds, (e.minX + e.maxX) / 2, (e.minY + e.maxY) / 2, (e.minZ + e.maxZ) / 2, e.maxX - e.minX, e.maxY - e.minY, e.maxZ - e.minZ);
    // 数据重载后按当前开关状态恢复显隐
    buildingGroup.visible = buildingVisible.value;
  }
  // 货架钢架同样参与遮挡拾取
  occluders.push(...rackFrames.pickMeshes);

  // 主平行光：对准场景中心，投影相机按包围盒收紧
  if (dirLight && isFinite(bounds.minX)) {
    const cx = (bounds.minX + bounds.maxX) / 2;
    const cz = (bounds.minZ + bounds.maxZ) / 2;
    const maxDim = Math.max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ, 10);
    dirLight.position.set(cx + maxDim, maxDim * 1.4, cz + maxDim * 0.7);
    dirLight.target.position.set(cx, 0, cz);
    const s = maxDim * 0.9;
    dirLight.shadow.camera.left = -s;
    dirLight.shadow.camera.right = s;
    dirLight.shadow.camera.top = s;
    dirLight.shadow.camera.bottom = -s;
    dirLight.shadow.camera.near = 1;
    dirLight.shadow.camera.far = maxDim * 5;
    dirLight.shadow.camera.updateProjectionMatrix();
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

/** 库区半透明外壳 + 描边（showWalls=0 时跳过，由货架钢结构表达体积） */
const addAreaBox = (area: WarehouseArea3dVO) => {
  if (Number(area.showWalls ?? 1) !== 1) return;
  const group = areaGroups.get(area.areaId);
  if (!group || !scene) return;
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
  mesh.rotation.set(toRad(area.rotationX), toRad(area.rotationY), toRad(area.rotationZ));
  group.add(mesh);

  // 描边
  const edges = new THREE.EdgesGeometry(geo);
  const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: color }));
  line.position.copy(mesh.position);
  line.rotation.copy(mesh.rotation);
  group.add(line);
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

/** 视角预设：全景/俯视/正视/侧视/等轴测（俯视用极小偏移避开万向锁） */
const applyViewPreset = (cmd: string) => {
  if (!camera || !controls || !initialTarget || !initialPosition) return;
  if (cmd === 'overview') {
    animateCamera(camera.position.clone(), initialPosition.clone(), initialTarget.clone());
    return;
  }
  const target = initialTarget.clone();
  const dist = Math.max(camera.position.distanceTo(controls.target), 10);
  let pos: THREE.Vector3;
  if (cmd === 'top') pos = target.clone().add(new THREE.Vector3(0.01, dist, 0.01));
  else if (cmd === 'front') pos = target.clone().add(new THREE.Vector3(0, dist * 0.25, dist));
  else if (cmd === 'side') pos = target.clone().add(new THREE.Vector3(dist, dist * 0.25, 0));
  else pos = target.clone().add(new THREE.Vector3(dist * 0.7, dist * 0.7, dist * 0.7));
  animateCamera(camera.position.clone(), pos, target);
};

/** 状态图例点击过滤：再点一次取消，空集 = 显示全部 */
const toggleStatusFilter = (st: string) => {
  const idx = statusFilter.value.indexOf(st);
  if (idx >= 0) statusFilter.value.splice(idx, 1);
  else statusFilter.value.push(st);
  locationRenderer?.setStatusFilter(statusFilter.value.length > 0 ? [...statusFilter.value] : null);
};

const clearStatusFilter = () => {
  statusFilter.value = [];
  locationRenderer?.setStatusFilter(null);
};

/** 库区显隐：联动外壳/标线/钢架/标签子组 + 库位实例 */
const onAreaVisible = (areaId: number, visible: boolean) => {
  areaVisibleMap[areaId] = visible;
  const group = areaGroups.get(areaId);
  if (group) group.visible = visible;
  locationRenderer?.setAreaVisible(areaId, visible);
};

const onAreaChange = (areaId: number, v: boolean | string | number) => onAreaVisible(areaId, Boolean(v));

const setAllAreasVisible = (visible: boolean) => {
  for (const area of areaList.value) onAreaVisible(area.areaId, visible);
};

/** 有效可见性：沿父链任一祖先不可见即视为不可见（用于遮挡拾取与屋顶自动隐藏） */
const isEffVisible = (o: THREE.Object3D | null): boolean => {
  for (let p: THREE.Object3D | null = o; p; p = p.parent) {
    if (!p.visible) return false;
  }
  return true;
};

/**
 * 由指针事件拾取库位：raycast 目标含建筑与货架网格，取"最近且有效可见"的命中。
 * 命中库位格实例返回其 entries 下标；命中建筑/货架（被遮挡）或空白返回 null —— 看得到才点得到。
 */
const pickEntryIndex = (e: MouseEvent | PointerEvent): number | null => {
  if (!renderer || !camera || !raycaster || !pointer || !locationRenderer?.pickTarget) return null;
  const rect = renderer.domElement.getBoundingClientRect();
  pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
  pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
  raycaster.setFromCamera(pointer, camera);
  const targets: THREE.Object3D[] = [locationRenderer.pickTarget, ...occluders];
  const hits = raycaster.intersectObjects(targets, false);
  const hit = hits.find((h) => isEffVisible(h.object));
  if (!hit || hit.object !== locationRenderer.pickTarget || hit.instanceId == null) return null;
  return hit.instanceId < locationRenderer.entries.length ? hit.instanceId : null;
};

// 屋顶自动隐藏阈值（相机俯仰角）：俯视开顶见内部，低角度恢复外壳
const ROOF_HIDE_ELEV = THREE.MathUtils.degToRad(42);
const ROOF_SHOW_ELEV = THREE.MathUtils.degToRad(32);

/** 按相机俯仰角自动显隐屋顶（42°/32° 双阈值迟滞，避免边界抖动） */
const updateRoofVisibility = () => {
  if (!roofMesh || !camera || !controls) return;
  const dx = camera.position.x - controls.target.x;
  const dy = camera.position.y - controls.target.y;
  const dz = camera.position.z - controls.target.z;
  const elev = Math.atan2(dy, Math.hypot(dx, dz));
  if (!roofHidden && elev > ROOF_HIDE_ELEV) roofHidden = true;
  else if (roofHidden && elev < ROOF_SHOW_ELEV) roofHidden = false;
  roofMesh.visible = !roofHidden;
};

/** 外壳显隐开关：隐藏整栋厂房（墙+顶+地台），回到纯内部视图 */
const onToggleBuilding = (val: boolean) => {
  if (buildingGroup) buildingGroup.visible = val;
};

/** 标签显隐开关 */
const onToggleLabels = (val: boolean) => {
  labels?.setVisible(val);
};

/** 阴影显隐开关（关主光源投影即可，无需重建材质） */
const onToggleShadows = (val: boolean) => {
  if (dirLight) dirLight.castShadow = val;
};

/** 悬停检测 */
const onPointerMove = (e: PointerEvent) => {
  if (!renderer || !locationRenderer) return;
  const idx = pickEntryIndex(e);
  if (idx !== null) {
    locationRenderer.highlight(idx);
    const entry = locationRenderer.entries[idx];
    const rect = renderer.domElement.getBoundingClientRect();
    tooltip.title = entry.loc.locationName || entry.loc.locationCode;
    tooltip.status = entry.loc.status || '';
    tooltip.areaName = entry.area.areaName || '';
    tooltip.stockQuantity = entry.loc.stockQuantity ?? 0;
    tooltip.reservedQuantity = entry.loc.reservedQuantity ?? 0;
    tooltip.stockCount = entry.loc.stockCount ?? 0;
    tooltip.x = e.clientX - rect.left + 14;
    tooltip.y = e.clientY - rect.top + 14;
    tooltip.visible = true;
  } else {
    locationRenderer.highlight(null);
    tooltip.visible = false;
  }
};

const onPointerLeave = () => {
  locationRenderer?.highlight(null);
  tooltip.visible = false;
};

/** 按当前着色模式重绘所有货物颜色（仅改 instanceColor，不重建几何体） */
const recolorLocations = () => {
  locationRenderer?.recolor(colorMode.value);
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

/** 双击聚焦：相机沿当前视线方向退到距库位合适距离处，target 移到库位中心 */
const focusEntry = (entry: LocationEntry) => {
  if (!camera || !controls) return;
  const center = entry.center.clone();
  const dist = Math.max(entry.size.x, entry.size.y, entry.size.z) * 5 + 2;
  const dir = camera.position.clone().sub(controls.target).normalize().multiplyScalar(dist);
  animateCamera(camera.position.clone(), center.clone().add(dir), center);
};

/** pointerdown 记录起点，用于区分点击与拖拽 */
const onPointerDown = (e: PointerEvent) => {
  pointerDownPos = { x: e.clientX, y: e.clientY };
};

/** pointerup：位移 < 5px 判定为点击（避开 OrbitControls 拖拽），延迟 250ms 触发以避让双击 */
const onPointerUp = (e: PointerEvent) => {
  if (!pointerDownPos) return;
  const dx = e.clientX - pointerDownPos.x;
  const dy = e.clientY - pointerDownPos.y;
  pointerDownPos = null;
  if (Math.hypot(dx, dy) >= 5) return;
  const idx = pickEntryIndex(e);
  if (idx === null || !locationRenderer) return;
  const loc = locationRenderer.entries[idx].loc;
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
  const idx = pickEntryIndex(e);
  if (idx === null || !locationRenderer) return;
  focusEntry(locationRenderer.entries[idx]);
};

/** 渲染循环 */
const animate = () => {
  rafId = requestAnimationFrame(animate);
  if (controls) controls.update();
  updateRoofVisibility();
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
  // 渲染组件资源
  locationRenderer?.dispose();
  locationRenderer = null;
  rackFrames?.dispose();
  rackFrames = null;
  floorMarkings?.dispose();
  floorMarkings = null;
  labels?.dispose();
  labels = null;
  areaGroups.clear();
  occluders = [];
  buildingGroup = null;
  roofMesh = null;
  roofHidden = false;
  dirLight = null;
  if (scene) {
    scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry?.dispose();
        const mat = obj.material;
        if (Array.isArray(mat)) mat.forEach((m) => m.dispose());
        else mat?.dispose();
      } else if (obj instanceof THREE.LineSegments || obj instanceof THREE.LineLoop) {
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

  .wm3d-legend-btn {
    cursor: pointer;
    user-select: none;
    padding: 2px 8px;
    border-radius: 4px;
    transition: background-color 0.2s;

    &:hover {
      background: #f0f2f5;
    }

    &.is-active {
      background: #e6f0ff;
      color: #1a6ee0;
    }
  }

  .wm3d-total {
    font-size: 13px;
    color: #909399;
  }
}

.wm3d-ctl-btn {
  margin-left: 8px;
}

.wm3d-area-pop {
  .wm3d-area-pop-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    padding-bottom: 6px;
    margin-bottom: 6px;
    border-bottom: 1px solid #ebeef5;
  }

  .wm3d-area-pop-empty {
    font-size: 13px;
    color: #909399;
    text-align: center;
    padding: 8px 0;
  }

  .wm3d-area-pop-item {
    display: flex;
    width: 100%;
    margin-bottom: 4px;

    .wm3d-dot {
      display: inline-block;
      width: 10px;
      height: 10px;
      border-radius: 2px;
      margin-right: 6px;
    }

    .wm3d-area-pop-name {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .wm3d-area-pop-count {
      color: #909399;
      font-size: 12px;
    }
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
