package com.nexus.im.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.constants.ImPayloadConstants;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.bo.ImGroupCreateBo;
import com.nexus.im.domain.bo.ImGroupMemberAddBo;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.domain.vo.ImGroupMemberVo;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.msgtype.ImMessageTypeHandler;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.IImChatService;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.service.ISysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 即时通讯聊天Service业务层处理
 *
 * @author wanghuan
 */
@Service
@RequiredArgsConstructor
public class ImChatServiceImpl implements IImChatService {

    private static final int MAX_GROUP_NAME_LENGTH = 50;
    /** 消息撤回时间窗（毫秒），超过则不允许撤回 */
    private static final long REVOKE_WINDOW_MS = 2 * 60 * 1000L;

    private final ImMessageMapper imMessageMapper;
    private final ImGroupMapper imGroupMapper;
    private final ImGroupMemberMapper imGroupMemberMapper;
    private final ISysUserService sysUserService;
    private final ISysOssService sysOssService;
    private final ISysUserNotificationService sysUserNotificationService;
    private final ImRealtimePush imRealtimePush;
    /** P1-2: 消息类型策略模式注册中心 */
    private final ImMessageTypeRegistry messageTypeRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Long> sendPrivate(ImPrivateSendBo bo) {
        Long fromId = LoginHelper.getUserId();
        if (fromId.equals(bo.getToUserId())) {
            return R.fail("不能给自己发私聊");
        }
        requireSameTenantUser(bo.getToUserId());
        SysUserVo from = sysUserService.selectUserById(fromId);
        // P1-2: 消息类型策略模式，替代硬编码 SUPPORTED_MSG_TYPES
        ImMessageTypeHandler handler = messageTypeRegistry.require(bo.getMsgType());
        String content = handler.normalize(bo.getContent());
        String msgType = handler.type();
        ImMessage msg = new ImMessage();
        msg.setFromUserId(fromId);
        msg.setToUserId(bo.getToUserId());
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setIsRead("0");
        msg.setRevoked("0");
        msg.setForwardFrom(bo.getForwardFrom());
        imMessageMapper.insert(msg);

        String title = "私聊 · " + (from != null ? StrUtil.blankToDefault(from.getNickName(), from.getUserName()) : "用户");
        sysUserNotificationService.addAndPush(
            bo.getToUserId(),
            ImPayloadConstants.CATEGORY_IM_PRIVATE,
            "im_message",
            String.valueOf(msg.getMessageId()),
            title,
            StrUtil.maxLength(content, 500),
            null);

        String json = ImEnvelopeHelper.imMessage(buildPrivatePayload(msg, from));
        // P0-2: 推送移到事务提交后，避免回滚时接收方收到"幽灵消息"
        imRealtimePush.publishToUserAfterCommit(bo.getToUserId(), json);
        return R.ok(msg.getMessageId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Long> sendGroup(ImGroupSendBo bo) {
        Long fromId = LoginHelper.getUserId();
        ImGroup group = imGroupMapper.selectById(bo.getGroupId());
        if (group == null || !"0".equals(group.getStatus())) {
            return R.fail("群组不存在或已解散");
        }
        if (!isGroupMember(bo.getGroupId(), fromId)) {
            return R.fail("您不是该群成员");
        }
        // P1-2: 消息类型策略模式，替代硬编码 SUPPORTED_MSG_TYPES
        ImMessageTypeHandler handler = messageTypeRegistry.require(bo.getMsgType());
        String content = handler.normalize(bo.getContent());
        String msgType = handler.type();
        ImMessage msg = new ImMessage();
        msg.setFromUserId(fromId);
        msg.setGroupId(bo.getGroupId());
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setIsRead("0");
        msg.setRevoked("0");
        msg.setForwardFrom(bo.getForwardFrom());
        imMessageMapper.insert(msg);

        SysUserVo from = sysUserService.selectUserById(fromId);
        String senderName = from != null ? StrUtil.blankToDefault(from.getNickName(), from.getUserName()) : "";

        List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, bo.getGroupId()));
        for (ImGroupMember m : members) {
            if (m.getUserId().equals(fromId)) {
                continue;
            }
            String title = "群聊 · " + group.getGroupName();
            sysUserNotificationService.addAndPush(
                m.getUserId(),
                ImPayloadConstants.CATEGORY_IM_GROUP,
                "im_message",
                String.valueOf(msg.getMessageId()),
                title,
                senderName + "：" + StrUtil.maxLength(content, 400),
                null);
            String json = ImEnvelopeHelper.imMessage(buildGroupPayload(msg, group, from));
            // P0-2: 推送移到事务提交后，避免回滚时接收方收到"幽灵消息"
            imRealtimePush.publishToUserAfterCommit(m.getUserId(), json);
        }
        return R.ok(msg.getMessageId());
    }

    @Override
    public TableDataInfo<ImMessage> privateHistory(Long peerUserId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        requireSameTenantUser(peerUserId);
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .isNull(ImMessage::getGroupId)
            .and(q -> q.and(a -> a.eq(ImMessage::getFromUserId, me).eq(ImMessage::getToUserId, peerUserId))
                .or(a -> a.eq(ImMessage::getFromUserId, peerUserId).eq(ImMessage::getToUserId, me)))
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<Map<String, Object>> privateHistoryWithSender(Long peerUserId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        requireSameTenantUser(peerUserId);
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .isNull(ImMessage::getGroupId)
            .and(q -> q.and(a -> a.eq(ImMessage::getFromUserId, me).eq(ImMessage::getToUserId, peerUserId))
                .or(a -> a.eq(ImMessage::getFromUserId, peerUserId).eq(ImMessage::getToUserId, me)))
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return buildSenderMessagePage(page);
    }

    @Override
    public TableDataInfo<ImMessage> groupHistory(Long groupId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        if (!isGroupMember(groupId, me)) {
            throw new ServiceException("无权查看该群消息");
        }
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<Map<String, Object>> groupHistoryWithSender(Long groupId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        if (!isGroupMember(groupId, me)) {
            throw new ServiceException("无权查看该群消息");
        }
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return buildSenderMessagePage(page);
    }

    @Override
    public List<ImConversationVo> listConversations() {
        Long me = LoginHelper.getUserId();
        List<ImConversationVo> list = new ArrayList<>();

        List<ImGroupMember> memberships = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getUserId, me));
        for (ImGroupMember gm : memberships) {
            ImGroup g = imGroupMapper.selectById(gm.getGroupId());
            if (g == null || !"0".equals(g.getStatus())) {
                continue;
            }
            ImMessage last = imMessageMapper.selectOne(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getGroupId, g.getGroupId())
                .orderByDesc(ImMessage::getCreateTime)
                .last("limit 1"));
            ImConversationVo vo = new ImConversationVo();
            vo.setChatType("GROUP");
            vo.setTargetId(String.valueOf(g.getGroupId()));
            vo.setTitle(g.getGroupName());
            vo.setAvatar(g.getAvatar());
            vo.setMemberAvatars(loadGroupMemberAvatars(g.getGroupId()));
            vo.setLastContent(last != null ? last.getContent() : "");
            vo.setLastTime(last != null ? last.getCreateTime() : g.getCreateTime());
            vo.setUnreadCount(countUnreadGroup(me, g.getGroupId()));
            list.add(vo);
        }

        List<ImMessage> recent = imMessageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
            .isNull(ImMessage::getGroupId)
            .and(w -> w.eq(ImMessage::getFromUserId, me).or().eq(ImMessage::getToUserId, me))
            .orderByDesc(ImMessage::getCreateTime)
            .last("limit 200"));

        Map<Long, ImMessage> latestByPeer = new LinkedHashMap<>();
        for (ImMessage m : recent) {
            Long peer = m.getFromUserId().equals(me) ? m.getToUserId() : m.getFromUserId();
            latestByPeer.putIfAbsent(peer, m);
        }
        for (Map.Entry<Long, ImMessage> e : latestByPeer.entrySet()) {
            Long peer = e.getKey();
            ImMessage last = e.getValue();
            SysUserVo u = sysUserService.selectUserById(peer);
            ImConversationVo vo = new ImConversationVo();
            vo.setChatType("PRIVATE");
            vo.setTargetId(String.valueOf(peer));
            vo.setTitle(u != null ? StrUtil.blankToDefault(u.getNickName(), u.getUserName()) : String.valueOf(peer));
            vo.setAvatar(resolveAvatarUrl(u != null ? u.getAvatar() : null));
            vo.setLastContent(last.getContent());
            vo.setLastTime(last.getCreateTime());
            vo.setUnreadCount(countUnreadPrivate(me, peer));
            list.add(vo);
        }

        list.sort(Comparator.comparing(ImConversationVo::getLastTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Long> createGroup(ImGroupCreateBo bo) {
        Long ownerId = LoginHelper.getUserId();
        LinkedHashSet<Long> ids = normalizeUserIds(bo.getMemberUserIds());
        ids.add(ownerId);
        if (ids.size() < 2) {
            return R.fail("群聊至少需要两名成员");
        }
        for (Long uid : ids) {
            requireSameTenantUser(uid);
        }
        ImGroup g = new ImGroup();
        g.setGroupName(normalizeGroupName(bo.getGroupName()));
        g.setAvatar(StrUtil.trimToNull(bo.getAvatar()));
        g.setOwnerUserId(ownerId);
        g.setStatus("0");
        imGroupMapper.insert(g);

        Date now = new Date();
        for (Long uid : ids) {
            ImGroupMember m = new ImGroupMember();
            m.setGroupId(g.getGroupId());
            m.setUserId(uid);
            m.setJoinTime(now);
            imGroupMemberMapper.insert(m);
        }
        return R.ok(g.getGroupId());
    }

    @Override
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
        LinkedHashSet<Long> ids = normalizeUserIds(bo.getMemberUserIds());
        ids.remove(operator);
        if (ids.isEmpty()) {
            return R.fail("请选择有效的新成员");
        }
        Date now = new Date();
        for (Long uid : ids) {
            requireSameTenantUser(uid);
            if (imGroupMemberMapper.selectCount(new LambdaQueryWrapper<ImGroupMember>()
                .eq(ImGroupMember::getGroupId, groupId).eq(ImGroupMember::getUserId, uid)) > 0) {
                continue;
            }
            ImGroupMember m = new ImGroupMember();
            m.setGroupId(groupId);
            m.setUserId(uid);
            m.setJoinTime(now);
            imGroupMemberMapper.insert(m);
        }
        return R.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markPrivateRead(Long peerUserId) {
        Long me = LoginHelper.getUserId();
        requireSameTenantUser(peerUserId);
        ImMessage patch = new ImMessage();
        patch.setIsRead("1");
        return imMessageMapper.update(patch, new LambdaUpdateWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getFromUserId, peerUserId)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markGroupRead(Long groupId) {
        Long me = LoginHelper.getUserId();
        ImGroup group = imGroupMapper.selectById(groupId);
        if (group == null || !"0".equals(group.getStatus())) {
            return 0;
        }
        ImMessage patch = new ImMessage();
        patch.setIsRead("1");
        return imMessageMapper.update(patch, new LambdaUpdateWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .ne(ImMessage::getFromUserId, me)
            .eq(ImMessage::getIsRead, "0"));
    }

    @Override
    public List<ImGroupMemberVo> listGroupMembers(Long groupId) {
        Long me = LoginHelper.getUserId();
        ImGroup group = imGroupMapper.selectById(groupId);
        if (group == null || !"0".equals(group.getStatus())) {
            throw new ServiceException("群组不存在或已解散");
        }
        if (!isGroupMember(groupId, me)) {
            throw new ServiceException("无权查看该群成员");
        }
        List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .orderByAsc(ImGroupMember::getJoinTime));
        List<ImGroupMemberVo> list = new ArrayList<>();
        for (ImGroupMember m : members) {
            SysUserVo u = sysUserService.selectUserById(m.getUserId());
            ImGroupMemberVo vo = new ImGroupMemberVo();
            vo.setUserId(m.getUserId());
            vo.setUserName(u != null ? StrUtil.blankToDefault(u.getNickName(), u.getUserName()) : String.valueOf(m.getUserId()));
            vo.setAvatar(resolveAvatarUrl(u != null ? u.getAvatar() : null));
            vo.setJoinTime(m.getJoinTime());
            vo.setOwner(group.getOwnerUserId() != null && group.getOwnerUserId().equals(m.getUserId()));
            list.add(vo);
        }
        return list;
    }

    @Override
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Void> revokeMessage(Long messageId) {
        Long me = LoginHelper.getUserId();
        ImMessage msg = imMessageMapper.selectById(messageId);
        if (msg == null) {
            return R.fail("消息不存在");
        }
        if (!me.equals(msg.getFromUserId())) {
            return R.fail("只能撤回自己的消息");
        }
        if (msg.getCreateTime() == null || System.currentTimeMillis() - msg.getCreateTime().getTime() > REVOKE_WINDOW_MS) {
            return R.fail("超过2分钟，无法撤回");
        }
        ImMessage patch = new ImMessage();
        patch.setMessageId(messageId);
        patch.setRevoked("1");
        imMessageMapper.updateById(patch);

        // 推送撤回事件给接收方（复用 IM_MESSAGE 信封，payload 带 action=revoke，前端按 action 处理）
        if (msg.getGroupId() == null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chatType", "PRIVATE");
            payload.put("messageId", messageId);
            payload.put("fromUserId", msg.getFromUserId());
            payload.put("toUserId", msg.getToUserId());
            payload.put("action", "revoke");
            // P0-2: 推送移到事务提交后，避免回滚时接收方收到撤回通知但实际未撤回
            imRealtimePush.publishToUserAfterCommit(msg.getToUserId(), ImEnvelopeHelper.imMessage(payload));
        } else {
            ImGroup group = imGroupMapper.selectById(msg.getGroupId());
            List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
                .eq(ImGroupMember::getGroupId, msg.getGroupId()));
            for (ImGroupMember m : members) {
                if (m.getUserId().equals(me)) {
                    continue;
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("chatType", "GROUP");
                payload.put("messageId", messageId);
                payload.put("groupId", msg.getGroupId());
                payload.put("fromUserId", msg.getFromUserId());
                payload.put("action", "revoke");
                if (group != null) {
                    payload.put("groupName", group.getGroupName());
                }
                // P0-2: 推送移到事务提交后，避免回滚时接收方收到撤回通知但实际未撤回
                imRealtimePush.publishToUserAfterCommit(m.getUserId(), ImEnvelopeHelper.imMessage(payload));
            }
        }
        return R.ok();
    }

    private long countUnreadGroup(Long me, Long groupId) {
        return imMessageMapper.selectCount(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .ne(ImMessage::getFromUserId, me)
            .eq(ImMessage::getIsRead, "0"));
    }

    private long countUnreadPrivate(Long me, Long peer) {
        return imMessageMapper.selectCount(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getFromUserId, peer)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId));
    }

    private TableDataInfo<Map<String, Object>> buildSenderMessagePage(Page<ImMessage> page) {
        List<Map<String, Object>> rows = page.getRecords().stream()
            .map(this::buildSenderMessageRow)
            .toList();
        return new TableDataInfo<>(rows, page.getTotal());
    }

    private Map<String, Object> buildSenderMessageRow(ImMessage msg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("messageId", msg.getMessageId());
        row.put("fromUserId", msg.getFromUserId());
        row.put("toUserId", msg.getToUserId());
        row.put("groupId", msg.getGroupId());
        row.put("content", msg.getContent());
        row.put("msgType", msg.getMsgType());
        row.put("messageType", msg.getMsgType());
        row.put("isRead", msg.getIsRead());
        row.put("revoked", msg.getRevoked());
        row.put("forwardFrom", msg.getForwardFrom());
        row.put("createTime", msg.getCreateTime());
        SysUserVo sender = msg.getFromUserId() == null ? null : sysUserService.selectUserById(msg.getFromUserId());
        if (sender != null) {
            row.put("senderId", sender.getUserId());
            row.put("senderName", StrUtil.blankToDefault(sender.getNickName(), sender.getUserName()));
            row.put("senderAvatar", resolveAvatarUrl(sender.getAvatar()));
        }
        return row;
    }

    private List<String> loadGroupMemberAvatars(Long groupId) {
        List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .orderByAsc(ImGroupMember::getJoinTime)
            .last("limit 4"));
        List<String> avatars = new ArrayList<>();
        for (ImGroupMember member : members) {
            SysUserVo user = sysUserService.selectUserById(member.getUserId());
            String avatar = resolveAvatarUrl(user != null ? user.getAvatar() : null);
            if (StrUtil.isNotBlank(avatar)) {
                avatars.add(avatar);
            }
        }
        return avatars;
    }

    @Override
    public String resolveAvatarUrl(Long avatarId) {
        if (avatarId == null) {
            return "";
        }
        try {
            List<SysOssVo> list = sysOssService.listByIds(List.of(avatarId));
            return list.isEmpty() || list.get(0) == null ? "" : StrUtil.blankToDefault(list.get(0).getUrl(), "");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isGroupMember(Long groupId, Long userId) {
        return imGroupMemberMapper.selectCount(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .eq(ImGroupMember::getUserId, userId)) > 0;
    }

    private SysUserVo requireSameTenantUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException("用户ID无效");
        }
        SysUserVo u = sysUserService.selectUserById(userId);
        if (u == null) {
            throw new ServiceException("用户不存在");
        }
        if (TenantHelper.isEnable() && !StringUtils.equals(TenantHelper.getTenantId(), u.getTenantId())) {
            throw new ServiceException("不可跨租户通讯");
        }
        return u;
    }

    private String normalizeGroupName(String groupName) {
        String value = StrUtil.trim(groupName);
        if (StrUtil.isBlank(value)) {
            throw new ServiceException("群名称不能为空");
        }
        if (value.length() > MAX_GROUP_NAME_LENGTH) {
            throw new ServiceException("群名称不能超过" + MAX_GROUP_NAME_LENGTH + "个字符");
        }
        return value;
    }

    private LinkedHashSet<Long> normalizeUserIds(List<Long> userIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long uid : userIds) {
                if (uid != null && uid > 0) {
                    ids.add(uid);
                }
            }
        }
        return ids;
    }

    private Map<String, Object> buildPrivatePayload(ImMessage msg, SysUserVo from) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("chatType", "PRIVATE");
        p.put("messageId", msg.getMessageId());
        p.put("fromUserId", msg.getFromUserId());
        p.put("toUserId", msg.getToUserId());
        p.put("content", msg.getContent());
        p.put("msgType", msg.getMsgType());
        p.put("createTime", msg.getCreateTime());
        if (from != null) {
            p.put("senderName", StrUtil.blankToDefault(from.getNickName(), from.getUserName()));
            p.put("senderAvatar", resolveAvatarUrl(from.getAvatar()));
        }
        return p;
    }

    private Map<String, Object> buildGroupPayload(ImMessage msg, ImGroup group, SysUserVo from) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("chatType", "GROUP");
        p.put("messageId", msg.getMessageId());
        p.put("groupId", msg.getGroupId());
        p.put("fromUserId", msg.getFromUserId());
        p.put("content", msg.getContent());
        p.put("msgType", msg.getMsgType());
        p.put("createTime", msg.getCreateTime());
        p.put("groupName", group.getGroupName());
        if (from != null) {
            p.put("senderName", StrUtil.blankToDefault(from.getNickName(), from.getUserName()));
            p.put("senderAvatar", resolveAvatarUrl(from.getAvatar()));
        }
        return p;
    }
}
