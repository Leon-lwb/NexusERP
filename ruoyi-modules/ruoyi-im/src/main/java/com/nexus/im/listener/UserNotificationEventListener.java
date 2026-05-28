package com.nexus.im.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.service.ISysUserNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 将 {@link UserNotificationEvent} 落库并实时推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationEventListener {

    private final ISysUserNotificationService sysUserNotificationService;

    @EventListener
    public void onUserNotification(UserNotificationEvent event) {
        try {
            if (StringUtils.isNotBlank(event.getTenantId())) {
                TenantHelper.dynamic(event.getTenantId(), () -> sysUserNotificationService.handleUserNotificationEvent(event));
            } else {
                sysUserNotificationService.handleUserNotificationEvent(event);
            }
        } catch (Exception e) {
            log.error("处理用户通知事件失败: {}", e.getMessage(), e);
        }
    }
}
