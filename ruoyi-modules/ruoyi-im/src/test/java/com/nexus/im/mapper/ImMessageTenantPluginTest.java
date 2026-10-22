package com.nexus.im.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImMetrics;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.service.impl.support.ImChatSupport;
import com.nexus.im.service.impl.support.ImConversationCache;
import com.nexus.im.service.impl.support.ImGroupChatService;
import com.nexus.im.support.ImTestFixture;
import com.nexus.im.support.ImUnitTestBase;
import com.nexus.system.service.ISysUserService;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 真正的 Mapper 动态 SQL、租户处理器和插件改写，仅最终 Executor 使用内存替身。 */
class ImMessageTenantPluginTest extends ImUnitTestBase {
    private static final String ACTOR_TENANT = "000000";
    private static final Long HIDDEN = 1_900_000_000_000_000_123L;
    private static final Long GROUP = 1_900_000_000_000_000_200L;
    private MybatisConfiguration configuration;
    private MybatisPlusInterceptor plugins;

    @BeforeEach
    void prepareRealPlugin() {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(ACTOR_TENANT);
        var properties = new TenantProperties();
        properties.setEnable(true);
        properties.setExcludes(List.of());
        plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties)));
        configuration = new MybatisConfiguration();
        configuration.addMapper(ImMessageMapper.class);
        configuration.addMapper(SysUserNotificationMapper.class);
    }

    @Test
    void latestHiddenPrivateQuery_injectsTenantIntoBothAggregationAndOuterMessage() throws Exception {
        // 传入其他租户也不能覆盖当前登录租户的插件条件。
        var params = params("other-tenant", List.of(HIDDEN));
        BoundSql bound = throughPlugin(ImMessageMapper.class, "selectLatestVisiblePrivatePerPeer", params);
        PlainSelect outer = parsed(bound);
        var subquery = (ParenthesedSelect) outer.getJoins().get(0).getRightItem();
        var inner = (PlainSelect) subquery.getSelect();

        assertThat(outer.getWhere().toString()).contains("m.tenant_id = ?", "m.tenant_id = '000000'");
        assertThat(inner.getWhere().toString()).contains("tenant_id = ?", "tenant_id = '000000'", "message_id NOT IN (?)");
        assertThat(inner.getGroupBy().toString()).contains("LEAST", "GREATEST");
        assertThat(outer.getLimit()).isNotNull();
        assertThat(bindingValues(bound, params)).containsExactly("other-tenant", ME_USER_ID, ME_USER_ID, HIDDEN, "other-tenant");
    }

    @Test
    void unreadGroupJoin_keepsBothTablesTenantAndRecipientsHiddenFilters() throws Exception {
        var params = params(ACTOR_TENANT, List.of(HIDDEN, HIDDEN + 1));
        BoundSql bound = throughPlugin(ImMessageMapper.class, "selectGroupUnreadCounts", params);
        PlainSelect query = parsed(bound);
        String sql = query.toString();

        assertThat(sql).contains("m.tenant_id = '000000'", "n.tenant_id = '000000'", "n.tenant_id = m.tenant_id",
            "n.user_id = ?", "n.read_flag = '0'", "m.revoked = '0'", "m.group_id IN (?, ?)",
            "m.message_id NOT IN (?, ?)", "CAST(m.message_id AS CHAR)", "COUNT(DISTINCT m.message_id)");
        assertThat(sql).doesNotContain("m.is_read", "n.tenant_id = NULL");
        assertThat(bindingValues(bound, params)).contains(ME_USER_ID, HIDDEN, HIDDEN + 1, GROUP, GROUP + 1);
    }

    @Test
    void emptyHiddenSet_stillParsesAndDoesNotGenerateInvalidNotIn() throws Exception {
        var params = params(ACTOR_TENANT, List.of());
        for (String method : List.of("selectLatestVisiblePrivatePerPeer", "selectGroupUnreadCounts")) {
            BoundSql bound = throughPlugin(ImMessageMapper.class, method, params);
            String sql = parsed(bound).toString();
            assertThat(sql).doesNotContain("NOT IN ()", "message_id NOT IN");
            assertThat(sql).contains("tenant_id = '000000'");
        }
    }

    @Test
    void singleTenantPlugin_doesNotAddNullTenantPredicate() throws Exception {
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(null);
        var params = params(null, List.of(HIDDEN));
        for (String method : List.of("selectLatestVisiblePrivatePerPeer", "selectGroupUnreadCounts")) {
            BoundSql bound = throughPlugin(ImMessageMapper.class, method, params);
            String sql = parsed(bound).toString();
            assertThat(sql).doesNotContain("tenant_id = NULL", "tenant_id = ?", "tenant_id = '000000'");
            assertThat(sql).contains("message_id NOT IN (?)");
        }
    }

    @Test
    void groupReadExistsQuery_usesTenantPluginForNotificationAndReferencedMessage() throws Exception {
        var messages = mock(ImMessageMapper.class);
        var groups = mock(ImGroupMapper.class);
        var members = mock(ImGroupMemberMapper.class);
        var notifications = mock(SysUserNotificationMapper.class);
        var support = mock(ImChatSupport.class);
        when(groups.selectById(GROUP)).thenReturn(ImTestFixture.buildGroup(GROUP, "测试群", ME_USER_ID));
        when(support.isGroupMember(GROUP, ME_USER_ID)).thenReturn(true);
        when(support.hiddenMessageIds()).thenReturn(Set.of(HIDDEN));
        when(notifications.selectList(any())).thenReturn(List.of());
        var service =
                new ImGroupChatService(
                        messages,
                        groups,
                        members,
                        mock(ISysUserService.class),
                        mock(ISysUserNotificationService.class),
                        mock(ImRealtimePush.class),
                        mock(ImMessageTypeRegistry.class),
                        support,
                        mock(ImConversationCache.class),
                        mock(ImMetrics.class),
                        notifications);
        service.markGroupRead(GROUP);
        ArgumentCaptor<LambdaQueryWrapper<SysUserNotification>> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(notifications).selectList(wrapper.capture());
        Map<String, Object> params = Map.of("ew", wrapper.getValue());
        BoundSql bound = throughPlugin(SysUserNotificationMapper.class, "selectList", params);
        String sql = parsed(bound).toString();

        // 外层表无别名，插件正确生成未限定表名的 tenant_id 条件。
        assertThat(sql).contains("AND tenant_id = '000000'", "m.tenant_id = '000000'",
            "m.tenant_id = sys_user_notification.tenant_id", "CAST(m.message_id AS CHAR)", "user_id = ?",
            "read_flag = ?", "ref_id NOT IN (?)", "m.group_id = ?");
        assertThat(sql).doesNotContain("im_message.is_read");
        assertThat(bindingValues(bound, params)).contains(ME_USER_ID, String.valueOf(HIDDEN), GROUP);
    }

    private Map<String, Object> params(String tenantId, Collection<Long> hiddenIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("me", ME_USER_ID);
        params.put("hiddenIds", hiddenIds);
        params.put("groupIds", List.of(GROUP, GROUP + 1));
        return params;
    }

    /** 完整经过 MyBatis-Plus 查询插件；只隔离最后的数据库 Executor。 */
    private BoundSql throughPlugin(Class<?> mapper, String method, Map<String, Object> params) throws Exception {
        MappedStatement statement = configuration.getMappedStatement(mapper.getName() + "." + method);
        assertThat(InterceptorIgnoreHelper.willIgnoreTenantLine(statement.getId())).isFalse();
        BoundSql bound = statement.getBoundSql(params);
        List<String> originalBindings = bound.getParameterMappings().stream().map(p -> p.getProperty()).toList();
        Executor sink = mock(Executor.class);
        when(sink.createCacheKey(any(), any(), any(), any())).thenReturn(new CacheKey());
        doAnswer(call -> List.of(call.getArgument(5, BoundSql.class))).when(sink).query(
            any(MappedStatement.class), any(), any(RowBounds.class), isNull(), any(CacheKey.class), any(BoundSql.class));
        Executor intercepted = (Executor) plugins.plugin(sink);
        List<BoundSql> forwarded = intercepted.query(statement, params, RowBounds.DEFAULT, null, new CacheKey(), bound);
        assertThat(forwarded).hasSize(1);
        assertThat(bound.getParameterMappings().stream().map(p -> p.getProperty()).toList()).containsExactlyElementsOf(originalBindings);
        return forwarded.get(0);
    }

    private PlainSelect parsed(BoundSql bound) throws Exception {
        // 再解析改写后的语法树，不能仅凭字符串包含判断插件是否成功。
        return (PlainSelect) CCJSqlParserUtil.parse(bound.getSql());
    }

    private List<Object> bindingValues(BoundSql bound, Map<String, Object> params) {
        List<Object> values = new ArrayList<>();
        for (var binding : bound.getParameterMappings()) {
            String property = binding.getProperty();
            values.add(bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property)
                : configuration.newMetaObject(params).getValue(property));
        }
        return values;
    }
}
