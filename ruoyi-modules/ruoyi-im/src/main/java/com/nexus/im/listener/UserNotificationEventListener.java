package com.nexus.im.listener;

import lombok.RequiredArgsConstructor;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.exception.ServiceException;
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
        } catch (ServiceException e) {
            // 同步事件属于发布方事务，业务失败必须原样传播，不能提交缺失通知的业务记录。
            throw e;
        } catch (RuntimeException e) {
            // 保留原因供服务端排查，同时立即让发布方事务回滚，避免在提交阶段才报回滚异常。
            ServiceException failure = new ServiceException("通知保存失败，请稍后重试");
            failure.initCause(e);
            throw failure;
        }
    }
}
