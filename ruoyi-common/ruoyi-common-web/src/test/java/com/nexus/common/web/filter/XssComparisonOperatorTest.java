package com.nexus.common.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 直接运行生产 Wrapper 的请求边界；不连接 API/DB，不解析并重序列化正文数字。
 * 范围仅是两种精确比较符与现有标签过滤，不代表任意 SQL/HTML 安全证明。
 */
class XssComparisonOperatorTest {

    private enum Boundary {
        JSON_BODY, NESTED_CONFIG_JSON, PARAMETER, PARAMETER_MAP, PARAMETER_VALUES
    }

    private static Stream<Arguments> comparators() {
        return Arrays.stream(Boundary.values()).flatMap(boundary -> Stream.of("<>", "<=>")
            .map(operator -> Arguments.of(boundary, operator)));
    }

    private static Stream<Arguments> maliciousTags() {
        return Arrays.stream(Boundary.values()).flatMap(boundary -> Stream.of(
                new String[]{"<script>alert('恶意')</script>", "alert('恶意')"},
                new String[]{"<img src=x onerror=alert('恶意')>", ""},
                new String[]{"<iframe src='javascript:alert(1)'>恶意框架</iframe>", "恶意框架"})
            .map(example -> Arguments.of(boundary, example[0], example[1])));
    }

    @ParameterizedTest(name = "{0} 精确保留比较符 {1}")
    @MethodSource("comparators")
    void exactComparisonOperatorIsPreservedByRealWrapper(Boundary boundary, String operator) throws IOException {
        String sql = "SELECT s.base_uom_id FROM so_source s JOIN bd_material m ON s.material_id=m.id "
            + "WHERE s.base_uom_id " + operator + " m.base_uom_id";
        assertEquals(expectedAt(boundary, sql), readAt(boundary, sql));
    }

    @ParameterizedTest(name = "{0} 真实恶意标签仍按现有过滤规则删除")
    @MethodSource("maliciousTags")
    void realHtmlTagsAreStillRemoved(Boundary boundary, String html, String filtered) throws IOException {
        assertEquals(expectedAt(boundary, filtered), readAt(boundary, html));
    }

    @ParameterizedTest(name = "{0} 标签与比较符混合时两类处理不互相绕过")
    @MethodSource("maliciousTags")
    void mixedHtmlAndBothComparatorsKeepOnlyOperators(Boundary boundary, String html, String filtered) throws IOException {
        String prefix = "SELECT a <> b AND c <=> d; ";
        String input = prefix + html + " 中文资料";
        String expected = prefix + filtered + " 中文资料";
        String actual = readAt(boundary, input);
        assertEquals(expectedAt(boundary, expected), actual);
        assertFalse(actual.contains("<script"));
        assertFalse(actual.contains("<img"));
        assertFalse(actual.contains("<iframe"));
    }

    @Test
    void nestedJsonKeepsChineseLargeIdsAndHighPrecisionQuantityByteForByte() throws IOException {
        String body = """
            {"id":"2107945008340336642","rawId":9223372036854775807,
             "quantity":12345678901234567890.123456789012345678901234567890,
             "description":"采购与库存作业规范／设备工位",
             "configJson":"{\\"nodes\\":[{\\"sql\\":\\"SELECT a <> b AND c <=> d\\"}]}"}
            """.trim();
        // 仅在测试中确认夹具及其嵌套字符串确实是合法 JSON，不改生产处理流程。
        var parsed = assertDoesNotThrow(() -> new ObjectMapper().readTree(body));
        assertDoesNotThrow(() -> new ObjectMapper().readTree(parsed.get("configJson").asText()));
        assertEquals(body, readBody(body, MediaType.APPLICATION_JSON_VALUE));
    }

    @Test
    void parameterMapAndValuesPreserveEveryOriginalValueWithoutMutatingUnderlyingRequest() {
        String[] values = {"2107945008340336642", "12345678901234567890.12345678901234567890",
            " 采购中文 <> <=> ", "<script>告警</script> a<>b"};
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("sql", values);
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        String[] expected = {values[0], values[1], "采购中文 <> <=>", "告警 a<>b"};
        assertArrayEquals(expected, wrapper.getParameterValues("sql"));
        Map<String, String[]> map = wrapper.getParameterMap();
        assertArrayEquals(expected, map.get("sql"));
        assertArrayEquals(values, request.getParameterValues("sql"), "清洗返回值不能改写原请求参数数组");
    }

    @Test
    void adjacentRepeatedOperatorsAndQuotedHtmlAttributesDoNotRestoreRealTags() throws IOException {
        String payload = "<><=><><=> <img src=x data-check='a<>b' onerror='alert(1)'> <script>告警</script>";
        assertEquals("<><=><><=>  告警", readAt(Boundary.PARAMETER, payload));
        assertEquals(expectedAt(Boundary.JSON_BODY, "<><=><><=>  告警"), readAt(Boundary.JSON_BODY, payload));
    }

    @Test
    void existingTextThatLooksLikeAMarkerIsNeverAccidentallyRestored() throws IOException {
        String markerText = "__XSS_COMPARE_NE__ __XSS_COMPARE_NSE__ XSS_SQL_COMPARE 中文 a<>b c<=>d";
        assertEquals(expectedAt(Boundary.NESTED_CONFIG_JSON, markerText), readAt(Boundary.NESTED_CONFIG_JSON, markerText));
    }

    @Test
    void nonJsonBodyStillUsesOriginalPassThrough() throws IOException {
        String body = "<script>原非JSON逻辑</script> a<>b c<=>d";
        assertEquals(body, readBody(body, MediaType.TEXT_PLAIN_VALUE));
    }

    @Test
    void missingParametersAndEmptyParameterMapKeepExistingNullAndEmptyBehavior() {
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(new MockHttpServletRequest());
        assertNull(wrapper.getParameter("missing"));
        assertNull(wrapper.getParameterValues("missing"));
        assertEquals(Map.of(), wrapper.getParameterMap());
    }

    private static String readAt(Boundary boundary, String payload) throws IOException {
        if (boundary == Boundary.JSON_BODY || boundary == Boundary.NESTED_CONFIG_JSON) {
            return readBody(expectedAt(boundary, payload), MediaType.APPLICATION_JSON_VALUE);
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("sql", "  " + payload + "  ");
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        return switch (boundary) {
            case PARAMETER -> wrapper.getParameter("sql");
            case PARAMETER_MAP -> wrapper.getParameterMap().get("sql")[0];
            case PARAMETER_VALUES -> wrapper.getParameterValues("sql")[0];
            default -> throw new AssertionError("不应到达的边界");
        };
    }

    private static String expectedAt(Boundary boundary, String payload) {
        if (boundary == Boundary.JSON_BODY) {
            return "{\"sqlTemplate\":" + quote(payload) + "}";
        }
        if (boundary == Boundary.NESTED_CONFIG_JSON) {
            return "{\"configJson\":" + quote("{\"sql\":" + quote(payload) + "}") + "}";
        }
        return payload;
    }

    // 仅拼装合成测试字符串，不把生产正文交给JSON解析器，也不参与数量处理。
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String readBody(String body, String contentType) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType(contentType + ";charset=UTF-8");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        return new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
