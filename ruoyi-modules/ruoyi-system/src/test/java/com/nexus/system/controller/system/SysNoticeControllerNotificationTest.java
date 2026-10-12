package com.nexus.system.controller.system;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.system.domain.bo.SysNoticeBo;
import com.nexus.system.service.ISysNoticeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 公告接口只负责保存结果，不得绕过业务通知链路全局广播。 */
@ExtendWith(MockitoExtension.class)
class SysNoticeControllerNotificationTest {

    @Mock
    private ISysNoticeService noticeService;
    @InjectMocks
    private SysNoticeController controller;

    @BeforeAll
    static void initializeSseWithoutExternalServices() throws ClassNotFoundException {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("sse.enabled", Boolean.class, true)).thenReturn(false);
            Class.forName(SseMessageUtils.class.getName());
        }
    }

    @Test
    void successfulPublicationNeverCallsGlobalSseBroadcast() {
        when(noticeService.insertNotice(any(SysNoticeBo.class))).thenReturn(1);

        try (MockedStatic<SseMessageUtils> sse = mockStatic(SseMessageUtils.class)) {
            assertEquals(200, controller.add(new SysNoticeBo()).getCode());
            sse.verifyNoInteractions();
        }
    }

    @Test
    void failedPublicationNeverCallsGlobalSseBroadcast() {
        when(noticeService.insertNotice(any(SysNoticeBo.class))).thenReturn(0);

        try (MockedStatic<SseMessageUtils> sse = mockStatic(SseMessageUtils.class)) {
            assertEquals(500, controller.add(new SysNoticeBo()).getCode());
            sse.verifyNoInteractions();
        }
    }
}
