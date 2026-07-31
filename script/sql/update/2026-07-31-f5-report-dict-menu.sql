-- =============================================
-- NexusERP - F5 财务报表与期末关账模块: 字典 + 菜单
-- 目标库: ry-vue
-- 创建时间: 2026-07-31
-- 所属: NexusERP-SPEC-2026-005-F5
-- 幂等: 使用 INSERT IGNORE
-- 字典段位: 6007xx (避让 6005xx F0 GL / 6006xx F1 ARAP / 6004xx 发票)
--   dict_type: 600701-600702
--   dict_data: 600711-600726
-- 菜单段位: 6130-6160 (避让 6100-6117 F0 / 6120-6129 F1 / 4241-4260 NCR / 26xx 条码)
-- 权威 schema 并入 script/sql/ry-vue.sql
-- =============================================

USE `ry-vue`;

-- ===== 字典类型 =====
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600701, '000000', '报表类型', 'fin_report_type', 103, 1, NOW(), NULL, NULL, '财务报表类型(BALANCE/INCOME/CASH)'),
(600702, '000000', '关账步骤', 'fin_closing_step', 103, 1, NOW(), NULL, NULL, '期末关账步骤状态');

-- ===== 字典数据 =====
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- fin_report_type
(600711, '000000', 1, '资产负债表', 'BALANCE', 'fin_report_type', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600712, '000000', 2, '利润表', 'INCOME', 'fin_report_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600713, '000000', 3, '现金流量表', 'CASH', 'fin_report_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- fin_closing_step
(600721, '000000', 1, '待执行', 'PENDING', 'fin_closing_step', NULL, 'info', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600722, '000000', 2, '试算校验', 'TRIAL_CHECK', 'fin_closing_step', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600723, '000000', 3, '损益结转', 'CARRY_PL', 'fin_closing_step', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600724, '000000', 4, '外币重估', 'REVALUATION', 'fin_closing_step', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600725, '000000', 5, '报表快照', 'REPORT_SNAPSHOT', 'fin_closing_step', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600726, '000000', 6, '关账完成', 'CLOSED', 'fin_closing_step', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL);

-- ===== 菜单:财务管理(6100) -> 财务报表(6130) / 期末关账(6140) =====
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- 目录:财务报表(挂在财务管理 6100 下,排在应收应付 6120 之后)
(6130, '财务报表', 6100, 3, 'report', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'chart', 103, 1, NOW(), NULL, NULL, '三大报表+项目配置'),
(6140, '期末关账', 6100, 4, 'closing', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'lock', 103, 1, NOW(), NULL, NULL, '月结/年结向导'),
-- 报表页面
(6131, '资产负债表', 6130, 1, 'balance', 'erp/fin/report/balance', NULL, 1, 0, 'C', '0', '0', 'fin:report:list', 'table', 103, 1, NOW(), NULL, NULL, '按期间取数,资产=负债+权益'),
(6132, '利润表', 6130, 2, 'income', 'erp/fin/report/income', NULL, 1, 0, 'C', '0', '0', 'fin:report:list', 'table', 103, 1, NOW(), NULL, NULL, '本期/本年累计'),
(6133, '现金流量表', 6130, 3, 'cashFlow', 'erp/fin/report/cashFlow', NULL, 1, 0, 'C', '0', '0', 'fin:report:list', 'table', 103, 1, NOW(), NULL, NULL, '直接法基础版'),
(6134, '报表项目配置', 6130, 4, 'itemConfig', 'erp/fin/report/itemConfig', NULL, 1, 0, 'C', '0', '0', 'fin:report:edit', 'settings', 103, 1, NOW(), NULL, NULL, '报表项目-科目映射'),
-- 期末关账页面
(6141, '月结向导', 6140, 1, 'monthly', 'erp/fin/closing/monthly', NULL, 1, 0, 'C', '0', '0', 'fin:closing:list', 'guide', 103, 1, NOW(), NULL, NULL, '试算校验→损益结转→外币重估→关账'),
(6142, '年结', 6140, 2, 'yearly', 'erp/fin/closing/yearly', NULL, 1, 0, 'C', '0', '0', 'fin:closing:yearly', 'calendar', 103, 1, NOW(), NULL, NULL, '本年利润结转+期初结转'),
-- 报表项目配置按钮
(6135, '项目配置新增', 6134, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:report:item:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(6136, '项目配置修改', 6134, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:report:item:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
-- 月结向导按钮
(6143, '损益结转', 6141, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:closing:carry', '#', 103, 1, NOW(), NULL, NULL, '损益类科目结转本年利润'),
(6144, '外币重估', 6141, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:closing:reval', '#', 103, 1, NOW(), NULL, NULL, '期末汇率重估'),
(6145, '执行关账', 6141, 3, '', '', '', 1, 0, 'F', '0', '0', 'fin:closing:close', '#', 103, 1, NOW(), NULL, NULL, '月结整体执行'),
(6146, '关账日志', 6141, 4, '', '', '', 1, 0, 'F', '0', '0', 'fin:closing:logs', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证
-- SELECT dict_type, dict_label, dict_value FROM `sys_dict_data` WHERE dict_type LIKE 'fin_%' ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'fin:report:%' OR `perms` LIKE 'fin:closing:%' ORDER BY menu_id;
