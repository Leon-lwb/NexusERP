package com.nexus.ai.tool;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 库存工具的只读调用、租户 JOIN 与未知单位边界；不会执行 SQL 或准备数据库数据。 */
class ErpInventoryToolTest {

    private final ErpQueryService query = mock(ErpQueryService.class);
    private final ErpInventoryTool tool = new ErpInventoryTool(query);

    @BeforeEach
    void 设置普通测试租户() throws Exception {
        ErpToolTenantBoundaryTest.setTenant("42");
    }

    @AfterEach
    void 清理租户() {
        AiRequestContext.clear();
    }

    @Test
    void 查询成功保留雪花ID和未知单位而不猜测() {
        var row = new LinkedHashMap<String, Object>();
        row.put("库存ID", "1888888888888888888");
        row.put("物料名称", "精密电机壳体");
        row.put("单位", "未知单位");
        row.put("库存数量", null);
        when(query.executeQuery(anyString(), eq(100), eq(5))).thenReturn(List.of(row));
        String result = tool.queryStockByWarehouse();
        assertTrue(result.contains("1888888888888888888"));
        assertTrue(result.contains("精密电机壳体"));
        assertTrue(result.contains("未知单位 | -"));
    }

    @Test
    void 无结果不得编造库存() {
        assertEquals("查询成功，但未返回数据。", tool.queryLowStockMaterials());
    }

    @Test
    void 查询异常应如实返回失败() {
        when(query.executeQuery(anyString(), eq(100), eq(5)))
            .thenThrow(new IllegalStateException("测试查询服务不可用"));
        assertTrue(tool.queryInventoryValue().startsWith("查询失败："));
    }

    @ParameterizedTest(name = "非法天数或月数 {0} 不触发查询")
    @ValueSource(ints = {0, -1})
    void 非正数查询范围应拒绝(int range) {
        assertTrue(tool.queryInventoryAging(range).startsWith("参数错误"));
        assertTrue(tool.queryMaterialOutboundTrend("MAT-1001", range).startsWith("参数错误"));
        assertTrue(tool.queryInventoryTurnover(range).startsWith("参数错误"));
        verifyNoInteractions(query);
    }

    @ParameterizedTest(name = "空物料编码不触发查询：{0}")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 空物料编码应拒绝(String materialCode) {
        assertTrue(tool.queryMaterialOutboundTrend(materialCode, 6).startsWith("参数错误"));
        verifyNoInteractions(query);
    }

    @Test
    void 字符串物料编码只绑定为参数不拼接SQL() {
        String code = "MAT-1001' OR 1=1 --";
        tool.queryMaterialOutboundTrend(code, 6);
        var call = mockingDetails(query).getInvocations().iterator().next();
        assertFalse(((String) call.getArgument(0)).contains(code));
        Object[] params = call.getArgument(1);
        assertArrayEquals(new Object[]{code, 6}, params);
    }

    @ParameterizedTest(name = "库存查询 {0} 的关联表也必须属于同一租户")
    @ValueSource(strings = {"queryLowStockMaterials", "queryMaterialOutboundTrend", "queryInventoryValue", "queryStockByWarehouse"})
    void 关联表不能仅依赖主表租户条件(String methodName) throws Exception {
        if (methodName.equals("queryMaterialOutboundTrend")) tool.queryMaterialOutboundTrend("MAT-1001", 6);
        else tool.getClass().getMethod(methodName).invoke(tool);
        String sql = lastSql();
        String expected = switch (methodName) {
            case "queryLowStockMaterials" -> "m.tenant_id = s.tenant_id";
            case "queryMaterialOutboundTrend" -> "m.tenant_id = tx.tenant_id";
            default -> "w.tenant_id = s.tenant_id";
        };
        assertTrue(sql.contains(expected), sql);
    }

    @Test
    void 库存金额查询不应同时返回跨单位累计数量() {
        tool.queryInventoryValue();
        assertFalse(lastSql().contains("SUM(s.stock_quantity) AS 库存数量"), lastSql());
    }

    @Test
    void 仓库数量只能按明确单位分组未知单位不能合计() {
        tool.queryStockByWarehouse();
        String sql = lastSql();
        assertTrue(sql.contains("s.base_uom_id AS 单位ID"), sql);
        assertTrue(sql.contains("'未知单位'"), sql);
        assertTrue(sql.contains("THEN NULL"), "未知单位必须显示不可合计，而不是猜成件或相加：" + sql);
        String groupBy = sql.substring(sql.indexOf("GROUP BY"));
        assertTrue(groupBy.contains("w.warehouse_id"), groupBy);
        assertTrue(groupBy.contains("s.base_uom_id"), groupBy);
    }

    @Test
    void 原0上下文不会被自动升级成000000() throws Exception {
        ErpToolTenantBoundaryTest.setTenant("0");
        tool.queryInventoryTurnover(6);
        var call = mockingDetails(query).getInvocations().iterator().next();
        Object[] params = call.getArgument(1);
        assertArrayEquals(new Object[]{6, "0", "0"}, params);
    }

    private String lastSql() {
        return (String) mockingDetails(query).getInvocations().stream()
            .filter(call -> call.getMethod().getName().startsWith("execute"))
            .reduce((first, second) -> second).orElseThrow().getArgument(0);
    }
}
