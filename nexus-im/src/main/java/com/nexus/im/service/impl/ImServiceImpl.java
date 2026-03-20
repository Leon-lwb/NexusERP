package com.nexus.im.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.utils.StringUtils;
import com.nexus.im.config.ImProperties;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.service.ImGroupMemberService;
import com.nexus.im.service.ImGroupService;
import com.nexus.im.service.ImMessageService;
import com.nexus.im.service.ImUserService;
import com.nexus.im.service.ImService;
import com.nexus.common.websocket.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IM服务实现类
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImProperties imProperties;
    private final ImMessageService imMessageService;
    private final ImUserService imUserService;
    private final ImGroupService imGroupService;
    private final ImGroupMemberService imGroupMemberService;
    private final WebSocketServer webSocketServer;

    @Override
    public AjaxResult loginToIm(SysUser user) {
        try {
            if (!imProperties.isEnabled()) {
                log.warn("IM功能未启用");
                return AjaxResult.error("IM功能未启用");
            }

            SysUser sysUser = imUserService.getSysUser(user.getUserId());
            if (sysUser == null) {
                return AjaxResult.error("用户不存在");
            }
            
            imUserService.updateUserStatus(user.getUserId(), "online");
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", sysUser.getUserId());
            result.put("username", sysUser.getUserName());
            result.put("nickname", sysUser.getNickName());
            result.put("avatar", sysUser.getAvatar());
            result.put("status", "online");
            
            log.info("用户登录IM系统成功: {} ({})", user.getUserId(), user.getUserName());
            return AjaxResult.success(result);
        } catch (Exception e) {
            log.error("用户登录IM系统失败: {}", e.getMessage(), e);
            return AjaxResult.error("登录IM系统失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult logoutFromIm(Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.success();
            }

            log.info("用户登出IM系统成功: {}", userId);
            return AjaxResult.success();
        } catch (Exception e) {
            log.error("用户登出IM系统失败: {}", e.getMessage(), e);
            return AjaxResult.error("登出IM系统失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult syncUserToIm(SysUser user) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.success();
            }
            log.info("用户已集成到IM系统: {}", user.getUserName());
            return AjaxResult.success();
        } catch (Exception e) {
            log.error("用户同步到IM系统失败: {}", e.getMessage(), e);
            return AjaxResult.error("同步用户失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult batchSyncUsersToIm(List<SysUser> users) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.success();
            }
            log.info("批量用户已集成到IM系统，数量: {}", users.size());
            return AjaxResult.success();
        } catch (Exception e) {
            log.error("批量同步用户到IM系统失败: {}", e.getMessage(), e);
            return AjaxResult.error("批量同步用户失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult sendMessage(Long fromUserId, Long toUserId, String content, String messageType) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            String messageId = imMessageService.sendPrivateMessage(fromUserId, toUserId, content, messageType);
            
            ImMessage message = imMessageService.getById(messageId);
            if (message != null) {
                SysUser fromUser = imUserService.getSysUser(fromUserId);
                SysUser toUser = imUserService.getSysUser(toUserId);
                
                log.info("发送消息 - fromUser: {}, toUser: {}", fromUser != null ? fromUser.getUserName() : "null", toUser != null ? toUser.getUserName() : "null");
                
                if (fromUser != null && toUser != null) {
                    JSONObject imMessage = new JSONObject();
                    imMessage.put("type", "new_message");
                    imMessage.put("messageId", message.getMessageId());
                    imMessage.put("fromUserId", message.getFromUserId());
                    imMessage.put("toUserId", message.getToUserId());
                    imMessage.put("content", message.getContent());
                    imMessage.put("messageType", message.getMessageType());
                    imMessage.put("createTime", message.getCreateTime());
                    imMessage.put("senderName", fromUser.getNickName() != null ? fromUser.getNickName() : fromUser.getUserName());
                    imMessage.put("senderAvatar", fromUser.getAvatar());
                    
                    String jsonMessage = imMessage.toJSONString();
                    log.info("推送WebSocket消息 - userName: {}, message: {}", toUser.getUserName(), jsonMessage);
                    webSocketServer.sendMessage(toUser.getUserName(), jsonMessage);
                } else {
                    log.warn("发送消息失败 - fromUser或toUser为空: fromUser={}, toUser={}", fromUser, toUser);
                }
            }
            
            log.info("发送私聊消息成功: {} -> {}, 消息ID: {}", fromUserId, toUserId, messageId);
            return AjaxResult.success();
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage(), e);
            return AjaxResult.error("发送消息失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult sendGroupMessage(Long fromUserId, String groupId, String content, String messageType) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            String messageId = imMessageService.sendGroupMessage(fromUserId, groupId, content, messageType);
            
            ImMessage message = imMessageService.getById(messageId);
            if (message != null) {
                List<ImGroupMember> members = imGroupService.getGroupMembers(groupId);
                if (members != null && !members.isEmpty()) {
                    SysUser fromUser = imUserService.getSysUser(fromUserId);
                    
                    JSONObject groupMessage = new JSONObject();
                    groupMessage.put("type", "new_group_message");
                    groupMessage.put("messageId", message.getMessageId());
                    groupMessage.put("fromUserId", message.getFromUserId());
                    groupMessage.put("groupId", message.getGroupId());
                    groupMessage.put("content", message.getContent());
                    groupMessage.put("messageType", message.getMessageType());
                    groupMessage.put("createTime", message.getCreateTime());
                    groupMessage.put("senderName", fromUser != null ? fromUser.getNickName() : "未知用户");
                    groupMessage.put("senderAvatar", fromUser != null ? fromUser.getAvatar() : "");
                    
                    for (ImGroupMember member : members) {
                        if (!member.getUserId().equals(fromUserId)) {
                            SysUser memberUser = imUserService.getSysUser(member.getUserId());
                            if (memberUser != null) {
                                webSocketServer.sendMessage(memberUser.getUserName(), groupMessage.toJSONString());
                            }
                        }
                    }
                }
            }

            log.info("发送群组消息成功: {} -> {}, 消息ID: {}", fromUserId, groupId, messageId);
            return AjaxResult.success();
        } catch (Exception e) {
            log.error("发送群组消息失败: {}", e.getMessage(), e);
            return AjaxResult.error("发送群组消息失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getUserImInfo(Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            SysUser sysUser = imUserService.getSysUser(userId);
            if (sysUser == null) {
                return AjaxResult.error("用户不存在");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("userId", sysUser.getUserId());
            result.put("username", sysUser.getUserName());
            result.put("nickname", sysUser.getNickName());
            result.put("avatar", sysUser.getAvatar());
            result.put("email", sysUser.getEmail());

            return AjaxResult.success(result);
        } catch (Exception e) {
            log.error("获取用户IM信息失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取用户IM信息失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getImUserList() {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            List<SysUser> sysUsers = imUserService.getOnlineUsers();
            List<Map<String, Object>> userList = new ArrayList<>();
            
            for (SysUser user : sysUsers) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userId", user.getUserId());
                userInfo.put("userName", user.getUserName());
                userInfo.put("nickName", user.getNickName());
                userInfo.put("avatar", user.getAvatar());
                userInfo.put("imStatus", "offline");
                userInfo.put("status", user.getStatus());
                userList.add(userInfo);
            }

            return AjaxResult.success(userList);
        } catch (Exception e) {
            log.error("获取IM用户列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取IM用户列表失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getMessageHistory(Long userId, String targetId, Integer pageNum, Integer pageSize) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            List<ImMessage> messages;
            final SysUser currentUser = imUserService.getSysUser(userId);
            final SysUser targetUser;
            
            if (targetId.startsWith("group_")) {
                String groupId = targetId;
                messages = imMessageService.getGroupMessageHistory(groupId, pageNum, pageSize);
                targetUser = null;
            } else {
                Long targetUserId;
                if (targetId.startsWith("private_")) {
                    targetUserId = Long.valueOf(targetId.substring(8));
                } else {
                    targetUserId = Long.valueOf(targetId);
                }
                messages = imMessageService.getPrivateMessageHistory(userId, targetUserId, pageNum, pageSize);
                targetUser = imUserService.getSysUser(targetUserId);
            }
            
            List<Map<String, Object>> messageList = messages.stream().map(msg -> {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("messageId", msg.getMessageId());
                messageMap.put("fromUserId", msg.getFromUserId());
                messageMap.put("toUserId", msg.getToUserId());
                messageMap.put("content", msg.getContent());
                messageMap.put("messageType", msg.getMessageType());
                messageMap.put("createTime", msg.getCreateTime());
                messageMap.put("isRead", msg.getIsRead());
                messageMap.put("status", msg.getStatus());
                
                if (msg.getFromUserId().equals(userId)) {
                    messageMap.put("senderName", currentUser != null ? currentUser.getNickName() : "我");
                    messageMap.put("senderAvatar", currentUser != null ? currentUser.getAvatar() : "");
                } else {
                    messageMap.put("senderName", targetUser != null ? targetUser.getNickName() : "对方");
                    messageMap.put("senderAvatar", targetUser != null ? targetUser.getAvatar() : "");
                }
                
                return messageMap;
            }).collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("records", messageList);
            result.put("list", messageList);
            result.put("total", messageList.size());
            
            return AjaxResult.success(result);
        } catch (Exception e) {
            log.error("获取消息历史失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取消息历史失败: " + e.getMessage());
        }
    }

    @Override
    public String getMessageId(Long fromUserId, Long toUserId, String content, String messageType) {
        try {
            LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImMessage::getFromUserId, fromUserId)
                   .eq(ImMessage::getToUserId, toUserId)
                   .orderByDesc(ImMessage::getCreateTime)
                   .last("LIMIT 1");
            
            ImMessage message = imMessageService.getOne(wrapper);
            return message != null ? message.getMessageId() : null;
        } catch (Exception e) {
            log.error("获取消息ID失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult createGroup(Long ownerId, String groupName, String description, String avatar, List<Long> memberIds) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            String groupId = "group_" + System.currentTimeMillis() + "_" + ownerId;
            
            ImGroup group = new ImGroup();
            group.setGroupId(groupId);
            group.setGroupName(groupName);
            group.setDescription(description);
            group.setAvatar(avatar);
            group.setOwnerId(ownerId);
            group.setGroupType("normal");
            group.setMaxMembers(500);
            group.setMemberCount(1);
            group.setStatus("active");
            group.setAllowInvite(1);
            group.setAllowMemberModify(0);
            group.setAllowMemberInvite(1);
            
            boolean groupSaved = imGroupService.save(group);
            if (!groupSaved) {
                return AjaxResult.error("创建群组失败");
            }
            
            ImGroupMember ownerMember = new ImGroupMember();
            ownerMember.setGroupId(groupId);
            ownerMember.setUserId(ownerId);
            ownerMember.setGroupNickname("");
            ownerMember.setRole("owner");
            ownerMember.setIsMuted(0);
            ownerMember.setEnabled(1);
            
            imGroupMemberService.save(ownerMember);
            
            int actualMemberCount = 1;
            if (memberIds != null && !memberIds.isEmpty()) {
                for (Long memberId : memberIds) {
                    if (!memberId.equals(ownerId)) {
                        ImGroupMember member = new ImGroupMember();
                        member.setGroupId(groupId);
                        member.setUserId(memberId);
                        member.setGroupNickname("");
                        member.setRole("member");
                        member.setIsMuted(0);
                        member.setEnabled(1);
                        
                        if (imGroupMemberService.save(member)) {
                            actualMemberCount++;
                        }
                    }
                }
                group.setMemberCount(actualMemberCount);
                imGroupService.updateById(group);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("groupId", groupId);
            result.put("groupName", groupName);
            result.put("description", description);
            result.put("avatar", avatar);
            result.put("ownerId", ownerId);
            result.put("memberCount", group.getMemberCount());
            
            log.info("创建群聊成功: groupId={}, groupName={}, ownerId={}", groupId, groupName, ownerId);
            return AjaxResult.success(result);
        } catch (Exception e) {
            log.error("创建群聊失败: {}", e.getMessage(), e);
            return AjaxResult.error("创建群聊失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getUserGroups(Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImGroupMember::getUserId, userId)
                   .eq(ImGroupMember::getEnabled, 1);
            
            List<ImGroupMember> memberships = imGroupMemberService.list(wrapper);
            
            List<Map<String, Object>> groups = new ArrayList<>();
            for (ImGroupMember membership : memberships) {
                ImGroup group = imGroupService.getById(membership.getGroupId());
                if (group != null && "active".equals(group.getStatus())) {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("groupId", group.getGroupId());
                    groupInfo.put("groupName", group.getGroupName());
                    groupInfo.put("avatar", group.getAvatar());
                    groupInfo.put("description", group.getDescription());
                    groupInfo.put("memberCount", group.getMemberCount());
                    groupInfo.put("role", membership.getRole());
                    groups.add(groupInfo);
                }
            }
            
            return AjaxResult.success(groups);
        } catch (Exception e) {
            log.error("获取用户群聊列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取用户群聊列表失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getConversationList(Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            List<Map<String, Object>> conversations = new ArrayList<>();
            
            List<ImMessage> privateMessages = imMessageService.getPrivateConversations(userId);
            for (ImMessage message : privateMessages) {
                Long otherUserId = message.getFromUserId().equals(userId) ? message.getToUserId() : message.getFromUserId();
                SysUser otherUser = imUserService.getSysUser(otherUserId);
                
                if (otherUser != null) {
                    int unreadCount = imMessageService.getUnreadCount(userId, otherUserId, null);
                    
                    Map<String, Object> conversation = new HashMap<>();
                    conversation.put("id", "private_" + otherUserId);
                    conversation.put("name", otherUser.getNickName() != null ? otherUser.getNickName() : otherUser.getUserName());
                    conversation.put("avatar", otherUser.getAvatar());
                    conversation.put("lastMessage", message.getContent());
                    conversation.put("lastTime", message.getCreateTime());
                    conversation.put("unreadCount", unreadCount);
                    conversation.put("type", "private");
                    conversation.put("userId", otherUserId);
                    conversations.add(conversation);
                }
            }
            
            LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImGroupMember::getUserId, userId)
                   .eq(ImGroupMember::getEnabled, 1);
            
            List<ImGroupMember> memberships = imGroupMemberService.list(wrapper);
            for (ImGroupMember membership : memberships) {
                ImGroup group = imGroupService.getById(membership.getGroupId());
                if (group != null && "active".equals(group.getStatus())) {
                    List<ImMessage> groupMessages = imMessageService.getGroupMessageHistory(group.getGroupId(), 1, 1);
                    int unreadCount = imMessageService.getUnreadCount(userId, null, group.getGroupId());
                    
                    Map<String, Object> conversation = new HashMap<>();
                    conversation.put("id", group.getGroupId());
                    conversation.put("name", group.getGroupName());
                    conversation.put("avatar", group.getAvatar());
                    conversation.put("lastMessage", groupMessages.isEmpty() ? "群聊消息..." : groupMessages.get(0).getContent());
                    conversation.put("lastTime", groupMessages.isEmpty() ? group.getCreateTime() : groupMessages.get(0).getCreateTime());
                    conversation.put("unreadCount", unreadCount);
                    conversation.put("type", "group");
                    conversation.put("groupId", group.getGroupId());
                    conversation.put("memberCount", group.getMemberCount());
                    conversations.add(conversation);
                }
            }
            
            conversations.sort((a, b) -> {
                Object timeA = a.get("lastTime");
                Object timeB = b.get("lastTime");
                if (timeA instanceof LocalDateTime && timeB instanceof LocalDateTime) {
                    return ((LocalDateTime) timeB).compareTo((LocalDateTime) timeA);
                }
                return 0;
            });
            
            return AjaxResult.success(conversations);
        } catch (Exception e) {
            log.error("获取会话列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult markMessagesAsRead(Long userId, Long toUserId, String groupId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            imMessageService.markAsRead(userId, toUserId, groupId);
            return AjaxResult.success();
        } catch (Exception e) {
            log.error("标记消息已读失败: {}", e.getMessage(), e);
            return AjaxResult.error("标记消息已读失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getUnreadMessages(Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            List<Map<String, Object>> messageList = new ArrayList<>();

            LambdaQueryWrapper<ImMessage> privateWrapper = new LambdaQueryWrapper<>();
            privateWrapper.eq(ImMessage::getToUserId, userId)
                    .isNull(ImMessage::getGroupId)
                    .eq(ImMessage::getIsRead, 0)
                    .eq(ImMessage::getIsRevoked, 0)
                    .orderByAsc(ImMessage::getCreateTime);

            List<ImMessage> privateMessages = imMessageService.list(privateWrapper);
            for (ImMessage msg : privateMessages) {
                SysUser sender = imUserService.getSysUser(msg.getFromUserId());
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("messageId", msg.getMessageId());
                messageMap.put("fromUserId", msg.getFromUserId());
                messageMap.put("toUserId", msg.getToUserId());
                messageMap.put("content", msg.getContent());
                messageMap.put("messageType", msg.getMessageType());
                messageMap.put("createTime", msg.getCreateTime());
                messageMap.put("isRead", msg.getIsRead());
                messageMap.put("status", msg.getStatus());
                messageMap.put("senderName", sender != null ? sender.getNickName() : "用户" + msg.getFromUserId());
                messageMap.put("senderAvatar", sender != null ? sender.getAvatar() : "");
                messageList.add(messageMap);
            }

            LambdaQueryWrapper<ImGroupMember> memberWrapper = new LambdaQueryWrapper<>();
            memberWrapper.eq(ImGroupMember::getUserId, userId).eq(ImGroupMember::getEnabled, 1);
            List<ImGroupMember> memberships = imGroupMemberService.list(memberWrapper);
            if (memberships != null && !memberships.isEmpty()) {
                List<String> groupIds = memberships.stream().map(ImGroupMember::getGroupId).collect(Collectors.toList());
                LambdaQueryWrapper<ImMessage> groupWrapper = new LambdaQueryWrapper<>();
                groupWrapper.in(ImMessage::getGroupId, groupIds)
                        .ne(ImMessage::getFromUserId, userId)
                        .eq(ImMessage::getIsRead, 0)
                        .eq(ImMessage::getIsRevoked, 0)
                        .orderByAsc(ImMessage::getCreateTime);
                List<ImMessage> groupMessages = imMessageService.list(groupWrapper);
                for (ImMessage msg : groupMessages) {
                    SysUser sender = imUserService.getSysUser(msg.getFromUserId());
                    Map<String, Object> messageMap = new HashMap<>();
                    messageMap.put("messageId", msg.getMessageId());
                    messageMap.put("fromUserId", msg.getFromUserId());
                    messageMap.put("groupId", msg.getGroupId());
                    messageMap.put("content", msg.getContent());
                    messageMap.put("messageType", msg.getMessageType());
                    messageMap.put("createTime", msg.getCreateTime());
                    messageMap.put("isRead", msg.getIsRead());
                    messageMap.put("status", msg.getStatus());
                    messageMap.put("senderName", sender != null ? sender.getNickName() : "用户" + msg.getFromUserId());
                    messageMap.put("senderAvatar", sender != null ? sender.getAvatar() : "");
                    messageList.add(messageMap);
                }
            }

            messageList.sort((a, b) -> {
                Object timeA = a.get("createTime");
                Object timeB = b.get("createTime");
                if (timeA instanceof LocalDateTime && timeB instanceof LocalDateTime) {
                    return ((LocalDateTime) timeA).compareTo((LocalDateTime) timeB);
                }
                return 0;
            });

            return AjaxResult.success(messageList);
        } catch (Exception e) {
            log.error("获取未读消息失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取未读消息失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getGroupInfo(String groupId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            ImGroup group = imGroupService.getById(groupId);
            if (group == null) {
                return AjaxResult.error("群组不存在");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("groupId", group.getGroupId());
            result.put("groupName", group.getGroupName());
            result.put("avatar", group.getAvatar());
            result.put("description", group.getDescription());
            result.put("ownerId", group.getOwnerId());
            result.put("memberCount", group.getMemberCount());
            result.put("status", group.getStatus());

            return AjaxResult.success(result);
        } catch (Exception e) {
            log.error("获取群聊详情失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取群聊详情失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getGroupMembers(String groupId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImGroupMember::getGroupId, groupId)
                   .eq(ImGroupMember::getEnabled, 1)
                   .orderByAsc(ImGroupMember::getRole);
            
            List<ImGroupMember> members = imGroupMemberService.list(wrapper);
            
            List<Map<String, Object>> memberList = new ArrayList<>();
            for (ImGroupMember member : members) {
                SysUser user = imUserService.getSysUser(member.getUserId());
                
                Map<String, Object> memberInfo = new HashMap<>();
                memberInfo.put("userId", member.getUserId());
                memberInfo.put("role", member.getRole());
                memberInfo.put("joinTime", member.getJoinTime());
                
                if (user != null) {
                    memberInfo.put("userName", user.getUserName());
                    memberInfo.put("nickName", user.getNickName());
                    memberInfo.put("avatar", user.getAvatar());
                } else {
                    memberInfo.put("userName", "用户" + member.getUserId());
                    memberInfo.put("nickName", "用户" + member.getUserId());
                    memberInfo.put("avatar", "");
                }
                
                memberList.add(memberInfo);
            }

            return AjaxResult.success(memberList);
        } catch (Exception e) {
            log.error("获取群成员列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取群成员列表失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult addGroupMembers(String groupId, Long operatorId, List<Long> memberIds) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            ImGroup group = imGroupService.getById(groupId);
            if (group == null) {
                return AjaxResult.error("群组不存在");
            }

            int addedCount = 0;
            for (Long memberId : memberIds) {
                LambdaQueryWrapper<ImGroupMember> memberWrapper = new LambdaQueryWrapper<>();
                memberWrapper.eq(ImGroupMember::getGroupId, groupId)
                            .eq(ImGroupMember::getUserId, memberId);
                
                ImGroupMember existingMember = imGroupMemberService.getOne(memberWrapper);
                if (existingMember == null) {
                    ImGroupMember member = new ImGroupMember();
                    member.setGroupId(groupId);
                    member.setUserId(memberId);
                    member.setGroupNickname("");
                    member.setRole("member");
                    member.setIsMuted(0);
                    member.setEnabled(1);
                    
                    imGroupMemberService.save(member);
                    addedCount++;
                }
            }

            group.setMemberCount(group.getMemberCount() + addedCount);
            imGroupService.updateById(group);

            return AjaxResult.success();
        } catch (Exception e) {
            log.error("添加群成员失败: {}", e.getMessage(), e);
            return AjaxResult.error("添加群成员失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult removeGroupMember(String groupId, Long operatorId, Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            ImGroup group = imGroupService.getById(groupId);
            if (group == null) {
                return AjaxResult.error("群组不存在");
            }

            LambdaQueryWrapper<ImGroupMember> memberWrapper = new LambdaQueryWrapper<>();
            memberWrapper.eq(ImGroupMember::getGroupId, groupId)
                        .eq(ImGroupMember::getUserId, userId)
                        .eq(ImGroupMember::getEnabled, 1);
            
            ImGroupMember targetMember = imGroupMemberService.getOne(memberWrapper);
            if (targetMember == null) {
                return AjaxResult.error("该用户不是群成员");
            }

            imGroupMemberService.removeById(targetMember.getId());

            group.setMemberCount(group.getMemberCount() - 1);
            imGroupService.updateById(group);

            return AjaxResult.success();
        } catch (Exception e) {
            log.error("移除群成员失败: {}", e.getMessage(), e);
            return AjaxResult.error("移除群成员失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult updateGroupInfo(String groupId, Long operatorId, String groupName, String description, String avatar) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            ImGroup group = imGroupService.getById(groupId);
            if (group == null) {
                return AjaxResult.error("群组不存在");
            }

            if (StringUtils.isNotBlank(groupName)) {
                group.setGroupName(groupName);
            }
            if (StringUtils.isNotBlank(description)) {
                group.setDescription(description);
            }
            if (StringUtils.isNotBlank(avatar)) {
                group.setAvatar(avatar);
            }

            imGroupService.updateById(group);

            return AjaxResult.success();
        } catch (Exception e) {
            log.error("更新群信息失败: {}", e.getMessage(), e);
            return AjaxResult.error("更新群信息失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult quitGroup(String groupId, Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            ImGroup group = imGroupService.getById(groupId);
            if (group == null) {
                return AjaxResult.error("群组不存在");
            }

            LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImGroupMember::getGroupId, groupId)
                   .eq(ImGroupMember::getUserId, userId)
                   .eq(ImGroupMember::getEnabled, 1);
            
            ImGroupMember member = imGroupMemberService.getOne(wrapper);
            if (member == null) {
                return AjaxResult.error("您不是群成员");
            }

            if ("owner".equals(member.getRole())) {
                return AjaxResult.error("群主不能直接退出，请先转让群主或解散群");
            }

            member.setEnabled(0);
            imGroupMemberService.updateById(member);

            group.setMemberCount(group.getMemberCount() - 1);
            imGroupService.updateById(group);

            return AjaxResult.success();
        } catch (Exception e) {
            log.error("退出群聊失败: {}", e.getMessage(), e);
            return AjaxResult.error("退出群聊失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult disbandGroup(String groupId, Long operatorId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.error("IM功能未启用");
            }

            ImGroup group = imGroupService.getById(groupId);
            if (group == null) {
                return AjaxResult.error("群组不存在");
            }

            if (!operatorId.equals(group.getOwnerId())) {
                return AjaxResult.error("只有群主可以解散群聊");
            }

            group.setStatus("disbanded");
            imGroupService.updateById(group);

            LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImGroupMember::getGroupId, groupId)
                   .eq(ImGroupMember::getEnabled, 1);
            
            List<ImGroupMember> members = imGroupMemberService.list(wrapper);
            for (ImGroupMember member : members) {
                member.setEnabled(0);
                imGroupMemberService.updateById(member);
            }

            return AjaxResult.success();
        } catch (Exception e) {
            log.error("解散群聊失败: {}", e.getMessage(), e);
            return AjaxResult.error("解散群聊失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxResult getUnreadCount(Long userId) {
        try {
            if (!imProperties.isEnabled()) {
                return AjaxResult.success(0);
            }

            LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImMessage::getToUserId, userId)
                   .eq(ImMessage::getIsRead, 0);
            
            long count = imMessageService.count(wrapper);
            return AjaxResult.success(count);
        } catch (Exception e) {
            log.error("获取未读消息数失败: {}", e.getMessage(), e);
            return AjaxResult.success(0);
        }
    }
}
