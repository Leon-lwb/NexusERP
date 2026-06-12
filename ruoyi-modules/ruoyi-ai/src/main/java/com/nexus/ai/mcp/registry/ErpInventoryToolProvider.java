package com.nexus.ai.mcp.registry;

import com.nexus.ai.mcp.BuiltinToolProvider;
import org.springframework.stereotype.Component;

/**
 * 库存查询工具提供者
 *
 * @author NexusERP
 */
@Component
public class ErpInventoryToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "erp_inventory";
    }

    @Override
    public String getDisplayName() {
        return "库存查询工具";
    }

    @Override
    public String getDescription() {
        return "查询库存预警、出库趋势、库存金额分布、按仓库统计";
    }
}
