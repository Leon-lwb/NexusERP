package com.nexus.ai.tool;

import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.ErpProductionTool;
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

    @Override
    public Class<?> getToolClass() {
        return ErpProductionTool.class;
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
