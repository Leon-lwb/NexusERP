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
 * <p>定义 IM 模块聊天核心能力的契约：私聊/群聊消息发送、历史查询、会话列表、
 * 群组管理、已读标记、消息撤回与头像解析等。实现类为 {@code ImChatServiceImpl}。</p>
 *
 * @author wanghuan
 */
public interface IImChatService {

    /**
     * 发送私聊消息。
     *
     * @param bo 私聊发送参数（接收方、内容、类型、引用、clientMsgId 等）
     * @return 发送成功后的消息 ID
     */
    R<Long> sendPrivate(ImPrivateSendBo bo);

    /**
     * 发送群聊消息。
     *
     * @param bo 群聊发送参数（群组、内容、类型、@ 提醒、引用、clientMsgId 等）
     * @return 发送成功后的消息 ID
     */
    R<Long> sendGroup(ImGroupSendBo bo);

    /**
     * 分页查询与指定用户的私聊历史消息。
     *
     * @param peerUserId 对端用户 ID
     * @param pageQuery  分页查询参数
     * @return 私聊消息分页结果
     */
    TableDataInfo<ImMessage> privateHistory(Long peerUserId, PageQuery pageQuery);

    /**
     * 分页查询与指定用户的私聊历史消息（每条附带发送者名称/头像）。
     *
     * @param peerUserId 对端用户 ID
     * @param pageQuery  分页查询参数
     * @return 带发送者信息的私聊消息分页结果
     */
    TableDataInfo<Map<String, Object>> privateHistoryWithSender(Long peerUserId, PageQuery pageQuery);

    /**
     * 分页查询指定群组的群聊历史消息。
     *
     * @param groupId   群组 ID
     * @param pageQuery 分页查询参数
     * @return 群聊消息分页结果
     */
    TableDataInfo<ImMessage> groupHistory(Long groupId, PageQuery pageQuery);

    /**
     * 分页查询指定群组的群聊历史消息（每条附带发送者名称/头像）。
     *
     * @param groupId   群组 ID
     * @param pageQuery 分页查询参数
     * @return 带发送者信息的群聊消息分页结果
     */
    TableDataInfo<Map<String, Object>> groupHistoryWithSender(Long groupId, PageQuery pageQuery);

    /**
     * 查询当前登录用户的会话列表（按最近消息时间排序，含未读数）。
     *
     * @return 会话视图列表
     */
    List<ImConversationVo> listConversations();

    /**
     * 创建群聊并把创建人设为群主，同时加入初始成员。
     *
     * @param bo 建群参数
     * @return 新建群组 ID
     */
    R<Long> createGroup(ImGroupCreateBo bo);

    /**
     * 向指定群组添加成员（群主操作）。
     *
     * @param groupId 群组 ID
     * @param bo      待添加成员参数
     * @return 操作结果
     */
    R<Void> addGroupMembers(Long groupId, ImGroupMemberAddBo bo);

    /**
     * 标记与指定用户的私聊消息为已读，返回被标记为已读的消息数。
     *
     * @param peerUserId 对端用户 ID
     * @return 被标记已读的消息条数
     */
    int markPrivateRead(Long peerUserId);

    /**
     * 标记指定群组的群聊消息为已读，返回被标记为已读的消息数。
     *
     * @param groupId 群组 ID
     * @return 被标记已读的消息条数
     */
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
