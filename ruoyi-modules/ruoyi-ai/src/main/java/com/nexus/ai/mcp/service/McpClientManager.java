package com.nexus.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.ai.mcp.McpConfigSanitizer;
import com.nexus.ai.mcp.McpEndpointPolicy;
import com.nexus.ai.tool.BuiltinToolRegistry;
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    /** 仅来自运维/启动参数的精确本地 URI 白名单，默认不授权任何本地地址。 */
    @Value("${nexus.ai.mcp.allowed-local-endpoints:}")
    private String allowedLocalEndpoints = "";

    /** 连续失败达到该次数后触发熔断禁用。 */
    private static final int MAX_FAILURE_COUNT = 3;
    /** 熔断禁用持续时间（毫秒），到期自动重新启用。 */
    private static final long DISABLE_DURATION_MS = 5 * 60 * 1000;

    /** AI 工具表 Mapper，负责工具查询与状态校验。 */
    private final AiToolMapper aiToolMapper;
    /** Jackson JSON 处理器，用于解析工具 configJson。 */
    private final ObjectMapper objectMapper;

    /** 客户端缓存：toolId -> McpClient */
    private final Map<Long, McpClient> activeClients = new ConcurrentHashMap<>();
    /** 只保存配置摘要，不保存或打印认证原文；租户、类型、状态及完整配置均参与版本判断。 */
    private final Map<Long, String> clientFingerprints = new ConcurrentHashMap<>();
    /** 失效代数先递增再取锁，正在创建/发现的旧候选也不能重新发布。 */
    private final Map<Long, AtomicLong> clientGenerations = new ConcurrentHashMap<>();
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
        List<AiTool> enabledTools =
                aiToolMapper.selectList(
                        new LambdaQueryWrapper<AiTool>()
                                .eq(AiTool::getStatus, AiToolStatusContract.ACTIVE)
                                .ne(AiTool::getToolType, BuiltinToolRegistry.TYPE_BUILTIN));

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
                log.error("[MCP] 创建客户端失败: toolId={}, error={}", tool.getId(), e.getClass().getSimpleName());
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

        List<AiTool> tools =
                aiToolMapper.selectList(
                        new LambdaQueryWrapper<AiTool>()
                                .in(AiTool::getToolName, toolNames)
                                .eq(AiTool::getStatus, AiToolStatusContract.ACTIVE)
                                .ne(AiTool::getToolType, BuiltinToolRegistry.TYPE_BUILTIN));

        return buildToolProvider(tools);
    }

    /**
     * 按工具 ID 列表获取 MCP 工具 Provider
     * <p>仅返回指定 ID 且状态为启用（0，字典 sys_normal_disable）的 MCP 外部工具</p>
     * <p>当没有可用的 MCP 客户端时返回 null</p>
     *
     * @param toolIds 工具 ID 列表
     * @return MCP ToolProvider，无匹配时返回 null
     */
    public ToolProvider getToolProvidersByIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return null;
        }

        List<AiTool> tools =
                aiToolMapper.selectList(
                        new LambdaQueryWrapper<AiTool>()
                                .in(AiTool::getId, toolIds)
                                .eq(AiTool::getStatus, AiToolStatusContract.ACTIVE)
                                .ne(AiTool::getToolType, BuiltinToolRegistry.TYPE_BUILTIN));

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
                log.error("[MCP] 创建客户端失败: toolId={}, error={}", tool.getId(), e.getClass().getSimpleName());
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
        // 缓存命中也必须经过当前租户查询；锁内再核对权限、状态及配置版本。
        AiTool tool = aiToolMapper.selectById(toolId);
        if (!eligible(tool) || isToolDisabled(toolId)) {
            return null;
        }
        synchronized (("mcp-tool-" + toolId).intern()) {
            tool = aiToolMapper.selectById(toolId);
            if (!eligible(tool) || isToolDisabled(toolId)) {
                return null;
            }
            String fingerprint = configurationFingerprint(tool);
            McpClient cached = activeClients.get(toolId);
            if (cached != null && isToolHealthy(toolId)
                && fingerprint.equals(clientFingerprints.get(toolId))) {
                return cached;
            }
            closeClient(toolId);
            long generation = generation(toolId);
            McpClient candidate = null;
            try {
                candidate = createMcpClient(tool);
                if (!publishCurrent(toolId, candidate, fingerprint, generation)) {
                    closeQuietly(candidate);
                    return null;
                }
                return candidate;
            } catch (Exception e) {
                closeQuietly(candidate);
                log.warn("[MCP] 创建客户端失败: toolId={}, errorType={}", toolId, e.getClass().getSimpleName());
                handleToolFailure(toolId);
                return null;
            }
        }
    }

    /**
     * 健康检查
     */
    public boolean checkToolHealth(Long toolId) {
        return discoverToolNames(toolId) != null;
    }

    /**
     * 对当前租户可见且启用的 MCP 服务实际执行 tools/list。
     * 返回 null 表示失败；空列表表示连接成功但服务器没有可用工具，绝不伪造配置名。
     */
    public List<String> discoverToolNames(Long toolId) {
        AiTool tool = aiToolMapper.selectById(toolId);
        if (tool == null || !AiToolStatusContract.ACTIVE.equals(tool.getStatus())
            || BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType())) {
            return null;
        }
        synchronized (("mcp-tool-" + toolId).intern()) {
            // discovery 与正常客户端创建使用同一锁内复核，防止停用/删除/熔断竞态下
            // 仍使用锁外旧实体建立连接并执行 tools/list。
            tool = aiToolMapper.selectById(toolId);
            if (tool == null || !AiToolStatusContract.ACTIVE.equals(tool.getStatus())
                || BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType())
                || isToolDisabled(toolId)) {
                return null;
            }
            String fingerprint = configurationFingerprint(tool);
            long generation = generation(toolId);
            McpClient candidate = null;
            try {
                candidate = createMcpClient(tool);
                List<String> names = candidate.listTools().stream().map(spec -> spec.name()).toList();
                if (!publishCurrent(toolId, candidate, fingerprint, generation)) {
                    closeQuietly(candidate);
                    return null;
                }
                return names;
            } catch (Exception e) {
                closeQuietly(candidate);
                log.warn("[MCP] 工具发现失败: toolId={}, errorType={}", toolId, e.getClass().getSimpleName());
                handleToolFailure(toolId);
                return null;
            }
        }
    }

    /**
     * 刷新指定工具的客户端连接
     */
    public void refreshClient(Long toolId) {
        clientGenerations.computeIfAbsent(toolId, ignored -> new AtomicLong()).incrementAndGet();
        synchronized (("mcp-tool-" + toolId).intern()) {
            closeClient(toolId);
            toolHealthStatus.remove(toolId);
            toolFailureCount.remove(toolId);
            toolDisabledUntil.remove(toolId);
        }
        log.info("[MCP] 刷新客户端: toolId={}", toolId);
    }

    /**
     * 获取当前活跃的客户端数量
     */
    public int getActiveClientCount() {
        return activeClients.size();
    }

    // ==================== 私有方法 ====================

    private boolean eligible(AiTool tool) {
        return tool != null && AiToolStatusContract.ACTIVE.equals(tool.getStatus())
            && !BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType());
    }

    private long generation(Long toolId) {
        return clientGenerations.computeIfAbsent(toolId, ignored -> new AtomicLong()).get();
    }

    /** 权限/指纹/失效代数在发布前复核；发布与失效共享锁，代数复核封住等待锁的提交回调。 */
    private boolean publishCurrent(Long toolId, McpClient candidate, String fingerprint, long expectedGeneration) {
        AiTool latest = aiToolMapper.selectById(toolId);
        if (!eligible(latest) || isToolDisabled(toolId) || generation(toolId) != expectedGeneration
            || !fingerprint.equals(configurationFingerprint(latest))) {
            return false;
        }
        McpClient old = activeClients.put(toolId, candidate);
        clientFingerprints.put(toolId, fingerprint);
        if (generation(toolId) != expectedGeneration) {
            activeClients.remove(toolId, candidate);
            clientFingerprints.remove(toolId);
            closeQuietly(old);
            return false;
        }
        closeQuietly(old);
        markToolHealthy(toolId);
        return true;
    }

    private String configurationFingerprint(AiTool tool) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 长度前缀避免字段连接歧义；不数字化租户，不输出摘要或认证内容。
            for (Object field : new Object[]{tool.getId(), tool.getTenantId(), tool.getToolType(), tool.getStatus(), tool.getConfigJson()}) {
                // 空值用独立标记，不能与长度为0的已配置文本共享同一个版本。
                if (field == null) {
                    digest.update(ByteBuffer.allocate(4).putInt(-1).array());
                } else {
                    byte[] bytes = field.toString().getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("MCP配置版本无法校验");
        }
    }

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

        log.info("[MCP] 创建 STDIO 客户端: toolId={}", tool.getId());

        McpTransport transport = StdioMcpTransport.builder()
            .command(fullCommand)
            .environment(McpConfigSanitizer.stringMap(configNode.path("env"), "env"))
            .logEvents(false)
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

        String baseUrl = McpEndpointPolicy.validateRemoteTransport(configNode.get("baseUrl").asText(), allowedLocalEndpoints);
        log.info("[MCP] 创建 HTTP/SSE 客户端: toolId={}", tool.getId());

        McpTransport transport = StreamableHttpMcpTransport.builder()
            .url(baseUrl)
            .customHeaders(McpConfigSanitizer.stringMap(configNode.path("headers"), "headers"))
            .followRedirects(false)
            .logRequests(false)
            .logResponses(false)
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
                lowerCommand.equals("pnpm") || lowerCommand.equals("yarn")) {
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
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("[MCP] 命令 '{}' 不可用: {}", command, e.getClass().getSimpleName());
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
            log.warn("[MCP] 工具 {} 连续失败 {} 次，禁用至 {}", toolId, failures, new Date(disableUntil));
        } else {
            log.warn("[MCP] 工具 {} 失败 {} 次（上限 {}）", toolId, failures, MAX_FAILURE_COUNT);
        }
    }

    private void closeClient(Long toolId) {
        McpClient client = activeClients.remove(toolId);
        clientFingerprints.remove(toolId);
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
                log.warn("[MCP] 关闭客户端异常: {}", e.getClass().getSimpleName());
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("[MCP] 清理 {} 个 MCP 客户端...", activeClients.size());
        activeClients.keySet().forEach(this::closeClient);
    }
}
