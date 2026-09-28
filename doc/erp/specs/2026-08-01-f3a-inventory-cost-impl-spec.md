# F3-a 存货成本(收敛版)实施规格

> **文档编号**: NexusERP-SPEC-2026-007-F3A
> **创建日期**: 2026-08-01
> **状态**: 设计待评审
> **所属计划**: [ERP 模块主文档](../NexusERP_ERP文档.md)（原 07-29 财务开发总计划已清理，当前仓库无原件）(财务核心 F3-a 阶段,收敛版)
> **前置**: F0 GL(凭证引擎/科目余额)、F1 AR/AP(Poster 模式)、F2 FA(过账模式)、F5 报表/关账。
> **核心约束**: **收敛** —— 计价引擎 `MovingAverageValuationServiceImpl`(IInventoryValuationService)已存在但**零调用者**,本阶段只做"接线 + 过账",不新建计价算法、不触碰 MES。

---

## 一、范围与目标

### 1.1 范围

| 能力 | 交付 |
|---|---|
| 计价接线 | 移动加权平均引擎接入**关键收发策略**:采购入库(按采购价)、生产领料(按移动平均出库)、销售出库(按移动平均出库)、杂项收发(按标准成本)、完工入库(已有,保持) |
| 库存成本回写 | 入库 `updateStockCost` 累加 totalCost/重算 unitCost;出库 `issueStockCost` 扣减(新增) |
| 成本过账 | 新增 `CostPoster`:入库借存货/贷应付暂估、出库借成本/贷存货,复用 `createAndPost` 幂等 |
| 采购暂估 | 月末 GR/IR:收货未开票按暂估入账,发票到时冲回(本期实现暂估入账+回冲接口) |
| 成本报表 | 库存成本余额与 GL 勾稽(复用 F5 报表,存货科目已映射) |

### 1.2 不含(后置 F3-b)

- BOM 成本卷积、标准成本差异分析(依赖 MES 报工数据成熟)
- 生产成本归集(工单领料→直接材料、报工→人工/制造费用)
- 存货跌价准备

### 1.3 目标

让已写好的计价引擎真正生效,收发货自动维护 `unitCost/totalCost` 并自动过账 GL,存货科目余额可勾稽。

---

## 二、现状(grounding)

| 项 | 现状 | 证据 |
|---|---|---|
| 计价引擎 | 已实现,零调用 | `MovingAverageValuationServiceImpl`(updateStockCost/calculateMovingAverage) |
| 库存成本字段 | 已有 | `WmMaterialStock.unitCost/totalCost`(WmMaterialStock.java:198,204) |
| 库存流水成本字段 | 已有 | `WmMaterialStockTx.unitCost`(:192) |
| 完工入库已计价 | 已写 | `ProductionCompletionStrategy:221-224`(用物料 standardCost) |
| 策略装配 | Spring 构造器注入,@Component 自动收集 | `StockOperationStrategyFactory`(List<StockOperationStrategy>) |
| 成本来源 | 物料 `standardCost`/`referencePrice` | `BdMaterial.java:130,135` |
| 过账模式 | `AccountingPoster` + `createAndPost` 幂等 | F1/F2 既有 Poster |
| 来源常量 | `SOURCE_COST` F0 已预留 | `GlConstants.java:29` |

> **菜单段位**:F3-a 不新增页面,仅在"报表项目配置"补充映射(存货 1401/1403/1405 已映射 BS_INVENTORY)。
> **字典段位**:不新增。

---

## 三、数据模型

**不新建表。** 复用既有:
- `wm_material_stock.unit_cost/total_cost`(计价回写)
- `wm_material_stock_tx.unit_cost`(流水成本)
- 库存成本凭证走 `gl_voucher`(`voucher_source=COST`)

---

## 四、核心设计

### 4.1 计价引擎接口增强

`IInventoryValuationService` 增加**出库成本结转**方法(入库方法已有):

```java
/** 出库成本结转:按当前移动平均成本扣减,返回出库单位成本 */
BigDecimal issueStockCost(Long stockId, BigDecimal outQty);
```

实现(移动平均):`unitCost` 不变(移动平均只在入库变动),扣减 `totalCost -= outQty × unitCost`,库存数量由策略扣减。**不重复实现**,只加这一个方法。

### 4.2 策略接线(5 个)

| 策略 | 成本来源 | 接线点 | 动作 |
|---|---|---|---|
| `ReceiveInStrategy` 采购入库 | 明细无单价 → 取物料 `referencePrice` | 入库后 | `updateStockCost(stockId, qty, price)` + 流水 unitCost |
| `ProductionIssueStrategy` 生产领料 | 移动平均 | 出库时 | `issueStockCost` → 流水 unitCost |
| `SalesOutStrategy` 销售出库 | 移动平均 | 出库时 | `issueStockCost` → 流水 unitCost |
| `MiscRecptStrategy` 杂项入库 | 物料 `standardCost` | 入库后 | `updateStockCost` |
| `MiscIssueStrategy` 杂项出库 | 移动平均 | 出库时 | `issueStockCost` |

> 全部通过**构造器加参注入**(与现有策略构造器风格一致);完工入库/调拨/盘点/返工/委外保持现状(已按标准成本或不受影响)。

### 4.3 CostPoster 过账(独立 Poster,不触发 MQ)

`cost.poster.CostPoster implements AccountingPoster`,`sourceType=COST`。F3-a 走**同步过账**(与 F1/F2 同模式,不引入 GL_POSTING 队列):

| 业务 | 分录 |
|---|---|
| 采购入库(有发票价) | 借 1401 原材料 / 贷 2202 应付账款(暂估/发票) |
| 采购入库(无发票,暂估) | 借 1401 / 贷 2203 预付账款? 否 → 贷 2202 暂估(月末冲回) |
| 生产领料 | 借 5001 生产成本-直接材料 / 贷 1401 |
| 销售出库 | 借 6401 主营业务成本 / 贷 1401 |

**科目映射**:存货 1401(原材料)/1403(半成品)/1405(产成品,按物料类别),成本 5001,收入成本 6401,应付暂估 2202。映射放常量类(后续可配置化)。

### 4.4 GR/IR 暂估(月末)

- 接口 `POST /erp/fin/cost/provision(periodId)`:查询本期采购入库**未开票**明细(按采购单/收货单关联 pu_invoice),按暂估生成凭证(借 1401/贷 2202-暂估),`sourceId=periodId*100+N` 幂等。
- 发票到时(收票确认):`POST /erp/fin/cost/reverse(poNo)` 冲回暂估。
- **简化**:本期实现"按期间批量暂估 + 按采购单冲回",逐单暂估后置。

### 4.5 成本报表

复用 F5:`BS_INVENTORY`(1401,1403,1405)已映射,自动纳入。新增成本流水查询页面(可选,本期不做页面,仅接口)。

---

## 五、Controller 签名

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/erp/fin/cost/stockCost` | 库存成本余额(按物料/仓库汇总) |
| POST | `/erp/fin/cost/provision` | 期间暂估入库(幂等) |
| POST | `/erp/fin/cost/reverse` | 按采购单冲回暂估 |

> 权限 `fin:cost:*`,菜单挂在"财务管理 6100"下(仅 1 个页面"成本结转" 6181,可选)。

---

## 六、验收标准

1. 采购入库后库存 `unitCost=采购价`、`totalCost=价×量`,流水带 unitCost。
2. 生产领料/销售出库按移动平均出库成本结转,库存 totalCost 正确扣减。
3. 入库/出库自动生成 GL 凭证(借 1401/贷 2202,借 5001/贷 1401),借贷平衡。
4. 期间暂估入库生成凭证(借 1401/贷 2202-暂估),重跑幂等;按采购单冲回。
5. 库存成本余额(Σ totalCost)与 GL 1401 余额一致(勾稽)。
6. 既有 F5 报表 BS_INVENTORY 正确反映。

---

## 七、简化与后续

- 简化:不动调拨/盘点/返工/委外的计价;不做 BOM 卷积与差异分析(F3-b);不做逐单暂估与价格差异调整。
- 后续:计价方法配置化(先进先出等)、月末价格重估、与 MES 报工联动的生产成本归集。
