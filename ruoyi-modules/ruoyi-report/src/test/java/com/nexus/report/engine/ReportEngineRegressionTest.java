package com.nexus.report.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 报表引擎Regression测试，负责当前模块对应的契约或处理职责。 */
class ReportEngineRegressionTest {
    /** 报表编译器。 */
    private final ReportCompiler compiler = new ReportCompiler(new ObjectMapper());

    /** 保存当前 {@code root} 值。 */
    private final ReportActor root = new ReportActor("A", 1, 10, true, List.of());

    /** 保存当前 {@code dataset} 值。 */
    private final DatasetDescriptor dataset =
            new DatasetDescriptor(
                    "TEST",
                    "测试",
                    1,
                    "report:list",
                    false,
                    List.of(
                            new DatasetField("score", "分值", ValueType.DECIMAL),
                            new DatasetField("flag", "标志", ValueType.BOOLEAN),
                            new DatasetField("unit", "单位", ValueType.STRING),
                            new DatasetField(
                                    "cost",
                                    "成本",
                                    ValueType.DECIMAL,
                                    "report:cost",
                                    List.of(),
                                    false),
                            new DatasetField(
                                    "quantity", "数量", ValueType.DECIMAL, null, List.of("unit"))),
                    List.of(),
                    List.of("score"));

    /**
     * 完成 {@code column} 对应的报表引擎Regression测试处理。
     *
     * @param field 当前操作的 {@code field} 输入
     * @param expression 当前操作的 {@code expression} 输入
     * @return 当前处理返回的固定结果
     */
    private ReportDsl.Column column(String field, String expression) {
        return new ReportDsl.Column(field, field, 100, 2, expression, null);
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @param columns 当前操作的 {@code columns} 输入
     * @param summaries 当前操作的 {@code summaries} 输入
     * @return 当前处理返回的固定结果
     */
    private ReportDsl definition(
            List<ReportDsl.Column> columns, List<ReportDsl.Summary> summaries) {
        return new ReportDsl(
                1,
                "TEST_REPORT",
                "测试报表",
                ReportDsl.Kind.TABLE,
                "TEST",
                "1",
                columns,
                List.of(),
                null,
                List.of(),
                List.of(),
                summaries,
                true);
    }

    /**
     * 编译定义并检查字段、类型和业务权限。
     *
     * @param columns 当前操作的 {@code columns} 输入
     * @return 当前处理返回的固定结果
     */
    private CompiledReport compile(List<ReportDsl.Column> columns) {
        return compiler.compile(definition(columns, List.of()), dataset, root);
    }

    /**
     * 完成 {@code evaluate} 对应的报表引擎Regression测试处理。
     *
     * @param formula 当前操作的 {@code formula} 输入
     * @param row 当前精确业务行
     * @return 当前处理返回的固定结果
     */
    private Object evaluate(String formula, Map<String, Object> row) {
        return SafeExpression.compile(formula).evaluate(row, List.of(row));
    }

    /** 验证 {@code incompleteExpressionsProduceControlledFaults} 场景下的结果与边界条件。 */
    @Test
    void incompleteExpressionsProduceControlledFaults() {
        assertAll(
                List.of("1+", "1+EOF", "ROUND(1,)", "IF(TRUE,1,)", "(", "SUM(").stream()
                        .map(
                                text ->
                                        (org.junit.jupiter.api.function.Executable)
                                                () ->
                                                        assertThrows(
                                                                ReportFault.class,
                                                                () ->
                                                                        SafeExpression.compile(
                                                                                text))));
    }

    /** 验证 {@code logicalConditionsRejectNumbersInsteadOfSilentlyChoosingABranch} 场景下的结果与边界条件。 */
    @Test
    void logicalConditionsRejectNumbersInsteadOfSilentlyChoosingABranch() {
        assertAll(
                List.of("IF(1,2,3)", "1 AND TRUE", "FALSE OR 1").stream()
                        .map(
                                text ->
                                        (org.junit.jupiter.api.function.Executable)
                                                () ->
                                                        assertThrows(
                                                                ReportFault.class,
                                                                () -> evaluate(text, Map.of()))));
    }

    /** 验证 {@code booleanComparisonsAreSupported} 场景下的结果与边界条件。 */
    @Test
    void booleanComparisonsAreSupported() {
        assertEquals(true, evaluate("TRUE=TRUE", Map.of()));
        assertEquals(true, evaluate("FALSE!=TRUE", Map.of()));
    }

    /** 验证 {@code nullLogicRetainsThreeValuedResultsAndShortCircuiting} 场景下的结果与边界条件。 */
    @Test
    void nullLogicRetainsThreeValuedResultsAndShortCircuiting() {
        assertNull(evaluate("NULL AND TRUE", Map.of()));
        assertNull(evaluate("FALSE OR NULL", Map.of()));
        assertEquals(false, evaluate("NULL AND FALSE", Map.of()));
        assertEquals(true, evaluate("NULL OR TRUE", Map.of()));
        assertEquals(false, evaluate("FALSE AND (1/0>0)", Map.of()));
        assertEquals(true, evaluate("TRUE OR (1/0>0)", Map.of()));
        assertEquals(new BigDecimal("2"), evaluate("IF(NULL,1/0,2)", Map.of()));
    }

    /** 验证 {@code aggregateSessionScansTheSnapshotOncePerAggregateNode} 场景下的结果与边界条件。 */
    @Test
    void aggregateSessionScansTheSnapshotOncePerAggregateNode() {
        var reads = new AtomicInteger();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) rows.add(Map.of("amount", new CountedNumber(reads)));
        var session = SafeExpression.compile("SUM(amount)").session(rows);
        for (var row : rows) assertEquals(new BigDecimal("20"), session.evaluate(row));
        assertEquals(20, reads.get(), "Aggregate work must be linear in the snapshot row count");
    }

    /** 验证 {@code aggregateSessionsFreezeInputAndNeverShareCachedValues} 场景下的结果与边界条件。 */
    @Test
    void aggregateSessionsFreezeInputAndNeverShareCachedValues() {
        Map<String, Object> row =
                new HashMap<>(Map.of("amount", new BigDecimal("1"), "flag", true));
        var expression = SafeExpression.compile("SUM(amount)+IF(flag,1,2)");
        var session = expression.session(List.of(row));
        assertEquals(new BigDecimal("2"), session.evaluate(row));
        row.put("amount", new BigDecimal("99"));
        row.put("flag", false);
        assertEquals(new BigDecimal("3"), session.evaluate(row));
        assertEquals(new BigDecimal("101"), expression.session(List.of(row)).evaluate(row));
    }

    /** 验证 {@code formulaTypesAreCheckedDuringCompilation} 场景下的结果与边界条件。 */
    @Test
    void formulaTypesAreCheckedDuringCompilation() {
        assertAll(
                List.of(
                                "IF(score,1,2)",
                                "flag+1",
                                "score AND TRUE",
                                "ROUND(flag,2)",
                                "IF(flag,1,TRUE)")
                        .stream()
                        .map(
                                text ->
                                        (org.junit.jupiter.api.function.Executable)
                                                () ->
                                                        assertThrows(
                                                                ReportFault.class,
                                                                () ->
                                                                        compile(
                                                                                List.of(
                                                                                        column(
                                                                                                "derived",
                                                                                                text))))));
        assertThrows(
                ReportFault.class,
                () ->
                        compile(
                                List.of(
                                        column("condition", "score>0"),
                                        column("wrong", "condition+1"))));
    }

    /** 验证 {@code countMayInspectTextAndBooleanFieldsWithoutNumericCoercion} 场景下的结果与边界条件。 */
    @Test
    void countMayInspectTextAndBooleanFieldsWithoutNumericCoercion() {
        var report =
                compile(List.of(column("present", "COUNT(unit)"), column("flags", "COUNT(flag)")));
        var row = new HashMap<String, Object>();
        row.put("unit", null);
        row.put("flag", false);
        var result =
                new ReportCalculator()
                        .calculate(
                                report, List.of(Map.of("unit", "KG", "flag", true), row), Map.of());
        assertEquals("1", result.rows().get(0).get("present").value());
        assertEquals("2", result.rows().get(0).get("flags").value());
    }

    /**
     * 验证 {@code nonAggregatableMeasuresAreRejectedInDirectAndDerivedAggregateFormulas} 场景下的结果与边界条件。
     */
    @Test
    void nonAggregatableMeasuresAreRejectedInDirectAndDerivedAggregateFormulas() {
        assertAll(
                List.of("SUM(cost)", "MIN(cost)", "MAX(cost)").stream()
                        .map(
                                text ->
                                        (org.junit.jupiter.api.function.Executable)
                                                () ->
                                                        assertThrows(
                                                                ReportFault.class,
                                                                () ->
                                                                        compile(
                                                                                List.of(
                                                                                        column(
                                                                                                "total",
                                                                                                text))))));
        assertThrows(
                ReportFault.class,
                () ->
                        compile(
                                List.of(
                                        column("derived", "cost*2"),
                                        column("total", "SUM(derived)"))));
    }

    /**
     * 验证 {@code nonAggregatableMeasuresAreRejectedInNumericSummariesButMayBeCounted} 场景下的结果与边界条件。
     */
    @Test
    void nonAggregatableMeasuresAreRejectedInNumericSummariesButMayBeCounted() {
        for (String function : List.of("SUM", "MIN", "MAX"))
            assertThrows(
                    ReportFault.class,
                    () ->
                            compiler.compile(
                                    definition(
                                            List.of(column("cost", null)),
                                            List.of(
                                                    new ReportDsl.Summary(
                                                            "cost", function, List.of(), "成本", 2))),
                                    dataset,
                                    root));
        assertDoesNotThrow(
                () ->
                        compiler.compile(
                                definition(
                                        List.of(column("cost", null)),
                                        List.of(
                                                new ReportDsl.Summary(
                                                        "cost", "COUNT", List.of(), "条数", 0))),
                                dataset,
                                root));
    }

    /** 验证 {@code partitionedMeasuresRemainProtectedThroughComputedColumns} 场景下的结果与边界条件。 */
    @Test
    void partitionedMeasuresRemainProtectedThroughComputedColumns() {
        assertThrows(
                ReportFault.class,
                () ->
                        compile(
                                List.of(
                                        column("derived", "quantity*2"),
                                        column("total", "SUM(derived)"))));
    }

    /** 验证 {@code aggregatesCannotBeNestedThroughComputedColumns} 场景下的结果与边界条件。 */
    @Test
    void aggregatesCannotBeNestedThroughComputedColumns() {
        for (String formula : List.of("SUM(total)", "COUNT(total)", "MIN(total)"))
            assertThrows(
                    ReportFault.class,
                    () ->
                            compile(
                                    List.of(
                                            column("total", "SUM(score)"),
                                            column("again", formula))));
    }

    /** 验证 {@code protectedFieldsAreCheckedInLazyBranchesBandsAndSummaryGroups} 场景下的结果与边界条件。 */
    @Test
    void protectedFieldsAreCheckedInLazyBranchesBandsAndSummaryGroups() {
        var actor =
                new ReportActor(
                        "A",
                        2,
                        10,
                        false,
                        List.of(new ReportRole(2, Set.of("report:list"), ReportScope.all(2))));
        var body = definition(List.of(column("derived", "IF(FALSE,cost,0)")), List.of());
        assertEquals(
                "SCOPE_DENIED",
                assertThrows(ReportFault.class, () -> compiler.compile(body, dataset, actor))
                        .code());
        var header =
                new ReportDsl.BandRow(
                        24, List.of(new ReportDsl.Cell(0, 1, 1, "{{cost}}", null, null)));
        var band =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "测试",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(column("score", null)),
                        List.of(),
                        null,
                        List.of(header),
                        List.of(),
                        List.of(),
                        true);
        assertEquals(
                "SCOPE_DENIED",
                assertThrows(ReportFault.class, () -> compiler.compile(band, dataset, actor))
                        .code());
        var grouped =
                definition(
                        List.of(column("score", null)),
                        List.of(
                                new ReportDsl.Summary(
                                        null, "COUNT_ROWS", List.of("cost"), "条数", 0)));
        assertEquals(
                "SCOPE_DENIED",
                assertThrows(ReportFault.class, () -> compiler.compile(grouped, dataset, actor))
                        .code());
    }

    /** 验证 {@code computedAliasesCannotShadowParameterOrPaginationBindings} 场景下的结果与边界条件。 */
    @Test
    void computedAliasesCannotShadowParameterOrPaginationBindings() {
        for (String field : List.of("p_filter", "page_no", "page_count", "row_count"))
            assertThrows(ReportFault.class, () -> compile(List.of(column(field, "score*2"))));
    }

    /**
     * 验证 {@code strictDslParsingRejectsCoercionNullPrimitivesTrailingValuesAndDuplicates}
     * 场景下的结果与边界条件。
     */
    @Test
    void strictDslParsingRejectsCoercionNullPrimitivesTrailingValuesAndDuplicates() {
        String valid = compiler.json(definition(List.of(column("score", null)), List.of()));
        assertAll(
                List.of(
                                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"),
                                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":null"),
                                valid + " {}",
                                valid.replace(
                                        "\"schemaVersion\":1",
                                        "\"schemaVersion\":1,\"schemaVersion\":1"),
                                "null")
                        .stream()
                        .map(
                                text ->
                                        (org.junit.jupiter.api.function.Executable)
                                                () ->
                                                        assertThrows(
                                                                ReportFault.class,
                                                                () -> compiler.parse(text))));
    }

    /** 验证 {@code mergeOverflowCannotBypassBoundsValidation} 场景下的结果与边界条件。 */
    @Test
    void mergeOverflowCannotBypassBoundsValidation() {
        assertAll(
                List.of(
                                new ReportDsl.Cell(1, Integer.MAX_VALUE, 1, "标题", null, null),
                                new ReportDsl.Cell(0, 1, Integer.MAX_VALUE, "标题", null, null))
                        .stream()
                        .map(
                                cell ->
                                        (org.junit.jupiter.api.function.Executable)
                                                () -> {
                                                    var headers =
                                                            List.of(
                                                                    new ReportDsl.BandRow(
                                                                            24, List.of()),
                                                                    new ReportDsl.BandRow(
                                                                            24, List.of(cell)));
                                                    var dsl =
                                                            new ReportDsl(
                                                                    1,
                                                                    "TEST_REPORT",
                                                                    "测试",
                                                                    ReportDsl.Kind.TABLE,
                                                                    "TEST",
                                                                    "1",
                                                                    List.of(
                                                                            column("score", null),
                                                                            column("unit", null)),
                                                                    List.of(),
                                                                    null,
                                                                    headers,
                                                                    List.of(),
                                                                    List.of(),
                                                                    true);
                                                    assertThrows(
                                                            ReportFault.class,
                                                            () ->
                                                                    compiler.compile(
                                                                            dsl, dataset, root));
                                                }));
    }

    /** 验证 {@code nullAndDuplicateSummaryRulesProduceControlledFaults} 场景下的结果与边界条件。 */
    @Test
    void nullAndDuplicateSummaryRulesProduceControlledFaults() {
        assertAll(
                List.of(
                                new ReportDsl.Summary("score", null, List.of(), "总数", 2),
                                new ReportDsl.Summary(
                                        "score", "SUM", List.of("unit", "unit"), "总数", 2),
                                new ReportDsl.Summary(
                                        "score", "SUM", List.of(), "长".repeat(10001), 2))
                        .stream()
                        .map(
                                summary ->
                                        (org.junit.jupiter.api.function.Executable)
                                                () ->
                                                        assertThrows(
                                                                ReportFault.class,
                                                                () ->
                                                                        compiler.compile(
                                                                                definition(
                                                                                        List.of(
                                                                                                column(
                                                                                                        "score",
                                                                                                        null)),
                                                                                        List.of(
                                                                                                summary)),
                                                                                dataset,
                                                                                root))));
    }

    /** 验证 {@code missingResultFieldsAreDifferentFromExplicitNulls} 场景下的结果与边界条件。 */
    @Test
    void missingResultFieldsAreDifferentFromExplicitNulls() {
        var report = compile(List.of(column("score", null)));
        var calculator = new ReportCalculator();
        assertEquals(
                "MISSING_RESULT_FIELD",
                assertThrows(
                                ReportFault.class,
                                () -> calculator.calculate(report, List.of(Map.of()), Map.of()))
                        .code());
        assertEquals(
                "MISSING_RESULT_FIELD",
                assertThrows(
                                ReportFault.class,
                                () -> calculator.calculateRow(report, Map.of(), Map.of()))
                        .code());
        var nullable = new HashMap<String, Object>();
        nullable.put("score", null);
        assertNull(calculator.calculateRow(report, nullable, Map.of()).get("score").value());
    }

    /** 验证 {@code resultNumericBoundsAreEnforcedBeforeDecimalExpansion} 场景下的结果与边界条件。 */
    @Test
    void resultNumericBoundsAreEnforcedBeforeDecimalExpansion() {
        var report = compile(List.of(column("score", null)));
        for (String value : List.of("1e1000", "1e-1000", "999999999999999999999999999999999999999"))
            assertThrows(
                    ReportFault.class,
                    () ->
                            new ReportCalculator()
                                    .calculateRow(report, Map.of("score", value), Map.of()));
    }

    /** 验证 {@code calculatorRejectsUnknownParameters} 场景下的结果与边界条件。 */
    @Test
    void calculatorRejectsUnknownParameters() {
        var report = compile(List.of(column("score", null)));
        assertThrows(
                ReportFault.class,
                () ->
                        new ReportCalculator()
                                .calculate(
                                        report,
                                        List.of(Map.of("score", 1)),
                                        Map.of("unexpected", 1)));
        assertThrows(
                ReportFault.class,
                () ->
                        new ReportCalculator()
                                .calculateRow(report, Map.of("score", 1), Map.of("unexpected", 1)));
    }

    /** 验证 {@code summarySnapshotsCopyCallerOwnedGroupMaps} 场景下的结果与边界条件。 */
    @Test
    void summarySnapshotsCopyCallerOwnedGroupMaps() {
        Map<String, TypedValue> group =
                new LinkedHashMap<>(Map.of("unit", TypedValue.of(ValueType.STRING, "KG")));
        var summary =
                new ReportCalculator.SummaryValue(
                        "数量", group, TypedValue.of(ValueType.DECIMAL, "1"));
        var data = new ReportCalculator.CalculatedData(List.of(), List.of(summary));
        group.put("unit", TypedValue.of(ValueType.STRING, "PCS"));
        assertEquals("KG", data.summaries().get(0).group().get("unit").value());
        assertThrows(
                UnsupportedOperationException.class, () -> data.summaries().get(0).group().clear());
    }

    /** 验证 {@code compiledContextReauthorizesFieldsForTheExecutionActor} 场景下的结果与边界条件。 */
    @Test
    void compiledContextReauthorizesFieldsForTheExecutionActor() {
        var report = compile(List.of(column("cost", null)));
        var actor =
                new ReportActor(
                        "B",
                        2,
                        10,
                        false,
                        List.of(new ReportRole(2, Set.of("report:list"), ReportScope.all(2))));
        assertEquals(
                "SCOPE_DENIED",
                assertThrows(ReportFault.class, () -> report.context(actor)).code());
    }

    /** 验证 {@code compiledContextUsesTheExecutionActorsOwnRowScope} 场景下的结果与边界条件。 */
    @Test
    void compiledContextUsesTheExecutionActorsOwnRowScope() {
        var report = compile(List.of(column("score", null)));
        var actor =
                new ReportActor(
                        "B",
                        2,
                        10,
                        false,
                        List.of(
                                new ReportRole(
                                        2,
                                        Set.of("report:list"),
                                        new ReportScope(false, Set.of(), true, 2))));
        var context = report.context(actor);
        assertEquals("B", context.tenantId());
        assertTrue(
                context.scopes().stream()
                        .allMatch(
                                scope ->
                                        scope.userId() == 2
                                                && scope.allows(10L, 2L)
                                                && !scope.allows(10L, 1L)));
    }

    /** 验证 {@code calculationsNeverShareAggregateStateAcrossExecutions} 场景下的结果与边界条件。 */
    @Test
    void calculationsNeverShareAggregateStateAcrossExecutions() {
        var report = compile(List.of(column("total", "SUM(score)")));
        var calculator = new ReportCalculator();
        assertEquals(
                "1",
                calculator
                        .calculate(report, List.of(Map.of("score", 1)), Map.of())
                        .rows()
                        .get(0)
                        .get("total")
                        .value());
        assertEquals(
                "99",
                calculator
                        .calculate(report, List.of(Map.of("score", 99)), Map.of())
                        .rows()
                        .get(0)
                        .get("total")
                        .value());
    }

    /** 验证 {@code textMetricsRejectNonFiniteGeometryAndExcessiveBoundText} 场景下的结果与边界条件。 */
    @Test
    void textMetricsRejectNonFiniteGeometryAndExcessiveBoundText() {
        try (var metrics = new TextMetrics(FontSupport.find(null))) {
            assertThrows(ReportFault.class, () -> metrics.wrap("中文", Double.POSITIVE_INFINITY, 10));
            assertThrows(ReportFault.class, () -> metrics.wrap("中文", 100, Double.NaN));
            assertEquals(
                    "CAPACITY_EXCEEDED",
                    assertThrows(ReportFault.class, () -> metrics.wrap("长".repeat(10001), 100, 10))
                            .code());
        }
    }

    /** 验证 {@code cjkWrappingPreservesAllCharactersAtMeasuredWidths} 场景下的结果与边界条件。 */
    @Test
    void cjkWrappingPreservesAllCharactersAtMeasuredWidths() {
        try (var metrics = new TextMetrics(FontSupport.find(null))) {
            String text = "制造业中文长文本".repeat(120);
            var lines = metrics.wrap(text, 115, 10);
            assertEquals(text, String.join("", lines));
            for (String line : lines) assertTrue(metrics.width(line, 10) <= 115 + 0.01);
            assertEquals(lines, metrics.wrap(text, 115, 10));
        }
    }

    /** 验证 {@code paginationWithFooterAndNoRepeatedColumnHeaderIsDeterministic} 场景下的结果与边界条件。 */
    @Test
    void paginationWithFooterAndNoRepeatedColumnHeaderIsDeterministic() {
        var dsl =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "中文报表",
                        ReportDsl.Kind.DOCUMENT,
                        "TEST",
                        "1",
                        List.of(column("unit", null)),
                        List.of(),
                        new ReportDsl.Page(200, 220, 10, 10, 10, 10, 24),
                        List.of(
                                new ReportDsl.BandRow(
                                        24,
                                        List.of(
                                                new ReportDsl.Cell(
                                                        0, 1, 1, "第 {{page_no}} 页", null, null)))),
                        List.of(
                                new ReportDsl.BandRow(
                                        24,
                                        List.of(
                                                new ReportDsl.Cell(
                                                        0,
                                                        1,
                                                        1,
                                                        "共 {{row_count}} 行",
                                                        null,
                                                        null)))),
                        List.of(),
                        false);
        var report = compiler.compile(dsl, dataset, root);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) rows.add(Map.of("unit", "中文长文本".repeat(3)));
        var data = new ReportCalculator().calculate(report, rows, Map.of());
        var layout = new DocumentLayoutEngine();
        var font = FontSupport.find(null);
        var plan = layout.layout(report, data, Map.of(), font);
        assertEquals(plan, layout.layout(report, data, Map.of(), font));
        var detail =
                plan.pages().stream()
                        .flatMap(page -> page.cells().stream())
                        .filter(cell -> cell.band().equals("DETAIL"))
                        .toList();
        assertEquals(20, detail.size());
        assertEquals(20, detail.stream().map(RenderPlan.Cell::rowIndex).distinct().count());
        assertEquals(
                1,
                plan.pages().stream()
                        .flatMap(page -> page.cells().stream())
                        .filter(cell -> cell.band().equals("COLUMN_HEADER"))
                        .count());
        assertTrue(
                plan.pages().get(plan.pages().size() - 1).cells().stream()
                        .anyMatch(
                                cell ->
                                        cell.band().equals("FOOTER")
                                                && cell.lines().equals(List.of("共 20 行"))));
        for (var page : plan.pages())
            for (var cell : page.cells())
                assertTrue(cell.y() + cell.height() <= page.height() - 10 + 0.01);
    }

    /** 验证 {@code malformedBindingsAndUnsupportedCountFieldsCannotPublish} 场景下的结果与边界条件。 */
    @Test
    void malformedBindingsAndUnsupportedCountFieldsCannotPublish() {
        var headers =
                List.of(
                        new ReportDsl.BandRow(
                                24,
                                List.of(new ReportDsl.Cell(0, 1, 1, "{{score.bad}}", null, null))));
        var malformed =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "测试",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(column("score", null)),
                        List.of(),
                        null,
                        headers,
                        List.of(),
                        List.of(),
                        true);
        assertThrows(ReportFault.class, () -> compiler.compile(malformed, dataset, root));
        assertThrows(
                ReportFault.class,
                () ->
                        compiler.compile(
                                definition(
                                        List.of(column("score", null)),
                                        List.of(
                                                new ReportDsl.Summary(
                                                        "unknown",
                                                        "COUNT_ROWS",
                                                        List.of(),
                                                        "条数",
                                                        0))),
                                dataset,
                                root));
    }

    /** 验证 {@code overflowingVersionIdsAndScalarStringCoercionAreRejected} 场景下的结果与边界条件。 */
    @Test
    void overflowingVersionIdsAndScalarStringCoercionAreRejected() {
        String valid = compiler.json(definition(List.of(column("score", null)), List.of()));
        assertThrows(
                ReportFault.class,
                () -> compiler.parse(valid.replace("\"field\":\"score\"", "\"field\":123")));
        assertThrows(
                ReportFault.class,
                () -> compiler.parse(valid.replace("\"width\":100.0", "\"width\":\"100\"")));
        var original = definition(List.of(column("score", null)), List.of());
        var overflow =
                new ReportDsl(
                        1,
                        original.code(),
                        original.title(),
                        original.kind(),
                        "TEST",
                        "9223372036854775808",
                        original.columns(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        assertThrows(ReportFault.class, () -> compiler.compile(overflow, dataset, root));
    }

    /** 验证 {@code streamingSummaryGroupLimitRejectsOnlyANewExcessGroup} 场景下的结果与边界条件。 */
    @Test
    void streamingSummaryGroupLimitRejectsOnlyANewExcessGroup() {
        var report =
                compiler.compile(
                        definition(
                                List.of(column("unit", null)),
                                List.of(
                                        new ReportDsl.Summary(
                                                null, "COUNT_ROWS", List.of("unit"), "条数", 0))),
                        dataset,
                        root);
        var accumulator = new ReportCalculator.SummaryAccumulator(report);
        for (int i = 0; i < 10000; i++)
            accumulator.accept(Map.of("unit", TypedValue.of(ValueType.STRING, "U" + i)));
        assertDoesNotThrow(
                () -> accumulator.accept(Map.of("unit", TypedValue.of(ValueType.STRING, "U0"))));
        assertEquals(
                "CAPACITY_EXCEEDED",
                assertThrows(
                                ReportFault.class,
                                () ->
                                        accumulator.accept(
                                                Map.of(
                                                        "unit",
                                                        TypedValue.of(ValueType.STRING, "EXCESS"))))
                        .code());
    }

    /** 验证 {@code nullComputedBooleansKeepTheirDeclaredResultType} 场景下的结果与边界条件。 */
    @Test
    void nullComputedBooleansKeepTheirDeclaredResultType() {
        var report = compile(List.of(column("condition", "score>0")));
        var row = new HashMap<String, Object>();
        row.put("score", null);
        assertEquals(
                ValueType.BOOLEAN,
                new ReportCalculator().calculateRow(report, row, Map.of()).get("condition").type());
        assertEquals(
                ValueType.BOOLEAN,
                new ReportCalculator()
                        .calculate(report, List.of(row), Map.of())
                        .rows()
                        .get(0)
                        .get("condition")
                        .type());
    }

    /** 验证 {@code snapshotTextHasAnAggregateCapacityBound} 场景下的结果与边界条件。 */
    @Test
    void snapshotTextHasAnAggregateCapacityBound() {
        var report = compile(List.of(column("unit", null)));
        var rows = Collections.nCopies(1001, Map.<String, Object>of("unit", "长".repeat(10000)));
        assertEquals(
                "CAPACITY_EXCEEDED",
                assertThrows(
                                ReportFault.class,
                                () -> new ReportCalculator().calculate(report, rows, Map.of()))
                        .code());
    }

    /** 验证 {@code pageLimitStopsBeforePreparingUnreachableDetailText} 场景下的结果与边界条件。 */
    @Test
    void pageLimitStopsBeforePreparingUnreachableDetailText() {
        var original = definition(List.of(column("unit", null)), List.of());
        var dsl =
                new ReportDsl(
                        1,
                        original.code(),
                        "报表",
                        ReportDsl.Kind.DOCUMENT,
                        "TEST",
                        "1",
                        original.columns(),
                        List.of(),
                        new ReportDsl.Page(200, 200, 10, 10, 10, 10, 24),
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        var report = compiler.compile(dsl, dataset, root);
        List<Map<String, TypedValue>> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++)
            rows.add(Map.of("unit", TypedValue.of(ValueType.STRING, "KG")));
        rows.add(Map.of("unit", TypedValue.of(ValueType.STRING, "\u0000")));
        var data = new ReportCalculator.CalculatedData(rows, List.of());
        assertEquals(
                "CAPACITY_EXCEEDED",
                assertThrows(
                                ReportFault.class,
                                () ->
                                        new DocumentLayoutEngine()
                                                .layout(
                                                        report,
                                                        data,
                                                        Map.of(),
                                                        FontSupport.find(null)))
                        .code());
    }

    /** 验证 {@code layoutRejectsAbsentDetailFieldsInsteadOfPrintingBlankValues} 场景下的结果与边界条件。 */
    @Test
    void layoutRejectsAbsentDetailFieldsInsteadOfPrintingBlankValues() {
        var report = compile(List.of(column("unit", null)));
        var data = new ReportCalculator.CalculatedData(List.of(Map.of()), List.of());
        assertEquals(
                "MISSING_RESULT_FIELD",
                assertThrows(
                                ReportFault.class,
                                () ->
                                        new DocumentLayoutEngine()
                                                .layout(
                                                        report,
                                                        data,
                                                        Map.of(),
                                                        FontSupport.find(null)))
                        .code());
    }

    /** 验证 {@code streamingSummariesRejectMissingFieldsAndGroups} 场景下的结果与边界条件。 */
    @Test
    void streamingSummariesRejectMissingFieldsAndGroups() {
        var report =
                compiler.compile(
                        definition(
                                List.of(column("score", null)),
                                List.of(
                                        new ReportDsl.Summary(
                                                "score", "SUM", List.of("unit"), "总计", 2))),
                        dataset,
                        root);
        var accumulator = new ReportCalculator.SummaryAccumulator(report);
        assertEquals(
                "MISSING_RESULT_FIELD",
                assertThrows(
                                ReportFault.class,
                                () ->
                                        accumulator.accept(
                                                Map.of(
                                                        "score",
                                                        TypedValue.of(ValueType.DECIMAL, "1"))))
                        .code());
        assertEquals(
                "MISSING_RESULT_FIELD",
                assertThrows(
                                ReportFault.class,
                                () ->
                                        accumulator.accept(
                                                Map.of(
                                                        "unit",
                                                        TypedValue.of(ValueType.STRING, "KG"))))
                        .code());
    }

    /** 验证 {@code equalDecimalGroupKeysDoNotSplitByScale} 场景下的结果与边界条件。 */
    @Test
    void equalDecimalGroupKeysDoNotSplitByScale() {
        var report =
                compiler.compile(
                        definition(
                                List.of(column("score", null)),
                                List.of(
                                        new ReportDsl.Summary(
                                                "score", "SUM", List.of("score"), "分值", 2))),
                        dataset,
                        root);
        var data =
                new ReportCalculator()
                        .calculate(
                                report,
                                List.of(
                                        Map.of("score", new BigDecimal("1.0")),
                                        Map.of("score", new BigDecimal("1.00"))),
                                Map.of());
        assertEquals(1, data.summaries().size());
        assertEquals("2.00", data.summaries().get(0).value().value());
    }

    /** 验证 {@code streamingNumericSummariesRejectExcessiveExponentsBeforeFormatting} 场景下的结果与边界条件。 */
    @Test
    void streamingNumericSummariesRejectExcessiveExponentsBeforeFormatting() {
        var report =
                compiler.compile(
                        definition(
                                List.of(column("score", null)),
                                List.of(new ReportDsl.Summary("score", "SUM", List.of(), "总计", 2))),
                        dataset,
                        root);
        var accumulator = new ReportCalculator.SummaryAccumulator(report);
        assertThrows(
                ReportFault.class,
                () ->
                        accumulator.accept(
                                Map.of("score", new TypedValue(ValueType.DECIMAL, "1e1000"))));
    }

    /** 验证 {@code compactDecimalExponentsCannotBypassTheIntegerPrecisionLimit} 场景下的结果与边界条件。 */
    @Test
    void compactDecimalExponentsCannotBypassTheIntegerPrecisionLimit() {
        var report = compile(List.of(column("score", null)));
        assertThrows(
                ReportFault.class,
                () ->
                        new ReportCalculator()
                                .calculateRow(
                                        report, Map.of("score", new BigDecimal("1e38")), Map.of()));
        assertThrows(
                ReportFault.class,
                () -> evaluate("score+0", Map.of("score", new BigDecimal("1e38"))));
    }

    /** CountedNumber，负责当前模块对应的契约或处理职责。 */
    private static final class CountedNumber extends Number {
        /** 保存当前 {@code reads} 值。 */
        private final AtomicInteger reads;

        /**
         * 初始化CountedNumber并保存所需依赖或不可变值。
         *
         * @param reads 当前操作的 {@code reads} 输入
         */
        CountedNumber(AtomicInteger reads) {
            this.reads = reads;
        }

        /**
         * 验证 {@code intValue} 场景下的结果与边界条件。
         *
         * @return 当前处理返回的固定结果
         */
        public int intValue() {
            return 1;
        }

        /**
         * 验证 {@code longValue} 场景下的结果与边界条件。
         *
         * @return 当前处理返回的固定结果
         */
        public long longValue() {
            return 1;
        }

        /**
         * 验证 {@code floatValue} 场景下的结果与边界条件。
         *
         * @return 当前处理返回的固定结果
         */
        public float floatValue() {
            return 1;
        }

        /**
         * 验证 {@code doubleValue} 场景下的结果与边界条件。
         *
         * @return 当前处理返回的固定结果
         */
        public double doubleValue() {
            return 1;
        }

        /**
         * 统计字符串转换次数并返回固定数值，验证计算不会重复求值。
         *
         * @return 当前处理返回的固定结果
         */
        public String toString() {
            reads.incrementAndGet();
            return "1";
        }
    }
}
