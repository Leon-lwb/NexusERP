package com.nexus.im.push;

import cn.hutool.core.map.MapBuilder;
import cn.hutool.core.map.MapUtil;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.im.constants.ImPayloadConstants;

import java.util.Map;

/**
 * 推送信封构造助手
 * <p>统一封装实时推送消息的 JSON 信封（含协议版本、类型、时间戳与业务 payload），
 * 供 {@code ImRealtimePush} 与通知服务构造 WS/SSE 下行载荷。</p>
 */
public final class ImEnvelopeHelper {

    private ImEnvelopeHelper() {
    }

    /**
     * 构造系统用户通知信封。
     *
     * @param notificationId  通知 ID
     * @param category        业务分类（如 im_friend_request / workflow_todo）
     * @param title           通知标题
     * @param contentPreview  内容预览摘要
     * @param contentUrl      内容详情 URL（可为 null，不输出该字段）
     * @param refType         关联业务类型（可为 null，前端据此拼跳转；不输出该字段）
     * @param refId           关联业务 ID（可为 null）
     * @return 信封 JSON 字符串
     */
    public static String userNotification(long notificationId, String category, String title, String contentPreview, String contentUrl,
                                          String refType, String refId) {
        MapBuilder<String, Object> builder = MapUtil.<String, Object>builder()
            // 雪花 ID 转字符串（JS 精度丢失坑：信封不走全局 BigNumberSerializer，long 直出会被截断）
            .put("notificationId", String.valueOf(notificationId))
            .put("category", category)
            .put("title", title)
            .put("contentPreview", contentPreview);
        if (contentUrl != null) {
            builder.put("contentUrl", contentUrl);
        }
        if (refType != null) {
            builder.put("refType", refType);
            builder.put("refId", refId);
        }
        return envelope(ImPayloadConstants.TYPE_USER_NOTIFICATION, builder.build());
    }

    /**
     * 构造 IM 聊天消息信封。
     *
     * @param payload 消息业务载荷（如 chatType、messageId、收发方、content 等）
     * @return 信封 JSON 字符串
     */
    public static String imMessage(Map<String, Object> payload) {
        return envelope(ImPayloadConstants.TYPE_IM_MESSAGE, payload);
    }

    /**
     * P1-6: 已读回执信封。payload 字段：
     * <ul>
     *   <li>chatType: PRIVATE / GROUP</li>
     *   <li>peerUserId: 读取消息的用户 ID（即 readBy）</li>
     *   <li>groupId: 群聊时携带，私聊为 null</li>
     *   <li>messageIds: 被标记为已读的消息 ID 列表</li>
     *   <li>readAt: 读取时间戳（毫秒）</li>
     *   <li>readBy: 读取消息的用户 ID（与 peerUserId 相同，便于客户端统一处理）</li>
     * </ul>
     */
    public static String imMessageRead(Map<String, Object> payload) {
        return envelope(ImPayloadConstants.TYPE_IM_MESSAGE_READ, payload);
    }

    /**
     * 「正在输入」信封。瞬时状态提示，客户端收到后展示「对方正在输入…」并在超时后自动消失。
     * <p>payload 字段：
     * <ul>
     *   <li>chatType: PRIVATE / GROUP</li>
     *   <li>fromUserId: 输入方用户 ID（字符串，避免 JS 精度丢失）</li>
     *   <li>fromUserName: 输入方昵称（群聊展示用）</li>
     *   <li>peerUserId: 私聊时的接收方用户 ID；群聊不输出</li>
     *   <li>groupId: 群聊时的群 ID；私聊不输出</li>
     *   <li>typing: true 表示正在输入，false 表示停止输入</li>
     * </ul>
     */
    public static String imTyping(Map<String, Object> payload) {
        return envelope(ImPayloadConstants.TYPE_IM_TYPING, payload);
    }

    private static String envelope(String type, Map<String, Object> payload) {
        Map<String, Object> root = MapUtil.<String, Object>builder()
            .put("v", ImPayloadConstants.ENVELOPE_VERSION)
            .put("type", type)
            .put("ts", System.currentTimeMillis())
            .put("payload", payload)
            .build();
        return JsonUtils.toJsonString(root);
    }
}
