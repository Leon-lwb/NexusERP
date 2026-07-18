package com.nexus.im.service.impl.support;

import com.nexus.common.core.domain.R;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.bo.ImGroupCreateBo;
import com.nexus.im.domain.bo.ImGroupMemberAddBo;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentMatchers;

/**
 * P3-4: P2-4 TOCTOU + P2-5 批量插入幂等回归测试。
 * <p>
 * 守护 P2 阶段两项关键改造：
 * <ul>
 *   <li>P2-4 {@code addGroupMembers} 移除 selectCount 预检查，依赖唯一索引 + catch {@link DuplicateKeyException} 实现幂等</li>
 *   <li>P2-5 {@code createGroup} 用 {@code insertBatch} 替代 for 循环逐条 insert</li>
 * </ul>
 * <p>
 * 任意重构若：
 * <ul>
 *   <li>在 {@code addGroupMembers} 前加回 selectCount 预检查 → 用例 3 立即失败</li>
 *   <li>把 {@code createGroup} 的 insertBatch 改回 for 循环逐条 insert → 用例 4/5 立即失败</li>
 * </ul>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>使用纯 Mockito 单元测试，直接 new ImGroupChatService 实例，避免启动 Spring 容器</li>
 *   <li>{@code support.normalizeUserIds}/{@code requireSameTenantUser}/{@code normalizeGroupName} 通过 lenient 桩透传，
 *       保持业务逻辑焦点在 TOCTOU 与批量插入上</li>
 *   <li>{@code imGroupMapper.insert} 用 {@code doAnswer} 模拟 MyBatis-Plus 自动回填 groupId</li>
 * </ul>
 */
class ImGroupChatServiceTest extends ImUnitTestBase {

    @Mock private ImMessageMapper imMessageMapper;
    @Mock private ImGroupMapper imGroupMapper;
    @Mock private ImGroupMemberMapper imGroupMemberMapper;
    @Mock private ISysUserService sysUserService;
    @Mock private ISysUserNotificationService sysUserNotificationService;
    @Mock private ImRealtimePush imRealtimePush;
    @Mock private ImMessageTypeRegistry messageTypeRegistry;
    @Mock private ImChatSupport support;

    @InjectMocks
    private ImGroupChatService imGroupChatService;

    private static final Long GROUP_ID = 1L;
    private static final Long MEMBER_101 = 101L;
    private static final Long MEMBER_102 = 102L;
    private static final Long MEMBER_103 = 103L;

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
}
