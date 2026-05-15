package org.dromara.workflow.flowable.config;

import lombok.AllArgsConstructor;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventDispatcher;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import org.dromara.common.flowable.listener.GlobalEventListener;
import org.dromara.workflow.flowable.listener.ProcessCompleteListener;
import org.dromara.workflow.flowable.listener.TaskCreateListener;

/**
 * Flowable全局监听配置
 *
 * @author wanghuan
 */
@Configuration
@AllArgsConstructor
public class GlobalEventListenerConfig implements ApplicationListener<ContextRefreshedEvent> {

	private final GlobalEventListener globalEventListener;
	private final RuntimeService runtimeService;
	private final SpringProcessEngineConfiguration configuration;

    private final TaskCreateListener taskCreateListener;
    private final ProcessCompleteListener processCompleteListener;

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
