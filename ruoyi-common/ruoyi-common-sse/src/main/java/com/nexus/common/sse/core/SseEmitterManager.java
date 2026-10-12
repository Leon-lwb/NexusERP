package com.nexus.common.sse.core;

import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.sse.dto.SseMessageDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 管理 Server-Sent Events (SSE) 连接
 *
 * @author Lion Li
 */
@Slf4j
public class SseEmitterManager {

    /**
     * 订阅的频道
     */
    private final static String SSE_TOPIC = "global:sse";

    private final static Map<Long, Map<String, SseEmitter>> USER_TOKEN_EMITTERS = new ConcurrentHashMap<>();

    public SseEmitterManager() {
        // 定时执行 SSE 心跳检测
        SpringUtils.getBean(ScheduledExecutorService.class)
            .scheduleWithFixedDelay(this::sseMonitor, 60L, 60L, TimeUnit.SECONDS);
    }

    /**
     * 建立与指定用户的 SSE 连接
     *
     * @param userId 用户的唯一标识符，用于区分不同用户的连接
     * @param token  用户的唯一令牌，用于识别具体的连接
     * @return 返回一个 SseEmitter 实例，客户端可以通过该实例接收 SSE 事件
     */
    public SseEmitter connect(Long userId, String token) {
        SseEmitter emitter = new SseEmitter(86400000L);
        emitter.onCompletion(() -> removeEmitter(userId, token, emitter));
        emitter.onTimeout(() -> closeEmitter(userId, token, emitter));
        emitter.onError(error -> closeEmitter(userId, token, emitter));

        // 注册与清理都在用户维度原子执行，防止旧回调移除刚建立的新连接。
        SseEmitter[] replaced = new SseEmitter[1];
        USER_TOKEN_EMITTERS.compute(userId, (key, current) -> {
            Map<String, SseEmitter> emitters = current == null ? new ConcurrentHashMap<>() : current;
            replaced[0] = emitters.put(token, emitter);
            return emitters;
        });
        if (replaced[0] != null) {
            completeSafely(replaced[0]);
        }
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception e) {
            closeEmitter(userId, token, emitter);
        }
        return emitter;
    }

    /** 断开调用时对应的连接，不能关闭并发重连的新实例。 */
    public void disconnect(Long userId, String token) {
        if (userId == null || token == null) {
            return;
        }
        Map<String, SseEmitter> emitters = USER_TOKEN_EMITTERS.get(userId);
        SseEmitter emitter = emitters == null ? null : emitters.get(token);
        if (emitter != null) {
            closeEmitter(userId, token, emitter);
        }
    }

    /** 心跳检测按连接实例清理，其他设备及重连后的实例继续工作。 */
    public void sseMonitor() {
        USER_TOKEN_EMITTERS.forEach((userId, emitters) -> emitters.forEach((token, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                closeEmitter(userId, token, emitter);
            }
        }));
    }

    private void removeEmitter(Long userId, String token, SseEmitter emitter) {
        USER_TOKEN_EMITTERS.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(token, emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private void closeEmitter(Long userId, String token, SseEmitter emitter) {
        removeEmitter(userId, token, emitter);
        completeSafely(emitter);
    }

    private void completeSafely(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 容器可能已经关闭响应，仍需释放本地引用。
        }
    }

    /**
     * 订阅SSE消息主题，并提供一个消费者函数来处理接收到的消息
     *
     * @param consumer 处理SSE消息的消费者函数
     */
    public void subscribeMessage(Consumer<SseMessageDto> consumer) {
        RedisUtils.subscribe(SSE_TOPIC, SseMessageDto.class, consumer);
    }

    /**
     * 向指定的用户会话发送消息
     *
     * @param userId  要发送消息的用户id
     * @param message 要发送的消息内容
     */
    public void sendMessage(Long userId, String message) {
        Map<String, SseEmitter> emitters = USER_TOKEN_EMITTERS.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.forEach((token, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("message").data(message));
            } catch (Exception e) {
                closeEmitter(userId, token, emitter);
            }
        });
    }

    /**
     * 本机全用户会话发送消息
     *
     * @param message 要发送的消息内容
     */
    public void sendMessage(String message) {
        for (Long userId : USER_TOKEN_EMITTERS.keySet()) {
            sendMessage(userId, message);
        }
    }

    /**
     * 发布SSE订阅消息
     *
     * @param sseMessageDto 要发布的SSE消息对象
     */
    public void publishMessage(SseMessageDto sseMessageDto) {
        // 空列表只能表示没有接收者，跨用户广播必须显式调用 publishAll。
        if (sseMessageDto == null || sseMessageDto.getUserIds() == null || sseMessageDto.getUserIds().isEmpty()) {
            return;
        }
        SseMessageDto broadcastMessage = new SseMessageDto();
        broadcastMessage.setMessage(sseMessageDto.getMessage());
        broadcastMessage.setUserIds(sseMessageDto.getUserIds());
        RedisUtils.publish(SSE_TOPIC, broadcastMessage, consumer -> {
            log.debug("SSE 已发布，topic:{} 接收用户数:{}", SSE_TOPIC,
                sseMessageDto.getUserIds() == null ? 0 : sseMessageDto.getUserIds().size());
        });
    }

    /**
     * 向所有的用户发布订阅的消息(群发)
     *
     * @param message 要发布的消息内容
     */
    public void publishAll(String message) {
        SseMessageDto broadcastMessage = new SseMessageDto();
        broadcastMessage.setMessage(message);
        RedisUtils.publish(SSE_TOPIC, broadcastMessage, consumer -> {
            log.debug("SSE 已发布广播消息，topic:{}", SSE_TOPIC);
        });
    }
}
