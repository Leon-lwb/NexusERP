-- AI工具：按精确租户文本建立工具名唯一约束，新记录默认停用。
-- 目标系统库ry-vue；已有测试库获授权并备份，其他环境须分别审查和授权。
-- 不导入主初始化脚本，不修改工具记录/历史租户/凭据，不执行业务DML。
-- 适用基线：uk_tool_name(tool_name)为唯一索引；uk_tenant_tool_name不存在。
-- 先核对下列SELECT：租户不能为空，同租户工具名不能重复；不匹配时停止，不自动补租户。
-- MySQL DDL隐式提交，不可当作事务DML回滚；此增量不是无条件可重放脚本。
SELECT COUNT(*) AS invalid_tenant_count
FROM ai_tool WHERE tenant_id IS NULL OR TRIM(tenant_id) = '';
SELECT tenant_id, tool_name, COUNT(*) AS duplicate_count
FROM ai_tool GROUP BY tenant_id, tool_name HAVING COUNT(*) > 1;
SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'ai_tool'
ORDER BY index_name, seq_in_index;

ALTER TABLE ai_tool
    DROP INDEX uk_tool_name,
    ADD UNIQUE INDEX uk_tenant_tool_name (tenant_id, tool_name),
    MODIFY COLUMN status varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        NULL DEFAULT '1' COMMENT '状态: 0启用/1停用，默认停用';

-- 执行后核对新索引的两列顺序、status默认1及记录数/逐行摘要不变；禁止以历史0自动升级绕过冲突。
SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'ai_tool'
ORDER BY index_name, seq_in_index;
