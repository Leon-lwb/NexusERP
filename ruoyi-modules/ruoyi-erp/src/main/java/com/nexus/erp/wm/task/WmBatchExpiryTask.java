package com.nexus.erp.wm.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.redis.utils.SchedulerFallbackUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.service.IWmBatchService;
import com.nexus.system.domain.SysTenant;
import com.nexus.system.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 批次效期过期标记定时任务（ERP-WM-06）
 * <p>修复前 {@code EXPIRED} 状态是「准死状态」：只有人工调用 {@code POST /erp/wm/batch/checkExpiry}
 * 才能把 {@code ACTIVE + expiry_date < now} 的批次置为 {@code EXPIRED}，没有任何自动任务，
 * 导致效期报表与实物长期不符。此处补上每日自动标记。</p>
 * <p>任务线程无登录态，需遍历所有租户并在 {@code TenantHelper.dynamic} 下执行，
 * 否则仅默认租户批次被标记（与 {@code StockAlertTask} 同款写法）。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "wmBatchExpiryTask")
public class WmBatchExpiryTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /** 批次服务（{@code checkExpiry} 为幂等条件更新：仅 ACTIVE 且已过期的批次被置 EXPIRED） */
    private final IWmBatchService wmBatchService;
    /** 租户 Mapper（sys_tenant 在 ry-vue 主库，任务类无 @DS 走默认数据源） */
    private final SysTenantMapper sysTenantMapper;

    /**
     * 本地 {@code @Scheduled} 兜底入口（每天 01:30）。
     * <p>Lock4j 按任务名加分布式锁，多实例下仅一个节点执行，抢锁失败静默跳过。</p>
     */
    @Scheduled(cron = "0 30 1 * * ?")
    public void scheduledExecute() {
        SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "wmBatchExpiryTask", 900000L, () -> jobExecute(null));
    }

    /**
     * 执行批次过期标记任务
     *
     * @param jobArgs 任务参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行批次效期过期标记任务");
        try {
            List<SysTenant> tenants = sysTenantMapper.selectList(Wrappers.lambdaQuery(SysTenant.class));
            if (tenants == null || tenants.isEmpty()) {
                TenantHelper.dynamic("000000", () -> wmBatchService.checkExpiry());
            } else {
                for (SysTenant tenant : tenants) {
                    TenantHelper.dynamic(tenant.getTenantId(), () -> wmBatchService.checkExpiry());
                }
            }
            SnailJobLog.REMOTE.info("批次效期过期标记任务执行完成");
            return ExecuteResult.success("批次效期过期标记完成");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("批次效期过期标记任务执行失败 {}", e.getMessage());
            return ExecuteResult.failure("批次效期过期标记失败 " + e.getMessage());
        }
    }
}
