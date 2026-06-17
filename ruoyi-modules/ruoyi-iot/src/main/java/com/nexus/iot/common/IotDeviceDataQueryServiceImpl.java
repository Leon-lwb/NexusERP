package com.nexus.iot.common;

import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.service.IIotDeviceDataService;
import com.nexus.iot.mqtt.IotDeviceStatusCache;
import com.nexus.iot.pipeline.IotDeviceLatestCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link IotDeviceDataQueryService} IoT 侧实现，供 MES 数据采集模块调用。
 * <p>
 * latest / status 走 Redis 缓存，history 走 MySQL（iot 数据源）。
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class IotDeviceDataQueryServiceImpl implements IotDeviceDataQueryService {

    private final IotDeviceLatestCache latestCache;
    private final IotDeviceStatusCache statusCache;
    private final IIotDeviceDataService deviceDataService;

    @Override
    public Map<String, String> getLatestData(Long deviceId) {
        return latestCache.getAll(deviceId);
    }

    @Override
    public String getOnlineStatus(Long deviceId) {
        return statusCache.get(deviceId);
    }

    @Override
    public List<Map<String, Object>> getHistoryData(Long deviceId, Date start, Date end) {
        List<IotDeviceData> list = deviceDataService.selectHistory(deviceId, start, end);
        return list.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(IotDeviceData d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataId", d.getDataId());
        m.put("deviceId", d.getDeviceId());
        m.put("deviceCode", d.getDeviceCode());
        m.put("propertyCode", d.getPropertyCode());
        m.put("dataType", d.getDataType());
        m.put("numericValue", d.getNumericValue());
        m.put("textValue", d.getTextValue());
        m.put("boolValue", d.getBoolValue());
        m.put("collectTime", d.getCollectTime());
        m.put("quality", d.getQuality());
        return m;
    }

}
