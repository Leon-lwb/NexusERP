package com.nexus.common.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RabbitMQ 配置项
 *
 * @author Nexus
 */
@Data
@ConfigurationProperties(prefix = "mq.rabbit")
public class RabbitMqProperties {

    private boolean enabled = true;
    private String exchangePrefix = "erp";
    private boolean messageRecordEnabled = true;
    private int maxRetry = 3;
    private long retryInitialInterval = 1000L;
    private long retryMultiplier = 2;
    private long retryMaxInterval = 16000L;
    private long compensationScanInterval = 60000L;
    private long compensationResendThreshold = 60000L;
}
