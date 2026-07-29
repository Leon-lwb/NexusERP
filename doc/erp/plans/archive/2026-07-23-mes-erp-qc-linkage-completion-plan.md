# MES ↔ ERP 质量检测联动补全计划

> 日期：2026-07-23
> 范围：ruoyi-common-mq / ruoyi-mes / ruoyi-erp / plus-ui
> 目标：补全 OQC→销售订单、iqcCheck 生效、IPQC→工单 三条质检联动链路（质量门禁强制执行本次不做）

---

## 一、背景

审查发现 IQC（来料检验）双向 MQ 桥接已完整，但存在三个遗漏点：

| 遗漏点 | 现状 | 影响 |
|--------|------|------|
| OQC→ERP | MES `QcOqc` 有 `soId` 字段但检验完成后不发 MQ，ERP 销售订单无任何 OQC 字段 | 出货检验结果无法回写销售订单，发货不设质量关 |
| iqcCheck | `WmArrivalNoticeLine.iqcCheck` 被硬编码 `"0"`，`registerReceiveApprovedMessage` 无条件发 MQ | 所有收货单都触发 IQC，无法按物料跳过免检物料 |
| IPQC→ERP | MES `QcIpqc` 经 `mesWoId→erpWoId` 可定位 ERP 工单，但检验完成后不发 MQ | ERP `PrdWorkOrder` 无质检状态，`PrdWorkReport` 质检字段成数据孤岛 |

### 关键链路（已验证存在）

- **OQC**：`QcOqc.soId` → `so_sales_order_header.so_id`（+ `materialId` 可定位行）
- **IPQC**：`QcIpqc.mesWoId` → `ProWorkOrder.erpWoId` → `PrdWorkOrder.wo_id`（`ProWorkOrder` 第 29 行 `erpWoId` 字段已存在）
- **iqcCheck**：`WmMaterialReceive.noticeId` → `WmArrivalNotice.notice_id` → `WmArrivalNoticeLine.iqcCheck`

### 设计决策（已与用户确认）

1. 范围：核心 3 项，**不做**质量门禁强制（`BdRoutingStep.qualityRequired` 门禁另议）
2. OQC：在 `so_sales_order_line` 加行级 OQC 字段，按 `soId+materialId` 回写；`fulfill()` 对 `UNQUALIFIED` 行硬阻断
3. iqcCheck：`BdMaterial` 新增 `inspectionRequired`，建到货通知行时按物料标志派生 `iqcCheck`
4. IPQC：回写 `PrdWorkOrder` 聚合质量状态（不动 `PrdWorkReport`，避免多报工单模糊匹配）

---

## 二、总体架构

```
                         ┌─── 阶段1: MQ 公共基础设施（2条新通道）───┐
                         │  OqcCompletedMessage / IpqcCompletedMessage │
                         │  + QueueConstants / RoutingKeyConstants / Initializer │
                         └──────────────────────────────────────────┘

ERP (ruoyi-erp)                                MES (ruoyi-mes)
┌─────────────────────┐                      ┌────────────────────────┐
│ ErpOqcCompletedConsumer│←─MQ(erp.oqc.completed)─│ QcOqcServiceImpl.inspect() │  阶段2 OQC
│ 回写 so_sales_order_line│                      │ + MesMqSendService.sendOqcCompleted() │
│ + fulfill() 硬门禁     │                      └────────────────────────┘
└─────────────────────┘
                                              ┌────────────────────────┐
┌─────────────────────┐                      │ QcIpqcServiceImpl.inspect()│  阶段4 IPQC
│ ErpIpqcCompletedConsumer│←─MQ(erp.ipqc.completed)─│ + 查 ProWorkOrder.erpWoId   │
│ 回写 prd_work_order    │                      │ + sendIpqcCompleted()       │
└─────────────────────┘                      └────────────────────────┘

┌─────────────────────┐                      ┌────────────────────────┐
│ WmMaterialReceiveCrudSvc│──MQ(mes.iqc.create)─→│ IqcAutoCreateConsumer     │  阶段3 iqcCheck
│ registerReceiveApproved │                      │ (既有，无需改)            │
│ + 查 WmArrivalNoticeLine│                      └────────────────────────┘
│   iqcCheck 门控         │
│ PuPurchaseReceivingSvc  │
│ + 按物料 inspectionRequired 派生 iqcCheck │
└─────────────────────┘
```

---

## 三、阶段 1：MQ 公共基础设施

**模块**：`ruoyi-common-mq`

### 1.1 新增消息类（`com.nexus.common.mq.message` 包）

**新建 `OqcCompletedMessage.java`**（仿 `IqcCompletedMessage`）：
```java
public class OqcCompletedMessage extends BaseMessage {
    private Long oqcId;          // OQC检验ID
    private String oqcCode;      // OQC检验编号
    private Long soId;           // ERP销售订单头ID
    private Long materialId;     // 物料ID（用于定位销售订单行）
    private String materialCode; // 物料编码
    private String inspectResult;// QUALIFIED/UNQUALIFIED
    private BigDecimal quantity; // 检验数量
}
```

**新建 `IpqcCompletedMessage.java`**：
```java
public class IpqcCompletedMessage extends BaseMessage {
    private Long ipqcId;         // IPQC检验ID
    private String ipqcNo;       // IPQC检验编号
    private Long mesWoId;        // MES工单ID
    private Long erpWoId;        // ERP工单ID（消费端定位 PrdWorkOrder 的关键）
    private String woNo;         // 工单编号
    private String inspectResult;// QUALIFIED/UNQUALIFIED
    private BigDecimal defectQty; // 不良数量
    private BigDecimal defectRate; // 不良率
    private Long inspectorId;
    private String inspectorName;
}
```

### 1.2 常量（仿现有 IQC 常量）

**`QueueConstants.java`** 增加：
```java
/** MES OQC检验完成队列 (MES->ERP) */
String ERP_OQC_COMPLETED_QUEUE = "erp.oqc.completed.queue";
/** MES IPQC检验完成队列 (MES->ERP) */
String ERP_IPQC_COMPLETED_QUEUE = "erp.ipqc.completed.queue";
```

**`RoutingKeyConstants.java`** 增加：
```java
String ERP_OQC_COMPLETED = "erp.oqc.completed";
String ERP_IPQC_COMPLETED = "erp.ipqc.completed";
```

### 1.3 队列绑定

**`RabbitMqInitializer.declareQueuesAndBindings()`** 增加两行（仿 `ERP_IQC_COMPLETED_QUEUE`）：
```java
declareQueueWithDLX(QueueConstants.ERP_OQC_COMPLETED_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "erp.oqc.completed.#");
declareQueueWithDLX(QueueConstants.ERP_IPQC_COMPLETED_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "erp.ipqc.completed.#");
```

---

## 四、阶段 2：OQC → ERP 销售订单联动

### 2.1 MES 侧（`ruoyi-mes`）

**`MesMqSendService.java`** 新增 `sendOqcCompleted()`（完全仿 `sendIqcCompleted()`，第 85-110 行）：
- `@Async` + `DynamicDataSourceContextHolder.push("master")` + `RoutingKeyConstants.ERP_OQC_COMPLETED`

**`QcOqcServiceImpl.java`** 改造 `inspect()` 方法（仿 `QcIqcServiceImpl.inspect()` 第 146-162 行）：
- 注入 `MesMqSendService`
- CAS 更新成功后，注册 `TransactionSynchronization.afterCommit` 回调
- 回调内：`if (soId == null) { log.info("OQC未关联销售订单,跳过回传"); return; }`，否则取 `TenantHelper.getTenantId()` 调 `sendOqcCompleted()`
- 修改类 Javadoc：删除"OQC纯MES内部检验, 不发MQ回传ERP"，改为"OQC检验完成后通过MQ回传ERP, 更新销售订单行OQC状态"

**`IQcOqcService.java` / `QcOqcController.java`**：同步更新 Javadoc（删除"不回传ERP"表述）

### 2.2 ERP 侧（`ruoyi-erp`）

**Schema** — `so_sales_order_line` 增加列：
```sql
ALTER TABLE so_sales_order_line
  ADD COLUMN oqc_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'OQC检验状态(PENDING/QUALIFIED/UNQUALIFIED)',
  ADD COLUMN oqc_id BIGINT NULL COMMENT 'OQC检验单ID',
  ADD COLUMN oqc_code VARCHAR(64) NULL COMMENT 'OQC检验单编号',
  ADD COLUMN oqc_time DATETIME NULL COMMENT 'OQC检验时间';
```

**Domain** — `SoSalesOrderLine.java` / `SoSalesOrderLineVo.java` / `SoSalesOrderLineBo.java` 增加字段：
```java
private String oqcStatus;     // OQC检验状态
private Long oqcId;           // OQC检验单ID
private String oqcCode;       // OQC检验单编号
private Date oqcTime;         // OQC检验时间
```

**新建消费者 `ErpOqcCompletedConsumer.java`**（`com.nexus.erp.mq.consumer`，完全仿 `ErpIqcCompletedConsumer`）：
- `@DS("erp")` + `@RabbitListener(queues = ERP_OQC_COMPLETED_QUEUE)`
- 租户上下文恢复（仿第 60-68 行：`tenantId = String.format("%06d", ...)` + `TenantHelper.dynamic`）
- `doUpdate()` 逻辑：
  - 按 `soId` 查 `so_sales_order_line` 列表，筛选 `materialId` 匹配且 `lineStatus` 非 `CANCELLED` 的行
  - 回写 `oqcId`/`oqcCode`/`oqcTime`
  - `QUALIFIED` → `oqcStatus = QUALIFIED`；`UNQUALIFIED` → `oqcStatus = UNQUALIFIED`
  - 若无匹配行，`log.warn` 并返回（幂等友好）
  - 边界：不回退已 `FULFILLED` 行的状态（与 IQC 消费者防护一致）

**硬门禁** — `SoSalesOrderHeaderServiceImpl.fulfill()`（第 343 行）：
- 在现有"所有行 `delivered_qty >= ordered_qty`"校验**之前**，增加 OQC 校验：
```java
// OQC硬门禁: 任一行 OQC 不合格则禁止完工发货
List<SoSalesOrderLine> lines = lineMapper.selectList(...soId...);
for (SoSalesOrderLine line : lines) {
    if ("UNQUALIFIED".equals(line.getOqcStatus())) {
        throw new ServiceException("销售订单行[" + line.getLineNo() + "]OQC检验不合格,禁止完工发货");
    }
}
```
- 仅阻断 `UNQUALIFIED`；`PENDING`（未送检）放行（OQC 为可选流程）

---

## 五、阶段 3：iqcCheck 生效

### 3.1 物料新增检验标志

**Schema** — `bd_material` 增加列：
```sql
ALTER TABLE bd_material
  ADD COLUMN inspection_required INT DEFAULT 0 COMMENT '是否需要来料检验(0否/1是)';
```

**Domain** — `BdMaterial.java` / `BdMaterialVo.java` / `BdMaterialBo.java` 增加：
```java
private Integer inspectionRequired; // 是否需要来料检验(0否/1是)
```

**字典** — `ry-vue.sql` 增加字典类型 `bd_inspection_required`（0=否, 1=是），复用现有字典插入模式。

### 3.2 派生 iqcCheck（替代硬编码）

**`PuPurchaseReceivingServiceImpl.java`** 第 130 行改造：
- 注入 `IBdMaterialService`（或 `BdMaterialMapper`）
- 将 `noticeLine.setIqcCheck("0")` 改为按物料派生：
```java
BdMaterial material = materialService.selectById(line.getMaterialId());
String iqcCheck = (material != null && Integer.valueOf(1).equals(material.getInspectionRequired()))
    ? "1" : "0";
noticeLine.setIqcCheck(iqcCheck);
```
- 物料查询失败时降级为 `"0"` 并 `log.warn`（不阻断主流程）

### 3.3 收货审批 MQ 门控

**`WmMaterialReceiveCrudServiceImpl.java`** 改造 `registerReceiveApprovedMessage()`（第 588 行）：
- 注入 `WmArrivalNoticeLineMapper`
- 在构建消息**之前**增加门控：
```java
// 查到货通知行, 任一行 iqcCheck="1" 才触发 IQC 自动创建
List<WmArrivalNoticeLine> noticeLines = arrivalNoticeLineMapper.selectList(
    Wrappers.<WmArrivalNoticeLine>lambdaQuery()
        .eq(WmArrivalNoticeLine::getNoticeId, receive.getNoticeId()));
boolean needIqc = noticeLines.stream()
    .anyMatch(l -> "1".equals(l.getIqcCheck()));
if (!needIqc) {
    log.info("收货单 {} 所有关联到货通知行均免检, 跳过 IQC 自动创建 MQ", receive.getReceiveId());
    return;
}
```
- `noticeId` 为空时：`log.warn` 并跳过（无到货通知的收货单由 MES 端手动建 IQC）

---

## 六、阶段 4：IPQC → ERP 工单联动

### 4.1 MES 侧（`ruoyi-mes`）

**`MesMqSendService.java`** 新增 `sendIpqcCompleted()`（仿 `sendIqcCompleted()`）：
- 参数：`ipqcId, ipqcNo, mesWoId, erpWoId, woNo, inspectResult, defectQty, defectRate, inspectorId, inspectorName, tenantId`
- `@Async` + `push("master")` + `RoutingKeyConstants.ERP_IPQC_COMPLETED`

**`QcIpqcServiceImpl.java`** 改造 `inspect()` 方法：
- 注入 `MesMqSendService` + `IProWorkOrderService`（或 `ProWorkOrderMapper`）
- CAS 更新成功后，注册 `afterCommit` 回调：
  - 按 `mesWoId` 查 `ProWorkOrder`（`selectById`，因 `mesWoId` 是主键），取 `erpWoId` + `woNo`
  - `if (erpWoId == null) { log.info("IPQC关联工单无ERP对应,跳过回传"); return; }`
  - 取 `TenantHelper.getTenantId()` 调 `sendIpqcCompleted()`
- 修改 Javadoc：删除"IPQC纯MES内部检验, 不发MQ回传ERP"，改为"IPQC检验完成后,若关联ERP工单则通过MQ回传工单质检状态"

**`IQcIpqcService.java` / `QcIpqcController.java`**：同步更新 Javadoc

### 4.2 ERP 侧（`ruoyi-erp`）

**Schema** — `prd_work_order` 增加列：
```sql
ALTER TABLE prd_work_order
  ADD COLUMN quality_status VARCHAR(20) NULL COMMENT '质检状态(PENDING/QUALIFIED/UNQUALIFIED/PARTIAL)',
  ADD COLUMN quality_check_id BIGINT NULL COMMENT '质检记录ID(IPQC)',
  ADD COLUMN quality_check_no VARCHAR(64) NULL COMMENT '质检单号(IPQC)',
  ADD COLUMN inspector_id BIGINT NULL COMMENT '检验员ID',
  ADD COLUMN inspector_name VARCHAR(64) NULL COMMENT '检验员姓名',
  ADD COLUMN inspect_time DATETIME NULL COMMENT '检验时间';
```

**Domain** — `PrdWorkOrder.java` / `PrdWorkOrderVo.java` / `PrdWorkOrderBo.java` 增加上述字段。

**新建消费者 `ErpIpqcCompletedConsumer.java`**（仿 `ErpIqcCompletedConsumer`）：
- `@DS("erp")` + `@RabbitListener(queues = ERP_IPQC_COMPLETED_QUEUE)`
- 租户上下文恢复
- `doUpdate()` 逻辑：
  - 按 `erpWoId` 查 `PrdWorkOrder`（`wo_id`），不存在则 `log.warn` 返回
  - **UNQUALIFIED 粘性**：若 `inspectResult = UNQUALIFIED`，直接置 `qualityStatus = UNQUALIFIED`（已 UNQUALIFIED 也不变）
  - 若 `QUALIFIED`：仅当当前 `qualityStatus` 为 null/`PENDING`/`QUALIFIED` 时置 `QUALIFIED`，**不覆盖已存在的 UNQUALIFIED**（防止后续工序合格覆盖前序不合格）
  - 回写 `qualityCheckId`/`qualityCheckNo`/`inspectorId`/`inspectorName`/`inspectTime`
  - 幂等：已 `CLOSED` 工单仍允许回写质检状态（质检状态独立于工单状态机）

---

## 七、阶段 5：前端展示（plus-ui，轻量）

> 目标：让新增状态可见、检验标志可配。遵循 `useCrud` 标准模式。

| 页面 | 改动 |
|------|------|
| 物料档案表单 (`views/erp/bd/material`) | 新增 `inspectionRequired` 开关（字典 `bd_inspection_required`，radio 用 `:value=Number()`，参考 frozenFlag 约定） |
| 销售订单行明细 (`views/erp/so/...`) | 只读展示 `oqcStatus`（字典 `bd_quality_status`，tag 配色） |
| 工单列表/详情 (`views/erp/prd/...` 或 MES 工单) | 只读展示 `qualityStatus`（字典 `bd_quality_status`，tag 配色） |

API 层 `src/api/erp/{bd/material,so,prd}` 对应 VO 字段无需单独接口，跟随现有列表/详情返回。

---

## 八、数据库脚本

### 8.1 增量脚本（新建）

**`script/sql/update/2026-07-23-qc-linkage-schema.sql`**：合并阶段 2/3/4 全部 `ALTER TABLE` + 字典插入。文件头注明用途与依赖顺序。

### 8.2 全量脚本同步

- **`script/sql/ry-erp.sql`**：在 `bd_material` / `so_sales_order_line` / `prd_work_order` 建表语句中补入对应列定义
- **`script/sql/ry-vue.sql`**：补入 `bd_inspection_required` 字典类型与数据

> 注意：遵循 [[charset-utf8mb4-unicode-ci-standard]] 与 [[tenant-id-bigint-schema-trap]]，新增列不涉 tenant_id，字符集随表继承。

---

## 九、风险与回滚

| 风险 | 缓解 |
|------|------|
| OQC 硬门禁误阻断正常发货 | 仅阻断 `UNQUALIFIED`，`PENDING` 放行；异常信息含行号便于定位 |
| iqcCheck 门控导致原本会建 IQC 的收货单不再建 | `inspectionRequired` 默认 `0`，需物料档案显式开启才免检——默认行为与现状一致（现状全建 IQC，改为按标志；若希望保持"全建"可批量 `UPDATE bd_material SET inspection_required=1`） |
| IPQC UNQUALIFIED 粘性导致工单质检状态无法恢复 | 设计如此（质量优先）；恢复需人工介入或新增"重新检验"流程（本次不做） |
| MQ 消费失败 | 复用 `BaseMessageConsumer` 重试 + DLX + 补偿任务兜底（既有机制） |
| 多模块同时改 schema | 增量脚本独立可回滚（`ALTER TABLE ... DROP COLUMN`） |

### 回滚
- 停用 MQ：删除新队列绑定（`RabbitMqInitializer` 还原）即可切断联动，业务库 schema 可保留（字段空值无害）
- 还原 `fulfill()` 门禁：移除 OQC 校验代码块
- 还原 `registerReceiveApprovedMessage`：移除 iqcCheck 门控（恢复无条件发送）

---

## 十、验证清单

### IQC（回归，确认阶段 3 未破坏）
- [ ] 物料 `inspectionRequired=1` → 收货审批 → MES 自动建 IQC
- [ ] 物料 `inspectionRequired=0` → 收货审批 → MES **不**建 IQC（日志可见跳过）
- [ ] IQC 合格 → ERP 收货单 `APPROVED`；不合格 → `REJECTED`

### OQC（阶段 2）
- [ ] MES 创建 OQC（填 `soId`+`materialId`）→ 检验合格 → ERP 销售订单行 `oqcStatus=QUALIFIED`
- [ ] OQC 不合格 → 销售订单行 `UNQUALIFIED` → `fulfill()` 抛异常阻断
- [ ] OQC 未关联 `soId` → 不发 MQ（日志可见跳过）

### IPQC（阶段 4）
- [ ] MES 创建 IPQC（填 `mesWoId`）→ 检验合格 → ERP 工单 `qualityStatus=QUALIFIED`
- [ ] IPQC 不合格 → 工单 `UNQUALIFIED`；后续工序合格 IPQC → 工单**仍为** `UNQUALIFIED`（粘性）
- [ ] IPQC 关联工单无 `erpWoId` → 不发 MQ（日志可见跳过）

### 通用
- [ ] 多租户：非默认租户下三条链路均正确回写（tenantId 传播）
- [ ] 幂等：重复消费不产生脏数据
- [ ] 数据源：ERP 消费者 `@DS("erp")`，MES 发送者 `push("master")`

---

## 十一、文件清单总览

### 新建（8 个）
- `ruoyi-common-mq/.../message/OqcCompletedMessage.java`
- `ruoyi-common-mq/.../message/IpqcCompletedMessage.java`
- `ruoyi-erp/.../mq/consumer/ErpOqcCompletedConsumer.java`
- `ruoyi-erp/.../mq/consumer/ErpIpqcCompletedConsumer.java`
- `script/sql/update/2026-07-23-qc-linkage-schema.sql`
- 前端 3 处展示改动（物料/销售订单/工单）

### 修改（约 18 个）
- MQ 基础：`QueueConstants` / `RoutingKeyConstants` / `RabbitMqInitializer`
- OQC：`MesMqSendService` / `QcOqcServiceImpl` / `IQcOqcService` / `QcOqcController` / `SoSalesOrderLine`(+Vo+Bo) / `SoSalesOrderHeaderServiceImpl`
- iqcCheck：`BdMaterial`(+Vo+Bo) / `PuPurchaseReceivingServiceImpl` / `WmMaterialReceiveCrudServiceImpl`
- IPQC：`QcIpqcServiceImpl` / `IQcIpqcService` / `QcIpqcController` / `PrdWorkOrder`(+Vo+Bo)
- SQL 全量：`ry-erp.sql` / `ry-vue.sql`

### 不改（明确边界）
- `IqcAutoCreateConsumer` / `ErpIqcCompletedConsumer` / `QcIqcServiceImpl`（IQC 链路已完整）
- `PrdWorkReport` 质检字段（IPQC 走工单聚合，不动报工单）
- MES 工序流转质量门禁（`BdRoutingStep.qualityRequired`，本次不做）
