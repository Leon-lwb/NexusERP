-- ==========================================================================================
-- 增量脚本: ERP 仓储「单据与作业」批次 (B-wm-doc)
-- 用途    :
--   1) wm_arrival_notice_line 增加「已收数量」列 received_qty      —— 对应审计 ERP-WM-12
--      （收货执行时按物料原子累加回写；通知单状态改为「全部行收满才 FINISHED，否则 PARTIAL_RECEIVED」）
--   2) wm_transfer(transfer_no, tenant_id) 补唯一索引 uk_transfer_no            —— ERP-WM-13
--   3) wm_subcontract_issue(issue_no, tenant_id) 补唯一索引 uk_sc_issue_no      —— ERP-WM-13
--   4) wm_subcontract_receive(receive_no, tenant_id) 补唯一索引 uk_sc_receive_no —— ERP-WM-13
-- 说明    : ERP-WM-02（盘点实盘数不得默认 0）**无需 DDL** —— 经核对 script/sql/ry-erp.sql:6884，
--           wm_stock_taking_line.actual_quantity 本就是 `decimal(18,4) NULL DEFAULT NULL`，
--           代码侧只需把 generateLines 的初始值由 0 改为 NULL。
-- 执行前提: 已部署 ruoyi-erp 模块本次代码变更；建议业务低峰执行。
-- 影响库  : ry-erp
-- 幂等性  : 是。全部语句先查 information_schema 再 ALTER，重复执行只会打印"已存在, 跳过"。
--           **但唯一索引部分必须先完成前置检查（见下方注释）并确认无重复数据**，
--           否则 ALTER 会因存量重复单号失败（失败即回滚，不会破坏数据）。
-- Redis   : 无需清理（本次变更不涉及字典/缓存）
-- 日期    : 2026-09-20
-- ==========================================================================================

-- ------------------------------------------------------------------------------------------
-- 1) wm_arrival_notice_line.received_qty
-- ------------------------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wm_arrival_notice_line'
      AND COLUMN_NAME = 'received_qty');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `wm_arrival_notice_line` ADD COLUMN `received_qty` double(12, 2) NOT NULL DEFAULT 0 COMMENT ''已收数量(收货执行按物料累加回写, ERP-WM-12)'' AFTER `quantity_quanlified`',
    'SELECT ''wm_arrival_notice_line.received_qty 已存在, 跳过'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量数据升级（可选，幂等）：把已 FINISHED 的通知单行已收量按到货量补齐，避免历史单据显示"未收"
-- UPDATE wm_arrival_notice_line nl
--   JOIN wm_arrival_notice n ON n.notice_id = nl.notice_id
--    SET nl.received_qty = IFNULL(nl.quantity_arrival, 0)
--  WHERE n.status = 'FINISHED' AND IFNULL(nl.received_qty, 0) = 0;

-- ------------------------------------------------------------------------------------------
-- 2) wm_transfer 单号唯一索引
-- ------------------------------------------------------------------------------------------
-- ★ 前置检查（必须先执行；返回任何行都说明存在重复单号，需先人工去重再执行 ALTER）：
--   SELECT transfer_no, tenant_id, COUNT(*) AS c
--     FROM wm_transfer
--    WHERE del_flag = '0'
--    GROUP BY transfer_no, tenant_id
--   HAVING c > 1;
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wm_transfer'
      AND INDEX_NAME = 'uk_transfer_no');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `wm_transfer` ADD UNIQUE KEY `uk_transfer_no` (`transfer_no`, `tenant_id`)',
    'SELECT ''wm_transfer.uk_transfer_no 已存在, 跳过'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------------------
-- 3) wm_subcontract_issue 单号唯一索引
-- ------------------------------------------------------------------------------------------
-- ★ 前置检查：
--   SELECT issue_no, tenant_id, COUNT(*) AS c
--     FROM wm_subcontract_issue
--    WHERE del_flag = '0'
--    GROUP BY issue_no, tenant_id
--   HAVING c > 1;
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wm_subcontract_issue'
      AND INDEX_NAME = 'uk_sc_issue_no');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `wm_subcontract_issue` ADD UNIQUE KEY `uk_sc_issue_no` (`issue_no`, `tenant_id`)',
    'SELECT ''wm_subcontract_issue.uk_sc_issue_no 已存在, 跳过'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------------------------
-- 4) wm_subcontract_receive 单号唯一索引
-- ------------------------------------------------------------------------------------------
-- ★ 前置检查：
--   SELECT receive_no, tenant_id, COUNT(*) AS c
--     FROM wm_subcontract_receive
--    WHERE del_flag = '0'
--    GROUP BY receive_no, tenant_id
--   HAVING c > 1;
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wm_subcontract_receive'
      AND INDEX_NAME = 'uk_sc_receive_no');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `wm_subcontract_receive` ADD UNIQUE KEY `uk_sc_receive_no` (`receive_no`, `tenant_id`)',
    'SELECT ''wm_subcontract_receive.uk_sc_receive_no 已存在, 跳过'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==========================================================================================
-- 回滚语句（仅在确需回退本次变更时手工执行）
-- ==========================================================================================
-- ALTER TABLE `wm_transfer` DROP INDEX `uk_transfer_no`;
-- ALTER TABLE `wm_subcontract_issue` DROP INDEX `uk_sc_issue_no`;
-- ALTER TABLE `wm_subcontract_receive` DROP INDEX `uk_sc_receive_no`;
-- ALTER TABLE `wm_arrival_notice_line` DROP COLUMN `received_qty`;
-- ==========================================================================================
