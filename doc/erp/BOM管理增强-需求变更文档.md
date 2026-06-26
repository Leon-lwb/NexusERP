# BOM 管理 enhancement 需求变更文档

> **文档编号**: NexusERP-CHG-2026-001
> **变更主题**: BOM 管理对齐主流 ERP/MES 能力（工序 / 工艺路线 / 替代料 / 辅助功能）
> **变更日期**: 2026-06-17
> **影响模块**: ruoyi-erp (bd 包)、plus-ui (erp/bd)、sys_menu / sys_dict
> **变更负责人**: 王欢
> **关联文档**: `C:\Users\MyPC\.claude\plans\erp-bom-routing-enhancement.md`（详细设计稿）

---

## 一、变更背景与目标

### 1.1 背景
原有 BOM 模块（`bd_bom` / `bd_bom_detail`）仅支持基础的父子物料结构与版本管理，与主流 ERP/MES 系统的 BOM 管理能力存在明显差距：

- 无 BOM 类型区分（EBOM / MBOM / SBOM / CBOM 混用）
- 无工序 / 工艺路线概念，无法支撑 MES 的工序级排产与报工
- 无替代料机制，缺料时无法自动切换
- 缺少 Where-Used 反查、BOM 复制、BOM 版本比较等工程变更辅助功能

### 1.2 目标
在 ERP 模块统一维护主数据（物料 / BOM / 工艺路线 / 工序 / 工作中心），MES 模块通过跨库只读引用，实现 **一套 BOM、ERP 维护、MES 引用** 的架构。

### 1.3 架构原则
| 决策点 | 结论 |
|--------|------|
| BOM 归属 | 统一放 ERP 模块（bd 包）维护 |
| 工艺路线 | 建在 ERP 的 bd 包下 |
| MES 集成 | 跨库只读引用（`MesErpBridgeService` + `@DS("erp")`），不在 MES 建冗余表 |
| 功能范围 | 先落地：BOM 类型 + 工艺路线 + 替代料 + 辅助功能；预留：ECN / 成本滚算 / 虚拟件（文档预留，后续开发） |

---

## 二、变更范围总览

本次变更按 5 个 Phase 推进，并修复审查发现的数据完整性与前端入口缺口：

| Phase | 内容 | 状态 |
|-------|------|------|
| Phase 1 | 工序管理（BdProcess） | ✅ 已完成 |
| Phase 2 | 工艺路线管理（BdRouting + 步骤） | ✅ 已完成 |
| Phase 3 | BOM 增强：类型 + 工艺路线关联 | ✅ 已完成 |
| Phase 4 | 替代料机制（BdBomAlternative） | ✅ 已完成 |
| Phase 5 | BOM 辅助功能：Where-Used / 复制 / 比较 | ✅ 已完成 |
| 缺口修复 | 级联删除 / 替代料复制 / 工序比较 / 版本唯一性 / 菜单 / 前端入口 | ✅ 已完成 |

---

## 三、数据库变更

### 3.1 新增表（4 张）
脚本：`doc/erp/sql/erp-bom-routing-enhancement.sql`

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `bd_process` | 工序主数据 | process_id, process_code, process_name, process_category(加工/装配/检验/包装), workstation_id, standard_hours, setup_hours, is_key_process, is_outsource, quality_required, status |
| `bd_routing` | 工艺路线主表 | routing_id, routing_code, routing_name, material_id, routing_version, is_current, total_steps, total_hours, effective_date, expiry_date, status |
| `bd_routing_step` | 工艺路线工序步骤 | step_id, routing_id, sequence_num, process_id, step_name, workstation_id, standard_hours, setup_hours, is_key_process, is_outsource, quality_required, wait_time, transfer_time |
| `bd_bom_alternative` | BOM 替代料 | alternative_id, detail_id, bom_id, alt_material_id, alt_quantity, priority, substitution_type(FULL/PARTIAL/INTERCHANGEABLE), effective_date, expiry_date, status |

> 表结构预留（注释字段，未启用）：ECN 变更号、虚拟件标识、成本滚算相关列，供后续 Phase 开发。

### 3.2 既有表字段变更

| 表 | 新增字段 | 说明 |
|----|----------|------|
| `bd_bom` | `bom_type` VARCHAR DEFAULT 'MBOM' | BOM 类型（EBOM/MBOM/SBOM/CBOM） |
| `bd_bom` | `routing_id` BIGINT | 关联工艺路线 |
| `bd_bom_detail` | `process_id` BIGINT | 子项对应工序 |
| `bd_bom_detail` | `routing_step_id` BIGINT | 子项对应工艺步骤 |

### 3.3 字典变更
脚本：`doc/erp/sql/dict-bom-routing-process.sql`（3 个字典类型）

| 字典类型 | 字典编码 | 字典值 |
|----------|----------|--------|
| 工序类别 | `bd_process_category` | PROCESSING-加工 / ASSEMBLY-装配 / INSPECTION-检验 / PACKAGING-包装 |
| BOM 类型 | `bd_bom_type` | EBOM-工程BOM / MBOM-制造BOM / SBOM-服务BOM / CBOM-配置BOM |
| 替代料类型 | `bd_substitution_type` | FULL-完全替代 / PARTIAL-部分替代 / INTERCHANGEABLE-互换 |

> ⚠️ **主键冲突注意**：若 `ry-vue.sql` 已执行（其中已包含上述字典数据），**不要再执行** `dict-bom-routing-process.sql`，否则会因 dict_data 主键冲突报错。两者二选一。

### 3.4 菜单权限变更
脚本：`doc/erp/sql/erp-process-routing-menu.sql`（ID 段 2900–2999）

| 菜单 ID | 菜单名 | 父菜单 | 权限标识 |
|---------|--------|--------|----------|
| 2900 | 工序管理 | 2001 基础数据 | `bd:process:list` |
| 2901–2905 | 工序 查询/新增/修改/删除/导出 | 2900 | `bd:process:query/add/edit/remove/export` |
| 2910 | 工艺路线 | 2001 基础数据 | `bd:routing:list` |
| 2911–2915 | 工艺路线 查询/新增/修改/删除/导出 | 2910 | `bd:routing:query/add/edit/remove/export` |

> **执行后**：需到「角色管理」为相关角色分配上述权限，用户才能在左侧菜单看到工序/工艺路线入口。

---

## 四、后端变更（ruoyi-erp / bd 包）

### 4.1 新增实体与代码结构

| 业务对象 | 实体 | BO | VO | Mapper | Service | Controller |
|----------|------|----|----|--------|---------|------------|
| 工序 | BdProcess | BdProcessBo | BdProcessVo | BdProcessMapper | IBdProcessService / Impl | BdProcessController |
| 工艺路线 | BdRouting | BdRoutingBo | BdRoutingVo | BdRoutingMapper | IBdRoutingService / Impl | BdRoutingController |
| 工艺步骤 | BdRoutingStep | — | BdRoutingStepVo | BdRoutingStepMapper | (并入 Routing) | (并入 Routing) |
| 替代料 | BdBomAlternative | BdBomAlternativeBo | BdBomAlternativeVo | BdBomAlternativeMapper | IBdBomAlternativeService / Impl | BdBomAlternativeController |
| BOM 比较 | — | — | BomComparisonVo | — | (并入 BdBom) | (并入 BdBomController) |

### 4.2 关键业务规则

#### 4.2.1 工序管理（BdProcess）
- 工序类别：加工 / 装配 / 检验 / 包装
- 关键工序、外协工序、需质检为开关标志
- 标准工时 / 准备工时 / 人数 / 机台数

#### 4.2.2 工艺路线管理（BdRouting）
- **版本激活互斥**：`activateVersion` 将同 `material_id` 的所有版本置为非当前，仅激活指定版本
- **当前工艺路线查询**：`getCurrentRouting(materialId)` 优先返回物料专用路线；若物料无专用路线，回退到通用路线（`material_id IS NULL`）
- **工序步骤校验**：`verifyRouting` 校验步骤序号连续性
- **级联删除**：删除工艺路线时同步删除其下所有工序步骤
- 表单保存时自动计算 `total_steps` / `total_hours`

#### 4.2.3 BOM 增强
- `bd_bom` 增加 `bom_type`、`routing_id`，列表查询支持按类型 / 路线过滤
- 列表查询 `buildQueryWrapper` 已加入 bomType / routingId 条件

#### 4.2.4 替代料（BdBomAlternative）
- **批量保存**：`saveBatch(detailId, bomId, list)` 采用「先删后插」，事务保证
- **可用替代料**：`getAvailableAlternatives(detailId)` 按生效 / 失效日期过滤（生效日期 ≤ 今天 或 为空；失效日期 ≥ 今天 或 为空）
- 替代类型：完全替代 / 部分替代 / 互换

#### 4.2.5 BOM 辅助功能（Phase 5）

**Where-Used 反查** `whereUsed(materialId)`
- 查询 `bd_bom_detail` 中 `child_material_id` 命中的所有 BOM
- 返回 BOM VO 列表，并填充父项物料编码 / 名称

**BOM 复制** `copyBom(sourceBomId, newVersion)` — `@Transactional`
- 校验新版本号在同父项物料下唯一
- 新 BOM 默认 `is_current=0`、`status=0`（草稿），避免误激活
- 复制主表 + 全部明细（含 `process_id` / `routing_step_id`）
- **复制替代料**：建立 旧明细ID → 新明细ID 映射，重写 `detail_id` / `bom_id` 后插入

**BOM 比较** `compareBom(bomId1, bomId2)`
- 以 `child_material_id` 为键构建映射（非 detail_id，避免重排序误判变更）
- 分四组：仅 BOM1 有（移除）/ 仅 BOM2 有（新增）/ 已修改 / 未变更
- 比较字段：用量、损耗率、工序步骤、**工序 ID**、**工艺步骤 ID**
- BigDecimal 使用 `compareTo` 比较，含 null 保护
- 生成中文变更详情（changeDetail）

### 4.3 数据完整性修复（审查发现）

| 问题 | 修复 |
|------|------|
| `deleteByIds` 不级联，残留孤立明细/替代料 | 删除前校验**非终态工单引用**（COMPLETED/CLOSED/CANCELLED 不阻断），再级联删 替代料 → 明细 → 主表 |
| `copyBom` 漏复制替代料 | 新增替代料复制逻辑（见 4.2.5） |
| `compareBom` 未比较工序/工艺步骤 | 已补入判定与变更详情 |
| `insertByBo` 不校验版本号唯一 | 抽出 `checkVersionUnique(parentMaterialId, bomVersion, excludeBomId)`，新增/修改/复制三处统一调用 |

### 4.4 BOM 控制器新增端点

| 方法 | 端点 | 权限 | 说明 |
|------|------|------|------|
| GET | `/erp/bd/bom/whereUsed/{materialId}` | `bd:bom:query` | Where-Used 反查 |
| POST | `/erp/bd/bom/copy/{sourceBomId}?newVersion=xx` | `bd:bom:add` | BOM 复制 |
| GET | `/erp/bd/bom/compare?bomId1=xx&bomId2=xx` | `bd:bom:query` | BOM 比较 |

替代料控制器端点（`/erp/bd/bom/alternative`）：
- `GET /list/{detailId}` 按明细查
- `GET /bom/{bomId}` 按 BOM 查
- `POST /batch?detailId=&bomId=` 批量保存
- `DELETE /{ids}` 删除
- `GET /available/{detailId}` 可用替代料

工艺路线控制器端点（`/erp/bd/routing`）：
- 基础 CRUD + `PUT /activateVersion/{routingId}` + `GET /current/{materialId}` + `GET /steps/{routingId}` + `GET /verify/{routingId}`

---

## 五、前端变更（plus-ui / erp/bd）

### 5.1 新增页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 工序管理 | `views/erp/bd/process/index.vue` | 工序 CRUD，含工作中心下拉、关键/外协/质检开关 |
| 工艺路线列表 | `views/erp/bd/routing/index.vue` | 路线列表、激活版本 |
| 工艺路线表单 | `views/erp/bd/routing/form.vue` | 工序步骤可编辑表格，工序下拉自动带出工时/人数，保存时计算总步数/总工时 |

路由注册：`router/index.ts` 已加 `/erp/bd/routing/form`（hidden，权限 `bd:routing:query`）。

### 5.2 新增 / 修改 API

| API 文件 | 内容 |
|----------|------|
| `api/erp/bd/process/{types,index}.ts` | 工序 CRUD |
| `api/erp/bd/routing/{types,index}.ts` | 工艺路线 CRUD + 激活 / 当前 / 步骤 / 校验 |
| `api/erp/bd/bom/alternative/{types,index}.ts` | 替代料（按明细/按BOM查、批量保存、删除、可用替代料） |
| `api/erp/bd/bom/{types,index}.ts` | 增 bomType/routingId 字段；增 `whereUsedBom` / `copyBom` / `compareBom` |

### 5.3 BOM 列表页（index.vue）变更
- 新增 BOM 类型筛选与列表列（`bd_bom_type` 字典）
- 内嵌新增/编辑对话框补充 **BOM 类型**、**工艺路线** 选择
- 工具栏新增按钮：
  - **复制 BOM**（选中 1 条，弹出版本号输入框）
  - **BOM 比较**（选中 2 条，弹出比较对话框：摘要卡片 + 差异统计标签 + 移除/新增/修改/相同四个 Tab）
  - **Where-Used 反查**（选目标物料后展示引用该物料的所有 BOM）
- 物料选择对话框支持双模式（父项物料 / 反查目标物料）

### 5.4 BOM 编辑表单（form.vue）变更
- 新增 **BOM 类型** 选择
- 新增 **工艺路线** 选择器（联动显示路线版本号）
- 明细行操作列新增 **替代料** 按钮 → 打开替代料管理对话框（增删行、物料选择、批量保存）
- `useDict` 补充 `bd_bom_type`、`bd_substitution_type`

---

## 六、MES 集成方式

- MES 不建冗余 BOM / 工艺路线表，通过 `MesErpBridgeServiceImpl`（`@DS("erp")`）跨库只读引用 ERP 主数据
- 工单展开 BOM、工序报工等场景直接读取 ERP 的 `bd_bom` / `bd_routing` / `bd_process`
- 写入操作仅在 ERP 侧进行，保证主数据单一来源

---

## 七、遗留与后续开发（已预留，未实现）

| 功能 | 预留方式 | 计划 |
|------|----------|------|
| ECN 工程变更管理 | DDL 注释字段 + 设计文档 | 后续 Phase |
| 成本滚算 | DDL 注释字段 + 设计文档 | 后续 Phase |
| 虚拟件 / 模块件 | DDL 注释字段 + 设计文档 | 后续 Phase |

---

## 八、上线步骤（执行顺序）

1. **执行数据库脚本**（按顺序）：
   - `doc/erp/sql/erp-bom-routing-enhancement.sql`（建表 + 加字段）
   - 字典脚本二选一：若库未执行过 `ry-vue.sql` 的新字典则执行 `dict-bom-routing-process.sql`；**否则跳过**
   - `doc/erp/sql/erp-process-routing-menu.sql`（菜单权限）
2. **角色授权**：到「系统管理 → 角色管理」分配 `bd:process:*`、`bd:routing:*` 权限
3. **后端部署**：`mvn clean package` 后重启服务
4. **前端部署**：`npm run build:prod` 后发布
5. **验证**：
   - 维护工序 → 维护工艺路线（编排步骤）→ 新建 BOM 选择类型与工艺路线
   - 给 BOM 明细配置替代料
   - 测试复制 BOM（验证替代料一并复制）
   - 测试 BOM 比较（验证工序/工艺步骤差异）
   - 测试 Where-Used 反查
   - 测试删除 BOM（验证级联删除与工单引用阻断）

---

## 九、影响评估与回滚

### 9.1 影响
- 新增表与字段为增量变更，不影响既有 BOM 业务逻辑
- `bd_bom.bom_type` 默认 'MBOM'，历史数据平滑兼容
- BOM 删除行为变严格（有非终态工单引用时阻断），上线前需确认无在制工单引用待删 BOM

### 9.2 回滚方案
- 数据库：DROP 新增 4 表；ALTER DROP 新增字段；DELETE 菜单 ID 2900–2915
- 后端：回退 ruoyi-erp 模块代码
- 前端：回退 plus-ui 相关页面与 API

---

## 十、文件清单

### 后端
```
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/
├── domain/
│   ├── BdProcess.java
│   ├── BdRouting.java
│   ├── BdRoutingStep.java
│   ├── BdBomAlternative.java
│   ├── BdBom.java            （修改：+bomType, +routingId）
│   ├── BdBomDetail.java      （修改：+processId, +routingStepId）
│   ├── bo/
│   │   ├── BdProcessBo.java
│   │   ├── BdRoutingBo.java
│   │   ├── BdBomAlternativeBo.java
│   │   ├── BdBomBo.java      （修改）
│   │   └── BdBomDetailBo.java（修改）
│   └── vo/
│       ├── BdProcessVo.java
│       ├── BdRoutingVo.java
│       ├── BdRoutingStepVo.java
│       ├── BdBomAlternativeVo.java
│       ├── BomComparisonVo.java
│       ├── BomTreeNodeVo.java
│       ├── IndentedBomLineVo.java
│       ├── BdBomVo.java       （修改：+bomType, +routingId/Code/Name）
│       └── BdBomDetailVo.java （修改：+processId/Code/Name, +routingStepId）
├── mapper/
│   ├── BdProcessMapper.java
│   ├── BdRoutingMapper.java
│   ├── BdRoutingStepMapper.java
│   ├── BdBomAlternativeMapper.java
│   ├── BdBomMapper.java       （未改）
│   └── BdBomDetailMapper.java （未改）
├── service/
│   ├── IBdProcessService.java
│   ├── IBdRoutingService.java
│   ├── IBdBomAlternativeService.java
│   ├── IBdBomService.java     （修改：+whereUsed/copyBom/compareBom）
│   └── impl/
│       ├── BdProcessServiceImpl.java
│       ├── BdRoutingServiceImpl.java
│       ├── BdBomAlternativeServiceImpl.java
│       └── BdBomServiceImpl.java  （修改：级联删除/替代料复制/工序比较/版本唯一性/三个辅助方法）
└── controller/
    ├── BdProcessController.java
    ├── BdRoutingController.java
    ├── BdBomAlternativeController.java
    └── BdBomController.java   （修改：+3 个辅助端点）
```

### 前端
```
plus-ui/src/
├── api/erp/bd/
│   ├── process/{types,index}.ts          （新增）
│   ├── routing/{types,index}.ts          （新增）
│   ├── bom/alternative/{types,index}.ts  （新增）
│   └── bom/{types,index}.ts              （修改）
├── views/erp/bd/
│   ├── process/index.vue                 （新增）
│   ├── routing/index.vue                 （新增）
│   ├── routing/form.vue                  （新增）
│   └── bom/
│       ├── index.vue                     （修改：Where-Used/复制/比较/类型/路线）
│       └── form.vue                      （修改：bomType/路线/替代料入口）
└── router/index.ts                       （修改：+routing/form 路由）
```

### 数据库脚本
```
script/sql/
├── erp-bom-routing-enhancement.sql       （建表 + 加字段）
├── dict-bom-routing-process.sql          （字典）
└── erp-bd-process-routing-menu.sql       （菜单权限）
```

---

**文档结束** — 后续如有 ECN / 成本滚算 / 虚拟件开发，请在本文档基础上追加 Phase 6+ 章节。
