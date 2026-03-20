package com.nexus.im.service;

import com.nexus.common.core.domain.entity.SysUser;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IM用户服务接口
 * 直接使用系统用户，只管理IM相关字段
 *
 * @author nexus
 */
public interface ImUserService {
    
    SysUser getSysUser(Long userId);
    
    void updateUserStatus(Long userId, String status);
    
    List<SysUser> getOnlineUsers();
    
    List<SysUser> searchUsers(String keyword);
    
    void updateLastOnlineTime(Long userId);
    
    void updateImUserInfo(Long userId, String imSignature, String imGender, LocalDateTime imBirthday, String imRegion);
    
    String getUserImStatus(Long userId);
}
