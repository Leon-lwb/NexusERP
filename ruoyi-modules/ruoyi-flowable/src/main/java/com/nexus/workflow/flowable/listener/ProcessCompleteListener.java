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
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.system.service.CommonService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局监听-工作流完成消息提醒
 * <p>在流程/任务完成时向发起人发送系统通知，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 */

//必须要用 AbstractFlowableEngineEventListener 用FlowableEventListener这个会出现问题，应该是已经完成了
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCompleteListener extends AbstractFlowableEngineEventListener {

    /** 任务服务，用于查询任务信息。 */
    private final TaskService taskService;
    /** 系统公共服务，用于查询登录用户与发送系统通知。 */
    private final CommonService commonService;
    /** 历史服务，用于查询流程历史实例。 */
    private final HistoryService historyService;
    /** 仓储服务，用于查询流程定义/部署信息。 */
    private final RepositoryService repositoryService;

    /**
     * 流程完成时发送流程结束通知给发起人。
     * <p>通知属于旁路逻辑：任何失败都不应阻断或回滚流程完成命令本身。</p>
     *
     * @param event 流程完成事件
     */
    @Override
    protected void processCompleted(FlowableEngineEntityEvent event) {
        log.debug("进入流程结束监听器");
        try {
            String procInsId = event.getProcessInstanceId();
            if (StringUtils.isBlank(procInsId)) {
                return;
            }
            HistoricProcessInstance hi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(procInsId)
                    .singleResult();
            // 引擎命令内历史数据可能尚未可见，通知为尽力而为，查不到直接跳过
            if (hi == null || StringUtils.isBlank(hi.getStartUserId())) {
                return;
            }
            String startUserId = hi.getStartUserId();

            // 流程已结束，活动任务列表通常为空（list() 不会返回 null，仅判 null 是无效防护）
            List<Task> listtask = taskService.createTaskQuery().processInstanceId(procInsId).active().list();
            String taskId = "";
            if (listtask != null && !listtask.isEmpty()) {
                taskId = listtask.get(0).getId();
            }

            // 引擎线程（定时边界事件/异步/MQ 触发的完成）无登录态，getLoginUser() 为 null，跳过通知
            LoginUser loginUser = commonService.getLoginUser();
            if (loginUser == null) {
                log.debug("非登录线程触发的流程完成，跳过结束通知，procInsId={}", procInsId);
                return;
            }
            String contentUrl = commonService.getBaseUrl() + procInsId + "?taskId=" + taskId + "&processed=false";
            String msgContent = "流程任务结束通知，请点击查看";
            commonService.sendSysNotice(loginUser.getUserId().toString(), startUserId, "流程任务结束通知", msgContent, Constants.MSG_CATEGORY_1, contentUrl);
        } catch (Exception e) {
            // 通知失败不应阻断/回滚流程完成命令
            log.error("流程完成通知发送失败，procInsId={}", event.getProcessInstanceId(), e);
        } finally {
            super.processCompleted(event);
        }
    }

    @Override
    public boolean isFailOnException() {
        // 默认实现返回 true 会使监听器异常导致整个 complete/start 命令回滚（流程卡在最后一步）
        return false;
    }

    /**
     * 任务完成时记录日志。
     *
     * @param event 任务完成事件
     */
    @Override
    protected void taskCompleted(FlowableEngineEntityEvent event) {
        log.debug("进入taskCompleted监听器");
        super.taskCompleted(event);
    }

    /**
     * 事件分发入口，调用父类逻辑并记录日志。
     *
     * @param flowableEvent 流程引擎事件
     */
    @Override
    public void onEvent(FlowableEvent flowableEvent) {
        log.debug("进入流程结束监听器--onEvent");
        super.onEvent(flowableEvent);
    }
}
