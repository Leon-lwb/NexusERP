# P0-4 NCR 不合格品 + 返工流程 详细实施方案

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **编制日期**: 2026-07-28
> **计划周期**: Week 30-32（2026-08-17 ~ 09-06，P0-3/P0-5 提前完工可提前启动）
> **所属计划**: [2026-07-24-mes-p0-development-plan-v1.0.md](./2026-07-24-mes-p0-development-plan-v1.0.md) §3.4
> **状态**: ✅ 编码完成 + SQL 落库 + 编译/lint 通过（2026-07-28；运行时 E2E 待验证）

---

## 实施结果摘要

| 任务 | 状态 | 交付物 |
|------|------|--------|
| 1. DDL（5 表 + pro_work_order 扩展） | ✅ | 并入 `script/sql/ry-mes.sql`：ncr_order/ncr_defect/ncr_defect_code/ncr_rework_order/ncr_8d_report + pro_work_order 加 source_type |
| 2. Entity/BO/VO/Mapper | ✅ | 5 套（25 文件）+ ProWorkOrder 加 sourceType 字段 |
| 3. Service + 实现 | ✅ | 5 接口 + 5 实现：NCR 状态机/自动建单/隔离/返工派生/8D/缺陷代码 |
| 4. 触发点接入 | ✅ | 三检 inspect UNQUALIFIED + ProFlowCard exit defectQty>0 -> autoCreate |
| 5. Controller | ✅ | 5 REST 控制器，端点对齐 §四 |
| 6. 前端 API + 页面 | ✅ | 5 API + 5 页面，全选择器化 + dict-tag + autocode 预览 |
| 7. 菜单/字典/autocode SQL | ✅ | 菜单 4241-4260 + 7 字典 + 2 autocode 规则 |
| 8. 编译 + lint 验证 | ✅ | mvn compile + eslint 通过 |

---

## 一、Context（背景与目标）

### 1.1 为什么做这一阶段

对标西门子 Opcenter "缺陷跟踪和不合格管理"、鼎捷"不合格品管理+返工管理"，当前 MES 质量域只有**三检（IQC/IPQC/OQC）+ SPC**，缺陷信息以 `defectInfo` JSON 串散落在三检实体里，**缺少独立的不合格品单据生命周期、不良品隔离、返工工单、8D 报告闭环**。P0-3 进站/出站校验已落地（`ProFlowCardServiceImpl.exit()` 出站 `defectQty>0` 无处可去），P0-9 批次管理已预留 `LtLot.quarantineByNcr()` 契约方法（[ILtLotService.java:136](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/ILtLotService.java) 标注"P0-4 落地后由 NCR 创建流调用"）——**NCR 是闭合质量主线的最后一环**。

### 1.2 本阶段要解决的问题

1. **不良无处归集**：三检录不良（`inspectResult=UNQUALIFIED`）只回写 ERP 质检状态，MES 侧无单据跟踪不良处置
2. **出站不良悬空**：`ProFlowCardServiceImpl.exit()` L474 `defectQty>0` 报工不良无后续流程
3. **无隔离机制**：不良品批次未冻结，可能被继续领用消耗（`LtLot.quarantineByNcr()` 契约就绪但无人调用）
4. **返工无规范**：返工靠口头/线下，无返工工单派生、无与原工单关联、无返工流程跟踪
5. **无根因闭环**：缺 8D 报告、缺分层缺陷代码库、缺人机料法环测六维归因
6. **P0-6 无接入点**：电子签名的 `NCR_DISPOSITION` 事件缺真实业务场景

### 1.3 关键边界（已确认）

#### 命名边界（用户指令）

- **NCR 本身是缩写，包名与表前缀统一用 `ncr`**，不重复 `nr_ncr_*`：
  - Java 包：`com.nexus.mes.ncr`（独立子模块，非 qc 子包）
  - 表前缀：`ncr_`（`ncr_order`/`ncr_defect`/`ncr_defect_code`/`ncr_rework_order`/`ncr_8d_report`）
  - 前端：`plus-ui/src/api/mes/ncr/` + `plus-ui/src/views/mes/ncr/`
  - 权限：`mes:ncr:*`，Controller 映射 `/mes/ncr/*`

#### 隔离边界（关键架构决策）

- **不良品隔离只在 MES 侧记录批次状态（`LtLot` -> `QUARANTINED`），不回写 ERP `wm_material_stock.frozenFlag`**。
- 依据：①`MesErpBridgeService` 全部方法只读，MES 从无反写 ERP 库存先例；②ERP `frozenFlag`/`reservedQuantity` 由 ERP 仓储模块内部管理（`WmMaterialReceiveExecutorServiceImpl:448` 入库初始化）；③memory `mes-lot-remediation-and-erp-batch-conflict` 明确"MES 批次与 ERP 库存解耦"是已定架构决策。
- 落地方式：NCR 创建时若关联 `lot_id`，调 `ILtLotService.quarantineByNcr(lotId, ncrCode)` 将批次置 `QUARANTINED`；NCR 关闭且处置非返工/报废时（`ACCEPT_AS_IS`）可解隔离恢复。

#### 返工工单边界

- **返工工单是 MES 内部自建，不走 ERP 下发 MQ**：直接调 `ProWorkOrderServiceImpl.insertByBo()`（[ProWorkOrderServiceImpl.java:195](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImpl.java)），不走 `WorkOrderReleaseConsumer`/`createFromErpMessage`。
- **给 `pro_work_order` 加 `source_type` 字段**（`ERP`/`REWORK`/`MANUAL`，默认 `ERP`）区分返工工单。现有记录默认 `ERP`，返工工单设 `REWORK`，`erpWoId=null`。
- **返工工单复用既有流转卡全链路**：`createFlowCard` -> `releaseCard` -> `entry`/`exit`/`dispatch` -> `completeCard` -> `completeWo`。无需新建返工专用流程。
- **返工完工不通知 ERP**：`completeWo` 内 `sendWorkOrderCompleted` MQ 调用需守卫 `source_type=REWORK` 或 `erpWoId=null` 时跳过（返工是 MES 内部事务，ERP 无对应工单）。

#### 触发边界

- **四个自动触发点 + 一个手工创建**：
  - IQC inspect `UNQUALIFIED` -> `autoCreateFromIqc`（source_type=IQC）
  - IPQC inspect `UNQUALIFIED` -> `autoCreateFromIpqc`（source_type=IPQC）
  - OQC inspect `UNQUALIFIED` -> `autoCreateFromOqc`（source_type=OQC）
  - `ProFlowCardServiceImpl.exit()` `defectQty>0` -> `autoCreateFromExit`（source_type=PROCESS）
  - 手工创建（source_type=CUSTOMER，客户投诉/退货场景）
- **触发方式**：在三检 Service `inspect` 方法 CAS 更新成功后、`afterCommit` 注册前直接调用 `ncrOrderService.autoCreateXxx()`（同事务，建单失败抛异常回滚检验——质量闭环不容许"检验成功但 NCR 丢失"）。出站触发在 `exit()` 的 `completeStep` 之后。
- **幂等**：每个来源单据（iqc_id/ipqc_id/oqc_id/step_record_id）+ `source_type` 唯一，`existsBySource(sourceType, sourceId)` 守卫，已存在则跳过（避免重复建单）。

#### defectInfo JSON 边界（开放问题 #3 决议）

- **共存而非迁移**：三检实体的 `defectInfo` JSON 字段保留（它是检验记录的原始凭证），**不废弃**。NCR 自动建单时**解析 `defectInfo` JSON 成 `ncr_defect` 结构化明细**；JSON 为空或解析失败时建空 NCR，由操作员在 REVIEW 阶段手工补录缺陷明细。
- 理由：①避免历史数据迁移风险；②`defectInfo` 是检验员录入凭证，`ncr_defect` 是 NCR 处置视角的结构化分解，语义不同。

#### P0-6 电子签名边界

- **`ncr_order.disposition_signature_id` 字段预留**（同 P0-5 `sop_view_log.signature_id` 模式）。v1.0 处置决策不强制签名，P0-6 落地后由 `ESignatureService` 接入 `NCR_DISPOSITION` 事件。预留字段避免 P0-6 回头改表。

#### 安灯边界

- NCR 创建时**可选**触发 `callAndon(mesWoId, cardId, stepRecordId, QUALITY, ncrCode)`（`callType=QUALITY`），通过 SSE 推送质量异常看板。`source_type=CUSTOMER`（客户投诉）不触发安灯（无工单上下文）。配置项 `mes.ncr.trigger-andon=true` 可关。

### 1.4 预期结果

- 三检判定不合格 / 出站报工不良时自动生成 NCR 单，状态 OPEN
- 不良品批次自动隔离（`QUARANTINED`），冻结后续领用
- NCR 走 OPEN->REVIEW->DISPOSITION->IN_REWORK->CLOSED 状态机
- 处置 REWORK 派生返工工单（source_type=REWORK），关联原工单，走正常流转卡流程
- 处置 SCRAP 批次置 SCRAPPED；ACCEPT_AS_IS 解隔离
- 8D 报告 D1-D8 八步可录，关联 NCR
- 缺陷代码库分层（类别/子类/代码），六维归因（人机料法环测）
- `mvn compile` + eslint 通过

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| 三检实体均有 `defectInfo`（JSON 串）；IPQC 额外有 `defectQty`/`defectRate` | [QcIqc.java:21-78](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc/domain/QcIqc.java) / [QcIpqc.java:21-93](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc/domain/QcIpqc.java) / [QcOqc.java:21-72](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc/domain/QcOqc.java) |
| 三检 `inspect` 方法签名：`inspect(id, inspectResult, defectInfo, sampleQty, [defectQty,] remark)`，CAS `PENDING/INSPECTING->COMPLETED` | [QcIqcServiceImpl.java:132-191](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc/service/impl/QcIqcServiceImpl.java) / [QcIpqcServiceImpl.java:160-224](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc/service/impl/QcIpqcServiceImpl.java) / [QcOqcServiceImpl.java:121-172](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc/service/impl/QcOqcServiceImpl.java) |
| `inspectResult` 取值 `QUALIFIED/UNQUALIFIED/PENDING`；`status` 取值 `PENDING/INSPECTING/COMPLETED` | [IQcIqcService.java:20-29](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/qc/service/IQcIqcService.java) |
| IPQC `autoCreateForKeyProcess` 由 `ProFlowCardServiceImpl.completeStep` L269 / `checkAndCompleteStep` L401 触发 | [ProFlowCardServiceImpl.java:565-591](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java) |
| 出站 `exit(ProExitBo)` L456-481：`runExitChecks` 通过则 `completeStep`，`defectQty>0` 在 L474 之后无处理 | [ProFlowCardServiceImpl.java:456-481](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java) |
| `ProWorkOrderServiceImpl.insertByBo(ProWorkOrderBo)` 直接自建工单（默认 CREATED），不走 MQ | [ProWorkOrderServiceImpl.java:195-203](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImpl.java) |
| `ProWorkOrder` 无 `source_type`/`defect_qty` 字段；`woStatus`：CREATED/APPROVING/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED | [ProWorkOrder.java:22-99](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProWorkOrder.java) / [IProWorkOrderService.java:21-31](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/IProWorkOrderService.java) |
| `createFlowCard(mesWoId, quantity)` 校验工单 RELEASED/IN_PROGRESS，自动生成流转卡+工序记录 | [ProFlowCardServiceImpl.java:137-186](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java) |
| `ILtLotService.quarantineByNcr(lotId, ncrCode)` 契约已预留，置批次 QUARANTINED | [ILtLotService.java:136](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/ILtLotService.java) |
| `MesErpBridgeService` 用 `DynamicDataSourceContextHolder.push("erp")` 只读跨库，不查 wm_material_stock | [MesErpBridgeServiceImpl.java:42-55](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/bridge/MesErpBridgeServiceImpl.java) |
| `callAndon(mesWoId, cardId, stepRecordId, callType, callReason)`，callType 含 QUALITY/MATERIAL | [IProAndonService.java:67-68](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/IProAndonService.java) |
| lt 模块模式：`@DS("mes") @Service` + CAS 状态机（`.eq(status, currentStatus)` + rows==0 抛异常）+ `@Lock4j` | [LtLotServiceImpl.java:42-46,230-249,491-529](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java) |
| 自动编码：`autoCodeUtil.genSerialCode("RULE_CODE", null)` | [LtLotServiceImpl.java:582-584](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/impl/LtLotServiceImpl.java) |
| 菜单 4241-4260 空闲；4002 质量管理父菜单存在，下有 4090(IQC)/4094(IPQC)/4098(OQC) | [script/sql/ry-vue.sql:13781](../../script/sql/ry-vue.sql) |
| ry-mes.sql 锚点：`ncr_` 表插入在 `lt_wo_consumption` 之后（~L599） | [script/sql/ry-mes.sql:565-599](../../script/sql/ry-mes.sql) |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 | 状态 |
|---|------|--------|--------|----------|------|
| 1 | DDL（5 表 + pro_work_order 扩展） | 高 | 并入 ry-mes.sql | 5 表 CREATE TABLE IF NOT EXISTS + pro_work_order 加 source_type | ✅ |
| 2 | Entity/BO/VO/Mapper | 高 | 25 Java 文件 | 字段对齐 DDL，VO 含冗余名称 + @Translation | ✅ |
| 3 | NcrOrderService（核心状态机+触发+隔离） | 高 | INcrOrderService + Impl | 5 态状态机 + 4 autoCreate + 隔离/解隔离 + 幂等 | ✅ |
| 4 | NcrReworkOrderService（返工派生） | 高 | INcrReworkOrderService + Impl | disposition=REWORK 派生返工工单 + 关联原工单 | ✅ |
| 5 | NcrDefectCode/NcrDefect/Ncr8dReport Service | 高 | 3 接口 + 3 实现 | CRUD + 层级守卫 + 8D 状态机 | ✅ |
| 6 | 触发点接入 | 高 | 改 3 inspect + exit | UNQUALIFIED/defectQty>0 自动建 NCR，幂等 | ✅ |
| 7 | ProWorkOrder source_type + completeWo 守卫 | 高 | 改 ProWorkOrder + ServiceImpl | source_type 字段 + REWORK 跳过 MQ | ✅ |
| 8 | Controller | 高 | 5 REST 控制器 | list/get/add/edit/del + 状态动作端点 | ✅ |
| 9 | 前端 API + 页面 | 高 | 5 API + 5 页面 | 全选择器化 + dict-tag + autocode | ✅ |
| 10 | 菜单/字典/autocode SQL | 中 | 4 SQL 文件 | 菜单 4241-4260 + 7 字典 + 2 autocode | ✅ |
| 11 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 | ✅ |

---

## 四、详细任务分解

### 任务 1：DDL（5 表 + pro_work_order 扩展）

**File:** 并入 `script/sql/ry-mes.sql`（在 `lt_wo_consumption` 之后），DDL 详情见 §六。

5 张 `ncr_` 表 + `pro_work_order` 加 `source_type` 列：

```sql
-- 扩展 pro_work_order：加工单来源（ERP/REWORK/MANUAL）
ALTER TABLE pro_work_order
    ADD COLUMN source_type VARCHAR(16) DEFAULT 'ERP' COMMENT '工单来源(ERP/REWORK/MANUAL)';
```

### 任务 2：Entity/BO/VO/Mapper（25 文件）

**Packages:** `com.nexus.mes.ncr.*`（新建包，镜像 lt/tg 布局）

每张表 5 文件（domain + bo + vo + mapper + 不含 service），共 5 套：

| 表 | Entity | BO | VO | Mapper |
|----|--------|----|----|--------|
| ncr_order | NcrOrder | NcrOrderBo | NcrOrderVo | NcrOrderMapper |
| ncr_defect | NcrDefect | NcrDefectBo | NcrDefectVo | NcrDefectMapper |
| ncr_defect_code | NcrDefectCode | NcrDefectCodeBo | NcrDefectCodeVo | NcrDefectCodeMapper |
| ncr_rework_order | NcrReworkOrder | NcrReworkOrderBo | NcrReworkOrderVo | NcrReworkOrderMapper |
| ncr_8d_report | Ncr8dReport | Ncr8dReportBo | Ncr8dReportVo | Ncr8dReportMapper |

**模式**（对齐 lt，见 §二事实）：
- Entity：`@Data @EqualsAndHashCode(callSuper=true) @TableName("ncr_xxx") extends TenantEntity` + `@TableId` + `@TableLogic delFlag`
- BO：`@AutoMapper(target=NcrXxx.class, reverseConvertGenerate=false) extends BaseEntity` + `@NotNull/@NotBlank` + `Long[] ids`
- VO：`@ExcelIgnoreUnannotated @AutoMapper(target=NcrXxx.class) implements Serializable` + `@ExcelProperty` + `@Translation(USER_ID_TO_NAME)` 翻译人员名
- Mapper：`extends BaseMapperPlus<NcrXxx, NcrXxxVo>`

**ProWorkOrder 扩展**：加 `private String sourceType;` 字段（@TableName 已存在，仅加字段）。

**VO 关键增强**：
- `NcrOrderVo`：加 `materialName`/`woNo`/`lotCode`（冗余查询用）+ `dispositionSignatureId`（P0-6 预留）+ 嵌套 `List<NcrDefectVo> defects`
- `NcrDefectVo`：`defectCodeName`（缺陷代码名，关联翻译）+ `defectCauseLabel`
- `NcrReworkOrderVo`：`originalWoNo`/`reworkWoNo`（冗余）
- `Ncr8dReportVo`：嵌套 `NcrOrderVo` 摘要

### 任务 3：NcrOrderService（核心）

**File:** `ncr/service/INcrOrderService.java` + `ncr/service/impl/NcrOrderServiceImpl.java`

#### 状态机

```
                         review(reviewBy)
        ┌──────────┐ ─────────────────► ┌──────────┐
        │   OPEN   │                     │  REVIEW  │
        └────┬─────┘ ◄──────────────────└────┬─────┘
             │ dispose(disposition)           │ dispose(disposition)
             ▼                               ▼
        ┌──────────────────────────────────────────┐
        │              DISPOSITION                 │
        └──────┬───────────────┬───────────────┬───┘
   REWORK      │ SCRAP         │ ACCEPT_AS_IS  │ RETURN
               ▼               ▼               ▼
        ┌──────────┐    ┌──────────┐    ┌──────────┐
        │IN_REWORK │    │  CLOSED  │    │  CLOSED  │  (RETURN 走 CLOSED)
        └────┬─────┘    └──────────┘    └──────────┘
             │ reworkCompleted
             ▼
        ┌──────────┐
        │  CLOSED  │
        └──────────┘
```

**常量**（接口层，对齐 lt 模式）：
```java
String STATUS_OPEN = "OPEN";
String STATUS_REVIEW = "REVIEW";
String STATUS_DISPOSITION = "DISPOSITION";
String STATUS_IN_REWORK = "IN_REWORK";
String STATUS_CLOSED = "CLOSED";

String SOURCE_IQC = "IQC";
String SOURCE_IPQC = "IPQC";
String SOURCE_OQC = "OQC";
String SOURCE_PROCESS = "PROCESS";
String SOURCE_CUSTOMER = "CUSTOMER";

String DISPOSITION_REWORK = "REWORK";
String DISPOSITION_SCRAP = "SCRAP";
String DISPOSITION_ACCEPT = "ACCEPT_AS_IS";
String DISPOSITION_RETURN = "RETURN";
```

#### 关键方法

```java
// ===== 自动建单（4 个触发点，幂等）=====
/** IQC 判定不合格触发 */
NcrOrderVo autoCreateFromIqc(Long iqcId, Long receiveId, Long materialId, String defectInfo);
/** IPQC 判定不合格触发 */
NcrOrderVo autoCreateFromIpqc(Long ipqcId, Long mesWoId, Long cardId, Long stepRecordId,
                              Long processId, BigDecimal defectQty, String defectInfo);
/** OQC 判定不合格触发 */
NcrOrderVo autoCreateFromOqc(Long oqcId, Long soId, Long materialId, String defectInfo);
/** 出站报工不良触发（defectQty>0）*/
NcrOrderVo autoCreateFromExit(Long cardId, Long stepRecordId, Long mesWoId,
                              BigDecimal defectQty, String defectInfo);

// ===== 状态机 =====
/** 评审 */
Boolean review(Long ncrId, Long reviewBy);
/** 处置决策（REWORK/SCRAP/ACCEPT_AS_IS/RETURN）-> 派生返工/隔离终态/解隔离 */
Boolean dispose(Long ncrId, String disposition, Long dispositionBy, String remark);
/** 返工完工回写（由返工工单 completeWo 回调） */
Boolean reworkCompleted(Long ncrId);

// ===== 隔离 =====
/** 创建时调：批次隔离 */
void isolateLot(Long lotId, String ncrCode);
/** ACCEPT_AS_IS 时调：解隔离恢复 */
void releaseLot(Long lotId);

// ===== 契约（供 P0-6 签名接入预留）=====
/** 处置签名回写（P0-6 落地后由 ESignatureService 调用） */
void bindDispositionSignature(Long ncrId, Long signatureId);
```

**autoCreate 通用流程**（private `createNcr(sourceType, sourceId, woId, materialId, defectQty, defectInfo, lotId, cardId, stepRecordId)`）：
1. `existsBySource(sourceType, sourceId)` 幂等守卫 -> 已存在直接返回
2. `generateNcrCode()`（`autoCodeUtil.genSerialCode("MES_NCR_CODE", null)`）
3. 构建 `NcrOrder`（status=OPEN，defect_qty，source_*，created_time）
4. 解析 `defectInfo` JSON -> `ncr_defect` 明细行（解析失败/为空则跳过，REVIEW 阶段补录）
5. 若 `lotId != null` -> `isolateLot(lotId, ncrCode)`（调 `ltLotService.quarantineByNcr`）
6. 若 `mesWoId != null` 且配置开启 -> `callAndon(mesWoId, cardId, stepRecordId, QUALITY, ncrCode)`
7. insert + return VO

**dispose 流程**（CAS `OPEN/REVIEW -> DISPOSITION`）：
1. CAS 更新 `status: OPEN/REVIEW -> DISPOSITION` + `disposition` + `disposition_by` + `disposition_time`
2. 按 disposition 分支：
   - `REWORK` -> 调 `ncrReworkOrderService.deriveReworkOrder(ncrId, originalWoId, reworkQty, reason)` 派生返工工单 -> 置 NCR `status=IN_REWORK`
   - `SCRAP` -> 若有 lotId 调 `ltLotService.scrap(lotId)` 置批次 SCRAPPED -> 置 NCR `status=CLOSED` + closed_time
   - `ACCEPT_AS_IS` -> 若有 lotId 调 `releaseLot(lotId)` 解隔离 -> 置 NCR `status=CLOSED`
   - `RETURN` -> 置 NCR `status=CLOSED`（退货走 ERP 采购退货流程，MES 仅记录）

**CAS 状态机**：所有转换用 `.eq(NcrOrder::getStatus, currentStatus)` + rows==0 抛"NCR 状态已变更"（对齐 `LtLotServiceImpl.quarantine`）。

### 任务 4：NcrReworkOrderService（返工派生）

**File:** `ncr/service/INcrReworkOrderService.java` + `ncr/service/impl/NcrReworkOrderServiceImpl.java`

```java
/** 处置 REWORK 时派生返工工单 */
NcrReworkOrderVo deriveReworkOrder(Long ncrId, Long originalWoId, BigDecimal reworkQty, String reason);
```

**deriveReworkOrder 流程**：
1. 查原工单 `ProWorkOrder`（materialId/routingId/workshopId/bomId/unitId）
2. 构造 `ProWorkOrderBo`：复制原工单物料/工艺/车间，`plannedQty=reworkQty`，`sourceType=REWORK`，`erpWoId=null`，`woNo` 由 `MES_REWORK_CODE` 生成（或 `原工单号-R`）
3. `proWorkOrderService.insertByBo(bo)` -> 拿到 `reworkWoId`
4. 构建 `NcrReworkOrder`（ncr_id/original_wo_id/rework_wo_id/rework_qty/rework_reason，status=OPEN）
5. insert + return

> 返工工单后续走 `createFlowCard` -> `releaseCard` -> `entry`/`exit` 正常流程，由现场操作员在返工工单页面操作，不在 NCR 模块内编排。返工工单 `completeWo` 时回写 NCR（`reworkCompleted`）：需在 `ProWorkOrderServiceImpl.completeWo` 内识别 `source_type=REWORK` -> 查 `ncr_rework_order.rework_wo_id` -> 调 `ncrOrderService.reworkCompleted(ncrId)`。

### 任务 5：缺陷代码/缺陷明细/8D Service

**NcrDefectCodeServiceImpl**：CRUD + 层级守卫（删除时校验子代码/被 ncr_defect 引用，对齐 P0-2/P0-9 孤儿守卫）。`parent_id` 支持三层（level 1类别/2子类/3代码）。

**NcrDefectServiceImpl**：CRUD（按 ncr_id 查明细）+ `batchInsertForNcr(ncrId, List<NcrDefect>)`（autoCreate 时批量插入解析出的缺陷行）。

**Ncr8dReportServiceImpl**：CRUD + 状态机 `DRAFT -> IN_PROGRESS -> COMPLETED`（CAS）+ `getOrCreateByNcr(ncrId)`（一 NCR 一 8D，懒创建）。

### 任务 6：触发点接入（改 4 处既有代码）

**6.1 三检 inspect 方法**（3 处）：

在 CAS 更新成功后、`afterCommit` 注册前插入（同事务，失败回滚检验）：

```java
// QcIqcServiceImpl.inspect L164 之后：
if (RESULT_UNQUALIFIED.equals(inspectResult)) {
    ncrOrderService.autoCreateFromIqc(iqcId, receiveId, materialId, defectInfo);
}
```
IPQC/OQC 同理（传各自上下文：mesWoId/cardId/stepRecordId/processId/defectQty / soId）。

> 注入 `INcrOrderService`，避免循环依赖（ncr 不反向依赖 qc 的 inspect）。

**6.2 ProFlowCardServiceImpl.exit()** L474 之后：

```java
// completeStep 之后，exitCheckResult=PASS 写入前：
if (bo.getDefectQty() != null && bo.getDefectQty().compareTo(BigDecimal.ZERO) > 0) {
    ncrOrderService.autoCreateFromExit(bo.getCardId(), bo.getStepRecordId(),
        currentStep.getMesWoId(), bo.getDefectQty(), null);
}
```

### 任务 7：ProWorkOrder source_type + completeWo 守卫

**7.1** `ProWorkOrder` 加 `sourceType` 字段（任务 2 已含）。

**7.2** `ProWorkOrderServiceImpl.completeWo`（~L697）守卫：
```java
// 仅 ERP 来源工单通知 ERP；返工工单(REWORK)是 MES 内部事务
if (!"REWORK".equals(wo.getSourceType()) && wo.getErpWoId() != null) {
    mesMqSendService.sendWorkOrderCompleted(...);
}
// 返工工单完工 -> 回写 NCR
if ("REWORK".equals(wo.getSourceType())) {
    ncrReworkOrderService.notifyReworkCompleted(wo.getMesWoId());
}
```

### 任务 8：Controller（5 个）

| 端点 | 权限 | 说明 |
|------|------|------|
| GET /mes/ncr/order/list | mes:ncr:order:list | NCR 列表 |
| GET /mes/ncr/order/{ncrId} | mes:ncr:order:query | NCR 详情（含 defects） |
| POST /mes/ncr/order | mes:ncr:order:add | 手工建单（CUSTOMER） |
| PUT /mes/ncr/order | mes:ncr:order:edit | 编辑 |
| DELETE /mes/ncr/order/{ids} | mes:ncr:order:remove | 删除（孤儿守卫：CLOSED 才可删） |
| PUT /mes/ncr/order/review/{ncrId} | mes:ncr:order:review | 评审 |
| PUT /mes/ncr/order/dispose/{ncrId} | mes:ncr:order:dispose | 处置决策 |
| GET /mes/ncr/defect-code/list | mes:ncr:defectcode:list | 缺陷代码库（树形） |
| POST/PUT/DELETE /mes/ncr/defect-code | mes:ncr:defectcode:* | CRUD |
| GET /mes/ncr/defect/list | mes:ncr:defect:list | 缺陷明细（按 ncrId） |
| POST /mes/ncr/defect | mes:ncr:defect:add | 补录缺陷明细 |
| GET /mes/ncr/rework/list | mes:ncr:rework:list | 返工工单列表 |
| GET /mes/ncr/rework/{reworkId} | mes:ncr:rework:query | 返工工单详情 |
| GET /mes/ncr/8d/{ncrId} | mes:ncr:8d:query | 8D 报告（按 ncrId） |
| PUT /mes/ncr/8d | mes:ncr:8d:edit | 8D 录入/更新 |
| PUT /mes/ncr/8d/complete/{reportId} | mes:ncr:8d:complete | 8D 完成 |

### 任务 9：前端 5 API + 5 页面

- `api/mes/ncr/order.ts` / `defect-code.ts` / `defect.ts` / `rework.ts` / `8d-report.ts`
- `views/mes/ncr/order/index.vue` - NCR 列表 + 详情抽屉（含缺陷明细 tab + 返工工单 tab + 8D tab）+ 评审/处置对话框
- `views/mes/ncr/defect-code/index.vue` - 缺陷代码库（树形 el-tree-table）
- `views/mes/ncr/defect/index.vue` - 缺陷明细（通常内嵌 NCR 详情，独立页用于补录）
- `views/mes/ncr/rework/index.vue` - 返工工单列表（跳转到工单详情走流转卡）
- `views/mes/ncr/8d-report/index.vue` - 8D 报告录入（D1-D8 表单）

**全选择器化**（无裸 ID 输入，对齐 P0-9 约定）：
- 物料：`MaterialSelectDialog`
- 工单：`listWorkOrder`（mes/pro/workorder）
- 批次：`listLot`（mes/lt/lot）
- 缺陷代码：自建树选择器（listDefectCode）
- 人员：`UserSelect`
- NCR 编码：`previewCode('MES_NCR_CODE')`

**dict-tag**（无硬编码状态 map）：
- `useDict('mes_ncr_status')` / `mes_ncr_source_type` / `mes_ncr_disposition` / `mes_defect_cause` / `mes_rework_status` / `mes_8d_status`

### 任务 10：菜单/字典/autocode SQL

**File:** `doc/mes/sql/` 下 4 文件（落库 + 并入 ry-mes.sql/ry-vue.sql 规范库）

- `mes-p0-04-01-ncr.sql`（ry-mes）：5 表 DDL + pro_work_order ALTER
- `mes-p0-04-02-menu.sql`（ry-vue）：菜单 4241-4260 + sys_role_menu
- `mes-p0-04-03-autocode-ncr.sql`（ry-vue）：`MES_NCR_CODE` + `MES_REWORK_CODE` 规则
- `mes-p0-04-04-ncr-dicts.sql`（ry-vue）：7 字典

菜单 ID 段（父 4002 质量管理，详见 §七）：

| ID | 类型 | 名称 | perms/path |
|----|------|------|-----------|
| 4241 | M | NCR管理 | path=ncr（父 4002） |
| 4242 | C | NCR不合格品单 | mes:ncr:order:list, mes/ncr/order/index |
| 4243 | C | 缺陷代码库 | mes:ncr:defectcode:list, mes/ncr/defect-code/index |
| 4244 | C | 返工工单 | mes:ncr:rework:list, mes/ncr/rework/index |
| 4245 | C | 8D报告 | mes:ncr:8d:list, mes/ncr/8d-report/index |
| 4246-4260 | F | 功能按钮 | order/defectcode/defect/rework/8d 的 add/edit/remove + review/dispose/complete |

字典（7 个，动态 dict_id + WHERE NOT EXISTS 守护，对齐 P0-9 模式）：

| 字典 | 值域 | list_class |
|------|------|-----------|
| mes_ncr_status | OPEN/REVIEW/DISPOSITION/IN_REWORK/CLOSED | info/primary/warning/primary/success |
| mes_ncr_source_type | IQC/IPQC/OQC/PROCESS/CUSTOMER | - |
| mes_ncr_disposition | REWORK/SCRAP/ACCEPT_AS_IS/RETURN | warning/danger/info/primary |
| mes_defect_cause | MAN/MACHINE/MATERIAL/METHOD/ENVIRONMENT/MEASUREMENT | - |
| mes_rework_status | OPEN/IN_PROGRESS/COMPLETED | info/primary/success |
| mes_8d_status | DRAFT/IN_PROGRESS/COMPLETED | info/primary/success |
| mes_wo_source_type | ERP/REWORK/MANUAL | - |

autocode 规则（2 个，对齐 `MES_LOT_CODE` 模式）：
- `MES_NCR_CODE`：`NCR` + `yyyyMMdd` + 3位日循环 -> `NCR20260817001`
- `MES_REWORK_CODE`：`RW` + `yyyyMMdd` + 3位日循环 -> `RW20260817001`

### 任务 11：编译 + lint 验证

- `mvn -pl ruoyi-modules/ruoyi-mes -am compile` BUILD SUCCESS
- `eslint`（ncr 5 页面 + 5 API）0 错误
- vue-tsc 仅基线噪声（grep 自家路径，参考 memory `vue-tsc-baseline-noisy`）

---

## 五、端到端链路

```
场景A: IQC 来料不合格
  ERP 收货 -> MES IQC 录检 inspect(UNQUALIFIED, defectInfo="[{code:SCRATCH,qty:5}]")
    -> ncrOrderService.autoCreateFromIqc(iqcId, receiveId, materialId, defectInfo)
      -> 建 NCR(NCR20260817001, source=IQC, defect_qty=5, status=OPEN)
      -> 解析 defectInfo -> ncr_defect(缺陷代码=SCRATCH, qty=5)
      -> 若有 lotId -> quarantineByNcr(lotId) 批次 QUARANTINED
      -> callAndon(QUALITY) 推看板
  质量工程师评审 -> review(ncrId) -> status=REVIEW
  处置 -> dispose(ncrId, REWORK) -> status=IN_REWORK
    -> deriveReworkOrder -> 返工工单(RW..., source=REWORK, plannedQty=5)
  返工工单走流转卡: createFlowCard -> releaseCard -> entry -> exit -> completeCard -> completeWo
    -> completeWo 识别 REWORK -> ncrOrderService.reworkCompleted(ncrId) -> status=CLOSED

场景B: 出站报工不良
  操作员 exit(cardId, stepRecordId, goodQty=95, defectQty=5)
    -> completeStep -> defectQty>0 -> autoCreateFromExit
      -> 建 NCR(source=PROCESS, defect_qty=5, wo_id, card_id, step_record_id)
  后续同上评审->处置

场景C: 处置报废
  dispose(ncrId, SCRAP) -> lotId.scrap() 批次 SCRAPPED -> status=CLOSED

场景D: 处置让步接收
  dispose(ncrId, ACCEPT_AS_IS) -> releaseLot(lotId) 解隔离 -> status=CLOSED

场景E: 8D 报告
  NCR 详情 -> 8D tab -> getOrCreateByNcr -> 录入 D1-D8 -> complete -> status=COMPLETED
```

---

## 六、DDL 详情

### 6.1 ncr_order - NCR 不合格品单

```sql
USE `ry-mes`;

CREATE TABLE IF NOT EXISTS ncr_order (
    ncr_id              BIGINT NOT NULL COMMENT 'NCR ID(主键)',
    tenant_id           VARCHAR(20) NOT NULL DEFAULT '000000',
    ncr_code            VARCHAR(64) NOT NULL COMMENT 'NCR编号',
    source_type         VARCHAR(16) NOT NULL COMMENT '来源类型(IQC/IPQC/OQC/PROCESS/CUSTOMER)',
    source_id           BIGINT COMMENT '来源单据ID(iqc_id/ipqc_id/oqc_id/step_record_id)',
    wo_id               BIGINT COMMENT '关联工单ID',
    wo_no               VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id             BIGINT COMMENT '流转卡ID(PROCESS来源)',
    step_record_id      BIGINT COMMENT '工序记录ID(PROCESS来源)',
    process_id          BIGINT COMMENT '工序ID(IPQC/PROCESS来源)',
    process_name        VARCHAR(128) COMMENT '工序名称(冗余)',
    material_id         BIGINT COMMENT '物料ID',
    material_code       VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name       VARCHAR(128) COMMENT '物料名称(冗余)',
    lot_id              BIGINT COMMENT '关联批次ID(隔离用)',
    lot_code            VARCHAR(64) COMMENT '批次编码(冗余)',
    defect_qty          DECIMAL(14,4) NOT NULL COMMENT '不良数量',
    disposition         VARCHAR(16) COMMENT '处置(REWORK/SCRAP/ACCEPT_AS_IS/RETURN)',
    status              VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/REVIEW/DISPOSITION/IN_REWORK/CLOSED',
    review_by           BIGINT COMMENT '评审人',
    review_time         DATETIME COMMENT '评审时间',
    disposition_by      BIGINT COMMENT '处置人',
    disposition_time    DATETIME COMMENT '处置时间',
    disposition_signature_id BIGINT COMMENT '处置签名ID(P0-6预留)',
    created_time        DATETIME NOT NULL COMMENT '创建时间',
    closed_time         DATETIME COMMENT '关闭时间',
    remark              VARCHAR(500) COMMENT '备注',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (ncr_id),
    UNIQUE INDEX uk_ncr_code (ncr_code),
    UNIQUE INDEX uk_source (source_type, source_id),
    INDEX idx_wo_id (wo_id),
    INDEX idx_status (status),
    INDEX idx_lot_id (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NCR不合格品单';
```

> `uk_source (source_type, source_id)`：DB 层幂等兜底（同来源不重复建单）。CUSTOMER 来源 source_id 可为空（手工建单不挂来源）。

### 6.2 ncr_defect - NCR 缺陷明细

```sql
CREATE TABLE IF NOT EXISTS ncr_defect (
    defect_id           BIGINT NOT NULL COMMENT '缺陷明细ID(主键)',
    tenant_id           VARCHAR(20) NOT NULL DEFAULT '000000',
    ncr_id              BIGINT NOT NULL COMMENT 'NCR ID',
    defect_code_id      BIGINT NOT NULL COMMENT '缺陷代码ID(ncr_defect_code)',
    defect_code         VARCHAR(32) COMMENT '缺陷代码(冗余)',
    defect_name         VARCHAR(128) COMMENT '缺陷名称(冗余)',
    defect_qty          DECIMAL(14,4) NOT NULL COMMENT '缺陷数量',
    defect_cause        VARCHAR(32) COMMENT '缺陷原因(MAN/MACHINE/MATERIAL/METHOD/ENVIRONMENT/MEASUREMENT)',
    root_cause_desc     VARCHAR(500) COMMENT '根因描述',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (defect_id),
    INDEX idx_ncr_id (ncr_id),
    INDEX idx_defect_code_id (defect_code_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NCR缺陷明细';
```

### 6.3 ncr_defect_code - 缺陷代码库

```sql
CREATE TABLE IF NOT EXISTS ncr_defect_code (
    code_id             BIGINT NOT NULL COMMENT '代码ID(主键)',
    tenant_id           VARCHAR(20) NOT NULL DEFAULT '000000',
    parent_id           BIGINT COMMENT '父代码ID(层级,0=根)',
    code                VARCHAR(32) NOT NULL COMMENT '缺陷代码',
    name                VARCHAR(128) NOT NULL COMMENT '缺陷名称',
    level               INT DEFAULT 1 COMMENT '层级(1类别/2子类/3代码)',
    enabled             CHAR(1) DEFAULT '1' COMMENT '是否启用(1/0)',
    sort_order          INT DEFAULT 0,
    remark              VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (code_id),
    UNIQUE INDEX uk_code (code),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺陷代码库';
```

### 6.4 ncr_rework_order - 返工工单

```sql
CREATE TABLE IF NOT EXISTS ncr_rework_order (
    rework_id           BIGINT NOT NULL COMMENT '返工记录ID(主键)',
    tenant_id           VARCHAR(20) NOT NULL DEFAULT '000000',
    rework_code         VARCHAR(64) NOT NULL COMMENT '返工工单编号',
    ncr_id              BIGINT NOT NULL COMMENT '关联NCR ID',
    original_wo_id      BIGINT NOT NULL COMMENT '原工单ID',
    original_wo_no      VARCHAR(64) COMMENT '原工单编号(冗余)',
    rework_wo_id        BIGINT NOT NULL COMMENT '生成的返工工单ID(pro_work_order.mes_wo_id)',
    rework_wo_no        VARCHAR(64) COMMENT '返工工单编号(冗余)',
    rework_qty          DECIMAL(14,4) NOT NULL COMMENT '返工数量',
    rework_reason       VARCHAR(500) COMMENT '返工原因',
    status              VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/COMPLETED',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (rework_id),
    UNIQUE INDEX uk_rework_code (rework_code),
    INDEX idx_ncr_id (ncr_id),
    INDEX idx_rework_wo_id (rework_wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NCR返工工单';
```

### 6.5 ncr_8d_report - 8D 报告

```sql
CREATE TABLE IF NOT EXISTS ncr_8d_report (
    report_id           BIGINT NOT NULL COMMENT '报告ID(主键)',
    tenant_id           VARCHAR(20) NOT NULL DEFAULT '000000',
    ncr_id              BIGINT NOT NULL COMMENT '关联NCR ID',
    d1_team             TEXT COMMENT 'D1团队',
    d2_problem          TEXT COMMENT 'D2问题描述',
    d3_interim          TEXT COMMENT 'D3临时措施',
    d4_root_cause       TEXT COMMENT 'D4根因分析',
    d5_permanent        TEXT COMMENT 'D5永久措施',
    d6_implement        TEXT COMMENT 'D6措施实施与验证',
    d7_prevent          TEXT COMMENT 'D7预防措施',
    d8_recognize        TEXT COMMENT 'D8团队认可',
    status              VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_PROGRESS/COMPLETED',
    completed_by        BIGINT COMMENT '完成人',
    completed_time      DATETIME COMMENT '完成时间',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (report_id),
    INDEX idx_ncr_id (ncr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='8D报告';
```

---

## 七、菜单 ID 段分配

| ID | 类型 | 名称 | parent_id | path/component | perms |
|----|------|------|-----------|----------------|-------|
| 4241 | M | NCR管理 | 4002 | ncr / - | - |
| 4242 | C | NCR不合格品单 | 4241 | ncr-order / mes/ncr/order/index | mes:ncr:order:list |
| 4243 | C | 缺陷代码库 | 4241 | defect-code / mes/ncr/defect-code/index | mes:ncr:defectcode:list |
| 4244 | C | 返工工单 | 4241 | rework / mes/ncr/rework/index | mes:ncr:rework:list |
| 4245 | C | 8D报告 | 4241 | 8d-report / mes/ncr/8d-report/index | mes:ncr:8d:list |
| 4246-4250 | F | NCR 按钮 | 4242 | - | mes:ncr:order:{query,add,edit,remove,review} |
| 4251-4252 | F | NCR 处置/导出 | 4242 | - | mes:ncr:order:{dispose,export} |
| 4253-4256 | F | 缺陷代码按钮 | 4243 | - | mes:ncr:defectcode:{query,add,edit,remove} |
| 4257-4259 | F | 返工/8D 按钮 | 4244/4245 | - | mes:ncr:rework:{query,add} / mes:ncr:8d:{edit,complete} |
| 4260 | F | 缺陷明细补录 | 4242 | - | mes:ncr:defect:add |

> 4241-4260 共 20 ID，1 M + 4 C + 15 F。4002 质量管理下现有 4090(IQC)/4094(IPQC)/4098(OQC)，NCR 目录 order_num=4 接续。对齐主计划 §7.3 校正映射（P0-4 = 4241-4260）。

---

## 八、风险与注意事项

| 风险 | 等级 | 应对 |
|------|------|------|
| 循环依赖：ncr 依赖 qc/pro，qc/pro 又被 ncr 触发调用 | 高 | ncr 单向依赖 qc/pro Service；qc/pro 改造点仅注入 `INcrOrderService` 调 autoCreate，不反向依赖 ncr 实现包 |
| autoCreate 与 inspect 同事务，建单失败回滚检验 | 中 | 设计如此（质量闭环不容许 NCR 丢失）；若担心可改 `@Transactional(propagation=REQUIRES_NEW)` 独立事务（参考 IPQC autoCreateForKeyProcess 模式） |
| defectInfo JSON 解析失败 | 中 | 解析失败不抛异常，建空 NCR + log.warn，REVIEW 阶段手工补录；JSON 格式由前端录入控制 |
| 返工工单 completeWo 发 MQ 通知 ERP（erpWoId=null 报错） | 高 | completeWo 守卫：source_type=REWORK 或 erpWoId=null 跳过 sendWorkOrderCompleted |
| 返工工单 routingId 复制原工单可能不适用返修工艺 | 中 | v1.0 复制原 routing，操作员可在返工工单编辑页改 routingId；返修专用工艺路线列 v1.1 |
| 隔离后批次仍被既有领料单引用 | 低 | quarantineByNcr 已冻结（ltLotService 守卫 QUARANTINED 不可 consume）；历史已消耗不可逆 |
| 三检 inspect 改造影响既有 E2E（P0-5/P0-9 验证脚本） | 中 | autoCreate 用幂等守卫 + 失败不阻断（REQUIRES_NEW）保证既有检验流程行为不变 |
| 菜单 4241 已被占用 | 低 | 实施前 SELECT 核实（agent 已确认 4241-4260 空闲） |
| ProWorkOrder 加字段致既有 BO/VO 不兼容 | 低 | sourceType 默认 ERP，既有数据无感；BO/VO 加字段可选填 |
| P0-6 签名接入时改表 | 低 | disposition_signature_id 字段 v1.0 预留，P0-6 仅回写 ID |

---

## 九、明确排除项（本周不做）

- **P0-6 电子签名强制校验**：仅预留 `disposition_signature_id` 字段，强制签名待 P0-6 落地接入 `ESignatureService`
- **ERP 库存冻结回写**：隔离只在 MES 批次层（LtLot QUARANTINED），不反写 ERP `wm_material_stock.frozenFlag`（架构决策）
- **返修专用工艺路线**：返工工单复用原工单 routingId，返修 routing 配置列 v1.1
- **NCR 与 ERP 采购退货联动**：disposition=RETURN 仅在 MES 记录 CLOSED，ERP 采购退货走既有 `pu_return` 流程不联动
- **客户投诉（CUSTOMER）独立入口**：v1.0 复用 NCR 手工建单页面，不建独立客户投诉模块
- **NCR 数据迁移**：既有三检 defectInfo JSON 不迁移到 ncr_defect，共存（建单时按需解析）
- **8D 报告 PDF 导出**：v1.0 仅录入与查看，PDF 导出随 P0-6 DHR 导出一并做
- **PDA 端 NCR 录入**：后续 PDA 移动端

---

## 十、交付物汇总

| 交付物 | 类型 | 路径 |
|--------|------|------|
| `mes-p0-04-01-ncr.sql` | SQL(ry-mes) | `doc/mes/sql/mes-p0-04-01-ncr.sql`（+并入 ry-mes.sql） |
| `mes-p0-04-02-menu.sql` | SQL(ry-vue) | `doc/mes/sql/mes-p0-04-02-menu.sql` |
| `mes-p0-04-03-autocode-ncr.sql` | SQL(ry-vue) | `doc/mes/sql/mes-p0-04-03-autocode-ncr.sql` |
| `mes-p0-04-04-ncr-dicts.sql` | SQL(ry-vue) | `doc/mes/sql/mes-p0-04-04-ncr-dicts.sql` |
| NcrOrder/NcrDefect/NcrDefectCode/NcrReworkOrder/Ncr8dReport 各 Entity/BO/VO/Mapper | Java×20 | `ruoyi-mes/.../ncr/{domain,domain/bo,domain/vo,mapper}/` |
| 5 Service + 5 实现 | Java×10 | `ruoyi-mes/.../ncr/{service,service/impl}/` |
| 5 Controller | Java×5 | `ruoyi-mes/.../ncr/controller/` |
| 三检 inspect + ProFlowCard exit + ProWorkOrder completeWo 改造 | Java×5 改 | `ruoyi-mes/.../qc/service/impl/` + `pro/service/impl/` |
| order/defect-code/defect/rework/8d-report API | TS×5 | `plus-ui/src/api/mes/ncr/` |
| order/defect-code/defect/rework/8d-report 页面 | Vue×5 | `plus-ui/src/views/mes/ncr/` |

---

## 十一、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-mes` 两库 + Redis 就绪。

1. **DDL 执行**：ry-mes 建 5 表 + pro_work_order ALTER；ry-vue 执行 autocode + 字典 + 菜单 SQL（4241-4260）
2. **IQC 触发**：IQC 录检 UNQUALIFIED + defectInfo -> 自动建 NCR(NCR...001, source=IQC, defect_qty) + ncr_defect 解析入库 + 批次 QUARANTINED + 安灯推送
3. **出站触发**：流转卡 exit(defectQty=5) -> 自动建 NCR(source=PROCESS, wo_id, card_id, step_record_id)
4. **幂等**：同 IQC 再次 inspect -> existsBySource 命中跳过，不重复建单
5. **评审**：review(ncrId) -> status=REVIEW
6. **处置-返工**：dispose(REWORK) -> 派生返工工单(RW..., source=REWORK, plannedQty=5) + NCR=IN_REWORK；返工工单走 createFlowCard->entry->exit->completeWo -> NCR=CLOSED
7. **处置-报废**：dispose(SCRAP) -> 批次 SCRAPPED + NCR=CLOSED
8. **处置-让步**：dispose(ACCEPT_AS_IS) -> 批次解隔离恢复 + NCR=CLOSED
9. **8D**：NCR 详情 -> 8D 录入 D1-D8 -> complete -> status=COMPLETED
10. **缺陷代码库**：树形 CRUD，删除有子代码/被引用被拒
11. **删除守卫**：非 CLOSED 的 NCR 不可删
12. **返工 MQ 守卫**：返工工单 completeWo 不发 ERP MQ（erpWoId=null/source=REWORK）
13. **静态**：`mvn -pl ruoyi-modules/ruoyi-mes -am compile` + `eslint`（ncr 文件）均通过；vue-tsc 仅基线噪声

---

## 十二、与主计划的对齐项（建议同步更新）

执行本方案时，建议同步修订 [主计划 v1.0](./2026-07-24-mes-p0-development-plan-v1.0.md)：

1. **§3.4.3 DDL**：表前缀 `nr_` -> `ncr_`（用户指令，NCR 是缩写不双写），表名 `nr_ncr_order` -> `ncr_order` 等；`nr_rework_order` 不再"扩展 pro_work_order"而是独立表 + pro_work_order 加 source_type
2. **§3.4 隔离**：明确不回写 ERP frozenFlag（MES 批次层隔离）
3. **§13.4 开放问题 #3**：defectInfo JSON 与 ncr_defect 共存（不迁移）
4. **§7.3 菜单 ID 段**：P0-4 = 4241-4260（已校正，本方案沿用）
5. **§5.2 时间线**：P0-3/P0-5 提前完工，P0-4 可提前启动

---

**文档生成时间**: 2026-07-28
**编制者**: AI Agent
**审核状态**: 待用户批准 -> 进入执行
