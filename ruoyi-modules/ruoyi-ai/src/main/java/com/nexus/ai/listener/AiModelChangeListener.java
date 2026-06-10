package com.nexus.ai.listener;

import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.service.impl.AiModelServiceImpl.AiModelChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置变更监听器
 * <p>监听模型变更事件，清除 ChatModelFactory 中对应的缓存</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelChangeListener {

    private final ChatModelFactory chatModelFactory;

    /**
     * 处理模型配置变更事件，清除对应模型的缓存
     *
     * @param event 模型变更事件
     */
    @EventListener
    public void onModelChange(AiModelChangeEvent event) {
        log.info("AI模型配置变更，清除模型缓存: modelId={}", event.getModelId());
        chatModelFactory.evictCache(event.getModelId());
    }
}
