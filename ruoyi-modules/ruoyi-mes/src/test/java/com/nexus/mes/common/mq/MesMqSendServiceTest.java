package com.nexus.mes.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.IpqcCompletedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MesMqSendService 质检回写投递方式守卫测试（审计 MES-QC-01）
 * <p>质检回写必须是同步投递(失败在调用线程内可观测, 由 mq_message_record + 补偿任务重投),
 * 一旦有人改回 {@code @Async} fire-and-forget, 本测试失败。</p>
 */
class MesMqSendServiceTest {

    @Test
    void qcWritebackMethodsAreNotAsync() throws Exception {
        Method iqc = MesMqSendService.class.getMethod("sendIqcCompleted",
            Long.class, String.class, Long.class, Long.class, String.class, BigDecimal.class, String.class);
        Method oqc = MesMqSendService.class.getMethod("sendOqcCompleted",
            Long.class, String.class, Long.class, Long.class, String.class, String.class, BigDecimal.class, String.class);
        Method ipqc = MesMqSendService.class.getMethod("sendIpqcCompleted",
            Long.class, String.class, Long.class, Long.class, String.class, String.class,
            BigDecimal.class, BigDecimal.class, Long.class, String.class, Date.class, String.class);

        assertNull(iqc.getAnnotation(org.springframework.scheduling.annotation.Async.class),
            "IQC 回写不得为 @Async fire-and-forget");
        assertNull(oqc.getAnnotation(org.springframework.scheduling.annotation.Async.class),
            "OQC 回写不得为 @Async fire-and-forget");
        assertNull(ipqc.getAnnotation(org.springframework.scheduling.annotation.Async.class),
            "IPQC 回写不得为 @Async fire-and-forget");
    }
    @Test
    void ipqcMessageCarriesOriginalInspectionTimeAndDoesNotRetainCallerDate() throws Exception {
        ReliableMessageProducer producer = mock(ReliableMessageProducer.class);
        MesMqSendService sender = new MesMqSendService(producer);
        long sourceTime = 1_790_000_000_000L;
        Date original = new Date(sourceTime);
        sender.sendIpqcCompleted(11L, "原IPQC", 22L, 33L, "原工单", "QUALIFIED",
            BigDecimal.ZERO, BigDecimal.ZERO, 7L, "原检验员", original, "000123");
        original.setTime(sourceTime + 5_050_000L);
        var message = ArgumentCaptor.forClass(IpqcCompletedMessage.class);

        verify(producer).send(eq(ExchangeConstants.TOPIC_EXCHANGE),
            eq(RoutingKeyConstants.ERP_IPQC_COMPLETED), message.capture());
        var value = new ObjectMapper().valueToTree(message.getValue()).get("inspectTime");
        assertNotNull(value, "发送服务必须向共享消息传入原检验时间");
        assertEquals(sourceTime, value.longValue(), "消息不能保留调用方 Date 的可变引用");
        assertEquals(123L, message.getValue().getTenantId());
    }
}
