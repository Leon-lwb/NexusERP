package com.nexus.workflow.flowable.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.system.service.CommonService;


/**
 * 全局监听-工作流待办消息提醒
 * <p>在任务分配时向任务接收人发送待办系统通知，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreateListener implements FlowableEventListener {

    /** 任务服务，用于查询任务变量。 */
    private final TaskService taskService;
    /** 系统公共服务，用于查询登录用户与发送系统通知。 */
    private final CommonService commonService;
    /** 仓储服务，用于查询流程部署信息。 */
    private final RepositoryService repositoryService;
    /** 历史服务，用于查询流程历史实例。 */
    private final HistoryService historyService;


    /**
     * 监听任务分配事件，向任务接收人发送待办通知。
     * <p>仅处理任务分配（TASK_ASSIGNED）事件，并向非发起人的接收人发送系统站内信。</p>
     *
     * @param flowableEvent 流程引擎事件
     */
    @Override
    public void onEvent(FlowableEvent flowableEvent) {
        FlowableEventType type = flowableEvent.getType();
        if (type == FlowableEngineEventType.TASK_ASSIGNED) {
            if (flowableEvent instanceof org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl) {
                TaskEntity taskEntity = (TaskEntity) ((org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl) flowableEvent).getEntity();
                String taskId = taskEntity.getId();
                String procInsId = taskEntity.getProcessInstanceId();
                HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(procInsId)
                        .singleResult();
                String businessKey = historicProcessInstance.getBusinessKey();
                String startUserId = historicProcessInstance.getStartUserId();
                //获取任务接收人
                String receiver = taskEntity.getAssignee();
                if (StringUtils.isNotEmpty(receiver)) {
                    //发送提醒消息
                    String category = "";
                    if (taskService.getVariables(taskId).get("category") != null) {
                        category = taskService.getVariables(taskId).get("category").toString();
                    }

                    LoginUser loginUser = commonService.getLoginUser();
                    String bizKeyParam = StringUtils.isNotBlank(businessKey) ? "&businessKey=" + businessKey : "";
                    String contentUrl = commonService.getBaseUrl() + procInsId + "?taskId=" + taskId
                            + bizKeyParam + "&category=" + category + "&processed=true";
                    String msgContent = "流程待办通知，请处理";
                    if (!StringUtils.equals(loginUser.getUsername(), receiver)) {//发起人或登录人自己不发送
                        log.info("流程待办通知给:{}", receiver);
                        commonService.sendSysNotice(loginUser.getUserId().toString(), receiver, "流程待办通知", msgContent, Constants.MSG_CATEGORY_3, contentUrl);
                    }
                }
            }
        }
    }

    /**
     * 监听器在事件处理抛出异常时是否继续。
     *
     * @return 始终返回 false，即异常时不中断流程
     */
    @Override
    public boolean isFailOnException() {
        return false;
    }

    /**
     * 是否在事务生命周期事件中触发。
     *
     * @return 始终返回 false
     */
    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    /**
     * 获取触发的事务阶段。
     *
     * @return 始终返回 null
     */
    @Override
    public String getOnTransaction() {
        return null;
    }
}
