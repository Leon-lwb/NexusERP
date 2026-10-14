package com.nexus.report.pivot;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.service.ReportDefinitionService;

import java.math.*;
import java.util.*;

/** 对同一次不可变快照进行精确聚合；钻取坐标只在该结果内部有效。 */
public final class AggregationExecutor {
    /**
     * 一个交叉单元格含固定指标值和可复算的钻取坐标。
     *
     * @param coordinate 本次固定快照的服务端钻取坐标
     * @param values 固定类型的精确值列表
     */
    public record Cell(String coordinate, List<TypedValue> values) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param coordinate 当前受控操作的coordinate输入
         * @param values 当前受控操作的values输入
         */
        public Cell {
            values = List.copyOf(values);
        }
    }

    /**
     * 明细、其他桶或小计行；总额从原始记录重新聚合。
     *
     * @param keys 固定类型的轴维度值
     * @param cells 固定坐标的单元格列表
     * @param total 当前契约的total配置或状态
     * @param other 是否为TopN后的其他桶
     * @param subtotal 是否为安全聚合分区内的小计
     */
    public record Row(
            List<TypedValue> keys,
            List<Cell> cells,
            List<TypedValue> total,
            boolean other,
            boolean subtotal) {
        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param keys 当前受控操作的keys输入
         * @param cells 当前受控操作的cells输入
         * @param total 当前受控操作的total输入
         * @param other 当前受控操作的other输入
         * @param subtotal 当前受控操作的subtotal输入
         */
        public Row {
            keys = List.copyOf(keys);
            cells = List.copyOf(cells);
            total = List.copyOf(total);
        }
    }

    /**
     * 总计独立保留单位分区，禁止把不同币种或计量单位相加。
     *
     * @param partition 单位或币种等聚合分区
     * @param values 固定类型的精确值列表
     * @param cells 固定坐标的单元格列表
     */
    public record Total(
            Map<String, TypedValue> partition, List<TypedValue> values, List<Cell> cells) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param partition 当前受控操作的partition输入
         * @param values 当前受控操作的values输入
         * @param cells 固定坐标的单元格列表
         */
        public Total {
            partition = Collections.unmodifiableMap(new LinkedHashMap<>(partition));
            values = List.copyOf(values);
            cells = List.copyOf(cells);
        }
    }

    /**
     * 交叉结果保留原始行数、精确轴值、指标及分区总计。
     *
     * @param columns 输出列契约
     * @param rows 精确类型的数据行
     * @param totals 按聚合分区计算的总计
     * @param sourceRows 原始快照的数据行数
     */
    public record Result(
            List<List<TypedValue>> columns, List<Row> rows, List<Total> totals, long sourceRows) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param columns 当前受控操作的columns输入
         * @param rows 当前受控操作的rows输入
         * @param totals 当前受控操作的totals输入
         * @param sourceRows 当前受控操作的sourceRows输入
         */
        public Result {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            totals = List.copyOf(totals);
        }
    }

    /**
     * 钻取结果返回同一快照内的原始记录。
     *
     * @param rows 精确类型的数据行
     * @param total 当前契约的total配置或状态
     */
    public record Drill(List<Map<String, TypedValue>> rows, long total) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param rows 当前受控操作的rows输入
         * @param total 当前受控操作的total输入
         */
        public Drill {
            rows = List.copyOf(rows);
        }
    }

    /**
     * 内部坐标与原始行索引，不能由客户端指定聚合数据。
     *
     * @param result 当前契约的result配置或状态
     * @param members 当前契约的members配置或状态
     */
    private record Built(Result result, Map<String, List<Integer>> members) {}

    /**
     * 同类型轴值保证空值与空字符串不合并。
     *
     * @param values 固定类型的精确值列表
     */
    private record Axis(List<TypedValue> values) {
        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param values 当前受控操作的values输入
         */
        Axis {
            values = List.copyOf(values);
        }
    }

    /**
     * 按固定快照构造稀疏交叉表。
     *
     * @param definition 当前契约的definition配置或状态
     * @param dataset 当前契约的dataset配置或状态
     * @param rows 精确类型的数据行
     * @return 当前受控处理的固定结果
     */
    public Result aggregate(
            PivotDefinition definition,
            DatasetDescriptor dataset,
            List<Map<String, TypedValue>> rows) {
        return build(definition, dataset, rows).result();
    }

    /**
     * 验证坐标后分页读取原始快照，拒绝伪造坐标和过量读取。
     *
     * @param definition 当前契约的definition配置或状态
     * @param dataset 当前契约的dataset配置或状态
     * @param rows 精确类型的数据行
     * @param coordinate 本次固定快照的服务端钻取坐标
     * @param offset 当前契约的offset配置或状态
     * @param size 当前契约的size配置或状态
     * @return 当前受控处理的固定结果
     */
    public Drill drill(
            PivotDefinition definition,
            DatasetDescriptor dataset,
            List<Map<String, TypedValue>> rows,
            String coordinate,
            int offset,
            int size) {
        if (offset < 0 || size < 1 || size > 200) throw ReportFault.invalid("钻取分页无效");
        var members = build(definition, dataset, rows).members().get(coordinate);
        if (members == null) throw ReportFault.invalid("钻取坐标不属于当前快照");
        return new Drill(
                members.stream().skip(offset).limit(size).map(rows::get).toList(), members.size());
    }

    /**
     * 构造分区、动态轴、TopN和受限小计，并记录稳定成员集合。
     *
     * @param definition 当前契约的definition配置或状态
     * @param dataset 当前契约的dataset配置或状态
     * @param rows 精确类型的数据行
     * @return 当前受控处理的固定结果
     */
    private Built build(
            PivotDefinition definition,
            DatasetDescriptor dataset,
            List<Map<String, TypedValue>> rows) {
        new PivotPlanner().validate(definition, dataset);
        if (rows.size() > 100000) throw ReportFault.capacity();
        var columns = new LinkedHashSet<Axis>();
        var groups = new LinkedHashMap<Axis, List<Integer>>();
        var partitionFields = new LinkedHashSet<String>();
        for (var measure : definition.measures())
            if (!measure.function().startsWith("COUNT"))
                partitionFields.addAll(dataset.field(measure.field()).aggregationKeys());
        var partitions = new LinkedHashMap<Axis, List<Axis>>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var r = axis(row, definition.rowDimensions());
            var c = axis(row, definition.columnDimensions());
            columns.add(c);
            if (columns.size() * definition.measures().size() > 200) throw ReportFault.capacity();
            groups.computeIfAbsent(r, key -> new ArrayList<>()).add(i);
        }
        if (columns.isEmpty()) columns.add(new Axis(List.of()));
        for (var entry : groups.entrySet()) {
            var row = rows.get(entry.getValue().get(0));
            partitions
                    .computeIfAbsent(
                            axis(row, new ArrayList<>(partitionFields)), key -> new ArrayList<>())
                    .add(entry.getKey());
        }
        var resultRows = new ArrayList<Row>();
        var totals = new ArrayList<Total>();
        var members = new LinkedHashMap<String, List<Integer>>();
        var budget = new OutputBudget(columns.size(), definition.measures().size());
        for (var partition : partitions.entrySet()) {
            var ordered = new ArrayList<>(partition.getValue());
            if (definition.topN() > 0)
                ordered.sort(
                        (a, b) ->
                                compare(
                                        score(definition.measures().get(0), rows, groups.get(b)),
                                        score(definition.measures().get(0), rows, groups.get(a))));
            var all = new ArrayList<Integer>();
            for (var key : partition.getValue()) all.addAll(groups.get(key));
            Collections.sort(all);
            int keep =
                    definition.topN() == 0
                            ? ordered.size()
                            : Math.min(definition.topN(), ordered.size());
            for (int i = 0; i < keep; i++)
                resultRows.add(
                        render(
                                definition,
                                rows,
                                columns,
                                ordered.get(i).values(),
                                groups.get(ordered.get(i)),
                                false,
                                false,
                                members,
                                budget));
            if (keep < ordered.size()) {
                var rest = new ArrayList<Integer>();
                for (int i = keep; i < ordered.size(); i++) rest.addAll(groups.get(ordered.get(i)));
                Collections.sort(rest);
                var keys = new ArrayList<TypedValue>();
                for (String field : definition.rowDimensions()) {
                    int pi = new ArrayList<>(partitionFields).indexOf(field);
                    keys.add(
                            pi >= 0
                                    ? partition.getKey().values().get(pi)
                                    : TypedValue.of(ValueType.STRING, "其他"));
                }
                resultRows.add(
                        render(
                                definition,
                                rows,
                                columns,
                                keys,
                                rest,
                                true,
                                false,
                                members,
                                budget));
            }
            if (definition.subtotals()
                    && definition.rowDimensions().size() > 1
                    && definition.topN() == 0) {
                for (int depth = 1; depth < definition.rowDimensions().size(); depth++) {
                    if (!definition.rowDimensions().subList(0, depth).containsAll(partitionFields))
                        continue;
                    var buckets = new LinkedHashMap<Axis, List<Integer>>();
                    for (int index : all)
                        buckets.computeIfAbsent(
                                        axis(
                                                rows.get(index),
                                                definition.rowDimensions().subList(0, depth)),
                                        key -> new ArrayList<>())
                                .add(index);
                    for (var bucket : buckets.entrySet())
                        resultRows.add(
                                render(
                                        definition,
                                        rows,
                                        columns,
                                        bucket.getKey().values(),
                                        bucket.getValue(),
                                        false,
                                        true,
                                        members,
                                        budget));
                }
            }
            if (definition.totals()) {
                var p = new LinkedHashMap<String, TypedValue>();
                int i = 0;
                for (String field : partitionFields)
                    p.put(field, partition.getKey().values().get(i++));
                var totalRow =
                        render(
                                definition,
                                rows,
                                columns,
                                partition.getKey().values(),
                                all,
                                false,
                                true,
                                members,
                                budget);
                totals.add(new Total(p, totalRow.total(), totalRow.cells()));
            }
        }
        if ((long) (resultRows.size() + totals.size())
                        * (columns.size() + 1)
                        * definition.measures().size()
                > 20000) throw ReportFault.capacity();
        return new Built(
                new Result(
                        columns.stream().map(Axis::values).toList(),
                        resultRows,
                        totals,
                        rows.size()),
                members);
    }

    /**
     * 为一个行桶生成每列指标，同时把钻取成员保存在服务端。
     *
     * @param definition 当前契约的definition配置或状态
     * @param rows 精确类型的数据行
     * @param columns 输出列契约
     * @param keys 固定类型的轴维度值
     * @param indices 当前契约的indices配置或状态
     * @param other 是否为TopN后的其他桶
     * @param subtotal 是否为安全聚合分区内的小计
     * @param members 当前契约的members配置或状态
     * @return 当前受控处理的固定结果
     * @param budget 当前契约的budget配置或状态
     */
    private Row render(
            PivotDefinition definition,
            List<Map<String, TypedValue>> rows,
            Set<Axis> columns,
            List<TypedValue> keys,
            List<Integer> indices,
            boolean other,
            boolean subtotal,
            Map<String, List<Integer>> members,
            OutputBudget budget) {
        budget.take();
        var cells = new ArrayList<Cell>();
        for (var column : columns) {
            var selected =
                    indices.stream()
                            .filter(
                                    index ->
                                            axis(rows.get(index), definition.columnDimensions())
                                                    .equals(column))
                            .toList();
            String coordinate =
                    ReportDefinitionService.hash(
                            keys.toString()
                                    + "|"
                                    + column.values()
                                    + "|"
                                    + other
                                    + "|"
                                    + subtotal
                                    + "|"
                                    + selected);
            members.put(coordinate, selected);
            cells.add(new Cell(coordinate, values(definition, rows, selected)));
        }
        return new Row(keys, cells, values(definition, rows, indices), other, subtotal);
    }

    /**
     * 使用类型化字段值生成轴，不通过字符串拼接合并维度。
     *
     * @param row 当前契约的row配置或状态
     * @param fields 当前契约的fields配置或状态
     * @return 当前受控处理的固定结果
     */
    private static Axis axis(Map<String, TypedValue> row, List<String> fields) {
        var values = new ArrayList<TypedValue>();
        for (String field : fields) {
            if (!row.containsKey(field)) throw ReportFault.invalid("交叉快照缺少字段");
            values.add(row.get(field));
        }
        return new Axis(values);
    }

    /**
     * 每个指标直接处理原始记录；AVG绝不计算平均值的平均值。
     *
     * @param definition 当前契约的definition配置或状态
     * @param rows 精确类型的数据行
     * @param indices 当前契约的indices配置或状态
     * @return 当前受控处理的固定结果
     */
    private static List<TypedValue> values(
            PivotDefinition definition, List<Map<String, TypedValue>> rows, List<Integer> indices) {
        var result = new ArrayList<TypedValue>();
        for (var measure : definition.measures()) {
            BigDecimal sum = BigDecimal.ZERO, min = null, max = null;
            long count = 0;
            for (int index : indices) {
                var value = rows.get(index).get(measure.field());
                if (value == null) throw ReportFault.invalid("交叉快照缺少指标");
                if (value.value() == null) continue;
                count++;
                if (!measure.function().startsWith("COUNT")) {
                    BigDecimal number = decimal(value);
                    sum = sum.add(number);
                    min = min == null ? number : min.min(number);
                    max = max == null ? number : max.max(number);
                }
            }
            if (measure.function().startsWith("COUNT")) {
                result.add(
                        TypedValue.of(
                                ValueType.INTEGER,
                                measure.function().equals("COUNT_ROWS") ? indices.size() : count));
                continue;
            }
            BigDecimal value =
                    switch (measure.function()) {
                        case "SUM" -> count == 0 ? null : sum;
                        case "AVG" ->
                                count == 0
                                        ? null
                                        : sum.divide(
                                                BigDecimal.valueOf(count),
                                                measure.scale(),
                                                RoundingMode.HALF_UP);
                        case "MIN" -> min;
                        case "MAX" -> max;
                        default -> throw ReportFault.invalid("聚合函数无效");
                    };
            result.add(
                    TypedValue.of(
                            ValueType.DECIMAL,
                            value == null
                                    ? null
                                    : value.setScale(measure.scale(), RoundingMode.HALF_UP)));
        }
        return List.copyOf(result);
    }

    /**
     * 排名以分子和分母表示AVG，完全避免显示精度舍入。
     *
     * @param numerator 当前契约的numerator配置或状态
     * @param denominator 当前契约的denominator配置或状态
     */
    private record Score(BigDecimal numerator, BigDecimal denominator) {}

    /**
     * 从原始指标计算排名分数，空聚合排在所有非空值之后。
     *
     * @param measure 当前契约的measure配置或状态
     * @param rows 精确类型的数据行
     * @param indices 当前契约的indices配置或状态
     * @return 当前受控处理的固定结果
     */
    private static Score score(
            PivotDefinition.Measure measure,
            List<Map<String, TypedValue>> rows,
            List<Integer> indices) {
        BigDecimal sum = BigDecimal.ZERO, min = null, max = null;
        long count = 0;
        for (int index : indices) {
            var value = rows.get(index).get(measure.field());
            if (value.value() == null) continue;
            count++;
            if (!measure.function().startsWith("COUNT")) {
                var number = new BigDecimal(value.value());
                sum = sum.add(number);
                min = min == null ? number : min.min(number);
                max = max == null ? number : max.max(number);
            }
        }
        if (measure.function().startsWith("COUNT"))
            return new Score(
                    BigDecimal.valueOf(
                            measure.function().equals("COUNT_ROWS") ? indices.size() : count),
                    BigDecimal.ONE);
        if (count == 0) return new Score(null, BigDecimal.ONE);
        return switch (measure.function()) {
            case "AVG" -> new Score(sum, BigDecimal.valueOf(count));
            case "MIN" -> new Score(min, BigDecimal.ONE);
            case "MAX" -> new Score(max, BigDecimal.ONE);
            default -> new Score(sum, BigDecimal.ONE);
        };
    }

    /**
     * 通过交叉相乘比较精确有理数。
     *
     * @param a 当前契约的a配置或状态
     * @param b 当前契约的b配置或状态
     * @return 当前受控处理的固定结果
     */
    private static int compare(Score a, Score b) {
        if (a.numerator() == null) return b.numerator() == null ? 0 : -1;
        if (b.numerator() == null) return 1;
        return a.numerator()
                .multiply(b.denominator())
                .compareTo(b.numerator().multiply(a.denominator()));
    }

    /**
     * 从精确字符串读取数值，空值只在排序时视为零。
     *
     * @param value 当前契约的value配置或状态
     * @return 当前受控处理的固定结果
     */
    private static BigDecimal decimal(TypedValue value) {
        return value.value() == null ? BigDecimal.ZERO : new BigDecimal(value.value());
    }

    /** 按实际生成的指标行计量，在分配单元格或钻取坐标之前阻断超限。 */
    private static final class OutputBudget {
        /** 每个输出行含列指标及行合计的单元格数量。 */
        private final long cellsPerRow;

        /** 已分配的输出指标单元格数量。 */
        private long cells;

        /**
         * 固定动态列及指标数量，小计、其他桶和总计复用同一预算。
         *
         * @param columns 输出列契约
         * @param measures 当前契约的measures配置或状态
         */
        private OutputBudget(int columns, int measures) {
            cellsPerRow = (long) (columns + 1) * measures;
        }

        /** 在新输出行展开前原子推进本次计算的局部预算。 */
        private void take() {
            cells += cellsPerRow;
            if (cells > 20000) throw ReportFault.capacity();
        }
    }
}
