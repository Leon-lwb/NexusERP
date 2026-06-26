-- =============================================================================
-- 生产领料单（生产领料）全功能优化 - 数据库迁移脚本
-- 日期: 2026-06-23
-- 说明: 向后兼容地为 wm_production_issue / wm_production_issue_line 增加列，
--       支持多次部分领料（应领/已领/本次/欠领）、批次/库位、行状态、可用量追溯。
-- 特性: 幂等可重复执行（通过 information_schema 检查列是否存在），不会因列已存在
--       而报错，不涉及主键改动，不影响现有存量数据。
-- 适用: MySQL 5.7+ / 8.x，ry-erp 业务库
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 幂等加列存储过程：列不存在才添加，避免重复执行报错
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS proc_add_column_if_not_exists;
DELIMITER $$
CREATE PROCEDURE proc_add_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_def TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name,
                          '` ADD COLUMN `', p_column_name, '` ', p_column_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- -----------------------------------------------------------------------------
-- 1. wm_production_issue_line 行表新增列
--    支持多列数量语义：BOM 应领量 / 已领累计 / 本次领料 / 欠领，及行状态追溯
--    数量列类型与现有 quantity/unit_price/amount 保持一致 decimal(18,4)
-- -----------------------------------------------------------------------------
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'bom_qty',
    "DECIMAL(18,4) NULL DEFAULT NULL COMMENT 'BOM标准用量（单位产品用量）'");
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'loss_rate',
    "DECIMAL(8,4) NULL DEFAULT 0.0000 COMMENT '损耗率(%)'");
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'required_qty',
    "DECIMAL(18,4) NULL DEFAULT NULL COMMENT '应领数量（BOM定额×工单数量）'");
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'issued_qty',
    "DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '已领数量（累计已发）'");
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'current_qty',
    "DECIMAL(18,4) NULL DEFAULT NULL COMMENT '本次领料数量'");
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'short_qty',
    "DECIMAL(18,4) NULL DEFAULT NULL COMMENT '欠领数量（应领-已领）'");
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'requirement_detail_id',
    "BIGINT(20) NULL DEFAULT NULL COMMENT '物料需求明细ID（追溯工单BOM需求）'");
CALL proc_add_column_if_not_exists('wm_production_issue_line', 'line_status',
    "VARCHAR(20) NULL DEFAULT 'PENDING' COMMENT '行状态(PENDING/PARTIAL/ISSUED)'");

-- -----------------------------------------------------------------------------
-- 2. wm_production_issue 单头表新增列
--    领料类型、领料人、汇总数量/金额
-- -----------------------------------------------------------------------------
CALL proc_add_column_if_not_exists('wm_production_issue', 'issue_type',
    "VARCHAR(20) NULL DEFAULT 'NORMAL' COMMENT '领料类型(NORMAL普通/BACKFLUSH倒冲)'");
CALL proc_add_column_if_not_exists('wm_production_issue', 'issuer_id',
    "BIGINT(20) NULL DEFAULT NULL COMMENT '领料人ID'");
CALL proc_add_column_if_not_exists('wm_production_issue', 'issuer_name',
    "VARCHAR(100) NULL DEFAULT NULL COMMENT '领料人名称'");
CALL proc_add_column_if_not_exists('wm_production_issue', 'total_qty',
    "DECIMAL(18,4) NULL DEFAULT NULL COMMENT '总数量'");
CALL proc_add_column_if_not_exists('wm_production_issue', 'total_amount',
    "DECIMAL(18,4) NULL DEFAULT NULL COMMENT '总金额'");

-- -----------------------------------------------------------------------------
-- 3. 存量数据回填（可选，便于旧数据在新逻辑下可读）
--    旧领料单行无 required_qty/current_qty/short_qty 时，用 quantity 兜底
--    （幂等：仅在为 NULL 时回填）
-- -----------------------------------------------------------------------------
UPDATE wm_production_issue_line
SET required_qty = quantity
WHERE required_qty IS NULL AND quantity IS NOT NULL;

UPDATE wm_production_issue_line
SET short_qty = quantity
WHERE short_qty IS NULL AND quantity IS NOT NULL;

UPDATE wm_production_issue_line
SET issued_qty = 0
WHERE issued_qty IS NULL;

UPDATE wm_production_issue_line
SET line_status = 'PENDING'
WHERE line_status IS NULL OR line_status = '';

UPDATE wm_production_issue
SET issue_type = 'NORMAL'
WHERE issue_type IS NULL OR issue_type = '';

-- -----------------------------------------------------------------------------
-- 4. 清理临时存储过程
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS proc_add_column_if_not_exists;

-- =============================================================================
-- 迁移完成。
-- 执行后可校验：
--   DESC wm_production_issue;        -- 应含 issue_type/issuer_id/issuer_name/total_qty/total_amount
--   DESC wm_production_issue_line;  -- 应含 bom_qty/loss_rate/required_qty/issued_qty/current_qty/short_qty/requirement_detail_id/line_status
-- =============================================================================
