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
