package com.nexus.ai.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OutputChannelTerminalTest {
    @Test
    void errorCompletionDoesNotInsertNonJsonOrSensitiveText() {
        String id = UUID.randomUUID().toString();
        OutputChannel channel = OutputChannel.create(id);
        try {
            channel.send("{\"type\":\"THINKING\"}");
            channel.completeWithError(new IllegalStateException("内部密钥不可输出"));
            List<String> frames = new ArrayList<>();
            assertThrows(RuntimeException.class, () -> channel.drain(frames::add));
            assertEquals(List.of("{\"type\":\"THINKING\"}"), frames);
            frames.forEach(frame -> assertDoesNotThrow(() -> new ObjectMapper().readTree(frame)));
        } finally { OutputChannel.remove(id); }
    }

    @Test
    void successfulCompletionCannotBeChangedIntoFailure() throws Exception {
        OutputChannel channel = OutputChannel.create("成功终态测试");
        try {
            channel.complete();
            channel.completeWithError(new IllegalStateException("迟到错误"));
            assertDoesNotThrow(() -> channel.drain(ignored -> fail("成功完成后不得新增事件")));
        } finally { OutputChannel.remove("成功终态测试"); }
    }
}
