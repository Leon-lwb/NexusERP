package com.nexus.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.im.domain.ImFriend;
import com.nexus.im.mapper.ImFriendMapper;
import com.nexus.im.service.ImFriendService;
import com.nexus.im.service.ImUserStatusService;
import com.nexus.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM好友服务实现
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImFriendServiceImpl extends ServiceImpl<ImFriendMapper, ImFriend> implements ImFriendService {

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ImUserStatusService imUserStatusService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public List<ImFriend> getFriendList(Long userId) {
        LambdaQueryWrapper<ImFriend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImFriend::getUserId, userId)
               .eq(ImFriend::getStatus, "accepted")
               .orderByDesc(ImFriend::getLastChatTime);
        return list(wrapper);
    }
    
    /**
     * 获取好友列表（包含详细信息和在线状态）
     */
    public List<Map<String, Object>> getFriendListWithInfo(Long userId) {
        LambdaQueryWrapper<ImFriend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImFriend::getUserId, userId)
               .eq(ImFriend::getStatus, "accepted")
               .orderByDesc(ImFriend::getLastChatTime);
        List<ImFriend> friends = list(wrapper);
        log.info("获取好友列表: userId={}, 好友数量={}", userId, friends.size());

        // 批量获取好友的在线状态和最后活跃时间
        List<Long> friendUserIds = new ArrayList<>();
        for (ImFriend friend : friends) {
            friendUserIds.add(friend.getFriendUserId());
        }
        Map<Long, Boolean> onlineStatusMap = imUserStatusService.batchGetOnlineStatus(friendUserIds);
        Map<Long, LocalDateTime> lastActiveTimeMap = imUserStatusService.batchGetLastActiveTime(friendUserIds);
        log.info("在线状态查询结果: {}", onlineStatusMap);
        log.info("最后活跃时间查询结果: {}", lastActiveTimeMap);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ImFriend friend : friends) {
            Map<String, Object> friendMap = new HashMap<>();
            friendMap.put("id", friend.getId());
            friendMap.put("userId", friend.getUserId());
            friendMap.put("friendUserId", friend.getFriendUserId());
            friendMap.put("remark", friend.getRemark());
            friendMap.put("groupName", friend.getGroupName());
            friendMap.put("addTime", friend.getAddTime());
            friendMap.put("status", friend.getStatus());
            friendMap.put("isTop", friend.getIsTop());
            friendMap.put("isMuted", friend.getIsMuted());
            friendMap.put("lastChatTime", friend.getLastChatTime());

            // 获取好友用户信息
            SysUser friendUser = userService.selectUserById(friend.getFriendUserId());
            if (friendUser != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userId", friendUser.getUserId());
                userInfo.put("userName", friendUser.getUserName());
                userInfo.put("nickName", friendUser.getNickName());
                userInfo.put("avatar", friendUser.getAvatar());
                userInfo.put("email", friendUser.getEmail());
                userInfo.put("phonenumber", friendUser.getPhonenumber());
                friendMap.put("friendInfo", userInfo);
            }

            // 添加在线状态和最后活跃时间
            Long friendId = friend.getFriendUserId();
            boolean isOnline = onlineStatusMap.getOrDefault(friendId, false);
            friendMap.put("onlineStatus", isOnline ? "online" : "offline");

            LocalDateTime lastActiveTime = lastActiveTimeMap.get(friendId);
            if (lastActiveTime != null) {
                friendMap.put("lastActiveTime", lastActiveTime.format(DATE_TIME_FORMATTER));
            } else {
                friendMap.put("lastActiveTime", null);
            }

            result.add(friendMap);
        }

        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addFriend(Long userId, Long friendUserId, String remark) {
        try {
            if (isFriend(userId, friendUserId)) {
                log.warn("用户 {} 和 {} 已经是好友", userId, friendUserId);
                return false;
            }
            
            ImFriend friend = new ImFriend();
            friend.setUserId(userId);
            friend.setFriendUserId(friendUserId);
            friend.setRemark(remark);
            friend.setGroupName("默认分组");
            friend.setAddTime(LocalDateTime.now());
            friend.setStatus("accepted");
            friend.setIsTop(0);
            friend.setIsMuted(0);
            
            return save(friend);
        } catch (Exception e) {
            log.error("添加好友失败: {} -> {}, 错误: {}", userId, friendUserId, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeFriend(Long userId, Long friendUserId) {
        try {
            LambdaQueryWrapper<ImFriend> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImFriend::getUserId, userId)
                   .eq(ImFriend::getFriendUserId, friendUserId);
            
            return remove(wrapper);
        } catch (Exception e) {
            log.error("删除好友失败: {} -> {}, 错误: {}", userId, friendUserId, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean isFriend(Long userId, Long friendUserId) {
        LambdaQueryWrapper<ImFriend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImFriend::getUserId, userId)
               .eq(ImFriend::getFriendUserId, friendUserId)
               .eq(ImFriend::getStatus, "accepted");
        
        return count(wrapper) > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateFriendRemark(Long userId, Long friendUserId, String remark) {
        try {
            LambdaQueryWrapper<ImFriend> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImFriend::getUserId, userId)
                   .eq(ImFriend::getFriendUserId, friendUserId);
            
            ImFriend friend = getOne(wrapper);
            if (friend != null) {
                friend.setRemark(remark);
                return updateById(friend);
            }
            return false;
        } catch (Exception e) {
            log.error("更新好友备注失败: {} -> {}, 错误: {}", userId, friendUserId, e.getMessage(), e);
            return false;
        }
    }
}
