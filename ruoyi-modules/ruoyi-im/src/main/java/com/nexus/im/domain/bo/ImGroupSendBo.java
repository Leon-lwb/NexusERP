package com.nexus.im.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ImGroupSendBo {

    @NotNull(message = "群ID不能为空")
    private Long groupId;

    @NotBlank(message = "内容不能为空")
    private String content;

    private String msgType = "text";

    private Long forwardFrom;

    /**
     * P1-4: 客户端消息ID，用于重发幂等
     * <p>客户端生成 UUID，重发复用同一值；为空时不做幂等检查。
     */
    private String clientMsgId;

    /**
     * P1-5: 被 @ 的用户 ID 列表
     * <p>仅群聊消息使用；后端会校验所有 userId 均为该群成员，否则拒绝发送。
     * <p>为空或 null 表示无 @。
     */
    private List<Long> mentionUserIds;

    /** P6-B3: 引用的消息 ID（可选） */
    private Long replyMessageId;
}
