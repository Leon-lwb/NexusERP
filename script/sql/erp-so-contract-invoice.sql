-- ==========================================================
-- SO 合同管理 + SO 开票管理
-- 包含：DDL建表 + 菜单数据
-- 菜单ID范围: 2060-2089 (已确认无冲突)
-- ==========================================================

-- ----------------------------
-- Table structure for so_contract
-- ----------------------------
DROP TABLE IF EXISTS `so_contract`;
CREATE TABLE `so_contract` (
  `contract_id` bigint(20) NOT NULL COMMENT '合同ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `contract_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '合同编号',
  `contract_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '合同名称',
  `contract_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'SALES' COMMENT '合同类型 SALES-销售合同 FRAMEWORK-框架协议 SERVICE-服务合同',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `sign_date` date NULL DEFAULT NULL COMMENT '签订日期',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `total_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '合同金额',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CNY' COMMENT '币种',
  `payment_terms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '付款条款',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '销售代表ID',
  `sales_rep_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售代表名称',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '合同状态 DRAFT-草稿 APPROVING-审批中 EFFECTIVE-已生效 EXPIRED-已到期 TERMINATED-已终止',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`contract_id`) USING BTREE,
  UNIQUE INDEX `uk_contract_no`(`contract_no`, `tenant_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售合同表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for so_contract_line
-- ----------------------------
DROP TABLE IF EXISTS `so_contract_line`;
CREATE TABLE `so_contract_line` (
  `line_id` bigint(20) NOT NULL COMMENT '合同行ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `contract_id` bigint(20) NOT NULL COMMENT '合同ID',
  `line_no` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行号',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '数量',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `unit_price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '单价',
  `line_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '行金额',
  `tax_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '税率',
  `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '税额',
  `delivery_date` date NULL DEFAULT NULL COMMENT '交货日期',
  `delivered_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已发货数量',
  `invoiced_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已开票数量',
  `line_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '行状态 DRAFT-草稿 ACTIVE-生效 COMPLETED-完成 CANCELLED-取消',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_contract_id`(`contract_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售合同明细行表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for so_invoice
-- ----------------------------
DROP TABLE IF EXISTS `so_invoice`;
CREATE TABLE `so_invoice` (
  `invoice_id` bigint(20) NOT NULL COMMENT '发票ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `invoice_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发票号',
  `invoice_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'VAT_NORMAL' COMMENT '发票类型 VAT_SPECIAL-增值税专用发票 VAT_NORMAL-增值税普通发票 OTHER-其他',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `so_id` bigint(20) NULL DEFAULT NULL COMMENT '销售订单ID',
  `contract_id` bigint(20) NULL DEFAULT NULL COMMENT '合同ID',
  `invoice_date` date NULL DEFAULT NULL COMMENT '开票日期',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10, 4) NULL DEFAULT 1.0000 COMMENT '汇率',
  `amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '金额（不含税）',
  `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '税额',
  `total_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '价税合计',
  `verified_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '已核销金额',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '发票状态 DRAFT-草稿 ISSUED-已开 VERIFIED-已核销 CANCELLED-已作废',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`invoice_id`) USING BTREE,
  UNIQUE INDEX `uk_invoice_no`(`invoice_no`, `tenant_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_contract_id`(`contract_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售发票表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for so_invoice_line
-- ----------------------------
DROP TABLE IF EXISTS `so_invoice_line`;
CREATE TABLE `so_invoice_line` (
  `line_id` bigint(20) NOT NULL COMMENT '发票行ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `invoice_id` bigint(20) NOT NULL COMMENT '发票ID',
  `line_no` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行号',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '数量',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `unit_price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '单价',
  `line_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '行金额（不含税）',
  `tax_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '税率',
  `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '税额',
  `total_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '价税合计',
  `so_line_id` bigint(20) NULL DEFAULT NULL COMMENT '销售订单行ID',
  `contract_line_id` bigint(20) NULL DEFAULT NULL COMMENT '合同行ID',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_invoice_id`(`invoice_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售发票明细行表' ROW_FORMAT = Dynamic;

-- ==========================================================
-- 菜单数据 - SO 合同管理
-- 父菜单: 2200 (销售管理)
-- 注意: 父菜单ID 2200 需要 ry-vue.sql 中的销售管理目录已存在
-- ==========================================================

-- SO 合同管理菜单
INSERT INTO `sys_menu` VALUES (2060, '合同管理', 2200, 4, 'contract', 'erp/so/contract/index', NULL, 1, 0, 'C', '0', '0', 'so:contract:list', 'contract', 103, 1, NOW(), NULL, NULL, '销售合同管理菜单');
INSERT INTO `sys_menu` VALUES (2061, '合同查询', 2060, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:contract:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2062, '合同新增', 2060, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:contract:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2063, '合同修改', 2060, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:contract:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2064, '合同删除', 2060, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:contract:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2065, '合同导出', 2060, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:contract:export', '#', 103, 1, NOW(), NULL, NULL, '');

-- SO 开票管理菜单
INSERT INTO `sys_menu` VALUES (2070, '开票管理', 2200, 5, 'invoice', 'erp/so/invoice/index', NULL, 1, 0, 'C', '0', '0', 'so:invoice:list', 'money', 103, 1, NOW(), NULL, NULL, '销售发票管理菜单');
INSERT INTO `sys_menu` VALUES (2071, '发票查询', 2070, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:invoice:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2072, '发票新增', 2070, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:invoice:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2073, '发票修改', 2070, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:invoice:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2074, '发票删除', 2070, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:invoice:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2075, '发票导出', 2070, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:invoice:export', '#', 103, 1, NOW(), NULL, NULL, '');
