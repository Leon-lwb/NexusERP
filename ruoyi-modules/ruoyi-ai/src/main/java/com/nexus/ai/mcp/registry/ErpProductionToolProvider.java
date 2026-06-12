package com.nexus.ai.mcp.registry;

import com.nexus.ai.mcp.BuiltinToolProvider;
import org.springframework.stereotype.Component;

/**
 * 生产查询工具提供者
 *
 * @author NexusERP
 */
@Component
public class ErpProductionToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "erp_production";
    }

    @Override
    public String getDisplayName() {
        return "生产查询工具";
    }

    @Override
    public String getDescription() {
        return "查询在产工单、产能利用率、BOM展开、生产完成率";
    }
}
