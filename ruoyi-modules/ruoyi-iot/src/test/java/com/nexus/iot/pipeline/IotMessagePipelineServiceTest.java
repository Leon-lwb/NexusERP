package com.nexus.iot.pipeline;

import com.nexus.iot.pipeline.payload.IotMqttTopicParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 消息管道生产端单元测试。
 * <p>
 * 覆盖 IOT-MQTT-01（topic deviceCode 与 clientId 不一致必须丢弃并计数）
 * 与 IOT-PIPE-04（collect_time 合理性校验，防止未来时间戳绕过 90 天清理）。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotMessagePipelineServiceTest {

    private IotPipelineProperties properties;
    private IotMessagePipelineService service;

    @BeforeEach
    void setUp() {
        properties = new IotPipelineProperties();
        service = new IotMessagePipelineService(new IotMqttTopicParser(), properties);
    }

    /** IOT-MQTT-01：topic 内 deviceCode ≠ clientId → 消息被丢弃并计数。 */
    @Test
    void publishToStream_deviceCodeMismatch_droppedAndCounted() {
        assertDoesNotThrow(() -> service.publishToStream("DEV0001",
            "iot/000000/DEV0002/property/post", "{\"property\":\"temp\",\"value\":25.5}"));
        assertEquals(1L, service.getDeviceMismatchDroppedCount());
    }

    @Test
    void publishToStream_blankClientId_droppedAndCounted() {
        service.publishToStream("", "iot/000000/DEV0002/property/post", "{}");
        assertEquals(1L, service.getDeviceMismatchDroppedCount());
    }

    @Test
    void publishToStream_nonIotTopic_notCountedAsMismatch() {
        service.publishToStream("DEV0001", "some/other/topic", "{}");
        assertEquals(0L, service.getDeviceMismatchDroppedCount());
    }

    @Test
    void publishToStream_matchingDeviceCode_notCountedAsMismatch() {
        // 身份一致 → 不进入丢弃分支。单测环境无 Spring/Redis 上下文，
        // 链路后段的 Redis 写入可能抛出 Error（RedisUtils 静态初始化），此处只断言丢弃计数未被增加。
        try {
            service.publishToStream("DEV0001", "iot/000000/DEV0001/property/post",
                "{\"property\":\"temp\",\"value\":25.5}");
        } catch (Throwable ignored) {
            // 单测环境无 Redis，忽略后段失败
        }
        assertEquals(0L, service.getDeviceMismatchDroppedCount());
    }

    /** IOT-PIPE-04：未来时间戳（如 2100 年）必须改用服务端时间。 */
    @Test
    void resolveCollectTime_futureTimestamp_correctedToServerTime() {
        long now = System.currentTimeMillis();
        Date d = service.resolveCollectTime(4102444800000L, now, "DEV0001");
        assertEquals(now, d.getTime());
        assertEquals(1L, service.getCollectTimeCorrectedCount());
    }

    /** IOT-PIPE-04：1970 年时间戳必须改用服务端时间（否则会被清理任务立刻删除）。 */
    @Test
    void resolveCollectTime_tooOldTimestamp_correctedToServerTime() {
        long now = System.currentTimeMillis();
        Date d = service.resolveCollectTime(0L, now, "DEV0001");
        assertEquals(now, d.getTime());
        assertEquals(1L, service.getCollectTimeCorrectedCount());
    }

    @Test
    void resolveCollectTime_withinTolerance_keepsDeviceTime() {
        long now = System.currentTimeMillis();
        long deviceTs = now - 60_000L;
        Date d = service.resolveCollectTime(deviceTs, now, "DEV0001");
        assertEquals(deviceTs, d.getTime());
        assertEquals(0L, service.getCollectTimeCorrectedCount());
    }

    @Test
    void resolveCollectTime_atFutureToleranceBoundary_keepsDeviceTime() {
        long now = System.currentTimeMillis();
        long deviceTs = now + properties.getCollectTimeFutureToleranceMs();
        assertEquals(deviceTs, service.resolveCollectTime(deviceTs, now, "DEV0001").getTime());
        assertEquals(0L, service.getCollectTimeCorrectedCount());
    }

    @Test
    void resolveCollectTime_nullTimestamp_usesServerTime() {
        long now = System.currentTimeMillis();
        assertEquals(now, service.resolveCollectTime(null, now, "DEV0001").getTime());
        assertEquals(0L, service.getCollectTimeCorrectedCount());
    }

}
