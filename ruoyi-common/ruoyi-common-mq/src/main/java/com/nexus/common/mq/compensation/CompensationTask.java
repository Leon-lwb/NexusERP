package com.nexus.common.mq.compensation;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/** 补偿定时任务，扫描PENDING状态且到达重试时间的补偿记录并执行
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CompensationTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #execute()}，PENDING 补偿记录永不重放。cron 与 {@code sj_job} 种子等价
     * （固定 60s → 每分钟）。Lock4j 按任务名加分布式锁，多实例下仅一个节点执行，抢锁失败静默跳过。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 */1 * * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "mqCompensationTask", 120000L, this::execute);
    }

    private final CompensationRecordMapper compensationRecordMapper;
    private final CompensationService compensationService;

    /** 执行补偿扫描 */
    @JobExecutor(name = "mqCompensationTask")
    public void execute() {
        LambdaQueryWrapper<CompensationRecord> lqw = new LambdaQueryWrapper<>();
        lqw.eq(CompensationRecord::getCompensationStatus, "PENDING");
        lqw.le(CompensationRecord::getNextRetryTime, new Date());
        List<CompensationRecord> pendingRecords = compensationRecordMapper.selectList(lqw);
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return;
        }
        log.info("扫描到 {} 条待补偿记录", pendingRecords.size());
        for (CompensationRecord record : pendingRecords) {
            try {
                compensationService.executeCompensation(record.getId());
            } catch (Exception e) {
                log.error("补偿任务执行异常: recordId={}, error={}", record.getId(), e.getMessage());
            }
        }
    }
}
