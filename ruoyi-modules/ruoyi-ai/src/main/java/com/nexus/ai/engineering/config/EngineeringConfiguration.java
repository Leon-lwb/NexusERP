package com.nexus.ai.engineering.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 工程专用装配；关闭时不初始化维护扫描、设备认证及工程表访问。 */
@org.springframework.scheduling.annotation.EnableScheduling
@Configuration
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringConfiguration {
    /** 可替换的 UTC 时钟，方便隔离测试到期边界。 */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock engineeringClock() {
        return Clock.systemUTC();
    }
}
