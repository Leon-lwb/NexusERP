package com.nexus.ai.tool;

import org.springframework.stereotype.Component;

/**
 * ERP 供应链缺料风险查询工具提供者
 *
 * @author NexusERP
 */
@Component
public class ErpShortageRiskToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "erp_shortage_risk";
    }

    @Override
    public String getDisplayName() {
        return "缺料风险预测工具";
    }

    @Override
    public String getDescription() {
        return "查询未来N天供应链缺料风险清单（MRP毛需求+库存+在途采购推演），返回物料/最早缺料日/缺口/建议动作，支撑'未来两周哪些物料会缺料/要不要催货'等供应链决策问答";
    }

    @Override
    public Class<?> getToolClass() {
        return ErpShortageRiskTool.class;
    }

    /**
     * 获取工具类别标签
     * <p>QUERY=数据查询专用, ALL=通用（查询和报表均可使用）</p>
     */
    @Override
    public String getCategories() {
        return "ALL";
    }
}
