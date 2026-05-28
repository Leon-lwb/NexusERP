package com.nexus.im.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.im.domain.SysUserNotification;
import com.nexus.im.mapper.SysUserNotificationMapper;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import com.nexus.im.service.ISysUserNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户通知Service业务层处理
 *
 * @author wanghuan
 */
@Service
@RequiredArgsConstructor
public class SysUserNotificationServiceImpl implements ISysUserNotificationService {

    private final SysUserNotificationMapper notificationMapper;
    private final ImRealtimePush imRealtimePush;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addAndPush(Long userId, String category, String refType, String refId, String title, String content, String extJson) {
        SysUserNotification row = new SysUserNotification();
        row.setUserId(userId);
        row.setCategory(category);
        row.setRefType(refType);
        row.setRefId(refId);
        row.setTitle(StrUtil.blankToDefault(title, "通知"));
        row.setContent(content);
        row.setReadFlag("0");
        row.setExtJson(extJson);
        notificationMapper.insert(row);
        String preview = StrUtil.maxLength(StrUtil.removeAll(content, "<[^>]+>"), 200);
        String json = ImEnvelopeHelper.userNotification(row.getNotificationId(), category, row.getTitle(), preview);
        imRealtimePush.publishToUser(userId, json);
        return row.getNotificationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleUserNotificationEvent(UserNotificationEvent event) {
        if (event.getTargetUserId() == null) {
            return;
        }
        SysUserNotification row = new SysUserNotification();
        row.setUserId(event.getTargetUserId());
        row.setCategory(event.getCategory());
        row.setRefType(event.getRefType());
        row.setRefId(event.getRefId());
        row.setTitle(StrUtil.blankToDefault(event.getTitle(), "通知"));
        row.setContent(event.getContent());
        row.setReadFlag("0");
        row.setExtJson(event.getExtJson());
        if (StrUtil.isNotBlank(event.getTenantId())) {
            row.setTenantId(event.getTenantId());
        }
        notificationMapper.insert(row);
        String preview = StrUtil.maxLength(StrUtil.removeAll(event.getContent(), "<[^>]+>"), 200);
        String json = ImEnvelopeHelper.userNotification(row.getNotificationId(), row.getCategory(), row.getTitle(), preview);
        imRealtimePush.publishToUser(event.getTargetUserId(), json);
    }

    @Override
    public TableDataInfo<SysUserNotification> pageMyNotifications(PageQuery pageQuery) {
        Long userId = LoginHelper.getUserId();
        LambdaQueryWrapper<SysUserNotification> w = new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, userId)
            .orderByDesc(SysUserNotification::getCreateTime);
        Page<SysUserNotification> page = notificationMapper.selectPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markRead(Long notificationId) {
        Long userId = LoginHelper.getUserId();
        SysUserNotification row = notificationMapper.selectById(notificationId);
        if (row == null || !userId.equals(row.getUserId())) {
            throw new ServiceException("通知不存在或无权操作");
        }
        row.setReadFlag("1");
        return notificationMapper.updateById(row);
    }

    @Override
    public Long countUnread() {
        Long userId = LoginHelper.getUserId();
        return notificationMapper.selectCount(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, userId)
            .eq(SysUserNotification::getReadFlag, "0"));
    }
}
