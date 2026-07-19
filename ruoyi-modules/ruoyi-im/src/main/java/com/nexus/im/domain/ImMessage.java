package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_message")
public class ImMessage extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("message_id")
    private Long messageId;

    private Long fromUserId;

    private Long toUserId;

    private Long groupId;

    private String content;

    private String msgType;

    private String isRead;

    private String revoked;

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
