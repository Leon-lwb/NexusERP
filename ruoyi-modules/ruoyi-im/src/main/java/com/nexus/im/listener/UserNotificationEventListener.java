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
 * 用户通知事件监听器
 * <p>监听 {@link UserNotificationEvent}，在指定租户上下文下将其落库并实时推送。
 * 属于 IM（即时通讯）模块，作为通知的统一入口，解耦业务方与通知服务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationEventListener {

    /** 用户通知业务服务，负责事件落库与推送。 */
    private final ISysUserNotificationService sysUserNotificationService;

    /**
     * 处理用户通知事件：按事件携带的租户切换上下文后交给业务服务落库并推送。
     *
     * @param event 用户通知事件（含接收方、分类、内容、租户等信息）
     */
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
