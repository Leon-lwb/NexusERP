package org.dromara.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.UserService;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.SpringContextHolder;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.flowable.common.constant.ProcessConstants;
import org.dromara.common.flowable.common.constant.TaskConstants;
import org.dromara.common.flowable.common.enums.FlowComment;
import org.dromara.common.flowable.common.enums.ProcessStatus;
import org.dromara.common.flowable.core.FormConf;
import org.dromara.common.flowable.core.domain.ActStatus;
import org.dromara.common.flowable.core.domain.ProcessQuery;
import org.dromara.common.flowable.factory.FlowServiceFactory;
import org.dromara.common.flowable.flow.FindNextNodeUtil;
import org.dromara.common.flowable.flow.FlowableUtils;
import org.dromara.common.flowable.utils.ModelUtils;
import org.dromara.common.flowable.utils.ProcessFormUtils;
import org.dromara.common.flowable.utils.ProcessUtils;
import org.dromara.common.flowable.utils.TaskUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.domain.vo.SysDeptVo;
import org.dromara.system.domain.vo.SysRoleVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.domain.SysRole;
import org.dromara.system.service.CommonService;
import org.dromara.system.service.ISysDeptService;
import org.dromara.system.service.ISysRoleService;
import org.dromara.system.service.ISysUserService;
import org.dromara.workflow.domain.WfCustomForm;
import org.dromara.workflow.domain.WfDeployForm;
import org.dromara.workflow.domain.WfMyBusiness;
import org.dromara.workflow.domain.dto.FlowNextDto;
import org.dromara.workflow.domain.vo.*;
import org.dromara.workflow.mapper.WfCategoryMapper;
import org.dromara.workflow.mapper.WfDeployFormMapper;
import org.dromara.workflow.service.IWfCustomFormService;
import org.dromara.workflow.service.IWfMyBusinessService;
import org.dromara.workflow.service.IWfProcessService;
import org.dromara.workflow.service.IWfTaskService;
import org.dromara.workflow.service.WfCommonService;
import org.dromara.workflow.service.WfCallBackServiceI;
import org.dromara.common.core.domain.model.LoginUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.ObjectUtils;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import javax.sql.DataSource;

/**
 * @author nbacheng
 * @createTime 2023-09-25
 */
@DS("master")
@RequiredArgsConstructor
@Service
@Slf4j
public class WfProcessServiceImpl extends FlowServiceFactory implements IWfProcessService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final IdentityService identityService;
    private final TaskService taskService;
    private final HistoryService historyService;

    private final IWfTaskService wfTaskService;
    private final UserService userService;
    private final ISysRoleService roleService;
    private final ISysDeptService deptService;
    private final WfDeployFormMapper deployFormMapper;
    private final CommonService commonService;
    private final ISysUserService sysUserService;
    private final IWfCustomFormService wfCustomFormService;
    private final IWfMyBusinessService wfMyBusinessService;
    private final WfCommonService wfCommonService;
    private final WfCategoryMapper categoryMapper;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    
    //仿钉钉流程转bpmn用
    private BpmnModel ddBpmnModel;
    private Process ddProcess;
    private List<SequenceFlow> ddSequenceFlows;

    /**
     * 流程定义列表
     *
     * @param pageQuery 分页参数
     * @return 流程定义分页列表数据
     */
    @Override
    public TableDataInfo<WfDefinitionVo> selectPageStartProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        Page<WfDefinitionVo> page = new Page<>();
        // 流程定义列表数据查询
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery()
            .processDefinitionTenantId(TenantHelper.getTenantId())
            .latestVersion()
            .active()
            .orderByProcessDefinitionKey()
            .asc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(processDefinitionQuery, processQuery);
        long pageTotal = processDefinitionQuery.count();
        if (pageTotal <= 0) {
            return TableDataInfo.build();
        }
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<ProcessDefinition> definitionList = processDefinitionQuery.listPage(offset, pageQuery.getPageSize());

        List<WfDefinitionVo> definitionVoList = new ArrayList<>();
        for (ProcessDefinition processDefinition : definitionList) {
            String deploymentId = processDefinition.getDeploymentId();
            Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
           // 检查流程是否是OA流程
            WfAppTypeVo appTypeVo = categoryMapper.selectAppTypeVoByCode(deployment.getCategory(),TenantHelper.getTenantId());
            String appType = "";
            if(ObjectUtil.isNotEmpty(appTypeVo)) {
            	appType =  appTypeVo.getId();
            }
            if(StrUtil.equalsAnyIgnoreCase(appType, "OA")) {//OA流程取出，其它流程到其它相应地方发起流程
	            WfDefinitionVo vo = new WfDefinitionVo();
	            vo.setDefinitionId(processDefinition.getId());
	            vo.setProcessKey(processDefinition.getKey());
	            vo.setProcessName(processDefinition.getName());
	            vo.setVersion(processDefinition.getVersion());
	            vo.setDeploymentId(processDefinition.getDeploymentId());
	            vo.setSuspended(processDefinition.isSuspended());
	            // 流程定义时间
	            vo.setCategory(deployment.getCategory());
	            vo.setDeploymentTime(deployment.getDeploymentTime());
	            definitionVoList.add(vo);
            }
            
        }
        page.setRecords(definitionVoList);
        page.setTotal(pageTotal);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WfDefinitionVo> selectStartProcessList(ProcessQuery processQuery) {
        // 流程定义列表数据查询
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery()
        		.processDefinitionTenantId(TenantHelper.getTenantId())
                .latestVersion()
                .active()
                .orderByProcessDefinitionKey()
                .asc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(processDefinitionQuery, processQuery);

        List<ProcessDefinition> definitionList = processDefinitionQuery.list();

        List<WfDefinitionVo> definitionVoList = new ArrayList<>();
        for (ProcessDefinition processDefinition : definitionList) {
            String deploymentId = processDefinition.getDeploymentId();
            Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
            WfDefinitionVo vo = new WfDefinitionVo();
            vo.setDefinitionId(processDefinition.getId());
            vo.setProcessKey(processDefinition.getKey());
            vo.setProcessName(processDefinition.getName());
            vo.setVersion(processDefinition.getVersion());
            vo.setDeploymentId(processDefinition.getDeploymentId());
            vo.setSuspended(processDefinition.isSuspended());
            // 流程定义时间
            vo.setCategory(deployment.getCategory());
            vo.setDeploymentTime(deployment.getDeploymentTime());
            definitionVoList.add(vo);
        }
        return definitionVoList;
    }

    @Override
    public TableDataInfo<WfTaskVo> selectPageOwnProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        Page<WfTaskVo> page = new Page<>();
        HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery()
        	.processInstanceTenantId(TenantHelper.getTenantId())	
        	.includeProcessVariables()
        	.startedBy(TaskUtils.getUserName())
            .orderByProcessInstanceStartTime()
            .desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(historicProcessInstanceQuery, processQuery);
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<HistoricProcessInstance> historicProcessInstances = historicProcessInstanceQuery
            .listPage(offset, pageQuery.getPageSize());
        page.setTotal(historicProcessInstanceQuery.count());
        List<WfTaskVo> taskVoList = new ArrayList<>();
        for (HistoricProcessInstance hisIns : historicProcessInstances) {
            WfTaskVo taskVo = new WfTaskVo();
            // 获取流程状态
            HistoricVariableInstance processStatusVariable = historyService.createHistoricVariableInstanceQuery()	
                .processInstanceId(hisIns.getId())
                .variableName(ProcessConstants.PROCESS_STATUS_KEY)
                .singleResult();
            String processStatus = null;
            if (ObjectUtil.isNotNull(processStatusVariable)) {
                processStatus = Convert.toStr(processStatusVariable.getValue());
            }
            // 兼容旧流程
            if (processStatus == null) {
                processStatus = ObjectUtil.isNull(hisIns.getEndTime()) ? ProcessStatus.RUNNING.getStatus() : ProcessStatus.COMPLETED.getStatus();
            }
            Map<String, Object>  processVariables = hisIns.getProcessVariables();
            if(processVariables.containsKey("dataId")) {
            	taskVo.setDataId(processVariables.get("dataId").toString());
            }
            taskVo.setProcessStatus(processStatus);
            taskVo.setCreateTime(hisIns.getStartTime());
            taskVo.setFinishTime(hisIns.getEndTime());
            taskVo.setProcInsId(hisIns.getId());

            // 计算耗时
            if (Objects.nonNull(hisIns.getEndTime())) {
                taskVo.setDuration(DateUtils.getDatePoor(hisIns.getEndTime(), hisIns.getStartTime()));
            } else {
                taskVo.setDuration(DateUtils.getDatePoor(DateUtils.getNowDate(), hisIns.getStartTime()));
            }
            // 流程部署实例信息
            Deployment deployment = repositoryService.createDeploymentQuery()
                .deploymentId(hisIns.getDeploymentId()).singleResult();
            taskVo.setDeployId(hisIns.getDeploymentId());
            taskVo.setProcDefId(hisIns.getProcessDefinitionId());
            taskVo.setProcDefName(hisIns.getProcessDefinitionName());
            taskVo.setProcDefVersion(hisIns.getProcessDefinitionVersion());
            taskVo.setCategory(deployment.getCategory());
            // 当前所处流程
            List<Task> taskList = taskService.createTaskQuery().processInstanceId(hisIns.getId()).includeIdentityLinks().list();
            // 任务列表
            if (CollUtil.isNotEmpty(taskList)) {
            	taskVo.setTaskName(taskList.stream().map(Task::getName).filter(StringUtils::isNotEmpty).collect(Collectors.joining(",")));
            	taskVo.setTaskId(taskList.get(0).getId());
            } else {
                List<HistoricTaskInstance> historicTaskInstance = historyService.createHistoricTaskInstanceQuery().processInstanceId(hisIns.getId()).orderByHistoricTaskInstanceEndTime().desc().list();
                taskVo.setTaskId(historicTaskInstance.get(0).getId());
            }
            taskVoList.add(taskVo);
        }
        page.setRecords(taskVoList);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WfTaskVo> selectOwnProcessList(ProcessQuery processQuery) {
        HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery()
        		.processInstanceTenantId(TenantHelper.getTenantId())
                .startedBy(TaskUtils.getUserName())
                .processInstanceTenantId(TenantHelper.getTenantId())
                .orderByProcessInstanceStartTime()
                .desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(historicProcessInstanceQuery, processQuery);
        List<HistoricProcessInstance> historicProcessInstances = historicProcessInstanceQuery.list();
        List<WfTaskVo> taskVoList = new ArrayList<>();
        for (HistoricProcessInstance hisIns : historicProcessInstances) {
            WfTaskVo taskVo = new WfTaskVo();
            taskVo.setCreateTime(hisIns.getStartTime());
            taskVo.setFinishTime(hisIns.getEndTime());
            taskVo.setProcInsId(hisIns.getId());

            // 计算耗时
            if (Objects.nonNull(hisIns.getEndTime())) {
                taskVo.setDuration(DateUtils.getDatePoor(hisIns.getEndTime(), hisIns.getStartTime()));
            } else {
                taskVo.setDuration(DateUtils.getDatePoor(DateUtils.getNowDate(), hisIns.getStartTime()));
            }
            // 流程部署实例信息
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(hisIns.getDeploymentId()).singleResult();
            taskVo.setDeployId(hisIns.getDeploymentId());
            taskVo.setProcDefId(hisIns.getProcessDefinitionId());
            taskVo.setProcDefName(hisIns.getProcessDefinitionName());
            taskVo.setProcDefVersion(hisIns.getProcessDefinitionVersion());
            taskVo.setCategory(deployment.getCategory());
            // 当前所处流程
            List<Task> taskList = taskService.createTaskQuery().processInstanceId(hisIns.getId()).includeIdentityLinks().list();
            if (CollUtil.isNotEmpty(taskList)) {
                taskVo.setTaskName(taskList.stream().map(Task::getName).filter(StringUtils::isNotEmpty).collect(Collectors.joining(",")));
            }
            taskVoList.add(taskVo);
        }
        return taskVoList;
    }

    @Override
    public TableDataInfo<WfTaskVo> selectPageTodoProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
    	Page<WfTaskVo> page = new Page<>();
        TaskQuery taskQuery = taskService.createTaskQuery()
            .active()
            .includeProcessVariables()
            .taskCandidateOrAssigned(TaskUtils.getUserName())
            .taskCandidateGroupIn(TaskUtils.getCandidateGroup())
            .taskTenantId(TenantHelper.getTenantId())
            .orderByTaskCreateTime().desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(taskQuery, processQuery);
        page.setTotal(taskQuery.count());
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<Task> taskList = taskQuery.listPage(offset, pageQuery.getPageSize());
        List<WfTaskVo> flowList = new ArrayList<>();
        for (Task task : taskList) {
            WfTaskVo flowTask = new WfTaskVo();
            // 当前流程信息
            flowTask.setTaskId(task.getId());
            flowTask.setTaskDefKey(task.getTaskDefinitionKey());
            flowTask.setCreateTime(task.getCreateTime());
            flowTask.setProcDefId(task.getProcessDefinitionId());
            flowTask.setTaskName(task.getName());
            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
            flowTask.setDeployId(pd.getDeploymentId());
            flowTask.setProcDefName(pd.getName());
            flowTask.setProcDefVersion(pd.getVersion());
            flowTask.setProcInsId(task.getProcessInstanceId());

            // 流程发起人信息
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
            String userId = historicProcessInstance.getStartUserId();
            String nickName = sysUserService.selectUserByUserName(userId).getNickName();
            flowTask.setStartUserId(userId);
            flowTask.setStartUserName(nickName);
            flowTask.setDataId(historicProcessInstance.getBusinessKey());

            // 流程变量
            flowTask.setProcVars(task.getProcessVariables());

            flowList.add(flowTask);
        }
        page.setRecords(flowList);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WfTaskVo> selectTodoProcessList(ProcessQuery processQuery) {
        TaskQuery taskQuery = taskService.createTaskQuery()
                .active()
                .includeProcessVariables()
                .taskCandidateOrAssigned(TaskUtils.getUserName())
                .taskCandidateGroupIn(TaskUtils.getCandidateGroup())
                .taskTenantId(TenantHelper.getTenantId())
                .orderByTaskCreateTime().desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(taskQuery, processQuery);
        List<Task> taskList = taskQuery.list();
        List<WfTaskVo> taskVoList = new ArrayList<>();
        for (Task task : taskList) {
            WfTaskVo taskVo = new WfTaskVo();
            // 当前流程信息
            taskVo.setTaskId(task.getId());
            taskVo.setTaskDefKey(task.getTaskDefinitionKey());
            taskVo.setCreateTime(task.getCreateTime());
            taskVo.setProcDefId(task.getProcessDefinitionId());
            taskVo.setTaskName(task.getName());
            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(task.getProcessDefinitionId())
                    .singleResult();
            taskVo.setDeployId(pd.getDeploymentId());
            taskVo.setProcDefName(pd.getName());
            taskVo.setProcDefVersion(pd.getVersion());
            taskVo.setProcInsId(task.getProcessInstanceId());

            // 流程发起人信息
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            String userId = historicProcessInstance.getStartUserId();
            String nickName = sysUserService.selectUserByUserName(userId).getNickName();
            taskVo.setStartUserId(userId);
            taskVo.setStartUserName(nickName);

            taskVoList.add(taskVo);
        }
        return taskVoList;
    }

    @Override
    public TableDataInfo<WfTaskVo> selectPageClaimProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        Page<WfTaskVo> page = new Page<>();
        TaskQuery taskQuery = taskService.createTaskQuery()
            .active()
            .includeProcessVariables()
            .taskCandidateUser(TaskUtils.getUserName())
            .taskCandidateGroupIn(TaskUtils.getCandidateGroup())
            .taskTenantId(TenantHelper.getTenantId())
            .orderByTaskCreateTime().desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(taskQuery, processQuery);
        page.setTotal(taskQuery.count());
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<Task> taskList = taskQuery.listPage(offset, pageQuery.getPageSize());
        List<WfTaskVo> flowList = new ArrayList<>();
        for (Task task : taskList) {
            WfTaskVo flowTask = new WfTaskVo();
            // 当前流程信息
            flowTask.setTaskId(task.getId());
            flowTask.setTaskDefKey(task.getTaskDefinitionKey());
            flowTask.setCreateTime(task.getCreateTime());
            flowTask.setProcDefId(task.getProcessDefinitionId());
            flowTask.setTaskName(task.getName());
            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
            flowTask.setDeployId(pd.getDeploymentId());
            flowTask.setProcDefName(pd.getName());
            flowTask.setProcDefVersion(pd.getVersion());
            flowTask.setProcInsId(task.getProcessInstanceId());

            // 流程发起人信息
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
            String userId = historicProcessInstance.getStartUserId();
            String nickName = sysUserService.selectUserByUserName(userId).getNickName();
            flowTask.setStartUserId(userId);
            flowTask.setStartUserName(nickName);

            flowList.add(flowTask);
        }
        page.setRecords(flowList);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WfTaskVo> selectClaimProcessList(ProcessQuery processQuery) {
        TaskQuery taskQuery = taskService.createTaskQuery()
                .active()
                .includeProcessVariables()
                .taskCandidateUser(TaskUtils.getUserName())
                .taskCandidateGroupIn(TaskUtils.getCandidateGroup())
                .taskTenantId(TenantHelper.getTenantId())
                .orderByTaskCreateTime().desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(taskQuery, processQuery);
        List<Task> taskList = taskQuery.list();
        List<WfTaskVo> flowList = new ArrayList<>();
        for (Task task : taskList) {
            WfTaskVo flowTask = new WfTaskVo();
            // 当前流程信息
            flowTask.setTaskId(task.getId());
            flowTask.setTaskDefKey(task.getTaskDefinitionKey());
            flowTask.setCreateTime(task.getCreateTime());
            flowTask.setProcDefId(task.getProcessDefinitionId());
            flowTask.setTaskName(task.getName());
            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(task.getProcessDefinitionId())
                    .singleResult();
            flowTask.setDeployId(pd.getDeploymentId());
            flowTask.setProcDefName(pd.getName());
            flowTask.setProcDefVersion(pd.getVersion());
            flowTask.setProcInsId(task.getProcessInstanceId());

            // 流程发起人信息
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            String userId = historicProcessInstance.getStartUserId();
            String nickName = sysUserService.selectUserByUserName(userId).getNickName();
            flowTask.setStartUserId(userId);
            flowTask.setStartUserName(nickName);

            flowList.add(flowTask);
        }
        return flowList;
    }

    @Override
    public TableDataInfo<WfTaskVo> selectPageFinishedProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        Page<WfTaskVo> page = new Page<>();
        HistoricTaskInstanceQuery taskInstanceQuery = historyService.createHistoricTaskInstanceQuery()
            .includeProcessVariables()
            .finished()
            .taskAssignee(TaskUtils.getUserName())
            .taskTenantId(TenantHelper.getTenantId())
            .orderByHistoricTaskInstanceEndTime()
            .desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(taskInstanceQuery, processQuery);
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<HistoricTaskInstance> historicTaskInstanceList = taskInstanceQuery.listPage(offset, pageQuery.getPageSize());
        List<WfTaskVo> hisTaskList = new ArrayList<>();
        for (HistoricTaskInstance histTask : historicTaskInstanceList) {
            WfTaskVo flowTask = new WfTaskVo();
            // 当前流程信息
            flowTask.setTaskId(histTask.getId());
            // 审批人员信息
            flowTask.setCreateTime(histTask.getCreateTime());
            flowTask.setFinishTime(histTask.getEndTime());
            flowTask.setDuration(DateUtil.formatBetween(histTask.getDurationInMillis(), BetweenFormatter.Level.SECOND));
            flowTask.setProcDefId(histTask.getProcessDefinitionId());
            flowTask.setTaskDefKey(histTask.getTaskDefinitionKey());
            flowTask.setTaskName(histTask.getName());

            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(histTask.getProcessDefinitionId())
                .singleResult();
            flowTask.setDeployId(pd.getDeploymentId());
            flowTask.setProcDefName(pd.getName());
            flowTask.setProcDefVersion(pd.getVersion());
            flowTask.setProcInsId(histTask.getProcessInstanceId());
            flowTask.setHisProcInsId(histTask.getProcessInstanceId());

            // 流程发起人信息
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(histTask.getProcessInstanceId())
                .singleResult();
            String userId = historicProcessInstance.getStartUserId();
            String nickName = sysUserService.selectUserByUserName(userId).getNickName();
            flowTask.setStartUserId(userId);
            flowTask.setStartUserName(nickName);

            // 流程变量
            flowTask.setProcVars(histTask.getProcessVariables());
            //自定义业务
            Map<String, Object>  processVariables = histTask.getProcessVariables();
            if(processVariables.containsKey("dataId")) {
            	flowTask.setDataId(processVariables.get("dataId").toString());
            }

            hisTaskList.add(flowTask);
        }
        page.setTotal(taskInstanceQuery.count());
        page.setRecords(hisTaskList);
//        Map<String, Object> result = new HashMap<>();
//        result.put("result",page);
//        result.put("finished",true);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WfTaskVo> selectFinishedProcessList(ProcessQuery processQuery) {
        HistoricTaskInstanceQuery taskInstanceQuery = historyService.createHistoricTaskInstanceQuery()
                .includeProcessVariables()
                .finished()
                .taskAssignee(TaskUtils.getUserName())
                .taskTenantId(TenantHelper.getTenantId())
                .orderByHistoricTaskInstanceEndTime()
                .desc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(taskInstanceQuery, processQuery);
        List<HistoricTaskInstance> historicTaskInstanceList = taskInstanceQuery.list();
        List<WfTaskVo> hisTaskList = new ArrayList<>();
        for (HistoricTaskInstance histTask : historicTaskInstanceList) {
            WfTaskVo flowTask = new WfTaskVo();
            // 当前流程信息
            flowTask.setTaskId(histTask.getId());
            // 审批人员信息
            flowTask.setCreateTime(histTask.getCreateTime());
            flowTask.setFinishTime(histTask.getEndTime());
            flowTask.setDuration(DateUtil.formatBetween(histTask.getDurationInMillis(), BetweenFormatter.Level.SECOND));
            flowTask.setProcDefId(histTask.getProcessDefinitionId());
            flowTask.setTaskDefKey(histTask.getTaskDefinitionKey());
            flowTask.setTaskName(histTask.getName());

            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(histTask.getProcessDefinitionId())
                    .singleResult();
            flowTask.setDeployId(pd.getDeploymentId());
            flowTask.setProcDefName(pd.getName());
            flowTask.setProcDefVersion(pd.getVersion());
            flowTask.setProcInsId(histTask.getProcessInstanceId());
            flowTask.setHisProcInsId(histTask.getProcessInstanceId());

            // 流程发起人信息
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(histTask.getProcessInstanceId())
                    .singleResult();
            String userId = historicProcessInstance.getStartUserId();
            String nickName = sysUserService.selectUserByUserName(userId).getNickName();
            flowTask.setStartUserId(userId);
            flowTask.setStartUserName(nickName);

            // 流程变量
            flowTask.setProcVars(histTask.getProcessVariables());

            hisTaskList.add(flowTask);
        }
        return hisTaskList;
    }

    @Override
    public FormConf selectFormContent(String definitionId, String deployId, String procInsId) {
        WfDeployForm deployForm = getDeployForm(definitionId,deployId);
        /*FormConf formConf = JsonUtils.parseObject(deployForm.getContent(), FormConf.class);
        if (ObjectUtil.isNull(formConf)) {
            throw new RuntimeException("获取流程表单失败！");
        }   formdesigner*/
        Map<String, Object> formModel = JsonUtils.parseObject(deployForm.getContent(), Map.class);
        if (null == formModel || formModel.isEmpty()) {
            throw new RuntimeException("获取流程表单失败！");
        }
        FormConf formConf = new FormConf();
        formConf.setFormBtns(false);
        formConf.setFormModel(formModel);
        if (ObjectUtil.isNotEmpty(procInsId)) {
            // 获取流程实例
            HistoricProcessInstance historicProcIns = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(procInsId)
                .includeProcessVariables()
                .singleResult();
            // 填充表单信息
            //ProcessFormUtils.fillFormData(formConf, historicProcIns.getProcessVariables()); //formdesigner
            formConf.setFormData(historicProcIns.getProcessVariables());
        }
        return formConf;
    }
    
    private WfDeployForm getDeployForm(String definitionId, String deployId) {
    	BpmnModel bpmnModel = repositoryService.getBpmnModel(definitionId);
        if (ObjectUtil.isNull(bpmnModel)) {
            throw new RuntimeException("获取流程设计失败！");
        }
        StartEvent startEvent = ModelUtils.getStartEvent(bpmnModel);
        WfDeployForm deployForm = deployFormMapper.selectOne(new LambdaQueryWrapper<WfDeployForm>()
            .eq(WfDeployForm::getDeployId, deployId)
            .eq(WfDeployForm::getFormKey, startEvent.getFormKey())
            .eq(WfDeployForm::getNodeKey, startEvent.getId()));
		return deployForm;
    	
    }

    /**
     * 根据流程定义ID启动流程实例
     *
     * @param procDefId 流程定义Id
     * @param variables 流程变量
     * @return
     */
    @Override
    public R<Void> startProcessByDefId(String procDefId, Map<String, Object> variables) {
       ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(procDefId).singleResult();
       return startProcess(processDefinition, variables);
    }

    /**
     * 通过DefinitionKey启动流程
     * @param procDefKey 流程定义Key
     * @param variables 扩展参数
     */
    @Override
    public R<Void> startProcessByDefKey(String procDefKey, Map<String, Object> variables) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(procDefKey).latestVersion().singleResult();
        return startProcess(processDefinition, variables);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcessByIds(String[] instanceIds) {
        List<String> ids = Arrays.asList(instanceIds);
        // 校验流程是否结束
        long activeInsCount = runtimeService.createProcessInstanceQuery()
            .processInstanceIds(new HashSet<>(ids)).active().count();
        if (activeInsCount > 0) {
            throw new ServiceException("不允许删除进行中的流程实例");
        }
        // 删除历史流程实例
        historyService.bulkDeleteHistoricProcessInstances(ids);
    }

    /**
     * 读取xml文件
     * @param processDefId 流程定义ID
     */
    @Override
    public String queryBpmnXmlById(String processDefId) {
        InputStream inputStream = repositoryService.getProcessModel(processDefId);
        try {
            return IoUtil.readUtf8(inputStream);
        } catch (IORuntimeException exception) {
            throw new RuntimeException("加载xml文件异常");
        }
    }

    /**
     * 流程详情信息
     *
     * @param procInsId 流程实例ID
     * @param taskId 任务ID
     * @return
     */
    @Override
    public WfDetailVo queryProcessDetail(String procInsId, String taskId, String dataId ) {
        WfDetailVo detailVo = new WfDetailVo();
        List<CurNodeInfoVo> listCurNodeInfo = new ArrayList<CurNodeInfoVo>();
        // 获取流程实例
        HistoricProcessInstance historicProcIns = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(procInsId)
            .includeProcessVariables()
            .singleResult();

        // 提前加载 BPMN 模型，以便 currTaskFormData 获取自定义表单的 formKey
        InputStream inputStream = repositoryService.getProcessModel(historicProcIns.getProcessDefinitionId());
        String bpmnXmlStr = StrUtil.utf8Str(IoUtil.readBytes(inputStream, false));
        BpmnModel bpmnModel = ModelUtils.getBpmnModel(bpmnXmlStr);

        if (StringUtils.isNotBlank(taskId)) {
            HistoricTaskInstance taskIns = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .includeIdentityLinks()
                .includeProcessVariables()
                .includeTaskLocalVariables()
                .singleResult();
            if (taskIns == null) {
                throw new ServiceException("没有可办理的任务！");
            }
            // 从 BPMN 模型获取当前任务节点的 formKey（自定义表单的 key 格式为 "key_<id>"）
            String formKey = null;
            FlowElement flowElement = ModelUtils.getFlowElementById(bpmnModel, taskIns.getTaskDefinitionKey());
            if (flowElement != null) {
                formKey = ModelUtils.getFormKey(flowElement);
            }
            // 当前节点没有 formKey 时，尝试从开始节点获取（自定义表单可能配置在开始节点上）
            if (StringUtils.isBlank(formKey)) {
                StartEvent startEvent = ModelUtils.getStartEvent(bpmnModel);
                if (startEvent != null) {
                    formKey = ModelUtils.getFormKey(startEvent);
                }
            }
            detailVo.setTaskFormData(currTaskFormData(historicProcIns.getDeploymentId(), taskIns, formKey, dataId));
        }
        //当前任务信息
        List<Task> listtask = taskService.createTaskQuery().processInstanceId(procInsId).list();
        
        if(listtask != null && listtask.size() > 0) {
        	for(Task task: listtask) {
        		CurNodeInfoVo curNodeInfo = new CurNodeInfoVo();
        		curNodeInfo.setProcDefName(historicProcIns.getProcessDefinitionName());
                curNodeInfo.setProcDefVersion("V"+historicProcIns.getProcessDefinitionVersion().toString());
                curNodeInfo.setProcInsId(procInsId);
                curNodeInfo.setTaskId(task.getId());
                curNodeInfo.setAssignee(task.getAssignee());
                curNodeInfo.setReceiveTime(task.getCreateTime());
                listCurNodeInfo.add(curNodeInfo);
        	}
        }
        detailVo.setListCurNodeInfo(listCurNodeInfo);
        detailVo.setBpmnXml(bpmnXmlStr);
        detailVo.setHistoryProcNodeList(historyProcNodeList(historicProcIns));
        detailVo.setProcessFormList(processFormList(bpmnModel, historicProcIns, dataId));
        detailVo.setFlowViewer(getFlowViewer(bpmnModel, procInsId));
        if(isStartUserNode(taskId)) {
        	detailVo.setStartUserNode(true);
        }
        return detailVo;
    }
    
    /**
     * 根据任务ID判断当前节点是否为开始节点后面的第一个用户任务节点
     *
     * @param taskId 任务Id
     * @return
     */
    boolean isStartUserNode(String taskId) {
      //判断当前是否是第一个发起任务节点，若是就put变量isStartNode为True,让相应的表单可以编辑
      boolean isStartNode= false;
		if (Objects.nonNull(taskId)) {
			// 当前任务 task
			Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
			// 获取流程定义信息
			if (task != null) {
				ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
						.processDefinitionId(task.getProcessDefinitionId()).singleResult();
				// 获取所有节点信息
				Process process = repositoryService.getBpmnModel(processDefinition.getId()).getProcesses().get(0);
				// 获取全部节点列表，包含子节点
				Collection<FlowElement> allElements = FlowableUtils.getAllElements(process.getFlowElements(), null);
				// 获取当前任务节点元素
				FlowElement source = null;
				if (allElements != null) {
					for (FlowElement flowElement : allElements) {
						// 类型为用户节点
						if (flowElement.getId().equals(task.getTaskDefinitionKey())) {
							// 获取节点信息
							source = flowElement;
							List<SequenceFlow> inFlows = FlowableUtils.getElementIncomingFlows(source);
							if (inFlows.size() == 1) {
								FlowElement sourceFlowElement = inFlows.get(0).getSourceFlowElement();
								if (sourceFlowElement instanceof StartEvent) {// 源是开始节点
									isStartNode = true;
								}
							}
						}
					}
				}
			}
		}
        return isStartNode;
    }
    
    /**
     * 流程详情信息
     *
     * @param dataId 业务数据ID
     * @return
     */
    @Override
    public WfDetailVo queryProcessDetailByDataId(String dataId ) {
        WfDetailVo detailVo = new WfDetailVo();
        List<CurNodeInfoVo> listCurNodeInfo = new ArrayList<CurNodeInfoVo>();
        WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
        String procInsId = business.getProcessInstanceId();
        String taskId = business.getTaskId();
        // 获取流程实例
        HistoricProcessInstance historicProcIns = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(procInsId)
            .includeProcessVariables()
            .singleResult();
        // 提前加载 BPMN 模型
        InputStream inputStream = repositoryService.getProcessModel(historicProcIns.getProcessDefinitionId());
        String bpmnXmlStr = StrUtil.utf8Str(IoUtil.readBytes(inputStream, false));
        BpmnModel bpmnModel = ModelUtils.getBpmnModel(bpmnXmlStr);

        if (StringUtils.isNotBlank(taskId)) {
            HistoricTaskInstance taskIns = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .includeIdentityLinks()
                .includeProcessVariables()
                .includeTaskLocalVariables()
                .singleResult();
            if (taskIns == null) {
                throw new ServiceException("没有可办理的任务！");
            }
            String formKey = null;
            FlowElement flowElement = ModelUtils.getFlowElementById(bpmnModel, taskIns.getTaskDefinitionKey());
            if (flowElement != null) {
                formKey = ModelUtils.getFormKey(flowElement);
            }
            // 当前节点没有 formKey 时，尝试从开始节点获取（自定义表单可能配置在开始节点上）
            if (StringUtils.isBlank(formKey)) {
                StartEvent startEvent = ModelUtils.getStartEvent(bpmnModel);
                if (startEvent != null) {
                    formKey = ModelUtils.getFormKey(startEvent);
                }
            }
            detailVo.setTaskFormData(currTaskFormData(historicProcIns.getDeploymentId(), taskIns, formKey, dataId));
        }

        //当前任务信息
        List<Task> listtask = taskService.createTaskQuery().processInstanceId(procInsId).list();

        if(listtask != null && listtask.size() > 0) {
        	for(Task task: listtask) {
        		CurNodeInfoVo curNodeInfo = new CurNodeInfoVo();
        		curNodeInfo.setProcDefName(historicProcIns.getProcessDefinitionName());
                curNodeInfo.setProcDefVersion("V"+historicProcIns.getProcessDefinitionVersion().toString());
                curNodeInfo.setProcInsId(procInsId);
                curNodeInfo.setTaskId(task.getId());
                curNodeInfo.setAssignee(task.getAssignee());
                curNodeInfo.setReceiveTime(task.getCreateTime());
                listCurNodeInfo.add(curNodeInfo);
        	}
        }
        detailVo.setListCurNodeInfo(listCurNodeInfo);
        detailVo.setBpmnXml(bpmnXmlStr);
        detailVo.setHistoryProcNodeList(historyProcNodeList(historicProcIns));
        detailVo.setProcessFormList(processFormList(bpmnModel, historicProcIns, dataId));
        detailVo.setFlowViewer(getFlowViewer(bpmnModel, procInsId));
        return detailVo;
    }

    /**
     * 启动流程实例
     */
    @Transactional(rollbackFor = Exception.class)
    private R startProcess(ProcessDefinition procDef, Map<String, Object> variables) {
        if (ObjectUtil.isNotNull(procDef) && procDef.isSuspended()) {
            throw new ServiceException("流程已被挂起，请先激活流程");
        }   
        // 设置流程发起人Id到流程中,包括变量
        String userStr = TaskUtils.getUserName();
        SysUserVo sysUsr = sysUserService.selectUserByUserName(userStr);
 		setFlowVariables(sysUsr,variables);	
 		
 		Map<String, Object> variablesnew = variables;
 		Map<String, Object> usermap = new HashMap<String, Object>();
        List<String> userlist = new ArrayList<String>();
        boolean bparallelGateway = false;
        boolean bapprovedEG = false;
        
        //业务数据id
        Object objdataId = variables.get("dataId");
        String dataId = "";
        if(ObjectUtils.isNotEmpty(objdataId)) {
        	dataId = objdataId.toString();
        }
        if(StringUtils.isNotEmpty(dataId)) {//自定义业务表单
        	//设置自定义表单dataid的数据 
            WfMyBusiness flowmybusiness = wfMyBusinessServiceImpl.getByDataId(variables.get("dataId").toString());
            String serviceImplName = flowmybusiness.getServiceImplName();
            WfCallBackServiceI flowCallBackService = (WfCallBackServiceI) SpringContextHolder.getBean(serviceImplName);
            if (flowCallBackService!=null){
              Object businessDataById = flowCallBackService.getBusinessDataById(variables.get("dataId").toString());
              if (businessDataById != null) {
                  // Flowable 变量必须可序列化（以及深层字段也需可序列化）。这里统一转成 JSON(Map) 放入流程变量，避免实体对象/嵌套对象导致启动失败。
                  variables.put("formData", JSON.parseObject(JSON.toJSONString(businessDataById), JSONObject.class));
              }
            }
        }
        
        // remove by nbacheng
        /*System.out.print("流程描述="+procDef.getDescription()); 
        String definitionld = procDef.getId();        
        BpmnModel bpmnModel = repositoryService.getBpmnModel(definitionld);//获取bpm（模型）对象
        // 获取开始节点
        StartEvent startEvent = FlowableUtils.getStartEvent(bpmnModel);
        System.out.print("开始节点描述="+startEvent.getDocumentation());*/
        // remove by nbacheng
        
        //获取下个节点信息
        getNextFlowInfo(procDef, variablesnew, usermap, variables, userlist);
        
        //取出两个特殊的变量
        if(variablesnew.containsKey("bparallelGateway")) {//并行网关
        	bparallelGateway = (boolean) variablesnew.get("bparallelGateway");
        	variablesnew.remove("bparallelGateway");
        }
        if(variablesnew.containsKey("bapprovedEG")) {//通用拒绝同意排它网关
        	bapprovedEG = (boolean) variablesnew.get("bapprovedEG");
        	variablesnew.remove("bapprovedEG");
        }
      
        // 发起流程实例
        // 发起流程实例：后续若出现异常，尽量清理已启动的流程实例，避免出现“业务回滚但流程遗留待办”
        ProcessInstance processInstance = null;
        try {
            // 必须按「流程定义ID」启动：避免同 key 多版本时启动到 latestVersion，导致“选了流程A却跑到流程B/节点不一致/秒通过”等问题
            processInstance = runtimeService.startProcessInstanceById(procDef.getId(), dataId, variables);
        
        /*if (StringUtils.isEmpty(dataId)) {
        	WfDeployForm deployForm = getDeployForm(procDef.getId(),processInstance.getDeploymentId());
	        String tableName = deployForm.getTableName();
	        if(StringUtils.isNotEmpty(tableName)) {//VForm3表单同时也保存到数据库里
	        	try {
					insertVForm3Db(tableName,variables);
				} catch (SQLException e) {
					throw new RuntimeException(e.getMessage());
				}
	        }
        }*/
        
        
        // 第一个用户任务为发起人，则自动完成任务
        //wfTaskService.startFirstTask(processInstance, variables);
        R<Void> result = setNextAssignee(processInstance, usermap, userlist, sysUsr, variables, bparallelGateway, bapprovedEG);
        // setNextAssignee 返回失败时，必须抛异常触发补偿清理（否则会出现“业务回滚但流程遗留待办/重复待办”）
        if (!R.isSuccess(result)) {
            throw new ServiceException(result.getMsg());
        }
        if(StringUtils.isNotEmpty(dataId)) {//自定义业务表单
        	// 流程发起后的自定义业务更新-需要考虑两种情况，第一个发起人审批或跳过
            List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().list();
            /*======================todo 启动之后  回调以及关键数据保存======================*/
            //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
            	LoginUser sysUser = commonService.getLoginUser();
            	if(tasks!=null && !tasks.isEmpty()) {
            	 	List <String> listUser = new ArrayList<String>();
            	 	List <String> listId = new ArrayList<String>();
            	 	List <String> listName = new ArrayList<String>();
            	 	String taskUser = "";
            	 	String taskid = "";
            	 	String taskName = "";
            	 	int taskPriority = 0;
            	 	for(Task task : tasks) {
            	 		if(task.getAssignee() != null) {
                		// todoUsers 字段存 username（不是昵称），用于后端按用户名精准过滤/定位
                		listUser.add(task.getAssignee());
                	}
            	 		listId.add(task.getId());
            	 		listName.add(task.getName());
            	 	    taskPriority = task.getPriority();
            	 	}
            	 	taskUser = listUser.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.joining(","));
            	 	taskid = listId.stream().map(String::valueOf).collect(Collectors.joining(","));
            	 	taskName = listName.stream().map(String::valueOf).collect(Collectors.joining(","));
            	
            	WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
    	        business.setProcessDefinitionId(procDef.getId());
    	        business.setProcessInstanceId(processInstance.getProcessInstanceId());
    	        business.setActStatus(ActStatus.doing);
    	        business.setProposer(sysUser.getUsername());
    	        business.setTaskId(taskid);
    	        business.setTaskName(taskName);
    	        business.setTaskNameId(taskid);
    	        business.setPriority(String.valueOf(taskPriority));
    	        business.setDoneUsers("");
    	        business.setTodoUsers(taskUser);
    	        wfMyBusinessService.updateById(business);
    	        //spring容器类名
    	        String serviceImplNameafter = business.getServiceImplName();
    	        WfCallBackServiceI flowCallBackServiceafter = (WfCallBackServiceI) SpringContextHolder.getBean(serviceImplNameafter);
    	        // 流程处理完后，进行回调业务层
    	        business.setValues(variables);
    	        if (flowCallBackServiceafter!=null)flowCallBackServiceafter.afterFlowHandle(business);
            }
            else {
                // 没有任何待办任务：通常意味着流程被配置为“无审批/自动跳过”并直接结束
                // ERP 单据审批场景可通过 variables.requireTaskOnStart=true 强制校验，避免“提交即通过”
                Object requireObj = variables.get("requireTaskOnStart");
                boolean requireTaskOnStart = Boolean.TRUE.equals(requireObj) || "true".equalsIgnoreCase(String.valueOf(requireObj));
                if (requireTaskOnStart) {
                    throw new ServiceException("流程启动后未生成待办审批任务，请检查流程模板是否包含审批节点并配置候选人/候选组");
                }
            	WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
    	        business.setProcessDefinitionId(procDef.getId());
    	        business.setProcessInstanceId(processInstance.getProcessInstanceId());
    	        business.setActStatus(ActStatus.pass);
    	        business.setProposer(sysUser.getUsername());
    	        business.setTaskId("");
    	        business.setTaskName("");
    	        business.setTaskNameId("");
    	        business.setDoneUsers("");
    	        business.setTodoUsers("");
    	        wfMyBusinessService.updateById(business);
    	        //spring容器类名
    	        String serviceImplNameafter = business.getServiceImplName();
    	        WfCallBackServiceI flowCallBackServiceafter = (WfCallBackServiceI) SpringContextHolder.getBean(serviceImplNameafter);
    	        // 流程处理完后，进行回调业务层
    	        business.setValues(variables);
    	        if (flowCallBackServiceafter!=null)flowCallBackServiceafter.afterFlowHandle(business);
            }
        }
        return result;
        } catch (Exception e) {
            // 尽最大可能清理已创建的流程实例与业务关联，避免产生“孤儿待办”
            try {
                if (processInstance != null) {
                    runtimeService.deleteProcessInstance(processInstance.getProcessInstanceId(), "startProcess failed");
                }
            } catch (Exception ignore) {
            }
            try {
                if (StringUtils.isNotEmpty(dataId)) {
                    wfMyBusinessServiceImpl.remove(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfMyBusiness>()
                        .eq(WfMyBusiness::getDataId, dataId));
                }
            } catch (Exception ignore) {
            }
            throw e;
        } finally {
            // 避免线程复用导致身份污染
            try {
                identityService.setAuthenticatedUserId(null);
            } catch (Exception ignore) {
            }
        }
    }
    
    /**
	 * 保存VForm3数据到数据库里
	 *  add by nbacheng
	 *           
	 * @param tableName
	 *            
	 * @return
	 */
    private void insertVForm3Db(String tableName, Map<String, Object> variables) throws SQLException {
    	//考虑到减掉2个与增加一个
    	String[] columns = new String[variables.size()-1];
    	Object[] values = new Object[variables.size()-1];
    	// 使用EntrySet对variables进行遍历
        int n = 0;
        columns[n] = "id";
        long timestamp = System.currentTimeMillis();
        values[n] = timestamp;
        n++;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            //下面两种数据不是表单数据，忽略掉
            if (StringUtils.equalsIgnoreCase(key, "initiator") || StringUtils.equalsIgnoreCase(key, "processStatus")) {
            	break;
            }
            Object value = entry.getValue();
            columns[n] = key;
            values[n] = value;
            n++;
        }
        DataSource dataSource = SpringContextHolder.getApplicationContext().getBean(DataSource.class);
		Connection connection = dataSource.getConnection();
        try {
            // 构建SQL语句，使用参数占位符（?）
            StringBuilder sqlBuilder = new StringBuilder("INSERT INTO " + tableName +" (");
            for (int i = 0; i < columns.length; i++) {
                sqlBuilder.append(columns[i]);
                if (i < columns.length - 1) {
                    sqlBuilder.append(", ");
                }
            }
            sqlBuilder.append(") VALUES (");
            for (int i = 0; i < columns.length; i++) {
                sqlBuilder.append("?");
                if (i < columns.length - 1) {
                    sqlBuilder.append(", ");
                }
            }
            sqlBuilder.append(")");
            String sql = sqlBuilder.toString();

            PreparedStatement preparedStatement = connection.prepareStatement(sql);

            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof String) {
                    preparedStatement.setString(i + 1, (String) value);
                } else if (value instanceof Integer) {
                    preparedStatement.setInt(i + 1, (Integer) value);
                } else if (value instanceof Boolean) {
                    preparedStatement.setInt(i + 1, (Boolean) value? 1:0);
                } else if (value instanceof Double) {
                    preparedStatement.setDouble(i + 1, (Double) value);
                } else if (value instanceof Float) {
                    preparedStatement.setFloat(i + 1, (Float) value);
                } else if (value instanceof Long) {
                    preparedStatement.setLong(i + 1, (Long) value);
                } else if (value instanceof java.sql.Date) {
                    preparedStatement.setDate(i + 1, (java.sql.Date) value);
                } else if (value instanceof ArrayList) {
                	ArrayList<String> list = (ArrayList) value;
                	//去掉[]
                	String stringValue = (list.toString()).replaceAll("\\[|\\]", "");
                    preparedStatement.setString(i + 1, stringValue);
                } else {
                    // 如果遇到其他不支持的类型，可以根据需要进行扩展或者抛出异常提示
                    throw new IllegalArgumentException("不支持的数据类型: " + value.getClass().getName());
                }
            }

            int rowsInserted = preparedStatement.executeUpdate();
            if (rowsInserted > 0) {
                System.out.println("数据插入成功");
            }

        } catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		} finally {
            connection.close();
        }
    }
    
    /**
     * 获取字符串的类型
     *
     * @param type 数据类型
     * @return 结果
     */
    private String getDataType(Object obj) {
    	if (obj instanceof String) {
            return "String";
        } else if (obj instanceof Integer) {
            return "Integer";
        } else if (obj instanceof Double) {
            return "Double";
        } else if (obj instanceof Float) {
            return "Float";
        } else if (obj instanceof Date) {
            return "date";
        }else {
            // 使用getClass().getSimpleName()获取类名
            return obj.getClass().getSimpleName();
        }
    }
    
    /**
     * 获取字符串的类型
     *
     * @param type 数据类型
     * @return 结果
     */
    private String getType(String str) {
        try {
            Integer.parseInt(str);
            return "int";
        } catch (NumberFormatException e) {
            try {
                Double.parseDouble(str);
                return "double";
            } catch (NumberFormatException ex) {
                return "string";
            }
        }
    }
    
    /**
	 * 设置下个节点信息处理人员
	 *  add by nbacheng
	 *           
	 * @param variablesnew, usermap,
	 *		  userlist, sysUser, variables,  bparallelGateway
	 *            
	 * @return
	 */
	private R<Void> setNextAssignee(ProcessInstance processInstance, Map<String, Object> usermap,
			                       List<String> userlist, SysUserVo sysUser, Map<String, Object> variables,
			                       boolean bparallelGateway, boolean bapprovedEG) {
		// 给第一步申请人节点设置任务执行人和意见
		if((usermap.containsKey("isSequential")) && !(boolean)usermap.get("isSequential")) {//并发会签会出现2个以上需要特殊处理
			List<Task> nexttasklist = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().list();
			  int i=0;
			  for (Task nexttask : nexttasklist) {
				   String assignee = userlist.get(i).toString();	
				   taskService.addComment(nexttask.getId(), processInstance.getProcessInstanceId(),
							FlowComment.NORMAL.getType(), sysUser.getNickName() + "发起流程申请");
			       taskService.setAssignee(nexttask.getId(), assignee);
			       i++;
			  }
			  return R.ok("多实例会签流程启动成功.");
 	    }
		else {// 给第一步申请人节点设置任务执行人和意见
			Task task = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active()
				.singleResult();
			if (Objects.isNull(task)) {
				// No active task created after start; ERP can set requireTaskOnStart=true to enforce validation.
				Object requireObj = variables == null ? null : variables.get("requireTaskOnStart");
				boolean requireTaskOnStart = Boolean.TRUE.equals(requireObj) || "true".equalsIgnoreCase(String.valueOf(requireObj));
				if (requireTaskOnStart) {
					throw new ServiceException("流程启动后未生成待办审批任务，请检查流程模板是否包含审批节点并配置候选人/候选组");
				}
				return R.ok("流程启动成功.");
			}
			if (Objects.nonNull(task)) {
				taskService.addComment(task.getId(), processInstance.getProcessInstanceId(),
						FlowComment.NORMAL.getType(), sysUser.getNickName() + "发起流程申请");
				taskService.setAssignee(task.getId(), sysUser.getUserName());
			}

			// 获取下一个节点数据及设置数据

			FlowNextDto	nextFlowNode = wfTaskService.getNextFlowNode(task.getId(), variables);
			if(Objects.nonNull(nextFlowNode)) {
				if (Objects.nonNull(task)) {
					Map<String, Object> nVariablesMap = taskService.getVariables(task.getId());
					if(nVariablesMap.containsKey("SetAssigneeTaskListener")) {//是否通过动态设置审批人的任务监听器
					  taskService.complete(task.getId(), variables);
					  Task nexttask = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().singleResult();
					  taskService.setAssignee(nexttask.getId(), nVariablesMap.get("SetAssigneeTaskListener").toString());
					  return R.ok("通过动态设置审批人的任务监听器流程启动成功.");
				    }
					if(nVariablesMap.containsKey("SetDeptHeadTaskListener")) {//是否通过动态设置发起人部门负责人的任务监听器
						  taskService.complete(task.getId(), variables);
						  Task nexttask = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().singleResult();
						  if(Objects.nonNull(nexttask)) {
							  if(Objects.nonNull((List<String>) nVariablesMap.get("SetDeptHeadTaskListener"))) {
								  if(((List<String>) nVariablesMap.get("SetDeptHeadTaskListener")).size() == 1) {//是否就一个人
									  taskService.setAssignee(nexttask.getId(), ((List<String>)nVariablesMap.get("SetDeptHeadTaskListener")).get(0).toString());
							          return R.ok("设置发起人部门负责人的任务监听器流程启动成功.");
								  }
								  else {
									  for (String username : ((List<String>) nVariablesMap.get("SetDeptHeadTaskListener"))) {
	        							  taskService.addCandidateUser(nexttask.getId(), username);
	        						  }
						             return R.ok("设置多个发起人部门负责人的任务监听器流程启动成功,目前用户可通过签收方式完成审批."); 
								  }
								  
							  }
							
						  }
						  
					}
				}
				if(Objects.nonNull(nextFlowNode.getUserList())) {
					if( nextFlowNode.getUserList().size() == 1 ) {
						if (nextFlowNode.getUserList().get(0) != null) {
							if(StringUtils.equalsAnyIgnoreCase(nextFlowNode.getUserList().get(0).getUserName(), "${initiator}")) {//对发起人做特殊处理
								//System.out.print("任务描述="+task.getDescription()); //remove by nbacheng
								taskService.complete(task.getId(), variables);
								return R.ok("流程启动成功给发起人.");
							}
							else if(nextFlowNode.getUserTask().getCandidateUsers().size()>0 && StringUtils.equalsAnyIgnoreCase(nextFlowNode.getUserTask().getCandidateUsers().get(0), "${DepManagerHandler.getUsers(execution)}")) {//对部门经理做特殊处理								
								//taskService.complete(task.getId(), variables);
								return R.ok("流程启动成功给部门经理,请到我的待办里进行流程的提交流转.");
							}
							else {
								taskService.complete(task.getId(), variables);
							    return R.ok("流程启动成功.");
							}
						}
						else {
							return R.fail("审批人不存在，流程启动失败!");
						}
						
					}
					else if(nextFlowNode.getType() == ProcessConstants.PROCESS_MULTI_INSTANCE ) {//对多实例会签做特殊处理或者以后在流程设计进行修改也可以
		                Map<String, Object> approvalmap = new HashMap<>();
		                List<String> sysuserlist = nextFlowNode.getUserList().stream().map(obj-> (String) obj.getUserName()).collect(Collectors.toList());
						approvalmap.put("approval", sysuserlist);
						taskService.complete(task.getId(), approvalmap);
						if(!nextFlowNode.isBisSequential()){//对并发会签进行assignee单独赋值
		  				  List<Task> nexttasklist = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().list();
		  				  int i=0;
		  				  for (Task nexttask : nexttasklist) {
		  					   String assignee = sysuserlist.get(i).toString();	
		      			       taskService.setAssignee(nexttask.getId(), assignee);
		      			       i++;
		  				  }
		  				 
		  				}
						return R.ok("多实例会签流程启动成功.");
					}
					else if(nextFlowNode.getUserList().size() > 1) {
						if (bparallelGateway) {//后一个节点是并行网关的话
							taskService.complete(task.getId(), variables);
							return R.ok("流程启动成功.");
						}
						else {
							return R.ok("流程启动成功,请到我的待办里进行流程的提交流转.");
						}
					}
					else {
						return R.ok("流程启动失败,请检查流程设置人员！");
					}
				}
				else {//对跳过流程做特殊处理
					List<UserTask> nextUserTask = FindNextNodeUtil.getNextUserTasks(repositoryService, task, variables);
		            if (CollectionUtils.isNotEmpty(nextUserTask)) {
		            	List<FlowableListener> listlistener = nextUserTask.get(0).getTaskListeners();
		            	if(CollectionUtils.isNotEmpty(listlistener)) {
		            		String tasklistener =  listlistener.get(0).getImplementation();
		            		if(StringUtils.contains(tasklistener, "AutoSkipTaskListener")) {
			            		taskService.complete(task.getId(), variables);
			    				return R.ok("流程启动成功.");
			            	}else {
				            	return R.ok("流程启动失败,请检查流程设置人员！");
				            }
		            	}else {
			            	return R.ok("流程启动失败,请检查流程设置人员！");
			            }
		            	
		            }
		            else {
		            	return R.ok("流程启动失败,请检查流程设置人员！");
		            }
				}
			}
			else {
				if(bapprovedEG) {
					return R.ok("通用拒绝同意流程启动成功,请到我的待办里进行流程的提交流转.");
				}
				taskService.complete(task.getId(), variables);
				return R.ok("流程启动成功.");
			}
		}
	}

	/**
	 * 设置发起人变量
	 *  add by nbacheng
	 *           
	 * @param variables
	 *            流程变量
	 * @return
	 */
	private void setFlowVariables(SysUserVo sysUser,Map<String, Object> variables) {
		 // 设置流程发起人Id到流程中
        identityService.setAuthenticatedUserId(sysUser.getUserName());
        variables.put(BpmnXMLConstants.ATTRIBUTE_EVENT_START_INITIATOR, sysUser.getUserName());
        // 设置流程状态为进行中
        variables.put(ProcessConstants.PROCESS_STATUS_KEY, ProcessStatus.RUNNING.getStatus());
     // 设置流程状态为进行中
        variables.put(ProcessConstants.PROCESS_STATUS_KEY, ProcessStatus.RUNNING.getStatus());
	}
    
    /**
	 * 获取下个节点信息,对并行与排它网关做处理
	 *  add by nbacheng
	 *           
	 * @param processDefinition, variablesnew, usermap,
			   variables, userlist, bparallelGateway
	 *           
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private void getNextFlowInfo(ProcessDefinition processDefinition, Map<String, Object> variablesnew, Map<String, Object> usermap,
			                     Map<String, Object> variables, List<String> userlist) {
		String definitionld = processDefinition.getId();        //获取bpm（模型）对象
        BpmnModel bpmnModel = repositoryService.getBpmnModel(definitionld);
        //传节点定义key获取当前节点
        List<org.flowable.bpmn.model.Process> processes =  bpmnModel.getProcesses();
        //只处理发起人后面排它网关再后面是会签的情况，其它目前不考虑
        //List<UserTask> userTasks = process.findFlowElementsOfType(UserTask.class);
        List<FlowNode> flowNodes = processes.get(0).findFlowElementsOfType(FlowNode.class);
        List<SequenceFlow> outgoingFlows = flowNodes.get(1).getOutgoingFlows();
        //遍历返回下一个节点信息
        for (SequenceFlow outgoingFlow : outgoingFlows) {
            //类型自己判断（获取下个节点是网关还是节点）
            FlowElement targetFlowElement = outgoingFlow.getTargetFlowElement();
            //下个是节点
           if(targetFlowElement instanceof ExclusiveGateway){// 下个出口是排它网关的话,后一个用户任务又是会签的情况下需要approval的赋值处理，否则报错
        	   usermap =  GetExclusiveGatewayUser(targetFlowElement,variables);//还是需要返回用户与是否并发，因为并发要做特殊处理
        	   if(usermap != null) {
        		 userlist = (ArrayList<String>) usermap.get("approval");
        	     variablesnew.put("approval", userlist);
        	   }
        	   if(FindNextNodeUtil.GetExclusiveGatewayExpression(targetFlowElement)) {//下个出口是通用拒绝同意排它网关
        		   variablesnew.put("bapprovedEG",true);
        	   }
        	   break;
            }
           if(targetFlowElement instanceof ParallelGateway){// 下个出口是并行网关的话,直接需要进行complete，否则报错
        	   variablesnew.put("bparallelGateway",true);
           }
        }
	}

	/**
     * 获取排他网关分支名称、分支表达式、下一级任务节点
     * @param flowElement
     * @param data
     * add by nbacheng
     */
    private Map<String, Object> GetExclusiveGatewayUser(FlowElement flowElement,Map<String, Object> variables){
    	// 获取所有网关分支
        List<SequenceFlow> targetFlows=((ExclusiveGateway)flowElement).getOutgoingFlows();
        // 循环每个网关分支
        for(SequenceFlow sequenceFlow : targetFlows){
            // 获取下一个网关和节点数据
            FlowElement targetFlowElement=sequenceFlow.getTargetFlowElement();
            // 网关数据不为空
            if (StringUtils.isNotBlank(sequenceFlow.getConditionExpression())) {
                // 获取网关判断条件
            	String expression = sequenceFlow.getConditionExpression();
                if (expression == null ||Boolean.parseBoolean(
                                String.valueOf(
                                		FindNextNodeUtil.result(variables, expression.substring(expression.lastIndexOf("{") + 1, expression.lastIndexOf("}")))))) {
                	// 网关出线的下个节点是用户节点
                    if(targetFlowElement instanceof UserTask){
                        // 判断是否是会签
                        UserTask userTask = (UserTask) targetFlowElement;
                        MultiInstanceLoopCharacteristics multiInstance = userTask.getLoopCharacteristics();
                    	if (Objects.nonNull(multiInstance)) {//下个节点是会签节点
                    		Map<String, Object> approvalmap = new HashMap<>();
                    		List<String> getuserlist =  getmultiInstanceUsers(multiInstance,userTask);
                    		approvalmap.put("approval", getuserlist);
                    		if(multiInstance.isSequential()) {
                    			approvalmap.put("isSequential", true);
                    		}
                    		else {
                    			approvalmap.put("isSequential", false);
                    		}
                    		return approvalmap;
                    	}
                    }
                }
            }
        }
		return null;
    }
    
    /**
     * 获取多实例会签用户信息
     * @param userTask
     * @param multiInstance
     *
     **/
    List<String> getmultiInstanceUsers(MultiInstanceLoopCharacteristics multiInstance,UserTask userTask) {
    	List<String> sysuserlist = new ArrayList<>();
    	List<String> rolelist = new ArrayList<>();
        rolelist = userTask.getCandidateGroups();
    	List<String> userlist = new ArrayList<>();
        userlist = userTask.getCandidateUsers();
        if(rolelist.size() > 0) {
        	List<SysUserVo> list = new ArrayList<SysUserVo>();
			for(String roleId : rolelist ){
        	  List<SysUserVo> templist = commonService.getUserListByRoleId(roleId);
        	  for(SysUserVo sysuser : templist) {
          		SysUserVo sysUserTemp = sysUserService.selectUserById(sysuser.getUserId());
          		list.add(sysUserTemp);
          	  }
        	}
			sysuserlist = list.stream().map(obj-> (String) obj.getUserName()).collect(Collectors.toList());
           
        }
        else if(userlist.size() > 0) {
        	List<SysUserVo> list = new ArrayList<SysUserVo>();
        	for(String username : userlist) {
        		SysUserVo sysUser =  sysUserService.selectUserByUserName(username);
        		list.add(sysUser);
        	}
        	sysuserlist = list.stream().map(obj-> (String) obj.getUserName()).collect(Collectors.toList());
        }    
    	return sysuserlist;
    }

    /**
     * 获取流程变量
     *
     * @param taskId 任务ID
     * @return 流程变量
     */
    private Map<String, Object> getProcessVariables(String taskId) {
        HistoricTaskInstance historicTaskInstance = historyService.createHistoricTaskInstanceQuery()
            .includeProcessVariables()
            .finished()
            .taskId(taskId)
            .singleResult();
        if (Objects.nonNull(historicTaskInstance)) {
            return historicTaskInstance.getProcessVariables();
        }
        return taskService.getVariables(taskId);
    }

    /**
     * 获取当前任务流程表单信息
     */
    private FormConf currTaskFormData(String deployId, HistoricTaskInstance taskIns, String formKey, String dataId) {
        LambdaQueryWrapper<WfDeployForm> lqw = new LambdaQueryWrapper<WfDeployForm>()
            .eq(WfDeployForm::getDeployId, deployId);
        if (StringUtils.isNotBlank(formKey)) {
            lqw.eq(WfDeployForm::getFormKey, formKey);
        } else {
            lqw.eq(WfDeployForm::getNodeKey, taskIns.getTaskDefinitionKey());
        }
        List<WfDeployFormVo> formList = deployFormMapper.selectVoList(lqw);
        WfDeployFormVo deployFormVo = formList.stream().findFirst().orElse(null);
        if (ObjectUtil.isNotEmpty(deployFormVo)) {
            Map<String, Object> formModel = JsonUtils.parseObject(deployFormVo.getContent(), Map.class);
            if (null != formModel && !formModel.isEmpty()) {
                FormConf currTaskFormData = new FormConf();
                currTaskFormData.setFormBtns(false);
                currTaskFormData.setFormModel(formModel);
                Map<String, Object> formData = taskIns.getTaskLocalVariables();
                if (formData == null || formData.isEmpty()) {
                    Map<String, Object> procVars = taskIns.getProcessVariables();
                    if (procVars != null) {
                        Object fd = procVars.get("formData");
                        if (fd instanceof Map) {
                            formData = (Map<String, Object>) fd;
                        } else {
                            Object bizData = loadBusinessData(dataId);
                            if (bizData instanceof Map) {
                                formData = (Map<String, Object>) bizData;
                            } else {
                                formData = procVars;
                            }
                        }
                    }
                }
                currTaskFormData.setFormData(formData);
                return currTaskFormData;
            } else {
                // content 为空时，按自定义业务表单处理：通过 routeName 匹配前端 Vue 组件
                Object bizFormData = loadBusinessData(dataId);
                if (bizFormData != null) {
                    try {
                        WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
                        if (business != null && StringUtils.isNotBlank(business.getRouteName())) {
                            FormConf formConf = new FormConf();
                            formConf.setFormBtns(false);
                            Map<String, Object> formValues = new HashMap<>();
                            formValues.put("formData", bizFormData);
                            formValues.put("routeName", business.getRouteName());
                            formConf.setFormValues(formValues);
                            return formConf;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        // 未命中 wf_deploy_form 时仍按 dataId 构造自定义业务表单，与 processFormList 尾部逻辑对齐，避免 existTaskForm 恒为 false
        if (StringUtils.isNotBlank(dataId)) {
            try {
                Object bizFormData = loadBusinessData(dataId);
                if (bizFormData != null) {
                    WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
                    if (business != null && StringUtils.isNotBlank(business.getRouteName())) {
                        FormConf formConf = new FormConf();
                        formConf.setFormBtns(false);
                        Map<String, Object> formValues = new HashMap<>();
                        formValues.put("formData", bizFormData);
                        formValues.put("routeName", business.getRouteName());
                        formConf.setFormValues(formValues);
                        return formConf;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Object loadBusinessData(String dataId) {
        if (StringUtils.isEmpty(dataId)) {
            return null;
        }
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
            if (business != null) {
                String serviceImplName = business.getServiceImplName();
                WfCallBackServiceI callBackService = (WfCallBackServiceI) SpringContextHolder.getBean(serviceImplName);
                if (callBackService != null) {
                    return callBackService.getBusinessDataById(dataId);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 获取历史流程表单信息
     */
    private List<FormConf> processFormList(BpmnModel bpmnModel, HistoricProcessInstance historicProcIns, String dataId) {
        List<FormConf> procFormList = new ArrayList<>();
        // 业务表单数据：用于补齐/覆盖流程变量中的 formData（历史实例可能未保存子表，或变量结构不兼容前端渲染）
        Object bizFormData = null;
        if (StringUtils.isNotEmpty(dataId)) {
            try {
                WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
                if (ObjectUtils.isNotEmpty(business)) {
                    String serviceImplName = business.getServiceImplName();
                    WfCallBackServiceI flowCallBackService = (WfCallBackServiceI) SpringContextHolder.getBean(serviceImplName);
                    if (flowCallBackService != null) {
                        bizFormData = flowCallBackService.getBusinessDataById(dataId);
                    }
                }
            } catch (Exception ignore) {
            }
        }

        List<HistoricActivityInstance> activityInstanceList = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(historicProcIns.getId()).finished()
            .activityTypes(CollUtil.newHashSet(BpmnXMLConstants.ELEMENT_EVENT_START, BpmnXMLConstants.ELEMENT_TASK_USER))
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();
        List<String> processFormKeys = new ArrayList<>();
        for (HistoricActivityInstance activityInstance : activityInstanceList) {
            // 获取当前节点流程元素信息
            FlowElement flowElement = ModelUtils.getFlowElementById(bpmnModel, activityInstance.getActivityId());
            // 获取当前节点表单Key
            String formKey = ModelUtils.getFormKey(flowElement);
            if (formKey == null) {
                continue;
            }
            boolean localScope = Convert.toBool(ModelUtils.getElementAttributeValue(flowElement, ProcessConstants.PROCESS_FORM_LOCAL_SCOPE), false);
            Map<String, Object> variables;
            if (localScope) {
                // 查询任务节点参数，并转换成Map
                variables = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(historicProcIns.getId())
                    .taskId(activityInstance.getTaskId())
                    .list()
                    .stream()
                    .collect(Collectors.toMap(HistoricVariableInstance::getVariableName, HistoricVariableInstance::getValue));
            } else {
                if (processFormKeys.contains(formKey)) {
                    continue;
                }
                variables = historicProcIns.getProcessVariables();
                processFormKeys.add(formKey);
            }  
           
            Map<String, Object> formvariables = new HashedMap<String, Object>();
 
            // 非节点表单此处查询结果可能有多条，只获取第一条信息
            List<WfDeployFormVo> formInfoList = deployFormMapper.selectVoList(new LambdaQueryWrapper<WfDeployForm>()
                .eq(WfDeployForm::getDeployId, historicProcIns.getDeploymentId())
                .eq(WfDeployForm::getFormKey, formKey)
                .eq(localScope, WfDeployForm::getNodeKey, flowElement.getId()));

            //@update by Brath：避免空集合导致的NULL空指针
            WfDeployFormVo formInfo = formInfoList.stream().findFirst().orElse(null);
         
            if (ObjectUtil.isNotNull(formInfo)) {
                // 旧数据 formInfo.getFormName() 为 null
                String formName = Optional.ofNullable(formInfo.getFormName()).orElse(StringUtils.EMPTY);
                String title = localScope ? formName.concat("(" + flowElement.getName() + ")") : formName;
                FormConf formConf = new FormConf();
                Map<String, Object> formModel = JsonUtils.parseObject(formInfo.getContent(), Map.class);
                if (null != formModel && !formModel.isEmpty()) {
                    formConf.setTitle(title);
                    formConf.setDisabled(true);
                    formConf.setFormBtns(false);
                    formConf.setFormModel(formModel);
                    // 对业务表单：优先使用流程变量中的 formData（否则前端拿到的是“外层变量”，子表通常在 formData 内导致无法回填）
                    Object fd = variables == null ? null : variables.get("formData");
                    if (fd == null && bizFormData != null) {
                        fd = bizFormData;
                    }
                    if (fd instanceof Map) {
                        //noinspection unchecked
                        formConf.setFormData((Map<String, Object>) fd);
                    } else {
                        formConf.setFormData(variables);
                    }
                    procFormList.add(formConf);
                }
            }
        }
        if(StringUtils.isNoneEmpty(dataId) && bizFormData != null) {
            try {
                WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(dataId);
                if(ObjectUtils.isNotEmpty(business)) {
                    Map<String, Object> customMap = new HashMap<String, Object>();
                    FormConf formConf = new FormConf();
                    customMap.put("formData", bizFormData);
                    customMap.put("routeName", business.getRouteName());
                    formConf.setFormValues(customMap);
                    procFormList.add(formConf);
                }
            } catch (Exception ignore) {
            }
        }
        return procFormList;
    }

    @Deprecated
    private void buildStartFormData(HistoricProcessInstance historicProcIns, Process process, String deployId, List<FormConf> procFormList) {
        procFormList = procFormList == null ? new ArrayList<>() : procFormList;
        HistoricActivityInstance startInstance = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(historicProcIns.getId())
            .activityId(historicProcIns.getStartActivityId())
            .singleResult();
        StartEvent startEvent = (StartEvent) process.getFlowElement(startInstance.getActivityId());
        WfDeployFormVo startFormInfo = deployFormMapper.selectVoOne(new LambdaQueryWrapper<WfDeployForm>()
            .eq(WfDeployForm::getDeployId, deployId)
            .eq(WfDeployForm::getFormKey, startEvent.getFormKey())
            .eq(WfDeployForm::getNodeKey, startEvent.getId()));
        if (ObjectUtil.isNotNull(startFormInfo)) {
            FormConf formConf = JsonUtils.parseObject(startFormInfo.getContent(), FormConf.class);
            if (null != formConf) {
                //formConf.setTitle(startEvent.getName());
                //formConf.setDisabled(true);
                //formConf.setFormBtns(false);
                ProcessFormUtils.fillFormData(formConf, historicProcIns.getProcessVariables());
                procFormList.add(formConf);
            }
        }
    }

    @Deprecated
    private void buildUserTaskFormData(String procInsId, String deployId, Process process, List<FormConf> procFormList) {
        procFormList = procFormList == null ? new ArrayList<>() : procFormList;
        List<HistoricActivityInstance> activityInstanceList = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(procInsId).finished()
            .activityType(BpmnXMLConstants.ELEMENT_TASK_USER)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();
        for (HistoricActivityInstance instanceItem : activityInstanceList) {
            UserTask userTask = (UserTask) process.getFlowElement(instanceItem.getActivityId(), true);
            String formKey = userTask.getFormKey();
            if (formKey == null) {
                continue;
            }
            // 查询任务节点参数，并转换成Map
            Map<String, Object> variables = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(procInsId)
                .taskId(instanceItem.getTaskId())
                .list()
                .stream()
                .collect(Collectors.toMap(HistoricVariableInstance::getVariableName, HistoricVariableInstance::getValue));
            WfDeployFormVo deployFormVo = deployFormMapper.selectVoOne(new LambdaQueryWrapper<WfDeployForm>()
                .eq(WfDeployForm::getDeployId, deployId)
                .eq(WfDeployForm::getFormKey, formKey)
                .eq(WfDeployForm::getNodeKey, userTask.getId()));
            if (ObjectUtil.isNotNull(deployFormVo)) {
                FormConf formConf = JsonUtils.parseObject(deployFormVo.getContent(), FormConf.class);
                if (null != formConf) {
                    //formConf.setTitle(userTask.getName());
                    //formConf.setDisabled(true);
                    //formConf.setFormBtns(false);
                    ProcessFormUtils.fillFormData(formConf, variables);
                    procFormList.add(formConf);
                }
            }
        }
    }

    /**
     * 获取历史任务信息列表
     */
    private List<WfProcNodeVo> historyProcNodeList(HistoricProcessInstance historicProcIns) {
        String procInsId = historicProcIns.getId();
        List<HistoricActivityInstance> historicActivityInstanceList =  historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(procInsId)
            .activityTypes(CollUtil.newHashSet(BpmnXMLConstants.ELEMENT_EVENT_START, BpmnXMLConstants.ELEMENT_EVENT_END, BpmnXMLConstants.ELEMENT_TASK_USER))
            .orderByHistoricActivityInstanceStartTime().desc()
            .orderByHistoricActivityInstanceEndTime().desc()
            .list();

        List<Comment> commentList = taskService.getProcessInstanceComments(procInsId);

        List<WfProcNodeVo> elementVoList = new ArrayList<>();
        for (HistoricActivityInstance activityInstance : historicActivityInstanceList) {
            WfProcNodeVo elementVo = new WfProcNodeVo();
            elementVo.setProcDefId(activityInstance.getProcessDefinitionId());
            elementVo.setActivityId(activityInstance.getActivityId());
            elementVo.setActivityName(activityInstance.getActivityName());
            elementVo.setActivityType(activityInstance.getActivityType());
            elementVo.setCreateTime(activityInstance.getStartTime());
            elementVo.setEndTime(activityInstance.getEndTime());
            if (ObjectUtil.isNotNull(activityInstance.getDurationInMillis())) {
                elementVo.setDuration(DateUtil.formatBetween(activityInstance.getDurationInMillis(), BetweenFormatter.Level.SECOND));
            }

            if (BpmnXMLConstants.ELEMENT_EVENT_START.equals(activityInstance.getActivityType())) {
                if (ObjectUtil.isNotNull(historicProcIns)) {
                    String userName = historicProcIns.getStartUserId();
                    String nickName = sysUserService.selectUserByUserName(userName).getNickName();
                    if (nickName != null) {
                        elementVo.setAssigneeId(userName);
                        elementVo.setAssigneeName(nickName);
                    }
                }
            } else if (BpmnXMLConstants.ELEMENT_TASK_USER.equals(activityInstance.getActivityType())) {
                if (StringUtils.isNotBlank(activityInstance.getAssignee())) {
                	String userName = activityInstance.getAssignee();
                    String nickName = sysUserService.selectUserByUserName(userName).getNickName();
                    elementVo.setAssigneeId(userName);
                    elementVo.setAssigneeName(nickName);
                }
                // 展示审批人员
                List<HistoricIdentityLink> linksForTask = historyService.getHistoricIdentityLinksForTask(activityInstance.getTaskId());
                StringBuilder stringBuilder = new StringBuilder();
                for (HistoricIdentityLink identityLink : linksForTask) {
                    if ("candidate".equals(identityLink.getType())) {
                        if (StringUtils.isNotBlank(identityLink.getUserId())) {
                            String userId = identityLink.getUserId();
                            String nickName = sysUserService.selectUserByUserName(userId).getNickName();
                            stringBuilder.append(nickName).append(",");
                        }
                        if (StringUtils.isNotBlank(identityLink.getGroupId())) {
                            if (identityLink.getGroupId().startsWith(TaskConstants.ROLE_GROUP_PREFIX)) {
                                Long roleId = Long.parseLong(StringUtils.stripStart(identityLink.getGroupId(), TaskConstants.ROLE_GROUP_PREFIX));
                                SysRoleVo role = roleService.selectRoleById(roleId);
                                stringBuilder.append(role.getRoleName()).append(",");
                            } else if (identityLink.getGroupId().startsWith(TaskConstants.DEPT_GROUP_PREFIX)) {
                                Long deptId = Long.parseLong(StringUtils.stripStart(identityLink.getGroupId(), TaskConstants.DEPT_GROUP_PREFIX));
                                SysDeptVo dept = deptService.selectDeptById(deptId);
                                stringBuilder.append(dept.getDeptName()).append(",");
                            }
                        }
                    }
                }
                if (StringUtils.isNotBlank(stringBuilder)) {
                    elementVo.setCandidate(stringBuilder.substring(0, stringBuilder.length() - 1));
                }
                // 获取意见评论内容
                if (CollUtil.isNotEmpty(commentList)) {
                    List<Comment> comments = new ArrayList<>();
                    for (Comment comment : commentList) {

                        if (comment.getTaskId().equals(activityInstance.getTaskId())) {
                            comments.add(comment);
                        }
                    }
                    elementVo.setCommentList(comments);
                }
            }
            elementVoList.add(elementVo);
        }
        return elementVoList;
    }

    /**
     * 获取流程执行过程
     *
     * @param procInsId
     * @return
     */
    private WfViewerVo getFlowViewer(BpmnModel bpmnModel, String procInsId) {
        // 构建查询条件
        HistoricActivityInstanceQuery query = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(procInsId);
        List<HistoricActivityInstance> allActivityInstanceList = query.list();
        if (CollUtil.isEmpty(allActivityInstanceList)) {
            return new WfViewerVo();
        }
        // 查询所有已完成的元素
        List<HistoricActivityInstance> finishedElementList = allActivityInstanceList.stream()
            .filter(item -> ObjectUtil.isNotNull(item.getEndTime())).collect(Collectors.toList());
        // 所有已完成的连线
        Set<String> finishedSequenceFlowSet = new HashSet<>();
        // 所有已完成的任务节点
        Set<String> finishedTaskSet = new HashSet<>();
        finishedElementList.forEach(item -> {
            if (BpmnXMLConstants.ELEMENT_SEQUENCE_FLOW.equals(item.getActivityType())) {
                finishedSequenceFlowSet.add(item.getActivityId());
            } else {
                finishedTaskSet.add(item.getActivityId());
            }
        });
        // 查询所有未结束的节点
        Set<String> unfinishedTaskSet = allActivityInstanceList.stream()
            .filter(item -> ObjectUtil.isNull(item.getEndTime()))
            .map(HistoricActivityInstance::getActivityId)
            .collect(Collectors.toSet());
        // DFS 查询未通过的元素集合
        Set<String> rejectedSet = FlowableUtils.dfsFindRejects(bpmnModel, unfinishedTaskSet, finishedSequenceFlowSet, finishedTaskSet);
        return new WfViewerVo(finishedTaskSet, finishedSequenceFlowSet, unfinishedTaskSet, rejectedSet);
    }

    /**
	 * 获取流程是否结束
	 *  add by nbacheng          
	 * @param String procInsId       
	 * @return
	 */
	@Override
	public boolean processIscompleted(String procInsId) {
		
		// 获取流程状态
        HistoricVariableInstance processStatusVariable = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(procInsId)
            .variableName(ProcessConstants.PROCESS_STATUS_KEY)
            .singleResult();
        if (ObjectUtil.isNotNull(processStatusVariable)) {
        	String processStatus = null;
            if (ObjectUtil.isNotNull(processStatusVariable)) {
                processStatus = Convert.toStr(processStatusVariable.getValue());
                if(StringUtils.equalsIgnoreCase(processStatus, "completed")) {
                	return true;
                }
            }
        }    
		return false;
	}

	/**
	 * 根据流程dataId,serviceName启动流程实例，主要是自定义业务表单发起流程使用
	 *  add by nbacheng
	 * @param dataId,serviceName
	 *           
	 * @param variables
	 *            流程变量
	 * @return
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public R<Void> startProcessByDataId(String dataId, String selectFlowId, String serviceName, Map<String, Object> variables) {
        //提交审批的时候进行流程实例关联初始化
    	
        if (StringUtils.isEmpty(selectFlowId)){
             return R.fail("未找到关联流程selectFlowId："+selectFlowId);
        }
        WfCustomFormVo wfCustomFormvo = wfCustomFormService.queryById(Long.valueOf(selectFlowId));
        if(ObjectUtil.isEmpty(wfCustomFormvo)){
        	return R.fail("未找到wfCustomFormvo："+selectFlowId);
        }
        //优先考虑自定义业务表是否关联流程，再看通用的表单流程关联表
        ProcessDefinition processDefinition;
        String deployId = wfCustomFormvo.getDeployId(); //临时先用一个，实际要返回list前端进行选择
        if(StringUtils.isEmpty(deployId)) {
        	WfDeployForm sysDeployForm  = deployFormMapper.selectSysDeployFormByFormId("key_"+String.valueOf(wfCustomFormvo.getId()));
            if(sysDeployForm ==null){          	
       	       return R.fail("自定义表单也没关联流程定义表,流程没定义关联自定义表单"+wfCustomFormvo.getId());
            }
            processDefinition = repositoryService.createProcessDefinitionQuery()
        		.parentDeploymentId(sysDeployForm.getDeployId()).latestVersion().singleResult();
        }
        else {
        	processDefinition = repositoryService.createProcessDefinitionQuery()
        			.processDefinitionTenantId(TenantHelper.getTenantId())
            		.parentDeploymentId(deployId).latestVersion().singleResult();
        }
        try {
	        LambdaQueryWrapper<WfMyBusiness> wfMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
	        wfMyBusinessLambdaQueryWrapper.eq(WfMyBusiness::getDataId, dataId);
	        WfMyBusiness business = wfMyBusinessService.getOne(wfMyBusinessLambdaQueryWrapper);
	        if (business==null || (business != null && business.getActStatus().equals(ActStatus.stop)) 
	        		           || (business != null && business.getActStatus().equals(ActStatus.recall))){
	        	if(processDefinition==null) {
	        		return R.fail("自定义表单也没关联流程定义表,流程没定义关联自定义表单"+wfCustomFormvo.getId());
	        	}
	        	boolean binit = wfCommonService.initActBusiness(wfCustomFormvo.getBusinessName(), dataId, serviceName, 
	        	processDefinition.getKey(), processDefinition.getId(), wfCustomFormvo.getRouteName());
	        	if(!binit) {
	        		return R.fail("自定义表单也没关联流程定义表,流程没定义关联自定义表单"+wfCustomFormvo.getId());
	        	}
	        	WfMyBusiness businessnew = wfMyBusinessService.getOne(wfMyBusinessLambdaQueryWrapper);
	           //流程实例关联初始化结束
	            if (StrUtil.isNotBlank(businessnew.getProcessDefinitionId())){
	              return this.startProcessByDefId(businessnew.getProcessDefinitionId(),variables);
	            }
	            return this.startProcessByDefKey(businessnew.getProcessDefinitionKey(),variables);
	        }
	        else {
	        	 return R.fail("已经存在这个dataid实例，不要重复申请："+dataId);
	        }
        } catch (Exception e) {
            // 保留根因，便于 ERP 侧/前端定位“提交后秒通过/未生成待办”等流程问题
            log.error("startProcessByDataId failed. dataId={}, selectFlowId={}, serviceName={}", dataId, selectFlowId, serviceName, e);
            throw new RuntimeException("发起流程失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
	}
	
	List<WfCustomForm> selectSysCustomFormByServiceName(String serviceName) {
		List<WfCustomForm> wfCustomFormList = wfCustomFormService.selectSysCustomFormByServiceName(serviceName);
		List<WfCustomForm> newCustomFormList = new ArrayList<WfCustomForm>();
		 if(ObjectUtil.isNotEmpty(wfCustomFormList)){
			 for(WfCustomForm wfCustomForm: wfCustomFormList) {
		        	newCustomFormList.add(wfCustomForm);
			 }
			return newCustomFormList;
		 }
		return null;
	}

	@Override
	public List<WfCustomForm> getProcesssByServiceName(String serviceName) {
		return selectSysCustomFormByServiceName(serviceName);
	}

	/**
	 * 根据仿钉钉流程json转flowable的bpmn的xml格式
	 *  add by nbacheng
	 * @param String ddjson
	 *           
	 *
	 * @return
	 */
	@Override
	public R<Void> dingdingToBpmn(String ddjson) {	
		try {

            JSONObject object = JSON.parseObject(ddjson, JSONObject.class);
            //JSONObject workflow = object.getJSONObject("process");
            //ddBpmnModel.addProcess(ddProcess);
            //ddProcess.setName (workflow.getString("name"));
            //ddProcess.setId(workflow.getString("processId"));
            ddProcess = new Process();
            ddBpmnModel = new BpmnModel();
            ddSequenceFlows = Lists.newArrayList();
            ddBpmnModel.addProcess(ddProcess);
            ddProcess.setId("Process_"+UUID.randomUUID());
            ddProcess.setName ("dingding演示流程");
            JSONObject flowNode = object.getJSONObject("processData");
            StartEvent startEvent = createStartEvent(flowNode);
            ddProcess.addFlowElement(startEvent);
            String lastNode = create(startEvent.getId(), flowNode);
            EndEvent endEvent = createEndEvent();
            ddProcess.addFlowElement(endEvent);
            ddProcess.addFlowElement(connect(lastNode, endEvent.getId()));

            new BpmnAutoLayout(ddBpmnModel).execute();
            return R.ok(new String(new BpmnXMLConverter().convertToXML(ddBpmnModel)));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("创建失败: e=" + e.getMessage());
        }
	}
	
	String id(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }

    ServiceTask serviceTask(String name) {
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setName(name);
        return serviceTask;
    }

    SequenceFlow connect(String from, String to) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId(id("sequenceFlow"));
        flow.setSourceRef(from);
        flow.setTargetRef(to);
        ddSequenceFlows.add(flow);
        return flow;
    }

    StartEvent createStartEvent(JSONObject flowNode) {
    	String nodeType = flowNode.getString("type");
        StartEvent startEvent = new StartEvent();
        startEvent.setId(id("start"));
        if (Type.INITIATOR_TASK.isEqual(nodeType)) {
        	JSONObject properties = flowNode.getJSONObject("properties");
        	if(StringUtils.isNotEmpty(properties.getString("formKey"))) {
        		startEvent.setFormKey(properties.getString("formKey"));
        	}
        }
        return startEvent;
    }

    EndEvent createEndEvent() {
        EndEvent endEvent = new EndEvent();
        endEvent.setId(id("end"));
        return endEvent;
    }


    String create(String fromId, JSONObject flowNode) throws InvocationTargetException, IllegalAccessException {
        String nodeType = flowNode.getString("type");
       
        if (Type.INITIATOR_TASK.isEqual(nodeType)) {
            flowNode.put("incoming", Collections.singletonList(fromId));
            String id = createUserTask(flowNode,nodeType);

            if(flowNode.containsKey("concurrentNodes")) { //并行网关
                return createConcurrentGatewayBuilder(id, flowNode);   
            }
            
            if(flowNode.containsKey("conditionNodes")) { //排它网关或叫条件网关
                return createExclusiveGatewayBuilder(id, flowNode);
            }    
            
            // 如果当前任务还有后续任务，则遍历创建后续任务
            JSONObject nextNode = flowNode.getJSONObject("childNode");
            if (Objects.nonNull(nextNode)) {
                FlowElement flowElement = ddBpmnModel.getFlowElement(id);
                return create(id, nextNode);
            } else {
                return id;
            }
        } else if (Type.USER_TASK.isEqual(nodeType) || Type.APPROVER_TASK.isEqual(nodeType)) {
        	flowNode.put("incoming", Collections.singletonList(fromId));     
            String id = createUserTask(flowNode,nodeType);
            if(flowNode.containsKey("concurrentNodes")) { //并行网关
                return createConcurrentGatewayBuilder(id, flowNode);   
            }
            
            if(flowNode.containsKey("conditionNodes")) { //排它网关或叫条件网关
                return createExclusiveGatewayBuilder(id, flowNode);
            }  
            
            // 如果当前任务还有后续任务，则遍历创建后续任务
            JSONObject nextNode = flowNode.getJSONObject("childNode");
            if (Objects.nonNull(nextNode)) {
                FlowElement flowElement = ddBpmnModel.getFlowElement(id);
                return create(id, nextNode);          
            } else {
            	return id;  
            }
        } else if (Type.SERVICE_TASK.isEqual(nodeType)) {
            flowNode.put("incoming", Collections.singletonList(fromId));
            String id = createServiceTask(flowNode);

            if(flowNode.containsKey("concurrentNodes")) { //并行网关
                return createConcurrentGatewayBuilder(id, flowNode);   
            }
            
            if(flowNode.containsKey("conditionNodes")) { //排它网关或叫条件网关
                return createExclusiveGatewayBuilder(id, flowNode);
            } 
            
            // 如果当前任务还有后续任务，则遍历创建后续任务
            JSONObject nextNode = flowNode.getJSONObject("childNode");
            if (Objects.nonNull(nextNode)) {
                FlowElement flowElement = ddBpmnModel.getFlowElement(id);
                return create(id, nextNode);
            } else {
                return id;
            }
        }  
        else {
            throw new RuntimeException("未知节点类型: nodeType=" + nodeType);
        }
    }

    String createExclusiveGatewayBuilder(String formId, JSONObject flowNode) throws InvocationTargetException, IllegalAccessException {
        //String name = flowNode.getString("nodeName");
        String exclusiveGatewayId = id("exclusiveGateway");
        ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
        exclusiveGateway.setId(exclusiveGatewayId);
        exclusiveGateway.setName("排它条件网关");
        ddProcess.addFlowElement(exclusiveGateway);
        ddProcess.addFlowElement(connect(formId, exclusiveGatewayId));

        if (Objects.isNull(flowNode.getJSONArray("conditionNodes")) && Objects.isNull(flowNode.getJSONObject("childNode"))) {
            return exclusiveGatewayId;
        }
        List<JSONObject> flowNodes = Optional.ofNullable(flowNode.getJSONArray("conditionNodes")).map(e -> e.toJavaList(JSONObject.class)).orElse(Collections.emptyList());
        List<String> incoming = Lists.newArrayListWithCapacity(flowNodes.size());

        List<JSONObject> conditions = Lists.newCopyOnWriteArrayList();
        for (JSONObject element : flowNodes) {
            JSONObject childNode = element.getJSONObject("childNode");
            JSONObject properties = element.getJSONObject("properties");
            String nodeName = properties.getString("title");
            String expression = properties.getString("conditions");

            if (Objects.isNull(childNode)) {
                incoming.add(exclusiveGatewayId);
                JSONObject condition = new JSONObject();
                condition.fluentPut("nodeName", nodeName)
                        .fluentPut("expression", expression);
                conditions.add(condition);
                continue;
            }
            // 只生成一个任务，同时设置当前任务的条件
            childNode.put("incoming", Collections.singletonList(exclusiveGatewayId));
            String identifier = create(exclusiveGatewayId, childNode);
            List<SequenceFlow> flows = ddSequenceFlows.stream().filter(flow -> StringUtils.equals(exclusiveGatewayId, flow.getSourceRef()))
                    .collect(Collectors.toList());
            
            flows.stream().forEach(
                    e -> {
                        if (StringUtils.isBlank(e.getName()) && StringUtils.isNotBlank(nodeName)) {
                            e.setName(nodeName);
                        }
                        // 设置条件表达式
                        if (Objects.isNull(e.getConditionExpression()) && StringUtils.isNotBlank(expression)) {
                            e.setConditionExpression(expression);
                        }
                    }
            );
            if (Objects.nonNull(identifier)) {
                incoming.add(identifier);
            }
        }


        JSONObject childNode = flowNode.getJSONObject("childNode");
        if (Objects.nonNull(childNode)) {
            if (incoming == null || incoming.isEmpty()) {
                return create(exclusiveGatewayId, childNode);
            } else {
                // 所有 service task 连接 end exclusive gateway
                childNode.put("incoming", incoming);
                FlowElement flowElement = ddBpmnModel.getFlowElement(incoming.get(0));
                // 1.0 先进行边连接, 暂存 nextNode
                JSONObject nextNode = childNode.getJSONObject("childNode");
                childNode.put("childNode", null);
                String identifier = create(flowElement.getId(), childNode);
                for (int i = 1; i < incoming.size(); i++) {
                	ddProcess.addFlowElement(connect(incoming.get(i), identifier));
                }

                //  针对 gateway 空任务分支 添加条件表达式
                if (!conditions.isEmpty()) {
                    FlowElement flowElement1 = ddBpmnModel.getFlowElement(identifier);
                    // 获取从 gateway 到目标节点 未设置条件表达式的节点
                    List<SequenceFlow> flows = ddSequenceFlows.stream().filter(flow -> StringUtils.equals(flowElement1.getId(), flow.getTargetRef()))
                            .filter(flow -> StringUtils.equals(flow.getSourceRef(), exclusiveGatewayId))
                            .collect(Collectors.toList());
                    flows.stream().forEach(sequenceFlow -> {
                        if (!conditions.isEmpty()) {
                            JSONObject condition = conditions.get(0);
                            String nodeName = condition.getString("content");
                            String expression = condition.getString("expression");

                            if (StringUtils.isBlank(sequenceFlow.getName()) && StringUtils.isNotBlank(nodeName)) {
                                sequenceFlow.setName(nodeName);
                            }
                            // 设置条件表达式
                            if (Objects.isNull(sequenceFlow.getConditionExpression()) && StringUtils.isNotBlank(expression)) {
                                sequenceFlow.setConditionExpression(expression);
                            }

                            conditions.remove(0);
                        }
                    });

                }

                // 1.1 边连接完成后，在进行 nextNode 创建
                if (Objects.nonNull(nextNode)) {
                    return create(identifier, nextNode);
                } else {
                    return identifier;
                }
            }
        }
        return exclusiveGatewayId;
    }

    String createConcurrentGatewayBuilder(String fromId, JSONObject flowNode) throws InvocationTargetException, IllegalAccessException {
        //String name = flowNode.getString("nodeName");
        //下面创建并行网关并进行连线
    	ParallelGateway parallelGateway = new ParallelGateway();
        String parallelGatewayId = id("parallelGateway");
        parallelGateway.setId(parallelGatewayId);
        parallelGateway.setName("并行网关");
        ddProcess.addFlowElement(parallelGateway);
        ddProcess.addFlowElement(connect(fromId, parallelGatewayId));

        if (Objects.isNull(flowNode.getJSONArray("concurrentNodes"))
                && Objects.isNull(flowNode.getJSONObject("childNode"))) {
            return parallelGatewayId;
        }

        //获取并行列表数据
        List<JSONObject> flowNodes = Optional.ofNullable(flowNode.getJSONArray("concurrentNodes")).map(e -> e.toJavaList(JSONObject.class)).orElse(Collections.emptyList());
        List<String> incoming = Lists.newArrayListWithCapacity(flowNodes.size());
        for (JSONObject element : flowNodes) {
            JSONObject childNode = element.getJSONObject("childNode");
            if (Objects.isNull(childNode)) {//没子节点,就把并行id加入入口队列
                incoming.add(parallelGatewayId);
                continue;
            }
            String identifier = create(parallelGatewayId, childNode);
            if (Objects.nonNull(identifier)) {//否则加入有子节点的用户id
                incoming.add(identifier);
            }
        }

        JSONObject childNode = flowNode.getJSONObject("childNode");
       
        if (Objects.nonNull(childNode)) {
            // 普通结束网关
            if (CollectionUtils.isEmpty(incoming)) {
                return create(parallelGatewayId, childNode);
            } else {
                // 所有 user task 连接 end parallel gateway
                childNode.put("incoming", incoming);
                FlowElement flowElement = ddBpmnModel.getFlowElement(incoming.get(0));
                // 1.0 先进行边连接, 暂存 nextNode
                JSONObject nextNode = childNode.getJSONObject("childNode");
                childNode.put("childNode", null); //不加这个,下面创建子节点会进入递归了
                String identifier = create(incoming.get(0), childNode);
                for (int i = 1; i < incoming.size(); i++) {//其中0之前创建的时候已经连接过了，所以从1开始补另外一条
                    FlowElement flowElementIncoming = ddBpmnModel.getFlowElement(incoming.get(i));
                    ddProcess.addFlowElement(connect(flowElementIncoming.getId(), identifier));
                }
                // 1.1 边连接完成后，在进行 nextNode 创建
                if (Objects.nonNull(nextNode)) {
                    return create(identifier, nextNode);
                } else {
                    return identifier;
                }
            }
        }
        if(incoming.size()>0) {
        	return incoming.get(1);
        }
        else {
        	return parallelGatewayId;   
        }
        
    }

    String createUserTask(JSONObject flowNode, String nodeType) {
        List<String> incoming = flowNode.getJSONArray("incoming").toJavaList(String.class);
        // 自动生成id
        String id = id("userTask");
        if (incoming != null && !incoming.isEmpty()) {
        	UserTask userTask = new UserTask();
        	JSONObject properties = flowNode.getJSONObject("properties");
        	userTask.setName(properties.getString("title"));
        	userTask.setId(id);
        	List<ExtensionAttribute> attributes = new  ArrayList<ExtensionAttribute>();
        	if (Type.INITIATOR_TASK.isEqual(nodeType)) {
        		ExtensionAttribute extAttribute =  new ExtensionAttribute();
        		extAttribute.setNamespace(ProcessConstants.NAMASPASE);
        		extAttribute.setName("dataType");
        		extAttribute.setValue("INITIATOR");
        		attributes.add(extAttribute);
        		userTask.addAttribute(extAttribute);
        		userTask.setAssignee("${initiator}");
        	} else if (Type.USER_TASK.isEqual(nodeType) || Type.APPROVER_TASK.isEqual(nodeType)) {
        		String assignType = properties.getString("assigneeType");
        		if(StringUtils.equalsAnyIgnoreCase("user", assignType)) {
	        		JSONArray approvers = properties.getJSONArray("approvers");
	        		JSONObject approver = approvers.getJSONObject(0);
	        		ExtensionAttribute extDataTypeAttribute =  new ExtensionAttribute();
	        		extDataTypeAttribute.setNamespace(ProcessConstants.NAMASPASE);
	        		extDataTypeAttribute.setName("dataType");
	        		extDataTypeAttribute.setValue("USERS");
	        		userTask.addAttribute(extDataTypeAttribute);
	        		ExtensionAttribute extTextAttribute =  new ExtensionAttribute();
	        		extTextAttribute.setNamespace(ProcessConstants.NAMASPASE);
	        		extTextAttribute.setName("text");
	        		extTextAttribute.setValue(approver.getString("nickName"));
	        		userTask.addAttribute(extTextAttribute);
	        		userTask.setFormKey(properties.getString("formKey"));
	        		userTask.setAssignee(approver.getString("userName"));
        		}
        		else if (StringUtils.equalsAnyIgnoreCase("director", assignType)) {
	        		ExtensionAttribute extDataTypeAttribute =  new ExtensionAttribute();
	        		extDataTypeAttribute.setNamespace(ProcessConstants.NAMASPASE);
	        		extDataTypeAttribute.setName("dataType");
	        		extDataTypeAttribute.setValue("MANAGER");
	        		userTask.addAttribute(extDataTypeAttribute);
	        		ExtensionAttribute extTextAttribute =  new ExtensionAttribute();
	        		extTextAttribute.setNamespace(ProcessConstants.NAMASPASE);
	        		extTextAttribute.setName("text");
	        		extTextAttribute.setValue("部门经理");
	        		userTask.addAttribute(extTextAttribute);
	        		userTask.setFormKey(properties.getString("formKey"));
	        		userTask.setAssignee("${DepManagerHandler.getUser(execution)}");
        		}
        		else if (StringUtils.equalsAnyIgnoreCase("role", assignType)) {
        			JSONArray approvers = properties.getJSONArray("approvers");
	        		JSONObject approver = approvers.getJSONObject(0);
	        		ExtensionAttribute extDataTypeAttribute =  new ExtensionAttribute();
	        		extDataTypeAttribute.setNamespace(ProcessConstants.NAMASPASE);
	        		extDataTypeAttribute.setName("dataType");
	        		extDataTypeAttribute.setValue("ROLES");
	        		userTask.addAttribute(extDataTypeAttribute);
	        		ExtensionAttribute extTextAttribute =  new ExtensionAttribute();
	        		extTextAttribute.setNamespace(ProcessConstants.NAMASPASE);
	        		extTextAttribute.setName("text");
	        		extTextAttribute.setValue(approver.getString("roleName"));
	        		userTask.addAttribute(extTextAttribute);
	        		userTask.setFormKey(properties.getString("formKey"));
	        		List<SysRole> sysroleslist = approvers.toJavaList(SysRole.class);
	        		List<String> roleslist = sysroleslist.stream().map(e->e.getRoleKey()).collect(Collectors.toList());
	        		userTask.setCandidateGroups(roleslist);
	        		userTask.setAssignee("${assignee}");
	        		MultiInstanceLoopCharacteristics loopCharacteristics = new MultiInstanceLoopCharacteristics();
	        		if(StringUtils.equalsAnyIgnoreCase(properties.getString("counterSign"), "true")) {//并行会签
	        			loopCharacteristics.setSequential(false);
	        			loopCharacteristics.setInputDataItem("${multiInstanceHandler.getUserNames(execution)}");
	        			loopCharacteristics.setElementVariable("assignee");
	        			loopCharacteristics.setCompletionCondition("${nrOfCompletedInstances &gt;= nrOfInstances}");
	        		}
	        		else {
	        			loopCharacteristics.setSequential(false);
	        			loopCharacteristics.setInputDataItem("${multiInstanceHandler.getUserNames(execution)}");
	        			loopCharacteristics.setElementVariable("assignee");
	        			loopCharacteristics.setCompletionCondition("${nrOfCompletedInstances &gt; 0}");
	        		}
	        		userTask.setLoopCharacteristics(loopCharacteristics);
        		}
        	}
        	
            ddProcess.addFlowElement(userTask);
            ddProcess.addFlowElement(connect(incoming.get(0), id));
        }
        return id;
    }
    
    String createServiceTask(JSONObject flowNode) {
        List<String> incoming = flowNode.getJSONArray("incoming").toJavaList(String.class);
        // 自动生成id
        String id = id("serviceTask");
        if (incoming != null && !incoming.isEmpty()) {
            ServiceTask serviceTask = new ServiceTask();
            serviceTask.setName(flowNode.getString("nodeName"));
            serviceTask.setId(id);
            ddProcess.addFlowElement(serviceTask);
            ddProcess.addFlowElement(connect(incoming.get(0), id));
        }
        return id;
    }

    enum Type {

        /**
         * 并行事件
         */
    	CONCURRENT("concurrent", ParallelGateway.class),

        /**
         * 排他事件
         */
        EXCLUSIVE("exclusive", ExclusiveGateway.class),

        /**
         * 服务任务
         */
        SERVICE_TASK("serviceTask", ServiceTask.class),
        
        /**
         * 发起人任务
         */
        INITIATOR_TASK("start", ServiceTask.class),
        
        /**
         * 审批任务
         */
        APPROVER_TASK("approver", ServiceTask.class),
    	
    	/**
         * 用户任务
         */
        USER_TASK("userTask", UserTask.class);

        private String type;

        private Class<?> typeClass;

        Type(String type, Class<?> typeClass) {
            this.type = type;
            this.typeClass = typeClass;
        }

        public final static Map<String, Class<?>> TYPE_MAP = Maps.newHashMap();

        static {
            for (Type element : Type.values()) {
                TYPE_MAP.put(element.type, element.typeClass);
            }
        }

        public boolean isEqual(String type) {
            return this.type.equals(type);
        }

    }
	
}
