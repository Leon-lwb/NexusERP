package com.nexus.im.service.impl;

import cn.hutool.core.collection.CollUtil;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        String json = ImEnvelopeHelper.userNotification(row.getNotificationId(), category, row.getTitle(), preview, null);
        // P0-2: 推送移到事务提交后，避免回滚时接收方收到"幽灵通知"
        imRealtimePush.publishToUserAfterCommit(userId, json);
        return row.getNotificationId();
    }

    /**
     * P4-1: 批量写入用户通知并推送。
     * <p>一次 SQL 批量 INSERT（BaseMapperPlus.insertBatch，默认 1000 条/批），
     * 替代原 N 次循环 insert；推送按 userId 逐条注册 afterCommit（每用户 envelope 因 notificationId 不同）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchAddAndPush(List<SysUserNotification> rows) {
        if (CollUtil.isEmpty(rows)) {
            return Collections.emptyList();
        }
        // 规整字段：title 默认值、readFlag 默认 "0"
        for (SysUserNotification row : rows) {
            if (StrUtil.isBlank(row.getTitle())) {
                row.setTitle("通知");
            }
            if (StrUtil.isBlank(row.getReadFlag())) {
                row.setReadFlag("0");
            }
        }
        // 一次 SQL 批量插入
        notificationMapper.insertBatch(rows);
        // 逐条注册 afterCommit 推送（每用户 envelope 含不同 notificationId，无法合并）
        List<Long> ids = new ArrayList<>(rows.size());
        for (SysUserNotification row : rows) {
            String preview = StrUtil.maxLength(StrUtil.removeAll(row.getContent(), "<[^>]+>"), 200);
            String json = ImEnvelopeHelper.userNotification(
                row.getNotificationId(), row.getCategory(), row.getTitle(), preview, null);
            imRealtimePush.publishToUserAfterCommit(row.getUserId(), json);
            ids.add(row.getNotificationId());
        }
        return ids;
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
        String json = ImEnvelopeHelper.userNotification(row.getNotificationId(), row.getCategory(), row.getTitle(), preview, event.getContentUrl());
        // P0-2: 推送移到事务提交后，避免回滚时接收方收到"幽灵通知"
        imRealtimePush.publishToUserAfterCommit(event.getTargetUserId(), json);
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
