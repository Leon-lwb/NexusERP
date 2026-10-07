-- =====================================================
-- 用途：修复两条原始增量属于000000、全量导出误存为0且状态仍为ENABLED的内置工具种子
-- 目标：系统库ry-vue，仅本地开发/其他环境分别审核授权后执行
-- 范围：固定ID + 工具名 + 类型 + 旧租户 + 旧状态；不升级其他0租户资料或模板
-- 特性：匹配状态的一次修正；重放已修正行不生效；执行前须备份并核对来源
-- 说明：新安装种子已改为000000和1（停用），实际启用由正常授权API完成
-- 日期：2026-10-08
-- =====================================================
START TRANSACTION;

UPDATE ai_tool SET tenant_id = '000000', status = '1'
WHERE id = 2092000000000000110 AND tool_name = 'aps_schedule'
  AND tool_type = 'BUILTIN' AND tenant_id = '0' AND status = 'ENABLED';

UPDATE ai_tool SET tenant_id = '000000', status = '1'
WHERE id = 2092000000000000111 AND tool_name = 'erp_shortage_risk'
  AND tool_type = 'BUILTIN' AND tenant_id = '0' AND status = 'ENABLED';

COMMIT;

SELECT id, tenant_id, tool_name, status FROM ai_tool
WHERE id IN (2092000000000000110, 2092000000000000111);
