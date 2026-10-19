package com.nexus.common.trace;

/**
 * 应用确认与Broker确认分别记录，WAITING不等于业务成功。
 *
 * @param status 解析或业务处理的明确状态。
 * @param resultPayload 目标业务首次提交的不可变结果载荷。
 */
public record TraceDeliveryResult(String status, String resultPayload) {
    public boolean applied() {
        return "APPLIED".equals(status);
    }
}
