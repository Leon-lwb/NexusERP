# ERP 仓储（单据与作业半边）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm` 下**单据/作业**相关类（收货 `WmMaterialReceive*`、到货通知 `WmArrivalNotice*`、销售出库 `WmSalesIssue*`、生产领料/退料 `WmProductionIssue*`/`WmMaterialReturn*`、委外收发料 `WmSubcontractIssue*`/`WmSubcontractReceive*`、库位转移 `WmTransfer*`、盘点 `WmStockTaking*`、预留 `WmStockReservation*`、`wm/controller/app/**`（PDA）、`wm/report/**`）+ 相关 `strategy/impl` 与 `plus-ui/src/api/erp/wm`、`plus-ui/src/views/erp/wm`、`script/sql` | 实际读过文件数: 30（含 SQL 片段与前端片段） | 分支: feature/erp-mes-iot-audit-fix
>
> 不在本报告范围（另一代理负责，未重复）：库存主表/批次/流水/成本/预警/上架规则/拣货规则/条码 SN 的**内核实现**。
> 已知基线复核：`WmMaterialReceiveCrudServiceImpl.java:622` 附近 MQ 不带 tenantId 已修（见 `:685-712`）；IQC 冻结单向不可解冻属 B4 批次在修，未重复报告。

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| ERP-WM-01 | P0 | 金额精度 | 销售出库唯一活路径 `completeSalesIssue` 不结转出库成本；带成本结转的 `postSalesIssue`/`SalesOutStrategy` 无任何调用方（死代码）→ 库存金额虚高、出库成本为 0 | `WmSalesIssueServiceImpl.java:206`, `WmStockOperationServiceImpl.java:58`, `SalesOutStrategy.java:165` |
| ERP-WM-02 | P0 | 状态机/数据错 | 盘点未录入实盘数即可过账：`generateLines` 把 `actual_quantity` 初始化为 0，`postAdjustment` 不校验是否盘过 → 一次点击把仓库库存全部调成 0 | `WmStockTakingPlanServiceImpl.java:208`, `:251`, `plus-ui/src/views/erp/wm/stocktaking/form.vue:465` |
| ERP-WM-03 | P0 | 校验/数据错 | PDA 收货核销对累计实收数量无上限、读-改-写累加，重复扫码即可超收入库 | `PdaReceiveServiceImpl.java:150`, `:184` |
| ERP-WM-04 | P1 | 校验 | 生产领料「已领满」行 `shortQty=0` 时跳过超领守卫，仍可继续领料 | `WmProductionIssueServiceImpl.java:253` |
| ERP-WM-05 | P1 | 并发/幂等 | 生产领料 `execute` 无单据级 CAS 与幂等键，重复提交/并发双扣；需求明细回写非原子 | `WmProductionIssueServiceImpl.java:208`, `:301`, `:614` |
| ERP-WM-06 | P1 | 校验/跨模块 | 委外发料超发：`createIssue` 的先查后写校验在过账时不再复核，`doPostIssue` 无上限累加 `issued_qty` | `WmSubcontractIssueServiceImpl.java:187`, `:293` |
| ERP-WM-07 | P1 | 状态机 | 调拨 `execute` 条件为「非 EXECUTED」→ 草稿单可绕过审批、绕过 PDA 下架直接过账 | `WmTransferServiceImpl.java:173` |
| ERP-WM-08 | P1 | 状态机 | 调拨 `updateByBo`/`deleteByIds` 无状态守卫 → 已执行调拨单的明细可被改写/整单删除 | `WmTransferServiceImpl.java:129`, `:149` |
| ERP-WM-09 | P1 | 状态机/契约 | 销售出库 `insertByBo`/`updateByBo`/`deleteByIds` 无状态守卫且 `status` 由 BO 直传 → 已过账出库单可改行/删除，或直造 COMPLETED | `WmSalesIssueServiceImpl.java:139`, `:157`, `:172`, `WmSalesIssueBo.java:99` |
| ERP-WM-10 | P1 | 状态机 | 盘点计划 CRUD 无状态守卫 + `status` 直传；`generateLines` 亦无状态守卫，可对已过账计划重建明细（抹掉/伪造盘点结果） | `WmStockTakingPlanServiceImpl.java:110`, `:138`, `:159`, `:178` |
| ERP-WM-11 | P1 | 状态机 | `postAdjustment`/`executeCountAdjust` 允许 `APPROVING`（审批中）直接过账 → 绕过审批调整库存 | `WmStockTakingPlanServiceImpl.java:238`, `:403` |
| ERP-WM-12 | P1 | 功能缺失/跨模块 | 到货通知行没有「已收数量」字段，收货数量无法回写通知行；任何一次入库都把通知单整单置 FINISHED | `WmArrivalNoticeLine.java:42-53`, `WmMaterialReceiveExecutorServiceImpl.java:165` |
| ERP-WM-13 | P1 | DDL/幂等 | 委外发/收货单号用 `selectCount+1` 生成（并发必重号），且 `wm_subcontract_issue/receive`、`wm_transfer` 无单号唯一索引 | `WmSubcontractIssueServiceImpl.java:200`, `WmSubcontractReceiveServiceImpl.java:96`, `ry-erp.sql:7357` |
| ERP-WM-14 | P1 | 校验/数据错 | 调拨未校验目标仓库/目标库位非空，目标库存行可落 `warehouse_id=NULL/location_id=NULL`，库存维度丢失且会被其它仓库的拣货匹配到 | `TransferStrategy.java:122`, `:141`, `:147` |
| ERP-WM-15 | P1 | 数据错 | 出库「SO 行预留释放量」与「库存行预留释放量」两套口径可能不等且不做校验 → 库存行 `reserved_quantity` 残留（可用量虚减、永久占用） | `WmSalesIssueServiceImpl.java:280`, `:296`, `:347` |
| ERP-WM-16 | P1 | 状态机 | `confirmSalesIssue` 允许 `DRAFT→CONFIRMED`（无审批）、且读-改-写无 CAS | `WmSalesIssueServiceImpl.java:191` |
| ERP-WM-17 | P1 | 并发 | `confirmShelf` 读-改-写无 CAS，PDA 与 PC 双击并发可重复上架确认 | `WmMaterialReceiveCrudServiceImpl.java:329`, `:403` |

## 1. P0 详述

### ERP-WM-01 销售出库不结转成本（唯一活路径不调 `issueStockCost`）
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSalesIssueServiceImpl.java:206`（`completeSalesIssue` 全量 206-361，无任何 `valuationService` 调用）、`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockOperationServiceImpl.java:58`、`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmStockOperationService.java:30`、`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/strategy/impl/SalesOutStrategy.java:165`
- **现状**: 出库有两条实现：①`WmSalesIssueController` 的 `complete` → `completeSalesIssue`，逐行 `wmMaterialStockMapper.deductStock/deductStockWithReserved` + 写 `wm_material_stock_tx`，**但不调用 `IInventoryValuationService.issueStockCost`**；②`WmStockOperationServiceImpl.postSalesIssue` → `SalesOutStrategy.execute`，其中 `:165` 明确做「按移动平均成本结转(扣减 total_cost)」。全仓 grep `postSalesIssue` 只命中接口声明与实现两处，**无任何调用方**；`SalesOutStrategy` 只能由它触达 → ②整条链是死代码。
- **触发路径**: 出库单提交审批 → 通过（`afterFlowHandle` 置 APPROVED）→ 前端「确认」→「完成出库」（`plus-ui/src/api/erp/wm/sales-issue/index.ts:43,50`）。
- **后果**: 出库后库存数量减了、金额没减 → `wm_material_stock.total_cost/移动平均单价` 虚高，出库成本记为 0，销售毛利虚高；且下一次入库的移动平均价被高估，误差逐笔累积，月末存货账实不符。
- **修复建议**: 在 `completeSalesIssue` 扣减成功处（`WmSalesIssueServiceImpl.java:300` 之后）按批次调用 `valuationService.issueStockCost(stock.getStockId(), consumeQty)`，并把返回的 `outUnitCost` 写入 `tx.setUnitCost/totalCost`（与 `SalesOutStrategy.java:165-167` 完全同口径）；同时删除或改由 `postSalesIssue` 作为唯一入口，避免双实现漂移。需补测试：出库后断言 `wm_material_stock.total_cost` 减少额 = 数量×单位成本。
- **影响面**: `WmSalesIssueServiceImpl`、`SalesOutStrategy`、`IWmStockOperationService`；前端无改动；无需 DDL。
- **测试现状**: `ruoyi-modules/ruoyi-erp/src/test` 下无针对出库成本结转的单测（未发现覆盖该路径的测试类）。

### ERP-WM-02 盘点未录入实盘数即可过账，一次点击把库存清零
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockTakingPlanServiceImpl.java:208`（`line.setActualQuantity(BigDecimal.ZERO)`）、`:251-269`（过账循环，未校验是否盘过）、`:256-258`（`actual` 缺省取 0，`diff = 0 - book`）、`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmStockTakingPlanController.java:148`、`plus-ui/src/views/erp/wm/stocktaking/form.vue:465`
- **现状**:
  ```java
  // generateLines: 生成盘点行时实盘数直接写 0
  line.setBookQuantity(stock.getStockQuantity());
  line.setActualQuantity(BigDecimal.ZERO);   // :208
  // postAdjustment: 没有"是否已录入实盘数"的前置校验
  BigDecimal actual = line.getActualQuantity() == null ? BigDecimal.ZERO : line.getActualQuantity(); // :256
  BigDecimal diff = actual.subtract(book);   // :258
  if (diff.compareTo(BigDecimal.ZERO) != 0) { stockOperationService.postCountAdjust(...); } // :261
  ```
- **触发路径**: 盘点管理表单 → 点「生成盘点明细」（`form.vue:459`）→ 不录入任何实盘数 → 直接点「差异过账」（`form.vue:473`，仅一个二次确认弹窗，无任何数据校验）。或直接 `PUT /erp/wm/stocktaking/postAdjustment/{planId}`。
- **后果**: 该仓库/库区全部盘点行的 `diff = -book`，`CountAdjustStrategy` 逐行把库存调整为 0，并生成等量的盘亏流水；库存清零后仍可通过后续单据继续扣减（若并发），且盘点单状态变 POSTED，从界面看是「已正常过账」，账实核对无法发现是误操作。
- **修复建议**: 在 `postAdjustment` 领取（`:234-241`）后增加硬校验：统计该计划下 `status='PENDING'` 且未录入实盘数的行数（可给 `WmStockTakingLine` 增加 `counted_flag` 或约定 `actual_quantity IS NULL` 表示未盘），若有未盘行则抛 `ServiceException`；同时把 `generateLines` 的 `actualQuantity` 初始值改为 `null`（`WmStockTakingLine.actual_quantity` 需允许 NULL，需 DDL），过账时遇到 `null` 直接报错而非按 0 处理。
- **影响面**: `WmStockTakingPlanServiceImpl`、`PdaStocktakingServiceImpl.count`（`:208` 已写实盘数，不受影响）、前端 `stocktaking/form.vue`；**需要 DDL**（允许 `actual_quantity` 为 NULL 或新增 `counted_flag`）；需补测试：未盘行存在时过账必须失败且库存不变。
- **测试现状**: 无单测覆盖盘点过账。

### ERP-WM-03 PDA 收货核销无超收上限（读-改-写累加）
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/PdaReceiveServiceImpl.java:150-151`、`:184-186`
- **现状**:
  ```java
  BigDecimal received = line.getQuantityReceived() == null ? BigDecimal.ZERO : line.getQuantityReceived();
  line.setQuantityReceived(received.add(bo.getQty()));   // :150-151 无上限、无 CAS
  ...
  detail.setQuantity(qty.add(bo.getQty()));              // :185-186 上架明细同样无上限
  ```
  对照生产退料 PDA 的正确做法：`PdaReturnServiceImpl.java:114` 有 `actual.add(bo.getQty()) > plan` 的超退拦截；收货侧**完全没有**等价校验（既不比 `line.getQuantity()`，也不比到货通知行 `quantityArrival`）。
- **触发路径**: PDA 收货核销页对同一 `lineId` 重复扫码（或客户端重试、断网重放）；或直接调 `POST /app/pda/receive/{receiveId}/confirm-line` 传超大 `qty`。
- **后果**: `wm_material_receive_line.quantity_received` 与上架明细 `quantity` 被无限放大；`WmMaterialReceiveExecutorServiceImpl.executeReceive`（`:139`）按明细数量过账入库 → **账面库存 > 实际到货**，多出的库存可被后续出库/领料消耗，形成实物短缺且无法追溯来源。
- **修复建议**: 在 `confirmLine` 累加前按「行应到量（`line.getQuantity()`）或到货通知行 `quantity_arrival`」校验 `received + qty <= 上限`，超限返回 `PdaActionResultVo.fail`；累加改为条件更新 `UPDATE wm_material_receive_line SET quantity_received = IFNULL(quantity_received,0)+#{qty} WHERE line_id=#{id} AND IFNULL(quantity_received,0)+#{qty} <= #{limit}`，影响 0 行即拒绝（与 `PdaReturnServiceImpl:114` 同型）。前端 `PDA 收货` 页需同步显示「已收/应到」。
- **影响面**: `PdaReceiveServiceImpl`、PDA 前端；无需 DDL；需补测试：超收必须失败。
- **测试现状**: 无单测。

## 2. P1 详述

### ERP-WM-04 生产领料「已领满」行仍可继续领料（超领守卫被 0 短路）
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmProductionIssueServiceImpl.java:246-257`、`:285-290`
- **现状**: `shortQty = requiredQty - issuedQty`，当该行已领满时 `shortQty == 0`；而守卫写成 `if (shortQty != null && shortQty.compareTo(ZERO) > 0 && currentQty > shortQty)` —— `shortQty == 0` 时**整个校验被跳过**，只受「可用库存」限制。
- **触发路径**: 单头状态为 `PARTIAL`（其它行未领满）时，对已 `ISSUED` 的行填入 `current_qty > 0` 调 `PUT /erp/wm/productionissue/execute/{issueId}`（前端 `productionissue/index.ts:44`）。
- **后果**: 领料量超过工单应领量，多领的物料不计入欠料、也不回写 `prd_material_requirement_detail` 的合理口径（`:614-617` 会把 `issued_quantity` 继续累加并算出负欠料），物料成本被多摊到工单。
- **修复建议**: 改为 `if (shortQty != null && currentQty.compareTo(shortQty.max(ZERO)) > 0) throw ...`，即已领满（`shortQty<=0`）时任何正数 `currentQty` 都必须拒绝；同时把 `line.setLineStatus` 的 `LINE_ISSUED` 分支提前到循环开头直接 `continue`。
- **影响面**: `WmProductionIssueServiceImpl`；前端无需改动；需补测试。

### ERP-WM-05 生产领料 `execute` 无单据级 CAS / 幂等
- **证据**: `WmProductionIssueServiceImpl.java:208-219`（仅读状态判断，无抢占）、`:291`（`lineMapper.updateById` 覆盖式更新 `issued_qty`）、`:301-303`（结束时写单头状态）、`:606-618`（需求明细读-改-写）
- **现状**: 与销售出库（`:215-222` 有 `CONFIRMED→EXECUTING` CAS）、入库（`WmMaterialReceiveExecutorServiceImpl.java:104-110` 有 `SHELVED→EXECUTING` CAS）、委外发料/收货（有 `Lock4j` + CAS）不同，领料 `execute` 没有任何抢占；类注释 `:202-204` 自认「同一领料单不建议并发执行」。
- **触发路径**: 用户双击「执行领料」，或 PDA/PC 同时提交；两次请求都读到 `status=CREATED`，都按同一 `current_qty` 扣减。
- **后果**: 若两次扣到不同批次（`@Version` 不冲突），同一行被扣两次 → 实际领料翻倍、`issued_qty` 覆盖式更新为同一值（`recalcHeaderTotals` 也算错），库存与单据双错。
- **修复建议**: 在 `:219` 后加条件更新抢占 `CREATED/PARTIAL → EXECUTING`（影响 0 行则直接返回/抛异常），结束时置 `COMPLETED/PARTIAL`；`updateRequirementDetail` 改为 `setSql("issued_quantity = IFNULL(issued_quantity,0) + ...")` 原子累加。
- **影响面**: `WmProductionIssueServiceImpl`；无需 DDL；需补测试（并发两次 execute 只生效一次）。

### ERP-WM-06 委外发料可超发（过账不复核 + 无上限累加）
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSubcontractIssueServiceImpl.java:187-195`（创建时校验 `issued + issueQty <= demand`）、`:292-296`（过账时 `setSql("issued_qty = IFNULL(issued_qty,0) + qty")`，无上限条件）
- **现状**: `issued_qty` 只在过账（`doPostIssue`）时才累加，而超发校验只在 `createIssue`（DRAFT）时做。两张草稿发料单会各自通过校验。
- **触发路径**: 对同一委外订单创建发料单 A（需求 100、已发 0、发 60）与发料单 B（同样看到已发 0、发 60）→ 依次 `PUT /erp/wm/sc-issue/post/{id}` → 快照行 `issued_qty = 120 > demand 100`。
- **后果**: 多发给供应商 20 的物料，`allSelfSupplyLinesFulfilled`（`:307-320`）判为已发齐并推进订单 RELEASED，超发部分无单据可追回。
- **修复建议**: 在 `doPostIssue` 累加时改为带守卫的条件更新：`.setSql("issued_qty = IFNULL(issued_qty,0) + q") .apply("IFNULL(issued_qty,0) + {0} <= demand_qty", q)`，影响 0 行则抛 `ServiceException("累计发料将超过需求量")` 并回滚；`createIssue` 的校验保留作为前置提示。
- **影响面**: `WmSubcontractIssueServiceImpl`；无需 DDL；需补测试。

### ERP-WM-07 调拨单可绕过审批直接执行
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmTransferServiceImpl.java:169-179`
- **现状**: 抢占条件为 `.ne(WmTransfer::getStatus, STATUS_EXECUTED)`，即除「已执行」外任何状态（含 `DRAFT`、`REJECTED`）都能被置为 `EXECUTING` 并立即过账；PDA 侧 `PdaTransferServiceImpl.execute`（`:183`）反而正确要求 `APPROVED`。
- **触发路径**: `PUT /erp/wm/transfer/execute/{id}`（前端 `transfer/index.ts:19`）对一张草稿/被驳回的调拨单。
- **后果**: 未审批的调拨直接改库存，绕过 `submit` 流程与审批人；同时绕过 PDA 的「下架→扫目标库位」作业步骤（`PdaTransferServiceImpl.takeoff/shelf`），库位转移没有实际作业记录。
- **修复建议**: 抢占条件改为 `.in(WmTransfer::getStatus, STATUS_APPROVED, STATUS_TAKEN_OFF)`（PDA 下架后为 `TAKEN_OFF`），并把 PDA 侧的状态前置判断与 Service 保持同一白名单。
- **影响面**: `WmTransferServiceImpl`、`PdaTransferServiceImpl`；前端无需改动；需补测试。

### ERP-WM-08 已执行调拨单可改明细 / 删除
- **证据**: `WmTransferServiceImpl.java:129-139`（`updateByBo`：先 `deleteLinesByTransferId` 再重插，无状态校验）、`:149-154`（`deleteByIds` 无状态校验）、`:105-118`（`insertByBo` 未强制覆盖 `status`，而 `WmTransferBo.java:109` 有 `status` 字段）
- **触发路径**: 调拨单执行完成后 `PUT /erp/wm/transfer`（改数量/库位）或 `DELETE /erp/wm/transfer/{ids}`。
- **后果**: 明细被删改后与已生成的 `wm_material_stock_tx`（`source_doc_id` 指向该调拨单）不再对应，数量对不上、无法追溯；删除单据后流水成为孤儿，库存调整无凭据。`insertByBo` 若前端传 `status=EXECUTED`，还能造出一张「已执行但从未扣库存」的单据。
- **修复建议**: `updateByBo` 加 `only DRAFT/REJECTED 可改`、`deleteByIds` 加 `only DRAFT 可删`（对照 `WmMaterialReceiveCrudServiceImpl.java:186-190, 206-210` 的既有实现）；`insertByBo` 强制 `entity.setStatus(STATUS_DRAFT)`。
- **影响面**: `WmTransferServiceImpl`；无需 DDL。

### ERP-WM-09 销售出库单 CRUD 无状态守卫且 `status` 直传
- **证据**: `WmSalesIssueServiceImpl.java:138-146`（`insertByBo` 不覆盖 status）、`:156-162`（`updateByBo` 全删全插明细，无状态校验）、`:172-176`（`deleteByIds` 无校验）、`WmSalesIssueBo.java:99`（`private String status;`）
- **触发路径**: 出库单 `COMPLETED` 后调 `PUT /erp/wm/sales-issue`（改 `quantity_issue` 或删行）或 `DELETE /erp/wm/sales-issue/{ids}`；或新增时直接传 `status=COMPLETED`。
- **后果**: 已扣减库存的单据被改写/删除 → 库存流水与单据数量不一致（对账时找不到差异来源）；直造 `COMPLETED` 则出现「未扣库存但状态已完成」的出库单，`completeSalesIssue` 也不会再执行（状态不是 CONFIRMED）。
- **修复建议**: `insertByBo` 强制 `setStatus(STATUS_DRAFT)`；`updateByBo`/`deleteByIds` 参照 `WmMaterialReceiveCrudServiceImpl.java:186-190` 加状态白名单；`WmSalesIssueBo.status` 加 `@JsonIgnore` 或从 BO 移除。
- **影响面**: `WmSalesIssueServiceImpl`、`WmSalesIssueBo`；前端表单需确认不再依赖可写 status。

### ERP-WM-10 盘点计划 CRUD 与 `generateLines` 均无状态守卫
- **证据**: `WmStockTakingPlanServiceImpl.java:109-114`（`insertByBo` 不设默认 status，`WmStockTakingPlanBo.java:83` 有 `status`）、`:136-147`（`updateByBo` 全删全插行）、`:157-162`（`deleteByIds`）、`:172-178`（`generateLines` 先 `deleteLinesByPlanId` 再重建）、`WmStockTakingPlanController.java:132-138`
- **触发路径**: 对 `POSTED` 计划调 `PUT /erp/wm/stocktaking/generateLines/{planId}`（PC 表单「生成盘点明细」按钮）或 `PUT /erp/wm/stocktaking`。
- **后果**: 已过账计划的盘点行（`status=POSTED`、含差异数量）被删除重建为 `PENDING`、`actual_quantity=0` 的新行，账面差异记录消失；再点一次过账（见 ERP-WM-02）会把库存按 0 再调一次。`insertByBo` 直传 `status=POSTED` 还能伪造「已过账」单据。
- **修复建议**: `generateLines` 前置校验仅 `DRAFT/REJECTED` 可生成（PDA 侧 `PdaStocktakingServiceImpl.java:185` 已有该判断，PC 控制器漏了）；`updateByBo`/`deleteByIds` 加状态白名单；`insertByBo` 默认 `STATUS_DRAFT`。
- **影响面**: `WmStockTakingPlanServiceImpl`、`WmStockTakingPlanController`；无需 DDL。

### ERP-WM-11 盘点允许「审批中」直接过账
- **证据**: `WmStockTakingPlanServiceImpl.java:234-241`（`.in(status, "GENERATED", STATUS_APPROVING)`）、`:399-403`（`executeCountAdjust` 同一条件）
- **触发路径**: 盘点单 `submit` 后状态为 `APPROVING`，此时调 `PUT /erp/wm/stocktaking/postAdjustment/{planId}`。
- **后果**: 审批未通过即完成库存调整，审批流形同虚设；随后审批通过时 `executeCountAdjust` 因 CAS 已失败而跳过（`:404-407`），审批结果与库存调整脱钩。
- **修复建议**: `postAdjustment` 的手工通道只允许 `GENERATED`；`APPROVING` 仅保留给 `afterFlowHandle(pass)` 调用的 `executeCountAdjust`。
- **影响面**: `WmStockTakingPlanServiceImpl`；无需 DDL。

### ERP-WM-12 到货通知行无「已收数量」字段，收货数量闭环缺失
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmArrivalNoticeLine.java:42-53`（只有 `quantityArrival`、`quantityQuanlified`、`iqcCheck/iqcId/iqcCode/iqcStatus`，**无 received/已收字段**；全仓 grep `receivedQty|received_qty` 在 wm 包内仅命中 `WmSubcontractReceive`/`WmBatch`/`Pu` 相关）、`WmMaterialReceiveExecutorServiceImpl.java:165-171`（入库后无条件把通知单置 `FINISHED`）
- **现状**: 采购回写走的是 `PuPurchaseReceiveWritebackService.writeBackReceivedQty`（更新 `pu_purchase_order_line.received_quantity`，见 `mq/consumer/PurchaseReceiveWritebackConsumer.java:77`），但**到货通知行自身没有已收量**。
- **触发路径**: 一张到货通知 3 行、只收了其中 1 行 → 执行入库 → 通知单整体 `FINISHED`，其余 2 行没有任何「待收/已收」标记。
- **后果**: 到货通知→收货单→上架 的数量闭环只到单据级状态，无法回答「哪一行还差多少没到」；重复到货无法识别，也无法按通知行做差异统计（IQC 门禁 `confirmShelf` 依赖 `wm_arrival_notice_line.iqc_status`，但数量维度缺失）。
- **修复建议**: 给 `wm_arrival_notice_line` 增加 `received_qty`（decimal(16,6) DEFAULT 0，**需 DDL**），在 `WmMaterialReceiveExecutorServiceImpl.executeReceive` 中按物料/行累加回写，并把「整单 FINISHED」改为「全部行收满才 FINISHED，否则 PARTIAL_RECEIVED」。
- **影响面**: `WmArrivalNoticeLine`/`Vo`/DDL、`WmMaterialReceiveExecutorServiceImpl`、到货通知前端；需 DDL + 存量数据升级脚本。

### ERP-WM-13 单号并发重号 + 关键单据缺唯一索引
- **证据**: `WmSubcontractIssueServiceImpl.java:200-202`（`"WSCI-"+subcontractNo+"-"+ (selectCount(...)+1)`）、`WmSubcontractReceiveServiceImpl.java:96-98`（同型 `WSCR-`）、`script/sql/ry-erp.sql:7357-7359`（`wm_transfer` 只有 PK + `idx_transfer_status`/`idx_tenant_id`，**无 `transfer_no` 唯一索引**；对照 `wm_sales_issue` 有 `UNIQUE INDEX uk_issue_code(issue_code, tenant_id)` 见 `:6699`）
- **触发路径**: 同一委外订单并发创建两张发料单（或收货单）；调拨单并发创建/重复提交。
- **后果**: 单号重复 → 业务上无法用单号定位单据，条码/打印/对账错乱；且因无 DB 唯一约束，重复不会报错，静默产生脏数据。
- **修复建议**: 单号改用已有的 `wm.autoCodeUtil.genSerialCode(...)`（`WmTransferServiceImpl.java:108` 已是此模式）或 `DailyCodeSequenceProvider`；`script/sql/ry-erp.sql` 给 `wm_transfer(transfer_no, tenant_id)`、`wm_subcontract_issue(issue_no, tenant_id)`、`wm_subcontract_receive(receive_no, tenant_id)` 补唯一索引（**需 DDL + 存量去重**）。
- **影响面**: 两个委外 Service、DDL、`doc/system/数据库增量变更记录.md`。

### ERP-WM-14 调拨未校验目标仓库/库位，目标库存维度可落 NULL
- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/strategy/impl/TransferStrategy.java:120-126`（`toLqw.eq(line.getToWarehouseId() != null, ...)`、`eq(line.getToLocationId() != null, ...)`，为空时**不加过滤**）、`:141-149`（新建目标库存行直接用 `line.getToWarehouseId()/getToLocationId()`）、`WmTransferServiceImpl.java:191-200`（从单头填充，未校验非空）
- **触发路径**: 调拨单只填了来源仓库/库位，目标仓库或目标库位为空 → 执行调拨。
- **后果**: ①新建的目标库存行 `warehouse_id`/`location_id` 为 NULL，按仓库/库位查询、3D 库位视图（`WmMaterialStockMapper.aggregateStockByWarehouse`）都看不到这批库存；②`toLqw` 不加仓库过滤时会命中**任意仓库**里同物料同批次的第一条库存行并直接加数量，导致跨仓错账。
- **修复建议**: `WmTransferServiceImpl.execute` 前置校验 `toWarehouseId`、每行 `toLocationId` 非空（跨仓调拨必须有目标库位）；`TransferStrategy` 的目标查询把仓库/库位改为强制条件（`eq` 而非条件 `eq`）。
- **影响面**: `TransferStrategy`、`WmTransferServiceImpl`、`WmTransferBo` 校验注解；无需 DDL。

### ERP-WM-15 出库预留释放的两套口径可能不等
- **证据**: `WmSalesIssueServiceImpl.java:280-281`（`totalConsumeReserved = issueQty.min(soReservedQty)`）、`:294-304`（逐库存行 `batchConsumeReserved = consumeQty.min(stockReserved).min(remainingConsumeReserved)`）、`:343-351`（SO 行按 `totalConsumeReserved` 原子递减）、`SalesOutStrategy.java:132,150-151,175-184`（同型逻辑）
- **现状**: SO 行 `reserved_qty` 扣的是 `totalConsumeReserved`，库存行 `reserved_quantity` 扣的是各行 `batchConsumeReserved` 之和；当所扣库存行的 `reserved_quantity` 之和小于 SO 预留量时，两者之差不会被释放，代码也不校验 `remainingConsumeReserved == 0`。
- **触发路径**: SO 行预留 100，但预留实际落在批次 A（`reserved_quantity=40`）与批次 B（`reserved_quantity=0`）；出库 100 时先扣 A（释放 40）再扣 B（释放 0）→ SO 行减 100，库存行合计只释放 40。
- **后果**: 库存行残留 `reserved_quantity=60`，`stock_quantity - reserved_quantity` 的可用量虚减 60，该批库存永远「假占用」，后续 ATP 校验（`WmProductionIssueServiceImpl.sumAvailable`）与拣货都取不到，且释放预留接口（`WmStockReservationServiceImpl.unreserve` 按 RESERVE 流水释放）也对不上账。
- **修复建议**: 扣减循环结束后校验 `remainingConsumeReserved.compareTo(ZERO) == 0`，否则把差额用 `releaseReserved` 补释放（或直接抛异常回滚）；SO 行递减量改用实际 `totalConsumeReserved - remainingConsumeReserved`。
- **影响面**: `WmSalesIssueServiceImpl`、`SalesOutStrategy`；无需 DDL；需补测试（跨批次预留消耗）。

### ERP-WM-16 `confirmSalesIssue` 可绕过审批确认出库单
- **证据**: `WmSalesIssueServiceImpl.java:191-195`
- **现状**: 校验为 `!DRAFT.equals(status) && !APPROVED.equals(status)` 则拒绝 —— 即 `DRAFT` 直接允许置为 `CONFIRMED`，而 `CONFIRMED` 是 `completeSalesIssue`（`:211`）的唯一前置状态。
- **触发路径**: 新建出库单（DRAFT）→ 不提交审批 → `PUT /erp/wm/sales-issue/confirm/{id}`（前端 `sales-issue/index.ts:43`）→ `PUT .../complete/{id}`。
- **后果**: 出库完全绕过审批流程（对比入库：`confirmShelf` 只允许 `APPROVED`，`WmMaterialReceiveCrudServiceImpl.java:329`）；且 `:194-195` 是读-改-写 `updateById`，并发双击可同时通过。
- **修复建议**: `confirmSalesIssue` 只允许 `APPROVED → CONFIRMED`，并改成条件更新 CAS（`.eq(status, APPROVED)`，影响 0 行抛异常）。
- **影响面**: `WmSalesIssueServiceImpl`；前端按钮显隐需按状态调整。

### ERP-WM-17 `confirmShelf` 无 CAS（PDA 与 PC 并发）
- **证据**: `WmMaterialReceiveCrudServiceImpl.java:329`（读校验 `APPROVED`）、`:403-407`（`updateById` 写 `SHELVED`）；PDA 侧 `PdaReceiveServiceImpl.java:262` 复用同一方法
- **触发路径**: PDA 点「上架确认」与 PC 点「上架确认」同时到达，或网络重试。
- **后果**: 两次都通过状态校验、都执行 `updateById`，`shelf_by/shelf_time` 被后写覆盖；虽然 `SHELVED` 是幂等终态，但若与 `executeReceive`（`:104-110` 有 CAS）交错，可能出现上架确认与入库执行的状态判断不一致（入库已认领 EXECUTING 时又被写回 SHELVED 之外的状态）。
- **修复建议**: 与 `executeReceive` 一致改为条件更新 `.eq(WmMaterialReceive::getStatus, STATUS_APPROVED).set(status, STATUS_SHELVED)`，影响 0 行抛「状态已变更」。
- **影响面**: `WmMaterialReceiveCrudServiceImpl`；无需 DDL。

## 3. P2 详述

| 编号 | 类型 | 结论 | 证据 |
|---|---|---|---|
| ERP-WM-P2-01 | 校验 | 委外发料/收货 `add` 端点的 `@RequestBody` 未加 `@Validated`（类级 `@Validated` 对参数对象内部约束不生效），BO 上的 `@NotNull` 等不触发；同型见 `WmBatchController.java:112`、`WmSnController.java:101,113,135`、`AppPdaIssueController.java:66` | `WmSubcontractIssueController.java:81-82`、`WmSubcontractReceiveController.java:67-68` |
| ERP-WM-P2-02 | 幂等 | PDA 调拨 `takeoff` 对已下架行返回 `fail("该行已下架")` 而非幂等成功，断网重试会被判为错误；`line.setTakeoffQty` 无 CAS | `PdaTransferServiceImpl.java:117-130` |
| ERP-WM-P2-03 | 字段 | 出库行冗余字段 `quantity_delivered` 从不回写（只回写 SO 行），列表展示的「已交付」恒为 0 | `ry-erp.sql:6729`、`WmSalesIssueServiceImpl.java:353-356` |
| ERP-WM-P2-04 | 状态机 | `WmMaterialReceive` 的 `updateWithValidation`（`:224-255`）按前端传入的 `bo.getStatus()` 决定走哪套校验，且随后 `updateById` 直接落库该 status，可被构造为任意状态 | `WmMaterialReceiveCrudServiceImpl.java:241-254` |
| ERP-WM-P2-05 | 契约 | 销售出库拣货策略（`IPickingResolutionService`/`PickingStrategyFactory`）未接入 `completeSalesIssue`（它内联了 FEFO 查询），拣货规则配置对销售出库不生效 | `WmSalesIssueServiceImpl.java:249-258`、`PickingResolutionServiceImpl.java:55` |
| ERP-WM-P2-06 | 性能 | 入库执行后逐行逐明细 `updateById` 只为了刷 `update_time`（N+M 次更新），大单会明显拖慢事务 | `WmMaterialReceiveExecutorServiceImpl.java:149-162` |
| ERP-WM-P2-07 | 校验 | `WmStockTakingPlanServiceImpl.insertByBo` 不设默认 status（`:110-114`），与其它单据（`WmTransferServiceImpl.java:110`、`WmProductionIssueServiceImpl.java:148`）不一致 | `WmStockTakingPlanServiceImpl.java:109` |
| ERP-WM-P2-08 | 死代码 | `IWmStockOperationService.postSalesIssue` / `SalesOutStrategy` 无调用方（与 ERP-WM-01 同根因，单独列出供清理） | `IWmStockOperationService.java:30`、`WmStockOperationServiceImpl.java:58` |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 到货通知行「已收数量」回写与部分收货状态 | `WmArrivalNoticeLine.java:42-53` 无 received 字段；`WmMaterialReceiveExecutorServiceImpl.java:165-171` 整单 FINISHED | 通知单行表已有 `quantity_arrival`/`quantity_quanlified`；`WmArrivalNoticeLineServiceImpl.updateIqcResult`（`:117`）可作回写范式 | 0.5~1 天（DDL + 回写 + 前端列） |
| 2 | 委外发料/收货单审批流 | 两个 Controller 只有 list/get/suggest/create/delete/post，无 `submit`；状态枚举仅 `DRAFT`/`POSTED` | `WmSubcontractIssue/Receive` 均有 `select_flow_id` 同型字段缺；ERP 其它单据的 `processService.startProcessByDataId` 范式齐备 | 1 天 |
| 3 | 销售出库拣货规则/拣货分配落地 | `completeSalesIssue` 内联 FEFO 查询，未用 `IPickingResolutionService`/`PickingStrategyFactory` | `PickingResolutionServiceImpl`、`FefoPickingStrategy`、`FifoPickingStrategy`、`WmPickingRule` 均已实现 | 0.5~1 天 |
| 4 | 出库 OQC 结果回写 | 仅 `registerOqcCreateMessages`（`:454`）发消息给 MES 建 OQC，无消费端回写 `wm_sales_issue` 的检验结论 | `WmArrivalNoticeLine.iqc_status` 有收货侧范式可复制 | 1 天 |
| 5 | 盘点差异审批通过后自动过账的开关/提示 | `executeCountAdjust`（`:397`）已存在但仅在流程回调里；PC 页面同时暴露手工过账按钮（`form.vue:465`），二者可互相抢占 | `WmStockTakingPlanServiceImpl.executeCountAdjust` | 0.5 天（去掉手工按钮或加状态门禁） |

## 5. 已核查确认无问题（证明覆盖范围）

- 库存扣减/增加全部走原子 SQL + 乐观锁 + 非负守卫：`WmMaterialStockMapper.java:46-51`（`stock_quantity >= #{deductQty} AND version = #{version}`）、`:61-66`、`:98-108`（`deductStockWithReserved` 同时守卫 `stock_quantity >= outQty` 与 `reserved_quantity >= consumeReservedQty`）。未发现 Java 先算后写的库存写库。
- 入库执行有 CAS + 流水幂等双保险：`WmMaterialReceiveExecutorServiceImpl.java:104-110`（`SHELVED→EXECUTING`）、`:113-115` + `hasPosted(:219-222)`。
- 上架确认的 IQC 门禁已按「有/无到货通知」两分支收紧，无到货通知时按单据 `iqc_status` 拦截非 QUALIFIED：`WmMaterialReceiveCrudServiceImpl.java:359-401`（复核 09-06 报告 E5~E10 中 IQC 放行项，**已修复**）。
- 盲盘支持完整：`PdaStocktakingServiceImpl.java:79-82`（`OPEN/BLIND` 校验）、`:154`、`:169-170`（盲盘不下发账面数），前端 `PdaStocktakingDetailVo` 已按 `blind` 置 `bookQty=null`。
- 委外发料/收货有订单级 `Lock4j` 锁 + CAS 认领（锁覆盖整个事务）：`WmSubcontractIssueServiceImpl.java:250-258`、`:271-279`；`WmSubcontractReceiveServiceImpl.java:159-167`、`:180-188`。
- 委外收货有超收校验（对照本报告 ERP-WM-03 的收货缺失）：`WmSubcontractReceiveServiceImpl.java:82-88`。
- 库存预留不足显式抛异常、释放预留带 3 次重试且失败即回滚：`WmStockReservationServiceImpl.java:74-78`、`:106-121`。
- 委外发料的备料建议已按 FEFO/FIFO 且排除冻结与已预留：`WmSubcontractIssueServiceImpl.java:120-127`。
- 生产领料执行前做 ATP 可用量校验（不按头部仓库过滤、与策略扣减范围一致）：`WmProductionIssueServiceImpl.java:259-266`、`:587-603`。
- PDA 控制器权限齐备、无免登录通道：`wm/controller/app/**` 全部方法带 `@SaCheckPermission`（如 `AppPdaReceiveController.java:42,49,56,65,74`），全包无 `@SaIgnore`/`@Anonymous`（grep `@SaCheckPermission|@SaIgnore|@Anonymous` 命中 248 处，无豁免项）→ 默认 fail-closed。
- 使用的权限码在 `sys_menu` 有种子：`wm:receive:execute`（`script/sql/ry-vue.sql:96731`）、`wm:salesIssue:complete`（`:96743`）、`wm:stocktx:list`（`:96745`）、`wm:materialReturn:*`（`:92796-92801`）、`wm:basedata:sync`（`:92811`）、`prd:subcontract:issue/receive`（`:92343`、`:92344`）、`wm:transfer:*`/`wm:stocktaking:*`/`wm:reservation:*`（`:92049-92060`、`:92167-92172`）。
- 前后端端点对应（抽查）：`sales-issue/index.ts:43,50,59,67`、`transfer/index.ts:19,23,31`、`stocktaking/index.ts:20,22,30`、`receive/index.ts:43,51,58` 均有后端对应端点；委外发/收货前端在 `plus-ui/src/api/erp/prd/subcontract/index.ts:92-129`（`/erp/wm/sc-issue`、`/erp/wm/sc-receive`）与 `WmSubcontractIssueController.java:28`、`WmSubcontractReceiveController.java:27` 一致，未发现「后端有端点但前端无函数」。
- MQ 租户上下文：入库回写 `WmMaterialReceiveExecutorServiceImpl.java:197-203`、出库 OQC `WmSalesIssueServiceImpl.java:490-500`、收货审批 `WmMaterialReceiveCrudServiceImpl.java:685-712` 均已显式取 `TenantHelper.getTenantId()` 后传参（复核 09-06 报告 §8 的 tenantId 缺陷，**已修复**）。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `WmTransferBo.status` | `WmTransferBo.java:109` 可写 | 前端 `transfer` 表单未提交 status（未发现写值） | 后端可被构造直传，建议后端忽略 |
| `WmSalesIssueBo.status` | `WmSalesIssueBo.java:99` 可写 | 未提交 | 同上，风险见 ERP-WM-09 |
| `WmStockTakingPlanBo.status` | `WmStockTakingPlanBo.java:83` 可写 | `stocktaking/form.vue` 未提交 | 同上，风险见 ERP-WM-10 |
| `WmStockTakingPlan.countMode` | 实体/BO 有 `countMode`（PDA 侧使用，`PdaStocktakingServiceImpl.java:79-91`） | PC `views/erp/wm/stocktaking` 无 `countMode` 字段（grep 仅命中 `form.vue:233,459,473` 的 API 调用） | PC 端无法选择盲盘/明盘，盲盘仅 PDA 可建 |
| `WmSalesIssueLine.quantityDelivered` | `ry-erp.sql:6729` 有列 | 列表/详情展示该字段但后端从不回写 | 恒为 0，见 ERP-WM-P2-03 |
| 到货通知行已收量 | 后端无字段 | 前端也无 | 双方都缺，见功能缺失 #1 |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**：
  - ERP-WM-01 在 `completeSalesIssue` 扣减处补 `valuationService.issueStockCost` 并把成本写入流水；删除死路径 `postSalesIssue`（或改为唯一入口）。
  - ERP-WM-02 `postAdjustment` 增加「存在未录入实盘数的行则拒绝」校验，`generateLines` 初始实盘数改 `null`（含 DDL）。
  - ERP-WM-03 `PdaReceiveServiceImpl.confirmLine` 增加超收上限 + 条件更新累加。
- **批次 B（P1，按单据分组提交）**：
  - B1 状态机统一：ERP-WM-07/08/09/10/11/16 + ERP-WM-P2-04（抽一个 `assertStatusIn(...)` 工具或在各 Service 加白名单/CAS）。
  - B2 并发与幂等：ERP-WM-05、ERP-WM-17。
  - B3 数量与维度正确性：ERP-WM-04、ERP-WM-06、ERP-WM-12、ERP-WM-14、ERP-WM-15。
  - B4 DDL：ERP-WM-13（单号唯一索引 + 存量去重）、ERP-WM-12（`received_qty` 列）、ERP-WM-02（`actual_quantity` 允许 NULL）。
- **批次 C（P2）**：ERP-WM-P2-01~08（含给委外 add 端点补 `@Validated`、清理 `updateWithValidation` 的 status 直传）。

## 8. 未覆盖范围

- `wm` 下**库存内核**（`WmMaterialStockServiceImpl`、`WmBatchServiceImpl`、`WmMaterialStockTxServiceImpl`、`MovingAverageValuationServiceImpl`、`WmStockAlertServiceImpl`、`PutawayStrategyServiceImpl`、`PickingResolutionServiceImpl`、`WmBarcode*`、`WmSnServiceImpl`、`WmTraceServiceImpl`）按分工归属另一代理，本报告只在被单据路径调用处引用，未做独立审计。
- `wm/report/**` 仅通过文件名/行数清点（`WmInventoryReportController` 67 行、`WmInventoryReportServiceImpl` 105 行），未逐行审计其 SQL 租户隔离。
- `WmMaterialHandover*`（交接单）、`WmMiscIssue/Recpt*`（杂项）、`WmMaterialReturn` 的 PC 建单 Service 只做了权限/接口可达性与 PDA 路径抽查，未做完整状态机审计。
- 委外收货「达量自动完工」的 `PrdSubcontractClosureServiceImpl.accumulateActualQty`（`:375-385`）属 `prd` 包，只核对了调用点与 SQL 无上限守卫（与 ERP-WM-06 同型），未展开审计 `prd` 侧。
- 09-06 报告 §2.2 E5~E10 中「退货/采购退货/入库转储原子化」「返工出入库参数错位」未逐条复核（返工策略 `ReworkIssueStrategy`/`ReworkReceiveStrategy` 不在本次精读清单内）。
- 未运行任何编译/测试；所有结论基于静态阅读。
