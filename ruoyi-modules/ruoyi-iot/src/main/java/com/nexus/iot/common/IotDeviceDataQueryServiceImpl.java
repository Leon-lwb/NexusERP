package com.nexus.iot.common;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.mapper.IotDeviceDataMapper;
import com.nexus.iot.device.mapper.IotDeviceMapper;
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
 * 当 Redis 缓存无状态时回退查数据库 connection_status 字段（避免缓存 TTL 过期导致误报离线）。
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class IotDeviceDataQueryServiceImpl implements IotDeviceDataQueryService {

    private final IotDeviceLatestCache latestCache;
    private final IotDeviceStatusCache statusCache;
    private final IIotDeviceDataService deviceDataService;
    private final IotDeviceMapper deviceMapper;
    private final IotDeviceDataMapper deviceDataMapper;

    @Override
    public Map<String, String> getLatestData(Long deviceId) {
        Map<String, String> map = latestCache.getAll(deviceId);
        // Redis 缓存为空时回退查数据库(模拟器/直接写库场景不会触发 StreamConsumer 的缓存更新)
        if (map.isEmpty() && deviceId != null) {
            try {
                DynamicDataSourceContextHolder.push("iot");
                List<IotDeviceData> list = deviceDataMapper.selectList(
                    Wrappers.<IotDeviceData>lambdaQuery()
                        .eq(IotDeviceData::getDeviceId, deviceId)
                        .orderByDesc(IotDeviceData::getCollectTime)
                        .last("LIMIT 50"));
                Map<String, String> fallback = new LinkedHashMap<>();
                for (IotDeviceData d : list) {
                    fallback.putIfAbsent(d.getPropertyCode(), serialize(d));
                }
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        }
        return map;
    }

    @Override
    public String getOnlineStatus(Long deviceId) {
        String status = statusCache.get(deviceId);
        // Redis 缓存返回 OFFLINE 时回退查数据库(connection_status 字段),
        // 避免 Redis TTL 过期后误报离线。需切 iot 数据源(iot_device 表在 ry-iot 库)
        if ("OFFLINE".equals(status)) {
            try {
                DynamicDataSourceContextHolder.push("iot");
                IotDevice device = deviceMapper.selectById(deviceId);
                if (device != null && "ONLINE".equals(device.getConnectionStatus())) {
                    return "ONLINE";
                }
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        }
        return status;
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

    /** 将数据实体的值序列化为字符串(与 IotDeviceLatestCache.serialize 一致) */
    private String serialize(IotDeviceData d) {
        if (d.getNumericValue() != null) {
            return d.getNumericValue().toPlainString();
        }
        if (d.getBoolValue() != null) {
            return d.getBoolValue().toString();
        }
        return d.getTextValue();
    }

}
