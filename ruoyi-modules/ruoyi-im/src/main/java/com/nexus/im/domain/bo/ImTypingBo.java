package com.nexus.im.domain.bo;

import lombok.Data;

/**
 * 「正在输入」上报入参对象（BO）
 * <p>属于瞬时状态，不落库。私聊传 {@code toUserId}，群聊传 {@code groupId}；
 * 两者同时传递时以群聊为准。</p>
 */
@Data
public class ImTypingBo {

    /** 私聊接收方用户 ID（私聊场景必填）。 */
    private Long toUserId;

    /** 群组 ID（群聊场景必填）。 */
    private Long groupId;

    /**
     * 是否正在输入：true=开始输入，false=停止输入（清空输入框 / 已发出消息）。
     * <p>缺省 true，兼容只上报「正在输入」的简单客户端。
     */
    private Boolean typing = true;
}
