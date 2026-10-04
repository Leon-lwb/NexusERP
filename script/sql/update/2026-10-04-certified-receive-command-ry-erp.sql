-- 已批准认证订单的原生收货草稿永久请求记录；只追加，不回填旧收货或旧来源。
-- 适用存量库：MySQL5.7、已具备认证订单/18,4原生收货结构，执行前已备份并核对当前定义。
-- 表存在时不得盲重放；查询原请求后只恢复原单据，事务失败允许正常编号空洞。
CREATE TABLE `wm_certified_receive_command` (
  `tenant_id` varchar(20) NOT NULL COMMENT '请求租户',
  `request_key` varchar(128) NOT NULL COMMENT '原请求永久键',
  `request_hash` char(64) NOT NULL COMMENT '规范化原载荷摘要',
  `actor_id` bigint(20) NOT NULL COMMENT '原创建操作人',
  `order_id` bigint(20) NOT NULL COMMENT '批准采购订单',
  `purchase_line_id` bigint(20) NOT NULL COMMENT '明确采购订单行',
  `receive_id` bigint(20) NOT NULL COMMENT '原生收货单',
  `line_id` bigint(20) NOT NULL COMMENT '原生收货行',
  `receive_code` varchar(64) NOT NULL COMMENT '服务端规则编号',
  `material_id` bigint(20) NOT NULL COMMENT '原物料',
  `base_uom_id` varchar(64) NOT NULL COMMENT '原基本单位',
  `quantity` decimal(18,4) NOT NULL COMMENT '明确实际到货数量',
  `arrival_date` date NOT NULL COMMENT '实际到货日期',
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库',
  `area_id` bigint(20) NOT NULL COMMENT '库区',
  `location_id` bigint(20) NOT NULL COMMENT '储位',
  `create_time` datetime(3) NOT NULL COMMENT '提交创建时间',
  PRIMARY KEY (`tenant_id`,`request_key`),
  UNIQUE KEY `uk_wm_certified_receive` (`tenant_id`,`receive_id`,`line_id`),
  KEY `idx_wm_certified_order_line` (`tenant_id`,`order_id`,`purchase_line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证采购原生收货创建永久请求';
