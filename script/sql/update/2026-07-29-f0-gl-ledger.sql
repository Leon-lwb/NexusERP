-- ============================================================
-- F0 GL 总账模块 - 业务表 DDL + 科目表 seed
-- 目标库: ry-erp (业务库)
-- 所属: NexusERP-SPEC-2026-005-F0
-- 日期: 2026-07-29
-- 说明: 6 张表(gl_account/gl_voucher/gl_voucher_line/gl_period/gl_currency_rate/gl_account_balance)
--       + 企业会计准则一级科目 seed(含 AR/AP/FA 统驭科目)
--       权威 schema 并入 script/sql/ry-erp.sql
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. gl_account 会计科目表(物化路径树)
-- ------------------------------------------------------------
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
  `path_ids` varchar(500) NULL DEFAULT NULL COMMENT '路径ID串(如 6005001,6005002)',
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

-- ------------------------------------------------------------
-- 2. gl_voucher 记账凭证头
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `gl_voucher`;
CREATE TABLE `gl_voucher` (
  `voucher_id` bigint(20) NOT NULL COMMENT '凭证ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `voucher_no` varchar(50) NOT NULL COMMENT '凭证号(业务号)',
  `voucher_word` varchar(10) NULL DEFAULT 'J' COMMENT '凭证字 J-记 R-收 P-付 T-转',
  `voucher_date` date NOT NULL COMMENT '凭证日期(决定会计期间)',
  `period_id` bigint(20) NULL DEFAULT NULL COMMENT '会计期间ID(由 voucher_date 推导)',
  `voucher_source` varchar(30) NOT NULL DEFAULT 'MANUAL' COMMENT '来源 MANUAL-手工 SO_INVOICE-销售发票 PU_INVOICE-采购发票 RECEIPT-收付款 DEPRECIATION-折旧 COST-成本 ADJUSTMENT-调整',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(50) NULL DEFAULT NULL COMMENT '来源单据号(冗余)',
  `attachment_count` int(11) NULL DEFAULT 0 COMMENT '附件张数',
  `total_debit` decimal(18,2) NULL DEFAULT 0.00 COMMENT '借方合计',
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

-- ------------------------------------------------------------
-- 3. gl_voucher_line 记账凭证明细行
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `gl_voucher_line`;
CREATE TABLE `gl_voucher_line` (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `voucher_id` bigint(20) NOT NULL COMMENT '凭证ID',
  `line_no` int(11) NULL DEFAULT 0 COMMENT '行号',
  `account_id` bigint(20) NOT NULL COMMENT '科目ID(须末级)',
  `account_code` varchar(50) NULL DEFAULT NULL COMMENT '科目编码(冗余)',
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

-- ------------------------------------------------------------
-- 4. gl_period 会计期间
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 5. gl_currency_rate 币种汇率
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 6. gl_account_balance 科目余额表(试算/报表基座)
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 科目表 seed:企业会计准则一级科目(节选关键 + AR/AP/FA 统驭)
-- account_id 段位 6005xxx(新表无存量,硬编码安全)
-- path_ids 约定:根=自身ID;子=父path_ids+","+自身ID(SEPARATOR=",")
-- is_leaf:1=末级可过账;0=汇总(如 1401 材料采购/2221 应交税费)
-- ------------------------------------------------------------
INSERT IGNORE INTO `gl_account`(`account_id`,`tenant_id`,`account_code`,`account_name`,`account_type`,`balance_dir`,`parent_id`,`level_num`,`path_ids`,`path_names`,`is_leaf`,`is_recon`,`recon_type`,`is_open_item`,`status`,`sort_order`,`version`,`create_time`) VALUES
-- 资产类(余额方向:借)
(6005001,'000000','1001','库存现金','ASSET','DEBIT',0,1,'6005001','库存现金','1','0',NULL,'0','0',1,0,NOW()),
(6005002,'000000','1002','银行存款','ASSET','DEBIT',0,1,'6005002','银行存款','1','0',NULL,'0','0',2,0,NOW()),
(6005003,'000000','1122','应收账款','ASSET','DEBIT',0,1,'6005003','应收账款','1','1','AR','1','0',3,0,NOW()),
(6005004,'000000','1123','预付账款','ASSET','DEBIT',0,1,'6005004','预付账款','1','1','AR','1','0',4,0,NOW()),
(6005005,'000000','1221','其他应收款','ASSET','DEBIT',0,1,'6005005','其他应收款','1','0',NULL,'0','0',5,0,NOW()),
(6005006,'000000','1401','材料采购','ASSET','DEBIT',0,1,'6005006','材料采购','0','0',NULL,'0','0',6,0,NOW()),
(6005007,'000000','1403','原材料','ASSET','DEBIT',0,1,'6005007','原材料','1','0',NULL,'0','0',7,0,NOW()),
(6005008,'000000','1405','库存商品','ASSET','DEBIT',0,1,'6005008','库存商品','1','0',NULL,'0','0',8,0,NOW()),
(6005009,'000000','1601','固定资产','ASSET','DEBIT',0,1,'6005009','固定资产','1','1','FA','0','0',9,0,NOW()),
(6005010,'000000','1602','累计折旧','ASSET','CREDIT',0,1,'6005010','累计折旧','1','0',NULL,'0','0',10,0,NOW()),
-- 负债类(余额方向:贷)
(6005020,'000000','2202','应付账款','LIABILITY','CREDIT',0,1,'6005020','应付账款','1','1','AP','1','0',20,0,NOW()),
(6005021,'000000','2203','预收账款','LIABILITY','CREDIT',0,1,'6005021','预收账款','1','1','AP','1','0',21,0,NOW()),
(6005022,'000000','2211','应付职工薪酬','LIABILITY','CREDIT',0,1,'6005022','应付职工薪酬','1','0',NULL,'0','0',22,0,NOW()),
(6005023,'000000','2221','应交税费','LIABILITY','CREDIT',0,1,'6005023','应交税费','0','0',NULL,'0','0',23,0,NOW()),
(6005024,'000000','2221.01','应交增值税-销项税额','LIABILITY','CREDIT',6005023,2,'6005023,6005024','应交税费,应交增值税-销项税额','1','0',NULL,'0','0',1,0,NOW()),
(6005025,'000000','2221.02','应交增值税-进项税额','LIABILITY','CREDIT',6005023,2,'6005023,6005025','应交税费,应交增值税-进项税额','1','0',NULL,'0','0',2,0,NOW()),
-- 权益类
(6005030,'000000','4001','实收资本','EQUITY','CREDIT',0,1,'6005030','实收资本','1','0',NULL,'0','0',30,0,NOW()),
(6005031,'000000','4103','本年利润','EQUITY','CREDIT',0,1,'6005031','本年利润','1','0',NULL,'0','0',31,0,NOW()),
-- 成本类
(6005040,'000000','5001','生产成本','COST','DEBIT',0,1,'6005040','生产成本','1','0',NULL,'0','0',40,0,NOW()),
(6005041,'000000','5101','制造费用','COST','DEBIT',0,1,'6005041','制造费用','1','0',NULL,'0','0',41,0,NOW()),
-- 损益类
(6005050,'000000','6001','主营业务收入','INCOME','CREDIT',0,1,'6005050','主营业务收入','1','0',NULL,'0','0',50,0,NOW()),
(6005051,'000000','6401','主营业务成本','EXPENSE','DEBIT',0,1,'6005051','主营业务成本','1','0',NULL,'0','0',51,0,NOW()),
(6005052,'000000','6601','销售费用','EXPENSE','DEBIT',0,1,'6005052','销售费用','1','0',NULL,'0','0',52,0,NOW()),
(6005053,'000000','6602','管理费用','EXPENSE','DEBIT',0,1,'6005053','管理费用','1','0',NULL,'0','0',53,0,NOW());

SET FOREIGN_KEY_CHECKS = 1;
