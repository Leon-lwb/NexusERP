package com.nexus.common.report;

/** 订阅只发布最小站内摘要，适配器必须在同一主库事务内持久化并在提交后推送。 */
public interface ReportDeliveryNotificationPort {
    /**
     * 按不可变deliveryId幂等通知，不接受外部地址、登录令牌或业务明细。
     *
     * @param tenant 租户隔离标识
     * @param userId 当前用户标识
     * @param deliveryId 当前契约的deliveryId配置或状态
     * @param title 当前契约的title配置或状态
     */
    void available(String tenant, long userId, String deliveryId, String title);
}
