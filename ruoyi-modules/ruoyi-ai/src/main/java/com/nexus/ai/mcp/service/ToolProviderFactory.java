package com.nexus.ai.mcp.service;

import com.nexus.ai.mcp.BuiltinToolRegistry;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一工具提供工厂
 * <p>
 * 整合所有类型的 MCP 工具提供者，为 Agent 和 Chat 服务提供统一的工具获取入口。
 * 支持的工具类型：
 * <ul>
 *   <li>BUILTIN - 内置工具（含 @Tool 注解，通过 AgenticServices.tools() 注入）</li>
 *   <li>LOCAL/REMOTE - MCP 外部工具（通过 McpClientManager 创建客户端，以 ToolProvider 形式注入）</li>
 * </ul>
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolProviderFactory {

    private final BuiltinToolRegistry builtinToolRegistry;
    private final McpClientManager mcpClientManager;

    /**
     * 获取所有已启用的内置工具对象
     * <p>这些对象包含 @Tool 注解的方法，可直接用于 AgenticServices.tools()</p>
     */
    public List<Object> getAllEnabledBuiltinToolObjects() {
        return builtinToolRegistry.getEnabledBuiltinToolObjects();
    }

    /**
     * 获取所有已启用的 MCP 外部工具 Provider
     * <p>用于 AgenticServices.toolProvider() 注入</p>
     */
    public ToolProvider getAllEnabledMcpToolProviders() {
        return mcpClientManager.getAllEnabledToolProviders();
    }

    /**
     * 按工具名称列表获取 MCP 工具 Provider
     */
    public ToolProvider getMcpToolProvidersByNames(List<String> toolNames) {
        return mcpClientManager.getToolProvidersByNames(toolNames);
    }
}
