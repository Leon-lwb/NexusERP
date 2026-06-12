package com.nexus.ai.mcp;

/**
 * 内置工具提供者接口
 * <p>
 * 所有系统内置工具都应实现此接口，以便自动注册到 BuiltinToolRegistry。
 * 提供工具元数据（名称、显示名、描述）和工具 Bean 的 Class 引用，
 * 用于运行时动态获取工具实例。
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

    /**
     * 获取工具 Bean 的 Class
     * <p>用于从 Spring 容器中获取实际的工具实例，以支持动态过滤</p>
     */
    Class<?> getToolClass();
}
