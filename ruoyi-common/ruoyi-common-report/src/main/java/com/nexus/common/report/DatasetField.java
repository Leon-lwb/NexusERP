package com.nexus.common.report;

import java.util.*;

/**
 * 数据集逻辑字段，声明精确类型、敏感权限和聚合必须保留的维度。
 *
 * @param name 契约名称
 * @param label 用户可见标签
 * @param type 受控类型
 * @param permission 当前契约的permission配置或状态
 * @param aggregationKeys 当前契约的aggregationKeys配置或状态
 * @param aggregatable 当前契约的aggregatable配置或状态
 */
public record DatasetField(
        String name,
        String label,
        ValueType type,
        String permission,
        List<String> aggregationKeys,
        Boolean aggregatable) {
    /**
     * 初始化数据集字段并保存所需依赖或不可变值。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param label 展示标题
     * @param type 当前操作的 {@code type} 输入
     * @param permission 待核对权限
     * @param aggregationKeys 当前操作的 {@code aggregationKeys} 输入
     * @param aggregatable 当前操作的 {@code aggregatable} 输入
     */
    public DatasetField {
        aggregationKeys = List.copyOf(aggregationKeys == null ? List.of() : aggregationKeys);
        aggregatable = aggregatable == null ? true : aggregatable;
    }

    /**
     * 初始化数据集字段并保存所需依赖或不可变值。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param label 展示标题
     * @param type 当前操作的 {@code type} 输入
     * @param permission 待核对权限
     * @param aggregationKeys 当前操作的 {@code aggregationKeys} 输入
     */
    public DatasetField(
            String name,
            String label,
            ValueType type,
            String permission,
            List<String> aggregationKeys) {
        this(name, label, type, permission, aggregationKeys, true);
    }

    /**
     * 初始化数据集字段并保存所需依赖或不可变值。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param label 展示标题
     * @param type 当前操作的 {@code type} 输入
     */
    public DatasetField(String name, String label, ValueType type) {
        this(name, label, type, null, List.of(), true);
    }
}
