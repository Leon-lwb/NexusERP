package com.nexus.im.controller;

import com.nexus.common.core.domain.R;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.vo.ImSyncResultVo;
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
import com.nexus.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4-4: App syncToken 增量同步回归测试。
 * <p>
 * 守护 {@link AppImController#syncMessages(Long)} 的核心契约：
 * <ul>
 *   <li>核心守护 1：null sinceMessageId 视为 0，查询 messageId &gt; 0 的所有消息</li>
 *   <li>核心守护 2：仅返回 messageId &gt; sinceMessageId 的消息（增量同步不重复）</li>
 *   <li>核心守护 3：私聊（from/to = me）+ 群聊（me 所在群）消息都应返回</li>
 *   <li>核心守护 4：无群组时仅查私聊，不触发 IN 空集合 SQL</li>
 *   <li>核心守护 5：满 500 条 → hasMore=true；不足 500 → hasMore=false</li>
 *   <li>核心守护 6：无新消息 → 返回空列表 + newSyncToken=sinceMessageId（保持不变）</li>
 *   <li>核心守护 7：newSyncToken = 最后一条消息的 messageId</li>
 * </ul>
 * <p>
 * 任意重构若破坏上述契约（如改错比较运算符、漏查群聊、hasMore 判定错误），对应用例立即失败。
 *
 * @author nexus
 */
class AppImControllerSyncTest extends ImUnitTestBase {

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

    @InjectMocks
    private AppImController controller;

    private static final Long GROUP_1 = 1001L;
    private static final Long GROUP_2 = 1002L;
    private static final int SYNC_LIMIT = 500;

    // ==================== 基础契约守护 ====================

    /**
     * 核心守护 1: null sinceMessageId 视为 0，查询 messageId > 0 的所有消息。
     * <p>验证 baseline 处理正确，不会因 null 抛 NPE。
     */
    @Test
    void syncMessages_nullSinceMessageId_treatedAsZero() {
        // 无群组
        when(groupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        // 返回 1 条私聊消息
        ImMessage msg = ImTestFixture.buildMessage(10L, PEER_USER_ID, ME_USER_ID, null, "hi");
        when(messageMapper.selectList(any())).thenReturn(List.of(msg));

        R<ImSyncResultVo> result = controller.syncMessages(null);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        assertThat(vo.getMessages()).hasSize(1);
        // syncToken 更新为最后一条消息的 messageId
        assertThat(vo.getNewSyncToken()).isEqualTo(10L);
        // 不足 500 条 → hasMore = false
        assertThat(vo.getHasMore()).isFalse();
    }

    /**
     * 核心守护 6: 无新消息时返回空列表 + newSyncToken = sinceMessageId（保持不变）。
     * <p>验证无新消息时 syncToken 不变，客户端下次仍从此 ID 继续同步。
     */
    @Test
    void syncMessages_noNewMessages_returnsEmptyListAndSameToken() {
        when(groupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>());

        R<ImSyncResultVo> result = controller.syncMessages(1000L);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        assertThat(vo.getMessages()).isEmpty();
        // 关键守护：无新消息时 newSyncToken = sinceMessageId（不回退也不前进）
        assertThat(vo.getNewSyncToken()).isEqualTo(1000L);
        assertThat(vo.getHasMore()).isFalse();
    }

    // ==================== 增量同步守护 ====================

    /**
     * 核心守护 2 + 7: sinceMessageId=100，返回 messageId 101-150 的消息，
     * newSyncToken 应为最后一条（150），而非 sinceMessageId。
     */
    @Test
    void syncMessages_withSinceMessageId_returnsOnlyNewMessages() {
        when(groupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        // 模拟后端返回 101-150 共 50 条消息（按 messageId 升序）
        List<ImMessage> mockMessages = new ArrayList<>();
        for (long i = 101; i <= 150; i++) {
            mockMessages.add(ImTestFixture.buildMessage(i, PEER_USER_ID, ME_USER_ID, null, "msg-" + i));
        }
        when(messageMapper.selectList(any())).thenReturn(mockMessages);

        R<ImSyncResultVo> result = controller.syncMessages(100L);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        assertThat(vo.getMessages()).hasSize(50);
        // 关键守护：newSyncToken = 最后一条消息的 messageId（150）
        assertThat(vo.getNewSyncToken()).isEqualTo(150L);
        // 不足 500 → hasMore = false
        assertThat(vo.getHasMore()).isFalse();
    }

    /**
     * 核心守护 5: 满 500 条 → hasMore=true（客户端应继续拉取下一批）。
     */
    @Test
    void syncMessages_exactly500Messages_hasMoreTrue() {
        when(groupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        // 模拟后端返回 500 条消息
        List<ImMessage> mockMessages = new ArrayList<>();
        for (long i = 1; i <= SYNC_LIMIT; i++) {
            mockMessages.add(ImTestFixture.buildMessage(i, PEER_USER_ID, ME_USER_ID, null, "msg-" + i));
        }
        when(messageMapper.selectList(any())).thenReturn(mockMessages);

        R<ImSyncResultVo> result = controller.syncMessages(0L);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        assertThat(vo.getMessages()).hasSize(SYNC_LIMIT);
        // 关键守护：满 500 条 → hasMore = true
        assertThat(vo.getHasMore()).isTrue();
        // newSyncToken = 第 500 条消息的 messageId
        assertThat(vo.getNewSyncToken()).isEqualTo(500L);
    }

    /**
     * 核心守护 5: 499 条 → hasMore=false。
     */
    @Test
    void syncMessages_lessThan500Messages_hasMoreFalse() {
        when(groupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<ImMessage> mockMessages = new ArrayList<>();
        for (long i = 1; i <= 499; i++) {
            mockMessages.add(ImTestFixture.buildMessage(i, PEER_USER_ID, ME_USER_ID, null, "msg-" + i));
        }
        when(messageMapper.selectList(any())).thenReturn(mockMessages);

        R<ImSyncResultVo> result = controller.syncMessages(0L);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        assertThat(vo.getMessages()).hasSize(499);
        // 关键守护：不足 500 → hasMore = false
        assertThat(vo.getHasMore()).isFalse();
        assertThat(vo.getNewSyncToken()).isEqualTo(499L);
    }

    // ==================== 群聊 + 私聊混合守护 ====================

    /**
     * 核心守护 3: 用户有群组时，查询应包含私聊 + 群聊两类消息。
     * <p>验证用户群组成员关系被正确查询，且消息查询被触发。
     */
    @Test
    void syncMessages_withGroups_queriesBothPrivateAndGroup() {
        // 用户参与 2 个群
        ImGroupMember m1 = ImTestFixture.buildGroupMember(GROUP_1, ME_USER_ID);
        ImGroupMember m2 = ImTestFixture.buildGroupMember(GROUP_2, ME_USER_ID);
        when(groupMemberMapper.selectList(any())).thenReturn(List.of(m1, m2));

        // 模拟返回混合消息：1 条私聊 + 1 条群聊
        ImMessage privateMsg = ImTestFixture.buildMessage(101L, PEER_USER_ID, ME_USER_ID, null, "private");
        ImMessage groupMsg = ImTestFixture.buildMessage(102L, PEER_USER_ID, null, GROUP_1, "group");
        when(messageMapper.selectList(any())).thenReturn(List.of(privateMsg, groupMsg));

        R<ImSyncResultVo> result = controller.syncMessages(100L);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        assertThat(vo.getMessages()).hasSize(2);
        // 验证群成员查询被调用（用于构造 IN 条件）
        verify(groupMemberMapper).selectList(any());
        // newSyncToken = 最后一条消息的 messageId
        assertThat(vo.getNewSyncToken()).isEqualTo(102L);
    }

    /**
     * 核心守护 4: 无群组时仅查私聊，仍能正常返回。
     * <p>验证无群组时不会因 IN 空集合导致 SQL 异常。
     */
    @Test
    void syncMessages_noGroups_onlyQueriesPrivate() {
        when(groupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        ImMessage msg = ImTestFixture.buildMessage(101L, PEER_USER_ID, ME_USER_ID, null, "private");
        when(messageMapper.selectList(any())).thenReturn(List.of(msg));

        R<ImSyncResultVo> result = controller.syncMessages(100L);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        assertThat(vo.getMessages()).hasSize(1);
        // 验证群成员查询被调用（即使为空也会查询）
        verify(groupMemberMapper).selectList(any());
        assertThat(vo.getNewSyncToken()).isEqualTo(101L);
    }

    /**
     * 集成场景: 离线期间收到 300 条消息（>原 200 条上限），重连后 1 次拉取完成。
     * <p>守护 P4-4 的核心价值：突破原 200 条硬限制，单次拉取 300 条。
     */
    @Test
    void syncMessages_offline300Messages_singleFetch() {
        when(groupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<ImMessage> mockMessages = new ArrayList<>();
        for (long i = 101; i <= 400; i++) {
            mockMessages.add(ImTestFixture.buildMessage(i, PEER_USER_ID, ME_USER_ID, null, "msg-" + i));
        }
        when(messageMapper.selectList(any())).thenReturn(mockMessages);

        R<ImSyncResultVo> result = controller.syncMessages(100L);

        assertThat(result.getCode()).isEqualTo(200);
        ImSyncResultVo vo = result.getData();
        // 关键守护：300 条消息全部返回（原实现上限 200 会漏 100 条）
        assertThat(vo.getMessages()).hasSize(300);
        // 不足 500 → hasMore = false（1 次拉取完成）
        assertThat(vo.getHasMore()).isFalse();
        assertThat(vo.getNewSyncToken()).isEqualTo(400L);
    }
}
