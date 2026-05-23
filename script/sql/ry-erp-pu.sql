-- 采购模块增量脚本

DROP TABLE IF EXISTS `pu_purchase_requisition`;
CREATE TABLE `pu_purchase_requisition` (
  `requisition_id` bigint NOT NULL AUTO_INCREMENT COMMENT '采购申请ID',
  `requisition_no` varchar(64) NOT NULL COMMENT '采购申请单号',
  `requisition_name` varchar(128) DEFAULT NULL COMMENT '采购申请名称',
  `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `applicant_name` varchar(64) DEFAULT NULL COMMENT '申请人名称',
  `requisition_date` datetime DEFAULT NULL COMMENT '申请日期',
  `required_date` datetime DEFAULT NULL COMMENT '需求日期',
  `source_type` varchar(32) DEFAULT NULL COMMENT '来源类型',
  `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态',
  `select_flow_id` bigint DEFAULT NULL COMMENT '流程ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) DEFAULT NULL,
  `attr2` varchar(500) DEFAULT NULL,
  `attr3` bigint DEFAULT NULL,
  `attr4` bigint DEFAULT NULL,
  `tenant_id` varchar(64) DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`requisition_id`),
  UNIQUE KEY `uk_requisition_no` (`requisition_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购申请表';

-- 条码配置表：用于控制条码生成规则
DROP TABLE IF EXISTS `wm_barcode_config`;
CREATE TABLE `wm_barcode_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '条码配置ID',
  `config_code` varchar(64) NOT NULL COMMENT '规则编码',
  `config_name` varchar(128) NOT NULL COMMENT '规则名称',
  `prefix` varchar(32) DEFAULT NULL COMMENT '条码前缀',
  `date_pattern` varchar(32) DEFAULT 'yyyyMMdd' COMMENT '日期格式',
  `sequence_length` int NOT NULL DEFAULT 6 COMMENT '流水号长度',
  `with_check_digit` bigint DEFAULT 0 COMMENT '是否包含校验位(0否 1是)',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '适用业务类型',
  `category_id` bigint DEFAULT NULL COMMENT '适用物料类别',
  `enable_flag` bigint DEFAULT 1 COMMENT '是否启用(0否 1是)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) DEFAULT NULL,
  `attr2` varchar(500) DEFAULT NULL,
  `attr3` bigint DEFAULT NULL,
  `attr4` bigint DEFAULT NULL,
  `tenant_id` varchar(64) DEFAULT '000000',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) DEFAULT '0',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_barcode_config_code` (`config_code`),
  KEY `idx_barcode_config_biz_type` (`biz_type`),
  KEY `idx_barcode_config_category_id` (`category_id`),
  KEY `idx_barcode_config_enable_flag` (`enable_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='条码配置表';

-- 条码实例表：记录系统已生成、已绑定和已使用的条码
DROP TABLE IF EXISTS `wm_barcode`;
CREATE TABLE `wm_barcode` (
  `barcode_id` bigint NOT NULL AUTO_INCREMENT COMMENT '条码ID',
  `barcode_code` varchar(128) NOT NULL COMMENT '条码值',
  `config_id` bigint DEFAULT NULL COMMENT '配置ID',
  `config_code` varchar(64) DEFAULT NULL COMMENT '配置编码',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '业务类型',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `biz_line_id` bigint DEFAULT NULL COMMENT '关联业务行ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `stock_id` bigint DEFAULT NULL COMMENT '库存ID',
  `status` varchar(32) DEFAULT 'ACTIVE' COMMENT '状态',
  `generate_time` datetime DEFAULT NULL COMMENT '生成时间',
  `bind_time` datetime DEFAULT NULL COMMENT '绑定时间',
  `expire_time` datetime DEFAULT NULL COMMENT '失效时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) DEFAULT NULL,
  `attr2` varchar(500) DEFAULT NULL,
  `attr3` bigint DEFAULT NULL,
  `attr4` bigint DEFAULT NULL,
  `tenant_id` varchar(64) DEFAULT '000000',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) DEFAULT '0',
  PRIMARY KEY (`barcode_id`),
  UNIQUE KEY `uk_barcode_code` (`barcode_code`),
  KEY `idx_barcode_config_id` (`config_id`),
  KEY `idx_barcode_biz_type` (`biz_type`),
  KEY `idx_barcode_biz_id` (`biz_id`),
  KEY `idx_barcode_material_id` (`material_id`),
  KEY `idx_barcode_stock_id` (`stock_id`),
  KEY `idx_barcode_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='条码实例表';

-- 默认条码规则，确保开箱即用
INSERT INTO `wm_barcode_config` (
  `config_id`, `config_code`, `config_name`, `prefix`, `date_pattern`, `sequence_length`,
  `with_check_digit`, `biz_type`, `category_id`, `enable_flag`, `remark`,
  `tenant_id`, `create_time`, `update_time`, `del_flag`
) VALUES (
  900001, 'WM_RECEIVE_DEFAULT', '收货入库默认条码规则', 'RC', 'yyyyMMdd', 6,
  0, 'RECEIVE_IN', NULL, 1, '默认收货入库条码配置，入库后自动生成条码实例',
  '000000', NOW(), NOW(), '0'
);

DROP TABLE IF EXISTS `pu_purchase_order`;
CREATE TABLE `pu_purchase_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '采购订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '采购订单号',
  `requisition_id` bigint DEFAULT NULL COMMENT '来源申请ID',
  `requisition_no` varchar(64) DEFAULT NULL COMMENT '来源申请单号',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(128) DEFAULT NULL COMMENT '供应商名称',
  `order_date` datetime DEFAULT NULL COMMENT '订单日期',
  `expected_arrival_date` datetime DEFAULT NULL COMMENT '预计到货日期',
  `currency` varchar(16) DEFAULT 'CNY' COMMENT '币别',
  `exchange_rate` decimal(18,6) DEFAULT 1.000000 COMMENT '汇率',
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '含税总金额',
  `tax_amount` decimal(18,2) DEFAULT NULL COMMENT '税额',
  `discount_amount` decimal(18,2) DEFAULT NULL COMMENT '折扣金额',
  `discount_rate` decimal(10,2) DEFAULT NULL COMMENT '折扣率',
  `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) DEFAULT NULL,
  `attr2` varchar(500) DEFAULT NULL,
  `attr3` bigint DEFAULT NULL,
  `attr4` bigint DEFAULT NULL,
  `tenant_id` varchar(64) DEFAULT '000000',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) DEFAULT '0',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单表';

DROP TABLE IF EXISTS `pu_purchase_return`;
CREATE TABLE `pu_purchase_return` (
  `return_id` bigint NOT NULL AUTO_INCREMENT COMMENT '采购退货ID',
  `return_no` varchar(64) NOT NULL COMMENT '退货单号',
  `order_id` bigint DEFAULT NULL COMMENT '采购订单ID',
  `order_no` varchar(64) DEFAULT NULL COMMENT '采购订单号',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(128) DEFAULT NULL COMMENT '供应商名称',
  `return_date` datetime DEFAULT NULL COMMENT '退货日期',
  `return_reason` varchar(500) DEFAULT NULL COMMENT '退货原因',
  `total_amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `status` varchar(32) DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) DEFAULT NULL,
  `attr2` varchar(500) DEFAULT NULL,
  `attr3` bigint DEFAULT NULL,
  `attr4` bigint DEFAULT NULL,
  `tenant_id` varchar(64) DEFAULT '000000',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) DEFAULT '0',
  PRIMARY KEY (`return_id`),
  UNIQUE KEY `uk_return_no` (`return_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购退货表';

DROP TABLE IF EXISTS `pu_purchase_return_line`;
CREATE TABLE `pu_purchase_return_line` (
  `line_id` bigint NOT NULL AUTO_INCREMENT COMMENT '退货行ID',
  `return_id` bigint NOT NULL COMMENT '退货单ID',
  `order_line_id` bigint DEFAULT NULL COMMENT '采购订单行ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(128) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(32) DEFAULT NULL COMMENT '单位',
  `return_quantity` decimal(18,4) DEFAULT NULL COMMENT '退货数量',
  `unit_price` decimal(18,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `return_reason` varchar(500) DEFAULT NULL COMMENT '退货原因',
  `sort_order` int DEFAULT NULL COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) DEFAULT NULL,
  `attr2` varchar(500) DEFAULT NULL,
  `attr3` bigint DEFAULT NULL,
  `attr4` bigint DEFAULT NULL,
  `tenant_id` varchar(64) DEFAULT '000000',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) DEFAULT '0',
  PRIMARY KEY (`line_id`),
  KEY `idx_return_id` (`return_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购退货行表';

INSERT INTO `sys_dict_type` VALUES (300001, '000000', '采购申请状态', 'pu_purchase_requisition_status', 103, 1, NOW(), NULL, NULL, '采购申请状态');
INSERT INTO `sys_dict_type` VALUES (300002, '000000', '采购订单状态', 'pu_purchase_order_status', 103, 1, NOW(), NULL, NULL, '采购订单状态');
INSERT INTO `sys_dict_type` VALUES (300003, '000000', '采购退货状态', 'pu_purchase_return_status', 103, 1, NOW(), NULL, NULL, '采购退货状态');
INSERT INTO `sys_dict_type` VALUES (300004, '000000', '采购来源类型', 'pu_source_type', 103, 1, NOW(), NULL, NULL, '采购来源类型');

INSERT INTO `sys_dict_data` VALUES (3000011, '000000', 1, '草稿', 'DRAFT', 'pu_purchase_requisition_status', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '草稿');
INSERT INTO `sys_dict_data` VALUES (3000012, '000000', 2, '已提交', 'SUBMITTED', 'pu_purchase_requisition_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '已提交');
INSERT INTO `sys_dict_data` VALUES (3000013, '000000', 3, '审批中', 'APPROVING', 'pu_purchase_requisition_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '审批中');
INSERT INTO `sys_dict_data` VALUES (3000014, '000000', 4, '已批准', 'APPROVED', 'pu_purchase_requisition_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '已批准');
INSERT INTO `sys_dict_data` VALUES (3000015, '000000', 5, '已转单', 'ORDERED', 'pu_purchase_requisition_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '已转采购订单');
INSERT INTO `sys_dict_data` VALUES (3000016, '000000', 6, '已关闭', 'CLOSED', 'pu_purchase_requisition_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '已关闭');
INSERT INTO `sys_dict_data` VALUES (3000017, '000000', 7, '已取消', 'CANCELLED', 'pu_purchase_requisition_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '已取消');

INSERT INTO `sys_dict_data` VALUES (3000021, '000000', 1, '草稿', 'DRAFT', 'pu_purchase_order_status', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '草稿');
INSERT INTO `sys_dict_data` VALUES (3000022, '000000', 2, '已提交', 'SUBMITTED', 'pu_purchase_order_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '已提交');
INSERT INTO `sys_dict_data` VALUES (3000023, '000000', 3, '审批中', 'APPROVING', 'pu_purchase_order_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '审批中');
INSERT INTO `sys_dict_data` VALUES (3000024, '000000', 4, '已批准', 'APPROVED', 'pu_purchase_order_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '已批准');
INSERT INTO `sys_dict_data` VALUES (3000025, '000000', 5, '部分到货', 'PARTIALLY_RECEIVED', 'pu_purchase_order_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '部分到货');
INSERT INTO `sys_dict_data` VALUES (3000026, '000000', 6, '已到货', 'RECEIVED', 'pu_purchase_order_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '已到货');
INSERT INTO `sys_dict_data` VALUES (3000027, '000000', 7, '已关闭', 'CLOSED', 'pu_purchase_order_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '已关闭');
INSERT INTO `sys_dict_data` VALUES (3000028, '000000', 8, '已取消', 'CANCELLED', 'pu_purchase_order_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '已取消');

INSERT INTO `sys_dict_data` VALUES (3000031, '000000', 1, '草稿', 'DRAFT', 'pu_purchase_return_status', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '草稿');
INSERT INTO `sys_dict_data` VALUES (3000032, '000000', 2, '已确认', 'CONFIRMED', 'pu_purchase_return_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '已确认');
INSERT INTO `sys_dict_data` VALUES (3000033, '000000', 3, '已执行', 'EXECUTED', 'pu_purchase_return_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '已执行');
INSERT INTO `sys_dict_data` VALUES (3000034, '000000', 4, '已取消', 'CANCELLED', 'pu_purchase_return_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '已取消');

INSERT INTO `sys_dict_data` VALUES (3000041, '000000', 1, '手工', 'MANUAL', 'pu_source_type', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '手工来源');
INSERT INTO `sys_dict_data` VALUES (3000042, '000000', 2, '需求转入', 'REQ_CONVERT', 'pu_source_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '需求转入');
INSERT INTO `sys_dict_data` VALUES (3000043, '000000', 3, '到货联动', 'RECEIVE_LINK', 'pu_source_type', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '到货联动');

INSERT INTO `sys_menu` VALUES (2500, '采购管理', 2000, 9, 'pu', NULL, NULL, 1, 0, 'M', '0', '0', '', 'shopping-cart', 103, 1, NOW(), NULL, NULL, '采购管理目录');
INSERT INTO `sys_menu` VALUES (2501, '采购申请', 2500, 1, 'requisition', 'erp/pu/requisition/index', NULL, 1, 0, 'C', '0', '0', 'pu:requisition:list', 'document', 103, 1, NOW(), NULL, NULL, '采购申请菜单');
INSERT INTO `sys_menu` VALUES (2502, '采购申请查询', 2501, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2503, '采购申请新增', 2501, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2504, '采购申请修改', 2501, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2505, '采购申请删除', 2501, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2506, '采购申请导出', 2501, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:export', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2507, '采购申请提交', 2501, 6, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2508, '采购申请审批', 2501, 7, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2509, '采购申请转单', 2501, 8, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:requisition:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2510, '采购订单', 2500, 2, 'order', 'erp/pu/order/index', NULL, 1, 0, 'C', '0', '0', 'pu:order:list', 'list', 103, 1, NOW(), NULL, NULL, '采购订单菜单');
INSERT INTO `sys_menu` VALUES (2511, '采购订单查询', 2510, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2512, '采购订单新增', 2510, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2513, '采购订单修改', 2510, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2514, '采购订单删除', 2510, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2515, '采购订单导出', 2510, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:export', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2516, '采购订单提交', 2510, 6, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2517, '采购订单审批', 2510, 7, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2518, '采购订单收货联动', 2510, 8, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:order:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2520, '采购退货', 2500, 3, 'return', 'erp/pu/return/index', NULL, 1, 0, 'C', '0', '0', 'pu:return:list', 'back', 103, 1, NOW(), NULL, NULL, '采购退货菜单');
INSERT INTO `sys_menu` VALUES (2521, '采购退货查询', 2520, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:return:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2522, '采购退货新增', 2520, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:return:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2523, '采购退货修改', 2520, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:return:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2524, '采购退货删除', 2520, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:return:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2525, '采购退货导出', 2520, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:return:export', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2526, '采购退货确认', 2520, 6, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:return:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2527, '采购退货执行', 2520, 7, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:return:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2530, '供应商评估', 2500, 4, 'vendorEvaluation', 'erp/pu/vendorEvaluation/index', NULL, 1, 0, 'C', '0', '0', 'pu:vendorEvaluation:list', 'star', 103, 1, NOW(), NULL, NULL, '');
