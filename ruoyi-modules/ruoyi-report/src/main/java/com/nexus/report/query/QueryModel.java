package com.nexus.report.query;

import com.nexus.common.report.ValueType;

import java.util.*;

/**
 * 受限查询模型一版，只含逻辑标识和类型化参数，不包含SQL或连接信息。
 *
 * @param formatVersion 契约格式版本
 * @param catalogVersion 当前契约的catalogVersion配置或状态
 * @param entity 当前契约的entity配置或状态
 * @param joins 当前契约的joins配置或状态
 * @param fields 当前契约的fields配置或状态
 * @param filters 当前契约的filters配置或状态
 * @param parameters 固定参数契约
 * @param groupBy 当前契约的groupBy配置或状态
 * @param sort 当前契约的sort配置或状态
 */
public record QueryModel(
        int formatVersion,
        int catalogVersion,
        String entity,
        List<String> joins,
        List<Select> fields,
        List<Filter> filters,
        List<Parameter> parameters,
        List<String> groupBy,
        List<Sort> sort) {
    /**
     * 冻结客户端集合，空集合统一为不可变列表。
     *
     * @param formatVersion 契约格式版本
     * @param catalogVersion 当前契约的catalogVersion配置或状态
     * @param entity 当前契约的entity配置或状态
     * @param joins 当前契约的joins配置或状态
     * @param fields 当前契约的fields配置或状态
     * @param filters 当前契约的filters配置或状态
     * @param parameters 固定参数契约
     * @param groupBy 当前契约的groupBy配置或状态
     * @param sort 当前契约的sort配置或状态
     */
    public QueryModel {
        joins = copy(joins);
        fields = copy(fields);
        filters = copy(filters);
        parameters = copy(parameters);
        groupBy = copy(groupBy);
        sort = copy(sort);
    }

    /**
     * 统一空集合与不可变复制。
     *
     * @param values 固定类型的精确值列表
     * @return 当前受控处理的固定结果
     */
    private static <T> List<T> copy(List<T> values) {
        return List.copyOf(values == null ? List.of() : values);
    }

    /**
     * 输出逻辑字段、受控别名和固定聚合函数。
     *
     * @param field 已批准的逻辑字段
     * @param alias 当前契约的alias配置或状态
     * @param aggregate 当前契约的aggregate配置或状态
     */
    public record Select(String field, String alias, String aggregate) {}

    /**
     * 通过声明参数绑定条件，不能提交字面SQL值或片段。
     *
     * @param field 已批准的逻辑字段
     * @param operator 当前契约的operator配置或状态
     * @param parameter 当前契约的parameter配置或状态
     */
    public record Filter(String field, String operator, String parameter) {}

    /**
     * 代码类型体系中的参数，不允许脚本类型或无界对象。
     *
     * @param name 契约名称
     * @param type 受控类型
     * @param required 是否必须提供该参数
     */
    public record Parameter(String name, ValueType type, boolean required) {}

    /**
     * 对受权输出逻辑字段进行稳定排序。
     *
     * @param field 已批准的逻辑字段
     * @param descending 当前契约的descending配置或状态
     */
    public record Sort(String field, boolean descending) {}
}
