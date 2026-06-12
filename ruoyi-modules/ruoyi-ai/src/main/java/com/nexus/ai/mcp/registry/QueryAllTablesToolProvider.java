package com.nexus.ai.mcp.registry;

import com.nexus.ai.mcp.BuiltinToolProvider;
import org.springframework.stereotype.Component;

/**
 * 查询所有表工具提供者
 *
 * @author NexusERP
 */
@Component
public class QueryAllTablesToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "query_all_tables";
    }

    @Override
    public String getDisplayName() {
        return "查询所有表";
    }

    @Override
    public String getDescription() {
        return "查询数据库中所有可用的 ERP 业务表（从缓存获取）";
    }
}
