package org.dromara.system.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.CommonMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.CommonService;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 通用 公共服务实现
 *
 * @author nbacheng
 * @date 2023-09-21
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CommonServiceImpl extends ServiceImpl<CommonMapper, Object> implements CommonService {

    private final ISysUserService userService;

    @Override
    public void sendSysNotice(String message) {
        // TODO: 实现系统消息发送
        log.info("发送系统消息: {}", message);
    }

    @Override
    public void sendSysNotice(String fromUser, String toUser, String title, String msgContent, String setMsgCategory) {
        // TODO: 实现系统消息发送
        log.info("发送系统消息 fromUser: {}, toUser: {}, title: {}", fromUser, toUser, title);
    }

    @Override
    public String getBaseUrl() {
        // TODO: 获取流程发送消息基地址
        return "";
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
