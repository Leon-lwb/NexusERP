# PCBA 委外订单功能完善方案（可行性与工作量评估）

> 评估基准：`plus` 分支 093a94c9。评估对象：`PrdSubcontractController` 为核心的委外订单功能，向"PCBA 委外制作全流程闭环"扩展。

---

## 一、现状分析

### 1.1 当前委外订单实现（薄）

| 层 | 位置 | 现状 |
|---|---|---|
| 表 | `ry-erp.prd_subcontract` | 单表：单号、供应商、物料、数量、单价、金额、计划/实际日期、关联工单、状态 |
| 后端 | `ruoyi-erp/.../prd/`（Controller/Service/Mapper 共 7 个类） | 单表 CRUD + 状态机 + 操作时间线 |
| 状态机 | `PrdSubcontractServiceImpl` | DRAFT → SUBMITTED → APPROVED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED |
| 下达 | `release()` | 自动生成 ERP 工单（`prd_work_order.source_type='SUBCONTRACT'`，`wo_type` 枚举已含 SUBCONTRACT） |
| 前端 | `plus-ui/src/views/erp/prd/subcontract/index.vue` | 单表列表 + 表单，无明细页 |

关键结论：**当前委外订单只是"订单层面"的单据，无 BOM、无工程文件、无发料明细、无 SN、无询价，与仓储/MES 的集成是"策略已备好但未接线"**。

### 1.2 可复用的既有资产（本次评估的最大发现）

| 能力 | 位置 | 成熟度 |
|---|---|---|
| BOM 三层结构（头/行/替代料，用量+损耗率） | `bd_bom` / `bd_bom_detail` / `bd_bom_alternative`；`BdBom.bomType` 含 EBOM/MBOM，`routingId` 已关联工艺路线 | ✅ 齐备 |
| 物料来源类型 SUBCONTRACT / CFM（客供料） | `bd_material.sourceType` | ✅ 已有枚举 |
| 物料-供应商价格关系（含 isDefault/priority/比价页） | `bd_material_vendor` + `plus-ui/.../pu/price-comparison/index.vue` | ✅ 齐备 |
| 批次主数据 + 库存行级批次 | `wm_batch`（source_doc_type/source_doc_no/vender_batch_code）、`wm_material_stock.batch_id/batch_code` | ✅ 齐备 |
| 拣货分配（批次+储位粒度） | `AllocatedStock` VO、`PickingResolutionServiceImpl`（FIFO/FEFO 策略） | ✅ 齐备 |
| 生产领料过账（可指定批次扣减 + 写流水） | `postProductionIssueAllocated(issueId, lineId, List<AllocatedStock>)` → `ProductionIssueStrategy` | ✅ 可直接参照 |
| 完工入库（自动建批次、精确查找/新建库存行） | `ProductionCompletionStrategy.findOrCreateStock` | ✅ 可直接参照 |
| 委外库存过账策略 | `SubcontractIssueStrategy` / `SubcontractReceiveStrategy`（已在 `StockOperationStrategyFactory` 注册） | ⚠️ **已建未接线**：Issue 只按物料 FIFO 扣减（不支持指定批次/仓库）；Receive 按 materialId+vendorId 找第一条库存累加（不指定仓库、不写批次） |
| 委外 MQ 队列 | `QueueConstants.SUBCONTRACT_STOCK_QUEUE`（消费者仅做 ISSUE/RECEIVE 状态机更新） | ⚠️ 通道已通，业务未接 |
| E-SOP 体系（文档+版本+审批+工序绑定） | `mes/sop/` 四表，`fileOssId` + 签名 URL 范式 | ✅ 齐备 |
| 委外工序标记 | `BdRoutingStep.isOutsource` | ✅ 已有 |
| SN 批量生成 | `ProSnServiceImpl.generateSnForCard`（`@Lock4j` 防并发，SN-卡号-序号） | ✅ 但仅支持流转卡场景 |
| SN↔批次绑定 | `SnCreatedEvent` → `lt_sn_binding` | ✅ 齐备 |
| 双向追溯服务 | `ProTraceabilityServiceImpl`（backward/forward，按 SN 链 + 工单 BOM 快照） | ✅ 齐备 |
| 邮件（支持 HTML + 附件） | `ruoyi-common-mail/MailUtils`（`File... files` 可变参） | ✅ 齐备 |
| 附件存储 + 业务绑定约定 | `sys_oss` + `SysOssExt`（bizType/refId/refType/tags），`sop_document.file_oss_id` 直存先例 | ✅ 有范式，无通用关联表 |
| 跨模块通信范式 | ERP↔MES 互不依赖，全走 RabbitMQ（`MES_WORKORDER_QUEUE` 等 10 个队列常量） | ✅ 模式成熟 |
| 采购收货回写/工单完工联动 | `PurchaseReceiveWritebackConsumer`、`ErpWorkOrderCompletedConsumer` | ✅ 模式可参照 |

### 1.3 关键缺口清单

1. **委外订单无 BOM 关联**：`prd_subcontract` 只有产出物料，没有 `bom_id`/`routing_id`，也没有订单 BOM 快照表。
2. **BOM 行无供应方标注**：`bd_bom_detail` 无"自供/客供/外协厂供"字段（`bd_material.sourceType` 是物料级而非"该订单谁供料"）。
3. **无工程文件管理**：BOM/物料/订单均无 gerber、坐标文件（pick-and-place）、钢网等附件挂载点。
4. **委外发料/收货库存过账未接线**：策略类空有注册，`PrdSubcontractServiceImpl.release/issue/receive` 均不触发 `IWmStockOperationService`；且策略本身不支持批次/仓库参数。
5. **仓库无类型字段**：原料库/半成品库/成品库靠命名约定（WH005 半成品仓），收货路由到"半成品库"需要配置化方案。
6. **ProSn 无委外来源字段**：`pro_sn.source` 仅 MANUAL/BATCH，与 `prd_subcontract` 无关联，SN 无法直接反查委外订单。
7. **无采购询价单（RFQ）**：全库无供应商询价/报价实体（`SoQuotation` 是销售报价，不相关）。
8. **BOM 行无位号（designator/reference）**：贴片位号在 gerber/坐标文件里，系统内无结构化字段（对本需求影响小，见 §3.2 决策点）。
9. **按收货量自动闭环缺失**：`complete()` 只改状态，`actualQty` 从未累加。

---

## 二、业务流程设计（目标态）

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 阶段0 选料带料   选 PCBA 物料 → 带出 EBOM/工艺路线/SOP/工程文件资料        │
│                  BOM 行标注：自供(PROVIDED) / 外协供(VENDOR) / 客供(CFM)   │
│ 阶段1 工程资料   挂 gerber/坐标/BOM/钢网文件（物料级工程文件库 + 订单快照）  │
│ 阶段2 询比价     生成询价邮件(附件包) → 报价录入/回填 → 比价 → 选定供应商     │
│                  → 回写 vendorId/unitPrice → 订单提交审批                  │
│ 阶段3 备料发料   按"自供"行生成备料建议(FIFO/FEFO自动选批次,可手工调整)      │
│                  → 领料发货单 → 仓库过账扣库存(批次级) → 写流水             │
│                  → MES 生成 PCBA SN 列表 → 导出/邮件发外协厂(镭雕)          │
│ 阶段4 外协生产   状态跟踪 IN_PROGRESS；SOP/工艺要求随资料包下发              │
│ 阶段5 收货入库   分批收货(到货通知→收货单,可挂 QC 检验)                     │
│                  → 半成品仓(配置路由) → 自动建批次(source=SUBCONTRACT)      │
│                  → 录入/导入 SN → actualQty 累加 → 满量自动完工闭环         │
│ 阶段6 追溯       SN → 委外订单号(冗余字段直达) → 发料批次/供应商/工艺版本     │
└─────────────────────────────────────────────────────────────────────────┘
```

状态机扩展：DRAFT → (询价中 QUOTING，可选) → SUBMITTED → APPROVED → **MATERIAL_PREPARED(备料完成)** → RELEASED(已发料) → IN_PROGRESS → COMPLETED → CLOSED。

---

## 三、逐项可行性分析（对应需求 1–10）

### 需求 1：选择 PCBA 物料带出相关资料 —— ✅ 可行，改造小

- **依据**：`bd_bom`（`isCurrent`/`bomVersion`/`bomType=EBOM`）、`bd_bom_detail`（用量/损耗率/替代料）、`BdRouting`/`BdRoutingStep`、MES SOP 桥接、`bd_material.sourceType=SUBCONTRACT` 全部现成。
- **改造**：
  1. `prd_subcontract` 增加 `bom_id`、`routing_id`（快照冗余字段）；
  2. 新增聚合接口 `GET /erp/prd/subcontract/material-info/{materialId}`：按物料返回当前生效 BOM（含行+替代料）、工艺路线（含委外工序）、已挂工程文件清单、SOP 列表；
  3. 新增 `PrdSubcontractBomLine`（订单 BOM 快照表，见需求 3/4）。
- **风险**：一个物料多版本 BOM 时需引导用户选版本（默认取 `isCurrent=1`）。

### 需求 2：PCBA 核心工程文件（gerber/坐标/BOM 等）—— ✅ 可行，中等工作量

- **依据**：`sys_oss` + `SysOssExt`（bizType/refId/refType 约定）+ `sop_document.file_oss_id` 直存先例 + 前端上传组件现成。
- **改造**：
  1. 新建 **`erp_subcontract_doc`（委外工程文件表）**：`doc_id, material_id, subcontract_id(可空=物料级模板), doc_type(字典: GERBER/PICK_PLACE/BOM_FILE/STENCIL/SOP/OTHER), file_oss_id, version, md5, upload_by/time`；
  2. 文件类型做成字典 `erp_subcontract_doc_type`，支持"必传类型"校验（询价/下达前校验 gerber+坐标+工艺要求齐套）；
  3. 两层设计：**物料级**（工程文件库，一次上传多单复用）+ **订单级**（引用或重新上传形成版本快照，防止后续工程变更影响在途订单）；
  4. 询价/发料时可勾选文件打包为附件随邮件发出。
- **不建通用附件关联表**：沿用 `SysOssExt.refId/refType` + 独立业务表范式（与项目现有 `mes_content_file`、`sop_document` 一致），避免过度设计。

### 需求 3：主料/辅料供应方标注 —— ✅ 可行，改造小

- **依据**：`wm_material_stock.material_type` 已有 1原材料/2半成品/3成品/4辅料分类；但"谁供料"是订单/配方级业务属性，物料级枚举不够用。
- **改造**：
  1. `bd_bom_detail` 增加 `supply_type`（字典：`SELF_PROVIDED 自备料` / `VENDOR_PROVIDED 外协厂供` / `CUSTOMER_PROVIDED 客供`，默认自备料）——在 BOM 维护页加一列；
  2. 订单 BOM 快照表 `prd_subcontract_bom_line` 同样带 `supply_type`（快照固化，BOM 后续变更不影响在途订单）；
  3. 快照表字段：`line_id, subcontract_id, bom_detail_id, material_id/code/name/spec, quantity_per, loss_rate, demand_qty(需求量=计划数×单耗×(1+损耗)), supply_type, remark`；
  4. 备料/发料/询价均只看快照表"自备料"行。
- **PCBA 特有语义**：贴片阻容等辅料常为外协厂自备（turnkey），主芯片等贵重料自备（consignment）——行级标注正好覆盖，无需更多结构。

### 需求 4：自供主料备料（选批次 lot）+ 领料发货 —— ✅ 可行，**本次最大工作量**

- **依据**：批次主数据、库存行级批次、`AllocatedStock` 分配（batchId/warehouseId/locationId 粒度）、FIFO/FEFO 拣货策略、`postProductionIssueAllocated` 按批次扣减+写流水，全部现成——**等于把生产领料的成熟链路换成委外单据**。
- **改造**：
  1. **备料建议**：新增接口按快照"自备料"行 × 实时库存 → 自动生成拣货分配建议（复用 `PickingResolutionServiceImpl` FIFO/FEFO），前端可编辑（换批次/改数量），锁定后写 `WmStockReservation`（预留表已有）防止他单占用；
  2. **委外发料单**：新建 `WmSubcontractIssue(+Line+Alloc)` 三表（或复用 `WmProductionIssue` 加 `subcontractId` 字段，推荐前者，账目清晰）；
  3. **过账接线**：`SubcontractIssueStrategy` 增强——入参支持批次/仓库/储位（参照 `ProductionIssueStrategy` 的 `deductStock` 乐观锁扣减 + `buildBaseTxFromStock` 写流水，`tx_type=SUBCONTRACT_ISSUE`）；分批发料、发料单与订单快照行数量核对（超发校验）；
  4. 发料完成 → MQ 通知订单状态 RELEASED（接线现有 `SUBCONTRACT_STOCK_QUEUE`）；
  5. **退料**：委外退料单（外协商退回多余料，走杂收或复用 ReworkReceive 策略，一期可用杂收入库简化）。
- **风险**：`SubcontractIssueStrategy` 当前实现与需求不符需重写核心逻辑；预留（reservation）与实际扣减的并发一致性需测试。

### 需求 5：生成 PCBA SN 列表 —— ✅ 可行，中等工作量，需跨模块 MQ

- **依据**：`ProSnServiceImpl.generateSnForCard` 批量生成已成熟（Lock4j 防并发、`SnCreatedEvent` 自动绑批次/打标签）；但仅支持流转卡场景，且 `pro_sn` 无委外字段。
- **依赖方向约束**：ERP 与 MES 互不依赖（pom 已确认），SN 生成逻辑在 MES，必须走 MQ（范式同 `WorkOrderReleaseConsumer`）。
- **改造**：
  1. 新增队列 `erp.mes.subcontract.release.queue` + 消息体（subcontractId/no、materialId、plannedQty、snPrefix 规则）；
  2. `pro_sn` 增加 `source_type(SUBCONTRACT)`、`source_id(subcontractId)`、`subcontract_no` 冗余字段；
  3. MES 侧新增 `SubcontractSnGenerateConsumer`：新增 `generateSnForSubcontract(subcontractId, qty)`（编码规则建议 `SN-委外单号-4位序号`，或读可配置规则）；
  4. ERP 侧"下达/发料"动作发消息；SN 生成完成后回消息（或定时查询）更新订单 `sn_generated_qty`；
  5. SN 列表页 + **Excel 导出**（外协厂镭雕导入用）+ 可选邮件发送（挂需求 7 的邮件链路）；
  6. 委外订单详情页展示 SN 生成进度。
- **备选方案**（更简单但欠佳）：下达时在 ERP 侧建流转卡再复用 `generateSnForCard`——引入流转卡语义污染，不推荐。

### 需求 6：PCBA 生产 SOP、工艺要求 —— ✅ 可行，改造小

- **依据**：MES E-SOP 四表完整（版本/审批/工序绑定/fileOssId）；`BdRoutingStep.isOutsource` 已标记委外工序。
- **改造**：
  1. 委外订单带出 `routing_id` → 过滤 `isOutsource=1` 工序 → 展示工序绑定 SOP（`SopProcessBinding`）；
  2. 订单增加"工艺要求"长文本字段（质量标准、特殊工艺说明：如无铅喷锡、AOI 要求、测试要求 FCT/ICT）；
  3. 工艺要求 + SOP 文件纳入询价/发料资料包（与需求 2/7 共用附件打包逻辑）。
- **说明**：一期"展示+打包"即可，不需要向外协厂开放 SOP 在线查看（那是外部门户范畴）。

### 需求 7：询价邮件 + 外协厂报价 —— ⚠️ 可行，**无现成实体，需从零建 RFQ**

- **依据**：`MailUtils` 支持 HTML+附件；比价页面 `price-comparison` 有交互范式；`bd_material_vendor` 有历史价格。**但全系统无采购 RFQ/报价实体**。
- **改造**（两档方案）：
  - **方案 A（推荐一期，简化版）**：不建独立 RFQ 单，在委外订单下建 **`prd_subcontract_quote`（报价子表）**：`quote_id, subcontract_id, vendor_id/code/name, unit_price, currency, lead_time_days, tooling_fee(治具/钢网费), quote_status, valid_until, quote_time, remark`；
    - "生成询价邮件"：HTML 模板（物料/BOM 摘要/工艺要求/交期）+ 附件包（gerber/坐标/工艺文件 zip），发给多个候选供应商；
    - 报价回填：采购员在订单详情页录入各家报价（外协厂邮件回复，人工录入）；
    - 邮件发送记录表 `prd_subcontract_mail_log`（收件人、时间、状态，复用链路可追溯）。
  - **方案 B（二期，完整 RFQ）**：独立 `pu_rfq/pu_rfq_quote` 实体 + 状态机 + 与请购/订单打通 + 外部报价门户/小程序回填——工作量大，建议独立立项。
- **限制说明**：内网 ERP 无外部门户，外协厂无法在线填报报价，一期为"邮件出、人工录"闭环，业务上可接受（行业现状多数如此）。

### 需求 8：价格确认、选定供应商 —— ✅ 可行，改造小

- **改造**：
  1. 报价对比视图（复用比价页交互 + `bd_material_vendor` 历史价作参考列）；
  2. "选定供应商"操作：将选中报价回写订单 `vendorId/vendorCode/vendorName/unitPrice/totalAmount`，其余报价置 NOT_SELECTED；
  3. 选定后订单才允许提交审批/下达（状态机卡点）；
  4. 可选：审批通过后自动生成 `pu_contract`（采购合同已有实体，委外合同走同一张表，行 = PCBA 物料 + 加工费）。
- **说明**：若一期不做合同，直接订单生效即可，合同生成作为可选增强。

### 需求 9：收货入半成品库 + 订单闭环 —— ✅ 可行，中-大工作量

- **依据**：收货单据体系（`WmMaterialReceive/_Line/_Detail` + 到货通知）成熟；`ProductionCompletionStrategy.findOrCreateStock`（自动建批次+精确找库存行）可参照；QC 三检（QcIqc）模式现成；`ErpWorkOrderCompletedConsumer` 的"完工联动"范式现成。
- **改造**：
  1. **收货单**：新建委外收货单（或复用 `WmMaterialReceive` 增加 `receive_type=SUBCONTRACT`），行关联订单 + 快照产出物料；
  2. **半成品库路由**：仓库无类型字段——一期用**配置项**（`nexus.erp.subcontract.targetWarehouseId` 或按物料 `material_type=2` 映射默认仓），二期再加 `wm_warehouse.warehouse_type` 字段做正经路由；
  3. **过账**：`SubcontractReceiveStrategy` 重写——指定半成品仓/库位，`findOrCreateStock` 模式建库存行，**自动创建 `wm_batch`**（`source_doc_type='SUBCONTRACT_RECEIVE'`, `source_doc_no=委外单号`，可填 `vendor_batch_code` 外协批次）；
  4. **SN 回收**：收货时手工录入/Excel 导入外协回传的 SN 范围（或按规则区间），写 `pro_sn.snStatus=COMPLETED`（MQ 到 MES，同需求 5 通道）；
  5. **QC 联动**（可选开关）：收货过账前挂 IQC 检验（复用 `ERP_IQC_COMPLETED_QUEUE` 范式）；
  6. **自动闭环**：收货过账累加 `actualQty`，`actualQty >= plannedQty` 时自动置 COMPLETED（部分完工允许分批，尾差处理：关闭时按实际量结案）；
  7. 金额闭环：按实收量 × 订单单价生成应付参考（对接 `pu_invoice` 对账，可选增强）。
- **风险**：委外收货与"采购收货"在单据/策略上的边界要划清（避免混入 `PurchaseReceiveWritebackConsumer` 链路）；分批收货 × 分批发料的成本核算（加工费摊销）一期建议只按订单维度，不做行级成本。

### 需求 10：SN 追溯到委外订单 —— ✅ 可行，改造小

- **依据**：追溯服务 `ProTraceabilityServiceImpl` 已实现双向追溯；数据链路 `pro_sn → pro_work_order.erp_wo_id → prd_work_order → prd_subcontract.wo_id` 已通，但链路长且依赖工单关联，不如直接冗余。
- **改造**：
  1. `pro_sn` 冗余 `subcontract_id/subcontract_no`（需求 5 已加，此步免费）；
  2. `TraceabilityResult` VO 增加"委外信息"节点（订单号、供应商、发料批次、收货批次）；
  3. `wm_batch.source_doc_no=委外单号` → 从收货批次可反查发料批次（需求 4 的流水 `tx_type=SUBCONTRACT_ISSUE` 记录了具体 lot）→ 形成"成品 SN ↔ 委外单 ↔ 投料 lot"完整链；
  4. 前端追溯页（MES 已有 `/trace/{snId}`）增加委外信息展示卡片。
- **说明**：PCBA 在外协厂生产期间厂内不扫 SN，装配关系追溯依赖收货时回收 SN + 内部后续组装扫描，链路成立。

---

## 四、数据库改造汇总

### 新增表（7 张）

| 表 | 用途 | 关键字段 |
|---|---|---|
| `prd_subcontract_bom_line` | 订单 BOM 快照（含供应方标注） | bom_detail_id, material_id, quantity_per, loss_rate, demand_qty, **supply_type** |
| `erp_subcontract_doc` | 工程/工艺文件 | material_id, subcontract_id(可空), **doc_type(字典)**, file_oss_id, version, md5 |
| `prd_subcontract_quote` | 报价记录 | vendor_id, unit_price, lead_time_days, tooling_fee, quote_status |
| `prd_subcontract_mail_log` | 询价邮件记录 | mail_type(询价/SN下发), receivers, oss_id(附件包), send_status |
| `wm_subcontract_issue` (+line +alloc) | 委外发料单 | subcontract_id, line→快照行, alloc→stockId/batchId/locationId/qty |
| `wm_subcontract_receive` (+line) | 委外收货单 | subcontract_id, target_warehouse_id, received_qty, sn_file_oss_id |
| （可选）`wm_warehouse.warehouse_type` | 仓库类型字段 | 二期：STORAGE/SEMI/PRODUCT/RAW |

### 修改表（5 张）

| 表 | 变更 |
|---|---|
| `prd_subcontract` | +`bom_id`、`routing_id`、`process_requirement`(工艺要求文本)、`sn_generated_qty`、`target_warehouse_id`、状态枚举扩展(QUOTING/MATERIAL_PREPARED) |
| `bd_bom_detail` | +`supply_type`(字典, 默认 SELF_PROVIDED) |
| `pro_sn` | +`source_type`、`source_id`、`subcontract_no` |
| `wm_material_stock_tx` | tx_type 字典 +`SUBCONTRACT_ISSUE`/`SUBCONTRACT_RECEIVE`（若 tx_type 为 varchar 则无 DDL） |
| 字典表 | +`erp_subcontract_doc_type`、`erp_subcontract_supply_type`、`erp_subcontract_quote_status` |

迁移脚本放 `script/sql/update/`（含菜单 SQL：委外订单详情新页签、备料、报价等权限项）。

---

## 五、工作量估算

按"1 名熟悉本项目的全栈 + 0.5 名前端"基准，单位 = 人天（含自测，不含 UAT）。

| # | 模块 | 涉及需求 | 后端 | 前端 | 说明 |
|---|---|---|---|---|---|
| 0 | 表结构/字典/菜单/迁移 SQL | 全部 | 2 | — | |
| 1 | 资料带出（BOM/路线/SOP 聚合接口） | 1 | 2 | 1 | |
| 2 | 工程文件管理（两层+类型字典+齐套校验） | 2 | 3 | 2 | |
| 3 | BOM 供应方标注 + 订单快照 | 3 | 2 | 1.5 | 含 BOM 维护页加列 |
| 4 | 备料建议 + 委外发料单 + 过账接线 | 4 | **6** | 3 | **最大项**；含策略重写、预留、分批发料 |
| 5 | SN 生成（MQ 双向 + pro_sn 扩展 + 导出） | 5 | 3.5 | 1.5 | 含 MES 侧消费者 |
| 6 | SOP/工艺要求关联与资料包 | 6 | 1.5 | 1 | |
| 7 | 询价邮件 + 报价录入（方案 A） | 7 | 4 | 2.5 | 含邮件模板/附件打包/发送记录 |
| 8 | 报价比价与选定回写 | 8 | 1.5 | 1.5 | |
| 9 | 委外收货 + 半成品入库 + 自动闭环 | 9 | **5** | 2.5 | 含批次创建、SN 回收、QC 开关 |
| 10 | 追溯链增强 | 10 | 1 | 1 | |
| — | 联调 + 修缺陷 + SQL 脚本打磨 | 全部 | 4 | 2 | 跨模块 MQ 联调偏重 |
| | **合计** | | **36** | **19.5** | **≈ 55 人天** |

- **并行排期**：全栈 + 前端两人约 **6–7 周**；单人串行约 **11 周**。
- **MVP 截断方案**（最小可用闭环，先上工厂跑起来）：需求 0/1/3/4/5/9/10，询价跳过系统化（邮件手工发、报价录一次）、工程文件只做订单级单层 —— **约 33 人天（后端 24 + 前端 9）**，单人 6–7 周。
- **二期增强**（另行立项）：完整 RFQ 与外部报价门户、`wm_warehouse` 类型字段与库位路由、委外加工费成本核算/应付对账、外协厂测试数据回传（对应 [MES 硬件生产流程差异分析](../mes/hardware-production-flow-analysis.md) 的 P1 项）。

---

## 六、风险与决策点

| # | 风险/决策 | 建议 |
|---|---|---|
| 1 | **ERP↔MES 跨模块**（SN 生成、收货回写必须走 MQ，两模块 pom 互不依赖） | 严格沿用 `WorkOrderReleaseConsumer`/`ErpWorkOrderCompletedConsumer` 既有范式；消息体带 tenantId（MQ 消费者写多租户表要 `TenantHelper`，见 AGENTS.md 陷阱） |
| 2 | `SubcontractIssue/ReceiveStrategy` 现有实现与需求不符 | 直接重写这两个策略类核心逻辑（它们目前无调用方，改动无破坏面） |
| 3 | 仓库无类型字段，"半成品库"靠命名 | 一期配置化路由（订单上显式选目标仓 + 系统默认值），二期加 `warehouse_type` |
| 4 | 外协厂无法在线填报报价（无外部门户） | 一期邮件出、人工录，业务可接受；门户列入二期 |
| 5 | BOM 无位号字段 | 一期不做结构化位号管控——位号在坐标文件里随工程文件包下发即可；若后续要做"按位号核对贴片"，再给 `bd_bom_detail` 加 `designator` 长文本 |
| 6 | 发料预留与实际扣减的并发竞争 | 备料确认即写 `WmStockReservation`，发料过账时扣预留+扣库存同一事务，Lock4j 按订单维度加锁 |
| 7 | 询价邮件带工程文件 = 核心技术资产外发 | 邮件发送需权限点 + 发送记录留痕（`prd_subcontract_mail_log`）；可加水印/保密提示由模板控制 |
| 8 | 委外收货与采购收货链路混淆 | 独立单据类型 + 独立策略，不接入 `PurchaseReceiveWritebackConsumer` |
| 9 | 多租户/雪花 ID | 新表全部带 `tenant_id`；BO/VO 走 MapstructUtils+`@AutoMapper` 既有范式，ID 序列化已由全局 BigNumberSerializer 处理，无额外工作 |

---

## 七、结论

**总体可行，且改造成本显著低于从零建设**：本次需求的 10 项中，8 项有直接可复用的既有资产（BOM 体系、批次库存、拣货分配、过账策略框架、E-SOP、SN 生成、邮件、追溯），2 项需要新建实体（工程文件、询价报价）。

- 全量方案 **约 55 人天**（后端 36 + 前端 19.5），两人并行 6–7 周；
- MVP（最小闭环）**约 33 人天**，单人 6–7 周；
- 技术风险集中在两处：**委外发料/收货过账策略重写**（策略已建未接线，正好是"填坑"而非"开荒"）与 **ERP↔MES 的 SN 生成 MQ 通道**（范式成熟，照抄即可）。

建议按 MVP → 增强 → 二期的节奏推进，一期重点把"带料下单 → 备料发料 → 收货入库 → SN 追溯"的实物闭环跑通，商务侧（询价/比价）用轻量方案 A 过渡。
