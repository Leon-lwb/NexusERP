package com.nexus.report.pivot;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.common.report.ReportCancellation;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.export.ReportSpreadsheetExporter;
import com.nexus.report.service.ReportDefinitionService;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 交叉表用固定精确行验证总计、空维度、钻取和容量。 */
class ReportPivotTest {
    /**
     * 合成目录保留数量的单位口径。
     *
     * @return 当前受控处理的固定结果
     */
    private DatasetDescriptor descriptor() {
        return new DatasetDescriptor(
                "SAMPLE",
                "测试",
                1,
                "sample:list",
                false,
                List.of(
                        new DatasetField("category", "类别", ValueType.STRING, null, List.of(), true),
                        new DatasetField("period", "期间", ValueType.STRING, null, List.of(), true),
                        new DatasetField("amount", "数值", ValueType.DECIMAL, null, List.of(), true)),
                List.of(),
                List.of("category", "period", "amount"));
    }

    /**
     * 生成不经过浮点数的原始快照行。
     *
     * @param category 当前契约的category配置或状态
     * @param period 当前契约的period配置或状态
     * @param amount 当前契约的amount配置或状态
     * @return 当前受控处理的固定结果
     */
    private Map<String, TypedValue> row(String category, String period, String amount) {
        var row = new LinkedHashMap<String, TypedValue>();
        row.put("category", TypedValue.of(ValueType.STRING, category));
        row.put("period", TypedValue.of(ValueType.STRING, period));
        row.put("amount", TypedValue.of(ValueType.DECIMAL, amount));
        return row;
    }

    /**
     * 明确行列轴、指标和TopN。
     *
     * @param function 白名单聚合函数
     * @param top 当前契约的top配置或状态
     * @return 当前受控处理的固定结果
     */
    private PivotDefinition pivot(String function, int top) {
        return new PivotDefinition(
                List.of("category"),
                List.of("period"),
                List.of(new PivotDefinition.Measure("amount", function, "值", 3)),
                true,
                true,
                top);
    }

    /** 执行grandAverageUsesRawSumAndCountAndDecimalsStayExact对应的受控处理。 */
    @Test
    void grandAverageUsesRawSumAndCountAndDecimalsStayExact() {
        var rows =
                List.of(
                        row("A", "一", "9007199254740993.005"),
                        row("A", "二", "1.005"),
                        row("A", "二", "2.005"));
        var sum = new AggregationExecutor().aggregate(pivot("SUM", 0), descriptor(), rows);
        assertEquals("9007199254740996.015", sum.totals().get(0).values().get(0).value());
        var avg = new AggregationExecutor().aggregate(pivot("AVG", 0), descriptor(), rows);
        assertEquals("3002399751580332.005", avg.totals().get(0).values().get(0).value());
    }

    /** 执行sparseAndNullDimensionsRemainDistinctAndDrillUsesSameRows对应的受控处理。 */
    @Test
    void sparseAndNullDimensionsRemainDistinctAndDrillUsesSameRows() {
        var rows = List.of(row(null, "一", "1"), row("", "二", null), row("空", "二", "3"));
        var engine = new AggregationExecutor();
        var result = engine.aggregate(pivot("SUM", 0), descriptor(), rows);
        assertEquals(3, result.rows().size());
        assertEquals(2, result.columns().size());
        var first = result.rows().get(0).cells().get(0);
        assertEquals(
                1,
                engine.drill(pivot("SUM", 0), descriptor(), rows, first.coordinate(), 0, 20)
                        .total());
        assertThrows(
                ReportFault.class,
                () -> engine.drill(pivot("SUM", 0), descriptor(), rows, "forged", 0, 20));
    }

    /** 部署前发出的原值坐标不因新增字典名称元数据而失效。 */
    @Test
    void historicalDrillCoordinatesRemainValidWithAndWithoutDictionaryNames() {
        String historical =
                ReportDefinitionService.hash(
                        "[TypedValue[type=STRING, value=001]]|[TypedValue[type=STRING, value=2026]]|false|false|[0]");
        var original = row("001", "2026", "1");
        var labelled = new LinkedHashMap<>(original);
        labelled.put("category", new TypedValue(ValueType.STRING, "001", "类别名称"));
        labelled.put("period", new TypedValue(ValueType.STRING, "2026", "年度名称"));
        var engine = new AggregationExecutor();
        for (var current : List.of(original, labelled)) {
            var rows = List.of(current);
            var result = engine.aggregate(pivot("SUM", 0), descriptor(), rows);
            assertEquals(historical, result.rows().get(0).cells().get(0).coordinate());
            assertEquals(1, engine.drill(pivot("SUM", 0), descriptor(), rows, historical, 0, 20).total());
        }
    }

    /** 执行topNOtherBucketIsNotTruncatedAndUsesWeightedAverage对应的受控处理。 */
    @Test
    void topNOtherBucketIsNotTruncatedAndUsesWeightedAverage() {
        var rows =
                List.of(
                        row("A", "一", "10"),
                        row("B", "一", "8"),
                        row("C", "一", "2"),
                        row("C", "一", "4"));
        var result = new AggregationExecutor().aggregate(pivot("AVG", 1), descriptor(), rows);
        assertEquals(2, result.rows().size());
        assertTrue(result.rows().get(1).other());
        assertEquals("4.667", result.rows().get(1).cells().get(0).values().get(0).value());
        assertEquals(
                3,
                new AggregationExecutor()
                        .drill(
                                pivot("AVG", 1),
                                descriptor(),
                                rows,
                                result.rows().get(1).cells().get(0).coordinate(),
                                0,
                                20)
                        .total());
    }

    /** 执行mixedUnitAggregationAndExcessiveDynamicColumnsFailClearly对应的受控处理。 */
    @Test
    void mixedUnitAggregationAndExcessiveDynamicColumnsFailClearly() {
        var quantity =
                new DatasetField(
                        "amount", "数值", ValueType.DECIMAL, null, List.of("category"), true);
        var d =
                new DatasetDescriptor(
                        "SAMPLE",
                        "测试",
                        1,
                        "sample:list",
                        false,
                        List.of(
                                descriptor().field("category"),
                                descriptor().field("period"),
                                quantity),
                        List.of(),
                        List.of("amount"));
        assertThrows(
                ReportFault.class,
                () ->
                        new PivotPlanner()
                                .validate(
                                        new PivotDefinition(
                                                List.of(),
                                                List.of("period"),
                                                pivot("SUM", 0).measures(),
                                                true,
                                                true,
                                                0),
                                        d));
        var rows = new ArrayList<Map<String, TypedValue>>();
        for (int i = 0; i < 201; i++) rows.add(row("A", "P" + i, "1"));
        assertThrows(
                ReportFault.class,
                () -> new AggregationExecutor().aggregate(pivot("SUM", 0), descriptor(), rows));
    }

    /** 执行countCountsNonNullAndCountRowsCountsEveryRecord对应的受控处理。 */
    @Test
    void countCountsNonNullAndCountRowsCountsEveryRecord() {
        var rows = List.of(row("A", "一", null), row("A", "一", "2"));
        assertEquals(
                "1",
                new AggregationExecutor()
                        .aggregate(pivot("COUNT", 0), descriptor(), rows)
                        .totals()
                        .get(0)
                        .values()
                        .get(0)
                        .value());
        assertEquals(
                "2",
                new AggregationExecutor()
                        .aggregate(pivot("COUNT_ROWS", 0), descriptor(), rows)
                        .totals()
                        .get(0)
                        .values()
                        .get(0)
                        .value());
    }

    /** 排名使用未舍入精确值，显示精度不能改变TopN成员。 */
    @Test
    void topNRankingDoesNotUseRoundedDisplayValues() {
        var definition =
                new PivotDefinition(
                        List.of("category"),
                        List.of("period"),
                        List.of(new PivotDefinition.Measure("amount", "SUM", "金额", 0)),
                        false,
                        true,
                        1);
        var result =
                new AggregationExecutor()
                        .aggregate(
                                definition,
                                descriptor(),
                                List.of(row("A", "一", "10.1"), row("B", "一", "10.2")));
        assertEquals("B", result.rows().get(0).keys().get(0).value());
    }

    /** 验证每列与整行总计进入同一投影，并以精确文本写入真实数据工作簿。 */
    @Test
    void pivotTotalsAreIncludedInDataExportWithExactColumnValues() throws Exception {
        var definition = pivot("AVG", 0);
        var rows =
                List.of(
                        row("A", "一", "9007199254740993.005"),
                        row("A", "二", "1.005"),
                        row("A", "二", "2.005"));
        var result = new AggregationExecutor().aggregate(definition, descriptor(), rows);
        var dsl =
                new ReportDsl(
                        2,
                        "PIVOT",
                        "交叉",
                        ReportDsl.Kind.PIVOT,
                        "SAMPLE",
                        "1",
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        definition);
        var projection = new PivotProjection().project(dsl, result);
        assertEquals(2, projection.data().rows().size(), "总计应成为三种导出共用的最后一行");
        var total = projection.data().rows().get(1);
        assertEquals("9007199254740993.005", total.get("cell_0_0").value());
        assertEquals("1.505", total.get("cell_1_0").value());
        assertEquals("3002399751580332.005", total.get("total_0").value());
        assertTrue(projection.data().summaries().isEmpty(), "不应另外重复输出摘要总计");
        var output = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter()
                .data(
                        projection.dsl(),
                        consumer -> projection.data().rows().forEach(consumer),
                        output,
                        new ReportCancellation());
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(
                    "9007199254740993.005",
                    book.getSheetAt(0).getRow(2).getCell(1).getStringCellValue());
            assertEquals(
                    "3002399751580332.005",
                    book.getSheetAt(0).getRow(2).getCell(3).getStringCellValue());
        }
    }

    /** 交叉单元格容量在生成每行之前检查，不先构造超限矩阵再报错。 */
    @Test
    void matrixCapacityStopsAggregationBeforeExpandingAllOutputRows() {
        var reads = new AtomicInteger();
        var rows = new ArrayList<Map<String, TypedValue>>();
        for (int i = 0; i < 1000; i++) {
            var data = row("R" + i, "P" + (i % 100), "1");
            rows.add(
                    new AbstractMap<String, TypedValue>() {
                        /**
                         * 返回不可变源行条目。
                         *
                         * @return 当前受控处理的固定结果
                         */
                        public Set<Entry<String, TypedValue>> entrySet() {
                            return data.entrySet();
                        }

                        /**
                         * 只统计真实指标读取，以测量超限之前是否已停止展开。
                         *
                         * @param key 当前契约的key配置或状态
                         * @return 当前受控处理的固定结果
                         */
                        public TypedValue get(Object key) {
                            if ("amount".equals(key)) reads.incrementAndGet();
                            return data.get(key);
                        }
                    });
        }
        assertThrows(
                ReportFault.class,
                () -> new AggregationExecutor().aggregate(pivot("SUM", 0), descriptor(), rows));
        assertTrue(reads.get() < 500, "超过20000单元格之前应停止，实际指标读取=" + reads.get());
    }
}
