package com.nexus.im.service.impl;

import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.mapper.SysUserNotificationMapper;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户通知落库回归测试 —— 跳转链接（contentUrl）必须持久化。
 *
 * <p><b>缺陷背景</b>：流程待办/流程结束通知的跳转地址（{@code /workflow/process/detail/{procInsId}?taskId=...}）
 * 此前只随实时推送信封下发、没有落库。用户刷新页面后消息盒子从 {@code /im/notification/page}
 * 读回持久化列表，此时既没有 {@code contentUrl}、{@code ref_id} 也是空的，点击通知只能弹出详情窗，
 * 无法跳转到流程审批详情。</p>
 *
 * <p><b>守护点</b>：{@code handleUserNotificationEvent} 必须把事件里的 {@code contentUrl}
 * 写入 {@link SysUserNotification}，否则消息盒子刷新后再次丢失跳转目标。</p>
 *
 * <p><b>为何 mockStatic ImEnvelopeHelper</b>：{@code ImEnvelopeHelper} → {@code JsonUtils.toJsonString}
 * 依赖 {@code JsonUtils.OBJECT_MAPPER}（{@code static final}，类加载时取 Spring 容器 Bean），
 * 纯 Mockito 环境下会抛 {@code ExceptionInInitializerError}；{@code ImEnvelopeHelper} 自身无静态字段，
 * 类加载安全，故沿用本模块既有做法（见 {@code ImGroupChatServiceTest}）静态 mock 掉它。
 * 推送信封内容的完整路径由 {@code ImEnvelopeHelperIntegrationTest} 集成测试覆盖。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户通知落库：跳转链接必须持久化")
class SysUserNotificationServiceImplTest {

    /** 测试用雪花 ID（超出 JS 安全整数范围，验证落库链路不依赖序列化）。 */
    private static final Long NOTIFICATION_ID = 1_900_000_000_000_000_123L;

    /** 通知表 Mapper。 */
    @Mock
    private SysUserNotificationMapper notificationMapper;

    /** 实时推送组件。 */
    @Mock
    private ImRealtimePush imRealtimePush;

    /** 静态 mock，隔离 JsonUtils 对 Spring 容器的依赖。 */
    private MockedStatic<ImEnvelopeHelper> envelopeHelperMock;

    /** 被测服务（手工构造，避免启动 Spring 容器）。 */
    private SysUserNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysUserNotificationServiceImpl(notificationMapper, imRealtimePush, Mockito.mock(com.nexus.im.service.impl.support.ImConversationCache.class));
        envelopeHelperMock = Mockito.mockStatic(ImEnvelopeHelper.class);
        // 模拟 MyBatis-Plus 在 insert 时回填主键（真实环境由雪花 ID 生成器完成），
        // 否则 envelope 构造处 Long → long 拆箱会 NPE
        when(notificationMapper.insert(any(SysUserNotification.class))).thenAnswer(invocation -> {
            invocation.<SysUserNotification>getArgument(0).setNotificationId(NOTIFICATION_ID);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        if (envelopeHelperMock != null) {
            envelopeHelperMock.close();
        }
    }

    /**
     * 流程结束通知：跳转链接必须落库，刷新后从 /page 读回仍可跳转流程审批详情。
     */
    @Test
    @DisplayName("流程通知落库保留跳转链接（刷新后仍可跳转流程详情）")
    void handleUserNotificationEvent_persistsContentUrl() {
        String contentUrl = "/workflow/process/detail/proc-1?taskId=&processed=false";

        service.handleUserNotificationEvent(buildWorkflowEvent("workflow_notice", "流程任务结束通知", contentUrl));

        ArgumentCaptor<SysUserNotification> captor = ArgumentCaptor.forClass(SysUserNotification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentUrl()).isEqualTo(contentUrl);
    }

    /**
     * 流程待办通知：跳转链接含 taskId，必须原样落库（待办要靠它直接进入任务办理页）。
     */
    @Test
    @DisplayName("流程待办通知落库保留含 taskId 的跳转链接")
    void handleUserNotificationEvent_persistsContentUrlWithTaskId() {
        String contentUrl = "/workflow/process/detail/proc-2?taskId=task-9&businessKey=1001&category=&processed=true";

        service.handleUserNotificationEvent(buildWorkflowEvent("workflow_todo", "流程待办通知", contentUrl));

        ArgumentCaptor<SysUserNotification> captor = ArgumentCaptor.forClass(SysUserNotification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentUrl()).isEqualTo(contentUrl);
        assertThat(captor.getValue().getCategory()).isEqualTo("workflow_todo");
    }

    /**
     * 无跳转目标的通知（IM 私聊/群聊、系统消息）：contentUrl 为空，不应报错也不应被塞入默认值。
     */
    @Test
    @DisplayName("无跳转目标的通知 contentUrl 为空")
    void handleUserNotificationEvent_nullContentUrlStaysNull() {
        UserNotificationEvent event = new UserNotificationEvent();
        event.setTargetUserId(100L);
        event.setCategory("im_private");
        event.setRefType("im_message");
        event.setRefId("1900000000000000124");
        event.setTitle("私聊 · 系统管理员");
        event.setContent("你好");

        service.handleUserNotificationEvent(event);

        ArgumentCaptor<SysUserNotification> captor = ArgumentCaptor.forClass(SysUserNotification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentUrl()).isNull();
        assertThat(captor.getValue().getRefId()).isEqualTo("1900000000000000124");
    }

    /**
     * 构造一条工作流通知事件。
     */
    private UserNotificationEvent buildWorkflowEvent(String category, String title, String contentUrl) {
        UserNotificationEvent event = new UserNotificationEvent();
        event.setTenantId("000000");
        event.setTargetUserId(100L);
        event.setCategory(category);
        event.setRefType("workflow");
        event.setTitle(title);
        event.setContent(title + "，请点击查看");
        event.setContentUrl(contentUrl);
        return event;
    }
}
