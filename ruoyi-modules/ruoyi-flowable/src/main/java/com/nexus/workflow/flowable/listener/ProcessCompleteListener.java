package com.nexus.workflow.flowable.listener;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl;
import org.flowable.task.api.Task;
import org.flowable.variable.api.persistence.entity.VariableInstance;
import org.springframework.stereotype.Component;

import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.system.service.CommonService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局监听-工作流完成消息提醒
 *
 * @author wanghuan
 */

//必须要用 AbstractFlowableEngineEventListener 用FlowableEventListener这个会出现问题，应该是已经完成了
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCompleteListener extends AbstractFlowableEngineEventListener {

    private final TaskService taskService;
    private final CommonService commonService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;

    @Override
    protected void processCompleted(FlowableEngineEntityEvent event) {
        log.debug("进入流程结束监听器");

        String procInsId = event.getProcessInstanceId();
        HistoricProcessInstance hi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(procInsId)
                .singleResult();

        List<Task> listtask = taskService.createTaskQuery().processInstanceId(procInsId).active().list();
        String taskId = "";
        if (listtask != null) {
            taskId = listtask.get(0).getId();
        }
        String startUserId = hi.getStartUserId();
        String category = "";

        if (StringUtils.isNotEmpty(startUserId)) {
            if (((ExecutionEntityImpl) event.getEntity()).getVariableInstances().get("category") != null) {
                category = ((VariableInstance) ((ExecutionEntityImpl) event.getEntity()).getVariableInstances().get("category")).getTextValue();
            }

            LoginUser loginUser = commonService.getLoginUser();
            String contentUrl = commonService.getBaseUrl() + procInsId + "?taskId=" + taskId + "&processed=false";
            String msgContent = "流程任务结束通知，请点击查看";
            commonService.sendSysNotice(loginUser.getUserId().toString(), startUserId, "流程任务结束通知", msgContent, Constants.MSG_CATEGORY_1, contentUrl);
        }

        super.processCompleted(event);
    }

    @Override
    protected void taskCompleted(FlowableEngineEntityEvent event) {
        log.debug("进入taskCompleted监听器");
        super.taskCompleted(event);
    }

    @Override
    public void onEvent(FlowableEvent flowableEvent) {
        log.debug("进入流程结束监听器--onEvent");
        super.onEvent(flowableEvent);
    }
}
