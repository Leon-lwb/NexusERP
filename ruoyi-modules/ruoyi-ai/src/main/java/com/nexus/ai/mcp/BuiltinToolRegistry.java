package com.nexus.ai.mcp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置工具注册中心
 * <p>
 * 自动发现并注册所有实现 {@link BuiltinToolProvider} 接口的工具。
 * 仅存储工具元数据（名称、描述），用于数据库同步和 UI 展示。
 * 不通过 newInstance() 创建工具实例，避免依赖注入 NPE 问题。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinToolRegistry {

    public static final String TYPE_BUILTIN = "BUILTIN";

    private final List<BuiltinToolProvider> toolProviders;

    private final Map<String, BuiltinToolProvider> registeredProviders = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[MCP] 开始注册内置工具，发现 {} 个工具提供者", toolProviders.size());
        for (BuiltinToolProvider provider : toolProviders) {
            String toolName = provider.getToolName();
            if (registeredProviders.containsKey(toolName)) {
                log.warn("[MCP] 工具名称重复: {}，将覆盖原有注册", toolName);
            }
            registeredProviders.put(toolName, provider);
            log.info("[MCP] 注册内置工具: {} ({})", toolName, provider.getDisplayName());
        }
        log.info("[MCP] 内置工具注册完成，共 {} 个工具", registeredProviders.size());
    }

    /**
     * 获取所有已注册的内置工具提供者
     */
    public Collection<BuiltinToolProvider> getAllProviders() {
        return registeredProviders.values();
    }

    /**
     * 按名称获取工具提供者
     */
    public BuiltinToolProvider getProvider(String toolName) {
        return registeredProviders.get(toolName);
    }

    /**
     * 检查工具是否已注册
     */
    public boolean hasTool(String toolName) {
        return registeredProviders.containsKey(toolName);
    }

    /**
     * 获取已注册工具数量
     */
    public int getToolCount() {
        return registeredProviders.size();
    }
}
