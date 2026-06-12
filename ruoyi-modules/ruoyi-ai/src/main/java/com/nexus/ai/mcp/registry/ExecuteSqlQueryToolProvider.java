package com.nexus.ai.mcp.registry;

import com.nexus.ai.mcp.BuiltinToolProvider;
import org.springframework.stereotype.Component;

/**
 * 执行 SQL 查询工具提供者
 *
 * @author NexusERP
 */
@Component
public class ExecuteSqlQueryToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "execute_sql_query";
    }

    @Override
    public String getDisplayName() {
        return "执行 SQL 查询";
    }

    @Override
    public String getDescription() {
        return "执行 SELECT SQL 查询（必须包含 tenant_id 条件）";
    }
}
