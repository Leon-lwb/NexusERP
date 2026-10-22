package com.nexus.im;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.sse.core.SseEmitterManager;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.web.core.BigNumberSerializer;
import com.nexus.common.websocket.utils.WebSocketUtils;
import com.nexus.im.controller.AppImController;
import com.nexus.im.controller.SysUserNotificationController;
import com.nexus.im.domain.*;
import com.nexus.im.domain.bo.*;
import com.nexus.im.mapper.*;
import com.nexus.im.msgtype.ImMessageTypeHandler;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.*;
import com.nexus.im.service.*;
import com.nexus.im.service.impl.SysUserNotificationServiceImpl;
import com.nexus.im.service.impl.support.*;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.service.ISysUserService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.LongStream;

/** APP 与 PC 共用消息链路的安全和交互行为回归，不连接真实数据库或 Redis。 */
class ImBehaviorRegressionTest extends ImUnitTestBase {
    @Mock IImChatService chat;
    @Mock ISysUserNotificationService notifications;
    @Mock SysUserMapper users;
    @Mock ImMessageMapper messages;
    @Mock ImGroupMapper groups;
    @Mock ImGroupMemberMapper members;
    @Mock SysUserNotificationMapper notificationMapper;
    @Mock ImFriendMapper friends;
    @Mock ImRealtimePush push;
    @Mock ImConversationService conversations;
    @Mock ImConversationSettingMapper settings;
    @Mock ImFavoriteMapper favorites;
    @Mock ImContactSupport contacts;
    @Mock ImOnlineCache online;
    @Mock ISysUserService userService;
    @Mock ImMessageTypeRegistry types;
    @Mock ImChatSupport support;
    @Mock ImConversationCache cache;
    @Mock ImMetrics metrics;
    @Mock ImMessageVisibility visibility;
    @InjectMocks AppImController controller;
    @InjectMocks ImGroupChatService groupChat;
    @InjectMocks ImPrivateChatService privateChat;

    static final Long GROUP = 900L;
    static final Long MESSAGE = 1_900_000_000_000_000_001L;
    static final Long NOTICE = 1_900_000_000_000_000_002L;
    static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void infrastructure() {
        // 只注册序列化和通道替身，避免静态工具初始化触及外部服务。
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, BigNumberSerializer.instance);
        module.addSerializer(Long.TYPE, BigNumberSerializer.instance);
        JSON.registerModule(module);
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("objectMapper", JSON);
        context.getBeanFactory().registerSingleton("redissonClient", mock(RedissonClient.class));
        context.getBeanFactory().registerSingleton("sseEmitterManager", mock(SseEmitterManager.class));
        context.refresh();
        SpringUtils spring = new SpringUtils();
        spring.postProcessBeanFactory(context.getBeanFactory());
        spring.setApplicationContext(context);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(ImMessage.class, ImGroup.class, ImGroupMember.class,
            SysUserNotification.class, ImFriend.class, ImConversationSetting.class, ImFavorite.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @Test
    void unauthorizedFriendHandling_hasNoWrites() {
        ImMessage request = friendRequest(PEER_USER_ID, OTHER_TENANT_USER_ID);
        when(messages.selectById(MESSAGE)).thenReturn(request);
        R<Void> result = controller.handleFriendRequest(Map.of("messageId", MESSAGE.toString(), "accept", true));
        assertThat(result.getCode()).isNotEqualTo(200);
        verify(messages, never()).updateById(any(ImMessage.class));
        verifyNoInteractions(notificationMapper, friends, push);
    }

    @Test
    void unrelatedNotificationAndMessage_areRejectedBeforeWrites() {
        when(messages.selectById(MESSAGE)).thenReturn(friendRequest(PEER_USER_ID, ME_USER_ID));
        SysUserNotification unrelated = ImTestFixture.buildNotification(NOTICE, ME_USER_ID, "system");
        unrelated.setRefId("123");
        when(notificationMapper.selectById(NOTICE)).thenReturn(unrelated);
        R<Void> result = controller.handleFriendRequest(Map.of("messageId", MESSAGE.toString(),
            "id", NOTICE.toString(), "accept", false));
        assertThat(result.getCode()).isNotEqualTo(200);
        verify(messages, never()).updateById(any(ImMessage.class));
        verify(notificationMapper, never()).updateById(any(SysUserNotification.class));
        verifyNoInteractions(friends, push);
    }

    @Test
    void friendSendAndHandle_areTransactional() throws Exception {
        assertThat(AppImController.class.getMethod("sendFriendRequest", Map.class)
            .getAnnotation(Transactional.class)).isNotNull();
        assertThat(AppImController.class.getMethod("handleFriendRequest", Map.class)
            .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void appRevoke_returnsServiceFailureAndDoesNotRequireUnknownPermission() throws Exception {
        when(chat.revokeMessage(MESSAGE)).thenReturn(R.fail("超过2分钟，无法撤回"));
        assertThat(controller.revokeMessage(Map.of("messageId", MESSAGE.toString())).getCode()).isNotEqualTo(200);
        assertThat(AppImController.class.getMethod("revokeMessage", Map.class)
            .getAnnotation(SaCheckPermission.class)).isNull();
    }

    @Test
    void deleteMessage_neverDeletesSharedRecord() {
        when(messages.selectById(MESSAGE)).thenReturn(
            ImTestFixture.buildMessage(MESSAGE, PEER_USER_ID, ME_USER_ID, null, "保留共享记录"));
        controller.deleteMessage(MESSAGE);
        verify(messages, never()).deleteById(anyLong());
    }

    @Test
    void groupRead_updatesOnlyCurrentUsersNotifications() {
        when(groups.selectById(GROUP)).thenReturn(ImTestFixture.buildGroup(GROUP, "群", PEER_USER_ID));
        when(support.isGroupMember(GROUP, ME_USER_ID)).thenReturn(true);
        // 旧实现从共享消息取水位，修复后应从本人的未读通知取消息。
        lenient().when(messages.selectMaps(any())).thenReturn(List.of(Map.of("fromUserId", PEER_USER_ID, "maxMessageId", MESSAGE)));
        SysUserNotification pending = ImTestFixture.buildNotification(NOTICE, ME_USER_ID, "im_group");
        pending.setRefId(MESSAGE.toString());
        lenient().when(notificationMapper.selectList(any())).thenReturn(List.of(pending));
        lenient().when(messages.selectList(any())).thenReturn(List.of(
            ImTestFixture.buildMessage(MESSAGE, PEER_USER_ID, null, GROUP, "未读")));
        groupChat.markGroupRead(GROUP);
        verify(messages, never()).update(any(ImMessage.class), any(Wrapper.class));
        verify(notificationMapper).update(any(SysUserNotification.class), argThat(w -> {
            String sql = w.getSqlSegment();
            return sql.contains("user_id") && sql.contains("notification_id") && sql.contains("read_flag");
        }));
    }

    @Test
    void oversizedMentionJson_isRejectedBeforeInsert() {
        prepareSend();
        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(GROUP);
        bo.setContent("@多人");
        bo.setMentionUserIds(LongStream.range(1_900_000_000_000_000_100L, 1_900_000_000_000_000_130L).boxed().toList());
        assertThatThrownBy(() -> groupChat.sendGroup(bo)).isInstanceOf(ServiceException.class)
            .hasMessageContaining("@成员");
        verify(messages, never()).insert(any(ImMessage.class));
    }

    @Test
    void groupNotification_containsExactRoutingAndMentionMetadata() throws Exception {
        prepareSend();
        when(members.selectList(any())).thenReturn(ImTestFixture.buildGroupMembers(GROUP, List.of(ME_USER_ID, PEER_USER_ID)));
        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(GROUP);
        bo.setContent("@同事 你好");
        bo.setMentionUserIds(List.of(PEER_USER_ID));
        groupChat.sendGroup(bo);
        ArgumentCaptor<List<SysUserNotification>> rows = ArgumentCaptor.forClass(List.class);
        verify(notifications).batchAddAndPush(rows.capture());
        String ext = rows.getValue().get(0).getExtJson();
        assertThat(ext).isNotBlank();
        Map<?, ?> payload = JSON.readValue(ext, Map.class);
        assertThat(String.valueOf(payload.get("messageId"))).isEqualTo(MESSAGE.toString());
        assertThat(String.valueOf(payload.get("senderId"))).isEqualTo(ME_USER_ID.toString());
        assertThat(String.valueOf(payload.get("groupId"))).isEqualTo(GROUP.toString());
        assertThat(payload.get("mentioned")).isEqualTo(true);
        assertThat(payload.get("groupName")).isEqualTo("群");
    }

    @Test
    void duplicateKeyConflict_isBusinessResultWithoutNotifications() {
        prepareSend();
        when(messages.insert(any(ImMessage.class))).thenThrow(new DuplicateKeyException("冲突"));
        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("重试");
        bo.setClientMsgId("重复标识");
        assertThatCode(() -> {
            R<Long> result = privateChat.sendPrivate(bo);
            assertThat(result.getCode()).isNotEqualTo(200);
            assertThat(result.getMsg()).contains("消息标识");
        }).doesNotThrowAnyException();
        verifyNoInteractions(notifications, push);
    }

    @Test
    void notificationRead_isIdempotentForZeroUpdate() {
        ISysUserNotificationService service = mock(ISysUserNotificationService.class);
        SysUserNotificationController api = new SysUserNotificationController(service);
        when(service.markRead(NOTICE)).thenReturn(0);
        assertThat(api.markRead(NOTICE).getCode()).isEqualTo(200);
    }

    @Test
    void notificationPush_retainsExtJsonAndCreateTime() throws Exception {
        when(notificationMapper.insert(any(SysUserNotification.class))).thenAnswer(inv -> {
            SysUserNotification row = inv.getArgument(0);
            row.setNotificationId(NOTICE);
            row.setCreateTime(new Date(1791772800000L));
            return 1;
        });
        UserNotificationEvent event = new UserNotificationEvent();
        event.setTargetUserId(ME_USER_ID);
        event.setCategory("im_group");
        event.setRefId(MESSAGE.toString());
        event.setRefType("im_message");
        event.setContent("消息");
        event.setExtJson("{\"groupId\":\"900\",\"messageId\":\"" + MESSAGE + "\"}");
        new SysUserNotificationServiceImpl(notificationMapper, push, cache).handleUserNotificationEvent(event);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(push).publishToUserAfterCommit(eq(ME_USER_ID), json.capture());
        Map<?, ?> payload = (Map<?, ?>) JSON.readValue(json.getValue(), Map.class).get("payload");
        assertThat(payload.get("extJson")).isEqualTo(event.getExtJson());
        assertThat(payload.get("createTime")).isNotNull();
    }

    @Test
    void imSse_usesClusterPublish() {
        ImRealtimePush realtime = new ImRealtimePush(new ImMetrics(new SimpleMeterRegistry()));
        try (MockedStatic<WebSocketUtils> ws = mockStatic(WebSocketUtils.class);
             MockedStatic<SseMessageUtils> sse = mockStatic(SseMessageUtils.class)) {
            sse.when(SseMessageUtils::isEnable).thenReturn(true);
            realtime.publishToUser(ME_USER_ID, "消息");
            sse.verify(() -> SseMessageUtils.publishMessage(argThat(dto ->
                dto.getUserIds().equals(List.of(ME_USER_ID)) && dto.getMessage().equals("消息"))));
            sse.verify(() -> SseMessageUtils.sendMessage(anyLong(), anyString()), never());
        }
    }

    @Test
    void disbandedGroup_historyIsRejected() {
        ImGroup group = ImTestFixture.buildGroup(GROUP, "已解散", PEER_USER_ID);
        group.setStatus("1");
        when(groups.selectById(GROUP)).thenReturn(group);
        lenient().when(support.isGroupMember(GROUP, ME_USER_ID)).thenReturn(true);
        assertThatThrownBy(() -> groupChat.groupHistory(GROUP, pageQuery()))
            .isInstanceOf(ServiceException.class).hasMessageContaining("已解散");
        verify(messages, never()).selectPage(any(), any());
    }

    /** 引用不能把其他私聊的正文带入当前会话。 */
    @Test
    void privateReply_cannotReadAnotherConversation() {
        prepareSend();
        when(messages.selectById(99L)).thenReturn(ImTestFixture.buildMessage(99L, PEER_USER_ID,
            OTHER_TENANT_USER_ID, null, "其他人的私聊"));
        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(PEER_USER_ID);
        bo.setContent("引用");
        bo.setReplyMessageId(99L);
        assertThatThrownBy(() -> privateChat.sendPrivate(bo)).isInstanceOf(ServiceException.class).hasMessageContaining("引用");
        verify(messages, never()).insert(any(ImMessage.class));
    }

    /** 群引用必须来自当前群，不能按猜到的消息 ID 读取别的群。 */
    @Test
    void groupReply_cannotReadAnotherGroup() {
        prepareSend();
        when(messages.selectById(99L)).thenReturn(ImTestFixture.buildMessage(99L, PEER_USER_ID, null,
            901L, "其他群消息"));
        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(GROUP);
        bo.setContent("引用");
        bo.setReplyMessageId(99L);
        assertThatThrownBy(() -> groupChat.sendGroup(bo)).isInstanceOf(ServiceException.class).hasMessageContaining("引用");
        verify(messages, never()).insert(any(ImMessage.class));
    }

    /** 同一申请重复接受不重复建关系、不重复产生结果消息。 */
    @Test
    void acceptedFriendRequest_isIdempotentAndOnlyPushesAfterCommit() {
        ImMessage request = friendRequest(PEER_USER_ID, ME_USER_ID);
        when(messages.selectById(MESSAGE)).thenReturn(request);
        SysUserNotification notice = linkedNotice();
        when(notificationMapper.selectById(NOTICE)).thenReturn(notice);
        SysUserVo peer = new SysUserVo();
        peer.setUserId(PEER_USER_ID);
        when(users.selectVoById(PEER_USER_ID)).thenReturn(peer);
        when(messages.update(any(ImMessage.class), any(Wrapper.class))).thenAnswer(inv -> {
            request.setContent(inv.<ImMessage>getArgument(0).getContent());
            return 1;
        });
        when(notificationMapper.updateById(any(SysUserNotification.class))).thenReturn(1);
        when(messages.insert(any(ImMessage.class))).thenAnswer(inv -> {
            inv.<ImMessage>getArgument(0).setMessageId(MESSAGE + 10);
            return 1;
        });
        Map<String, Object> input = Map.of("messageId", MESSAGE.toString(), "id", NOTICE.toString(), "accept", true);
        assertThat(controller.handleFriendRequest(input).getCode()).isEqualTo(200);
        assertThat(controller.handleFriendRequest(input).getCode()).isEqualTo(200);
        verify(friends, times(2)).insert(any(ImFriend.class));
        verify(messages, times(1)).insert(any(ImMessage.class));
        verify(push, times(1)).publishToUserAfterCommit(eq(PEER_USER_ID), any());
        verify(push, never()).publishToUser(any(), any());
    }

    /** 通知 ID 和消息 ID 都有效但关联不同申请时，必须整体拒绝。 */
    @Test
    void anotherFriendsNotice_cannotBeCombinedWithValidRequest() {
        when(messages.selectById(MESSAGE)).thenReturn(friendRequest(PEER_USER_ID, ME_USER_ID));
        SysUserNotification notice = linkedNotice();
        notice.setExtJson("{\"messageId\":\"1900000000000000099\"}");
        when(notificationMapper.selectById(NOTICE)).thenReturn(notice);
        assertThat(controller.handleFriendRequest(Map.of("messageId", MESSAGE.toString(), "id", NOTICE.toString(), "accept", true)).getCode()).isNotEqualTo(200);
        verify(messages, never()).update(any(ImMessage.class), any(Wrapper.class));
        verifyNoInteractions(friends, push);
    }

    /** 已读通知重放不会把整条旧实体写回数据库。 */
    @Test
    void alreadyReadNotification_performsNoUpdate() {
        SysUserNotification notice = linkedNotice();
        notice.setReadFlag("1");
        when(notificationMapper.selectById(NOTICE)).thenReturn(notice);
        assertThat(new SysUserNotificationServiceImpl(notificationMapper, push, cache).markRead(NOTICE)).isZero();
        verify(notificationMapper, never()).updateById(any(SysUserNotification.class));
        verify(notificationMapper, never()).update(any(), any());
    }

    /** 字符串雪花 ID 在请求绑定时保留为 Long，不经过浮点数。 */
    @Test
    void groupSend_acceptsStringSnowflakeIds() throws Exception {
        ImGroupSendBo input = JSON.readValue("{\"groupId\":\"" + MESSAGE + "\",\"mentionUserIds\":[\"" + NOTICE + "\"]}", ImGroupSendBo.class);
        assertThat(input.getGroupId()).isEqualTo(MESSAGE);
        assertThat(input.getMentionUserIds()).containsExactly(NOTICE);
    }

    /** 有效提及在历史重载后仍按当前用户高亮，损坏旧数据不阻断整个页面。 */
    @Test
    void historyMention_isComputedForCurrentUserAndToleratesOldCorruptJson() {
        ImChatSupport realSupport =
                new ImChatSupport(
                        messages,
                        members,
                        userService,
                        mock(ISysOssService.class),
                        users,
                        visibility);
        ImMessage msg = ImTestFixture.buildMessage(MESSAGE, PEER_USER_ID, null, GROUP, "@本人");
        msg.setMentionUserIds("[100,200]");
        assertThat(realSupport.buildSenderMessageRow(msg).get("mentioned")).isEqualTo(true);
        msg.setMentionUserIds("[损坏JSON");
        assertThat(realSupport.buildSenderMessageRow(msg).get("mentioned")).isEqualTo(false);
    }

    /** 系统通知单条已读和全部已读是不同路由，重复操作均返回成功。 */
    @Test
    void notificationRead_routesRemainCompatible() throws Exception {
        var api = new SysUserNotificationController(notifications);
        var mvc = MockMvcBuilders.standaloneSetup(api).build();
        mvc.perform(MockMvcRequestBuilders.put("/im/notification/read/all"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
        mvc.perform(MockMvcRequestBuilders.put("/im/notification/read/" + NOTICE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
        verify(notifications).markAllRead();
        verify(notifications).markRead(NOTICE);
    }

    /** 接收者点通知已读后，同一用户会话未读缓存同步刷新。 */
    @Test
    void notificationRead_invalidatesRecipientsConversationCache() {
        when(notificationMapper.selectById(NOTICE)).thenReturn(linkedNotice());
        when(notificationMapper.update(any(), any())).thenReturn(1);
        new SysUserNotificationServiceImpl(notificationMapper, push, cache).markRead(NOTICE);
        verify(cache).evictUser(ME_USER_ID);
    }

    /** 好友发送成功后两个通道均在实际事务提交后投递。 */
    @Test
    void friendSend_transactionCommitsBeforeRealtimeDelivery() {
        var tx = new MemoryTransactions();
        ImRealtimePush realtime = spy(new ImRealtimePush(new ImMetrics(new SimpleMeterRegistry())));
        doAnswer(inv -> {
            assertThat(tx.commits).isEqualTo(1);
            return null;
        }).when(realtime).publishToUsers(anyList(), anyString(), anyString());
        ReflectionTestUtils.setField(controller, "imRealtimePush", realtime);
        ReflectionTestUtils.setField(
                controller,
                "notificationService",
                new SysUserNotificationServiceImpl(notificationMapper, realtime, cache));
        SysUserVo peer = new SysUserVo();
        peer.setUserId(PEER_USER_ID);
        when(users.selectVoById(PEER_USER_ID)).thenReturn(peer);
        when(messages.insert(any(ImMessage.class))).thenAnswer(inv -> { inv.<ImMessage>getArgument(0).setMessageId(MESSAGE); return 1; });
        when(notificationMapper.insert(any(SysUserNotification.class)))
                .thenAnswer(
                        inv -> {
                            assertThat(tx.commits).isZero();
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isTrue();
                            SysUserNotification notice = inv.getArgument(0);
                            notice.setNotificationId(NOTICE);
                            assertThat(notice.getExtJson()).contains(MESSAGE.toString());
                            return 1;
                        });
        assertThat(transactionalController(tx).sendFriendRequest(Map.of("toUserId", PEER_USER_ID.toString())).getCode()).isEqualTo(200);
        assertThat(tx.rollbacks).isZero();
        verify(realtime, times(2)).publishToUsers(anyList(), anyString(), anyString());
    }

    /** 申请写通知失败时事务回滚，不提前发送任何消息。 */
    @Test
    void friendSend_rollbackDoesNotDeliverRealtime() {
        var tx = new MemoryTransactions();
        ImRealtimePush realtime = spy(new ImRealtimePush(new ImMetrics(new SimpleMeterRegistry())));
        ReflectionTestUtils.setField(controller, "imRealtimePush", realtime);
        ReflectionTestUtils.setField(
                controller,
                "notificationService",
                new SysUserNotificationServiceImpl(notificationMapper, realtime, cache));
        SysUserVo peer = new SysUserVo();
        peer.setUserId(PEER_USER_ID);
        when(users.selectVoById(PEER_USER_ID)).thenReturn(peer);
        when(messages.insert(any(ImMessage.class))).thenAnswer(inv -> { inv.<ImMessage>getArgument(0).setMessageId(MESSAGE); return 1; });
        when(notificationMapper.insert(any(SysUserNotification.class))).thenThrow(new IllegalStateException("通知写入失败"));
        assertThatThrownBy(() -> transactionalController(tx).sendFriendRequest(Map.of("toUserId", PEER_USER_ID.toString())))
            .isInstanceOf(IllegalStateException.class);
        assertThat(tx.rollbacks).isEqualTo(1);
        assertThat(tx.commits).isZero();
        verify(realtime, never()).publishToUsers(anyList(), anyString(), anyString());
    }

    private AppImController transactionalController(MemoryTransactions tx) {
        var proxy = new ProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return (AppImController) proxy.getProxy();
    }

    /** 只验证 Spring 事务时序，不创建真实数据库连接。 */
    static class MemoryTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        protected Object doGetTransaction() { return new Object(); }

        protected void doBegin(Object tx, TransactionDefinition definition) {}

        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    /** 创建群和加成员的两个字段名均支持字符串雪花 ID 数组，原路径保持不变。 */
    @Test
    void appGroupMembership_bindsStringArraysAndLegacyAlias() throws Exception {
        when(chat.createGroup(any(ImGroupCreateBo.class))).thenReturn(R.ok(900L));
        when(chat.addGroupMembers(anyLong(), any(ImGroupMemberAddBo.class))).thenReturn(R.ok());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String field : List.of("memberUserIds", "memberIds")) {
            String body = "{\"groupName\":\"群\",\"groupId\":\"900\",\"" + field + "\":[\"" + MESSAGE + "\",\"" + NOTICE + "\"]}";
            for (String path : List.of("/im/app/group/create", "/im/app/group/add-members")) {
                mvc.perform(
                                MockMvcRequestBuilders.post(path)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(MockMvcResultMatchers.status().isOk())
                        .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
            }
        }
        ArgumentCaptor<ImGroupCreateBo> create = ArgumentCaptor.forClass(ImGroupCreateBo.class);
        verify(chat, times(2)).createGroup(create.capture());
        create.getAllValues().forEach(input -> assertThat(input.getMemberUserIds()).containsExactly(MESSAGE, NOTICE));
        ArgumentCaptor<ImGroupMemberAddBo> add = ArgumentCaptor.forClass(ImGroupMemberAddBo.class);
        verify(chat, times(2)).addGroupMembers(eq(900L), add.capture());
        add.getAllValues().forEach(input -> assertThat(input.getMemberUserIds()).containsExactly(MESSAGE, NOTICE));
    }

    private SysUserNotification linkedNotice() {
        SysUserNotification notice = ImTestFixture.buildNotification(NOTICE, ME_USER_ID, "im_friend_request");
        notice.setRefType("friend_request");
        notice.setRefId(PEER_USER_ID.toString());
        notice.setExtJson("{\"status\":\"sent\",\"messageId\":\"" + MESSAGE + "\"}");
        return notice;
    }

    private PageQuery pageQuery() {
        return ImTestFixture.pageQuery(20, 1);
    }

    private ImMessage friendRequest(Long sender, Long receiver) {
        ImMessage msg = ImTestFixture.buildMessage(MESSAGE, sender, receiver, null, "{\"status\":\"pending\"}");
        msg.setMsgType("friend_request");
        return msg;
    }

    private void prepareSend() {
        lenient().when(groups.selectById(GROUP)).thenReturn(ImTestFixture.buildGroup(GROUP, "群", ME_USER_ID));
        lenient().when(support.isGroupMember(anyLong(), anyLong())).thenReturn(true);
        lenient().when(support.findExistingByClientMsgId(any(), any(), any(), any())).thenReturn(null);
        ImMessageTypeHandler handler = new ImMessageTypeHandler() {
            public String type() { return "text"; }
            public String normalize(String content) { return content; }
        };
        lenient().when(types.require(anyString())).thenReturn(handler);
        lenient().when(messages.insert(any(ImMessage.class))).thenAnswer(inv -> {
            ImMessage msg = inv.getArgument(0);
            msg.setMessageId(MESSAGE);
            msg.setCreateTime(new Date());
            return 1;
        });
        SysUserVo user = new SysUserVo();
        user.setUserId(ME_USER_ID);
        user.setNickName("本人");
        lenient().when(userService.selectUserById(ME_USER_ID)).thenReturn(user);
    }
}
