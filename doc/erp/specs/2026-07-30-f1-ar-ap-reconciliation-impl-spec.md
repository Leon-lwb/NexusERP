# F1 AR/AP 核销 实施规格

> 日期:2026-07-30
> 承接:发票域 spec P3(`doc/erp/specs/2026-07-15-invoicing-ar-ap-redesign.md` 第七节)
> 前置:F0 GL 总账(`doc/erp/specs/2026-07-29-f0-gl-ledger-impl-spec.md`)已就绪
> 核心约束:**凭证引擎(GlVoucherService)与余额服务(GlAccountBalanceService)零改动**--F1 通过 F0 的 AccountingPoster SPI + 未清项字段(open_item/cleared/cleared_by)接入。

## 一、概述

实现 AR/AP 核销闭环:发票开票自动过账 GL → 收付款确认过账 GL → 核销清账 → 支付回调自动建单核销。

承 spec P3 的 `fin_receipt`/`fin_settlement` 设计,补齐 P3 缺失的 GL 过账维度(F0 已补):发票 ISSUED/收款 CONFIRMED 通过 AccountingPoster SPI 自动制单,核销时清账 gl_voucher_line。

## 二、架构决策

| # | 决策 | 说明 |
|---|------|------|
| 1 | 零改动 F0 | 不改 GlVoucherService/GlAccountBalanceService 代码与表。Poster 调 `createAndPost()`(内部自动 `applyPosting` 写余额)。 |
| 2 | 方案 C 事件解耦 | so/pu 发票 ISSUED 时发 Spring 事件;fin `GlPostingEventDispatcher`(`@TransactionalEventListener(AFTER_COMMIT)` + `@Async("financialExecutor")`)监听 → 发 GlPostingMessage 到 GL_POSTING_QUEUE → GlPostingConsumer 路由到 Poster。so/pu 不依赖 fin。 |
| 3 | 统驭科目固定 | AR 一律 1122(account_id=6005003),AP 一律 2202(6005020)。不改 BdClient/BdVendor。 |
| 4 | 采购 ISSUED 即过账 | 采购发票 ISSUED(收票)即过账(借存货1405+进项税2221.02/贷应付2202),与销售对称。认证子状态机独立管税务抵扣。 |
| 5 | 核销复用 verifyInvoice | FinSettlementService.settle() 调 so/pu 的 verifyInvoice(已有原子累加+乐观锁+状态翻转原语),不重写。 |
| 6 | 部分核销简化 | 核销金额在 fin_settlement + 发票 verified_amount + 收款 settled_amount 管理;gl_voucher_line.cleared 仅在全额核销时置 1,cleared_by=settlement_id。不改 F0 表。 |
| 7 | pay 回调完整链路 | pay 模块加 common-mq 依赖;PayOrder 加 biz_type/biz_id/client_id;AliPayServiceImpl.notify 验签后发 PayReceivedMessage;ERP PayReceivedConsumer 自动建 AR 收款单 + 过账 + FIFO 核销。 |

## 三、实施清单

### 后端(`com.nexus.erp.fin.arap`)
- **领域层**:FinReceipt / FinSettlement(extends TenantEntity)+ BO/VO/Mapper
- **表**(`script/sql/update/2026-07-30-f1-ar-ap-reconciliation.sql`,ry-erp 库):fin_receipt(AR/AP 合一)/ fin_settlement
- **服务层**:FinReceiptService(confirm 发 GlPostingMessage + cancel)、FinSettlementService(settle 复用 verifyInvoice + 全额清账 + queryOpenItems)
- **3 Poster**(实现 AccountingPoster,调 createAndPost):
  - SoInvoicePoster:借应收1122(统驭AR,openItem=1,partner=CLIENT)/ 贷收入6001 + 销项税2221.01
  - PuInvoicePoster:借存货1405 + 进项税2221.02 / 贷应付2202(统驭AP,openItem=1,partner=VENDOR)
  - ReceiptPoster:AR 收款 借银行1002/贷应收1122;AP 付款 借应付2202/贷银行1002
- **事件**:SoInvoiceIssuedEvent(so 包)/ PuInvoiceIssuedEvent(pu 包);so/pu 在 ISSUED 出口(afterFlowHandle pass / redFlush / receiveInvoice / redFlushForReturn)发事件;fin GlPostingEventDispatcher 监听发 MQ
- **pay 回调**:PayReceivedMessage(ruoyi-common-mq 共享)+ PayReceivedConsumer(建 AR 收款单 + 发 GlPostingMessage + FIFO 核销);AliPayServiceImpl.notify 发 MQ
- **常量**:ArapConstants(方向/状态/来源/往来类型/科目编码)+ RoutingKeyConstants 加 GL_POSTING_*/PAY_RECEIVED_CALLBACK

### 前端(`plus-ui/src/.../erp/fin/`)
- API:receipt/{index,types}、settlement/{index,types}、arap-report.ts
- 视图:receipt/index.vue(收付款单 CRUD+确认+作废)、settlement/index.vue(核销:选未清发票+收款+金额)、arap/report/index.vue(未清项明细)

### SQL
- `2026-07-30-f1-ar-ap-reconciliation.sql`:fin_receipt/fin_settlement 建表(ry-erp)
- `2026-07-30-f1-ar-ap-dict-menu.sql`:字典 6006xx(fin_receipt_source/status,复用 6004xx fin_receipt_type/direction)+ 菜单 6120-6129(财务管理6100 下应收应付目录)
- `2026-07-30-f1-pay-order-biz.sql`:pay_order 加 biz_type/biz_id/client_id(ry-vue)

## 四、E2E 验证(2026-07-30 全通过)

复用 F0 加密登录脚本(RSA+AES+Redis 验证码)+ RabbitMQ HTTP API(vhost=/erp,nexus/nexus)发 MQ + 业务 HTTP API + SQL 造数/验证。

| 验证点 | 结果 |
|---|---|
| SoInvoicePoster | ✓ SO_INVOICE 凭证生成,借=904 贷=904 平衡,应收行 openItem=1 partnerType=CLIENT |
| ReceiptPoster | ✓ RECEIPT 凭证生成(借银行/贷应收 openItem=1) |
| 核销 settle | ✓ 发票 VERIFIED 904 + 收款 SETTLED 904 |
| 清账 | ✓ gl_voucher_line.cleared=1, clearedBy=settlement_id |
| pay 回调 | ✓ PayReceivedMessage → 自动建 AR 收款单(PAY_CALLBACK) + 过账 + FIFO 核销(发票 VERIFIED 565) |

## 五、简化与后续

- **统驭科目固定**:AR=1122/AP=2202,未按客户/供应商细分(后续可加 BdClient/BdVendor.reconAccountId)
- **部分核销清账**:仅全额核销时 gl_voucher_line.cleared=1;部分核销记 fin_settlement 明细,凭证行保持 openItem=1(后续可加 cleared_amount 字段支持部分清账)
- **pay 渠道**:支付宝完整链路,微信/收钱吧对称实现预留 TODO
- **收款 cancel**:仅 DRAFT 可作废;已确认单据反核销+反过账待后续
- **事件链信任**:E2E 手动发 GlPostingMessage 验证 Poster;真实事件链(ISSUED→事件→Dispatcher→MQ)代码编译通过,逻辑同 ReceiptPoster(已验证 createAndPost 链路)

## 六、后续阶段

F1 完成,下一步可选:
- F2 固定资产(DepreciationPoster,统驭 1601 FA)
- F3 成本(CostPoster)
- 或 F1 优化(部分清账、统驭科目分客户、收款反过账)
