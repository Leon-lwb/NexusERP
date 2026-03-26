package com.nexus.im.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * IM用户状态服务接口
 * 管理用户在线状态和最后活跃时间
 *
 * @author nexus
 */
public interface ImUserStatusService {

    /**
     * 设置用户在线
     *
     * @param userId 用户ID
     */
    void setUserOnline(Long userId);

    /**
     * 设置用户离线
     *
     * @param userId 用户ID
     */
    void setUserOffline(Long userId);

    /**
     * 更新用户最后活跃时间
     *
     * @param userId 用户ID
     */
    void updateLastActiveTime(Long userId);

    /**
     * 获取用户在线状态
     *
     * @param userId 用户ID
     * @return true-在线, false-离线
     */
    boolean isUserOnline(Long userId);

    /**
     * 获取用户最后活跃时间
     *
     * @param userId 用户ID
     * @return 最后活跃时间
     */
    LocalDateTime getLastActiveTime(Long userId);

    /**
     * 批量获取用户在线状态
     *
     * @param userIds 用户ID列表
     * @return Map<userId, 在线状态>
     */
    Map<Long, Boolean> batchGetOnlineStatus(List<Long> userIds);

    /**
     * 批量获取用户最后活跃时间
     *
     * @param userIds 用户ID列表
     * @return Map<userId, 最后活跃时间>
     */
    Map<Long, LocalDateTime> batchGetLastActiveTime(List<Long> userIds);

    /**
     * 获取在线用户数量
     *
     * @return 在线用户数量
     */
    long getOnlineUserCount();

    /**
     * 获取所有在线用户ID
     *
     * @return 在线用户ID列表
     */
    List<Long> getAllOnlineUserIds();
}
