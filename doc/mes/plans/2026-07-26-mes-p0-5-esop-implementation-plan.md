# P0-5 E-SOP 电子作业指导书模块详细实施方案

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **编制日期**: 2026-07-26
> **计划周期**: Week 34（2026-09-14 ~ 09-20）；属 Phase 4-C 质量与合规类
> **所属计划**: [2026-07-24-mes-p0-development-plan-v1.0.md](./2026-07-24-mes-p0-development-plan-v1.0.md) §3.5
> **状态**: ✅ 编码完成 + SQL 落库 + 端到端联调通过（28/28 断言），后端 BUILD SUCCESS + 前端 eslint 0 错误

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

对标 MESA-4 文档控制、鼎捷"E-SOP 管理"、西门子 Opcenter "Electronic Procedures"。当前 MES **完全没有 SOP / 作业指导 / 文档控制**能力（探索结论：`ruoyi-mes` 全模块 grep `sop`/`guide`/`作业指导`/`attachment`/`media` 零命中），工序作业依赖纸质 SOP 或口头交接，存在三大断点：

1. **版本失控**：纸质 SOP 改版后产线仍用旧版，质量事故无法追溯到具体版本
2. **推送缺失**：工单开工后操作员无法在工位终端即时获取当前工序的正确 SOP
3. **查阅无痕**：操作员是否查阅、何时查阅、查阅哪个版本无记录，合规审计无据

P0-5 是 P0-3（进站校验"SOP 版本"项）的硬依赖，也是 P0-6（电子签名接入 SOP 查阅）的前置。

### 1.2 本周要解决的问题

1. **SOP 文档库缺失**：无主档管理 SOP 编码/名称/类型/状态/文件
2. **版本管理缺失**：无多版本共存、发布/归档/废止机制
3. **工序绑定缺失**：SOP 与工序无多对多绑定，无法按工序定位 SOP
4. **工位推送缺失**：工单进站后无法自动推送当前工序 SOP 到工位终端
5. **查阅记录缺失**：操作员查阅历史无记录，无法支撑合规审计
6. **富媒体缺失**：仅支持单一格式，无法覆盖 PDF/图片/视频/Word 多种作业指导形态
7. **审批流缺失**：SOP 发布无审批管控，错误版本可直接上线

### 1.3 关键边界（已确认）

- **独立 `sop` 模块**（非 dc 扩展）：4 张 `sop_` 表，镜像 tg/lt 包布局。主计划 §3.5 写"dc 扩展 + sop"，但 SOP 本质是文档控制（MESA-4），与数据采集（dc）领域不同，独立包更清晰。dc 仅在"IoT 数据归档到 SOP 关联工单"这一远景点有交集，本周不做。
- **文件存储用 OSS ossId（非 file_url）**：⚠️ **偏离主计划 §3.5.3 DDL 的 `file_url VARCHAR(500)`**。依据 memory `oss-private-bucket-store-ossid`：MinIO 桶为 private，上传返回 120s 预签名 URL（超长 + 过期），直接存 URL 会触发 `Data truncation` 且链接过期失效。**正确做法**：存 `file_oss_id BIGINT`，VO 用 `@Translation(type = OSS_ID_TO_URL, mapper = "fileOssId")` 解析为临时签名 URL（对齐 `EqPatrolRecordVo.photoOssId` 模式）。`file_type` 字段保留（PDF/IMAGE/VIDEO/WORD，用于前端选择渲染组件）。
- **审批流走 Flowable + 兜底**：`ruoyi-mes` 已依赖 `ruoyi-flowable`（pom 已确认），采用 `WmSalesIssueServiceImpl` 同款模式——`SopDocumentServiceImpl` 实现 `WfCallBackServiceI`，`submitForApproval` 调 `IWfProcessService.startProcessByDataId`，`afterFlowHandle` 回调驱动状态机。**兜底**（主计划 §10 R11）：提供 `approve`/`reject` 管理员强制端点，未部署 BPMN 时可直接审批，不阻塞业务。
- **工序绑定到 `bd_process`（工序主数据）**：ERP 有完整三层工艺模型（详见 §1.5）。`sop_process_binding.process_id` = `bd_process.process_id`（工序主数据 PK，**非** `bd_routing_step.step_id`）。SOP 是"如何执行某工序"的作业指导，与工序本身绑定（如"焊接工序 SOP"），跨工艺路线复用--同一种工序在不同路线中出现，SOP 只绑一次。冗余存 `process_code`/`process_name`/`process_category`，避免跨库查 ERP 与列表 N+1（对齐 P0-9/P0-2 冗余字段策略）。**不新建 `ErpProcessMapper`**--绑定用前端选择器带入冗余字段，工位推送走 `ProFlowCardStep` 已冗余的 `processId`（MES 库内），均不跨库查 ERP。
- **电子签名字段预留**：`sop_view_log.signature_id` 预留 BIGINT，本周仅记录查阅（viewer_id + view_time + 关联工单/工序），实际签名接入待 P0-6（`ESignatureService` 落地后回填 signature_id）。F5-5"含电子签名"本周完成查阅记录 + 预留字段，签名强制校验列为 P0-6 集成点。
- **工位推送 = 查询 API + 查看器页 + SSE 契约**：F5-4 提供三类能力：① `GET /mes/sop/document/by-process/{processId}` 查询绑定 SOP；② `views/mes/sop/viewer/index.vue` 工位终端查看器（按 processId/stepRecordId 拉取并渲染富媒体）；③ `pushSopToWorkstation(stepRecordId)` 契约方法（用 `SseMessageUtils.publishMessage`，ruoyi-mes 已用于 `ProAndonServiceImpl`/`MaintenanceDueTask`），供 P0-3 `startStep` 调用触发实时推送。本周实现查询 + 查看器 + 推送方法，**触发时机由 P0-3 接入**。
- **版本流转**：`sop_document.status` = DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED；`sop_version.status` = PENDING → CURRENT → HISTORICAL。发布时新版本→CURRENT，旧 CURRENT→HISTORICAL，`sop_document.current_version`/`file_oss_id` 同步更新。
- **编码生成**：`autoCodeUtil.genSerialCode("MES_SOP_CODE", null)`，规则 `SOP + yyyyMMdd + 3位日循环`（对齐 P0-2 `MES_TOOLING_CODE` / P0-9 `MES_LOT_CODE`）。
- **跨库**：process/workstation 信息冗余存储（`process_code`/`process_name`/`workstation_id`），不跨库查 ERP。

### 1.4 预期结果

- SOP 可创建/编辑/提交审批/发布/归档，版本机正确流转
- 多版本共存，发布后旧版本自动归档，当前版本可查
- SOP 可与工序多对多绑定，支持指定版本 + 默认 SOP 标记
- 工位终端可通过 processId/stepRecordId 拉取当前工序 SOP 并渲染（PDF/图片/视频/Word）
- 查阅记录完整（viewer/time/工单/工序/IP），signature_id 预留待 P0-6
- Flowable 审批接入 + 管理员兜底审批双通路
- `mvn compile`（mes）与前端 `eslint` 通过，vue-tsc 仅基线噪声

### 1.5 ERP 工艺/工序模型分析（SOP 绑定设计基础）

> 2026-07-26 据 `ruoyi-erp` `BdProcessController`/`BdRoutingController` + 实体类分析补入。SOP 绑定设计基于此模型。

ERP 工艺数据为**三层结构**（`com.nexus.erp.bd` 包）：

```
bd_routing（工艺路线） ──1:N──> bd_routing_step（路线步骤） ──N:1──> bd_process（工序主数据）
       │                           │
       │ material_id（适用物料）     │ process_id（FK→bd_process）
       │ routing_version+is_current │ sequence_num（步骤序号）
       │                            │ workstation_id（可覆盖工序默认工位）
       │                            │ description（步骤说明/作业指导 ← 已有文本字段）
       ▼
  getCurrentRouting(materialId)  ← 物料的当前有效工艺路线
  getRoutingSteps(routingId)     ← 路线的工序步骤列表
```

**`bd_process`（工序主数据，`BdProcess`）关键字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `process_id` (PK) | Long | 工序 ID（SOP 绑定目标） |
| `process_code` / `process_name` | String | 编码/名称 |
| `process_category` | String | **PROCESSING-加工/ASSEMBLY-装配/INSPECTION-检验/PACKAGING-包装** |
| `workstation_id`/`code`/`name` | - | 默认工作中心 |
| `standard_hours`/`setup_hours` | BigDecimal | 标准工时/准备工时 |
| `is_key_process` | Integer | 是否关键工序（0/1） |
| `is_outsource` | Integer | 是否委外工序 |
| `quality_required` | Integer | 是否需要质检 |

**`bd_routing_step`（路线步骤，`BdRoutingStep`）**：`step_id`(PK) + `routing_id` + `sequence_num` + `process_id`(FK) + 冗余 `process_code`/`process_name` + `workstation_id`（可覆盖工序默认）+ `is_key_process`/`quality_required` + **`description`（步骤说明/作业指导，已有文本字段）**。

**关键洞察与设计影响**：

1. **SOP 绑定层级 = `bd_process`（工序级），非 `bd_routing_step`（步骤级）**：SOP 描述"如何执行某工序"，跨路线复用；步骤级差异（同工序在不同路线的工位/工时差异）由 `bd_routing_step` 自身字段承载。`sop_process_binding.process_id` = `bd_process.process_id`。

2. **`bd_routing_step.description` 已是轻量作业指导**：ERP 路线步骤已有文本"步骤说明/作业指导"字段。SOP 模块是它的**富媒体增强版**（PDF/图片/视频/Word），不替换它。三者分工：
   - `bd_routing_step.description`：路线步骤级简短文本说明（ERP 已有，**当前为死字段**--前端不可编辑、`ErpRoutingStepMapper` 不 SELECT、`ProWorkOrderStep`/`ProFlowCardStep` 无此字段、前端零展示，从未到达操作员）
   - `pro_step_guide`（P0-3）：工序级流程指引项（**TEXT + SOP_LINK** 两种类型，详见 §1.6 收敛决策）
   - `sop_document`：工序级富媒体 SOP（MES 新增，绑 `bd_process`，跨路线复用，**唯一富媒体 SOP 载体**）
   
   本周 SOP 与 `description` 并存；P0-3 `pro_step_guide` 的 SOP_LINK 类型对接 SOP（契约）。

3. **工位推送完整链路**：工单(woId) → 物料(materialId) → `getCurrentRouting(materialId)` → `getRoutingSteps(routingId)` → 当前 `stepRecordId` 对应步骤 → `process_id` → `getSopForProcess(processId)`。**简化实现**：`ProFlowCardStep` 已冗余 `processId`/`processCode`/`processName`，`getSopForStep(stepRecordId)` 直接经 `ProFlowCardStepMapper` 取 `processId` → `getSopForProcess`，无需绕 routing 跨库查。

4. **`process_category` 用于展示与辅助筛选**：`mes_sop_type`（PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN，SOP 文档类型视角）与 `process_category`（工序类别视角）语义不同但交叉--INSPECTION 类工序默认关联 INSPECTION 类 SOP（提示，不强制映射）。绑定页面展示工序 `process_category` 帮助用户选对 SOP。

5. **工序选择器复用 ERP 既有 API**：前端 `listProcess`（`/erp/bd/process/list`，`plus-ui/src/api/erp/bd/process/index.ts` 已存在）+ `getCurrentRouting`/`getRoutingSteps`（`bd/routing/index.ts` 已存在）。选择器展示工序编码/名称/类别/默认工位/关键工序/质检标志，选中后带入 `process_id`/`code`/`name`/`category` 冗余字段。**无需新建 ERP 侧 API**。

### 1.6 与 P0-3 `pro_step_guide` 的设计收敛（2026-07-26 评审补入）

> 评审发现 P0-3 `pro_step_guide` 计划的 `guide_type` 含 IMAGE/VIDEO + `media_url`，与 `sop_document` 富媒体能力**高重叠**--若工艺员在 pro_step_guide 直接粘贴未版本化媒体，会形成"影子 SOP"绕过文档控制（版本/审批/审计）。本评审建议三层收敛，P0-5 据此明确边界。

**三层作业指导模型**：

| 层 | 表 | 归属 | 粒度 | 内容 | 约束 |
|---|---|---|---|---|---|
| L1 | `bd_routing_step.description` | ERP 既有 | 路线步骤 | varchar(500) 文本 | 死字段，保持现状（本周不动） |
| L2 | `pro_step_guide` | P0-3 | 工序 | 快捷指引序列 | **guide_type 限 TEXT + SOP_LINK**（去 IMAGE/VIDEO）；删除 `media_url` |
| L3 | `sop_document` + `sop_process_binding` | P0-5 | 工序 | 正式文档（版本/审批/审计） | **唯一富媒体 SOP 载体**；pro_step_guide.SOP_LINK 须引用已绑定 SOP |

**P0-5 边界明确**：
- `sop_document` 是**唯一富媒体 SOP 载体**（PDF/图片/视频/Word）。富媒体作业指导必须先建 sop_document（走版本+审批），再由 pro_step_guide 用 SOP_LINK 引用，杜绝影子 SOP。
- **进站推送的是 `sop_document`**（经 `sop_process_binding` -> `getSopForProcess`），不是 pro_step_guide。
- **工位终端展示协作**：先展示 `pro_step_guide` 指引序列（TEXT 提示 + SOP_LINK 项），遇 SOP_LINK 展开 `sop_document` 正式文档（含版本/查阅登记）。
- **`sop_process_binding` 是规范绑定目录**（"该工序分配了哪些正式 SOP"），与 `pro_step_guide.sop_id`（"该指引项链接哪个 SOP"）**不冗余**。但 P0-3 落地时 `pro_step_guide.sop_id`（SOP_LINK 类型）须校验该 SOP 在 `sop_process_binding` 已绑定到同 process_id，防孤儿引用。

**对 P0-3 的建议（主计划 §3.3 修订项）**：`pro_step_guide.guide_type` 去掉 IMAGE/VIDEO，只留 TEXT + SOP_LINK；删除 `media_url` 字段（TEXT 用 guide_content，SOP_LINK 用 sop_id）。

### 1.7 生产数据流与 SOP 插入点（2026-07-26 评审补入）

> 评审追踪 ERP->MES 完整生产数据流，确认 SOP 的精确插入点。

**生产数据流全图**：

```
ERP: PrdProductionPlan 生产计划
       -> PrdWorkOrder 工单 (无 routing_id)
         -> release(woId) [或 Flowable 审批通过]
           -> MQ: WorkOrderReleaseMessage {woId, materialId, bomId, workshopId, routingId=null}

MES (WorkOrderReleaseConsumer -> ProWorkOrderServiceImpl.createFromErpMessage):
  -> getDefaultRoutingId(materialId)  // bd_material.default_routing_id
  -> getRoutingSteps(routingId)        // ErpRoutingStepMapper (不查 description)
  -> ProWorkOrder + ProWorkOrderStep   // 复制 process_id/code/name/workstation
  -> MBOM 快照 (ProWorkOrderBomLine.operationContent 物料级作业内容)
  -> ProFlowCard + ProFlowCardStep     // 复制自 ProWorkOrderStep
     -> startStep(cardId, stepRecordId, operatorId)  ★进站时刻 = SOP 唯一插入点★
        ProFlowCardStep: PENDING -> IN_PROGRESS
     -> completeStep(...)  ★出站★
     -> completeCard -> completeWo
        -> MQ: MesWorkOrderCompletedMessage -> ERP ErpWorkOrderCompletedConsumer
           -> PrdWorkOrder COMPLETED -> 成品入库 + 倒冲领料
```

**SOP 插入点**：`ProFlowCardServiceImpl.startStep()`（[line 200](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java)）。操作员进站时：
1. 经 `ProFlowCardStep.processId`（MES 库内冗余）-> `sop_process_binding` -> `sop_document`
2. SSE 推送到工位终端（`pushSopToWorkstation`，`SseMessageUtils`）
3. 终端渲染富媒体 SOP（查看器页）
4. 登记 `sop_view_log`（含 viewer/time/工单/工序/IP/UA）

`by-material` 预加载（`getSopForMaterial`）是优化--工单开工时一次性拉全部工序 SOP，减少逐站查询，非新插入点。

**关键事实**：
- ERP `PrdWorkOrder` 无 `routing_id`，MES 经 `MesErpBridgeService.getDefaultRoutingId(materialId)` 取默认路线
- `WorkOrderReleaseMessage` payload 不含工艺步骤，MES 桥接服务单独拉 `bd_routing_step`
- `bd_routing_step.description`（作业指导）从未传入 MES（死字段，§1.5 已述）
- MES 无独立工位终端页，操作员流程嵌在 `ScanExecute.vue`（扫码对话框，仅显示 processName，无 SOP）--P0-5 查看器页 `views/mes/sop/viewer/index.vue` 填补此空白
- P0-3 `startStep` 进站校验门禁（F3-1/F3-2）是 SOP 推送的天然触发点（P0-3 落地后接入 `pushSopToWorkstation`）

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| `ruoyi-mes` 已依赖 `ruoyi-flowable`（pom.xml 含 `<artifactId>ruoyi-flowable</artifactId>`），可直接用 `IWfProcessService` + `WfCallBackServiceI` | `ruoyi-modules/ruoyi-mes/pom.xml` |
| Flowable 启动模式：`processService.startProcessByDataId(dataId, selectFlowId, serviceName, variables)`，serviceName=Spring bean 名；回调 `afterFlowHandle(WfMyBusiness)` 驱动业务状态机（`ActStatus.doing/pass/reject/stop/recall`） | [WmSalesIssueServiceImpl.java:372,412](../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSalesIssueServiceImpl.java) |
| OSS 上传：`ISysOssService.upload(MultipartFile)` 返回 `SysOssVo`（含 `ossId`），业务表存 ossId；VO 用 `@Translation(type=TransConstant.OSS_ID_TO_URL, mapper="fileOssId")` 解析签名 URL | [SysOssController](../../ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysOssController.java) + [EqPatrolRecordVo](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/domain/vo/EqPatrolRecordVo.java) |
| SSE 推送在 MES 已用：`SseMessageUtils.publishMessage`（`ProAndonServiceImpl` 安灯推送、`MaintenanceDueTask` 保养到期推送） | `ruoyi-mes/.../pro/service/impl/ProAndonServiceImpl.java` + `eq/task/MaintenanceDueTask.java` |
| ERP 三层工艺模型：`bd_process`（工序主数据，`BdProcess`）+ `bd_routing`（工艺路线，`BdRouting`，版本化+物料绑定+`is_current`）+ `bd_routing_step`（路线步骤，`BdRoutingStep`，`process_id` FK->`bd_process` + `sequence_num` + `description` 文本作业指导）；`BdProcess` 含 `process_category`(PROCESSING/ASSEMBLY/INSPECTION/PACKAGING)/`is_key_process`/`quality_required`/默认工位 | [BdProcess.java](../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/BdProcess.java) + [BdRouting.java](../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/BdRouting.java) + [BdRoutingStep.java](../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/BdRoutingStep.java) |
| ERP 工序/路线 API 已就绪：`BdProcessController`(`/erp/bd/process`,CRUD,perm `bd:process:*`) + `BdRoutingController`(`/erp/bd/routing`,CRUD+`/current/{materialId}`物料当前路线+`/steps/{routingId}`路线步骤+`/activateVersion`) | [BdProcessController.java](../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/controller/BdProcessController.java) + [BdRoutingController.java](../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/controller/BdRoutingController.java) |
| 前端 ERP API 已存在：`listProcess`/`getProcess`(`plus-ui/src/api/erp/bd/process/index.ts`，`ProcessVO` 含 processCategory/isKeyProcess/qualityRequired) + `listRouting`/`getCurrentRouting`/`getRoutingSteps`(`bd/routing/index.ts`) | `plus-ui/src/api/erp/bd/{process,routing}/index.ts` |
| MES 无 `ProProcess` 实体：工序执行记录 `ProFlowCardStep`/`ProWorkOrderStep` 冗余 `processId`/`processCode`/`processName`（源自 ERP `bd_process`）；MES 跨库查 `bd_routing_step` 走 `ErpRoutingStepMapper`（返回 `Map`，无 `ErpProcessMapper`） | [ErpRoutingStepMapper.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mapper/ErpRoutingStepMapper.java) + `ProFlowCardStep.processId/processCode/processName` |
| 流转卡工序执行：`ProFlowCardStep.stepRecordId` 定位执行中的工序记录，含 `cardId`/`mesWoId`/`processId`/`workcenterId`/`stepStatus`(PENDING/IN_PROGRESS/COMPLETED) | [ProFlowCardStep.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProFlowCardStep.java) |
| Entity/BO/VO/Mapper/Service/Controller 既定模式（@DS("mes")、TenantEntity、BaseMapperPlus、@Lock4j、CAS 状态机、buildQueryWrapper、autoCodeUtil） | P0-2 [实施方案 §二](./2026-07-26-mes-p0-2-tooling-management-implementation-plan.md) + [LtLotServiceImpl](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java) |
| 菜单现状：MES 顶级 M 目录 4000（order 6）下已有 4001/4002/4004/4005/4006/4150/4151/4201-4205 共 12 个子 M 目录；**max(menu_id)=4205**；无"文档管理"/"SOP"菜单；role_id=3(test1) 为测试授权角色（P0-2 同款） | 本地 DB 查询（2026-07-26） |
| 字典/autocode SQL 主键雪花：`sys_dict_type`/`sys_dict_data`/`sys_auto_code_rule`/`sys_auto_code_part` 动态 MAX+1 + `WHERE NOT EXISTS` 守护，目标库 ry-vue，CLI 执行需 `USE \`ry-vue\`;` + `SET NAMES utf8mb4;` | memory `ruoyi-snowflake-dict-menu-id` + `mysql-cli-charset-utf8mb4` + P0-2 03/04 SQL |
| MES 前端约定：全选择器化（无裸 ID 输入）+ dict-tag（无硬编码状态 map） | memory `mes-lot-remediation-and-erp-batch-conflict` + P0-2 §任务5 |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 | 状态 |
|---|------|--------|--------|----------|------|
| 1 | DDL（4 表） | 高 | `mes-p0-05-01-sop.sql` | 四表对齐既定约定，`file_oss_id` 替代 `file_url`，`CREATE TABLE IF NOT EXISTS` 幂等 | ⬜ |
| 2 | Entity/BO/VO/Mapper | 高 | 16 个 Java 文件 | 字段对齐 DDL，VO 含 `@Translation(OSS_ID_TO_URL)` + 用户名翻译 | ⬜ |
| 3 | Service + 实现 | 高 | 4 接口 + 4 实现 | CRUD + 版本状态机 + Flowable 审批接入 + 兜底审批 + 工序绑定 + 查阅记录 + 推送契约 | ⬜ |
| 4 | Controller | 高 | 4 REST 控制器 | list/get/add/edit/del + 提交审批/通过/驳回/归档 + by-process/by-step 查询 + 查阅登记 | ⬜ |
| 5 | 前端 API + 页面 | 高 | 4 API + 5 页面 | 文档(含版本 Tab)/绑定/查阅记录/查看器，全选择器化 + dict-tag + OSS 上传组件 | ⬜ |
| 6 | 菜单权限 SQL | 中 | `mes-p0-05-02-menu.sql` | M-dir 4206 + C/F 4261-4275（16 条），父菜单 4000 | ⬜ |
| 7 | 自动编码规则 | 中 | `mes-p0-05-03-autocode-sop.sql` | `MES_SOP_CODE`（SOP+日期+3位日循环） | ⬜ |
| 8 | 字典种子 | 中 | `mes-p0-05-04-sop-dicts.sql` | 4 字典（status/type/file_type/version_status） | ⬜ |
| 9 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 | ⬜ |
| 10 | 端到端联调 | 高 | `p0-5-e2e-test.sh` | 建档→提交→发布→版本归档→工序绑定→查阅登记→查看器拉取 全链路 | ⬜ |

---

## 四、详细任务分解

### 任务 1：DDL（4 表）

**File:** Create `doc/mes/sql/mes-p0-05-01-sop.sql`

四表设计（见 §六 DDL 详情）：
- `sop_document`：SOP 主档（编码/名称/类型/当前版本/状态/当前文件 ossId/审批字段）
- `sop_version`：版本历史（version_no/file_oss_id/change_log/published_by/status PENDING→CURRENT→HISTORICAL）
- `sop_process_binding`：工序-SOP 绑定（process_id 冗余 code/name/sop_id/version_no/is_default）
- `sop_view_log`：查阅记录（viewer_id/view_time/wo_id/process_id/signature_id 预留/IP/UA）

**关键修正**：
- `file_oss_id BIGINT` 替代 `file_url VARCHAR(500)`（OSS 私有桶存 ID，memory `oss-private-bucket-store-ossid`）
- 保留 `file_type VARCHAR(16)`（前端渲染分发：PDF→iframe，IMAGE→img，VIDEO→video，WORD→下载链接）
- 冗余 `process_code`/`process_name`/`viewer_name`/`wo_no`，避免跨库查 ERP 与列表 N+1
- `sop_version.status` 增加 `PENDING`（草稿/待审版本），原计划仅 CURRENT/HISTORICAL 不足覆盖新建未发布版本

### 任务 2：Entity/BO/VO/Mapper（16 文件）

**Packages:** `com.nexus.mes.sop.*`（新建包，镜像 tg/lt 布局）

每张表 4 文件（domain + bo + vo + mapper），共 16 个。

**VO 关键增强**：
- `SopDocumentVo`：`fileOssId` + `@Translation(type=OSS_ID_TO_URL, mapper="fileOssId") fileUrl`（自动解析签名 URL）；`createBy`/`approvalBy` 用 `@Translation(USER_ID_TO_NAME)` 翻译
- `SopVersionVo`：同样 `fileOssId` + `fileUrl` 翻译；`publishedByName` 翻译
- `SopProcessBindingVo`：`sopName`/`sopCode`（联表或 Service 填充）+ `processName`/`processCategory` 冗余（dict-tag 展示工序类别）
- `SopViewLogVo`：`viewerName` 用 `@Translation(USER_ID_TO_NAME, mapper="viewerId")`；`sopName` 填充

### 任务 3：Service + 实现（8 文件）

#### 3.1 `SopDocumentServiceImpl`（核心，实现 `WfCallBackServiceI`）

**状态机**：

```
   addVersion(fileOssId, changeLog)        submitForApproval(selectFlowId)
   ┌──────────────────────────┐            ┌─────────────────────┐
   ▼                           │            ▼                     │
┌──────────┐  submitForApproval │  ┌──────────────┐  approve     │  ┌───────────┐
│  DRAFT   │─────────────────────▶│  IN_REVIEW   │──────────────▶│ PUBLISHED │
└────┬─────┘                     └──────┬───────┘               └─────┬─────┘
     │                                  │ reject                       │ archive
     │ recall/stop (Flowable)           ▼                              ▼
     │                           ┌──────────┐                   ┌──────────┐
     │                           │  DRAFT   │                   │ ARCHIVED │
     │                           │ (驳回回退)│                   │ (废止终态)│
     │                           └──────────┘                   └──────────┘
     │
     │  addVersion (PUBLISHED 上修订新版本)
     ▼  sop_document.status -> DRAFT, sop_version 新行 PENDING
   循环
```

**关键方法**：

```java
// 新建 SOP + 首版本（DRAFT）
@Transactional(rollbackFor = Exception.class)
public Boolean insertByBo(SopDocumentBo bo);  // genSerialCode("MES_SOP_CODE") + 建 sop_version v1.0 PENDING

// 新增版本（DRAFT 或 PUBLISHED 上修订）-> sop_version PENDING, sop_document.status DRAFT
@Lock4j(keys = {"#sopId"})
@Transactional(rollbackFor = Exception.class)
public Boolean addVersion(Long sopId, Long fileOssId, String fileType, String changeLog);

// 提交审批：DRAFT -> IN_REVIEW。selectFlowId 非空则走 Flowable, 否则直接 IN_REVIEW
@Transactional(rollbackFor = Exception.class)
public Boolean submitForApproval(Long sopId, String selectFlowId);

// Flowable 回调：doing->IN_REVIEW, pass->doPublish, reject/stop->DRAFT
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void afterFlowHandle(WfMyBusiness business);

// 兜底审批通过（管理员强制, R11）: IN_REVIEW -> PUBLISHED, 内部调 doPublish
@SaCheckPermission("mes:sop:document:approve")
@Transactional(rollbackFor = Exception.class)
public Boolean approve(Long sopId, String remark);

// 兜底驳回: IN_REVIEW -> DRAFT
@SaCheckPermission("mes:sop:document:reject")
@Transactional(rollbackFor = Exception.class)
public Boolean reject(Long sopId, String reason);

// 内部发布: 最新 PENDING 版本 -> CURRENT, 旧 CURRENT -> HISTORICAL, sop_document.status PUBLISHED,
//          current_version/file_oss_id/file_type 同步, 写 published_time/by
private void doPublish(Long sopId);

// 归档（废止）: PUBLISHED -> ARCHIVED
@Transactional(rollbackFor = Exception.class)
public Boolean archive(Long sopId, String reason);

// Flowable 业务数据回显
@Override
public Object getBusinessDataById(String dataId);

// 删除孤儿守卫: PUBLISHED 不可删（先归档）; 存在版本/绑定/查阅记录不可删
@Transactional(rollbackFor = Exception.class)
public Boolean deleteByIds(Long[] ids);
```

**Flowable 接入要点**（镜像 `WmSalesIssueServiceImpl`）：
- `@Service("sopDocumentService")`（bean 名即 serviceName）
- `submitForApproval` 内：`processService.startProcessByDataId(sopId.toString(), selectFlowId, "sopDocumentService", variables)`
- `afterFlowHandle` 内按 `ActStatus` 分发：`pass` → `doPublish`；`doing` → IN_REVIEW；`reject`/`retrun` → DRAFT；`stop`/`recall` → DRAFT
- `variables` 放 `sopCode`/`sopName`/`sopType` 供审批表单回显
- **兜底**：`approve`/`reject` 端点直接调 `doPublish`/回退，不依赖 Flowable 部署状态

#### 3.2 `SopVersionServiceImpl`

- CRUD + `selectBySop(sopId)` 返回版本列表（按 published_time desc）
- `setCurrent(versionId)` 内部方法（供 `doPublish` 调用）：新版本 CURRENT，旧 CURRENT → HISTORICAL
- 删除守卫：CURRENT 版本不可删（先发布新版本或归档 SOP）

#### 3.3 `SopProcessBindingServiceImpl`

- CRUD + `selectByProcess(processId)` 返回工序绑定的 SOP 列表（is_default 优先 + sort_order）
- `bindSop(processId, sopId, versionNo, isDefault, processCode, processName, processCategory)`：前端选择器带入工序冗余字段；若 isDefault=1，同工序其他绑定置 0（CAS 排他）
- 契约方法 `getSopForProcess(processId)`：返回默认 SOP（含文件 URL），供 P0-3 / 查看器调用
- 契约方法 `getSopForStep(stepRecordId)`：经 `ProFlowCardStepMapper`（MES 库内）查 stepRecordId → processId → 绑定 SOP

- 契约方法 `getSopForMaterial(materialId)`：工单开工预加载--物料 -> 当前工艺路线（`ErpRoutingHeaderMapper`）-> 路线步骤（`ErpRoutingStepMapper`）-> 各步骤 processId -> 绑定 SOP 列表，返回 `Map<processId, List<SopVo>>`，供工单开工一次性拉取全部工序 SOP（减少工位逐站查询）
- 删除守卫：`process_category` 等冗余字段仅展示用，不参与校验

#### 3.4 `SopViewLogServiceImpl`

- `recordView(sopId, versionNo, woId, processId, workstationId)`：写查阅记录，viewer_id = LoginHelper.getUserId()，viewer_name 冗余，ip/ua 从 RequestUtils 取，signature_id=null（P0-6 接入）
- 列表查询（按 sop_id / viewer_id / wo_id / 时间段）
- `signature_id` 待 P0-6 `ESignatureService.sign()` 回填（本周仅预留字段）

### 任务 4：Controller（4 个）

| 端点 | 权限 | 说明 |
|------|------|------|
| GET /mes/sop/document/list | mes:sop:document:list | SOP 文档列表 |
| GET /mes/sop/document/{sopId} | mes:sop:document:query | 详情（含版本列表） |
| POST /mes/sop/document | mes:sop:document:add | 新增（编码自动生成 + 首版本 PENDING） |
| PUT /mes/sop/document | mes:sop:document:edit | 编辑（DRAFT/ARCHIVED 可编辑，PUBLISHED 需先新增版本） |
| DELETE /mes/sop/document/{ids} | mes:sop:document:remove | 删除（孤儿守卫） |
| POST /mes/sop/document/version | mes:sop:document:add | 新增版本 |
| POST /mes/sop/document/submit/{sopId} | mes:sop:document:submit | 提交审批（可选 selectFlowId） |
| PUT /mes/sop/document/approve/{sopId} | mes:sop:document:approve | 兜底通过 |
| PUT /mes/sop/document/reject/{sopId} | mes:sop:document:reject | 兜底驳回 |
| PUT /mes/sop/document/archive/{sopId} | mes:sop:document:archive | 归档废止 |
| GET /mes/sop/document/by-process/{processId} | mes:sop:document:list | 按工序查 SOP（工位推送用） |
| GET /mes/sop/document/by-step/{stepRecordId} | mes:sop:document:list | 按流转卡工序记录查 SOP（经 ProFlowCardStep.processId） |
| GET /mes/sop/document/by-material/{materialId} | mes:sop:document:list | 按物料查全部工序 SOP（工单开工预加载，经 ERP 当前路线+步骤） |
| POST /mes/sop/document/push/{stepRecordId} | mes:sop:document:push | 触发 SSE 推送到工位（P0-3 调用契约） |
| GET /mes/sop/version/list | mes:sop:version:list | 版本列表 |
| DELETE /mes/sop/version/{ids} | mes:sop:version:remove | 删除版本（CURRENT 守卫） |
| GET /mes/sop/binding/list | mes:sop:binding:list | 工序绑定列表 |
| POST /mes/sop/binding | mes:sop:binding:add | 新增绑定 |
| DELETE /mes/sop/binding/{ids} | mes:sop:binding:remove | 删除绑定 |
| GET /mes/sop/view-log/list | mes:sop:viewlog:list | 查阅记录列表 |
| POST /mes/sop/view-log | mes:sop:viewlog:add | 登记查阅（查看器打开时调） |

### 任务 5：前端 4 API + 5 页面

- `api/mes/sop/document.ts` / `version.ts` / `binding.ts` / `view-log.ts`
- `views/mes/sop/document/index.vue` - 文档列表 + 新增/编辑 + **版本历史 Tab**（详情抽屉内）+ 提交审批/通过/驳回/归档按钮 + OSS 文件上传
- `views/mes/sop/binding/index.vue` - 工序-SOP 绑定 CRUD（工序选择器 + SOP 选择器）
- `views/mes/sop/view-log/index.vue` - 查阅记录列表
- `views/mes/sop/viewer/index.vue` - **工位终端查看器**（隐藏页 visible=1，按 `?processId=` / `?stepRecordId=` 拉取并渲染：PDF→`el-iframe`/`vue-pdf`，IMAGE→`el-image`，VIDEO→`<video>`，WORD→下载链接；打开时调 `/view-log` 登记查阅）

**全选择器化**（P0-9 第八/九批约定，无裸 ID 输入）：
- 工序：复用 ERP `listProcess`（`/erp/bd/process/list`，`plus-ui/src/api/erp/bd/process/index.ts` 已存在）选择对话框，展示工序编码/名称/**类别(process_category)**/默认工位/关键工序/质检标志，选中带入 `processId`/`processCode`/`processName`/`processCategory` 冗余字段（**无需新建 ERP API**）
- SOP：自建 `listSop` 选择对话框（仅列 PUBLISHED 状态）
- 工单：`listWorkOrder`（取 `mesWoId`，对齐 lt/consumption）
- 操作员/审批人：`UserSelect` 组件
- 文件：OSS 上传组件（复用 `SysOssController.upload`，存返回的 ossId；前端用 `OssFile` 或既有上传组件）

**dict-tag**（无硬编码状态 map）：
- 状态 `useDict('mes_sop_status')` + dict-tag
- 类型 `useDict('mes_sop_type')`
- 文件类型 `useDict('mes_sop_file_type')`
- 版本状态 `useDict('sop_version_status')`
- `is_default`/`enabled` 用 el-switch（CHAR(1) '1'/'0'，非状态字段不 dict 化，对齐 P0-2 §任务8 决策）

### 任务 6：菜单 SQL

**File:** `doc/mes/sql/mes-p0-05-02-menu.sql`

父菜单 **新建 4206 文档管理 M 目录**（parent=4000 MES系统，order_num=12，path=sop）。沿用 P0-2 "M-dir 取整百数 + C/F 用计划段" 模式（4205 工装管理 → 4206 文档管理）。C/F 用主计划 §7.3 分配的 **4261-4280** 段。

| ID | 类型 | 名称 | 父 | path/component |
|----|------|------|------|----------------|
| 4206 | M | 文档管理 | 4000 | sop（order 12） |
| 4261 | C | SOP文档 | 4206 | document / mes/sop/document/index |
| 4262-4268 | F×7 | 查询/新增/修改/删除/提交审批/通过/驳回 | 4261 | - |
| 4269 | C | 工序绑定 | 4206 | binding / mes/sop/binding/index |
| 4270-4272 | F×3 | 查询/新增/删除 | 4269 | - |
| 4273 | C | 查阅记录 | 4206 | view-log / mes/sop/view-log/index |
| 4274 | F×1 | 查询 | 4273 | - |
| 4275 | C(隐藏) | SOP查看器 | 4206 | viewer / mes/sop/viewer/index（visible=1） |

合计 1 M + 4 C + 11 F = 16 条。role_id=3(test1) 授权全部（对齐 P0-2）。

> ⚠️ **ID 段说明**：M-dir 4206 接续 4205（工装），C/F 4261-4275 落在主计划 §7.3 分配给 P0-5 的 4261-4280 段内。提示下游 P0-3（原分配 4200-4215）需避让已占用的 4201-4206。

### 任务 7：自动编码规则

**File:** `doc/mes/sql/mes-p0-05-03-autocode-sop.sql`

镜像 `mes-p0-02-03-autocode-tooling.sql` 结构：
- 规则 `MES_SOP_CODE`：`SOP` + `yyyyMMdd` + 3位日循环流水号 → `SOP20260914001`
- 三 part：FIXCHAR('SOP') + NOWDATE(yyyyMMdd) + SERIALNO(3位, cycle_flag=Y, cycle_method=DAY)
- `rule_id`/`part_id` 动态 MAX+1 + `WHERE NOT EXISTS` 守护
- 目标库 ry-vue（`AutoCodeUtil` 为 `@DS("master")`）；文件首行 `SET NAMES utf8mb4;` + `USE \`ry-vue\`;`

### 任务 8：字典种子

**File:** `doc/mes/sql/mes-p0-05-04-sop-dicts.sql`

镜像 `mes-p0-02-04-tooling-dicts.sql` 结构，4 个字典：

| 字典 | 值域 | list_class |
|------|------|-----------|
| `mes_sop_status` | DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED | info/warning/success/default |
| `mes_sop_type` | PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN | primary/success/warning/info/info |
| `mes_sop_file_type` | PDF/IMAGE/VIDEO/WORD | - |
| `sop_version_status` | PENDING/CURRENT/HISTORICAL | warning/success/info |

目标库 ry-vue；`SET NAMES utf8mb4;` + `USE \`ry-vue\`;`；动态 MAX+1 主键 + `WHERE NOT EXISTS`。

---

## 五、端到端链路

```
SOP 创建与发布:
  新建 SOP(SOP-20260914-001/装配作业指导/PRODUCTION/上传 PDF v1.0)
    -> sop_document.status=DRAFT, current_version=null, file_oss_id=null
    -> sop_version v1.0 PENDING (file_oss_id=xxx, change_log="初版")

提交审批:
  submitForApproval(sopId, selectFlowId)
    -> status DRAFT -> IN_REVIEW
    -> (selectFlowId 非空) startProcessByDataId -> Flowable 流转
    -> (或管理员兜底) approve(sopId, "通过")
       -> doPublish: v1.0 PENDING->CURRENT, sop_document PUBLISHED
       -> current_version="1.0", file_oss_id=v1.0 的 ossId

版本修订:
  PUBLISHED 状态新增版本 v1.1 (上传新 PDF)
    -> sop_document.status PUBLISHED -> DRAFT, v1.1 PENDING
    -> 提交审批 -> 通过 -> doPublish
    -> v1.0 CURRENT->HISTORICAL, v1.1 PENDING->CURRENT
    -> current_version="1.1", file_oss_id=v1.1 的 ossId

工序绑定:
  绑定(工序"装配"/processId=1001 <-> SOP-001, version_no=null=最新, is_default=1)
    -> 同工序其他绑定 is_default 置 0 (CAS 排他)

工位推送 (F5-4):
  工单开工预加载: /by-material/{materialId}
    -> 物料 -> ERP getCurrentRouting(materialId) -> getRoutingSteps(routingId)
    -> 各步骤 processId -> getSopForProcess -> Map<processId, List<SopVo>>
    -> 工位终端一次性获取全部工序 SOP（减少逐站查询）
  工单进站 (P0-3 startStep) -> pushSopToWorkstation(stepRecordId)
    -> ProFlowCardStep.processId (MES 库内冗余) -> getSopForProcess -> 默认 SOP
    -> SseMessageUtils.publishMessage 推送到工位用户
  工位终端查看器: viewer?stepRecordId=xxx
    -> /by-step/xxx (经 ProFlowCardStep.processId) 拉取 SOP -> 渲染 PDF/图片/视频
    -> /view-log 登记查阅 (viewer/time/wo/process/IP/UA, signature_id=null)

查阅记录 (F5-5):
  操作员打开查看器 -> recordView(sopId, versionNo, woId, processId, wsId)
    -> sop_view_log 写入, signature_id=null (P0-6 接入后回填)

归档废止:
  archive(sopId, "工艺淘汰") -> PUBLISHED -> ARCHIVED (终态, 工位不再推送)

P0-6 电子签名接入 (后续):
  查阅时 -> ESignatureService.sign(userId, meaning="SOP 查阅确认", event=SOP_VIEW, ref=sopId)
    -> 回填 sop_view_log.signature_id (本周预留字段)
```

---

## 六、DDL 详情

### 6.1 sop_document - SOP 文档主档

```sql
USE `ry-mes`;

CREATE TABLE IF NOT EXISTS sop_document (
    sop_id            BIGINT NOT NULL COMMENT 'SOP ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    sop_code          VARCHAR(64) NOT NULL COMMENT 'SOP编码',
    sop_name          VARCHAR(128) NOT NULL COMMENT 'SOP名称',
    sop_type          VARCHAR(16) NOT NULL COMMENT 'PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN',
    current_version   VARCHAR(32) COMMENT '当前发布版本号',
    status            VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED',
    file_oss_id       BIGINT COMMENT '当前发布文件OSS ID(私有桶存ID)',
    file_type         VARCHAR(16) COMMENT 'PDF/IMAGE/VIDEO/WORD',
    description       VARCHAR(500) COMMENT '说明',
    approval_by       BIGINT COMMENT '审批人ID',
    approval_time     DATETIME COMMENT '审批时间',
    approval_remark   VARCHAR(500) COMMENT '审批意见',
    archive_reason    VARCHAR(500) COMMENT '归档原因',
    remark            VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (sop_id),
    UNIQUE INDEX uk_sop_code (sop_code),
    INDEX idx_status (status),
    INDEX idx_type (sop_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SOP文档主档';
```

### 6.2 sop_version - SOP 版本历史

```sql
CREATE TABLE IF NOT EXISTS sop_version (
    version_id        BIGINT NOT NULL COMMENT '版本ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    sop_id            BIGINT NOT NULL COMMENT 'SOP ID',
    version_no        VARCHAR(32) NOT NULL COMMENT '版本号(如1.0/1.1)',
    file_oss_id       BIGINT NOT NULL COMMENT '该版本文件OSS ID',
    file_type         VARCHAR(16) COMMENT 'PDF/IMAGE/VIDEO/WORD',
    change_log        VARCHAR(500) COMMENT '变更说明',
    published_time    DATETIME COMMENT '发布时间',
    published_by      BIGINT COMMENT '发布人ID',
    status            VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/CURRENT/HISTORICAL',
    remark            VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (version_id),
    INDEX idx_sop_id (sop_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOP版本历史';
```

### 6.3 sop_process_binding - 工序 SOP 绑定

```sql
CREATE TABLE IF NOT EXISTS sop_process_binding (
    binding_id        BIGINT NOT NULL COMMENT '绑定ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    process_id        BIGINT NOT NULL COMMENT '工序ID(ERP bd_process.process_id, 工序主数据PK)',
    process_code      VARCHAR(64) COMMENT '工序编码(冗余)',
    process_name      VARCHAR(128) COMMENT '工序名称(冗余)',
    process_category  VARCHAR(16) COMMENT '工序类别(冗余, PROCESSING/ASSEMBLY/INSPECTION/PACKAGING)',
    sop_id            BIGINT NOT NULL COMMENT 'SOP ID',
    version_no        VARCHAR(32) COMMENT '指定版本号(空=当前发布版)',
    is_default        CHAR(1) DEFAULT '0' COMMENT '是否该工序默认SOP(1/0)',
    sort_order        INT DEFAULT 0 COMMENT '排序',
    remark            VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (binding_id),
    INDEX idx_process (process_id),
    INDEX idx_sop_id (sop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序SOP绑定';
```

### 6.4 sop_view_log - SOP 查阅记录

```sql
CREATE TABLE IF NOT EXISTS sop_view_log (
    log_id            BIGINT NOT NULL COMMENT '查阅记录ID(主键)',
    tenant_id         VARCHAR(20) NOT NULL DEFAULT '000000',
    sop_id            BIGINT NOT NULL COMMENT 'SOP ID',
    version_no        VARCHAR(32) COMMENT '查阅的版本号',
    viewer_id         BIGINT NOT NULL COMMENT '查阅人ID',
    viewer_name       VARCHAR(64) COMMENT '查阅人姓名(冗余)',
    view_time         DATETIME NOT NULL COMMENT '查阅时间',
    wo_id             BIGINT COMMENT '关联工单ID',
    wo_no             VARCHAR(64) COMMENT '工单编号(冗余)',
    process_id        BIGINT COMMENT '关联工序ID',
    process_name      VARCHAR(128) COMMENT '工序名称(冗余)',
    workstation_id    BIGINT COMMENT '工位ID',
    signature_id      BIGINT COMMENT '电子签名记录ID(预留,P0-6接入)',
    ip_address        VARCHAR(64) COMMENT 'IP地址',
    user_agent        VARCHAR(256) COMMENT 'User-Agent',
    PRIMARY KEY (log_id),
    INDEX idx_sop_id (sop_id),
    INDEX idx_viewer (viewer_id),
    INDEX idx_wo_id (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOP查阅记录';
```

---

## 七、菜单 ID 段对齐（主计划 §7.3）

| Phase | 模块 | 分配 | 父菜单 | 状态 |
|-------|------|------|--------|------|
| 4-C | **P0-5 E-SOP** | **4206(M) + 4261-4275(C/F)** | 4206 文档管理（新建） | 本方案 |
| 4-C | P0-4 NCR | 4241-4260 | 4002 质量管理 | 待 P0-4 |
| 4-C | P0-6 电子签名 | 4281-4300 | 系统管理下 | 待 P0-6 |

> M-dir 4206 接续 4205（工装管理），C/F 4261-4275 落在主计划 §7.3 给 P0-5 的 4261-4280 段内（用 15 条，余 5 条余量）。提示 P0-3（原 4200-4215）需避让 4201-4206。

---

## 八、风险与注意事项

| 风险 | 应对 |
|------|------|
| Flowable BPMN 未部署致 `startProcessByDataId` 失败 | 兜底 `approve`/`reject` 端点直接驱动状态机（R11 已预见）；`submitForApproval` 捕获 Flowable 异常降级为直接 IN_REVIEW |
| `afterFlowHandle` 事务传播不当致状态不一致 | `@Transactional(propagation=REQUIRES_NEW)`（对齐 `WmSalesIssueServiceImpl:411`） |
| OSS 私有桶存 URL 致 `Data truncation`/链接过期 | 存 `file_oss_id`，VO 用 `@Translation(OSS_ID_TO_URL)` 解析签名 URL（memory `oss-private-bucket-store-ossid`） |
| 多版本并发发布（同时审批两版本） | `doPublish` 用 `@Lock4j(keys={"#sopId"})` + CAS（`sop_version.status` PENDING→CURRENT） |
| 删除 PUBLISHED SOP 致产线断 SOP | 删除守卫：PUBLISHED 不可删（先 archive）；存在版本/绑定/查阅记录不可删 |
| 工序绑定 is_default 多个并发 | `bindSop` 设 default=1 时 CAS 排他同工序其他绑定置 0 |
| 查阅记录 signature_id 空字段被误判脏数据 | 字段注释标明"预留 P0-6"，VO 不翻译该字段；P0-6 接入后回填 |
| 工位推送 SSE 无订阅用户 | 推送方法捕获无订阅静默降级（查询 API 仍可用）；查看器页支持主动拉取 |
| 大文件（视频 SOP）上传超时 | OSS 分片上传（`OssClient.upload` 支持 InputStream）；前端分块加载；本周不优化，列 v1.1 |
| 自动编码规则未入库致生成失败 | DDL 执行顺序：先 autocode SQL（ry-vue）再联调 |
| 字典/autocode SQL CLI 中文乱码 | 文件首行 `SET NAMES utf8mb4;` + `USE \`ry-vue\`;`（memory `mysql-cli-charset-utf8mb4`） |
| 跨库查 process/workstation | 冗余 `process_code`/`process_name`/`process_category`，前端选择器带入；工位推送走 `ProFlowCardStep` 已冗余的 `processId`（MES 库内），均不跨库查 ERP |
| ERP 工序删除致 MES 绑定孤儿 | SOP 绑定到 `bd_process.process_id`（跨模块 ERP 主数据）；ERP 删工序不通知 MES。`getSopForProcess` 返回的 `process_code`/`process_name` 走冗余字段（绑定时快照），不实时查 ERP，故 ERP 工序被删后 MES 仍可展示历史绑定；`by-material` 预加载时经 `ErpRoutingStepMapper` 查到的 processId 若无绑定则跳过（不报错） |
| `bd_routing_step.description` 与 SOP 功能混淆 | 三者分工明确（§1.5）：description=路线步骤级文本简述，SOP=工序级富媒体，pro_step_guide=指引项(可 SOP_LINK)。本周 SOP 不替换 description，文档与培训说明区分 |
| `ErpRoutingStepMapper` 返回 `Map`（无类型） | `getSopForMaterial` 内部用 Map 取 `process_id`，强转 Long 时防御空值；后续可补 `BdRoutingStepVo` 强类型化（本周不做） |

---

## 九、明确排除项（本周不做）

- **Flowable BPMN 流程定义部署**：本周实现 `WfCallBackServiceI` 接入 + 兜底审批，BPMN 流程文件（.bpmn20.xml）部署与 `wf_custom_form` 配置列为部署阶段任务（可用既有通用审批流或管理员兜底）
- **电子签名强制校验**：`sop_view_log.signature_id` 预留，实际签名接入待 P0-6 `ESignatureService` 落地
- **P0-3 进站"SOP 版本"校验深度集成**：仅提供 `getSopForProcess`/`getSopForStep` 契约方法 + SSE 推送方法，触发时机由 P0-3 `startStep` 接入
- **SOP PDF 在线批注/水印**：v1.1（PDF 水印可由 P0-6 DHR 导出能力复用）
- **SOP 多语言版本**：v1.1
- **SOP 知识库全文检索**：v1.1（可接 ruoyi-ai 向量检索）
- **SOP 与 IoT 数据归档关联**：远景，IoT 采集数据按工单/工序归档时关联 SOP 查阅记录
- **离线 SOP（工位断网缓存）**：v1.1 PDA 端

---

## 十、交付物汇总

| 交付物 | 类型 | 路径 |
|--------|------|------|
| `mes-p0-05-01-sop.sql` | SQL | `doc/mes/sql/mes-p0-05-01-sop.sql` |
| `mes-p0-05-02-menu.sql` | SQL | `doc/mes/sql/mes-p0-05-02-menu.sql` |
| `mes-p0-05-03-autocode-sop.sql` | SQL | `doc/mes/sql/mes-p0-05-03-autocode-sop.sql` |
| `mes-p0-05-04-sop-dicts.sql` | SQL | `doc/mes/sql/mes-p0-05-04-sop-dicts.sql` |
| SopDocument/SopVersion/SopProcessBinding/SopViewLog Entity/BO/VO/Mapper | Java×16 | `ruoyi-mes/.../sop/{domain,domain/bo,domain/vo,mapper}/` |
| 4 Service + 4 实现（SopDocumentServiceImpl 实现 WfCallBackServiceI） | Java×8 | `ruoyi-mes/.../sop/{service,service/impl}/` |
| 4 Controller | Java×4 | `ruoyi-mes/.../sop/controller/` |
| `document.ts`/`version.ts`/`binding.ts`/`view-log.ts` | TS×4 | `plus-ui/src/api/mes/sop/` |
| `document/index.vue`/`binding/index.vue`/`view-log/index.vue`/`viewer/index.vue` | Vue×4 | `plus-ui/src/views/mes/sop/` |
| `p0-5-e2e-test.sh` | Shell | `doc/mes/scripts/p0-5-e2e-test.sh` |

---

## 十一、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-mes` 两库 + Redis 就绪。复用 P0-2 E2E 测试模式（memory `ruoyi-e2e-api-test-pattern`：openssl 复刻 RSA+AES 登录 + redis 读验证码 + Bearer/clientid 头）。

1. **DDL 执行**：ry-mes 建 4 表；ry-vue 执行 autocode 规则 + 4 字典 + 菜单 SQL（4206 + 4261-4275）
2. **建档**：新增 SOP（SOP-20260914-001/装配作业指导/PRODUCTION/上传 PDF），编码自动生成，status=DRAFT，首版本 v1.0 PENDING
3. **提交审批**：`/submit/{sopId}`，DRAFT→IN_REVIEW
4. **兜底通过**：`/approve/{sopId}`，IN_REVIEW→PUBLISHED，v1.0→CURRENT，current_version="1.0"，file_oss_id 回填
5. **版本修订**：`/version` 新增 v1.1，PUBLISHED→DRAFT，v1.1 PENDING；提交→通过→v1.0 HISTORICAL，v1.1 CURRENT
6. **工序绑定**：`/binding` 绑定工序↔SOP（is_default=1），同工序旧默认置 0
7. **按工序查询**：`/by-process/{processId}` 返回默认 SOP + 文件签名 URL
8. **按物料预加载**：`/by-material/{materialId}` 经 ERP 当前路线+步骤返回 `Map<processId, List<SopVo>>`，含已绑定 SOP 的工序
9. **查阅登记**：`/view-log` 登记查阅，记录 viewer/time/IP/UA，signature_id=null
10. **查看器**：`viewer?processId=xxx` 拉取并渲染（PDF/图片/视频）
11. **归档**：`/archive/{sopId}`，PUBLISHED→ARCHIVED
12. **删除守卫**：PUBLISHED 不可删；有版本/绑定/查阅记录不可删
13. **静态**：`mvn -pl ruoyi-modules/ruoyi-mes -am compile` + `eslint`（sop 4 文件）均通过；vue-tsc 仅基线噪声（grep 自查，参考 memory `vue-tsc-baseline-noisy`）

---

## 十二、与主计划的对齐项（建议同步更新）

执行本方案时，建议同步修订 [主计划 v1.0](./2026-07-24-mes-p0-development-plan-v1.0.md)：

1. **§3.5.3 DDL**：`file_url VARCHAR(500)` → `file_oss_id BIGINT`（OSS 私有桶存 ID）；`sop_version.status` 增加 PENDING 态；补 `approval_by/time/remark`/`archive_reason` 字段
2. **§3.5.2 F5-5**：注明电子签名本周仅预留 `signature_id`，强制校验待 P0-6
3. **§3.5.2 F5-7**：注明 Flowable 接入 + 管理员兜底双通路（R11）
4. **§7.3 菜单 ID 段**：P0-5 补 M-dir 4206（接续 4205），C/F 4261-4275
5. **§4.1 架构**：`sop/` 独立子包（非 dc 扩展）
6. **§3.5.2 F5-3 工序绑定**：明确绑定到 ERP `bd_process.process_id`（工序主数据，非 `bd_routing_step`）；补 `process_category` 冗余字段；工序选择器复用 ERP `/erp/bd/process/list`（已存在，无需新建 API）；与 `bd_routing_step.description`（文本作业指导）并存，P0-3 `pro_step_guide` 的 SOP_LINK 对接 SOP

---

**文档生成时间**: 2026-07-26
**编制者**: AI Agent
**审核状态**: 📝 待执行（Phase 4-C Week 34；契约层不阻塞 P0-3，可按需提前启动）
