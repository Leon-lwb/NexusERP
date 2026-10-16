-- 2026-10-17 生产增量：自研报表34表 + 工程3表，兼容MySQL 5.7。
-- 使用前先备份并查询确认：系统库ry-vue尚无rpt_* / ai_engineering_*，新菜单ID无冲突。
-- 最终列/索引包含E0/E1进度字段，CREATE遇已有表失败，禁止--force/主安装SQL覆盖。
-- 本轮用户明确授权删除下列19张积木（含3张明确的积木演示）物理表和旧菜单；其他业务表/数据不变。
-- 仅超级管理员取得新功能权限；普通角色/租户套餐不自动扩权。
SET NAMES utf8mb4;
USE `ry-vue`;

CREATE TABLE `ai_engineering_artifact` (
  `artifact_id` bigint(20) NOT NULL COMMENT '产物编号',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `user_id` bigint(20) NOT NULL COMMENT '产物所有者',
  `task_id` bigint(20) NOT NULL COMMENT '所属任务',
  `attempt` int(11) NOT NULL COMMENT '所属尝试编号',
  `artifact_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物类型',
  `group_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模型材质组',
  `relative_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '组内相对路径，区分大小写',
  `service` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '显式私有存储配置键',
  `oss_id` bigint(20) DEFAULT NULL COMMENT '内部私有对象编号',
  `object_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务端对象键，不对外输出',
  `sha256` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务端复核内容摘要',
  `size` bigint(20) NOT NULL COMMENT '真实字节数',
  `mime_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '验证后媒体类型',
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安全显示文件名',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESERVED' COMMENT '预留、确认或待清理状态',
  `cleanup_generation` bigint(20) NOT NULL DEFAULT '0' COMMENT '清理租约CAS代数，旧进程不得覆盖新清理者',
  `cleanup_expires_at` datetime DEFAULT NULL COMMENT '清理租约到期或失败后重试时间，过期CLEANING可恢复',
  `validation_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '服务端格式解析摘要',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建用户',
  `create_time` datetime DEFAULT NULL COMMENT '创建审计时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '修改用户',
  `update_time` datetime DEFAULT NULL COMMENT '修改审计时间',
  PRIMARY KEY (`artifact_id`),
  UNIQUE KEY `uk_engineering_artifact_path` (`tenant_id`,`task_id`,`attempt`,`artifact_type`,`relative_path`),
  KEY `idx_engineering_artifact_owner` (`tenant_id`,`user_id`,`task_id`,`status`),
  KEY `idx_engineering_artifact_cleanup` (`status`,`cleanup_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI工程私有产物';

CREATE TABLE `ai_engineering_device` (
  `device_id` bigint(20) NOT NULL COMMENT '设备编号',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '000000' COMMENT '租户编号，保留前导零',
  `user_id` bigint(20) NOT NULL COMMENT '所有者用户编号',
  `device_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '设备显示名称',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '设备状态',
  `access_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '访问凭证摘要',
  `refresh_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '刷新凭证摘要',
  `generation` bigint(20) NOT NULL DEFAULT '1' COMMENT '凭证代数',
  `access_expires_at` datetime DEFAULT NULL COMMENT '访问凭证到期时间',
  `refresh_expires_at` datetime DEFAULT NULL COMMENT '刷新凭证到期时间',
  `credential_fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配对时认证状态内部摘要',
  `capabilities` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '设备实际能力与版本',
  `allowed_capabilities` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '配对允许能力集合',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '最近心跳时间',
  `busy_task_id` bigint(20) DEFAULT NULL COMMENT '正在执行的任务',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建用户',
  `create_time` datetime DEFAULT NULL COMMENT '创建审计时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '修改用户',
  `update_time` datetime DEFAULT NULL COMMENT '修改审计时间',
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `uk_engineering_device_access` (`access_hash`),
  UNIQUE KEY `uk_engineering_device_refresh` (`refresh_hash`),
  KEY `idx_engineering_device_owner` (`tenant_id`,`user_id`,`status`,`last_heartbeat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI工程设备';

CREATE TABLE `ai_engineering_task` (
  `task_id` bigint(20) NOT NULL COMMENT '任务编号',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `user_id` bigint(20) NOT NULL COMMENT '任务所有者',
  `device_id` bigint(20) NOT NULL COMMENT '指定执行设备',
  `session_id` bigint(20) DEFAULT NULL COMMENT '关联会话编号',
  `request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务端聊天请求标识',
  `parent_task_id` bigint(20) DEFAULT NULL COMMENT '原任务编号',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '受控动作',
  `idempotency_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '用户作用域幂等键',
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规范请求摘要',
  `request_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '有界结构化请求',
  `state` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态',
  `stage` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前阶段',
  `attempt` int(11) NOT NULL DEFAULT '0' COMMENT '领取尝试编号',
  `revision` bigint(20) NOT NULL DEFAULT '0' COMMENT '比较交换修订号',
  `lease_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租约凭证摘要',
  `lease_expires_at` datetime DEFAULT NULL COMMENT '租约到期时间',
  `deadline_at` datetime DEFAULT NULL COMMENT '运行截止时间',
  `waiting_expires_at` datetime DEFAULT NULL COMMENT '等待设备截止时间',
  `cancel_requested` tinyint(1) NOT NULL DEFAULT '0' COMMENT '运行取消请求',
  `step_journal` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '有界步骤回执日志',
  `reserved_bytes` bigint(20) NOT NULL DEFAULT '0' COMMENT '已确认及预留字节总量',
  `reserved_files` int(11) NOT NULL DEFAULT '0' COMMENT '已确认及预留文件数量',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安全错误类别',
  `error_message` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '中文安全错误摘要',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '修改时间',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建用户',
  `create_time` datetime DEFAULT NULL COMMENT '创建审计时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '修改用户',
  `update_time` datetime DEFAULT NULL COMMENT '修改审计时间',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_engineering_task_owner` (`tenant_id`,`user_id`,`idempotency_key`),
  KEY `idx_engineering_task_device` (`tenant_id`,`user_id`,`device_id`,`state`),
  KEY `idx_engineering_task_expiry` (`state`,`lease_expires_at`,`waiting_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI工程任务';

CREATE TABLE `rpt_acl` (
  `acl_id` bigint(20) NOT NULL COMMENT '资源授权ID',
  `resource_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源类型',
  `resource_id` bigint(20) NOT NULL COMMENT '被授权报表资源ID',
  `subject_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '授权主体类型（用户、角色或部门）',
  `subject_id` bigint(20) NOT NULL COMMENT '被授权用户、角色或部门ID',
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源授权操作（查看、编辑、发布、运行、导出、授权或管理）',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`acl_id`),
  UNIQUE KEY `uk_report_acl` (`tenant_id`,`resource_type`,`resource_id`,`subject_type`,`subject_id`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表资源访问授权';

CREATE TABLE `rpt_artifact_manifest` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `store_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有存储实例标识',
  `reference` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有文件逻辑引用',
  `state` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有文件生命周期状态',
  `reserved_bytes` bigint(20) NOT NULL DEFAULT '0' COMMENT '写入预留容量（字节）',
  `content_length` bigint(20) NOT NULL DEFAULT '0' COMMENT '已提交文件大小（字节）',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内容SHA-256校验和',
  `lease_owner` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '当前执行租约持有者',
  `staging_path` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '尚未提交文件的暂存路径',
  `lease_until` datetime NOT NULL COMMENT '当前执行租约到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`tenant_id`,`store_id`,`reference`),
  KEY `idx_rpt_manifest_recovery` (`store_id`,`state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表私有文件生命周期清单';

CREATE TABLE `rpt_asset` (
  `asset_id` bigint(20) NOT NULL COMMENT '私有图片资源ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有图片资源唯一编码',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有图片资源名称',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有图片资源草稿定义JSON',
  `revision` bigint(20) NOT NULL DEFAULT '1' COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '私有图片资源生命周期状态',
  `create_dept` bigint(20) NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`asset_id`),
  UNIQUE KEY `uk_rpt_asset_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表私有图片资源定义';

CREATE TABLE `rpt_asset_version` (
  `version_id` bigint(20) NOT NULL COMMENT '私有图片资源发布版本ID',
  `asset_id` bigint(20) NOT NULL COMMENT '私有图片资源ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `version_no` int(11) NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '不可变私有图片资源定义JSON',
  `dependency_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_asset_version_number` (`tenant_id`,`asset_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表私有图片资源版本';

CREATE TABLE `rpt_audit` (
  `audit_id` bigint(20) NOT NULL COMMENT '操作审计ID',
  `actor_id` bigint(20) NOT NULL COMMENT '操作用户ID',
  `resource_id` bigint(20) DEFAULT NULL COMMENT '本次操作资源ID',
  `action` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '审计操作类型',
  `outcome` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '审计操作结果',
  `details_json` text COLLATE utf8mb4_general_ci COMMENT '操作审计详情JSON',
  `trace_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作追踪标识',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`audit_id`),
  KEY `idx_report_audit_owner` (`tenant_id`,`actor_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表操作审计';

CREATE TABLE `rpt_batch` (
  `batch_id` bigint(20) NOT NULL COMMENT '报表批次ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表批次唯一编码',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表批次名称',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表批次草稿定义JSON',
  `revision` bigint(20) NOT NULL DEFAULT '1' COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '报表批次生命周期状态',
  `create_dept` bigint(20) NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_rpt_batch_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表批次定义';

CREATE TABLE `rpt_batch_item` (
  `batch_id` bigint(20) NOT NULL COMMENT '报表批次ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `item_no` int(11) NOT NULL COMMENT '批次内执行项序号',
  `report_version_id` bigint(20) NOT NULL COMMENT '固定报表发布版本ID',
  `parameters_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定运行参数JSON',
  `job_id` bigint(20) DEFAULT NULL COMMENT '导出任务ID',
  `state` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '批次执行项状态',
  `error_code` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控错误码',
  PRIMARY KEY (`batch_id`,`item_no`),
  KEY `idx_rpt_batch_tenant` (`tenant_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表批次执行明细';

CREATE TABLE `rpt_batch_version` (
  `version_id` bigint(20) NOT NULL COMMENT '报表批次发布版本ID',
  `batch_id` bigint(20) NOT NULL COMMENT '报表批次ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `version_no` int(11) NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '不可变报表批次定义JSON',
  `dependency_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_batch_version_number` (`tenant_id`,`batch_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表批次发布版本';

CREATE TABLE `rpt_catalog` (
  `catalog_id` bigint(20) NOT NULL COMMENT '报表目录ID',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父报表目录ID',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表目录名称',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '目录显示排序',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`catalog_id`),
  UNIQUE KEY `uk_report_catalog_name` (`tenant_id`,`parent_id`,`name`),
  KEY `idx_report_catalog_tenant` (`tenant_id`,`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表目录';

CREATE TABLE `rpt_dashboard` (
  `dashboard_id` bigint(20) NOT NULL COMMENT '仪表盘ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '仪表盘唯一编码',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '仪表盘名称',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '仪表盘草稿定义JSON',
  `revision` bigint(20) NOT NULL DEFAULT '1' COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '仪表盘生命周期状态',
  `create_dept` bigint(20) NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dashboard_id`),
  UNIQUE KEY `uk_rpt_dashboard_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表仪表盘定义';

CREATE TABLE `rpt_dashboard_favorite` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `dashboard_id` bigint(20) NOT NULL COMMENT '仪表盘ID',
  PRIMARY KEY (`tenant_id`,`user_id`,`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户仪表盘收藏';

CREATE TABLE `rpt_dashboard_version` (
  `version_id` bigint(20) NOT NULL COMMENT '仪表盘发布版本ID',
  `dashboard_id` bigint(20) NOT NULL COMMENT '仪表盘ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `version_no` int(11) NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '不可变仪表盘定义JSON',
  `dependency_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_dashboard_version_number` (`tenant_id`,`dashboard_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表仪表盘发布版本';

CREATE TABLE `rpt_dataset` (
  `dataset_id` bigint(20) NOT NULL COMMENT '数据集ID',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集唯一编码',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集名称',
  `provider_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '代码数据提供者编码',
  `published_version_id` bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '数据集启用状态',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dataset_id`),
  UNIQUE KEY `uk_report_dataset_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表数据集';

CREATE TABLE `rpt_dataset_version` (
  `version_id` bigint(20) NOT NULL COMMENT '数据集契约版本ID',
  `dataset_id` bigint(20) NOT NULL COMMENT '数据集ID',
  `version_no` int(11) NOT NULL COMMENT '资源内递增版本号',
  `provider_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '代码数据提供者编码',
  `provider_version` int(11) NOT NULL COMMENT '代码数据提供者契约版本',
  `descriptor_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集字段与参数契约JSON',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_dataset_version` (`tenant_id`,`dataset_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表数据集契约版本';

CREATE TABLE `rpt_definition` (
  `report_id` bigint(20) NOT NULL COMMENT '报表定义ID',
  `catalog_id` bigint(20) DEFAULT NULL COMMENT '所属报表目录ID',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表唯一编码',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表名称',
  `kind` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表类型（表格、单据或交叉报表）',
  `draft_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表DSL草稿JSON',
  `draft_revision` bigint(20) NOT NULL DEFAULT '1' COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '报表生命周期状态',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_report_code` (`tenant_id`,`code`),
  KEY `idx_report_definition_catalog` (`tenant_id`,`catalog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表定义与草稿';

CREATE TABLE `rpt_delivery` (
  `delivery_id` bigint(20) NOT NULL COMMENT '私有交付ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `execution_id` bigint(20) NOT NULL COMMENT '订阅执行ID',
  `subscription_id` bigint(20) NOT NULL COMMENT '报表订阅ID',
  `definition_version_id` bigint(20) NOT NULL COMMENT '固定订阅定义版本ID',
  `recipient_id` bigint(20) NOT NULL COMMENT '接收人用户ID',
  `job_id` bigint(20) NOT NULL COMMENT '导出任务ID',
  `state` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PREPARING' COMMENT '接收人私有交付状态',
  `notification_state` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DISABLED' COMMENT '交付通知状态',
  `notification_attempts` int(11) NOT NULL DEFAULT '0' COMMENT '已尝试通知次数',
  `expires_at` datetime(6) NOT NULL COMMENT '交付文件有效期截止时间',
  `claimed_at` datetime(6) DEFAULT NULL COMMENT '接收人领取时间',
  `error_code` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控错误码',
  PRIMARY KEY (`delivery_id`),
  UNIQUE KEY `uk_rpt_delivery_execution` (`tenant_id`,`execution_id`),
  KEY `idx_rpt_delivery_recipient` (`tenant_id`,`recipient_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表接收人私有交付';

CREATE TABLE `rpt_dispatch_gate` (
  `scope_key` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公平调度作用域标识',
  `last_claim` datetime DEFAULT NULL COMMENT '最近一次领取任务的调度标记',
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表公平调度领取控制';

CREATE TABLE `rpt_export_job` (
  `job_id` bigint(20) NOT NULL COMMENT '导出任务ID',
  `actor_id` bigint(20) NOT NULL COMMENT '操作用户ID',
  `run_id` bigint(20) NOT NULL COMMENT '报表运行ID',
  `format` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出文件格式',
  `idempotency_key` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '请求幂等键',
  `request_hash` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '规范化请求参数指纹',
  `state` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出任务状态',
  `execution_phase` varchar(24) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'QUEUED' COMMENT '导出任务执行阶段',
  `processed_rows` bigint(20) NOT NULL DEFAULT '0' COMMENT '已处理数据行数',
  `total_rows` bigint(20) DEFAULT NULL COMMENT '预计处理总行数',
  `retry_round` int(11) NOT NULL DEFAULT '0' COMMENT '任务重试轮次',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '执行器最近心跳时间',
  `attempts` int(11) NOT NULL DEFAULT '0' COMMENT '已尝试执行次数',
  `lease_owner` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前执行租约持有者',
  `lease_until` datetime DEFAULT NULL COMMENT '当前执行租约到期时间',
  `cancel_requested` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否请求取消任务',
  `file_ref` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '私有导出文件逻辑引用',
  `file_size` bigint(20) DEFAULT NULL COMMENT '导出文件大小（字节）',
  `expires_at` datetime NOT NULL COMMENT '导出文件有效期截止时间',
  `error_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控错误码',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`job_id`),
  UNIQUE KEY `uk_report_export_request` (`tenant_id`,`actor_id`,`idempotency_key`),
  KEY `idx_report_export_claim` (`state`,`lease_until`),
  KEY `idx_report_export_owner` (`tenant_id`,`actor_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表异步导出任务';

CREATE TABLE `rpt_font_resource` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `font_ref` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有字体文件逻辑引用',
  `lease_until` datetime NOT NULL COMMENT '当前执行租约到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tenant_id`,`checksum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表共享字体资源';

CREATE TABLE `rpt_plan_font` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `plan_ref` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有渲染计划逻辑引用',
  `font_ref` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有字体文件逻辑引用',
  `font_checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '字体文件SHA-256校验和',
  PRIMARY KEY (`tenant_id`,`plan_ref`),
  KEY `idx_rpt_plan_font` (`tenant_id`,`font_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表渲染计划字体引用';

CREATE TABLE `rpt_query_dataset_binding` (
  `dataset_version_id` bigint(20) NOT NULL COMMENT '固定数据集版本ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `query_version_id` bigint(20) NOT NULL COMMENT '固定受控查询版本ID',
  `catalog_version` int(11) NOT NULL COMMENT '批准的逻辑目录版本',
  `query_checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '受控查询定义SHA-256校验和',
  `catalog_checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '逻辑目录内容SHA-256校验和',
  PRIMARY KEY (`dataset_version_id`),
  KEY `idx_rpt_query_dataset_tenant` (`tenant_id`,`query_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='受控查询与数据集版本绑定';

CREATE TABLE `rpt_query_definition` (
  `query_id` bigint(20) NOT NULL COMMENT '受控查询定义ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '受控查询唯一编码',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '受控查询名称',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '受控查询草稿定义JSON',
  `revision` bigint(20) NOT NULL DEFAULT '1' COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '受控查询生命周期状态',
  `create_dept` bigint(20) NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`query_id`),
  UNIQUE KEY `uk_rpt_query_definition_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表受控查询定义';

CREATE TABLE `rpt_query_version` (
  `version_id` bigint(20) NOT NULL COMMENT '受控查询发布版本ID',
  `query_id` bigint(20) NOT NULL COMMENT '受控查询定义ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `version_no` int(11) NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '不可变受控查询定义JSON',
  `dependency_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_query_version_number` (`tenant_id`,`query_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表受控查询发布版本';

CREATE TABLE `rpt_quota` (
  `scope_key` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储配额作用域标识',
  `limit_bytes` bigint(20) NOT NULL COMMENT '允许使用的存储容量上限（字节）',
  `limit_objects` bigint(20) NOT NULL COMMENT '允许保存的文件数量上限',
  `used_bytes` bigint(20) NOT NULL DEFAULT '0' COMMENT '已占用存储容量（字节）',
  `object_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '已占用文件数量',
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表私有存储容量配额';

CREATE TABLE `rpt_refresh_quota` (
  `scope_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '刷新限流作用域标识',
  `window_start` datetime NOT NULL COMMENT '当前限流时间窗口起点',
  `counter` int(11) NOT NULL DEFAULT '0' COMMENT '当前时间窗口累计次数',
  PRIMARY KEY (`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表刷新频率配额';

CREATE TABLE `rpt_resource_artifact` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `resource_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源类型',
  `version_id` bigint(20) NOT NULL COMMENT '不可变版本ID',
  `artifact_ref` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有文件逻辑引用',
  PRIMARY KEY (`tenant_id`,`resource_type`,`version_id`),
  KEY `idx_rpt_resource_artifact` (`tenant_id`,`artifact_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表资源版本与私有文件关联';

CREATE TABLE `rpt_run` (
  `run_id` bigint(20) NOT NULL COMMENT '报表运行ID',
  `actor_id` bigint(20) NOT NULL COMMENT '操作用户ID',
  `report_id` bigint(20) NOT NULL COMMENT '报表定义ID',
  `version_id` bigint(20) NOT NULL COMMENT '不可变版本ID',
  `mode` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表运行模式',
  `state` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表运行状态',
  `parameters_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定运行参数JSON',
  `scope_hash` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务端权限与业务范围指纹',
  `snapshot_ref` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '私有数据快照逻辑引用',
  `plan_ref` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '私有渲染计划逻辑引用',
  `row_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '数据快照总行数',
  `data_time` datetime NOT NULL COMMENT '业务数据快照时间',
  `expires_at` datetime NOT NULL COMMENT '运行快照有效期截止时间',
  `error_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控错误码',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`run_id`),
  KEY `idx_report_run_owner` (`tenant_id`,`actor_id`,`create_time`),
  KEY `idx_report_run_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表运行记录与私有快照';

CREATE TABLE `rpt_snapshot_index` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `snapshot_ref` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有数据快照逻辑引用',
  `index_ref` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '私有快照索引逻辑引用',
  `snapshot_checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据快照SHA-256校验和',
  `index_checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '快照索引SHA-256校验和',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tenant_id`,`snapshot_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表数据快照与索引关联';

CREATE TABLE `rpt_subscription` (
  `subscription_id` bigint(20) NOT NULL COMMENT '报表订阅ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表订阅唯一编码',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表订阅名称',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '报表订阅草稿定义JSON',
  `revision` bigint(20) NOT NULL DEFAULT '1' COMMENT '草稿修订号（乐观锁）',
  `published_version_id` bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT' COMMENT '报表订阅生命周期状态',
  `create_dept` bigint(20) NOT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`subscription_id`),
  UNIQUE KEY `uk_rpt_subscription_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表订阅定义';

CREATE TABLE `rpt_subscription_occurrence` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `subscription_id` bigint(20) NOT NULL COMMENT '报表订阅ID',
  `scheduled_key` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订阅触发幂等键',
  `definition_version_id` bigint(20) NOT NULL COMMENT '固定订阅定义版本ID',
  `scheduled_instant` datetime(6) NOT NULL COMMENT '本次触发计划时间',
  `event_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订阅触发事件类型',
  PRIMARY KEY (`tenant_id`,`subscription_id`,`scheduled_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表订阅触发去重记录';

CREATE TABLE `rpt_subscription_run` (
  `execution_id` bigint(20) NOT NULL COMMENT '订阅执行ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `subscription_id` bigint(20) NOT NULL COMMENT '报表订阅ID',
  `definition_version_id` bigint(20) NOT NULL COMMENT '固定订阅定义版本ID',
  `scheduled_key` varchar(80) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订阅触发幂等键',
  `scheduled_instant` datetime(6) NOT NULL COMMENT '本次触发计划时间',
  `event_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订阅触发事件类型',
  `recipient_id` bigint(20) NOT NULL COMMENT '接收人用户ID',
  `job_id` bigint(20) DEFAULT NULL COMMENT '导出任务ID',
  `state` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订阅执行与重试状态',
  `attempts` int(11) NOT NULL DEFAULT '0' COMMENT '已尝试执行次数',
  `next_retry_at` datetime(6) DEFAULT NULL COMMENT '下一次允许重试时间',
  `error_code` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '受控错误码',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`execution_id`),
  UNIQUE KEY `uk_rpt_subscription_occurrence` (`tenant_id`,`subscription_id`,`scheduled_key`,`recipient_id`),
  KEY `idx_rpt_subscription_recipient` (`tenant_id`,`recipient_id`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表订阅执行与重试记录';

CREATE TABLE `rpt_subscription_schedule` (
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `subscription_id` bigint(20) NOT NULL COMMENT '报表订阅ID',
  `definition_version_id` bigint(20) NOT NULL COMMENT '固定订阅定义版本ID',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用订阅调度',
  `next_at` datetime(6) NOT NULL COMMENT '下一次计划触发时间',
  `last_error` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近一次调度错误码',
  PRIMARY KEY (`tenant_id`,`subscription_id`),
  KEY `idx_rpt_subscription_due` (`enabled`,`next_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表订阅调度状态';

CREATE TABLE `rpt_subscription_version` (
  `version_id` bigint(20) NOT NULL COMMENT '报表订阅发布版本ID',
  `subscription_id` bigint(20) NOT NULL COMMENT '报表订阅ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `version_no` int(11) NOT NULL COMMENT '资源内递增版本号',
  `definition_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '不可变报表订阅定义JSON',
  `dependency_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定依赖版本与范围指纹JSON',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_rpt_subscription_version_number` (`tenant_id`,`subscription_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表订阅发布版本';

CREATE TABLE `rpt_version` (
  `version_id` bigint(20) NOT NULL COMMENT '报表发布版本ID',
  `report_id` bigint(20) NOT NULL COMMENT '报表定义ID',
  `version_no` int(11) NOT NULL COMMENT '资源内递增版本号',
  `schema_version` int(11) NOT NULL COMMENT '报表DSL结构版本',
  `engine_version` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '固定报表执行引擎版本',
  `dsl_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '不可变报表DSL定义JSON',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容SHA-256校验和',
  `dataset_version_id` bigint(20) NOT NULL COMMENT '固定数据集版本ID',
  `tenant_id` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '租户隔离标识',
  `create_dept` bigint(20) DEFAULT NULL COMMENT '创建人部门ID',
  `create_by` bigint(20) NOT NULL COMMENT '创建人用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新人用户ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_report_version` (`tenant_id`,`report_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表不可变发布版本';

-- 菜单元数据事务；DDL不依赖此事务回滚。
START TRANSACTION;
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
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23030,'受控查询',23000,7,'query','report/query/index',1,1,'C','0','0','report:query:list','search',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23030);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23031,'新建查询',23030,1,'#',NULL,1,1,'F','0','0','report:query:add','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23031);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23032,'编辑查询',23030,2,'#',NULL,1,1,'F','0','0','report:query:edit','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23032);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23033,'发布查询',23030,3,'#',NULL,1,1,'F','0','0','report:query:publish','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23033);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23040,'交叉报表',23000,8,'pivot','report/pivot/index',1,1,'C','0','0','report:definition:edit','chart',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23040);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23050,'私有打印图片',23000,9,'assets','report/assets/index',1,1,'C','0','0','report:asset:list','image',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23050);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23051,'管理打印图片',23050,1,'#',NULL,1,1,'F','0','0','report:asset:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23051);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23060,'批量单据',23000,10,'batch','report/batch/index',1,1,'C','0','0','report:batch:list','print',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23060);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23061,'管理单据批次',23060,1,'#',NULL,1,1,'F','0','0','report:batch:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23061);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23070,'个人仪表盘',23000,11,'dashboard','report/dashboard/index',1,1,'C','0','0','report:dashboard:list','dashboard',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23070);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23071,'编辑仪表盘',23070,1,'#',NULL,1,1,'F','0','0','report:dashboard:edit','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23071);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23072,'发布仪表盘',23070,2,'#',NULL,1,1,'F','0','0','report:dashboard:publish','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23072);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23080,'报表订阅',23000,12,'subscription','report/subscription/index',1,1,'C','0','0','report:subscription:list','time',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23080);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23081,'管理订阅',23080,1,'#',NULL,1,1,'F','0','0','report:subscription:manage','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23081);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23082,'发布订阅',23080,2,'#',NULL,1,1,'F','0','0','report:subscription:publish','#',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23082);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by) SELECT 23090,'我的报表交付',23000,13,'delivery','report/delivery/index',1,1,'C','0','0','report:runtime:export','download',1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=23090);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon) SELECT 24000,'工程设计',1706,20,'engineering',NULL,'M','0','0',NULL,'tool' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=24000);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon) SELECT 24001,'工程设备',24000,1,'device','ai/engineering/device/index','C','0','0','ai:engineering:device','tool' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=24001);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon) SELECT 24002,'工程任务',24000,2,'task','ai/engineering/task/index','C','0','0','ai:engineering:query','tool' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=24002);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon) SELECT 24003,'创建工程任务',24002,1,'#',NULL,'F','0','0','ai:engineering:task','#' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=24003);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon) SELECT 24004,'取消工程任务',24002,2,'#',NULL,'F','0','0','ai:engineering:task','#' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=24004);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon) SELECT 24005,'下载工程产物',24002,3,'#',NULL,'F','0','0','ai:engineering:download','#' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=24005);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon) SELECT 24006,'设备配对解绑',24001,1,'#',NULL,'F','0','0','ai:engineering:device','#' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=24006);
-- 仅超级管理员菜单授权，不修改普通角色已有权限。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.menu_id IN (23000,23001,23002,23003,23004,23005,23006,23010,23011,23012,23013,23014,23015,23016,23017,23018,23019,23030,23031,23032,23033,23040,23050,23051,23060,23061,23070,23071,23072,23080,23081,23082,23090,24000,24001,24002,24003,24004,24005,24006)
WHERE r.role_key='superadmin' AND r.status='0' AND r.del_flag='0'
AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=r.role_id AND x.menu_id=m.menu_id);
-- 工程工具保持默认停用，私桶/配置验真后由管理员开启。
INSERT INTO ai_tool(id,tenant_id,tool_name,display_name,description,tool_type,status,del_flag)
SELECT 24000001,'000000','easyeda_engineering','嘉立创EDA工程工具','受控草稿、检查与私有产物任务；需显式设备配对','BUILTIN','1','0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_tool WHERE id=24000001 OR (tenant_id='000000' AND tool_name='easyeda_engineering'));
-- 已核对的旧积木菜单及关联；不选择其他业务报表菜单。
DELETE FROM sys_role_menu WHERE menu_id IN (3020,3021,3022,2053846181246812161);
DELETE FROM sys_menu WHERE menu_id IN (3020,3021,3022,2053846181246812161);
-- 精确移除旧ID，保留套餐中的其他权限与原顺序。
UPDATE sys_tenant_package
SET menu_ids=TRIM(BOTH ',' FROM REPLACE(REPLACE(REPLACE(REPLACE(CONCAT(',',COALESCE(menu_ids,''),','),
',3020,',','),',3021,',','),',3022,',','),',2053846181246812161,',','))
WHERE FIND_IN_SET('3020',menu_ids) OR FIND_IN_SET('3021',menu_ids)
OR FIND_IN_SET('3022',menu_ids) OR FIND_IN_SET('2053846181246812161',menu_ids);
-- 生产SnailJob命名空间已存在；先登记停用，确认新版执行器在线后再启用。
INSERT INTO sj_job(namespace_id,biz_id,group_name,job_name,args_str,args_type,next_trigger_at,
job_status,task_type,route_key,executor_type,executor_info,trigger_type,trigger_interval,
block_strategy,executor_timeout,max_retry_times,parallel_num,retry_interval,bucket_index,description)
SELECT 'prod','nexus-report-subscription',j.group_name,'报表订阅调度',NULL,1,
UNIX_TIMESTAMP(DATE_FORMAT(DATE_ADD(NOW(),INTERVAL 1 MINUTE),'%Y-%m-%d %H:%i:00'))*1000,
0,1,j.route_key,1,'reportSubscriptionDispatch',3,'0 * * * * ?',1,240,0,1,1,j.bucket_index,
'自研报表订阅调度；仅处理已发布且有权限的订阅，每分钟检查一次'
FROM sj_job j WHERE j.id=3 AND j.namespace_id='prod' AND j.group_name='ruoyi_group' AND j.deleted=0
AND NOT EXISTS (SELECT 1 FROM sj_job x WHERE x.namespace_id='prod'
AND x.group_name='ruoyi_group' AND x.executor_info='reportSubscriptionDispatch' AND x.deleted=0);
COMMIT;
-- 仅清理本轮用户授权的19张积木（含3张明确的积木演示）物理表；先明细后主表，不禁用外键校验。

DROP TABLE IF EXISTS `jimu_dict_item`;
DROP TABLE IF EXISTS `jimu_report_db_field`;
DROP TABLE IF EXISTS `jimu_report_db_param`;
DROP TABLE IF EXISTS `jimu_report_export_log`;
DROP TABLE IF EXISTS `jimu_report_export_job`;
DROP TABLE IF EXISTS `jimu_report_ext_data`;
DROP TABLE IF EXISTS `jimu_report_link`;
DROP TABLE IF EXISTS `jimu_report_map`;
DROP TABLE IF EXISTS `jimu_report_share`;
DROP TABLE IF EXISTS `jimu_report_sheet`;
DROP TABLE IF EXISTS `jimu_report_icon_lib`;
DROP TABLE IF EXISTS `jimu_report_db`;
DROP TABLE IF EXISTS `jimu_report_data_source`;
DROP TABLE IF EXISTS `jimu_report_category`;
DROP TABLE IF EXISTS `jimu_report`;
DROP TABLE IF EXISTS `jimu_dict`;
DROP TABLE IF EXISTS `test_monthly_report`;
DROP TABLE IF EXISTS `tmp_report_data_1`;
DROP TABLE IF EXISTS `tmp_report_data_income`;
-- 部署后核对37张新表、40个新菜单、旧积木表为0，以及业务库结构/关键计数。
