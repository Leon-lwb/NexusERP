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
     * 请求停止实例（协作式，节点边界生效）
     *
     * @param instanceId 实例ID
     * @return 是否首次请求成功
     */
    boolean stop(Long instanceId);
}
