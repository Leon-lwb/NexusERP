-- 销售价目表折扣率语义修正（销售模块优化决策 D2）
-- 背景:
--   原实现把 so_price_list_line.discount_rate 当「实付百分比」用：成交价 = 单价 × 折扣率 / 100，
--   而 DDL 默认值是 100.00 —— 两者叠加意味着**默认配置下成交价恒为 0 元**；
--   折扣率 5 会算出原价的 5%（等于让利 95%），方向与业务意图完全相反。
--   订单侧（orderheader/form.vue 的 calcLine/calcHeader）一直是「折扣百分比」语义：
--   让利 5% 即 discountRate=5、实付 = 金额 × (1 − 5/100)。
--   本次把价目表侧对齐到同一口径（决策 D2）。
-- 目标库: ERP 业务库 (ry-erp)。本脚本幂等，可重复执行。
-- 注意: 执行第 2 步 UPDATE 前，请先确认不存在「真实让利 100%」的行（那会被一并改成 0）：
--         SELECT * FROM so_price_list_line WHERE discount_rate = 100;
--       若确有让利 100% 的行，请先手工把它们改成其它值或单独处理，再执行 UPDATE。

-- 1. 默认值 100.00 -> 0.00（新语义下 100 表示免费），并补上单位说明
ALTER TABLE `so_price_list_line`
    MODIFY COLUMN `discount_rate` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '折扣率（百分数：5 表示让利 5%，0 表示不让利）';

-- 2. 存量数据迁移：把「未填折扣率」留下的默认值 100 归零
UPDATE `so_price_list_line` SET `discount_rate` = 0 WHERE `discount_rate` = 100;

-- 3. 关联表的单位说明同步补齐（订单/报价的税率与折扣率同样是百分数）
ALTER TABLE `so_sales_order_header`
    MODIFY COLUMN `discount_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '整单折扣率（百分数：5 表示让利 5%）';
ALTER TABLE `so_sales_order_line`
    MODIFY COLUMN `tax_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '税率（百分数：13 表示 13%）',
    MODIFY COLUMN `discount_percent` decimal(18, 6) NULL DEFAULT NULL COMMENT '行折扣率（百分数：5 表示让利 5%）';
