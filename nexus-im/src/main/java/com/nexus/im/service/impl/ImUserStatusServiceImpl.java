package com.nexus.im.service.impl;

import com.nexus.common.core.redis.RedisCache;
import com.nexus.im.service.ImUserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * IM用户状态服务实现
 * 基于Redis管理用户在线状态和最后活跃时间
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImUserStatusServiceImpl implements ImUserStatusService {

    private final RedisCache redisCache;

    // Redis key前缀
    private static final String USER_ONLINE_KEY = "im:user:online:";
    private static final String USER_LAST_ACTIVE_KEY = "im:user:lastActive:";
    
    // 在线状态过期时间（分钟）- 5分钟无活动视为离线
    private static final Integer ONLINE_EXPIRE_MINUTES = 5;
    // 最后活跃时间保留时间（天）
    private static final Integer LAST_ACTIVE_RETAIN_DAYS = 7;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void setUserOnline(Long userId) {
        String key = USER_ONLINE_KEY + userId;
        redisCache.setCacheObject(key, "online", ONLINE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        updateLastActiveTime(userId);
        log.debug("用户设置在线状态: userId={}", userId);
    }

    @Override
    public void setUserOffline(Long userId) {
        String key = USER_ONLINE_KEY + userId;
        redisCache.deleteObject(key);
        // 离线时更新最后活跃时间
        updateLastActiveTime(userId);
        log.debug("用户设置离线状态: userId={}", userId);
    }

    @Override
    public void updateLastActiveTime(Long userId) {
        String key = USER_LAST_ACTIVE_KEY + userId;
        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        redisCache.setCacheObject(key, now, LAST_ACTIVE_RETAIN_DAYS, TimeUnit.DAYS);
        log.debug("更新用户最后活跃时间: userId={}, time={}", userId, now);
    }

    @Override
    public boolean isUserOnline(Long userId) {
        String key = USER_ONLINE_KEY + userId;
        Object value = redisCache.getCacheObject(key);
        return value != null && "online".equals(value.toString());
    }

    @Override
    public LocalDateTime getLastActiveTime(Long userId) {
        String key = USER_LAST_ACTIVE_KEY + userId;
        Object value = redisCache.getCacheObject(key);
        if (value != null) {
            try {
                return LocalDateTime.parse(value.toString(), DATE_TIME_FORMATTER);
            } catch (Exception e) {
                log.warn("解析最后活跃时间失败: userId={}, value={}", userId, value);
            }
        }
        return null;
    }

    @Override
    public Map<Long, Boolean> batchGetOnlineStatus(List<Long> userIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        
        for (Long userId : userIds) {
            result.put(userId, isUserOnline(userId));
        }
        return result;
    }

    @Override
    public Map<Long, LocalDateTime> batchGetLastActiveTime(List<Long> userIds) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        
        for (Long userId : userIds) {
            LocalDateTime lastActive = getLastActiveTime(userId);
            if (lastActive != null) {
                result.put(userId, lastActive);
            }
        }
        return result;
    }

    @Override
    public long getOnlineUserCount() {
        // 获取所有在线用户key
        Collection<String> keys = redisCache.keys(USER_ONLINE_KEY + "*");
        return keys != null ? keys.size() : 0;
    }

    @Override
    public List<Long> getAllOnlineUserIds() {
        Collection<String> keys = redisCache.keys(USER_ONLINE_KEY + "*");
        List<Long> userIds = new ArrayList<>();
        
        if (keys != null) {
            for (String key : keys) {
                try {
                    // 从key中提取userId: im:user:online:{userId}
                    String userIdStr = key.substring(USER_ONLINE_KEY.length());
                    userIds.add(Long.valueOf(userIdStr));
                } catch (Exception e) {
                    log.warn("解析在线用户ID失败: key={}", key);
                }
            }
        }
        return userIds;
    }
}
