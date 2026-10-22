package com.nexus.ai.config;

import com.nexus.ai.observability.OutputChannel;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 专用线程池配置
 * <p>避免 CompletableFuture.runAsync() 使用 ForkJoinPool.commonPool() 耗尽公共线程</p>
 *
 * @author NexusERP
 */
@Slf4j
@Configuration
public class AiThreadPoolConfig {

    /**
     * AI 任务专用线程池
     *
     * @return 线程池执行器
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(AiThreadPoolConfig::runOnCallerUnlessShutdown);
        executor.initialize();
        return executor;
    }

    /**
     * 满载时在调用线程施加背压，关闭时明确拒绝，禁止 CallerRunsPolicy 静默丢弃已登记业务任务。
     *
     * @param task 需要执行的业务任务
     * @param executor 当前拒绝任务的线程池
     * @throws RejectedExecutionException 线程池已关闭，任务未被接收
     */
    private static void runOnCallerUnlessShutdown(Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("AI 任务线程池已关闭");
        }
        task.run();
    }

    /**
     * 流程并行分支专用线程池（波次执行）
     *
     * <p>独立于 aiExecutor（其 CallerRunsPolicy 会导致波次 await 死锁——池满时任务由提交线程执行， 而提交线程正在 await 波次完成）。固定大小 =
     * parallelWorkers，任务排队而非拒绝。
     *
     * @return 线程池执行器
     */
    @Bean("flowExecutor")
    public ExecutorService flowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("flow-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * AI 模型连接测试专用线程池（与 {@code aiExecutor} 严格隔离）
     *
     * <p>连接测试的超时护栏依赖 {@code CompletableFuture.get(timeout)} 等待：{@code cancel(true)} 无法中断底层 HTTP
     * 调用，线程会一直占用到上游自身读超时（最长约 180 秒）。 若复用 {@code aiExecutor}（core4/max16，与对话/Agent/知识图谱等生产流量共享），
     * 慢上游会长时间占满生产线程。本池 core2/max4 + queueCapacity 0（SynchronousQueue） + {@link
     * ThreadPoolExecutor.AbortPolicy}：线程耗尽时立即拒绝，由 {@code AiModelConnectionTestService} 捕获 {@link
     * RejectedExecutionException} 转为"连接测试繁忙"失败结果，绝不排队放大等待。
     *
     * @return 连接测试线程池执行器
     */
    @Bean("aiConnectionTestExecutor")
    public Executor aiConnectionTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ai-conn-test-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 知识库向量索引重建专用线程池（与 {@code aiExecutor} 严格隔离）
     *
     * <p>重建 = 全量 DROP + 批量重嵌入，单任务可达分钟级；若复用 {@code aiExecutor} （CallerRunsPolicy），队列满时重建任务会退回 HTTP
     * 提交线程同步执行，长时间阻塞管理端 请求并挤占对话线程。本池 core1/max2 + queueCapacity 0（SynchronousQueue）+ {@link
     * ThreadPoolExecutor.AbortPolicy}（同 {@code aiConnectionTestExecutor} 模式）： 线程耗尽时立即拒绝，由 {@code
     * KnowledgeIndexRebuildService} 捕获 {@link RejectedExecutionException} 将知识库标记为 FAILED，
     * 管理员可重试，绝不退回提交线程执行。
     *
     * @return 向量索引重建线程池执行器
     */
    @Bean("aiIndexRebuildExecutor")
    public Executor aiIndexRebuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ai-index-rebuild-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** 工业决策专用有界线程池；满载时拒绝，不回退到 HTTP 请求线程。 */
    @Bean("decisionExecutor")
    public Executor decisionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-decision-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 定时清理 OutputChannel 注册表中的过期条目（每 10 分钟执行一次）
     * <p>防止 Agent 异常退出时注册表泄漏</p>
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanupOutputChannelRegistry() {
        OutputChannel.cleanupStaleEntries();
    }

    /** 治理任务不使用CallerRuns，排队饱和由调用方落失败状态。 */
    private ExecutorService bounded(String name, int core, int max, int capacity) {
        var count = new AtomicInteger();
        return new ThreadPoolExecutor(
                core,
                max,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity),
                task -> new Thread(task, name + count.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(value = "aiChatExecutor", destroyMethod = "shutdown")
    public ExecutorService aiChatExecutor() {
        return bounded("ai-chat-", 4, 16, 100);
    }

    @Bean(value = "aiAbExecutor", destroyMethod = "shutdown")
    public ExecutorService aiAbExecutor() {
        return bounded("ai-ab-", 4, 4, 32);
    }

    @Bean(value = "flowCoordinatorExecutor", destroyMethod = "shutdown")
    public ExecutorService flowCoordinatorExecutor() {
        return bounded("flow-coordinator-", 4, 4, 100);
    }

    @Bean(value = "flowNodeExecutor", destroyMethod = "shutdown")
    public ExecutorService flowNodeExecutor() {
        return bounded("flow-node-", 4, 16, 128);
    }

    @Bean(value = "aiEventExecutor", destroyMethod = "shutdown")
    public ExecutorService aiEventExecutor() {
        return bounded("ai-events-", 4, 8, 128);
    }
}
