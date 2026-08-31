package com.nexus.im.push;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * P5-1: IM 推送异步执行器配置。
 * <p>
 * P4-1 群消息扇出通过「批量 INSERT + afterCommit 钩子」优化了 DB 路径，
 * 但 afterCommit 钩子内的 {@code publishToUsers} 仍由 Spring 事务同步线程
 * 同步执行，慢消费者（网络抖动 / SSE 阻塞）会拖累事务提交线程。
 * <p>
 * P5-1 引入 {@code imPushExecutor} 独立线程池，将推送动作异步化：
 * <ul>
 *   <li>corePoolSize=8 / maxPoolSize=32 / queueCapacity=2000</li>
 *   <li>拒绝策略 {@link ThreadPoolExecutor.CallerRunsPolicy}：线程池满时由调用线程同步执行（背压传导，不丢消息）</li>
 *   <li>线程名前缀 {@code im-push-}，便于线程 dump 定位</li>
 * </ul>
 * <p>
 * 监控指标（由 {@link ImMetrics#registerPushExecutor} 注册）：
 * <ul>
 *   <li>{@code im.push.executor.active} (Gauge): 活跃线程数</li>
 *   <li>{@code im.push.executor.queue.size} (Gauge): 线程池队列大小</li>
 *   <li>{@code im.push.executor.completed} (Counter): 累计完成任务数</li>
 *   <li>{@code im.push.executor.rejected} (Counter): CallerRunsPolicy 触发次数</li>
 * </ul>
 *
 * @author nexus
 */
@Configuration
public class ImPushExecutorConfig {

    /**
     * IM 推送专用线程池。
     * <p>不与业务线程池混用，避免推送慢任务拖累其他业务。
     * <p>注入方式：{@code @Qualifier("imPushExecutor") Executor executor}
     */
    @Bean("imPushExecutor")
    @ConditionalOnMissingBean(name = "imPushExecutor")
    public Executor imPushExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(8);
        exec.setMaxPoolSize(32);
        exec.setQueueCapacity(2000);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("im-push-");
        // 线程池满时由调用线程同步执行（背压传导至调用方，不丢消息）
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 应用关闭时等待已提交任务完成，避免推送丢失
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(10);
        exec.initialize();
        return exec;
    }
}

