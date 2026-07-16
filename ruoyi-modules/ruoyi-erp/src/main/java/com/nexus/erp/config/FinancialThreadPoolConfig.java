package com.nexus.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 财务专用线程池配置
 * <p>用于开票/核销/电票等财务异步任务(如 {@code SalesOrderFulfilledEventListener} 自动生成发票草稿、
 * 后续 P3 {@code PayReceivedConsumer} 自动核销),与 {@code aiExecutor} 隔离,
 * 避免 AI 长任务阻塞财务实时性要求高的核销/回调处理。</p>
 *
 * <p>异步线程不传播 Sa-Token ThreadLocal,租户上下文必须由调用方用
 * {@code TenantHelper.dynamic(tenantId, ...)} 显式恢复(见 AI 异步租户上下文坑)。</p>
 *
 * @author nexus
 * @since 2026-07-16
 */
@Slf4j
@Configuration
public class FinancialThreadPoolConfig {

    /**
     * 财务任务专用线程池
     *
     * @return 线程池执行器
     */
    @Bean("financialExecutor")
    public Executor financialExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("financial-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
