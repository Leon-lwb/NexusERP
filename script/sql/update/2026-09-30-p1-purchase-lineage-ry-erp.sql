/** P1 ERP 来源分配与计量条款：只对已确认基线/备份的 ry-erp 存量库执行一次。 */
/** 手工预检实例、MySQL 5.7 版本、备份可恢复性、P0 门禁生效和所有采购写入口的维护窗口。 */
/** 不可重放；MySQL 5.7 DDL 每步隐式提交，中途失败必须保全现场、核对对象状态再人工恢复。 */
/** 禁止导入 ry-erp.sql 主初始化脚本，禁止将已有 min_order_qty 数字、旧 UOM 换算或 source_id 升格为可信来源。 */
/** 不自动为历史申请/供应商/UOM 建立归属或条款；legacy 未结申请必须由业务层在同一 guard 下独立核验。 */
/** 只读预检（由 DBA 另行运行并审核，以下注释不随迁移自动执行）： */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'ry-erp' AND TABLE_NAME IN ('bd_unit_of_measure','bd_material_vendor','prd_material_requirement_detail','pu_purchase_requisition_line','pu_purchase_order_line','pu_requirement_material_guard','pu_requirement_allocation_ledger','pu_requirement_allocation_event'); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME,COLUMN_NAME,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'ry-erp' AND TABLE_NAME IN ('bd_unit_of_measure','bd_material_vendor','pu_purchase_requisition_line') AND COLUMN_NAME IN ('uom_id','relation_id','source_id','order_uom_id','verified_moq_qty','moq_uom_id','order_multiple','allow_split','units_per_pack','terms_version','terms_effective_at','terms_expires_at','terms_verified_at','quantity_scale','conversion_version','conversion_verified_at'); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME,INDEX_NAME,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'ry-erp' AND TABLE_NAME IN ('bd_material_vendor','bd_unit_of_measure','pu_requirement_material_guard','pu_requirement_allocation_ledger','pu_requirement_allocation_event'); */
/** 预期五张原表存在；3 张新表和全部新增列不存在；核对旧列、tenant_id、原索引与主脚本一致。 */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'ry-erp' AND ((TABLE_NAME IN ('bd_unit_of_measure','bd_material_vendor','pu_purchase_requisition_line') AND COLUMN_NAME IN ('del_flag','source_id','tenant_id')) OR (TABLE_NAME='prd_material_requirement_detail' AND COLUMN_NAME IN ('detail_id','tenant_id','required_quantity','issued_quantity','required_date')) OR (TABLE_NAME='pu_purchase_order_line' AND COLUMN_NAME IN ('line_id','tenant_id','order_quantity','received_quantity','requisition_line_id'))); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME,COLUMN_NAME,INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME IN ('prd_material_requirement_detail','pu_purchase_requisition_line','pu_purchase_order_line'); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT COUNT(*) FROM `ry-erp`.`prd_material_requirement_detail`; SELECT COUNT(*) FROM `ry-erp`.`pu_purchase_requisition_line`; SELECT COUNT(*) FROM `ry-erp`.`pu_purchase_order_line`; */
/** 注意既有 pu_purchase_requisition_line / pu_purchase_order_line 当前无单独 tenant_id 索引；为历史基线事实而非新DDL漏建，业务查询须带tenant过滤并按来源/单头字段锁行，若实际索引与只读预检不同停止。 */
/** 用只读业务 API/查询盘点 legacy MRP 关联申请；source_id 不是可靠的 detail_id，不得据此自动回填。 */

USE `ry-erp`;

ALTER TABLE `bd_unit_of_measure`
  ADD COLUMN `quantity_scale` tinyint(3) unsigned NULL DEFAULT NULL COMMENT '可信订货数量小数精度；未核验为NULL' AFTER `del_flag`,
  ADD COLUMN `conversion_version` varchar(64) NULL DEFAULT NULL COMMENT '经业务确认的单位换算版本；旧换算率不自动可信' AFTER `quantity_scale`,
  ADD COLUMN `conversion_verified_at` datetime NULL DEFAULT NULL COMMENT '换算核验时间；NULL拒绝自动推断' AFTER `conversion_version`;

ALTER TABLE `bd_material_vendor`
  ADD COLUMN `order_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '业务确认的订购计量单位ID' AFTER `del_flag`,
  ADD COLUMN `verified_moq_qty` decimal(18, 4) NULL DEFAULT NULL COMMENT '已核实MOQ数值；不得从旧min_order_qty回填' AFTER `order_uom_id`,
  ADD COLUMN `moq_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '已核实MOQ所属计量单位ID' AFTER `verified_moq_qty`,
  ADD COLUMN `order_multiple` decimal(18, 4) NULL DEFAULT NULL COMMENT '业务确认的订购倍数（订购单位）' AFTER `moq_uom_id`,
  ADD COLUMN `allow_split` tinyint(1) NULL DEFAULT NULL COMMENT '是否允许拆零；NULL为未知' AFTER `order_multiple`,
  ADD COLUMN `units_per_pack` decimal(18, 4) NULL DEFAULT NULL COMMENT '每包装实物基本单位量；NULL为未知' AFTER `allow_split`,
  ADD COLUMN `terms_version` varchar(64) NULL DEFAULT NULL COMMENT '供应商条款确认版本；NULL为历史未核验' AFTER `units_per_pack`,
  ADD COLUMN `terms_effective_at` datetime NULL DEFAULT NULL COMMENT '核验条款生效时间；不得套用旧关系生效日期' AFTER `terms_version`,
  ADD COLUMN `terms_expires_at` datetime NULL DEFAULT NULL COMMENT '核验条款失效时间；不得套用旧关系失效日期' AFTER `terms_effective_at`,
  ADD COLUMN `terms_verified_at` datetime NULL DEFAULT NULL COMMENT '采购业务确认时间；NULL为未核验' AFTER `terms_expires_at`;

CREATE TABLE `pu_requirement_material_guard` (
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `version` int(11) NOT NULL DEFAULT 0 COMMENT '竞争池版本，由ERP事务内锁定后核验',
  `update_time` datetime NULL DEFAULT NULL COMMENT '最近一次事务变更时间',
  PRIMARY KEY (`tenant_id`, `material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购同租户物料竞争池行锁（由业务事务创建并锁定）' ROW_FORMAT = DYNAMIC;

CREATE TABLE `pu_requirement_allocation_ledger` (
  `allocation_id` bigint(20) NOT NULL COMMENT '雪花ID，逐来源占用ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显式来源类型，MRP_DETAIL；不可推断旧source_id',
  `detail_id` bigint(20) NOT NULL COMMENT '已核验的MRP需求明细ID',
  `requisition_line_id` bigint(20) NOT NULL COMMENT '采购申请行ID（ERP事务内写入）',
  `allocation_no` int(11) NOT NULL COMMENT '同来源与申请行的拆分序号，幂等不可复用',
  `order_line_id` bigint(20) NULL DEFAULT NULL COMMENT '继承来源的采购订单行ID，未转单为NULL',
  `base_uom_id` bigint(20) NOT NULL COMMENT '物料已核验基本单位ID',
  `covered_demand_base_qty` decimal(18, 4) NOT NULL COMMENT '本条来源分配的基本单位量，不含MOQ超额',
  `occupied_base_qty` decimal(18, 4) NOT NULL COMMENT '当前申请意图/晚到订单阻断量（基本单位）',
  `fulfilled_base_qty` decimal(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '已核验入账履约量（基本单位）',
  `released_base_qty` decimal(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '已核验释放量（基本单位）',
  `allocation_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'REQUEST_INTENT/ORDER_COMMITTED_NOT_TIMELY_SUPPLY/ORDER_TIMELY_SUPPLY/RELEASED',
  `allocation_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户内确定性的来源占用幂等键（含人工）',
  `decision_idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'AI动作幂等键；人工来源为NULL',
  `version` int(11) NOT NULL DEFAULT 0 COMMENT '行乐观锁版本；改写/释放需CAS',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建留痕',
  `transfer_time` datetime NULL DEFAULT NULL COMMENT '最近一次订单承接时间',
  `released_at` datetime NULL DEFAULT NULL COMMENT '最终释放时间；不能仅因申请关闭释放订单承接',
  `release_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '业务释放原因',
  `update_time` datetime NULL DEFAULT NULL COMMENT '最后一次业务状态变更时间',
  PRIMARY KEY (`allocation_id`) USING BTREE,
  UNIQUE INDEX `uk_pral_tenant_key`(`tenant_id`, `allocation_key`) USING BTREE,
  UNIQUE INDEX `uk_pral_tenant_req_detail_no`(`tenant_id`, `requisition_line_id`, `detail_id`, `allocation_no`) USING BTREE,
  INDEX `idx_pral_tenant_material`(`tenant_id`, `material_id`, `allocation_status`) USING BTREE,
  INDEX `idx_pral_tenant_detail`(`tenant_id`, `detail_id`) USING BTREE,
  INDEX `idx_pral_tenant_req_line`(`tenant_id`, `requisition_line_id`) USING BTREE,
  INDEX `idx_pral_tenant_order_line`(`tenant_id`, `order_line_id`) USING BTREE,
  INDEX `idx_pral_tenant_decision_key`(`tenant_id`, `decision_idempotency_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '逐MRP明细到申请行/订单行的基本单位占用账本' ROW_FORMAT = DYNAMIC;

CREATE TABLE `pu_requirement_allocation_event` (
  `event_id` bigint(20) NOT NULL COMMENT '雪花ID，占用事件ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  `allocation_id` bigint(20) NOT NULL COMMENT '对应来源占用账本ID',
  `event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事务内幂等事件键，不可复用',
  `event_type` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建/承接/部分入账/释放/补偿',
  `from_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原状态',
  `to_status` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '新状态',
  `occupied_base_before` decimal(18, 4) NOT NULL COMMENT '原基本单位阻断量',
  `occupied_base_after` decimal(18, 4) NOT NULL COMMENT '新基本单位阻断量',
  `requisition_line_id` bigint(20) NOT NULL COMMENT '申请行ID快照',
  `order_line_id` bigint(20) NULL DEFAULT NULL COMMENT '订单行ID快照',
  `actor_id` bigint(20) NULL DEFAULT NULL COMMENT '业务操作人ID；自动任务另行审计',
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件发生时间',
  PRIMARY KEY (`event_id`) USING BTREE,
  UNIQUE INDEX `uk_prae_tenant_event_key`(`tenant_id`, `event_key`) USING BTREE,
  INDEX `idx_prae_tenant_allocation`(`tenant_id`, `allocation_id`, `occurred_at`) USING BTREE,
  INDEX `idx_prae_tenant_order_line`(`tenant_id`, `order_line_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '逐来源占用状态变更与释放不可覆盖审计' ROW_FORMAT = DYNAMIC;