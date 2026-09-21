package com.nexus.iot.pipeline;

import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.service.IIotDeviceDataService;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.pipeline.payload.IotDataValueResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stream 消费端单元测试。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>IOT-MQTT-01：topic 租户与设备记录租户不一致时 ACK + 拒绝入库；</li>
 *   <li>IOT-PIPE-01：{@code source_msg_id} 唯一键冲突（Stream 重投）视为已处理成功；</li>
 *   <li>IOT-PIPE-03：{@code data_type} 与实际值列一致（空 dataType 不硬写 NUMERIC）。</li>
 * </ul>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotStreamConsumerTest {

    private static final String TENANT = "000000";
    private static final String DEVICE_CODE = "DEV0001";
    private static final StreamMessageId MSG_ID = new StreamMessageId(1, 0);

    private IotPipelineProperties properties;
    private IIotDeviceService deviceService;
    private IIotDeviceDataService deviceDataService;
    private IotDeviceLatestCache latestCache;
    private IotAlertChecker alertChecker;
    private IotAnomalyDetector anomalyDetector;
    private RStream<Object, Object> stream;
    private IotStreamConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new IotPipelineProperties();
        deviceService = mock(IIotDeviceService.class);
        deviceDataService = mock(IIotDeviceDataService.class);
        latestCache = mock(IotDeviceLatestCache.class);
        alertChecker = mock(IotAlertChecker.class);
        anomalyDetector = mock(IotAnomalyDetector.class);
        stream = mock(RStream.class);
        consumer = new IotStreamConsumer(properties, deviceService, deviceDataService, latestCache,
            alertChecker, anomalyDetector, new IotDataValueResolver());
        ReflectionTestUtils.setField(consumer, "stream", stream);
        when(deviceService.selectEntityByCode(DEVICE_CODE)).thenReturn(device());
    }

    private IotDevice device() {
        IotDevice d = new IotDevice();
        d.setDeviceId(1L);
        d.setDeviceCode(DEVICE_CODE);
        d.setTenantId(TENANT);
        d.setStatus("ACTIVE");
        return d;
    }

    private Map<Object, Object> entry(String topicTenant, String value, String dataType) {
        Map<Object, Object> e = new HashMap<>();
        e.put("device_code", DEVICE_CODE);
        e.put("topic_tenant_id", topicTenant);
        e.put("property_code", "temp");
        e.put("data_type", dataType);
        e.put("value", value);
        e.put("quality", "GOOD");
        e.put("source", "MQTT");
        long now = System.currentTimeMillis();
        e.put("collect_time", String.valueOf(now));
        e.put("receive_time", String.valueOf(now));
        return e;
    }

    private Map<StreamMessageId, Map<Object, Object>> batch(Map<Object, Object> entry) {
        return Map.of(MSG_ID, entry);
    }

    /** IOT-MQTT-01：租户不一致 → ACK 拒绝入库（修复前只 warn 后照常写入）。 */
    @Test
    void processBatch_tenantMismatch_ackAndNotPersisted() {
        consumer.processBatch(batch(entry("000001", "25.5", "NUMERIC")));
        verify(deviceDataService, never()).batchInsert(anyCollection());
        verify(deviceDataService, never()).insert(any());
        verify(stream).ack(eq(properties.getConsumerGroup()), any(StreamMessageId.class));
    }

    @Test
    void processBatch_tenantMatch_persistedAndAcked() {
        consumer.processBatch(batch(entry(TENANT, "25.5", "NUMERIC")));
        verify(deviceDataService).batchInsert(anyCollection());
        verify(stream).ack(eq(properties.getConsumerGroup()), any(StreamMessageId.class));
    }

    @Test
    void processBatch_unknownDevice_ackedWithoutPersist() {
        when(deviceService.selectEntityByCode("DEV9999")).thenReturn(null);
        Map<Object, Object> e = entry(TENANT, "1", "NUMERIC");
        e.put("device_code", "DEV9999");
        consumer.processBatch(batch(e));
        verify(deviceDataService, never()).batchInsert(anyCollection());
        verify(stream).ack(eq(properties.getConsumerGroup()), any(StreamMessageId.class));
    }

    /** IOT-PIPE-01：唯一键冲突 = Stream 重投，按已处理成功 ACK，且不重复触发缓存/告警。 */
    @Test
    void processBatch_duplicateKey_treatedAsAlreadyProcessed() {
        when(deviceDataService.batchInsert(anyCollection()))
            .thenThrow(new DuplicateKeyException("Duplicate entry '1-0' for key 'uk_source_msg_id'"));
        when(deviceDataService.insert(any()))
            .thenThrow(new DuplicateKeyException("Duplicate entry '1-0' for key 'uk_source_msg_id'"));
        consumer.processBatch(batch(entry(TENANT, "25.5", "NUMERIC")));
        verify(stream).ack(eq(properties.getConsumerGroup()), any(StreamMessageId.class));
        verify(latestCache, never()).update(any());
        verify(alertChecker, never()).check(any());
    }

    // ---------------- buildData：data_type 与值列一致性（IOT-PIPE-03） ----------------

    @Test
    void buildData_blankDataType_infersNumeric() {
        IotDeviceData d = consumer.buildData(entry(TENANT, "25.5", ""), device(), MSG_ID);
        assertEquals("NUMERIC", d.getDataType());
        assertEquals(new BigDecimal("25.5"), d.getNumericValue());
        assertNull(d.getTextValue());
        assertEquals(MSG_ID.toString(), d.getSourceMsgId());
    }

    @Test
    void buildData_blankDataType_infersBool() {
        IotDeviceData d = consumer.buildData(entry(TENANT, "true", null), device(), MSG_ID);
        assertEquals("BOOL", d.getDataType());
        assertEquals(Boolean.TRUE, d.getBoolValue());
        assertNull(d.getNumericValue());
    }

    @Test
    void buildData_blankDataType_infersText() {
        IotDeviceData d = consumer.buildData(entry(TENANT, "SN001", ""), device(), MSG_ID);
        assertEquals("TEXT", d.getDataType());
        assertEquals("SN001", d.getTextValue());
        assertNull(d.getNumericValue());
    }

    /** 声明 NUMERIC 但值非数值：落 text_value，data_type 必须纠正为 TEXT（修复前记 NUMERIC）。 */
    @Test
    void buildData_numericDeclaredButNotNumeric_correctedToText() {
        IotDeviceData d = consumer.buildData(entry(TENANT, "abc", "NUMERIC"), device(), MSG_ID);
        assertEquals("TEXT", d.getDataType());
        assertEquals("abc", d.getTextValue());
        assertNull(d.getNumericValue());
    }

    @Test
    void buildData_boolDeclared_keepsBool() {
        IotDeviceData d = consumer.buildData(entry(TENANT, "1", "BOOL"), device(), MSG_ID);
        assertEquals("BOOL", d.getDataType());
        assertEquals(Boolean.TRUE, d.getBoolValue());
    }

    @Test
    void buildData_tenantAndDeviceComeFromDeviceRecord() {
        IotDeviceData d = consumer.buildData(entry(TENANT, "25.5", "NUMERIC"), device(), MSG_ID);
        assertEquals(TENANT, d.getTenantId());
        assertEquals(1L, d.getDeviceId());
        assertEquals(DEVICE_CODE, d.getDeviceCode());
    }

}
