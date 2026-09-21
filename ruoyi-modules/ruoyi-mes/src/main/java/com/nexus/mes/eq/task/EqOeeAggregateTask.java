package com.nexus.mes.eq.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.nexus.mes.eq.service.IEqOeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OEE 聚合定时任务
 * <p>
 * 每日聚合前一天的设备 OEE 数据(停机时长取自 eq_repair.downtime_hours),
 * 写入 eq_oee_record(按 租户+设备+stat_date 唯一, 幂等可重跑)。
 * <p>
 * 建议频率: 每天 01:00 一次 (cron: 0 0 1 * * ?), 避开保养到期任务。
 * 多租户: 由 {@link IEqOeeService#aggregate} 内部遍历有效租户(TenantHelper.dynamic),
 * 无需在本任务硬编码租户。
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@JobExecutor(name = "oeeAggregateTask")
public class EqOeeAggregateTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（MES-EQ-01 / MES-EQ-02）。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #jobExecute}，OEE 看板永远空白（只有人工点“手动聚合”才有数据）。本任务在
     * {@code sj_job} 中原本连任务定义都没有（MES-EQ-02），cron 与补种后的种子一致（每天 01:00）。
     * 聚合体按 租户+设备+stat_date 唯一幂等可重跑，另由 Lock4j 按任务名加分布式锁防多实例重复。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 1 * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "oeeAggregateTask", 900000L, () -> jobExecute(null));
    }

    private final IEqOeeService oeeService;

    /**
     * 定时任务执行入口，由调度框架回调。
     *
     * @param jobArgs 任务执行参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[OEE聚合任务] 开始执行");
        try {
            // date=null 表示聚合昨天
            int count = oeeService.aggregate(null);
            SnailJobLog.REMOTE.info("[OEE聚合任务] 完成, 处理 {} 条记录", count);
            return ExecuteResult.success("处理 " + count + " 条 OEE 记录");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[OEE聚合任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("OEE 聚合任务失败: " + e.getMessage());
        }
    }
}
