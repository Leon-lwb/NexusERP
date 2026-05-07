package org.dromara.im.constants;

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

    public static final String CATEGORY_WORKFLOW_TODO = "workflow_todo";
    public static final String CATEGORY_WORKFLOW_NOTICE = "workflow_notice";
    public static final String CATEGORY_IM_PRIVATE = "im_private";
    public static final String CATEGORY_IM_GROUP = "im_group";
    public static final String CATEGORY_SYSTEM = "system";
}
