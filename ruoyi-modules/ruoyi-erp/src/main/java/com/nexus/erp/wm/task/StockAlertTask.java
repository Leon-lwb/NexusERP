package com.nexus.erp.wm.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.nexus.erp.wm.service.IWmStockAlertService;
import org.springframework.stereotype.Component;

/**
 * 安全库存预警定时任务
 * 定期扫描物料库存，当库存低于安全库存或最低库存时自动生成预警记录
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "stockAlertTask")
public class StockAlertTask {

    private final IWmStockAlertService stockAlertService;

    /**
     * 执行安全库存预警检查任务
     *
     * @param jobArgs 任务参数
     * @return 任务执行结果
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行安全库存预警检查任务");
        try {
            stockAlertService.checkStockAlerts();
            SnailJobLog.REMOTE.info("安全库存预警检查任务执行完成");
            return ExecuteResult.success("安全库存预警检查完成");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("安全库存预警检查任务执行失败 {}", e.getMessage());
            return ExecuteResult.failure("安全库存预警检查失败 " + e.getMessage());
        }
    }
}
