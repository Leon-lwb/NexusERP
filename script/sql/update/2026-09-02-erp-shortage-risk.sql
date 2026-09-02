-- ====================================================================
-- S3 供应链缺料风险预测: 菜单(生产计划 2300 下) + AI Agent 工具注册
-- 分段执行说明(混合库脚本, 不能整文件灌进一个库):
--   第一段(ry-vue 库): 菜单 sys_menu
--   第二段(ry-vue 库): ai_tool 表注册 erp_shortage_risk 工具
-- ====================================================================

-- ----------------------------------------------------------------------
-- 第一段 >>> ry-vue 库: 菜单(生产计划 2300 下)
-- ----------------------------------------------------------------------

INSERT IGNORE INTO `sys_menu` VALUES
(2325, '缺料风险预测', 2300, 5, 'shortage', 'erp/prd/shortage/index', NULL, 1, 0, 'C', '0', '0', 'prd:shortage:list', 'warning', 103, 1, '2026-09-02 12:00:00', NULL, NULL, '供应链缺料风险推演(MRP+在途+安全库存)'),
(2326, '缺料风险查询', 2325, 1, '', '', '', 1, 0, 'F', '0', '0', 'prd:shortage:query', '#', 103, 1, '2026-09-02 12:00:00', NULL, NULL, '');

-- ----------------------------------------------------------------------
-- 第二段 >>> ry-vue 库: AI Agent 工具注册(ry-vue 库 ai_tool 表)
--    tool_name 必须与 ErpShortageRiskToolProvider.getToolName() 一致
--    (AiToolInitializer 启动同步为兜底, 此种子为幂等保险)
-- ----------------------------------------------------------------------

INSERT IGNORE INTO `ai_tool`(`id`, `tenant_id`, `tool_name`, `display_name`, `description`, `tool_type`, `config_json`, `status`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `attr1`, `attr2`, `attr3`, `attr4`) VALUES
(2092000000000000111, '000000', 'erp_shortage_risk', '缺料风险预测工具', '查询未来N天供应链缺料风险清单(MRP毛需求+库存+在途采购推演), 返回物料/最早缺料日/缺口/建议动作, 支撑未来两周哪些物料会缺料/要不要催货等决策问答', 'BUILTIN', NULL, 'ENABLED', 103, 1, NOW(), NULL, NULL, '0', NULL, NULL, NULL, NULL);

-- ----------------------------------------------------------------------
-- 校验
-- ----------------------------------------------------------------------
-- ry-vue: SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id IN (2325, 2326);
--         SELECT id, tool_name, display_name, status FROM ai_tool WHERE tool_name = 'erp_shortage_risk';
