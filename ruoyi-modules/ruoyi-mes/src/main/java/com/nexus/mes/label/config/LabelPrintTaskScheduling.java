package com.nexus.mes.label.config;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.label.service.LabelPrintDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 独立双线程调度器轮询已提交MES任务；多节点由数据库领取CAS协调，不依赖外部调度或Redis。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexus.mes.label", name = "print-task-enabled", havingValue = "true", matchIfMissing = true)
public class LabelPrintTaskScheduling {
    private final MesSysTenantMapper tenants;
    private final LabelPrintDispatcher dispatcher;

    /** 标签专用调度器不作为全局默认候选，保留其他模块原调度选择。 */
    @Bean(name = "labelPrintTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler labelPrintTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); scheduler.setThreadNamePrefix("label-task-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false); scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /** 正常发送轮询，不回退匿名默认租户，失败保留数据库任务。 */
    @Scheduled(fixedDelayString = "${nexus.mes.label.print-task-poll-ms:5000}", scheduler = "labelPrintTaskScheduler")
    public void poll() { eachTenant(dispatcher::pollTenant); }

    /** 独立租约回收任务不经过网络，避免发送停顿阻止过期任务封存UNKNOWN。 */
    @Scheduled(fixedDelayString = "${nexus.mes.label.print-task-sweep-ms:10000}", scheduler = "labelPrintTaskScheduler")
    public void recover() { eachTenant(dispatcher::recoverTenant); }

    private void eachTenant(Runnable operation) {
        java.util.List<String> ids;
        DynamicDataSourceContextHolder.push("master");
        try { ids = tenants.selectAllTenantIds(); }
        catch (Exception unavailable) { log.warn("标签任务租户目录不可用，保留待发任务"); return; }
        finally { DynamicDataSourceContextHolder.poll(); }
        if (ids == null) return;
        for (String tenant : ids) {
            if (tenant == null || tenant.isBlank()) continue;
            try { TenantHelper.dynamic(tenant, operation); }
            catch (Exception failure) { log.warn("标签任务调度失败，数据库状态保留且不会重领发送中任务"); }
        }
    }
}
