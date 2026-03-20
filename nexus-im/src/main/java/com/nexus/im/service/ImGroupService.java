package com.nexus.im.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IM群组服务接口
 *
 * @author nexus
 */
public interface ImGroupService extends IService<ImGroup> {
    
    boolean isMember(String groupId, Long userId);
    
    LocalDateTime getJoinTime(String groupId, Long userId);
    
    List<ImGroupMember> getGroupMembers(String groupId);
}
