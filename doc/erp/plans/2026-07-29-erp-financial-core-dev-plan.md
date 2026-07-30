# Nexus-ERP 财务核心模块开发计划

> **文档编号**: NexusERP-PLAN-2026-005
> **创建日期**: 2026-07-29
> **状态**: 规划待评审
> **基于**: [2026-07-29-system-gap-analysis-vs-mainstream.md](../../system/2026-07-29-system-gap-analysis-vs-mainstream.md)(ERP 财务核心 0%)
> **关联/扩展**: [2026-07-15-invoicing-ar-ap-redesign.md](../specs/2026-07-15-invoicing-ar-ap-redesign.md)(发票域 P0-P2 已落地,P3-P5 已设计未实现,本计划承接并补齐会计核心)
> **对标产品**: SAP FI/CO、Oracle Cloud ERP、用友 U8/YonBIP、金蝶 K3 Cloud、Odoo Accounting、ERPNext Accounts
> **信息来源**: 差距分析报告、发票域 spec、代码结构扫描(4 路并行:ERP 财务代码现状 + 主流 ERP 财务矩阵调研)

---

## 一、背景与目标

### 1.1 背景

差距分析报告(`doc/system/2026-07-29-system-gap-analysis-vs-mainstream.md`)指出:**ERP 财务核心完全缺失**是 Nexus-ERP 最大短板,使系统停留在"进销存 + 生产"层级,无法与 SAP/Oracle/用友/金蝶在财务维度竞争。

| 能力域 | 主流 ERP | Nexus-ERP | 等级 |
|---|---|---|---|
| 总账 GL(科目表/凭证/日记账/试算) | 标配 | 完全不存在 | 🔴 |
| 应收应付 AR/AP(核销/账龄) | 标配 | spec 已设计 P3,`com.nexus.erp.fin` 包为空,未编码 | 🔴 |
| 固定资产 FA(折旧/处置) | 标配 | 无 | 🔴 |
| 成本核算 CO(标准/实际成本/差异) | 标配 | 字段预埋,无核算逻辑 | 🔴 |
| 预算管理 | 标配 | 无 | 🔴 |
| 多组织/多法人 | 标配 | 仅租户隔离 | 🟠 |

发票域已有工作(`doc/erp/specs/2026-07-15-invoicing-ar-ap-redesign.md`):**P0/P1/P2 已落地**(销售发票 `so_invoice` + 采购进项 `pu_invoice`,含状态机/三单匹配/进项认证/红冲/退货进项转出/Flowable 审批),**P3(AR/AP 核销)/P4(电票)/P5(对账)已设计未实现**。但该 spec 聚焦"发票 + 收付款核销"链路,**未覆盖会计核心**(GL/FA/CO/预算)。

### 1.2 目标

补齐财务会计核心,使 Nexus-ERP 从"进销存 + 生产"升级为完整 ERP:

1. **总账 GL 为地基**:科目表 + 复式记账凭证 + 会计期间,所有业务子账过账到此
2. **AR/AP 子账化**:发票/收付款/核销自动生成 GL 凭证(承接 spec P3,补 GL 过账维度)
3. **固定资产 FA / 成本核算 CO / 预算**:补齐主流标配
4. **财务报表与期末关账**:试算/资产负债/利润/现金流量 + 月结年结
5. **对账与电票**:承接 spec P5/P4

### 1.3 范围边界

- **本次纳入**:GL / AR-AP / FA / CO / 预算 / 报表与关账 / 对账 / 电票网关
- **后置(非本次)**:多组织/多法人独立核算(当前租户隔离够用)、资金管理/银企直联、合并报表、应收应付票据、内部订单/项目核算、获利能力分析(CO-PA)
- **不做**:不重做已落地的发票域 P0-P2;不迁移 `so_invoice`/`pu_invoice` 表(随 so/pu 模块,见 spec 决议 11.1 方案 C)

---

## 二、对标依据:主流 ERP 财务功能矩阵

### 2.1 功能覆盖矩阵(精简)

| 一级模块 | Odoo | ERPNext | 用友 U8 | 金蝶 K3 | SAP FI/CO | Nexus-ERP 现状 |
|---|---|---|---|---|---|---|
| **总账 GL** | account.move/line 复式 | GL 自动分录 | 凭证/辅助核算/期末结转 | 凭证/账簿 | FS00/FB50 借贷平衡 | 🔴 无 |
| **应收 AR** | move line 核销 | 销售发票+收款分录 | 应收单/收款/坏账/票据 | 合同+收款 | VF01+统驭科目 | 🟡 发票有,核销/过账无 |
| **应付 AP** | 账单核销 | 采购发票+付款分录 | 应付单/付款/票据 | 合同+付款 | MIRO+GR/IR 清账 | 🟡 发票有,核销/过账无 |
| **固定资产 FA** | 草稿→过账 | 卡片+折旧+处置+CWIP | 卡片/增减/折旧/盘点 | 卡片+折旧 | AS01/AFAB/ABUMN | 🔴 无 |
| **成本 CO** | 有限 | 制造模块 | 产品/订单/工序成本卷积 | 费用预算 | CO-PC+CO-CCA | 🔴 字段有,逻辑无 |
| **预算** | 有限 | Stop/Warn/Ignore | 全面预算 | 费用预算 | — | 🔴 无 |
| **资金/对账** | 银行对账 | — | 银企直联+对账机器人 | 科目计息 | — | 🔴 无 |
| **报表** | 内置全套 | 内置 | UFO 取数公式 | 内置 | — | 🟡 仅积木报表基础 |

### 2.2 借鉴的关键架构概念

| 概念 | 来源 | 对本计划的意义 |
|---|---|---|
| **统驭科目(Reconciliation Account)** | SAP FI | AR/AP/FA 作为子账,通过统驭科目汇总到总账,明细独立管理。是区分"进销存"与"真财务"的关键架构 |
| **未清项管理(Open Item)** | SAP FI | 发票/收付款行项目区分已核销/未核销,支持自动清账,减少手工 |
| **自动清账** | SAP F.13 | 按 PO+行号等键自动匹配借贷项,金额归零自动核销 |
| **特别总账标识** | SAP FI | 预收/预付/票据通过标识位映射到备选科目,无需独立模块 |
| **GR/IR 清账** | SAP FI | 采购收货与发票校验差异通过中间科目自动匹配,差异归集 |
| **期间控制** | SAP/用友 | 严格按会计期间控制,非当前期间交易需特殊授权,月结锁定 |
| **业务自动制单** | Odoo/ERPNext | 发票/收付款/出入库过账时自动生成记账凭证,不增加用户操作 |

### 2.3 中小制造/贸易企业 MVP 范围结论

| 优先级 | 功能 | 对应本计划阶段 |
|---|---|---|
| **P0 必须** | GL 科目表 + 凭证 + 业务自动制单 + AR/AP 核销 + 三大报表 + 期间 + 多币种 | F0 / F1 / F5 |
| **P1 建议** | 统驭科目 + 未清项 + 银行对账 + 存货成本 + 固定资产 + 预收预付 + 账龄 | F1 / F2 / F3(存货部分) / F6 |
| **P2 后置** | 预算 + 成本中心 + 产品成本(标准/差异) + 资金 + 合并报表 + 票据 | F3(产品成本) / F4 / 后置清单 |

> 本计划阶段编排与 MVP 结论对齐:GL(F0)与 AR/AP(F1)优先,FA(F2)次之,产品成本(F3)与预算(F4)后置。试算平衡表前置到 F0 满足 P0 报表底线,完整三大报表在 F5(需各子账过账数据齐全)。

---

## 三、现状基础评估

### 3.1 可复用基础(无需从零)

| 基础 | 证据 | 复用方式 |
|---|---|---|
| **发票模块(P1/P2 已完成)** | `SoInvoiceServiceImpl`(generateDraftFromOrder/verifyInvoice/cancelInvoice/redFlush/差量更新+乐观锁)、`PuInvoiceServiceImpl`(generateDraftFromPo/matchThreeWay/certifyInvoice/deductInvoice/redFlushForReturn) | F1 发票过账的源头单据;`verifyInvoice()` 已预留 `receiptId` 参数待 P3 核销消费 |
| **客户/供应商开票四要素** | `BdClient.java:116-129`(taxId/invoiceTitle/invoiceBankName/invoiceBankAccount/invoiceAddressPhone/defaultInvoiceType)、`BdVendor.java:41-70` | 发票过账取销售方/购买方信息,已完备 |
| **客户信用管理** | `BdClientCredit`(clientCredit/clientTime/clientLevel/clientScore) | AR 信用控制直接复用 |
| **支付网关** | `ruoyi-pay`:AliPay/WxPay/SQB 三通道 + `PayOrder` + `PayService.notify()` SPI + `MoneyUtil` | F1 支付回调→收款单链路;`notify()` 需扩展发 MQ |
| **财务异步线程池** | `FinancialThreadPoolConfig.java:32`(financialExecutor,core4/max16/queue200) | 核销/过账/电票异步任务复用,无需重建 |
| **价税分离** | so/pu 订单/合同/退货全链路含不含税金额、税额、价税合计 | 发票过账借贷拆分(收入/进项税/销项税)的数据基础 |
| **成本字段预埋** | `BdMaterial.standardCost`/`referencePrice`、`WmMaterialStock.unitCost`/`totalCost`、`WmMaterialStockTx.unitCost`/`txAmount`、`BdRoutingStep.laborRate`/`machineRate`(注释"BOM 成本滚算依赖") | F3 成本核算的字段地基(缺计算逻辑) |
| **字典预配** | `ry-vue.sql:12830-12831`(`fin_receipt_type`/`fin_receipt_direction`) | F1 收付款单字典已就绪 |
| **跨库 MQ 模式** | `QueueConstants` + `StockInConsumer`/`PurchaseReceiveWritebackConsumer`(含 `TenantHelper.dynamic` 租户恢复) | F1 `PayReceivedConsumer` 照搬模式 |

### 3.2 从零开始(需开发)

| 缺口 | 说明 |
|---|---|
| **`com.nexus.erp.fin` 包** | 完全不存在,仅有 `FinancialThreadPoolConfig` 在 `config` 包 |
| **GL 总账全套** | 无 `gl_` 表(科目表/凭证/凭证行/期间/汇率),无凭证引擎 |
| **AR/AP 核销** | 无 `fin_receipt`/`fin_settlement` 表与服务(spec P3 已设计表结构,待实现) |
| **业务记账联动** | 发票/收付款/出入库/折旧/成本 → GL 凭证的自动制单,完全无 |
| **固定资产 FA** | 无 `fa_` 表,无卡片/折旧/处置 |
| **成本核算 CO** | 无 BOM 成本卷积、无存货成本结转引擎、无标准/实际差异分析(字段有逻辑无) |
| **预算管理** | 无 `budget_` 表,无编制/控制/预警 |
| **财务报表** | 无试算/资产负债/利润/现金流量(仅积木报表基础) |
| **期末关账** | 无期间锁定/损益结转/月结年结流程 |
| **财务 MQ 通道** | `QueueConstants` 无 `erp.pay.received`/`erp.einvoice.result` 等通道 |

### 3.3 关键约束(继承项目既有坑)

- **异步租户上下文**:核销/过账/电票异步任务须 `TenantHelper.dynamic(tenantId, ...)` 包裹,否则 `tenant_id` 落 0(见 [[ai-async-tenant-context]]、发票域 P1 监听器模式)
- **雪花 ID 前端精度**:新增财务接口返回雪花 ID 已由全局 `BigNumberSerializer` 序列化为字符串,勿绕过
- **乐观锁初始化**:`fin_receipt`/`gl_voucher` 等含 `version` 字段的表,新建须 `setVersion(0)`(见 [[wm-stock-version-optimistic-lock]])
- **多数据源**:财务表落 `ry-erp` 库,Service 须 `@DS("erp")`;pay 跨库(ry-vue)走 MQ 不跨库 JOIN(见 spec 2.1)
- **字典/菜单主键**:增量字典/菜单 SQL 用雪花大 ID 或 `@var:=@var+1` 动态分配,勿硬编码小 ID(见 [[ruoyi-snowflake-dict-menu-id]])

---

## 四、目标架构

### 4.1 分层架构

```
┌──────────────────────────────────────────────────────────────┐
│                    财务报表层(F5)                              │
│  试算平衡表 │ 资产负债表 │ 利润表 │ 现金流量表 │ AR/AP 账龄      │
└───────────────────────────┬──────────────────────────────────┘
                            │ 读取 GL 余额 / 子账明细
┌───────────────────────────┴──────────────────────────────────┐
│               总账服务层 GL(F0,com.nexus.erp.fin.gl)          │
│  科目表(树状/统驭标识) │ 凭证引擎(借贷平衡/过账/反过账)        │
│  会计期间(锁定) │ 多币种(本币+原币双存储) │ 期末损益结转       │
└──────┬──────────────┬──────────────┬──────────────┬──────────┘
       │              │              │              │
┌──────┴──────┐ ┌─────┴─────┐ ┌──────┴──────┐ ┌─────┴─────┐
│  AR/AP 子账  │ │  FA 子账  │ │  CO 成本    │ │  预算     │
│   (F1)      │ │   (F2)   │ │   (F3)      │ │  (F4)    │
│ 发票→应收应付│ │ 卡片→折旧 │ │ BOM卷积/存货 │ │ 科目预算  │
│ 收付款→核销  │ │ 处置→损益 │ │ 生产成本/差异│ │ 超支控制  │
│ 账龄/预收预付│ │           │ │             │ │           │
└──────┬──────┘ └─────┬─────┘ └──────┬──────┘ └─────┬─────┘
       │              │              │              │
       └──────────────┴──────┬───────┴──────────────┘
                             │ AccountingPoster SPI 自动制单
┌────────────────────────────┴─────────────────────────────────┐
│               业务集成层(已有基础,不重做)                     │
│  销售 so │ 采购 pu │ 仓储 wm │ 生产 prd │ 发票 so/pu_invoice │
│  支付 pay(ry-vue,跨库 MQ) │ 财务对账(F6) │ 电票网关(F7)      │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 代码归属(承接 spec 决议 11.1 方案 C)

- **发票**(`so_invoice`/`pu_invoice`):随 so/pu 模块(已落地,不迁移)
- **财务能力**集中 `com.nexus.erp.fin`:
  - `fin.gl` — 总账(科目/凭证/期间/汇率)
  - `fin.arap` — 收付款单/核销/账龄(承接 spec P3)
  - `fin.fa` — 固定资产
  - `fin.co` — 成本核算
  - `fin.budget` — 预算
  - `fin.report` — 财务报表
  - `fin.reconciliation` — 对账(承接 spec P5)
  - `fin.einvoice` — 电票网关(承接 spec P4)
- **防循环依赖**:fin 单向依赖 so/pu Service 接口(`ISoInvoiceService`/`IPuInvoiceService`),so/pu 不反向依赖 fin;跨域查询走 Service 聚合,不写跨模块 XML JOIN

### 4.3 核心架构决策

1. **统驭科目模式**(借鉴 SAP):AR/AP/FA 子账通过"统驭科目"汇总到 GL。客户/供应商/资产作为辅助核算项,明细在子账,余额自动反映在 GL 统驭科目。发票过账时贷/借统驭科目(如"应收账款"),核销时清账明细。
2. **AccountingPoster SPI**(借鉴 Odoo/ERPNext 自动制单):各业务模块实现记账规则,业务单据状态变更时触发对应 Poster 生成 GL 凭证。新增业务类型只需新增 Poster 实现,不改凭证引擎。
3. **未清项管理**:发票/收付款 GL 行项目带 `open_item` 标记与 `cleared` 状态,核销即清账,支持自动清账(按客户/供应商+金额+日期匹配)。
4. **凭证借贷平衡**:凭证过账前强制校验 `SUM(debit) == SUM(credit)`,不平衡拒绝过账。
5. **期间控制**:凭证过账校验会计期间未锁定;月结/年结锁定已结期间,反过账需特殊授权。
6. **多币种双存储**:外币凭证行同时存本币金额(`debit`/`credit`)与原币金额(`amount_currency`)+ 汇率,期末按期末汇率重估生成汇兑损益凭证。

---

## 五、分阶段实施计划

> 阶段编号 F0-F7(F=Financial),与发票域 spec 的 P0-P5 区分。映射见第十一节。

### F0 — GL 总账地基(必须最先,2-3 周)

**目标**:搭起科目表 + 凭证 + 期间 + 多币种地基,提供借贷平衡的凭证引擎与试算平衡表。所有后续子账过账的基座。

**交付物**:
- `fin.gl` 包:`GlAccount`/`GlVoucher`/`GlVoucherLine`/`GlPeriod`/`GlCurrencyRate` + Controller/Service/Impl/Mapper/BO/VO
- `AccountingPoster` SPI 接口 + `GlVoucherService.post()/unpost()` 凭证引擎(借贷平衡校验、过账/反过账)
- 会计期间管理(开账/锁定/月结年结状态机)
- 试算平衡表查询(按期间/科目层级汇总借贷余额)
- 多币种:汇率维护 + 外币凭证原币双存储
- 初始化科目表(参照 2014 年《企业会计准则》一级科目,含资产/负债/权益/成本/损益五类,预置 AR/AP 统驭科目)

**数据模型**(核心字段,详细 DDL 见实施):
```sql
gl_account(account_id, code, name, account_type[ASSET/LIABILITY/EQUITY/COST/INCOME/EXPENSE],
           parent_id, is_leaf, is_recon[统驭标识], recon_type[AR/AP/FA], balance_dir, status)
gl_voucher(voucher_id, voucher_no, voucher_date, period_id, voucher_source[MANUAL/SO_INVOICE/PU_INVOICE/RECEIPT/...],
           attachment_count, status[DRAFT/POSTED/CANCELLED], version)
gl_voucher_line(line_id, voucher_id, line_no, account_id, debit, credit, currency_id,
                amount_currency, exchange_rate, partner_id[客户/供应商], partner_type,
                open_item[是否未清项], cleared[已清账], cleared_by_settlement, remark)
gl_period(period_id, fiscal_year, period_no, start_date, end_date, status[OPEN/CLOSED/Locked])
gl_currency_rate(rate_id, currency, rate_date, rate, base_currency)
```

**验收**:科目表树状维护;手工录入凭证并过账(借贷不平衡被拒);试算平衡表借贷合计相等;期间锁定后该期间凭证不可过账;外币凭证同时存本币/原币。

### F1 — AR/AP 子账与核销(承接 spec P3,增强 GL 过账,3-4 周)

**目标**:实现收付款单 + 核销 + 账龄,并**补 spec P3 缺失的 GL 过账维度**——发票/收付款/核销自动生成 GL 凭证,通过统驭科目汇总到总账。打通 pay 支付回调→收款单→自动核销跨库链路。

**交付物**:
- `fin.arap` 包:`FinReceipt`/`FinSettlement`(表结构复用 spec 7.1 设计)+ Controller/Service/Impl
- `FinSettlementService.settle()`(复用 spec 7.2 设计:写核销记录→原子累加发票 `verified_amount`→累加收付款 `settled_amount`→达额转 VERIFIED/SETTLED)
- **NEW 发票过账**:`so_invoice` ISSUED 时生成凭证(借应收账款-客户 / 贷主营业务收入 + 贷销项税额);`pu_invoice` ISSUED 时生成凭证(借存货/费用 + 借进项税额 / 贷应付账款-供应商)。通过 `AccountingPoster` 实现
- **NEW 收付款过账**:`fin_receipt` CONFIRMED 时生成凭证(收款:借银行存款 / 贷应收账款;付款:借应付账款 / 贷银行存款)
- **NEW 核销清账**:核销时清账对应未清项(`gl_voucher_line.cleared=true`),统驭科目明细归零
- 预收/预付(特别总账标识:预收走"预收账款"科目,核销时转入应收)
- pay 回调→MQ→`PayReceivedConsumer`→创建 `fin_receipt`(source=PAY_CALLBACK)→自动核销(复用 spec 7.4 设计)
- AR/AP 账龄报表(0-30/30-60/60-90/90+ 天,基于发票 `total_amount - verified_amount`)
- 权限拆分:`so:invoice:verify`/`pu:invoice:verify` 独立财务权限(补 spec P1 遗留 TODO)

**数据模型**:`fin_receipt`/`fin_settlement` 复用 spec 7.1;新增 `gl_voucher_line.open_item`/`cleared` 字段(F0 已含)。MQ 通道见第十节。

**依赖**:F0(GL 凭证引擎)、发票域 P1/P2(已完成)。

**验收**:销售/采购发票 ISSUED 自动生成借贷平衡凭证;录收款单核销发票→`verified_amount` 回写+GL 未清项清账+达额转 VERIFIED;pay 支付成功→ERP 自动生成收款单→自动核销;AR/AP 账龄报表正确;预收/预付可抵扣。

### F2 — 固定资产 FA(2-3 周)

**目标**:资产卡片全生命周期(购置→折旧→变动→处置),折旧/处置自动生成 GL 凭证。

**交付物**:
- `fin.fa` 包:`FaAsset`/`FaAssetCategory`/`FaDepreciation`/`FaDisposal` + Controller/Service/Impl
- 资产类别决定折旧政策(直线法/双倍余额递减法/年数总和法/手工)
- 折旧计划自动生成 + 过账(借费用科目 / 贷累计折旧),通过 `AccountingPoster`
- 资产变动(转移/原值调整)、处置(出售/报废)计算处置损益并过账
- 在建工程(CWIP)转固
- 资产台账与折旧分析报表

**数据模型**:
```sql
fa_asset_category(category_id, name, debit_account_id[费用科目], credit_account_id[累计折旧],
                  disposal_loss_account, disposal_gain_account, depreciation_method, useful_life)
fa_asset(asset_id, asset_no, name, category_id, original_value, salvage_value, useful_life,
         depreciation_method, acquisition_date, status[IN_USE/CWIP/DISPOSED], location, version)
fa_depreciation(depreciation_id, asset_id, period_id, depreciation_amount, posted_voucher_id, status)
fa_disposal(disposal_id, asset_id, disposal_type[SALE/SCRAP], disposal_date, net_book_value,
            proceeds, gain_loss, posted_voucher_id)
```

**依赖**:F0(GL)、F1(期间一致)。

**验收**:资产卡片可建;按月自动计提折旧并生成凭证;资产出售/报废生成处置损益凭证;台账与累计折旧余额与 GL 一致。

### F3 — 成本核算 CO(4-5 周,最复杂,依赖 MES 成熟度)

**目标**:补齐制造业核心——BOM 成本卷积 + 存货成本结转 + 生产成本归集 + 标准/实际成本差异分析,均过账到 GL。

> **分两子阶段**:F3-a 存货成本(可达,P1 建议);F3-b 产品成本卷积与差异(P2 后置,依赖 MES 报工数据成熟)。

**交付物(F3-a 存货成本)**:
- 存货计价引擎:加权平均/移动平均/先进先出(可配置),更新 `WmMaterialStock.unitCost`/`totalCost`
- 出入库成本结转:入库借存货/贷应付或暂估;出库贷存货/借成本或费用,通过 `AccountingPoster`
- 暂估入库与 GR/IR 清账(月末发票未到按暂估入账,发票到时冲回,借鉴 SAP GR/IR)

**交付物(F3-b 产品成本)**:
- BOM 成本卷积引擎:按 BOM 层级 + 工序 `laborRate`/`machineRate` 滚算物料标准成本(直材/直接人工/制造费用)
- 生产成本归集:工单领料→直接材料;报工→直接人工+制造费用;完工入库→贷生产成本/借存货
- 标准 vs 实际成本差异分析(量差/价差),差异分摊过账

**数据模型**:
```sql
co_product_cost(cost_id, material_id, cost_type[STANDARD/ACTUAL], period_id,
                material_cost, labor_cost, overhead_cost, total_cost, version)
co_bom_cost(bom_cost_id, bom_id, material_id, rolled_material, rolled_labor, rolled_overhead, total)
co_cost_variance(variance_id, work_order_id, material_id, variance_type[QTY/PRICE/RATE],
                 standard_cost, actual_cost, variance_amount, period_id)
```

**依赖**:F0(GL)、F1(应付/暂估)、MES 报工与领料数据(F3-b)。**前置条件**:MES 工序报工成本字段稳定。

**验收**:入库自动按计价方法更新单位成本并生成凭证;月末暂估入库与发票到货冲回正确;BOM 卷积标准成本可算;工单完工归集生产成本并结转存货;差异报表正确。

### F4 — 预算管理(2 周)

**目标**:科目/部门/项目维度预算编制与控制,超支预警/拦截。

**交付物**:
- `fin.budget` 包:`BudgetHeader`/`BudgetLine` + Controller/Service/Impl
- 预算编制(年度/月度分布模板)、审批、调整
- 预算控制策略(Stop 拦截/Warn 预警/Ignore 不控制,借鉴 ERPNext)
- 凭证过账时按科目+维度检查预算占用,超支按策略处理
- 预算执行报表(预算 vs 实际 vs 差异)

**数据模型**:
```sql
budget_header(budget_id, budget_no, fiscal_year, scope_type[ACCOUNT/DEPT/PROJECT], status, version)
budget_line(line_id, budget_id, account_id, dept_id, project_id, period_id,
            budget_amount, used_amount, control_strategy[STOP/WARN/IGNORE])
```

**依赖**:F0(GL 凭证过账触发预算检查)。

**验收**:可编制科目/部门预算;凭证过账超预算时按策略拦截/预警;执行报表显示预算/实际/差异。

### F5 — 财务报表与期末关账(3 周)

**目标**:三大法定报表 + 期末损益结转 + 月结年结流程。

**交付物**:
- `fin.report` 包:试算平衡表(F0 已基础版,此处增强)、资产负债表、利润表、现金流量表
- 报表取数引擎:基于 GL 余额 + 科目类型映射(资产/负债/权益/损益),支持报表项目与科目的映射配置
- 期末损益结转:收入/费用科目余额结转至"本年利润"生成结转凭证
- 外币期末重估:外币科目按期末汇率重估,生成汇兑损益凭证
- 月结流程向导:费用分摊→AR/AP 核销→折旧→存货成本→损益结转→报表→期间锁定
- 年结:本年利润→未分配利润、资产/负债科目余额结转为下年期初

**依赖**:F0-F4 全部(报表需各子账过账数据齐全)。

**验收**:三大报表与 GL 余额勾稽一致;损益结转后损益类科目余额为 0;月结锁定后该期间不可过账;年结后期初余额正确。

### F6 — 财务对账(承接 spec P5,2 周)

**目标**:银行流水 vs 系统收付款单勾对,对账批次,差异处理,余额调节表。

**交付物**:
- `fin.reconciliation` 包:`FinReconciliation`/`FinReconciliationDetail`(表结构复用 spec 9.2)+ Controller/Service/Impl
- 银行对账单导入(Excel/FastExcel)
- 自动勾对策略(金额+日期±容差,优先 `third_party_no` 精确匹配)
- 差异人工处理、对账批次关闭、余额调节表生成

**依赖**:F1(`fin_receipt` 数据)。

**验收**:银行流水导入自动勾对收付款单;差异可人工处理;对账批次可关闭并生成余额调节表。

### F7 — 电子发票网关(承接 spec P4,2-3 周)

**目标**:`EInvoiceGateway` SPI + 数电票开具/红冲/查询/下载,先 Mock 后接平台。

**交付物**:
- `fin.einvoice` 包:`EInvoiceGateway` SPI + `ManualEInvoiceGateway`(默认 Mock)+ 平台实现占位(百望/航信/用友)
- `so_invoice.issue_channel=ELECTRONIC` 时调网关开具,返回数电票号+PDF/OFD,存 `e_invoice_no`/`pdf_oss_id`/`ofd_oss_id`
- `EInvoiceResultConsumer` 处理平台异步回调(MQ 通道见第十节)
- 配置 `nexus.einvoice.*`(凭证入 `application-secret.yml`)

**依赖**:发票域 P1(so_invoice 已有电票字段)。

**验收**:`issue_channel=ELECTRONIC` 调网关(Manual 模式仅改状态)返回数电票号与 PDF;红冲走平台;凭证就绪后切配置零改业务代码。

---

## 六、核心数据模型汇总

| 模块 | 表 | 来源 |
|---|---|---|
| GL | `gl_account`/`gl_voucher`/`gl_voucher_line`/`gl_period`/`gl_currency_rate` | F0 新建 |
| AR/AP | `fin_receipt`/`fin_settlement` | spec 7.1 已设计,F1 实现 |
| FA | `fa_asset`/`fa_asset_category`/`fa_depreciation`/`fa_disposal` | F2 新建 |
| CO | `co_product_cost`/`co_bom_cost`/`co_cost_variance` | F3 新建 |
| 预算 | `budget_header`/`budget_line` | F4 新建 |
| 对账 | `fin_reconciliation`/`fin_reconciliation_detail` | spec 9.2 已设计,F6 实现 |
| 发票(已有) | `so_invoice`/`so_invoice_line`/`pu_invoice`/`pu_invoice_line` | P1/P2 已落地 |
| 支付(已有) | `pay_order` | ruoyi-pay |

> 所有新表落 `ry-erp` 库,带 `tenant_id`/审计字段/`version` 乐观锁/`del_flag`,DDL 并入 `script/sql/ry-mes.sql`?— 否,并入 `script/sql/ry-erp.sql`(业务库),菜单/字典并入 `script/sql/ry-vue.sql`。增量脚本放 `script/sql/update/`(权威 schema 已并入主 SQL,见 CONTRIBUTING §5)。

---

## 七、关键技术决议

| # | 决议 | 理由 |
|---|---|---|
| 1 | **统驭科目模式**(AR/AP/FA 子账→GL 汇总) | 借鉴 SAP,明细独立、余额自动汇总,区分"进销存"与"真财务" |
| 2 | **AccountingPoster SPI 自动制单** | 借鉴 Odoo/ERPNext,业务单据过账自动生成凭证,新增业务类型只加 Poster |
| 3 | **凭证借贷平衡强校验** | 复式记账铁律,过账前 `SUM(debit)==SUM(credit)` 否则拒绝 |
| 4 | **未清项管理 + 自动清账** | 发票/收付款 GL 行带 `open_item`/`cleared`,核销即清账,支持自动匹配 |
| 5 | **期间锁定控制** | 月结/年结冻结已结期间,反过账需授权 |
| 6 | **多币种本币+原币双存储** | 外币凭证行存 `debit`/`credit`(本币)+`amount_currency`(原币)+汇率,期末重估 |
| 7 | **财务表落 ry-erp 库,`@DS("erp")`** | 与发票域一致;pay 跨库走 MQ |
| 8 | **复用 `financialExecutor` 线程池** | 核销/过账/电票异步任务,与 aiExecutor 隔离(spec 11.4) |
| 9 | **复用发票域 P1/P2,不迁移不重做** | so/pu_invoice 已完整,只加过账 Poster |
| 10 | **多组织/多法人后置** | 当前租户隔离够用,独立核算是未来层 |

---

## 八、风险与依赖

| 风险/依赖 | 应对 |
|---|---|
| **GL 是全局地基,F0 阻塞所有后续** | F0 优先开工,范围严格收敛(科目表+凭证+期间+试算),不贪大 |
| **发票过账改造触碰已落地 P1/P2** | 过账用 `AccountingPoster` 在状态变更后异步触发,不改发票状态机主流程;过账失败不影响发票状态(补偿) |
| **跨库 MQ 消息丢失/重复**(pay→ERP) | 消费者幂等(`fin_receipt` 按 `third_party_no`/`pay_order_number` 去重),参考 `StockInConsumer` |
| **异步租户上下文丢失** | 所有异步过账/核销 `TenantHelper.dynamic` + 消息体带 tenantId |
| **核销并发超开** | `verified_amount`/`settled_amount` 用 `setSql` 原子累加 + 乐观锁,核销前校验余额 |
| **成本核算依赖 MES 报工数据成熟度** | F3 拆 F3-a(存货成本,可达)/F3-b(产品成本,待 MES 稳定) |
| **数电票平台凭证缺失** | F7 先 ManualEInvoiceGateway Mock,凭证就绪再接(spec 11.3) |
| **科目表初始化与既有业务对齐** | F0 预置准则一级科目 + AR/AP 统驭科目,与 so/pu 发票过账科目映射在 F1 配置 |
| **多币种汇率来源** | 先手工维护 `gl_currency_rate`,后置对接汇率源 API |

---

## 九、验收标准(各阶段汇总)

- **F0**:科目树维护;凭证借贷平衡校验生效;试算平衡表借贷相等;期间锁定生效;外币双存储。
- **F1**:发票 ISSUED 自动生成借贷平衡凭证;收付款核销回写 `verified_amount`+GL 清账;pay 回调→自动收款→自动核销;账龄报表正确;预收预付可抵扣。
- **F2**:资产卡片建账;月度折旧自动过账;处置损益凭证;台账与 GL 累计折旧一致。
- **F3-a**:出入库按计价方法更新成本并过账;暂估入库与冲回正确。
- **F3-b**:BOM 卷积标准成本;工单完工归集结转;差异报表。
- **F4**:预算编制;超支按策略拦截/预警;执行报表。
- **F5**:三大报表与 GL 勾稽一致;损益结转后损益类余额为 0;月结/年结锁定。
- **F6**:银行流水自动勾对;差异处理;余额调节表。
- **F7**:电票开具返回数电票号+PDF;红冲走平台;Mock→平台零改业务。

---

## 十、MQ 通道设计(新增 QueueConstants)

承接 spec 第十节,补充 GL 过账相关:

```java
// spec 已设计
String PAY_RECEIVED_QUEUE = "erp.pay.received.queue";        // pay -> ERP 收款单
String EINVOICE_ISSUE_RESULT_QUEUE = "erp.einvoice.issue.result.queue";
String EINVOICE_CERT_RESULT_QUEUE = "erp.einvoice.cert.result.queue";
// 本计划新增(GL 过账,业务事件 -> fin.gl 异步制单)
String GL_POSTING_QUEUE = "erp.gl.posting.queue";            // 业务单据过账事件 -> AccountingPoster
```

| 队列 | 生产者 | 消费者 | 消息体 |
|---|---|---|---|
| `PAY_RECEIVED_QUEUE` | pay `notify()` | `PayReceivedConsumer` | orderNumber/thirdPartyNo/actualAmount/payTime/tenantId/bizType/bizId |
| `EINVOICE_ISSUE_RESULT_QUEUE` | 电票平台回调 | `EInvoiceResultConsumer` | eInvoiceNo/status/pdfOssId |
| `GL_POSTING_QUEUE` | so/pu/wm/fa/co 状态变更 | `GlPostingConsumer` | sourceType[SO_INVOICE/PU_INVOICE/RECEIPT/DEPRECIATION/...]/sourceId/tenantId |

> 所有跨库/异步消费者写多租户表必须 `TenantHelper.dynamic` 恢复租户,消息体显式携带 `tenantId`(字符串避免雪花 ID 精度丢失)。

---

## 十一、与已有发票域 spec 的衔接映射

| 发票域 spec 阶段 | 状态 | 本计划映射 |
|---|---|---|
| P0 基础修补(开票四要素/invoicedQty/价税分离/pay decimal/字典) | ✅ 已落地 | F1 复用 |
| P1 销售开票闭环 | ✅ 已落地(E2E 基本通过) | F1 发票过账源头 |
| P2 采购进项发票 | ✅ 已落地(编译/造数通过,运行时待跑) | F1 发票过账源头 |
| **P3 AR/AP + 核销** | 📐 已设计未实现 | **F1 承接**(fin_receipt/fin_settlement/settle 复用 spec 设计 + 补 GL 过账) |
| **P4 电子发票网关** | 📐 已设计未实现 | **F7 承接**(EInvoiceGateway SPI + 数电票) |
| **P5 财务对账** | 📐 已设计未实现 | **F6 承接**(fin_reconciliation + 银行勾对) |
| —(spec 未覆盖) | ❌ | **F0 GL / F2 FA / F3 CO / F4 预算 / F5 报表关账**(本计划新增) |

> 本计划不替代发票域 spec,而是**在其之上补齐会计核心**。spec 的 P3/P4/P5 设计(表结构、MQ、决议)直接复用,F1/F6/F7 在其基础上增加 GL 过账维度。

---

## 十二、文件清单与工作量预估

| 阶段 | 工作量 | 主要新增 |
|---|---|---|
| F0 GL 总账 | 2-3 周 | `fin.gl` 整包(5 实体)+ 科目初始化 SQL + 试算报表 |
| F1 AR/AP 核销 | 3-4 周 | `fin.arap` 整包 + AccountingPoster(发票/收付款)+ `PayReceivedConsumer` + 账龄报表 + 权限拆分 |
| F2 固定资产 | 2-3 周 | `fin.fa` 整包(4 实体)+ 折旧引擎 + 处置 |
| F3 成本核算 | 4-5 周 | `fin.co` 整包 + BOM 卷积 + 存货计价 + 差异(F3-a 先行) |
| F4 预算 | 2 周 | `fin.budget` 整包 + 预算检查拦截 |
| F5 报表与关账 | 3 周 | `fin.report` + 三大报表取数 + 损益结转 + 月结年结向导 |
| F6 财务对账 | 2 周 | `fin.reconciliation` 整包 + 银行流水导入/勾对 |
| F7 电票网关 | 2-3 周 | `fin.einvoice` SPI + Manual 实现 + 平台占位 |
| **合计** | **~20-25 人周** | 分阶段交付,F0+F1 为最高优先级(MVP P0) |

> 建议实施顺序:F0 → F1 → F5(试算+基础报表,验证 GL 闭环)→ F2 → F3-a → F6 → F4 → F3-b → F7。F7 可与任意阶段并行(Mock 不阻塞)。

---

## 十三、下一步

1. **评审本计划**,确认阶段范围、统驭科目架构、AccountingPoster SPI 设计
2. **F0 GL 总账**先行,产出详细 impl spec(科目表 DDL + 凭证引擎 + 期间状态机),落 `doc/erp/specs/`
3. F0 落地后启动 **F1 AR/AP 核销**,承接发票域 spec P3 设计并补 GL 过账
4. 同步更新 `doc/PROGRESS.md`/`doc/PLANS.md` 财务核心条目与 [[invoicing-ar-ap-redesign]] 记忆

> 本计划为战略级开发规划,各阶段实施前产出详细 impl spec(含完整 DDL/接口签名/调用序列),参考发票域 spec 的 P1/P2 实施记录模式。
