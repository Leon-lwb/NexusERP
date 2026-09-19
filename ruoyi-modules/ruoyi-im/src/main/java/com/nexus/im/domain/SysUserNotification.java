package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 用户站内通知实体
 * <p>记录推送给指定用户的系统通知（如好友请求、工作流待办/通知、IM 消息提醒等），
 * 含分类、关联业务类型与 ID、标题正文、已读标记及扩展 JSON。
 * 属于 IM（即时通讯）模块，继承租户实体自动带租户字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_notification")
public class SysUserNotification extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通知 ID。 */
    @TableId("notification_id")
    private Long notificationId;

    /** 通知接收用户 ID。 */
    private Long userId;

    /** 业务分类，如 im_friend_request / workflow_todo / im_private 等。 */
    private String category;

    /** 关联业务类型（如 friend_request / message）。 */
    private String refType;

    /** 关联业务 ID（如好友请求发起方用户 ID、消息 ID）。 */
    private String refId;

    /** 通知标题。 */
    private String title;

    /** 通知正文内容。 */
    private String content;

    /** 跳转链接（如流程详情页地址，含 procInsId/taskId 参数）；无跳转目标时为 null。 */
    private String contentUrl;

    /** 是否已读：0 未读，1 已读。 */
    private String readFlag;

    /** 扩展信息 JSON，承载业务自定义字段（如好友请求状态、发送方信息）。 */
    private String extJson;
}
