-- ----------------------------
-- R-042 批次管理
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- Table structure for wm_batch
-- ----------------------------
DROP TABLE IF EXISTS `wm_batch`;
CREATE TABLE `wm_batch`  (
  `batch_id` bigint(20) NOT NULL COMMENT '批次ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次编码',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `production_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '有效期',
  `quantity` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '批次数量',
  `received_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '已收数量',
  `issued_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '已发数量',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `source_doc_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `source_doc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源单据编号',
  `rule_id` bigint(20) NULL DEFAULT NULL COMMENT '生成规则ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`batch_id`) USING BTREE,
  UNIQUE INDEX `uk_batch_code`(`batch_code`) USING BTREE,
  INDEX `idx_batch_material`(`material_id`) USING BTREE,
  INDEX `idx_batch_vendor`(`vendor_id`) USING BTREE,
  INDEX `idx_batch_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_batch_status`(`status`) USING BTREE,
  INDEX `idx_batch_expiry`(`expiry_date`) USING BTREE,
  INDEX `idx_batch_source`(`source_doc_type`, `source_doc_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '批次管理表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 字典类型 (使用安全大ID段 500xxx 避免主键冲突)
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (500010, '000000', '批次状态', 'wm_batch_status', 103, 1, NOW(), NULL, NULL, '批次状态');

-- ----------------------------
-- 字典数据
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (5000101, '000000', 1, '有效', 'ACTIVE', 'wm_batch_status', '', 'success', 'Y', 103, 1, NOW(), NULL, NULL, '批次有效');
INSERT INTO `sys_dict_data` VALUES (5000102, '000000', 2, '过期', 'EXPIRED', 'wm_batch_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '批次过期');
INSERT INTO `sys_dict_data` VALUES (5000103, '000000', 3, '锁定', 'LOCKED', 'wm_batch_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '批次锁定');
INSERT INTO `sys_dict_data` VALUES (5000104, '000000', 4, '关闭', 'CLOSED', 'wm_batch_status', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '批次关闭');

-- ----------------------------
-- 菜单 SQL - 批次管理 (使用安全大ID段 50xx 避免主键冲突)
-- ----------------------------
-- 目录(M): 批次管理 (parent: 仓库管理 2100)
INSERT INTO `sys_menu` VALUES (5010, '批次管理', 2100, 12, 'batch', 'erp/wm/batch/index', NULL, 1, 0, 'C', '0', '0', 'wm:batch:list', 'cascader', 103, 1, NOW(), NULL, NULL, '批次管理菜单');
-- 按钮(F): 批次查询
INSERT INTO `sys_menu` VALUES (5011, '批次查询', 5010, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:batch:query', '#', 103, 1, NOW(), NULL, NULL, '');
-- 按钮(F): 批次新增
INSERT INTO `sys_menu` VALUES (5012, '批次新增', 5010, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:batch:add', '#', 103, 1, NOW(), NULL, NULL, '');
-- 按钮(F): 批次修改
INSERT INTO `sys_menu` VALUES (5013, '批次修改', 5010, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:batch:edit', '#', 103, 1, NOW(), NULL, NULL, '');
-- 按钮(F): 批次删除
INSERT INTO `sys_menu` VALUES (5014, '批次删除', 5010, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:batch:remove', '#', 103, 1, NOW(), NULL, NULL, '');
-- 按钮(F): 批次导出
INSERT INTO `sys_menu` VALUES (5015, '批次导出', 5010, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:batch:export', '#', 103, 1, NOW(), NULL, NULL, '');
