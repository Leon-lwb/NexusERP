-- ============================================================
-- 为 so_credit_usage 增加 used_time 列（信用三态化）
-- ============================================================
-- 用途：销售订单"履行(fulfill)"时信用从 FROZEN → USED（已交付形成应收，
--       仍占用信用额度），close/cancel 时统一 RELEASED。
--       used_time 记录转为已用的时间。
-- 适用数据库：ry-erp
-- 特性：幂等可重复执行（information_schema 判存后再 ADD）
-- 对应文档：doc/erp/plans/2026-07-07-so-fulfillment-linkage-plan.md
-- 创建日期：2026-07-07
-- ============================================================

SET @col_cnt := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'ry-erp'
    AND table_name = 'so_credit_usage'
    AND column_name = 'used_time'
);
SET @ddl := IF(@col_cnt = 0,
  'ALTER TABLE `ry-erp`.`so_credit_usage` ADD COLUMN `used_time` datetime DEFAULT NULL COMMENT ''转为已用(已交付)时间'' AFTER `freeze_time`',
  'SELECT ''so_credit_usage.used_time already exists, skipped.'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 验证（执行后人工核对）：
--   SHOW COLUMNS FROM `ry-erp`.`so_credit_usage` LIKE 'used_time';
-- ============================================================
