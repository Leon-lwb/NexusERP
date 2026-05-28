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
}
