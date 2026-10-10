package com.nexus.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** AI 任务拒绝策略回归：保留满载退回调用线程，关闭时禁止静默丢弃任务。 */
class AiThreadPoolConfigRejectionTest {

    @Test
    void shutdownExecutorExplicitlyRejectsRatherThanSilentlyDroppingTask() {
        var executor = (ThreadPoolTaskExecutor) new AiThreadPoolConfig().aiExecutor();
        executor.shutdown();
        var ran = new AtomicBoolean();
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> ran.set(true)));
        assertFalse(ran.get());
    }

    @Test
    void saturatedRunningExecutorKeepsCallerRunsBackpressure() throws Exception {
        var configured = (ThreadPoolTaskExecutor) new AiThreadPoolConfig().aiExecutor();
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), configured.getThreadPoolExecutor().getRejectedExecutionHandler());
        try {
            executor.execute(() -> {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            executor.execute(() -> { });
            var submittingThread = Thread.currentThread();
            var ran = new AtomicBoolean();
            executor.execute(() -> { assertSame(submittingThread, Thread.currentThread()); ran.set(true); });
            assertTrue(ran.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            configured.shutdown();
        }
    }
}
