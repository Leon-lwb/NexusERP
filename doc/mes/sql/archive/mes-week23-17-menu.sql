-- =============================================
-- NexusERP - MES模块 Week 23 安灯菜单权限SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: 安灯看板菜单 + 按钮权限
-- 父菜单: 4001(生产执行, Week17已建)
-- 菜单ID段: 4070-4075 (Week22用到4067, 接续)
-- 创建时间: 2026-06-21
-- =============================================
USE `ry-vue`;

-- 安灯看板菜单 (parent=4001 生产执行)
INSERT INTO sys_menu VALUES
(4070, '安灯看板', 4001, 4, 'andon', 'mes/pro/andon/index', '', 1, 0, 'C', '0', '0', 'mes:pro:andon:list', 'bell', 103, 1, NOW(), NULL, NULL, 'MES安灯看板');

-- 按钮权限
INSERT INTO sys_menu VALUES
(4071, '安灯查询', 4070, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:andon:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(4072, '安灯呼叫', 4070, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:andon:call', '#', 103, 1, NOW(), NULL, NULL, ''),
(4073, '安灯响应', 4070, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:andon:respond', '#', 103, 1, NOW(), NULL, NULL, ''),
(4074, '安灯解决', 4070, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:andon:resolve', '#', 103, 1, NOW(), NULL, NULL, ''),
(4075, '安灯取消', 4070, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:andon:cancel', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4070 AND 4075 ORDER BY menu_id;
-- 预期: 4070(安灯看板/C/mes:pro:andon:list) + 4071~4075(5个按钮权限F)
-- 执行后为相关角色勾选 4070-4075 权限
