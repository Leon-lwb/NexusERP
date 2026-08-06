package com.nexus.ai.flow.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 画布边定义（对应 config_json.edges[]）
 * <p>handleId 编码对齐 n8n: {mode}/{type}/{index}，如 "outputs/main/0"、"outputs/if/true"</p>
 *
 * @author NexusERP
 */
@Data
public class FlowEdgeDef {

    /** 边ID */
    private String id;

    /** 源节点ID */
    private String source;

    /** 目标节点ID */
    private String target;

    /** 源端口编码（如 outputs/main/0、outputs/if/true） */
    private String sourceHandle;

    /** 目标端口编码（如 inputs/main/0） */
    private String targetHandle;

    /** 原始边 JSON（保留前端附加字段） */
    private JsonNode raw;

    /**
     * 是否为 IF 节点的 true 分支输出
     *
     * @return 是否 true 分支
     */
    public boolean isTrueBranch() {
        return sourceHandle != null && sourceHandle.endsWith("/true");
    }

    /**
     * 是否为 IF 节点的 false 分支输出
     *
     * @return 是否 false 分支
     */
    public boolean isFalseBranch() {
        return sourceHandle != null && sourceHandle.endsWith("/false");
    }
}
