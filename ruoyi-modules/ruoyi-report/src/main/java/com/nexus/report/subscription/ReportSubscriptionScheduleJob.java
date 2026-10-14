package com.nexus.report.subscription;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.model.dto.ExecuteResult;

import org.springframework.stereotype.Component;

import java.time.Instant;

/** SnailJob只触发主库调度，租户和接收人身份均由持久订阅重新解析。 */
@Component
@JobExecutor(name = "reportSubscriptionDispatch")
public final class ReportSubscriptionScheduleJob {
    /** 保存当前subscriptions状态或配置。 */
    private final ReportSubscriptionService subscriptions;

    /** 保存当前deliveries状态或配置。 */
    private final ReportDeliveryService deliveries;

    /** 保存当前properties状态或配置。 */
    private final ReportSubscriptionProperties properties;

    /**
     * 真实通知、调度和交付分别受明确开关控制。
     *
     * @param subscriptions 当前契约的subscriptions配置或状态
     * @param deliveries 当前契约的deliveries配置或状态
     * @param properties 当前契约的properties配置或状态
     */
    public ReportSubscriptionScheduleJob(
            ReportSubscriptionService subscriptions,
            ReportDeliveryService deliveries,
            ReportSubscriptionProperties properties) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.properties = properties;
    }

    /**
     * 建议SnailJob一分钟触发一次；任务参数不能覆盖租户或执行用户。
     *
     * @param args 当前契约的args配置或状态
     * @return 当前受控处理的固定结果
     */
    public ExecuteResult jobExecute(JobArgs args) {

        try {
            int count =
                    properties.isSchedulingEnabled() ? subscriptions.dispatchDue(Instant.now()) : 0;
            subscriptions.drain();
            deliveries.reconcile();
            return ExecuteResult.success("已处理订阅时刻：" + count);
        } catch (RuntimeException failure) {
            return ExecuteResult.failure("报表订阅调度未完成，请查看持久执行记录");
        }
    }
}
