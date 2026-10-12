package com.nexus.im;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.im.controller.AppImController;
import com.nexus.im.domain.*;
import com.nexus.im.mapper.*;
import com.nexus.im.push.ImMetrics;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.impl.support.*;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 好友申请撤销的身份、关联、状态和实际事务提交时序回归，不连接外部服务。 */
class ImFriendRequestCancellationTest extends ImUnitTestBase {
    @Mock ImMessageMapper messages;
    @Mock SysUserNotificationMapper notices;
    @Mock ImFriendMapper friends;
    @Mock SysUserMapper users;
    @Mock ImRealtimePush push;
    @Mock ImConversationService conversations;
    @Mock ImGroupMapper groups;
    @Mock ImGroupMemberMapper members;
    @Mock ImConversationSettingMapper settings;
    @InjectMocks AppImController app;

    static final Long MESSAGE = ImBehaviorRegressionTest.MESSAGE;
    static final Long NOTICE = ImBehaviorRegressionTest.NOTICE;

    @BeforeAll
    static void infrastructure() { ImBehaviorRegressionTest.infrastructure(); }

    @Test
    void receiverCannotCancelSendersRequest() {
        arrange(request(PEER_USER_ID, ME_USER_ID), notice(PEER_USER_ID, ME_USER_ID, "sent"));
        assertThat(app.revokeRequest(NOTICE).getCode()).isNotEqualTo(200);
        assertNoMutation();
    }

    @Test
    void unrelatedUserCannotCancelRequest() {
        arrange(request(PEER_USER_ID, OTHER_TENANT_USER_ID), notice(PEER_USER_ID, OTHER_TENANT_USER_ID, "sent"));
        assertThat(app.revokeRequest(NOTICE).getCode()).isNotEqualTo(200);
        assertNoMutation();
    }

    @Test
    void missingNoticeHasNoSideEffects() {
        assertThat(app.revokeRequest(NOTICE).getCode()).isNotEqualTo(200);
        assertNoMutation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "refType", "refId", "fromUserId", "senderId", "sender", "receiver", "group", "type", "revoked", "messageId"})
    void invalidAssociationIsRejectedBeforeAnyMutation(String mismatch) {
        ImMessage message = request(ME_USER_ID, PEER_USER_ID);
        SysUserNotification notification = notice(ME_USER_ID, PEER_USER_ID, "sent");
        switch (mismatch) {
            case "category" -> notification.setCategory("system");
            case "refType" -> notification.setRefType("message");
            case "refId" -> notification.setRefId(OTHER_TENANT_USER_ID.toString());
            case "fromUserId" -> notification.setExtJson(ext(OTHER_TENANT_USER_ID, "sent"));
            case "senderId" -> notification.setExtJson(ext(ME_USER_ID, "sent").replace("\"senderId\":\"100\"", "\"senderId\":\"300\""));
            case "sender" -> message.setFromUserId(OTHER_TENANT_USER_ID);
            case "receiver" -> message.setToUserId(OTHER_TENANT_USER_ID);
            case "group" -> message.setGroupId(900L);
            case "type" -> message.setMsgType("text");
            case "revoked" -> message.setRevoked("1");
            case "messageId" -> message.setMessageId(MESSAGE + 1);
            default -> throw new AssertionError(mismatch);
        }
        arrange(message, notification);
        assertThat(app.revokeRequest(NOTICE).getCode()).isNotEqualTo(200);
        assertNoMutation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accepted", "rejected", "cancelled"})
    void processedNoticeCannotBeCancelled(String status) {
        arrange(request(ME_USER_ID, PEER_USER_ID), notice(ME_USER_ID, PEER_USER_ID, status));
        assertThat(app.revokeRequest(NOTICE).getCode()).isNotEqualTo(200);
        assertNoMutation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accepted", "rejected", "cancelled"})
    void processedMessageCannotBeCancelled(String status) {
        ImMessage message = request(ME_USER_ID, PEER_USER_ID);
        message.setContent("{\"status\":\"" + status + "\"}");
        arrange(message, notice(ME_USER_ID, PEER_USER_ID, "sent"));
        assertThat(app.revokeRequest(NOTICE).getCode()).isNotEqualTo(200);
        assertNoMutation();
    }

    @Test
    void crossTenantReceiverCannotBeCancelled() {
        arrange(request(ME_USER_ID, PEER_USER_ID), notice(ME_USER_ID, PEER_USER_ID, "sent"));
        enableTenant();
        SysUserVo receiver = new SysUserVo();
        receiver.setUserId(PEER_USER_ID);
        receiver.setTenantId(OTHER_TENANT_ID);
        lenient().when(users.selectVoById(PEER_USER_ID)).thenReturn(receiver);
        assertThatThrownBy(() -> app.revokeRequest(NOTICE)).isInstanceOf(ServiceException.class).hasMessageContaining("跨租户");
        assertNoMutation();
    }

    @Test
    void senderCancelsBothRecordsAndPublishesOnlyAfterCommit() throws Exception {
        ImMessage message = request(ME_USER_ID, PEER_USER_ID);
        SysUserNotification notification = notice(ME_USER_ID, PEER_USER_ID, "sent");
        arrange(message, notification);
        var tx = new ImBehaviorRegressionTest.MemoryTransactions();
        ImRealtimePush realtime = transactionalInfrastructure(tx);
        lenient().when(messages.update(any(ImMessage.class), any(Wrapper.class))).thenAnswer(inv -> {
            assertThat(tx.commits).isZero();
            verify(realtime, never()).publishToUsers(anyList(), anyString(), anyString());
            return 1;
        });
        lenient().when(notices.updateById(any(SysUserNotification.class))).thenAnswer(inv -> {
            assertThat(tx.commits).isZero();
            verify(realtime, never()).publishToUsers(anyList(), anyString(), anyString());
            return 1;
        });
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.deleteObject(anyString())).thenAnswer(inv -> {
                assertThat(tx.commits).isEqualTo(1);
                return true;
            });
            assertThat(transactionalApp(tx).revokeRequest(NOTICE).getCode()).isEqualTo(200);
            assertThat(tx.commits).isEqualTo(1);
            assertThat(tx.rollbacks).isZero();
            redis.verify(() -> RedisUtils.deleteObject("im:conv:1:100"));
            redis.verify(() -> RedisUtils.deleteObject("im:conv:1:200"));
        }
        ArgumentCaptor<ImMessage> messagePatch = ArgumentCaptor.forClass(ImMessage.class);
        ArgumentCaptor<LambdaUpdateWrapper<ImMessage>> condition = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messages).update(messagePatch.capture(), condition.capture());
        assertThat(ImBehaviorRegressionTest.JSON.readTree(messagePatch.getValue().getContent()).path("status").asText()).isEqualTo("cancelled");
        assertThat(messagePatch.getValue().getIsRead()).isEqualTo("1");
        assertThat(condition.getValue().getSqlSegment()).contains("message_id", "from_user_id", "to_user_id", "content");
        assertThat(condition.getValue().getParamNameValuePairs().values()).contains(message.getContent());
        ArgumentCaptor<SysUserNotification> noticePatch = ArgumentCaptor.forClass(SysUserNotification.class);
        verify(notices).updateById(noticePatch.capture());
        assertThat(noticePatch.getValue().getNotificationId()).isEqualTo(NOTICE);
        assertThat(noticePatch.getValue().getReadFlag()).isEqualTo("1");
        var updatedExt = ImBehaviorRegressionTest.JSON.readTree(noticePatch.getValue().getExtJson());
        assertThat(updatedExt.path("status").asText()).isEqualTo("cancelled");
        assertThat(updatedExt.path("messageId").asText()).isEqualTo(MESSAGE.toString());
        ArgumentCaptor<List<Long>> recipients = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        verify(realtime).publishToUsers(recipients.capture(), envelope.capture(), anyString());
        assertThat(recipients.getValue()).containsExactlyInAnyOrder(ME_USER_ID, PEER_USER_ID);
        var event = ImBehaviorRegressionTest.JSON.readTree(envelope.getValue());
        assertThat(event.path("type").asText()).isEqualTo("IM_MESSAGE");
        var payload = event.path("payload");
        assertThat(payload.path("action").asText()).isEqualTo("friend_request_update");
        assertThat(payload.path("status").asText()).isEqualTo("cancelled");
        assertThat(payload.path("chatType").asText()).isEqualTo("PRIVATE");
        assertThat(payload.path("msgType").asText()).isEqualTo("friend_request");
        assertThat(payload.path("messageId").isTextual()).isTrue();
        assertThat(payload.path("messageId").asText()).isEqualTo(MESSAGE.toString());
        assertThat(payload.path("notificationId").asText()).isEqualTo(NOTICE.toString());
        assertThat(payload.path("fromUserId").asText()).isEqualTo(ME_USER_ID.toString());
        assertThat(payload.path("toUserId").asText()).isEqualTo(PEER_USER_ID.toString());
        assertThat(ImBehaviorRegressionTest.JSON.readTree(payload.path("content").asText()).path("status").asText()).isEqualTo("cancelled");
        verify(messages, never()).deleteById(anyLong());
        verify(notices, never()).deleteById(anyLong());
        verify(messages, never()).insert(any(ImMessage.class));
        verifyNoInteractions(friends);
    }

    @Test
    void numericLegacySenderIdKeepsCancellationCompatible() {
        SysUserNotification notification = notice(ME_USER_ID, PEER_USER_ID, "sent");
        notification.setExtJson(ext(ME_USER_ID, "sent").replace("\"fromUserId\":\"100\"", "\"fromUserId\":100"));
        arrange(request(ME_USER_ID, PEER_USER_ID), notification);
        lenient().when(messages.update(any(ImMessage.class), any(Wrapper.class))).thenReturn(1);
        lenient().when(notices.updateById(any(SysUserNotification.class))).thenReturn(1);
        assertThat(app.revokeRequest(NOTICE).getCode()).isEqualTo(200);
        verify(conversations).evictConversationCacheForUsers(List.of(ME_USER_ID, PEER_USER_ID));
    }

    @Test
    void concurrentProcessingWinsWithoutCancellationSideEffects() {
        arrange(request(ME_USER_ID, PEER_USER_ID), notice(ME_USER_ID, PEER_USER_ID, "sent"));
        assertThat(app.revokeRequest(NOTICE).getMsg()).contains("状态已变更");
        verify(messages).update(any(ImMessage.class), any(Wrapper.class));
        verify(notices, never()).updateById(any(SysUserNotification.class));
        verifyNoInteractions(friends, conversations, push);
    }

    @Test
    void missingNoticeUpdateRollsBackWithoutDeliveryOrCacheEviction() {
        arrange(request(ME_USER_ID, PEER_USER_ID), notice(ME_USER_ID, PEER_USER_ID, "sent"));
        lenient().when(messages.update(any(ImMessage.class), any(Wrapper.class))).thenReturn(1);
        var tx = new ImBehaviorRegressionTest.MemoryTransactions();
        ImRealtimePush realtime = transactionalInfrastructure(tx);
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            assertThatThrownBy(() -> transactionalApp(tx).revokeRequest(NOTICE)).isInstanceOf(ServiceException.class);
            assertThat(tx.commits).isZero();
            assertThat(tx.rollbacks).isEqualTo(1);
            verify(realtime, never()).publishToUsers(anyList(), anyString(), anyString());
            redis.verifyNoInteractions();
        }
    }

    private ImRealtimePush transactionalInfrastructure(ImBehaviorRegressionTest.MemoryTransactions tx) {
        ImMetrics metrics = new ImMetrics(new SimpleMeterRegistry());
        ImRealtimePush realtime = spy(new ImRealtimePush(metrics));
        lenient().doAnswer(inv -> {
            assertThat(tx.commits).isEqualTo(1);
            return null;
        }).when(realtime).publishToUsers(anyList(), anyString(), anyString());
        ReflectionTestUtils.setField(app, "imRealtimePush", realtime);
        ReflectionTestUtils.setField(app, "conversationService", new ImConversationService(messages, groups, members,
            mock(ImChatSupport.class), new ImConversationCache(metrics), settings));
        return realtime;
    }

    private AppImController transactionalApp(ImBehaviorRegressionTest.MemoryTransactions tx) {
        var proxy = new org.springframework.aop.framework.ProxyFactory(app);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return (AppImController) proxy.getProxy();
    }

    private void arrange(ImMessage message, SysUserNotification notification) {
        lenient().when(notices.selectById(NOTICE)).thenReturn(notification);
        lenient().when(messages.selectById(MESSAGE)).thenReturn(message);
        SysUserVo receiver = new SysUserVo();
        receiver.setUserId(notification.getUserId());
        receiver.setTenantId(TENANT_ID);
        lenient().when(users.selectVoById(notification.getUserId())).thenReturn(receiver);
    }

    private ImMessage request(Long sender, Long receiver) {
        ImMessage message = ImTestFixture.buildMessage(MESSAGE, sender, receiver, null, "{\"status\":\"pending\",\"requestMessage\":\"请添加好友\"}");
        message.setMsgType("friend_request");
        return message;
    }

    private SysUserNotification notice(Long sender, Long receiver, String status) {
        SysUserNotification notification = ImTestFixture.buildNotification(NOTICE, receiver, "im_friend_request");
        notification.setRefType("friend_request");
        notification.setRefId(sender.toString());
        notification.setExtJson(ext(sender, status));
        return notification;
    }

    private String ext(Long sender, String status) {
        return "{\"status\":\"" + status + "\",\"fromUserId\":\"" + sender + "\",\"senderId\":\"" + sender + "\",\"messageId\":\"" + MESSAGE + "\"}";
    }

    private void assertNoMutation() {
        verify(messages, never()).update(any(ImMessage.class), any(Wrapper.class));
        verify(messages, never()).updateById(any(ImMessage.class));
        verify(messages, never()).insert(any(ImMessage.class));
        verify(messages, never()).deleteById(anyLong());
        verify(notices, never()).updateById(any(SysUserNotification.class));
        verify(notices, never()).update(any(SysUserNotification.class), any(Wrapper.class));
        verify(notices, never()).deleteById(anyLong());
        verifyNoInteractions(friends, conversations, push);
    }
}
