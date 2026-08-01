-- =============================================
-- NexusERP - ERP库存预留 菜单权限SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: 库存预留汇总 + 预留明细 + 手动预留/释放 + 可用库存检查
-- 父菜单: 2965 (库存管理)
-- 菜单ID段: 2230-2235 (原 2182-2187 被出库管理按钮占用, 已删除误插的 2187)
--   2230         C 页面 库存预留 (component=erp/wm/reservation/index, path=reservation)
--   2231         F 按钮 预留查询 (wm:reservation:query)
--   2232         F 按钮 预留明细 (wm:reservation:list)
--   2233         F 按钮 预留汇总 (wm:reservation:summary)
--   2234         F 按钮 手动预留 (wm:reservation:add)
--   2235         F 按钮 释放预留 (wm:reservation:edit)
-- 幂等: INSERT IGNORE, 可重复执行
-- =============================================
USE `ry-vue`;

-- 库存预留页面 (parent=2965 库存管理, order_num=6)
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2230, '库存预留', 2965, 6, 'reservation', 'erp/wm/reservation/index', '', 1, 0, 'C', '0', '0', 'wm:reservation:summary', 'lock', 103, 1, NOW(), NULL, NULL, '库存预留(销售订单/工单锁定, 汇总/明细/手动预留/释放)');

-- 按钮权限
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2231, '预留查询', 2230, 1, '', '', '', 1, 0, 'F', '0', '0', 'wm:reservation:query', '#', 103, 1, NOW(), NULL, NULL, '可用库存检查');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2232, '预留明细', 2230, 2, '', '', '', 1, 0, 'F', '0', '0', 'wm:reservation:list', '#', 103, 1, NOW(), NULL, NULL, '预留流水查询');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2233, '预留汇总', 2230, 3, '', '', '', 1, 0, 'F', '0', '0', 'wm:reservation:summary', '#', 103, 1, NOW(), NULL, NULL, '在库/已预留/可用汇总');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2234, '手动预留', 2230, 4, '', '', '', 1, 0, 'F', '0', '0', 'wm:reservation:add', '#', 103, 1, NOW(), NULL, NULL, '手动锁定库存');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2235, '释放预留', 2230, 5, '', '', '', 1, 0, 'F', '0', '0', 'wm:reservation:edit', '#', 103, 1, NOW(), NULL, NULL, '释放预留(按来源单据)');
