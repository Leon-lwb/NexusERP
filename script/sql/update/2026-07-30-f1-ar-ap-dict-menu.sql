-- ============================================================
-- F1 AR/AP 核销:字典 + 菜单
-- 库: ry-vue (系统库)
-- 日期: 2026-07-30
-- 段位避让:字典 6006xx (F0 用 6005xx);菜单 6120-6129 (F0 用 6100-6117)
-- ============================================================
SET NAMES utf8mb4;

-- ===== 字典类型(6006xx) =====
-- 复用已有(6004xx 发票域段):fin_receipt_type(600406 收付款类型)、fin_receipt_direction(600407 收付款方向)
-- 本脚本仅补充发票域未建的:fin_receipt_source / fin_receipt_status
INSERT IGNORE INTO `sys_dict_type`(`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600603, '000000', '收付款来源', 'fin_receipt_source',  103, 1, NOW(), NULL, NULL, 'F1 收付款单来源'),
(600604, '000000', '收付款状态', 'fin_receipt_status',  103, 1, NOW(), NULL, NULL, 'F1 收付款单状态');

-- ===== 字典数据(6006xx) =====
INSERT IGNORE INTO `sys_dict_data`(`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- fin_receipt_source
(600631, '000000', 1, '手工录入',     'MANUAL',       'fin_receipt_source', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600632, '000000', 2, '支付回调',     'PAY_CALLBACK', 'fin_receipt_source', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- fin_receipt_status
(600641, '000000', 1, '草稿',     'DRAFT',     'fin_receipt_status', NULL, 'info',    'N', 103, 1, NOW(), NULL, NULL, NULL),
(600642, '000000', 2, '已确认',   'CONFIRMED', 'fin_receipt_status', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600643, '000000', 3, '已结算',   'SETTLED',   'fin_receipt_status', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600644, '000000', 4, '已作废',   'CANCELLED', 'fin_receipt_status', NULL, 'danger',  'N', 103, 1, NOW(), NULL, NULL, NULL);

-- ===== 菜单:财务管理(6100) -> 应收应付(6120) -> 收付款单/核销/报表(6121-6123) + 按钮(6124-6129) =====
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- 目录:应收应付(挂在财务管理 6100 下,排在总账 6101 之后)
(6120, '应收应付', 6100, 2, 'arap', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'coin', 103, 1, NOW(), NULL, NULL, 'AR/AP 核销'),
-- 页面
(6121, '收付款单',   6120, 1, 'receipt',     'erp/fin/receipt/index',         NULL, 1, 0, 'C', '0', '0', 'fin:arap:receipt:list',     'money',      103, 1, NOW(), NULL, NULL, 'AR收款/AP付款单'),
(6122, '核销管理',   6120, 2, 'settlement',  'erp/fin/settlement/index',      NULL, 1, 0, 'C', '0', '0', 'fin:arap:settlement:list',  'cascader',   103, 1, NOW(), NULL, NULL, '发票-收付款核销'),
(6123, '应收应付报表', 6120, 3, 'arap-report', 'erp/fin/arap/report/index',   NULL, 1, 0, 'C', '0', '0', 'fin:arap:report:list',      'chart',      103, 1, NOW(), NULL, NULL, '应收应付明细'),
-- 收付款单按钮
(6124, '收付款单查询', 6121, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:arap:receipt:query',   '#', 103, 1, NOW(), NULL, NULL, ''),
(6125, '收付款单新增', 6121, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:arap:receipt:add',     '#', 103, 1, NOW(), NULL, NULL, ''),
(6126, '收付款单确认', 6121, 3, '', '', '', 1, 0, 'F', '0', '0', 'fin:arap:receipt:confirm', '#', 103, 1, NOW(), NULL, NULL, '确认后触发GL过账'),
(6127, '收付款单作废', 6121, 4, '', '', '', 1, 0, 'F', '0', '0', 'fin:arap:receipt:cancel',  '#', 103, 1, NOW(), NULL, NULL, ''),
-- 核销按钮
(6128, '核销查询', 6122, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:arap:settlement:query',  '#', 103, 1, NOW(), NULL, NULL, ''),
(6129, '核销操作', 6122, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:arap:settlement:settle', '#', 103, 1, NOW(), NULL, NULL, '发票-收付款核销');

-- 校验
-- SELECT dict_type, dict_label, dict_value FROM sys_dict_data WHERE dict_type LIKE 'fin_%' ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, parent_id, perms FROM sys_menu WHERE perms LIKE 'fin:arap:%' ORDER BY menu_id;
