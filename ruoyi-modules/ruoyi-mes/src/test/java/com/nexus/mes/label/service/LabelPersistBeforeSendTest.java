package com.nexus.mes.label.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** 旧缺陷回归：持久化失败时，真实事务提交门面不得调用任何网络发送。 */
class LabelPersistBeforeSendTest extends LabelDurableTestSupport {
    @Test
    void persistenceFailurePreventsAnyNetworkSend() {
        jdbc.execute("DROP TABLE mes_label_print_task");
        assertThrows(Exception.class, () -> service.send(record("TEST"), "FROZEN", StandardCharsets.UTF_8));
        assertEquals(0, sends.get(), "任务不能持久化时不得先发送");
    }
}
