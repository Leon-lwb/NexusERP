-- 严格阶段：必须先完成扩展、逐行租户凭据清理、完整审计、可恢复备份及独占停写窗口。
-- 本文件只生成供人工审核；MySQL5.7 DDL隐式提交，失败须保全部分完成状态，禁止--force。
-- 明确设置expected_schema/expected_server_uuid/backup_reference/maintenance_reference及cleanup_confirmed。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS `material_trace_strict_tenants_20261019`;
DELIMITER $$
CREATE PROCEDURE `material_trace_strict_tenants_20261019`() SQL SECURITY INVOKER
BEGIN
  IF DATABASE() IS NULL OR @material_trace_expected_schema IS NULL OR BINARY DATABASE()<>BINARY @material_trace_expected_schema
     OR @material_trace_expected_server_uuid IS NULL OR BINARY @@server_uuid<>BINARY @material_trace_expected_server_uuid
     OR @material_trace_backup_reference IS NULL OR CHAR_LENGTH(TRIM(@material_trace_backup_reference))=0
     OR @material_trace_maintenance_reference IS NULL OR CHAR_LENGTH(TRIM(@material_trace_maintenance_reference))=0
     OR @material_trace_strict_tenant_cleanup_confirmed IS NULL OR BINARY @material_trace_strict_tenant_cleanup_confirmed<>BINARY 'TENANT_CLEANUP_VERIFIED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='严格租户约束须显式核实目标、UUID、备份、停写及清理确认';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND ENGINE='InnoDB')<>1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND IS_NULLABLE IN ('YES','NO'))<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='wm_sn租户列或基线引擎偏离契约';
  END IF;
  IF EXISTS (SELECT 1 FROM `wm_sn` WHERE tenant_id IS NULL OR CHAR_LENGTH(TRIM(tenant_id))=0) THEN
    SELECT 'wm_sn' AS source_table,COUNT(*) AS unresolved_tenants FROM `wm_sn` WHERE tenant_id IS NULL OR CHAR_LENGTH(TRIM(tenant_id))=0;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='仍有NULL或空白租户，必须凭原证据清理；禁止回填未知租户';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND ENGINE='InnoDB')<>1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND IS_NULLABLE IN ('YES','NO'))<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='wm_material_stock租户列或基线引擎偏离契约';
  END IF;
  IF EXISTS (SELECT 1 FROM `wm_material_stock` WHERE tenant_id IS NULL OR CHAR_LENGTH(TRIM(tenant_id))=0) THEN
    SELECT 'wm_material_stock' AS source_table,COUNT(*) AS unresolved_tenants FROM `wm_material_stock` WHERE tenant_id IS NULL OR CHAR_LENGTH(TRIM(tenant_id))=0;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='仍有NULL或空白租户，必须凭原证据清理；禁止回填未知租户';
  END IF;
  IF EXISTS (SELECT 1 FROM wm_sn GROUP BY tenant_id,sn_code HAVING COUNT(*)>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='正式码存在旧唯一性冲突，保持原行并先核实';
  END IF;
  IF EXISTS (SELECT 1 FROM wm_material_stock WHERE tracking_version>=2 AND stock_quantity>0 AND sn_id IS NULL)
     OR EXISTS (SELECT 1 FROM wm_material_stock WHERE tracking_version>=2 AND stock_quantity>0 GROUP BY tenant_id,sn_id HAVING COUNT(*)>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='新版正库存身份缺失或重复，禁止收紧前掩盖旧冲突';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='active_sn_id' AND EXTRA LIKE '%STORED GENERATED%' AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(GENERATION_EXPRESSION,'`',''),' ',''),'(',''),')',''))='casewhentracking_version>=2andstock_quantity>0thensn_idelsenullend')<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='扩展阶段active_sn_id生成列尚未正确安装';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND INDEX_NAME='uk_sn_code')<>2
     OR (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND INDEX_NAME='uk_sn_code')<>'sn_code,tenant_id'
     OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND INDEX_NAME='uk_sn_code')<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='uk_sn_code唯一键尚未正确安装';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND INDEX_NAME='uk_material_stock_active_sn')<>2
     OR (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND INDEX_NAME='uk_material_stock_active_sn')<>'tenant_id,active_sn_id'
     OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND INDEX_NAME='uk_material_stock_active_sn')<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='uk_material_stock_active_sn唯一键尚未正确安装';
  END IF;
  IF (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND COLUMN_NAME='tenant_id')='YES'
     OR (SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND COLUMN_NAME='tenant_id') IS NOT NULL THEN
    ALTER TABLE `wm_sn` MODIFY COLUMN `tenant_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID，已凭原证据完成清理；禁止未知租户默认值';
  END IF;
  IF (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='tenant_id')='YES'
     OR (SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='tenant_id') IS NOT NULL THEN
    ALTER TABLE `wm_material_stock` MODIFY COLUMN `tenant_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID，已凭原证据完成清理；禁止未知租户默认值';
  END IF;
END$$
DELIMITER ;
CALL `material_trace_strict_tenants_20261019`();
DROP PROCEDURE `material_trace_strict_tenants_20261019`;
