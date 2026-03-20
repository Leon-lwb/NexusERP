package com.nexus.im.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.im.core.ImFriend;
import com.nexus.im.core.mapper.ImFriendMapper;
import com.nexus.im.core.service.ImFriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IM好友服务实现
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImFriendServiceImpl extends ServiceImpl<ImFriendMapper, ImFriend> implements ImFriendService {
    
    @Override
    public List<ImFriend> getFriendList(Long userId) {
        LambdaQueryWrapper<ImFriend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImFriend::getUserId, userId)
               .eq(ImFriend::getStatus, "accepted")
               .orderByDesc(ImFriend::getLastChatTime);
        return list(wrapper);
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
