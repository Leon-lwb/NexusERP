package com.nexus.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.service.ImMessageService;
import com.nexus.im.service.ImUserService;
import com.nexus.im.service.ImGroupService;
import com.nexus.common.core.domain.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * IM消息服务实现
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageServiceImpl extends ServiceImpl<ImMessageMapper, ImMessage> implements ImMessageService {
    
    private final ImUserService imUserService;
    private final ImGroupService imGroupService;
    
    @Override
    public String sendPrivateMessage(Long fromUserId, Long toUserId, String content, String messageType) {
        try {
            log.info("开始发送私聊消息: {} -> {}, 内容: {}", fromUserId, toUserId, content);
            
            SysUser fromUser = imUserService.getSysUser(fromUserId);
            if (fromUser == null) {
                log.error("发送者用户不存在: {}", fromUserId);
                throw new RuntimeException("发送者用户不存在");
            }
            
            SysUser toUser = imUserService.getSysUser(toUserId);
            if (toUser == null) {
                log.error("接收者用户不存在: {}", toUserId);
                throw new RuntimeException("接收者用户不存在");
            }
            
            ImMessage message = new ImMessage();
            message.setMessageId(UUID.randomUUID().toString());
            message.setFromUserId(fromUserId);
            message.setToUserId(toUserId);
            message.setContent(content);
            message.setMessageType(messageType);
            message.setStatus("sent");
            message.setCreateTime(LocalDateTime.now());
            message.setIsRead(0);
            message.setIsRevoked(0);
            message.setSource("user");
            
            boolean saveResult = save(message);
            if (!saveResult) {
                throw new RuntimeException("保存消息失败");
            }
            
            log.info("发送私聊消息成功: {} -> {}, 消息ID: {}", fromUserId, toUserId, message.getMessageId());
            return message.getMessageId();
        } catch (Exception e) {
            log.error("发送私聊消息失败: {} -> {}, 错误: {}", fromUserId, toUserId, e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String sendGroupMessage(Long fromUserId, String groupId, String content, String messageType) {
        if (imGroupService.getById(groupId) == null) {
            throw new RuntimeException("群组不存在");
        }
        
        if (!imGroupService.isMember(groupId, fromUserId)) {
            throw new RuntimeException("用户不在群组中");
        }
        
        ImMessage message = new ImMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setFromUserId(fromUserId);
        message.setGroupId(groupId);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setStatus("sent");
        message.setCreateTime(LocalDateTime.now());
        message.setIsRead(0);
        message.setIsRevoked(0);
        message.setSource("user");
        
        save(message);
        
        log.info("发送群组消息: {} -> {}, 内容: {}", fromUserId, groupId, content);
        return message.getMessageId();
    }
    
    @Override
    public List<ImMessage> getPrivateMessageHistory(Long userId1, Long userId2, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(ImMessage::getFromUserId, userId1).eq(ImMessage::getToUserId, userId2)
                .or()
                .eq(ImMessage::getFromUserId, userId2).eq(ImMessage::getToUserId, userId1))
                .orderByDesc(ImMessage::getCreateTime);
        
        Page<ImMessage> page = new Page<>(pageNum, pageSize);
        IPage<ImMessage> result = page(page, wrapper);
        
        return result.getRecords();
    }
    
    @Override
    public List<ImMessage> getPrivateConversations(Long userId) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(ImMessage::getFromUserId, userId)
                .or()
                .eq(ImMessage::getToUserId, userId))
                .isNull(ImMessage::getGroupId)
                .orderByDesc(ImMessage::getCreateTime);
        
        List<ImMessage> allMessages = list(wrapper);
        
        Map<Long, ImMessage> latestMessages = new HashMap<>();
        for (ImMessage message : allMessages) {
            Long otherUserId = message.getFromUserId().equals(userId) ? 
                message.getToUserId() : message.getFromUserId();
            
            if (!latestMessages.containsKey(otherUserId) || 
                message.getCreateTime().isAfter(latestMessages.get(otherUserId).getCreateTime())) {
                latestMessages.put(otherUserId, message);
            }
        }
        
        List<ImMessage> result = new ArrayList<>(latestMessages.values());
        result.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
        
        return result;
    }
    
    @Override
    public int getUnreadCount(Long userId, Long toUserId, String groupId) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        
        if (toUserId != null) {
            wrapper.and(w -> w.eq(ImMessage::getFromUserId, toUserId).eq(ImMessage::getToUserId, userId))
                   .isNull(ImMessage::getGroupId)
                   .eq(ImMessage::getIsRead, 0);
        } else if (groupId != null) {
            wrapper.eq(ImMessage::getGroupId, groupId)
                   .ne(ImMessage::getFromUserId, userId)
                   .eq(ImMessage::getIsRead, 0);
        } else {
            return 0;
        }
        
        return Math.toIntExact(count(wrapper));
    }
    
    @Override
    public int markAsRead(Long userId, Long toUserId, String groupId) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        
        if (toUserId != null) {
            wrapper.and(w -> w.eq(ImMessage::getFromUserId, toUserId).eq(ImMessage::getToUserId, userId))
                   .isNull(ImMessage::getGroupId)
                   .eq(ImMessage::getIsRead, 0);
        } else if (groupId != null) {
            wrapper.eq(ImMessage::getGroupId, groupId)
                   .ne(ImMessage::getFromUserId, userId)
                   .eq(ImMessage::getIsRead, 0);
        } else {
            return 0;
        }
        
        ImMessage updateMessage = new ImMessage();
        updateMessage.setIsRead(1);
        
        update(updateMessage, wrapper);
        return Math.toIntExact(count(wrapper));
    }
    
    @Override
    public List<ImMessage> getGroupMessageHistory(String groupId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getGroupId, groupId)
                .orderByDesc(ImMessage::getCreateTime);
        
        Page<ImMessage> page = new Page<>(pageNum, pageSize);
        IPage<ImMessage> result = page(page, wrapper);
        return result.getRecords();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(String messageId, Long userId) {
        ImMessage message = getById(messageId);
        if (message != null && !message.getFromUserId().equals(userId)) {
            message.setIsRead(1);
            updateById(message);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeMessage(String messageId, Long userId) {
        ImMessage message = getById(messageId);
        if (message != null && message.getFromUserId().equals(userId)) {
            if (message.getCreateTime().isAfter(LocalDateTime.now().minusMinutes(2))) {
                message.setIsRevoked(1);
                message.setRevokeTime(LocalDateTime.now());
                updateById(message);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public Integer getUnreadCount(Long userId) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getToUserId, userId)
                .eq(ImMessage::getIsRead, 0)
                .eq(ImMessage::getIsRevoked, 0);
        return Math.toIntExact(count(wrapper));
    }
    
    @Override
    public Integer getUnreadCount(Long userId, Long targetUserId) {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getFromUserId, targetUserId)
                .eq(ImMessage::getToUserId, userId)
                .eq(ImMessage::getIsRead, 0)
                .eq(ImMessage::getIsRevoked, 0);
        return Math.toIntExact(count(wrapper));
    }
    
    @Override
    public Integer getGroupUnreadCount(Long userId, String groupId) {
        LocalDateTime joinTime = imGroupService.getJoinTime(groupId, userId);
        if (joinTime == null) {
            return 0;
        }
        
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getGroupId, groupId)
                .ne(ImMessage::getFromUserId, userId)
                .gt(ImMessage::getCreateTime, joinTime)
                .eq(ImMessage::getIsRevoked, 0);
        return Math.toIntExact(count(wrapper));
    }
}
