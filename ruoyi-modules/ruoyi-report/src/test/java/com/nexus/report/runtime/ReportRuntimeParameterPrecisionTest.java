package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** 持久参数即使使用普通Jackson，也不能把大数DECIMAL转成double。 */
class ReportRuntimeParameterPrecisionTest {
    /** 执行persistentPayloadAndBatchParametersUseExactNumericStrings对应的受控处理。 */
    @Test
    void persistentPayloadAndBatchParametersUseExactNumericStrings() throws Exception {
        var json = new ObjectMapper();
        var payload =
                new ReportRuntimeService.Payload(
                        Map.of(
                                "amount",
                                new BigDecimal("9007199254740993.005"),
                                "id",
                                Long.MAX_VALUE),
                        "private-test",
                        null,
                        List.of());
        var decoded =
                json.readValue(
                        json.writeValueAsString(payload), ReportRuntimeService.Payload.class);
        assertEquals("9007199254740993.005", decoded.input().get("amount"));
        assertEquals("9223372036854775807", decoded.input().get("id"));
    }
}
