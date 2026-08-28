package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.enums.AiModelProtocolEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调用协议策略工厂
 * <p>启动时自动发现所有 {@link ChatServiceProvider} Bean，按 {@link ChatServiceProvider#getProtocol()}
 * 注册到 Map；运行时按<b>协议</b>（而非厂商类型）路由，协议解析规则：</p>
 * <ol>
 *   <li>ai_model.api_protocol 为合法协议码（{@link AiModelProtocolEnum#isKnown}）→ 直接使用</li>
 *   <li>否则（NULL/空白/未知串的存量数据）按厂商类型（provider_id → ai_provider.provider_type，
 *       由 AiModelServiceImpl.fillProviderInfo 填充到瞬态字段）取
 *       {@link AiProviderPolicyRegistry#defaultProtocol} 默认协议</li>
 *   <li>仍无法解析 → 抛出异常，提示管理员到模型管理配置协议（不再静默回退 OpenAI）</li>
 * </ol>
 * <p>替代原"按 providerType 路由 + 未知厂商兜底 OpenAI"的策略：厂商差异收敛到
 * {@link AiProviderPolicyRegistry} 的协议分组，本工厂只认协议。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatServiceProviderFactory implements ApplicationContextAware {

    /** 厂商协议策略注册表（协议分组可信事实源） */
    private final AiProviderPolicyRegistry providerPolicyRegistry;

    /** 协议码 → 协议策略 注册表（线程安全）。 */
    private final Map<String, ChatServiceProvider> providers = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        ctx.getBeansOfType(ChatServiceProvider.class).values().forEach(p -> {
            String key = p.getProtocol().trim().toUpperCase(Locale.ROOT);
            ChatServiceProvider old = providers.put(key, p);
            if (old != null) {
                log.warn("[ChatServiceProviderFactory] 协议策略重复注册: protocol={}, 覆盖 {} -> {}",
                    key, old.getClass().getSimpleName(), p.getClass().getSimpleName());
            }
            log.info("[ChatServiceProviderFactory] 注册协议策略: {} -> {}", key, p.getClass().getSimpleName());
        });
    }

    /**
     * 按模型配置解析协议并获取协议策略
     * <p>协议解析规则见类注释；未注册的协议直接抛异常，提示管理员配置。</p>
     *
     * @param config 模型配置（apiProtocol 持久化字段 + providerType/apiHost/apiKey 瞬态字段已填充）
     * @return 协议策略实例
     * @throws IllegalStateException 协议无法解析或未注册对应策略
     */
    public ChatServiceProvider get(AiModel config) {
        String protocol = resolveProtocol(config);
        ChatServiceProvider provider = providers.get(protocol);
        if (provider == null) {
            throw new IllegalStateException("未注册调用协议: " + protocol
                + "（模型 " + config.getModelName() + "），请检查模型管理中的 API 协议配置");
        }
        return provider;
    }

    /**
     * 解析模型配置的调用协议
     * <p>api_protocol 已知 → 直接用（并做厂商策略校验）；否则按厂商默认协议回填；
     * 均失败 → 明确报错。包级可见便于单元测试直接构造（无 Spring 上下文）。</p>
     */
    String resolveProtocol(AiModel config) {
        String apiProtocol = config.getApiProtocol();
        String resolved;
        if (AiModelProtocolEnum.isKnown(apiProtocol)) {
            resolved = apiProtocol.trim().toUpperCase(Locale.ROOT);
        } else {
            if (apiProtocol != null && !apiProtocol.isBlank()) {
                log.warn("[ChatServiceProviderFactory] 模型 {} 的 api_protocol 非法: {}，回退厂商默认协议",
                    config.getModelName(), apiProtocol);
            }
            String providerType = config.getProviderType();
            if (!providerPolicyRegistry.isRegistered(providerType)) {
                throw new IllegalStateException("模型 " + config.getModelName()
                    + "（id=" + config.getId() + "）未配置 API 协议且无法从厂商类型（"
                    + providerType + "）推断默认协议，请前往模型管理配置调用协议");
            }
            resolved = providerPolicyRegistry.defaultProtocol(providerType);
            log.debug("[ChatServiceProviderFactory] 模型 {} 未配置协议，按厂商 {} 默认协议 {} 路由",
                config.getModelName(), providerType, resolved);
        }
        // 运行时厂商策略校验（设计 6.3）：厂商类型已知时，协议必须在其允许集合内，
        // 防止如 DASHSCOPE 行手填 OPENAI_CHAT_COMPLETIONS 后拿 OpenAI 客户端硬打原生端点
        String providerType = config.getProviderType();
        if (providerPolicyRegistry.isRegistered(providerType)
            && !providerPolicyRegistry.allows(providerType, resolved)) {
            throw new IllegalStateException("模型 " + config.getModelName() + "（id=" + config.getId()
                + "）的 API 协议 " + resolved + " 不被厂商 " + providerType + " 允许（允许的协议: "
                + providerPolicyRegistry.forProvider(providerType).allowedProtocols()
                + "），请前往模型管理修正协议配置");
        }
        return resolved;
    }
}
