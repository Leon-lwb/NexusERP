package com.nexus.im.service.impl;

import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.bo.ImGroupCreateBo;
import com.nexus.im.domain.bo.ImGroupMemberAddBo;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.domain.vo.ImGroupMemberVo;
import com.nexus.im.service.IImChatService;
import com.nexus.im.service.impl.support.ImChatSupport;
import com.nexus.im.service.impl.support.ImConversationService;
import com.nexus.im.service.impl.support.ImGroupChatService;
import com.nexus.im.service.impl.support.ImMessageLifecycleService;
import com.nexus.im.service.impl.support.ImPrivateChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 即时通讯聊天Service业务层处理（门面）
 * <p>
 * P1-7: 重构为门面模式，所有方法委托到内部子 Service：
 * <ul>
 *   <li>{@link ImPrivateChatService} — 私聊消息发送/历史/已读</li>
 *   <li>{@link ImGroupChatService} — 群聊消息发送/历史/已读/群组管理</li>
 *   <li>{@link ImConversationService} — 会话列表聚合</li>
 *   <li>{@link ImMessageLifecycleService} — 消息撤回等生命周期</li>
 *   <li>{@link ImChatSupport} — 跨子 Service 共享辅助方法</li>
 * </ul>
 * <p>外部调用方（Controller）无感知，IImChatService 接口契约不变。
 *
 * @author wanghuan
 */
@Service
@RequiredArgsConstructor
public class ImChatServiceImpl implements IImChatService {

    /** 私聊子服务：私聊消息发送/历史/已读。 */
    private final ImPrivateChatService privateChatService;
    /** 群聊子服务：群消息发送/历史/已读/群组管理。 */
    private final ImGroupChatService groupChatService;
    /** 会话列表聚合服务。 */
    private final ImConversationService conversationService;
    /** 消息生命周期服务：撤回等。 */
    private final ImMessageLifecycleService messageLifecycleService;
    /** 跨子服务共享辅助能力（头像解析等）。 */
    private final ImChatSupport support;

    @Override
    public R<Long> sendPrivate(ImPrivateSendBo bo) {
        return privateChatService.sendPrivate(bo);
    }

    @Override
    public R<Long> sendGroup(ImGroupSendBo bo) {
        return groupChatService.sendGroup(bo);
    }

    @Override
    public TableDataInfo<ImMessage> privateHistory(Long peerUserId, PageQuery pageQuery) {
        return privateChatService.privateHistory(peerUserId, pageQuery);
    }

    @Override
    public TableDataInfo<Map<String, Object>> privateHistoryWithSender(Long peerUserId, PageQuery pageQuery) {
        return privateChatService.privateHistoryWithSender(peerUserId, pageQuery);
    }

    @Override
    public TableDataInfo<ImMessage> groupHistory(Long groupId, PageQuery pageQuery) {
        return groupChatService.groupHistory(groupId, pageQuery);
    }

    @Override
    public TableDataInfo<Map<String, Object>> groupHistoryWithSender(Long groupId, PageQuery pageQuery) {
        return groupChatService.groupHistoryWithSender(groupId, pageQuery);
    }

    @Override
    public List<ImConversationVo> listConversations() {
        return conversationService.listConversations();
    }

    @Override
    public R<Long> createGroup(ImGroupCreateBo bo) {
        return groupChatService.createGroup(bo);
    }

    @Override
    public R<Void> addGroupMembers(Long groupId, ImGroupMemberAddBo bo) {
        return groupChatService.addGroupMembers(groupId, bo);
    }

    @Override
    public int markPrivateRead(Long peerUserId) {
        return privateChatService.markPrivateRead(peerUserId);
    }

    @Override
    public int markGroupRead(Long groupId) {
        return groupChatService.markGroupRead(groupId);
    }

    @Override
    public List<ImGroupMemberVo> listGroupMembers(Long groupId) {
        return groupChatService.listGroupMembers(groupId);
    }

    @Override
    public R<Void> removeMember(Long groupId, Long userId) {
        return groupChatService.removeMember(groupId, userId);
    }

    @Override
    public R<Void> revokeMessage(Long messageId) {
        return messageLifecycleService.revokeMessage(messageId);
    }

    @Override
    public String resolveAvatarUrl(Long avatarId) {
        return support.resolveAvatarUrl(avatarId);
    }
}
