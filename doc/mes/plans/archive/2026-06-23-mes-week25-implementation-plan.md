# MES Phase 3 Week 25 开发实施计划 — 质量管理模块（IQC/IPQC/OQC + MES→ERP MQ 回传全闭环）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。

> **实施日期**: 2026-06-23
> **阶段**: MES Phase 3 起点 — 质量管理 + MES→ERP 回传
> **状态**: 待 Claude 自审 → 待用户审查

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

Phase 2（流转卡+派工+安灯+SN）已于 Week 24 全部闭合（见 `memory/mes-phase2-progress.md`）。按主计划《NexusERP_MES文档.md》§10.3，Phase 3 Week 28-29 = 质量管理（IQC/IPQC/OQC + ERP 联动），实际进度领先主计划约 3-4 周，故 **Week 25 = 质量管理模块**。对应主文档 §2.2（qc_iqc/qc_ipqc/qc_oqc DDL）、§8.3（IQC 完成→ERP 回传）、§14.2/§14.4（/mes/qc/* 接口 + MES→ERP 消息契约）、§15.3 Week 28-29 任务表。

### 1.2 本周要解决的问题

1. 来料/过程/出货三类检验缺乏统一载体。需落地 `qc_iqc`/`qc_ipqc`/`qc_oqc` 三表 + 检验状态机（PENDING→INSPECTING→COMPLETED）+ 检验结果（QUALIFIED/UNQUALIFIED/PENDING）+ 不良品信息（JSON `defect_info`）。
2. IQC 检验完成后需 MQ 通知 ERP 更新收货单质检状态（QUALIFIED 允许入库 / UNQUALIFIED 标记 REJECTED），闭合 §8.3 链路。
3. MES 工单完工→ERP 回传链路 **producer 已就绪但存在 wiring bug 且无 ERP 消费端**：`MesMqSendService.sendWorkOrderCompleted` 发往 `DIRECT_EXCHANGE`，而 `erp.workorder.completed.queue` 绑定在 `TOPIC_EXCHANGE`，消息无法路由；且 `ErpWorkOrderCompletedConsumer` 不存在。本周修复 bug + 补 ERP 消费端，闭合 MES→ERP 完工回传。
4. MES→ERP 消息 DTO（`IqcCompletedMessage`/`MesWorkOrderCompletedMessage`）当前位于 `ruoyi-mes`，ERP 消费端无法引用。需迁移至 `ruoyi-common-mq`（遵循 `WorkOrderReleaseMessage` 先例）。

### 1.3 关键边界（已确认）

- **UNQUALIFIED 仅标记状态**：ERP 收货单无专用 `qualityStatus` 字段（只有 `iqcId`/`iqcCode` 引用 + 单据 `status`）。本周 IQC 不合格仅置收货单 `status=REJECTED` + 备注，**不实现不良品入库/退货流程**（列为后续增强）。
- **完工入库复用既有链路**：`ErpWorkOrderCompletedConsumer` 更新 `PrdWorkOrder`（woStatus=COMPLETED + completedQty 累加 + actualEndDate）后，**复用既有 `ProductionCompleteMessage` → `ProductionBackflushConsumer`** 链路触发倒冲领料 + 成品入库，不重写入库逻辑。
- **不跨库复用 ERP 质检表**：MES `qc_*` 为生产检验自包含表，ERP `wm_material_receive.iqcId/iqcCode` 仅作引用回写，双方不双写。
- **租户上下文**：MQ 消费者线程无登录态，ERP 消费端用 `message.getTenantId()` + `String.format("%06d", tenantId)`（0→"000000"）经 `TenantHelper.dynamic(tenantIdStr, ...)` 恢复，镜像 `WorkOrderReleaseConsumer` 模式（非 `PurchaseReceiveWritebackConsumer` 的 `tenantIdStr`，因 `BaseMessage` 仅有 `Long tenantId`）。

### 1.4 预期结果

- 操作员可创建/查询/检验 IQC/IPQC/OQC，状态机正确流转；IQC 检验完成自动 MQ 通知 ERP。
- ERP 侧 `ErpIqcCompletedConsumer` 据检验结果更新收货单状态（QUALIFIED→APPROVED / UNQUALIFIED→REJECTED）。
- MES 工单完工→ERP 回传链路修复并贯通：`ErpWorkOrderCompletedConsumer` 更新 ERP 工单 + 触发既有完工入库链路。
- `mvn compile`（mes + erp + common-mq）与前端 `eslint` 通过。

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| qc_iqc/qc_ipqc/qc_oqc DDL 规范在主文档 §2.2（未执行），原 `tenant_id BIGINT` 需改为 Week22/23 约定 `VARCHAR(20) DEFAULT '000000'` + `del_flag CHAR(1)` | 主文档 §2.2 + `mes-week22-03-dispatch.sql / mes-week23-04-andon.sql` |
| `IqcCompletedMessage` DTO 已存在于 `com.nexus.mes.common.mq.message`（字段 iqcId/receiveId/inspectResult/quantity），无发送方 | `IqcCompletedMessage.java` |
| `MesWorkOrderCompletedMessage` DTO + `MesMqSendService.sendWorkOrderCompleted()` 已存在，已接入 `ProWorkOrderServiceImpl.completeWo()` afterCommit | `MesMqSendService.java`、`ProWorkOrderServiceImpl.java:393-427` |
| **Wiring bug**：`sendWorkOrderCompleted` 发 `DIRECT_EXCHANGE`，而 `RabbitMqInitializer` 将 `ERP_WORKORDER_COMPLETED_QUEUE` 绑定到 `TOPIC_EXCHANGE`（pattern `erp.workorder.completed.#`），消息无法路由 | `MesMqSendService.java:63` + `RabbitMqInitializer.java:56-58` |
| MQ 常量/队列已就绪：`RoutingKeyConstants.ERP_IQC_COMPLETED`/`ERP_WORKORDER_COMPLETED`、`QueueConstants.ERP_IQC_COMPLETED_QUEUE`/`ERP_WORKORDER_COMPLETED_QUEUE`，队列已在 `RabbitMqInitializer` 声明（TOPIC_EXCHANGE） | `ruoyi-common-mq` constant/ + config |
| `BaseMessage` 仅 `Long tenantId`（无 `getTenantIdStr`）；MES 消费端租户恢复用 `String.format("%06d", tenantId)`，0→"000000" | `BaseMessage.java` + `WorkOrderReleaseConsumer.java:42-50` |
| ERP 收货单 = `WmMaterialReceive`（wm 包，非 pu），有 `iqcId`/`iqcCode` 引用字段 + 单据 `status`（DRAFT/APPROVING/APPROVED/SHELVED/FINISHED/REJECTED/CANCELED），无专用 qualityStatus | `WmMaterialReceive.java` + `IWmMaterialReceiveCrudService.java:19-31` |
| ERP 工单 = `PrdWorkOrder`（prd 包），`woStatus`（CREATED/.../COMPLETED/CLOSED）+ `completedQty`/`scrapQty`/`actualEndDate`；`complete()` 已有完工+发 ProductionCompleteMessage 逻辑 | `PrdWorkOrder.java` + `PrdWorkOrderServiceImpl.java:349-381` |
| 既有完工入库链路：`ProductionCompleteMessage` → `ProductionBackflushConsumer`（@DS("erp")）→ `PrdBackflushServiceImpl.backflushOnCompletion` + `IWmStockOperationService.postProductionCompletion` | `ProductionBackflushConsumer.java`、`IWmStockOperationService.java:68` |
| ERP 侧消费者模式：`@Slf4j @RequiredArgsConstructor @Component @DS("erp")` extends `BaseMessageConsumer<T>`，`@RabbitListener(queues=...)`，doProcess 内 `TenantHelper.dynamic(tenantId, ...)` | `ProductionBackflushConsumer.java`、`PurchaseReceiveWritebackConsumer.java` |
| MES 模块包布局：`com/nexus/mes/{pro, common}`，pro/ 下 {domain, domain/bo, domain/vo, mapper, service, service/impl, controller}；**qc/ 包尚不存在** | `ruoyi-modules/ruoyi-mes/.../com/nexus/mes/` |
| MES Entity 约定：`@Data @EqualsAndHashCode(callSuper=true) @TableName` + extends `TenantEntity` + `@TableId` + `@TableLogic delFlag`；Impl `@DS("mes") @Service("xxService")` | `ProSn.java`、`ProSnServiceImpl.java` |
| 菜单结构：4000=MES系统(root)，4001=生产执行，**4002=质量管理(已存在)**，4003=设备管理，4004=数据采集，4005=SPC分析；Week24 用到 4086，**Week25 取 4090-4101** | `mes-week17-10-menu.sql` + week22/23/24.sql |
| `ErpIqcCompletedConsumer`/`ErpWorkOrderCompletedConsumer` 全仓库不存在（仅文档引用） | grep 无命中 |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | DTO 迁移至 common-mq | 高 | 移动 2 个 DTO + 改 MES 引用 | ERP 可引用，MES 编译通过 |
| 2 | qc DDL | 高 | `mes-week25-06-quality.sql` | 三表对齐 Week22/23 约定 |
| 3 | QcIqc/QcIpqc/QcOqc Entity/BO/VO/Mapper | 高 | 12 个 Java 文件 | 字段对齐 DDL |
| 4 | 三个 Service + 实现（CRUD + 检验状态机 + IQC MQ 触发） | 高 | 3 接口 + 3 实现 | inspect 状态流转 + IQC 完成发 MQ |
| 5 | MesMqSendService.sendIqcCompleted + 修复 exchange bug | 高 | 改 MesMqSendService | TOPIC_EXCHANGE + 新 sendIqcCompleted |
| 6 | 三个 Controller | 高 | 3 REST 控制器 | list/get/add/inspect |
| 7 | ErpIqcCompletedConsumer | 高 | ERP 消费者 | 更新收货单状态（QUALIFIED/UNQUALIFIED） |
| 8 | ErpWorkOrderCompletedConsumer | 高 | ERP 消费者 | 更新工单 + 触发完工入库链路 |
| 9 | 前端 3 API + 3 列表页 | 高 | 6 文件 | 列表+搜索+检验弹窗 |
| 10 | 菜单权限 SQL | 中 | `mes-week25-19-menu.sql` | 4090-4101，父菜单 4002 |
| 11 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 |

---

## 四、详细任务分解

### 任务 1：DTO 迁移至 ruoyi-common-mq

**目的**：ERP 消费端需引用 DTO，跨模块依赖 MES 不可行。遵循 `WorkOrderReleaseMessage`（已在 common-mq）先例。

- 将 `com.nexus.mes.common.mq.message.IqcCompletedMessage` → `com.nexus.common.mq.message.IqcCompletedMessage`（移动文件，包名改）。
- 将 `com.nexus.mes.common.mq.message.MesWorkOrderCompletedMessage` → `com.nexus.common.mq.message.MesWorkOrderCompletedMessage`。
- 更新 MES 引用：`MesMqSendService.java` import；`ProWorkOrderServiceImpl` import（若有）。
- MES 侧 `common/mq/message/` 目录清空后删除（保留 `common/mq/MesMqSendService.java` + `common/mq/consumer/`）。
- **DTO 字段补充**：`IqcCompletedMessage` 增加 `private String iqcCode;`（ERP 回写收货单 iqcCode 引用需）。`MesWorkOrderCompletedMessage` 不变。

### 任务 2：qc DDL

**File:** Create `doc/mes/sql/mes-week25-06-quality.sql`

对齐主文档 §2.2 字段，按 Week22/23 约定调整（`tenant_id` BIGINT→VARCHAR(20)、加 `del_flag`、`mes_wo_id` 命名、`status VARCHAR(16)`）。三表结构：

```sql
USE `ry-mes`;

-- qc_iqc: MES来料检验
-- 状态机: PENDING -> INSPECTING -> COMPLETED
-- 检验结果: QUALIFIED / UNQUALIFIED / PENDING
CREATE TABLE qc_iqc (
    iqc_id          BIGINT NOT NULL COMMENT 'IQC ID(主键)',
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    iqc_no          VARCHAR(64) NOT NULL COMMENT 'IQC编号',
    receive_id      BIGINT COMMENT 'ERP收货单ID',
    material_id     BIGINT NOT NULL,
    material_code   VARCHAR(64),
    material_name   VARCHAR(128),
    vendor_id       BIGINT,
    vendor_name     VARCHAR(128),
    quantity        DECIMAL(14,4) NOT NULL COMMENT '检验数量',
    sample_qty      DECIMAL(14,4) COMMENT '抽样数量',
    inspect_result  VARCHAR(16) DEFAULT 'PENDING' COMMENT 'QUALIFIED/UNQUALIFIED/PENDING',
    defect_info     JSON COMMENT '不良品信息(类型/数量/描述)',
    inspector_id    BIGINT,
    inspector_name  VARCHAR(64),
    inspect_time    DATETIME,
    status          VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/INSPECTING/COMPLETED',
    remark          VARCHAR(500),
    create_dept     BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (iqc_id),
    UNIQUE INDEX uk_iqc_no (iqc_no),
    INDEX idx_status (status), INDEX idx_receive_id (receive_id), INDEX idx_material_id (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES来料检验';

-- qc_ipqc: MES过程检验 (同理, 含 mes_wo_id/card_id/step_record_id/process_*)
CREATE TABLE qc_ipqc (
    ipqc_id         BIGINT NOT NULL,
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    ipqc_no         VARCHAR(64) NOT NULL,
    mes_wo_id       BIGINT NOT NULL COMMENT 'MES工单ID',
    wo_no           VARCHAR(64),
    card_id         BIGINT COMMENT '流转卡ID',
    step_record_id  BIGINT COMMENT '流转卡工序记录ID',
    process_id      BIGINT, process_code VARCHAR(64), process_name VARCHAR(128),
    material_id     BIGINT NOT NULL, material_code VARCHAR(64), material_name VARCHAR(128),
    sample_qty      DECIMAL(14,4), defect_qty DECIMAL(14,4) DEFAULT 0,
    defect_rate     DECIMAL(5,2) COMMENT '不良率(%)',
    inspect_result  VARCHAR(16) DEFAULT 'PENDING',
    defect_info     JSON, inspector_id BIGINT, inspector_name VARCHAR(64), inspect_time DATETIME,
    status          VARCHAR(16) DEFAULT 'PENDING',
    remark          VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (ipqc_id),
    UNIQUE INDEX uk_ipqc_no (ipqc_no),
    INDEX idx_mes_wo_id (mes_wo_id), INDEX idx_status (status), INDEX idx_card_id (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES过程检验';

-- qc_oqc: MES出货检验 (含 so_id 销售订单引用)
CREATE TABLE qc_oqc (
    oqc_id          BIGINT NOT NULL,
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    oqc_no          VARCHAR(64) NOT NULL,
    so_id           BIGINT COMMENT 'ERP销售订单ID',
    material_id     BIGINT NOT NULL, material_code VARCHAR(64), material_name VARCHAR(128),
    quantity        DECIMAL(14,4) NOT NULL, sample_qty DECIMAL(14,4),
    inspect_result  VARCHAR(16) DEFAULT 'PENDING',
    defect_info     JSON, inspector_id BIGINT, inspector_name VARCHAR(64), inspect_time DATETIME,
    status          VARCHAR(16) DEFAULT 'PENDING',
    remark          VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (oqc_id),
    UNIQUE INDEX uk_oqc_no (oqc_no),
    INDEX idx_status (status), INDEX idx_so_id (so_id), INDEX idx_material_id (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES出货检验';
```

> 与主文档差异：`tenant_id` BIGINT→VARCHAR(20)；加 `del_flag`/`remark`；`wo_id`→`mes_wo_id`+`wo_no` 冗余；加 `material_code/name` 冗余。均对齐 Week17/22/23 既定约定。

### 任务 3：Entity/BO/VO/Mapper（12 文件）

**Package:** `com.nexus.mes.qc.*`（新建 qc 包，镜像 pro/ 布局）

每张表 4 文件（domain + bo + vo + mapper），共 12 个：

- `qc/domain/QcIqc.java` / `QcIpqc.java` / `QcOqc.java` — `@Data @EqualsAndHashCode(callSuper=true) @TableName("qc_xxx")` extends `TenantEntity`，`@TableId` 主键，`@TableLogic delFlag`。`defect_info` 用 `String` 存 JSON（避免 TypeHandler 复杂度，前端序列化/反序列化）。
- `qc/domain/bo/QcIqcBo.java` 等 — `@AutoMapper(target=QcXxx.class, reverseConvertGenerate=false)` extends `BaseEntity`，校验 `@NotNull materialId`/`@NotBlank xxNo`，查询字段 + `Long[] ids` 批量删。
- `qc/domain/vo/QcIqcVo.java` 等 — `@ExcelIgnoreUnannotated @AutoMapper(target=QcXxx.class)`，Excel 导出注解。
- `qc/mapper/QcIqcMapper.java` 等 — `extends BaseMapperPlus<QcXxx, QcXxxVo>`。

### 任务 4：三个 Service + 实现

**Files:** `qc/service/IQcXxxService.java` + `qc/service/impl/QcXxxServiceImpl.java`（6 文件）

类头：`@Slf4j @RequiredArgsConstructor @DS("mes") @Service("qcXxxService")`。状态常量 `STATUS_PENDING/INSPECTING/COMPLETED` + `RESULT_QUALIFIED/UNQUALIFIED/PENDING`。

**通用方法**：`selectPageList(bo, pageQuery)`、`selectById(id)`、`insertByBo(bo)`（生成编号 `IQC-yyyyMMdd-seq`）、`inspect(inspectBo)`。

**`inspect` 检验状态机**（核心，CAS）：
- 入参：检验ID + `inspectResult`(QUALIFIED/UNQUALIFIED) + `defectInfo`(JSON String) + `sampleQty` + 可选 `inspectorId/Name`（默认 `LoginHelper`）。
- CAS 校验 `status` 必须 PENDING/INSPECTING（已 COMPLETED 抛"已检验"）。
- 置 `inspectResult`/`defectInfo`/`sampleQty`/`inspectorId`/`inspectorName`/`inspectTime=now`/`status=COMPLETED`。
- IPQC 额外：若 `defectQty`/`sampleQty` 有值算 `defectRate = defectQty/sampleQty*100`。

**IQC 专属 — 检验完成触发 MQ 回传**（仅 IQC）：
- `QcIqcServiceImpl.inspect` 完成后，注册 `TransactionSynchronization.afterCommit()` 调 `mesMqSendService.sendIqcCompleted(iqcId, iqcCode, receiveId, inspectResult, quantity, tenantId)`。
- `tenantId = TenantHelper.getTenantId()`。
- IPQC/OQC 不发 MQ（主文档仅 IQC 有 ERP 回传契约）。

**编号生成**（私有方法）：`IQC-/IPQC-/OQC-` + `yyyyMMdd` + `-` + 当日序号（基于当日已有数 +1，简化实现；不加 Lock4j，编号冲突由 `uk_xxx_no` 唯一索引兜底 + catch 重试）。

### 任务 5：MesMqSendService 改造

**File:** 改 `ruoyi-modules/ruoyi-mes/.../common/mq/MesMqSendService.java`

1. **修复 exchange bug**：`sendWorkOrderCompleted` 第 63 行 `ExchangeConstants.DIRECT_EXCHANGE` → `ExchangeConstants.TOPIC_EXCHANGE`（与 `RabbitMqInitializer` 绑定一致）。
2. **新增 `sendIqcCompleted`**（镜像 `sendWorkOrderCompleted`，`@Async`）：
   ```java
   @Async
   public void sendIqcCompleted(Long iqcId, String iqcCode, Long receiveId,
                                String inspectResult, BigDecimal quantity, String tenantId) {
       IqcCompletedMessage msg = new IqcCompletedMessage();
       msg.setIqcId(iqcId); msg.setIqcCode(iqcCode);
       msg.setReceiveId(receiveId); msg.setInspectResult(inspectResult);
       msg.setQuantity(quantity);
       if (tenantId != null) { try { msg.setTenantId(Long.parseLong(tenantId)); } catch (...) {} }
       DynamicDataSourceContextHolder.push("master");
       try {
           messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
               RoutingKeyConstants.ERP_IQC_COMPLETED, msg);
       } finally { DynamicDataSourceContextHolder.poll(); }
   }
   ```
   import 改为 `com.nexus.common.mq.message.IqcCompletedMessage` / `MesWorkOrderCompletedMessage`（迁移后）。

### 任务 6：三个 Controller

**Files:** `qc/controller/QcIqcController.java` / `QcIpqcController.java` / `QcOqcController.java`

镜像 `ProSnController`：`@Validated @RequiredArgsConstructor @RestController @RequestMapping("/mes/qc/{iqc,ipqc,oqc}") extends BaseController`，`@SaCheckPermission("mes:qc:xx:list|query|add|inspect")`，`@Log` 记录检验操作。

| 端点 | 权限 |
|------|------|
| GET /list | mes:qc:xx:list |
| GET /{id} | mes:qc:xx:query |
| POST / (新增) | mes:qc:xx:add |
| PUT /inspect (检验) | mes:qc:xx:inspect |

> 主文档 §14.4 提到 IQC 含 `submit`，本周 `inspect` 即完成检验（COMPLETED），不再单列 submit。

### 任务 7：ErpIqcCompletedConsumer

**File:** Create `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/ErpIqcCompletedConsumer.java`

镜像 `PurchaseReceiveWritebackConsumer` + `WorkOrderReleaseConsumer`：

```java
@Slf4j @RequiredArgsConstructor @Component @DS("erp")
public class ErpIqcCompletedConsumer extends BaseMessageConsumer<IqcCompletedMessage> {
    private final WmMaterialReceiveMapper materialReceiveMapper;

    @Override protected Class<IqcCompletedMessage> getMessageClass() { return IqcCompletedMessage.class; }

    @Override protected void doProcess(IqcCompletedMessage message) {
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doUpdate(message));
    }

    private void doUpdate(IqcCompletedMessage message) {
        WmMaterialReceive receive = materialReceiveMapper.selectById(message.getReceiveId());
        if (receive == null) { log.warn("收货单不存在: receiveId={}", message.getReceiveId()); return; }
        receive.setIqcId(message.getIqcId());
        receive.setIqcCode(message.getIqcCode());
        // 仅标记状态: QUALIFIED -> APPROVED(允许上架入库); UNQUALIFIED -> REJECTED
        if ("QUALIFIED".equals(message.getInspectResult())) {
            // 已 APPROVED/SHELVED/FINISHED 不回退
            if (!"APPROVED".equals(receive.getStatus())
                && !"SHELVED".equals(receive.getStatus())
                && !"FINISHED".equals(receive.getStatus())) {
                receive.setStatus("APPROVED");
            }
        } else if ("UNQUALIFIED".equals(message.getInspectResult())) {
            receive.setStatus("REJECTED");
            receive.setRemark("IQC检验不合格: " + message.getIqcCode());
        }
        materialReceiveMapper.updateById(receive);
        log.info("IQC回写收货单完成: receiveId={}, result={}", message.getReceiveId(), message.getInspectResult());
    }

    @RabbitListener(queues = QueueConstants.ERP_IQC_COMPLETED_QUEUE)
    public void onMessage(IqcCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
```

import：`com.nexus.common.mq.message.IqcCompletedMessage`（迁移后）+ `com.nexus.erp.wm.domain.WmMaterialReceive` + `com.nexus.erp.wm.mapper.WmMaterialReceiveMapper`。

### 任务 8：ErpWorkOrderCompletedConsumer

**File:** Create `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/ErpWorkOrderCompletedConsumer.java`

```java
@Slf4j @RequiredArgsConstructor @Component @DS("erp")
public class ErpWorkOrderCompletedConsumer extends BaseMessageConsumer<MesWorkOrderCompletedMessage> {
    private final PrdWorkOrderMapper prdWorkOrderMapper;
    private final ReliableMessageProducer messageProducer;

    @Override protected Class<MesWorkOrderCompletedMessage> getMessageClass() { return MesWorkOrderCompletedMessage.class; }

    @Override protected void doProcess(MesWorkOrderCompletedMessage message) {
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> doComplete(message));
    }

    private void doComplete(MesWorkOrderCompletedMessage message) {
        PrdWorkOrder wo = prdWorkOrderMapper.selectById(message.getErpWoId());
        if (wo == null) { log.warn("ERP工单不存在: erpWoId={}", message.getErpWoId()); return; }
        if ("COMPLETED".equals(wo.getWoStatus()) || "CLOSED".equals(wo.getWoStatus())) {
            log.info("工单已完工,跳过: woNo={}", wo.getWoNo()); return;
        }
        wo.setWoStatus("COMPLETED");
        wo.setActualEndDate(new Date());
        BigDecimal done = message.getCompletedQty() != null ? message.getCompletedQty() : BigDecimal.ZERO;
        wo.setCompletedQty(wo.getCompletedQty() != null ? wo.getCompletedQty().add(done) : done);
        if (message.getScrappedQty() != null) {
            wo.setScrapQty(wo.getScrapQty() != null ? wo.getScrapQty().add(message.getScrappedQty()) : message.getScrappedQty());
        }
        prdWorkOrderMapper.updateById(wo);
        // 复用既有完工入库链路: 发 ProductionCompleteMessage -> ProductionBackflushConsumer (倒冲+成品入库)
        ProductionCompleteMessage pcm = new ProductionCompleteMessage();
        pcm.setWoId(wo.getWoId());
        pcm.setCompletionQty(done);
        pcm.setMaterialId(wo.getMaterialId()); pcm.setMaterialCode(wo.getMaterialCode()); pcm.setMaterialName(wo.getMaterialName());
        pcm.setWorkshopId(wo.getWorkshopId());
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.PRODUCTION_COMPLETE_BACKFLUSH, pcm);
        log.info("MES完工回写ERP完成,触发倒冲入库: woNo={}", wo.getWoNo());
    }

    @RabbitListener(queues = QueueConstants.ERP_WORKORDER_COMPLETED_QUEUE)
    public void onMessage(MesWorkOrderCompletedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
```

import：`com.nexus.common.mq.message.MesWorkOrderCompletedMessage` + `com.nexus.erp.prd.domain.PrdWorkOrder` + `com.nexus.erp.prd.mapper.PrdWorkOrderMapper` + `com.nexus.erp.mq.message.ProductionCompleteMessage` + `ExchangeConstants`/`RoutingKeyConstants`/`QueueConstants`/`ReliableMessageProducer`。

> **注意**：ERP 侧 `PrdWorkOrderServiceImpl.complete()` 已存在 ERP 本地完工 + 发 ProductionCompleteMessage 的逻辑。MES→ERP 回写是另一条触发路径。两条路径都发 ProductionCompleteMessage，`ProductionBackflushConsumer` 需幂等（运行时验证；若无，consumer 内 catch 唯一约束）。

### 任务 9：前端 3 API + 3 列表页

**Files (plus-ui/src/):**
- `api/mes/qc/iqc.ts` / `ipqc.ts` / `oqc.ts` — 对齐 `andon.ts`/`sn.ts`：VO/Query 接口 + `listXxx/getXxx/inspectXxx/addXxx`。
- `views/mes/qc/iqc/index.vue` / `ipqc/index.vue` / `oqc/index.vue` — 镜像 `andon/index.vue` 结构：搜索栏 + el-table + 检验弹窗（el-dialog：填 inspectResult 单选 + sampleQty + defectInfo 文本域 + 备注）+ 状态/结果 el-tag 颜色编码（QUALIFIED=success/UNQUALIFIED=danger/PENDING=info）。`v-hasPermi` 对应权限。

### 任务 10：菜单权限 SQL

**File:** Create `doc/mes/sql/mes-week25-19-menu.sql`

父菜单 **4002（质量管理，Week17 已建）**。菜单 ID 段 **4090-4101**（Week24 用到 4086，接续）。

```sql
USE `ry-vue`;
-- IQC来料检验 (parent=4002 质量管理)
INSERT INTO sys_menu VALUES
(4090,'IQC来料检验',4002,1,'qc/iqc','mes/qc/iqc/index','',1,0,'C','0','0','mes:qc:iqc:list','check',103,1,NOW(),NULL,NULL,'MES来料检验'),
(4091,'IQC查询',4090,1,'','','',1,0,'F','0','0','mes:qc:iqc:query','#',103,1,NOW(),NULL,NULL,''),
(4092,'IQC新增',4090,2,'','','',1,0,'F','0','0','mes:qc:iqc:add','#',103,1,NOW(),NULL,NULL,''),
(4093,'IQC检验',4090,3,'','','',1,0,'F','0','0','mes:qc:iqc:inspect','#',103,1,NOW(),NULL,NULL,''),
-- IPQC过程检验
(4094,'IPQC过程检验',4002,2,'qc/ipqc','mes/qc/ipqc/index','',1,0,'C','0','0','mes:qc:ipqc:list','check',103,1,NOW(),NULL,NULL,'MES过程检验'),
(4095,'IPQC查询',4094,1,'','','',1,0,'F','0','0','mes:qc:ipqc:query','#',103,1,NOW(),NULL,NULL,''),
(4096,'IPQC新增',4094,2,'','','',1,0,'F','0','0','mes:qc:ipqc:add','#',103,1,NOW(),NULL,NULL,''),
(4097,'IPQC检验',4094,3,'','','',1,0,'F','0','0','mes:qc:ipqc:inspect','#',103,1,NOW(),NULL,NULL,''),
-- OQC出货检验
(4098,'OQC出货检验',4002,3,'qc/oqc','mes/qc/oqc/index','',1,0,'C','0','0','mes:qc:oqc:list','check',103,1,NOW(),NULL,NULL,'MES出货检验'),
(4099,'OQC查询',4098,1,'','','',1,0,'F','0','0','mes:qc:oqc:query','#',103,1,NOW(),NULL,NULL,''),
(4100,'OQC新增',4098,2,'','','',1,0,'F','0','0','mes:qc:oqc:add','#',103,1,NOW(),NULL,NULL,''),
(4101,'OQC检验',4098,3,'','','',1,0,'F','0','0','mes:qc:oqc:inspect','#',103,1,NOW(),NULL,NULL,'');
```

> 验证：`SELECT menu_id,menu_name,perms FROM sys_menu WHERE menu_id BETWEEN 4090 AND 4101;` 执行后为角色勾选 4090-4101。

### 任务 11：编译 + lint 验证

- [ ] `mvn compile -pl ruoyi-common/ruoyi-common-mq,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-erp -am` 通过
- [ ] 前端 `eslint src/views/mes/qc src/api/mes/qc --ext .vue,.ts` 无 error

---

## 五、端到端链路

```
IQC 检验回传链路:
  MES IQC 新增(关联 ERP receiveId) → 检验(QUALIFIED/UNQUALIFIED) → status=COMPLETED
    → afterCommit: MesMqSendService.sendIqcCompleted(TOPIC_EXCHANGE, erp.iqc.completed)
    → ErpIqcCompletedConsumer(ERP, @DS("erp")) → 更新 WmMaterialReceive
        (iqcId/iqcCode 回写; QUALIFIED→APPROVED 允许上架; UNQUALIFIED→REJECTED 标记)

MES 工单完工回传链路(修复 + 闭合):
  MES ProWorkOrderServiceImpl.completeWo() (已存在) → afterCommit: sendWorkOrderCompleted
    → [修复] TOPIC_EXCHANGE, erp.workorder.completed
    → ErpWorkOrderCompletedConsumer(ERP, @DS("erp")) → 更新 PrdWorkOrder(COMPLETED+completedQty+actualEndDate)
    → 发 ProductionCompleteMessage → 既有 ProductionBackflushConsumer → 倒冲领料 + 成品入库

IPQC/OQC: 纯 MES 内检验, 不发 MQ, 状态机 PENDING→INSPECTING→COMPLETED。
```

---

## 六、风险与注意事项

| 风险 | 应对 |
|------|------|
| DTO 迁移破坏 MES 现有引用 | 全仓 grep `IqcCompletedMessage`/`MesWorkOrderCompletedMessage` 改 import；编译验证 |
| exchange bug 修复影响既有行为 | sendWorkOrderCompleted 改 TOPIC_EXCHANGE 后消息才真正能路由到队列；修复是正确方向，无回归 |
| 完工入库双触发（ERP 本地 complete + MES 回写都发 ProductionCompleteMessage） | ErpWorkOrderCompletedConsumer 内工单幂等（已 COMPLETED 跳过）+ 验证 ProductionBackflushConsumer 幂等性；运行时验证项 |
| IQC 回写收货单状态回退 | QUALIFIED 仅在未 APPROVED/SHELVED/FINISHED 时推进，不回退已上架/已完成单据 |
| 租户精度丢失（Long→String） | 用 `String.format("%06d", tenantId)`，0→"000000"，镜像 WorkOrderReleaseConsumer |
| MQ 消费者租户上下文缺失 | 消费者线程无登录态，`TenantHelper.dynamic(tenantId, ...)` 恢复，否则 TenantLineInnerInterceptor 加 tenant_id 条件查不到数据 |
| defect_info JSON 处理 | Entity 用 String 存，前端 JSON.stringify/parse，避免 TypeHandler 复杂度 |
| 编号并发冲突 | `uk_xxx_no` 唯一索引兜底；不引入 Lock4j（检验创建低并发） |
| ErpIqcCompletedConsumer 与既有 PurchaseReceiveWritebackConsumer 共改 WmMaterialReceive | 两者改不同字段（iqcId/Code+status vs 采购回写），无冲突；均 @DS("erp") |

---

## 七、明确排除项（本周不做）

- IQC 不合格的不良品入库/采购退货流程（仅标记 REJECTED，后续增强）
- 设备维度追溯（依赖 eq 模块，后续 Week）
- SPC/数据采集（Phase 3 后续 Week）
- ERP 侧质检页面（本周 ERP 仅消费端，无前端）
- 重写完工入库逻辑（复用既有 ProductionBackflushConsumer 链路）
- IQC/IPQC/OQC 详情独立页（检验用弹窗，无需 hidden 路由）
- el-table → vxe-table 迁移
- 跨库复用 ERP 质检表 / bd_serial_number_rule 规则引擎

---

## 八、交付物汇总

| 交付物 | 类型 | 路径 |
|--------|------|------|
| `IqcCompletedMessage`/`MesWorkOrderCompletedMessage` 迁移 | 改 | `ruoyi-common-mq/.../message/` |
| `mes-week25-06-quality.sql` | SQL | `doc/mes/sql/mes-week25-06-quality.sql` |
| QcIqc/QcIpqc/QcOqc Entity/BO/VO/Mapper | Java×12 | `ruoyi-mes/.../qc/{domain,domain/bo,domain/vo,mapper}/` |
| IQcXxxService + 实现 | Java×6 | `ruoyi-mes/.../qc/{service,service/impl}/` |
| `MesMqSendService.java`（+sendIqcCompleted, 修 exchange） | 改 | `ruoyi-mes/.../common/mq/MesMqSendService.java` |
| QcIqcController/QcIpqcController/QcOqcController | Java×3 | `ruoyi-mes/.../qc/controller/` |
| `ErpIqcCompletedConsumer.java` | Java | `ruoyi-erp/.../mq/consumer/` |
| `ErpWorkOrderCompletedConsumer.java` | Java | `ruoyi-erp/.../mq/consumer/` |
| `iqc.ts`/`ipqc.ts`/`oqc.ts` | TS×3 | `plus-ui/src/api/mes/qc/` |
| `iqc/index.vue`/`ipqc/index.vue`/`oqc/index.vue` | Vue×3 | `plus-ui/src/views/mes/qc/` |
| `mes-week25-19-menu.sql` | SQL | `doc/mes/sql/mes-week25-19-menu.sql` |

---

## 九、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-erp`/`ry-mes`/`ry-iot` 四库 + Redis + RabbitMQ 就绪，并已执行 Week17/20/22/23/24 DDL 与菜单 SQL。

1. **DDL 执行**：`mes-week25-06-quality.sql` 建三表；`mes-week25-19-menu.sql` 注入菜单并为角色勾选 4090-4101。
2. **IQC 检验 + 回传**：MES 新建 IQC（关联一 ERP 收货单 receiveId）→ 检验 QUALIFIED → 列表 status=COMPLETED → RabbitMQ Management Console 见 `erp.iqc.completed.queue` 消息 → ERP 收货单 `iqcId/iqcCode` 回写 + status=APPROVED。再建一 IQC 检验 UNQUALIFIED → 收货单 status=REJECTED + 备注。
3. **MES 工单完工回传**：MES 流转卡全完工触发 `completeWo` → `erp.workorder.completed.queue` 收到消息（验证 bug 修复后消息真正路由）→ ERP 工单 woStatus=COMPLETED + completedQty 累加 + 触发 ProductionCompleteMessage → 倒冲领料 + 成品入库（验证幂等无重复入库）。
4. **IPQC/OQC**：新建 + 检验，状态 PENDING→COMPLETED，defect_rate（IPQC）自动计算；不发 MQ。
5. **幂等**：同一 IQC 检验两次 → 第二次抛"已检验"；同一完工消息重投 → 工单已 COMPLETED 跳过。
6. **静态**：`mvn compile`（common-mq+mes+erp）+ `eslint` 均通过。

---

**文档生成时间**: 2026-06-23
**执行者**: AI Agent
**审核状态**: 待 Claude 自审 → 待用户审查
