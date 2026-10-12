package com.nexus.im.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
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
import com.nexus.im.domain.bo.AppImGroupMemberAddBo;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.domain.bo.ImTypingBo;
import com.nexus.im.domain.vo.ImContactDeptVo;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.domain.vo.ImFriendVo;
import com.nexus.im.domain.vo.ImSyncResultVo;
import com.nexus.im.domain.vo.ImSearchResultVo;
import com.nexus.im.mapper.*;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.IImChatService;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.im.service.impl.support.ImContactSupport;
import com.nexus.im.service.impl.support.ImConversationService;
import com.nexus.im.service.impl.support.ImOnlineCache;
import com.nexus.im.service.impl.support.ImMessageVisibility;
import com.nexus.im.constants.ImPayloadConstants;
import com.nexus.system.domain.SysUser;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
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
    /** 好友请求通知分类标识，用于通知表 category 字段区分好友申请类消息。 */
    private static final String CATEGORY_FRIEND_REQUEST = "im_friend_request";
    /** 好友请求状态：已发出，等待对方处理。 */
    private static final String FRIEND_REQUEST_SENT = "sent";
    /** 好友请求状态：已被对方接受。 */
    private static final String FRIEND_REQUEST_ACCEPTED = "accepted";
    /** 好友请求状态：已被对方拒绝。 */
    private static final String FRIEND_REQUEST_REJECTED = "rejected";
    /** 好友请求状态：发送方已撤销，保留双方可同步的申请记录。 */
    private static final String FRIEND_REQUEST_CANCELLED = "cancelled";

    // ============ 「正在输入」节流 ============
    /** 节流窗口（毫秒）：同一会话同一用户在该窗口内仅转发一次，防止刷屏与推送风暴。 */
    private static final long TYPING_THROTTLE_MS = 2000L;
    /** 节流表容量上限，超过则整体清理，避免无界增长。 */
    private static final int TYPING_THROTTLE_MAX = 10_000;
    /**
     * 「正在输入」单机节流表：key = 会话维度标识，value = 上次放行时间戳。
     * <p>仅做单实例内存节流（多实例部署下为每实例粒度），对瞬时输入提示足够。
     */
    private final Map<String, Long> typingThrottle = new ConcurrentHashMap<>();

    /** 聊天业务服务，提供私聊/群聊发送、历史、已读等能力。 */
    private final IImChatService imChatService;
    /** 系统通知业务服务，用于创建并推送好友请求等通知。 */
    private final ISysUserNotificationService notificationService;
    /** 系统用户 Mapper，用于查询用户昵称、头像等资料。 */
    private final SysUserMapper userMapper;
    /** 消息 Mapper，直接操作用于好友请求消息、已读计数等。 */
    private final ImMessageMapper messageMapper;
    /** 群组 Mapper。 */
    private final ImGroupMapper groupMapper;
    /** 群成员 Mapper。 */
    private final ImGroupMemberMapper groupMemberMapper;
    /** 系统通知 Mapper，用于好友请求通知的读写。 */
    private final SysUserNotificationMapper notificationMapper;
    /** 好友关系 Mapper。 */
    private final ImFriendMapper friendMapper;
    /** 实时推送组件，向指定用户推送消息信封。 */
    private final ImRealtimePush imRealtimePush;
    /** 会话列表与缓存服务。 */
    private final ImConversationService conversationService;
    /** 会话配置（置顶/免打扰）Mapper。 */
    private final ImConversationSettingMapper conversationSettingMapper;
    /** 消息收藏 Mapper。 */
    private final ImFavoriteMapper favoriteMapper;
    /** 通讯录聚合辅助组件（部门树/好友列表/备注）。 */
    private final ImContactSupport contactSupport;
    /** 在线状态缓存（心跳维护）。 */
    private final ImOnlineCache onlineCache;
    /** 当前用户隐藏的消息，与共享消息记录分开保存。 */
    private final ImMessageVisibility visibility;

    // ==================== Session ====================

    /**
     * IM 登录（无状态，返回当前用户信息即可）
     */
    @PostMapping("/login")
    public R<Map<String, Object>> login() {
        Long userId = LoginHelper.getUserId();
        // 登录即视为活跃，刷新在线标记
        onlineCache.touch(userId);
        SysUserVo user = userMapper.selectVoById(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("userName", user != null ? user.getUserName() : "");
        data.put("nickName", user != null ? user.getNickName() : "");
        data.put("avatar", imChatService.resolveAvatarUrl(user != null ? user.getAvatar() : null));
        return R.ok(data);
    }

    /**
     * IM 登出（无状态），清除在线标记
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        onlineCache.offline(LoginHelper.getUserId());
        return R.ok();
    }

    /**
     * 在线心跳：App 前端随 WebSocket 心跳周期调用，刷新在线状态 TTL
     */
    @PostMapping("/heartbeat")
    public R<Void> heartbeat() {
        onlineCache.touch(LoginHelper.getUserId());
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
        // P0-1: 补跨租户校验，防止越租户读取用户资料
        requireSameTenantUser(userId);
        SysUserVo user = userMapper.selectVoById(userId);
        return user != null ? R.ok(user) : R.fail("用户不存在");
    }

    // ==================== 通讯录 ====================

    /**
     * 通讯录聚合接口：部门树 + 每部门直属成员（含岗位、在线状态、人数统计）。
     * <p>仅登录即可访问，替代原前端拼装 system:dept/list + system:user/list 的管理端接口组合。
     */
    @GetMapping("/contact/list")
    public R<List<ImContactDeptVo>> contactList() {
        return R.ok(contactSupport.buildContactTree());
    }

    // ==================== 好友列表 ====================

    /**
     * 好友列表（批量组装用户资料/头像/在线状态，附备注）
     */
    @GetMapping("/friend/list")
    public R<List<ImFriendVo>> friendList() {
        Long me = LoginHelper.getUserId();
        return R.ok(contactSupport.buildFriendList(me));
    }

    /**
     * 修改好友备注（仅本人维度，只影响我看到的名称）
     */
    @PostMapping("/friend/remark")
    public R<Void> updateFriendRemark(@RequestBody Map<String, Object> body) {
        Long friendUserId = body.get("friendUserId") != null
            ? Long.valueOf(body.get("friendUserId").toString()) : null;
        if (friendUserId == null || friendUserId <= 0) {
            return R.fail("好友ID无效");
        }
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        if (remark != null && remark.length() > 500) {
            return R.fail("备注最长500字符");
        }
        boolean updated = contactSupport.updateFriendRemark(LoginHelper.getUserId(), friendUserId, remark);
        return updated ? R.ok() : R.fail("好友关系不存在");
    }

    // ==================== 好友请求 ====================

    /**
     * 发送好友请求
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/friend/request/send")
    public R<Void> sendFriendRequest(@RequestBody Map<String, String> body) {
        Long fromUserId = LoginHelper.getUserId();
        Long toUserId = positiveId(body.get("toUserId"), "接收者");
        if (fromUserId.equals(toUserId)) {
            return R.fail("不能添加自己为好友");
        }
        requireSameTenantUser(toUserId);
        String requestMessage = StrUtil.blankToDefault(body.get("message"), "请求添加您为好友");
        if (requestMessage.length() > 500) {
            return R.fail("好友申请内容不能超过500个字符");
        }
        SysUserVo fromUser = userMapper.selectVoById(fromUserId);
        String senderName = userName(fromUser);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("fromUserInfo", friendUserInfo(fromUser));
        content.put("requestMessage", requestMessage);
        content.put("status", "pending");
        ImMessage msg = new ImMessage();
        msg.setFromUserId(fromUserId);
        msg.setToUserId(toUserId);
        msg.setContent(JsonUtils.toJsonString(content));
        msg.setMsgType("friend_request");
        msg.setIsRead("0");
        msg.setRevoked("0");
        messageMapper.insert(msg);

        // 先生成消息 ID，再把精确关联写入通知，两个写入共享一个事务。
        Map<String, Object> ext = friendNotificationExt(msg, senderName, FRIEND_REQUEST_SENT);
        notificationService.addAndPush(toUserId, CATEGORY_FRIEND_REQUEST, "friend_request",
            String.valueOf(fromUserId), senderName + " 请求添加您为好友", requestMessage, JsonUtils.toJsonString(ext));
        pushFriendMessage(msg, fromUser, "pending");
        conversationService.evictConversationCacheForUsers(List.of(fromUserId, toUserId));
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
            .eq(SysUserNotification::getRefId, String.valueOf(me))
            .orderByDesc(SysUserNotification::getCreateTime));

        // P0-3: 原实现用字符串 contains("\"fromUserId\":" + me) 匹配，
        // 修复 L189 后雪花 ID 会被序列化为 String，导致旧匹配逻辑失效。
        // 改为解析 extJson 为 Map，提取 fromUserId 比较字符串值，
        // 兼容旧格式（number）和新格式（string）两种历史数据。
        String meStr = String.valueOf(me);
        List<SysUserNotification> sent = new ArrayList<>();
        for (SysUserNotification n : all) {
            if (StrUtil.isBlank(n.getExtJson())) {
                continue;
            }
            Map<String, Object> ext = JsonUtils.parseMap(n.getExtJson());
            if (ext == null) {
                continue;
            }
            Object fromUserIdVal = ext.get("fromUserId");
            if (fromUserIdVal != null && meStr.equals(fromUserIdVal.toString())) {
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
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/friend/request/handle")
    public R<Void> handleFriendRequest(@RequestBody Map<String, Object> body) {
        Long me = LoginHelper.getUserId();
        Long messageId = optionalId(body.get("messageId"), "消息");
        Long notificationId = optionalId(body.get("id"), "通知");
        if (messageId == null && notificationId == null) {
            return R.fail("请指定好友申请消息或通知");
        }
        boolean accept;
        if (body.get("accept") instanceof Boolean value) {
            accept = value;
        } else if ("accept".equals(body.get("action")) || "reject".equals(body.get("action"))) {
            accept = "accept".equals(body.get("action"));
        } else {
            return R.fail("好友申请处理动作无效");
        }
        ImMessage msg = messageId == null ? null : messageMapper.selectById(messageId);
        if (messageId != null && !isFriendRequestFor(msg, me)) {
            return R.fail("请求不存在或无权处理该好友请求");
        }
        SysUserNotification notification = notificationId == null ? null : notificationMapper.selectById(notificationId);
        if (notificationId != null && !isFriendNotificationFor(notification, me)) {
            return R.fail("请求不存在或无权处理该好友请求");
        }
        if (notification != null && msg == null) {
            msg = resolveFriendMessage(notification);
        }
        if (!isFriendRequestFor(msg, me)) {
            return R.fail("请求不存在或无权处理该好友请求");
        }
        if (notification == null) {
            notification = findFriendNotification(msg);
        }
        // 包含两种 ID 的请求也必须指向同一份申请，全部验证先于任何写入。
        if (!isFriendNotificationFor(notification, me) || !matchesFriendRequest(notification, msg)) {
            return R.fail("通知与好友申请消息不匹配，请刷新后重试");
        }
        requireSameTenantUser(msg.getFromUserId());
        Map<String, Object> content = JsonUtils.parseMap(msg.getContent());
        if (content == null) {
            return R.fail("好友申请内容无效");
        }
        String targetStatus = accept ? FRIEND_REQUEST_ACCEPTED : FRIEND_REQUEST_REJECTED;
        String oldStatus = String.valueOf(content.get("status"));
        if (targetStatus.equals(oldStatus)) {
            return R.ok();
        }
        if (!"pending".equals(oldStatus)) {
            return R.fail("好友申请已处理，不能重复变更");
        }
        content.put("status", targetStatus);
        ImMessage patch = new ImMessage();
        patch.setContent(JsonUtils.toJsonString(content));
        patch.setIsRead("1");
        int changed = messageMapper.update(patch, new LambdaUpdateWrapper<ImMessage>()
            .eq(ImMessage::getMessageId, msg.getMessageId())
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getContent, msg.getContent()));
        if (changed == 0) {
            return R.fail("好友申请状态已变更，请刷新后重试");
        }
        Map<String, Object> ext = notificationExt(notification);
        ext.put("status", targetStatus);
        ext.put("messageId", String.valueOf(msg.getMessageId()));
        SysUserNotification noticePatch = new SysUserNotification();
        noticePatch.setNotificationId(notification.getNotificationId());
        noticePatch.setReadFlag("1");
        noticePatch.setExtJson(JsonUtils.toJsonString(ext));
        if (notificationMapper.updateById(noticePatch) == 0) {
            throw new ServiceException("好友申请通知已变更，请刷新后重试");
        }
        if (accept) {
            addFriendRelation(msg.getFromUserId(), me);
        }
        SysUserVo currentUser = userMapper.selectVoById(me);
        Map<String, Object> resultContent = new LinkedHashMap<>();
        resultContent.put("fromUserInfo", friendUserInfo(currentUser));
        resultContent.put("requestMessage", userName(currentUser) + (accept ? "已同意" : "已拒绝") + "了您的好友申请");
        resultContent.put("status", targetStatus);
        ImMessage result = new ImMessage();
        result.setFromUserId(me);
        result.setToUserId(msg.getFromUserId());
        result.setMsgType("friend_request");
        result.setContent(JsonUtils.toJsonString(resultContent));
        result.setIsRead("0");
        result.setRevoked("0");
        messageMapper.insert(result);
        pushFriendMessage(result, currentUser, targetStatus);
        conversationService.evictConversationCacheForUsers(List.of(me, msg.getFromUserId()));
        return R.ok();
    }

    /** 老通知无消息 ID 时只允许匹配唯一、同时间窗口的申请，避免串改其他请求。 */
    private ImMessage resolveFriendMessage(SysUserNotification notification) {
        Map<String, Object> ext = notificationExt(notification);
        Long linkedId = optionalId(ext.get("messageId"), "消息");
        if (linkedId != null) {
            return messageMapper.selectById(linkedId);
        }
        if (notification.getCreateTime() == null) {
            return null;
        }
        List<ImMessage> candidates = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getFromUserId, positiveId(notification.getRefId(), "申请者"))
            .eq(ImMessage::getToUserId, notification.getUserId())
            .eq(ImMessage::getMsgType, "friend_request")
            .between(ImMessage::getCreateTime, new Date(notification.getCreateTime().getTime() - 5000),
                new Date(notification.getCreateTime().getTime() + 5000)).last("LIMIT 2"));
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private SysUserNotification findFriendNotification(ImMessage msg) {
        List<SysUserNotification> notices = notificationMapper.selectList(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, msg.getToUserId())
            .eq(SysUserNotification::getCategory, CATEGORY_FRIEND_REQUEST)
            .eq(SysUserNotification::getRefType, "friend_request")
            .eq(SysUserNotification::getRefId, String.valueOf(msg.getFromUserId())));
        List<SysUserNotification> matched = notices.stream().filter(n -> matchesFriendRequest(n, msg)).toList();
        return matched.size() == 1 ? matched.get(0) : null;
    }

    private boolean isFriendRequestFor(ImMessage msg, Long userId) {
        return msg != null && userId.equals(msg.getToUserId()) && msg.getGroupId() == null
            && "friend_request".equals(msg.getMsgType()) && !"1".equals(msg.getRevoked());
    }

    private boolean isFriendNotificationFor(SysUserNotification notification, Long userId) {
        return notification != null && userId.equals(notification.getUserId())
            && CATEGORY_FRIEND_REQUEST.equals(notification.getCategory()) && "friend_request".equals(notification.getRefType());
    }

    private boolean matchesFriendRequest(SysUserNotification notification, ImMessage msg) {
        if (notification == null || msg == null || !Objects.equals(notification.getUserId(), msg.getToUserId())
            || !String.valueOf(msg.getFromUserId()).equals(notification.getRefId())) {
            return false;
        }
        Object linkedId = notificationExt(notification).get("messageId");
        if (linkedId != null) {
            return String.valueOf(msg.getMessageId()).equals(String.valueOf(linkedId));
        }
        if (notification.getCreateTime() == null || msg.getCreateTime() == null
            || Math.abs(notification.getCreateTime().getTime() - msg.getCreateTime().getTime()) > 5000) {
            return false;
        }
        Map<String, Object> content = JsonUtils.parseMap(msg.getContent());
        return content != null && Objects.equals(content.get("requestMessage"), notification.getContent());
    }

    private Map<String, Object> notificationExt(SysUserNotification notification) {
        try {
            Map<String, Object> ext = JsonUtils.parseMap(notification.getExtJson());
            return ext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ext);
        } catch (RuntimeException e) {
            throw new ServiceException("好友申请通知内容无效，请刷新后重试");
        }
    }

    private Map<String, Object> friendNotificationExt(ImMessage msg, String senderName, String status) {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("status", status);
        ext.put("fromUserId", String.valueOf(msg.getFromUserId()));
        ext.put("senderId", String.valueOf(msg.getFromUserId()));
        ext.put("messageId", String.valueOf(msg.getMessageId()));
        ext.put("senderName", senderName);
        ext.put("name", senderName);
        ext.put("mentioned", false);
        return ext;
    }

    private Map<String, Object> friendUserInfo(SysUserVo user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("nickName", user == null ? "" : user.getNickName());
        info.put("userName", user == null ? "" : user.getUserName());
        info.put("avatar", imChatService.resolveAvatarUrl(user == null ? null : user.getAvatar()));
        return info;
    }

    private String userName(SysUserVo user) {
        return user == null ? "用户" : StrUtil.blankToDefault(user.getNickName(), user.getUserName());
    }

    private void pushFriendMessage(ImMessage msg, SysUserVo user, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chatType", "PRIVATE");
        payload.put("messageId", msg.getMessageId());
        payload.put("fromUserId", msg.getFromUserId());
        payload.put("toUserId", msg.getToUserId());
        payload.put("content", msg.getContent());
        payload.put("msgType", "friend_request");
        payload.put("status", status);
        payload.put("createTime", msg.getCreateTime());
        payload.put("senderName", userName(user));
        payload.put("senderAvatar", imChatService.resolveAvatarUrl(user == null ? null : user.getAvatar()));
        imRealtimePush.publishToUserAfterCommit(msg.getToUserId(), ImEnvelopeHelper.imMessage(payload));
    }

    /** 唯一键消除并发重复关系，两个方向仍处于同一事务。 */
    private void addFriendRelation(Long userIdA, Long userIdB) {
        for (List<Long> pair : List.of(List.of(userIdA, userIdB), List.of(userIdB, userIdA))) {
            ImFriend relation = new ImFriend();
            relation.setUserId(pair.get(0));
            relation.setFriendUserId(pair.get(1));
            try {
                friendMapper.insert(relation);
            } catch (DuplicateKeyException ignored) {
                // 已存在的好友关系视为幂等成功。
            }
        }
    }

    private Long optionalId(Object value, String label) {
        return value == null ? null : positiveId(value, label);
    }

    private Long positiveId(Object value, String label) {
        try {
            long id = Long.parseLong(String.valueOf(value));
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // 将无效客户端参数转换为可理解的业务结果。
        }
        throw new ServiceException(label + "ID无效");
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
            .like(SysUserNotification::getExtJson, "\"status\":\"sent\""));
        return R.ok(count);
    }

    /**
     * 发送方撤销待处理好友申请，路径参数沿用历史名称，实际传入通知 ID。
     * 保留接收方通知与原消息，便于双方设备同步最终状态。
     */
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/friend/request/{messageId}")
    public R<Void> revokeRequest(@PathVariable Long messageId) {
        Long me = LoginHelper.getUserId();
        SysUserNotification notification = notificationMapper.selectById(messageId);
        if (notification == null || notification.getUserId() == null
            || !CATEGORY_FRIEND_REQUEST.equals(notification.getCategory())
            || !"friend_request".equals(notification.getRefType())
            || !String.valueOf(me).equals(notification.getRefId())) {
            return R.fail("通知不存在或无权操作");
        }
        Map<String, Object> ext = notificationExt(notification);
        if (!Objects.equals(me, optionalId(ext.get("fromUserId"), "申请者"))
            || (ext.get("senderId") != null && !Objects.equals(me, optionalId(ext.get("senderId"), "申请者")))) {
            return R.fail("通知不存在或无权操作");
        }
        ImMessage msg = resolveFriendMessage(notification);
        if (!isFriendRequestFor(msg, notification.getUserId()) || !Objects.equals(me, msg.getFromUserId())
            || !matchesFriendRequest(notification, msg)) {
            return R.fail("通知与好友申请消息不匹配或无权操作，请刷新后重试");
        }
        requireSameTenantUser(notification.getUserId());
        Map<String, Object> content;
        try {
            content = JsonUtils.parseMap(msg.getContent());
        } catch (RuntimeException e) {
            return R.fail("好友申请内容无效");
        }
        if (content == null) {
            return R.fail("好友申请内容无效");
        }
        if (!FRIEND_REQUEST_SENT.equals(ext.get("status")) || !"pending".equals(content.get("status"))) {
            return R.fail("好友申请已处理，不能撤销");
        }
        content.put("status", FRIEND_REQUEST_CANCELLED);
        ImMessage patch = new ImMessage();
        patch.setContent(JsonUtils.toJsonString(content));
        patch.setIsRead("1");
        // 接收方处理申请也比较原内容，确保接受、拒绝与撤销只有一个操作成功。
        int changed = messageMapper.update(patch, new LambdaUpdateWrapper<ImMessage>()
            .eq(ImMessage::getMessageId, msg.getMessageId())
            .eq(ImMessage::getFromUserId, me)
            .eq(ImMessage::getToUserId, notification.getUserId())
            .eq(ImMessage::getContent, msg.getContent()));
        if (changed == 0) {
            return R.fail("好友申请状态已变更，请刷新后重试");
        }
        ext.put("status", FRIEND_REQUEST_CANCELLED);
        ext.put("messageId", String.valueOf(msg.getMessageId()));
        SysUserNotification noticePatch = new SysUserNotification();
        noticePatch.setNotificationId(notification.getNotificationId());
        noticePatch.setExtJson(JsonUtils.toJsonString(ext));
        noticePatch.setReadFlag("1");
        if (notificationMapper.updateById(noticePatch) == 0) {
            throw new ServiceException("好友申请通知已变更，请刷新后重试");
        }
        List<Long> participants = List.of(me, notification.getUserId());
        // 缓存先在提交后失效，再推送原消息更新，避免客户端刷新时命中旧摘要。
        conversationService.evictConversationCacheForUsers(participants);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "friend_request_update");
        payload.put("chatType", "PRIVATE");
        payload.put("messageId", msg.getMessageId());
        payload.put("notificationId", notification.getNotificationId());
        payload.put("fromUserId", msg.getFromUserId());
        payload.put("toUserId", msg.getToUserId());
        payload.put("msgType", "friend_request");
        payload.put("content", patch.getContent());
        payload.put("status", FRIEND_REQUEST_CANCELLED);
        payload.put("isRead", "1");
        payload.put("createTime", msg.getCreateTime());
        imRealtimePush.publishToUsersAfterCommit(participants, ImEnvelopeHelper.imMessage(payload), "private");
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
     * <p>直接绑定 {@link ImPrivateSendBo}（与 PC 端 ImChatController 一致），避免手工
     * Map 映射漏读 clientMsgId（重发幂等失效）等问题。APP 端 messageType 经 @JsonAlias 映射到 msgType。
     */
    @PostMapping("/message/send")
    public R<Long> sendPrivate(@Validated @RequestBody ImPrivateSendBo bo) {
        return imChatService.sendPrivate(bo);
    }

    /**
     * 发送群聊消息
     * <p>直接绑定 {@link ImGroupSendBo}（与 PC 端 ImChatController 一致）。原先用
     * {@code Map<String,String>} 手工映射有两个缺陷：mentionUserIds 是 JSON 数组无法
     * 反序列化为 String（直接 400）；且 mentionUserIds / clientMsgId 未读取，@ 提醒与重发幂等静默失效。
     * APP 端 messageType 经 @JsonAlias 映射到 msgType。
     */
    @PostMapping("/message/send-group")
    public R<Long> sendGroup(@Validated @RequestBody ImGroupSendBo bo) {
        return imChatService.sendGroup(bo);
    }

    /**
     * 「正在输入」上报（瞬时状态，不落库）
     * <p>私聊传 {@code toUserId}，群聊传 {@code groupId}（两者同时传时以群聊为准）。
     * 服务端按会话做 2 秒节流后转发 {@code IM_TYPING} 信封：私聊转发给对端，
     * 群聊转发给群内其他成员；客户端据此展示「对方正在输入…」并在超时后自动消失。
     *
     * @param bo 上报参数（会话对象 + 是否正在输入）
     * @return 统一结果（节流命中时同样返回成功，避免客户端重试）
     */
    @PostMapping("/message/typing")
    public R<Void> typing(@RequestBody ImTypingBo bo) {
        Long me = LoginHelper.getUserId();
        boolean typing = bo.getTyping() == null || bo.getTyping();

        // 群聊：校验成员身份后广播给群内其他成员
        if (bo.getGroupId() != null) {
            requireActiveGroup(bo.getGroupId());
            Long memberCount = groupMemberMapper.selectCount(new LambdaQueryWrapper<ImGroupMember>()
                .eq(ImGroupMember::getGroupId, bo.getGroupId())
                .eq(ImGroupMember::getUserId, me));
            if (memberCount == null || memberCount == 0) {
                return R.fail("你不在该群聊中");
            }
            if (!allowTyping("g:" + bo.getGroupId() + ":" + me)) {
                return R.ok();
            }
            List<Long> receivers = groupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
                    .eq(ImGroupMember::getGroupId, bo.getGroupId()))
                .stream()
                .map(ImGroupMember::getUserId)
                .filter(userId -> userId != null && !userId.equals(me))
                .distinct()
                .toList();
            if (receivers.isEmpty()) {
                return R.ok();
            }
            imRealtimePush.publishToUsers(receivers,
                ImEnvelopeHelper.imTyping(buildTypingPayload(me, null, bo.getGroupId(), typing)), "group");
            return R.ok();
        }

        // 私聊：转发给对端
        if (bo.getToUserId() == null) {
            return R.fail("接收者不能为空");
        }
        if (me.equals(bo.getToUserId())) {
            return R.ok();
        }
        requireSameTenantUser(bo.getToUserId());
        if (!allowTyping("p:" + me + ":" + bo.getToUserId())) {
            return R.ok();
        }
        imRealtimePush.publishToUser(bo.getToUserId(),
            ImEnvelopeHelper.imTyping(buildTypingPayload(me, bo.getToUserId(), null, typing)), "private");
        return R.ok();
    }

    /**
     * 「正在输入」单机节流：返回 true 表示本次允许转发。
     *
     * @param key 会话维度标识（私聊 p:发送方:接收方，群聊 g:群ID:发送方）
     */
    private boolean allowTyping(String key) {
        long now = System.currentTimeMillis();
        if (typingThrottle.size() > TYPING_THROTTLE_MAX) {
            typingThrottle.clear();
        }
        Long last = typingThrottle.put(key, now);
        return last == null || now - last >= TYPING_THROTTLE_MS;
    }

    /**
     * 构造 {@code IM_TYPING} 载荷。ID 统一转字符串，避免 JS 端雪花 ID 精度丢失。
     *
     * @param fromUserId 输入方用户 ID
     * @param peerUserId 私聊接收方（群聊传 null）
     * @param groupId    群 ID（私聊传 null）
     * @param typing     是否正在输入
     */
    private Map<String, Object> buildTypingPayload(Long fromUserId, Long peerUserId, Long groupId, boolean typing) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chatType", groupId != null ? "GROUP" : "PRIVATE");
        payload.put("fromUserId", String.valueOf(fromUserId));
        SysUserVo from = userMapper.selectVoById(fromUserId);
        payload.put("fromUserName", from == null ? "" : StrUtil.blankToDefault(from.getNickName(), from.getUserName()));
        if (peerUserId != null) {
            payload.put("peerUserId", String.valueOf(peerUserId));
        }
        if (groupId != null) {
            payload.put("groupId", String.valueOf(groupId));
        }
        payload.put("typing", typing);
        return payload;
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
        LambdaQueryWrapper<ImMessage> query = new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me).eq(ImMessage::getIsRead, "0")
            .eq(ImMessage::getRevoked, "0").isNull(ImMessage::getGroupId);
        visibility.excludeHidden(query);
        long count = messageMapper.selectCount(query);
        List<Long> groupIds = activeGroupIds(me);
        if (!groupIds.isEmpty()) {
            count += messageMapper.selectGroupUnreadCounts(TenantHelper.getTenantId(), me, groupIds, visibility.hiddenIds())
                .stream().mapToLong(row -> ((Number) row.get("cnt")).longValue()).sum();
        }
        return R.ok(count);
    }

    /**
     * 未读消息列表（分页）
     * P1-3: 原实现返回全部未读，未读 1000+ 条时单次响应过大，改为分页
     */
    @GetMapping("/message/unread")
    public TableDataInfo<ImMessage> unreadMessages(PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId)
            .orderByDesc(ImMessage::getCreateTime);
        visibility.excludeHidden(w);
        Page<ImMessage> page = messageMapper.selectPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    /**
     * P4-4: 增量同步消息（App syncToken 模式）。
     * <p>
     * App 端持久化 syncToken（上次同步的最后 messageId），重连时传入 sinceMessageId 参数，
     * 后端返回该 ID 之后的所有新消息（私聊 + 群聊），最多 500 条。
     * <p>
     * 相比 {@link #unreadMessages} 的优势：
     * <ul>
     *   <li>支持群聊消息同步（unreadMessages 仅返回私聊未读）</li>
     *   <li>增量拉取，避免每次重连全量扫描（syncUnreadMessages 原本固定拉 200 条）</li>
     *   <li>未读超过 200 条不漏（hasMore=true 时客户端循环拉取）</li>
     *   <li>已读消息也能同步（用于多端消息漫游，A 端发送 B 端拉取）</li>
     * </ul>
     * <p>
     * 查询范围：当前用户作为发送者或接收者的私聊消息 + 当前用户所在群组的群聊消息。
     * <p>
     * 一致性：syncToken 仅前进不回退。无新消息时返回空列表 + 入参 sinceMessageId。
     * 撤回事件不依赖此接口，由 WS 推送独立通道处理（P4-8）。
     *
     * @param sinceMessageId 上次同步的最后 messageId（null 或 0 表示首次同步，拉取最近 500 条）
     */
    @GetMapping("/messages/sync")
    public R<ImSyncResultVo> syncMessages(@RequestParam(required = false) Long sinceMessageId) {
        Long me = LoginHelper.getUserId();
        long baseline = sinceMessageId == null ? 0L : sinceMessageId;

        // 隐藏列表与本批查询共用同一次读取，即使无新消息也返回完整快照。
        Set<Long> hiddenMessageIds = visibility.hiddenIds();

        // 1. 查询当前用户参与的群组（1 SQL）
        List<Long> groupIds = activeGroupIds(me);

        // 2. 查询 sinceMessageId 之后的消息（私聊 + 群聊），按 messageId 升序，limit 500
        //    私聊：from_user_id = me OR to_user_id = me
        //    群聊：group_id IN (user's groups)
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .gt(ImMessage::getMessageId, baseline);
        if (groupIds.isEmpty()) {
            // 无群组：仅查私聊
            w.isNull(ImMessage::getGroupId).and(p -> p.eq(ImMessage::getFromUserId, me).or().eq(ImMessage::getToUserId, me));
        } else {
            // 有群组：私聊 OR 群聊
            w.and(p -> p
                .and(q -> q.isNull(ImMessage::getGroupId).and(pair -> pair.eq(ImMessage::getFromUserId, me).or().eq(ImMessage::getToUserId, me)))
                .or(q -> q.in(ImMessage::getGroupId, groupIds)));
        }
        w.notIn(!hiddenMessageIds.isEmpty(), ImMessage::getMessageId, hiddenMessageIds);
        w.orderByAsc(ImMessage::getMessageId).last("limit 500");
        List<ImMessage> messages = messageMapper.selectList(w);
        fillGroupReadState(messages, me);

        // 3. 计算 newSyncToken + hasMore
        Long newSyncToken = messages.isEmpty() ? baseline : messages.get(messages.size() - 1).getMessageId();
        boolean hasMore = messages.size() >= 500;

        ImSyncResultVo vo = new ImSyncResultVo();
        vo.setMessages(messages);
        vo.setNewSyncToken(newSyncToken);
        vo.setHasMore(hasMore);
        vo.setHiddenMessageIds(hiddenMessageIds.stream().sorted().toList());
        return R.ok(vo);
    }

    /**
     * P4-8: 消息搜索（LIKE 查询，限制 7 天内）。
     * <p>搜索当前用户参与的私聊 + 群聊消息，按 createTime 降序分页返回。
     * <p>性能：LIKE 全表扫描，限制 7 天 + 分页 20 条缓解；P5 评估引入 ES 索引。
     *
     * @param keyword   搜索关键词
     * @param chatType  可选筛选：PRIVATE / GROUP（不传则搜索全部）
     * @param targetId  可选筛选：指定私聊对方 userId 或群 groupId
     * @param pageNum   页码（默认 1）
     * @param pageSize  每页条数（默认 20）
     */
    @GetMapping("/messages/search")
    public R<ImSearchResultVo> searchMessages(
        @RequestParam String keyword,
        @RequestParam(required = false) String chatType,
        @RequestParam(required = false) Long targetId,
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "20") Integer pageSize) {

        if (StrUtil.isBlank(keyword)) {
            return R.ok(new ImSearchResultVo(Collections.emptyList(), pageNum, pageSize));
        }

        // P5-4 LOW: pageSize clamp 防止 DoS——攻击者传 pageSize=1000000 会拖垮 DB 内存与带宽
        // 上限 100 覆盖移动端合理翻页需求（默认 20），同时阻断异常大页
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        } else if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }

        Long me = LoginHelper.getUserId();
        long offset = (long) (pageNum - 1) * pageSize;
        Date sevenDaysAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));

        // 1. 查询当前用户参与的群组
        List<Long> groupIds = activeGroupIds(me);

        // 2. 构造查询条件：关键词 LIKE + 7 天内 + 当前用户可见的消息
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .like(ImMessage::getContent, keyword)
            .ge(ImMessage::getCreateTime, sevenDaysAgo)
            .eq(ImMessage::getRevoked, "0");

        // 可见范围：私聊（from=me OR to=me）OR 群聊（group_id IN user's groups）
        boolean filterPrivate = "PRIVATE".equalsIgnoreCase(chatType);
        boolean filterGroup = "GROUP".equalsIgnoreCase(chatType);

        if (filterPrivate) {
            w.isNull(ImMessage::getGroupId);
            // 仅搜私聊
            if (targetId != null) {
                w.and(p -> p
                    .and(q -> q.eq(ImMessage::getFromUserId, me).eq(ImMessage::getToUserId, targetId))
                    .or(q -> q.eq(ImMessage::getFromUserId, targetId).eq(ImMessage::getToUserId, me)));
            } else {
                w.isNull(ImMessage::getGroupId).and(p -> p.eq(ImMessage::getFromUserId, me).or().eq(ImMessage::getToUserId, me));
            }
        } else if (filterGroup) {
            // 仅搜群聊
            if (targetId != null) {
                // 越权防护：仅群成员可搜索该群消息
                if (!groupIds.contains(targetId)) {
                    return R.ok(new ImSearchResultVo(Collections.emptyList(), pageNum, pageSize));
                }
                w.eq(ImMessage::getGroupId, targetId);
            } else if (!groupIds.isEmpty()) {
                w.in(ImMessage::getGroupId, groupIds);
            } else {
                // 无群组，返回空
                return R.ok(new ImSearchResultVo(Collections.emptyList(), pageNum, pageSize));
            }
        } else {
            // 搜全部（私聊 + 群聊）
            if (groupIds.isEmpty()) {
                w.isNull(ImMessage::getGroupId).and(p -> p.eq(ImMessage::getFromUserId, me).or().eq(ImMessage::getToUserId, me));
            } else {
                w.and(p -> p
                    .and(q -> q.isNull(ImMessage::getGroupId).and(pair -> pair.eq(ImMessage::getFromUserId, me).or().eq(ImMessage::getToUserId, me)))
                    .or(q -> q.in(ImMessage::getGroupId, groupIds)));
            }
        }

        visibility.excludeHidden(w);
        w.orderByDesc(ImMessage::getCreateTime).orderByDesc(ImMessage::getMessageId)
            .last("limit " + offset + "," + pageSize);

        List<ImMessage> messages = messageMapper.selectList(w);
        fillGroupReadState(messages, me);
        return R.ok(new ImSearchResultVo(messages, pageNum, pageSize));
    }

    /**
     * 撤回消息
     * <p>P6 M1 修复：委托给 imChatService.revokeMessage，复用 Service 层 2 分钟窗口校验
     * 与撤回事件推送（publishToUser(s)AfterCommit），与 PC 端 ImChatController 行为对齐。
     */
    @SaCheckLogin
    @PostMapping("/message/revoke")
    public R<Void> revokeMessage(@RequestBody Map<String, Object> body) {
        Long messageId = Long.valueOf(body.get("messageId").toString());
        return imChatService.revokeMessage(messageId);
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/message/{messageId}")
    public R<Void> deleteMessage(@PathVariable Long messageId) {
        // P0-1: 原实现直接 deleteById 无归属校验，任意用户可删任意消息
        Long me = LoginHelper.getUserId();
        ImMessage msg = messageMapper.selectById(messageId);
        if (msg == null) {
            return R.fail("消息不存在");
        }
        if (!canAccessMessage(msg, me)) {
            return R.fail("无权操作此消息");
        }
        visibility.hide(messageId);
        conversationService.evictConversationCache();
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
        // P0-1: 原实现不校验调用方是否有权读原消息，存在越权转发风险
        Long me = LoginHelper.getUserId();
        if (!canAccessMessage(original, me)) {
            return R.fail("无权转发此消息");
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
    public R<Long> createGroup(@Validated @RequestBody ImGroupCreateBo bo) {
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
        // 越权防护：仅群成员可查看群详情（与 PC 端 listGroupMembers 行为对齐）
        if (!isGroupMember(groupId, LoginHelper.getUserId())) {
            return R.fail("无权查看该群组");
        }
        ImGroup g = requireActiveGroup(groupId);
        Map<String, Object> map = new HashMap<>();
        map.put("groupId", g.getGroupId());
        map.put("groupName", g.getGroupName());
        map.put("avatar", g.getAvatar());
        map.put("ownerUserId", g.getOwnerUserId());
        map.put("status", g.getStatus());
        map.put("description", g.getRemark());
        map.put("memberCount", groupMemberMapper.selectCount(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)));
        return R.ok(map);
    }

    /**
     * 群成员
     */
    @GetMapping("/group/members")
    public R<List<Map<String, Object>>> groupMembers(@RequestParam Long groupId) {
        // 越权防护：仅群成员可查看成员名单（与 PC 端 listGroupMembers 行为对齐）
        if (!isGroupMember(groupId, LoginHelper.getUserId())) {
            return R.fail("无权查看该群组成员");
        }
        ImGroup group = requireActiveGroup(groupId);
        List<ImGroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId));
        boolean hasOwner = group != null && group.getOwnerUserId() != null;

        // 批量加载用户资料与头像 URL（消除 N+1；avatar 由 OSS ID 解析为可访问 URL）
        Set<Long> userIds = members.stream()
            .map(ImGroupMember::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, SysUserVo> usersById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUserVo u : userMapper.selectVoByIds(userIds)) {
                if (u != null && u.getUserId() != null) {
                    usersById.put(u.getUserId(), u);
                }
            }
        }
        // 批量解析头像 OSS URL
        Set<Long> avatarIds = usersById.values().stream()
            .map(SysUserVo::getAvatar)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> avatarUrlMap = new HashMap<>();
        for (Long ossId : avatarIds) {
            String url = imChatService.resolveAvatarUrl(ossId);
            if (url != null && !url.isEmpty()) {
                avatarUrlMap.put(ossId, url);
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (ImGroupMember m : members) {
            SysUserVo u = usersById.get(m.getUserId());
            Map<String, Object> map = new HashMap<>();
            map.put("userId", m.getUserId());
            map.put("userName", u != null ? u.getUserName() : "");
            map.put("nickName", u != null ? u.getNickName() : "");
            Long avatarId = u != null ? u.getAvatar() : null;
            map.put("avatar", avatarId == null ? "" : avatarUrlMap.getOrDefault(avatarId, ""));
            map.put("joinTime", m.getJoinTime());
            map.put("role", hasOwner && group.getOwnerUserId().equals(m.getUserId()) ? "owner" : "member");
            list.add(map);
        }
        return R.ok(list);
    }

    /**
     * 添加群成员
     */
    @PostMapping("/group/add-members")
    public R<Void> addMembers(@Validated @RequestBody AppImGroupMemberAddBo bo) {
        return imChatService.addGroupMembers(bo.getGroupId(), bo);
    }

    /**
     * 移除群成员
     */
    @PostMapping("/group/remove-member")
    public R<Void> removeMember(@RequestBody Map<String, String> body) {
        return imChatService.removeMember(positiveId(body.get("groupId"), "群"), positiveId(body.get("userId"), "成员"));
    }

    /** 群主修改群资料后刷新所有成员的会话摘要和合成头像。 */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/group/update")
    public R<Void> updateGroup(@RequestBody Map<String, String> body) {
        Long groupId = positiveId(body.get("groupId"), "群");
        ImGroup group = requireActiveGroup(groupId);
        if (!LoginHelper.getUserId().equals(group.getOwnerUserId())) {
            return R.fail("仅群主可修改群信息");
        }
        ImGroup update = new ImGroup();
        update.setGroupId(groupId);
        if (body.containsKey("groupName")) {
            String name = StrUtil.trim(body.get("groupName"));
            if (StrUtil.isBlank(name) || name.length() > 50) return R.fail("群名称须为1至50个字符");
            update.setGroupName(name);
        }
        if (body.containsKey("avatar")) update.setAvatar(body.get("avatar"));
        if (body.containsKey("description")) {
            if (body.get("description") != null && body.get("description").length() > 500) return R.fail("群描述不能超过500个字符");
            update.setRemark(body.get("description"));
        }
        groupMapper.updateById(update);
        evictGroupMembers(groupId);
        return R.ok();
    }

    /** 群主必须先解散群或转交管理权，避免留下无群主成员的群。 */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/group/quit")
    public R<Void> quitGroup(@RequestBody Map<String, String> body) {
        Long groupId = positiveId(body.get("groupId"), "群");
        ImGroup group = requireActiveGroup(groupId);
        Long me = LoginHelper.getUserId();
        if (me.equals(group.getOwnerUserId())) return R.fail("群主请先解散群聊");
        evictGroupMembers(groupId);
        groupMemberMapper.delete(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId).eq(ImGroupMember::getUserId, me));
        return R.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/group/disband")
    public R<Void> disbandGroup(@RequestBody Map<String, String> body) {
        Long groupId = positiveId(body.get("groupId"), "群");
        ImGroup group = requireActiveGroup(groupId);
        if (!LoginHelper.getUserId().equals(group.getOwnerUserId())) return R.fail("仅群主可解散群聊");
        ImGroup update = new ImGroup();
        update.setGroupId(groupId);
        update.setStatus("1");
        groupMapper.updateById(update);
        evictGroupMembers(groupId);
        return R.ok();
    }

    private ImGroup requireActiveGroup(Long groupId) {
        ImGroup group = groupMapper.selectById(groupId);
        if (group == null || !"0".equals(group.getStatus())) throw new ServiceException("群组不存在或已解散");
        return group;
    }

    /** 同步和搜索也返回当前接收者的已读状态，兼容旧共享 is_read 数据。 */
    private void fillGroupReadState(List<ImMessage> messages, Long me) {
        List<String> groupMessageIds = messages.stream().filter(m -> m.getGroupId() != null)
            .map(m -> String.valueOf(m.getMessageId())).toList();
        if (groupMessageIds.isEmpty()) return;
        List<SysUserNotification> notices = notificationMapper.selectList(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, me)
            .eq(SysUserNotification::getCategory, ImPayloadConstants.CATEGORY_IM_GROUP)
            .eq(SysUserNotification::getRefType, "im_message")
            .in(SysUserNotification::getRefId, groupMessageIds));
        com.nexus.im.service.impl.support.ImChatSupport.applyGroupReadState(messages, notices);
    }

    private List<Long> activeGroupIds(Long userId) {
        List<Long> ids = groupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getUserId, userId)).stream().map(ImGroupMember::getGroupId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Collections.emptyList();
        return groupMapper.selectByIds(ids).stream().filter(g -> "0".equals(g.getStatus())).map(ImGroup::getGroupId).toList();
    }

    private void evictGroupMembers(Long groupId) {
        List<Long> ids = groupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)).stream().map(ImGroupMember::getUserId).toList();
        conversationService.evictConversationCacheForUsers(ids);
    }

    // ==================== P0-1 安全校验辅助方法 ====================

    /**
     * 校验目标用户与当前登录用户属于同一租户（多租户开启时生效）。
     * 校验失败抛出 ServiceException，与 ImChatServiceImpl 中的同名方法保持一致语义。
     */
    private void requireSameTenantUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException("用户ID无效");
        }
        SysUserVo u = userMapper.selectVoById(userId);
        if (u == null) {
            throw new ServiceException("用户不存在");
        }
        if (TenantHelper.isEnable() && !StringUtils.equals(TenantHelper.getTenantId(), u.getTenantId())) {
            throw new ServiceException("不可跨租户通讯");
        }
    }

    /**
     * 判断用户是否为指定群组的成员。
     */
    private boolean isGroupMember(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            return false;
        }
        return groupMemberMapper.selectCount(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .eq(ImGroupMember::getUserId, userId)) > 0;
    }

    /**
     * 判断用户是否有权访问某条消息（用于 delete/forward 等操作的归属校验）。
     * - 私聊：发送方或接收方
     * - 群聊：群组成员
     */
    private boolean canAccessMessage(ImMessage msg, Long userId) {
        if (msg == null || userId == null) {
            return false;
        }
        if (msg.getGroupId() != null) {
            return isGroupMember(msg.getGroupId(), userId);
        }
        return userId.equals(msg.getFromUserId()) || userId.equals(msg.getToUserId());
    }

    // =============== P6: 会话配置（置顶/免打扰）===============

    /**
     * P6-B1: 查询当前用户所有会话配置
     */
    @GetMapping("/conversation/settings")
    public R<List<ImConversationSetting>> conversationSettings() {
        Long me = LoginHelper.getUserId();
        return R.ok(conversationSettingMapper.selectList(
            new LambdaQueryWrapper<ImConversationSetting>().eq(ImConversationSetting::getUserId, me)));
    }

    /**
     * P6-B1: 设置置顶（isPinned=true 置顶 / isPinned=false 取消置顶）
     */
    @PostMapping("/conversation/pin")
    public R<Void> pinConversation(@RequestBody Map<String, Object> body) {
        Long me = LoginHelper.getUserId();
        String chatType = (String) body.get("chatType");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        boolean pinned = Boolean.TRUE.equals(body.get("isPinned"));

        ImConversationSetting s = getOrCreateSetting(me, chatType, targetId);
        ImConversationSetting update = new ImConversationSetting();
        update.setId(s.getId());
        update.setIsPinned(pinned ? 1 : 0);
        update.setPinnedAt(pinned ? new java.util.Date() : null);
        conversationSettingMapper.updateById(update);
        conversationService.evictConversationCache();
        return R.ok();
    }

    /**
     * P6-B1: 设置免打扰（isMuted=true 免打扰 / isMuted=false 取消）
     */
    @PostMapping("/conversation/mute")
    public R<Void> muteConversation(@RequestBody Map<String, Object> body) {
        Long me = LoginHelper.getUserId();
        String chatType = (String) body.get("chatType");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        boolean muted = Boolean.TRUE.equals(body.get("isMuted"));

        ImConversationSetting s = getOrCreateSetting(me, chatType, targetId);
        ImConversationSetting update = new ImConversationSetting();
        update.setId(s.getId());
        update.setIsMuted(muted ? 1 : 0);
        conversationSettingMapper.updateById(update);
        conversationService.evictConversationCache();
        return R.ok();
    }

    /**
     * P6-B1: 取消会话所有配置（删除记录）
     */
    @DeleteMapping("/conversation/setting")
    public R<Void> removeConversationSetting(@RequestParam String chatType, @RequestParam Long targetId) {
        Long me = LoginHelper.getUserId();
        conversationSettingMapper.delete(new LambdaQueryWrapper<ImConversationSetting>()
            .eq(ImConversationSetting::getUserId, me)
            .eq(ImConversationSetting::getChatType, chatType)
            .eq(ImConversationSetting::getTargetId, targetId));
        conversationService.evictConversationCache();
        return R.ok();
    }

    /**
     * P6-B1: 获取或创建会话配置记录（首次操作时 INSERT，后续 UPDATE）
     * <p>P6 M2 修复：捕获 {@link DuplicateKeyException} 处理并发竞态。
     * 表已有唯一索引 uk_user_chat_target(user_id, chat_type, target_id, tenant_id)，
     * 并发场景下两个请求同时 selectOne 返回 null 后会先后 insert，第二个抛 DuplicateKeyException。
     * 捕获后重新 selectOne 返回已存在记录，与 ImGroupChatService.addGroupMembers 同模式。
     */
    private ImConversationSetting getOrCreateSetting(Long userId, String chatType, Long targetId) {
        LambdaQueryWrapper<ImConversationSetting> qw = new LambdaQueryWrapper<ImConversationSetting>()
            .eq(ImConversationSetting::getUserId, userId)
            .eq(ImConversationSetting::getChatType, chatType)
            .eq(ImConversationSetting::getTargetId, targetId);
        ImConversationSetting existing = conversationSettingMapper.selectOne(qw);
        if (existing != null) {
            return existing;
        }
        ImConversationSetting s = new ImConversationSetting();
        s.setUserId(userId);
        s.setChatType(chatType);
        s.setTargetId(targetId);
        s.setIsPinned(0);
        s.setIsMuted(0);
        try {
            conversationSettingMapper.insert(s);
            return s;
        } catch (DuplicateKeyException e) {
            // 并发场景：另一个请求已插入，重新查询返回
            return conversationSettingMapper.selectOne(qw);
        }
    }

    // =============== P6: 消息收藏 ===============

    /**
     * P6-B2: 收藏消息
     * <p>幂等：已收藏则直接返回成功。
     * <p>越权：非消息发送者/接收者/群成员不可收藏。
     * <p>P6 M3 修复：
     * <ul>
     *   <li>NPE：原 {@code msg.getFromUserId().equals(me)} 反向调用，fromUserId 理论可为 null，
     *       改为 {@code me.equals(msg.getFromUserId())} 防止 NPE。</li>
     *   <li>并发竞态：selectCount 与 insert 之间存在 TOCTOU 窗口，并发重复收藏时
     *       第二个 insert 抛 {@link DuplicateKeyException}，捕获后视为幂等成功。</li>
     * </ul>
     */
    @PostMapping("/favorite/add")
    public R<Void> addFavorite(@RequestBody Map<String, Object> body) {
        Long me = LoginHelper.getUserId();
        Long messageId = Long.valueOf(body.get("messageId").toString());
        ImMessage msg = messageMapper.selectById(messageId);
        if (msg == null) return R.fail("消息不存在");
        if (!canAccessMessage(msg, me)) return R.fail("无权收藏此消息");

        // 幂等：已收藏则直接返回
        long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<ImFavorite>()
            .eq(ImFavorite::getUserId, me).eq(ImFavorite::getMessageId, messageId));
        if (exists > 0) return R.ok();

        ImFavorite fav = new ImFavorite();
        fav.setUserId(me);
        fav.setMessageId(messageId);
        // 群消息：chatType=GROUP，targetId=groupId；私聊消息：chatType=PRIVATE，targetId=对端
        if (msg.getGroupId() != null) {
            fav.setChatType("GROUP");
            fav.setTargetId(msg.getGroupId());
        } else {
            fav.setChatType("PRIVATE");
            // M3 修复 NPE：改为 me.equals(...) 防止 fromUserId 为 null 时抛 NPE
            fav.setTargetId(me.equals(msg.getFromUserId()) ? msg.getToUserId() : msg.getFromUserId());
        }
        fav.setFromUserId(msg.getFromUserId());
        fav.setMsgType(msg.getMsgType());
        fav.setContentSnippet(truncate(msg.getContent(), 500));
        try {
            favoriteMapper.insert(fav);
        } catch (DuplicateKeyException e) {
            // 并发场景：另一个请求已收藏，视为幂等成功
        }
        return R.ok();
    }

    /**
     * P6-B2: 取消收藏
     */
    @DeleteMapping("/favorite/{favoriteId}")
    public R<Void> removeFavorite(@PathVariable Long favoriteId) {
        Long me = LoginHelper.getUserId();
        ImFavorite fav = favoriteMapper.selectById(favoriteId);
        if (fav == null || !me.equals(fav.getUserId())) return R.fail("收藏不存在或无权操作");
        favoriteMapper.deleteById(favoriteId);
        return R.ok();
    }

    /**
     * P6-B2: 收藏列表（分页）
     */
    @GetMapping("/favorite/list")
    public TableDataInfo<ImFavorite> favoriteList(PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        Page<ImFavorite> page = favoriteMapper.selectPage(pageQuery.build(),
            new LambdaQueryWrapper<ImFavorite>()
                .eq(ImFavorite::getUserId, me)
                .orderByDesc(ImFavorite::getCreateTime));
        return TableDataInfo.build(page);
    }

    /**
     * P6-B2: 字符串截断辅助方法
     */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
