package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.UserConstants;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.ObjectUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.system.domain.SysNotice;
import com.nexus.system.domain.SysUser;
import com.nexus.system.domain.bo.SysNoticeBo;
import com.nexus.system.domain.vo.SysNoticeVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysNoticeMapper;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysNoticeService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 公告 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysNoticeServiceImpl implements ISysNoticeService {

    /** SysNoticeMapper 数据访问层组件，提供 通知公告 的持久化能力。 */
    private final SysNoticeMapper baseMapper;
    /** SysUserMapper 数据访问层组件，提供 用户 的持久化能力。 */
    private final SysUserMapper userMapper;
    /** 用户通知事件发布器，复用 IM 模块的通知落库与提交后推送能力。 */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询通知公告列表
     *
     * @param notice    查询条件
     * @param pageQuery 分页参数
     * @return 通知公告分页列表
     */
    @Override
    public TableDataInfo<SysNoticeVo> selectPageNoticeList(SysNoticeBo notice, PageQuery pageQuery) {
        LambdaQueryWrapper<SysNotice> lqw = buildQueryWrapper(notice);
        Page<SysNoticeVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询公告信息
     *
     * @param noticeId 公告ID
     * @return 公告信息
     */
    @Override
    public SysNoticeVo selectNoticeById(Long noticeId) {
        return baseMapper.selectVoById(noticeId);
    }

    /**
     * 查询公告列表
     *
     * @param notice 公告信息
     * @return 公告集合
     */
    @Override
    public List<SysNoticeVo> selectNoticeList(SysNoticeBo notice) {
        LambdaQueryWrapper<SysNotice> lqw = buildQueryWrapper(notice);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<SysNotice> buildQueryWrapper(SysNoticeBo bo) {
        LambdaQueryWrapper<SysNotice> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getNoticeTitle()), SysNotice::getNoticeTitle, bo.getNoticeTitle());
        lqw.eq(StringUtils.isNotBlank(bo.getNoticeType()), SysNotice::getNoticeType, bo.getNoticeType());
        if (StringUtils.isNotBlank(bo.getCreateByName())) {
            SysUserVo sysUser = userMapper.selectVoOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, bo.getCreateByName()));
            lqw.eq(SysNotice::getCreateBy, ObjectUtils.notNullGetter(sysUser, SysUserVo::getUserId));
        }
        lqw.orderByAsc(SysNotice::getNoticeId);
        return lqw;
    }

    /**
     * 新增公告
     *
     * @param bo 公告信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertNotice(SysNoticeBo bo) {
        SysNotice notice = MapstructUtils.convert(bo, SysNotice.class);
        int rows = baseMapper.insert(notice);
        if (rows > 0) {
            // 租户由持久化层写入，必须读取实际保存值，不能用当前登录租户或全局默认值猜测。
            publishNoticeNotifications(baseMapper.selectById(notice.getNoticeId()));
        }
        return rows;
    }

    /**
     * 修改公告
     *
     * @param bo 公告信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateNotice(SysNoticeBo bo) {
        if (bo.getNoticeId() == null) {
            return 0;
        }
        // 锁定发布状态，避免两个编辑请求同时把关闭公告发布后各发送一轮通知。
        SysNotice previous = baseMapper.selectOne(new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getNoticeId, bo.getNoticeId())
            .last("FOR UPDATE"));
        if (previous == null) {
            return 0;
        }
        SysNotice notice = MapstructUtils.convert(bo, SysNotice.class);
        int rows = baseMapper.updateById(notice);
        if (rows > 0 && !"0".equals(previous.getStatus())) {
            // 只有从非启用状态变为启用才通知；普通内容编辑和重复保存均不补发。
            publishNoticeNotifications(baseMapper.selectById(previous.getNoticeId()));
        }
        return rows;
    }

    /**
     * 向公告所属租户的启用用户发布通知事件，关闭公告不产生通知。
     * 事件在公告事务内发布，现有 IM 监听器同事务落库，并在提交后推送。
     */
    private void publishNoticeNotifications(SysNotice notice) {
        if (notice == null) {
            throw new ServiceException("公告保存后读取失败，请重试");
        }
        if (!"0".equals(notice.getStatus())) {
            return;
        }
        if (StringUtils.isBlank(notice.getTenantId())) {
            throw new ServiceException("公告租户信息缺失，无法发布通知");
        }
        // 显式限定租户、账号状态和删除标志，即使租户拦截器未启用也不能扩大接收范围。
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getUserId)
            .eq(SysUser::getTenantId, notice.getTenantId())
            .eq(SysUser::getStatus, UserConstants.USER_NORMAL)
            .eq(SysUser::getDelFlag, "0"));
        String noticeId = notice.getNoticeId().toString();
        String extJson = JsonUtils.toJsonString(Map.of("noticeId", noticeId));
        for (SysUser user : users) {
            UserNotificationEvent event = new UserNotificationEvent();
            event.setTenantId(notice.getTenantId());
            event.setTargetUserId(user.getUserId());
            event.setCategory("sys_notice");
            event.setRefType("sys_notice");
            event.setRefId(noticeId);
            event.setTitle(notice.getNoticeTitle());
            event.setContent(notice.getNoticeContent());
            // 公告 ID 保持字符串，供 APP 通知点击和冷启动跳转恢复使用。
            event.setExtJson(extJson);
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 删除公告对象
     *
     * @param noticeId 公告ID
     * @return 结果
     */
    @Override
    public int deleteNoticeById(Long noticeId) {
        return baseMapper.deleteById(noticeId);
    }

    /**
     * 批量删除公告信息
     *
     * @param noticeIds 需要删除的公告ID
     * @return 结果
     */
    @Override
    public int deleteNoticeByIds(Long[] noticeIds) {
        return baseMapper.deleteByIds(Arrays.asList(noticeIds));
    }
}
