package com.nexus.im.core.service.impl;

import com.nexus.common.utils.StringUtils;
import com.nexus.im.core.service.ImUserService;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IM用户服务实现
 * 直接使用系统用户，只管理IM相关字段
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImUserServiceImpl implements ImUserService {
    
    private final ISysUserService sysUserService;
    
    @Override
    public SysUser getSysUser(Long userId) {
        return sysUserService.selectUserById(userId);
    }
    
    @Override
    public void updateUserStatus(Long userId, String status) {
        log.info("更新用户IM状态: {} -> {}", userId, status);
    }
    
    @Override
    public List<SysUser> getOnlineUsers() {
        SysUser searchUser = new SysUser();
        searchUser.setStatus("0");
        
        List<SysUser> sysUsers = sysUserService.selectUserList(searchUser);
        return sysUsers.stream()
                .filter(user -> "0".equals(user.getStatus()))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<SysUser> searchUsers(String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return List.of();
        }
        
        SysUser searchUser = new SysUser();
        searchUser.setUserName(keyword);
        
        List<SysUser> sysUsers = sysUserService.selectUserList(searchUser);
        return sysUsers.stream()
                .filter(user -> "0".equals(user.getStatus()))
                .collect(Collectors.toList());
    }
    
    @Override
    public void updateLastOnlineTime(Long userId) {
        log.info("更新用户最后在线时间: {}", userId);
    }
    
    @Override
    public void updateImUserInfo(Long userId, String imSignature, String imGender, LocalDateTime imBirthday, String imRegion) {
        log.info("更新IM用户信息: userId={}", userId);
    }
    
    @Override
    public String getUserImStatus(Long userId) {
        return "offline";
    }
}
