package com.nexus.ai.tool;

import org.springframework.stereotype.Component;

/**
 * APS 智能排产查询工具提供者
 *
 * @author NexusERP
 */
@Component
public class ApsScheduleToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "aps_schedule";
    }

    @Override
    public String getDisplayName() {
        return "智能排产查询工具";
    }

    @Override
    public String getDescription() {
        return "查询APS排产方案、拖期工单、瓶颈资源利用率、工单工序排程明细，支撑'哪些订单会拖期/瓶颈在哪'等决策问答";
    }

    @Override
    public Class<?> getToolClass() {
        return ApsScheduleTool.class;
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
