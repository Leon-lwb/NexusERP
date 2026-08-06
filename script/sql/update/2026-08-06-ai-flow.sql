-- =============================================
-- NexusERP - AI 流程编排: 建表 + 菜单
-- 目标库: ry-vue
-- 创建时间: 2026-08-06
-- 所属: AI 流程编排系统 P0 数据层 (feature/ai-flow-orchestration)
-- 幂等: 使用 DROP TABLE IF EXISTS + INSERT IGNORE
-- 菜单段位: 1830-1845 (已核对 ry-vue.sql 无占用; 避让 1700 系 AI 目录 / 4241-4260 NCR / 6182-6187 CV)
-- 目录: 流程编排(1830) 挂在 AI 根(1700) 下, order_num=6
-- 页面: 流程列表(1831) + 流程设计(1832, 隐藏路由) + 运行历史(1833)
-- 权限: ai:flow:list/add/edit/remove/publish/run
-- 权威 schema 并入 script/sql/ry-vue.sql
-- =============================================

USE `ry-vue`;

-- ===== 流程定义表 =====
DROP TABLE IF EXISTS `ai_flow`;
CREATE TABLE `ai_flow`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
  `flow_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程名称',
  `flow_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识(唯一, 定时/Webhook 引用)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/OFFLINE',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '版本号(每次发布+1)',
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '画布配置(JSON: nodes+edges)',
  `trigger_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MANUAL' COMMENT '触发方式: MANUAL/SCHEDULE (逗号分隔可多选)',
  `cron_expr` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '定时 Cron 表达式',
  `max_retry` int(11) NOT NULL DEFAULT 1 COMMENT '节点失败最大重试次数',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `attr1` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段4',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_flow_key` (`flow_key`) USING BTREE,
  INDEX `idx_tenant_status` (`tenant_id`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 流程定义' ROW_FORMAT = DYNAMIC;

-- ===== 运行实例表 =====
DROP TABLE IF EXISTS `ai_flow_instance`;
CREATE TABLE `ai_flow_instance`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
  `flow_id` bigint(20) NOT NULL COMMENT '流程ID',
  `flow_version` int(11) NOT NULL DEFAULT 1 COMMENT '运行时的流程版本',
  `trigger_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式: MANUAL/SCHEDULE',
  `trigger_by` bigint(20) NULL DEFAULT NULL COMMENT '触发人(定时任务=流程创建人, 用于SSE通知)',
  `input_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输入(JSON)',
  `output_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输出(JSON, 末节点输出)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCEEDED/FAILED/STOPPED',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '错误信息',
  `duration_ms` bigint(20) NULL DEFAULT NULL COMMENT '总耗时(毫秒)',
  `node_total` int(11) NULL DEFAULT 0 COMMENT '节点总数',
  `node_success` int(11) NULL DEFAULT 0 COMMENT '成功节点数',
  `node_failed` int(11) NULL DEFAULT 0 COMMENT '失败节点数',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `attr1` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段4',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_id` (`flow_id`, `id`) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE,
  INDEX `idx_create_time` (`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 流程运行实例' ROW_FORMAT = DYNAMIC;

-- ===== 节点运行记录表 =====
DROP TABLE IF EXISTS `ai_flow_node_run`;
CREATE TABLE `ai_flow_node_run`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
  `instance_id` bigint(20) NOT NULL COMMENT '实例ID',
  `flow_id` bigint(20) NOT NULL COMMENT '流程ID(冗余, 便于查询)',
  `node_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '画布节点ID(如 node_1)',
  `node_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型: LLM/AGENT/RAG/IF/CODE',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态: PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED',
  `input_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输入(JSON, 渲染后的最终入参)',
  `output_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输出(JSON)',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '错误信息',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `duration_ms` bigint(20) NULL DEFAULT NULL COMMENT '耗时(毫秒)',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `attr1` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段1',
  `attr2` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段2',
  `attr3` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段3',
  `attr4` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预留字段4',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_instance_id` (`instance_id`) USING BTREE,
  INDEX `idx_flow_id` (`flow_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 流程节点运行记录' ROW_FORMAT = DYNAMIC;

-- ===== 菜单 =====
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- 目录: 流程编排
(1830, '流程编排', 1700, 6, 'flow', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 's-opportunity', 103, 1, NOW(), NULL, NULL, 'AI 流程编排目录'),
-- 页面
(1831, '流程列表', 1830, 1, 'index', 'ai/flow/index', NULL, 1, 0, 'C', '0', '0', 'ai:flow:list', 'list', 103, 1, NOW(), NULL, NULL, '流程列表/新建/编辑/发布/下线/删除/运行'),
(1832, '流程设计', 1830, 2, 'designer', 'ai/flow/designer/index', NULL, 1, 0, 'C', '1', '0', 'ai:flow:edit', 'edit', 103, 1, NOW(), NULL, NULL, '流程设计器(侧边栏隐藏, 路由可达)'),
(1833, '运行历史', 1830, 3, 'monitor', 'ai/flow/monitor/index', NULL, 1, 0, 'C', '0', '0', 'ai:flow:list', 'monitor', 103, 1, NOW(), NULL, NULL, '流程运行历史/实例详情'),
(1839, '实例详情', 1833, 1, 'detail', 'ai/flow/monitor/InstanceDetail', NULL, 1, 0, 'C', '1', '0', 'ai:flow:list', 'detail', 103, 1, NOW(), NULL, NULL, '实例详情(侧边栏隐藏, 路由可达)'),
-- 按钮
(1834, '流程新增', 1831, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:flow:add', '#', 103, 1, NOW(), NULL, NULL, '新建/存草稿'),
(1835, '流程修改', 1831, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:flow:edit', '#', 103, 1, NOW(), NULL, NULL, '编辑/发布校验/下线'),
(1836, '流程删除', 1831, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:flow:remove', '#', 103, 1, NOW(), NULL, NULL, '删除(有运行中实例拒绝)'),
(1837, '流程发布', 1831, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:flow:publish', '#', 103, 1, NOW(), NULL, NULL, '发布(校验图合法性)'),
(1838, '流程运行', 1831, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:flow:run', '#', 103, 1, NOW(), NULL, NULL, '手动运行/停止');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'ai:flow:%' OR menu_id BETWEEN 1830 AND 1845 ORDER BY menu_id;
