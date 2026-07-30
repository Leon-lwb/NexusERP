-- =============================================
-- NexusERP - F0 GL 总账模块: 字典 + 菜单
-- 目标库: ry-vue
-- 创建时间: 2026-07-29
-- 所属: NexusERP-SPEC-2026-005-F0
-- 幂等: 使用 INSERT IGNORE
-- 字典段位: 6005xx (避让 6001xx bom-routing / 6002xx IoT / 6003xx pu-contract / 6004xx invoicing)
--   dict_type: 600501-600506
--   dict_data: 600511-600563
-- 菜单段位: 6100-6117 (避让 25xx 采购销售发票 / 26xx 条码 / 42xx NCR / 50xx-60xx / 9000+)
-- 权威 schema 并入 script/sql/ry-vue.sql
-- =============================================

USE `ry-vue`;

-- ===== 字典类型 =====
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600501, '000000', '科目类型', 'gl_account_type', 103, 1, NOW(), NULL, NULL, 'GL 会计科目类型'),
(600502, '000000', '余额方向', 'gl_balance_dir', 103, 1, NOW(), NULL, NULL, 'GL 科目余额方向'),
(600503, '000000', '凭证字', 'gl_voucher_word', 103, 1, NOW(), NULL, NULL, 'GL 记账凭证字'),
(600504, '000000', '凭证状态', 'gl_voucher_status', 103, 1, NOW(), NULL, NULL, 'GL 凭证状态'),
(600505, '000000', '凭证来源', 'gl_voucher_source', 103, 1, NOW(), NULL, NULL, 'GL 凭证来源'),
(600506, '000000', '期间状态', 'gl_period_status', 103, 1, NOW(), NULL, NULL, 'GL 会计期间状态');

-- ===== 字典数据 =====
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- gl_account_type
(600511, '000000', 1, '资产', 'ASSET', 'gl_account_type', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600512, '000000', 2, '负债', 'LIABILITY', 'gl_account_type', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600513, '000000', 3, '权益', 'EQUITY', 'gl_account_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600514, '000000', 4, '成本', 'COST', 'gl_account_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600515, '000000', 5, '收入', 'INCOME', 'gl_account_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600516, '000000', 6, '费用', 'EXPENSE', 'gl_account_type', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- gl_balance_dir
(600521, '000000', 1, '借', 'DEBIT', 'gl_balance_dir', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600522, '000000', 2, '贷', 'CREDIT', 'gl_balance_dir', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- gl_voucher_word
(600531, '000000', 1, '记', 'J', 'gl_voucher_word', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600532, '000000', 2, '收', 'R', 'gl_voucher_word', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600533, '000000', 3, '付', 'P', 'gl_voucher_word', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600534, '000000', 4, '转', 'T', 'gl_voucher_word', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- gl_voucher_status
(600541, '000000', 1, '草稿', 'DRAFT', 'gl_voucher_status', NULL, 'info', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600542, '000000', 2, '已过账', 'POSTED', 'gl_voucher_status', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600543, '000000', 3, '已作废', 'CANCELLED', 'gl_voucher_status', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- gl_voucher_source
(600551, '000000', 1, '手工', 'MANUAL', 'gl_voucher_source', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600552, '000000', 2, '销售发票', 'SO_INVOICE', 'gl_voucher_source', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600553, '000000', 3, '采购发票', 'PU_INVOICE', 'gl_voucher_source', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600554, '000000', 4, '收付款', 'RECEIPT', 'gl_voucher_source', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600555, '000000', 5, '折旧', 'DEPRECIATION', 'gl_voucher_source', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600556, '000000', 6, '成本', 'COST', 'gl_voucher_source', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
-- gl_period_status
(600561, '000000', 1, '开放', 'OPEN', 'gl_period_status', NULL, 'success', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(600562, '000000', 2, '已关账', 'CLOSED', 'gl_period_status', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(600563, '000000', 3, '已锁定', 'LOCKED', 'gl_period_status', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, NULL);

-- ===== 菜单:财务管理(6100) -> 总账(6101) -> 科目表/凭证/期间/汇率/试算(6102-6106) + 按钮(6107-6117) =====
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- 目录
(6100, '财务管理', 2000, 22, 'fin', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'money', 103, 1, NOW(), NULL, NULL, '财务核心模块(ERP 子域)'),
(6101, '总账', 6100, 1, 'gl', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'ledger', 103, 1, NOW(), NULL, NULL, 'GL 总账'),
-- 菜单页
(6102, '科目表', 6101, 1, 'account', 'erp/fin/gl/account/index', NULL, 1, 0, 'C', '0', '0', 'fin:gl:account:list', 'tree-table', 103, 1, NOW(), NULL, NULL, '会计科目树'),
(6103, '记账凭证', 6101, 2, 'voucher', 'erp/fin/gl/voucher/index', NULL, 1, 0, 'C', '0', '0', 'fin:gl:voucher:list', 'edit', 103, 1, NOW(), NULL, NULL, '记账凭证'),
(6104, '会计期间', 6101, 3, 'period', 'erp/fin/gl/period/index', NULL, 1, 0, 'C', '0', '0', 'fin:gl:period:list', 'date', 103, 1, NOW(), NULL, NULL, '会计期间'),
(6105, '币种汇率', 6101, 4, 'currency-rate', 'erp/fin/gl/currencyRate/index', NULL, 1, 0, 'C', '0', '0', 'fin:gl:currency:list', 'rate', 103, 1, NOW(), NULL, NULL, '币种汇率'),
(6106, '试算平衡表', 6101, 5, 'trial-balance', 'erp/fin/gl/report/trialBalance', NULL, 1, 0, 'C', '0', '0', 'fin:gl:report:list', 'chart', 103, 1, NOW(), NULL, NULL, '试算平衡表'),
-- 科目表按钮
(6107, '科目查询', 6102, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:account:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(6108, '科目新增', 6102, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:account:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(6109, '科目修改', 6102, 3, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:account:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
(6110, '科目删除', 6102, 4, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:account:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
-- 凭证按钮
(6111, '凭证查询', 6103, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:voucher:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(6112, '凭证新增', 6103, 2, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:voucher:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(6113, '凭证修改', 6103, 3, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:voucher:edit', '#', 103, 1, NOW(), NULL, NULL, '仅草稿'),
(6114, '凭证删除', 6103, 4, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:voucher:remove', '#', 103, 1, NOW(), NULL, NULL, '仅草稿'),
(6115, '凭证过账', 6103, 5, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:voucher:post', '#', 103, 1, NOW(), NULL, NULL, '借贷平衡+期间校验'),
(6116, '凭证反过账', 6103, 6, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:voucher:unpost', '#', 103, 1, NOW(), NULL, NULL, '期间未锁定'),
-- 期间按钮
(6117, '期间维护', 6104, 1, '', '', '', 1, 0, 'F', '0', '0', 'fin:gl:period:edit', '#', 103, 1, NOW(), NULL, NULL, '关账/锁定/重开/生成');

-- 验证
-- SELECT dict_type, dict_label, dict_value FROM `sys_dict_data` WHERE dict_type LIKE 'gl_%' ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'fin:gl:%' ORDER BY menu_id;
