-- =============================================================================
-- 用途：ERP 前端修复批次（doc/audit/2026-09-20-fix-fe-erp.md）所需字典种子
--   FE-ERP-04 盘点方式 countMode（WmStockTakingPlanBo.countMode，PDA 端已按 OPEN/BLIND 分支）
--   FE-ERP-02 生产报工状态 / 报工途径（PrdWorkReportVo.reportStatus / reportChannel）
-- 执行前提：ry-vue 库存在 sys_dict_type / sys_dict_data
-- 影响库：ry-vue（仅 DML，无 DDL）
-- 幂等性：全部 INSERT IGNORE（按主键去重），可重复执行
-- 是否需清 Redis 缓存：是 —— 字典有缓存，执行后清 `sys_dict:*` 或重启应用
--                （后台「字典管理」保存任意字典亦可触发刷新）
-- 备注：已按 sys_dict_type / sys_dict_data 的**真实列定义**编写 ——
--       两表均**没有** status 列（早期版本脚本误加了 status，已在 2026-09-20 修正）。
-- =============================================================================

-- 1) 盘点方式：明盘 OPEN / 盲盘 BLIND（与 PdaStocktakingServiceImpl 的值域校验一致）
INSERT IGNORE INTO `ry-vue`.`sys_dict_type`
  (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600240, '000000', '盘点方式', 'erp_stocktaking_count_mode', 103, 1, NOW(), '盘点计划明盘/盲盘（FE-ERP-04）');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data`
  (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600243, '000000', 1, '明盘', 'OPEN', 'erp_stocktaking_count_mode', '', 'primary', 'Y', 103, 1, NOW(), '账面数量对盘点人可见'),
       (600244, '000000', 2, '盲盘', 'BLIND', 'erp_stocktaking_count_mode', '', 'warning', 'N', 103, 1, NOW(), '账面数量对盘点人隐藏');

-- 2) 生产报工状态（PrdWorkReport.reportStatus）
INSERT IGNORE INTO `ry-vue`.`sys_dict_type`
  (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600241, '000000', '生产报工状态', 'prd_work_report_status', 103, 1, NOW(), '工单报工单据状态（FE-ERP-02）');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data`
  (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600245, '000000', 1, '草稿', 'DRAFT', 'prd_work_report_status', '', 'info', 'Y', 103, 1, NOW(), ''),
       (600246, '000000', 2, '已提交', 'SUBMITTED', 'prd_work_report_status', '', 'warning', 'N', 103, 1, NOW(), ''),
       (600247, '000000', 3, '已审核', 'APPROVED', 'prd_work_report_status', '', 'success', 'N', 103, 1, NOW(), '审核通过时后端会回写工单累计产量'),
       (600248, '000000', 4, '已驳回', 'REJECTED', 'prd_work_report_status', '', 'danger', 'N', 103, 1, NOW(), '');

-- 3) 报工途径（PrdWorkReport.reportChannel）
INSERT IGNORE INTO `ry-vue`.`sys_dict_type`
  (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600242, '000000', '报工途径', 'prd_work_report_channel', 103, 1, NOW(), '报工数据来源渠道（FE-ERP-02）');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data`
  (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
VALUES (600249, '000000', 1, 'PC 端', 'PC', 'prd_work_report_channel', '', 'primary', 'Y', 103, 1, NOW(), ''),
       (600250, '000000', 2, 'PDA 手持端', 'PDA', 'prd_work_report_channel', '', 'success', 'N', 103, 1, NOW(), ''),
       (600251, '000000', 3, 'MES 制造执行', 'MES', 'prd_work_report_channel', '', 'warning', 'N', 103, 1, NOW(), '');

-- 校验（人工执行）：
-- SELECT dict_type, dict_name FROM `ry-vue`.`sys_dict_type` WHERE dict_type IN ('erp_stocktaking_count_mode','prd_work_report_status','prd_work_report_channel');
-- SELECT dict_type, dict_value, dict_label FROM `ry-vue`.`sys_dict_data` WHERE dict_type IN ('erp_stocktaking_count_mode','prd_work_report_status','prd_work_report_channel') ORDER BY dict_type, dict_sort;