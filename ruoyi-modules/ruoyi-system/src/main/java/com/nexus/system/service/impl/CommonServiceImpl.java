package com.nexus.system.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.CommonMapper;
import com.nexus.system.service.CommonService;
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

    private final ISysUserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${flowable.message-base-url:/workflow/process/detail/}")
    private String messageBaseUrl;

    @Override
    public void sendSysNotice(String message) {
        // TODO: 实现系统消息发送
        log.info("发送系统消息: {}", message);
    }

    @Override
    public void sendSysNotice(String fromUser, String toUser, String title, String msgContent, String setMsgCategory) {
        sendSysNotice(fromUser, toUser, title, msgContent, setMsgCategory, null);
    }

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

    @Override
    public String getBaseUrl() {
        return messageBaseUrl;
    }

    @Override
    public LoginUser getLoginUser() {
        return LoginHelper.getLoginUser();
    }

    @Override
    public List<SysUserVo> getUserListByRoleId(String roleId) {
        // TODO: 根据角色获取用户列表
        return List.of();
    }

    @Override
    public SysUserVo getSysUserByUserId(Long userId) {
        return userService.selectUserById(userId);
    }

    @Override
    public SysUserVo getSysUserByUserName(String userName) {
        return userService.selectUserByUserName(userName);
    }

    @Override
    public String getDepLeaderByUserName(String userName) {
        // TODO: 根据用户名获取部门领导
        return "";
    }

    @Override
    public Long selectMaxId() {
        // TODO: 获取菜单的最大id
        return 0L;
    }

    @Override
    public List<SysUserVo> getAllUser() {
        // TODO: 获取所有用户列表
        return List.of();
    }
}
