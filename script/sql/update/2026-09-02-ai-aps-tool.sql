-- ====================================================================
-- AI Agent 工具注册: APS 智能排产查询工具
-- 库: ry-vue (ai_tool 表)
-- 对应: ApsScheduleTool(ApsScheduleToolProvider) — 查询排产方案/拖期工单/瓶颈资源/工序明细
-- ====================================================================

INSERT IGNORE INTO `ai_tool`(`id`, `tenant_id`, `tool_name`, `display_name`, `description`, `tool_type`, `config_json`, `status`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `attr1`, `attr2`, `attr3`, `attr4`) VALUES
(2092000000000000110, '000000', 'aps_schedule', '智能排产查询工具', '查询APS排产方案、拖期工单、瓶颈资源利用率、工单工序排程明细，支撑哪些订单会拖期/瓶颈在哪等决策问答', 'BUILTIN', NULL, 'ENABLED', 103, 1, NOW(), NULL, NULL, '0', NULL, NULL, NULL, NULL);

-- ==================== 校验 ====================
-- SELECT id, tool_name, display_name, status FROM ai_tool WHERE tool_name = 'aps_schedule';
