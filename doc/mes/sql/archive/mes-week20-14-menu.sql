-- =============================================
-- NexusERP - MES模块 Week 20 菜单权限补充SQL
-- 数据库: ry-vue
-- 说明: 流转卡菜单 + 按钮权限
-- 菜单ID段: 4040~4050 (对齐Week17的4000段)
-- 创建时间: 2026-06-19
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 1. 流转卡菜单 (parent=4001 生产执行)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES
(4040, '流转卡', 4001, 2, 'flow-card', 'mes/pro/flow-card/index', '', 1, 0, 'C', '0', '0', 'mes:pro:flowcard:list', 'card', 103, 1, NOW(), NULL, NULL, 'MES流转卡列表');

-- 流转卡详情 (hidden, parent=4040)
INSERT INTO `sys_menu` VALUES
(4041, '流转卡详情', 4040, 1, 'flow-card/detail/:cardId', 'mes/pro/flow-card/detail', '', 1, 0, 'C', '1', '0', 'mes:pro:flowcard:query', '#', 103, 1, NOW(), NULL, NULL, 'MES流转卡详情');

-- -----------------------------------------------------------
-- 2. 流转卡按钮权限 (parent=4040)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES
(4042, '流转卡查询', 4040, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(4043, '流转卡创建', 4040, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(4044, '流转卡下达', 4040, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:release', '#', 103, 1, NOW(), NULL, NULL, ''),
(4045, '流转卡开工', 4040, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:start', '#', 103, 1, NOW(), NULL, NULL, ''),
(4046, '流转卡完工', 4040, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:complete', '#', 103, 1, NOW(), NULL, NULL, ''),
(4047, '流转卡导出', 4040, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:export', '#', 103, 1, NOW(), NULL, NULL, ''),
(4048, '流转卡关闭', 4040, 7, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:edit', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 3. 前端路由配置指引
-- -----------------------------------------------------------
-- 流转卡列表: /mes/pro/flow-card -> mes/pro/flow-card/index
-- 流转卡详情: /mes/pro/flow-card/detail/:cardId -> mes/pro/flow-card/detail (hidden)
