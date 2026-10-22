package com.nexus.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.state.FlowState;

import lombok.Data;

/**
 * 流程节点执行上下文
 * <p>节点读写状态 + 访问实例信息。由引擎创建并注入节点执行器</p>
 *
 * @author NexusERP
 */
@Data
public class FlowExecutionContext {
    /** 同一节点/批次重试与接管保持稳定的外部幂等键。 */
    private String executionKey;

    /** 当前节点ID */
    private String nodeId;

    /** 当前节点名称 */
    private String nodeName;

    /** 节点类型 */
    private String nodeType;

    /** 节点配置（渲染前原始 config） */
    private JsonNode config;

    /** 全局状态（读写） */
    private FlowState state;

    /** 流程实例ID */
    private Long instanceId;

    /** 流程ID */
    private Long flowId;

    /** 目标用户ID（SSE 通知用，定时任务为流程创建人） */
    private Long userId;

    /** 租户ID（引擎注入；节点在 ForkJoinPool 线程执行，AiRequestContext ThreadLocal 不可达，必须经 ctx 传递） */
    private String tenantId;

    /** 流程图模型（merge 节点收集上游输出用） */
    private FlowGraph graph;
}
