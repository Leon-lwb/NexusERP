# ERP 采购 / 生产计划 / 基础数据（pu-prd-bd）修复报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-erp-pu-prd.md`（ERP-PU-01..07、ERP-PRD-01/02/04/05/07）、`doc/audit/2026-09-20-audit-erp-bd.md`（ERP-BD-01/02/03/06..10） | 分支: `feature/erp-mes-iot-audit-fix`
> 修复规范: `doc/audit/2026-09-20-修复执行规范.md`

---

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| **ERP-PU-01**（P0） | ①回写补「已收 + 本次 ≤ 订单量 × (1+超收容差)」Java 守卫 + SQL 侧 `apply` 双保险（容差常量 `OVER_RECEIVE_TOLERANCE = 0`，按既有业务约定不允许超收）；影响 0 行即抛 `ServiceException`。②到货通知行 `quantityArrival` 改为**剩余未收量**（订单量 − 已收，下限 0）；③自动生成的收货行 `quantityReceived` 改为 **0**，由仓库按实收填写（不再把订单量预填为收货量） | `pu/service/impl/PuPurchaseReceiveWritebackServiceImpl.java:51-160`、`pu/service/impl/PuPurchaseReceivingServiceImpl.java:126-140,186-215` | 否 | `PuPurchaseReceiveWritebackServiceImplTest#writeBack_overReceive_rejected`、`#writeBack_exactQuantity_succeedsAndWritesIdempotentKey` |
| **ERP-PU-02**（P0） | 幂等键不再在事务入口占位：入口只做**只读判重**（`RedisUtils.getCacheObject`），业务写库全部成功后在 `TransactionSynchronization.afterCommit` 才 `setObjectIfAbsent`（无事务时直接写）。回写失败/回滚不留占位键 → 可重试。`purId == null` 由「静默 return」改为**抛异常**交给 MQ 重投 | `pu/service/impl/PuPurchaseReceiveWritebackServiceImpl.java:60-70,157-176` | 否 | `#writeBack_failed_doesNotOccupyIdempotentKey`、`#writeBack_blankPurId_throwsInsteadOfSilentSkip` |
| **ERP-BD-02**（P0） | `.eq(BdMaterial::getStatus, "ACTIVE")` → `.eq(BdMaterial::getStatus, 1L)`（与 `WmStockAlertServiceImpl:154` 一致），恢复低库存补货只命中启用物料 | `pu/service/impl/PuPurchaseRequisitionServiceImpl.java:453-458` | 否 | `PuPurchaseRequisitionServiceImplTest#generateFromLowStock_materialStatusComparedNumerically`（断言 wrapper 参数含 `1L` 且不含 `"ACTIVE"`） |
| **ERP-BD-01**（P0） | `BdBomDetailBo` / `BdBomDetailVo` 补 `supplyType`（BO 带 `@Size(max=32)`），打通 BO→实体→VO 三向；`buildQueryWrapper` 增加 `supplyType` 等值筛选。确认下游 `PrdSubcontractClosureServiceImpl:181` 读的就是实体字段，写入路径打通后快照可取到真实值（不再是恒定 `SELF_PROVIDED`） | `bd/domain/bo/BdBomDetailBo.java:75-82`、`bd/domain/vo/BdBomDetailVo.java:94-98`、`bd/service/impl/BdBomDetailServiceImpl.java`(buildQueryWrapper) | 否（列已存在） | 编译期字段打通；前端联动见 §3「前端需求」 |
| **ERP-BD-03**（P0 单行级） | `selectById` 改为先 `selectVoById` 判空返回 `null`，再用 `new ArrayList<>(List.of(vo))` 富集，消除 `List.of(null)` NPE | `bd/service/impl/BdBomDetailServiceImpl.java:73-83` | 否 | `BdBomDetailServiceImplTest#selectById_notFound_returnsNullWithoutNpe` |
| **ERP-BD-07**（P1） | `updateByBo` 中当 `isCurrent == 0` 时调用 `assertNotLastCurrentVersion`：若目标 BOM 原本是当前版本且同父项无其它当前版本 → 抛 `ServiceException`，保住「至少一个当前版本」不变量 | `bd/service/impl/BdBomServiceImpl.java:303-345` | 否 | 见 §2（未单独补测，已由代码路径覆盖；建议后续补） |
| **ERP-BD-08**（P1） | 抽出 `assertNoOpenWorkOrderReference(bomIds)`，在 `BdBomDetailServiceImpl.saveBatch` / `updateByBo` / `deleteByIds` 三处补与整表删除一致的「未完成工单引用」守卫 | `bd/service/impl/BdBomDetailServiceImpl.java:104-190` | 否 | `BdBomDetailServiceImplTest#saveBatch_openWorkOrderReference_rejected`、`#saveBatch_noWorkOrderReference_succeeds` |
| **ERP-BD-09**（P1） | **未修复**（跨所有权，见 §2）：`CreditCheckServiceImpl` 在 `so` 包，本批次不可编辑。唯一键 `uk_client_tenant` 已由 DDL 批次补上，重复行问题已从根上收口 | — | 已由 DDL 批次处理 | — |
| **ERP-BD-10**（P1） | ①`BdMaterialServiceImpl.selectByCategoryIds`（分类树选择器语义）强制 `.eq(status, 1L)`；②`BdBomDetailServiceImpl` 在 `insertByBo`/`updateByBo`/`saveBatch` 前校验子项物料存在且启用，否则抛可读异常 | `bd/service/impl/BdMaterialServiceImpl.java:139-152`、`bd/service/impl/BdBomDetailServiceImpl.java:196-224` | 否 | `BdBomDetailServiceImplTest#insertByBo_disabledMaterial_rejected` |
| **ERP-PU-03**（P1） | `execute` 先校验 `STATUS_CONFIRMED`，CAS 条件由 `.eq(status, 当前状态)` 改为 `.eq(status, STATUS_CONFIRMED)`；草稿单直接执行 → 抛「只有已确认状态…」 | `pu/service/impl/PuPurchaseReturnServiceImpl.java:186-206` | 否 | `PuPurchaseReturnServiceImplTest#execute_draftStatus_rejected`、`#execute_confirmedStatus_allowed`、`#execute_casConflict_throws` |
| **ERP-PU-04**（P1） | 四类单据服务端强制状态机：`insertByBo` 无条件 `setStatus(DRAFT)`（订单/退货/申请）；`updateByBo` 显式 `entity.setStatus(null)`（updateById 忽略 null，客户端 status 不再落库） | `pu/service/impl/PuPurchaseOrderServiceImpl.java:157-166,186-192`、`PuPurchaseReturnServiceImpl.java:110-124,133-148`、`PuPurchaseRequisitionServiceImpl.java:141-180` | 否 | `PuPurchaseReturnServiceImplTest#insertByBo_clientStatus_forcedToDraft`、`#updateByBo_clientStatus_notPersisted` |
| **ERP-PU-05**（P1） | ①`PuPurchaseOrderLine` 补 `returnedQuantity` 字段；②`execute` 内按 `orderLineId` 用 `setSql` 原子累加 `returned_quantity = IFNULL(returned_quantity,0) + qty`；③增量脚本补列 + 存量 EXECUTED 退货单回填 | `pu/domain/PuPurchaseOrderLine.java:59-67`、`pu/service/impl/PuPurchaseReturnServiceImpl.java:209-232`、`script/sql/update/2026-09-20-pu-prd-bd.sql` | **是** | 见 §4 DDL 登记 |
| **ERP-PU-06**（P1） | ①收货行落 `purchaseLineId`（到货通知行无该列，改为在 `createMaterialReceiveFromNotice` 内按物料在订单内**唯一匹配**解析；多行/未匹配留空，由回写阶段给可读错误）；②回写兜底匹配由 `selectOne` 改 `selectList`，多行时抛「存在多行，无法确定回写目标」而不是 `TooManyResultsException` | `pu/service/impl/PuPurchaseReceivingServiceImpl.java:186-232`、`PuPurchaseReceiveWritebackServiceImpl.java:178-205` | 否（`wm_material_receive_line.purchase_line_id` 列已存在） | `PuPurchaseReceiveWritebackServiceImplTest#writeBack_ambiguousMaterialLine_throwsReadableError` |
| **ERP-PU-07**（P1） | 订单头状态汇总由「`selectById` + 整实体 `updateById`」改为**条件更新**：`.set(status, newStatus).eq(orderId).notIn(status, "CLOSED","CANCELLED")`，影响 0 行只记 warn，不再复活已关闭/已作废订单 | `pu/service/impl/PuPurchaseReceiveWritebackServiceImpl.java:222-252` | 否 | `#writeBack_exactQuantity_succeedsAndWritesIdempotentKey`（覆盖条件更新路径） |
| **ERP-PRD-01**（P1） | 抽出共用口径类 `BomExplosionCalculator`（`calculateChildQuantity`：含损耗率 + CEILING；`resolveRequiredDate`：父层需求日期 − 提前期**小时**），两条入口（计划驱动 / 手工导入 BOM 明细）统一调用；`PrdMaterialRequirementServiceImpl.resolveRequiredDate` 标注 `@Deprecated` 并委托共用实现 | 新增 `prd/util/BomExplosionCalculator.java`；`prd/service/impl/PrdMaterialRequirementServiceImpl.java:344-352,486-497`；`PrdProductionPlanServiceImpl.java:715-733,884-897` | 否 | `BomExplosionCalculatorTest`（6 例：损耗率取整、精确乘积、空参、小时倒排、无提前期、无父层基准） |
| **ERP-PRD-02**（P1） | ①`insertByBo` 强制 `reportStatus = SUBMITTED`；②审核改为 **CAS**：`.set(APPROVED).eq(reportId).ne(reportStatus, APPROVED)`，抢不到返回 0 且不回写；③`updatePrdWorkReport` 不再绕过回写，APPROVED 路径统一走 CAS；④补超报工上限（Java 侧 `assertNotOverReport` + SQL 侧 `IFNULL(completed_qty,0) + qty <= planned_qty × (1+容差)`，容差 0） | `prd/service/impl/PrdWorkReportServiceImpl.java:120-240` | 否 | `PrdWorkReportServiceImplTest`（5 例：强制 SUBMITTED、超报拒绝、CAS 未抢到不双累加、CAS 抢到累加、`updatePrdWorkReport` 走 CAS） |
| **ERP-PRD-04**（P1） | `ReworkStockConsumer.doProcess` 不再只 `log.info`：新增 `IPrdReworkOrderService.advanceByStockOperation(reworkId, operationType, quantity)` 并真正落库。`ISSUE`：DRAFT → IN_PROGRESS（条件更新，重复投递幂等）；`RECEIVE`：实收 ≥ 返工数量才 IN_PROGRESS → COMPLETED，未达量保持进行中 | `mq/consumer/ReworkStockConsumer.java:44-55`、`prd/service/IPrdReworkOrderService.java`、`prd/service/impl/PrdReworkOrderServiceImpl.java`(advanceByStockOperation) | 否 | `PrdReworkOrderServiceImplTest#advanceByStockOperation_issue_advancesToInProgress`、`#issueRepeated_idempotent`、`#receivePartial_keepsInProgress`、`#receiveFull_completes` |
| **ERP-PRD-05**（P1） | 返工/委外状态流转全部改**条件更新**：`PrdReworkOrderServiceImpl.start/complete`（`complete` 同时把来源工单 `scrap_qty` 由读-改-写改为 `setSql` 原子累加，CAS 抢到才回写）；`PrdSubcontractServiceImpl.release/start/complete/close` 改 CAS，影响 0 行抛可读异常。委外收货口径统一为「达量才完工」 | `prd/service/impl/PrdReworkOrderServiceImpl.java:147-330`、`prd/service/impl/PrdSubcontractServiceImpl.java:160-260` | 否 | `PrdReworkOrderServiceImplTest#start_casConflict_throws`、`#complete_casWon_accumulatesSourceWoScrap`、`#complete_casConflict_noWorkOrderWrite` |
| **ERP-PRD-07**（P1） | MRP `queryTotalStock` 补 `.eq(WmMaterialStock::getFrozenFlag, 0L)`，冻结库存不再计入可用量，与低库存采购申请口径一致 | `prd/service/impl/PrdMaterialRequirementServiceImpl.java:472-480` | 否 | 见 §2（建议后续补数值级用例） |
| **ERP-PRD-03** | **不属本批次**（前端）：后端端点已存在，缺前端 api 函数与按钮 → 见 §3「前端需求」 | — | 否 | 不适用 |

---

## 2. 未修复 / 部分修复项（含理由与建议）

| 编号 | 状态 | 理由与建议 |
|---|---|---|
| **ERP-BD-09** | 未修复（跨所有权） | 修复点是 `ruoyi-erp/.../erp/so/service/impl/CreditCheckServiceImpl.java:47`（`selectOne` 在多行时抛 `TooManyResultsException`），`so` 包由其他代理独占，本批次禁止编辑。**唯一键 `bd_client_credit.uk_client_tenant` 已由 DDL 批次补上**，重复行写入已被 DB 拦住，500 的触发面基本消除。建议由 `so` 批次补一条 `selectList` + 取最新 + `log.warn` 的容错，或直接把 `creditLimit` 改为 `BigDecimal` 口径（额度列 `int(11)` → `decimal(18,2)` 需 DDL）。 |
| **ERP-BD-07 测试** | 部分（已修代码，未补独立用例） | 代码守卫已落地（`assertNotLastCurrentVersion`）。未补独立单测是因为 `BdBomServiceImpl` 构造依赖较多，且本批次 step 预算已用于 P0 用例；建议后续在 `bd` 包补「取消唯一当前版本应报错」用例。 |
| **ERP-PRD-07 测试** | 部分（已修代码，未补数值级用例） | `queryTotalStock` 为 `private`，需通过 `calculateNetRequirements` 间接断言（冻结库存行不计入 `stockQuantity`）。建议后续补「冻结 80 / 在库 100 → 可用按 20 计」的数值级用例。 |
| **ERP-PU-06 仓库/批次维度** | 部分修复 | `warehouseId` / `batchId` 无法在服务端派生：到货通知（`WmArrivalNotice`）与采购订单都没有仓库/批次来源，且 `wm/domain/**` 属其他代理独占，不能给到货通知行加列。当前自动生成的收货单仍为 `warehouseId = null`（DRAFT，待仓库收货时填写）。**建议**：由 `wm` 批次给 `wm_arrival_notice` / `wm_arrival_notice_line` 补 `warehouse_id`、`batch_id`（DDL）并在收货联动时透传；或前端在收货单草稿上强制选择仓库。 |
| **ERP-PU-06 到货通知行 `purchase_line_id`** | 替代实现 | 审计建议给 `wm_arrival_notice_line` 加 `purchase_line_id`（DDL）。因该文件属 `wm` 所有权，改为在收货行生成时按物料唯一匹配解析订单行，达到同等效果（回写按行而非按物料）。若后续由 `wm` 批次加列，可把解析改为直接透传。 |
| **ERP-PRD-05 委外收货口径** | 部分修复 | 已删除「只要 `IN_PROGRESS` 就置 `COMPLETED`」的语义（`SubcontractStockConsumer` 属其他代理范围未改；但 `PrdReworkOrder` 侧已按达量判定）。`SubcontractStockConsumer` 的 RECEIVE→COMPLETED 分支仍存在，**建议由 `mq/consumer` 负责方**统一改为调用 `PrdSubcontractClosureServiceImpl.accumulateActualQty` 的达量判定。 |
| **ERP-PU-09 / ERP-PRD-06 / ERP-PRD-08 / ERP-PRD-09 / ERP-PRD-10 / ERP-PU-08** | 未修复（P2，不在本批次范围） | 见审计报告 §3；ERP-PRD-06 的权限码对齐由权限种子批次处理（本批次未改 `ry-vue.sql`）。 |
| **ERP-BD-04 / ERP-BD-05 / ERP-BD-11..20** | 未修复（P1 大件 / P2） | 单位换算接线（BD-04）与批次序列号强制校验（BD-05）为跨 `wm`/`so`/`prd` 的大件改造，需与业务确认是否本轮支持；BD-11..20 属 P2 随手清理，未在本批次展开。 |
| **冻结库存与采购退货（B-wm 通报）** | 已核对，无需改动 | `PuPurchaseReturnServiceImpl.postReturnOut` 本就先 `.eq(WmMaterialStock::getFrozenFlag, 0L)` 选行，`deductStock` 新增的 `frozen_flag = 0` 守卫与之一致；仅剩冻结品时 `stock == null` → 抛「库存不足，无法执行采购退货」（可读提示，非静默失败）。若业务需允许退冻结品，改用 `deductStockIgnoreFrozen`。 |
| **MRP 可用量口径（B-wm 通报）** | 部分采纳 | 本批次按审计要求只补 `frozenFlag = 0` 过滤；**未**改为复用 `deductStockAvailable` 的「在库 − 已预留」口径，因为 `queryTotalStock` 是聚合查询（多库存行求和），不是单行扣减入口。建议后续抽公共「可用量查询服务」供 MRP / 低库存申请 / 排产共用（审计 §建议已提出）。 |

---

## 3. 主脚本合并清单（目标文件 + 锚点 + 待插入行）

> 以下内容**不在**本批次可编辑文件内，需主代理串行合并。

### 3.1 前端需求（交前端代理，`plus-ui/**`）

| # | 需求 | 位置 | 说明 |
|---|---|---|---|
| 1 | **ERP-BD-01**：BOM 明细行增加「供应方」下拉 | `plus-ui/src/api/erp/bd/bom/types.ts`、`plus-ui/src/views/erp/bd/bom/form.vue`（明细表格列 + 编辑控件） | 后端 `BdBomDetailBo/Vo.supplyType` 已打通（本批次）；字典 `bd_supply_type` 需补种子（见 3.2）。候选值：`SELF_PROVIDED` 自备 / `VENDOR_PROVIDED` 外协厂供 / `CUSTOMER_PROVIDED` 客供 |
| 2 | **ERP-PRD-03**：返工出库/入库按钮不可达 | `plus-ui/src/api/erp/prd/rework/index.ts` 补 `issueReworkOrder(reworkId)` → `PUT /erp/prd/rework/issue/{reworkId}`、`receiveReworkOrder(reworkId)` → `PUT /erp/prd/rework/receive/{reworkId}`；`plus-ui/src/views/erp/prd/rework/index.vue` 操作列加按钮 | 权限码用已种子的 `prd:rework:issue` / `prd:rework:receive`；后端无需改动 |
| 3 | **ERP-PU-01**：收货单草稿数量不再预填 | `plus-ui/src/views/erp/wm/receive/**` | 自动生成的收货行 `quantityReceived` 现在为 0，需仓库按实收填写；到货通知行 `quantityArrival` 现为「剩余未收量」，可作参考列展示 |
| 4 | **ERP-PU-06**：收货单/行仓库、批次维度 | `plus-ui/src/views/erp/wm/receive/**` | 自动生成的收货单 `warehouseId` 仍为空，需在收货草稿上强制选择仓库（与 wm 批次协商） |
| 5 | **ERP-PU-04**：单据表单 `status` 字段只读 | `views/erp/pu/order/form.vue`、`views/erp/pu/return/form.vue`、`views/erp/pu/requisition/form.vue` | 服务端已强制 `DRAFT` / 忽略客户端 status，前端应改为只读展示，避免用户误以为可改 |
| 6 | **ERP-PU-05**：采购订单行展示「已退货数量 / 净采购量」 | `views/erp/pu/order/**` | 后端新增 `returnedQuantity`；建议展示 `order_quantity - returned_quantity` 与 `received_quantity - returned_quantity`（可开票口径） |
| 7 | **ERP-PRD-02**：报工表单状态只读 | `views/erp/prd/report/**` | 新增报工强制 `SUBMITTED`；审核走 CAS，重复审核返回 0 不再报错 |

### 3.2 `script/sql/ry-vue.sql`（字典种子，锚点：现有 `bd_` 字典插入区）

在 `sys_dict_type` / `sys_dict_data` 中补 `bd_supply_type` 字典（锚点建议取现有 `bd_client_level` 字典所在行附近，`ry-vue.sql:89983-89987`）：

```sql
-- sys_dict_type
INSERT INTO sys_dict_type(dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
VALUES (<新雪花ID>, '000000', 'BOM供应方', 'bd_supply_type', 103, 1, NOW(), 'BOM明细供应方');

-- sys_dict_data
INSERT INTO sys_dict_data(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
(<ID1>, '000000', 1, '自备',     'SELF_PROVIDED',     'bd_supply_type', '', 'primary', 'Y', 103, 1, NOW(), '我方提供'),
(<ID2>, '000000', 2, '外协厂供', 'VENDOR_PROVIDED',   'bd_supply_type', '', 'warning', 'N', 103, 1, NOW(), '外协厂提供'),
(<ID3>, '000000', 3, '客供',     'CUSTOMER_PROVIDED', 'bd_supply_type', '', 'info',    'N', 103, 1, NOW(), '客户提供');
```

> 注：本批次未编辑 `script/sql/ry-vue.sql`（§1.1 绝对独占）。以上为待主代理合并内容。

---

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更 | 脚本 | 说明 |
|---|---|---|---|---|---|
| 2026-09-20 | `ry-erp` | `pu_purchase_order_line` | `ADD COLUMN returned_quantity decimal(18,4) NULL DEFAULT 0 COMMENT '已退货数量…' AFTER invoiced_qty` | `script/sql/update/2026-09-20-pu-prd-bd.sql` | ERP-PU-05：订单行退货数量回写，净采购量口径。脚本幂等（`information_schema` 判存在 + `PREPARE`）；附带存量回填：`status='EXECUTED'` 的退货单按 `order_line_id` 汇总回填仍为 0/NULL 的行 |
| 2026-09-20 | `ry-erp` | `bd_bom_detail` | `ADD COLUMN supply_type varchar(32) NULL DEFAULT 'SELF_PROVIDED'`（仅当列不存在） | 同上 | ERP-BD-01：主脚本 `ry-erp.sql:419` 已有该列，此处仅为更早期版本升级兜底 |
| 2026-09-20 | `ry-vue` | `sys_dict_type` / `sys_dict_data` | 新增字典 `bd_supply_type`（3 项） | 见 §3.2（待主代理合并到 `ry-vue.sql`） | ERP-BD-01 前端下拉依赖 |

**执行前提**：`ry-erp` 库；MySQL 8.0+；脚本可重复执行。**无需清 Redis 缓存**。

---

## 5. 验证记录

### 5.1 新增/修改测试类

| 测试类 | 用例数 | 覆盖审计编号 |
|---|---|---|
| `com.nexus.erp.pu.PuPurchaseReceiveWritebackServiceImplTest` | 6 | ERP-PU-01、ERP-PU-02、ERP-PU-06 |
| `com.nexus.erp.pu.PuPurchaseReturnServiceImplTest` | 5 | ERP-PU-03、ERP-PU-04 |
| `com.nexus.erp.pu.PuPurchaseRequisitionServiceImplTest` | 1 | ERP-BD-02 |
| `com.nexus.erp.bd.BdBomDetailServiceImplTest` | 4 | ERP-BD-03、ERP-BD-08、ERP-BD-10 |
| `com.nexus.erp.prd.PrdWorkReportServiceImplTest` | 5 | ERP-PRD-02 |
| `com.nexus.erp.prd.PrdReworkOrderServiceImplTest` | 7 | ERP-PRD-04、ERP-PRD-05 |
| `com.nexus.erp.prd.BomExplosionCalculatorTest` | 6 | ERP-PRD-01 |
| **合计** | **34** | |

全部新增测试类均带 `@Tag("dev")`（与仓库 `pom.xml` 的 `<groups>${profiles.active}</groups>` 及既有 `BdRoutingServiceImplTest` 一致）；`@InjectMocks` 不注入继承的 `baseMapper` 场景（`BdBomDetailServiceImpl`、`PrdWorkReportServiceImpl`、`PrdReworkOrderServiceImpl`、`PuPurchaseReturnServiceImpl`、`PuPurchaseRequisitionServiceImpl`）均按显式 `private final` 字段名声明 `@Mock`；`MapstructUtils` 依赖的 `SpringUtil.getBean(Converter.class)` 按既有范式用 `mockStatic(SpringUtil)` + 真实 `Converter` 注入；Lambda 列缓存用 `TableInfoHelper.initTableInfo` 预热。

### 5.2 执行命令与结果

```powershell
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false `
  "-Dtest=PuPurchaseReceiveWritebackServiceImplTest,PuPurchaseReturnServiceImplTest,PuPurchaseRequisitionServiceImplTest,BdBomDetailServiceImplTest,PrdWorkReportServiceImplTest,PrdReworkOrderServiceImplTest,BomExplosionCalculatorTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

- **第 1 次运行**：编译失败，`PrdProductionPlanServiceImpl:873` 报 `Long` 无法转为 `Integer`（我引入的 `BomExplosionCalculator.resolveRequiredDate(Date, Integer)` 与 `MaterialRequirementCalcDTO.leadTime` 的 `Long` 不匹配）→ 已把形参改为 `Number` 并修正 `@link`，**该错误已消除**。
- **第 2、3 次运行**：`ruoyi-erp` **主源码**编译阶段报 28 处 `读取 target/generated-sources/annotations/...MapperImpl.java 时出错`（MapStruct 生成源被并发写入/读取），全部指向 `target/` 生成产物、**无一处指向本批次修改的源文件**。判定为**其他修复代理的并发 Maven 构建**同时写同一 `ruoyi-erp/target` 目录所致（多代理共享工作区，Maven 无隔离构建目录）。
- **第 4 次运行（最新）**：`main` 源码 **编译通过**（`PrdProductionPlanServiceImpl` 的 `Long/Integer` 类型错误已消除，主源码错误列表为空），失败点转移到 **`test-compile`**：
  ```
  [ERROR] .../src/test/java/com/nexus/erp/fin/GlVoucherServiceImplTest.java:[154,61] 错误: 不是 UTF-8 的未映射字符 (0xE380)
  [ERROR] .../fin/GlVoucherServiceImplTest.java:[133,24] 未结束的字符串文字
  [ERROR] .../fin/GlVoucherServiceImplTest.java:[199,56] 需要')'
  ```
  该文件属 **`fin` 批次（其他代理正在编辑中）**，是「未结束的字符串文字 / 非 UTF-8 字符」级语法错误，与 `@Tag` 无关；`testCompile` 是全量编译，因此**本批次的 7 个测试类无法参与编译**。
- **结论**：
  1. **本批次主源码（`main`）已确认编译通过**（第 4 次运行的错误列表中没有 `bd`/`pu`/`prd`/`mq` 任何文件）。
  2. **单元测试未能实际执行，未取得 `Tests run` 结果** —— 阻塞源有两个，均为其他代理的并发写入：①`ruoyi-erp/target/generated-sources` 被并发构建破坏（第 2/3 次）；②`fin/GlVoucherServiceImplTest.java` 处于编辑中间态（第 4 次）。
  3. **建议主代理在所有代理停止构建后串行重跑**上述命令（命令已校正，可直接照抄）。若重跑后出现本批次用例失败，请回传失败清单——各用例断言均为增量数值级，失败信息会直接指向断言点，可快速定位。

> 如主代理重跑后有用例失败，请把失败清单回传，我这边可立即定位（各用例断言均为增量数值级，失败信息会直接指向断言点）。

---

## 6. 风险与回滚说明

| 改动 | 改错的后果 | 回滚方式 |
|---|---|---|
| **ERP-PU-01 超收守卫（容差 0）** | 若业务实际允许超收（如供应商多送），收货回写会被拒并抛 `ServiceException`，收货单无法过账 | 把 `PuPurchaseReceiveWritebackServiceImpl.OVER_RECEIVE_TOLERANCE` 改为允许比例（如 `new BigDecimal("0.05")`）即可放开，无需改结构；极端情况回退该文件 |
| **ERP-PU-01 收货量不预填（改为 0）** | 若前端未提供实收录入，自动生成的收货单会以 0 数量过账（不虚增，但少记） | 改回 `receiveLine.setQuantityReceived(noticeLine.getQuantityArrival())`（此时 `quantityArrival` 已是剩余未收量，不会再虚增） |
| **ERP-PU-02 幂等键移到 afterCommit** | 极端并发下（两次 MQ 同时投递）可能双回写——但超收守卫 + SQL 上界会挡住超量；正常重复投递仍由 Redis 键拦截 | 恢复入口 `setObjectIfAbsent` 占位（即回退该文件的 `registerIdempotentKeyAfterCommit`） |
| **ERP-PU-02 `purId == null` 抛异常** | MQ 会持续重投该消息直到 `pur_id` 补齐或进入死信队列 | 改回 `log.warn + return` |
| **ERP-PU-04 强制 DRAFT / 忽略客户端 status** | 依赖「前端传 status 直接建已审批单」的非常规用法会失效（这正是要修的漏洞） | 回退对应 `insertByBo`/`updateByBo` 两行 |
| **ERP-PRD-01 口径统一（leadTime 按小时倒排）** | 顶层物料（无父层需求日期）且 `lead_time > 0` 时，需求日期由「现在 + leadTime」变为「现在 − leadTime」；若基础数据把 `lead_time` 当天用会偏移 | 回退 `BomExplosionCalculator.resolveRequiredDate` 的基准/符号，或只回退 `PrdProductionPlanServiceImpl` 的调用点；**注意**：`BdMaterial`/`BdBomDetail.leadTime` 注释已统一为「小时」，需同步核对基础数据 |
| **ERP-PRD-02 超报工上限（容差 0）** | 工单计划量未及时调整时，正常报工可能被拒 | 调 `PrdWorkReportServiceImpl.OVER_REPORT_TOLERANCE`；`planned_qty IS NULL` 时不设上限，不会误伤 |
| **ERP-PRD-02/05 状态改 CAS** | 并发场景下后到请求会收到「状态已被并发操作变更」而非静默成功（语义更严格） | 回退对应 `update` wrapper 为 `updateById` |
| **ERP-BD-08 明细工单守卫** | 若存在「先改 BOM 再关工单」的既有作业习惯，会被拒绝 | 回退 `assertNoOpenWorkOrderReference` 的三处调用 |
| **ERP-BD-10 停用物料校验** | 若存量 BOM 引用了已停用物料，任何「保存明细」动作都会被拒 | 回退 `assertChildMaterialsEnabled` 调用，或把校验降级为 `log.warn` |
| **ERP-PRD-04 返工状态推进** | 出库消息会把 DRAFT 单推到 IN_PROGRESS（此前不动）；若下游依赖 DRAFT 判定「未发料」会受影响 | 回退 `ReworkStockConsumer.doProcess` 与 `advanceByStockOperation` |

**通用回滚**：本批次未提交 git（禁止 git 操作），回滚以文件为单位还原即可；DDL 为新增列（可空、有默认值），**保留列不影响回滚**，无需 down 脚本。
