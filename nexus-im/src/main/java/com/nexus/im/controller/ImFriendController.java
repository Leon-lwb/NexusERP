package com.nexus.im.controller;

import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.utils.SecurityUtils;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.dto.ImFriendRequestDTO;
import com.nexus.im.dto.HandleFriendRequestDTO;
import com.nexus.im.service.ImFriendRequestService;
import com.nexus.im.service.ImFriendService;
import com.nexus.im.service.impl.ImFriendServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "IM好友管理", description = "IM好友管理接口")
@RestController
@RequestMapping("/im/friend")
public class ImFriendController extends BaseController
{
    @Autowired
    private ImFriendRequestService friendRequestService;
    
    @Autowired
    private ImFriendService imFriendService;

    /**
     * 获取好友列表
     */
    @Operation(summary = "获取好友列表", description = "获取当前用户的好友列表")
    @GetMapping("/list")
    public AjaxResult getFriendList()
    {
        Long userId = SecurityUtils.getUserId();
        log.info("获取好友列表: userId={}", userId);
        List<java.util.Map<String, Object>> list = ((ImFriendServiceImpl) imFriendService).getFriendListWithInfo(userId);
        return AjaxResult.success(list);
    }

    /**
     * 发送好友申请
     */
    @Operation(summary = "发送好友申请", description = "发送好友申请消息")
    @Log(title = "IM好友申请", businessType = BusinessType.INSERT)
    @PostMapping("/request/send")
    public AjaxResult sendFriendRequest(@RequestBody ImFriendRequestDTO request)
    {
        log.info("发送好友申请: toUserId={}, message={}", request.getToUserId(), request.getRequestMessage());
        
        Long fromUserId = SecurityUtils.getUserId();
        return friendRequestService.sendFriendRequest(
            fromUserId, 
            request.getToUserId(), 
            request.getRequestMessage(),
            request.getRemark()
        );
    }

    /**
     * 处理好友申请
     */
    @Operation(summary = "处理好友申请", description = "同意或拒绝好友申请")
    @Log(title = "IM好友申请", businessType = BusinessType.UPDATE)
    @PostMapping("/request/handle")
    public AjaxResult handleFriendRequest(@RequestBody HandleFriendRequestDTO request)
    {
        log.info("处理好友申请: messageId={}, accept={}", request.getMessageId(), request.isAccept());
        
        Long userId = SecurityUtils.getUserId();
        return friendRequestService.handleFriendRequest(
            request.getMessageId(), 
            userId, 
            request.isAccept(), 
            request.getRemark()
        );
    }

    /**
     * 获取收到的好友申请列表
     */
    @Operation(summary = "获取收到的好友申请", description = "获取收到的好友申请消息列表")
    @GetMapping("/request/received")
    public TableDataInfo getReceivedRequests()
    {
        Long userId = SecurityUtils.getUserId();
        List<ImMessage> list = friendRequestService.getReceivedFriendRequests(userId);
        return getDataTable(list);
    }

    /**
     * 获取发送的好友申请列表
     */
    @Operation(summary = "获取发送的好友申请", description = "获取发送的好友申请消息列表")
    @GetMapping("/request/sent")
    public TableDataInfo getSentRequests()
    {
        Long userId = SecurityUtils.getUserId();
        List<ImMessage> list = friendRequestService.getSentFriendRequests(userId);
        return getDataTable(list);
    }

    /**
     * 获取未处理的好友申请数量
     */
    @Operation(summary = "获取未处理申请数量", description = "获取未处理的好友申请数量")
    @GetMapping("/request/unhandled-count")
    public AjaxResult getUnhandledCount()
    {
        Long userId = SecurityUtils.getUserId();
        int count = friendRequestService.getUnhandledRequestCount(userId);
        return AjaxResult.success(count);
    }

    /**
     * 撤回好友申请
     */
    @Operation(summary = "撤回好友申请", description = "撤回未处理的好友申请")
    @Log(title = "IM好友申请", businessType = BusinessType.DELETE)
    @DeleteMapping("/request/{messageId}")
    public AjaxResult revokeFriendRequest(@PathVariable String messageId)
    {
        log.info("撤回好友申请: messageId={}", messageId);
        
        Long userId = SecurityUtils.getUserId();
        return friendRequestService.revokeFriendRequest(messageId, userId);
    }

    /**
     * 检查是否有待处理的好友申请
     */
    @Operation(summary = "检查待处理申请", description = "检查是否已发送过好友申请")
    @GetMapping("/request/check")
    public AjaxResult checkPendingRequest(@RequestParam Long toUserId)
    {
        Long fromUserId = SecurityUtils.getUserId();
        boolean hasPending = friendRequestService.hasPendingRequest(fromUserId, toUserId);
        return AjaxResult.success("查询成功", hasPending);
    }

    /**
     * 删除好友
     */
    @Operation(summary = "删除好友", description = "删除指定好友")
    @Log(title = "IM好友管理", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    public AjaxResult removeFriend(@RequestBody java.util.Map<String, Object> params)
    {
        Long userId = SecurityUtils.getUserId();
        Long friendUserId = Long.valueOf(params.get("friendUserId").toString());
        log.info("删除好友: userId={}, friendUserId={}", userId, friendUserId);
        
        boolean success = imFriendService.removeFriend(userId, friendUserId);
        if (success)
        {
            return AjaxResult.success("删除好友成功");
        }
        return AjaxResult.error("删除好友失败");
    }
}
