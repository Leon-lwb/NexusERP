-- 销售模块（so）DDL 收敛：唯一约束补租户维度、缺失索引、注释修正
-- 背景（2026-09-19 销售模块审计批次 4）:
--   1) 多租户唯一键不一致：同模块的 so_contract/so_invoice/so_price_list 唯一键都带 tenant_id，
--      而 so_sales_order_header.uk_so_no 与 so_customer_order.uk_co_no 只按单号唯一
--      —— 两个租户各自生成的同号单据会互相阻塞（自动编码规则是租户内的，重号完全可能）。
--   2) so_quotation.quotation_no / so_return.return_no 是 NOT NULL 但没有唯一约束，
--      而服务端仅在为空时生成单号 —— 重复单号可以入库。
--   3) so_price_list_line 缺少 (price_list_id, material_id, min_qty) 唯一约束，
--      定价引擎按该维度取行，重复行会让价格命中结果不确定。
--   4) 多处高频查询列无索引（详见第 5 节）。
--   5) so_invoice 有 14 处列注释是 GBK 被当作 UTF-8 读入产生的乱码，无法阅读。
-- 目标库: ERP 业务库 (ry-erp)。
-- 执行方式: 本脚本为**一次性**脚本（MySQL 的 ALTER 不支持 IF NOT EXISTS 形式的加索引），
--           重复执行会在 DROP INDEX / ADD INDEX 处报错。执行前请先跑各节的「前置检查」。
-- 建议: 大表加索引请在业务低峰执行。

-- ============================================================
-- 1. so_sales_order_header：单号唯一键补 tenant_id（放宽，允许跨租户重号）
--    前置检查（应无输出）: SELECT so_no FROM so_sales_order_header GROUP BY so_no, tenant_id HAVING COUNT(*) > 1;
-- ============================================================
ALTER TABLE `so_sales_order_header`
    DROP INDEX `uk_so_no`,
    ADD UNIQUE INDEX `uk_so_no`(`so_no`, `tenant_id`) USING BTREE;

-- ============================================================
-- 2. so_customer_order：单号唯一键补 tenant_id
--    前置检查（应无输出）: SELECT co_no FROM so_customer_order GROUP BY co_no, tenant_id HAVING COUNT(*) > 1;
-- ============================================================
ALTER TABLE `so_customer_order`
    DROP INDEX `uk_co_no`,
    ADD UNIQUE INDEX `uk_co_no`(`co_no`, `tenant_id`) USING BTREE;

-- ============================================================
-- 3. so_quotation / so_return：补单号唯一约束
--    前置检查（应无输出，有输出需先人工去重，否则 ALTER 失败）:
--      SELECT quotation_no, tenant_id, COUNT(*) FROM so_quotation GROUP BY quotation_no, tenant_id HAVING COUNT(*) > 1;
--      SELECT return_no,    tenant_id, COUNT(*) FROM so_return    GROUP BY return_no,    tenant_id HAVING COUNT(*) > 1;
-- ============================================================
ALTER TABLE `so_quotation`
    ADD UNIQUE INDEX `uk_quotation_no`(`quotation_no`, `tenant_id`) USING BTREE;

ALTER TABLE `so_return`
    ADD UNIQUE INDEX `uk_return_no`(`return_no`, `tenant_id`) USING BTREE;

-- ============================================================
-- 4. so_price_list_line：min_qty 去 NULL + 补阶梯唯一约束
--    说明: min_qty 原为可空（DEFAULT 0），而 MySQL 唯一索引允许多个 NULL
--          —— 不先去 NULL，约束对「min_qty 为 NULL 的重复行」不生效。
--    前置检查（应无输出）:
--      SELECT price_list_id, material_id, IFNULL(min_qty,0), COUNT(*) FROM so_price_list_line
--        GROUP BY price_list_id, material_id, IFNULL(min_qty,0) HAVING COUNT(*) > 1;
-- ============================================================
UPDATE `so_price_list_line` SET `min_qty` = 0 WHERE `min_qty` IS NULL;

ALTER TABLE `so_price_list_line`
    MODIFY COLUMN `min_qty` decimal(14, 4) NOT NULL DEFAULT 0.0000 COMMENT '最小数量（含）',
    MODIFY COLUMN `max_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '最大数量（含）；NULL 表示不设上限',
    ADD UNIQUE INDEX `uk_pl_line_tier`(`price_list_id`, `material_id`, `min_qty`) USING BTREE;

-- ============================================================
-- 5. 补齐高频查询索引
-- ============================================================
-- 5.1 退货单：按原订单号 / 客户查询
ALTER TABLE `so_return`
    ADD INDEX `idx_return_so_no`(`so_no`) USING BTREE,
    ADD INDEX `idx_return_client`(`client_id`) USING BTREE;

-- 5.2 客户订单：优先级 / 客户 PO 号 / 来源渠道
ALTER TABLE `so_customer_order`
    ADD INDEX `idx_co_priority`(`priority`) USING BTREE,
    ADD INDEX `idx_co_customer_po_no`(`customer_po_no`) USING BTREE,
    ADD INDEX `idx_co_source_channel`(`source_channel`) USING BTREE;

-- 5.3 销售合同：合同类型
ALTER TABLE `so_contract`
    ADD INDEX `idx_contract_type`(`contract_type`) USING BTREE;

-- 5.4 价格表头：客户 / 状态 / 类型（取价引擎按 类型+状态+客户+有效期 过滤）
ALTER TABLE `so_price_list`
    ADD INDEX `idx_pl_client`(`client_id`) USING BTREE,
    ADD INDEX `idx_pl_status`(`status`) USING BTREE,
    ADD INDEX `idx_pl_list_type`(`list_type`) USING BTREE;

-- 5.5 信用占用：客户+状态 / 订单+状态（getUsedAmount 与幂等判重都按这两个组合过滤）
ALTER TABLE `so_credit_usage`
    ADD INDEX `idx_cu_client_status`(`client_id`, `status`) USING BTREE,
    ADD INDEX `idx_cu_so_status`(`so_id`, `status`) USING BTREE;

-- 5.6 发票行 / 退货行：回写订单行的关联列（此前无索引，回写与红冲按该列定位）
ALTER TABLE `so_invoice_line`
    ADD INDEX `idx_so_line_id`(`so_line_id`) USING BTREE,
    ADD INDEX `idx_contract_line_id`(`contract_line_id`) USING BTREE;

ALTER TABLE `so_return_line`
    ADD INDEX `idx_return_line_so_line`(`so_line_id`) USING BTREE;

-- ============================================================
-- 6. 注释修正：税率/折扣率单位（均为百分数），以及 so_invoice 的乱码注释
-- ============================================================
ALTER TABLE `so_quotation`
    MODIFY COLUMN `discount_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '折扣率（百分数：5 表示让利 5%）';

ALTER TABLE `so_quotation_line`
    MODIFY COLUMN `tax_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '税率（百分数：13 表示 13%）';

ALTER TABLE `so_return_line`
    MODIFY COLUMN `tax_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '税率（百分数：13 表示 13%）';

ALTER TABLE `so_contract_line`
    MODIFY COLUMN `tax_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '税率（百分数：13 表示 13%）';

ALTER TABLE `so_invoice_line`
    MODIFY COLUMN `tax_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '税率（百分数：13 表示 13%）';

-- so_invoice 的乱码注释重建（14 处）
ALTER TABLE `so_invoice`
    MODIFY COLUMN `invoice_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '发票代码（数电票时代可与号码合并）',
    MODIFY COLUMN `buyer_tax_id` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '购买方纳税人识别号',
    MODIFY COLUMN `buyer_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '购买方发票抬头',
    MODIFY COLUMN `buyer_bank_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '购买方开户行',
    MODIFY COLUMN `buyer_bank_account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '购买方银行账号',
    MODIFY COLUMN `buyer_address_phone` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '购买方地址、电话',
    MODIFY COLUMN `original_invoice_id` bigint(20) NULL DEFAULT NULL COMMENT '红冲原发票ID（NULL=蓝字正数）',
    MODIFY COLUMN `red_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '是否红字发票 0-否 1-是',
    MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '发票状态 DRAFT-草稿 APPROVING-审批中 ISSUED-已开 REJECTED-已驳回 VERIFIED-已核销 CANCELLED-已作废',
    MODIFY COLUMN `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID',
    MODIFY COLUMN `issue_channel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT '开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台',
    MODIFY COLUMN `e_invoice_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '数电票号码（平台返回）',
    MODIFY COLUMN `pdf_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票PDF的OSS对象ID（私有桶存ossId）',
    MODIFY COLUMN `ofd_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票OFD的OSS对象ID',
    MODIFY COLUMN `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号';
