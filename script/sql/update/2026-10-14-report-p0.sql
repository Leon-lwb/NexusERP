-- 2026-10-14 Report P0, local master / ry-vue, MySQL 5.7 compatible
-- User authorized direct deletion of unused Jimu assets; no business-table migration.
-- DROP list is explicit; report metadata CREATE and menu insertion are replay-safe.
-- Existing rpt_* definitions are never dropped or reseeded.
-- Known IDs also repair role/package references after the old menu rows are gone.
-- Capture every legacy URL ID before deleting menus; business report menus are not selected.
DROP TEMPORARY TABLE IF EXISTS rpt_p0_legacy_menu_ids;
CREATE TEMPORARY TABLE rpt_p0_legacy_menu_ids (
  menu_id bigint NOT NULL PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO rpt_p0_legacy_menu_ids (menu_id) VALUES
  (3020),(3021),(3022),(2053846181246812161);
INSERT IGNORE INTO rpt_p0_legacy_menu_ids (menu_id)
SELECT m.menu_id FROM sys_menu m
WHERE (m.path LIKE '%/jmreport/%' OR m.path LIKE '%/jmubi/%' OR m.path LIKE '%/jimubi/%'
  OR m.path LIKE '%/drag/%' OR m.path LIKE '%/jmreport-designer/%');

-- Rebuild each CSV once from its surviving tokens, rather than updating it once per joined menu.
-- menu_ids is utf8mb4 varchar(3000); allow 12000 bytes and restore the session setting.
DROP TEMPORARY TABLE IF EXISTS rpt_p0_package_menus;
CREATE TEMPORARY TABLE rpt_p0_package_menus (
  package_id bigint NOT NULL PRIMARY KEY,
  menu_ids varchar(3000) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET @rpt_p0_group_concat_max_len = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = GREATEST(@@SESSION.group_concat_max_len, 12000);
INSERT INTO rpt_p0_package_menus (package_id,menu_ids)
SELECT p.package_id, COALESCE(GROUP_CONCAT(
  CASE WHEN legacy.menu_id IS NULL THEN
    SUBSTRING_INDEX(SUBSTRING_INDEX(p.menu_ids, ',', sequence.n), ',', -1)
  END ORDER BY sequence.n SEPARATOR ','), '')
FROM sys_tenant_package p
JOIN (
  SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 + 1 AS n
  FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) thousands
) sequence ON sequence.n <= CHAR_LENGTH(p.menu_ids) -
  CHAR_LENGTH(REPLACE(p.menu_ids, ',', '')) + 1
LEFT JOIN rpt_p0_legacy_menu_ids legacy ON CAST(legacy.menu_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci =
  TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p.menu_ids, ',', sequence.n), ',', -1))
WHERE p.menu_ids IS NOT NULL
GROUP BY p.package_id;
UPDATE sys_tenant_package p JOIN rpt_p0_package_menus cleaned ON cleaned.package_id = p.package_id
SET p.menu_ids = cleaned.menu_ids;
SET SESSION group_concat_max_len = @rpt_p0_group_concat_max_len;
DELETE rm FROM sys_role_menu rm JOIN rpt_p0_legacy_menu_ids legacy ON legacy.menu_id = rm.menu_id;
DELETE m FROM sys_menu m JOIN rpt_p0_legacy_menu_ids legacy ON legacy.menu_id = m.menu_id;
DROP TEMPORARY TABLE IF EXISTS rpt_p0_package_menus;
DROP TEMPORARY TABLE IF EXISTS rpt_p0_legacy_menu_ids;
DROP TABLE IF EXISTS `jimu_dict`;
DROP TABLE IF EXISTS `jimu_dict_item`;
DROP TABLE IF EXISTS `jimu_report`;
DROP TABLE IF EXISTS `jimu_report_category`;
DROP TABLE IF EXISTS `jimu_report_data_source`;
DROP TABLE IF EXISTS `jimu_report_db`;
DROP TABLE IF EXISTS `jimu_report_db_field`;
DROP TABLE IF EXISTS `jimu_report_db_param`;
DROP TABLE IF EXISTS `jimu_report_export_job`;
DROP TABLE IF EXISTS `jimu_report_export_log`;
DROP TABLE IF EXISTS `jimu_report_ext_data`;
DROP TABLE IF EXISTS `jimu_report_icon_lib`;
DROP TABLE IF EXISTS `jimu_report_link`;
DROP TABLE IF EXISTS `jimu_report_map`;
DROP TABLE IF EXISTS `jimu_report_share`;
DROP TABLE IF EXISTS `jimu_report_sheet`;
DROP TABLE IF EXISTS `test_monthly_report`;
DROP TABLE IF EXISTS `tmp_report_data_1`;
DROP TABLE IF EXISTS `tmp_report_data_income`;

CREATE TABLE IF NOT EXISTS `rpt_catalog` (
  `catalog_id` bigint NOT NULL,
`parent_id` bigint NOT NULL DEFAULT 0,
  `name` varchar(100) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`catalog_id`),
  UNIQUE KEY `uk_report_catalog_name` (`tenant_id`,`parent_id`,`name`),
  KEY `idx_report_catalog_tenant` (`tenant_id`,`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_definition` (
  `report_id` bigint NOT NULL,
`catalog_id` bigint DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `kind` varchar(20) NOT NULL,
  `draft_json` longtext NOT NULL,
  `draft_revision` bigint NOT NULL DEFAULT 1,
  `published_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_report_code` (`tenant_id`,`code`),
  KEY `idx_report_definition_catalog` (`tenant_id`,`catalog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_version` (
  `version_id` bigint NOT NULL,
`report_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `schema_version` int NOT NULL,
  `engine_version` varchar(32) NOT NULL,
  `dsl_json` longtext NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `dataset_version_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_report_version` (`tenant_id`,`report_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_dataset` (
  `dataset_id` bigint NOT NULL,
`code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `provider_code` varchar(64) NOT NULL,
  `published_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`dataset_id`),
  UNIQUE KEY `uk_report_dataset_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_dataset_version` (
  `version_id` bigint NOT NULL,
`dataset_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `provider_code` varchar(64) NOT NULL,
  `provider_version` int NOT NULL,
  `descriptor_json` longtext NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_dataset_version` (`tenant_id`,`dataset_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_acl` (
  `acl_id` bigint NOT NULL,
`resource_type` varchar(20) NOT NULL,
  `resource_id` bigint NOT NULL,
  `subject_type` varchar(20) NOT NULL,
  `subject_id` bigint NOT NULL,
  `action` varchar(20) NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`acl_id`),
  UNIQUE KEY `uk_report_acl` (`tenant_id`,`resource_type`,`resource_id`,`subject_type`,`subject_id`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_run` (
  `run_id` bigint NOT NULL,
`actor_id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `version_id` bigint NOT NULL,
  `mode` varchar(20) NOT NULL,
  `state` varchar(20) NOT NULL,
  `parameters_json` longtext NOT NULL,
  `scope_hash` varchar(64) NOT NULL,
  `snapshot_ref` varchar(256) DEFAULT NULL,
  `plan_ref` varchar(256) DEFAULT NULL,
  `row_count` bigint NOT NULL DEFAULT 0,
  `data_time` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`run_id`),
  KEY `idx_report_run_owner` (`tenant_id`,`actor_id`,`create_time`),
  KEY `idx_report_run_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_export_job` (
  `job_id` bigint NOT NULL,
`actor_id` bigint NOT NULL,
  `run_id` bigint NOT NULL,
  `format` varchar(32) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `state` varchar(32) NOT NULL,
  `attempts` int NOT NULL DEFAULT 0,
  `lease_owner` varchar(100) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `cancel_requested` tinyint NOT NULL DEFAULT 0,
  `file_ref` varchar(256) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`job_id`),
  UNIQUE KEY `uk_report_export_request` (`tenant_id`,`actor_id`,`idempotency_key`),
  KEY `idx_report_export_claim` (`state`,`lease_until`),
  KEY `idx_report_export_owner` (`tenant_id`,`actor_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `rpt_audit` (
  `audit_id` bigint NOT NULL,
`actor_id` bigint NOT NULL,
  `resource_id` bigint DEFAULT NULL,
  `action` varchar(32) NOT NULL,
  `outcome` varchar(32) NOT NULL,
  `details_json` text DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  KEY `idx_report_audit_owner` (`tenant_id`,`actor_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- Native report routes and operation permissions
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23000,'报表中心',0,8,'report',NULL,1,1,'M','0','0',NULL,'dataAnalysis',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23000);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23001,'报表管理',23000,1,'center','report/center/index',1,1,'C','0','0','report:definition:list','list',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23001);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23002,'数据集目录',23000,2,'dataset','report/dataset/index',1,1,'C','0','0','report:dataset:list','database',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23002);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23003,'导出中心',23000,3,'exports','report/exports/index',1,1,'C','0','0','report:runtime:export','download',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23003);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23004,'报表设计',23000,4,'designer','report/designer/index',1,1,'C','1','0','report:definition:edit','edit',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23004);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23005,'报表预览',23000,5,'viewer','report/viewer/index',1,1,'C','1','0','report:runtime:preview','preview',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23005);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23006,'发布历史',23000,6,'history','report/history/index',1,1,'C','1','0','report:definition:list','history',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23006);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23010,'新建报表',23001,1,'#',NULL,1,1,'F','0','0','report:definition:add','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23010);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23011,'修改报表',23001,2,'#',NULL,1,1,'F','0','0','report:definition:edit','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23011);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23012,'归档报表',23001,3,'#',NULL,1,1,'F','0','0','report:definition:remove','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23012);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23013,'发布报表',23001,4,'#',NULL,1,1,'F','0','0','report:definition:publish','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23013);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23014,'预览报表',23001,5,'#',NULL,1,1,'F','0','0','report:runtime:preview','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23014);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23015,'导出报表',23001,6,'#',NULL,1,1,'F','0','0','report:runtime:export','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23015);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23016,'管理资源授权',23001,7,'#',NULL,1,1,'F','0','0','report:acl:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23016);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23017,'管理数据集',23001,8,'#',NULL,1,1,'F','0','0','report:dataset:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23017);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23018,'管理报表目录',23001,9,'#',NULL,1,1,'F','0','0','report:catalog:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23018);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23019,'查询报表审计',23001,10,'#',NULL,1,1,'F','0','0','report:audit:query','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23019);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id BETWEEN 23000 AND 23019 WHERE r.role_key IN ('superadmin','admin') AND r.status='0' AND r.del_flag='0' AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=r.role_id AND x.menu_id=m.menu_id);

-- Replay-safe addition for a database created earlier in this P0 development.
SET @rpt_p0_catalog_index_exists=(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='rpt_catalog' AND INDEX_NAME='uk_report_catalog_name');
SET @rpt_p0_catalog_index_sql=IF(@rpt_p0_catalog_index_exists=0,'ALTER TABLE rpt_catalog ADD UNIQUE KEY uk_report_catalog_name (tenant_id,parent_id,name)','SELECT 1');
PREPARE rpt_p0_catalog_index_stmt FROM @rpt_p0_catalog_index_sql;
EXECUTE rpt_p0_catalog_index_stmt;
DEALLOCATE PREPARE rpt_p0_catalog_index_stmt;
