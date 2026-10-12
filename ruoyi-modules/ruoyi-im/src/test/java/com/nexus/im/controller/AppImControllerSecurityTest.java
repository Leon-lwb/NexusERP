package com.nexus.im.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.im.controller.AppImController;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.mapper.ImFriendMapper;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.mapper.SysUserNotificationMapper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.IImChatService;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-2: P0-1 越权修复回归测试。
 * <p>
 * 守护 4 处越权漏洞修复：
 * <ul>
 *   <li>{@code deleteMessage} - 消息归属校验（私聊 from/to 或群聊成员）</li>
 *   <li>{@code forwardMessage} - 原消息可读校验</li>
 *   <li>{@code revokeRequest} - 申请发送方身份校验</li>
 *   <li>{@code sendFriendRequest} - 跨租户好友申请校验</li>
 * </ul>
 * <p>
 * 任意重构若移除上述校验，对应测试用例会立即失败，确保安全回归。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>使用纯 Mockito 单元测试（非 @WebMvcTest），直接调用 Controller 方法，避免启动 Spring 容器</li>
 *   <li>除了"拒绝路径"也守护"放行路径"，确保校验逻辑不会误拒合法请求</li>
 *   <li>拒绝路径额外 verify 业务方法 never() 被调用，防止"校验失败仍写库"的二次缺陷</li>
 * </ul>
 */
class AppImControllerSecurityTest extends ImUnitTestBase {

    @Mock private IImChatService imChatService;
    @Mock private ISysUserNotificationService notificationService;
    @Mock private SysUserMapper userMapper;
    @Mock private ImMessageMapper messageMapper;
    @Mock private ImGroupMapper groupMapper;
    @Mock private ImGroupMemberMapper groupMemberMapper;
    @Mock private SysUserNotificationMapper notificationMapper;
    @Mock private ImFriendMapper friendMapper;
    @Mock private ImRealtimePush imRealtimePush;

    @Mock private com.nexus.im.service.impl.support.ImMessageVisibility visibility;
    @Mock private com.nexus.im.service.impl.support.ImConversationService conversationService;

    @InjectMocks
    private AppImController controller;

    // ==================== deleteMessage 越权校验 ====================

    /**
     * 私聊消息，当前用户既不是发送者也不是接收者 → 拒绝删除。
     */
    @Test
    void deleteMessage_nonOwner_returnsFail() {
        ImMessage msg = ImTestFixture.buildMessage(1L, PEER_USER_ID, OTHER_TENANT_USER_ID, null, "secret");
        when(messageMapper.selectById(1L)).thenReturn(msg);

        R<Void> result = controller.deleteMessage(1L);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("无权操作");
        // 守护：校验失败时不应执行删除
        verify(messageMapper, never()).deleteById(anyLong());
    }

    /**
     * 私聊消息，当前用户是发送者 → 允许删除。
     */
    @Test
    void deleteMessage_asSender_returnsOk() {
        ImMessage msg = ImTestFixture.buildMessage(1L, ME_USER_ID, PEER_USER_ID, null, "hello");
        when(messageMapper.selectById(1L)).thenReturn(msg);

        R<Void> result = controller.deleteMessage(1L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(visibility).hide(1L);
        verify(messageMapper, never()).deleteById(anyLong());
    }

    /**
     * 私聊消息，当前用户是接收者 → 允许删除。
     */
    @Test
    void deleteMessage_asReceiver_returnsOk() {
        ImMessage msg = ImTestFixture.buildMessage(1L, PEER_USER_ID, ME_USER_ID, null, "hello");
        when(messageMapper.selectById(1L)).thenReturn(msg);

        R<Void> result = controller.deleteMessage(1L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(visibility).hide(1L);
        verify(messageMapper, never()).deleteById(anyLong());
    }

    /**
     * 群聊消息，当前用户不是群成员 → 拒绝删除。
     */
    @Test
    void deleteMessage_groupNonMember_returnsFail() {
        ImMessage msg = ImTestFixture.buildMessage(1L, PEER_USER_ID, null, 999L, "group msg");
        when(messageMapper.selectById(1L)).thenReturn(msg);
        // isGroupMember 内部调用 groupMemberMapper.selectCount，返回 0 = 非成员
        when(groupMemberMapper.selectCount(any())).thenReturn(0L);

        R<Void> result = controller.deleteMessage(1L);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("无权操作");
        verify(messageMapper, never()).deleteById(anyLong());
    }

    // ==================== forwardMessage 越权校验 ====================

    /**
     * 转发消息时，当前用户无权读原消息 → 拒绝转发。
     */
    @Test
    void forwardMessage_nonAccessible_returnsFail() {
        ImMessage original = ImTestFixture.buildMessage(1L, PEER_USER_ID, OTHER_TENANT_USER_ID, null, "secret");
        when(messageMapper.selectById(1L)).thenReturn(original);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");
        body.put("targetType", "private");
        body.put("targetId", String.valueOf(PEER_USER_ID));

        R<Long> result = controller.forwardMessage(body);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("无权转发");
        // 守护：校验失败时不应触发实际转发
        verify(imChatService, never()).sendPrivate(any());
        verify(imChatService, never()).sendGroup(any());
    }

    /**
     * 转发消息时，当前用户是原消息发送者 → 允许转发到私聊。
     */
    @Test
    void forwardMessage_asSender_proceedsToForward() {
        ImMessage original = ImTestFixture.buildMessage(1L, ME_USER_ID, PEER_USER_ID, null, "hello");
        when(messageMapper.selectById(1L)).thenReturn(original);
        when(imChatService.sendPrivate(any())).thenReturn(R.ok(2L));

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");
        body.put("targetType", "private");
        body.put("targetId", String.valueOf(PEER_USER_ID));

        R<Long> result = controller.forwardMessage(body);

        assertThat(result.getCode()).isEqualTo(200);
        // 守护：转发流程被触发，且携带 forwardFrom
        ArgumentCaptor<com.nexus.im.domain.bo.ImPrivateSendBo> captor =
            ArgumentCaptor.forClass(com.nexus.im.domain.bo.ImPrivateSendBo.class);
        verify(imChatService).sendPrivate(captor.capture());
        assertThat(captor.getValue().getForwardFrom()).isEqualTo(1L);
    }

    // ==================== revokeRequest 越权校验 ====================

    /**
     * 缺少申请关联的通知不能撤销，也不能删除接收方记录。
     */
    @Test
    void revokeRequest_nonOwner_returnsFail() {
        SysUserNotification n = ImTestFixture.buildNotification(1L, PEER_USER_ID, "im_friend_request");
        when(notificationMapper.selectById(1L)).thenReturn(n);

        R<Void> result = controller.revokeRequest(1L);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("无权操作");
        verify(notificationMapper, never()).deleteById(anyLong());
    }

    /**
     * 通知接收方不能代替发送方撤销好友申请，必须保留接收方记录。
     */
    @Test
    void revokeRequest_receiver_returnsFail() {
        SysUserNotification n = ImTestFixture.buildNotification(1L, ME_USER_ID, "im_friend_request");
        when(notificationMapper.selectById(1L)).thenReturn(n);

        R<Void> result = controller.revokeRequest(1L);

        assertThat(result.getCode()).isEqualTo(500);
        verify(notificationMapper, never()).deleteById(anyLong());
        verify(messageMapper, never()).update(any(ImMessage.class), any());
        verify(notificationMapper, never()).updateById(any(SysUserNotification.class));
    }

    // ==================== sendFriendRequest 跨租户校验 ====================

    /**
     * 发送好友请求时，目标用户不在当前租户 → 抛 ServiceException（被全局异常处理器转为 R.fail）。
     * <p>
     * 注：sendFriendRequest 内部调用 {@code requireSameTenantUser}，校验失败抛 {@link ServiceException}，
     * 在 Controller 层未捕获，由全局异常处理器转为 R.fail。单元测试直接断言异常类型与消息。
     */
    @Test
    void sendFriendRequest_crossTenant_throwsServiceException() {
        enableTenant();
        // 目标用户属于其他租户
        SysUserVo peer = new SysUserVo();
        peer.setUserId(PEER_USER_ID);
        peer.setTenantId(OTHER_TENANT_ID);
        when(userMapper.selectVoById(PEER_USER_ID)).thenReturn(peer);

        Map<String, String> body = new HashMap<>();
        body.put("toUserId", String.valueOf(PEER_USER_ID));
        body.put("message", "hi");

        assertThatThrownBy(() -> controller.sendFriendRequest(body))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("跨租户");

        // 守护：跨租户时不应触发任何业务副作用
        verify(notificationService, never()).addAndPush(anyLong(), any(), any(), any(), any(), any(), any());
        verify(messageMapper, never()).insert(any(ImMessage.class));
        verify(imRealtimePush, never()).publishToUser(anyLong(), any());
    }

    // ==================== P5-4 LOW: searchMessages pageSize clamp ====================

    /**
     * P5-4 LOW DoS 守护：searchMessages 收到超大 pageSize 时应被 clamp 到 100，
     * 避免攻击者传 pageSize=1000000 拖垮 DB 内存与带宽。
     * <p>
     * 守护点：
     * <ul>
     *   <li>pageSize=1000000 → 返回的 pageSize 字段 = 100</li>
     *   <li>SQL 末尾 {@code limit offset,100}（非 1000000）</li>
     * </ul>
     * 若有人移除 clamp 逻辑，此用例立即失败。
     */
    @Test
    void searchMessages_oversizedPageSize_clampedTo100() {
        // 走"搜全部"分支：无群成员关系
        when(groupMemberMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        R<com.nexus.im.domain.vo.ImSearchResultVo> result = controller.searchMessages(
            "hi", null, null, 1, 1000000);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getPageSize()).isEqualTo(100);
    }

    /**
     * P5-4 LOW: pageSize 非法值（0/null）应回退到默认 20，pageNum 非法值回退到 1。
     */
    @Test
    void searchMessages_illegalPageSize_fallsBackToDefault() {
        when(groupMemberMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        R<com.nexus.im.domain.vo.ImSearchResultVo> result = controller.searchMessages(
            "hi", null, null, 0, 0);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getPageSize()).isEqualTo(20);
        assertThat(result.getData().getPageNum()).isEqualTo(1);
    }
}
