package com.nexus.iot.realtime;

import com.nexus.common.json.utils.JsonUtils;
import com.nexus.iot.mqtt.IotDeviceStatusCache;
import com.nexus.iot.pipeline.IotDeviceLatestCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IoT 设备实时数据 SSE 连接管理器。
 * <p>
 * 按 deviceId 维护独立的 SseEmitter 集合（区别于全局 {@code SseEmitterManager} 的 userId 维度），
 * 供前端实时仪表盘按设备订阅。{@link #pushRealtimeData} 每 3 秒轮询
 * {@code iot:latest} + {@code iot:status} 缓存并推送快照。
 * <p>
 * 连接生命周期：connect 时注册 onCompletion/onTimeout/onError 回调移除会话；
 * 推送时遇 IOException 立即移除坏连接（3 秒内自愈）。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class IotRealtimeSseManager {

    /** 外键 deviceId → (会话键 → SseEmitter) */
    private final Map<Long, Map<String, SseEmitter>> deviceEmitters = new ConcurrentHashMap<>();

    private final IotDeviceLatestCache latestCache;
    private final IotDeviceStatusCache statusCache;

    /**
     * 建立设备实时数据 SSE 连接。
     *
     * @param deviceId   设备ID
     * @param sessionKey 会话键（userId:uuid），同一会话重复连接时先关闭旧连接
     * @return SseEmitter
     */
    public SseEmitter connect(Long deviceId, String sessionKey) {
        // 不超时（0L），依赖 onCompletion/onError + 推送时 IOException 自愈清理
        SseEmitter emitter = new SseEmitter(0L);
        Map<String, SseEmitter> sessions = deviceEmitters.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());
        SseEmitter old = sessions.put(sessionKey, emitter);
        if (old != null) {
            old.complete();
        }
        Runnable cleanup = () -> removeSession(deviceId, sessionKey);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /**
     * 每 3 秒向所有订阅者推送设备实时数据快照。
     * <p>
     * payload: {"deviceId":123,"timestamp":<ms>,"connectionStatus":"ONLINE","properties":{"temperature":"25.3"}}
     */
    @Scheduled(fixedRate = 3000, scheduler = "iotTaskScheduler")
    public void pushRealtimeData() {
        if (deviceEmitters.isEmpty()) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        deviceEmitters.forEach((deviceId, sessions) -> {
            if (sessions.isEmpty()) {
                return;
            }
            Map<String, String> properties = latestCache.getAll(deviceId);
            String connectionStatus = statusCache.get(deviceId);
            Map<String, Object> payload = new LinkedHashMap<>(4);
            payload.put("deviceId", deviceId);
            payload.put("timestamp", timestamp);
            payload.put("connectionStatus", connectionStatus);
            payload.put("properties", properties);
            String json = JsonUtils.toJsonString(payload);

            sessions.forEach((sessionKey, emitter) -> {
                try {
                    emitter.send(SseEmitter.event().name("realtime").data(json));
                } catch (IOException | IllegalStateException e) {
                    // 连接已断开，移除会话
                    removeSession(deviceId, sessionKey);
                }
            });
        });
    }

    /**
     * 移除指定设备下的一个会话；设备无订阅者时清理外层 entry。
     */
    private void removeSession(Long deviceId, String sessionKey) {
        Map<String, SseEmitter> sessions = deviceEmitters.get(deviceId);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionKey);
        if (sessions.isEmpty()) {
            deviceEmitters.remove(deviceId, sessions);
        }
    }

}
