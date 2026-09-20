-- =====================================================================================
-- 2026-09-20 MES 质量域 P0 修复增量脚本（B4 批次）
-- 用途:
--   1) qc_inspect_template 增加 process_id / customer_id —— IPQC/OQC 与 IQC 一样按
--      "物料/工序/客户"维度匹配检验标准模板并生成检验明细（审计 MES-QC-02）。
--   2) wm_material_receive 增加 iqc_status —— 无到货通知的收货单也能在单据级记录 IQC 结论，
--      上架门禁据此拦截"无到货通知 / 收货行无批次号"的 IQC 不合格旁路（审计 MES-X-01）。
-- 执行前提: 幂等（先查 information_schema 再 ALTER），可重复执行。
-- 影响的库: ry-mes（第 1 段）、ry-erp（第 2 段）——脚本内以 USE 切换，请按库权限分别执行。
-- 是否需清 Redis 缓存: 否（仅 DDL，无字典/菜单变更）。
-- 加列前无需去重（新增列均可空，无唯一约束变更）。
-- =====================================================================================

-- ------------------------------------------------------------------ ry-mes
USE `ry-mes`;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `qc_inspect_template` ADD COLUMN `process_id` BIGINT NULL COMMENT ''工序ID(IPQC维度,空=不限工序)'' AFTER `material_name`',
  'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_inspect_template' AND COLUMN_NAME = 'process_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `qc_inspect_template` ADD COLUMN `customer_id` BIGINT NULL COMMENT ''客户ID(客户专属模板维度,空=不限客户)'' AFTER `process_id`',
  'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_inspect_template' AND COLUMN_NAME = 'customer_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 模板匹配按 (template_type,status,material_id,process_id,customer_id) 过滤，补覆盖索引
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `qc_inspect_template` ADD INDEX `idx_qc_tpl_match` (`template_type`, `status`, `material_id`, `process_id`, `customer_id`)',
  'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qc_inspect_template' AND INDEX_NAME = 'idx_qc_tpl_match');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------ ry-erp
USE `ry-erp`;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `wm_material_receive` ADD COLUMN `iqc_status` VARCHAR(20) NULL COMMENT ''来料检验结论: QUALIFIED/UNQUALIFIED(无到货通知时上架门禁依据)'' AFTER `iqc_code`',
  'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wm_material_receive' AND COLUMN_NAME = 'iqc_status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量数据回填: 已有 iqc_id 且收货单状态已上架/已入库的，按到货通知行结论回填（仅一次，重复执行幂等）
UPDATE `wm_material_receive` r
   SET r.`iqc_status` = (
       SELECT l.`iqc_status` FROM `wm_arrival_notice_line` l
        WHERE l.`notice_id` = r.`notice_id` AND l.`iqc_status` IS NOT NULL
        LIMIT 1)
 WHERE r.`iqc_status` IS NULL AND r.`notice_id` IS NOT NULL
   AND EXISTS (SELECT 1 FROM `wm_arrival_notice_line` l2
                WHERE l2.`notice_id` = r.`notice_id` AND l2.`iqc_status` IS NOT NULL);
