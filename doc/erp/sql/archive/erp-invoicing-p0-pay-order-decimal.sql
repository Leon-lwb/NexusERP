-- =====================================================================
-- pay_order 金额类型迁移:varchar(分) -> decimal(18,2)(元)
-- 目标库:ry-vue (master)
--
-- 背景:pay_order.total_amount/actual_amount 原为 varchar(255) 存"分",
--   P3 核销需与 fin_receipt.amount / so_invoice.total_amount (decimal 元)
--   做数值匹配,类型+单位均不一致。本脚本统一为 decimal(18,2) 元。
--
-- 三 SDK 单位:支付宝要元(原代码 /100)、微信要分整数(原直接 parseInt)、
--   收钱吧要分(原直接传)。迁移后 DB 存元,SDK 边界用 MoneyUtil 换算。
--
-- ⚠️ 一次性迁移,不可重复执行(重复执行会把元再 /100)!
--   执行前存量 22 条均为数字分值(已校验,无非数字),MODIFY 安全。
-- =====================================================================
USE `ry-vue`;

-- 1. 列类型 varchar(分) -> decimal(18,2)
--    (此步后值仍为分,如 1100.00;下一步换算为元)
ALTER TABLE `pay_order`
  MODIFY COLUMN `total_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT '订单总金额（单位：元）',
  MODIFY COLUMN `actual_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT '实际支付金额（单位：元）';

-- 2. 分 -> 元(一次性换算,四舍五入保留2位)
UPDATE `pay_order` SET `total_amount` = ROUND(`total_amount` / 100, 2) WHERE `total_amount` IS NOT NULL;
UPDATE `pay_order` SET `actual_amount` = ROUND(`actual_amount` / 100, 2) WHERE `actual_amount` IS NOT NULL;

-- 3. 校验(执行后人工核对:最大值应从 500000000(分) 变为 5000000.00(元))
-- SELECT MAX(total_amount), MAX(actual_amount), COUNT(*) FROM pay_order;