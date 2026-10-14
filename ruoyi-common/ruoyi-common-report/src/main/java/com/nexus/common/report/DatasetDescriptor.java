package com.nexus.common.report;

import java.util.*;

/**
 * 代码版本化数据集契约，固定业务权限、字段类型、参数及默认列。
 *
 * @param code 当前契约的code配置或状态
 * @param name 契约名称
 * @param version 当前契约的version配置或状态
 * @param permission 当前契约的permission配置或状态
 * @param document 当前契约的document配置或状态
 * @param fields 当前契约的fields配置或状态
 * @param parameters 固定参数契约
 * @param defaultColumns 当前契约的defaultColumns配置或状态
 */
public record DatasetDescriptor(
        String code,
        String name,
        int version,
        String permission,
        boolean document,
        List<DatasetField> fields,
        List<DatasetParameter> parameters,
        List<String> defaultColumns) {
    /**
     * 初始化数据集契约描述并保存所需依赖或不可变值。
     *
     * @param code 业务编码
     * @param name 当前操作的 {@code name} 输入
     * @param version 固定版本
     * @param permission 待核对权限
     * @param document 当前操作的 {@code document} 输入
     * @param fields 允许访问的字段
     * @param parameters 按契约解析的输入参数
     * @param defaultColumns 当前操作的 {@code defaultColumns} 输入
     */
    public DatasetDescriptor {
        fields = List.copyOf(fields);
        parameters = List.copyOf(parameters);
        defaultColumns = List.copyOf(defaultColumns);
    }

    /**
     * 完成 {@code field} 对应的数据集契约描述处理。
     *
     * @param name 当前操作的 {@code name} 输入
     * @return 当前处理返回的固定结果
     */
    public DatasetField field(String name) {
        return fields.stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dataset field: " + name));
    }
}
