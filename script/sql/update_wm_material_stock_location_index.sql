-- ============================================================
-- 为 wm_material_stock.location_id 建索引
-- ============================================================
-- 用途：3D 仓库可视化库位钻取（GET /erp/wm/stock/list?locationId=）与
--       库存聚合（aggregateStockByWarehouse GROUP BY location_id）的性能保障。
-- 大仓库场景下，按 location_id 过滤/聚合若无索引将全表扫描。
-- 适用数据库：ry-erp
-- 特性：幂等可重复执行（information_schema 判存后再 ADD）
-- 对应文档：doc/erp/plans/2026-07-04-erp-wm-3d-warehouse-m2m3-plan.md（任务 10b）
-- 创建日期：2026-07-04
-- ============================================================

-- 复合 (location_id, del_flag)：覆盖库位钻取过滤 + 软删除条件
SET @idx_cnt := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = 'ry-erp'
    AND table_name = 'wm_material_stock'
    AND index_name = 'idx_wm_material_stock_location'
);
SET @ddl := IF(@idx_cnt = 0,
  'ALTER TABLE `ry-erp`.`wm_material_stock` ADD INDEX `idx_wm_material_stock_location` (`location_id`, `del_flag`)',
  'SELECT ''idx_wm_material_stock_location already exists, skipped.'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 验证（执行后人工核对）：
--   SHOW INDEX FROM `ry-erp`.`wm_material_stock` WHERE Key_name = 'idx_wm_material_stock_location';
--   EXPLAIN SELECT location_id, COUNT(*), SUM(stock_quantity)
--     FROM `ry-erp`.`wm_material_stock`
--     WHERE location_id = 9900000101 AND del_flag = '0'
--     GROUP BY location_id;  -- 期望 key = idx_wm_material_stock_location
-- ============================================================
