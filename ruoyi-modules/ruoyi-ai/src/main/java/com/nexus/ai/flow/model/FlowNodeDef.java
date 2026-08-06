package com.nexus.ai.flow.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 画布节点定义（对应 config_json.nodes[]）
 *
 * @author NexusERP
 */
@Data
public class FlowNodeDef {

    /** 画布节点ID（如 node_llm_1） */
    private String id;

    /** 节点类型: llm/agent/rag/if/code */
    private String type;

    /** 画布坐标 [x, y] */
    private Double[] position;

    /** 节点数据（label/config/onError/retry 等） */
    private JsonNode data;

    /**
     * 获取节点配置（data.config）
     *
     * @return 配置 JSON，缺失返回空对象
     */
    public JsonNode getConfig() {
        if (data == null || data.get("config") == null) {
            return null;
        }
        return data.get("config");
    }

    /**
     * 获取节点显示名称
     *
     * @return 节点名称，缺失返回节点ID
     */
    public String getName() {
        if (data != null && data.get("label") != null && !data.get("label").isNull()) {
            return data.get("label").asText();
        }
        return id;
    }

    /**
     * 获取失败处理策略（默认 STOP）
     *
     * @return STOP/CONTINUE
     */
    public String getOnError() {
        if (data != null && data.get("onError") != null && !data.get("onError").isNull()) {
            return data.get("onError").asText();
        }
        return "STOP";
    }

    /**
     * 获取是否启用节点级重试（默认 false）
     *
     * @return 是否重试
     */
    public boolean isRetryOnFail() {
        if (data != null && data.get("retryOnFail") != null && !data.get("retryOnFail").isNull()) {
            return data.get("retryOnFail").asBoolean();
        }
        return false;
    }

    /**
     * 获取节点级最大重试次数（clamp 2-5，默认 3）
     *
     * @return 最大重试次数
     */
    public int getRetryMaxTries() {
        if (data != null && data.get("retryMaxTries") != null && !data.get("retryMaxTries").isNull()) {
            int tries = data.get("retryMaxTries").asInt();
            return Math.min(5, Math.max(2, tries));
        }
        return 3;
    }

    /**
     * 获取重试等待间隔毫秒（clamp 0-5000，默认 1000）
     *
     * @return 等待毫秒
     */
    public long getWaitBetweenTries() {
        if (data != null && data.get("waitBetweenTries") != null && !data.get("waitBetweenTries").isNull()) {
            long wait = data.get("waitBetweenTries").asLong();
            return Math.min(5000, Math.max(0, wait));
        }
        return 1000;
    }

    /**
     * 获取节点超时秒数（默认 600）
     *
     * @return 超时秒数
     */
    public long getTimeoutSeconds() {
        if (data != null && data.get("timeoutSeconds") != null && !data.get("timeoutSeconds").isNull()) {
            return data.get("timeoutSeconds").asLong();
        }
        return 600;
    }
}
