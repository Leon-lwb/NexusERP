-- =============================================
-- NexusERP - MES模块 P0-9 批次管理菜单权限SQL（单一干净版）
-- 目标库: ry-vue (sys_menu)
-- 说明: 批次主档 + 工单批次消耗 + SN批次绑定 菜单及按钮权限
-- 父菜单: 4000 (MES系统) -> 4006 批次管理 (M目录)
-- 菜单ID段:
--   4006         M 目录 批次管理
--   4170         C 页面 批次主档 (component=mes/lt/lot/index)
--   4171         F 按钮 批次查询
--   4152~4160    F 按钮 批次增删改/隔离/报废/放行/QC/FEFO/追溯 (parent=4170)
--   4161         C 页面 工单批次消耗
--   4162~4164    F 按钮 消耗 查询/新增/删除
--   4165         C 页面 SN批次绑定
--   4166~4168    F 按钮 绑定 查询/新增/删除
-- 整合记录(2026-07-25):
--   原 02 把 4152~4160 的 parent_id 误写为 4150(被主数据占用,INSERT IGNORE 静默跳过 4150/4151),
--   致 getRouters 不返回 mes/lt/lot 路由; 原 03(menu-fix) 已修正。本版直接用正确 parent_id=4170,
--   原 03 已删除并入此脚本。4150/4151 不再使用(留给既有主数据/生产订单)。
-- 幂等: INSERT IGNORE, 可重复执行
-- =============================================
USE `ry-vue`;

-- -----------------------------------------------------------
-- 一、二级目录: 批次管理 (parent=4000 MES系统, order_num=6)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4006, '批次管理', 4000, 6, 'lt', NULL, '', 1, 0, 'M', '0', '0', '', 'peoples', 103, 1, NOW(), NULL, NULL, 'MES批次/FEFO/追溯管理');

-- -----------------------------------------------------------
-- 二、批次主档 (parent=4006)  Controller: LtLotController, 权限前缀 mes:lt:lot
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4170, '批次主档', 4006, 1, 'lot', 'mes/lt/lot/index', '', 1, 0, 'C', '0', '0', 'mes:lt:lot:list', 'list', 103, 1, NOW(), NULL, NULL, 'MES批次主档(含FEFO推荐/QC/隔离/追溯)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4171, '批次查询', 4170, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:query', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4152, '批次新增', 4170, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:add', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4153, '批次修改', 4170, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:edit', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4154, '批次删除', 4170, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:remove', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4155, '批次隔离', 4170, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:quarantine', '#', 103, 1, NOW(), NULL, NULL, 'IN_STOCK/IN_USE -> QUARANTINED');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4156, '批次报废', 4170, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:scrap', '#', 103, 1, NOW(), NULL, NULL, 'QUARANTINED -> SCRAPPED');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4157, '批次放行', 4170, 7, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:release', '#', 103, 1, NOW(), NULL, NULL, 'QUARANTINED -> IN_STOCK/IN_USE (隔离解除)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4158, '批次QC', 4170, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:qc', '#', 103, 1, NOW(), NULL, NULL, 'PENDING -> PASS/FAIL (FAIL自动隔离)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4159, 'FEFO推荐', 4170, 9, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:fefo', '#', 103, 1, NOW(), NULL, NULL, '按到期升序推荐可用批次');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4160, '批次追溯', 4170, 10, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:lot:trace', '#', 103, 1, NOW(), NULL, NULL, '批次 -> 工单消耗 -> SN绑定 追溯链');

-- -----------------------------------------------------------
-- 三、工单批次消耗 (parent=4006)  Controller: LtWoConsumptionController, 权限前缀 mes:lt:consumption
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4161, '工单批次消耗', 4006, 2, 'consumption', 'mes/lt/consumption/index', '', 1, 0, 'C', '0', '0', 'mes:lt:consumption:list', 'shopping', 103, 1, NOW(), NULL, NULL, 'MES工单批次消耗记录(联动扣减批次数量,含FEFO领料)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4162, '消耗查询', 4161, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:consumption:query', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4163, '消耗新增', 4161, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:consumption:add', '#', 103, 1, NOW(), NULL, NULL, '新增消耗/FEFO领料确认时联动扣减批次已消耗数量');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4164, '消耗删除', 4161, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:consumption:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 四、SN批次绑定 (parent=4006)  Controller: LtSnBindingController, 权限前缀 mes:lt:binding
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4165, 'SN批次绑定', 4006, 3, 'binding', 'mes/lt/binding/index', '', 1, 0, 'C', '0', '0', 'mes:lt:binding:list', 'link', 103, 1, NOW(), NULL, NULL, 'MES SN与批次绑定关系(产品批次追溯)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4166, '绑定查询', 4165, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:binding:query', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4167, '绑定新增', 4165, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:binding:add', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4168, '绑定删除', 4165, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:lt:binding:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 五、为 role_id=3 (test1 角色, 非超管需显式授权) 分配本模块全部菜单
--    超管 (role_id=1 / user_id=1) 走 isSuperAdmin 旁路, 无需此步
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 4006),
(3, 4170),
(3, 4171),
(3, 4152),
(3, 4153),
(3, 4154),
(3, 4155),
(3, 4156),
(3, 4157),
(3, 4158),
(3, 4159),
(3, 4160),
(3, 4161),
(3, 4162),
(3, 4163),
(3, 4164),
(3, 4165),
(3, 4166),
(3, 4167),
(3, 4168);

-- =============================================
-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, parent_id, menu_type, component, perms
--   FROM sys_menu WHERE menu_id = 4006 OR menu_id BETWEEN 4150 AND 4171 ORDER BY menu_id;
-- 预期:
--   1 个 M 目录: 4006 批次管理
--   3 个 C 页面: 4170 批次主档 / 4161 工单批次消耗 / 4165 SN批次绑定
--  16 个 F 按钮: 4171 + 4152~4160(9) + 4162~4164(3) + 4166~4168(3)
--   4152~4160 的 parent_id 均为 4170 (非 4150)
-- SELECT role_id, COUNT(*) FROM sys_role_menu WHERE menu_id IN
--   (4006,4170,4171,4152,4153,4154,4155,4156,4157,4158,4159,4160,4161,4162,4163,4164,4165,4166,4167,4168)
--   GROUP BY role_id;
-- 预期: role_id=3 计数=20
-- =============================================
