package com.nexus.ai.listener;

import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.service.impl.AiModelServiceImpl.AiModelChangeEvent;
import com.nexus.common.ai.service.impl.AiProviderServiceImpl.AiProviderChangeEvent;
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

    /**
     * 处理厂商配置变更事件，清除全部模型缓存
     * <p>厂商 apiKey/apiHost 变更后，已缓存的 ChatModel 仍持旧凭证，需全量失效（S2-D B 修复）</p>
     *
     * @param event 厂商变更事件
     */
    @EventListener
    public void onProviderChange(AiProviderChangeEvent event) {
        log.info("AI厂商配置变更，清除全部模型缓存: providerId={}", event.getProviderId());
        chatModelFactory.evictAllCache();
    }
}
