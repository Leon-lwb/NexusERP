-- ============================================================
-- F5 财务报表与期末关账模块 - 业务表 DDL + 科目/映射 seed
-- 目标库: ry-erp (业务库)
-- 所属: NexusERP-SPEC-2026-005-F5
-- 日期: 2026-07-31
-- 说明: report_item(报表项目-科目映射) / fin_close_log(关账日志) 两张新表
--       + gl_period 追加月结字段(INPLACE)
--       + gl_account 追加损益结转/重估/报表所需科目(INSERT IGNORE)
--       + report_item 报表映射 seed(资产负债表/利润表/现金流量表-直接法)
--       权威 schema 并入 script/sql/ry-erp.sql
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. report_item 报表项目-科目映射
--    report_type: BALANCE-资产负债表 INCOME-利润表 CASH-现金流量表
--    account_code_match: 科目编码匹配(逗号分隔;'100%' 结尾通配)
--    sign: 取值符号 1-正 -1-负
--      BALANCE/INCOME: 金额=期末/发生净额×sign(贷方科目 CREDIT 方向由 sign 修正)
--      CASH: sign=1 取贷方发生(收现),sign=-1 取借方发生(付现)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `report_item`;
CREATE TABLE `report_item` (
  `item_id` bigint(20) NOT NULL COMMENT '项目ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `report_type` varchar(20) NOT NULL COMMENT '报表类型 BALANCE-资产负债表 INCOME-利润表 CASH-现金流量表',
  `item_code` varchar(30) NOT NULL COMMENT '项目编码(如 BS_MONETARY)',
  `item_name` varchar(100) NOT NULL COMMENT '项目名称(如 货币资金)',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示顺序',
  `parent_item_id` bigint(20) NULL DEFAULT 0 COMMENT '父项目ID(分组行,0=顶级)',
  `is_summary` char(1) NULL DEFAULT '0' COMMENT '是否合计行 0-否 1-是(由子项汇总)',
  `account_code_match` varchar(500) NULL DEFAULT NULL COMMENT '科目编码匹配(逗号分隔;100% 结尾通配)',
  `sign` varchar(5) NULL DEFAULT '1' COMMENT '取值符号 1-正 -1-负',
  `status` char(1) NULL DEFAULT '0' COMMENT '状态 0-正常 1-停用',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`item_id`) USING BTREE,
  UNIQUE INDEX `uk_item`(`report_type`, `item_code`, `tenant_id`) USING BTREE,
  INDEX `idx_report_type`(`report_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '报表项目-科目映射' ROW_FORMAT = DYNAMIC;

-- ------------------------------------------------------------
-- 2. fin_close_log 期末关账执行日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `fin_close_log`;
CREATE TABLE `fin_close_log` (
  `log_id` bigint(20) NOT NULL COMMENT '日志ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `period_id` bigint(20) NOT NULL COMMENT '期间ID',
  `close_type` varchar(20) NOT NULL COMMENT '类型 MONTHLY-月结 YEARLY-年结',
  `step` varchar(30) NOT NULL COMMENT '步骤 TRIAL_CHECK/CARRY_PL/REVALUATION/REPORT_SNAPSHOT/CLOSE_PERIOD/CARRY_FORWARD',
  `status` varchar(20) NOT NULL COMMENT '状态 SUCCESS/FAILED',
  `message` varchar(500) NULL DEFAULT NULL COMMENT '结果/错误信息',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_period`(`period_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '期末关账执行日志' ROW_FORMAT = DYNAMIC;

-- ------------------------------------------------------------
-- 3. gl_period 追加月结字段(INPLACE 不阻塞;重复执行报 Duplicate column 属正常,已应用)
-- ------------------------------------------------------------
ALTER TABLE `gl_period`
  ADD COLUMN `closing_step` varchar(30) NULL DEFAULT NULL COMMENT '月结进度(CLOSING_TRIAL/CLOSING_CARRY/CLOSING_REVAL/CLOSING_DONE,CLOSED 时冗余)' AFTER `status`,
  ADD COLUMN `closed_voucher_count` int(11) NULL DEFAULT 0 COMMENT '本期凭证数(结账时快照)' AFTER `closed_time`,
  ADD COLUMN `closing_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '损益结转凭证ID(幂等回溯)' AFTER `closed_voucher_count`,
  ADD COLUMN `revaluation_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '重估凭证ID(幂等回溯)' AFTER `closing_voucher_id`;

-- ------------------------------------------------------------
-- 4. gl_account 追加科目 seed(F5 结转/重估/报表所需)
--    段位 6005032-6005059(避让 F0 6005001-6005053)
-- ------------------------------------------------------------
INSERT IGNORE INTO `gl_account`(`account_id`,`tenant_id`,`account_code`,`account_name`,`account_type`,`balance_dir`,`parent_id`,`level_num`,`path_ids`,`path_names`,`is_leaf`,`is_recon`,`recon_type`,`is_open_item`,`status`,`sort_order`,`version`,`create_time`) VALUES
-- 权益类:未分配利润(年结目标科目)
(6005032,'000000','4104','未分配利润','EQUITY','CREDIT',0,1,'6005032','未分配利润','1','0',NULL,'0','0',32,0,NOW()),
-- 损益类:税金及附加/财务费用(含利息、汇兑子科目)/营业外收支(结转与重估/报表)
(6005054,'000000','6603','财务费用','EXPENSE','DEBIT',0,1,'6005054','财务费用','1','0',NULL,'0','0',54,0,NOW()),
(6005055,'000000','6603.01','财务费用-利息收入','EXPENSE','DEBIT',6005054,2,'6005054,6005055','财务费用,财务费用-利息收入','1','0',NULL,'0','0',1,0,NOW()),
(6005056,'000000','6603.02','财务费用-汇兑损益','EXPENSE','DEBIT',6005054,2,'6005054,6005056','财务费用,财务费用-汇兑损益','1','0',NULL,'0','0',2,0,NOW()),
(6005057,'000000','6403','税金及附加','EXPENSE','DEBIT',0,1,'6005057','税金及附加','1','0',NULL,'0','0',57,0,NOW()),
(6005058,'000000','6711','营业外支出','EXPENSE','DEBIT',0,1,'6005058','营业外支出','1','0',NULL,'0','0',58,0,NOW()),
(6005059,'000000','6301','营业外收入','INCOME','CREDIT',0,1,'6005059','营业外收入','1','0',NULL,'0','0',59,0,NOW());

-- ------------------------------------------------------------
-- 5. report_item 报表映射 seed
--    item_id 段位 7000001+(7000xxx,避让其他段)
--    BALANCE 金额=期末净额(借方向科目取 closing_debit-closing_credit,贷方向×-1)
--    INCOME 金额=本期/本年发生净额(收入取 credit-debit,费用取 debit-credit)
--    CASH 金额=本期发生额(sign=1 贷方收现 / sign=-1 借方付现)
-- ------------------------------------------------------------
INSERT IGNORE INTO `report_item`(`item_id`,`tenant_id`,`report_type`,`item_code`,`item_name`,`sort_order`,`parent_item_id`,`is_summary`,`account_code_match`,`sign`,`status`,`version`,`create_time`) VALUES
-- ===== 资产负债表(BALANCE)=====
(7000001,'000000','BALANCE','BS_MONETARY','货币资金',10,0,'0','1001,1002','1','0',0,NOW()),
(7000002,'000000','BALANCE','BS_AR','应收账款',20,0,'0','1122','1','0',0,NOW()),
(7000003,'000000','BALANCE','BS_PREPAY','预付账款',30,0,'0','1123','1','0',0,NOW()),
(7000004,'000000','BALANCE','BS_OTHER_RECEIVABLE','其他应收款',40,0,'0','1221','1','0',0,NOW()),
(7000005,'000000','BALANCE','BS_INVENTORY','存货',50,0,'0','1401,1403,1405','1','0',0,NOW()),
(7000006,'000000','BALANCE','BS_FA_NET','固定资产净值',60,0,'0','1601,-1602','1','0',0,NOW()), -- 原值1601(借) - 累计折旧1602(抵减,contra "-" 前缀),净值
(7000010,'000000','BALANCE','BS_AP','应付账款',70,0,'0','2202','1','0',0,NOW()),
(7000011,'000000','BALANCE','BS_ADVANCE_RECEIVED','预收账款',80,0,'0','2203','1','0',0,NOW()),
(7000012,'000000','BALANCE','BS_SALARY_PAYABLE','应付职工薪酬',90,0,'0','2211','1','0',0,NOW()),
(7000013,'000000','BALANCE','BS_TAX_PAYABLE','应交税费',100,0,'0','2221%','1','0',0,NOW()), -- 2221 及子科目(销项/进项)
(7000020,'000000','BALANCE','BS_EQUITY_TOTAL','实收资本+留存收益',110,0,'0','4001,4103,4104','1','0',0,NOW()),
-- 合计行(引擎按子项汇总,不取科目)
(7000099,'000000','BALANCE','BS_TOTAL','资产/负债/权益总计',999,0,'1',NULL,'1','0',0,NOW());
-- ===== 利润表(INCOME)=====
INSERT IGNORE INTO `report_item`(`item_id`,`tenant_id`,`report_type`,`item_code`,`item_name`,`sort_order`,`parent_item_id`,`is_summary`,`account_code_match`,`sign`,`status`,`version`,`create_time`) VALUES
(7000101,'000000','INCOME','IS_REVENUE','营业收入',10,0,'0','6001','1','0',0,NOW()),
(7000102,'000000','INCOME','IS_COST','营业成本',20,0,'0','6401','1','0',0,NOW()),
(7000103,'000000','INCOME','IS_TAX','税金及附加',30,0,'0','6403','1','0',0,NOW()),
(7000104,'000000','INCOME','IS_SELL_EXP','销售费用',40,0,'0','6601','1','0',0,NOW()),
(7000105,'000000','INCOME','IS_ADMIN_EXP','管理费用',50,0,'0','6602','1','0',0,NOW()),
(7000106,'000000','INCOME','IS_FIN_EXP','财务费用',60,0,'0','6603%','1','0',0,NOW()), -- 含利息/汇兑子科目
(7000107,'000000','INCOME','IS_NON_OPER_INCOME','营业外收入',70,0,'0','6301','1','0',0,NOW()),
(7000108,'000000','INCOME','IS_NON_OPER_EXP','营业外支出',80,0,'0','6711','1','0',0,NOW()),
(7000199,'000000','INCOME','IS_NET_PROFIT','净利润(合计行)',999,0,'1',NULL,'1','0',0,NOW());
-- ===== 现金流量表(CASH,直接法基础版)=====
INSERT IGNORE INTO `report_item`(`item_id`,`tenant_id`,`report_type`,`item_code`,`item_name`,`sort_order`,`parent_item_id`,`is_summary`,`account_code_match`,`sign`,`status`,`version`,`create_time`) VALUES
(7000201,'000000','CASH','CF_SALES_RECEIPT','销售商品、提供劳务收到的现金',10,0,'0','1001,1002','1','0',0,NOW()),   -- 贷方发生(收现)
(7000202,'000000','CASH','CF_PURCHASE_PAY','购买商品、接受劳务支付的现金',20,0,'0','1001,1002','-1','0',0,NOW()), -- 借方发生(付现)
(7000203,'000000','CASH','CF_TAX_PAY','支付的各项税费',30,0,'0','2221%','-1','0',0,NOW()),                  -- 应交税费借方发生(缴税)
(7000204,'000000','CASH','CF_SALARY_PAY','支付给职工的现金',40,0,'0','2211','-1','0',0,NOW()),              -- 应付职工薪酬借方发生(发薪)
(7000205,'000000','CASH','CF_FA_PAY','购建固定资产支付的现金',50,0,'0','1601','-1','0',0,NOW());             -- 固定资产借方发生(购建)

SET FOREIGN_KEY_CHECKS = 1;
