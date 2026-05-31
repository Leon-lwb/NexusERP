package com.nexus.common.mq.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 消息基类
 *
 * @author Nexus
 */
@Data
public abstract class BaseMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;
    private Long timestamp;
    private String traceId;
    private Long tenantId;
    private String sourceModule;
    private Integer retryCount = 0;
    private Integer maxRetry = 3;
}
