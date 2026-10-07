package com.nexus.flow.node.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.ai.service.query.SqlSafetyValidator;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实 SQL 校验器验证 Flow 显式租户路径；仅 mock 数据库出口。 */
class SqlNodeProviderTenantTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ErpQueryService queries = mock(ErpQueryService.class);
    private final AiProperties properties = new AiProperties();
    private final SqlSafetyValidator validator = new SqlSafetyValidator(queries, properties);
    private final SqlNodeProvider provider = new SqlNodeProvider(validator, queries, properties, new ObjectMapper());

    @BeforeEach
    void clearContext() {
        AiRequestContext.clear();
    }

    @AfterEach
    void cleanupContext() {
        AiRequestContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "000123", "0007", "00123456", "7", "tenant-A", "ACME_01"})
    void explicitTenantIdentityReachesExplainAndExecutionUnchanged(String tenantId) {
        var ctx = context(tenantId, "SELECT s.id FROM wm_material_stock s WHERE s.tenant_id = '999999'", 7);
        String expectedSql = "SELECT s.id FROM wm_material_stock s WHERE s.tenant_id = '" + tenantId
            + "' AND s.tenant_id = '" + tenantId + "' LIMIT 7";
        when(queries.executeQuery(anyString(), eq(7), eq(5))).thenReturn(List.of(Map.of("id", "row-1")));

        JsonNode output = assertDoesNotThrow(() -> provider.execute(ctx));

        assertEquals(expectedSql, output.path("sql").asText());
        assertEquals(1, output.path("count").asInt());
        assertEquals("id", output.path("columns").get(0).asText());
        assertEquals("row-1", output.path("rows").get(0).path("id").asText());
        verify(queries).executeQuery("EXPLAIN " + expectedSql, 1, 2);
        verify(queries).executeQuery(expectedSql, 7, 5);
        verifyNoMoreInteractions(queries);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " 000000", "000000 ", "000000'", "0' OR '1'='1", "tenant\\A",
        "tenant$1", "tenant;SELECT", "tenant-- A", "tenant/**/A", "tenant\nA", "tenant\"A"})
    void invalidOrMissingTenantFailsBeforeAnyQuery(String tenantId) {
        var error = assertThrowsExactly(IllegalArgumentException.class,
            () -> provider.execute(context(tenantId, "SELECT id FROM wm_material_stock", 7)));

        assertTrue(error.getMessage().contains("租户"));
        verifyNoInteractions(queries);
    }

    @Test
    void sqlSecurityRejectionStopsExecution() {
        assertThrows(IllegalArgumentException.class,
            () -> provider.execute(context("000000", "DELETE FROM wm_material_stock", 7)));
        verifyNoInteractions(queries);
    }

    private FlowExecutionContext context(String tenantId, String sql, int maxRows) {
        var ctx = new FlowExecutionContext();
        ctx.setNodeName("SQL tenant regression");
        ctx.setTenantId(tenantId);
        ctx.setConfig(MAPPER.createObjectNode().put("sqlTemplate", sql).put("maxRows", maxRows));
        ctx.setState(new FlowState(null));
        return ctx;
    }
}
