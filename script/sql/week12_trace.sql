-- ----------------------------
-- R-043 + R-044 追溯查询服务
-- ----------------------------

SET NAMES utf8mb4;

-- ----------------------------
-- 字典类型：追溯方向 (使用安全大ID段 500xxx 避免主键冲突)
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (500020, '000000', '追溯方向', 'wm_trace_direction', 103, 1, NOW(), NULL, NULL, '追溯查询方向');

-- ----------------------------
-- 字典数据：追溯方向
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (5000201, '000000', 1, '正向追溯', 'FORWARD', 'wm_trace_direction', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '原材料→成品');
INSERT INTO `sys_dict_data` VALUES (5000202, '000000', 2, '反向追溯', 'BACKWARD', 'wm_trace_direction', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '成品→原材料');

-- ----------------------------
-- 菜单 SQL - 追溯查询 (使用安全大ID段 50xx 避免主键冲突)
-- ----------------------------
-- 菜单(C): 追溯查询 (parent: 仓库管理 2100)
INSERT INTO `sys_menu` VALUES (5020, '追溯查询', 2100, 13, 'trace', 'erp/wm/trace/index', NULL, 1, 0, 'C', '0', '0', 'wm:trace:list', 'link', 103, 1, NOW(), NULL, NULL, '追溯查询菜单');
-- 按钮(F): 追溯列表
INSERT INTO `sys_menu` VALUES (5021, '追溯列表', 5020, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:trace:list', '#', 103, 1, NOW(), NULL, NULL, '');
-- 按钮(F): 追溯查询
INSERT INTO `sys_menu` VALUES (5022, '追溯查询', 5020, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:trace:query', '#', 103, 1, NOW(), NULL, NULL, '');
