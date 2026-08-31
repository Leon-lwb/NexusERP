package com.nexus.flow.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 画布边定义（对应 config_json.edges[]）
 * <p>handleId 编码对齐 n8n: {mode}/{type}/{index}，如 "outputs/main/0"、"outputs/if/true"、
 * "outputs/error/0"（ERROR_PORT 错误分支）、"outputs/loop/main|done"（split 循环分支）</p>
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

    /** 循环返回边标记（split 节点，target=source） */
    private boolean loop = false;

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

    /**
     * 是否为错误输出分支（onError=ERROR_PORT 节点的 outputs/error/0 出边）
     * <p>编码对齐 n8n {mode}/{type}/{index}：outputs/error/0 → 按 "/error/" 段匹配</p>
     *
     * @return 是否 error 分支
     */
    public boolean isErrorBranch() {
        return sourceHandle != null
            && (sourceHandle.contains("/error/") || sourceHandle.endsWith("/error"));
    }

    /**
     * 是否为循环完成分支（split 节点的 outputs/loop/done 出边）
     *
     * @return 是否 done 分支
     */
    public boolean isDoneBranch() {
        return sourceHandle != null && sourceHandle.endsWith("/done");
    }

    /**
     * 是否为循环返回边（loop 标记边：循环体尾节点 → split 锚点）
     * <p>前端在返回边上标 loop=true；source=循环体尾节点，target=split 锚点</p>
     *
     * @return 是否 loop 边
     */
    public boolean isLoopEdge() {
        return loop;
    }
}
