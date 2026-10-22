package com.nexus.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.UserService;
import com.nexus.common.core.utils.DateUtils;
import com.nexus.common.core.utils.SpringContextHolder;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.common.constant.ProcessConstants;
import com.nexus.common.flowable.common.enums.FlowComment;
import com.nexus.common.flowable.common.enums.ProcessStatus;
import com.nexus.common.flowable.core.FormConf;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.core.domain.ProcessQuery;
import com.nexus.common.flowable.factory.FlowServiceFactory;
import com.nexus.common.flowable.flow.FindNextNodeUtil;
import com.nexus.common.flowable.flow.FlowableUtils;
import com.nexus.common.flowable.utils.ModelUtils;
import com.nexus.common.flowable.utils.ProcessUtils;
import com.nexus.common.flowable.utils.TaskUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.SysRole;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.CommonService;
import com.nexus.system.service.ISysUserService;
import com.nexus.workflow.domain.WfCustomForm;
import com.nexus.workflow.domain.WfDeployForm;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.domain.dto.FlowNextDto;
import com.nexus.workflow.domain.vo.*;
import com.nexus.workflow.handler.ProcessAccessGuard;
import com.nexus.workflow.mapper.WfCategoryMapper;
import com.nexus.workflow.mapper.WfDeployFormMapper;
import com.nexus.workflow.service.IWfCustomFormService;
import com.nexus.workflow.service.IWfMyBusinessService;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.IWfTaskService;
import com.nexus.workflow.service.WfCallBackServiceI;
import com.nexus.workflow.service.WfCommonService;
import com.nexus.workflow.util.WfParticipantNameResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.ObjectUtils;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
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
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author wanghuan
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
    private final WfDeployFormMapper deployFormMapper;
    private final CommonService commonService;
    private final ISysUserService sysUserService;
    private final IWfCustomFormService wfCustomFormService;
    private final IWfMyBusinessService wfMyBusinessService;
    private final WfCommonService wfCommonService;
    private final WfCategoryMapper categoryMapper;
    private final ProcessAccessGuard processAccessGuard;
    private final WfParticipantNameResolver participantNameResolver;

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
        // OA 过滤在分页后进行会导致 total 虚高且页内丢数据，先全量过滤（流程定义量级有限）再内存分页
        List<ProcessDefinition> definitionList = processDefinitionQuery.list();
        // 批量取部署信息；类别映射按 code 去重缓存（类别数远小于定义行数）
        Map<String, Deployment> deploymentMap = queryDeploymentsByDefinitionList(definitionList);
        Map<String, WfAppTypeVo> appTypeCache = new HashMap<>();
        String tenantId = TenantHelper.getTenantId();

        List<WfDefinitionVo> definitionVoList = new ArrayList<>();
        for (ProcessDefinition processDefinition : definitionList) {
            Deployment deployment = deploymentMap.get(processDefinition.getDeploymentId());
            String category = deployment != null ? deployment.getCategory() : processDefinition.getCategory();
           // 检查流程是否是OA流程
            WfAppTypeVo appTypeVo = appTypeCache.computeIfAbsent(category,
                c -> categoryMapper.selectAppTypeVoByCode(c, tenantId));
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
	            vo.setCategory(category);
	            vo.setDeploymentTime(deployment != null ? deployment.getDeploymentTime() : null);
	            definitionVoList.add(vo);
            }

        }
        // 内存分页（过滤后总数与页内条数保持一致）
        int total = definitionVoList.size();
        int pageNum = Math.max(pageQuery.getPageNum(), 1);
        int pageSize = Math.max(pageQuery.getPageSize(), 1);
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        page.setRecords(definitionVoList.subList(from, to));
        page.setTotal(total);
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
        // 批量取部署信息：一条 deploymentIds 查询替代原先逐定义一次 singleResult 的 N+1
        Map<String, Deployment> deploymentMap = queryDeploymentsByDefinitionList(definitionList);

        List<WfDefinitionVo> definitionVoList = new ArrayList<>();
        for (ProcessDefinition processDefinition : definitionList) {
            Deployment deployment = deploymentMap.get(processDefinition.getDeploymentId());
            WfDefinitionVo vo = new WfDefinitionVo();
            vo.setDefinitionId(processDefinition.getId());
            vo.setProcessKey(processDefinition.getKey());
            vo.setProcessName(processDefinition.getName());
            vo.setVersion(processDefinition.getVersion());
            vo.setDeploymentId(processDefinition.getDeploymentId());
            vo.setSuspended(processDefinition.isSuspended());
            // 流程定义时间
            vo.setCategory(deployment != null ? deployment.getCategory() : processDefinition.getCategory());
            vo.setDeploymentTime(deployment != null ? deployment.getDeploymentTime() : null);
            definitionVoList.add(vo);
        }
        return definitionVoList;
    }

    /**
     * 按流程定义列表批量查询部署信息（deploymentIds 单条查询），返回部署ID -> 部署对象映射
     */
    private Map<String, Deployment> queryDeploymentsByDefinitionList(List<ProcessDefinition> definitionList) {
        List<String> deploymentIds = definitionList.stream()
            .map(ProcessDefinition::getDeploymentId)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        if (deploymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return repositoryService.createDeploymentQuery()
            .deploymentIds(deploymentIds)
            .list()
            .stream()
            .collect(Collectors.toMap(Deployment::getId, d -> d));
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
                // 运行中实例可能既无活动任务也无历史任务（如停在等待事件/异步作业），空列表直接取 get(0) 会越界
                if (CollUtil.isNotEmpty(historicTaskInstance)) {
                    taskVo.setTaskId(historicTaskInstance.get(0).getId());
                }
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
            flowTask.setProcInsId(task.getProcessInstanceId());
            // 流程变量
            flowTask.setProcVars(task.getProcessVariables());
            flowList.add(flowTask);
        }
        // 批量回填流程定义与发起人信息，避免逐条 N+1 查询
        fillTaskListExtraInfo(flowList);
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
            taskVo.setProcInsId(task.getProcessInstanceId());
            taskVoList.add(taskVo);
        }
        // 批量回填流程定义与发起人信息，避免逐条 N+1 查询
        fillTaskListExtraInfo(taskVoList);
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
            flowTask.setProcInsId(task.getProcessInstanceId());
            flowList.add(flowTask);
        }
        // 批量回填流程定义与发起人信息，避免逐条 N+1 查询
        fillTaskListExtraInfo(flowList);
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
            flowTask.setProcInsId(task.getProcessInstanceId());
            flowList.add(flowTask);
        }
        // 批量回填流程定义与发起人信息，避免逐条 N+1 查询
        fillTaskListExtraInfo(flowList);
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
            flowTask.setProcInsId(histTask.getProcessInstanceId());
            flowTask.setHisProcInsId(histTask.getProcessInstanceId());
            // 流程变量
            flowTask.setProcVars(histTask.getProcessVariables());
            //自定义业务
            Map<String, Object>  processVariables = histTask.getProcessVariables();
            if(processVariables.containsKey("dataId")) {
            	flowTask.setDataId(processVariables.get("dataId").toString());
            }

            hisTaskList.add(flowTask);
        }
        // 批量回填流程定义与发起人信息，避免逐条 N+1 查询
        fillTaskListExtraInfo(hisTaskList);
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
            flowTask.setProcInsId(histTask.getProcessInstanceId());
            flowTask.setHisProcInsId(histTask.getProcessInstanceId());
            // 流程变量
            flowTask.setProcVars(histTask.getProcessVariables());

            hisTaskList.add(flowTask);
        }
        // 批量回填流程定义与发起人信息，避免逐条 N+1 查询
        fillTaskListExtraInfo(hisTaskList);
        return hisTaskList;
    }

    @Override
    public FormConf selectFormContent(String definitionId, String deployId, String procInsId) {
        WfDeployForm deployForm = getDeployForm(definitionId,deployId);
        if (deployForm == null || StringUtils.isBlank(deployForm.getContent())) {
            throw new RuntimeException("流程未配置表单，请先在流程设计中关联表单！");
        }
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
            if (historicProcIns == null) {
                throw new RuntimeException("流程实例不存在: " + procInsId);
            }
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
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
    public R<Void> startProcessByDefKey(String procDefKey, Map<String, Object> variables) {
        // 必须限定当前租户，防止多租户同 key 时启动到其它租户的流程定义
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(procDefKey)
            .processDefinitionTenantId(TenantHelper.getTenantId())
            .latestVersion().singleResult();
        return startProcess(processDefinition, variables);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcessByIds(String[] instanceIds) {
        List<String> ids = Arrays.asList(instanceIds);
        // 仅保留属于当前租户的历史实例，防止跨租户批量删除他人审批历史
        List<HistoricProcessInstance> tenantInstances = historyService.createHistoricProcessInstanceQuery()
            .processInstanceIds(new HashSet<>(ids))
            .processInstanceTenantId(TenantHelper.getTenantId())
            .list();
        if (tenantInstances.isEmpty()) {
            return;
        }
        List<String> tenantIds = tenantInstances.stream()
            .map(HistoricProcessInstance::getId).collect(Collectors.toList());
        // 校验流程是否结束
        long activeInsCount = runtimeService.createProcessInstanceQuery()
            .processInstanceIds(new HashSet<>(tenantIds)).active().count();
        if (activeInsCount > 0) {
            throw new ServiceException("不允许删除进行中的流程实例");
        }
        // 删除历史流程实例
        historyService.bulkDeleteHistoricProcessInstances(tenantIds);
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
        // 仅流程参与人（发起人/办理人/候选人/被抄送人）或管理员可查看详情，防止越权读取他人单据
        processAccessGuard.checkCanView(procInsId);
        WfDetailVo detailVo = new WfDetailVo();
        List<CurNodeInfoVo> listCurNodeInfo = new ArrayList<CurNodeInfoVo>();
        // 获取流程实例
        HistoricProcessInstance historicProcIns = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(procInsId)
            .includeProcessVariables()
            .singleResult();

        // 查看流程时前端只传 procInsId、未透传 dataId，此处从流程变量回查 dataId，
        // 确保 processFormList 能命中自定义业务表单（routeName），「表单信息」Tab 才能渲染业务表单
        if (StringUtils.isBlank(dataId) && historicProcIns != null) {
            Map<String, Object> procVars = historicProcIns.getProcessVariables();
            if (procVars != null) {
                Object vid = procVars.get("dataId");
                if (vid != null) {
                    dataId = vid.toString();
                }
            }
        }

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
        WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
        if (business == null) {
            throw new ServiceException("未找到该业务数据的流程关联记录");
        }
        String procInsId = business.getProcessInstanceId();
        // 仅流程参与人或管理员可查看详情，防止越权读取他人单据
        processAccessGuard.checkCanView(procInsId);
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
     * <p>注意：本方法为 private，不可被 Spring 事务代理拦截（同类自调用也不生效），
     * 事务边界在 public 入口 {@link #startProcessByDefId} / {@link #startProcessByDefKey} 上。</p>
     */
    private R startProcess(ProcessDefinition procDef, Map<String, Object> variables) {
        if (procDef == null) {
            throw new ServiceException("流程定义不存在或已被删除");
        }
        if (procDef.isSuspended()) {
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
            WfMyBusiness flowmybusiness = wfMyBusinessService.getByDataId(variables.get("dataId").toString());
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

        // 第一个用户任务为发起人，则自动完成任务
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

            	WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
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
            	WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
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
                    wfMyBusinessService.remove(
                            new LambdaQueryWrapper<WfMyBusiness>()
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
	 *  add by wanghuan
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
				   if (i >= userlist.size()) {
					    // 活动任务数多于审批人数量（如并行分支既有任务），超出部分不再对位指派，避免越界
					    break;
				   }
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
	 *  add by wanghuan
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
	 *  add by wanghuan
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
        // 传节点定义key获取当前节点
        List<Process> processes = bpmnModel.getProcesses();
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
        	   Map<String, Object> gatewayUserMap =  GetExclusiveGatewayUser(targetFlowElement,variables);//还是需要返回用户与是否并发，因为并发要做特殊处理
        	   if(gatewayUserMap != null) {
        		   // Java 按值传递：不可对 usermap/userlist 参数重新赋值（调用方不可见，原写法导致并发会签分支永远不执行），
        		   // 必须把结果写回调用方传入的同一容器
        		   usermap.putAll(gatewayUserMap);
        		   Object gatewayUserList = gatewayUserMap.get("approval");
        		   userlist.clear();
        		   if (gatewayUserList instanceof List) {
        		       userlist.addAll((List<String>) gatewayUserList);
        		   }
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
     * add by wanghuan
     */
    /**
     * 钉钉流程条件表达式允许的字符：变量名/数字/中文/比较与逻辑运算符/括号/引号/${} 包裹。
     * 中文用于兼容中文变量名（不构成方法调用标识符，无注入面）；禁止 #、;、反斜杠等特征字符。
     */
    private static final Pattern DD_CONDITION_CHARS = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5$ \\t{}().,<>=!&|+\\-*%'\"]+$");
    /**
     * 方法调用特征：标识符后紧跟左括号（如 exec(、T(、getClass(），合法的条件表达式不会出现
     */
    private static final Pattern DD_CONDITION_CALL = Pattern.compile("[A-Za-z0-9_]\\s*\\(");

    /**
     * 校验钉钉流程转换的条件表达式，防止 UEL 表达式注入导致任意方法调用。
     * 仅允许「变量 比较运算符 值」及 &&/||、括号分组的组合（可含 ${} 包裹）。
     *
     * @param expression 原始条件表达式，允许为空
     * @return 原表达式（校验通过时）
     */
    private static String validateDdConditionExpression(String expression) {
        if (StringUtils.isBlank(expression)) {
            return expression;
        }
        if (expression.length() > 200
            || expression.contains(";") || expression.contains("\\") || expression.contains("#")
            || !DD_CONDITION_CHARS.matcher(expression).matches()
            || DD_CONDITION_CALL.matcher(expression).find()) {
            throw new ServiceException("流程条件表达式不合法（仅支持变量比较运算），已拒绝保存");
        }
        return expression;
    }

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
                if (FindNextNodeUtil.evalCondition(variables, expression)) {
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
                        WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
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
                    WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
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
            WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
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
                WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
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
                WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
                if(ObjectUtils.isNotEmpty(business)) {
                    Map<String, Object> customMap = new HashMap<String, Object>();
                    FormConf formConf = new FormConf();
                    customMap.put("formData", bizFormData);
                    customMap.put("routeName", business.getRouteName());
                    formConf.setFormValues(customMap);
                    procFormList.add(formConf);
                }
            } catch (Exception e) {
                // 历史遗留：脏配置的解析失败不应阻断详情展示，但不能完全无声
                log.warn("流程节点表单配置解析失败，已跳过该节点表单", e);
            }
        }
        return procFormList;
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

        // ===== 批量预取，消除逐活动逐人查询的 N+1 =====
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
        if (StringUtils.isNotBlank(historicProcIns.getStartUserId())) {
            involvedUserIds.add(historicProcIns.getStartUserId());
        }
        for (HistoricActivityInstance activityItem : historicActivityInstanceList) {
            if (StringUtils.isNotBlank(activityItem.getAssignee())) {
                involvedUserIds.add(activityItem.getAssignee());
            }
        }
        // 用户昵称一次批量查询；候选组名按不同 ID 去重后各查一次
        Map<String, String> nickMap = participantNameResolver.userNickMap(involvedUserIds);
        Map<String, String> groupMap = participantNameResolver.groupNameMap(involvedGroupIds);

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
                    // 发起人可能已被删除：昵称缺失时降级显示登录名（批量解析已预置回填）
                    String nickName = nickMap.getOrDefault(userName, userName);
                    if (StringUtils.isNotBlank(nickName)) {
                        elementVo.setAssigneeId(userName);
                        elementVo.setAssigneeName(nickName);
                    }
                }
            } else if (BpmnXMLConstants.ELEMENT_TASK_USER.equals(activityInstance.getActivityType())) {
                if (StringUtils.isNotBlank(activityInstance.getAssignee())) {
                    String userName = activityInstance.getAssignee();
                    elementVo.setAssigneeId(userName);
                    elementVo.setAssigneeName(nickMap.getOrDefault(userName, userName));
                }
                // 展示审批人员（候选人/候选组名称缺失时跳过该项）
                List<HistoricIdentityLink> linksForTask =
                    linksByTask.getOrDefault(activityInstance.getTaskId(), Collections.emptyList());
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
                        elementVo.setCandidate(candidateBuilder.substring(0, candidateBuilder.length() - 1));
                    }
                }
                // 获取意见评论内容
                if (CollUtil.isNotEmpty(commentList)) {
                    List<Comment> comments = new ArrayList<>();
                    for (Comment comment : commentList) {
                        // 流程级评论 taskId 为 null，用 Objects.equals 防止 NPE
                        if (Objects.equals(comment.getTaskId(), activityInstance.getTaskId())) {
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
     * 批量查询流程定义信息（避免列表方法内 N+1 查询）
     *
     * @param processDefinitionIds 流程定义ID集合
     * @return 流程定义ID -> 流程定义
     */
    private Map<String, ProcessDefinition> batchQueryProcessDefinitions(Collection<String> processDefinitionIds) {
        if (CollUtil.isEmpty(processDefinitionIds)) {
            return Collections.emptyMap();
        }
        List<ProcessDefinition> pdList = repositoryService.createProcessDefinitionQuery()
            .processDefinitionIds(new HashSet<>(processDefinitionIds))
            .list();
        Map<String, ProcessDefinition> pdMap = new HashMap<>();
        if (CollUtil.isNotEmpty(pdList)) {
            pdList.forEach(pd -> pdMap.put(pd.getId(), pd));
        }
        return pdMap;
    }

    /**
     * 批量查询历史流程实例（发起人/业务Key），避免列表方法内 N+1 查询
     *
     * @param processInstanceIds 流程实例ID集合
     * @return 流程实例ID -> 历史流程实例
     */
    private Map<String, HistoricProcessInstance> batchQueryHistoricProcessInstances(Collection<String> processInstanceIds) {
        if (CollUtil.isEmpty(processInstanceIds)) {
            return Collections.emptyMap();
        }
        List<HistoricProcessInstance> hisList = historyService.createHistoricProcessInstanceQuery()
            .processInstanceIds(new HashSet<>(processInstanceIds))
            .list();
        Map<String, HistoricProcessInstance> hisMap = new HashMap<>();
        if (CollUtil.isNotEmpty(hisList)) {
            hisList.forEach(his -> hisMap.put(his.getId(), his));
        }
        return hisMap;
    }

    /**
     * 批量查询用户名->昵称映射（避免列表方法内按用户逐条查询）
     *
     * @param userNames 用户名集合
     * @return 用户名 -> 昵称（用户不存在时回退显示用户名）
     */
    private Map<String, String> batchQueryUserNickNames(Collection<String> userNames) {
        if (CollUtil.isEmpty(userNames)) {
            return Collections.emptyMap();
        }
        Map<String, String> nickNameMap = new HashMap<>();
        sysUserService.selectUserByUserNames(userNames)
            .forEach(user -> nickNameMap.put(user.getUserName(), user.getNickName()));
        return nickNameMap;
    }

    /**
     * 批量回填任务列表的流程定义与发起人信息（todo/claim/finished 列表共用）
     *
     * @param flowList 待回填的任务VO列表
     */
    private void fillTaskListExtraInfo(List<WfTaskVo> flowList) {
        if (CollUtil.isEmpty(flowList)) {
            return;
        }
        Set<String> procDefIds = new HashSet<>();
        Set<String> procInsIds = new HashSet<>();
        Set<String> userNames = new HashSet<>();
        for (WfTaskVo flowTask : flowList) {
            if (StringUtils.isNotBlank(flowTask.getProcDefId())) {
                procDefIds.add(flowTask.getProcDefId());
            }
            if (StringUtils.isNotBlank(flowTask.getProcInsId())) {
                procInsIds.add(flowTask.getProcInsId());
            }
            if (StringUtils.isNotBlank(flowTask.getStartUserId())) {
                userNames.add(flowTask.getStartUserId());
            }
        }
        Map<String, ProcessDefinition> pdMap = batchQueryProcessDefinitions(procDefIds);
        Map<String, HistoricProcessInstance> hisMap = batchQueryHistoricProcessInstances(procInsIds);
        Map<String, String> nickNameMap = batchQueryUserNickNames(userNames);
        for (WfTaskVo flowTask : flowList) {
            // 流程定义信息
            ProcessDefinition pd = pdMap.get(flowTask.getProcDefId());
            if (pd != null) {
                flowTask.setDeployId(pd.getDeploymentId());
                flowTask.setProcDefName(pd.getName());
                flowTask.setProcDefVersion(pd.getVersion());
            }
            // 流程发起人信息
            HistoricProcessInstance his = hisMap.get(flowTask.getProcInsId());
            if (his != null) {
                flowTask.setStartUserId(his.getStartUserId());
                flowTask.setStartUserName(nickNameMap.getOrDefault(his.getStartUserId(), his.getStartUserId()));
                if (StringUtils.isBlank(flowTask.getDataId())) {
                    flowTask.setDataId(his.getBusinessKey());
                }
            }
        }
    }

    /**
	 * 获取流程是否结束
	 *  add by wanghuan
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
	 *  add by wanghuan
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
        		.processDefinitionTenantId(TenantHelper.getTenantId())
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
	 *  add by wanghuan
	 * @param String ddjson
	 *
	 *
	 * @return
	 */
	@Override
	public R<Void> dingdingToBpmn(String ddjson) {
		try {

            JSONObject object = JSON.parseObject(ddjson, JSONObject.class);
            // 以下对象均为本次转换的局部变量，禁止提升为实例字段（多线程/多租户并发调用会互相污染）
            Process ddProcess = new Process();
            BpmnModel ddBpmnModel = new BpmnModel();
            List<SequenceFlow> ddSequenceFlows = Lists.newArrayList();
            ddBpmnModel.addProcess(ddProcess);
            ddProcess.setId("Process_"+UUID.randomUUID());
            ddProcess.setName ("dingding演示流程");
            JSONObject flowNode = object.getJSONObject("processData");
            StartEvent startEvent = createStartEvent(flowNode);
            ddProcess.addFlowElement(startEvent);
            String lastNode = create(startEvent.getId(), flowNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            EndEvent endEvent = createEndEvent();
            ddProcess.addFlowElement(endEvent);
            ddProcess.addFlowElement(connect(lastNode, endEvent.getId(), ddSequenceFlows));

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

    SequenceFlow connect(String from, String to, List<SequenceFlow> ddSequenceFlows) {
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


    String create(String fromId, JSONObject flowNode, Process ddProcess, BpmnModel ddBpmnModel, List<SequenceFlow> ddSequenceFlows) throws InvocationTargetException, IllegalAccessException {
        String nodeType = flowNode.getString("type");

        if (Type.INITIATOR_TASK.isEqual(nodeType)) {
            flowNode.put("incoming", Collections.singletonList(fromId));
            String id = createUserTask(flowNode,nodeType, ddProcess, ddSequenceFlows);

            if(flowNode.containsKey("concurrentNodes")) { //并行网关
                return createConcurrentGatewayBuilder(id, flowNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            }

            if(flowNode.containsKey("conditionNodes")) { //排它网关或叫条件网关
                return createExclusiveGatewayBuilder(id, flowNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            }

            // 如果当前任务还有后续任务，则遍历创建后续任务
            JSONObject nextNode = flowNode.getJSONObject("childNode");
            if (Objects.nonNull(nextNode)) {
                return create(id, nextNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            } else {
                return id;
            }
        } else if (Type.USER_TASK.isEqual(nodeType) || Type.APPROVER_TASK.isEqual(nodeType)) {
        	flowNode.put("incoming", Collections.singletonList(fromId));
            String id = createUserTask(flowNode,nodeType, ddProcess, ddSequenceFlows);
            if(flowNode.containsKey("concurrentNodes")) { //并行网关
                return createConcurrentGatewayBuilder(id, flowNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            }

            if(flowNode.containsKey("conditionNodes")) { //排它网关或叫条件网关
                return createExclusiveGatewayBuilder(id, flowNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            }

            // 如果当前任务还有后续任务，则遍历创建后续任务
            JSONObject nextNode = flowNode.getJSONObject("childNode");
            if (Objects.nonNull(nextNode)) {
                return create(id, nextNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            } else {
            	return id;
            }
        } else if (Type.SERVICE_TASK.isEqual(nodeType)) {
            flowNode.put("incoming", Collections.singletonList(fromId));
            String id = createServiceTask(flowNode, ddProcess, ddSequenceFlows);

            if(flowNode.containsKey("concurrentNodes")) { //并行网关
                return createConcurrentGatewayBuilder(id, flowNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            }

            if(flowNode.containsKey("conditionNodes")) { //排它网关或叫条件网关
                return createExclusiveGatewayBuilder(id, flowNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            }

            // 如果当前任务还有后续任务，则遍历创建后续任务
            JSONObject nextNode = flowNode.getJSONObject("childNode");
            if (Objects.nonNull(nextNode)) {
                return create(id, nextNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            } else {
                return id;
            }
        }
        else {
            throw new RuntimeException("未知节点类型: nodeType=" + nodeType);
        }
    }

    String createExclusiveGatewayBuilder(String formId, JSONObject flowNode, Process ddProcess, BpmnModel ddBpmnModel, List<SequenceFlow> ddSequenceFlows) throws InvocationTargetException, IllegalAccessException {
        //String name = flowNode.getString("nodeName");
        String exclusiveGatewayId = id("exclusiveGateway");
        ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
        exclusiveGateway.setId(exclusiveGatewayId);
        exclusiveGateway.setName("排它条件网关");
        ddProcess.addFlowElement(exclusiveGateway);
        ddProcess.addFlowElement(connect(formId, exclusiveGatewayId, ddSequenceFlows));

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
            // 条件表达式来自用户提交的 JSON，写入 BPMN 前必须做白名单校验，
            // 否则存在 UEL 表达式注入（如 ${bean.exec(...)}）导致任意方法调用
            String expression = validateDdConditionExpression(properties.getString("conditions"));

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
            String identifier = create(exclusiveGatewayId, childNode, ddProcess, ddBpmnModel, ddSequenceFlows);
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
                return create(exclusiveGatewayId, childNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            } else {
                // 所有 service task 连接 end exclusive gateway
                childNode.put("incoming", incoming);
                FlowElement flowElement = ddBpmnModel.getFlowElement(incoming.get(0));
                // 1.0 先进行边连接, 暂存 nextNode
                JSONObject nextNode = childNode.getJSONObject("childNode");
                childNode.put("childNode", null);
                String identifier = create(flowElement.getId(), childNode, ddProcess, ddBpmnModel, ddSequenceFlows);
                for (int i = 1; i < incoming.size(); i++) {
                	ddProcess.addFlowElement(connect(incoming.get(i), identifier, ddSequenceFlows));
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
                    return create(identifier, nextNode, ddProcess, ddBpmnModel, ddSequenceFlows);
                } else {
                    return identifier;
                }
            }
        }
        return exclusiveGatewayId;
    }

    String createConcurrentGatewayBuilder(String fromId, JSONObject flowNode, Process ddProcess, BpmnModel ddBpmnModel, List<SequenceFlow> ddSequenceFlows) throws InvocationTargetException, IllegalAccessException {
        //String name = flowNode.getString("nodeName");
        //下面创建并行网关并进行连线
    	ParallelGateway parallelGateway = new ParallelGateway();
        String parallelGatewayId = id("parallelGateway");
        parallelGateway.setId(parallelGatewayId);
        parallelGateway.setName("并行网关");
        ddProcess.addFlowElement(parallelGateway);
        ddProcess.addFlowElement(connect(fromId, parallelGatewayId, ddSequenceFlows));

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
            String identifier = create(parallelGatewayId, childNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            if (Objects.nonNull(identifier)) {//否则加入有子节点的用户id
                incoming.add(identifier);
            }
        }

        JSONObject childNode = flowNode.getJSONObject("childNode");

        if (Objects.nonNull(childNode)) {
            // 普通结束网关
            if (CollectionUtils.isEmpty(incoming)) {
                return create(parallelGatewayId, childNode, ddProcess, ddBpmnModel, ddSequenceFlows);
            } else {
                // 所有 user task 连接 end parallel gateway
                childNode.put("incoming", incoming);
                FlowElement flowElement = ddBpmnModel.getFlowElement(incoming.get(0));
                // 1.0 先进行边连接, 暂存 nextNode
                JSONObject nextNode = childNode.getJSONObject("childNode");
                childNode.put("childNode", null); //不加这个,下面创建子节点会进入递归了
                String identifier = create(incoming.get(0), childNode, ddProcess, ddBpmnModel, ddSequenceFlows);
                for (int i = 1; i < incoming.size(); i++) {//其中0之前创建的时候已经连接过了，所以从1开始补另外一条
                    FlowElement flowElementIncoming = ddBpmnModel.getFlowElement(incoming.get(i));
                    ddProcess.addFlowElement(connect(flowElementIncoming.getId(), identifier, ddSequenceFlows));
                }
                // 1.1 边连接完成后，在进行 nextNode 创建
                if (Objects.nonNull(nextNode)) {
                    return create(identifier, nextNode, ddProcess, ddBpmnModel, ddSequenceFlows);
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

    String createUserTask(JSONObject flowNode, String nodeType, Process ddProcess, List<SequenceFlow> ddSequenceFlows) {
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
            ddProcess.addFlowElement(connect(incoming.get(0), id, ddSequenceFlows));
        }
        return id;
    }

    String createServiceTask(JSONObject flowNode, Process ddProcess, List<SequenceFlow> ddSequenceFlows) {
        List<String> incoming = flowNode.getJSONArray("incoming").toJavaList(String.class);
        // 自动生成id
        String id = id("serviceTask");
        if (incoming != null && !incoming.isEmpty()) {
            ServiceTask serviceTask = new ServiceTask();
            serviceTask.setName(flowNode.getString("nodeName"));
            serviceTask.setId(id);
            ddProcess.addFlowElement(serviceTask);
            ddProcess.addFlowElement(connect(incoming.get(0), id, ddSequenceFlows));
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
