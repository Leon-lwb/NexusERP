package com.nexus.workflow.flowable.config;

import lombok.AllArgsConstructor;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventDispatcher;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import com.nexus.common.flowable.listener.GlobalEventListener;
import com.nexus.workflow.flowable.listener.ProcessCompleteListener;
import com.nexus.workflow.flowable.listener.TaskCreateListener;

/**
 * Flowable全局监听配置
 * <p>在容器刷新完成后向 Flowable 事件分发器注册全局事件监听器，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 */
@Configuration
@AllArgsConstructor
public class GlobalEventListenerConfig implements ApplicationListener<ContextRefreshedEvent> {

	/** 全局流程事件监听器，用于监听流程正常结束事件。 */
	private final GlobalEventListener globalEventListener;
	/** Flowable 运行时服务，用于注册全局事件监听器。 */
	private final RuntimeService runtimeService;
	/** Spring 流程引擎配置，提供事件分发器。 */
	private final SpringProcessEngineConfiguration configuration;

    /** 任务创建监听器，在任务创建/分配时发送待办消息。 */
    private final TaskCreateListener taskCreateListener;
    /** 流程完成监听器，在流程完成时发送结束消息。 */
    private final ProcessCompleteListener processCompleteListener;

	/**
	 * 容器刷新完成后注册 Flowable 全局事件监听器。
	 * <p>分别注册任务创建监听（待办消息）、流程完成监听（结束消息）及流程正常结束监听。</p>
	 *
	 * @param event Spring 容器刷新事件
	 */
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		FlowableEventDispatcher dispatcher = configuration.getEventDispatcher();
		// 任务创建全局监听-待办消息发送
        dispatcher.addEventListener(taskCreateListener, FlowableEngineEventType.TASK_CREATED, FlowableEngineEventType.TASK_ASSIGNED);
        //任务创建全局监听-完成消息发送
        dispatcher.addEventListener(processCompleteListener, FlowableEngineEventType.PROCESS_COMPLETED);
		// 流程正常结束
		runtimeService.addEventListener(globalEventListener, FlowableEngineEventType.PROCESS_COMPLETED);
	}
}
