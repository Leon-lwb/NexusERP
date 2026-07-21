package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * IM 消息实体
 * <p>记录私聊/群聊消息，包括发送方、接收方/群组、内容、消息类型、已读与撤回状态、
 * 转发来源、@ 提醒、引用等。属于 IM（即时通讯）模块，继承租户实体自动带租户字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_message")
public class ImMessage extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息 ID（雪花 ID）。 */
    @TableId("message_id")
    private Long messageId;

    /** 发送方用户 ID。 */
    private Long fromUserId;

    /** 私聊接收方用户 ID（群聊消息为 null）。 */
    private Long toUserId;

    /** 所属群组 ID（私聊消息为 null）。 */
    private Long groupId;

    /** 消息内容，文本或图片/文件等的承载 JSON。 */
    private String content;

    /** 消息类型：text / image / file / friend_request 等。 */
    private String msgType;

    /** 是否已读：0 未读，1 已读。 */
    private String isRead;

    /** 是否已撤回：0 未撤回，1 已撤回。 */
    private String revoked;

    /** 转发来源消息 ID（非转发消息为 null）。 */
    private Long forwardFrom;

    /**
     * P1-4: 客户端消息ID，用于重发幂等
     * <p>客户端生成 UUID，重发复用同一值；后端按 (tenant_id, client_msg_id) 唯一索引去重。
     */
    private String clientMsgId;

    /**
     * P1-5: 被 @ 的用户 ID 列表（JSON 数组字符串，如 "[101,102,103]"）
     * <p>仅群聊消息可能携带；私聊为 null。
     */
    private String mentionUserIds;

    /** P6-B3: 引用的消息 ID（reply 类型消息使用） */
    private Long replyMessageId;

    /** P6-B3: 引用消息内容摘要（避免前端再次查询原消息） */
    private String replySnippet;
}
