-- 2026-10-18 物料身份与追溯增量；仅适用已核实的MES业务数据源。
-- 本文件只提供待审SQL；离线工具不执行SQL，未接触既有业务库。
-- 操作者须先显式设置 @material_trace_expected_schema 与 @material_trace_backup_reference。
-- 先停写并核对备份及基线；禁止 --force。DDL隐式提交，失败后按元数据检查安全续跑。
-- 禁止以含DROP TABLE的主安装SQL迁移存量库；本文件不改写历史业务行。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS `material_trace_20261018_mes`;
DELIMITER $$
CREATE PROCEDURE `material_trace_20261018_mes`()
BEGIN
  IF DATABASE() IS NULL OR @material_trace_expected_schema IS NULL
     OR BINARY DATABASE() <> BINARY @material_trace_expected_schema
     OR @material_trace_backup_reference IS NULL OR CHAR_LENGTH(TRIM(@material_trace_backup_reference)) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '必须显式核实MES目标库及备份引用';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_wo_consumption' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表lt_wo_consumption不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表pro_sn不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_template' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表mes_label_template不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表mes_label_print_log不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND ENGINE='InnoDB') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表lt_lot不存在或不是InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_wo_consumption' AND COLUMN_NAME='source_allocation_id') = 0 THEN
    ALTER TABLE `lt_wo_consumption` ADD COLUMN `source_allocation_id` bigint(20) DEFAULT NULL COMMENT '实际消耗引用的原额度分配ID，旧记录未知保持NULL';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_wo_consumption' AND (COLUMN_NAME='source_allocation_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段lt_wo_consumption.source_allocation_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND COLUMN_NAME='identity_id') = 0 THEN
    ALTER TABLE `pro_sn` ADD COLUMN `identity_id` bigint(20) DEFAULT NULL COMMENT 'ERP正式身份wm_sn.sn_id，本地sn_id语义不变';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND (COLUMN_NAME='identity_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段pro_sn.identity_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND COLUMN_NAME='product_lot_id') = 0 THEN
    ALTER TABLE `pro_sn` ADD COLUMN `product_lot_id` bigint(20) DEFAULT NULL COMMENT 'MES成品批次lt_lot.lot_id';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND (COLUMN_NAME='product_lot_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段pro_sn.product_lot_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND COLUMN_NAME='identity_version') = 0 THEN
    ALTER TABLE `pro_sn` ADD COLUMN `identity_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '身份映射版本';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND (COLUMN_NAME='identity_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段pro_sn.identity_version类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND COLUMN_NAME='handoff_status') = 0 THEN
    ALTER TABLE `pro_sn` ADD COLUMN `handoff_status` varchar(24) NOT NULL DEFAULT 'NONE' COMMENT '交付状态，历史默认NONE';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND (COLUMN_NAME='handoff_status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT='NONE')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段pro_sn.handoff_status类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_template' AND COLUMN_NAME='template_revision') = 0 THEN
    ALTER TABLE `mes_label_template` ADD COLUMN `template_revision` bigint(20) NOT NULL DEFAULT 1 COMMENT '模板修订号，保存时递增，打印时冻结';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_template' AND (COLUMN_NAME='template_revision' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_template.template_revision类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='template_revision') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `template_revision` bigint(20) DEFAULT NULL COMMENT '打印时模板修订号，历史未知留空';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='template_revision' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.template_revision类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='original_print_log_id') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `original_print_log_id` bigint(20) DEFAULT NULL COMMENT '补打引用的原任务ID，历史不补造';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='original_print_log_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.original_print_log_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='template_snapshot') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `template_snapshot` longtext COLLATE utf8mb4_unicode_ci COMMENT '冻结模板布局及修订JSON，历史留空';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='template_snapshot' AND DATA_TYPE='longtext' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.template_snapshot类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='field_snapshot') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `field_snapshot` longtext COLLATE utf8mb4_unicode_ci COMMENT '冻结SN物料工单等字段JSON，历史留空';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='field_snapshot' AND DATA_TYPE='longtext' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.field_snapshot类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='rendered_payload') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `rendered_payload` longtext COLLATE utf8mb4_unicode_ci COMMENT '冻结完整打印指令，补打不读取新模板';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='rendered_payload' AND DATA_TYPE='longtext' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.rendered_payload类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='content_hash') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '原模板快照UTF8字节SHA256，历史不补造';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='content_hash' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.content_hash类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='payload_hash') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '实际编码发送字节SHA256，历史不补造';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='payload_hash' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.payload_hash类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='payload_charset') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `payload_charset` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '实际发送字符集名称';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='payload_charset' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=32 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.payload_charset类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='snapshot_mode') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `snapshot_mode` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '冻结或历史兼容模式，旧记录保持空值';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='snapshot_mode' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=32 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.snapshot_mode类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='reprint_reason') = 0 THEN
    ALTER TABLE `mes_label_print_log` ADD COLUMN `reprint_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工补打原因，历史缺失不补造';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND (COLUMN_NAME='reprint_reason' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=500 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段mes_label_print_log.reprint_reason类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND COLUMN_NAME='erp_batch_id') = 0 THEN
    ALTER TABLE `lt_lot` ADD COLUMN `erp_batch_id` bigint(20) DEFAULT NULL COMMENT '有明确凭据的ERP批次身份，不按同码猜测';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND (COLUMN_NAME='erp_batch_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL)) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段lt_lot.erp_batch_id类型偏离契约';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND COLUMN_NAME='allocation_version') = 0 THEN
    ALTER TABLE `lt_lot` ADD COLUMN `allocation_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '批次分配版本';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND (COLUMN_NAME='allocation_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段lt_lot.allocation_version类型偏离契约';
  END IF;
  -- 标签只扩展结构及新行默认值，不补造旧快照、不重写历史SUCCESS。
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='sn_code' AND DATA_TYPE='varchar') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '标签SN列基线必须为varchar';
  END IF;
  IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='sn_code') < 128 THEN
    ALTER TABLE `mes_label_print_log` MODIFY COLUMN `sn_code` varchar(128) DEFAULT NULL COMMENT '正式SN原码，最长128位';
  END IF;
  IF (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='retry_count') = 'NO' THEN
    ALTER TABLE `mes_label_print_log` MODIFY COLUMN `retry_count` int(11) DEFAULT NULL COMMENT '同SN模板既有发送尝试数，未知保留空值';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='retry_count' AND COLUMN_DEFAULT IS NOT NULL) > 0 THEN
    ALTER TABLE `mes_label_print_log` ALTER COLUMN `retry_count` SET DEFAULT NULL;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='result' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH>=7) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '标签结果必须兼容SENT及历史SUCCESS，禁止重写旧值';
  END IF;
  -- 只有SUBCONTRACT来源允许空工单；服务必须核实来源，不依赖5.7不执行的CHECK。
  IF (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND COLUMN_NAME='mes_wo_id') = 'NO' THEN
    ALTER TABLE `pro_sn` MODIFY COLUMN `mes_wo_id` bigint(20) DEFAULT NULL COMMENT '工单ID，仅已核实SUBCONTRACT来源可空';
  END IF;
  IF EXISTS (SELECT 1 FROM pro_sn WHERE identity_id IS NOT NULL GROUP BY tenant_id,identity_id HAVING COUNT(*) > 1) THEN
    SELECT tenant_id,identity_id,COUNT(*) AS conflicting_rows FROM pro_sn WHERE identity_id IS NOT NULL GROUP BY tenant_id,identity_id HAVING COUNT(*) > 1;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MES正式身份重复，须先凭据审计';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_wo_consumption' AND INDEX_NAME='idx_wo_consumption_source_allocation') = 0 THEN
    ALTER TABLE `lt_wo_consumption` ADD KEY `idx_wo_consumption_source_allocation` (`tenant_id`,`source_allocation_id`);
  ELSE
    IF (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_wo_consumption' AND INDEX_NAME='idx_wo_consumption_source_allocation') <> 'tenant_id,source_allocation_id'
       OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_wo_consumption' AND INDEX_NAME='idx_wo_consumption_source_allocation') <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '索引idx_wo_consumption_source_allocation定义偏离契约';
    END IF;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND INDEX_NAME='uk_pro_sn_identity') = 0 THEN
    ALTER TABLE `pro_sn` ADD UNIQUE KEY `uk_pro_sn_identity` (`tenant_id`,`identity_id`);
  ELSE
    IF (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND INDEX_NAME='uk_pro_sn_identity') <> 'tenant_id,identity_id'
       OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND INDEX_NAME='uk_pro_sn_identity') <> 0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '索引uk_pro_sn_identity定义偏离契约';
    END IF;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND INDEX_NAME='idx_lot_erp_batch') = 0 THEN
    ALTER TABLE `lt_lot` ADD KEY `idx_lot_erp_batch` (`tenant_id`,`erp_batch_id`);
  ELSE
    IF (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND INDEX_NAME='idx_lot_erp_batch') <> 'tenant_id,erp_batch_id'
       OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot' AND INDEX_NAME='idx_lot_erp_batch') <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '索引idx_lot_erp_batch定义偏离契约';
    END IF;
  END IF;

CREATE TABLE IF NOT EXISTS `pro_completion_manifest` (
  `manifest_id` bigint(20) NOT NULL COMMENT '最终交付清单ID',
  `revision` int(11) NOT NULL DEFAULT 1 COMMENT '冻结修订号',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `erp_wo_id` bigint(20) DEFAULT NULL COMMENT 'ERP工单ID',
  `material_id` bigint(20) NOT NULL COMMENT '成品物料ID',
  `line_count` bigint(20) NOT NULL COMMENT '冻结清单行数',
  `good_qty` decimal(18,4) NOT NULL COMMENT '合格基本单位数量',
  `scrap_qty` decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '报废基本单位数量',
  `content_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结清单规范内容SHA256',
  `material_line_count` bigint(20) DEFAULT NULL COMMENT '实际用料分配行数，旧清单未知保持NULL',
  `material_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '实际用料分配规范内容SHA256，旧清单不回填',
  `status` varchar(24) NOT NULL DEFAULT 'FROZEN' COMMENT '冻结交付状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`manifest_id`),
  UNIQUE KEY `uk_completion_manifest_revision` (`tenant_id`,`mes_wo_id`,`revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='最终工单一次交付的不可变修订清单';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND COLUMN_NAME='material_line_count') = 0 THEN
    ALTER TABLE `pro_completion_manifest` ADD COLUMN `material_line_count` bigint(20) DEFAULT NULL COMMENT '实际用料分配行数，旧清单未知保持NULL';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND COLUMN_NAME='material_hash') = 0 THEN
    ALTER TABLE `pro_completion_manifest` ADD COLUMN `material_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '实际用料分配规范内容SHA256，旧清单不回填';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND COLUMN_NAME IN ('manifest_id','revision','tenant_id','mes_wo_id','erp_wo_id','material_id','line_count','good_qty','scrap_qty','content_hash','material_line_count','material_hash','status','created_at')) <> 14 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_manifest字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND ((COLUMN_NAME='manifest_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='revision' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='mes_wo_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='erp_wo_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='material_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='line_count' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='good_qty' AND DATA_TYPE='decimal' AND IS_NULLABLE='NO' AND NUMERIC_PRECISION=18 AND NUMERIC_SCALE=4 AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='scrap_qty' AND DATA_TYPE='decimal' AND IS_NULLABLE='NO' AND NUMERIC_PRECISION=18 AND NUMERIC_SCALE=4 AND COLUMN_DEFAULT='0.0000') OR (COLUMN_NAME='content_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='material_line_count' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='material_hash' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT='FROZEN') OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 14 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_manifest字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND INDEX_NAME='PRIMARY'),'') <> 'manifest_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND INDEX_NAME='uk_completion_manifest_revision'),'') <> 'tenant_id,mes_wo_id,revision'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND INDEX_NAME='uk_completion_manifest_revision'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest' AND INDEX_NAME='uk_completion_manifest_revision' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_manifest索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `pro_completion_manifest_line` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `manifest_id` bigint(20) NOT NULL COMMENT '冻结清单ID',
  `revision` int(11) NOT NULL COMMENT '冻结修订号',
  `line_no` bigint(20) NOT NULL COMMENT '稳定清单行号',
  `identity_id` bigint(20) DEFAULT NULL COMMENT 'ERP正式实物身份，非SN行可空',
  `local_sn_id` bigint(20) DEFAULT NULL COMMENT 'MES本地SN主键',
  `sn_code` varchar(128) DEFAULT NULL COMMENT '冻结正式SN原码',
  `batch_id` bigint(20) DEFAULT NULL COMMENT 'ERP批次ID',
  `local_lot_id` bigint(20) DEFAULT NULL COMMENT 'MES本地批次ID',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `quantity` decimal(18,4) NOT NULL COMMENT '基本单位数量，SN行一件',
  `result` varchar(16) NOT NULL COMMENT '质量结果GOOD或SCRAP',
  `source_version` bigint(20) NOT NULL COMMENT '冻结时来源身份或批次版本',
  PRIMARY KEY (`tenant_id`,`manifest_id`,`revision`,`line_no`),
  UNIQUE KEY `uk_manifest_identity` (`tenant_id`,`manifest_id`,`revision`,`identity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='公共CompletionManifestLine逐字段映射';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND COLUMN_NAME IN ('tenant_id','manifest_id','revision','line_no','identity_id','local_sn_id','sn_code','batch_id','local_lot_id','material_id','quantity','result','source_version')) <> 13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_manifest_line字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND ((COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='manifest_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='revision' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='line_no' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='identity_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='local_sn_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='sn_code' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='batch_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='local_lot_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='material_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='quantity' AND DATA_TYPE='decimal' AND IS_NULLABLE='NO' AND NUMERIC_PRECISION=18 AND NUMERIC_SCALE=4 AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='result' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL))) <> 13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_manifest_line字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND INDEX_NAME='PRIMARY'),'') <> 'tenant_id,manifest_id,revision,line_no'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND INDEX_NAME='uk_manifest_identity'),'') <> 'tenant_id,manifest_id,revision,identity_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND INDEX_NAME='uk_manifest_identity'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_manifest_line' AND INDEX_NAME='uk_manifest_identity' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_manifest_line索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `lt_sn_consumption` (
  `entry_id` bigint(20) NOT NULL COMMENT '组件消耗账分录ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `line_no` bigint(20) NOT NULL COMMENT '命令内稳定行号',
  `identity_id` bigint(20) NOT NULL COMMENT '成品正式实物身份',
  `local_sn_id` bigint(20) NOT NULL COMMENT 'MES本地成品SN主键',
  `component_material_id` bigint(20) NOT NULL COMMENT '组件物料ID',
  `component_lot_id` bigint(20) NOT NULL COMMENT '组件MES批次ID',
  `quantity` decimal(18,4) NOT NULL COMMENT '正数绝对数量，方向由分录类型确定',
  `wo_consumption_id` bigint(20) NOT NULL COMMENT '原工单消耗记录lt_wo_consumption主键',
  `process_id` bigint(20) DEFAULT NULL COMMENT '工序ID',
  `operation_ref` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '工位操作或离线操作凭据',
  `entry_type` varchar(16) NOT NULL COMMENT 'CONSUME消耗或REVERSE冲正，不覆盖原分录',
  `original_entry_id` bigint(20) DEFAULT NULL COMMENT '冲正所引用的原CONSUME分录',
  `policy_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '消耗时混批策略版本',
  `created_by` bigint(20) NOT NULL COMMENT '操作人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`entry_id`),
  UNIQUE KEY `uk_sn_consumption_command_line` (`tenant_id`,`command_id`,`line_no`),
  KEY `idx_sn_consumption_identity` (`tenant_id`,`identity_id`,`component_material_id`),
  KEY `idx_sn_consumption_source` (`tenant_id`,`wo_consumption_id`),
  KEY `idx_sn_consumption_reverse` (`tenant_id`,`original_entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='实物组件消耗不可变流水账，冲正必须引用原分录';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND COLUMN_NAME IN ('entry_id','tenant_id','command_id','line_no','identity_id','local_sn_id','component_material_id','component_lot_id','quantity','wo_consumption_id','process_id','operation_ref','entry_type','original_entry_id','policy_version','created_by','created_at')) <> 17 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_sn_consumption字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND ((COLUMN_NAME='entry_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='command_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='line_no' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='identity_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='local_sn_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='component_material_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='component_lot_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='quantity' AND DATA_TYPE='decimal' AND IS_NULLABLE='NO' AND NUMERIC_PRECISION=18 AND NUMERIC_SCALE=4 AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='wo_consumption_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='process_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='operation_ref' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='entry_type' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='original_entry_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='policy_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='created_by' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 17 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_sn_consumption字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='PRIMARY'),'') <> 'entry_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='uk_sn_consumption_command_line'),'') <> 'tenant_id,command_id,line_no'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='uk_sn_consumption_command_line'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='uk_sn_consumption_command_line' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_identity'),'') <> 'tenant_id,identity_id,component_material_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_identity'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_identity' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_source'),'') <> 'tenant_id,wo_consumption_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_source'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_source' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_reverse'),'') <> 'tenant_id,original_entry_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_reverse'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_consumption' AND INDEX_NAME='idx_sn_consumption_reverse' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_sn_consumption索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `lt_sn_material_guard` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `identity_id` bigint(20) NOT NULL COMMENT '成品正式身份',
  `component_material_id` bigint(20) NOT NULL COMMENT '组件物料ID',
  `selected_lot_id` bigint(20) DEFAULT NULL COMMENT '禁止混批时首次选定批次',
  `allow_mixed_lot` tinyint(1) NOT NULL DEFAULT 1 COMMENT '冻结混批策略，默认允许',
  `policy_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '策略版本',
  `version` bigint(20) NOT NULL DEFAULT 1 COMMENT '并发互斥与乐观锁版本',
  PRIMARY KEY (`tenant_id`,`identity_id`,`component_material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='同实物同组件物料的混批并发守卫';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_material_guard' AND COLUMN_NAME IN ('tenant_id','identity_id','component_material_id','selected_lot_id','allow_mixed_lot','policy_version','version')) <> 7 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_sn_material_guard字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_material_guard' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_material_guard' AND ((COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='identity_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='component_material_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='selected_lot_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='allow_mixed_lot' AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='policy_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1'))) <> 7 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_sn_material_guard字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_material_guard' AND INDEX_NAME='PRIMARY'),'') <> 'tenant_id,identity_id,component_material_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_material_guard' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_sn_material_guard' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_sn_material_guard索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `lt_lot_allocation` (
  `allocation_id` bigint(20) NOT NULL COMMENT '批次分配ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `line_no` bigint(20) NOT NULL COMMENT '命令内分配行号',
  `lot_id` bigint(20) NOT NULL COMMENT 'MES本地批次ID',
  `erp_batch_id` bigint(20) DEFAULT NULL COMMENT '有证据的ERP批次ID，SN-only来源允许空值',
  `material_id` bigint(20) NOT NULL COMMENT '物料ID',
  `quantity` decimal(18,4) NOT NULL COMMENT '分配基本单位数量',
  `source_type` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '分配凭据来源类型',
  `source_id` bigint(20) NOT NULL COMMENT '分配凭据ID',
  `source_line_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '凭据行ID',
  `allocation_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '批次分配版本',
  `evidence_json` longtext NOT NULL COMMENT '原凭据及分配依据JSON，不能仅用同码匹配',
  `evidence_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结证据摘要',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`allocation_id`),
  UNIQUE KEY `uk_lot_allocation_command_line` (`tenant_id`,`command_id`,`line_no`),
  KEY `idx_lot_allocation_mapping` (`tenant_id`,`lot_id`,`erp_batch_id`,`allocation_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='MES批次到ERP批次的带证据数量分配';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND COLUMN_NAME='erp_batch_id' AND IS_NULLABLE='NO') = 1 THEN
    IF (SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND COLUMN_NAME='erp_batch_id') <> 'bigint'
       OR (SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND COLUMN_NAME='erp_batch_id') LIKE '%unsigned%'
       OR (SELECT EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND COLUMN_NAME='erp_batch_id') <> ''
       OR (SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND COLUMN_NAME='erp_batch_id') IS NOT NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_lot_allocation字段erp_batch_id旧定义不兼容，禁止隐式转换';
    END IF;
    ALTER TABLE `lt_lot_allocation` MODIFY COLUMN `erp_batch_id` bigint(20) DEFAULT NULL COMMENT '有证据的ERP批次ID，SN-only来源允许空值';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND COLUMN_NAME IN ('allocation_id','tenant_id','command_id','line_no','lot_id','erp_batch_id','material_id','quantity','source_type','source_id','source_line_id','allocation_version','evidence_json','evidence_hash','created_at')) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_lot_allocation字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND ((COLUMN_NAME='allocation_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='command_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='line_no' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='lot_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='erp_batch_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='material_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='quantity' AND DATA_TYPE='decimal' AND IS_NULLABLE='NO' AND NUMERIC_PRECISION=18 AND NUMERIC_SCALE=4 AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_type' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=32 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_line_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0') OR (COLUMN_NAME='allocation_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='evidence_json' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='evidence_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_lot_allocation字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='PRIMARY'),'') <> 'allocation_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='uk_lot_allocation_command_line'),'') <> 'tenant_id,command_id,line_no'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='uk_lot_allocation_command_line'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='uk_lot_allocation_command_line' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='idx_lot_allocation_mapping'),'') <> 'tenant_id,lot_id,erp_batch_id,allocation_version'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='idx_lot_allocation_mapping'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lt_lot_allocation' AND INDEX_NAME='idx_lot_allocation_mapping' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表lt_lot_allocation索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `mes_trace_outbox` (
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
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND COLUMN_NAME IN ('event_id','tenant_id','source_module','aggregate_type','aggregate_id','aggregate_version','event_type','schema_version','payload','content_hash','status','attempts','next_attempt_at','lease_owner','lease_until','last_error','result_payload','created_at','updated_at','applied_at')) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_outbox字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND ((COLUMN_NAME='event_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_module' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='aggregate_type' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='aggregate_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='aggregate_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='event_type' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='schema_version' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='payload' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='content_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT='NEW') OR (COLUMN_NAME='attempts' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0') OR (COLUMN_NAME='next_attempt_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='lease_owner' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='lease_until' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='last_error' AND DATA_TYPE='text' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='result_payload' AND DATA_TYPE='longtext' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp') OR (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp') OR (COLUMN_NAME='applied_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL))) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_outbox字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='PRIMARY'),'') <> 'event_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='uk_trace_event_stream_version'),'') <> 'tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='uk_trace_event_stream_version'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='uk_trace_event_stream_version' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='idx_trace_event_retry'),'') <> 'status,next_attempt_at,lease_until'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='idx_trace_event_retry'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='idx_trace_event_retry' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='idx_trace_event_tenant'),'') <> 'tenant_id,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='idx_trace_event_tenant'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_outbox' AND INDEX_NAME='idx_trace_event_tenant' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_outbox索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `mes_trace_inbox` (
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
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND COLUMN_NAME IN ('event_id','tenant_id','source_module','aggregate_type','aggregate_id','aggregate_version','event_type','schema_version','payload','content_hash','status','attempts','next_attempt_at','lease_owner','lease_until','last_error','result_payload','created_at','updated_at','applied_at')) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_inbox字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND ((COLUMN_NAME='event_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_module' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='aggregate_type' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='aggregate_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='aggregate_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='event_type' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='schema_version' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='payload' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='content_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT='NEW') OR (COLUMN_NAME='attempts' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0') OR (COLUMN_NAME='next_attempt_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='lease_owner' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='lease_until' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='last_error' AND DATA_TYPE='text' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='result_payload' AND DATA_TYPE='longtext' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp') OR (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp') OR (COLUMN_NAME='applied_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL))) <> 20 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_inbox字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='PRIMARY'),'') <> 'event_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='uk_trace_event_stream_version'),'') <> 'tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='uk_trace_event_stream_version'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='uk_trace_event_stream_version' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='idx_trace_event_retry'),'') <> 'status,next_attempt_at,lease_until'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='idx_trace_event_retry'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='idx_trace_event_retry' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='idx_trace_event_tenant'),'') <> 'tenant_id,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='idx_trace_event_tenant'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_inbox' AND INDEX_NAME='idx_trace_event_tenant' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_inbox索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `mes_trace_repair_run` (
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
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_run' AND COLUMN_NAME IN ('run_id','target_database','target_environment','backup_reference','plan_hash','before_snapshot_hash','after_snapshot_hash','journal_hash','status','version','created_at','updated_at')) <> 12 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_repair_run字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_run' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_run' AND ((COLUMN_NAME='run_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='target_database' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='target_environment' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='backup_reference' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=255 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='plan_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='before_snapshot_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='after_snapshot_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='journal_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT='PLANNED') OR (COLUMN_NAME='version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp') OR (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 12 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_repair_run字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_run' AND INDEX_NAME='PRIMARY'),'') <> 'run_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_run' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_run' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_repair_run索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `mes_trace_repair_item` (
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
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND COLUMN_NAME IN ('run_id','item_id','tenant_id','target_table','target_key','before_json','after_json','evidence_json','before_hash','after_hash','evidence_hash','status','version','created_at','updated_at')) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_repair_item字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND ((COLUMN_NAME='run_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='item_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='target_table' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='target_key' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='before_json' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='after_json' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='evidence_json' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='before_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='after_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='evidence_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT='PLANNED') OR (COLUMN_NAME='version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp') OR (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_repair_item字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND INDEX_NAME='PRIMARY'),'') <> 'run_id,item_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND INDEX_NAME='idx_trace_repair_target'),'') <> 'tenant_id,target_table'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND INDEX_NAME='idx_trace_repair_target'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_trace_repair_item' AND INDEX_NAME='idx_trace_repair_target' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_trace_repair_item索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `mes_label_print_task` (
  `task_id` bigint(20) NOT NULL COMMENT '打印任务ID，同时作为本次终态打印日志ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `task_key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户内稳定任务键，自动任务固定SN及模板版本',
  `request_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交业务意图摘要，命令不得更换原任务或补打原因',
  `sn_id` bigint(20) DEFAULT NULL COMMENT 'MES本地SN，测试任务可空',
  `template_id` bigint(20) NOT NULL COMMENT '请求打印模板ID',
  `template_revision` bigint(20) DEFAULT NULL COMMENT '冻结修订，历史未知保持空值',
  `print_source` varchar(16) NOT NULL COMMENT 'AUTO、TEST或REPRINT',
  `original_print_log_id` bigint(20) DEFAULT NULL COMMENT '补打引用原任务或旧日志，保留原审计',
  `reprint_reason` varchar(500) DEFAULT NULL COMMENT '人工补打原因，不补造历史原因',
  `frozen_record` longtext NOT NULL COMMENT '不可变完整打印快照JSON，包含布局字段载荷及来源',
  `record_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '完整冻结记录UTF8字节SHA256',
  `status` varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、SENDING、SENT、FAIL、UNKNOWN、LEGACY_SENT',
  `attempt_count` int(11) NOT NULL DEFAULT 0 COMMENT '实际发送领取次数，只允许0转1',
  `lease_token` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '一次领取令牌，禁止发送中任务重新领取',
  `lease_until` datetime DEFAULT NULL COMMENT '数据库时钟计算的发送租约，过期只能转UNKNOWN',
  `sending_started_at` datetime DEFAULT NULL COMMENT '领取发送时刻',
  `completed_at` datetime DEFAULT NULL COMMENT '终态记录时刻，不代表实际出纸',
  `last_error` varchar(500) DEFAULT NULL COMMENT '脱敏错误或待人工核验原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_label_task_key` (`tenant_id`,`task_key`),
  KEY `idx_label_task_pending` (`tenant_id`,`status`,`created_at`),
  KEY `idx_label_task_lease` (`tenant_id`,`status`,`lease_until`),
  KEY `idx_label_task_sn` (`tenant_id`,`sn_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='MES事务内冻结的持久化打印任务，发送不等于出纸确认';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND COLUMN_NAME IN ('task_id','tenant_id','task_key','request_fingerprint','sn_id','template_id','template_revision','print_source','original_print_log_id','reprint_reason','frozen_record','record_hash','status','attempt_count','lease_token','lease_until','sending_started_at','completed_at','last_error','created_at','updated_at')) <> 21 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_label_print_task字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND ((COLUMN_NAME='task_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='task_key' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='request_fingerprint' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='sn_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='template_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='template_revision' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='print_source' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='original_print_log_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='reprint_reason' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=500 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='frozen_record' AND DATA_TYPE='longtext' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='record_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=24 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT='PENDING') OR (COLUMN_NAME='attempt_count' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0') OR (COLUMN_NAME='lease_token' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='lease_until' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='sending_started_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='completed_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='last_error' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=500 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp') OR (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 21 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_label_print_task字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='PRIMARY'),'') <> 'task_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='uk_label_task_key'),'') <> 'tenant_id,task_key'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='uk_label_task_key'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='uk_label_task_key' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_pending'),'') <> 'tenant_id,status,created_at'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_pending'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_pending' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_lease'),'') <> 'tenant_id,status,lease_until'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_lease'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_lease' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_sn'),'') <> 'tenant_id,sn_id,template_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_sn'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_task' AND INDEX_NAME='idx_label_task_sn' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表mes_label_print_task索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `pro_completion_material_allocation` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `manifest_id` bigint(20) NOT NULL COMMENT '冻结完工清单ID',
  `revision` int(11) NOT NULL COMMENT '冻结清单修订',
  `allocation_id` bigint(20) NOT NULL COMMENT '原实际消耗lt_wo_consumption.consumption_id，不是额度分配ID',
  `material_id` bigint(20) NOT NULL COMMENT '实际组件物料ID',
  `reservation_id` bigint(20) DEFAULT NULL COMMENT 'ERP实际库存预留头',
  `reservation_line_id` bigint(20) DEFAULT NULL COMMENT 'ERP实际库存预留行',
  `posting_group_id` bigint(20) DEFAULT NULL COMMENT '已发料ERP原过账证明组',
  `identity_id` bigint(20) DEFAULT NULL COMMENT 'ERP正式组件身份',
  `source_stock_id` bigint(20) DEFAULT NULL COMMENT 'ERP实际来源库存ID',
  `erp_batch_id` bigint(20) DEFAULT NULL COMMENT 'ERP批次ID，SN-only来源允许空值',
  `quantity` decimal(18,4) NOT NULL COMMENT '实际消耗基本单位数量',
  `source_version` bigint(20) NOT NULL COMMENT '冻结时实际分配来源版本',
  `evidence_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际来源证据SHA256',
  `source_event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ERP原授权或过账事件ID',
  PRIMARY KEY (`tenant_id`,`manifest_id`,`revision`,`allocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='最终完工清单的实际用料不可变快照';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_material_allocation' AND COLUMN_NAME IN ('tenant_id','manifest_id','revision','allocation_id','material_id','reservation_id','reservation_line_id','posting_group_id','identity_id','source_stock_id','erp_batch_id','quantity','source_version','evidence_hash','source_event_id')) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_material_allocation字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_material_allocation' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_material_allocation' AND ((COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='manifest_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='revision' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='allocation_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='material_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='reservation_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='reservation_line_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='posting_group_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='identity_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_stock_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='erp_batch_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='quantity' AND DATA_TYPE='decimal' AND IS_NULLABLE='NO' AND NUMERIC_PRECISION=18 AND NUMERIC_SCALE=4 AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='evidence_hash' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_event_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_material_allocation字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_material_allocation' AND INDEX_NAME='PRIMARY'),'') <> 'tenant_id,manifest_id,revision,allocation_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_material_allocation' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_completion_material_allocation' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_completion_material_allocation索引偏离契约';
  END IF;

CREATE TABLE IF NOT EXISTS `pro_product_lot_output` (
  `output_id` bigint(20) NOT NULL COMMENT '实际产品批次产出ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户ID，必须由认证上下文提供',
  `command_id` varchar(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等业务命令ID',
  `line_no` bigint(20) NOT NULL COMMENT '原命令内实际产出行号',
  `mes_wo_id` bigint(20) NOT NULL COMMENT '真实MES生产工单',
  `card_id` bigint(20) NOT NULL COMMENT '实际生产流转卡',
  `step_record_id` bigint(20) NOT NULL COMMENT '实际报工工序记录',
  `lot_id` bigint(20) NOT NULL COMMENT '实际MES产品批次',
  `source_allocation_id` bigint(20) NOT NULL COMMENT '原PRODUCTION_OUTPUT额度分配ID，ERP批次由该原凭据取得',
  `material_id` bigint(20) NOT NULL COMMENT '实际产品物料',
  `quantity` decimal(18,4) NOT NULL COMMENT '实际基本单位产出数量',
  `result` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '实际GOOD或SCRAP，不以授权额度推定合格产出',
  `source_version` bigint(20) NOT NULL DEFAULT 1 COMMENT '实际产出冻结版本',
  `created_by` bigint(20) NOT NULL COMMENT '认证上下文中的实际报工人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`output_id`),
  UNIQUE KEY `uk_product_output_command` (`tenant_id`,`command_id`,`line_no`),
  UNIQUE KEY `uk_product_output_lot` (`tenant_id`,`lot_id`),
  KEY `idx_product_output_work_order` (`tenant_id`,`mes_wo_id`,`card_id`,`step_record_id`,`lot_id`,`output_id`),
  KEY `idx_product_output_step` (`tenant_id`,`step_record_id`),
  KEY `idx_product_output_source` (`tenant_id`,`source_allocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='真实批次型产品产出，来源额度不是质量证明，不补造历史产出';
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND COLUMN_NAME IN ('output_id','tenant_id','command_id','line_no','mes_wo_id','card_id','step_record_id','lot_id','source_allocation_id','material_id','quantity','result','source_version','created_by','created_at')) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_product_lot_output字段不完整，禁止盲目续跑';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND ENGINE='InnoDB') <> 1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND ((COLUMN_NAME='output_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='command_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=128 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='line_no' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='mes_wo_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='card_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='step_record_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='lot_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_allocation_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='material_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='quantity' AND DATA_TYPE='decimal' AND IS_NULLABLE='NO' AND NUMERIC_PRECISION=18 AND NUMERIC_SCALE=4 AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='result' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_bin' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='source_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1') OR (COLUMN_NAME='created_by' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL) OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND LOWER(COLUMN_DEFAULT)='current_timestamp'))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_product_lot_output字段定义偏离契约';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='PRIMARY'),'') <> 'output_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='PRIMARY'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='PRIMARY' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='uk_product_output_command'),'') <> 'tenant_id,command_id,line_no'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='uk_product_output_command'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='uk_product_output_command' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='uk_product_output_lot'),'') <> 'tenant_id,lot_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='uk_product_output_lot'),-1) <> 0
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='uk_product_output_lot' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_work_order'),'') <> 'tenant_id,mes_wo_id,card_id,step_record_id,lot_id,output_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_work_order'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_work_order' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_step'),'') <> 'tenant_id,step_record_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_step'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_step' AND SUB_PART IS NOT NULL) <> 0
     OR COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_source'),'') <> 'tenant_id,source_allocation_id'
     OR COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_source'),-1) <> 1
     OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_product_lot_output' AND INDEX_NAME='idx_product_output_source' AND SUB_PART IS NOT NULL) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表pro_product_lot_output索引偏离契约';
  END IF;
END$$
DELIMITER ;
CALL `material_trace_20261018_mes`();
DROP PROCEDURE `material_trace_20261018_mes`;
