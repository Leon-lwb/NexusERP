package com.nexus.common.mq.constant;

/**
 * 交换机常量
 *
 * @author Nexus
 */
public interface ExchangeConstants {
    String DIRECT_EXCHANGE = "erp.direct.exchange";
    String TOPIC_EXCHANGE = "erp.topic.exchange";
    String DELAY_EXCHANGE = "erp.delay.exchange";
    String DLX_EXCHANGE = "erp.dlx.exchange";
    String TRACE_EXCHANGE = "trace.v1.exchange";
    String TRACE_DEAD_EXCHANGE = "trace.v1.dead.exchange";
}
