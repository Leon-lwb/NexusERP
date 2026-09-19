-- 销售退货闭环：新增 so_sales_order_line.returned_qty（销售模块优化决策 Q2「尽量全闭环」/ T3.3）
-- 背景:
--   退货与销售订单行此前**零数量关联**：既没有「已退货数量」列，退货执行入库时也不回写订单行、
--   不做任何超退校验 —— 同一订单行可以无限次退货，每次都照常加库存，形成账实不符。
--   本次补齐：执行入库时按条件更新累加 returned_qty，并强制
--     COALESCE(returned_qty,0) + 本次退货量 <= COALESCE(delivered_qty,0)
--   （超出即整体回滚，不会留下「状态已 RECEIVED 但数量未回写」的半成品）。
-- 目标库: ERP 业务库 (ry-erp)。
-- 执行方式: 幂等（MySQL 8.0 支持 ADD COLUMN IF NOT EXISTS 之外的写法需注意；
--           本脚本使用普通 ADD COLUMN，**重复执行会报 Duplicate column name**，
--           执行前请先跑下面的前置检查确认列不存在）。

-- ============================================================
-- 前置检查（应无输出）：确认列尚未存在
-- ============================================================
-- SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'so_sales_order_line' AND COLUMN_NAME = 'returned_qty';

-- ============================================================
-- 1. 新增已退货数量列（放在 invoiced_qty 之后，与「已发货/已开票/已退货」同组）
-- ============================================================
ALTER TABLE `so_sales_order_line`
    ADD COLUMN `returned_qty` decimal(18, 4) NULL DEFAULT 0.0000
        COMMENT '已退货数量（退货单执行入库时累加；超退守卫：returned_qty + 本次 <= delivered_qty）'
        AFTER `invoiced_qty`;

-- ============================================================
-- 2. 存量数据回填（可选，但**建议执行**）：按已执行入库的退货单补齐历史已退数量
--    只统计 RECEIVED（已入库）状态的退货单；DRAFT/CONFIRMED/APPROVING 未入库，不应计入。
--    说明：回填后 returned_qty 与历史退货数据一致，超退守卫才能正确工作。
-- ============================================================
UPDATE `so_sales_order_line` l
    LEFT JOIN (
        SELECT rl.`so_line_id` AS so_line_id, SUM(rl.`return_quantity`) AS returned
          FROM `so_return_line` rl
          JOIN `so_return` r ON r.`return_id` = rl.`return_id`
         WHERE r.`status` = 'RECEIVED'
           AND r.`del_flag` = '0'
           AND rl.`so_line_id` IS NOT NULL
         GROUP BY rl.`so_line_id`
    ) agg ON agg.so_line_id = l.`line_id`
   SET l.`returned_qty` = COALESCE(agg.returned, 0);

-- ============================================================
-- 3. 复核：确认列已建立，并抽查回填结果
-- ============================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'so_sales_order_line' AND COLUMN_NAME = 'returned_qty';
--
-- -- 应无输出：已退货数量不得超过已发货数量
-- SELECT line_id, line_no, delivered_qty, returned_qty FROM so_sales_order_line
--  WHERE COALESCE(returned_qty, 0) > COALESCE(delivered_qty, 0);
