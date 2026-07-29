-- =============================================
-- NexusERP - ERP 采购合同字典数据
-- 目标库: ry-vue
-- 创建时间: 2026-06-26
-- 说明: Wave2 P1 补全 - 注册 contract_type / pu_contract_status 字典
-- 幂等: 使用 INSERT IGNORE
-- ID 调整说明: 计划原拟 600201-600217, 但 600201-600209 已被 IoT 字典
--            (product_type / iot_protocol_type / iot_connection_status / iot_device_status) 占用,
--            故调整至 600301-600317 段 (dict_type 与 dict_data 主键复用与既有
--            dict-bom-routing-process.sql 600106-600116 模式一致):
--              dict_type: 600301(contract_type) / 600302(pu_contract_status)
--              dict_data: 600301-600303(合同类型3值) / 600311-600317(合同状态7值)
-- =============================================

USE `ry-vue`;

-- 字典类型: 合同类型
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600301, '000000', '合同类型', 'contract_type', 103, 1, NOW(), NULL, NULL, '采购合同类型');

-- 字典类型: 采购合同状态
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600302, '000000', '采购合同状态', 'pu_contract_status', 103, 1, NOW(), NULL, NULL, '采购合同状态');

-- 字典数据: 合同类型 (3 个值)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600301, '000000', 1, '框架合同', 'FRAMEWORK', 'contract_type', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '长期框架协议'),
(600302, '000000', 2, '现货合同', 'SPOT', 'contract_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '单次现货采购'),
(600303, '000000', 3, '服务合同', 'SERVICE', 'contract_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '服务类合同');

-- 字典数据: 采购合同状态 (7 个值)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600311, '000000', 1, '草稿', 'DRAFT', 'pu_contract_status', '', 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600312, '000000', 2, '审批中', 'APPROVING', 'pu_contract_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600313, '000000', 3, '已批准', 'APPROVED', 'pu_contract_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600314, '000000', 4, '执行中', 'EXECUTING', 'pu_contract_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600315, '000000', 5, '已完成', 'COMPLETED', 'pu_contract_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600316, '000000', 6, '已终止', 'TERMINATED', 'pu_contract_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600317, '000000', 7, '已驳回', 'REJECTED', 'pu_contract_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 验证
-- SELECT dict_type, dict_label, dict_value FROM `sys_dict_data` WHERE `dict_type` IN ('contract_type', 'pu_contract_status') ORDER BY dict_type, dict_sort;
