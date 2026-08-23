# NexusERP-PLAN-2026-006 — 3D 仓库 M4 视觉升级

- **日期**: 2026-08-22 ~ 2026-08-23
- **范围**: `plus-ui/src/views/erp/wm/warehouse/3d.vue` + `plus-ui/src/views/erp/wm/components/*` + 示例数据 SQL（零后端 Java 改动）
- **前置**: M1 基础渲染 / M2 交互增强 / M3 货架排版 / 厂房外建模（PLAN-2026-003/004/005）均已完成

## 目标

1. 写实视觉建模：货架钢结构、货物按库存量填充、库区/库位标签、地面标线、阴影光影
2. 库位渲染统一 InstancedMesh（完成 P3-1 遗留项）
3. 交互增强：状态图例点击过滤、库区显隐、视角预设
4. 重写示例仓库数据（104 库位，四状态全覆盖）
5. 厂房外壳升级为工业写实风（用户追加需求，原计划不改 warehouseBuilding.ts，实际已重写）

## 实施内容

### 1. 渲染架构重构 — `components/instancedLocations.ts`（新建）

- 库位统一 InstancedMesh 三层实例网格（共享单位立方体矩阵缩放）：**cells 幽灵格**（拾取目标，极淡半透明）、**goods 货物盒**（按库存量/仓库最大库存归一化填充高度 0.15~0.95，底对齐，instanceColor 承载三种着色模式）、**pallets 托盘薄板**（仅占用库位）
- 库位轮廓合并为单个 LineSegments（vertexColors 按库位 borderColor；showBorder 控制）
- 悬停高亮改为共享高亮盒（随悬停移动），替代逐 Mesh emissive
- 隐藏实现：实例矩阵 scale 置 0（零缩放实例不产生 raycast 命中）；API：`recolor(mode) / setStatusFilter / setAreaVisible / highlight / dispose`
- 后端已返回但前端未渲染的字段本次全部启用：库位 showBorder/borderColor/showLabel/labelPosition/labelSize、库区 showWalls/showLabel/gridVisible

### 2. 货架钢结构 — `components/rackFrame.ts`（新建）

- RACK 布局库区按 rackId 分组，由排版后库位实际坐标推导每排钢架：立柱（相邻列中线定位）+ 承重横梁（顶面贴合层底，货物落其上；地面层不设）+ 底脚板
- 立柱蓝灰 / 横梁橙、金属质感；每库区 mergeGeometries 合并为 2 个 Mesh；网格参与遮挡拾取；导出 `groupLocationsByRack / rackBounds` 供地面标线复用

### 3. 标签 — `components/sceneLabels.ts`（新建）

- Canvas 纹理 Sprite：库区名（库区顶上方悬浮，area.showLabel 控制）+ 库位编码（loc.showLabel/labelPosition(TOP/FRONT)/labelSize 控制）
- 库位总数 > 300（LOCATION_LABEL_MAX_COUNT）时仅渲染库区标签防噪声；depthTest 开启（被遮挡不透出）

### 4. 地面标线 — `components/floorMarkings.ts`（新建）

- 库区地面色块（area.color 半透明，gridVisible 控制）+ 库区边界 LineLoop
- RACK 区相邻货架排间隙 ≥0.8m 自动绘制黄色通道虚线

### 5. 厂房外壳升级 — `components/warehouseBuilding.ts`（重写，用户追加需求）

- 两段式墙体：混凝土勒脚（1.1m）+ 彩钢墙 + 全周竖楞（间距 0.8m，合并单 Mesh）
- 前墙双卷帘门（宽度不足自动降为单门）：金属门框 + 横楞门板 + 整体雨棚
- 卸货平台：地台 + 黄色警示边条 + 两端坡道
- 高位条窗：整条玻璃 + 金属框梃（2m 分隔）
- 屋顶：人字屋面（坡角 28°→18°）+ 屋脊盖板 + 檐口封板 + 山墙斜封边 + 4 组屋脊通风器（底座/筒/锥帽），**屋顶改为 Object3D 整组**联动俯视自动隐藏
- 山墙标牌：仓库编码·名称 Canvas 纹理（buildWarehouseBuilding 新增 opts.label）
- 屋顶不投影（避免压暗室内），全部构件仅接收投影

### 6. 3d.vue 重构

- 工具栏：图例可点击过滤状态（空集=全部）、库区显隐 Popover（联动外壳/标线/钢架/标签/实例）、视角预设下拉（全景/俯视/正视/侧视/等轴测，animateCamera 平滑过渡）、标签/阴影开关
- 光影：PCFSoftShadowMap + ACES 色调映射 + HemisphereLight，主平行光阴影相机按场景包围盒收紧；阴影开关切 dirLight.castShadow
- 库区半透明外壳改由 area.showWalls 控制（货架区关掉，靠钢架表达体积）
- 拾取改 instanceId 索引（cells + 厂房 + 钢架遮挡拾取，"看得到才点得到"语义不变）；悬停/单击钻取/双击聚焦交互保持

### 7. 示例仓库数据（SQL 重写）

- `doc/erp/sql/test-data/erp-wm-3d-demo-seed.sql`：`SET NAMES utf8mb4` + 第 0 节清理段可重复执行；仓库 ID 9900000001 / 编码 WH-3D-DEMO 沿用
- 布局：A3D01 原料货架区 RACK（3 排×5 列×4 层=60）、B3D02 成品货架区 RACK（2 排×5 列×3 层=30）、C3D03 拣选网格区 GRID（5×2=10，PICKING 位）、D3D04 散货堆放区 BULK（2×2=4 地堆），共 **104 库位**
- 库存 55 条：OCCUPIED 35 / WARNING 8（预留≥80%）/ LOCKED 6（1 库位级冻结 + 5 库存级冻结）/ EMPTY 55，多批次库位 6 个，数量梯度 30~800
- `script/sql/ry-erp.sql` 初始数据块同步替换（新装环境一致）
- 生成器 `erp-wm-3d-demo-datagen.mjs`：幂等生成上述两处数据（含列数/值数自检断言），重生成执行 `node erp-wm-3d-demo-datagen.mjs`

### 8. 类型补齐

- `api/erp/wm/warehouse/types.ts` 补齐后端 VO 已返回字段；`api/erp/wm/warehouse/index.ts` 修复 Warehouse3dVO 未导入的既有缺陷

## 验证结果

- 数据：本机 MySQL 执行种子脚本，104 库位/55 库存/4 库区入库；按后端 applyStatus 同口径聚合验证四状态分布 35/8/6/55、多批次 6 个 ✓
- 前端：ESLint / vue-tsc（改动范围零错误）；vite 编译全部新模块通过；浏览器实测登录后打开 `/erp/wm/warehouse/3d/9900000001`，工具栏图例统计与种子数据一致（空置 55/占用 35/预警 8/锁定 6/共 104）✓；货架/网格区内部效果经用户确认 ✓
- 厂房升级后经 vite 编译验证 + lint/类型检查通过，视觉由用户在 HMR 下确认

## 已知取舍

- `borderWidth` 无法生效（WebGL LineBasicMaterial 线宽恒为 1），仅渲染边框颜色
- InstancedMesh 单一材质：`loc.opacity` 逐库位透明度不再逐实例生效（统一层透明度）
- 货物填充高度是展示启发式（按仓库最大库存量归一化），非真实容量（库位无容量字段）
- 自动排版（autoLayoutRack）多组货架沿 Z 从原点排布不整体居中，示例数据采用手填坐标规避
