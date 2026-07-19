package com.nexus.im.service;

import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.im.domain.SysUserNotification;

import java.util.List;

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

    /**
     * P4-1: 批量写入用户通知并推送（群消息扇出优化）。
     * <p>一次 SQL 批量 INSERT 所有通知行，避免 N 次循环 insert；
     * 推送按 userId 逐条注册 afterCommit 钩子（每用户 envelope 不同，因 notificationId 不同）。
     *
     * @param rows 已填充字段的通知实体列表（userId/category/refType/refId/title/content/extJson）
     * @return 插入后的 notificationId 列表（顺序与入参一致）
     */
    List<Long> batchAddAndPush(List<SysUserNotification> rows);

    void handleUserNotificationEvent(UserNotificationEvent event);

    TableDataInfo<SysUserNotification> pageMyNotifications(PageQuery pageQuery);

    int markRead(Long notificationId);

    Long countUnread();
}
