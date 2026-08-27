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
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程参与人名称批量解析。
 * <p>历史任务的办理人/候选人可能已被删除或停用，角色/部门可能已撤销，
 * 候选组 ID 后缀也可能非纯数字（历史脏数据）。此处统一做空值与格式防御，
 * 解析失败时降级返回原始标识或跳过，保证流程详情/流转记录接口
 * 不因单个参与人数据缺失而 500。</p>
 * <p>详情页每节点每人逐条查询是典型 N+1，调用方应先用 {@link #userNickMap}/{@link #groupNameMap}
 * 批量预取，再在循环内读映射。</p>
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
     * 批量解析用户昵称（消除详情页逐节点逐人查询的 N+1）。
     * 键为登录名，值为昵称；查无此人/昵称为空时值降级为登录名。
     */
    public Map<String, String> userNickMap(Collection<String> userNames) {
        Map<String, String> nickMap = new HashMap<>();
        List<String> distinctNames = userNames.stream()
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        if (distinctNames.isEmpty()) {
            return nickMap;
        }
        for (SysUserVo user : sysUserService.selectUserByUserNames(distinctNames)) {
            nickMap.put(user.getUserName(), StringUtils.defaultIfBlank(user.getNickName(), user.getUserName()));
        }
        // 未命中（已删除用户）回填登录名，保证展示降级一致
        for (String name : distinctNames) {
            nickMap.putIfAbsent(name, name);
        }
        return nickMap;
    }

    /**
     * 批量解析候选组名称（ROLE{id}/DEPT{id}），每个不同 ID 只查一次库；
     * 前缀不符、ID 非数字或记录已删除的项不出现在结果中（调用方按缺失跳过）
     */
    public Map<String, String> groupNameMap(Collection<String> groupIds) {
        Map<String, String> nameMap = new HashMap<>();
        for (String groupId : groupIds.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList())) {
            String name = groupName(groupId);
            if (name != null) {
                nameMap.put(groupId, name);
            }
        }
        return nameMap;
    }

    /**
     * 解析单个候选组名称；前缀不符、ID 非数字或记录已删除时返回 null
     */
    private String groupName(String groupId) {
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
}
