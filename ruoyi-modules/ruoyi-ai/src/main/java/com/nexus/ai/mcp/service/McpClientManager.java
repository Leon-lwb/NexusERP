package com.nexus.ai.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.mcp.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 客户端管理器
 * <p>
 * 管理 MCP 客户端生命周期（创建/缓存/销毁），支持 REMOTE（HTTP/SSE）和 LOCAL（STDIO）两种传输方式。
 * 内置健康检查 + 熔断降级机制：连续 3 次失败后自动禁用 5 分钟。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private static final int MAX_FAILURE_COUNT = 3;
    private static final long DISABLE_DURATION_MS = 5 * 60 * 1000;

    private final AiToolMapper aiToolMapper;
    private final ObjectMapper objectMapper;

    /** 客户端缓存：toolId -> McpClient */
    private final Map<Long, McpClient> activeClients = new ConcurrentHashMap<>();
    /** 健康状态：toolId -> boolean */
    private final Map<Long, Boolean> toolHealthStatus = new ConcurrentHashMap<>();
    /** 失败计数：toolId -> count */
    private final Map<Long, AtomicInteger> toolFailureCount = new ConcurrentHashMap<>();
    /** 禁用截止时间：toolId -> timestamp */
    private final Map<Long, Long> toolDisabledUntil = new ConcurrentHashMap<>();

    /**
     * 获取所有已启用 MCP 工具的 ToolProvider
     * <p>当没有可用的 MCP 客户端时返回 null，调用方应跳过 toolProvider 注入</p>
     *
     * @return MCP ToolProvider，无可用客户端时返回 null
     */
    public ToolProvider getAllEnabledToolProviders() {
        List<AiTool> enabledTools = aiToolMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getStatus, "ENABLED")
                .ne(AiTool::getToolType, BuiltinToolRegistry.TYPE_BUILTIN)
        );

        if (enabledTools.isEmpty()) {
            return null;
        }

        List<McpClient> clients = new ArrayList<>();
        for (AiTool tool : enabledTools) {
            try {
                McpClient client = getOrCreateClient(tool.getId());
                if (client != null) {
                    clients.add(client);
                }
            } catch (Exception e) {
                log.error("[MCP] 创建客户端失败: toolId={}, error={}", tool.getId(), e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            return null;
        }

        return McpToolProvider.builder()
            .mcpClients(clients)
            .build();
    }

    /**
     * 按工具名称列表获取 MCP 工具 Provider
     * <p>当没有可用的 MCP 客户端时返回 null</p>
     *
     * @param toolNames 工具名称列表
     * @return MCP ToolProvider，无匹配时返回 null
     */
    public ToolProvider getToolProvidersByNames(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return null;
        }

        List<AiTool> tools = aiToolMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiTool>()
                .in(AiTool::getToolName, toolNames)
                .eq(AiTool::getStatus, "ENABLED")
                .ne(AiTool::getToolType, BuiltinToolRegistry.TYPE_BUILTIN)
        );

        return buildToolProvider(tools);
    }

    /**
     * 按工具 ID 列表获取 MCP 工具 Provider
     * <p>仅返回指定 ID 且状态为 ENABLED 的 MCP 外部工具</p>
     * <p>当没有可用的 MCP 客户端时返回 null</p>
     *
     * @param toolIds 工具 ID 列表
     * @return MCP ToolProvider，无匹配时返回 null
     */
    public ToolProvider getToolProvidersByIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return null;
        }

        List<AiTool> tools = aiToolMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiTool>()
                .in(AiTool::getId, toolIds)
                .eq(AiTool::getStatus, "ENABLED")
                .ne(AiTool::getToolType, BuiltinToolRegistry.TYPE_BUILTIN)
        );

        return buildToolProvider(tools);
    }

    /**
     * 根据工具列表构建 MCP ToolProvider
     * <p>遍历工具列表创建客户端，过滤掉创建失败的客户端</p>
     *
     * @param tools 工具实体列表
     * @return MCP ToolProvider，无可用客户端时返回 null
     */
    private ToolProvider buildToolProvider(List<AiTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        List<McpClient> clients = new ArrayList<>();
        for (AiTool tool : tools) {
            try {
                McpClient client = getOrCreateClient(tool.getId());
                if (client != null) {
                    clients.add(client);
                }
            } catch (Exception e) {
                log.error("[MCP] 创建客户端失败: toolId={}, error={}", tool.getId(), e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            return null;
        }

        return McpToolProvider.builder()
            .mcpClients(clients)
            .build();
    }

    /**
     * 获取或创建 MCP Client（含健康检查和重试逻辑）
     */
    public McpClient getOrCreateClient(Long toolId) {
        // 检查工具是否被禁用
        if (isToolDisabled(toolId)) {
            log.warn("[MCP] 工具 {} 因连续失败被暂时禁用", toolId);
            return null;
        }

        // 尝试从缓存获取
        McpClient cachedClient = activeClients.get(toolId);
        if (cachedClient != null && isToolHealthy(toolId)) {
            return cachedClient;
        }

        // 使用 per-toolId 锁替代 ConcurrentHashMap.compute() 的全局锁，
        // 避免数据库查询和进程启动在 compute 锁内执行时阻塞其他工具的操作
        synchronized (("mcp-tool-" + toolId).intern()) {
            // 双重检查
            cachedClient = activeClients.get(toolId);
            if (cachedClient != null && isToolHealthy(toolId)) {
                return cachedClient;
            }

            AiTool tool = aiToolMapper.selectById(toolId);
            if (tool == null || !"ENABLED".equals(tool.getStatus())) {
                closeQuietly(cachedClient);
                activeClients.remove(toolId);
                return null;
            }

            if (BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType())) {
                closeQuietly(cachedClient);
                activeClients.remove(toolId);
                return null;
            }

            try {
                McpClient client = createMcpClient(tool);
                markToolHealthy(toolId);
                closeQuietly(cachedClient);
                activeClients.put(toolId, client);
                log.info("[MCP] 成功创建客户端: toolName={}", tool.getToolName());
                return client;
            } catch (Exception e) {
                log.error("[MCP] 创建客户端失败: toolName={}, error={}", tool.getToolName(), e.getMessage());
                handleToolFailure(toolId);
                closeQuietly(cachedClient);
                activeClients.remove(toolId);
                return null;
            }
        }
    }

    /**
     * 健康检查
     */
    public boolean checkToolHealth(Long toolId) {
        AiTool tool = aiToolMapper.selectById(toolId);
        if (tool == null || !"ENABLED".equals(tool.getStatus())) {
            return false;
        }

        try {
            McpClient client = createMcpClient(tool);
            if (client != null) {
                markToolHealthy(toolId);
                // 先关闭旧客户端再替换
                McpClient oldClient = activeClients.put(toolId, client);
                if (oldClient != null) {
                    try { oldClient.close(); } catch (Exception e) { log.warn("[MCP] 关闭旧客户端异常: {}", e.getMessage()); }
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("[MCP] 健康检查失败: toolName={}, error={}", tool.getToolName(), e.getMessage());
            handleToolFailure(toolId);
            return false;
        }
    }

    /**
     * 刷新指定工具的客户端连接
     */
    public void refreshClient(Long toolId) {
        closeClient(toolId);
        log.info("[MCP] 刷新客户端: toolId={}", toolId);
    }

    /**
     * 获取当前活跃的客户端数量
     */
    public int getActiveClientCount() {
        return activeClients.size();
    }

    // ==================== 私有方法 ====================

    private McpClient createMcpClient(AiTool tool) throws Exception {
        if ("LOCAL".equals(tool.getToolType())) {
            return createStdioClient(tool);
        } else if ("REMOTE".equals(tool.getToolType())) {
            return createRemoteClient(tool);
        }
        throw new IllegalArgumentException("不支持的工具类型: " + tool.getToolType());
    }

    /**
     * 创建 STDIO Client（本地命令行工具）
     */
    private McpClient createStdioClient(AiTool tool) throws Exception {
        String configJson = tool.getConfigJson();
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("LOCAL 类型工具需要配置 configJson");
        }

        JsonNode configNode = objectMapper.readTree(configJson);

        String command = null;
        List<String> args = new ArrayList<>();

        if (configNode.has("command")) {
            command = configNode.get("command").asText();
        }
        if (configNode.has("args") && configNode.get("args").isArray()) {
            for (JsonNode arg : configNode.get("args")) {
                args.add(arg.asText());
            }
        }

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("configJson 中 command 不能为空");
        }

        // Windows 命令兼容性处理
        command = resolveCommand(command);

        // 检查命令是否可用
        if (!isCommandAvailable(command)) {
            throw new IllegalArgumentException("命令 '" + command + "' 在当前系统不可用，请安装所需依赖");
        }

        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(command);
        fullCommand.addAll(args);

        log.info("[MCP] 创建 STDIO 客户端: toolName={}, command={}", tool.getToolName(), fullCommand);

        McpTransport transport = StdioMcpTransport.builder()
            .command(fullCommand)
            .logEvents(true)
            .build();

        return new DefaultMcpClient.Builder()
            .transport(transport)
            .build();
    }

    /**
     * 创建远程 HTTP/SSE Client
     */
    private McpClient createRemoteClient(AiTool tool) throws Exception {
        String configJson = tool.getConfigJson();
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("REMOTE 类型工具需要配置 configJson");
        }

        JsonNode configNode = objectMapper.readTree(configJson);

        if (!configNode.has("baseUrl")) {
            throw new IllegalArgumentException("configJson 中 baseUrl 不能为空");
        }

        String baseUrl = configNode.get("baseUrl").asText();
        log.info("[MCP] 创建 HTTP/SSE 客户端: toolName={}, baseUrl={}", tool.getToolName(), baseUrl);

        McpTransport transport = StreamableHttpMcpTransport.builder()
            .url(baseUrl)
            .logRequests(true)
            .build();

        return new DefaultMcpClient.Builder()
            .transport(transport)
            .build();
    }

    /**
     * 解析命令，处理 Windows 系统兼容性
     */
    private String resolveCommand(String command) {
        if (command == null || command.isBlank()) {
            return command;
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            String lowerCommand = command.toLowerCase();
            if (lowerCommand.equals("npx") || lowerCommand.equals("npm") ||
                lowerCommand.equals("node") || lowerCommand.equals("pnpm") ||
                lowerCommand.equals("yarn") || lowerCommand.equals("uvx") ||
                lowerCommand.equals("uv")) {
                String resolvedCommand = command + ".cmd";
                log.debug("[MCP] Windows 环境，命令解析: {} -> {}", command, resolvedCommand);
                return resolvedCommand;
            }
        }
        return command;
    }

    /**
     * 检查命令是否在系统上可用
     */
    private boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("[MCP] 命令 '{}' 不可用: {}", command, e.getMessage());
            return false;
        }
    }

    private boolean isToolDisabled(Long toolId) {
        Long disabledUntil = toolDisabledUntil.get(toolId);
        if (disabledUntil == null) {
            return false;
        }
        if (System.currentTimeMillis() > disabledUntil) {
            toolDisabledUntil.remove(toolId);
            toolFailureCount.remove(toolId);
            log.info("[MCP] 工具 {} 禁用时间已过，重新启用", toolId);
            return false;
        }
        return true;
    }

    private boolean isToolHealthy(Long toolId) {
        return toolHealthStatus.getOrDefault(toolId, true);
    }

    private void markToolHealthy(Long toolId) {
        toolHealthStatus.put(toolId, true);
        toolFailureCount.remove(toolId);
    }

    private void handleToolFailure(Long toolId) {
        AtomicInteger counter = toolFailureCount.computeIfAbsent(toolId, k -> new AtomicInteger(0));
        int failures = counter.incrementAndGet();
        toolHealthStatus.put(toolId, false);

        if (failures >= MAX_FAILURE_COUNT) {
            long disableUntil = System.currentTimeMillis() + DISABLE_DURATION_MS;
            toolDisabledUntil.put(toolId, disableUntil);
            log.warn("[MCP] 工具 {} 连续失败 {} 次，禁用至 {}", toolId, failures, new java.util.Date(disableUntil));
        } else {
            log.warn("[MCP] 工具 {} 失败 {} 次（上限 {}）", toolId, failures, MAX_FAILURE_COUNT);
        }
    }

    private void closeClient(Long toolId) {
        McpClient client = activeClients.remove(toolId);
        closeQuietly(client);
        if (client != null) {
            log.info("[MCP] 关闭客户端: toolId={}", toolId);
        }
    }

    private void closeQuietly(McpClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[MCP] 关闭客户端异常: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("[MCP] 清理 {} 个 MCP 客户端...", activeClients.size());
        activeClients.keySet().forEach(this::closeClient);
    }
}
