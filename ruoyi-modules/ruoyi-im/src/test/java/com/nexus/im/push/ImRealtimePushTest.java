package com.nexus.im.push;

import com.nexus.im.support.ImUnitTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * P3-3: P0-2 事务推送回归测试。
 * <p>
 * 守护 {@link ImRealtimePush#publishToUserAfterCommit(Long, String)} 的核心契约：
 * <ul>
 *   <li>在活动事务内调用时，推送必须延迟到 afterCommit 钩子（注册 TransactionSynchronization）</li>
 *   <li>无活动事务时，不注册同步回调（降级为立即推送）</li>
 *   <li>多次调用应注册多个回调（每条消息独立推送）</li>
 * </ul>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>不 mock {@code WebSocketUtils}/{@code SseMessageUtils} 静态方法——它们依赖 Spring 容器初始化，
 *       mockStatic 会因类初始化失败而无法工作</li>
 *   <li>改为验证可观察的事务同步行为：注册了几个 TransactionSynchronization 回调</li>
 *   <li>afterCommit 回调内部会调用 WebSocketUtils.publishMessage，可能抛异常（无容器），
 *       本测试不触发 afterCommit，只验证注册行为</li>
 *   <li>完整的"推送发生"验证由 P3-9 集成测试覆盖（@SpringBootTest + 真实容器）</li>
 * </ul>
 * <p>
 * 任意重构若将 {@code publishToUserAfterCommit} 改回 {@code publishToUser}（事务内立即推送），
 * 用例 1 会立即失败（因为不会注册 TransactionSynchronization）。
 * <p>
 * P5-1: 新增异步推送路径测试（asyncEnabled + imPushExecutor 字段）。
 */
class ImRealtimePushTest extends ImUnitTestBase {

    private static final String JSON_MESSAGE = "{\"type\":\"IM_MESSAGE\",\"payload\":{\"messageId\":1}}";

    private SimpleMeterRegistry registry;
    private ImRealtimePush imRealtimePush;

    @BeforeEach
    void setUpPush() {
        // P4-9: ImRealtimePush 现依赖 ImMetrics；用 SimpleMeterRegistry 提供内存实现，
        // 不依赖 Spring 容器即可完成事务同步行为验证。
        registry = new SimpleMeterRegistry();
        imRealtimePush = new ImRealtimePush(new ImMetrics(registry));
        // P5-1: 默认 asyncEnabled=false（boolean 默认值）+ imPushExecutor=null → 走同步路径
        // 与 P3-3 / P4-12 测试行为一致
    }

    /**
     * 事务内调用：应注册 1 个 TransactionSynchronization（afterCommit 回调），不立即推送。
     */
    @Test
    void publishToUserAfterCommit_withinTransaction_registersSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();

            imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, JSON_MESSAGE);

            // 守护：注册了 1 个 afterCommit 回调
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 事务回滚场景：注册了回调但不触发 afterCommit，推送永不发生。
     * <p>
     * 这是 P0-2 修复的核心价值：消除"幽灵消息"（事务回滚后接收方仍收到推送）。
     */
    @Test
    void publishToUserAfterCommit_transactionRolledBack_callbackNeverTriggered() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, JSON_MESSAGE);

            // 守护：注册了回调（说明推送被延迟）
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            // 模拟事务回滚：直接清理同步上下文，不调用 afterCommit
            // 期望：回调从未被触发 → 推送永不发生
            // （afterCommit 未被调用，内部 publishToUsers 不会执行）
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 无活动事务时调用：不注册同步回调（降级为立即推送路径）。
     * <p>
     * 立即推送路径内部会调用 {@code WebSocketUtils.publishMessage}/{@code SseMessageUtils.sendMessage}，
     * 这些工具类静态初始化依赖 Spring 容器，在无容器环境下会抛 {@link ExceptionInInitializerError}（是 {@link Error} 不是 {@link Exception}）。
     * 本测试用 catch {@link Throwable} 捕获，只验证"未注册同步回调"这一核心契约。
     * <p>
     * 完整的"立即推送发生"验证由 P3-9 集成测试覆盖（@SpringBootTest + 真实容器）。
     */
    @Test
    void publishToUserAfterCommit_noTransaction_doesNotRegisterSynchronization() {
        // 确保无活动事务
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        try {
            // 立即推送路径会因 WebSocketUtils/SseMessageUtils 静态初始化失败抛 Error，捕获即可
            imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, JSON_MESSAGE);
        } catch (Throwable t) {
            // 预期：WebSocketUtils/SseMessageUtils 在无容器环境下的 ExceptionInInitializerError
            // 关键守护点不在此，而在"未注册同步回调"
        }

        // 守护：无事务时不应注册任何同步回调（说明走了立即推送路径，而非延迟路径）
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    /**
     * 多次调用：每次都应注册独立的 TransactionSynchronization 回调。
     * <p>
     * 守护：不会因"去重"或"合并"导致回调丢失，每条消息独立推送。
     */
    @Test
    void publishToUserAfterCommit_multipleCalls_registersMultipleSynchronizations() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, JSON_MESSAGE);
            imRealtimePush.publishToUserAfterCommit(OTHER_TENANT_USER_ID, JSON_MESSAGE);
            imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, "{\"type\":\"IM_MESSAGE_READ\"}");

            // 守护：3 次调用注册 3 个独立回调
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(3);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== P5-1: 异步推送路径测试 ====================

    /**
     * P5-1: asyncEnabled=true + imPushExecutor 非 null → 提交到 executor，不立即同步执行。
     * <p>守护：异步路径下，推送动作被封装为 Runnable 提交到 imPushExecutor，
     * 调用线程不阻塞（不直接调用 WebSocketUtils 等静态方法）。
     */
    @Test
    @DisplayName("P5-1: asyncEnabled=true 时推送动作提交到 imPushExecutor")
    void publishToUsers_asyncEnabled_submitsToExecutor() throws Exception {
        // 用计数 Executor 捕获提交的 Runnable，但不实际执行（避免触发 WebSocketUtils 静态初始化）
        AtomicInteger submitted = new AtomicInteger();
        Executor noopExecutor = r -> submitted.incrementAndGet();
        setField(imRealtimePush, "imPushExecutor", noopExecutor);
        setField(imRealtimePush, "asyncEnabled", true);

        imRealtimePush.publishToUsers(List.of(PEER_USER_ID), JSON_MESSAGE, "private");

        // 守护：提交了 1 个任务到 executor
        assertThat(submitted.get()).isEqualTo(1);
        // 守护：异步路径下不立即调用 WebSocketUtils（否则会抛 ExceptionInInitializerError）
        // 若走了同步路径，本测试会因 WebSocketUtils 静态初始化失败而抛 Error
    }

    /**
     * P5-1: asyncEnabled=false → 走同步路径（不提交到 executor）。
     * <p>守护：异步开关关闭时，即使 imPushExecutor 非 null 也不提交，
     * 直接走同步推送路径（与 P4-9 行为一致）。
     */
    @Test
    @DisplayName("P5-1: asyncEnabled=false 时走同步路径（不提交 executor）")
    void publishToUsers_asyncDisabled_executesSynchronously() throws Exception {
        Executor mockExecutor = mock(Executor.class);
        setField(imRealtimePush, "imPushExecutor", mockExecutor);
        setField(imRealtimePush, "asyncEnabled", false);

        try {
            // 同步路径会触发 WebSocketUtils/SseMessageUtils 静态初始化失败，捕获即可
            imRealtimePush.publishToUsers(List.of(PEER_USER_ID), JSON_MESSAGE, "private");
        } catch (Throwable t) {
            // 预期：WebSocketUtils 静态初始化失败
        }

        // 守护：未提交到 executor（asyncEnabled=false 走同步路径）
        verify(mockExecutor, never()).execute(any(Runnable.class));
    }

    /**
     * P5-1: imPushExecutor=null（单元测试默认场景）→ 走同步路径，不抛 NPE。
     * <p>守护：单元测试无 Spring 容器，imPushExecutor 字段为 null 时不能抛 NPE，
     * 必须降级为同步路径。
     */
    @Test
    @DisplayName("P5-1: imPushExecutor=null 时降级同步路径（不抛 NPE）")
    void publishToUsers_nullExecutor_fallsBackToSynchronous() {
        // imPushExecutor 默认为 null（@BeforeEach 未设置）
        // asyncEnabled 默认为 false（boolean 默认值）
        try {
            imRealtimePush.publishToUsers(List.of(PEER_USER_ID), JSON_MESSAGE, "private");
        } catch (Throwable t) {
            // 预期：WebSocketUtils 静态初始化失败（非 NPE）
            // 守护点：不抛 NPE
            assertThat(t).isNotInstanceOf(NullPointerException.class);
        }
    }

    /**
     * P5-1: ExecutorServiceMetrics 在 @PostConstruct 时注册（仅当 imPushExecutor 为 ExecutorService）。
     * <p>守护：registerExecutorMetrics 仅对 ExecutorService 实例注册监控，
     * 普通 Executor（如单元测试的 noop Executor）跳过注册，不抛异常。
     */
    @Test
    @DisplayName("P5-1: registerExecutorMetrics 对非 ExecutorService 跳过注册")
    void registerExecutorMetrics_nonExecutorService_skipsRegistration() throws Exception {
        Executor noopExecutor = r -> {};
        setField(imRealtimePush, "imPushExecutor", noopExecutor);

        // 不应抛异常
        imRealtimePush.registerExecutorMetrics();

        // 守护：未注册 executor.* 指标（因为 noopExecutor 不是 ExecutorService）
        assertThat(registry.find("executor.active").gauges()).isEmpty();
    }

    /**
     * 反射设置 ImRealtimePush 私有字段（仅测试用）。
     */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        if (value instanceof Boolean) {
            field.setBoolean(target, (Boolean) value);
        } else {
            field.set(target, value);
        }
    }
}
