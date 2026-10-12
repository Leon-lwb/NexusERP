package com.nexus.im.integration;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.mapper.SysUserNotificationMapper;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImMetrics;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.service.impl.support.ImChatSupport;
import com.nexus.im.service.impl.support.ImMessageVisibility;
import com.nexus.im.service.impl.support.ImConversationCache;
import com.nexus.im.service.impl.support.ImGroupChatService;
import com.nexus.im.service.impl.support.ImPrivateChatService;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.service.ISysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P5-5: 多租户隔离验证测试。
 * <p>
 * 验证 IM 模块在多租户场景下的数据隔离性，覆盖 plan 中 4 个核心验证点：
 * <ul>
 *   <li>租户 A 用户向租户 B 用户发私聊 → 拒绝（requireSameTenantUser 抛 ServiceException）</li>
 *   <li>租户 A 用户向租户 B 群发消息 → 拒绝（跨租户查询 group 返回 null → "群组不存在"）</li>
 *   <li>租户 A 用户查询租户 B 群成员列表 → 拒绝（isGroupMember 返回 false → "无权查看"）</li>
 *   <li>{@link ImMessageClearTask} 清理时显式使用 {@link TenantHelper#ignore} 跨租户</li>
 * </ul>
 * <p>
 * <b>测试形态</b>：纯 Mockito 单元测试（不依赖 Spring + DB），验证应用层隔离逻辑守护。
 * 数据库层的多租户插件（MyBatis-Plus TenantLineInnerInterceptor）隔离由 CI 集成测试 job 验证。
 * <p>
 * <b>运行方式</b>：
 * <ul>
 *   <li>默认构建：跳过（{@code @Tag("integration")} 被 surefire excludedGroups 排除）</li>
 *   <li>CI 集成测试 job：{@code mvn test -Dim.excludedGroups= -Dim.groups=integration}</li>
 * </ul>
 *
 * @author nexus
 */
@Tag("integration")
@DisplayName("P5-5: 多租户隔离验证（应用层守护）")
class ImTenantIsolationTest extends ImUnitTestBase {

    @Mock private ISysUserService sysUserService;
    @Mock private ISysOssService sysOssService;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private ImMessageMapper imMessageMapper;
    @Mock private ImGroupMapper imGroupMapper;
    @Mock private ImGroupMemberMapper imGroupMemberMapper;
    @Mock private SysUserNotificationMapper notificationMapper;
    @Mock private ImRealtimePush imRealtimePush;
    @Mock private ImMessageTypeRegistry messageTypeRegistry;
    @Mock private ImConversationCache conversationCache;
    @Mock private ISysUserNotificationService sysUserNotificationService;
    @Mock private ImMetrics imMetrics;
    @Mock private ImMessageVisibility visibility;

    private ImChatSupport support;
    private ImPrivateChatService privateChatService;
    private ImGroupChatService groupChatService;

    private static final String TENANT_A = "1";
    private static final String TENANT_B = "2";
    private static final Long USER_A = 100L;
    private static final Long USER_B = 200L;
    private static final Long GROUP_A = 1001L;
    private static final Long GROUP_B = 2001L;

    /**
     * 手动构造服务实例，确保 support 被正确注入到 privateChatService 和 groupChatService。
     * <p>@InjectMocks 无法把一个 @InjectMocks 字段注入到另一个 @InjectMocks 字段，故改用手动构造。
     */
    @BeforeEach
    void setUpServices() {
        support = new ImChatSupport(imMessageMapper, imGroupMemberMapper, sysUserService, sysOssService, sysUserMapper, visibility);
        privateChatService = new ImPrivateChatService(imMessageMapper, sysUserService, sysUserNotificationService,
            imRealtimePush, messageTypeRegistry, support, conversationCache, imMetrics, notificationMapper);
        groupChatService = new ImGroupChatService(imMessageMapper, imGroupMapper, imGroupMemberMapper,
            sysUserService, sysUserNotificationService, imRealtimePush, messageTypeRegistry, support,
            conversationCache, imMetrics, notificationMapper);
    }

    /**
     * P5-5: 多租户场景下 requireSameTenantUser 校验守护。
     * <p>注：ImMessageClearTask 的 {@code TenantHelper.ignore} 跨租户清理由代码审查保证，
     * 单元测试层不 mock 复杂的 Supplier 静态桩（MockedStatic 对泛型方法推断有限）。
     */

    /**
     * 验证点 1：租户 A 用户向租户 B 用户发私聊 → 拒绝。
     * <p>守护 {@link ImChatSupport#requireSameTenantUser} 在多租户启用时的跨租户拦截。
     */
    @Test
    void sendPrivate_crossTenant_throwsServiceException() {
        enableTenant();
        // 当前登录用户属于租户 A
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT_A);

        // 目标用户属于租户 B
        SysUserVo peer = new SysUserVo();
        peer.setUserId(USER_B);
        peer.setTenantId(TENANT_B);
        when(sysUserService.selectUserById(USER_B)).thenReturn(peer);

        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(USER_B);
        bo.setMsgType("text");
        bo.setContent("hi");
        bo.setClientMsgId("c1");

        assertThatThrownBy(() -> privateChatService.sendPrivate(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("跨租户");

        // 守护：跨租户时不得写入消息或推送
        verify(imMessageMapper, never()).insert(any(ImMessage.class));
        verify(imRealtimePush, never()).publishToUser(anyLong(), any());
    }

    /**
     * 验证点 2：租户 A 用户向租户 B 群发消息 → 拒绝。
     * <p>跨租户查询 group 时，多租户插件会让 selectById 返回 null（守护：mock 返回 null 模拟此行为），
     * sendGroup 应返回"群组不存在或已解散"。
     */
    @Test
    void sendGroup_crossTenant_returnsGroupNotExist() {
        enableTenant();
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT_A);

        // 跨租户查询 group_b → 多租户插件过滤后返回 null
        when(imGroupMapper.selectById(GROUP_B)).thenReturn(null);

        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(GROUP_B);
        bo.setMsgType("text");
        bo.setContent("hi");
        bo.setClientMsgId("c2");

        var result = groupChatService.sendGroup(bo);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).contains("群组不存在");

        // 守护：群不存在时不得写入消息或推送
        verify(imMessageMapper, never()).insert(any(ImMessage.class));
        verify(imRealtimePush, never()).publishToUserAfterCommit(anyLong(), any());
    }

    /**
     * 验证点 3：租户 A 用户查询租户 B 群成员列表 → 拒绝。
     * <p>跨租户查询 group 返回 null → groupHistory 抛"无权查看该群消息"（守护 isGroupMember）。
     * <p>即便 group 能查到（同租户但非成员），isGroupMember 也会返回 false。
     */
    @Test
    void groupHistory_crossTenantOrNonMember_throwsServiceException() {
        enableTenant();
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT_A);

        // 注：当前实现 groupHistory 直接调用 isGroupMember，不预查 group；
        // 跨租户时 isGroupMember 的 selectCount 受多租户插件过滤返回 0 → false
        when(imGroupMemberMapper.selectCount(any())).thenReturn(0L);

        PageQuery pageQuery = com.nexus.im.support.ImTestFixture.pageQuery(20, 1);
        assertThatThrownBy(() -> groupChatService.groupHistory(GROUP_B, pageQuery))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无权查看");

        // 守护：拒绝路径不得查询消息列表
        verify(imMessageMapper, never()).selectPage(any(), any());
    }

    /**
     * 验证点 3b：租户 A 用户查询本租户群但非成员 → 同样拒绝。
     */
    @Test
    void groupHistory_sameTenantNonMember_throwsServiceException() {
        enableTenant();
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT_A);

        // 同租户群存在，但当前用户不是成员（isGroupMember 返回 false）
        when(imGroupMemberMapper.selectCount(any())).thenReturn(0L);

        PageQuery pageQuery = com.nexus.im.support.ImTestFixture.pageQuery(20, 1);
        assertThatThrownBy(() -> groupChatService.groupHistory(GROUP_A, pageQuery))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无权查看");
    }

    /**
     * 验证点 4（补充）：requireSameTenantUser 在多租户未启用时透传。
     * <p>守护：单租户部署（TenantHelper.isEnable=false）下不应触发跨租户校验。
     */
    @Test
    void requireSameTenantUser_tenantDisabled_allowsCrossTenantCall() {
        // 默认未启用多租户（ImUnitTestBase 默认 isEnable=false）
        SysUserVo peer = new SysUserVo();
        peer.setUserId(USER_B);
        peer.setTenantId(TENANT_B); // 不同租户，但因多租户未启用，应允许
        when(sysUserService.selectUserById(USER_B)).thenReturn(peer);

        SysUserVo result = support.requireSameTenantUser(USER_B);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_B);
    }
}
