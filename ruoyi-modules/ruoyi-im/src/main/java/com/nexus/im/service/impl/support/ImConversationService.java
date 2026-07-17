package com.nexus.im.service.impl.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1-7: 会话子 Service
 * <p>从 ImChatServiceImpl 拆分，负责会话列表聚合（私聊 + 群聊）。
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class ImConversationService {

    private final ImMessageMapper imMessageMapper;
    private final ImGroupMapper imGroupMapper;
    private final ImGroupMemberMapper imGroupMemberMapper;
    private final ISysUserService sysUserService;
    private final ImChatSupport support;

    /**
     * 列出当前用户的所有会话（群聊 + 私聊），按最后消息时间倒序。
     */
    public List<ImConversationVo> listConversations() {
        Long me = LoginHelper.getUserId();
        List<ImConversationVo> list = new ArrayList<>();

        // 群聊会话
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
            vo.setMemberAvatars(support.loadGroupMemberAvatars(g.getGroupId()));
            vo.setLastContent(last != null ? last.getContent() : "");
            vo.setLastTime(last != null ? last.getCreateTime() : g.getCreateTime());
            vo.setUnreadCount(support.countUnreadGroup(me, g.getGroupId()));
            list.add(vo);
        }

        // 私聊会话
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
            vo.setAvatar(support.resolveAvatarUrl(u != null ? u.getAvatar() : null));
            vo.setLastContent(last.getContent());
            vo.setLastTime(last.getCreateTime());
            vo.setUnreadCount(support.countUnreadPrivate(me, peer));
            list.add(vo);
        }

        list.sort(Comparator.comparing(ImConversationVo::getLastTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return list;
    }
}
