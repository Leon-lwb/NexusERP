package org.dromara.im.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.im.domain.SysUserNotification;
import org.dromara.im.service.ISysUserNotificationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户通知Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/im/notification")
public class SysUserNotificationController extends BaseController {

    private final ISysUserNotificationService sysUserNotificationService;

    @SaCheckLogin
    @GetMapping("/page")
    public TableDataInfo<SysUserNotification> page(PageQuery pageQuery) {
        return sysUserNotificationService.pageMyNotifications(pageQuery);
    }

    @SaCheckLogin
    @PutMapping("/read/{notificationId}")
    public R<Void> markRead(@PathVariable Long notificationId) {
        return toAjax(sysUserNotificationService.markRead(notificationId));
    }

    @SaCheckLogin
    @GetMapping("/unread/count")
    public R<Long> unreadCount() {
        return R.ok(sysUserNotificationService.countUnread());
    }
}
