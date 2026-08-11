package com.nexus.ai.flow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.common.ai.domain.entity.AiFlow;

/**
 * AI 流程执行引擎
 *
 * @author NexusERP
 */
public interface FlowEngine {

    /**
     * 异步启动流程（aiExecutor 执行），返回 instanceId
     *
     * @param flow          流程定义
     * @param input         运行入参（JSON），可为 null
     * @param triggerType   触发方式: MANUAL/SCHEDULE
     * @param tenantIdStr   租户ID（String，写库与 AiRequestContext 用）
     * @param userId        触发人ID（SSE 通知目标）
     * @param userName      触发人名称
     * @return 实例ID
     */
    Long start(AiFlow flow, JsonNode input, String triggerType,
               String tenantIdStr, Long userId, String userName);

    /**
     * 部分执行：运行到指定节点（含全部上游），对齐 n8n "Execute up to node"
     * <p>只执行目标节点及其传递上游闭包，目标节点执行完即结束（其下游不参与）。
     * 用于设计器单独调试某节点前序链路。</p>
     *
     * @param flow              流程定义
     * @param input             运行入参（JSON），可为 null
     * @param triggerType       触发方式（通常 TEST）
     * @param tenantIdStr       租户ID
     * @param userId            触发人ID
     * @param userName          触发人名称
     * @param destinationNodeId 目标节点ID（执行到它为止）
     * @return 实例ID
     */
    Long startToNode(AiFlow flow, JsonNode input, String triggerType,
                     String tenantIdStr, Long userId, String userName, String destinationNodeId);

    /**
     * 请求停止实例（协作式，节点边界生效）
     *
     * @param instanceId 实例ID
     * @return 是否首次请求成功
     */
    boolean stop(Long instanceId);

    /**
     * 执行级续跑（从失败节点重跑，对齐 n8n execution.service.ts retry()）
     * <p>从 ai_flow_node_run 快照重建状态：已 SUCCEEDED 节点保留原输出，从最早 FAILED 节点继续。
     * 实例状态须为 FAILED/STOPPED；循环场景从失败批次继续。</p>
     *
     * @param instanceId   实例ID
     * @param tenantIdStr  租户ID
     * @param userId       触发人ID（SSE 通知目标）
     * @return 续跑后的实例ID
     * @throws IllegalArgumentException 状态不允许续跑/无可续跑点
     */
    Long retry(Long instanceId, String tenantIdStr, Long userId);
}
