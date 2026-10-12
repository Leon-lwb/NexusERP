package com.nexus.im.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.im.domain.ImConversationSetting;
import com.nexus.im.domain.ImFavorite;
import com.nexus.im.domain.ImFriend;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.mapper.ImConversationSettingMapper;
import com.nexus.im.mapper.ImFavoriteMapper;
import com.nexus.im.mapper.ImFriendMapper;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.mapper.SysUserNotificationMapper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.IImChatService;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.service.impl.support.ImConversationService;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P6-G1: AppImController 高级交互接口测试。
 * <p>
 * 守护 P6 阶段新增的 3 类接口：
 * <ul>
 *   <li>会话配置（pin/mute/removeSetting）— 首次 INSERT、二次 UPDATE、幂等</li>
 *   <li>消息收藏（add/remove/list）— 越权消息拒绝、重复收藏幂等、收藏归属校验</li>
 * </ul>
 * <p>
 * 设计要点（与 AppImControllerSecurityTest / AppImControllerSyncTest 一致）：
 * <ul>
 *   <li>纯 Mockito 单元测试（{@code @ExtendWith(MockitoExtension.class)} 由 {@link ImUnitTestBase} 提供）</li>
 *   <li>{@code LoginHelper.getUserId()} 静态 mock 在基类管理，默认返回 {@link #ME_USER_ID}</li>
 *   <li>断言用 AssertJ，与现有测试同风格</li>
 *   <li>{@code @BeforeAll} 预热 MyBatis-Plus Lambda 缓存（ImConversationSetting/ImFavorite 为 P6 新增实体，
 *       其他模块的测试未注册，必须本类自行 initTableInfo）</li>
 * </ul>
 */
class AppImControllerP6Test extends ImUnitTestBase {

    @Mock private IImChatService imChatService;
    @Mock private ISysUserNotificationService notificationService;
    @Mock private SysUserMapper userMapper;
    @Mock private ImMessageMapper messageMapper;
    @Mock private ImGroupMapper groupMapper;
    @Mock private ImGroupMemberMapper groupMemberMapper;
    @Mock private SysUserNotificationMapper notificationMapper;
    @Mock private ImFriendMapper friendMapper;
    @Mock private ImRealtimePush imRealtimePush;
    @Mock private ImConversationService conversationService;
    @Mock private ImConversationSettingMapper conversationSettingMapper;
    @Mock private ImFavoriteMapper favoriteMapper;

    @Mock private com.nexus.im.service.impl.support.ImMessageVisibility visibility;

    @InjectMocks
    private AppImController controller;

    private static final Long TARGET_ID = 999L;

    /**
     * 预热 MyBatis-Plus Lambda 缓存。
     * <p>P6 新增 ImConversationSetting / ImFavorite 在其他测试类未注册，
     * 控制器内构造 {@code new LambdaQueryWrapper<ImConversationSetting>().eq(...)} 时需要表信息缓存。
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ImMessage.class);
        TableInfoHelper.initTableInfo(assistant, ImGroup.class);
        TableInfoHelper.initTableInfo(assistant, ImGroupMember.class);
        TableInfoHelper.initTableInfo(assistant, ImFriend.class);
        TableInfoHelper.initTableInfo(assistant, SysUserNotification.class);
        TableInfoHelper.initTableInfo(assistant, ImConversationSetting.class);
        TableInfoHelper.initTableInfo(assistant, ImFavorite.class);
    }

    // ==================== pinConversation ====================

    /**
     * P6-B1: 首次置顶 → getOrCreateSetting 走 INSERT 分支（selectOne 返回 null）。
     * <p>守护点：
     * <ul>
     *   <li>selectOne 返回 null 时调用 insert（创建配置记录）</li>
     *   <li>insert 后调用 updateById 设置 isPinned=1 + pinnedAt</li>
     *   <li>失效会话缓存</li>
     * </ul>
     */
    @Test
    void pinConversation_firstCall_shouldInsert() {
        when(conversationSettingMapper.selectOne(any())).thenReturn(null);
        when(conversationSettingMapper.insert(any(ImConversationSetting.class))).thenReturn(1);
        when(conversationSettingMapper.updateById(any(ImConversationSetting.class))).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("chatType", "PRIVATE");
        body.put("targetId", TARGET_ID);
        body.put("isPinned", true);

        R<Void> result = controller.pinConversation(body);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        // 守护：首次调用 → insert 1 次
        verify(conversationSettingMapper, times(1)).insert(any(ImConversationSetting.class));
        // 守护：insert 后 updateById 设置 isPinned
        ArgumentCaptor<ImConversationSetting> insertCaptor = ArgumentCaptor.forClass(ImConversationSetting.class);
        verify(conversationSettingMapper).insert(insertCaptor.capture());
        ImConversationSetting inserted = insertCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(ME_USER_ID);
        assertThat(inserted.getChatType()).isEqualTo("PRIVATE");
        assertThat(inserted.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(inserted.getIsPinned()).isEqualTo(0);
        assertThat(inserted.getIsMuted()).isEqualTo(0);
        // 守护：updateById 设置 isPinned=1 + pinnedAt 非 null
        ArgumentCaptor<ImConversationSetting> updateCaptor = ArgumentCaptor.forClass(ImConversationSetting.class);
        verify(conversationSettingMapper).updateById(updateCaptor.capture());
        ImConversationSetting updated = updateCaptor.getValue();
        assertThat(updated.getIsPinned()).isEqualTo(1);
        assertThat(updated.getPinnedAt()).isNotNull();
        // 守护：失效会话缓存
        verify(conversationService, times(1)).evictConversationCache();
    }

    /**
     * P6-B1: 第二次置顶（取消置顶）→ getOrCreateSetting 走 UPDATE 分支（selectOne 返回已存在）。
     * <p>守护点：
     * <ul>
     *   <li>selectOne 返回非 null 时不调用 insert（仅 UPDATE）</li>
     *   <li>isPinned=false 时 updateById 设置 isPinned=0 + pinnedAt=null</li>
     * </ul>
     * 若有人移除 selectOne 短路逻辑（每次都 insert），此用例立即失败。
     */
    @Test
    void pinConversation_secondCall_shouldUpdateNotInsert() {
        ImConversationSetting existing = new ImConversationSetting();
        existing.setId(1L);
        existing.setUserId(ME_USER_ID);
        existing.setChatType("PRIVATE");
        existing.setTargetId(TARGET_ID);
        existing.setIsPinned(1);
        when(conversationSettingMapper.selectOne(any())).thenReturn(existing);
        when(conversationSettingMapper.updateById(any(ImConversationSetting.class))).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("chatType", "PRIVATE");
        body.put("targetId", TARGET_ID);
        body.put("isPinned", false);

        R<Void> result = controller.pinConversation(body);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        // 守护：第二次调用 → 不再 insert
        verify(conversationSettingMapper, never()).insert(any(ImConversationSetting.class));
        // 守护：updateById 调用 1 次，isPinned=0 + pinnedAt=null
        ArgumentCaptor<ImConversationSetting> updateCaptor = ArgumentCaptor.forClass(ImConversationSetting.class);
        verify(conversationSettingMapper).updateById(updateCaptor.capture());
        ImConversationSetting updated = updateCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getIsPinned()).isEqualTo(0);
        assertThat(updated.getPinnedAt()).isNull();
    }

    // ==================== muteConversation ====================

    /**
     * P6-B1: 免打扰幂等——重复调用 muteConversation，仍只有 1 次 insert（首次创建配置）+ 多次 updateById。
     * <p>守护点：
     * <ul>
     *   <li>第 1 次：selectOne 返回 null → insert 1 次 + updateById 1 次</li>
     *   <li>第 2 次：selectOne 返回已存在 → 不再 insert，仅 updateById 1 次</li>
     *   <li>合计：insert 1 次 + updateById 2 次（不会产生重复配置记录）</li>
     * </ul>
     */
    @Test
    void muteConversation_idempotent() {
        ImConversationSetting existing = new ImConversationSetting();
        existing.setId(1L);
        existing.setUserId(ME_USER_ID);
        existing.setChatType("GROUP");
        existing.setTargetId(TARGET_ID);
        existing.setIsMuted(1);

        // 第 1 次 selectOne 返回 null（不存在），第 2 次返回已存在
        when(conversationSettingMapper.selectOne(any()))
            .thenReturn(null)
            .thenReturn(existing);
        when(conversationSettingMapper.insert(any(ImConversationSetting.class))).thenReturn(1);
        when(conversationSettingMapper.updateById(any(ImConversationSetting.class))).thenReturn(1);

        Map<String, Object> body1 = new HashMap<>();
        body1.put("chatType", "GROUP");
        body1.put("targetId", TARGET_ID);
        body1.put("isMuted", true);
        controller.muteConversation(body1);

        Map<String, Object> body2 = new HashMap<>();
        body2.put("chatType", "GROUP");
        body2.put("targetId", TARGET_ID);
        body2.put("isMuted", true);
        controller.muteConversation(body2);

        // 守护：仅 1 次 insert（首次创建配置）
        verify(conversationSettingMapper, times(1)).insert(any(ImConversationSetting.class));
        // 守护：2 次 updateById（每次都更新 isMuted）
        verify(conversationSettingMapper, times(2)).updateById(any(ImConversationSetting.class));
    }

    // ==================== removeConversationSetting ====================

    /**
     * P6-B1: removeConversationSetting 仅删除当前用户的配置记录。
     * <p>守护点：
     * <ul>
     *   <li>调用 conversationSettingMapper.delete（带 wrapper）</li>
     *   <li>失效会话缓存</li>
     *   <li>返回 R.ok()</li>
     * </ul>
     * <p>注：当前用户过滤通过 LambdaQueryWrapper 在 SQL 层强制，单元测试层面验证 delete 被调用即可，
     * wrapper 的 user_id 过滤由集成测试覆盖（ImTenantIsolationTest 等）。
     */
    @Test
    void removeConversationSetting_onlyDeleteOwn() {
        when(conversationSettingMapper.delete(any())).thenReturn(1);

        R<Void> result = controller.removeConversationSetting("PRIVATE", TARGET_ID);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        // 守护：delete 调用 1 次（按 userId/chatType/targetId 过滤）
        verify(conversationSettingMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        // 守护：失效缓存
        verify(conversationService, times(1)).evictConversationCache();
    }

    // ==================== addFavorite 越权 ====================

    /**
     * P6-B2: 收藏不存在的消息 → R.fail("消息不存在")。
     */
    @Test
    void addFavorite_messageNotExist_shouldFail() {
        when(messageMapper.selectById(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "99999");

        R<Void> result = controller.addFavorite(body);

        assertThat(result.getCode()).isEqualTo(R.FAIL);
        assertThat(result.getMsg()).contains("消息不存在");
        // 守护：消息不存在时不应触发收藏写入
        verify(favoriteMapper, never()).insert(any(ImFavorite.class));
    }

    /**
     * P6-B2: 收藏他人私聊消息（既不是发送者也不是接收者）→ R.fail("无权收藏此消息")。
     * <p>canAccessMessage 私聊分支：from != me && to != me → 无权。
     */
    @Test
    void addFavorite_nonAccessiblePrivateMessage_shouldFail() {
        // 消息归属：PEER → OTHER（当前用户 ME 不在 from/to 中）
        ImMessage msg = ImTestFixture.buildMessage(1L, PEER_USER_ID, OTHER_TENANT_USER_ID, null, "secret");
        when(messageMapper.selectById(1L)).thenReturn(msg);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");

        R<Void> result = controller.addFavorite(body);

        assertThat(result.getCode()).isEqualTo(R.FAIL);
        assertThat(result.getMsg()).contains("无权收藏");
        // 守护：越权场景下不应写入收藏
        verify(favoriteMapper, never()).insert(any(ImFavorite.class));
        // 守护：未触发幂等检查（消息越权在前）
        verify(favoriteMapper, never()).selectCount(any());
    }

    /**
     * P6-B2: 收藏他人群聊消息（非群成员）→ R.fail("无权收藏此消息")。
     * <p>canAccessMessage 群聊分支：groupMemberMapper.selectCount 返回 0 → 无权。
     */
    @Test
    void addFavorite_nonAccessibleGroupMessage_shouldFail() {
        ImMessage msg = ImTestFixture.buildMessage(1L, PEER_USER_ID, null, 888L, "group secret");
        when(messageMapper.selectById(1L)).thenReturn(msg);
        // 当前用户不是群 888 的成员
        when(groupMemberMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");

        R<Void> result = controller.addFavorite(body);

        assertThat(result.getCode()).isEqualTo(R.FAIL);
        assertThat(result.getMsg()).contains("无权收藏");
        verify(favoriteMapper, never()).insert(any(ImFavorite.class));
    }

    // ==================== addFavorite 幂等 ====================

    /**
     * P6-B2: 重复收藏同一消息 → 幂等返回 R.ok()，不重复 insert。
     * <p>守护点：
     * <ul>
     *   <li>favoriteMapper.selectCount 返回 1（已收藏）</li>
     *   <li>返回 R.ok()（不抛错）</li>
     *   <li>favoriteMapper.insert 永不被调用</li>
     * </ul>
     */
    @Test
    void addFavorite_duplicate_shouldBeIdempotent() {
        // 当前用户是发送者 → 有权收藏
        ImMessage msg = ImTestFixture.buildMessage(1L, ME_USER_ID, PEER_USER_ID, null, "hi");
        when(messageMapper.selectById(1L)).thenReturn(msg);
        // 已存在收藏记录
        when(favoriteMapper.selectCount(any())).thenReturn(1L);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");

        R<Void> result = controller.addFavorite(body);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        // 守护：幂等场景下不应重复 insert
        verify(favoriteMapper, never()).insert(any(ImFavorite.class));
    }

    /**
     * P6-B2: 首次收藏合法消息 → 正确写入 ImFavorite，填充 chatType/targetId/fromUserId/msgType/contentSnippet。
     * <p>守护点：私聊消息收藏时 targetId = 对端 userId（fromUserId==me 时取 toUserId）。
     */
    @Test
    void addFavorite_firstTime_privateMessage_fillsCorrectFields() {
        // 当前用户是发送者 → 有权收藏，对端 = PEER
        ImMessage msg = ImTestFixture.buildMessage(1L, ME_USER_ID, PEER_USER_ID, null, "hello world");
        when(messageMapper.selectById(1L)).thenReturn(msg);
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.insert(any(ImFavorite.class))).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");

        R<Void> result = controller.addFavorite(body);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        ArgumentCaptor<ImFavorite> captor = ArgumentCaptor.forClass(ImFavorite.class);
        verify(favoriteMapper).insert(captor.capture());
        ImFavorite fav = captor.getValue();
        assertThat(fav.getUserId()).isEqualTo(ME_USER_ID);
        assertThat(fav.getMessageId()).isEqualTo(1L);
        assertThat(fav.getChatType()).isEqualTo("PRIVATE");
        assertThat(fav.getTargetId()).isEqualTo(PEER_USER_ID);
        assertThat(fav.getFromUserId()).isEqualTo(ME_USER_ID);
        assertThat(fav.getMsgType()).isEqualTo("text");
        assertThat(fav.getContentSnippet()).isEqualTo("hello world");
    }

    /**
     * P6-B2: 收藏群聊消息 → chatType=GROUP，targetId=groupId。
     */
    @Test
    void addFavorite_groupMessage_fillsGroupTargetId() {
        // 群消息：当前用户在群里（selectCount > 0）
        ImMessage msg = ImTestFixture.buildMessage(1L, PEER_USER_ID, null, 888L, "group hello");
        when(messageMapper.selectById(1L)).thenReturn(msg);
        when(groupMemberMapper.selectCount(any())).thenReturn(1L);
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.insert(any(ImFavorite.class))).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");

        R<Void> result = controller.addFavorite(body);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        ArgumentCaptor<ImFavorite> captor = ArgumentCaptor.forClass(ImFavorite.class);
        verify(favoriteMapper).insert(captor.capture());
        ImFavorite fav = captor.getValue();
        assertThat(fav.getChatType()).isEqualTo("GROUP");
        assertThat(fav.getTargetId()).isEqualTo(888L);
        assertThat(fav.getFromUserId()).isEqualTo(PEER_USER_ID);
    }

    /**
     * P6-B2: 收藏内容超过 500 字 → contentSnippet 被截断到 500 字。
     */
    @Test
    void addFavorite_longContent_truncatedTo500() {
        String longContent = "a".repeat(800);
        ImMessage msg = ImTestFixture.buildMessage(1L, ME_USER_ID, PEER_USER_ID, null, longContent);
        when(messageMapper.selectById(1L)).thenReturn(msg);
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.insert(any(ImFavorite.class))).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("messageId", "1");

        controller.addFavorite(body);

        ArgumentCaptor<ImFavorite> captor = ArgumentCaptor.forClass(ImFavorite.class);
        verify(favoriteMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentSnippet()).hasSize(500);
    }

    // ==================== removeFavorite 越权 ====================

    /**
     * P6-B2: 取消他人收藏 → R.fail("收藏不存在或无权操作")。
     * <p>守护点：favoriteMapper.selectById 返回的记录 userId != 当前用户 → 拒绝。
     */
    @Test
    void removeFavorite_notOwner_shouldFail() {
        ImFavorite others = new ImFavorite();
        others.setId(1L);
        others.setUserId(PEER_USER_ID);  // 不是当前用户
        others.setMessageId(100L);
        when(favoriteMapper.selectById(1L)).thenReturn(others);

        R<Void> result = controller.removeFavorite(1L);

        assertThat(result.getCode()).isEqualTo(R.FAIL);
        assertThat(result.getMsg()).contains("无权操作");
        // 守护：非归属场景下不应删除
        verify(favoriteMapper, never()).deleteById(anyLong());
    }

    /**
     * P6-B2: 收藏不存在 → R.fail。
     */
    @Test
    void removeFavorite_notExist_shouldFail() {
        when(favoriteMapper.selectById(1L)).thenReturn(null);

        R<Void> result = controller.removeFavorite(1L);

        assertThat(result.getCode()).isEqualTo(R.FAIL);
        verify(favoriteMapper, never()).deleteById(anyLong());
    }

    /**
     * P6-B2: 取消自己的收藏 → R.ok()，调用 deleteById。
     */
    @Test
    void removeFavorite_owner_shouldSucceed() {
        ImFavorite mine = new ImFavorite();
        mine.setId(1L);
        mine.setUserId(ME_USER_ID);
        mine.setMessageId(100L);
        when(favoriteMapper.selectById(1L)).thenReturn(mine);
        when(favoriteMapper.deleteById(anyLong())).thenReturn(1);

        R<Void> result = controller.removeFavorite(1L);

        assertThat(result.getCode()).isEqualTo(R.SUCCESS);
        verify(favoriteMapper, times(1)).deleteById(1L);
    }

    // ==================== favoriteList ====================

    /**
     * P6-B2: favoriteList 委托 favoriteMapper.selectPage 查询，返回 TableDataInfo。
     * <p>守护点：
     * <ul>
     *   <li>调用 favoriteMapper.selectPage 1 次（按 userId 过滤的 SQL 由 wrapper 保证）</li>
     *   <li>返回的 TableDataInfo.rows 与 mock 返回的 Page.records 一致</li>
     * </ul>
     * <p>注：userId 过滤在 SQL 层强制，单元测试验证委托关系即可。
     */
    @Test
    void favoriteList_delegatesToMapperAndReturnsRows() {
        // 准备 mock 数据：当前用户的 2 条收藏
        ImFavorite fav1 = new ImFavorite();
        fav1.setId(1L);
        fav1.setUserId(ME_USER_ID);
        fav1.setMessageId(100L);
        ImFavorite fav2 = new ImFavorite();
        fav2.setId(2L);
        fav2.setUserId(ME_USER_ID);
        fav2.setMessageId(200L);

        Page<ImFavorite> mockPage = new Page<>(1, 10);
        mockPage.setTotal(2);
        List<ImFavorite> records = new ArrayList<>();
        records.add(fav1);
        records.add(fav2);
        mockPage.setRecords(records);
        when(favoriteMapper.selectPage(any(), any())).thenReturn(mockPage);

        TableDataInfo<ImFavorite> result = controller.favoriteList(com.nexus.im.support.ImTestFixture.pageQuery(10, 1));

        // 守护：调用 selectPage 1 次
        verify(favoriteMapper, times(1)).selectPage(any(), any(LambdaQueryWrapper.class));
        // 守护：返回的 rows 与 mock 数据一致
        assertThat(result.getRows()).hasSize(2);
        // 守护：所有返回的收藏都归属当前用户（模拟 SQL 过滤生效后的结果）
        for (ImFavorite fav : result.getRows()) {
            assertThat(fav.getUserId()).isEqualTo(ME_USER_ID);
        }
        assertThat(result.getTotal()).isEqualTo(2L);
    }

    /**
     * P6-B2: favoriteList 空列表场景 → 返回空 rows，不抛异常。
     */
    @Test
    void favoriteList_empty_returnsEmptyRows() {
        Page<ImFavorite> mockPage = new Page<>(1, 10);
        mockPage.setTotal(0);
        mockPage.setRecords(new ArrayList<>());
        when(favoriteMapper.selectPage(any(), any())).thenReturn(mockPage);

        TableDataInfo<ImFavorite> result = controller.favoriteList(com.nexus.im.support.ImTestFixture.pageQuery(10, 1));

        assertThat(result.getRows()).isNullOrEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
    }
}
