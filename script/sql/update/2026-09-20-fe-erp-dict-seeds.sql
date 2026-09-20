-- =============================================================================
-- 用途：ERP 前端修复批次（doc/audit/2026-09-20-fix-fe-erp.md）所需字典种子
--   FE-ERP-04 盘点方式 countMode（WmStockTakingPlanBo.countMode，PDA 端已按 OPEN/BLIND 分支）
--   FE-ERP-02 生产报工状态 / 报工途径（PrdWorkReportVo.reportStatus / reportChannel）
-- 执行前提：先执行 script/sql/ry-vue.sql（sys_dict_type / sys_dict_data 已存在）
-- 影响库：ry-vue（系统库）
-- 幂等性：全部 INSERT IGNORE（按 dict_type / (dict_type,dict_value) 唯一键去重），可重复执行
-- 是否需清 Redis 缓存：是 —— 字典有缓存，执行后清理 `sys_dict:sys_dict_type:*` /
--   `sys_dict:sys_dict_data:*` 或重启应用（后台「字典管理」页面亦可点一次刷新）
-- 备注：主脚本合并清单见 doc/audit/2026-09-20-fix-fe-erp.md「主脚本合并清单」一节
-- =============================================================================

-- 1) 盘点方式：明盘 OPEN / 盲盘 BLIND（与 PdaStocktakingServiceImpl 的值域校验一致）
INSERT IGNORE INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('盘点方式', 'erp_stocktaking_count_mode', '0', 1, NOW(), '盘点计划明盘/盲盘（FE-ERP-04）');

INSERT IGNORE INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (1, '明盘', 'OPEN', 'erp_stocktaking_count_mode', '', 'primary', 'Y', '0', 1, NOW(), '账面数量对盘点人可见'),
       (2, '盲盘', 'BLIND', 'erp_stocktaking_count_mode', '', 'warning', 'N', '0', 1, NOW(), '账面数量对盘点人隐藏');

-- 2) 生产报工状态（PrdWorkReport.reportStatus；后端无枚举类，按报工单据流转定义）
INSERT IGNORE INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('生产报工状态', 'prd_work_report_status', '0', 1, NOW(), '工单报工单据状态（FE-ERP-02）');

INSERT IGNORE INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (1, '草稿', 'DRAFT', 'prd_work_report_status', '', 'info', 'Y', '0', 1, NOW(), ''),
       (2, '已提交', 'SUBMITTED', 'prd_work_report_status', '', 'warning', 'N', '0', 1, NOW(), ''),
       (3, '已审核', 'APPROVED', 'prd_work_report_status', '', 'success', 'N', '0', 1, NOW(), '审核通过时后端会回写工单累计产量'),
       (4, '已驳回', 'REJECTED', 'prd_work_report_status', '', 'danger', 'N', '0', 1, NOW(), '');

-- 3) 报工途径（PrdWorkReport.reportChannel）
INSERT IGNORE INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('报工途径', 'prd_work_report_channel', '0', 1, NOW(), '报工数据来源渠道（FE-ERP-02）');

INSERT IGNORE INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (1, 'PC 端', 'PC', 'prd_work_report_channel', '', 'primary', 'Y', '0', 1, NOW(), ''),
       (2, 'PDA 手持端', 'PDA', 'prd_work_report_channel', '', 'success', 'N', '0', 1, NOW(), ''),
       (3, 'MES 制造执行', 'MES', 'prd_work_report_channel', '', 'warning', 'N', '0', 1, NOW(), '');
