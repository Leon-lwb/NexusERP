package com.nexus.ai.flow.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 不可变图模型：由 config_json 解析
 * <p>提供拓扑排序（含环检测）、静态入度、出边索引，供引擎调度使用</p>
 *
 * @author NexusERP
 */
public class FlowGraph {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 节点集合: nodeId -> 节点定义 */
    private final Map<String, FlowNodeDef> nodes;

    /** 边集合 */
    private final Map<String, FlowEdgeDef> edges;

    /** 出边索引: nodeId -> 出边列表（保持顺序） */
    private final Map<String, List<FlowEdgeDef>> outEdges;

    /** 入边索引: nodeId -> 入边列表（merge 节点收集上游输出用） */
    private final Map<String, List<FlowEdgeDef>> inEdges;

    /** 静态入度: nodeId -> 入度 */
    private final Map<String, Integer> inDegree;

    /** 入度为 0 的起点节点 */
    private final List<String> startNodeIds;

    /** 拓扑序（环检测通过后有效） */
    private final List<String> topologicalOrder;

    private FlowGraph(Map<String, FlowNodeDef> nodes, Map<String, FlowEdgeDef> edges) {
        this.nodes = nodes;
        this.edges = edges;
        this.outEdges = buildOutEdges();
        this.inEdges = buildInEdges();
        this.inDegree = buildInDegree();
        this.startNodeIds = buildStartNodes();
        this.topologicalOrder = topologicalSort();
    }

    /**
     * 从 config_json 解析图
     *
     * @param configJson 画布配置 JSON 字符串
     * @return 图模型
     * @throws IllegalArgumentException JSON 非法或结构缺失
     */
    public static FlowGraph parse(String configJson) {
        try {
            JsonNode root = MAPPER.readTree(configJson);
            return parse(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("流程配置 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 从 config_json 根节点解析图
     *
     * @param root 画布配置根节点
     * @return 图模型
     */
    public static FlowGraph parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("流程配置必须为 JSON 对象");
        }
        JsonNode nodesNode = root.get("nodes");
        JsonNode edgesNode = root.get("edges");
        if (nodesNode == null || !nodesNode.isArray() || nodesNode.isEmpty()) {
            throw new IllegalArgumentException("流程配置缺少 nodes 节点");
        }
        if (edgesNode == null || !edgesNode.isArray()) {
            throw new IllegalArgumentException("流程配置缺少 edges 节点");
        }

        Map<String, FlowNodeDef> nodes = new LinkedHashMap<>();
        for (JsonNode node : nodesNode) {
            FlowNodeDef def = MAPPER.convertValue(node, FlowNodeDef.class);
            if (def.getId() == null || def.getId().isBlank()) {
                throw new IllegalArgumentException("节点缺少 id");
            }
            if (def.getType() == null || def.getType().isBlank()) {
                throw new IllegalArgumentException("节点 " + def.getId() + " 缺少 type");
            }
            if (nodes.containsKey(def.getId())) {
                throw new IllegalArgumentException("节点 id 重复: " + def.getId());
            }
            nodes.put(def.getId(), def);
        }

        Map<String, FlowEdgeDef> edges = new LinkedHashMap<>();
        for (JsonNode edge : edgesNode) {
            FlowEdgeDef def = MAPPER.convertValue(edge, FlowEdgeDef.class);
            def.setRaw(edge);
            if (def.getId() == null || def.getId().isBlank()) {
                throw new IllegalArgumentException("边缺少 id");
            }
            if (def.getSource() == null || def.getTarget() == null) {
                throw new IllegalArgumentException("边 " + def.getId() + " 缺少 source/target");
            }
            if (!nodes.containsKey(def.getSource())) {
                throw new IllegalArgumentException("边 " + def.getId() + " 引用了不存在的源节点: " + def.getSource());
            }
            if (!nodes.containsKey(def.getTarget())) {
                throw new IllegalArgumentException("边 " + def.getId() + " 引用了不存在的目标节点: " + def.getTarget());
            }
            edges.put(def.getId(), def);
        }

        return new FlowGraph(nodes, edges);
    }

    /**
     * 校验图合法性：拓扑排序（环检测）由构造时完成，此处补充节点类型注册校验
     *
     * @param typeValidator 节点类型校验器（校验 type 是否已注册）
     */
    public void validate(Predicate<String> typeValidator) {
        if (startNodeIds.isEmpty()) {
            throw new IllegalArgumentException("流程图中没有可执行的起点节点（入度为 0）");
        }
        for (FlowNodeDef node : nodes.values()) {
            if (!typeValidator.test(node.getType())) {
                throw new IllegalArgumentException("未注册的节点类型: " + node.getType() + "（节点 " + node.getId() + "）");
            }
        }
    }

    /**
     * 拓扑排序（含环检测）
     *
     * @return 拓扑序（节点ID列表）
     * @throws IllegalArgumentException 存在环
     */
    private List<String> topologicalSort() {
        Map<String, Integer> indeg = new HashMap<>(inDegree);
        Deque<String> queue = new ArrayDeque<>(startNodeIds);
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            order.add(nodeId);
            for (FlowEdgeDef edge : outEdges.getOrDefault(nodeId, List.of())) {
                String target = edge.getTarget();
                int remain = indeg.merge(target, -1, Integer::sum);
                if (remain == 0) {
                    queue.add(target);
                }
            }
        }
        if (order.size() != nodes.size()) {
            // 找出环内节点，给出可读错误
            Set<String> visited = new HashSet<>(order);
            List<String> inCycle = nodes.keySet().stream().filter(id -> !visited.contains(id)).toList();
            throw new IllegalArgumentException("流程图中存在环，涉及节点: " + inCycle);
        }
        return order;
    }

    /**
     * 构建出边索引
     */
    private Map<String, List<FlowEdgeDef>> buildOutEdges() {
        Map<String, List<FlowEdgeDef>> map = new HashMap<>();
        for (FlowEdgeDef edge : edges.values()) {
            map.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
        }
        return map;
    }

    /**
     * 构建入边索引
     */
    private Map<String, List<FlowEdgeDef>> buildInEdges() {
        Map<String, List<FlowEdgeDef>> map = new HashMap<>();
        for (FlowEdgeDef edge : edges.values()) {
            map.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge);
        }
        return map;
    }

    /**
     * 构建静态入度
     */
    private Map<String, Integer> buildInDegree() {
        Map<String, Integer> map = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            map.put(nodeId, 0);
        }
        for (FlowEdgeDef edge : edges.values()) {
            map.merge(edge.getTarget(), 1, Integer::sum);
        }
        return map;
    }

    /**
     * 构建起点节点（入度为 0）
     */
    private List<String> buildStartNodes() {
        List<String> starts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                starts.add(entry.getKey());
            }
        }
        // 起点按拓扑序稳定输出
        starts.sort((a, b) -> a.compareTo(b));
        return starts;
    }

    public Map<String, FlowNodeDef> getNodes() {
        return nodes;
    }

    public Map<String, FlowEdgeDef> getEdges() {
        return edges;
    }

    public List<FlowEdgeDef> getOutEdges(String nodeId) {
        return outEdges.getOrDefault(nodeId, List.of());
    }

    /**
     * 获取节点的入边列表
     *
     * @param nodeId 节点ID
     * @return 入边列表
     */
    public List<FlowEdgeDef> getInEdges(String nodeId) {
        return inEdges.getOrDefault(nodeId, List.of());
    }

    public int getInDegree(String nodeId) {
        return inDegree.getOrDefault(nodeId, 0);
    }

    public List<String> getStartNodeIds() {
        return startNodeIds;
    }

    public List<String> getTopologicalOrder() {
        return topologicalOrder;
    }

    public FlowNodeDef getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public int getNodeCount() {
        return nodes.size();
    }
}
