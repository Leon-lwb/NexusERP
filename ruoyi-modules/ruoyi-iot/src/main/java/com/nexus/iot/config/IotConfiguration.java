package com.nexus.iot.config;

import com.nexus.common.iot.service.IMesAndonService;
import com.nexus.iot.common.IotMesAndonServiceNoop;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * IoT 模块配置。
 * <p>
 * 注册 MES 联动接口的默认 no-op 实现：当 {@code ruoyi-mes} 未提供 {@link IMesAndonService}
 * 真实 Bean 时生效；MES Phase 2 提供 {@code @Primary @Service} 实现后自动让位。
 * <p>
 * 启用 {@link EnableScheduling} 供 {@code IotRealtimeSseManager} 的定时推送使用，
 * 并提供独立 {@link ThreadPoolTaskScheduler} 避免占用全局调度池。
 *
 * @author nexus
 */
@EnableScheduling
@Configuration
public class IotConfiguration {

    @Bean
    @ConditionalOnMissingBean(IMesAndonService.class)
    public IMesAndonService iotMesAndonService() {
        return new IotMesAndonServiceNoop();
    }

    /**
     * IoT 专用调度器，承载 SSE 实时推送等定时任务。
     */
    @Bean(name = "iotTaskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler iotTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("iot-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }

}
