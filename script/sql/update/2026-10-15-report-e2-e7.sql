/** 自研报表E2至E7新增结构。执行前核对P0、E0/E1基线及目标主库；不删除业务数据。 */
/** 受控查询草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_query_definition` (
  `query_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `revision` bigint NOT NULL DEFAULT 1,
  `published_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `create_dept` bigint NOT NULL,
  `create_by` bigint NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`query_id`),
  UNIQUE KEY `uk_rpt_query_definition_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_query_version` (
  `version_id` bigint NOT NULL,
  `query_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `version_no` int NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `dependency_json` mediumtext NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_query_version_number` (`tenant_id`,`query_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 数据集版本与查询发布、目录版本的唯一固定绑定。 */
CREATE TABLE IF NOT EXISTS `rpt_query_dataset_binding` (
  `dataset_version_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `query_version_id` bigint NOT NULL,
  `catalog_version` int NOT NULL,
  `query_checksum` varchar(64) NOT NULL,
  `catalog_checksum` varchar(64) NOT NULL,
  PRIMARY KEY (`dataset_version_id`),
  KEY `idx_rpt_query_dataset_tenant` (`tenant_id`,`query_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 私有图片草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_asset` (
  `asset_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `revision` bigint NOT NULL DEFAULT 1,
  `published_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `create_dept` bigint NOT NULL,
  `create_by` bigint NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`asset_id`),
  UNIQUE KEY `uk_rpt_asset_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_asset_version` (
  `version_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `version_no` int NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `dependency_json` mediumtext NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_asset_version_number` (`tenant_id`,`asset_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 批量单据草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_batch` (
  `batch_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `revision` bigint NOT NULL DEFAULT 1,
  `published_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `create_dept` bigint NOT NULL,
  `create_by` bigint NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_rpt_batch_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_batch_version` (
  `version_id` bigint NOT NULL,
  `batch_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `version_no` int NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `dependency_json` mediumtext NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_batch_version_number` (`tenant_id`,`batch_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 批次每个单据独立保留固定版本、参数和导出任务。 */
CREATE TABLE IF NOT EXISTS `rpt_batch_item` (
  `batch_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `item_no` int NOT NULL,
  `report_version_id` bigint NOT NULL,
  `parameters_json` mediumtext NOT NULL,
  `job_id` bigint DEFAULT NULL,
  `state` varchar(20) NOT NULL DEFAULT 'PENDING',
  `error_code` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`batch_id`,`item_no`),
  KEY `idx_rpt_batch_tenant` (`tenant_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 独立仪表盘草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_dashboard` (
  `dashboard_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `revision` bigint NOT NULL DEFAULT 1,
  `published_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `create_dept` bigint NOT NULL,
  `create_by` bigint NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`dashboard_id`),
  UNIQUE KEY `uk_rpt_dashboard_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_dashboard_version` (
  `version_id` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `version_no` int NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `dependency_json` mediumtext NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_dashboard_version_number` (`tenant_id`,`dashboard_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 收藏不授予访问权限；刷新配额按全局、租户及用户统一锁定。 */
CREATE TABLE IF NOT EXISTS `rpt_dashboard_favorite` (
  `tenant_id` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  `dashboard_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_id`,`user_id`,`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_refresh_quota` (
  `scope_key` varchar(100) NOT NULL,
  `window_start` datetime NOT NULL,
  `counter` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 全局及租户硬配额，暂存和未确认删除对象均占用容量。 */
CREATE TABLE IF NOT EXISTS `rpt_quota` (
  `scope_key` varchar(80) NOT NULL,
  `limit_bytes` bigint NOT NULL,
  `limit_objects` bigint NOT NULL,
  `used_bytes` bigint NOT NULL DEFAULT 0,
  `object_count` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_artifact_manifest` (
  `tenant_id` varchar(20) NOT NULL,
  `store_id` varchar(64) NOT NULL,
  `reference` varchar(80) NOT NULL,
  `state` varchar(20) NOT NULL,
  `reserved_bytes` bigint NOT NULL DEFAULT 0,
  `content_length` bigint NOT NULL DEFAULT 0,
  `checksum` varchar(64) DEFAULT NULL,
  `lease_owner` varchar(100) NOT NULL,
  `staging_path` varchar(500) DEFAULT NULL,
  `lease_until` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`store_id`,`reference`),
  KEY `idx_rpt_manifest_recovery` (`store_id`,`state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_resource_artifact` (
  `tenant_id` varchar(20) NOT NULL,
  `resource_type` varchar(20) NOT NULL,
  `version_id` bigint NOT NULL,
  `artifact_ref` varchar(80) NOT NULL,
  PRIMARY KEY (`tenant_id`,`resource_type`,`version_id`),
  KEY `idx_rpt_resource_artifact` (`tenant_id`,`artifact_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_snapshot_index` (
  `tenant_id` varchar(20) NOT NULL,
  `snapshot_ref` varchar(80) NOT NULL,
  `index_ref` varchar(80) NOT NULL,
  `snapshot_checksum` varchar(64) NOT NULL,
  `index_checksum` varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`snapshot_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_font_resource` (
  `tenant_id` varchar(20) NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `font_ref` varchar(80) NOT NULL,
  `lease_until` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`checksum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_plan_font` (
  `tenant_id` varchar(20) NOT NULL,
  `plan_ref` varchar(80) NOT NULL,
  `font_ref` varchar(80) NOT NULL,
  `font_checksum` varchar(64) NOT NULL,
  PRIMARY KEY (`tenant_id`,`plan_ref`),
  KEY `idx_rpt_plan_font` (`tenant_id`,`font_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 调度全局互斥及租户/用户轮转记录，排队容量和执行并发分别控制。 */
CREATE TABLE IF NOT EXISTS `rpt_dispatch_gate` (
  `scope_key` varchar(80) NOT NULL,
  `last_claim` datetime DEFAULT NULL,
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 固定报表订阅草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_subscription` (
  `subscription_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `revision` bigint NOT NULL DEFAULT 1,
  `published_version_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `create_dept` bigint NOT NULL,
  `create_by` bigint NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`subscription_id`),
  UNIQUE KEY `uk_rpt_subscription_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_subscription_version` (
  `version_id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `version_no` int NOT NULL,
  `definition_json` mediumtext NOT NULL,
  `dependency_json` mediumtext NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_subscription_version_number` (`tenant_id`,`subscription_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/** 调度、逐接收人执行及领取记录分离，并有独立幂等约束。 */
CREATE TABLE IF NOT EXISTS `rpt_subscription_schedule` (
  `tenant_id` varchar(20) NOT NULL,
  `subscription_id` bigint NOT NULL,
  `definition_version_id` bigint NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `next_at` datetime(6) NOT NULL,
  `last_error` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`tenant_id`,`subscription_id`),
  KEY `idx_rpt_subscription_due` (`enabled`,`next_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_subscription_run` (
  `execution_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `subscription_id` bigint NOT NULL,
  `definition_version_id` bigint NOT NULL,
  `scheduled_key` varchar(80) NOT NULL,
  `scheduled_instant` datetime(6) NOT NULL,
  `event_type` varchar(20) NOT NULL,
  `recipient_id` bigint NOT NULL,
  `job_id` bigint DEFAULT NULL,
  `state` varchar(20) NOT NULL,
  `attempts` int NOT NULL DEFAULT 0,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `error_code` varchar(80) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`execution_id`),
  UNIQUE KEY `uk_rpt_subscription_occurrence` (`tenant_id`,`subscription_id`,`scheduled_key`,`recipient_id`),
  KEY `idx_rpt_subscription_recipient` (`tenant_id`,`recipient_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `rpt_delivery` (
  `delivery_id` bigint NOT NULL,
  `tenant_id` varchar(20) NOT NULL,
  `execution_id` bigint NOT NULL,
  `subscription_id` bigint NOT NULL,
  `definition_version_id` bigint NOT NULL,
  `recipient_id` bigint NOT NULL,
  `job_id` bigint NOT NULL,
  `state` varchar(20) NOT NULL DEFAULT 'PREPARING',
  `notification_state` varchar(20) NOT NULL DEFAULT 'DISABLED',
  `notification_attempts` int NOT NULL DEFAULT 0,
  `expires_at` datetime(6) NOT NULL,
  `claimed_at` datetime(6) DEFAULT NULL,
  `error_code` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`delivery_id`),
  UNIQUE KEY `uk_rpt_delivery_execution` (`tenant_id`,`execution_id`),
  KEY `idx_rpt_delivery_recipient` (`tenant_id`,`recipient_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/** 原生扩展菜单沿用23000根，查询和资源管理保持独立权限。 */
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23030,'受控查询',23000,7,'query','report/query/index',1,1,'C','0','0','report:query:list','search',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23030);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23030 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23030);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23031,'新建查询',23030,1,'#',NULL,1,1,'F','0','0','report:query:add','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23031);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23031 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23031);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23032,'编辑查询',23030,2,'#',NULL,1,1,'F','0','0','report:query:edit','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23032);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23032 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23032);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23033,'发布查询',23030,3,'#',NULL,1,1,'F','0','0','report:query:publish','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23033);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23033 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23033);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23040,'交叉报表',23000,8,'pivot','report/pivot/index',1,1,'C','0','0','report:definition:edit','chart',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23040);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23040 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23040);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23050,'私有打印图片',23000,9,'assets','report/assets/index',1,1,'C','0','0','report:asset:list','image',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23050);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23050 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23050);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23051,'管理打印图片',23050,1,'#',NULL,1,1,'F','0','0','report:asset:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23051);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23051 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23051);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23060,'批量单据',23000,10,'batch','report/batch/index',1,1,'C','0','0','report:batch:list','print',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23060);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23060 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23060);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23061,'管理单据批次',23060,1,'#',NULL,1,1,'F','0','0','report:batch:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23061);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23061 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23061);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23070,'个人仪表盘',23000,11,'dashboard','report/dashboard/index',1,1,'C','0','0','report:dashboard:list','dashboard',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23070);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23070 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23070);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23071,'编辑仪表盘',23070,1,'#',NULL,1,1,'F','0','0','report:dashboard:edit','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23071);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23071 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23071);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23072,'发布仪表盘',23070,2,'#',NULL,1,1,'F','0','0','report:dashboard:publish','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23072);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23072 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23072);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23080,'报表订阅',23000,12,'subscription','report/subscription/index',1,1,'C','0','0','report:subscription:list','time',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23080);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23080 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23080);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23081,'管理订阅',23080,1,'#',NULL,1,1,'F','0','0','report:subscription:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23081);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23081 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23081);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23082,'发布订阅',23080,2,'#',NULL,1,1,'F','0','0','report:subscription:publish','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23082);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23082 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23082);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23090,'我的报表交付',23000,13,'delivery','report/delivery/index',1,1,'C','0','0','report:runtime:export','download',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23090);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,23090 FROM DUAL WHERE EXISTS (SELECT 1 FROM sys_role WHERE role_id=1) AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=1 AND menu_id=23090);
/** 手动及定时时刻先固定一个整体封套，重放不能扩充接收人或改变定义版本。 */
CREATE TABLE IF NOT EXISTS `rpt_subscription_occurrence` (
  `tenant_id` varchar(20) NOT NULL,
  `subscription_id` bigint NOT NULL,
  `scheduled_key` varchar(80) NOT NULL,
  `definition_version_id` bigint NOT NULL,
  `scheduled_instant` datetime(6) NOT NULL,
  `event_type` varchar(20) NOT NULL,
  PRIMARY KEY (`tenant_id`,`subscription_id`,`scheduled_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
