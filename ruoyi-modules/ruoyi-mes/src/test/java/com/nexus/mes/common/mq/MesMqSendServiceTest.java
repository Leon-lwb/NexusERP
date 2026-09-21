package com.nexus.mes.common.mq;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNull;

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
            BigDecimal.class, BigDecimal.class, Long.class, String.class, String.class);

        assertNull(iqc.getAnnotation(org.springframework.scheduling.annotation.Async.class),
            "IQC 回写不得为 @Async fire-and-forget");
        assertNull(oqc.getAnnotation(org.springframework.scheduling.annotation.Async.class),
            "OQC 回写不得为 @Async fire-and-forget");
        assertNull(ipqc.getAnnotation(org.springframework.scheduling.annotation.Async.class),
            "IPQC 回写不得为 @Async fire-and-forget");
    }
}
