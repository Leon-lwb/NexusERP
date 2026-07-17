package com.nexus.im.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 站内推送与 IM 载荷常量
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImPayloadConstants {

    public static final int ENVELOPE_VERSION = 1;

    public static final String TYPE_USER_NOTIFICATION = "USER_NOTIFICATION";
    public static final String TYPE_IM_MESSAGE = "IM_MESSAGE";
    /**
     * P1-6: 已读回执信封类型。接收方 markRead 后向消息原发送方推送，
     * 发送方客户端据此把对应消息标为已读。
     */
    public static final String TYPE_IM_MESSAGE_READ = "IM_MESSAGE_READ";

    public static final String CATEGORY_WORKFLOW_TODO = "workflow_todo";
    public static final String CATEGORY_WORKFLOW_NOTICE = "workflow_notice";
    public static final String CATEGORY_IM_PRIVATE = "im_private";
    public static final String CATEGORY_IM_GROUP = "im_group";
    public static final String CATEGORY_SYSTEM = "system";
}
