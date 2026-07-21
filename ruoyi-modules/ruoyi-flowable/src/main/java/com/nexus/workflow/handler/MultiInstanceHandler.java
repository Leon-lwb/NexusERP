package com.nexus.workflow.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;

import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.SimpleQuery;
import com.nexus.common.core.utils.SpringContextHolder;
import com.nexus.common.flowable.common.constant.ProcessConstants;
import com.nexus.system.domain.SysUser;
import com.nexus.system.domain.SysUserRole;
import com.nexus.system.service.ISysUserService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多实例处理类
 * <p>解析多实例用户任务的候选处理人集合，支持按用户、角色、部门解析，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 */
@AllArgsConstructor
@Component("multiInstanceHandler")
public class MultiInstanceHandler {

    /**
     * 解析当前用户任务的候选处理人名称集合。
     * <p>根据任务设计中的数据类型（USERS/ROLES/DEPTS）分别解析候选用户、角色下用户或部门下用户。</p>
     *
     * @param execution 流程执行实例
     * @return 候选处理人用户名集合（去重、有序）
     */
    public Set<String> getUserNames(DelegateExecution execution) {
        Set<String> candidateUserNames = new LinkedHashSet<>();
        FlowElement flowElement = execution.getCurrentFlowElement();
        if (ObjectUtil.isNotEmpty(flowElement) && flowElement instanceof UserTask) {
            UserTask userTask = (UserTask) flowElement;
            String dataType = userTask.getAttributeValue(ProcessConstants.NAMASPASE, ProcessConstants.PROCESS_CUSTOM_DATA_TYPE);
            if ("USERS".equals(dataType) && CollUtil.isNotEmpty(userTask.getCandidateUsers())) {
                // 添加候选用户
            	candidateUserNames.addAll(userTask.getCandidateUsers());
            } else if (CollUtil.isNotEmpty(userTask.getCandidateGroups())) {
                // 获取组的ID，角色ID集合或部门ID集合
                List<Long> groups = userTask.getCandidateGroups().stream()
                    .map(item -> Long.parseLong(item.substring(4)))
                    .collect(Collectors.toList());
                List<Long> userIds = new ArrayList<>();
                List<String> userNames = new ArrayList<>();
                if ("ROLES".equals(dataType)) {
                    // 通过角色id，获取所有用户id集合
                    LambdaQueryWrapper<SysUserRole> lqw = Wrappers.lambdaQuery(SysUserRole.class).select(SysUserRole::getUserId).in(SysUserRole::getRoleId, groups);
                    userIds = SimpleQuery.list(lqw, SysUserRole::getUserId);
                } else if ("DEPTS".equals(dataType)) {
                    // 通过部门id，获取所有用户id集合
                    LambdaQueryWrapper<SysUser> lqw = Wrappers.lambdaQuery(SysUser.class).select(SysUser::getUserId).in(SysUser::getDeptId, groups);
                    userIds = SimpleQuery.list(lqw, SysUser::getUserId);
                }
                // 添加候选用户
                ISysUserService sysUserService = SpringContextHolder.getBean(ISysUserService.class);
                userNames = sysUserService.selectUserNames(userIds);
                userNames.forEach(userName -> candidateUserNames.add(userName));
            }
        }
        return candidateUserNames;
    }
}
