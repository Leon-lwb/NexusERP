package org.dromara.erp.pu.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import org.dromara.erp.pu.service.IPuVendorEvaluationService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@JobExecutor(name = "vendorEvaluationTask")
public class VendorEvaluationTask {

    private final IPuVendorEvaluationService vendorEvaluationService;

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
