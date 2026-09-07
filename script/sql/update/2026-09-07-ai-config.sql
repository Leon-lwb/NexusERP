-- ====================================================================
-- AI 参数设置页面化: ai_config 表 + 菜单
-- 库: ry-vue
-- 对应: nexus.ai 运行参数从 application.yml 迁移到页面化管理 (yml 仅作首次启动兜底默认)
-- 后端: AiConfigController(/ai/config) / AiConfigServiceImpl(启动加载回填 AiProperties + Redis 广播多节点)
-- 说明: ai_config 为全局配置表, 已加入 application.yml 的 tenant.excludes
-- ====================================================================

-- 1. 配置表 (无记录的分组沿用 yml 默认值, 无需种子数据)
CREATE TABLE IF NOT EXISTS `ai_config`  (
  `config_group` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置分组: sql_safety/vector_store/web_search/flow/knowledge_graph',
  `config_value` json NULL COMMENT '配置值(分组内字段JSON)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`config_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI运行参数配置(全局, 页面化替代nexus.ai yml运行参数)' ROW_FORMAT = DYNAMIC;

-- 2. 菜单 (1841/1842 已确认空闲; 页面入口: AI 智能助手 → AI参数设置)
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (1841, 'AI参数设置', 1700, 7, 'config', 'ai/config/index', NULL, 1, 0, 'C', '0', '0', 'ai:config:list', 'edit', 103, 1, NOW(), 'AI运行参数设置页(SQL安全/向量存储/联网搜索/流程编排/知识图谱)');

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (1842, '参数修改', 1841, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'ai:config:edit', '#', 103, 1, NOW(), '');

-- ==================== 存量环境配套 ====================
-- 1) application.yml 的 tenant.enable.excludes 需手工追加: ai_config (本脚本无法改 yml)
-- 2) 菜单入库后需在 [系统管理-角色管理] 为相关角色勾选「AI参数设置」+「参数修改」

-- ==================== 校验 ====================
-- SELECT config_group, update_time FROM ai_config;
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id IN (1841, 1842);
