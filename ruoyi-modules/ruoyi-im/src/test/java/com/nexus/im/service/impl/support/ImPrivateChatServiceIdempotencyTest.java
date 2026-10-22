package com.nexus.im.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.common.core.domain.R;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * P3-7: P1-4 client_msg_id 幂等回归测试。
 * <p>
 * 守护 P1-4 阶段为私聊消息发送引入 client_msg_id 幂等：
 * <ul>
 *   <li>客户端生成 UUID 作为 client_msg_id，重发复用同一值</li>
 *   <li>后端按 (tenant_id, client_msg_id, from_user_id, to_user_id) 查询已存在消息</li>
 *   <li>命中则直接返回旧 messageId，不重复 insert / 不重复推送</li>
 * </ul>
 * <p>
 * 测试策略：仅覆盖 {@code sendPrivate} 的早返回路径（self / duplicateClientMsgId），
 * 避免触发 {@code ImEnvelopeHelper.imMessage} → {@code JsonUtils.toJsonString} 静态初始化失败
 * （JsonUtils.OBJECT_MAPPER 依赖 Spring 容器，移交 P3-9 集成测试覆盖完整发送路径）。
 */
class ImPrivateChatServiceIdempotencyTest extends ImUnitTestBase {

    @Mock
    private ImMessageMapper imMessageMapper;
    @Mock
    private ISysUserService sysUserService;
    @Mock
    private ISysUserNotificationService sysUserNotificationService;
    @Mock
    private ImRealtimePush imRealtimePush;
    @Mock
    private ImMessageTypeRegistry messageTypeRegistry;
    @Mock
    private ImChatSupport support;

    @InjectMocks
    private ImPrivateChatService imPrivateChatService;

    private static final String CLIENT_MSG_ID = "client-uuid-001";
    private static final Long EXISTING_MESSAGE_ID = 999L;

    /**
     * P1-4: 给自己发私聊 → 返回 R.fail("不能给自己发私聊")，不触发幂等查询。
     */
    @Test
    void sendPrivate_self_returnsFail() {
        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(ME_USER_ID);
        bo.setContent("hi");

        R<Long> result = imPrivateChatService.sendPrivate(bo);

        assertThat(result.getCode()).isEqualTo(R.FAIL);
        assertThat(result.getMsg()).contains("不能给自己");
        // 守护：自我发送场景不应触发任何后续业务
        verify(support, never()).requireSameTenantUser(anyLong());
        verify(support, never()).findExistingByClientMsgId(any(), anyLong(), anyLong(), any());
        verify(imMessageMapper, never()).insert(any(ImMessage.class));
    }

    /**
     * P1-4 核心守护: 重发携带相同 client_msg_id → 命中幂等查询，直接返回旧 messageId，
     * 不触发 insert / 推送 / 通知。
     * <p>若有人移除 {@code support.findExistingByClientMsgId} 调用，此用例立即失败。
     */
    @Test
    void sendPrivate_duplicateClientMsgId_returnsOldMessageId() {
        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("hi");
        bo.setClientMsgId(CLIENT_MSG_ID);

        // mock requireSameTenantUser 不抛异常
        when(support.requireSameTenantUser(PEER_USER_ID)).thenReturn(new SysUserVo());
        // mock 幂等查询命中：返回旧 messageId
        when(support.findExistingByClientMsgId(eq(CLIENT_MSG_ID), eq(ME_USER_ID), eq(PEER_USER_ID), eq(null)))
            .thenReturn(EXISTING_MESSAGE_ID);

        R<Long> result = imPrivateChatService.sendPrivate(bo);

        // 守护：返回旧 messageId（而非新分配的 ID）
        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        assertThat(result.getData()).isEqualTo(EXISTING_MESSAGE_ID);
        // 守护：幂等命中 → 不重复 insert
        verify(imMessageMapper, never()).insert(any(ImMessage.class));
        // 守护：幂等命中 → 不重复推送通知
        verify(sysUserNotificationService, never()).addAndPush(anyLong(), any(), any(), any(), any(), any(), any());
        // 守护：幂等命中 → 不重复实时推送
        verify(imRealtimePush, never()).publishToUserAfterCommit(anyLong(), any());
    }
}
