-- ============================================================
-- NexusERP AI 二阶段 S2-A 数据库变更
-- 知识图谱（Knowledge Graph）：实体关系抽取 + 社区检测 + 局部/全局检索
-- 存储：MySQL 优先，Neo4j 经 GraphStoreStrategy 预留 v2
-- ============================================================

-- 1. 知识图谱实例（每个知识库一个图）
CREATE TABLE IF NOT EXISTS `ai_knowledge_graph` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
    `knowledge_id` bigint(20) NOT NULL COMMENT '知识库ID',
    `graph_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图谱名称',
    `graph_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '状态（0未构建 1构建中 2已完成 3失败）',
    `node_count` int(11) NULL DEFAULT 0 COMMENT '实体节点数',
    `relationship_count` int(11) NULL DEFAULT 0 COMMENT '关系数',
    `community_count` int(11) NULL DEFAULT 0 COMMENT '社区数',
    `model_id` bigint(20) NULL DEFAULT NULL COMMENT '抽取用的LLM模型ID',
    `entity_types` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实体类型（逗号分隔）',
    `relation_types` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关系类型（逗号分隔）',
    `config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图谱配置（JSON）',
    `error_message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '错误信息',
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
    INDEX `idx_knowledge_id`(`knowledge_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 知识图谱实例' ROW_FORMAT = DYNAMIC;

-- 2. 图谱实体节点
CREATE TABLE IF NOT EXISTS `ai_kg_entity` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
    `graph_id` bigint(20) NOT NULL COMMENT '图谱ID',
    `entity_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体名称',
    `entity_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实体类型',
    `description` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实体描述',
    `confidence` decimal(3,2) NULL DEFAULT NULL COMMENT '置信度（0-1）',
    `source_chunk_id` bigint(20) NULL DEFAULT NULL COMMENT '来源切片ID',
    `mention_count` int(11) NULL DEFAULT 1 COMMENT '被提及次数（同名合并）',
    `community_id` bigint(20) NULL DEFAULT NULL COMMENT '所属社区ID',
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
    INDEX `idx_graph_id`(`graph_id`) USING BTREE,
    INDEX `idx_entity_name`(`entity_name`) USING BTREE,
    INDEX `idx_entity_type`(`entity_type`) USING BTREE,
    INDEX `idx_source_chunk`(`source_chunk_id`) USING BTREE,
    INDEX `idx_community`(`community_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 知识图谱实体' ROW_FORMAT = DYNAMIC;

-- 3. 图谱关系
CREATE TABLE IF NOT EXISTS `ai_kg_relation` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
    `graph_id` bigint(20) NOT NULL COMMENT '图谱ID',
    `source_entity_id` bigint(20) NOT NULL COMMENT '头实体ID',
    `target_entity_id` bigint(20) NOT NULL COMMENT '尾实体ID',
    `relation_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关系类型',
    `description` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关系描述',
    `strength` int(11) NULL DEFAULT NULL COMMENT '关系强度（0-10）',
    `confidence` decimal(3,2) NULL DEFAULT NULL COMMENT '置信度（0-1）',
    `source_chunk_id` bigint(20) NULL DEFAULT NULL COMMENT '来源切片ID',
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
    INDEX `idx_graph_id`(`graph_id`) USING BTREE,
    INDEX `idx_source`(`source_entity_id`) USING BTREE,
    INDEX `idx_target`(`target_entity_id`) USING BTREE,
    INDEX `idx_source_chunk`(`source_chunk_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 知识图谱关系' ROW_FORMAT = DYNAMIC;

-- 4. 图谱社区（GraphRAG 社区检测 + 社区摘要）
CREATE TABLE IF NOT EXISTS `ai_kg_community` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
    `graph_id` bigint(20) NOT NULL COMMENT '图谱ID',
    `community_level` int(11) NULL DEFAULT 0 COMMENT '社区层级（v1 单层=0）',
    `parent_community_id` bigint(20) NULL DEFAULT NULL COMMENT '父社区ID（v1 为空）',
    `community_index` int(11) NULL DEFAULT NULL COMMENT '社区序号',
    `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '社区摘要（LLM 生成）',
    `summary_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '摘要状态（0待生成 1生成中 2已完成 3失败）',
    `entity_count` int(11) NULL DEFAULT 0 COMMENT '社区内实体数',
    `relation_count` int(11) NULL DEFAULT 0 COMMENT '社区内关系数',
    `token_used` bigint(20) NULL DEFAULT 0 COMMENT '摘要消耗token',
    `error_message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '错误信息',
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
    INDEX `idx_graph_id`(`graph_id`) USING BTREE,
    INDEX `idx_community_level`(`community_level`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 知识图谱社区' ROW_FORMAT = DYNAMIC;

-- 5. ai_knowledge_chunk 增加图谱抽取跟踪列（新增列加在 vector_id 之后）
ALTER TABLE `ai_knowledge_chunk`
    ADD COLUMN `extraction_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '图谱抽取状态（0待处理 1处理中 2已完成 3失败）' AFTER `vector_id`,
    ADD COLUMN `entity_count` int(11) NULL DEFAULT 0 COMMENT '抽取实体数' AFTER `extraction_status`,
    ADD COLUMN `relation_count` int(11) NULL DEFAULT 0 COMMENT '抽取关系数' AFTER `entity_count`,
    ADD COLUMN `token_used` bigint(20) NULL DEFAULT 0 COMMENT '抽取消耗token' AFTER `relation_count`,
    ADD COLUMN `kg_error_message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '抽取错误信息' AFTER `token_used`;

-- 6. 菜单：知识图谱按钮权限（已与 ry-vue.sql 当前菜单结构对齐）
--    父菜单 menu_id=1704「知识库管理」（当前菜单结构中挂在 1708「知识与工具」目录下，
--    组件 ai/knowledge/index），其按钮 1740-1743 占用 order 1-4，图谱按钮续 5-8。
--    图谱为知识库详情页内 Tab，故仅补 F 按钮权限，不新增 C 菜单，也不改路由。
--    该批菜单已合并进 ry-vue.sql 主脚本（sys_menu 1820-1823），此处用 DELETE+INSERT 保证幂等，
--    避免在已含这些菜单的库上重复执行时主键冲突。
DELETE FROM `sys_menu` WHERE `menu_id` IN (1820, 1821, 1822, 1823);
INSERT INTO `sys_menu` VALUES (1820, '图谱构建', 1704, 5, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:knowledge:graph:build', '#', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, '知识图谱构建');
INSERT INTO `sys_menu` VALUES (1821, '图谱查询', 1704, 6, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:knowledge:graph:query', '#', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (1822, '图谱重抽', 1704, 7, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:knowledge:graph:edit', '#', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, '知识图谱全量重抽');
INSERT INTO `sys_menu` VALUES (1823, '图谱删除', 1704, 8, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:knowledge:graph:remove', '#', NULL, 1, '2026-06-29 10:00:00', NULL, NULL, '');
