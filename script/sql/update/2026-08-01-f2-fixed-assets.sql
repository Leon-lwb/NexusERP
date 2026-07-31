-- ============================================================
-- F2 固定资产 FA 模块 - 业务表 DDL + 类别 seed
-- 目标库: ry-erp (业务库)
-- 所属: NexusERP-SPEC-2026-006-F2
-- 日期: 2026-08-01
-- 说明: fa_asset_category(类别/折旧政策) / fa_asset(卡片)
--       / fa_depreciation(折旧记录) / fa_disposal(处置) 四张新表
--       + 类别 seed(默认折旧政策,科目引用 F0 既有 account_id)
--       权威 schema 并入 script/sql/ry-erp.sql
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. fa_asset_category 固定资产类别(折旧政策)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `fa_asset_category`;
CREATE TABLE `fa_asset_category` (
  `category_id` bigint(20) NOT NULL COMMENT '类别ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `category_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '类别编码',
  `category_name` varchar(100) NOT NULL COMMENT '类别名称(如 机器设备/办公设备/运输工具)',
  `depreciation_method` varchar(20) NOT NULL DEFAULT 'STRAIGHT_LINE' COMMENT '折旧方法 STRAIGHT_LINE-直线法 DOUBLE_DECLINING-双倍余额递减 SUM_OF_YEARS-年数总和 MANUAL-手工',
  `useful_life` int(11) NOT NULL DEFAULT 120 COMMENT '预计使用年限(月)',
  `salvage_value_rate` decimal(10,4) NULL DEFAULT 0.0000 COMMENT '净残值率(0-1,默认0)',
  `debit_account_id` bigint(20) NULL DEFAULT NULL COMMENT '折旧费用科目ID(借,默认查 6602 管理费用)',
  `credit_account_id` bigint(20) NULL DEFAULT NULL COMMENT '累计折旧科目ID(贷,默认查 1602)',
  `disposal_loss_account_id` bigint(20) NULL DEFAULT NULL COMMENT '处置损失科目ID(借,默认查 6711 营业外支出)',
  `disposal_gain_account_id` bigint(20) NULL DEFAULT NULL COMMENT '处置收益科目ID(贷,默认查 6301 营业外收入)',
  `status` char(1) NULL DEFAULT '0' COMMENT '状态 0-正常 1-停用',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示顺序',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`category_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`category_code`, `tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产类别(折旧政策)' ROW_FORMAT = DYNAMIC;

-- ------------------------------------------------------------
-- 2. fa_asset 固定资产卡片
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `fa_asset`;
CREATE TABLE `fa_asset` (
  `asset_id` bigint(20) NOT NULL COMMENT '资产ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `asset_no` varchar(30) NOT NULL COMMENT '资产编号(自动生成 FA-YYYY-NNNN)',
  `asset_name` varchar(100) NOT NULL COMMENT '资产名称',
  `category_id` bigint(20) NOT NULL COMMENT '类别ID',
  `original_value` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '原值',
  `salvage_value` decimal(18,2) NULL DEFAULT 0.00 COMMENT '净残值(原值×残值率,可手工覆盖)',
  `useful_life` int(11) NOT NULL DEFAULT 120 COMMENT '使用年限(月,随类别带出可改)',
  `depreciation_method` varchar(20) NULL DEFAULT NULL COMMENT '折旧方法(空=随类别)',
  `acquisition_date` date NULL DEFAULT NULL COMMENT '购置日期',
  `in_use_date` date NULL DEFAULT NULL COMMENT '启用日期(开始计提,空=购置日)',
  `location` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '存放地点',
  `status` varchar(20) NOT NULL DEFAULT 'IN_USE' COMMENT '状态 IN_USE-使用中 CWIP-在建 DISPOSED-已处置',
  `cumulative_depreciation` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '累计已计提折旧(随折旧过账更新)',
  `net_book_value` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '账面净值(原值-累计折旧)',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`asset_id`) USING BTREE,
  UNIQUE INDEX `uk_asset_no`(`asset_no`, `tenant_id`) USING BTREE,
  INDEX `idx_category`(`category_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产卡片' ROW_FORMAT = DYNAMIC;

-- ------------------------------------------------------------
-- 3. fa_depreciation 折旧记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `fa_depreciation`;
CREATE TABLE `fa_depreciation` (
  `depreciation_id` bigint(20) NOT NULL COMMENT '折旧ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `asset_id` bigint(20) NOT NULL COMMENT '资产ID',
  `period_id` bigint(20) NOT NULL COMMENT '期间ID',
  `depreciation_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '本期折旧额',
  `cumulative_depreciation` decimal(18,2) NULL DEFAULT 0.00 COMMENT '计提后累计折旧',
  `status` varchar(20) NOT NULL DEFAULT 'PLANNED' COMMENT '状态 PLANNED-计划 DONE-已过账',
  `posted_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '过账凭证ID',
  `posted_voucher_no` varchar(50) NULL DEFAULT NULL COMMENT '过账凭证号',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`depreciation_id`) USING BTREE,
  UNIQUE INDEX `uk_asset_period`(`asset_id`, `period_id`, `tenant_id`) USING BTREE,
  INDEX `idx_period`(`period_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产折旧记录' ROW_FORMAT = DYNAMIC;

-- ------------------------------------------------------------
-- 4. fa_disposal 资产处置
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `fa_disposal`;
CREATE TABLE `fa_disposal` (
  `disposal_id` bigint(20) NOT NULL COMMENT '处置ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `asset_id` bigint(20) NOT NULL COMMENT '资产ID',
  `disposal_type` varchar(20) NOT NULL COMMENT '类型 SALE-出售 SCRAP-报废',
  `disposal_date` date NOT NULL COMMENT '处置日期',
  `disposal_price` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '处置收入(售价/残值回收)',
  `net_book_value` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '处置时账面净值(原值-累计折旧)',
  `gain_loss` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '处置损益(正=收益 负=损失)',
  `posted_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '过账凭证ID',
  `posted_voucher_no` varchar(50) NULL DEFAULT NULL COMMENT '过账凭证号',
  `status` varchar(20) NOT NULL DEFAULT 'POSTED' COMMENT '状态 POSTED-已过账',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`disposal_id`) USING BTREE,
  INDEX `idx_asset`(`asset_id`) USING BTREE,
  INDEX `idx_date`(`disposal_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产处置记录' ROW_FORMAT = DYNAMIC;

-- ------------------------------------------------------------
-- 5. 类别 seed(默认折旧政策;科目引用 F0 seed 既有 account_id)
--    6602 管理费用=6005053 / 1602 累计折旧=6005010
--    6711 营业外支出=6005058 / 6301 营业外收入=6005059
-- ------------------------------------------------------------
INSERT IGNORE INTO `fa_asset_category`(`category_id`,`tenant_id`,`category_code`,`category_name`,`depreciation_method`,`useful_life`,`salvage_value_rate`,`debit_account_id`,`credit_account_id`,`disposal_loss_account_id`,`disposal_gain_account_id`,`status`,`sort_order`,`version`,`create_time`) VALUES
(6100001,'000000','FA_CAT_MACHINE','机器设备','STRAIGHT_LINE',120,0.0500,6005053,6005010,6005058,6005059,'0',1,0,NOW()),
(6100002,'000000','FA_CAT_OFFICE','办公设备','STRAIGHT_LINE',60,0.0500,6005053,6005010,6005058,6005059,'0',2,0,NOW()),
(6100003,'000000','FA_CAT_VEHICLE','运输工具','DOUBLE_DECLINING',96,0.0500,6005053,6005010,6005058,6005059,'0',3,0,NOW());

SET FOREIGN_KEY_CHECKS = 1;

-- 验证
-- SELECT category_id, category_name, depreciation_method, useful_life FROM fa_asset_category;
