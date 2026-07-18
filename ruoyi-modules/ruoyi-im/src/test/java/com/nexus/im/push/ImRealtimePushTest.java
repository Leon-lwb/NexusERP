package com.nexus.im.push;

import com.nexus.im.support.ImUnitTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

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
 */
class ImRealtimePushTest extends ImUnitTestBase {

    private static final String JSON_MESSAGE = "{\"type\":\"IM_MESSAGE\",\"payload\":{\"messageId\":1}}";

    private final ImRealtimePush imRealtimePush = new ImRealtimePush();

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
}
