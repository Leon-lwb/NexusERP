-- =============================================
-- NexusERP - 开票与应收应付优化 P2: 采购进项发票建表
-- 目标库: ry-erp
-- 创建时间: 2026-07-16
-- 说明: 新建 pu_invoice / pu_invoice_line, 镜像 so_invoice, 字段对偶(客户↔供应商、so_id↔po_id、购买方↔销售方),
--       额外增加进项认证字段(cert_status/cert_date/deductible_tax_amount)与三单匹配关联列(po_line_id/gr_line_id)。
--       相比设计文档 3.2 草图, 补"销售方开票四要素"(seller_*), 落实"购买方↔销售方"对偶(so 的 buyer_* 对应 pu 的 seller_*),
--       便于进项认证时核对销售方纳税人识别号。
-- 幂等: DROP IF EXISTS + CREATE (本地无生产数据, 可重建)
-- 关联设计: doc/erp/specs/2026-07-15-invoicing-ar-ap-redesign.md 第六节 / 第 3.2 小节
-- 字典: pu_invoice_type / pu_invoice_status / pu_invoice_cert_status 已由 dict-invoicing.sql 落库
-- =============================================

USE `ry-erp`;

-- ===== 采购进项发票头 =====
DROP TABLE IF EXISTS `pu_invoice`;
CREATE TABLE `pu_invoice`  (
  `invoice_id` bigint(20) NOT NULL COMMENT '发票ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `invoice_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发票号',
  `invoice_code` varchar(50) NULL DEFAULT NULL COMMENT '发票代码',
  `invoice_type` varchar(20) NULL DEFAULT 'VAT_SPECIAL' COMMENT '发票类型 VAT_SPECIAL-增值税专用 VAT_NORMAL-增值税普通 OTHER-其他',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_name` varchar(200) NULL DEFAULT NULL COMMENT '供应商名称',
  `seller_tax_id` varchar(30) NULL DEFAULT NULL COMMENT '销售方纳税人识别号(供应商)',
  `seller_title` varchar(200) NULL DEFAULT NULL COMMENT '销售方发票抬头',
  `seller_bank_name` varchar(100) NULL DEFAULT NULL COMMENT '销售方开户行',
  `seller_bank_account` varchar(50) NULL DEFAULT NULL COMMENT '销售方银行账号',
  `seller_address_phone` varchar(200) NULL DEFAULT NULL COMMENT '销售方地址、电话',
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
  -- 进项认证专用
  `cert_status` varchar(20) NULL DEFAULT 'UNCERTIFIED' COMMENT '认证状态 UNCERTIFIED-未认证 CERTIFIED-已认证 DEDUCTED-已抵扣 TRANSFERRED-已转出',
  `cert_date` date NULL DEFAULT NULL COMMENT '认证日期',
  `deductible_tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '可抵扣税额',
  `issue_channel` varchar(20) NULL DEFAULT 'MANUAL' COMMENT '来源渠道 MANUAL-手工录入 ELECTRONIC-电子发票平台同步',
  `e_invoice_no` varchar(50) NULL DEFAULT NULL COMMENT '数电票号码',
  `pdf_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票PDF的OSS对象ID(私有桶存ossId)',
  `ofd_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票OFD的OSS对象ID',
  `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) NULL DEFAULT NULL,
  `attr2` varchar(255) NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
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

-- ===== 采购进项发票明细行(镜像 so_invoice_line, so_line_id 换 po_line_id + gr_line_id 三单匹配) =====
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
  `gr_line_id` bigint(20) NULL DEFAULT NULL COMMENT '收货单行ID(三单匹配用, wm_material_receive_line.line_id)',
  `contract_line_id` bigint(20) NULL DEFAULT NULL COMMENT '采购合同行ID',
  `sort_order` int(11) NULL DEFAULT 0,
  `remark` varchar(500) NULL DEFAULT NULL,
  `attr1` varchar(64) NULL DEFAULT NULL,
  `attr2` varchar(255) NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
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

-- 验证
-- SHOW COLUMNS FROM `ry-erp`.`pu_invoice`;
-- SHOW COLUMNS FROM `ry-erp`.`pu_invoice_line`;
