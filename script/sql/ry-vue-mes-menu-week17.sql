-- =============================================
-- NexusERP - MES模块 Week 17 菜单权限初始化SQL
-- 目标库: ry-vue (sys_menu表)
-- 菜单ID段: 4000~4999 (与ERP的2000~2999区分)
-- 创建时间: 2026-06-16
-- 冲突检查:
--   已占用ID段: 1~1792(系统), 2000~2845(ERP), 3000~3xxx(已占用), 11638~11806(工作流), 20000+(流程管理)
--   4000段空闲，安全可用
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 一级目录: MES系统 (parent=0)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (4000, 'MES系统', 0, 7, 'mes', NULL, '', 1, 0, 'M', '0', '0', '', 'setting', 103, 1, NOW(), NULL, NULL, 'MES制造执行系统');

-- -----------------------------------------------------------
-- 二级目录
-- -----------------------------------------------------------

-- 生产执行 (parent=4000)
INSERT INTO `sys_menu` VALUES (4001, '生产执行', 4000, 1, 'pro', NULL, '', 1, 0, 'M', '0', '0', '', 'tool', 103, 1, NOW(), NULL, NULL, '生产执行管理');

-- 质量管理 (parent=4000)
INSERT INTO `sys_menu` VALUES (4002, '质量管理', 4000, 2, 'qc', NULL, '', 1, 0, 'M', '0', '0', '', 'documentation', 103, 1, NOW(), NULL, NULL, '质量管理(IQC/IPQC/OQC)');

-- 设备管理 (parent=4000)
INSERT INTO `sys_menu` VALUES (4003, '设备管理', 4000, 3, 'eq', NULL, '', 1, 0, 'M', '0', '0', '', 'computer', 103, 1, NOW(), NULL, NULL, '设备台账/保养/维修');

-- 数据采集 (parent=4000)
INSERT INTO `sys_menu` VALUES (4004, '数据采集', 4000, 4, 'dc', NULL, '', 1, 0, 'M', '0', '0', '', 'monitor', 103, 1, NOW(), NULL, NULL, '数据采集点/采集记录');

-- SPC分析 (parent=4000)
INSERT INTO `sys_menu` VALUES (4005, 'SPC分析', 4000, 5, 'spc', NULL, '', 1, 0, 'M', '0', '0', '', 'chart', 103, 1, NOW(), NULL, NULL, '统计过程控制(SPC)');

-- -----------------------------------------------------------
-- Phase 1 页面: MES工单 (parent=4001 生产执行)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (4010, 'MES工单', 4001, 1, 'workorder', 'mes/pro/workorder/index', '', 1, 0, 'C', '0', '0', 'mes:workorder:list', 'list', 103, 1, NOW(), NULL, NULL, 'MES工单列表');

-- -----------------------------------------------------------
-- MES工单按钮权限 (parent=4010)
-- -----------------------------------------------------------
INSERT INTO `sys_menu` VALUES (4011, 'MES工单查询', 4010, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:workorder:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4012, 'MES工单新增', 4010, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:workorder:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4013, 'MES工单修改', 4010, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:workorder:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4014, 'MES工单删除', 4010, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:workorder:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4015, 'MES工单导出', 4010, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:workorder:export', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4016, 'MES工单审批', 4010, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:workorder:approve', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4017, 'MES工单下达', 4010, 7, '', '', '', 1, 0, 'F', '0', '0', 'mes:workorder:release', '#', 103, 1, NOW(), NULL, NULL, '');
