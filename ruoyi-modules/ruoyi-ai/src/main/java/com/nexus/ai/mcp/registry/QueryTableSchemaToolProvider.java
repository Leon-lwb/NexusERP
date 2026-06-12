package com.nexus.ai.mcp.registry;

import com.nexus.ai.mcp.BuiltinToolProvider;
import com.nexus.ai.tool.QueryTableSchemaTool;
import org.springframework.stereotype.Component;

/**
 * 查询表结构工具提供者
 *
 * @author NexusERP
 */
@Component
public class QueryTableSchemaToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "query_table_schema";
    }

    @Override
    public String getDisplayName() {
        return "查询表结构";
    }

    @Override
    public String getDescription() {
        return "查询指定表的 CREATE TABLE DDL（优先从缓存获取）";
    }

    @Override
    public Class<?> getToolClass() {
        return QueryTableSchemaTool.class;
    }
}
