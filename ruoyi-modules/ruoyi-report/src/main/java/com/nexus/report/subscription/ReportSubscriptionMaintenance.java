package com.nexus.report.subscription;

import com.nexus.report.runtime.ReportRuntimeService;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 已授权手动任务的outbox及交付对账不依赖新cron触发开关。 */
@Component
public final class ReportSubscriptionMaintenance {
    /** 主库报表元数据访问器。 */
    private final JdbcTemplate jdbc;

    /** 已有授权订阅执行的outbox维护服务。 */
    private final ReportSubscriptionService subscriptions;

    /** 已有私有交付的对账服务。 */
    private final ReportDeliveryService deliveries;

    /** 本次启动是否已安装扩展表的缓存检测结果。 */
    private Boolean installed;

    /**
     * 只维护已经存在的授权记录，不在启动阶段创建订阅或消息。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param subscriptions 当前契约的subscriptions配置或状态
     * @param deliveries 当前契约的deliveries配置或状态
     */
    public ReportSubscriptionMaintenance(
            JdbcTemplate jdbc,
            ReportSubscriptionService subscriptions,
            ReportDeliveryService deliveries) {
        this.jdbc = jdbc;
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
    }

    /** 独立维护已持久任务；表未安装的旧环境保持静默。 */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void tick() {
        try {
            if (installed == null) {
                Boolean present =
                        jdbc.execute(
                                (ConnectionCallback<Boolean>)
                                        connection -> {
                                            try (var tables =
                                                    connection
                                                            .getMetaData()
                                                            .getTables(
                                                                    connection.getCatalog(),
                                                                    null,
                                                                    "rpt_subscription_run",
                                                                    new String[] {"TABLE"})) {
                                                if (tables.next()) return true;
                                            }
                                            try (var tables =
                                                    connection
                                                            .getMetaData()
                                                            .getTables(
                                                                    connection.getCatalog(),
                                                                    null,
                                                                    "RPT_SUBSCRIPTION_RUN",
                                                                    new String[] {"TABLE"})) {
                                                return tables.next();
                                            }
                                        });
                installed = Boolean.TRUE.equals(present);
            }
            if (!installed) return;
            subscriptions.drain();
            deliveries.reconcile();
        } catch (RuntimeException deferred) {
            LoggerFactory.getLogger(ReportSubscriptionMaintenance.class)
                    .warn("报表交付维护待重试：{}", ReportRuntimeService.code(deferred));
        }
    }
}
