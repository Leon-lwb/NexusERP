# 开票与应收应付优化设计

- **日期**:2026-07-15
- **范围**:ERP(so / pu / bd / wm)+ pay 模块 + 前端(plus-ui)
- **状态**:**设计待评审,第十一节四项决议已按推荐采纳;P0 + P1 销售开票闭环 + P2 采购进项发票已落地并通过编译/造数验证(详见第十五、十六、十七节)**
- **关联**:`doc/erp/plans/archive/2026-07-07-so-fulfillment-linkage-plan.md`(该计划创建了 `SalesOrderFulfilledEvent` 并自注"为未来开票/通知预留",本文档接续此断点)

---

## 一、背景与现状诊断

### 1.1 三套割裂的"发票",且彼此不通

仓库里目前存在三个互不相干的"发票"概念,这是开票功能形同虚设的根本原因:

| 位置 | 实体 | 所在库 | 成熟度 | 定位 |
|------|------|--------|--------|------|
| `ruoyi-erp/so` | `SoInvoice` + `SoInvoiceLine` | ry-erp(`@DS("erp")`) | 较完整(20+字段、Flowable 审批、价税分离) | B 端销售发票,但**纯手工录入** |
| `ruoyi-erp/pu` | ❌ 不存在 | - | 完全缺失 | 进项发票整块空白 |
| `ruoyi-pay` | `PayInvoice` | ry-vue(master) | 空壳(仅 6 字段) | C 端开票登记簿,与 ERP 零集成 |

`so_invoice` 建表于 2026-06-14(`SoInvoice.java:20`),模型本身不差,但**没有任何业务联动**:不能从订单生成、状态机走不通、与收款/支付完全断开。

### 1.2 三个关键断点(阻塞性)

| # | 断点 | 证据 | 后果 |
|---|------|------|------|
| 1 | **业务事件无人监听** | `SoSalesOrderHeaderServiceImpl.java:300` 发布 `SalesOrderFulfilledEvent`(javadoc 自注"为未来开票/通知预留"),全局无任何 `@EventListener` | 订单履约后无法自动生成发票草稿,用户只能手工逐行录入 |
| 2 | **发票状态机走不通** | `SoInvoice` 有 `verified_amount` 字段与 `VERIFIED`/`CANCELLED` 状态常量(`ISoInvoiceService`),但 Service 无 `verifyInvoice()`/`cancelInvoice()` 方法 | 发票状态永远卡在 `ISSUED`,无法核销/作废 |
| 3 | **无 AR/AP、无核销、无对账** | `reconcil` 关键字在 pay 模块命中 0;仅有的两个"对账"是 MQ 消息补偿(`ReconciliationTask`)和库存台账(`WmStockReconciliation`),均非财务对账;`AliPayServiceImpl.callback()` 方法体为空 | 收款与发票无法核销,`verified_amount` 无人更新;支付完成不触发任何 ERP 动作 |

### 1.3 数据模型缺口清单(带证据)

| 实体/表 | 缺口 | 证据 |
|---------|------|------|
| `BdClient` | 仅有 `creditCode`(统一社会信用代码),缺纳税人识别号/开票抬头/开户行/银行账号/开票地址电话 | `BdClient.java:116`;对比 `BdVendor.java:41` 有 `taxId` |
| `SoSalesOrderLine` | 有 ordered/delivered/fulfilled/production/purchase/reserved qty,但**无 `invoicedQty`** | `SoSalesOrderLine.java:42-70` |
| `PuPurchaseOrderLine` | 有 `taxRate`/`lineAmount`(含税)/`unitPrice`,缺不含税单价、行税额、不含税行金额 | pu agent 报告 |
| `PuPurchaseOrder` | 头有 `totalAmount`/`taxAmount`,缺不含税总金额 | pu agent 报告 |
| `PuPurchaseReturn`/`Line` | 退货无任何税额字段 | pu agent 报告 |
| `PuContract`/`Line` | 合同无税额字段 | pu agent 报告 |
| `pu_invoice` | **整表不存在** | 全仓库无此表 |
| `so_invoice` | 缺发票代码、红冲原发票、购买方开票四要素、电票下载、乐观锁 | `ry-erp.sql:3115-3149` |
| `pay_order.total_amount` | 用 `varchar(255)` 存"分",无法做数学运算 | `ry-vue.sql:8348` |
| `pay_invoice` | 仅 6 业务字段(订单号/类型/抬头/税号/手机/邮箱),`invoice_number` 字段名存的是纳税人识别号而非发票号码 | `ry-vue.sql:8320` |

> 附带发现:`SoInvoice.selectFlowId` 在实体中存在(`SoInvoice.java:109`)但 `so_invoice` 建表 DDL(`ry-erp.sql:3115-3149`)无 `select_flow_id` 列,Flowable 流程 ID 实际未持久化,属既有缺陷,P1 阶段一并补列。

---

## 二、目标架构

### 2.1 主线原则

**发票属于 ERP 财务域,`ruoyi-pay` 退化为纯支付网关。** `pay_invoice` 空壳废弃或降级为"支付订单开票请求"(生成 `so_invoice`),真正的发票生命周期都在 `ruoyi-erp` 内闭环。跨库(ry-vue ↔ ry-erp)集成走 RabbitMQ,不做跨库 JOIN--项目已有 `QueueConstants` 集中声明通道的成熟模式。

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────────┐
│  ruoyi-erp 财务域 (ry-erp 库)                                │
│                                                              │
│  ① 统一发票模型                                              │
│     so_invoice(销项,增强)  ←镜像->  pu_invoice(进项,新建)      │
│     公共: invoice_code, 红冲(original_invoice_id/red_flag),  │
│           电票(issue_channel/e_invoice_no/pdf_oss_id),       │
│           购买/销售方开票四要素, version 乐观锁               │
│                                                              │
│  ② 应收应付 AR/AP (新建, com.nexus.erp.fin)                  │
│     fin_receipt 收款单 / fin_payment 付款单                  │
│     fin_settlement 核销记录 -> 回写 *.verified_amount         │
│                                                              │
│  ③ 业务联动 (补齐事件链)                                      │
│     订单履约->开票草稿  发货->可开票量  退货->红字发票           │
│     采购收货->三单匹配(PO+GR+Invoice)  退货->进项转出          │
│                                                              │
│  ④ 电子发票网关 (新建, SPI 适配层, com.nexus.erp.fin)         │
│     数电票: 开具/查询/红冲/下载PDF-OFD (百望/航信/用友 SPI)    │
│                                                              │
│  ⑤ 财务对账 (新建, com.nexus.erp.fin)                        │
│     银行流水 vs 系统收款单, 对账批次, 差异处理                 │
└─────────────────────────────────────────────────────────────┘
        ▲ MQ (RabbitMQ, 跨库解耦)
        │  PAY_RECEIVED_QUEUE / ERP_FINANCIAL_*_QUEUE
        │
┌───────┴──────────────────────────────────────────────────────┐
│  ruoyi-pay (ry-vue 库) - 纯支付网关                           │
│  支付成功回调 -> 发 MQ -> ERP 生成收款单 -> 自动核销发票        │
│  pay_order.order_number 携带 soOrderId 业务含义               │
│  pay_invoice 废弃(见 11.2 决议)                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 五根支柱

| 支柱 | 内容 | 阶段 |
|------|------|------|
| ① 统一发票模型 | so_invoice 增强 + pu_invoice 新建,统一状态机/红冲/电票字段 | P1/P2 |
| ② AR/AP + 核销 | 收款单/付款单/核销记录,回写 `verified_amount`,账龄报表 | P3 |
| ③ 业务联动 | 补齐订单->开票、发货->可开票量、退货->红冲/进项转出、采购三单匹配 | P1/P2 |
| ④ 电子发票网关 | `EInvoiceGateway` SPI + 数电票开具/查询/红冲/下载 | P4 |
| ⑤ 财务对账 | 银行流水 vs 收款单,对账批次/差异/调账 | P5 |

---

## 三、统一发票领域模型

### 3.1 so_invoice 增强(ALTER)

`so_invoice` 现有 DDL 见 `ry-erp.sql:3115-3149`。补齐发票代码、红冲、购买方开票四要素、电票下载、乐观锁,并补 `select_flow_id`:

```sql
-- script/sql/update/2026-07-15-so-invoice-enhance.sql
ALTER TABLE `so_invoice`
  ADD COLUMN `invoice_code` varchar(50) NULL DEFAULT NULL COMMENT '发票代码(数电票时代可与号码合并)' AFTER `invoice_no`,
  ADD COLUMN `buyer_tax_id` varchar(30) NULL DEFAULT NULL COMMENT '购买方纳税人识别号' AFTER `client_name`,
  ADD COLUMN `buyer_title` varchar(200) NULL DEFAULT NULL COMMENT '购买方发票抬头' AFTER `buyer_tax_id`,
  ADD COLUMN `buyer_bank_name` varchar(100) NULL DEFAULT NULL COMMENT '购买方开户行' AFTER `buyer_title`,
  ADD COLUMN `buyer_bank_account` varchar(50) NULL DEFAULT NULL COMMENT '购买方银行账号' AFTER `buyer_bank_name`,
  ADD COLUMN `buyer_address_phone` varchar(200) NULL DEFAULT NULL COMMENT '购买方地址、电话' AFTER `buyer_bank_account`,
  ADD COLUMN `original_invoice_id` bigint(20) NULL DEFAULT NULL COMMENT '红冲原发票ID(NULL=蓝字正数)' AFTER `contract_id`,
  ADD COLUMN `red_flag` char(1) NULL DEFAULT '0' COMMENT '是否红字发票 0-否 1-是' AFTER `original_invoice_id`,
  ADD COLUMN `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID' AFTER `status`,
  ADD COLUMN `issue_channel` varchar(20) NULL DEFAULT 'MANUAL' COMMENT '开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台' AFTER `select_flow_id`,
  ADD COLUMN `e_invoice_no` varchar(50) NULL DEFAULT NULL COMMENT '数电票号码(平台返回)' AFTER `issue_channel`,
  ADD COLUMN `pdf_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票PDF的OSS对象ID(私有桶存ossId)' AFTER `e_invoice_no`,
  ADD COLUMN `ofd_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票OFD的OSS对象ID' AFTER `pdf_oss_id`,
  ADD COLUMN `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `ofd_oss_id`,
  ADD INDEX `idx_original_invoice_id`(`original_invoice_id`) USING BTREE,
  ADD INDEX `idx_issue_channel`(`issue_channel`) USING BTREE;
```

> **OSS 存储约定**:电票 PDF/OFD 文件字段存 `ossId` 而非 URL(minio 私有桶,URL 为 120s 预签名且超长会触发 Data truncation)。VO 用 `@Translation(OSS_ID_TO_URL)` 解析,与现有客户 LOGO 上传一致。

> **乐观锁**:`SoInvoice` 加 `@Version`,新建发票 `version=0`(参考库存乐观锁初始化坑:不初始化则 WHERE version=NULL 永不匹配)。

### 3.2 pu_invoice 新建(CREATE,镜像 so_invoice)

进项发票镜像销售发票,字段对偶:客户↔供应商、so_id↔po_id、购买方↔销售方。额外增加进项认证字段。

```sql
-- script/sql/update/2026-07-15-pu-invoice-create.sql
DROP TABLE IF EXISTS `pu_invoice`;
CREATE TABLE `pu_invoice`  (
  `invoice_id` bigint(20) NOT NULL COMMENT '发票ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `invoice_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发票号',
  `invoice_code` varchar(50) NULL DEFAULT NULL COMMENT '发票代码',
  `invoice_type` varchar(20) NULL DEFAULT 'VAT_SPECIAL' COMMENT '发票类型 VAT_SPECIAL-增值税专用 VAT_NORMAL-增值税普通 OTHER-其他',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_name` varchar(200) NULL DEFAULT NULL COMMENT '供应商名称',
  `po_id` bigint(20) NULL DEFAULT NULL COMMENT '采购订单ID',
  `contract_id` bigint(20) NULL DEFAULT NULL COMMENT '采购合同ID',
  `original_invoice_id` bigint(20) NULL DEFAULT NULL COMMENT '红冲原发票ID(NULL=蓝字正数)',
  `red_flag` char(1) NULL DEFAULT '0' COMMENT '是否红字发票 0-否 1-是',
  `invoice_date` date NULL DEFAULT NULL COMMENT '开票日期',
  `currency` varchar(10) NULL DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10, 4) NULL DEFAULT 1.0000 COMMENT '汇率',
  `amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '金额(不含税)',
  `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '税额',
  `total_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '价税合计',
  `verified_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '已核销(已付款)金额',
  `status` varchar(20) NULL DEFAULT 'DRAFT' COMMENT '发票状态 DRAFT-草稿 ISSUED-已收 VERIFIED-已核销 CANCELLED-已作废',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
  -- 进项认证专用
  `cert_status` varchar(20) NULL DEFAULT 'UNCERTIFIED' COMMENT '认证状态 UNCERTIFIED-未认证 CERTIFIED-已认证 DEDUCTED-已抵扣 TRANSFERRED-已转出',
  `cert_date` date NULL DEFAULT NULL COMMENT '认证日期',
  `deductible_tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '可抵扣税额',
  `issue_channel` varchar(20) NULL DEFAULT 'MANUAL' COMMENT '来源渠道 MANUAL-手工录入 ELECTRONIC-电子发票平台同步',
  `e_invoice_no` varchar(50) NULL DEFAULT NULL COMMENT '数电票号码',
  `pdf_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票PDF的OSS对象ID',
  `ofd_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票OFD的OSS对象ID',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) NULL DEFAULT NULL,
  `attr2` varchar(255) NULL DEFAULT NULL,
  `attr3` int(11) NULL DEFAULT NULL,
  `attr4` int(11) NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`invoice_id`) USING BTREE,
  UNIQUE INDEX `uk_invoice_no`(`invoice_no`, `tenant_id`) USING BTREE,
  INDEX `idx_vendor_id`(`vendor_id`) USING BTREE,
  INDEX `idx_po_id`(`po_id`) USING BTREE,
  INDEX `idx_original_invoice_id`(`original_invoice_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_cert_status`(`cert_status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购进项发票表' ROW_FORMAT = DYNAMIC;

-- pu_invoice_line 镜像 so_invoice_line,so_line_id 换 po_line_id
DROP TABLE IF EXISTS `pu_invoice_line`;
CREATE TABLE `pu_invoice_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '发票行ID',
  `tenant_id` varchar(20) NULL DEFAULT '000000' COMMENT '租户ID',
  `invoice_id` bigint(20) NOT NULL COMMENT '发票ID',
  `line_no` varchar(10) NULL DEFAULT NULL COMMENT '行号',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(50) NULL DEFAULT NULL,
  `material_name` varchar(200) NULL DEFAULT NULL,
  `material_spec` varchar(200) NULL DEFAULT NULL,
  `quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '数量',
  `unit` varchar(20) NULL DEFAULT NULL COMMENT '单位',
  `unit_price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '单价(不含税)',
  `line_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '行金额(不含税)',
  `tax_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '税率',
  `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '税额',
  `total_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '价税合计',
  `po_line_id` bigint(20) NULL DEFAULT NULL COMMENT '采购订单行ID',
  `gr_line_id` bigint(20) NULL DEFAULT NULL COMMENT '收货单行ID(三单匹配用)',
  `contract_line_id` bigint(20) NULL DEFAULT NULL COMMENT '采购合同行ID',
  `sort_order` int(11) NULL DEFAULT 0,
  `remark` varchar(500) NULL DEFAULT NULL,
  `attr1` varchar(64) NULL DEFAULT NULL,
  `attr2` varchar(255) NULL DEFAULT NULL,
  `attr3` int(11) NULL DEFAULT NULL,
  `attr4` int(11) NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_invoice_id`(`invoice_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_po_line_id`(`po_line_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购进项发票明细行表' ROW_FORMAT = DYNAMIC;
```

### 3.3 统一状态机

销项/进项共用同一状态语义,仅终态动作不同(销项=开具/红冲,进项=收取/认证/抵扣):

```
DRAFT ─[提交审批]─▶ APPROVING ─[审批通过]─▶ ISSUED ─[核销金额=价税合计]─▶ VERIFIED
                       │                       │
                       └─[驳回]─▶ REJECTED      └─[作废/红冲]─▶ CANCELLED
                       └─[撤回]─▶ DRAFT
```

- **销项** `ISSUED`:发票已开具(开给客户);`VERIFIED`:收款已核销完毕
- **进项** `ISSUED`:发票已收取(供应商开来);`VERIFIED`:已付款核销完毕;另含 `cert_status` 认证子状态机:`UNCERTIFIED -> CERTIFIED -> DEDUCTED`(退货时 `-> TRANSFERRED` 进项转出)
- **红冲**:`original_invoice_id` 指向原蓝字发票,`red_flag=1`,金额为负;核销时红字发票冲减原发票已核销金额

---

## 四、基础数据模型修补(P0)

低风险纯加字段,为后续所有阶段打底。建议**优先实施**。

> **实施修正(2026-07-15 审查)**:
> 1. **SQL 落位**:统一放 `doc/erp/sql/`(实际约定,`script/sql/update/` 当前为空),文件名 `erp-invoicing-p0-*.sql` / `dict-invoicing.sql`。第十三节文件清单内的 `script/sql/update/` 路径以此为准。
> 2. **字典 ID**:段位硬编码小 ID + INSERT IGNORE(非 `@var:=@var+1`),发票/收付款字典用 6004xx 段,避让 6001xx/6002xx/6003xx。详见 4.6。
> 3. **BdVendor 开票字段**:放 `bank_account` 后(与银行账号聚簇),非 `tax_id` 后。
> 4. **pay_order 金额 varchar->decimal**:波及 AliPay/Wx/SQB 三个 SDK 金额构造 + 27 条测试数据迁移 + 跨库(ry-vue),风险高于 ERP 侧纯加字段,作为 P0 内**独立子任务**单独处理;本轮先落地 ERP 侧数据模型。
> 5. **实体基类不一致**:`PuPurchaseOrder`/`PuPurchaseOrderLine` 继承 `BaseEntity`,其余 pu 实体与 bd 实体继承 `TenantEntity`;不影响 P0 加字段,仅记录。

### 4.1 BdClient 补开票四要素

```sql
-- script/sql/update/2026-07-15-bd-client-invoice-info.sql
ALTER TABLE `bd_client`
  ADD COLUMN `tax_id` varchar(30) NULL DEFAULT NULL COMMENT '纳税人识别号' AFTER `credit_code`,
  ADD COLUMN `invoice_title` varchar(200) NULL DEFAULT NULL COMMENT '发票抬头(默认取客户名称)' AFTER `tax_id`,
  ADD COLUMN `invoice_bank_name` varchar(100) NULL DEFAULT NULL COMMENT '开户行' AFTER `invoice_title`,
  ADD COLUMN `invoice_bank_account` varchar(50) NULL DEFAULT NULL COMMENT '银行账号' AFTER `invoice_bank_name`,
  ADD COLUMN `invoice_address_phone` varchar(200) NULL DEFAULT NULL COMMENT '开票地址、电话' AFTER `invoice_bank_account`,
  ADD COLUMN `default_invoice_type` varchar(20) NULL DEFAULT 'VAT_NORMAL' COMMENT '默认发票类型' AFTER `invoice_address_phone`;
```

### 4.2 BdVendor 补开票信息(对称)

`BdVendor` 已有 `taxId`/`bankAccount`,补抬头/地址电话/开户行名/默认发票类型。

```sql
ALTER TABLE `bd_vendor`
  ADD COLUMN `invoice_title` varchar(200) NULL DEFAULT NULL COMMENT '发票抬头' AFTER `bank_account`,
  ADD COLUMN `invoice_address_phone` varchar(200) NULL DEFAULT NULL COMMENT '开票地址、电话' AFTER `invoice_title`,
  ADD COLUMN `invoice_bank_name` varchar(100) NULL DEFAULT NULL COMMENT '开户行名称' AFTER `invoice_address_phone`,
  ADD COLUMN `default_invoice_type` varchar(20) NULL DEFAULT 'VAT_SPECIAL' COMMENT '默认发票类型' AFTER `invoice_bank_name`;
```

### 4.3 so/pu 订单行补 invoicedQty(防重复开票)

```sql
ALTER TABLE `so_sales_order_line` ADD COLUMN `invoiced_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已开票数量' AFTER `delivered_qty`;
ALTER TABLE `pu_purchase_order_line` ADD COLUMN `invoiced_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已开票数量' AFTER `received_quantity`;
```

> 开票时原子累加 `invoiced_qty += qty`,校验 `invoiced_qty + 本次开票 <= delivered_qty`(销项按已发货)或 `<= received_quantity`(进项按已收货),防超开。

### 4.4 pu 价税分离字段

```sql
ALTER TABLE `pu_purchase_order`
  ADD COLUMN `amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税总金额' AFTER `total_amount`;
ALTER TABLE `pu_purchase_order_line`
  ADD COLUMN `unit_price_excluding_tax` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不含税单价' AFTER `unit_price`,
  ADD COLUMN `line_amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税行金额' AFTER `line_amount`,
  ADD COLUMN `line_tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '行税额' AFTER `line_amount_excluding_tax`;
-- pu_purchase_return / pu_purchase_return_line / pu_contract / pu_contract_line 同理补 tax_rate/tax_amount,略
```

### 4.5 pay_order 金额类型修正(独立子任务,✅ 已实施 2026-07-16)

**现状**:`pay_order`(ry-vue 库)`total_amount`/`actual_amount` 为 `varchar(255)`,存**金额的"分"**用字符串表示(`PayOrder.java:41,45` 为 `String`)。P3 核销需与 `fin_receipt.amount`/`so_invoice.total_amount`(decimal 元)做数值匹配,类型+单位均不一致,无法 SUM/比较。

**难点:三个支付平台金额单位不同**,当前存"分",各 SDK 在边界换算:

| 平台 | API 要求 | 现状代码(存分) | 迁移后(存元)需改成 |
|------|---------|---------------|-------------------|
| 支付宝 | 元(带小数) | `AliPayServiceImpl:47,143` `Double.parseDouble(分)/100` | 直接用元,去掉 `/100` |
| 微信 | 分(整数) | `WxPayServiceImpl:57,149,150` `Integer/Long.parseInt(分)` | `元×100` 转分 |
| 收钱吧 | 分(整数) | `SQBServiceImpl:137,191,199,217` `Long.parseLong(分)` | `元×100` 转分 |

微信回调写回 `actualAmount`(`WxPayServiceImpl:102,132`)返回分,需 `/100` 转元再存。**单位换算错一处即真金白银 bug。**

**改动清单**:
- DDL:`ALTER pay_order MODIFY total_amount/actual_amount decimal(18,2)`(ry-vue 库,文件 `doc/erp/sql/erp-invoicing-p0-pay-order-decimal.sql`)
- 数据迁移:存量 27 条测试数据 `分/100 -> 元`(`UPDATE pay_order SET total_amount=ROUND(total_amount/100,2), actual_amount=ROUND(actual_amount/100,2)`);无生产数据可直接重建
- Java:`PayOrder`/`PayOrderVo`/`PayOrderBo` 三处 `String -> BigDecimal`;`PayOrderController:76-77`;三个 SDK Impl 单位换算(上表);微信回调写回处
- 前端:`views/pay/paytest/index.vue`(下单输入)、`views/pay/order/index.vue`、`api/pay/order/types.ts` 三文件(展示/输入从分串改元)
- 验证:`mvn -pl ruoyi-modules/ruoyi-pay -am compile` + 支付宝沙箱下单->回调跑通

**风险**:碰支付真金白银代码;数据迁移方向错金额放大/缩小 100 倍;跨库(ry-vue)。**可缓做**:真正消费在 P3,离当前隔 P1/P2。

#### 4.5.1 实施记录(2026-07-16)

已落地并验证,实际与原方案的差异:

| 项 | 原方案 | 实际 |
|----|--------|------|
| SQL 文件 | `doc/erp/sql/erp-invoicing-p0-pay-order-decimal.sql` | ✅ 同名,本地 ry-vue 执行成功 |
| 存量数据 | 27 条 | 实际 **22 条**(部分已删),全为数字分值,MODIFY 安全;迁移后校验:1分→0.01元、1100分→11.00元、500000000分→5000000.00元、123213分→1232.13元 ✓ |
| 列定义 | `decimal(18,2)` | `decimal(18,2) NULL DEFAULT 0.00`(与 so_invoice 金额列约定一致,P3 SUM 友好) |
| Java 实体 | PayOrder/Vo/Bo String→BigDecimal | ✅ 三处已改,字段注释 分→元 |
| `PayOrderController:76-77` | 需改 | **无需逻辑改动**:setTotalAmount/setActualAmount 两端同为 BigDecimal,类型直通 |
| 三 SDK 单位换算 | 各自手算 | ✅ 抽 `com.nexus.pay.util.MoneyUtil`(`yuanToFen`/`fenToYuan`)集中换算,三 SDK 复用,避免重复;支付宝去 `/100` 改 `toPlainString()`,微信/收钱吧 `×100`,微信回调/查询 `分→元` |
| 微信 `amount.setTotal(Integer)` | - | 用 `Math.toIntExact(yuanToFen(...))` 失败优于负数扣款(超 int 范围抛异常,与原 `Integer.parseInt` 一致) |
| 前端 | 3 文件(含 types.ts) | 实际改 2 文件:`paytest/index.vue`、`order/index.vue` 标签 分→元;**types.ts 未改**--BigDecimal 经 Jackson 默认序列化为数字(scale=2 无科学计数法,与 so_invoice 同模式),前端 `string` 类型经 JS 强制转换兼容,改类型反增 vue-tsc 噪声 |
| 编译 | - | `mvn -pl ruoyi-pay -am compile -o` EXIT 0 |
| eslint | - | 2 文件 eslint 错误数与 HEAD 基线完全一致(paytest 74 / order 78,既有 prettier baseline 噪声),**零新增** |
| 幂等性 | - | ⚠️ 迁移非幂等(重复执行会再 /100),SQL 文件已标注一次性;无生产数据,本地可重建 |

> **运行时验证待办**:`mvn compile` 仅保证编译;支付宝沙箱下单→回调→查询的端到端金额正确性,需启动应用实跑一次确认(当前环境未跑)。这是支付真金白银代码,合入前建议在沙箱验证。

### 4.6 so_invoice 前端字典化

`views/erp/so/invoice/index.vue:17/27/68` 硬编码 `el-option` 改为 `useDict` + `dict-tag`(代码自注"技术债")。新增字典:`so_invoice_type`、`so_invoice_status`、`pu_invoice_type`、`pu_invoice_status`、`pu_invoice_cert_status`、`fin_receipt_type` 等。

> **字典 ID 约定**(2026-07-15 审查更正):实际项目约定为**段位硬编码小 ID + INSERT IGNORE + 冲突避让注释**(见 `doc/erp/sql/dict-pu-contract.sql` 6003xx、`erp-pu-contract-menu.sql` 25xx),非 `@var:=@var+1`。本轮发票/收付款字典用 6004xx 段,先 grep 避让既有 6001xx/6002xx/6003xx。

---

## 五、销售开票闭环(P1)

### 5.1 改动 1:SalesOrderFulfilledEvent 监听器 -> 自动生成发票草稿

**新文件** `so/event/SalesOrderFulfilledEventListener.java`(`SalesOrderFulfilledEvent` 已存在,见 `2026-07-07-so-fulfillment-linkage-plan.md` 改动 6)

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderFulfilledEventListener {
    private final ISoInvoiceService soInvoiceService;
    private final ISoSalesOrderHeaderService soSalesOrderHeaderService;
    private final IBdClientService bdClientService;

    @EventListener
    @Async("financialExecutor") // 见 11.4 决议
    public void onFulfilled(SalesOrderFulfilledEvent event) {
        // 租户上下文恢复(异步线程 TenantHelper 不传播)
        TenantHelper.dynamic(event::getTenantId, () -> {
            soInvoiceService.generateDraftFromOrder(event.getSoId());
        });
    }
}
```

> **异步租户上下文坑**:`@Async` 线程不传播 Sa-Token ThreadLocal,异步写多租户表必须 `TenantHelper.dynamic(tenantId, ...)` 包裹,否则 `tenant_id` 落 0。`SalesOrderFulfilledEvent` 需补 `tenantId` 字段(发布处 `SoSalesOrderHeaderServiceImpl.java:300` 传入 `TenantHelper.getTenantId()`)。

**`ISoInvoiceService.generateDraftFromOrder(Long soId)` 新增方法**:
- 查 SO 头 + 行 + 客户档案(取开票四要素填 `buyer_*`)
- 按"已发货未开票"生成发票草稿:`invoice_qty = delivered_qty - invoiced_qty`,过滤 `>0` 的行
- 头状态 `DRAFT`,默认 `invoice_type` 取客户 `default_invoice_type`
- 原子累加 `so_sales_order_line.invoiced_qty`
- **幂等**:同一 SO 重复履约不重复生成(查已有非作废草稿则跳过/合并)

### 5.2 改动 2:发票状态机补全

**`ISoInvoiceService` 新增**:
```java
void verifyInvoice(Long invoiceId, BigDecimal verifyAmount, Long receiptId); // 核销:verified_amount += verifyAmount,达价税合计则 VERIFIED
void cancelInvoice(Long invoiceId, String reason);                            // 作废/红冲
void redFlush(Long originalInvoiceId, String reason);                         // 红字发票(销项负数)
```

- `verifyInvoice`:原子 `setSql("verified_amount = verified_amount + ?")`,达 `total_amount` 转 `VERIFIED`;核销记录写 `fin_settlement`(P3)
- `redFlush`:复制原发票行取负生成红字发票,`original_invoice_id` 指向原发票,`red_flag=1`,冲减 `so_sales_order_line.invoiced_qty`,冲减原发票 `verified_amount`

### 5.3 改动 3:SoInvoiceServiceImpl.updateByBo 差量更新 + 乐观锁

现状(`SoInvoiceServiceImpl.java:114-130`)先删全部行再重新插入,无锁,并发丢行。改为:
- 行差量对比(新增/更新/删除)而非全删重插
- 头更新带 `@Version` 乐观锁(`version` 字段,P0 已加)

### 5.4 改动 4:前端

- `views/erp/so/invoice/index.vue` 操作列补"核销""红冲"按钮,调新增端点
- 新增"从订单生成发票"入口(或在订单列表 fulfill 后提示)
- 字典化(4.6)

### 5.5 配套:财务异步线程池

参考 `AiThreadPoolConfig`,新建 `FinancialThreadPoolConfig` 的 `financialExecutor` Bean,专用于开票/核销/电票异步任务,与 `aiExecutor` 隔离(见 11.4 决议)。

---

## 六、采购进项发票(P2)

### 6.1 新建 pu_invoice 模块(镜像 so)

`com.nexus.erp.pu.invoice` 子包:`PuInvoice`/`PuInvoiceLine`/`PuInvoiceController`/`IPuInvoiceService`/`PuInvoiceServiceImpl`/BO/VO/Mapper。代码结构与 `SoInvoiceServiceImpl` 对称,状态机含 `cert_status` 认证子状态。

> **代码归属**:放 `pu` 模块(对称 `so_invoice` 在 so),见第十一节决议 11.1(方案 C)。财务能力(收付款/核销/对账/电票网关)归 `com.nexus.erp.fin`。

### 6.2 三单匹配(PO + GR + Invoice)

进项发票核销前校验三单一致:
- **采购订单(PO)**:`pu_purchase_order_line`
- **收货单(GR)**:收货明细(`PuPurchaseReceivingServiceImpl` 生成,已有 `received_quantity`)
- **发票(Invoice)**:`pu_invoice_line`

校验维度:物料、数量(发票 ≤ 收货)、单价、税率、税额、价税合计。差异超容差告警或拦截。`PuInvoiceServiceImpl.verify()` 前置三单匹配校验。

> 收货已有 MQ 回写链路(`StockInConsumer`/`PurchaseReceiveWritebackConsumer`),租户上下文已正确处理。三单匹配在 `pu_invoice_line.gr_line_id` 关联收货单行。

### 6.3 进项认证管理

`pu_invoice.cert_status`:`UNCERTIFIED -> CERTIFIED -> DEDUCTED`。新增 `certifyInvoice()`/`deductInvoice()` 端点,记录 `cert_date`/`deductible_tax_amount`。可对接税务局增值税发票综合查验平台(查验真伪)。

### 6.4 采购退货 -> 进项转出/红冲

`PuPurchaseReturnServiceImpl.execute()`(`:188-203`)现状仅扣库存。增强:若关联进项发票已认证(`DEDUCTED`),退货时:
- 生成红字进项发票(`red_flag=1`,`original_invoice_id` 指向原发票)
- `cert_status -> TRANSFERRED`(进项税额转出)
- 红字发票冲减供应商应付

---

## 七、应收应付与核销(P3)

### 7.1 新建 fin 财务子模块

`com.nexus.erp.fin`(财务能力子域,见第十一节决议 11.1 方案 C;发票仍归 so/pu,fin 通过 Service 接口调用,不反向依赖)。三张核心表:

#### fin_receipt(收款单)
```sql
CREATE TABLE `fin_receipt` (
  `receipt_id` bigint(20) NOT NULL COMMENT '收款单ID',
  `tenant_id` varchar(20) NULL DEFAULT '000000',
  `receipt_no` varchar(50) NOT NULL COMMENT '收款单号',
  `receipt_type` varchar(20) NULL DEFAULT 'BANK' COMMENT '收款类型 CASH-现金 BANK-银行 ALIPAY-支付宝 WECHAT-微信 OTHER-其他',
  `direction` varchar(10) NULL DEFAULT 'AR' COMMENT '方向 AR-应收(收款) AP-应付(付款)',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID(AR)',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID(AP)',
  `payer_name` varchar(200) NULL DEFAULT NULL COMMENT '付款方名称',
  `amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '收款金额',
  `settled_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '已核销金额',
  `currency` varchar(10) NULL DEFAULT 'CNY',
  `receipt_date` date NULL DEFAULT NULL COMMENT '收款日期',
  `bank_account` varchar(50) NULL DEFAULT NULL COMMENT '收款银行账号',
  `third_party_no` varchar(100) NULL DEFAULT NULL COMMENT '第三方流水号(支付宝/微信交易号)',
  `pay_order_number` varchar(50) NULL DEFAULT NULL COMMENT '关联pay_order订单号(跨库)',
  `source` varchar(20) NULL DEFAULT 'MANUAL' COMMENT '来源 MANUAL-手工 PAY_CALLBACK-支付回调',
  `status` varchar(20) NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT-草稿 CONFIRMED-已确认 CANCELLED-已作废',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` varchar(500) NULL DEFAULT NULL,
  -- 审计字段略(同 so_invoice)
  PRIMARY KEY (`receipt_id`),
  UNIQUE INDEX `uk_receipt_no`(`receipt_no`, `tenant_id`),
  INDEX `idx_client_id`(`client_id`),
  INDEX `idx_vendor_id`(`vendor_id`),
  INDEX `idx_pay_order_number`(`pay_order_number`),
  INDEX `idx_third_party_no`(`third_party_no`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收付款单表' ROW_FORMAT = DYNAMIC;
```

> AR/AP 合一为 `fin_receipt`(`direction` 区分),收款单/付款单结构同构,降低表数量。付款单即 `direction=AP` 的记录。

#### fin_settlement(核销记录)
```sql
CREATE TABLE `fin_settlement` (
  `settlement_id` bigint(20) NOT NULL,
  `tenant_id` varchar(20) NULL DEFAULT '000000',
  `direction` varchar(10) NOT NULL COMMENT 'AR-应收核销 AP-应付核销',
  `invoice_id` bigint(20) NOT NULL COMMENT '发票ID(so_invoice/pu_invoice)',
  `invoice_no` varchar(50) NULL DEFAULT NULL COMMENT '发票号(冗余)',
  `invoice_type` varchar(20) NULL DEFAULT NULL COMMENT '发票类型(销项/进项, 决定关联so/pu)',
  `receipt_id` bigint(20) NOT NULL COMMENT '收付款单ID',
  `settle_amount` decimal(18, 2) NOT NULL COMMENT '本次核销金额',
  `settle_date` date NULL DEFAULT NULL,
  `operator` varchar(64) NULL DEFAULT NULL,
  `remark` varchar(500) NULL DEFAULT NULL,
  -- 审计字段略
  PRIMARY KEY (`settlement_id`),
  INDEX `idx_invoice_id`(`invoice_id`),
  INDEX `idx_receipt_id`(`receipt_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收付款核销记录表' ROW_FORMAT = DYNAMIC;
```

### 7.2 核销回写 verified_amount

核销流程(`FinSettlementService.settle()`):
1. 写 `fin_settlement` 一条核销记录
2. 原子累加发票 `verified_amount += settle_amount`(`setSql`,带乐观锁)
3. 原子累加收付款单 `settled_amount += settle_amount`
4. 发票 `verified_amount >= total_amount` -> 状态转 `VERIFIED`
5. 收付款单 `settled_amount >= amount` -> 状态转 `SETTLED`

> 对应补齐 P1 的 `verifyInvoice()`:核销即调 `FinSettlementService.settle()`,销项发票经此回写 `so_invoice.verified_amount`,解决断点 2/3。

### 7.3 AR/AP 账龄报表

基于 `so_invoice`(应收)与 `fin_receipt`/`fin_settlement` 计算:未核销金额 = `total_amount - verified_amount`,按账龄区间(0-30/30-60/60-90/90+ 天)统计。AP 同理(`pu_invoice` + `fin_receipt direction=AP`)。挂 `ruoyi-report`(积木报表)或自建 `FinAgingReportController`。

### 7.4 pay 支付回调 -> 收款单 -> 自动核销(跨库 MQ)

打通断点 3 的支付侧:

1. **pay 侧**:`AliPayServiceImpl.callback()`(`:39-41` 空实现)填充:校验签名 -> 更新 `pay_order` 状态"已支付" -> 发 MQ `PAY_RECEIVED_QUEUE`,消息体含 `orderNumber/thirdPartyNo/actualAmount/payTime/tenantId`
2. **pay 侧增强**:`PayOrderController.java:74` 订单号生成时携带业务含义(如 `SO` + soId),`pay_order` 新增 `biz_type`/`biz_id` 列关联销售订单
3. **ERP 侧**:新建 `PayReceivedConsumer`(`@RabbitListener(PAY_RECEIVED_QUEUE)`),`TenantHelper.dynamic` 恢复租户 -> 创建 `fin_receipt`(`source=PAY_CALLBACK`) -> 按 `biz_id`(soId)查该客户未核销 `so_invoice` -> 自动核销(调 `FinSettlementService.settle`)

> 跨库(ry-vue->ry-erp)经 MQ 解耦,消费者租户上下文处理参考 `StockInConsumer.java:76-88`。

---

## 八、电子发票平台对接(P4)

### 8.1 EInvoiceGateway SPI 抽象

```java
public interface EInvoiceGateway {
    /** 开具(蓝字) */
    EInvoiceIssueResult issue(EInvoiceIssueRequest req);
    /** 红冲 */
    EInvoiceIssueResult redFlush(Long originalInvoiceId, String reason);
    /** 查询发票状态 */
    EInvoiceQueryResult query(String eInvoiceNo);
    /** 下载 PDF/OFD, 上传 OSS, 返回 ossId */
    EInvoiceFileResult download(String eInvoiceNo);
}
```

实现:`ManualEInvoiceGateway`(默认,仅改状态不调平台)、`BaiwangEInvoiceGateway`(百望)、`AisinoEInvoiceGateway`(航信)、`YonyouEInvoiceGateway`(用友票易通)。通过 `nexus.einvoice.provider` 配置切换,`ChatModelFactory` 式动态构建+缓存。

### 8.2 数电票合规

2024 年起中国全面推广数字化电子发票(数电票),`so_invoice.issue_channel=ELECTRONIC` 时调网关:
- `issue()`:平台返回 `e_invoice_no`(数电票号码,20 位)+ PDF/OFD 文件,存 `e_invoice_no`/`pdf_oss_id`/`ofd_oss_id`
- `redFlush()`:数电票红冲无需原票退回,平台直接开红字
- `query()`:轮询开票结果(异步开具场景)

### 8.3 配置

```yaml
# application-secret.yml
nexus:
  einvoice:
    enabled: false
    provider: manual  # manual/baiwang/aisino/yonyou
    baiwang:
      app-id: ${secret.einvoice.baiwang.app-id}
      app-secret: ${secret.einvoice.baiwang.app-secret}
      tax-no: ${secret.einvoice.baiwang.tax-no}
      api-url: https://openapi.baiwang.com
```

> 数电票平台凭证敏感,放 `application-secret.yml`,不入基础 `application.yml`。

---

## 九、财务对账(P5)

### 9.1 对账范围

| 类型 | 对账双方 | 来源 |
|------|---------|------|
| 支付对账 | 银行流水 vs 系统收付款单(`fin_receipt`) | 银行对账单导入 |
| 应收对账 | 客户对账单 vs `so_invoice`+`fin_settlement` | 客户提供的对账单 |
| 应付对账 | 供应商对账单 vs `pu_invoice`+`fin_settlement` | 供应商对账单 |

### 9.2 fin_reconciliation 表(草图)

```sql
CREATE TABLE `fin_reconciliation` (
  `recon_id` bigint(20) NOT NULL,
  `recon_no` varchar(50) NOT NULL COMMENT '对账批次号',
  `recon_type` varchar(20) NOT NULL COMMENT 'PAYMENT/AR/AP',
  `period` varchar(7) NULL DEFAULT NULL COMMENT '账期 YYYY-MM',
  `bank_account` varchar(50) NULL DEFAULT NULL,
  `total_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT '对账单总金额',
  `matched_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT '已匹配金额',
  `diff_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT '差异金额',
  `status` varchar(20) NULL DEFAULT 'PENDING' COMMENT 'PENDING-进行中 MATCHED-已匹配 DIFF-有差异 CLOSED-已关闭',
  `version` int(11) NULL DEFAULT 0,
  -- 审计字段略
  PRIMARY KEY (`recon_id`),
  UNIQUE INDEX `uk_recon_no`(`recon_no`, `tenant_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '财务对账批次表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `fin_reconciliation_detail` (
  `detail_id` bigint(20) NOT NULL,
  `recon_id` bigint(20) NOT NULL,
  `bank_seq_no` varchar(100) NULL COMMENT '银行流水号',
  `bank_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT '流水金额',
  `bank_date` date NULL DEFAULT NULL,
  `receipt_id` bigint(20) NULL DEFAULT NULL COMMENT '匹配的收付款单ID',
  `match_status` varchar(20) NULL DEFAULT 'UNMATCHED' COMMENT 'UNMATCHED-未匹配 MATCHED-已匹配 DIFF-差异',
  `diff_reason` varchar(500) NULL DEFAULT NULL,
  -- 审计字段略
  PRIMARY KEY (`detail_id`),
  INDEX `idx_recon_id`(`recon_id`),
  INDEX `idx_receipt_id`(`receipt_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '财务对账明细表' ROW_FORMAT = DYNAMIC;
```

### 9.3 自动勾对策略

银行流水导入 -> 按 `金额 + 日期 ± 容差` 自动匹配 `fin_receipt`(优先 `third_party_no` 精确匹配)-> 匹配失败的标记 `DIFF` 人工处理 -> 全部匹配后批次 `CLOSED`。

---

## 十、MQ 通道设计

### 10.1 新增 QueueConstants

`ruoyi-common-mq/.../QueueConstants.java` 追加(沿用 `erp.*` 前缀):

```java
/** 支付收款通知队列 (pay -> ERP, 跨库) */
String PAY_RECEIVED_QUEUE = "erp.pay.received.queue";
/** 销项发票开具结果队列 (电子发票平台异步回调) */
String EINVOICE_ISSUE_RESULT_QUEUE = "erp.einvoice.issue.result.queue";
/** 进项发票认证结果队列 */
String EINVOICE_CERT_RESULT_QUEUE = "erp.einvoice.cert.result.queue";
```

### 10.2 消息体与消费方

| 队列 | 生产者 | 消费者 | 消息体 | 租户上下文 |
|------|--------|--------|--------|-----------|
| `PAY_RECEIVED_QUEUE` | `AliPayServiceImpl`/`WxPayServiceImpl`/`SQBServiceImpl` 回调 | `PayReceivedConsumer`(ERP) | orderNumber/thirdPartyNo/actualAmount/payTime/tenantId/bizType/bizId | `TenantHelper.dynamic`(参考 `StockInConsumer:76-88`) |
| `EINVOICE_ISSUE_RESULT_QUEUE` | 电子发票平台回调 | `EInvoiceResultConsumer`(ERP) | eInvoiceNo/status/pdfOssId | 同上 |
| `EINVOICE_CERT_RESULT_QUEUE` | 税务查验平台 | `EInvoiceCertConsumer`(ERP) | invoiceId/certStatus | 同上 |

> 所有跨库 MQ 消费者写多租户表必须 `TenantHelper.dynamic` 恢复租户,消息体显式携带 `tenantId`(字符串避免雪花 ID 精度丢失,参考 `PurchaseReceiveWritebackConsumer:58-65`)。

---

## 十一、代码归属与关键技术决议(已按推荐采纳)

> 2026-07-15 更新:以下四项按设计推荐方案采纳,作为后续实施基准。P2/P3/P4 不再阻塞。

### 11.1 采购进项发票与 AR/AP 代码归属 —— 采纳方案 C(折中)

| 方案 | 说明 | 结论 |
|------|------|------|
| A. 对称分散 | `pu_invoice` 放 pu;AR/AP/核销/对账新建 fin | 未选:发票分散,跨域查询需聚合 |
| B. 统一 fin 财务域 | fin 容纳 so/pu 发票(迁入)+ AR/AP + 核销 + 对账 + 电票 | 未选:`so_invoice` 需迁移,打破 so/pu 对称,改动大 |
| **C. 折中(采纳)** | `pu_invoice` 放 pu(对称 `so_invoice`);AR/AP/核销/对账/电票网关放 `com.nexus.erp.fin`;fin 通过 Service 接口调 so/pu 发票 | ✅ 兼顾对称与内聚,迁移量最小 |

**落地约定**:
- 发票(`so_invoice`/`pu_invoice`)随业务模块 so/pu,便于订单/收货/退货联动
- 财务能力(收付款单 `fin_receipt`、核销 `fin_settlement`、对账 `fin_reconciliation`、电票网关 `EInvoiceGateway`)集中 `com.nexus.erp.fin`
- **防循环依赖**:fin 单向依赖 so/pu 的 Service 接口(`ISoInvoiceService`/`IPuInvoiceService`),so/pu 不反向依赖 fin;跨域查询走 Service 聚合,不写跨模块 XML JOIN
- 核销回写发票 `verified_amount` 由 fin 调 so/pu Service 完成(单向)

### 11.2 pay_invoice 去留 —— 采纳:废弃

`pay_invoice` 空壳(仅 6 字段),发票统一在 ERP。C 端开票需求由"P3 支付回调 -> `fin_receipt` -> `so_invoice`"链路覆盖。

**落地约定**:
- P3 完成支付回调->收款单->核销链路后,`PayInvoice`/`PayInvoiceController`/`IPayInvoiceService` 及前端 `views/pay/invoice` 标记 `@Deprecated`,不再新增功能
- 待 P3 链路验证稳定后,后续版本移除 `pay_invoice` 表与相关代码(提供存量迁移脚本将存量 `pay_invoice` 转为 `so_invoice` 草稿;无存量则直接下线)
- 下线前保留只读查询,避免存量数据丢失

### 11.3 数电票平台选型 —— 采纳:先 SPI + Mock

P4 先建 `EInvoiceGateway` SPI 抽象 + `ManualEInvoiceGateway`(默认实现,仅改 `issue_channel`/状态,不调外部平台),平台凭证就绪后再接百望/航信/用友具体实现。

**落地约定**:
- `nexus.einvoice.provider` 默认 `manual`,配置切换 `baiwang`/`aisino`/`yonyou`
- `ManualEInvoiceGateway` 保证 P1-P3 不被外部凭证阻塞,可端到端跑通开票/核销流程
- 凭证就绪后,新增对应 `*EInvoiceGateway` 实现并切换配置,业务代码零改动(SPI 隔离)

### 11.4 财务异步线程池 —— 采纳:新建 financialExecutor

新建 `FinancialThreadPoolConfig` 的 `financialExecutor` Bean,专用于开票/核销/电票异步任务,与 `aiExecutor` 隔离。

**落地约定**:
- 参考 `AiThreadPoolConfig` 结构(独立核心数、队列容量、拒绝策略)
- `SalesOrderFulfilledEventListener`、`PayReceivedConsumer`、`EInvoiceResultConsumer` 等异步/消费任务用 `financialExecutor`
- 隔离目的:避免 AI 长任务阻塞财务实时性要求高的核销/回调处理

---

## 十二、分阶段实施计划与风险

### 12.1 阶段计划

| 阶段 | 范围 | 关键交付 | 风险 | 依赖 |
|------|------|----------|------|------|
| **P0** 基础修补 | 第四节 | BdClient/BdVendor 开票信息、invoicedQty、pu 价税分离、pay_order 金额类型、字典化 | 低(纯加字段) | 无 |
| **P1** 销售开票闭环 | 第五节 | Event 监听器、generateDraftFromOrder、verify/cancel/redFlush、差量更新+乐观锁 | 中(状态机) | P0 |
| **P2** 采购进项发票 | 第六节 | pu_invoice 模块、三单匹配、认证、退货进项转出 | 中高(新模块) | P0 |
| **P3** AR/AP + 核销 | 第七节 | fin_receipt/fin_settlement、核销回写、账龄、pay 回调->收款->自动核销 | 中高(跨库 MQ) | P1/P2 |
| **P4** 电子发票网关 | 第八节 | EInvoiceGateway SPI + 数电票开具/红冲/下载 | 高(外部平台凭证) | P1 |
| **P5** 财务对账 | 第九节 | 银行流水勾对、对账批次、差异处理 | 中 | P3 |

### 12.2 风险与回滚

| 风险 | 应对 | 回滚 |
|------|------|------|
| 跨库 MQ 消息丢失/重复 | 消费者幂等(收付款单按 `third_party_no`/`pay_order_number` 去重) | MQ 为增量链路,停消费即回退手工 |
| 异步租户上下文丢失 | 所有异步写库 `TenantHelper.dynamic` 包裹 + 消息体带 tenantId | 参考 AI 异步坑,审计 `tenant_id=0` 记录 |
| 核销并发超开 | `verified_amount`/`settled_amount` 用 `setSql` 原子累加 + 乐观锁,核销前校验余额 | 乐观锁失败重试/报错 |
| 红冲金额错算 | 红字发票金额取负,核销冲减原发票,单元测试覆盖 | 红冲为新增记录,删记录即回退 |
| 数电票平台凭证缺失 | P4 先 Mock,平台就绪再接 | `issue_channel=MANUAL` 回退手工 |
| pay_order 金额单位迁移 | 存量"分"->"元"换算脚本,无生产数据可直接重建 | MODIFY COLUMN 可逆向 |
| pay_invoice 废弃期数据孤岛 | 11.2 决议:先 @Deprecated 只读,P3 链路稳定后再下线,提供存量迁移脚本 | 下线前保留只读查询 |

### 12.3 实施顺序

1. P0(地基,可立即开工,不阻塞任何决议)
2. P1(用户可见价值最高)
3. P2(补采购空白,归属已定方案 C)
4. P3(打通发票↔收款,pay 集成)
5. P4(电票合规,先 SPI+Mock,凭证就绪再接平台)
6. P5(财务对账收尾)

---

## 十三、文件清单(预估,实施阶段细化)

| 阶段 | 类型 | 文件 |
|------|------|------|
| P0 | SQL | `doc/erp/sql/erp-invoicing-p0-data-model.sql`(bd_client/bd_vendor 开票四要素 + so/pu invoiced_qty + pu 价税分离) |
| P0 | SQL | `doc/erp/sql/dict-invoicing.sql`(发票/收付款字典 6004xx 段) |
| P0 | 改 | `BdClient.java`/`BdVendor.java`/`SoSalesOrderLine.java`/`PuPurchaseOrder.java`/`PuPurchaseOrderLine.java`/`PuPurchaseReturn.java`/`PuPurchaseReturnLine.java`/`PuContract.java`/`PuContractLine.java` + 对应 VO/BO/前端 |
| P0 | 字典 | `so_invoice_type`/`so_invoice_status`/`pu_invoice_*`/`fin_receipt_type` 等(雪花 ID 动态分配) |
| P1 | SQL | `script/sql/update/2026-07-15-so-invoice-enhance.sql` |
| P1 | 新 | `so/event/SalesOrderFulfilledEventListener.java` |
| P1 | 改 | `so/event/SalesOrderFulfilledEvent.java`(补 tenantId) |
| P1 | 改 | `ISoInvoiceService.java`/`SoInvoiceServiceImpl.java`(generateDraftFromOrder/verify/cancel/redFlush/差量更新) |
| P1 | 改 | `SoSalesOrderHeaderServiceImpl.java:300`(发布事件传 tenantId) |
| P1 | 新 | `FinancialThreadPoolConfig.java`(financialExecutor,11.4) |
| P1 | 改 | 前端 `views/erp/so/invoice/index.vue`(核销/红冲/字典化) |
| P2 | SQL | `script/sql/update/2026-07-15-pu-invoice-create.sql` |
| P2 | 新 | `pu/invoice/` 整包(PuInvoice/PuInvoiceLine/Controller/Service/Impl/BO/VO/Mapper) |
| P2 | 改 | `PuPurchaseReturnServiceImpl.java`(退货进项转出) |
| P2 | 新 | 前端 `views/erp/pu/invoice/` + `api/erp/pu/invoice/` |
| P3 | SQL | `script/sql/update/2026-07-15-fin-receipt-settlement.sql` |
| P3 | 新 | `fin/` 整包(fin_receipt/fin_settlement/Controller/Service/Impl,11.1 方案 C) |
| P3 | 新 | `PayReceivedConsumer.java`(ERP 侧) |
| P3 | 改 | `AliPayServiceImpl.java`/`WxPayServiceImpl.java`/`SQBServiceImpl.java`(回调发 MQ) |
| P3 | 改 | `QueueConstants.java`(追加通道) |
| P3 | 改 | `pay` 侧 `PayInvoice*` 标记 @Deprecated(11.2) |
| P3 | 新 | 账龄报表 Controller/积木报表 |
| P4 | 新 | `EInvoiceGateway` SPI + Manual/Baiwang/Aisino 实现(11.3) |
| P4 | 新 | `EInvoiceResultConsumer.java` |
| P4 | 配置 | `application-secret.yml`(`nexus.einvoice.*`) |
| P5 | SQL | `script/sql/update/2026-07-15-fin-reconciliation.sql` |
| P5 | 新 | `fin/reconciliation/` 对账批次 + 明细 + 导入/勾对 |

---

## 十四、验收标准(各阶段)

- **P0**:BdClient/BdVendor 表单可录入开票四要素;so/pu 订单行展示 invoicedQty;pay_order 金额可数学运算;发票类型/状态走字典。
- **P1**:销售订单 fulfill 后自动生成发票草稿(含已发货未开票行);发票可核销/作废/红冲;状态机可走通 DRAFT->ISSUED->VERIFIED;并发编辑不丢行(乐观锁)。
- **P2**:可登记进项发票;三单匹配校验数量/金额/税额;可认证/抵扣;退货触发进项转出红冲。
- **P3**:可录收款单/付款单;核销回写 `verified_amount`,达额自动 VERIFIED;pay 支付成功->ERP 自动生成收款单->自动核销发票;AR/AP 账龄报表正确。
- **P4**:`issue_channel=ELECTRONIC` 时调网关开具,返回数电票号码与 PDF/OFD;红冲走平台。
- **P5**:银行流水导入自动勾对收付款单;差异可人工处理;对账批次可关闭。

---

## 十五、P0 实施记录(2026-07-15)

P0(基础数据模型修补)ERP 侧已落地并验证。pay_order decimal 迁移作为独立子任务暂缓(详见 4.5)。

### 15.1 已完成

| 项 | 交付 | 验证 |
|----|------|------|
| 增量 SQL | `doc/erp/sql/erp-invoicing-p0-data-model.sql`(10 段 ALTER:bd_client/bd_vendor 开票四要素 + so/pu invoiced_qty + pu 价税分离) | 本地 ry-erp 执行成功,26 个新列验证存在 |
| 字典 SQL | `doc/erp/sql/dict-invoicing.sql`(8 字典 6004xx 段,29 条数据) | 本地 ry-vue 执行成功,8 类型 + 29 数据验证存在 |
| 实体 | 9 实体补字段:BdClient/BdVendor/SoSalesOrderLine/PuPurchaseOrder(Line)/PuPurchaseReturn(Line)/PuContract(Line) | `mvn -pl ruoyi-erp -am compile` 通过,13 个 .class 生成 |
| VO/BO | BdClientVo/Bo、BdVendorVo/Bo 补开票字段 | 同上(MapStruct @AutoMapper 自动映射新字段) |
| 前端 | BdClient/BdVendor 表单"开票信息"页签;so_invoice/index.vue 硬编码改 useDict+dict-tag,删 3 个冗余 map 函数 | `npx eslint` 3 文件通过 |
| 文档/记忆 | Section 4 修正块、4.2/4.6 更正、第十三节文件名对齐;更正 ruoyi-snowflake-dict-menu-id 记忆 | - |
| pay_order decimal 迁移 | `doc/erp/sql/erp-invoicing-p0-pay-order-decimal.sql`(varchar 分 -> decimal 元);PayOrder/Vo/Bo String->BigDecimal;新增 `MoneyUtil`;AliPay/Wx/SQB 三 SDK 单位换算;前端 2 文件标签(详见 4.5.1) | 本地 ry-vue 迁移成功 22 条校验通过;`mvn -pl ruoyi-pay -am compile -o` EXIT 0;eslint 零新增(运行时端到端验证待办) |

### 15.2 待办(不阻塞 P1)

- **pu 侧新字段 VO/BO 暴露**:invoiced_qty/价税分离字段延后到 P1 开票逻辑消费时一并补;当前 pu 订单/退货/合同表单的价税分离 UI 属 P1+ 范畴
- **pay_order 运行时端到端验证**:编译已过,但支付宝沙箱下单->回调->查询的金额正确性需启动应用实跑一次确认(支付真金白银代码,见 4.5.1)

---

## 十六、P1 销售开票闭环 实施记录(2026-07-16)

P1 全部落地并编译/eslint 通过。运行时端到端验证为待办(同 pay_order,仅编译级验证)。

### 16.1 已完成

| 子任务 | 交付 | 验证 |
|--------|------|------|
| P1-0 so_invoice 增强 | `doc/erp/sql/p1-so-invoice-enhance.sql`(14 列:invoice_code/buyer_四要素/original_invoice_id/red_flag/select_flow_id/issue_channel/e_invoice_no/pdf_oss_id/ofd_oss_id/version+2 索引);SoInvoice/Vo/Bo 补字段(@Version、pdf/ofd 用 @Translation(OSS_ID_TO_URL) 解析 URL);SoSalesOrderLineVo 补 invoicedQty | 本地 ry-erp 执行成功,14 列验证存在(version 默认 0、issue_channel 默认 MANUAL、select_flow_id 终于可持久化) |
| P1-1 financialExecutor | `com.nexus.erp.config.FinancialThreadPoolConfig`(镜像 AiThreadPoolConfig,core4/max16/queue200/CallerRunsPolicy) | - |
| P1-2 Event 补 tenantId | `SalesOrderFulfilledEvent` 加 tenantId;`SoSalesOrderHeaderServiceImpl:300` 传 `TenantHelper.getTenantId()` | - |
| P1-3 generateDraftFromOrder | `ISoInvoiceService`/Impl 新增;按 `delivered_qty - invoiced_qty >0` 生成 DRAFT,原子累加 invoiced_qty(setSql),buyer_* 从 BdClient 取,invoiceNo=`FP`+`IdWorker.getId()` | - |
| P1-4 事件监听器 | `so/event/SalesOrderFulfilledEventListener`:`@TransactionalEventListener(AFTER_COMMIT)`+`@Async("financialExecutor")`+`TenantHelper.dynamic`,异常吞掉 | - |
| P1-5 状态机 | verifyInvoice(原子累加 verified_amount+达额转 VERIFIED,带乐观锁)/cancelInvoice(回退 invoiced_qty)/redFlush(红字负数,冲减原 verified_amount 与 invoiced_qty);Controller 加 `/verify/{id}``/cancel/{id}``/redFlush/{id}` 三端点 | - |
| P1-6 差量更新+乐观锁 | `updateByBo` 全删重插 → 按 lineId 差量(新增/更新/删除);头更新带 @Version(existing.version 来自 DB,失败抛"数据已变更");deleteByIds 删草稿前回退 invoiced_qty | - |
| P1-7 前端 | `so/invoice/index.vue` 操作列加 核销/红冲/作废 按钮(按 status 显隐)+ 核销弹窗;表单加发票代码/开票渠道/购买方四要素;`api/erp/so/invoice` 加三接口;types.ts 补字段 | eslint 0 problems(--fix 折行后) |
| 编译 | - | `mvn -pl ruoyi-erp -am compile -o` EXIT 0 |

### 16.2 关键决策与坑

- **监听器用 `@TransactionalEventListener(AFTER_COMMIT)` 而非普通 `@EventListener`**:fulfill 是 @Transactional,事件在事务内发布;普通 @Async @EventListener 会在提交前读到旧 delivered_qty,导致可开票量算错。AFTER_COMMIT 确保生成草稿时读到履约后的已发货量。`fallbackExecution=true` 兜底无事务场景。
- **发票号生成**:`generateDraftFromOrder` 用 `FP`+`IdWorker.getId()`(雪花),**未用 AutoCodeUtil**--`genSerialCode` 在规则未配置时 Assert 抛异常,为避免依赖 `SO_INVOICE_CODE` auto-code 配置 + 三处对齐复杂度,auto-draft 走雪花(唯一保证);手动新增仍走 BO `@NotBlank` 用户录入。
- **SO 单价语义**:so_sales_order_line.unit_price 含税与否服务端不强制(DDL 仅注 "行金额=qty*price");草稿按"SO 单价为不含税单价"计算 lineAmount=qty×unitPrice、taxAmount=lineAmount×taxRate/100,草稿可编辑。
- **乐观锁初始化**:insertByBo 与 generateDraftFromOrder 均 `setVersion(0)`(参考库存乐观锁坑,不初始化则 WHERE version=NULL 永不匹配)。
- **核销/作废/红冲权限**:复用 `so:invoice:edit`(避免新增权限需配套菜单 SQL);TODO P3 拆分 `so:invoice:verify` 独立财务权限。
- **幂等**:generateDraftFromOrder 靠 `delivered-invoiced` 自然幂等(全 0 则跳过);cancel/delete 回退 invoiced_qty 以释放可开票量。

### 16.3 验证与待办

- **运行时端到端验证 ✅ 基本通过(2026-07-16)**:fulfill→自动生成草稿→编辑(差量+乐观锁)→核销达额转 VERIFIED→红冲冲减→作废回退,已实跑通过(含 tenant_id≠0、并发乐观锁)。
- **so:invoice:verify 权限拆分**(P3):核销是敏感财务动作,应独立权限 + 菜单按钮。
- **发票号顺序化**(可选):若需 FP20260716001 式顺序号,补 `SO_INVOICE_CODE` auto-code 规则配置 + 三处对齐(见 [[autocode-form-pattern]])。

---

## 十七、P2 采购进项发票 实施记录(2026-07-16)

P2 全部落地并编译/eslint/造数通过。镜像 so 销售开票,新增进项认证子状态机与三单匹配,退货联动进项转出。运行时端到端验证待跑(同 P1 模式,见 17.3 调用序列)。

### 17.1 已完成

| 子任务 | 交付 | 验证 |
|--------|------|------|
| P2-0 SQL 建表+菜单 | `doc/erp/sql/p2-pu-invoice-create.sql`(pu_invoice 43 列:镜像 so + 销售方四要素 seller_* + cert_status/cert_date/deductible_tax_amount + version;pu_invoice_line 30 列:po_line_id/gr_line_id/contract_line_id);`doc/erp/sql/erp-pu-invoice-menu.sql`(2550-2558,9 权限,verify/certify/match 一开始即拆分) | ry-erp 建表成功(头 43/行 30 列);ry-vue 9 菜单落库;字典复用 dict-invoicing.sql(pu_invoice_type/status/cert_status 已在 P0 落库) |
| P2-1 实体/VO/BO/Mapper | `com.nexus.erp.pu.invoice`:PuInvoice(@Version + cert + seller 四要素 + vendorId/poId)、PuInvoiceLine(poLineId/grLineId)、PuInvoiceVo(@Translation pdf/ofd + cert + seller)、PuInvoiceBo(不暴露 cert/verified/version)、PuInvoiceMapper、PuInvoiceLineMapper、MatchResultVo | - |
| P2-2 Service/Controller | IPuInvoiceService(状态机 + cert 状态机常量)+ PuInvoiceServiceImpl(@DS("erp")):CRUD(差量更新+乐观锁)、generateDraftFromPo(按 received-invoiced,seller_* 从 BdVendor,原子累加 po invoiced_qty)、receiveInvoice(DRAFT->ISSUED)、matchThreeWay(PO+GR+Invoice)、verifyInvoice(前置三单匹配+原子累加)、certifyInvoice/deductInvoice、cancelInvoice、redFlush(全量)、redFlushForReturn(部分)+redFlushForReturnByPoLines;PuInvoiceController(`/erp/pu/invoice`)13 端点 | `mvn -pl ruoyi-modules/ruoyi-erp -am compile -o` EXIT 0;pu.invoice 包 15 个 .class 生成 |
| P2-3 退货进项转出 | `PuPurchaseReturnServiceImpl.execute()` 增强:扣库存后按退货 PO 行调 `redFlushForReturnByPoLines`,仅 DEDUCTED 触发部分红冲 + 原 cert->TRANSFERRED + 冲减 verified/invoiced_qty | 同上编译 |
| P2-4 前端 | `api/erp/pu/invoice/{types,index}.ts` + `views/erp/pu/invoice/index.vue`(认证状态列 + 收票/匹配/认证/抵扣/核销/红冲/作废/从订单生成按钮 + 销售方四要素表单 + 供应商内联选择对话框) | `npx eslint --fix` EXIT 0 |
| P2-5 E2E 造数 | `doc/erp/sql/p2-e2e-test-data.sql`(供应商 8800002 + PO 8800200 + 2 行全额收货未开票 + 库存 8803001 + 退货 8800400) | 本地 ry-erp 执行成功,5 项校验通过(PO 1695/1500/195、2 行 received=ordered/invoiced=0、vendor 四要素、退货 CONFIRMED、库存 frozen=0) |

### 17.2 关键决策与坑

- **销售方四要素**:spec 3.2 草图只有 vendor_id/vendor_name,补 `seller_tax_id/title/bank_name/bank_account/address_phone` 落实"购买方↔销售方"对偶(so 的 buyer_* 对应 pu 的 seller_*),进项认证需核对销售方纳税人识别号。
- **税率形态不一致**:PO 行 taxRate 存小数(0.13,per `PuPurchaseOrderLine` 注释"0.13 表示 13%"),发票行 taxRate 用百分比(13,与 so 一致、前端 `×taxRate/100`)。抽 `normalizeTaxRateToPercent`(`<1 视为小数 ×100`)归一化,`generateDraftFromPo` 与 `matchThreeWay` 均用此,避免比较错配。
- **不含税单价**:`generateDraftFromPo` 用 PO 的 `unitPriceExcludingTax`(P0 已加),缺失回退 `unitPrice`(视为不含税);lineAmount=qty×unitPriceExcl,taxAmount=lineAmount×taxRate/100。
- **无 Flowable**:进项发票收票登记无需内部审批流转,状态机 `DRAFT->ISSUED`(收票确认),省略 so 的 APPROVING/REJECTED;IPuInvoiceService 不继承 WfCallBackServiceI。
- **权限一开始即拆分**(吸取 so 的 `so:invoice:verify` 待 P3 拆分教训):核销/红冲=`pu:invoice:verify`,认证/抵扣=`pu:invoice:certify`,三单匹配=`pu:invoice:match`,收票/作废/CRUD=`pu:invoice:edit`,从订单生成=`pu:invoice:add`。
- **三单匹配前置 verify**:`verifyInvoice` 内部调 `matchThreeWay`,带 PO 关联行且失败则抛异常阻止核销;纯手工发票(无 poLineId)跳过匹配(matchThreeWay 返回 passed=true)。匹配维度:物料一致、开票数量≤收货数量、不含税单价(容差 0.01)、税率(归一化后容差 0.01)。
- **退货进项转出仅 DEDUCTED 触发**:`execute()` 只对 `cert_status=DEDUCTED` 的覆盖发票红冲(未抵扣无进项可转出,spec 6.4);部分红冲按退货数量取负(非全量);`execute` 同事务,红冲失败回滚退货(含库存扣减,原子)。
- **`redFlushForReturnByPoLines` 封装**:退货 Service 不直连 pu_invoice 表,由 IPuInvoiceService 按 PO 行查覆盖发票(lineMapper.selectList + baseMapper.selectList 过滤蓝字/DEDUCTED/已收已核销)并逐个 `redFlushForReturn`。
- **`pu_purchase_order` 表有 tenant_id**:实体继承 BaseEntity 但表有 tenant_id 列(租户插件自动填),SQL 造数显式置 `000000`;退货 Service 的 `TenantHelper.dynamic` 由 execute 调用链上下文继承(同步调用,无异步租户坑)。
- **乐观锁初始化**:insertByBo 与 generateDraftFromPo 均 `setVersion(0)`(同 so,参考库存乐观锁坑)。

### 17.3 验证与待办

- **编译/eslint/造数均通过**;**运行时端到端验证待跑**(同 P1 模式:启动应用按下列调用序列实跑)。
- **调用序列(主链路)**:造数 `p2-e2e-test-data.sql` 后,
  1. `POST /erp/pu/invoice/generateFromPo/8800200` -> 返回 invoiceId(草稿,2 行,价税合计 1695,seller_* 已带入)
  2. `PUT /erp/pu/invoice/receive/{invoiceId}` -> DRAFT->ISSUED
  3. `GET /erp/pu/invoice/match/{invoiceId}` -> 返回 passed=true(数量/单价/税率均与 PO 一致)
  4. `PUT /erp/pu/invoice/certify/{invoiceId}` -> UNCERTIFIED->CERTIFIED,可抵扣税额=195
  5. `PUT /erp/pu/invoice/deduct/{invoiceId}` -> CERTIFIED->DEDUCTED
  6. `PUT /erp/pu/invoice/verify/{invoiceId}?verifyAmount=1695` -> verified=1695,ISSUED->VERIFIED(前置三单匹配通过)
  7. `PUT /erp/pu/invoice/redFlush/{invoiceId}` -> 生成红字发票(负数 1695),原发票 verified 1695->0、VERIFIED->ISSUED、cert DEDUCTED->TRANSFERRED
- **调用序列(退货进项转出)**:主链路执行到第 5 步(deduct,发票 DEDUCTED)后,**勿做 verify/redFlush**,
  - `PUT /erp/pu/return/execute/8800400` -> 扣库存(100->90)+ 触发 `redFlushForReturnByPoLines({8800301:10})` -> 生成红字发票(负数 113,即 10 个物料A)+ 原发票 cert DEDUCTED->TRANSFERRED + 原发票 verified 冲减 113 + PO 行 8800301 invoiced_qty 100->90
- **待办**:
  - **菜单角色授权**:9 个 pu:invoice:* 权限需配角色后前端可见(同所有新菜单)。
  - **standalone redFlush 与退货红冲互斥**:同一发票二选一(红冲后 cert=TRANSFERRED,退货红冲仅查 DEDUCTED);测试时需 reset 造数切换场景。
  - **运行时端到端**:启动应用实跑上述序列(当前仅编译+造数级验证)。

---

> **下一步**:P0 + P1 + P2 全部落地并通过编译/造数验证(P1 已端到端基本通过 2026-07-16;P2 运行时端到端待跑,见 17.3)。下一步进 **P3 应收应付与核销**(`fin_receipt`/`fin_settlement` + 核销回写 `verified_amount` + pay 支付回调->收款单->自动核销 + AR/AP 账龄,跨库 MQ,方案 C 已定)。
