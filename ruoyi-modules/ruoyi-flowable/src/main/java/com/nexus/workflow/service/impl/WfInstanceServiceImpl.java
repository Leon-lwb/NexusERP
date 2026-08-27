package com.nexus.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.UserService;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.core.utils.SpringContextHolder;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.common.constant.ProcessConstants;
import com.nexus.common.flowable.common.enums.FlowComment;
import com.nexus.common.flowable.common.enums.ProcessStatus;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.factory.FlowServiceFactory;
import com.nexus.common.flowable.utils.TaskUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.handler.ProcessAccessGuard;
import com.nexus.workflow.util.WfParticipantNameResolver;
import com.nexus.workflow.service.WfCallBackServiceI;
import com.nexus.system.service.CommonService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.domain.bo.WfTaskBo;
import com.nexus.workflow.domain.vo.WfFormVo;
import com.nexus.workflow.domain.vo.WfTaskVo;
import com.nexus.workflow.service.IWfDeployFormService;
import com.nexus.workflow.service.IWfMyBusinessService;
import com.nexus.workflow.service.IWfInstanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;


/**
 * 工作流流程实例管理
 *
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class WfInstanceServiceImpl extends FlowServiceFactory implements IWfInstanceService {

    private final IWfDeployFormService deployFormService;
    private final UserService userService;
    private final IWfMyBusinessService wfMyBusinessService;
    private final ProcessAccessGuard processAccessGuard;
    private final WfParticipantNameResolver participantNameResolver;
    @Resource
    private CommonService commonService;

    /**
     * 结束流程实例（终止整个流程，走引擎 API 将活动迁移到结束节点，并回写业务状态）
     *
     * @param vo 包含流程实例ID与终止原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopProcessInstance(WfTaskBo vo) {
        String procInsId = vo.getProcInsId();
        if (StringUtils.isBlank(procInsId)) {
            throw new ServiceException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceTenantId(TenantHelper.getTenantId())
            .processInstanceId(procInsId)
            .singleResult();
        if (processInstance == null) {
            throw new ServiceException("流程实例不存在或已结束");
        }
        // 仅流程发起人（或超级管理员/租户管理员）可终止流程实例，防止任意用户终止他人流程
        String currentUsername = TaskUtils.getUserName();
        if (!StringUtils.equals(currentUsername, processInstance.getStartUserId())
            && !LoginHelper.isSuperAdmin() && !LoginHelper.isTenantAdmin()) {
            throw new ServiceException("仅流程发起人或管理员可终止该流程实例");
        }
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        if (ObjectUtil.isNull(bpmnModel)) {
            throw new ServiceException("获取流程定义信息失败");
        }
        List<EndEvent> endNodes = bpmnModel.getMainProcess().findFlowElementsOfType(EndEvent.class, false);
        if (CollUtil.isEmpty(endNodes)) {
            throw new ServiceException("流程定义中不存在结束节点");
        }
        // 当前活动任务加终止意见
        List<Task> activeTasks = taskService.createTaskQuery().processInstanceId(procInsId).active().list();
        if (CollUtil.isNotEmpty(activeTasks)) {
            for (Task activeTask : activeTasks) {
                taskService.addComment(activeTask.getId(), procInsId, FlowComment.STOP.getType(),
                    StringUtils.isBlank(vo.getComment()) ? "取消申请" : vo.getComment());
            }
        }
        // 流程状态置为已取消，并将所有活动迁移到结束节点
        runtimeService.setVariable(procInsId, ProcessConstants.PROCESS_STATUS_KEY, ProcessStatus.CANCELED.getStatus());
        List<Execution> executions = runtimeService.createExecutionQuery().parentId(procInsId).list();
        List<String> executionIds = executions.stream().map(Execution::getId).collect(Collectors.toList());
        runtimeService.createChangeActivityStateBuilder()
            .moveExecutionsToSingleActivityId(executionIds, endNodes.get(0).getId())
            .changeState();
        // 回写自定义业务状态为已终止
        if (StringUtils.isNotBlank(vo.getDataId())) {
            WfMyBusiness business = wfMyBusinessService.getByDataId(vo.getDataId());
            if (business != null) {
                business.setActStatus(ActStatus.stop);
                business.setTaskId("");
                business.setTaskName("已终止");
                business.setTodoUsers("");
                wfMyBusinessService.updateById(business);
            }
        }
    }

    /**
     * 激活或挂起流程实例
     *
     * @param state      状态
     * @param instanceId 流程实例ID
     */
    @Override
    public void updateState(Integer state, String instanceId) {
        if (state == null) {
            throw new ServiceException("状态参数不能为空");
        }
        // 校验实例存在于当前租户，防止跨租户挂起/激活任意实例
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceTenantId(TenantHelper.getTenantId())
            .processInstanceId(instanceId)
            .singleResult();
        if (processInstance == null) {
            throw new ServiceException("流程实例不存在或已结束");
        }
        // 激活
        if (state == 1) {
            runtimeService.activateProcessInstanceById(instanceId);
        }
        // 挂起
        if (state == 2) {
            runtimeService.suspendProcessInstanceById(instanceId);
        }
    }

    /**
     * 删除流程实例ID
     *
     * @param instanceId   流程实例ID
     * @param deleteReason 删除原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String instanceId, String deleteReason) {

        // 查询历史数据
        HistoricProcessInstance historicProcessInstance = getHistoricProcessInstanceById(instanceId);
        checkInstanceTenant(historicProcessInstance);
        if (historicProcessInstance.getEndTime() != null) {
            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
            return;
        }
        // 删除流程实例
        runtimeService.deleteProcessInstance(instanceId, deleteReason);
        // 删除历史流程实例
        historyService.deleteHistoricProcessInstance(instanceId);
    }

    /**
     * 删除流程实例ID
     *
     * <p>使用 REQUIRES_NEW 独立事务：唯一调用方 WfCommonService.delActBusiness 在
     * try/catch 中调用本方法以"实例删除失败不阻断业务关联删除"，若与本方法共用外层事务，
     * 内部抛出异常会把共享事务标记为 rollback-only，外层 catch 后提交时仍会抛
     * UnexpectedRollbackException；独立事务让失败只回滚删除本身。</p>
     *
     * @param instanceId   流程实例ID，涉及业务DataId
     * @param deleteReason 删除原因
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void delete(String instanceId, String deleteReason, String dataId ) {
       if(StringUtils.isNotBlank(dataId)){
    	   LambdaQueryWrapper<WfMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
           flowMyBusinessLambdaQueryWrapper.eq(WfMyBusiness::getDataId, dataId);
           //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
           WfMyBusiness business = wfMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
           if (business == null) {
               throw new ServiceException("未找到流程业务关联记录，删除申请失败");
           }
           this.deleteForDataId(business.getProcessInstanceId(),deleteReason);
       }
       else {
    	   List<Task> task = taskService.createTaskQuery().processInstanceId(instanceId).list();
           if (CollectionUtils.isEmpty(task)) {//真要想删除，可以考虑去掉这部分代码
               throw new ServiceException("流程未启动或已执行完成，删除申请失败");
           }
	        // 查询历史数据
	        HistoricProcessInstance historicProcessInstance = getHistoricProcessInstanceById(instanceId);
	        if (historicProcessInstance.getEndTime() != null) {
	            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
	            return;
	        }
	        // 删除流程实例
	        runtimeService.deleteProcessInstance(instanceId, deleteReason);
	        // 删除历史流程实例
	        historyService.deleteHistoricProcessInstance(instanceId);
       }
    }

    /**
     * 删除流程实例ID
     *
     * @param instanceId   流程实例ID，涉及业务DataId
     * @param deleteReason 删除原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForDataId(String instanceId, String deleteReason) {
        // 校验实例属于当前租户，防止跨租户删除
        HistoricProcessInstance tenantCheckInstance =
            historyService.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (tenantCheckInstance == null) {
            throw new ServiceException("流程实例不存在");
        }
        checkInstanceTenant(tenantCheckInstance);
        List<Task> task = taskService.createTaskQuery().processInstanceId(instanceId).list();
        if (CollectionUtils.isEmpty(task)) {
            throw new ServiceException("流程未启动或已执行完成，删除申请失败");
        }
        // 查询历史数据
        HistoricProcessInstance historicProcessInstance = getHistoricProcessInstanceById(instanceId);
        if (historicProcessInstance.getEndTime() != null) {
            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
            return;
        }
        // 删除流程实例
        runtimeService.deleteProcessInstance(instanceId, deleteReason);
        // 删除历史流程实例
        historyService.deleteHistoricProcessInstance(instanceId);
        /*======================撤回删除 回调以及关键数据保存======================*/
        LambdaQueryWrapper<WfMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(WfMyBusiness::getProcessInstanceId,instanceId)
        ;
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        WfMyBusiness business = wfMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        if (business == null) {
            throw new ServiceException("未找到流程业务关联记录，删除申请失败");
        }
        //设置数据
        String doneUsers = business.getDoneUsers();
        //SysUser sysUser = iFlowThirdService.getLoginUser();
        LoginUser loginUser = commonService.getLoginUser();
        // 处理过流程的人
        JSONArray doneUserList = new JSONArray();
        if (StrUtil.isNotBlank(doneUsers)){
            doneUserList = JSON.parseArray(doneUsers);
        }
        if (!doneUserList.contains(loginUser.getUsername())){
            doneUserList.add(loginUser.getUsername());
        }
        business.setActStatus(ActStatus.revoke);
        business.setTaskId("");
        business.setTaskName("已撤回");
        business.setPriority("");
        business.setDoneUsers(doneUserList.toJSONString());
        business.setTodoUsers("");
        wfMyBusinessService.updateById(business);
        //spring容器类名
        String serviceImplName = business.getServiceImplName();
        WfCallBackServiceI flowCallBackService = (WfCallBackServiceI) SpringContextHolder.getBean(serviceImplName);
        // 流程处理完后，进行回调业务层
        if (flowCallBackService!=null)flowCallBackService.afterFlowHandle(business);

    }

    /**
     * 根据实例ID查询历史实例数据
     *
     * @param processInstanceId
     * @return
     */
    @Override
    public HistoricProcessInstance getHistoricProcessInstanceById(String processInstanceId) {
        HistoricProcessInstance historicProcessInstance =
                historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (Objects.isNull(historicProcessInstance)) {
            throw new FlowableObjectNotFoundException("流程实例不存在: " + processInstanceId);
        }
        return historicProcessInstance;
    }

    /**
     * 校验历史流程实例属于当前租户，防止跨租户删除/操作
     */
    private void checkInstanceTenant(HistoricProcessInstance historicProcessInstance) {
        if (historicProcessInstance == null
            || !StringUtils.equals(historicProcessInstance.getTenantId(), TenantHelper.getTenantId())) {
            throw new ServiceException("无权操作该流程实例");
        }
    }


    /**
     * 流程历史流转记录
     *
     * @param procInsId 流程实例Id
     * @return
     */
    @Override
    public Map<String, Object> queryDetailProcess(String procInsId, String deployId) {
        // 仅流程参与人（发起人/办理人/候选人/被抄送人）或管理员可查看流转记录，防止越权读取
        processAccessGuard.checkCanView(procInsId);
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isNotBlank(procInsId)) {
            List<HistoricTaskInstance> taskInstanceList = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(procInsId)
                .taskTenantId(TenantHelper.getTenantId())
                .orderByHistoricTaskInstanceStartTime().desc()
                .list();
            List<Comment> commentList = taskService.getProcessInstanceComments(procInsId);

            // ===== 批量预取，消除逐任务逐人查询的 N+1 =====
            // 身份链接：整实例一次查询替代原先每个任务一次 getHistoricIdentityLinksForTask
            List<HistoricIdentityLink> allLinks = historyService.getHistoricIdentityLinksForProcessInstance(procInsId);
            Map<String, List<HistoricIdentityLink>> linksByTask = new HashMap<>();
            Set<String> involvedUserIds = new HashSet<>();
            Set<String> involvedGroupIds = new HashSet<>();
            for (HistoricIdentityLink link : allLinks) {
                if (!"candidate".equals(link.getType()) || StringUtils.isBlank(link.getTaskId())) {
                    continue;
                }
                linksByTask.computeIfAbsent(link.getTaskId(), k -> new ArrayList<>()).add(link);
                if (StringUtils.isNotBlank(link.getUserId())) {
                    involvedUserIds.add(link.getUserId());
                }
                if (StringUtils.isNotBlank(link.getGroupId())) {
                    involvedGroupIds.add(link.getGroupId());
                }
            }
            for (HistoricTaskInstance taskInstance : taskInstanceList) {
                if (StringUtils.isNotBlank(taskInstance.getAssignee())) {
                    involvedUserIds.add(taskInstance.getAssignee());
                }
            }
            // 用户昵称一次批量查询；候选组名按不同 ID 去重后各查一次
            Map<String, String> nickMap = participantNameResolver.userNickMap(involvedUserIds);
            Map<String, String> groupMap = participantNameResolver.groupNameMap(involvedGroupIds);

            List<WfTaskVo> taskVoList = new ArrayList<>(taskInstanceList.size());
            taskInstanceList.forEach(taskInstance -> {
                WfTaskVo taskVo = new WfTaskVo();
                taskVo.setProcDefId(taskInstance.getProcessDefinitionId());
                taskVo.setTaskId(taskInstance.getId());
                taskVo.setTaskDefKey(taskInstance.getTaskDefinitionKey());
                taskVo.setTaskName(taskInstance.getName());
                taskVo.setCreateTime(taskInstance.getStartTime());
                taskVo.setFinishTime(taskInstance.getEndTime());
                if (StringUtils.isNotBlank(taskInstance.getAssignee())) {
                    String userId = taskInstance.getAssignee();
                    // 办理人可能已被删除：昵称缺失时降级显示登录名（批量解析已预置回填）
                    taskVo.setAssigneeId(userId);
                    taskVo.setAssigneeName(nickMap.getOrDefault(userId, userId));
                }
                // 展示审批人员（候选人/候选组可能已删除或组ID为脏数据，名称缺失时跳过该项）
                List<HistoricIdentityLink> linksForTask =
                    linksByTask.getOrDefault(taskInstance.getId(), Collections.emptyList());
                if (!linksForTask.isEmpty()) {
                    StringBuilder candidateBuilder = new StringBuilder();
                    for (HistoricIdentityLink link : linksForTask) {
                        if (StringUtils.isNotBlank(link.getUserId())) {
                            candidateBuilder.append(nickMap.getOrDefault(link.getUserId(), link.getUserId())).append(",");
                        } else if (StringUtils.isNotBlank(link.getGroupId())) {
                            String groupName = groupMap.get(link.getGroupId());
                            if (groupName != null) {
                                candidateBuilder.append(groupName).append(",");
                            }
                        }
                    }
                    if (candidateBuilder.length() > 0) {
                        taskVo.setCandidate(candidateBuilder.substring(0, candidateBuilder.length() - 1));
                    }
                }
                if (ObjectUtil.isNotNull(taskInstance.getDurationInMillis())) {
                    taskVo.setDuration(DateUtil.formatBetween(taskInstance.getDurationInMillis(), BetweenFormatter.Level.SECOND));
                }
                // 获取意见评论内容
                if (CollUtil.isNotEmpty(commentList)) {
                    List<Comment> comments = new ArrayList<>();
                    // commentList.stream().filter(comment -> taskInstance.getId().equals(comment.getTaskId())).collect(Collectors.toList());
                    for (Comment comment : commentList) {
                        // 流程级评论 taskId 为 null，用 Objects.equals 防止 NPE
                        if (Objects.equals(comment.getTaskId(), taskInstance.getId())) {
                            comments.add(comment);
                            // taskVo.setComment(WfCommentDto.builder().type(comment.getType()).comment(comment.getFullMessage()).build());
                        }
                    }
                    taskVo.setCommentList(comments);
                }
                taskVoList.add(taskVo);
            });
            map.put("flowList", taskVoList);
//            // 查询当前任务是否完成
//            List<Task> taskList = taskService.createTaskQuery().processInstanceId(procInsId).list();
//            if (CollectionUtils.isNotEmpty(taskList)) {
//                map.put("finished", true);
//            } else {
//                map.put("finished", false);
//            }
        }
        // 第一次申请获取初始化表单
        if (StringUtils.isNotBlank(deployId)) {
            WfFormVo formVo = deployFormService.selectDeployFormByDeployId(deployId);
            if (Objects.isNull(formVo)) {
                throw new ServiceException("请先配置流程表单");
            }
            map.put("formData", JsonUtils.parseObject(formVo.getContent(), Map.class));
        }
        return map;
    }
}
