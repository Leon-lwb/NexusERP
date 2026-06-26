-- =============================================
-- NexusERP - 工序/工艺路线 菜单权限初始化SQL
-- 目标库: ry-vue (sys_menu表)
-- 菜单ID段: 2900~2999 (ERP基础数据 2001 下)
-- 创建时间: 2026-06-17
-- 冲突检查:
--   已占用ID段: 1~1792(系统), 2000~2831(ERP), 4000~4017(MES), 11638~11806(工作流), 20000+(流程管理)
--   2900段空闲，安全可用
-- 父菜单: 2001 基础数据 (BOM管理=2014, 物料-供应商关系=2050 order=10)
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 工序管理 (parent=2001 基础数据, order=11)
-- 对应页面: erp/bd/process/index
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (2900, '工序管理', 2001, 11, 'process', 'erp/bd/process/index', NULL, 1, 0, 'C', '0', '0', 'bd:process:list', 'tool', 103, 1, NOW(), NULL, NULL, '工序管理(加工/装配/检验/包装)');

-- 工序管理按钮权限 (parent=2900)
INSERT INTO `sys_menu` VALUES (2901, '工序查询', 2900, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:process:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2902, '工序新增', 2900, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:process:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2903, '工序修改', 2900, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:process:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2904, '工序删除', 2900, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:process:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2905, '工序导出', 2900, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:process:export', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 工艺路线管理 (parent=2001 基础数据, order=12)
-- 对应页面: erp/bd/routing/index，表单页: erp/bd/routing/form (hidden路由)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (2910, '工艺路线', 2001, 12, 'routing', 'erp/bd/routing/index', NULL, 1, 0, 'C', '0', '0', 'bd:routing:list', 'cascader', 103, 1, NOW(), NULL, NULL, '工艺路线管理(工序步骤编排)');

-- 工艺路线按钮权限 (parent=2910)
INSERT INTO `sys_menu` VALUES (2911, '工艺路线查询', 2910, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:routing:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2912, '工艺路线新增', 2910, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:routing:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2913, '工艺路线修改', 2910, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:routing:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2914, '工艺路线删除', 2910, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:routing:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (2915, '工艺路线导出', 2910, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'bd:routing:export', '#', 103, 1, NOW(), NULL, NULL, '');
