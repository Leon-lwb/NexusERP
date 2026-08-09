package com.nexus.im.service;

import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.im.domain.SysUserNotification;

import java.util.List;

/**
 * 用户通知Service接口
 * <p>定义系统通知的写入、推送、查询与已读等能力契约，供 IM 好友请求、工作流等模块复用。
 * 实现类为 {@code SysUserNotificationServiceImpl}。</p>
 *
 * @author wanghuan
 */
public interface ISysUserNotificationService {

    /**
     * 写入一条用户通知并推送（IM 等模块复用）。
     *
     * @param userId   接收用户 ID
     * @param category 业务分类
     * @param refType  关联业务类型
     * @param refId    关联业务 ID
     * @param title    通知标题
     * @param content  通知正文
     * @param extJson  扩展信息 JSON
     * @return 写入的通知 ID
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

    /**
     * 处理用户通知事件：落库并实时推送。
     *
     * @param event 用户通知事件
     */
    void handleUserNotificationEvent(UserNotificationEvent event);

    /**
     * 分页查询当前登录用户的系统通知列表。
     *
     * @param pageQuery 分页查询参数
     * @return 通知分页结果
     */
    TableDataInfo<SysUserNotification> pageMyNotifications(PageQuery pageQuery);

    /**
     * 将指定通知标记为已读，返回受影响行数。
     *
     * @param notificationId 待标记已读的通知 ID
     * @return 受影响行数（0 或 1）
     */
    int markRead(Long notificationId);

    /**
     * 将当前登录用户的全部未读通知标记为已读。
     *
     * @return 受影响行数
     */
    int markAllRead();

    /**
     * 统计当前登录用户的未读通知数量。
     *
     * @return 未读通知数量
     */
    Long countUnread();
}
