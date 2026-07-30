-- ============================================================
-- F1 AR/AP:pay_order 加业务关联列(pay 回调 -> ERP 核销)
-- 库: ry-vue (pay 模块表)
-- 日期: 2026-07-30
-- ============================================================
SET NAMES utf8mb4;

-- biz_type/biz_id: 关联业务单据(销售订单等);client_id: AR 收款核销的客户
ALTER TABLE `pay_order` ADD COLUMN `biz_type` varchar(32) DEFAULT NULL COMMENT '业务类型 SO_ORDER/AR_RECEIPT' AFTER `order_status`;
ALTER TABLE `pay_order` ADD COLUMN `biz_id` bigint(20) DEFAULT NULL COMMENT '业务单据ID(销售订单ID等)' AFTER `biz_type`;
ALTER TABLE `pay_order` ADD COLUMN `client_id` bigint(20) DEFAULT NULL COMMENT '客户ID(AR 收款核销用)' AFTER `biz_id`;

-- 校验
-- DESCRIBE pay_order;
