package com.nexus.iot.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.nexus.iot.device.service.IIotDeviceDataService;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

/**
 * IoT 设备数据清理任务：删除采集时间超过 90 天的 iot_device_data 记录。
 * <p>
 * SnailJob 后台 ruoyi_group 建任务: executor=iotDataCleanupTask, 建议 cron 0 0 2 * * ? (每天凌晨2:00).
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "iotDataCleanupTask")
public class IotDataCleanupTask {

    private final IIotDeviceDataService deviceDataService;

    private static final int RETENTION_DAYS = 90;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行IoT设备数据清理任务，保留天数={}", RETENTION_DAYS);
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -RETENTION_DAYS);
            Date deadline = cal.getTime();
            int deleted = deviceDataService.deleteBefore(deadline);
            SnailJobLog.REMOTE.info("IoT数据清理完成：deadline={}, 删除{}条", deadline, deleted);
            return ExecuteResult.success("数据清理完成，删除" + deleted + "条");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("IoT数据清理任务执行失败: {}", e.getMessage());
            return ExecuteResult.failure("数据清理失败: " + e.getMessage());
        }
    }
}
