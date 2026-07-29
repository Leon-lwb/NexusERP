-- =============================================
-- NexusERP - 开票与应收应付优化 P0: 发票/收付款字典
-- 目标库: ry-vue
-- 创建时间: 2026-07-15
-- 说明: 注册发票类型/状态、进项认证状态、收付款类型/方向、开票渠道字典
-- 幂等: 使用 INSERT IGNORE
-- ID 段位: 6004xx (避让 6001xx bom-routing / 6002xx IoT / 6003xx pu-contract)
--   dict_type: 600401-600408
--   dict_data: 600411-600482
-- 关联设计: doc/erp/specs/2026-07-15-invoicing-ar-ap-redesign.md 第四节 4.6
-- =============================================

USE `ry-vue`;

-- ===== 字典类型 =====
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600401, '000000', '销售发票类型', 'so_invoice_type', 103, 1, NOW(), NULL, NULL, '销售发票类型'),
(600402, '000000', '销售发票状态', 'so_invoice_status', 103, 1, NOW(), NULL, NULL, '销售发票状态'),
(600403, '000000', '采购进项发票类型', 'pu_invoice_type', 103, 1, NOW(), NULL, NULL, '采购进项发票类型'),
(600404, '000000', '采购进项发票状态', 'pu_invoice_status', 103, 1, NOW(), NULL, NULL, '采购进项发票状态'),
(600405, '000000', '进项发票认证状态', 'pu_invoice_cert_status', 103, 1, NOW(), NULL, NULL, '进项发票认证状态'),
(600406, '000000', '收付款类型', 'fin_receipt_type', 103, 1, NOW(), NULL, NULL, '收付款类型'),
(600407, '000000', '收付款方向', 'fin_receipt_direction', 103, 1, NOW(), NULL, NULL, '收付款方向 AR应收 AP应付'),
(600408, '000000', '开票渠道', 'invoice_issue_channel', 103, 1, NOW(), NULL, NULL, '开票渠道');

-- ===== 字典数据 =====

-- 销售发票类型 (3)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600411, '000000', 1, '增值税专用发票', 'VAT_SPECIAL', 'so_invoice_type', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600412, '000000', 2, '增值税普通发票', 'VAT_NORMAL', 'so_invoice_type', '', 'success', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600413, '000000', 3, '其他', 'OTHER', 'so_invoice_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 销售发票状态 (6)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600421, '000000', 1, '草稿', 'DRAFT', 'so_invoice_status', '', 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600422, '000000', 2, '审批中', 'APPROVING', 'so_invoice_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600423, '000000', 3, '已开', 'ISSUED', 'so_invoice_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600424, '000000', 4, '已核销', 'VERIFIED', 'so_invoice_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600425, '000000', 5, '已驳回', 'REJECTED', 'so_invoice_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600426, '000000', 6, '已作废', 'CANCELLED', 'so_invoice_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 采购进项发票类型 (3)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600431, '000000', 1, '增值税专用发票', 'VAT_SPECIAL', 'pu_invoice_type', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '可抵扣进项'),
(600432, '000000', 2, '增值税普通发票', 'VAT_NORMAL', 'pu_invoice_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600433, '000000', 3, '其他', 'OTHER', 'pu_invoice_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 采购进项发票状态 (4)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600441, '000000', 1, '草稿', 'DRAFT', 'pu_invoice_status', '', 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600442, '000000', 2, '已收', 'ISSUED', 'pu_invoice_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600443, '000000', 3, '已核销', 'VERIFIED', 'pu_invoice_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600444, '000000', 4, '已作废', 'CANCELLED', 'pu_invoice_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 进项发票认证状态 (4)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600451, '000000', 1, '未认证', 'UNCERTIFIED', 'pu_invoice_cert_status', '', 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600452, '000000', 2, '已认证', 'CERTIFIED', 'pu_invoice_cert_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600453, '000000', 3, '已抵扣', 'DEDUCTED', 'pu_invoice_cert_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600454, '000000', 4, '已转出', 'TRANSFERRED', 'pu_invoice_cert_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '退货进项转出');

-- 收付款类型 (5)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600461, '000000', 1, '现金', 'CASH', 'fin_receipt_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600462, '000000', 2, '银行', 'BANK', 'fin_receipt_type', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600463, '000000', 3, '支付宝', 'ALIPAY', 'fin_receipt_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600464, '000000', 4, '微信', 'WECHAT', 'fin_receipt_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600465, '000000', 5, '其他', 'OTHER', 'fin_receipt_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 收付款方向 (2)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600471, '000000', 1, '应收(收款)', 'AR', 'fin_receipt_direction', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600472, '000000', 2, '应付(付款)', 'AP', 'fin_receipt_direction', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 开票渠道 (2)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600481, '000000', 1, '手工', 'MANUAL', 'invoice_issue_channel', '', 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600482, '000000', 2, '电子发票平台', 'ELECTRONIC', 'invoice_issue_channel', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '数电票');

-- 验证
-- SELECT dict_type, dict_label, dict_value FROM `sys_dict_data` WHERE `dict_type` LIKE 'so_invoice%' OR `dict_type` LIKE 'pu_invoice%' OR `dict_type` LIKE 'fin_receipt%' OR `dict_type` = 'invoice_issue_channel' ORDER BY dict_type, dict_sort;
