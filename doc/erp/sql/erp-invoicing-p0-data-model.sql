-- =============================================
-- NexusERP - 开票与应收应付优化 P0: ERP 数据模型补字段
-- 目标库: ry-erp
-- 创建时间: 2026-07-15
-- 说明: P0 基础数据模型修补,为开票/价税分离/已开票量跟踪打底
--       1. bd_client / bd_vendor 补开票四要素(纳税人识别号/抬头/开户行/银行账号/地址电话/默认发票类型)
--       2. so_sales_order_line / pu_purchase_order_line 补 invoiced_qty(防重复开票)
--       3. pu 采购订单/退货/合同 头与行补价税分离字段(不含税金额/税额/税率)
-- 关联设计: doc/erp/specs/2026-07-15-invoicing-ar-ap-redesign.md 第四节
-- 注意: 重复执行会因列已存在报错,按需 DROP COLUMN 或忽略重复列错误
-- =============================================

USE `ry-erp`;

-- ---------- 1. 客户档案 bd_client 补开票四要素 ----------
ALTER TABLE `bd_client`
  ADD COLUMN `tax_id` varchar(30) NULL DEFAULT NULL COMMENT '纳税人识别号' AFTER `credit_code`,
  ADD COLUMN `invoice_title` varchar(200) NULL DEFAULT NULL COMMENT '发票抬头(默认取客户名称)' AFTER `tax_id`,
  ADD COLUMN `invoice_bank_name` varchar(100) NULL DEFAULT NULL COMMENT '开户行' AFTER `invoice_title`,
  ADD COLUMN `invoice_bank_account` varchar(50) NULL DEFAULT NULL COMMENT '银行账号' AFTER `invoice_bank_name`,
  ADD COLUMN `invoice_address_phone` varchar(200) NULL DEFAULT NULL COMMENT '开票地址、电话' AFTER `invoice_bank_account`,
  ADD COLUMN `default_invoice_type` varchar(20) NULL DEFAULT 'VAT_NORMAL' COMMENT '默认发票类型 VAT_SPECIAL/VAT_NORMAL/OTHER' AFTER `invoice_address_phone`;

-- ---------- 2. 供应商档案 bd_vendor 补开票信息(放 bank_account 后聚簇) ----------
ALTER TABLE `bd_vendor`
  ADD COLUMN `invoice_title` varchar(200) NULL DEFAULT NULL COMMENT '发票抬头' AFTER `bank_account`,
  ADD COLUMN `invoice_address_phone` varchar(200) NULL DEFAULT NULL COMMENT '开票地址、电话' AFTER `invoice_title`,
  ADD COLUMN `invoice_bank_name` varchar(100) NULL DEFAULT NULL COMMENT '开户行名称' AFTER `invoice_address_phone`,
  ADD COLUMN `default_invoice_type` varchar(20) NULL DEFAULT 'VAT_SPECIAL' COMMENT '默认发票类型 VAT_SPECIAL/VAT_NORMAL/OTHER' AFTER `invoice_bank_name`;

-- ---------- 3. 销售订单行补已开票数量(防重复开票) ----------
ALTER TABLE `so_sales_order_line`
  ADD COLUMN `invoiced_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已开票数量' AFTER `delivered_qty`;

-- ---------- 4. 采购订单行补已开票数量 ----------
ALTER TABLE `pu_purchase_order_line`
  ADD COLUMN `invoiced_qty` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '已开票数量' AFTER `received_quantity`;

-- ---------- 5. 采购订单头补不含税总金额 ----------
ALTER TABLE `pu_purchase_order`
  ADD COLUMN `amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税总金额' AFTER `total_amount`;

-- ---------- 6. 采购订单行补价税分离(不含税单价/不含税行金额/行税额) ----------
ALTER TABLE `pu_purchase_order_line`
  ADD COLUMN `unit_price_excluding_tax` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '不含税单价' AFTER `unit_price`,
  ADD COLUMN `line_amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税行金额' AFTER `line_amount`,
  ADD COLUMN `line_tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '行税额' AFTER `line_amount_excluding_tax`;

-- ---------- 7. 采购退货单头补税额/不含税金额 ----------
ALTER TABLE `pu_purchase_return`
  ADD COLUMN `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '税额' AFTER `total_amount`,
  ADD COLUMN `amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税总金额' AFTER `tax_amount`;

-- ---------- 8. 采购退货行补税率/税额/不含税金额 ----------
ALTER TABLE `pu_purchase_return_line`
  ADD COLUMN `tax_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '税率' AFTER `unit_price`,
  ADD COLUMN `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '行税额' AFTER `amount`,
  ADD COLUMN `amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税行金额' AFTER `tax_amount`;

-- ---------- 9. 采购合同头补税额/不含税金额 ----------
ALTER TABLE `pu_contract`
  ADD COLUMN `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '税额' AFTER `total_amount`,
  ADD COLUMN `amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税总金额' AFTER `tax_amount`;

-- ---------- 10. 采购合同行补税率/税额/不含税金额 ----------
ALTER TABLE `pu_contract_line`
  ADD COLUMN `tax_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '税率' AFTER `unit_price`,
  ADD COLUMN `tax_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '行税额' AFTER `line_amount`,
  ADD COLUMN `line_amount_excluding_tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '不含税行金额' AFTER `tax_amount`;

-- 验证
-- SELECT client_name, tax_id, invoice_title, invoice_bank_name FROM `bd_client` LIMIT 5;
-- SELECT vendor_name, tax_id, invoice_title, invoice_bank_name, default_invoice_type FROM `bd_vendor` LIMIT 5;
