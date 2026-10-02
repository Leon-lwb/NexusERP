package com.nexus.station.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.common.core.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 同一 Station 设备通道的全部密钥拒绝日志必须脱敏，仅使用虚构测试标记。 */
class StationApiKeyLoggingTest {
    static Stream<Class<?>> channelControllers() {
        return Stream.of(StationDeviceController.class,StationHeartbeatController.class,StationFlashPrepareController.class,
            StationSopController.class,StationMaterialController.class,com.nexus.mes.flash.controller.FlashReportController.class);
    }
    @ParameterizedTest @MethodSource("channelControllers")
    void rejectedValueNeverAppearsInDeviceChannelLogs(Class<?> type) {
        Object controller=mock(type,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(controller,"apiKey","test-only-configured-value");
        var logger=(Logger)LoggerFactory.getLogger(type); var appender=new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            assertThrows(ServiceException.class,()->ReflectionTestUtils.invokeMethod(controller,"checkApiKey","test-only-rejected-marker",mock(HttpServletRequest.class)));
            assertFalse(appender.list.isEmpty(), "密钥拒绝应留下脱敏警告");
            assertTrue(appender.list.stream().noneMatch(e->e.getFormattedMessage().contains("test-only-rejected-marker")));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
