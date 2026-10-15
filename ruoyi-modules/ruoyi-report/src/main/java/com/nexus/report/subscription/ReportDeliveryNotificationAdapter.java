package com.nexus.report.subscription;

import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.report.ReportDeliveryNotificationPort;

import org.springframework.context.ApplicationEventPublisher;

/** 站内通知使用同步主库事件，事务提交后由现有IM监听器推送。 */
public final class ReportDeliveryNotificationAdapter implements ReportDeliveryNotificationPort {
    /** 保存当前events状态或配置。 */
    private final ApplicationEventPublisher events;

    /**
     * 默认不开启真实发送，只有交付服务的显式开关可以调用。
     *
     * @param events 当前契约的events配置或状态
     */
    public ReportDeliveryNotificationAdapter(ApplicationEventPublisher events) {
        this.events = events;
    }

    /**
     * 摘要仅包含订阅名称和正常登录后的领取入口，不携带文件外链或Token。
     *
     * @param tenant 租户隔离标识
     * @param user 当前契约的user配置或状态
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param title 当前契约的title配置或状态
     */
    public void available(String tenant, long user, String id, String title) {
        events.publishEvent(
                new UserNotificationEvent(
                        tenant,
                        user,
                        "REPORT",
                        "REPORT_DELIVERY",
                        id,
                        "订阅报表已准备好",
                        "订阅“" + title + "”已准备好，请登录报表交付中心领取。",
                        "{}",
                        "/report/delivery"));
    }
}
