package com.nexus.erp.pu.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.nexus.erp.pu.service.IPuVendorEvaluationService;
import org.springframework.stereotype.Component;

/**
 * 供应商绩效评估定时任务
 * <p>
 * 基于 SnailJob 分布式任务调度框架执行的定时任务，
 * 负责定期扫描所有供应商并触发批量绩效评估计算。
 * 评估维度包括交货及时率、质量合格率、价格竞争力，
 * 计算结果用于供应商分级管理和采购决策参考。
 *
 * @author ruoyi
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "vendorEvaluationTask")
public class VendorEvaluationTask {

    private final IPuVendorEvaluationService vendorEvaluationService;

    /**
     * 执行供应商绩效批量评估任务
     * <p>
     * 调用 {@link IPuVendorEvaluationService#batchCalculateAll()} 方法，
     * 遍历所有有效供应商并逐条计算其当期绩效评估数据。
     *
     * @param jobArgs 任务参数，包含任务执行上下文信息
     * @return 任务执行结果，成功或失败状态及提示信息
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行供应商绩效批量评估任务");
        try {
            vendorEvaluationService.batchCalculateAll();
            SnailJobLog.REMOTE.info("供应商绩效批量评估任务执行完成");
            return ExecuteResult.success("供应商绩效批量评估完成");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("供应商绩效批量评估任务执行失败: {}", e.getMessage());
            return ExecuteResult.failure("供应商绩效批量评估失败: " + e.getMessage());
        }
    }
}
