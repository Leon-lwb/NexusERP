-- =============================================
-- NexusERP - F2 固定资产 FA 模块: 字典 + 菜单
-- 目标库: ry-vue
-- 创建时间: 2026-08-01
-- 所属: NexusERP-SPEC-2026-006-F2
-- 幂等: 使用 INSERT IGNORE
-- 字典段位: 6008xx (避让 6005xx F0 GL / 6006xx F1 ARAP / 6007xx F5 / 6004xx 发票)
--   dict_type: 600801-600803
--   dict_data: 600811-600826
-- 菜单段位: 6161-6175 (避让 6100-6117 F0 / 6120-6129 F1 / 6130-6160 F5 / 4241-4260 NCR / 26xx 条码)
-- 权威 schema 并入 script/sql/ry-vue.sql
-- =============================================

USE `ry-vue`;

-- ===== 字典类型 =====
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600801, '000000', '资产状态', 'fa_asset_status', 103, 1, NOW(), NULL, NULL, '固定资产状态(IN_USE/CWIP/DISPOSED)'),
(600802, '000000', '折旧方法', 'fa_depreciation_method', 103, 1, NOW(), NULL, NULL, '折旧方法(STRAIGHT_LINE/DOUBLE_DECLINING/SUM_OF_YEARS/MANUAL)'),
(600803, '000000', '处置类型', 'fa_disposal_type', 103, 1, NOW(), NULL, NULL, '资产处置类型(SALE/SCRAP)');

-- ===== 字典数据 =====
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- fa_asset_status
(600811, '000000', 1, '使用中', 'IN_USE', 'fa_asset_status', NULL, 'success', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600812, '000000', 2, '在建', 'CWIP', 'fa_asset_status', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600813, '000000', 3, '已处置', 'DISPOSED', 'fa_asset_status', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- fa_depreciation_method
(600814, '000000', 1, '直线法', 'STRAIGHT_LINE', 'fa_depreciation_method', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600815, '000000', 2, '双倍余额递减法', 'DOUBLE_DECLINING', 'fa_depreciation_method', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600816, '000000', 3, '年数总和法', 'SUM_OF_YEARS', 'fa_depreciation_method', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600817, '000000', 4, '手工', 'MANUAL', 'fa_depreciation_method', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- fa_disposal_type
(600818, '000000', 1, '出售', 'SALE', 'fa_disposal_type', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600819, '000000', 2, '报废', 'SCRAP', 'fa_disposal_type', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, NULL);

-- ===== 菜单:财务管理(6100) -> 固定资产(6161) =====
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- 目录:固定资产(挂在财务管理 6100 下,排在期末关账 6140 之后)
(6161, '固定资产', 6100, 5, 'fa', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'office-building', 103, 1, NOW(), NULL, NULL, '资产卡片/折旧/处置'),
-- 页面
(6162, '资产卡片', 6161, 1, 'asset', 'erp/fin/fa/asset', NULL, 1, 0, 'C', '0', '0', 'fin:fa:asset:list', 'list', 103, 1, NOW(), NULL, NULL, '资产全生命周期'),
(6163, '资产类别', 6161, 2, 'category', 'erp/fin/fa/category', NULL, 1, 0, 'C', '0', '0', 'fin:fa:category:list', 'set-up', 103, 1, NOW(), NULL, NULL, '类别+折旧政策'),
(6164, '折旧管理', 6161, 3, 'depreciation', 'erp/fin/fa/depreciation', NULL, 1, 0, 'C', '0', '0', 'fin:fa:depreciation:list', 'timer', 103, 1, NOW(), NULL, NULL, '计提/计划/历史'),
(6165, '资产处置', 6161, 4, 'disposal', 'erp/fin/fa/disposal', NULL, 1, 0, 'C', '0', '0', 'fin:fa:disposal:list', 'delete', 103, 1, NOW(), NULL, NULL, '出售/报废'),
-- 资产卡片按钮
(6166, '卡片新增', 6162, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:asset:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(6167, '卡片修改', 6162, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:asset:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
(6168, '卡片删除', 6162, 3, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:asset:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
(6169, '原值调整', 6162, 4, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:asset:adjust', '#', 103, 1, NOW(), NULL, NULL, ''),
(6170, '转固', 6162, 5, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:asset:activate', '#', 103, 1, NOW(), NULL, NULL, '在建转使用'),
-- 类别按钮
(6171, '类别新增', 6163, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:category:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(6172, '类别修改', 6163, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:category:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
(6173, '类别删除', 6163, 3, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:category:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
-- 折旧/处置按钮
(6174, '计提折旧', 6164, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:depreciation:run', '#', 103, 1, NOW(), NULL, NULL, '按期间计提并过账'),
(6175, '资产处置', 6165, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:fa:disposal:post', '#', 103, 1, NOW(), NULL, NULL, '出售/报废并过账');

-- 验证
-- SELECT dict_type, dict_label, dict_value FROM `sys_dict_data` WHERE dict_type LIKE 'fa_%' ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'fin:fa:%' ORDER BY menu_id;
