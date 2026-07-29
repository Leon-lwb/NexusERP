-- =============================================
-- NexusERP - MES模块 P0-3 进站/出站+流程指引 菜单+字典 SQL
-- 目标库: ry-vue (sys_menu / sys_role_menu / sys_dict_type / sys_dict_data)
-- 父菜单: 4001 (生产执行)
-- 菜单ID段:
--   4200         C 页面 工序流程指引配置 (component=mes/pro/step-guide/index)
--   4207         C 页面 工序校验规则配置 (component=mes/pro/step-check-rule/index)
--   4208~4212    F 按钮 指引 query/add/edit/remove/export (parent=4200)
--   4213~4217    F 按钮 规则 query/add/edit/remove/export (parent=4207)
--   4218         F 按钮 流转卡跳站 (parent=4040 流转卡)
-- 字典:
--   mes_guide_type      TEXT/SOP_LINK
--   mes_check_stage     ENTRY/EXIT
--   mes_check_type      10 项校验类型
-- 幂等: INSERT IGNORE + WHERE NOT EXISTS, 可重复执行
-- =============================================
USE `ry-vue`;

-- -----------------------------------------------------------
-- 一、工序流程指引配置 (parent=4001 生产执行)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4200, '工序指引配置', 4001, 20, 'step-guide', 'mes/pro/step-guide/index', '', 1, 0, 'C', '0', '0', 'mes:pro:step-guide:list', 'guide', 103, 1, NOW(), NULL, NULL, 'MES工序流程指引配置(TEXT+SOP_LINK)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4208, '指引查询', 4200, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-guide:query', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4209, '指引新增', 4200, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-guide:add', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4210, '指引修改', 4200, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-guide:edit', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4211, '指引删除', 4200, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-guide:remove', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4212, '指引导出', 4200, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-guide:export', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 二、工序校验规则配置 (parent=4001 生产执行)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4207, '校验规则配置', 4001, 21, 'step-check-rule', 'mes/pro/step-check-rule/index', '', 1, 0, 'C', '0', '0', 'mes:pro:step-check-rule:list', 'validCode', 103, 1, NOW(), NULL, NULL, 'MES工序进站/出站校验规则配置');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4213, '规则查询', 4207, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-check-rule:query', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4214, '规则新增', 4207, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-check-rule:add', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4215, '规则修改', 4207, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-check-rule:edit', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4216, '规则删除', 4207, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-check-rule:remove', '#', 103, 1, NOW(), NULL, NULL, '');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4217, '规则导出', 4207, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:step-check-rule:export', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 三、流转卡跳站按钮 (parent=4040 流转卡, 接续 4048)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4218, '流转卡跳站', 4040, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:flowcard:skip', '#', 103, 1, NOW(), NULL, NULL, 'P0-3 工序跳站(首道禁跳,须原因)');

-- -----------------------------------------------------------
-- 四、角色授权 (role_id=3, 同 P0-2/P0-9 约定)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 4200), (3, 4207), (3, 4208), (3, 4209), (3, 4210), (3, 4211), (3, 4212),
(3, 4213), (3, 4214), (3, 4215), (3, 4216), (3, 4217), (3, 4218);

-- =============================================
-- 五、字典: mes_guide_type (指引类型)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352001, '000000', 'MES指引类型', 'mes_guide_type', 103, 1, NOW(), NULL, NULL, '工序流程指引类型(P0-3)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_guide_type');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352011, '000000', 1, '文本', 'TEXT', 'mes_guide_type', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '文本指引'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_guide_type' AND `dict_value` = 'TEXT');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352012, '000000', 2, 'SOP链接', 'SOP_LINK', 'mes_guide_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '关联SOP文档(须已绑定工序)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_guide_type' AND `dict_value` = 'SOP_LINK');

-- =============================================
-- 六、字典: mes_check_stage (校验阶段)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352002, '000000', 'MES校验阶段', 'mes_check_stage', 103, 1, NOW(), NULL, NULL, '进站/出站校验阶段(P0-3)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_check_stage');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352021, '000000', 1, '进站', 'ENTRY', 'mes_check_stage', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '进站校验'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_stage' AND `dict_value` = 'ENTRY');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352022, '000000', 2, '出站', 'EXIT', 'mes_check_stage', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '出站校验'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_stage' AND `dict_value` = 'EXIT');

-- =============================================
-- 七、字典: mes_check_type (校验类型, 10 项)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352003, '000000', 'MES校验类型', 'mes_check_type', 103, 1, NOW(), NULL, NULL, '进站/出站校验规则类型(P0-3)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_check_type');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352031, '000000', 1, '串行门禁', 'SERIAL_GATE', 'mes_check_type', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, '内置:上一工序须完成'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'SERIAL_GATE');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352032, '000000', 2, 'SOP版本', 'SOP_VERSION', 'mes_check_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '进站:须绑定已发布SOP'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'SOP_VERSION');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352033, '000000', 3, '工装可用', 'TOOLING_AVAILABLE', 'mes_check_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '进站:工装须IDLE可用'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'TOOLING_AVAILABLE');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352034, '000000', 4, '批次可用', 'LOT_AVAILABLE', 'mes_check_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '进站:批次须IN_STOCK+QC PASS'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'LOT_AVAILABLE');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352035, '000000', 5, '设备状态', 'EQUIPMENT_STATUS', 'mes_check_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '进站:工位设备须非FAULT'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'EQUIPMENT_STATUS');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352036, '000000', 6, '物料齐套', 'MATERIAL_KITTING', 'mes_check_type', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, '进站:物料齐套(存根,待P0-1)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'MATERIAL_KITTING');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352037, '000000', 7, '人员资质', 'OPERATOR_SKILL', 'mes_check_type', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, '进站:操作员资质(存根,待资质表)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'OPERATOR_SKILL');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352038, '000000', 8, '数量达标', 'QUANTITY_REACHED', 'mes_check_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '出站:良品+不良+报废=计划'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'QUANTITY_REACHED');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352039, '000000', 9, '检验完成', 'INSPECTION_REQUIRED', 'mes_check_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '出站:关键工序IPQC须PASS'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'INSPECTION_REQUIRED');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352040, '000000', 10, '异常已关闭', 'EXCEPTION_CLOSED', 'mes_check_type', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, '出站:本工序须无活跃安灯'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'EXCEPTION_CLOSED');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053352041, '000000', 11, '参数达标', 'PARAMETER_OK', 'mes_check_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '出站:工艺参数达标(存根,待dc)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_check_type' AND `dict_value` = 'PARAMETER_OK');

-- =============================================
-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, parent_id, menu_type, component, perms FROM sys_menu
--   WHERE menu_id IN (4200,4207,4208,4209,4210,4211,4212,4213,4214,4215,4216,4217,4218) ORDER BY menu_id;
-- 预期: 2 C 页面 + 11 F 按钮
-- SELECT dict_type, dict_label, dict_value FROM sys_dict_data
--   WHERE dict_type IN ('mes_guide_type','mes_check_stage','mes_check_type') ORDER BY dict_type, dict_sort;
-- 预期: 2 + 2 + 11 = 15 条
-- =============================================
