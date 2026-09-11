package com.nexus.im.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 站内推送与 IM 载荷常量
 * <p>定义实时推送信封（envelope）的版本号、信封类型（type）以及业务分类（category），
 * 供 {@code ImEnvelopeHelper} 与各推送通道统一构造与识别推送消息。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImPayloadConstants {

    /** 推送信封协议版本号，当前为 1。 */
    public static final int ENVELOPE_VERSION = 1;

    /** 信封类型：系统用户通知（USER_NOTIFICATION）。 */
    public static final String TYPE_USER_NOTIFICATION = "USER_NOTIFICATION";
    /** 信封类型：IM 聊天消息（IM_MESSAGE）。 */
    public static final String TYPE_IM_MESSAGE = "IM_MESSAGE";
    /**
     * P1-6: 已读回执信封类型。接收方 markRead 后向消息原发送方推送，
     * 发送方客户端据此把对应消息标为已读。
     */
    public static final String TYPE_IM_MESSAGE_READ = "IM_MESSAGE_READ";
    /**
     * 「正在输入」信封类型（IM_TYPING）。瞬时状态提示，不落库、不产生消息记录，
     * 由 {@code POST /im/app/message/typing} 触发后向对端（私聊）或群内其他成员（群聊）转发。
     */
    public static final String TYPE_IM_TYPING = "IM_TYPING";

    /** 业务分类：工作流待办。 */
    public static final String CATEGORY_WORKFLOW_TODO = "workflow_todo";
    /** 业务分类：工作流通知。 */
    public static final String CATEGORY_WORKFLOW_NOTICE = "workflow_notice";
    /** 业务分类：IM 私聊消息。 */
    public static final String CATEGORY_IM_PRIVATE = "im_private";
    /** 业务分类：IM 群聊消息。 */
    public static final String CATEGORY_IM_GROUP = "im_group";
    /** 业务分类：系统消息。 */
    public static final String CATEGORY_SYSTEM = "system";
}
