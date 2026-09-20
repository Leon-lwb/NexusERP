-- =============================================================================
-- 2026-09-20 收尾批次增量脚本（leftover）
-- 用途: 财务 P2「精度口径收敛」(FIN-ARAP-03) 的 DDL 部分。
-- 执行前提:
--   1) 已备份 ry-erp 库（本脚本会 ROUND 存量金额, 属不可逆的数据收敛）;
--   2) 应用已停止写入收付款单/核销单, 或已在维护窗口内;
--   3) 先执行文末「§0 前置检查」, 确认将被舍入的行数与金额影响面已评估。
-- 影响库: ry-erp
-- 是否需清 Redis 缓存: 否
-- 幂等性: 是（每处 ALTER 前先查 information_schema.COLUMNS 的 NUMERIC_SCALE, 已是目标精度则跳过）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- §0 前置检查（只读，请先人工执行并留存结果）
-- -----------------------------------------------------------------------------
-- 0.1 受影响的列当前精度（应为 decimal(18,4)）
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = 'ry-erp'
--    AND ((TABLE_NAME = 'fin_receipt'    AND COLUMN_NAME IN ('amount','settled_amount'))
--      OR (TABLE_NAME = 'fin_settlement' AND COLUMN_NAME = 'settle_amount'));
--
-- 0.2 有多少行会因舍入到 2 位而改变金额（>0 即需业务确认）
-- SELECT 'fin_receipt.amount' AS col, COUNT(*) AS changed_rows
--   FROM `fin_receipt` WHERE `amount` <> ROUND(`amount`, 2)
-- UNION ALL
-- SELECT 'fin_receipt.settled_amount', COUNT(*) FROM `fin_receipt`
--  WHERE `settled_amount` <> ROUND(`settled_amount`, 2)
-- UNION ALL
-- SELECT 'fin_settlement.settle_amount', COUNT(*) FROM `fin_settlement`
--  WHERE `settle_amount` <> ROUND(`settle_amount`, 2);
--
-- 0.3 核销对不平的规模（4 位累加 vs 2 位入账的历史差额）
-- SELECT s.`invoice_id`, SUM(s.`settle_amount`) AS settled_4dp, ROUND(SUM(s.`settle_amount`), 2) AS settled_2dp
--   FROM `fin_settlement` s WHERE s.`del_flag` = '0' GROUP BY s.`invoice_id`
--  HAVING settled_4dp <> settled_2dp;

-- -----------------------------------------------------------------------------
-- §1 精度收敛: fin_receipt.amount / settled_amount  decimal(18,4) -> decimal(18,2)
--    FIN-ARAP-03: 核销按 4 位累加、凭证与余额表按 2 位入账 → 永久对不平。
--    收敛方向为「向下对齐到凭证口径(2 位)」, 与 gl_voucher_line / 余额表一致。
--    存量数据先 ROUND 再改列, 避免 MySQL 静默截断(严格模式下会直接报错)。
-- -----------------------------------------------------------------------------
-- 1.1 先按 2 位舍入存量数据（幂等：重复执行结果不变）
UPDATE `fin_receipt`
   SET `amount` = ROUND(`amount`, 2)
 WHERE `amount` IS NOT NULL AND `amount` <> ROUND(`amount`, 2);

UPDATE `fin_receipt`
   SET `settled_amount` = ROUND(`settled_amount`, 2)
 WHERE `settled_amount` IS NOT NULL AND `settled_amount` <> ROUND(`settled_amount`, 2);

-- 1.2 再收敛列精度（幂等：已是 scale=2 则跳过）
SET @ddl := (
  SELECT IF(COUNT(*) = 1,
            'ALTER TABLE `fin_receipt` MODIFY COLUMN `amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT ''单据金额''',
            'DO 0')
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt'
     AND COLUMN_NAME = 'amount' AND NUMERIC_SCALE = 4);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(COUNT(*) = 1,
            'ALTER TABLE `fin_receipt` MODIFY COLUMN `settled_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT ''已核销金额''',
            'DO 0')
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_receipt'
     AND COLUMN_NAME = 'settled_amount' AND NUMERIC_SCALE = 4);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- §2 精度收敛: fin_settlement.settle_amount  decimal(18,4) -> decimal(18,2)
-- -----------------------------------------------------------------------------
UPDATE `fin_settlement`
   SET `settle_amount` = ROUND(`settle_amount`, 2)
 WHERE `settle_amount` IS NOT NULL AND `settle_amount` <> ROUND(`settle_amount`, 2);

SET @ddl := (
  SELECT IF(COUNT(*) = 1,
            'ALTER TABLE `fin_settlement` MODIFY COLUMN `settle_amount` decimal(18,2) NULL DEFAULT 0.00 COMMENT ''核销金额''',
            'DO 0')
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_settlement'
     AND COLUMN_NAME = 'settle_amount' AND NUMERIC_SCALE = 4);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- §3 回滚（仅在确认收敛方向错误时使用；无法恢复被舍入的 2 位以下尾差）
-- -----------------------------------------------------------------------------
-- ALTER TABLE `fin_receipt`    MODIFY COLUMN `amount` decimal(18,4) NULL DEFAULT 0.0000;
-- ALTER TABLE `fin_receipt`    MODIFY COLUMN `settled_amount` decimal(18,4) NULL DEFAULT 0.0000;
-- ALTER TABLE `fin_settlement` MODIFY COLUMN `settle_amount` decimal(18,4) NULL DEFAULT 0.0000;

-- =============================================================================
-- 说明: 本批次其余修复均不需要 DDL
--   * ERP-SO-04（报价转单重复）已改为「报价单状态 CAS（CONFIRMED -> CONVERTED, 影响 0 行即回滚）」
--     在代码层实现幂等, 无需新增 source_quotation_id 列与唯一键。
--   * ERP-BD-09 所需的 bd_client_credit 唯一键已由 script/sql/update/2026-09-20-uk-tenant-dimension.sql 提供。
--   * ERP-SO-01 / ERP-SO-02 / ERP-SO-08 / FIN-FA-02/03/04 / FIN-GL-03 均为纯代码修复。
--   * MQ-01 的 5 个死通道采用「注释定性为预留」而非删除, 无 DDL。
-- =============================================================================
