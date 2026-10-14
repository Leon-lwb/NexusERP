package com.nexus.report.pivot;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;

import java.util.*;

/** 编译交叉轴和口径约束，不接受动态脚本或客户端数值。 */
public final class PivotPlanner {
    /**
     * 校验轴、指标、容量和单位分区，返回所有必须保存的快照字段。
     *
     * @param definition 当前契约的definition配置或状态
     * @param dataset 当前契约的dataset配置或状态
     * @return 当前受控处理的固定结果
     */
    public Set<String> validate(PivotDefinition definition, DatasetDescriptor dataset) {
        if (definition == null
                || definition.rowDimensions().size() > 3
                || definition.columnDimensions().size() > 2
                || definition.measures().isEmpty()
                || definition.measures().size() > 5
                || definition.topN() < 0
                || definition.topN() > 100)
            throw ReportFault.invalid("交叉表最多3个行维度、2个列维度、5个指标，TopN范围0–100");
        var required = new LinkedHashSet<String>();
        for (String field : concat(definition.rowDimensions(), definition.columnDimensions())) {
            if (!required.add(field)) throw ReportFault.invalid("交叉轴不能重复");
            field(dataset, field);
        }
        var labels = new HashSet<String>();
        for (var measure : definition.measures()) {
            var field = field(dataset, measure.field());
            if (measure.label() == null
                    || measure.label().isBlank()
                    || measure.label().length() > 80
                    || !labels.add(measure.label())
                    || measure.scale() < 0
                    || measure.scale() > 12
                    || !Set.of("SUM", "COUNT", "COUNT_ROWS", "MIN", "MAX", "AVG")
                            .contains(measure.function())) throw ReportFault.invalid("交叉指标定义无效");
            if (!field.aggregatable()) throw ReportFault.invalid("字段不允许聚合");
            if (!measure.function().startsWith("COUNT")
                    && !Set.of(ValueType.DECIMAL, ValueType.INTEGER).contains(field.type()))
                throw ReportFault.invalid("交叉数值指标必须为精确数值");
            if (!measure.function().startsWith("COUNT")
                    && !definition.rowDimensions().containsAll(field.aggregationKeys()))
                throw ReportFault.invalid("数值汇总必须在行轴保留物料、单位或币种分区");
            required.add(measure.field());
            required.addAll(field.aggregationKeys());
        }
        return Collections.unmodifiableSet(required);
    }

    /**
     * 合并两组轴字段，保留用户明确给出的次序。
     *
     * @param left 当前契约的left配置或状态
     * @param right 当前契约的right配置或状态
     * @return 当前受控处理的固定结果
     */
    private static List<String> concat(List<String> left, List<String> right) {
        var result = new ArrayList<>(left);
        result.addAll(right);
        return result;
    }

    /**
     * 读取可信目录中的字段，未知字段使用稳定错误。
     *
     * @param dataset 当前契约的dataset配置或状态
     * @param name 契约名称
     * @return 当前受控处理的固定结果
     */
    private static DatasetField field(DatasetDescriptor dataset, String name) {
        return dataset.fields().stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow(() -> ReportFault.invalid("交叉字段未在数据集登记"));
    }
}
