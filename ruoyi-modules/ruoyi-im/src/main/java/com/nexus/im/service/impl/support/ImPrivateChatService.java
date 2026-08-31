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
import com.nexus.im.push.ImMetrics;
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

    /** 消息表 Mapper。 */
    private final ImMessageMapper imMessageMapper;
    /** 系统用户服务。 */
    private final ISysUserService sysUserService;
    /** 用户通知服务（私聊消息提醒通知）。 */
    private final ISysUserNotificationService sysUserNotificationService;
    /** 实时推送组件。 */
    private final ImRealtimePush imRealtimePush;
    /** 消息类型注册中心（按类型获取处理器）。 */
    private final ImMessageTypeRegistry messageTypeRegistry;
    /** 共享辅助组件。 */
    private final ImChatSupport support;
    /** 会话列表 Redis 缓存。 */
    private final ImConversationCache conversationCache;
    /** 指标采集组件。 */
    private final ImMetrics imMetrics;

    /**
     * 发送私聊消息：校验接收方、幂等、内容规范化，插入消息后写通知并推送（提交后）。
     *
     * @param bo 私聊发送参数
     * @return 发送成功后的消息 ID
     */
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
        // P6-B3: 引用消息填充
        if (bo.getReplyMessageId() != null) {
            ImMessage origin = imMessageMapper.selectById(bo.getReplyMessageId());
            if (origin != null && !"1".equals(origin.getRevoked())) {
                msg.setReplyMessageId(bo.getReplyMessageId());
                // P6 M4 修复：image/file 等非 text 消息的 content 是原样透传 JSON，可能含 <script> 等 HTML 标签，
                // 统一调用 HtmlUtil.cleanHtmlTag 净化（与 TextMessageHandler.sanitize 路径一致），防止 XSS。
                String snippet = truncate(origin.getContent(), 200);
                snippet = cn.hutool.http.HtmlUtil.cleanHtmlTag(snippet);
                msg.setReplySnippet(snippet);
            }
        }
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
        // P4-9: 传 chatType="private" 以便 push 指标按聊天类型分维度统计
        imRealtimePush.publishToUserAfterCommit(bo.getToUserId(), json, "private");
        // P4-2: 失效发送方与接收方的会话列表缓存（新消息进入会话）
        conversationCache.evictUser(fromId);
        conversationCache.evictUser(bo.getToUserId());
        // P4-9: 消息发送计数（按聊天类型分维度）
        imMetrics.recordMessageSend("private");
        return R.ok(msg.getMessageId());
    }

    /**
     * 分页查询与指定用户的私聊历史消息（校验同租户权限）。
     *
     * @param peerUserId 对端用户 ID
     * @param pageQuery  分页查询参数
     * @return 私聊消息分页结果
     */
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

    /**
     * 分页查询与指定用户的私聊历史消息（每条附带发送者信息）。
     *
     * @param peerUserId 对端用户 ID
     * @param pageQuery  分页查询参数
     * @return 带发送者信息的私聊消息分页结果
     */
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
        // P4-2: 失效当前用户的会话列表缓存（未读数变化）
        conversationCache.evict();
        return updated;
    }

    /**
     * 构建私聊消息推送 payload（含收发方、内容、发送者名称/头像）。
     *
     * @param msg  消息实体
     * @param from 发送者用户视图
     * @return 私聊消息信封 payload
     */
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

    /**
     * P6-B3: 字符串截断辅助
     */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
