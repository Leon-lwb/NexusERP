-- ============================================================
-- NexusERP AI 二阶段 S2-C 数据库变更
-- 联网搜索 + MCP 市场 + 多模态文生图
-- ============================================================

-- 1. MCP 市场源表
CREATE TABLE IF NOT EXISTS `ai_mcp_market` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
    `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '市场名称',
    `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '市场URL（返回工具列表JSON）',
    `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述',
    `auth_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '认证配置（JSON）',
    `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1' COMMENT '状态（1启用 0停用）',
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
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI MCP 市场源' ROW_FORMAT = DYNAMIC;

-- 2. MCP 市场工具表
CREATE TABLE IF NOT EXISTS `ai_mcp_market_tool` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户编号',
    `market_id` bigint(20) NOT NULL COMMENT '市场ID',
    `tool_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具名称',
    `tool_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具描述',
    `tool_version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具版本',
    `tool_metadata` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具元数据JSON（含 command/args/baseUrl 等）',
    `is_loaded` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '是否已加载到本地ai_tool（1是 0否）',
    `local_tool_id` bigint(20) NULL DEFAULT NULL COMMENT '关联 ai_tool.id',
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
    INDEX `idx_market_id`(`market_id`) USING BTREE,
    INDEX `idx_tool_name`(`tool_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI MCP 市场工具' ROW_FORMAT = DYNAMIC;

-- 3. 菜单：AI MCP 市场（父菜单为 AI 管理目录 menu_id=1700，order_num=10）
INSERT INTO `sys_menu` VALUES (1800, 'MCP市场', 1700, 10, 'mcpMarket', 'ai/mcp/market', NULL, 1, 0, 'C', '0', '0', 'ai:mcp:market:list', 'shopping', NULL, 1, '2026-06-28 19:30:00', NULL, NULL, 'MCP 市场管理菜单');
INSERT INTO `sys_menu` VALUES (1801, '市场查询', 1800, 1, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:mcp:market:query', '#', NULL, 1, '2026-06-28 19:30:00', NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (1802, '市场新增', 1800, 2, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:mcp:market:add', '#', NULL, 1, '2026-06-28 19:30:00', NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (1803, '市场修改', 1800, 3, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:mcp:market:edit', '#', NULL, 1, '2026-06-28 19:30:00', NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (1804, '市场删除', 1800, 4, '', '', NULL, 1, 0, 'F', '0', '0', 'ai:mcp:market:remove', '#', NULL, 1, '2026-06-28 19:30:00', NULL, NULL, '');

-- ============================================================
-- 说明（无需 DDL，仅文档）：
-- 4. 联网搜索：WebSearchTool 为内置工具（BuiltinToolProvider），无需新表。
-- 5. 文生图：ai_model.model_type 新增 IMAGE（已在 S2-B 说明），无需新表。
--    IImageGenerationService 接口放 ruoyi-common-ai，实现放 ruoyi-ai。
-- ============================================================
