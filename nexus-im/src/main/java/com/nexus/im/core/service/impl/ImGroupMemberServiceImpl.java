package com.nexus.im.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.im.core.ImGroupMember;
import com.nexus.im.core.mapper.ImGroupMemberMapper;
import com.nexus.im.core.service.ImGroupMemberService;
import org.springframework.stereotype.Service;

/**
 * IM群组成员服务实现类
 *
 * @author nexus
 */
@Service
public class ImGroupMemberServiceImpl extends ServiceImpl<ImGroupMemberMapper, ImGroupMember> implements ImGroupMemberService {
}
