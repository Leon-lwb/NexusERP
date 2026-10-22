package com.nexus.im.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.im.constants.ImPayloadConstants;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.domain.bo.ImGroupCreateBo;
import com.nexus.im.domain.bo.ImGroupMemberAddBo;
import com.nexus.im.domain.vo.ImGroupMemberVo;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.mapper.SysUserNotificationMapper;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * P3-4 + P3-6: P2-4 TOCTOU + P2-5 批量插入 + P2-6 listGroupMembers 批量化回归测试。
 * <p>
 * 守护 P2 阶段三项关键改造：
 * <ul>
 *   <li>P2-4 {@code addGroupMembers} 移除 selectCount 预检查，依赖唯一索引 + catch {@link DuplicateKeyException} 实现幂等</li>
 *   <li>P2-5 {@code createGroup} 用 {@code insertBatch} 替代 for 循环逐条 insert</li>
 *   <li>P2-6 {@code listGroupMembers} 批量加载用户与头像，消除 N+1（50 成员 SQL 数从 100+ 降到 4）</li>
 * </ul>
 * <p>
 * 任意重构若：
 * <ul>
 *   <li>在 {@code addGroupMembers} 前加回 selectCount 预检查 → P3-4 用例 3 立即失败</li>
 *   <li>把 {@code createGroup} 的 insertBatch 改回 for 循环逐条 insert → P3-4 用例 4/5 立即失败</li>
 *   <li>把 {@code listGroupMembers} 的 batchLoadUsers 改回循环 selectUserById → P3-6 用例立即失败</li>
 * </ul>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>使用纯 Mockito 单元测试，直接 new ImGroupChatService 实例，避免启动 Spring 容器</li>
 *   <li>{@code support.normalizeUserIds}/{@code requireSameTenantUser}/{@code normalizeGroupName} 通过 lenient 桩透传，
 *       保持业务逻辑焦点在 TOCTOU / 批量插入 / 批量加载上</li>
 *   <li>{@code imGroupMapper.insert} 用 {@code doAnswer} 模拟 MyBatis-Plus 自动回填 groupId</li>
 *   <li>{@code @BeforeAll} 预热 MyBatis-Plus Lambda 缓存，让 {@code listGroupMembers} 内部的
 *       {@code LambdaQueryWrapper.eq(ImGroupMember::getGroupId, ...)} 能正常解析</li>
 * </ul>
 */
class ImGroupChatServiceTest extends ImUnitTestBase {

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
    private SysUserNotificationMapper notificationMapper;

    @InjectMocks
    private ImGroupChatService imGroupChatService;

    private static final Long GROUP_ID = 1L;
    private static final Long MEMBER_101 = 101L;
    private static final Long MEMBER_102 = 102L;
    private static final Long MEMBER_103 = 103L;

    /**
     * P4-3: mockStatic ImEnvelopeHelper 让 markGroupRead 推送路径能在纯 Mockito 环境下工作。
     * <p>JsonUtils.OBJECT_MAPPER 静态初始化依赖 Spring 容器（SpringUtils.getBean），
     * 无容器环境下抛 ExceptionInInitializerError，且 mockStatic(JsonUtils.class) 无法
     * instrument static final 字段初始化（类加载即失败）。
     * <p>改用 mockStatic(ImEnvelopeHelper.class)：ImEnvelopeHelper 无静态字段，类加载安全；
     * 用 thenAnswer 让 imMessageRead 用真实 ObjectMapper 序列化 payload，便于断言 JSON 内容。
     */
    private MockedStatic<ImEnvelopeHelper> envelopeHelperMock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 预热 MyBatis-Plus Lambda 缓存，让 {@code listGroupMembers} 内部
     * 的 {@code LambdaQueryWrapper.eq(ImGroupMember::getGroupId, ...)} 能正常解析。
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ImGroupMember.class);
        TableInfoHelper.initTableInfo(assistant, ImGroup.class);
        TableInfoHelper.initTableInfo(assistant, ImMessage.class);
    }

    @BeforeEach
    void setUpGroupChat() {
        // 公共桩：群存在、状态正常、当前用户是群主
        ImGroup group = ImTestFixture.buildGroup(GROUP_ID, "test-group", ME_USER_ID);
        lenient().when(imGroupMapper.selectById(GROUP_ID)).thenReturn(group);

        // 公共桩：support.normalizeUserIds 直接透传有效用户 ID（与生产实现一致）
        lenient().when(support.normalizeUserIds(any())).thenAnswer(invocation -> {
            List<Long> input = invocation.getArgument(0);
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            if (input != null) {
                for (Long uid : input) {
                    if (uid != null && uid > 0) {
                        ids.add(uid);
                    }
                }
            }
            return ids;
        });

        // 公共桩：support.requireSameTenantUser 不抛异常，返回最小 SysUserVo
        lenient().when(support.requireSameTenantUser(any())).thenAnswer(invocation -> {
            SysUserVo u = new SysUserVo();
            u.setUserId(invocation.getArgument(0));
            u.setTenantId(TENANT_ID);
            return u;
        });

        // 公共桩：normalizeGroupName 透传
        lenient().when(support.normalizeGroupName(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // P4-3: mockStatic ImEnvelopeHelper — 用真实 ObjectMapper 序列化 payload，
        // 避开 JsonUtils.OBJECT_MAPPER 静态初始化对 Spring 容器的依赖
        envelopeHelperMock = Mockito.mockStatic(ImEnvelopeHelper.class);
        envelopeHelperMock.when(() -> ImEnvelopeHelper.imMessageRead(any())).thenAnswer(inv -> {
            Map<String, Object> payload = inv.getArgument(0);
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("v", ImPayloadConstants.ENVELOPE_VERSION);
            root.put("type", ImPayloadConstants.TYPE_IM_MESSAGE_READ);
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
    void tearDownGroupChat() {
        if (envelopeHelperMock != null) {
            envelopeHelperMock.close();
        }
    }

    // ==================== P2-4 addGroupMembers TOCTOU 幂等 ====================

    /**
     * P2-4: 部分成员已存在（唯一索引冲突）时，catch DuplicateKeyException 跳过，
     * 返回"新增 N 条，已存在 M 条"提示，不抛异常。
     * <p>
     * 守护点：
     * <ul>
     *   <li>所有成员都尝试 insert（不预过滤）</li>
     *   <li>DuplicateKeyException 被吞掉并计入 existingCount（而非向上抛）</li>
     *   <li>返回消息文案正确反映 N/M 计数</li>
     * </ul>
     */
    @Test
    void addGroupMembers_partialDuplicate_skipsAndReturnsCount() {
        ImGroupMemberAddBo bo = new ImGroupMemberAddBo();
        bo.setMemberUserIds(List.of(MEMBER_101, MEMBER_102, MEMBER_103));

        // 101 和 103 触发唯一索引冲突，102 成功
        // 注：MyBatis-Plus BaseMapper 新版有 insert(T) 和 insert(Collection<T>) 两个重载，
        // argThat 返回 null 时编译器无法决议 → 用 ArgumentMatchers.<ImGroupMember>argThat 显式指定类型见证
        when(imGroupMemberMapper.insert(ArgumentMatchers.<ImGroupMember>argThat(m -> m != null && MEMBER_101.equals(m.getUserId()))))
            .thenThrow(new DuplicateKeyException("uk_group_user"));
        when(imGroupMemberMapper.insert(ArgumentMatchers.<ImGroupMember>argThat(m -> m != null && MEMBER_102.equals(m.getUserId()))))
            .thenReturn(1);
        when(imGroupMemberMapper.insert(ArgumentMatchers.<ImGroupMember>argThat(m -> m != null && MEMBER_103.equals(m.getUserId()))))
            .thenThrow(new DuplicateKeyException("uk_group_user"));

        R<Void> result = imGroupChatService.addGroupMembers(GROUP_ID, bo);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        assertThat(result.getMsg()).contains("新增 1 条");
        assertThat(result.getMsg()).contains("已存在 2 条");
        // 守护：3 个成员都尝试 insert（不预过滤），1 成功 + 2 冲突
        verify(imGroupMemberMapper, times(3)).insert(any(ImGroupMember.class));
    }

    /**
     * P2-4: 全新成员场景，全部 insert 成功。
     */
    @Test
    void addGroupMembers_allNew_insertsAll() {
        ImGroupMemberAddBo bo = new ImGroupMemberAddBo();
        bo.setMemberUserIds(List.of(MEMBER_101, MEMBER_102));
        when(imGroupMemberMapper.insert(any(ImGroupMember.class))).thenReturn(1);

        R<Void> result = imGroupChatService.addGroupMembers(GROUP_ID, bo);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        assertThat(result.getMsg()).contains("新增 2 条");
        verify(imGroupMemberMapper, times(2)).insert(any(ImGroupMember.class));
    }

    /**
     * P2-4 TOCTOU 核心守护：addGroupMembers 不应调用 selectCount 做预检查。
     * <p>
     * 若有人在 addGroupMembers 前加回 selectCount（"先查再插"模式），此用例立即失败。
     * selectCount 预检查就是 P2-4 修复的核心——它是 TOCTOU 竞态的根源：
     * <ul>
     *   <li>线程 A selectCount=0 → 准备 insert</li>
     *   <li>线程 B selectCount=0 → 准备 insert</li>
     *   <li>线程 A insert 成功</li>
     *   <li>线程 B insert 触发唯一索引冲突（如果没 catch 则抛异常）</li>
     * </ul>
     * 移除 selectCount + catch DuplicateKeyException 是无竞态的"先试再判"模式。
     */
    @Test
    void addGroupMembers_neverCallsSelectCount() {
        ImGroupMemberAddBo bo = new ImGroupMemberAddBo();
        bo.setMemberUserIds(List.of(MEMBER_101));
        when(imGroupMemberMapper.insert(any(ImGroupMember.class))).thenReturn(1);

        imGroupChatService.addGroupMembers(GROUP_ID, bo);

        // 核心守护：selectCount 永不被调用
        verify(imGroupMemberMapper, never()).selectCount(any());
    }

    // ==================== P2-5 createGroup 批量插入 ====================

    /**
     * P2-5: 多成员创建群组时，应调用 1 次 insertBatch，而非 N 次 insert。
     * <p>
     * 守护点：
     * <ul>
     *   <li>insertBatch 调用 1 次（不论成员数量）</li>
     *   <li>insert（单条）永不被调用</li>
     * </ul>
     * 若有人把 insertBatch 改回 for 循环 insert，此用例立即失败。
     */
    @Test
    void createGroup_multipleMembers_callsInsertBatchOnce() {
        ImGroupCreateBo bo = new ImGroupCreateBo();
        bo.setGroupName("test");
        bo.setMemberUserIds(List.of(MEMBER_101, MEMBER_102, MEMBER_103, 104L, 105L));

        // 模拟 MyBatis-Plus insert 后自动回填 groupId
        when(imGroupMapper.insert(any(ImGroup.class))).thenAnswer(invocation -> {
            ImGroup g = invocation.getArgument(0);
            g.setGroupId(GROUP_ID);
            return 1;
        });

        imGroupChatService.createGroup(bo);

        // 守护：调用 1 次 insertBatch（而非 N 次 insert）
        verify(imGroupMemberMapper, times(1)).insertBatch(anyList());
        verify(imGroupMemberMapper, never()).insert(any(ImGroupMember.class));
    }

    /**
     * P2-5: 10 个成员 + 群主共 11 人，应一次性 insertBatch 全部成员。
     * <p>
     * 守护点：insertBatch 接收的集合包含全部 11 个成员（10 个 BO 传入 + 群主自动加入）。
     * 若有人误删 {@code ids.add(ownerId)} 或拆分批量调用，此用例立即失败。
     */
    @Test
    void createGroup_10members_insertBatchCalledWithAllMembers() {
        ImGroupCreateBo bo = new ImGroupCreateBo();
        bo.setGroupName("test");
        // 10 个成员，不含群主（ME_USER_ID=100）
        bo.setMemberUserIds(LongStream.rangeClosed(MEMBER_101, 110L).boxed().toList());

        when(imGroupMapper.insert(any(ImGroup.class))).thenAnswer(invocation -> {
            ImGroup g = invocation.getArgument(0);
            g.setGroupId(GROUP_ID);
            return 1;
        });

        imGroupChatService.createGroup(bo);

        // 捕获 insertBatch 参数：应包含 11 个成员（10 个 BO 传入 + 群主）
        ArgumentCaptor<List<ImGroupMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(imGroupMemberMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(11);
    }

    // ==================== P2-6 listGroupMembers 批量化 ====================

    /**
     * P2-6: 50 个群成员场景下，{@code support.batchLoadUsers} 仅调用 1 次（而非 N 次 selectUserById）。
     * <p>核心守护：50 成员 SQL 数从 100+ 降到 4（selectById + selectList + batchLoadUsers + batchResolveAvatarUrls）。
     * <p>任意重构若把 {@code batchLoadUsers} 改回 for 循环 {@code sysUserService.selectUserById}，
     * 此用例立即失败。
     */
    @Test
    void listGroupMembers_50members_callsBatchLoadUsersOnce() {
        // 群存在，当前用户是群成员
        lenient().when(support.isGroupMember(GROUP_ID, ME_USER_ID)).thenReturn(true);

        // 50 个群成员（含群主 ME_USER_ID 和 49 个其他用户）
        List<ImGroupMember> members =
                LongStream.rangeClosed(MEMBER_101, MEMBER_101 + 48)
                        .mapToObj(uid -> ImTestFixture.buildGroupMember(GROUP_ID, uid))
                        .collect(Collectors.toList());
        members.add(ImTestFixture.buildGroupMember(GROUP_ID, ME_USER_ID));
        when(imGroupMemberMapper.selectList(any())).thenReturn(members);

        // batchLoadUsers 返回空 Map（不引发后续 avatar 解析）
        when(support.batchLoadUsers(anyCollection())).thenReturn(new LinkedHashMap<>());

        List<ImGroupMemberVo> result = imGroupChatService.listGroupMembers(GROUP_ID);

        assertThat(result).hasSize(50);
        // 守护：batchLoadUsers 仅调用 1 次（替代 50 次 selectUserById）
        verify(support, times(1)).batchLoadUsers(anyCollection());
    }

    /**
     * P2-6: 群成员携带 avatar 时，{@code support.batchResolveAvatarUrls} 仅调用 1 次（而非 N 次 resolveAvatarUrl）。
     */
    @Test
    void listGroupMembers_membersWithAvatar_callsBatchResolveAvatarUrlsOnce() {
        lenient().when(support.isGroupMember(GROUP_ID, ME_USER_ID)).thenReturn(true);

        // 3 个群成员（含群主）
        List<ImGroupMember> members = List.of(
            ImTestFixture.buildGroupMember(GROUP_ID, ME_USER_ID),
            ImTestFixture.buildGroupMember(GROUP_ID, MEMBER_101),
            ImTestFixture.buildGroupMember(GROUP_ID, MEMBER_102)
        );
        when(imGroupMemberMapper.selectList(any())).thenReturn(members);

        // batchLoadUsers 返回 2 个带 avatar 的用户
        Map<Long, SysUserVo> userMap = new LinkedHashMap<>();
        SysUserVo u1 = new SysUserVo();
        u1.setUserId(MEMBER_101);
        u1.setUserName("m101");
        u1.setAvatar(901L);
        SysUserVo u2 = new SysUserVo();
        u2.setUserId(MEMBER_102);
        u2.setUserName("m102");
        u2.setAvatar(902L);
        userMap.put(MEMBER_101, u1);
        userMap.put(MEMBER_102, u2);
        // 群主 ME_USER_ID 不在 userMap 中（模拟用户查不到的情况，不引发 avatar 解析）
        when(support.batchLoadUsers(anyCollection())).thenReturn(userMap);

        // batchResolveAvatarUrls 返回 URL 映射
        Map<Long, String> avatarUrlMap = new LinkedHashMap<>();
        avatarUrlMap.put(901L, "https://oss.example.com/901.png");
        avatarUrlMap.put(902L, "https://oss.example.com/902.png");
        when(support.batchResolveAvatarUrls(anyCollection())).thenReturn(avatarUrlMap);

        List<ImGroupMemberVo> result = imGroupChatService.listGroupMembers(GROUP_ID);

        // 守护：batchResolveAvatarUrls 仅调用 1 次（替代 2 次 resolveAvatarUrl）
        verify(support, times(1)).batchResolveAvatarUrls(anyCollection());
        // 守护：成员 101 的 avatar 正确解析
        ImGroupMemberVo vo101 = result.stream().filter(v -> MEMBER_101.equals(v.getUserId())).findFirst().orElseThrow();
        assertThat(vo101.getAvatar()).isEqualTo("https://oss.example.com/901.png");
    }

    /**
     * P2-6: 群主标记正确（owner=true 仅对群主，其他成员 false）。
     */
    @Test
    void listGroupMembers_marksOwnerCorrectly() {
        lenient().when(support.isGroupMember(GROUP_ID, ME_USER_ID)).thenReturn(true);

        List<ImGroupMember> members = List.of(
            ImTestFixture.buildGroupMember(GROUP_ID, ME_USER_ID),
            ImTestFixture.buildGroupMember(GROUP_ID, MEMBER_101)
        );
        when(imGroupMemberMapper.selectList(any())).thenReturn(members);
        when(support.batchLoadUsers(anyCollection())).thenReturn(new LinkedHashMap<>());

        List<ImGroupMemberVo> result = imGroupChatService.listGroupMembers(GROUP_ID);

        // 守护：群主 owner=true
        ImGroupMemberVo ownerVo = result.stream().filter(v -> ME_USER_ID.equals(v.getUserId())).findFirst().orElseThrow();
        assertThat(ownerVo.getOwner()).isTrue();
        // 守护：非群主 owner=false
        ImGroupMemberVo memberVo = result.stream().filter(v -> MEMBER_101.equals(v.getUserId())).findFirst().orElseThrow();
        assertThat(memberVo.getOwner()).isFalse();
    }

    /** 群已读仅更改当前用户的通知，仍按发送者合并回执。 */
    @Test
    void markGroupRead_onlyUpdatesPersonalNotifications() {
        prepareUnread(15, 3);
        assertThat(imGroupChatService.markGroupRead(GROUP_ID)).isEqualTo(15);
        verify(imMessageMapper, never()).update(any(), any());
        verify(imRealtimePush, times(3)).publishToUserAfterCommit(any(), any());
    }

    /** 水位只由当前用户读取前的通知对应消息产生。 */
    @Test
    void markGroupRead_payloadContainsPersonalWatermark() {
        prepareUnread(5, 1);
        imGroupChatService.markGroupRead(GROUP_ID);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(imRealtimePush).publishToUserAfterCommit(eq(MEMBER_101), json.capture());
        assertThat(json.getValue()).contains("\"watermark\"", "\"maxMessageId\":1005", "\"readBy\":" + ME_USER_ID);
    }

    /** 大量未读通知仍只按发送者发送回执，不逐消息推送。 */
    @Test
    void markGroupRead_1000PersonalUnread_pushesOnly3Times() {
        prepareUnread(1000, 3);
        imGroupChatService.markGroupRead(GROUP_ID);
        verify(imMessageMapper, never()).update(any(), any());
        verify(imRealtimePush, times(3)).publishToUserAfterCommit(any(), any());
    }

    private void prepareUnread(int count, int senderCount) {
        when(support.isGroupMember(GROUP_ID, ME_USER_ID)).thenReturn(true);
        List<SysUserNotification> notices = new ArrayList<>();
        List<ImMessage> messages = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            SysUserNotification notice =
                    ImTestFixture.buildNotification(2000L + i, ME_USER_ID, "im_group");
            notice.setRefId(String.valueOf(1000L + i));
            notices.add(notice);
            messages.add(ImTestFixture.buildMessage(1000L + i, MEMBER_101 + (i - 1) % senderCount, null, GROUP_ID, "消息"));
        }
        when(notificationMapper.selectList(any())).thenReturn(notices);
        when(imMessageMapper.selectList(any())).thenReturn(messages);
        when(notificationMapper.update(any(), any())).thenReturn(count);
    }

    /**
     * P5-4 HIGH 越权守护：非群成员调用 markGroupRead 应抛 ServiceException，且不触碰 DB 写操作。
     * <p>
     * 漏洞描述：原 markGroupRead 仅校验群组存在与状态，未校验调用者是否为群成员，
     * 非成员可调用 PUT /im/chat/group/read?groupId=xxx 将他人未读数清零。
     * <p>
     * 守护点：
     * <ul>
     *   <li>非成员调用 → 抛 ServiceException("无权操作此群")</li>
     *   <li>不执行 update（避免污染真实接收者的 is_read 状态）</li>
     *   <li>不推送 watermark（避免误发已读回执）</li>
     * </ul>
     */
    @Test
    void markGroupRead_nonMember_throwsServiceException() {
        // 当前用户不是群成员
        when(support.isGroupMember(GROUP_ID, ME_USER_ID)).thenReturn(false);

        ServiceException ex = Assertions.assertThrows(ServiceException.class,
            () -> imGroupChatService.markGroupRead(GROUP_ID));
        assertThat(ex.getMessage()).isEqualTo("无权操作此群");

        // 守护：非成员场景下不得执行任何 DB 写或推送
        verify(imMessageMapper, never()).update(any(), any());
        verify(imMessageMapper, never()).selectMaps(any());
        verify(imRealtimePush, never()).publishToUserAfterCommit(any(), any());
    }

    /**
     * 构造 watermark 行（模拟 selectMaps 返回，key 为驼峰式）。
     */
    private Map<String, Object> buildWatermarkRow(Long fromUserId, Long maxMessageId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fromUserId", fromUserId);
        row.put("maxMessageId", maxMessageId);
        return row;
    }
}
