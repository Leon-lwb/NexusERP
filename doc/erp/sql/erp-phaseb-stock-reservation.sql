-- =============================================
-- NexusERP - Phase B: 库存预留机制
-- 目标库: ry-erp
-- 创建时间: 2026-06-27
-- 说明: wm_material_stock 新增 reserved_quantity 列
--       available_quantity = stock_quantity - reserved_quantity (计算列, 不持久化)
-- =============================================

USE `ry-erp`;

ALTER TABLE `wm_material_stock`
  ADD COLUMN `reserved_quantity` double(12,2) NOT NULL DEFAULT 0.00 COMMENT '已预留数量(销售订单/工单锁定)' AFTER `stock_quantity`;

-- 初始化: 现有库存预留数量为0
UPDATE `wm_material_stock` SET `reserved_quantity` = 0 WHERE `reserved_quantity` IS NULL;
