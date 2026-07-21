package com.nexus.workflow.flowable.listener;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 用户任务监听器
 * <p>作为流程设计中用户任务的任务监听器示例，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @since 2023/5/13
 */
@Component(value = "userTaskListener")
public class UserTaskListener implements TaskListener {

    /**
     * 任务创建/完成时触发，目前为示例实现。
     *
     * @param delegateTask 委托任务对象
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        //TODO 实现你的任务监听器逻辑
        System.out.println("执行任务监听器...");
    }

}
