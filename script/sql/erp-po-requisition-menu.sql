-- ==========================================================
-- 部门请购单菜单数据
-- 菜单ID范围: 2080-2085 (已确认无冲突)
-- 父菜单: 2000 (ERP管理)
-- 注意: 父菜单ID 2000 需要 ry-vue.sql 中的ERP管理目录已存在
-- 权限前缀: po:requisition
-- ==========================================================

-- 部门请购单菜单
INSERT INTO `sys_menu` VALUES (2080, '部门请购单', 2000, 6, 'requisition', 'erp/po/requisition/index', NULL, 1, 0, 'C', '0', '0', 'po:requisition:list', 'form', 103, 1, NOW(), NULL, NULL, '部门请购单菜单');

-- 部门请购单查询按钮
INSERT INTO `sys_menu` VALUES (2081, '部门请购单查询', 2080, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'po:requisition:query', '#', 103, 1, NOW(), NULL, NULL, '');

-- 部门请购单新增按钮
INSERT INTO `sys_menu` VALUES (2082, '部门请购单新增', 2080, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'po:requisition:add', '#', 103, 1, NOW(), NULL, NULL, '');

-- 部门请购单修改按钮
INSERT INTO `sys_menu` VALUES (2083, '部门请购单修改', 2080, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'po:requisition:edit', '#', 103, 1, NOW(), NULL, NULL, '');

-- 部门请购单删除按钮
INSERT INTO `sys_menu` VALUES (2084, '部门请购单删除', 2080, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'po:requisition:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- 部门请购单导出按钮
INSERT INTO `sys_menu` VALUES (2085, '部门请购单导出', 2080, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'po:requisition:export', '#', 103, 1, NOW(), NULL, NULL, '');
