package com.nexus.mes.common.mq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.message.IpqcCompletedMessage;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/** IPQC 共享消息的源时间契约与可变日期隔离测试，不连接外部服务。 */
class IpqcCompletedMessageTest {
    private static final long SOURCE_TIME = 1_790_000_000_000L;
    private final ObjectMapper json = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void sourceInspectionTimeSurvivesJsonRoundTripInsteadOfMessageTimestamp() throws Exception {
        IpqcCompletedMessage message = json.readValue(
            "{\"inspectTime\":" + SOURCE_TIME + ",\"timestamp\":" + (SOURCE_TIME + 5_050_000L) + "}",
            IpqcCompletedMessage.class);

        var source = json.valueToTree(message).get("inspectTime");
        assertNotNull(source, "共享消息必须携带原检验时间，不能仅有发送时间");
        assertEquals(SOURCE_TIME, source.longValue());
        assertNotEquals(message.getTimestamp(), source.longValue());
    }

    @Test
    void settingSourceTimeDoesNotRetainMutableDateReference() {
        IpqcCompletedMessage message = new IpqcCompletedMessage();
        Date original = new Date(SOURCE_TIME);
        message.setInspectTime(original);

        original.setTime(SOURCE_TIME + 5_050_000L);

        assertEquals(SOURCE_TIME, message.getInspectTime().getTime(), "设置源时间必须防御拷贝");
    }

    @Test
    void readingSourceTimeCannotMutateTheSharedMessage() {
        IpqcCompletedMessage message = new IpqcCompletedMessage();
        message.setInspectTime(new Date(SOURCE_TIME));
        Date exposed = message.getInspectTime();

        exposed.setTime(0L);

        assertEquals(SOURCE_TIME, message.getInspectTime().getTime(), "读取源时间必须防御拷贝");
        assertNotSame(exposed, message.getInspectTime());
    }
}
