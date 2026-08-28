package com.nexus.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内置工具注册中心
 * <p>
 * 自动发现并注册所有实现 {@link BuiltinToolProvider} 接口的工具。
 * 存储工具元数据（名称、描述、Class），用于数据库同步、UI 展示和运行时动态获取工具实例。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinToolRegistry {

    /** 内置工具类型标识，用于 ai_tool 表 tool_type 字段过滤。 */
    public static final String TYPE_BUILTIN = "BUILTIN";

    /** Spring 收集的全部内置工具提供者实例（由 @RequiredArgsConstructor 注入）。 */
    private final List<BuiltinToolProvider> toolProviders;
    /** AI 工具表 Mapper，用于按状态查询已启用的工具。 */
    private final AiToolMapper aiToolMapper;
    /** Spring 应用上下文，用于按 Class 获取工具 Bean 实例。 */
    private final ApplicationContext applicationContext;

    /** 工具名称 → 提供者 的注册表（线程安全）。 */
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

    /**
     * 获取所有已启用的内置工具对象
     * <p>
     * 根据 ai_tool 表的 status 字段过滤，只返回启用状态（0，字典 sys_normal_disable）的工具 Bean 实例。
     * 这些对象包含 @Tool 注解的方法，可直接用于 AgenticServices.tools()。
     * </p>
     *
     * @return 已启用的内置工具对象列表
     */
    public List<Object> getEnabledBuiltinToolObjects() {
        // 查询所有已启用的 BUILTIN 类型工具
        List<AiTool> enabledTools = aiToolMapper.selectList(
            new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getToolType, TYPE_BUILTIN)
                .eq(AiTool::getStatus, AiToolStatusContract.ACTIVE)
        );

        List<Object> toolObjects = new ArrayList<>();
        for (AiTool tool : enabledTools) {
            BuiltinToolProvider provider = registeredProviders.get(tool.getToolName());
            if (provider != null) {
                try {
                    Object toolBean = applicationContext.getBean(provider.getToolClass());
                    toolObjects.add(toolBean);
                } catch (Exception e) {
                    log.warn("[MCP] 获取工具 Bean 失败: toolName={}, error={}", tool.getToolName(), e.getMessage());
                }
            }
        }
        return toolObjects;
    }

    /**
     * 获取指定类别的已启用工具对象列表
     * <p>
     * 根据 categories 参数过滤工具，支持逗号分隔的多个类别。
     * 例如 "QUERY,ALL" 会返回 QUERY 和 ALL 两个类别的所有已启用工具。
     * </p>
     *
     * @param categories 类别标签（逗号分隔，如 "QUERY,ALL"）
     * @return 匹配类别的已启用工具对象列表
     */
    public List<Object> getEnabledBuiltinToolObjectsByCategories(String categories) {
        Set<String> categorySet = Arrays.stream(categories.split(","))
            .map(String::trim)
            .collect(Collectors.toSet());

        // 查询所有已启用的 BUILTIN 类型工具
        List<AiTool> enabledTools = aiToolMapper.selectList(
            new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getToolType, TYPE_BUILTIN)
                .eq(AiTool::getStatus, AiToolStatusContract.ACTIVE)
        );

        List<Object> toolObjects = new ArrayList<>();
        for (AiTool tool : enabledTools) {
            BuiltinToolProvider provider = registeredProviders.get(tool.getToolName());
            if (provider != null && categorySet.contains(provider.getCategories())) {
                try {
                    Object toolBean = applicationContext.getBean(provider.getToolClass());
                    toolObjects.add(toolBean);
                } catch (Exception e) {
                    log.warn("[MCP] 获取工具 Bean 失败: toolName={}, error={}", tool.getToolName(), e.getMessage());
                }
            }
        }
        return toolObjects;
    }

    /**
     * 获取所有内置工具对象（不过滤状态）
     * <p>用于全量加载场景</p>
     */
    public List<Object> getAllBuiltinToolObjects() {
        List<Object> toolObjects = new ArrayList<>();
        for (BuiltinToolProvider provider : registeredProviders.values()) {
            try {
                Object toolBean = applicationContext.getBean(provider.getToolClass());
                toolObjects.add(toolBean);
            } catch (Exception e) {
                log.warn("[MCP] 获取工具 Bean 失败: toolName={}, error={}", provider.getToolName(), e.getMessage());
            }
        }
        return toolObjects;
    }
}
