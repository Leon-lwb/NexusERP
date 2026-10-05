-- 仅 ERP 业务库 b4cba98b 对应基线；执行前核对库、备份、维护窗口及所有应用节点版本。
-- MySQL 5.7 DDL 隐式提交，不声称多语句原子；中途失败后可重放已成功的追加。
-- 不转数量类型、不补历史、不覆盖旧请求哈希；原无通知引用保持 NULL。
-- 主初始化脚本仅用于空库，不能在存量库重导。
DELIMITER $$
DROP PROCEDURE IF EXISTS `_nx_certified_arrival_refs`$$
CREATE PROCEDURE `_nx_certified_arrival_refs`()
BEGIN
  IF DATABASE() IS NULL OR (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('wm_arrival_notice','wm_arrival_notice_line','wm_material_receive','wm_material_receive_line','wm_certified_receive_command') AND ENGINE='InnoDB') <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='必须选择已核 ERP 基线且原表均为 InnoDB';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_arrival_notice_line' AND COLUMN_NAME='received_qty') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少既有通知累计列，请先按适用基线审查原增量';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_receive_line' AND COLUMN_NAME='notice_line_id') THEN
    ALTER TABLE `wm_material_receive_line` ADD COLUMN `notice_line_id` BIGINT(20) NULL DEFAULT NULL COMMENT '原认证通知引用，历史不回填';
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_receive_line' AND COLUMN_NAME='notice_line_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND COLUMN_TYPE NOT LIKE '%unsigned%') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='既有通知引用类型/可空性不符合要求，停止而非改写';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receive_command' AND COLUMN_NAME='notice_id') THEN
    ALTER TABLE `wm_certified_receive_command` ADD COLUMN `notice_id` BIGINT(20) NULL DEFAULT NULL COMMENT '原认证通知引用，历史不回填';
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receive_command' AND COLUMN_NAME='notice_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND COLUMN_TYPE NOT LIKE '%unsigned%') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='既有通知引用类型/可空性不符合要求，停止而非改写';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receive_command' AND COLUMN_NAME='notice_line_id') THEN
    ALTER TABLE `wm_certified_receive_command` ADD COLUMN `notice_line_id` BIGINT(20) NULL DEFAULT NULL COMMENT '原认证通知引用，历史不回填';
  ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receive_command' AND COLUMN_NAME='notice_line_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND COLUMN_TYPE NOT LIKE '%unsigned%') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='既有通知引用类型/可空性不符合要求，停止而非改写';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receive_command' AND INDEX_NAME='idx_wm_certified_arrival_ref') THEN
    ALTER TABLE `wm_certified_receive_command` ADD KEY `idx_wm_certified_arrival_ref` (`tenant_id`,`notice_id`,`notice_line_id`);
  ELSEIF (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_certified_receive_command' AND INDEX_NAME='idx_wm_certified_arrival_ref') <> 'tenant_id,notice_id,notice_line_id' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='既有通知引用索引定义不一致';
  END IF;
END$$
CALL `_nx_certified_arrival_refs`()$$
DROP PROCEDURE `_nx_certified_arrival_refs`$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS `wm_certified_arrival_command` (
  `tenant_id` varchar(20) NOT NULL COMMENT '原请求租户',
  `request_key` varchar(128) NOT NULL COMMENT '原请求永久键',
  `request_hash` char(64) NOT NULL COMMENT '规范化原载荷摘要',
  `actor_id` bigint(20) NOT NULL COMMENT '服务端原创建操作人',
  `order_id` bigint(20) NOT NULL COMMENT '真实批准采购订单',
  `purchase_line_id` bigint(20) NOT NULL COMMENT '原采购订单行',
  `order_version` int NOT NULL COMMENT '创建时批准版本',
  `notice_id` bigint(20) NOT NULL COMMENT '原通知业务头',
  `notice_line_id` bigint(20) NOT NULL COMMENT '原通知业务行',
  `notice_code` varchar(64) NOT NULL COMMENT '正常编号规则结果',
  `vendor_id` bigint(20) NOT NULL COMMENT '原采购供应商',
  `material_id` bigint(20) NOT NULL COMMENT '原正常物料',
  `base_uom_id` varchar(64) NOT NULL COMMENT '原基本单位',
  `quantity` decimal(18,4) NOT NULL COMMENT '原通知声明量，不新增采购额度',
  `arrival_date` date NOT NULL COMMENT '通知计划到货日',
  `notice_name` varchar(255) NOT NULL COMMENT '原通知名称',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '原说明',
  `create_time` datetime(3) NOT NULL COMMENT '原创建提交时点',
  PRIMARY KEY (`tenant_id`,`request_key`),
  UNIQUE KEY `uk_wm_certified_arrival` (`tenant_id`,`notice_id`),
  UNIQUE KEY `uk_wm_certified_arrival_line` (`tenant_id`,`notice_line_id`),
  KEY `idx_wm_certified_arrival_source` (`tenant_id`,`order_id`,`purchase_line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证订单独立到货原生创建命令，不补历史';
