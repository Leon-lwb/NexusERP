-- =====================================================================
-- P1 销售开票闭环 端到端测试造数
-- 库: ry-erp   租户: 000000(默认, admin 可见)
-- 造一个全新测试客户(开票四要素齐全)+ 已确认可履行的销售订单 + 2 行(delivered_qty=ordered_qty)
-- 幂等: 先按测试 ID 段清理再插入,可重复执行
-- =====================================================================
USE `ry-erp`;
SET NAMES utf8mb4;

-- 清理旧测试数据(测试 ID 段 8800xxx, 不与雪花 ID 冲突;含上轮测试产生的发票)
DELETE FROM `so_invoice_line`   WHERE invoice_id IN (SELECT invoice_id FROM so_invoice WHERE so_id = 8800100);
DELETE FROM `so_invoice`        WHERE so_id = 8800100;
DELETE FROM `so_sales_order_line`   WHERE so_id = 8800100;
DELETE FROM `so_sales_order_header` WHERE so_id = 8800100;
DELETE FROM `bd_client`             WHERE client_id = 8800001;

-- 1. 测试客户(开票四要素齐全, default_invoice_type=VAT_SPECIAL)
INSERT INTO `bd_client` (client_id, tenant_id, client_code, client_name, client_type,
  tax_id, invoice_title, invoice_bank_name, invoice_bank_account, invoice_address_phone, default_invoice_type,
  create_by, create_time)
VALUES (8800001, '000000', 'TEST-FP-001', 'P1测试客户有限公司', 'ENTERPRISE',
  '91110000TEST00001X', 'P1测试客户有限公司', '中国测试银行科技支行', '6228480000000001234',
  '北京市海淀区测试路1号 010-88888888', 'VAT_SPECIAL', 1, NOW());

-- 2. 销售订单头(已确认 CONFIRMED, 可履行; 价税合计 1695.00 = 不含税1500 + 税195)
INSERT INTO `so_sales_order_header` (so_id, tenant_id, so_no, client_id, client_name, order_date,
  currency, exchange_rate, total_amount, tax_amount, order_status, credit_status, create_by, create_time)
VALUES (8800100, '000000', 'SO-FP-TEST-001', 8800001, 'P1测试客户有限公司', NOW(),
  'CNY', 1.000000, 1695.00, 195.00, 'CONFIRMED', 'PASSED', 1, NOW());

-- 3. 销售订单行(2 行, delivered_qty = ordered_qty, 满足 fulfill(force=false) 门禁)
--   行1: 100个 × 单价10 = 1000(不含税), 税率13% -> 税130, 价税1130
--   行2: 10套 × 单价50 = 500(不含税),  税率13% -> 税65,  价税565
INSERT INTO `so_sales_order_line` (line_id, tenant_id, so_id, line_no, material_id, material_code, material_name,
  ordered_qty, delivered_qty, invoiced_qty, unit, unit_price, line_amount, tax_rate, tax_amount, line_status, create_by, create_time)
VALUES
  (8800201, '000000', 8800100, '0010', 8800001, 'TEST-MAT-A', '测试物料A',
   100, 100, 0, '个', 10.000000, 1000.00, 13.000000, 130.00, 'SCHEDULED', 1, NOW()),
  (8800202, '000000', 8800100, '0020', 8800002, 'TEST-MAT-B', '测试物料B',
   10, 10, 0, '套', 50.000000, 500.00, 13.000000, 65.00, 'SCHEDULED', 1, NOW());

-- 校验
SELECT so_id, so_no, client_id, client_name, order_status, total_amount, tax_amount FROM so_sales_order_header WHERE so_id = 8800100;
SELECT line_id, line_no, material_code, material_name, ordered_qty, delivered_qty, invoiced_qty, unit_price, line_amount, tax_rate, tax_amount FROM so_sales_order_line WHERE so_id = 8800100;
SELECT client_id, client_name, tax_id, invoice_title, invoice_bank_name, invoice_bank_account, default_invoice_type FROM bd_client WHERE client_id = 8800001;
