# NexusERP 序列号/批次号/条码 编码生成规则设计方案

> 版本：v1.1  
> 日期：2026-06-04  
> 状态：Phase 1 已完成

---

## 一、背景与目标

NexusERP 后端已具备批次号（`WmBatch`）、序列号（`WmSn`）、条码（`WmBarcode` + `WmBarcodeConfig`）的生成能力，物料主数据（`BdMaterial`）也已支持 `isBatchManage` 和 `isSerialManage` 标识。但当前存在以下问题：

1. **前端未调用**：`createBatch`、`batchGenerateSn`、`generateBarcode` 等接口已定义但未在业务页面中集成
2. **字段不统一**：部分实体有 `batchId/batchCode`，部分实体用近义字段（`lotNumber`、`batchNo`、`reservedBatchNo`），部分完全缺失
3. **序列号和条码字段全模块缺失**：所有业务行实体均缺少 `snId/snCode/barcodeCode`
4. **生成时机未定义**：没有明确在哪个业务节点触发自动生成

**目标**：参照 SAP、Oracle EBS、用友 U8+、金蝶 K3 等通用 ERP 的做法，定义清晰的编码规则和业务触发点，使系统在正确的节点自动生成批次号、序列号和条码。

---

## 二、三种编码的定义与关系

### 2.1 概念定义

| 编码类型 | 定义 | 粒度 | 适用物料 | 典型行业 |
|---------|------|------|---------|---------|
| **批次号 (Batch/Lot)** | 同一批物料共享一个编号，用于追溯生产日期、供应商、质量状态 | 一批多件 | `isBatchManage=1` 的物料 | 食品、医药、化工、电子元器件 |
| **序列号 (SN)** | 单件唯一编号，一物一码，全生命周期跟踪 | 一物一码 | `isSerialManage=1` 的物料 | 手机、电脑、汽车、精密仪器 |
| **条码 (Barcode)** | 批次号或序列号的物理载体，用于扫码作业 | 按需生成 | 所有需要扫描作业的环节 | 通用 |

### 2.2 关系模型

```
物料 (BdMaterial)
  ├── isBatchManage=1 ──→ 批次 (WmBatch) ──→ 批次条码 (WmBarcode, bizType=BATCH)
  └── isSerialManage=1 ──→ 序列号 (WmSn) ──→ 序列号条码 (WmBarcode, bizType=SN)
```

- 一个批次号可对应多个条码（箱码、托盘码）
- 一个序列号对应唯一条码
- 序列号必须关联批次号（同一批次下的多个 SN）
- 条码是批次号/序列号的"物理表达"，用于打印和扫码

---

## 三、编码规则设计

### 3.1 批次号规则

| 场景 | 规则编码 | 规则格式 | 示例 | 说明 |
|------|---------|---------|------|------|
| 采购入库批次 | `BATCH_PURCHASE` | `B{yyyyMMdd}{4位流水}` | `B202506040001` | 按日重置流水号 |
| 生产入库批次 | `BATCH_PRODUCTION` | `P{yyyyMMdd}{产线代码}{4位流水}` | `P20250604A1001` | 体现生产日期和产线 |
| 其他入库批次 | `BATCH_MISC` | `M{yyyyMMdd}{4位流水}` | `M202506040001` | 其他入库单 |
| 退货入库批次 | — | 复用原批次号 | — | 不生成新批次，关联原批次 |

> 当前 `WmBatchServiceImpl.generateBatchCode()` 已实现 `B{yyyyMMdd}{4位流水}` 格式。  
> 建议扩展为支持多种前缀，通过 `BdSerialNumberRule` 统一管理，而非硬编码。

### 3.2 序列号规则

| 场景 | 规则编码 | 规则格式 | 示例 | 说明 |
|------|---------|---------|------|------|
| 成品序列号 | `SN_PRODUCT` | `{物料编码后4位}{yyyyMM}{6位流水}` | `M001202506000001` | 体现物料和月份 |
| 采购序列号 | `SN_PURCHASE` | `SN{yyyyMMdd}{6位流水}` | `SN20250604000001` | 采购入库时生成 |
| 返修序列号 | — | `RW{原SN}` | `RWM001202506...` | 关联原 SN |

> 当前 `BdSerialNumberRuleServiceImpl.generateSerialNumber()` 已支持前缀+日期+流水+校验位。  
> 当前 `WmSnServiceImpl` 已有 `batchGenerateSn()` 接口（按数量批量生成）。

### 3.3 条码规则

| 条码类型 | 规则编码 | 编码格式 | 内容说明 |
|---------|---------|---------|---------|
| 物料条码 | `BC_MATERIAL` | Code128: `MAT:{materialCode}` | 物料基础条码 |
| 批次条码 | `BC_BATCH` | Code128: `BATCH:{batchCode}:QTY:{quantity}` | 含批次号和数量 |
| 序列号条码 | `BC_SN` | QR Code: `SN:{snCode}` | 含序列号 |
| 库位条码 | `BC_LOCATION` | Code128: `LOC:{warehouseCode}-{areaCode}-{locationCode}` | 仓库库位标签 |
| 单据条码 | `BC_DOC` | QR Code: `DOC:{docType}:{docCode}` | 单据打印条码 |

> 当前 `WmBarcodeServiceImpl.generateAndSave()` 已支持按配置生成条码并落库。  
> 当前 `WmBarcodeConfig` 已支持按业务类型（`bizType`）和物料类别（`categoryId`）配置规则。

---

## 四、业务触发点设计

### 4.1 采购 / 仓库管理模块

| # | 操作节点 | 生成内容 | 触发逻辑 | 优先级 |
|---|---------|---------|---------|--------|
| 1 | 到货通知单创建 | 不生成内部批次 | 记录供应商原始批次号（新增 `vendorBatchCode` 字段） | P2 |
| 2 | 来料检验（IQC） | 不生成 | 检验结果绑定到货单行 | — |
| 3 | **物料入库单 → 上架** | **生成批次号** | 若物料 `isBatchManage=1`，上架时自动调用 `WmBatchService.createBatch()`，回填 `batchId` + `batchCode` | **P0** |
| 4 | 物料入库单 → 上架（序列号物料） | **生成序列号** | 若物料 `isSerialManage=1`，上架时按入库数量调用 `WmSnService.batchGenerateSn()` | **P0** |
| 5 | 物料入库单 → 上架 | **生成条码** | 批次物料生成批次条码，序列号物料生成序列号条码 | P1 |
| 6 | 入库单审批通过 → 执行入库 | 写入库存 | 将 `batchId`/`batchCode` 写入 `WmMaterialStock` | P0 |
| 7 | 采购退货出库 | 引用原批次 | 出库时选择原入库批次，不生成新批次 | P1 |

### 4.2 生产管理模块

| # | 操作节点 | 生成内容 | 触发逻辑 | 优先级 |
|---|---------|---------|---------|--------|
| 1 | 生产领料单（发料） | 引用批次 | 按先进先出/指定批次扣减库存批次 | P1 |
| 2 | **生产报工/完工入库** | **生成生产批次号** | 若物料 `isBatchManage=1`，按 `BATCH_PRODUCTION` 规则生成 | **P0** |
| 3 | **生产报工/完工入库（序列号物料）** | **批量生成序列号** | 若物料 `isSerialManage=1`，按报工数量批量生成 SN | **P0** |
| 4 | 生产报工/完工入库 | 生成条码 | 批次物料生成批次条码，序列号物料生成序列号条码 | P1 |
| 5 | 生产退料入库 | 引用原发料批次 | 退回原批次 | P2 |
| 6 | 返修工单完工 | 生成返修批次/SN | 关联原批次/原 SN | P2 |

### 4.3 销售管理模块

| # | 操作节点 | 生成内容 | 触发逻辑 | 优先级 |
|---|---------|---------|---------|--------|
| 1 | 销售出库单创建 | 引用批次 | 按批次先进先出或指定批次出库 | P1 |
| 2 | 销售出库（序列号物料） | 扫描 SN | 出库时扫描序列号条码，校验并绑定出库单 | P1 |
| 3 | 销售出库 | 生成出库条码 | 可选：为出库行生成单据条码用于拣货 | P2 |
| 4 | 销售退货入库 | 引用原出库批次/SN | 关联原批次号或原序列号 | P1 |

### 4.4 库存管理模块

| # | 操作节点 | 生成内容 | 触发逻辑 | 优先级 |
|---|---------|---------|---------|--------|
| 1 | 库存调拨单 | 引用批次 | 调拨时指定批次，不改变批次号 | P1 |
| 2 | 库存盘点单 | 引用批次 | 按批次+库位维度盘点 | P1 |
| 3 | 其他入库单 | 生成批次号 | 同采购入库逻辑 | P1 |
| 4 | 其他出库单 | 引用批次 | 同销售出库逻辑 | P1 |
| 5 | 库位定义 | 生成库位条码 | 打印库位标签 | P2 |

---

## 五、数据模型改造

### 5.1 现状审查结果

| 实体类 | batchId | batchCode | snId | snCode | barcodeCode | 备注 |
|--------|:-------:|:---------:|:----:|:------:|:-----------:|------|
| **WmMaterialReceiveDetail** | 有 | 有 | 缺 | 缺 | 缺 | 入库明细 |
| **WmMaterialReceiveLine** | 缺 | 缺 | 缺 | 缺 | 缺 | 入库行 |
| **WmMaterialStock** | 有 | 有 | 缺 | 缺 | 缺 | 库存 |
| **WmSalesIssueLine** | 有 | 有 | 缺 | 缺 | 缺 | 销售出库行 |
| **WmTransferLine** | 有 | 有 | 缺 | 缺 | 缺 | 调拨行 |
| **WmProductionIssueLine** | 有 | 有 | 缺 | 缺 | 缺 | 生产发料行 |
| **WmProductionRecpt** | 缺 | 缺 | 缺 | 缺 | 缺 | 生产入库（扁平结构） |
| **WmMiscRecptLine** | 有 | 有 | 缺 | 缺 | 缺 | 其他入库行 |
| **WmMiscIssueLine** | 有 | 有 | 缺 | 缺 | 缺 | 其他出库行 |
| **WmStockTakingLine** | 有 | 有 | 缺 | 缺 | 缺 | 盘点行 |
| **WmArrivalNoticeLine** | 缺 | 缺 | 缺 | 缺 | 缺 | 到货通知行 |
| **PuPurchaseOrderLine** | 缺 | 缺 | 缺 | 缺 | 缺 | 采购订单行 |
| **PuPurchaseReturnLine** | 缺 | 缺 | 缺 | 缺 | 缺 | 采购退货行 |
| **SoSalesOrderLine** | 缺 | 缺 | 缺 | 缺 | 缺 | 有近义字段 `reservedBatchNo`、`reservedSerials` |
| **SoReturnLine** | 缺 | 缺 | 缺 | 缺 | 缺 | 销售退货行 |
| **PrdWorkReport** | 缺 | 缺 | 缺 | 缺 | 缺 | 有近义字段 `lotNumber`、`batchNo`、`barcode` |
| **PrdWorkOrder** | 缺 | 缺 | 缺 | 缺 | 缺 | 生产工单 |

### 5.2 字段新增计划

#### P0 优先级（入库链路必须）

| 表/实体 | 新增字段 | 类型 | 用途 |
|---------|---------|------|------|
| `wm_material_receive_line` | `batch_id` | BIGINT | 入库行关联批次 |
| `wm_material_receive_line` | `batch_code` | VARCHAR(50) | 入库行批次号 |
| `wm_material_receive_detail` | `sn_id` | BIGINT | 入库明细关联序列号 |
| `wm_material_receive_detail` | `sn_code` | VARCHAR(64) | 入库明细序列号 |
| `wm_material_stock` | `sn_id` | BIGINT | 库存关联序列号 |
| `wm_material_stock` | `sn_code` | VARCHAR(64) | 库存序列号 |
| `wm_batch` | `vendor_batch_code` | VARCHAR(64) | 供应商原始批次号 |
| `wm_batch` | `source_doc_type` | VARCHAR(32) | 批次来源单据类型 |
| `wm_batch` | `source_doc_id` | BIGINT | 批次来源单据ID |
| `wm_sn` | `source_doc_type` | VARCHAR(32) | 序列号来源单据类型 |
| `wm_sn` | `source_doc_id` | BIGINT | 序列号来源单据ID |
| `wm_production_recpt` | `batch_id` | BIGINT | 生产入库关联批次 |
| `wm_production_recpt` | `batch_code` | VARCHAR(50) | 生产入库批次号 |
| `prd_work_report` | `batch_id` | BIGINT | 统一为标准字段（替换 `batchNo`） |
| `prd_work_report` | `batch_code` | VARCHAR(50) | 统一为标准字段（替换 `lotNumber`） |

#### P1 优先级（出库/调拨链路）

| 表/实体 | 新增字段 | 类型 | 用途 |
|---------|---------|------|------|
| `wm_sales_issue_line` | `sn_id` | BIGINT | 销售出库行关联序列号 |
| `wm_sales_issue_line` | `sn_code` | VARCHAR(64) | 销售出库行序列号 |
| `wm_production_issue_line` | `sn_id` | BIGINT | 生产发料行关联序列号 |
| `wm_production_issue_line` | `sn_code` | VARCHAR(64) | 生产发料行序列号 |
| `wm_arrival_notice_line` | `vendor_batch_code` | VARCHAR(64) | 到货行记录供应商批次 |
| `so_sales_order_line` | `batch_id` | BIGINT | 统一为标准字段（与 `reservedBatchNo` 并存） |
| `so_sales_order_line` | `batch_code` | VARCHAR(50) | 统一为标准字段 |
| `pu_purchase_return_line` | `batch_id` | BIGINT | 采购退货关联原批次 |
| `pu_purchase_return_line` | `batch_code` | VARCHAR(50) | 采购退货批次号 |
| `so_return_line` | `batch_id` | BIGINT | 销售退货关联原批次 |
| `so_return_line` | `batch_code` | VARCHAR(50) | 销售退货批次号 |

#### P2 优先级（增强功能）

| 表/实体 | 新增字段 | 类型 | 用途 |
|---------|---------|------|------|
| `wm_material_receive_detail` | `barcode_code` | VARCHAR(128) | 入库明细条码 |
| `wm_material_stock` | `barcode_code` | VARCHAR(128) | 库存条码 |
| `wm_sales_issue_line` | `barcode_code` | VARCHAR(128) | 出库行条码 |
| `wm_transfer_line` | `sn_id` / `sn_code` / `barcode_code` | — | 调拨行序列号和条码 |
| `wm_misc_recpt_line` | `sn_id` / `sn_code` / `barcode_code` | — | 其他入库行序列号和条码 |
| `wm_misc_issue_line` | `sn_id` / `sn_code` / `barcode_code` | — | 其他出库行序列号和条码 |
| `wm_stock_taking_line` | `sn_id` / `sn_code` / `barcode_code` | — | 盘点行序列号和条码 |

---

## 六、前端交互改造

### 6.1 物料入库单 → 上架弹窗（P0）

**当前问题**：`batchId` 和 `batchCode` 为手动输入文本框，无自动生成逻辑。

**改造方案**：

1. 打开上架弹窗时，根据物料 `isBatchManage` 判断是否需要自动生成批次号
2. 若需要，自动调用 `/erp/wm/batch/createBatch` 接口，回填 `batchId` + `batchCode`
3. 批次号输入框改为：自动填充 + 允许覆盖（兼容供应商自带批次号场景）
4. 若物料 `isSerialManage=1`，增加序列号批量生成区域

```
上架弹窗打开
    ↓
查询物料 isBatchManage / isSerialManage
    ↓
isBatchManage=1 → 自动调用 createBatch → 回填 batchId + batchCode
isSerialManage=1 → 显示序列号生成区域 → 调用 batchGenerateSn
    ↓
用户选择仓库/库区/库位，确认
    ↓
保存入库明细
```

### 6.2 生产报工/完工入库（P0）

1. 完工入库时，若物料启用批次管理，自动生成生产批次号
2. 若启用序列号，弹出 SN 批量生成/扫描界面
3. 报工记录关联 `batchId`/`batchCode`

### 6.3 销售出库（P1）

1. 出库行增加批次选择器（下拉选择当前库存中的批次）
2. 序列号物料增加 SN 扫描框
3. 支持先进先出自动推荐批次

### 6.4 库存调拨/盘点（P1）

1. 调拨行增加批次选择器
2. 盘点行按批次+库位维度展示

### 6.5 条码打印（P2）

1. 入库上架确认后，可打印批次条码/序列号条码标签
2. 库位定义时，可打印库位标签
3. 出库拣货时，可打印拣货单条码

---

## 七、后端服务扩展

### 7.1 批次服务扩展

| 服务方法 | 说明 |
|---------|------|
| `WmBatchService.createBatchByMaterial(materialId, sourceDocType, sourceDocId, warehouseId, vendorBatchCode)` | 自动填充物料信息，按来源单据类型选择规则编码 |
| `WmBatchService.createBatchByRule(ruleCode, materialId, ...)` | 按指定规则编码生成批次号 |

> 当前 `WmBatchServiceImpl.generateBatchCode()` 硬编码了 `B{yyyyMMdd}{4位流水}` 前缀。  
> 建议改为读取 `BdSerialNumberRule` 配置，支持多种前缀。

### 7.2 序列号服务扩展

| 服务方法 | 说明 |
|---------|------|
| `WmSnService.batchGenerateSn(materialId, batchId, qty, sourceDocType, sourceDocId)` | 已有接口，需补充 `sourceDocType`/`sourceDocId` 参数 |

### 7.3 条码服务扩展

| 服务方法 | 说明 |
|---------|------|
| `WmBarcodeService.generateBatchBarcode(batchId, quantity)` | 为批次生成条码（含数量信息） |
| `WmBarcodeService.generateSnBarcode(snId)` | 为序列号生成条码 |
| `WmBarcodeService.generateLocationBarcode(warehouseId, areaId, locationId)` | 为库位生成条码 |

### 7.4 入库执行服务扩展

| 服务方法 | 说明 |
|---------|------|
| `ReceiveInStrategy.executeReceive()` | 入库执行时，将明细的 `batchId`/`batchCode`/`snId`/`snCode` 写入 `WmMaterialStock` |
| `WmStockOperationServiceImpl` | 出库/调拨/盘点时，按批次维度扣减/校验库存 |

---

## 八、生成时序图

### 8.1 采购入库上架（批次物料）

```
用户点击"上架"按钮
        │
        ▼
前端：查询物料 isBatchManage = 1 ?
        │
   ┌────┴────┐
   是         否
   │          │
   ▼          ▼
调用         跳过
createBatch  批次生成
   │
   ▼
后端 WmBatchService.createBatch()
   ├── 自动生成 batchCode = B202506040001
   ├── 记录 materialId, sourceDocType, sourceDocId
   └── 返回 { batchId, batchCode }
        │
        ▼
前端：自动回填上架表单
   batchId = 10001
   batchCode = B202506040001
        │
        ▼
用户选择仓库/库区/库位，点击确定
        │
        ▼
调用 addDetail 保存入库明细
        │
        ▼
入库单审批通过 → 执行入库
        │
        ▼
写入 WmMaterialStock（含 batchId/batchCode）
```

### 8.2 采购入库上架（序列号物料）

```
用户点击"上架"按钮
        │
        ▼
前端：查询物料 isSerialManage = 1 ?
        │
   ┌────┴────┐
   是         否
   │          │
   ▼          ▼
先创建批次    跳过
(同上)        序列号生成
   │
   ▼
调用 batchGenerateSn(materialId, batchId, qty)
   │
   ▼
后端 WmSnService.batchGenerateSn()
   ├── 按 qty 生成 N 个序列号
   ├── 每个 SN 关联 batchId
   └── 返回 [{ snId, snCode }, ...]
        │
        ▼
前端：展示序列号列表
   SN001: M001202506000001
   SN002: M001202506000002
   ...
        │
        ▼
用户确认，保存入库明细
```

### 8.3 生产完工入库

```
生产报工 → 完工确认
        │
        ▼
查询物料 isBatchManage / isSerialManage
        │
        ▼
isBatchManage=1 → 调用 createBatch(ruleCode=BATCH_PRODUCTION)
   生成 batchCode = P20250604A1001
        │
        ▼
isSerialManage=1 → 调用 batchGenerateSn
   按报工数量生成序列号
        │
        ▼
写入 WmProductionRecpt（含 batchId/batchCode）
写入 WmMaterialStock（含 batchId/batchCode/snId/snCode）
```

---

## 九、与主流 ERP 对标

| 功能 | NexusERP（本方案） | SAP | 用友 U8+ | 金蝶 K3 |
|------|-------------------|-----|---------|---------|
| 批次生成节点 | 入库上架/生产完工 | 收货/生产订单确认 | 采购入库单保存 | 外购入库单审核 |
| 序列号生成节点 | 入库上架/生产完工 | 生产订单完工/收货 | 产成品入库单 | 产品入库单 |
| 条码生成 | 随批次/SN自动生成 | 基于 SSCC/EAN 规则 | 存货条码管理模块 | 条码管理系统 |
| 库位条码 | 库位定义时生成 | WM 模块托盘管理 | 库存管理-货位 | 仓库管理-库位 |
| 供应商批次映射 | `vendor_batch_code` 字段 | 供应商批次分类 | 供应商批号 | 供应商批号 |
| 批次追溯 | `source_doc_type` + `source_doc_id` | 批次追溯报表 | 批次追溯 | 批次追踪 |
| 序列号全生命周期 | `WmSn` 状态流转 | 设备序列号管理 | 序列号跟踪 | 序列号跟踪 |

---

## 十、实施路线图

### Phase 1：入库链路打通（P0） ✅ 已完成

1. ~~`wm_batch` 表新增 `vendor_batch_code`、`source_doc_type`、`source_doc_id` 字段~~ → `source_doc_type`/`source_doc_id` 已存在，仅新增 `vendor_batch_code`
2. ~~`wm_material_receive_line` 表新增 `batch_id`、`batch_code` 字段~~ → 已存在，无需修改
3. ~~`wm_material_receive_detail` 表新增 `sn_id`、`sn_code` 字段~~ → 已完成
4. ~~`wm_material_stock` 表新增 `sn_id`、`sn_code` 字段~~ → 已完成
5. ~~`wm_production_recpt` 表新增 `batch_id`、`batch_code` 字段~~ → 已完成
6. ~~`prd_work_report` 表新增 `batch_id`、`batch_code` 字段~~ → 已完成（保留原 `lotNumber`/`batchNo` 向后兼容）
7. ~~前端上架弹窗集成 `createBatch` 自动生成批次号~~ → 已完成（`batchGenerateSn` 待 Phase 2 集成）
8. ~~入库执行服务将批次/序列号信息写入库存~~ → 已完成（`buildStockFromDetail` + `copyStock` 已添加 `snId`/`snCode` 赋值）
9. ~~`WmBatchService` 扩展 `createBatchByMaterial` 方法~~ → 已完成
10. ~~SQL 迁移脚本~~ → 已生成 `script/sql/update/update_batch_sn_barcode_phase1.sql`

### Phase 2：出库/调拨链路（P1）

1. 销售出库行新增序列号字段，集成 SN 扫描
2. 到货通知行新增 `vendor_batch_code`
3. 采购退货/销售退货行新增批次字段
4. 出库服务按批次维度扣减库存
5. 先进先出批次推荐逻辑

### Phase 3：条码与增强（P2）

1. 各业务行新增 `barcode_code` 字段
2. 条码打印功能（标签设计、批量打印）
3. 库位条码生成与打印
4. 批次追溯报表
5. 序列号全生命周期查询

---

## 附录 A：现有自动编码规则清单

系统当前通过 `genCode()` 使用的自动编码规则（`/system/autocode/get/{ruleCode}`）：

| 规则编码 | 业务含义 | 使用页面 |
|---------|---------|---------|
| `WM_MATERIAL_RECEIVE_CODE` | 收料单编码 | receive/index.vue, receive/form.vue |
| `WM_TRANSFER_CODE` | 调拨单编码 | transfer/form.vue |
| `WM_SALES_ISSUE_CODE` | 销售出库单编码 | sales-issue/form.vue |
| `WM_ARRIVAL_NOTICE_CODE` | 到货通知单编码 | arrivalnotice/form.vue, arrivalnotice/index.vue |
| `WM_STOCKTAKING_CODE` | 盘点单编码 | stocktaking/form.vue |
| `WM_PROD_ISSUE_CODE` | 生产领料单编码 | productionissue/form.vue |
| `WM_MISC_RECPT_CODE` | 其他入库单编码 | miscrecpt/form.vue |
| `WM_MISC_ISSUE_CODE` | 其他出库单编码 | miscissue/form.vue |
| `WAREHOUSE_CODE` | 仓库编码 | warehouse/index.vue |
| `WM_AREA_CODE` | 存储区编码 | storagearea/index.vue |
| `WM_LOCATION_CODE` | 库位编码 | storagelocation/index.vue |
| `PU_REQUISITION_CODE` | 请购单编码 | requisition/form.vue |
| `PU_ORDER_CODE` | 采购订单编码 | order/form.vue |
| `PU_RETURN_CODE` | 采购退货单编码 | return/form.vue |
| `SO_CO_NO` | 客户订单编码 | customerorder/index.vue |
| `SO_ORDER_CODE` | 销售订单编码 | orderheader/form.vue |
| `SO_RETURN_CODE` | 销售退货单编码 | return/form.vue |
| `SO_QUOTATION_CODE` | 报价单编码 | quotation/form.vue |
| `PRD_WORK_ORDER_CODE` | 生产工单编码 | prdwork/form.vue |
| `PRD_MATERIAL_MBOM` | 物料需求编码 | requirement/form.vue |
| `PRD_PLAN_CODE` | 生产计划编码 | prdplan/form.vue |
| `CLIENT_CODE` | 客户编码 | client/index.vue |
| `VENDOR_CODE` | 供应商编码 | vendor/index.vue |
| `MATERIAL_CODE` | 物料编码 | material/index.vue |
| `WORKSTATION_CODE` | 工作站编码 | workstation/index.vue |
| `WORKSHOP_CODE` | 车间编码 | workshop/index.vue |

## 附录 B：现有 API 能力清单

### 批次 API（`/erp/wm/batch`）

| 接口 | 方法 | 说明 | 前端是否已调用 |
|------|------|------|:----------:|
| `/list` | GET | 查询批次列表 | 是 |
| `/{batchId}` | GET | 获取批次详情 | 是 |
| `/` | POST | 新增批次 | 是 |
| `/` | PUT | 修改批次 | 是 |
| `/{batchIds}` | DELETE | 删除批次 | 是 |
| `/export` | POST | 导出批次 | 是 |
| **`/createBatch`** | POST | **创建批次（自动生成编码）** | **否** |
| `/updateQuantity` | PUT | 更新批次数量 | 否 |
| `/changeStatus` | PUT | 变更批次状态 | 否 |
| `/checkExpiry` | POST | 检查批次过期 | 否 |
| `/code/{batchCode}` | GET | 按编码查询批次 | 否 |

### 序列号 API（`/erp/wm/sn`）

| 接口 | 方法 | 说明 | 前端是否已调用 |
|------|------|------|:----------:|
| `/list` | GET | 查询序列号列表 | 是 |
| `/{snId}` | GET | 获取序列号详情 | 是 |
| `/` | POST | 新增序列号 | 是 |
| `/` | PUT | 修改序列号 | 是 |
| `/{snIds}` | DELETE | 删除序列号 | 是 |
| `/export` | POST | 导出序列号 | 是 |
| **`/batchGenerate`** | POST | **批量生成序列号** | 否 |
| `/bindStock` | PUT | 绑定库存 | 否 |
| `/unbindStock/{snId}` | PUT | 解绑库存 | 否 |
| `/changeStatus` | PUT | 变更序列号状态 | 否 |

### 条码 API（`/erp/wm/barcode`）

| 接口 | 方法 | 说明 | 前端是否已调用 |
|------|------|------|:----------:|
| `/list` | GET | 查询条码列表 | 是 |
| `/{id}` | GET | 获取条码详情 | 是 |
| `/` | POST | 新增条码 | 是 |
| `/` | PUT | 修改条码 | 是 |
| `/{id}` | DELETE | 删除条码 | 是 |
| **`/generateByCode`** | POST | **根据配置生成条码** | 否 |

### 条码配置 API（`/erp/wm/barcode/config`）

| 接口 | 方法 | 说明 | 前端是否已调用 |
|------|------|------|:----------:|
| `/list` | GET | 查询配置列表 | 是 |
| `/{id}` | GET | 获取配置详情 | 是 |
| `/` | POST | 新增配置 | 是 |
| `/` | PUT | 修改配置 | 是 |
| `/{id}` | DELETE | 删除配置 | 是 |

### 序列号规则 API（`/erp/bd/serial-number-rule`）

| 接口 | 方法 | 说明 | 前端是否已调用 |
|------|------|------|:----------:|
| `/list` | GET | 查询规则列表 | 是 |
| `/{id}` | GET | 获取规则详情 | 是 |
| `/` | POST | 新增规则 | 是 |
| `/` | PUT | 修改规则 | 是 |
| `/{ids}` | DELETE | 删除规则 | 是 |
| **`/generate/{ruleCode}`** | GET | **根据规则生成序列号** | 是（仅规则管理页） |
