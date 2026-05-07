package org.dromara.im.service;

import org.dromara.common.core.event.UserNotificationEvent;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.im.domain.SysUserNotification;

/**
 * 用户通知Service接口
 *
 * @author wanghuan
 */
public interface ISysUserNotificationService {

    /**
     * 写入一条用户通知并推送（IM 等模块复用）
     */
    Long addAndPush(Long userId, String category, String refType, String refId, String title, String content, String extJson);

    void handleUserNotificationEvent(UserNotificationEvent event);

    TableDataInfo<SysUserNotification> pageMyNotifications(PageQuery pageQuery);

    int markRead(Long notificationId);

    Long countUnread();
}
