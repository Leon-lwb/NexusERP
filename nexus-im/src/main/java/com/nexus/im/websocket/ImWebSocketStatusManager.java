package com.nexus.im.websocket;

import com.nexus.im.service.ImUserStatusService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IM WebSocket 状态管理器
 * 管理 WebSocket 连接状态，并与 Redis 同步
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketStatusManager {

    private final ImUserStatusService imUserStatusService;

    // 本地缓存：userId -> sessionCount (支持多端登录)
    private static final Map<Long, Integer> userSessionCount = new ConcurrentHashMap<>();

    /**
     * 用户连接 WebSocket
     *
     * @param userId 用户ID
     */
    public void userConnected(Long userId) {
        int count = userSessionCount.getOrDefault(userId, 0);
        userSessionCount.put(userId, count + 1);
        
        // 第一次连接时设置在线状态
        if (count == 0) {
            imUserStatusService.setUserOnline(userId);
            log.info("用户上线: userId={}", userId);
        }
        log.debug("用户WebSocket连接: userId={}, sessionCount={}", userId, count + 1);
    }

    /**
     * 用户断开 WebSocket
     *
     * @param userId 用户ID
     */
    public void userDisconnected(Long userId) {
        int count = userSessionCount.getOrDefault(userId, 0);
        if (count > 0) {
            count--;
            if (count == 0) {
                userSessionCount.remove(userId);
                // 所有连接都断开时设置离线
                imUserStatusService.setUserOffline(userId);
                log.info("用户离线: userId={}", userId);
            } else {
                userSessionCount.put(userId, count);
                log.debug("用户WebSocket断开: userId={}, remainingSessions={}", userId, count);
            }
        }
    }

    /**
     * 更新用户最后活跃时间
     *
     * @param userId 用户ID
     */
    public void updateUserActiveTime(Long userId) {
        imUserStatusService.updateLastActiveTime(userId);
    }

    /**
     * 检查用户是否在线
     *
     * @param userId 用户ID
     * @return true-在线
     */
    public boolean isUserOnline(Long userId) {
        return imUserStatusService.isUserOnline(userId);
    }

    /**
     * 获取在线用户ID列表
     *
     * @return 在线用户ID集合
     */
    public Set<Long> getOnlineUserIds() {
        return userSessionCount.keySet();
    }

    /**
     * 获取用户会话数量
     *
     * @param userId 用户ID
     * @return 会话数量
     */
    public int getUserSessionCount(Long userId) {
        return userSessionCount.getOrDefault(userId, 0);
    }

    @PostConstruct
    public void init() {
        log.info("ImWebSocketStatusManager 初始化完成");
    }
}
