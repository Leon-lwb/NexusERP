package com.nexus.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.im.controller.AppImController;
import com.nexus.im.domain.*;
import com.nexus.im.mapper.*;
import com.nexus.im.push.*;
import com.nexus.im.service.*;
import com.nexus.im.service.impl.support.*;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.im.task.ImMessageClearTask;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.*;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.redisson.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证隐藏状态、消息可见范围、通知保存期以及群成员间已读隔离。 */
class ImVisibilityRegressionTest extends ImUnitTestBase {
    @Mock RedissonClient redis;
    @Mock RSetCache<Long> hiddenSet;
    @Mock ImMessageMapper messages;
    @Mock ImGroupMemberMapper members;
    @Mock ImGroupMapper groups;
    @Mock SysUserMapper users;
    @Mock ISysUserService userService;
    @Mock ISysOssService oss;
    @Mock ISysUserNotificationService notifications;
    @Mock SysUserNotificationMapper notificationMapper;
    @Mock IImChatService chat;
    @Mock ImFriendMapper friends;
    @Mock ImRealtimePush push;
    @Mock ImConversationService conversations;
    @Mock ImConversationCache cache;
    @Mock ImConversationSettingMapper settings;
    @Mock ImFavoriteMapper favorites;
    @Mock ImMetrics metrics;
    @InjectMocks AppImController app;
    ImMessageVisibility visibility;
    ImChatSupport support;
    static final Long HIDDEN = 1_900_000_000_000_000_100L;
    static final Long GROUP = 900L;

    @BeforeAll
    static void infrastructure() { ImBehaviorRegressionTest.infrastructure(); }

    @BeforeEach
    void prepareVisibility() {
        visibility = new ImMessageVisibility(redis);
        support = new ImChatSupport(messages, members, userService, oss, users, visibility);
        ReflectionTestUtils.setField(app, "visibility", visibility);
        lenient().when(redis.<Long>getSetCache(anyString())).thenReturn(hiddenSet);
        lenient().when(hiddenSet.readAll()).thenReturn(Set.of(HIDDEN));
    }

    @Test
    void hide_isTenantAndUserScopedWithIndependent180DayExpiry() {
        visibility.hide(HIDDEN);
        verify(redis).getSetCache("im:hidden:1:100");
        verify(hiddenSet).add(HIDDEN, 180, TimeUnit.DAYS);
        loginAs(PEER_USER_ID);
        visibility.hiddenIds();
        verify(redis).getSetCache("im:hidden:1:200");
        tenantHelperMock.when(com.nexus.common.tenant.helper.TenantHelper::getTenantId).thenReturn("2");
        visibility.hiddenIds();
        verify(redis).getSetCache("im:hidden:2:200");
    }

    @Test
    void redisFailure_cannotReportDeleteSuccess() {
        when(messages.selectById(HIDDEN)).thenReturn(ImTestFixture.buildMessage(HIDDEN, PEER_USER_ID, ME_USER_ID, null, "隐藏"));
        when(hiddenSet.add(HIDDEN, 180, TimeUnit.DAYS)).thenThrow(new IllegalStateException("离线"));
        assertThatThrownBy(() -> app.deleteMessage(HIDDEN)).isInstanceOf(ServiceException.class);
        verify(messages, never()).deleteById(anyLong());
    }

    @Test
    void syncAndSearch_excludeHiddenBeforeLimitAndNeverIncludeFormerGroupBySender() {
        when(members.selectList(any())).thenReturn(List.of());
        when(messages.selectList(any())).thenReturn(List.of());
        app.syncMessages(0L);
        app.searchMessages("内容", null, null, 1, 20);
        ArgumentCaptor<LambdaQueryWrapper<ImMessage>> queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messages, times(2)).selectList(queries.capture());
        for (LambdaQueryWrapper<ImMessage> query : queries.getAllValues()) {
            assertThat(query.getSqlSegment()).contains("message_id NOT IN", "group_id IS NULL");
            assertThat(query.getParamNameValuePairs().values()).contains(HIDDEN);
        }
    }

    /** 无新消息也返回全量隐藏列表，客户端先清理本地缓存再合并消息。 */
    @Test
    void syncWithoutNewMessagesReturnsCompleteHiddenSnapshot() throws Exception {
        Long other = HIDDEN + 1;
        when(hiddenSet.readAll()).thenReturn(Set.of(other, HIDDEN));
        when(members.selectList(any())).thenReturn(List.of());
        when(messages.selectList(any())).thenReturn(List.of());
        var result = app.syncMessages(HIDDEN + 10).getData();
        var json = ImBehaviorRegressionTest.JSON.valueToTree(result);
        assertThat(json.path("hiddenMessageIds").isArray()).isTrue();
        assertThat(json.path("hiddenMessageIds").size()).isEqualTo(2);
        assertThat(json.path("hiddenMessageIds").get(0).isTextual()).isTrue();
        assertThat(json.path("hiddenMessageIds").get(0).asText()).isEqualTo(HIDDEN.toString());
        assertThat(json.path("hiddenMessageIds").get(1).asText()).isEqualTo(other.toString());
        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getNewSyncToken()).isEqualTo(HIDDEN + 10);
        assertThat(result.getHasMore()).isFalse();
        verify(hiddenSet, times(1)).readAll();
    }

    @Test
    void syncWithNoHiddenMessagesReturnsEmptyArray() {
        when(hiddenSet.readAll()).thenReturn(Set.of());
        when(members.selectList(any())).thenReturn(List.of());
        when(messages.selectList(any())).thenReturn(List.of());
        var json = ImBehaviorRegressionTest.JSON.valueToTree(app.syncMessages(0L).getData());
        assertThat(json.path("hiddenMessageIds").isArray()).isTrue();
        assertThat(json.path("hiddenMessageIds").size()).isZero();
    }

    /** 隐藏全量快照独立于单批消息的 500 条上限，雪花 ID 不做浮点转换。 */
    @Test
    void syncReturnsAll10000HiddenIdsWithoutMessagePageTruncation() {
        long start = 1_900_000_000_000_000_000L;
        Set<Long> full = new HashSet<>(LongStream.range(start, start + 10_000).boxed().toList());
        when(hiddenSet.readAll()).thenReturn(full);
        when(members.selectList(any())).thenReturn(List.of());
        when(messages.selectList(any())).thenReturn(List.of());
        var json = ImBehaviorRegressionTest.JSON.valueToTree(app.syncMessages(0L).getData());
        assertThat(json.path("hiddenMessageIds").size()).isEqualTo(10_000);
        assertThat(json.path("hiddenMessageIds").get(0).asText()).isEqualTo(String.valueOf(start));
        assertThat(json.path("hiddenMessageIds").get(9_999).asText()).isEqualTo(String.valueOf(start + 9_999));
        ArgumentCaptor<LambdaQueryWrapper<ImMessage>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messages).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("message_id NOT IN", "limit 500");
        assertThat(query.getValue().getParamNameValuePairs().values()).containsAll(full);
        verify(hiddenSet, times(1)).readAll();
    }

    /** 过滤条件和返回列表必须来自同一次读取，避免同步中途隐藏状态变化导致复活消息。 */
    @Test
    void syncFilterAndResponseUseSameHiddenSnapshot() {
        when(hiddenSet.readAll()).thenReturn(Set.of(HIDDEN), Set.of());
        when(members.selectList(any())).thenReturn(List.of());
        when(messages.selectList(any())).thenReturn(List.of(ImTestFixture.buildMessage(HIDDEN + 1, PEER_USER_ID, ME_USER_ID, null, "可见")));
        var result = app.syncMessages(0L).getData();
        var json = ImBehaviorRegressionTest.JSON.valueToTree(result);
        assertThat(json.path("hiddenMessageIds").size()).isEqualTo(1);
        assertThat(json.path("hiddenMessageIds").get(0).asText()).isEqualTo(HIDDEN.toString());
        assertThat(result.getMessages()).extracting(ImMessage::getMessageId).containsExactly(HIDDEN + 1);
        ArgumentCaptor<LambdaQueryWrapper<ImMessage>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messages).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("message_id NOT IN");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains(HIDDEN);
        verify(hiddenSet, times(1)).readAll();
    }

    @Test
    void syncHiddenSnapshotsRemainScopedToCurrentUserAndTenant() {
        RSetCache<Long> otherUser = mock(RSetCache.class);
        RSetCache<Long> otherTenant = mock(RSetCache.class);
        when(redis.<Long>getSetCache("im:hidden:1:200")).thenReturn(otherUser);
        when(redis.<Long>getSetCache("im:hidden:2:200")).thenReturn(otherTenant);
        when(otherUser.readAll()).thenReturn(Set.of(HIDDEN + 1));
        when(otherTenant.readAll()).thenReturn(Set.of(HIDDEN + 2));
        when(members.selectList(any())).thenReturn(List.of());
        when(messages.selectList(any())).thenReturn(List.of());
        var mine = ImBehaviorRegressionTest.JSON.valueToTree(app.syncMessages(0L).getData());
        assertThat(mine.path("hiddenMessageIds").size()).isEqualTo(1);
        assertThat(mine.path("hiddenMessageIds").get(0).asText()).isEqualTo(HIDDEN.toString());
        loginAs(PEER_USER_ID);
        var peer = ImBehaviorRegressionTest.JSON.valueToTree(app.syncMessages(0L).getData());
        assertThat(peer.path("hiddenMessageIds").size()).isEqualTo(1);
        assertThat(peer.path("hiddenMessageIds").get(0).asText()).isEqualTo(String.valueOf(HIDDEN + 1));
        tenantHelperMock.when(com.nexus.common.tenant.helper.TenantHelper::getTenantId).thenReturn("2");
        var tenant = ImBehaviorRegressionTest.JSON.valueToTree(app.syncMessages(0L).getData());
        assertThat(tenant.path("hiddenMessageIds").size()).isEqualTo(1);
        assertThat(tenant.path("hiddenMessageIds").get(0).asText()).isEqualTo(String.valueOf(HIDDEN + 2));
    }

    /** 超过传输上限的异常旧集合拒绝同步，不能静默截断后让消息重新出现。 */
    @Test
    void oversizedHiddenSnapshotFailsBeforeMessageQuery() {
        when(hiddenSet.readAll()).thenReturn(new HashSet<>(LongStream.range(1, 10_002).boxed().toList()));
        lenient().when(members.selectList(any())).thenReturn(List.of());
        lenient().when(messages.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> app.syncMessages(0L)).isInstanceOf(ServiceException.class);
        verify(messages, never()).selectList(any());
    }

    @Test
    void privateHistory_excludesHiddenBeforePagination() {
        var privateChat = new ImPrivateChatService(messages, userService, notifications, push,
            mock(com.nexus.im.msgtype.ImMessageTypeRegistry.class), support, cache, metrics, notificationMapper);
        var peer = new com.nexus.system.domain.vo.SysUserVo();
        peer.setUserId(PEER_USER_ID);
        when(userService.selectUserById(PEER_USER_ID)).thenReturn(peer);
        Page<ImMessage> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(messages.selectPage(any(), any())).thenReturn(page);
        privateChat.privateHistory(PEER_USER_ID, pageQuery());
        ArgumentCaptor<LambdaQueryWrapper<ImMessage>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messages).selectPage(any(), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("message_id NOT IN");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains(HIDDEN);
    }

    @Test
    void cachedConversation_cannotRestoreHiddenLastMessage() {
        when(members.selectList(any())).thenReturn(List.of());
        when(messages.selectLatestVisiblePrivatePerPeer(eq("1"), eq(ME_USER_ID), eq(Set.of(HIDDEN)))).thenReturn(List.of());
        new ImConversationService(messages, groups, members, support, cache, settings).listConversations();
        verify(cache, never()).get();
        verify(messages).selectLatestVisiblePrivatePerPeer("1", ME_USER_ID, Set.of(HIDDEN));
        verify(messages, never()).selectLatestPrivatePerPeer(any());
    }

    @Test
    void groupRead_oneMemberCannotClearOthersNotifications() {
        when(hiddenSet.readAll()).thenReturn(Set.of());
        when(groups.selectById(GROUP)).thenReturn(ImTestFixture.buildGroup(GROUP, "群", ME_USER_ID));
        when(members.selectCount(any())).thenReturn(1L);
        ImMessage message = ImTestFixture.buildMessage(10L, 300L, null, GROUP, "各自未读");
        SysUserNotification mine = ImTestFixture.buildNotification(20L, ME_USER_ID, "im_group");
        mine.setRefId("10");
        SysUserNotification others = ImTestFixture.buildNotification(21L, PEER_USER_ID, "im_group");
        others.setRefId("10");
        when(notificationMapper.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUserNotification> query = inv.getArgument(0);
            query.getSqlSegment();
            assertThat(query.getParamNameValuePairs().values()).contains(ME_USER_ID);
            return "0".equals(mine.getReadFlag()) ? List.of(mine) : List.of();
        });
        when(messages.selectList(any())).thenReturn(List.of(message));
        when(notificationMapper.update(any(), any())).thenAnswer(inv -> { mine.setReadFlag("1"); return 1; });
        var groupChat = new ImGroupChatService(messages, groups, members, userService, notifications, push,
            mock(com.nexus.im.msgtype.ImMessageTypeRegistry.class), support, cache, metrics, notificationMapper);
        assertThat(groupChat.markGroupRead(GROUP)).isEqualTo(1);
        assertThat(groupChat.markGroupRead(GROUP)).isZero();
        assertThat(others.getReadFlag()).isEqualTo("0");
        assertThat(message.getIsRead()).isEqualTo("0");
        verify(messages, never()).update(any(), any());
        verify(push, times(1)).publishToUserAfterCommit(eq(300L), any());
    }

    @Test
    void customQuery_filtersHiddenBeforeMaxAndUnreadIsPerRecipient() throws Exception {
        var method = ImMessageMapper.class.getMethod("selectLatestVisiblePrivatePerPeer", String.class, Long.class, Collection.class);
        String sql = boundSql(method.getAnnotation(Select.class), Map.of("tenantId", "1", "me", ME_USER_ID, "hiddenIds", Set.of(HIDDEN)));
        assertThat(sql.indexOf("message_id NOT IN")).isLessThan(sql.indexOf("GROUP BY"));
        assertThat(sql).contains("tenant_id = ?");
        method = ImMessageMapper.class.getMethod("selectGroupUnreadCounts", String.class, Long.class, Collection.class, Collection.class);
        sql = boundSql(method.getAnnotation(Select.class), Map.of("tenantId", "1", "me", ME_USER_ID, "groupIds", List.of(GROUP), "hiddenIds", Set.of(HIDDEN)));
        assertThat(sql).contains("n.user_id = ?", "n.read_flag = '0'", "n.tenant_id = m.tenant_id", "m.message_id NOT IN");
        assertThat(sql).doesNotContain("m.is_read");
    }

    @Test
    void messageCleanup_retainsGroupNotificationReadStateFor180Days() {
        when(messages.delete(any())).thenReturn(0);
        when(notificationMapper.delete(any())).thenReturn(0);
        tenantHelperMock.when(() -> com.nexus.common.tenant.helper.TenantHelper.ignore(any(java.util.function.Supplier.class)))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        new ImMessageClearTask(messages, notificationMapper).clearExpiredMessages();
        ArgumentCaptor<LambdaQueryWrapper<SysUserNotification>> queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(notificationMapper, atLeastOnce()).delete(queries.capture());
        assertThat(queries.getAllValues()).allSatisfy(query -> {
            assertThat(query.getSqlSegment()).contains("category");
            assertThat(query.getParamNameValuePairs().values()).contains("im_group");
        });
    }

    /** 即使命中旧缓存，退出或解散的群也不能返回聊天摘要。 */
    @Test
    void cachedConversation_rechecksGroupMembership() {
        when(hiddenSet.readAll()).thenReturn(Set.of());
        var old = new com.nexus.im.domain.vo.ImConversationVo();
        old.setChatType("GROUP");
        old.setTargetId(GROUP.toString());
        old.setLastContent("不应继续展示的群消息");
        when(cache.get()).thenReturn(List.of(old));
        when(members.selectList(any())).thenReturn(List.of());
        assertThat(new ImConversationService(messages, groups, members, support, cache, settings).listConversations()).isEmpty();
    }

    /** 过长的客户端标识在查询前拒绝，避免落到 varchar(64) 触发数据库错误。 */
    @Test
    void oversizedClientId_isRejectedBeforeDatabaseQuery() {
        assertThatThrownBy(() -> support.findExistingByClientMsgId("x".repeat(65), ME_USER_ID, PEER_USER_ID, null))
            .isInstanceOf(ServiceException.class).hasMessageContaining("消息标识");
        verify(messages, never()).selectOne(any());
    }

    /** 增量同步不能透传旧共享已读标志，必须使用当前接收者状态。 */
    @Test
    void syncGroupReadFlag_comesFromCurrentUsersNotification() {
        when(hiddenSet.readAll()).thenReturn(Set.of());
        when(members.selectList(any())).thenReturn(List.of(ImTestFixture.buildGroupMember(GROUP, ME_USER_ID)));
        when(groups.selectByIds(anyCollection())).thenReturn(List.of(ImTestFixture.buildGroup(GROUP, "群", PEER_USER_ID)));
        ImMessage old = ImTestFixture.buildMessage(10L, PEER_USER_ID, null, GROUP, "仍未读取");
        old.setIsRead("1");
        when(messages.selectList(any())).thenReturn(List.of(old));
        SysUserNotification mine = ImTestFixture.buildNotification(20L, ME_USER_ID, "im_group");
        mine.setRefId("10");
        when(notificationMapper.selectList(any())).thenReturn(List.of(mine));
        assertThat(app.syncMessages(0L).getData().getMessages().get(0).getIsRead()).isEqualTo("0");
    }

    /** 关闭多租户时 TenantHelper 返回空，SQL 不能用 tenant_id = NULL 清空结果。 */
    @Test
    void singleTenantQueries_doNotCompareTenantWithNull() throws Exception {
        var params = new HashMap<String, Object>();
        params.put("tenantId", null);
        params.put("me", ME_USER_ID);
        params.put("groupIds", List.of(GROUP));
        params.put("hiddenIds", Set.of(HIDDEN));
        var method = ImMessageMapper.class.getMethod("selectGroupUnreadCounts", String.class, Long.class, Collection.class, Collection.class);
        assertThat(boundSql(method.getAnnotation(Select.class), params)).doesNotContain("m.tenant_id = ?", "n.tenant_id = ?");
        method = ImMessageMapper.class.getMethod("selectLatestVisiblePrivatePerPeer", String.class, Long.class, Collection.class);
        assertThat(boundSql(method.getAnnotation(Select.class), params)).doesNotContain("tenant_id = ?");
    }

    private String boundSql(Select select, Map<String, Object> params) {
        return new XMLLanguageDriver().createSqlSource(new org.apache.ibatis.session.Configuration(),
            String.join(" ", select.value()), Map.class).getBoundSql(params).getSql();
    }

    private com.nexus.common.mybatis.core.page.PageQuery pageQuery() {
        return ImTestFixture.pageQuery(20, 1);
    }


}
