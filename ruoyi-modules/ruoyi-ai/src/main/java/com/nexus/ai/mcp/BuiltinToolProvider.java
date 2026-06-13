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
     *
     * @return 工具名称
     */
    String getToolName();

    /**
     * 获取工具显示名称（用于 UI 展示）
     *
     * @return 显示名称
     */
    String getDisplayName();

    /**
     * 获取工具描述（用于 AI 理解工具用途）
     *
     * @return 工具描述
     */
    String getDescription();

    /**
     * 获取工具 Bean 的 Class
     * <p>用于从 Spring 容器中获取实际的工具实例，以支持动态过滤</p>
     *
     * @return 工具 Bean 的 Class 对象
     */
    Class<?> getToolClass();

    /**
     * 获取工具类别标签（用于 Agent 工具差异化注入）
     * <p>返回工具所属的类别，多个类别用逗号分隔。默认返回 "ALL" 表示适用于所有 Agent。</p>
     * <p>可选值：QUERY（数据查询类）、REPORT（报表可视化类）、ALL（通用）</p>
     *
     * @return 工具类别标签，默认 "ALL"
     */
    default String getCategories() {
        return "ALL";
    }
}
