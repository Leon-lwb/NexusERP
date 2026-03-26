package com.nexus.im.controller;

import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.utils.SecurityUtils;
import com.nexus.im.config.ImProperties;
import com.nexus.im.service.ImMessageService;
import com.nexus.im.service.ImGroupService;
import com.nexus.im.service.ImService;
import com.nexus.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM控制器
 *
 * @author nexus
 */
@Slf4j
@RestController
@RequestMapping("/im")
@RequiredArgsConstructor
@Tag(name = "即时通讯", description = "IM相关接口")
public class ImController {

    private final ImService imService;
    private final ISysUserService userService;
    private final ImMessageService imMessageService;
    private final ImGroupService imGroupService;
    private final ImProperties imProperties;

    @Operation(summary = "用户登录IM", description = "用户登录到IM系统")
    @PostMapping("/login")
    public AjaxResult loginToIm() {
        try {
            Long userId = SecurityUtils.getUserId();
            log.info("用户 {} 尝试登录IM系统", userId);
            
            SysUser user = userService.selectUserById(userId);
            if (user == null) {
                log.warn("用户 {} 不存在", userId);
                return AjaxResult.error("用户不存在");
            }
            
            AjaxResult result = imService.loginToIm(user);
            log.info("用户 {} IM登录结果: {}", userId, result.isSuccess() ? "成功" : "失败");
            
            return result;
        } catch (Exception e) {
            log.error("用户登录IM失败: {}", e.getMessage(), e);
            return AjaxResult.error("登录IM失败: " + e.getMessage());
        }
    }

    @Operation(summary = "用户登出IM", description = "用户从IM系统登出")
    @PostMapping("/logout")
    public AjaxResult logoutFromIm() {
        try {
            Long userId = SecurityUtils.getUserId();
            return imService.logoutFromIm(userId);
        } catch (Exception e) {
            log.error("用户登出IM失败: {}", e.getMessage(), e);
            return AjaxResult.error("登出IM失败: " + e.getMessage());
        }
    }

    @Operation(summary = "同步用户到IM", description = "将系统用户同步到IM系统")
    @PostMapping("/sync-user")
    public AjaxResult syncUserToIm(@RequestParam Long userId) {
        try {
            SysUser user = userService.selectUserById(userId);
            if (user == null) {
                return AjaxResult.error("用户不存在");
            }
            return imService.syncUserToIm(user);
        } catch (Exception e) {
            log.error("同步用户到IM失败: {}", e.getMessage(), e);
            return AjaxResult.error("同步用户失败: " + e.getMessage());
        }
    }

    @Operation(summary = "批量同步用户到IM", description = "批量将系统用户同步到IM系统")
    @PostMapping("/batch-sync-users")
    public AjaxResult batchSyncUsersToIm(@RequestBody List<Long> userIds) {
        try {
            List<SysUser> users = new ArrayList<>();
            for (Long userId : userIds) {
                SysUser user = userService.selectUserById(userId);
                if (user != null) {
                    users.add(user);
                }
            }
            return imService.batchSyncUsersToIm(users);
        } catch (Exception e) {
            log.error("批量同步用户到IM失败: {}", e.getMessage(), e);
            return AjaxResult.error("批量同步用户失败: " + e.getMessage());
        }
    }

    @Operation(summary = "发送消息", description = "发送私聊消息")
    @PostMapping("/message/send")
    public AjaxResult sendMessage(@RequestBody Map<String, Object> messageData) {
        try {
            Long fromUserId = SecurityUtils.getUserId();
            
            if (!messageData.containsKey("toUserId") || messageData.get("toUserId") == null) {
                return AjaxResult.error("接收者ID不能为空");
            }
            if (!messageData.containsKey("content") || messageData.get("content") == null) {
                return AjaxResult.error("消息内容不能为空");
            }
            
            Long toUserId = Long.valueOf(messageData.get("toUserId").toString());
            String content = messageData.get("content").toString();
            String messageType = messageData.getOrDefault("messageType", "text").toString();
            
            AjaxResult result = imService.sendMessage(fromUserId, toUserId, content, messageType);
            if (result.isSuccess()) {
                Map<String, Object> data = new HashMap<>();
                String messageId = imService.getMessageId(fromUserId, toUserId, content, messageType);
                if (messageId != null) {
                    data.put("messageId", messageId);
                } else {
                    data.put("messageId", "temp_" + System.currentTimeMillis());
                }
                return AjaxResult.success(data);
            }
            return AjaxResult.error(result.get("msg").toString());
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage(), e);
            return AjaxResult.error("发送消息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "发送群组消息", description = "发送群组消息")
    @PostMapping("/message/send-group")
    public AjaxResult sendGroupMessage(@RequestBody Map<String, Object> messageData) {
        try {
            Long fromUserId = SecurityUtils.getUserId();
            
            if (!messageData.containsKey("groupId") || messageData.get("groupId") == null) {
                return AjaxResult.error("群组ID不能为空");
            }
            if (!messageData.containsKey("content") || messageData.get("content") == null) {
                return AjaxResult.error("消息内容不能为空");
            }
            
            String groupId = messageData.get("groupId").toString();
            String content = messageData.get("content").toString();
            String messageType = messageData.getOrDefault("messageType", "text").toString();
            
            return imService.sendGroupMessage(fromUserId, groupId, content, messageType);
        } catch (Exception e) {
            log.error("发送群组消息失败: {}", e.getMessage(), e);
            return AjaxResult.error("发送群组消息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取用户IM信息", description = "获取用户IM相关信息")
    @GetMapping("/user/info")
    public AjaxResult getUserImInfo() {
        try {
            Long userId = SecurityUtils.getUserId();
            return imService.getUserImInfo(userId);
        } catch (Exception e) {
            log.error("获取用户IM信息失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取用户IM信息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取IM用户列表", description = "获取所有用户的IM信息列表")
    @GetMapping("/user/list")
    public AjaxResult getImUserList() {
        try {
            return imService.getImUserList();
        } catch (Exception e) {
            log.error("获取IM用户列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取IM用户列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取指定用户信息", description = "根据用户ID获取用户基本信息")
    @GetMapping("/user/{userId}")
    public AjaxResult getUserById(@PathVariable Long userId) {
        try {
            return imService.getUserById(userId);
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取用户信息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取未读消息数", description = "获取用户的未读消息总数")
    @GetMapping("/message/unread-count")
    public AjaxResult getUnreadCount() {
        try {
            Long userId = SecurityUtils.getUserId();
            return imService.getUnreadCount(userId);
        } catch (Exception e) {
            log.error("获取未读消息数失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取未读消息数失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取未读消息列表", description = "获取用户未读消息列表")
    @GetMapping("/message/unread")
    public AjaxResult getUnreadMessages() {
        try {
            Long userId = SecurityUtils.getUserId();
            return imService.getUnreadMessages(userId);
        } catch (Exception e) {
            log.error("获取未读消息列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取未读消息列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取消息历史", description = "获取与指定用户或群组的消息历史")
    @GetMapping("/message/history")
    public AjaxResult getMessageHistory(
            @RequestParam String targetId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Long userId = SecurityUtils.getUserId();
            return imService.getMessageHistory(userId, targetId, pageNum, pageSize);
        } catch (Exception e) {
            log.error("获取消息历史失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取消息历史失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取会话列表", description = "获取用户的会话列表")
    @GetMapping("/conversation/list")
    public AjaxResult getConversationList() {
        try {
            Long userId = SecurityUtils.getUserId();
            return imService.getConversationList(userId);
        } catch (Exception e) {
            log.error("获取会话列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取会话列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "标记消息已读", description = "标记消息为已读")
    @PostMapping("/message/read")
    public AjaxResult markMessagesAsRead(@RequestBody Map<String, Object> params) {
        try {
            Long userId = SecurityUtils.getUserId();
            Long toUserId = params.get("toUserId") != null ? Long.valueOf(params.get("toUserId").toString()) : null;
            String groupId = params.get("groupId") != null ? params.get("groupId").toString() : null;
            return imService.markMessagesAsRead(userId, toUserId, groupId);
        } catch (Exception e) {
            log.error("标记消息已读失败: {}", e.getMessage(), e);
            return AjaxResult.error("标记消息已读失败: " + e.getMessage());
        }
    }

    @Operation(summary = "创建群组", description = "创建新的群组")
    @PostMapping("/group/create")
    public AjaxResult createGroup(@RequestBody Map<String, Object> params) {
        try {
            Long ownerId = SecurityUtils.getUserId();
            String groupName = params.get("groupName").toString();
            String description = params.getOrDefault("description", "").toString();
            String avatar = params.getOrDefault("avatar", "").toString();
            
            List<Long> memberIds = null;
            Object memberIdsObj = params.get("memberIds");
            if (memberIdsObj instanceof List) {
                memberIds = ((List<?>) memberIdsObj).stream()
                    .map(obj -> {
                        if (obj instanceof Number) {
                            return ((Number) obj).longValue();
                        }
                        return Long.parseLong(obj.toString());
                    })
                    .collect(java.util.stream.Collectors.toList());
            }
            
            return imService.createGroup(ownerId, groupName, description, avatar, memberIds);
        } catch (Exception e) {
            log.error("创建群组失败: {}", e.getMessage(), e);
            return AjaxResult.error("创建群组失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取用户群组列表", description = "获取用户参与的群组列表")
    @GetMapping("/group/list")
    public AjaxResult getUserGroups() {
        try {
            Long userId = SecurityUtils.getUserId();
            return imService.getUserGroups(userId);
        } catch (Exception e) {
            log.error("获取用户群组列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取用户群组列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取群组详情", description = "获取群组详细信息")
    @GetMapping("/group/info")
    public AjaxResult getGroupInfo(@RequestParam String groupId) {
        try {
            return imService.getGroupInfo(groupId);
        } catch (Exception e) {
            log.error("获取群组详情失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取群组详情失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取群组成员", description = "获取群组成员列表")
    @GetMapping("/group/members")
    public AjaxResult getGroupMembers(@RequestParam String groupId) {
        try {
            return imService.getGroupMembers(groupId);
        } catch (Exception e) {
            log.error("获取群组成员失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取群组成员失败: " + e.getMessage());
        }
    }

    @Operation(summary = "添加群组成员", description = "添加群组成员")
    @PostMapping("/group/add-members")
    public AjaxResult addGroupMembers(@RequestBody Map<String, Object> params) {
        try {
            Long operatorId = SecurityUtils.getUserId();
            String groupId = params.get("groupId").toString();
            @SuppressWarnings("unchecked")
            List<Long> memberIds = (List<Long>) params.get("memberIds");
            
            return imService.addGroupMembers(groupId, operatorId, memberIds);
        } catch (Exception e) {
            log.error("添加群组成员失败: {}", e.getMessage(), e);
            return AjaxResult.error("添加群组成员失败: " + e.getMessage());
        }
    }

    @Operation(summary = "移除群组成员", description = "移除群组成员")
    @PostMapping("/group/remove-member")
    public AjaxResult removeGroupMember(@RequestBody Map<String, Object> params) {
        try {
            Long operatorId = SecurityUtils.getUserId();
            String groupId = params.get("groupId").toString();
            Long userId = Long.valueOf(params.get("userId").toString());
            
            return imService.removeGroupMember(groupId, operatorId, userId);
        } catch (Exception e) {
            log.error("移除群组成员失败: {}", e.getMessage(), e);
            return AjaxResult.error("移除群组成员失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新群组信息", description = "更新群组信息")
    @PostMapping("/group/update")
    public AjaxResult updateGroupInfo(@RequestBody Map<String, Object> params) {
        try {
            Long operatorId = SecurityUtils.getUserId();
            String groupId = params.get("groupId").toString();
            String groupName = params.getOrDefault("groupName", "").toString();
            String description = params.getOrDefault("description", "").toString();
            String avatar = params.getOrDefault("avatar", "").toString();
            
            return imService.updateGroupInfo(groupId, operatorId, groupName, description, avatar);
        } catch (Exception e) {
            log.error("更新群组信息失败: {}", e.getMessage(), e);
            return AjaxResult.error("更新群组信息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "退出群组", description = "退出群组")
    @PostMapping("/group/quit")
    public AjaxResult quitGroup(@RequestBody Map<String, Object> params) {
        try {
            Long userId = SecurityUtils.getUserId();
            String groupId = params.get("groupId").toString();
            
            return imService.quitGroup(groupId, userId);
        } catch (Exception e) {
            log.error("退出群组失败: {}", e.getMessage(), e);
            return AjaxResult.error("退出群组失败: " + e.getMessage());
        }
    }

    @Operation(summary = "解散群组", description = "解散群组")
    @PostMapping("/group/disband")
    public AjaxResult disbandGroup(@RequestBody Map<String, Object> params) {
        try {
            Long operatorId = SecurityUtils.getUserId();
            String groupId = params.get("groupId").toString();
            
            return imService.disbandGroup(groupId, operatorId);
        } catch (Exception e) {
            log.error("解散群组失败: {}", e.getMessage(), e);
            return AjaxResult.error("解散群组失败: " + e.getMessage());
        }
    }

    @Operation(summary = "检查IM表是否存在", description = "检查IM相关表是否存在")
    @GetMapping("/check-tables")
    public AjaxResult checkImTables() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            try {
                imMessageService.count();
                result.put("im_message", "存在");
            } catch (Exception e) {
                result.put("im_message", "不存在或错误: " + e.getMessage());
            }
            
            try {
                imGroupService.count();
                result.put("im_group", "存在");
            } catch (Exception e) {
                result.put("im_group", "不存在或错误: " + e.getMessage());
            }
            
            return AjaxResult.success(result);
        } catch (Exception e) {
            log.error("检查IM表失败: {}", e.getMessage(), e);
            return AjaxResult.error("检查IM表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "测试IM模块", description = "测试IM模块是否正常启动")
    @GetMapping("/test")
    public AjaxResult testIm() {
        log.info("=== IM模块测试API被调用 ===");
        log.info("当前时间: {}", System.currentTimeMillis());
        log.info("IM配置状态: enabled={}", imProperties.isEnabled());
        return AjaxResult.success("IM模块启动正常，时间: " + System.currentTimeMillis());
    }
}
