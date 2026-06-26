-- =============================================
-- NexusERP - MES 设备点检菜单注册
-- 目标库: ry-vue
-- 创建时间: 2026-06-26
-- 说明: Wave2 MES 运行时补齐 - 注册 mes:eq:equipment:inspect 权限
-- 幂等: INSERT IGNORE
-- 父菜单 4003 = 设备管理, 现有 4110-4124, 点检从 4125 起
-- =============================================

USE `ry-vue`;

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(4125, '设备点检', 4003, 5, 'eq-inspection', 'mes/eq/inspection/index', NULL, 1, 0, 'C', '0', '0', 'mes:eq:inspection:list', 'check', 103, 1, NOW(), NULL, NULL, 'MES设备点检执行菜单'),
(4126, '点检执行', 4125, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:equipment:inspect', '#', 103, 1, NOW(), NULL, NULL, '提交点检结果');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'mes:eq:%inspection%' OR menu_id IN (4125, 4126);
