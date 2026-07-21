package com.nexus.workflow.flowable.listener;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import com.nexus.common.core.utils.SpringContextHolder;
import com.nexus.system.service.CommonService;

import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;

/**
 * 部门经理处理类
 * <p>作为流程任务指派人解析处理器，按发起人所在部门的负责人解析处理人，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @date 2023-08-06
 */
@AllArgsConstructor
@Component("DepManagerHandler")
public class DepManagerHandler {

	/** 系统公共服务，用于查询部门负责人。 */
	private CommonService commonService = SpringContextHolder.getBean(CommonService.class);
	/** Flowable 运行时服务，用于查询流程实例与发起人。 */
	RuntimeService runtimeService = SpringContextHolder.getBean(RuntimeService.class);

	/**
	 * 解析部门经理处理人。
	 * <p>当当前用户任务指派人为 {@code DepManagerHandler} 时，根据流程发起人查询其部门负责人作为任务处理人。</p>
	 *
	 * @param execution 流程执行实例
	 * @return 部门经理用户名，未匹配时返回空字符串
	 */
	public String getUser(DelegateExecution execution) {
		String assignUserName = "";
		FlowElement flowElement = execution.getCurrentFlowElement();
        if (ObjectUtil.isNotEmpty(flowElement) && flowElement instanceof UserTask) {
            UserTask userTask = (UserTask) flowElement;
            if ( StringUtils.isNotEmpty(userTask.getAssignee())) {
           		if(StringUtils.contains(userTask.getAssignee(),"DepManagerHandler")) {
           		// 获取流程发起人
           		ProcessInstance processInstance = runtimeService
                            .createProcessInstanceQuery()
                            .processInstanceTenantId(execution.getTenantId())
                            .processInstanceId(execution.getProcessInstanceId())
                            .singleResult();
                    String startUserId = processInstance.getStartUserId();
                    // 获取部门负责人
                    assignUserName = commonService.getDepLeaderByUserName(startUserId);
           		}
            }
        }
        return assignUserName;
	}
}
