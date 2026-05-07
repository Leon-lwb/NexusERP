package org.dromara.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 用户站内通知
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_notification")
public class SysUserNotification extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("notification_id")
    private Long notificationId;

    private Long userId;

    private String category;

    private String refType;

    private String refId;

    private String title;

    private String content;

    private String readFlag;

    private String extJson;
}
