package com.nexus.flow.node.logic;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.ai.service.query.SqlSafetyValidator;
import com.nexus.common.web.config.JacksonConfig;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SQL 输出精度回归：仅 mock SQL 校验与查询出口，不连接数据库或启动服务。 */
class SqlNodeProviderOutputPrecisionTest {

    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;
    private static final String RAW_SQL = "SELECT id FROM wm_material_stock";
    private static final String SAFE_SQL = RAW_SQL + " WHERE wm_material_stock.tenant_id = '000000' LIMIT 7";

    /** 直接装配生产的 Long 序列化模块，不复制边界判断或更改全局配置。 */
    private final ObjectMapper json = new ObjectMapper().registerModule(new JacksonConfig().bigNumberModule());
    private final ErpQueryService queries = mock(ErpQueryService.class);
    private final AiProperties properties = new AiProperties();
    private final SqlSafetyValidator validator = mock(SqlSafetyValidator.class);
    private final SqlNodeProvider provider = new SqlNodeProvider(validator, queries, properties, json);

    @ParameterizedTest
    @ValueSource(longs = {9007199254740992L, 9007199254740993L, 2104385976564420610L,
        Long.MAX_VALUE, -9007199254740992L, -9007199254740993L, Long.MIN_VALUE})
    void unsafeLongIsExactStringBeforeAndAfterJsonRoundTrip(long value) throws Exception {
        JsonNode output = execute(Map.of("id", value));
        assertTrue(cell(output, "id").isTextual());
        assertEquals(Long.toString(value), cell(output, "id").textValue());
        assertEquals(Long.toString(value), cell(roundTrip(output), "id").textValue());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 42L, JS_MAX_SAFE_INTEGER, -JS_MAX_SAFE_INTEGER})
    void safeLongRemainsIntegralJsonNumber(long value) throws Exception {
        JsonNode output = execute(Map.of("id", value));
        assertTrue(cell(output, "id").isIntegralNumber());
        assertEquals(value, cell(output, "id").longValue());
        assertTrue(cell(roundTrip(output), "id").isIntegralNumber());
        assertEquals(value, cell(roundTrip(output), "id").longValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9007199254740992", "-9007199254740992", "18446744073709551616",
        "922337203685477580812345678901234567890", "-922337203685477580812345678901234567890"})
    void unsafeBigIntegerDoesNotTruncateThroughLongOrDouble(String value) throws Exception {
        JsonNode output = execute(Map.of("id", new BigInteger(value)));
        assertTrue(cell(output, "id").isTextual());
        assertEquals(value, cell(output, "id").textValue());
        assertEquals(value, cell(roundTrip(output), "id").textValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "42", "9007199254740991", "-9007199254740991"})
    void safeBigIntegerRemainsIntegralJsonNumber(String value) throws Exception {
        BigInteger expected = new BigInteger(value);
        JsonNode output = execute(Map.of("id", expected));
        assertTrue(cell(output, "id").isIntegralNumber());
        assertEquals(expected, cell(output, "id").bigIntegerValue());
        assertEquals(expected, cell(roundTrip(output), "id").bigIntegerValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901234567890.12345678901234567890",
        "0.10000000000000000000000000000000000001", "1.2300", "0E-20", "1E+1000"})
    void decimalUsesExplicitExactStringContractIncludingScaleAndExponent(String value) throws Exception {
        BigDecimal expected = new BigDecimal(value);
        JsonNode output = execute(Map.of("amount", expected));
        assertTrue(cell(output, "amount").isTextual());
        assertEquals(expected.toString(), cell(output, "amount").textValue());
        assertEquals(expected, new BigDecimal(cell(roundTrip(output), "amount").textValue()));
    }

    @Test
    void normalIntegerAndFloatingPointValuesRemainNumbersWithoutGuessingIdFields() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tiny", (byte) 7);
        row.put("small", (short) 8);
        row.put("id", 42);
        row.put("longCount", 123L);
        row.put("floatValue", 0.1F);
        row.put("doubleId", 2104385976564420610D);
        JsonNode output = execute(row);
        JsonNode returned = roundTrip(output);
        for (var entry : row.entrySet()) {
            JsonNode actual = cell(output, entry.getKey());
            assertTrue(actual.isNumber(), entry.getKey());
            // 普通数值沿用注入的全局序列化结果；列名 id 不能触发猜测或补回已经丢失的位数。
            assertEquals(json.writeValueAsString(entry.getValue()), json.writeValueAsString(actual));
            assertTrue(cell(returned, entry.getKey()).isNumber(), entry.getKey());
        }
    }

    @Test
    void textIdsNullBooleanAndExistingMetadataRemainCompatible() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "2104385976564420610");
        row.put("code", "000021");
        row.put("optional", null);
        row.put("enabled", true);
        JsonNode output = execute(row);
        JsonNode returned = roundTrip(output);
        assertEquals(List.of("id", "code", "optional", "enabled"), json.convertValue(output.path("columns"), List.class));
        assertEquals(1, output.path("count").intValue());
        assertEquals(SAFE_SQL, output.path("sql").textValue());
        assertEquals("2104385976564420610", cell(returned, "id").textValue());
        assertEquals("000021", cell(returned, "code").textValue());
        assertTrue(cell(returned, "optional").isNull());
        assertTrue(cell(returned, "enabled").booleanValue());
        verify(validator).validateAndSanitize(RAW_SQL, 7, "000000");
        verify(queries).executeQuery(SAFE_SQL, 7, 5);
        verifyNoMoreInteractions(validator, queries);
    }

    @Test
    void numericConversionUsesInjectedMapperRatherThanPrivateDefaults() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Integer.class, new JsonSerializer<>() {
            @Override
            public void serialize(Integer value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
                generator.writeString("configured-" + value);
            }
        });
        json.registerModule(module);
        JsonNode output = execute(Map.of("configured", 7));
        assertEquals("configured-7", cell(output, "configured").textValue());
        assertEquals("configured-7", cell(roundTrip(output), "configured").textValue());
    }

    @Test
    void emptyResultKeepsEmptyColumnsRowsAndNumericCount() {
        when(validator.validateAndSanitize(RAW_SQL, 7, "000000")).thenReturn(SAFE_SQL);
        when(queries.executeQuery(SAFE_SQL, 7, 5)).thenReturn(List.of());
        JsonNode output = provider.execute(context());
        assertTrue(output.path("columns").isArray());
        assertTrue(output.path("columns").isEmpty());
        assertTrue(output.path("rows").isEmpty());
        assertTrue(output.path("count").isIntegralNumber());
        assertEquals(0, output.path("count").intValue());
    }

    private JsonNode execute(Map<String, Object> row) {
        when(validator.validateAndSanitize(RAW_SQL, 7, "000000")).thenReturn(SAFE_SQL);
        when(queries.executeQuery(SAFE_SQL, 7, 5)).thenReturn(List.of(row));
        return provider.execute(context());
    }

    private JsonNode cell(JsonNode output, String name) {
        return output.path("rows").get(0).path(name);
    }

    private JsonNode roundTrip(JsonNode output) throws Exception {
        return json.readTree(json.writeValueAsString(output));
    }

    private FlowExecutionContext context() {
        var ctx = new FlowExecutionContext();
        ctx.setNodeName("SQL 输出精度回归");
        ctx.setTenantId("000000");
        ctx.setConfig(json.createObjectNode().put("sqlTemplate", RAW_SQL).put("maxRows", 7));
        ctx.setState(new FlowState(null));
        return ctx;
    }
}
