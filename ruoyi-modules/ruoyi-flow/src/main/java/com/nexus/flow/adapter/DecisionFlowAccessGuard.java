package com.nexus.flow.adapter;

import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.flow.model.FlowGraph;

/**
 * 禁止从通用流程、回调及测试入口进入受保护决策图。
 */
public final class DecisionFlowAccessGuard {
    private DecisionFlowAccessGuard() { }
    public static boolean protectedFlow(Flow flow) {
        return flow != null && (flow.getFlowKey() != null
            && flow.getFlowKey().startsWith("system_delivery_risk_") || protectedConfig(flow.getConfigJson()));
    }
    public static boolean protectedConfig(String json) {
        if (json == null) return false;
        if (json.contains("industrial.")) return true;
        try { return FlowGraph.parse(json).getNodes().values().stream()
            .anyMatch(n -> n.getType().startsWith("industrial.")); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    public static void requireGeneral(Flow flow) {
        if (protectedFlow(flow)) throw new IllegalArgumentException("工业系统流程只允许受控决策入口执行");
    }
}
