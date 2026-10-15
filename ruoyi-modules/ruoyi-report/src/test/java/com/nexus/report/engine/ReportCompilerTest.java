package com.nexus.report.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 报表Compiler测试，负责当前模块对应的契约或处理职责。 */
class ReportCompilerTest {
    /** 报表编译器。 */
    private final ReportCompiler compiler = new ReportCompiler(new ObjectMapper());

    /** 保存当前 {@code root} 值。 */
    private final ReportActor root = new ReportActor("A", 1, 10, true, List.of());

    /**
     * 读取固定代码契约描述。
     *
     * @return 当前处理返回的固定结果
     */
    private DatasetDescriptor descriptor() {
        return new DatasetDescriptor(
                "TEST",
                "测试数据",
                1,
                "wm:stock:list",
                false,
                List.of(
                        new DatasetField(
                                "quantity", "数量", ValueType.DECIMAL, null, List.of("unit")),
                        new DatasetField("unit", "单位", ValueType.STRING),
                        new DatasetField(
                                "secret", "成本", ValueType.DECIMAL, "wm:stock:query", List.of())),
                List.of(),
                List.of("quantity", "unit"));
    }

    /**
     * 完成 {@code dsl} 对应的报表Compiler测试处理。
     *
     * @param columns 当前操作的 {@code columns} 输入
     * @param summaries 当前操作的 {@code summaries} 输入
     * @return 当前处理返回的固定结果
     */
    private ReportDsl dsl(List<ReportDsl.Column> columns, List<ReportDsl.Summary> summaries) {
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
     * 完成 {@code column} 对应的报表Compiler测试处理。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param expression 当前操作的 {@code expression} 输入
     * @return 当前处理返回的固定结果
     */
    private ReportDsl.Column column(String name, String expression) {
        return new ReportDsl.Column(name, name, 100, 4, expression, null);
    }

    /** 验证 {@code numericDependenciesAreTopologicallyCompiled} 场景下的结果与边界条件。 */
    @Test
    void numericDependenciesAreTopologicallyCompiled() {
        var compiled =
                compiler.compile(
                        dsl(
                                List.of(
                                        column("quantity", null),
                                        column("second", "first+1"),
                                        column("first", "quantity*2")),
                                List.of()),
                        descriptor(),
                        root);
        assertEquals(List.of("first", "second"), compiled.computedOrder());
        assertEquals(Set.of("quantity"), compiled.requiredFields());
    }

    /** 验证 {@code cyclesAndUnknownFieldsCannotPublish} 场景下的结果与边界条件。 */
    @Test
    void cyclesAndUnknownFieldsCannotPublish() {
        assertThrows(
                ReportFault.class,
                () ->
                        compiler.compile(
                                dsl(List.of(column("a", "b+1"), column("b", "a+1")), List.of()),
                                descriptor(),
                                root));
        assertThrows(
                ReportFault.class,
                () ->
                        compiler.compile(
                                dsl(List.of(column("missing", null)), List.of()),
                                descriptor(),
                                root));
    }

    /** 验证 {@code protectedFieldsAreCheckedEvenWhenOnlyUsedInsideFormula} 场景下的结果与边界条件。 */
    @Test
    void protectedFieldsAreCheckedEvenWhenOnlyUsedInsideFormula() {
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(new ReportRole(2, Set.of("wm:stock:list"), ReportScope.all(7))));
        var fault =
                assertThrows(
                        ReportFault.class,
                        () ->
                                compiler.compile(
                                        dsl(List.of(column("derived", "secret*2")), List.of()),
                                        descriptor(),
                                        actor));
        assertEquals("SCOPE_DENIED", fault.code());
    }

    /** 验证 {@code quantitySummationRequiresItsPartition} 场景下的结果与边界条件。 */
    @Test
    void quantitySummationRequiresItsPartition() {
        var unsafe = new ReportDsl.Summary("quantity", "SUM", List.of(), "数量总计", 4);
        assertThrows(
                ReportFault.class,
                () ->
                        compiler.compile(
                                dsl(List.of(column("quantity", null)), List.of(unsafe)),
                                descriptor(),
                                root));
        var safe = new ReportDsl.Summary("quantity", "SUM", List.of("unit"), "按单位汇总", 4);
        assertEquals(
                Set.of("quantity", "unit"),
                compiler.compile(
                                dsl(List.of(column("quantity", null)), List.of(safe)),
                                descriptor(),
                                root)
                        .requiredFields());
    }

    /** 验证 {@code unknownWireVersionAndEditorStateAreRejected} 场景下的结果与边界条件。 */
    @Test
    void unknownWireVersionAndEditorStateAreRejected() {
        String valid =
                "{\"schemaVersion\":1,\"code\":\"TEST_REPORT\",\"title\":\"测试\",\"kind\":\"TABLE\",\"datasetCode\":\"TEST\",\"datasetVersionId\":\"1\",\"columns\":[{\"field\":\"quantity\",\"label\":\"数量\",\"width\":100}],\"editorSelection\":1}";
        assertThrows(ReportFault.class, () -> compiler.parse(valid));
        var original = dsl(List.of(column("quantity", null)), List.of());
        var v2 =
                new ReportDsl(
                        2,
                        original.code(),
                        original.title(),
                        original.kind(),
                        original.datasetCode(),
                        "1",
                        original.columns(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        assertEquals(
                "VERSION_UNSUPPORTED",
                assertThrows(ReportFault.class, () -> compiler.compile(v2, descriptor(), root))
                        .code());
    }

    /** 验证 {@code overlappingStaticMergesAndPaperOverflowAreRejected} 场景下的结果与边界条件。 */
    @Test
    void overlappingStaticMergesAndPaperOverflowAreRejected() {
        var headers =
                List.of(
                        new ReportDsl.BandRow(
                                24,
                                List.of(
                                        new ReportDsl.Cell(0, 2, 1, "标题", null, null),
                                        new ReportDsl.Cell(1, 1, 1, "重复", null, null))));
        var invalid =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "测试",
                        ReportDsl.Kind.DOCUMENT,
                        "TEST",
                        "1",
                        List.of(column("quantity", null), column("unit", null)),
                        List.of(),
                        null,
                        headers,
                        List.of(),
                        List.of(),
                        true);
        assertThrows(ReportFault.class, () -> compiler.compile(invalid, descriptor(), root));
        var wide =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "测试",
                        ReportDsl.Kind.DOCUMENT,
                        "TEST",
                        "1",
                        List.of(new ReportDsl.Column("quantity", "数量", 600, 4, null, null)),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        assertThrows(ReportFault.class, () -> compiler.compile(wide, descriptor(), root));
    }
}
