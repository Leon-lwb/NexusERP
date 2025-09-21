/*
 Navicat Premium Data Transfer

 Source Server         : mysql8
 Source Server Type    : MySQL
 Source Server Version : 80039
 Source Host           : localhost:3307
 Source Schema         : nexuserp

 Target Server Type    : MySQL
 Target Server Version : 80039
 File Encoding         : 65001

 Date: 21/09/2025 10:49:36
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for bd_client
-- ----------------------------
DROP TABLE IF EXISTS `bd_client`;
CREATE TABLE `bd_client`  (
  `client_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户名称',
  `client_nick` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户简写',
  `client_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户英文名称',
  `client_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户简介',
  `client_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'ENTERPRISE' COMMENT '客户类型: ENTERPRISE企业；PERSON个人',
  `client_logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户LOGO地址',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户默认送货地点',
  `web_site` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户官网地址',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户邮箱',
  `tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户电话',
  `contact1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人1',
  `contact1_tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人1电话',
  `contact1_emal` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人1邮箱',
  `contact2` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人2',
  `contact2_tel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人2电话',
  `contact2_emal` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人2邮箱',
  `credit_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '统一社会信用代码',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(0) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(0) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`client_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of bd_client
-- ----------------------------
INSERT INTO `bd_client` VALUES (1, 'cs123', '测试', '测试', 'cs', '测试测试', 'ENTERPRISE', NULL, '123', NULL, '123', '123', '123', '123', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-09-18 15:45:44', NULL, '2025-09-19 18:00:33');

-- ----------------------------
-- Table structure for bd_client_credit
-- ----------------------------
DROP TABLE IF EXISTS `bd_client_credit`;
CREATE TABLE `bd_client_credit`  (
  `credit_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '客户信用ID',
  `client_id` bigint(0) NOT NULL COMMENT '客户ID',
  `client_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户编码',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户名称',
  `client_en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户英文名称',
  `client_credit` int(0) NULL DEFAULT NULL COMMENT '信用额度',
  `client_time` int(0) NULL DEFAULT NULL COMMENT '账期',
  `client_level` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL' COMMENT '供应商等级: A优质客户(80-100);B正常(60-80);C重点关注(50-60);D劣质客户(40-50);E黑名单(0-40)',
  `client_score` int(0) NOT NULL DEFAULT 60 COMMENT '客户评分',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(0) NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` int(0) NULL DEFAULT NULL COMMENT '预留字段4',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`credit_id`) USING BTREE,
  INDEX `idx_client_id`(`client_id`) USING BTREE,
  INDEX `idx_client_code`(`client_code`) USING BTREE,
  CONSTRAINT `bd_client_credit_bd_client__fk` FOREIGN KEY (`client_id`) REFERENCES `bd_client` (`client_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户信用表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table`  (
  `table_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '代码生成业务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of gen_table
-- ----------------------------
INSERT INTO `gen_table` VALUES (4, 'bd_client', '客户表', NULL, NULL, 'BdClient', 'crud', 'element-plus', 'com.nexus.core.bd', 'core', 'bd', '客户信息', 'nexus', '0', '/', '{\"parentMenuId\":2007}', 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19', NULL);
INSERT INTO `gen_table` VALUES (5, 'bd_client_credit', '客户信用表', NULL, NULL, 'BdClientCredit', 'crud', '', 'com.nexus.core', 'core', 'credit', '客户信用', 'nexus', '0', '/', NULL, 'admin', '2025-09-18 15:01:18', '', NULL, NULL);
INSERT INTO `gen_table` VALUES (6, 'sys_auto_code_part', '编码生成规则组成表', NULL, NULL, 'SysAutoCodePart', 'crud', 'element-plus', 'com.nexus.system', 'system', 'system', '编码规则', 'nexus', '0', '/', '{\"parentMenuId\":1}', 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04', NULL);
INSERT INTO `gen_table` VALUES (7, 'sys_auto_code_result', '编码生成记录表', NULL, NULL, 'SysAutoCodeResult', 'crud', 'element-plus', 'com.nexus.system', 'system', 'system', '编码生成记录', 'nexus', '0', '/', '{\"parentMenuId\":1}', 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43', NULL);
INSERT INTO `gen_table` VALUES (8, 'sys_auto_code_rule', '编码生成规则表', NULL, NULL, 'SysAutoCodeRule', 'crud', 'element-plus', 'com.nexus.system', 'system', 'system', '编码生成规则', 'nexus', '0', '/', '{\"parentMenuId\":1}', 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25', NULL);

-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column`  (
  `column_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint(0) NULL DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典类型',
  `sort` int(0) NULL DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 168 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '代码生成业务表字段' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of gen_table_column
-- ----------------------------
INSERT INTO `gen_table_column` VALUES (66, 4, 'client_id', '客户ID', 'bigint', 'Long', 'clientId', '1', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 1, 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (67, 4, 'client_code', '客户编码', 'varchar(64)', 'String', 'clientCode', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 2, 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (68, 4, 'client_name', '客户名称', 'varchar(255)', 'String', 'clientName', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', 3, 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (69, 4, 'client_nick', '客户简写', 'varchar(255)', 'String', 'clientNick', '0', '0', '0', '1', '1', '1', '0', 'EQ', 'input', '', 4, 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (70, 4, 'client_en', '客户英文名称', 'varchar(255)', 'String', 'clientEn', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 5, 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (71, 4, 'client_desc', '客户简介', 'varchar(500)', 'String', 'clientDesc', '0', '0', '1', '1', '1', '1', '0', 'EQ', 'textarea', '', 6, 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (72, 4, 'client_type', '客户类型', 'varchar(64)', 'String', 'clientType', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'select', 'bd_client_type', 7, 'admin', '2025-09-18 15:01:17', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (73, 4, 'client_logo', '客户LOGO', 'varchar(255)', 'String', 'clientLogo', '0', '0', '0', '1', '1', '1', '0', 'EQ', 'input', '', 8, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (74, 4, 'address', '客户默认送货地点', 'varchar(500)', 'String', 'address', '0', '0', '1', '1', '1', '0', '0', 'EQ', 'textarea', '', 9, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (75, 4, 'web_site', '客户官网地址', 'varchar(255)', 'String', 'webSite', '0', '0', '0', '1', '1', '0', '0', 'EQ', 'input', '', 10, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (76, 4, 'email', '客户邮箱', 'varchar(255)', 'String', 'email', '0', '0', '1', '1', '1', '0', '0', 'EQ', 'input', '', 11, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (77, 4, 'tel', '客户电话', 'varchar(64)', 'String', 'tel', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 12, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (78, 4, 'contact1', '联系人1', 'varchar(64)', 'String', 'contact1', '0', '0', '1', '1', '1', '0', '0', 'EQ', 'input', '', 13, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (79, 4, 'contact1_tel', '联系人1电话', 'varchar(64)', 'String', 'contact1Tel', '0', '0', '1', '1', '1', '0', '0', 'EQ', 'input', '', 14, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (80, 4, 'contact1_emal', '联系人1邮箱', 'varchar(255)', 'String', 'contact1Emal', '0', '0', '0', '1', '1', '0', '0', 'EQ', 'input', '', 15, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (81, 4, 'contact2', '联系人2', 'varchar(64)', 'String', 'contact2', '0', '0', '0', '1', '1', '0', '0', 'EQ', 'input', '', 16, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (82, 4, 'contact2_tel', '联系人2电话', 'varchar(64)', 'String', 'contact2Tel', '0', '0', '0', '1', '1', '0', '0', 'EQ', 'input', '', 17, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (83, 4, 'contact2_emal', '联系人2邮箱', 'varchar(255)', 'String', 'contact2Emal', '0', '0', '0', '1', '1', '0', '0', 'EQ', 'input', '', 18, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (84, 4, 'credit_code', '统一社会信用代码', 'varchar(64)', 'String', 'creditCode', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 19, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (85, 4, 'remark', '备注', 'varchar(500)', 'String', 'remark', '0', '0', '0', '1', '1', '0', NULL, 'EQ', 'textarea', '', 20, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (86, 4, 'attr1', '预留字段1', 'varchar(64)', 'String', 'attr1', '0', '0', '0', '0', '0', '0', '0', 'EQ', 'input', '', 21, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (87, 4, 'attr2', '预留字段2', 'varchar(255)', 'String', 'attr2', '0', '0', '0', '0', '0', '0', '0', 'EQ', 'input', '', 22, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (88, 4, 'attr3', '预留字段3', 'int', 'Long', 'attr3', '0', '0', '0', '0', '0', '0', '0', 'EQ', 'input', '', 23, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (89, 4, 'attr4', '预留字段4', 'int', 'Long', 'attr4', '0', '0', '0', '0', '0', '0', '0', 'EQ', 'input', '', 24, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (90, 4, 'create_by', '创建人', 'varchar(64)', 'String', 'createBy', '0', '0', '0', '0', NULL, NULL, NULL, 'EQ', 'input', '', 25, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (91, 4, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '0', NULL, NULL, NULL, 'EQ', 'datetime', '', 26, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (92, 4, 'update_by', '更新人', 'varchar(64)', 'String', 'updateBy', '0', '0', '0', '0', '0', NULL, NULL, 'EQ', 'input', '', 27, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (93, 4, 'update_time', '更新时间', 'datetime', 'Date', 'updateTime', '0', '0', '0', '0', '0', NULL, NULL, 'EQ', 'datetime', '', 28, 'admin', '2025-09-18 15:01:18', '', '2025-09-18 15:04:19');
INSERT INTO `gen_table_column` VALUES (94, 5, 'credit_id', '客户信用ID', 'bigint', 'Long', 'creditId', '1', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 1, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (95, 5, 'client_id', '客户ID', 'bigint', 'Long', 'clientId', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 2, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (96, 5, 'client_code', '客户编码', 'varchar(64)', 'String', 'clientCode', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 3, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (97, 5, 'client_name', '客户名称', 'varchar(255)', 'String', 'clientName', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', 4, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (98, 5, 'client_en', '客户英文名称', 'varchar(255)', 'String', 'clientEn', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 5, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (99, 5, 'client_credit', '信用额度', 'int', 'Long', 'clientCredit', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 6, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (100, 5, 'client_time', '账期', 'int', 'Long', 'clientTime', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 7, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (101, 5, 'client_level', '供应商等级: A优质客户(80-100);B正常(60-80);C重点关注(50-60);D劣质客户(40-50);E黑名单(0-40)', 'varchar(64)', 'String', 'clientLevel', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 8, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (102, 5, 'client_score', '客户评分', 'int', 'Long', 'clientScore', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 9, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (103, 5, 'attr1', '预留字段1', 'varchar(64)', 'String', 'attr1', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 10, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (104, 5, 'attr2', '预留字段2', 'varchar(255)', 'String', 'attr2', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 11, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (105, 5, 'attr3', '预留字段3', 'int', 'Long', 'attr3', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 12, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (106, 5, 'attr4', '预留字段4', 'int', 'Long', 'attr4', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 13, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (107, 5, 'create_by', '创建人', 'varchar(64)', 'String', 'createBy', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 14, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (108, 5, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'datetime', '', 15, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (109, 5, 'update_by', '更新人', 'varchar(64)', 'String', 'updateBy', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'input', '', 16, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (110, 5, 'update_time', '更新时间', 'datetime', 'Date', 'updateTime', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'datetime', '', 17, 'admin', '2025-09-18 15:01:18', '', NULL);
INSERT INTO `gen_table_column` VALUES (111, 6, 'part_id', '分段ID', 'bigint', 'Long', 'partId', '1', '1', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 1, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (112, 6, 'rule_id', '规则ID', 'bigint', 'Long', 'ruleId', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 2, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (113, 6, 'part_index', '分段序号', 'int', 'Long', 'partIndex', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 3, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (114, 6, 'part_type', '分段类型，INPUTCHAR：输入字符，NOWDATE：当前日期时间，FIXCHAR：固定字符，SERIALNO：流水号', 'varchar(20)', 'String', 'partType', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', 4, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (115, 6, 'part_code', '分段编号', 'varchar(64)', 'String', 'partCode', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 5, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (116, 6, 'part_name', '分段名称', 'varchar(255)', 'String', 'partName', '0', '0', '0', '1', '1', '1', '1', 'LIKE', 'input', '', 6, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (117, 6, 'part_length', '分段长度', 'int', 'Long', 'partLength', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 7, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (118, 6, 'date_format', NULL, 'varchar(20)', 'String', 'dateFormat', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 8, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (119, 6, 'input_character', '输入字符', 'varchar(64)', 'String', 'inputCharacter', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 9, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (120, 6, 'fix_character', '固定字符', 'varchar(64)', 'String', 'fixCharacter', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 10, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (121, 6, 'seria_start_no', '流水号起始值', 'int', 'Long', 'seriaStartNo', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 11, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (122, 6, 'seria_step', '流水号步长', 'int', 'Long', 'seriaStep', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 12, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (123, 6, 'seria_now_no', '流水号当前值', 'int', 'Long', 'seriaNowNo', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 13, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (124, 6, 'cycle_flag', '流水号是否循环', 'char(1)', 'String', 'cycleFlag', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 14, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (125, 6, 'cycle_method', '循环方式，YEAR：按年，MONTH：按月，DAY：按天，HOUR：按小时，MINITE：按分钟，OTHER：按传入字符变', 'varchar(20)', 'String', 'cycleMethod', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 15, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (126, 6, 'remark', '备注', 'varchar(500)', 'String', 'remark', '0', '0', '0', '1', '1', '1', NULL, 'EQ', 'textarea', '', 16, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (127, 6, 'attr1', '预留字段1', 'varchar(64)', 'String', 'attr1', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 17, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (128, 6, 'attr2', '预留字段2', 'varchar(255)', 'String', 'attr2', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 18, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (129, 6, 'attr3', '预留字段3', 'int', 'Long', 'attr3', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 19, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (130, 6, 'attr4', '预留字段4', 'int', 'Long', 'attr4', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 20, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (131, 6, 'create_by', '创建者', 'varchar(64)', 'String', 'createBy', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 21, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (132, 6, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'datetime', '', 22, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (133, 6, 'update_by', '更新者', 'varchar(64)', 'String', 'updateBy', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'input', '', 23, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (134, 6, 'update_time', '更新时间', 'datetime', 'Date', 'updateTime', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'datetime', '', 24, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:39:04');
INSERT INTO `gen_table_column` VALUES (135, 7, 'code_id', '记录ID', 'bigint', 'Long', 'codeId', '1', '1', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 1, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (136, 7, 'rule_id', '规则ID', 'bigint', 'Long', 'ruleId', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 2, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (137, 7, 'gen_date', '生成日期时间', 'varchar(20)', 'String', 'genDate', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 3, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (138, 7, 'gen_index', '最后产生的序号', 'int', 'Long', 'genIndex', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 4, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (139, 7, 'last_result', '最后产生的值', 'varchar(64)', 'String', 'lastResult', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 5, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (140, 7, 'last_serial_no', '最后产生的流水号', 'int', 'Long', 'lastSerialNo', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 6, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (141, 7, 'last_input_char', '最后传入的参数', 'varchar(64)', 'String', 'lastInputChar', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 7, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (142, 7, 'remark', '备注', 'varchar(500)', 'String', 'remark', '0', '0', '0', '1', '1', '1', NULL, 'EQ', 'textarea', '', 8, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (143, 7, 'attr1', '预留字段1', 'varchar(64)', 'String', 'attr1', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 9, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (144, 7, 'attr2', '预留字段2', 'varchar(255)', 'String', 'attr2', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 10, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (145, 7, 'attr3', '预留字段3', 'int', 'Long', 'attr3', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 11, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (146, 7, 'attr4', '预留字段4', 'int', 'Long', 'attr4', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 12, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (147, 7, 'create_by', '创建者', 'varchar(64)', 'String', 'createBy', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 13, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (148, 7, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'datetime', '', 14, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (149, 7, 'update_by', '更新者', 'varchar(64)', 'String', 'updateBy', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'input', '', 15, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (150, 7, 'update_time', '更新时间', 'datetime', 'Date', 'updateTime', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'datetime', '', 16, 'admin', '2025-09-18 16:33:32', '', '2025-09-18 16:49:43');
INSERT INTO `gen_table_column` VALUES (151, 8, 'rule_id', '规则ID', 'bigint', 'Long', 'ruleId', '1', '1', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 1, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (152, 8, 'rule_code', '规则编码', 'varchar(64)', 'String', 'ruleCode', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 2, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (153, 8, 'rule_name', '规则名称', 'varchar(255)', 'String', 'ruleName', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', 3, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (154, 8, 'rule_desc', '描述', 'varchar(500)', 'String', 'ruleDesc', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'textarea', '', 4, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (155, 8, 'max_length', '最大长度', 'int', 'Long', 'maxLength', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 5, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (156, 8, 'is_padded', '是否补齐', 'char(1)', 'String', 'isPadded', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 6, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (157, 8, 'padded_char', '补齐字符', 'varchar(20)', 'String', 'paddedChar', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 7, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (158, 8, 'padded_method', '补齐方式', 'char(1)', 'String', 'paddedMethod', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 8, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (159, 8, 'enable_flag', '是否启用', 'char(1)', 'String', 'enableFlag', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 9, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (160, 8, 'remark', '备注', 'varchar(500)', 'String', 'remark', '0', '0', '0', '1', '1', '1', NULL, 'EQ', 'textarea', '', 10, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (161, 8, 'attr1', '预留字段1', 'varchar(64)', 'String', 'attr1', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 11, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (162, 8, 'attr2', '预留字段2', 'varchar(255)', 'String', 'attr2', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 12, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (163, 8, 'attr3', '预留字段3', 'int', 'Long', 'attr3', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 13, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (164, 8, 'attr4', '预留字段4', 'int', 'Long', 'attr4', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 14, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (165, 8, 'create_by', '创建者', 'varchar(64)', 'String', 'createBy', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 15, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (166, 8, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', NULL, NULL, NULL, 'EQ', 'datetime', '', 16, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (167, 8, 'update_by', '更新者', 'varchar(64)', 'String', 'updateBy', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'input', '', 17, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');
INSERT INTO `gen_table_column` VALUES (168, 8, 'update_time', '更新时间', 'datetime', 'Date', 'updateTime', '0', '0', '0', '1', '1', NULL, NULL, 'EQ', 'datetime', '', 18, 'admin', '2025-09-18 16:33:32', '', '2025-09-19 16:39:25');

-- ----------------------------
-- Table structure for sys_auto_code_part
-- ----------------------------
DROP TABLE IF EXISTS `sys_auto_code_part`;
CREATE TABLE `sys_auto_code_part`  (
  `part_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '分段ID',
  `rule_id` bigint(0) NOT NULL COMMENT '规则ID',
  `part_index` int(0) NOT NULL COMMENT '分段序号',
  `part_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分段类型，INPUTCHAR：输入字符，NOWDATE：当前日期时间，FIXCHAR：固定字符，SERIALNO：流水号',
  `part_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分段编号',
  `part_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分段名称',
  `part_length` int(0) NOT NULL COMMENT '分段长度',
  `date_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `input_character` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '输入字符',
  `fix_character` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '固定字符',
  `seria_start_no` int(0) NULL DEFAULT NULL COMMENT '流水号起始值',
  `seria_step` int(0) NULL DEFAULT NULL COMMENT '流水号步长',
  `seria_now_no` int(0) NULL DEFAULT NULL COMMENT '流水号当前值',
  `cycle_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '流水号是否循环',
  `cycle_method` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '循环方式，YEAR：按年，MONTH：按月，DAY：按天，HOUR：按小时，MINITE：按分钟，OTHER：按传入字符变',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(0) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(0) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`part_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 358 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '编码生成规则组成表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_auto_code_part
-- ----------------------------
INSERT INTO `sys_auto_code_part` VALUES (200, 205, 2, 'SERIALNO', 'P1', '流水号', 8, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-04-26 21:13:17', 'admin', '2022-04-26 22:47:49');
INSERT INTO `sys_auto_code_part` VALUES (201, 205, 1, 'FIXCHAR', 'P0', '前缀', 4, NULL, NULL, 'ITEM', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-04-26 22:44:03', 'admin', '2022-08-15 15:59:19');
INSERT INTO `sys_auto_code_part` VALUES (202, 206, 1, 'FIXCHAR', 'P1', '前缀', 10, NULL, NULL, 'ITEM_TYPE_', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-04-26 23:02:12', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (203, 206, 2, 'SERIALNO', 'P2', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-04-26 23:02:42', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (204, 207, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'C', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-06 21:21:04', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (205, 207, 2, 'SERIALNO', 'SERIAL', '流水号部分', 5, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-06 21:21:44', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (206, 208, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'V', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-06 22:50:38', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (207, 208, 2, 'SERIALNO', 'SERIAL', '流水号', 5, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-06 22:51:02', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (208, 209, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'WS', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-07 17:49:16', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (209, 209, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-07 17:49:40', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (210, 210, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'WH', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-07 22:00:17', 'admin', '2022-08-16 18:58:36');
INSERT INTO `sys_auto_code_part` VALUES (211, 210, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-07 22:00:40', 'admin', '2022-07-30 11:26:14');
INSERT INTO `sys_auto_code_part` VALUES (212, 211, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'L', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 14:50:29', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (213, 211, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 14:52:12', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (214, 212, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'A', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 18:38:29', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (215, 212, 2, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 18:38:51', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (216, 213, 1, 'FIXCHAR', 'PREFIX', '前缀', 7, NULL, NULL, 'M_TYPE_', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 19:46:42', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (217, 213, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 19:47:03', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (218, 214, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'M', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 21:26:59', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (219, 214, 2, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 21:27:18', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (220, 215, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'MO', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-09 11:40:23', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (222, 215, 2, 'NOWDATE', 'DATEPART', '年月日部分', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-09 11:58:57', 'admin', '2022-05-09 12:46:34');
INSERT INTO `sys_auto_code_part` VALUES (223, 215, 3, 'SERIALNO', 'SERIAL', '流水号部分', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-09 11:59:31', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (224, 216, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'WS', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-10 21:55:51', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (225, 216, 2, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-10 21:56:19', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (226, 217, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'TT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-11 00:22:02', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (227, 217, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-11 00:22:25', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (228, 218, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'T', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-11 22:07:44', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (229, 218, 2, 'SERIALNO', 'SERIAL', '流水号', 5, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-11 22:08:17', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (230, 219, 1, 'FIXCHAR', 'PREFIX', '前缀', 7, NULL, NULL, 'PROCESS', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-12 00:10:13', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (231, 219, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-12 00:10:33', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (232, 220, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'R', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-12 23:07:01', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (233, 220, 2, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1000, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-12 23:07:23', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (234, 221, 1, 'FIXCHAR', 'PREFIX', '固定前缀', 4, NULL, NULL, 'TASK', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-15 18:22:53', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (235, 221, 2, 'NOWDATE', 'YEAR', '年份', 4, 'yyyy', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-15 18:23:39', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (236, 221, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'YEAR', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-15 18:24:03', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (237, 222, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'I', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-17 21:57:46', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (238, 222, 2, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-17 21:58:05', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (239, 223, 1, 'FIXCHAR', 'PREFIX', '前缀', 3, NULL, NULL, 'QCT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-17 22:43:31', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (240, 223, 2, 'NOWDATE', 'YEAR', '年份', 4, 'yyyy', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-17 22:44:04', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (241, 223, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'YEAR', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-17 22:44:25', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (242, 224, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'DF', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-19 11:33:52', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (243, 224, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-19 11:34:11', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (244, 225, 1, 'FIXCHAR', 'PREFIX', '前缀', 3, NULL, NULL, 'IQC', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-19 16:29:59', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (245, 225, 2, 'NOWDATE', 'DATE', '年月日', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-19 16:30:28', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (246, 225, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-19 16:31:00', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (247, 226, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'R', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-22 20:51:47', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (248, 226, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-05-22 20:52:10', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (249, 226, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-22 20:52:58', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (250, 227, 1, 'FIXCHAR', 'PREFIX', '固定前缀', 1, NULL, NULL, 'T', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-06 19:54:45', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (251, 227, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-06 19:55:06', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (252, 228, 1, 'FIXCHAR', 'PREFIX', '前缀', 4, NULL, NULL, 'PLAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-06 22:08:39', 'admin', '2022-07-31 16:42:59');
INSERT INTO `sys_auto_code_part` VALUES (253, 228, 2, 'NOWDATE', 'YEAR', '年', 4, 'yyyy', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-06 22:08:59', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (254, 228, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'YEAR', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-06 22:09:24', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (255, 229, 1, 'FIXCHAR', 'PREFIX', '前缀', 3, NULL, NULL, 'RTV', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-13 16:06:14', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (256, 229, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-13 16:06:42', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (257, 229, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-13 16:07:10', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (258, 230, 1, 'FIXCHAR', 'PREFIX', '固定前缀', 3, NULL, NULL, 'SUB', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-16 20:28:22', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (259, 230, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-16 20:28:44', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (260, 231, 1, 'FIXCHAR', 'PREFIX', '前缀', 4, NULL, NULL, 'PLAN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-16 21:50:22', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (261, 231, 2, 'NOWDATE', 'YEAR', '年份', 4, 'yyyy', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-06-16 21:50:43', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (262, 231, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'YEAR', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-16 21:51:07', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (263, 232, 1, 'FIXCHAR', '1', '1', 3, NULL, NULL, 'BAT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-07-14 12:02:54', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (265, 233, 1, 'FIXCHAR', 'PREFIX', '前缀', 5, NULL, NULL, 'ISSUE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-07-17 19:32:46', 'admin', '2022-07-17 19:35:44');
INSERT INTO `sys_auto_code_part` VALUES (266, 233, 2, 'NOWDATE', 'DATE', '年月日', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-07-17 19:33:22', 'admin', '2022-07-17 19:35:57');
INSERT INTO `sys_auto_code_part` VALUES (267, 233, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-07-17 19:33:45', 'admin', '2022-07-17 19:36:05');
INSERT INTO `sys_auto_code_part` VALUES (271, 234, 1, 'INPUTCHAR', 'PREFIX', '1', 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-07-30 14:20:49', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (275, 236, 1, 'FIXCHAR', '前缀', '固定字符', 2, NULL, NULL, 'IF', NULL, 2, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-08-19 10:48:20', 'admin', '2022-08-19 14:13:30');
INSERT INTO `sys_auto_code_part` VALUES (276, 236, 2, 'NOWDATE', '后缀', '固定字段', 8, 'yyyyMMdd', NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-08-19 14:12:19', 'admin', '2022-10-22 20:41:54');
INSERT INTO `sys_auto_code_part` VALUES (277, 237, 1, 'FIXCHAR', 'PREFIX', '前缀', 4, NULL, NULL, 'IPQC', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-08-29 22:07:43', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (278, 237, 2, 'NOWDATE', 'DATE', '年月日', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-08-29 22:08:18', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (279, 237, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-08-29 22:08:46', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (280, 238, 1, 'FIXCHAR', 'PREFIX', '前缀', 3, NULL, NULL, 'OQC', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-01 20:30:53', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (281, 238, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-01 20:32:11', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (282, 238, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-01 20:32:38', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (283, 239, 1, 'FIXCHAR', '001', '前缀', 5, NULL, NULL, 'PBACK', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-03 23:49:07', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (284, 239, 2, 'NOWDATE', '002', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-03 23:49:31', 'admin', '2022-09-03 23:49:44');
INSERT INTO `sys_auto_code_part` VALUES (285, 239, 3, 'SERIALNO', '003', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-03 23:50:10', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (286, 240, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'RT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-15 23:19:25', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (287, 240, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-15 23:19:47', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (288, 240, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-15 23:20:09', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (289, 241, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'PR', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-23 10:58:17', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (290, 241, 2, 'NOWDATE', 'DATE', '年月日', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-23 10:58:44', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (291, 241, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-23 10:59:06', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (292, 242, 1, 'FIXCHAR', 'PREFIX', '前缀', 3, NULL, NULL, 'REP', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-28 22:01:19', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (293, 242, 2, 'NOWDATE', 'DATE', '日期', 4, 'yyyy', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-09-28 22:01:39', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (294, 242, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'YEAR', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-28 22:02:00', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (295, 243, 1, 'FIXCHAR', 'PERFIX', '前缀', 2, NULL, NULL, 'PS', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-10-05 19:46:02', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (296, 243, 2, 'NOWDATE', 'DATA', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-10-05 19:46:24', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (297, 243, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-10-05 19:46:48', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (298, 244, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'RS', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-10-06 21:40:42', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (299, 244, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-10-06 21:41:03', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (300, 244, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-10-06 21:41:22', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (301, 245, 1, 'FIXCHAR', 'PREFIX', '前缀', 4, NULL, NULL, 'PACK', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-10-11 01:22:38', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (302, 245, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-10-11 01:23:09', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (303, 245, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-10-11 01:23:35', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (304, 236, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-10-22 20:43:08', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (305, 246, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'TR', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-11-30 21:58:57', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (306, 246, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-11-30 21:59:19', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (307, 246, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-11-30 21:59:39', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (308, 247, 1, 'FIXCHAR', 'PREFIX', '前缀', 3, NULL, NULL, 'SN-', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-12-09 11:22:36', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (309, 247, 2, 'INPUTCHAR', 'INPUT', '产品编码', 12, NULL, 'ITEM_CODE', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-12-09 11:23:36', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (310, 247, 3, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2022-12-09 11:24:07', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (311, 247, 4, 'SERIALNO', 'SERIAL', '流水号', 6, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2022-12-09 11:25:00', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (312, 248, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'FB', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2023-10-17 11:09:49', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (313, 248, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2023-10-17 11:10:11', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (314, 248, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2023-10-17 11:10:38', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (315, 249, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'OI', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 14:24:59', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (316, 249, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 14:25:17', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (317, 249, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 14:25:40', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (318, 250, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'OR', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 20:17:25', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (319, 250, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 20:17:45', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (320, 250, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 20:18:18', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (321, 251, 1, 'FIXCHAR', 'PRFIX', '前缀', 1, NULL, NULL, 'T', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-05-20 11:59:59', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (322, 251, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-05-20 12:00:27', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (323, 252, 1, 'FIXCHAR', 'PRIFIX', '前缀', 1, NULL, NULL, 'P', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-12-03 19:00:18', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (324, 252, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-12-03 19:01:03', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (325, 252, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2024-12-03 19:01:33', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (326, 253, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'C', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-12-05 14:07:10', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (327, 253, 2, 'SERIALNO', 'SERIAL', '流水号', 10, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-12-05 14:07:40', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (328, 254, 1, 'FIXCHAR', 'PREFIX', '前缀', 7, NULL, NULL, 'PRINTER', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-12-05 14:34:40', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (329, 254, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-12-05 14:35:02', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (330, 255, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'AN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2024-12-09 21:16:21', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (331, 255, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, 'admin', '2024-12-09 21:16:53', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (332, 255, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2024-12-09 21:17:16', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (333, 256, 1, 'FIXCHAR', 'PREFIX', '前缀', 4, NULL, NULL, 'DEPT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-02-24 11:34:17', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (334, 256, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-02-24 11:34:35', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (335, 232, 2, 'SERIALNO', 'SERIAL', '流水号', 8, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-02-28 17:05:46', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (336, 257, 1, 'FIXCHAR', 'PRIFIX', '前缀', 3, NULL, NULL, 'RQC', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-03-06 17:20:40', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (337, 257, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-03-06 17:21:07', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (338, 257, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2025-03-06 17:21:27', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (339, 258, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'SN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-03-14 09:32:54', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (340, 258, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-03-14 09:33:13', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (341, 258, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2025-03-14 09:33:33', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (342, 259, 1, 'FIXCHAR', 'PRIFIX', '前缀', 3, NULL, NULL, 'STP', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-03-31 21:39:08', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (343, 259, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-03-31 21:39:28', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (344, 260, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'ST', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-04-01 22:58:19', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (345, 260, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-04-01 22:58:41', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (346, 260, 3, 'SERIALNO', 'SERIAL', '流水号', 2, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2025-04-01 22:59:00', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (347, 261, 1, 'FIXCHAR', 'PREFIX', '前缀', 6, NULL, NULL, 'CLIENT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-04-03 10:50:44', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (348, 261, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2025-04-03 10:51:17', 'admin', '2025-04-03 10:51:54');
INSERT INTO `sys_auto_code_part` VALUES (349, 262, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'C', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-05-12 12:13:04', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (350, 262, 2, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-05-12 12:13:28', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (351, 263, 1, 'FIXCHAR', 'PREFIX', '前缀', 5, NULL, NULL, 'MISCR', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-05-15 11:07:27', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (352, 263, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-05-15 11:07:48', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (353, 263, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2025-05-15 11:08:22', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (354, 264, 1, 'FIXCHAR', 'PREFIX', '前缀', 5, NULL, NULL, 'MISCI', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-05-16 09:50:08', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (355, 264, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-05-16 09:50:26', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (356, 264, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', NULL, NULL, NULL, 0, 0, 'admin', '2025-05-16 09:50:48', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (357, 265, 1, 'FIXCHAR', 'PREFIX', '前缀', 1, NULL, NULL, 'B', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-06-20 10:08:15', '', NULL);
INSERT INTO `sys_auto_code_part` VALUES (358, 265, 2, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'N', NULL, NULL, NULL, NULL, 0, 0, 'admin', '2025-06-20 10:08:37', 'admin', '2025-06-20 10:14:09');

-- ----------------------------
-- Table structure for sys_auto_code_result
-- ----------------------------
DROP TABLE IF EXISTS `sys_auto_code_result`;
CREATE TABLE `sys_auto_code_result`  (
  `code_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `rule_id` bigint(0) NOT NULL COMMENT '规则ID',
  `gen_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '生成日期时间',
  `gen_index` int(0) NULL DEFAULT NULL COMMENT '最后产生的序号',
  `last_result` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最后产生的值',
  `last_serial_no` int(0) NULL DEFAULT NULL COMMENT '最后产生的流水号',
  `last_input_char` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最后传入的参数',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(0) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(0) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`code_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 657 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '编码生成记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_auto_code_result
-- ----------------------------
INSERT INTO `sys_auto_code_result` VALUES (200, 205, '20220808095803', 76, 'ITEM00000076', 76, NULL, '', NULL, NULL, 0, 0, '', '2022-04-26 22:28:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (201, 206, '20220813231829', 62, 'ITEM_TYPE_0062', 62, NULL, '', NULL, NULL, 0, 0, '', '2022-04-27 10:17:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (202, 207, '20220802144206', 10, 'C00010', 10, NULL, '', NULL, NULL, 0, 0, '', '2022-05-06 21:22:23', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (203, 208, '20220805160743', 10, 'V00010', 10, NULL, '', NULL, NULL, 0, 0, '', '2022-05-06 22:52:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (204, 209, '20220805111938', 12, 'WS012', 12, NULL, '', NULL, NULL, 0, 0, '', '2022-05-07 17:54:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (205, 210, '20220729090506', 2, 'WH002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-05-07 22:01:31', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (206, 211, '20220729085518', 10, 'L010', 10, NULL, '', NULL, NULL, 0, 0, '', '2022-05-08 14:52:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (207, 212, '20220810082205', 11, 'A0011', 11, NULL, '', NULL, NULL, 0, 0, '', '2022-05-08 18:39:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (208, 213, '20220823091541', 24, 'M_TYPE_024', 24, NULL, '', NULL, NULL, 0, 0, '', '2022-05-08 19:47:31', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (209, 214, '20220819091251', 26, 'M0026', 26, NULL, '', NULL, NULL, 0, 0, '', '2022-05-08 21:38:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (210, 215, '20220509234547', 21, 'MO202205090021', 21, NULL, '', NULL, NULL, 0, 0, '', '2022-05-09 12:46:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (212, 216, '20220818103207', 34, 'WS0034', 34, NULL, '', NULL, NULL, 0, 0, '', '2022-05-10 21:59:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (213, 217, '20220816195823', 25, 'TT025', 25, NULL, '', NULL, NULL, 0, 0, '', '2022-05-11 00:22:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (214, 218, '20220804162652', 10, 'T00010', 10, NULL, '', NULL, NULL, 0, 0, '', '2022-05-11 22:35:05', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (215, 219, '20220729122036', 46, 'PROCESS046', 46, NULL, '', NULL, NULL, 0, 0, '', '2022-05-12 00:10:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (217, 220, '20220809171633', 29, 'R1028', 1028, NULL, '', NULL, NULL, 0, 0, '', '2022-05-12 23:11:24', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (219, 215, '20220514155158', 26, 'MO202205140026', 26, NULL, '', NULL, NULL, 0, 0, '', '2022-05-14 15:24:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (220, 215, '20220515134342', 5, 'MO202205150005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-05-15 13:41:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (221, 221, '20221117213919', 107, 'TASK20220107', 107, NULL, '', NULL, NULL, 0, 0, '', '2022-05-15 18:29:41', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (222, 222, '20220812142031', 13, 'I0013', 13, NULL, '', NULL, NULL, 0, 0, '', '2022-05-17 21:58:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (223, 223, '20221119144903', 43, 'QCT2022043', 43, NULL, '', NULL, NULL, 0, 0, '', '2022-05-17 22:44:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (224, 224, '20231120222501', 9, 'DF009', 9, NULL, '', NULL, NULL, 0, 0, '', '2022-05-19 11:37:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (225, 225, '20220519230422', 21, 'IQC20220519021', 21, NULL, '', NULL, NULL, 0, 0, '', '2022-05-19 16:49:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (226, 226, '20220522220740', 8, 'R20220522008', 8, NULL, '', NULL, NULL, 0, 0, '', '2022-05-22 20:53:25', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (227, 227, '20220814214249', 25, 'T025', 25, NULL, '', NULL, NULL, 0, 0, '', '2022-06-06 19:55:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (228, 228, '20220823150049', 121, 'PLAN2022121', 121, NULL, '', NULL, NULL, 0, 0, '', '2022-06-06 22:09:41', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (229, 229, '20220613162434', 2, 'RTV20220613002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-06-13 16:19:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (230, 226, '20220613164021', 1, 'R20220613001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-06-13 16:40:22', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (231, 229, '20220614161107', 3, 'RTV20220614003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-06-14 11:11:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (232, 226, '20220614122043', 3, 'R20220614003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-06-14 11:34:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (233, 230, '20220812221323', 4, 'SUB004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-06-16 20:29:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (234, 231, '20220901230257', 50, 'PLAN2022050', 50, NULL, '', NULL, NULL, 0, 0, '', '2022-06-16 21:51:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (235, 226, '20220717124046', 2, 'R20220717002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-07-17 12:40:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (236, 233, '20220717194751', 2, 'ISSUE20220717002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-07-17 19:36:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (237, 233, '20220725150745', 8, 'ISSUE20220725008', 8, NULL, '', NULL, NULL, 0, 0, '', '2022-07-25 11:16:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (238, 215, '20220727133912', 3, 'MO202207270003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-07-27 13:39:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (239, 229, '20220727135402', 3, 'RTV20220727003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-07-27 13:53:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (240, 215, '20220728231952', 1, 'MO202207280001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-07-28 23:19:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (241, 211, '20220729085519', 1, 'L011', 11, NULL, '', NULL, NULL, 0, 0, '', '2022-07-29 08:55:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (242, 211, '20220801170339', 6, 'L017', 17, NULL, '', NULL, NULL, 0, 0, '', '2022-07-29 08:55:20', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (243, 229, '20220729091253', 4, 'RTV20220729004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-07-29 08:56:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (244, 210, '20220729090520', 3, 'WH005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-07-29 09:05:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (245, 210, '20220801161858', 52, 'WH060', 60, NULL, '', NULL, NULL, 0, 0, '', '2022-07-29 09:05:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (246, 215, '20220729111608', 3, 'MO202207290003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-07-29 09:30:33', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (247, 219, '20220729122132', 3, 'PROCESS049', 49, NULL, '', NULL, NULL, 0, 0, '', '2022-07-29 12:21:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (248, 215, '20220730170708', 6, 'MO202207300006', 6, NULL, '', NULL, NULL, 0, 0, '', '2022-07-30 14:41:08', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (249, 226, '20220730164005', 3, 'R20220730003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-07-30 16:30:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (250, 219, '20220730170439', 3, 'PROCESS052', 52, NULL, '', NULL, NULL, 0, 0, '', '2022-07-30 17:02:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (251, 215, '20220731161554', 1, 'MO202207310001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-07-31 16:15:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (252, 226, '20220801162854', 33, 'R20220801033', 33, NULL, '', NULL, NULL, 0, 0, '', '2022-08-01 11:11:28', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (253, 215, '20220801154407', 1, 'MO202208010001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-08-01 15:44:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (254, 210, '20220801161857', 1, 'WH060', 60, NULL, '', NULL, NULL, 0, 0, '', '2022-08-01 16:18:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (255, 210, '20220801161857', 1, 'WH060', 60, NULL, '', NULL, NULL, 0, 0, '', '2022-08-01 16:18:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (256, 210, '20220809140656', 13, 'WH073', 73, NULL, '', NULL, NULL, 0, 0, '', '2022-08-01 16:26:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (257, 219, '20220801172652', 3, 'PROCESS055', 55, NULL, '', NULL, NULL, 0, 0, '', '2022-08-01 17:14:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (258, 215, '20220802174313', 13, 'MO202208020013', 13, NULL, '', NULL, NULL, 0, 0, '', '2022-08-02 09:27:31', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (259, 225, '20220802180956', 2, 'IQC20220802002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-08-02 17:54:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (260, 229, '20220802180930', 2, 'RTV20220802002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-08-02 18:01:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (261, 215, '20220803152758', 3, 'MO202208030003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-08-03 15:19:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (262, 218, '20220810105807', 7, 'T00017', 17, NULL, '', NULL, NULL, 0, 0, '', '2022-08-04 16:34:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (263, 209, '20220813133944', 14, 'WS026', 26, NULL, '', NULL, NULL, 0, 0, '', '2022-08-05 11:19:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (264, 226, '20220805131441', 4, 'R20220805004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-08-05 13:09:41', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (265, 208, '20220808161115', 4, 'V00014', 14, NULL, '', NULL, NULL, 0, 0, '', '2022-08-05 16:27:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (266, 205, '20220812160022', 12, 'ITEM00000088', 88, NULL, '', NULL, NULL, 0, 0, '', '2022-08-08 10:01:12', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (267, 229, '20220808105742', 2, 'RTV20220808002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-08-08 10:44:56', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (268, 207, '20220813112402', 14, 'C00024', 24, NULL, '', NULL, NULL, 0, 0, '', '2022-08-08 15:52:04', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (269, 208, '20220814231644', 10, 'V00024', 24, NULL, '', NULL, NULL, 0, 0, '', '2022-08-08 16:11:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (270, 219, '20220809091746', 2, 'PROCESS057', 57, NULL, '', NULL, NULL, 0, 0, '', '2022-08-09 09:17:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (271, 219, '20220813232649', 11, 'PROCESS068', 68, NULL, '', NULL, NULL, 0, 0, '', '2022-08-09 09:22:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (272, 215, '20220809173457', 7, 'MO202208090007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-08-09 12:27:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (273, 210, '20220809140657', 1, 'WH074', 74, NULL, '', NULL, NULL, 0, 0, '', '2022-08-09 14:06:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (274, 210, '20220814231315', 5, 'WH079', 79, NULL, '', NULL, NULL, 0, 0, '', '2022-08-09 14:06:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (275, 220, '20220813113700', 12, 'R1040', 1040, NULL, '', NULL, NULL, 0, 0, '', '2022-08-09 17:16:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (276, 226, '20220809174609', 2, 'R20220809002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-08-09 17:36:22', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (277, 211, '20220810082146', 5, 'L022', 22, NULL, '', NULL, NULL, 0, 0, '', '2022-08-10 08:21:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (278, 211, '20250312143950', 35, 'L057', 57, NULL, '', NULL, NULL, 0, 0, '', '2022-08-10 08:21:48', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (279, 212, '20220810082207', 2, 'A0013', 13, NULL, '', NULL, NULL, 0, 0, '', '2022-08-10 08:22:06', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (280, 212, '20220810083445', 4, 'A0017', 17, NULL, '', NULL, NULL, 0, 0, '', '2022-08-10 08:22:08', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (281, 218, '20220814194551', 6, 'T00023', 23, NULL, '', NULL, NULL, 0, 0, '', '2022-08-10 10:58:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (282, 215, '20220810141929', 7, 'MO202208100007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-08-10 12:30:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (283, 215, '20220811155054', 2, 'MO202208110002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-08-11 15:50:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (284, 215, '20220812163345', 36, 'MO202208120036', 36, NULL, '', NULL, NULL, 0, 0, '', '2022-08-12 11:40:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (285, 225, '20220812202238', 5, 'IQC20220812005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-08-12 13:54:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (286, 222, '20241203152531', 30, 'I0043', 43, NULL, '', NULL, NULL, 0, 0, '', '2022-08-12 14:20:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (287, 205, '20220813012808', 21, 'ITEM00000109', 109, NULL, '', NULL, NULL, 0, 0, '', '2022-08-12 16:00:23', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (288, 226, '20220812214513', 3, 'R20220812003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-08-12 16:51:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (289, 205, '20220813133124', 10, 'ITEM00000119', 119, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 01:28:09', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (290, 226, '20220813152657', 5, 'R20220813005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 01:47:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (291, 215, '20220813232357', 10, 'MO202208130010', 10, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 08:50:33', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (292, 207, '20220813134013', 7, 'C00031', 31, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 11:24:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (293, 220, '20220815153054', 12, 'R1052', 1052, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 11:37:04', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (294, 209, '20220816172957', 8, 'WS034', 34, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 13:39:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (295, 207, '20220813134017', 6, 'C00037', 37, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 13:40:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (296, 207, '20220815132333', 20, 'C00057', 57, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 13:40:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (297, 225, '20220813225044', 3, 'IQC20220813003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 15:14:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (298, 205, '20220815085111', 12, 'ITEM00000131', 131, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 21:48:41', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (299, 230, '20220815103546', 5, 'SUB009', 9, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 22:00:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (300, 206, '20220815143043', 4, 'ITEM_TYPE_0066', 66, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 23:18:41', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (301, 219, '20220815164921', 9, 'PROCESS077', 77, NULL, '', NULL, NULL, 0, 0, '', '2022-08-13 23:32:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (302, 215, '20220814231425', 5, 'MO202208140005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-08-14 10:40:29', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (303, 212, '20220818105233', 4, 'A0021', 21, NULL, '', NULL, NULL, 0, 0, '', '2022-08-14 11:19:56', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (304, 227, '20220814214249', 1, 'T026', 26, NULL, '', NULL, NULL, 0, 0, '', '2022-08-14 21:42:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (305, 227, '20220817195511', 33, 'T058', 58, NULL, '', NULL, NULL, 0, 0, '', '2022-08-14 21:42:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (306, 210, '20220817170523', 21, 'WH100', 100, NULL, '', NULL, NULL, 0, 0, '', '2022-08-14 23:13:23', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (307, 208, '20220816133617', 7, 'V00031', 31, NULL, '', NULL, NULL, 0, 0, '', '2022-08-14 23:16:45', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (308, 205, '20220815110633', 5, 'ITEM00000136', 136, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 08:51:13', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (309, 218, '20231111111200', 37, 'T00060', 60, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 09:53:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (310, 215, '20220815185151', 18, 'MO202208150018', 18, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 10:12:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (311, 226, '20220815143729', 8, 'R20220815008', 8, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 10:44:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (312, 205, '20220815140036', 3, 'ITEM00000139', 139, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 11:06:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (313, 225, '20220815111142', 1, 'IQC20220815001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 11:11:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (314, 207, '20220815132338', 6, 'C00063', 63, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 13:23:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (315, 207, '20220815132340', 4, 'C00067', 67, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 13:23:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (316, 207, '20220822100445', 95, 'C00162', 162, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 13:23:41', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (317, 230, '20220818164931', 21, 'SUB030', 30, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 14:40:05', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (318, 205, '20220817093425', 51, 'ITEM00000190', 190, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 15:43:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (319, 219, '20220818141839', 28, 'PROCESS105', 105, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 16:50:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (320, 229, '20220815172658', 2, 'RTV20220815002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 17:12:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (321, 220, '20220815200538', 2, 'R1054', 1054, NULL, '', NULL, NULL, 0, 0, '', '2022-08-15 19:52:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (322, 220, '20220816155501', 6, 'R1060', 1060, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 00:59:25', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (323, 226, '20220816173419', 18, 'R20220816018', 18, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 09:24:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (324, 215, '20220816232550', 38, 'MO202208160038', 38, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 10:18:25', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (325, 225, '20220816201615', 4, 'IQC20220816004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 10:32:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (326, 208, '20220816140813', 5, 'V00036', 36, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 13:36:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (327, 208, '20220823111843', 12, 'V00048', 48, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 14:08:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (328, 229, '20220816195356', 6, 'RTV20220816006', 6, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 17:35:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (329, 220, '20220818105353', 6, 'R1066', 1066, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 19:55:41', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (330, 217, '20220819093714', 7, 'TT032', 32, NULL, '', NULL, NULL, 0, 0, '', '2022-08-16 19:58:24', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (331, 215, '20220817222518', 14, 'MO202208170014', 14, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 08:47:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (332, 209, '20220820094836', 17, 'WS051', 51, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 08:55:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (333, 229, '20220817091138', 2, 'RTV20220817002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 09:11:36', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (334, 205, '20220818115033', 32, 'ITEM00000222', 222, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 09:34:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (335, 206, '20220822091118', 19, 'ITEM_TYPE_0085', 85, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 11:14:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (336, 226, '20220817205507', 22, 'R20220817022', 22, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 14:03:20', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (337, 225, '20220817203604', 5, 'IQC20220817005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 15:57:28', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (338, 210, '20220817170524', 1, 'WH101', 101, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 17:05:24', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (339, 233, '20220817231312', 6, 'ISSUE20220817006', 6, NULL, '', NULL, NULL, 0, 0, '', '2022-08-17 23:02:13', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (340, 226, '20220818211659', 12, 'R20220818012', 12, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 09:30:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (341, 210, '20220822145345', 35, 'WH136', 136, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 09:39:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (342, 225, '20220818165508', 12, 'IQC20220818012', 12, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 09:41:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (343, 229, '20220818180526', 7, 'RTV20220818007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 10:10:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (344, 215, '20220818230633', 77, 'MO202208180077', 77, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 10:33:04', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (345, 220, '20220819091351', 17, 'R1083', 1083, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 10:53:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (346, 212, '20220820170019', 5, 'A0026', 26, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 11:07:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (347, 216, '20220818164828', 11, 'WS0045', 45, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 11:39:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (348, 227, '20220818160823', 8, 'T066', 66, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 13:40:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (349, 205, '20220818134540', 1, 'ITEM00000223', 223, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 13:45:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (350, 205, '20220818141238', 3, 'ITEM00000226', 226, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 14:04:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (351, 205, '20220819101459', 33, 'ITEM00000259', 259, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 14:13:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (352, 219, '20220818144345', 2, 'PROCESS107', 107, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 14:43:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (353, 227, '20220818175718', 3, 'T069', 69, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 16:08:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (354, 216, '20250522171029', 96, 'WS0141', 141, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 16:48:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (355, 230, '20241226150737', 19, 'SUB049', 49, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 16:56:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (356, 227, '20220820110823', 7, 'T076', 76, NULL, '', NULL, NULL, 0, 0, '', '2022-08-18 17:57:45', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (357, 219, '20250227115115', 89, 'PROCESS196', 196, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 00:22:13', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (358, 226, '20220819211451', 22, 'R20220819022', 22, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 08:46:50', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (359, 215, '20220819211957', 44, 'MO202208190044', 44, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 08:47:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (360, 225, '20220819213654', 41, 'IQC20220819041', 41, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 08:49:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (361, 214, '20220821094813', 15, 'M0041', 41, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 09:12:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (362, 220, '20220819173023', 13, 'R1096', 1096, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 09:13:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (363, 217, '20220821133355', 15, 'TT047', 47, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 09:37:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (364, 236, '20220819111319', 3, '32323232323232323232323232ITEM_TYPE_', 14, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 11:13:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (365, 236, '20220819142129', 64, 'IF2022081929', 72, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 11:16:04', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (366, 236, '20220819145515', 12, 'IF2022081915', 1094, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 14:21:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (367, 236, '20220819205101', 16, 'IF2022081901', 21, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 16:18:20', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (368, 236, '20220823103219', 76, 'IF2022082319', 48, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 20:51:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (369, 229, '20220819205514', 1, 'RTV20220819001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 20:55:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (370, 220, '20250516144213', 28, 'R1124', 1124, NULL, '', NULL, NULL, 0, 0, '', '2022-08-19 21:12:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (371, 209, '20220820160129', 7, 'WS058', 58, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 09:48:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (372, 215, '20220820222241', 39, 'MO202208200039', 39, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 10:00:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (373, 226, '20220820215444', 21, 'R20220820021', 21, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 10:13:38', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (374, 227, '20220820110825', 2, 'T078', 78, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 11:08:24', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (375, 227, '20220820220731', 17, 'T095', 95, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 11:08:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (376, 225, '20220820160353', 3, 'IQC20220820003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 13:27:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (377, 209, '20250205105159', 26, 'WS084', 84, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 16:01:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (378, 227, '20220823083639', 10, 'T105', 105, NULL, '', NULL, NULL, 0, 0, '', '2022-08-20 22:07:33', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (379, 215, '20220821192938', 5, 'MO202208210005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-08-21 00:51:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (380, 214, '20220821094815', 1, 'M0042', 42, NULL, '', NULL, NULL, 0, 0, '', '2022-08-21 09:48:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (381, 212, '20250312144005', 21, 'A0047', 47, NULL, '', NULL, NULL, 0, 0, '', '2022-08-21 13:28:13', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (382, 214, '20250205105545', 16, 'M0058', 58, NULL, '', NULL, NULL, 0, 0, '', '2022-08-21 13:31:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (383, 217, '20220823205336', 11, 'TT058', 58, NULL, '', NULL, NULL, 0, 0, '', '2022-08-21 13:45:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (384, 226, '20220821225220', 7, 'R20220821007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-08-21 15:16:36', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (385, 215, '20220822234830', 19, 'MO202208220019', 19, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 08:38:59', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (386, 226, '20220822192029', 25, 'R20220822025', 25, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 08:48:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (387, 219, '20220822090456', 1, 'PROCESS140', 140, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 09:04:56', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (388, 219, '20220822090503', 1, 'PROCESS152', 152, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 09:05:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (389, 206, '20250312103442', 12, 'ITEM_TYPE_0097', 97, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 09:11:56', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (390, 229, '20220822155824', 7, 'RTV20220822007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 09:13:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (391, 207, '20220822100442', 1, 'C00153', 153, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 10:04:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (392, 207, '20220822102252', 5, 'C00167', 167, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 10:05:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (393, 207, '20250919172829', 39, 'C00206', 206, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 10:22:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (394, 210, '20220823083833', 3, 'WH139', 139, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 14:53:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (395, 225, '20220822154721', 1, 'IQC20220822001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-08-22 15:47:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (396, 226, '20220823203609', 20, 'R20220823020', 20, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 00:41:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (397, 215, '20220823200644', 52, 'MO202208230052', 52, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 07:51:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (398, 210, '20250224094719', 23, 'WH162', 162, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 08:38:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (399, 229, '20220823205401', 5, 'RTV20220823005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 08:43:32', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (400, 227, '20220823184025', 12, 'T117', 117, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 08:58:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (401, 225, '20220823192837', 7, 'IQC20220823007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 09:47:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (402, 236, '20220823173612', 17, 'IF2022082312', 51, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 10:41:04', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (403, 208, '20250312153848', 20, 'V00068', 68, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 15:09:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (404, 236, '20220823192141', 10, 'IF2022082341', 154, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 19:08:33', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (405, 236, '20220927102019', 16, 'IF2022092719', 95, NULL, '', NULL, NULL, 0, 0, '', '2022-08-23 19:21:45', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (406, 215, '20220828084935', 6, 'MO202208280006', 6, NULL, '', NULL, NULL, 0, 0, '', '2022-08-28 08:36:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (407, 237, '20220829224554', 4, 'IPQC202208290004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-08-29 22:09:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (408, 225, '20220831211548', 3, 'IQC20220831003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-08-31 20:57:09', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (409, 238, '20220901212705', 4, 'OQC20220901004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-09-01 20:35:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (410, 226, '20220902104056', 4, 'R20220902004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-09-02 10:36:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (411, 233, '20220902104302', 1, 'ISSUE20220902001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-09-02 10:43:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (412, 239, '20220904223417', 10, 'PBACK20220904010', 10, NULL, '', NULL, NULL, 0, 0, '', '2022-09-03 23:50:32', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (413, 237, '20220909211934', 1, 'IPQC202209090001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-09-09 21:19:38', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (414, 226, '20220910111422', 2, 'R20220910002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-09-10 10:57:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (415, 226, '20220912232033', 1, 'R20220912001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-09-12 23:20:33', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (416, 226, '20220913223354', 1, 'R20220913001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-09-13 22:33:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (417, 233, '20220913225049', 2, 'ISSUE20220913002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-09-13 22:35:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (418, 240, '20220915232017', 1, 'RT202209150001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-09-15 23:20:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (419, 226, '20220916212404', 1, 'R20220916001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-09-16 21:24:06', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (420, 241, '20220923105914', 1, 'PR20220923001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-09-23 10:59:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (421, 215, '20220926212817', 2, 'MO202209260002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-09-26 21:27:05', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (422, 215, '20220927112106', 2, 'MO202209270002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-09-27 10:25:50', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (423, 226, '20220927131833', 2, 'R20220927002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-09-27 13:15:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (424, 242, '20221030003901', 5, 'REP2022005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-09-28 22:02:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (425, 243, '20221005194837', 3, 'PS20221005003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-10-05 19:46:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (426, 233, '20221006132703', 1, 'ISSUE20221006001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-06 13:27:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (427, 244, '20221006214134', 1, 'RS20221006001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-06 21:41:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (428, 245, '20221011233528', 7, 'PACK202210110007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-10-11 01:23:50', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (429, 240, '20221015152217', 1, 'RT202210150001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-15 15:22:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (430, 236, '20221022210843', 7, 'IF20221022007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-10-22 20:43:20', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (431, 236, '20221025204220', 4, 'IF20221025004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-10-25 20:38:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (432, 236, '20221026212601', 3, 'IF20221026003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-10-26 21:15:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (433, 226, '20221026231933', 1, 'R20221026001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-26 23:19:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (434, 229, '20221027210214', 1, 'RTV20221027001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-27 21:02:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (435, 240, '20221027221229', 2, 'RT202210270002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-10-27 22:11:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (436, 241, '20221027225011', 1, 'PR20221027001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-27 22:50:12', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (437, 243, '20221027231017', 1, 'PS20221027001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-27 23:10:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (438, 245, '20221029234108', 3, 'PACK202210290003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-10-29 23:10:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (439, 225, '20221030164100', 1, 'IQC20221030001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-10-30 16:41:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (440, 215, '20221103223659', 3, 'MO202211030003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-11-03 21:39:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (441, 226, '20221105195223', 5, 'R20221105005', 5, NULL, '', NULL, NULL, 0, 0, '', '2022-11-05 19:26:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (442, 229, '20221105193737', 1, 'RTV20221105001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-11-05 19:37:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (443, 233, '20221105195829', 4, 'ISSUE20221105004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-11-05 19:42:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (444, 240, '20221105195732', 1, 'RT202211050001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-11-05 19:57:32', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (445, 241, '20221115212306', 1, 'PR20221115001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-11-15 21:23:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (446, 226, '20221115214315', 4, 'R20221115004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-11-15 21:27:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (447, 229, '20221115214430', 1, 'RTV20221115001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-11-15 21:44:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (448, 233, '20221115232625', 7, 'ISSUE20221115007', 7, NULL, '', NULL, NULL, 0, 0, '', '2022-11-15 21:50:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (449, 240, '20221115233222', 1, 'RT202211150001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-11-15 23:32:23', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (450, 241, '20221119162353', 4, 'PR20221119004', 4, NULL, '', NULL, NULL, 0, 0, '', '2022-11-19 10:44:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (451, 226, '20221119141933', 2, 'R20221119002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-11-19 12:11:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (452, 236, '20221119141004', 1, 'IF20221119001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-11-19 14:10:05', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (453, 243, '20221119164023', 2, 'PS20221119002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-11-19 16:39:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (454, 244, '20221119164159', 1, 'RS20221119001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-11-19 16:42:00', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (455, 246, '20221130220243', 3, 'TR20221130003', 3, NULL, '', NULL, NULL, 0, 0, '', '2022-11-30 21:59:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (456, 246, '20221204142346', 1, 'TR20221204001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-12-04 14:23:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (457, 241, '20221204152859', 1, 'PR20221204001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-12-04 15:28:59', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (458, 233, '20221204152931', 1, 'ISSUE20221204001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-12-04 15:29:31', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (459, 246, '20221206190440', 1, 'TR20221206001', 1, NULL, '', NULL, NULL, 0, 0, '', '2022-12-06 19:04:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (460, 246, '20221207185741', 2, 'TR20221207002', 2, NULL, '', NULL, NULL, 0, 0, '', '2022-12-07 18:42:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (461, 247, '20221209122247', 1, 'SN-IF202208243920221209000001', 1, 'IF2022082439', '', NULL, NULL, 0, 0, '', '2022-12-09 12:22:48', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (462, 236, '20230304180258', 1, 'IF20230304001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-03-04 19:19:25', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (463, 221, '20231127103652', 24, 'TASK20230024', 24, NULL, '', NULL, NULL, 0, 0, '', '2023-03-04 19:40:36', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (464, 236, '20230305182553', 6, 'IF20230305006', 6, NULL, '', NULL, NULL, 0, 0, '', '2023-03-05 15:47:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (465, 247, '20230306185218', 5, 'SN-IF202208243720230306000005', 5, 'IF2022082437', '', NULL, NULL, 0, 0, '', '2023-03-06 18:52:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (466, 225, '20230310204736', 2, 'IQC20230310002', 2, NULL, '', NULL, NULL, 0, 0, '', '2023-03-10 20:45:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (467, 226, '20230605170607', 4, 'R20230605004', 4, '', '', NULL, NULL, 0, 0, '', '2023-06-05 17:04:05', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (468, 226, '20230608162433', 2, 'R20230608002', 2, '', '', NULL, NULL, 0, 0, '', '2023-06-08 15:30:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (469, 226, '20230613145535', 1, 'R20230613001', 1, '', '', NULL, NULL, 0, 0, '', '2023-06-13 14:55:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (470, 226, '20230615142745', 4, 'R20230615004', 4, '', '', NULL, NULL, 0, 0, '', '2023-06-15 09:12:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (471, 226, '20230825201547', 1, 'R20230825001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-08-25 20:15:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (472, 236, '20230905202141', 4, 'IF20230905004', 4, NULL, '', NULL, NULL, 0, 0, '', '2023-09-05 20:19:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (473, 226, '20230905202609', 1, 'R20230905001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-09-05 20:26:09', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (474, 215, '20230905203008', 4, 'MO202309050004', 4, NULL, '', NULL, NULL, 0, 0, '', '2023-09-05 20:27:20', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (475, 233, '20230905203917', 1, 'ISSUE20230905001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-09-05 20:39:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (476, 241, '20230905205116', 1, 'PR20230905001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-09-05 20:51:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (477, 225, '20230905210044', 1, 'IQC20230905001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-09-05 21:00:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (478, 215, '20230921202108', 1, 'MO202309210001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-09-21 20:21:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (479, 243, '20231010180612', 1, 'PS20231010001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-10-10 18:06:12', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (480, 248, '20231017111110', 1, 'FB202310170001', 1, '', '', NULL, NULL, 0, 0, '', '2023-10-17 11:11:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (481, 237, '20231017163722', 7, 'IPQC202310170007', 7, NULL, '', NULL, NULL, 0, 0, '', '2023-10-17 16:05:28', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (482, 223, '20231019142225', 2, 'QCT2023002', 2, NULL, '', NULL, NULL, 0, 0, '', '2023-10-17 16:28:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (483, 236, '20231017201337', 1, 'IF20231017001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-10-17 20:13:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (484, 225, '20231017210102', 1, 'IQC20231017001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-10-17 21:01:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (485, 237, '20231018160141', 4, 'IPQC202310180004', 4, NULL, '', NULL, NULL, 0, 0, '', '2023-10-18 10:12:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (486, 225, '20231018163758', 1, 'IQC20231018001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-10-18 16:37:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (487, 226, '20231019141441', 1, 'R20231019001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-10-19 14:14:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (488, 225, '20231019151010', 5, 'IQC20231019005', 5, NULL, '', NULL, NULL, 0, 0, '', '2023-10-19 14:18:48', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (489, 215, '20231029225916', 4, 'MO202310290004', 4, NULL, '', NULL, NULL, 0, 0, '', '2023-10-29 22:53:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (490, 249, '20231030150119', 3, 'OI202310300003', 3, NULL, '', NULL, NULL, 0, 0, '', '2023-10-30 14:26:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (491, 250, '20231030201833', 1, 'OR202310300001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-10-30 20:18:33', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (492, 250, '20231031164803', 1, 'OR202310310001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-10-31 16:48:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (493, 247, '20231101224447', 15, 'SN-IF202208240420231101000015', 15, 'IF2022082404', '', NULL, NULL, 0, 0, '', '2023-11-01 22:34:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (494, 237, '20231104212959', 1, 'IPQC202311040001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-11-04 21:29:59', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (495, 225, '20231112204800', 1, 'IQC20231112001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-11-12 20:47:59', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (496, 236, '20231120212133', 1, 'IF20231120001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-11-20 21:21:32', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (497, 215, '20231120220233', 3, 'MO202311200003', 3, NULL, '', NULL, NULL, 0, 0, '', '2023-11-20 21:39:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (498, 248, '20231120222037', 2, 'FB202311200002', 2, '', '', NULL, NULL, 0, 0, '', '2023-11-20 22:15:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (499, 238, '20231120223801', 1, 'OQC20231120001', 1, NULL, '', NULL, NULL, 0, 0, '', '2023-11-20 22:38:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (500, 215, '20231127102842', 7, 'MO202311270007', 7, NULL, '', NULL, NULL, 0, 0, '', '2023-11-27 10:20:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (501, 248, '20231127103822', 1, 'FB202311270001', 1, '', '', NULL, NULL, 0, 0, '', '2023-11-27 10:38:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (502, 226, '20240517164217', 1, 'R20240517001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-05-17 16:42:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (503, 251, '20250401144641', 26, 'T026', 26, NULL, '', NULL, NULL, 0, 0, '', '2024-05-20 12:01:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (504, 226, '20240619153334', 1, 'R20240619001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-06-19 15:33:33', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (505, 215, '20240906111624', 2, 'MO202409060002', 2, NULL, '', NULL, NULL, 0, 0, '', '2024-09-06 11:14:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (506, 236, '20240906153544', 9, 'IF20240906009', 9, '', '', NULL, NULL, 0, 0, '', '2024-09-06 15:05:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (507, 233, '20240908094739', 3, 'ISSUE20240908003', 3, NULL, '', NULL, NULL, 0, 0, '', '2024-09-08 09:39:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (508, 225, '20241113155529', 7, 'IQC20241113007', 7, NULL, '', NULL, NULL, 0, 0, '', '2024-11-13 14:47:05', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (509, 223, '20241113152950', 1, 'QCT2024001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-11-13 15:29:50', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (510, 226, '20241113162830', 1, 'R20241113001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-11-13 16:28:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (511, 236, '20241118165010', 1, 'IF20241118001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-11-18 16:50:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (512, 225, '20241129152535', 1, 'IQC20241129001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-11-29 15:25:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (513, 225, '20241203152847', 1, 'IQC20241203001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-12-03 15:28:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (514, 252, '20241203235213', 15, 'P20241203015', 15, NULL, '', NULL, NULL, 0, 0, '', '2024-12-03 19:03:12', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (515, 237, '20241203233044', 2, 'IPQC202412030002', 2, NULL, '', NULL, NULL, 0, 0, '', '2024-12-03 23:07:13', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (516, 238, '20241203235152', 2, 'OQC20241203002', 2, NULL, '', NULL, NULL, 0, 0, '', '2024-12-03 23:50:12', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (517, 253, '20241210203814', 13, 'C0000000013', 13, NULL, '', NULL, NULL, 0, 0, '', '2024-12-05 14:17:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (518, 254, '20250604144929', 12, 'PRINTER012', 12, NULL, '', NULL, NULL, 0, 0, '', '2024-12-05 14:36:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (519, 255, '20241209211728', 1, 'AN20241209001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-12-09 21:17:28', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (520, 225, '20241209222915', 2, 'IQC20241209002', 2, NULL, '', NULL, NULL, 0, 0, '', '2024-12-09 22:24:04', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (521, 252, '20241209224555', 6, 'P20241209006', 6, NULL, '', NULL, NULL, 0, 0, '', '2024-12-09 22:29:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (522, 225, '20241210170902', 3, 'IQC20241210003', 3, NULL, '', NULL, NULL, 0, 0, '', '2024-12-10 16:20:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (523, 252, '20241210233916', 29, 'P20241210029', 29, NULL, '', NULL, NULL, 0, 0, '', '2024-12-10 16:20:37', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (531, 247, '20241210224911', 10, 'SN-IF202208242820241210000010', 10, 'IF2022082428', '', NULL, NULL, 0, 0, '', '2024-12-10 21:32:25', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (532, 237, '20241210232800', 8, 'IPQC202412100008', 8, NULL, '', NULL, NULL, 0, 0, '', '2024-12-10 23:21:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (533, 252, '20241211110155', 7, 'P20241211007', 7, NULL, '', NULL, NULL, 0, 0, '', '2024-12-11 00:35:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (534, 226, '20241220145930', 2, 'R20241220002', 2, NULL, '', NULL, NULL, 0, 0, '', '2024-12-20 14:09:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (535, 255, '20241220144455', 1, 'AN20241220001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-12-20 14:44:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (536, 225, '20241220144838', 1, 'IQC20241220001', 1, NULL, '', NULL, NULL, 0, 0, '', '2024-12-20 14:48:38', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (537, 252, '20241229172817', 5, 'P20241229005', 5, NULL, '', NULL, NULL, 0, 0, '', '2024-12-29 15:47:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (538, 252, '20241230201553', 2, 'P20241230002', 2, NULL, '', NULL, NULL, 0, 0, '', '2024-12-30 20:13:28', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (539, 241, '20250116121035', 1, 'PR20250116001', 1, '', '', NULL, NULL, 0, 0, '', '2025-01-16 12:10:36', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (540, 240, '20250116173817', 1, 'RT202501160001', 1, '', '', NULL, NULL, 0, 0, '', '2025-01-16 17:38:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (541, 226, '20250207114943', 2, 'R20250207002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-02-07 11:49:28', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (542, 215, '20250208172237', 10, 'MO202502080010', 10, NULL, '', NULL, NULL, 0, 0, '', '2025-02-08 15:08:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (543, 225, '20250208180026', 2, 'IQC20250208002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-02-08 17:59:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (544, 248, '20250211152304', 3, 'FB202502110003', 3, '', '', NULL, NULL, 0, 0, '', '2025-02-11 14:47:19', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (545, 236, '20250211155749', 1, 'IF20250211001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-02-11 15:57:48', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (546, 215, '20250221172058', 1, 'MO202502210001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-02-21 17:20:59', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (547, 256, '20250311211444', 7, 'DEPT007', 7, NULL, '', NULL, NULL, 0, 0, '', '2025-02-24 11:34:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (548, 255, '20250225103907', 8, 'AN20250225008', 8, NULL, '', NULL, NULL, 0, 0, '', '2025-02-25 10:13:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (549, 225, '20250225104530', 5, 'IQC20250225005', 5, NULL, '', NULL, NULL, 0, 0, '', '2025-02-25 10:31:46', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (550, 232, '20250521142706', 30, 'BAT00000030', 30, '', '', NULL, NULL, 0, 0, '', '2025-02-28 17:06:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (551, 236, '20250228175838', 4, 'IF20250228004', 4, NULL, '', NULL, NULL, 0, 0, '', '2025-02-28 17:48:17', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (552, 226, '20250228182725', 1, 'R20250228001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-02-28 18:27:25', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (553, 226, '20250303143300', 4, 'R20250303004', 4, NULL, '', NULL, NULL, 0, 0, '', '2025-03-03 13:55:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (554, 229, '20250303225559', 3, 'RTV20250303003', 3, NULL, '', NULL, NULL, 0, 0, '', '2025-03-03 21:21:15', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (555, 226, '20250304115600', 4, 'R20250304004', 4, NULL, '', NULL, NULL, 0, 0, '', '2025-03-04 11:53:29', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (556, 229, '20250304145737', 3, 'RTV20250304003', 3, NULL, '', NULL, NULL, 0, 0, '', '2025-03-04 14:50:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (557, 249, '20250304181555', 10, 'OI202503040010', 10, NULL, '', NULL, NULL, 0, 0, '', '2025-03-04 16:55:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (558, 240, '20250305185018', 2, 'RT202503050002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-05 18:42:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (559, 237, '20250306102654', 4, 'IPQC202503060004', 4, NULL, '', NULL, NULL, 0, 0, '', '2025-03-06 09:45:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (560, 257, '20250306214213', 6, 'RQC20250306006', 6, NULL, '', NULL, NULL, 0, 0, '', '2025-03-06 17:22:25', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (561, 249, '20250306212254', 1, 'OI202503060001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-06 21:22:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (562, 240, '20250306212528', 1, 'RT202503060001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-06 21:25:28', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (563, 257, '20250307120006', 9, 'RQC20250307009', 9, NULL, '', NULL, NULL, 0, 0, '', '2025-03-07 10:30:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (564, 248, '20250307112704', 1, 'FB202503070001', 1, '', '', NULL, NULL, 0, 0, '', '2025-03-07 11:27:04', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (565, 252, '20250307115414', 2, 'P20250307002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-07 11:49:23', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (566, 240, '20250307142455', 1, 'RT202503070001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-07 14:24:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (567, 249, '20250311211648', 1, 'OI202503110001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-11 21:16:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (568, 236, '20250312113204', 6, 'IF20250312006', 6, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 10:34:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (569, 215, '20250312113849', 10, 'MO202503120010', 10, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 11:09:12', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (570, 221, '20250312142651', 15, 'TASK20250015', 15, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 11:41:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (571, 255, '20250312184744', 5, 'AN20250312005', 5, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 15:28:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (572, 229, '20250312160123', 5, 'RTV20250312005', 5, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 15:33:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (573, 225, '20250312172057', 7, 'IQC20250312007', 7, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 15:53:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (574, 226, '20250312184840', 6, 'R20250312006', 6, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 15:58:23', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (575, 233, '20250312162252', 1, 'ISSUE20250312001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 16:22:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (576, 240, '20250312231053', 3, 'RT202503120003', 3, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 23:08:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (577, 257, '20250312231823', 2, 'RQC20250312002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-12 23:10:05', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (578, 248, '20250312235109', 1, 'FB202503120001', 1, '', '', NULL, NULL, 0, 0, '', '2025-03-12 23:51:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (579, 248, '20250313181600', 10, 'FB202503130010', 10, '', '', NULL, NULL, 0, 0, '', '2025-03-13 10:06:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (580, 237, '20250313181647', 7, 'IPQC202503130007', 7, NULL, '', NULL, NULL, 0, 0, '', '2025-03-13 14:21:06', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (581, 241, '20250313235157', 2, 'PR20250313002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-13 23:42:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (582, 241, '20250314000158', 2, 'PR20250314002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-14 00:01:26', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (583, 258, '20250314094940', 1, 'SN20250314001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-14 09:49:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (584, 243, '20250314230517', 2, 'PS20250314002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-14 23:04:10', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (585, 233, '20250315232507', 3, 'ISSUE20250315003', 3, NULL, '', NULL, NULL, 0, 0, '', '2025-03-15 10:59:09', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (586, 248, '20250315113102', 4, 'FB202503150004', 4, '', '', NULL, NULL, 0, 0, '', '2025-03-15 11:02:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (587, 237, '20250315143054', 3, 'IPQC202503150003', 3, NULL, '', NULL, NULL, 0, 0, '', '2025-03-15 11:16:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (588, 243, '20250315143356', 1, 'PS20250315001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-15 14:33:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (589, 241, '20250315203358', 2, 'PR20250315002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-15 14:50:22', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (590, 236, '20250315213051', 5, 'IF20250315005', 5, NULL, '', NULL, NULL, 0, 0, '', '2025-03-15 21:22:21', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (591, 238, '20250315230603', 8, 'OQC20250315008', 8, NULL, '', NULL, NULL, 0, 0, '', '2025-03-15 22:13:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (592, 244, '20250315232932', 1, 'RS20250315001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-15 23:29:31', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (593, 244, '20250316004503', 2, 'RS20250316002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-16 00:12:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (594, 244, '20250317095058', 2, 'RS20250317002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-17 09:49:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (595, 257, '20250317180053', 2, 'RQC20250317002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-17 17:55:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (596, 233, '20250317183141', 1, 'ISSUE20250317001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-17 18:31:40', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (597, 215, '20250318110305', 15, 'MO202503180015', 15, NULL, '', NULL, NULL, 0, 0, '', '2025-03-18 09:41:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (598, 248, '20250318142934', 2, 'FB202503180002', 2, '', '', NULL, NULL, 0, 0, '', '2025-03-18 14:28:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (599, 237, '20250318142826', 1, 'IPQC202503180001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-18 14:28:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (600, 246, '20250318183104', 4, 'TR20250318004', 4, NULL, '', NULL, NULL, 0, 0, '', '2025-03-18 18:20:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (601, 246, '20250319142318', 2, 'TR20250319002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-19 12:03:01', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (602, 236, '20250321174516', 1, 'IF20250321001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-21 17:45:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (603, 226, '20250324145423', 1, 'R20250324001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-24 14:54:23', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (604, 240, '20250325162814', 1, 'RT202503250001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-03-25 16:28:14', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (605, 255, '20250327102538', 2, 'AN20250327002', 2, '', '', NULL, NULL, 0, 0, '', '2025-03-27 10:22:00', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (606, 226, '20250327125549', 1, 'R20250327001', 1, '', '', NULL, NULL, 0, 0, '', '2025-03-27 12:55:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (607, 226, '20250330135253', 1, 'R20250330001', 1, '', '', NULL, NULL, 0, 0, '', '2025-03-30 13:52:44', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (608, 248, '20250331212351', 1, 'FB202503310001', 1, '', '', NULL, NULL, 0, 0, '', '2025-03-31 21:23:52', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (609, 259, '20250423121112', 2, 'STP002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-03-31 21:56:36', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (610, 260, '20250401230826', 1, 'ST2025040101', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-01 23:08:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (611, 261, '20250430103114', 12, 'CLIENT012', 12, NULL, '', NULL, NULL, 0, 0, '', '2025-04-03 11:01:20', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (612, 260, '20250407185501', 12, 'ST2025040712', 12, NULL, '', NULL, NULL, 0, 0, '', '2025-04-07 17:51:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (613, 248, '20250408221447', 5, 'FB202504080005', 5, '', '', NULL, NULL, 0, 0, '', '2025-04-08 00:21:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (614, 226, '20250408101631', 2, 'R20250408002', 2, '', '', NULL, NULL, 0, 0, '', '2025-04-08 10:12:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (615, 225, '20250408105555', 1, 'IQC20250408001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-08 10:55:57', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (616, 255, '20250408171950', 12, 'AN20250408012', 12, NULL, '', NULL, NULL, 0, 0, '', '2025-04-08 17:18:45', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (617, 248, '20250409154736', 7, 'FB202504090007', 7, '', '', NULL, NULL, 0, 0, '', '2025-04-09 15:19:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (618, 249, '20250412124240', 2, 'OI202504120002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-04-12 12:40:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (619, 215, '20250412124206', 1, 'MO202504120001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-12 12:42:07', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (620, 250, '20250412173846', 2, 'OR202504120002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-04-12 17:08:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (621, 240, '20250412194400', 1, 'RT202504120001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-12 19:44:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (622, 248, '20250412200756', 1, 'FB202504120001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-12 20:07:56', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (623, 225, '20250412214457', 7, 'IQC20250412007', 7, NULL, '', NULL, NULL, 0, 0, '', '2025-04-12 21:33:13', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (624, 229, '20250414174026', 3, 'RTV20250414003', 3, '', '', NULL, NULL, 0, 0, '', '2025-04-14 10:16:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (625, 233, '20250414104033', 1, 'ISSUE20250414001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-14 10:40:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (626, 226, '20250414140740', 1, 'R20250414001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-14 14:07:42', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (627, 215, '20250415172626', 3, 'MO202504150003', 3, NULL, '', NULL, NULL, 0, 0, '', '2025-04-15 17:11:36', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (628, 229, '20250415175215', 1, 'RTV20250415001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-15 17:52:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (629, 240, '20250415175817', 1, 'RT202504150001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-15 17:58:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (630, 240, '20250417120420', 1, 'RT202504170001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-17 12:04:20', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (631, 241, '20250417173727', 1, 'PR20250417001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-17 17:37:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (632, 258, '20250418112553', 1, 'SN20250418001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-18 11:25:54', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (633, 243, '20250421162427', 2, 'PS20250421002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-04-21 14:30:03', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (634, 238, '20250421163138', 1, 'OQC20250421001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-21 16:31:39', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (635, 244, '20250421175348', 1, 'RS20250421001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-21 17:53:50', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (636, 244, '20250422104824', 3, 'RS20250422003', 3, '', '', NULL, NULL, 0, 0, '', '2025-04-22 10:39:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (637, 246, '20250422184735', 4, 'TR20250422004', 4, '', '', NULL, NULL, 0, 0, '', '2025-04-22 14:15:53', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (638, 246, '20250423105136', 1, 'TR20250423001', 1, '', '', NULL, NULL, 0, 0, '', '2025-04-23 10:51:36', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (639, 260, '20250423172218', 12, 'ST2025042312', 12, '', '', NULL, NULL, 0, 0, '', '2025-04-23 16:30:18', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (640, 240, '20250425154747', 4, 'RT202504250004', 4, '', '', NULL, NULL, 0, 0, '', '2025-04-25 15:19:29', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (641, 250, '20250425164134', 1, 'OR202504250001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-25 16:41:35', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (642, 225, '20250425183607', 1, 'IQC20250425001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-04-25 18:36:09', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (643, 242, '20250506161037', 2, 'REP2025002', 2, '', '', NULL, NULL, 0, 0, '', '2025-05-06 16:09:09', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (644, 262, '20250514180926', 7, 'C007', 7, NULL, '', NULL, NULL, 0, 0, '', '2025-05-12 16:31:24', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (645, 252, '20250513221420', 1, 'P20250513001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-05-13 22:14:22', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (646, 263, '20250515172334', 6, 'MISCR20250515006', 6, NULL, '', NULL, NULL, 0, 0, '', '2025-05-15 11:09:11', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (647, 215, '20250515150016', 1, 'MO202505150001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-05-15 15:00:16', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (648, 264, '20250516103553', 2, 'MISCI20250516002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-05-16 09:51:02', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (649, 236, '20250520201658', 1, 'IF20250520001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-05-20 20:16:58', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (650, 215, '20250520211030', 1, 'MO202505200001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-05-20 21:10:30', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (651, 226, '20250521142649', 2, 'R20250521002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-05-21 11:26:34', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (652, 248, '20250601163609', 1, 'FB202506010001', 1, '', '', NULL, NULL, 0, 0, '', '2025-06-01 16:36:13', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (653, 237, '20250601165214', 2, 'IPQC202506010002', 2, NULL, '', NULL, NULL, 0, 0, '', '2025-06-01 16:47:43', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (654, 237, '20250612100627', 1, 'IPQC202506120001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-06-12 10:06:27', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (655, 252, '20250612103840', 6, 'P20250612006', 6, NULL, '', NULL, NULL, 0, 0, '', '2025-06-12 10:06:49', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (656, 265, '20250627185329', 9, 'B0009', 9, NULL, '', NULL, NULL, 0, 0, '', '2025-06-20 10:08:55', '', NULL);
INSERT INTO `sys_auto_code_result` VALUES (657, 215, '20250626110929', 1, 'MO202506260001', 1, NULL, '', NULL, NULL, 0, 0, '', '2025-06-26 11:09:30', '', NULL);

-- ----------------------------
-- Table structure for sys_auto_code_rule
-- ----------------------------
DROP TABLE IF EXISTS `sys_auto_code_rule`;
CREATE TABLE `sys_auto_code_rule`  (
  `rule_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则编码',
  `rule_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
  `rule_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `max_length` int(0) NULL DEFAULT NULL COMMENT '最大长度',
  `is_padded` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否补齐',
  `padded_char` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '补齐字符',
  `padded_method` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'L' COMMENT '补齐方式',
  `enable_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'Y' COMMENT '是否启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  `attr1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` int(0) NULL DEFAULT 0 COMMENT '预留字段3',
  `attr4` int(0) NULL DEFAULT 0 COMMENT '预留字段4',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`rule_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 265 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '编码生成规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_auto_code_rule
-- ----------------------------
INSERT INTO `sys_auto_code_rule` VALUES (206, 'ITEM_TYPE_CODE', '物料分类编码', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-04-26 23:01:09', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (207, 'CLIENT_CODE', '客户编码规则', NULL, 6, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-06 21:20:29', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (208, 'VENDOR_CODE', '供应商编码规则', NULL, 6, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-06 22:50:13', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (209, 'WORKSHOP_CODE', '车间编码生成规则', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-07 17:48:52', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (210, 'WAREHOUSE_CODE', '仓库编码规则', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-07 21:59:51', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (211, 'LOCATION_CODE', '库区编码生成规则', NULL, 4, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 14:49:56', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (212, 'AREA_CODE', '库位编码规则', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 18:38:08', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (213, 'MACHINERY_TYPE_CODE', '设备类型编码规则', NULL, 10, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 19:46:09', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (214, 'MACHINERY_CODE', '设备编码规则', NULL, 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-08 21:26:39', 'admin', '2022-08-23 09:15:17');
INSERT INTO `sys_auto_code_rule` VALUES (215, 'WORKORDER_CODE', '生产工单编码规则1', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-09 11:39:59', 'admin', '2022-08-20 09:12:40');
INSERT INTO `sys_auto_code_rule` VALUES (216, 'WORKSTATION_CODE', '工作站编码规则', NULL, 6, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-10 21:55:24', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (217, 'TOOL_TYPE_CODE', '工装夹具类型编码', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-11 00:21:37', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (218, 'TOOL_CODE', '工装夹具编码规则', NULL, 6, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-11 22:07:17', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (219, 'PROCESS_CODE', '工序编码规则', NULL, 10, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-12 00:09:45', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (220, 'ROUTE_CODE', '工艺流程编码规则', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-12 23:06:36', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (221, 'TASK_CODE', '生产任务编码规则', NULL, 12, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-15 18:22:29', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (222, 'QC_INDEX_CODE', '检测项编码规则', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-17 21:57:23', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (223, 'QC_TEMPLATE_CODE', '检测模板编码规则', NULL, 10, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-17 22:43:08', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (224, 'DEFECT_CODE', '常见缺陷编码', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-19 11:33:27', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (225, 'QC_IQC_CODE', '来料检验单编码规则', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-19 16:28:07', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (226, 'ITEMRECPT_CODE', '物料入库单编码规则', NULL, 12, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-05-22 20:51:29', 'admin', '2022-05-22 20:53:12');
INSERT INTO `sys_auto_code_rule` VALUES (227, 'CAL_TEAM_CODE', '班组编码规则', NULL, 4, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-06 19:54:22', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (228, 'CAL_PLAN_CODE', '排班计划编号', NULL, 11, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-06 22:08:10', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (229, 'WM_RTVENDOR_CODE', '供应商退货单编码', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-13 15:48:07', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (230, 'SUBJECT_CODE', '设备点检保养项目编码', NULL, 6, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-16 20:27:54', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (231, 'CHECKPLAN_CODE', '点检编码规则', NULL, 11, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-06-16 21:50:00', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (232, 'BATCH_CODE', '批次规则', NULL, 11, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-07-14 12:02:10', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (233, 'ISSUE_CODE', '生产领料单编码', NULL, 16, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-07-17 19:32:10', 'admin', '2022-07-17 19:32:57');
INSERT INTO `sys_auto_code_rule` VALUES (234, '1', '2', NULL, 1, 'Y', '3', 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-07-29 16:34:20', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (236, 'ITEM_CODE', '物料规则', NULL, 13, 'N', '32', 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-08-19 10:44:20', 'admin', '2022-10-22 20:42:33');
INSERT INTO `sys_auto_code_rule` VALUES (237, 'QC_PQC_CODE', '过程检验单编码', NULL, 16, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-08-29 22:07:13', 'admin', '2025-03-13 14:20:26');
INSERT INTO `sys_auto_code_rule` VALUES (238, 'QC_OQC_CODE', '出货编码规则', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-01 20:30:31', 'admin', '2024-12-10 23:21:34');
INSERT INTO `sys_auto_code_rule` VALUES (239, 'PBACK_CODE', '生产退料单编码', '生产退料单编码', 16, 'N', '0', 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-03 23:47:11', 'admin', '2022-09-03 23:47:57');
INSERT INTO `sys_auto_code_rule` VALUES (240, 'RTISSUE_CODE', '生产退库单编号规则', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-15 23:18:40', 'admin', '2022-09-15 23:19:04');
INSERT INTO `sys_auto_code_rule` VALUES (241, 'PRODUCTRECPT_CODE', '产品入库单编码规则', NULL, 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-23 10:57:47', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (242, 'DV_REPAIR_CODE', '维修工单编号规则', NULL, 10, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-09-28 21:59:54', 'admin', '2025-05-06 16:09:03');
INSERT INTO `sys_auto_code_rule` VALUES (243, 'PRODUCTSALSE_CODE', '销售出库单编号', NULL, 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-10-05 19:45:35', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (244, 'WM_RTSALSE_CODE', '销售退货单编码规则', NULL, 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-10-06 21:40:18', 'admin', '2025-03-16 00:26:20');
INSERT INTO `sys_auto_code_rule` VALUES (245, 'PACKAGE_CODE', '装箱单编码规则', NULL, 16, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-10-11 01:22:08', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (246, 'TRANSFER_CODE', '转移单编码', NULL, 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-11-30 21:58:37', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (247, 'SN_CODE', 'SN码', NULL, 30, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2022-12-09 11:22:03', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (248, 'FEEDBACK_CODE', '生产报工单编码', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2023-10-17 11:09:25', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (249, 'WM_OUTSOURCE_ISSUE_CODE', '外协发料单编码', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 14:24:35', 'admin', '2025-04-25 17:39:15');
INSERT INTO `sys_auto_code_rule` VALUES (250, 'WM_OUTSOURCE_RECPT_CODE', '外协入库单编码', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2023-10-30 20:17:00', 'admin', '2025-04-25 17:39:08');
INSERT INTO `sys_auto_code_rule` VALUES (251, 'PRINT_TEMPLATE_CODE', '打印模板编号生成规则', NULL, 4, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2024-05-20 11:59:37', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (252, 'QC_RESULT_CODE', '检测样品编号', NULL, 12, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2024-12-03 18:59:48', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (253, 'CARD_CODE', '流转卡编码规则', NULL, 11, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2024-12-05 14:06:40', 'admin', '2024-12-05 14:17:41');
INSERT INTO `sys_auto_code_rule` VALUES (254, 'PRINTER_CODE', '打印机编号规则', NULL, 10, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2024-12-05 14:34:15', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (255, 'ARRIVALNOTICE_CODE', '到货通知单编号规则', NULL, 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2024-12-09 21:15:58', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (256, 'DEPT_CODE', '部门编码规则', NULL, 7, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-02-24 11:33:55', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (257, 'QC_RQC_CODE', '退料检验单编码规则', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-03-06 17:20:14', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (258, 'WM_SALES_NOTICE_CODE', '发货通知单编码规则', NULL, 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-03-14 09:30:45', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (259, 'WM_STOCK_TAKING_PLAN_CODE', '盘点方案编码规则', NULL, 6, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-03-31 21:38:48', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (260, 'WM_STOCK_TAKING_CODE', '盘点任务编码规则', NULL, 12, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-04-01 22:58:00', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (261, 'PRINTER_CLIENT_CODE', '打印机客户端编码规则', NULL, 10, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-04-03 10:49:36', 'admin', '2025-04-03 10:49:49');
INSERT INTO `sys_auto_code_rule` VALUES (262, 'REPORT_CHART_CODE', '统计图表编号', NULL, 4, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-05-12 12:12:36', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (263, 'MISC_RECPT_CODE', '杂项入库单编码规则', NULL, 16, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-05-15 11:06:51', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (264, 'MISC_ISSUE_CODE', '杂项出库单编码规则', NULL, 16, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-05-16 09:49:26', '', NULL);
INSERT INTO `sys_auto_code_rule` VALUES (265, 'BOM_CODE', 'BOM编码规则', NULL, 5, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, 'admin', '2025-06-20 10:07:51', '', NULL);

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `config_id` int(0) NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '参数配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
INSERT INTO `sys_config` VALUES (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '初始化密码 123456');
INSERT INTO `sys_config` VALUES (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '深色主题theme-dark，浅色主题theme-light');
INSERT INTO `sys_config` VALUES (4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '是否开启验证码功能（true开启，false关闭）');
INSERT INTO `sys_config` VALUES (5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '是否开启注册用户功能（true开启，false关闭）');
INSERT INTO `sys_config` VALUES (6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
INSERT INTO `sys_config` VALUES (7, '用户管理-初始密码修改策略', 'sys.account.initPasswordModify', '1', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
INSERT INTO `sys_config` VALUES (8, '用户管理-账号密码更新周期', 'sys.account.passwordValidateDays', '0', 'Y', 'admin', '2025-08-29 16:28:04', '', NULL, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`  (
  `dept_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint(0) NULL DEFAULT 0 COMMENT '父部门id',
  `ancestors` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '部门名称',
  `order_num` int(0) NULL DEFAULT 0 COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 109 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (100, 0, '0', '若依科技', 0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (101, 100, '0,100', '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (102, 100, '0,100', '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (103, 101, '0,100,101', '研发部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (104, 101, '0,100,101', '市场部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (105, 101, '0,100,101', '测试部门', 3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (106, 101, '0,100,101', '财务部门', 4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (107, 101, '0,100,101', '运维部门', 5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (108, 102, '0,100,102', '市场部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);
INSERT INTO `sys_dept` VALUES (109, 102, '0,100,102', '财务部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL);

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`  (
  `dict_code` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int(0) NULL DEFAULT 0 COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 111 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (1, 1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '性别男');
INSERT INTO `sys_dict_data` VALUES (2, 2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '性别女');
INSERT INTO `sys_dict_data` VALUES (3, 3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '性别未知');
INSERT INTO `sys_dict_data` VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '显示菜单');
INSERT INTO `sys_dict_data` VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '隐藏菜单');
INSERT INTO `sys_dict_data` VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '默认分组');
INSERT INTO `sys_dict_data` VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '系统分组');
INSERT INTO `sys_dict_data` VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '系统默认是');
INSERT INTO `sys_dict_data` VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '系统默认否');
INSERT INTO `sys_dict_data` VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '通知');
INSERT INTO `sys_dict_data` VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '公告');
INSERT INTO `sys_dict_data` VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '关闭状态');
INSERT INTO `sys_dict_data` VALUES (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '其他操作');
INSERT INTO `sys_dict_data` VALUES (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '新增操作');
INSERT INTO `sys_dict_data` VALUES (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '修改操作');
INSERT INTO `sys_dict_data` VALUES (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '删除操作');
INSERT INTO `sys_dict_data` VALUES (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '授权操作');
INSERT INTO `sys_dict_data` VALUES (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '导出操作');
INSERT INTO `sys_dict_data` VALUES (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '导入操作');
INSERT INTO `sys_dict_data` VALUES (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '强退操作');
INSERT INTO `sys_dict_data` VALUES (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '生成操作');
INSERT INTO `sys_dict_data` VALUES (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '清空操作');
INSERT INTO `sys_dict_data` VALUES (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (100, 1, '企业', 'ENTERPRISE', 'bd_client_type', NULL, 'primary', 'N', '0', 'admin', '2025-09-17 16:19:28', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (101, 2, '个人', 'PERSON', 'bd_client_type', NULL, 'success', 'N', '0', 'admin', '2025-09-17 16:19:56', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (102, 1, '输入字符', 'INPUTCHAR', 'sys_autocode_parttype', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:41:21', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (103, 2, '当前日期时间', 'NOWDATE', 'sys_autocode_parttype', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:41:38', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (104, 3, '固定字符', 'FIXCHAR', 'sys_autocode_parttype', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:41:52', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (105, 4, '流水号', 'SERIALNO', 'sys_autocode_parttype', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:42:06', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (106, 1, '按年', 'YEAR', 'sys_autocode_cyclemethod', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:42:58', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (107, 2, '按月', 'MONTH', 'sys_autocode_cyclemethod', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:43:10', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (108, 3, '按天', 'DAY', 'sys_autocode_cyclemethod', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:43:29', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (109, 4, '按小时', 'HOUR', 'sys_autocode_cyclemethod', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:46:37', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (110, 4, '按分钟', 'MINUTE', 'sys_autocode_cyclemethod', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:46:54', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (111, 5, '按传入字符', 'OTHER', 'sys_autocode_cyclemethod', NULL, 'default', 'N', '0', 'admin', '2025-09-18 16:47:07', '', NULL, NULL);

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type`  (
  `dict_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '字典类型',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE INDEX `dict_type`(`dict_type`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 102 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典类型表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '用户性别列表');
INSERT INTO `sys_dict_type` VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '菜单状态列表');
INSERT INTO `sys_dict_type` VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '系统开关列表');
INSERT INTO `sys_dict_type` VALUES (4, '任务状态', 'sys_job_status', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '任务状态列表');
INSERT INTO `sys_dict_type` VALUES (5, '任务分组', 'sys_job_group', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '任务分组列表');
INSERT INTO `sys_dict_type` VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '系统是否列表');
INSERT INTO `sys_dict_type` VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '通知类型列表');
INSERT INTO `sys_dict_type` VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '通知状态列表');
INSERT INTO `sys_dict_type` VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '操作类型列表');
INSERT INTO `sys_dict_type` VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '登录状态列表');
INSERT INTO `sys_dict_type` VALUES (100, '客户类型', 'bd_client_type', '0', 'admin', '2025-09-17 16:18:21', '', NULL, '客户类型');
INSERT INTO `sys_dict_type` VALUES (101, '编码生成规则', 'sys_autocode_parttype', '0', 'admin', '2025-09-18 16:40:52', '', NULL, '编码生成规则');
INSERT INTO `sys_dict_type` VALUES (102, '编码规则组成循环方式', 'sys_autocode_cyclemethod', '0', 'admin', '2025-09-18 16:42:34', '', NULL, '编码规则组成循环方式');

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job`  (
  `job_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`, `job_name`, `job_group`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '定时任务调度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_job
-- ----------------------------
INSERT INTO `sys_job` VALUES (1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams', '0/10 * * * * ?', '3', '1', '1', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_job` VALUES (2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')', '0/15 * * * * ?', '3', '1', '1', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_job` VALUES (3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)', '0/20 * * * * ?', '3', '1', '1', 'admin', '2025-08-29 16:28:04', '', NULL, '');

-- ----------------------------
-- Table structure for sys_job_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log`  (
  `job_log_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '日志信息',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '异常信息',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '定时任务调度日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_logininfor
-- ----------------------------
DROP TABLE IF EXISTS `sys_logininfor`;
CREATE TABLE `sys_logininfor`  (
  `info_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作系统',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '提示消息',
  `login_time` datetime(0) NULL DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`) USING BTREE,
  INDEX `idx_sys_logininfor_s`(`status`) USING BTREE,
  INDEX `idx_sys_logininfor_lt`(`login_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 181 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统访问记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_logininfor
-- ----------------------------
INSERT INTO `sys_logininfor` VALUES (100, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-01 15:31:37');
INSERT INTO `sys_logininfor` VALUES (101, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 10:38:44');
INSERT INTO `sys_logininfor` VALUES (102, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 14:35:41');
INSERT INTO `sys_logininfor` VALUES (103, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:09:40');
INSERT INTO `sys_logininfor` VALUES (104, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:46:15');
INSERT INTO `sys_logininfor` VALUES (105, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-09-17 16:47:09');
INSERT INTO `sys_logininfor` VALUES (106, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:47:14');
INSERT INTO `sys_logininfor` VALUES (107, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:48:44');
INSERT INTO `sys_logininfor` VALUES (108, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:48:58');
INSERT INTO `sys_logininfor` VALUES (109, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-09-17 16:49:46');
INSERT INTO `sys_logininfor` VALUES (110, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:49:50');
INSERT INTO `sys_logininfor` VALUES (111, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:52:30');
INSERT INTO `sys_logininfor` VALUES (112, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:54:04');
INSERT INTO `sys_logininfor` VALUES (113, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 16:54:42');
INSERT INTO `sys_logininfor` VALUES (114, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 17:03:24');
INSERT INTO `sys_logininfor` VALUES (115, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 17:06:06');
INSERT INTO `sys_logininfor` VALUES (116, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 17:07:32');
INSERT INTO `sys_logininfor` VALUES (117, 'admin123', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '用户不存在/密码错误', '2025-09-17 17:08:06');
INSERT INTO `sys_logininfor` VALUES (118, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 17:08:15');
INSERT INTO `sys_logininfor` VALUES (119, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-09-17 17:10:33');
INSERT INTO `sys_logininfor` VALUES (120, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-17 17:10:36');
INSERT INTO `sys_logininfor` VALUES (121, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-18 09:56:44');
INSERT INTO `sys_logininfor` VALUES (122, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-18 10:57:36');
INSERT INTO `sys_logininfor` VALUES (123, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-18 11:16:08');
INSERT INTO `sys_logininfor` VALUES (124, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-18 12:00:04');
INSERT INTO `sys_logininfor` VALUES (125, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-18 14:45:11');
INSERT INTO `sys_logininfor` VALUES (126, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-18 15:31:26');
INSERT INTO `sys_logininfor` VALUES (127, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-18 15:38:24');
INSERT INTO `sys_logininfor` VALUES (128, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 10:30:11');
INSERT INTO `sys_logininfor` VALUES (129, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 10:30:26');
INSERT INTO `sys_logininfor` VALUES (130, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:00:32');
INSERT INTO `sys_logininfor` VALUES (131, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-09-19 11:02:23');
INSERT INTO `sys_logininfor` VALUES (132, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:02:26');
INSERT INTO `sys_logininfor` VALUES (133, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:04:08');
INSERT INTO `sys_logininfor` VALUES (134, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:04:39');
INSERT INTO `sys_logininfor` VALUES (135, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-09-19 11:09:22');
INSERT INTO `sys_logininfor` VALUES (136, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:09:26');
INSERT INTO `sys_logininfor` VALUES (137, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:21:06');
INSERT INTO `sys_logininfor` VALUES (138, 'admina', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:23:30');
INSERT INTO `sys_logininfor` VALUES (139, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:24:15');
INSERT INTO `sys_logininfor` VALUES (140, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-09-19 11:27:44');
INSERT INTO `sys_logininfor` VALUES (141, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:27:48');
INSERT INTO `sys_logininfor` VALUES (142, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:38:12');
INSERT INTO `sys_logininfor` VALUES (143, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:38:25');
INSERT INTO `sys_logininfor` VALUES (144, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:39:03');
INSERT INTO `sys_logininfor` VALUES (145, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:39:30');
INSERT INTO `sys_logininfor` VALUES (146, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:42:55');
INSERT INTO `sys_logininfor` VALUES (147, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:43:13');
INSERT INTO `sys_logininfor` VALUES (148, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-09-19 11:52:59');
INSERT INTO `sys_logininfor` VALUES (149, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:53:07');
INSERT INTO `sys_logininfor` VALUES (150, 'ry', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:54:15');
INSERT INTO `sys_logininfor` VALUES (151, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码已失效', '2025-09-19 11:58:18');
INSERT INTO `sys_logininfor` VALUES (152, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 11:58:22');
INSERT INTO `sys_logininfor` VALUES (153, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 12:01:55');
INSERT INTO `sys_logininfor` VALUES (154, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 12:07:50');
INSERT INTO `sys_logininfor` VALUES (155, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 12:08:26');
INSERT INTO `sys_logininfor` VALUES (156, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 12:11:31');
INSERT INTO `sys_logininfor` VALUES (157, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 14:41:14');
INSERT INTO `sys_logininfor` VALUES (158, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-09-19 15:44:06');
INSERT INTO `sys_logininfor` VALUES (159, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:44:09');
INSERT INTO `sys_logininfor` VALUES (160, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:44:30');
INSERT INTO `sys_logininfor` VALUES (161, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:49:26');
INSERT INTO `sys_logininfor` VALUES (162, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:49:37');
INSERT INTO `sys_logininfor` VALUES (163, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:52:50');
INSERT INTO `sys_logininfor` VALUES (164, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:53:03');
INSERT INTO `sys_logininfor` VALUES (165, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-09-19 15:53:49');
INSERT INTO `sys_logininfor` VALUES (166, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:53:57');
INSERT INTO `sys_logininfor` VALUES (167, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 15:56:47');
INSERT INTO `sys_logininfor` VALUES (168, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:03:26');
INSERT INTO `sys_logininfor` VALUES (169, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码已失效', '2025-09-19 16:07:58');
INSERT INTO `sys_logininfor` VALUES (170, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:08:03');
INSERT INTO `sys_logininfor` VALUES (171, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:10:22');
INSERT INTO `sys_logininfor` VALUES (172, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:29:12');
INSERT INTO `sys_logininfor` VALUES (173, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:31:16');
INSERT INTO `sys_logininfor` VALUES (174, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:37:41');
INSERT INTO `sys_logininfor` VALUES (175, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:42:49');
INSERT INTO `sys_logininfor` VALUES (176, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:44:14');
INSERT INTO `sys_logininfor` VALUES (177, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:47:16');
INSERT INTO `sys_logininfor` VALUES (178, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:50:23');
INSERT INTO `sys_logininfor` VALUES (179, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:53:13');
INSERT INTO `sys_logininfor` VALUES (180, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 16:56:53');
INSERT INTO `sys_logininfor` VALUES (181, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-09-19 17:01:10');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `menu_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint(0) NULL DEFAULT 0 COMMENT '父菜单ID',
  `order_num` int(0) NULL DEFAULT 0 COMMENT '显示顺序',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '路由名称',
  `is_frame` int(0) NULL DEFAULT 1 COMMENT '是否为外链（0是 1否）',
  `is_cache` int(0) NULL DEFAULT 0 COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2030 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, '系统管理', 0, 1, 'system', NULL, '', '', 1, 0, 'M', '0', '0', '', 'system', 'admin', '2025-08-29 16:28:04', '', NULL, '系统管理目录');
INSERT INTO `sys_menu` VALUES (2, '系统监控', 0, 2, 'monitor', NULL, '', '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', '2025-08-29 16:28:04', '', NULL, '系统监控目录');
INSERT INTO `sys_menu` VALUES (3, '系统工具', 0, 3, 'tool', NULL, '', '', 1, 0, 'M', '0', '0', '', 'tool', 'admin', '2025-08-29 16:28:04', '', NULL, '系统工具目录');
INSERT INTO `sys_menu` VALUES (4, '若依官网', 0, 4, 'http://ruoyi.vip', NULL, '', '', 0, 0, 'M', '0', '1', '', 'guide', 'admin', '2025-08-29 16:28:04', 'admin', '2025-09-17 14:42:13', '若依官网地址');
INSERT INTO `sys_menu` VALUES (100, '用户管理', 1, 1, 'user', 'system/user/index', '', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', '2025-08-29 16:28:04', '', NULL, '用户管理菜单');
INSERT INTO `sys_menu` VALUES (101, '角色管理', 1, 2, 'role', 'system/role/index', '', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', '2025-08-29 16:28:04', '', NULL, '角色管理菜单');
INSERT INTO `sys_menu` VALUES (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', '2025-08-29 16:28:04', '', NULL, '菜单管理菜单');
INSERT INTO `sys_menu` VALUES (103, '部门管理', 1, 4, 'dept', 'system/dept/index', '', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', '2025-08-29 16:28:04', '', NULL, '部门管理菜单');
INSERT INTO `sys_menu` VALUES (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', '2025-08-29 16:28:04', '', NULL, '岗位管理菜单');
INSERT INTO `sys_menu` VALUES (105, '字典管理', 1, 6, 'dict', 'system/dict/index', '', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', '2025-08-29 16:28:04', '', NULL, '字典管理菜单');
INSERT INTO `sys_menu` VALUES (106, '参数设置', 1, 7, 'config', 'system/config/index', '', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', '2025-08-29 16:28:04', '', NULL, '参数设置菜单');
INSERT INTO `sys_menu` VALUES (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', '', 1, 0, 'C', '0', '0', 'system:notice:list', 'message', 'admin', '2025-08-29 16:28:04', '', NULL, '通知公告菜单');
INSERT INTO `sys_menu` VALUES (108, '日志管理', 1, 9, 'log', '', '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', '2025-08-29 16:28:04', '', NULL, '日志管理菜单');
INSERT INTO `sys_menu` VALUES (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', '2025-08-29 16:28:04', '', NULL, '在线用户菜单');
INSERT INTO `sys_menu` VALUES (110, '定时任务', 2, 2, 'job', 'monitor/job/index', '', '', 1, 0, 'C', '0', '0', 'monitor:job:list', 'job', 'admin', '2025-08-29 16:28:04', '', NULL, '定时任务菜单');
INSERT INTO `sys_menu` VALUES (111, '数据监控', 2, 3, 'druid', 'monitor/druid/index', '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list', 'druid', 'admin', '2025-08-29 16:28:04', '', NULL, '数据监控菜单');
INSERT INTO `sys_menu` VALUES (112, '服务监控', 2, 4, 'server', 'monitor/server/index', '', '', 1, 0, 'C', '0', '0', 'monitor:server:list', 'server', 'admin', '2025-08-29 16:28:04', '', NULL, '服务监控菜单');
INSERT INTO `sys_menu` VALUES (113, '缓存监控', 2, 5, 'cache', 'monitor/cache/index', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis', 'admin', '2025-08-29 16:28:04', '', NULL, '缓存监控菜单');
INSERT INTO `sys_menu` VALUES (114, '缓存列表', 2, 6, 'cacheList', 'monitor/cache/list', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis-list', 'admin', '2025-08-29 16:28:04', '', NULL, '缓存列表菜单');
INSERT INTO `sys_menu` VALUES (115, '表单构建', 3, 1, 'build', 'tool/build/index', '', '', 1, 0, 'C', '0', '0', 'tool:build:list', 'build', 'admin', '2025-08-29 16:28:04', '', NULL, '表单构建菜单');
INSERT INTO `sys_menu` VALUES (116, '代码生成', 3, 2, 'gen', 'tool/gen/index', '', '', 1, 0, 'C', '0', '0', 'tool:gen:list', 'code', 'admin', '2025-08-29 16:28:04', '', NULL, '代码生成菜单');
INSERT INTO `sys_menu` VALUES (117, '系统接口', 3, 3, 'swagger', 'tool/swagger/index', '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list', 'swagger', 'admin', '2025-08-29 16:28:04', '', NULL, '系统接口菜单');
INSERT INTO `sys_menu` VALUES (500, '操作日志', 108, 1, 'operlog', 'monitor/operlog/index', '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list', 'form', 'admin', '2025-08-29 16:28:04', '', NULL, '操作日志菜单');
INSERT INTO `sys_menu` VALUES (501, '登录日志', 108, 2, 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', 'admin', '2025-08-29 16:28:04', '', NULL, '登录日志菜单');
INSERT INTO `sys_menu` VALUES (1000, '用户查询', 100, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1001, '用户新增', 100, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1002, '用户修改', 100, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1003, '用户删除', 100, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1004, '用户导出', 100, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1005, '用户导入', 100, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1006, '重置密码', 100, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1007, '角色查询', 101, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1008, '角色新增', 101, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1009, '角色修改', 101, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1010, '角色删除', 101, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1011, '角色导出', 101, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1012, '菜单查询', 102, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1013, '菜单新增', 102, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1014, '菜单修改', 102, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1015, '菜单删除', 102, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1016, '部门查询', 103, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1017, '部门新增', 103, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1018, '部门修改', 103, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1019, '部门删除', 103, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1020, '岗位查询', 104, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1021, '岗位新增', 104, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1022, '岗位修改', 104, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1023, '岗位删除', 104, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1024, '岗位导出', 104, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1025, '字典查询', 105, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1026, '字典新增', 105, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1027, '字典修改', 105, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1028, '字典删除', 105, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1029, '字典导出', 105, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1030, '参数查询', 106, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1031, '参数新增', 106, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1032, '参数修改', 106, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1033, '参数删除', 106, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1034, '参数导出', 106, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1035, '公告查询', 107, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1036, '公告新增', 107, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1037, '公告修改', 107, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1038, '公告删除', 107, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1039, '操作查询', 500, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1040, '操作删除', 500, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1041, '日志导出', 500, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1042, '登录查询', 501, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1043, '登录删除', 501, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1044, '日志导出', 501, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1045, '账户解锁', 501, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1046, '在线查询', 109, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1047, '批量强退', 109, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1048, '单条强退', 109, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1049, '任务查询', 110, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1050, '任务新增', 110, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1051, '任务修改', 110, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1052, '任务删除', 110, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1053, '状态修改', 110, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1054, '任务导出', 110, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1055, '生成查询', 116, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1056, '生成修改', 116, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1057, '生成删除', 116, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1058, '导入代码', 116, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1059, '预览代码', 116, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1060, '生成代码', 116, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code', '#', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2007, '主数据', 0, 4, 'core.bd', NULL, NULL, '', 1, 0, 'M', '0', '0', NULL, 'build', 'ry', '2025-09-17 17:10:27', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2014, '客户信息', 2007, 1, 'bd', 'core/bd/index', NULL, '', 1, 0, 'C', '0', '0', 'core:bd:list', 'peoples', 'admin', '2025-09-17 17:34:02', 'admin', '2025-09-19 17:30:26', '客户信息菜单');
INSERT INTO `sys_menu` VALUES (2015, '客户信息查询', 2014, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'core:bd:query', '#', 'admin', '2025-09-17 17:34:02', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2016, '客户信息新增', 2014, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'core:bd:add', '#', 'admin', '2025-09-17 17:34:02', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2017, '客户信息修改', 2014, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'core:bd:edit', '#', 'admin', '2025-09-17 17:34:02', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2018, '客户信息删除', 2014, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'core:bd:remove', '#', 'admin', '2025-09-17 17:34:02', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2019, '客户信息导出', 2014, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'core:bd:export', '#', 'admin', '2025-09-17 17:34:02', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2026, '编码规则', 1, 10, 'autocodeRule', 'system/autocode/index', NULL, '', 1, 0, 'C', '0', '0', 'system:autocode:rule', 'code', 'ry', '2025-09-19 11:08:13', 'admin', '2025-09-19 17:03:56', '');
INSERT INTO `sys_menu` VALUES (2027, '编码规则查询', 2026, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'system:autocode:rule:list', '#', 'admin', '2025-09-17 21:46:05', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2028, '编码规则新增', 2026, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'system:autocode:rule:add', '#', 'admin', '2025-09-17 21:46:30', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2029, '编码规则更新', 2026, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'system:autocode:rule:edit', '#', 'admin', '2025-09-17 21:46:59', 'admin', '2025-09-19 21:47:42', '');
INSERT INTO `sys_menu` VALUES (2030, '编码规则删除', 2026, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'system:autocode:rule:remove', '#', 'admin', '2025-09-17 21:47:20', '', NULL, '');

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice`  (
  `notice_id` int(0) NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob NULL COMMENT '公告内容',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知公告表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
INSERT INTO `sys_notice` VALUES (1, '温馨提醒：2018-07-01 若依新版本发布啦', '2', 0xE696B0E78988E69CACE58685E5AEB9, '0', 'admin', '2025-08-29 16:28:04', '', NULL, '管理员');
INSERT INTO `sys_notice` VALUES (2, '维护通知：2018-07-01 若依系统凌晨维护', '1', 0xE7BBB4E68AA4E58685E5AEB9, '0', 'admin', '2025-08-29 16:28:04', '', NULL, '管理员');

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log`  (
  `oper_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '模块标题',
  `business_type` int(0) NULL DEFAULT 0 COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '请求方式',
  `operator_type` int(0) NULL DEFAULT 0 COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '返回参数',
  `status` int(0) NULL DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime(0) NULL DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint(0) NULL DEFAULT 0 COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`) USING BTREE,
  INDEX `idx_sys_oper_log_bt`(`business_type`) USING BTREE,
  INDEX `idx_sys_oper_log_s`(`status`) USING BTREE,
  INDEX `idx_sys_oper_log_ot`(`oper_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 176 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作日志记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------
INSERT INTO `sys_oper_log` VALUES (100, '菜单管理', 1, 'com.nexus.web.controller.system.SysMenuController.add()', 'POST', 1, 'admin', '研发部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"createBy\":\"admin\",\"icon\":\"build\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuName\":\"主数据\",\"menuType\":\"M\",\"orderNum\":1,\"params\":{},\"parentId\":0,\"path\":\"bd\",\"status\":\"0\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 14:41:05', 24);
INSERT INTO `sys_oper_log` VALUES (101, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/4', '127.0.0.1', '内网IP', '4', '{\"msg\":\"菜单已分配,不允许删除\",\"code\":601}', 0, NULL, '2025-09-17 14:41:38', 8);
INSERT INTO `sys_oper_log` VALUES (102, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/4', '127.0.0.1', '内网IP', '4', '{\"msg\":\"菜单已分配,不允许删除\",\"code\":601}', 0, NULL, '2025-09-17 14:41:57', 5);
INSERT INTO `sys_oper_log` VALUES (103, '菜单管理', 2, 'com.nexus.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"createTime\":\"2025-08-29 16:28:04\",\"icon\":\"guide\",\"isCache\":\"0\",\"isFrame\":\"0\",\"menuId\":4,\"menuName\":\"若依官网\",\"menuType\":\"M\",\"orderNum\":4,\"params\":{},\"parentId\":0,\"path\":\"http://ruoyi.vip\",\"perms\":\"\",\"query\":\"\",\"routeName\":\"\",\"status\":\"1\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 14:42:13', 7);
INSERT INTO `sys_oper_log` VALUES (104, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/4', '127.0.0.1', '内网IP', '4', '{\"msg\":\"菜单已分配,不允许删除\",\"code\":601}', 0, NULL, '2025-09-17 14:42:15', 5);
INSERT INTO `sys_oper_log` VALUES (105, '菜单管理', 2, 'com.nexus.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"createTime\":\"2025-09-17 14:41:05\",\"icon\":\"build\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2000,\"menuName\":\"主数据\",\"menuType\":\"M\",\"orderNum\":4,\"params\":{},\"parentId\":0,\"path\":\"bd\",\"perms\":\"\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 14:42:25', 7);
INSERT INTO `sys_oper_log` VALUES (106, '代码生成', 6, 'com.nexus.generator.controller.GenController.importTableSave()', 'POST', 1, 'admin', '研发部门', '/tool/gen/importTable', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 14:45:31', 96);
INSERT INTO `sys_oper_log` VALUES (107, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":1,\"columnName\":\"client_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 14:45:31\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":true,\"isIncrement\":\"1\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":1,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":2,\"columnName\":\"client_code\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 14:45:31\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":1,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":3,\"columnName\":\"client_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 14:45:31\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":1,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"CreditLevel\",\"columnComment\":\"客户分级 (战略/普通)\",\"columnId\":4,\"columnName\":\"credit_level\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 14:45:31\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 14:47:18', 59);
INSERT INTO `sys_oper_log` VALUES (108, '代码生成', 3, 'com.nexus.generator.controller.GenController.remove()', 'DELETE', 1, 'admin', '研发部门', '/tool/gen/1', '127.0.0.1', '内网IP', '[1]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:09:47', 24);
INSERT INTO `sys_oper_log` VALUES (109, '代码生成', 6, 'com.nexus.generator.controller.GenController.importTableSave()', 'POST', 1, 'admin', '研发部门', '/tool/gen/importTable', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client_credit,bd_client\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:09:52', 123);
INSERT INTO `sys_oper_log` VALUES (110, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQu', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:13:25', 51);
INSERT INTO `sys_oper_log` VALUES (111, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:13:25\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:13:25\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:13:25\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:17:26', 44);
INSERT INTO `sys_oper_log` VALUES (112, '字典类型', 1, 'com.nexus.web.controller.system.SysDictTypeController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/type', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"dictName\":\"客户类型\",\"dictType\":\"bd_client_type\",\"params\":{},\"remark\":\"客户类型\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:18:21', 25);
INSERT INTO `sys_oper_log` VALUES (113, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"企业\",\"dictSort\":1,\"dictType\":\"bd_client_type\",\"dictValue\":\"ENTERPRISE\",\"listClass\":\"primary\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:19:28', 8);
INSERT INTO `sys_oper_log` VALUES (114, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"个人\",\"dictSort\":2,\"dictType\":\"bd_client_type\",\"dictValue\":\"PERSON\",\"listClass\":\"success\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:19:56', 7);
INSERT INTO `sys_oper_log` VALUES (115, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:17:26\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:17:26\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:17:26\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 16:20:39', 38);
INSERT INTO `sys_oper_log` VALUES (116, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', NULL, 0, NULL, '2025-09-17 16:20:42', 87);
INSERT INTO `sys_oper_log` VALUES (117, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'ry', '测试部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:20:39\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:20:39\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 16:20:39\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:09:02', 89);
INSERT INTO `sys_oper_log` VALUES (118, '菜单管理', 1, 'com.nexus.web.controller.system.SysMenuController.add()', 'POST', 1, 'ry', '测试部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"icon\":\"build\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuName\":\"主数据\",\"menuType\":\"M\",\"orderNum\":4,\"params\":{},\"parentId\":0,\"path\":\"core.bd\",\"status\":\"0\",\"visible\":\"0\"}', '{\"msg\":\"新增菜单\'主数据\'失败，菜单名称已存在\",\"code\":500}', 0, NULL, '2025-09-17 17:09:49', 8);
INSERT INTO `sys_oper_log` VALUES (119, '菜单管理', 1, 'com.nexus.web.controller.system.SysMenuController.add()', 'POST', 1, 'ry', '测试部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"createBy\":\"ry\",\"icon\":\"build\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuName\":\"主数据\",\"menuType\":\"M\",\"orderNum\":4,\"params\":{},\"parentId\":0,\"path\":\"core.bd\",\"status\":\"0\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:10:27', 7);
INSERT INTO `sys_oper_log` VALUES (120, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:09:02\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:09:02\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:09:02\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:11:20', 64);
INSERT INTO `sys_oper_log` VALUES (121, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', NULL, 0, NULL, '2025-09-17 17:11:26', 147);
INSERT INTO `sys_oper_log` VALUES (122, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"core.bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:11:20\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:11:20\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:11:20\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"inpu', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:19:46', 85);
INSERT INTO `sys_oper_log` VALUES (123, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:19:46\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:19:46\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:19:46\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:20:21', 64);
INSERT INTO `sys_oper_log` VALUES (124, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:20:21\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:20:21\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:20:21\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:31:13', 116);
INSERT INTO `sys_oper_log` VALUES (125, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:31:12\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:31:12\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:31:12\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:31:38', 52);
INSERT INTO `sys_oper_log` VALUES (126, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:31:38\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:31:38\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:31:38\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:32:10', 49);
INSERT INTO `sys_oper_log` VALUES (127, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":21,\"columnName\":\"CLIENT_ID\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:32:10\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":22,\"columnName\":\"CLIENT_CODE\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:32:10\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":23,\"columnName\":\"CLIENT_NAME\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-09-17 17:32:10\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":24,\"columnName\":\"CLIENT_NICK\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-17 16:09:52\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-17 17:33:00', 41);
INSERT INTO `sys_oper_log` VALUES (128, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', NULL, 0, NULL, '2025-09-17 17:33:04', 46);
INSERT INTO `sys_oper_log` VALUES (129, '代码生成', 8, 'com.ruoyi.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', NULL, 0, NULL, '2025-09-18 10:58:06', 185);
INSERT INTO `sys_oper_log` VALUES (130, '代码生成', 3, 'com.nexus.generator.controller.GenController.remove()', 'DELETE', 1, 'admin', '研发部门', '/tool/gen/2', '127.0.0.1', '内网IP', '[2]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 15:01:08', 30);
INSERT INTO `sys_oper_log` VALUES (131, '代码生成', 3, 'com.nexus.generator.controller.GenController.remove()', 'DELETE', 1, 'admin', '研发部门', '/tool/gen/3', '127.0.0.1', '内网IP', '[3]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 15:01:11', 7);
INSERT INTO `sys_oper_log` VALUES (132, '代码生成', 6, 'com.nexus.generator.controller.GenController.importTableSave()', 'POST', 1, 'admin', '研发部门', '/tool/gen/importTable', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client,bd_client_credit\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 15:01:18', 149);
INSERT INTO `sys_oper_log` VALUES (133, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"bd\",\"className\":\"BdClient\",\"columns\":[{\"capJavaField\":\"ClientId\",\"columnComment\":\"客户ID\",\"columnId\":66,\"columnName\":\"client_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 15:01:17\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"clientId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":4,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientCode\",\"columnComment\":\"客户编码\",\"columnId\":67,\"columnName\":\"client_code\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 15:01:17\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":4,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientName\",\"columnComment\":\"客户名称\",\"columnId\":68,\"columnName\":\"client_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 15:01:17\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"clientName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":4,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ClientNick\",\"columnComment\":\"客户简写\",\"columnId\":69,\"columnName\":\"client_nick\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 15:01:17\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQu', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 15:04:19', 68);
INSERT INTO `sys_oper_log` VALUES (134, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', NULL, 0, NULL, '2025-09-18 15:04:24', 92);
INSERT INTO `sys_oper_log` VALUES (135, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client_credit\"}', NULL, 0, NULL, '2025-09-18 15:07:24', 166);
INSERT INTO `sys_oper_log` VALUES (136, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', NULL, 0, NULL, '2025-09-18 15:07:27', 52);
INSERT INTO `sys_oper_log` VALUES (137, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"bd_client\"}', NULL, 0, NULL, '2025-09-18 15:31:38', 189);
INSERT INTO `sys_oper_log` VALUES (138, '客户信息', 1, 'controller.com.nexus.bd.BdClientController.add()', 'POST', 1, 'admin', '研发部门', '/core/bd', '127.0.0.1', '内网IP', '{\"address\":\"123\",\"clientCode\":\"cs123\",\"clientDesc\":\"测试测试\",\"clientEn\":\"cs\",\"clientName\":\"测试\",\"clientNick\":\"测试\",\"clientType\":\"ENTERPRISE\",\"contact1\":\"123\",\"contact1Tel\":\"123\",\"createTime\":\"2025-09-18 15:40:40\",\"email\":\"123\",\"params\":{},\"tel\":\"123\"}', NULL, 1, '\r\n### Error updating database.  Cause: java.sql.SQLException: Field \'client_id\' doesn\'t have a default value\r\n### The error may exist in file [E:\\NexusERP\\nexus-core\\target\\classes\\mapper\\bd\\BdClientMapper.xml]\r\n### The error may involve mapper.com.nexus.bd.BdClientMapper.insertBdClient-Inline\r\n### The error occurred while setting parameters\r\n### SQL: insert into bd_client          ( client_code,                     client_name,                     client_nick,                     client_en,                     client_desc,                     client_type,                                          address,                                          email,                     tel,                     contact1,                     contact1_tel,                                                                                                                                                                                                                                                            create_time )           values ( ?,                     ?,                     ?,                     ?,                     ?,                     ?,                                          ?,                                          ?,                     ?,                     ?,                     ?,                                                                                                                                                                                                                                                            ? )\r\n### Cause: java.sql.SQLException: Field \'client_id\' doesn\'t have a default value\n; Field \'client_id\' doesn\'t have a default value', '2025-09-18 15:40:41', 140);
INSERT INTO `sys_oper_log` VALUES (139, '客户信息', 1, 'controller.com.nexus.bd.BdClientController.add()', 'POST', 1, 'admin', '研发部门', '/core/bd', '127.0.0.1', '内网IP', '{\"address\":\"123\",\"clientCode\":\"cs123\",\"clientDesc\":\"测试测试\",\"clientEn\":\"cs\",\"clientName\":\"测试\",\"clientNick\":\"测试\",\"clientType\":\"ENTERPRISE\",\"contact1\":\"123\",\"contact1Tel\":\"123\",\"createTime\":\"2025-09-18 15:45:43\",\"email\":\"123\",\"params\":{},\"tel\":\"123\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 15:45:43', 9);
INSERT INTO `sys_oper_log` VALUES (140, '代码生成', 6, 'com.nexus.generator.controller.GenController.importTableSave()', 'POST', 1, 'admin', '研发部门', '/tool/gen/importTable', '127.0.0.1', '内网IP', '{\"tables\":\"sys_auto_code_part,sys_auto_code_result,sys_auto_code_rule\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:33:33', 178);
INSERT INTO `sys_oper_log` VALUES (141, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"system\",\"className\":\"SysAutoCodePart\",\"columns\":[{\"capJavaField\":\"PartId\",\"columnComment\":\"分段ID\",\"columnId\":111,\"columnName\":\"part_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":true,\"isIncrement\":\"1\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"partId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":6,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"RuleId\",\"columnComment\":\"规则ID\",\"columnId\":112,\"columnName\":\"rule_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"ruleId\",\"javaType\":\"Long\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":6,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"PartIndex\",\"columnComment\":\"分段序号\",\"columnId\":113,\"columnName\":\"part_index\",\"columnType\":\"int\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"partIndex\",\"javaType\":\"Long\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":6,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"PartType\",\"columnComment\":\"分段类型，INPUTCHAR：输入字符，NOWDATE：当前日期时间，FIXCHAR：固定字符，SERIALNO：流水号\",\"columnId\":114,\"columnName\":\"part_type\",\"columnType\":\"varchar(20)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"select\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isL', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:39:04', 51);
INSERT INTO `sys_oper_log` VALUES (142, '字典类型', 1, 'com.nexus.web.controller.system.SysDictTypeController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/type', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"dictName\":\"编码生成规则\",\"dictType\":\"sys_autocode_parttype\",\"params\":{},\"remark\":\"编码生成规则\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:40:52', 10);
INSERT INTO `sys_oper_log` VALUES (143, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"输入字符\",\"dictSort\":1,\"dictType\":\"sys_autocode_parttype\",\"dictValue\":\"INPUTCHAR\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:41:21', 17);
INSERT INTO `sys_oper_log` VALUES (144, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"当前日期时间\",\"dictSort\":2,\"dictType\":\"sys_autocode_parttype\",\"dictValue\":\"NOWDATE\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:41:38', 7);
INSERT INTO `sys_oper_log` VALUES (145, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"固定字符\",\"dictSort\":3,\"dictType\":\"sys_autocode_parttype\",\"dictValue\":\"FIXCHAR\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:41:52', 6);
INSERT INTO `sys_oper_log` VALUES (146, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"流水号\",\"dictSort\":4,\"dictType\":\"sys_autocode_parttype\",\"dictValue\":\"SERIALNO\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:42:06', 16);
INSERT INTO `sys_oper_log` VALUES (147, '字典类型', 1, 'com.nexus.web.controller.system.SysDictTypeController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/type', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"dictName\":\"编码规则组成循环方式\",\"dictType\":\"sys_autocode_cyclemethod\",\"params\":{},\"remark\":\"编码规则组成循环方式\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:42:34', 17);
INSERT INTO `sys_oper_log` VALUES (148, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"按年\",\"dictSort\":1,\"dictType\":\"sys_autocode_cyclemethod\",\"dictValue\":\"YEAR\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:42:58', 17);
INSERT INTO `sys_oper_log` VALUES (149, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"按月\",\"dictSort\":2,\"dictType\":\"sys_autocode_cyclemethod\",\"dictValue\":\"MONTH\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:43:10', 10);
INSERT INTO `sys_oper_log` VALUES (150, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"按天\",\"dictSort\":3,\"dictType\":\"sys_autocode_cyclemethod\",\"dictValue\":\"DAY\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:43:29', 16);
INSERT INTO `sys_oper_log` VALUES (151, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"按小时\",\"dictSort\":4,\"dictType\":\"sys_autocode_cyclemethod\",\"dictValue\":\"HOUR\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:46:37', 17);
INSERT INTO `sys_oper_log` VALUES (152, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"按分钟\",\"dictSort\":4,\"dictType\":\"sys_autocode_cyclemethod\",\"dictValue\":\"MINUTE\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:46:54', 15);
INSERT INTO `sys_oper_log` VALUES (153, '字典数据', 1, 'com.nexus.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"按传入字符\",\"dictSort\":5,\"dictType\":\"sys_autocode_cyclemethod\",\"dictValue\":\"OTHER\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:47:07', 19);
INSERT INTO `sys_oper_log` VALUES (154, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"system\",\"className\":\"SysAutoCodeResult\",\"columns\":[{\"capJavaField\":\"CodeId\",\"columnComment\":\"记录ID\",\"columnId\":135,\"columnName\":\"code_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":true,\"isIncrement\":\"1\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"codeId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":7,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"RuleId\",\"columnComment\":\"规则ID\",\"columnId\":136,\"columnName\":\"rule_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"ruleId\",\"javaType\":\"Long\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":7,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"GenDate\",\"columnComment\":\"生成日期时间\",\"columnId\":137,\"columnName\":\"gen_date\",\"columnType\":\"varchar(20)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"genDate\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":7,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"GenIndex\",\"columnComment\":\"最后产生的序号\",\"columnId\":138,\"columnName\":\"gen_index\",\"columnType\":\"int\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"0\",\"ja', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:48:11', 36);
INSERT INTO `sys_oper_log` VALUES (155, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"system\",\"className\":\"SysAutoCodeRule\",\"columns\":[{\"capJavaField\":\"RuleId\",\"columnComment\":\"规则ID\",\"columnId\":151,\"columnName\":\"rule_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":true,\"isIncrement\":\"1\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"ruleId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":8,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"RuleCode\",\"columnComment\":\"规则编码\",\"columnId\":152,\"columnName\":\"rule_code\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"ruleCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":8,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"RuleName\",\"columnComment\":\"规则名称\",\"columnId\":153,\"columnName\":\"rule_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"ruleName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":8,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"RuleDesc\",\"columnComment\":\"描述\",\"columnId\":154,\"columnName\":\"rule_desc\",\"columnType\":\"varchar(500)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"textarea\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:49:33', 31);
INSERT INTO `sys_oper_log` VALUES (156, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"system\",\"className\":\"SysAutoCodeResult\",\"columns\":[{\"capJavaField\":\"CodeId\",\"columnComment\":\"记录ID\",\"columnId\":135,\"columnName\":\"code_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":true,\"isIncrement\":\"1\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"codeId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":7,\"updateBy\":\"\",\"updateTime\":\"2025-09-18 16:48:11\",\"usableColumn\":false},{\"capJavaField\":\"RuleId\",\"columnComment\":\"规则ID\",\"columnId\":136,\"columnName\":\"rule_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"ruleId\",\"javaType\":\"Long\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":7,\"updateBy\":\"\",\"updateTime\":\"2025-09-18 16:48:11\",\"usableColumn\":false},{\"capJavaField\":\"GenDate\",\"columnComment\":\"生成日期时间\",\"columnId\":137,\"columnName\":\"gen_date\",\"columnType\":\"varchar(20)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"genDate\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":7,\"updateBy\":\"\",\"updateTime\":\"2025-09-18 16:48:11\",\"usableColumn\":false},{\"capJavaField\":\"GenIndex\",\"columnComment\":\"最后产生的序号\",\"columnId\":138,\"columnName\":\"gen_index\",\"columnType\":\"int\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-18 16:49:43', 27);
INSERT INTO `sys_oper_log` VALUES (157, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"sys_auto_code_part\"}', NULL, 0, NULL, '2025-09-18 16:51:20', 89);
INSERT INTO `sys_oper_log` VALUES (158, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"sys_auto_code_result\"}', NULL, 0, NULL, '2025-09-18 16:54:12', 36);
INSERT INTO `sys_oper_log` VALUES (159, '代码生成', 8, 'com.nexus.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"sys_auto_code_rule\"}', NULL, 0, NULL, '2025-09-18 16:54:20', 39);
INSERT INTO `sys_oper_log` VALUES (160, '生成业务编号', 1, 'com.nexus.system.strategy.AutoCodeUtil.genSerialCode()', 'GET', 1, 'admin', '研发部门', '/system/autocode/get/CLIENT_CODE', '127.0.0.1', '内网IP', '{}', '\"C00204\"', 0, NULL, '2025-09-19 10:43:13', 106);
INSERT INTO `sys_oper_log` VALUES (161, '菜单管理', 1, 'com.nexus.web.controller.system.SysMenuController.add()', 'POST', 1, 'ry', '测试部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"system/autocode/index\",\"createBy\":\"ry\",\"icon\":\"code\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuName\":\"编码规则\",\"menuType\":\"C\",\"orderNum\":10,\"params\":{},\"parentId\":1,\"path\":\"autocodeRule\",\"perms\":\"system:autocode:rule\",\"routeName\":\"\",\"status\":\"0\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 11:08:13', 22);
INSERT INTO `sys_oper_log` VALUES (162, '角色管理', 2, 'com.nexus.web.controller.system.SysRoleController.dataScope()', 'PUT', 1, 'ry', '测试部门', '/system/role/dataScope', '127.0.0.1', '内网IP', '{\"admin\":false,\"createTime\":\"2025-08-29 16:28:04\",\"dataScope\":\"1\",\"delFlag\":\"0\",\"deptCheckStrictly\":true,\"deptIds\":[],\"flag\":false,\"menuCheckStrictly\":true,\"params\":{},\"remark\":\"普通角色\",\"roleId\":2,\"roleKey\":\"common\",\"roleName\":\"普通角色\",\"roleSort\":2,\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 11:24:45', 35);
INSERT INTO `sys_oper_log` VALUES (163, '用户管理', 1, 'com.nexus.web.controller.system.SysUserController.add()', 'POST', 1, 'ry', '测试部门', '/system/user', '127.0.0.1', '内网IP', '{\"admin\":false,\"createBy\":\"ry\",\"deptId\":103,\"nickName\":\"xiaowang\",\"params\":{},\"postIds\":[],\"roleIds\":[2],\"status\":\"0\",\"userId\":100,\"userName\":\"xiaowang\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 11:44:44', 91);
INSERT INTO `sys_oper_log` VALUES (164, '角色管理', 2, 'com.nexus.web.controller.system.SysRoleController.dataScope()', 'PUT', 1, 'ry', '测试部门', '/system/role/dataScope', '127.0.0.1', '内网IP', '{\"admin\":false,\"createTime\":\"2025-08-29 16:28:04\",\"dataScope\":\"2\",\"delFlag\":\"0\",\"deptCheckStrictly\":true,\"deptIds\":[100,101,103,104,105,106,107,102,108,109],\"flag\":false,\"menuCheckStrictly\":true,\"params\":{},\"remark\":\"普通角色\",\"roleId\":2,\"roleKey\":\"common\",\"roleName\":\"普通角色\",\"roleSort\":2,\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 11:45:23', 10);
INSERT INTO `sys_oper_log` VALUES (165, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/2021', '127.0.0.1', '内网IP', '2021', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 16:32:32', 25);
INSERT INTO `sys_oper_log` VALUES (166, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/2022', '127.0.0.1', '内网IP', '2022', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 16:32:35', 7);
INSERT INTO `sys_oper_log` VALUES (167, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/2023', '127.0.0.1', '内网IP', '2023', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 16:32:36', 16);
INSERT INTO `sys_oper_log` VALUES (168, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/2024', '127.0.0.1', '内网IP', '2024', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 16:32:38', 15);
INSERT INTO `sys_oper_log` VALUES (169, '菜单管理', 3, 'com.nexus.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/menu/2025', '127.0.0.1', '内网IP', '2025', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 16:32:40', 6);
INSERT INTO `sys_oper_log` VALUES (170, '代码生成', 2, 'com.nexus.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"system\",\"className\":\"SysAutoCodeRule\",\"columns\":[{\"capJavaField\":\"RuleId\",\"columnComment\":\"规则ID\",\"columnId\":151,\"columnName\":\"rule_id\",\"columnType\":\"bigint\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":true,\"isIncrement\":\"1\",\"isInsert\":\"1\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"ruleId\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":8,\"updateBy\":\"\",\"updateTime\":\"2025-09-18 16:49:33\",\"usableColumn\":false},{\"capJavaField\":\"RuleCode\",\"columnComment\":\"规则编码\",\"columnId\":152,\"columnName\":\"rule_code\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"ruleCode\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":8,\"updateBy\":\"\",\"updateTime\":\"2025-09-18 16:49:33\",\"usableColumn\":false},{\"capJavaField\":\"RuleName\",\"columnComment\":\"规则名称\",\"columnId\":153,\"columnName\":\"rule_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"ruleName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":8,\"updateBy\":\"\",\"updateTime\":\"2025-09-18 16:49:33\",\"usableColumn\":false},{\"capJavaField\":\"RuleDesc\",\"columnComment\":\"描述\",\"columnId\":154,\"columnName\":\"rule_desc\",\"columnType\":\"varchar(500)\",\"createBy\":\"admin\",\"createTime\":\"2025-09-18 16:33:32\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"textarea\",\"incremen', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 16:39:25', 55);
INSERT INTO `sys_oper_log` VALUES (171, '菜单管理', 2, 'com.nexus.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"system/autocode/index\",\"createTime\":\"2025-09-19 11:08:13\",\"icon\":\"code\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2026,\"menuName\":\"编码规则\",\"menuType\":\"C\",\"orderNum\":10,\"params\":{},\"parentId\":1,\"path\":\"autocodeRule\",\"perms\":\"system:autocode:rule:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 17:03:34', 32);
INSERT INTO `sys_oper_log` VALUES (172, '菜单管理', 2, 'com.nexus.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"system/autocode/index\",\"createTime\":\"2025-09-19 11:08:13\",\"icon\":\"code\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2026,\"menuName\":\"编码规则\",\"menuType\":\"C\",\"orderNum\":10,\"params\":{},\"parentId\":1,\"path\":\"autocodeRule\",\"perms\":\"system:autocode:rule\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 17:03:56', 7);
INSERT INTO `sys_oper_log` VALUES (173, '生成业务编号', 1, 'com.nexus.system.strategy.AutoCodeUtil.genSerialCode()', 'GET', 1, 'admin', '研发部门', '/system/autocode/get/CLIENT_CODE', '127.0.0.1', '内网IP', '{}', '\"C00205\"', 0, NULL, '2025-09-19 17:28:28', 36);
INSERT INTO `sys_oper_log` VALUES (174, '生成业务编号', 1, 'com.nexus.system.strategy.AutoCodeUtil.genSerialCode()', 'GET', 1, 'admin', '研发部门', '/system/autocode/get/CLIENT_CODE', '127.0.0.1', '内网IP', '{}', '\"C00206\"', 0, NULL, '2025-09-19 17:28:29', 13);
INSERT INTO `sys_oper_log` VALUES (175, '菜单管理', 2, 'com.nexus.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"core/bd/index\",\"createTime\":\"2025-09-17 17:34:02\",\"icon\":\"peoples\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2014,\"menuName\":\"客户信息\",\"menuType\":\"C\",\"orderNum\":1,\"params\":{},\"parentId\":2007,\"path\":\"bd\",\"perms\":\"core:bd:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 17:30:26', 17);
INSERT INTO `sys_oper_log` VALUES (176, '客户信息', 2, 'controller.com.nexus.bd.BdClientController.edit()', 'PUT', 1, 'admin', '研发部门', '/core/bd', '127.0.0.1', '内网IP', '{\"address\":\"123\",\"clientCode\":\"cs123\",\"clientDesc\":\"测试测试\",\"clientEn\":\"cs\",\"clientId\":1,\"clientName\":\"测试\",\"clientNick\":\"测试\",\"clientType\":\"ENTERPRISE\",\"contact1\":\"123\",\"contact1Tel\":\"123\",\"createTime\":\"2025-09-18 15:45:44\",\"email\":\"123\",\"params\":{},\"tel\":\"123\",\"updateTime\":\"2025-09-19 18:00:32\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-09-19 18:00:32', 14);

-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post`  (
  `post_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int(0) NOT NULL COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '岗位信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_post
-- ----------------------------
INSERT INTO `sys_post` VALUES (1, 'ceo', '董事长', 1, '0', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_post` VALUES (2, 'se', '项目经理', 2, '0', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_post` VALUES (3, 'hr', '人力资源', 3, '0', 'admin', '2025-08-29 16:28:04', '', NULL, '');
INSERT INTO `sys_post` VALUES (4, 'user', '普通员工', 4, '0', 'admin', '2025-08-29 16:28:04', '', NULL, '');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `role_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int(0) NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) NULL DEFAULT 1 COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) NULL DEFAULT 1 COMMENT '部门树选择项是否关联显示',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '超级管理员', 'admin', 1, '1', 1, 1, '0', '0', 'admin', '2025-08-29 16:28:04', '', NULL, '超级管理员');
INSERT INTO `sys_role` VALUES (2, '普通角色', 'common', 2, '2', 1, 1, '0', '0', 'admin', '2025-08-29 16:28:04', '', '2025-09-19 11:45:23', '普通角色');

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept`  (
  `role_id` bigint(0) NOT NULL COMMENT '角色ID',
  `dept_id` bigint(0) NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`, `dept_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色和部门关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------
INSERT INTO `sys_role_dept` VALUES (2, 100);
INSERT INTO `sys_role_dept` VALUES (2, 101);
INSERT INTO `sys_role_dept` VALUES (2, 102);
INSERT INTO `sys_role_dept` VALUES (2, 103);
INSERT INTO `sys_role_dept` VALUES (2, 104);
INSERT INTO `sys_role_dept` VALUES (2, 105);
INSERT INTO `sys_role_dept` VALUES (2, 106);
INSERT INTO `sys_role_dept` VALUES (2, 107);
INSERT INTO `sys_role_dept` VALUES (2, 108);
INSERT INTO `sys_role_dept` VALUES (2, 109);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `role_id` bigint(0) NOT NULL COMMENT '角色ID',
  `menu_id` bigint(0) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色和菜单关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (2, 1);
INSERT INTO `sys_role_menu` VALUES (2, 2);
INSERT INTO `sys_role_menu` VALUES (2, 3);
INSERT INTO `sys_role_menu` VALUES (2, 4);
INSERT INTO `sys_role_menu` VALUES (2, 100);
INSERT INTO `sys_role_menu` VALUES (2, 101);
INSERT INTO `sys_role_menu` VALUES (2, 102);
INSERT INTO `sys_role_menu` VALUES (2, 103);
INSERT INTO `sys_role_menu` VALUES (2, 104);
INSERT INTO `sys_role_menu` VALUES (2, 105);
INSERT INTO `sys_role_menu` VALUES (2, 106);
INSERT INTO `sys_role_menu` VALUES (2, 107);
INSERT INTO `sys_role_menu` VALUES (2, 108);
INSERT INTO `sys_role_menu` VALUES (2, 109);
INSERT INTO `sys_role_menu` VALUES (2, 110);
INSERT INTO `sys_role_menu` VALUES (2, 111);
INSERT INTO `sys_role_menu` VALUES (2, 112);
INSERT INTO `sys_role_menu` VALUES (2, 113);
INSERT INTO `sys_role_menu` VALUES (2, 114);
INSERT INTO `sys_role_menu` VALUES (2, 115);
INSERT INTO `sys_role_menu` VALUES (2, 116);
INSERT INTO `sys_role_menu` VALUES (2, 117);
INSERT INTO `sys_role_menu` VALUES (2, 500);
INSERT INTO `sys_role_menu` VALUES (2, 501);
INSERT INTO `sys_role_menu` VALUES (2, 1000);
INSERT INTO `sys_role_menu` VALUES (2, 1001);
INSERT INTO `sys_role_menu` VALUES (2, 1002);
INSERT INTO `sys_role_menu` VALUES (2, 1003);
INSERT INTO `sys_role_menu` VALUES (2, 1004);
INSERT INTO `sys_role_menu` VALUES (2, 1005);
INSERT INTO `sys_role_menu` VALUES (2, 1006);
INSERT INTO `sys_role_menu` VALUES (2, 1007);
INSERT INTO `sys_role_menu` VALUES (2, 1008);
INSERT INTO `sys_role_menu` VALUES (2, 1009);
INSERT INTO `sys_role_menu` VALUES (2, 1010);
INSERT INTO `sys_role_menu` VALUES (2, 1011);
INSERT INTO `sys_role_menu` VALUES (2, 1012);
INSERT INTO `sys_role_menu` VALUES (2, 1013);
INSERT INTO `sys_role_menu` VALUES (2, 1014);
INSERT INTO `sys_role_menu` VALUES (2, 1015);
INSERT INTO `sys_role_menu` VALUES (2, 1016);
INSERT INTO `sys_role_menu` VALUES (2, 1017);
INSERT INTO `sys_role_menu` VALUES (2, 1018);
INSERT INTO `sys_role_menu` VALUES (2, 1019);
INSERT INTO `sys_role_menu` VALUES (2, 1020);
INSERT INTO `sys_role_menu` VALUES (2, 1021);
INSERT INTO `sys_role_menu` VALUES (2, 1022);
INSERT INTO `sys_role_menu` VALUES (2, 1023);
INSERT INTO `sys_role_menu` VALUES (2, 1024);
INSERT INTO `sys_role_menu` VALUES (2, 1025);
INSERT INTO `sys_role_menu` VALUES (2, 1026);
INSERT INTO `sys_role_menu` VALUES (2, 1027);
INSERT INTO `sys_role_menu` VALUES (2, 1028);
INSERT INTO `sys_role_menu` VALUES (2, 1029);
INSERT INTO `sys_role_menu` VALUES (2, 1030);
INSERT INTO `sys_role_menu` VALUES (2, 1031);
INSERT INTO `sys_role_menu` VALUES (2, 1032);
INSERT INTO `sys_role_menu` VALUES (2, 1033);
INSERT INTO `sys_role_menu` VALUES (2, 1034);
INSERT INTO `sys_role_menu` VALUES (2, 1035);
INSERT INTO `sys_role_menu` VALUES (2, 1036);
INSERT INTO `sys_role_menu` VALUES (2, 1037);
INSERT INTO `sys_role_menu` VALUES (2, 1038);
INSERT INTO `sys_role_menu` VALUES (2, 1039);
INSERT INTO `sys_role_menu` VALUES (2, 1040);
INSERT INTO `sys_role_menu` VALUES (2, 1041);
INSERT INTO `sys_role_menu` VALUES (2, 1042);
INSERT INTO `sys_role_menu` VALUES (2, 1043);
INSERT INTO `sys_role_menu` VALUES (2, 1044);
INSERT INTO `sys_role_menu` VALUES (2, 1045);
INSERT INTO `sys_role_menu` VALUES (2, 1046);
INSERT INTO `sys_role_menu` VALUES (2, 1047);
INSERT INTO `sys_role_menu` VALUES (2, 1048);
INSERT INTO `sys_role_menu` VALUES (2, 1049);
INSERT INTO `sys_role_menu` VALUES (2, 1050);
INSERT INTO `sys_role_menu` VALUES (2, 1051);
INSERT INTO `sys_role_menu` VALUES (2, 1052);
INSERT INTO `sys_role_menu` VALUES (2, 1053);
INSERT INTO `sys_role_menu` VALUES (2, 1054);
INSERT INTO `sys_role_menu` VALUES (2, 1055);
INSERT INTO `sys_role_menu` VALUES (2, 1056);
INSERT INTO `sys_role_menu` VALUES (2, 1057);
INSERT INTO `sys_role_menu` VALUES (2, 1058);
INSERT INTO `sys_role_menu` VALUES (2, 1059);
INSERT INTO `sys_role_menu` VALUES (2, 1060);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint(0) NULL DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '密码',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime(0) NULL DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime(0) NULL DEFAULT NULL COMMENT '密码最后更新时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 100 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', '2025-09-19 17:01:11', '2025-08-29 16:28:04', 'admin', '2025-08-29 16:28:04', '', '2025-09-19 17:01:10', '管理员');
INSERT INTO `sys_user` VALUES (2, 105, 'ry', '若依', '00', 'ry@qq.com', '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', '2025-09-19 11:54:15', '2025-08-29 16:28:04', 'admin', '2025-08-29 16:28:04', '', '2025-09-19 11:54:15', '测试员');
INSERT INTO `sys_user` VALUES (100, 103, 'xiaowang', 'xiaowang', '00', '', '', '0', '', '$2a$10$uo3P.kW3Qzrd.oEST8ht1OG2CPr1kStHirypPHqIe96npCqNLt21K', '0', '0', '', NULL, NULL, 'ry', '2025-09-19 11:44:44', '', NULL, NULL);

-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post`  (
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `post_id` bigint(0) NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`, `post_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户与岗位关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_post
-- ----------------------------
INSERT INTO `sys_user_post` VALUES (1, 1);
INSERT INTO `sys_user_post` VALUES (2, 2);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `role_id` bigint(0) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户和角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1);
INSERT INTO `sys_user_role` VALUES (2, 2);
INSERT INTO `sys_user_role` VALUES (100, 2);

SET FOREIGN_KEY_CHECKS = 1;
