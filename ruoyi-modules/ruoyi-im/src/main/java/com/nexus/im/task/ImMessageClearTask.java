package com.nexus.im.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.mapper.SysUserNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

/**
 * IM 消息清理任务
 * <p>
 * 定期清理超过保留期的消息和通知，
 * 防止 im_message / sys_user_notification 无限增长拖慢历史查询。
 * <p>
 * 清理策略:
 *   - im_message: 保留最近 180 天
 *   - sys_user_notification: 普通通知已读保留 30 天，未读保留 90 天；群消息通知保留 180 天
 * <p>
 * 清理是物理删除，不可恢复；清理跨所有租户，通过 TenantHelper.ignore 绕过多租户拦截器。
 * 单批上限 BATCH_SIZE 避免长事务锁表。
 * <p>
 * P4-10 B7: 调度频率由"每天凌晨 4 点"改为"每小时整点"，将单次大事务拆为 N 次小事务，
 * 降低锁持有时间，避免凌晨 4 点集中清理导致的 IO 峰值与从库延迟。
 * 保留期不变（180/30/90 天），每小时仅清理"刚过期"的少量数据（稳态下约 1 小时增量）。
 *
 * @author nexus
 * @see com.nexus.im.mapper.ImMessageMapper
 * @see com.nexus.im.mapper.SysUserNotificationMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImMessageClearTask {

    /** 消息保留天数（默认 180 天） */
    private static final int MESSAGE_RETENTION_DAYS = 180;
    /** 已读通知保留天数（默认 30 天） */
    private static final int NOTIFICATION_READ_RETENTION_DAYS = 30;
    /** 未读通知保留天数（默认 90 天） */
    private static final int NOTIFICATION_UNREAD_RETENTION_DAYS = 90;
    /** 单批删除上限，避免长事务锁表 */
    private static final int BATCH_SIZE = 1000;
    /**
     * 单次任务最大循环次数，防止意外场景下无限循环。
     * <p>P4-10 B7: 改为每小时执行后，稳态下每小时仅清理少量数据，循环次数远低于上限；
     * 上限作为兜底，防止首次启用或积压场景下长时间占用调度线程。
     */
    private static final int MAX_BATCH_LOOP = 1000;

    /** 消息表 Mapper。 */
    private final ImMessageMapper messageMapper;
    /** 用户通知表 Mapper。 */
    private final SysUserNotificationMapper notificationMapper;

    /**
     * P4-10 B7: 每小时整点执行清理（原每天凌晨 4 点）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void clearExpiredMessages() {
        Date messageThreshold = daysAgo(MESSAGE_RETENTION_DAYS);
        Date readThreshold = daysAgo(NOTIFICATION_READ_RETENTION_DAYS);
        Date unreadThreshold = daysAgo(NOTIFICATION_UNREAD_RETENTION_DAYS);

        log.info("[ImMessageClearTask] 开始清理: message<{}d, notificationRead<{}d, notificationUnread<{}d",
            MESSAGE_RETENTION_DAYS, NOTIFICATION_READ_RETENTION_DAYS, NOTIFICATION_UNREAD_RETENTION_DAYS);

        // 跨租户清理
        long messageDeleted = TenantHelper.ignore(() -> deleteMessagesBatch(messageThreshold));
        long readDeleted = TenantHelper.ignore(() -> deleteNotificationsBatch(readThreshold, true));
        long unreadDeleted = TenantHelper.ignore(() -> deleteNotificationsBatch(unreadThreshold, false));

        log.info("[ImMessageClearTask] 清理完成: im_message={} rows, sys_user_notification(read={})={} rows, sys_user_notification(unread={})={} rows",
            messageDeleted, readThreshold, readDeleted, unreadThreshold, unreadDeleted);
    }

    /**
     * 分批删除过期消息，返回总删除行数
     */
    private long deleteMessagesBatch(Date threshold) {
        long total = 0;
        for (int i = 0; i < MAX_BATCH_LOOP; i++) {
            int deleted = messageMapper.delete(new LambdaQueryWrapper<ImMessage>()
                .lt(ImMessage::getCreateTime, threshold)
                .last("LIMIT " + BATCH_SIZE));
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    /**
     * 分批删除过期通知
     * @param threshold 时间阈值
     * @param read 是否清理已读通知（true=已读，false=未读）
     */
    private long deleteNotificationsBatch(Date threshold, boolean read) {
        long total = 0;
        String readFlag = read ? "1" : "0";
        for (int i = 0; i < MAX_BATCH_LOOP; i++) {
            int deleted = notificationMapper.delete(new LambdaQueryWrapper<SysUserNotification>()
                .eq(SysUserNotification::getReadFlag, readFlag)
                .and(w -> w.and(ordinary -> ordinary.ne(SysUserNotification::getCategory, "im_group")
                        .lt(SysUserNotification::getCreateTime, threshold))
                    .or(group -> group.eq(SysUserNotification::getCategory, "im_group")
                        .lt(SysUserNotification::getCreateTime, daysAgo(MESSAGE_RETENTION_DAYS))))
                .last("LIMIT " + BATCH_SIZE));
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    private static Date daysAgo(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -days);
        return c.getTime();
    }
}
