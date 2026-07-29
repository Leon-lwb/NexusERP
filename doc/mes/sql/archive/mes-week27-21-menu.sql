-- =============================================
-- NexusERP - MES模块 Week 27 数据采集+SPC 菜单权限SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: 数据采集点 + 采集数据记录 + SPC控制图 + SPC数据点 菜单 + 按钮权限
-- 父菜单: 4004(数据采集, Week17已建, 此前零子菜单), 4005(SPC分析, 此前零子菜单)
-- 菜单ID段: 4130-4145 (Week26用到4124, 留4125-4129空隙取整段接续)
-- 创建时间: 2026-06-25
-- =============================================
USE `ry-vue`;

-- 数据采集点 (parent=4004 数据采集)
INSERT INTO sys_menu VALUES
(4130, '数据采集点', 4004, 1, 'dc/collection-point', 'mes/dc/collection-point/index', '', 1, 0, 'C', '0', '0', 'mes:dc:point:list', 'monitor', 103, 1, NOW(), NULL, NULL, 'MES数据采集点');
INSERT INTO sys_menu VALUES
(4131, '采集点查询', 4130, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:dc:point:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4132, '采集点新增', 4130, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:dc:point:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4133, '采集点编辑', 4130, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:dc:point:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4134, '采集点删除', 4130, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:dc:point:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- 采集数据记录 (parent=4004 数据采集)
INSERT INTO sys_menu VALUES
(4135, '采集数据记录', 4004, 2, 'dc/data-record', 'mes/dc/data-record/index', '', 1, 0, 'C', '0', '0', 'mes:dc:record:list', 'document', 103, 1, NOW(), NULL, NULL, 'MES采集数据记录');
INSERT INTO sys_menu VALUES
(4136, '记录查询', 4135, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:dc:record:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4137, '记录录入', 4135, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:dc:record:add', '#', 103, 1, NOW(), NULL, NULL, '');

-- SPC控制图 (parent=4005 SPC分析)
INSERT INTO sys_menu VALUES
(4138, 'SPC控制图', 4005, 1, 'spc/control-chart', 'mes/spc/control-chart/index', '', 1, 0, 'C', '0', '0', 'mes:spc:chart:list', 'chart', 103, 1, NOW(), NULL, NULL, 'MES SPC控制图');
INSERT INTO sys_menu VALUES
(4139, '控制图查询', 4138, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:spc:chart:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4140, '控制图新增', 4138, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:spc:chart:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4141, '控制图编辑', 4138, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:spc:chart:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4142, '控制图删除', 4138, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:spc:chart:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4143, '控制图分析', 4138, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:spc:chart:analyze', '#', 103, 1, NOW(), NULL, NULL, '');

-- SPC数据点 (parent=4005 SPC分析)
INSERT INTO sys_menu VALUES
(4144, 'SPC数据点', 4005, 2, 'spc/data-point', 'mes/spc/data-point/index', '', 1, 0, 'C', '0', '0', 'mes:spc:point:list', 'tickets', 103, 1, NOW(), NULL, NULL, 'MES SPC数据点');
INSERT INTO sys_menu VALUES
(4145, '数据点查询', 4144, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:spc:point:query', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4130 AND 4145 ORDER BY menu_id;
-- 预期: 4个C菜单(4130采集点/4135数据记录/4138控制图/4144数据点) + 11个F按钮权限
-- 执行后为相关角色勾选 4130-4145 权限
