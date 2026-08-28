package com.nexus.common.ai.service.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文生图厂商策略工厂
 * <p>启动时自动发现所有 {@link IImageGenerationService} Bean，按 {@link IImageGenerationService#getProviderName()}
 * 注册；运行时按键路由。键规范化规则：</p>
 * <ul>
 *   <li>大小写/首尾空白宽容（"Tongyiwanx"/"tongyiwanx" 等价）</li>
 *   <li>接受<b>厂商类型码</b>别名（ai_provider.provider_type，如 DASHSCOPE），
 *       便于运行时按所选 IMAGE 模型的 providerType 直查；别名映射统一收敛在本工厂
 *       （此前散落在模型连接测试服务的 DASHSCOPE→Tongyiwanx 硬编码）</li>
 * </ul>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
public class ImageServiceFactory implements ApplicationContextAware {

    /**
     * 厂商类型码 → 厂商实现名（规范化大写）。
     * <p>DASHSCOPE 厂商的文生图由通义万相实现承载（TongYiWanxImageService#getProviderName）。</p>
     */
    private static final Map<String, String> PROVIDER_TYPE_ALIASES = Map.of(
        "DASHSCOPE", "TONGYIWANX");

    private final Map<String, IImageGenerationService> providers = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        ctx.getBeansOfType(IImageGenerationService.class).values().forEach(p -> {
            String key = normalize(p.getProviderName());
            IImageGenerationService old = providers.put(key, p);
            if (old != null) {
                log.warn("[ImageServiceFactory] 文生图策略重复注册: key={}, 覆盖 {} -> {}",
                    key, old.getClass().getSimpleName(), p.getClass().getSimpleName());
            }
            log.info("[ImageServiceFactory] 注册文生图策略: {} -> {}", p.getProviderName(), p.getClass().getSimpleName());
        });
    }

    /**
     * 获取文生图策略
     * <p>键可为厂商实现名（如 Tongyiwanx，兼容 AiProperties.image.defaultProvider 等存量配置）
     * 或厂商类型码（如 DASHSCOPE，模型 providerType 直查）；未注册返回 null。</p>
     *
     * @param providerKey 厂商实现名或厂商类型码
     * @return 策略实例，未注册返回 null
     */
    public IImageGenerationService get(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            return null;
        }
        String key = normalize(providerKey);
        return providers.get(PROVIDER_TYPE_ALIASES.getOrDefault(key, key));
    }

    private static String normalize(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
