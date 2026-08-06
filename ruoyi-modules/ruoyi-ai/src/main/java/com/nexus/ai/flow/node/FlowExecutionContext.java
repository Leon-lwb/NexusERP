package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.ai.flow.state.FlowState;
import lombok.Data;

/**
 * 流程节点执行上下文
 * <p>节点读写状态 + 访问实例信息。由引擎创建并注入节点执行器</p>
 *
 * @author NexusERP
 */
@Data
public class FlowExecutionContext {

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
}
