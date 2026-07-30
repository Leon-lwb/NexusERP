# F0 GL 总账模块 详细实施 spec

> **文档编号**: NexusERP-SPEC-2026-005-F0
> **创建日期**: 2026-07-29
> **状态**: 设计待评审
> **所属计划**: [2026-07-29-erp-financial-core-dev-plan.md](../plans/2026-07-29-erp-financial-core-dev-plan.md)(财务核心 F0 阶段)
> **前置**: 无(F0 是财务核心地基,无财务前置依赖)
> **后置依赖方**: F1 AR/AP 核销(发票/收付款过账)、F2 FA(折旧过账)、F3 CO(成本过账)、F5 报表(读取 GL 余额)均依赖本模块的凭证引擎与 AccountingPoster SPI
> **模式参考**: so/pu_invoice(P1/P2 已落地)、BdMaterialCategory(物化路径树)、WmInventoryReportController(报表)、AutoCodeUtil(单号)

---

## 一、范围与目标

### 1.1 范围

`com.nexus.erp.fin.gl` 子包,落地 GL 总账地基:

| 能力 | 交付 |
|---|---|
| 科目表 | 树状(物化路径)、科目类型、统驭标识、余额方向;预置企业会计准则一级科目 + AR/AP 统驭科目 |
| 记账凭证 | 头+行复式记账、借贷平衡强校验、草稿/过账/反过账/作废状态机、差量更新+乐观锁 |
| 会计期间 | 年/月期间、开账/关闭/锁定状态、过账期间校验 |
| 多币种 | 汇率维护、外币凭证本币+原币双存储 |
| 科目余额 | 按期间+科目的期初/本期发生/期末余额(试算平衡与报表基座) |
| 凭证引擎 | `GlVoucherService.post()/unpost()` 过账/反过账,过账时更新科目余额 |
| 试算平衡表 | 按期间/科目层级汇总借贷余额报表 |
| AccountingPoster SPI | 业务自动制单扩展点 + 注册表 + `GlPostingConsumer`(F1+ 各业务 Poster 实现此 SPI) |

### 1.2 不含(后置)

- 三大报表(资产负债/利润/现金流量)-> F5
- 期末损益结转/月结年结向导 -> F5
- 外币期末重估 -> F5
- 业务 Poster 实现(销售发票/采购发票/收付款/折旧/成本)-> F1/F2/F3
- 多组织/多法人独立核算 -> 后置

### 1.3 目标

提供借贷平衡、期间受控、可被各业务子账过账的 GL 地基,使 F1+ 的发票/收付款/折旧/成本能通过 `AccountingPoster` 自动生成凭证并入总账。

---

## 二、模式参考(grounding)

| 关注点 | 参考源 | 关键证据 |
|---|---|---|
| 实体基类 | `SoInvoice extends TenantEntity` | `SoInvoice.java:27` |
| 主键/乐观锁/逻辑删 | `@TableId`/`@Version`/`@TableLogic` | `SoInvoice.java:34,175,212` |
| @DS | Service 类级 `@DS("erp")` | `SoInvoiceServiceImpl.java:60` |
| 差量行更新 | `diffUpdateLines` 按 lineId | `SoInvoiceServiceImpl.java:300-326` |
| 原子累加+手动乐观锁 | `setSql("field=field+?")` + `eq(version)` + `set(version+1)` | `SoInvoiceServiceImpl.java:373` |
| BO/VO | BO `extends BaseEntity`+`@AutoMapper`;VO `implements Serializable`+`@AutoMapper` | `SoInvoiceBo.java:26`/`SoInvoiceVo.java:27` |
| Mapper | `extends BaseMapperPlus<Entity,Vo>` | `SoInvoiceMapper.java:13` |
| Controller | `extends BaseController`+`@SaCheckPermission`+`PageQuery`+`selectVoPage` | `SoInvoiceController.java:35` |
| 物化路径树 | `parentId`+`pathIds`+`pathNames`+`levelNum`+`FIND_IN_SET`+前缀替换 | `BdMaterialCategoryServiceImpl.java:346-389` |
| 单号 | `AutoCodeUtil.genSerialCode(rule,input)` 或 `IdWorker.getId()` | `PuPurchaseOrderServiceImpl.java:162`/`SoInvoiceServiceImpl.java:201` |
| 报表 | `@DS("erp")` Controller+`R<List<Map>>` | `WmInventoryReportController.java:23,38` |
| 字典 SQL | 硬编码 `6004xx` 段+`INSERT IGNORE` | `dict-invoicing.sql` |
| 菜单 SQL | 硬编码 `menu_id` 段+`parent_id`+`INSERT IGNORE` | `erp-pu-invoice-menu.sql` |
| 财务线程池 | `financialExecutor`(core4/max16/queue200) | `FinancialThreadPoolConfig.java:32` |
| 租户/用户工具 | `TenantHelper.dynamic`/`LoginHelper.getUserId/getUsername` | - |

> **字典段位**:GL 用 `6005xx`(避让 6001xx bom-routing / 6002xx IoT / 6003xx pu-contract / 6004xx invoicing)。
> **菜单段位**:GL 用 `2600-2620`(避让 25xx 采购/销售/发票)。

---

## 三、数据模型(DDL)

> 全部落 `ry-erp` 库,带 `tenant_id`/审计字段/`version` 乐观锁/`del_flag`。增量脚本 `script/sql/update/2026-07-29-f0-gl-ledger.sql`,权威 schema 并入 `script/sql/ry-erp.sql`。

### 3.1 gl_account(科目表)

```sql
DROP TABLE IF EXISTS `gl_account`;
CREATE TABLE `gl_account` (
  `account_id` bigint(20) NOT NULL COMMENT '科目ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `account_code` varchar(50) NOT NULL COMMENT '科目编码(如 1001)',
  `account_name` varchar(200) NOT NULL COMMENT '科目名称',
  `account_type` varchar(20) NOT NULL COMMENT '科目类型 ASSET-资产 LIABILITY-负债 EQUITY-权益 COST-成本 INCOME-收入 EXPENSE-费用',
  `balance_dir` varchar(10) NOT NULL COMMENT '余额方向 DEBIT-借 CREDIT-贷',
  `parent_id` bigint(20) NULL DEFAULT 0 COMMENT '父科目ID(0=顶级)',
  `level_num` int(11) NULL DEFAULT 1 COMMENT '层级(1=一级)',
  `path_ids` varchar(500) NULL DEFAULT NULL COMMENT '路径ID串(如 0,1001,100101)',
  `path_names` varchar(1000) NULL DEFAULT NULL COMMENT '路径名称串',
  `is_leaf` char(1) NULL DEFAULT '1' COMMENT '是否末级 0-否 1-是(仅末级可过账)',
  `is_recon` char(1) NULL DEFAULT '0' COMMENT '是否统驭科目 0-否 1-是(AR/AP/FA 子账汇总)',
  `recon_type` varchar(10) NULL DEFAULT NULL COMMENT '统驭类型 AR-应收 AP-应付 FA-固定资产',
  `is_open_item` char(1) NULL DEFAULT '0' COMMENT '是否未清项管理 0-否 1-是(行项目可清账)',
  `currency` varchar(10) NULL DEFAULT 'CNY' COMMENT '币种(空=本位币)',
  `status` char(1) NULL DEFAULT '0' COMMENT '状态 0-正常 1-停用',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`account_id`) USING BTREE,
  UNIQUE INDEX `uk_account_code`(`account_code`, `tenant_id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE,
  INDEX `idx_path_ids`(`path_ids`(64)) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会计科目表' ROW_FORMAT = DYNAMIC;
```

> **物化路径**:新增/移动科目时计算 `path_ids`/`path_names`/`level_num`(参照 `BdMaterialCategoryServiceImpl.fillPathAndLevel`);父级变更用前缀替换刷新子孙路径(参照 `updateCategoryChildren`)。子树查询用 `FIND_IN_SET` 或 `path_ids LIKE 'prefix%'`。
> **末级过账**:仅 `is_leaf=1` 科目可被凭证引用(过账前校验),非末级科目为汇总用。
> **统驭科目**:`is_recon=1` + `recon_type` 标识 AR/AP/FA 子账汇总科目(如 1131 应收账款/2202 应付账款),F1+ 业务 Poster 借/贷此类科目。

### 3.2 gl_voucher(记账凭证头)

```sql
DROP TABLE IF EXISTS `gl_voucher`;
CREATE TABLE `gl_voucher` (
  `voucher_id` bigint(20) NOT NULL COMMENT '凭证ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `voucher_no` varchar(50) NOT NULL COMMENT '凭证号(业务号,顺序化)',
  `voucher_word` varchar(10) NULL DEFAULT 'J' COMMENT '凭证字 J-记 R-收 P-付 T-转',
  `voucher_date` date NOT NULL COMMENT '凭证日期(决定会计期间)',
  `period_id` bigint(20) NULL DEFAULT NULL COMMENT '会计期间ID(由 voucher_date 推导)',
  `voucher_source` varchar(30) NOT NULL DEFAULT 'MANUAL' COMMENT '来源 MANUAL-手工 SO_INVOICE-销售发票 PU_INVOICE-采购发票 RECEIPT-收付款 DEPRECIATION-折旧 COST-成本 ADJUSTMENT-调整',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID(业务凭证回溯)',
  `source_no` varchar(50) NULL DEFAULT NULL COMMENT '来源单据号(冗余)',
  `attachment_count` int(11) NULL DEFAULT 0 COMMENT '附件张数',
  `total_debit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '借方合计(过账时写入)',
  `total_credit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '贷方合计',
  `currency` varchar(10) NULL DEFAULT 'CNY' COMMENT '本位币',
  `status` varchar(20) NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT-草稿 POSTED-已过账 CANCELLED-已作废',
  `posted_by` bigint(20) NULL DEFAULT NULL COMMENT '过账人',
  `posted_time` datetime NULL DEFAULT NULL COMMENT '过账时间',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` varchar(500) NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`voucher_id`) USING BTREE,
  UNIQUE INDEX `uk_voucher_no`(`voucher_no`, `tenant_id`) USING BTREE,
  INDEX `idx_period_id`(`period_id`) USING BTREE,
  INDEX `idx_voucher_date`(`voucher_date`) USING BTREE,
  INDEX `idx_source`(`voucher_source`, `source_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '记账凭证头' ROW_FORMAT = DYNAMIC;
```

### 3.3 gl_voucher_line(凭证行)

```sql
DROP TABLE IF EXISTS `gl_voucher_line`;
CREATE TABLE `gl_voucher_line` (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `voucher_id` bigint(20) NOT NULL COMMENT '凭证ID',
  `line_no` int(11) NULL DEFAULT 0 COMMENT '行号',
  `account_id` bigint(20) NOT NULL COMMENT '科目ID(须末级)',
  `account_code` varchar(50) NULL DEFAULT NULL COMMENT '科目编码(冗余,便于查询)',
  `account_name` varchar(200) NULL DEFAULT NULL COMMENT '科目名称(冗余)',
  `summary` varchar(500) NULL DEFAULT NULL COMMENT '摘要',
  `debit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '借方金额(本币)',
  `credit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '贷方金额(本币)',
  `currency` varchar(10) NULL DEFAULT 'CNY' COMMENT '原币',
  `amount_currency` decimal(18,2) NULL DEFAULT 0.00 COMMENT '原币金额',
  `exchange_rate` decimal(18,6) NULL DEFAULT 1.000000 COMMENT '汇率',
  `partner_id` bigint(20) NULL DEFAULT NULL COMMENT '往来对象ID(客户/供应商,统驭科目用)',
  `partner_type` varchar(10) NULL DEFAULT NULL COMMENT 'CLIENT-客户 VENDOR-供应商',
  `open_item` char(1) NULL DEFAULT '0' COMMENT '是否未清项 0-否 1-是',
  `cleared` char(1) NULL DEFAULT '0' COMMENT '是否已清账 0-否 1-是',
  `cleared_by` bigint(20) NULL DEFAULT NULL COMMENT '清账核销记录ID(F1 fin_settlement.settlement_id)',
  `sort_order` int(11) NULL DEFAULT 0,
  `remark` varchar(500) NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_voucher_id`(`voucher_id`) USING BTREE,
  INDEX `idx_account_id`(`account_id`) USING BTREE,
  INDEX `idx_partner`(`partner_type`, `partner_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '记账凭证明细行' ROW_FORMAT = DYNAMIC;
```

> **未清项**:`open_item=1` 的行(应收/应付/预收/预付)参与核销清账,F1 核销时置 `cleared=1`+`cleared_by=settlement_id`。本字段为 F1 预留,F0 仅落字段不消费。

### 3.4 gl_period(会计期间)

```sql
DROP TABLE IF EXISTS `gl_period`;
CREATE TABLE `gl_period` (
  `period_id` bigint(20) NOT NULL,
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `fiscal_year` int(11) NOT NULL COMMENT '会计年度',
  `period_no` int(11) NOT NULL COMMENT '期间号 1-12(0=调整期)',
  `period_name` varchar(20) NULL DEFAULT NULL COMMENT '名称(如 2026-07)',
  `start_date` date NOT NULL COMMENT '开始日期',
  `end_date` date NOT NULL COMMENT '结束日期',
  `status` varchar(20) NULL DEFAULT 'OPEN' COMMENT '状态 OPEN-开放 CLOSED-已关账 LOCKED-已锁定',
  `closed_by` bigint(20) NULL DEFAULT NULL,
  `closed_time` datetime NULL DEFAULT NULL,
  `version` int(11) NULL DEFAULT 0,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`period_id`) USING BTREE,
  UNIQUE INDEX `uk_period`(`fiscal_year`, `period_no`, `tenant_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会计期间' ROW_FORMAT = DYNAMIC;
```

> **状态机**:`OPEN`(可过账)-> `CLOSED`(已关账,反过账需授权)-> `LOCKED`(已锁定,不可再动)。过账前校验 `voucher_date` 落在 `OPEN` 期间。

### 3.5 gl_currency_rate(汇率)

```sql
DROP TABLE IF EXISTS `gl_currency_rate`;
CREATE TABLE `gl_currency_rate` (
  `rate_id` bigint(20) NOT NULL,
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `currency` varchar(10) NOT NULL COMMENT '币种(如 USD)',
  `base_currency` varchar(10) NULL DEFAULT 'CNY' COMMENT '本位币',
  `rate_date` date NOT NULL COMMENT '汇率日期',
  `rate` decimal(18,6) NOT NULL COMMENT '汇率(本币=原币×rate)',
  `rate_type` varchar(20) NULL DEFAULT 'MIDDLE' COMMENT '类型 MIDDLE-中间价 BID-买入 ASK-卖出',
  `version` int(11) NULL DEFAULT 0,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`rate_id`) USING BTREE,
  UNIQUE INDEX `uk_rate`(`currency`, `rate_date`, `rate_type`, `tenant_id`) USING BTREE,
  INDEX `idx_rate_date`(`rate_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '币种汇率' ROW_FORMAT = DYNAMIC;
```

### 3.6 gl_account_balance(科目余额)

```sql
DROP TABLE IF EXISTS `gl_account_balance`;
CREATE TABLE `gl_account_balance` (
  `balance_id` bigint(20) NOT NULL,
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `period_id` bigint(20) NOT NULL COMMENT '期间ID',
  `account_id` bigint(20) NOT NULL COMMENT '科目ID(末级)',
  `opening_debit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '期初借方',
  `opening_credit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '期初贷方',
  `period_debit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '本期借方发生',
  `period_credit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '本期贷方发生',
  `closing_debit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '期末借方',
  `closing_credit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '期末贷方',
  `year_opening_debit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '年初借方',
  `year_opening_credit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '年初贷方',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`balance_id`) USING BTREE,
  UNIQUE INDEX `uk_balance`(`period_id`, `account_id`, `tenant_id`) USING BTREE,
  INDEX `idx_account_id`(`account_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '科目余额表(试算/报表基座)' ROW_FORMAT = DYNAMIC;
```

> **余额维护**:凭证过账时 `setSql` 原子累加 `period_debit/period_credit`,并重算 `closing = opening + period`(方向按 `balance_dir`)。期末结转(F5)将 `closing` 结转为下期 `opening`。试算平衡表读此表 + 凭证行汇总校验。

---

## 四、科目表初始化(seed)

预置《企业会计准则》一级科目(节选关键 + AR/AP 统驭),完整 seed 见 `script/sql/update/2026-07-29-f0-gl-account-seed.sql`。一级科目 `is_leaf=0`(汇总),实际过账需在末级新增(如 1001->100101 库存现金)。

```sql
-- 资产类(余额方向:借)
INSERT IGNORE INTO gl_account(account_id,tenant_id,account_code,account_name,account_type,balance_dir,parent_id,level_num,path_ids,path_names,is_leaf,is_recon,recon_type,status,version,create_time) VALUES
(6005001,'000000','1001','库存现金','ASSET','DEBIT',0,1,'0,1001','库存现金','1','0',NULL,'0',0,NOW()),
(6005002,'000000','1002','银行存款','ASSET','DEBIT',0,1,'0,1002','银行存款','1','0',NULL,'0',0,NOW()),
(6005003,'000000','1122','应收账款','ASSET','DEBIT',0,1,'0,1122','应收账款','1','1','AR','0',0,NOW()),   -- AR 统驭
(6005004,'000000','1123','预付账款','ASSET','DEBIT',0,1,'0,1123','预付账款','1','1','AR','0',0,NOW()),
(6005005,'000000','1221','其他应收款','ASSET','DEBIT',0,1,'0,1221','其他应收款','1','0',NULL,'0',0,NOW()),
(6005006,'000000','1401','材料采购','ASSET','DEBIT',0,1,'0,1401','材料采购','0','0',NULL,'0',0,NOW()),
(6005007,'000000','1403','原材料','ASSET','DEBIT',0,1,'0,1403','原材料','1','0',NULL,'0',0,NOW()),
(6005008,'000000','1405','库存商品','ASSET','DEBIT',0,1,'0,1405','库存商品','1','0',NULL,'0',0,NOW()),
(6005009,'000000','1601','固定资产','ASSET','DEBIT',0,1,'0,1601','固定资产','1','1','FA','0',0,NOW()),  -- FA 统驭
(6005010,'000000','1602','累计折旧','ASSET','CREDIT',0,1,'0,1602','累计折旧','1','0',NULL,'0',0,NOW()),
-- 负债类(余额方向:贷)
(6005020,'000000','2202','应付账款','LIABILITY','CREDIT',0,1,'0,2202','应付账款','1','1','AP','0',0,NOW()), -- AP 统驭
(6005021,'000000','2203','预收账款','LIABILITY','CREDIT',0,1,'0,2203','预收账款','1','1','AP','0',0,NOW()),
(6005022,'000000','2211','应付职工薪酬','LIABILITY','CREDIT',0,1,'0,2211','应付职工薪酬','1','0',NULL,'0',0,NOW()),
(6005023,'000000','2221','应交税费','LIABILITY','CREDIT',0,1,'0,2221','应交税费','0','0',NULL,'0',0,NOW()),
(6005024,'000000','2221.01','应交增值税-销项税额','LIABILITY','CREDIT',6005023,2,'0,2221,2221.01','应交税费,应交增值税-销项税额','1','0',NULL,'0',0,NOW()),
(6005025,'000000','2221.02','应交增值税-进项税额','LIABILITY','CREDIT',6005023,2,'0,2221,2221.02','应交税费,应交增值税-进项税额','1','0',NULL,'0',0,NOW()),
-- 权益类
(6005030,'000000','4001','实收资本','EQUITY','CREDIT',0,1,'0,4001','实收资本','1','0',NULL,'0',0,NOW()),
(6005031,'000000','4103','本年利润','EQUITY','CREDIT',0,1,'0,4103','本年利润','1','0',NULL,'0',0,NOW()),
-- 成本类
(6005040,'000000','5001','生产成本','COST','DEBIT',0,1,'0,5001','生产成本','1','0',NULL,'0',0,NOW()),
(6005041,'000000','5101','制造费用','COST','DEBIT',0,1,'0,5101','制造费用','1','0',NULL,'0',0,NOW()),
-- 损益类
(6005050,'000000','6001','主营业务收入','INCOME','CREDIT',0,1,'0,6001','主营业务收入','1','0',NULL,'0',0,NOW()),
(6005051,'000000','6401','主营业务成本','EXPENSE','DEBIT',0,1,'0,6401','主营业务成本','1','0',NULL,'0',0,NOW()),
(6005052,'000000','6601','销售费用','EXPENSE','DEBIT',0,1,'0,6601','销售费用','1','0',NULL,'0',0,NOW()),
(6005053,'000000','6602','管理费用','EXPENSE','DEBIT',0,1,'0,6602','管理费用','1','0',NULL,'0',0,NOW());
```

> 完整 seed 含准则全部一级科目(资产 1001-1699/负债 2001-2901/权益 4001-4101/成本 5001-5401/损益 6001-6901),约 80 条。`account_id` 用 `6005xxx` 段(与字典段对齐,便于识别)。`tenant_id='000000'` 为模板,租户启用财务时复制为本租户科目(或直接共享,由 `tenant_id` 隔离)。

---

## 五、字典与菜单 SQL

### 5.1 字典(`6005xx` 段)

```sql
-- 字典类型(dict_type 段位 600501-600506)
INSERT IGNORE INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,create_dept,create_by,create_time,remark) VALUES
(600501,'000000','科目类型','gl_account_type',NULL,NULL,NOW(),'GL'),
(600502,'000000','余额方向','gl_balance_dir',NULL,NULL,NOW(),'GL'),
(600503,'000000','凭证字','gl_voucher_word',NULL,NULL,NOW(),'GL'),
(600504,'000000','凭证状态','gl_voucher_status',NULL,NULL,NOW(),'GL'),
(600505,'000000','凭证来源','gl_voucher_source',NULL,NULL,NOW(),'GL'),
(600506,'000000','期间状态','gl_period_status',NULL,NULL,NOW(),'GL');

-- 字典数据(dict_code 段位 600511+)
INSERT IGNORE INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,is_default,create_time) VALUES
(600511,'000000',1,'资产','ASSET','gl_account_type','N',NOW()),
(600512,'000000',2,'负债','LIABILITY','gl_account_type','N',NOW()),
(600513,'000000',3,'权益','EQUITY','gl_account_type','N',NOW()),
(600514,'000000',4,'成本','COST','gl_account_type','N',NOW()),
(600515,'000000',5,'收入','INCOME','gl_account_type','N',NOW()),
(600516,'000000',6,'费用','EXPENSE','gl_account_type','N',NOW()),
(600521,'000000',1,'借','DEBIT','gl_balance_dir','Y',NOW()),
(600522,'000000',2,'贷','CREDIT','gl_balance_dir','N',NOW()),
(600531,'000000',1,'记','J','gl_voucher_word','Y',NOW()),
(600532,'000000',2,'收','R','gl_voucher_word','N',NOW()),
(600533,'000000',3,'付','P','gl_voucher_word','N',NOW()),
(600534,'000000',4,'转','T','gl_voucher_word','N',NOW()),
(600541,'000000',1,'草稿','DRAFT','gl_voucher_status','Y',NOW()),
(600542,'000000',2,'已过账','POSTED','gl_voucher_status','N',NOW()),
(600543,'000000',3,'已作废','CANCELLED','gl_voucher_status','N',NOW()),
(600551,'000000',1,'手工','MANUAL','gl_voucher_source','Y',NOW()),
(600552,'000000',2,'销售发票','SO_INVOICE','gl_voucher_source','N',NOW()),
(600553,'000000',3,'采购发票','PU_INVOICE','gl_voucher_source','N',NOW()),
(600554,'000000',4,'收付款','RECEIPT','gl_voucher_source','N',NOW()),
(600555,'000000',5,'折旧','DEPRECIATION','gl_voucher_source','N',NOW()),
(600556,'000000',6,'成本','COST','gl_voucher_source','N',NOW()),
(600561,'000000',1,'开放','OPEN','gl_period_status','Y',NOW()),
(600562,'000000',2,'已关账','CLOSED','gl_period_status','N',NOW()),
(600563,'000000',3,'已锁定','LOCKED','gl_period_status','N',NOW());
```

> 执行后若 Redis 已缓存旧字典,需 `DEL sys_dict:gl_*`(参考 [[mes-p0-overall-progress]] 字典 stale 坑)。

### 5.2 菜单(`2600-2620` 段)

```sql
-- 财务管理父菜单(menu_id=2600,parent_id 指向 ERP 顶级菜单,实施时 grep sys_menu 确认既有点位)
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) VALUES
(2600,'财务管理',0,60,'fin',NULL,1,0,'M','0','0',NULL,'money',NOW()),            -- 目录
(2601,'总账',2600,1,'gl',NULL,1,0,'M','0','0',NULL,'ledger',NOW()),              -- 总账目录
(2602,'科目表',2601,1,'account','erp/fin/gl/account/index',1,0,'C','0','0','fin:gl:account:list','tree-table',NOW()),
(2603,'记账凭证',2601,2,'voucher','erp/fin/gl/voucher/index',1,0,'C','0','0','fin:gl:voucher:list','edit',NOW()),
(2604,'会计期间',2601,3,'period','erp/fin/gl/period/index',1,0,'C','0','0','fin:gl:period:list','date',NOW()),
(2605,'币种汇率',2601,4,'currency-rate','erp/fin/gl/currencyRate/index',1,0,'C','0','0','fin:gl:currency:list','rate',NOW()),
(2606,'试算平衡表',2601,5,'trial-balance','erp/fin/gl/report/trialBalance',1,0,'C','0','0','fin:gl:report:list','chart',NOW());

-- 科目表按钮
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) VALUES
(2607,'科目查询',2602,1,'#','',1,0,'F','0','0','fin:gl:account:query',NULL,NOW()),
(2608,'科目新增',2602,2,'#','',1,0,'F','0','0','fin:gl:account:add',NULL,NOW()),
(2609,'科目修改',2602,3,'#','',1,0,'F','0','0','fin:gl:account:edit',NULL,NOW()),
(2610,'科目删除',2602,4,'#','',1,0,'F','0','0','fin:gl:account:remove',NULL,NOW());

-- 凭证按钮
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) VALUES
(2611,'凭证查询',2603,1,'#','',1,0,'F','0','0','fin:gl:voucher:query',NULL,NOW()),
(2612,'凭证新增',2603,2,'#','',1,0,'F','0','0','fin:gl:voucher:add',NULL,NOW()),
(2613,'凭证修改',2603,3,'#','',1,0,'F','0','0','fin:gl:voucher:edit',NULL,NOW()),
(2614,'凭证删除',2603,4,'#','',1,0,'F','0','0','fin:gl:voucher:remove',NULL,NOW()),
(2615,'凭证过账',2603,5,'#','',1,0,'F','0','0','fin:gl:voucher:post',NULL,NOW()),
(2616,'凭证反过账',2603,6,'#','',1,0,'F','0','0','fin:gl:voucher:unpost',NULL,NOW());

-- 期间按钮
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_time) VALUES
(2617,'期间维护',2604,1,'#','',1,0,'F','0','0','fin:gl:period:edit',NULL,NOW());
```

> `menu_id` 硬编码 `2600-2620`(避让 25xx)。`parent_id=0` 的 2600 财务管理目录实施时挂到 ERP 顶级菜单下(grep `sys_menu` 确认顶级 `menu_id`)。角色授权后前端可见。

---

## 六、领域层(实体)

### 6.1 GlAccount

```java
package com.nexus.erp.fin.gl.domain;

@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
@TableName("gl_account")
public class GlAccount extends TenantEntity {
    @TableId(value = "account_id")
    private Long accountId;
    private String accountCode;     // 科目编码
    private String accountName;
    private String accountType;     // ASSET/LIABILITY/EQUITY/COST/INCOME/EXPENSE
    private String balanceDir;      // DEBIT/CREDIT
    private Long parentId;
    private Integer levelNum;
    private String pathIds;
    private String pathNames;
    private String isLeaf;          // 0/1
    private String isRecon;         // 0/1
    private String reconType;       // AR/AP/FA
    private String isOpenItem;      // 0/1
    private String currency;
    private String status;          // 0/1
    private Integer sortOrder;
    @Version private Integer version;
    @TableLogic private String delFlag;
}
```

### 6.2 GlVoucher / GlVoucherLine

```java
@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
@TableName("gl_voucher")
public class GlVoucher extends TenantEntity {
    @TableId(value = "voucher_id")
    private Long voucherId;
    private String voucherNo;
    private String voucherWord;     // J/R/P/T
    private Date voucherDate;
    private Long periodId;
    private String voucherSource;   // MANUAL/SO_INVOICE/...
    private Long sourceId;
    private String sourceNo;
    private Integer attachmentCount;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String currency;
    private String status;          // DRAFT/POSTED/CANCELLED
    private Long postedBy;
    private Date postedTime;
    @Version private Integer version;
    private String remark;
    @TableLogic private String delFlag;
    @TableField(exist = false)
    private List<GlVoucherLine> voucherLineList;
}

@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
@TableName("gl_voucher_line")
public class GlVoucherLine extends TenantEntity {
    @TableId(value = "line_id")
    private Long lineId;
    private Long voucherId;
    private Integer lineNo;
    private Long accountId;
    private String accountCode;     // 冗余
    private String accountName;     // 冗余
    private String summary;
    private BigDecimal debit;
    private BigDecimal credit;
    private String currency;        // 原币
    private BigDecimal amountCurrency;
    private BigDecimal exchangeRate;
    private Long partnerId;
    private String partnerType;     // CLIENT/VENDOR
    private String openItem;        // 0/1
    private String cleared;         // 0/1
    private Long clearedBy;
    private Integer sortOrder;
    private String remark;
    @TableLogic private String delFlag;
}
```

### 6.3 GlPeriod / GlCurrencyRate / GlAccountBalance

字段对应 §3.4/3.5/3.6 DDL,均 `extends TenantEntity`+`@TableId`+`@Version`+`@TableLogic`。

---

## 七、Mapper 层

```java
// ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/gl/mapper/
public interface GlAccountMapper extends BaseMapperPlus<GlAccount, GlAccountVo> {}
public interface GlVoucherMapper extends BaseMapperPlus<GlVoucher, GlVoucherVo> {}
public interface GlVoucherLineMapper extends BaseMapperPlus<GlVoucherLine, GlVoucherLine> {}
public interface GlPeriodMapper extends BaseMapperPlus<GlPeriod, GlPeriodVo> {}
public interface GlCurrencyRateMapper extends BaseMapperPlus<GlCurrencyRate, GlCurrencyRateVo> {}
public interface GlAccountBalanceMapper extends BaseMapperPlus<GlAccountBalance, GlAccountBalanceVo> {
    /** 试算平衡表:按期间汇总科目借贷(自定义 XML) */
    List<TrialBalanceVo> selectTrialBalance(@Param("periodId") Long periodId, @Param("accountType") String accountType);
}
```

> `selectTrialBalance` 走 `GlAccountBalanceMapper.xml`,SUM 余额 + 可选 JOIN `gl_voucher_line` 校验发生额。

---

## 八、Service 层

### 8.1 GlAccountService(科目树 CRUD)

```java
public interface IGlAccountService {
    List<GlAccountVo> queryList(GlAccountBo bo);                  // 平铺列表(带条件)
    List<Tree<Long>> buildAccountTree(List<GlAccountVo> list);    // 树形(前端 tree-select)
    GlAccountVo queryById(Long accountId);
    Boolean insertByBo(GlAccountBo bo);
    Boolean updateByBo(GlAccountBo bo);
    Boolean deleteByIds(Long[] ids);                              // 仅删末级且无余额
    List<GlAccountVo> selectLeafAccounts(String accountType);     // 末级科目(凭证录入下拉)
}
```

**关键逻辑**:
- `insertByBo`/`updateByBo`:调 `fillPathAndLevel(entity)` 计算 `path_ids`/`path_names`/`level_num`(参照 `BdMaterialCategoryServiceImpl`);父级变更时 `updateChildrenPath()` 前缀替换子孙路径。
- 新增子科目时,父科目 `is_leaf` 置 `0`(变为非末级)。
- `deleteByIds`:校验科目为末级、无子科目、无凭证引用、`gl_account_balance` 无余额,否则拒绝。

### 8.2 GlVoucherService(凭证引擎,核心)

```java
public interface IGlVoucherService {
    TableDataInfo<GlVoucherVo> queryPageList(GlVoucherBo bo, PageQuery pageQuery);
    GlVoucherVo queryById(Long voucherId);
    Boolean insertByBo(GlVoucherBo bo);            // 草稿,校验借贷平衡
    Boolean updateByBo(GlVoucherBo bo);            // 差量更新+乐观锁(仅 DRAFT)
    Boolean deleteByIds(Long[] ids);               // 仅 DRAFT
    void post(Long voucherId);                     // 过账:校验平衡+期间开放+末级科目+写余额
    void unpost(Long voucherId);                   // 反过账:期间未关+回滚余额
    void cancel(Long voucherId);                   // 作废
    GlVoucher createAndPost(VoucherBuildRequest req); // 供 AccountingPoster 调用(自动制单)
}
```

**状态机常量**:
```java
String STATUS_DRAFT = "DRAFT";
String STATUS_POSTED = "POSTED";
String STATUS_CANCELLED = "CANCELLED";
```

**关键方法实现**:

```java
@DS("erp")
@RequiredArgsConstructor
public class GlVoucherServiceImpl extends BaseServiceImpl<GlVoucherMapper, GlVoucher> implements IGlVoucherService {

    /** 借贷平衡校验(BigDecimal,零容差) */
    private void validateBalanced(List<GlVoucherLine> lines) {
        if (CollUtil.isEmpty(lines)) throw new ServiceException("凭证至少一行分录");
        BigDecimal dr = lines.stream().map(l -> Optional.ofNullable(l.getDebit()).orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(l -> Optional.ofNullable(l.getCredit()).orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (dr.compareTo(cr) != 0) {
            throw new ServiceException("借贷不平衡:借方{} 贷方{}", dr, cr);
        }
        // 每行 debit/credit 不可同时有值,且须末级科目
        for (GlVoucherLine l : lines) {
            GlAccount acc = accountMapper.selectById(l.getAccountId());
            if (acc == null || !"1".equals(acc.getIsLeaf())) {
                throw new ServiceException("科目{}非末级,不可过账", l.getAccountCode());
            }
        }
    }

    /** 过账 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void post(Long voucherId) {
        GlVoucher v = baseMapper.selectById(voucherId);
        if (v == null) throw new ServiceException("凭证不存在");
        if (!STATUS_DRAFT.equals(v.getStatus())) throw new ServiceException("仅草稿可过账,当前【{}】", v.getStatus());
        List<GlVoucherLine> lines = lineMapper.selectList(
            Wrappers.<GlVoucherLine>lambdaQuery().eq(GlVoucherLine::getVoucherId, voucherId));
        validateBalanced(lines);
        // 期间校验
        GlPeriod period = periodService.getOrDerivePeriod(v.getVoucherDate());
        if (!"OPEN".equals(period.getStatus())) {
            throw new ServiceException("期间【{}】非开放状态,不可过账", period.getPeriodName());
        }
        // 写合计
        BigDecimal dr = lines.stream().map(GlVoucherLine::getDebit)...sum;
        // 头更新:status->POSTED + posted_by/time + period_id + totals + 乐观锁
        int rows = baseMapper.update(null,
            Wrappers.<GlVoucher>lambdaUpdate()
                .eq(GlVoucher::getVoucherId, voucherId)
                .eq(GlVoucher::getVersion, v.getVersion())
                .set(GlVoucher::getStatus, STATUS_POSTED)
                .set(GlVoucher::getPeriodId, period.getPeriodId())
                .set(GlVoucher::getTotalDebit, dr)
                .set(GlVoucher::getTotalCredit, cr)
                .set(GlVoucher::getPostedBy, LoginHelper.getUserId())
                .set(GlVoucher::getPostedTime, new Date())
                .set(GlVoucher::getVersion, v.getVersion() + 1));
        if (rows == 0) throw new ServiceException("凭证已变更,过账失败,请重试");
        // 更新科目余额(原子累加 period_debit/period_credit + 重算 closing)
        balanceService.applyPosting(period.getPeriodId(), lines);
    }

    /** 反过账 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpost(Long voucherId) {
        GlVoucher v = baseMapper.selectById(voucherId);
        if (!STATUS_POSTED.equals(v.getStatus())) throw new ServiceException("仅已过账可反过账");
        GlPeriod period = periodMapper.selectById(v.getPeriodId());
        if ("LOCKED".equals(period.getStatus())) throw new ServiceException("期间已锁定,不可反过账");
        // @SaCheckPermission("fin:gl:voucher:unpost") 在 Controller 层
        balanceService.reversePosting(v.getPeriodId(), ...);  // 回滚余额
        // 头 status->DRAFT(保留 posted 信息清空可选)
        baseMapper.update(null, Wrappers.<GlVoucher>lambdaUpdate()
            .eq(GlVoucher::getVoucherId, voucherId).eq(GlVoucher::getVersion, v.getVersion())
            .set(GlVoucher::getStatus, STATUS_DRAFT)
            .set(GlVoucher::getVersion, v.getVersion() + 1));
    }

    /** 差量更新(仅 DRAFT) */
    @Override
    public Boolean updateByBo(GlVoucherBo bo) {
        GlVoucher existing = baseMapper.selectById(bo.getVoucherId());
        if (!STATUS_DRAFT.equals(existing.getStatus())) throw new ServiceException("仅草稿可修改");
        // 显式拷贝可编辑字段,不覆盖 version/status/posted_*
        existing.setVoucherWord(bo.getVoucherWord());
        existing.setVoucherDate(bo.getVoucherDate());
        // ...
        int rows = baseMapper.updateById(existing);
        if (rows == 0) throw new ServiceException("凭证已变更,修改失败,请重试");
        if (bo.getVoucherLineList() != null) diffUpdateLines(existing.getVoucherId(), bo.getVoucherLineList());
        return true;
    }

    private void diffUpdateLines(Long voucherId, List<GlVoucherLine> newLines) {
        // 同 SoInvoiceServiceImpl.diffUpdateLines:按 lineId 新增/更新/删除
        validateBalanced(newLines);  // 修改后仍须平衡
        // ...
    }

    /** 凭证号生成 */
    private String genVoucherNo(String voucherWord) {
        try {
            return autoCodeUtil.genSerialCode("GL_VOUCHER_CODE", voucherWord);  // PZ+YYYYMMDD+seq
        } catch (Exception e) {
            // 规则未配置时回退雪花(同 so_invoice 模式,保证唯一)
            return "PZ" + IdWorker.getId();
        }
    }
}
```

> **凭证号 AutoCode 规则**:`GL_VOUCHER_CODE`(PZ + 日期 + 4 位流水),参照 `sys_auto_code_rule` 中 `PU_ORDER_CODE` 模式新增规则配置。`genSerialCode` 走 master 库 `@Transactional(REQUIRES_NEW)`。规则未配置时回退 `IdWorker`(同 so_invoice 发票号策略,避免硬依赖)。**三处对齐**(见 [[autocode-form-pattern]]):前端表单 `voucherNo` 清空不填、BO `voucherNo` 去 `@NotBlank`、Service `insertByBo` 调 `genVoucherNo`。

### 8.3 GlPeriodService(期间)

```java
public interface IGlPeriodService {
    TableDataInfo<GlPeriodVo> queryPageList(GlPeriodBo bo, PageQuery pageQuery);
    GlPeriod getOrDerivePeriod(Date date);   // 按 date 查期间;不存在则按年月派生(OPEN)
    Boolean closePeriod(Long periodId);      // OPEN->CLOSED(校验该期间凭证已审核)
    Boolean lockPeriod(Long periodId);       // CLOSED->LOCKED(不可逆,需授权)
    Boolean reopenPeriod(Long periodId);     // CLOSED->OPEN(反结账,需授权)
    Boolean generateYearPeriods(Integer fiscalYear); // 批量生成某年 12 期间
}
```

**关键逻辑**:
- `getOrDerivePeriod(date)`:`fiscal_year=year(date)` + `period_no=month(date)`,查不到则自动建 `OPEN` 期间(或抛"期间不存在,请先生成")。
- `closePeriod`:校验该期间无 `DRAFT` 凭证(草稿须先过账或作废),否则拒绝。
- 年结(F5)将 `closing` 结转为下年 `opening`。

### 8.4 GlCurrencyRateService

简单 CRUD:按 `currency`+`rate_date`+`rate_type` 唯一约束维护。`getRate(currency, date)` 取当日或最近汇率,供凭证录入默认。

### 8.5 GlAccountBalanceService(余额)

```java
public interface IGlAccountBalanceService {
    /** 过账时:原子累加 period_debit/period_credit + 重算 closing */
    void applyPosting(Long periodId, List<GlVoucherLine> lines);
    /** 反过账时:原子扣减 */
    void reversePosting(Long periodId, List<GlVoucherLine> lines);
    /** 期末结转:某期间 closing -> 下期 opening(F5 调用) */
    void carryForward(Long fromPeriodId, Long toPeriodId);
    /** 试算平衡表数据 */
    List<TrialBalanceVo> trialBalance(Long periodId, String accountType);
}
```

**`applyPosting` 关键逻辑**(原子累加 + 乐观锁,参考 `verifyInvoice` 模式):
```java
public void applyPosting(Long periodId, List<GlVoucherLine> lines) {
    // 按 account_id 分组求和 debit/credit
    Map<Long, BigDecimal[]> agg = ...; // account_id -> [dr, cr]
    for (Map.Entry<Long, BigDecimal[]> e : agg.entrySet()) {
        Long accountId = e.getKey();
        BigDecimal dr = e.getValue()[0], cr = e.getValue()[1];
        GlAccountBalance bal = baseMapper.selectOne(Wrappers.<GlAccountBalance>lambdaQuery()
            .eq(GlAccountBalance::getPeriodId, periodId).eq(GlAccountBalance::getAccountId, accountId));
        if (bal == null) {
            bal = new GlAccountBalance();
            bal.setPeriodId(periodId); bal.setAccountId(accountId);
            bal.setVersion(0); // 初始化(参考库存乐观锁坑)
            baseMapper.insert(bal);
        }
        // 原子累加 period_debit/period_credit,closing = opening + period
        baseMapper.update(null, Wrappers.<GlAccountBalance>lambdaUpdate()
            .eq(GlAccountBalance::getBalanceId, bal.getBalanceId())
            .eq(GlAccountBalance::getVersion, bal.getVersion())
            .setSql("period_debit = period_debit + " + dr.toPlainString())
            .setSql("period_credit = period_credit + " + cr.toPlainString())
            // closing 按 balance_dir 重算(借方科目 closing_debit = opening_debit + period_debit - period_credit)
            .setSql("closing_debit = ...")
            .setSql("closing_credit = ...")
            .set(GlAccountBalance::getVersion, bal.getVersion() + 1));
    }
}
```

> **并发**:同一期间同一科目多凭证并发过账,`setSql` 原子累加 + `eq(version)` 手动乐观锁;失败重试(参考发票核销模式)。`version` 初始化为 0(见 [[wm-stock-version-optimistic-lock]])。

### 8.6 AccountingPoster SPI(业务自动制单扩展点)

```java
package com.nexus.erp.fin.gl.poster;

/** 业务自动制单扩展点。F0 定义,F1+ 各业务实现。 */
public interface AccountingPoster {
    /** 来源类型,对应 gl_voucher.voucher_source */
    String sourceType();
    /** 根据来源单据构建并过账凭证 */
    GlVoucher post(PostingContext ctx);
}

public class PostingContext {
    private String sourceType;   // SO_INVOICE/PU_INVOICE/RECEIPT/DEPRECIATION/COST
    private Long sourceId;       // 来源单据ID
    private String sourceNo;     // 来源单据号
    private Long tenantId;       // 租户(异步消费时恢复)
    private Date voucherDate;    // 凭证日期(默认来源单据日期)
    // F1+ 扩展:行模板、partner 等
}

/** Poster 注册表:Spring 注入所有 AccountingPoster,按 sourceType 路由 */
@Component
@RequiredArgsConstructor
public class AccountingPosterRegistry {
    private final List<AccountingPoster> posters;
    private Map<String, AccountingPoster> map;

    @PostConstruct
    void init() { map = posters.stream().collect(toMap(AccountingPoster::sourceType, p -> p)); }

    public AccountingPoster get(String sourceType) {
        return Optional.ofNullable(map.get(sourceType))
            .orElseThrow(() -> new ServiceException("无【{}】类型的记账 Poster", sourceType));
    }
}
```

**F0 仅提供 SPI + Registry + Consumer,不提供业务 Poster**(业务 Poster 在 F1+ 实现:`SoInvoicePoster`/`PuInvoicePoster`/`ReceiptPoster`/`DepreciationPoster`/`CostPoster`)。手工凭证走 `GlVoucherController` -> `GlVoucherService` 直接录入,不经 Poster。

### 8.7 GlPostingConsumer(MQ 异步制单)

```java
package com.nexus.erp.fin.gl.mq;

@Slf4j @Component @RequiredArgsConstructor
public class GlPostingConsumer {
    private final AccountingPosterRegistry registry;
    private final IGlVoucherService voucherService;

    @RabbitListener(queues = QueueConstants.GL_POSTING_QUEUE)
    public void onPosting(GlPostingMessage msg) {
        // 跨库/异步:租户上下文恢复(参考 StockInConsumer:76-88)
        TenantHelper.dynamic(msg.getTenantId(), () -> {
            AccountingPoster poster = registry.get(msg.getSourceType());
            poster.post(PostingContext.builder()
                .sourceType(msg.getSourceType())
                .sourceId(msg.getSourceId())
                .sourceNo(msg.getSourceNo())
                .tenantId(msg.getTenantId())
                .voucherDate(msg.getVoucherDate())
                .build());
        });
    }
}
```

> **幂等**:业务侧发 MQ 前先查 `gl_voucher` 是否已存在 `(voucher_source, source_id)` 记录,已存在则不发(或 Poster 内 `createAndPost` 前查重)。消息体 `tenantId` 用字符串(避免雪花 ID 精度丢失)。

---

## 九、Controller 层

```java
// /erp/fin/gl/account
@Validated @RequiredArgsConstructor @RestController
@RequestMapping("/erp/fin/gl/account")
public class GlAccountController extends BaseController {
    @SaCheckPermission("fin:gl:account:list")
    @GetMapping("/list")
    public TableDataInfo<GlAccountVo> list(GlAccountBo bo) { ... }
    @SaCheckPermission("fin:gl:account:query")
    @GetMapping("/{accountId}")
    public R<GlAccountVo> getInfo(@PathVariable Long accountId) { ... }
    @SaCheckPermission("fin:gl:account:add")
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody GlAccountBo bo) { ... }
    @SaCheckPermission("fin:gl:account:edit")
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody GlAccountBo bo) { ... }
    @SaCheckPermission("fin:gl:account:remove")
    @DeleteMapping("/{accountIds}")
    public R<Void> remove(@PathVariable Long[] accountIds) { ... }
    @GetMapping("/tree")
    public R<List<Tree<Long>>> tree() { ... }              // 科目树(tree-select)
    @GetMapping("/leaf")
    public R<List<GlAccountVo>> leaf(@RequestParam(required=false) String accountType) { ... }
}

// /erp/fin/gl/voucher
@Validated @RequiredArgsConstructor @RestController
@RequestMapping("/erp/fin/gl/voucher")
public class GlVoucherController extends BaseController {
    // 标准 CRUD + 以下状态动作
    @SaCheckPermission("fin:gl:voucher:post")
    @PutMapping("/post/{voucherId}")
    public R<Void> post(@PathVariable Long voucherId) { voucherService.post(voucherId); return toAjax(1); }
    @SaCheckPermission("fin:gl:voucher:unpost")
    @PutMapping("/unpost/{voucherId}")
    public R<Void> unpost(@PathVariable Long voucherId) { voucherService.unpost(voucherId); return toAjax(1); }
    @SaCheckPermission("fin:gl:voucher:edit")
    @PutMapping("/cancel/{voucherId}")
    public R<Void> cancel(@PathVariable Long voucherId) { voucherService.cancel(voucherId); return toAjax(1); }
}

// /erp/fin/gl/period  (CRUD + close/lock/reopen/generateYearPeriods)
// /erp/fin/gl/currency (CRUD)
```

---

## 十、试算平衡表

```java
@Validated @RequiredArgsConstructor @RestController
@DS("erp")
@RequestMapping("/erp/fin/gl/report")
public class GlReportController {
    private final IGlAccountBalanceService balanceService;

    @SaCheckPermission("fin:gl:report:list")
    @GetMapping("/trialBalance")
    public R<List<TrialBalanceVo>> trialBalance(
            @RequestParam Long periodId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false, defaultValue = "false") Boolean includeZero) {
        return R.ok(balanceService.trialBalance(periodId, accountType));
    }
}
```

**`TrialBalanceVo`**:accountId/accountCode/accountName/accountType/balanceDir/openingDebit/openingCredit/periodDebit/periodCredit/closingDebit/closingCredit。

**数据源**:`gl_account_balance`(期初/期末)+ 凭证行 SUM(本期发生,校验用)。按 `account_type` 过滤、`includeZero` 控制是否显示零余额科目。汇总行:合计借贷(须相等)。

---

## 十一、MQ 设计

`QueueConstants` 追加:

```java
/** GL 过账队列(业务单据事件 -> AccountingPoster 异步制单) */
String GL_POSTING_QUEUE = "erp.gl.posting.queue";
/** 支付收款通知队列(pay -> ERP,跨库,F1 用) */
String PAY_RECEIVED_QUEUE = "erp.pay.received.queue";
/** 电票结果队列(F7 用) */
String EINVOICE_ISSUE_RESULT_QUEUE = "erp.einvoice.issue.result.queue";
```

**消息体 `GlPostingMessage`**:`sourceType`/`sourceId`/`sourceNo`/`tenantId`/`voucherDate`。生产者:各业务 Service 在单据状态变更后(事务提交后)发 MQ(参考 `SalesOrderFulfilledEventListener` 用 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("financialExecutor")`)。消费者:`GlPostingConsumer`,`TenantHelper.dynamic` 恢复租户。

> F0 阶段仅落 `GL_POSTING_QUEUE` 常量 + `GlPostingConsumer` + `AccountingPosterRegistry`,无生产者(无业务 Poster)。F1 起各业务 Service 成为生产者。

---

## 十二、前端(plus-ui)

### 12.1 页面

```
src/views/erp/fin/gl/
  account/index.vue        - 科目表(tree-table,新增/编辑/删除,树形展开)
  account/form.vue         - 科目表单(父科目 tree-select,类型/方向/统驭/末级)
  voucher/index.vue        - 凭证列表(状态筛选,过账/反过账/作废按钮)
  voucher/form.vue         - 凭证录入(头 + 行编辑器,科目下拉/摘要/借贷,实时校验借贷平衡)
  period/index.vue         - 会计期间(年/月,关账/锁定/重开,生成年度期间)
  currencyRate/index.vue   - 汇率维护
  report/trialBalance.vue  - 试算平衡表(期间选择,科目层级,合计校验)
```

### 12.2 API

```
src/api/erp/fin/gl/
  account/{index,types}.ts
  voucher/{index,types}.ts
  period/{index,types}.ts
  currencyRate/{index,types}.ts
  report.ts
```

### 12.3 关键交互

- **凭证录入借贷平衡**:行编辑器 `debit`/`credit` 互斥(一行只能填一个),底部实时显示"借方合计 / 贷方合计 / 差额",差额≠0 时禁用保存。
- **科目下拉**:`/erp/fin/gl/account/leaf` 拉末级科目,带 `accountCode-accountName` 展示。
- **字典化**:`gl_account_type`/`gl_voucher_word`/`gl_voucher_status`/`gl_period_status` 用 `useDict`+`dict-tag`。
- **过账按钮按状态显隐**:仅 `DRAFT` 显示过账、仅 `POSTED` 显示反过账。

---

## 十三、关键技术决议

| # | 决议 | 理由 |
|---|---|---|
| 1 | 科目表用**物化路径**(`path_ids`+`path_names`+`level_num`) | 参照 BdMaterialCategory,子树查询/路径展示高效,父级变更前缀替换 |
| 2 | **仅末级科目可过账** | 非末级为汇总科目,凭证引用末级,余额按末级累加、按层级汇总 |
| 3 | **借贷平衡强校验**(BigDecimal 零容差) | 复式记账铁律,insert/update/post 均校验 |
| 4 | 过账状态机 `DRAFT->POSTED->(反过账)DRAFT`,反过账需期间未锁+权限 | 期间锁定后不可反过账,保证已结账数据稳定 |
| 5 | **期间受控**:过账校验 `voucher_date` 落在 `OPEN` 期间 | 借鉴 SAP/用友期间控制,月结锁定 |
| 6 | 科目余额 `setSql` 原子累加 + 手动乐观锁 | 并发过账不覆盖,参考发票核销 `verifyInvoice` |
| 7 | **AccountingPoster SPI + Registry** | 业务自动制单扩展点,F1+ 各业务实现 Poster,F0 零业务耦合 |
| 8 | 凭证号 AutoCode(`GL_VOUCHER_CODE`)+ 回退 `IdWorker` | 顺序化优先,规则未配置回退雪花(同 so_invoice 策略,三处对齐) |
| 9 | 业务过账走 MQ 异步(`GL_POSTING_QUEUE`+`financialExecutor`) | 解耦业务单据与记账,事务提交后制单,租户上下文恢复 |
| 10 | 多币种本币+原币双存储(`debit`/`credit` 本币 + `amount_currency`+`exchange_rate`) | 外币凭证留原币,期末重估(F5)基础 |
| 11 | `gl_account_balance` 表存期初/发生/期末 | 试算平衡与三大报表(F5)的基座,避免每次全量重算凭证 |
| 12 | 统驭科目 `is_recon`+`recon_type` 标识 | F1 AR/AP/F2 FA 子账通过统驭科目汇总到 GL |

---

## 十四、风险与坑

| 风险/坑 | 应对 |
|---|---|
| **借贷平衡 BigDecimal 比较** | 用 `compareTo` 不用 `equals`(1.0 vs 1.00),零容差 |
| **乐观锁 version 未初始化** | `gl_account_balance` 新建 `setVersion(0)`(见 [[wm-stock-version-optimistic-lock]]) |
| **异步租户上下文丢失** | `GlPostingConsumer` 必须 `TenantHelper.dynamic(msg.getTenantId(), ...)`(见 [[ai-async-tenant-context]]) |
| **MQ 重复制单** | Poster `createAndPost` 前查 `(voucher_source, source_id)` 去重;业务侧发 MQ 前查重 |
| **凭证号 AutoCode 规则未配置** | `genVoucherNo` try-catch 回退 `IdWorker`,不阻塞录入(三处对齐见 [[autocode-form-pattern]]) |
| **科目树父级变更波及子孙** | `updateChildrenPath` 前缀替换,事务内完成,校验无环 |
| **期间关闭遗漏草稿凭证** | `closePeriod` 校验该期间无 DRAFT 凭证,否则拒绝关账 |
| **末级科目变非末级后存量凭证** | 科目已有凭证引用时,禁止将其设为非末级(或允许,仅影响新凭证) |
| **字典 Redis stale** | 字典 SQL 执行后 `DEL sys_dict:gl_*`(见 [[mes-p0-overall-progress]]) |
| **菜单 parent_id 锚点** | 2600 财务管理目录 `parent_id` 实施时 grep `sys_menu` 确认 ERP 顶级菜单 ID |
| **跨库** | GL 表全在 ry-erp(`@DS("erp")`);pay 跨库走 MQ(F1),不跨库 JOIN |
| **雪花 ID 前端精度** | 新接口返回雪花 ID 已由全局 `BigNumberSerializer` 序列化为字符串,勿绕过(见 [[js-long-precision-snowflake]]) |

---

## 十五、验收标准与调用序列

### 15.1 验收标准

1. **科目表**:树形维护;新增子科目父级变非末级;物化路径正确;仅末级可选作凭证科目;删除校验(末级+无余额+无引用)。
2. **凭证**:手工录入多行分录,借贷不平衡被拒;草稿可改可删;过账后状态 POSTED、写合计、更新科目余额;反过账回滚余额(期间未锁);作废不可逆。
3. **期间**:按年生成 12 期间;过账时 `voucher_date` 落 CLOSED/LOCKED 期间被拒;关账校验无草稿凭证。
4. **多币种**:外币凭证行同时存本币(debit/credit)+原币(amount_currency)+汇率。
5. **试算平衡表**:按期间汇总,期初+本期发生=期末,借贷合计相等。
6. **AccountingPoster SPI**:`AccountingPosterRegistry` 注入 F0 后的 Poster(无业务 Poster 时为空);`GlPostingConsumer` 收到 MQ 调对应 Poster(F0 用单元测试 mock 验证)。
7. **编译**:`mvn -pl ruoyi-modules/ruoyi-erp -am compile` EXIT 0;前端 `npx eslint` 零新增。

### 15.2 调用序列(手工凭证主链路)

1. `GET /erp/fin/gl/account/leaf` -> 拉末级科目下拉
2. `POST /erp/fin/gl/voucher` -> 录入草稿(头+行,借贷平衡校验通过,voucher_no 自动生成)
3. `PUT /erp/fin/gl/voucher` -> 修改(差量更新+乐观锁)
4. `PUT /erp/fin/gl/voucher/post/{id}` -> 过账(校验平衡+期间开放+末级+写余额,status->POSTED)
5. `GET /erp/fin/gl/report/trialBalance?periodId=...` -> 试算平衡表显示该科目期末余额
6. `PUT /erp/fin/gl/voucher/unpost/{id}` -> 反过账(回滚余额,status->DRAFT)
7. `PUT /erp/fin/gl/period/close/{periodId}` -> 关账(校验无草稿,期间 CLOSED)

### 15.3 调用序列(业务自动制单,F1+ 验证)

> F0 不实现业务 Poster,但 SPI 链路用 mock 验证:发 `GL_POSTING_QUEUE` 消息(sourceType=MANUAL_TEST) -> `GlPostingConsumer` -> `AccountingPosterRegistry.get` -> mock Poster `post` -> 凭证创建并过账。

---

## 十六、文件清单

| 类型 | 文件 |
|---|---|
| SQL | `script/sql/update/2026-07-29-f0-gl-ledger.sql`(6 表 DDL,并入 `script/sql/ry-erp.sql`) |
| SQL | `script/sql/update/2026-07-29-f0-gl-account-seed.sql`(科目表 seed ~80 条) |
| SQL | `script/sql/update/2026-07-29-f0-gl-dict-menu.sql`(字典 6005xx + 菜单 2600-2620,并入 `script/sql/ry-vue.sql`) |
| SQL | `script/sql/update/2026-07-29-f0-gl-voucher-autocode.sql`(`GL_VOUCHER_CODE` 规则,参照 PU_ORDER_CODE) |
| 实体 | `fin/gl/domain/`:`GlAccount`/`GlVoucher`/`GlVoucherLine`/`GlPeriod`/`GlCurrencyRate`/`GlAccountBalance` |
| BO | `fin/gl/domain/bo/`:6 个 Bo(+ `GlVoucherBo` 含 `voucherLineList`) |
| VO | `fin/gl/domain/vo/`:6 个 Vo + `TrialBalanceVo` |
| Mapper | `fin/gl/mapper/`:6 个 Mapper + `GlAccountBalanceMapper.xml`(试算平衡 SQL) |
| Service | `fin/gl/service/`:`IGlAccountService`/`IGlVoucherService`/`IGlPeriodService`/`IGlCurrencyRateService`/`IGlAccountBalanceService` + impl |
| Poster | `fin/gl/poster/`:`AccountingPoster`/`PostingContext`/`AccountingPosterRegistry` |
| MQ | `fin/gl/mq/`:`GlPostingMessage`/`GlPostingConsumer` |
| Controller | `fin/gl/controller/`:`GlAccountController`/`GlVoucherController`/`GlPeriodController`/`GlCurrencyRateController`/`GlReportController` |
| 常量 | `fin/gl/constant/GlConstants.java`(状态常量、统驭类型) |
| MQ 常量 | `QueueConstants` 追加 `GL_POSTING_QUEUE` 等 |
| 前端 | `views/erp/fin/gl/`(7 页面)+ `api/erp/fin/gl/`(5 api) |

---

## 十七、与 F1+ 的衔接(AccountingPoster 扩展点)

| 后续阶段 | 业务 Poster | sourceType | 借贷模板(示例) |
|---|---|---|---|
| F1 | `SoInvoicePoster` | SO_INVOICE | 借:应收账款(统驭 AR) / 贷:主营业务收入 + 销项税额 |
| F1 | `PuInvoicePoster` | PU_INVOICE | 借:原材料/库存商品 + 进项税额 / 贷:应付账款(统驭 AP) |
| F1 | `ReceiptPoster` | RECEIPT | 收款:借银行存款 / 贷应收账款;付款:借应付账款 / 贷银行存款 |
| F2 | `DepreciationPoster` | DEPRECIATION | 借:制造费用/管理费用 / 贷:累计折旧 |
| F3 | `CostPoster` | COST | 借:生产成本 / 贷:原材料(领料);借:库存商品 / 贷:生产成本(完工) |

> 各 Poster 实现 `AccountingPoster`,Spring 自动注入 `AccountingPosterRegistry`,业务 Service 在单据状态变更后发 `GL_POSTING_QUEUE` 消息即可,凭证引擎(`GlVoucherService.createAndPost`)与余额更新(`GlAccountBalanceService`)零改动。

---

## 十八、下一步

1. **评审本 spec**,确认 6 表 DDL、科目表 seed 范围、AccountingPoster SPI、期间状态机
2. 落地 SQL(6 表 + seed + 字典菜单 + AutoCode 规则)到 `script/sql/update/`,执行本地 ry-erp/ry-vue
3. 实现 `fin.gl` 包(实体/BO/VO/Mapper/Service/Controller/Poster/MQ),`mvn -pl ruoyi-erp -am compile` 通过
4. 前端 7 页面 + 5 api,eslint 零新增
5. 按 15.2/15.3 调用序列端到端验证
6. F0 闭合后启动 **F1 AR/AP 核销**(承接发票域 spec P3,实现 SoInvoicePoster/PuInvoicePoster/ReceiptPoster + fin_receipt/fin_settlement + pay 回调 MQ)
