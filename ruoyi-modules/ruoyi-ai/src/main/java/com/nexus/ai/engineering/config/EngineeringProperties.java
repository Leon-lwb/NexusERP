package com.nexus.ai.engineering.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 工程功能配置；默认关闭，启用必须指定专用私桶配置键。 */
@Data
@Component
@ConfigurationProperties(prefix = "nexus.ai.engineering")
public class EngineeringProperties {
    /** 显式启用开关。 */
    private boolean enabled = false;

    /** 专用私有 OSS 配置键，禁止隐式回落默认公共桶。 */
    private String ossConfigKey;

    /** 等待设备时限。 */
    private Duration waitingTimeout = Duration.ofMinutes(15);

    /** 单任务执行截止时间。 */
    private Duration executionTimeout = Duration.ofMinutes(20);

    /** 租约时长。 */
    private Duration leaseDuration = Duration.ofSeconds(90);

    /** 访问凭证有效期。 */
    private Duration accessDuration = Duration.ofMinutes(15);

    /** 刷新凭证有效期。 */
    private Duration refreshDuration = Duration.ofDays(7);

    /** 单文件最大字节数。 */
    private long maxArtifactBytes = 100L * 1024 * 1024;

    /** 单任务最大字节数。 */
    private long maxTaskBytes = 300L * 1024 * 1024;

    /** 单任务最大文件数。 */
    private int maxFiles = 64;
}
