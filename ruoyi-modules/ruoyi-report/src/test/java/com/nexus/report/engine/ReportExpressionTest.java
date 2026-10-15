package com.nexus.report.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** 报表表达式测试，负责当前模块对应的契约或处理职责。 */
class ReportExpressionTest {
    /**
     * 完成 {@code eval} 对应的报表表达式测试处理。
     *
     * @param text 当前操作的 {@code text} 输入
     * @param row 当前精确业务行
     * @return 当前处理返回的固定结果
     */
    private Object eval(String text, Map<String, Object> row) {
        return SafeExpression.compile(text).evaluate(row, List.of(row));
    }

    /** 验证 {@code arithmeticIsDecimalExactAndHandlesUnaryPrecedence} 场景下的结果与边界条件。 */
    @Test
    void arithmeticIsDecimalExactAndHandlesUnaryPrecedence() {
        assertEquals(new BigDecimal("0.3"), eval("0.1+0.2", Map.of()));
        assertEquals(new BigDecimal("-7"), eval("-(2+3)*2+3", Map.of()));
    }

    /** 验证 {@code nullPropagatesAndIfIsLazy} 场景下的结果与边界条件。 */
    @Test
    void nullPropagatesAndIfIsLazy() {
        var row = new HashMap<String, Object>();
        row.put("quantity", null);
        assertNull(eval("quantity+1", row));
        assertEquals(new BigDecimal("0"), eval("COALESCE(quantity,0)", row));
        assertEquals(new BigDecimal("3"), eval("IF(1<2,3,1/0)", row));
    }

    /** 验证 {@code roundingAndFullDatasetAggregationAreExplicit} 场景下的结果与边界条件。 */
    @Test
    void roundingAndFullDatasetAggregationAreExplicit() {
        assertEquals(new BigDecimal("1.01"), eval("ROUND(1.005,2)", Map.of()));
        var values =
                List.<Map<String, Object>>of(
                        Map.of("amount", new BigDecimal("0.1")),
                        Map.of("amount", new BigDecimal("0.2")));
        assertEquals(
                new BigDecimal("0.3"),
                SafeExpression.compile("SUM(amount)").evaluate(values.get(0), values));
        assertEquals(
                new BigDecimal("2"),
                SafeExpression.compile("COUNT(amount)").evaluate(values.get(0), values));
    }

    /** 验证 {@code scriptsUnknownFunctionsAndDangerousComplexityAreRejected} 场景下的结果与边界条件。 */
    @Test
    void scriptsUnknownFunctionsAndDangerousComplexityAreRejected() {
        for (String script :
                List.of("Runtime.getRuntime()", "1;System.exit(0)", "eval(1)", "x[0]", "'script'"))
            assertThrows(ReportFault.class, () -> SafeExpression.compile(script));
        assertThrows(
                ReportFault.class,
                () -> SafeExpression.compile("(".repeat(100) + "1" + ")".repeat(100)));
        assertThrows(ReportFault.class, () -> eval("1/0", Map.of()));
        assertThrows(ReportFault.class, () -> eval("missing+1", Map.of()));
    }

    /** 验证 {@code aggregateDependenciesAndPrecisionAreTracked} 场景下的结果与边界条件。 */
    @Test
    void aggregateDependenciesAndPrecisionAreTracked() {
        var expression = SafeExpression.compile("ROUND(amount*rate,2)");
        assertEquals(Set.of("amount", "rate"), expression.dependencies());
        assertFalse(expression.aggregate());
        assertTrue(SafeExpression.compile("SUM(amount)").aggregate());
        assertThrows(
                ReportFault.class,
                () -> eval("99999999999999999999999999999999999999*10", Map.of()));
    }
}
