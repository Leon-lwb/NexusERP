-- 工业交付风险决策：已审查 plus 基线的本机一次性增量。
-- 目标仅为 TCP 127.0.0.1:3306 的 ry-vue / ry-erp；执行前须完整备份并核对实例身份。
-- 主定义来自 script/sql/ry-vue.sql、ry-erp.sql；禁止整份重建已有库。
-- 必须使用 mysql --no-defaults --protocol=TCP --host=127.0.0.1 --port=3306，不得使用 --force。
-- 缺失基线或菜单/字典冲突会触发临时护栏主键错误，客户端应立即停止。
-- 再次执行或部分迁移的目标会在护栏处拒绝，不是用忽略插入掩盖差异。
-- DDL 不能依靠事务回滚；失败保留现场与备份，不自动删除业务表或恢复整库。
-- 不复制演示数据、模型凭据、审批记录，不扩大角色权限。

SET NAMES utf8mb4;
SET SESSION time_zone = '+08:00';
SET SESSION lock_wait_timeout = 15;
SET SESSION innodb_lock_wait_timeout = 15;
SET @nexus_plus_migration_lock = GET_LOCK('nexus_plus_delivery_decision_20260928', 0);

-- 会话内临时主键护栏：条件不满足时插入同名主键并报错，不依赖未生效的 CHECK 约束。
CREATE TEMPORARY TABLE `ry-vue`.`_nexus_decision_migration_guard_20260928` (
  `guard_code` varchar(32) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO `ry-vue`.`_nexus_decision_migration_guard_20260928` VALUES ('TARGET'), ('SCHEMA_BASE'), ('MENU_BASE'), ('DICT_BASE');

INSERT INTO `ry-vue`.`_nexus_decision_migration_guard_20260928`
SELECT 'TARGET' WHERE IFNULL(NOT (
  @@port = 3306
  AND @nexus_plus_migration_lock = 1
  AND (SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('ry-vue','ry-erp')) = 2
), 1);

INSERT INTO `ry-vue`.`_nexus_decision_migration_guard_20260928`
SELECT 'SCHEMA_BASE' WHERE IFNULL(NOT (
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='ry-vue' AND TABLE_NAME IN ('ai_decision_run','ai_decision_evidence','ai_decision_action')) = 0
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='prd_schedule_plan' AND COLUMN_NAME IN ('decision_run_id','decision_action_id','decision_idempotency_key','decision_scenario_code','decision_work_order_ids_json','version')) = 0
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='pu_purchase_requisition' AND COLUMN_NAME IN ('decision_run_id','decision_action_id','decision_idempotency_key','version')) = 0
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='prd_schedule_plan' AND INDEX_NAME IN ('uk_psp_decision_idem','idx_psp_decision_run')) = 0
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='pu_purchase_requisition' AND INDEX_NAME IN ('uk_pur_decision_idem','idx_pur_decision_run')) = 0
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-vue' AND TABLE_NAME='ai_flow_node_run' AND COLUMN_NAME='node_type' AND COLUMN_TYPE='varchar(30)' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci') = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='pu_purchase_requisition_line' AND COLUMN_NAME='material_name' AND COLUMN_TYPE='varchar(128)' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci') = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='pu_purchase_requisition_line' AND COLUMN_NAME='material_spec' AND COLUMN_TYPE='varchar(128)' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci') = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='pu_purchase_requisition_line' AND COLUMN_NAME='unit' AND COLUMN_TYPE='varchar(32)' AND IS_NULLABLE='YES' AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci') = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-erp' AND TABLE_NAME='pu_purchase_requisition' AND COLUMN_NAME IN ('dept_id','dept_name')) = 2
  AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE (TABLE_SCHEMA='ry-vue' AND TABLE_NAME='ai_flow_node_run') OR (TABLE_SCHEMA='ry-erp' AND TABLE_NAME IN ('prd_schedule_plan','pu_purchase_requisition','pu_purchase_requisition_line'))) = 4
), 1);

INSERT INTO `ry-vue`.`_nexus_decision_migration_guard_20260928`
SELECT 'MENU_BASE' WHERE IFNULL(NOT (
  (SELECT COUNT(*) FROM `ry-vue`.`sys_menu` WHERE menu_id BETWEEN 1850 AND 1859 OR perms LIKE 'ai:decision:%') = 0
  AND (SELECT COUNT(*) FROM `ry-vue`.`sys_menu` WHERE menu_id=1700 AND menu_type='M' AND status='0') = 1
), 1);

INSERT INTO `ry-vue`.`_nexus_decision_migration_guard_20260928`
SELECT 'DICT_BASE' WHERE IFNULL(NOT (
  (SELECT COUNT(*) FROM `ry-vue`.`sys_dict_data` WHERE dict_code=3000044 OR (tenant_id='000000' AND dict_type='pu_source_type' AND dict_value='AI_DECISION')) = 0
  AND (SELECT COUNT(*) FROM `ry-vue`.`sys_dict_type` WHERE tenant_id='000000' AND dict_type='pu_source_type') = 1
), 1);

-- 新建缺失决策表，保持全部租户、审批、状态及幂等索引。
CREATE TABLE `ry-vue`.`ai_decision_run` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `scene_code` varchar(64) NOT NULL COMMENT '场景编码',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型',
  `biz_id` bigint(20) NULL COMMENT '根业务ID',
  `biz_code` varchar(64) NULL COMMENT '根业务编号',
  `parent_run_id` bigint(20) NULL COMMENT '父运行ID',
  `superseded_by_run_id` bigint(20) NULL COMMENT '替代运行ID',
  `trigger_type` varchar(20) NOT NULL COMMENT '触发类型',
  `status` varchar(32) NOT NULL COMMENT '运行状态',
  `risk_score` decimal(8,2) NULL COMMENT '风险分数',
  `risk_level` varchar(16) NULL COMMENT '风险等级',
  `confidence_level` varchar(16) NULL COMMENT '置信度等级',
  `recommended_scenario_code` varchar(64) NULL COMMENT '推荐方案编码',
  `input_snapshot_json` longtext NULL COMMENT '输入快照JSON',
  `input_snapshot_hash` varchar(64) NULL COMMENT '输入快照SHA-256',
  `snapshot_time` datetime NULL COMMENT '快照时间',
  `valid_until` datetime NULL COMMENT '审批有效截止时间',
  `score_config_json` text NULL COMMENT '评分配置快照',
  `scenario_result_json` longtext NULL COMMENT '场景推演结果',
  `narrative_json` longtext NULL COMMENT '决策说明JSON',
  `result_json` longtext NULL COMMENT '完整决策结果JSON',
  `outcome_json` longtext NULL COMMENT '执行后结果JSON',
  `approval_context_json` longtext NULL COMMENT '可信审批执行人权限快照（禁止从Flow输入恢复）',
  `summary_mode` varchar(32) NULL COMMENT 'MODEL/TEMPLATE_FALLBACK',
  `model_id` bigint(20) NULL COMMENT '模型ID',
  `model_name` varchar(128) NULL COMMENT '模型名称快照',
  `prompt_version` varchar(32) NULL COMMENT 'Prompt版本',
  `input_tokens` int NULL COMMENT '输入Token',
  `output_tokens` int NULL COMMENT '输出Token',
  `estimated_cost` decimal(18,6) NULL COMMENT '估算成本',
  `analysis_flow_instance_id` bigint(20) NULL COMMENT '分析Flow实例ID',
  `execution_flow_instance_id` bigint(20) NULL COMMENT '执行Flow实例ID',
  `error_code` varchar(64) NULL COMMENT '错误码',
  `error_message` varchar(1000) NULL COMMENT '脱敏错误信息',
  `started_at` datetime NULL COMMENT '开始时间',
  `analysis_finished_at` datetime NULL COMMENT '分析完成时间',
  `finished_at` datetime NULL COMMENT '完成时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_dept` bigint(20) NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL COMMENT '创建者',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL COMMENT '更新者',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  `attr1` varchar(256) NULL COMMENT '预留字段1',
  `attr2` varchar(256) NULL COMMENT '预留字段2',
  `attr3` varchar(256) NULL COMMENT '预留字段3',
  `attr4` varchar(256) NULL COMMENT '预留字段4',
  PRIMARY KEY (`id`),
  KEY `idx_decision_tenant_scene_status` (`tenant_id`,`scene_code`,`status`),
  KEY `idx_decision_biz` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_decision_create_time` (`tenant_id`,`create_time`),
  KEY `idx_decision_parent` (`parent_run_id`),
  KEY `idx_decision_analysis_flow` (`analysis_flow_instance_id`),
  KEY `idx_decision_execution_flow` (`execution_flow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工业决策运行';

-- 新建缺失决策表，保持全部租户、审批、状态及幂等索引。
CREATE TABLE `ry-vue`.`ai_decision_evidence` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `run_id` bigint(20) NOT NULL COMMENT '决策运行ID',
  `evidence_key` varchar(128) NOT NULL COMMENT '运行内证据键',
  `source_domain` varchar(20) NOT NULL COMMENT 'ERP/MES/IOT/RAG/RULE/DERIVED',
  `source_type` varchar(64) NOT NULL COMMENT '来源类型',
  `source_id` bigint(20) NULL COMMENT '来源ID',
  `source_code` varchar(128) NULL COMMENT '来源编号',
  `source_name` varchar(255) NULL COMMENT '来源名称',
  `metric_code` varchar(64) NOT NULL COMMENT '指标编码',
  `metric_name` varchar(128) NOT NULL COMMENT '指标名称',
  `value_type` varchar(20) NOT NULL COMMENT '值类型',
  `metric_value` varchar(2000) NULL COMMENT '指标展示值',
  `unit` varchar(32) NULL COMMENT '单位',
  `observed_at` datetime NULL COMMENT '观察时间',
  `data_version` varchar(128) NULL COMMENT '数据版本',
  `confidence` decimal(5,4) NULL COMMENT '置信度',
  `verifiable` char(1) NOT NULL DEFAULT 'Y' COMMENT '是否可验证',
  `normative` char(1) NOT NULL DEFAULT 'N' COMMENT '是否规范性证据',
  `derived_from_json` text NULL COMMENT '派生来源证据键JSON',
  `display_path` varchar(500) NULL COMMENT '前端业务跳转路径',
  `create_dept` bigint(20) NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL COMMENT '创建者',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL COMMENT '更新者',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  `attr1` varchar(256) NULL COMMENT '预留字段1',
  `attr2` varchar(256) NULL COMMENT '预留字段2',
  `attr3` varchar(256) NULL COMMENT '预留字段3',
  `attr4` varchar(256) NULL COMMENT '预留字段4',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_evidence` (`run_id`,`evidence_key`),
  KEY `idx_decision_evidence_source` (`tenant_id`,`source_type`,`source_id`),
  KEY `idx_decision_evidence_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工业决策证据';

-- 新建缺失决策表，保持全部租户、审批、状态及幂等索引。
CREATE TABLE `ry-vue`.`ai_decision_action` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `tenant_id` varchar(20) NOT NULL DEFAULT '000000' COMMENT '租户编号',
  `run_id` bigint(20) NOT NULL COMMENT '决策运行ID',
  `action_type` varchar(64) NOT NULL COMMENT '动作类型',
  `title` varchar(255) NOT NULL COMMENT '动作标题',
  `display_reason` varchar(1000) NULL COMMENT '展示理由',
  `target_biz_type` varchar(32) NULL COMMENT '目标业务类型',
  `target_biz_id` bigint(20) NULL COMMENT '目标业务ID',
  `target_biz_code` varchar(64) NULL COMMENT '目标业务编号',
  `payload_json` longtext NOT NULL COMMENT '白名单动作参数JSON',
  `risk_level` varchar(16) NOT NULL COMMENT '动作风险等级',
  `approval_required` char(1) NOT NULL DEFAULT 'Y' COMMENT '是否需要审批',
  `approval_status` varchar(32) NOT NULL COMMENT '审批状态',
  `execution_status` varchar(32) NOT NULL COMMENT '执行状态',
  `permission_code` varchar(128) NULL COMMENT '业务权限码',
  `executor_code` varchar(64) NOT NULL COMMENT '执行器编码',
  `sequence_no` int NOT NULL DEFAULT 0 COMMENT '执行顺序',
  `dependency_ids_json` varchar(2000) NULL COMMENT '依赖动作ID JSON',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键',
  `approved_by` bigint(20) NULL COMMENT '审批人',
  `approved_at` datetime NULL COMMENT '审批时间',
  `approval_comment` varchar(1000) NULL COMMENT '审批意见',
  `result_biz_type` varchar(32) NULL COMMENT '结果业务类型',
  `result_biz_id` bigint(20) NULL COMMENT '结果业务ID',
  `result_biz_code` varchar(64) NULL COMMENT '结果业务编号',
  `result_json` longtext NULL COMMENT '执行结果JSON',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `last_error_code` varchar(64) NULL COMMENT '最后错误码',
  `last_error_message` varchar(1000) NULL COMMENT '最后脱敏错误',
  `started_at` datetime NULL COMMENT '执行开始时间',
  `finished_at` datetime NULL COMMENT '执行完成时间',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_dept` bigint(20) NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL COMMENT '创建者',
  `create_time` datetime NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL COMMENT '更新者',
  `update_time` datetime NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  `attr1` varchar(256) NULL COMMENT '预留字段1',
  `attr2` varchar(256) NULL COMMENT '预留字段2',
  `attr3` varchar(256) NULL COMMENT '预留字段3',
  `attr4` varchar(256) NULL COMMENT '预留字段4',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decision_action_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_decision_action_run` (`run_id`,`sequence_no`),
  KEY `idx_decision_action_approval` (`tenant_id`,`approval_status`),
  KEY `idx_decision_action_execution` (`tenant_id`,`execution_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工业决策动作';

-- 仅增加可空来源及默认版本，不改现有状态、数量或审批。
ALTER TABLE `ry-erp`.`prd_schedule_plan`
  ADD COLUMN `decision_run_id` bigint(20) NULL DEFAULT NULL COMMENT 'AI决策运行ID',
  ADD COLUMN `decision_action_id` bigint(20) NULL DEFAULT NULL COMMENT 'AI决策动作ID',
  ADD COLUMN `decision_idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '租户内AI决策幂等键',
  ADD COLUMN `decision_scenario_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'AI决策场景编码',
  ADD COLUMN `decision_work_order_ids_json` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '显式工单ID十进制字符串JSON',
  ADD COLUMN `version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  ADD UNIQUE INDEX `uk_psp_decision_idem`(`tenant_id`, `decision_idempotency_key`) USING BTREE,
  ADD INDEX `idx_psp_decision_run`(`tenant_id`, `decision_run_id`) USING BTREE;

-- 仅增加可空来源及默认版本，不改现有状态、数量或审批。
ALTER TABLE `ry-erp`.`pu_purchase_requisition`
  ADD COLUMN `decision_run_id` bigint(20) NULL DEFAULT NULL COMMENT 'AI决策运行ID',
  ADD COLUMN `decision_action_id` bigint(20) NULL DEFAULT NULL COMMENT 'AI决策动作ID',
  ADD COLUMN `decision_idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '租户内AI决策幂等键',
  ADD COLUMN `version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  ADD UNIQUE INDEX `uk_pur_decision_idem`(`tenant_id`, `decision_idempotency_key`) USING BTREE,
  ADD INDEX `idx_pur_decision_run`(`tenant_id`, `decision_run_id`) USING BTREE;

-- 仅扩容展示/节点字段，保持原字符集、空值语义和注释。
ALTER TABLE `ry-vue`.`ai_flow_node_run`
  MODIFY COLUMN `node_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型: LLM/AGENT/RAG/IF/CODE';

-- 仅扩容展示/节点字段，保持原字符集、空值语义和注释。
ALTER TABLE `ry-erp`.`pu_purchase_requisition_line`
  MODIFY COLUMN `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称',
  MODIFY COLUMN `material_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格型号',
  MODIFY COLUMN `unit` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计量单位';

-- 菜单与默认租户来源字典在同一事务中插入，不授权任何角色。
START TRANSACTION;
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1850, '工业决策中心', 1700, 7, 'decision', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'data-analysis', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '工业交付风险决策目录');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1851, '交付风险看板', 1850, 1, 'index', 'ai/decision/index', NULL, 1, 0, 'C', '0', '0', 'ai:decision:query', 'warning', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '工业交付风险看板');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1852, '决策运行详情', 1850, 2, 'detail', 'ai/decision/detail', NULL, 1, 0, 'C', '1', '0', 'ai:decision:query', 'document', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '隐藏详情路由');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1853, '决策查询', 1851, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:decision:query', '#', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1854, '发起决策', 1851, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:decision:run', '#', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1855, '审批决策', 1851, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:decision:approve', '#', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1856, '执行重试', 1851, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:decision:execute', '#', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1857, '决策导出', 1851, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:decision:export', '#', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1858, '决策配置', 1851, 6, '', '', '', 1, 0, 'F', '0', '0', 'ai:decision:config', '#', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '');
INSERT INTO `ry-vue`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1859, '决策评测', 1851, 7, '', '', '', 1, 0, 'F', '0', '0', 'ai:decision:benchmark', '#', 103, 1, '2026-09-23 00:00:00', NULL, NULL, '');
INSERT INTO `ry-vue`.`sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (3000044, '000000', 4, 'AI交付风险决策', 'AI_DECISION', 'pu_source_type', '', 'info', 'N', 103, 1, '2026-09-27 23:00:00', NULL, NULL, '授权AI工业决策生成的采购申请草稿');
COMMIT;

-- 临时护栏仅存在于本次会话，结束后释放迁移互斥锁。
DROP TEMPORARY TABLE `ry-vue`.`_nexus_decision_migration_guard_20260928`;
DO RELEASE_LOCK('nexus_plus_delivery_decision_20260928');
