package com.nexus.report.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 订阅调度及通知分别开启，开发和测试不自动发送真实通知。 */
@ConfigurationProperties(prefix = "nexus.report.subscription")
public final class ReportSubscriptionProperties {
    /** 显式启用SnailJob定时调度。 */
    private boolean schedulingEnabled = false;

    /**
     * 获取显式启用SnailJob定时调度。
     *
     * @return 当前受控处理的固定结果
     */
    public boolean isSchedulingEnabled() {
        return schedulingEnabled;
    }

    /**
     * 设置显式启用SnailJob定时调度。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setSchedulingEnabled(boolean value) {
        schedulingEnabled = value;
    }

    /** 显式启用真实站内通知，测试默认关闭。 */
    private boolean notificationsEnabled = false;

    /**
     * 获取显式启用真实站内通知，测试默认关闭。
     *
     * @return 当前受控处理的固定结果
     */
    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    /**
     * 设置显式启用真实站内通知，测试默认关闭。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setNotificationsEnabled(boolean value) {
        notificationsEnabled = value;
    }

    /** 一次调度最多补跑的时刻数。 */
    private int maxCatchupOccurrences = 20;

    /**
     * 获取一次调度最多补跑的时刻数。
     *
     * @return 当前受控处理的固定结果
     */
    public int getMaxCatchupOccurrences() {
        return maxCatchupOccurrences;
    }

    /**
     * 设置一次调度最多补跑的时刻数。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setMaxCatchupOccurrences(int value) {
        maxCatchupOccurrences = value;
    }
}
