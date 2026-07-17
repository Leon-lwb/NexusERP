-- ============================================================
-- so_sales_order_line.line_status 字典一致性修复 (幂等)
-- 日期: 2026-07-17
-- 关联: so_sales_order_line 状态字段赋值优化
--
-- 背景:
--   so_order_line_status 字典合法值仅 5 个: PENDING / SCHEDULED /
--   PARTIALLY_FULFILLED / FULFILLED / CANCELLED。
--   1) 字典 sort 重复: PARTIALLY_FULFILLED 与 FULFILLED 均为 4,
--      前端下拉排序错乱, 应为 3 / 4。
--   2) 存量脏数据: so_sales_order_line.line_status 存在历史写入的
--      IN_PRODUCTION / PRODUCED 两个非字典值(各 1 行, 其 production_qty
--      已达 ordered_qty), 前端字典翻译会显示原值, 统一映射为 SCHEDULED
--      (已投产完工, 待发货)。
--
-- 说明:
--   - 本脚本跨两个库, 按节切换库执行:
--       第 1 节 -> ry-vue 系统库 (sys_dict_data)
--       第 2 节 -> ry-erp 业务库 (so_sales_order_line)
--   - 全部为 UPDATE, 天然幂等, 可安全重复执行。
--   - 主脚本 ry-vue.sql / ry-erp.sql 已同步这些变更, 本脚本供已部署环境增量执行。
-- ============================================================

-- ========== 第 1 节: ry-vue 系统库 ==========
-- 修复字典 so_order_line_status 的 PARTIALLY_FULFILLED 排序: 4 -> 3
UPDATE `sys_dict_data`
SET `dict_sort` = 3
WHERE `tenant_id` = '000000'
  AND `dict_type` = 'so_order_line_status'
  AND `dict_value` = 'PARTIALLY_FULFILLED';

-- ========== 第 2 节: ry-erp 业务库 ==========
-- 清理存量脏数据: IN_PRODUCTION / PRODUCED -> SCHEDULED
UPDATE `so_sales_order_line`
SET `line_status` = 'SCHEDULED'
WHERE `line_status` IN ('IN_PRODUCTION', 'PRODUCED');
