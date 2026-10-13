package com.nexus.report.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReportEngineRegressionTest {
    private final ReportCompiler compiler = new ReportCompiler(new ObjectMapper());
    private final ReportActor root = new ReportActor("A", 1, 10, true, List.of());
    private final DatasetDescriptor dataset = new DatasetDescriptor("TEST", "测试", 1, "report:list", false,
        List.of(new DatasetField("score", "分值", ValueType.DECIMAL), new DatasetField("flag", "标志", ValueType.BOOLEAN),
            new DatasetField("unit", "单位", ValueType.STRING),
            new DatasetField("cost", "成本", ValueType.DECIMAL, "report:cost", List.of(), false),
            new DatasetField("quantity", "数量", ValueType.DECIMAL, null, List.of("unit"))), List.of(), List.of("score"));

    private ReportDsl.Column column(String field, String expression) {
        return new ReportDsl.Column(field, field, 100, 2, expression, null);
    }
    private ReportDsl definition(List<ReportDsl.Column> columns, List<ReportDsl.Summary> summaries) {
        return new ReportDsl(1, "TEST_REPORT", "测试报表", ReportDsl.Kind.TABLE, "TEST", "1", columns,
            List.of(), null, List.of(), List.of(), summaries, true);
    }
    private CompiledReport compile(List<ReportDsl.Column> columns) {
        return compiler.compile(definition(columns, List.of()), dataset, root);
    }
    private Object evaluate(String formula, Map<String,Object> row) {
        return SafeExpression.compile(formula).evaluate(row, List.of(row));
    }

    @Test void incompleteExpressionsProduceControlledFaults() {
        assertAll(List.of("1+", "1+EOF", "ROUND(1,)", "IF(TRUE,1,)", "(", "SUM(").stream()
            .map(text -> (org.junit.jupiter.api.function.Executable) () -> assertThrows(ReportFault.class, () -> SafeExpression.compile(text))));
    }
    @Test void logicalConditionsRejectNumbersInsteadOfSilentlyChoosingABranch() {
        assertAll(List.of("IF(1,2,3)", "1 AND TRUE", "FALSE OR 1").stream()
            .map(text -> (org.junit.jupiter.api.function.Executable) () -> assertThrows(ReportFault.class, () -> evaluate(text, Map.of()))));
    }
    @Test void booleanComparisonsAreSupported() {
        assertEquals(true, evaluate("TRUE=TRUE", Map.of()));
        assertEquals(true, evaluate("FALSE!=TRUE", Map.of()));
    }
    @Test void nullLogicRetainsThreeValuedResultsAndShortCircuiting() {
        assertNull(evaluate("NULL AND TRUE", Map.of()));
        assertNull(evaluate("FALSE OR NULL", Map.of()));
        assertEquals(false, evaluate("NULL AND FALSE", Map.of()));
        assertEquals(true, evaluate("NULL OR TRUE", Map.of()));
        assertEquals(false, evaluate("FALSE AND (1/0>0)", Map.of()));
        assertEquals(true, evaluate("TRUE OR (1/0>0)", Map.of()));
        assertEquals(new BigDecimal("2"), evaluate("IF(NULL,1/0,2)", Map.of()));
    }
    @Test void aggregateSessionScansTheSnapshotOncePerAggregateNode() {
        var reads = new AtomicInteger();
        List<Map<String,Object>> rows = new ArrayList<>();
        for (int i=0; i<20; i++) rows.add(Map.of("amount", new CountedNumber(reads)));
        var session = SafeExpression.compile("SUM(amount)").session(rows);
        for (var row : rows) assertEquals(new BigDecimal("20"), session.evaluate(row));
        assertEquals(20, reads.get(), "Aggregate work must be linear in the snapshot row count");
    }
    @Test void aggregateSessionsFreezeInputAndNeverShareCachedValues() {
        Map<String,Object> row = new HashMap<>(Map.of("amount", new BigDecimal("1"), "flag", true));
        var expression = SafeExpression.compile("SUM(amount)+IF(flag,1,2)");
        var session = expression.session(List.of(row));
        assertEquals(new BigDecimal("2"), session.evaluate(row));
        row.put("amount", new BigDecimal("99")); row.put("flag", false);
        assertEquals(new BigDecimal("3"), session.evaluate(row));
        assertEquals(new BigDecimal("101"), expression.session(List.of(row)).evaluate(row));
    }
    @Test void formulaTypesAreCheckedDuringCompilation() {
        assertAll(List.of("IF(score,1,2)", "flag+1", "score AND TRUE", "ROUND(flag,2)", "IF(flag,1,TRUE)").stream()
            .map(text -> (org.junit.jupiter.api.function.Executable) () -> assertThrows(ReportFault.class,
                () -> compile(List.of(column("derived", text))))));
        assertThrows(ReportFault.class, () -> compile(List.of(column("condition", "score>0"), column("wrong", "condition+1"))));
    }
    @Test void countMayInspectTextAndBooleanFieldsWithoutNumericCoercion() {
        var report = compile(List.of(column("present", "COUNT(unit)"), column("flags", "COUNT(flag)")));
        var row = new HashMap<String,Object>(); row.put("unit", null); row.put("flag", false);
        var result = new ReportCalculator().calculate(report, List.of(Map.of("unit","KG","flag",true),row), Map.of());
        assertEquals("1", result.rows().get(0).get("present").value());
        assertEquals("2", result.rows().get(0).get("flags").value());
    }
    @Test void nonAggregatableMeasuresAreRejectedInDirectAndDerivedAggregateFormulas() {
        assertAll(List.of("SUM(cost)", "MIN(cost)", "MAX(cost)").stream()
            .map(text -> (org.junit.jupiter.api.function.Executable) () -> assertThrows(ReportFault.class,
                () -> compile(List.of(column("total", text))))));
        assertThrows(ReportFault.class, () -> compile(List.of(column("derived","cost*2"),column("total","SUM(derived)"))));
    }
    @Test void nonAggregatableMeasuresAreRejectedInNumericSummariesButMayBeCounted() {
        for (String function : List.of("SUM","MIN","MAX")) assertThrows(ReportFault.class, () -> compiler.compile(
            definition(List.of(column("cost",null)),List.of(new ReportDsl.Summary("cost",function,List.of(),"成本",2))),dataset,root));
        assertDoesNotThrow(() -> compiler.compile(definition(List.of(column("cost",null)),
            List.of(new ReportDsl.Summary("cost","COUNT",List.of(),"条数",0))),dataset,root));
    }
    @Test void partitionedMeasuresRemainProtectedThroughComputedColumns() {
        assertThrows(ReportFault.class, () -> compile(List.of(column("derived","quantity*2"),column("total","SUM(derived)"))));
    }
    @Test void aggregatesCannotBeNestedThroughComputedColumns() {
        for (String formula : List.of("SUM(total)","COUNT(total)","MIN(total)")) assertThrows(ReportFault.class,
            () -> compile(List.of(column("total","SUM(score)"),column("again",formula))));
    }
    @Test void protectedFieldsAreCheckedInLazyBranchesBandsAndSummaryGroups() {
        var actor = new ReportActor("A",2,10,false,List.of(new ReportRole(2,Set.of("report:list"),ReportScope.all(2))));
        var body = definition(List.of(column("derived","IF(FALSE,cost,0)")),List.of());
        assertEquals("SCOPE_DENIED",assertThrows(ReportFault.class, () -> compiler.compile(body,dataset,actor)).code());
        var header = new ReportDsl.BandRow(24,List.of(new ReportDsl.Cell(0,1,1,"{{cost}}",null,null)));
        var band = new ReportDsl(1,"TEST_REPORT","测试",ReportDsl.Kind.TABLE,"TEST","1",List.of(column("score",null)),
            List.of(),null,List.of(header),List.of(),List.of(),true);
        assertEquals("SCOPE_DENIED",assertThrows(ReportFault.class, () -> compiler.compile(band,dataset,actor)).code());
        var grouped = definition(List.of(column("score",null)), List.of(new ReportDsl.Summary(null,"COUNT_ROWS",List.of("cost"),"条数",0)));
        assertEquals("SCOPE_DENIED",assertThrows(ReportFault.class, () -> compiler.compile(grouped,dataset,actor)).code());
    }
    @Test void computedAliasesCannotShadowParameterOrPaginationBindings() {
        for (String field : List.of("p_filter","page_no","page_count","row_count")) assertThrows(ReportFault.class,
            () -> compile(List.of(column(field,"score*2"))));
    }
    @Test void strictDslParsingRejectsCoercionNullPrimitivesTrailingValuesAndDuplicates() {
        String valid = compiler.json(definition(List.of(column("score",null)),List.of()));
        assertAll(List.of(valid.replace("\"schemaVersion\":1","\"schemaVersion\":\"1\""),
            valid.replace("\"schemaVersion\":1","\"schemaVersion\":1.5"),
            valid.replace("\"schemaVersion\":1","\"schemaVersion\":null"),
            valid + " {}",valid.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1"),"null").stream()
            .map(text -> (org.junit.jupiter.api.function.Executable) () -> assertThrows(ReportFault.class, () -> compiler.parse(text))));
    }
    @Test void mergeOverflowCannotBypassBoundsValidation() {
        assertAll(List.of(new ReportDsl.Cell(1,Integer.MAX_VALUE,1,"标题",null,null),
            new ReportDsl.Cell(0,1,Integer.MAX_VALUE,"标题",null,null)).stream().map(cell ->
            (org.junit.jupiter.api.function.Executable) () -> {
                var headers = List.of(new ReportDsl.BandRow(24,List.of()), new ReportDsl.BandRow(24,List.of(cell)));
                var dsl = new ReportDsl(1,"TEST_REPORT","测试",ReportDsl.Kind.TABLE,"TEST","1",List.of(column("score",null),column("unit",null)),
                    List.of(),null,headers,List.of(),List.of(),true);
                assertThrows(ReportFault.class, () -> compiler.compile(dsl,dataset,root));
            }));
    }
    @Test void nullAndDuplicateSummaryRulesProduceControlledFaults() {
        assertAll(List.of(new ReportDsl.Summary("score",null,List.of(),"总数",2),
            new ReportDsl.Summary("score","SUM",List.of("unit","unit"),"总数",2),
            new ReportDsl.Summary("score","SUM",List.of(),"长".repeat(10001),2)).stream().map(summary ->
            (org.junit.jupiter.api.function.Executable) () -> assertThrows(ReportFault.class,
                () -> compiler.compile(definition(List.of(column("score",null)),List.of(summary)),dataset,root))));
    }
    @Test void missingResultFieldsAreDifferentFromExplicitNulls() {
        var report = compile(List.of(column("score",null)));
        var calculator = new ReportCalculator();
        assertEquals("MISSING_RESULT_FIELD",assertThrows(ReportFault.class,
            () -> calculator.calculate(report,List.of(Map.of()),Map.of())).code());
        assertEquals("MISSING_RESULT_FIELD",assertThrows(ReportFault.class,
            () -> calculator.calculateRow(report,Map.of(),Map.of())).code());
        var nullable = new HashMap<String,Object>(); nullable.put("score",null);
        assertNull(calculator.calculateRow(report,nullable,Map.of()).get("score").value());
    }
    @Test void resultNumericBoundsAreEnforcedBeforeDecimalExpansion() {
        var report = compile(List.of(column("score",null)));
        for (String value : List.of("1e1000","1e-1000","999999999999999999999999999999999999999")) assertThrows(ReportFault.class,
            () -> new ReportCalculator().calculateRow(report,Map.of("score",value),Map.of()));
    }
    @Test void calculatorRejectsUnknownParameters() {
        var report = compile(List.of(column("score",null)));
        assertThrows(ReportFault.class, () -> new ReportCalculator().calculate(report,List.of(Map.of("score",1)),Map.of("unexpected",1)));
        assertThrows(ReportFault.class, () -> new ReportCalculator().calculateRow(report,Map.of("score",1),Map.of("unexpected",1)));
    }
    @Test void summarySnapshotsCopyCallerOwnedGroupMaps() {
        Map<String,TypedValue> group = new LinkedHashMap<>(Map.of("unit",TypedValue.of(ValueType.STRING,"KG")));
        var summary = new ReportCalculator.SummaryValue("数量",group,TypedValue.of(ValueType.DECIMAL,"1"));
        var data = new ReportCalculator.CalculatedData(List.of(),List.of(summary));
        group.put("unit",TypedValue.of(ValueType.STRING,"PCS"));
        assertEquals("KG",data.summaries().get(0).group().get("unit").value());
        assertThrows(UnsupportedOperationException.class, () -> data.summaries().get(0).group().clear());
    }
    @Test void compiledContextReauthorizesFieldsForTheExecutionActor() {
        var report = compile(List.of(column("cost",null)));
        var actor = new ReportActor("B",2,10,false,List.of(new ReportRole(2,Set.of("report:list"),ReportScope.all(2))));
        assertEquals("SCOPE_DENIED",assertThrows(ReportFault.class, () -> report.context(actor)).code());
    }
    @Test void compiledContextUsesTheExecutionActorsOwnRowScope() {
        var report = compile(List.of(column("score",null)));
        var actor = new ReportActor("B",2,10,false,List.of(new ReportRole(2,Set.of("report:list"),new ReportScope(false,Set.of(),true,2))));
        var context = report.context(actor);
        assertEquals("B",context.tenantId());
        assertTrue(context.scopes().stream().allMatch(scope -> scope.userId()==2 && scope.allows(10L,2L) && !scope.allows(10L,1L)));
    }
    @Test void calculationsNeverShareAggregateStateAcrossExecutions() {
        var report = compile(List.of(column("total","SUM(score)")));
        var calculator = new ReportCalculator();
        assertEquals("1",calculator.calculate(report,List.of(Map.of("score",1)),Map.of()).rows().get(0).get("total").value());
        assertEquals("99",calculator.calculate(report,List.of(Map.of("score",99)),Map.of()).rows().get(0).get("total").value());
    }
    @Test void textMetricsRejectNonFiniteGeometryAndExcessiveBoundText() {
        try (var metrics = new TextMetrics(FontSupport.find(null))) {
            assertThrows(ReportFault.class, () -> metrics.wrap("中文",Double.POSITIVE_INFINITY,10));
            assertThrows(ReportFault.class, () -> metrics.wrap("中文",100,Double.NaN));
            assertEquals("CAPACITY_EXCEEDED",assertThrows(ReportFault.class, () -> metrics.wrap("长".repeat(10001),100,10)).code());
        }
    }
    @Test void cjkWrappingPreservesAllCharactersAtMeasuredWidths() {
        try (var metrics = new TextMetrics(FontSupport.find(null))) {
            String text = "制造业中文长文本".repeat(120);
            var lines = metrics.wrap(text,115,10);
            assertEquals(text,String.join("",lines));
            for (String line : lines) assertTrue(metrics.width(line,10)<=115+0.01);
            assertEquals(lines,metrics.wrap(text,115,10));
        }
    }
    @Test void paginationWithFooterAndNoRepeatedColumnHeaderIsDeterministic() {
        var dsl = new ReportDsl(1,"TEST_REPORT","中文报表",ReportDsl.Kind.DOCUMENT,"TEST","1",List.of(column("unit",null)),
            List.of(),new ReportDsl.Page(200,220,10,10,10,10,24),
            List.of(new ReportDsl.BandRow(24,List.of(new ReportDsl.Cell(0,1,1,"第 {{page_no}} 页",null,null)))),
            List.of(new ReportDsl.BandRow(24,List.of(new ReportDsl.Cell(0,1,1,"共 {{row_count}} 行",null,null)))),List.of(),false);
        var report = compiler.compile(dsl,dataset,root);
        List<Map<String,Object>> rows = new ArrayList<>(); for (int i=0;i<20;i++) rows.add(Map.of("unit","中文长文本".repeat(3)));
        var data = new ReportCalculator().calculate(report,rows,Map.of()); var layout = new DocumentLayoutEngine(); var font = FontSupport.find(null);
        var plan = layout.layout(report,data,Map.of(),font); assertEquals(plan,layout.layout(report,data,Map.of(),font));
        var detail = plan.pages().stream().flatMap(page -> page.cells().stream()).filter(cell -> cell.band().equals("DETAIL")).toList();
        assertEquals(20,detail.size()); assertEquals(20,detail.stream().map(RenderPlan.Cell::rowIndex).distinct().count());
        assertEquals(1,plan.pages().stream().flatMap(page -> page.cells().stream()).filter(cell -> cell.band().equals("COLUMN_HEADER")).count());
        assertTrue(plan.pages().get(plan.pages().size()-1).cells().stream().anyMatch(cell -> cell.band().equals("FOOTER") && cell.lines().equals(List.of("共 20 行"))));
        for (var page : plan.pages()) for (var cell : page.cells()) assertTrue(cell.y()+cell.height()<=page.height()-10+0.01);
    }
    @Test void malformedBindingsAndUnsupportedCountFieldsCannotPublish() {
        var headers=List.of(new ReportDsl.BandRow(24,List.of(new ReportDsl.Cell(0,1,1,"{{score.bad}}",null,null))));
        var malformed=new ReportDsl(1,"TEST_REPORT","测试",ReportDsl.Kind.TABLE,"TEST","1",List.of(column("score",null)),List.of(),null,headers,List.of(),List.of(),true);
        assertThrows(ReportFault.class, () -> compiler.compile(malformed,dataset,root));
        assertThrows(ReportFault.class, () -> compiler.compile(definition(List.of(column("score",null)),
            List.of(new ReportDsl.Summary("unknown","COUNT_ROWS",List.of(),"条数",0))),dataset,root));
    }
    @Test void overflowingVersionIdsAndScalarStringCoercionAreRejected() {
        String valid=compiler.json(definition(List.of(column("score",null)),List.of()));
        assertThrows(ReportFault.class, () -> compiler.parse(valid.replace("\"field\":\"score\"","\"field\":123")));
        assertThrows(ReportFault.class, () -> compiler.parse(valid.replace("\"width\":100.0","\"width\":\"100\"")));
        var original=definition(List.of(column("score",null)),List.of());
        var overflow=new ReportDsl(1,original.code(),original.title(),original.kind(),"TEST","9223372036854775808",original.columns(),List.of(),null,List.of(),List.of(),List.of(),true);
        assertThrows(ReportFault.class, () -> compiler.compile(overflow,dataset,root));
    }
    @Test void streamingSummaryGroupLimitRejectsOnlyANewExcessGroup() {
        var report=compiler.compile(definition(List.of(column("unit",null)),List.of(new ReportDsl.Summary(null,"COUNT_ROWS",List.of("unit"),"条数",0))),dataset,root);
        var accumulator=new ReportCalculator.SummaryAccumulator(report);
        for(int i=0;i<10000;i++)accumulator.accept(Map.of("unit",TypedValue.of(ValueType.STRING,"U"+i)));
        assertDoesNotThrow(() -> accumulator.accept(Map.of("unit",TypedValue.of(ValueType.STRING,"U0"))));
        assertEquals("CAPACITY_EXCEEDED",assertThrows(ReportFault.class,
            () -> accumulator.accept(Map.of("unit",TypedValue.of(ValueType.STRING,"EXCESS")))).code());
    }
    @Test void nullComputedBooleansKeepTheirDeclaredResultType() {
        var report=compile(List.of(column("condition","score>0")));
        var row=new HashMap<String,Object>();row.put("score",null);
        assertEquals(ValueType.BOOLEAN,new ReportCalculator().calculateRow(report,row,Map.of()).get("condition").type());
        assertEquals(ValueType.BOOLEAN,new ReportCalculator().calculate(report,List.of(row),Map.of()).rows().get(0).get("condition").type());
    }
    @Test void snapshotTextHasAnAggregateCapacityBound() {
        var report=compile(List.of(column("unit",null)));
        var rows=Collections.nCopies(1001,Map.<String,Object>of("unit","长".repeat(10000)));
        assertEquals("CAPACITY_EXCEEDED",assertThrows(ReportFault.class,
            () -> new ReportCalculator().calculate(report,rows,Map.of())).code());
    }
    @Test void pageLimitStopsBeforePreparingUnreachableDetailText() {
        var original=definition(List.of(column("unit",null)),List.of());
        var dsl=new ReportDsl(1,original.code(),"报表",ReportDsl.Kind.DOCUMENT,"TEST","1",original.columns(),List.of(),
            new ReportDsl.Page(200,200,10,10,10,10,24),List.of(),List.of(),List.of(),true);
        var report=compiler.compile(dsl,dataset,root);
        List<Map<String,TypedValue>> rows=new ArrayList<>();
        for(int i=0;i<1000;i++)rows.add(Map.of("unit",TypedValue.of(ValueType.STRING,"KG")));
        rows.add(Map.of("unit",TypedValue.of(ValueType.STRING,"\u0000")));
        var data=new ReportCalculator.CalculatedData(rows,List.of());
        assertEquals("CAPACITY_EXCEEDED",assertThrows(ReportFault.class,
            () -> new DocumentLayoutEngine().layout(report,data,Map.of(),FontSupport.find(null))).code());
    }
    @Test void layoutRejectsAbsentDetailFieldsInsteadOfPrintingBlankValues() {
        var report=compile(List.of(column("unit",null)));
        var data=new ReportCalculator.CalculatedData(List.of(Map.of()),List.of());
        assertEquals("MISSING_RESULT_FIELD",assertThrows(ReportFault.class,
            () -> new DocumentLayoutEngine().layout(report,data,Map.of(),FontSupport.find(null))).code());
    }
    @Test void streamingSummariesRejectMissingFieldsAndGroups() {
        var report=compiler.compile(definition(List.of(column("score",null)),List.of(new ReportDsl.Summary("score","SUM",List.of("unit"),"总计",2))),dataset,root);
        var accumulator=new ReportCalculator.SummaryAccumulator(report);
        assertEquals("MISSING_RESULT_FIELD",assertThrows(ReportFault.class,
            () -> accumulator.accept(Map.of("score",TypedValue.of(ValueType.DECIMAL,"1")))).code());
        assertEquals("MISSING_RESULT_FIELD",assertThrows(ReportFault.class,
            () -> accumulator.accept(Map.of("unit",TypedValue.of(ValueType.STRING,"KG")))).code());
    }
    @Test void equalDecimalGroupKeysDoNotSplitByScale() {
        var report=compiler.compile(definition(List.of(column("score",null)),List.of(new ReportDsl.Summary("score","SUM",List.of("score"),"分值",2))),dataset,root);
        var data=new ReportCalculator().calculate(report,List.of(Map.of("score",new BigDecimal("1.0")),Map.of("score",new BigDecimal("1.00"))),Map.of());
        assertEquals(1,data.summaries().size());assertEquals("2.00",data.summaries().get(0).value().value());
    }
    @Test void streamingNumericSummariesRejectExcessiveExponentsBeforeFormatting() {
        var report=compiler.compile(definition(List.of(column("score",null)),List.of(new ReportDsl.Summary("score","SUM",List.of(),"总计",2))),dataset,root);
        var accumulator=new ReportCalculator.SummaryAccumulator(report);
        assertThrows(ReportFault.class, () -> accumulator.accept(Map.of("score",new TypedValue(ValueType.DECIMAL,"1e1000"))));
    }
    @Test void compactDecimalExponentsCannotBypassTheIntegerPrecisionLimit() {
        var report=compile(List.of(column("score",null)));
        assertThrows(ReportFault.class, () -> new ReportCalculator().calculateRow(report,Map.of("score",new BigDecimal("1e38")),Map.of()));
        assertThrows(ReportFault.class, () -> evaluate("score+0",Map.of("score",new BigDecimal("1e38"))));
    }
    private static final class CountedNumber extends Number {
        private final AtomicInteger reads;
        CountedNumber(AtomicInteger reads) { this.reads=reads; }
        public int intValue() { return 1; } public long longValue() { return 1; }
        public float floatValue() { return 1; } public double doubleValue() { return 1; }
        public String toString() { reads.incrementAndGet(); return "1"; }
    }
}
