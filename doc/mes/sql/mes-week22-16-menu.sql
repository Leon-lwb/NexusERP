-- =============================================
-- NexusERP - MES模块 Week 22 派工菜单权限SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: 派工管理菜单 + 按钮权限
-- 父菜单: 4001(生产执行, Week17已建)
-- 创建时间: 2026-06-20
-- =============================================
USE `ry-vue`;

-- 派工管理菜单 (parent=4001 生产执行)
INSERT INTO sys_menu VALUES 
(4060, '派工管理', 4001, 3, 'dispatch', 'mes/pro/dispatch/index', '', 1, 0, 'C', '0', '0', 'mes:pro:dispatch:list', 'people', 103, 1, NOW(), NULL, NULL, 'MES派工管理');

-- 派工详情 (hidden)
INSERT INTO sys_menu VALUES 
(4061, '派工详情', 4060, 1, 'dispatch/detail/:dispatchId', 'mes/pro/dispatch/detail', '', 1, 0, 'C', '1', '0', 'mes:pro:dispatch:query', '#', 103, 1, NOW(), NULL, NULL, '');

-- 按钮权限
INSERT INTO sys_menu VALUES 
(4062, '派工查询', 4060, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(4063, '派工创建', 4060, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(4064, '派工开工', 4060, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:start', '#', 103, 1, NOW(), NULL, NULL, ''),
(4065, '派工完工', 4060, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:complete', '#', 103, 1, NOW(), NULL, NULL, ''),
(4066, '派工取消', 4060, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:cancel', '#', 103, 1, NOW(), NULL, NULL, ''),
(4067, '扫码执行', 4060, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:scan', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4060 AND 4067 ORDER BY menu_id;
-- 预期: 4060(派工管理/C/mes:pro:dispatch:list) + 4061(派工详情/C hidden) + 4062~4067(6个按钮权限F)
