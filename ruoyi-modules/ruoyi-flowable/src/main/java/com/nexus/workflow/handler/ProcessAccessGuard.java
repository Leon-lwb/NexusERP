package com.nexus.workflow.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.utils.TaskUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.domain.WfCopy;
import com.nexus.workflow.mapper.WfCopyMapper;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程详情访问权限校验。
 * <p>防止任意登录用户凭 procInsId 越权查看他人流程的表单内容与审批记录。
 * 允许查看的身份：超级管理员、租户管理员、流程发起人、办理人/候选人（含在途任务，
 * ACT_HI_TASKINST 在任务创建时即写入）、被抄送人。</p>
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@Component
public class ProcessAccessGuard {

    private final HistoryService historyService;
    private final WfCopyMapper wfCopyMapper;

    /**
     * 校验当前登录用户是否可查看指定流程实例详情，无权时抛出业务异常。
     * <p>procInsId 为空或实例不存在时不拦截（由后续查询逻辑抛出各自的业务异常）。</p>
     *
     * @param procInsId 流程实例 ID
     */
    public void checkCanView(String procInsId) {
        if (StringUtils.isBlank(procInsId)) {
            return;
        }
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return;
        }
        // 租户过滤：非本租户实例一律视为不存在
        HistoricProcessInstance hisIns = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(procInsId)
            .processInstanceTenantId(TenantHelper.getTenantId())
            .singleResult();
        if (hisIns == null) {
            return;
        }
        String username = TaskUtils.getUserName();
        // 发起人
        if (StringUtils.equals(username, hisIns.getStartUserId())) {
            return;
        }
        // 办理人/候选人（含在途任务：ACT_HI_TASKINST 在任务创建时即写入）
        long assigneeCount = historyService.createHistoricTaskInstanceQuery()
            .processInstanceId(procInsId)
            .taskAssignee(username)
            .count();
        if (assigneeCount > 0) {
            return;
        }
        long candidateCount = historyService.createHistoricTaskInstanceQuery()
            .processInstanceId(procInsId)
            .taskCandidateUser(username)
            .count();
        if (candidateCount > 0) {
            return;
        }
        // 候选组（ROLE{id}/DEPT{id}）：taskCandidateUser 不解析用户所属组，
        // 本系统任务大量按候选组分配，必须单独按组查询，否则合法办理人会被误拦
        List<String> candidateGroups = TaskUtils.getCandidateGroup();
        if (candidateGroups != null && !candidateGroups.isEmpty()) {
            long groupCount = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(procInsId)
                .taskCandidateGroupIn(candidateGroups)
                .count();
            if (groupCount > 0) {
                return;
            }
        }
        // 被抄送人
        Long copyCount = wfCopyMapper.selectCount(new LambdaQueryWrapper<WfCopy>()
            .eq(WfCopy::getInstanceId, procInsId)
            .eq(WfCopy::getUserId, LoginHelper.getUserId()));
        if (copyCount != null && copyCount > 0) {
            return;
        }
        throw new ServiceException("当前用户不是该流程的参与人，无权查看流程详情");
    }
}
