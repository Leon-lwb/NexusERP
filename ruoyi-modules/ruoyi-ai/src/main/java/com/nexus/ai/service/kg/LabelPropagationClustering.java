package com.nexus.ai.service.kg;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 加权标签传播社区检测算法（自实现，纯 Java 无依赖）
 * <p>v1 采用单层标签传播替代 Leiden，满足社区摘要的分簇需求；v2 可替换为 JGraphT/Neo4j GDS Leiden 提升质量。</p>
 * <p>输入：无向加权邻接表（entityId → neighborId → weight）；输出：entityId → 社区标签。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public final class LabelPropagationClustering {

    private LabelPropagationClustering() {
    }

    /**
     * 执行加权标签传播
     *
     * @param adjacency 邻接表（建议 LinkedHashMap 以保证迭代顺序稳定）
     * @param maxIter   最大迭代次数
     * @return entityId → 社区标签（标签为任意 Long 标识，同一标签即同一社区）
     */
    public static Map<Long, Long> cluster(Map<Long, Map<Long, Integer>> adjacency, int maxIter) {
        Map<Long, Long> labels = new LinkedHashMap<>();
        if (adjacency == null || adjacency.isEmpty()) {
            return labels;
        }
        // 初始：每个节点的标签为自身
        for (Long node : adjacency.keySet()) {
            labels.put(node, node);
        }
        boolean changed = true;
        int iter = 0;
        while (changed && iter < maxIter) {
            changed = false;
            iter++;
            for (Long node : adjacency.keySet()) {
                Map<Long, Integer> neighbors = adjacency.get(node);
                if (neighbors == null || neighbors.isEmpty()) {
                    continue;
                }
                // 加权投票：邻居标签 → 累计权重
                Map<Long, Long> vote = new LinkedHashMap<>();
                for (Map.Entry<Long, Integer> en : neighbors.entrySet()) {
                    Long neighborLabel = labels.get(en.getKey());
                    if (neighborLabel == null) {
                        continue;
                    }
                    vote.merge(neighborLabel, en.getValue().longValue(), Long::sum);
                }
                if (vote.isEmpty()) {
                    continue;
                }
                // 选权重最大的标签；并列时保留当前标签（避免振荡）
                long bestWeight = -1;
                Long bestLabel = null;
                for (Map.Entry<Long, Long> e : vote.entrySet()) {
                    if (e.getValue() > bestWeight) {
                        bestWeight = e.getValue();
                        bestLabel = e.getKey();
                    }
                }
                Long current = labels.get(node);
                if (bestLabel != null && !bestLabel.equals(current)) {
                    labels.put(node, bestLabel);
                    changed = true;
                }
            }
        }
        return labels;
    }
}
