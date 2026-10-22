package com.nexus.ai.mcp.service;

import com.nexus.ai.context.AiToolInvocationGuard;
import com.nexus.ai.tool.BuiltinToolRegistry;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;

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

    /** 内置工具注册中心，提供已启用内置工具对象。 */
    private final BuiltinToolRegistry builtinToolRegistry;
    /** MCP 客户端管理器，提供外部工具 ToolProvider。 */
    private final McpClientManager mcpClientManager;

    /**
     * 获取所有已启用的内置工具对象
     * <p>这些对象包含 @Tool 注解的方法，可直接用于 AgenticServices.tools()</p>
     */
    public List<Object> getAllEnabledBuiltinToolObjects() {
        return guardObjects(builtinToolRegistry.getEnabledBuiltinToolObjects());
    }

    /**
     * 获取指定类别的已启用内置工具对象列表
     * <p>
     * 根据 categories 参数过滤工具，支持逗号分隔的多个类别。
     * 用于 Agent 构建时按需注入不同工具集。
     * </p>
     *
     * @param categories 类别标签（逗号分隔，如 "QUERY,ALL"）
     * @return 匹配类别的已启用工具对象列表
     */
    public List<Object> getEnabledBuiltinToolObjectsByCategories(String categories) {
        return guardObjects(
                builtinToolRegistry.getEnabledBuiltinToolObjectsByCategories(categories));
    }

    /**
     * 获取所有已启用的 MCP 外部工具 Provider
     * <p>用于 AgenticServices.toolProvider() 注入</p>
     */
    public ToolProvider getAllEnabledMcpToolProviders() {
        return guard(mcpClientManager.getAllEnabledToolProviders());
    }

    /**
     * 按工具名称列表获取 MCP 工具 Provider
     *
     * @param toolNames 工具名称列表
     * @return MCP ToolProvider，无匹配时返回 null
     */
    public ToolProvider getMcpToolProvidersByNames(List<String> toolNames) {
        return guard(mcpClientManager.getToolProvidersByNames(toolNames));
    }

    /**
     * 按工具 ID 列表获取 MCP 工具 Provider
     * <p>用于会话级工具选择，仅返回指定 ID 且状态为启用（0，字典 sys_normal_disable）的 MCP 外部工具</p>
     *
     * @param toolIds 工具 ID 列表
     * @return MCP ToolProvider，无匹配时返回 null
     */
    public ToolProvider getMcpToolProvidersByIds(List<Long> toolIds) {
        return guard(mcpClientManager.getToolProvidersByIds(toolIds));
    }

    /** 捕获构建Agent时的请求身份，SDK在工作线程执行工具时仍先核对取消状态。 */
    public List<Object> guardObjects(List<Object> objects) {
        return objects.stream().map(AiToolInvocationGuard::wrap).toList();
    }

    private ToolProvider guard(ToolProvider provider) {
        if (provider == null) return null;
        var captured = AiToolInvocationGuard.capture();
        return request -> {
            var snapshot = captured;
            var provided = snapshot.call(() -> provider.provideTools(request));
            if (provided == null) return null;
            var result = ToolProviderResult.builder();
            for (var tool : provided.aiServiceTools()) {
                var delegate = tool.toolExecutor();
                var guarded =
                        new ToolExecutor() {
                            public String execute(ToolExecutionRequest call, Object memory) {
                                return snapshot.call(() -> delegate.execute(call, memory));
                            }

                            public ToolExecutionResult executeWithContext(
                                    ToolExecutionRequest call, InvocationContext context) {
                                return snapshot.call(
                                        () -> delegate.executeWithContext(call, context));
                            }
                        };
                result.add(tool.toBuilder().toolExecutor(guarded).build());
            }
            return result.build();
        };
    }
}
