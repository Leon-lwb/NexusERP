-- ----------------------------
-- 支付模块表（从 Geek-Scene-Pay 移植）
-- 放入 ry-vue 库
-- ----------------------------

-- ----------------------------
-- 订单表
-- ----------------------------
DROP TABLE IF EXISTS `pay_order`;
CREATE TABLE `pay_order` (
  `order_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_number` varchar(255) DEFAULT NULL COMMENT '商户订单号',
  `third_number` varchar(255) DEFAULT NULL COMMENT '第三方订单号',
  `order_status` varchar(255) DEFAULT NULL COMMENT '订单状态',
  `total_amount` varchar(255) DEFAULT NULL COMMENT '订单总金额（单位：分）',
  `actual_amount` varchar(255) DEFAULT NULL COMMENT '实际支付金额（单位：分）',
  `order_content` varchar(255) DEFAULT NULL COMMENT '订单内容',
  `order_message` varchar(255) DEFAULT NULL COMMENT '负载信息',
  `pay_type` varchar(255) DEFAULT NULL COMMENT '支付方式',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `pay_by` varchar(64) DEFAULT '' COMMENT '支付人',
  `tenant_id` varchar(20) DEFAULT '000000' COMMENT '租户编号',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`order_id`) USING BTREE,
  KEY `idx_order_number` (`order_number`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------
-- 发票表
-- ----------------------------
DROP TABLE IF EXISTS `pay_invoice`;
CREATE TABLE `pay_invoice` (
  `invoice_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '发票ID',
  `order_number` varchar(255) DEFAULT NULL COMMENT '订单号',
  `invoice_type` varchar(255) DEFAULT NULL COMMENT '发票类型',
  `invoice_header` varchar(255) DEFAULT NULL COMMENT '发票抬头',
  `invoice_number` varchar(255) DEFAULT NULL COMMENT '纳税人识别号',
  `invoice_phone` varchar(255) DEFAULT NULL COMMENT '收票人手机号',
  `invoice_email` varchar(255) DEFAULT NULL COMMENT '收票人邮箱',
  `invoice_remark` varchar(255) DEFAULT NULL COMMENT '发票备注',
  `tenant_id` varchar(20) DEFAULT '000000' COMMENT '租户编号',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`invoice_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='发票表';
