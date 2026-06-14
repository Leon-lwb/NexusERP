-- ==========================================================
-- 物料-供应商关系管理菜单数据
-- 菜单ID范围: 2050-2055 (已确认无冲突)
-- 父菜单: 2001 (基础数据)
-- 权限前缀: bd:materialVendor
-- ==========================================================

-- 物料-供应商关系管理菜单
INSERT INTO `sys_menu` VALUES (2050, '物料-供应商关系', 2001, 10, 'materialVendor', 'erp/bd/material-vendor/index', NULL, 1, 0, 'C', '0', '0', 'bd:materialVendor:list', 'shopping', 103, 1, NOW(), NULL, NULL, '物料-供应商关系菜单');

-- 物料-供应商关系查询按钮
INSERT INTO `sys_menu` VALUES (2051, '物料-供应商关系查询', 2050, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:materialVendor:query', '#', 103, 1, NOW(), NULL, NULL, '');

-- 物料-供应商关系新增按钮
INSERT INTO `sys_menu` VALUES (2052, '物料-供应商关系新增', 2050, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:materialVendor:add', '#', 103, 1, NOW(), NULL, NULL, '');

-- 物料-供应商关系修改按钮
INSERT INTO `sys_menu` VALUES (2053, '物料-供应商关系修改', 2050, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:materialVendor:edit', '#', 103, 1, NOW(), NULL, NULL, '');

-- 物料-供应商关系删除按钮
INSERT INTO `sys_menu` VALUES (2054, '物料-供应商关系删除', 2050, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:materialVendor:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- 物料-供应商关系导出按钮
INSERT INTO `sys_menu` VALUES (2055, '物料-供应商关系导出', 2050, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:materialVendor:export', '#', 103, 1, NOW(), NULL, NULL, '');
