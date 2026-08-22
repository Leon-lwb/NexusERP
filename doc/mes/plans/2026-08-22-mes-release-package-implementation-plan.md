# 优化方案：客户定制化发布包（方案D = 发布包 + 工单固件确认）

## 一、方案定位与对两份方案的取舍

以发布包方案为骨架，融合方案C的"工单固件确认 + 下达门禁"，并修正两份文档中与现有代码不符/存在缺陷的部分：

| 决策 | 结论 | 理由 |
|------|------|------|
| 放弃 `so_sales_order_line_firmware` 子表 | ✂️ 不做 | 发布包是技术配置唯一来源，避免双来源冲突；一次性指定走 MES 工单固件编辑（用户已确认） |
| 放弃 `mes_firmware_customer_binding` 预设表 | ✂️ 不做 | 唯一键 `(client_id, material_id)` 无法表达一机多固件（BIOS+SSD+网卡），发布包固件映射天然支持 |
| BOM选配定位方式 | 🔧 改进 | 按 `child_material_id`（原物料）定位而非 `bom_detail_id`——抗 BOM/MBOM 版本变更，语义更自然（"把 SSD-256 换成 SSD-512"） |
| 配置项消费落地 | 🔧 补全 | 原文档未定义非 FLASH 配置去向：全量快照到 `pro_work_order.release_config` JSON 列 + FLASH 组注入 `stepFlashConfig` |
| FLASH 门禁挂载点 | 🔧 修正 | 同时挂 `release()` 直达路径与 `submit()` 审批提交前——探索发现 `afterFlowHandle` 审批通过直接置 RELEASED，**不经过** `doReleaseAndSnapshot`，文档方案会漏掉审批路径 |
| ERP 侧 client 落点 | 🔧 改进 | `generateWorkOrders` 生成 ERP 工单时一次性落到 `prd_work_order`（单一落点），而非 MES 消费时 4 跳回查（erpWoId→plan→line→header） |
| 前端路由 | 🔧 修正 | 本仓库菜单为 sys_menu 数据库驱动，两份文档写的"改 router/index.ts"是错的，只需菜单 SQL |
| 执行层 | ✅ 零改动 | `checkFlashStepGate`/Provider 校验链/刷写记录完全复用，`contentRefId` 级门禁天然支持覆写后的固件 |

## 二、数据模型变更

### 新增表（ry-mes 库，4 张，沿用文档 3.1-3.4 设计）

1. **`mes_release_package`** 发布包主表：`package_id/package_code`、`client_id/client_name`、`material_id/code/name`、`customer_product_code/name`、`version/release_notes`、`status`(DRAFT/IN_REVIEW/RELEASED/ARCHIVED)、`effective_date/expiry_date`、`is_default`、`select_flow_id`、`published_by/publish_time` + TenantEntity 基础字段；唯一键 `(client_id, material_id, version)`
2. **`mes_release_pkg_firmware`** 固件映射表：`routing_step_id/process_id/process_code/name`（工序定位）、`pcba_material_id/code/name`（追溯用）、`firmware_id/code/name/version`、`sort_order`
3. **`mes_release_pkg_bom_item`** BOM选配表：`action_type`(REPLACE/ADD/REMOVE)、`child_material_id`（原物料，REPLACE/REMOVE 定位键）、`bom_detail_id`（可选精确）、`target_material_id/code/name`、`quantity_per_parent`
4. **`mes_release_pkg_config`** 配置项表：`config_group`(FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS)、`config_key/config_value/config_type`、`process_id/process_code`（空=全局）

### 修改表

| 表 | 变更 |
|----|------|
| `so_sales_order_line` (ry-erp) | + `customer_product_code` VARCHAR(128)、`customer_product_name` VARCHAR(256) |
| `prd_work_order` (ry-erp) | + `client_id` BIGINT、`client_name` VARCHAR(128)、`customer_product_code` VARCHAR(128) |
| `pro_work_order` (ry-mes) | + `release_package_id` BIGINT、`release_package_code` VARCHAR(64)、`release_package_ver` VARCHAR(32)、`release_config` TEXT（配置项快照 JSON） |
| `pro_work_order_step` (ry-mes) | + `firmware_source` VARCHAR(32)（PACKAGE/ROUTING/MATERIAL_DEFAULT/MANUAL，前端来源标记用） |

### MQ 消息（ruoyi-common-mq）

`WorkOrderReleaseMessage` + `clientId`、`clientName`、`customerProductCode`

## 三、后端改动

### 新增 `com.nexus.mes.release` 子包（@DS("mes")，主类扫描自动注册）

- **domain**：`ReleasePackage`、`ReleasePkgFirmware`、`ReleasePkgBomItem`、`ReleasePkgConfig` + bo/vo（Bo 含三个子表 List，Vo 同）
- **mapper**：4 个 BaseMapperPlus
- **service**：`IReleasePackageService` + `ReleasePackageServiceImpl`（实现 `WfCallBackServiceI`，SERVICE_NAME=`mesReleasePackageService`，完全镜像 `FirmwareServiceImpl` 模式）：
  - CRUD：主表 + 子表级联（先删子表再插，参考 `SoSalesOrderHeaderServiceImpl.insertOrUpdateLines`）
  - 状态机：`submitForApproval`（Flowable，先启动流程后持久化 selectFlowId 防死锁）/`afterFlowHandle`（@DS("mes")+REQUIRES_NEW，pass→RELEASED+发布人/时间）/`approve`/`reject` 管理员兜底/`archive`
  - `setDefault`：同 client+material 内唯一，仅 RELEASED 可设
  - 保存校验：固件映射的 firmware 必须 RELEASED 且 material 匹配发布包物料；BOM 选配目标物料必须存在；同包内工序定位键不重复
  - `matchPackage(clientId, materialId, customerProductCode)`：①精确 code 匹配最新 RELEASED（有效期内）→ ②client+material `is_default=1` RELEASED（有效期内）→ null
- **controller**：`ReleasePackageController` `/mes/release/package`（CRUD/submit/approve/reject/archive/default/match/list-by-client-material）

### 修改 `ProWorkOrderServiceImpl`（核心，5 处）

1. **`createFromErpMessage()`**：消息 clientId 非空时调 `matchPackage`，事务外加载包+子表传入 `doCreateWorkOrderAndSteps`
2. **`doCreateWorkOrderAndSteps()`**：+包参数；工单落 `releasePackageId/Code/Ver` + `releaseConfig`（全部配置项 JSON 快照）；透传给步骤生成与 BOM 快照
3. **`generateStepsFromRouting()`**：固件优先级 = **包映射（routingStepId 精确 → processId 宽泛，fail-loudly 校验 RELEASED）→ 工艺路线 firmware_id → 物料默认**；包映射命中置 `firmware_source=PACKAGE`；FLASH 组配置项按工序注入 `stepFlashConfig`（`[{flashTypeCode, contentRefId}]`）
4. **`snapshotBomLines()`**：+包参数；REPLACE（按 child_material_id 定位替换物料/用量）/ADD（追加行）/REMOVE（removeIf）
5. **门禁**：私有方法 `assertFlashStepsBound(mesWoId)`（每个 FLASH 步骤必须有 contentRefId 或 stepFlashConfig 含 contentRefId 项），在 `release()` CAS 之前与 `submit()` 启动流程之前调用；`release()` 手动工单路径若 `wo.releasePackageId` 非空也加载包覆写（幂等快照分支）

### 修改 `ProWorkOrderController` + 新 Bo

`PUT /mes/pro/work/{mesWoId}/step/{stepId}/firmware`（`UpdateStepFirmwareBo`）：校验工单 CREATED、步骤 FLASH 且属于该工单、固件 RELEASED 且 material 匹配工单物料；更新 `contentRefId` 并置 `firmware_source=MANUAL`

### 修改 ERP 侧（3 文件）

- `PrdProductionPlanServiceImpl.generateWorkOrders()`：经 `plan.orderLineId` 查 SO 行（customerProductCode）+ SO 头（clientId/clientName），落到新工单字段（单一落点，非 SO 驱动的工单保持空）
- `PrdWorkOrderServiceImpl.sendWorkOrderReleaseMessage()`：+3 字段赋值
- `PrdWorkOrder` 实体/Bo/Vo、`SoSalesOrderLine` 实体/Bo/Vo +字段（SO 行删重建模式下自动流经）

### 修改 `FirmwareServiceImpl.archive()`

+守卫：固件被 RELEASED 状态发布包映射时禁止归档，提示先更新发布包（防工单创建时 fail-loudly）

## 四、前端改动

**新增**：
- `api/mes/release/package.ts`（注意：本仓库 API 是 .ts，文档写的 .js 有误）
- `views/mes/release/package/index.vue`（列表：客户/物料/状态查询，按状态显示 编辑/提交审批/审批/归档/设默认）
- `views/mes/release/package/form.vue`（基本信息 + 固件映射/BOM选配/配置项 三个动态行编辑子表）
- `views/mes/pro/workorder/components/FirmwareSelectDialog.vue`（按物料列 RELEASED 固件选择弹窗）

**修改**：
- `views/mes/pro/workorder/detail.vue`：基本信息区+发布包编码/版本/客户代号（跳转查看）；工序列显示固件版本+来源标记（发布包/工艺路线/物料默认/手动），CREATED 状态显示[更改]按钮；FLASH 未绑定红色警示；BOM 明细行标"发布包选配"
- `views/mes/pro/workorder/form.vue`：+发布包选择器（按物料过滤，手动工单用）
- `views/erp/so/orderheader/form.vue`（实际路径，非文档写的 so/order/）：行编辑+客户产品代号/名称输入

## 五、SQL 脚本

`script/sql/update/2026-08-22-mes-release-package.sql`（分 ry-mes / ry-erp 两段）+ 同步追加 `ry-mes.sql`、`ry-erp.sql` 基线：
- 4 新表 DDL + 4 表加列
- 字典：`mes_release_pkg_status`、`mes_release_pkg_action`、`mes_release_cfg_group`（沿用固件脚本 6009xx 模式）
- 菜单权限 `mes:release:package:*`（沿用固件菜单 4280-4295 模式分配新 ID 段；路由由 sys_menu 驱动，不改 router）
- `wf_custom_form` 注册审批服务 `mesReleasePackageService`
- 编码规则 `MES_RP_CODE`（package_code 自动生成，沿用 MES_FW_CODE 模式）

## 六、实施阶段（一步到位，约 6.5 天）

| 阶段 | 内容 | 工时 |
|------|------|------|
| P1 | SQL + 4 实体/Mapper/Bo/Vo + Service CRUD/状态机(Flowable)/匹配 + Controller + 前端列表/表单页 | 1.5d |
| P2 | SO/ERP工单字段 + MQ消息 + matchPackage 集成 + 步骤固件覆写 + BOM选配覆写 + releaseConfig 快照 | 2d |
| P3 | 工单固件编辑接口 + firmware_source + FLASH 门禁(release/submit 双入口) + 固件归档守卫 | 1d |
| P4 | 前端：detail.vue + FirmwareSelectDialog + orderheader/form.vue + 手动工单发布包选择 | 1d |
| P5 | 单测 + 全链路手工测试 + 设计文档 `doc/mes/plans/` | 1d |

## 七、测试要点

发布包状态机全流转；无匹配包→标准工艺路线（回归）；包映射覆写 contentRefId 且 firmware_source=PACKAGE；映射固件已归档→工单创建报错不静默回退；多固件（BIOS+SSD）各自命中工序；REPLACE/ADD/REMOVE 三种选配在 MBOM 快照生效；FLASH 配置项注入 stepFlashConfig；FLASH 步骤未绑定→release 与 submit 双入口拦截；下达后修改固件被拒；工单审批路径（submit→afterFlowHandle→RELEASED）门禁生效；clientId 从 SO 头→ERP工单→MQ→MES工单→匹配包全链路透传；多租户下包隔离。

## 八、风险与注意点

- `afterFlowHandle` 是 Flowable(master 事务)回调，发布包实现必须镜像固件的 `@DS("mes")+REQUIRES_NEW` 模式，否则写入会落到 ry-vue 报表不存在
- ERP 侧 generateWorkOrders 查 SO 行/头是同库(ry-erp)操作，直接注入对应 Mapper 即可，无需桥接
- 匹配逻辑在 MQ 消费线程执行，租户上下文由消费者恢复（BaseMessage 携带 tenantId），与现有 createFromErpMessage 一致
- 雪花 ID 序列化已由全局 BigNumberSerializer 处理，新增接口无需处理