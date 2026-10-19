-- 2026-10-18 物料身份与追溯增量；仅适用已核实的ERP业务数据源。
-- 本文件只提供待审SQL；离线工具不执行SQL，未接触既有业务库。
-- 操作者须先显式设置 @material_trace_expected_schema 与 @material_trace_backup_reference。
-- 先停写并核对备份及基线；禁止 --force。DDL隐式提交，失败后按元数据检查安全续跑。
-- 禁止以含DROP TABLE的主安装SQL迁移存量库；本文件不改写历史业务行。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS `material_trace_20261018_erp`;
DELIMITER $$
CREATE PROCEDURE `material_trace_20261018_erp`()
BEGIN
  IF DATABASE() IS NULL OR @material_trace_expected_schema IS NULL
     OR BINARY DATABASE() <> BINARY @material_trace_expected_schema
     OR @material_trace_backup_reference IS NULL OR CHAR_LENGTH(TRIM(@material_trace_backup_reference)) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '必须显式核实ERP目标库及备份引用';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_barcode' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表wm_barcode不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_material' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表bd_material不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_serial_number_rule' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表bd_serial_number_rule不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表wm_sn不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表wm_material_stock不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock_tx' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表wm_material_stock_tx不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_barcode' AND COLUMN_NAME='primary_object_type') = 0 THEN
    ALTER TABLE `wm_barcode` ADD COLUMN `primary_object_type` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '不可重绑的主对象类型SN/BATCH/MATERIAL/DOCUMENT，历史NULL保持未知';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_barcode' AND (
         COLUMN_NAME='primary_object_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='YES'
         AND CHARACTER_MAXIMUM_LENGTH=16
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_barcode.primary_object_type类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_material' AND COLUMN_NAME='allow_mixed_lot') = 0 THEN
    ALTER TABLE `bd_material` ADD COLUMN `allow_mixed_lot` tinyint(1) NOT NULL DEFAULT 1 COMMENT '同实物同组件物料是否允许混批，默认允许';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_material' AND (
         COLUMN_NAME='allow_mixed_lot'
         AND DATA_TYPE='tinyint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段bd_material.allow_mixed_lot类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_material' AND COLUMN_NAME='tracking_version') = 0 THEN
    ALTER TABLE `bd_material` ADD COLUMN `tracking_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '物料追踪策略版本';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_material' AND (
         COLUMN_NAME='tracking_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段bd_material.tracking_version类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_material' AND COLUMN_NAME='tracking_stage') = 0 THEN
    ALTER TABLE `bd_material` ADD COLUMN `tracking_stage` varchar(16) NOT NULL DEFAULT 'AUDIT' COMMENT 'AUDIT只审计，STRICT按物料逐步启用';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_material' AND (
         COLUMN_NAME='tracking_stage'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=16
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='AUDIT')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段bd_material.tracking_stage类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_serial_number_rule' AND COLUMN_NAME='rule_version') = 0 THEN
    ALTER TABLE `bd_serial_number_rule` ADD COLUMN `rule_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '发号规则版本，规则变更递增';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bd_serial_number_rule' AND (
         COLUMN_NAME='rule_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段bd_serial_number_rule.rule_version类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND COLUMN_NAME='registration_command') = 0 THEN
    ALTER TABLE `wm_sn` ADD COLUMN `registration_command` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '原发号预留命令ID，不补造历史命令';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND (
         COLUMN_NAME='registration_command'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='YES'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_sn.registration_command类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND COLUMN_NAME='identity_version') = 0 THEN
    ALTER TABLE `wm_sn` ADD COLUMN `identity_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '正式身份版本';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND (
         COLUMN_NAME='identity_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_sn.identity_version类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='tracking_version') = 0 THEN
    ALTER TABLE `wm_material_stock` ADD COLUMN `tracking_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '历史默认1，已对账单件库存才升级2';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND (
         COLUMN_NAME='tracking_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_material_stock.tracking_version类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='valuation_group_id') = 0 THEN
    ALTER TABLE `wm_material_stock` ADD COLUMN `valuation_group_id` bigint(20) DEFAULT NULL COMMENT '原计价组ID，不改变原金额';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND (
         COLUMN_NAME='valuation_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_material_stock.valuation_group_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='active_sn_id') = 0 THEN
    ALTER TABLE `wm_material_stock` ADD COLUMN `active_sn_id` bigint(20) GENERATED ALWAYS AS (CASE WHEN tracking_version >= 2 AND stock_quantity > 0 THEN sn_id ELSE NULL END) STORED COMMENT '仅新版正库存占用正式身份';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND (
         COLUMN_NAME='active_sn_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_material_stock.active_sn_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock_tx' AND COLUMN_NAME='tracking_version') = 0 THEN
    ALTER TABLE `wm_material_stock_tx` ADD COLUMN `tracking_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '事务追踪版本，历史保留1';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock_tx' AND (
         COLUMN_NAME='tracking_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_material_stock_tx.tracking_version类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock_tx' AND COLUMN_NAME='posting_group_id') = 0 THEN
    ALTER TABLE `wm_material_stock_tx` ADD COLUMN `posting_group_id` bigint(20) DEFAULT NULL COMMENT '冻结过账证明组ID';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock_tx' AND (
         COLUMN_NAME='posting_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段wm_material_stock_tx.posting_group_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='active_sn_id' AND EXTRA LIKE '%STORED GENERATED%' AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(GENERATION_EXPRESSION,'`',''),' ',''),'(',''),')',''))='casewhentracking_version>=2andstock_quantity>0thensn_idelsenullend') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'active_sn_id必须为约定的新版正库存生成列';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='sn_code' AND DATA_TYPE='varchar') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SN列基线必须为varchar';
  END IF;
  IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='sn_code') < 128 THEN
    ALTER TABLE `wm_material_stock` MODIFY COLUMN `sn_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '正式SN原码，最长128位';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock_tx' AND COLUMN_NAME='sn_code' AND DATA_TYPE='varchar') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SN列基线必须为varchar';
  END IF;
  IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock_tx' AND COLUMN_NAME='sn_code') < 128 THEN
    ALTER TABLE `wm_material_stock_tx` MODIFY COLUMN `sn_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '正式SN原码，最长128位';
  END IF;
  -- 只变更新行默认值，历史IN_STOCK等状态保持原值。
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND COLUMN_NAME='status' AND COLUMN_DEFAULT='REGISTERED') = 0 THEN
    ALTER TABLE `wm_sn` ALTER COLUMN `status` SET DEFAULT 'REGISTERED';
  END IF;
  -- 不静默修复；新版活跃身份重复或缺失时立即停止，旧版库存不强制迁移。
  IF EXISTS (SELECT 1 FROM wm_material_stock WHERE tracking_version >= 2 AND stock_quantity > 0 AND sn_id IS NOT NULL GROUP BY tenant_id,sn_id HAVING COUNT(*) > 1) THEN
    SELECT tenant_id,sn_id,COUNT(*) AS conflicting_rows FROM wm_material_stock WHERE tracking_version >= 2 AND stock_quantity > 0 AND sn_id IS NOT NULL GROUP BY tenant_id,sn_id HAVING COUNT(*) > 1;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新版活跃SN存在重复，须先审计和凭据修复';
  END IF;
  IF EXISTS (SELECT 1 FROM wm_material_stock s LEFT JOIN bd_material m ON m.tenant_id=s.tenant_id AND m.material_id=s.material_id WHERE s.tracking_version >= 2 AND s.stock_quantity > 0 AND (s.sn_id IS NOT NULL OR m.is_serial_manage=1) AND (s.tenant_id IS NULL OR s.sn_id IS NULL OR s.stock_quantity <> 1 OR NOT EXISTS (SELECT 1 FROM wm_sn n WHERE n.tenant_id=s.tenant_id AND n.sn_id=s.sn_id AND n.material_id=s.material_id))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新版SN库存缺身份或不是单件，禁止静默升级';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND INDEX_NAME='uk_material_stock_active_sn') = 0 THEN
    ALTER TABLE `wm_material_stock` ADD UNIQUE KEY `uk_material_stock_active_sn` (`tenant_id`,`active_sn_id`);
  ELSE
    IF (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND INDEX_NAME='uk_material_stock_active_sn') <> 'tenant_id,active_sn_id'
       OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND INDEX_NAME='uk_material_stock_active_sn') <> 0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '索引uk_material_stock_active_sn定义偏离契约';
    END IF;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND INDEX_NAME='idx_sn_registration_command') = 0 THEN
    ALTER TABLE `wm_sn` ADD KEY `idx_sn_registration_command` (`tenant_id`,`registration_command`);
  ELSE
    IF (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND INDEX_NAME='idx_sn_registration_command') <> 'tenant_id,registration_command'
       OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND INDEX_NAME='idx_sn_registration_command') <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '索引idx_sn_registration_command定义偏离契约';
    END IF;
  END IF;

CREATE TABLE IF NOT EXISTS `wm_serial_reservation` (
  `reservation_id` bigint(20) NOT NULL COMMENT '发号预留ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范请求摘要，相同命令不能改变参数',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `quantity` int(11) NOT NULL COMMENT '本次预留件数',
  `rule_id` bigint(20) DEFAULT NULL COMMENT '号码规则ID',
  `rule_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '预留时规则版本',
  `rule_snapshot` longtext NOT NULL COMMENT '预留时冻结规则JSON，重试不得读取新规则',
  `source_type` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '业务来源类型',
  `source_id` bigint(20) DEFAULT NULL COMMENT '业务来源ID',
  `cycle_key` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '号段周期键',
  `biz_date` date NOT NULL COMMENT '发号业务日期',
  `status` varchar(24) NOT NULL DEFAULT 'RESERVED' COMMENT '预留状态，取消不回收号码',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`reservation_id`),
  UNIQUE KEY `uk_serial_reservation_command` (`tenant_id`,`command_id`),
  KEY `idx_serial_reservation_source` (`tenant_id`,`source_type`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='正式实物发号预留，号码永久占用';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND COLUMN_NAME IN ('reservation_id','tenant_id','command_id','fingerprint','material_id','quantity','rule_id','rule_version','rule_snapshot','source_type','source_id','cycle_key','biz_date','status','created_at','updated_at')) <> 16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_reservation字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND ((
         COLUMN_NAME='reservation_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='command_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='fingerprint'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='quantity'
         AND DATA_TYPE='int'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='rule_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='rule_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='rule_snapshot'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='cycle_key'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='biz_date'
         AND DATA_TYPE='date'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='RESERVED')
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_reservation字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='PRIMARY'),'') <> 'reservation_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='uk_serial_reservation_command'),'') <> 'tenant_id,command_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='uk_serial_reservation_command'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='uk_serial_reservation_command' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='idx_serial_reservation_source'),'') <> 'tenant_id,source_type,source_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='idx_serial_reservation_source'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_reservation' AND INDEX_NAME='idx_serial_reservation_source' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_reservation索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_serial_lifecycle_command` (
  `operation_id` bigint(20) NOT NULL COMMENT '正式身份生命周期操作ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '身份、操作人、原因、版本及动作的规范摘要',
  `action` varchar(32) NOT NULL COMMENT 'RETIRE_UNUSED_MANUAL等已核实生命周期动作',
  `identity_id` bigint(20) NOT NULL COMMENT '原正式身份wm_sn.sn_id',
  `reservation_id` bigint(20) NOT NULL COMMENT '原正式发号预留ID',
  `actor_id` bigint(20) NOT NULL COMMENT '认证上下文中的操作人',
  `reason` varchar(500) NOT NULL COMMENT '明确人工退休原因',
  `before_version` bigint(20) NOT NULL COMMENT '原身份版本',
  `after_version` bigint(20) NOT NULL COMMENT '动作提交后的身份版本',
  `before_status` varchar(16) NOT NULL COMMENT '原身份状态',
  `after_status` varchar(16) NOT NULL COMMENT '动作提交后的身份状态',
  `reservation_status` varchar(24) NOT NULL COMMENT '动作提交后的原预留状态，取消不回收号码',
  `source_type` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '核实后的原注册来源类型',
  `source_id` bigint(20) DEFAULT NULL COMMENT '原业务来源ID，ERP_MANUAL为空',
  `before_json` longtext NOT NULL COMMENT '完整原身份冻结JSON',
  `after_json` longtext NOT NULL COMMENT '动作后身份冻结JSON',
  `result_json` longtext NOT NULL COMMENT '同命令幂等回执冻结JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_serial_lifecycle_command` (`tenant_id`,`command_id`),
  KEY `idx_serial_lifecycle_identity` (`tenant_id`,`identity_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='正式SN生命周期命令审计，状态与审计同ERP事务提交，不回收号码';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND COLUMN_NAME IN ('operation_id','tenant_id','command_id','fingerprint','action','identity_id','reservation_id','actor_id','reason','before_version','after_version','before_status','after_status','reservation_status','source_type','source_id','before_json','after_json','result_json','created_at')) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_lifecycle_command字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND ((
         COLUMN_NAME='operation_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='command_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='fingerprint'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='action'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='identity_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='reservation_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='actor_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='reason'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=500
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='before_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='after_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='before_status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=16
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='after_status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=16
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='reservation_status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='before_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='after_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='result_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_lifecycle_command字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='PRIMARY'),'') <> 'operation_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='uk_serial_lifecycle_command'),'') <> 'tenant_id,command_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='uk_serial_lifecycle_command'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='uk_serial_lifecycle_command' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='idx_serial_lifecycle_identity'),'') <> 'tenant_id,identity_id,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='idx_serial_lifecycle_identity'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_lifecycle_command' AND INDEX_NAME='idx_serial_lifecycle_identity' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_lifecycle_command索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_serial_sequence` (
  `sequence_id` bigint(20) NOT NULL COMMENT '号段计数器ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `namespace` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '计数器命名空间',
  `rule_key` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规则或共享范围键',
  `cycle_key` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '周期键',
  `current_value` bigint(20) NOT NULL DEFAULT 0 COMMENT '已分配最大序号，只增不回收',
  `version` bigint(20) NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`sequence_id`),
  UNIQUE KEY `uk_serial_sequence_scope` (`tenant_id`,`namespace`,`rule_key`,`cycle_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='事务号段计数器';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND COLUMN_NAME IN ('sequence_id','tenant_id','namespace','rule_key','cycle_key','current_value','version','created_at','updated_at')) <> 9 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_sequence字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND ((
         COLUMN_NAME='sequence_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='namespace'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='rule_key'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='cycle_key'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='current_value'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='0')
         OR (
         COLUMN_NAME='version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 9 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_sequence字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND INDEX_NAME='PRIMARY'),'') <> 'sequence_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND INDEX_NAME='uk_serial_sequence_scope'),'') <> 'tenant_id,namespace,rule_key,cycle_key'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND INDEX_NAME='uk_serial_sequence_scope'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_serial_sequence' AND INDEX_NAME='uk_serial_sequence_scope' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_serial_sequence索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_sn_alias` (
  `alias_id` bigint(20) NOT NULL COMMENT '别名ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `identity_id` bigint(20) NOT NULL COMMENT '正式身份wm_sn.sn_id',
  `source_type` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '供应商、客户或旧凭据来源类型',
  `source_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '来源主体ID，无主体使用0避免空值绕过唯一键',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `alias_code` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '外部原码，区分大小写且不改写',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`alias_id`),
  UNIQUE KEY `uk_sn_alias_identity_source` (`tenant_id`,`identity_id`,`source_type`,`source_id`,`material_id`,`alias_code`),
  UNIQUE KEY `uk_sn_alias_command` (`tenant_id`,`command_id`),
  KEY `idx_sn_alias_lookup` (`tenant_id`,`alias_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='外部原码与正式实物身份的证据映射';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND COLUMN_NAME IN ('alias_id','tenant_id','identity_id','source_type','source_id','material_id','alias_code','command_id','created_at')) <> 9 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_sn_alias字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND ((
         COLUMN_NAME='alias_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='identity_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='0')
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='alias_code'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='command_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 9 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_sn_alias字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='PRIMARY'),'') <> 'alias_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='uk_sn_alias_identity_source'),'') <> 'tenant_id,identity_id,source_type,source_id,material_id,alias_code'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='uk_sn_alias_identity_source'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='uk_sn_alias_identity_source' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='uk_sn_alias_command'),'') <> 'tenant_id,command_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='uk_sn_alias_command'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='uk_sn_alias_command' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='idx_sn_alias_lookup'),'') <> 'tenant_id,alias_code'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='idx_sn_alias_lookup'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn_alias' AND INDEX_NAME='idx_sn_alias_lookup' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_sn_alias索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_document_sn` (
  `document_sn_id` bigint(20) NOT NULL COMMENT '单据实物明细ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `line_no` bigint(20) NOT NULL COMMENT '命令内稳定行号',
  `source_doc_type` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint(20) NOT NULL COMMENT '来源单据ID',
  `source_doc_line_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '来源单据行ID',
  `detail_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '提交时单据明细版本',
  `identity_id` bigint(20) NOT NULL COMMENT '正式实物身份',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT 'ERP批次ID',
  `source_stock_id` bigint(20) DEFAULT NULL COMMENT '来源库存ID',
  `target_location_id` bigint(20) DEFAULT NULL COMMENT '目标储位ID',
  `original_tx_id` bigint(20) DEFAULT NULL COMMENT '退回或冲正所引用的原事务ID',
  `posting_group_id` bigint(20) DEFAULT NULL COMMENT '过账证明组ID',
  `quantity` decimal(18,4) NOT NULL DEFAULT 1.0000 COMMENT '基本单位数量，SN行由服务校验为1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`document_sn_id`),
  UNIQUE KEY `uk_document_sn_command_line` (`tenant_id`,`command_id`,`line_no`),
  UNIQUE KEY `uk_document_sn_version_identity` (`tenant_id`,`source_doc_type`,`source_doc_id`,`source_doc_line_id`,`detail_version`,`identity_id`),
  KEY `idx_document_sn_identity` (`tenant_id`,`identity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='版本化单据实物明细，不改写历史凭据';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND COLUMN_NAME IN ('document_sn_id','tenant_id','command_id','line_no','source_doc_type','source_doc_id','source_doc_line_id','detail_version','identity_id','material_id','batch_id','source_stock_id','target_location_id','original_tx_id','posting_group_id','quantity','created_at')) <> 17 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_document_sn字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND ((
         COLUMN_NAME='document_sn_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='command_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='line_no'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_doc_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_doc_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_doc_line_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='0')
         OR (
         COLUMN_NAME='detail_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='identity_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='batch_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_stock_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='target_location_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='original_tx_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='posting_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='quantity'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT='1.0000')
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 17 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_document_sn字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='PRIMARY'),'') <> 'document_sn_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='uk_document_sn_command_line'),'') <> 'tenant_id,command_id,line_no'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='uk_document_sn_command_line'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='uk_document_sn_command_line' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='uk_document_sn_version_identity'),'') <> 'tenant_id,source_doc_type,source_doc_id,source_doc_line_id,detail_version,identity_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='uk_document_sn_version_identity'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='uk_document_sn_version_identity' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='idx_document_sn_identity'),'') <> 'tenant_id,identity_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='idx_document_sn_identity'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_document_sn' AND INDEX_NAME='idx_document_sn_identity' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_document_sn索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_posting_group` (
  `posting_group_id` bigint(20) NOT NULL COMMENT '过账证明组ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `source_doc_type` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint(20) NOT NULL COMMENT '来源单据ID',
  `source_doc_line_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '来源单据行ID',
  `detail_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '冻结明细版本',
  `tx_type` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '库存业务类型',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `valuation_group_id` bigint(20) DEFAULT NULL COMMENT '原计价组ID',
  `quantity` decimal(18,4) NOT NULL COMMENT '冻结基本单位数量',
  `amount` decimal(18,4) NOT NULL COMMENT '冻结四位金额，单件拆分合计保持不变',
  `fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等请求摘要',
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结明细内容摘要',
  `source_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结原业务单据行内容摘要，不补造历史来源',
  `policy_version` bigint(20) NOT NULL COMMENT '冻结时物料追踪策略版本，不推定旧证明版本',
  `original_posting_group_id` bigint(20) DEFAULT NULL COMMENT '补偿所引用的原过账组ID',
  `status` varchar(24) NOT NULL DEFAULT 'PREPARED' COMMENT '过账状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`posting_group_id`),
  UNIQUE KEY `uk_trace_posting_command` (`tenant_id`,`command_id`),
  UNIQUE KEY `uk_trace_posting_document_version` (`tenant_id`,`source_doc_type`,`source_doc_id`,`source_doc_line_id`,`detail_version`,`tx_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='原子库存过账证明，补偿另建新组';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND COLUMN_NAME IN ('posting_group_id','tenant_id','command_id','source_doc_type','source_doc_id','source_doc_line_id','detail_version','tx_type','material_id','valuation_group_id','quantity','amount','fingerprint','content_hash','source_hash','policy_version','original_posting_group_id','status','created_at','updated_at')) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_posting_group字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND ((
         COLUMN_NAME='posting_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='command_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_doc_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_doc_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_doc_line_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='0')
         OR (
         COLUMN_NAME='detail_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='tx_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='valuation_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='quantity'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='amount'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='fingerprint'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='content_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='policy_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='original_posting_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='PREPARED')
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_posting_group字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='PRIMARY'),'') <> 'posting_group_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='uk_trace_posting_command'),'') <> 'tenant_id,command_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='uk_trace_posting_command'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='uk_trace_posting_command' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='uk_trace_posting_document_version'),'') <> 'tenant_id,source_doc_type,source_doc_id,source_doc_line_id,detail_version,tx_type'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='uk_trace_posting_document_version'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_posting_group' AND INDEX_NAME='uk_trace_posting_document_version' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_posting_group索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_valuation_group` (
  `valuation_group_id` bigint(20) NOT NULL COMMENT '计价组ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `warehouse_id` bigint(20) NOT NULL COMMENT '原计价仓库ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT '原计价批次ID',
  `source_stock_id` bigint(20) DEFAULT NULL COMMENT '拆分前原库存ID',
  `quantity` decimal(18,4) NOT NULL COMMENT '原计价数量',
  `amount` decimal(18,4) NOT NULL COMMENT '原四位总金额',
  `unit_cost` decimal(18,6) NOT NULL COMMENT '冻结原单位成本，禁止按SN重新计价',
  `valuation_method` varchar(32) NOT NULL DEFAULT 'MOVING_AVERAGE' COMMENT '原计价方式',
  `policy_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '原计价策略版本',
  `valuation_snapshot` longtext NOT NULL COMMENT '冻结计价依据JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`valuation_group_id`),
  KEY `idx_valuation_group_material` (`tenant_id`,`material_id`,`warehouse_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='原计价范围冻结快照，拆分数量金额守恒';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND COLUMN_NAME IN ('valuation_group_id','tenant_id','material_id','warehouse_id','batch_id','source_stock_id','quantity','amount','unit_cost','valuation_method','policy_version','valuation_snapshot','created_at')) <> 13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_valuation_group字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND ((
         COLUMN_NAME='valuation_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='warehouse_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='batch_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_stock_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='quantity'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='amount'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='unit_cost'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=6
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='valuation_method'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='MOVING_AVERAGE')
         OR (
         COLUMN_NAME='policy_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='valuation_snapshot'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_valuation_group字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND INDEX_NAME='PRIMARY'),'') <> 'valuation_group_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND INDEX_NAME='idx_valuation_group_material'),'') <> 'tenant_id,material_id,warehouse_id,batch_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND INDEX_NAME='idx_valuation_group_material'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_valuation_group' AND INDEX_NAME='idx_valuation_group_material' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_valuation_group索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_outbox` (
  `event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '跨库稳定事件ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `source_module` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '事件源模块',
  `aggregate_type` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '来源业务聚合类型',
  `aggregate_id` bigint(20) NOT NULL COMMENT '来源业务聚合ID',
  `aggregate_version` bigint(20) NOT NULL COMMENT '同来源聚合单调版本',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `schema_version` int(11) NOT NULL DEFAULT 1 COMMENT '消息契约版本',
  `payload` longtext NOT NULL COMMENT '原始冻结载荷JSON，禁止重试时重新计算',
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始载荷UTF8字节SHA256',
  `status` varchar(24) NOT NULL DEFAULT 'NEW' COMMENT 'NEW、WAITING、BROKER_SENT、APPLIED或CONFLICT状态',
  `attempts` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `next_attempt_at` datetime DEFAULT NULL COMMENT '下次可重试时间，ACK重新激活时允许空值',
  `lease_owner` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前租约持有者',
  `lease_until` datetime DEFAULT NULL COMMENT '租约截止时间',
  `last_error` text COMMENT '脱敏后的最近错误',
  `result_payload` longtext COMMENT '幂等消费结果JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `applied_at` datetime DEFAULT NULL COMMENT '成功应用时间',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_trace_event_stream_version` (`tenant_id`,`source_module`,`aggregate_type`,`aggregate_id`,`aggregate_version`),
  KEY `idx_trace_event_retry` (`status`,`next_attempt_at`,`lease_until`),
  KEY `idx_trace_event_tenant` (`tenant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='本数据源事务内冻结的追溯发件箱';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND COLUMN_NAME IN ('event_id','tenant_id','source_module','aggregate_type','aggregate_id','aggregate_version','event_type','schema_version','payload','content_hash','status','attempts','next_attempt_at','lease_owner','lease_until','last_error','result_payload','created_at','updated_at','applied_at')) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_outbox字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND ((
         COLUMN_NAME='event_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_module'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=16
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='aggregate_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='aggregate_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='aggregate_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='event_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='schema_version'
         AND DATA_TYPE='int'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='payload'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='content_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='NEW')
         OR (
         COLUMN_NAME='attempts'
         AND DATA_TYPE='int'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='0')
         OR (
         COLUMN_NAME='next_attempt_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='lease_owner'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='YES'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='lease_until'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='last_error'
         AND DATA_TYPE='text'
         AND IS_NULLABLE='YES'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='result_payload'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='YES'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='applied_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL))) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_outbox字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='PRIMARY'),'') <> 'event_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='uk_trace_event_stream_version'),'') <> 'tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='uk_trace_event_stream_version'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='uk_trace_event_stream_version' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='idx_trace_event_retry'),'') <> 'status,next_attempt_at,lease_until'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='idx_trace_event_retry'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='idx_trace_event_retry' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='idx_trace_event_tenant'),'') <> 'tenant_id,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='idx_trace_event_tenant'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_outbox' AND INDEX_NAME='idx_trace_event_tenant' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_outbox索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_inbox` (
  `event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '跨库稳定事件ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `source_module` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '事件源模块',
  `aggregate_type` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '来源业务聚合类型',
  `aggregate_id` bigint(20) NOT NULL COMMENT '来源业务聚合ID',
  `aggregate_version` bigint(20) NOT NULL COMMENT '同来源聚合单调版本',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `schema_version` int(11) NOT NULL DEFAULT 1 COMMENT '消息契约版本',
  `payload` longtext NOT NULL COMMENT '原始冻结载荷JSON，禁止重试时重新计算',
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始载荷UTF8字节SHA256',
  `status` varchar(24) NOT NULL DEFAULT 'NEW' COMMENT 'NEW、WAITING、BROKER_SENT、APPLIED或CONFLICT状态',
  `attempts` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `next_attempt_at` datetime DEFAULT NULL COMMENT '下次可重试时间，ACK重新激活时允许空值',
  `lease_owner` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前租约持有者',
  `lease_until` datetime DEFAULT NULL COMMENT '租约截止时间',
  `last_error` text COMMENT '脱敏后的最近错误',
  `result_payload` longtext COMMENT '幂等消费结果JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `applied_at` datetime DEFAULT NULL COMMENT '成功应用时间',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_trace_event_stream_version` (`tenant_id`,`source_module`,`aggregate_type`,`aggregate_id`,`aggregate_version`),
  KEY `idx_trace_event_retry` (`status`,`next_attempt_at`,`lease_until`),
  KEY `idx_trace_event_tenant` (`tenant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='本数据源事务内冻结的追溯幂等收件箱';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND COLUMN_NAME IN ('event_id','tenant_id','source_module','aggregate_type','aggregate_id','aggregate_version','event_type','schema_version','payload','content_hash','status','attempts','next_attempt_at','lease_owner','lease_until','last_error','result_payload','created_at','updated_at','applied_at')) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_inbox字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND ((
         COLUMN_NAME='event_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_module'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=16
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='aggregate_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='aggregate_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='aggregate_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='event_type'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='schema_version'
         AND DATA_TYPE='int'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='payload'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='content_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='NEW')
         OR (
         COLUMN_NAME='attempts'
         AND DATA_TYPE='int'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='0')
         OR (
         COLUMN_NAME='next_attempt_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='lease_owner'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='YES'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='lease_until'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='last_error'
         AND DATA_TYPE='text'
         AND IS_NULLABLE='YES'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='result_payload'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='YES'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='applied_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL))) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_inbox字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='PRIMARY'),'') <> 'event_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='uk_trace_event_stream_version'),'') <> 'tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='uk_trace_event_stream_version'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='uk_trace_event_stream_version' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='idx_trace_event_retry'),'') <> 'status,next_attempt_at,lease_until'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='idx_trace_event_retry'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='idx_trace_event_retry' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='idx_trace_event_tenant'),'') <> 'tenant_id,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='idx_trace_event_tenant'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_inbox' AND INDEX_NAME='idx_trace_event_tenant' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_inbox索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_repair_run` (
  `run_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '修复运行ID',
  `target_database` varchar(64) NOT NULL COMMENT '显式核实的目标业务库',
  `target_environment` varchar(128) NOT NULL COMMENT '环境标识，不保存口令或连接串',
  `backup_reference` varchar(255) NOT NULL COMMENT '已核实的离线备份引用',
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批准计划摘要',
  `before_snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '完整修复前导出摘要',
  `after_snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '离线预演结果摘要',
  `journal_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批准运行日志摘要',
  `status` varchar(24) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED、APPLIED、REVERTED或待补偿',
  `version` bigint(20) NOT NULL DEFAULT 1 COMMENT '运行状态CAS版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='本数据源历史修复运行日志，跨库不伪装原子事务';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_run' AND COLUMN_NAME IN ('run_id','target_database','target_environment','backup_reference','plan_hash','before_snapshot_hash','after_snapshot_hash','journal_hash','status','version','created_at','updated_at')) <> 12 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_repair_run字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_run' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_run' AND ((
         COLUMN_NAME='run_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='target_database'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='target_environment'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='backup_reference'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=255
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='plan_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='before_snapshot_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='after_snapshot_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='journal_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='PLANNED')
         OR (
         COLUMN_NAME='version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 12 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_repair_run字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_run' AND INDEX_NAME='PRIMARY'),'') <> 'run_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_run' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_run' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_repair_run索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_repair_item` (
  `run_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '修复运行ID',
  `item_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计划内修复项ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `target_table` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '白名单目标表',
  `target_key` longtext NOT NULL COMMENT '包含租户的完整主键JSON',
  `before_json` longtext NOT NULL COMMENT '修复前完整行JSON',
  `after_json` longtext NOT NULL COMMENT '修复后完整行JSON',
  `evidence_json` longtext NOT NULL COMMENT '冻结来源凭据及审核依据JSON',
  `before_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '修复前规范行摘要，CAS比较依据',
  `after_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '修复后规范行摘要',
  `evidence_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '证据摘要',
  `status` varchar(24) NOT NULL DEFAULT 'PLANNED' COMMENT '修复项状态',
  `version` bigint(20) NOT NULL DEFAULT 1 COMMENT '修复项CAS版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`,`item_id`),
  KEY `idx_trace_repair_target` (`tenant_id`,`target_table`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='历史修复前后镜像与证据，不覆盖原业务凭据';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND COLUMN_NAME IN ('run_id','item_id','tenant_id','target_table','target_key','before_json','after_json','evidence_json','before_hash','after_hash','evidence_hash','status','version','created_at','updated_at')) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_repair_item字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND ((
         COLUMN_NAME='run_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='item_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='target_table'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='target_key'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='before_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='after_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='evidence_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='before_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='after_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='evidence_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='PLANNED')
         OR (
         COLUMN_NAME='version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_repair_item字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND INDEX_NAME='PRIMARY'),'') <> 'run_id,item_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND INDEX_NAME='idx_trace_repair_target'),'') <> 'tenant_id,target_table'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND INDEX_NAME='idx_trace_repair_target'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_repair_item' AND INDEX_NAME='idx_trace_repair_target' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_repair_item索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_audit_run` (
  `audit_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审计工件ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `material_id` bigint(20) DEFAULT NULL COMMENT '审计物料，未限定时为空',
  `status` varchar(24) NOT NULL DEFAULT 'REVIEW_REQUIRED' COMMENT '只读审计结论状态，不修改原业务',
  `summary` text COMMENT '审计摘要',
  `report_json` longtext NOT NULL COMMENT '完整审计报告工件',
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审计报告原始UTF8字节SHA256',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`audit_id`),
  KEY `idx_trace_audit_tenant_created` (`tenant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='物料追溯审计工件，只追加报告不改原业务';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND COLUMN_NAME IN ('audit_id','tenant_id','material_id','status','summary','report_json','content_hash','created_at')) <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_audit_run字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND ((
         COLUMN_NAME='audit_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='REVIEW_REQUIRED')
         OR (
         COLUMN_NAME='summary'
         AND DATA_TYPE='text'
         AND IS_NULLABLE='YES'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='report_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='content_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_audit_run字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND INDEX_NAME='PRIMARY'),'') <> 'audit_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND INDEX_NAME='idx_trace_audit_tenant_created'),'') <> 'tenant_id,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND INDEX_NAME='idx_trace_audit_tenant_created'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_audit_run' AND INDEX_NAME='idx_trace_audit_tenant_created' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_audit_run索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_material_reservation` (
  `reservation_id` bigint(20) NOT NULL COMMENT '实际库存预留ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `erp_wo_id` bigint(20) NOT NULL COMMENT '原ERP生产工单',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '原MES生产工单',
  `requirement_detail_id` bigint(20) NOT NULL COMMENT '原确认物料需求行',
  `material_id` bigint(20) NOT NULL COMMENT '预留组件物料ID',
  `policy_version` bigint(20) NOT NULL COMMENT '授权时物料追踪策略版本',
  `quantity` decimal(18,4) NOT NULL COMMENT '实际预留合计数量',
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原命令和实际选择的规范摘要',
  `status` varchar(24) NOT NULL DEFAULT 'RESERVED' COMMENT 'RESERVED预留或CONSUMED完工核销',
  PRIMARY KEY (`reservation_id`),
  UNIQUE KEY `uk_material_reservation_command` (`tenant_id`,`command_id`),
  KEY `idx_material_reservation_wo` (`tenant_id`,`erp_wo_id`,`status`),
  KEY `idx_material_reservation_requirement` (`tenant_id`,`requirement_detail_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='ERP倒冲实际库存预留头，与原库存同事务';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND COLUMN_NAME IN ('reservation_id','tenant_id','command_id','erp_wo_id','mes_wo_id','requirement_detail_id','material_id','policy_version','quantity','content_hash','status')) <> 11 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_reservation字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND ((
         COLUMN_NAME='reservation_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='command_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='erp_wo_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='mes_wo_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='requirement_detail_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='policy_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='quantity'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='content_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='RESERVED'))) <> 11 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_reservation字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='PRIMARY'),'') <> 'reservation_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='uk_material_reservation_command'),'') <> 'tenant_id,command_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='uk_material_reservation_command'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='uk_material_reservation_command' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='idx_material_reservation_wo'),'') <> 'tenant_id,erp_wo_id,status'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='idx_material_reservation_wo'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='idx_material_reservation_wo' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='idx_material_reservation_requirement'),'') <> 'tenant_id,requirement_detail_id,status'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='idx_material_reservation_requirement'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation' AND INDEX_NAME='idx_material_reservation_requirement' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_reservation索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_material_reservation_line` (
  `reservation_line_id` bigint(20) NOT NULL COMMENT '实际库存预留行ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `reservation_id` bigint(20) NOT NULL COMMENT '原预留头ID',
  `line_no` bigint(20) NOT NULL COMMENT '原命令内预留行号',
  `source_stock_id` bigint(20) NOT NULL COMMENT '实际选定的原库存行',
  `identity_id` bigint(20) DEFAULT NULL COMMENT 'ERP正式组件身份',
  `erp_batch_id` bigint(20) DEFAULT NULL COMMENT 'ERP批次ID，SN-only来源允许空值',
  `valuation_group_id` bigint(20) DEFAULT NULL COMMENT '原库存计价组，不按SN重新计价',
  `quantity` decimal(18,4) NOT NULL COMMENT '本行实际预留数量',
  `consumed_quantity` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '完工实际核销数量',
  `version` bigint(20) NOT NULL DEFAULT 1 COMMENT '原预留行并发版本',
  PRIMARY KEY (`reservation_line_id`),
  UNIQUE KEY `uk_material_reservation_line` (`tenant_id`,`reservation_id`,`line_no`),
  KEY `idx_material_reservation_stock` (`tenant_id`,`source_stock_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='ERP实际预留库存明细，保留正式组件与批次证据';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND COLUMN_NAME IN ('reservation_line_id','tenant_id','reservation_id','line_no','source_stock_id','identity_id','erp_batch_id','valuation_group_id','quantity','consumed_quantity','version')) <> 11 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_reservation_line字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND ((
         COLUMN_NAME='reservation_line_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='reservation_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='line_no'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_stock_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='identity_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='erp_batch_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='valuation_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='quantity'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='consumed_quantity'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT='0.0000')
         OR (
         COLUMN_NAME='version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1'))) <> 11 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_reservation_line字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='PRIMARY'),'') <> 'reservation_line_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='uk_material_reservation_line'),'') <> 'tenant_id,reservation_id,line_no'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='uk_material_reservation_line'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='uk_material_reservation_line' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='idx_material_reservation_stock'),'') <> 'tenant_id,source_stock_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='idx_material_reservation_stock'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_reservation_line' AND INDEX_NAME='idx_material_reservation_stock' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_reservation_line索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_material_consumption` (
  `consumption_id` bigint(20) NOT NULL COMMENT 'ERP实际消耗接收记录ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `manifest_id` bigint(20) NOT NULL COMMENT '原MES完工清单ID',
  `manifest_revision` int(11) NOT NULL COMMENT '原清单修订',
  `allocation_id` bigint(20) NOT NULL COMMENT '原实际消耗lt_wo_consumption.consumption_id，不是额度分配ID',
  `erp_wo_id` bigint(20) NOT NULL COMMENT '原ERP生产工单',
  `material_id` bigint(20) NOT NULL COMMENT '实际组件物料ID',
  `reservation_id` bigint(20) DEFAULT NULL COMMENT '原实际库存预留头',
  `reservation_line_id` bigint(20) DEFAULT NULL COMMENT '原实际库存预留行',
  `posting_group_id` bigint(20) DEFAULT NULL COMMENT '原发料过账证明组',
  `identity_id` bigint(20) DEFAULT NULL COMMENT 'ERP正式组件身份',
  `source_stock_id` bigint(20) DEFAULT NULL COMMENT '实际来源库存行',
  `erp_batch_id` bigint(20) DEFAULT NULL COMMENT 'ERP批次ID，SN-only来源允许空值',
  `quantity` decimal(18,4) NOT NULL COMMENT '实际消耗基本单位数量',
  `source_version` bigint(20) NOT NULL COMMENT 'MES冻结的实际来源版本',
  `evidence_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原实际来源证据SHA256',
  `source_event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ERP原授权或过账事件ID',
  PRIMARY KEY (`consumption_id`),
  UNIQUE KEY `uk_material_consumption_allocation` (`tenant_id`,`manifest_id`,`manifest_revision`,`allocation_id`),
  KEY `idx_material_consumption_wo` (`tenant_id`,`erp_wo_id`,`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='ERP按原MES实际消耗分配接收，不按BOM推算';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND COLUMN_NAME IN ('consumption_id','tenant_id','manifest_id','manifest_revision','allocation_id','erp_wo_id','material_id','reservation_id','reservation_line_id','posting_group_id','identity_id','source_stock_id','erp_batch_id','quantity','source_version','evidence_hash','source_event_id')) <> 17 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_consumption字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND ((
         COLUMN_NAME='consumption_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='manifest_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='manifest_revision'
         AND DATA_TYPE='int'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='allocation_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='erp_wo_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='reservation_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='reservation_line_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='posting_group_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='identity_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_stock_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='erp_batch_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='quantity'
         AND DATA_TYPE='decimal'
         AND IS_NULLABLE='NO'
         AND NUMERIC_PRECISION=18
         AND NUMERIC_SCALE=4
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='evidence_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_event_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL))) <> 17 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_consumption字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='PRIMARY'),'') <> 'consumption_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='uk_material_consumption_allocation'),'') <> 'tenant_id,manifest_id,manifest_revision,allocation_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='uk_material_consumption_allocation'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='uk_material_consumption_allocation' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='idx_material_consumption_wo'),'') <> 'tenant_id,erp_wo_id,material_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='idx_material_consumption_wo'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_material_consumption' AND INDEX_NAME='idx_material_consumption_wo' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_material_consumption索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_trace_stock_conversion_run` (
  `run_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前库存转换运行ID，不重用已回退运行',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `material_id` bigint(20) NOT NULL COMMENT '整物料当前库存转换范围',
  `target_database` varchar(64) NOT NULL COMMENT '显式核实的ERP目标库',
  `target_environment` varchar(128) NOT NULL COMMENT '显式核实的目标环境',
  `backup_reference` varchar(255) NOT NULL COMMENT '操作者核实的备份引用，不含连接凭据',
  `cutover_at` datetime NOT NULL COMMENT '真实physical census切点，不代表补造历史收货',
  `plan_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批准转换计划摘要',
  `before_snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '完整转换前快照摘要',
  `after_snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '完整转换后快照摘要',
  `bridge_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'census当前身份桥摘要，保留原聚合流水',
  `journal_json` longtext NOT NULL COMMENT '不可变完整转换日志，不含journal_hash自身；回退不覆盖',
  `journal_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原完整日志原始UTF8字节SHA256，回退保持原值',
  `revert_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '实际回退日志摘要，未回退保持NULL',
  `status` varchar(24) NOT NULL DEFAULT 'PREPARED' COMMENT '同事务PREPARED到APPLIED，安全回退后REVERTED',
  `version` bigint(20) NOT NULL DEFAULT 1 COMMENT '审计状态CAS，回退后为2',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`),
  KEY `idx_stock_conversion_material` (`tenant_id`,`material_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='当前库存到正式SN的零损益转换审计，仅追加工件并CAS状态';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND COLUMN_NAME IN ('run_id','tenant_id','material_id','target_database','target_environment','backup_reference','cutover_at','plan_hash','before_snapshot_hash','after_snapshot_hash','bridge_hash','journal_json','journal_hash','revert_hash','status','version','created_at','updated_at')) <> 18 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_stock_conversion_run字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND ((
         COLUMN_NAME='run_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='target_database'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='target_environment'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='backup_reference'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=255
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='cutover_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='plan_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='before_snapshot_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='after_snapshot_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='bridge_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='journal_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='journal_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='revert_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='YES'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=24
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='PREPARED')
         OR (
         COLUMN_NAME='version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='created_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp')
         OR (
         COLUMN_NAME='updated_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 18 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_stock_conversion_run字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND INDEX_NAME='PRIMARY'),'') <> 'run_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND INDEX_NAME='idx_stock_conversion_material'),'') <> 'tenant_id,material_id,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND INDEX_NAME='idx_stock_conversion_material'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_trace_stock_conversion_run' AND INDEX_NAME='idx_stock_conversion_material' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_trace_stock_conversion_run索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_work_order_tracking_policy` (
  `snapshot_id` bigint(20) NOT NULL COMMENT '不可变工单策略记录ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `erp_wo_id` bigint(20) NOT NULL COMMENT '真实ERP工单来源ID',
  `material_id` bigint(20) NOT NULL COMMENT '根物料或原组件物料ID',
  `snapshot_version` int(11) NOT NULL COMMENT '策略证据格式版本，不推定历史版本',
  `policy_version` bigint(20) NOT NULL COMMENT '原追踪策略版本',
  `policy_json` longtext NOT NULL COMMENT '原追踪策略冻结JSON',
  `policy_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原策略JSON原始UTF8字节摘要',
  `source_json` longtext NOT NULL COMMENT '原工单及完整物料来源范围JSON',
  `source_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原工单来源JSON原始字节摘要',
  `snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同工单整组策略证据摘要',
  `provenance` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'ERP_RELEASE或有明确原证据的SOURCE_POLICY_ADOPTION',
  `evidence_ref` varchar(255) NOT NULL COMMENT '原release或经批准历史策略凭据引用',
  `frozen_at` datetime(3) NOT NULL COMMENT '原策略证据冻结时点',
  `effective_from` datetime(3) NOT NULL COMMENT '明确原生效切点，不以当前时间补造历史',
  `actor_id` bigint(20) DEFAULT NULL COMMENT '明确采纳操作人；原自动release可空',
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uk_wo_tracking_policy` (`tenant_id`,`erp_wo_id`,`material_id`),
  KEY `idx_wo_tracking_policy_material` (`tenant_id`,`material_id`,`erp_wo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='不可变工单原追踪策略证据，旧工单缺失保持UNKNOWN';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND COLUMN_NAME IN ('snapshot_id','tenant_id','erp_wo_id','material_id','snapshot_version','policy_version','policy_json','policy_hash','source_json','source_hash','snapshot_hash','provenance','evidence_ref','frozen_at','effective_from','actor_id')) <> 16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_work_order_tracking_policy字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND ((
         COLUMN_NAME='snapshot_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='erp_wo_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='material_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='snapshot_version'
         AND DATA_TYPE='int'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='policy_version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='policy_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='policy_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='snapshot_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='provenance'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=32
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='evidence_ref'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=255
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='frozen_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='effective_from'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='actor_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL))) <> 16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_work_order_tracking_policy字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='PRIMARY'),'') <> 'snapshot_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='uk_wo_tracking_policy'),'') <> 'tenant_id,erp_wo_id,material_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='uk_wo_tracking_policy'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='uk_wo_tracking_policy' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='idx_wo_tracking_policy_material'),'') <> 'tenant_id,material_id,erp_wo_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='idx_wo_tracking_policy_material'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_tracking_policy' AND INDEX_NAME='idx_wo_tracking_policy_material' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_work_order_tracking_policy索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `wm_work_order_policy_adoption` (
  `adoption_id` bigint(20) NOT NULL COMMENT '已独立审批的采纳申请ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `erp_wo_id` bigint(20) NOT NULL COMMENT '真实在途ERP工单ID',
  `expected_wo_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已核实WO/BOM/需求来源预览摘要',
  `source_json` longtext NOT NULL COMMENT '完整原始策略归档集合，不使用当前主数据',
  `source_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始策略归档字节摘要',
  `approval_json` longtext NOT NULL COMMENT '独立审核人对原申请意图的批准工件',
  `approval_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原批准工件字节摘要',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工单来源、原策略、双方操作人及审批时间窗的长度分帧摘要',
  `proposed_by` bigint(20) NOT NULL COMMENT '明确申请人，与批准人不同',
  `approved_by` bigint(20) NOT NULL COMMENT '独立批准人',
  `approved_at` datetime(3) NOT NULL COMMENT '原批准时间',
  `expires_at` datetime(3) NOT NULL COMMENT '批准有效期截止，过期拒绝',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '只有显式独立批准后才可APPROVED，实际服务CAS到APPLIED',
  `version` bigint(20) NOT NULL DEFAULT 1 COMMENT '批准/执行版本CAS',
  `applied_at` datetime(3) DEFAULT NULL COMMENT '实际执行后未来生效时间，不回填历史生效',
  `snapshot_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '实际服务冻结后的整组策略摘要，未执行保持NULL',
  PRIMARY KEY (`adoption_id`),
  UNIQUE KEY `uk_wo_policy_adoption_command` (`tenant_id`,`command_id`),
  KEY `idx_wo_policy_adoption_state` (`tenant_id`,`erp_wo_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='原工单策略独立审批采纳账，实际生效由ERP业务事务执行';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND COLUMN_NAME IN ('adoption_id','tenant_id','command_id','erp_wo_id','expected_wo_hash','source_json','source_hash','approval_json','approval_hash','request_hash','proposed_by','approved_by','approved_at','expires_at','status','version','applied_at','snapshot_hash')) <> 18 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_work_order_policy_adoption字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND ((
         COLUMN_NAME='adoption_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='tenant_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=20
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='command_id'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=128
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='erp_wo_id'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='expected_wo_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='source_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='approval_json'
         AND DATA_TYPE='longtext'
         AND IS_NULLABLE='NO'
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='approval_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='request_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='proposed_by'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='approved_by'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='approved_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='expires_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='status'
         AND DATA_TYPE='varchar'
         AND IS_NULLABLE='NO'
         AND CHARACTER_MAXIMUM_LENGTH=16
         AND CHARACTER_SET_NAME='utf8mb4'
         AND COLLATION_NAME='utf8mb4_unicode_ci'
         AND COLUMN_DEFAULT='PENDING_REVIEW')
         OR (
         COLUMN_NAME='version'
         AND DATA_TYPE='bigint'
         AND IS_NULLABLE='NO'
         AND COLUMN_DEFAULT='1')
         OR (
         COLUMN_NAME='applied_at'
         AND DATA_TYPE='datetime'
         AND IS_NULLABLE='YES'
         AND COLUMN_DEFAULT IS NULL)
         OR (
         COLUMN_NAME='snapshot_hash'
         AND DATA_TYPE='char'
         AND IS_NULLABLE='YES'
         AND CHARACTER_MAXIMUM_LENGTH=64
         AND CHARACTER_SET_NAME='ascii'
         AND COLLATION_NAME='ascii_bin'
         AND COLUMN_DEFAULT IS NULL))) <> 18 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_work_order_policy_adoption字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='PRIMARY'),'') <> 'adoption_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='uk_wo_policy_adoption_command'),'') <> 'tenant_id,command_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='uk_wo_policy_adoption_command'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='uk_wo_policy_adoption_command' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='idx_wo_policy_adoption_state'),'') <> 'tenant_id,erp_wo_id,status'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='idx_wo_policy_adoption_state'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_work_order_policy_adoption' AND INDEX_NAME='idx_wo_policy_adoption_state' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表wm_work_order_policy_adoption索引偏离契约';
  END IF;
END$$
DELIMITER ;
CALL `material_trace_20261018_erp`();
DROP PROCEDURE `material_trace_20261018_erp`;
