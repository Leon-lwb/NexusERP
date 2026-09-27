package com.nexus.ai.service.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.sse.dto.SseMessageDto;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiDecisionSseMessageHelperTest {
    @Test void nodeEventHasStringRunIdMonotonicSequenceAndStructuredStatus() throws Exception {
        var captured = new AtomicReference<SseMessageDto>();
        var helper = new AiDecisionSseMessageHelper(new ObjectMapper(), captured::set);
        {
            helper.sendNodeEnd(7L, 9007199254740993L, 2L, "D04_APS_COMPARE", "排产方案", "SUCCEEDED", 128L, "完成");
            var event = new ObjectMapper().readTree(captured.get().getMessage());
            assertEquals("AI_DECISION_NODE_END", event.path("type").asText());
            assertEquals("9007199254740993", event.path("runId").asText());
            assertTrue(event.path("runId").isTextual());
            assertEquals(2, event.path("seq").asLong());
            assertEquals("D04_APS_COMPARE", event.path("nodeCode").asText());
            assertEquals("SUCCEEDED", event.path("status").asText());
        }
    }
}
