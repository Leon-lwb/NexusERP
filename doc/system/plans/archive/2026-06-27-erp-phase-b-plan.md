# NexusERP ERP Phase B（结构优化）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 NexusERP 升级方案 §4.1 ERP Phase B 的四项结构优化：报工自动回写工单补强、采购订单状态机完善、库存预留机制、多级BOM展开优化。

**Architecture:** 四个子项独立并行。报工回写与采购订单聚焦"稳定性补强"（枚举类+状态机+并发防护+校验补全）；库存预留聚焦"新建业务逻辑层"（DDL→Mapper→Service→集成）；BOM 展开聚焦"性能优化"（批量查询消除 N+1）。所有 DDL 变更最小化，复用已有字段和模式。

**Tech Stack:** Spring Boot + MyBatis-Plus + Flowable + Redis + 动态数据源 @DS("erp")

**前置文档:**
- `doc/system/NexusERP_三模块共同升级方案.md` §4.1 Phase B（~18 人天）
- `doc/erp/NexusERP_ERP文档.md` §四 Phase B 路线图

**关键发现（代码探索结论）:**
- 报工 MQ 链路已通（`ErpWorkOrderCompletedConsumer`），但 `complete()` 硬编码 plannedQty，两条回写链路无并发防护
- 采购订单 9 状态为 String 常量，`cancel` 未排除 PARTIALLY_RECEIVED，`close` 校验过松
- `SoSalesOrderLine.reservedQty`/`reservedBatchNo`/`reservedSerials`/`paGroupId` 字段 DB+Entity+Bo+Vo 四层已就位但 0 调用
- `wm_material_stock` 缺 `reserved_quantity` 列；`WmMaterialStockMapper` 仅有 deductStock/addStock
- BOM 展开 `buildTreeNode`/`buildIndentedLines`/`checkCircularRef`/`whereUsed` 四处 N+1，`BdBomMapper`/`BdBomDetailMapper` 完全空白
- `enrichBomVo`（BdBomServiceImpl L103-142）已示范批量加载范式，可推广

---

## 文件结构

### 子项1：报工自动回写工单补强（Task 1-2）

| 操作 | 文件路径 | 责任 |
|---|---|---|
| 新建 | `ruoyi-modules/ruoyi-erp/.../prd/domain/enums/WoStatusEnum.java` | 工单状态枚举 + 合法转换矩阵 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../prd/service/impl/PrdWorkOrderServiceImpl.java` | complete() 使用消息数量 + 原子更新 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../mq/consumer/ErpWorkOrderCompletedConsumer.java` | 原子更新防并发 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../prd/service/impl/PrdWorkReportServiceImpl.java` | 原子更新防并发 |

### 子项2：采购订单状态机完善（Task 3-4）

| 操作 | 文件路径 | 责任 |
|---|---|---|
| 新建 | `ruoyi-modules/ruoyi-erp/.../pu/domain/enums/PurchaseOrderStatusEnum.java` | 采购订单状态枚举 + 合法转换 |
| 新建 | `ruoyi-modules/ruoyi-erp/.../pu/domain/enums/PurchaseOrderLineReceiveStatus.java` | 行级收货状态枚举 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../pu/service/impl/PuPurchaseOrderServiceImpl.java` | cancel/close 校验补全 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../wm/domain/WmMaterialReceiveLine.java` | 新增 purchaseLineId 外键字段 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../pu/service/impl/PuPurchaseReceiveWritebackServiceImpl.java` | 按 lineId 直接匹配 |
| 新建 | `doc/erp/sql/archive/erp-phaseb-po-receive-line.sql` | DDL: wm_material_receive_line 加 purchase_line_id 列 |
| 新建 | `ruoyi-modules/ruoyi-erp/.../pu/controller/PuPurchaseOrderLineController.java` | 收货差异处理端点 |

### 子项3：库存预留机制（Task 5-7）

| 操作 | 文件路径 | 责任 |
|---|---|---|
| 新建 | `doc/erp/sql/archive/erp-phaseb-stock-reservation.sql` | DDL: wm_material_stock 加 reserved_quantity 列 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../wm/domain/WmMaterialStock.java` | 新增 reservedQuantity 字段 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../wm/mapper/WmMaterialStockMapper.java` | reserveStock/releaseReserved 原子方法 |
| 新建 | `ruoyi-modules/ruoyi-erp/.../wm/service/IWmStockReservationService.java` | 预留服务接口 |
| 新建 | `ruoyi-modules/ruoyi-erp/.../wm/service/impl/WmStockReservationServiceImpl.java` | 预留服务实现 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../wm/enums/StockOperationTypeEnum.java` | 添加 RESERVE/UNRESERVE 类型 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../so/service/impl/SoSalesOrderHeaderServiceImpl.java` | confirm() 触发预留 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../prd/service/impl/PrdWorkOrderServiceImpl.java` | release() 可用性检查 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../wm/strategy/impl/SalesOutStrategy.java` | 出库消费预留 |

### 子项4：多级BOM展开优化（Task 8-9）

| 操作 | 文件路径 | 责任 |
|---|---|---|
| 修改 | `ruoyi-modules/ruoyi-erp/.../bd/mapper/BdBomDetailMapper.java` | 批量查询方法 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../bd/mapper/BdBomMapper.java` | 批量查当前 BOM |
| 修改 | `ruoyi-modules/ruoyi-erp/.../bd/service/impl/BdBomServiceImpl.java` | 重构 4 处递归方法消除 N+1 |

---

## Task 1: 工单状态枚举类 WoStatusEnum

**Files:**
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/domain/enums/WoStatusEnum.java`

**背景**：`IPrdWorkOrderService` 用 7 个 String 常量表示工单状态（L22-34），类型不安全，状态转换校验散落在各方法。抽取枚举类集中管理合法转换。

- [ ] **Step 1: 新建 WoStatusEnum 枚举类**

文件 `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/domain/enums/WoStatusEnum.java`：

```java
package com.nexus.erp.prd.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 生产工单状态枚举
 * 集中管理状态值与合法转换矩阵, 替换散落的 String 常量
 */
public enum WoStatusEnum {

    CREATED("CREATED", "已创建"),
    APPROVING("APPROVING", "审批中"),
    APPROVED("APPROVED", "已批准"),
    RELEASED("RELEASED", "已下达"),
    IN_PROGRESS("IN_PROGRESS", "生产中"),
    COMPLETED("COMPLETED", "已完工"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String label;

    WoStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 合法状态转换矩阵
     * key = 当前状态, value = 可转换到的状态集合
     */
    public boolean canTransitionTo(WoStatusEnum target) {
        Set<WoStatusEnum> allowed = switch (this) {
            case CREATED -> EnumSet.of(APPROVING, RELEASED, CLOSED);
            case APPROVING -> EnumSet.of(APPROVED, CREATED);
            case APPROVED -> EnumSet.of(RELEASED, CLOSED);
            case RELEASED -> EnumSet.of(IN_PROGRESS, CLOSED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CLOSED);
            case COMPLETED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(WoStatusEnum.class);
        };
        return allowed.contains(target);
    }

    /**
     * 终态判断(不可再转换)
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }

    public static WoStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (WoStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知工单状态: " + code);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/domain/enums/WoStatusEnum.java
git commit -m "feat(prd): 新建 WoStatusEnum 工单状态枚举类 (PhaseB-子项1)"
```

---

## Task 2: 报工回写 complete() 修复 + 并发防护

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkOrderServiceImpl.java`（complete 方法 L365-410）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/ErpWorkOrderCompletedConsumer.java`（doComplete 方法 L80-117）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkReportServiceImpl.java`（syncWorkOrderQuantity 方法 L133-153）

**背景**：`PrdWorkOrderServiceImpl.complete()` L375 硬编码取 `plannedQty` 作为完工数量，应优先使用 MQ 消息中的 `completedQty`。报工回写（`syncWorkOrderQuantity`）和 MQ 完工回写（`doComplete`）两条链路都直接 `updateById`，存在并发覆盖风险。改用 LambdaUpdateWrapper 原子累加。

- [ ] **Step 1: 修复 PrdWorkOrderServiceImpl.complete() 硬编码**

修改 `complete()` 方法，将硬编码 `plannedQty` 改为参数传入的实际完工数量。同时将状态更新+数量更新合并为一次原子操作。

在 `IPrdWorkOrderService` 中新增带数量参数的 complete 重载：

```java
// IPrdWorkOrderService.java 新增方法签名
void complete(Long woId, BigDecimal completeQty, BigDecimal scrapQty);
```

修改 `PrdWorkOrderServiceImpl.complete()`：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void complete(Long woId, BigDecimal completeQty, BigDecimal scrapQty) {
    PrdWorkOrder wo = workOrderMapper.selectById(woId);
    if (wo == null) {
        throw new ServiceException("工单不存在: " + woId);
    }
    WoStatusEnum currentStatus = WoStatusEnum.fromCode(wo.getWoStatus());
    if (currentStatus == WoStatusEnum.COMPLETED || currentStatus == WoStatusEnum.CLOSED) {
        throw new ServiceException("工单已完工或已关闭, 不可重复完工");
    }
    if (!currentStatus.canTransitionTo(WoStatusEnum.COMPLETED)) {
        throw new ServiceException("工单状态[" + currentStatus.getLabel() + "]不可完工");
    }
    // 原子更新: 状态 + 累加完工数量 + 累加报废数量 + 完工时间
    LambdaUpdateWrapper<PrdWorkOrder> wrapper = Wrappers.<PrdWorkOrder>lambdaUpdate()
        .eq(PrdWorkOrder::getWoId, woId)
        .set(PrdWorkOrder::getWoStatus, WoStatusEnum.COMPLETED.getCode())
        .set(PrdWorkOrder::getActualEndDate, new Date())
        .setSql(completeQty != null, "completed_qty = completed_qty + " + completeQty)
        .setSql(scrapQty != null, "scrap_qty = scrap_qty + " + scrapQty);
    int rows = workOrderMapper.update(null, wrapper);
    if (rows == 0) {
        throw new ServiceException("工单完工更新失败, 可能已被其他操作修改");
    }
    // 触发倒冲入库(复用现有逻辑)
    triggerBackflush(wo, completeQty);
}

// 保留无参重载兼容现有调用(默认取 plannedQty)
@Override
@Transactional(rollbackFor = Exception.class)
public void complete(Long woId) {
    PrdWorkOrder wo = workOrderMapper.selectById(woId);
    if (wo == null) {
        throw new ServiceException("工单不存在: " + woId);
    }
    complete(woId, wo.getPlannedQty(), BigDecimal.ZERO);
}
```

- [ ] **Step 2: 修改 ErpWorkOrderCompletedConsumer 使用原子更新**

修改 `doComplete()` 方法，不再直接 `updateById`，改为调用带数量参数的 `complete()`：

```java
// ErpWorkOrderCompletedConsumer.java doComplete() 方法
// 替换原有的 updateById 逻辑为:
@Override
protected void doComplete(MesWorkOrderCompletedMessage message) {
    Long erpWoId = message.getErpWoId();
    if (erpWoId == null) {
        log.warn("MES工单完工消息缺少 erpWoId, 跳过");
        return;
    }
    PrdWorkOrder wo = workOrderService.selectById(erpWoId);
    if (wo == null) {
        log.warn("ERP工单不存在: {}, 跳过", erpWoId);
        return;
    }
    WoStatusEnum currentStatus = WoStatusEnum.fromCode(wo.getWoStatus());
    if (currentStatus == WoStatusEnum.COMPLETED || currentStatus.isTerminal()) {
        log.info("工单 {} 已完工/已关闭, 跳过", erpWoId);
        return;
    }
    // 使用消息中的实际完工数量, 不再硬编码 plannedQty
    BigDecimal completedQty = message.getCompletedQty() != null ? message.getCompletedQty() : BigDecimal.ZERO;
    BigDecimal scrappedQty = message.getScrappedQty() != null ? message.getScrappedQty() : BigDecimal.ZERO;
    workOrderService.complete(erpWoId, completedQty, scrappedQty);
}
```

- [ ] **Step 3: 修改 PrdWorkReportServiceImpl.syncWorkOrderQuantity 使用原子更新**

```java
// PrdWorkReportServiceImpl.java syncWorkOrderQuantity() 方法
private void syncWorkOrderQuantity(PrdWorkReport report) {
    LambdaUpdateWrapper<PrdWorkOrder> wrapper = Wrappers.<PrdWorkOrder>lambdaUpdate()
        .eq(PrdWorkOrder::getWoId, report.getWoId())
        .setSql(report.getGoodQty() != null, "completed_qty = completed_qty + " + report.getGoodQty())
        .setSql(report.getScrapQty() != null, "scrap_qty = scrap_qty + " + report.getScrapQty());
    int rows = workOrderMapper.update(null, wrapper);
    if (rows == 0) {
        log.warn("报工回写工单数量失败, 工单可能已被修改: woId={}", report.getWoId());
        throw new ServiceException("工单数量更新失败, 请重试");
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/IPrdWorkOrderService.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkOrderServiceImpl.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/ErpWorkOrderCompletedConsumer.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkReportServiceImpl.java
git commit -m "fix(prd): complete()使用消息数量+原子更新防并发 (PhaseB-子项1)"
```

---

## Task 3: 采购订单状态枚举 + 校验补全

**Files:**
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/enums/PurchaseOrderStatusEnum.java`
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/enums/PurchaseOrderLineReceiveStatus.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuPurchaseOrderServiceImpl.java`（cancel L346 / close L332）

**背景**：`cancel()` L346 仅阻止 RECEIVED 状态作废，未排除 PARTIALLY_RECEIVED。`close()` L332 无前置状态校验（除已 CLOSED/CANCELLED 外都可关），应限制只有 RECEIVED/PARTIALLY_RECEIVED 可关闭。

- [ ] **Step 1: 新建采购订单状态枚举**

文件 `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/enums/PurchaseOrderStatusEnum.java`：

```java
package com.nexus.erp.pu.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 采购订单状态枚举
 */
public enum PurchaseOrderStatusEnum {

    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVING("APPROVING", "审批中"),
    APPROVED("APPROVED", "已批准"),
    PARTIALLY_RECEIVED("PARTIALLY_RECEIVED", "部分收货"),
    RECEIVED("RECEIVED", "已收货"),
    CLOSED("CLOSED", "已关闭"),
    CANCELLED("CANCELLED", "已作废"),
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String label;

    PurchaseOrderStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public boolean canTransitionTo(PurchaseOrderStatusEnum target) {
        Set<PurchaseOrderStatusEnum> allowed = switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> EnumSet.of(APPROVING, DRAFT, CANCELLED);
            case APPROVING -> EnumSet.of(APPROVED, REJECTED, DRAFT);
            case APPROVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CLOSED, CANCELLED);
            case PARTIALLY_RECEIVED -> EnumSet.of(RECEIVED, CLOSED);
            case RECEIVED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(PurchaseOrderStatusEnum.class);
            case CANCELLED -> EnumSet.noneOf(PurchaseOrderStatusEnum.class);
            case REJECTED -> EnumSet.of(DRAFT);
        };
        return allowed.contains(target);
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    public static PurchaseOrderStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (PurchaseOrderStatusEnum s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知采购订单状态: " + code);
    }
}
```

- [ ] **Step 2: 新建行级收货状态枚举**

文件 `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/enums/PurchaseOrderLineReceiveStatus.java`：

```java
package com.nexus.erp.pu.domain.enums;

/**
 * 采购订单行收货状态
 */
public enum PurchaseOrderLineReceiveStatus {
    PENDING("PENDING", "待收货"),
    FULL_RECEIVED("FULL_RECEIVED", "全收"),
    PARTIAL_RECEIVED("PARTIAL_RECEIVED", "部分收"),
    OVER_RECEIVED("OVER_RECEIVED", "超收"),
    SHORT_RECEIVED("SHORT_RECEIVED", "短收");

    private final String code;
    private final String label;

    PurchaseOrderLineReceiveStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static PurchaseOrderLineReceiveStatus fromCode(String code) {
        if (code == null) return null;
        for (PurchaseOrderLineReceiveStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知行收货状态: " + code);
    }
}
```

- [ ] **Step 3: 补全 cancel() 状态校验**

修改 `PuPurchaseOrderServiceImpl.cancel()`：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void cancel(Long orderId) {
    PuPurchaseOrder order = baseMapper.selectById(orderId);
    if (order == null) {
        throw new ServiceException("采购订单不存在");
    }
    PurchaseOrderStatusEnum current = PurchaseOrderStatusEnum.fromCode(order.getStatus());
    // 作废仅允许 DRAFT / SUBMITTED / APPROVING / REJECTED / APPROVED 状态
    // 已收货(PARTIALLY_RECEIVED / RECEIVED)不可作废
    if (!current.canTransitionTo(PurchaseOrderStatusEnum.CANCELLED)) {
        throw new ServiceException("采购订单状态[" + current.getLabel() + "]不可作废, 已收货订单不可作废");
    }
    LambdaUpdateWrapper<PuPurchaseOrder> wrapper = Wrappers.<PuPurchaseOrder>lambdaUpdate()
        .eq(PuPurchaseOrder::getOrderId, orderId)
        .set(PuPurchaseOrder::getStatus, PurchaseOrderStatusEnum.CANCELLED.getCode());
    int rows = baseMapper.update(null, wrapper);
    if (rows == 0) {
        throw new ServiceException("作废失败, 订单可能已被修改");
    }
}
```

- [ ] **Step 4: 补全 close() 状态校验**

修改 `PuPurchaseOrderServiceImpl.close()`：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void close(Long orderId) {
    PuPurchaseOrder order = baseMapper.selectById(orderId);
    if (order == null) {
        throw new ServiceException("采购订单不存在");
    }
    PurchaseOrderStatusEnum current = PurchaseOrderStatusEnum.fromCode(order.getStatus());
    // 关闭仅允许 RECEIVED / PARTIALLY_RECEIVED 状态(已收货的订单才可关闭)
    if (!current.canTransitionTo(PurchaseOrderStatusEnum.CLOSED)) {
        throw new ServiceException("采购订单状态[" + current.getLabel() + "]不可关闭, 仅已收货订单可关闭");
    }
    LambdaUpdateWrapper<PuPurchaseOrder> wrapper = Wrappers.<PuPurchaseOrder>lambdaUpdate()
        .eq(PuPurchaseOrder::getOrderId, orderId)
        .set(PuPurchaseOrder::getStatus, PurchaseOrderStatusEnum.CLOSED.getCode());
    int rows = baseMapper.update(null, wrapper);
    if (rows == 0) {
        throw new ServiceException("关闭失败, 订单可能已被修改");
    }
}
```

- [ ] **Step 5: 编译验证 + Commit**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/enums/ \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuPurchaseOrderServiceImpl.java
git commit -m "feat(pu): 采购订单状态枚举+cancel/close校验补全 (PhaseB-子项2)"
```

---

## Task 4: 收货行外键 + 收货差异处理端点

**Files:**
- Create: `doc/erp/sql/archive/erp-phaseb-po-receive-line.sql`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMaterialReceiveLine.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuPurchaseReceiveWritebackServiceImpl.java`
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/controller/PuPurchaseOrderLineController.java`

**背景**：`WmMaterialReceiveLine` 有 `sourceDocLineId` 字段但未使用，回写时按物料匹配存在歧义。收货差异（OVER_RECEIVED/SHORT_RECEIVED）仅设状态无后续处理端点。

- [ ] **Step 1: DDL 新增 purchase_line_id 列**

文件 `doc/erp/sql/archive/erp-phaseb-po-receive-line.sql`：

```sql
-- =============================================
-- NexusERP - Phase B: 收货行关联采购订单行外键
-- 目标库: ry-erp
-- 创建时间: 2026-06-27
-- 说明: wm_material_receive_line 新增 purchase_line_id 直接外键
--       消除按物料匹配的歧义风险
-- =============================================

USE `ry-erp`;

ALTER TABLE `wm_material_receive_line`
  ADD COLUMN `purchase_line_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采购订单行ID(pu_purchase_order_line.line_id)' AFTER `source_doc_line_id`;

CREATE INDEX `idx_wm_receive_line_po_line` ON `wm_material_receive_line`(`purchase_line_id`);
```

- [ ] **Step 2: WmMaterialReceiveLine 实体添加 purchaseLineId 字段**

在 `WmMaterialReceiveLine.java` 中 `sourceDocLineId` 字段后添加：

```java
/** 关联采购订单行ID (pu_purchase_order_line.line_id) */
private Long purchaseLineId;
```

- [ ] **Step 3: 修改回写逻辑使用 lineId 直接匹配**

修改 `PuPurchaseReceiveWritebackServiceImpl.writeBackReceivedQty()`，将按物料匹配改为按 `purchaseLineId` 直接匹配：

```java
// PuPurchaseReceiveWritebackServiceImpl.java writeBackReceivedQty() 方法
// 替换原有的"按物料ID匹配, 失败按物料编码兜底"逻辑为:
for (WmMaterialReceiveLine receiveLine : receiveLines) {
    // 优先按 purchaseLineId 直接匹配
    LambdaQueryWrapper<PuPurchaseOrderLine> lineWrapper = Wrappers.<PuPurchaseOrderLine>lambdaQuery()
        .eq(receiveLine.getPurchaseLineId() != null, PuPurchaseOrderLine::getLineId, receiveLine.getPurchaseLineId());
    // 兜底: 按 orderId + materialId 匹配(兼容旧数据)
    if (receiveLine.getPurchaseLineId() == null) {
        lineWrapper = Wrappers.<PuPurchaseOrderLine>lambdaQuery()
            .eq(PuPurchaseOrderLine::getOrderId, purId)
            .eq(PuPurchaseOrderLine::getMaterialId, receiveLine.getMaterialId());
    }
    PuPurchaseOrderLine poLine = poLineMapper.selectOne(lineWrapper);
    if (poLine == null) {
        log.warn("收货行 {} 未匹配到采购订单行, 跳过", receiveLine.getLineId());
        continue;
    }
    // 累加已收货数量(原子更新)
    BigDecimal newReceivedQty = poLine.getReceivedQuantity().add(receiveLine.getQuantityReceived());
    PurchaseOrderLineReceiveStatus lineStatus = calcLineReceiveStatus(poLine.getOrderQuantity(), newReceivedQty);
    LambdaUpdateWrapper<PuPurchaseOrderLine> updateWrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
        .eq(PuPurchaseOrderLine::getLineId, poLine.getLineId())
        .set(PuPurchaseOrderLine::getReceivedQuantity, newReceivedQty)
        .set(PuPurchaseOrderLine::getReceiveStatus, lineStatus.getCode());
    poLineMapper.update(null, updateWrapper);
}

// 行级状态计算
private PurchaseOrderLineReceiveStatus calcLineReceiveStatus(BigDecimal orderQty, BigDecimal receivedQty) {
    int cmp = receivedQty.compareTo(orderQty);
    if (cmp == 0) return PurchaseOrderLineReceiveStatus.FULL_RECEIVED;
    if (cmp > 0) return PurchaseOrderLineReceiveStatus.OVER_RECEIVED;
    if (receivedQty.compareTo(BigDecimal.ZERO) > 0) return PurchaseOrderLineReceiveStatus.PARTIAL_RECEIVED;
    return PurchaseOrderLineReceiveStatus.PENDING;
}
```

- [ ] **Step 4: 新增收货差异处理端点**

文件 `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/controller/PuPurchaseOrderLineController.java`：

```java
package com.nexus.erp.pu.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.security.annotation.RequiresPermissions;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.enums.PurchaseOrderLineReceiveStatus;
import com.nexus.erp.pu.service.IPuPurchaseOrderLineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

/**
 * 采购订单行 收货差异处理
 */
@RestController
@RequestMapping("/pu/orderLine")
@RequiredArgsConstructor
public class PuPurchaseOrderLineController {

    private final IPuPurchaseOrderLineService orderLineService;

    /**
     * 标记超收行已确认
     */
    @RequiresPermissions("pu:order:edit")
    @Log(title = "采购订单行超收确认", businessType = BusinessType.UPDATE)
    @PutMapping("/{lineId}/confirmOverReceive")
    public R<Void> confirmOverReceive(@PathVariable Long lineId) {
        LambdaUpdateWrapper<PuPurchaseOrderLine> wrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
            .eq(PuPurchaseOrderLine::getLineId, lineId)
            .eq(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.OVER_RECEIVED.getCode())
            .set(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.FULL_RECEIVED.getCode());
        boolean ok = orderLineService.update(wrapper);
        return ok ? R.ok() : R.fail("行不存在或非超收状态");
    }

    /**
     * 标记短收行为短收关闭(不再补货)
     */
    @RequiresPermissions("pu:order:edit")
    @Log(title = "采购订单行短收关闭", businessType = BusinessType.UPDATE)
    @PutMapping("/{lineId}/closeShortReceive")
    public R<Void> closeShortReceive(@PathVariable Long lineId) {
        LambdaUpdateWrapper<PuPurchaseOrderLine> wrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
            .eq(PuPurchaseOrderLine::getLineId, lineId)
            .eq(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.SHORT_RECEIVED.getCode())
            .set(PuPurchaseOrderLine::getReceiveStatus, PurchaseOrderLineReceiveStatus.FULL_RECEIVED.getCode());
        boolean ok = orderLineService.update(wrapper);
        return ok ? R.ok() : R.fail("行不存在或非短收状态");
    }
}
```

- [ ] **Step 5: 编译验证 + Commit**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

```bash
git add doc/erp/sql/archive/erp-phaseb-po-receive-line.sql \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMaterialReceiveLine.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuPurchaseReceiveWritebackServiceImpl.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/controller/PuPurchaseOrderLineController.java
git commit -m "feat(pu): 收货行外键+差异处理端点 (PhaseB-子项2)"
```

---

## Task 5: 库存预留 DDL + Entity + Mapper

**Files:**
- Create: `doc/erp/sql/archive/erp-phaseb-stock-reservation.sql`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMaterialStock.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/mapper/WmMaterialStockMapper.java`

**背景**：`wm_material_stock` 仅有 `stock_quantity`，无预留数量字段。`SoSalesOrderLine.reservedQty` 字段已就位但 0 调用。需新增 `reserved_quantity` 列，并在 Mapper 添加原子预留/释放方法。

- [ ] **Step 1: DDL 新增 reserved_quantity 列**

文件 `doc/erp/sql/archive/erp-phaseb-stock-reservation.sql`：

```sql
-- =============================================
-- NexusERP - Phase B: 库存预留机制
-- 目标库: ry-erp
-- 创建时间: 2026-06-27
-- 说明: wm_material_stock 新增 reserved_quantity 列
--       available_quantity = stock_quantity - reserved_quantity (计算列, 不持久化)
-- =============================================

USE `ry-erp`;

ALTER TABLE `wm_material_stock`
  ADD COLUMN `reserved_quantity` double(12,2) NOT NULL DEFAULT 0.00 COMMENT '已预留数量(销售订单/工单锁定)' AFTER `stock_quantity`;

-- 初始化: 现有库存预留数量为0
UPDATE `wm_material_stock` SET `reserved_quantity` = 0 WHERE `reserved_quantity` IS NULL;
```

- [ ] **Step 2: WmMaterialStock 实体添加 reservedQuantity 字段**

在 `WmMaterialStock.java` 中 `stockQuantity` 字段后添加：

```java
/** 已预留数量(销售订单/工单锁定) */
private BigDecimal reservedQuantity;

/**
 * 可用数量 = 在库数量 - 已预留数量 (计算属性, 非数据库列)
 */
public BigDecimal getAvailableQuantity() {
    BigDecimal stock = stockQuantity != null ? stockQuantity : BigDecimal.ZERO;
    BigDecimal reserved = reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
    return stock.subtract(reserved);
}
```

- [ ] **Step 3: WmMaterialStockMapper 添加原子预留/释放方法**

在 `WmMaterialStockMapper.java` 中添加：

```java
/**
 * 原子预留库存 (乐观锁 + 可用数量校验)
 * @return 影响行数, 0 表示预留失败(库存不足或版本冲突)
 */
@Update("UPDATE wm_material_stock SET reserved_quantity = reserved_quantity + #{reserveQty}, "
      + "version = version + 1, update_time = NOW() "
      + "WHERE stock_id = #{stockId} "
      + "AND (stock_quantity - reserved_quantity) >= #{reserveQty} "
      + "AND version = #{version}")
int reserveStock(@Param("stockId") Long stockId, @Param("reserveQty") BigDecimal reserveQty, @Param("version") Integer version);

/**
 * 原子释放预留 (乐观锁, 不超过已预留数量)
 * @return 影响行数, 0 表示释放失败(版本冲突)
 */
@Update("UPDATE wm_material_stock SET reserved_quantity = reserved_quantity - #{releaseQty}, "
      + "version = version + 1, update_time = NOW() "
      + "WHERE stock_id = #{stockId} "
      + "AND reserved_quantity >= #{releaseQty} "
      + "AND version = #{version}")
int releaseReserved(@Param("stockId") Long stockId, @Param("releaseQty") BigDecimal releaseQty, @Param("version") Integer version);

/**
 * 原子出库并扣减预留 (出库时同步释放对应预留)
 * @return 影响行数, 0 表示失败
 */
@Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity - #{outQty}, "
      + "reserved_quantity = reserved_quantity - #{consumeReservedQty}, "
      + "version = version + 1, update_time = NOW() "
      + "WHERE stock_id = #{stockId} "
      + "AND stock_quantity >= #{outQty} "
      + "AND reserved_quantity >= #{consumeReservedQty} "
      + "AND version = #{version}")
int deductStockWithReserved(@Param("stockId") Long stockId, @Param("outQty") BigDecimal outQty,
                            @Param("consumeReservedQty") BigDecimal consumeReservedQty, @Param("version") Integer version);
```

- [ ] **Step 4: 编译验证 + Commit**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

```bash
git add doc/erp/sql/archive/erp-phaseb-stock-reservation.sql \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMaterialStock.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/mapper/WmMaterialStockMapper.java
git commit -m "feat(wm): 库存预留DDL+Entity+Mapper原子方法 (PhaseB-子项3)"
```

---

## Task 6: 库存预留 Service + 事务类型

**Files:**
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmStockReservationService.java`
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockReservationServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/enums/StockOperationTypeEnum.java`

**背景**：需要预留 Service 封装 reserve/unreserve/checkAvailable 逻辑，并添加 RESERVE/UNRESERVE 事务类型记录预留流水。

- [ ] **Step 1: 新建预留 Service 接口**

文件 `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmStockReservationService.java`：

```java
package com.nexus.erp.wm.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存预留服务
 * 支持销售订单确认/工单下达时锁定库存, 出库时消费预留
 */
public interface IWmStockReservationService {

    /**
     * 预留库存 (按物料+仓库, FIFO 多批次)
     * @param materialId 物料ID
     * @param warehouseId 仓库ID (可为null, 表示不限仓库)
     * @param reserveQty 预留数量
     * @param sourceDocType 来源单据类型 (SALES_ORDER / WORK_ORDER)
     * @param sourceDocId 来源单据ID
     * @param sourceDocLineId 来源单据行ID
     * @return 预留成功数量 (不足时返回实际预留量)
     */
    BigDecimal reserve(Long materialId, Long warehouseId, BigDecimal reserveQty,
                       String sourceDocType, Long sourceDocId, Long sourceDocLineId);

    /**
     * 释放预留 (按来源单据行)
     * @param sourceDocType 来源单据类型
     * @param sourceDocId 来源单据ID
     * @param sourceDocLineId 来源单据行ID
     * @return 释放数量
     */
    BigDecimal unreserve(String sourceDocType, Long sourceDocId, Long sourceDocLineId);

    /**
     * 检查可用库存 (stock_quantity - reserved_quantity)
     * @param materialId 物料ID
     * @param warehouseId 仓库ID (可为null)
     * @return 可用数量
     */
    BigDecimal checkAvailable(Long materialId, Long warehouseId);
}
```

- [ ] **Step 2: 新建预留 Service 实现**

文件 `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockReservationServiceImpl.java`：

```java
package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IWmStockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存预留服务实现
 * FIFO 多批次预留: 按 expireDate/recptDate/stockId 排序, 逐条预留直到满足需求量
 */
@Slf4j
@DS("erp")
@Service
@RequiredArgsConstructor
public class WmStockReservationServiceImpl implements IWmStockReservationService {

    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper txMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal reserve(Long materialId, Long warehouseId, BigDecimal reserveQty,
                              String sourceDocType, Long sourceDocId, Long sourceDocLineId) {
        if (reserveQty == null || reserveQty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // 查询可用库存批次 (FIFO: 先到期先出)
        LambdaQueryWrapper<WmMaterialStock> wrapper = Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
            .eq(WmMaterialStock::getFrozenFlag, 0L)
            .apply("(stock_quantity - reserved_quantity) > 0")
            .orderByAsc(WmMaterialStock::getExpireDate)
            .orderByAsc(WmMaterialStock::getRecptDate)
            .orderByAsc(WmMaterialStock::getStockId);
        List<WmMaterialStock> stocks = stockMapper.selectList(wrapper);

        BigDecimal remaining = reserveQty;
        BigDecimal reserved = BigDecimal.ZERO;
        for (WmMaterialStock stock : stocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal available = stock.getStockQuantity()
                .subtract(stock.getReservedQuantity() != null ? stock.getReservedQuantity() : BigDecimal.ZERO);
            BigDecimal toReserve = remaining.min(available);
            int rows = stockMapper.reserveStock(stock.getStockId(), toReserve, stock.getVersion());
            if (rows > 0) {
                reserved = reserved.add(toReserve);
                remaining = remaining.subtract(toReserve);
                // 记录预留流水
                recordStockTx(stock, toReserve, "RESERVE", sourceDocType, sourceDocId, sourceDocLineId);
                log.info("预留库存: stockId={}, qty={}, source={}/{}", stock.getStockId(), toReserve, sourceDocType, sourceDocId);
            }
        }
        if (reserved.compareTo(reserveQty) < 0) {
            log.warn("库存预留不足: 需求={}, 实际预留={}, 物料={}", reserveQty, reserved, materialId);
        }
        return reserved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal unreserve(String sourceDocType, Long sourceDocId, Long sourceDocLineId) {
        // 查询该来源单据的所有预留流水
        LambdaQueryWrapper<WmMaterialStockTx> wrapper = Wrappers.<WmMaterialStockTx>lambdaQuery()
            .eq(WmMaterialStockTx::getTxType, "RESERVE")
            .eq(WmMaterialStockTx::getSourceDocType, sourceDocType)
            .eq(WmMaterialStockTx::getSourceDocId, sourceDocId)
            .eq(sourceDocLineId != null, WmMaterialStockTx::getSourceDocLineId, sourceDocLineId);
        List<WmMaterialStockTx> reserveTxs = txMapper.selectList(wrapper);
        BigDecimal released = BigDecimal.ZERO;
        for (WmMaterialStockTx tx : reserveTxs) {
            WmMaterialStock stock = stockMapper.selectById(tx.getStockId());
            if (stock == null) continue;
            int rows = stockMapper.releaseReserved(stock.getStockId(), tx.getQuantity(), stock.getVersion());
            if (rows > 0) {
                released = released.add(tx.getQuantity());
                recordStockTx(stock, tx.getQuantity().negate(), "UNRESERVE", sourceDocType, sourceDocId, sourceDocLineId);
            }
        }
        log.info("释放预留: source={}/{}, 释放量={}", sourceDocType, sourceDocId, released);
        return released;
    }

    @Override
    public BigDecimal checkAvailable(Long materialId, Long warehouseId) {
        LambdaQueryWrapper<WmMaterialStock> wrapper = Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getMaterialId, materialId)
            .eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId)
            .eq(WmMaterialStock::getFrozenFlag, 0L);
        List<WmMaterialStock> stocks = stockMapper.selectList(wrapper);
        return stocks.stream()
            .map(s -> s.getAvailableQuantity())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recordStockTx(WmMaterialStock stock, BigDecimal qty, String txType,
                               String sourceDocType, Long sourceDocId, Long sourceDocLineId) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setStockId(stock.getStockId());
        tx.setMaterialId(stock.getMaterialId());
        tx.setTxType(txType);
        tx.setTxFlag("RESERVE".equals(txType) ? 0 : 0); // 预留/释放不改变在库数量, flag=0
        tx.setQuantity(qty.abs());
        tx.setBeforeQuantity(stock.getStockQuantity());
        tx.setAfterQuantity(stock.getStockQuantity()); // 在库数量不变
        tx.setSourceDocType(sourceDocType);
        tx.setSourceDocId(sourceDocId);
        tx.setSourceDocLineId(sourceDocLineId);
        tx.setTxTime(new Date());
        txMapper.insert(tx);
    }
}
```

- [ ] **Step 3: 添加 RESERVE/UNRESERVE 事务类型**

在 `StockOperationTypeEnum.java` 中添加：

```java
RESERVE("RESERVE", "库存预留"),
UNRESERVE("UNRESERVE", "释放预留"),
```

- [ ] **Step 4: 编译验证 + Commit**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmStockReservationService.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockReservationServiceImpl.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/enums/StockOperationTypeEnum.java
git commit -m "feat(wm): 库存预留Service+事务类型 (PhaseB-子项3)"
```

---

## Task 7: 库存预留集成 - SO确认 + 工单下达 + 出库消费

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/so/service/impl/SoSalesOrderHeaderServiceImpl.java`（confirm L184-207）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkOrderServiceImpl.java`（release L240-253）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/strategy/impl/SalesOutStrategy.java`

**背景**：`SoSalesOrderHeaderServiceImpl.confirm()` 仅做信用检查未调用预留；`PrdWorkOrderServiceImpl.release()` 仅生成领料单未检查可用性；`SalesOutStrategy` 出库时直接扣 stock_quantity 未消费预留。

- [ ] **Step 1: SO 确认时触发库存预留**

在 `SoSalesOrderHeaderServiceImpl.confirm()` 方法中，信用检查通过后、创建生产计划前，添加库存预留调用：

```java
// SoSalesOrderHeaderServiceImpl.java confirm() 方法中添加
// 注入: private final IWmStockReservationService stockReservationService;

// 在信用检查通过后添加:
// 遍历销售订单行, 对库存物料(非自制)触发预留
for (SoSalesOrderLine line : order.getLines()) {
    BdMaterial material = materialService.selectById(line.getMaterialId());
    if (material != null && material.getSourceType() != null && material.getSourceType() == 1) {
        // sourceType=1 自制物料, 跳过(走生产计划)
        continue;
    }
    // 库存物料触发预留
    BigDecimal reserved = stockReservationService.reserve(
        line.getMaterialId(), line.getWarehouseId(), line.getOrderQuantity(),
        "SALES_ORDER", header.getOrderId(), line.getLineId());
    // 更新 SO 行预留数量
    if (reserved.compareTo(BigDecimal.ZERO) > 0) {
        LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .eq(SoSalesOrderLine::getLineId, line.getLineId())
            .set(SoSalesOrderLine::getReservedQty, reserved);
        salesOrderLineMapper.update(null, wrapper);
    }
    if (reserved.compareTo(line.getOrderQuantity()) < 0) {
        log.warn("销售订单行 {} 库存预留不足: 需求={}, 预留={}", line.getLineId(), line.getOrderQuantity(), reserved);
    }
}
```

- [ ] **Step 2: 工单下达时检查库存可用性**

在 `PrdWorkOrderServiceImpl.release()` 方法中，状态变更为 RELEASED 后、生成领料单前，添加可用性检查：

```java
// PrdWorkOrderServiceImpl.java release() 方法中添加
// 注入: private final IWmStockReservationService stockReservationService;

// 在状态变更成功后添加:
// 检查工单所需物料的库存可用性 (BOM 展开 + 库存检查)
BdBom bom = bomService.findCurrentBom(wo.getMaterialId());
if (bom != null) {
    List<IndentedBomLineVo> bomLines = bomService.explodeBom(bom.getBomId());
    for (IndentedBomLineVo bomLine : bomLines) {
        BigDecimal requiredQty = bomLine.getTotalQuantity().multiply(wo.getPlannedQty());
        BigDecimal available = stockReservationService.checkAvailable(bomLine.getChildMaterialId(), null);
        if (available.compareTo(requiredQty) < 0) {
            log.warn("工单 {} 物料 {} 库存不足: 需求={}, 可用={}", wo.getWoId(), bomLine.getChildMaterialId(), requiredQty, available);
            // 不阻断下达, 仅告警 (可配置为阻断)
        }
    }
}
```

- [ ] **Step 3: 出库策略消费预留**

修改 `SalesOutStrategy.java`，出库时优先消费已预留数量：

```java
// SalesOutStrategy.java resolveSalesIssueStocks() 方法修改
// 在 deductStock 调用前, 检查该 SO 行是否有预留
// 如果有预留, 使用 deductStockWithReserved 原子扣减库存+预留

// 替换原有的 stockMapper.deductStock(...) 调用:
// 查询该 SO 行的预留数量
SoSalesOrderLine soLine = salesOrderLineMapper.selectById(issue.getSoLineId());
BigDecimal reservedQty = soLine != null && soLine.getReservedQty() != null ? soLine.getReservedQty() : BigDecimal.ZERO;
BigDecimal consumeReserved = issue.getIssueQty().min(reservedQty); // 本次出库消费的预留量

int rows;
if (consumeReserved.compareTo(BigDecimal.ZERO) > 0) {
    // 有预留: 同步扣减库存+预留
    rows = stockMapper.deductStockWithReserved(stock.getStockId(), issue.getIssueQty(), consumeReserved, stock.getVersion());
} else {
    // 无预留: 普通扣减
    rows = stockMapper.deductStock(stock.getStockId(), issue.getIssueQty(), stock.getVersion());
}
if (rows == 0) {
    throw new ServiceException("库存扣减失败(版本冲突或库存不足): " + stock.getStockCode());
}
// 更新 SO 行剩余预留数量
if (consumeReserved.compareTo(BigDecimal.ZERO) > 0) {
    BigDecimal remainingReserved = reservedQty.subtract(consumeReserved);
    LambdaUpdateWrapper<SoSalesOrderLine> wrapper = Wrappers.<SoSalesOrderLine>lambdaUpdate()
        .eq(SoSalesOrderLine::getLineId, soLine.getLineId())
        .set(SoSalesOrderLine::getReservedQty, remainingReserved);
    salesOrderLineMapper.update(null, wrapper);
}
```

- [ ] **Step 4: 编译验证 + Commit**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/so/service/impl/SoSalesOrderHeaderServiceImpl.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkOrderServiceImpl.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/strategy/impl/SalesOutStrategy.java
git commit -m "feat: 库存预留集成SO确认+工单下达+出库消费 (PhaseB-子项3)"
```

---

## Task 8: BOM Mapper 批量查询方法

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/mapper/BdBomDetailMapper.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/mapper/BdBomMapper.java`

**背景**：`BdBomMapper` 和 `BdBomDetailMapper` 完全空白（仅 `extends BaseMapperPlus`），所有查询通过 Java 层 `selectList` 完成，导致递归展开时每节点一次查询（N+1）。需添加批量查询方法作为 N+1 消除的基础设施。

- [ ] **Step 1: BdBomDetailMapper 添加批量查询**

修改 `BdBomDetailMapper.java`：

```java
package com.nexus.erp.bd.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.bd.domain.BdBomDetail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface BdBomDetailMapper extends BaseMapperPlus<BdBomDetail, ?> {

    /**
     * 按 bomId 集合批量查询明细 (消除 N+1 的核心方法)
     */
    @Select("<script>" +
            "SELECT * FROM bd_bom_detail WHERE bom_id IN " +
            "<foreach item='id' collection='bomIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND status = 1 ORDER BY sort_order" +
            "</script>")
    List<BdBomDetail> selectBatchByBomIds(@Param("bomIds") Collection<Long> bomIds);
}
```

- [ ] **Step 2: BdBomMapper 添加批量查当前 BOM**

修改 `BdBomMapper.java`：

```java
package com.nexus.erp.bd.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.bd.domain.BdBom;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface BdBomMapper extends BaseMapperPlus<BdBom, ?> {

    /**
     * 按物料ID集合批量查当前有效 BOM (消除 findCurrentBom 的 N+1)
     */
    @Select("<script>" +
            "SELECT * FROM bd_bom WHERE parent_material_id IN " +
            "<foreach item='id' collection='materialIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND is_current = 1 AND status = 1" +
            "</script>")
    List<BdBom> findCurrentBomsByMaterialIds(@Param("materialIds") Collection<Long> materialIds);
}
```

- [ ] **Step 3: 编译验证 + Commit**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/mapper/BdBomDetailMapper.java \
       ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/mapper/BdBomMapper.java
git commit -m "feat(bd): BOM Mapper批量查询方法消除N+1基础设施 (PhaseB-子项4)"
```

---

## Task 9: 重构 BOM 递归方法消除 N+1

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomServiceImpl.java`（buildTreeNode L370 / buildIndentedLines L481 / checkCircularRef L285 / whereUsed L583）

**背景**：4 处递归方法存在 N+1：每节点 `selectById` 查物料 + `findCurrentBom` 查子 BOM + `selectList` 查明细。参照 `enrichBomVo`（L103-142）的批量加载范式重构。

- [ ] **Step 1: 新建批量预加载辅助方法**

在 `BdBomServiceImpl.java` 中添加批量预加载辅助方法，将递归过程中需要的所有数据一次性查出来：

```java
/**
 * BOM 展开上下文 (预加载所有需要的物料/BOM/明细, 消除 N+1)
 */
private static class BomExpandContext {
    // materialId -> BdMaterial
    Map<Long, BdMaterial> materialMap;
    // materialId -> BdBom (当前有效版本)
    Map<Long, BdBom> currentBomMap;
    // bomId -> List<BdBomDetail>
    Map<Long, List<BdBomDetail>> detailMap;
}

/**
 * 预加载 BOM 展开所需的全部数据 (BFS 层层展开)
 * @param rootBomId 根 BOM ID
 * @param maxDepth 最大深度
 */
private BomExpandContext preloadBomExpandData(Long rootBomId, int maxDepth) {
    BomExpandContext ctx = new BomExpandContext();
    ctx.materialMap = new HashMap<>();
    ctx.currentBomMap = new HashMap<>();
    ctx.detailMap = new HashMap<>();

    Set<Long> visitedBomIds = new HashSet<>();
    Set<Long> visitedMaterialIds = new HashSet<>();
    List<Long> currentBomIds = new ArrayList<>();
    currentBomIds.add(rootBomId);
    visitedBomIds.add(rootBomId);

    for (int depth = 0; depth <= maxDepth && !currentBomIds.isEmpty(); depth++) {
        // 1. 批量查这层 BOM 的明细
        List<BdBomDetail> details = bomDetailMapper.selectBatchByBomIds(currentBomIds);
        Map<Long, List<BdBomDetail>> levelDetailMap = details.stream()
            .collect(Collectors.groupingBy(BdBomDetail::getBomId));
        ctx.detailMap.putAll(levelDetailMap);

        // 2. 收集这层明细中的子物料ID
        List<Long> childMaterialIds = details.stream()
            .map(BdBomDetail::getChildMaterialId)
            .filter(id -> !visitedMaterialIds.contains(id))
            .distinct()
            .collect(Collectors.toList());
        visitedMaterialIds.addAll(childMaterialIds);

        // 3. 批量查子物料
        if (!childMaterialIds.isEmpty()) {
            List<BdMaterial> materials = materialMapper.selectBatchIds(childMaterialIds);
            ctx.materialMap.putAll(materials.stream()
                .collect(Collectors.toMap(BdMaterial::getMaterialId, m -> m)));
        }

        // 4. 批量查子物料的当前 BOM (下一层)
        if (!childMaterialIds.isEmpty()) {
            List<BdBom> childBoms = bomMapper.findCurrentBomsByMaterialIds(childMaterialIds);
            ctx.currentBomMap.putAll(childBoms.stream()
                .collect(Collectors.toMap(BdBom::getParentMaterialId, b -> b)));
            // 下一层的 bomIds
            currentBomIds = childBoms.stream()
                .map(BdBom::getBomId)
                .filter(id -> !visitedBomIds.contains(id))
                .collect(Collectors.toList());
            visitedBomIds.addAll(currentBomIds);
        } else {
            currentBomIds.clear();
        }
    }
    return ctx;
}
```

- [ ] **Step 2: 重构 buildTreeNode 使用预加载上下文**

```java
@Override
public BomTreeNodeVo getBomTree(Long bomId) {
    BdBom root = baseMapper.selectById(bomId);
    if (root == null) {
        throw new ServiceException("BOM不存在: " + bomId);
    }
    // 预加载全部数据
    BomExpandContext ctx = preloadBomExpandData(bomId, MAX_BOM_DEPTH);
    // 从预加载上下文构建树
    return buildTreeNodeWithContext(root, 0, BigDecimal.ONE, new HashSet<>(), ctx);
}

private BomTreeNodeVo buildTreeNodeWithContext(BdBom bom, int level, BigDecimal cumulativeQty,
                                              Set<Long> visited, BomExpandContext ctx) {
    if (level > MAX_BOM_DEPTH) {
        throw new ServiceException("BOM展开超过最大深度: " + MAX_BOM_DEPTH);
    }
    BomTreeNodeVo node = new BomTreeNodeVo();
    node.setLevel(level);
    node.setParentBomId(bom.getBomId());
    // 从预加载Map取明细 (不再 selectList)
    List<BdBomDetail> details = ctx.detailMap.getOrDefault(bom.getBomId(), Collections.emptyList());
    node.setHasChildren(!details.isEmpty());

    List<BomTreeNodeVo> children = new ArrayList<>();
    for (BdBomDetail detail : details) {
        if (visited.contains(detail.getDetailId())) {
            continue; // 循环引用保护
        }
        Set<Long> childVisited = new HashSet<>(visited);
        childVisited.add(detail.getDetailId());

        BomTreeNodeVo childNode = new BomTreeNodeVo();
        childNode.setLevel(level + 1);
        childNode.setDetailId(detail.getDetailId());
        childNode.setQuantityPerParent(detail.getQuantityPerParent());
        childNode.setTotalQuantity(cumulativeQty.multiply(detail.getQuantityPerParent()));
        // 从预加载Map取物料 (不再 selectById)
        BdMaterial material = ctx.materialMap.get(detail.getChildMaterialId());
        if (material != null) {
            childNode.setMaterialCode(material.getMaterialCode());
            childNode.setMaterialName(material.getMaterialName());
        }
        childNode.setNodeType("PART");
        // 从预加载Map取子BOM (不再 findCurrentBom)
        BdBom childBom = ctx.currentBomMap.get(detail.getChildMaterialId());
        if (childBom != null) {
            childNode.setNodeType("ASSEMBLY");
            BomTreeNodeVo grandChild = buildTreeNodeWithContext(childBom, level + 1,
                cumulativeQty.multiply(detail.getQuantityPerParent()), childVisited, ctx);
            childNode.getChildren().add(grandChild);
        }
        children.add(childNode);
    }
    node.setChildren(children);
    return node;
}
```

- [ ] **Step 3: 重构 buildIndentedLines 使用预加载上下文**

```java
@Override
public List<IndentedBomLineVo> getIndentedBom(Long bomId) {
    BdBom root = baseMapper.selectById(bomId);
    if (root == null) {
        throw new ServiceException("BOM不存在: " + bomId);
    }
    BomExpandContext ctx = preloadBomExpandData(bomId, MAX_BOM_DEPTH);
    List<IndentedBomLineVo> result = new ArrayList<>();
    buildIndentedLinesWithContext(root, 0, BigDecimal.ONE, new HashSet<>(), result, ctx);
    return result;
}

private void buildIndentedLinesWithContext(BdBom bom, int level, BigDecimal parentCumulativeQty,
                                          Set<Long> visited, List<IndentedBomLineVo> result, BomExpandContext ctx) {
    if (level > MAX_BOM_DEPTH) {
        throw new ServiceException("BOM展开超过最大深度: " + MAX_BOM_DEPTH);
    }
    List<BdBomDetail> details = ctx.detailMap.getOrDefault(bom.getBomId(), Collections.emptyList());
    for (BdBomDetail detail : details) {
        if (visited.contains(detail.getDetailId())) {
            continue;
        }
        Set<Long> childVisited = new HashSet<>(visited);
        childVisited.add(detail.getDetailId());

        IndentedBomLineVo line = new IndentedBomLineVo();
        line.setLevel(level);
        line.setIndent(String.join("", Collections.nCopies(level, "  ")));
        line.setDetailId(detail.getDetailId());
        line.setParentBomId(bom.getBomId());
        line.setQuantityPerParent(detail.getQuantityPerParent());
        BigDecimal cumulativeQty = parentCumulativeQty.multiply(detail.getQuantityPerParent());
        line.setCumulativeQuantity(cumulativeQty);
        // 从预加载Map取物料
        BdMaterial material = ctx.materialMap.get(detail.getChildMaterialId());
        if (material != null) {
            line.setMaterialCode(material.getMaterialCode());
            line.setMaterialName(material.getMaterialName());
            line.setUnitCost(material.getStandardCost());
            line.setLineCost(cumulativeQty.multiply(material.getStandardCost() != null ? material.getStandardCost() : BigDecimal.ZERO));
        }
        line.setExpandable(ctx.currentBomMap.containsKey(detail.getChildMaterialId()));
        result.add(line);
        // 递归子BOM
        BdBom childBom = ctx.currentBomMap.get(detail.getChildMaterialId());
        if (childBom != null) {
            buildIndentedLinesWithContext(childBom, level + 1, cumulativeQty, childVisited, result, ctx);
        }
    }
}
```

- [ ] **Step 4: 重构 checkCircularRef 使用预加载上下文**

```java
/**
 * 循环引用检测 (使用预加载上下文, 不再 N+1)
 */
private boolean checkCircularRefWithContext(Long bomId, Set<Long> ancestors, BomExpandContext ctx) {
    if (ancestors.contains(bomId)) {
        return true; // 发现循环
    }
    List<BdBomDetail> details = ctx.detailMap.getOrDefault(bomId, Collections.emptyList());
    Set<Long> newAncestors = new HashSet<>(ancestors);
    newAncestors.add(bomId);
    for (BdBomDetail detail : details) {
        BdBom childBom = ctx.currentBomMap.get(detail.getChildMaterialId());
        if (childBom != null && checkCircularRefWithContext(childBom.getBomId(), newAncestors, ctx)) {
            return true;
        }
    }
    return false;
}

// 修改 verifyBom() 方法调用处:
// 原: if (checkCircularRef(bomId, new HashSet<>())) { ... }
// 改: BomExpandContext ctx = preloadBomExpandData(bomId, MAX_BOM_DEPTH);
//     if (checkCircularRefWithContext(bomId, new HashSet<>(), ctx)) { ... }
```

- [ ] **Step 5: 重构 whereUsed 使用批量查询**

```java
@Override
public List<BomWhereUsedVo> whereUsed(Long materialId) {
    // 批量查所有引用该物料作为子件的 BOM 明细
    LambdaQueryWrapper<BdBomDetail> detailWrapper = Wrappers.<BdBomDetail>lambdaQuery()
        .eq(BdBomDetail::getChildMaterialId, materialId);
    List<BdBomDetail> referencingDetails = bomDetailMapper.selectList(detailWrapper);
    if (referencingDetails.isEmpty()) {
        return Collections.emptyList();
    }
    // 批量查这些明细所属的 BOM
    Set<Long> bomIds = referencingDetails.stream()
        .map(BdBomDetail::getBomId)
        .collect(Collectors.toSet());
    List<BdBom> boms = baseMapper.selectBatchIds(bomIds);
    // 批量查这些 BOM 的父物料
    Set<Long> parentMaterialIds = boms.stream()
        .map(BdBom::getParentMaterialId)
        .collect(Collectors.toSet());
    List<BdMaterial> materials = materialMapper.selectBatchIds(parentMaterialIds);
    Map<Long, BdMaterial> materialMap = materials.stream()
        .collect(Collectors.toMap(BdMaterial::getMaterialId, m -> m));
    // 组装结果 (不再 N+1)
    Map<Long, BdBom> bomMap = boms.stream()
        .collect(Collectors.toMap(BdBom::getBomId, b -> b));
    List<BomWhereUsedVo> result = new ArrayList<>();
    for (BdBomDetail detail : referencingDetails) {
        BdBom bom = bomMap.get(detail.getBomId());
        if (bom == null) continue;
        BdMaterial parentMaterial = materialMap.get(bom.getParentMaterialId());
        BomWhereUsedVo vo = new BomWhereUsedVo();
        vo.setBomId(bom.getBomId());
        vo.setBomVersion(bom.getBomVersion());
        if (parentMaterial != null) {
            vo.setParentMaterialId(parentMaterial.getMaterialId());
            vo.setParentMaterialCode(parentMaterial.getMaterialCode());
            vo.setParentMaterialName(parentMaterial.getMaterialName());
        }
        vo.setQuantityPerParent(detail.getQuantityPerParent());
        result.add(vo);
    }
    return result;
}
```

- [ ] **Step 6: 编译验证 + Commit**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C`
Expected: BUILD SUCCESS

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomServiceImpl.java
git commit -m "perf(bd): BOM展开N+1消除-批量预加载重构4处递归方法 (PhaseB-子项4)"
```

---

## 自检：覆盖升级方案要求点

### §4.1 ERP Phase B 覆盖

| 升级方案要求 | 对应 Task | 覆盖状态 |
|---|---|---|
| 报工自动回写工单 | Task 1/2 | ✅ 枚举类 + complete() 修复 + 原子更新防并发 |
| 库存预留机制 | Task 5/6/7 | ✅ DDL+Mapper+Service+SO/工单/出库集成 |
| 多级BOM展开优化 | Task 8/9 | ✅ 批量查询 + N+1 消除（4处递归方法重构） |
| 采购订单状态机完善 | Task 3/4 | ✅ 枚举类 + 校验补全 + 收货行外键 + 差异处理 |

### 遗留决策点（需用户确认，不阻塞执行）

1. **状态机框架**：本计划采用枚举类 + `canTransitionTo` 方法（轻量级），未引入 Spring StateMachine。如果未来需要完整状态机（事件驱动+监听器+持久化），可升级为框架。
2. **工单下达库存检查策略**：当前为"告警不阻断"（可用性不足时仅 log.warn），可配置为阻断。需用户确认默认策略。
3. **库存预留粒度**：当前按 stock 批次 FIFO 预留，未实现按批次号/序列号预留（`reservedBatchNo`/`reservedSerials` 字段已就位但本次不启用）。
4. **BOM 展开缓存**：当前优化为批量预加载（消除 N+1），未引入结果缓存。如果 BOM 频繁展开且数据稳定，可后续加 Redis 缓存。

### 部署后手动操作项

1. **执行 DDL**（`ry-erp` 库）：
   - `doc/erp/sql/archive/erp-phaseb-po-receive-line.sql`（wm_material_receive_line 加 purchase_line_id）
   - `doc/erp/sql/archive/erp-phaseb-stock-reservation.sql`（wm_material_stock 加 reserved_quantity）
2. **验证库存预留初始化**：`SELECT COUNT(*) FROM wm_material_stock WHERE reserved_quantity > 0` 应为 0
3. **验证 BOM 展开性能**：对一个 5 级 BOM 调用 `getBomTree`，观察 SQL 日志确认无 N+1

---

## 执行顺序建议

**并行批次 1（无依赖，可同时启动）：**
- Task 1（WoStatusEnum 枚举）
- Task 3（PoStatusEnum 枚举 + 校验补全）
- Task 5（库存预留 DDL + Entity + Mapper）
- Task 8（BOM Mapper 批量查询方法）

**并行批次 2（依赖批次1）：**
- Task 2（报工回写修复，依赖 Task 1 枚举）
- Task 4（收货行外键 + 差异端点，依赖 Task 3 枚举）
- Task 6（库存预留 Service，依赖 Task 5 Mapper）
- Task 9（BOM 递归重构，依赖 Task 8 Mapper）

**串行批次 3：**
- Task 7（库存预留集成，依赖 Task 6 Service）

**最终编译验证 + 整体 commit（可选 squash）**
