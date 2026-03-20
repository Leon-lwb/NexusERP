package com.nexus.im.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.im.domain.ImMessage;

import java.util.List;

/**
 * IM消息服务接口
 *
 * @author nexus
 */
public interface ImMessageService extends IService<ImMessage> {
    
    String sendPrivateMessage(Long fromUserId, Long toUserId, String content, String messageType);
    
    String sendGroupMessage(Long fromUserId, String groupId, String content, String messageType);
    
    List<ImMessage> getPrivateMessageHistory(Long userId1, Long userId2, Integer pageNum, Integer pageSize);
    
    List<ImMessage> getPrivateConversations(Long userId);
    
    int getUnreadCount(Long userId, Long toUserId, String groupId);
    
    int markAsRead(Long userId, Long toUserId, String groupId);
    
    List<ImMessage> getGroupMessageHistory(String groupId, Integer pageNum, Integer pageSize);
    
    void markAsRead(String messageId, Long userId);
    
    boolean revokeMessage(String messageId, Long userId);
    
    Integer getUnreadCount(Long userId);
    
    Integer getUnreadCount(Long userId, Long targetUserId);
    
    Integer getGroupUnreadCount(Long userId, String groupId);
}
