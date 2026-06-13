package com.nexus.ai.mcp.registry;

import com.nexus.ai.mcp.BuiltinToolProvider;
import com.nexus.ai.tool.ErpSalesTool;
import org.springframework.stereotype.Component;

/**
 * 销售查询工具提供者
 *
 * @author NexusERP
 */
@Component
public class ErpSalesToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "erp_sales";
    }

    @Override
    public String getDisplayName() {
        return "销售查询工具";
    }

    @Override
    public String getDescription() {
        return "查询月度销售趋势、客户排行、退货统计、订单履行率";
    }

    @Override
    public Class<?> getToolClass() {
        return ErpSalesTool.class;
    }

    /**
     * 获取工具类别标签
     * <p>QUERY=数据查询专用, ALL=通用（查询和报表均可使用）</p>
     *
     * @return 类别标签
     */
    @Override
    public String getCategories() {
        return "ALL";
    }
}
