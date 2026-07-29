# ERP 仓储管理 (wm) 3D 可视化仓库 M2/M3 实施计划

> 文档编号: NexusERP-PLAN-2026-004
> 版本: v1.0
> 状态: 草稿
> 创建日期: 2026-07-04
> 最后更新: 2026-07-04
> 作者: AI Agent
> 关联文档: `2026-06-30-erp-wm-3d-warehouse-plan.md`（前序 MVP 计划）、`../NexusERP_ERP文档.md`、`../../CONTRIBUTING.md`

> **For agentic workers:** 本计划可用 superpowers:executing-plans 或 superpowers:subagent-driven-development 逐任务执行。任务以 `- [ ]` 复选框跟踪，状态标记遵循 CONTRIBUTING §3（✅🚧⬜❌🗑️）。

---

## 概述

**Goal:** 在前序 MVP（M1，2026-06-30 完成，提交 `544c383`）基础上，补齐 3D 仓库可视化的**交互钻取**与**大仓库性能/自动排版**能力，使特性从"能看"升级为"能查、能定位、能扛大仓库"。

**前序基线（M1 已交付，本计划不重复）：**
- 后端聚合接口 `GET /erp/wm/warehouse/{id}/3d` → `WmWarehouse3dVo`（仓库+库区+库位+库存状态）
- 状态判定 LOCKED>WARNING>OCCUPIED>EMPTY（WARNING=预留占比≥80%）
- 前端 `warehouse/3d.vue`：Three.js 场景/灯光/地面/库区库位 mesh/OrbitControls/hover 浮窗/资源释放
- 路由 `/erp/wm/warehouse/3d/:warehouseId` + 仓库列表"3D预览"按钮，复用 `wm:warehouse:query`
- 演示种子 `doc/erp/sql/test-data/erp-wm-3d-demo-seed.sql`（1 仓库+2 库区+18 库位+10 库存）

**本计划范围：**
- **Phase 0**：MVP 端到端验证（M1 遗留 `⬜ 端到端联调待本地验证`，先于 M2 闭合）
- **M2 交互增强**：点击库位钻取库存明细、双击聚焦、着色模式切换
- **M3 完整版**：货架自动排版、InstancedMesh 大仓库性能、composable 抽取与端到端验收

**决策摘要（2026-07-04 已确认）：**

| 决策点 | 选择 | 影响 |
|--------|------|------|
| 库位钻取数据源 | 复用现有 `GET /erp/wm/stock/list?locationId=` | 后端零改造；前端补薄封装 + 弹窗；3D 用户需具备 `wm:stock:list` 权限 |
| 推进节奏 | 先验证 MVP 端到端，再做 M2 | Phase 0 闭合后再开工 M2，避免在未验证基盖上楼 |
| 交付形式 | 写入新计划文档（本文件） | 与 2026-06-30 MVP 计划并列，互不污染 |

**Tech Stack:** 沿用 M1（Three.js + Vue 3.5 + TS + Element Plus + vxe-table），M3 新增 `InstancedMesh`。

---

## 一、任务清单

| # | 任务 | 优先级 | 阶段 | 交付物 | 验收标准 |
|---|------|--------|------|--------|----------|
| 0 | MVP 端到端验证 | 高 | P0 | 验证记录 | 种子 SQL→后端→前端全链路渲染四态，记录写入进度表 |
| 7 | 点击库位钻取库存明细 | 高 | P2 | `api/erp/wm/stock` + `WmLocationStockDialog.vue` + `3d.vue` click 拾取 | 点击占用库位弹出该库位库存列表（物料/批次/数量/预留/冻结） |
| 8a | 双击聚焦 | 中 | P2 | `3d.vue` dblclick + 相机 lerp | 双击库位相机平滑聚焦，无跳跃，hover 不受影响 |
| 8b | 着色模式切换 | 中 | P2 | `3d.vue` 工具栏下拉 | 状态/库区/占用率 三模式切换正确，仅改 material.color |
| 9 | 货架自动排版 | 中 | P3 | `components/rackLayout.ts` + `3d.vue` 接线 | 无坐标库位按 layer/col/row 自动排成立体货架，手填优先 |
| 10 | InstancedMesh 性能 | 中 | P3 | `3d.vue` InstancedMesh 路径 | 1000 库位 60fps，点击拾取定位正确 |
| 10b | location_id 索引确认 | 低 | P3 | `script/sql/update/*.sql`（若缺） | `wm_material_stock.location_id` 走索引 |
| 12 | composable 抽取 + 端到端验收 | 高 | P3 | `components/useWm3d.ts` + 验收记录 | 编译/lint/构建通过，全链路演示通过 |

---

## 二、详细任务分解

### 阶段 P0：MVP 端到端验证（任务 0）

> M1 进度表遗留 `⬜ 端到端联调待用户本地验证`。M2 开工前必须闭合，避免在未验证基盖上楼。

**步骤：**

- [ ] 本地执行 `doc/erp/sql/test-data/erp-wm-3d-demo-seed.sql`（幂等，落到 ry-erp 库）
- [ ] 启动后端：`mvn spring-boot:run -P local`（或 IDE 运行 `DromaraApplication`）
- [ ] 启动前端：`npm run dev`，登录后进入 仓库管理 → 仓库列表 → 点"3D预览"
- [ ] 验证点：
  - [ ] 3D 画布渲染出 1 仓库 + 2 库区 + 18 库位
  - [ ] 四态颜色肉眼可区分（空置灰/占用蓝/预警橙/锁定红），与种子数据一致
  - [ ] 鼠标旋转/缩放/平移正常
  - [ ] hover 库位浮窗显示状态/库区/在库数量/预留/库存记录数
  - [ ] "重置视角"恢复初始取景
  - [ ] 离开页面控制台无报错、无 WebGL context 泄漏
- [ ] 将验证结果（含截图/录屏或文字描述）写入本文档"实施进度"章节

**异常处置：**
- 若聚合接口返回空：核查种子 SQL 是否执行到 ry-erp 库、`@DS` 数据源、tenant_id 隔离
- 若库位全灰（EMPTY）：核查 `wm_material_stock` 种子数据 `location_id` 与 `wm_storage_location` 是否对齐
- 若 z-fighting/闪烁：M1 已加 GAP=0.98 与 depthWrite=false 兜底，仍有问题记录复现条件

**验收：** 上述验证点全部通过，或发现问题记入进度表并据实决定是否回补 M1 修复后再进 M2。

---

### 阶段 P2：M2 交互增强（任务 7、8a、8b）

#### 任务 7：点击库位钻取库存明细

**复用决策：** 后端零改造，复用 `GET /erp/wm/stock/list`（`WmMaterialStockController.java:50`，权限 `wm:stock:list`，`WmMaterialStockBo` 已支持 `locationId` 过滤 `:118`，Service `buildQueryWrapper` 第 161 行 `lqw.eq(locationId)` 生效）。

**Files:**
- Reuse: `plus-ui/src/api/erp/wm/stock/index.ts`（**已存在** `listStock`，复用 `/erp/wm/stock/list`，无需新建）
- Modify: `plus-ui/src/api/erp/wm/stock/types.ts`（`StockQuery` 增 `locationId?: number`，与库存页 queryParams 已有用法对齐）
- Create: `plus-ui/src/views/erp/wm/components/WmLocationStockDialog.vue`（库位库存明细弹窗）
- Modify: `plus-ui/src/views/erp/wm/warehouse/3d.vue`（增 `click` 拾取 → 打开弹窗）

**`WmLocationStockDialog.vue` 契约：**

```vue
<template>
  <el-dialog v-model="visible" :title="`${locationName} 库存明细`" width="900px" append-to-body>
    <vxe-table :data="rows" :loading="loading" border stripe>
      <vxe-column field="materialCode" title="物料编码" />
      <vxe-column field="materialName" title="物料名称" />
      <vxe-column field="materialSpec" title="规格型号" />
      <vxe-column field="batchCode" title="批次号" />
      <vxe-column field="stockQuantity" title="在库数量" align="right" />
      <vxe-column field="baseUomName" title="单位" />
      <vxe-column field="frozenFlag" title="冻结" :formatter="frozenFmt" />
      <vxe-column field="recptDate" title="入库时间" />
      <vxe-column field="expireDate" title="有效期" />
    </vxe-table>
    <pagination v-show="total > 0" :total="total" v-model:page="pageNum" v-model:limit="pageSize" @pagination="load" />
  </el-dialog>
</template>

<script setup lang="ts">
// props: { modelValue, locationId, locationName }
// 打开时调 listStock({ locationId, pageNum, pageSize })，分页加载
// 字段全部来自 WmMaterialStockVo（materialCode/Name/Spec/batchCode/stockQuantity/baseUomName/frozenFlag/recptDate/expireDate）
</script>
```

**`3d.vue` click 拾取：**

```ts
// 复用现有 raycaster + pointer，新增 click 监听
const onPointerDown = (e: PointerEvent) => {
  // 排除拖动（pointerdown→up 位移 < 阈值才算 click，避免与 OrbitControls 旋转冲突）
  ...
  raycaster.setFromCamera(pointer, camera);
  const hits = raycaster.intersectObjects(locationMeshes, false);
  if (hits.length > 0) {
    const loc = hits[0].object.userData.loc as WarehouseLocation3dVO;
    stockDialog.locationId = loc.locationId;
    stockDialog.locationName = loc.locationName || loc.locationCode;
    stockDialog.visible = true;
  }
};
container.addEventListener('pointerdown', onPointerDown);
```

> **坑：** Three.js 中 click 与 OrbitControls 拖拽易冲突。用 pointerdown 记录起点坐标，pointerup 比较位移，<5px 才判定为点击，否则视为拖拽丢弃。M1 已有 `pointermove`，注意事件不互斥。

**权限提示：** 钻取依赖 `wm:stock:list`。3D 预览本身只校验 `wm:warehouse:query`。若用户无 `wm:stock:list`，弹窗调用会 403 → 弹窗内用 `v-loading` + 错误提示"无库存查看权限"，不阻塞 3D 浏览。**不在此处强行提权。**

**验收：**
- [ ] 点击占用库位弹出库存明细弹窗，数据与该库位 `wm_material_stock` 记录一致
- [ ] 分页可用（库位库存 > pageSize 时）
- [ ] 拖拽旋转视角不会误触弹窗
- [ ] 无 `wm:stock:list` 权限用户看到友好提示而非白屏
- [ ] 弹窗关闭后 3D 交互恢复正常

---

#### 任务 8a：双击聚焦

**Files:** Modify `plus-ui/src/views/erp/wm/warehouse/3d.vue`

**实现：**

```ts
const focusTarget = (loc: WarehouseLocation3dVO) => {
  const mesh = locationMeshes.find(m => m.userData.loc === loc);
  if (!mesh || !camera || !controls) return;
  const center = mesh.position.clone();
  // 相机沿当前视线方向反向退到一个合适距离，target 平滑移到库位中心
  const offset = camera.position.clone().sub(controls.target).normalize().multiplyScalar(6);
  animateCamera(camera.position, center.clone().add(offset), center);
};

// 手写 lerp 动画（避免引入 tween.js 依赖）
const animateCamera = (fromPos: THREE.Vector3, toPos: THREE.Vector3, toTarget: THREE.Vector3) => {
  const start = performance.now();
  const dur = 500;
  const step = (now: number) => {
    const t = Math.min((now - start) / dur, 1);
    const e = 1 - Math.pow(1 - t, 3); // easeOutCubic
    camera!.position.lerpVectors(fromPos, toPos, e);
    controls!.target.lerp(toTarget, e);
    controls!.update();
    if (t < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
};

container.addEventListener('dblclick', onDblClick); // 命中库位 → focusTarget(loc)
```

**验收：**
- [ ] 双击库位相机平滑（500ms easeOutCubic）聚焦，无瞬移
- [ ] 聚焦后仍可继续旋转/缩放
- [ ] 双击空白处不触发聚焦
- [ ] 连续双击不同库位，动画不抖动（后一次打断前一次）

---

#### 任务 8b：着色模式切换

**Files:** Modify `plus-ui/src/views/erp/wm/warehouse/3d.vue`

> 补齐 M1 计划 P2 工具栏规格遗留的"按状态/按库区/按占用率"切换。

**工具栏：**

```vue
<el-select v-model="colorMode" size="small" @change="recolorLocations">
  <el-option label="按状态" value="status" />
  <el-option label="按库区" value="area" />
  <el-option label="按占用率" value="occupancy" />
</el-select>
```

**重绘逻辑（仅改 material.color，不重建几何体）：**

```ts
const recolorLocations = () => {
  for (const mesh of locationMeshes) {
    const loc = mesh.userData.loc as WarehouseLocation3dVO;
    const area = mesh.userData.area; // 需在 addLocationMesh 时挂 area 引用
    (mesh.material as THREE.MeshStandardMaterial).color.setHex(computeColor(loc, area, colorMode));
  }
};

// 占用率模式：occupancyRate 0→绿, 0.8→黄, 1→红，HSL 插值
const computeColor = (loc, area, mode): number => {
  if (mode === 'status') return statusColor(loc, loc.status || 'EMPTY');
  if (mode === 'area') return areaColorHex(area); // 库区 color 字段，缺省按 areaId 哈希取色
  if (mode === 'occupancy') return occupancyGradient(loc.occupancyRate ?? 0);
  return 0x909399;
};
```

> **前置：** `addLocationMesh` 当前 `userData` 只挂了 `loc` + `areaName`，需补挂 `area`（整个库区对象）以支持"按库区"着色。

**验收：**
- [ ] 三种模式切换后库位颜色正确更新，库区/地面/标签不受影响
- [ ] 占用率模式：occupancyRate 越高越偏红，空库位（rate=0）偏绿
- [ ] 切换不重建几何体，无卡顿（500 库位 < 100ms）
- [ ] hover 高亮在任一模式下仍生效（emissive 叠加）

---

### 阶段 P3：M3 完整版（任务 9、10、10b、12）

#### 任务 9：货架自动排版

**Files:**
- Create: `plus-ui/src/views/erp/wm/components/rackLayout.ts`
- Modify: `plus-ui/src/views/erp/wm/warehouse/3d.vue`（库位坐标缺省时调用自动排版）

**`rackLayout.ts`：**

```ts
import type { WarehouseLocation3dVO, WarehouseArea3dVO } from '@/api/erp/wm/warehouse/types';

/** 同库区内，按 rackId 分组，layer/col/row → (y, z, x) 排成立体货架 */
export function autoLayoutRack(
  locations: WarehouseLocation3dVO[],
  area: WarehouseArea3dVO,
  cellSize: { w: number; h: number; d: number }
): void {
  const ox = Number(area.posX) || 0;
  const oy = Number(area.posY) || 0;
  const oz = Number(area.posZ) || 0;
  // 按 rackId 分组
  const groups = new Map<string, WarehouseLocation3dVO[]>();
  for (const loc of locations) {
    const key = loc.rackId || '__default__';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(loc);
  }
  // 每个 rack 占一列（x 方向递进），组内 layer→y、row→z、col→x 微调
  let rackIdx = 0;
  for (const [, locs] of groups) {
    for (const loc of locs) {
      if (hasManualCoord(loc)) continue; // 手填坐标优先
      const layer = Number(loc.layer) || 1;
      const col = Number(loc.col) || 1;
      const row = Number(loc.rowNum) || 1;
      loc.posX = ox + (rackIdx + (col - 1) * 0.0) * cellSize.w; // 简化：单列货架
      loc.posY = oy + (layer - 1) * cellSize.h;
      loc.posZ = oz + (row - 1) * cellSize.d;
    }
    rackIdx++;
  }
}

const hasManualCoord = (loc: WarehouseLocation3dVO) =>
  [loc.posX, loc.posY, loc.posZ].some(v => v != null && Number(v) !== 0);
```

> **优先级策略：** 手填坐标（posX/Y/Z 任一非 0）优先，缺省才走自动排版。开箱即用 + 支持手工微调。cellSize 默认从库位 width/height/locationDepth 推导，或从 area.configJson 读取。

**`3d.vue` 接线（建场景前对每个 area 的 locations 调一次）：**

```ts
for (const area of areas) {
  autoLayoutRack(area.locations || [], area, { w: 1.2, h: 1.2, d: 1.2 });
  addAreaMesh(area, bounds);
  ...
}
```

**验收：**
- [ ] 给一组库位 layer/col/row 但 posX/Y/Z 全 0，渲染为立体货架（多层多列）
- [ ] 手填坐标的库位不受自动排版影响
- [ ] 同 rackId 的库位聚到同一货架，不同 rackId 在 x 方向错开
- [ ] 自动排版后 hover/click/双击聚焦坐标正确

---

#### 任务 10：InstancedMesh 大仓库性能

**Files:** Modify `plus-ui/src/views/erp/wm/warehouse/3d.vue`

> 当前逐库位一个 `Mesh`，>500 库位场景 drawcall 与内存压力大。改用 `InstancedMesh` 单 drawcall 渲染所有库位。

**实现要点：**

```ts
const LOCATION_THRESHOLD = 500; // 库位数超过阈值走 InstancedMesh

const buildLocationInstancedMesh = (locations: WarehouseLocation3dVO[]) => {
  const geo = new THREE.BoxGeometry(cellW, cellH, cellD);
  const mat = new THREE.MeshStandardMaterial({ roughness: 0.6 });
  const instanced = new THREE.InstancedMesh(geo, mat, locations.length);
  const dummy = new THREE.Object3D();
  const color = new THREE.Color();
  locations.forEach((loc, i) => {
    dummy.position.set(...);
    dummy.rotation.set(...);
    dummy.updateMatrix();
    instanced.setMatrixAt(i, dummy.matrix);
    color.setHex(statusColor(loc, loc.status || 'EMPTY'));
    instanced.setColorAt(i, color);
    // 建立 instanceId → location 映射
    instanceIdMap.set(i, loc);
  });
  instanced.instanceMatrix.needsUpdate = true;
  if (instanced.instanceColor) instanced.instanceColor.needsUpdate = true;
  scene.add(instanced);
  return instanced;
};

// Raycaster 对 InstancedMesh 返回 intersection.instanceId
const onPointerMove = (e) => {
  const hits = raycaster.intersectObjects([instancedMesh], false);
  if (hits.length > 0) {
    const id = hits[0].instanceId!;
    const loc = instanceIdMap.get(id);
    // hover 高亮：临时把该 instance 的 color 提亮（需保存原色，移出恢复）
  }
};
```

**InstancedMesh 下的 hover/click/聚焦：**
- `intersectObjects` 对 `InstancedMesh` 返回 `intersection.instanceId`，用 `instanceIdMap` 映射回 `loc`
- hover 高亮：用 `setColorAt(id, highlightedColor)` + `instanceColor.needsUpdate = true`，移出恢复原色
- 双击聚焦：同上取 loc，复用任务 8a 的 `focusTarget`
- click 钻取：同上取 loc，复用任务 7 的弹窗

> **取舍：** InstancedMesh 共享几何体与材质，所有库位尺寸需一致或归类。若库位尺寸差异大，按尺寸分桶建多个 InstancedMesh（如大/中/小三档）。MVP 种子库位尺寸一致，单 InstancedMesh 足够。

**验收：**
- [ ] 1000 库位场景首屏 < 2s，旋转/缩放 60fps（开发机基准）
- [ ] InstancedMesh 模式下 hover/click/双击聚焦均定位到正确库位
- [ ] 着色模式切换（任务 8b）在 InstancedMesh 下通过 `setColorAt` 批量更新
- [ ] 库位数 < 阈值时仍走逐 Mesh 路径（保持简单场景的可读性）

---

#### 任务 10b：location_id 索引确认

**Files:** Possibly Create: `script/sql/update/erp-wm-stock-location-index.sql`

> 前序计划"实施前确认项"遗留：`wm_material_stock` 是否已对 `location_id` 建索引。大仓库聚合查询 `aggregateStockByWarehouse`（GROUP BY location_id）性能依赖此索引。

**步骤：**
- [ ] 查 `wm_material_stock` 现有索引：`SHOW INDEX FROM wm_material_stock;`
- [ ] 若 `location_id` 无索引，补增量脚本（幂等，参照 CONTRIBUTING §5）：

```sql
-- 适用 ry-erp 库；幂等
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'wm_material_stock' AND index_name = 'idx_wm_material_stock_location');
SET @sql := IF(@idx = 0,
  'ALTER TABLE wm_material_stock ADD INDEX idx_wm_material_stock_location (location_id, del_flag)',
  'SELECT ''idx_wm_material_stock_location already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

**验收：** `EXPLAIN` 聚合查询确认走 `idx_wm_material_stock_location`。

---

#### 任务 12：composable 抽取 + 端到端验收

**Files:**
- Create: `plus-ui/src/views/erp/wm/components/useWm3d.ts`（场景管理 composable）
- Refactor: `plus-ui/src/views/erp/wm/warehouse/3d.vue`（瘦身，调用 composable）

**`useWm3d.ts` 职责（从 `3d.vue` 抽出）：**
- `initScene(container)` / `disposeScene()` / `onResize()`
- `loadWarehouse(warehouseId)` → 聚合数据 + 建场景
- `recolorLocations(mode)` / `focusTarget(loc)` / `dispose()`
- 暴露 `viewData`、`loading`、`ready`、`tooltip`、事件回调（onSelectLocation / onDblClickLocation）
- 内部封装 InstancedMesh vs Mesh 分支

**目的：** `3d.vue` 当前 560 行，M2/M3 完成后预计 >900 行。抽 composable 后 `3d.vue` 仅保留工具栏 UI + 弹窗编排，便于维护与测试。

**端到端验收：**
- [ ] `mvn compile -pl ruoyi-modules/ruoyi-erp -am` 通过（后端 M2/M3 零改动，回归确认）
- [ ] `npm run build:prod` 构建通过（three.js 仍懒加载独立 chunk）
- [ ] `npm run lint:eslint` 对 `plus-ui/src/views/erp/wm/**`、`plus-ui/src/api/erp/wm/stock/**` 无 error
- [ ] 端到端全链路：仓库列表 → 3D预览 → 渲染四态 → 切换着色模式 → hover → 双击聚焦 → 点击钻取库存明细 → 关闭弹窗 → 重置视角 → 离开无泄漏
- [ ] 1000 库位压测（可用种子脚本批量生成）60fps
- [ ] 验证记录写入本文档"实施进度"章节

---

## 三、接口复用说明

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 仓库 3D 聚合数据 | GET | `/erp/wm/warehouse/{id}/3d` | `wm:warehouse:query` | M1 已交付，M2/M3 不改 |
| 库位库存明细 | GET | `/erp/wm/stock/list?locationId=` | `wm:stock:list` | **复用现有**，任务 7 钻取，后端零改造 |

> **复用原则：** M2/M3 后端零新增接口。仅前端补 `api/erp/wm/stock` 薄封装（任务 7）。

---

## 四、数据模型复用说明

本计划**不新建业务表、不修改现有表结构**（任务 10b 仅可能补索引，属性能优化非结构变更）。3D 字段与库存聚合数据源沿用 M1：

- `wm_storage_area` / `wm_storage_location`：3D 坐标/尺寸/颜色/货架结构字段（M1 已确认就位）
- `wm_material_stock`：`stock_quantity`/`reserved_quantity`/`frozen_flag`/`location_id`（钻取与聚合数据源）

> 以 `script/sql/ry-erp.sql` 为 DDL 权威来源（CONTRIBUTING §6.1 单一数据源）。

---

## 五、工作量估算

| 阶段 | 工作项 | 人天 |
|------|--------|------|
| P0 | MVP 端到端验证 | 0.5 |
| P2 | 点击钻取 + 双击聚焦 + 着色模式切换 | 2–3 |
| P3 | 货架自动排版 + InstancedMesh + 索引确认 | 3–5 |
| P3 | composable 抽取 + 端到端验收 | 1–1.5 |
| **M2 合计（P0+P2）** | | **约 2.5–3.5** |
| **M3 合计（P3）** | | **约 4–6.5** |
| **总计** | | **约 6.5–10** |

---

## 六、分阶段交付里程碑

| 里程碑 | 范围 | 交付物 | 估算 |
|--------|------|--------|------|
| M0 验证 | P0 | MVP 端到端验证记录，问题清单 | 0.5 人天 |
| M2 交互增强 | P2 | 点击钻取 + 双击聚焦 + 着色模式切换 | 2–3 人天 |
| M3 完整版 | P3 | 自动排版 + InstancedMesh + composable + 验收 | 4–6.5 人天 |

> M2 完成即可交付"能查能定位"的可用版本；M3 为大仓库场景的性能与可维护性收尾。

---

## 七、风险与应对

| 风险 | 说明 | 应对 |
|------|------|------|
| click 与 OrbitControls 拖拽冲突 | 旋转视角误触弹窗 | pointerdown/up 位移阈值 <5px 才判定 click |
| InstancedMesh 下 hover 高亮 | 共享材质，单 instance 高亮需 instanceColor | 用 `setColorAt` 临时提亮，保存原色移出恢复 |
| 库位尺寸差异大 | InstancedMesh 共享几何体 | 按尺寸分桶建多个 InstancedMesh |
| 钻取权限耦合 | 3D 用户可能无 `wm:stock:list` | 弹窗内捕获 403 给友好提示，不阻塞 3D 浏览 |
| 自动排版坐标冲突 | 手填与自动混排可能重叠 | 手填优先，自动排版只补缺省；提供"全部自动"开关（可选） |
| composable 抽取回归风险 | 重构可能破坏 M1 行为 | 抽取后跑 P0 验证点回归 |
| 大仓库压测数据 | 现有种子仅 18 库位 | 编写批量生成脚本（M3 验收前） |

---

## 八、明确排除项（本计划不做）

- 不新建/修改 wm 业务表结构（仅可能补 location_id 索引）
- 不新增后端接口（钻取复用 `/erp/wm/stock/list`）
- 不做 CUSTOM(GLTF) 模型加载（列 M3 之后增强）
- 不做 SSE 实时库存刷新（列后续增强）
- 不做路径规划/拣货动线可视化（列后续增强）
- 不做 VR/AR（列后续增强）

---

## 九、实施前确认项

- [ ] P0 验证：种子 SQL 执行无误、四态着色与种子数据一致
- [ ] 任务 10b：`wm_material_stock.location_id` 索引现状（`SHOW INDEX`）
- [ ] 任务 7：确认 3D 目标用户角色是否已分配 `wm:stock:list` 权限（影响钻取可用性）
- [ ] 任务 10：大仓库压测库位尺寸是否一致（决定 InstancedMesh 分桶策略）

---

## 十、实施进度更新

> 执行阶段在此记录各任务状态与验证结果。

| 任务 | 状态 | 执行日期 | 结果 |
|------|------|----------|------|
| 0 MVP 端到端验证 | ✅ | 2026-07-04 | 用户本地验证通过：种子 SQL→后端→前端 3D 预览全链路渲染四态 |
| 7 点击钻取 | ✅ | 2026-07-04 | 复用 listStock({locationId})；StockQuery 增 locationId；WmLocationStockDialog 弹窗（el-table+分页+dict-tag 冻结）；3d.vue pointerdown/up 位移<5px 判定 click→raycast→开弹窗，250ms 延迟避让双击；无 wm:stock:list 权限时弹窗友好提示 |
| 8a 双击聚焦 | ✅ | 2026-07-04 | dblclick 取消待执行 click→focusLocation→animateCamera easeOutCubic 500ms lerp；相机沿视线退 6 单位，target 移库位中心 |
| 8b 着色模式切换 | ✅ | 2026-07-04 | 工具栏 el-select 状态/库区/占用率；recolorLocations 仅改 material.color 不重建几何体；占用率按 stockQuantity 归一化绿→红；addLocationMesh userData 挂 area 引用 |
| 9 货架自动排版 | ✅ | 2026-07-04 | components/rackLayout.ts：autoLayoutRack 按 rackId 分组，col→X/layer→Y/row→Z，居中公式 (idx-(max+1)/2)*cell 与 seed 手填坐标一致可互验；手填优先，3d.vue 建场景前调用 |
| 10 InstancedMesh | 🗑️ | 2026-07-04 | 延后：库位>500 才触发，现有环境（18 库位）无法验证，属未测代码；待有真实大仓库时再做 |
| 10b 索引确认 | ✅ | 2026-07-04 | script/sql/update_wm_material_stock_location_index.sql：幂等创建 idx_wm_material_stock_location(location_id, del_flag)；用户已执行 + EXPLAIN 核对通过 |
| 12 composable + 验收 | 🗑️ | 2026-07-04 | 延后：纯重构无新功能，回归风险高于收益；3d.vue 维护成本可接受时再做 |

### M3 交付范围与延后决策 — 2026-07-04

**交付（✅）：** 任务 9 货架自动排版 + 任务 10b location_id 索引脚本
**延后（🗑️）：** 任务 10 InstancedMesh、任务 12 composable 抽取

**延后理由（用户 2026-07-04 确认）：**
- InstancedMesh 仅在库位 >500 时触发（阈值），现有演示环境 18 库位走不到该路径，无法验证，ship 未测代码风险高。待出现真实大仓库（≥500 库位）时再实现，届时同步编写压测种子验证 60fps 与交互拾取。
- composable 抽取为纯重构，无用户可见价值，且 M1/M2/M3-自动排版均依赖现有 3d.vue 结构，回归风险高于维护收益。

**M3 静态验证记录 — 2026-07-04：**
- ✅ `npx eslint` 对 `rackLayout.ts`、`3d.vue` EXIT=0
- ✅ `npm run build:prod` 通过：`✓ built in 41.16s`，3D 懒加载 chunk 548KB（gzip 139KB）独立分包
- ✅ 任务 9 运行时验证：用户测试通过（公式与 seed 手填坐标一致，可互验）
- ✅ 任务 10b 运行时验证：用户已执行索引 SQL + EXPLAIN 核对通过

### M3 交付物

| 文件 | 类型 |
|------|------|
| `plus-ui/src/views/erp/wm/components/rackLayout.ts` | 新建：货架自动排版工具 |
| `plus-ui/src/views/erp/wm/warehouse/3d.vue` | 修改：建场景前调用 autoLayoutRack |
| `script/sql/update_wm_material_stock_location_index.sql` | 新建：location_id 索引幂等脚本 |

### M2 静态验证记录 — 2026-07-04

- ✅ `npx eslint` 对 `3d.vue`、`WmLocationStockDialog.vue`、`stock/types.ts` 三文件 EXIT=0（无 error）
- ✅ `npm run build:prod` 构建通过：`✓ 4601 modules transformed`、`✓ built in 41.51s`
- ✅ 3D 懒加载 chunk `3d-DCqU3MsA.js`（548KB，含 three.js，gzip 135KB）独立分包，不影响首屏
- ✅ `WmLocationStockDialog.vue` 已打包
- ✅ 运行时验证：用户测试通过——点击钻取/双击聚焦/着色切换三功能与权限降级路径均正常

### M2 交付物

| 文件 | 类型 |
|------|------|
| `plus-ui/src/api/erp/wm/stock/types.ts` | 修改：StockQuery 增 locationId |
| `plus-ui/src/views/erp/wm/components/WmLocationStockDialog.vue` | 新建：库位库存明细弹窗 |
| `plus-ui/src/views/erp/wm/warehouse/3d.vue` | 修改：click 钻取 + dblclick 聚焦 + 着色模式切换 + userData 挂 area |

---

**文档生成时间**: 2026-07-04
**执行者**: AI Agent
**审核状态**: 草稿
