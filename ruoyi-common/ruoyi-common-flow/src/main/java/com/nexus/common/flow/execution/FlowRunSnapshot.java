package com.nexus.common.flow.execution;

import com.fasterxml.jackson.databind.JsonNode;

/** 不含Webhook密钥的运行快照；配置与原实例快照具有相同受控访问边界。 */
public record FlowRunSnapshot(
        Long flowId,
        Integer version,
        String flowKey,
        String config,
        Integer maxRetry,
        JsonNode input,
        String userName,
        String triggerType,
        String destinationNodeId,
        Long retryOfInstanceId,
        Boolean parallel) {
    public FlowRunSnapshot(
            Long flowId,
            Integer version,
            String flowKey,
            String config,
            Integer maxRetry,
            JsonNode input,
            String userName,
            String triggerType,
            String destinationNodeId,
            Long retryOfInstanceId) {
        this(
                flowId,
                version,
                flowKey,
                config,
                maxRetry,
                input,
                userName,
                triggerType,
                destinationNodeId,
                retryOfInstanceId,
                null);
    }
}
