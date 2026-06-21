-- =============================================
-- NexusERP - MES模块 Week 28 菜单权限 (修正版)
-- 目标库: ry-vue (系统库)
-- 说明: 先删除之前错误执行的菜单(2950-2954, 2980), 再按正确列结构重新插入
-- 父菜单: 4001 (生产执行)
-- 创建时间: 2026-06-25
-- =============================================

-- 1. 先删除之前错误插入的菜单记录
DELETE FROM `sys_menu` WHERE `menu_id` IN (2950, 2951, 2952, 2953, 2954, 2980);

-- 2. 重新插入(列顺序对齐 sys_menu 实际结构)
-- MBOM管理主菜单(父菜单: 4001 生产执行)
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2950, 'MBOM管理', 4001, 75, 'mbom', 'mes/pro/mbom/index', '', 1, 0, 'C', '0', '0', 'mes:pro:mbom:list', 'tree', 103, 1, NOW(), NULL, NULL, 'MES制造BOM(MBOM)管理');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2951, 'MBOM查询', 2950, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:mbom:query', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2952, 'MBOM新增', 2950, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:mbom:add', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2953, 'MBOM修改', 2950, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:mbom:edit', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2954, 'MBOM删除', 2950, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:mbom:remove', '#', 103, 1, NOW(), NULL, NULL, '');
