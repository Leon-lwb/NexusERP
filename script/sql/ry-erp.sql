/*
 Navicat Premium Data Transfer

 Source Server         : 本地5.7
 Source Server Type    : MySQL
 Source Server Version : 50714 (5.7.14)
 Source Host           : localhost:3306
 Source Schema         : ry-erp

 Target Server Type    : MySQL
 Target Server Version : 50714 (5.7.14)
 File Encoding         : 65001

 Date: 06/07/2026 23:57:17
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for bd_bom
-- ----------------------------
DROP TABLE IF EXISTS `bd_bom`;
CREATE TABLE `bd_bom`  (
  `bom_id` bigint(20) NOT NULL COMMENT 'BOM主表ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `parent_material_id` bigint(20) NOT NULL COMMENT '父项物料ID',
  `bom_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1.0' COMMENT 'BOM版本号',
  `bom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BOM名称',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型',
  `bom_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MBOM' COMMENT 'BOM类型: EBOM-工程BOM, MBOM-制造BOM, SBOM-服务BOM, CBOM-配置BOM',
  `routing_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工艺路线ID',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `is_current` tinyint(4) NULL DEFAULT 0 COMMENT '是否当前版本 0:否 1:是',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
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
  PRIMARY KEY (`bom_id`) USING BTREE,
  UNIQUE INDEX `uk_parent_version`(`parent_material_id`, `bom_version`) USING BTREE,
  INDEX `idx_parent_material`(`parent_material_id`) USING BTREE,
  INDEX `idx_current`(`is_current`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_bom
-- ----------------------------
INSERT INTO `bd_bom` VALUES (1, '000000', 2, '1.0', '减速机JZQ-350标准版BOM', 'PROCESS', 'MBOM', NULL, '2024-01-01', '2025-12-31', 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:22:23', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom` VALUES (2, '000000', 2, '1.1', '减速机JZQ-350改进版BOM', 'PROCESS', 'MBOM', NULL, '2024-06-01', '2025-10-20', 0, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:22:23', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom` VALUES (3, '000000', 3, '1.0', '电机Y132M-4标准版BOM', 'PROCESS', 'MBOM', NULL, '2024-01-01', NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:22:23', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom` VALUES (4, '000000', 4, '1.0', '齿轮组标准版BOM', 'PROCESS', 'MBOM', NULL, '2024-01-01', NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:22:23', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom` VALUES (5, '000000', 1001, '1.0', '自行车标准版BOM', 'PROCESS', 'MBOM', NULL, '2024-01-01', '2026-12-31', 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:44:35', 1, '2026-05-15 18:28:50', '0');
INSERT INTO `bd_bom` VALUES (6, '000000', 2002, '1.0', '车轮标准版BOM', 'PROCESS', 'MBOM', NULL, '2024-01-01', NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:46:19', 1, '2026-05-21 10:24:40', '0');
INSERT INTO `bd_bom` VALUES (7, '000000', 2003, '1.0', '车把标准版BOM', 'PROCESS', 'MBOM', NULL, '2024-01-01', NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:47:37', 1, '2026-05-21 10:26:26', '0');
INSERT INTO `bd_bom` VALUES (9, '000000', 2001, '1.0', '车架标准版BOM', 'PROCESS', 'MBOM', NULL, '2025-11-01', '2026-11-01', 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-11-03 14:47:25', 1, '2026-05-15 18:29:09', '0');
INSERT INTO `bd_bom` VALUES (20, '000000', 8003, '1.0', '简易金属工具架标准版BOM', 'PROCESS', 'MBOM', 3, '2026-01-01', '2026-12-31', 1, 1, '工具架BOM: 侧板×2 + 连接件包×1', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 10:04:02', 1, '2026-06-30 10:04:02', '0');
INSERT INTO `bd_bom` VALUES (21, '000000', 8001, '1.0', '侧板组件标准版BOM', 'PROCESS', 'MBOM', NULL, '2026-01-01', '2026-12-31', 1, 1, '侧板BOM: 钢板×1.5kg', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 10:04:02', 1, '2026-06-30 10:04:02', '0');
INSERT INTO `bd_bom` VALUES (2057368028339740674, '000000', 3003, '1.0', '辐条标准BOM', 'PROCESS', 'MBOM', NULL, '2026-05-21', NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-05-21 15:48:57', 1, '2026-05-21 17:50:35', '0');

-- ----------------------------
-- Table structure for bd_bom_alternative
-- ----------------------------
DROP TABLE IF EXISTS `bd_bom_alternative`;
CREATE TABLE `bd_bom_alternative`  (
  `alternative_id` bigint(20) NOT NULL COMMENT '替代料ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `detail_id` bigint(20) NOT NULL COMMENT 'BOM明细ID',
  `bom_id` bigint(20) NOT NULL COMMENT 'BOM主表ID',
  `alt_material_id` bigint(20) NOT NULL COMMENT '替代物料ID',
  `alt_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '替代物料编码',
  `alt_material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '替代物料名称',
  `alt_quantity` decimal(18, 6) NOT NULL COMMENT '替代用量',
  `priority` int(11) NULL DEFAULT 1 COMMENT '优先级(数字越小优先级越高)',
  `substitution_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'FULL' COMMENT '替代类型: FULL-完全替代, PARTIAL-部分替代, INTERCHANGEABLE-互换',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`alternative_id`) USING BTREE,
  INDEX `idx_detail_id`(`detail_id`) USING BTREE,
  INDEX `idx_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_alt_material`(`alt_material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM替代料表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_bom_alternative
-- ----------------------------

-- ----------------------------
-- Table structure for bd_bom_detail
-- ----------------------------
DROP TABLE IF EXISTS `bd_bom_detail`;
CREATE TABLE `bd_bom_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT 'BOM明细ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `bom_id` bigint(20) NOT NULL COMMENT 'BOM主表ID',
  `child_material_id` bigint(20) NOT NULL COMMENT '子项物料ID',
  `quantity_per_parent` decimal(18, 6) NOT NULL COMMENT '单位父项用量',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `operation_step` int(11) NULL DEFAULT NULL COMMENT '工序步骤',
  `lead_time` int(11) NULL DEFAULT 0 COMMENT '提前期(小时)',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
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
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_bom_id`(`bom_id`) USING BTREE,
  INDEX `idx_child_material`(`child_material_id`) USING BTREE,
  INDEX `idx_operation_step`(`operation_step`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BOM明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_bom_detail
-- ----------------------------
INSERT INTO `bd_bom_detail` VALUES (1, '000000', 1, 6, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (2, '000000', 1, 7, 4.000000, 'PROCESS', 2.0000, 10, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (3, '000000', 1, 5, 1.000000, 'PROCESS', 0.0000, 20, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (4, '000000', 1, 8, 2.000000, 'PROCESS', 1.0000, 20, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (5, '000000', 1, 2, 1.000000, 'PROCESS', 0.0000, 30, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (6, '000000', 1, 9, 2.000000, 'PROCESS', 0.0000, 30, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (7, '000000', 1, 10, 0.500000, 'PROCESS', 0.0000, 40, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (8, '000000', 2, 6, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (9, '000000', 2, 7, 6.000000, 'PROCESS', 2.0000, 10, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (10, '000000', 2, 5, 1.000000, 'PROCESS', 0.0000, 20, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (11, '000000', 2, 8, 2.000000, 'PROCESS', 1.0000, 20, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (12, '000000', 2, 2, 1.000000, 'PROCESS', 0.0000, 30, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (13, '000000', 2, 9, 2.000000, 'PROCESS', 0.0000, 30, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (14, '000000', 2, 10, 0.600000, 'PROCESS', 0.0000, 40, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (15, '000000', 3, 11, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (16, '000000', 3, 12, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (17, '000000', 3, 13, 1.000000, 'PROCESS', 0.0000, 20, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (18, '000000', 3, 14, 2.000000, 'PROCESS', 0.0000, 20, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (19, '000000', 3, 15, 4.000000, 'PROCESS', 1.0000, 30, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (20, '000000', 4, 16, 3.000000, 'PROCESS', 2.0000, 10, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (21, '000000', 4, 17, 2.000000, 'PROCESS', 2.0000, 10, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (22, '000000', 4, 18, 5.000000, 'PROCESS', 1.0000, 20, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:29:00', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (23, '000000', 1, 16, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-21 10:53:59', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (24, '000000', 1, 16, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 0, 0, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-21 10:55:17', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (25, '000000', 1, 2, 1.000000, 'PROCESS', 0.0000, 1, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-21 10:58:37', 1, '2026-06-28 18:53:49', '1');
INSERT INTO `bd_bom_detail` VALUES (26, '000000', 2, 3, 1.000000, 'PROCESS', 0.0000, 1, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-21 11:02:31', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (27, '000000', 2, 3, 1.000000, 'PROCESS', 0.0000, 1, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-21 11:03:42', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (28, '000000', 5, 2001, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:45:40', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (29, '000000', 5, 2002, 2.000000, 'PROCESS', 2.0000, 20, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:45:40', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (30, '000000', 5, 2003, 1.000000, 'PROCESS', 1.0000, 30, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:45:40', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (31, '000000', 6, 3001, 1.000000, 'PROCESS', 1.0000, 10, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:47:04', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (32, '000000', 6, 3002, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:47:04', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (33, '000000', 6, 3003, 36.000000, 'PROCESS', 3.0000, 20, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:47:04', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (34, '000000', 7, 3004, 1.000000, 'PROCESS', 0.5000, 10, 0, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:48:08', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (35, '000000', 7, 3005, 2.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 2, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:48:08', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (36, '000000', 9, 1, 1.000000, 'PROCESS', 0.0000, 1, 1, '2025-11-01', '2026-11-01', 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-11-03 14:49:06', 1, '2026-06-28 18:53:49', '0');
INSERT INTO `bd_bom_detail` VALUES (37, '000000', 5, 10, 1.000000, 'PROCUREMENT', 0.0000, 40, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2025-11-27 17:45:55', 1, '2026-05-21 10:33:38', '0');
INSERT INTO `bd_bom_detail` VALUES (100, '000000', 20, 8001, 2.000000, 'PROCESS', 0.0000, 30, 1, '2026-01-01', '2026-12-31', 10, 1, '每台工具架需要2件侧板', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 10:04:08', 1, '2026-06-30 10:04:08', '0');
INSERT INTO `bd_bom_detail` VALUES (101, '000000', 20, 8002, 1.000000, 'PROCUREMENT', 0.0000, 30, 1, '2026-01-01', '2026-12-31', 20, 1, '每台工具架需要1套连接件', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 10:04:08', 1, '2026-06-30 10:04:08', '0');
INSERT INTO `bd_bom_detail` VALUES (102, '000000', 21, 1, 1.500000, 'PROCUREMENT', 2.0000, 10, 2, '2026-01-01', '2026-12-31', 10, 1, '每件侧板需要1.5kg钢板（损耗2%）', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 10:04:08', 1, '2026-06-30 10:04:08', '0');
INSERT INTO `bd_bom_detail` VALUES (2057371558593089537, '000000', 2057368028339740674, 1, 1.000000, 'PROCESS', 3.0000, 4, 2, '2026-05-21', NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-05-21 16:02:58', 1, '2026-05-21 17:50:35', '1');
INSERT INTO `bd_bom_detail` VALUES (2057398640882688002, '000000', 2057368028339740674, 1, 0.001000, 'PROCESS', 3.0000, 4, 2, '2026-05-21', NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-05-21 17:50:35', 1, '2026-05-21 17:50:35', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815687536642, '000000', 1, 6, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815687536643, '000000', 1, 7, 4.000000, 'PROCESS', 2.0000, 10, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815687536644, '000000', 1, 5, 1.000000, 'PROCESS', 0.0000, 20, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815687536645, '000000', 1, 8, 2.000000, 'PROCESS', 1.0000, 20, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815687536646, '000000', 1, 2, 1.000000, 'PROCESS', 0.0000, 30, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815754645506, '000000', 1, 9, 2.000000, 'PROCESS', 0.0000, 30, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815754645507, '000000', 1, 10, 0.500000, 'PROCESS', 0.0000, 40, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815754645508, '000000', 1, 16, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815754645509, '000000', 1, 16, 1.000000, 'PROCESS', 0.0000, 10, 0, NULL, NULL, 0, 0, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');
INSERT INTO `bd_bom_detail` VALUES (2061811815754645510, '000000', 1, 2, 1.000000, 'PROCESS', 0.0000, 1, 0, NULL, NULL, 0, 1, '', NULL, NULL, NULL, NULL, 103, 1, '2026-06-02 22:06:58', 1, '2026-06-02 22:06:58', '0');

-- ----------------------------
-- Table structure for bd_client
-- ----------------------------
DROP TABLE IF EXISTS `bd_client`;
CREATE TABLE `bd_client`  (
  `client_id` bigint(20) NOT NULL COMMENT '客户ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称',
  `client_nick` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户简写',
  `client_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户英文名称',
  `client_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户简介',
  `client_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ENTERPRISE' COMMENT '客户类型',
  `client_logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户LOGO地址',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户默认送货地点',
  `web_site` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户官网地址',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户邮箱',
  `tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户电话',
  `contact1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人1',
  `contact1_tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人1电话',
  `contact1_emal` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人1邮箱',
  `contact2` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人2',
  `contact2_tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人2电话',
  `contact2_emal` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人2邮箱',
  `credit_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '统一社会信用代码',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `province_area_id` bigint(20) NULL DEFAULT NULL COMMENT '省份行政区划ID',
  `city_area_id` bigint(20) NULL DEFAULT NULL COMMENT '城市行政区划ID',
  `district_area_id` bigint(20) NULL DEFAULT NULL COMMENT '区县行政区划ID',
  `street_id` bigint(20) NULL DEFAULT NULL COMMENT '街道行政区划ID',
  `address_lat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址纬度',
  `address_lng` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址经度',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '业务员ID（关联sys_user）',
  `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '业务员名称',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '归属部门ID（关联sys_dept）',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归属部门名称',
  PRIMARY KEY (`client_id`) USING BTREE,
  INDEX `idx_province_area`(`province_area_id`) USING BTREE,
  INDEX `idx_city_area`(`city_area_id`) USING BTREE,
  INDEX `idx_district_area`(`district_area_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_client
-- ----------------------------
INSERT INTO `bd_client` VALUES (1, '000000', 'C00001', '测试', '测试', 'cs', '测试测试', 'ENTERPRISE', '/profile/client_logo//2025/09/22/883112fba9d74b63857adfcade752ad6.png', '浙江省杭州市上城区彭埠街道', NULL, '123', '123', '123', '123', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 330000, 330100, 330102, 330102015, NULL, NULL, 103, 1, '2025-09-18 15:45:44', 1, '2025-11-02 16:14:00', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (2, '000000', 'C00003', '小明科技有限公司', '小明科技', 'Xiaoming Tech Co., Ltd', '测试', 'ENTERPRISE', NULL, '深圳市南山区科技园南区W1-A栋', NULL, 'contact@xiaomingtech.com', '0755-88888888', '李明', '13800138001', 'liming@xiaomingtech.com', NULL, NULL, NULL, '91440300MA5FXXXXXX', '门户网站客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-01-15 10:30:00', 1, '2025-10-21 17:46:34', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (3, '000000', 'C00004', '王芳贸易公司', '王芳贸易', 'Wangfang Trading Co., Ltd', '测试', 'ENTERPRISE', NULL, '广州市天河区珠江新城商务区', NULL, 'info@wangfang.com', '020-66666666', '王芳', '13900139002', 'wangfang@wangfang.com', NULL, NULL, NULL, '91440100MA5GXXXXXX', '门户网站客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-02-20 14:15:00', 1, '2025-10-21 17:46:42', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (4, '000000', 'C00005', '张伟实业集团', '张伟集团', 'Zhangwei Industries Group', '测试', 'ENTERPRISE', NULL, '上海市浦东新区陆家嘴金融中心', NULL, 'group@zhangwei.com', '021-77777777', '张伟', '13700137003', 'zhangwei@zhangwei.com', NULL, NULL, NULL, '91310000MA5HXXXXXX', '门户网站客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-03-10 09:45:00', 1, '2025-10-21 17:46:49', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (5, '000000', 'C00006', '陈氏机械制造有限公司', '陈氏机械', 'Chen Machinery Co., Ltd', '测试', 'ENTERPRISE', NULL, '北京市朝阳区经济技术开发区', NULL, 'sales@chenmachinery.com', '010-55555555', '陈总', '13600136004', 'chen@chenmachinery.com', NULL, NULL, NULL, '91110000MA5JXXXXXX', '销售代表客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-04-05 11:20:00', 1, '2025-10-21 17:46:58', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (6, '000000', 'C00007', '刘氏电子科技有限公司', '刘氏电子', 'Liu Electronics Co., Ltd', '测试', 'ENTERPRISE', NULL, '杭州市滨江区物联网小镇', NULL, 'info@liuelectronics.com', '0571-33333333', '刘经理', '13500135005', 'liu@liuelectronics.com', NULL, NULL, NULL, '91330000MA5KXXXXXX', '销售代表客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-05-12 13:40:00', 1, '2025-10-21 17:47:06', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (7, '000000', 'C00008', '赵氏工程公司', '赵氏工程', 'Zhao Engineering Co., Ltd', '测试', 'ENTERPRISE', NULL, '成都市高新区天府软件园', NULL, 'contact@zhaoengineering.com', '028-22222222', '赵主任', '13400134006', 'zhao@zhaoengineering.com', NULL, NULL, NULL, '91510000MA5LXXXXXX', '销售代表客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-06-18 15:30:00', 1, '2025-10-21 17:47:12', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (8, '000000', 'C00009', '沃尔玛（中国）投资有限公司', '沃尔玛', 'Walmart (China) Investment Co., Ltd', '测试', 'ENTERPRISE', NULL, '深圳市罗湖区深南东路5016号', NULL, 'procurement@walmart.cn', '0755-11111111', '采购部', '0755-11111112', 'purchase@walmart.cn', NULL, NULL, NULL, '914403007152XXXXXX', 'EDI客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-07-01 08:00:00', 1, '2025-10-21 17:47:19', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (9, '000000', 'C00010', '好市多（中国）投资有限公司', '好市多', 'Costco (China) Investment Co., Ltd', '测试', 'ENTERPRISE', NULL, '上海市闵行区吴中路1599号', NULL, 'buying@costco.cn', '021-44444444', '采购经理', '021-44444445', 'buying@costco.cn', NULL, NULL, NULL, '913100008888XXXXXX', 'EDI客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-08-15 09:15:00', 1, '2025-10-21 17:47:25', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (10, '000000', 'C00011', '家乐福（中国）投资有限公司', '家乐福', 'Carrefour (China) Investment Co., Ltd', '测试', 'ENTERPRISE', NULL, '北京市朝阳区霄云路甲26号', NULL, 'procurement@carrefour.cn', '010-99999999', '供应链经理', '010-99999998', 'supplychain@carrefour.cn', NULL, NULL, NULL, '911100009999XXXXXX', 'EDI客户', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2024-09-20 10:30:00', 1, '2025-10-21 17:47:33', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_client` VALUES (11, '000000', 'C00012', '测试五金贸易公司', '五金贸易', 'Test Hardware Trading Co., Ltd', '进货测试客户', 'ENTERPRISE', NULL, '浙江省杭州市滨江区测试路100号', NULL, 'test@hardware.com', '0571-88886666', '张三', '13800138001', 'zhangsan@hardware.com', NULL, NULL, NULL, '91330100MA8TEST001', '工具架销售测试客户', NULL, NULL, NULL, NULL, 330000, 330100, 330108, NULL, NULL, NULL, 103, 1, '2026-06-30 10:12:40', 1, '2026-06-30 10:12:40', '0', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for bd_client_credit
-- ----------------------------
DROP TABLE IF EXISTS `bd_client_credit`;
CREATE TABLE `bd_client_credit`  (
  `credit_id` bigint(20) NOT NULL COMMENT '客户信用ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `client_id` bigint(20) NOT NULL COMMENT '客户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称',
  `client_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户英文名称',
  `client_credit` int(11) NULL DEFAULT NULL COMMENT '信用额度',
  `client_time` int(11) NULL DEFAULT NULL COMMENT '账期',
  `client_level` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL' COMMENT '客户等级',
  `client_score` int(11) NOT NULL DEFAULT 60 COMMENT '客户评分',
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
  PRIMARY KEY (`credit_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_client_code`(`client_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户信用表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_client_credit
-- ----------------------------
INSERT INTO `bd_client_credit` VALUES (1, '000000', 1, 'C00001', '测试', NULL, 500000, 30, 'VIP', 95, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_client_credit` VALUES (2, '000000', 2, 'C00003', '小明科技有限公司', NULL, 300000, 45, 'NORMAL', 80, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_client_credit` VALUES (3, '000000', 3, 'C00004', '王芳贸易公司', NULL, 200000, 30, 'NORMAL', 75, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_client_credit` VALUES (4, '000000', 5, 'C00006', '陈氏机械制造有限公司', NULL, 500000, 60, 'VIP', 90, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_client_credit` VALUES (5, '000000', 8, 'C00009', '沃尔玛（中国）投资有限公司', NULL, 1000000, 30, 'VIP', 98, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_client_credit` VALUES (6, '000000', 11, 'C00012', '测试五金贸易公司', NULL, 50000, 30, 'NORMAL', 80, NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 10:12:40', 1, '2026-06-30 10:12:40', '0');

-- ----------------------------
-- Table structure for bd_material
-- ----------------------------
DROP TABLE IF EXISTS `bd_material`;
CREATE TABLE `bd_material`  (
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `material_desc` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料描述',
  `category_id` bigint(20) NOT NULL COMMENT '物料分类ID',
  `base_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '基本计量单位ID',
  `purchase_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '采购单位ID',
  `inventory_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '库存单位ID',
  `sale_uom_id` bigint(20) NULL DEFAULT NULL COMMENT '销售单位ID',
  `material_type` tinyint(4) NOT NULL COMMENT '物料类型 1:原材料 2:半成品 3:成品 4:辅料',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PROCESS' COMMENT '来源类型: PROCESS-加工 PROCUREMENT-采购 SUBCONTRACT-委外 PHANTOM-虚拟件 CFM-客户供料 TRANSFER-调拨',
  `is_batch_manage` tinyint(4) NULL DEFAULT 0 COMMENT '是否批次管理 0:否 1:是',
  `is_serial_manage` tinyint(4) NULL DEFAULT 0 COMMENT '是否序列号管理 0:否 1:是',
  `safety_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '安全库存',
  `min_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最低库存',
  `max_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最高库存',
  `standard_cost` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '标准成本',
  `reference_price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '参考价格',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `quality_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '质量状态',
  `shelf_life` int(11) NULL DEFAULT 0 COMMENT '保质期(天)',
  `weight` decimal(12, 4) NULL DEFAULT 0.0000 COMMENT '重量(kg)',
  `volume` decimal(12, 4) NULL DEFAULT 0.0000 COMMENT '体积(m³)',
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
  `is_phantom` tinyint(1) NULL DEFAULT 0 COMMENT '是否虚拟件: 0-否 1-是（虚拟件展开时跳层，不入库不生成工单）',
  PRIMARY KEY (`material_id`) USING BTREE,
  UNIQUE INDEX `uk_material_code`(`material_code`) USING BTREE,
  INDEX `idx_category_id`(`category_id`) USING BTREE,
  INDEX `idx_material_type`(`material_type`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_base_uom`(`base_uom_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料产品主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_material
-- ----------------------------
INSERT INTO `bd_material` VALUES (1, '000000', 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '长方形钢板', 5, 2, 3, 3, 2, 1, 'PROCUREMENT', 1, 1, 10.0000, 15.0000, 45.0000, 1000.0000, 1000.0000, 1, 'QUALIFIED', 0, 100.0000, 0.0100, '采购钢板', NULL, NULL, NULL, NULL, 103, 1, '2025-10-16 17:20:58', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (2, '000000', 'IF20251018001', '电机Y132M-4 7.5KW', '7.5KW 380V', NULL, 3, 2, NULL, NULL, NULL, 3, 'PROCUREMENT', 1, 0, 2.0000, 3.0000, 9.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 365, 2.0000, 3.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:14:02', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (3, '000000', 'JZQ350', '减速机JZQ-350', '标准型号', NULL, 3, 1, NULL, NULL, NULL, 3, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (4, '000000', 'Y132M4', '电机Y132M-4 7.5KW', '7.5KW 380V', NULL, 3, 1, NULL, NULL, NULL, 3, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (5, '000000', 'GEAR_SET', '齿轮组', '标准齿轮组', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (6, '000000', 'HOUSING', '箱体', '铸铁箱体', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (7, '000000', 'BOLT_M12', '螺栓M12', '8.8级', NULL, 10, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (8, '000000', 'BEARING6204', '轴承6204', '6204型号', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (9, '000000', 'KEY', '平键', '10×8×50', NULL, 10, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (10, '000000', 'LUBRICANT', '润滑油', 'ISO VG46', NULL, 11, 29, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (11, '000000', 'STATOR', '定子', '电机定子', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (12, '000000', 'ROTOR', '转子', '电机转子', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (13, '000000', 'END_CAP', '端盖', '电机端盖', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (14, '000000', 'BEARING6204_2', '轴承6204', '6204型号', NULL, 1, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (15, '000000', 'SCREW_M6', '螺丝M6', '不锈钢', NULL, 10, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (16, '000000', 'LARGE_GEAR', '大齿轮', '模数3 齿数50', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (17, '000000', 'SMALL_GEAR', '小齿轮', '模数3 齿数20', NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (18, '000000', 'KEY_2', '键', '普通平键', NULL, 10, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-18 23:15:40', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (1001, '000000', 'IF20251110001', '自行车', '1.5m长0.5m高', '儿童自行车', 3, 26, 26, 26, 26, 3, 'PROCESS', 0, 0, 3.0000, 4.5000, 13.5000, 0.0000, 7.0000, 1, 'QUALIFIED', 364, 6.0000, 0.2000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (2001, '000000', 'IF20251203001', '车架', NULL, NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (2002, '000000', 'IF20251203002', '车轮', NULL, NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (2003, '000000', 'IF20251203003', '车把', NULL, NULL, 2, 1, NULL, NULL, NULL, 2, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (3001, '000000', 'IF20251203004', '轮圈', NULL, NULL, 1, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (3002, '000000', 'IF20251203005', '轮胎', NULL, NULL, 1, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (3003, '000000', 'IF20251203006', '辐条', NULL, NULL, 1, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (3004, '000000', 'IF20251203007', '把管', NULL, NULL, 1, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (3005, '000000', 'IF20251203008', '把套', NULL, NULL, 1, 1, NULL, NULL, NULL, 1, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 'QUALIFIED', 0, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-10-26 19:43:42', 1, '2026-06-28 18:53:42', '0', 0);
INSERT INTO `bd_material` VALUES (8001, '000000', 'RACK-SIDE', '侧板组件', '300mm*200mm*2mm', '工具架金属侧板，经切割焊接制成', 2, 26, NULL, 26, NULL, 3, 'PROCESS', 0, 0, 0.0000, 0.0000, 0.0000, 15.0000, NULL, 1, 'QUALIFIED', 0, 1.5000, 0.0010, '工具架侧板，每台需2件', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 09:40:30', 1, '2026-06-30 09:40:30', '0', 0);
INSERT INTO `bd_material` VALUES (8002, '000000', 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '包含安装所需全套五金件', 10, 28, 28, 28, 28, 3, 'PROCUREMENT', 0, 0, 10.0000, 0.0000, 0.0000, 8.0000, 12.0000, 1, 'QUALIFIED', 0, 0.2000, 0.0005, '五金连接件套装', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 09:40:30', 1, '2026-06-30 09:40:30', '0', 0);
INSERT INTO `bd_material` VALUES (8003, '000000', 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', '简易金属置物架，承重30kg', 3, 27, 27, 27, 27, 3, 'PROCESS', 1, 1, 5.0000, 0.0000, 0.0000, 80.0000, 200.0000, 1, 'QUALIFIED', 0, 5.0000, 0.0500, '测试用五金产品', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 09:40:30', 1, '2026-06-30 11:45:23', '0', 0);

-- ----------------------------
-- Table structure for bd_material_category
-- ----------------------------
DROP TABLE IF EXISTS `bd_material_category`;
CREATE TABLE `bd_material_category`  (
  `category_id` bigint(20) NOT NULL COMMENT '分类ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `parent_id` bigint(20) NULL DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
  `category_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `category_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类描述',
  `level_num` int(11) NULL DEFAULT 1 COMMENT '层级深度，1为顶级',
  `path_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路径ID串，格式：1,5,23',
  `path_names` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路径名称串，格式：原材料,金属材料,钢材',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
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
  PRIMARY KEY (`category_id`) USING BTREE,
  UNIQUE INDEX `uk_category_code`(`category_code`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_level_sort`(`level_num`, `sort_order`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料产品分类表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_material_category
-- ----------------------------
INSERT INTO `bd_material_category` VALUES (1, '000000', 0, 'RAW', '原材料', NULL, 1, '1', '原材料', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 12:46:31', NULL, '2025-10-17 18:04:57', '0');
INSERT INTO `bd_material_category` VALUES (2, '000000', 0, 'SEMI', '半成品', NULL, 1, '2', '半成品', 2, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 12:46:31', NULL, '2025-10-17 18:04:49', '0');
INSERT INTO `bd_material_category` VALUES (3, '000000', 0, 'FINISH', '产成品', NULL, 1, '3', '产成品', 3, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 12:46:31', NULL, '2025-11-03 10:52:13', '0');
INSERT INTO `bd_material_category` VALUES (4, '000000', 0, 'AUX', '辅助材料', NULL, 1, '4', '辅助材料', 4, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 12:46:31', NULL, '2025-10-17 18:05:09', '0');
INSERT INTO `bd_material_category` VALUES (5, '000000', 1, 'METAL', '金属材料', NULL, 2, '1,5', '原材料,金属材料', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 12:46:31', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (6, '000000', 1, 'PLASTIC', '塑料材料', NULL, 2, '1,6', '原材料,塑料材料', 2, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 12:46:31', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (7, '000000', 1, 'CHEMICAL', '化工材料', NULL, 2, '1,7', '原材料,化工材料', 3, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 12:46:31', NULL, '2025-10-17 18:05:03', '0');
INSERT INTO `bd_material_category` VALUES (8, '000000', 7, 'OIL', '石油', NULL, 3, '1,7,8', '原材料,化工材料,石油', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-01 13:13:03', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (10, '000000', 1, 'FASTENER', '紧固件', NULL, 2, '1,10', '原材料,紧固件', 4, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-18 23:09:31', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (11, '000000', 1, 'LUBRICANT', '润滑材料', NULL, 2, '1,11', '原材料,润滑材料', 5, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-18 23:09:57', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (12, '000000', 1, 'ELECTRIC', '电气材料', NULL, 2, '1,12', '原材料,电气材料', 6, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-10-18 23:10:27', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (14, '000000', 1, 'TIMBER', '木材', NULL, 2, '1,14', '原材料,木材', 6, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-11-03 11:05:08', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (15, '000000', 6, 'PE', '聚乙烯', 'HDPE常用于水桶、瓶盖等', 3, '1,6,15', '原材料,塑料材料,聚乙烯', 1, 1, '具有良好的耐腐蚀性和电绝缘性', NULL, NULL, NULL, NULL, NULL, NULL, '2025-11-03 11:24:29', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (16, '000000', 6, 'PVC', '聚氯乙烯', '广泛用于建筑材料', 3, '1,6,16', '原材料,塑料材料,聚氯乙烯', 2, 1, '具有良好的耐化学性和耐磨性', NULL, NULL, NULL, NULL, NULL, NULL, '2025-11-03 11:40:42', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (17, '000000', 6, 'PP', '聚丙烯', '常用于食品容器', 3, '1,6,17', '原材料,塑料材料,聚丙烯', 3, 1, '轻质、耐热性好', NULL, NULL, NULL, NULL, NULL, NULL, '2025-11-03 11:43:52', NULL, '2026-06-25 23:33:58', '0');
INSERT INTO `bd_material_category` VALUES (18, '000000', 6, 'PS', '聚苯乙烯', '常用于一次性餐具', 3, '1,6,18', '原材料,塑料材料,聚苯乙烯', 4, 1, '透明度高，易于加工', NULL, NULL, NULL, NULL, NULL, NULL, '2025-11-03 11:48:33', NULL, '2026-06-25 23:33:58', '0');

-- ----------------------------
-- Table structure for bd_material_vendor
-- ----------------------------
DROP TABLE IF EXISTS `bd_material_vendor`;
CREATE TABLE `bd_material_vendor`  (
  `relation_id` bigint(20) NOT NULL COMMENT '关系ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `vendor_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `vendor_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `vendor_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `lead_time` int(11) NULL DEFAULT 0 COMMENT '供货周期(天)',
  `min_order_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最小订货量',
  `price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '单价',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CNY' COMMENT '币种',
  `is_default` tinyint(4) NULL DEFAULT 0 COMMENT '是否默认供应商 0:否 1:是',
  `priority` int(11) NULL DEFAULT 0 COMMENT '优先级',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
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
  PRIMARY KEY (`relation_id`) USING BTREE,
  UNIQUE INDEX `uk_material_supplier`(`material_id`, `vendor_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_supplier_id`(`vendor_id`) USING BTREE,
  INDEX `idx_default`(`is_default`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料供应商关系表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_material_vendor
-- ----------------------------
INSERT INTO `bd_material_vendor` VALUES (40001, '000000', 1, 2062098763051089921, 'V00001', '钢板产出商', 7, 10.0000, 4500.0000, 'CNY', 1, 1, '2026-01-01', '2026-12-31', 1, '钢板主力供应商', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_material_vendor` VALUES (40002, '000000', 8, 2062098763051089922, 'V00002', '华东轴承有限公司', 5, 100.0000, 25.0000, 'CNY', 1, 1, '2026-01-01', '2026-12-31', 1, '轴承主力供应商', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_material_vendor` VALUES (40003, '000000', 2, 2062098763051089923, 'V00003', '深圳精密电机制造厂', 10, 5.0000, 3500.0000, 'CNY', 1, 1, '2026-01-01', '2026-12-31', 1, '电机主力供应商', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_material_vendor` VALUES (40004, '000000', 10, 2062098763051089924, 'V00004', '天津化工材料有限公司', 3, 20.0000, 85.0000, 'CNY', 1, 1, '2026-01-01', '2026-12-31', 1, '润滑油供应商', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_material_vendor` VALUES (40005, '000000', 3001, 2062098763051089921, 'V00001', '钢板产出商', 5, 50.0000, 35.0000, 'CNY', 1, 1, '2026-01-01', '2026-12-31', 1, '轮圈供应商', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_material_vendor` VALUES (40006, '000000', 3002, 2062098763051089921, 'V00001', '钢板产出商', 5, 100.0000, 15.0000, 'CNY', 1, 1, '2026-01-01', '2026-12-31', 1, '轮胎供应商', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_material_vendor` VALUES (40007, '000000', 8002, 2062098763051089925, 'V00005', '宏达五金有限公司', 3, 1.0000, 12.0000, 'CNY', 1, 1, '2026-01-01', '2026-12-31', 1, '连接件包默认供应商', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 10:12:48', 1, '2026-06-30 10:12:48', '0');

-- ----------------------------
-- Table structure for bd_process
-- ----------------------------
DROP TABLE IF EXISTS `bd_process`;
CREATE TABLE `bd_process`  (
  `process_id` bigint(20) NOT NULL COMMENT '工序ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序名称',
  `process_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序描述',
  `process_category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序类别: PROCESSING-加工, ASSEMBLY-装配, INSPECTION-检验, PACKAGING-包装',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '默认工作中心ID',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '默认工作中心编码',
  `workstation_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '默认工作中心名称',
  `standard_hours` decimal(10, 4) NULL DEFAULT NULL COMMENT '标准工时(小时/件)',
  `setup_hours` decimal(10, 4) NULL DEFAULT NULL COMMENT '准备工时(小时)',
  `labor_count` int(11) NULL DEFAULT NULL COMMENT '标准人工数',
  `machine_count` int(11) NULL DEFAULT NULL COMMENT '标准机台数',
  `is_key_process` tinyint(1) NULL DEFAULT 0 COMMENT '是否关键工序: 0-否 1-是',
  `is_outsource` tinyint(1) NULL DEFAULT 0 COMMENT '是否委外工序: 0-否 1-是',
  `quality_required` tinyint(1) NULL DEFAULT 0 COMMENT '是否需要质检: 0-否 1-是',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`process_id`) USING BTREE,
  UNIQUE INDEX `uk_process_code`(`process_code`, `tenant_id`) USING BTREE,
  INDEX `idx_workstation`(`workstation_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工序定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_process
-- ----------------------------
INSERT INTO `bd_process` VALUES (1, '000000', 'CUT', '切割', '板材切割工序', 'PROCESSING', 13, 'GZZ0014', '切割工位', 0.5000, 0.2500, 1, 1, 0, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-16 17:25:27', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (2, '000000', 'WELD', '焊接', '金属焊接工序', 'PROCESSING', 11, 'GZZ0012', '焊接工位A', 1.0000, 0.5000, 2, 1, 1, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-16 17:25:27', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (3, '000000', 'ASM', '装配', '产品装配工序', 'ASSEMBLY', 6, 'GZZ0007', '装配线A', 2.0000, 0.5000, 2, 0, 1, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-16 17:25:27', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (4, '000000', 'QC', '质检', '成品质量检验', 'INSPECTION', 16, 'GZZ0017', '质检站', 0.5000, 0.1000, 1, 0, 1, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-16 17:25:27', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (5, '000000', 'PKG', '包装', '成品包装工序', 'PACKAGING', 9, 'GZZ0010', '包装线A', 0.2500, 0.1000, 1, 0, 0, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-16 17:25:27', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (6, '000000', 'MOLD', '注塑成型', '塑料件注塑成型', 'PROCESSING', 4, 'GZZ0005', '注塑机A', 0.3000, 1.0000, 1, 1, 1, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (7, '000000', 'PUNCH', '冲压成型', '金属板材冲压', 'PROCESSING', 2, 'GZZ0003', '工作站2', 0.2000, 0.5000, 1, 1, 1, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (8, '000000', 'SPRAY', '喷涂', '表面喷涂处理', 'PROCESSING', 14, 'GZZ0015', '喷涂线A', 0.4000, 0.3000, 2, 1, 0, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (9, '000000', 'DRY', '烘干固化', '涂层烘干固化', 'PROCESSING', 15, 'GZZ0016', '烘干炉', 0.5000, 0.1000, 0, 1, 0, 0, 0, 1, NULL, NULL, NULL, NULL, NULL, 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_process` VALUES (10, '000000', 'TIGWELD', '氩弧焊', '精密氩弧焊接', 'PROCESSING', 12, 'GZZ0013', '焊接工位B', 1.5000, 0.5000, 1, 1, 1, 0, 1, 1, NULL, NULL, NULL, NULL, NULL, 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');

-- ----------------------------
-- Table structure for bd_routing
-- ----------------------------
DROP TABLE IF EXISTS `bd_routing`;
CREATE TABLE `bd_routing`  (
  `routing_id` bigint(20) NOT NULL COMMENT '工艺路线ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `routing_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工艺路线编码',
  `routing_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工艺路线名称',
  `routing_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工艺路线描述',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '适用物料ID(可为空表示通用)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料名称',
  `routing_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1.0' COMMENT '版本号',
  `is_current` tinyint(1) NULL DEFAULT 1 COMMENT '是否当前版本: 0-否 1-是',
  `total_steps` int(11) NULL DEFAULT 0 COMMENT '总工序数',
  `total_hours` decimal(10, 4) NULL DEFAULT NULL COMMENT '总标准工时(小时)',
  `effective_date` date NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`routing_id`) USING BTREE,
  UNIQUE INDEX `uk_routing_code_version`(`routing_code`, `routing_version`, `tenant_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_current`(`is_current`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工艺路线主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_routing
-- ----------------------------
INSERT INTO `bd_routing` VALUES (1, '000000', 'RT-BIKE-001', '自行车装配工艺路线V1.0', '标准自行车装配流程', 1001, 'IF20251110001', '自行车', '1.0', 1, 4, 2.5000, '2026-01-01', '2026-12-31', 1, '自行车成品装配路线', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_routing` VALUES (2, '000000', 'RT-JZQ-001', '减速机JZQ-350装配工艺路线V1.0', '标准减速机装配流程', 3, 'JZQ350', '减速机JZQ-350', '1.0', 1, 4, 4.0000, '2026-01-01', '2026-12-31', 1, '减速机成品装配路线', NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_routing` VALUES (3, '000000', 'RT-RACK-001', '简易金属工具架装配工艺路线V1.0', '标准工具架装配流程: 切割→焊接→装配→包装', 8003, 'RACK-001', '简易金属工具架', '1.0', 1, 4, 1.2500, '2026-01-01', '2026-12-31', 1, '标准工艺路线', NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 09:41:25', 1, '2026-06-30 09:41:25', '0');

-- ----------------------------
-- Table structure for bd_routing_step
-- ----------------------------
DROP TABLE IF EXISTS `bd_routing_step`;
CREATE TABLE `bd_routing_step`  (
  `step_id` bigint(20) NOT NULL COMMENT '步骤ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `routing_id` bigint(20) NOT NULL COMMENT '工艺路线ID',
  `sequence_num` int(11) NOT NULL COMMENT '步骤序号',
  `process_id` bigint(20) NOT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `step_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '步骤名称',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID(覆盖工序默认)',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心编码',
  `workstation_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心名称',
  `standard_hours` decimal(10, 4) NULL DEFAULT NULL COMMENT '标准工时(覆盖工序默认)',
  `setup_hours` decimal(10, 4) NULL DEFAULT NULL COMMENT '准备工时(覆盖工序默认)',
  `labor_count` int(11) NULL DEFAULT NULL COMMENT '标准人工数(覆盖工序默认)',
  `machine_count` int(11) NULL DEFAULT NULL COMMENT '标准机台数(覆盖工序默认)',
  `labor_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '人工费率(元/小时)',
  `machine_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '机器费率(元/小时)',
  `is_key_process` tinyint(1) NULL DEFAULT 0 COMMENT '是否关键工序',
  `is_outsource` tinyint(1) NULL DEFAULT 0 COMMENT '是否委外工序',
  `quality_required` tinyint(1) NULL DEFAULT 0 COMMENT '是否需要质检',
  `wait_time` decimal(10, 4) NULL DEFAULT 0.0000 COMMENT '等待时间(小时)',
  `transfer_time` decimal(10, 4) NULL DEFAULT 0.0000 COMMENT '转移时间(小时)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '步骤说明/作业指导',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`step_id`) USING BTREE,
  INDEX `idx_routing`(`routing_id`) USING BTREE,
  INDEX `idx_process`(`process_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工艺路线步骤表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_routing_step
-- ----------------------------
INSERT INTO `bd_routing_step` VALUES (1, '000000', 1, 10, 1, 'CUT', '切割', '钢板切割', 1, 'GZZ0002', '工作站1', 0.5000, 0.2000, 2, 1, 50.000000, 100.000000, 0, 0, 0, 0.1000, 0.0500, '将钢板切割为车架所需尺寸', '切割工序', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (2, '000000', 1, 20, 2, 'WELD', '焊接', '车架焊接', 2, 'GZZ0003', '工作站2', 0.6000, 0.1500, 2, 1, 55.000000, 120.000000, 1, 0, 1, 0.1000, 0.0500, '将切割件焊接为车架', '关键工序，需质检', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (3, '000000', 1, 30, 3, 'ASM', '装配', '整车装配', 1, 'GZZ0002', '工作站1', 0.8000, 0.1000, 3, 0, 45.000000, 0.000000, 1, 0, 1, 0.1000, 0.0500, '将车架、车轮、车把组装为整车', '关键工序', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (4, '000000', 1, 40, 5, 'PKG', '包装', '成品包装', 3, 'GZZ0004', '工作站3', 0.3000, 0.0500, 1, 0, 35.000000, 0.000000, 0, 0, 0, 0.0000, 0.0500, '成品包装入库', '包装工序', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (5, '000000', 2, 10, 1, 'CUT', '切割', '箱体毛坯加工', 1, 'GZZ0002', '工作站1', 1.0000, 0.3000, 2, 2, 50.000000, 150.000000, 0, 0, 1, 0.2000, 0.1000, '箱体毛坯切割和粗加工', '切割工序', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (6, '000000', 2, 20, 2, 'WELD', '焊接', '箱体焊接组装', 2, 'GZZ0003', '工作站2', 1.0000, 0.2000, 2, 1, 55.000000, 120.000000, 1, 0, 1, 0.2000, 0.1000, '箱体焊接和组装', '关键工序', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (7, '000000', 2, 30, 3, 'ASM', '装配', '总装配', 1, 'GZZ0002', '工作站1', 1.2000, 0.2000, 3, 1, 45.000000, 100.000000, 1, 0, 1, 0.1000, 0.1000, '齿轮组、轴承、电机总装配', '关键工序', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (8, '000000', 2, 40, 4, 'QC', '质检', '出厂检验', 3, 'GZZ0004', '工作站3', 0.5000, 0.1000, 1, 1, 40.000000, 80.000000, 1, 0, 1, 0.0000, 0.1000, '减速机出厂性能检测', '质检工序', NULL, NULL, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46');
INSERT INTO `bd_routing_step` VALUES (9, '000000', 3, 10, 1, 'CUT', '切割', '钢板切割', 1, 'GZZ0002', '工作站1', 0.3000, 0.2000, 1, 1, 30.000000, 20.000000, 0, 0, 0, 0.1000, 0.1000, '将钢板切割为侧板所需尺寸（2片）', NULL, 103, 1, '2026-06-30 09:41:25', 1, '2026-06-30 09:41:25');
INSERT INTO `bd_routing_step` VALUES (10, '000000', 3, 20, 2, 'WELD', '焊接', '侧板焊接', 2, 'GZZ0003', '工作站2', 0.5000, 0.3000, 1, 1, 40.000000, 30.000000, 1, 0, 1, 0.2000, 0.1000, '将切割件焊接为侧板组件（关键工序，需IPQC质检）', NULL, 103, 1, '2026-06-30 09:41:25', 1, '2026-06-30 09:41:25');
INSERT INTO `bd_routing_step` VALUES (11, '000000', 3, 30, 3, 'ASM', '装配', '总成装配', 1, 'GZZ0002', '工作站1', 0.3000, 0.2000, 1, 0, 25.000000, 0.000000, 0, 0, 0, 0.1000, 0.1000, '将侧板组件+连接件包装配为成品工具架', NULL, 103, 1, '2026-06-30 09:41:25', 1, '2026-06-30 09:41:25');
INSERT INTO `bd_routing_step` VALUES (12, '000000', 3, 40, 5, 'PKG', '包装', '成品包装', 3, 'GZZ0004', '工作站3', 0.1500, 0.1000, 1, 0, 20.000000, 0.000000, 0, 0, 0, 0.0500, 0.0500, '成品检测外观后包装入库', NULL, 103, 1, '2026-06-30 09:41:25', 1, '2026-06-30 09:41:25');

-- ----------------------------
-- Table structure for bd_serial_number_rule
-- ----------------------------
DROP TABLE IF EXISTS `bd_serial_number_rule`;
CREATE TABLE `bd_serial_number_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `rule_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRODUCT_SN' COMMENT '规则类型(PRODUCT_SN-产品序列号/BARCODE-条码/BATCH_NO-批次号)',
  `prefix` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前缀(固定字符)',
  `date_format` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '日期格式(如yyyyMMdd)',
  `seq_length` int(11) NULL DEFAULT 4 COMMENT '流水号长度(补零位数)',
  `seq_cycle` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NONE' COMMENT '流水号重置周期(DAY/MONTH/YEAR/NONE)',
  `current_seq` int(11) NULL DEFAULT 0 COMMENT '当前流水号',
  `check_digit_rule` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NONE' COMMENT '校验位规则(NONE/MOD10/MOD11)',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/INACTIVE)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE INDEX `uk_rule_code`(`rule_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '产品序列号/条码规则表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_serial_number_rule
-- ----------------------------
INSERT INTO `bd_serial_number_rule` VALUES (1, '000000', 'SN-FG-001', '成品序列号规则', 'PRODUCT_SN', 'SN', 'yyyyMMdd', 4, 'DAY', 0, 'NONE', 'ACTIVE', '成品序列号，按日重置序号', NULL, NULL, 0, 0, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_serial_number_rule` VALUES (2, '000000', 'SN-BATCH-001', '批次号规则', 'BATCH_NO', 'B', 'yyyyMM', 6, 'MONTH', 0, 'NONE', 'ACTIVE', '批次号，按月重置序号', NULL, NULL, 0, 0, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');
INSERT INTO `bd_serial_number_rule` VALUES (3, '000000', 'SN-BC-001', '条码规则', 'BARCODE', 'BC', NULL, 8, 'NONE', 0, 'MOD10', 'ACTIVE', '产品条码，全局递增，含校验位', NULL, NULL, 0, 0, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0');

-- ----------------------------
-- Table structure for bd_unit_of_measure
-- ----------------------------
DROP TABLE IF EXISTS `bd_unit_of_measure`;
CREATE TABLE `bd_unit_of_measure`  (
  `uom_id` bigint(20) NOT NULL COMMENT '单位ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `uom_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位编码',
  `uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位名称',
  `uom_symbol` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位符号(kg、m、pcs等)',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位类别(重量、长度、数量等)',
  `is_base` tinyint(4) NULL DEFAULT 0 COMMENT '是否基本单位 0:否 1:是',
  `primary_id` bigint(20) NULL DEFAULT NULL COMMENT '基本单位ID',
  `primary_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '基本单位名称',
  `conversion_rate` decimal(18, 6) NULL DEFAULT 1.000000 COMMENT '转换率(相对于基本单位)',
  `status` tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`uom_id`) USING BTREE,
  UNIQUE INDEX `uk_uom_code`(`uom_code`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_primary_id`(`primary_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计量单位表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_unit_of_measure
-- ----------------------------
INSERT INTO `bd_unit_of_measure` VALUES (1, '000000', 'GRAM', '克', 'g', '重量', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:55:06', NULL, '2025-09-30 17:13:46', '0');
INSERT INTO `bd_unit_of_measure` VALUES (2, '000000', 'KILOGRAM', '千克', 'kg', '重量', 0, 1, '克', 1000.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:55:06', NULL, '2025-09-30 17:13:58', '0');
INSERT INTO `bd_unit_of_measure` VALUES (3, '000000', 'TON', '吨', 't', '重量', 0, 1, '克', 1000000.000000, 1, 3, NULL, NULL, NULL, '2025-09-30 16:55:06', NULL, '2025-09-30 16:55:06', '0');
INSERT INTO `bd_unit_of_measure` VALUES (4, '000000', 'MILLIGRAM', '毫克', 'mg', '重量', 0, 1, '克', 0.001000, 1, 4, NULL, NULL, NULL, '2025-09-30 16:55:06', NULL, '2025-09-30 16:55:06', '0');
INSERT INTO `bd_unit_of_measure` VALUES (5, '000000', 'POUND', '磅', 'lb', '重量', 0, 1, '克', 453.592000, 1, 5, NULL, NULL, NULL, '2025-09-30 16:55:06', NULL, '2025-09-30 16:55:06', '0');
INSERT INTO `bd_unit_of_measure` VALUES (6, '000000', 'OUNCE', '盎司', 'oz', '重量', 0, 1, '克', 28.349500, 1, 6, NULL, NULL, NULL, '2025-09-30 16:55:06', NULL, '2025-09-30 16:55:06', '0');
INSERT INTO `bd_unit_of_measure` VALUES (7, '000000', 'JIN', '斤', '斤', '重量', 0, 1, '克', 500.000000, 1, 7, NULL, NULL, NULL, '2025-09-30 16:55:06', NULL, '2025-09-30 16:55:06', '0');
INSERT INTO `bd_unit_of_measure` VALUES (8, '000000', 'MILLIMETER', '毫米', 'mm', '长度', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (9, '000000', 'CENTIMETER', '厘米', 'cm', '长度', 0, 8, '毫米', 10.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (10, '000000', 'METER', '米', 'm', '长度', 0, 8, '毫米', 1000.000000, 1, 3, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (11, '000000', 'KILOMETER', '千米', 'km', '长度', 0, 8, '毫米', 1000000.000000, 1, 4, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (12, '000000', 'INCH', '英寸', '″', '长度', 0, 8, '毫米', 25.400000, 1, 5, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (13, '000000', 'FOOT', '英尺', '′', '长度', 0, 8, '毫米', 304.800000, 1, 6, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (14, '000000', 'YARD', '码', 'yd', '长度', 0, 8, '毫米', 914.400000, 1, 7, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (15, '000000', 'SQUARE_MILLIMETER', '平方毫米', 'mm²', '面积', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (16, '000000', 'SQUARE_CENTIMETER', '平方厘米', 'cm²', '面积', 0, 15, '平方毫米', 100.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (17, '000000', 'SQUARE_METER', '平方米', '㎡', '面积', 0, 15, '平方毫米', 1000000.000000, 1, 3, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (18, '000000', 'HECTARE', '公顷', 'ha', '面积', 0, 15, '平方毫米', 10000000000.000000, 1, 4, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (19, '000000', 'ACRE', '英亩', 'acre', '面积', 0, 15, '平方毫米', 4046860000.000000, 1, 5, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (20, '000000', 'CUBIC_MILLIMETER', '立方毫米', 'mm³', '体积', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (21, '000000', 'CUBIC_CENTIMETER', '立方厘米', 'cm³', '体积', 0, 20, '立方毫米', 1000.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (22, '000000', 'CUBIC_METER', '立方米', 'm³', '体积', 0, 20, '立方毫米', 1000000000.000000, 1, 3, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (23, '000000', 'LITER', '升', 'L', '体积', 0, 20, '立方毫米', 1000000.000000, 1, 4, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (24, '000000', 'MILLILITER', '毫升', 'ml', '体积', 0, 20, '立方毫米', 1000.000000, 1, 5, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (25, '000000', 'GALLON', '加仑', 'gal', '体积', 0, 20, '立方毫米', 3785410.000000, 1, 6, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (26, '000000', 'PIECE', '件', 'pcs', '数量', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (27, '000000', 'SET', '套', 'set', '数量', 0, 26, '件', 1.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (28, '000000', 'PAIR', '双', 'pair', '数量', 0, 26, '件', 2.000000, 1, 3, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (29, '000000', 'DOZEN', '打', 'doz', '数量', 0, 26, '件', 12.000000, 1, 4, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (30, '000000', 'BOX', '箱', 'box', '数量', 0, 26, '件', 1.000000, 1, 5, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (31, '000000', 'PACKAGE', '包', 'pkg', '数量', 0, 26, '件', 1.000000, 1, 6, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (32, '000000', 'ROLL', '卷', 'roll', '数量', 0, 26, '件', 1.000000, 1, 7, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (33, '000000', 'BAG', '袋', 'bag', '数量', 0, 26, '件', 1.000000, 1, 8, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (34, '000000', 'BOTTLE', '瓶', 'btl', '数量', 0, 26, '件', 1.000000, 1, 9, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (35, '000000', 'HOUR', '小时', 'h', '时间', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (36, '000000', 'MINUTE', '分钟', 'min', '时间', 0, 35, '小时', 0.016667, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (37, '000000', 'SECOND', '秒', 's', '时间', 0, 35, '小时', 0.000278, 1, 3, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (38, '000000', 'DAY', '天', 'day', '时间', 0, 35, '小时', 24.000000, 1, 4, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (39, '000000', 'CELSIUS', '摄氏度', '℃', '温度', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (40, '000000', 'FAHRENHEIT', '华氏度', '℉', '温度', 0, 39, '摄氏度', 1.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (41, '000000', 'KELVIN', '开尔文', 'K', '温度', 0, 39, '摄氏度', 1.000000, 1, 3, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (42, '000000', 'PA', '帕斯卡', 'Pa', '压力', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (43, '000000', 'KPA', '千帕', 'kPa', '压力', 0, 42, '帕斯卡', 1000.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (44, '000000', 'MPA', '兆帕', 'MPa', '压力', 0, 42, '帕斯卡', 1000000.000000, 1, 3, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (45, '000000', 'BAR', '巴', 'bar', '压力', 0, 42, '帕斯卡', 100000.000000, 1, 4, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (46, '000000', 'PSI', '磅力/平方英寸', 'psi', '压力', 0, 42, '帕斯卡', 6894.760000, 1, 5, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (47, '000000', 'VOLT', '伏特', 'V', '电压', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (48, '000000', 'AMPERE', '安培', 'A', '电流', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (49, '000000', 'WATT', '瓦特', 'W', '功率', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (50, '000000', 'KILOWATT', '千瓦', 'kW', '功率', 0, 49, '瓦特', 1000.000000, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (51, '000000', 'KWH', '千瓦时', 'kWh', '电能', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (52, '000000', 'PERCENT', '百分比', '%', '浓度', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (53, '000000', 'PPM', '百万分比', 'ppm', '浓度', 0, 52, '百分比', 0.000100, 1, 2, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');
INSERT INTO `bd_unit_of_measure` VALUES (54, '000000', 'MOLAR', '摩尔浓度', 'mol/L', '浓度', 1, NULL, NULL, 1.000000, 1, 1, NULL, NULL, NULL, '2025-09-30 16:56:37', NULL, '2025-09-30 16:56:37', '0');

-- ----------------------------
-- Table structure for bd_vendor
-- ----------------------------
DROP TABLE IF EXISTS `bd_vendor`;
CREATE TABLE `bd_vendor`  (
  `vendor_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `tax_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '税号',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '详细地址',
  `contact_person` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人',
  `phone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '电话',
  `bank_account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '银行账户',
  `industry` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行业',
  `region` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地区',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分类',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '合作状态 ACTIVE/FROZEN',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `province_area_id` bigint(20) NULL DEFAULT NULL COMMENT '省份行政区划ID',
  `city_area_id` bigint(20) NULL DEFAULT NULL COMMENT '城市行政区划ID',
  `district_area_id` bigint(20) NULL DEFAULT NULL COMMENT '区县行政区划ID',
  `street_id` bigint(20) NULL DEFAULT NULL COMMENT '街道行政区划ID',
  `address_lat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址纬度',
  `address_lng` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址经度',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `buyer_id` bigint(20) NULL DEFAULT NULL COMMENT '采购员ID（关联sys_user）',
  `buyer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购员名称',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '归属部门ID（关联sys_dept）',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归属部门名称',
  PRIMARY KEY (`vendor_id`) USING BTREE,
  UNIQUE INDEX `uk_vendor_code`(`vendor_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_vendor
-- ----------------------------
INSERT INTO `bd_vendor` VALUES (2062098763051089921, '000000', 'V00001', '钢板产出商', 'xxxx', '浙江省宁波市海曙区白云街道', '钢板人', '153xxxxx', 'xxxxx', 'B', '5', '1', 'ACTIVE', NULL, NULL, NULL, NULL, NULL, 330000, 330200, 330203, 330203006, NULL, NULL, 103, 1, '2026-06-03 17:07:12', 1, '2026-06-03 17:07:12', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (2062098763051089922, '000000', 'V00002', '华东轴承有限公司', '91310000MA1FL8XQ3B', '上海市浦东新区张江高科技园区', '李经理', '13800002222', '6228480000001234567', '机械制造', '华东', '原材料供应商', 'ACTIVE', '轴承类专业供应商', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (2062098763051089923, '000000', 'V00003', '深圳精密电机制造厂', '91440300MA5F3XYZ0A', '深圳市宝安区西乡街道', '王总', '13900003333', '6228480000002345678', '电机制造', '华南', '关键部件供应商', 'ACTIVE', '电机类产品供应商', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (2062098763051089924, '000000', 'V00004', '天津化工材料有限公司', '91120000MA06KLMN2D', '天津市滨海新区', '张工', '13700004444', '6228480000003456789', '化工材料', '华北', '辅助材料供应商', 'ACTIVE', '润滑油等辅料供应商', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 100, 1, '2026-06-21 20:52:46', NULL, '2026-06-21 20:52:46', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (2062098763051089925, '000000', 'V00005', '宏达五金有限公司', '91330100MA8TEST002', '浙江省杭州市萧山区五金工业园', '李四', '13800138002', '6228480000009876543', '五金制造', '华东', '紧固件供应商', 'ACTIVE', '连接件包供应商', NULL, NULL, NULL, NULL, 330000, 330100, 330109, NULL, NULL, NULL, 103, 1, '2026-06-30 10:12:40', 1, '2026-06-30 10:12:40', '0', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for bd_workshop
-- ----------------------------
DROP TABLE IF EXISTS `bd_workshop`;
CREATE TABLE `bd_workshop`  (
  `workshop_id` bigint(20) NOT NULL COMMENT '车间ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `workshop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间编码',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间名称',
  `workshop_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间描述',
  `manager_id` bigint(20) NOT NULL COMMENT '车间主任ID',
  `manager_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间主任',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '所属部门ID（关联sys_dept，打通行政组织体系）',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属部门名称',
  `capacity` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '产能（标准工时/天）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`workshop_id`) USING BTREE,
  UNIQUE INDEX `uk_workshop_code`(`workshop_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_dept_id`(`dept_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '车间表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_workshop
-- ----------------------------
INSERT INTO `bd_workshop` VALUES (1, '000000', 'CJ007', '注塑车间#1', '注塑', 100, 'xiaowang', 129, '加工车间', 10.00, 1, 1, '注塑车间#1', NULL, NULL, 0, 0, NULL, NULL, '2025-11-19 11:53:35', NULL, '2026-07-02 16:19:26', '0');
INSERT INTO `bd_workshop` VALUES (2, '000000', 'CJ008', '冲压车间#1', NULL, 106, '李芳华', 130, '装配车间', 100.00, 1, 2, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-03-30 11:52:29', NULL, '2026-07-02 16:19:26', '0');
INSERT INTO `bd_workshop` VALUES (3, '000000', 'CJ009', '装配车间#1', '产品总装车间', 106, '李芳华', 130, '装配车间', 80.00, 1, 3, '负责产品最终装配', NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workshop` VALUES (4, '000000', 'CJ010', '包装车间#1', '成品包装车间', 107, '王思远', 131, '包装车间', 120.00, 1, 4, '负责成品包装入库', NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workshop` VALUES (5, '000000', 'CJ011', '焊接车间#1', '金属焊接车间', 108, '刘薇薇', 129, '加工车间', 60.00, 1, 5, '负责金属件焊接加工', NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workshop` VALUES (6, '000000', 'CJ012', '喷涂车间#1', '表面处理车间', 109, '陈志强', 129, '加工车间', 50.00, 1, 6, '负责喷涂和表面处理', NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');

-- ----------------------------
-- Table structure for bd_workstation
-- ----------------------------
DROP TABLE IF EXISTS `bd_workstation`;
CREATE TABLE `bd_workstation`  (
  `workstation_id` bigint(20) NOT NULL COMMENT '工作站ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `workstation_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作站编码',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作站名称',
  `workshop_id` bigint(20) NOT NULL COMMENT '所属车间ID',
  `workshop_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间编码',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称',
  `workstation_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心描述',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '设备ID',
  `operator_count` int(11) NULL DEFAULT 1 COMMENT '标准操作工人数',
  `standard_capacity` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '标准产能（件/小时）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`workstation_id`) USING BTREE,
  UNIQUE INDEX `uk_workstation_code`(`workstation_code`) USING BTREE,
  INDEX `idx_workshop_id`(`workshop_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作站表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bd_workstation
-- ----------------------------
INSERT INTO `bd_workstation` VALUES (1, '000000', 'GZZ0002', '工作站1', 1, 'CJ007', '注塑车间#1', NULL, NULL, 1, 0.00, 1, NULL, NULL, NULL, 0, 0, 103, 1, '2025-11-19 16:50:00', 1, '2025-12-24 14:31:08', '0');
INSERT INTO `bd_workstation` VALUES (2, '000000', 'GZZ0003', '工作站2', 2, 'CJ008', '冲压车间#1', NULL, 1, 2, 4.00, 1, NULL, NULL, NULL, 0, 0, 103, 1, '2026-03-30 11:53:09', 1, NULL, '0');
INSERT INTO `bd_workstation` VALUES (3, '000000', 'GZZ0004', '工作站3', 2, 'CJ008', '冲压车间#1', NULL, 5, 1, 1.00, 1, NULL, NULL, NULL, 0, 0, 103, 1, '2026-03-30 19:16:45', 1, NULL, '0');
INSERT INTO `bd_workstation` VALUES (4, '000000', 'GZZ0005', '注塑机A', 1, 'CJ007', '注塑车间#1', '800T注塑机', NULL, 2, 50.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (5, '000000', 'GZZ0006', '注塑机B', 1, 'CJ007', '注塑车间#1', '500T注塑机', NULL, 1, 35.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (6, '000000', 'GZZ0007', '装配线A', 3, 'CJ009', '装配车间#1', '主装配线', NULL, 4, 30.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (7, '000000', 'GZZ0008', '装配线B', 3, 'CJ009', '装配车间#1', '副装配线', NULL, 3, 25.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (8, '000000', 'GZZ0009', '调试工位', 3, 'CJ009', '装配车间#1', '产品调试测试', NULL, 2, 15.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (9, '000000', 'GZZ0010', '包装线A', 4, 'CJ010', '包装车间#1', '自动包装线', NULL, 3, 80.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (10, '000000', 'GZZ0011', '包装线B', 4, 'CJ010', '包装车间#1', '手工包装线', NULL, 4, 40.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (11, '000000', 'GZZ0012', '焊接工位A', 5, 'CJ011', '焊接车间#1', 'CO2气体保护焊', NULL, 2, 20.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (12, '000000', 'GZZ0013', '焊接工位B', 5, 'CJ011', '焊接车间#1', '氩弧焊', NULL, 1, 15.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (13, '000000', 'GZZ0014', '切割工位', 5, 'CJ011', '焊接车间#1', '激光切割', NULL, 1, 40.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (14, '000000', 'GZZ0015', '喷涂线A', 6, 'CJ012', '喷涂车间#1', '自动喷涂线', NULL, 2, 60.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (15, '000000', 'GZZ0016', '烘干炉', 6, 'CJ012', '喷涂车间#1', '烘干固化', NULL, 1, 100.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `bd_workstation` VALUES (16, '000000', 'GZZ0017', '质检站', 3, 'CJ009', '装配车间#1', '成品质量检验', NULL, 2, 25.00, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');

-- ----------------------------
-- Table structure for po_requisition
-- ----------------------------
DROP TABLE IF EXISTS `po_requisition`;
CREATE TABLE `po_requisition`  (
  `requisition_id` bigint(20) NOT NULL COMMENT '请购单ID',
  `requisition_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '请购单号',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'draft' COMMENT '状态',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '部门ID（关联sys_dept）',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门名称',
  `applicant_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '申请人',
  `required_date` datetime NULL DEFAULT NULL COMMENT '需求日期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint(20) NULL DEFAULT 0 COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标志',
  `applicant_id` bigint(20) NULL DEFAULT NULL COMMENT '申请人ID（关联sys_user）',
  PRIMARY KEY (`requisition_id`) USING BTREE,
  UNIQUE INDEX `uk_po_requisition_no`(`requisition_no`) USING BTREE,
  INDEX `idx_po_requisition_status`(`status`) USING BTREE,
  INDEX `idx_po_requisition_applicant`(`applicant_name`) USING BTREE,
  INDEX `idx_po_requisition_dept`(`dept_id`) USING BTREE,
  INDEX `idx_po_requisition_applicant_id`(`applicant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '请购单主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of po_requisition
-- ----------------------------

-- ----------------------------
-- Table structure for po_requisition_detail
-- ----------------------------
DROP TABLE IF EXISTS `po_requisition_detail`;
CREATE TABLE `po_requisition_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '明细ID',
  `requisition_id` bigint(20) NOT NULL COMMENT '请购单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料规格',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `required_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '需求数量',
  `issued_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已发料数量',
  `stock_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '库存数量',
  `shortage_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '缺料数量',
  `required_date` datetime NULL DEFAULT NULL COMMENT '需求日期',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'draft' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源类型',
  `tenant_id` bigint(20) NULL DEFAULT 0 COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_po_requisition_detail_req`(`requisition_id`) USING BTREE,
  INDEX `idx_po_requisition_detail_material`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '请购单明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of po_requisition_detail
-- ----------------------------

-- ----------------------------
-- Table structure for prd_capacity_calendar
-- ----------------------------
DROP TABLE IF EXISTS `prd_capacity_calendar`;
CREATE TABLE `prd_capacity_calendar`  (
  `calendar_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日历ID',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  `calendar_date` date NOT NULL COMMENT '日期',
  `object_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象类型: WORKSHOP-车间, WORKSTATION-工位, LINE-生产线',
  `object_id` bigint(20) NOT NULL COMMENT '对象ID',
  `object_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '对象编码',
  `object_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '对象名称',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '班次ID',
  `shift_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次编码',
  `shift_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次名称',
  `available_hours` decimal(6, 2) NULL DEFAULT 0.00 COMMENT '可用工时(小时)',
  `planned_hours` decimal(6, 2) NULL DEFAULT 0.00 COMMENT '已计划工时(小时)',
  `actual_hours` decimal(6, 2) NULL DEFAULT 0.00 COMMENT '实际已用工时(小时)',
  `efficiency` decimal(5, 2) NULL DEFAULT 100.00 COMMENT '效率系数(%)',
  `effective_hours` decimal(6, 2) NULL DEFAULT 0.00 COMMENT '有效产能',
  `remaining_hours` decimal(6, 2) NULL DEFAULT 0.00 COMMENT '剩余产能',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-有效, CLOSED-已关闭',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`calendar_id`) USING BTREE,
  UNIQUE INDEX `uk_date_object_shift`(`calendar_date`, `object_type`, `object_id`, `shift_id`, `tenant_id`) USING BTREE,
  INDEX `idx_date`(`calendar_date`) USING BTREE,
  INDEX `idx_object`(`object_type`, `object_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '产能日历表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_capacity_calendar
-- ----------------------------

-- ----------------------------
-- Table structure for prd_material_requirement
-- ----------------------------
DROP TABLE IF EXISTS `prd_material_requirement`;
CREATE TABLE `prd_material_requirement`  (
  `requirement_id` bigint(20) NOT NULL COMMENT '物料需求单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `requirement_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料需求单编号',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '生产计划ID',
  `plan_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产计划编号',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOM ID',
  `requirement_date` datetime NULL DEFAULT NULL COMMENT '需求日期',
  `total_material_types` bigint(20) NULL DEFAULT 0 COMMENT '物料种类总数',
  `total_required_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '需求总量',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态',
  `planner_id` bigint(20) NULL DEFAULT NULL COMMENT '计划员ID',
  `planner_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划员',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '部门ID',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门',
  `confirmed_time` datetime NULL DEFAULT NULL COMMENT '确认时间',
  `issued_time` datetime NULL DEFAULT NULL COMMENT '发料时间',
  `closed_time` datetime NULL DEFAULT NULL COMMENT '关闭时间',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `parent_requirement_id` bigint(20) NULL DEFAULT NULL COMMENT '父级物料需求单ID',
  PRIMARY KEY (`requirement_id`) USING BTREE,
  UNIQUE INDEX `uk_requirement_no`(`requirement_no`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料需求单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_material_requirement
-- ----------------------------
INSERT INTO `prd_material_requirement` VALUES (51, '000000', 'MBOM251130001', 8, 'SCJH20250001', 5, '2025-11-30 00:00:00', 1, 100.0000, NULL, 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', NULL, 103, 1, '2025-11-30 00:30:58', 1, '2025-11-30 00:30:58', '0', NULL);
INSERT INTO `prd_material_requirement` VALUES (52, '000000', 'MBOM251130002', 8, 'SCJH20250001', 9, '2025-11-30 00:00:00', 1, 100.0000, NULL, 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2001, 'FRAME', '车架', NULL, NULL, 103, 1, '2025-11-30 00:30:59', 1, '2025-11-30 00:30:58', '0', NULL);
INSERT INTO `prd_material_requirement` VALUES (53, '000000', 'MBOM251130003', 8, 'SCJH20250001', 6, '2025-11-30 00:00:00', 3, 7974.3600, NULL, 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2002, 'WHEEL', '车轮', NULL, NULL, 103, 1, '2025-11-30 00:30:59', 1, '2025-11-30 00:30:58', '0', NULL);
INSERT INTO `prd_material_requirement` VALUES (54, '000000', 'MBOM251130004', 8, 'SCJH20250001', 7, '2025-11-30 00:00:00', 2, 303.5050, NULL, 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2003, 'HANDLEBAR', '车把', NULL, NULL, 103, 1, '2025-11-30 00:30:59', 1, '2025-11-30 00:30:58', '0', NULL);
INSERT INTO `prd_material_requirement` VALUES (2057398677851283458, '000000', 'MBOM260521018', 2054814962902032386, 'SCJH20260008', 5, '2026-05-21 17:50:44', 1, 1.0000, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', NULL);
INSERT INTO `prd_material_requirement` VALUES (2057398678052610049, '000000', 'MBOM260521019', 2054814962902032386, 'SCJH20260008', 9, '2026-05-21 17:50:44', 1, 1.0000, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2001, 'IF20251203001', '车架', NULL, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 2057398677851283458);
INSERT INTO `prd_material_requirement` VALUES (2057398678274908161, '000000', 'MBOM260521020', 2054814962902032386, 'SCJH20260008', 6, '2026-05-21 17:50:44', 2, 4.1004, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2002, 'IF20251203002', '车轮', NULL, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 2057398677851283458);
INSERT INTO `prd_material_requirement` VALUES (2057398678497206274, '000000', 'MBOM260521021', 2054814962902032386, 'SCJH20260008', 2057368028339740674, '2026-05-21 17:50:44', 1, 0.0779, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 3003, 'IF20251203006', '辐条', NULL, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 2057398678274908161);
INSERT INTO `prd_material_requirement` VALUES (2057398678732087298, '000000', 'MBOM260521022', 2054814962902032386, 'SCJH20260008', 7, '2026-05-21 17:50:44', 2, 3.0351, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2003, 'IF20251203003', '车把', NULL, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 2057398677851283458);
INSERT INTO `prd_material_requirement` VALUES (2068919533466345473, '000000', 'MBOM260622001', 2068919152468353026, 'SCJH20260009', 5, '2026-06-22 12:50:30', 1, 100.0000, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-22 12:50:30', '0', NULL);
INSERT INTO `prd_material_requirement` VALUES (2068919533705420802, '000000', 'MBOM260622002', 2068919152468353026, 'SCJH20260009', 9, '2026-06-22 12:50:30', 1, 100.0000, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2001, 'IF20251203001', '车架', NULL, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-22 12:50:30', '0', 2068919533466345473);
INSERT INTO `prd_material_requirement` VALUES (2068919533927718914, '000000', 'MBOM260622003', 2068919152468353026, 'SCJH20260009', 6, '2026-06-22 12:50:30', 2, 410.0400, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2002, 'IF20251203002', '车轮', NULL, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-22 12:50:30', '0', 2068919533466345473);
INSERT INTO `prd_material_requirement` VALUES (2068919534158405633, '000000', 'MBOM260622004', 2068919152468353026, 'SCJH20260009', 2057368028339740674, '2026-06-22 12:50:30', 1, 7.7912, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 3003, 'IF20251203006', '辐条', NULL, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-22 12:50:30', '0', 2068919533927718914);
INSERT INTO `prd_material_requirement` VALUES (2068919534355537921, '000000', 'MBOM260622005', 2068919152468353026, 'SCJH20260009', 7, '2026-06-22 12:50:30', 2, 303.5050, 'PROCESS', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2003, 'IF20251203003', '车把', NULL, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-22 12:50:30', '0', 2068919533466345473);
INSERT INTO `prd_material_requirement` VALUES (2073655856091795457, '000000', 'MBOM260705001', 2071821464829349890, 'SCJH20260010', 20, '2026-07-05 14:30:57', 1, 10.0000, 'PROCESS', 'DRAFT', 101, '麦得朶', 111, '生产中心', NULL, NULL, NULL, 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', NULL, 103, 1, '2026-07-05 14:30:57', 1, '2026-07-05 14:30:57', '0', NULL);
INSERT INTO `prd_material_requirement` VALUES (2073655856284733441, '000000', 'MBOM260705002', 2071821464829349890, 'SCJH20260010', 21, '2026-07-05 14:30:57', 1, 31.0000, 'PROCESS', 'DRAFT', 101, '麦得朶', 111, '生产中心', NULL, NULL, NULL, 8001, 'RACK-SIDE', '侧板组件', '300mm*200mm*2mm', NULL, 103, 1, '2026-07-05 14:30:57', 1, '2026-07-05 14:30:57', '0', 2073655856091795457);

-- ----------------------------
-- Table structure for prd_material_requirement_detail
-- ----------------------------
DROP TABLE IF EXISTS `prd_material_requirement_detail`;
CREATE TABLE `prd_material_requirement_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '需求明细ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `requirement_id` bigint(20) NOT NULL COMMENT '物料需求单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型',
  `bom_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT 'BOM标准用量',
  `loss_rate` decimal(18, 6) NULL DEFAULT 0.000000 COMMENT '损耗率(%)',
  `required_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '需求数量',
  `issued_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已发料数量',
  `stock_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '当前库存数量',
  `shortage_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '缺料数量',
  `operation_step` bigint(20) NULL DEFAULT NULL COMMENT '工序步骤',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作中心ID',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作中心名称',
  `lead_time` bigint(20) NULL DEFAULT NULL COMMENT '提前期(小时)',
  `required_date` datetime NULL DEFAULT NULL COMMENT '需求日期',
  `status` bigint(20) NULL DEFAULT NULL COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `backflush_flag` tinyint(4) NULL DEFAULT 0 COMMENT '是否倒冲物料: 0-否, 1-是',
  `actual_consumed_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '实际消耗数量',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_requirement_id`(`requirement_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料需求单明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_material_requirement_detail
-- ----------------------------
INSERT INTO `prd_material_requirement_detail` VALUES (2057398677889032193, '000000', 2057398677851283458, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', 'PROCESS', 1.0000, 0.000000, 1.0000, 0.0000, 0.0000, 0.0000, 40, NULL, NULL, NULL, '2026-05-21 17:50:44', 1, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2057398678081970178, '000000', 2057398678052610049, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', 'PROCESS', 1.0000, 0.000000, 1.0000, 0.0000, 0.0000, 0.0000, 1, NULL, NULL, NULL, '2026-05-21 17:50:44', 1, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2057398678312656897, '000000', 2057398678274908161, 3001, 'IF20251203004', '轮圈', NULL, '1', 'PROCESS', 1.0000, 1.000000, 2.0604, 0.0000, 0.0000, 0.0000, 10, NULL, NULL, NULL, '2026-05-21 17:50:44', 1, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2057398678321045505, '000000', 2057398678274908161, 3002, 'IF20251203005', '轮胎', NULL, '1', 'PROCESS', 1.0000, 0.000000, 2.0400, 0.0000, 0.0000, 0.0000, 10, NULL, NULL, NULL, '2026-05-21 17:50:44', 1, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2057398678526566402, '000000', 2057398678497206274, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', 'PROCESS', 0.0010, 3.000000, 0.0779, 0.0000, 0.0000, 0.0000, 4, NULL, NULL, NULL, '2026-05-21 17:50:44', 1, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2057398678774030338, '000000', 2057398678732087298, 3004, 'IF20251203007', '把管', NULL, '1', 'PROCESS', 1.0000, 0.500000, 1.0151, 0.0000, 0.0000, 0.0000, 10, NULL, NULL, NULL, '2026-05-21 17:50:44', 1, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2057398678786613250, '000000', 2057398678732087298, 3005, 'IF20251203008', '把套', NULL, '1', 'PROCESS', 2.0000, 0.000000, 2.0200, 0.0000, 0.0000, 0.0000, 10, NULL, NULL, NULL, '2026-05-21 17:50:44', 1, NULL, 103, 1, '2026-05-21 17:50:44', 1, '2026-05-21 17:50:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2068919533550231554, '000000', 2068919533466345473, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', 'PROCESS', 1.0000, 0.000000, 100.0000, 100.0000, 500.0000, 0.0000, 40, NULL, NULL, 0, '2026-06-22 12:50:30', 1, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-24 11:30:43', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2068919533755752449, '000000', 2068919533705420802, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', 'PROCESS', 1.0000, 0.000000, 100.0000, 100.0000, 1500.0000, 0.0000, 1, NULL, NULL, 1, '2026-06-22 13:50:30', 1, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-24 11:30:43', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2068919534003216386, '000000', 2068919533927718914, 3001, 'IF20251203004', '轮圈', NULL, '1', 'PROCESS', 1.0000, 1.000000, 206.0400, 200.0000, 200.0000, 6.0400, 10, NULL, NULL, 0, '2026-06-22 12:50:30', 1, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-24 11:30:43', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2068919534015799297, '000000', 2068919533927718914, 3002, 'IF20251203005', '轮胎', NULL, '1', 'PROCESS', 1.0000, 0.000000, 204.0000, 200.0000, 200.0000, 4.0000, 10, NULL, NULL, 0, '2026-06-22 12:50:30', 1, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-24 11:30:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2068919534200348673, '000000', 2068919534158405633, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', 'PROCESS', 0.0010, 3.000000, 7.7912, 7.7912, 1500.0000, 0.0000, 4, NULL, NULL, 2, '2026-06-22 14:50:30', 1, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-24 11:30:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2068919534435229697, '000000', 2068919534355537921, 3004, 'IF20251203007', '把管', NULL, '1', 'PROCESS', 1.0000, 0.500000, 101.5050, 101.5050, 200.0000, 0.0000, 10, NULL, NULL, 0, '2026-06-22 12:50:30', 1, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-24 11:30:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2068919534447812609, '000000', 2068919534355537921, 3005, 'IF20251203008', '把套', NULL, '1', 'PROCESS', 2.0000, 0.000000, 202.0000, 202.0000, 500.0000, 0.0000, 10, NULL, NULL, 0, '2026-06-22 12:50:30', 1, NULL, 103, 1, '2026-06-22 12:50:30', 1, '2026-06-24 11:30:44', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2073655856154710018, '000000', 2073655856091795457, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '28', 'PROCUREMENT', 1.0000, 0.000000, 10.0000, 10.0000, 0.0000, 0.0000, 30, NULL, NULL, 1, '2026-07-05 15:30:57', 1, NULL, 103, 1, '2026-07-05 14:30:57', 1, '2026-07-06 23:24:39', '0', 0, 0.0000);
INSERT INTO `prd_material_requirement_detail` VALUES (2073655856351842306, '000000', 2073655856284733441, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', 'PROCUREMENT', 1.5000, 2.000000, 31.0000, 93.0000, 1392.2100, -62.0000, 10, NULL, NULL, 2, '2026-07-05 16:30:57', 1, NULL, 103, 1, '2026-07-05 14:30:57', 1, '2026-07-06 23:24:22', '0', 0, 0.0000);

-- ----------------------------
-- Table structure for prd_production_plan
-- ----------------------------
DROP TABLE IF EXISTS `prd_production_plan`;
CREATE TABLE `prd_production_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '生产计划ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生产计划编号',
  `plan_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划名称',
  `version` bigint(20) NULL DEFAULT 1 COMMENT '版本号',
  `original_plan_id` bigint(20) NULL DEFAULT NULL COMMENT '原始计划ID',
  `reference_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '参考单据号',
  `plan_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划类型',
  `plan_date` datetime NULL DEFAULT NULL COMMENT '计划日期',
  `start_date` datetime NULL DEFAULT NULL COMMENT '计划开始日期',
  `end_date` datetime NULL DEFAULT NULL COMMENT '计划结束日期',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态',
  `priority` bigint(20) NULL DEFAULT NULL COMMENT '优先级',
  `planner_id` bigint(20) NULL DEFAULT NULL COMMENT '计划员ID',
  `planner_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划员',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '生产部门ID',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产部门',
  `total_quantity` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划总数量',
  `confirmed_time` datetime NULL DEFAULT NULL COMMENT '确认时间',
  `released_time` datetime NULL DEFAULT NULL COMMENT '下达时间',
  `closed_time` datetime NULL DEFAULT NULL COMMENT '关闭时间',
  `bom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'bom名称',
  `bom_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'bom版本',
  `is_current` bigint(20) NULL DEFAULT 0 COMMENT '是否当前版本',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOM ID',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `material_requirement_id` bigint(20) NULL DEFAULT NULL COMMENT '物料需求单ID',
  `material_requirement_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料需求单编号',
  `order_line_id` bigint(20) NULL DEFAULT NULL COMMENT '销售订单子表id',
  `order_line_no` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售订单子表行号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  UNIQUE INDEX `uk_plan_no`(`plan_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_plan_date`(`plan_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产计划表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_production_plan
-- ----------------------------
INSERT INTO `prd_production_plan` VALUES (1, '000000', 'SCJH20250001', '测试计划单3', 1, NULL, NULL, 'MPS', '2025-11-01 00:00:00', NULL, NULL, 'DRAFT', 0, NULL, NULL, NULL, NULL, 0.0000, NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-11-13 17:47:49', 1, '2025-11-18 16:29:07', '0');
INSERT INTO `prd_production_plan` VALUES (7, '000000', 'SCJH20250001-V2', '自行车订单', 2, NULL, 'CO20251029001', 'MPS', '2025-11-01 00:00:00', '2025-11-01 00:00:00', '2025-11-30 00:00:00', 'DRAFT', 0, NULL, NULL, NULL, NULL, 100.0000, '2025-11-01 00:00:00', '2025-11-01 00:00:00', NULL, NULL, NULL, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 103, 1, '2025-11-13 17:47:49', 1, '2025-11-18 17:19:37', '0');
INSERT INTO `prd_production_plan` VALUES (8, '000000', 'SCJH20250001-V3', '自行车订单', 3, NULL, 'CO20251029001', 'MPS', '2025-11-01 00:00:00', '2025-11-01 00:00:00', '2025-11-30 00:00:00', 'CONFIRMED', 0, NULL, NULL, NULL, NULL, 100.0000, '2025-11-01 00:00:00', '2025-11-01 00:00:00', NULL, '自行车标准版BOM', '1.0', 1, 5, 0, NULL, NULL, 1, '101', NULL, 103, 1, '2025-11-13 17:47:49', 1, '2025-11-20 18:06:58', '0');
INSERT INTO `prd_production_plan` VALUES (10001, '000000', 'PP-TEST-001', '自行车生产计划-202606', 1, NULL, NULL, 'SALES_ORDER', '2026-06-02 00:00:00', '2026-06-10 00:00:00', '2026-07-05 00:00:00', 'RELEASED', 2, 1, '管理员', NULL, NULL, 100.0000, NULL, NULL, NULL, '自行车标准版BOM', '1.0', 1, 5, NULL, NULL, NULL, 10001, NULL, '来自SO-TEST-001的生产计划', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `prd_production_plan` VALUES (2054814962902032386, '000000', 'SCJH20260008', '自行车', 2, 13, 'CO20251029001', 'MPS', '2026-05-31 00:00:00', '2026-05-14 00:00:00', '2026-05-31 00:00:00', 'DRAFT', 1, NULL, NULL, NULL, NULL, 1.0000, NULL, NULL, NULL, '自行车标准版BOM', '1.0', 1, 5, NULL, NULL, '101', 2054455410809610242, '101', '测试aaa', 103, 1, '2026-05-14 14:43:58', 1, '2026-05-15 10:56:03', '0');
INSERT INTO `prd_production_plan` VALUES (2054832339882573826, '000000', 'SCJH20260003', '自行车', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1.0000, NULL, NULL, NULL, NULL, NULL, 1, 5, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, '2026-05-14 15:53:01', 1, '2026-05-14 16:11:54', '1');
INSERT INTO `prd_production_plan` VALUES (2068919152468353026, '000000', 'SCJH20260009', '销售订单-XSDD20260622004-自行车', 2, NULL, 'XSDD20260622004', 'MPS', '2026-06-22 12:48:59', '2026-06-22 00:00:00', '2026-06-29 00:00:00', 'CONFIRMED', 0, NULL, NULL, NULL, NULL, 100.0000, '2026-06-22 00:00:00', '2026-06-22 00:00:00', NULL, '自行车标准版BOM', '1.0', 1, 5, NULL, NULL, NULL, 2068918652628951041, '101', NULL, 103, 1, '2026-06-22 12:48:59', 1, '2026-06-22 12:51:50', '0');
INSERT INTO `prd_production_plan` VALUES (2071821464829349890, '000000', 'SCJH20260010', '销售订单-XSDD20260630001-简易金属工具架', 2, NULL, 'XSDD20260630001', 'MPS', '2026-06-30 13:01:44', NULL, NULL, 'RELEASED', 0, 101, '麦得朶', 111, '生产中心', 10.0000, '2026-07-05 14:12:26', '2026-07-06 17:03:39', NULL, '简易金属工具架标准版BOM', '1.0', 1, 20, NULL, NULL, NULL, 2071821415999262722, '', NULL, 103, 1, '2026-06-30 13:01:44', 1, '2026-07-06 17:03:38', '0');
INSERT INTO `prd_production_plan` VALUES (2071858653202145281, '000000', 'SCJH20260011', '销售订单-XSDD20260630001-简易金属工具架', 1, NULL, 'XSDD20260630001', 'MPS', '2026-06-30 15:29:31', NULL, NULL, 'DRAFT', NULL, NULL, NULL, NULL, NULL, 10.0000, NULL, NULL, NULL, '简易金属工具架标准版BOM', '1.0', 0, 20, NULL, NULL, NULL, 2071824847971733505, '101', NULL, 103, 1, '2026-06-30 15:29:31', 1, '2026-07-01 15:02:07', '1');

-- ----------------------------
-- Table structure for prd_rework_order
-- ----------------------------
DROP TABLE IF EXISTS `prd_rework_order`;
CREATE TABLE `prd_rework_order`  (
  `rework_id` bigint(20) NOT NULL COMMENT '返工单ID',
  `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID',
  `rework_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '返工单号',
  `source_wo_id` bigint(20) NULL DEFAULT NULL COMMENT '来源工单ID',
  `source_wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源工单编号',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `rework_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '返工数量',
  `rework_reason` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '返工原因',
  `actual_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '实际数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '返工状态',
  `del_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`rework_id`) USING BTREE,
  UNIQUE INDEX `uk_rework_no`(`rework_no`, `tenant_id`) USING BTREE,
  INDEX `idx_rework_source_wo`(`source_wo_id`) USING BTREE,
  INDEX `idx_rework_material`(`material_id`) USING BTREE,
  INDEX `idx_rework_status`(`status`) USING BTREE,
  INDEX `idx_rework_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '返工订单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_rework_order
-- ----------------------------

-- ----------------------------
-- Table structure for prd_subcontract
-- ----------------------------
DROP TABLE IF EXISTS `prd_subcontract`;
CREATE TABLE `prd_subcontract`  (
  `subcontract_id` bigint(20) NOT NULL COMMENT '委外单ID',
  `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID',
  `subcontract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '委外单号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `actual_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '实际数量',
  `unit_price` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '单价',
  `total_amount` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '总金额',
  `plan_start_date` date NULL DEFAULT NULL COMMENT '计划开始日期',
  `plan_end_date` date NULL DEFAULT NULL COMMENT '计划结束日期',
  `actual_start_date` date NULL DEFAULT NULL COMMENT '实际开始日期',
  `actual_end_date` date NULL DEFAULT NULL COMMENT '实际结束日期',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联工单编号',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '委外状态',
  `del_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`subcontract_id`) USING BTREE,
  UNIQUE INDEX `uk_subcontract_no`(`subcontract_no`, `tenant_id`) USING BTREE,
  INDEX `idx_subcontract_vendor`(`vendor_id`) USING BTREE,
  INDEX `idx_subcontract_material`(`material_id`) USING BTREE,
  INDEX `idx_subcontract_status`(`status`) USING BTREE,
  INDEX `idx_subcontract_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '委外订单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_subcontract
-- ----------------------------

-- ----------------------------
-- Table structure for prd_work_order
-- ----------------------------
DROP TABLE IF EXISTS `prd_work_order`;
CREATE TABLE `prd_work_order`  (
  `wo_id` bigint(20) NOT NULL COMMENT '工单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工单编号',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '来源计划ID',
  `plan_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计划名称',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售订单编号',
  `source_line_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据行ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品规格',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量',
  `completed_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已完成数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `unit_id` bigint(20) NULL DEFAULT NULL COMMENT '单位id',
  `unit_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `plan_start_date` datetime NULL DEFAULT NULL COMMENT '计划开始日期',
  `plan_end_date` datetime NULL DEFAULT NULL COMMENT '计划结束日期',
  `actual_start_date` datetime NULL DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_date` datetime NULL DEFAULT NULL COMMENT '实际结束时间',
  `wo_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单状态',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作站ID',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作站名称',
  `production_line_id` bigint(20) NULL DEFAULT NULL COMMENT '生产线ID',
  `responsible_id` bigint(20) NULL DEFAULT NULL COMMENT '负责人ID',
  `responsible_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人',
  `team_id` bigint(20) NULL DEFAULT NULL COMMENT '班组ID',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组',
  `priority` bigint(20) NULL DEFAULT NULL COMMENT '优先级',
  `bom_id` bigint(20) NULL DEFAULT NULL COMMENT 'BOMid',
  `bom_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BOM版本',
  `bom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BOM名称',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `wo_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT '工单类型:NORMAL-正常,SUBCONTRACT-委外,REWORK-返工',
  `backflush_flag` tinyint(4) NULL DEFAULT 0 COMMENT '是否倒冲工单: 0-否, 1-是',
  `issued_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '累计发料数量',
  PRIMARY KEY (`wo_id`) USING BTREE,
  UNIQUE INDEX `uk_wo_no`(`wo_no`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_wo_status`(`wo_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产工单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_work_order
-- ----------------------------
INSERT INTO `prd_work_order` VALUES (4, '000000', 'SCGD202603300002', 8, '自行车订单', 'PLAN', 8, 'CO20251029001', NULL, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', 100.0000, 0.0000, 0.0000, 26, '件', '2025-11-01 00:00:00', '2025-11-30 00:00:00', NULL, NULL, 'IN_PROGRESS', 1, '注塑车间#1', NULL, NULL, NULL, 117, '车间主任', NULL, NULL, 0, 5, '1.0', '自行车标准版BOM', NULL, 0, 0, NULL, NULL, 103, 1, '2026-03-30 20:59:28', 1, '2026-05-14 11:15:22', '0', 'NORMAL', 0, 0.0000);
INSERT INTO `prd_work_order` VALUES (10001, '000000', 'WO-TEST-001', 10001, '自行车生产计划-202606', 'SALES_ORDER', 10001, 'SO-TEST-001', NULL, 1001, 'IF20251110001', '自行车', NULL, 100.0000, 0.0000, 0.0000, 26, '件', '2026-06-10 00:00:00', '2026-07-05 00:00:00', NULL, NULL, 'RELEASED', 1, '注塑车间#1', NULL, NULL, NULL, NULL, NULL, 30001, '装配A组', 2, 5, '1.0', '自行车标准版BOM', NULL, NULL, NULL, '自行车100辆工单', NULL, 100, 1, '2026-06-21 20:59:14', 1, '2026-07-06 10:57:02', '1', 'NORMAL', 0, 0.0000);
INSERT INTO `prd_work_order` VALUES (2068920326621814786, '000000', 'SCGD202606220001', 2068919152468353026, '销售订单-XSDD20260622004-自行车', 'PLAN', 2068919152468353026, 'XSDD20260622004', NULL, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', 100.0000, 50.0000, 0.0000, 26, '件', '2026-06-22 00:00:00', '2026-06-29 00:00:00', '2026-06-24 11:33:29', '2026-06-24 15:24:57', 'COMPLETED', 1, '注塑车间#1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 5, '1.0', '自行车标准版BOM', NULL, NULL, NULL, NULL, 2058473835820376066, 103, 1, '2026-06-22 12:53:39', -1, '2026-06-24 16:07:26', '0', 'NORMAL', 0, 0.0000);
INSERT INTO `prd_work_order` VALUES (2073979191344271361, '000000', 'SCGD202607060001', 2071821464829349890, '销售订单-XSDD20260630001-简易金属工具架', 'PLAN', 2071821464829349890, 'XSDD20260630001', NULL, 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 10.0000, 0.0000, 27, '套', '2026-07-03 00:00:00', '2026-07-31 00:00:00', '2026-07-06 23:46:27', '2026-07-06 23:46:27', 'COMPLETED', 3, '装配车间#1', NULL, NULL, NULL, 117, '车间主任', NULL, NULL, 0, 20, '1.0', '简易金属工具架标准版BOM', NULL, NULL, NULL, NULL, 2058473835820376066, 103, 1, '2026-07-06 11:55:47', -1, '2026-07-06 23:46:27', '0', 'NORMAL', 0, 0.0000);

-- ----------------------------
-- Table structure for prd_work_order_resource
-- ----------------------------
DROP TABLE IF EXISTS `prd_work_order_resource`;
CREATE TABLE `prd_work_order_resource`  (
  `id` bigint(20) NOT NULL COMMENT '资源分配ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `wo_id` bigint(20) NOT NULL COMMENT '工单ID',
  `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型：WORKSHOP-车间, WORKSTATION-工作站, EQUIPMENT-设备, TEAM-班组, PRODUCTION_LINE-生产线',
  `resource_id` bigint(20) NULL DEFAULT NULL COMMENT '资源ID',
  `resource_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '资源编码',
  `resource_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '资源名称',
  `seq` int(11) NULL DEFAULT 0 COMMENT '资源顺序/工序顺序',
  `parent_id` bigint(20) NULL DEFAULT NULL COMMENT '父资源ID（如工作站所属车间ID）',
  `parent_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父资源类型',
  `plan_start_time` datetime NULL DEFAULT NULL COMMENT '计划开始时间',
  `plan_end_time` datetime NULL DEFAULT NULL COMMENT '计划结束时间',
  `actual_start_time` datetime NULL DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime NULL DEFAULT NULL COMMENT '实际结束时间',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待执行, PROCESSING-执行中, COMPLETED-已完成',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_resource_type`(`resource_type`) USING BTREE,
  INDEX `idx_resource_id`(`resource_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产工单资源分配表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_work_order_resource
-- ----------------------------
INSERT INTO `prd_work_order_resource` VALUES (1, '000000', 4, 'WORKSHOP', 1, 'CJ007', '注塑车间#1', 1, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-03-30 20:59:27', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (2, '000000', 4, 'WORKSHOP', 2, 'CJ008', '冲压车间#1', 2, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-03-30 20:59:27', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (3, '000000', 4, 'WORKSTATION', 1, 'GZZ0002', '工作站1', 3, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-03-30 20:59:27', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (4, '000000', 4, 'WORKSTATION', 2, 'GZZ0003', '工作站2', 4, 2, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-03-30 20:59:27', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (5, '000000', 4, 'WORKSHOP', 1, 'CJ007', '注塑车间#1', 1, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-04-02 10:49:38', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (6, '000000', 4, 'WORKSHOP', 2, 'CJ008', '冲压车间#1', 2, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-04-02 10:49:38', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (7, '000000', 4, 'WORKSTATION', 1, 'GZZ0002', '工作站1', 3, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-04-02 10:49:38', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (8, '000000', 4, 'WORKSTATION', 2, 'GZZ0003', '工作站2', 4, 2, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-04-02 10:49:38', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (9, '000000', 4, 'TEAM', 8, 'SC0001', '组装一组', 5, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-04-02 10:49:38', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (10, '000000', 4, 'TEAM', 11, 'SC0003', '注塑一组', 6, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-04-02 10:49:38', 1, '2026-05-14 11:15:22', '1');
INSERT INTO `prd_work_order_resource` VALUES (2054762466246635521, '000000', 4, 'WORKSHOP', 1, 'CJ007', '注塑车间#1', 1, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466267607041, '000000', 4, 'WORKSHOP', 1, 'CJ007', '注塑车间#1', 2, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466275995649, '000000', 4, 'WORKSHOP', 2, 'CJ008', '冲压车间#1', 3, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466288578562, '000000', 4, 'WORKSHOP', 2, 'CJ008', '冲压车间#1', 4, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466292772865, '000000', 4, 'WORKSTATION', 1, 'GZZ0002', '工作站1', 5, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466305355778, '000000', 4, 'WORKSTATION', 1, 'GZZ0002', '工作站1', 6, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466313744386, '000000', 4, 'WORKSTATION', 2, 'GZZ0003', '工作站2', 7, 2, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466322132994, '000000', 4, 'WORKSTATION', 2, 'GZZ0003', '工作站2', 8, 2, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466334715905, '000000', 4, 'TEAM', 8, 'SC0001', '组装一组', 9, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2054762466334715906, '000000', 4, 'TEAM', 11, 'SC0003', '注塑一组', 10, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-05-14 11:15:22', 1, '2026-05-14 11:15:22', '0');
INSERT INTO `prd_work_order_resource` VALUES (2068921531758600193, '000000', 2068920326621814786, 'WORKSHOP', 1, 'CJ007', '注塑车间#1', 1, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-06-22 12:58:27', 1, '2026-06-22 12:58:27', '0');
INSERT INTO `prd_work_order_resource` VALUES (2068921531813126146, '000000', 2068920326621814786, 'WORKSHOP', 2, 'CJ008', '冲压车间#1', 2, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-06-22 12:58:27', 1, '2026-06-22 12:58:27', '0');
INSERT INTO `prd_work_order_resource` VALUES (2068921531825709057, '000000', 2068920326621814786, 'WORKSTATION', 2, 'GZZ0003', '工作站2', 3, 2, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-06-22 12:58:27', 1, '2026-06-22 12:58:27', '0');
INSERT INTO `prd_work_order_resource` VALUES (2068921531829903361, '000000', 2068920326621814786, 'TEAM', 8, 'SC0001', '组装一组', 4, 1, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-06-22 12:58:27', 1, '2026-06-22 12:58:27', '0');
INSERT INTO `prd_work_order_resource` VALUES (2074038028239712257, '000000', 2073979191344271361, 'WORKSHOP', 2, 'CJ008', '冲压车间#1', 1, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-06 15:49:34', 1, '2026-07-06 15:49:34', '0');
INSERT INTO `prd_work_order_resource` VALUES (2074038028264878082, '000000', 2073979191344271361, 'WORKSHOP', 3, 'CJ009', '装配车间#1', 2, NULL, NULL, NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-06 15:49:34', 1, '2026-07-06 15:49:34', '0');
INSERT INTO `prd_work_order_resource` VALUES (2074038028281655298, '000000', 2073979191344271361, 'WORKSTATION', 2, 'GZZ0003', '工作站2', 3, 2, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-06 15:49:34', 1, '2026-07-06 15:49:34', '0');
INSERT INTO `prd_work_order_resource` VALUES (2074038028298432513, '000000', 2073979191344271361, 'WORKSTATION', 6, 'GZZ0007', '装配线A', 4, 3, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-06 15:49:34', 1, '2026-07-06 15:49:34', '0');
INSERT INTO `prd_work_order_resource` VALUES (2074038028365541377, '000000', 2073979191344271361, 'TEAM', 12, 'ZP0001', '装配一组', 5, 3, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-06 15:49:34', 1, '2026-07-06 15:49:34', '0');
INSERT INTO `prd_work_order_resource` VALUES (2074038028394901505, '000000', 2073979191344271361, 'TEAM', 15, 'HJ0001', '焊接一组', 6, 5, 'WORKSHOP', NULL, NULL, NULL, NULL, 'PENDING', NULL, 103, 1, '2026-07-06 15:49:34', 1, '2026-07-06 15:49:34', '0');

-- ----------------------------
-- Table structure for prd_work_report
-- ----------------------------
DROP TABLE IF EXISTS `prd_work_report`;
CREATE TABLE `prd_work_report`  (
  `report_id` bigint(20) NOT NULL COMMENT '报工记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `report_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报工单编号',
  `report_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报工类型：NORMAL-正常报工，REWORK-返工，REPAIR-返修，OVERTIME-加班',
  `report_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报工状态：DRAFT-草稿，SUBMITTED-已提交，APPROVED-已审核，REJECTED-已驳回',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号',
  `wo_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单名称',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `unit_id` bigint(20) NULL DEFAULT NULL COMMENT '单位ID',
  `unit_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位编码',
  `unit_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `route_id` bigint(20) NULL DEFAULT NULL COMMENT '工艺流程ID',
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工艺流程编号',
  `route_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工艺流程名称',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `operation_step` bigint(20) NULL DEFAULT NULL COMMENT '工序步骤序号',
  `operation_seq` bigint(20) NULL DEFAULT NULL COMMENT '当前工序报工次数（用于多次报工）',
  `task_id` bigint(20) NULL DEFAULT NULL COMMENT '生产任务ID',
  `task_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产任务编号',
  `workstation_id` bigint(20) NULL DEFAULT NULL COMMENT '工作站ID',
  `workstation_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作站编码',
  `workstation_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作站名称',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '车间名称',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备编码',
  `equipment_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称',
  `lot_number` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生产批号/批次号',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编号',
  `expire_date` datetime NULL DEFAULT NULL COMMENT '过期日期/有效期至',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '班次ID',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次名称',
  `planned_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '计划数量（工序计划数量）',
  `reported_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '本次报工数量',
  `good_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '合格数量',
  `scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '报废数量',
  `defect_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不良品数量',
  `labor_scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '工废数量（人为原因报废）',
  `material_scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '料废数量（材料原因报废）',
  `other_scrap_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '其他原因报废数量',
  `uncheck_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '待检测数量',
  `cumulative_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '累计报工数量（到本次为止）',
  `remaining_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '剩余数量',
  `report_date` datetime NULL DEFAULT NULL COMMENT '报工日期',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `labor_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '人工工时（小时）',
  `machine_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '机器工时（小时）',
  `setup_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '准备工时（小时）',
  `wait_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '等待工时（小时）',
  `pause_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '暂停工时（小时）',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '操作工ID（主操作工）',
  `worker_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作工编码',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作工姓名',
  `team_id` bigint(20) NULL DEFAULT NULL COMMENT '班组ID',
  `team_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组编码',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组名称',
  `helper_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '辅助人员ID列表（逗号分隔）',
  `helper_names` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '辅助人员姓名列表',
  `quality_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '质量状态：PENDING-待检，QUALIFIED-合格，UNQUALIFIED-不合格，PARTIAL-部分合格',
  `quality_check_id` bigint(20) NULL DEFAULT NULL COMMENT '质检记录ID',
  `quality_check_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '质检单号',
  `inspector_id` bigint(20) NULL DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验员姓名',
  `inspect_time` datetime NULL DEFAULT NULL COMMENT '检验时间',
  `report_channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '报工途径：PC-电脑端，APP-移动端，PDA-手持设备，AUTO-自动采集',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型：MANUAL-手工录入，SCAN-扫码，IMPORT-导入，INTERFACE-接口同步',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源单据编号',
  `barcode` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '条码信息',
  `submit_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '提交人',
  `submit_time` datetime NULL DEFAULT NULL COMMENT '提交时间',
  `audit_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核意见',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `deleted` tinyint(4) NULL DEFAULT 0 COMMENT '删除标志（0-未删除，1-已删除）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID（关联 wm_batch，统一批次管理）',
  `batch_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次号（关联 wm_batch.batch_code，统一批次管理）',
  PRIMARY KEY (`report_id`) USING BTREE,
  UNIQUE INDEX `uk_report_no`(`report_no`) USING BTREE,
  INDEX `idx_wo_id`(`wo_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_report_status`(`report_status`) USING BTREE,
  INDEX `idx_quality_status`(`quality_status`) USING BTREE,
  INDEX `idx_report_date`(`report_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_prd_work_report_batch_id`(`batch_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产报工表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of prd_work_report
-- ----------------------------
INSERT INTO `prd_work_report` VALUES (10001, '000000', 'WR-TEST-001', 'NORMAL', 'APPROVED', 10001, 'WO-TEST-001', NULL, 1001, 'IF20251110001', '自行车', NULL, 26, NULL, '件', NULL, NULL, NULL, 3, 'ASM', '装配', NULL, NULL, NULL, NULL, 1, 'GZZ0002', '工作站1', 1, '注塑车间#1', NULL, NULL, NULL, NULL, NULL, NULL, 30001, '测试白班', 50.0000, 50.0000, 48.0000, 2.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, '2026-06-20 00:00:00', '2026-06-20 08:00:00', '2026-06-20 15:30:00', 6.50, 0.00, 0.17, 0.00, 0.00, 30001, NULL, '张三', 30001, NULL, '装配A组', NULL, NULL, 'QUALIFIED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, '第一批装配报工:48良品+2报废', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0', NULL, NULL);

-- ----------------------------
-- Table structure for pu_contract
-- ----------------------------
DROP TABLE IF EXISTS `pu_contract`;
CREATE TABLE `pu_contract`  (
  `contract_id` bigint(20) NOT NULL COMMENT '合同ID',
  `contract_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '合同编号',
  `contract_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同名称',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `contract_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同类型 FRAMEWORK-框架合同 SPOT-现货合同 SERVICE-服务合同',
  `sign_date` datetime NULL DEFAULT NULL COMMENT '签订日期',
  `effective_date` datetime NULL DEFAULT NULL COMMENT '生效日期',
  `expiry_date` datetime NULL DEFAULT NULL COMMENT '失效日期',
  `total_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '合同总金额',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CNY' COMMENT '币种',
  `payment_terms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '付款条款',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '合同状态 DRAFT-草稿 APPROVING-审批中 APPROVED-已批准 EXECUTING-执行中 COMPLETED-已完成 TERMINATED-已终止 REJECTED-已驳回',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  `attr2` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扩展字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '扩展字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '扩展字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户编号',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志(0代表存在 2代表删除)',
  PRIMARY KEY (`contract_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购合同头表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_contract
-- ----------------------------

-- ----------------------------
-- Table structure for pu_contract_line
-- ----------------------------
DROP TABLE IF EXISTS `pu_contract_line`;
CREATE TABLE `pu_contract_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `contract_id` bigint(20) NOT NULL COMMENT '合同ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料规格',
  `unit` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '计量单位',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '数量',
  `line_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '行金额',
  `delivery_date` datetime NULL DEFAULT NULL COMMENT '交货日期',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  `attr2` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扩展字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '扩展字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '扩展字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户编号',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志(0代表存在 2代表删除)',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_contract_id`(`contract_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购合同行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_contract_line
-- ----------------------------

-- ----------------------------
-- Table structure for pu_purchase_order
-- ----------------------------
DROP TABLE IF EXISTS `pu_purchase_order`;
CREATE TABLE `pu_purchase_order`  (
  `order_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '采购订单ID',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '采购订单号',
  `requisition_id` bigint(20) NULL DEFAULT NULL COMMENT '来源申请ID',
  `requisition_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源申请单号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `order_date` datetime NULL DEFAULT NULL COMMENT '订单日期',
  `expected_arrival_date` datetime NULL DEFAULT NULL COMMENT '预计到货日期',
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CNY' COMMENT '币别',
  `exchange_rate` decimal(18, 6) NULL DEFAULT 1.000000 COMMENT '汇率',
  `total_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '含税总金额',
  `tax_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '税额',
  `discount_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '折扣金额',
  `freight_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '运费金额',
  `discount_rate` decimal(10, 2) NULL DEFAULT NULL COMMENT '折扣率',
  `payment_terms` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '付款条款',
  `delivery_terms` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '交货条款',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
  `contract_id` bigint(20) NULL DEFAULT NULL COMMENT '合同ID，关联pu_contract表（预留字段）',
  `contract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同编号（预留字段）',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '采购部门ID（关联sys_dept）',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购部门名称',
  `buyer_id` bigint(20) NULL DEFAULT NULL COMMENT '采购员ID（关联sys_user）',
  `buyer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购员名称',
  PRIMARY KEY (`order_id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2074143658025840643 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购订单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_purchase_order
-- ----------------------------
INSERT INTO `pu_purchase_order` VALUES (10001, 'PO-TEST-001', 10001, 'PR-TEST-001', 2062098763051089921, 'V00001', '钢板产出商', '2026-06-02 00:00:00', '2026-06-18 00:00:00', 'CNY', 1.000000, 2250000.00, 292500.00, 0.00, 5000.00, 0.00, 'NET30', 'FOB', 'APPROVED', '钢板500吨采购订单', NULL, NULL, NULL, NULL, '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `pu_purchase_order` VALUES (10002, 'PO-TEST-002', 10002, 'PR-TEST-002', 2062098763051089924, 'V00004', '天津化工材料有限公司', '2026-06-03 00:00:00', '2026-06-12 00:00:00', 'CNY', 1.000000, 17000.00, 2210.00, 0.00, 500.00, 0.00, 'NET15', 'FOB', 'RECEIVED', '润滑油200升采购订单(已收货)', NULL, NULL, NULL, NULL, '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `pu_purchase_order` VALUES (2062092892921307138, 'CGDD202606030001', 2062086009753870338, 'CGSQ202606030002', 2062098763051089921, 'V00001', '钢板产出商', '2026-06-03 16:43:52', '2026-06-05 00:00:00', 'CNY', 1.000000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'RECEIVED', '由采购申请【CGSQ202606030002】转单生成', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 16:43:52', -1, '2026-06-07 22:13:52', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `pu_purchase_order` VALUES (2074143658025840642, 'CGDD202607060001', 2074142238945996802, 'CGSQ202607060001', 2062098763051089925, 'V00005', '宏达五金有限公司', '2026-07-06 22:49:18', '2026-07-07 00:00:00', 'CNY', 1.000000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'RECEIVED', '由采购申请【CGSQ202607060001】转单生成', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-07-06 22:49:18', -1, '2026-07-06 23:22:42', '0', NULL, NULL, NULL, 114, '采购部门', 104, '蔡埋朶');

-- ----------------------------
-- Table structure for pu_purchase_order_line
-- ----------------------------
DROP TABLE IF EXISTS `pu_purchase_order_line`;
CREATE TABLE `pu_purchase_order_line`  (
  `line_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '明细行ID',
  `order_id` bigint(20) NOT NULL COMMENT '所属采购订单ID',
  `requisition_line_id` bigint(20) NULL DEFAULT NULL COMMENT '来源请购明细行ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '计量单位',
  `order_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '订购数量',
  `received_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '已收货数量',
  `receive_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '收货状态(PENDING/FULL_RECEIVED/PARTIAL_RECEIVED/OVER_RECEIVED/SHORT_RECEIVED)',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `tax_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '税率',
  `line_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '行金额（含税）',
  `sort_order` int(11) NULL DEFAULT NULL COMMENT '排序号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_order_id`(`order_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_pu_order_line_material`(`order_id`, `material_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2074143983021486083 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购订单明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_purchase_order_line
-- ----------------------------
INSERT INTO `pu_purchase_order_line` VALUES (10001, 10001, 10001, 1, 'IF20251016004', '钢板', NULL, '吨', 500.0000, 0.0000, 'PENDING', 4500.0000, 0.1300, 2250000.00, 1, '钢板500吨', '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `pu_purchase_order_line` VALUES (10002, 10002, 10002, 10, 'LUBRICANT', '润滑油', NULL, '升', 200.0000, 200.0000, 'FULL_RECEIVED', 85.0000, 0.1300, 17000.00, 1, '润滑油200升(已收货)', '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `pu_purchase_order_line` VALUES (2062184390943023106, 2062092892921307138, 2062092362954219521, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', 1000.0000, 1000.0000, 'FULL_RECEIVED', 300.0000, NULL, 300000.00, NULL, '测试', '000000', 103, 1, '2026-06-03 16:43:52', -1, '2026-06-07 22:13:52', '0');
INSERT INTO `pu_purchase_order_line` VALUES (2074143983021486082, 2074143658025840642, 2074142305039839233, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '双', 10.0000, 10.0000, 'FULL_RECEIVED', 25.0000, NULL, 250.00, NULL, NULL, '000000', 103, 1, '2026-07-06 22:49:18', 1, '2026-07-06 23:22:42', '0');

-- ----------------------------
-- Table structure for pu_purchase_requisition
-- ----------------------------
DROP TABLE IF EXISTS `pu_purchase_requisition`;
CREATE TABLE `pu_purchase_requisition`  (
  `requisition_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '采购申请ID',
  `requisition_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '采购申请单号',
  `requisition_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '采购申请名称',
  `applicant_id` bigint(20) NULL DEFAULT NULL COMMENT '申请人ID',
  `applicant_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '申请人名称',
  `requisition_date` datetime NULL DEFAULT NULL COMMENT '申请日期',
  `required_date` datetime NULL DEFAULT NULL COMMENT '需求日期',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源类型',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '流程ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '采购部门ID（关联sys_dept）',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采购部门名称',
  PRIMARY KEY (`requisition_id`) USING BTREE,
  UNIQUE INDEX `uk_requisition_no`(`requisition_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2074142238945996803 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购申请表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_purchase_requisition
-- ----------------------------
INSERT INTO `pu_purchase_requisition` VALUES (10001, 'PR-TEST-001', '钢板采购申请-2026Q3', 1, '管理员', '2026-06-01 00:00:00', '2026-06-20 00:00:00', 'MANUAL', 'APPROVED', NULL, '生产用钢板补充采购', NULL, NULL, NULL, NULL, '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (10002, 'PR-TEST-002', '润滑油采购申请-2026Q3', 1, '管理员', '2026-06-02 00:00:00', '2026-06-15 00:00:00', 'LOW_STOCK', 'APPROVED', NULL, '库存低于安全库存触发', NULL, NULL, NULL, NULL, '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2058434206744338434, '123123', '测试', NULL, NULL, '2026-05-24 00:00:00', '2026-05-31 00:00:00', 'MANUAL', 'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 14:25:33', 1, '2026-06-03 16:15:49', '1', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2058439491252805634, 'PU2026052401', 'cs2', NULL, NULL, '2026-05-24 00:00:00', '2026-05-31 00:00:00', 'MANUAL', 'DRAFT', 2058435612079448066, 'cs', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 14:46:33', 1, '2026-06-03 16:15:49', '1', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2058451208582094850, 'PU20260524002', '测试3', NULL, NULL, '2026-05-24 00:00:00', '2026-05-25 00:00:00', 'MANUAL', 'APPROVED', 2058435612079448066, '测试', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 15:33:07', 1, '2026-06-03 16:15:49', '1', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2058493299811049473, 'CGSQ202605240001', '采购', NULL, NULL, '2026-05-24 18:20:22', NULL, 'MANUAL', 'DRAFT', 2058435612079448066, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 18:20:22', 1, '2026-06-03 16:15:49', '1', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2062085313994334209, 'CGSQ202606030001', NULL, NULL, NULL, '2026-06-03 16:13:45', NULL, 'MANUAL', 'DRAFT', 2058435612079448066, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 16:13:45', 1, '2026-06-03 16:15:41', '1', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2062086009753870338, 'CGSQ202606030002', '钢板采购', NULL, NULL, '2026-06-03 00:00:00', '2026-06-04 00:00:00', 'MANUAL', 'ORDERED', 2058435612079448066, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 16:16:31', 1, '2026-06-03 16:43:52', '0', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2071442348321062913, 'CGSQ202606290001', '测试', NULL, NULL, '2026-06-29 00:00:00', '2026-06-30 00:00:00', 'MANUAL', 'APPROVED', 2058435612079448066, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-29 11:55:16', 1, '2026-06-29 11:57:37', '0', NULL, NULL);
INSERT INTO `pu_purchase_requisition` VALUES (2074142238945996802, 'CGSQ202607060001', '简易金属工具架连接件包', NULL, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00', 'MANUAL', 'ORDERED', 2058435612079448066, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-07-06 22:43:40', 1, '2026-07-06 22:49:18', '0', 114, '采购部门');

-- ----------------------------
-- Table structure for pu_purchase_requisition_line
-- ----------------------------
DROP TABLE IF EXISTS `pu_purchase_requisition_line`;
CREATE TABLE `pu_purchase_requisition_line`  (
  `line_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '明细行ID',
  `requisition_id` bigint(20) NOT NULL COMMENT '所属采购申请ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '计量单位',
  `apply_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '申请数量',
  `approved_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '已批准数量',
  `shortage_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '短缺数量',
  `required_date` datetime NULL DEFAULT NULL COMMENT '需求日期',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源类型',
  `source_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_requisition_id`(`requisition_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2074142305039839234 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购申请明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_purchase_requisition_line
-- ----------------------------
INSERT INTO `pu_purchase_requisition_line` VALUES (10001, 10001, 1, 'IF20251016004', '钢板', NULL, '吨', 500.0000, 500.0000, 500.0000, '2026-06-20 00:00:00', 'MANUAL', NULL, '钢板补充500吨', NULL, NULL, NULL, NULL, '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `pu_purchase_requisition_line` VALUES (10002, 10002, 10, 'LUBRICANT', '润滑油', NULL, '升', 200.0000, 200.0000, 200.0000, '2026-06-15 00:00:00', 'LOW_STOCK', NULL, '润滑油补充200升', NULL, NULL, NULL, NULL, '000000', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058439491252805635, 2058439491252805634, NULL, 'HOUSING', '箱体', '铸铁箱体', NULL, 20.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 14:46:33', 1, '2026-05-24 15:03:21', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058443720113676290, 2058439491252805634, NULL, 'JZQ350', '减速机JZQ-350', '标准型号', NULL, 20.0000, NULL, NULL, '2026-05-24 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 15:03:22', 1, '2026-05-24 15:31:54', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058450903584890881, 2058439491252805634, NULL, 'BOLT_M12', '螺栓M12', '8.8级', NULL, 10.0000, NULL, NULL, '2026-05-25 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 15:31:54', 1, '2026-05-24 15:31:56', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058450913575723009, 2058439491252805634, NULL, 'BOLT_M12', '螺栓M12', '8.8级', NULL, 10.0000, NULL, NULL, '2026-05-25 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 15:31:57', 1, '2026-06-03 16:15:49', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058451208582094851, 2058451208582094850, NULL, 'GEAR_SET', '齿轮组', '标准齿轮组', NULL, 11.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 15:33:07', 1, '2026-05-24 15:35:07', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058451713970561026, 2058451208582094850, NULL, 'JZQ350', '减速机JZQ-350', '标准型号', NULL, 2.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 15:35:07', 1, '2026-06-03 16:15:49', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058493299811049474, 2058493299811049473, NULL, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, 20.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 18:20:22', 1, '2026-05-24 18:51:56', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058501246347796481, 2058493299811049473, NULL, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, 20.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 18:20:22', 1, '2026-05-24 18:52:01', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2058501265104723969, 2058493299811049473, NULL, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, 20.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-05-24 18:20:22', 1, '2026-06-03 16:15:49', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2062085314069831681, 2062085313994334209, NULL, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', 1000.0000, NULL, NULL, '2026-06-04 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 16:13:45', 1, '2026-06-03 16:15:41', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2062086009774841858, 2062086009753870338, NULL, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', 1000.0000, NULL, NULL, '2026-06-03 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 16:16:31', 1, '2026-06-03 16:41:36', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2062092322290442241, 2062086009753870338, NULL, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', 1000.0000, NULL, NULL, '2026-06-03 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 16:16:31', 1, '2026-06-03 16:41:45', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2062092362954219521, 2062086009753870338, NULL, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', 1000.0000, NULL, NULL, '2026-06-03 00:00:00', NULL, NULL, '测试', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 16:16:31', 1, '2026-06-03 16:16:31', '0');
INSERT INTO `pu_purchase_requisition_line` VALUES (2071442348346228737, 2071442348321062913, 2, 'IF20251018001', '电机Y132M-4 7.5KW', '7.5KW 380V', '千克', 2.0000, NULL, NULL, '2026-06-30 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-29 11:55:16', 1, '2026-06-29 11:56:56', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2071442767617245185, 2071442348321062913, 2, 'IF20251018001', '电机Y132M-4 7.5KW', '7.5KW 380V', '千克', 2.0000, NULL, NULL, '2026-06-30 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-29 11:55:16', 1, '2026-06-29 11:55:16', '0');
INSERT INTO `pu_purchase_requisition_line` VALUES (2074142238945996803, 2074142238945996802, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '双', 10.0000, NULL, NULL, '2026-07-06 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-07-06 22:43:40', 1, '2026-07-06 22:43:55', '1');
INSERT INTO `pu_purchase_requisition_line` VALUES (2074142305039839233, 2074142238945996802, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '双', 10.0000, NULL, NULL, '2026-07-06 00:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-07-06 22:43:40', 1, '2026-07-06 22:43:40', '0');

-- ----------------------------
-- Table structure for pu_purchase_return
-- ----------------------------
DROP TABLE IF EXISTS `pu_purchase_return`;
CREATE TABLE `pu_purchase_return`  (
  `return_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '采购退货ID',
  `return_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '退货单号',
  `order_id` bigint(20) NULL DEFAULT NULL COMMENT '采购订单ID',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '采购订单号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `return_date` datetime NULL DEFAULT NULL COMMENT '退货日期',
  `return_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退货原因',
  `total_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '金额',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
  `attr1` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  PRIMARY KEY (`return_id`) USING BTREE,
  UNIQUE INDEX `uk_return_no`(`return_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购退货表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_purchase_return
-- ----------------------------

-- ----------------------------
-- Table structure for pu_purchase_return_line
-- ----------------------------
DROP TABLE IF EXISTS `pu_purchase_return_line`;
CREATE TABLE `pu_purchase_return_line`  (
  `line_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '退货行ID',
  `return_id` bigint(20) NOT NULL COMMENT '退货单ID',
  `order_line_id` bigint(20) NULL DEFAULT NULL COMMENT '采购订单行ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `return_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '退货数量',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '金额',
  `return_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退货原因',
  `sort_order` int(11) NULL DEFAULT NULL COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_return_id`(`return_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购退货行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_purchase_return_line
-- ----------------------------

-- ----------------------------
-- Table structure for pu_vendor_evaluation
-- ----------------------------
DROP TABLE IF EXISTS `pu_vendor_evaluation`;
CREATE TABLE `pu_vendor_evaluation`  (
  `evaluation_id` bigint(20) NOT NULL COMMENT '评估ID',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  `vendor_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `eval_period` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评估周期(如2026-05)',
  `delivery_rate` decimal(5, 2) NULL DEFAULT NULL COMMENT '交货及时率(%)',
  `quality_rate` decimal(5, 2) NULL DEFAULT NULL COMMENT '质量合格率(%)',
  `price_score` decimal(5, 2) NULL DEFAULT NULL COMMENT '价格竞争力评分',
  `total_score` decimal(5, 2) NULL DEFAULT NULL COMMENT '综合评分',
  `eval_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '评估等级(A/B/C/D)',
  `total_orders` int(11) NULL DEFAULT NULL COMMENT '评估期内总订单数',
  `on_time_orders` int(11) NULL DEFAULT NULL COMMENT '按时交货订单数',
  `total_received_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '总收货数量',
  `qualified_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '合格数量',
  `return_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '退货数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  PRIMARY KEY (`evaluation_id`) USING BTREE,
  UNIQUE INDEX `uk_vendor_period`(`vendor_id`, `eval_period`, `tenant_id`) USING BTREE,
  INDEX `idx_vendor`(`vendor_id`) USING BTREE,
  INDEX `idx_period`(`eval_period`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商评估记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of pu_vendor_evaluation
-- ----------------------------

-- ----------------------------
-- Table structure for sch_attendance
-- ----------------------------
DROP TABLE IF EXISTS `sch_attendance`;
CREATE TABLE `sch_attendance`  (
  `attendance_id` bigint(20) NOT NULL COMMENT '考勤记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '人员ID',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '人员姓名',
  `record_date` datetime NULL DEFAULT NULL COMMENT '考勤日期',
  `schedule_id` bigint(20) NULL DEFAULT NULL COMMENT '关联排班ID',
  `clock_in_time` datetime NULL DEFAULT NULL COMMENT '上班打卡时间',
  `clock_out_time` datetime NULL DEFAULT NULL COMMENT '下班打卡时间',
  `late_minutes` bigint(20) NULL DEFAULT 0 COMMENT '迟到分钟数',
  `leave_early_minutes` bigint(20) NULL DEFAULT 0 COMMENT '早退分钟数',
  `overtime_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '加班时长',
  `attendance_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '考勤结果: normal正常, late迟到, early_leave早退, absent旷工, leave请假',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '数据来源: manual手动, device设备同步',
  `device_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '打卡设备ID',
  `device_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '设备名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`attendance_id`) USING BTREE,
  INDEX `idx_worker_id`(`worker_id`) USING BTREE,
  INDEX `idx_record_date`(`record_date`) USING BTREE,
  INDEX `idx_attendance_status`(`attendance_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '考勤记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_attendance
-- ----------------------------
INSERT INTO `sch_attendance` VALUES (1, '000000', 19, NULL, '2026-01-09 00:00:00', NULL, '2026-01-09 10:44:37', '2026-01-09 19:46:15', 0, 0, 0.00, 'normal', 'manual', NULL, NULL, NULL, 103, 1, '2026-01-09 10:44:37', 1, '2026-01-09 17:06:23', '0');
INSERT INTO `sch_attendance` VALUES (2, '000000', 19, NULL, '2026-01-10 00:00:00', NULL, '2026-01-10 19:16:23', '2026-01-10 19:17:22', 0, 342, 0.00, 'late_early_leave', 'manual', NULL, NULL, NULL, 103, 1, '2026-01-10 19:16:23', 1, '2026-01-10 19:17:22', '0');
INSERT INTO `sch_attendance` VALUES (4, '000000', 19, '吴静', '2026-01-22 00:00:00', NULL, '2026-01-22 11:13:37', '2026-01-22 12:55:56', 133, 244, 0.00, 'late_early_leave', 'manual', NULL, NULL, NULL, 103, 1, '2026-01-22 11:13:37', 1, '2026-01-22 12:55:56', '0');
INSERT INTO `sch_attendance` VALUES (5, '000000', 19, '吴静', '2026-01-23 00:00:00', NULL, '2026-01-23 15:43:28', NULL, 403, 0, 0.00, 'late', 'manual', NULL, NULL, NULL, 103, 1, '2026-01-23 15:43:28', 1, '2026-01-23 15:43:28', '0');
INSERT INTO `sch_attendance` VALUES (30001, '000000', 30001, '张三', '2026-06-01 00:00:00', 30001, '2026-06-01 07:55:00', '2026-06-01 16:05:00', 0, 0, 0.00, 'normal', 'device', 'PUNCH-001', '1号考勤机', NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_attendance` VALUES (30002, '000000', 30003, '王五', '2026-06-01 00:00:00', 30002, '2026-06-01 08:05:00', '2026-06-01 16:03:00', 5, 0, 0.00, 'late', 'device', 'PUNCH-001', '1号考勤机', '迟到5分钟', 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_attendance` VALUES (30003, '000000', 30004, '赵六', '2026-06-01 00:00:00', 30003, '2026-06-01 07:50:00', '2026-06-01 16:10:00', 0, 0, 0.00, 'normal', 'device', 'PUNCH-001', '1号考勤机', NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_attendance` VALUES (30004, '000000', 30002, '李四', '2026-06-01 00:00:00', 30004, '2026-06-01 07:58:00', '2026-06-01 16:00:00', 0, 0, 0.00, 'normal', 'device', 'PUNCH-002', '2号考勤机', NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_attendance` VALUES (30005, '000000', 30005, '钱七', '2026-06-01 00:00:00', 30005, '2026-06-01 08:00:00', '2026-06-01 15:50:00', 0, 10, 0.00, 'early_leave', 'device', 'PUNCH-002', '2号考勤机', '早退10分钟', 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_attendance` VALUES (30006, '000000', 30006, '孙八', '2026-06-01 00:00:00', 30006, '2026-06-01 07:52:00', '2026-06-01 16:08:00', 0, 0, 0.00, 'normal', 'device', 'PUNCH-002', '2号考勤机', NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_attendance` VALUES (2052577815196393473, '000000', 12, '张明', '2026-05-08 00:00:00', NULL, '2026-05-08 10:34:21', NULL, 0, 0, 0.00, 'normal', 'manual', NULL, NULL, NULL, 103, 1, '2026-05-08 10:34:21', 1, '2026-05-08 10:34:21', '0');

-- ----------------------------
-- Table structure for sch_employee_schedule
-- ----------------------------
DROP TABLE IF EXISTS `sch_employee_schedule`;
CREATE TABLE `sch_employee_schedule`  (
  `schedule_id` bigint(20) NOT NULL COMMENT '员工排班ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '排班计划ID',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '人员ID',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '人员姓名',
  `team_id` bigint(20) NULL DEFAULT NULL COMMENT '班组ID',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组名称',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '班次ID',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次名称',
  `scheduled_date` datetime NULL DEFAULT NULL COMMENT '排班日期',
  `start_time` datetime NULL DEFAULT NULL COMMENT '排班开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '排班结束时间',
  `actual_shift_id` bigint(20) NULL DEFAULT NULL COMMENT '实际班次ID（用于调班覆盖）',
  `schedule_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '排班状态',
  `is_working_day` tinyint(4) NULL DEFAULT 1 COMMENT '是否工作日 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`schedule_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_worker_id`(`worker_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_shift_id`(`shift_id`) USING BTREE,
  INDEX `idx_scheduled_date`(`scheduled_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工排班明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_employee_schedule
-- ----------------------------
INSERT INTO `sch_employee_schedule` VALUES (4871, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-01 00:00:00', '2025-12-01 01:00:00', '2025-12-01 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:52', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4872, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-01 00:00:00', '2025-12-01 01:00:00', '2025-12-01 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4873, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-01 00:00:00', '2025-12-01 09:00:00', '2025-12-01 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4874, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-01 00:00:00', '2025-12-01 17:00:00', '2025-12-02 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4875, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-01 00:00:00', '2025-12-01 17:00:00', '2025-12-02 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4876, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-01 00:00:00', '2025-12-01 01:00:00', '2025-12-01 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4877, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-02 00:00:00', '2025-12-02 01:00:00', '2025-12-02 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4878, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-02 00:00:00', '2025-12-02 01:00:00', '2025-12-02 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4879, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-02 00:00:00', '2025-12-02 09:00:00', '2025-12-02 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4880, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-02 00:00:00', '2025-12-02 17:00:00', '2025-12-03 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4881, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-02 00:00:00', '2025-12-02 17:00:00', '2025-12-03 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4882, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-02 00:00:00', '2025-12-02 01:00:00', '2025-12-02 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4883, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-03 00:00:00', '2025-12-03 01:00:00', '2025-12-03 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4884, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-03 00:00:00', '2025-12-03 01:00:00', '2025-12-03 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4885, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-03 00:00:00', '2025-12-03 09:00:00', '2025-12-03 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4886, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-03 00:00:00', '2025-12-03 17:00:00', '2025-12-04 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4887, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-03 00:00:00', '2025-12-03 17:00:00', '2025-12-04 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4888, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-03 00:00:00', '2025-12-03 01:00:00', '2025-12-03 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4889, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-04 00:00:00', '2025-12-04 01:00:00', '2025-12-04 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4890, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-04 00:00:00', '2025-12-04 01:00:00', '2025-12-04 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4891, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-04 00:00:00', '2025-12-04 09:00:00', '2025-12-04 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4892, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-04 00:00:00', '2025-12-04 17:00:00', '2025-12-05 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4893, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-04 00:00:00', '2025-12-04 17:00:00', '2025-12-05 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4894, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-04 00:00:00', '2025-12-04 01:00:00', '2025-12-04 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4895, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-05 00:00:00', '2025-12-05 01:00:00', '2025-12-05 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4896, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-05 00:00:00', '2025-12-05 01:00:00', '2025-12-05 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4897, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-05 00:00:00', '2025-12-05 09:00:00', '2025-12-05 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4898, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-05 00:00:00', '2025-12-05 17:00:00', '2025-12-06 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4899, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-05 00:00:00', '2025-12-05 17:00:00', '2025-12-06 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4900, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-05 00:00:00', '2025-12-05 01:00:00', '2025-12-05 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4901, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-06 00:00:00', '2025-12-06 01:00:00', '2025-12-06 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4902, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-06 00:00:00', '2025-12-06 01:00:00', '2025-12-06 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4903, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-06 00:00:00', '2025-12-06 09:00:00', '2025-12-06 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4904, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-06 00:00:00', '2025-12-06 17:00:00', '2025-12-07 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4905, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-06 00:00:00', '2025-12-06 17:00:00', '2025-12-07 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4906, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-06 00:00:00', '2025-12-06 01:00:00', '2025-12-06 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4907, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2025-12-07 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4908, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2025-12-07 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4909, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2025-12-07 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4910, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2025-12-07 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4911, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2025-12-07 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4912, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2025-12-07 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4913, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-08 00:00:00', '2025-12-08 09:00:00', '2025-12-08 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4914, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-08 00:00:00', '2025-12-08 09:00:00', '2025-12-08 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4915, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-08 00:00:00', '2025-12-08 17:00:00', '2025-12-09 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4916, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-08 00:00:00', '2025-12-08 01:00:00', '2025-12-08 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4917, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-08 00:00:00', '2025-12-08 01:00:00', '2025-12-08 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4918, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-08 00:00:00', '2025-12-08 09:00:00', '2025-12-08 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4919, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-09 00:00:00', '2025-12-09 09:00:00', '2025-12-09 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4920, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-09 00:00:00', '2025-12-09 09:00:00', '2025-12-09 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4921, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-09 00:00:00', '2025-12-09 17:00:00', '2025-12-10 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4922, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-09 00:00:00', '2025-12-09 01:00:00', '2025-12-09 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4923, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-09 00:00:00', '2025-12-09 01:00:00', '2025-12-09 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4924, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-09 00:00:00', '2025-12-09 09:00:00', '2025-12-09 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4925, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-10 00:00:00', '2025-12-10 09:00:00', '2025-12-10 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4926, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-10 00:00:00', '2025-12-10 09:00:00', '2025-12-10 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4927, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-10 00:00:00', '2025-12-10 17:00:00', '2025-12-11 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4928, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-10 00:00:00', '2025-12-10 01:00:00', '2025-12-10 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4929, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-10 00:00:00', '2025-12-10 01:00:00', '2025-12-10 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4930, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-10 00:00:00', '2025-12-10 09:00:00', '2025-12-10 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4931, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-11 00:00:00', '2025-12-11 09:00:00', '2025-12-11 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4932, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-11 00:00:00', '2025-12-11 09:00:00', '2025-12-11 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4933, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-11 00:00:00', '2025-12-11 17:00:00', '2025-12-12 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4934, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-11 00:00:00', '2025-12-11 01:00:00', '2025-12-11 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4935, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-11 00:00:00', '2025-12-11 01:00:00', '2025-12-11 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4936, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-11 00:00:00', '2025-12-11 09:00:00', '2025-12-11 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4937, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-12 00:00:00', '2025-12-12 09:00:00', '2025-12-12 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4938, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-12 00:00:00', '2025-12-12 09:00:00', '2025-12-12 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4939, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-12 00:00:00', '2025-12-12 17:00:00', '2025-12-13 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4940, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-12 00:00:00', '2025-12-12 01:00:00', '2025-12-12 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4941, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-12 00:00:00', '2025-12-12 01:00:00', '2025-12-12 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4942, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-12 00:00:00', '2025-12-12 09:00:00', '2025-12-12 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4943, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-13 00:00:00', '2025-12-13 09:00:00', '2025-12-13 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4944, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-13 00:00:00', '2025-12-13 09:00:00', '2025-12-13 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4945, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-13 00:00:00', '2025-12-13 17:00:00', '2025-12-14 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4946, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-13 00:00:00', '2025-12-13 01:00:00', '2025-12-13 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4947, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-13 00:00:00', '2025-12-13 01:00:00', '2025-12-13 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4948, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-13 00:00:00', '2025-12-13 09:00:00', '2025-12-13 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4949, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2025-12-14 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4950, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2025-12-14 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4951, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2025-12-14 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4952, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2025-12-14 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4953, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2025-12-14 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4954, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2025-12-14 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4955, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2025-12-15 00:00:00', '2025-12-15 17:00:00', '2025-12-16 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4956, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2025-12-15 00:00:00', '2025-12-15 17:00:00', '2025-12-16 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4957, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2025-12-15 00:00:00', '2025-12-15 01:00:00', '2025-12-15 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4958, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2025-12-15 00:00:00', '2025-12-15 09:00:00', '2025-12-15 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4959, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2025-12-15 00:00:00', '2025-12-15 09:00:00', '2025-12-15 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4960, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2025-12-15 00:00:00', '2025-12-15 17:00:00', '2025-12-16 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4961, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2025-12-16 00:00:00', '2025-12-16 17:00:00', '2025-12-17 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4962, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2025-12-16 00:00:00', '2025-12-16 17:00:00', '2025-12-17 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4963, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2025-12-16 00:00:00', '2025-12-16 01:00:00', '2025-12-16 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4964, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2025-12-16 00:00:00', '2025-12-16 09:00:00', '2025-12-16 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4965, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2025-12-16 00:00:00', '2025-12-16 09:00:00', '2025-12-16 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4966, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2025-12-16 00:00:00', '2025-12-16 17:00:00', '2025-12-17 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4967, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2025-12-17 00:00:00', '2025-12-17 17:00:00', '2025-12-18 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4968, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2025-12-17 00:00:00', '2025-12-17 17:00:00', '2025-12-18 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4969, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2025-12-17 00:00:00', '2025-12-17 01:00:00', '2025-12-17 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:52', '0');
INSERT INTO `sch_employee_schedule` VALUES (4970, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2025-12-17 00:00:00', '2025-12-17 09:00:00', '2025-12-17 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4971, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2025-12-17 00:00:00', '2025-12-17 09:00:00', '2025-12-17 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4972, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2025-12-17 00:00:00', '2025-12-17 17:00:00', '2025-12-18 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4973, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2025-12-18 00:00:00', '2025-12-18 17:00:00', '2025-12-19 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4974, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2025-12-18 00:00:00', '2025-12-18 17:00:00', '2025-12-19 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4975, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2025-12-18 00:00:00', '2025-12-18 01:00:00', '2025-12-18 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4976, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2025-12-18 00:00:00', '2025-12-18 09:00:00', '2025-12-18 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4977, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2025-12-18 00:00:00', '2025-12-18 09:00:00', '2025-12-18 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4978, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2025-12-18 00:00:00', '2025-12-18 17:00:00', '2025-12-19 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4979, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2025-12-19 00:00:00', '2025-12-19 17:00:00', '2025-12-20 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4980, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2025-12-19 00:00:00', '2025-12-19 17:00:00', '2025-12-20 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4981, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2025-12-19 00:00:00', '2025-12-19 01:00:00', '2025-12-19 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4982, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2025-12-19 00:00:00', '2025-12-19 09:00:00', '2025-12-19 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4983, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2025-12-19 00:00:00', '2025-12-19 09:00:00', '2025-12-19 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4984, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2025-12-19 00:00:00', '2025-12-19 17:00:00', '2025-12-20 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4985, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2025-12-20 00:00:00', '2025-12-20 17:00:00', '2025-12-21 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4986, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2025-12-20 00:00:00', '2025-12-20 17:00:00', '2025-12-21 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4987, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2025-12-20 00:00:00', '2025-12-20 01:00:00', '2025-12-20 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4988, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2025-12-20 00:00:00', '2025-12-20 09:00:00', '2025-12-20 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4989, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2025-12-20 00:00:00', '2025-12-20 09:00:00', '2025-12-20 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4990, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2025-12-20 00:00:00', '2025-12-20 17:00:00', '2025-12-21 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4991, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2025-12-21 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4992, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2025-12-21 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4993, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2025-12-21 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4994, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2025-12-21 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4995, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2025-12-21 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4996, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2025-12-21 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4997, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-22 00:00:00', '2025-12-22 01:00:00', '2025-12-22 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4998, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-22 00:00:00', '2025-12-22 01:00:00', '2025-12-22 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (4999, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-22 00:00:00', '2025-12-22 09:00:00', '2025-12-22 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5000, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-22 00:00:00', '2025-12-22 17:00:00', '2025-12-23 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5001, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-22 00:00:00', '2025-12-22 17:00:00', '2025-12-23 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5002, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-22 00:00:00', '2025-12-22 01:00:00', '2025-12-22 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5003, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-23 00:00:00', '2025-12-23 01:00:00', '2025-12-23 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5004, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-23 00:00:00', '2025-12-23 01:00:00', '2025-12-23 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5005, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-23 00:00:00', '2025-12-23 09:00:00', '2025-12-23 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5006, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-23 00:00:00', '2025-12-23 17:00:00', '2025-12-24 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5007, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-23 00:00:00', '2025-12-23 17:00:00', '2025-12-24 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5008, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-23 00:00:00', '2025-12-23 01:00:00', '2025-12-23 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5009, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-24 00:00:00', '2025-12-24 01:00:00', '2025-12-24 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5010, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-24 00:00:00', '2025-12-24 01:00:00', '2025-12-24 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5011, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-24 00:00:00', '2025-12-24 09:00:00', '2025-12-24 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5012, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-24 00:00:00', '2025-12-24 17:00:00', '2025-12-25 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5013, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-24 00:00:00', '2025-12-24 17:00:00', '2025-12-25 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5014, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-24 00:00:00', '2025-12-24 01:00:00', '2025-12-24 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5015, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-25 00:00:00', '2025-12-25 01:00:00', '2025-12-25 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5016, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-25 00:00:00', '2025-12-25 01:00:00', '2025-12-25 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5017, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-25 00:00:00', '2025-12-25 09:00:00', '2025-12-25 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5018, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-25 00:00:00', '2025-12-25 17:00:00', '2025-12-26 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5019, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-25 00:00:00', '2025-12-25 17:00:00', '2025-12-26 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5020, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-25 00:00:00', '2025-12-25 01:00:00', '2025-12-25 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5021, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-26 00:00:00', '2025-12-26 01:00:00', '2025-12-26 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5022, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-26 00:00:00', '2025-12-26 01:00:00', '2025-12-26 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5023, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-26 00:00:00', '2025-12-26 09:00:00', '2025-12-26 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5024, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-26 00:00:00', '2025-12-26 17:00:00', '2025-12-27 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5025, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-26 00:00:00', '2025-12-26 17:00:00', '2025-12-27 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5026, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-26 00:00:00', '2025-12-26 01:00:00', '2025-12-26 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5027, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2025-12-27 00:00:00', '2025-12-27 01:00:00', '2025-12-27 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5028, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2025-12-27 00:00:00', '2025-12-27 01:00:00', '2025-12-27 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5029, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2025-12-27 00:00:00', '2025-12-27 09:00:00', '2025-12-27 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5030, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2025-12-27 00:00:00', '2025-12-27 17:00:00', '2025-12-28 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5031, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2025-12-27 00:00:00', '2025-12-27 17:00:00', '2025-12-28 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5032, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2025-12-27 00:00:00', '2025-12-27 01:00:00', '2025-12-27 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5033, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2025-12-28 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5034, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2025-12-28 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5035, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2025-12-28 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5036, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2025-12-28 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5037, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2025-12-28 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5038, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2025-12-28 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5039, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-29 00:00:00', '2025-12-29 09:00:00', '2025-12-29 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5040, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-29 00:00:00', '2025-12-29 09:00:00', '2025-12-29 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5041, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-29 00:00:00', '2025-12-29 17:00:00', '2025-12-30 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:53', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5042, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-29 00:00:00', '2025-12-29 01:00:00', '2025-12-29 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5043, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-29 00:00:00', '2025-12-29 01:00:00', '2025-12-29 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5044, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-29 00:00:00', '2025-12-29 09:00:00', '2025-12-29 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5045, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-30 00:00:00', '2025-12-30 09:00:00', '2025-12-30 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5046, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-30 00:00:00', '2025-12-30 09:00:00', '2025-12-30 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5047, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-30 00:00:00', '2025-12-30 17:00:00', '2025-12-31 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5048, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-30 00:00:00', '2025-12-30 01:00:00', '2025-12-30 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5049, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-30 00:00:00', '2025-12-30 01:00:00', '2025-12-30 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5050, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-30 00:00:00', '2025-12-30 09:00:00', '2025-12-30 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5051, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2025-12-31 00:00:00', '2025-12-31 09:00:00', '2025-12-31 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5052, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2025-12-31 00:00:00', '2025-12-31 09:00:00', '2025-12-31 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5053, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2025-12-31 00:00:00', '2025-12-31 17:00:00', '2026-01-01 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5054, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2025-12-31 00:00:00', '2025-12-31 01:00:00', '2025-12-31 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5055, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2025-12-31 00:00:00', '2025-12-31 01:00:00', '2025-12-31 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5056, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2025-12-31 00:00:00', '2025-12-31 09:00:00', '2025-12-31 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5057, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-01 00:00:00', '2026-01-01 09:00:00', '2026-01-01 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5058, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-01 00:00:00', '2026-01-01 09:00:00', '2026-01-01 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5059, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-01 00:00:00', '2026-01-01 17:00:00', '2026-01-02 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5060, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-01 00:00:00', '2026-01-01 01:00:00', '2026-01-01 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5061, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-01 00:00:00', '2026-01-01 01:00:00', '2026-01-01 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5062, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-01 00:00:00', '2026-01-01 09:00:00', '2026-01-01 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5063, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-02 00:00:00', '2026-01-02 09:00:00', '2026-01-02 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5064, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-02 00:00:00', '2026-01-02 09:00:00', '2026-01-02 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5065, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-02 00:00:00', '2026-01-02 17:00:00', '2026-01-03 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5066, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-02 00:00:00', '2026-01-02 01:00:00', '2026-01-02 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5067, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-02 00:00:00', '2026-01-02 01:00:00', '2026-01-02 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5068, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-02 00:00:00', '2026-01-02 09:00:00', '2026-01-02 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5069, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-03 00:00:00', '2026-01-03 09:00:00', '2026-01-03 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5070, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-03 00:00:00', '2026-01-03 09:00:00', '2026-01-03 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5071, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-03 00:00:00', '2026-01-03 17:00:00', '2026-01-04 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5072, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-03 00:00:00', '2026-01-03 01:00:00', '2026-01-03 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5073, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-03 00:00:00', '2026-01-03 01:00:00', '2026-01-03 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5074, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-03 00:00:00', '2026-01-03 09:00:00', '2026-01-03 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5075, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2026-01-04 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5076, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2026-01-04 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5077, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2026-01-04 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5078, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2026-01-04 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5079, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2026-01-04 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5080, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2026-01-04 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5081, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-05 00:00:00', '2026-01-05 17:00:00', '2026-01-06 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5082, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-05 00:00:00', '2026-01-05 17:00:00', '2026-01-06 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5083, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-05 00:00:00', '2026-01-05 01:00:00', '2026-01-05 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5084, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-05 00:00:00', '2026-01-05 09:00:00', '2026-01-05 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5085, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-05 00:00:00', '2026-01-05 09:00:00', '2026-01-05 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5086, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-05 00:00:00', '2026-01-05 17:00:00', '2026-01-06 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5087, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-06 00:00:00', '2026-01-06 17:00:00', '2026-01-07 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5088, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-06 00:00:00', '2026-01-06 17:00:00', '2026-01-07 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5089, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-06 00:00:00', '2026-01-06 01:00:00', '2026-01-06 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5090, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-06 00:00:00', '2026-01-06 09:00:00', '2026-01-06 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5091, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-06 00:00:00', '2026-01-06 09:00:00', '2026-01-06 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5092, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-06 00:00:00', '2026-01-06 17:00:00', '2026-01-07 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5093, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-07 00:00:00', '2026-01-07 17:00:00', '2026-01-08 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5094, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-07 00:00:00', '2026-01-07 17:00:00', '2026-01-08 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5095, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-07 00:00:00', '2026-01-07 01:00:00', '2026-01-07 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5096, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-07 00:00:00', '2026-01-07 09:00:00', '2026-01-07 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5097, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-07 00:00:00', '2026-01-07 09:00:00', '2026-01-07 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5098, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-07 00:00:00', '2026-01-07 17:00:00', '2026-01-08 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5099, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-08 00:00:00', '2026-01-08 17:00:00', '2026-01-09 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5100, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-08 00:00:00', '2026-01-08 17:00:00', '2026-01-09 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5101, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-08 00:00:00', '2026-01-08 01:00:00', '2026-01-08 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5102, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-08 00:00:00', '2026-01-08 09:00:00', '2026-01-08 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5103, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-08 00:00:00', '2026-01-08 09:00:00', '2026-01-08 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5104, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-08 00:00:00', '2026-01-08 17:00:00', '2026-01-09 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5105, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-09 00:00:00', '2026-01-09 17:00:00', '2026-01-10 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5106, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-09 00:00:00', '2026-01-09 17:00:00', '2026-01-10 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5107, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-09 00:00:00', '2026-01-09 01:00:00', '2026-01-09 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5108, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-09 00:00:00', '2026-01-09 09:00:00', '2026-01-09 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5109, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-09 00:00:00', '2026-01-09 09:00:00', '2026-01-09 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5110, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-09 00:00:00', '2026-01-09 17:00:00', '2026-01-10 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5111, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-10 00:00:00', '2026-01-10 17:00:00', '2026-01-11 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5112, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-10 00:00:00', '2026-01-10 17:00:00', '2026-01-11 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5113, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-10 00:00:00', '2026-01-10 01:00:00', '2026-01-10 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5114, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-10 00:00:00', '2026-01-10 09:00:00', '2026-01-10 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5115, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-10 00:00:00', '2026-01-10 09:00:00', '2026-01-10 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5116, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-10 00:00:00', '2026-01-10 17:00:00', '2026-01-11 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5117, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2026-01-11 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5118, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2026-01-11 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5119, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2026-01-11 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5120, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2026-01-11 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5121, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2026-01-11 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5122, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2026-01-11 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5123, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2026-01-12 00:00:00', '2026-01-12 01:00:00', '2026-01-12 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5124, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2026-01-12 00:00:00', '2026-01-12 01:00:00', '2026-01-12 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5125, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2026-01-12 00:00:00', '2026-01-12 09:00:00', '2026-01-12 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5126, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2026-01-12 00:00:00', '2026-01-12 17:00:00', '2026-01-13 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5127, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2026-01-12 00:00:00', '2026-01-12 17:00:00', '2026-01-13 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5128, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2026-01-12 00:00:00', '2026-01-12 01:00:00', '2026-01-12 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5129, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2026-01-13 00:00:00', '2026-01-13 01:00:00', '2026-01-13 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5130, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2026-01-13 00:00:00', '2026-01-13 01:00:00', '2026-01-13 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5131, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2026-01-13 00:00:00', '2026-01-13 09:00:00', '2026-01-13 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5132, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2026-01-13 00:00:00', '2026-01-13 17:00:00', '2026-01-14 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5133, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2026-01-13 00:00:00', '2026-01-13 17:00:00', '2026-01-14 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5134, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2026-01-13 00:00:00', '2026-01-13 01:00:00', '2026-01-13 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5135, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2026-01-14 00:00:00', '2026-01-14 01:00:00', '2026-01-14 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5136, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2026-01-14 00:00:00', '2026-01-14 01:00:00', '2026-01-14 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5137, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2026-01-14 00:00:00', '2026-01-14 09:00:00', '2026-01-14 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5138, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2026-01-14 00:00:00', '2026-01-14 17:00:00', '2026-01-15 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5139, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2026-01-14 00:00:00', '2026-01-14 17:00:00', '2026-01-15 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5140, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2026-01-14 00:00:00', '2026-01-14 01:00:00', '2026-01-14 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5141, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2026-01-15 00:00:00', '2026-01-15 01:00:00', '2026-01-15 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5142, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2026-01-15 00:00:00', '2026-01-15 01:00:00', '2026-01-15 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5143, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2026-01-15 00:00:00', '2026-01-15 09:00:00', '2026-01-15 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5144, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2026-01-15 00:00:00', '2026-01-15 17:00:00', '2026-01-16 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5145, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2026-01-15 00:00:00', '2026-01-15 17:00:00', '2026-01-16 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5146, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2026-01-15 00:00:00', '2026-01-15 01:00:00', '2026-01-15 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5147, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2026-01-16 00:00:00', '2026-01-16 01:00:00', '2026-01-16 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5148, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2026-01-16 00:00:00', '2026-01-16 01:00:00', '2026-01-16 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5149, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2026-01-16 00:00:00', '2026-01-16 09:00:00', '2026-01-16 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5150, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2026-01-16 00:00:00', '2026-01-16 17:00:00', '2026-01-17 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5151, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2026-01-16 00:00:00', '2026-01-16 17:00:00', '2026-01-17 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5152, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2026-01-16 00:00:00', '2026-01-16 01:00:00', '2026-01-16 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5153, '000000', 1, 13, '李芳华', 8, '组装一组', 1, '早班', '2026-01-17 00:00:00', '2026-01-17 01:00:00', '2026-01-17 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5154, '000000', 1, 17, '赵琳', 8, '组装一组', 1, '早班', '2026-01-17 00:00:00', '2026-01-17 01:00:00', '2026-01-17 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5155, '000000', 1, 14, '王思远', 9, '设备维护一组', 2, '中班', '2026-01-17 00:00:00', '2026-01-17 09:00:00', '2026-01-17 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:53', '0');
INSERT INTO `sch_employee_schedule` VALUES (5156, '000000', 1, 16, '陈志强', 10, '组装测试一组', 3, '晚班', '2026-01-17 00:00:00', '2026-01-17 17:00:00', '2026-01-18 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5157, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 3, '晚班', '2026-01-17 00:00:00', '2026-01-17 17:00:00', '2026-01-18 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5158, '000000', 1, 19, '吴静', 11, '注塑一组', 1, '早班', '2026-01-17 00:00:00', '2026-01-17 01:00:00', '2026-01-17 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5159, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2026-01-18 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5160, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2026-01-18 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5161, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2026-01-18 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5162, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2026-01-18 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5163, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2026-01-18 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5164, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2026-01-18 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5165, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-19 00:00:00', '2026-01-19 09:00:00', '2026-01-19 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5166, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-19 00:00:00', '2026-01-19 09:00:00', '2026-01-19 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5167, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-19 00:00:00', '2026-01-19 17:00:00', '2026-01-20 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5168, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-19 00:00:00', '2026-01-19 01:00:00', '2026-01-19 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5169, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-19 00:00:00', '2026-01-19 01:00:00', '2026-01-19 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5170, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-19 00:00:00', '2026-01-19 09:00:00', '2026-01-19 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5171, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-20 00:00:00', '2026-01-20 09:00:00', '2026-01-20 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5172, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-20 00:00:00', '2026-01-20 09:00:00', '2026-01-20 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5173, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-20 00:00:00', '2026-01-20 17:00:00', '2026-01-21 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5174, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-20 00:00:00', '2026-01-20 01:00:00', '2026-01-20 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5175, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-20 00:00:00', '2026-01-20 01:00:00', '2026-01-20 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5176, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-20 00:00:00', '2026-01-20 09:00:00', '2026-01-20 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5177, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-21 00:00:00', '2026-01-21 09:00:00', '2026-01-21 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5178, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-21 00:00:00', '2026-01-21 09:00:00', '2026-01-21 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5179, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-21 00:00:00', '2026-01-21 17:00:00', '2026-01-22 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5180, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-21 00:00:00', '2026-01-21 01:00:00', '2026-01-21 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5181, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-21 00:00:00', '2026-01-21 01:00:00', '2026-01-21 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5182, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-21 00:00:00', '2026-01-21 09:00:00', '2026-01-21 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5183, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-22 00:00:00', '2026-01-22 09:00:00', '2026-01-22 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5184, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-22 00:00:00', '2026-01-22 09:00:00', '2026-01-22 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5185, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-22 00:00:00', '2026-01-22 17:00:00', '2026-01-23 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5186, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-22 00:00:00', '2026-01-22 01:00:00', '2026-01-22 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5187, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-22 00:00:00', '2026-01-22 01:00:00', '2026-01-22 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5188, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-22 00:00:00', '2026-01-22 09:00:00', '2026-01-22 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5189, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-23 00:00:00', '2026-01-23 09:00:00', '2026-01-23 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5190, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-23 00:00:00', '2026-01-23 09:00:00', '2026-01-23 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5191, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-23 00:00:00', '2026-01-23 17:00:00', '2026-01-24 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5192, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-23 00:00:00', '2026-01-23 01:00:00', '2026-01-23 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5193, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-23 00:00:00', '2026-01-23 01:00:00', '2026-01-23 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5194, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-23 00:00:00', '2026-01-23 09:00:00', '2026-01-23 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5195, '000000', 1, 13, '李芳华', 8, '组装一组', 2, '中班', '2026-01-24 00:00:00', '2026-01-24 09:00:00', '2026-01-24 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5196, '000000', 1, 17, '赵琳', 8, '组装一组', 2, '中班', '2026-01-24 00:00:00', '2026-01-24 09:00:00', '2026-01-24 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5197, '000000', 1, 14, '王思远', 9, '设备维护一组', 3, '晚班', '2026-01-24 00:00:00', '2026-01-24 17:00:00', '2026-01-25 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5198, '000000', 1, 16, '陈志强', 10, '组装测试一组', 1, '早班', '2026-01-24 00:00:00', '2026-01-24 01:00:00', '2026-01-24 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5199, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 1, '早班', '2026-01-24 00:00:00', '2026-01-24 01:00:00', '2026-01-24 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5200, '000000', 1, 19, '吴静', 11, '注塑一组', 2, '中班', '2026-01-24 00:00:00', '2026-01-24 09:00:00', '2026-01-24 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5201, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2026-01-25 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5202, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2026-01-25 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5203, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2026-01-25 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5204, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2026-01-25 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5205, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2026-01-25 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5206, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2026-01-25 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5207, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-26 00:00:00', '2026-01-26 17:00:00', '2026-01-27 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5208, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-26 00:00:00', '2026-01-26 17:00:00', '2026-01-27 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5209, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-26 00:00:00', '2026-01-26 01:00:00', '2026-01-26 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5210, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-26 00:00:00', '2026-01-26 09:00:00', '2026-01-26 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5211, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-26 00:00:00', '2026-01-26 09:00:00', '2026-01-26 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5212, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-26 00:00:00', '2026-01-26 17:00:00', '2026-01-27 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5213, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-27 00:00:00', '2026-01-27 17:00:00', '2026-01-28 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5214, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-27 00:00:00', '2026-01-27 17:00:00', '2026-01-28 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5215, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-27 00:00:00', '2026-01-27 01:00:00', '2026-01-27 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5216, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-27 00:00:00', '2026-01-27 09:00:00', '2026-01-27 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5217, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-27 00:00:00', '2026-01-27 09:00:00', '2026-01-27 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5218, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-27 00:00:00', '2026-01-27 17:00:00', '2026-01-28 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5219, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-28 00:00:00', '2026-01-28 17:00:00', '2026-01-29 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5220, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-28 00:00:00', '2026-01-28 17:00:00', '2026-01-29 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5221, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-28 00:00:00', '2026-01-28 01:00:00', '2026-01-28 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5222, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-28 00:00:00', '2026-01-28 09:00:00', '2026-01-28 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5223, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-28 00:00:00', '2026-01-28 09:00:00', '2026-01-28 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5224, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-28 00:00:00', '2026-01-28 17:00:00', '2026-01-29 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5225, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-29 00:00:00', '2026-01-29 17:00:00', '2026-01-30 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5226, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-29 00:00:00', '2026-01-29 17:00:00', '2026-01-30 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5227, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-29 00:00:00', '2026-01-29 01:00:00', '2026-01-29 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5228, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-29 00:00:00', '2026-01-29 09:00:00', '2026-01-29 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5229, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-29 00:00:00', '2026-01-29 09:00:00', '2026-01-29 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5230, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-29 00:00:00', '2026-01-29 17:00:00', '2026-01-30 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5231, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-30 00:00:00', '2026-01-30 17:00:00', '2026-01-31 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5232, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-30 00:00:00', '2026-01-30 17:00:00', '2026-01-31 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5233, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-30 00:00:00', '2026-01-30 01:00:00', '2026-01-30 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5234, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-30 00:00:00', '2026-01-30 09:00:00', '2026-01-30 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5235, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-30 00:00:00', '2026-01-30 09:00:00', '2026-01-30 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5236, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-30 00:00:00', '2026-01-30 17:00:00', '2026-01-31 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5237, '000000', 1, 13, '李芳华', 8, '组装一组', 3, '晚班', '2026-01-31 00:00:00', '2026-01-31 17:00:00', '2026-02-01 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5238, '000000', 1, 17, '赵琳', 8, '组装一组', 3, '晚班', '2026-01-31 00:00:00', '2026-01-31 17:00:00', '2026-02-01 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5239, '000000', 1, 14, '王思远', 9, '设备维护一组', 1, '早班', '2026-01-31 00:00:00', '2026-01-31 01:00:00', '2026-01-31 09:00:00', 1, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5240, '000000', 1, 16, '陈志强', 10, '组装测试一组', 2, '中班', '2026-01-31 00:00:00', '2026-01-31 09:00:00', '2026-01-31 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5241, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', 2, '中班', '2026-01-31 00:00:00', '2026-01-31 09:00:00', '2026-01-31 17:00:00', 2, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5242, '000000', 1, 19, '吴静', 11, '注塑一组', 3, '晚班', '2026-01-31 00:00:00', '2026-01-31 17:00:00', '2026-02-01 01:00:00', 3, 'normal', 1, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5243, '000000', 1, 13, '李芳华', 8, '组装一组', -1, NULL, '2026-02-01 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5244, '000000', 1, 17, '赵琳', 8, '组装一组', -1, NULL, '2026-02-01 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5245, '000000', 1, 14, '王思远', 9, '设备维护一组', -1, NULL, '2026-02-01 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5246, '000000', 1, 16, '陈志强', 10, '组装测试一组', -1, NULL, '2026-02-01 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5247, '000000', 1, 15, '刘薇薇', 10, '组装测试一组', -1, NULL, '2026-02-01 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (5248, '000000', 1, 19, '吴静', 11, '注塑一组', -1, NULL, '2026-02-01 00:00:00', NULL, NULL, NULL, 'rest', 0, NULL, 103, 1, '2026-01-07 17:54:54', 1, '2026-01-07 17:54:54', '0');
INSERT INTO `sch_employee_schedule` VALUES (30001, '000000', 30001, 30001, '张三', 30001, '装配A组', 30001, '测试白班', '2026-06-01 00:00:00', '2026-06-01 08:00:00', '2026-06-01 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30002, '000000', 30001, 30003, '王五', 30001, '装配A组', 30001, '测试白班', '2026-06-01 00:00:00', '2026-06-01 08:00:00', '2026-06-01 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30003, '000000', 30001, 30004, '赵六', 30001, '装配A组', 30001, '测试白班', '2026-06-01 00:00:00', '2026-06-01 08:00:00', '2026-06-01 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30004, '000000', 30001, 30002, '李四', 30002, '装配B组', 30001, '测试白班', '2026-06-01 00:00:00', '2026-06-01 08:00:00', '2026-06-01 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30005, '000000', 30001, 30005, '钱七', 30002, '装配B组', 30001, '测试白班', '2026-06-01 00:00:00', '2026-06-01 08:00:00', '2026-06-01 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30006, '000000', 30001, 30006, '孙八', 30002, '装配B组', 30001, '测试白班', '2026-06-01 00:00:00', '2026-06-01 08:00:00', '2026-06-01 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30007, '000000', 30001, 30001, '张三', 30001, '装配A组', 30001, '测试白班', '2026-06-02 00:00:00', '2026-06-02 08:00:00', '2026-06-02 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30008, '000000', 30001, 30003, '王五', 30001, '装配A组', 30001, '测试白班', '2026-06-02 00:00:00', '2026-06-02 08:00:00', '2026-06-02 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30009, '000000', 30001, 30004, '赵六', 30001, '装配A组', 30001, '测试白班', '2026-06-02 00:00:00', '2026-06-02 08:00:00', '2026-06-02 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30010, '000000', 30001, 30002, '李四', 30002, '装配B组', 30001, '测试白班', '2026-06-02 00:00:00', '2026-06-02 08:00:00', '2026-06-02 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30011, '000000', 30001, 30005, '钱七', 30002, '装配B组', 30001, '测试白班', '2026-06-02 00:00:00', '2026-06-02 08:00:00', '2026-06-02 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30012, '000000', 30001, 30006, '孙八', 30002, '装配B组', 30001, '测试白班', '2026-06-02 00:00:00', '2026-06-02 08:00:00', '2026-06-02 16:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30013, '000000', 30001, 30001, '张三', 30001, '装配A组', 30002, '测试中班', '2026-06-03 00:00:00', '2026-06-03 16:00:00', '2026-06-04 00:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30014, '000000', 30001, 30003, '王五', 30001, '装配A组', 30002, '测试中班', '2026-06-03 00:00:00', '2026-06-03 16:00:00', '2026-06-04 00:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30015, '000000', 30001, 30004, '赵六', 30001, '装配A组', 30002, '测试中班', '2026-06-03 00:00:00', '2026-06-03 16:00:00', '2026-06-04 00:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30016, '000000', 30001, 30002, '李四', 30002, '装配B组', 30002, '测试中班', '2026-06-03 00:00:00', '2026-06-03 16:00:00', '2026-06-04 00:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30017, '000000', 30001, 30005, '钱七', 30002, '装配B组', 30002, '测试中班', '2026-06-03 00:00:00', '2026-06-03 16:00:00', '2026-06-04 00:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_employee_schedule` VALUES (30018, '000000', 30001, 30006, '孙八', 30002, '装配B组', 30002, '测试中班', '2026-06-03 00:00:00', '2026-06-03 16:00:00', '2026-06-04 00:00:00', NULL, 'SCHEDULED', 1, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');

-- ----------------------------
-- Table structure for sch_holiday
-- ----------------------------
DROP TABLE IF EXISTS `sch_holiday`;
CREATE TABLE `sch_holiday`  (
  `holiday_id` bigint(20) NOT NULL COMMENT '节假日ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `holiday_date` datetime NOT NULL COMMENT '日期',
  `holiday_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '节假日名称',
  `holiday_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '类型',
  `year` bigint(20) NULL DEFAULT NULL COMMENT '年份',
  `is_working_day` tinyint(4) NULL DEFAULT 0 COMMENT '是否调休工作日 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`holiday_id`) USING BTREE,
  UNIQUE INDEX `uk_holiday_date`(`holiday_date`) USING BTREE,
  INDEX `idx_year`(`year`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '节假日设置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_holiday
-- ----------------------------
INSERT INTO `sch_holiday` VALUES (1, '000000', '2026-01-01 00:00:00', '元旦', 'legal', 2026, 0, NULL, 103, 1, '2025-12-18 10:11:13', 1, '2025-12-18 14:20:24', '0');
INSERT INTO `sch_holiday` VALUES (3, '000000', '2026-01-02 00:00:00', '元旦', 'legal', 2026, 0, NULL, 103, 1, '2025-12-18 15:05:01', 1, '2025-12-18 15:05:00', '0');
INSERT INTO `sch_holiday` VALUES (5, '000000', '2026-01-03 00:00:00', '元旦', 'legal', 2026, 1, NULL, 103, 1, '2025-12-18 15:41:03', 1, '2025-12-18 15:41:02', '0');

-- ----------------------------
-- Table structure for sch_plan
-- ----------------------------
DROP TABLE IF EXISTS `sch_plan`;
CREATE TABLE `sch_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '计划ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划编码',
  `plan_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `start_date` datetime NULL DEFAULT NULL COMMENT '开始日期',
  `end_date` datetime NULL DEFAULT NULL COMMENT '结束日期',
  `period_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '周期类型',
  `rotation_type` int(11) NULL DEFAULT NULL COMMENT '轮班方式',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态',
  `published_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发布人',
  `published_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  UNIQUE INDEX `uk_plan_code`(`plan_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '排班计划表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_plan
-- ----------------------------
INSERT INTO `sch_plan` VALUES (1, '000000', 'PBJH2025001', '12月组装三班倒', '2025-12-01 00:00:00', '2026-01-31 23:59:59', 'week', 3, 'published', 'admin', '2025-12-30 15:16:09', NULL, 103, 1, '2025-12-26 11:10:18', 1, '2026-01-07 16:18:39', '0');
INSERT INTO `sch_plan` VALUES (2, '000000', 'PBJH2025002', '测试', '2026-01-01 00:00:00', '2026-01-31 23:59:59', 'week', 1, 'draft', NULL, NULL, NULL, 103, 1, '2025-12-30 15:35:38', 1, '2025-12-30 15:44:51', '0');
INSERT INTO `sch_plan` VALUES (30001, '000000', 'PLAN-202606', '2026年6月排班计划', '2026-06-01 00:00:00', '2026-06-30 00:00:00', 'MONTHLY', 1, 'PUBLISHED', '管理员', '2026-05-25 10:00:00', '6月份标准排班', 100, 1, '2026-06-21 20:53:38', 1, '2026-07-04 23:38:49', '1');

-- ----------------------------
-- Table structure for sch_plan_team
-- ----------------------------
DROP TABLE IF EXISTS `sch_plan_team`;
CREATE TABLE `sch_plan_team`  (
  `record_id` bigint(20) NOT NULL COMMENT '记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_id` bigint(20) NOT NULL COMMENT '排班计划ID',
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '计划班组关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_plan_team
-- ----------------------------
INSERT INTO `sch_plan_team` VALUES (1, '000000', 1, 8, '组装一组', NULL, 103, 1, '2025-12-29 11:51:03', 1, '2025-12-29 11:51:03', '0');
INSERT INTO `sch_plan_team` VALUES (3, '000000', 1, 9, '设备维护一组', NULL, 103, 1, '2025-12-29 17:59:20', 1, '2025-12-29 17:59:19', '0');
INSERT INTO `sch_plan_team` VALUES (4, '000000', 1, 10, '组装测试一组', NULL, 103, 1, '2025-12-29 17:59:20', 1, '2025-12-29 17:59:19', '0');
INSERT INTO `sch_plan_team` VALUES (5, '000000', 1, 11, '注塑一组', NULL, 103, 1, '2025-12-30 15:15:53', 1, '2025-12-30 15:15:53', '0');
INSERT INTO `sch_plan_team` VALUES (30001, '000000', 30001, 30001, '装配A组', '装配A组纳入6月排班', 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_plan_team` VALUES (30002, '000000', 30001, 30002, '装配B组', '装配B组纳入6月排班', 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');

-- ----------------------------
-- Table structure for sch_punch_rule
-- ----------------------------
DROP TABLE IF EXISTS `sch_punch_rule`;
CREATE TABLE `sch_punch_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `rule_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '适用班次ID（NULL表示所有班次）',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次名称',
  `work_start_time` time NULL DEFAULT NULL COMMENT '上班时间',
  `work_end_time` time NULL DEFAULT NULL COMMENT '下班时间',
  `allow_clock_in_start` time NULL DEFAULT NULL COMMENT '允许上班打卡开始时间',
  `allow_clock_in_end` time NULL DEFAULT NULL COMMENT '允许上班打卡结束时间',
  `allow_clock_out_start` time NULL DEFAULT NULL COMMENT '允许下班打卡开始时间',
  `allow_clock_out_end` time NULL DEFAULT NULL COMMENT '允许下班打卡结束时间',
  `location_lat` decimal(18, 8) NULL DEFAULT NULL COMMENT '打卡中心点纬度',
  `location_lng` decimal(18, 8) NULL DEFAULT NULL COMMENT '打卡中心点经度',
  `location_radius` bigint(20) NULL DEFAULT NULL COMMENT '打卡半径（米）',
  `location_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '打卡地点描述',
  `allow_wifi_ssids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '允许的Wi-Fi网络SSID列表（JSON数组）',
  `allow_bluetooth_devices` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '允许的蓝牙设备列表（JSON数组）',
  `allow_outdoor_punch` tinyint(4) NULL DEFAULT 0 COMMENT '是否允许外勤打卡 0:否 1:是',
  `outdoor_require_photo` tinyint(4) NULL DEFAULT 0 COMMENT '外勤打卡是否要求拍照 0:否 1:是',
  `allow_supplement` tinyint(4) NULL DEFAULT 1 COMMENT '是否允许补卡 0:否 1:是',
  `supplement_deadline` bigint(20) NULL DEFAULT 7 COMMENT '补卡申请截止天数',
  `late_threshold` bigint(20) NULL DEFAULT 0 COMMENT '迟到阈值（分钟）',
  `early_leave_threshold` bigint(20) NULL DEFAULT 0 COMMENT '早退阈值（分钟）',
  `absent_threshold` bigint(20) NULL DEFAULT 30 COMMENT '旷工阈值（分钟）',
  `is_enabled` tinyint(4) NULL DEFAULT 1 COMMENT '是否启用 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`rule_id`) USING BTREE,
  INDEX `idx_shift_id`(`shift_id`) USING BTREE,
  INDEX `idx_is_enabled`(`is_enabled`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '打卡规则表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_punch_rule
-- ----------------------------
INSERT INTO `sch_punch_rule` VALUES (1, '000000', '默认打卡规则', NULL, '所有班次', '09:00:00', '18:00:00', '08:00:00', '10:00:00', '17:00:00', '20:00:00', 29.60501700, 105.03659600, 500000, '内江市规展馆', NULL, NULL, 1, 1, 1, 3, 5, 5, 60, 1, '系统默认打卡规则，适用于所有班次', 103, 1, '2026-01-06 17:53:10', 1, '2026-01-10 18:10:32', '0');
INSERT INTO `sch_punch_rule` VALUES (2, '000000', '早班打卡规则', 1, '早班', '08:00:00', '17:00:00', '07:00:00', '09:00:00', '16:00:00', '19:00:00', 31.23041600, 121.47370100, 500, '上海市黄浦区', NULL, NULL, 1, 1, 1, 3, 5, 5, 60, 0, '早班专用打卡规则', 103, 1, '2026-01-06 17:53:10', 1, '2026-01-10 17:58:49', '0');
INSERT INTO `sch_punch_rule` VALUES (3, '000000', '早班打卡规则', 1, '早班', '01:00:00', '09:00:00', '00:30:00', '02:00:00', '08:00:00', '10:00:00', 29.60501700, 105.03659600, 500000, '内江市规展馆', NULL, NULL, 1, 1, 1, 3, 5, 5, 60, 1, '早班专用打卡规则', 103, 1, '2026-01-22 13:12:51', 1, '2026-01-22 13:12:51', '0');
INSERT INTO `sch_punch_rule` VALUES (4, '000000', '中班打卡规则', 2, '中班', '09:00:00', '17:00:00', '08:30:00', '10:00:00', '16:00:00', '18:00:00', 29.60501700, 105.03659600, 500000, '内江市规展馆', NULL, NULL, 1, 1, 1, 3, 5, 5, 60, 1, '中班专用打卡规则', 103, 1, '2026-01-22 13:12:51', 1, '2026-01-22 13:12:51', '0');
INSERT INTO `sch_punch_rule` VALUES (5, '000000', '晚班打卡规则', 3, '晚班', '17:00:00', '01:00:00', '16:30:00', '18:00:00', '00:00:00', '02:00:00', 29.60501700, 105.03659600, 500000, '内江市规展馆', NULL, NULL, 1, 1, 1, 3, 5, 5, 60, 1, '晚班专用打卡规则', 103, 1, '2026-01-22 13:12:51', 1, '2026-01-22 13:12:51', '0');
INSERT INTO `sch_punch_rule` VALUES (6, '000000', '长白班打卡规则', 4, '长白班', '08:00:00', '18:00:00', '07:30:00', '09:00:00', '17:00:00', '19:00:00', 29.60501700, 105.03659600, 500000, '内江市规展馆', NULL, NULL, 1, 1, 1, 3, 5, 5, 60, 1, '长白班专用打卡规则', 103, 1, '2026-01-22 13:12:51', 1, '2026-01-22 13:12:51', '0');

-- ----------------------------
-- Table structure for sch_shift
-- ----------------------------
DROP TABLE IF EXISTS `sch_shift`;
CREATE TABLE `sch_shift`  (
  `shift_id` bigint(20) NOT NULL COMMENT '班次ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `shift_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班次编码',
  `shift_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班次名称',
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '排班计划ID',
  `start_time` time NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` time NULL DEFAULT NULL COMMENT '结束时间',
  `work_hours` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '工时(小时)',
  `is_cross_day` tinyint(4) NULL DEFAULT 0 COMMENT '是否跨天 0:否 1:是',
  `rest_start_time` time NULL DEFAULT NULL COMMENT '休息开始时间',
  `rest_end_time` time NULL DEFAULT NULL COMMENT '休息结束时间',
  `late_threshold` bigint(20) NULL DEFAULT NULL COMMENT '迟到阈值(分钟)',
  `early_leave_threshold` bigint(20) NULL DEFAULT NULL COMMENT '早退阈值(分钟)',
  `shift_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次类别',
  `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班次显示颜色',
  `sort_order` bigint(20) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`shift_id`) USING BTREE,
  UNIQUE INDEX `uk_shift_code`(`shift_code`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班次定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_shift
-- ----------------------------
INSERT INTO `sch_shift` VALUES (1, '000000', 'PBJH2025001-1', '早班', 1, '01:00:00', '09:00:00', 8.00, 0, '05:30:00', '06:15:00', 5, 5, 'production', '#3986ce', 1, NULL, 103, 1, '2025-12-29 10:03:45', 1, '2025-12-29 11:26:27', '0');
INSERT INTO `sch_shift` VALUES (2, '000000', 'PBJH2025001-2', '中班', 1, '09:00:00', '17:00:00', 8.00, 0, '11:45:00', '12:30:00', 5, 5, 'production', '#d2dc12', 2, NULL, 103, 1, '2025-12-29 11:24:31', 1, '2025-12-29 11:24:46', '0');
INSERT INTO `sch_shift` VALUES (3, '000000', 'PBJH2025001-3', '晚班', 1, '17:00:00', '01:00:00', 8.00, 1, '22:15:00', '23:00:00', 5, 5, 'production', '#22d194', 3, '123312', 103, 1, '2025-12-29 11:28:15', 1, '2025-12-30 10:26:11', '0');
INSERT INTO `sch_shift` VALUES (4, '000000', 'PBJH2025002-1', '长白班', 2, '08:00:00', '18:00:00', 10.00, 0, '12:00:00', '13:30:00', 5, 5, 'production', '#d9cb30', 1, NULL, 103, 1, '2025-12-30 15:44:43', 1, '2025-12-30 15:44:43', '0');
INSERT INTO `sch_shift` VALUES (30001, '000000', 'TEST-DAY', '测试白班', 30001, '08:00:00', '16:00:00', 8.00, 0, '12:00:00', '13:00:00', 10, 10, 'DAY_SHIFT', '#4CAF50', 1, '白班8:00-16:00,午休1小时', 100, 1, '2026-06-21 20:53:38', 1, '2026-07-04 19:18:51', '1');
INSERT INTO `sch_shift` VALUES (30002, '000000', 'TEST-SWING', '测试中班', 30001, '16:00:00', '00:00:00', 8.00, 1, '18:00:00', '18:30:00', 10, 10, 'SWING_SHIFT', '#FF9800', 2, '中班16:00-00:00', 100, 1, '2026-06-21 20:53:38', 1, '2026-07-04 19:18:54', '1');
INSERT INTO `sch_shift` VALUES (30003, '000000', 'TEST-NIGHT', '测试夜班', 30001, '00:00:00', '08:00:00', 8.00, 1, '03:00:00', '03:30:00', 10, 10, 'NIGHT_SHIFT', '#9C27B0', 3, '夜班00:00-08:00', 100, 1, '2026-06-21 20:53:38', 1, '2026-07-04 19:18:57', '1');

-- ----------------------------
-- Table structure for sch_team
-- ----------------------------
DROP TABLE IF EXISTS `sch_team`;
CREATE TABLE `sch_team`  (
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `team_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班组编码',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班组名称',
  `team_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组类别',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '所属车间ID',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属车间名称',
  `leader_id` bigint(20) NULL DEFAULT NULL COMMENT '班组长ID',
  `leader_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '班组长姓名',
  `member_count` bigint(20) NULL DEFAULT 0 COMMENT '成员数量',
  `status` bigint(20) NULL DEFAULT 1 COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`team_id`) USING BTREE,
  UNIQUE INDEX `uk_team_code`(`team_code`) USING BTREE,
  INDEX `idx_workshop_id`(`workshop_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组设置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_team
-- ----------------------------
INSERT INTO `sch_team` VALUES (8, '000000', 'SC0001', '组装一组', 'Assembly Team', 1, '注塑车间#1', 13, '李芳华', 2, 1, NULL, 103, 1, '2025-12-24 11:56:31', 1, '2026-07-04 19:18:37', '1');
INSERT INTO `sch_team` VALUES (9, '000000', 'FZ0001', '设备维护一组', 'Equipment Maintenance Team', 1, '注塑车间#1', 14, '王思远', 1, 1, NULL, 103, 1, '2025-12-24 11:56:36', 1, '2026-07-04 19:18:37', '1');
INSERT INTO `sch_team` VALUES (10, '000000', 'SC0002', '冲压一组', 'FATP Team', 2, '注塑车间#1', 40004, '刘大伟', 2, 1, NULL, 103, 1, '2025-12-24 11:56:44', 1, '2026-07-04 23:00:01', '1');
INSERT INTO `sch_team` VALUES (11, '000000', 'SC0003', '冲压二组', 'Plastic Molding Team', 2, '注塑车间#1', 18, '周涛', 1, 1, NULL, 103, 1, '2025-12-30 15:14:55', 1, '2026-07-04 23:00:01', '1');
INSERT INTO `sch_team` VALUES (12, '000000', 'ZP0001', '装配一组', 'ASSEMBLY', 3, '装配车间#1', 40006, '王建华', 3, 1, '主装配班组', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:23:32', '0');
INSERT INTO `sch_team` VALUES (13, '000000', 'ZP0002', '调试组', 'DEBUG', 3, '装配车间#1', 40008, '张志强', 2, 1, '产品调试班组', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:23:32', '0');
INSERT INTO `sch_team` VALUES (14, '000000', 'BZ0001', '包装一组', 'PACKAGING', 4, '包装车间#1', 40009, '赵丽华', 2, 1, '包装主力班组', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:23:32', '0');
INSERT INTO `sch_team` VALUES (15, '000000', 'HJ0001', '焊接一组', 'WELDING', 5, '焊接车间#1', 40011, '周志明', 2, 1, '焊接主力班组', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:23:32', '0');
INSERT INTO `sch_team` VALUES (16, '000000', 'HJ0002', '切割组', 'CUTTING', 5, '焊接车间#1', 40013, '郑晓东', 1, 1, '切割班组', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:23:32', '0');
INSERT INTO `sch_team` VALUES (17, '000000', 'PT0001', '喷涂一组', 'SPRAYING', 6, '喷涂车间#1', 40014, '黄海龙', 2, 1, '喷涂主力班组', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:23:32', '0');
INSERT INTO `sch_team` VALUES (30001, '000000', 'TEST-TEAM-A', '装配A组', 'ASSEMBLY', 1, '注塑车间#1', 30001, '张三', 3, 1, '注塑车间装配班组', 100, 1, '2026-06-21 20:53:38', 1, '2026-07-04 19:18:37', '1');
INSERT INTO `sch_team` VALUES (30002, '000000', 'TEST-TEAM-B', '装配B组', 'ASSEMBLY', 2, '冲压车间#1', 30002, '李四', 3, 1, '冲压车间装配班组', 100, 1, '2026-06-21 20:53:38', 1, '2026-07-04 19:18:37', '1');

-- ----------------------------
-- Table structure for sch_team_member
-- ----------------------------
DROP TABLE IF EXISTS `sch_team_member`;
CREATE TABLE `sch_team_member`  (
  `member_id` bigint(20) NOT NULL COMMENT '成员ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `worker_id` bigint(20) NULL DEFAULT NULL COMMENT '人员ID',
  `worker_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '人员姓名',
  `worker_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工号',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色',
  `effective_date` datetime NULL DEFAULT NULL COMMENT '生效日期',
  `expire_date` datetime NULL DEFAULT NULL COMMENT '失效日期',
  `status` bigint(20) NULL DEFAULT 1 COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`member_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_worker_id`(`worker_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组成员表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_team_member
-- ----------------------------
INSERT INTO `sch_team_member` VALUES (2, '000000', 8, 12, '张明', 'SCZX20251222017', 'leader', '2025-12-24 00:00:00', NULL, 0, NULL, 103, 1, NULL, 1, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (4, '000000', 9, 14, '王思远', 'SCZX20251222019', 'leader', '2025-12-24 00:00:00', '2026-01-01 00:00:00', 1, NULL, 103, 1, NULL, 1, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (5, '000000', 8, 13, '李芳华', 'SCZX20251222018', 'leader', '2025-12-24 00:00:00', NULL, 1, NULL, 103, 1, NULL, 1, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (10, '000000', 8, 17, '赵琳', 'SCZX20251222022', 'member', '2025-12-24 00:00:00', NULL, 1, NULL, 103, 1, NULL, 1, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (11, '000000', 8, 40001, '马建国', 'EMP20260011', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (12, '000000', 8, 40002, '许海涛', 'EMP20260012', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (13, '000000', 9, 40003, '杨晓红', 'EMP20260013', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (14, '000000', 10, 16, '刘大伟', 'SCZX20251222021', 'leader', '2025-12-25 00:00:00', NULL, 1, NULL, 103, 1, NULL, 1, '2026-07-04 23:00:01', '1');
INSERT INTO `sch_team_member` VALUES (15, '000000', 10, 15, '陈小军', 'SCZX20251222020', 'member', '2025-12-25 00:00:00', NULL, 1, NULL, 103, 1, NULL, 1, '2026-07-04 23:00:01', '1');
INSERT INTO `sch_team_member` VALUES (16, '000000', 11, 19, '周涛', 'SCZX20251222024', 'leader', '2025-12-30 00:00:00', NULL, 1, NULL, 103, 1, NULL, 1, '2026-07-04 23:00:01', '1');
INSERT INTO `sch_team_member` VALUES (17, '000000', 12, 40006, '王建华', 'EMP20260016', 'leader', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (18, '000000', 12, 40007, '李秀英', 'EMP20260017', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (19, '000000', 12, 30001, '张三', 'EMP20260001', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (20, '000000', 13, 40008, '张志强', 'EMP20260018', 'leader', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (21, '000000', 13, 30005, '钱七', 'EMP20260005', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (22, '000000', 14, 40009, '赵丽华', 'EMP20260019', 'leader', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (23, '000000', 14, 40010, '孙国强', 'EMP20260020', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (24, '000000', 15, 40011, '周志明', 'EMP20260021', 'leader', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (25, '000000', 15, 40012, '吴小平', 'EMP20260022', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (26, '000000', 16, 40013, '郑晓东', 'EMP20260023', 'leader', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (27, '000000', 17, 40014, '黄海龙', 'EMP20260024', 'leader', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (28, '000000', 17, 40015, '林美玲', 'EMP20260025', 'member', '2026-01-01 00:00:00', NULL, 1, NULL, NULL, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_team_member` VALUES (30001, '000000', 30001, 30001, '张三', 'EMP20260001', 'LEADER', '2026-06-01 00:00:00', NULL, 1, '装配A组组长', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (30002, '000000', 30001, 30003, '王五', 'EMP20260003', 'MEMBER', '2026-06-01 00:00:00', NULL, 1, '装配A组成员', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (30003, '000000', 30001, 30004, '赵六', 'EMP20260004', 'MEMBER', '2026-06-01 00:00:00', NULL, 1, '装配A组成员', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (30004, '000000', 30002, 30002, '李四', 'EMP20260002', 'LEADER', '2026-06-01 00:00:00', NULL, 1, '装配B组组长', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (30005, '000000', 30002, 30005, '钱七', 'EMP20260005', 'MEMBER', '2026-06-01 00:00:00', NULL, 1, '装配B组成员', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 19:18:36', '1');
INSERT INTO `sch_team_member` VALUES (30006, '000000', 30002, 30006, '孙八', 'EMP20260006', 'MEMBER', '2026-06-01 00:00:00', NULL, 1, '装配B组成员', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 19:18:36', '1');

-- ----------------------------
-- Table structure for sch_team_schedule
-- ----------------------------
DROP TABLE IF EXISTS `sch_team_schedule`;
CREATE TABLE `sch_team_schedule`  (
  `team_schedule_id` bigint(20) NOT NULL COMMENT '班组排班ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `plan_id` bigint(20) NOT NULL COMMENT '排班计划ID',
  `team_id` bigint(20) NOT NULL COMMENT '班组ID',
  `shift_id` bigint(20) NULL DEFAULT NULL COMMENT '班次ID',
  `scheduled_date` datetime NULL DEFAULT NULL COMMENT '排班日期',
  `week_day` bigint(20) NULL DEFAULT NULL COMMENT '星期几(1-7)',
  `is_holiday` tinyint(4) NULL DEFAULT 0 COMMENT '是否节假日 0:否 1:是',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`team_schedule_id`) USING BTREE,
  INDEX `idx_plan_id`(`plan_id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_shift_id`(`shift_id`) USING BTREE,
  INDEX `idx_scheduled_date`(`scheduled_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '班组排班（模板）表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_team_schedule
-- ----------------------------
INSERT INTO `sch_team_schedule` VALUES (1, '000000', 1, 8, 1, '2025-12-01 00:00:00', NULL, 0, NULL, 103, 1, '2025-12-30 16:08:07', 1, '2025-12-30 16:08:06', '0');
INSERT INTO `sch_team_schedule` VALUES (2, '000000', 1, 9, 1, '2025-12-02 00:00:00', NULL, 0, NULL, 103, 1, '2025-12-30 16:09:48', 1, '2025-12-30 16:09:48', '0');
INSERT INTO `sch_team_schedule` VALUES (3, '000000', 1, 11, 2, '2025-12-30 00:00:00', NULL, 0, NULL, 103, 1, '2025-12-30 16:12:01', 1, '2025-12-30 16:12:00', '0');
INSERT INTO `sch_team_schedule` VALUES (5, '000000', 1, 10, 3, NULL, NULL, 0, NULL, 103, 1, '2025-12-30 16:40:28', 1, '2025-12-30 16:40:27', '0');
INSERT INTO `sch_team_schedule` VALUES (30001, '000000', 30001, 30001, 30001, '2026-06-01 00:00:00', 1, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30002, '000000', 30001, 30001, 30001, '2026-06-02 00:00:00', 2, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30003, '000000', 30001, 30001, 30002, '2026-06-03 00:00:00', 3, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30004, '000000', 30001, 30001, 30002, '2026-06-04 00:00:00', 4, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30005, '000000', 30001, 30001, 30003, '2026-06-05 00:00:00', 5, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30006, '000000', 30001, 30002, 30001, '2026-06-01 00:00:00', 1, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30007, '000000', 30001, 30002, 30001, '2026-06-02 00:00:00', 2, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30008, '000000', 30001, 30002, 30002, '2026-06-03 00:00:00', 3, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30009, '000000', 30001, 30002, 30002, '2026-06-04 00:00:00', 4, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');
INSERT INTO `sch_team_schedule` VALUES (30010, '000000', 30001, 30002, 30003, '2026-06-05 00:00:00', 5, 0, NULL, 100, 1, '2026-06-21 20:53:38', NULL, '2026-06-21 20:53:38', '0');

-- ----------------------------
-- Table structure for sch_worker
-- ----------------------------
DROP TABLE IF EXISTS `sch_worker`;
CREATE TABLE `sch_worker`  (
  `worker_id` bigint(20) NOT NULL COMMENT '工人档案ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '关联的系统用户ID',
  `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '所属车间ID（关联bd_workshop）',
  `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属车间名称',
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '员工姓名',
  `employee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '员工工号',
  `phonenumber` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '性别',
  `work_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工种',
  `skill_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '技能等级',
  `work_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '在岗状态',
  `entry_date` datetime NULL DEFAULT NULL COMMENT '入职日期',
  `regularization_date` datetime NULL DEFAULT NULL COMMENT '转正日期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`worker_id`) USING BTREE,
  UNIQUE INDEX `uk_employee_id`(`employee_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_work_status`(`work_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_workshop_id`(`workshop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产工人档案表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sch_worker
-- ----------------------------
INSERT INTO `sch_worker` VALUES (12, '000000', 105, 1, '注塑车间#1', '张明', 'SCZX20251222017', '13812345678', '0', 'welder', '1', 'active', '2025-12-01 00:00:00', '2025-12-08 00:00:00', '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (13, '000000', 106, 3, '装配车间#1', '李芳华', 'SCZX20251222018', '15687654321', '1', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (14, '000000', 107, 4, '包装车间#1', '王思远', 'SCZX20251222019', '18855551234', '0', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (15, '000000', 108, 5, '焊接车间#1', '刘薇薇', 'SCZX20251222020', '13922334455', '1', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (16, '000000', 109, 6, '喷涂车间#1', '陈志强', 'SCZX20251222021', '15199887766', '0', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (17, '000000', 110, 1, '注塑车间#1', '赵琳', 'SCZX20251222022', '13711223344', '0', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (18, '000000', 111, 2, '冲压车间#1', '周涛', 'SCZX20251222023', '15800998877', '0', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (19, '000000', 112, 3, '装配车间#1', '吴静', 'SCZX20251222024', '18255443322', '1', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (20, '000000', 113, 5, '焊接车间#1', '黄俊杰', 'SCZX20251222025', '13366778899', '0', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (21, '000000', 114, 4, '包装车间#1', '林小雨', 'SCZX20251222026', NULL, '1', NULL, NULL, 'active', NULL, NULL, '定时任务同步用户', 103, 1, '2025-12-22 14:21:40', 1, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (30001, '000000', NULL, 3, '装配车间#1', '张三', 'EMP20260001', '13800001001', '0', 'ASSEMBLY', 'SENIOR', 'ACTIVE', '2024-03-15 00:00:00', '2024-06-15 00:00:00', '装配工，高级技工', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (30002, '000000', NULL, 5, '焊接车间#1', '李四', 'EMP20260002', '13800001002', '0', 'WELDING', 'SENIOR', 'ACTIVE', '2023-08-01 00:00:00', '2023-11-01 00:00:00', '焊工，高级技工', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (30003, '000000', NULL, 1, '注塑车间#1', '王五', 'EMP20260003', '13800001003', '0', 'MACHINING', 'INTERMEDIATE', 'ACTIVE', '2025-01-10 00:00:00', '2025-04-10 00:00:00', '机加工，中级技工', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (30004, '000000', NULL, 3, '装配车间#1', '赵六', 'EMP20260004', '13800001004', '0', 'ASSEMBLY', 'INTERMEDIATE', 'ACTIVE', '2024-06-01 00:00:00', '2024-09-01 00:00:00', '装配工，中级技工', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (30005, '000000', NULL, 3, '装配车间#1', '钱七', 'EMP20260005', '13800001005', '0', 'QC', 'SENIOR', 'ACTIVE', '2022-05-20 00:00:00', '2022-08-20 00:00:00', '质检员，高级检验师', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (30006, '000000', NULL, 4, '包装车间#1', '孙八', 'EMP20260006', '13800001006', '0', 'PACKAGING', 'JUNIOR', 'ACTIVE', '2025-09-01 00:00:00', '2025-12-01 00:00:00', '包装工，初级', 100, 1, '2026-06-21 20:53:38', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40001, '000000', NULL, 1, '注塑车间#1', '马建国', 'EMP20260011', '13800001011', '0', 'MOLDING', 'SENIOR', 'ACTIVE', '2023-03-15 00:00:00', '2023-06-15 00:00:00', '注塑高级技师', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40002, '000000', NULL, 1, '注塑车间#1', '许海涛', 'EMP20260012', '13800001012', '0', 'MOLDING', 'INTERMEDIATE', 'ACTIVE', '2024-06-01 00:00:00', '2024-09-01 00:00:00', '注塑中级工', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40003, '000000', NULL, 1, '注塑车间#1', '杨晓红', 'EMP20260013', '13800001013', '1', 'QC', 'SENIOR', 'ACTIVE', '2022-01-10 00:00:00', '2022-04-10 00:00:00', '注塑车间质检员', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40004, '000000', NULL, 2, '冲压车间#1', '刘大伟', 'EMP20260014', '13800001014', '0', 'PUNCHING', 'SENIOR', 'ACTIVE', '2021-08-20 00:00:00', '2021-11-20 00:00:00', '冲压高级技师', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40005, '000000', NULL, 2, '冲压车间#1', '陈小军', 'EMP20260015', '13800001015', '0', 'PUNCHING', 'JUNIOR', 'ACTIVE', '2025-03-01 00:00:00', '2025-06-01 00:00:00', '冲压初级工', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40006, '000000', NULL, 3, '装配车间#1', '王建华', 'EMP20260016', '13800001016', '0', 'ASSEMBLY', 'SENIOR', 'ACTIVE', '2020-05-15 00:00:00', '2020-08-15 00:00:00', '装配高级技师', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40007, '000000', NULL, 3, '装配车间#1', '李秀英', 'EMP20260017', '13800001017', '1', 'ASSEMBLY', 'INTERMEDIATE', 'ACTIVE', '2023-09-01 00:00:00', '2023-12-01 00:00:00', '装配中级工', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40008, '000000', NULL, 3, '装配车间#1', '张志强', 'EMP20260018', '13800001018', '0', 'DEBUG', 'SENIOR', 'ACTIVE', '2021-02-20 00:00:00', '2021-05-20 00:00:00', '调试技师', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40009, '000000', NULL, 4, '包装车间#1', '赵丽华', 'EMP20260019', '13800001019', '1', 'PACKAGING', 'INTERMEDIATE', 'ACTIVE', '2024-01-15 00:00:00', '2024-04-15 00:00:00', '包装中级工', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40010, '000000', NULL, 4, '包装车间#1', '孙国强', 'EMP20260020', '13800001020', '0', 'PACKAGING', 'JUNIOR', 'ACTIVE', '2025-06-01 00:00:00', NULL, '包装初级工', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40011, '000000', NULL, 5, '焊接车间#1', '周志明', 'EMP20260021', '13800001021', '0', 'WELDING', 'SENIOR', 'ACTIVE', '2019-11-01 00:00:00', '2020-02-01 00:00:00', '焊接高级技师，持证', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40012, '000000', NULL, 5, '焊接车间#1', '吴小平', 'EMP20260022', '13800001022', '0', 'WELDING', 'INTERMEDIATE', 'ACTIVE', '2023-04-15 00:00:00', '2023-07-15 00:00:00', '焊接中级工', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40013, '000000', NULL, 5, '焊接车间#1', '郑晓东', 'EMP20260023', '13800001023', '0', 'CUTTING', 'SENIOR', 'ACTIVE', '2022-07-01 00:00:00', '2022-10-01 00:00:00', '激光切割技师', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40014, '000000', NULL, 6, '喷涂车间#1', '黄海龙', 'EMP20260024', '13800001024', '0', 'SPRAYING', 'SENIOR', 'ACTIVE', '2021-03-10 00:00:00', '2021-06-10 00:00:00', '喷涂高级技师', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');
INSERT INTO `sch_worker` VALUES (40015, '000000', NULL, 6, '喷涂车间#1', '林美玲', 'EMP20260025', '13800001025', '1', 'SPRAYING', 'JUNIOR', 'ACTIVE', '2025-01-15 00:00:00', NULL, '喷涂初级工', 103, NULL, '2026-07-04 23:00:01', NULL, '2026-07-04 23:00:01', '0');

-- ----------------------------
-- Table structure for so_contract
-- ----------------------------
DROP TABLE IF EXISTS `so_contract`;
CREATE TABLE `so_contract`  (
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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`contract_id`) USING BTREE,
  UNIQUE INDEX `uk_contract_no`(`contract_no`, `tenant_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售合同表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_contract
-- ----------------------------

-- ----------------------------
-- Table structure for so_contract_line
-- ----------------------------
DROP TABLE IF EXISTS `so_contract_line`;
CREATE TABLE `so_contract_line`  (
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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_contract_id`(`contract_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售合同明细行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_contract_line
-- ----------------------------

-- ----------------------------
-- Table structure for so_credit_usage
-- ----------------------------
DROP TABLE IF EXISTS `so_credit_usage`;
CREATE TABLE `so_credit_usage`  (
  `usage_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT 0,
  `client_id` bigint(20) NOT NULL,
  `so_id` bigint(20) NOT NULL,
  `amount` decimal(14, 2) NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'FROZEN',
  `freeze_time` datetime NULL DEFAULT NULL,
  `release_time` datetime NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`usage_id`) USING BTREE,
  INDEX `idx_client`(`client_id`) USING BTREE,
  INDEX `idx_so`(`so_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '信用占用记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_credit_usage
-- ----------------------------
INSERT INTO `so_credit_usage` VALUES (2068919152287997953, 0, 3, 2068918578570125314, 20000.00, 'FROZEN', '2026-06-22 12:48:59', NULL, 103, 1, '2026-06-22 12:48:59', 1, '2026-06-22 12:48:59');
INSERT INTO `so_credit_usage` VALUES (2071821464569303041, 0, 11, 2071797189309288450, 1859.15, 'FROZEN', '2026-06-30 13:01:44', NULL, 103, 1, '2026-06-30 13:01:44', 1, '2026-06-30 13:01:44');
INSERT INTO `so_credit_usage` VALUES (2071858652526862338, 0, 11, 2071797189309288450, 1859.15, 'FROZEN', '2026-06-30 15:29:31', NULL, 103, 1, '2026-06-30 15:29:31', 1, '2026-06-30 15:29:31');

-- ----------------------------
-- Table structure for so_customer_order
-- ----------------------------
DROP TABLE IF EXISTS `so_customer_order`;
CREATE TABLE `so_customer_order`  (
  `co_id` bigint(20) NOT NULL COMMENT '客户订单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `co_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户订单号',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人电话',
  `order_date` datetime NULL DEFAULT NULL COMMENT '客户下单日期',
  `requested_date` datetime NULL DEFAULT NULL COMMENT '客户要求交货日',
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '货币',
  `total_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '订单总金额（未税）',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '订单状态',
  `priority` bigint(20) NULL DEFAULT NULL COMMENT '订单优先级',
  `customer_po_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户采购订单号',
  `source_channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '订单来源',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '销售代表id',
  `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售代表',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`co_id`) USING BTREE,
  UNIQUE INDEX `uk_co_no`(`co_no`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_order_date`(`order_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户订单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_customer_order
-- ----------------------------
INSERT INTO `so_customer_order` VALUES (1, '000000', 'CO20251029001', 2, '小明科技有限公司', '李明', '0755-88888888', '2024-12-01 00:00:00', '2024-12-15 00:00:00', 'CNY', 25000.00, 'CONFIRMED', 0, NULL, 'PORTAL', 'C00003', NULL, 101, '麦得朶', '客户在线下单', 103, 1, '2024-12-01 09:30:00', 1, '2025-11-07 16:57:58', '0');
INSERT INTO `so_customer_order` VALUES (2, '000000', 'WEB20241201002', 3, NULL, '王芳', '13900139002', '2024-12-01 00:00:00', '2024-12-20 00:00:00', 'CNY', 15000.00, 'DRAFT', 0, NULL, 'PORTAL', NULL, NULL, NULL, NULL, '购物车订单', 103, 1, '2024-12-01 14:20:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (3, '000000', 'WEB20241202001', 4, NULL, '张伟', '13700137003', '2024-12-02 00:00:00', '2024-12-18 00:00:00', 'CNY', 8000.00, 'CONFIRMED', 0, NULL, 'PORTAL', NULL, NULL, NULL, NULL, '会员客户订单', 103, 1, '2024-12-02 11:15:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (4, '000000', 'CO20241201001', 5, NULL, '陈总', '13600136004', '2024-12-01 00:00:00', '2024-12-25 00:00:00', 'CNY', 150000.00, 'CONFIRMED', 0, 'PO-2024-12-001', 'SALES', NULL, NULL, NULL, NULL, '大客户年度订单', 103, 1, '2024-12-01 10:45:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (5, '000000', 'CO20241202002', 6, NULL, '刘经理', '13500135005', '2024-12-02 00:00:00', '2024-12-30 00:00:00', 'CNY', 85000.00, 'PENDING_REVIEW', 0, 'ABC-PO-20241202', 'SALES', NULL, NULL, NULL, NULL, '紧急项目订单', 103, 1, '2024-12-02 15:30:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (6, '000000', 'CO20241203001', 7, NULL, '赵主任', '13400134006', '2024-12-03 00:00:00', '2024-12-28 00:00:00', 'CNY', 220000.00, 'DRAFT', 0, 'XYZ-2024-PO-0123', 'SALES', NULL, NULL, NULL, NULL, '框架协议补充订单', 103, 1, '2024-12-03 09:15:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (7, '000000', 'EDI20241201001', 8, NULL, NULL, NULL, '2024-12-01 00:00:00', '2024-12-20 00:00:00', 'CNY', 500000.00, 'CONFIRMED', 0, 'WM-PO-20241201-56789', 'EDI', NULL, NULL, NULL, NULL, '沃尔玛自动补货订单', 103, 1, '2024-12-01 03:20:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (8, '000000', 'EDI20241202001', 9, NULL, NULL, NULL, '2024-12-02 00:00:00', '2024-12-22 00:00:00', 'CNY', 320000.00, 'CONFIRMED', 0, 'COSTCO-PO-20241202', 'EDI', NULL, NULL, NULL, NULL, '好市多定期采购', 103, 1, '2024-12-02 04:10:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (9, '000000', 'EDI20241203001', 10, NULL, NULL, NULL, '2024-12-03 00:00:00', '2024-12-25 00:00:00', 'CNY', 180000.00, 'PENDING_REVIEW', 0, 'CARREFOUR-PO-20241203', 'EDI', NULL, NULL, NULL, NULL, '家乐福库存补充', 103, 1, '2024-12-03 02:45:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (10, '000000', 'CO20241130001', 5, NULL, '陈总', '13600136004', '2024-11-30 00:00:00', '2024-12-15 00:00:00', 'CNY', 75000.00, 'CANCELLED', 0, 'PO-2024-11-005', 'SALES', NULL, NULL, NULL, NULL, '客户项目变更取消', 103, 1, '2024-11-30 14:20:00', 1, '2025-10-21 17:48:31', '0');
INSERT INTO `so_customer_order` VALUES (10001, '000000', 'CO-TEST-001', 2, '小明科技有限公司', '明经理', '13800002001', '2026-06-01 00:00:00', '2026-07-15 00:00:00', 'CNY', 150000.00, 'CONFIRMED', 2, 'PO-XMKJ-20260601', 'WEB', 'C00003', NULL, 1, '管理员', '测试订单:100辆自行车', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `so_customer_order` VALUES (10002, '000000', 'CO-TEST-002', 8, '沃尔玛（中国）投资有限公司', '采购部王经理', '13800008001', '2026-06-05 00:00:00', '2026-08-01 00:00:00', 'CNY', 280000.00, 'SUBMITTED', 1, 'PO-WMT-20260605', 'EDI', 'C00009', NULL, 1, '管理员', '测试订单:200辆自行车', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `so_customer_order` VALUES (2058005870289227777, '000000', 'CO20260523001', 2, '小明科技有限公司', '李明', '0755-88888888', '2026-05-23 00:00:00', '2026-05-31 00:00:00', 'CNY', 1000.00, NULL, 1, NULL, 'SALES', 'C00003', NULL, 101, '麦得朶', NULL, 103, 1, '2026-05-23 10:03:30', 1, '2026-05-23 10:03:30', '0');
INSERT INTO `so_customer_order` VALUES (2068918064298123265, '000000', 'CO20260622002', 3, '王芳贸易公司', '王芳', '020-66666666', '2026-06-22 00:00:00', '2026-06-30 00:00:00', 'CNY', 20000.00, NULL, 0, NULL, 'SALES', 'C00004', NULL, 101, '麦得朶', NULL, 103, 1, '2026-06-22 12:44:40', 1, '2026-06-22 12:44:40', '0');
INSERT INTO `so_customer_order` VALUES (2071788981538705410, '000000', 'CO20260630001', 11, '测试五金贸易公司', '张三', '0571-88886666', '2026-06-30 00:00:00', '2026-07-03 00:00:00', 'CNY', 2000.00, NULL, 0, NULL, 'SALES', 'C00012', NULL, 101, '麦得朶', NULL, 103, 1, '2026-06-30 10:52:40', 1, '2026-06-30 10:52:40', '0');
INSERT INTO `so_customer_order` VALUES (2071789957649715202, '000000', 'CO20260630002', 11, '测试五金贸易公司', '张三', '0571-88886666', '2026-06-30 00:00:00', '2026-07-03 00:00:00', 'CNY', 2000.00, NULL, 0, NULL, 'SALES', 'C00012', NULL, 101, '麦得朶', NULL, 103, 1, '2026-06-30 10:56:33', 1, '2026-06-30 11:11:21', '1');

-- ----------------------------
-- Table structure for so_invoice
-- ----------------------------
DROP TABLE IF EXISTS `so_invoice`;
CREATE TABLE `so_invoice`  (
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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`invoice_id`) USING BTREE,
  UNIQUE INDEX `uk_invoice_no`(`invoice_no`, `tenant_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_contract_id`(`contract_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售发票表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_invoice
-- ----------------------------

-- ----------------------------
-- Table structure for so_invoice_line
-- ----------------------------
DROP TABLE IF EXISTS `so_invoice_line`;
CREATE TABLE `so_invoice_line`  (
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
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_invoice_id`(`invoice_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售发票明细行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_invoice_line
-- ----------------------------

-- ----------------------------
-- Table structure for so_price_list
-- ----------------------------
DROP TABLE IF EXISTS `so_price_list`;
CREATE TABLE `so_price_list`  (
  `price_list_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT 0,
  `list_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `list_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `list_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'BASE',
  `client_id` bigint(20) NULL DEFAULT NULL,
  `currency` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CNY',
  `priority` int(11) NULL DEFAULT 0,
  `valid_from` date NULL DEFAULT NULL,
  `valid_to` date NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE',
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`price_list_id`) USING BTREE,
  UNIQUE INDEX `uk_code_tenant`(`list_code`, `tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售价格表头' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_price_list
-- ----------------------------

-- ----------------------------
-- Table structure for so_price_list_line
-- ----------------------------
DROP TABLE IF EXISTS `so_price_list_line`;
CREATE TABLE `so_price_list_line`  (
  `line_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT 0,
  `price_list_id` bigint(20) NOT NULL,
  `material_id` bigint(20) NOT NULL,
  `unit_price` decimal(14, 4) NOT NULL,
  `min_qty` decimal(14, 4) NULL DEFAULT 0.0000,
  `max_qty` decimal(14, 4) NULL DEFAULT NULL,
  `discount_rate` decimal(5, 2) NULL DEFAULT 100.00,
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_list_material`(`price_list_id`, `material_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售价格表行' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_price_list_line
-- ----------------------------

-- ----------------------------
-- Table structure for so_quotation
-- ----------------------------
DROP TABLE IF EXISTS `so_quotation`;
CREATE TABLE `so_quotation`  (
  `quotation_id` bigint(20) NOT NULL COMMENT '报价单ID',
  `quotation_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报价单号',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '销售代表ID',
  `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售代表',
  `quotation_date` datetime NULL DEFAULT NULL COMMENT '报价日期',
  `valid_until` datetime NULL DEFAULT NULL COMMENT '有效期至',
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '币种',
  `exchange_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '汇率',
  `total_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '总金额',
  `tax_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '税额',
  `discount_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '折扣额',
  `discount_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '折扣率',
  `payment_terms` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '付款条件',
  `delivery_terms` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '交货条件',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1（转换来源报价单号）',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`quotation_id`) USING BTREE,
  INDEX `idx_quotation_status`(`status`) USING BTREE,
  INDEX `idx_quotation_client`(`client_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售报价单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_quotation
-- ----------------------------
INSERT INTO `so_quotation` VALUES (2058006675960500226, 'BJ202605230002', 2, '小明科技有限公司', NULL, NULL, '2026-05-23 00:00:00', '2026-05-31 00:00:00', 'CNY', 1.000000, NULL, NULL, NULL, 95.0000, NULL, NULL, 'CONVERTED', NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-23 10:06:42', 1, '2026-05-23 10:40:52');

-- ----------------------------
-- Table structure for so_quotation_line
-- ----------------------------
DROP TABLE IF EXISTS `so_quotation_line`;
CREATE TABLE `so_quotation_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `quotation_id` bigint(20) NOT NULL COMMENT '报价单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '数量',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '金额',
  `tax_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '税率',
  `tax_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '税额',
  `delivery_date` datetime NULL DEFAULT NULL COMMENT '交货日期',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_quotation_line_mid`(`quotation_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售报价行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_quotation_line
-- ----------------------------
INSERT INTO `so_quotation_line` VALUES (2058006676027609090, 2058006675960500226, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', '26', '件', 10.0000, 100.0000, NULL, 3.0000, NULL, '2026-05-31 00:00:00', 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-23 10:06:42', 1, '2026-05-23 10:06:42');

-- ----------------------------
-- Table structure for so_return
-- ----------------------------
DROP TABLE IF EXISTS `so_return`;
CREATE TABLE `so_return`  (
  `return_id` bigint(20) NOT NULL COMMENT '退货单ID',
  `return_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '退货单号',
  `so_id` bigint(20) NULL DEFAULT NULL COMMENT '原销售订单ID',
  `so_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原销售订单号',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '销售代表ID',
  `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售代表',
  `return_date` datetime NULL DEFAULT NULL COMMENT '退货日期',
  `return_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '退货原因',
  `return_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '退货类型',
  `total_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '总金额',
  `tax_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '税额',
  `refund_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '退款金额',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '退货入库仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`return_id`) USING BTREE,
  INDEX `idx_return_so`(`so_id`) USING BTREE,
  INDEX `idx_return_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售退货单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_return
-- ----------------------------

-- ----------------------------
-- Table structure for so_return_line
-- ----------------------------
DROP TABLE IF EXISTS `so_return_line`;
CREATE TABLE `so_return_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `return_id` bigint(20) NOT NULL COMMENT '退货单ID',
  `so_line_id` bigint(20) NULL DEFAULT NULL COMMENT '原订单行ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `return_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '退货数量',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '金额',
  `tax_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '税率',
  `tax_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '税额',
  `return_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '退货原因',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_return_line_mid`(`return_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售退货行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_return_line
-- ----------------------------

-- ----------------------------
-- Table structure for so_sales_order_header
-- ----------------------------
DROP TABLE IF EXISTS `so_sales_order_header`;
CREATE TABLE `so_sales_order_header`  (
  `so_id` bigint(20) NOT NULL COMMENT '销售订单ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `so_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '销售订单编号',
  `co_id` bigint(20) NULL DEFAULT NULL COMMENT '来源客户订单',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '来源客户ID',
  `client_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `sales_rep_id` bigint(20) NULL DEFAULT NULL COMMENT '销售代表',
  `sales_rep_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售代表名称',
  `order_date` datetime NULL DEFAULT NULL COMMENT '订单生成日期',
  `promise_date` datetime NULL DEFAULT NULL COMMENT '承诺交货日',
  `requested_date` datetime NULL DEFAULT NULL COMMENT '客户要求交货日',
  `delivery_address_id` bigint(20) NULL DEFAULT NULL COMMENT '发货地址',
  `invoice_address_id` bigint(20) NULL DEFAULT NULL COMMENT '开票地址ID',
  `delivery_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发货地址详情',
  `invoice_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '开票地址详情',
  `payment_terms` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '付款条款',
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '货币',
  `exchange_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '汇率',
  `total_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '含税总金额',
  `tax_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '税款',
  `discount_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '整单折扣',
  `discount_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '整单折扣率',
  `credit_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '信用状态',
  `credit_check_time` datetime NULL DEFAULT NULL COMMENT '信用检查时间',
  `credit_blocked_by` bigint(20) NULL DEFAULT NULL COMMENT '信用拦截人',
  `order_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '订单状态',
  `project_id` bigint(20) NULL DEFAULT NULL COMMENT '项目ID',
  `is_pa_enabled` tinyint(4) NULL DEFAULT 0 COMMENT '是否启用项目配套发料 0:否 1:是',
  `confirmed_time` datetime NULL DEFAULT NULL COMMENT '确认时间',
  `fulfilled_time` datetime NULL DEFAULT NULL COMMENT '完全履行时间',
  `closed_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `cancelled_time` datetime NULL DEFAULT NULL COMMENT '取消时间',
  `cancelled_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '取消原因',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '销售部门ID（关联sys_dept）',
  `distribution_channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分销渠道',
  `customer_ref_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户参考号',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售部门名称',
  PRIMARY KEY (`so_id`) USING BTREE,
  UNIQUE INDEX `uk_so_no`(`so_no`) USING BTREE,
  INDEX `idx_co_id`(`co_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_order_status`(`order_status`) USING BTREE,
  INDEX `idx_order_date`(`order_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售订单主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_sales_order_header
-- ----------------------------
INSERT INTO `so_sales_order_header` VALUES (13, '000000', 'CO20251029001', 1, 2, '小明科技有限公司', 101, '麦得朶', '2024-12-01 00:00:00', '2025-12-30 00:00:00', '2025-12-31 00:00:00', NULL, NULL, NULL, NULL, NULL, 'CNY', 1.000000, 25000.00, NULL, NULL, NULL, 'CHECKED_OK', '2025-11-12 00:00:00', NULL, 'CONFIRMED', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '从客户订单同步', 103, 1, '2025-11-07 17:39:02', 1, '2026-05-13 14:55:15', '0', NULL, NULL);
INSERT INTO `so_sales_order_header` VALUES (14, '000000', 'WEB20241202001', 3, 4, NULL, NULL, NULL, '2024-12-02 00:00:00', NULL, '2024-12-18 00:00:00', NULL, NULL, NULL, NULL, NULL, 'CNY', 1.000000, 8000.00, NULL, NULL, NULL, 'NOT_CHECKED', NULL, NULL, 'DRAFT', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '从客户订单同步', 103, 1, '2025-11-07 17:39:02', 1, '2025-11-07 17:39:01', '0', NULL, NULL);
INSERT INTO `so_sales_order_header` VALUES (15, '000000', 'CO20241201001', 4, 5, NULL, NULL, NULL, '2024-12-01 00:00:00', NULL, '2024-12-25 00:00:00', NULL, NULL, NULL, NULL, NULL, 'CNY', 1.000000, 150000.00, NULL, NULL, NULL, 'NOT_CHECKED', NULL, NULL, 'DRAFT', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '从客户订单同步', 103, 1, '2025-11-07 17:39:02', 1, '2025-11-07 17:39:01', '0', NULL, NULL);
INSERT INTO `so_sales_order_header` VALUES (16, '000000', 'EDI20241201001', 7, 8, NULL, NULL, NULL, '2024-12-01 00:00:00', NULL, '2024-12-20 00:00:00', NULL, NULL, NULL, NULL, NULL, 'CNY', 1.000000, 500000.00, NULL, NULL, NULL, 'NOT_CHECKED', NULL, NULL, 'DRAFT', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '从客户订单同步', 103, 1, '2025-11-07 17:39:02', 1, '2025-11-07 17:39:01', '0', NULL, NULL);
INSERT INTO `so_sales_order_header` VALUES (17, '000000', 'EDI20241202001', 8, 9, NULL, NULL, NULL, '2024-12-02 00:00:00', NULL, '2024-12-22 00:00:00', NULL, NULL, NULL, NULL, NULL, 'CNY', 1.000000, 320000.00, NULL, NULL, NULL, 'NOT_CHECKED', NULL, NULL, 'DRAFT', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '从客户订单同步', 103, 1, '2025-11-07 17:39:02', 1, '2025-11-07 17:39:01', '0', NULL, NULL);
INSERT INTO `so_sales_order_header` VALUES (10001, '000000', 'SO-TEST-001', 10001, 2, '小明科技有限公司', 1, '管理员', '2026-06-01 00:00:00', '2026-07-10 00:00:00', '2026-07-15 00:00:00', NULL, NULL, '上海市浦东新区XX路100号', NULL, 'NET30', 'CNY', 1.000000, 150000.00, 19500.00, 0.00, 0.000000, 'PASSED', NULL, NULL, 'CONFIRMED', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '小明科技自行车订单', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0', NULL, NULL);
INSERT INTO `so_sales_order_header` VALUES (2058015273746894850, '000000', 'XSDD20260523001', NULL, 2, '小明科技有限公司', NULL, NULL, '2026-05-23 10:40:52', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CNY', 1.000000, NULL, NULL, NULL, 95.000000, NULL, NULL, NULL, 'DRAFT', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'BJ202605230002', NULL, NULL, NULL, '从报价单【BJ202605230002】转换', 103, 1, '2026-05-23 10:40:52', 1, '2026-05-23 10:40:52', '0', NULL, NULL);
INSERT INTO `so_sales_order_header` VALUES (2068918578570125314, '000000', 'XSDD20260622004', 2068918064298123265, 3, '王芳贸易公司', 101, '麦得朶', '2026-06-22 00:00:00', NULL, '2026-06-30 00:00:00', NULL, NULL, NULL, NULL, NULL, 'CNY', 1.000000, 20000.00, NULL, 100.00, 0.950000, 'PASSED', '2026-06-22 12:48:59', NULL, 'FULFILLED', NULL, 1, '2026-06-22 12:48:59', '2026-06-22 12:49:14', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-22 12:46:42', 1, '2026-06-22 12:49:14', '0', 2058473342767357953, NULL);
INSERT INTO `so_sales_order_header` VALUES (2071797189309288450, '000000', 'XSDD20260630001', 2071788981538705410, 11, '测试五金贸易公司', 101, '麦得朶', '2026-06-30 00:00:00', '2026-07-03 00:00:00', '2026-07-03 00:00:00', 12332, 123, '四川省', '成都市', '111', 'CNY', 1.000000, 1859.15, 57.00, 97.85, 5.000000, 'PASSED', '2026-06-30 15:29:31', NULL, 'CONFIRMED', NULL, 0, '2026-06-30 15:29:31', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-30 11:25:17', 1, '2026-06-30 15:29:31', '0', 2058473342767357953, NULL);

-- ----------------------------
-- Table structure for so_sales_order_line
-- ----------------------------
DROP TABLE IF EXISTS `so_sales_order_line`;
CREATE TABLE `so_sales_order_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '销售订单行表id',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `so_id` bigint(20) NOT NULL COMMENT '销售订单ID',
  `line_no` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行号（0010,0020）',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称（冗余）',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格（冗余）',
  `ordered_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '订单数量',
  `delivered_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已发货数量',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `unit_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '单价',
  `line_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '行金额 = qty * price',
  `tax_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '税率',
  `tax_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '行税额',
  `discount_percent` decimal(18, 6) NULL DEFAULT NULL COMMENT '行折扣率',
  `discount_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT '行折扣额',
  `requested_date` datetime NULL DEFAULT NULL COMMENT '行要求交期',
  `promise_date` datetime NULL DEFAULT NULL COMMENT '行承诺交期',
  `fulfilled_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已履行数量（已发货）',
  `production_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已投产数量',
  `purchase_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已采购数量',
  `reserved_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已预留库存数量',
  `line_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PENDING-待处理,SCHEDULED-已排产/已采购,PARTIALLY_FULFILLED-部分履行,FULFILLED-已履行,CANCELLED-已取消',
  `linked_mo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联生产工单ID',
  `linked_po_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采购订单ID',
  `reserved_batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留批次号',
  `reserved_serials` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '预留序列号列表（JSON）',
  `pa_group_id` bigint(20) NULL DEFAULT NULL COMMENT 'PA配套组ID',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '默认仓库ID',
  `delivery_priority` bigint(20) NULL DEFAULT NULL COMMENT '交付优先级',
  `customer_line_ref` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户行参考号',
  `sort_order` bigint(20) NULL DEFAULT NULL COMMENT '排序号',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_line_status`(`line_status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售订单行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of so_sales_order_line
-- ----------------------------
INSERT INTO `so_sales_order_line` VALUES (10001, '000000', 10001, '1', 1001, 'IF20251110001', '自行车', NULL, 100.0000, 0.0000, '件', 1500.000000, 150000.00, 0.130000, 19500.00, 0.000000, 0.00, NULL, NULL, 0.0000, 100.0000, 0.0000, 0.0000, 'IN_PRODUCTION', NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, 1, NULL, NULL, NULL, NULL, 'SO行:100辆自行车', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0');
INSERT INTO `so_sales_order_line` VALUES (2054455410809610242, '000000', 13, '101', 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', 1.0000, 0.0000, '', 7.000000, 7.00, 0.030000, 0.03, 0.030000, 0.95, '2025-12-31 00:00:00', '2025-12-30 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-05-13 14:55:15', 1, '2026-05-13 14:55:15', '0');
INSERT INTO `so_sales_order_line` VALUES (2058015273801420802, '000000', 2058015273746894850, NULL, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', 10.0000, 0.0000, '件', 100.000000, NULL, 3.000000, NULL, NULL, NULL, '2026-05-31 00:00:00', NULL, 0.0000, 0.0000, 0.0000, 0.0000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, '', 103, 1, '2026-05-23 10:40:52', 1, '2026-05-23 10:40:52', '0');
INSERT INTO `so_sales_order_line` VALUES (2068918578712731650, '000000', 2068918578570125314, '101', 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', 100.0000, 0.0000, '26', 200.000000, 20000.00, 0.030000, 600.00, 0.950000, 100.00, '2026-06-30 00:00:00', '2026-06-30 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-22 12:46:42', 1, '2026-06-22 12:47:00', '1');
INSERT INTO `so_sales_order_line` VALUES (2068918652628951041, '000000', 2068918578570125314, '101', 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', 100.0000, 0.0000, '26', 200.000000, 20000.00, 0.030000, 600.00, 0.950000, 100.00, '2026-06-30 00:00:00', '2026-06-30 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-22 12:47:00', 1, '2026-06-22 12:47:00', '0');
INSERT INTO `so_sales_order_line` VALUES (2071797189418340354, '000000', 2071797189309288450, '', 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 0.0000, '', 200.000000, 2000.00, NULL, NULL, NULL, NULL, '2026-07-03 00:00:00', NULL, 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-30 11:25:17', 1, '2026-06-30 11:55:52', '1');
INSERT INTO `so_sales_order_line` VALUES (2071804887140585473, '000000', 2071797189309288450, '', 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 0.0000, '套', 200.000000, 2000.00, 3.000000, 55.34, 95.000000, 100.00, '2026-07-03 00:00:00', '2026-07-03 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-30 11:55:52', 1, '2026-06-30 13:01:32', '1');
INSERT INTO `so_sales_order_line` VALUES (2071821415999262722, '000000', 2071797189309288450, '', 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 0.0000, '套', 200.000000, 2000.00, 3.000000, 57.00, 5.000000, 100.00, '2026-07-03 00:00:00', '2026-07-03 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-30 13:01:33', 1, '2026-06-30 13:02:09', '1');
INSERT INTO `so_sales_order_line` VALUES (2071821568453824513, '000000', 2071797189309288450, '', 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 0.0000, '套', 200.000000, 2000.00, 3.000000, 57.00, 5.000000, 100.00, '2026-07-03 00:00:00', '2026-07-03 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-30 13:02:09', 1, '2026-06-30 13:14:53', '1');
INSERT INTO `so_sales_order_line` VALUES (2071824773388619777, '000000', 2071797189309288450, '101', 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 0.0000, '套', 200.000000, 2000.00, 3.000000, 57.00, 5.000000, 100.00, '2026-07-03 00:00:00', '2026-07-03 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-30 13:14:53', 1, '2026-06-30 13:15:11', '1');
INSERT INTO `so_sales_order_line` VALUES (2071824847971733505, '000000', 2071797189309288450, '101', 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 0.0000, '套', 200.000000, 2000.00, 3.000000, 57.00, 5.000000, 100.00, '2026-07-03 00:00:00', '2026-07-03 00:00:00', 0.0000, 0.0000, 0.0000, 0.0000, 'PENDING', NULL, NULL, '', '', NULL, NULL, NULL, '', 1, NULL, NULL, NULL, NULL, '', 103, 1, '2026-06-30 13:15:11', 1, '2026-06-30 13:15:11', '0');

-- ----------------------------
-- Table structure for wm_arrival_notice
-- ----------------------------
DROP TABLE IF EXISTS `wm_arrival_notice`;
CREATE TABLE `wm_arrival_notice`  (
  `notice_id` bigint(20) NOT NULL COMMENT '通知单ID',
  `notice_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知单编号',
  `notice_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知单名称',
  `pur_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '采购订单编号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `vendor_nick` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `arrival_date` datetime NULL DEFAULT NULL COMMENT '到货日期',
  `contact` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系人',
  `phone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系方式',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '单据状态',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`notice_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '到货通知单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_arrival_notice
-- ----------------------------
INSERT INTO `wm_arrival_notice` VALUES (1, 'AN20251204001', '自行车所需物料', NULL, 1, 'V00005', '北京五道口', NULL, NULL, '张三', '15388888888', 'FINISHED', NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-04 15:36:20', 1, '2026-05-13 11:04:55');
INSERT INTO `wm_arrival_notice` VALUES (2, 'AN20251212003', '自行车所需物料2', '231231', 1, 'V00005', '北京五道口', '5', '2025-12-12 00:00:00', '张三', '15388888888', 'FINISHED', NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-12 17:26:48', 1, '2025-12-16 10:26:25');
INSERT INTO `wm_arrival_notice` VALUES (10001, 'AN-TEST-001', '钢板到货通知', 'PO-TEST-001', 2062098763051089921, 'V00001', '钢板产出商', NULL, '2026-06-18 00:00:00', '张经理', '13800001001', 'SUBMITTED', NULL, '钢板500吨预计6/18到货', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14');
INSERT INTO `wm_arrival_notice` VALUES (2062198287922073602, 'AN20260603005', '采购订单到货通知', 'CGDD202606030001', 2062098763051089921, 'V00001', '钢板产出商', NULL, '2026-06-03 23:42:40', NULL, NULL, 'DRAFT', NULL, '由采购订单自动生成', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-03 23:42:40', 1, '2026-06-04 00:21:47');
INSERT INTO `wm_arrival_notice` VALUES (2062208168804696066, 'AN20260604001', '采购订单到货通知', 'CGDD202606030001', 2062098763051089921, 'V00001', '钢板产出商', NULL, '2026-06-04 00:21:56', '买的多', '15xxxx', 'FINISHED', 2062368011052814337, '由采购订单自动生成', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-04 00:21:56', 1, '2026-06-07 22:13:52');
INSERT INTO `wm_arrival_notice` VALUES (2074145040774623233, 'AN20260706001', '连接件包', 'CGDD202607060001', 2062098763051089925, 'V00005', '宏达五金有限公司', NULL, '2026-07-06 00:00:00', '李四', '13800138002', 'FINISHED', 2062368011052814337, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 22:54:48', 1, '2026-07-06 23:22:42');

-- ----------------------------
-- Table structure for wm_arrival_notice_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_arrival_notice_line`;
CREATE TABLE `wm_arrival_notice_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `notice_id` bigint(20) NULL DEFAULT NULL COMMENT '通知单ID',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `quantity_arrival` double(12, 2) NOT NULL COMMENT '到货数量',
  `quantity_quanlified` double(12, 2) NULL DEFAULT NULL COMMENT '合格数量',
  `iqc_check` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否来料检验',
  `iqc_id` bigint(20) NULL DEFAULT NULL COMMENT '来料检验单ID',
  `iqc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来料检验单编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '到货通知单行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_arrival_notice_line
-- ----------------------------
INSERT INTO `wm_arrival_notice_line` VALUES (10, 1, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 100.00, NULL, '1', NULL, '', '', NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_arrival_notice_line` VALUES (11, 2, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '0', NULL, '', '', NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_arrival_notice_line` VALUES (12, 2, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, '0', NULL, '', '', NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_arrival_notice_line` VALUES (13, 2, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '0', NULL, '', '', NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_arrival_notice_line` VALUES (14, 2, 3003, 'IF20251203006', '辐条', NULL, '1', '克', 10000.00, NULL, '0', NULL, '', '', NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_arrival_notice_line` VALUES (15, 2, 3004, 'IF20251203007', '把管', NULL, '1', '克', 120.00, NULL, '0', NULL, '', '', NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_arrival_notice_line` VALUES (16, 2, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, '0', NULL, '', '', NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_arrival_notice_line` VALUES (10001, 10001, 1, 'IF20251016004', '钢板', NULL, '3', '吨', 500.00, 0.00, '1', NULL, NULL, '钢板到货需IQC检验', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14');
INSERT INTO `wm_arrival_notice_line` VALUES (2062198287964016641, 2062198287922073602, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, 1000.00, 'N', NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-03 23:42:40', 1, '2026-06-03 23:42:40');
INSERT INTO `wm_arrival_notice_line` VALUES (2062208168871804929, 2062208168804696066, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 1000.00, 1000.00, '0', NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 00:21:56', 1, '2026-06-04 00:21:56');
INSERT INTO `wm_arrival_notice_line` VALUES (2062211711074779138, 2062208168804696066, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 1000.00, 1000.00, '0', NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 00:36:01', 1, '2026-06-04 00:36:01');
INSERT INTO `wm_arrival_notice_line` VALUES (2062211768180228097, 2062208168804696066, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 1000.00, 1000.00, '0', NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 00:36:14', 1, '2026-06-04 00:36:14');
INSERT INTO `wm_arrival_notice_line` VALUES (2062375843877859329, 2062208168804696066, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 1000.00, 1000.00, '0', NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 11:28:13', 1, '2026-06-04 11:28:13');
INSERT INTO `wm_arrival_notice_line` VALUES (2062395104738361346, 2062208168804696066, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 1000.00, 1000.00, '0', NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-04 12:44:45', 1, '2026-06-04 12:44:45');
INSERT INTO `wm_arrival_notice_line` VALUES (2074145236485042177, 2074145040774623233, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '27', '套', 10.00, 10.00, '0', NULL, '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 22:55:35', 1, '2026-07-06 22:55:35');

-- ----------------------------
-- Table structure for wm_barcode
-- ----------------------------
DROP TABLE IF EXISTS `wm_barcode`;
CREATE TABLE `wm_barcode`  (
  `barcode_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '条码ID',
  `barcode_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '条码值',
  `config_id` bigint(20) NULL DEFAULT NULL COMMENT '配置ID',
  `config_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '配置编码',
  `biz_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务类型',
  `biz_id` bigint(20) NULL DEFAULT NULL COMMENT '关联业务ID',
  `biz_line_id` bigint(20) NULL DEFAULT NULL COMMENT '关联业务行ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `stock_id` bigint(20) NULL DEFAULT NULL COMMENT '库存ID',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `generate_time` datetime NULL DEFAULT NULL COMMENT '生成时间',
  `bind_time` datetime NULL DEFAULT NULL COMMENT '绑定时间',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '失效时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次编码',
  `sn_id` bigint(20) NULL DEFAULT NULL COMMENT '序列号ID',
  PRIMARY KEY (`barcode_id`) USING BTREE,
  UNIQUE INDEX `uk_barcode_code`(`barcode_code`) USING BTREE,
  INDEX `idx_barcode_config_id`(`config_id`) USING BTREE,
  INDEX `idx_barcode_biz_type`(`biz_type`) USING BTREE,
  INDEX `idx_barcode_biz_id`(`biz_id`) USING BTREE,
  INDEX `idx_barcode_material_id`(`material_id`) USING BTREE,
  INDEX `idx_barcode_stock_id`(`stock_id`) USING BTREE,
  INDEX `idx_barcode_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2074152062840729602 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '条码实例表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_barcode
-- ----------------------------
INSERT INTO `wm_barcode` VALUES (2063528144043950082, 'RC20260607000001', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063528143851012098, 'ACTIVE', '2026-06-07 15:47:03', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 15:47:03', 1, '2026-06-07 15:47:03', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063537705559769089, 'RC20260607000002', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063537705362636802, 'ACTIVE', '2026-06-07 16:25:02', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 16:25:02', 1, '2026-06-07 16:25:02', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063543124650864642, 'RC20260607000003', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063543124457926658, 'ACTIVE', '2026-06-07 16:46:34', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 16:46:34', 1, '2026-06-07 16:46:34', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063544163907125249, 'RC20260607000004', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063544163709992962, 'ACTIVE', '2026-06-07 16:50:42', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 16:50:42', 1, '2026-06-07 16:50:42', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063547647079387138, 'RC20260607000005', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063547646890643458, 'ACTIVE', '2026-06-07 17:04:33', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 17:04:33', 1, '2026-06-07 17:04:33', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063609421262618626, 'RC20260607000006', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063609421065486338, 'ACTIVE', '2026-06-07 21:10:01', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 21:10:01', 1, '2026-06-07 21:10:01', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063612738172747777, 'RC20260607000007', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063612737979809794, 'ACTIVE', '2026-06-07 21:23:12', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 21:23:12', 1, '2026-06-07 21:23:12', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063615886018920450, 'RC20260607000008', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063615885825982466, 'ACTIVE', '2026-06-07 21:35:42', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 21:35:42', 1, '2026-06-07 21:35:42', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063620726187081729, 'RC20260607000009', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063620725994143746, 'ACTIVE', '2026-06-07 21:54:56', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 21:54:56', 1, '2026-06-07 21:54:56', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063622914904821762, 'RC20260607000010', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063622914707689473, 'ACTIVE', '2026-06-07 22:03:38', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 22:03:38', 1, '2026-06-07 22:03:38', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063623345664036865, 'RC20260607000011', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063623345471098884, 'ACTIVE', '2026-06-07 22:05:21', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 22:05:21', 1, '2026-06-07 22:05:21', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2063625490874368001, 'RC20260607000012', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2062395873109688322, NULL, 1, 2062717540369408001, 2063625490677235714, 'ACTIVE', '2026-06-07 22:13:52', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-07 22:13:52', 1, '2026-06-07 22:13:52', '0', NULL, NULL);
INSERT INTO `wm_barcode` VALUES (2074152062840729601, 'RC20260706000013', 900001, 'WM_RECEIVE_DEFAULT', 'RECEIVE_IN', 2074145387983302657, NULL, 8002, 2074149637295607809, 2074152062656180226, 'ACTIVE', '2026-07-06 23:22:42', NULL, NULL, '系统生成条码', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-07-06 23:22:42', 1, '2026-07-06 23:22:42', '0', NULL, NULL);

-- ----------------------------
-- Table structure for wm_barcode_config
-- ----------------------------
DROP TABLE IF EXISTS `wm_barcode_config`;
CREATE TABLE `wm_barcode_config`  (
  `config_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '条码配置ID',
  `config_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则编码',
  `config_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
  `prefix` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '条码前缀',
  `date_pattern` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'yyyyMMdd' COMMENT '日期格式',
  `sequence_length` int(11) NOT NULL DEFAULT 6 COMMENT '流水号长度',
  `with_check_digit` bigint(20) NULL DEFAULT 0 COMMENT '是否包含校验位(0否 1是)',
  `biz_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '适用业务类型',
  `category_id` bigint(20) NULL DEFAULT NULL COMMENT '适用物料类别',
  `enable_flag` bigint(20) NULL DEFAULT 1 COMMENT '是否启用(0否 1是)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr2` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `attr3` bigint(20) NULL DEFAULT NULL,
  `attr4` bigint(20) NULL DEFAULT NULL,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0',
  `current_sequence` bigint(20) NULL DEFAULT 0 COMMENT '当前序列号',
  PRIMARY KEY (`config_id`) USING BTREE,
  UNIQUE INDEX `uk_barcode_config_code`(`config_code`) USING BTREE,
  INDEX `idx_barcode_config_biz_type`(`biz_type`) USING BTREE,
  INDEX `idx_barcode_config_category_id`(`category_id`) USING BTREE,
  INDEX `idx_barcode_config_enable_flag`(`enable_flag`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2062021060516102147 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '条码配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_barcode_config
-- ----------------------------
INSERT INTO `wm_barcode_config` VALUES (900001, 'WM_RECEIVE_DEFAULT', '收货入库默认条码规则', 'RC', 'yyyyMMdd', 6, 0, 'RECEIVE_IN', NULL, 1, '默认收货入库条码配置，入库后自动生成条码实例.', NULL, NULL, NULL, NULL, '000000', NULL, NULL, '2026-05-23 17:46:58', 1, '2026-07-06 23:22:42', '0', 13);
INSERT INTO `wm_barcode_config` VALUES (2062021060516102146, 'SO_SALESOUT_DEFAULT', '销售订单出库配置', 'SO', 'yyyyMMdd', 6, 0, 'SALES_OUT', NULL, 1, '', NULL, NULL, NULL, NULL, '000000', 103, 1, '2026-06-03 11:58:26', 1, '2026-06-03 11:59:25', '0', 0);

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
  `vendor_batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供应商原始批次号',
  PRIMARY KEY (`batch_id`) USING BTREE,
  UNIQUE INDEX `uk_batch_code`(`batch_code`) USING BTREE,
  INDEX `idx_batch_material`(`material_id`) USING BTREE,
  INDEX `idx_batch_vendor`(`vendor_id`) USING BTREE,
  INDEX `idx_batch_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_batch_status`(`status`) USING BTREE,
  INDEX `idx_batch_expiry`(`expiry_date`) USING BTREE,
  INDEX `idx_batch_source`(`source_doc_type`, `source_doc_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_wm_batch_vendor_batch_code`(`vendor_batch_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '批次管理表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_batch
-- ----------------------------
INSERT INTO `wm_batch` VALUES (2062468833967259649, '000000', 'B202606040001', 1, 'IF20251016004', '钢板', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1000.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-04 17:37:43', 1, '2026-06-04 17:37:43', '0', NULL);
INSERT INTO `wm_batch` VALUES (2062469103145107457, '000000', 'B202606040002', 1, 'IF20251016004', '钢板', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1000.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-04 17:38:48', 1, '2026-06-04 17:38:48', '0', NULL);
INSERT INTO `wm_batch` VALUES (2062473906839535618, '000000', 'B202606040003', 1, 'IF20251016004', '钢板', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1000.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-04 17:57:53', 1, '2026-06-04 17:57:53', '0', NULL);
INSERT INTO `wm_batch` VALUES (2062479099027177473, '000000', 'B202606040004', 1, 'IF20251016004', '钢板', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1000.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-04 18:18:31', 1, '2026-06-04 18:18:31', '0', NULL);
INSERT INTO `wm_batch` VALUES (2062710064911720450, '000000', 'B202606050001', 1, 'IF20251016004', '钢板', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1000.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-05 09:36:17', 1, '2026-06-05 09:36:17', '0', NULL);
INSERT INTO `wm_batch` VALUES (2062717540369408001, '000000', 'B202606050002', 1, 'IF20251016004', '钢板', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1000.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-06-05 10:06:00', 1, '2026-06-05 10:06:00', '0', NULL);
INSERT INTO `wm_batch` VALUES (2074145859616010242, '000000', 'B202607060001', 8002, 'RACK-FIT', '连接件包', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 10.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2074145387983302657, 'CGRK20260706001', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 22:58:03', 1, '2026-07-06 22:58:03', '0', NULL);
INSERT INTO `wm_batch` VALUES (2074149637295607809, '000000', 'B202607060002', 8002, 'RACK-FIT', '连接件包', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 10.0000, 0.0000, 0.0000, 'ACTIVE', 'PURCHASE_RECEIVE', 2074145387983302657, 'CGRK20260706001', NULL, NULL, NULL, NULL, NULL, NULL, 103, 1, '2026-07-06 23:13:04', 1, '2026-07-06 23:13:04', '0', NULL);

-- ----------------------------
-- Table structure for wm_material_receive
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_receive`;
CREATE TABLE `wm_material_receive`  (
  `receive_id` bigint(20) NOT NULL COMMENT '入库单ID',
  `receive_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '入库单编号',
  `receive_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '入库单名称',
  `iqc_id` bigint(20) NULL DEFAULT NULL COMMENT '来料检验单ID',
  `iqc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来料检验单编号',
  `notice_id` bigint(20) NULL DEFAULT NULL COMMENT '到货通知单ID',
  `notice_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '到货通知单编号',
  `pur_id` bigint(20) NULL DEFAULT NULL COMMENT '采购订单ID',
  `pur_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '采购订单编号',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `vendor_nick` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `receive_date` datetime NULL DEFAULT NULL COMMENT '入库日期',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'DRAFT' COMMENT '单据状态',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板（wf_custom_form.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `shelf_by` bigint(20) NULL DEFAULT NULL COMMENT '上架人ID',
  `shelf_time` datetime NULL DEFAULT NULL COMMENT '上架时间',
  PRIMARY KEY (`receive_id`) USING BTREE,
  INDEX `idx_receive_status`(`status`) USING BTREE,
  INDEX `idx_receive_vendor`(`vendor_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料入库单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_material_receive
-- ----------------------------
INSERT INTO `wm_material_receive` VALUES (1, 'CGRK20251208002', '测试订单1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'SHELVED', 1, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-08 14:54:24', 1, '2026-03-05 11:04:51', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2, 'CGRK20251208003', '自行车材料入库1', NULL, NULL, 1, 'AN20251204001', NULL, NULL, 1, 'V00005', '北京五道口', '5', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-12-08 00:00:00', 'FINISHED', NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-08 17:29:54', 1, '2025-12-11 16:17:01', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (3, 'CGRK20251212002', '自行车材料2入库', NULL, NULL, 2, 'AN20251212003', NULL, '123', 1, 'V00005', '北京五道口', '五2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-12-12 00:00:00', 'FINISHED', NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-12 17:32:11', 1, '2025-12-16 10:26:25', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (10001, 'RCV-TEST-001', '润滑油收货单', NULL, NULL, NULL, NULL, 10002, 'PO-TEST-002', 2062098763051089924, 'V00004', '天津化工材料有限公司', NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', '2026-06-10 00:00:00', 'EXECUTED', NULL, '润滑油200升已收货入库', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051671493137604610, 'CGRK20260505001', '测试入库2', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-05 00:00:00', 'DRAFT', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 22:32:57', 1, '2026-05-05 23:12:38', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051681538382364673, 'CGRK20260505002', 'cs3', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-05 00:00:00', 'DRAFT', NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 23:12:52', 1, '2026-05-06 16:16:37', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051684557505404930, 'CGRK20260505003', 'cs3', NULL, NULL, 1, 'AN20251204001', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-05 00:00:00', 'DRAFT', NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 23:24:52', 1, '2026-05-06 16:16:38', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051857741142466561, 'CGRK20260506001', 'cs3', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-06 00:00:00', 'DRAFT', NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 10:53:02', 1, '2026-05-06 16:16:40', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051865868801810434, 'CGRK20260506002', 'cs4', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 11:25:20', 1, '2026-05-06 16:16:42', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051868354212253698, 'CGRK20260506003', 'cs5', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-06 00:00:00', 'DRAFT', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 11:35:12', 1, '2026-05-06 16:16:44', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051875108484485122, 'CGRK20260506004', 'cs6', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-06 00:00:00', 'DRAFT', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 12:02:03', 1, '2026-05-06 16:16:46', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051889381948514306, 'CGRK20260506005', 'cs6', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-06 00:00:00', 'DRAFT', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 12:58:46', 1, '2026-05-13 09:46:57', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051894586140160001, 'CGRK20260506006', 'cs7', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-06 00:00:00', 'DRAFT', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 13:19:26', 1, '2026-05-13 09:46:59', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051900626126671874, 'CGRK20260506007', 'cs8', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-06 00:00:00', 'SHELVED', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-06 13:43:26', 1, '2026-05-06 16:15:48', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2051939282136768513, 'CGRK20260506008', '测试', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-06 00:00:00', 'SHELVED', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-06 16:17:03', 1, '2026-05-06 17:31:58', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2052251072364216321, 'CGRK20260507001', '入库单0507', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-07 00:00:00', 'SHELVED', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-07 12:55:59', 1, '2026-05-07 12:56:04', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2054133595956535298, 'CGRK20260512001', '入库0512', NULL, NULL, 2, 'AN20251212003', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 00:00:00', 'SHELVED', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-12 17:36:28', 1, '2026-05-12 17:54:01', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2054376868876881922, 'CGRK20260513001', '入库0513', NULL, NULL, 1, 'AN20251204001', NULL, NULL, 1, 'V00005', '北京五道口', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-13 00:00:00', 'FINISHED', 2015458019458437122, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-13 09:43:09', 1, '2026-05-13 11:04:55', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2062198288031125507, 'CGRK20260603001', '采购到货收货单', NULL, NULL, 2062198287922073602, 'AN20260603005', 2062092892921307138, 'CGDD202606030001', 2062098763051089921, 'V00001', '钢板产出商', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-06-03 23:42:40', 'SHELVED', NULL, '采购订单自动联动生成', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-03 23:42:40', 1, '2026-06-03 23:42:40', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2062208168934719491, 'CGRK20260604001', '采购到货收货单', NULL, NULL, 2062208168804696066, 'AN20260604001', 2062092892921307138, 'CGDD202606030001', 2062098763051089921, 'V00001', '钢板产出商', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-06-04 00:21:56', 'SHELVED', NULL, '采购订单自动联动生成', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 00:21:56', 1, '2026-06-04 11:27:35', NULL, NULL);
INSERT INTO `wm_material_receive` VALUES (2062395873109688322, 'CGRK20260604002', '采购到货收货单', NULL, NULL, 2062208168804696066, 'AN20260604001', 2062092892921307138, 'CGDD202606030001', 2062098763051089921, 'V00001', '钢板产出商', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-06-04 12:47:48', 'FINISHED', 2015458019458437122, '到货通知审批通过后自动生成', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-04 12:47:48', 1, '2026-06-07 22:13:52', 1, '2026-06-07 15:11:04');
INSERT INTO `wm_material_receive` VALUES (2074145387983302657, 'CGRK20260706001', '采购到货收货单', NULL, NULL, 2074145040774623233, 'AN20260706001', 2074143658025840642, 'CGDD202607060001', 2062098763051089925, 'V00005', '宏达五金有限公司', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-06 22:56:11', 'FINISHED', 2015458019458437122, '到货通知审批通过后自动生成', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 22:56:11', 1, '2026-07-06 23:22:42', 1, '2026-07-06 23:14:12');

-- ----------------------------
-- Table structure for wm_material_receive_detail
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_receive_detail`;
CREATE TABLE `wm_material_receive_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '明细ID',
  `line_id` bigint(20) NULL DEFAULT NULL COMMENT '行ID',
  `receive_id` bigint(20) NULL DEFAULT NULL COMMENT '入库单ID',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `quantity` double(12, 2) NOT NULL COMMENT '入库数量',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `sn_id` bigint(20) NULL DEFAULT NULL COMMENT '序列号ID（序列号管理物料上架时关联）',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '序列号编码（序列号管理物料上架时关联）',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_wm_receive_detail_sn_id`(`sn_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料入库单明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_material_receive_detail
-- ----------------------------
INSERT INTO `wm_material_receive_detail` VALUES (3, 1, 2, 1, 'IF20251016004', '钢板', NULL, '2', '千克', 20.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-11 11:40:04', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (4, 1, 2, 1, 'IF20251016004', '钢板', NULL, '2', '千克', 80.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 9, 'L013', '辐条库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-11 11:40:29', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (5, 2, 3, 10, 'LUBRICANT', '润滑油', NULL, '29', '打', 110.00, NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:14:50', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (6, 9, 3, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 7, 'L011', '轮圈库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:15:08', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (7, 10, 3, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 8, 'L012', '轮胎库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:15:21', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (8, 11, 3, 3003, 'IF20251203006', '辐条', NULL, '1', '克', 10000.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 9, 'L013', '辐条库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:16:12', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (9, 12, 3, 3004, 'IF20251203007', '把管', NULL, '1', '克', 120.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 10, 'L014', '把管库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:22:25', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (10, 13, 3, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 11, 'L015', '把套库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:24:38', 1, NULL, NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2054135612548542465, 2054133799183147009, 2054133595956535298, 10, 'LUBRICANT', '润滑油', NULL, '29', '打', 110.00, NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:44:29', 1, '2026-05-12 17:44:29', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2054135756870348801, 2054133799199924226, 2054133595956535298, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 7, 'L011', '轮圈库位', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:45:03', 1, '2026-05-12 17:45:03', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2054135809982820354, 2054133799199924226, 2054133595956535298, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:45:16', 1, '2026-05-12 17:45:55', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2054137456834306050, 2054137143029063681, 2054133595956535298, 10, 'LUBRICANT', '润滑油', NULL, '29', '打', 110.00, NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:51:48', 1, '2026-05-12 17:51:48', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2054379115350233089, 2054377017514627073, 2054376868876881922, 1, 'IF20251016004', '钢板', NULL, '2', '千克', 100.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-13 09:52:04', 1, '2026-05-13 09:52:04', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2054383784692776961, 2054383784638251010, 2054376868876881922, 1, 'IF20251016004', '钢板', NULL, '2', '千克', 100.00, NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-13 10:10:38', 1, '2026-05-13 11:04:55', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2062468989966008321, 2062399115839356929, 2062395873109688322, 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 1000.00, 2062468833967259649, 'B202606040001', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', '一吨', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 17:38:21', 1, '2026-06-04 17:38:21', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2062479162872872962, 2062469049319604225, 2062395873109688322, 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 1000.00, 2062479099027177473, 'B202606040004', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', '一吨', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 18:18:46', 1, '2026-06-04 18:18:46', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2062717661832257537, 2062717661786120194, 2062395873109688322, 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 1000.00, 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', '最新的一吨钢板', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-05 10:06:29', 1, '2026-06-07 22:13:52', NULL, NULL);
INSERT INTO `wm_material_receive_detail` VALUES (2074149902488866818, 2074149902430146561, 2074145387983302657, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 10.00, 2074149637295607809, 'B202607060002', 3, 'WH005', '半成品仓库', 6, 'A0007', '加工区', 2074149487256965121, 'L002', '连接件包', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 23:14:07', 1, '2026-07-06 23:22:42', NULL, NULL);

-- ----------------------------
-- Table structure for wm_material_receive_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_receive_line`;
CREATE TABLE `wm_material_receive_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '物料入库单行ID',
  `receive_id` bigint(20) NULL DEFAULT NULL COMMENT '物料入库单ID',
  `source_doc_line_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据行ID',
  `purchase_line_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采购订单行ID(pu_purchase_order_line.line_id)',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `quantity_received` double(12, 2) NOT NULL COMMENT '入库数量',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_wm_receive_line_po_line`(`purchase_line_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料入库单行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_material_receive_line
-- ----------------------------
INSERT INTO `wm_material_receive_line` VALUES (1, 2, 10, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 100.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', NULL, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_material_receive_line` VALUES (2, 3, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', NULL, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_material_receive_line` VALUES (9, 3, 12, NULL, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', NULL, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_material_receive_line` VALUES (10, 3, 13, NULL, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', NULL, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_material_receive_line` VALUES (11, 3, 14, NULL, 3003, 'IF20251203006', '辐条', NULL, '1', '克', 10000.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', NULL, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_material_receive_line` VALUES (12, 3, 15, NULL, 3004, 'IF20251203007', '把管', NULL, '1', '克', 120.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', NULL, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_material_receive_line` VALUES (13, 3, 16, NULL, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', NULL, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, NULL, 1, NULL);
INSERT INTO `wm_material_receive_line` VALUES (10001, 10001, NULL, NULL, 10, 'LUBRICANT', '润滑油', NULL, '23', '升', 200.00, NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', '润滑油200升收货', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14');
INSERT INTO `wm_material_receive_line` VALUES (2051671980381511682, 2051671493137604610, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 22:34:53', 1, '2026-05-05 22:34:53');
INSERT INTO `wm_material_receive_line` VALUES (2051681592790876161, 2051681538382364673, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 23:13:05', 1, '2026-05-05 23:13:05');
INSERT INTO `wm_material_receive_line` VALUES (2051681603956113410, 2051681538382364673, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 23:13:07', 1, '2026-05-05 23:13:07');
INSERT INTO `wm_material_receive_line` VALUES (2051681933032816641, 2051681538382364673, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 23:14:26', 1, '2026-05-05 23:14:26');
INSERT INTO `wm_material_receive_line` VALUES (2051684630150750210, 2051684557505404930, 10, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 100.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-05 23:25:09', 1, '2026-05-05 23:25:09');
INSERT INTO `wm_material_receive_line` VALUES (2051857781416173569, 2051857741142466561, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 10:53:11', 1, '2026-05-06 10:53:11');
INSERT INTO `wm_material_receive_line` VALUES (2051857842611068929, 2051857741142466561, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 10:53:26', 1, '2026-05-06 10:53:26');
INSERT INTO `wm_material_receive_line` VALUES (2051865939190620162, 2051865868801810434, 16, NULL, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 11:25:36', 1, '2026-05-06 11:25:36');
INSERT INTO `wm_material_receive_line` VALUES (2051868408067117058, 2051868354212253698, 12, NULL, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 11:35:25', 1, '2026-05-06 11:35:25');
INSERT INTO `wm_material_receive_line` VALUES (2051875145197228033, 2051875108484485122, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 12:02:11', 1, '2026-05-06 12:02:11');
INSERT INTO `wm_material_receive_line` VALUES (2051889521891467266, 2051889381948514306, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 12:59:19', 1, '2026-05-06 12:59:19');
INSERT INTO `wm_material_receive_line` VALUES (2051889835600240641, 2051889381948514306, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 13:00:34', 1, '2026-05-06 13:00:34');
INSERT INTO `wm_material_receive_line` VALUES (2051889953263050754, 2051889381948514306, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 13:01:02', 1, '2026-05-06 13:01:02');
INSERT INTO `wm_material_receive_line` VALUES (2051894768332337154, 2051894586140160001, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-06 13:20:10', 1, '2026-05-06 13:20:10');
INSERT INTO `wm_material_receive_line` VALUES (2051900679037816833, 2051900626126671874, 13, NULL, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-06 13:43:39', 1, '2026-05-06 13:43:39');
INSERT INTO `wm_material_receive_line` VALUES (2051939325904330753, 2051939282136768513, 13, NULL, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-06 16:17:13', 1, '2026-05-06 16:17:13');
INSERT INTO `wm_material_receive_line` VALUES (2054133799183147009, 2054133595956535298, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:37:16', 1, '2026-05-12 17:37:16');
INSERT INTO `wm_material_receive_line` VALUES (2054133799199924226, 2054133595956535298, 12, NULL, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:37:16', 1, '2026-05-12 17:37:16');
INSERT INTO `wm_material_receive_line` VALUES (2054133799204118530, 2054133595956535298, 13, NULL, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:37:16', 1, '2026-05-12 17:37:16');
INSERT INTO `wm_material_receive_line` VALUES (2054133799212507137, 2054133595956535298, 14, NULL, 3003, 'IF20251203006', '辐条', NULL, '1', '克', 10000.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:37:16', 1, '2026-05-12 17:37:16');
INSERT INTO `wm_material_receive_line` VALUES (2054133799212507138, 2054133595956535298, 15, NULL, 3004, 'IF20251203007', '把管', NULL, '1', '克', 120.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:37:16', 1, '2026-05-12 17:37:16');
INSERT INTO `wm_material_receive_line` VALUES (2054133799212507139, 2054133595956535298, 16, NULL, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:37:16', 1, '2026-05-12 17:37:16');
INSERT INTO `wm_material_receive_line` VALUES (2054137143029063681, 2054133595956535298, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:50:34', 1, '2026-05-12 17:50:34');
INSERT INTO `wm_material_receive_line` VALUES (2054137143058423810, 2054133595956535298, 12, NULL, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:50:34', 1, '2026-05-12 17:50:34');
INSERT INTO `wm_material_receive_line` VALUES (2054137143087783938, 2054133595956535298, 13, NULL, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:50:34', 1, '2026-05-12 17:50:34');
INSERT INTO `wm_material_receive_line` VALUES (2054137143096172545, 2054133595956535298, 14, NULL, 3003, 'IF20251203006', '辐条', NULL, '1', '克', 10000.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:50:34', 1, '2026-05-12 17:50:34');
INSERT INTO `wm_material_receive_line` VALUES (2054137143100366849, 2054133595956535298, 15, NULL, 3004, 'IF20251203007', '把管', NULL, '1', '克', 120.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:50:34', 1, '2026-05-12 17:50:34');
INSERT INTO `wm_material_receive_line` VALUES (2054137143108755457, 2054133595956535298, 16, NULL, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:50:34', 1, '2026-05-12 17:50:34');
INSERT INTO `wm_material_receive_line` VALUES (2054137480037195777, 2054133595956535298, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:51:54', 1, '2026-05-12 17:51:54');
INSERT INTO `wm_material_receive_line` VALUES (2054137480045584386, 2054133595956535298, 12, NULL, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:51:54', 1, '2026-05-12 17:51:54');
INSERT INTO `wm_material_receive_line` VALUES (2054137480058167298, 2054133595956535298, 13, NULL, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:51:54', 1, '2026-05-12 17:51:54');
INSERT INTO `wm_material_receive_line` VALUES (2054137480062361601, 2054133595956535298, 14, NULL, 3003, 'IF20251203006', '辐条', NULL, '1', '克', 10000.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:51:54', 1, '2026-05-12 17:51:54');
INSERT INTO `wm_material_receive_line` VALUES (2054137480062361602, 2054133595956535298, 15, NULL, 3004, 'IF20251203007', '把管', NULL, '1', '克', 120.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:51:54', 1, '2026-05-12 17:51:54');
INSERT INTO `wm_material_receive_line` VALUES (2054137480066555905, 2054133595956535298, 16, NULL, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-12 17:51:54', 1, '2026-05-12 17:51:54');
INSERT INTO `wm_material_receive_line` VALUES (2054138014135672834, 2054133595956535298, 11, NULL, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', '打', 110.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-12 17:54:01', 1, '2026-05-12 17:54:01');
INSERT INTO `wm_material_receive_line` VALUES (2054138014144061442, 2054133595956535298, 12, NULL, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-12 17:54:01', 1, '2026-05-12 17:54:01');
INSERT INTO `wm_material_receive_line` VALUES (2054138014152450049, 2054133595956535298, 13, NULL, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-12 17:54:01', 1, '2026-05-12 17:54:01');
INSERT INTO `wm_material_receive_line` VALUES (2054138014152450050, 2054133595956535298, 14, NULL, 3003, 'IF20251203006', '辐条', NULL, '1', '克', 10000.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-12 17:54:01', 1, '2026-05-12 17:54:01');
INSERT INTO `wm_material_receive_line` VALUES (2054138014152450051, 2054133595956535298, 15, NULL, 3004, 'IF20251203007', '把管', NULL, '1', '克', 120.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-12 17:54:01', 1, '2026-05-12 17:54:01');
INSERT INTO `wm_material_receive_line` VALUES (2054138014160838658, 2054133595956535298, 16, NULL, 3005, 'IF20251203008', '把套', NULL, '1', '克', 250.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-12 17:54:01', 1, '2026-05-12 17:54:01');
INSERT INTO `wm_material_receive_line` VALUES (2054376937827045378, 2054376868876881922, 10, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 100.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-13 09:43:25', 1, '2026-05-13 09:43:25');
INSERT INTO `wm_material_receive_line` VALUES (2054376953127866370, 2054376868876881922, 10, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 100.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-13 09:43:29', 1, '2026-05-13 09:43:29');
INSERT INTO `wm_material_receive_line` VALUES (2054377017514627073, 2054376868876881922, 10, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 100.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-05-13 09:43:44', 1, '2026-05-13 09:43:44');
INSERT INTO `wm_material_receive_line` VALUES (2054383784638251010, 2054376868876881922, 10, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', '千克', 100.00, NULL, '', NULL, '', '', NULL, '', '', NULL, '', '', '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-05-13 10:10:38', 1, '2026-05-13 11:04:55');
INSERT INTO `wm_material_receive_line` VALUES (2062198288031125508, 2062198288031125507, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-03 23:42:40', 1, '2026-06-03 23:42:40');
INSERT INTO `wm_material_receive_line` VALUES (2062208169006022657, 2062208168934719491, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 00:21:56', 1, '2026-06-04 00:21:56');
INSERT INTO `wm_material_receive_line` VALUES (2062395873189380098, 2062395873109688322, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 12:47:48', 1, '2026-06-04 12:47:48');
INSERT INTO `wm_material_receive_line` VALUES (2062396758678257666, 2062395873109688322, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 12:51:19', 1, '2026-06-04 12:51:19');
INSERT INTO `wm_material_receive_line` VALUES (2062399115839356929, 2062395873109688322, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 13:00:41', 1, '2026-06-04 13:00:41');
INSERT INTO `wm_material_receive_line` VALUES (2062469049319604225, 2062395873109688322, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 17:38:35', 1, '2026-06-04 17:38:35');
INSERT INTO `wm_material_receive_line` VALUES (2062479179658477570, 2062395873109688322, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-04 18:18:50', 1, '2026-06-04 18:18:50');
INSERT INTO `wm_material_receive_line` VALUES (2062710168410365954, 2062395873109688322, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-05 09:36:42', 1, '2026-06-05 09:36:42');
INSERT INTO `wm_material_receive_line` VALUES (2062717661786120194, 2062395873109688322, NULL, NULL, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', NULL, '千克', 1000.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '测试', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-05 10:06:29', 1, '2026-06-07 22:13:52');
INSERT INTO `wm_material_receive_line` VALUES (2074145387983302658, 2074145387983302657, NULL, NULL, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 10.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 22:56:11', 1, '2026-07-06 22:56:11');
INSERT INTO `wm_material_receive_line` VALUES (2074145728166522882, 2074145387983302657, NULL, NULL, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 10.00, NULL, 'CGRK123', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 22:57:32', 1, '2026-07-06 22:57:32');
INSERT INTO `wm_material_receive_line` VALUES (2074149902430146561, 2074145387983302657, NULL, NULL, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 10.00, NULL, 'CGRK123', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 23:14:07', 1, '2026-07-06 23:22:42');

-- ----------------------------
-- Table structure for wm_material_stock
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_stock`;
CREATE TABLE `wm_material_stock`  (
  `stock_id` bigint(20) NOT NULL COMMENT '库存ID',
  `category_id` bigint(20) NULL DEFAULT NULL COMMENT '物料类型ID',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位id',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '入库批次号',
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '库位名称',
  `vendor_id` bigint(20) NULL DEFAULT NULL COMMENT '供应商ID',
  `vendor_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商编号',
  `vendor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商名称',
  `vendor_nick` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `stock_quantity` double(12, 2) NULL DEFAULT NULL COMMENT '在库数量',
  `reserved_quantity` double(12, 2) NOT NULL DEFAULT 0.00 COMMENT '已预留数量(销售订单/工单锁定)',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '生产工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生产工单编号',
  `production_date` datetime NULL DEFAULT NULL COMMENT '生产日期',
  `recpt_date` datetime NULL DEFAULT NULL COMMENT '入库时间',
  `expire_date` datetime NULL DEFAULT NULL COMMENT '库存有效期',
  `frozen_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '冻结标志',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `unit_cost` decimal(14, 6) NULL DEFAULT 0.000000 COMMENT '单位成本(移动加权平均)',
  `total_cost` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '库存总金额',
  `sn_id` bigint(20) NULL DEFAULT NULL COMMENT '序列号ID（序列号管理物料的唯一标识）',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '序列号编码（序列号管理物料的唯一编码）',
  PRIMARY KEY (`stock_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_wm_material_stock_material`(`material_id`, `frozen_flag`, `recpt_date`) USING BTREE,
  INDEX `idx_wm_material_stock_vendor`(`material_id`, `vendor_id`, `frozen_flag`) USING BTREE,
  INDEX `idx_wm_material_stock_sn_id`(`sn_id`) USING BTREE,
  INDEX `idx_wm_material_stock_sn_code`(`sn_code`) USING BTREE,
  INDEX `idx_wm_material_stock_location`(`location_id`, `del_flag`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存物料记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_material_stock
-- ----------------------------
INSERT INTO `wm_material_stock` VALUES (55, 5, 1, 'IF20251016004', '钢板', NULL, '2', '千克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, NULL, NULL, 20.00, 0.00, NULL, NULL, NULL, '2025-12-11 16:17:01', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-11 16:17:01', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (56, 5, 1, 'IF20251016004', '钢板', NULL, '2', '千克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 9, 'L013', '辐条库位', NULL, NULL, NULL, NULL, 80.00, 0.00, NULL, NULL, NULL, '2025-12-11 16:17:01', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-11 16:17:01', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (57, 11, 10, 'LUBRICANT', '润滑油', NULL, '29', '打', NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', 1, 'V00005', '北京五道口', '五2', 110.00, 0.00, NULL, NULL, NULL, '2025-12-16 10:26:25', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-16 10:26:25', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (58, 1, 3001, 'IF20251203004', '轮圈', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 7, 'L011', '轮圈库位', 1, 'V00005', '北京五道口', '五2', 250.00, 0.00, NULL, NULL, NULL, '2025-12-16 10:26:25', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-16 10:26:25', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (59, 1, 3002, 'IF20251203005', '轮胎', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 8, 'L012', '轮胎库位', 1, 'V00005', '北京五道口', '五2', 250.00, 0.00, NULL, NULL, NULL, '2025-12-16 10:26:25', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-16 10:26:25', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (60, 1, 3003, 'IF20251203006', '辐条', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 9, 'L013', '辐条库位', 1, 'V00005', '北京五道口', '五2', 10000.00, 0.00, NULL, NULL, NULL, '2025-12-16 10:26:25', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-16 10:26:25', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (61, 1, 3004, 'IF20251203007', '把管', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 10, 'L014', '把管库位', 1, 'V00005', '北京五道口', '五2', 120.00, 0.00, NULL, NULL, NULL, '2025-12-16 10:26:25', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-16 10:26:25', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (62, 1, 3005, 'IF20251203008', '把套', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 11, 'L015', '把套库位', 1, 'V00005', '北京五道口', '五2', 250.00, 0.00, NULL, NULL, NULL, '2025-12-16 10:26:25', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', NULL, 1, 1, '2025-12-16 10:26:25', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1001, 5, 1, 'IF20251016004', '钢板', NULL, '3', '吨', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, NULL, NULL, 500.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '钢板初始库存', 1, 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', 4500.000000, 2250000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1002, 1, 3001, 'IF20251203004', '轮圈', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 7, 'L011', '轮圈库位', NULL, NULL, NULL, NULL, 0.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '轮圈初始库存', 2, 1, '2026-06-21 20:52:46', 1, '2026-06-24 11:30:43', 35.000000, 7000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1003, 1, 3002, 'IF20251203005', '轮胎', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 8, 'L012', '轮胎库位', NULL, NULL, NULL, NULL, 0.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '轮胎初始库存', 2, 1, '2026-06-21 20:52:46', 1, '2026-06-24 11:30:44', 15.000000, 3000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1004, 1, 3003, 'IF20251203006', '辐条', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 9, 'L013', '辐条库位', NULL, NULL, NULL, NULL, 10000.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '辐条初始库存', 1, 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', 2.000000, 20000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1005, 11, 10, 'LUBRICANT', '润滑油', NULL, '23', '升', NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', NULL, NULL, NULL, NULL, 400.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '润滑油初始库存', 2, 1, '2026-06-21 20:52:46', 1, '2026-06-24 11:30:43', 85.000000, 42500.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1006, 3, 1001, 'IF20251110001', '自行车', NULL, '26', '件', NULL, NULL, 1, 'WH003', '成品仓库', 1, 'A0002', '成品区', 1, 'L001', '自行车库位', NULL, NULL, NULL, NULL, 0.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '自行车成品初始库存', 4, 1, '2026-06-21 20:52:46', 1, '2026-06-25 17:01:10', 800.000000, 40000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1007, 10, 7, 'BOLT_M12', '螺栓M12', NULL, '26', '件', NULL, NULL, 3, 'WH005', '半成品仓库', 6, 'A0007', '加工区', 13, 'L017', '电机库位', NULL, NULL, NULL, NULL, 5000.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '螺栓初始库存(存放于半成品仓库空闲库位)', 1, 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', 1.500000, 7500.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1008, 1, 3004, 'IF20251203007', '把管', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 10, 'L014', '把管库位', NULL, NULL, NULL, NULL, 98.50, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '把管初始库存', 2, 1, '2026-06-21 20:52:46', 1, '2026-06-24 11:30:44', 20.000000, 4000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (1009, 1, 3005, 'IF20251203008', '把套', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 11, 'L015', '把套库位', NULL, NULL, NULL, NULL, 298.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', 100, '0', '把套初始库存', 2, 1, '2026-06-21 20:52:46', 1, '2026-06-24 11:30:44', 8.000000, 4000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003001, NULL, 9900000001, 'DEMO-M001', '演示物料A', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000101, 'A-L1C1', 'A区1层1列', NULL, NULL, NULL, NULL, 100.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 10.000000, 1000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003002, NULL, 9900000001, 'DEMO-M001', '演示物料A', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000102, 'A-L1C2', 'A区1层2列', NULL, NULL, NULL, NULL, 100.00, 85.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 10.000000, 1000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003003, NULL, 9900000002, 'DEMO-M002', '演示物料B', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000103, 'A-L1C3', 'A区1层3列', NULL, NULL, NULL, NULL, 50.00, 0.00, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 20.000000, 1000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003004, NULL, 9900000001, 'DEMO-M001', '演示物料A', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000105, 'A-L2C1', 'A区2层1列', NULL, NULL, NULL, NULL, 200.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 10.000000, 2000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003005, NULL, 9900000002, 'DEMO-M002', '演示物料B', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000106, 'A-L2C2', 'A区2层2列', NULL, NULL, NULL, NULL, 80.00, 70.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 20.000000, 1600.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003006, NULL, 9900000001, 'DEMO-M001', '演示物料A', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000011, 'A3D01', '原料货架区', 9900000108, 'A-L2C4', 'A区2层4列', NULL, NULL, NULL, NULL, 30.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 10.000000, 300.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003007, NULL, 9900000003, 'DEMO-P001', '演示成品P', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000201, 'B-R1C1', 'B区1行1列', NULL, NULL, NULL, NULL, 500.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 50.000000, 25000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003008, NULL, 9900000003, 'DEMO-P001', '演示成品P', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000203, 'B-R1C3', 'B区1行3列', NULL, NULL, NULL, NULL, 60.00, 55.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 50.000000, 3000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003009, NULL, 9900000003, 'DEMO-P001', '演示成品P', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000204, 'B-R2C1', 'B区2行1列', NULL, NULL, NULL, NULL, 10.00, 0.00, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 50.000000, 500.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (9900003010, NULL, 9900000003, 'DEMO-P001', '演示成品P', NULL, NULL, '件', NULL, NULL, 9900000001, 'WH-3D-DEMO', '3D演示仓库', 9900000012, 'B3D02', '成品网格区', 9900000206, 'B-R2C3', 'B区2行3列', NULL, NULL, NULL, NULL, 40.00, 0.00, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0, '000000', NULL, '0', NULL, 1, 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 50.000000, 2000.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (2054397446132207618, 5, 1, 'IF20251016004', '钢板', NULL, '2', '千克', NULL, NULL, 2, 'WH004', '原料仓库', 0, 'A0005', '金属库区', 0, 'VIRTUAL', '虚拟库位', 1, 'V00005', '北京五道口', NULL, 0.00, 0.00, NULL, NULL, NULL, '2026-05-13 11:04:55', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', '', 1, 1, '2026-05-13 11:04:55', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (2054397446228676610, 5, 1, 'IF20251016004', '钢板', NULL, '2', '千克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', 1, 'V00005', '北京五道口', NULL, 100.00, 0.00, NULL, NULL, NULL, '2026-05-13 11:04:55', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '1', '', 1, 1, '2026-05-13 11:04:55', 1, '2026-06-03 15:56:24', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (2063625490610126850, 5, 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 0, 'A0005', '金属库区', 0, 'VIRTUAL', '虚拟库位', 2062098763051089921, 'V00001', '钢板产出商', NULL, 0.00, 0.00, NULL, NULL, NULL, '2026-06-07 22:13:52', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '0', '最新的一吨钢板', 1, 1, '2026-06-07 22:13:52', 1, '2026-06-07 22:13:52', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (2063625490677235714, 5, 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', 2062098763051089921, 'V00001', '钢板产出商', NULL, 799.21, 0.00, NULL, NULL, NULL, '2026-06-07 22:13:52', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '0', '最新的一吨钢板', 6, 1, '2026-06-07 22:13:52', 1, '2026-07-06 23:24:21', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (2074152062618431489, 10, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 2074149637295607809, 'B202607060002', 3, 'WH005', '半成品仓库', 0, 'A0007', '加工区', 0, 'VIRTUAL', '虚拟库位', 2062098763051089925, 'V00005', '宏达五金有限公司', NULL, 0.00, 0.00, NULL, NULL, NULL, '2026-07-06 23:22:42', NULL, 1, NULL, NULL, 0, 0, '000000', 103, '0', '', 1, 1, '2026-07-06 23:22:42', 1, '2026-07-06 23:22:42', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (2074152062656180226, 10, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 2074149637295607809, 'B202607060002', 3, 'WH005', '半成品仓库', 6, 'A0007', '加工区', 2074149487256965121, 'L002', '连接件包', 2062098763051089925, 'V00005', '宏达五金有限公司', NULL, 0.00, 0.00, NULL, NULL, NULL, '2026-07-06 23:22:42', NULL, 0, NULL, NULL, 0, 0, '000000', 103, '0', '', 2, 1, '2026-07-06 23:22:42', 1, '2026-07-06 23:24:38', 0.000000, 0.0000, NULL, NULL);
INSERT INTO `wm_material_stock` VALUES (2074160243641847809, NULL, 8003, 'RACK-001', '简易金属工具架', NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10.00, 0.00, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, 0, 0, '000000', 103, '0', NULL, 1, 1, '2026-07-06 23:55:13', 1, '2026-07-06 23:55:12', 0.000000, 0.0000, NULL, NULL);

-- ----------------------------
-- Table structure for wm_material_stock_tx
-- ----------------------------
DROP TABLE IF EXISTS `wm_material_stock_tx`;
CREATE TABLE `wm_material_stock_tx`  (
  `tx_id` bigint(20) NOT NULL COMMENT '事务ID',
  `tx_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事务类型',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `base_uom_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `package_id` bigint(20) NULL DEFAULT NULL COMMENT '容器ID',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '容器编号',
  `source_doc_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单据类型',
  `source_doc_id` bigint(20) NULL DEFAULT NULL COMMENT '单据ID',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单据编号',
  `source_doc_line_id` bigint(20) NULL DEFAULT NULL COMMENT '单据行ID',
  `material_stock_id` bigint(20) NOT NULL COMMENT '库存记录ID',
  `tx_flag` int(11) NULL DEFAULT 1 COMMENT '库存方向',
  `tx_quantity` decimal(12, 2) NULL DEFAULT NULL COMMENT '事务数量',
  `before_quantity` decimal(12, 2) NULL DEFAULT NULL COMMENT '变动前数量',
  `after_quantity` decimal(12, 2) NULL DEFAULT NULL COMMENT '变动后数量',
  `tx_date` datetime NULL DEFAULT NULL COMMENT '事务日期',
  `related_tx_id` bigint(20) NULL DEFAULT NULL COMMENT '关联的事务ID',
  `erp_date` datetime NULL DEFAULT NULL COMMENT 'ERP账期',
  `recpt_date` datetime NULL DEFAULT NULL COMMENT '接收日期',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `unit_cost` decimal(14, 6) NULL DEFAULT 0.000000 COMMENT '单位成本',
  `tx_amount` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '事务金额',
  PRIMARY KEY (`tx_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_wm_stock_tx_source`(`source_doc_type`, `source_doc_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存事务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_material_stock_tx
-- ----------------------------
INSERT INTO `wm_material_stock_tx` VALUES (1, 'TARGET_IN', 10, 'LUBRICANT', '润滑油', NULL, '29', '打', NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', NULL, NULL, NULL, NULL, NULL, NULL, 57, 1, 110.00, 0.00, 110.00, '2025-12-16 10:26:25', NULL, NULL, '2025-12-16 10:26:25', NULL, NULL, 0, 0, '000000', 103, NULL, '0', 1, '2025-12-16 10:26:25', 1, NULL, 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2, 'TARGET_IN', 3001, 'IF20251203004', '轮圈', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 7, 'L011', '轮圈库位', NULL, NULL, NULL, NULL, NULL, NULL, 58, 1, 250.00, 0.00, 250.00, '2025-12-16 10:26:25', NULL, NULL, '2025-12-16 10:26:25', NULL, NULL, 0, 0, '000000', 103, NULL, '0', 1, '2025-12-16 10:26:25', 1, NULL, 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (3, 'TARGET_IN', 3002, 'IF20251203005', '轮胎', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 8, 'L012', '轮胎库位', NULL, NULL, NULL, NULL, NULL, NULL, 59, 1, 250.00, 0.00, 250.00, '2025-12-16 10:26:25', NULL, NULL, '2025-12-16 10:26:25', NULL, NULL, 0, 0, '000000', 103, NULL, '0', 1, '2025-12-16 10:26:25', 1, NULL, 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (4, 'TARGET_IN', 3003, 'IF20251203006', '辐条', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 9, 'L013', '辐条库位', NULL, NULL, NULL, NULL, NULL, NULL, 60, 1, 10000.00, 0.00, 10000.00, '2025-12-16 10:26:25', NULL, NULL, '2025-12-16 10:26:25', NULL, NULL, 0, 0, '000000', 103, NULL, '0', 1, '2025-12-16 10:26:25', 1, NULL, 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (5, 'TARGET_IN', 3004, 'IF20251203007', '把管', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 10, 'L014', '把管库位', NULL, NULL, NULL, NULL, NULL, NULL, 61, 1, 120.00, 0.00, 120.00, '2025-12-16 10:26:25', NULL, NULL, '2025-12-16 10:26:25', NULL, NULL, 0, 0, '000000', 103, NULL, '0', 1, '2025-12-16 10:26:25', 1, NULL, 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (6, 'TARGET_IN', 3005, 'IF20251203008', '把套', NULL, '1', '克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 11, 'L015', '把套库位', NULL, NULL, NULL, NULL, NULL, NULL, 62, 1, 250.00, 0.00, 250.00, '2025-12-16 10:26:25', NULL, NULL, '2025-12-16 10:26:25', NULL, NULL, 0, 0, '000000', 103, NULL, '0', 1, '2025-12-16 10:26:25', 1, NULL, 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2054397446203510786, 'VIRTUAL_OUT', 1, 'IF20251016004', '钢板', NULL, '2', '千克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_MATERIAL_RECEIVE', 2054376868876881922, 'CGRK20260513001', NULL, 2054397446132207618, -1, 100.00, 100.00, 0.00, '2026-05-13 11:04:55', NULL, '2026-05-13 11:04:55', '2026-05-13 11:04:55', NULL, NULL, 0, 0, '000000', 103, '', '0', 1, '2026-05-13 11:04:55', 1, '2026-05-13 11:04:55', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2054397446245453826, 'TARGET_IN', 1, 'IF20251016004', '钢板', NULL, '2', '千克', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_MATERIAL_RECEIVE', 2054376868876881922, 'CGRK20260513001', NULL, 2054397446228676610, 1, 100.00, 0.00, 100.00, '2026-05-13 11:04:55', NULL, '2026-05-13 11:04:55', '2026-05-13 11:04:55', NULL, NULL, 0, 0, '000000', 103, '', '0', 1, '2026-05-13 11:04:55', 1, '2026-05-13 11:04:55', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2063625490677235713, 'VIRTUAL_OUT', 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_MATERIAL_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, 2063625490610126850, -1, 1000.00, 1000.00, 0.00, '2026-06-07 22:13:52', NULL, '2026-06-07 22:13:52', '2026-06-07 22:13:52', NULL, NULL, 0, 0, '000000', 103, '最新的一吨钢板', '0', 1, '2026-06-07 22:13:52', 1, '2026-06-07 22:13:52', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2063625490677235715, 'TARGET_IN', 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_MATERIAL_RECEIVE', 2062395873109688322, 'CGRK20260604002', NULL, 2063625490677235714, 1, 1000.00, 0.00, 1000.00, '2026-06-07 22:13:52', NULL, '2026-06-07 22:13:52', '2026-06-07 22:13:52', NULL, NULL, 0, 0, '000000', 103, '最新的一吨钢板', '0', 1, '2026-06-07 22:13:52', 1, '2026-06-07 22:13:52', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069624232344469505, 'PRODUCTION_ISSUE', 10, 'LUBRICANT', '润滑油', NULL, '23', '升', NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 12, 'L016', '润滑油', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2069315834902159362, NULL, 2069315834977656834, 1005, -1, 100.00, 500.00, 400.00, '2026-06-24 11:30:43', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-06-24 11:30:43', 1, '2026-06-24 11:30:43', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069624232503853058, 'PRODUCTION_ISSUE', 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2069315834902159362, NULL, 2069315835015405569, 2063625490677235714, -1, 100.00, 1000.00, 900.00, '2026-06-24 11:30:43', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-06-24 11:30:43', 1, '2026-06-24 11:30:43', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069624232633876482, 'PRODUCTION_ISSUE', 3001, 'IF20251203004', '轮圈', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 7, 'L011', '轮圈库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2069315834902159362, NULL, 2069315835048960001, 1002, -1, 200.00, 200.00, 0.00, '2026-06-24 11:30:43', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-06-24 11:30:43', 1, '2026-06-24 11:30:43', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069624232759705601, 'PRODUCTION_ISSUE', 3002, 'IF20251203005', '轮胎', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 8, 'L012', '轮胎库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2069315834902159362, NULL, 2069315835061542913, 1003, -1, 200.00, 200.00, 0.00, '2026-06-24 11:30:43', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-06-24 11:30:44', 1, '2026-06-24 11:30:44', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069624232889729026, 'PRODUCTION_ISSUE', 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2069315834902159362, NULL, 2069315835090903042, 2063625490677235714, -1, 7.79, 900.00, 892.21, '2026-06-24 11:30:43', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-06-24 11:30:44', 1, '2026-06-24 11:30:44', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069624233002975234, 'PRODUCTION_ISSUE', 3004, 'IF20251203007', '把管', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 10, 'L014', '把管库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2069315834902159362, NULL, 2069315835124457473, 1008, -1, 101.51, 200.00, 98.50, '2026-06-24 11:30:43', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-06-24 11:30:44', 1, '2026-06-24 11:30:44', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069624233095249921, 'PRODUCTION_ISSUE', 3005, 'IF20251203008', '把套', NULL, '26', '件', NULL, NULL, 2, 'WH004', '原料仓库', 5, 'A0006', '辅料库区', 11, 'L015', '把套库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2069315834902159362, NULL, 2069315835141234689, 1009, -1, 202.00, 500.00, 298.00, '2026-06-24 11:30:43', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-06-24 11:30:44', 1, '2026-06-24 11:30:44', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2069695925196865537, 'PRODUCTION_IN', 1001, 'IF20251110001', '自行车', NULL, '26', '件', NULL, NULL, 1, 'WH003', '成品仓库', 1, 'A0002', '成品区', 1, 'L001', '自行车库位', NULL, NULL, 'PRD_WORK_ORDER', 2068920326621814786, NULL, NULL, 1006, 1, 50.00, 50.00, 100.00, '2026-06-24 16:15:36', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '工单完工入库', '0', 1, '2026-06-24 16:15:36', 1, '2026-06-24 16:15:36', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2070069781001744386, 'COUNT_ADJUST', 1001, 'IF20251110001', '自行车', NULL, '26', '件', NULL, NULL, 1, 'WH003', '成品仓库', 1, 'A0002', '成品区', 1, 'L001', '自行车库位', NULL, NULL, 'WM_STOCK_TAKING', 2069684336863395841, NULL, 2070069750991499266, 1006, -1, 100.00, 100.00, 0.00, '2026-06-25 17:01:10', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '盘点调整，账面:100.0000 实际:0.0000 差异:-100.0000', '0', 1, '2026-06-25 17:01:10', 1, '2026-06-25 17:01:10', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2074141007800332290, 'PRODUCTION_ISSUE', 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2074134799278313474, NULL, 2074134799337033730, 2063625490677235714, -1, 31.00, 892.21, 861.21, '2026-07-06 22:38:47', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-07-06 22:38:47', 1, '2026-07-06 22:38:47', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2074141476891291650, 'PRODUCTION_ISSUE', 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2074134799278313474, NULL, 2074134799337033730, 2063625490677235714, -1, 31.00, 861.21, 830.21, '2026-07-06 22:40:38', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-07-06 22:40:38', 1, '2026-07-06 22:40:38', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2074152062656180225, 'VIRTUAL_OUT', 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 2074149637295607809, 'B202607060002', 3, 'WH005', '半成品仓库', 6, 'A0007', '加工区', 2074149487256965121, 'L002', '连接件包', NULL, NULL, 'WM_MATERIAL_RECEIVE', 2074145387983302657, 'CGRK20260706001', NULL, 2074152062618431489, -1, 10.00, 10.00, 0.00, '2026-07-06 23:22:42', NULL, '2026-07-06 23:22:42', '2026-07-06 23:22:42', NULL, NULL, 0, 0, '000000', 103, '', '0', 1, '2026-07-06 23:22:42', 1, '2026-07-06 23:22:42', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2074152062723289089, 'TARGET_IN', 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 2074149637295607809, 'B202607060002', 3, 'WH005', '半成品仓库', 6, 'A0007', '加工区', 2074149487256965121, 'L002', '连接件包', NULL, NULL, 'WM_MATERIAL_RECEIVE', 2074145387983302657, 'CGRK20260706001', NULL, 2074152062656180226, 1, 10.00, 0.00, 10.00, '2026-07-06 23:22:42', NULL, '2026-07-06 23:22:42', '2026-07-06 23:22:42', NULL, NULL, 0, 0, '000000', 103, '', '0', 1, '2026-07-06 23:22:42', 1, '2026-07-06 23:22:42', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2074152480194949122, 'PRODUCTION_ISSUE', 1, 'IF20251016004', '钢板', NULL, NULL, '千克', 2062717540369408001, 'B202606050002', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 6, 'L010', '钢板库位', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2074134799278313474, NULL, 2074134799337033730, 2063625490677235714, -1, 31.00, 830.21, 799.21, '2026-07-06 23:24:22', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-07-06 23:24:22', 1, '2026-07-06 23:24:22', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2074152551061909506, 'PRODUCTION_ISSUE', 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', NULL, '套', 2074149637295607809, 'B202607060002', 3, 'WH005', '半成品仓库', 6, 'A0007', '加工区', 2074149487256965121, 'L002', '连接件包', NULL, NULL, 'WM_PRODUCTION_ISSUE', 2074134799278313474, NULL, 2074141167745921025, 2074152062656180226, -1, 10.00, 10.00, 0.00, '2026-07-06 23:24:39', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '生产领料', '0', 1, '2026-07-06 23:24:39', 1, '2026-07-06 23:24:39', 0.000000, 0.0000);
INSERT INTO `wm_material_stock_tx` VALUES (2074160243717345281, 'PRODUCTION_IN', 8003, 'RACK-001', '简易金属工具架', NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, NULL, 'PRD_WORK_ORDER', 2073979191344271361, NULL, NULL, 2074160243641847809, 1, 10.00, 0.00, 10.00, '2026-07-06 23:55:13', NULL, NULL, NULL, NULL, NULL, 0, 0, '000000', 103, '工单完工入库', '0', 1, '2026-07-06 23:55:13', 1, '2026-07-06 23:55:13', 0.000000, 0.0000);

-- ----------------------------
-- Table structure for wm_misc_issue
-- ----------------------------
DROP TABLE IF EXISTS `wm_misc_issue`;
CREATE TABLE `wm_misc_issue`  (
  `issue_id` bigint(20) NOT NULL COMMENT '杂项出库ID',
  `issue_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单号',
  `issue_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出库类型',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `issue_date` datetime NULL DEFAULT NULL COMMENT '出库日期',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`issue_id`) USING BTREE,
  INDEX `idx_misc_issue_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '杂项出库单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_misc_issue
-- ----------------------------

-- ----------------------------
-- Table structure for wm_misc_issue_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_misc_issue_line`;
CREATE TABLE `wm_misc_issue_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `issue_id` bigint(20) NOT NULL COMMENT '杂项出库单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编码',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '出库数量',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_misc_issue_line_mid`(`issue_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '杂项出库行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_misc_issue_line
-- ----------------------------

-- ----------------------------
-- Table structure for wm_misc_recpt
-- ----------------------------
DROP TABLE IF EXISTS `wm_misc_recpt`;
CREATE TABLE `wm_misc_recpt`  (
  `recpt_id` bigint(20) NOT NULL COMMENT '杂项入库ID',
  `recpt_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单号',
  `recpt_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '入库类型',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区名称',
  `recpt_date` datetime NULL DEFAULT NULL COMMENT '入库日期',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`recpt_id`) USING BTREE,
  INDEX `idx_misc_recpt_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '杂项入库单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_misc_recpt
-- ----------------------------

-- ----------------------------
-- Table structure for wm_misc_recpt_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_misc_recpt_line`;
CREATE TABLE `wm_misc_recpt_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `recpt_id` bigint(20) NOT NULL COMMENT '杂项入库单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编码',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '入库数量',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_misc_recpt_line_mid`(`recpt_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '杂项入库行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_misc_recpt_line
-- ----------------------------

-- ----------------------------
-- Table structure for wm_picking_rule
-- ----------------------------
DROP TABLE IF EXISTS `wm_picking_rule`;
CREATE TABLE `wm_picking_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID(空=全局)',
  `material_category_id` bigint(20) NULL DEFAULT NULL COMMENT '物料类别ID(空=全局)',
  `strategy_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIFO' COMMENT '策略类型(FIFO/FEFO/BATCH_PRIORITY/LOCATION_PATH)',
  `priority` int(11) NULL DEFAULT 100 COMMENT '优先级',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE INDEX `uk_rule_code`(`rule_code`, `tenant_id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '拣货规则配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_picking_rule
-- ----------------------------

-- ----------------------------
-- Table structure for wm_production_issue
-- ----------------------------
DROP TABLE IF EXISTS `wm_production_issue`;
CREATE TABLE `wm_production_issue`  (
  `issue_id` bigint(20) NOT NULL COMMENT '生产领料ID',
  `issue_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领料单号',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `issue_date` datetime NULL DEFAULT NULL COMMENT '领料日期',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `issue_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'NORMAL' COMMENT '领料类型(NORMAL普通/BACKFLUSH倒冲)',
  `issuer_id` bigint(20) NULL DEFAULT NULL COMMENT '领料人ID',
  `issuer_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '领料人名称',
  `total_qty` decimal(18, 4) NULL DEFAULT NULL COMMENT '总数量',
  `total_amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '总金额',
  PRIMARY KEY (`issue_id`) USING BTREE,
  INDEX `idx_prod_issue_status`(`status`) USING BTREE,
  INDEX `idx_prod_issue_wo`(`wo_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产领料单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_production_issue
-- ----------------------------
INSERT INTO `wm_production_issue` VALUES (2068980090844217346, 'SCLL202606220001', 2068920326621814786, 'SCGD202606220001', 1, NULL, NULL, '2026-06-22 16:51:08', 'CREATED', '工单下达自动生成', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', 'NORMAL', NULL, NULL, NULL, NULL);
INSERT INTO `wm_production_issue` VALUES (2069315834902159362, 'SCLL202606230001', 2068920326621814786, 'SCGD202606220001', 1, NULL, NULL, '2026-06-23 15:05:16', 'PARTIAL', '工单下达自动生成', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:44', 'NORMAL', NULL, NULL, 921.3362, 20000.0000);
INSERT INTO `wm_production_issue` VALUES (2074128788853243905, 'SCLL202607060001', 2073979191344271361, 'SCGD202607060001', 3, NULL, NULL, '2026-07-06 21:50:13', 'CREATED', '工单下达自动生成', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 21:50:13', 1, '2026-07-06 22:13:25', 'NORMAL', NULL, NULL, NULL, NULL);
INSERT INTO `wm_production_issue` VALUES (2074130105474629634, 'SCLL202607060002', 2073979191344271361, 'SCGD202607060001', 3, NULL, NULL, '2026-07-06 21:55:27', 'CREATED', '工单下达自动生成', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 21:55:27', 1, '2026-07-06 22:13:22', 'NORMAL', NULL, NULL, NULL, NULL);
INSERT INTO `wm_production_issue` VALUES (2074134799278313474, 'SCLL202607060003', 2073979191344271361, 'SCGD202607060001', 3, NULL, NULL, '2026-07-06 22:14:06', 'COMPLETED', '工单下达自动生成', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 22:14:06', 1, '2026-07-06 23:24:39', 'NORMAL', NULL, NULL, 31.0000, 0.0000);

-- ----------------------------
-- Table structure for wm_production_issue_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_production_issue_line`;
CREATE TABLE `wm_production_issue_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `issue_id` bigint(20) NOT NULL COMMENT '生产领料单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编码',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '领料数量',
  `unit_price` decimal(18, 4) NULL DEFAULT NULL COMMENT '单价',
  `amount` decimal(18, 4) NULL DEFAULT NULL COMMENT '金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `bom_qty` decimal(18, 4) NULL DEFAULT NULL COMMENT 'BOM标准用量（单位产品用量）',
  `loss_rate` decimal(8, 4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)',
  `required_qty` decimal(18, 4) NULL DEFAULT NULL COMMENT '应领数量（BOM定额×工单数量）',
  `issued_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已领数量（累计已发）',
  `current_qty` decimal(18, 4) NULL DEFAULT NULL COMMENT '本次领料数量',
  `short_qty` decimal(18, 4) NULL DEFAULT NULL COMMENT '欠领数量（应领-已领）',
  `requirement_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '物料需求明细ID（追溯工单BOM需求）',
  `line_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '行状态(PENDING/PARTIAL/ISSUED)',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_prod_issue_line_mid`(`issue_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产领料行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_production_issue_line
-- ----------------------------
INSERT INTO `wm_production_issue_line` VALUES (2068980090894548994, 2068980090844217346, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', NULL, NULL, NULL, NULL, NULL, NULL, 100.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', NULL, 0.0000, 100.0000, 0.0000, NULL, 100.0000, NULL, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2068980090928103426, 2068980090844217346, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, NULL, NULL, NULL, NULL, NULL, 100.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', NULL, 0.0000, 100.0000, 0.0000, NULL, 100.0000, NULL, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2068980090965852161, 2068980090844217346, 3001, 'IF20251203004', '轮圈', NULL, '1', NULL, NULL, NULL, NULL, NULL, NULL, 206.0400, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', NULL, 0.0000, 206.0400, 0.0000, NULL, 206.0400, NULL, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2068980090974240770, 2068980090844217346, 3002, 'IF20251203005', '轮胎', NULL, '1', NULL, NULL, NULL, NULL, NULL, NULL, 204.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', NULL, 0.0000, 204.0000, 0.0000, NULL, 204.0000, NULL, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2068980090999406594, 2068980090844217346, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, NULL, NULL, NULL, NULL, NULL, 7.7912, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', NULL, 0.0000, 7.7912, 0.0000, NULL, 7.7912, NULL, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2068980091024572417, 2068980090844217346, 3004, 'IF20251203007', '把管', NULL, '1', NULL, NULL, NULL, NULL, NULL, NULL, 101.5050, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', NULL, 0.0000, 101.5050, 0.0000, NULL, 101.5050, NULL, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2068980091037155329, 2068980090844217346, 3005, 'IF20251203008', '把套', NULL, '1', NULL, NULL, NULL, NULL, NULL, NULL, 202.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-22 16:51:08', 1, '2026-06-22 16:51:08', NULL, 0.0000, 202.0000, 0.0000, NULL, 202.0000, NULL, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2069315834977656834, 2069315834902159362, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '29', NULL, NULL, NULL, 12, 'L016', '润滑油', 100.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:43', 1.0000, 0.0000, 100.0000, 100.0000, 100.0000, 0.0000, 2068919533550231554, 'ISSUED');
INSERT INTO `wm_production_issue_line` VALUES (2069315835015405569, 2069315834902159362, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, 2062717540369408001, 'B202606050002', 6, 'L010', '钢板库位', 100.0000, 200.0000, 20000.0000, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:43', 1.0000, 0.0000, 100.0000, 100.0000, 100.0000, 0.0000, 2068919533755752449, 'ISSUED');
INSERT INTO `wm_production_issue_line` VALUES (2069315835048960001, 2069315834902159362, 3001, 'IF20251203004', '轮圈', NULL, '1', NULL, NULL, NULL, 7, 'L011', '轮圈库位', 206.0400, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:43', 1.0000, 1.0000, 206.0400, 200.0000, 200.0000, 6.0400, 2068919534003216386, 'PARTIAL');
INSERT INTO `wm_production_issue_line` VALUES (2069315835061542913, 2069315834902159362, 3002, 'IF20251203005', '轮胎', NULL, '1', NULL, NULL, NULL, 8, 'L012', '轮胎库位', 204.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:44', 1.0000, 0.0000, 204.0000, 200.0000, 200.0000, 4.0000, 2068919534015799297, 'PARTIAL');
INSERT INTO `wm_production_issue_line` VALUES (2069315835090903042, 2069315834902159362, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, 2062717540369408001, 'B202606050002', 6, 'L010', '钢板库位', 7.7912, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:44', 0.0010, 3.0000, 7.7912, 7.7912, 7.7912, 0.0000, 2068919534200348673, 'ISSUED');
INSERT INTO `wm_production_issue_line` VALUES (2069315835124457473, 2069315834902159362, 3004, 'IF20251203007', '把管', NULL, '1', NULL, NULL, NULL, 10, 'L014', '把管库位', 101.5050, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:44', 1.0000, 0.5000, 101.5050, 101.5050, 101.5050, 0.0000, 2068919534435229697, 'ISSUED');
INSERT INTO `wm_production_issue_line` VALUES (2069315835141234689, 2069315834902159362, 3005, 'IF20251203008', '把套', NULL, '1', NULL, NULL, NULL, 11, 'L015', '把套库位', 202.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-06-23 15:05:16', 1, '2026-06-24 11:30:44', 2.0000, 0.0000, 202.0000, 202.0000, 202.0000, 0.0000, 2068919534447812609, 'ISSUED');
INSERT INTO `wm_production_issue_line` VALUES (2074128788853243906, 2074128788853243905, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '28', NULL, NULL, NULL, NULL, NULL, NULL, 10.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 21:50:13', 1, '2026-07-06 21:50:13', 1.0000, 0.0000, 10.0000, 0.0000, 10.0000, 10.0000, 2073655856154710018, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2074128788853243907, 2074128788853243905, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, NULL, NULL, NULL, NULL, NULL, 31.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 21:50:13', 1, '2026-07-06 21:50:13', 1.5000, 2.0000, 31.0000, 0.0000, 31.0000, 31.0000, 2073655856351842306, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2074130105474629635, 2074130105474629634, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '28', NULL, NULL, NULL, NULL, NULL, NULL, 10.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 21:55:27', 1, '2026-07-06 21:55:27', 1.0000, 0.0000, 10.0000, 0.0000, 10.0000, 10.0000, 2073655856154710018, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2074130105537544193, 2074130105474629634, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, NULL, NULL, NULL, NULL, NULL, 31.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 21:55:27', 1, '2026-07-06 21:55:27', 1.5000, 2.0000, 31.0000, 0.0000, 31.0000, 31.0000, 2073655856351842306, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2074134799337033729, 2074134799278313474, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '28', NULL, NULL, NULL, NULL, NULL, NULL, 10.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 22:14:06', 1, '2026-07-06 22:38:47', 1.0000, 0.0000, 10.0000, 0.0000, 10.0000, 10.0000, 2073655856154710018, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2074134799337033730, 2074134799278313474, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, 2062717540369408001, 'B202606050002', 6, 'L010', '钢板库位', 31.0000, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 22:14:06', 1, '2026-07-06 23:24:39', 1.5000, 2.0000, 31.0000, 93.0000, 0.0000, 0.0000, 2073655856351842306, 'ISSUED');
INSERT INTO `wm_production_issue_line` VALUES (2074141099554926593, 2074134799278313474, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '28', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 22:39:08', 1, '2026-07-06 22:39:25', 1.0000, 0.0000, 10.0000, 0.0000, 10.0000, 10.0000, 2073655856154710018, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2074141099554926594, 2074134799278313474, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 22:39:08', 1, '2026-07-06 22:39:25', 1.5000, 2.0000, 31.0000, 0.0000, 31.0000, 31.0000, 2073655856351842306, 'PENDING');
INSERT INTO `wm_production_issue_line` VALUES (2074141167745921025, 2074134799278313474, 8002, 'RACK-FIT', '连接件包', 'M6螺丝*8+挂钩*4+膨胀管*4', '28', NULL, 2074149637295607809, 'B202607060002', 2074149487256965121, 'L002', '连接件包', NULL, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 22:39:25', 1, '2026-07-06 23:24:39', 1.0000, 0.0000, 10.0000, 10.0000, 10.0000, 0.0000, 2073655856154710018, 'ISSUED');
INSERT INTO `wm_production_issue_line` VALUES (2074141167745921026, 2074134799278313474, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '2', NULL, 2062717540369408001, 'B202606050002', 6, 'L010', '钢板库位', NULL, NULL, NULL, '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-07-06 22:39:25', 1, '2026-07-06 22:40:38', 1.5000, 2.0000, 31.0000, 0.0000, 31.0000, 31.0000, 2073655856351842306, 'PENDING');

-- ----------------------------
-- Table structure for wm_production_recpt
-- ----------------------------
DROP TABLE IF EXISTS `wm_production_recpt`;
CREATE TABLE `wm_production_recpt`  (
  `recpt_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '入库ID',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  `recpt_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单号',
  `wo_id` bigint(20) NULL DEFAULT NULL COMMENT '工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `quantity` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '入库数量',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `recpt_date` date NULL DEFAULT NULL COMMENT '入库日期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿, EXECUTED-已执行',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID（生产入库时自动生成或引用）',
  `batch_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次号（生产入库时自动生成或引用）',
  PRIMARY KEY (`recpt_id`) USING BTREE,
  UNIQUE INDEX `uk_recpt_code`(`recpt_code`, `tenant_id`) USING BTREE,
  INDEX `idx_wo`(`wo_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_wm_production_recpt_batch_id`(`batch_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2074153963774799874 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '生产完工入库表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_production_recpt
-- ----------------------------
INSERT INTO `wm_production_recpt` VALUES (10001, 0, 'PR-TEST-001', 10001, 'WO-TEST-001', 1001, 'IF20251110001', '自行车', NULL, 50.0000, 1, 'WH003', '成品仓库', 1, 'L001', '自行车库位', '2026-06-25', 'EXECUTED', '第一批50辆自行车入库', 100, 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', '0', NULL, NULL);
INSERT INTO `wm_production_recpt` VALUES (2069684019362971650, 0, 'SCRK20260624', 2068920326621814786, 'SCGD202606220001', 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', 50.0000, 1, NULL, NULL, 1, NULL, NULL, NULL, 'EXECUTED', NULL, 103, 1, '2026-06-24 15:28:18', 1, '2026-06-25 17:07:03', '0', NULL, NULL);
INSERT INTO `wm_production_recpt` VALUES (2074153963774799873, 0, 'SCRK20260706001', 2073979191344271361, 'SCGD202607060001', 8003, 'RACK-001', '简易金属工具架', '600mm*300mm*800mm', 10.0000, 1, NULL, NULL, 1, NULL, NULL, NULL, 'EXECUTED', NULL, 103, 1, '2026-07-06 23:30:16', 1, '2026-07-06 23:55:13', '0', NULL, NULL);

-- ----------------------------
-- Table structure for wm_putaway_rule
-- ----------------------------
DROP TABLE IF EXISTS `wm_putaway_rule`;
CREATE TABLE `wm_putaway_rule`  (
  `rule_id` bigint(20) NOT NULL COMMENT '规则ID',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID(空=全局)',
  `material_category_id` bigint(20) NULL DEFAULT NULL COMMENT '物料类别ID(空=全局)',
  `strategy_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEAREST_EMPTY' COMMENT '策略类型(NEAREST_EMPTY/CLASS_BASED/FIFO_LOCATION)',
  `priority` int(11) NULL DEFAULT 100 COMMENT '优先级(越小越优先)',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE INDEX `uk_rule_code`(`rule_code`, `tenant_id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_category`(`material_category_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '上架规则配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_putaway_rule
-- ----------------------------

-- ----------------------------
-- Table structure for wm_sales_issue
-- ----------------------------
DROP TABLE IF EXISTS `wm_sales_issue`;
CREATE TABLE `wm_sales_issue`  (
  `issue_id` bigint(20) NOT NULL COMMENT '出库单ID',
  `issue_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单编号',
  `issue_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出库单名称',
  `so_id` bigint(20) NULL DEFAULT NULL COMMENT '销售订单ID',
  `so_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '销售订单编号',
  `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `issue_date` date NULL DEFAULT NULL COMMENT '出库日期',
  `total_quantity` decimal(16, 6) NULL DEFAULT 0.000000 COMMENT '出库总数量',
  `total_amount` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '出库总金额',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '单据状态(PREPARE-准备中，CONFIRMED-已确认，COMPLETED-已完成，CANCELLED-已取消)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流程模板ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `attr1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` bigint(20) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` bigint(20) NULL DEFAULT NULL COMMENT '预留字段4',
  PRIMARY KEY (`issue_id`) USING BTREE,
  UNIQUE INDEX `uk_issue_code`(`issue_code`) USING BTREE,
  INDEX `idx_so_id`(`so_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_issue_date`(`issue_date`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售出库主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_sales_issue
-- ----------------------------
INSERT INTO `wm_sales_issue` VALUES (10001, 'SI-TEST-001', '小明科技出货单', 10001, 'SO-TEST-001', 2, 'C00003', '小明科技有限公司', 1, 'WH003', '成品仓库', '2026-06-26', 30.000000, 45000.0000, 'DRAFT', '小明科技第一批出货30辆', NULL, '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for wm_sales_issue_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_sales_issue_line`;
CREATE TABLE `wm_sales_issue_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '出库单行ID',
  `issue_id` bigint(20) NOT NULL COMMENT '出库单ID',
  `source_doc_line_id` bigint(20) NULL DEFAULT NULL COMMENT '来源单据行ID（销售订单行ID）',
  `line_no` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行号',
  `material_id` bigint(20) NOT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次号',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `quantity_issue` decimal(16, 6) NOT NULL DEFAULT 0.000000 COMMENT '出库数量',
  `quantity_ordered` decimal(16, 6) NULL DEFAULT 0.000000 COMMENT '订单数量（冗余）',
  `quantity_delivered` decimal(16, 6) NULL DEFAULT 0.000000 COMMENT '已交付数量（冗余）',
  `unit_price` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '单价',
  `line_amount` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '行金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_issue_id`(`issue_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_batch_id`(`batch_id`) USING BTREE,
  INDEX `idx_location_id`(`location_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '销售出库明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_sales_issue_line
-- ----------------------------
INSERT INTO `wm_sales_issue_line` VALUES (10001, 10001, 10001, '1', 1001, 'IF20251110001', '自行车', NULL, '26', '件', NULL, NULL, 1, 'L001', '自行车库位', 30.000000, 100.000000, 0.000000, 1500.0000, 45000.0000, '出货30辆自行车', '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14');

-- ----------------------------
-- Table structure for wm_sn
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
  UNIQUE INDEX `uk_sn_code`(`sn_code`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_batch_id`(`batch_id`) USING BTREE,
  INDEX `idx_warehouse_id`(`warehouse_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_stock_id`(`stock_id`) USING BTREE,
  INDEX `idx_source_doc`(`source_doc_type`, `source_doc_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '序列号管理表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_sn
-- ----------------------------

-- ----------------------------
-- Table structure for wm_stock_alert
-- ----------------------------
DROP TABLE IF EXISTS `wm_stock_alert`;
CREATE TABLE `wm_stock_alert`  (
  `alert_id` bigint(20) NOT NULL COMMENT '预警ID',
  `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `current_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '当前库存',
  `safety_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '安全库存',
  `min_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最低库存',
  `max_stock` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '最高库存',
  `alert_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预警类型',
  `alert_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'WARNING' COMMENT '预警级别',
  `handled_flag` tinyint(4) NULL DEFAULT 0 COMMENT '处理状态(0未处理1已处理)',
  `handled_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `handled_by` bigint(20) NULL DEFAULT NULL COMMENT '处理人ID',
  `handled_remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处理说明',
  `del_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`alert_id`) USING BTREE,
  INDEX `idx_alert_material`(`material_id`, `alert_type`, `handled_flag`) USING BTREE,
  INDEX `idx_alert_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安全库存预警表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_stock_alert
-- ----------------------------
INSERT INTO `wm_stock_alert` VALUES (2059923485663133698, 0, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', NULL, NULL, NULL, 0.0000, 10.0000, 15.0000, 45.0000, 'BELOW_MIN', 'CRITICAL', 1, '2026-05-28 17:04:12', NULL, '采购钢板', '1', 103, 1, '2026-05-28 17:03:25', 1, '2026-06-03 15:56:31', NULL);
INSERT INTO `wm_stock_alert` VALUES (2059923485772185602, 0, 2, 'IF20251018001', '电机Y132M-4 7.5KW', '7.5KW 380V', '千克', NULL, NULL, NULL, 0.0000, 2.0000, 3.0000, 9.0000, 'BELOW_MIN', 'CRITICAL', 0, NULL, NULL, NULL, '1', 103, 1, '2026-05-28 17:03:25', 1, '2026-06-03 15:56:31', NULL);
INSERT INTO `wm_stock_alert` VALUES (2059923485839294466, 0, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', '件', NULL, NULL, NULL, 0.0000, 3.0000, 4.5000, 13.5000, 'BELOW_MIN', 'CRITICAL', 0, NULL, NULL, NULL, '1', 103, 1, '2026-05-28 17:03:25', 1, '2026-06-03 15:56:31', NULL);
INSERT INTO `wm_stock_alert` VALUES (2063647119193014274, 0, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', NULL, NULL, NULL, 0.0000, 10.0000, 15.0000, 45.0000, 'BELOW_MIN', 'CRITICAL', 1, '2026-06-08 00:04:02', NULL, '库存状态变更，自动关闭', '0', 103, 1, '2026-06-07 23:39:49', 1, '2026-06-08 00:04:02', NULL);
INSERT INTO `wm_stock_alert` VALUES (2063647119255928834, 0, 2, 'IF20251018001', '电机Y132M-4 7.5KW', '7.5KW 380V', '千克', NULL, NULL, NULL, 0.0000, 2.0000, 3.0000, 9.0000, 'BELOW_MIN', 'CRITICAL', 0, NULL, NULL, NULL, '0', 103, 1, '2026-06-07 23:39:49', 1, '2026-06-24 15:23:58', NULL);
INSERT INTO `wm_stock_alert` VALUES (2063647119255928835, 0, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', '件', NULL, NULL, NULL, 0.0000, 3.0000, 4.5000, 13.5000, 'BELOW_MIN', 'CRITICAL', 1, '2026-06-24 15:23:58', NULL, '库存状态变更，自动关闭', '0', 103, 1, '2026-06-07 23:39:49', 1, '2026-06-24 15:23:58', NULL);
INSERT INTO `wm_stock_alert` VALUES (2063651383273455617, 0, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', NULL, NULL, NULL, 1392.2100, 10.0000, 15.0000, 45.0000, 'ABOVE_MAX', 'INFO', 0, NULL, NULL, NULL, '0', 103, 1, '2026-06-07 23:56:45', 1, '2026-06-24 15:23:58', NULL);
INSERT INTO `wm_stock_alert` VALUES (2069682931637338114, 0, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', '件', NULL, NULL, NULL, 50.0000, 3.0000, 4.5000, 13.5000, 'ABOVE_MAX', 'INFO', 0, NULL, NULL, NULL, '0', 103, 1, '2026-06-24 15:23:58', 1, '2026-06-24 15:23:58', NULL);

-- ----------------------------
-- Table structure for wm_stock_reconciliation
-- ----------------------------
DROP TABLE IF EXISTS `wm_stock_reconciliation`;
CREATE TABLE `wm_stock_reconciliation`  (
  `recon_id` bigint(20) NOT NULL COMMENT '校验ID',
  `tenant_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '租户ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  `base_uom_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `book_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '账面库存',
  `tx_balance` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '事务汇总',
  `diff_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '差异数量',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DIFF' COMMENT '校验状态(MATCH-一致,DIFF-有差异)',
  `recon_time` datetime NULL DEFAULT NULL COMMENT '校验时间',
  `del_flag` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`recon_id`) USING BTREE,
  INDEX `idx_recon_material`(`material_id`, `status`) USING BTREE,
  INDEX `idx_recon_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存台账校验表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_stock_reconciliation
-- ----------------------------
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733977276417, 0, 1, NULL, NULL, NULL, NULL, 1500.0000, 0.0000, 1500.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733989859329, 0, 7, NULL, NULL, NULL, NULL, 5000.0000, 0.0000, 5000.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733989859330, 0, 10, NULL, NULL, NULL, NULL, 500.0000, 110.0000, 390.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:10', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733989859331, 0, 1001, NULL, NULL, NULL, NULL, 50.0000, 0.0000, 50.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:10', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733989859332, 0, 3001, NULL, NULL, NULL, NULL, 200.0000, 250.0000, -50.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:10', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733989859333, 0, 3002, NULL, NULL, NULL, NULL, 200.0000, 250.0000, -50.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:10', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733989859334, 0, 3004, NULL, NULL, NULL, NULL, 200.0000, 120.0000, 80.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:10', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069345733998247938, 0, 3005, NULL, NULL, NULL, NULL, 500.0000, 250.0000, 250.0000, 'DIFF', '2026-06-23 17:04:04', '1', 103, 1, '2026-06-23 17:04:04', 1, '2026-06-24 11:32:10', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532463697922, 0, 1, NULL, NULL, NULL, NULL, 1392.2100, -107.7900, 1500.0000, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532472086530, 0, 7, NULL, NULL, NULL, NULL, 5000.0000, 0.0000, 5000.0000, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532476280834, 0, 10, NULL, NULL, NULL, NULL, 400.0000, 10.0000, 390.0000, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532476280835, 0, 1001, NULL, NULL, NULL, NULL, 50.0000, 0.0000, 50.0000, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532476280836, 0, 3001, NULL, NULL, NULL, NULL, 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532476280837, 0, 3002, NULL, NULL, NULL, NULL, 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532480475138, 0, 3004, NULL, NULL, NULL, NULL, 98.5000, 18.4900, 80.0100, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2069624532480475139, 0, 3005, NULL, NULL, NULL, NULL, 298.0000, 48.0000, 250.0000, 'DIFF', '2026-06-24 11:31:55', '1', 103, 1, '2026-06-24 11:31:55', 1, '2026-06-24 11:32:03', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680475746305, 0, 1, NULL, NULL, NULL, NULL, 1392.2100, -107.7900, 1500.0000, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680492523521, 0, 7, NULL, NULL, NULL, NULL, 5000.0000, 0.0000, 5000.0000, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680492523522, 0, 10, NULL, NULL, NULL, NULL, 400.0000, 10.0000, 390.0000, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:30', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680492523523, 0, 1001, NULL, NULL, NULL, NULL, 100.0000, 249.0000, -149.0000, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:30', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680492523524, 0, 3001, NULL, NULL, NULL, NULL, 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:30', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680492523525, 0, 3002, NULL, NULL, NULL, NULL, 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:30', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680500912129, 0, 3004, NULL, NULL, NULL, NULL, 98.5000, 18.4900, 80.0100, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:30', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070066680505106434, 0, 3005, NULL, NULL, NULL, NULL, 298.0000, 48.0000, 250.0000, 'DIFF', '2026-06-25 16:48:51', '1', 103, 1, '2026-06-25 16:48:51', 1, '2026-06-25 16:59:30', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095807660034, 0, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', 1392.2100, -107.7900, 1500.0000, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095837020161, 0, 7, 'BOLT_M12', '螺栓M12', '8.8级', '克', 5000.0000, 0.0000, 5000.0000, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095845408769, 0, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '打', 400.0000, 10.0000, 390.0000, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095849603074, 0, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', '件', 100.0000, 249.0000, -149.0000, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095853797378, 0, 3001, 'IF20251203004', '轮圈', NULL, '克', 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095862185986, 0, 3002, 'IF20251203005', '轮胎', NULL, '克', 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095862185987, 0, 3004, 'IF20251203007', '把管', NULL, '克', 98.5000, 18.4900, 80.0100, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069095862185988, 0, 3005, 'IF20251203008', '把套', NULL, '克', 298.0000, 48.0000, 250.0000, 'DIFF', '2026-06-25 16:58:27', '1', 103, 1, '2026-06-25 16:58:27', 1, '2026-06-25 16:59:27', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363500724226, 0, 1, 'IF20251016004', '钢板', '1m*0.5m*2cm,100kg', '千克', 1392.2100, -107.7900, 1500.0000, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363513307137, 0, 7, 'BOLT_M12', '螺栓M12', '8.8级', '克', 5000.0000, 0.0000, 5000.0000, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363517501442, 0, 10, 'LUBRICANT', '润滑油', 'ISO VG46', '打', 400.0000, 10.0000, 390.0000, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363517501443, 0, 1001, 'IF20251110001', '自行车', '1.5m长0.5m高', '件', 100.0000, 50.0000, 50.0000, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363517501444, 0, 3001, 'IF20251203004', '轮圈', NULL, '克', 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363525890049, 0, 3002, 'IF20251203005', '轮胎', NULL, '克', 0.0000, 50.0000, -50.0000, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363525890050, 0, 3004, 'IF20251203007', '把管', NULL, '克', 98.5000, 18.4900, 80.0100, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);
INSERT INTO `wm_stock_reconciliation` VALUES (2070069363525890051, 0, 3005, 'IF20251203008', '把套', NULL, '克', 298.0000, 48.0000, 250.0000, 'DIFF', '2026-06-25 16:59:31', '0', 103, 1, '2026-06-25 16:59:31', 1, '2026-06-25 16:59:31', NULL);

-- ----------------------------
-- Table structure for wm_stock_taking_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_stock_taking_line`;
CREATE TABLE `wm_stock_taking_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '盘点行ID',
  `plan_id` bigint(20) NOT NULL COMMENT '盘点计划ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编码',
  `location_id` bigint(20) NULL DEFAULT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库位名称',
  `stock_id` bigint(20) NULL DEFAULT NULL COMMENT '库存记录ID',
  `book_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '账面数量',
  `actual_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '实盘数量',
  `difference_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '差异数量',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_taking_line_pid`(`plan_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '盘点行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_stock_taking_line
-- ----------------------------
INSERT INTO `wm_stock_taking_line` VALUES (2070069689423310850, 2069684336863395841, 1001, 'IF20251110001', '自行车', NULL, '26', '件', NULL, NULL, 1, 'L001', '自行车库位', 1006, 100.0000, 0.0000, 0.0000, 'PENDING', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-25 17:00:49', 1, '2026-06-25 17:00:49');
INSERT INTO `wm_stock_taking_line` VALUES (2070069750991499266, 2069684336863395841, 1001, 'IF20251110001', '自行车', NULL, '26', '件', NULL, NULL, 1, 'L001', '自行车库位', 1006, 100.0000, 0.0000, -100.0000, 'POSTED', '', NULL, NULL, 0, 0, '000000', 103, '1', 1, '2026-06-25 17:01:03', 1, '2026-06-25 17:01:10');

-- ----------------------------
-- Table structure for wm_stock_taking_plan
-- ----------------------------
DROP TABLE IF EXISTS `wm_stock_taking_plan`;
CREATE TABLE `wm_stock_taking_plan`  (
  `plan_id` bigint(20) NOT NULL COMMENT '盘点计划ID',
  `plan_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划编号',
  `plan_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `area_id` bigint(20) NULL DEFAULT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '库区名称',
  `planned_date` datetime NULL DEFAULT NULL COMMENT '计划盘点日期',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `act_process_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流程实例ID',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_taking_plan_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '盘点计划表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_stock_taking_plan
-- ----------------------------
INSERT INTO `wm_stock_taking_plan` VALUES (2069684336863395841, 'PD202606240001', 'cs', 1, 'WH003', '成品仓库', 1, 'A0002', '成品区', '2026-06-24 00:00:00', 'POSTED', '', '000000', 103, '1', 1, '2026-06-24 15:29:33', 1, '2026-06-25 17:29:52', NULL, 2061989953741484034);
INSERT INTO `wm_stock_taking_plan` VALUES (2070077189707083778, 'PD202606250001', '自行车盘点', 1, 'WH003', '成品仓库', 1, 'A0002', '成品区', '2026-06-25 00:00:00', 'COMPLETED', '', '000000', 103, '0', 1, '2026-06-25 17:30:37', 1, '2026-06-25 17:31:13', NULL, 2061989953741484034);

-- ----------------------------
-- Table structure for wm_storage_area
-- ----------------------------
DROP TABLE IF EXISTS `wm_storage_area`;
CREATE TABLE `wm_storage_area`  (
  `area_id` bigint(20) NOT NULL COMMENT '库区ID',
  `area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库区编码',
  `area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库区名称',
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `area` double(12, 2) NULL DEFAULT NULL COMMENT '面积',
  `location_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否开启库位管理',
  `enable_flag` tinyint(4) NOT NULL COMMENT '是否启用',
  `frozen_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否冻结',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `layout_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'GRID' COMMENT '布局类型: GRID-网格, RACK-货架, FLUID-流体, BULK-散货',
  `pos_x` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标X(米)',
  `pos_y` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Y(米)',
  `pos_z` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Z(米)',
  `area_width` decimal(10, 2) NULL DEFAULT NULL COMMENT '库区宽度(米)',
  `area_length` decimal(10, 2) NULL DEFAULT NULL COMMENT '库区长度(米)',
  `area_height` decimal(10, 2) NULL DEFAULT 3.00 COMMENT '库区高度(米)',
  `rotation_x` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'X轴旋转角度',
  `rotation_y` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Y轴旋转角度',
  `rotation_z` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Z轴旋转角度',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#4CAF50' COMMENT '显示颜色',
  `opacity` decimal(3, 2) NULL DEFAULT 0.80 COMMENT '透明度',
  `model_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'CUBE' COMMENT '模型类型: CUBE-立方体, CUSTOM-自定义',
  `model_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '自定义模型URL',
  `show_walls` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示边界墙',
  `show_label` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示标签',
  `grid_visible` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示网格',
  `config_json` json NULL COMMENT '3D配置JSON',
  PRIMARY KEY (`area_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库区表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_storage_area
-- ----------------------------
INSERT INTO `wm_storage_area` VALUES (1, 'A0002', '成品区', 1, 20.00, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-01 22:08:20', 1, '2026-06-08 23:56:52', 'RACK', 0.00, 0.00, 0.00, NULL, NULL, 3.00, 0.00, 0.00, 0.00, '#4CAF50', 0.80, 'CUBE', NULL, 1, 1, 1, NULL);
INSERT INTO `wm_storage_area` VALUES (2, 'A0003', '半成品区', 1, 50.00, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:07:43', 1, NULL, 'PALLET', 0.00, 0.00, 0.00, NULL, NULL, 3.00, 0.00, 0.00, 0.00, '#4CAF50', 0.80, 'CUBE', NULL, 1, 1, 1, NULL);
INSERT INTO `wm_storage_area` VALUES (3, 'A0004', '废品仓', 1, 30.00, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:11:12', 1, NULL, 'BULK', 0.00, 0.00, 0.00, NULL, NULL, 3.00, 0.00, 0.00, 0.00, '#4CAF50', 0.80, 'CUBE', NULL, 1, 1, 1, NULL);
INSERT INTO `wm_storage_area` VALUES (4, 'A0005', '金属库区', 2, 200.00, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:30:57', 1, '2026-06-08 23:57:54', 'RACK', 0.00, 0.00, 0.00, NULL, NULL, 3.00, 0.00, 0.00, 0.00, '#4CAF50', 0.80, 'CUBE', NULL, 1, 1, 1, NULL);
INSERT INTO `wm_storage_area` VALUES (5, 'A0006', '辅料库区', 2, 20.00, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:14:01', 1, '2026-06-08 23:58:01', 'RACK', 0.00, 0.00, 0.00, NULL, NULL, 3.00, 0.00, 0.00, 0.00, '#4CAF50', 0.80, 'CUBE', NULL, 1, 1, 1, NULL);
INSERT INTO `wm_storage_area` VALUES (6, 'A0007', '加工区', 3, 800.00, 1, 1, 1, '半成品加工存放区', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', 'GRID', 0.00, 0.00, 0.00, NULL, NULL, 3.00, 0.00, 0.00, 0.00, '#4CAF50', 0.80, 'CUBE', NULL, 1, 1, 1, NULL);
INSERT INTO `wm_storage_area` VALUES (9900000011, 'A3D01', '原料货架区', 9900000001, 24.00, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'RACK', 0.00, 0.00, 0.00, 6.00, 4.00, 3.00, 0.00, 0.00, 0.00, '#409eff', 0.12, 'CUBE', NULL, 1, 1, 1, NULL);
INSERT INTO `wm_storage_area` VALUES (9900000012, 'B3D02', '成品网格区', 9900000001, 24.00, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'GRID', 8.00, 0.00, 0.00, 6.00, 4.00, 3.00, 0.00, 0.00, 0.00, '#67c23a', 0.12, 'CUBE', NULL, 1, 1, 1, NULL);

-- ----------------------------
-- Table structure for wm_storage_location
-- ----------------------------
DROP TABLE IF EXISTS `wm_storage_location`;
CREATE TABLE `wm_storage_location`  (
  `location_id` bigint(20) NOT NULL COMMENT '库位ID',
  `location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位编码',
  `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位名称',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '仓库ID',
  `area_id` bigint(20) NOT NULL COMMENT '库区ID',
  `category_id` bigint(20) NULL DEFAULT NULL COMMENT '绑定的物料类别ID',
  `area` double(8, 2) NULL DEFAULT NULL COMMENT '面积',
  `max_loa` double(8, 2) NULL DEFAULT NULL COMMENT '最大载重量',
  `position_x` int(11) NULL DEFAULT NULL COMMENT '库位位置X',
  `position_y` int(11) NULL DEFAULT NULL COMMENT '库位位置y',
  `position_z` int(11) NULL DEFAULT NULL COMMENT '库位位置z',
  `product_mixing` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否允许产品混放',
  `batch_mixing` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否允许批次混放',
  `enable_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `frozen_flag` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否冻结',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT 0 COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `location_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'STORAGE' COMMENT '库位类型: STORAGE-存储, PICKING-拣货, TRANSIT-中转',
  `rack_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属货架ID',
  `layer` int(11) NULL DEFAULT 1 COMMENT '层数(从下往上)',
  `col` int(11) NULL DEFAULT 1 COMMENT '列数(从左往右)',
  `row_num` int(11) NULL DEFAULT 1 COMMENT '行数(从前到后)',
  `pos_x` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标X(米)',
  `pos_y` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Y(米)',
  `pos_z` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '3D坐标Z(米)',
  `width` decimal(8, 2) NULL DEFAULT 1.00 COMMENT '库位宽度(米)',
  `height` decimal(8, 2) NULL DEFAULT 1.00 COMMENT '库位高度(米)',
  `location_depth` decimal(8, 2) NULL DEFAULT 1.00 COMMENT '库位深度(米)',
  `rotation_x` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'X轴旋转角度',
  `rotation_y` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Y轴旋转角度',
  `rotation_z` decimal(5, 2) NULL DEFAULT 0.00 COMMENT 'Z轴旋转角度',
  `color_empty` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#4CAF50' COMMENT '空置时颜色',
  `color_occupied` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#2196F3' COMMENT '占用时颜色',
  `color_locked` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#F44336' COMMENT '锁定时颜色',
  `color_warning` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#FF9800' COMMENT '预警时颜色',
  `opacity` decimal(3, 2) NULL DEFAULT 0.85 COMMENT '透明度',
  `show_border` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示边框',
  `border_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '#000000' COMMENT '边框颜色',
  `border_width` decimal(3, 2) NULL DEFAULT 0.02 COMMENT '边框宽度',
  `show_label` tinyint(4) NULL DEFAULT 1 COMMENT '是否显示标签',
  `label_position` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'TOP' COMMENT '标签位置: TOP-顶部, FRONT-前面',
  `label_size` decimal(3, 2) NULL DEFAULT 0.30 COMMENT '标签大小',
  `status` int(11) NULL DEFAULT 0 COMMENT '库位状态: 0-空置, 1-占用, 2-锁定, 3-预警',
  `occupancy_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '占用率(0-100)',
  `last_inbound_time` datetime NULL DEFAULT NULL COMMENT '最后入库时间',
  `last_outbound_time` datetime NULL DEFAULT NULL COMMENT '最后出库时间',
  `config_json` json NULL COMMENT '3D配置JSON数据',
  PRIMARY KEY (`location_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库位表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_storage_location
-- ----------------------------
INSERT INTO `wm_storage_location` VALUES (1, 'L001', '自行车库位', NULL, 1, NULL, 5.00, 50.00, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-02 15:55:20', 1, '2025-12-03 17:20:40', 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (2, 'L006', '车轮库位', NULL, 2, NULL, 5.00, 200.00, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:25:23', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (3, 'L007', '车架库位', NULL, 2, NULL, 3.00, 100.00, NULL, NULL, NULL, 0, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:26:17', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (4, 'L008', '车把库位', NULL, 2, NULL, 4.00, 200.00, NULL, NULL, NULL, 1, 0, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:26:45', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (5, 'L009', '废品库位', NULL, 3, NULL, 2.00, 100.00, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:27:19', 1, '2026-06-08 23:57:22', 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (6, 'L010', '钢板库位', NULL, 4, NULL, 10.00, 1000.00, NULL, NULL, NULL, 0, 0, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:31:41', 1, '2026-06-08 23:40:27', 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (7, 'L011', '轮圈库位', NULL, 4, NULL, 12.00, NULL, NULL, NULL, NULL, 0, 0, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:32:07', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (8, 'L012', '轮胎库位', NULL, 4, NULL, 10.00, 1000.00, NULL, NULL, NULL, 0, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:32:38', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9, 'L013', '辐条库位', NULL, 4, NULL, 15.00, 1000.00, NULL, NULL, NULL, 0, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:33:02', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (10, 'L014', '把管库位', NULL, 4, NULL, 10.00, 100.00, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:33:43', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (11, 'L015', '把套库位', NULL, 4, NULL, 10.00, NULL, NULL, NULL, NULL, 0, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-03 17:34:04', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (12, 'L016', '润滑油', NULL, 5, NULL, 1.00, 100.00, NULL, NULL, NULL, 0, 0, 1, 0, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2025-12-16 10:14:33', 1, NULL, 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (13, 'L017', '电机库位', 3, 6, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 1, '存放电机半成品', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (14, 'L018', '齿轮库位', 3, 6, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 1, '存放齿轮半成品', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (15, 'L019', '减速机库位', 3, 6, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 1, '存放减速机半成品', NULL, NULL, 0, 0, '000000', 100, '0', 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000101, 'A-L1C1', 'A区1层1列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 1, 1, 1, -1.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000102, 'A-L1C2', 'A区1层2列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 1, 2, 1, -0.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000103, 'A-L1C3', 'A区1层3列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 1, 3, 1, 0.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000104, 'A-L1C4', 'A区1层4列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 1, 4, 1, 1.50, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000105, 'A-L2C1', 'A区2层1列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 2, 1, 1, -1.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000106, 'A-L2C2', 'A区2层2列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 2, 2, 1, -0.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000107, 'A-L2C3', 'A区2层3列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 2, 3, 1, 0.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000108, 'A-L2C4', 'A区2层4列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 2, 4, 1, 1.50, 1.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000109, 'A-L3C1', 'A区3层1列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 3, 1, 1, -1.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000110, 'A-L3C2', 'A区3层2列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 3, 2, 1, -0.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000111, 'A-L3C3', 'A区3层3列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 3, 3, 1, 0.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000112, 'A-L3C4', 'A区3层4列', 9900000001, 9900000011, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', 'RACK-A', 3, 4, 1, 1.50, 2.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#409eff', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000201, 'B-R1C1', 'B区1行1列', 9900000001, 9900000012, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', NULL, 1, 1, 1, 7.00, 0.00, -0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000202, 'B-R1C2', 'B区1行2列', 9900000001, 9900000012, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', NULL, 1, 2, 1, 8.00, 0.00, -0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000203, 'B-R1C3', 'B区1行3列', 9900000001, 9900000012, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', NULL, 1, 3, 1, 9.00, 0.00, -0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000204, 'B-R2C1', 'B区2行1列', 9900000001, 9900000012, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', NULL, 1, 1, 2, 7.00, 0.00, 0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000205, 'B-R2C2', 'B区2行2列', 9900000001, 9900000012, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', NULL, 1, 2, 2, 8.00, 0.00, 0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (9900000206, 'B-R2C3', 'B区2行3列', 9900000001, 9900000012, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0, '', NULL, NULL, 0, 0, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', 'STORAGE', NULL, 1, 3, 2, 9.00, 0.00, 0.50, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#909399', '#67c23a', '#f56c6c', '#e6a23c', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);
INSERT INTO `wm_storage_location` VALUES (2074149487256965121, 'L002', '连接件包', 3, 6, NULL, 10.00, NULL, NULL, NULL, NULL, 1, 1, 1, 1, '', NULL, NULL, 0, 0, '000000', 103, '0', 1, '2026-07-06 23:12:28', 1, '2026-07-06 23:12:28', 'STORAGE', NULL, 1, 1, 1, 0.00, 0.00, 0.00, 1.00, 1.00, 1.00, 0.00, 0.00, 0.00, '#4CAF50', '#2196F3', '#F44336', '#FF9800', 0.85, 1, '#000000', 0.02, 1, 'TOP', 0.30, 0, 0.00, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for wm_transfer
-- ----------------------------
DROP TABLE IF EXISTS `wm_transfer`;
CREATE TABLE `wm_transfer`  (
  `transfer_id` bigint(20) NOT NULL COMMENT '调拨单ID',
  `transfer_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调拨单号',
  `from_warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '来源仓库ID',
  `from_warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源仓库编码',
  `from_warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源仓库名称',
  `from_area_id` bigint(20) NULL DEFAULT NULL COMMENT '来源库区ID',
  `from_area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源库区编码',
  `from_area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源库区名称',
  `to_warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '目标仓库ID',
  `to_warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标仓库编码',
  `to_warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标仓库名称',
  `to_area_id` bigint(20) NULL DEFAULT NULL COMMENT '目标库区ID',
  `to_area_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标库区编码',
  `to_area_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标库区名称',
  `transfer_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '调拨类型',
  `total_quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '总数量',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人',
  `transfer_date` datetime NULL DEFAULT NULL COMMENT '调拨日期',
  `execute_date` datetime NULL DEFAULT NULL COMMENT '执行日期',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '已选审批流ID(wf_custom_form.id)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`transfer_id`) USING BTREE,
  INDEX `idx_transfer_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存调拨单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_transfer
-- ----------------------------
INSERT INTO `wm_transfer` VALUES (10001, 'TR-TEST-001', 2, 'WH004', '原料仓库', 4, 'A0005', '金属库区', 3, 'WH005', '半成品仓库', 6, 'A0007', '加工区', 'INTERNAL', 50.0000, 'DRAFT', 1, '管理员', '2026-06-05 00:00:00', NULL, NULL, '钢板50吨调拨到半成品仓库供生产使用', '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14');

-- ----------------------------
-- Table structure for wm_transfer_line
-- ----------------------------
DROP TABLE IF EXISTS `wm_transfer_line`;
CREATE TABLE `wm_transfer_line`  (
  `line_id` bigint(20) NOT NULL COMMENT '行ID',
  `transfer_id` bigint(20) NOT NULL COMMENT '调拨单ID',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  `material_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料规格',
  `base_uom_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位ID',
  `base_uom_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位名称',
  `batch_id` bigint(20) NULL DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次编码',
  `from_location_id` bigint(20) NULL DEFAULT NULL COMMENT '来源库位ID',
  `from_location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源库位编码',
  `from_location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源库位名称',
  `to_location_id` bigint(20) NULL DEFAULT NULL COMMENT '目标库位ID',
  `to_location_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标库位编码',
  `to_location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标库位名称',
  `quantity` decimal(18, 4) NULL DEFAULT NULL COMMENT '调拨数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_transfer_line_mid`(`transfer_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存调拨行表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_transfer_line
-- ----------------------------
INSERT INTO `wm_transfer_line` VALUES (10001, 10001, 1, 'IF20251016004', '钢板', NULL, '3', '吨', NULL, NULL, 6, 'L010', '钢板库位', 15, 'L019', '减速机库位', 50.0000, '钢板调拨', '000000', 100, '0', 1, '2026-06-21 20:59:14', 1, '2026-06-21 20:59:14');

-- ----------------------------
-- Table structure for wm_warehouse
-- ----------------------------
DROP TABLE IF EXISTS `wm_warehouse`;
CREATE TABLE `wm_warehouse`  (
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库名称',
  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '位置',
  `area` double(12, 2) NULL DEFAULT NULL COMMENT '面积',
  `manager` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人',
  `manager_id` bigint(20) NULL DEFAULT NULL COMMENT 'user_id',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `status` int(11) NULL DEFAULT NULL COMMENT '仓库状态',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(11) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(11) NULL DEFAULT NULL COMMENT '预留字段4',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `province_area_id` bigint(20) NULL DEFAULT NULL COMMENT '省份行政区划ID',
  `city_area_id` bigint(20) NULL DEFAULT NULL COMMENT '城市行政区划ID',
  `district_area_id` bigint(20) NULL DEFAULT NULL COMMENT '区县行政区划ID',
  `street_id` bigint(20) NULL DEFAULT NULL COMMENT '街道行政区划id',
  `address_lat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址纬度',
  `address_lng` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址经度',
  `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '归属部门ID（关联sys_dept）',
  `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '归属部门名称',
  PRIMARY KEY (`warehouse_id`) USING BTREE,
  UNIQUE INDEX `uk_warehouse_code`(`warehouse_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '仓库表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of wm_warehouse
-- ----------------------------
INSERT INTO `wm_warehouse` VALUES (1, 'WH003', '成品仓库', '浙江省杭州市上城区小营街道', 400.30, 'ry', 2, NULL, 1, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, '2025-09-30 12:04:16', 1, '2025-12-03 17:06:02', 330000, 330100, 330102, 330102004, NULL, NULL, NULL, NULL);
INSERT INTO `wm_warehouse` VALUES (2, 'WH004', '原料仓库', '浙江省杭州市上城区清波街道', 300.00, 'admin', 1, '成品仓库', 1, NULL, NULL, NULL, NULL, '000000', 103, '0', 1, '2025-12-02 10:55:51', 1, '2025-12-03 17:19:14', 330000, 330100, 330102, 330102001, NULL, NULL, NULL, NULL);
INSERT INTO `wm_warehouse` VALUES (3, 'WH005', '半成品仓库', '厂区东侧B栋', 2000.00, '王五', NULL, '存放半成品及在制品', 1, NULL, NULL, NULL, NULL, '000000', 100, '0', 1, '2026-06-21 20:52:46', 1, '2026-06-21 20:52:46', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `wm_warehouse` VALUES (9900000001, 'WH-3D-DEMO', '3D演示仓库', '厂区3D可视化演示仓', 500.00, 'admin', NULL, NULL, 1, NULL, NULL, NULL, NULL, '000000', NULL, '0', 1, '2026-06-30 17:06:21', 1, '2026-06-30 17:06:21', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

SET FOREIGN_KEY_CHECKS = 1;
