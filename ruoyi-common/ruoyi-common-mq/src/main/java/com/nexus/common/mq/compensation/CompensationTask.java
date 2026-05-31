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
