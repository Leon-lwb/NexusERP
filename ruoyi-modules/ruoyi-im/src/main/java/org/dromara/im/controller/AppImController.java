package org.dromara.im.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.im.domain.*;
import org.dromara.im.domain.bo.ImGroupCreateBo;
import org.dromara.im.domain.bo.ImGroupMemberAddBo;
import org.dromara.im.domain.bo.ImGroupSendBo;
import org.dromara.im.domain.bo.ImPrivateSendBo;
import org.dromara.im.domain.vo.ImConversationVo;
import org.dromara.im.mapper.*;
import org.dromara.im.service.IImChatService;
import org.dromara.im.service.ISysUserNotificationService;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
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
        data.put("avatar", user != null ? user.getAvatar() : "");
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
     * 好友列表：有私聊记录的用户
     */
    @GetMapping("/friend/list")
    public R<List<Map<String, Object>>> friendList() {
        Long me = LoginHelper.getUserId();
        List<ImMessage> msgs = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
            .isNull(ImMessage::getGroupId)
            .and(w -> w.eq(ImMessage::getFromUserId, me).or().eq(ImMessage::getToUserId, me))
            .orderByDesc(ImMessage::getCreateTime));

        Set<Long> peerIds = new LinkedHashSet<>();
        for (ImMessage m : msgs) {
            Long peer = m.getFromUserId().equals(me) ? m.getToUserId() : m.getFromUserId();
            if (peer != null) peerIds.add(peer);
        }

        List<Map<String, Object>> friends = new ArrayList<>();
        for (Long peerId : peerIds) {
            SysUserVo u = userMapper.selectVoById(peerId);
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
        String message = body.getOrDefault("message", "请求添加您为好友");

        if (fromUserId.equals(toUserId)) {
            return R.fail("不能添加自己为好友");
        }

        SysUserVo fromUser = userMapper.selectVoById(fromUserId);
        String title = StrUtil.blankToDefault(fromUser != null ? fromUser.getNickName() : "", fromUser != null ? fromUser.getUserName() : "用户") + " 请求添加您为好友";

        notificationService.addAndPush(toUserId, CATEGORY_FRIEND_REQUEST, "friend_request",
            String.valueOf(fromUserId), title, message,
            "{\"status\":\"" + FRIEND_REQUEST_SENT + "\",\"fromUserId\":" + fromUserId + "}");

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
     */
    @PostMapping("/friend/request/handle")
    public R<Void> handleFriendRequest(@RequestBody Map<String, String> body) {
        Long notificationId = Long.valueOf(body.get("id"));
        boolean accept = "accept".equals(body.get("action"));

        SysUserNotification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            return R.fail("请求不存在");
        }

        String status = accept ? FRIEND_REQUEST_ACCEPTED : FRIEND_REQUEST_REJECTED;

        // 更新通知的extJson状态
        SysUserNotification update = new SysUserNotification();
        update.setNotificationId(notificationId);
        if (notification.getExtJson() != null) {
            update.setExtJson(notification.getExtJson().replace("\"status\":\"" + FRIEND_REQUEST_SENT + "\"", "\"status\":\"" + status + "\""));
        } else {
            update.setExtJson("{\"status\":\"" + status + "\"}");
        }
        notification.setReadFlag("1");
        update.setReadFlag("1");
        notificationMapper.updateById(update);

        return R.ok();
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
    public TableDataInfo<ImMessage> history(@RequestParam String targetId, PageQuery pageQuery) {
        if (targetId.startsWith("private_")) {
            return imChatService.privateHistory(Long.valueOf(targetId.substring(8)), pageQuery);
        } else {
            return imChatService.groupHistory(Long.valueOf(targetId), pageQuery);
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
     * 标记已读
     */
    @PostMapping("/message/read")
    public R<Void> markRead(@RequestBody Map<String, Object> body) {
        if (body.containsKey("toUserId") && body.get("toUserId") != null) {
            imChatService.markPrivateRead(Long.valueOf(body.get("toUserId").toString()));
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
