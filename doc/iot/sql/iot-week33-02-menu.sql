-- =============================================
-- NexusERP - IoT模块 Week 33 菜单权限初始化SQL
-- 目标库: ry-vue (sys_menu表)
-- 菜单ID段: 6000~6999 (与 ERP 2000段 / MES 4000段 区分)
-- 创建时间: 2026-06-17
-- 冲突检查: 6000段空闲，安全可用
-- 说明: 前端页面(Week 35)尚未开发，仅埋菜单与按钮权限，供后续前端对接
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 一级目录: IoT平台 (parent=0, order=6)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (6000, 'IoT平台', 0, 6, 'iot', NULL, '', 1, 0, 'M', '0', '0', '', 'monitor', 103, 1, NOW(), NULL, NULL, 'IoT物联网平台');

-- -----------------------------------------------------------
-- 产品管理 (parent=6000)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (6010, '产品管理', 6000, 1, 'product', 'iot/product/index', '', 1, 0, 'C', '0', '0', 'iot:product:list', 'list', 103, 1, NOW(), NULL, NULL, 'IoT产品(设备模板)管理');

-- 产品管理按钮权限 (parent=6010)
INSERT INTO `sys_menu` VALUES (6011, '产品查询', 6010, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:product:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6012, '产品新增', 6010, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:product:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6013, '产品修改', 6010, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:product:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6014, '产品删除', 6010, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:product:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6015, '产品导出', 6010, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:product:export', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 设备管理 (parent=6000)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (6020, '设备管理', 6000, 2, 'device', 'iot/device/index', '', 1, 0, 'C', '0', '0', 'iot:device:list', 'server', 103, 1, NOW(), NULL, NULL, 'IoT设备实例管理(含在线状态)');

-- 设备管理按钮权限 (parent=6020)
INSERT INTO `sys_menu` VALUES (6021, '设备查询', 6020, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6022, '设备新增', 6020, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6023, '设备修改', 6020, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6024, '设备删除', 6020, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (6025, '设备导出', 6020, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:device:export', '#', 103, 1, NOW(), NULL, NULL, '');
