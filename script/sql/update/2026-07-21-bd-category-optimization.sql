-- ============================================================
-- 物料分类(bd_material_category)优化 - 增量脚本
-- 日期: 2026-07-21
-- 关联方案: doc/erp/plans/2026-07-21-erp-bd-material-category-optimization-plan.md
--
-- 背景:
--   1) uk_category_code 原为 (category_code) 全局唯一，未含 tenant_id，
--      多租户下不同租户无法共用相同分类编码（租户A占 RAW，租户B被拦截）。
--      向项目新表惯例(process/routing/rework/subcontract/invoice 均含 tenant_id)
--      对齐，改为 (category_code, tenant_id)。已核对存量 (category_code,tenant_id)
--      唯一，重建索引无冲突。
--      注：与项目全仓惯例一致，索引不含 del_flag，软删后同编码不可重建
--      （material/vendor/workshop 同状，属已知限制，不在本次修复）。
--   2) 电气材料(id12) 与 木材(id14) sort_order 均为 6，去重。
--
-- 说明:
--   - 主脚本 ry-erp.sql 已同步索引变更，本脚本供已部署环境增量执行。
--   - 索引 DROP/CREATE 为一次性 DDL；UPDATE 天然幂等。
--   - P4 阶段(分类属性模板)的新表/字典/菜单将追加至本脚本底部对应章节。
-- ============================================================

USE `ry-erp`;

-- ========== P1: 多租户唯一索引修复 ==========
-- 重建 uk_category_code: (category_code) -> (category_code, tenant_id)
ALTER TABLE `bd_material_category` DROP INDEX `uk_category_code`;
ALTER TABLE `bd_material_category`
  ADD UNIQUE INDEX `uk_category_code`(`category_code`, `tenant_id`) USING BTREE;

-- ========== P1: sortOrder 重复修复 ==========
-- 木材(id14) sort_order 6 -> 7（与电气材料 id12 的 6 去重）
UPDATE `bd_material_category`
SET `sort_order` = 7
WHERE `category_id` = 14 AND `sort_order` = 6;

-- ============================================================
-- P4: 分类属性模板（新表 + 字典）
--   bd_category_attribute        分类属性定义（模板）
--   bd_material_attribute_value  物料扩展属性值（分类型列存储）
--   字典 bd_attr_data_type        STRING/NUMBER/ENUM/DATE/BOOLEAN
-- 权限复用 bd:category:edit/query 与 bd:material:edit/query，不新增菜单按钮。
-- ============================================================

-- ========== P4: 分类属性模板表 ==========
DROP TABLE IF EXISTS `bd_category_attribute`;
CREATE TABLE `bd_category_attribute` (
  `attr_id` bigint(20) NOT NULL COMMENT '属性ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `category_id` bigint(20) NOT NULL COMMENT '所属分类ID',
  `attr_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性编码',
  `attr_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性名称',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STRING' COMMENT '数据类型 STRING/NUMBER/ENUM/DATE/BOOLEAN',
  `enum_values` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '枚举可选项(逗号分隔,ENUM时用)',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位(NUMBER时用)',
  `required_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否必填 0否1是',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示排序',
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
  PRIMARY KEY (`attr_id`) USING BTREE,
  UNIQUE INDEX `uk_category_attr_code`(`attr_code`, `category_id`, `tenant_id`) USING BTREE,
  INDEX `idx_category_id`(`category_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料分类属性模板' ROW_FORMAT = DYNAMIC;

-- ========== P4: 物料扩展属性值表（分类型列，照 iot_device_data/dc_data_record 范式） ==========
DROP TABLE IF EXISTS `bd_material_attribute_value`;
CREATE TABLE `bd_material_attribute_value` (
  `value_id` bigint(20) NOT NULL COMMENT '值ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `attr_id` bigint(20) NOT NULL COMMENT '属性ID',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据类型(冗余自模板)',
  `numeric_value` decimal(14, 4) NULL DEFAULT NULL COMMENT '数值型值',
  `text_value` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '字符串/枚举值',
  `bool_value` tinyint(1) NULL DEFAULT NULL COMMENT '布尔值',
  `date_value` datetime NULL DEFAULT NULL COMMENT '日期值',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`value_id`) USING BTREE,
  UNIQUE INDEX `uk_material_attr`(`material_id`, `attr_id`, `tenant_id`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_attr_id`(`attr_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料扩展属性值' ROW_FORMAT = DYNAMIC;

-- ========== P4: 字典 bd_attr_data_type（动态ID + 幂等） ==========
USE `ry-vue`;
SET @dt_id = (SELECT IFNULL(MAX(dict_id), 0) + 1 FROM `sys_dict_type`);
INSERT INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT @dt_id, '000000', '物料属性数据类型', 'bd_attr_data_type', 103, 1, NOW(), '分类属性值数据类型'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'bd_attr_data_type');

SET @dd_id = (SELECT IFNULL(MAX(dict_code), 0) + 1 FROM `sys_dict_data`);
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT @dd_id + 0, '000000', 1, '字符串', 'STRING', 'bd_attr_data_type', NULL, 'default', 'N', 103, 1, NOW(), NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'bd_attr_data_type' AND `dict_value` = 'STRING');
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT @dd_id + 1, '000000', 2, '数值', 'NUMBER', 'bd_attr_data_type', NULL, 'primary', 'N', 103, 1, NOW(), NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'bd_attr_data_type' AND `dict_value` = 'NUMBER');
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT @dd_id + 2, '000000', 3, '枚举', 'ENUM', 'bd_attr_data_type', NULL, 'success', 'N', 103, 1, NOW(), NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'bd_attr_data_type' AND `dict_value` = 'ENUM');
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT @dd_id + 3, '000000', 4, '日期', 'DATE', 'bd_attr_data_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'bd_attr_data_type' AND `dict_value` = 'DATE');
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT @dd_id + 4, '000000', 5, '布尔', 'BOOLEAN', 'bd_attr_data_type', NULL, 'info', 'N', 103, 1, NOW(), NULL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'bd_attr_data_type' AND `dict_value` = 'BOOLEAN');
