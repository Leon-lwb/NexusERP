package com.nexus.im.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.im.core.ImGroup;
import com.nexus.im.core.ImGroupMember;
import com.nexus.im.core.mapper.ImGroupMapper;
import com.nexus.im.core.service.ImGroupService;
import com.nexus.im.core.service.ImGroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IM群组服务实现类
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class ImGroupServiceImpl extends ServiceImpl<ImGroupMapper, ImGroup> implements ImGroupService {
    
    private final ImGroupMemberService imGroupMemberService;
    
    @Override
    public boolean isMember(String groupId, Long userId) {
        LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImGroupMember::getGroupId, groupId)
               .eq(ImGroupMember::getUserId, userId)
               .eq(ImGroupMember::getEnabled, 1);
        
        return imGroupMemberService.count(wrapper) > 0;
    }
    
    @Override
    public LocalDateTime getJoinTime(String groupId, Long userId) {
        LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImGroupMember::getGroupId, groupId)
               .eq(ImGroupMember::getUserId, userId)
               .eq(ImGroupMember::getEnabled, 1);
        
        ImGroupMember member = imGroupMemberService.getOne(wrapper);
        return member != null ? member.getJoinTime() : null;
    }
    
    @Override
    public List<ImGroupMember> getGroupMembers(String groupId) {
        LambdaQueryWrapper<ImGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImGroupMember::getGroupId, groupId)
               .eq(ImGroupMember::getEnabled, 1)
               .orderByAsc(ImGroupMember::getJoinTime);
        
        return imGroupMemberService.list(wrapper);
    }
}
