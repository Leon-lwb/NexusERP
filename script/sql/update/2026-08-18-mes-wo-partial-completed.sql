-- =============================================================================
-- MES 工单状态新增"部分完成"(PARTIAL_COMPLETED)
-- 场景: 工单计划10, 仅流转卡3完工时, 工单不应直接置"已完成", 须为"部分完成";
--       待全部流转卡完工才置"已完成"并通知ERP。
-- 字典: prd_wo_status 新增 PARTIAL_COMPLETED(dict_sort=4), 原 COMPLETED/CLOSED/CANCELLED/APPROVED 顺延
-- 主键 dict_code 走雪花算法, 用 MAX+1 派生表动态分配(避免硬编码小ID冲突, 同 ruoyi-snowflake-dict-menu-id 坑)
-- =============================================================================

-- 1. 新增"部分完成"(dict_sort=4)
--    dict_code 走雪花算法(全局唯一), 用全表 MAX+1 动态分配(避免与既有雪花ID冲突, 同 ruoyi-snowflake-dict-menu-id 坑)
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT IFNULL(MAX(dict_code), 0) + 1, '000000', 4, '部分完成', 'PARTIAL_COMPLETED', 'prd_wo_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '生产工单状态-部分完成(部分流转卡已完工,仍有在制流转卡)'
FROM sys_dict_data;

-- 2. 原 COMPLETED/CLOSED/CANCELLED/APPROVED 顺延 dict_sort(保持与状态机顺序一致)
UPDATE sys_dict_data SET dict_sort = 5 WHERE dict_type = 'prd_wo_status' AND dict_value = 'COMPLETED';
UPDATE sys_dict_data SET dict_sort = 6 WHERE dict_type = 'prd_wo_status' AND dict_value = 'CLOSED';
UPDATE sys_dict_data SET dict_sort = 7 WHERE dict_type = 'prd_wo_status' AND dict_value = 'CANCELLED';
UPDATE sys_dict_data SET dict_sort = 8 WHERE dict_type = 'prd_wo_status' AND dict_value = 'APPROVED';

-- 3. 清 Redis 字典缓存(Spring Cache @Cacheable SYS_DICT, Redisson Hash 结构: <tenant>:sys_dict)
--    不清全量, 仅 HDEL prd_wo_status 字段, 触发下次查询从DB重载(同 mes-p0 字典SQL直插后Redis stale坑)
-- HDEL 000000:sys_dict prd_wo_status
