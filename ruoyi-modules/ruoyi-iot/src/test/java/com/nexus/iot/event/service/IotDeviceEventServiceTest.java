package com.nexus.iot.event.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.event.domain.IotDeviceEvent;
import com.nexus.iot.event.mapper.IotDeviceEventMapper;
import com.nexus.iot.event.service.impl.IotDeviceEventServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IoT 设备事件落库与告警联动单元测试（IOT-PIPE-02 上行事件）。
 * <p>
 * 覆盖：event/post 落库字段与处理状态、幂等（唯一键冲突）、告警类事件联动告警记录（SOURCE_EVENT）、
 * 非告警类事件不联动、处理状态 CAS。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotDeviceEventServiceTest {

    private static final String TENANT = "000000";

    private IotDeviceEventMapper baseMapper;
    private IIotAlertRecordService alertRecordService;
    private IotDeviceEventServiceImpl service;
    private MockedStatic<TenantHelper> tenantMock;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDeviceEvent.class);
        baseMapper = mock(IotDeviceEventMapper.class);
        alertRecordService = mock(IIotAlertRecordService.class);
        service = new IotDeviceEventServiceImpl(baseMapper, alertRecordService);

        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(inv -> {
                ((Runnable) inv.getArgument(1)).run();
                return null;
            });
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
    }

    private IotDevice device() {
        IotDevice d = new IotDevice();
        d.setDeviceId(1L);
        d.setTenantId(TENANT);
        d.setDeviceCode("DEV0001");
        d.setProductId(4L);
        return d;
    }

    private Map<Object, Object> entry(String eventCode, String level) {
        Map<Object, Object> entry = new HashMap<>();
        entry.put("entry_type", "EVENT");
        entry.put("device_code", "DEV0001");
        entry.put("topic_tenant_id", TENANT);
        entry.put("event_code", eventCode);
        entry.put("event_name", "门禁异常");
        entry.put("event_level", level);
        entry.put("event_time", "1758326400000");
        entry.put("receive_time", "1758326401000");
        entry.put("payload", "{\"eventCode\":\"" + eventCode + "\"}");
        entry.put("quality", "GOOD");
        entry.put("source", "MQTT");
        return entry;
    }

    /** 事件落库：字段完整 + 初始处理状态 PENDING（修复前 event/post 被静默丢弃）。 */
    @Test
    void persistEvent_insertsWithPendingStatus() {
        when(baseMapper.insert(any(IotDeviceEvent.class))).thenReturn(1);

        Long eventId = service.persistEvent(device(), entry("DOOR_OPEN", "INFO"), "1758326401-0");

        ArgumentCaptor<IotDeviceEvent> captor = ArgumentCaptor.forClass(IotDeviceEvent.class);
        verify(baseMapper).insert(captor.capture());
        IotDeviceEvent saved = captor.getValue();
        assertEquals(TENANT, saved.getTenantId());
        assertEquals(1L, saved.getDeviceId());
        assertEquals("DEV0001", saved.getDeviceCode());
        assertEquals("DOOR_OPEN", saved.getEventCode());
        assertEquals("INFO", saved.getEventLevel());
        assertEquals(IotDeviceEventServiceImpl.STATUS_PENDING, saved.getProcessStatus());
        assertEquals("1758326401-0", saved.getSourceMsgId());
        assertNotNull(saved.getEventTime());
        verify(alertRecordService, never()).trigger(any(), any(), any(), anyString());
        assertEquals(eventId, saved.getEventId());
    }

    /** 幂等：唯一键冲突（Stream 重投）→ 返回 null，不重复联动告警。 */
    @Test
    void persistEvent_duplicateKey_treatedAsProcessed() {
        when(baseMapper.insert(any(IotDeviceEvent.class)))
            .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_source_msg_id'"));

        Long eventId = service.persistEvent(device(), entry("OVERHEAT_ALARM", "CRITICAL"), "1758326401-0");

        assertNull(eventId, "重复消息应视为已处理并返回 null");
        verify(alertRecordService, never()).trigger(any(), any(), any(), anyString());
    }

    /** 告警类事件 → 联动告警记录（来源 EVENT），并回写 alert_id。 */
    @Test
    void persistEvent_alarmEvent_linksAlertRecord() {
        when(baseMapper.insert(any(IotDeviceEvent.class))).thenReturn(1);
        when(alertRecordService.trigger(isNull(), any(IotDeviceData.class), any(IotAlert.class), eq(IotAlertConstants.SOURCE_EVENT)))
            .thenReturn(88L);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.persistEvent(device(), entry("OVERHEAT_ALARM", "CRITICAL"), "1758326401-0");

        ArgumentCaptor<IotAlert> alertCaptor = ArgumentCaptor.forClass(IotAlert.class);
        verify(alertRecordService).trigger(isNull(), any(IotDeviceData.class), alertCaptor.capture(),
            eq(IotAlertConstants.SOURCE_EVENT));
        IotAlert alert = alertCaptor.getValue();
        assertEquals(TENANT, alert.getTenantId());
        assertEquals(1L, alert.getDeviceId());
        assertEquals("HIGH", alert.getAlertLevel(), "CRITICAL 应映射为 HIGH 级别");
        assertTrue(alert.getAlertMessage().contains("OVERHEAT_ALARM"));
        verify(baseMapper).update(any(), any());
    }

    /** 非告警类事件（INFO 且编码无告警语义）→ 不联动告警。 */
    @Test
    void persistEvent_normalEvent_doesNotLinkAlert() {
        when(baseMapper.insert(any(IotDeviceEvent.class))).thenReturn(1);

        service.persistEvent(device(), entry("HEARTBEAT", "INFO"), "1758326401-0");

        verify(alertRecordService, never()).trigger(any(), any(), any(), anyString());
    }

    /** 事件编码缺失 → 跳过落库（避免产生无法追溯的脏事件）。 */
    @Test
    void persistEvent_missingEventCode_skipped() {
        Map<Object, Object> entry = entry(null, "INFO");
        assertNull(service.persistEvent(device(), entry, "1758326401-0"));
        verify(baseMapper, never()).insert(any(IotDeviceEvent.class));
    }

    /** 处理状态 CAS：0 行 → 抛"已被其他操作变更"。 */
    @Test
    @SuppressWarnings("unchecked")
    void markProcessed_casConflict_throws() {
        IotDeviceEvent event = new IotDeviceEvent();
        event.setEventId(5L);
        event.setProcessStatus(IotDeviceEventServiceImpl.STATUS_PENDING);
        when(baseMapper.selectById(5L)).thenReturn(event);
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.markProcessed(5L, IotDeviceEventServiceImpl.STATUS_PROCESSED, "现场已处理"));
        assertTrue(ex.getMessage().contains("已被其他操作变更"));
    }

    /** 处理状态：非法目标状态被拒。 */
    @Test
    void markProcessed_invalidStatus_rejected() {
        assertThrows(ServiceException.class, () -> service.markProcessed(5L, "DONE", null));
    }

    /** 处理状态：CAS 成功并写入处理说明。 */
    @Test
    @SuppressWarnings("unchecked")
    void markProcessed_success_writesStatusAndRemark() {
        IotDeviceEvent event = new IotDeviceEvent();
        event.setEventId(5L);
        event.setProcessStatus(IotDeviceEventServiceImpl.STATUS_PENDING);
        when(baseMapper.selectById(5L)).thenReturn(event);
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.markProcessed(5L, IotDeviceEventServiceImpl.STATUS_IGNORED, "误报"));

        ArgumentCaptor<Wrapper<IotDeviceEvent>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotDeviceEvent> wrapper = (LambdaUpdateWrapper<IotDeviceEvent>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotDeviceEventServiceImpl.STATUS_IGNORED));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("误报"));
    }
}
