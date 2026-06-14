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
     * 定时清理 OutputChannel 注册表中的过期条目（每 10 分钟执行一次）
     * <p>防止 Agent 异常退出时注册表泄漏</p>
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanupOutputChannelRegistry() {
        OutputChannel.cleanupStaleEntries();
    }
}
