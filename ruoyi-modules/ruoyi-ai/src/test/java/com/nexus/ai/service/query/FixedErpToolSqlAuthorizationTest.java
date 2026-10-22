package com.nexus.ai.service.query;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.tool.*;
import com.nexus.common.report.ReportActor;
import com.nexus.common.report.ReportAuthorizationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 捕获真实固定工具生成的 SQL，验证最终授权策略兼容性；不代表目标数据库的运行验收。 */
class FixedErpToolSqlAuthorizationTest {
    private ErpQueryAuthorizationGuard guard;

    @BeforeEach
    void liveAdmin() {
        RequestContextHolder.resetRequestAttributes();
        AiRequestContext.set("000000", 1L);
        var authorization = mock(ReportAuthorizationPort.class);
        when(authorization.resolve("000000", 1)).thenReturn(new ReportActor("000000", 1, 1, true, List.of()));
        guard = new ErpQueryAuthorizationGuard(authorization, List.of(), new AiProperties());
    }

    @AfterEach
    void clearIdentity() {
        AiRequestContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest(name = "固定工具原始 SQL：{0}")
    @MethodSource("queries")
    void actualFixedToolSqlKeepsChineseAliasesAndSupportedExpressions(String name, Consumer<ErpQueryService> call) {
        var capture = mock(ErpQueryService.class);
        when(capture.executeQuery(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(capture.executeParameterizedQuery(anyString(), any(Object[].class), anyInt(), anyInt())).thenReturn(List.of());
        call.accept(capture);
        var calls = mockingDetails(capture).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("executeQuery")
                || invocation.getMethod().getName().equals("executeParameterizedQuery")).toList();
        assertEquals(1, calls.size(), name);
        String original = calls.get(0).getArgument(0);
        String approved = assertDoesNotThrow(() -> guard.authorize(original).sql(), name + "\n" + original);
        assertEquals(original.chars().filter(character -> character == '?').count(),
            approved.chars().filter(character -> character == '?').count(), name);
        assertTrue(approved.contains("'000000'"), approved);
        if (name.equals("生产/BOM展开")) {
            for (String expected : List.of("bt.子件编码", "bt.子件名称", "bt.层级", "父件编码", "子件编码", "层级", "UNION ALL"))
                assertTrue(approved.contains(expected), approved);
        }
        if (name.equals("APS/拖期工单")) {
            for (String expected : List.of("GROUP_CONCAT(DISTINCT CONCAT", "GROUP_CONCAT(DISTINCT t.resource_name)", "ORDER BY 拖期天数"))
                assertTrue(approved.contains(expected), approved);
        }
        if (name.equals("供应链/缺料风险")) {
            for (String expected : List.of("CASE", "INTERVAL ? DAY", "ORDER BY 预计缺口", "建议"))
                assertTrue(approved.contains(expected), approved);
        }
    }

    @Test
    void unicodeDerivedColumnReferenceDoesNotAuthorizeUnicodePhysicalTables() {
        assertDoesNotThrow(() -> guard.authorize("SELECT x.物料标识 FROM "
            + "(SELECT material_id AS 物料标识 FROM bd_material) x ORDER BY x.物料标识"));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize("SELECT * FROM wm_物料"));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize("SELECT unknown_function(stock_id) AS 完成率 FROM wm_material_stock"));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize("SELECT @@version AS 完成率 FROM wm_material_stock"));
    }

    private static Arguments query(String name, Consumer<ErpQueryService> call) { return Arguments.of(name, call); }

    private static Stream<Arguments> queries() {
        return Stream.of(
            query("库存/低库存", service -> new ErpInventoryTool(service).queryLowStockMaterials()),
            query("库存/出库趋势", service -> new ErpInventoryTool(service).queryMaterialOutboundTrend("M-1", 6)),
            query("库存/金额", service -> new ErpInventoryTool(service).queryInventoryValue()),
            query("库存/仓库分组", service -> new ErpInventoryTool(service).queryStockByWarehouse()),
            query("库存/库龄", service -> new ErpInventoryTool(service).queryInventoryAging(90)),
            query("库存/周转率", service -> new ErpInventoryTool(service).queryInventoryTurnover(6)),
            query("生产/在产工单", service -> new ErpProductionTool(service).queryActiveWorkOrders()),
            query("生产/产能利用率", service -> new ErpProductionTool(service).queryWorkstationUtilization(10L)),
            query("生产/BOM展开", service -> new ErpProductionTool(service).queryBomExplosion("P-1")),
            query("生产/完成率", service -> new ErpProductionTool(service).queryProductionCompletionRate(30)),
            query("生产/延期分析", service -> new ErpProductionTool(service).queryProductionDelayAnalysis(30)),
            query("生产/车间产量", service -> new ErpProductionTool(service).queryWorkshopOutput(30)),
            query("采购/供应商金额", service -> new ErpPurchaseTool(service).queryPurchaseAmountByVendor("2026-10-01", "2026-10-22")),
            query("采购/供应商风险", service -> new ErpPurchaseTool(service).queryVendorRiskAssessment(10L)),
            query("采购/在途订单", service -> new ErpPurchaseTool(service).queryPendingPurchaseOrders(30)),
            query("采购/价格历史", service -> new ErpPurchaseTool(service).queryPurchasePriceHistory("M-1", 6)),
            query("采购/供应商交付", service -> new ErpPurchaseTool(service).queryVendorDeliveryPerformance()),
            query("采购/采购趋势", service -> new ErpPurchaseTool(service).queryPurchaseTrend(6)),
            query("销售/月趋势", service -> new ErpSalesTool(service).queryMonthlySalesTrend("M-1", 6)),
            query("销售/客户排行", service -> new ErpSalesTool(service).queryTopClientsBySales(30, 10)),
            query("销售/退货", service -> new ErpSalesTool(service).queryRecentReturns(30)),
            query("销售/履行率", service -> new ErpSalesTool(service).querySalesOrderFulfillmentRate()),
            query("销售/品类", service -> new ErpSalesTool(service).querySalesByCategory(30)),
            query("销售/物料排行", service -> new ErpSalesTool(service).queryTopMaterialsBySales(30, 10)),
            query("供应链/缺料风险", service -> new ErpShortageRiskTool(service).queryShortageRisk(14)),
            query("APS/排产方案", service -> new ApsScheduleTool(service).querySchedulePlans(30)),
            query("APS/拖期工单", service -> new ApsScheduleTool(service).queryDelayedWorkOrders()),
            query("APS/资源利用率", service -> new ApsScheduleTool(service).queryResourceUtilization()),
            query("APS/工单排程", service -> new ApsScheduleTool(service).queryWorkOrderSchedule("WO-1"))
        );
    }
}
