/** 2026-10-14 自研报表首期增量，适用于本机系统主库 ry-vue，兼容 MySQL 5.7。 */
/** 用户授权直接删除未使用积木资源，不迁移业务表。 */
/** 删除表名使用明确清单；报表建表与菜单插入支持幂等重放。 */
/** 保留已有 rpt_* 表与定义，不删除或重新植入模板。 */
/** 旧菜单已删除时，已知标识仍用于修复角色和套餐引用。 */
/** 删除菜单前捕获旧报表地址菜单标识，不选择业务模块报表菜单。 */
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

/** 从保留标识一次重建每份逗号列表，避免按关联菜单重复更新。 */
/** 菜单标识列表使用四字节字符集，允许一万二千字节并恢复会话设置。 */
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
  `catalog_id` bigint NOT NULL COMMENT '报表目录ID',
`parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父报表目录ID',
  `name` varchar(100) NOT NULL COMMENT '报表目录名称',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '目录显示排序',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`catalog_id`),
  UNIQUE KEY `uk_report_catalog_name` (`tenant_id`,`parent_id`,`name`),
  KEY `idx_report_catalog_tenant` (`tenant_id`,`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表目录';

CREATE TABLE IF NOT EXISTS `rpt_definition` (
  `report_id` bigint NOT NULL COMMENT '报表定义ID',
`catalog_id` bigint DEFAULT NULL COMMENT '所属报表目录ID',
  `code` varchar(64) NOT NULL COMMENT '报表唯一编码',
  `name` varchar(100) NOT NULL COMMENT '报表名称',
  `kind` varchar(20) NOT NULL COMMENT '报表类型（表格、单据或交叉报表）',
  `draft_json` longtext NOT NULL COMMENT '报表DSL草稿JSON',
  `draft_revision` bigint NOT NULL DEFAULT 1 COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '报表生命周期状态',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_report_code` (`tenant_id`,`code`),
  KEY `idx_report_definition_catalog` (`tenant_id`,`catalog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表定义与草稿';

CREATE TABLE IF NOT EXISTS `rpt_version` (
  `version_id` bigint NOT NULL COMMENT '报表发布版本ID',
`report_id` bigint NOT NULL COMMENT '报表定义ID',
  `version_no` int NOT NULL COMMENT '资源内递增版本号',
  `schema_version` int NOT NULL COMMENT '报表DSL结构版本',
  `engine_version` varchar(32) NOT NULL COMMENT '固定报表执行引擎版本',
  `dsl_json` longtext NOT NULL COMMENT '不可变报表DSL定义JSON',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `dataset_version_id` bigint NOT NULL COMMENT '固定数据集版本ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_report_version` (`tenant_id`,`report_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表不可变发布版本';

CREATE TABLE IF NOT EXISTS `rpt_dataset` (
  `dataset_id` bigint NOT NULL COMMENT '数据集ID',
`code` varchar(64) NOT NULL COMMENT '数据集唯一编码',
  `name` varchar(100) NOT NULL COMMENT '数据集名称',
  `provider_code` varchar(64) NOT NULL COMMENT '代码数据提供者编码',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '数据集启用状态',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dataset_id`),
  UNIQUE KEY `uk_report_dataset_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表数据集';

CREATE TABLE IF NOT EXISTS `rpt_dataset_version` (
  `version_id` bigint NOT NULL COMMENT '数据集契约版本ID',
`dataset_id` bigint NOT NULL COMMENT '数据集ID',
  `version_no` int NOT NULL COMMENT '资源内递增版本号',
  `provider_code` varchar(64) NOT NULL COMMENT '代码数据提供者编码',
  `provider_version` int NOT NULL COMMENT '代码数据提供者契约版本',
  `descriptor_json` longtext NOT NULL COMMENT '数据集字段与参数契约JSON',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_dataset_version` (`tenant_id`,`dataset_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表数据集契约版本';

CREATE TABLE IF NOT EXISTS `rpt_acl` (
  `acl_id` bigint NOT NULL COMMENT '资源授权ID',
`resource_type` varchar(20) NOT NULL COMMENT '资源类型',
  `resource_id` bigint NOT NULL COMMENT '被授权报表资源ID',
  `subject_type` varchar(20) NOT NULL COMMENT '授权主体类型（用户、角色或部门）',
  `subject_id` bigint NOT NULL COMMENT '被授权用户、角色或部门ID',
  `action` varchar(20) NOT NULL COMMENT '资源授权操作（查看、编辑、发布、运行、导出、授权或管理）',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`acl_id`),
  UNIQUE KEY `uk_report_acl` (`tenant_id`,`resource_type`,`resource_id`,`subject_type`,`subject_id`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表资源访问授权';

CREATE TABLE IF NOT EXISTS `rpt_run` (
  `run_id` bigint NOT NULL COMMENT '报表运行ID',
`actor_id` bigint NOT NULL COMMENT '操作用户ID',
  `report_id` bigint NOT NULL COMMENT '报表定义ID',
  `version_id` bigint NOT NULL COMMENT '不可变版本ID',
  `mode` varchar(20) NOT NULL COMMENT '报表运行模式',
  `state` varchar(20) NOT NULL COMMENT '报表运行状态',
  `parameters_json` longtext NOT NULL COMMENT '固定运行参数JSON',
  `scope_hash` varchar(64) NOT NULL COMMENT '服务端权限与业务范围指纹',
  `snapshot_ref` varchar(256) DEFAULT NULL COMMENT '私有数据快照逻辑引用',
  `plan_ref` varchar(256) DEFAULT NULL COMMENT '私有渲染计划逻辑引用',
  `row_count` bigint NOT NULL DEFAULT 0 COMMENT '数据快照总行数',
  `data_time` datetime NOT NULL COMMENT '业务数据快照时间',
  `expires_at` datetime NOT NULL COMMENT '运行快照有效期截止时间',
  `error_code` varchar(64) DEFAULT NULL COMMENT '受控错误码',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`),
  KEY `idx_report_run_owner` (`tenant_id`,`actor_id`,`create_time`),
  KEY `idx_report_run_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表运行记录与私有快照';

CREATE TABLE IF NOT EXISTS `rpt_export_job` (
  `job_id` bigint NOT NULL COMMENT '导出任务ID',
`actor_id` bigint NOT NULL COMMENT '操作用户ID',
  `run_id` bigint NOT NULL COMMENT '报表运行ID',
  `format` varchar(32) NOT NULL COMMENT '导出文件格式',
  `idempotency_key` varchar(80) NOT NULL COMMENT '请求幂等键',
  `request_hash` varchar(64) NOT NULL COMMENT '规范化请求参数指纹',
  `state` varchar(32) NOT NULL COMMENT '导出任务状态',
  `execution_phase` varchar(24) NOT NULL DEFAULT 'QUEUED' COMMENT '导出任务执行阶段',
  `processed_rows` bigint NOT NULL DEFAULT 0 COMMENT '已处理数据行数',
  `total_rows` bigint DEFAULT NULL COMMENT '预计处理总行数',
  `retry_round` int NOT NULL DEFAULT 0 COMMENT '任务重试轮次',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '执行器最近心跳时间',
  `attempts` int NOT NULL DEFAULT 0 COMMENT '已尝试执行次数',
  `lease_owner` varchar(100) DEFAULT NULL COMMENT '当前执行租约持有者',
  `lease_until` datetime DEFAULT NULL COMMENT '当前执行租约到期时间',
  `cancel_requested` tinyint NOT NULL DEFAULT 0 COMMENT '是否请求取消任务',
  `file_ref` varchar(256) DEFAULT NULL COMMENT '私有导出文件逻辑引用',
  `file_size` bigint DEFAULT NULL COMMENT '导出文件大小（字节）',
  `expires_at` datetime NOT NULL COMMENT '导出文件有效期截止时间',
  `error_code` varchar(64) DEFAULT NULL COMMENT '受控错误码',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`job_id`),
  UNIQUE KEY `uk_report_export_request` (`tenant_id`,`actor_id`,`idempotency_key`),
  KEY `idx_report_export_claim` (`state`,`lease_until`),
  KEY `idx_report_export_owner` (`tenant_id`,`actor_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表异步导出任务';

CREATE TABLE IF NOT EXISTS `rpt_audit` (
  `audit_id` bigint NOT NULL COMMENT '操作审计ID',
`actor_id` bigint NOT NULL COMMENT '操作用户ID',
  `resource_id` bigint DEFAULT NULL COMMENT '本次操作资源ID',
  `action` varchar(32) NOT NULL COMMENT '审计操作类型',
  `outcome` varchar(32) NOT NULL COMMENT '审计操作结果',
  `details_json` text DEFAULT NULL COMMENT '操作审计详情JSON',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '操作追踪标识',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`audit_id`),
  KEY `idx_report_audit_owner` (`tenant_id`,`actor_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表操作审计';


/** 原生报表路由和操作权限。 */
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

/** 为首期开发早期创建的数据库幂等补充缺失字段。 */
SET @rpt_p0_catalog_index_exists=(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='rpt_catalog' AND INDEX_NAME='uk_report_catalog_name');
SET @rpt_p0_catalog_index_sql=IF(@rpt_p0_catalog_index_exists=0,'ALTER TABLE rpt_catalog ADD UNIQUE KEY uk_report_catalog_name (tenant_id,parent_id,name)','SELECT 1');
PREPARE rpt_p0_catalog_index_stmt FROM @rpt_p0_catalog_index_sql;
EXECUTE rpt_p0_catalog_index_stmt;
DEALLOCATE PREPARE rpt_p0_catalog_index_stmt;
