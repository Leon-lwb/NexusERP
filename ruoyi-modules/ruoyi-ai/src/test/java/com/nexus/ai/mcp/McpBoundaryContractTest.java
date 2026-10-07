package com.nexus.ai.mcp;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.domain.entity.AiMcpMarketTool;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.core.domain.R;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import com.nexus.common.web.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 复用实际租户 SQL 插件和全局 ID 序列化契约；不连接数据库或网络。 */
class McpBoundaryContractTest {
    private static final long SNOWFLAKE_ID = 2071644711126446082L;

    @ParameterizedTest
    @ValueSource(strings = {"000000", "000001"})
    void tenantSqlKeepsLeadingZerosForMcpTables(String tenantId) {
        TenantProperties properties = new TenantProperties();
        properties.setEnable(true);
        properties.setExcludes(List.of());
        TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties));
        try (MockedStatic<TenantHelper> tenants = mockStatic(TenantHelper.class)) {
            tenants.when(TenantHelper::getTenantId).thenReturn(tenantId);
            for (String table : List.of("ai_tool", "ai_mcp_market", "ai_mcp_market_tool")) {
                String select = interceptor.parserSingle("SELECT id FROM " + table + " WHERE id = " + SNOWFLAKE_ID, null);
                assertTrue(select.contains("tenant_id = '" + tenantId + "'"), select);
                String insert = interceptor.parserSingle("INSERT INTO " + table + " (id) VALUES (" + SNOWFLAKE_ID + ")", null);
                assertTrue(insert.contains("tenant_id"));
                assertTrue(insert.contains("'" + tenantId + "'"), insert);
            }
        }
    }

    @Test
    void globalJacksonKeepsToolAndMarketSnowflakeIdsAsExactStrings() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JacksonConfig().bigNumberModule());
        AiTool tool = new AiTool();
        tool.setId(SNOWFLAKE_ID);
        assertEquals(Long.toString(SNOWFLAKE_ID), mapper.valueToTree(R.ok(tool)).path("data").path("id").asText());
        assertTrue(mapper.valueToTree(R.ok(tool)).path("data").path("id").isTextual());
        AiMcpMarketTool marketTool = new AiMcpMarketTool();
        marketTool.setMarketId(SNOWFLAKE_ID);
        marketTool.setLocalToolId(SNOWFLAKE_ID);
        assertTrue(mapper.valueToTree(marketTool).path("marketId").isTextual());
        assertTrue(mapper.valueToTree(marketTool).path("localToolId").isTextual());
        assertTrue(mapper.valueToTree(R.ok(SNOWFLAKE_ID)).path("data").isTextual());
        assertTrue(mapper.valueToTree(R.ok(List.of(SNOWFLAKE_ID))).path("data").get(0).isTextual());
        assertEquals(SNOWFLAKE_ID, mapper.readValue("{\"id\":\"" + SNOWFLAKE_ID + "\"}", AiTool.class).getId());
    }

    @Test
    void endpointRejectsAHostWithAnyPrivateResolvedAddress() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{93, (byte) 184, (byte) 216, 34});
        InetAddress privateAddress = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        try (MockedStatic<InetAddress> dns = mockStatic(InetAddress.class)) {
            dns.when(() -> InetAddress.getAllByName("mixed.example.test"))
                .thenReturn(new InetAddress[]{publicAddress, privateAddress});
            assertThrows(RuntimeException.class, () -> McpEndpointPolicy.validate("https://mixed.example.test/mcp"));
        }
    }
}
