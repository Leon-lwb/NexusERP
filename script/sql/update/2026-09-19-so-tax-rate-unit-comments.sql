-- 销售模块税率/折扣率「百分数」单位注释统一（销售模块优化决策 Q3）
-- 背景（2026-09-19 审计 + 用户决策「Q3 百分数」）:
--   代码侧**早已统一为百分数**（所有前后端计算均为 ×税率 ÷ 100，见下方核对结论），
--   但 DDL 列注释长期只写「税率」「折扣率」，没有单位说明；历史上也出现过
--   两种写法并存（sys_oper_log 实测有 "taxRate":"0.03" 与 "taxRate":"3"）。
--   本次统一注释口径为「百分数：13 表示 13%」，并给出存量数据核对查询。
-- 目标库: ERP 业务库 (ry-erp)。
-- 执行方式: 幂等（MODIFY COLUMN 重复执行结果一致），可直接执行。

-- ============================================================
-- 0. 代码侧口径核对结论（无需改动，仅记录，避免后续重复排查）
--    - 后端：SoSalesOrderHeaderServiceImpl.recalcAmounts、SoContractServiceImpl、
--            SoQuotationServiceImpl、SoInvoiceServiceImpl 全部为 `× 税率 ÷ 100`
--    - 前端：orderheader/form.vue、quotation/form.vue、contract/index.vue、
--            invoice/index.vue 全部为 `× 税率 / 100`
--    - 区间校验：订单（SoSalesOrderLineBo 的 @DecimalMin/@DecimalMax）、报价、合同
--            均为 0~100；发票原先缺失，本次已在 SoInvoiceServiceImpl.assertLineTaxRate 补齐
--    - 关于「按含税金额计税」的旧结论：**系误读注释所致** —— contract/index.vue 的
--            calcLineAmount 注释原文误写「行金额(含税)」，而代码把它当不含税用
--            （lineAmount = 数量×单价，taxAmount 另加）。注释已更正，公式本就一致。
-- ============================================================

-- ============================================================
-- 1. so_sales_order_line：税率/行折扣率补单位说明
-- ============================================================
ALTER TABLE `so_sales_order_line`
    MODIFY COLUMN `tax_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '税率（百分数：13 表示 13%）',
    MODIFY COLUMN `discount_percent` decimal(18, 6) NULL DEFAULT NULL COMMENT '行折扣率（百分数：5 表示让利 5%，0 表示不让利）';

-- ============================================================
-- 2. so_sales_order_header：整单折扣率补单位说明
-- ============================================================
ALTER TABLE `so_sales_order_header`
    MODIFY COLUMN `discount_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '整单折扣率（百分数：5 表示让利 5%，0 表示不让利）';

-- ============================================================
-- 3. 存量数据核对（只读，请人工判断后决定是否修正）
--    期望：第 1 段（疑似小数写法）应无输出；若**有输出**，说明这些行把 13% 写成了 0.13，
--          会导致税额少算 100 倍，需要按业务确认后手工 ×100 修正。
--    注意：0.5 这类值在百分数语义下**也可能是合法的**（0.5% 的优惠税率），
--          因此本脚本**不做自动迁移**，只提供核对清单。
-- ============================================================
-- SELECT 'so_sales_order_line.tax_rate' AS col, line_id AS id, tax_rate AS val FROM so_sales_order_line WHERE tax_rate > 0 AND tax_rate < 1
-- UNION ALL SELECT 'so_sales_order_line.discount_percent', line_id, discount_percent FROM so_sales_order_line WHERE discount_percent > 0 AND discount_percent < 1
-- UNION ALL SELECT 'so_quotation_line.tax_rate', line_id, tax_rate FROM so_quotation_line WHERE tax_rate > 0 AND tax_rate < 1
-- UNION ALL SELECT 'so_return_line.tax_rate', line_id, tax_rate FROM so_return_line WHERE tax_rate > 0 AND tax_rate < 1
-- UNION ALL SELECT 'so_invoice_line.tax_rate', line_id, tax_rate FROM so_invoice_line WHERE tax_rate > 0 AND tax_rate < 1
-- UNION ALL SELECT 'so_contract_line.tax_rate', line_id, tax_rate FROM so_contract_line WHERE tax_rate > 0 AND tax_rate < 1;

-- ============================================================
-- 4. 存量数据核对（只读）：超出 0~100 的非法值（应无输出）
-- ============================================================
-- SELECT 'so_sales_order_line.tax_rate' AS col, line_id AS id, tax_rate AS val FROM so_sales_order_line WHERE tax_rate > 100
-- UNION ALL SELECT 'so_invoice_line.tax_rate', line_id, tax_rate FROM so_invoice_line WHERE tax_rate > 100
-- UNION ALL SELECT 'so_contract_line.tax_rate', line_id, tax_rate FROM so_contract_line WHERE tax_rate > 100
-- UNION ALL SELECT 'so_quotation_line.tax_rate', line_id, tax_rate FROM so_quotation_line WHERE tax_rate > 100
-- UNION ALL SELECT 'so_return_line.tax_rate', line_id, tax_rate FROM so_return_line WHERE tax_rate > 100;

-- ============================================================
-- 5. 复核：确认注释已更新
-- ============================================================
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME IN ('tax_rate','discount_rate','discount_percent')
--    AND TABLE_NAME LIKE 'so\_%' ORDER BY TABLE_NAME, COLUMN_NAME;
