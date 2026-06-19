-- =============================================
-- NexusERP - MES模块 Week 26 设备管理菜单权限SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: 设备台账 + 保养计划 + 维修记录 菜单 + 按钮权限
-- 父菜单: 4003(设备管理, Week17已建, 此前零子菜单)
-- 菜单ID段: 4110-4123 (Week25用到4101, 留4102-4109空隙取整段接续)
-- 创建时间: 2026-06-24
-- =============================================
USE `ry-vue`;

-- 设备台账 (parent=4003 设备管理)
INSERT INTO sys_menu VALUES
(4110, '设备台账', 4003, 1, 'eq/equipment', 'mes/eq/equipment/index', '', 1, 0, 'C', '0', '0', 'mes:eq:equipment:list', 'computer', 103, 1, NOW(), NULL, NULL, 'MES设备台账');
INSERT INTO sys_menu VALUES
(4111, '设备查询', 4110, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:equipment:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4112, '设备新增', 4110, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:equipment:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4113, '设备编辑', 4110, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:equipment:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4114, '设备删除', 4110, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:equipment:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- 保养计划
INSERT INTO sys_menu VALUES
(4115, '保养计划', 4003, 2, 'eq/maintenance', 'mes/eq/maintenance/index', '', 1, 0, 'C', '0', '0', 'mes:eq:maintenance:list', 'tool', 103, 1, NOW(), NULL, NULL, 'MES设备保养计划');
INSERT INTO sys_menu VALUES
(4116, '保养查询', 4115, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:maintenance:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4117, '保养新增', 4115, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:maintenance:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4118, '保养编辑', 4115, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:maintenance:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4119, '保养删除', 4115, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:maintenance:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- 维修记录
INSERT INTO sys_menu VALUES
(4120, '维修记录', 4003, 3, 'eq/repair', 'mes/eq/repair/index', '', 1, 0, 'C', '0', '0', 'mes:eq:repair:list', 'wrench', 103, 1, NOW(), NULL, NULL, 'MES设备维修记录');
INSERT INTO sys_menu VALUES
(4121, '维修查询', 4120, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:repair:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4122, '维修报修', 4120, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:repair:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4123, '维修开工', 4120, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:repair:start', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4124, '维修完成', 4120, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:repair:complete', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4110 AND 4124 ORDER BY menu_id;
-- 预期: 3个C菜单(4110设备台账/4115保养计划/4120维修记录) + 各4个F按钮权限
-- 执行后为相关角色勾选 4110-4124 权限
