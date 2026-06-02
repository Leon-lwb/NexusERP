-- ----------------------------
-- 序列号管理表 wm_sn
-- ----------------------------
DROP TABLE IF EXISTS `wm_sn`;
CREATE TABLE `wm_sn`  (
  `sn_id` bigint(20) NOT NULL COMMENT '序列号ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `sn_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '序列号编码',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '关联批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联批次编码',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '当前仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '当前库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `stock_id` bigint(20) NULL DEFAULT NULL COMMENT '关联库存记录ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'IN_STOCK' COMMENT '状态',
  `source_doc_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `source_doc_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源单据编号',
  `rule_id` bigint(20) NULL DEFAULT NULL COMMENT '生成规则ID',
  `production_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '有效期',
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
  PRIMARY KEY (`sn_id`) USING BTREE,
  UNIQUE KEY `uk_sn_code`(`sn_code`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_batch_id`(`batch_id`) USING BTREE,
  INDEX `idx_warehouse_id`(`warehouse_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_stock_id`(`stock_id`) USING BTREE,
  INDEX `idx_source_doc`(`source_doc_type`, `source_doc_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '序列号管理表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 字典类型：序列号状态 (使用安全大ID段 500xxx 避免主键冲突)
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (500001, '000000', '序列号状态', 'wm_sn_status', 103, 1, NOW(), NULL, NULL, '序列号状态列表');

-- ----------------------------
-- 字典数据：序列号状态
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (500001, '000000', 1, '在库', 'IN_STOCK', 'wm_sn_status', '', 'success', 'Y', 103, 1, NOW(), NULL, NULL, '序列号在库');
INSERT INTO `sys_dict_data` VALUES (500002, '000000', 2, '出库', 'OUT', 'wm_sn_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '序列号出库');
INSERT INTO `sys_dict_data` VALUES (500003, '000000', 3, '报废', 'SCRAP', 'wm_sn_status', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '序列号报废');
INSERT INTO `sys_dict_data` VALUES (500004, '000000', 4, '预留', 'RESERVED', 'wm_sn_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '序列号预留');

-- ----------------------------
-- 菜单：序列号管理（目录M → 菜单C → 按钮F）
-- ----------------------------
INSERT INTO `sys_menu` VALUES (5000, '序列号管理', 2100, 10, 'sn', 'erp/wm/sn/index', NULL, 1, 0, 'C', '0', '0', 'wm:sn:list', 'code', 103, 1, NOW(), NULL, NULL, '序列号管理菜单');
INSERT INTO `sys_menu` VALUES (5001, '序列号查询', 5000, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:sn:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (5002, '序列号新增', 5000, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:sn:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (5003, '序列号修改', 5000, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:sn:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (5004, '序列号删除', 5000, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:sn:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (5005, '序列号导出', 5000, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:sn:export', '#', 103, 1, NOW(), NULL, NULL, '');
