package com.nexus.ai.mcp.registry;

import com.nexus.ai.mcp.BuiltinToolProvider;
import com.nexus.ai.tool.ErpPurchaseTool;
import org.springframework.stereotype.Component;

/**
 * 采购查询工具提供者
 *
 * @author NexusERP
 */
@Component
public class ErpPurchaseToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "erp_purchase";
    }

    @Override
    public String getDisplayName() {
        return "采购查询工具";
    }

    @Override
    public String getDescription() {
        return "查询采购金额分布、供应商评估、在途订单、采购价格历史";
    }

    @Override
    public Class<?> getToolClass() {
        return ErpPurchaseTool.class;
    }
}
