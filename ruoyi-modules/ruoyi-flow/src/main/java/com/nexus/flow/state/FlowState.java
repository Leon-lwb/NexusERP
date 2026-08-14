package com.nexus.flow.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 流程全局状态
 * <p>结构: {input: {...}, nodes: {nodeId: {output: {...}}}}</p>
 * <p>引擎控制写入时机，节点执行时读取快照（串行执行下天然一致）</p>
 *
 * @author NexusERP
 */
public class FlowState {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode root;

    /**
     * 新建流程状态
     *
     * @param input 运行入参（JSON），可为 null
     */
    public FlowState(JsonNode input) {
        this.root = MAPPER.createObjectNode();
        this.root.set("input", input == null ? MAPPER.createObjectNode() : input);
        this.root.set("nodes", MAPPER.createObjectNode());
    }

    /**
     * 按路径取值（支持点号 + 数组下标）
     * <p>路径语义（对齐 n8n 表达式缺失分层）：
     * <ul>
     *   <li>路径中节点不存在/未执行 → 抛 {@link IllegalArgumentException}（带节点名与提示）</li>
     *   <li>已存在节点的属性缺失 → 返回 {@link com.fasterxml.jackson.databind.node.MissingNode}</li>
     * </ul>
     * </p>
     * <p>并行安全：读操作与写入并发，synchronized 保证结构一致性</p>
     *
     * @param path 路径（如 input.warehouse、nodes.node_llm_1.output.content、chunks[0]）
     * @return 值节点；属性缺失返回 MissingNode（调用方渲染为空串）
     */
    public synchronized JsonNode get(String path) {
        if (path == null || path.isBlank()) {
            return MAPPER.missingNode();
        }
        // 支持简写 <nodeId>.output.xxx → nodes.<nodeId>.output.xxx
        String normalized = path;
        if (!normalized.startsWith("input") && !normalized.startsWith("nodes")) {
            normalized = "nodes." + normalized;
        }

        String[] segments = normalized.split("\\.");
        JsonNode current = root;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            // 处理数组下标: field[0]
            String field = segment;
            int bracketIdx = segment.indexOf('[');
            if (bracketIdx >= 0) {
                field = segment.substring(0, bracketIdx);
            }

            if (!field.isEmpty()) {
                if (current.isObject() && current.has(field)) {
                    current = current.get(field);
                } else if (current.isObject()) {
                    // 节点段缺失（nodes.<nodeId> 中的 nodeId 不存在）→ 结构化错误（对齐 n8n no_execution_data）
                    if ("nodes".equals(segments[0]) && i == 1 && segments.length > 1) {
                        throw new IllegalArgumentException("节点 " + field + " 的输出不存在（未执行或执行失败），无法引用 " + path);
                    }
                    // 属性缺失
                    return MAPPER.missingNode();
                } else {
                    return MAPPER.missingNode();
                }
            }

            // 处理连续数组下标
            int cursor = bracketIdx;
            while (cursor >= 0) {
                int closeIdx = segment.indexOf(']', cursor);
                if (closeIdx < 0) {
                    break;
                }
                String idxStr = segment.substring(cursor + 1, closeIdx);
                try {
                    int idx = Integer.parseInt(idxStr.trim());
                    if (current.isArray() && idx >= 0 && idx < current.size()) {
                        current = current.get(idx);
                    } else {
                        return MAPPER.missingNode();
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("无效的数组下标: " + idxStr);
                }
                cursor = segment.indexOf('[', closeIdx);
            }

            // 路径中间段缺失 → 节点未执行（结构化错误）
            if (i < segments.length - 1 && current.isMissingNode()) {
                String nodeHint = segments[0].replace("nodes.", "");
                throw new IllegalArgumentException("节点 " + nodeHint + " 的输出不存在（未执行或执行失败），无法引用 " + path);
            }
        }
        return current;
    }

    /**
     * 写入节点输出（并行安全：synchronized）
     *
     * @param nodeId 节点ID
     * @param output 输出 JSON
     */
    public synchronized void putNodeOutput(String nodeId, JsonNode output) {
        ObjectNode nodes = (ObjectNode) root.get("nodes");
        ObjectNode nodeEntry = MAPPER.createObjectNode();
        nodeEntry.set("output", output == null ? MAPPER.createObjectNode() : output);
        nodes.set(nodeId, nodeEntry);
    }

    /**
     * 获取运行入参（并行安全：synchronized）
     *
     * @return 入参 JSON
     */
    public synchronized JsonNode getInput() {
        return root.get("input");
    }

    /**
     * 获取末节点输出（引擎在流程结束时调用；并行安全：synchronized）
     *
     * @return 输出 JSON，无输出返回空对象
     */
    public synchronized JsonNode getFinalOutput() {
        JsonNode nodes = root.get("nodes");
        if (nodes == null || !nodes.isObject() || nodes.isEmpty()) {
            return MAPPER.createObjectNode();
        }
        // 返回最后写入的节点输出（LinkedHashMap 顺序）
        ObjectNode result = MAPPER.createObjectNode();
        nodes.fields().forEachRemaining(entry -> {
            JsonNode output = entry.getValue().get("output");
            if (output != null) {
                result.set(entry.getKey(), output);
            }
        });
        return result;
    }

    /**
     * 获取底层根节点（并行安全：synchronized；调用方仅在读快照时使用）
     *
     * @return 根节点
     */
    public synchronized ObjectNode getRoot() {
        return root;
    }
}
