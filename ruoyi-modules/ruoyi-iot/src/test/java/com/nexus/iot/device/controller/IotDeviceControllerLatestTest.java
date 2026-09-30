package com.nexus.iot.device.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.service.IIotDeviceDataService;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.mqtt.IotDeviceStatusCache;
import com.nexus.iot.pipeline.IotDeviceLatestCache;
import com.nexus.iot.realtime.IotRealtimeSseManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class IotDeviceControllerLatestTest {

    /** 必须使用 Long：此实际设备 ID 超出 JavaScript 数值的安全整数范围。 */
    private static final Long DEVICE_ID = 2104512194450214913L;

    @Mock
    private IIotDeviceService deviceService;
    @Mock
    private IIotDeviceDataService deviceDataService;
    @Mock
    private IotDeviceLatestCache latestCache;
    @Mock
    private IotDeviceStatusCache statusCache;
    @Mock
    private IotRealtimeSseManager realtimeSseManager;
    @Mock
    private IotDeviceDataQueryService dataQueryService;
    @InjectMocks
    private IotDeviceController controller;

    @Test
    void latestUsesPersistedDataWhenCacheReadIsEmpty() {
        IotDeviceVo device = new IotDeviceVo();
        device.setDeviceId(DEVICE_ID);
        when(deviceService.selectById(DEVICE_ID)).thenReturn(device);
        when(latestCache.getAll(DEVICE_ID)).thenReturn(Map.of());
        when(dataQueryService.getLatestData(DEVICE_ID)).thenReturn(Map.of("temperature", "187.3"));

        assertEquals(Map.of("temperature", "187.3"), controller.latest(DEVICE_ID).getData());
        verify(dataQueryService).getLatestData(DEVICE_ID);
    }

    @Test
    void latestKeepsFreshCachedValueWithoutDatabaseFallback() {
        IotDeviceVo device = new IotDeviceVo();
        device.setDeviceId(DEVICE_ID);
        when(deviceService.selectById(DEVICE_ID)).thenReturn(device);
        when(latestCache.getAll(DEVICE_ID)).thenReturn(Map.of("temperature", "187.3"));

        assertEquals(Map.of("temperature", "187.3"), controller.latest(DEVICE_ID).getData());
        verify(dataQueryService, never()).getLatestData(DEVICE_ID);
    }

    @Test
    void latestDoesNotReadDataOfInvisibleDevice() {
        R<Map<String, String>> result = controller.latest(DEVICE_ID);

        assertEquals(R.FAIL, result.getCode());
        verifyNoInteractions(latestCache, dataQueryService);
    }
}
