package com.nexus.report.pivot;

import java.util.*;

/**
 * DSL第二版的交叉分析契约；轴和指标只能引用可信数据集字段。
 *
 * @param rowDimensions 当前契约的rowDimensions配置或状态
 * @param columnDimensions 当前契约的columnDimensions配置或状态
 * @param measures 当前契约的measures配置或状态
 * @param subtotals 当前契约的subtotals配置或状态
 * @param totals 按聚合分区计算的总计
 * @param topN 每分区保留的排名桶数量
 */
public record PivotDefinition(
        List<String> rowDimensions,
        List<String> columnDimensions,
        List<Measure> measures,
        boolean subtotals,
        boolean totals,
        int topN) {
    /**
     * 固定轴和指标集合，阻止发布后被调用方修改。
     *
     * @param rowDimensions 当前契约的rowDimensions配置或状态
     * @param columnDimensions 当前契约的columnDimensions配置或状态
     * @param measures 当前契约的measures配置或状态
     * @param subtotals 当前契约的subtotals配置或状态
     * @param totals 按聚合分区计算的总计
     * @param topN 每分区保留的排名桶数量
     */
    public PivotDefinition {
        rowDimensions = List.copyOf(rowDimensions == null ? List.of() : rowDimensions);
        columnDimensions = List.copyOf(columnDimensions == null ? List.of() : columnDimensions);
        measures = List.copyOf(measures == null ? List.of() : measures);
    }

    /**
     * 指标使用白名单聚合函数，精度仅影响最终输出舍入。
     *
     * @param field 已批准的逻辑字段
     * @param function 白名单聚合函数
     * @param label 用户可见标签
     * @param scale 输出小数位数
     */
    public record Measure(String field, String function, String label, int scale) {}
}
