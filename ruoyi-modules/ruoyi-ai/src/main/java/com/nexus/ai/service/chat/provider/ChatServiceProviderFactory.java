package com.nexus.ai.service.chat.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厂商策略工厂
 * <p>启动时自动发现所有 {@link ChatServiceProvider} Bean，按 {@link ChatServiceProvider#getProviderType()}
 * 注册到 Map；运行时按 providerType 路由，未知厂商兜底 OpenAI 兼容</p>
 * <p>替代原 {@code ChatModelFactory} 中的单 switch(providerType) 路由</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
public class ChatServiceProviderFactory implements ApplicationContextAware {

    /** 未知厂商时的兜底策略编码（OpenAI 兼容接口）。 */
    private static final String FALLBACK_PROVIDER = "OPENAI";

    /** 厂商编码 → 厂商策略 注册表（线程安全）。 */
    private final Map<String, ChatServiceProvider> providers = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        ctx.getBeansOfType(ChatServiceProvider.class).values().forEach(p -> {
            String key = p.getProviderType().toUpperCase();
            ChatServiceProvider old = providers.put(key, p);
            if (old != null) {
                log.warn("[ChatServiceProviderFactory] 厂商策略重复注册: providerType={}, 覆盖 {} -> {}",
                    key, old.getClass().getSimpleName(), p.getClass().getSimpleName());
            }
            log.info("[ChatServiceProviderFactory] 注册厂商策略: {} -> {}", key, p.getClass().getSimpleName());
        });
    }

    /**
     * 获取厂商策略（未知厂商兜底 OpenAI 兼容）
     *
     * @param providerType 厂商编码
     * @return 厂商策略实例
     */
    public ChatServiceProvider get(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            providerType = FALLBACK_PROVIDER;
        }
        ChatServiceProvider provider = providers.get(providerType.toUpperCase());
        if (provider == null) {
            log.warn("[ChatServiceProviderFactory] 未知的 providerType: {}，回退到 OpenAI 兼容接口", providerType);
            provider = providers.get(FALLBACK_PROVIDER);
        }
        if (provider == null) {
            throw new IllegalStateException("未找到任何 ChatServiceProvider，请检查 OpenAiServiceProvider 是否已注册");
        }
        return provider;
    }
}
