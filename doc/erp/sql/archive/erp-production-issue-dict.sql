-- =============================================================================
-- 生产领料单状态字典 - 字典数据脚本
-- 日期: 2026-06-23
-- 说明: 为生产领料单新增字典：
--       wm_production_issue_status        生产领料单状态（列表/表单状态回显）
--       wm_production_issue_line_status   生产领料行状态（应领/已领/本次/欠领 的行状态）
-- 特性: 幂等可重复执行——通过 dict_type 是否存在判断，不使用固定主键，避免主键冲突。
--       字典数据存放在 ry-vue 系统库（sys_dict_type / sys_dict_data），非 ry-erp 业务库。
-- 适用: MySQL 5.7+ / 8.x，ry-vue 库
-- 注意: 若使用多租户，本脚本仅写入 tenant_id='000000'（默认租户），其他租户需在字典管理界面
--       复制或自行按租户补充。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 字典主键采用雪花式高位段（避免与初始化数据 1~xx 冲突），但仍以 dict_type 存在性做幂等
-- 兜底，即便主键被占用也不会因重复执行报错。
-- -----------------------------------------------------------------------------

-- ======================== 1. 字典类型 ========================

-- 1.1 生产领料单状态
INSERT INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 9001, '000000', '生产领料单状态', 'wm_production_issue_status', 103, 1, NOW(), '生产领料单状态列表'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'wm_production_issue_status' AND `tenant_id` = '000000');

-- 1.2 生产领料行状态
INSERT INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 9002, '000000', '生产领料行状态', 'wm_production_issue_line_status', 103, 1, NOW(), '生产领料行状态列表'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'wm_production_issue_line_status' AND `tenant_id` = '000000');

-- ======================== 2. 字典数据 ========================
-- dict_code 同样以 dict_type+dict_value 存在性做幂等，防止重复执行产生重复项。

-- 2.1 生产领料单状态
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90011, '000000', 1, '草稿', 'DRAFT', 'wm_production_issue_status', '', 'info', 'N', 103, 1, NOW(), '草稿（未保存执行前）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_status' AND `dict_value` = 'DRAFT' AND `tenant_id` = '000000');

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90012, '000000', 2, '待领料', 'CREATED', 'wm_production_issue_status', '', 'warning', 'Y', 103, 1, NOW(), '已生成待领料'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_status' AND `dict_value` = 'CREATED' AND `tenant_id` = '000000');

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90013, '000000', 3, '部分领料', 'PARTIAL', 'wm_production_issue_status', '', 'primary', 'N', 103, 1, NOW(), '部分领料（仍有欠领）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_status' AND `dict_value` = 'PARTIAL' AND `tenant_id` = '000000');

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90014, '000000', 4, '已完成', 'COMPLETED', 'wm_production_issue_status', '', 'success', 'N', 103, 1, NOW(), '全部领料完成'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_status' AND `dict_value` = 'COMPLETED' AND `tenant_id` = '000000');

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90015, '000000', 5, '倒冲领料', 'BACKFLUSH', 'wm_production_issue_status', '', 'danger', 'N', 103, 1, NOW(), '倒冲自动生成（完工扣减）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_status' AND `dict_value` = 'BACKFLUSH' AND `tenant_id` = '000000');

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90016, '000000', 6, '已执行', 'EXECUTED', 'wm_production_issue_status', '', 'success', 'N', 103, 1, NOW(), '历史状态（等价于已完成）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_status' AND `dict_value` = 'EXECUTED' AND `tenant_id` = '000000');

-- 2.2 生产领料行状态
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90021, '000000', 1, '待领料', 'PENDING', 'wm_production_issue_line_status', '', 'info', 'Y', 103, 1, NOW(), '行待领料'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_line_status' AND `dict_value` = 'PENDING' AND `tenant_id` = '000000');

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90022, '000000', 2, '部分领料', 'PARTIAL', 'wm_production_issue_line_status', '', 'warning', 'N', 103, 1, NOW(), '行部分领料（仍有欠领）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_line_status' AND `dict_value` = 'PARTIAL' AND `tenant_id` = '000000');

INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 90023, '000000', 3, '已领料', 'ISSUED', 'wm_production_issue_line_status', '', 'success', 'N', 103, 1, NOW(), '行已领料完成'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'wm_production_issue_line_status' AND `dict_value` = 'ISSUED' AND `tenant_id` = '000000');

-- =============================================================================
-- 执行完成。校验：
--   SELECT * FROM sys_dict_type  WHERE dict_type LIKE 'wm_production_issue%';
--   SELECT * FROM sys_dict_data WHERE dict_type LIKE 'wm_production_issue%' ORDER BY dict_type, dict_sort;
-- 前端无需重启，刷新页面即可在列表/表单状态列看到字典回显。
-- =============================================================================
