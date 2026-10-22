package com.nexus.flow.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    /** 静态入度: nodeId -> 入度（loop 边与循环体内边不计） */
    private final Map<String, Integer> inDegree;

    /** 循环体内边集合（split 锚点 → 体首 及体内节点间边；loop 返回边与 done 边不计） */
    private final Set<String> loopBodyEdgeIds;

    /** 循环体内节点集合（含 split 锚点自身） */
    private final Set<String> loopBodyNodes;

    /** 入度为 0 的起点节点（排除循环体内节点——由 split 锚点驱动） */
    private final List<String> startNodeIds;

    /** 拓扑序（环检测通过后有效） */
    private final List<String> topologicalOrder;

    private FlowGraph(Map<String, FlowNodeDef> nodes, Map<String, FlowEdgeDef> edges) {
        this.nodes = nodes;
        this.edges = edges;
        this.outEdges = buildOutEdges();
        this.inEdges = buildInEdges();
        this.loopBodyNodes = buildLoopBodyNodes();
        this.loopBodyEdgeIds = buildLoopBodyEdgeIds();
        this.inDegree = buildInDegree();
        this.startNodeIds = buildStartNodes();
        this.topologicalOrder = topologicalSort();
        validateLoops();
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
                throw new IllegalArgumentException(
                        "边 " + def.getId() + " 引用了不存在的源节点: " + def.getSource());
            }
            if (!nodes.containsKey(def.getTarget())) {
                throw new IllegalArgumentException(
                        "边 " + def.getId() + " 引用了不存在的目标节点: " + def.getTarget());
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
                throw new IllegalArgumentException(
                        "未注册的节点类型: " + node.getType() + "（节点 " + node.getId() + "）");
            }
        }
    }

    /**
     * 拓扑排序（含环检测；loop 返回边豁免，循环体内边直接入队传播）
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
                if (edge.isLoopEdge()) {
                    // loop 返回边：不计入拓扑（循环体节点由体内边传播覆盖）
                    continue;
                }
                if (loopBodyEdgeIds.contains(edge.getId())) {
                    // 循环体内边：直接入队下游（线性链每轮唯一触发，不走入度）
                    queue.add(edge.getTarget());
                    continue;
                }
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
     * 构建循环体内节点集合（从 split 锚点沿 loop/main 边 DFS，排除 loop 返回边与 done 边）
     * <p>体节点包括 split 锚点自身 + 沿 outputs/loop/main 可达的节点（线性链）。
     * loop 返回边 target=split 锚点（source=循环体尾节点）</p>
     */
    private Set<String> buildLoopBodyNodes() {
        Set<String> body = new HashSet<>();
        for (FlowEdgeDef loopEdge : edges.values()) {
            if (!loopEdge.isLoopEdge()) {
                continue;
            }
            String splitId = loopEdge.getTarget(); // loop 边 target=split 锚点
            body.add(splitId);
            // 从 split 沿 loop/main 出边遍历（不经过 done 边与 loop 返回边）
            Deque<String> stack = new ArrayDeque<>();
            stack.push(splitId);
            while (!stack.isEmpty()) {
                String nodeId = stack.pop();
                for (FlowEdgeDef edge : outEdges.getOrDefault(nodeId, List.of())) {
                    if (edge.isLoopEdge() || edge.isDoneBranch()) {
                        continue;
                    }
                    if (body.add(edge.getTarget())) {
                        stack.push(edge.getTarget());
                    }
                }
            }
        }
        return body;
    }

    /**
     * 构建循环体内边集合（两端都在循环体内的边；loop 返回边与 done 边不计）
     */
    private Set<String> buildLoopBodyEdgeIds() {
        Set<String> bodyEdges = new HashSet<>();
        for (FlowEdgeDef edge : edges.values()) {
            if (edge.isLoopEdge() || edge.isDoneBranch()) {
                continue;
            }
            if (loopBodyNodes.contains(edge.getSource()) && loopBodyNodes.contains(edge.getTarget())) {
                bodyEdges.add(edge.getId());
            }
        }
        return bodyEdges;
    }

    /**
     * 构建静态入度（loop 返回边与循环体内边不计；merge 多入边照常累计）
     */
    private Map<String, Integer> buildInDegree() {
        Map<String, Integer> map = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            map.put(nodeId, 0);
        }
        for (FlowEdgeDef edge : edges.values()) {
            if (edge.isLoopEdge() || loopBodyEdgeIds.contains(edge.getId())) {
                continue;
            }
            map.merge(edge.getTarget(), 1, Integer::sum);
        }
        return map;
    }

    /**
     * 构建起点节点（入度为 0 且不在循环体内——循环体由 split 锚点驱动；split 锚点本身保留为起点）
     */
    private List<String> buildStartNodes() {
        List<String> starts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                String nodeId = entry.getKey();
                FlowNodeDef def = nodes.get(nodeId);
                boolean isSplitAnchor = def != null && "split".equals(def.getType());
                // 循环体内非 split 节点排除（由 split 驱动）；split 锚点保留为起点
                if (!loopBodyNodes.contains(nodeId) || isSplitAnchor) {
                    starts.add(nodeId);
                }
            }
        }
        // 起点按拓扑序稳定输出
        starts.sort((a, b) -> a.compareTo(b));
        return starts;
    }

    /**
     * 循环结构校验
     * <p>loop 返回边 target 必须是 split 锚点；循环体必须为线性链（体内节点出度 ≤1 且不跨入其他分支）</p>
     */
    private void validateLoops() {
        for (FlowEdgeDef loopEdge : edges.values()) {
            if (!loopEdge.isLoopEdge()) {
                continue;
            }
            String splitId = loopEdge.getTarget();
            FlowNodeDef splitDef = nodes.get(splitId);
            if (splitDef == null || !"split".equals(splitDef.getType())) {
                throw new IllegalArgumentException("loop 返回边 " + loopEdge.getId()
                    + " 的 target 必须是 split 循环节点（当前: " + splitId + "）");
            }
            // 循环体线性链校验：体内节点（除 split 锚点）出边必须全部在体内（无外溢分支）
            for (String bodyNodeId : loopBodyNodes) {
                if (bodyNodeId.equals(splitId)) {
                    continue;
                }
                for (FlowEdgeDef edge : outEdges.getOrDefault(bodyNodeId, List.of())) {
                    if (!edge.isLoopEdge() && !loopBodyNodes.contains(edge.getTarget())) {
                        throw new IllegalArgumentException("循环体内节点 " + bodyNodeId
                            + " 存在指向循环体外的边（循环体暂不支持分支/合并）: " + edge.getId());
                    }
                }
            }
        }
    }

    /**
     * 是否为循环体内部边（fanOut 时直接入队，不走入度递减）
     *
     * @param edgeId 边ID
     * @return 是否体内边
     */
    public boolean isLoopBodyEdge(String edgeId) {
        return loopBodyEdgeIds.contains(edgeId);
    }

    /**
     * 获取循环体节点集合
     *
     * @return 循环体节点ID集合
     */

    /** 实际循环归属用于稳定操作键；不同独立循环的游标不能互相影响。 */
    public String loopOwner(String nodeId) {
        String owner = null;
        for (FlowEdgeDef returning : edges.values()) {
            if (!returning.isLoopEdge()) continue;
            String anchor = returning.getTarget();
            Set<String> seen = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.push(anchor);
            while (!pending.isEmpty()) {
                String next = pending.pop();
                if (!seen.add(next)) continue;
                for (FlowEdgeDef edge : getOutEdges(next))
                    if (!edge.isLoopEdge() && !edge.isDoneBranch()) pending.push(edge.getTarget());
            }
            if (seen.contains(nodeId)) {
                if (owner != null && !owner.equals(anchor))
                    throw new IllegalStateException("节点属于多个循环，无法确定操作键");
                owner = anchor;
            }
        }
        return owner;
    }

    public Set<String> getLoopBodyNodes() {
        return loopBodyNodes;
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
