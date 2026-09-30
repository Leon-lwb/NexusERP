package com.nexus.iot.common;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.mapper.IotDeviceDataMapper;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import com.nexus.iot.device.service.IIotDeviceDataService;
import com.nexus.iot.mqtt.IotDeviceStatusCache;
import com.nexus.iot.pipeline.IotDeviceLatestCache;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
class IotDeviceDataQueryServiceImplLatestTest {

    @Test
    void missingCacheFallsBackToPersistedValueForExactSnowflakeId() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDeviceData.class);
        long deviceId = 2104512194450214913L;
        IotDeviceLatestCache cache = mock(IotDeviceLatestCache.class);
        IotDeviceDataMapper dataMapper = mock(IotDeviceDataMapper.class);
        IotDeviceData data = new IotDeviceData();
        data.setDeviceId(deviceId);
        data.setTenantId("000000");
        data.setPropertyCode("temperature");
        data.setNumericValue(new BigDecimal("187.3"));
        when(cache.getAll(deviceId)).thenReturn(Map.of());
        when(dataMapper.selectList(any())).thenReturn(List.of(data));
        IotDeviceDataQueryServiceImpl service = new IotDeviceDataQueryServiceImpl(cache,
            mock(IotDeviceStatusCache.class), mock(IIotDeviceDataService.class),
            mock(IotDeviceMapper.class), dataMapper);

        assertEquals(Map.of("temperature", "187.3"), service.getLatestData(deviceId));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<IotDeviceData>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(dataMapper).selectList(query.capture());
        query.getValue().getSqlSegment(); /** MyBatis-Plus 在渲染 SQL 时才延迟绑定 Lambda 参数。 */
        assertTrue(((AbstractWrapper<IotDeviceData, ?, ?>) query.getValue())
            .getParamNameValuePairs().containsValue(deviceId));
    }
}
