package com.nexus.im.service.impl.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.im.domain.ImConversationSetting;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.mapper.ImConversationSettingMapper;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-5: P2-1 listConversations 批量化回归测试。
 * <p>
 * 守护 P2-1 阶段从 ImChatServiceImpl.listConversations 重写为 6 步批量化的核心改造：
 * <ul>
 *   <li>核心守护 1：群聊未读数 selectMaps 用驼峰 key {@code groupId} 读取（非 {@code group_id}）
 *       <br>— 原 bug：{@code mapUnderscoreToCamelCase=true} 把列名 {@code group_id} 转 map key {@code groupId}，
 *       原 row.get("group_id") 返回 null → 未读数始终为 0（commit 19b23fd1 修复）</li>
 *   <li>核心守护 2：私聊未读数 selectMaps 用驼峰 key {@code fromUserId} 读取（非 {@code from_user_id}）</li>
 *   <li>核心守护 3：群最后消息用 selectMaps + MAX(message_id) + 二次 IN 查询（避免一次性加载所有消息到内存）</li>
 *   <li>核心守护 4：批量加载用户与头像（{@code batchLoadUsers}/{@code batchResolveAvatarUrls}）</li>
 *   <li>核心守护 5：无群组时走快速路径，不触发后续群聊 SQL</li>
 * </ul>
 * <p>
 * 任意重构若回退到 N+1 查询或下划线 key 读取，对应用例立即失败。
 */
class ImConversationServiceTest extends ImUnitTestBase {

    @Mock private ImMessageMapper imMessageMapper;
    @Mock private ImGroupMapper imGroupMapper;
    @Mock private ImGroupMemberMapper imGroupMemberMapper;
    @Mock private ImChatSupport support;
    @Mock private ImConversationCache conversationCache;
    @Mock private ImConversationSettingMapper conversationSettingMapper;

    @InjectMocks
    private ImConversationService imConversationService;

    private static final Long GROUP_1 = 11L;
    private static final Long GROUP_2 = 12L;
    private static final Long MEMBER_201 = 201L;
    private static final Long MEMBER_202 = 202L;
    private static final Long PEER_301 = 301L;
    private static final Long AVATAR_OSS_1 = 901L;

    /**
     * 预热 MyBatis-Plus Lambda 缓存。
     * <p>单元测试无 Spring 容器，{@code LambdaQueryWrapper.select(ImMessage::getMessageId)} 等
     * 方法引用解析依赖 {@link TableInfoHelper} 的实体表信息缓存，无预热会抛
     * "MybatisPlus can not find lambda cache for this entity"。
     * <p>这里手动注册 ImMessage/ImGroup/ImGroupMember 三张实体的表信息。
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ImMessage.class);
        TableInfoHelper.initTableInfo(assistant, ImGroup.class);
        TableInfoHelper.initTableInfo(assistant, ImGroupMember.class);
        // P6-B1: 预热 ImConversationSetting 表信息（applyConversationSettings 用 LambdaQueryWrapper）
        TableInfoHelper.initTableInfo(assistant, ImConversationSetting.class);
    }

    @BeforeEach
    void setUpConversation() {
        // 公共桩：support.batchLoadUsers 返回空 Map（不抛异常）
        lenient().when(support.batchLoadUsers(anyCollection())).thenReturn(new java.util.LinkedHashMap<>());
        // 公共桩：support.batchResolveAvatarUrls 返回空 Map
        lenient().when(support.batchResolveAvatarUrls(anyCollection())).thenReturn(new java.util.LinkedHashMap<>());
        // P4-2: 公共桩 — 会话缓存未命中（返回 null 让测试走 DB 查询路径）
        lenient().when(conversationCache.get()).thenReturn(null);
        // P6-B1: 公共桩 — 会话配置（置顶/免打扰）默认返回空列表，避免 applyConversationSettings NPE
        lenient().when(conversationSettingMapper.selectList(any())).thenReturn(new ArrayList<>());
    }

    // ==================== 快速路径守护 ====================

    /**
     * P2-1: 无群组 + 无私聊 → 返回空列表，不触发群聊批量 SQL。
     */
    @Test
    void listConversations_noGroupsNoPrivate_returnsEmpty() {
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        // P4-10 B6: 私聊路径改用 selectLatestPrivatePerPeer（Mockito 默认返回空列表，无需显式 stub）

        List<ImConversationVo> result = imConversationService.listConversations();

        assertThat(result).isEmpty();
        // 守护：无群成员 → 不查 group 信息
        verify(imGroupMapper, never()).selectByIds(anyCollection());
        // 守护：无私聊消息 → 不查 user
        verify(support, never()).batchLoadUsers(anyCollection());
    }

    /**
     * P2-1: 无群组 + 有私聊 → 仅走私聊路径，不触发群聊 SQL。
     */
    @Test
    void listConversations_noGroupsOnlyPrivate_skipsGroupPath() {
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());
        // P4-10 B6: 私聊最近消息改用 selectLatestPrivatePerPeer（DB 侧去重后返回）
        ImMessage privateMsg = ImTestFixture.buildMessage(1L, PEER_301, ME_USER_ID, null, "hi");
        when(imMessageMapper.selectLatestPrivatePerPeer(any())).thenReturn(List.of(privateMsg));

        List<ImConversationVo> result = imConversationService.listConversations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChatType()).isEqualTo("PRIVATE");
        // 守护：无群 → 不查群信息
        verify(imGroupMapper, never()).selectByIds(anyCollection());
    }

    // ==================== 群聊批量化守护 ====================

    /**
     * P2-1: 有群组时走完整 6 步批量化流程。
     * <p>守护 selectByIds 用群 ID 集合一次性查询（而非 N 次 selectById）。
     * <p>注：ImGroupMapper extends BaseMapperPlus<ImGroup, ImGroup>（实体类与 VO 同类型），
     * 不能用 selectVoByIds（mapstruct-plus 找不到同类型转换器），改用 selectByIds 直接返回实体列表。
     */
    @Test
    void listConversations_withGroups_usesBatchSelectVoByIds() {
        // 用户参与 2 个群
        ImGroupMember m1 = ImTestFixture.buildGroupMember(GROUP_1, ME_USER_ID);
        ImGroupMember m2 = ImTestFixture.buildGroupMember(GROUP_2, ME_USER_ID);
        when(imGroupMemberMapper.selectList(any())).thenReturn(List.of(m1, m2));

        // 群组信息批量查询
        ImGroup g1 = ImTestFixture.buildGroup(GROUP_1, "group-1", ME_USER_ID);
        ImGroup g2 = ImTestFixture.buildGroup(GROUP_2, "group-2", ME_USER_ID);
        when(imGroupMapper.selectByIds(anyCollection())).thenReturn(List.of(g1, g2));

        // 群最后消息：selectMaps 返回 maxId
        Map<String, Object> maxRow1 = new HashMap<>();
        maxRow1.put("maxId", 1001L);
        maxRow1.put("group_id", GROUP_1);
        when(imMessageMapper.selectMaps(any())).thenReturn(List.of(maxRow1));

        // 二次 IN 查询返回具体消息
        ImMessage lastMsg = ImTestFixture.buildMessage(1001L, MEMBER_201, null, GROUP_1, "last");
        when(imMessageMapper.selectList(any())).thenReturn(List.of(lastMsg));

        List<ImConversationVo> result = imConversationService.listConversations();

        // 守护：selectByIds 调用 1 次（批量查群信息）
        verify(imGroupMapper, times(1)).selectByIds(anyCollection());
        // 守护：群成员列表查询只调 2 次（用户参与的群 + 群成员详情），不是 N×2 次
        verify(imGroupMemberMapper, times(2)).selectList(any());
    }

    /**
     * P2-1 核心守护: 群未读数 selectMaps 返回的 Map 必须用驼峰 key {@code groupId} 读取。
     * <p>若回退到下划线 {@code group_id} 读取，未读数会始终为 0（原 bug，commit 19b23fd1 修复）。
     * <p>测试策略：mock selectMaps 返回驼峰 key 的 Map，断言 unreadCount 非零。
     */
    @Test
    void listConversations_groupUnread_usesCamelCaseKey() {
        // 1 群
        ImGroupMember m1 = ImTestFixture.buildGroupMember(GROUP_1, ME_USER_ID);
        when(imGroupMemberMapper.selectList(any())).thenReturn(List.of(m1));
        ImGroup g1 = ImTestFixture.buildGroup(GROUP_1, "group-1", ME_USER_ID);
        when(imGroupMapper.selectByIds(anyCollection())).thenReturn(List.of(g1));

        // 第 1 次 selectMaps：返回 maxId（群最后消息查询）
        // 第 2 次 selectMaps：返回未读数（驼峰 key）
        Map<String, Object> maxRow = new HashMap<>();
        maxRow.put("maxId", 1001L);

        Map<String, Object> unreadRow = new HashMap<>();
        // 关键：模拟 mapUnderscoreToCamelCase=true 的行为，map key 为驼峰
        unreadRow.put("groupId", GROUP_1);
        unreadRow.put("cnt", 5L);

        when(imMessageMapper.selectMaps(any())).thenReturn(List.of(maxRow), List.of(unreadRow));

        // 二次 IN 查询返回最后消息
        ImMessage lastMsg = ImTestFixture.buildMessage(1001L, MEMBER_201, null, GROUP_1, "last");
        when(imMessageMapper.selectList(any())).thenReturn(List.of(lastMsg));

        List<ImConversationVo> result = imConversationService.listConversations();

        // 守护：群未读数 = 5（若读取下划线 key 会得到 null → 0L）
        ImConversationVo groupVo = result.stream()
            .filter(v -> "GROUP".equals(v.getChatType()))
            .findFirst()
            .orElseThrow();
        assertThat(groupVo.getUnreadCount()).isEqualTo(5L);
    }

    // ==================== 私聊未读数驼峰 key 守护 ====================

    /**
     * P2-1 核心守护: 私聊未读数 selectMaps 返回的 Map 必须用驼峰 key {@code fromUserId} 读取。
     * <p>若回退到下划线 {@code from_user_id} 读取，未读数会始终为 0。
     */
    @Test
    void listConversations_privateUnread_usesCamelCaseKey() {
        // 无群组
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());

        // P4-10 B6: 私聊最近消息改用 selectLatestPrivatePerPeer
        ImMessage privateMsg = ImTestFixture.buildMessage(1L, PEER_301, ME_USER_ID, null, "hi");
        when(imMessageMapper.selectLatestPrivatePerPeer(any())).thenReturn(List.of(privateMsg));

        // selectMaps：返回私聊未读数（驼峰 key）
        Map<String, Object> unreadRow = new HashMap<>();
        // 关键：mapUnderscoreToCamelCase=true 把 from_user_id 转为 fromUserId
        unreadRow.put("fromUserId", PEER_301);
        unreadRow.put("cnt", 3L);
        when(imMessageMapper.selectMaps(any())).thenReturn(List.of(unreadRow));

        List<ImConversationVo> result = imConversationService.listConversations();

        // 守护：私聊未读数 = 3（若读取下划线 key 会得到 null → 0L）
        ImConversationVo privateVo = result.stream()
            .filter(v -> "PRIVATE".equals(v.getChatType()))
            .findFirst()
            .orElseThrow();
        assertThat(privateVo.getUnreadCount()).isEqualTo(3L);
    }

    // ==================== 批量加载用户守护 ====================

    /**
     * P2-1: 群聊会话组装时调用 {@code support.batchLoadUsers} 与 {@code support.batchResolveAvatarUrls}
     * 各 1 次（而非 N 次循环），消除 N+1。
     * <p>测试 mock 私聊消息返回空，使私聊路径不触发 batchLoadUsers，从而精确断言群聊路径的调用次数。
     */
    @Test
    void listConversations_groupPath_callsBatchLoadUsersOnce() {
        // 1 群，群内有 2 成员
        ImGroupMember m1 = ImTestFixture.buildGroupMember(GROUP_1, ME_USER_ID);
        ImGroupMember m2 = ImTestFixture.buildGroupMember(GROUP_1, MEMBER_201);
        // 第 1 次 selectList（用户参与的群）→ 返回 m1
        // 第 2 次 selectList（群成员详情）→ 返回 m1, m2
        when(imGroupMemberMapper.selectList(any()))
            .thenReturn(List.of(m1))
            .thenReturn(List.of(m1, m2));

        ImGroup g1 = ImTestFixture.buildGroup(GROUP_1, "group-1", ME_USER_ID);
        when(imGroupMapper.selectByIds(anyCollection())).thenReturn(List.of(g1));

        Map<String, Object> maxRow = new HashMap<>();
        maxRow.put("maxId", 1001L);
        when(imMessageMapper.selectMaps(any())).thenReturn(List.of(maxRow));

        ImMessage lastMsg = ImTestFixture.buildMessage(1001L, MEMBER_201, null, GROUP_1, "last");
        // 群聊路径调 selectList 取最后消息详情；私聊路径 B6 后改用 selectLatestPrivatePerPeer（默认返回空）
        when(imMessageMapper.selectList(any()))
            .thenReturn(List.of(lastMsg));

        imConversationService.listConversations();

        // 守护：群聊路径 batchLoadUsers 仅调用 1 次（私聊路径因无消息未触发）
        verify(support, times(1)).batchLoadUsers(anyCollection());
        // 守护：batchResolveAvatarUrls 仅调用 1 次（群聊路径）
        verify(support, times(1)).batchResolveAvatarUrls(anyCollection());
    }

    // ==================== 私聊头像批量加载守护 ====================

    /**
     * P2-1: 私聊路径也走 batchLoadUsers + batchResolveAvatarUrls，且 senderName/avatar 字段正确填充。
     */
    @Test
    void listConversations_privatePath_callsBatchLoadUsersOnceAndFillsAvatar() {
        // 无群组
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());

        // P4-10 B6: 私聊最近消息改用 selectLatestPrivatePerPeer
        ImMessage privateMsg = ImTestFixture.buildMessage(1L, PEER_301, ME_USER_ID, null, "hi");
        when(imMessageMapper.selectLatestPrivatePerPeer(any())).thenReturn(List.of(privateMsg));

        // selectMaps 返回空（无私聊未读）
        when(imMessageMapper.selectMaps(any())).thenReturn(new ArrayList<>());

        // mock batchLoadUsers 返回 PEER_301 用户
        Map<Long, SysUserVo> userMap = new LinkedHashMap<>();
        SysUserVo peer = new SysUserVo();
        peer.setUserId(PEER_301);
        peer.setUserName("peer_name");
        peer.setNickName("PeerNick");
        peer.setAvatar(AVATAR_OSS_1);
        userMap.put(PEER_301, peer);
        when(support.batchLoadUsers(anyCollection())).thenReturn(userMap);

        // mock batchResolveAvatarUrls 返回 OSS URL
        Map<Long, String> avatarUrlMap = new LinkedHashMap<>();
        avatarUrlMap.put(AVATAR_OSS_1, "https://oss.example.com/avatar-301.png");
        when(support.batchResolveAvatarUrls(anyCollection())).thenReturn(avatarUrlMap);

        List<ImConversationVo> result = imConversationService.listConversations();

        // 守护：私聊路径 batchLoadUsers 调用 1 次
        verify(support, times(1)).batchLoadUsers(anyCollection());
        verify(support, times(1)).batchResolveAvatarUrls(anyCollection());

        ImConversationVo privateVo = result.get(0);
        assertThat(privateVo.getTitle()).isEqualTo("PeerNick");  // 优先 nickName
        assertThat(privateVo.getAvatar()).isEqualTo("https://oss.example.com/avatar-301.png");
    }

    // ==================== 排序守护 ====================

    /**
     * P2-1: 会话列表按 lastTime 倒序（最新在前）。
     */
    @Test
    void listConversations_sortsByLastTimeDesc() {
        // 无群组
        when(imGroupMemberMapper.selectList(any())).thenReturn(new ArrayList<>());

        // 2 条私聊消息（来自不同 peer），createTime 不同
        Date older = new Date(1700000000000L);
        Date newer = new Date(1800000000000L);
        ImMessage msgOld = ImTestFixture.buildMessage(1L, PEER_301, ME_USER_ID, null, "old");
        msgOld.setCreateTime(older);
        ImMessage msgNew = ImTestFixture.buildMessage(2L, MEMBER_201, ME_USER_ID, null, "new");
        msgNew.setCreateTime(newer);

        // P4-10 B6: 私聊最近消息改用 selectLatestPrivatePerPeer（DB 侧已按 create_time DESC 返回）
        when(imMessageMapper.selectLatestPrivatePerPeer(any())).thenReturn(List.of(msgNew, msgOld));
        when(imMessageMapper.selectMaps(any())).thenReturn(new ArrayList<>());

        List<ImConversationVo> result = imConversationService.listConversations();

        assertThat(result).hasSize(2);
        // 守护：newer 在前
        assertThat(result.get(0).getLastTime()).isEqualTo(newer);
        assertThat(result.get(1).getLastTime()).isEqualTo(older);
    }
}
