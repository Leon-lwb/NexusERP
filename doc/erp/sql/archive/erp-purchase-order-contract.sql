-- =============================================================================
-- 采购订单（pu_purchase_order）合同关联预留字段 - 数据库迁移脚本
-- 日期: 2026-06-23
-- 说明: 实体 PuPurchaseOrder 已声明 contract_id / contract_no 两个预留字段
--       （commit b6f4c3c7「优化BOM、预留采购订单合同关联」），用于未来合同模块
--       关联，但建表脚本未同步加列，导致 /erp/pu/order/list 查询报错：
--       Unknown column 'contract_id' in 'field list'。
--       本脚本向后兼容地为 pu_purchase_order 补齐这两列。
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
-- 1. pu_purchase_order 新增合同关联预留列
--    与实体 PuPurchaseOrder.contractId / contractNo 对应
-- -----------------------------------------------------------------------------
CALL proc_add_column_if_not_exists('pu_purchase_order', 'contract_id',
    "BIGINT(20) NULL DEFAULT NULL COMMENT '合同ID，关联pu_contract表（预留字段）'");
CALL proc_add_column_if_not_exists('pu_purchase_order', 'contract_no',
    "VARCHAR(64) NULL DEFAULT NULL COMMENT '合同编号（预留字段）'");

-- -----------------------------------------------------------------------------
-- 2. 清理临时存储过程
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS proc_add_column_if_not_exists;

-- =============================================================================
-- 迁移完成。
-- 执行后可校验：
--   DESC pu_purchase_order;  -- 应含 contract_id / contract_no
--   SELECT order_id, contract_id, contract_no FROM pu_purchase_order LIMIT 1;  -- 应正常返回
-- =============================================================================
