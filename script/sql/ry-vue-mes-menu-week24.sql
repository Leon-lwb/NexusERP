-- =============================================
-- NexusERP - MES模块 Week 24 SN追溯菜单权限SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: SN追溯菜单 + 追溯树(hidden) + 按钮权限
-- 父菜单: 4001(生产执行, Week17已建)
-- 菜单ID段: 4080-4086 (Week23用到4075, 接续)
-- 创建时间: 2026-06-22
-- =============================================
USE `ry-vue`;

-- SN追溯菜单 (parent=4001 生产执行)
INSERT INTO sys_menu VALUES
(4080, 'SN追溯', 4001, 5, 'sn', 'mes/pro/sn/index', '', 1, 0, 'C', '0', '0', 'mes:pro:sn:list', 'barcode', 103, 1, NOW(), NULL, NULL, 'MES SN序列号追溯');

-- SN追溯树(hidden, 由列表"追溯"按钮跳转)
INSERT INTO sys_menu VALUES
(4081, 'SN追溯树', 4080, 1, 'sn/trace/:snId', 'mes/pro/sn/trace', '', 1, 0, 'C', '1', '0', 'mes:pro:sn:query', '#', 103, 1, NOW(), NULL, NULL, '');

-- 按钮权限
INSERT INTO sys_menu VALUES
(4082, 'SN查询', 4080, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:sn:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(4083, 'SN生成', 4080, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:sn:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(4084, 'SN完工', 4080, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:sn:complete', '#', 103, 1, NOW(), NULL, NULL, ''),
(4085, 'SN报废', 4080, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:sn:scrap', '#', 103, 1, NOW(), NULL, NULL, ''),
(4086, 'SN发货', 4080, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:sn:ship', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4080 AND 4086 ORDER BY menu_id;
-- 预期: 4080(SN追溯/C/mes:pro:sn:list) + 4081(SN追溯树/C hidden) + 4082~4086(5个按钮权限F)
-- 执行后为相关角色勾选 4080-4086 权限
