package com.nexus.ai.config;

import com.nexus.ai.observability.OutputChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 流程并行分支专用线程池（波次执行）
     * <p>独立于 aiExecutor（其 CallerRunsPolicy 会导致波次 await 死锁——池满时任务由提交线程执行，
     * 而提交线程正在 await 波次完成）。固定大小 = parallelWorkers，任务排队而非拒绝。</p>
     *
     * @return 线程池执行器
     */
    @Bean("flowExecutor")
    public java.util.concurrent.ExecutorService flowExecutor() {
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
     * 定时清理 OutputChannel 注册表中的过期条目（每 10 分钟执行一次）
     * <p>防止 Agent 异常退出时注册表泄漏</p>
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanupOutputChannelRegistry() {
        OutputChannel.cleanupStaleEntries();
    }
}
