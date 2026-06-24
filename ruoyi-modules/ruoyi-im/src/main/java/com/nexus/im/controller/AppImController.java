package com.nexus.im.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.json.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.im.domain.*;
import com.nexus.im.domain.bo.ImGroupCreateBo;
import com.nexus.im.domain.bo.ImGroupMemberAddBo;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.mapper.*;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.IImChatService;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.system.domain.SysUser;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * App移动端IM控制器
 * <p>
 * 为移动端提供即时通讯接口，封装好友关系、群组管理、消息操作等功能。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/im/app")
public class AppImController extends BaseController {

    // ============ 好友请求分类常量 ============
    private static final String CATEGORY_FRIEND_REQUEST = "im_friend_request";
    private static final String FRIEND_REQUEST_SENT = "sent";
    private static final String FRIEND_REQUEST_ACCEPTED = "accepted";
    private static final String FRIEND_REQUEST_REJECTED = "rejected";

    private final IImChatService imChatService;
    private final ISysUserNotificationService notificationService;
    private final SysUserMapper userMapper;
    private final ImMessageMapper messageMapper;
    private final ImGroupMapper groupMapper;
    private final ImGroupMemberMapper groupMemberMapper;
    private final SysUserNotificationMapper notificationMapper;
    private final ImFriendMapper friendMapper;
    private final ImRealtimePush imRealtimePush;

    // ==================== Session ====================

    /**
     * IM 登录（无状态，返回当前用户信息即可）
     */
    @PostMapping("/login")
    public R<Map<String, Object>> login() {
        Long userId = LoginHelper.getUserId();
        SysUserVo user = userMapper.selectVoById(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("userName", user != null ? user.getUserName() : "");
        data.put("nickName", user != null ? user.getNickName() : "");
        data.put("avatar", imChatService.resolveAvatarUrl(user != null ? user.getAvatar() : null));
        return R.ok(data);
    }

    /**
     * IM 登出（无状态）
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }

    // ==================== 用户 ====================

    /**
     * 获取当前登录用户的 IM 信息
     */
    @GetMapping("/user/info")
    public R<Map<String, Object>> currentUserInfo() {
        Long userId = LoginHelper.getUserId();
        SysUserVo user = userMapper.selectVoById(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("userName", user != null ? user.getUserName() : "");
        data.put("nickName", user != null ? user.getNickName() : "");
        data.put("avatar", imChatService.resolveAvatarUrl(user != null ? user.getAvatar() : null));
        return R.ok(data);
    }

    /**
     * 可聊天用户列表（同租户下所有用户）
     */
    @GetMapping("/user/list")
    public R<List<SysUserVo>> userList() {
        String tenantId = TenantHelper.getTenantId();
        List<SysUserVo> users;
        if (TenantHelper.isEnable()) {
            users = userMapper.selectVoList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId));
        } else {
            users = userMapper.selectVoList(new LambdaQueryWrapper<>());
        }
        return R.ok(users);
    }

    /**
     * 获取单个用户信息
     */
    @GetMapping("/user/{userId}")
    public R<SysUserVo> userInfo(@PathVariable Long userId) {
        SysUserVo user = userMapper.selectVoById(userId);
        return user != null ? R.ok(user) : R.fail("用户不存在");
    }

    // ==================== 好友列表 ====================

    /**
     * 好友列表（从好友关系表读取）
     */
    @GetMapping("/friend/list")
    public R<List<Map<String, Object>>> friendList() {
        Long me = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        List<ImFriend> relations;
        if (TenantHelper.isEnable()) {
            relations = friendMapper.selectList(new LambdaQueryWrapper<ImFriend>()
                .eq(ImFriend::getTenantId, tenantId)
                .eq(ImFriend::getUserId, me));
        } else {
            relations = friendMapper.selectList(new LambdaQueryWrapper<ImFriend>()
                .eq(ImFriend::getUserId, me));
        }

        List<Map<String, Object>> friends = new ArrayList<>();
        for (ImFriend rel : relations) {
            SysUserVo u = userMapper.selectVoById(rel.getFriendUserId());
            if (u != null) {
                Map<String, Object> f = new HashMap<>();
                f.put("userId", u.getUserId());
                f.put("userName", u.getUserName());
                f.put("nickName", u.getNickName());
                f.put("avatar", u.getAvatar());
                friends.add(f);
            }
        }
        return R.ok(friends);
    }

    // ==================== 好友请求 ====================

    /**
     * 发送好友请求
     */
    @PostMapping("/friend/request/send")
    public R<Void> sendFriendRequest(@RequestBody Map<String, String> body) {
        Long fromUserId = LoginHelper.getUserId();
        Long toUserId = Long.valueOf(body.get("toUserId"));
        String requestMessage = body.getOrDefault("message", "请求添加您为好友");

        if (fromUserId.equals(toUserId)) {
            return R.fail("不能添加自己为好友");
        }

        SysUserVo fromUser = userMapper.selectVoById(fromUserId);
        String senderName = StrUtil.blankToDefault(fromUser != null ? fromUser.getNickName() : "", fromUser != null ? fromUser.getUserName() : "用户");
        String title = senderName + " 请求添加您为好友";

        // 原有的通知（用于 toast 弹窗，保持兼容）
        notificationService.addAndPush(toUserId, CATEGORY_FRIEND_REQUEST, "friend_request",
            String.valueOf(fromUserId), title, requestMessage,
            "{\"status\":\"" + FRIEND_REQUEST_SENT + "\",\"fromUserId\":" + fromUserId + "}");

        // 插入 IM 消息（让好友请求出现在私聊对话中）
        Map<String, Object> fromInfo = new LinkedHashMap<>();
        fromInfo.put("nickName", fromUser != null ? fromUser.getNickName() : "");
        fromInfo.put("userName", fromUser != null ? fromUser.getUserName() : "");
        fromInfo.put("avatar", fromUser != null ? fromUser.getAvatar() : "");
        Map<String, Object> contentMap = new LinkedHashMap<>();
        contentMap.put("fromUserInfo", fromInfo);
        contentMap.put("requestMessage", requestMessage);
        contentMap.put("status", "pending");
        String contentJson = JsonUtils.toJsonString(contentMap);

        ImMessage msg = new ImMessage();
        msg.setFromUserId(fromUserId);
        msg.setToUserId(toUserId);
        msg.setContent(contentJson);
        msg.setMsgType("friend_request");
        msg.setIsRead("0");
        msg.setRevoked("0");
        messageMapper.insert(msg);

        // 推送给对方
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chatType", "PRIVATE");
        payload.put("messageId", msg.getMessageId());
        payload.put("fromUserId", fromUserId);
        payload.put("toUserId", toUserId);
        payload.put("content", contentJson);
        payload.put("msgType", "friend_request");
        payload.put("status", "pending");
        payload.put("createTime", msg.getCreateTime());
        if (fromUser != null) {
            payload.put("senderName", senderName);
            payload.put("senderAvatar", fromUser.getAvatar());
        }
        imRealtimePush.publishToUser(toUserId, ImEnvelopeHelper.imMessage(payload));

        return R.ok();
    }

    /**
     * 收到的好友请求
     */
    @GetMapping("/friend/request/received")
    public R<List<SysUserNotification>> receivedRequests() {
        Long me = LoginHelper.getUserId();
        List<SysUserNotification> list = notificationMapper.selectList(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, me)
            .eq(SysUserNotification::getCategory, CATEGORY_FRIEND_REQUEST)
            .orderByDesc(SysUserNotification::getCreateTime));
        return R.ok(list);
    }

    /**
     * 发出的好友请求
     */
    @GetMapping("/friend/request/sent")
    public R<List<SysUserNotification>> sentRequests() {
        Long me = LoginHelper.getUserId();
        List<SysUserNotification> all = notificationMapper.selectList(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getCategory, CATEGORY_FRIEND_REQUEST)
            .orderByDesc(SysUserNotification::getCreateTime));

        // 过滤出发送者是当前用户的请求（通过extJson中的fromUserId判断）
        List<SysUserNotification> sent = new ArrayList<>();
        for (SysUserNotification n : all) {
            if (n.getExtJson() != null && n.getExtJson().contains("\"fromUserId\":" + me)) {
                sent.add(n);
            }
        }
        return R.ok(sent);
    }

    /**
     * 处理好友请求（接受/拒绝）
     * 支持参数格式:
     * - 前端消息格式: { messageId, accept: true/false }
     * - 通知格式:     { id, action: "accept"/"reject" }
     */
    @PostMapping("/friend/request/handle")
    public R<Void> handleFriendRequest(@RequestBody Map<String, Object> body) {
        Long notificationId;
        Long messageId;
        boolean accept;
        Long fromUserId = null;
        Long toUserId = null;

        // 解析参数并获取申请者/接收者信息
        // 前端消息格式: { messageId: 123, accept: true, remark: '' }
        messageId = body.get("messageId") != null
            ? Long.valueOf(body.get("messageId").toString()) : null;

        if (body.containsKey("accept")) {
            accept = Boolean.TRUE.equals(body.get("accept"));
            notificationId = body.get("id") != null
                ? Long.valueOf(body.get("id").toString()) : null;
        } else {
            notificationId = Long.valueOf(body.get("id").toString());
            accept = "accept".equals(body.get("action"));
        }

        // 优先从 IM 消息获取好友关系
        if (messageId != null) {
            ImMessage msg = messageMapper.selectById(messageId);
            if (msg != null && "friend_request".equals(msg.getMsgType())) {
                fromUserId = msg.getFromUserId();
                toUserId = msg.getToUserId();
                // 更新消息内容中的状态
                updateFriendRequestMessageStatus(msg, accept);
            }
        }

        // 从通知获取（旧格式兼容）
        if (notificationId != null) {
            SysUserNotification notification = notificationMapper.selectById(notificationId);
            if (notification == null && messageId == null) {
                return R.fail("请求不存在");
            }
            if (notification != null) {
                String status = accept ? FRIEND_REQUEST_ACCEPTED : FRIEND_REQUEST_REJECTED;
                SysUserNotification update = new SysUserNotification();
                update.setNotificationId(notificationId);
                if (notification.getExtJson() != null) {
                    update.setExtJson(notification.getExtJson().replace("\"status\":\"" + FRIEND_REQUEST_SENT + "\"", "\"status\":\"" + status + "\""));
                } else {
                    update.setExtJson("{\"status\":\"" + status + "\"}");
                }
                update.setReadFlag("1");
                notificationMapper.updateById(update);
                if (fromUserId == null) {
                    fromUserId = notification.getRefId() != null ? Long.valueOf(notification.getRefId()) : null;
                    toUserId = notification.getUserId();
                }
            }
        }

        if (fromUserId == null || toUserId == null) {
            return R.fail("请求不存在");
        }

        // 接受时写入双向好友关系
        if (accept) {
            addFriendRelation(fromUserId, toUserId);
        }

        // 通知申请者处理结果
        String resultStatus = accept ? "accepted" : "rejected";
        String resultAction = accept ? "已同意" : "已拒绝";
        SysUserVo me = userMapper.selectVoById(LoginHelper.getUserId());
        String myName = me != null ? StrUtil.blankToDefault(me.getNickName(), me.getUserName()) : "对方";

        Map<String, Object> resultFromInfo = new LinkedHashMap<>();
        resultFromInfo.put("nickName", me != null ? me.getNickName() : "");
        resultFromInfo.put("userName", me != null ? me.getUserName() : "");
        resultFromInfo.put("avatar", me != null ? me.getAvatar() : "");
        Map<String, Object> resultContentMap = new LinkedHashMap<>();
        resultContentMap.put("fromUserInfo", resultFromInfo);
        resultContentMap.put("requestMessage", myName + resultAction + "了您的好友申请");
        resultContentMap.put("status", resultStatus);
        String notifyContent = JsonUtils.toJsonString(resultContentMap);

        ImMessage notifyMsg = new ImMessage();
        notifyMsg.setFromUserId(toUserId); // 被通知的消息显示为"对方"发的
        notifyMsg.setToUserId(fromUserId);
        notifyMsg.setContent(notifyContent);
        notifyMsg.setMsgType("friend_request");
        notifyMsg.setIsRead("0");
        notifyMsg.setRevoked("0");
        messageMapper.insert(notifyMsg);

        // 推送给申请者
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chatType", "PRIVATE");
        payload.put("messageId", notifyMsg.getMessageId());
        payload.put("fromUserId", toUserId);
        payload.put("toUserId", fromUserId);
        payload.put("content", notifyContent);
        payload.put("msgType", "friend_request");
        payload.put("status", resultStatus);
        payload.put("createTime", notifyMsg.getCreateTime());
        imRealtimePush.publishToUser(fromUserId, ImEnvelopeHelper.imMessage(payload));

        return R.ok();
    }

    /**
     * 更新好友申请消息内容中的状态
     */
    private void updateFriendRequestMessageStatus(ImMessage msg, boolean accept) {
        String oldContent = msg.getContent();
        if (oldContent == null) return;
        try {
            Map<String, Object> map = JsonUtils.parseMap(oldContent);
            if (map != null) {
                map.put("status", accept ? "accepted" : "rejected");
                String newContent = JsonUtils.toJsonString(map);
                if (!newContent.equals(oldContent)) {
                    ImMessage update = new ImMessage();
                    update.setMessageId(msg.getMessageId());
                    update.setContent(newContent);
                    messageMapper.updateById(update);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 写入双向好友关系
     */
    private void addFriendRelation(Long userIdA, Long userIdB) {
        // 检查是否已存在好友关系
        long countA = friendMapper.selectCount(new LambdaQueryWrapper<ImFriend>()
            .eq(ImFriend::getUserId, userIdA).eq(ImFriend::getFriendUserId, userIdB));
        if (countA == 0) {
            ImFriend f1 = new ImFriend();
            f1.setUserId(userIdA);
            f1.setFriendUserId(userIdB);
            friendMapper.insert(f1);
        }
        long countB = friendMapper.selectCount(new LambdaQueryWrapper<ImFriend>()
            .eq(ImFriend::getUserId, userIdB).eq(ImFriend::getFriendUserId, userIdA));
        if (countB == 0) {
            ImFriend f2 = new ImFriend();
            f2.setUserId(userIdB);
            f2.setFriendUserId(userIdA);
            friendMapper.insert(f2);
        }
    }

    /**
     * 未处理的好友请求数
     */
    @GetMapping("/friend/request/unhandled-count")
    public R<Long> unhandledCount() {
        Long me = LoginHelper.getUserId();
        long count = notificationMapper.selectCount(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, me)
            .eq(SysUserNotification::getCategory, CATEGORY_FRIEND_REQUEST)
            .eq(SysUserNotification::getReadFlag, "0"));
        return R.ok(count);
    }

    /**
     * 撤销好友请求
     */
    @DeleteMapping("/friend/request/{messageId}")
    public R<Void> revokeRequest(@PathVariable Long messageId) {
        notificationMapper.deleteById(messageId);
        return R.ok();
    }

    /**
     * 删除好友
     */
    @PostMapping("/friend/remove")
    public R<Void> removeFriend(@RequestBody Map<String, String> body) {
        Long me = LoginHelper.getUserId();
        Long friendUserId = Long.valueOf(body.get("friendUserId"));

        friendMapper.delete(new LambdaQueryWrapper<ImFriend>()
            .eq(ImFriend::getUserId, me)
            .eq(ImFriend::getFriendUserId, friendUserId));
        friendMapper.delete(new LambdaQueryWrapper<ImFriend>()
            .eq(ImFriend::getUserId, friendUserId)
            .eq(ImFriend::getFriendUserId, me));
        return R.ok();
    }

    /**
     * 检查是否已发送过请求
     */
    @GetMapping("/friend/request/check")
    public R<Boolean> checkPending(@RequestParam Long toUserId) {
        Long me = LoginHelper.getUserId();
        List<SysUserNotification> list = notificationMapper.selectList(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getCategory, CATEGORY_FRIEND_REQUEST)
            .eq(SysUserNotification::getRefId, String.valueOf(me)));
        boolean pending = list.stream().anyMatch(n ->
            n.getUserId().equals(toUserId) &&
            n.getExtJson() != null &&
            n.getExtJson().contains("\"status\":\"" + FRIEND_REQUEST_SENT + "\""));
        return R.ok(pending);
    }

    // ==================== 私聊 ====================

    /**
     * 发送私聊消息
     */
    @PostMapping("/message/send")
    public R<Long> sendPrivate(@RequestBody Map<String, String> body) {
        ImPrivateSendBo bo = new ImPrivateSendBo();
        bo.setToUserId(Long.valueOf(body.get("toUserId")));
        bo.setContent(body.get("content"));
        bo.setMsgType(body.getOrDefault("messageType", "text"));
        return imChatService.sendPrivate(bo);
    }

    /**
     * 发送群聊消息
     */
    @PostMapping("/message/send-group")
    public R<Long> sendGroup(@RequestBody Map<String, String> body) {
        ImGroupSendBo bo = new ImGroupSendBo();
        bo.setGroupId(Long.valueOf(body.get("groupId")));
        bo.setContent(body.get("content"));
        bo.setMsgType(body.getOrDefault("messageType", "text"));
        return imChatService.sendGroup(bo);
    }

    /**
     * 获取聊天记录
     */
    @GetMapping("/message/history")
    public TableDataInfo<Map<String, Object>> history(@RequestParam String targetId, PageQuery pageQuery) {
        if (targetId.startsWith("private_")) {
            return imChatService.privateHistoryWithSender(Long.valueOf(targetId.substring(8)), pageQuery);
        } else {
            return imChatService.groupHistoryWithSender(Long.valueOf(targetId), pageQuery);
        }
    }

    /**
     * 会话列表
     */
    @GetMapping("/conversation/list")
    public R<List<ImConversationVo>> conversations() {
        return R.ok(imChatService.listConversations());
    }

    /**
     * 标记已读（支持私聊和群聊）
     */
    @PostMapping("/message/read")
    public R<Void> markRead(@RequestBody Map<String, Object> body) {
        if (body.containsKey("toUserId") && body.get("toUserId") != null) {
            imChatService.markPrivateRead(Long.valueOf(body.get("toUserId").toString()));
        }
        if (body.containsKey("groupId") && body.get("groupId") != null) {
            imChatService.markGroupRead(Long.valueOf(body.get("groupId").toString()));
        }
        return R.ok();
    }

    /**
     * 未读消息数
     */
    @GetMapping("/message/unread-count")
    public R<Long> unreadCount() {
        Long me = LoginHelper.getUserId();
        long count = messageMapper.selectCount(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId));
        return R.ok(count);
    }

    /**
     * 未读消息列表
     */
    @GetMapping("/message/unread")
    public R<List<ImMessage>> unreadMessages() {
        Long me = LoginHelper.getUserId();
        List<ImMessage> list = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId)
            .orderByDesc(ImMessage::getCreateTime));
        return R.ok(list);
    }

    /**
     * 撤回消息
     */
    @PostMapping("/message/revoke/{messageId}")
    public R<Void> revokeMessage(@PathVariable Long messageId) {
        ImMessage msg = messageMapper.selectById(messageId);
        if (msg == null) {
            return R.fail("消息不存在");
        }
        if (!msg.getFromUserId().equals(LoginHelper.getUserId())) {
            return R.fail("只能撤回自己发送的消息");
        }
        ImMessage update = new ImMessage();
        update.setMessageId(messageId);
        update.setRevoked("1");
        messageMapper.updateById(update);
        return R.ok();
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/message/{messageId}")
    public R<Void> deleteMessage(@PathVariable Long messageId) {
        messageMapper.deleteById(messageId);
        return R.ok();
    }

    /**
     * 转发消息
     */
    @PostMapping("/message/forward")
    public R<Long> forwardMessage(@RequestBody Map<String, Object> body) {
        Long messageId = Long.valueOf(body.get("messageId").toString());
        String targetType = (String) body.get("targetType");
        Long targetId = Long.valueOf(body.get("targetId").toString());

        ImMessage original = messageMapper.selectById(messageId);
        if (original == null) {
            return R.fail("消息不存在");
        }
        if ("1".equals(original.getRevoked())) {
            return R.fail("已被撤回的消息无法转发");
        }

        if ("private".equalsIgnoreCase(targetType)) {
            ImPrivateSendBo bo = new ImPrivateSendBo();
            bo.setToUserId(targetId);
            bo.setContent(original.getContent());
            bo.setMsgType(original.getMsgType());
            bo.setForwardFrom(original.getMessageId());
            return imChatService.sendPrivate(bo);
        } else {
            ImGroupSendBo bo = new ImGroupSendBo();
            bo.setGroupId(targetId);
            bo.setContent(original.getContent());
            bo.setMsgType(original.getMsgType());
            bo.setForwardFrom(original.getMessageId());
            return imChatService.sendGroup(bo);
        }
    }

    // ==================== 群组 ====================

    /**
     * 创建群聊
     */
    @PostMapping("/group/create")
    public R<Long> createGroup(@RequestBody Map<String, Object> body) {
        ImGroupCreateBo bo = new ImGroupCreateBo();
        bo.setGroupName((String) body.get("groupName"));
        bo.setAvatar((String) body.get("avatar"));
        @SuppressWarnings("unchecked")
        List<Number> memberIds = (List<Number>) body.get("memberUserIds");
        if (memberIds != null) {
            bo.setMemberUserIds(memberIds.stream().map(Number::longValue).collect(Collectors.toList()));
        }
        return imChatService.createGroup(bo);
    }

    /**
     * 我的群列表
     */
    @GetMapping("/group/list")
    public R<List<Map<String, Object>>> groupList() {
        Long me = LoginHelper.getUserId();
        List<ImGroupMember> memberships = groupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getUserId, me));

        List<Map<String, Object>> groups = new ArrayList<>();
        for (ImGroupMember gm : memberships) {
            ImGroup g = groupMapper.selectById(gm.getGroupId());
            if (g != null && "0".equals(g.getStatus())) {
                Map<String, Object> map = new HashMap<>();
                map.put("groupId", g.getGroupId());
                map.put("groupName", g.getGroupName());
                map.put("avatar", g.getAvatar());
                map.put("ownerUserId", g.getOwnerUserId());
                groups.add(map);
            }
        }
        return R.ok(groups);
    }

    /**
     * 群详情
     */
    @GetMapping("/group/info")
    public R<Map<String, Object>> groupInfo(@RequestParam Long groupId) {
        ImGroup g = groupMapper.selectById(groupId);
        if (g == null) {
            return R.fail("群组不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("groupId", g.getGroupId());
        map.put("groupName", g.getGroupName());
        map.put("avatar", g.getAvatar());
        map.put("ownerUserId", g.getOwnerUserId());
        map.put("status", g.getStatus());
        return R.ok(map);
    }

    /**
     * 群成员
     */
    @GetMapping("/group/members")
    public R<List<Map<String, Object>>> groupMembers(@RequestParam Long groupId) {
        List<ImGroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId));

        List<Map<String, Object>> list = new ArrayList<>();
        for (ImGroupMember m : members) {
            SysUserVo u = userMapper.selectVoById(m.getUserId());
            Map<String, Object> map = new HashMap<>();
            map.put("userId", m.getUserId());
            map.put("userName", u != null ? u.getUserName() : "");
            map.put("nickName", u != null ? u.getNickName() : "");
            map.put("avatar", u != null ? u.getAvatar() : "");
            map.put("joinTime", m.getJoinTime());
            list.add(map);
        }
        return R.ok(list);
    }

    /**
     * 添加群成员
     */
    @PostMapping("/group/add-members")
    public R<Void> addMembers(@RequestBody Map<String, Object> body) {
        Long groupId = Long.valueOf(body.get("groupId").toString());
        ImGroupMemberAddBo bo = new ImGroupMemberAddBo();
        @SuppressWarnings("unchecked")
        List<Number> memberIds = (List<Number>) body.get("memberUserIds");
        if (memberIds != null) {
            bo.setMemberUserIds(memberIds.stream().map(Number::longValue).collect(Collectors.toList()));
        }
        return imChatService.addGroupMembers(groupId, bo);
    }

    /**
     * 移除群成员
     */
    @PostMapping("/group/remove-member")
    public R<Void> removeMember(@RequestBody Map<String, String> body) {
        Long groupId = Long.valueOf(body.get("groupId"));
        Long userId = Long.valueOf(body.get("userId"));
        Long me = LoginHelper.getUserId();

        ImGroup g = groupMapper.selectById(groupId);
        if (g == null || !g.getOwnerUserId().equals(me)) {
            return R.fail("仅群主可移除成员");
        }
        if (g.getOwnerUserId().equals(userId)) {
            return R.fail("不能移除群主");
        }

        groupMemberMapper.delete(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .eq(ImGroupMember::getUserId, userId));
        return R.ok();
    }

    /**
     * 更新群信息
     */
    @PostMapping("/group/update")
    public R<Void> updateGroup(@RequestBody Map<String, String> body) {
        Long groupId = Long.valueOf(body.get("groupId"));
        Long me = LoginHelper.getUserId();

        ImGroup g = groupMapper.selectById(groupId);
        if (g == null || !g.getOwnerUserId().equals(me)) {
            return R.fail("仅群主可修改群信息");
        }

        ImGroup update = new ImGroup();
        update.setGroupId(groupId);
        if (body.containsKey("groupName")) update.setGroupName(body.get("groupName"));
        if (body.containsKey("avatar")) update.setAvatar(body.get("avatar"));
        groupMapper.updateById(update);
        return R.ok();
    }

    /**
     * 退出群聊
     */
    @PostMapping("/group/quit")
    public R<Void> quitGroup(@RequestBody Map<String, String> body) {
        Long groupId = Long.valueOf(body.get("groupId"));
        Long me = LoginHelper.getUserId();

        groupMemberMapper.delete(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .eq(ImGroupMember::getUserId, me));
        return R.ok();
    }

    /**
     * 解散群聊
     */
    @PostMapping("/group/disband")
    public R<Void> disbandGroup(@RequestBody Map<String, String> body) {
        Long groupId = Long.valueOf(body.get("groupId"));
        Long me = LoginHelper.getUserId();

        ImGroup g = groupMapper.selectById(groupId);
        if (g == null || !g.getOwnerUserId().equals(me)) {
            return R.fail("仅群主可解散群聊");
        }

        ImGroup update = new ImGroup();
        update.setGroupId(groupId);
        update.setStatus("1");
        groupMapper.updateById(update);
        return R.ok();
    }
}
