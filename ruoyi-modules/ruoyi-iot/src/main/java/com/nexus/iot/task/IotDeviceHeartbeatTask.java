package com.nexus.iot.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.mqtt.IotDeviceStatusCache;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * IoT device heartbeat task: scan ONLINE devices whose last_online_time is older
 * than 15 minutes and mark them as OFFLINE.
 * <p>
 * SnailJob: executor=iotDeviceHeartbeatTask, cron=0 *&#47;5 * * * ? (every 5 min).
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "iotDeviceHeartbeatTask")
public class IotDeviceHeartbeatTask {

    private final IIotDeviceService deviceService;
    private final IotDeviceStatusCache statusCache;

    private static final int TIMEOUT_MINUTES = 15;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("IoT heartbeat check start, timeout={}min", TIMEOUT_MINUTES);
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -TIMEOUT_MINUTES);
            Date deadline = cal.getTime();
            List<IotDevice> staleDevices = deviceService.selectStaleOnlineDevices(deadline);
            int offlineCount = 0;
            for (IotDevice device : staleDevices) {
                deviceService.updateConnectionStatus(device.getDeviceId(), false);
                statusCache.markOffline(device);
                offlineCount++;
            }
            SnailJobLog.REMOTE.info("IoT heartbeat done: {} stale devices set OFFLINE", offlineCount);
            return ExecuteResult.success("Heartbeat done, offline=" + offlineCount);
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("IoT heartbeat task failed: {}", e.getMessage());
            return ExecuteResult.failure("Heartbeat failed: " + e.getMessage());
        }
    }
}
