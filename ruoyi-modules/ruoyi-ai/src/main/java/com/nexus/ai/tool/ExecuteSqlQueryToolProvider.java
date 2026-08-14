package com.nexus.ai.tool;

import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.ExecuteSqlQueryTool;
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

    @Override
    public Class<?> getToolClass() {
        return ExecuteSqlQueryTool.class;
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
