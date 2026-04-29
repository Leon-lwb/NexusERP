package org.dromara.workflow.service;

import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;

import org.dromara.common.core.domain.R;
import org.dromara.common.flowable.core.domain.ExtensionElementInfo;
import org.dromara.workflow.domain.bo.WfTaskBo;
import org.dromara.workflow.domain.dto.FlowNextDto;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author nbacheng
 * @createTime 2022/3/10 00:12
 */
public interface IWfTaskService {

    /**
     * 审批任务
     *
     * @param task 请求实体参数
     */
    void complete(WfTaskBo task);
    
    /**
     * 驳回任务
     *
     * @param taskBo
     */
    void taskReject(WfTaskBo taskBo);

    /**
     * 拒绝任务
     *
     * @param taskBo
     */
    void taskRefuse(WfTaskBo taskBo);


    /**
     * 退回任务
     *
     * @param bo 请求实体参数
     */
    void taskReturn(WfTaskBo bo);
    /**
     * 跳转任务
     *
     * @param bo 请求实体参数
     */
    void taskJump(WfTaskBo bo);
    
    /**
     * 获取所有可回退的节点
     *
     * @param bo
     * @return
     */
    List<UserTask> findReturnTaskList(WfTaskBo bo);

    /**
     * 删除任务
     *
     * @param bo 请求实体参数
     */
    void deleteTask(WfTaskBo bo);

    /**
     * 认领/签收任务
     *
     * @param bo 请求实体参数
     */
    void claim(WfTaskBo bo);

    /**
     * 取消认领/签收任务
     *
     * @param bo 请求实体参数
     */
    void unClaim(WfTaskBo bo);

    /**
     * 委派任务
     *
     * @param bo 请求实体参数
     */
    void delegateTask(WfTaskBo bo);


    /**
     * 转办任务
     *
     * @param bo 请求实体参数
     */
    void transferTask(WfTaskBo bo);

    /**
     * 取消申请
     * @param bo
     * @return
     */
    void stopProcess(WfTaskBo bo);

    /**
     * 撤回流程
     * @param bo
     * @return
     */
    void revokeProcess(WfTaskBo bo);

    /**
     * 获取流程过程图
     * @param processId
     * @return
     */
    InputStream diagram(String processId);

    /**
     * 获取流程变量
     * @param taskId 任务ID
     * @return 流程变量
     */
    Map<String, Object> getProcessVariables(String taskId);

    /**
     * 启动第一个任务
     * @param processInstance 流程实例
     * @param variables 流程参数
     */
    void startFirstTask(ProcessInstance processInstance, Map<String, Object> variables);
    
    /**
     * 获取下一节点
     * @param WfTaskBo 任务
     * @return
     */
    public R getNextFlowNode(WfTaskBo flowTaskVo);
    /**
     * 获取下一节点
     * @param String taskId, Map<String, Object> values
     * @return
     */
    public FlowNextDto getNextFlowNode(String taskId, Map<String, Object> values);

    /**
     * 自定义业务使用
     * 判断是否是第一个发起人节点，目前只针对退回，驳回情况进行处理
     * @param dataId 流程业务数据id, variables 变量集合,json对象
     * @return
     */
	boolean isFirstInitiator(String processInstanceId, String actStatusType);

	/**
     * 自定义业务使用
     *  删除自定义业务任务关联表与流程历史表，以便可以重新发起流程。
     * @param dataId 流程业务数据id, variables 变量集合,json对象
     * @return
     */
	boolean deleteActivityAndJoin(String dataId, String processInstanceId, String actStatusType);
	/**
     * 获取序列流扩展节点
     *
     * @param taskId 任务ID
     * @return 
     */
    Map<String, List<ExtensionElement>> getSequenceFlowExtensionElement(String taskId);
	/**
     * 获取扩展属性值
     *
     * @param taskId 任务ID
     * @return 
     */
    List<ExtensionElementInfo> getExtensionElement(String taskId);
    
    Map<String, Object> getFlowProperties(String procInsId);
    /**
     * 获取流程执行过程
     * @param procInsId
     * @return
     */
    R getFlowViewer(String procInsId);

    /**
     * 用户任务列表,作为跳转任务使用
     *
     * @param WfTaskBo
     * @return 
     */
	R userTaskList(WfTaskBo bo);

	 /**
     * 加签任务
     *
     * @param WfTaskBo
     * @return 
     */
	void addSignTask(WfTaskBo bo);
 
	/**
     * 多实例加签任务
     *
     * @param WfTaskBo
     * @return 
     */
	void multiInstanceAddSign(WfTaskBo bo);
	
	/**
     * 任务前加签 （如果多次加签只能显示第一次前加签的处理人来处理任务）
     * 多个加签人处理完毕任务之后又流到自己这里
     *
     * @param processInstanceId 流程实例id
     * @param assignee          受让人
     * @param description       描述
     * @param assignees 被加签人
     */
    void addTasksBefore(WfTaskBo bo, TaskEntityImpl taskEntity, String assignee, Set<String> assignees, String description);

    /**
     * 任务后加签（加签人自己自动审批完毕加签多个人处理任务）
     *
     * @param processInstanceId 流程实例id
     * @param assignee          受让人
     * @param description       描述
     * @param assignees 被加签人
     */
    void addTasksAfter(WfTaskBo bo, TaskEntityImpl taskEntity, String assignee, Set<String> assignees, String description);

    /**
     * 添加任务
     *
     * @param processInstanceId 流程实例id
     * @param assignee          受让人
     * @param description       描述
     * @param assignees 被加签人
     * @param flag  true向后加签  false向前加签
     */
    void addTask(WfTaskBo bo, TaskEntityImpl taskEntity, String assignee, Set<String> assignees, String description, Boolean flag);

    /**
     * 收回流程,收回后发起人可以重新编辑表单发起流程，对于自定义业务就是原有任务都删除，重新进行申请
     * @param bo
     * @return
     */
	R recallProcess(WfTaskBo bo);

}
