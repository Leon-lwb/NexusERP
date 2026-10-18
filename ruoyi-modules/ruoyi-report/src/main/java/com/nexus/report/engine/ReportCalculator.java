package com.nexus.report.engine;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;

import java.math.*;
import java.util.*;

/** 按精确类型计算展示值，不将计算结果回写业务数据提供者。 */
public final class ReportCalculator {
    /**
     * 在每次执行独立的上下文中计算精确列、表达式和按维度隔离的汇总。
     *
     * @param label 用户可见标签
     * @param group 当前契约的group配置或状态
     * @param value 当前契约的value配置或状态
     */
    public record SummaryValue(String label, Map<String, TypedValue> group, TypedValue value) {

        /**
         * 初始化Summary值并保存所需依赖或不可变值。
         *
         * @param label 展示标题
         * @param group 当前操作的 {@code group} 输入
         * @param value 当前操作的 {@code value} 输入
         */
        public SummaryValue {
            group = Collections.unmodifiableMap(new LinkedHashMap<>(group));
        }
    }

    /**
     * CalculatedData，负责当前模块对应的契约或处理职责。
     *
     * @param rows 精确类型的数据行
     * @param summaries 当前契约的summaries配置或状态
     */
    public record CalculatedData(List<Map<String, TypedValue>> rows, List<SummaryValue> summaries) {

        /**
         * 初始化CalculatedData并保存所需依赖或不可变值。
         *
         * @param rows 待处理业务行
         * @param summaries 当前操作的 {@code summaries} 输入
         */
        public CalculatedData {
            rows =
                    rows.stream()
                            .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                            .toList();
            summaries = List.copyOf(summaries);
        }
    }

    /**
     * 基于精确值计算列与汇总。
     *
     * @param report 已编译报表
     * @param source 来源定义或资源
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    public CalculatedData calculate(
            CompiledReport report,
            List<Map<String, Object>> source,
            Map<String, Object> parameters) {
        if (source.size() > 100_000 || report.aggregateExpressions() && source.size() > 5000)
            throw ReportFault.capacity();
        long work =
                (long) source.size()
                        * report.expressions().values().stream()
                                .mapToInt(SafeExpression::cost)
                                .sum();
        if (work > 2_000_000) throw ReportFault.capacity();
        Map<String, Object> parameterValues = parameterVariables(report, parameters);
        Map<String, ValueType> resultTypes = expressionTypes(report);
        List<Map<String, TypedValue>> rows = new ArrayList<>();
        List<Map<String, Object>> values = new ArrayList<>();
        long characters = 0;
        for (Map<String, Object> raw : source) {
            Map<String, TypedValue> typed = typedRow(report, raw);
            for (TypedValue value : typed.values())
                if (value.value() != null) {
                    characters += value.value().length();
                    if (value.displayValue() != null) characters += value.displayValue().length();
                    if (characters > 10_000_000) throw ReportFault.capacity();
                }
            rows.add(typed);
            values.add(variables(typed, parameterValues));
        }
        for (String name : report.computedOrder()) {
            SafeExpression.Evaluation evaluation = report.expressions().get(name).session(values);
            for (int i = 0; i < rows.size(); i++) {
                Object computed = evaluation.evaluate(values.get(i));
                TypedValue typed = TypedValue.of(resultTypes.get(name), computed);
                rows.get(i).put(name, typed);
                values.get(i).put(name, scalar(typed));
            }
        }
        var accumulator = new SummaryAccumulator(report);
        rows.forEach(accumulator::accept);
        return new CalculatedData(rows, accumulator.finish());
    }

    /**
     * 计算单行精确展示值。
     *
     * @param report 已编译报表
     * @param source 来源定义或资源
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    public Map<String, TypedValue> calculateRow(
            CompiledReport report, Map<String, Object> source, Map<String, Object> parameters) {
        if (report.aggregateExpressions()) throw ReportFault.invalid("聚合计算需要有界快照");
        Map<String, Object> parameterValues = parameterVariables(report, parameters);
        Map<String, ValueType> resultTypes = expressionTypes(report);
        Map<String, TypedValue> result = typedRow(report, source);
        Map<String, Object> variables = variables(result, parameterValues);
        for (String name : report.computedOrder()) {
            Object computed =
                    report.expressions().get(name).evaluate(variables, List.of(variables));
            TypedValue typed = TypedValue.of(resultTypes.get(name), computed);
            result.put(name, typed);
            variables.put(name, scalar(typed));
        }
        return result;
    }

    /**
     * 完成 {@code expressionTypes} 对应的报表计算器处理。
     *
     * @param report 已编译报表
     * @return 当前处理返回的固定结果
     */
    private static Map<String, ValueType> expressionTypes(CompiledReport report) {
        Map<String, ValueType> types = new HashMap<>();
        report.descriptor().fields().forEach(field -> types.put(field.name(), field.type()));
        report.descriptor()
                .parameters()
                .forEach(parameter -> types.put("p_" + parameter.name(), parameter.type()));
        for (String name : report.computedOrder())
            types.put(name, report.expressions().get(name).validateTypes(types));
        return types;
    }

    /**
     * 完成 {@code typedRow} 对应的报表计算器处理。
     *
     * @param report 已编译报表
     * @param source 来源定义或资源
     * @return 当前处理返回的固定结果
     */
    private static Map<String, TypedValue> typedRow(
            CompiledReport report, Map<String, Object> source) {
        Map<String, TypedValue> row = new LinkedHashMap<>();
        for (String name : report.requiredFields()) {
            if (!source.containsKey(name))
                throw new ReportFault("MISSING_RESULT_FIELD", "数据集缺少必需字段: " + name);
            row.put(name, typed(report.descriptor().field(name).type(), source.get(name), name));
        }
        return row;
    }

    /**
     * 完成 {@code typed} 对应的报表计算器处理。
     *
     * @param type 当前操作的 {@code type} 输入
     * @param raw 当前操作的 {@code raw} 输入
     * @param name 当前操作的 {@code name} 输入
     * @return 当前处理返回的固定结果
     */
    private static TypedValue typed(ValueType type, Object raw, String name) {
        Object scalar = raw instanceof TypedValue typed ? typed.value() : raw;
        if (scalar != null && scalar.toString().length() > 10_000
                || raw instanceof TypedValue typed && typed.displayText().length() > 10_000)
            throw ReportFault.capacity();
        try {
            if (scalar != null && (type == ValueType.DECIMAL || type == ValueType.INTEGER))
                SafeExpression.checked(new BigDecimal(scalar.toString()));
            return TypedValue.of(type, raw);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw new ReportFault("INVALID_DATA_TYPE", "数据集字段或参数类型不匹配: " + name);
        }
    }

    /**
     * 完成 {@code parameterVariables} 对应的报表计算器处理。
     *
     * @param report 已编译报表
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    private static Map<String, Object> parameterVariables(
            CompiledReport report, Map<String, Object> parameters) {
        if (parameters == null) throw ReportFault.invalid("参数必须明确");
        Set<String> names = new HashSet<>();
        report.descriptor().parameters().forEach(parameter -> names.add(parameter.name()));
        if (!names.containsAll(parameters.keySet())) throw ReportFault.invalid("包含未登记的报表参数");
        Set<String> required = new HashSet<>();
        report.descriptor().parameters().stream()
                .filter(DatasetParameter::required)
                .forEach(parameter -> required.add(parameter.name()));
        report.dsl().parameters().stream()
                .filter(ReportDsl.Parameter::required)
                .forEach(parameter -> required.add(parameter.name()));
        for (String name : required)
            if (parameters.get(name) == null) throw ReportFault.invalid("缺少必填报表参数: " + name);
        Map<String, Object> result = new LinkedHashMap<>();
        for (DatasetParameter parameter : report.descriptor().parameters())
            result.put(
                    "p_" + parameter.name(),
                    scalar(
                            typed(
                                    parameter.type(),
                                    parameters.get(parameter.name()),
                                    parameter.name())));
        return result;
    }

    /**
     * 完成 {@code variables} 对应的报表计算器处理。
     *
     * @param row 当前精确业务行
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    private static Map<String, Object> variables(
            Map<String, TypedValue> row, Map<String, Object> parameters) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((name, value) -> result.put(name, scalar(value)));
        result.putAll(parameters);
        return result;
    }

    /**
     * 完成 {@code scalar} 对应的报表计算器处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    private static Object scalar(TypedValue value) {
        if (value == null || value.value() == null) return null;
        return switch (value.type()) {
            case DECIMAL, INTEGER -> new BigDecimal(value.value());
            case BOOLEAN -> Boolean.valueOf(value.value());
            default -> value.value();
        };
    }

    /**
     * 完成 {@code display} 对应的报表计算器处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @param scale 当前操作的 {@code scale} 输入
     * @return 当前处理返回的固定结果
     */
    public static String display(TypedValue value, int scale) {
        if (value == null || value.value() == null) return "";
        return value.type() == ValueType.DECIMAL
                ? new BigDecimal(value.value())
                        .setScale(scale, RoundingMode.HALF_UP)
                        .toPlainString()
                : value.displayText();
    }

    /** SummaryAccumulator，负责当前模块对应的契约或处理职责。 */
    public static final class SummaryAccumulator {
        /** 已编译报表。 */
        private final CompiledReport report;

        /** 保存当前 {@code groups} 值。 */
        private final List<LinkedHashMap<List<TypedValue>, Bucket>> groups = new ArrayList<>();

        /**
         * 初始化SummaryAccumulator并保存所需依赖或不可变值。
         *
         * @param report 已编译报表
         */
        public SummaryAccumulator(CompiledReport report) {
            this.report = report;
            report.dsl().summaries().forEach(s -> groups.add(new LinkedHashMap<>()));
        }

        /**
         * 将当前精确行加入本次计算会话。
         *
         * @param row 当前精确业务行
         */
        public void accept(Map<String, TypedValue> row) {
            for (int i = 0; i < report.dsl().summaries().size(); i++) {
                var definition = report.dsl().summaries().get(i);
                for (String name : definition.groupBy())
                    if (!row.containsKey(name))
                        throw new ReportFault("MISSING_RESULT_FIELD", "汇总缺少分组字段: " + name);
                if (!definition.function().equals("COUNT_ROWS")
                        && !row.containsKey(definition.field()))
                    throw new ReportFault(
                            "MISSING_RESULT_FIELD", "汇总缺少必需字段: " + definition.field());
                var key = definition.groupBy().stream().map(name -> groupValue(row, name)).toList();
                var group = groups.get(i);
                if (group.size() >= 10_000 && !group.containsKey(key)) throw ReportFault.capacity();
                var bucket = group.computeIfAbsent(key, k -> new Bucket());
                bucket.rows++;
                if (definition.function().equals("COUNT_ROWS")) continue;
                TypedValue value = row.get(definition.field());
                if (value == null || value.value() == null) continue;
                bucket.present++;
                if (Set.of("SUM", "MIN", "MAX").contains(definition.function())) {
                    BigDecimal number = summaryNumber(value);
                    bucket.value =
                            bucket.value == null
                                    ? number
                                    : definition.function().equals("SUM")
                                            ? bucket.value.add(number)
                                            : definition.function().equals("MIN")
                                                    ? bucket.value.min(number)
                                                    : bucket.value.max(number);
                    SafeExpression.checked(bucket.value);
                }
            }
        }

        /**
         * 完成 {@code groupValue} 对应的SummaryAccumulator处理。
         *
         * @param row 当前精确业务行
         * @param name 当前操作的 {@code name} 输入
         * @return 当前处理返回的固定结果
         */
        private TypedValue groupValue(Map<String, TypedValue> row, String name) {
            TypedValue value = row.get(name);
            if (value == null) return TypedValue.of(report.descriptor().field(name).type(), null);
            return value.type() == ValueType.DECIMAL && value.value() != null
                    ? TypedValue.of(ValueType.DECIMAL, summaryNumber(value).stripTrailingZeros())
                    : value;
        }

        /**
         * 完成 {@code summaryNumber} 对应的SummaryAccumulator处理。
         *
         * @param value 当前操作的 {@code value} 输入
         * @return 当前处理返回的固定结果
         */
        private static BigDecimal summaryNumber(TypedValue value) {
            try {
                return SafeExpression.checked(new BigDecimal(value.value()));
            } catch (NumberFormatException e) {
                throw new ReportFault("INVALID_DATA_TYPE", "汇总度量类型无效");
            }
        }

        /**
         * 关闭计算会话并返回固定结果。
         *
         * @return 当前处理返回的固定结果
         */
        public List<SummaryValue> finish() {
            List<SummaryValue> result = new ArrayList<>();
            for (int i = 0; i < report.dsl().summaries().size(); i++) {
                var definition = report.dsl().summaries().get(i);
                var group = groups.get(i);
                if (group.isEmpty() && definition.groupBy().isEmpty())
                    group.put(List.of(), new Bucket());
                for (var entry : group.entrySet()) {
                    Map<String, TypedValue> keys = new LinkedHashMap<>();
                    for (int j = 0; j < definition.groupBy().size(); j++)
                        keys.put(definition.groupBy().get(j), entry.getKey().get(j));
                    Object value =
                            switch (definition.function()) {
                                case "COUNT_ROWS" -> entry.getValue().rows;
                                case "COUNT" -> entry.getValue().present;
                                default ->
                                        entry.getValue().value == null
                                                ? null
                                                : entry.getValue()
                                                        .value
                                                        .setScale(
                                                                definition.scale(),
                                                                RoundingMode.HALF_UP);
                            };
                    result.add(
                            new SummaryValue(
                                    definition.label(),
                                    Collections.unmodifiableMap(keys),
                                    TypedValue.of(
                                            definition.function().startsWith("COUNT")
                                                    ? ValueType.INTEGER
                                                    : ValueType.DECIMAL,
                                            value)));
                }
            }
            return result;
        }

        /** Bucket，负责当前模块对应的契约或处理职责。 */
        private static final class Bucket {
            /** 待处理业务行。 */
            long rows;

            /** 保存当前 {@code present} 值。 */
            long present;

            /** 保存当前 {@code value} 值。 */
            BigDecimal value;
        }
    }
}
