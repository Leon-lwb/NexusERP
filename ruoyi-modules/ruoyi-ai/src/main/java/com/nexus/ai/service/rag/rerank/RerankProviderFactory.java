package com.nexus.ai.service.rag.rerank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rerank 厂商策略工厂
 * <p>启动时自动发现所有 {@link RerankProvider} Bean，按 {@link RerankProvider#getProviderType()}
 * 注册；运行时按 providerType 路由，未注册返回 null（由 RerankService 回退 LLM 兜底）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
public class RerankProviderFactory implements ApplicationContextAware {

    private final Map<String, RerankProvider> providers = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        ctx.getBeansOfType(RerankProvider.class).values().forEach(p -> {
            providers.put(p.getProviderType().toUpperCase(), p);
            log.info("[RerankProviderFactory] 注册 Rerank 策略: {} -> {}", p.getProviderType(), p.getClass().getSimpleName());
        });
    }

    /**
     * 获取 Rerank 策略
     *
     * @param providerType 厂商编码
     * @return 策略实例，未注册返回 null
     */
    public RerankProvider get(String providerType) {
        if (providerType == null) {
            return null;
        }
        return providers.get(providerType.toUpperCase());
    }
}
