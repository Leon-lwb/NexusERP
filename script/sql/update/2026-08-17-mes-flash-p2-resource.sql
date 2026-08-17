-- ============================================================
-- MES 刷写能力抽象 P2: 资源池(MAC/SN) + 配置文件档案 + 刷写类型种子补全
-- 库: MES 业务表 -> ry-erp 业务库; 字典/菜单/编码 -> ry-vue 系统库
-- 日期: 2026-08-17
-- 配套: doc/mes/plans/2026-08-17-mes-flash-abstraction-plan.md (§3.5/§3.6)
-- 前置: 2026-08-17-mes-flash-abstraction.sql (P0) 已执行
-- 幂等: CREATE TABLE IF NOT EXISTS / INSERT IGNORE 可重入
-- 段位避让: 字典 600908/600909(6009xx); 菜单 4296-4310(4280-4295 P0已占); 编码 rule_id 292-293(289-291 P0已占)
-- ============================================================

-- ==================== 一、MES 业务库: 资源池主表 + 明细 + 配置文件档案 ====================

CREATE TABLE IF NOT EXISTS `mes_resource_pool` (
  `pool_id`        bigint(20)   NOT NULL COMMENT '资源池ID',
  `tenant_id`      varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `pool_code`      varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源池编码(自动编码 MES_POOL_CODE)',
  `pool_name`      varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源池名称',
  `resource_type`  varchar(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型: MAC/SN/CERT',
  `source`         varchar(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INTERNAL' COMMENT '来源: INTERNAL(内部导入)/EXTERNAL(外部REST)',
  `external_config` text        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '外部REST配置JSON(baseUrl/路径/鉴权, 经SSRF校验)',
  `alloc_strategy` varchar(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIFO' COMMENT '分配策略: FIFO/随机',
  `total_count`    int(11)      NULL DEFAULT NULL COMMENT '总数量',
  `used_count`     int(11)      NOT NULL DEFAULT 0 COMMENT '已分配数量',
  `status`         varchar(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `create_dept`    bigint(20)   NULL DEFAULT NULL,
  `create_by`      bigint(20)   NULL DEFAULT NULL,
  `create_time`    datetime     NULL DEFAULT NULL,
  `update_by`      bigint(20)   NULL DEFAULT NULL,
  `update_time`    datetime     NULL DEFAULT NULL,
  `del_flag`       char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark`         varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`pool_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_pool_code`(`tenant_id`, `pool_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_resource_type`(`resource_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷写资源池(MAC/SN等)' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `mes_resource_item` (
  `item_id`        bigint(20)   NOT NULL COMMENT '资源项ID',
  `tenant_id`      varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `pool_id`        bigint(20)   NOT NULL COMMENT '资源池ID',
  `resource_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源值(如MAC 00:1A:2B:3C:4D:5E)',
  `status`         varchar(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/ALLOCATED/USED/SCRAPPED',
  `bound_sn_id`    bigint(20)   NULL DEFAULT NULL COMMENT '已绑定SN ID(一SN绑一资源)',
  `bound_time`     datetime     NULL DEFAULT NULL COMMENT '绑定时间',
  `create_dept`    bigint(20)   NULL DEFAULT NULL,
  `create_by`      bigint(20)   NULL DEFAULT NULL,
  `create_time`    datetime     NULL DEFAULT NULL,
  `update_by`      bigint(20)   NULL DEFAULT NULL,
  `update_time`    datetime     NULL DEFAULT NULL,
  `del_flag`       char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark`         varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`item_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_pool_value`(`tenant_id`, `pool_id`, `resource_value`) USING BTREE,
  INDEX `idx_pool_status`(`pool_id`, `status`) USING BTREE,
  INDEX `idx_bound_sn`(`bound_sn_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源池明细' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `mes_content_file` (
  `content_file_id` bigint(20)  NOT NULL COMMENT '配置文件ID',
  `tenant_id`       varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `content_code`    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置编码(自动编码 MES_CONFIG_CODE)',
  `content_name`    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `content_type`    varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CONFIG_FILE' COMMENT '配置类型: CONFIG_FILE(可扩展校准/证书)',
  `material_id`     bigint(20)  NOT NULL COMMENT '适用物料ID',
  `material_code`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `material_name`   varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `version`         varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号',
  `oss_id`          bigint(20)  NULL DEFAULT NULL COMMENT '配置文件OSS文件ID',
  `file_md5`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件MD5',
  `file_size`       bigint(20)  NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `release_note`    text        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status`          varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `is_default`      char(1)     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '物料默认版本(0否/1是)',
  `published_by`    bigint(20)  NULL DEFAULT NULL,
  `publish_time`    datetime    NULL DEFAULT NULL,
  `create_dept`     bigint(20)  NULL DEFAULT NULL,
  `create_by`       bigint(20)  NULL DEFAULT NULL,
  `create_time`     datetime    NULL DEFAULT NULL,
  `update_by`       bigint(20)  NULL DEFAULT NULL,
  `update_time`     datetime    NULL DEFAULT NULL,
  `del_flag`        char(1)     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  `remark`          varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`content_file_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_content_code`(`tenant_id`, `content_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '刷写内容档案(配置文件等, 审批发布)' ROW_FORMAT = DYNAMIC;

-- ==================== 二、ry-vue 系统库: mes_flash_type 补种子(P0仅firmware_file, P2补config_file/mac/sn) ====================
INSERT IGNORE INTO `mes_flash_type`(`flash_type_id`, `tenant_id`, `type_code`, `type_name`, `source_type`, `source_provider`, `verify_strategy`, `gate_enabled`, `status`, `sort_order`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(2091000000000006002, '000000', 'config_file', '刷写配置文件', 'FILE', 'configFileFlashProvider', 'MD5_MATCH', '1', 'ENABLED', 2, 103, 1, NOW(), '配置文件(审批发布), MD5校验'),
(2091000000000006003, '000000', 'mac',         '刷写MAC',     'POOL', 'macPoolFlashProvider', 'RESOURCE_BOUND', '1', 'ENABLED', 3, 103, 1, NOW(), 'MAC资源池原子分配, 一SN绑一MAC'),
(2091000000000006004, '000000', 'sn',          '刷写SN',      'POOL', 'snPoolFlashProvider', 'RESOURCE_BOUND', '1', 'ENABLED', 4, 103, 1, NOW(), 'SN资源池原子分配, 一SN绑一SN');

-- ==================== 三、ry-vue 系统库: 字典 ====================
INSERT IGNORE INTO `sys_dict_type`(`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(600908, '000000', '资源类型',     'mes_resource_type',   103, 1, NOW(), 'MES刷写资源池类型(MAC/SN/CERT)'),
(600909, '000000', '资源项状态',   'mes_resource_status', 103, 1, NOW(), 'MES资源项状态(AVAILABLE/ALLOCATED/USED/SCRAPPED)');

INSERT IGNORE INTO `sys_dict_data`(`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(600971, '000000', 1, 'MAC',  'MAC',  'mes_resource_type', 'primary', 'Y', 103, 1, NOW(), 'MAC地址池'),
(600972, '000000', 2, 'SN',   'SN',   'mes_resource_type', 'success', 'N', 103, 1, NOW(), 'SN资源池'),
(600973, '000000', 3, '证书', 'CERT', 'mes_resource_type', 'warning', 'N', 103, 1, NOW(), '证书资源池'),
(600981, '000000', 1, '可用',     'AVAILABLE', 'mes_resource_status', 'success', 'Y', 103, 1, NOW(), '未分配'),
(600982, '000000', 2, '已分配',   'ALLOCATED', 'mes_resource_status', 'warning', 'N', 103, 1, NOW(), '已绑定SN待刷写'),
(600983, '000000', 3, '已使用',   'USED',      'mes_resource_status', 'primary', 'N', 103, 1, NOW(), '已刷写完成'),
(600984, '000000', 4, '已作废',   'SCRAPPED',  'mes_resource_status', 'danger',  'N', 103, 1, NOW(), '作废不可用');

-- ==================== 四、ry-vue 系统库: 菜单/权限(4296-4310, 4280刷写管理下) ====================
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(4296, '资源池管理', 4280, 5, 'pool', 'mes/flash/pool/index', NULL, 1, 0, 'C', '0', '0', 'mes:flash:pool:list', 'coin', 103, 1, NOW(), 'MAC/SN资源池(导入/分配/状态)'),
(4297, '资源池查询', 4296, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:pool:query',  '#', 103, 1, NOW(), ''),
(4298, '资源池新增', 4296, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:pool:add',    '#', 103, 1, NOW(), ''),
(4299, '资源池修改', 4296, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:pool:edit',   '#', 103, 1, NOW(), ''),
(4300, '资源池删除', 4296, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:pool:remove', '#', 103, 1, NOW(), ''),
(4301, '资源导入',   4296, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:pool:import',  '#', 103, 1, NOW(), '批量导入资源项'),
(4302, '配置档案',   4280, 6, 'content', 'mes/flash/content/index', NULL, 1, 0, 'C', '0', '0', 'mes:flash:content:list', 'documentation', 103, 1, NOW(), '配置文件档案(审批发布)'),
(4303, '配置查询',   4302, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:content:query',  '#', 103, 1, NOW(), ''),
(4304, '配置新增',   4302, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:content:add',    '#', 103, 1, NOW(), ''),
(4305, '配置修改',   4302, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:content:edit',   '#', 103, 1, NOW(), ''),
(4306, '配置删除',   4302, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:content:remove', '#', 103, 1, NOW(), ''),
(4307, '配置发布',   4302, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:flash:content:release', '#', 103, 1, NOW(), 'DRAFT→RELEASED');

-- ==================== 五、ry-vue 系统库: 编码规则 ====================
INSERT IGNORE INTO `sys_auto_code_rule`(`rule_id`, `tenant_id`, `rule_code`, `rule_name`, `rule_desc`, `max_length`, `is_padded`, `padded_char`, `padded_method`, `enable_flag`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(292, '000000', 'MES_POOL_CODE',   'MES资源池编码', 'PL+日期+3位日循环流水号', 15, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0'),
(293, '000000', 'MES_CONFIG_CODE', 'MES配置文件编码', 'CF+日期+3位日循环流水号', 15, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0');

INSERT IGNORE INTO `sys_auto_code_part`(`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
-- MES_POOL_CODE (292)
(2058014518088437810, '000000', 292, 1, 'FIXCHAR',  'PREFIX', '前缀',   2, NULL, NULL, 'PL', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0'),
(2058014518088437811, '000000', 292, 2, 'NOWDATE',  'DATE',   '日期',   8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0'),
(2058014518088437812, '000000', 292, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0'),
-- MES_CONFIG_CODE (293)
(2058014518088437813, '000000', 293, 1, 'FIXCHAR',  'PREFIX', '前缀',   2, NULL, NULL, 'CF', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0'),
(2058014518088437814, '000000', 293, 2, 'NOWDATE',  'DATE',   '日期',   8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0'),
(2058014518088437815, '000000', 293, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), '0');

-- ==================== 校验 ====================
-- SELECT type_code, type_name, source_type, source_provider, status FROM mes_flash_type ORDER BY sort_order;
-- SELECT pool_id, pool_code, resource_type, source, used_count FROM mes_resource_pool;
-- SELECT dict_type, dict_label, dict_value FROM sys_dict_data WHERE dict_type LIKE 'mes_resource%' ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE perms LIKE 'mes:flash:pool:%' OR perms LIKE 'mes:flash:content:%' ORDER BY menu_id;
