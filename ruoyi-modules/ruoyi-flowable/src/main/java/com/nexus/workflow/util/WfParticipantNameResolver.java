package com.nexus.workflow.util;

import com.nexus.common.flowable.common.constant.TaskConstants;
import com.nexus.system.service.ISysDeptService;
import com.nexus.system.service.ISysRoleService;
import com.nexus.system.service.ISysUserService;
import com.nexus.system.domain.vo.SysDeptVo;
import com.nexus.system.domain.vo.SysRoleVo;
import com.nexus.system.domain.vo.SysUserVo;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程参与人名称解析。
 * <p>历史任务的办理人/候选人可能已被删除或停用，角色/部门可能已撤销，
 * 候选组 ID 后缀也可能非纯数字（历史脏数据）。此处统一做空值与格式防御，
 * 解析失败时降级返回原始标识，保证流程详情/流转记录接口不因单个参与人数据缺失而 500。</p>
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@Component
public class WfParticipantNameResolver {

    private final ISysUserService sysUserService;
    private final ISysRoleService roleService;
    private final ISysDeptService deptService;

    /**
     * 解析用户昵称；用户不存在/已删除时降级返回登录名，登录名为空返回空串
     */
    public String userNick(String userName) {
        if (StringUtils.isBlank(userName)) {
            return StringUtils.EMPTY;
        }
        SysUserVo user = sysUserService.selectUserByUserName(userName);
        if (user == null) {
            return userName;
        }
        return StringUtils.defaultIfBlank(user.getNickName(), userName);
    }

    /**
     * 解析候选身份（候选人或候选组）的展示名，无法解析返回 null（调用方跳过拼接）
     */
    public String candidateName(HistoricIdentityLink identityLink) {
        if (StringUtils.isNotBlank(identityLink.getUserId())) {
            return userNick(identityLink.getUserId());
        }
        String groupId = identityLink.getGroupId();
        if (StringUtils.isNotBlank(groupId)) {
            return groupName(groupId);
        }
        return null;
    }

    /**
     * 解析 ROLE{id}/DEPT{id} 候选组名称；前缀不符、ID 非数字或记录已删除时返回 null
     */
    public String groupName(String groupId) {
        if (StringUtils.isBlank(groupId)) {
            return null;
        }
        try {
            if (groupId.startsWith(TaskConstants.ROLE_GROUP_PREFIX)) {
                Long roleId = Long.parseLong(StringUtils.stripStart(groupId, TaskConstants.ROLE_GROUP_PREFIX));
                SysRoleVo role = roleService.selectRoleById(roleId);
                return role == null ? null : role.getRoleName();
            }
            if (groupId.startsWith(TaskConstants.DEPT_GROUP_PREFIX)) {
                Long deptId = Long.parseLong(StringUtils.stripStart(groupId, TaskConstants.DEPT_GROUP_PREFIX));
                SysDeptVo dept = deptService.selectDeptById(deptId);
                return dept == null ? null : dept.getDeptName();
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /**
     * 拼接任务全部候选身份名称（逗号分隔）；无候选返回 null
     */
    public String joinCandidateNames(List<HistoricIdentityLink> linksForTask) {
        if (linksForTask == null || linksForTask.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (HistoricIdentityLink link : linksForTask) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            String name = candidateName(link);
            if (StringUtils.isNotBlank(name)) {
                sb.append(name).append(",");
            }
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : null;
    }
}
