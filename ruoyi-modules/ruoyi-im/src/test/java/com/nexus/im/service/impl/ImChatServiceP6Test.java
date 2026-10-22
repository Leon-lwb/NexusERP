package com.nexus.im.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.msgtype.ImMessageTypeHandler;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImMetrics;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.service.impl.support.ImChatSupport;
import com.nexus.im.service.impl.support.ImConversationCache;
import com.nexus.im.service.impl.support.ImGroupChatService;
import com.nexus.im.service.impl.support.ImPrivateChatService;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P6-G1: 引用消息字段填充测试（B3 回归守护）。
 * <p>
 * 守护 P6-B3 阶段在 sendPrivate / sendGroup 中引入的 replyMessageId / replySnippet 填充逻辑：
 * <ul>
 *   <li>replyMessageId 非 null 且原消息未撤回 → 填充 replyMessageId + replySnippet（截断 200）</li>
 *   <li>replyMessageId 指向已撤回消息（revoked="1"）→ 不填充（replyMessageId / replySnippet 均为 null）</li>
 *   <li>replyMessageId 为 null → 不填充</li>
 *   <li>replyMessageId 指向不存在的消息 → 不填充</li>
 * </ul>
 * <p>
 * 设计要点（与 ImGroupChatServiceTest 一致）：
 * <ul>
 *   <li>纯 Mockito 单元测试，{@code @ExtendWith(MockitoExtension.class)} 由 {@link ImUnitTestBase} 提供</li>
 *   <li>{@code mockStatic(ImEnvelopeHelper.class)} 让推送信封 JSON 序列化避开 JsonUtils.OBJECT_MAPPER 静态初始化
 *       （依赖 Spring 容器，无容器环境下抛 ExceptionInInitializerError）</li>
 *   <li>用 ArgumentCaptor 捕获 {@code imMessageMapper.insert(msg)} 的 ImMessage 参数，验证字段填充</li>
 *   <li>{@code support.requireSameTenantUser} 用 lenient 桩透传，{@code support.findExistingByClientMsgId} 返回 null（非幂等命中）</li>
 * </ul>
 * <p>
 * 任意重构若移除 {@code bo.getReplyMessageId() != null} 判空或撤回校验，对应用例立即失败。
 */
class ImChatServiceP6Test extends ImUnitTestBase {

    @Mock
    private ImMessageMapper imMessageMapper;
    @Mock
    private ImGroupMapper imGroupMapper;
    @Mock
    private ImGroupMemberMapper imGroupMemberMapper;
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
    @Mock
    private ImConversationCache conversationCache;
    @Mock
    private ImMetrics imMetrics;

    @InjectMocks
    private ImPrivateChatService imPrivateChatService;
    @InjectMocks
    private ImGroupChatService imGroupChatService;

    /**
     * mockStatic ImEnvelopeHelper：让 ImEnvelopeHelper.imMessage(...) 用真实 ObjectMapper 序列化 payload。
     * <p>避开 JsonUtils.OBJECT_MAPER 静态初始化对 Spring 容器的依赖。
     */
    private MockedStatic<ImEnvelopeHelper> envelopeHelperMock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 简易消息类型 handler 桩：normalize 透传，type="text"。
     */
    private final ImMessageTypeHandler textHandler = new ImMessageTypeHandler() {
        @Override
        public String type() {
            return "text";
        }

        @Override
        public String normalize(String content) {
            return content;
        }
    };

    @BeforeEach
    void setUpChatService() {
        // 公共桩：requireSameTenantUser 透传，返回最小 SysUserVo
        lenient().when(support.requireSameTenantUser(anyLong())).thenAnswer(invocation -> {
            SysUserVo u = new SysUserVo();
            u.setUserId(invocation.getArgument(0));
            u.setTenantId(TENANT_ID);
            return u;
        });
        // 公共桩：幂等查询返回 null（不命中，正常 insert 路径）
        lenient().when(support.findExistingByClientMsgId(any(), anyLong(), any(), any())).thenReturn(null);
        // 公共桩：消息类型 handler — text 透传
        lenient().when(messageTypeRegistry.require(any())).thenReturn(textHandler);
        // 公共桩：发送方 SysUserVo（避免 NPE）
        lenient().when(sysUserService.selectUserById(ME_USER_ID)).thenReturn(new SysUserVo());

        // mockStatic ImEnvelopeHelper — 用真实 ObjectMapper 序列化
        envelopeHelperMock = Mockito.mockStatic(ImEnvelopeHelper.class);
        envelopeHelperMock
                .when(() -> ImEnvelopeHelper.imMessage(any()))
                .thenAnswer(
                        inv -> {
                            Map<String, Object> payload = inv.getArgument(0);
                            Map<String, Object> root = new LinkedHashMap<>();
                            root.put("v", "1");
                            root.put("type", "IM_MESSAGE");
                            root.put("ts", System.currentTimeMillis());
                            root.put("payload", payload);
                            try {
                                return objectMapper.writeValueAsString(root);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @AfterEach
    void tearDownChatService() {
        if (envelopeHelperMock != null) {
            envelopeHelperMock.close();
        }
    }

    // ==================== sendPrivate replyMessageId 填充 ====================

    /**
     * P6-B3 核心守护: sendPrivate 携带 replyMessageId 且原消息未撤回
     * → msg.replyMessageId / replySnippet 正确填充。
     * <p>守护点：
     * <ul>
     *   <li>msg.replyMessageId == bo.replyMessageId</li>
     *   <li>msg.replySnippet == 原消息内容（truncate 200）</li>
     * </ul>
     */
    @Test
    void sendPrivate_withReplyMessageId_fillsReplyFields() {
        // 原消息：未撤回（revoked="0"），内容 "origin content"
        ImMessage origin = ImTestFixture.buildMessage(100L, PEER_USER_ID, ME_USER_ID, null, "origin content");
        when(imMessageMapper.selectById(100L)).thenReturn(origin);

        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("reply body");
        bo.setMsgType("text");
        bo.setReplyMessageId(100L);

        imPrivateChatService.sendPrivate(bo);

        // 捕获 insert 的 ImMessage，验证 reply 字段
        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        assertThat(inserted.getReplyMessageId()).isEqualTo(100L);
        assertThat(inserted.getReplySnippet()).isEqualTo("origin content");
    }

    /**
     * P6-B3: replyMessageId 指向已撤回消息 → 不填充 replyMessageId / replySnippet。
     * <p>守护点：origin.revoked == "1" 时 replyMessageId 与 replySnippet 均为 null，
     * 避免引用一条已被对方撤回的消息（撤回后内容不应再被引用展示）。
     */
    @Test
    void sendPrivate_replyToRevokedMessage_doesNotFillReplyFields() {
        // 原消息已撤回
        ImMessage origin = ImTestFixture.buildMessage(100L, PEER_USER_ID, ME_USER_ID, null, "revoked content");
        origin.setRevoked("1");
        when(imMessageMapper.selectById(100L)).thenReturn(origin);

        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("reply body");
        bo.setMsgType("text");
        bo.setReplyMessageId(100L);

        imPrivateChatService.sendPrivate(bo);

        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        // 守护：撤回消息不被填充
        assertThat(inserted.getReplyMessageId()).isNull();
        assertThat(inserted.getReplySnippet()).isNull();
    }

    /**
     * P6-B3: replyMessageId 为 null → 不查询原消息，不填充 reply 字段。
     */
    @Test
    void sendPrivate_nullReplyMessageId_doesNotFillReplyFields() {
        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("normal message");
        bo.setMsgType("text");
        // replyMessageId 未设置（null）

        imPrivateChatService.sendPrivate(bo);

        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        assertThat(inserted.getReplyMessageId()).isNull();
        assertThat(inserted.getReplySnippet()).isNull();
        // 守护：replyMessageId=null 时不应查询原消息
        verify(imMessageMapper, never()).selectById(anyLong());
    }

    /**
     * P6-B3: replyMessageId 指向不存在的消息（selectById 返回 null）→ 不填充。
     */
    @Test
    void sendPrivate_replyToNonexistentMessage_doesNotFillReplyFields() {
        when(imMessageMapper.selectById(100L)).thenReturn(null);

        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("reply body");
        bo.setMsgType("text");
        bo.setReplyMessageId(100L);

        imPrivateChatService.sendPrivate(bo);

        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        assertThat(inserted.getReplyMessageId()).isNull();
        assertThat(inserted.getReplySnippet()).isNull();
    }

    /**
     * P6-B3: replySnippet 超过 200 字 → 截断到 200 字。
     */
    @Test
    void sendPrivate_longReplySnippet_truncatedTo200() {
        String longContent = "x".repeat(300);
        ImMessage origin = ImTestFixture.buildMessage(100L, PEER_USER_ID, ME_USER_ID, null, longContent);
        when(imMessageMapper.selectById(100L)).thenReturn(origin);

        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("reply body");
        bo.setMsgType("text");
        bo.setReplyMessageId(100L);

        imPrivateChatService.sendPrivate(bo);

        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        assertThat(inserted.getReplySnippet()).hasSize(200);
    }

    // ==================== sendGroup replyMessageId 填充 ====================

    /**
     * P6-B3: sendGroup 携带 replyMessageId 且原消息未撤回 → replyMessageId / replySnippet 正确填充。
     * <p>守护点：群聊路径与私聊路径走同一份 reply 填充逻辑，行为应一致。
     */
    @Test
    void sendGroup_withReplyMessageId_fillsReplyFields() {
        // 群存在 + 当前用户是群成员
        ImGroup group = ImTestFixture.buildGroup(1L, "test-group", ME_USER_ID);
        when(imGroupMapper.selectById(1L)).thenReturn(group);
        when(support.isGroupMember(eq(1L), eq(ME_USER_ID))).thenReturn(true);
        // 群成员列表为空（避免扇出推送路径触发更多 mock 依赖）
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());

        // 原消息：未撤回
        ImMessage origin = ImTestFixture.buildMessage(100L, PEER_USER_ID, null, 1L, "group origin");
        when(imMessageMapper.selectById(100L)).thenReturn(origin);

        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(1L);
        bo.setContent("group reply");
        bo.setMsgType("text");
        bo.setReplyMessageId(100L);

        imGroupChatService.sendGroup(bo);

        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        assertThat(inserted.getReplyMessageId()).isEqualTo(100L);
        assertThat(inserted.getReplySnippet()).isEqualTo("group origin");
    }

    /**
     * P6-B3: sendGroup replyMessageId 指向已撤回消息 → 不填充。
     */
    @Test
    void sendGroup_replyToRevokedMessage_doesNotFillReplyFields() {
        ImGroup group = ImTestFixture.buildGroup(1L, "test-group", ME_USER_ID);
        when(imGroupMapper.selectById(1L)).thenReturn(group);
        when(support.isGroupMember(eq(1L), eq(ME_USER_ID))).thenReturn(true);
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());

        // 原消息已撤回
        ImMessage origin = ImTestFixture.buildMessage(100L, PEER_USER_ID, null, 1L, "revoked");
        origin.setRevoked("1");
        when(imMessageMapper.selectById(100L)).thenReturn(origin);

        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(1L);
        bo.setContent("group reply");
        bo.setMsgType("text");
        bo.setReplyMessageId(100L);

        imGroupChatService.sendGroup(bo);

        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        assertThat(inserted.getReplyMessageId()).isNull();
        assertThat(inserted.getReplySnippet()).isNull();
    }

    /**
     * P6-B3: sendGroup replyMessageId 为 null → 不查询原消息，不填充。
     */
    @Test
    void sendGroup_nullReplyMessageId_doesNotFillReplyFields() {
        ImGroup group = ImTestFixture.buildGroup(1L, "test-group", ME_USER_ID);
        when(imGroupMapper.selectById(1L)).thenReturn(group);
        when(support.isGroupMember(eq(1L), eq(ME_USER_ID))).thenReturn(true);
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());

        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(1L);
        bo.setContent("normal group message");
        bo.setMsgType("text");
        // replyMessageId 未设置

        imGroupChatService.sendGroup(bo);

        ArgumentCaptor<ImMessage> captor = ArgumentCaptor.forClass(ImMessage.class);
        verify(imMessageMapper).insert(captor.capture());
        ImMessage inserted = captor.getValue();
        assertThat(inserted.getReplyMessageId()).isNull();
        assertThat(inserted.getReplySnippet()).isNull();
        // 守护：replyMessageId=null 时不应查询原消息
        verify(imMessageMapper, never()).selectById(anyLong());
    }
}
