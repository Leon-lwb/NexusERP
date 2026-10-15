package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.ReportCompiler;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 基础模板的内容、权限裁剪与可信汇总规则回归。 */
class ReportFoundationTemplateTest {
    /** 管理员身份用于纯编译验证，不连接业务数据库。 */
    private final ReportActor actor = new ReportActor("A", 1, 10, true, List.of());

    /** 严格的一版报表编译器。 */
    private final ReportCompiler compiler = new ReportCompiler(new ObjectMapper());

    /**
     * 构造有真实汇总维度的库存契约。
     *
     * @return 用于模板测试的固定描述
     */
    private DatasetDescriptor inventory() {
        return new DatasetDescriptor(
                "ERP_STOCK_BALANCE",
                "库存余额",
                1,
                "wm:stock:list",
                false,
                List.of(
                        new DatasetField("materialCode", "物料编码", ValueType.STRING),
                        new DatasetField("materialName", "物料名称", ValueType.STRING),
                        new DatasetField("unitName", "单位", ValueType.STRING),
                        new DatasetField(
                                "quantity",
                                "现存量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName")),
                        new DatasetField(
                                "reservedQuantity",
                                "预留量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"))),
                List.of(new DatasetParameter("toDate", "结束日期", ValueType.DATE, false, "TODAY")),
                List.of(
                        "materialCode",
                        "materialName",
                        "quantity",
                        "reservedQuantity",
                        "unitName"));
    }

    /**
     * 调用尚待实现的模板目录入口，缺失能力时明确失败。
     *
     * @param descriptor 已裁剪业务字段的契约
     * @return 不可变基础模板列表
     * @throws Exception 模板目录入口不存在
     */
    @SuppressWarnings("unchecked")
    private List<ReportDsl> foundation(DatasetDescriptor descriptor) throws Exception {
        return (List<ReportDsl>)
                ReportTemplates.class
                        .getMethod("foundation", DatasetDescriptor.class, String.class)
                        .invoke(null, descriptor, "1");
    }

    /** 验证可用量由服务端公式计算，汇总仅引用可信原始字段并保留单位分区。 */
    @Test
    void inventoryVariantCompilesWithoutSummingComputedValues() throws Exception {
        var templates = foundation(inventory());
        assertEquals(2, templates.size());
        var available =
                templates.stream()
                        .filter(d -> d.code().endsWith("_AVAILABLE"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                "quantity-reservedQuantity",
                available.columns().stream()
                        .filter(c -> c.field().equals("availableQuantity"))
                        .findFirst()
                        .orElseThrow()
                        .expression());
        assertTrue(
                available.summaries().stream()
                        .noneMatch(s -> "availableQuantity".equals(s.field())));
        assertTrue(
                available.summaries().stream()
                        .filter(s -> s.function().equals("SUM"))
                        .allMatch(s -> s.groupBy().equals(List.of("materialCode", "unitName"))));
        assertDoesNotThrow(() -> compiler.compile(available, inventory(), actor));
    }

    /** 验证金额汇总按币种分区，金额被权限裁剪时不生成受限变体。 */
    @Test
    void amountVariantsRequireAuthorizedAmountAndCurrency() throws Exception {
        var d =
                new DatasetDescriptor(
                        "ERP_SALES_EXECUTION",
                        "销售执行",
                        1,
                        "so:orderheader:list",
                        false,
                        List.of(
                                new DatasetField("orderNo", "订单号", ValueType.STRING),
                                new DatasetField(
                                        "totalAmount",
                                        "订单金额",
                                        ValueType.DECIMAL,
                                        "so:orderheader:query",
                                        List.of("currency")),
                                new DatasetField("currency", "币种", ValueType.STRING)),
                        List.of(),
                        List.of("orderNo", "totalAmount", "currency"));
        var templates = foundation(d);
        assertEquals(2, templates.size());
        var totals =
                templates.stream()
                        .filter(t -> t.code().endsWith("_TOTALS"))
                        .findFirst()
                        .orElseThrow();
        var sum =
                totals.summaries().stream()
                        .filter(s -> s.function().equals("SUM"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("totalAmount", sum.field());
        assertEquals(List.of("currency"), sum.groupBy());
        assertDoesNotThrow(() -> compiler.compile(totals, d, actor));
        var masked =
                new DatasetDescriptor(
                        d.code(),
                        d.name(),
                        1,
                        d.permission(),
                        false,
                        List.of(d.field("orderNo"), d.field("currency")),
                        List.of(),
                        List.of("orderNo", "currency"));
        assertEquals(1, foundation(masked).size());
    }

    /** 验证日期默认规则与适配名称、时间的列宽被保留。 */
    @Test
    void initialTemplateRetainsDefaultsAndUsefulWidths() {
        var d = inventory();
        var t = ReportTemplates.initial(d, "1", "BASE_ERP_STOCK_BALANCE");
        assertEquals("TODAY", t.parameters().get(0).defaultValue());
        assertTrue(
                t.columns().stream()
                                .filter(c -> c.field().equals("materialName"))
                                .findFirst()
                                .orElseThrow()
                                .width()
                        >= 180);
        assertDoesNotThrow(() -> compiler.compile(t, d, actor));
    }
}
