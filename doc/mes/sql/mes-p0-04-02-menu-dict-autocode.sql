-- =============================================
-- NexusERP - MES模块 P0-4 NCR不合格品+返工 菜单+字典+自动编码 SQL
-- 目标库: ry-vue (sys_menu / sys_role_menu / sys_dict_type / sys_dict_data / sys_auto_code_rule / sys_auto_code_part)
-- 父菜单: 4002 (质量管理)
-- 菜单ID段:
--   4241         M 目录 NCR管理 (parent=4002)
--   4242         C 页面 NCR不合格品单 (mes/ncr/order/index)
--   4243         C 页面 缺陷代码库 (mes/ncr/defect-code/index)
--   4244         C 页面 返工工单 (mes/ncr/rework/index)
--   4245         C 页面 8D报告 (mes/ncr/8d-report/index)
--   4246~4253    F 按钮 NCR query/add/edit/remove/review/dispose/export + 缺陷明细补录 (parent=4242)
--   4254~4257    F 按钮 缺陷代码 query/add/edit/remove (parent=4243)
--   4258         F 按钮 返工查询 (parent=4244)
--   4259~4260    F 按钮 8D edit/complete (parent=4245)
-- 字典:
--   mes_ncr_status       OPEN/REVIEW/DISPOSITION/IN_REWORK/CLOSED
--   mes_ncr_source_type  IQC/IPQC/OQC/PROCESS/CUSTOMER
--   mes_ncr_disposition  REWORK/SCRAP/ACCEPT_AS_IS/RETURN
--   mes_defect_cause     MAN/MACHINE/MATERIAL/METHOD/ENVIRONMENT/MEASUREMENT
--   mes_rework_status    OPEN/IN_PROGRESS/COMPLETED
--   mes_8d_status        DRAFT/IN_PROGRESS/COMPLETED
--   mes_wo_source_type   ERP/REWORK/MANUAL
-- 自动编码:
--   MES_NCR_CODE     NCR + yyyyMMdd + 3位日循环
--   MES_REWORK_CODE  RW  + yyyyMMdd + 3位日循环
-- 幂等: INSERT IGNORE + WHERE NOT EXISTS, 可重复执行
-- =============================================
USE `ry-vue`;

-- -----------------------------------------------------------
-- 一、NCR管理目录 (parent=4002 质量管理)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4241, 'NCR管理', 4002, 5, 'ncr', NULL, '', 1, 0, 'M', '0', '0', '', 'bug', 103, 1, NOW(), NULL, NULL, 'MES不合格品(NCR)管理');

-- -----------------------------------------------------------
-- 二、NCR不合格品单 (parent=4241)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4242, 'NCR不合格品单', 4241, 1, 'ncr-order', 'mes/ncr/order/index', '', 1, 0, 'C', '0', '0', 'mes:ncr:order:list', 'edit', 103, 1, NOW(), NULL, NULL, 'NCR不合格品处理单(状态机/处置/隔离)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4246, 'NCR查询', 4242, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:order:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4247, 'NCR新增', 4242, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:order:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4248, 'NCR修改', 4242, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:order:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4249, 'NCR删除', 4242, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:order:remove', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4250, 'NCR评审', 4242, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:order:review', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4251, 'NCR处置', 4242, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:order:dispose', '#', 103, 1, NOW(), NULL, NULL, '处置决策(返工/报废/让步/退货)');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4252, 'NCR导出', 4242, 7, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:order:export', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4253, '缺陷明细补录', 4242, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:defect:add', '#', 103, 1, NOW(), NULL, NULL, 'NCR缺陷明细补录');

-- -----------------------------------------------------------
-- 三、缺陷代码库 (parent=4241)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4243, '缺陷代码库', 4241, 2, 'defect-code', 'mes/ncr/defect-code/index', '', 1, 0, 'C', '0', '0', 'mes:ncr:defectcode:list', 'tree-table', 103, 1, NOW(), NULL, NULL, '分层缺陷代码(类别/子类/代码)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4254, '缺陷代码查询', 4243, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:defectcode:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4255, '缺陷代码新增', 4243, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:defectcode:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4256, '缺陷代码修改', 4243, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:defectcode:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4257, '缺陷代码删除', 4243, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:defectcode:remove', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 四、返工工单 (parent=4241)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4244, '返工工单', 4241, 3, 'rework', 'mes/ncr/rework/index', '', 1, 0, 'C', '0', '0', 'mes:ncr:rework:list', 'tool', 103, 1, NOW(), NULL, NULL, 'NCR派生返工工单(关联原工单)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4258, '返工查询', 4244, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:rework:query', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 五、8D报告 (parent=4241)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4245, '8D报告', 4241, 4, '8d-report', 'mes/ncr/8d-report/index', '', 1, 0, 'C', '0', '0', 'mes:ncr:8d:list', 'documentation', 103, 1, NOW(), NULL, NULL, '8D问题解决报告(D1-D8)');

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4259, '8D编辑', 4245, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:8d:edit', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (4260, '8D完成', 4245, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:ncr:8d:complete', '#', 103, 1, NOW(), NULL, NULL, '');

-- -----------------------------------------------------------
-- 六、角色授权 (role_id=3, 同 P0-2/P0-3/P0-9 约定)
-- -----------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 4241), (3, 4242), (3, 4243), (3, 4244), (3, 4245),
(3, 4246), (3, 4247), (3, 4248), (3, 4249), (3, 4250),
(3, 4251), (3, 4252), (3, 4253), (3, 4254), (3, 4255),
(3, 4256), (3, 4257), (3, 4258), (3, 4259), (3, 4260);

-- =============================================
-- 七、字典: mes_ncr_status (NCR 状态)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356001, '000000', 'MES NCR状态', 'mes_ncr_status', 103, 1, NOW(), NULL, NULL, 'NCR不合格品单生命周期状态(P0-4)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_ncr_status');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356011, '000000', 1, '待处理', 'OPEN', 'mes_ncr_status', NULL, 'info', 'Y', 103, 1, NOW(), NULL, NULL, '新建'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_status' AND `dict_value` = 'OPEN');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356012, '000000', 2, '评审中', 'REVIEW', 'mes_ncr_status', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '评审中'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_status' AND `dict_value` = 'REVIEW');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356013, '000000', 3, '已处置', 'DISPOSITION', 'mes_ncr_status', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '处置决策已定'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_status' AND `dict_value` = 'DISPOSITION');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356014, '000000', 4, '返工中', 'IN_REWORK', 'mes_ncr_status', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '返工工单执行中'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_status' AND `dict_value` = 'IN_REWORK');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356015, '000000', 5, '已关闭', 'CLOSED', 'mes_ncr_status', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '已关闭'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_status' AND `dict_value` = 'CLOSED');

-- =============================================
-- 八、字典: mes_ncr_source_type (NCR 来源类型)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356002, '000000', 'MES NCR来源类型', 'mes_ncr_source_type', 103, 1, NOW(), NULL, NULL, 'NCR触发来源(P0-4)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_ncr_source_type');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356021, '000000', 1, '来料检验', 'IQC', 'mes_ncr_source_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, 'IQC判定不合格'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_source_type' AND `dict_value` = 'IQC');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356022, '000000', 2, '过程检验', 'IPQC', 'mes_ncr_source_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, 'IPQC判定不合格'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_source_type' AND `dict_value` = 'IPQC');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356023, '000000', 3, '出货检验', 'OQC', 'mes_ncr_source_type', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, 'OQC判定不合格'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_source_type' AND `dict_value` = 'OQC');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356024, '000000', 4, '工序报工', 'PROCESS', 'mes_ncr_source_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '出站报工不良(defectQty>0)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_source_type' AND `dict_value` = 'PROCESS');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356025, '000000', 5, '客户投诉', 'CUSTOMER', 'mes_ncr_source_type', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, '客户退货/投诉手工建单'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_source_type' AND `dict_value` = 'CUSTOMER');

-- =============================================
-- 九、字典: mes_ncr_disposition (NCR 处置)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356003, '000000', 'MES NCR处置', 'mes_ncr_disposition', 103, 1, NOW(), NULL, NULL, 'NCR处置决策(P0-4)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_ncr_disposition');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356031, '000000', 1, '返工', 'REWORK', 'mes_ncr_disposition', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '派生返工工单'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_disposition' AND `dict_value` = 'REWORK');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356032, '000000', 2, '报废', 'SCRAP', 'mes_ncr_disposition', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, '批次报废'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_disposition' AND `dict_value` = 'SCRAP');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356033, '000000', 3, '让步接收', 'ACCEPT_AS_IS', 'mes_ncr_disposition', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, '解隔离放行'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_disposition' AND `dict_value` = 'ACCEPT_AS_IS');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356034, '000000', 4, '退货', 'RETURN', 'mes_ncr_disposition', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '退回供应商/客户'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_ncr_disposition' AND `dict_value` = 'RETURN');

-- =============================================
-- 十、字典: mes_defect_cause (缺陷原因 人机料法环测)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356004, '000000', 'MES缺陷原因', 'mes_defect_cause', 103, 1, NOW(), NULL, NULL, '缺陷归因5M1E(P0-4)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_defect_cause');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356041, '000000', 1, '人', 'MAN', 'mes_defect_cause', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, '人员'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_defect_cause' AND `dict_value` = 'MAN');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356042, '000000', 2, '机', 'MACHINE', 'mes_defect_cause', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '设备/工装'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_defect_cause' AND `dict_value` = 'MACHINE');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356043, '000000', 3, '料', 'MATERIAL', 'mes_defect_cause', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '物料'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_defect_cause' AND `dict_value` = 'MATERIAL');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356044, '000000', 4, '法', 'METHOD', 'mes_defect_cause', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '方法/工艺'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_defect_cause' AND `dict_value` = 'METHOD');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356045, '000000', 5, '环', 'ENVIRONMENT', 'mes_defect_cause', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, '环境'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_defect_cause' AND `dict_value` = 'ENVIRONMENT');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356046, '000000', 6, '测', 'MEASUREMENT', 'mes_defect_cause', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, '测量'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_defect_cause' AND `dict_value` = 'MEASUREMENT');

-- =============================================
-- 十一、字典: mes_rework_status (返工工单状态)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356005, '000000', 'MES返工工单状态', 'mes_rework_status', 103, 1, NOW(), NULL, NULL, '返工工单状态(P0-4)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_rework_status');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356051, '000000', 1, '待返工', 'OPEN', 'mes_rework_status', NULL, 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_rework_status' AND `dict_value` = 'OPEN');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356052, '000000', 2, '返工中', 'IN_PROGRESS', 'mes_rework_status', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_rework_status' AND `dict_value` = 'IN_PROGRESS');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356053, '000000', 3, '已完成', 'COMPLETED', 'mes_rework_status', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_rework_status' AND `dict_value` = 'COMPLETED');

-- =============================================
-- 十二、字典: mes_8d_status (8D报告状态)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356006, '000000', 'MES 8D报告状态', 'mes_8d_status', 103, 1, NOW(), NULL, NULL, '8D报告状态(P0-4)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_8d_status');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356061, '000000', 1, '草稿', 'DRAFT', 'mes_8d_status', NULL, 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_8d_status' AND `dict_value` = 'DRAFT');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356062, '000000', 2, '进行中', 'IN_PROGRESS', 'mes_8d_status', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_8d_status' AND `dict_value` = 'IN_PROGRESS');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356063, '000000', 3, '已完成', 'COMPLETED', 'mes_8d_status', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_8d_status' AND `dict_value` = 'COMPLETED');

-- =============================================
-- 十三、字典: mes_wo_source_type (工单来源)
-- =============================================
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356007, '000000', 'MES工单来源', 'mes_wo_source_type', 103, 1, NOW(), NULL, NULL, '工单来源类型(P0-4)'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'mes_wo_source_type');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356071, '000000', 1, 'ERP下发', 'ERP', 'mes_wo_source_type', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, 'ERP工单下发'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_wo_source_type' AND `dict_value` = 'ERP');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356072, '000000', 2, '返工工单', 'REWORK', 'mes_wo_source_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, 'NCR派生返工工单'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_wo_source_type' AND `dict_value` = 'REWORK');
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2068956987053356073, '000000', 3, '手工创建', 'MANUAL', 'mes_wo_source_type', NULL, 'info', 'N', 103, 1, NOW(), NULL, NULL, '手工建单'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'mes_wo_source_type' AND `dict_value` = 'MANUAL');

-- =============================================
-- 十四、自动编码规则: MES_NCR_CODE (NCR + yyyyMMdd + 3位日循环)
-- 目标库: ry-vue (sys_auto_code_rule / sys_auto_code_part)
-- 注意: 本地库 sys_auto_code_rule 实际列为 is_padded/padded_method/enable_flag(非 ry-vue.sql 规范库的 is_seq_cycle/rule_type/is_enabled);
--       sys_auto_code_part 实际列为 part_code/part_name/part_length/fix_character/seria_start_no/seria_step/cycle_flag/cycle_method。
--       镜像既有 MES_LOT_CODE(rule_id=284) 结构。
-- 幂等: FROM-less SELECT + WHERE NOT EXISTS 控制行数(0或1), MAX 放标量子查询(避免聚合返回 NULL 行)
-- =============================================
INSERT INTO `sys_auto_code_rule` (`rule_id`, `tenant_id`, `rule_code`, `rule_name`, `rule_desc`, `max_length`, `is_padded`, `padded_char`, `padded_method`, `enable_flag`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`rule_id`)+1 FROM `sys_auto_code_rule`), '000000', 'MES_NCR_CODE', 'MES NCR编码', 'NCR+日期+3位日循环流水号', 14, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_NCR_CODE');

-- part 1: FIXCHAR 前缀 'NCR'
INSERT INTO `sys_auto_code_part` (`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`part_id`)+1 FROM `sys_auto_code_part`), '000000', (SELECT `rule_id` FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_NCR_CODE'), 1, 'FIXCHAR', 'PREFIX', '前缀', 3, NULL, NULL, 'NCR', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_part` p JOIN `sys_auto_code_rule` r ON p.rule_id = r.rule_id WHERE r.rule_code = 'MES_NCR_CODE' AND p.part_index = 1);

-- part 2: NOWDATE 日期 yyyyMMdd
INSERT INTO `sys_auto_code_part` (`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`part_id`)+1 FROM `sys_auto_code_part`), '000000', (SELECT `rule_id` FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_NCR_CODE'), 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_part` p JOIN `sys_auto_code_rule` r ON p.rule_id = r.rule_id WHERE r.rule_code = 'MES_NCR_CODE' AND p.part_index = 2);

-- part 3: SERIALNO 流水号 3位 日循环
INSERT INTO `sys_auto_code_part` (`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`part_id`)+1 FROM `sys_auto_code_part`), '000000', (SELECT `rule_id` FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_NCR_CODE'), 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_part` p JOIN `sys_auto_code_rule` r ON p.rule_id = r.rule_id WHERE r.rule_code = 'MES_NCR_CODE' AND p.part_index = 3);

-- =============================================
-- 十五、自动编码规则: MES_REWORK_CODE (RW + yyyyMMdd + 3位日循环)
-- =============================================
INSERT INTO `sys_auto_code_rule` (`rule_id`, `tenant_id`, `rule_code`, `rule_name`, `rule_desc`, `max_length`, `is_padded`, `padded_char`, `padded_method`, `enable_flag`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`rule_id`)+1 FROM `sys_auto_code_rule`), '000000', 'MES_REWORK_CODE', 'MES返工工单编码', 'RW+日期+3位日循环流水号', 13, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_REWORK_CODE');

INSERT INTO `sys_auto_code_part` (`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`part_id`)+1 FROM `sys_auto_code_part`), '000000', (SELECT `rule_id` FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_REWORK_CODE'), 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'RW', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_part` p JOIN `sys_auto_code_rule` r ON p.rule_id = r.rule_id WHERE r.rule_code = 'MES_REWORK_CODE' AND p.part_index = 1);

INSERT INTO `sys_auto_code_part` (`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`part_id`)+1 FROM `sys_auto_code_part`), '000000', (SELECT `rule_id` FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_REWORK_CODE'), 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_part` p JOIN `sys_auto_code_rule` r ON p.rule_id = r.rule_id WHERE r.rule_code = 'MES_REWORK_CODE' AND p.part_index = 2);

INSERT INTO `sys_auto_code_part` (`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT (SELECT MAX(`part_id`)+1 FROM `sys_auto_code_part`), '000000', (SELECT `rule_id` FROM `sys_auto_code_rule` WHERE `rule_code` = 'MES_REWORK_CODE'), 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'
WHERE NOT EXISTS (SELECT 1 FROM `sys_auto_code_part` p JOIN `sys_auto_code_rule` r ON p.rule_id = r.rule_id WHERE r.rule_code = 'MES_REWORK_CODE' AND p.part_index = 3);

-- =============================================
-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, parent_id, menu_type, perms FROM sys_menu WHERE menu_id BETWEEN 4241 AND 4260 ORDER BY menu_id;
-- 预期: 1 M + 4 C + 15 F = 20 条
-- SELECT dict_type, dict_label, dict_value FROM sys_dict_data WHERE dict_type IN ('mes_ncr_status','mes_ncr_source_type','mes_ncr_disposition','mes_defect_cause','mes_rework_status','mes_8d_status','mes_wo_source_type') ORDER BY dict_type, dict_sort;
-- 预期: 5+5+4+6+3+3+3 = 29 条
-- SELECT rule_code, rule_name FROM sys_auto_code_rule WHERE rule_code IN ('MES_NCR_CODE','MES_REWORK_CODE');
-- 预期: 2 条
-- =============================================
