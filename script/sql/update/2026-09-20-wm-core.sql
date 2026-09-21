-- =============================================================================
-- 2026-09-20-wm-core.sql  —— ERP 仓储「库存内核」P0 修复配套增量脚本
-- -----------------------------------------------------------------------------
-- 用途    : ERP-WM-08（库存行「查不到就 insert」无唯一键 → 并发重复行）的唯一键 DDL 建议
-- 执行前提: 1) 目标库为 ry-erp（业务库），不是 ry-vue
--           2) 必须先在【只读】状态下执行下面的「前置检查」并确认重复行数为 0
--           3) MySQL >= 8.0.13（用到函数索引键部分 functional key parts）
--           4) 必须先完成 ERP-WM-07（调拨目标行查找带 frozen_flag=0 导致同维度新建第二行），
--              否则本唯一键会让调拨入库直接报重复键错误
-- 影响库  : ry-erp
-- 是否需清 Redis 缓存: 否（无字典/缓存依赖）
-- 幂等性  : 是（先查 information_schema 再决定是否 ALTER）
-- 可重复执行: 是
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 【第一步 · 前置检查，只读】存量重复库存行清点
-- 任何一行 count > 1 都说明已存在 ERP-WM-08 描述的重复行，加唯一键会直接失败。
-- 必须先人工合并（把 stock_quantity / reserved_quantity / total_cost 归并到保留行、
-- 另一行 del_flag 置 '2' 或物理删除）后再执行第二步。
-- -----------------------------------------------------------------------------
-- SELECT tenant_id, material_id, IFNULL(batch_id,0) AS batch_id, IFNULL(warehouse_id,0) AS warehouse_id,
--        IFNULL(location_id,0) AS location_id, IFNULL(vendor_id,0) AS vendor_id, IFNULL(sn_id,0) AS sn_id,
--        del_flag, COUNT(*) AS cnt, GROUP_CONCAT(stock_id) AS stock_ids
--   FROM wm_material_stock
--  GROUP BY tenant_id, material_id, IFNULL(batch_id,0), IFNULL(warehouse_id,0),
--           IFNULL(location_id,0), IFNULL(vendor_id,0), IFNULL(sn_id,0), del_flag
-- HAVING COUNT(*) > 1;

-- -----------------------------------------------------------------------------
-- 【第二步 · 建议 DDL】wm_material_stock 库存维度唯一键
-- 维度 = 租户 + 物料 + 批次 + 仓库 + 库位 + 供应商 + 序列号 + 删除标志，
-- 与 ReceiveInStrategy#findStockForReceive / TransferStrategy 的「查库存行」维度一致。
-- 由于 MySQL 唯一索引不对 NULL 去重（batch_id/vendor_id/sn_id 常为 NULL），
-- 这里用 IFNULL(...,0) 函数索引键部分把 NULL 归一化，否则 NULL 维度仍会重复插入。
-- 备选方案（若 MySQL < 8.0.13）：增加 3 个 STORED 生成列
--   batch_key  BIGINT AS (IFNULL(batch_id,0))  STORED,
--   vendor_key BIGINT AS (IFNULL(vendor_id,0)) STORED,
--   sn_key     BIGINT AS (IFNULL(sn_id,0))     STORED,
-- 再对 (tenant_id, material_id, batch_key, warehouse_id, location_id, vendor_key, sn_key, del_flag) 建唯一键。
-- -----------------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'wm_material_stock'
     AND INDEX_NAME = 'uk_stock_dim'
);
-- MySQL 5.7 不支持"表达式索引"(8.0.13+ 才有)，因此仅 8.0+ 才建该索引；
-- 5.7 请改用"生成列 + 普通唯一键"方案（见 doc/audit/2026-09-20-fix-wm-core.md 的待办）。
SET @ver := SUBSTRING_INDEX(VERSION(), '.', 2);
SET @ddl := IF(@idx_exists > 0,
  'SELECT ''uk_stock_dim already exists, skip'' AS msg',
  IF(@ver >= '8.0',
     'ALTER TABLE wm_material_stock ADD UNIQUE KEY uk_stock_dim (tenant_id, material_id, (IFNULL(batch_id,0)), (IFNULL(warehouse_id,0)), (IFNULL(location_id,0)), (IFNULL(vendor_id,0)), (IFNULL(sn_id,0)), del_flag)',
     'SELECT ''[SKIP] uk_stock_dim: 当前 MySQL 5.7 不支持表达式索引, 需改用生成列方案后再建(详见 fix-wm-core.md)'' AS msg'));
PREPARE wm_core_stmt FROM @ddl;
EXECUTE wm_core_stmt;
DEALLOCATE PREPARE wm_core_stmt;

-- -----------------------------------------------------------------------------
-- 【第三步 · 回滚】
-- ALTER TABLE wm_material_stock DROP INDEX uk_stock_dim;
-- -----------------------------------------------------------------------------
