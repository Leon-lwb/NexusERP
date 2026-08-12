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
 * <p>提供当前登录用户的系统通知相关 REST 接口，包括通知分页查询、标记已读、未读数量统计。
 * 属于 IM（即时通讯）模块，所有接口均要求登录态（{@code @SaCheckLogin}）。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/im/notification")
public class SysUserNotificationController extends BaseController {

    /** 用户通知业务服务，提供通知分页、标记已读、未读计数等能力。 */
    private final ISysUserNotificationService sysUserNotificationService;

    /**
     * 分页查询当前登录用户的系统通知列表。
     *
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页结果，包含当前用户下的通知列表及分页元信息
     */
    @SaCheckLogin
    @GetMapping("/page")
    public TableDataInfo<SysUserNotification> page(PageQuery pageQuery) {
        return sysUserNotificationService.pageMyNotifications(pageQuery);
    }

    /**
     * 将指定通知标记为已读。
     *
     * @param notificationId 待标记已读的通知 ID
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckLogin
    @PutMapping("/read/{notificationId}")
    public R<Void> markRead(@PathVariable Long notificationId) {
        return toAjax(sysUserNotificationService.markRead(notificationId));
    }

    /**
     * 将当前登录用户的全部未读通知标记为已读（通知中心"全部已读"）。
     * <p>幂等操作：无未读通知时 UPDATE 影响 0 行也应视为成功，不能用 toAjax（0 行会返回 500）</p>
     *
     * @return 操作结果
     */
    @SaCheckLogin
    @PutMapping("/read/all")
    public R<Void> markAllRead() {
        sysUserNotificationService.markAllRead();
        return R.ok();
    }

    /**
     * 统计当前登录用户的未读通知数量。
     *
     * @return 未读通知数量
     */
    @SaCheckLogin
    @GetMapping("/unread/count")
    public R<Long> unreadCount() {
        return R.ok(sysUserNotificationService.countUnread());
    }
}
