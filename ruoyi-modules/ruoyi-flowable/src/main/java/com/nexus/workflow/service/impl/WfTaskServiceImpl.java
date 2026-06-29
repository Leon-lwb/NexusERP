package com.nexus.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import com.nexus.online.service.IFormService;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.bpmn.behavior.ParallelMultiInstanceBehavior;
import org.flowable.engine.impl.bpmn.behavior.SequentialMultiInstanceBehavior;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ActivityInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.UserService;
import com.nexus.common.core.utils.DateUtils;
import com.nexus.common.core.utils.SpringContextHolder;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.common.constant.ProcessConstants;
import com.nexus.common.flowable.common.constant.TaskConstants;
import com.nexus.common.flowable.common.enums.FlowComment;
import com.nexus.common.flowable.common.enums.ProcessStatus;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.core.domain.ExtensionElementInfo;

import com.nexus.common.flowable.factory.FlowServiceFactory;
import com.nexus.common.flowable.flow.CustomProcessDiagramGenerator;
import com.nexus.common.flowable.flow.FindNextNodeUtil;
import com.nexus.common.flowable.flow.FlowableUtils;
import com.nexus.common.flowable.utils.ModelUtils;
import com.nexus.common.flowable.utils.TaskUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.CommonService;
import com.nexus.system.service.ISysUserService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.domain.bo.WfTaskBo;
import com.nexus.workflow.domain.dto.FlowNextDto;
import com.nexus.workflow.domain.dto.FlowViewerDto;
import com.nexus.workflow.domain.vo.WfUserTaskVo;
import com.nexus.workflow.mapper.FlowTaskMapper;
import com.nexus.workflow.mapper.WfDeployFormMapper;
import com.nexus.workflow.service.IWfCopyService;
import com.nexus.workflow.service.IWfTaskService;
import com.nexus.workflow.util.flowExp;

import com.nexus.workflow.service.WfCallBackServiceI;
import com.nexus.common.core.domain.model.LoginUser;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.nexus.workflow.domain.WfDeployForm;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;


/**
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class WfTaskServiceImpl extends FlowServiceFactory implements IWfTaskService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    private final UserService userService;
    private final IWfCopyService copyService;

    private final CommonService commonService;

    private final ISysUserService sysUserService;

    private final WfMyBusinessServiceImpl wfMyBusinessService;

    private final WfDeployFormMapper wfDeployFormMapper;

    private final FlowTaskMapper flowTaskMapper;

    /**
     * 完成任务
     *
     * @param taskBo 请求实体参数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void complete(WfTaskBo taskBo) {
        // 完成任务并按需处理节点表单数据保存

        Task task = taskService.createTaskQuery().taskId(taskBo.getTaskId()).singleResult();
        TaskEntity taskEntity = (TaskEntity) taskService.createTaskQuery().taskId(taskBo.getTaskId()).singleResult();
        if (Objects.isNull(task)) {
            throw new ServiceException("任务不存在");
        }

         // 处理节点表单数据保存
         if (ObjectUtil.isNotEmpty(taskBo.getVariables())) {
             try {
                 // 获取当前节点的表单配置
                 String taskDefKey = task.getTaskDefinitionKey();
                 String procDefId = task.getProcessDefinitionId();

                 // 获取流程定义
                 ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                     .processDefinitionId(procDefId)
                     .singleResult();

                 if (processDefinition != null) {
                     // 获取节点表单配置
                     WfDeployForm deployForm = getDeployForm(processDefinition.getId(), taskDefKey);

                     if (deployForm != null && StringUtils.isNotBlank(deployForm.getTableName())) {
                         // 直接调用现有的 FormService 方法保存数据
                         IFormService formService = SpringContextHolder.getBean(IFormService.class);
                         int result = formService.addOrUpdateOnlineDataByName(deployForm.getTableName(), taskBo.getVariables());
                     } else {
                         log.warn("complete - 未找到有效的表单配置或表名为空，deployForm: {}", deployForm);
                     }
                 } else {
                     log.warn("complete - 未找到流程定义，procDefId: {}", procDefId);
                 }
             } catch (Exception e) {
                 log.error("complete - 保存节点表单数据失败", e);
                 // 不抛出异常，避免影响流程正常进行
             }
         } else {

         }

        //获取流程当前节点设置的扩展属性值,需要的时候可以使用
        //Map<String, Object> flowProperties = getFlowProperties(taskBo.getProcInsId());

        //处理自定义业务表单回写状态，节点与处理人信息
        if (StringUtils.isNotBlank(taskBo.getDataId())) {
        	//业务数据id
            String dataId = taskBo.getDataId();
            //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
            WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
            //spring容器类名
            String serviceImplName = business.getServiceImplName();
            WfCallBackServiceI flowCallBackService = (WfCallBackServiceI) SpringContextHolder.getBean(serviceImplName);

            FlowNextDto flowNextDto = this.getNextFlowNode(taskBo.getTaskId(), taskBo.getVariables());
            if(flowNextDto != null) {
            	//**有下一个节点
                UserTask nextUserTask = flowNextDto.getUserTask();
                //能够处理下个节点的候选人
                List<SysUserVo> nextFlowNodeUserList = flowNextDto.getUserList();
                List<String> newusername = new ArrayList<String>();
                if(nextFlowNodeUserList != null &&  nextFlowNodeUserList.get(0) != null ) {
    	            List<String> collect_username = nextFlowNodeUserList.stream().filter(Objects::nonNull).filter(item->item.getUserName()!=null).map(SysUserVo::getUserName).collect(Collectors.toList());
    	            //collect_username转换成realname
    	            // 流程发起人
    	            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(taskBo.getProcInsId()).singleResult();
    	            String startUserId = processInstance.getStartUserId();
    	            if(taskBo.getVariables() !=null && taskBo.getVariables().containsKey("approval")) {//前端传回的变量值
    	            	SysUserVo sysUser = commonService.getSysUserByUserName(taskBo.getVariables().get("approval").toString());
    	            	newusername.add(sysUser.getNickName());
    	            }
    	            else {
    	            	for (String oldUser : collect_username) {
    	                  if(StrUtil.equalsAnyIgnoreCase(oldUser,"${INITIATOR}")) {
    	                	  SysUserVo sysUser = commonService.getSysUserByUserName(startUserId);
    	                      newusername.add(sysUser.getNickName());
    	                  }
    	                  else {
    	                	 SysUserVo sysUser = commonService.getSysUserByUserName(oldUser);
    	                     newusername.add(sysUser.getNickName());
    	                  }
    	                }
    	            }
                }

                //下一个实例节点
                List<Task> listtask = taskService.createTaskQuery().processInstanceId(business.getProcessInstanceId()).active().list();
                Task nexttask = null;
                if(listtask.size()==1) {
                	nexttask = taskService.createTaskQuery().processInstanceId(business.getProcessInstanceId()).active().singleResult();
                }
                else {
                	nexttask = taskService.createTaskQuery().processInstanceId(business.getProcessInstanceId()).active().list().get(0);
                }

                if(nextFlowNodeUserList !=null) {
                	business.setActStatus(ActStatus.doing);
                	business.setTaskId(nexttask.getId());
                	business.setTaskNameId(nextUserTask.getId());
                	business.setTaskName(nextUserTask.getName());
                	business.setPriority(nextUserTask.getPriority());
                    business.setTodoUsers(JSON.toJSONString(newusername));
                }
                else {
                	business.setActStatus(ActStatus.doing);
                	business.setTaskId(nexttask.getId());
                	business.setTaskNameId("");
                	business.setTaskName("");
                	business.setPriority("");
                    business.setTodoUsers("");
                }
                /*if(ObjectUtil.isNotEmpty(taskBo.getNextApproval())) {
                	business.setActStatus(ActStatus.doing);
                	business.setTaskId(nexttask.getId());
                	business.setTaskNameId(nextUserTask.getId());
                	business.setTaskName(nextUserTask.getName());
                	business.setPriority(nextUserTask.getPriority());
                    business.setTodoUsers(taskBo.getNextApproval());
                }*/
            }
            else {//**没有下一个节点，流程已经结束了
            	business.setActStatus(ActStatus.pass);
            	business.setTaskId("");
            	business.setTaskNameId("");
            	business.setTaskName("");
                business.setTodoUsers("");
            }
            // 流程处理完后，进行回调业务层
            wfMyBusinessService.updateById(business);
            if (flowCallBackService!=null)flowCallBackService.afterFlowHandle(business);
        }

        // 获取 bpmn 模型
        BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        if (DelegationState.PENDING.equals(task.getDelegationState())) {
            taskService.addComment(taskBo.getTaskId(), taskBo.getProcInsId(), FlowComment.DELEGATE.getType(), taskBo.getComment());
            taskService.resolveTask(taskBo.getTaskId());
        } else {
            taskService.addComment(taskBo.getTaskId(), taskBo.getProcInsId(), FlowComment.NORMAL.getType(), taskBo.getComment());
            taskService.setAssignee(taskBo.getTaskId(), TaskUtils.getUserName());
            if (ObjectUtil.isNotEmpty(taskBo.getVariables())) {
                // 获取模型信息
                String localScopeValue = ModelUtils.getUserTaskAttributeValue(bpmnModel, task.getTaskDefinitionKey(), ProcessConstants.PROCESS_FORM_LOCAL_SCOPE);
                boolean localScope = Convert.toBool(localScopeValue, false);
                taskService.complete(taskBo.getTaskId(), taskBo.getVariables(), localScope);
            } else {
                taskService.complete(taskBo.getTaskId());
            }
        }
        // 设置任务节点名称
        taskBo.setTaskName(task.getName());

        // 处理下一级审批人
        if (StringUtils.isNotBlank(taskBo.getNextUserIds())) {
            this.assignNextUsers(bpmnModel, taskBo.getProcInsId(), taskBo.getNextUserIds());
        }

        //加签处理
        addSignForComplete(taskBo,taskEntity);

        // 处理抄送用户
        if (!copyService.makeCopy(taskBo)) {
            throw new RuntimeException("抄送任务失败");
        }
    }

    /**
     * 获取部署表单信息
     *
     * @param processDefinitionId 流程定义ID
     * @param nodeKey 节点Key
     * @return 部署表单信息
     */
    private WfDeployForm getDeployForm(String processDefinitionId, String nodeKey) {
        try {
            log.info("🔍 [DEBUG] getDeployForm - 查询参数: processDefinitionId={}, nodeKey={}", processDefinitionId, nodeKey);

            // 根据流程定义ID和节点Key查询表单配置
            LambdaQueryWrapper<WfDeployForm> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(WfDeployForm::getDeployId, processDefinitionId)
                       .eq(WfDeployForm::getNodeKey, nodeKey);

            WfDeployForm result = wfDeployFormMapper.selectOne(queryWrapper);
            log.info(" [DEBUG] getDeployForm - 查询结果: {}", result);

            return result;
        } catch (Exception e) {
            log.error("🔍 [ERROR] getDeployForm - 获取部署表单信息失败", e);
            return null;
        }
    }

    /**
     * 保存节点表单数据到业务表
     * 直接调用现有的 FormService 方法，复用发起任务时的保存逻辑
     *
     * @param tableName 表名
     * @param variables 表单数据
     */
    private void saveNodeFormDataToTable(String tableName, Map<String, Object> variables) {
        try {
            // 直接调用现有的 FormService 方法
            IFormService formService = SpringContextHolder.getBean(IFormService.class);
            int result = formService.addOrUpdateOnlineDataByName(tableName, variables);

            log.info("节点表单数据保存成功，表名：{}，影响行数：{}", tableName, result);

        } catch (Exception e) {
            log.error("保存节点表单数据失败，表名：{}，错误：{}", tableName, e.getMessage(), e);
            throw new RuntimeException("保存节点表单数据失败：" + e.getMessage());
        }
    }

    /**
     * 处理公共字段
     */
    private void handleCommonFields(Map<String, Object> data, boolean isInsert) {
        String currentUser = TaskUtils.getUserName();
        String currentTime = DateUtils.getTime();

        if (isInsert) {
            data.put("create_by", currentUser);
            data.put("create_time", currentTime);
            data.put("create_dept", getCurrentDeptId());
        }

        data.put("update_by", currentUser);
        data.put("update_time", currentTime);
        data.put("tenant_id", TenantHelper.getTenantId());
    }

    /**
     * 设置SQL参数
     */
    private void setParameter(PreparedStatement preparedStatement, int index, Object value) throws SQLException {
        if (value instanceof String) {
            preparedStatement.setString(index, (String) value);
        } else if (value instanceof Integer) {
            preparedStatement.setInt(index, (Integer) value);
        } else if (value instanceof Boolean) {
            preparedStatement.setInt(index, (Boolean) value ? 1 : 0);
        } else if (value instanceof Double) {
            preparedStatement.setDouble(index, (Double) value);
        } else if (value instanceof Float) {
            preparedStatement.setFloat(index, (Float) value);
        } else if (value instanceof Long) {
            preparedStatement.setLong(index, (Long) value);
        } else if (value instanceof java.sql.Date) {
            preparedStatement.setDate(index, (java.sql.Date) value);
        } else if (value instanceof java.sql.Timestamp) {
            preparedStatement.setTimestamp(index, (java.sql.Timestamp) value);
        } else if (value instanceof ArrayList) {
            ArrayList<String> list = (ArrayList) value;
            String stringValue = list.toString().replaceAll("\\[|\\]", "");
            preparedStatement.setString(index, stringValue);
        } else {
            preparedStatement.setString(index, value != null ? value.toString() : null);
        }
    }

    /**
     * 获取当前部门ID
     */
    private String getCurrentDeptId() {
        try {
            LoginUser loginUser = commonService.getLoginUser();
            return loginUser.getDeptId() != null ? loginUser.getDeptId().toString() : "1";
        } catch (Exception e) {
            return "1";
        }
    }

  //流程审批处理加签任务
    void addSignForComplete(WfTaskBo taskVo, TaskEntity taskEntity) {
    	//查看当前任务是存在
        if (taskEntity == null) {
            throw new FlowableException("该任务id对应任务不存在！");
        }
        //处理加签父任务
        String parentTaskId = taskEntity.getParentTaskId();
        if (StringUtils.isNotBlank(parentTaskId)) {
            int subTaskCount = flowTaskMapper.querySubTaskByParentTaskId(parentTaskId);
            //如果没有其他子任务
            if (subTaskCount == 0) {
                Task task = processEngine.getTaskService()
                        .createTaskQuery()
                        .taskId(parentTaskId)
                        .singleResult();

                //处理前后加签的任务
                processEngine.getTaskService()
                        .resolveTask(parentTaskId);
                if ("after".equals(task.getScopeType())) {
                    processEngine.getTaskService()
                            .complete(parentTaskId);
                }
            }
        }
    }

    /**
     * 驳回任务
     *
     * @param taskBo
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void taskReject(WfTaskBo taskBo) {
    	// 当前任务 task
        Task task = taskService.createTaskQuery().taskId(taskBo.getTaskId()).singleResult();
        if (ObjectUtil.isNull(task)) {
            throw new RuntimeException("获取任务信息异常！");
        }
        if (task.isSuspended()) {
            throw new RuntimeException("任务处于挂起状态");
        }
        // 获取流程实例
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
        	.processInstanceTenantId(TenantHelper.getTenantId())
            .processInstanceId(taskBo.getProcInsId())
            .singleResult();
        if (processInstance == null) {
            throw new RuntimeException("流程实例不存在，请确认！");
        }
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
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
                }
            }
        }

        // 目的获取所有跳转到的节点 targetIds
        // 获取当前节点的所有父级用户任务节点
        // 深度优先算法思想：延边迭代深入
        List<UserTask> parentUserTaskList = FlowableUtils.iteratorFindParentUserTasks(source, null, null);
        if (parentUserTaskList == null || parentUserTaskList.size() == 0) {
            throw new RuntimeException("当前节点为初始任务节点，不能驳回");
        }
        // 获取活动 ID 即节点 Key
        List<String> parentUserTaskKeyList = new ArrayList<>();
        parentUserTaskList.forEach(item -> parentUserTaskKeyList.add(item.getId()));
        // 获取全部历史节点活动实例，即已经走过的节点历史，数据采用开始时间升序
        List<HistoricTaskInstance> historicTaskInstanceList = historyService.createHistoricTaskInstanceQuery().processInstanceId(task.getProcessInstanceId()).orderByHistoricTaskInstanceStartTime().asc().list();
        // 数据清洗，将回滚导致的脏数据清洗掉
        List<String> lastHistoricTaskInstanceList = FlowableUtils.historicTaskInstanceClean(allElements, historicTaskInstanceList);
        // 此时历史任务实例为倒序，获取最后走的节点
        List<String> targetIds = new ArrayList<>();
        // 循环结束标识，遇到当前目标节点的次数
        int number = 0;
        StringBuilder parentHistoricTaskKey = new StringBuilder();
        for (String historicTaskInstanceKey : lastHistoricTaskInstanceList) {
            // 当会签时候会出现特殊的，连续都是同一个节点历史数据的情况，这种时候跳过
            if (parentHistoricTaskKey.toString().equals(historicTaskInstanceKey)) {
                continue;
            }
            parentHistoricTaskKey = new StringBuilder(historicTaskInstanceKey);
            if (historicTaskInstanceKey.equals(task.getTaskDefinitionKey())) {
                number++;
            }
            // 在数据清洗后，历史节点就是唯一一条从起始到当前节点的历史记录，理论上每个点只会出现一次
            // 在流程中如果出现循环，那么每次循环中间的点也只会出现一次，再出现就是下次循环
            // number == 1，第一次遇到当前节点
            // number == 2，第二次遇到，代表最后一次的循环范围
            if (number == 2) {
                break;
            }
            // 如果当前历史节点，属于父级的节点，说明最后一次经过了这个点，需要退回这个点
            if (parentUserTaskKeyList.contains(historicTaskInstanceKey)) {
                targetIds.add(historicTaskInstanceKey);
            }
        }


        // 目的获取所有需要被跳转的节点 currentIds
        // 取其中一个父级任务，因为后续要么存在公共网关，要么就是串行公共线路
        UserTask oneUserTask = parentUserTaskList.get(0);
        // 获取所有正常进行的任务节点 Key，这些任务不能直接使用，需要找出其中需要驳回的任务
        List<Task> runTaskList = taskService.createTaskQuery().processInstanceId(task.getProcessInstanceId()).list();
        List<String> runTaskKeyList = new ArrayList<>();
        runTaskList.forEach(item -> runTaskKeyList.add(item.getTaskDefinitionKey()));
        // 需驳回任务列表
        List<String> currentIds = new ArrayList<>();
        // 通过父级网关的出口连线，结合 runTaskList 比对，获取需要驳回的任务
        List<UserTask> currentUserTaskList = FlowableUtils.iteratorFindChildUserTasks(oneUserTask, runTaskKeyList, null, null);
        currentUserTaskList.forEach(item -> currentIds.add(item.getId()));


        // 规定：并行网关之前节点必须需存在唯一用户任务节点，如果出现多个任务节点，则并行网关节点默认为结束节点，原因为不考虑多对多情况
        if (targetIds.size() > 1 && currentIds.size() > 1) {
            throw new RuntimeException("任务出现多对多情况，无法驳回");
        }

        // 循环获取那些需要被撤回的节点的ID，用来设置驳回原因
        List<String> currentTaskIds = new ArrayList<>();
        currentIds.forEach(currentId -> runTaskList.forEach(runTask -> {
            if (currentId.equals(runTask.getTaskDefinitionKey())) {
                currentTaskIds.add(runTask.getId());
            }
        }));
        // 设置驳回意见
        currentTaskIds.forEach(item -> taskService.addComment(item, task.getProcessInstanceId(), FlowComment.REJECT.getType(), taskBo.getComment()));

        try {
            // 设置处理人
            taskService.setAssignee(task.getId(), TaskUtils.getUserName());
            // 如果父级任务多于 1 个，说明当前节点不是并行节点，原因为不考虑多对多情况
            if (targetIds.size() > 1) {
                // 1 对 多任务跳转，currentIds 当前节点(1)，targetIds 跳转到的节点(多)
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(task.getProcessInstanceId()).
                        moveSingleActivityIdToActivityIds(currentIds.get(0), targetIds).changeState();
            }
            // 如果父级任务只有一个，因此当前任务可能为网关中的任务
            if (targetIds.size() == 1) {
                // 1 对 1 或 多 对 1 情况，currentIds 当前要跳转的节点列表(1或多)，targetIds.get(0) 跳转到的节点(1)
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(task.getProcessInstanceId())
                        .moveActivityIdsToSingleActivityId(currentIds, targetIds.get(0)).changeState();
            }

            //驳回到目标节点
            List<Task> listTask = taskService.createTaskQuery().processInstanceId(taskBo.getProcInsId()).active().list();
            if(listTask.size() == 1 ) {
            	Task targetTask = listTask.get(0);
                FlowElement targetElement = null;
                if (allElements != null) {
                    for (FlowElement flowElement : allElements) {
                        // 类型为用户节点
                        if (flowElement.getId().equals(targetTask.getTaskDefinitionKey())) {
                            // 获取节点信息
                            targetElement = flowElement;
                        }
                    }
                }

                // 流程发起人
                String startUserId = processInstance.getStartUserId();

                if (targetElement!=null){
                    UserTask targetUserTask = (UserTask) targetElement;

                    if (targetUserTask.getAssignee()!=null && StringUtils.equalsAnyIgnoreCase(targetUserTask.getAssignee().toString(),"${INITIATOR}", "${initiator}")) {//是否为发起人节点
                        //开始节点 设置处理人为申请人
                        taskService.setAssignee(targetTask.getId(), startUserId);
                    } else {
                        List<SysUserVo> sysUserFromTask = getSysUserFromTask(targetUserTask,startUserId);
                        List<String> collect_username = sysUserFromTask.stream().filter(Objects::nonNull).filter(item->item.getUserName()!=null).map(SysUserVo::getUserName).collect(Collectors.toList());
                        //collect_username转换成realname
                        List<String> newusername = new ArrayList<String>();
                        for (String oldUser : collect_username) {
                        	SysUserVo sysUser = commonService.getSysUserByUserName(oldUser);
                            newusername.add(sysUser.getNickName());
                        }

                        // 删除后重写
                        for (String oldUser : collect_username) {
                            taskService.deleteCandidateUser(targetTask.getId(),oldUser);
                        }

                        for (String oldUser : collect_username) {
                            taskService.addCandidateUser(targetTask.getId(),oldUser);
                        }

                        if(collect_username.size() ==1) {
                            targetTask.setAssignee(newusername.get(0).toString());
                            taskService.addUserIdentityLink(targetTask.getId(), collect_username.get(0).toString(), IdentityLinkType.ASSIGNEE);
                        }else if(collect_username.size() > 1){
                            List<HistoricActivityInstance> list = historyService
                                    .createHistoricActivityInstanceQuery()
                                    .activityId(targetTask.getTaskDefinitionKey())
                                    .orderByHistoricActivityInstanceStartTime()
                                    .desc().list();
                            for (HistoricActivityInstance historicActivityInstance : list) {
                                if (StrUtil.isNotBlank(historicActivityInstance.getAssignee())) {
                                    targetTask.setAssignee(historicActivityInstance.getAssignee());
                                    taskService.addUserIdentityLink(targetTask.getId(), historicActivityInstance.getAssignee(), IdentityLinkType.ASSIGNEE);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            else if(listTask.size()>1) {//多任务
               String startUserId = processInstance.getStartUserId();
               String definitionld = runtimeService.createProcessInstanceQuery().processInstanceId(listTask.get(0).getProcessInstanceId()).singleResult().getProcessDefinitionId();   //获取bpm（模型）对象
               BpmnModel bpmnModel = repositoryService.getBpmnModel(definitionld);
               //通过节点定义key获取当前节点
               FlowNode flowNode = (FlowNode) bpmnModel.getFlowElement(listTask.get(0).getTaskDefinitionKey());
               if(Objects.nonNull(flowNode)) {
                   if(flowNode instanceof UserTask ){//当前节点是用户任务
                   	UserTask userTask = (UserTask) flowNode;
                   	MultiInstanceLoopCharacteristics multiInstance = userTask.getLoopCharacteristics();
                   	if (Objects.nonNull(multiInstance) && !multiInstance.isSequential()) {//当前节点是会签而且是并发的话
                   		List<SysUserVo> sysUserFromTask = getSysUserFromTask(userTask,startUserId);
                   		List<String> userlist = sysUserFromTask.stream().filter(Objects::nonNull).filter(item->item.getUserName()!=null).map(SysUserVo::getUserName).collect(Collectors.toList());
                   		int i=0;
         				    for (Task nexttask : listTask) {
         					   String assignee = userlist.get(i).toString();
             			       taskService.setAssignee(nexttask.getId(), assignee);
             			       i++;
         				    }

                   	}
                   }
               }
           }

        } catch (FlowableObjectNotFoundException e) {
            throw new RuntimeException("未找到流程实例，流程可能已发生变化");
        } catch (FlowableException e) {
            throw new RuntimeException("无法取消或开始活动");
        }
    }

    public List<SysUserVo> getSysUserFromTask(UserTask userTask, String startUserId) {
        String assignee = userTask.getAssignee();
        if (StrUtil.isNotBlank(assignee) && !Objects.equals(assignee, "null") && !Objects.equals(assignee,"${assignee}")){
        	// 指定单人
        	if(StringUtils.equalsAnyIgnoreCase(assignee, "${INITIATOR}", "${initiator}")) {//对发起人做特殊处理
        		List<SysUserVo> list = new ArrayList<SysUserVo>();
        		SysUserVo sysUser = new SysUserVo();
        		sysUser.setUserName(startUserId);
        		return Lists.newArrayList(sysUser);
        	}
        	else {
              SysUserVo userByUsername = commonService.getSysUserByUserName(assignee);
              return Lists.newArrayList(userByUsername);
        	}

        }
        List<String> candidateUsers = userTask.getCandidateUsers();
        if (CollUtil.isNotEmpty(candidateUsers)){
            // 指定多人
            List<SysUserVo> list = commonService.getAllUser();
            return list.stream().filter(o->candidateUsers.contains(o.getUserName())).collect(Collectors.toList());
        }
        List<String> candidateGroups = userTask.getCandidateGroups();
        if (CollUtil.isNotEmpty(candidateGroups)){
        //    指定多组
            List<SysUserVo> userList = Lists.newArrayList();
            for (String candidateGroup : candidateGroups) {
                List<SysUserVo> usersByRoleId = commonService.getUserListByRoleId(candidateGroup);
                userList.addAll(usersByRoleId);
            }
            return userList;
        }
        return Lists.newArrayList();
    }

    /**
     * 拒绝任务
     *
     * @param taskBo
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void taskRefuse(WfTaskBo taskBo) {
        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(taskBo.getTaskId()).singleResult();
        if (ObjectUtil.isNull(task)) {
            throw new RuntimeException("获取任务信息异常！");
        }
        if (task.isSuspended()) {
            throw new RuntimeException("任务处于挂起状态");
        }
        // 获取流程实例
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
        	.processInstanceTenantId(TenantHelper.getTenantId())
            .processInstanceId(taskBo.getProcInsId())
            .singleResult();
        if (processInstance == null) {
            throw new RuntimeException("流程实例不存在，请确认！");
        }
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(task.getProcessDefinitionId())
            .singleResult();

        // 添加审批意见
        taskService.addComment(taskBo.getTaskId(), taskBo.getProcInsId(), FlowComment.REFUSE.getType(), taskBo.getComment());
        // 设置流程状态为已终结
        runtimeService.setVariable(processInstance.getId(), ProcessConstants.PROCESS_STATUS_KEY, ProcessStatus.TERMINATED.getStatus());
        // 获取所有节点信息
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        EndEvent endEvent = ModelUtils.getEndEvent(bpmnModel);
        // 终止流程
        List<Execution> executions = runtimeService.createExecutionQuery().parentId(task.getProcessInstanceId()).list();
        List<String> executionIds = executions.stream().map(Execution::getId).collect(Collectors.toList());
        runtimeService.createChangeActivityStateBuilder()
            .processInstanceId(task.getProcessInstanceId())
            .moveExecutionsToSingleActivityId(executionIds, endEvent.getId())
            .changeState();
        // 处理抄送用户
        if (!copyService.makeCopy(taskBo)) {
            throw new RuntimeException("抄送任务失败");
        }
    }

    /**
     * 退回任务
     *
     * @param bo 请求实体参数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void taskReturn(WfTaskBo bo) {
        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
        if (ObjectUtil.isNull(task)) {
            throw new RuntimeException("获取任务信息异常！");
        }
        if (task.isSuspended()) {
            throw new RuntimeException("任务处于挂起状态");
        }
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
        // 获取流程模型信息
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        // 获取当前任务节点元素
        FlowElement source = ModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        // 获取跳转的节点元素
        FlowElement target = ModelUtils.getFlowElementById(bpmnModel, bo.getTargetKey());
        // 从当前节点向前扫描，判断当前节点与目标节点是否属于串行，若目标节点是在并行网关上或非同一路线上，不可跳转
        boolean isSequential = ModelUtils.isSequentialReachable(source, target, new HashSet<>());
        if (!isSequential) {
            throw new RuntimeException("当前节点相对于目标节点，不属于串行关系，无法回退");
        }

        // 获取所有正常进行的任务节点 Key，这些任务不能直接使用，需要找出其中需要撤回的任务
        List<Task> runTaskList = taskService.createTaskQuery().processInstanceId(task.getProcessInstanceId()).list();
        List<String> runTaskKeyList = new ArrayList<>();
        runTaskList.forEach(item -> runTaskKeyList.add(item.getTaskDefinitionKey()));
        // 需退回任务列表
        List<String> currentIds = new ArrayList<>();
        // 通过父级网关的出口连线，结合 runTaskList 比对，获取需要撤回的任务
        List<UserTask> currentUserTaskList = FlowableUtils.iteratorFindChildUserTasks(target, runTaskKeyList, null, null);
        currentUserTaskList.forEach(item -> currentIds.add(item.getId()));

        // 循环获取那些需要被撤回的节点的ID，用来设置驳回原因
        List<String> currentTaskIds = new ArrayList<>();
        currentIds.forEach(currentId -> runTaskList.forEach(runTask -> {
            if (currentId.equals(runTask.getTaskDefinitionKey())) {
                currentTaskIds.add(runTask.getId());
            }
        }));
        // 设置回退意见
        for (String currentTaskId : currentTaskIds) {
            taskService.addComment(currentTaskId, task.getProcessInstanceId(), FlowComment.REBACK.getType(), bo.getComment());
        }

        try {
            // 1 对 1 或 多 对 1 情况，currentIds 当前要跳转的节点列表(1或多)，targetKey 跳转到的节点(1)
            runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdsToSingleActivityId(currentIds, bo.getTargetKey()).changeState();
        } catch (FlowableObjectNotFoundException e) {
            throw new RuntimeException("未找到流程实例，流程可能已发生变化");
        } catch (FlowableException e) {
            throw new RuntimeException("无法取消或开始活动");
        }
        // 设置任务节点名称
        bo.setTaskName(task.getName());
        // 处理抄送用户
        if (!copyService.makeCopy(bo)) {
            throw new RuntimeException("抄送任务失败");
        }
    }


    /**
     * 获取所有可回退的节点
     *
     * @param bo
     * @return
     */
    @Override
    public List<UserTask> findReturnTaskList(WfTaskBo bo) {
    	// 当前任务 task
        Task task = taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
        // 获取所有节点信息，暂不考虑子流程情况
        Process process = repositoryService.getBpmnModel(processDefinition.getId()).getProcesses().get(0);
        Collection<FlowElement> flowElements = process.getFlowElements();
        // 获取当前任务节点元素
        UserTask source = null;
        if (flowElements != null) {
            for (FlowElement flowElement : flowElements) {
                // 类型为用户节点
                if (flowElement.getId().equals(task.getTaskDefinitionKey())) {
                    source = (UserTask) flowElement;
                }
            }
        }
        // 获取节点的所有路线
        List<List<UserTask>> roads = FlowableUtils.findRoad(source, null, null, null);
        // 可回退的节点列表
        List<UserTask> userTaskList = new ArrayList<>();
        for (List<UserTask> road : roads) {
            if (userTaskList.size() == 0) {
                // 还没有可回退节点直接添加
                userTaskList = road;
            } else {
                // 如果已有回退节点，则比对取交集部分
                userTaskList.retainAll(road);
            }
        }
        return userTaskList;
    }

    @Override
	@Transactional(rollbackFor = Exception.class)
	public void taskJump(WfTaskBo bo) {
		//校验任务是否存在
		Task task = taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
        //当前节点id
        String currentActId = task.getTaskDefinitionKey();
        //获取流程实例id
        String processInstanceId = task.getProcessInstanceId();
        //当前活动节点名称（任务名称）
        String currentActName = task.getName();
        //获取当前操作人姓名
        String name = LoginHelper.getUsername();
        String type = FlowComment.JUMP.getType();
        //添加跳转意见 name + "将任务跳转到【" + targetActName + "】，跳转原因：" + comment + ";";
        taskService.addComment(task.getId(), processInstanceId, type,"当前任务["+currentActName +"]由" + name + "跳转到[" + bo.getTargetActName() + "]，跳转原因：" + bo.getComment());
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
        // 获取流程模型信息
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        // 获取当前任务节点元素
        FlowElement source = ModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
        // 获取跳转的节点元素
        FlowElement target = ModelUtils.getFlowElementById(bpmnModel, bo.getTargetActId());
        // 从当前节点向前扫描，判断当前节点与目标节点是否属于串行，若目标节点是在并行网关上或非同一路线上，不可跳转
        boolean isSequential = ModelUtils.isSequentialReachable(source, target, new HashSet<>());
        if (!isSequential) {
            throw new RuntimeException("当前节点相对于目标节点，不属于串行关系，无法回退");
        }
		//执行跳转操作
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(currentActId, bo.getTargetActId()).changeState();

	}

    /**
     * 删除任务
     *
     * @param bo 请求实体参数
     */
    @Override
    public void deleteTask(WfTaskBo bo) {
        // todo 待确认删除任务是物理删除任务 还是逻辑删除，让这个任务直接通过？
        taskService.deleteTask(bo.getTaskId(), bo.getComment());
    }

    /**
     * 认领/签收任务
     *
     * @param taskBo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(WfTaskBo taskBo) {
        Task task = taskService.createTaskQuery().taskId(taskBo.getTaskId()).singleResult();
        if (Objects.isNull(task)) {
            throw new ServiceException("任务不存在");
        }
        taskService.claim(taskBo.getTaskId(), TaskUtils.getUserName());
    }

    /**
     * 取消认领/签收任务
     *
     * @param bo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unClaim(WfTaskBo bo) {
        taskService.unclaim(bo.getTaskId());
    }

    /**
     * 委派任务
     *
     * @param bo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(WfTaskBo bo) {
        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
        if (ObjectUtil.isEmpty(task)) {
            throw new ServiceException("获取任务失败！");
        }
        StringBuilder commentBuilder = new StringBuilder(LoginHelper.getUsername())
            .append("->");
        String nickName = userService.selectUserNameById(Long.parseLong(bo.getUserId()));
        if (StringUtils.isNotBlank(nickName)) {
            commentBuilder.append(nickName);
        } else {
            commentBuilder.append(bo.getUserId());
        }
        if (StringUtils.isNotBlank(bo.getComment())) {
            commentBuilder.append(": ").append(bo.getComment());
        }
        // 添加审批意见
        taskService.addComment(bo.getTaskId(), task.getProcessInstanceId(), FlowComment.DELEGATE.getType(), commentBuilder.toString());
        // 设置办理人为当前登录人
        taskService.setOwner(bo.getTaskId(), TaskUtils.getUserName());
        // 执行委派
        String userName = userService.selectUserNameById(Long.parseLong(bo.getUserId()));
        taskService.delegateTask(bo.getTaskId(), userName);
        // 设置任务节点名称
        bo.setTaskName(task.getName());
        // 处理抄送用户
        if (!copyService.makeCopy(bo)) {
            throw new RuntimeException("抄送任务失败");
        }
    }


    /**
     * 转办任务
     *
     * @param bo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(WfTaskBo bo) {
        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
        if (ObjectUtil.isEmpty(task)) {
            throw new ServiceException("获取任务失败！");
        }
        StringBuilder commentBuilder = new StringBuilder(LoginHelper.getUsername())
            .append("->");
        String nickName = userService.selectUserNameById(Long.parseLong(bo.getUserId()));
        if (StringUtils.isNotBlank(nickName)) {
            commentBuilder.append(nickName);
        } else {
            commentBuilder.append(bo.getUserId());
        }
        if (StringUtils.isNotBlank(bo.getComment())) {
            commentBuilder.append(": ").append(bo.getComment());
        }
        // 添加审批意见
        taskService.addComment(bo.getTaskId(), task.getProcessInstanceId(), FlowComment.TRANSFER.getType(), commentBuilder.toString());
        // 设置拥有者为当前登录人
        taskService.setOwner(bo.getTaskId(), TaskUtils.getUserName());
        // 转办任务
        String userName = userService.selectUserNameById(Long.parseLong(bo.getUserId()));
        taskService.setAssignee(bo.getTaskId(), userName);
        // 设置任务节点名称
        bo.setTaskName(task.getName());
        // 处理抄送用户
        if (!copyService.makeCopy(bo)) {
            throw new RuntimeException("抄送任务失败");
        }
    }

    /**
     * 取消申请
     *
     * @param bo
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopProcess(WfTaskBo bo) {
        List<Task> task = taskService.createTaskQuery().processInstanceId(bo.getProcInsId()).list();
        if (CollectionUtils.isEmpty(task)) {
            throw new RuntimeException("流程未启动或已执行完成，取消申请失败");
        }

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
        	.processInstanceTenantId(TenantHelper.getTenantId())
            .processInstanceId(bo.getProcInsId()).singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        if (Objects.nonNull(bpmnModel)) {
            Process process = bpmnModel.getMainProcess();
            List<EndEvent> endNodes = process.findFlowElementsOfType(EndEvent.class, false);
            if (CollectionUtils.isNotEmpty(endNodes)) {
                Authentication.setAuthenticatedUserId(TaskUtils.getUserName());
                // 获取当前流程最后一个节点
                taskService.addComment(task.get(0).getId(), processInstance.getProcessInstanceId(), FlowComment.STOP.getType(),
                        StringUtils.isBlank(bo.getComment()) ? "取消申请" : bo.getComment());
                String endId = endNodes.get(0).getId();
                List<Execution> executions = runtimeService.createExecutionQuery()
                    .parentId(processInstance.getProcessInstanceId()).list();
                List<String> executionIds = new ArrayList<>();
                executions.forEach(execution -> executionIds.add(execution.getId()));
                //设置流程状态为已取消
                runtimeService.setVariable(bo.getProcInsId(), ProcessConstants.PROCESS_STATUS_KEY, ProcessStatus.CANCELED.getStatus());
                // 变更流程为已结束状态
                runtimeService.createChangeActivityStateBuilder()
                    .moveExecutionsToSingleActivityId(executionIds, endId).changeState();
                String dataId = bo.getDataId();
                if(StringUtils.isNotEmpty(dataId)) {
                	WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
                	//删除自定义业务任务关联表与流程历史表，以便可以重新发起流程。
                	if (business != null) {
                		business.setActStatus(ActStatus.stop);
                		business.setTodoUsers("");
                		business.setDoneUsers("");
                		business.setProposer("");
                		business.setTaskName("");
                		business.setTaskId("");
                		business.setTaskNameId("");
                    	wfMyBusinessService.updateById(business);
                    }
                }
            }
        }
    }

    /**
     * 撤回流程
     *
     * @param taskBo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeProcess(WfTaskBo taskBo) {
        String procInsId = taskBo.getProcInsId();
        String taskId = taskBo.getTaskId();
        // 校验流程是否结束
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
        	.processInstanceTenantId(TenantHelper.getTenantId())
            .processInstanceId(procInsId)
            .active()
            .singleResult();
        if(ObjectUtil.isNull(processInstance)) {
            throw new RuntimeException("流程已结束或已挂起，无法执行撤回操作");
        }
        // 获取待撤回任务实例
        HistoricTaskInstance currTaskIns = historyService.createHistoricTaskInstanceQuery()
            .taskId(taskId)
            .taskAssignee(TaskUtils.getUserName())
            .singleResult();
        if (ObjectUtil.isNull(currTaskIns)) {
            throw new RuntimeException("当前任务不存在，无法执行撤回操作");
        }
        // 获取 bpmn 模型
        BpmnModel bpmnModel = repositoryService.getBpmnModel(currTaskIns.getProcessDefinitionId());
        UserTask currUserTask = ModelUtils.getUserTaskByKey(bpmnModel, currTaskIns.getTaskDefinitionKey());
        // 查找下一级用户任务列表
        List<UserTask> nextUserTaskList = ModelUtils.findNextUserTasks(currUserTask);
        List<String> nextUserTaskKeys = nextUserTaskList.stream().map(UserTask::getId).collect(Collectors.toList());

        // 获取当前节点之后已完成的流程历史节点
        List<HistoricTaskInstance> finishedTaskInsList = historyService.createHistoricTaskInstanceQuery()
            .processInstanceId(procInsId)
            .taskCreatedAfter(currTaskIns.getEndTime())
            .finished()
            .list();
        for (HistoricTaskInstance finishedTaskInstance : finishedTaskInsList) {
            // 检查已完成流程历史节点是否存在下一级中
            if (CollUtil.contains(nextUserTaskKeys, finishedTaskInstance.getTaskDefinitionKey())) {
                throw new RuntimeException("下一流程已处理，无法执行撤回操作");
            }
        }
        // 获取所有激活的任务节点，找到需要撤回的任务
        List<Task> activateTaskList = taskService.createTaskQuery().processInstanceId(procInsId).list();
        List<String> revokeExecutionIds = new ArrayList<>();
        for (Task task : activateTaskList) {
            // 检查激活的任务节点是否存在下一级中，如果存在，则加入到需要撤回的节点
            if (CollUtil.contains(nextUserTaskKeys, task.getTaskDefinitionKey())) {
                // 添加撤回审批信息
                taskService.setAssignee(task.getId(), TaskUtils.getUserName());
                taskService.addComment(task.getId(), task.getProcessInstanceId(), FlowComment.REVOKE.getType(), LoginHelper.getUsername() + "撤回流程审批");
                revokeExecutionIds.add(task.getExecutionId());
            }
        }
        try {
            runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(procInsId)
                .moveExecutionsToSingleActivityId(revokeExecutionIds, currTaskIns.getTaskDefinitionKey()).changeState();
            String dataId = taskBo.getDataId();
            if(StringUtils.isNotEmpty(dataId)) {
            	//当前任务信息
                List<Task> listtask = taskService.createTaskQuery().processInstanceId(procInsId).taskAssignee(TaskUtils.getUserName()).list();
            	WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
            	//更新自定义业务任务关联表与流程历史表，以便可以重新发起流程。
            	if (business != null) {
            		if (listtask != null && listtask.size()>0) {
            			business.setActStatus(ActStatus.revoke);
                		business.setTodoUsers(listtask.get(0).getAssignee());
            		}
            		else {
            			business.setActStatus(ActStatus.revoke);
	            		business.setTodoUsers("");
	            		business.setDoneUsers("");
	            		business.setProposer("");
	            		business.setTaskName("");
	            		business.setTaskId("");
	            		business.setTaskNameId("");
            		}

                	wfMyBusinessService.updateById(business);
                }
            }
        } catch (FlowableObjectNotFoundException e) {
            throw new RuntimeException("未找到流程实例，流程可能已发生变化");
        } catch (FlowableException e) {
            throw new RuntimeException("执行撤回操作失败");
        }
    }

    /**
     * 获取流程过程图
     *
     * @param processId
     * @return
     */
    @Override
    public InputStream diagram(String processId) {
        String processDefinitionId;
        // 获取当前的流程实例
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
        		.processInstanceTenantId(TenantHelper.getTenantId())
        		.processInstanceId(processId).singleResult();
        // 如果流程已经结束，则得到结束节点
        if (Objects.isNull(processInstance)) {
            HistoricProcessInstance pi = historyService.createHistoricProcessInstanceQuery().processInstanceId(processId).singleResult();

            processDefinitionId = pi.getProcessDefinitionId();
        } else {// 如果流程没有结束，则取当前活动节点
            // 根据流程实例ID获得当前处于活动状态的ActivityId合集
            ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
            processDefinitionId = pi.getProcessDefinitionId();
        }

        // 获得活动的节点
        List<HistoricActivityInstance> highLightedFlowList = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(processId).orderByHistoricActivityInstanceStartTime().asc().list();

        List<String> highLightedFlows = new ArrayList<>();
        List<String> highLightedNodes = new ArrayList<>();
        //高亮线
        for (HistoricActivityInstance tempActivity : highLightedFlowList) {
            if ("sequenceFlow".equals(tempActivity.getActivityType())) {
                //高亮线
                highLightedFlows.add(tempActivity.getActivityId());
            } else {
                //高亮节点
                highLightedNodes.add(tempActivity.getActivityId());
            }
        }

        //获取流程图
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        ProcessEngineConfiguration configuration = processEngine.getProcessEngineConfiguration();
        //获取自定义图片生成器
        ProcessDiagramGenerator diagramGenerator = new CustomProcessDiagramGenerator();
        return diagramGenerator.generateDiagram(bpmnModel, "png", highLightedNodes, highLightedFlows, configuration.getActivityFontName(),
            configuration.getLabelFontName(), configuration.getAnnotationFontName(), configuration.getClassLoader(), 1.0, true);

    }

    /**
     * 获取流程变量
     *
     * @param taskId 任务ID
     * @return 流程变量
     */
    @Override
    public Map<String, Object> getProcessVariables(String taskId) {
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
     * 启动第一个任务
     * @param processInstance 流程实例
     * @param variables 流程参数
     */
    @Override
    public void startFirstTask(ProcessInstance processInstance, Map<String, Object> variables) {
        // 若第一个用户任务为发起人，则自动完成任务
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).list();
        if (CollUtil.isNotEmpty(tasks)) {
            String userIdStr = (String) variables.get(TaskConstants.PROCESS_INITIATOR);
            for (Task task : tasks) {
                if (StrUtil.equals(task.getAssignee(), userIdStr)) {
                    taskService.addComment(task.getId(), processInstance.getProcessInstanceId(), FlowComment.NORMAL.getType(), LoginHelper.getUsername() + "发起流程申请");
                    taskService.complete(task.getId(), variables);
                }
            }
        }
    }

    /**
     * 指派下一任务审批人
     * @param bpmnModel bpmn模型
     * @param processInsId 流程实例id
     * @param userIds 用户ids
     */
    private void assignNextUsers(BpmnModel bpmnModel, String processInsId, String userIds) {
        // 获取所有节点信息
        List<Task> list = taskService.createTaskQuery()
            .processInstanceId(processInsId)
            .list();
        if (list.size() == 0) {
            return;
        }
        Queue<String> assignIds = CollUtil.newLinkedList(userIds.split(","));
        if (list.size() == assignIds.size()) {
            for (Task task : list) {
                taskService.setAssignee(task.getId(), assignIds.poll());
            }
            return;
        }
        // 优先处理非多实例任务
        Iterator<Task> iterator = list.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (!ModelUtils.isMultiInstance(bpmnModel, task.getTaskDefinitionKey())) {
                if (!assignIds.isEmpty()) {
                    taskService.setAssignee(task.getId(), assignIds.poll());
                }
                iterator.remove();
            }
        }
        // 若存在多实例任务，则进行动态加减签
        if (CollUtil.isNotEmpty(list)) {
            if (assignIds.isEmpty()) {
                // 动态减签
                for (Task task : list) {
                    runtimeService.deleteMultiInstanceExecution(task.getExecutionId(), true);
                }
            } else {
                // 动态加签
                for (String assignId : assignIds) {
                    Map<String, Object> assignVariables = Collections.singletonMap(BpmnXMLConstants.ATTRIBUTE_TASK_USER_ASSIGNEE, assignId);
                    runtimeService.addMultiInstanceExecution(list.get(0).getTaskDefinitionKey(), list.get(0).getProcessInstanceId(), assignVariables);
                }
            }
        }
    }

    /**
     * 获取下一节点
     *
     * @param flowTaskVo 任务
     * @return
     */
    @Override
    public R getNextFlowNode(WfTaskBo flowTaskVo) {
        // todo 目前只支持部分功能
        FlowNextDto flowNextDto = this.getNextFlowNode(flowTaskVo.getTaskId(), flowTaskVo.getVariables());
        if (flowNextDto==null) {
            return R.ok(null);
        }
        return R.ok(flowNextDto);

    }

    /**  modify by wanghuan
     * 获取下一个节点信息,流程定义上的节点信息
     * @param taskId 当前节点id
     * @param values 流程变量
     * @return 如果返回null，表示没有下一个节点，流程结束
     */

    public FlowNextDto getNextFlowNode(String taskId, Map<String, Object> values) {
    	//当前节点
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        FlowNextDto flowNextDto = new FlowNextDto();

    	if (Objects.nonNull(task)) {
        	// 下个任务节点
    		if (DelegationState.PENDING.equals(task.getDelegationState())) { //对于委派的处理
	        	List<UserTask> nextUserTask = FindNextNodeUtil.getNextUserTasks(repositoryService, task, values);
	            if (CollectionUtils.isNotEmpty(nextUserTask)) {
	            	flowNextDto.setType(ProcessConstants.FIXED);//委派是按原来流程执行，所以直接赋值返回
	            	return flowNextDto;
	            }
	            else {
	            	return null;
	            }

             }
            List<UserTask> nextUserTask = FindNextNodeUtil.getNextUserTasks(repositoryService, task, values);
            List<SysUserVo> list = new ArrayList<SysUserVo>();
            if (CollectionUtils.isNotEmpty(nextUserTask)) {
                for (UserTask userTask : nextUserTask) {
                    MultiInstanceLoopCharacteristics multiInstance = userTask.getLoopCharacteristics();
                    // 会签节点
                    if (Objects.nonNull(multiInstance)) {
                    	List<String> rolelist = new ArrayList<>();
                        rolelist = userTask.getCandidateGroups();
                    	List<String> userlist = new ArrayList<>();
                        userlist = userTask.getCandidateUsers();
                        UserTask newUserTask = userTask;
                        if(rolelist.size() != 0 && StringUtils.contains(rolelist.get(0), "${flowExp.getDynamic")) {//对表达式多个动态角色做特殊处理
                        	String methodname = StringUtils.substringBetween(rolelist.get(0), ".", "(");
                        	Object[] argsPara=new Object[]{};
                        	setMultiFlowExp(flowNextDto,newUserTask,multiInstance,methodname,argsPara);
                        }
                        else if(userlist.size() != 0 && StringUtils.contains(userlist.get(0), "${flowExp.getDynamic")) {//对表达式多个动态用户做特殊处理
                        	String methodname = StringUtils.substringBetween(userlist.get(0), ".", "(");
                        	Object[] argsPara=new Object[]{};
                        	setMultiFlowExp(flowNextDto,newUserTask,multiInstance,methodname,argsPara);
                        }
                        else if(userlist.size() != 0 && StringUtils.contains(userlist.get(0), "DepManagerHandler")) {//对部门经理做特殊处理
                        	String methodname = "getInitiatorDepManagers";
                        	// 获取流程发起人
	                   		ProcessInstance processInstance = runtimeService
	                                   .createProcessInstanceQuery()
	                                   .processInstanceTenantId(TenantHelper.getTenantId())
	                                   .processInstanceId(task.getProcessInstanceId())
	                                   .singleResult();
	                        String startUserId = processInstance.getStartUserId();
	                        Object[] argsPara=new Object[]{};
	                        argsPara=new Object[]{startUserId};
                        	setMultiFlowExp(flowNextDto,newUserTask,multiInstance,methodname,argsPara);
                        }
                        else if(rolelist.size() > 0) {
							for(String roleId : rolelist ){
                        	  List<SysUserVo> templist = commonService.getUserListByRoleId(roleId);
                        	  for(SysUserVo sysuser : templist) {
                          		SysUserVo sysUserTemp = sysUserService.selectUserById(sysuser.getUserId());
                          		list.add(sysUserTemp);
                          	  }
                        	}
							setMultiFlowNetDto(flowNextDto,list,userTask,multiInstance);
                        }
                        else if(userlist.size() > 0) {
                        	for(String username : userlist) {
                        		SysUserVo sysUser =  sysUserService.selectUserByUserName(username);
                        		list.add(sysUser);
                        	}
                        	setMultiFlowNetDto(flowNextDto,list,userTask,multiInstance);
                        }
                        else {
                        	flowNextDto.setType(ProcessConstants.FIXED);
                        }

                    } else {

                        // 读取自定义节点属性 判断是否是否需要动态指定任务接收人员、组,目前只支持用户角色或多用户，还不支持子流程和变量
                        //String dataType = userTask.getAttributeValue(ProcessConstants.NAMASPASE, ProcessConstants.PROCESS_CUSTOM_DATA_TYPE);
                        //String userType = userTask.getAttributeValue(ProcessConstants.NAMASPASE, ProcessConstants.PROCESS_CUSTOM_USER_TYPE);

                        List<String> rolelist = new ArrayList<>();
                        rolelist = userTask.getCandidateGroups();
                        List<String> userlist = new ArrayList<>();
                        userlist = userTask.getCandidateUsers();
                        String assignee = userTask.getAssignee();
                        // 处理加载动态指定下一节点接收人员信息
                        if(assignee !=null) {
                        	if(StringUtils.equalsAnyIgnoreCase(assignee, "${initiator}")) {//对发起人做特殊处理
                        		SysUserVo sysUser = new SysUserVo();
                        		sysUser.setUserName("${initiator}");
                        		list.add(sysUser);
                        		setAssigneeFlowNetDto(flowNextDto,list,userTask);
                        	} else if(StringUtils.equalsAnyIgnoreCase(assignee, "${DepManagerHandler.getUser(execution)}")) {//对部门经理做特殊处理
                        		SysUserVo sysUser = new SysUserVo();
                        		sysUser.setUserName("${DepManagerHandler.getUser(execution)}");
                        		list.add(sysUser);
                        		setAssigneeFlowNetDto(flowNextDto,list,userTask);
                        	}
                        	else if(StringUtils.contains(assignee, "${flowExp.getDynamicAssignee")) {//对表达式单个动态用户做特殊处理
                        		String methodname = StringUtils.substringBetween(assignee, ".", "(");
                        		SysUserVo sysUser = new SysUserVo();
                        		flowExp flowexp = SpringContextHolder.getBean(flowExp.class);
                        		Object[] argsPara=new Object[]{};
                        		String username = null;
                        		try {
									username = (String) flowexp.invokeMethod(flowexp, methodname,argsPara);
								} catch (Exception e) {
									e.printStackTrace();
								}
                        		sysUser.setUserName(username);
                        		list.add(sysUser);
                        		setAssigneeFlowNetDto(flowNextDto,list,userTask);
                        	}
                        	else if(StringUtils.contains(assignee, "${flowExp.getDynamicList")) {//对表达式多个动态用户做特殊处理
                        		String methodname = StringUtils.substringBetween(assignee, ".", "(");
                        		flowExp flowexp = SpringContextHolder.getBean(flowExp.class);
                        		Object[] argsPara=new Object[]{};
                        		try {
                        			list = (List<SysUserVo>) flowexp.invokeMethod(flowexp, methodname,argsPara);
								} catch (Exception e) {
									e.printStackTrace();
								}
                        		setUsersFlowNetDto(flowNextDto,list,userTask);

                        	}
                        	else {
                        	    SysUserVo sysUser =  sysUserService.selectUserByUserName(assignee);

                    		    list.add(sysUser);
                    		    setAssigneeFlowNetDto(flowNextDto,list,userTask);
                        	}

                        }
                        else if(userlist.size()>0 && StringUtils.equalsAnyIgnoreCase(userlist.get(0), "${DepManagerHandler.getUsers(execution)}")) {//对部门经理做特殊处理
	                   		// 获取流程发起人
	                   		ProcessInstance processInstance = runtimeService
	                                   .createProcessInstanceQuery()
	                                   .processInstanceTenantId(TenantHelper.getTenantId())
	                                   .processInstanceId(task.getProcessInstanceId())
	                                   .singleResult();
	                           String startUserId = processInstance.getStartUserId();
	                        flowExp flowexp = SpringContextHolder.getBean(flowExp.class);
	                   		String manager =  flowexp.getDynamicManager(startUserId);
	                   		SysUserVo sysUser =  sysUserService.selectUserByUserName(manager);
	                   		list.add(sysUser);
	                   		setUsersFlowNetDto(flowNextDto,list,userTask);
                        }
                        else if(userlist.size() > 0) {
                        	for(String username : userlist) {
                        		SysUserVo sysUser =  sysUserService.selectUserByUserName(username);

                        		list.add(sysUser);
                        	}
                        	setUsersFlowNetDto(flowNextDto,list,userTask);
                        	setMultiFinishFlag(task,flowNextDto,list);

                        }
                        else if(rolelist.size() > 0) {
							for(String roleId : rolelist ){
                        	  List<SysUserVo> templist =  commonService.getUserListByRoleId(roleId);
                        	  for(SysUserVo sysuser : templist) {
                          		SysUserVo sysUserTemp = sysUserService.selectUserByUserName(sysuser.getUserName());
                          		list.add(sysUserTemp);
                          	  }
                        	}
							setUsersFlowNetDto(flowNextDto,list,userTask);
							setMultiFinishFlag(task,flowNextDto,list);
                        }
                        else {
                        	flowNextDto.setType(ProcessConstants.FIXED);
                        }
                    }
                }
                return flowNextDto;
            } else {
                return null;
          }
       }
       return null;

    }

    //设置单用户下一节点流程数据
    private void setAssigneeFlowNetDto(FlowNextDto flowNextDto,List<SysUserVo> list,UserTask userTask) {
    	flowNextDto.setVars(ProcessConstants.PROCESS_APPROVAL);
	    flowNextDto.setType(ProcessConstants.USER_TYPE_ASSIGNEE);
	    flowNextDto.setUserList(list);
	    flowNextDto.setUserTask(userTask);
    }

    //设置多用户下一节点流程数据
    private void setUsersFlowNetDto(FlowNextDto flowNextDto,List<SysUserVo> list,UserTask userTask) {
    	flowNextDto.setVars(ProcessConstants.PROCESS_APPROVAL);
        flowNextDto.setType(ProcessConstants.USER_TYPE_USERS);
        flowNextDto.setUserList(list);
        flowNextDto.setUserTask(userTask);
    }

    //设置多实例结束标志
    private void setMultiFinishFlag(Task task,FlowNextDto flowNextDto,List<SysUserVo> list) {
    	String definitionld = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult().getProcessDefinitionId();        //获取bpm（模型）对象
        BpmnModel bpmnModel = repositoryService.getBpmnModel(definitionld);
        //通过节点定义key获取当前节点
        FlowNode flowNode = (FlowNode) bpmnModel.getFlowElement(task.getTaskDefinitionKey());
        if(flowNode instanceof UserTask ){
        	UserTask curuserTask = (UserTask) flowNode;
        	MultiInstanceLoopCharacteristics curmultiInstance = curuserTask.getLoopCharacteristics();
        	if (Objects.nonNull(curmultiInstance)) {
        		if(list.size()>1) {//多人选择的时候,从redis获取之前监听器写入的会签结束信息
        		   String smutinstance_next_finish = Constants.MUTIINSTANCE_NEXT_FINISH + task.getProcessInstanceId();
        	       if(Objects.nonNull(RedisUtils.getCacheObject(smutinstance_next_finish))) {
        		      flowNextDto.setBmutiInstanceFinish(true);
        	       }
                }
        	}
        }
    }

    //设置多实例流程表达式
    private void setMultiFlowExp(FlowNextDto flowNextDto,UserTask newUserTask,MultiInstanceLoopCharacteristics multiInstance,String methodname,Object[] argsPara) {
    	List<SysUserVo> list = new ArrayList<SysUserVo>();
		flowExp flowexp = SpringContextHolder.getBean(flowExp.class);
		//Object[] argsPara=new Object[]{};
		List<String> templist = new ArrayList<String>();
		try {
			templist = (List<String>) flowexp.invokeMethod(flowexp, methodname,argsPara);
		} catch (Exception e) {
			log.error("执行多实例流程表达式失败, method={}, args={}", methodname, argsPara, e);
		}
		for(String sysuser : templist) {
      		SysUserVo sysUserTemp = sysUserService.selectUserByUserName(sysuser);
      		list.add(sysUserTemp);
      	}
		newUserTask.setAssignee("${assignee}");
		newUserTask.setCandidateUsers(templist);
		setMultiFlowNetDto(flowNextDto,list,newUserTask,multiInstance);
    }

    //设置多实例流程数据
    private void setMultiFlowNetDto(FlowNextDto flowNextDto,List<SysUserVo> list,UserTask userTask,MultiInstanceLoopCharacteristics multiInstance) {
    	flowNextDto.setVars(ProcessConstants.PROCESS_MULTI_INSTANCE_USER);
        flowNextDto.setType(ProcessConstants.PROCESS_MULTI_INSTANCE);
        flowNextDto.setUserList(list);
        flowNextDto.setUserTask(userTask);
        if(multiInstance.isSequential()) {
        	flowNextDto.setBisSequential(true);
        }
        else {
        	flowNextDto.setBisSequential(false);
        }
    }

	@Override
	public boolean isFirstInitiator(String processInstanceId, String actStatusType) {
		if(StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.reject) ||
 	    	   StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.recall) ||
 	    	   StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.retrun) ||
 	    	   StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.revoke)) {
 		if(StringUtils.isNotEmpty(processInstanceId)) {
 		     //  获取当前任务
             Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
             if (task != null) {
            	 BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
		        //  获取当前活动节点
		        FlowNode currentFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(task.getTaskDefinitionKey());
		        // 输入连线
		        List<SequenceFlow> inFlows = currentFlowNode.getIncomingFlows();
		        for (SequenceFlow sequenceFlow : inFlows) {
		        	FlowElement sourceFlowElement = sequenceFlow.getSourceFlowElement();
		        	// 如果上个节点为开始节点
		            if (sourceFlowElement instanceof StartEvent) {
		            	log.info("当前节点为发起人节点,上个节点为开始节点：id=" + sourceFlowElement.getId() + ",name=" + sourceFlowElement.getName());
		                return true;
		            }
		        }
             }
 		}
 	}
		return false;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteActivityAndJoin(String dataId, String processInstanceId, String actStatusType) {
		if (dataId==null) return false;
        WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
        if(StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.reject) ||
 	    	   StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.recall) ||
 	    	   StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.retrun) ||
 	    	   StringUtils.equalsAnyIgnoreCase(actStatusType, ActStatus.revoke) ) {
            //  重新查询当前任务
            Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
            //删除自定义业务任务关联表与流程历史表，以便可以重新发起流程。
            if (business != null) {
            	wfMyBusinessService.removeById(business);
            	// 对自定义业务，删除运行和历史的节点信息
                this.deleteActivity(currentTask.getTaskDefinitionKey(), currentTask.getProcessInstanceId(), dataId);
                return true;
            }
        }
		return false;
	}

	/**
     * 删除跳转的历史节点信息
     *
     * @param disActivityId     跳转的节点id
     * @param processInstanceId 流程实例id
     * @param dataId   自定义业务id
     */
    protected void deleteActivity(String disActivityId, String processInstanceId, String dataId) {
        List<ActivityInstance> disActivities = flowTaskMapper
                .queryActivityInstance(disActivityId, processInstanceId, null);

        //删除运行时和历史节点信息
        if (CollectionUtils.isNotEmpty(disActivities)) {
            ActivityInstance activityInstance = disActivities.get(0);
            List<ActivityInstance> datas = flowTaskMapper
                    .queryActivityInstance(disActivityId, processInstanceId, activityInstance.getEndTime());

            //datas.remove(0); //保留流程发起节点信息
            List<String> runActivityIds = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(datas)) {
                datas.forEach(ai -> runActivityIds.add(ai.getId()));
                flowTaskMapper.deleteRunActinstsByIds(runActivityIds);
                flowTaskMapper.deleteHisActinstsByIds(runActivityIds);
            }
            if(dataId != null) {//对于自定义业务, 删除所有相关流程信息
            	//flowTaskMapper.deleteAllHisAndRun(processInstanceId);
                //根据流程实例id 删除去ACT_RU_*与ACT_HI_*流程实例数据
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                		.processInstanceTenantId(TenantHelper.getTenantId())
                		.processInstanceId(processInstanceId).singleResult();
                if (null != processInstance) {
                    runtimeService.deleteProcessInstance(processInstanceId, "流程实例删除");
                    historyService.deleteHistoricProcessInstance(processInstanceId);
                }
            }
        }
    }

    @Override
	public Map<String, List<ExtensionElement>> getSequenceFlowExtensionElement(String taskId) {
		Task task =  taskService.createTaskQuery().taskId(taskId).singleResult();
        if (ObjectUtil.isNotNull(task)) {

            Map<String, List<ExtensionElement>> extensionElements = MapUtil.newHashMap();

            HistoricTaskInstance historicTaskInstance = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(historicTaskInstance.getProcessDefinitionId()).active().singleResult();;
            Execution execution = runtimeService.createExecutionQuery().executionId(historicTaskInstance.getExecutionId()).singleResult();

            String activityId = execution.getActivityId();
            while (true) {
                //根据活动节点获取当前的组件信息
                FlowNode flowNode = getFlowNode(processDefinition.getId(), activityId);

                //获取该节点之后的流向
                List<SequenceFlow> sequenceFlowListOutGoing = flowNode.getOutgoingFlows();

                // 获取的下个节点不一定是userTask的任务节点，所以要判断是否是任务节点
                if (sequenceFlowListOutGoing.size() > 1) {
                    sequenceFlowListOutGoing.forEach(a -> extensionElements.putAll(a.getExtensionElements()));
                } else if (sequenceFlowListOutGoing.size() == 1) {
                    // 只有1条出线,直接取得下个节点
                    SequenceFlow sequenceFlow = sequenceFlowListOutGoing.get(0);
                    // 下个节点
                    FlowElement flowElement = sequenceFlow.getTargetFlowElement();
                    if (flowElement instanceof UserTask) {
                        return extensionElements;
                    } else if (flowElement instanceof ExclusiveGateway) {
                        // 下个节点为排它网关时
                        ExclusiveGateway exclusiveGateway = (ExclusiveGateway) flowElement;
                        List<SequenceFlow> outgoingFlows = exclusiveGateway.getOutgoingFlows();
                        outgoingFlows.forEach(a -> extensionElements.putAll(a.getExtensionElements()));
                        return extensionElements;
                    }

                } else {
                    // 没有出线，则表明是结束节点
                    return Collections.emptyMap();
                }
            }
        }
        return MapUtil.empty();
	}

    @Override
	public List<ExtensionElementInfo> getExtensionElement(String taskId) {
		Map<String, List<ExtensionElement>> extensionElements = getSequenceFlowExtensionElement(taskId);
        return FlowableUtils.getExtensionElement(extensionElements);
	}

    /**
     * 根据活动节点和流程定义ID获取该活动节点的组件信息
     */
    private FlowNode getFlowNode(String processDefinitionId, String flowElementId) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        return (FlowNode) bpmnModel.getMainProcess().getFlowElement(flowElementId);
    }

    @Override
	public Map<String, Object> getFlowProperties(String procInsId) {
		try {
            Task task = taskService.createTaskQuery().processInstanceId(procInsId).active().singleResult();
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
            List<FlowViewerDto> flowViewerList = (List<FlowViewerDto>) getFlowViewer(task.getProcessInstanceId()).getData();
            FlowViewerDto dto = flowViewerList.stream().filter(flowViewerDto -> !flowViewerDto.isCompleted()).findFirst().orElse(null);
            String actId;
            if (ObjectUtils.isNotEmpty(dto)) {
                actId = dto.getKey();
            } else {
                return null;
            }
            FlowElement element = bpmnModel.getFlowElement(actId);
            List<ExtensionElement> extensionElements = element
                    .getExtensionElements().get("properties");
            List<ExtensionElement> child = null;

            for (ExtensionElement extensionElement : extensionElements) {
                child = extensionElement.getChildElements().get("property");
            }
            List<Map<String, List<ExtensionAttribute>>> list = new ArrayList<>();
            child.stream().forEach(o -> {
                Map<String, List<ExtensionAttribute>> attributeMap = o.getAttributes();
                list.add(attributeMap);
            });
            Map<String, Object> result = new HashMap();
            list.stream().forEach(a -> {
                result.put(a.get("name").get(0).getValue(), a.get("value").get(0).getValue());
            });
            return result;
        } catch (NullPointerException nullExcption) {
            return null;
        }
	}
    /**
     * 获取流程执行过程
     *
     * @param procInsId
     * @return
     */
    @Override
    public R getFlowViewer(String procInsId) {
        List<FlowViewerDto> flowViewerList = new ArrayList<>();
        FlowViewerDto flowViewerDto;
        // 获得活动的节点
        List<HistoricActivityInstance> hisActIns = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInsId)
                .orderByHistoricActivityInstanceStartTime()
                .asc().list();
        for (HistoricActivityInstance activityInstance : hisActIns) {
            if (!"sequenceFlow".equals(activityInstance.getActivityType())) {
                flowViewerDto = new FlowViewerDto();
                flowViewerDto.setKey(activityInstance.getActivityId());
                flowViewerDto.setCompleted(!Objects.isNull(activityInstance.getEndTime()));
                flowViewerList.add(flowViewerDto);
            }
        }
        return R.ok(flowViewerList);
    }

	@Override
	public R userTaskList(WfTaskBo bo) {
		List<WfUserTaskVo> resultList = new ArrayList<WfUserTaskVo>();

        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();

        //根据流程定义获取deployment
        String deploymentId = processDefinition.getDeploymentId();
        Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
        if (ObjectUtil.isEmpty(deployment)) {
            throw new FlowableException("流程还没布置");
        }

        //获取bpmnModel并转为modelNode
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        //获取主流程
        Process mainProcess = bpmnModel.getMainProcess();
        //获取用户任务节点类型，深入子流程
        mainProcess.findFlowElementsOfType(UserTask.class, true).forEach(userTask -> {
        	WfUserTaskVo userTaskResult = new WfUserTaskVo();
        	userTaskResult.setId(userTask.getId());
        	userTaskResult.setProcessDefinitionId(processDefinition.getId());
        	userTaskResult.setName(userTask.getName());
        	resultList.add(userTaskResult);
        });
        return R.ok(resultList);
	}

	@Override
	public void addSignTask(WfTaskBo bo) {
		//登录用户
		String userName = LoginHelper.getUsername();
		String nickName = LoginHelper.getUsername();
		String[] userids = bo.getAddSignUsers().split(",");
		TaskEntityImpl taskEntity = (TaskEntityImpl) taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
		if (taskEntity != null) {
			if(StringUtils.equalsIgnoreCase(bo.getAddSignType(), "0"))
			{
				addTasksBefore(bo, taskEntity,userName,new HashSet<String>(Arrays.asList(userids)),nickName + "在[" + taskEntity.getName() +"]节点" + "前加签原因:"+ bo.getComment());
			}
			else {
				addTasksAfter(bo,taskEntity,userName,new HashSet<String>(Arrays.asList(userids)),nickName + "在[" + taskEntity.getName() +"]节点" + "后加签原因:"+ bo.getComment());
			}
		}
		else {
			Assert.notNull(taskEntity, "不存在任务实例，请确认!");
		}

	}

	@Override
	public void multiInstanceAddSign(WfTaskBo bo) {
		//校验任务是否存在
        Task task = taskService.createTaskQuery().taskId(bo.getTaskId()).singleResult();
        //流程定义id
        String processDefinitionId = task.getProcessDefinitionId();
        //流程实例id
        String processInstanceId = task.getProcessInstanceId();
        //当前活动节点id
        String currentActId = task.getTaskDefinitionKey();
        //当前活动节点名称（任务名称）
        String currentActName = task.getName();
        //多实例用户任务节点的元素变量名
        String multiInstanceActAssigneeParam = getMultiInstanceActAssigneeParam(processDefinitionId, currentActId);
        //如果元素变量名为空则表示该节点不是会签节点
        if (ObjectUtil.isEmpty(multiInstanceActAssigneeParam)) {
            throw new FlowableException("加签失败，该任务不是会签（或签）任务或节点配置错误");
        }
        //加签人的姓名
        List<String> assigneeNameList = CollectionUtil.newArrayList();
		String[] usernames = bo.getAddSignUsers().split(",");
		List<String> assigneeList = new ArrayList<String>();
		assigneeList = Arrays.asList(usernames);
        //遍历要加签的人
        assigneeList.forEach(assignee -> {
            //获取加签人名称
            String assigneeName = commonService.getSysUserByUserName(assignee).getNickName();
            assigneeNameList.add(assigneeName);
            //定义参数
            Map<String, Object> assigneeVariables = new HashMap<String, Object>();
            //根据获取的变量名加参数
            assigneeVariables.put(multiInstanceActAssigneeParam, assignee);
            //执行加签操作
            try {
                runtimeService.addMultiInstanceExecution(currentActId, processInstanceId, assigneeVariables);
            } catch (FlowableException e) {
                //抛异常加签失败
                throw new FlowableException("加签失败，该任务不是会签（或签）任务或节点配置错误");
            } catch (Exception e) {
                //否则的话，可能出现服务器内部异常
                throw new FlowableException("服务器出现异常，请联系管理员");
            }
        });
        //当前办理人姓名
        String name = LoginHelper.getUsername();
        //添加加签意见
        String type = FlowComment.DSLJQ.getType();
        taskService.addComment(task.getId(), processInstanceId, type, name + "加签:" + bo.getComment());

	}

	public String getMultiInstanceActAssigneeParam(String processDefinitionId, String actId) {
        AtomicReference<String> resultParam = new AtomicReference<>();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId).singleResult();
        //获取bpmnModel并转为modelNode
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        //获取主流程
        Process mainProcess = bpmnModel.getMainProcess();
        //获取用户任务节点类型，深入子流程
        mainProcess.findFlowElementsOfType(UserTask.class, true).forEach(userTask -> {
            String userTaskId = userTask.getId();
            if (userTaskId.equals(actId)) {
                Object behavior = userTask.getBehavior();
                if (ObjectUtil.isNotNull(behavior)) {
                    //并行多实例节点
                    if (behavior instanceof ParallelMultiInstanceBehavior) {
                        ParallelMultiInstanceBehavior parallelMultiInstanceBehavior =
                                (ParallelMultiInstanceBehavior) behavior;
                        String collectionElementVariable = parallelMultiInstanceBehavior
                                .getCollectionElementVariable();
                        if (ObjectUtil.isNotEmpty(collectionElementVariable)) {
                            resultParam.set(collectionElementVariable);
                        }
                    }
                    //串行多实例节点
                    if (behavior instanceof SequentialMultiInstanceBehavior) {
                        SequentialMultiInstanceBehavior sequentialMultiInstanceBehavior =
                                (SequentialMultiInstanceBehavior) behavior;
                        String collectionElementVariable = sequentialMultiInstanceBehavior
                                .getCollectionElementVariable();
                        if (ObjectUtil.isNotEmpty(collectionElementVariable)) {
                            resultParam.set(collectionElementVariable);
                        }
                    }
                }
            }
        });
        return resultParam.get();
    }

	@Override
	public void addTasksBefore(WfTaskBo bo, TaskEntityImpl taskEntity, String assignee, Set<String> assignees, String description) {
		addTask(bo, taskEntity, assignee, assignees, description, Boolean.FALSE);

	}

	@Override
	public void addTasksAfter(WfTaskBo bo,TaskEntityImpl taskEntity, String assignee, Set<String> assignees, String description) {
		addTask(bo, taskEntity, assignee, assignees, description, Boolean.TRUE);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void addTask(WfTaskBo bo,TaskEntityImpl taskEntity, String assignee, Set<String> assignees, String description,
			Boolean flag) {
        Assert.notNull(taskEntity, String.format("分配人 [%s] 没有待处理任务", assignee));

        //如果是加签再加签
        String parentTaskId = taskEntity.getParentTaskId();
        if (StrUtil.isBlank(parentTaskId)) {
        	taskEntity.setOwner(assignee);
        	taskEntity.setAssignee(null);
        	taskEntity.setCountEnabled(true);
            if (flag) {
            	taskEntity.setScopeType("after");
            } else {
            	taskEntity.setScopeType("before");
            }
            // 设置任务为空执行者
            taskService.saveTask(taskEntity);
        }
        //添加加签数据
        this.createSignSubTasks(assignee, assignees, taskEntity);
        //添加审批意见
        String type = flag ? FlowComment.HJQ.getType() : FlowComment.QJQ.getType();
        taskService.addComment(taskEntity.getId(), bo.getProcInsId(), type, description);

	}

	/**
     * 创建加签子任务
     * @param assignees 被加签人
     * @param assignee 加签人
     * @param taskEntity 父任务
     */
    private void createSignSubTasks(String assignee, Set<String> assignees, TaskEntity taskEntity) {
        if (CollectionUtil.isNotEmpty(assignees)) {
        	//1.创建被加签人的任务列表
            assignees.forEach(userId -> {
                if (StrUtil.isNotBlank(userId)) {
                	SysUserVo sysuser = commonService.getSysUserByUserId(Long.valueOf(userId));
                	String username = sysuser.getUserName();
                    this.createSubTask(taskEntity, taskEntity.getId(), username);
                }
            });

            String parentTaskId = taskEntity.getParentTaskId();
            if (StrUtil.isBlank(parentTaskId)) {
                parentTaskId = taskEntity.getId();
            }
            String finalParentTaskId = parentTaskId;
            //2.创建加签人的任务并执行完毕
            String taskId = taskEntity.getId();
            if (StrUtil.isBlank(taskEntity.getParentTaskId())) {
                Task task = this.createSubTask(taskEntity, finalParentTaskId, assignee);
                taskId = task.getId();
            }
            Task taskInfo = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (ObjectUtil.isNotNull(taskInfo)) {
                taskService.complete(taskId);
            }
            //如果是候选人，需要删除运行时候选不中的数据。
            long candidateCount = taskService.createTaskQuery().taskId(parentTaskId).taskCandidateUser(assignee).count();
            if (candidateCount > 0) {
                taskService.deleteCandidateUser(parentTaskId, assignee);
            }
        }
    }

	/**
     * 创建子任务
     *
     * @param ptask    创建子任务
     * @param assignee 子任务的执行人
     * @return
     */
     TaskEntity createSubTask(TaskEntity ptask, String ptaskId, String assignee) {
        TaskEntity task = null;
        if (ptask != null) {
            //1.生成子任务
            task = (TaskEntity) taskService.newTask(UUID.randomUUID()+"");
            task.setCategory(ptask.getCategory());
            task.setDescription(ptask.getDescription());
            task.setTenantId(ptask.getTenantId());
            task.setAssignee(assignee);
            task.setName(ptask.getName());
            task.setParentTaskId(ptaskId);
            task.setProcessDefinitionId(ptask.getProcessDefinitionId());
            task.setProcessInstanceId(ptask.getProcessInstanceId());
            task.setTaskDefinitionKey(ptask.getTaskDefinitionKey());
            task.setTaskDefinitionId(ptask.getTaskDefinitionId());
            task.setPriority(ptask.getPriority());
            task.setCreateTime(new Date());
            taskService.saveTask(task);
        }
        return task;
    }

	@Override
	@Transactional(rollbackFor = Exception.class)
	public R recallProcess(WfTaskBo bo) {
		// 当前任务 listtask
    	List<Task>  listtask = taskService.createTaskQuery().processInstanceId(bo.getProcInsId()).active().list();
        if (listtask == null || listtask.size()==0) {
            throw new FlowableException("流程未启动或已执行完成，无法收回");
        }

    	if (taskService.createTaskQuery().taskId(listtask.get(0).getId()).singleResult().isSuspended()) {
            throw new FlowableException("任务处于挂起状态");
        }

    	List<Task> procInsId = taskService.createNativeTaskQuery().sql("select * from ACT_HI_TASKINST where PROC_INST_ID_ = #{procInsId} ORDER BY START_TIME_ desc").parameter("procInsId", bo.getProcInsId()).list();

        String processInstanceId = listtask.get(0).getProcessInstanceId();

        //  获取所有历史任务（按创建时间升序）
        List<HistoricTaskInstance> hisTaskList = historyService.createHistoricTaskInstanceQuery()
        .processInstanceId(processInstanceId).orderByTaskCreateTime()
        .asc()
        .list();
        if (CollectionUtil.isEmpty(hisTaskList) || hisTaskList.size() < 2) {
            log.error("当前流程 【{}】 审批节点 【{}】正在初始节点无法收回", processInstanceId, listtask.get(0).getName());
            throw new FlowableException(String.format("当前流程 【%s】 审批节点【%s】正在初始节点无法收回", processInstanceId, listtask.get(0).getName()));
        }

        //  第一个任务
        HistoricTaskInstance startTask = hisTaskList.get(0);
        //若操作用户不是发起人，不能收回
        if(!StringUtils.equalsAnyIgnoreCase(LoginHelper.getUsername(), startTask.getAssignee())) {
        	throw new FlowableException("操作用户不是发起人，不能收回");
        }
        //  当前任务
        HistoricTaskInstance currentTask = hisTaskList.get(hisTaskList.size() - 1);

        BpmnModel bpmnModel = repositoryService.getBpmnModel(listtask.get(0).getProcessDefinitionId());

        //  获取第一个活动节点
        FlowNode startFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(startTask.getTaskDefinitionKey());
        //  获取当前活动节点
        FlowNode currentFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(currentTask.getTaskDefinitionKey());

        //  临时保存当前活动的原始方向
        List<SequenceFlow> originalSequenceFlowList = new ArrayList<>(currentFlowNode.getOutgoingFlows());
        //  清理活动方向
        currentFlowNode.getOutgoingFlows().clear();

        //  建立新方向
        SequenceFlow newSequenceFlow = new SequenceFlow();
        newSequenceFlow.setId("newSequenceFlowId");
        newSequenceFlow.setSourceFlowElement(currentFlowNode);
        newSequenceFlow.setTargetFlowElement(startFlowNode);
        List<SequenceFlow> newSequenceFlowList = new ArrayList<>();
        newSequenceFlowList.add(newSequenceFlow);
        //  当前节点指向新的方向
        currentFlowNode.setOutgoingFlows(newSequenceFlowList);

        //  完成当前任务
        for(Task task : listtask) {
		    taskService.addComment(task.getId(), listtask.get(0).getProcessInstanceId(),FlowComment.RECALL.getType(), "发起人收回");
		    taskService.setAssignee(task.getId(), startTask.getAssignee());
		    taskService.complete(task.getId());
        }


        //  重新查询当前任务
        Task nextTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        if (ObjectUtil.isNotNull(nextTask)) {
            taskService.setAssignee(nextTask.getId(), startTask.getAssignee());
            //taskService.complete(nextTask.getId());;//跳过流程发起节点
        }
        //自定义业务处理id
        String dataId = bo.getDataId();
        if(StringUtils.isNotEmpty(dataId)) {
        	WfMyBusiness business = wfMyBusinessService.getByDataId(dataId);
        	//更新删除自定义业务任务关联表与流程历史表，以便可以重新发起流程。
        	if (business != null) {
        		business.setActStatus(ActStatus.recall);
        		business.setTodoUsers("");
        		business.setDoneUsers("");
        		business.setProposer("");
        		business.setTaskName("");
        		business.setTaskId("");
        		business.setTaskNameId("");
            	wfMyBusinessService.updateById(business);
            }
        }

        // 删除运行和历史的节点信息
        this.deleteActivity(procInsId.get(1).getTaskDefinitionKey(), bo.getProcInsId(), dataId);
        //  恢复原始方向
        currentFlowNode.setOutgoingFlows(originalSequenceFlowList);

		return R.ok("发起人收回成功");
	}
}
