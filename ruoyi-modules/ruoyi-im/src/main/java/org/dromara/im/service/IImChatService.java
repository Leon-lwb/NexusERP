package org.dromara.im.service;

import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.im.domain.ImMessage;
import org.dromara.im.domain.bo.ImGroupCreateBo;
import org.dromara.im.domain.bo.ImGroupMemberAddBo;
import org.dromara.im.domain.bo.ImGroupSendBo;
import org.dromara.im.domain.bo.ImPrivateSendBo;
import org.dromara.im.domain.vo.ImConversationVo;

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
