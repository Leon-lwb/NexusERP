package com.nexus.common.report;

import java.time.Instant;
import java.util.*;

/**
 * 只读取数结果，包含实际数据时间、精确原始行和总行数。
 *
 * @param rows 精确类型的数据行
 * @param total 当前契约的total配置或状态
 * @param dataTime 当前契约的dataTime配置或状态
 */
public record DatasetResult(List<Map<String, Object>> rows, long total, Instant dataTime) {

    /**
     * 初始化数据集结果并保存所需依赖或不可变值。
     *
     * @param rows 待处理业务行
     * @param total 当前操作的 {@code total} 输入
     * @param dataTime 当前操作的 {@code dataTime} 输入
     */
    public DatasetResult {
        rows =
                rows.stream()
                        .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                        .toList();
        Objects.requireNonNull(dataTime);
    }
}
