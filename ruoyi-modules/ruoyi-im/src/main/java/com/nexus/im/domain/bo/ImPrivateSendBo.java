package com.nexus.im.domain.bo;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImPrivateSendBo {

    @NotNull(message = "接收者不能为空")
    private Long toUserId;

    @NotBlank(message = "内容不能为空")
    private String content;

    /**
     * 消息类型：text / image / file / friend_request 等。
     * <p>APP 端历史字段名为 messageType，PC 端为 msgType；用 @JsonAlias 兼容 APP 入参，
     * 序列化输出仍为 msgType（不影响 PC）。
     */
    @JsonAlias("messageType")
    private String msgType = "text";

    private Long forwardFrom;

    /**
     * P1-4: 客户端消息ID，用于重发幂等
     * <p>客户端生成 UUID，重发复用同一值；为空时不做幂等检查。
     */
    private String clientMsgId;

    /** P6-B3: 引用的消息 ID（可选） */
    private Long replyMessageId;
}
