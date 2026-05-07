package org.dromara.im.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.im.constants.ImPayloadConstants;
import org.dromara.im.domain.ImGroup;
import org.dromara.im.domain.ImGroupMember;
import org.dromara.im.domain.ImMessage;
import org.dromara.im.domain.bo.ImGroupCreateBo;
import org.dromara.im.domain.bo.ImGroupMemberAddBo;
import org.dromara.im.domain.bo.ImGroupSendBo;
import org.dromara.im.domain.bo.ImPrivateSendBo;
import org.dromara.im.domain.vo.ImConversationVo;
import org.dromara.im.mapper.ImGroupMapper;
import org.dromara.im.mapper.ImGroupMemberMapper;
import org.dromara.im.mapper.ImMessageMapper;
import org.dromara.im.push.ImEnvelopeHelper;
import org.dromara.im.push.ImRealtimePush;
import org.dromara.im.service.IImChatService;
import org.dromara.im.service.ISysUserNotificationService;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
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

    private static final Set<String> SUPPORTED_MSG_TYPES = Set.of("text", "image", "file");
    private static final int MAX_TEXT_CONTENT_LENGTH = 2000;
    private static final int MAX_GROUP_NAME_LENGTH = 50;

    private final ImMessageMapper imMessageMapper;
    private final ImGroupMapper imGroupMapper;
    private final ImGroupMemberMapper imGroupMemberMapper;
    private final ISysUserService sysUserService;
    private final ISysUserNotificationService sysUserNotificationService;
    private final ImRealtimePush imRealtimePush;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Long> sendPrivate(ImPrivateSendBo bo) {
        Long fromId = LoginHelper.getUserId();
        if (fromId.equals(bo.getToUserId())) {
            return R.fail("不能给自己发私聊");
        }
        requireSameTenantUser(bo.getToUserId());
        SysUserVo from = sysUserService.selectUserById(fromId);
        String content = normalizeContent(bo.getContent());
        String msgType = normalizeMsgType(bo.getMsgType());
        ImMessage msg = new ImMessage();
        msg.setFromUserId(fromId);
        msg.setToUserId(bo.getToUserId());
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setIsRead("0");
        msg.setRevoked("0");
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
        imRealtimePush.publishToUser(bo.getToUserId(), json);
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
        String content = normalizeContent(bo.getContent());
        String msgType = normalizeMsgType(bo.getMsgType());
        ImMessage msg = new ImMessage();
        msg.setFromUserId(fromId);
        msg.setGroupId(bo.getGroupId());
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setIsRead("0");
        msg.setRevoked("0");
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
            imRealtimePush.publishToUser(m.getUserId(), json);
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
            vo.setLastContent(last != null ? last.getContent() : "");
            vo.setLastTime(last != null ? last.getCreateTime() : g.getCreateTime());
            vo.setUnreadCount(0L);
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

    private long countUnreadPrivate(Long me, Long peer) {
        return imMessageMapper.selectCount(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getFromUserId, peer)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId));
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

    private String normalizeContent(String content) {
        String value = StrUtil.trim(content);
        if (StrUtil.isBlank(value)) {
            throw new ServiceException("消息内容不能为空");
        }
        if (value.length() > MAX_TEXT_CONTENT_LENGTH) {
            throw new ServiceException("消息内容不能超过" + MAX_TEXT_CONTENT_LENGTH + "个字符");
        }
        return value;
    }

    private String normalizeMsgType(String msgType) {
        String value = StrUtil.blankToDefault(StrUtil.trim(msgType), "text").toLowerCase();
        if (!SUPPORTED_MSG_TYPES.contains(value)) {
            throw new ServiceException("不支持的消息类型");
        }
        return value;
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
            p.put("senderAvatar", from.getAvatar());
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
            p.put("senderAvatar", from.getAvatar());
        }
        return p;
    }
}
