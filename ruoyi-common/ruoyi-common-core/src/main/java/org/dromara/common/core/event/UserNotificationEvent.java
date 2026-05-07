package org.dromara.common.core.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 站内用户通知事件（由 system 等模块发布，im 模块监听落库并推送）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private Long targetUserId;
    private String category;
    private String refType;
    private String refId;
    private String title;
    private String content;
    private String extJson;
}
