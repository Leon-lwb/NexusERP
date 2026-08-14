package com.nexus.ai.tool;

import com.nexus.ai.tool.BuiltinToolProvider;
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

    /**
     * 获取工具类别标签
     * <p>QUERY=数据查询专用, ALL=通用（查询和报表均可使用）</p>
     *
     * @return 类别标签
     */
    @Override
    public String getCategories() {
        return "QUERY";
    }
}
