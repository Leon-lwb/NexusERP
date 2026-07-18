package com.nexus.im.service.impl.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        for (ImGroupMember m : members) {
            if (m.getUserId().equals(fromId)) {
                continue;
            }
            boolean mentioned = mentionSet.contains(m.getUserId());
            // P1-5: 被 @ 成员的通知标题加 "@你" 前缀，便于客户端特殊提示
            String title = (mentioned ? "[@你] " : "") + "群聊 · " + group.getGroupName();
            sysUserNotificationService.addAndPush(
                m.getUserId(),
                ImPayloadConstants.CATEGORY_IM_GROUP,
                "im_message",
                String.valueOf(msg.getMessageId()),
                title,
                senderName + "：" + StrUtil.maxLength(content, 400),
                null);
            String json = ImEnvelopeHelper.imMessage(buildGroupPayload(msg, group, from, mentioned));
            // P0-2: 推送移到事务提交后，避免回滚时接收方收到"幽灵消息"
            imRealtimePush.publishToUserAfterCommit(m.getUserId(), json);
        }
        return R.ok(msg.getMessageId());
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
     */
    @Transactional(rollbackFor = Exception.class)
    public int markGroupRead(Long groupId) {
        Long me = LoginHelper.getUserId();
        ImGroup group = imGroupMapper.selectById(groupId);
        if (group == null || !"0".equals(group.getStatus())) {
            return 0;
        }
        // P1-6: 先查出待标记为已读的消息（按发送者分组），用于推送已读回执
        List<ImMessage> pending = imMessageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
            .select(ImMessage::getMessageId, ImMessage::getFromUserId)
            .eq(ImMessage::getGroupId, groupId)
            .ne(ImMessage::getFromUserId, me)
            .eq(ImMessage::getIsRead, "0"));
        if (pending.isEmpty()) {
            return 0;
        }
        ImMessage patch = new ImMessage();
        patch.setIsRead("1");
        int updated = imMessageMapper.update(patch, new LambdaUpdateWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .ne(ImMessage::getFromUserId, me)
            .eq(ImMessage::getIsRead, "0"));
        // P1-6: 按发送者分组推送已读回执（事务提交后）
        pushGroupReadReceipt(groupId, me, pending);
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
     * P1-6: 群聊已读回执推送（按发送者分组）
     * <p>遍历群内每个发送者，向其推送 IM_MESSAGE_READ 信封，仅包含自己发的 messageId。
     */
    private void pushGroupReadReceipt(Long groupId, Long readBy, List<ImMessage> pending) {
        Map<Long, List<Long>> bySender = new LinkedHashMap<>();
        for (ImMessage m : pending) {
            bySender.computeIfAbsent(m.getFromUserId(), k -> new ArrayList<>()).add(m.getMessageId());
        }
        long readAt = System.currentTimeMillis();
        for (Map.Entry<Long, List<Long>> e : bySender.entrySet()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chatType", "GROUP");
            payload.put("groupId", groupId);
            payload.put("peerUserId", readBy);
            payload.put("messageIds", e.getValue());
            payload.put("readAt", readAt);
            payload.put("readBy", readBy);
            imRealtimePush.publishToUserAfterCommit(e.getKey(), ImEnvelopeHelper.imMessageRead(payload));
        }
    }
}
