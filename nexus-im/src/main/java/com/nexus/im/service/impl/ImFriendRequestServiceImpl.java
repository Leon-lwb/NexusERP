package com.nexus.im.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.websocket.websocket.WebSocketServer;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.mapper.ImFriendMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.service.ImFriendRequestService;
import com.nexus.im.service.ImFriendService;
import com.nexus.system.service.ISysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ImFriendRequestServiceImpl extends ServiceImpl<ImMessageMapper, ImMessage> implements ImFriendRequestService
{
    @Autowired
    private ImMessageMapper imMessageMapper;

    @Autowired
    private ImFriendMapper imFriendMapper;

    @Autowired
    private ImFriendService imFriendService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 发送好友申请
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult sendFriendRequest(Long fromUserId, Long toUserId, String requestMessage, String remark)
    {
        log.info("发送好友申请: from={}, to={}, message={}", fromUserId, toUserId, requestMessage);

        // 1. 检查是否已是好友
        if (imFriendService.isFriend(fromUserId, toUserId))
        {
            return AjaxResult.error("对方已是你的好友，无需重复添加");
        }

        // 2. 检查是否有待处理的申请
        if (hasPendingRequest(fromUserId, toUserId))
        {
            return AjaxResult.error("你已发送过好友申请，请等待对方处理");
        }

        // 3. 检查被申请人是否存在
        SysUser toUser = userService.selectUserById(toUserId);
        if (toUser == null)
        {
            return AjaxResult.error("用户不存在");
        }

        // 4. 获取申请人信息
        SysUser fromUser = userService.selectUserById(fromUserId);
        if (fromUser == null)
        {
            return AjaxResult.error("申请人信息不存在");
        }

        // 5. 创建好友申请消息
        ImMessage message = new ImMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setMessageType("friend_request");
        message.setContent(requestMessage);
        message.setStatus("pending");
        message.setCreateTime(LocalDateTime.now());

        // 6. 构建扩展数据
        Map<String, Object> extraData = new HashMap<>();
        
        Map<String, Object> fromUserInfo = new HashMap<>();
        fromUserInfo.put("userId", fromUser.getUserId());
        fromUserInfo.put("userName", fromUser.getUserName());
        fromUserInfo.put("nickName", fromUser.getNickName());
        fromUserInfo.put("avatar", fromUser.getAvatar());
        extraData.put("fromUserInfo", fromUserInfo);

        Map<String, Object> toUserInfo = new HashMap<>();
        toUserInfo.put("userId", toUser.getUserId());
        toUserInfo.put("userName", toUser.getUserName());
        toUserInfo.put("nickName", toUser.getNickName());
        toUserInfo.put("avatar", toUser.getAvatar());
        extraData.put("toUserInfo", toUserInfo);

        extraData.put("requestMessage", requestMessage);
        extraData.put("requestTime", LocalDateTime.now());
        extraData.put("remark", remark);

        message.setExtraData(JSON.toJSONString(extraData));

        // 7. 保存消息
        imMessageMapper.insert(message);

        // 8. 发送WebSocket通知
        try
        {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "friend_request");
            notification.put("messageId", message.getMessageId());
            notification.put("fromUserInfo", fromUserInfo);
            notification.put("content", requestMessage);
            notification.put("createTime", message.getCreateTime());

            webSocketServer.sendMessage(toUser.getUserName(), JSON.toJSONString(notification));
            log.info("发送好友申请WebSocket通知成功: toUser={}", toUser.getUserName());
        }
        catch (Exception e)
        {
            log.error("发送好友申请WebSocket通知失败", e);
        }

        return AjaxResult.success("好友申请已发送", message);
    }

    /**
     * 处理好友申请
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult handleFriendRequest(String messageId, Long userId, boolean accept, String remark)
    {
        log.info("处理好友申请: messageId={}, userId={}, accept={}", messageId, userId, accept);

        // 1. 查询消息
        ImMessage message = imMessageMapper.selectById(messageId);
        if (message == null)
        {
            return AjaxResult.error("好友申请不存在");
        }

        // 2. 验证权限
        if (!message.getToUserId().equals(userId))
        {
            return AjaxResult.error("无权处理此好友申请");
        }

        // 3. 检查状态
        if (!"pending".equals(message.getStatus()))
        {
            return AjaxResult.error("该好友申请已处理");
        }

        // 4. 更新消息状态
        message.setStatus(accept ? "accepted" : "rejected");
        message.setUpdateTime(LocalDateTime.now());

        // 5. 更新扩展数据
        Map<String, Object> extraData = JSON.parseObject(message.getExtraData(), Map.class);
        extraData.put("handleTime", LocalDateTime.now());
        if (remark != null)
        {
            extraData.put("remark", remark);
        }
        message.setExtraData(JSON.toJSONString(extraData));

        imMessageMapper.updateById(message);

        // 6. 如果同意，创建好友关系
        if (accept)
        {
            // 创建双向好友关系
            imFriendService.addFriend(message.getFromUserId(), message.getToUserId(), remark);
            imFriendService.addFriend(message.getToUserId(), message.getFromUserId(), null);

            log.info("创建好友关系成功: user1={}, user2={}", message.getFromUserId(), message.getToUserId());
        }

        // 7. 发送WebSocket通知
        try
        {
            SysUser toUser = userService.selectUserById(userId);
            SysUser fromUser = userService.selectUserById(message.getFromUserId());

            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "friend_request_handled");
            notification.put("messageId", messageId);
            notification.put("result", accept ? "accepted" : "rejected");
            notification.put("friendUserId", userId);
            notification.put("friendUserInfo", buildUserInfo(toUser));
            notification.put("handleTime", LocalDateTime.now());

            webSocketServer.sendMessage(fromUser.getUserName(), JSON.toJSONString(notification));
            log.info("发送好友申请处理结果WebSocket通知成功: toUser={}", fromUser.getUserName());
        }
        catch (Exception e)
        {
            log.error("发送好友申请处理结果WebSocket通知失败", e);
        }

        return AjaxResult.success(accept ? "已同意好友申请" : "已拒绝好友申请");
    }

    /**
     * 获取收到的好友申请消息列表
     */
    @Override
    public List<ImMessage> getReceivedFriendRequests(Long userId)
    {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getToUserId, userId)
               .eq(ImMessage::getMessageType, "friend_request")
               .orderByDesc(ImMessage::getCreateTime);
        
        return imMessageMapper.selectList(wrapper);
    }

    /**
     * 获取发送的好友申请消息列表
     */
    @Override
    public List<ImMessage> getSentFriendRequests(Long userId)
    {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getFromUserId, userId)
               .eq(ImMessage::getMessageType, "friend_request")
               .orderByDesc(ImMessage::getCreateTime);
        
        return imMessageMapper.selectList(wrapper);
    }

    /**
     * 获取未处理的好友申请数量
     */
    @Override
    public int getUnhandledRequestCount(Long userId)
    {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getToUserId, userId)
               .eq(ImMessage::getMessageType, "friend_request")
               .eq(ImMessage::getStatus, "pending");
        
        return Math.toIntExact(imMessageMapper.selectCount(wrapper));
    }

    /**
     * 检查是否有待处理的好友申请
     */
    @Override
    public boolean hasPendingRequest(Long fromUserId, Long toUserId)
    {
        LambdaQueryWrapper<ImMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImMessage::getFromUserId, fromUserId)
               .eq(ImMessage::getToUserId, toUserId)
               .eq(ImMessage::getMessageType, "friend_request")
               .eq(ImMessage::getStatus, "pending");
        
        return imMessageMapper.selectCount(wrapper) > 0;
    }

    /**
     * 撤回好友申请
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult revokeFriendRequest(String messageId, Long userId)
    {
        log.info("撤回好友申请: messageId={}, userId={}", messageId, userId);

        // 1. 查询消息
        ImMessage message = imMessageMapper.selectById(messageId);
        if (message == null)
        {
            return AjaxResult.error("好友申请不存在");
        }

        // 2. 验证权限
        if (!message.getFromUserId().equals(userId))
        {
            return AjaxResult.error("无权撤回此好友申请");
        }

        // 3. 检查状态
        if (!"pending".equals(message.getStatus()))
        {
            return AjaxResult.error("该好友申请已处理，无法撤回");
        }

        // 4. 删除消息
        imMessageMapper.deleteById(messageId);

        log.info("撤回好友申请成功: messageId={}", messageId);

        return AjaxResult.success("好友申请已撤回");
    }

    /**
     * 构建用户信息Map
     */
    private Map<String, Object> buildUserInfo(SysUser user)
    {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getUserId());
        userInfo.put("userName", user.getUserName());
        userInfo.put("nickName", user.getNickName());
        userInfo.put("avatar", user.getAvatar());
        return userInfo;
    }
}
