# MES 工单完工联动链路缺陷修复方案

> 日期：2026-07-10
> 范围：ERP（so / wm / prd / mq）+ SQL 增量
> 触发：派工完工 -> 流转卡 -> MES工单 -> ERP工单 -> SO回写 -> 倒冲领料 -> 成品入库 链路日志分析
> 关联：`doc/system/so-line-and-production-recpt-fix.md`（前序，引入了 PRODUCED/DELIVERED 与 createAndExecute）

---

## 一、缺陷总览

| # | 缺陷 | 根因 | 证据 |
|---|------|------|------|
| 1 | `so_sales_order_line.line_status` 写入非法值 `PRODUCED`/`DELIVERED` | `SoFulfillmentWritebackServiceImpl` 用了字典不存在的状态码 | 字典 `so_order_line_status` 仅 PENDING/SCHEDULED/PARTIALLY_FULFILLED/FULFILLED/CANCELLED（ry-vue.sql:11501-11505）；列注释 ry-erp.sql:3448 |
| 2a | `wm_production_recpt.tenant_id` 落库为 `0` 而非 `'000000'` | 列类型 `bigint(20) NOT NULL`（ry-erp.sql:4536），非标准 `varchar(20)`；租户拦截器插入 `'000000'` 经 bigint 截断为 `0` | 存量记录 tenant_id 全为 0；wm_production_issue/wm_material_stock 均为 varchar（故未受影响） |
| 2b | `wm_production_recpt.wo_no` 空 | `createAndExecute` 未接收/赋值 wo_no | INSERT 日志无 wo_no 列 |
| 2c | `wm_production_recpt.batch_id/batch_code` 空 | 完工入库未为成品生成批次 | INSERT 日志无 batch_id/batch_code |
| 3 | `wm_material_stock` 新建记录大量字段空 | `ProductionCompletionStrategy.findOrCreateStock` 仅设 7 字段（materialId/Code/Name/warehouseId/locationId/stockQuantity/version） | 对比 `ReceiveInStrategy.buildStockFromReceiveDetail`（字段最全） |

---

## 二、缺陷 1：SO 行状态机（SoFulfillmentWritebackServiceImpl）

**有效枚举：** `PENDING`-待处理 / `SCHEDULED`-已排产已采购 / `PARTIALLY_FULFILLED`-部分履行 / `FULFILLED`-已履行 / `CANCELLED`-已取消

**状态映射（用户确认：全发即 FULFILLED）：**

| 触发 | 条件 | 目标状态 |
|------|------|---------|
| `addProducedQty`（生产完工回写） | `production_qty >= ordered_qty` | `SCHEDULED`（已排产：已投产完工，待发货） |
| `addShippedQty`（销售出库回写） | `0 < delivered_qty < ordered_qty` | `PARTIALLY_FULFILLED`（部分履行） |
| `addShippedQty` | `delivered_qty >= ordered_qty` | `FULFILLED`（已履行，行已发齐） |
| `fulfill()` 按钮（既有，不改） | 全单发齐后点履行 | `FULFILLED`（幂等；再做订单级信用/计划/表头收尾） |

> `FULFILLED` 由「全发」与「履行按钮」两条路径达到，语义一致（行已发齐），按钮额外做订单级收尾，对已 FULFILLED 的行幂等。

**改动：** `so/service/impl/SoFulfillmentWritebackServiceImpl.java`
- 常量：`LINE_STATUS_PRODUCED`→`LINE_STATUS_SCHEDULED="SCHEDULED"`；`LINE_STATUS_DELIVERED`→拆为 `LINE_STATUS_PARTIALLY_FULFILLED` / `LINE_STATUS_FULFILLED`
- `promoteToProduced`：设 `SCHEDULED`；前置守卫 notIn 调整为 `(SCHEDULED, PARTIALLY_FULFILLED, FULFILLED)`；条件 UPDATE 的 notIn 同步
- `promoteToDelivered`：select 后按 `delivered_qty` vs `ordered_qty` 判定 —— 部分发设 `PARTIALLY_FULFILLED`，全发设 `FULFILLED`；守卫 notIn `(FULFILLED)`；保留 DB 级 `ge` 权威判定与并发防御
- 事件发布：全发达 FULFILLED 时可保留既有 `SoLineProducedEvent` 语义（或按需调整），本次不新增事件

---

## 三、缺陷 2：wm_production_recpt 字段缺失 + tenant_id 类型

### 3.1 tenant_id 类型修复（SQL 增量）
- **根因：** `wm_production_recpt.tenant_id` 为 `bigint(20) NOT NULL`，非标准 `varchar(20) DEFAULT '000000'`。租户拦截器（`PlusTenantLineHandler.getTenantId` 返回 `StringValue("000000")`）插入字符串，bigint 列落库为 `0`。
- **修复：**
  ```sql
  ALTER TABLE `wm_production_recpt` MODIFY COLUMN `tenant_id` varchar(20) NULL DEFAULT '000000' COMMENT '租户ID';
  UPDATE `wm_production_recpt` SET `tenant_id`='000000' WHERE `tenant_id`='0' OR `tenant_id`=0 OR `tenant_id` IS NULL;
  ```
- **文件：** 新增 `script/sql/update/2026-07-10-wm-production-recpt-tenant-varchar.sql`；同步修改 `script/sql/ry-erp.sql:4536` DDL 与存量 INSERT 记录的 tenant_id 值
- **范围说明：** `wm_production_issue`（ry-erp.sql:4417）、`wm_material_stock`（:4065）已是 varchar，无需改。其余 bigint tenant 表（wm_putaway_rule 等）本次不动 —— 数值匹配下功能正常，且用户未提，列为可选后续

### 3.2 wo_no 赋值
- `IWmProductionRecptService.createAndExecute` 签名增加 `String woNo` 参数
- `ProductionBackflushConsumer.doProcess` 传入 `message.getWoNo()`（消息体已含 woNo=SCGD…）
- `createAndExecute` 内 `recpt.setWoNo(woNo)`
- 手动 `execute(recptId)` 路径：recpt 实体已有 woNo 字段，无需额外处理

### 3.3 batch_id/batch_code 自动生成
- 完工入库为成品生成批次：`createAndExecute` 调用 `IWmBatchService.createBatchByMaterial(materialId, "PRD_WORK_ORDER", woId, woNo, warehouseId, null)`
- 设到 `recpt.batchId/batchCode`，并将 batchId/batchCode 透传给 `postProductionCompletion`（供库存记录共用同一批次）
- 手动 `execute(recptId)` 路径：若 `recpt.batchId` 为空同样生成并回写

---

## 四、缺陷 3：wm_material_stock 新建记录字段补全（ProductionCompletionStrategy）

**参考实现：** `ReceiveInStrategy.buildStockFromReceiveDetail`（采购入库，字段最全）

**改动：** `wm/strategy/impl/ProductionCompletionStrategy.java`
- `execute` 签名扩展：增加 `woNo`、`batchId`、`batchCode` 参数
  - 同步改 `IWmStockOperationService.postProductionCompletion` 接口 + `WmStockOperationServiceImpl` 转发 + 两处调用方（`createAndExecute`、手动 `execute`）
- 注入：`BdMaterialMapper`、`WmWarehouseMapper`、`WmStorageLocationMapper`、`WmStorageAreaMapper`（+ 计量单位查询）
- `findOrCreateStock` 新建分支完整赋值：
  - **物料维度**（查 BdMaterial）：`categoryId`、`materialSpec`、`baseUomId`、`baseUomName`（按 baseUomId 查计量单位名）、`standardCost`、`isBatchManage`、`isSerialManage`
  - **仓库维度**（查 WmWarehouse）：`warehouseCode`、`warehouseName`
  - **库位/库区**（查 WmStorageLocation → WmStorageArea）：`locationCode`、`locationName`、`areaId`、`areaCode`、`areaName`
  - **批次**：`batchId`、`batchCode`（传入）
  - **工单**：`woId`、`woNo`
  - **数量/时间**：`stockQuantity=0`（随后 addStock 累加）、`productionDate=now`、`recptDate=now`
  - **成本**：`unitCost=standardCost`（或 0）、`totalCost=unitCost×quantity`
  - **序列号**：`snId/snCode=null`（完工入库不生成 SN）
  - `frozenFlag=0`、`version=0`
- `findOrCreateStock` 查询条件增加 `batchId` 维度（不同批次独立库存记录，与 ReceiveInStrategy 一致）
- 流水 `buildBaseTxFromStock` 已从 stock 取字段，stock 补全后流水自动完整（无需额外改）

**调用方改动：** `WmProductionRecptServiceImpl`
- `createAndExecute`：生成批次 → 设 recpt → 调用扩展的 `postProductionCompletion` 传入 woNo+batchId+batchCode
- `execute`（手动）：recpt 已有字段，传入 woNo+batchId+batchCode（batchId 空则生成）

---

## 五、文件改动清单

| # | 类型 | 文件 | 说明 |
|---|------|------|------|
| 1 | 改 | `so/service/impl/SoFulfillmentWritebackServiceImpl.java` | 状态机映射：PRODUCED→SCHEDULED，DELIVERED→PARTIALLY_FULFILLED/FULFILLED |
| 2 | 改 | `wm/service/IWmStockOperationService.java` | `postProductionCompletion` 签名加 woNo/batchId/batchCode |
| 3 | 改 | `wm/service/impl/WmStockOperationServiceImpl.java` | 转发新参数到策略 |
| 4 | 改 | `wm/strategy/impl/ProductionCompletionStrategy.java` | 字段补全 + 注入查询 Mapper + batchId 查询维度 |
| 5 | 改 | `wm/service/IWmProductionRecptService.java` | `createAndExecute` 签名加 woNo |
| 6 | 改 | `wm/service/impl/WmProductionRecptServiceImpl.java` | createAndExecute+execute：woNo/批次生成/传参 |
| 7 | 改 | `mq/consumer/ProductionBackflushConsumer.java` | 传 message.getWoNo() |
| 8 | 新 | `script/sql/update/2026-07-10-wm-production-recpt-tenant-varchar.sql` | tenant_id 改 varchar + 存量修正 |
| 9 | 改 | `script/sql/ry-erp.sql` | wm_production_recpt DDL tenant_id 改 varchar + 存量记录值 |

---

## 六、验证要点

1. **SO 行状态**：派工完工后 `line_status=SCHEDULED`（不再 PRODUCED）；部分发货→PARTIALLY_FULFILLED；全发→FULFILLED；fulfill 按钮仍 FULFILLED
2. **生产入库单**：`wm_production_recpt` tenant_id=`'000000'`、wo_no=工单号、batch_id/batch_code 有值
3. **库存记录**（新建成品库位时）：category/uom/batch/warehouse/area/location/cost 字段齐全
4. **库存流水**：`wm_material_stock_tx` 字段齐全（从 stock 派生）
5. **手动入库** `execute()` 路径同样字段齐全
6. **幂等不变**：`hasPosted`（库存级）+ 同 woId EXECUTED 单据级，重复消费不重复入库
7. **并发不变**：setSql 原子累加 + 条件 UPDATE 状态推进保留

---

## 七、不在本次范围

1. 其余 bigint tenant_id 表（wm_putaway_rule 等）类型统一 —— 可选后续
2. SO 行 SCHEDULED 在「排产/下单」时的提前设置 —— 当前仅完工回写设 SCHEDULED，已满足「已排产」语义
3. `SoLineProducedEvent` 监听器（自动创建销售出库单）—— 前序方案标注为后续扩展，本次不启用
