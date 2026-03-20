package com.nexus.im.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.im.domain.ImMessage;

import java.util.List;

/**
 * IM好友申请Service接口
 * 
 * @author nexus
 * @date 2024-01-01
 */
public interface ImFriendRequestService extends IService<ImMessage>
{
    /**
     * 发送好友申请
     * 
     * @param fromUserId 申请人ID
     * @param toUserId 被申请人ID
     * @param requestMessage 申请消息
     * @param remark 好友备注
     * @return 结果
     */
    AjaxResult sendFriendRequest(Long fromUserId, Long toUserId, String requestMessage, String remark);

    /**
     * 处理好友申请
     * 
     * @param messageId 消息ID
     * @param userId 当前用户ID
     * @param accept 是否同意
     * @param remark 好友备注
     * @return 结果
     */
    AjaxResult handleFriendRequest(String messageId, Long userId, boolean accept, String remark);

    /**
     * 获取收到的好友申请消息列表
     * 
     * @param userId 用户ID
     * @return 消息列表
     */
    List<ImMessage> getReceivedFriendRequests(Long userId);

    /**
     * 获取发送的好友申请消息列表
     * 
     * @param userId 用户ID
     * @return 消息列表
     */
    List<ImMessage> getSentFriendRequests(Long userId);

    /**
     * 获取未处理的好友申请数量
     * 
     * @param userId 用户ID
     * @return 数量
     */
    int getUnhandledRequestCount(Long userId);

    /**
     * 检查是否有待处理的好友申请
     * 
     * @param fromUserId 申请人ID
     * @param toUserId 被申请人ID
     * @return true-有待处理申请 false-无
     */
    boolean hasPendingRequest(Long fromUserId, Long toUserId);

    /**
     * 撤回好友申请
     * 
     * @param messageId 消息ID
     * @param userId 当前用户ID
     * @return 结果
     */
    AjaxResult revokeFriendRequest(String messageId, Long userId);
}
