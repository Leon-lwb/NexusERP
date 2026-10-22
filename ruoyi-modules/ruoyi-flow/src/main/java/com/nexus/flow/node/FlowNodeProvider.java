package com.nexus.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.common.flow.execution.FlowReplayPolicy;

import java.util.List;

/**
 * 流程节点执行器接口
 * <p>每类节点一个实现，注册进 {@link FlowNodeRegistry}。
 * 执行异常直接抛出（引擎统一捕获并标记节点 FAILED）</p>
 *
 * @author NexusERP
 */
public interface FlowNodeProvider {
    /** 默认不能重放未知外部副作用；提供者须显式声明安全或幂等能力。 */
    default FlowReplayPolicy replayPolicy(JsonNode config) {
        return FlowReplayPolicy.NON_REPLAYABLE;
    }

    /**
     * 节点类型标识: llm/agent/rag/if/code
     *
     * @return 节点类型
     */
    String getNodeType();

    /**
     * 节点显示名（前端面板用）
     *
     * @return 显示名
     */
    String getDisplayName();

    /**
     * 参数 schema（前端表单驱动 + 发布校验）
     *
     * @return 参数定义列表
     */
    List<NodeParamSchema> getParamSchemas();

    /**
     * 校验配置（发布/保存时调用），非法抛 IllegalArgumentException
     *
     * @param config 节点配置 JSON
     */
    void validateConfig(JsonNode config);

    /**
     * 执行节点，返回输出 JSON
     *
     * @param ctx 执行上下文
     * @return 输出 JSON（写入 state.nodes.&lt;nodeId&gt;.output）
     * @throws Exception 执行失败（引擎标记 FAILED）
     */
    JsonNode execute(FlowExecutionContext ctx) throws Exception;
}
