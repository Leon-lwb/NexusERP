package com.nexus.common.cv.client;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.cv.config.CvProperties;
import com.nexus.common.cv.domain.DetectDtos.ProductDetectResult;
import com.nexus.common.cv.exception.VisionException;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Real multipart HTTP regression on a loopback stub; no Vision process or database required. */
class VisionClientScenarioTest {
    private static final byte[] IMAGE = "test-image-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String DETECTION_JSON = """
        {"success":true,"count":1,"process_time_ms":23.75,
         "detections":[{"class_name":"scratches","confidence":0.92,"bbox":[120,40,360,180]}],
         "ocr_results":[{"text":"MAT-001","confidence":0.95}]}
        """;
    private volatile String body;
    private volatile String path;
    private volatile int requestCount;
    private volatile int responseStatus = 200;
    private volatile String responseBody = DETECTION_JSON;
    private VisionClient client;

    private GenericApplicationContext context;
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        context = new GenericApplicationContext();
        context.registerBean(ObjectMapper.class);
        context.refresh();
        new SpringUtils().setApplicationContext(context);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path = exchange.getRequestURI().getPath();
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestCount++;
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        CvProperties properties = new CvProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("unit-test-key");
        client = new VisionClientImpl(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (context != null) context.close();
    }

    @Test
    void metalScenarioReachesMultipart() throws Exception {
        industrialScenario("defect_metal", false);
    }

    @Test
    void pcbScenarioPreservesOcrFlag() throws Exception {
        industrialScenario("defect_pcb", true);
    }

    @Test
    void legacyProduct() {
        ProductDetectResult result = client.detectProduct(IMAGE, true);
        equal("/api/detect/product", path);
        field("file", "test-image-bytes");
        field("enable_ocr", "true");
        check(!body.contains("name=\"scenario\""), "Legacy request must omit scenario");
        check(result.isSuccess(), "Success must be preserved");
        assertNull(result.getEffectiveScenario(), "Legacy OCR accepts a server without scenario acknowledgement");
        check(!body.contains("name=\"strict_scenario\""), "Legacy request must omit strict_scenario");
        equal(1, result.getCount());
        equal(23.75, result.getProcessTimeMs());
        equal("scratches", result.getDetections().get(0).getClassName());
        equal(0.92, result.getDetections().get(0).getConfidence());
        equal(List.of(120.0, 40.0, 360.0, 180.0), result.getDetections().get(0).getBbox());
        equal("MAT-001", result.getOcrResults().get(0).getText());
    }

    @Test
    void legacyWithoutOcr() {
        client.detectProduct(IMAGE, false);
        field("enable_ocr", "false");
        check(!body.contains("name=\"scenario\""), "Legacy request must omit scenario");
    }

    @Test
    void optionalScenario() throws Exception {
        scenario(null, true);
        field("enable_ocr", "true");
        check(!body.contains("name=\"scenario\""), "Null scenario must be omitted");
    }

    private void industrialScenario(String selected, boolean ocr) throws Exception {
        responseBody = DETECTION_JSON.replace("\"success\":true", "\"success\":true,\"effective_scenario\":\"" + selected + "\"");
        ProductDetectResult result = scenario(selected, ocr);
        assertEquals(selected, result.getEffectiveScenario());
        field("strict_scenario", "true");
        equal("/api/detect/product", path);
        field("scenario", selected);
        field("enable_ocr", String.valueOf(ocr));
        field("file", "test-image-bytes");
        equal(List.of(120.0, 40.0, 360.0, 180.0), result.getDetections().get(0).getBbox());
    }

    @Test
    void invalidScenarios() throws Exception {
        for (String selected : List.of("", " ", "general", "defect_unknown", "DEFECT_PCB", "defect_pcb ")) {
            int before = requestCount;
            try {
                scenario(selected, false);
                throw new AssertionError("Invalid scenario was accepted: " + selected);
            } catch (VisionException expected) {
                equal("INVALID_SCENARIO", expected.getCode());
                equal(before, requestCount);
            }
        }
    }

    @Test
    void upstreamFailure() throws Exception {
        responseStatus = 503;
        responseBody = "{\"detail\":\"Selected defect model unavailable\"}";
        try {
            scenario("defect_metal", false);
            throw new AssertionError("Model error must not become a successful empty result");
        } catch (VisionException expected) {
            equal(503, expected.getHttpStatus());
            check(expected.getMessage().contains("model unavailable"), "Keep model error reason");
        }
    }

    @Test
    void unsuccessfulInference() throws Exception {
        responseBody = "{\"success\":false,\"count\":0,\"detections\":[],\"ocr_results\":[],\"process_time_ms\":1}";
        check(!scenario("defect_pcb", false).isSuccess(), "Do not fabricate successful detections");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"general", "defect_metal"})
    void successfulIndustrialResponseRequiresMatchingAcknowledgement(String effective) {
        String acknowledgement = effective == null ? "" : "\"effective_scenario\":\"" + effective + "\",";
        responseBody = "{" + acknowledgement + "\"success\":true,\"count\":0,\"detections\":[],\"ocr_results\":[],\"process_time_ms\":1}";
        VisionException failure = assertThrows(VisionException.class,
            () -> client.detectProduct(IMAGE, false, "defect_pcb"));
        assertEquals("SCENARIO_MISMATCH", failure.getCode());
        field("scenario", "defect_pcb");
        field("strict_scenario", "true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"defect_metal", "defect_pcb"})
    void acknowledgedIndustrialZeroDetectionsRemainSuccessful(String selected) {
        responseBody = "{\"effective_scenario\":\"" + selected + "\",\"success\":true,\"count\":0,\"detections\":[],\"ocr_results\":[],\"process_time_ms\":1}";
        ProductDetectResult result = client.detectProduct(IMAGE, false, selected);
        assertTrue(result.isSuccess());
        assertEquals(selected, result.getEffectiveScenario());
        assertEquals(0, result.getCount());
        assertTrue(result.getDetections().isEmpty());
        field("strict_scenario", "true");
    }

    @Test
    void absentUpstreamRegistrationKeepsHttpError() {
        responseStatus = 400;
        responseBody = "{\"success\":false,\"code\":\"INVALID_SCENARIO\",\"message\":\"unknown scenario\"}";
        VisionException failure = assertThrows(VisionException.class,
            () -> client.detectProduct(IMAGE, false, "defect_pcb"));
        assertEquals(400, failure.getHttpStatus());
        assertEquals("HTTP_400", failure.getCode());
        field("strict_scenario", "true");
    }

    private ProductDetectResult scenario(String selected, boolean ocr) throws Exception {
        try {
            return (ProductDetectResult) VisionClient.class
                .getMethod("detectProduct", byte[].class, boolean.class, String.class)
                .invoke(client, IMAGE, ocr, selected);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("VisionClient must expose an optional validated scenario overload", missing);
        } catch (InvocationTargetException invoked) {
            if (invoked.getCause() instanceof Exception cause) throw cause;
            throw new AssertionError(invoked.getCause());
        }
    }

    private void field(String name, String value) {
        int start = body.indexOf("name=\"" + name + "\"");
        check(start >= 0, "Missing multipart field " + name);
        int content = body.indexOf("\r\n\r\n", start) + 4;
        check(content >= 4 && body.startsWith(value + "\r\n", content), "Wrong multipart value for " + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual) {
        check(Objects.equals(expected, actual), "Expected " + expected + " but got " + actual);
    }

}
