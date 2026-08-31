package com.nexus.system.service.impl;

import java.util.List;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.vo.SysDeptVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.CommonMapper;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.CommonService;
import com.nexus.system.service.ISysDeptService;
import com.nexus.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 通用 公共服务实现
 *
 * @author wanhuan
 * @date 2023-09-21
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CommonServiceImpl extends ServiceImpl<CommonMapper, Object> implements CommonService {

    /** ISysUserService 业务服务，提供 用户 相关能力。 */
    private final ISysUserService userService;
    /** ISysDeptService 业务服务，提供 部门 相关能力。 */
    private final ISysDeptService deptService;
    /** SysUserMapper 数据访问层组件，提供 用户 的持久化能力。 */
    private final SysUserMapper userMapper;
    /** eventPublisher 成员变量。 */
    private final ApplicationEventPublisher eventPublisher;

    @Value("${flowable.message-base-url:/workflow/process/detail/}")
    /** messageBaseUrl 成员变量。 */
    private String messageBaseUrl;

    /**
     * 处理记录相关操作。
     * @param message message 参数
     */
    @Override
    public void sendSysNotice(String message) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null || StringUtils.isBlank(loginUser.getUsername())) {
            log.info("发送系统消息(无登录用户): {}", message);
            return;
        }
        sendSysNotice("system", loginUser.getUsername(), message, message, Constants.MSG_CATEGORY_2, null);
    }

    /**
     * 处理记录相关操作。
     * @param fromUser fromUser 参数
     * @param toUser toUser 参数
     * @param title title 参数
     * @param msgContent msgContent 参数
     * @param setMsgCategory setMsgCategory 参数
     */
    @Override
    public void sendSysNotice(String fromUser, String toUser, String title, String msgContent, String setMsgCategory) {
        sendSysNotice(fromUser, toUser, title, msgContent, setMsgCategory, null);
    }

    /**
     * 处理记录相关操作。
     * @param fromUser fromUser 参数
     * @param toUser toUser 参数
     * @param title title 参数
     * @param msgContent msgContent 参数
     * @param setMsgCategory setMsgCategory 参数
     * @param contentUrl contentUrl 参数
     */
    @Override
    public void sendSysNotice(String fromUser, String toUser, String title, String msgContent, String setMsgCategory, String contentUrl) {
        log.info("发送系统消息 fromUser: {}, toUser: {}, title: {}", fromUser, toUser, title);
        if (StringUtils.isBlank(toUser)) {
            return;
        }
        SysUserVo target = userService.selectUserByUserName(toUser);
        if (target == null) {
            log.warn("接收用户不存在: {}", toUser);
            return;
        }
        String tenantId = TenantHelper.getTenantId();
        String category = "system";
        if (Constants.MSG_CATEGORY_3.equals(setMsgCategory)) {
            category = "workflow_todo";
        } else if (Constants.MSG_CATEGORY_1.equals(setMsgCategory)) {
            category = "workflow_notice";
        }
        UserNotificationEvent event = new UserNotificationEvent();
        event.setTenantId(tenantId);
        event.setTargetUserId(target.getUserId());
        event.setCategory(category);
        event.setRefType("workflow");
        event.setTitle(title);
        event.setContent(msgContent);
        event.setContentUrl(contentUrl);
        eventPublisher.publishEvent(event);
    }

    /**
     * 根据条件查询单个记录。
     * @return 结果字符串
     */
    @Override
    public String getBaseUrl() {
        return messageBaseUrl;
    }

    /**
     * 根据条件查询单个用户。
     * @return 用户对象
     */
    @Override
    public LoginUser getLoginUser() {
        return LoginHelper.getLoginUser();
    }

    /**
     * 根据条件查询单个用户。
     * @param roleId 角色 ID
     * @return 匹配条件的用户列表
     */
    @Override
    public List<SysUserVo> getUserListByRoleId(String roleId) {
        if (StringUtils.isBlank(roleId)) {
            return List.of();
        }
        Long rid;
        try {
            rid = Long.parseLong(roleId);
        } catch (NumberFormatException e) {
            log.warn("根据角色获取用户列表失败, 角色ID格式错误: {}", roleId);
            return List.of();
        }
        List<Long> userIds = baseMapper.selectUserIdByRoleId(rid);
        if (CollUtil.isEmpty(userIds)) {
            return List.of();
        }
        return userService.selectUserByIds(userIds, null);
    }

    /**
     * 根据条件查询单个用户。
     * @param userId 用户 ID
     * @return 用户对象
     */
    @Override
    public SysUserVo getSysUserByUserId(Long userId) {
        return userService.selectUserById(userId);
    }

    /**
     * 根据条件查询单个用户。
     * @param userName 用户名
     * @return 用户对象
     */
    @Override
    public SysUserVo getSysUserByUserName(String userName) {
        return userService.selectUserByUserName(userName);
    }

    /**
     * 根据条件查询单个记录。
     * @param userName 用户名
     * @return 结果字符串
     */
    @Override
    public String getDepLeaderByUserName(String userName) {
        if (StringUtils.isBlank(userName)) {
            return "";
        }
        SysUserVo user = userService.selectUserByUserName(userName);
        if (user == null || user.getDeptId() == null) {
            return "";
        }
        SysDeptVo dept = deptService.selectDeptById(user.getDeptId());
        if (dept == null || dept.getLeader() == null) {
            return "";
        }
        SysUserVo leader = userService.selectUserById(dept.getLeader());
        return leader == null ? "" : leader.getUserName();
    }

    /**
     * 根据条件查询记录列表。
     * @return 受影响行数
     */
    @Override
    public Long selectMaxId() {
        Long maxId = baseMapper.selectMaxMenuId();
        return maxId == null ? 0L : maxId;
    }

    /**
     * 根据条件查询单个用户。
     * @return 匹配条件的用户列表
     */
    @Override
    public List<SysUserVo> getAllUser() {
        return userMapper.selectVoList();
    }
}
