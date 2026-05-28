package com.nexus.im.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.service.ISysUserNotificationService;
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
