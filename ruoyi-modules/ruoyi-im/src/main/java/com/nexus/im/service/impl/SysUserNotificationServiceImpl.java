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
 * <p>实现通知写入、批量扇出、事件落库、分页查询、已读标记与未读计数等能力，
 * 属于 IM（即时通讯）模块。推送统一在事务提交后下发，避免回滚产生"幽灵通知"。</p>
 *
 * @author wanghuan
 */
@Service
@RequiredArgsConstructor
public class SysUserNotificationServiceImpl implements ISysUserNotificationService {

    /** 通知表 Mapper。 */
    private final SysUserNotificationMapper notificationMapper;
    /** 实时推送组件，用于 afterCommit 下发通知信封。 */
    private final ImRealtimePush imRealtimePush;

    /**
     * 写入一条用户通知并在事务提交后推送。
     *
     * @param userId   接收用户 ID
     * @param category 业务分类
     * @param refType  关联业务类型
     * @param refId    关联业务 ID
     * @param title    通知标题（空时默认"通知"）
     * @param content  通知正文
     * @param extJson  扩展信息 JSON
     * @return 写入的通知 ID
     */
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
        String json = ImEnvelopeHelper.userNotification(row.getNotificationId(), category, row.getTitle(), preview, null,
            row.getRefType(), row.getRefId());
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
                row.getNotificationId(), row.getCategory(), row.getTitle(), preview, null,
                row.getRefType(), row.getRefId());
            imRealtimePush.publishToUserAfterCommit(row.getUserId(), json);
            ids.add(row.getNotificationId());
        }
        return ids;
    }

    /**
     * 处理用户通知事件：落库并在事务提交后推送。
     *
     * @param event 用户通知事件
     */
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
        // 跳转链接必须落库：只随实时信封下发的话，刷新后从 /page 读回的通知就丢了跳转目标，
        // 消息盒子点击只能弹详情窗（流程待办/结束通知无法跳转到审批详情）
        row.setContentUrl(event.getContentUrl());
        row.setReadFlag("0");
        row.setExtJson(event.getExtJson());
        if (StrUtil.isNotBlank(event.getTenantId())) {
            row.setTenantId(event.getTenantId());
        }
        notificationMapper.insert(row);
        String preview = StrUtil.maxLength(StrUtil.removeAll(event.getContent(), "<[^>]+>"), 200);
        String json = ImEnvelopeHelper.userNotification(row.getNotificationId(), row.getCategory(), row.getTitle(), preview,
            event.getContentUrl(), row.getRefType(), row.getRefId());
        // P0-2: 推送移到事务提交后，避免回滚时接收方收到"幽灵通知"
        imRealtimePush.publishToUserAfterCommit(event.getTargetUserId(), json);
    }

    /**
     * 分页查询当前登录用户的系统通知列表（按创建时间倒序）。
     *
     * @param pageQuery 分页查询参数
     * @return 通知分页结果
     */
    @Override
    public TableDataInfo<SysUserNotification> pageMyNotifications(PageQuery pageQuery) {
        Long userId = LoginHelper.getUserId();
        LambdaQueryWrapper<SysUserNotification> w = new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, userId)
            // 未读在前（readFlag: 0未读 < 1已读）+ 时间倒序（对齐主流消息中心）
            .orderByAsc(SysUserNotification::getReadFlag)
            .orderByDesc(SysUserNotification::getCreateTime);
        Page<SysUserNotification> page = notificationMapper.selectPage(pageQuery.build(), w);
        return TableDataInfo.build(page);
    }

    /**
     * 将指定通知标记为已读（校验归属，非本人通知抛异常）。
     *
     * @param notificationId 待标记已读的通知 ID
     * @return 受影响行数（0 或 1）
     */
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

    /**
     * 统计当前登录用户的未读通知数量。
     *
     * @return 未读通知数量
     */
    @Override
    public Long countUnread() {
        Long userId = LoginHelper.getUserId();
        return notificationMapper.selectCount(new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, userId)
            .eq(SysUserNotification::getReadFlag, "0"));
    }

    /**
     * 将当前登录用户的全部未读通知标记为已读。
     *
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead() {
        Long userId = LoginHelper.getUserId();
        SysUserNotification update = new SysUserNotification();
        update.setReadFlag("1");
        return notificationMapper.update(update, new LambdaQueryWrapper<SysUserNotification>()
            .eq(SysUserNotification::getUserId, userId)
            .eq(SysUserNotification::getReadFlag, "0"));
    }
}
