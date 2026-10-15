/** 自研报表E2至E7新增结构。执行前核对P0、E0/E1基线及目标主库；不删除业务数据。 */
/** 受控查询草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_query_definition` (
  `query_id` bigint NOT NULL COMMENT '受控查询定义ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) NOT NULL COMMENT '受控查询唯一编码',
  `name` varchar(100) NOT NULL COMMENT '受控查询名称',
  `definition_json` mediumtext NOT NULL COMMENT '受控查询草稿定义JSON',
  `revision` bigint NOT NULL DEFAULT 1 COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '受控查询生命周期状态',
  `create_dept` bigint NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`query_id`),
  UNIQUE KEY `uk_rpt_query_definition_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表受控查询定义';
CREATE TABLE IF NOT EXISTS `rpt_query_version` (
  `version_id` bigint NOT NULL COMMENT '受控查询发布版本ID',
  `query_id` bigint NOT NULL COMMENT '受控查询定义ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `version_no` int NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext NOT NULL COMMENT '不可变受控查询定义JSON',
  `dependency_json` mediumtext NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_query_version_number` (`tenant_id`,`query_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表受控查询发布版本';
/** 数据集版本与查询发布、目录版本的唯一固定绑定。 */
CREATE TABLE IF NOT EXISTS `rpt_query_dataset_binding` (
  `dataset_version_id` bigint NOT NULL COMMENT '固定数据集版本ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `query_version_id` bigint NOT NULL COMMENT '固定受控查询版本ID',
  `catalog_version` int NOT NULL COMMENT '批准的逻辑目录版本',
  `query_checksum` varchar(64) NOT NULL COMMENT '受控查询定义SHA-256校验和',
  `catalog_checksum` varchar(64) NOT NULL COMMENT '逻辑目录内容SHA-256校验和',
  PRIMARY KEY (`dataset_version_id`),
  KEY `idx_rpt_query_dataset_tenant` (`tenant_id`,`query_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控查询与数据集版本绑定';
/** 私有图片草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_asset` (
  `asset_id` bigint NOT NULL COMMENT '私有图片资源ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) NOT NULL COMMENT '私有图片资源唯一编码',
  `name` varchar(100) NOT NULL COMMENT '私有图片资源名称',
  `definition_json` mediumtext NOT NULL COMMENT '私有图片资源草稿定义JSON',
  `revision` bigint NOT NULL DEFAULT 1 COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '私有图片资源生命周期状态',
  `create_dept` bigint NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`asset_id`),
  UNIQUE KEY `uk_rpt_asset_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表私有图片资源定义';
CREATE TABLE IF NOT EXISTS `rpt_asset_version` (
  `version_id` bigint NOT NULL COMMENT '私有图片资源发布版本ID',
  `asset_id` bigint NOT NULL COMMENT '私有图片资源ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `version_no` int NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext NOT NULL COMMENT '不可变私有图片资源定义JSON',
  `dependency_json` mediumtext NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_asset_version_number` (`tenant_id`,`asset_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表私有图片资源版本';
/** 批量单据草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_batch` (
  `batch_id` bigint NOT NULL COMMENT '报表批次ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) NOT NULL COMMENT '报表批次唯一编码',
  `name` varchar(100) NOT NULL COMMENT '报表批次名称',
  `definition_json` mediumtext NOT NULL COMMENT '报表批次草稿定义JSON',
  `revision` bigint NOT NULL DEFAULT 1 COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '报表批次生命周期状态',
  `create_dept` bigint NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_rpt_batch_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表批次定义';
CREATE TABLE IF NOT EXISTS `rpt_batch_version` (
  `version_id` bigint NOT NULL COMMENT '报表批次发布版本ID',
  `batch_id` bigint NOT NULL COMMENT '报表批次ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `version_no` int NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext NOT NULL COMMENT '不可变报表批次定义JSON',
  `dependency_json` mediumtext NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_batch_version_number` (`tenant_id`,`batch_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表批次发布版本';
/** 批次每个单据独立保留固定版本、参数和导出任务。 */
CREATE TABLE IF NOT EXISTS `rpt_batch_item` (
  `batch_id` bigint NOT NULL COMMENT '报表批次ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `item_no` int NOT NULL COMMENT '批次内执行项序号',
  `report_version_id` bigint NOT NULL COMMENT '固定报表发布版本ID',
  `parameters_json` mediumtext NOT NULL COMMENT '固定运行参数JSON',
  `job_id` bigint DEFAULT NULL COMMENT '导出任务ID',
  `state` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '批次执行项状态',
  `error_code` varchar(80) DEFAULT NULL COMMENT '受控错误码',
  PRIMARY KEY (`batch_id`,`item_no`),
  KEY `idx_rpt_batch_tenant` (`tenant_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表批次执行明细';
/** 独立仪表盘草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_dashboard` (
  `dashboard_id` bigint NOT NULL COMMENT '仪表盘ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) NOT NULL COMMENT '仪表盘唯一编码',
  `name` varchar(100) NOT NULL COMMENT '仪表盘名称',
  `definition_json` mediumtext NOT NULL COMMENT '仪表盘草稿定义JSON',
  `revision` bigint NOT NULL DEFAULT 1 COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '仪表盘生命周期状态',
  `create_dept` bigint NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dashboard_id`),
  UNIQUE KEY `uk_rpt_dashboard_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表仪表盘定义';
CREATE TABLE IF NOT EXISTS `rpt_dashboard_version` (
  `version_id` bigint NOT NULL COMMENT '仪表盘发布版本ID',
  `dashboard_id` bigint NOT NULL COMMENT '仪表盘ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `version_no` int NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext NOT NULL COMMENT '不可变仪表盘定义JSON',
  `dependency_json` mediumtext NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_dashboard_version_number` (`tenant_id`,`dashboard_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表仪表盘发布版本';
/** 收藏不授予访问权限；刷新配额按全局、租户及用户统一锁定。 */
CREATE TABLE IF NOT EXISTS `rpt_dashboard_favorite` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `dashboard_id` bigint NOT NULL COMMENT '仪表盘ID',
  PRIMARY KEY (`tenant_id`,`user_id`,`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户仪表盘收藏';
CREATE TABLE IF NOT EXISTS `rpt_refresh_quota` (
  `scope_key` varchar(100) NOT NULL COMMENT '刷新限流作用域标识',
  `window_start` datetime NOT NULL COMMENT '当前限流时间窗口起点',
  `counter` int NOT NULL DEFAULT 0 COMMENT '当前时间窗口累计次数',
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表刷新频率配额';
/** 全局及租户硬配额，暂存和未确认删除对象均占用容量。 */
CREATE TABLE IF NOT EXISTS `rpt_quota` (
  `scope_key` varchar(80) NOT NULL COMMENT '存储配额作用域标识',
  `limit_bytes` bigint NOT NULL COMMENT '允许使用的存储容量上限（字节）',
  `limit_objects` bigint NOT NULL COMMENT '允许保存的文件数量上限',
  `used_bytes` bigint NOT NULL DEFAULT 0 COMMENT '已占用存储容量（字节）',
  `object_count` bigint NOT NULL DEFAULT 0 COMMENT '已占用文件数量',
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表私有存储容量配额';
CREATE TABLE IF NOT EXISTS `rpt_artifact_manifest` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `store_id` varchar(64) NOT NULL COMMENT '私有存储实例标识',
  `reference` varchar(80) NOT NULL COMMENT '私有文件逻辑引用',
  `state` varchar(20) NOT NULL COMMENT '私有文件生命周期状态',
  `reserved_bytes` bigint NOT NULL DEFAULT 0 COMMENT '写入预留容量（字节）',
  `content_length` bigint NOT NULL DEFAULT 0 COMMENT '已提交文件大小（字节）',
  `checksum` varchar(64) DEFAULT NULL COMMENT '内容SHA-256校验和',
  `lease_owner` varchar(100) NOT NULL COMMENT '当前执行租约持有者',
  `staging_path` varchar(500) DEFAULT NULL COMMENT '尚未提交文件的暂存路径',
  `lease_until` datetime NOT NULL COMMENT '当前执行租约到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`tenant_id`,`store_id`,`reference`),
  KEY `idx_rpt_manifest_recovery` (`store_id`,`state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表私有文件生命周期清单';
CREATE TABLE IF NOT EXISTS `rpt_resource_artifact` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `resource_type` varchar(20) NOT NULL COMMENT '资源类型',
  `version_id` bigint NOT NULL COMMENT '不可变版本ID',
  `artifact_ref` varchar(80) NOT NULL COMMENT '私有文件逻辑引用',
  PRIMARY KEY (`tenant_id`,`resource_type`,`version_id`),
  KEY `idx_rpt_resource_artifact` (`tenant_id`,`artifact_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表资源版本与私有文件关联';
CREATE TABLE IF NOT EXISTS `rpt_snapshot_index` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `snapshot_ref` varchar(80) NOT NULL COMMENT '私有数据快照逻辑引用',
  `index_ref` varchar(80) NOT NULL COMMENT '私有快照索引逻辑引用',
  `snapshot_checksum` varchar(64) NOT NULL COMMENT '数据快照SHA-256校验和',
  `index_checksum` varchar(64) NOT NULL COMMENT '快照索引SHA-256校验和',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tenant_id`,`snapshot_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表数据快照与索引关联';
CREATE TABLE IF NOT EXISTS `rpt_font_resource` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `font_ref` varchar(80) NOT NULL COMMENT '私有字体文件逻辑引用',
  `lease_until` datetime NOT NULL COMMENT '当前执行租约到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tenant_id`,`checksum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表共享字体资源';
CREATE TABLE IF NOT EXISTS `rpt_plan_font` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `plan_ref` varchar(80) NOT NULL COMMENT '私有渲染计划逻辑引用',
  `font_ref` varchar(80) NOT NULL COMMENT '私有字体文件逻辑引用',
  `font_checksum` varchar(64) NOT NULL COMMENT '字体文件SHA-256校验和',
  PRIMARY KEY (`tenant_id`,`plan_ref`),
  KEY `idx_rpt_plan_font` (`tenant_id`,`font_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表渲染计划字体引用';
/** 调度全局互斥及租户/用户轮转记录，排队容量和执行并发分别控制。 */
CREATE TABLE IF NOT EXISTS `rpt_dispatch_gate` (
  `scope_key` varchar(80) NOT NULL COMMENT '公平调度作用域标识',
  `last_claim` datetime DEFAULT NULL COMMENT '最近一次领取任务的调度标记',
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表公平调度领取控制';
/** 固定报表订阅草稿与不可变发布元数据，仅存于系统主库。 */
CREATE TABLE IF NOT EXISTS `rpt_subscription` (
  `subscription_id` bigint NOT NULL COMMENT '报表订阅ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) NOT NULL COMMENT '报表订阅唯一编码',
  `name` varchar(100) NOT NULL COMMENT '报表订阅名称',
  `definition_json` mediumtext NOT NULL COMMENT '报表订阅草稿定义JSON',
  `revision` bigint NOT NULL DEFAULT 1 COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '报表订阅生命周期状态',
  `create_dept` bigint NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`subscription_id`),
  UNIQUE KEY `uk_rpt_subscription_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表订阅定义';
CREATE TABLE IF NOT EXISTS `rpt_subscription_version` (
  `version_id` bigint NOT NULL COMMENT '报表订阅发布版本ID',
  `subscription_id` bigint NOT NULL COMMENT '报表订阅ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `version_no` int NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext NOT NULL COMMENT '不可变报表订阅定义JSON',
  `dependency_json` mediumtext NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_subscription_version_number` (`tenant_id`,`subscription_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表订阅发布版本';
/** 调度、逐接收人执行及领取记录分离，并有独立幂等约束。 */
CREATE TABLE IF NOT EXISTS `rpt_subscription_schedule` (
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `subscription_id` bigint NOT NULL COMMENT '报表订阅ID',
  `definition_version_id` bigint NOT NULL COMMENT '固定订阅定义版本ID',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用订阅调度',
  `next_at` datetime(6) NOT NULL COMMENT '下一次计划触发时间',
  `last_error` varchar(80) DEFAULT NULL COMMENT '最近一次调度错误码',
  PRIMARY KEY (`tenant_id`,`subscription_id`),
  KEY `idx_rpt_subscription_due` (`enabled`,`next_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表订阅调度状态';
CREATE TABLE IF NOT EXISTS `rpt_subscription_run` (
  `execution_id` bigint NOT NULL COMMENT '订阅执行ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `subscription_id` bigint NOT NULL COMMENT '报表订阅ID',
  `definition_version_id` bigint NOT NULL COMMENT '固定订阅定义版本ID',
  `scheduled_key` varchar(80) NOT NULL COMMENT '订阅触发幂等键',
  `scheduled_instant` datetime(6) NOT NULL COMMENT '本次触发计划时间',
  `event_type` varchar(20) NOT NULL COMMENT '订阅触发事件类型',
  `recipient_id` bigint NOT NULL COMMENT '接收人用户ID',
  `job_id` bigint DEFAULT NULL COMMENT '导出任务ID',
  `state` varchar(20) NOT NULL COMMENT '订阅执行与重试状态',
  `attempts` int NOT NULL DEFAULT 0 COMMENT '已尝试执行次数',
  `next_retry_at` datetime(6) DEFAULT NULL COMMENT '下一次允许重试时间',
  `error_code` varchar(80) DEFAULT NULL COMMENT '受控错误码',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`execution_id`),
  UNIQUE KEY `uk_rpt_subscription_occurrence` (`tenant_id`,`subscription_id`,`scheduled_key`,`recipient_id`),
  KEY `idx_rpt_subscription_recipient` (`tenant_id`,`recipient_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表订阅执行与重试记录';
CREATE TABLE IF NOT EXISTS `rpt_delivery` (
  `delivery_id` bigint NOT NULL COMMENT '私有交付ID',
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `execution_id` bigint NOT NULL COMMENT '订阅执行ID',
  `subscription_id` bigint NOT NULL COMMENT '报表订阅ID',
  `definition_version_id` bigint NOT NULL COMMENT '固定订阅定义版本ID',
  `recipient_id` bigint NOT NULL COMMENT '接收人用户ID',
  `job_id` bigint NOT NULL COMMENT '导出任务ID',
  `state` varchar(20) NOT NULL DEFAULT 'PREPARING' COMMENT '接收人私有交付状态',
  `notification_state` varchar(20) NOT NULL DEFAULT 'DISABLED' COMMENT '交付通知状态',
  `notification_attempts` int NOT NULL DEFAULT 0 COMMENT '已尝试通知次数',
  `expires_at` datetime(6) NOT NULL COMMENT '交付文件有效期截止时间',
  `claimed_at` datetime(6) DEFAULT NULL COMMENT '接收人领取时间',
  `error_code` varchar(80) DEFAULT NULL COMMENT '受控错误码',
  PRIMARY KEY (`delivery_id`),
  UNIQUE KEY `uk_rpt_delivery_execution` (`tenant_id`,`execution_id`),
  KEY `idx_rpt_delivery_recipient` (`tenant_id`,`recipient_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表接收人私有交付';

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
  `tenant_id` varchar(20) NOT NULL COMMENT '租户隔离标识',
  `subscription_id` bigint NOT NULL COMMENT '报表订阅ID',
  `scheduled_key` varchar(80) NOT NULL COMMENT '订阅触发幂等键',
  `definition_version_id` bigint NOT NULL COMMENT '固定订阅定义版本ID',
  `scheduled_instant` datetime(6) NOT NULL COMMENT '本次触发计划时间',
  `event_type` varchar(20) NOT NULL COMMENT '订阅触发事件类型',
  PRIMARY KEY (`tenant_id`,`subscription_id`,`scheduled_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表订阅触发去重记录';
