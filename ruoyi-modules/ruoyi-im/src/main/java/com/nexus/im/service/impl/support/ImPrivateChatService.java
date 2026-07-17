package com.nexus.im.service.impl.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.im.constants.ImPayloadConstants;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.msgtype.ImMessageTypeHandler;
import com.nexus.im.msgtype.ImMessageTypeRegistry;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1-7: 私聊子 Service
 * <p>从 ImChatServiceImpl 拆分，负责私聊消息发送、历史、已读标记。
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class ImPrivateChatService {

    private final ImMessageMapper imMessageMapper;
    private final ISysUserService sysUserService;
    private final ISysUserNotificationService sysUserNotificationService;
    private final ImRealtimePush imRealtimePush;
    private final ImMessageTypeRegistry messageTypeRegistry;
    private final ImChatSupport support;

    @Transactional(rollbackFor = Exception.class)
    public R<Long> sendPrivate(ImPrivateSendBo bo) {
        Long fromId = LoginHelper.getUserId();
        if (fromId.equals(bo.getToUserId())) {
            return R.fail("不能给自己发私聊");
        }
        support.requireSameTenantUser(bo.getToUserId());
        // P1-4: client_msg_id 幂等检查 — 重发命中直接返回旧 messageId
        Long existingId = support.findExistingByClientMsgId(bo.getClientMsgId(), fromId, bo.getToUserId(), null);
        if (existingId != null) {
            return R.ok(existingId);
        }
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
        msg.setClientMsgId(StrUtil.trimToNull(bo.getClientMsgId()));
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

    public TableDataInfo<ImMessage> privateHistory(Long peerUserId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        support.requireSameTenantUser(peerUserId);
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .isNull(ImMessage::getGroupId)
            .and(q -> q.and(a -> a.eq(ImMessage::getFromUserId, me).eq(ImMessage::getToUserId, peerUserId))
                .or(a -> a.eq(ImMessage::getFromUserId, peerUserId).eq(ImMessage::getToUserId, me)))
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    public TableDataInfo<Map<String, Object>> privateHistoryWithSender(Long peerUserId, PageQuery pageQuery) {
        Long me = LoginHelper.getUserId();
        support.requireSameTenantUser(peerUserId);
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .isNull(ImMessage::getGroupId)
            .and(q -> q.and(a -> a.eq(ImMessage::getFromUserId, me).eq(ImMessage::getToUserId, peerUserId))
                .or(a -> a.eq(ImMessage::getFromUserId, peerUserId).eq(ImMessage::getToUserId, me)))
            .orderByDesc(ImMessage::getCreateTime);
        Page<ImMessage> page = imMessageMapper.selectPage(pageQuery.build(), w);
        return support.buildSenderMessagePage(page);
    }

    /**
     * P1-6: 标记私聊消息已读，并向原发送方推送已读回执。
     */
    @Transactional(rollbackFor = Exception.class)
    public int markPrivateRead(Long peerUserId) {
        Long me = LoginHelper.getUserId();
        support.requireSameTenantUser(peerUserId);
        // P1-6: 先查出待标记为已读的消息 ID，UPDATE 后用于推送给原发送方做已读回执
        List<ImMessage> pending = imMessageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
            .select(ImMessage::getMessageId)
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getFromUserId, peerUserId)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId));
        if (pending.isEmpty()) {
            return 0;
        }
        List<Long> messageIds = new ArrayList<>(pending.size());
        for (ImMessage m : pending) {
            messageIds.add(m.getMessageId());
        }
        ImMessage patch = new ImMessage();
        patch.setIsRead("1");
        int updated = imMessageMapper.update(patch, new LambdaUpdateWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getFromUserId, peerUserId)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId));
        // P1-6: 向原发送方推送已读回执（事务提交后）
        pushPrivateReadReceipt(peerUserId, me, messageIds);
        return updated;
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
            p.put("senderAvatar", support.resolveAvatarUrl(from.getAvatar()));
        }
        return p;
    }

    /**
     * P1-6: 私聊已读回执推送
     * <p>向原发送方推送 IM_MESSAGE_READ 信封，发送方客户端据此把对应 messageId 标为已读。
     */
    private void pushPrivateReadReceipt(Long peerUserId, Long readBy, List<Long> messageIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chatType", "PRIVATE");
        payload.put("peerUserId", readBy);
        payload.put("messageIds", messageIds);
        payload.put("readAt", System.currentTimeMillis());
        payload.put("readBy", readBy);
        imRealtimePush.publishToUserAfterCommit(peerUserId, ImEnvelopeHelper.imMessageRead(payload));
    }
}
