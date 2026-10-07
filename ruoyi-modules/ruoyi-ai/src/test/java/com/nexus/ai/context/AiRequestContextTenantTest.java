package com.nexus.ai.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 聊天与Flow上下文保留租户文本，业务雪花ID仍为Long。 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiRequestContextTenantTest {
    @AfterEach
    void clearContext() {
        AiRequestContext.clear();
    }

    @ParameterizedTest(name = "基础上下文保留租户 {0}")
    @ValueSource(strings = {"000000", "000021", "TENANT-A"})
    void basicContextPreservesTenant(String tenant) {
        AiRequestContext.set(tenant, 2104385976564420610L);
        assertEquals(tenant, AiRequestContext.getTenantId());
        assertEquals(2104385976564420610L, AiRequestContext.getUserId());
    }

    @ParameterizedTest(name = "完整上下文保留租户 {0}")
    @ValueSource(strings = {"000000", "000021", "TENANT-A"})
    void fullContextAndChildThreadPreserveTenant(String tenant) throws InterruptedException {
        AiRequestContext.set(tenant, 1L, "业务管理员", 2L, 3L, "业务解读模型");
        var inherited = new AtomicReference<String>();
        var thread = new Thread(() -> {
            try {
                inherited.set(AiRequestContext.getTenantId());
            } finally {
                AiRequestContext.clear();
            }
        });
        thread.start();
        thread.join(5000);
        assertEquals(tenant, inherited.get());
        assertEquals(tenant, AiRequestContext.getTenantId());
        assertEquals(2L, AiRequestContext.getSessionId());
        assertEquals(3L, AiRequestContext.getModelId());
    }

    @Test
    void clearRemovesTenantAndBusinessContext() {
        AiRequestContext.set("000000", 1L, "业务管理员", 2L, 3L, "业务解读模型");
        AiRequestContext.setKnowledgeId(4L);
        AiRequestContext.clear();
        assertNull(AiRequestContext.getTenantId());
        assertNull(AiRequestContext.getUserId());
        assertNull(AiRequestContext.getSessionId());
        assertNull(AiRequestContext.getModelId());
        assertNull(AiRequestContext.getKnowledgeId());
    }
}
