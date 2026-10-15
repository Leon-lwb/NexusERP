package com.nexus.common.report;

import java.util.*;

/**
 * 只读数据请求，限定参数、可见字段、页码、快照模式和可信排序。
 *
 * @param parameters 固定参数契约
 * @param fields 当前契约的fields配置或状态
 * @param pageNumber 当前契约的pageNumber配置或状态
 * @param pageSize 当前契约的pageSize配置或状态
 * @param snapshot 当前契约的snapshot配置或状态
 * @param sortField 当前契约的sortField配置或状态
 * @param descending 当前契约的descending配置或状态
 */
public record DatasetQuery(
        Map<String, Object> parameters,
        Set<String> fields,
        int pageNumber,
        int pageSize,
        boolean snapshot,
        String sortField,
        boolean descending) {
    /**
     * 初始化数据集查询并保存所需依赖或不可变值。
     *
     * @param parameters 按契约解析的输入参数
     * @param fields 允许访问的字段
     * @param pageNumber 当前操作的 {@code pageNumber} 输入
     * @param pageSize 当前操作的 {@code pageSize} 输入
     * @param snapshot 当前操作的 {@code snapshot} 输入
     * @param sortField 当前操作的 {@code sortField} 输入
     * @param descending 当前操作的 {@code descending} 输入
     */
    public DatasetQuery {
        parameters =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(parameters == null ? Map.of() : parameters));
        fields = Collections.unmodifiableSet(new LinkedHashSet<>(fields));
    }
}
