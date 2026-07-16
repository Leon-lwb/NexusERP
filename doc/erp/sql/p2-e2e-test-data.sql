-- =============================================
-- NexusERP - 开票与应收应付优化 P2: 采购进项发票 E2E 测试数据
-- 目标库: ry-erp
-- 创建时间: 2026-07-16
-- 说明: 造数覆盖 P2 主链路 + 退货进项转出。
--   主链路: generateFromPo -> receive -> match -> certify -> deduct -> verify -> redFlush
--   退货链路: (发票已抵扣后) execute 退货 -> redFlushForReturn 部分红冲 + 原 cert->TRANSFERRED
-- 租户: 000000(默认租户,与 dict/menu 一致;pu_purchase_order 表有 tenant_id 列,租户插件自动填,SQL 显式置 000000)
-- 幂等: 先清理后插入,可重复执行
-- 关联设计: doc/erp/specs/2026-07-15-invoicing-ar-ap-redesign.md 第六节
-- =============================================

USE `ry-erp`;
SET NAMES utf8mb4;

-- ===== 清理(可重复执行) =====
DELETE FROM `pu_invoice_line` WHERE invoice_id IN (SELECT invoice_id FROM `pu_invoice` WHERE po_id = 8800200);
DELETE FROM `pu_invoice` WHERE po_id = 8800200;
UPDATE `pu_purchase_order_line` SET invoiced_qty = 0 WHERE order_id = 8800200;
DELETE FROM `pu_purchase_return_line` WHERE return_id = 8800400;
DELETE FROM `pu_purchase_return` WHERE return_id = 8800400;
DELETE FROM `wm_material_stock` WHERE material_id IN (8803001, 8803002);
DELETE FROM `pu_purchase_order_line` WHERE order_id = 8800200;
DELETE FROM `pu_purchase_order` WHERE order_id = 8800200;
DELETE FROM `bd_vendor` WHERE vendor_id = 8800002;

-- ===== 测试供应商(含销售方开票四要素) =====
INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `tax_id`, `bank_account`,
  `invoice_title`, `invoice_address_phone`, `invoice_bank_name`, `default_invoice_type`, `status`,
  `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(8800002, '000000', 'V-P2-TEST', 'P2测试供应商有限公司', '91110000P2TEST0002X', '6228480402564890018',
  'P2测试供应商有限公司', '北京市海淀区测试路2号 010-88880002', '中国银行北京测试支行', 'VAT_SPECIAL', 'ACTIVE',
  103, 1, NOW(), '0');

-- ===== 测试采购订单(已批准,两行均已全额收货、未开票) =====
-- 价税分离: line1 100个×不含税10=1000 + 税130 = 1130(含税单价11.30)
--           line2 10套×不含税50=500  + 税65  = 565 (含税单价56.50)
-- 头: 不含税1500 + 税195 = 价税合计1695
INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`,
  `order_date`, `status`, `currency`, `exchange_rate`, `total_amount`, `amount_excluding_tax`, `tax_amount`,
  `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(8800200, '000000', 'PO-FP-TEST-002', 8800002, 'V-P2-TEST', 'P2测试供应商有限公司',
  NOW(), 'APPROVED', 'CNY', 1.000000, 1695.00, 1500.00, 195.00, 103, 1, NOW(), '0');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`,
  `material_spec`, `unit`, `order_quantity`, `received_quantity`, `invoiced_qty`, `receive_status`,
  `unit_price`, `unit_price_excluding_tax`, `tax_rate`, `line_amount`, `line_amount_excluding_tax`, `line_tax_amount`,
  `sort_order`, `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(8800301, '000000', 8800200, 8803001, 'P2-MAT-001', 'P2测试物料A', '10kg/袋', '个', 100.0000, 100.0000, 0.0000, 'FULL_RECEIVED',
  11.3000, 10.0000, 0.13, 1130.00, 1000.00, 130.00, 1, 103, 1, NOW(), '0'),
(8800302, '000000', 8800200, 8803002, 'P2-MAT-002', 'P2测试物料B', '5kg/箱', '套', 10.0000, 10.0000, 0.0000, 'FULL_RECEIVED',
  56.5000, 50.0000, 0.13, 565.00, 500.00, 65.00, 2, 103, 1, NOW(), '0');

-- ===== 库存(退货测试用: postReturnOut 按 material_id + frozen_flag=0 查) =====
INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`,
  `warehouse_id`, `stock_quantity`, `reserved_quantity`, `frozen_flag`, `version`, `create_dept`, `create_by`, `del_flag`) VALUES
(8809001, '000000', 8803001, 'P2-MAT-001', 'P2测试物料A', 1, 100.0000, 0.00, 0, 0, 103, 1, '0');

-- ===== 测试采购退货单(已确认,执行时触发进项转出;退 10 个物料A,对应 PO 行 8800301) =====
-- 退货 10个: 不含税100 + 税13 = 价税113
INSERT INTO `pu_purchase_return` (`return_id`, `tenant_id`, `return_no`, `order_id`, `order_no`, `vendor_id`, `vendor_code`,
  `vendor_name`, `return_date`, `return_reason`, `total_amount`, `tax_amount`, `amount_excluding_tax`, `status`,
  `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(8800400, '000000', 'PR-FP-TEST-002', 8800200, 'PO-FP-TEST-002', 8800002, 'V-P2-TEST', 'P2测试供应商有限公司',
  NOW(), 'P2退货进项转出测试', 113.00, 13.00, 100.00, 'CONFIRMED', 103, 1, NOW(), '0');

INSERT INTO `pu_purchase_return_line` (`line_id`, `tenant_id`, `return_id`, `order_line_id`, `material_id`, `material_code`,
  `material_name`, `material_spec`, `unit`, `return_quantity`, `unit_price`, `tax_rate`, `amount`, `tax_amount`,
  `amount_excluding_tax`, `sort_order`, `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(8800501, '000000', 8800400, 8800301, 8803001, 'P2-MAT-001', 'P2测试物料A', '10kg/袋', '个', 10.0000, 11.3000, 0.13,
  113.00, 13.00, 100.00, 1, 103, 1, NOW(), '0');

-- ===== 验证 =====
-- SELECT order_id, order_no, status, total_amount FROM `pu_purchase_order` WHERE order_id = 8800200;
-- SELECT line_id, material_code, received_quantity, invoiced_qty, unit_price_excluding_tax, tax_rate FROM `pu_purchase_order_line` WHERE order_id = 8800200;
-- SELECT vendor_id, vendor_name, tax_id, invoice_title, invoice_bank_name, bank_account FROM `bd_vendor` WHERE vendor_id = 8800002;
-- SELECT return_id, return_no, status FROM `pu_purchase_return` WHERE return_id = 8800400;
