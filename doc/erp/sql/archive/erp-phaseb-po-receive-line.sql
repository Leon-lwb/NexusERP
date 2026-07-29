-- =============================================
-- NexusERP - Phase B: 收货行关联采购订单行外键
-- 目标库: ry-erp
-- 创建时间: 2026-06-27
-- 说明: wm_material_receive_line 新增 purchase_line_id 直接外键
--       消除按物料匹配的歧义风险
-- =============================================

USE `ry-erp`;

ALTER TABLE `wm_material_receive_line`
  ADD COLUMN `purchase_line_id` bigint(20) NULL DEFAULT NULL COMMENT '关联采购订单行ID(pu_purchase_order_line.line_id)' AFTER `source_doc_line_id`;

CREATE INDEX `idx_wm_receive_line_po_line` ON `wm_material_receive_line`(`purchase_line_id`);
