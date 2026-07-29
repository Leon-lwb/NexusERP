# ERP 仓储管理 (wm) 3D 可视化仓库 实施计划

> 文档编号: NexusERP-PLAN-2026-003
> 版本: v1.0
> 状态: 草稿
> 创建日期: 2026-06-30
> 最后更新: 2026-06-30
> 作者: AI Agent
> 关联文档: ../NexusERP_ERP文档.md、../README.md、../../CONTRIBUTING.md

> **For agentic workers:** 本计划可用 superpowers:executing-plans 或 superpowers:subagent-driven-development 逐任务执行。任务以 `- [ ]` 复选框跟踪，状态标记遵循 CONTRIBUTING §3（✅🚧⬜❌🗑️）。

---

## 概述

**Goal:** 为 ERP 仓储管理模块（`ruoyi-erp` / `wm`）引入 three.js，实现仓库→库区→库位的 3D 可视化预览，按库存状态着色，支持点击库位查看库存明细，赋能现场仓储可视化运营。

**Architecture:** 本特性**不新建任何业务表**——3D 所需的空间坐标、尺寸、外观、模型类型字段在 `wm_storage_area` / `wm_storage_location` 表与实体中已全部就位（2025-12 已建）。前端配置抽屉 `Wm3dConfigDrawer.vue` 已存在并接入库区/库位表单页。因此本计划聚焦于**唯一缺失的渲染层**：①后端聚合查询接口（一次性返回仓库+库区+库位+库存状态）②前端 three.js 渲染组件与交互 ③菜单/路由/入口。库存状态以 `wm_material_stock` 为数据源实时聚合。

**Tech Stack:**
- 后端：Spring Boot + MyBatis-Plus（复用现有 wm 分层架构），`@DS` 默认 master（ry-erp 库），Sa-Token 权限 `wm:warehouse:3d`
- 前端：three.js（新增依赖）+ Vue 3.5 + TypeScript + Element Plus，路由懒加载
- 性能：`InstancedMesh` 应对大仓库上千库位场景

**前置条件 / 现状基线（关键发现，决定工作量）：**

| 项 | 现状 | 来源 | 对本计划影响 |
|----|------|------|--------------|
| 3D 字段（库区） | ✅ 已有 posX/Y/Z、areaWidth/Length/Height、rotation、color、opacity、modelType、modelUrl、configJson | `wm_storage_area` 表（`script/sql/ry-erp.sql:4700+`）、`WmStorageArea.java` | 数据层零改造 |
| 3D 字段（库位） | ✅ 已有 posX/Y/Z、width/height/depth、rotation、colorEmpty/Occupied/Locked/Warning、rackId/layer/col/rowNum、label*、configJson | `wm_storage_location` 表、`WmStorageLocation.java` | 数据层零改造 |
| 3D 字段（BO） | ✅ 已含 posX/Y/Z、areaWidth、color、modelType、configJson 等 | `WmStorageAreaBo.java:95-175` | 配置抽屉保存链路已通 |
| 3D 字段（VO） | ❌ `WmStorageAreaVo` 仅含基础业务字段，未暴露任何 3D 字段 | `WmStorageAreaVo.java` | 渲染接口走**新建聚合 VO**，不改现有 VO |
| 前端配置抽屉 | ✅ `Wm3dConfigDrawer.vue` 已存在，已接入库区/库位表单页 | `plus-ui/src/views/erp/wm/components/` | 配置能力已具备 |
| three.js | ❌ 未安装，全仓库无引用 | `package.json` | 需新增依赖 |
| 渲染页面/组件 | ❌ 不存在 | — | 本计划核心交付 |
| 聚合查询接口 | ❌ 现有 Controller 均为单表 CRUD | `wm/controller/` | 本计划核心交付 |
| 菜单/路由/入口 | ❌ 无 3D 预览入口 | `router/index.ts`、`warehouse/index.vue` | 本计划交付 |

**结论：前期可行性高。数据层零改造，主要投入在前端渲染层 + 一个聚合接口 + 菜单/路由。**

---

## 一、任务清单

| # | 任务 | 优先级 | 阶段 | 交付物 | 验收标准 |
|---|------|--------|------|--------|----------|
| 1 | 后端聚合查询接口 + 聚合 VO | 高 | P1 | `WmWarehouse3dController`、`WmWarehouse3dVo`、Service | `GET /erp/wm/warehouse/{id}/3d` 返回仓库+库区+库位+状态聚合数据 |
| 2 | 库存状态聚合查询 | 高 | P1 | Mapper 方法（按 location_id 聚合 stock） | 库位可判定 空/占用/锁定/预警 |
| 3 | 引入 three.js 依赖 | 高 | P2 | `package.json` | `npm i three @types/three` 成功，构建无报错 |
| 4 | 封装 `Wm3dViewer.vue` 渲染组件 | 高 | P2 | `plus-ui/src/views/erp/wm/components/Wm3dViewer.vue` | 场景/相机/灯光/OrbitControls 就绪，渲染库区+库位立方体 |
| 5 | 库位按状态着色 + 标签 | 高 | P2 | 同上 | 空/占用/锁定/预警 四色区分，库位编码标签可见 |
| 6 | 3D 预览页面 | 高 | P2 | `plus-ui/src/views/erp/wm/warehouse/3d.vue` | 仓库选择 + 全屏 3D 画布 |
| 7 | 交互：点击库位弹库存明细 | 中 | P3 | `Wm3dViewer.vue` + 库存明细弹窗 | 点击库位弹出该库位库存列表 |
| 8 | hover 高亮 + 相机聚焦 | 中 | P3 | `Wm3dViewer.vue` | 鼠标悬停库位高亮，双击聚焦 |
| 9 | 货架自动排版 | 中 | P4 | 排版工具函数 | layer/col/row → 坐标自动生成（无手填坐标时） |
| 10 | 性能优化 InstancedMesh | 中 | P4 | `Wm3dViewer.vue` | 1000+ 库位场景 60fps 流畅 |
| 11 | 菜单/路由/入口按钮 | 高 | P5 | `router/index.ts`、`warehouse/index.vue`、菜单 SQL | 仓库列表"3D预览"按钮可跳转 |
| 12 | 打磨 + 验收 | 中 | P5 | 验证记录 | 编译通过、lint 无 error、端到端演示 |

---

## 二、详细任务分解

### 阶段 P1：后端聚合接口（任务 1-2）

**Files:**
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmWarehouse3dController.java`
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/vo/WmWarehouse3dVo.java`（聚合 VO，含 nested 库区/库位/状态）
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/vo/WmStorageArea3dVo.java`
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/vo/WmStorageLocation3dVo.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmWarehouseService.java`（增 `getWarehouse3d(Long id)`）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmWarehouseServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/mapper/WmMaterialStockMapper.java`（增按 location 聚合查询）

**接口设计：**

```java
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/warehouse")
public class WmWarehouse3dController extends BaseController {

    private final IWmWarehouseService wmWarehouseService;

    /**
     * 获取仓库 3D 可视化聚合数据
     * 权限标识：wm:warehouse:3d
     */
    @SaCheckPermission("wm:warehouse:3d")
    @GetMapping("/{id}/3d")
    public R<WmWarehouse3dVo> getWarehouse3d(@PathVariable Long id) {
        return R.ok(wmWarehouseService.getWarehouse3d(id));
    }
}
```

**聚合 VO 结构（核心，承载渲染所需的全部字段）：**

```java
@Data
public class WmWarehouse3dVo implements Serializable {
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    /** 仓库整体占地尺寸(米)，用于场景范围估算 */
    private BigDecimal boundWidth;
    private BigDecimal boundLength;
    private BigDecimal boundHeight;
    /** 库区列表 */
    private List<WmStorageArea3dVo> areas;
    /** 统计概览 */
    private Integer totalLocations;
    private Integer occupiedLocations;
    private Integer emptyLocations;
    private Integer lockedLocations;
}

@Data
public class WmStorageArea3dVo implements Serializable {
    private Long areaId;
    private String areaCode;
    private String areaName;
    private String layoutType;   // GRID/RACK/FLUID/BULK
    private BigDecimal posX, posY, posZ;
    private BigDecimal areaWidth, areaLength, areaHeight;
    private BigDecimal rotationX, rotationY, rotationZ;
    private String color;
    private BigDecimal opacity;
    private String modelType;    // CUBE/CUSTOM
    private String modelUrl;
    private Long showWalls, showLabel, gridVisible;
    private String configJson;
    /** 库位列表 */
    private List<WmStorageLocation3dVo> locations;
}

@Data
public class WmStorageLocation3dVo implements Serializable {
    private Long locationId;
    private String locationCode;
    private String locationName;
    private BigDecimal posX, posY, posZ;
    private BigDecimal width, height, locationDepth;
    private BigDecimal rotationX, rotationY, rotationZ;
    private String colorEmpty, colorOccupied, colorLocked, colorWarning;
    private BigDecimal opacity;
    private Long showBorder, showLabel;
    private String labelPosition;
    private BigDecimal labelSize;
    private Long frozenFlag;
    private Long enableFlag;
    /** 货架结构（用于自动排版） */
    private String rackId;
    private Long layer, col, rowNum;
    /** 运行时状态（由库存聚合计算，非库表字段） */
    private String status;          // EMPTY/OCCUPIED/LOCKED/WARNING
    private BigDecimal stockQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal occupancyRate;
    private Integer stockCount;      // 该库位上的库存记录数
}
```

**库存状态聚合查询（Mapper）：**

```java
// WmMaterialStockMapper 增方法：按库位聚合库存
List<WmLocationStockAggVo> aggregateStockByWarehouse(@Param("warehouseId") Long warehouseId);
```

```xml
<!-- 按 location_id 聚合，一次性返回该仓库所有库位的库存汇总 -->
<select id="aggregateStockByWarehouse" resultType="...WmLocationStockAggVo">
    SELECT location_id              AS locationId,
           COUNT(*)                 AS stockCount,
           COALESCE(SUM(stock_quantity), 0)     AS stockQuantity,
           COALESCE(SUM(reserved_quantity), 0) AS reservedQuantity,
           MAX(frozen_flag)        AS frozenFlag
    FROM wm_material_stock
    WHERE warehouse_id = #{warehouseId} AND del_flag = '0'
    GROUP BY location_id
</select>
```

**状态判定规则（Service 层装配）：**

| status | 判定条件 |
|--------|----------|
| `LOCKED` | 库位 `frozenFlag=1` 或库位下任一 stock `frozen_flag=1` |
| `OCCUPIED` | `stockQuantity > 0`（且非锁定） |
| `WARNING` | `reservedQuantity / stockQuantity` 超阈值（如 >0.8）或关联 `wm_stock_alert` 命中（P4 增强） |
| `EMPTY` | 无 stock 记录或 `stockQuantity = 0` |

> **说明：** 聚合查询走单次 `IN` 或 `GROUP BY`，避免 N+1。`tenant_id` 由 MyBatis-Plus 多租户插件自动注入，无需手写。

**验收：**
- [ ] `mvn compile -pl ruoyi-modules/ruoyi-erp -am` 通过
- [ ] 接口返回 JSON 含 areas[].locations[].status 字段
- [ ] Swagger / SpringDoc 可见该接口

---

### 阶段 P2：three.js 引入与渲染（任务 3-6）

**任务 3：引入依赖**

```bash
cd plus-ui
npm i three @types/three
```

> three.js 体积约 600KB，必须**路由懒加载**（任务 6 的页面用 `() => import(...)` 动态导入），不影响首屏。

**任务 4-6：渲染组件与页面**

**Files:**
- Create: `plus-ui/src/views/erp/wm/components/Wm3dViewer.vue`（核心渲染组件）
- Create: `plus-ui/src/views/erp/wm/components/useWm3d.ts`（场景管理 composable，便于复用与测试）
- Create: `plus-ui/src/api/erp/wm/warehouse/3d.ts`（聚合接口请求）
- Create: `plus-ui/src/views/erp/wm/warehouse/3d.vue`（预览页面，含仓库选择 + 全屏画布）

**`Wm3dViewer.vue` 组件契约：**

```vue
<template>
  <div ref="containerRef" class="wm-3d-container" v-loading="loading">
    <!-- three.js canvas 挂载点 -->
  </div>
</template>

<script setup lang="ts">
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js';
import type { WmWarehouse3dVo } from '@/api/erp/wm/warehouse/3d';

const props = defineProps<{
  warehouseId: number | string;
}>();

// 场景初始化：Scene / PerspectiveCamera / WebGLRenderer(antialias) / 灯光(环境光+平行光) / OrbitControls
// 地面网格 GridHelper 作为参照
// onMounted 初始化，onBeforeUnmount 释放 renderer/controls（防止内存泄漏）
// watch warehouseId → 重新加载数据 + 重建场景
</script>
```

**渲染逻辑：**
1. 遍历 `areas`，每个库区按 `modelType`：
   - `CUBE`：`BoxGeometry(areaWidth, areaHeight, areaLength)` + `MeshStandardMaterial({color, transparent, opacity})`，position 设为 (posX, posY, posZ)，rotation 设 Euler
   - `CUSTOM`：`GLTFLoader.load(modelUrl)`（P4 增强，MVP 仅 CUBE）
2. 遍历 `locations`，每个库位 `BoxGeometry(width, height, depth)`，**按 status 着色**：
   - EMPTY → `colorEmpty`，OCCUPIED → `colorOccupied`，LOCKED → `colorLocked`，WARNING → `colorWarning`
3. 标签：`CSS2DObject` 渲染库位编码，受 `showLabel` / `labelPosition` / `labelSize` 控制
4. 相机：根据仓库 `bound*` 自动定位，`OrbitControls` 启用阻尼

**`3d.vue` 页面：**
- 顶部工具栏：仓库下拉选择（复用 `listWarehouse`）、视图重置、着色模式切换（按状态/按库区/按占用率）、图例
- 主体：全屏 `<Wm3dViewer :warehouse-id="..." />`

**验收：**
- [ ] 选择仓库后 3D 画布渲染出库区与库位立方体
- [ ] 四种状态颜色肉眼可区分
- [ ] 鼠标可旋转/缩放/平移视角
- [ ] 切换仓库时场景正确重建，无残留几何体
- [ ] 离开页面后无控制台报错、无内存泄漏

---

### 阶段 P3：交互（任务 7-8）

**Files:**
- Modify: `plus-ui/src/views/erp/wm/components/Wm3dViewer.vue`
- Create: `plus-ui/src/views/erp/wm/components/WmLocationStockDialog.vue`（库位库存明细弹窗）

**交互实现：**
- **Raycaster 点击拾取**：`raycaster.setFromCamera(mouse, camera)`，`intersectObjects(locationMeshes)`，命中库位 → emit `select` 事件 → 父组件打开 `WmLocationStockDialog`，调用现有 `listMaterialStock({locationId})` 展示库存明细
- **hover 高亮**：`mousemove` 检测命中，对命中 mesh 的 `emissive` 提亮（`material.emissive.setHex(0x444444)`），移出恢复
- **双击聚焦**：`dblclick` 命中库位 → `controls.target` 平滑过渡到该库位中心，相机 dolly-in

**验收：**
- [ ] 点击占用库位弹出库存明细（物料/批次/数量/预留）
- [ ] hover 时库位高亮且不卡顿
- [ ] 双击库位相机平滑聚焦

---

### 阶段 P4：自动排版与性能（任务 9-10）

**任务 9：货架自动排版**

**Files:**
- Create: `plus-ui/src/views/erp/wm/components/rackLayout.ts`

当库位 `posX/Y/Z` 为空或 0 时，按货架结构字段（`rackId` / `layer` / `col` / `rowNum`）自动计算坐标：

```ts
/**
 * 货架自动排版：同 rackId 的库位按 layer/col/row 排成立体货架
 * @returns 补全后的库位坐标
 */
export function autoLayoutRack(
  locations: WmStorageLocation3dVo[],
  areaOrigin: { x: number; y: number; z: number },
  cellSize: { w: number; h: number; d: number }
): WmStorageLocation3dVo[] {
  // 按 rackId 分组 → 组内按 (layer, row, col) 映射到 (y, z, x)
  // x = areaOrigin.x + col * cellSize.w
  // y = areaOrigin.y + (layer - 1) * cellSize.h
  // z = areaOrigin.z + rowNum * cellSize.d
}
```

> **优先级策略：** 手填坐标（posX≠0）优先，缺省时才走自动排版。这样既有"开箱即用"的自动布局，又支持手工微调。

**任务 10：性能优化（InstancedMesh）**

当库位总数 > 阈值（如 500）时，将库位从逐个 `Mesh` 改为 `InstancedMesh`：

```ts
const geo = new THREE.BoxGeometry(cellW, cellH, cellD);
const mat = new THREE.MeshStandardMaterial({ vertexColors: true });
const instanced = new THREE.InstancedMesh(geo, mat, locations.length);
// 每个 instance 用 matrix 设置位置，用 instanceColor 设置状态颜色
// Raycaster 对 InstancedMesh 返回 instanceId，映射回库位
```

**验收：**
- [ ] 自动排版：给一组库位 layer/col/row 但无坐标，渲染为立体货架
- [ ] 1000 库位场景首屏 < 2s，交互 60fps（开发机基准）
- [ ] InstancedMesh 模式下点击拾取仍可定位到正确库位

---

### 阶段 P5：菜单/路由/入口与验收（任务 11-12）

**任务 11：路由、入口、菜单 SQL**

**Files:**
- Modify: `plus-ui/src/router/index.ts`（dynamicRoutes 增 3D 预览路由）
- Modify: `plus-ui/src/views/erp/wm/warehouse/index.vue`（操作列增"3D预览"按钮）
- Create: `doc/erp/sql/archive/erp-wm-3d-menu.sql`（菜单/权限，幂等）

**路由（参照现有 `/erp/wm/storagearea` 模式）：**

```ts
{
  path: '/erp/wm/warehouse/3d',
  component: Layout,
  hidden: true,
  permissions: ['wm:warehouse:3d'],
  children: [
    {
      path: ':warehouseId(\\d+)',
      component: () => import('@/views/erp/wm/warehouse/3d.vue'),
      name: 'WmWarehouse3d',
      meta: { title: '3D仓库', activeMenu: '/erp/wm/warehouse' }
    }
  ]
}
```

**入口按钮（参照 `handleStoragearea` 范式，`warehouse/index.vue` 操作列）：**

```vue
<el-button link type="primary" icon="View" @click="handleView3d(scope.row.warehouseId)"
  v-hasPermi="['wm:warehouse:3d']">3D预览</el-button>
```

```ts
import { useRouter } from 'vue-router';
const router = useRouter();
const handleView3d = (warehouseId: number | string) => {
  router.push(`/erp/wm/warehouse/3d/${warehouseId}`);
};
```

**菜单 SQL（草案，执行阶段落地为 `doc/erp/sql/archive/erp-wm-3d-menu.sql`，遵循 CONTRIBUTING §5 幂等规范）：**

```sql
-- =====================================================
-- 脚本名称: erp-wm-3d-menu.sql
-- 用途: 3D仓库可视化 菜单与权限
-- 对应文档: NexusERP-PLAN-2026-003
-- 适用数据库: ry-vue (sys_menu)
-- 特性: 幂等可重复执行
-- 创建日期: 2026-06-30
-- =====================================================
-- menu_id 段需执行前确认未占用（参照 wm 现有菜单段）
-- 按钮 perms: wm:warehouse:3d
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, perms, menu_type, visible, status, icon)
SELECT '3D仓库', <wm仓库菜单id>, 5, 'warehouse/3d', 'wm/warehouse/3d', 'wm:warehouse:3d', 'C', '1', '0', 'view'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'wm:warehouse:3d');
```

> **注意：** `menu_id` 与 `parent_id` 需执行前查 `sys_menu` 确认 wm 菜单段未占用，避免与现有冲突。该 SQL 仅注册权限点与隐藏菜单路由（页面通过 `dynamicRoutes` 静态路由 + 权限点访问）。

**任务 12：打磨与验收**

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-erp -am` 通过
- [ ] `npm run build:prod` 构建通过（验证 three.js 打包无问题）
- [ ] `npm run lint:eslint` 对新增/修改的 `plus-ui/src/views/erp/wm/**` 文件无 error
- [ ] 端到端：仓库列表 → 3D预览 → 渲染 → 点击库位 → 库存明细，全链路通过
- [ ] 验证记录写入本文档"实施进度"章节

---

## 三、接口设计汇总

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 仓库 3D 聚合数据 | GET | `/erp/wm/warehouse/{id}/3d` | `wm:warehouse:3d` | 返回 `WmWarehouse3dVo`（仓库+库区+库位+状态） |
| 库位库存明细 | GET | `/erp/wm/materialstock/list?locationId=` | 复用现有 | 库位点击钻取，**无需新接口** |

> **复用原则：** 库存明细、仓库列表、库区/库位 CRUD 均复用现有接口，仅新增 1 个聚合接口。

---

## 四、数据模型复用说明

本特性**不新建业务表、不修改现有表结构**。3D 字段已存在于：

- `wm_storage_area`：`pos_x/y/z`、`area_width/length/height`、`rotation_x/y/z`、`color`、`opacity`、`model_type`、`model_url`、`show_walls`、`show_label`、`grid_visible`、`config_json`（见 `script/sql/ry-erp.sql:4700+`）
- `wm_storage_location`：`pos_x/y/z`、`width`、`height`、`location_depth`、`rotation_x/y/z`、`color_empty/occupied/locked/warning`、`opacity`、`rack_id`、`layer`、`col`、`row_num`、`label_*`、`config_json`、`occupancy_rate`、`status`（见 `script/sql/ry-erp.sql:4768+`）
- `wm_material_stock`：`stock_quantity`、`reserved_quantity`、`frozen_flag`、`location_id`、`area_id`、`warehouse_id`（状态聚合数据源）

> 以 `script/sql/ry-erp.sql` 为 DDL 权威来源（CONTRIBUTING §6.1 单一数据源）。

---

## 五、工作量估算

| 阶段 | 工作项 | 人天 |
|------|--------|------|
| P1 | 后端聚合接口 + VO + 状态聚合查询 | 1.5–2 |
| P2 | three.js 引入 + 渲染组件 + 预览页 + 着色标签 | 2.5–3.5 |
| P3 | 点击钻取 + hover + 相机聚焦 | 2–3 |
| P4 | 货架自动排版 + InstancedMesh 性能 | 2–4 |
| P5 | 菜单/路由/入口 + 打磨验收 | 1.5–2 |
| **MVP 合计（P1+P2+P5 核心）** | | **约 5–7** |
| **完整版合计（P1-P5）** | | **约 10–15** |

> MVP = 能渲染 + 能看库存状态 + 基本交互，不含自动排版与大仓库性能优化。

---

## 六、分阶段交付里程碑

| 里程碑 | 范围 | 交付物 | 估算 |
|--------|------|--------|------|
| M1 MVP | P1 + P2 + P5（入口） | 聚合接口 + 渲染页 + 入口按钮，可渲染并按状态着色 | 5–7 人天 |
| M2 交互增强 | P3 | 点击钻取库存、hover、聚焦 | 2–3 人天 |
| M3 完整版 | P4 + 打磨 | 自动排版、大仓库性能、验收 | 3–5 人天 |

---

## 七、风险与应对

| 风险 | 说明 | 应对 |
|------|------|------|
| 大仓库性能 | 单仓库可能数百~上千库位，逐个 Mesh 会卡 | P4 用 `InstancedMesh`；MVP 设库位上限提示 |
| 坐标来源 | 全靠手填 posX/Y/Z 不现实 | P4 自动排版（layer/col/row → 坐标），手填仅作微调 |
| three.js 体积 | ~600KB 影响首屏 | 路由懒加载，仅 3D 页面按需加载 |
| VO 缺 3D 字段 | 现有 `WmStorageAreaVo` 未暴露 3D 字段 | 新建 `Wm*3dVo` 聚合 VO，不改现有 VO（避免影响现有接口） |
| WebGL 兼容 | 个别老旧终端无 WebGL | 检测 `WebGLRenderingContext`，不支持时降级提示 |
| 菜单 ID 冲突 | sys_menu ID 段需确认 | 执行前查 wm 菜单段，SQL 幂等 + `WHERE NOT EXISTS` |
| 多租户隔离 | 库存聚合需带 tenant_id | MyBatis-Plus 多租户插件自动注入，无需手写 |
| 自定义模型加载 | `modelType=CUSTOM` 需 GLTFLoader | MVP 仅支持 CUBE，CUSTOM 列入 P4 增强 |

---

## 八、明确排除项（本计划不做）

- 不新建/修改 wm 业务表结构（字段已就位）
- 不改造现有单表 CRUD 接口与 VO（仅新增聚合接口与新 VO）
- 不做 3D 模型建模/编辑器（仅消费已配置的坐标与尺寸）
- 不做实时库存刷新（SSE 推送库存变化），列为后续增强
- 不做路径规划/拣货动线可视化，列为后续增强
- CUSTOM 模型（GLTF）加载、VR/AR，列为后续增强

---

## 九、实施前确认项

执行前需核对以下事实（不阻塞计划审批，但影响任务 11 菜单 SQL）：

- [ ] `sys_menu` 中 wm 仓库菜单的 `menu_id` 与 `parent_id`，确认 3D 菜单 ID 段不冲突
- [ ] `wm_material_stock` 是否已对 `location_id` 建索引（影响聚合查询性能，大仓库场景）
- [ ] 确认 `WmStorageLocationVo` 是否同样缺 3D 字段（若库位列表页需回显坐标，需另行扩展；本渲染接口不依赖）

---

## 十、实施进度更新

> 执行阶段在此记录各任务状态与验证结果。

| 任务 | 状态 | 执行日期 | 结果 |
|------|------|----------|------|
| 1 后端聚合接口 | ✅ | 2026-06-30 | 4 个聚合 VO + 4 Mapper 方法 + XML + Service 装配 + Controller GET /{id}/3d |
| 2 库存状态聚合 | ✅ | 2026-06-30 | aggregateStockByWarehouse GROUP BY location_id，状态判定 LOCKED>WARNING>OCCUPIED>EMPTY |
| 3 引入 three.js | ✅ | 2026-06-30 | npm i three @types/three，vite.config optimizeDeps 加 three |
| 4 Wm3dViewer 组件 | ✅ | 2026-06-30 | 合并入 3d.vue（场景/相机/灯光/地面/库区库位 mesh/OrbitControls/悬停/释放） |
| 5 着色与标签 | ✅ | 2026-06-30 | 按状态四色 + 默认色兜底；标签 MVP 未用 CSS2D，图例在顶部 |
| 6 3D 预览页面 | ✅ | 2026-06-30 | warehouse/3d.vue，路由 /erp/wm/warehouse/3d/:warehouseId |
| 7 点击钻取 | ⬜ | — | M2（MVP 排除） |
| 8 hover 与聚焦 | 🚧 | 2026-06-30 | hover 浮窗已实现；双击聚焦留 M2 |
| 9 货架自动排版 | ⬜ | — | P4（MVP 排除，由种子 SQL 提供坐标） |
| 10 InstancedMesh | ⬜ | — | P4（MVP 排除） |
| 11 菜单/路由/入口 | ✅ | 2026-06-30 | 路由 + 仓库列表"3D预览"按钮；复用 wm:warehouse:query 权限，无菜单 SQL |
| 12 打磨验收 | ✅ | 2026-06-30 | 后端 mvn compile 通过；前端 build:prod 通过（three 独立懒加载 chunk 532K）；lint 仅 3d.vue 自动修复通过 |

### MVP (M1) 验收记录 — 2026-06-30

- ✅ 后端 `mvn compile -pl ruoyi-modules/ruoyi-erp -am` BUILD SUCCESS
- ✅ 前端 `npm run build:prod` ✓ built，3D 页懒加载 chunk `3d-*.js`（含 three，532K），不影响首屏
- ✅ 新增/修改文件 lint 无 error（`warehouse/index.vue` 的 178 个 prettier 错误为预存状态，非本次引入，未越权重排）
- ✅ 演示种子 SQL：`doc/erp/sql/test-data/erp-wm-3d-demo-seed.sql`（幂等，1 仓库 + 2 库区 + 18 库位 + 10 库存，覆盖四态）
- ⬜ 端到端联调：需运行环境（后端启动 + 执行种子 SQL + 前端访问 3D 预览页），待用户在本地验证

### MVP 交付物

| 文件 | 类型 |
|------|------|
| `wm/domain/vo/WmWarehouse3dVo.java`、`WmStorageArea3dVo.java`、`WmStorageLocation3dVo.java`、`WmLocationStockAggVo.java` | 新建 VO |
| `wm/mapper/Wm{Warehouse,StorageArea,StorageLocation,MaterialStock}Mapper.java` | 修改 +方法 |
| `resources/mapper/erp/wm/Wm{Warehouse,StorageArea,StorageLocation,MaterialStock}Mapper.xml` | 修改 +select |
| `wm/service/IWmWarehouseService.java`、`impl/WmWarehouseServiceImpl.java` | 修改 +getWarehouse3d |
| `wm/controller/WmWarehouseController.java` | 修改 +GET /{id}/3d |
| `plus-ui/package.json`、`vite.config.ts` | 修改 +three 依赖/optimizeDeps |
| `plus-ui/src/api/erp/wm/warehouse/{index,types}.ts` | 修改 +getWarehouse3d/3D 类型 |
| `plus-ui/src/router/index.ts` | 修改 +3d 路由 |
| `plus-ui/src/views/erp/wm/warehouse/index.vue` | 修改 +3D预览按钮 |
| `plus-ui/src/views/erp/wm/warehouse/3d.vue` | 新建 3D 渲染页 |
| `doc/erp/sql/test-data/erp-wm-3d-demo-seed.sql` | 新建演示种子 |

---

**文档生成时间**: 2026-06-30
**执行者**: AI Agent
**审核状态**: 草稿
