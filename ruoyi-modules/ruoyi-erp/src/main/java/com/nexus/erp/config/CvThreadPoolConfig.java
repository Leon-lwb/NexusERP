package com.nexus.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 视觉识别专用线程池配置
 * <p>用于打卡人脸验证等视觉异步任务,与 {@code financialExecutor}、{@code aiExecutor} 隔离,
 * 避免视觉调用(单次 ~350ms)阻塞财务/订单等核心异步链路。</p>
 * <p>不引用 ruoyi-ai 的 aiExecutor(避免破坏 erp→ai 模块边界);Phase 1 不引入 MQ。</p>
 *
 * <p>异步线程不传播 Sa-Token ThreadLocal,租户上下文必须由调用方用
 * {@code TenantHelper.dynamic(tenantId, ...)} 显式恢复(见 AI 异步租户上下文坑)。</p>
 *
 * @author nexus
 * @since 2026-08-02
 */
@Slf4j
@Configuration
public class CvThreadPoolConfig {

    /**
     * 视觉任务专用线程池
     * <p>核心 2 / 最大 8 / 队列 100,CallerRunsPolicy 兜底(队列满时退化为调用线程执行,不丢任务)</p>
     *
     * @return 线程池执行器
     */
    @Bean("cvExecutor")
    public Executor cvExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cv-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
