# ERP 采购（pu/po）+ 生产计划（prd）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-erp/.../erp/pu`（含 `invoice`/`report`/`task`）、`.../erp/prd`（含 `schedule`/`report`）、`.../erp/po`；关联读 `erp/wm/domain`、`erp/mq/consumer`、`plus-ui/src/api/erp/**`、`script/sql/ry-vue.sql`（菜单） | 分支: feature/erp-mes-iot-audit-fix
> 只读审计。已知基线（E6 退货原子扣减、E12/E13/E14、PCBA 委外闭环）按「复核」处理，不重复计数。
> 说明：本报告未读 `script/sql/ry-erp.sql` DDL，因此**不对索引/唯一键/列宽下任何结论**（见 §8）。

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| ERP-PU-01 | P0 | 数据错 | 收货回写无「超收」守卫，且到货通知路径把到货量直接写成收货量，`received_quantity` 会虚增 | `PuPurchaseReceiveWritebackServiceImpl.java:96-105`、`PuPurchaseReceivingServiceImpl.java:128-129` |
| ERP-PU-02 | P0 | 幂等 | 回写幂等键在事务内先占位且不回滚，一次失败后 24h 内永久跳过回写 | `PuPurchaseReceiveWritebackServiceImpl.java:54-58` |
| ERP-PU-03 | P1 | 状态机 | 采购退货 `execute` 不校验 CONFIRMED，草稿单可直接扣库存 + 红冲进项 | `PuPurchaseReturnServiceImpl.java:188-204` |
| ERP-PU-04 | P1 | 状态机 | 订单/退货/申请 `insertByBo`/`updateByBo` 直接采信客户端 `status`，可绕过审批与收货 | `PuPurchaseOrderServiceImpl.java:154-168`、`:178-191` |
| ERP-PU-05 | P1 | 功能缺失 | 采购订单行无退货数量字段，退货不回写订单行，净采购量无口径 | `PuPurchaseOrderLine.java:27-87`、`PuPurchaseReturnServiceImpl.java:205-221` |
| ERP-PU-06 | P1 | 数据错 | 收货单/收货行未写 warehouseId、batchId、purchaseLineId，收货无仓库/批次维度且按物料兜底匹配 | `PuPurchaseReceivingServiceImpl.java:171-197`、`WmMaterialReceiveLine.java:35,51,55` |
| ERP-PU-07 | P1 | 状态机 | 收货回写用整实体 `updateById` 汇总订单状态，可把 CLOSED/CANCELLED 订单改回 RECEIVED | `PuPurchaseReceiveWritebackServiceImpl.java:144-168` |
| ERP-PRD-01 | P1 | 算错数 | 两条 BOM 展开入口口径不一致：一条不算损耗率、需求日期以「现在」为基准且把 leadTime 当天 | `PrdMaterialRequirementServiceImpl.java:344-346,352,486-493` vs `PrdProductionPlanServiceImpl.java:721-729,884-897` |
| ERP-PRD-02 | P1 | 状态机 | 报工可直接落 APPROVED 不回写工单；回写无 CAS 可双累加；无超报工上限 | `PrdWorkReportServiceImpl.java:120-125,137-148,154-171,179-183` |
| ERP-PRD-03 | P1 | 契约 | 返工出库/入库后端有端点，前端 api 层无函数 → UI 不可达 | `PrdReworkOrderController.java:181-187,189-199`、`plus-ui/src/api/erp/prd/rework/index.ts:5-31` |
| ERP-PRD-04 | P1 | 跨模块链路 | `ReworkStockConsumer.doProcess` 只 log.info，返工出入库状态不闭环（复核仍存在） | `ReworkStockConsumer.java:45-57` |
| ERP-PRD-05 | P1 | 并发 | 返工/委外状态流转全为读-改-写；委外收货消息按「有收货即 COMPLETED」覆盖达量完工口径 | `PrdReworkOrderServiceImpl.java:152-156,174-187`、`PrdSubcontractServiceImpl.java:167-171,229-234`、`SubcontractStockConsumer.java:52-66` |
| ERP-PRD-06 | P1 | 权限 | 菜单种子与 `@SaCheckPermission` 双向错配：8 个死按钮码 + 3 个无种子码（非超管 403） | `ry-vue.sql:92252,92259,92261,92268,92269,92343,92344`、`PuPurchaseOrderController.java:188,204`、`PuContractController.java:48` |
| ERP-PRD-07 | P1 | 算错数 | MRP 可用库存口径不一致：`queryTotalStock` 把冻结库存计入可用 → 净需求偏低 | `PrdMaterialRequirementServiceImpl.java:467-479` vs `PuPurchaseRequisitionServiceImpl.java:470-473` |
| ERP-PU-08 | P2 | 功能缺失 | `po.PoRequisition`（部门请购单）与 `pu.PuPurchaseRequisition` 两套并存，前者仅 CRUD、无单号/无转单 | `PoRequisitionServiceImpl.java:33-136` |
| ERP-PRD-08 | P2 | 功能缺失 | 委外 `release()` 不下发 SN 生成消息，只有发料完成路径才发 | `PrdSubcontractServiceImpl.java:160-191` vs `PrdSubcontractClosureServiceImpl.java:354-368` |
| ERP-PRD-09 | P2 | 状态机 | 采购合同 `EXECUTING` 无终态推进入口（Controller 无 close/complete） | `PuContractController.java:39-119`、`PuContractServiceImpl.java:377-384` |
| ERP-PRD-10 | P2 | 排产 | `PrdScheduleEngine` CR 策略用剩余数量近似工时、无日历资源按默认 8h 计产能 | `PrdScheduleEngine.java:151-163,258-274` |
| ERP-PU-09 | P2 | 幂等 | 到货通知可无限重复生成，且不校验采购订单状态（作废/关闭单也能生成） | `PuPurchaseReceivingServiceImpl.java:87-115` |

## 1. P0 详述

### ERP-PU-01 收货回写无超收守卫 + 到货通知路径丢失实际收货量
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuPurchaseReceiveWritebackServiceImpl.java:96-105`、`.../PuPurchaseReceivingServiceImpl.java:128-129`、`:187-197`
- **现状**: 回写时只算 `newReceivedQty = current + lineReceivedQty` 并据此**只更新状态列**，SQL 无上界守卫：
  ```java
  LambdaUpdateWrapper<PuPurchaseOrderLine> updateWrapper = Wrappers.<PuPurchaseOrderLine>lambdaUpdate()
      .eq(PuPurchaseOrderLine::getLineId, poLine.getLineId())
      .setSql("received_quantity = received_quantity + " + lineReceivedQty.toPlainString())
      .set(PuPurchaseOrderLine::getReceiveStatus, lineStatus.getCode());
  ```
  `calcLineReceiveStatus` 把超额判为 `OVER_RECEIVED`（`:122-136`）却照样累加。同时到货通知路径把「订单数量」直接当作「到货数量」再当作「收货数量」：
  `noticeLine.setQuantityArrival(orderLine.getOrderQuantity())`（`PuPurchaseReceivingServiceImpl.java:128`）→ `receiveLine.setQuantityReceived(noticeLine.getQuantityArrival())`（`:195`）。
- **触发路径**: 采购订单 100 件 → 「生成收货联动单据」→ 收货单行数量被预填 100（实收 80 也只能改成 80，但**部分到货**场景下收货单草稿已被填成全量）→ 审批通过回写 100；或对同一订单重复生成/重复审批到货通知，第二次再累加 100。
- **后果**: `pu_purchase_order_line.received_quantity` 超过 `order_quantity`，订单头被置 `RECEIVED`；账面已收与实物不符，且发票校验按 `received_quantity` 放行 → 可按虚增数量开票付款。
- **修复建议**: ①回写改为带守卫的条件更新：`.apply("received_quantity + {0} <= order_quantity", qty)`，影响 0 行时抛 `ServiceException("收货数量超过订单数量")`；②`createMaterialReceiveFromNotice` 不预填 `quantityReceived`（留 0 由仓库实收填写），并补 `setPurchaseLineId(noticeLine.getPurchaseLineId())`（需到货通知行先落 `purchase_line_id`）。
- **影响面**: `PuPurchaseReceiveWritebackServiceImpl`、`PuPurchaseReceivingServiceImpl`、`WmMaterialReceiveLineBo`；到货通知行需新增 `purchase_line_id`（DDL）；前端 `plus-ui/src/views/erp/wm/receive`。需补并发/超收单测。
- **测试现状**: 未发现 `src/test` 覆盖（本包无测试类）。

### ERP-PU-02 回写幂等键先占位、事务内不回滚 → 永久跳过回写
- **证据**: `.../PuPurchaseReceiveWritebackServiceImpl.java:53-58`
- **现状**: `RedisUtils.setObjectIfAbsent("erp:writeback:receive:"+receiveId, "1", Duration.ofHours(24))` 在方法入口执行，之后才 `purId == null` 校验、查行、回写；任一后续步骤抛异常（或方法内 `return`）时事务回滚，但 **Redis key 不回滚**。
- **触发路径**: 收货单已批准但 `purId` 为空 → 日志 `采购订单ID为空，跳过回写`（`:59-62`）并 return，key 已占位；补全 `pur_id` 后重新触发（MQ 重投/人工重发）在 24h 内直接被 `收货单已回写过，跳过` 拦掉。
- **后果**: 采购订单 `received_quantity` 永久不回写，订单永远停在 SUBMITTED/PARTIAL，发票无法按已收开票，对账需人工修数。
- **修复建议**: 把占位放到校验之后、并在成功提交后写 key（`TransactionSynchronization.afterCommit` 写 key，或失败时 `RedisUtils.deleteObject`）；同时把 `purId == null` 从「静默 return」改为抛异常让 MQ 重投。
- **影响面**: `PuPurchaseReceiveWritebackServiceImpl`、`PurchaseReceiveWritebackConsumer.java:70-88`；需补幂等单测（失败后重试必须能回写）。
- **测试现状**: 无。

## 2. P1 详述

### ERP-PU-03 采购退货 `execute` 不校验 CONFIRMED 状态
- **证据**: `.../PuPurchaseReturnServiceImpl.java:186-204`（execute）、`:166-175`（confirm）
- **现状**: `confirm` 严格要求 DRAFT→CONFIRMED，但 `execute` 只做「已是 EXECUTED 则返回」，CAS 用的条件是 `eq(status, purchaseReturn.getStatus())`（即任意当前状态都能抢到），**没有要求 CONFIRMED**：
  ```java
  if (STATUS_EXECUTED.equals(purchaseReturn.getStatus())) { return; }
  int updated = baseMapper.update(null, Wrappers.<PuPurchaseReturn>lambdaUpdate()
      .set(PuPurchaseReturn::getStatus, STATUS_EXECUTED)
      .eq(PuPurchaseReturn::getReturnId, returnId)
      .eq(PuPurchaseReturn::getStatus, purchaseReturn.getStatus()));
  ```
- **触发路径**: 拥有 `pu:return:execute`（菜单 2527 有种子）但无 `pu:return:confirm` 的角色，直接 `PUT /erp/pu/return/execute/{id}`（`PuPurchaseReturnController.java:144`）对 DRAFT 单执行。
- **后果**: 未确认的退货单扣减库存并触发 `redFlushForReturnByPoLines`（`:216-221`）红冲已抵扣进项发票 → 库存与进项税双错，且无审批痕迹。
- **修复建议**: CAS 条件改为 `.eq(PuPurchaseReturn::getStatus, STATUS_CONFIRMED)`，并在影响 0 行时报「仅已确认状态可执行」。
- **影响面**: `PuPurchaseReturnServiceImpl.execute`、前端退货列表按钮显隐；需补状态机单测。
- **测试现状**: 无。

### ERP-PU-04 单据创建/编辑采信客户端 `status`
- **证据**: `.../PuPurchaseOrderServiceImpl.java:153-168`（insertByBo）、`:178-191`（updateByBo）；`.../PuPurchaseReturnServiceImpl.java:110-124`、`:133-144`；`.../PuPurchaseRequisitionServiceImpl.java`（同型）
- **现状**: `BeanUtils.copyProperties(bo, entity)` 后仅 `if (StringUtils.isBlank(entity.getStatus())) entity.setStatus(STATUS_DRAFT)`，即请求体显式传 `status` 即被采纳；`updateByBo` 只校验「当前状态是 DRAFT/SUBMITTED」，随后把 BO 的 `status` 一并 `updateById`。
- **触发路径**: `POST /erp/pu/order` body 带 `"status":"RECEIVED"`（或 `"APPROVED"`/`"CLOSED"`）→ 新单直接是已收货；对 DRAFT 单 `PUT /erp/pu/order` 带 `"status":"APPROVED"` → 免审批进入可收货状态。
- **后果**: 审批流（Flowable submit）与收货闭环被绕过；状态报表与到货/发票校验全部失真。
- **修复建议**: `insertByBo` 强制 `entity.setStatus(STATUS_DRAFT)`；`updateByBo` 显式 `entity.setStatus(null)`（或按白名单只允许改业务字段），状态只能由 `submit/afterFlowHandle/close/cancel` 改。
- **影响面**: `PuPurchaseOrderServiceImpl`、`PuPurchaseReturnServiceImpl`、`PuPurchaseRequisitionServiceImpl`、`PuContractServiceImpl`；前端 form 若有 status 字段应改为只读展示。无需 DDL。
- **测试现状**: 无。

### ERP-PU-05 采购订单行缺退货数量字段（退货不回写订单行）
- **证据**: `.../pu/domain/PuPurchaseOrderLine.java:27-87`（字段清单：`orderQuantity/receivedQuantity/invoicedQty/receiveStatus/unitPrice/...`，**无 returned 字段**）；全仓 grep `returned_quantity|returnedQuantity` 在 `com.nexus.erp` 下无任何命中；`PuPurchaseReturnServiceImpl.java:205-221`（execute 只扣库存 + 红冲，不回写订单行）
- **现状**: 退货量只存在于 `pu_purchase_return_line.return_quantity`；订单行只有「已收」「已开票」两个累计量。
- **后果**: ①无法回答「该订单行净采购量 = 订量 − 退货量」；②发票红冲只能用退货行的 `orderLineId` 现算（`PuPurchaseReturnServiceImpl.java:209-217`），一旦退货行未填 `orderLineId` 就静默跳过红冲；③供应商评估的「退货总数量」需另查退货表，与采购执行报表口径不一致。
- **修复建议**: 新增 `pu_purchase_order_line.returned_quantity decimal(18,4) default 0`（DDL）+ 在 `execute` 内按 `orderLineId` 原子累加；`received_quantity - returned_quantity` 作为可开票口径（同时调整 `PuInvoiceServiceImpl.java:350-355` 的守卫条件）。
- **影响面**: 订单行 VO/前端列、发票开票守卫、采购执行报表；需 DDL + 存量数据初始化脚本。
- **测试现状**: 无。

### ERP-PU-06 收货单/收货行缺仓库、批次、采购行维度
- **证据**: `.../PuPurchaseReceivingServiceImpl.java:171-184`（收货头只 set 单据/供应商/日期，**未 setWarehouseId**）、`:187-197`（收货行只 set 物料与数量，**未 setWarehouseId / setBatchId / setPurchaseLineId**）；列已存在：`wm/domain/WmMaterialReceive.java:55`、`WmMaterialReceiveLine.java:35,51,55`
- **现状**: 由到货通知自动生成的收货单，仓库/批次维度全为 NULL；收货行 `purchase_line_id` 为 NULL 时，回写走「按 orderId + materialId」兜底（`PuPurchaseReceiveWritebackServiceImpl.java:85-91`）且用 `selectOne`。
- **触发路径**: 同一采购订单中同一物料出现两行（不同交期/单价很常见）→ 收货回写 `selectOne` 抛 `TooManyResultsException`（回写整体失败）或匹配到错误行（数量记到另一行）；仓库侧因 `warehouseId` 为空无法定位上架库位。
- **后果**: 收货数据无法按仓库/批次追溯，回写可能直接异常或错行；批次管理（FEFO）在采购入库环节断裂。
- **修复建议**: ①`createArrivalNotice` 落 `noticeLine.purchaseLineId = orderLine.getLineId()`（DDL：`wm_arrival_notice_line.purchase_line_id`）；②`createMaterialReceiveFromNotice` 透传 `purchaseLineId` 并要求 `warehouseId`（收货单必须指定仓库）；③回写兜底匹配改 `selectList` 并在多行时报错而非 `selectOne`。
- **影响面**: `PuPurchaseReceivingServiceImpl`、`WmArrivalNoticeLine`、`WmMaterialReceiveLine`、`PuPurchaseReceiveWritebackServiceImpl`；需 DDL（到货通知行加列）；前端 `erp/wm/receive`。
- **测试现状**: 无。

### ERP-PU-07 收货回写整实体覆盖订单状态
- **证据**: `.../PuPurchaseReceiveWritebackServiceImpl.java:144-168`
- **现状**: `selectById` 取整实体后 `order.setStatus("RECEIVED"/"PARTIALLY_RECEIVED"); purchaseOrderMapper.updateById(order);` —— 无 CAS、无状态白名单。
- **触发路径**: 订单已 `CLOSED`/`CANCELLED` 后，一张滞留的收货单回写（MQ 重投或补录收货）→ 状态被改回 `RECEIVED`；或回写与人工 `close()`（`PuPurchaseOrderServiceImpl` close 端点 `PuPurchaseOrderController.java:188`）并发，后写覆盖先写。
- **后果**: 已关闭/作废订单复活为已收货，可继续开票；关闭动作失去意义。
- **修复建议**: 改为条件更新 `.set(status, newStatus).eq(orderId).notIn(status, "CLOSED","CANCELLED")`，影响 0 行时记警告并跳过；状态汇总逻辑同样按 `order_quantity` 判断（当前 `allReceived` 未考虑退货量）。
- **影响面**: `PuPurchaseReceiveWritebackServiceImpl`；需补状态机单测。
- **测试现状**: 无。

### ERP-PRD-01 两条 BOM 展开入口口径不一致（损耗率 + 倒排基准 + leadTime 单位）
- **证据**: 入口 A（`importBomDetails` → `PrdMaterialRequirementServiceImpl.java:344-346`）：
  ```java
  if (detail.getQuantityPerParent() != null) {
      reqDetail.setRequiredQuantity(detail.getQuantityPerParent().multiply(totalRequiredQuantity));
  }
  ...
  reqDetail.setRequiredDate(resolveRequiredDate(detail.getLeadTime()));   // :352
  ```
  `resolveRequiredDate`（`:486-493`）= `System.currentTimeMillis() - leadTime * 24h`（**天**，基准是「现在」）。
  入口 B（计划驱动 `PrdProductionPlanServiceImpl`）：`calculateChildQuantity`（`:715-733`）含损耗率 `1 + lossRate/100` 且 `setScale(0, CEILING)`；需求日期（`:884-897`）为 `parentRequiredDate - leadTime * 3600s`（**小时**，从父层需求日期倒排）。
- **触发路径**: 前端 `plus-ui/src/api/erp/prd/requirement/index.ts:52` 的 `importBomDetails`（页面「导入BOM明细」）走入口 A；计划页「生成物料需求」走入口 B。同一 BOM 同一数量，两条入口得到**不同的需求量与不同的需求日期**。
- **后果**: 走入口 A 的需求量少算损耗率（例如损耗 5% 的 10.5 件不会向上取整为 11），采购申请按此下量 → 缺料；需求日期以「现在」倒排，与计划完工日期无关，MRP 交期全线偏移；同一 `lead_time` 字段在 A 按天、在 B 按小时，基础数据无法同时正确。
- **修复建议**: 删除入口 A 的重复实现，统一调用 `calculateChildQuantity` 与「父层需求日期倒排」；`lead_time` 单位统一（建议小时，与 `BdBomDetail.leadTime` 语义对齐，`bd/domain/BdBomDetail.java:68`）并在 BOM 明细维护页标注单位。
- **影响面**: `PrdMaterialRequirementServiceImpl`、`PrdProductionPlanServiceImpl`、`MaterialRequirementCalcDTO`、`plus-ui/src/api/erp/prd/requirement`；需回归验证两条入口结果一致。
- **测试现状**: 无（BOM 展开无单测）。

### ERP-PRD-02 报工数量回写可绕过 / 可重复 / 无上限
- **证据**: `.../PrdWorkReportServiceImpl.java:120-125`（`insertByBo` 直接 `baseMapper.insert(entity)`，请求体可带 `reportStatus=APPROVED`）、`:137-148`（仅当 BO 的 `reportStatus == APPROVED` 且旧值非 APPROVED 才回写，**先读后写无 CAS**）、`:154-171`（原子累加 `completed_qty = completed_qty + reportedQty`）、`:179-183`（`updatePrdWorkReport` 直接 `updateById`，**完全不走回写**）
- **触发路径**: ①`POST /erp/prd/report` 带 `reportStatus=APPROVED` → 工单累计完工数量不增加，但报工单显示已审核；②两个请求并发把同一报工单置 APPROVED（各自读到旧值 SUBMITTED）→ `completed_qty` 双倍累加；③`PUT /erp/prd/report` 走 `updatePrdWorkReport` 改状态为 APPROVED → 不回写。
- **后果**: 工单完工数量与实际报工不一致（少记/双记），完工倒冲按 `completionQty` 扣料（`PrdBackflushServiceImpl.java:62`）也随之错料；超报工无上限，`completed_qty` 可远超 `planned_qty` 而工单仍允许完工。
- **修复建议**: ①`insertByBo` 强制 `reportStatus=SUBMITTED`；②回写改为条件更新 `.eq(reportId).eq(reportStatus, "SUBMITTED")` 抢状态后再累加；③`updatePrdWorkReport` 合并到 `updateByBo`；④累加前校验 `completed_qty + reportedQty <= planned_qty`（或按工单允许超报配置）。
- **影响面**: `PrdWorkReportServiceImpl`、`PrdWorkReportController`、前端报工页；需状态机与并发单测。
- **测试现状**: 无。

### ERP-PRD-03 返工出库/入库 UI 不可达
- **证据**: 后端端点 `PrdReworkOrderController.java:181-187`（`PUT /erp/prd/rework/issue/{reworkId}`）、`:189-199`（`PUT /erp/prd/rework/receive/{reworkId}`）；前端 `plus-ui/src/api/erp/prd/rework/index.ts:5-31` 只有 `listReworkOrder/getReworkOrder/addReworkOrder/updateReworkOrder/delReworkOrder/startReworkOrder/completeReworkOrder`，**无 issue/receive**；全仓 grep `views/erp` 内 `/erp/prd/rework/(issue|receive)` 零命中。
- **现状**: 返工单页只能「开始/完工」，无法发起领料与入库；而 `PrdReworkOrderServiceImpl.issueMaterial/receiveMaterial`（`:201-215`、`:227-241`）是唯一会过账到库存的入口。
- **后果**: 返工物料永不从仓库扣减、返工成品永不入库 → 返工闭环在库存上完全缺失（与 ERP-PRD-04 的消费者空转叠加）。
- **修复建议**: 在 `plus-ui/src/api/erp/prd/rework/index.ts` 补 `issueReworkOrder/receiveReworkOrder`，并在 `views/erp/prd/rework/index.vue` 操作列加按钮（权限码用已种子的 `prd:rework:issue`/`prd:rework:receive`，见 ERP-PRD-06）。
- **影响面**: 前端 api + 页面；后端无需改动。
- **测试现状**: 不适用。

### ERP-PRD-04 `ReworkStockConsumer` 只打日志（复核：仍存在）
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/ReworkStockConsumer.java:45-57`
- **现状**: 方法 docstring 写「根据操作类型更新返工订单状态」，实现里 `ISSUE`/`RECEIVE` 两个分支都只有 `log.info("...出库完成，状态更新")`，**没有任何 `update`**（对比 `SubcontractStockConsumer.java:52-66` 是有真实状态更新的）。
- **后果**: 返工出入库过账消息消费后返工单状态不变，`PrdReworkOrder` 永远停在 IN_PROGRESS；无法用状态判断「已发料/已入库」，也无法据此自动完工。
- **修复建议**: 参照 `SubcontractStockConsumer` 实现：`ISSUE` 时校验并记录 `actualStartDate`、`RECEIVE` 时按「实收 ≥ reworkQty」置 COMPLETED（条件更新，防重投）。
- **影响面**: `ReworkStockConsumer`、`PrdReworkOrder` 状态字段；需补 MQ 重投幂等。
- **测试现状**: 无。

### ERP-PRD-05 返工/委外状态流转读-改-写 + 委外收货口径冲突
- **证据**: `PrdReworkOrderServiceImpl.java:147-157`（start）、`:169-189`（complete，含 `sourceWo.setScrapQty(currentScrap.add(...)); updateById` 非原子累加）、`:201-215`、`:227-241`；`PrdSubcontractServiceImpl.java:162-191`（release）、`:201-213`（start）、`:223-235`（complete）、`:245-256`（close）、`:264-275`（submit）；`SubcontractStockConsumer.java:52-66`
- **现状**: 全部为 `selectById → setStatus → updateById`，无 `.eq(status, 旧值)` CAS；并发双点/双消息可双执行（如两次 `close` 与 `complete` 交错、两次 `complete` 各自把 `scrap_qty` 加上去）。委外侧另有口径冲突：`SubcontractStockConsumer` 只要当前是 `IN_PROGRESS` 就把单置 `COMPLETED`（不校验收货量），而 `PrdSubcontractClosureServiceImpl.accumulateActualQty`（`:373-394`）按「实收 ≥ 计划量」才自动完工 —— 两条路径同时生效时，部分收货也会被消息消费置为 COMPLETED。
- **后果**: 返工报废数量重复累加（来源工单报废量虚高）；委外单在只收一部分时被置 COMPLETED，尾量无处登记，`actual_qty < planned_qty` 但状态已完成。
- **修复建议**: 所有状态流转改条件更新（`.eq(status, 期望旧值)`，影响 0 行抛错）；委外收货统一走 `accumulateActualQty` 的达量判定，删除 `SubcontractStockConsumer` 里的 RECEIVE→COMPLETED 分支（或改为仅在达量时置 COMPLETED）。
- **影响面**: `PrdReworkOrderServiceImpl`、`PrdSubcontractServiceImpl`、`SubcontractStockConsumer`、`PrdSubcontractClosureServiceImpl`；需并发单测。
- **测试现状**: 无。

### ERP-PRD-06 权限码与菜单种子双向错配（死按钮 + 非超管 403）
- **证据**: 全量权限码从 `script/sql/ry-vue.sql` 提取（`sys_menu` INSERT），对照各 Controller 的 `@SaCheckPermission`：
  - **有种子、代码从不校验（死按钮）**: `prd:rework:issue`、`prd:rework:receive`（`PrdReworkOrderController.java:181,199` 实际用 `prd:rework:edit`）、`prd:subcontract:issue`、`prd:subcontract:receive`（菜单 `ry-vue.sql:92343,92344`；`PrdSubcontractController` 22 个端点权限码只有 `list/export/query/add/edit/remove`）、`prd:shortage:list`（`PrdShortageRiskController.java:34,44` 用 `prd:shortage:query`）、`pu:vendorEvaluation:calculate`/`calculateAll`（`PuVendorEvaluationController.java:147,165` 用 `pu:vendorEvaluation:edit`）、`pu:order:approve`、`pu:requisition:approve`（对应端点在 `PuPurchaseOrderController.java:168-178`、`PuPurchaseRequisitionController.java:164-174` 已整段注释掉）。
  - **无种子、代码却校验（非超管 403）**: `pu:order:close`（`PuPurchaseOrderController.java:188`）、`pu:order:cancel`（`:204`）、`pu:contract:export`（`PuContractController.java:48`）—— 提取到的 `pu:order:*` 种子只有 add/approve/edit/export/list/query/receive/remove/submit，`pu:contract:*` 只有 add/edit/list/query/remove/submit。
  - **复核上一轮说法（供主代理校正）**: `pu:order:submit`(`ry-vue.sql:92259`)、`pu:order:receive`(`:92261`)、`pu:requisition:convert`(`:92252`)、`pu:return:confirm`(`:92268`)、`pu:return:execute`(`:92269`) **都有** `sys_menu` 种子，不是缺种子项。
- **触发路径**: 角色按菜单勾选「返工出库」「委外发料」按钮 → 点击 403（真实校验的是 `*:edit`）；角色不勾 `pu:order:close`（菜单里没有该按钮）→ 关闭订单接口永远 403，只能给超管用。
- **后果**: 采购订单「关闭/作废」、合同导出对非超管不可用；返工/委外发料收货按钮授权与实际校验不一致，运维无法通过菜单正确授权。
- **修复建议**: ①三个无种子码补 `sys_menu` 按钮（或用已存在的等价码替换注解）；②`prd:rework:issue/receive`、`prd:subcontract:issue/receive`、`pu:vendorEvaluation:calculate*` 要么在 Controller 改为对应码，要么从菜单删除；③`pu:order:approve`/`pu:requisition:approve` 随注释掉的端点一起从菜单删除。
- **影响面**: `script/sql/ry-vue.sql`（菜单，需存量库升级脚本）、上述 Controller；前端按钮 `v-hasPermi` 需同步。
- **测试现状**: 无。

### ERP-PRD-07 MRP 可用库存口径不一致（冻结库存计入可用）
- **证据**: `PrdMaterialRequirementServiceImpl.java:467-479`（`queryTotalStock` 只过滤 `stockQuantity > 0`，**无 frozenFlag 条件**）vs `PuPurchaseRequisitionServiceImpl.java:470-473`（`.eq(WmMaterialStock::getFrozenFlag, 0L)`）
- **触发路径**: 某物料库存 100 中有 80 为冻结（质检/预留），安全库存 10 → MRP 认为可用 90、净需求按 90 抵扣；采购申请低库存扫描却认为可用 20。
- **后果**: 净需求偏低，`shortage_quantity` 少算，采购申请下量不足 → 生产缺料；与低库存申请口径矛盾，同一物料两个模块给出不同「可用量」。
- **修复建议**: `queryTotalStock` 统一加 `.eq(WmMaterialStock::getFrozenFlag, 0L)`（并考虑 `WmStockReservation` 预留量），抽成公共库存可用量服务供 MRP / 低库存申请 / 排产共用。
- **影响面**: `PrdMaterialRequirementServiceImpl`、`PrdProductionPlanServiceImpl:882`（复用同一计算）、`PrdShortageRiskServiceImpl`；需回归净需求数值。
- **测试现状**: 无。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果 / 建议 |
|---|---|---|---|
| ERP-PU-08 | 两套采购申请并存：`po.PoRequisition`（部门请购单，`/erp/po/requisition`）与 `pu.PuPurchaseRequisition`（`/erp/pu/requisition`） | `PoRequisitionServiceImpl.java:33-136`（全类仅 CRUD，无 `AutoCodeUtil` 依赖 → 单号不由服务端生成；无 submit/approve/convert/close）；`plus-ui/src/api/erp/po/requisition/index.ts` 与 `plus-ui/src/api/erp/pu/requisition/index.ts` 两套 | 部门请购单只能增删改查，无法转采购订单 → 业务断在 po 侧；两套表/菜单/页面维护成本翻倍。建议：明确 po 为「内部请购」并补转单，或下线 po 侧页面 |
| ERP-PRD-08 | 委外 `release()` 不触发 SN 生成 | `PrdSubcontractServiceImpl.java:160-191`（只建工单、无 `SubcontractSnGenerateMessage`）vs `PrdSubcontractClosureServiceImpl.java:354-368`（发料完成路径才发消息） | 从「下达」按钮走的委外单不生成 PCBA SN，MES 侧无 SN 可报工；建议把 SN 消息抽到公共方法，两条路径都调用 |
| ERP-PRD-09 | 采购合同 `EXECUTING` 无终态 | `PuContractController.java:39-119`（端点权限码仅 list/export/query/add/edit/remove/edit/query/edit，无 close/complete）；`PuContractServiceImpl.java:377-384` 置 EXECUTING 后无任何代码再改状态 | 合同永远「执行中」，无法统计在履行/已完成合同数；建议补 `close`/`complete` 端点与状态流转 |
| ERP-PRD-10 | 排产引擎近似与默认产能 | `PrdScheduleEngine.java:151-163`（CR 策略用 `remainingQty/8` 近似工期，注释自承「未展开工艺路线」）、`:258-274`（无日历记录的资源按 `DEFAULT_DAILY_HOURS` 8h 计产能） | CR 排序跨物料不可比、未维护日历的车间/工位被按 8h 排产，排产结果与真实产能偏差；建议排产前强制生成产能日历，CR 用真实工艺工时 |
| ERP-PU-09 | 到货通知可重复生成、不校验订单状态 | `PuPurchaseReceivingServiceImpl.java:87-115`（仅校验订单存在/有明细，无「已有未完结通知」查重、无状态校验） | 同一采购订单可反复点「生成收货联动单据」，产生多张到货通知+多张收货单，配合 ERP-PU-01 造成重复累加；建议按 `pur_order_code` + 未完结状态查重，并拒绝 DRAFT/CANCELLED/CLOSED 订单 |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 采购订单行退货数量回写 | `PuPurchaseOrderLine.java:27-87` 无 returned 字段 | `PuPurchaseReturnLine.returnQuantity`、`orderLineId` | 1 天（DDL + execute 累加 + 发票守卫改口径） |
| 2 | 到货通知/收货单的仓库与批次维度 | `PuPurchaseReceivingServiceImpl.java:171-197` 未写 warehouseId/batchId/purchaseLineId | `WmMaterialReceiveLine.java:35,51,55` 列已存在 | 1 天（DDL：到货通知行加 purchase_line_id；收货单强制仓库） |
| 3 | 返工出库/入库前端入口 | `PrdReworkOrderController.java:181-199` 有端点，`plus-ui/src/api/erp/prd/rework/index.ts` 无函数 | 后端 service 与库存过账 `IWmStockOperationService.postReworkIssue/postReworkReceive`（`IWmStockOperationService.java:104,119`）已具备 | 0.5 天（前端 api + 按钮 + 权限码） |
| 4 | 返工出入库 MQ 消费闭环 | `ReworkStockConsumer.java:45-57` 只打日志 | `ReworkStockMessage`、`PrdReworkOrder.status` | 0.5 天 |
| 5 | 委外 `release()` 的 SN 生成 | `PrdSubcontractServiceImpl.java:160-191` 无消息 | `SubcontractSnGenerateMessage`、`PrdSubcontractClosureServiceImpl.java:354-368` 可复用 | 0.5 天 |
| 6 | 采购合同终态（close/complete） | `PuContractController.java:39-119` 无端点 | `PuContract.status`（DRAFT/APPROVED/EXECUTING） | 0.5 天 |
| 7 | MRP 批量倍数/最小订购量 | 全仓 grep `minOrderQty` 只命中 `BdMaterialVendor`/`PriceComparisonVo`（基础数据），MRP 侧无引用 | `bd/domain/BdMaterialVendor.java:62 minOrderQty`、`:57 leadTime` | 1~2 天（采购申请下量时按供应商 minOrderQty 取整） |

## 5. 已核查确认无问题（证明覆盖范围）

- **采购退货库存扣减原子性（E6 复核：已修复仍有效）**：`execute` 先用条件更新抢占 `EXECUTED` 再逐行 `postReturnOut`（`PuPurchaseReturnServiceImpl.java:196-207`）。
- **发票防超开（复核：有效）**：草稿生成时用 `invoiced_qty + {0} <= received_quantity` 条件更新做门禁，影响 0 行回滚（`PuInvoiceServiceImpl.java:350-355`）；红冲前有 `cert_status=DEDUCTED` 过滤与「多发票覆盖同一 PO 行」防超冲注释（`:726`、`:744`）。
- **合同→采购订单 CAS 防重复生成**：`createPurchaseOrderFromContract` 以 `eq(status, APPROVED)` 条件更新为 EXECUTING（`PuContractServiceImpl.java:377-384`）。
- **合同明细批量赋值防护**：`normalizeLines` 白名单拷贝 + 服务端重算金额（`PuContractServiceImpl.java:408-419`）。
- **工单完工 CAS（防 MQ 重投 + 手动双完工）**：`.notIn(woStatus, COMPLETED, CLOSED)` 条件更新，影响 0 行抛错（`PrdWorkOrderServiceImpl.java:521-531`）；`WoStatusEnum.canTransitionTo` 转换矩阵完整（`WoStatusEnum.java:50-61`）。
- **完工倒冲原子累加 + MQ 幂等**：`actual_consumed_qty`/`issued_quantity` 用 `setSql` 原子累加（`PrdBackflushServiceImpl.java:121-127`）；`ProductionBackflushConsumer` 用 `erp:backflush:wo:{id}:{qty}` 幂等键（`ProductionBackflushConsumer.java:61-66`）。
- **报工回写工单数量用原子 SQL**（`PrdWorkReportServiceImpl.java:162-166`）——问题在入口与 CAS，不在累加本身。
- **委外 BOM 快照含损耗率**：`demandQty = plannedQty × qtyPer × (1 + lossRate/100)`（`PrdSubcontractClosureServiceImpl.java:172-179`），且仅 DRAFT 可重建（`:138-140`）。
- **委外收货达量自动完工为原子累加 + 重读判定**（`PrdSubcontractClosureServiceImpl.java:384-394`）。
- **MRP 虚拟件跳层与采购件不递归**：`isPhantom` 判断与 `PROCUREMENT` 不展开（`PrdProductionPlanServiceImpl.java:461-464,494-503,541-543,566-568`）。
- **计划驱动的需求生成幂等**：已生成过则直接返回既有记录（`PrdProductionPlanServiceImpl.java:407-414`）；工单生成按 `sourceId + sourceType` 查重（`:650-658`）。
- **低库存采购申请去重**：同物料已有 DRAFT/SUBMITTED/APPROVED 的低库存申请则跳过（`PuPurchaseRequisitionServiceImpl.java:482-513`）。
- **委外发料完成推进幂等**：已下达/进行中/完工直接返回（`PrdSubcontractClosureServiceImpl.java:324-330`）。
- **工单下达 MQ 在事务提交后发送**：`TransactionSynchronization.afterCommit`（`PrdWorkOrderServiceImpl.java:300-311`），且消息字段与客户信息透传完整（`:724-747`）。
- **委外/返工库存过账带仓库参数**：`postReworkIssue/postReworkReceive` 签名含 `warehouseId/locationId`（`IWmStockOperationService.java:104,119`），问题在调用方传 `null`（`PrdReworkOrderServiceImpl.java:212-213,238-239`）而非接口缺参。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| 返工出库/入库端点 | `PrdReworkOrderController.java:181-187,189-199` 有 `PUT /issue/{id}`、`/receive/{id}` | `plus-ui/src/api/erp/prd/rework/index.ts:5-31` 无对应函数，页面无按钮 | 后端有、前端无 → UI 不可达（ERP-PRD-03） |
| `WmMaterialReceiveLine.warehouseId/batchId/purchaseLineId` | 列存在（`WmMaterialReceiveLine.java:35,51,55`） | 自动生成路径不填（`PuPurchaseReceivingServiceImpl.java:187-197`） | 列有、链路不填 → 收货维度缺失（ERP-PU-06） |
| 权限码 `prd:rework:issue/receive`、`prd:subcontract:issue/receive` | 菜单种子存在（`ry-vue.sql:92343,92344` 等） | Controller 实际校验 `*:edit` | 前端按钮权限码与后端校验不一致（ERP-PRD-06） |
| `pu:order:close/cancel`、`pu:contract:export` | Controller 校验（`PuPurchaseOrderController.java:188,204`、`PuContractController.java:48`） | 菜单无种子 | 后端有、菜单无 → 非超管 403（ERP-PRD-06） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：ERP-PU-01（超收守卫 + 收货量不预填全量）、ERP-PU-02（幂等键移到 afterCommit）。两条都在同一文件，合并一次提交。
- **批次 B（P1 状态机/权限，风险低见效快）**：ERP-PU-03、ERP-PU-04、ERP-PU-07、ERP-PRD-02、ERP-PRD-05、ERP-PRD-06（权限码对齐，需配套菜单增量 SQL）。
- **批次 C（P1 数据/链路，需 DDL）**：ERP-PU-05、ERP-PU-06（订单行 returned_quantity、到货通知行 purchase_line_id）、ERP-PRD-01（统一 BOM 展开）、ERP-PRD-07（统一可用库存口径）。
- **批次 D（P1 前端 + MQ 闭环）**：ERP-PRD-03（返工 UI）、ERP-PRD-04（ReworkStockConsumer）。
- **批次 E（P2）**：ERP-PU-08、ERP-PU-09、ERP-PRD-08、ERP-PRD-09、ERP-PRD-10 及 §4 功能缺失清单。

## 8. 未覆盖范围

- **DDL 未核对**：未读 `script/sql/ry-erp.sql`，因此 `pu_*`/`prd_*`/`po_*` 表的唯一索引、`tenant_id` 维度、`del_flag`、列精度、`sys_dict_data` 字典条目**均未审计**（本报告不对此下任何结论）。已知 `received_quantity` 累加依赖列默认值 0，未核实 DDL 是否有 `default 0`（若为 NULL，`received_quantity + x` 结果为 NULL）。
- **未读/未展开的类**：`PuInvoiceServiceImpl`（866 行，仅读 `:350-355`、`:726` 关键段）、`PuVendorEvaluationServiceImpl` 计算内核（`:200-483` 未读，仅读入口与权限）、`PrdShortageRiskServiceImpl`（在途/需求聚合逻辑未读）、`PrdCapacityCalendarServiceImpl`（产能日历生成与占用未读）、`PrdWorkOrderResourceServiceImpl`、`PrdScheduleServiceImpl` 的工单取数与 `PrdScheduleEngine` 的 `buildLoads` 尾部（`:299-337`）、`PuPurchaseReportServiceImpl`/`PrdProductionReportServiceImpl`（报表口径未核对）。
- **前端页面布局/字段未审**：本轮前端只核对了 `plus-ui/src/api/erp/{pu,prd,po}` 的函数可达性与权限码，未审 `views/erp/pu/**`、`views/erp/prd/**` 的表格列、表单分组、校验与字典绑定（属前端专项范围）。
- **po 模块**：该包仅 9 个文件（部门请购单），已全量过一遍；`po` 无到货/收货/发票相关实现。
- **委外二期遗留**（`SubcontractStockConsumer` 闲置、`TraceabilityResult` 委外节点、发料预留、SN 状态回传）仅覆盖了 `SubcontractStockConsumer` 与 SN 生成消息两处，其余依赖 `doc/erp/pcba-subcontract-feasibility-and-plan.md` 结论未在代码中逐一复核。
- **未复核项**：其他代理已记录的 `PuPurchaseRequisitionServiceImpl.java:455` 的 `'ACTIVE'` tinyint 比较（BD-02）本轮未重复核对；「无供应商的委外单卡在下达」本轮未在 `PrdSubcontractServiceImpl.release()`（`:160-191`）中发现供应商校验代码，疑在 Controller/前端，未进一步定位。
