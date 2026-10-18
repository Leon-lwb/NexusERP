package com.nexus.report.dashboard;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;

import java.math.*;
import java.util.*;

/** 仪表盘指标以精确数值计算，图表坐标是独立且明确的归一化投影。 */
public final class WidgetCalculations {
    /**
     * 单点保留精确tooltip值和受控浮点坐标。
     *
     * @param label 用户可见标签
     * @param category 当前契约的category配置或状态
     * @param exact 当前契约的exact配置或状态
     * @param coordinate 本次固定快照的服务端钻取坐标
     */
    public record Point(String label, TypedValue category, TypedValue exact, double coordinate) {}

    /**
     * 图表坐标乘以此精确比例才能对应原始值，浏览器只使用坐标绘图。
     *
     * @param points 当前契约的points配置或状态
     * @param scale 输出小数位数
     */
    public record Chart(List<Point> points, String scale) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param points 当前受控操作的points输入
         * @param scale 当前受控操作的scale输入
         */
        public Chart {
            points = List.copyOf(points);
        }
    }

    /**
     * 在单一口径内计算完整源记录，AVG根据非空记录数加权。
     *
     * @param field 已批准的逻辑字段
     * @param function 白名单聚合函数
     * @param rows 精确类型的数据行
     * @return 当前受控处理的固定结果
     */
    public TypedValue metric(
            DatasetField field, String function, List<Map<String, TypedValue>> rows) {
        if (!Set.of("SUM", "COUNT", "COUNT_ROWS", "MIN", "MAX", "AVG").contains(function)
                || rows.size() > 5000) throw ReportFault.invalid("指标函数或源容量无效");
        var partitions = new HashSet<List<TypedValue>>();
        BigDecimal sum = BigDecimal.ZERO, min = null, max = null;
        long count = 0;
        for (var row : rows) {
            if (!function.startsWith("COUNT"))
                partitions.add(
                        field.aggregationKeys().stream()
                                .map(
                                        key -> {
                                            var value = row.get(key);
                                            if (value == null)
                                                throw ReportFault.invalid("指标快照缺少单位字段");
                                            return value;
                                        })
                                .toList());
            var value = row.get(field.name());
            if (value == null) throw ReportFault.invalid("指标字段未在快照保存");
            if (value.value() == null) continue;
            count++;
            if (!function.startsWith("COUNT")) {
                if (!Set.of(ValueType.DECIMAL, ValueType.INTEGER).contains(value.type()))
                    throw ReportFault.invalid("指标必须是精确数值");
                var number = new BigDecimal(value.value());
                sum = sum.add(number);
                min = min == null ? number : min.min(number);
                max = max == null ? number : max.max(number);
            }
        }
        if (partitions.size() > 1)
            throw new ReportFault("METRIC_MIXED_DIMENSIONS", "指标混合物料、单位或币种，请使用参数限制单一口径", 409);
        if (function.startsWith("COUNT"))
            return TypedValue.of(
                    ValueType.INTEGER, function.equals("COUNT_ROWS") ? rows.size() : count);
        BigDecimal result =
                switch (function) {
                    case "SUM" -> count == 0 ? null : sum;
                    case "AVG" ->
                            count == 0
                                    ? null
                                    : sum.divide(
                                            BigDecimal.valueOf(count), 12, RoundingMode.HALF_UP);
                    case "MIN" -> min;
                    case "MAX" -> max;
                    default -> throw ReportFault.invalid("指标函数无效");
                };
        return TypedValue.of(ValueType.DECIMAL, result);
    }

    /**
     * 基期为零或空值时变化率明确为空，不返回无穷大或伪造百分比。
     *
     * @param current 当前契约的current配置或状态
     * @param previous 当前契约的previous配置或状态
     * @return 当前受控处理的固定结果
     */
    public TypedValue rate(TypedValue current, TypedValue previous) {
        if (current.value() == null
                || previous.value() == null
                || new BigDecimal(previous.value()).signum() == 0)
            return TypedValue.of(ValueType.DECIMAL, null);
        var base = new BigDecimal(previous.value());
        return TypedValue.of(
                ValueType.DECIMAL,
                new BigDecimal(current.value())
                        .subtract(base)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(base.abs(), 6, RoundingMode.HALF_UP));
    }

    /**
     * 比较期间先验证每个期间自身的数值口径。
     *
     * @param field 已批准的逻辑字段
     * @param function 白名单聚合函数
     * @param current 当前契约的current配置或状态
     * @param previous 当前契约的previous配置或状态
     */
    public void comparable(
            DatasetField field,
            String function,
            List<Map<String, TypedValue>> current,
            List<Map<String, TypedValue>> previous) {
        metric(field, function, current);
        metric(field, function, previous);
        if (!function.startsWith("COUNT") && !current.isEmpty() && !previous.isEmpty()) {
            var a = field.aggregationKeys().stream().map(key -> current.get(0).get(key)).toList();
            var b = field.aggregationKeys().stream().map(key -> previous.get(0).get(key)).toList();
            if (!a.equals(b))
                throw new ReportFault("METRIC_MIXED_DIMENSIONS", "比较期间的物料、币种或单位不一致", 409);
        }
    }

    /**
     * 分类聚合后把坐标约束在[-1,1]，不损失精确值和分类原值。
     *
     * @param categoryField 当前契约的categoryField配置或状态
     * @param field 已批准的逻辑字段
     * @param function 白名单聚合函数
     * @param rows 精确类型的数据行
     * @return 当前受控处理的固定结果
     */
    public Chart chart(
            String categoryField,
            DatasetField field,
            String function,
            List<Map<String, TypedValue>> rows) {
        var groups = new LinkedHashMap<TypedValue, List<Map<String, TypedValue>>>();
        for (var row : rows) {
            var category = row.get(categoryField);
            if (category == null) throw ReportFault.invalid("图表分类字段缺失");
            groups.computeIfAbsent(category, key -> new ArrayList<>()).add(row);
        }
        if (groups.size() > 200) throw ReportFault.capacity();
        var values = new LinkedHashMap<TypedValue, TypedValue>();
        BigDecimal maximum = BigDecimal.ZERO;
        for (var entry : groups.entrySet()) {
            var value = metric(field, function, entry.getValue());
            values.put(entry.getKey(), value);
            if (value.value() != null) maximum = maximum.max(new BigDecimal(value.value()).abs());
        }
        var divisor = maximum.signum() == 0 ? BigDecimal.ONE : maximum;
        var points = new ArrayList<Point>();
        for (var entry : values.entrySet()) {
            var value = entry.getValue();
            double projection =
                    value.value() == null
                            ? 0
                            : new BigDecimal(value.value())
                                    .divide(divisor, 16, RoundingMode.HALF_UP)
                                    .doubleValue();
            points.add(
                    new Point(
                            entry.getKey().value() == null ? "（空）" : entry.getKey().displayText(),
                            entry.getKey(),
                            value,
                            projection));
        }
        return new Chart(points, divisor.toPlainString());
    }
}
