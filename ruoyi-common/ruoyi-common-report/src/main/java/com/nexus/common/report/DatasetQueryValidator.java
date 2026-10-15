package com.nexus.common.report;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 数据集查询校验器，负责当前模块对应的契约或处理职责。 */
public final class DatasetQueryValidator {
    /** 初始化数据集查询校验器并保存所需依赖或不可变值。 */
    private DatasetQueryValidator() {}

    /**
     * 完成 {@code parameters} 对应的数据集查询校验器处理。
     *
     * @param descriptor 可信数据集契约
     * @param input 待校验输入
     * @return 当前处理返回的固定结果
     */
    public static Map<String, Object> parameters(
            DatasetDescriptor descriptor, Map<String, Object> input) {
        if (input == null) input = Map.of();
        if (input.size() > 30) throw invalid();
        Set<String> names = new HashSet<>();
        descriptor.parameters().forEach(p -> names.add(p.name()));
        if (!names.containsAll(input.keySet()))
            throw new DatasetAccessException("INVALID_PARAMETER", "包含未登记或保留参数");
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (DatasetParameter parameter : descriptor.parameters()) {
            Object raw = input.get(parameter.name());
            if ((raw == null || raw instanceof String text && text.isBlank())
                    && parameter.defaultValue() != null) {
                raw = parameter.defaultValue();
                if (parameter.type() == ValueType.DATE && raw.equals("TODAY"))
                    raw = LocalDate.now().toString();
                if (parameter.type() == ValueType.DATE && raw.equals("MONTHS_AGO_6"))
                    raw = LocalDate.now().minusMonths(6).toString();
            }
            if (raw == null || raw instanceof String text && text.isBlank()) {
                if (parameter.required())
                    throw new DatasetAccessException(
                            "INVALID_PARAMETER", "缺少必填参数: " + parameter.name());
                continue;
            }
            if (!(raw instanceof String
                    || raw instanceof Number
                    || raw instanceof Boolean
                    || raw instanceof LocalDate)) throw invalid();
            String value = raw.toString();
            if (value.length() > 500) throw invalid();
            try {
                Object converted =
                        switch (parameter.type()) {
                            case ID -> {
                                if (!value.matches("[1-9][0-9]{0,18}")) throw invalid();
                                yield Long.parseLong(value);
                            }
                            case INTEGER -> Long.parseLong(value);
                            case DECIMAL -> {
                                BigDecimal number = new BigDecimal(value);
                                if (number.precision() > 38 || Math.abs(number.scale()) > 12)
                                    throw invalid();
                                yield number;
                            }
                            case BOOLEAN -> {
                                if (!value.equals("true") && !value.equals("false"))
                                    throw invalid();
                                yield Boolean.valueOf(value);
                            }
                            case DATE -> LocalDate.parse(value).toString();
                            case DATETIME -> OffsetDateTime.parse(value).toString();
                            case STRING -> value;
                        };
                normalized.put(parameter.name(), converted);
            } catch (RuntimeException e) {
                throw new DatasetAccessException(
                        "INVALID_PARAMETER", "参数类型无效: " + parameter.name());
            }
        }
        if (normalized.containsKey("fromDate") && normalized.containsKey("toDate")) {
            LocalDate from = LocalDate.parse(normalized.get("fromDate").toString()),
                    to = LocalDate.parse(normalized.get("toDate").toString());
            if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 3660)
                throw new DatasetAccessException("INVALID_PARAMETER", "日期范围无效或超过十年");
        }
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * 检查定义或查询是否满足受限契约。
     *
     * @param descriptor 可信数据集契约
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    public static DatasetQuery validate(DatasetDescriptor descriptor, DatasetQuery query) {
        if (query.pageNumber() < 1
                || query.pageNumber() > 1_000_000
                || query.pageSize() < 1
                || query.pageSize() > (query.snapshot() ? 100001 : 200)) throw invalid();
        Set<String> fields = new HashSet<>();
        descriptor.fields().forEach(field -> fields.add(field.name()));
        if (!fields.containsAll(query.fields())
                || query.fields().size() > 60
                || query.sortField() != null
                        && (!query.fields().contains(query.sortField())
                                || !fields.contains(query.sortField())))
            throw new DatasetAccessException("INVALID_PARAMETER", "字段或排序不在允许目录中");
        return new DatasetQuery(
                parameters(descriptor, query.parameters()),
                query.fields(),
                query.pageNumber(),
                query.pageSize(),
                query.snapshot(),
                query.sortField(),
                query.descending());
    }

    /**
     * 完成 {@code invalid} 对应的数据集查询校验器处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static DatasetAccessException invalid() {
        return new DatasetAccessException("INVALID_PARAMETER", "报表查询参数无效");
    }
}
