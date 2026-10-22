package com.nexus.im.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.mapper.SysUserNotificationMapper;
import com.nexus.im.push.ImMetrics;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.service.impl.SysUserNotificationServiceImpl;
import com.nexus.im.service.impl.support.ImConversationCache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/** 真正发布 Spring 事件并经过事务代理，持久化和网络端只使用内存替身。 */
class UserNotificationEventListenerTest {
    private MockedStatic<LoginHelper> login;
    private MockedStatic<TenantHelper> tenant;
    private AnnotationConfigApplicationContext context;
    private ISysUserNotificationService service;
    private RecordingTransactions transactions;
    private EventPublisher target;
    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isLogin).thenReturn(false);
        login.when(LoginHelper::getTenantId).thenReturn("request-tenant");
        // 保留真正的 dynamic/finally 实现，验证异常路径恢复线程内租户。
        tenant = mockStatic(TenantHelper.class, CALLS_REAL_METHODS);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        service = mock(ISysUserNotificationService.class);
        transactions = new RecordingTransactions();
        startContext(service);
    }

    @AfterEach
    void tearDown() {
        try {
            if (context != null) context.close();
            TenantHelper.clearDynamic();
        } finally {
            tenant.close();
            login.close();
        }
    }

    @Test
    void serviceFailureDuringEventPublication_rollsBackBeforePublisherContinues() {
        var failure = new DataAccessResourceFailureException("测试通知写入失败");
        doThrow(failure).when(service).handleUserNotificationEvent(any(UserNotificationEvent.class));

        assertThatThrownBy(() -> publisher.publish(event(null)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("通知保存失败，请稍后重试")
            .hasCause(failure);

        assertThat(target.continued).isZero();
        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(target.committedBusinessWrites).isZero();
    }

    @Test
    void eventTenantFailure_propagatesAndDoesNotLeakIntoRequestTenant() {
        // 嵌套动态租户恢复由 common 的专用回归覆盖，此处验证事件失败不泄漏事件租户。
        String requestTenant = TenantHelper.getTenantId();
        var failure = new DataAccessResourceFailureException("测试异常");
        doAnswer(call -> {
            assertThat(TenantHelper.getTenantId()).isEqualTo("event-tenant");
            throw failure;
        }).when(service).handleUserNotificationEvent(any(UserNotificationEvent.class));

        assertThatThrownBy(() -> publisher.publish(event("event-tenant")))
            .isInstanceOf(ServiceException.class).hasCause(failure);

        assertThat(TenantHelper.getTenantId()).isEqualTo(requestTenant);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(target.continued).isZero();
    }

    @Test
    void businessFailure_isPropagatedUnchangedThroughRealEventListener() {
        var failure = new ServiceException("通知接收方无效");
        doThrow(failure).when(service).handleUserNotificationEvent(any(UserNotificationEvent.class));

        assertThatThrownBy(() -> context.publishEvent(event(null))).isSameAs(failure);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void realTransactionalNotificationService_failureAbortsPublishingTransaction() {
        var mapper = mock(SysUserNotificationMapper.class);
        var failure = new DataAccessResourceFailureException("测试持久化异常");
        when(mapper.insert(any(SysUserNotification.class))).thenThrow(failure);
        var push = mock(ImRealtimePush.class);
        var realService = new SysUserNotificationServiceImpl(mapper, push, mock(ImConversationCache.class));
        // 通知服务同样经过 REQUIRED 事务代理，验证加入发布方事务后的失败传播。
        startContext(transactionProxy(realService, ISysUserNotificationService.class));

        assertThatThrownBy(() -> publisher.publish(event("event-tenant")))
            .isInstanceOf(ServiceException.class).hasCause(failure);

        assertThat(transactions.begins).isEqualTo(1);
        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(target.committedBusinessWrites).isZero();
        assertThat(target.continued).isZero();
        verify(push, never()).publishToUserAfterCommit(any(), any());
    }

    @Test
    void successfulEvent_deliversOnlyAfterPublishingTransactionCommits() {
        var push = spy(new ImRealtimePush(new ImMetrics(new SimpleMeterRegistry())));
        doAnswer(call -> {
            assertThat(transactions.commits).isEqualTo(1);
            return null;
        }).when(push).publishToUsers(anyList(), anyString(), anyString());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TenantHelper.getTenantId()).isEqualTo("event-tenant");
            push.publishToUserAfterCommit(100L, "测试通知");
            verify(push, never()).publishToUsers(anyList(), anyString(), anyString());
            return null;
        }).when(service).handleUserNotificationEvent(any(UserNotificationEvent.class));

        publisher.publish(event("event-tenant"));

        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
        assertThat(target.committedBusinessWrites).isEqualTo(1);
        assertThat(TenantHelper.getTenantId()).isEqualTo("request-tenant");
        verify(push, times(1)).publishToUsers(List.of(100L), "测试通知", "other");
    }

    @Test
    void publishingTransactionRollsBackAfterEvent_doesNotDeliverRegisteredNotification() {
        var push = spy(new ImRealtimePush(new ImMetrics(new SimpleMeterRegistry())));
        doNothing().when(push).publishToUsers(anyList(), anyString(), anyString());
        doAnswer(call -> {
            push.publishToUserAfterCommit(100L, "待提交通知");
            return null;
        }).when(service).handleUserNotificationEvent(any(UserNotificationEvent.class));

        assertThatThrownBy(() -> publisher.publishThenFail(event("event-tenant")))
            .isInstanceOf(ServiceException.class).hasMessage("发布方后续处理失败");

        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(target.committedBusinessWrites).isZero();
        verify(push, never()).publishToUsers(anyList(), anyString(), anyString());
    }

    private UserNotificationEvent event(String tenantId) {
        var event = new UserNotificationEvent();
        event.setTenantId(tenantId);
        event.setTargetUserId(100L);
        event.setCategory("sys_notice");
        event.setTitle("测试公告");
        event.setContent("测试通知正文");
        return event;
    }

    private void startContext(ISysUserNotificationService notificationService) {
        if (context != null) context.close();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(UserNotificationEventListener.class, () -> new UserNotificationEventListener(notificationService));
        context.refresh();
        target = new EventPublisher(context);
        publisher = transactionProxy(target, EventPublisher.class);
    }

    @SuppressWarnings("deprecation")
    private <T> T transactionProxy(Object bean, Class<T> type) {
        var factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }

    /** 模拟同事务公告或好友申请事件发布，不直接调用监听器。 */
    static class EventPublisher {
        private final ApplicationEventPublisher events;
        int continued;
        int committedBusinessWrites;

        EventPublisher(ApplicationEventPublisher events) { this.events = events; }

        @Transactional(rollbackFor = Exception.class)
        public void publish(UserNotificationEvent event) {
            recordBusinessWrite();
            events.publishEvent(event);
            continued++;
        }

        @Transactional(rollbackFor = Exception.class)
        public void publishThenFail(UserNotificationEvent event) {
            recordBusinessWrite();
            events.publishEvent(event);
            throw new ServiceException("发布方后续处理失败");
        }

        private void recordBusinessWrite() {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { committedBusinessWrites++; }
            });
        }
    }

    /** 真实 Spring 事务同步与 REQUIRED 嵌套语义，仅底层资源留在内存。 */
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        private final ThreadLocal<MemoryTransaction> current = new ThreadLocal<>();
        int begins;
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            var existing = current.get();
            return existing == null ? new MemoryTransaction() : existing;
        }
        @Override
        protected boolean isExistingTransaction(Object tx) { return ((MemoryTransaction) tx).active; }
        @Override
        protected void doBegin(Object tx, TransactionDefinition definition) {
            ((MemoryTransaction) tx).active = true;
            current.set((MemoryTransaction) tx);
            begins++;
        }
        @Override
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((MemoryTransaction) status.getTransaction()).rollbackOnly = true;
        }
        @Override
        protected void doCleanupAfterCompletion(Object tx) { current.remove(); }
    }

    static class MemoryTransaction implements SmartTransactionObject {
        boolean active;
        boolean rollbackOnly;
        @Override
        public boolean isRollbackOnly() { return rollbackOnly; }
        @Override
        public void flush() { }
    }
}
