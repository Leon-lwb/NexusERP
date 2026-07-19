package com.nexus.im.service.impl.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.im.constants.ImPayloadConstants;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.domain.bo.ImGroupCreateBo;
import com.nexus.im.domain.bo.ImGroupMemberAddBo;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.vo.ImGroupMemberVo;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.msgtype.ImMessageTypeHandler;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImMetrics;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * P1-7: 群聊子 Service
 * <p>从 ImChatServiceImpl 拆分，负责群聊消息发送、历史、已读标记、群组管理。
 *
 * @author nexus
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImGroupChatService {

    private final ImMessageMapper imMessageMapper;
    private final ImGroupMapper imGroupMapper;
    private final ImGroupMemberMapper imGroupMemberMapper;
    private final ISysUserService sysUserService;
    private final ISysUserNotificationService sysUserNotificationService;
    private final ImRealtimePush imRealtimePush;
    private final ImMessageTypeRegistry messageTypeRegistry;
    private final ImChatSupport support;
    private final ImConversationCache conversationCache;
    private final ImMetrics imMetrics;

    @Transactional(rollbackFor = Exception.class)
    public R<Long> sendGroup(ImGroupSendBo bo) {
        Long fromId = LoginHelper.getUserId();
        ImGroup group = imGroupMapper.selectById(bo.getGroupId());
        if (group == null || !"0".equals(group.getStatus())) {
            return R.fail("群组不存在或已解散");
        }
        if (!support.isGroupMember(bo.getGroupId(), fromId)) {
            return R.fail("您不是该群成员");
        }
        // P1-4: client_msg_id 幂等检查 — 重发命中直接返回旧 messageId
        Long existingId = support.findExistingByClientMsgId(bo.getClientMsgId(), fromId, null, bo.getGroupId());
        if (existingId != null) {
            return R.ok(existingId);
        }
        // P1-2: 消息类型策略模式，替代硬编码 SUPPORTED_MSG_TYPES
        ImMessageTypeHandler handler = messageTypeRegistry.require(bo.getMsgType());
        String content = handler.normalize(bo.getContent());
        String msgType = handler.type();
        // P1-5: 校验 @ 提及的用户均为该群成员，并序列化为 JSON 字符串
        List<Long> mentionList = normalizeMentionUserIds(bo.getMentionUserIds(), bo.getGroupId(), fromId);
        String mentionUserIdsJson = mentionList.isEmpty() ? null : JsonUtils.toJsonString(mentionList);
        ImMessage msg = new ImMessage();
        msg.setFromUserId(fromId);
        msg.setGroupId(bo.getGroupId());
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setIsRead("0");
        msg.setRevoked("0");
        msg.setForwardFrom(bo.getForwardFrom());
        msg.setClientMsgId(StrUtil.trimToNull(bo.getClientMsgId()));
        msg.setMentionUserIds(mentionUserIdsJson);
        imMessageMapper.insert(msg);

        SysUserVo from = sysUserService.selectUserById(fromId);
        String senderName = from != null ? StrUtil.blankToDefault(from.getNickName(), from.getUserName()) : "";

        Set<Long> mentionSet = new LinkedHashSet<>(mentionList);
        List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, bo.getGroupId()));

        // P4-1: 群消息扇出异步化 + 批量通知
        // 原实现 for 循环逐成员 addAndPush（N 次 INSERT + N 次注册 afterCommit），
        // 1000 人群 = 1000 次 INSERT + 1000 次 afterCommit 钩子，事务持有时间 ∝ 群规模。
        // 改为：1 次批量 INSERT（BaseMapperPlus.insertBatch）+ 2 次 publishToUsersAfterCommit（按 mentioned 分组）。
        List<Long> mentionedUserIds = new ArrayList<>();
        List<Long> notMentionedUserIds = new ArrayList<>();
        for (ImGroupMember m : members) {
            if (m.getUserId().equals(fromId)) {
                continue;
            }
            if (mentionSet.contains(m.getUserId())) {
                mentionedUserIds.add(m.getUserId());
            } else {
                notMentionedUserIds.add(m.getUserId());
            }
        }
        // 批量构造通知实体（2 种 title：被 @ / 未 @）
        String mentionedTitle = "[@你] 群聊 · " + group.getGroupName();
        String notMentionedTitle = "群聊 · " + group.getGroupName();
        String previewContent = senderName + "：" + StrUtil.maxLength(content, 400);
        List<SysUserNotification> notifications = new ArrayList<>(mentionedUserIds.size() + notMentionedUserIds.size());
        for (Long uid : mentionedUserIds) {
            notifications.add(buildGroupNotification(uid, ImPayloadConstants.CATEGORY_IM_GROUP,
                "im_message", String.valueOf(msg.getMessageId()), mentionedTitle, previewContent));
        }
        for (Long uid : notMentionedUserIds) {
            notifications.add(buildGroupNotification(uid, ImPayloadConstants.CATEGORY_IM_GROUP,
                "im_message", String.valueOf(msg.getMessageId()), notMentionedTitle, previewContent));
        }
        // 一次 SQL 批量插入通知（替代原 N 次循环 insert）
        if (!notifications.isEmpty()) {
            sysUserNotificationService.batchAddAndPush(notifications);
        }
        // IM 实时推送：按 mentioned 分组，2 次 publishToUsersAfterCommit（替代原 N 次 publishToUserAfterCommit）
        // P4-9: 传 chatType="group" 以便 push 指标按聊天类型分维度统计
        if (!mentionedUserIds.isEmpty()) {
            String mentionedJson = ImEnvelopeHelper.imMessage(buildGroupPayload(msg, group, from, true));
            imRealtimePush.publishToUsersAfterCommit(mentionedUserIds, mentionedJson, "group");
        }
        if (!notMentionedUserIds.isEmpty()) {
            String notMentionedJson = ImEnvelopeHelper.imMessage(buildGroupPayload(msg, group, from, false));
            imRealtimePush.publishToUsersAfterCommit(notMentionedUserIds, notMentionedJson, "group");
        }
        // P4-2: 失效发送方与所有群成员的会话列表缓存（新消息进入会话 + 未读数变化）
        conversationCache.evictUser(fromId);
        conversationCache.evictUsers(mentionedUserIds);
        conversationCache.evictUsers(notMentionedUserIds);
        // P4-9: 消息发送计数（按聊天类型分维度）
        imMetrics.recordMessageSend("group");
        return R.ok(msg.getMessageId());
    }

    /**
     * P4-1: 构造群消息通知实体（不含 notificationId，由批量插入回填）。
     */
    private SysUserNotification buildGroupNotification(Long userId, String category, String refType,
                                                       String refId, String title, String content) {
        SysUserNotification n = new SysUserNotification();
        n.setUserId(userId);
        n.setCategory(category);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setTitle(title);
        n.setContent(content);
        n.setReadFlag("0");
        return n;
    }

    public TableDataInfo<ImMessage> groupHistory(Long groupId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        if (!support.isGroupMember(groupId, me)) {
            throw new ServiceException("无权查看该群消息");
        }
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    public TableDataInfo<Map<String, Object>> groupHistoryWithSender(Long groupId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        if (!support.isGroupMember(groupId, me)) {
            throw new ServiceException("无权查看该群消息");
        }
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return support.buildSenderMessagePage(page);
    }

    /**
     * P1-6: 标记群聊消息已读，并按发送者分组推送已读回执。
     * <p>P4-3: 改用水位线模式（watermarks）替代 messageId 列表。
     * <ul>
     *   <li>原实现：SELECT 全部未读 messageId（活跃群可能数千条），payload.messageIds 数组体积过大</li>
     *   <li>新实现：聚合 SQL 按 from_user_id 分组取 MAX(message_id)，每发送者一条水位线记录</li>
     *   <li>payload.watermark: {fromUserId, maxMessageId}，客户端据此把本地 ≤ maxMessageId 的消息标为已读</li>
     *   <li>兼容字段 messageIds: 仅包含 maxMessageId 单元素，保证旧客户端不报错（行为降级，建议尽快迁移）</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public int markGroupRead(Long groupId) {
        Long me = LoginHelper.getUserId();
        ImGroup group = imGroupMapper.selectById(groupId);
        if (group == null || !"0".equals(group.getStatus())) {
            return 0;
        }
        // P4-3: 聚合 SQL 取 watermarks（按 from_user_id 分组 MAX(message_id)）
        // 注：mapUnderscoreToCamelCase=true 会将列别名 "from_user_id" → "fromUserId"，"max_message_id" → "maxMessageId"
        List<Map<String, Object>> watermarkRows = imMessageMapper.selectMaps(new QueryWrapper<ImMessage>()
            .select("from_user_id", "MAX(message_id) AS max_message_id")
            .eq("group_id", groupId)
            .ne("from_user_id", me)
            .eq("is_read", "0")
            .groupBy("from_user_id"));
        if (watermarkRows.isEmpty()) {
            return 0;
        }
        ImMessage patch = new ImMessage();
        patch.setIsRead("1");
        int updated = imMessageMapper.update(patch, new LambdaUpdateWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .ne(ImMessage::getFromUserId, me)
            .eq(ImMessage::getIsRead, "0"));
        // P4-3: 按发送者分组推送 watermark（替代原 messageIds 大数组）
        pushGroupReadWatermark(groupId, me, watermarkRows);
        // P4-2: 失效当前用户的会话列表缓存（未读数变化）
        conversationCache.evict();
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public R<Long> createGroup(ImGroupCreateBo bo) {
        Long ownerId = LoginHelper.getUserId();
        LinkedHashSet<Long> ids = support.normalizeUserIds(bo.getMemberUserIds());
        ids.add(ownerId);
        if (ids.size() < 2) {
            return R.fail("群聊至少需要两名成员");
        }
        for (Long uid : ids) {
            support.requireSameTenantUser(uid);
        }
        ImGroup g = new ImGroup();
        g.setGroupName(support.normalizeGroupName(bo.getGroupName()));
        g.setAvatar(StrUtil.trimToNull(bo.getAvatar()));
        g.setOwnerUserId(ownerId);
        g.setStatus("0");
        imGroupMapper.insert(g);

        // P2-5: 批量插入群成员，替换原 for 循环逐条 insert
        // group_id 为新生成的雪花 ID，(group_id, user_id) 不可能冲突，无需 catch DuplicateKeyException
        Date now = new Date();
        List<ImGroupMember> members = new ArrayList<>(ids.size());
        for (Long uid : ids) {
            ImGroupMember m = new ImGroupMember();
            m.setGroupId(g.getGroupId());
            m.setUserId(uid);
            m.setJoinTime(now);
            members.add(m);
        }
        imGroupMemberMapper.insertBatch(members);
        // P4-2: 失效所有新成员的会话列表缓存（新增群聊会话）
        conversationCache.evictUsers(ids);
        return R.ok(g.getGroupId());
    }

    @Transactional(rollbackFor = Exception.class)
    public R<Void> addGroupMembers(Long groupId, ImGroupMemberAddBo bo) {
        Long operator = LoginHelper.getUserId();
        ImGroup g = imGroupMapper.selectById(groupId);
        if (g == null || !"0".equals(g.getStatus())) {
            return R.fail("群组不存在或已解散");
        }
        if (!g.getOwnerUserId().equals(operator)) {
            return R.fail("仅群主可添加成员");
        }
        LinkedHashSet<Long> ids = support.normalizeUserIds(bo.getMemberUserIds());
        ids.remove(operator);
        if (ids.isEmpty()) {
            return R.fail("请选择有效的新成员");
        }
        Date now = new Date();
        int newCount = 0;
        int existingCount = 0;
        // P2-4: 移除 TOCTOU selectCount 检查，依赖 uk_group_user 唯一索引 + catch DuplicateKeyException
        // 并发场景下两个请求同时通过 selectCount 检查 → 第二个 insert 触发唯一索引冲突 → 视为"已存在"跳过（幂等）
        for (Long uid : ids) {
            support.requireSameTenantUser(uid);
            ImGroupMember m = new ImGroupMember();
            m.setGroupId(groupId);
            m.setUserId(uid);
            m.setJoinTime(now);
            try {
                imGroupMemberMapper.insert(m);
                newCount++;
            } catch (DuplicateKeyException e) {
                // (group_id, user_id) 唯一索引命中，视为"已存在"跳过
                existingCount++;
                log.debug("addGroupMembers 跳过已存在成员: groupId={}, userId={}", groupId, uid);
            }
        }
        if (newCount == 0) {
            return R.ok("所有成员均已在群中，新增 0 条，已存在 " + existingCount + " 条");
        }
        // P4-2: 失效操作者与新加入成员的会话列表缓存（新增群聊会话）
        conversationCache.evictUser(operator);
        conversationCache.evictUsers(ids);
        return R.ok("新增 " + newCount + " 条，已存在 " + existingCount + " 条");
    }

    public List<ImGroupMemberVo> listGroupMembers(Long groupId) {
        Long me = LoginHelper.getUserId();
        ImGroup group = imGroupMapper.selectById(groupId);
        if (group == null || !"0".equals(group.getStatus())) {
            throw new ServiceException("群组不存在或已解散");
        }
        if (!support.isGroupMember(groupId, me)) {
            throw new ServiceException("无权查看该群成员");
        }
        List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .orderByAsc(ImGroupMember::getJoinTime));
        if (members.isEmpty()) {
            return new ArrayList<>();
        }

        // P2-6: 批量化加载用户与头像 URL（消除 N+1）
        // 原: 循环 sysUserService.selectUserById + support.resolveAvatarUrl, 50 成员 = 100+ SQL
        // 新: 1 次 batchLoadUsers + 1 次 batchResolveAvatarUrls = 2 SQL
        Set<Long> userIds = members.stream()
            .map(ImGroupMember::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysUserVo> userMap = support.batchLoadUsers(userIds);
        Set<Long> avatarIds = userMap.values().stream()
            .map(SysUserVo::getAvatar)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> avatarUrlMap = support.batchResolveAvatarUrls(avatarIds);

        List<ImGroupMemberVo> list = new ArrayList<>(members.size());
        for (ImGroupMember m : members) {
            SysUserVo u = m.getUserId() == null ? null : userMap.get(m.getUserId());
            ImGroupMemberVo vo = new ImGroupMemberVo();
            vo.setUserId(m.getUserId());
            vo.setUserName(u != null ? StrUtil.blankToDefault(u.getNickName(), u.getUserName()) : String.valueOf(m.getUserId()));
            Long avatarId = u != null ? u.getAvatar() : null;
            vo.setAvatar(avatarId == null ? "" : avatarUrlMap.getOrDefault(avatarId, ""));
            vo.setJoinTime(m.getJoinTime());
            vo.setOwner(group.getOwnerUserId() != null && group.getOwnerUserId().equals(m.getUserId()));
            list.add(vo);
        }
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public R<Void> removeMember(Long groupId, Long userId) {
        Long operator = LoginHelper.getUserId();
        ImGroup g = imGroupMapper.selectById(groupId);
        if (g == null || !"0".equals(g.getStatus())) {
            return R.fail("群组不存在或已解散");
        }
        if (!g.getOwnerUserId().equals(operator)) {
            return R.fail("仅群主可移除成员");
        }
        if (userId.equals(operator)) {
            return R.fail("群主不可移除自己");
        }
        int deleted = imGroupMemberMapper.delete(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .eq(ImGroupMember::getUserId, userId));
        if (deleted <= 0) {
            return R.fail("成员不存在");
        }
        // P4-2: 失效被移除成员的会话列表缓存（群聊会话消失）
        conversationCache.evictUser(userId);
        return R.ok();
    }

    /**
     * P1-5: 规整 @ 提及的用户 ID 列表
     * <p>校验所有被 @ 的用户均为该群成员（去除发送者自身）；
     * 若包含非群成员则抛出 ServiceException 拒绝发送，防止跨群越权 @。
     */
    private List<Long> normalizeMentionUserIds(List<Long> mentionUserIds, Long groupId, Long fromUserId) {
        if (mentionUserIds == null || mentionUserIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long uid : mentionUserIds) {
            if (uid == null || uid <= 0 || uid.equals(fromUserId)) {
                continue;
            }
            normalized.add(uid);
        }
        if (normalized.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        for (Long uid : normalized) {
            if (!support.isGroupMember(groupId, uid)) {
                throw new ServiceException("被 @ 的用户 " + uid + " 不是该群成员");
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * P1-5: 构建群消息推送 payload
     * @param mentioned 当前接收者是否被 @（用于客户端高亮提示）
     */
    private Map<String, Object> buildGroupPayload(ImMessage msg, ImGroup group, SysUserVo from, boolean mentioned) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("chatType", "GROUP");
        p.put("messageId", msg.getMessageId());
        p.put("groupId", msg.getGroupId());
        p.put("fromUserId", msg.getFromUserId());
        p.put("content", msg.getContent());
        p.put("msgType", msg.getMsgType());
        p.put("createTime", msg.getCreateTime());
        p.put("groupName", group.getGroupName());
        // P1-5: 携带 mentionUserIds（解析为 List<Long>）+ 当前接收者是否被 @ 标记
        if (StrUtil.isNotBlank(msg.getMentionUserIds())) {
            p.put("mentionUserIds", JsonUtils.parseArray(msg.getMentionUserIds(), Long.class));
        }
        p.put("mentioned", mentioned);
        if (from != null) {
            p.put("senderName", StrUtil.blankToDefault(from.getNickName(), from.getUserName()));
            p.put("senderAvatar", support.resolveAvatarUrl(from.getAvatar()));
        }
        return p;
    }

    /**
     * P4-3: 群聊已读回执推送（按发送者水位线）
     * <p>遍历每发送者的 watermark 记录，向其推送 IM_MESSAGE_READ 信封，仅含其自己的 maxMessageId。
     * <p>客户端收到后，把本地所有 messageId ≤ maxMessageId 且 fromUserId == watermark.fromUserId 的消息标为已读。
     */
    private void pushGroupReadWatermark(Long groupId, Long readBy, List<Map<String, Object>> watermarkRows) {
        long readAt = System.currentTimeMillis();
        for (Map<String, Object> row : watermarkRows) {
            // mapUnderscoreToCamelCase=true：from_user_id → fromUserId，max_message_id → maxMessageId
            Object fromObj = row.get("fromUserId");
            Object maxIdObj = row.get("maxMessageId");
            if (!(fromObj instanceof Number) || !(maxIdObj instanceof Number)) {
                continue;
            }
            Long fromUserId = ((Number) fromObj).longValue();
            Long maxMessageId = ((Number) maxIdObj).longValue();
            Map<String, Object> watermark = new LinkedHashMap<>();
            watermark.put("fromUserId", fromUserId);
            watermark.put("maxMessageId", maxMessageId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chatType", "GROUP");
            payload.put("groupId", groupId);
            payload.put("peerUserId", readBy);
            payload.put("readAt", readAt);
            payload.put("readBy", readBy);
            // P4-3: 水位线字段（新协议，客户端优先使用）
            payload.put("watermark", watermark);
            // 兼容字段：旧客户端仍读 messageIds，仅包含 maxMessageId 单元素
            // 注意：旧客户端只会标记 messageId == maxMessageId 这一条消息，UI 行为降级
            payload.put("messageIds", Collections.singletonList(maxMessageId));
            imRealtimePush.publishToUserAfterCommit(fromUserId, ImEnvelopeHelper.imMessageRead(payload));
        }
    }
}
