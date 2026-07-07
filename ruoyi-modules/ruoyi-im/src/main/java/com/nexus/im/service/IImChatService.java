package com.nexus.im.service;

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

import java.util.List;
import java.util.Map;

/**
 * 即时通讯聊天Service接口
 *
 * @author wanghuan
 */
public interface IImChatService {

    R<Long> sendPrivate(ImPrivateSendBo bo);

    R<Long> sendGroup(ImGroupSendBo bo);

    TableDataInfo<ImMessage> privateHistory(Long peerUserId, PageQuery pageQuery);

    TableDataInfo<Map<String, Object>> privateHistoryWithSender(Long peerUserId, PageQuery pageQuery);

    TableDataInfo<ImMessage> groupHistory(Long groupId, PageQuery pageQuery);

    TableDataInfo<Map<String, Object>> groupHistoryWithSender(Long groupId, PageQuery pageQuery);

    List<ImConversationVo> listConversations();

    R<Long> createGroup(ImGroupCreateBo bo);

    R<Void> addGroupMembers(Long groupId, ImGroupMemberAddBo bo);

    int markPrivateRead(Long peerUserId);

    int markGroupRead(Long groupId);

    /**
     * 群成员列表（含用户名称/头像/是否群主）
     */
    List<ImGroupMemberVo> listGroupMembers(Long groupId);

    /**
     * 群主移除群成员
     */
    R<Void> removeMember(Long groupId, Long userId);

    /**
     * 撤回自己的消息（仅限发送者，2 分钟内）
     */
    R<Void> revokeMessage(Long messageId);

    /**
     * 将用户头像 OSS ID 解析为可直接访问的 URL（与消息历史中的 senderAvatar 保持一致）。
     * 用于 IM 登录/用户信息接口返回当前用户头像，避免前端拿到裸 OSS ID 拼出不存在的 /resource/oss/{id} 导致头像 404。
     *
     * @param avatarId 头像 OSS ID
     * @return 头像 URL，无则返回空串
     */
    String resolveAvatarUrl(Long avatarId);
}
