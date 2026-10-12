package com.nexus.system.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.system.domain.SysNotice;
import com.nexus.system.domain.SysUser;
import com.nexus.system.domain.bo.SysNoticeBo;
import com.nexus.system.mapper.SysNoticeMapper;
import com.nexus.system.mapper.SysUserMapper;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 公告发布安全回归：运行真实服务与事务拦截器，数据库和通知投递均使用内存替身。
 */
@ExtendWith(MockitoExtension.class)
class SysNoticeServiceImplNotificationTest {

    private static final Long NOTICE_ID = 2109242152783400961L;
    private static final String NOTICE_TENANT = "tenant-notice";

    @Mock
    private SysNoticeMapper baseMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private SysNoticeServiceImpl target;

    private SysNoticeServiceImpl service;
    private MemoryTransactionManager transactionManager;
    private final List<UserNotificationEvent> delivered = new ArrayList<>();

    @BeforeAll
    static void initializeMappingAndMetadata() throws ClassNotFoundException {
        // 静态工具只注入真实转换器和 JSON 编码器，不启动应用容器或外部服务。
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(new Converter());
            spring.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            Class.forName(MapstructUtils.class.getName());
            Class.forName(JsonUtils.class.getName());
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "notice-tests");
        TableInfoHelper.initTableInfo(assistant, SysNotice.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @BeforeEach
    void createTransactionalService() {
        transactionManager = new MemoryTransactionManager();
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        TransactionInterceptor transactionAdvice = new TransactionInterceptor();
        transactionAdvice.setTransactionManager(transactionManager);
        transactionAdvice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(transactionAdvice);
        service = (SysNoticeServiceImpl) proxy.getProxy();
    }

    @Test
    void publishesToEnabledUsersOfPersistedNoticeTenantWithExactNoticeId() throws Exception {
        SysNotice saved = notice("0");
        stubInsert(saved, 1);
        when(userMapper.selectList(any())).thenReturn(List.of(new SysUser(101L), new SysUser(102L)));
        captureAfterCommit();
        SysNoticeBo request = request(null);

        assertEquals(1, service.insertNotice(request));

        ArgumentCaptor<LambdaQueryWrapper<SysUser>> recipients = userQueryCaptor();
        verify(userMapper).selectList(recipients.capture());
        LambdaQueryWrapper<SysUser> query = recipients.getValue();
        assertEquals(NOTICE_TENANT, queryValue(query, "tenant_id"), "必须显式限制公告所属租户");
        assertEquals("0", queryValue(query, "status"), "停用账号不得接收公告");
        assertEquals("0", queryValue(query, "del_flag"), "已删除账号不得接收公告");
        assertEquals(2, delivered.size());
        assertEquals(List.of(101L, 102L), delivered.stream().map(UserNotificationEvent::getTargetUserId).toList());
        for (UserNotificationEvent event : delivered) {
            assertEquals(NOTICE_TENANT, event.getTenantId());
            assertEquals("sys_notice", event.getCategory());
            assertEquals("sys_notice", event.getRefType());
            assertEquals(NOTICE_ID.toString(), event.getRefId());
            assertEquals(saved.getNoticeTitle(), event.getTitle());
            assertEquals(saved.getNoticeContent(), event.getContent());
            Map<?, ?> extra = new ObjectMapper().readValue(event.getExtJson(), Map.class);
            assertEquals(NOTICE_ID.toString(), extra.get("noticeId"), "跳转参数不得丢失雪花 ID 精度");
        }
        assertEquals(1, transactionManager.commits);
    }

    @Test
    void doesNotNotifyWhenNewNoticeIsClosed() {
        stubInsert(notice("1"), 1);

        assertEquals(1, service.insertNotice(request("1")));

        verifyNoInteractions(userMapper, eventPublisher);
    }

    @Test
    void doesNotNotifyWhenInsertAffectsNoRows() {
        stubInsert(notice("0"), 0);

        assertEquals(0, service.insertNotice(request("0")));

        verifyNoInteractions(userMapper, eventPublisher);
    }

    @Test
    void refusesPublishedNoticeWithoutPersistedTenant() {
        SysNotice saved = notice("0");
        saved.setTenantId(" ");
        stubInsert(saved, 1);

        assertThrows(ServiceException.class, () -> service.insertNotice(request("0")));

        verifyNoInteractions(userMapper, eventPublisher);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void refusesUnreadablePersistedNoticeInsteadOfGuessingItsTenant() {
        stubInsert(null, 1);

        assertThrows(ServiceException.class, () -> service.insertNotice(request("0")));

        verifyNoInteractions(userMapper, eventPublisher);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void publishesWhenClosedNoticeBecomesEnabled() {
        stubUpdate(notice("1"), notice("0"), 1);
        when(userMapper.selectList(any())).thenReturn(List.of(new SysUser(101L)));
        captureAfterCommit();

        assertEquals(1, service.updateNotice(request("0")));

        assertEquals(1, delivered.size());
        ArgumentCaptor<Wrapper<SysNotice>> locked = noticeQueryCaptor();
        verify(baseMapper).selectOne(locked.capture());
        assertTrue(locked.getValue().getSqlSegment().contains("FOR UPDATE"), "发布状态必须串行判断，防止并发重复通知");
    }

    @Test
    void doesNotRepublishWhenEditingAnAlreadyEnabledNotice() {
        stubUpdate(notice("0"), notice("0"), 1);

        assertEquals(1, service.updateNotice(request("0")));

        verifyNoInteractions(userMapper, eventPublisher);
    }

    @Test
    void doesNotRepublishWhenEditingWithoutChangingStatus() {
        stubUpdate(notice("0"), notice("0"), 1);

        assertEquals(1, service.updateNotice(request(null)));

        verifyNoInteractions(userMapper, eventPublisher);
    }

    @Test
    void doesNotNotifyWhenClosingOrEditingAClosedNotice() {
        stubUpdate(notice("0"), notice("1"), 1);

        assertEquals(1, service.updateNotice(request("1")));

        verifyNoInteractions(userMapper, eventPublisher);
    }

    @Test
    void doesNotNotifyWhenUpdateAffectsNoRows() {
        stubUpdate(notice("1"), notice("0"), 0);

        assertEquals(0, service.updateNotice(request("0")));

        verifyNoInteractions(userMapper, eventPublisher);
    }

    @Test
    void doesNotUpdateAnInaccessibleNotice() {
        stubUpdate(null, notice("0"), 1);

        assertEquals(0, service.updateNotice(request("0")));

        verify(baseMapper, never()).updateById(any(SysNotice.class));
        verifyNoInteractions(userMapper, eventPublisher);
    }

    @Test
    void emptyRecipientListDoesNotPublishAnyEvent() {
        stubInsert(notice("0"), 1);
        lenient().when(userMapper.selectList(any())).thenReturn(List.of());

        assertEquals(1, service.insertNotice(request("0")));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rollsBackIfPublishingFailsAndDoesNotDeliverEarlierEvents() {
        stubInsert(notice("0"), 1);
        when(userMapper.selectList(any())).thenReturn(List.of(new SysUser(101L), new SysUser(102L)));
        captureAfterCommit();
        doThrow(new IllegalStateException("模拟通知失败")).when(eventPublisher)
            .publishEvent(argThat((Object value) -> value instanceof UserNotificationEvent event
                && Long.valueOf(102L).equals(event.getTargetUserId())));

        assertThrows(IllegalStateException.class, () -> service.insertNotice(request("0")));

        assertTrue(delivered.isEmpty(), "回滚事务不得执行任何提交后投递");
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void doesNotDeliverWhenOuterBusinessTransactionRollsBack() {
        stubInsert(notice("0"), 1);
        when(userMapper.selectList(any())).thenReturn(List.of(new SysUser(101L)));
        captureAfterCommit();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.insertNotice(request("0"));
            assertTrue(delivered.isEmpty(), "业务事务提交前不得投递公告");
            status.setRollbackOnly();
        });

        verify(eventPublisher).publishEvent(any(UserNotificationEvent.class));
        assertTrue(delivered.isEmpty());
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    private void stubInsert(SysNotice saved, int rows) {
        when(baseMapper.insert(any(SysNotice.class))).thenAnswer(invocation -> {
            SysNotice inserted = invocation.getArgument(0);
            inserted.setNoticeId(NOTICE_ID);
            return rows;
        });
        lenient().when(baseMapper.selectById(NOTICE_ID)).thenReturn(saved);
    }

    private void stubUpdate(SysNotice before, SysNotice after, int rows) {
        lenient().when(baseMapper.selectOne(any())).thenReturn(before);
        lenient().when(baseMapper.selectById(NOTICE_ID)).thenReturn(after);
        lenient().when(baseMapper.updateById(any(SysNotice.class))).thenReturn(rows);
    }

    private void captureAfterCommit() {
        doAnswer(invocation -> {
            UserNotificationEvent event = invocation.getArgument(0);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "公告保存与通知事件必须共用事务");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivered.add(event);
                }
            });
            return null;
        }).when(eventPublisher).publishEvent(any(UserNotificationEvent.class));
    }

    private static SysNotice notice(String status) {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(NOTICE_ID);
        notice.setTenantId(NOTICE_TENANT);
        notice.setStatus(status);
        notice.setNoticeType("2");
        notice.setNoticeTitle("生产安排公告");
        notice.setNoticeContent("<p>请确认生产安排。</p>");
        return notice;
    }

    private static SysNoticeBo request(String status) {
        SysNoticeBo request = new SysNoticeBo();
        request.setNoticeId(NOTICE_ID);
        request.setNoticeTitle("生产安排公告");
        request.setNoticeContent("<p>请确认生产安排。</p>");
        request.setNoticeType("2");
        request.setStatus(status);
        return request;
    }

    private static Object queryValue(LambdaQueryWrapper<SysUser> query, String column) {
        Matcher matcher = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}")
            .matcher(query.getSqlSegment());
        assertTrue(matcher.find(), "缺少接收方过滤条件：" + column);
        return query.getParamNameValuePairs().get(matcher.group(1));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<LambdaQueryWrapper<SysUser>> userQueryCaptor() {
        return ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Wrapper<SysNotice>> noticeQueryCaptor() {
        return ArgumentCaptor.forClass((Class) Wrapper.class);
    }

    /** 只维护事务状态和提交计数，不创建数据库连接或执行 SQL。 */
    private static final class MemoryTransactionManager extends AbstractPlatformTransactionManager {
        private final MemoryTransaction transaction = new MemoryTransaction();
        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return transaction;
        }

        @Override
        protected boolean isExistingTransaction(Object current) {
            return ((MemoryTransaction) current).active;
        }

        @Override
        protected void doBegin(Object current, TransactionDefinition definition) {
            transaction.active = true;
            transaction.rollbackOnly = false;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            transaction.rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object current) {
            transaction.active = false;
        }
    }

    private static final class MemoryTransaction implements SmartTransactionObject {
        private boolean active;
        private boolean rollbackOnly;

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public void flush() {
        }
    }
}
