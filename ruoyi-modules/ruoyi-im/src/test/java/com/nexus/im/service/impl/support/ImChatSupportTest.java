package com.nexus.im.service.impl.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.service.ISysUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-6: P2-2 buildSenderMessagePage 批量化回归测试。
 * <p>
 * 守护 P2-2 阶段从 ImChatServiceImpl.buildSenderMessagePage 重写为 3 步批量化：
 * <ul>
 *   <li>第 1 步：收集所有 fromUserId，1 次 batchLoadUsers（替代 N 次 sysUserService.selectUserById）</li>
 *   <li>第 2 步：收集所有 avatarId，1 次 batchResolveAvatarUrls（替代 N 次 resolveAvatarUrl）</li>
 *   <li>第 3 步：循环组装行（无 SQL）</li>
 * </ul>
 * 50 条历史消息 SQL 数从 100+ 降到 3。
 * <p>
 * 任意重构若回退到 N+1（如 for 循环内 selectUserById/resolveAvatarUrl），对应验证用例立即失败。
 * <p>
 * 注：@提及字段解析（JsonUtils.parseArray）依赖 Spring 容器，移交 P3-9 集成测试覆盖。
 */
class ImChatSupportTest extends ImUnitTestBase {

    @Mock private ImMessageMapper imMessageMapper;
    @Mock private ImGroupMemberMapper imGroupMemberMapper;
    @Mock private ISysUserService sysUserService;
    @Mock private ISysOssService sysOssService;
    @Mock private SysUserMapper sysUserMapper;

    @InjectMocks
    private ImChatSupport imChatSupport;

    private static final Long SENDER_101 = 101L;
    private static final Long SENDER_102 = 102L;
    private static final Long AVATAR_OSS_1 = 901L;
    private static final Long AVATAR_OSS_2 = 902L;

    /**
     * 预热 MyBatis-Plus Lambda 缓存，让 {@code findExistingByClientMsgId} 内部
     * 的 {@code LambdaQueryWrapper.select(ImMessage::getMessageId)} 能正常解析。
     * <p>单元测试无 Spring 容器，缓存为空会抛
     * {@code "MybatisPlus can not find lambda cache for this entity"}。
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ImMessage.class);
    }

    // 注：取消 @BeforeEach 公共 lenient 桩，避免 MockitoExtension 严格模式报 UnnecessaryStubbing。
    // 每个用例自行 mock sysUserMapper.selectVoByIds / sysOssService.listByIds 的返回值。

    // ==================== 空页守护 ====================

    /**
     * P2-2: 空分页结果 → 返回空列表，不触发 batchLoadUsers / batchResolveAvatarUrls。
     */
    @Test
    void buildSenderMessagePage_emptyPage_returnsEmpty() {
        Page<ImMessage> emptyPage = new Page<>();
        emptyPage.setRecords(new ArrayList<>());
        emptyPage.setTotal(0);

        TableDataInfo<Map<String, Object>> result = imChatSupport.buildSenderMessagePage(emptyPage);

        assertThat(result.getRows()).isNullOrEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
        // 守护：空页不应触发任何用户/头像查询
        verify(sysUserMapper, never()).selectVoByIds(anyCollection());
        verify(sysOssService, never()).listByIds(anyCollection());
    }

    // ==================== 批量化守护 ====================

    /**
     * P2-2: 多条消息 → batchLoadUsers 仅调用 1 次（而非 N 次 selectUserById）。
     * <p>核心守护：50 条消息 SQL 数从 100+ 降到 3。
     */
    @Test
    void buildSenderMessagePage_multipleRecords_callsBatchLoadUsersOnce() {
        // 50 条消息，2 个不同发送者
        List<ImMessage> records = LongStream.rangeClosed(1, 50).boxed()
            .map(i -> {
                Long sender = (i % 2 == 0) ? SENDER_101 : SENDER_102;
                return ImTestFixture.buildMessage(i, sender, ME_USER_ID, null, "msg-" + i);
            })
            .toList();
        Page<ImMessage> page = new Page<>();
        page.setRecords(records);
        page.setTotal(50);

        // mock batchLoadUsers：返回 2 个 sender
        List<SysUserVo> users = new ArrayList<>();
        SysUserVo u1 = new SysUserVo();
        u1.setUserId(SENDER_101);
        u1.setUserName("sender-101");
        users.add(u1);
        SysUserVo u2 = new SysUserVo();
        u2.setUserId(SENDER_102);
        u2.setUserName("sender-102");
        users.add(u2);
        when(sysUserMapper.selectVoByIds(anyCollection())).thenReturn(users);
        // 注：users 均无 avatar，batchResolveAvatarUrls 内部短路返回空 Map，不调 sysOssService.listByIds

        imChatSupport.buildSenderMessagePage(page);

        // 守护：batchLoadUsers 内部调 sysUserMapper.selectVoByIds 1 次（替代 N 次 selectUserById）
        verify(sysUserMapper, times(1)).selectVoByIds(anyCollection());
        // 守护：用户无 avatar 时 batchResolveAvatarUrls 短路，不触发 OSS 查询
        verify(sysOssService, never()).listByIds(anyCollection());
    }

    /**
     * P2-2: 用户有 avatar 时 → batchResolveAvatarUrls 调用 1 次，OSS 查询 1 次。
     */
    @Test
    void buildSenderMessagePage_usersWithAvatar_callsBatchResolveAvatarUrlsOnce() {
        // 10 条消息，1 个发送者带 avatar
        List<ImMessage> records = LongStream.rangeClosed(1, 10).boxed()
            .map(i -> ImTestFixture.buildMessage(i, SENDER_101, ME_USER_ID, null, "msg-" + i))
            .toList();
        Page<ImMessage> page = new Page<>();
        page.setRecords(records);
        page.setTotal(10);

        // mock batchLoadUsers：1 个 sender 带 avatar
        SysUserVo u1 = new SysUserVo();
        u1.setUserId(SENDER_101);
        u1.setUserName("sender-101");
        u1.setNickName("SenderNick");
        u1.setAvatar(AVATAR_OSS_1);
        when(sysUserMapper.selectVoByIds(anyCollection())).thenReturn(List.of(u1));

        // mock sysOssService.listByIds：返回 1 个 OSS URL
        com.nexus.system.domain.vo.SysOssVo oss = new com.nexus.system.domain.vo.SysOssVo();
        oss.setOssId(AVATAR_OSS_1);
        oss.setUrl("https://oss.example.com/avatar-101.png");
        when(sysOssService.listByIds(anyCollection())).thenReturn(List.of(oss));

        TableDataInfo<Map<String, Object>> result = imChatSupport.buildSenderMessagePage(page);

        // 守护：sysOssService.listByIds 仅调用 1 次
        verify(sysOssService, times(1)).listByIds(anyCollection());

        // 守护：每条消息行携带 senderAvatar URL
        assertThat(result.getRows()).hasSize(10);
        for (Map<String, Object> row : result.getRows()) {
            assertThat(row.get("senderId")).isEqualTo(SENDER_101);
            assertThat(row.get("senderName")).isEqualTo("SenderNick");
            assertThat(row.get("senderAvatar")).isEqualTo("https://oss.example.com/avatar-101.png");
        }
    }

    // ==================== null 发送者守护 ====================

    /**
     * P2-2: 消息的 fromUserId 在 userMap 中查不到（用户已删除） → 不抛异常，行不携带 sender 字段。
     */
    @Test
    void buildSenderMessagePage_senderNotFound_omitsSenderFields() {
        ImMessage msg = ImTestFixture.buildMessage(1L, 999L, ME_USER_ID, null, "hi");
        Page<ImMessage> page = new Page<>();
        page.setRecords(List.of(msg));
        page.setTotal(1);

        // batchLoadUsers 返回空（用户 999 不存在）
        when(sysUserMapper.selectVoByIds(anyCollection())).thenReturn(new ArrayList<>());
        // 注：userMap 为空 → avatarIds 也为空 → batchResolveAvatarUrls 短路，不调 listByIds

        TableDataInfo<Map<String, Object>> result = imChatSupport.buildSenderMessagePage(page);

        assertThat(result.getRows()).hasSize(1);
        Map<String, Object> row = result.getRows().get(0);
        // 守护：基础字段仍存在
        assertThat(row.get("messageId")).isEqualTo(1L);
        assertThat(row.get("content")).isEqualTo("hi");
        // 守护：sender 字段不存在（用户查不到）
        assertThat(row).doesNotContainKey("senderId");
        assertThat(row).doesNotContainKey("senderName");
        assertThat(row).doesNotContainKey("senderAvatar");
    }

    // ==================== 基础字段守护 ====================

    /**
     * P2-2: 单行基础字段（messageId/fromUserId/content/msgType 等）正确填充。
     */
    @Test
    void buildSenderMessagePage_singleMessage_includesBaseFields() {
        ImMessage msg = ImTestFixture.buildMessage(1L, SENDER_101, ME_USER_ID, null, "hello");
        msg.setMsgType("text");
        msg.setIsRead("0");
        msg.setRevoked("0");
        msg.setForwardFrom(null);
        Page<ImMessage> page = new Page<>();
        page.setRecords(List.of(msg));
        page.setTotal(1);

        SysUserVo u1 = new SysUserVo();
        u1.setUserId(SENDER_101);
        u1.setUserName("sender-101");
        when(sysUserMapper.selectVoByIds(anyCollection())).thenReturn(List.of(u1));
        // 注：u1 无 avatar → avatarIds 空 → batchResolveAvatarUrls 短路，不调 listByIds

        TableDataInfo<Map<String, Object>> result = imChatSupport.buildSenderMessagePage(page);

        Map<String, Object> row = result.getRows().get(0);
        // 守护：基础字段
        assertThat(row.get("messageId")).isEqualTo(1L);
        assertThat(row.get("fromUserId")).isEqualTo(SENDER_101);
        assertThat(row.get("toUserId")).isEqualTo(ME_USER_ID);
        assertThat(row.get("content")).isEqualTo("hello");
        assertThat(row.get("msgType")).isEqualTo("text");
        assertThat(row.get("messageType")).isEqualTo("text");  // 兼容字段
        assertThat(row.get("isRead")).isEqualTo("0");
        assertThat(row.get("revoked")).isEqualTo("0");
        // mentionUserIds 字段：null 时不放入 row
        assertThat(row).doesNotContainKey("mentionUserIds");
    }

    // ==================== P1-4 client_msg_id 幂等短路守护 ====================

    /**
     * P1-4: {@code findExistingByClientMsgId} 对 null/空白 clientMsgId 直接返回 null，
     * 不查询数据库（守护生产代码 {@code StrUtil.trimToNull} 短路逻辑）。
     * <p>若有人移除短路逻辑，对 null clientMsgId 也会查库，会产生无意义 SQL。
     */
    @Test
    void findExistingByClientMsgId_nullOrBlank_returnsNullWithoutDb() {
        // 守护：null clientMsgId → 直接返回 null，不查库
        Long r1 = imChatSupport.findExistingByClientMsgId(null, SENDER_101, ME_USER_ID, null);
        assertThat(r1).isNull();
        verify(imMessageMapper, never()).selectOne(any());

        // 守护：空白 clientMsgId → 直接返回 null，不查库
        Long r2 = imChatSupport.findExistingByClientMsgId("   ", SENDER_101, ME_USER_ID, null);
        assertThat(r2).isNull();
        verify(imMessageMapper, never()).selectOne(any());
    }

    /**
     * P1-4: {@code findExistingByClientMsgId} 对有效 clientMsgId 查询数据库，
     * 命中则返回旧 messageId。
     */
    @Test
    void findExistingByClientMsgId_validClientMsgId_queriesDbAndReturnsId() {
        ImMessage existing = ImTestFixture.buildMessage(999L, SENDER_101, ME_USER_ID, null, "old");
        when(imMessageMapper.selectOne(any())).thenReturn(existing);

        Long result = imChatSupport.findExistingByClientMsgId("client-uuid-001", SENDER_101, ME_USER_ID, null);

        assertThat(result).isEqualTo(999L);
        verify(imMessageMapper, times(1)).selectOne(any());
    }
}
