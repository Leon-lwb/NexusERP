package com.nexus.ai.mcp;

/**
 * 内置工具提供者接口
 * <p>
 * 所有系统内置工具都应实现此接口，以便自动注册到 BuiltinToolRegistry。
 * 仅提供工具元数据（名称、显示名、描述），不涉及工具实例化。
 * </p>
 *
 * @author NexusERP
 */
public interface BuiltinToolProvider {

    /**
     * 获取工具名称（唯一标识，snake_case 格式）
     */
    String getToolName();

    /**
     * 获取工具显示名称（用于 UI 展示）
     */
    String getDisplayName();

    /**
     * 获取工具描述（用于 AI 理解工具用途）
     */
    String getDescription();
}
