package com.nexus.ai.mcp.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.mcp.service.IMcpMarketService;
import com.nexus.common.ai.domain.entity.AiMcpMarket;
import com.nexus.common.ai.domain.entity.AiMcpMarketTool;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiMcpMarketMapper;
import com.nexus.common.ai.mapper.AiMcpMarketToolMapper;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 市场服务实现
 * <p>管理市场源、刷新市场工具列表（HTTP GET）、一键加载工具到本地 ai_tool</p>
 * <p>加载目标：写入统一 ai_tool 表（而非参照的 mcp_tool_info），自动复用现有 McpClientManager</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpMarketServiceImpl implements IMcpMarketService {

    /** MCP 市场源表 Mapper，负责市场源读写。 */
    private final AiMcpMarketMapper marketMapper;
    /** MCP 市场工具表 Mapper，负责市场工具 upsert 与状态更新。 */
    private final AiMcpMarketToolMapper marketToolMapper;
    /** AI 工具表 Mapper，负责本地 ai_tool 记录写入。 */
    private final AiToolMapper toolMapper;
    /** Jackson JSON 处理器，用于解析市场响应与构建工具配置。 */
    private final ObjectMapper objectMapper;

    @Override
    public McpMarketRefreshResult refreshMarketTools(Long marketId) {
        AiMcpMarket market = marketMapper.selectById(marketId);
        if (market == null) {
            throw new IllegalArgumentException("市场不存在: " + marketId);
        }

        // HTTP GET 市场 URL，获取工具列表 JSON
        HttpResponse resp = HttpRequest.get(market.getUrl())
            .timeout(30000)
            .execute();
        if (!resp.isOk()) {
            throw new IllegalArgumentException("市场请求失败: HTTP " + resp.getStatus());
        }

        try {
            JsonNode root = objectMapper.readTree(resp.body());
            // 兼容 {data:[...]}、{tools:[...]}、{servers:[...]} 或裸数组
            JsonNode tools = root.isArray() ? root : root.path("data");
            if (!tools.isArray()) {
                tools = root.path("tools");
            }
            if (!tools.isArray()) {
                tools = root.path("servers");
            }
            if (!tools.isArray()) {
                log.warn("[MCP市场] 响应格式异常，无 data/tools/servers 数组: {}", market.getUrl());
                return new McpMarketRefreshResult(0, 0);
            }

            int added = 0, updated = 0;
            for (JsonNode item : tools) {
                // 工具名兼容多种市场格式：name（标准）→ qualifiedName（Smithery）→ displayName
                String toolName = item.path("name").asText(null);
                if (toolName == null) {
                    toolName = item.path("qualifiedName").asText(null);
                }
                if (toolName == null) {
                    toolName = item.path("displayName").asText(null);
                }
                if (toolName == null) continue;

                // 按 marketId + tool_name 查找已有记录
                LambdaQueryWrapper<AiMcpMarketTool> lqw = new LambdaQueryWrapper<>();
                lqw.eq(AiMcpMarketTool::getMarketId, marketId)
                    .eq(AiMcpMarketTool::getToolName, toolName);
                AiMcpMarketTool existing = marketToolMapper.selectOne(lqw);

                if (existing != null) {
                    existing.setToolDescription(item.path("description").asText(existing.getToolDescription()));
                    existing.setToolVersion(item.path("version").asText(existing.getToolVersion()));
                    existing.setToolMetadata(item.toString());
                    marketToolMapper.updateById(existing);
                    updated++;
                } else {
                    AiMcpMarketTool mt = new AiMcpMarketTool();
                    mt.setMarketId(marketId);
                    mt.setToolName(toolName);
                    mt.setToolDescription(item.path("description").asText(null));
                    mt.setToolVersion(item.path("version").asText(null));
                    mt.setToolMetadata(item.toString());
                    mt.setIsLoaded("0");
                    marketToolMapper.insert(mt);
                    added++;
                }
            }

            log.info("[MCP市场] 刷新完成: marketId={}, 新增={}, 更新={}", marketId, added, updated);
            return new McpMarketRefreshResult(added, updated);
        } catch (Exception e) {
            throw new RuntimeException("解析市场响应失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Long loadToolToLocal(Long marketToolId) {
        AiMcpMarketTool mt = marketToolMapper.selectById(marketToolId);
        if (mt == null) {
            throw new IllegalArgumentException("市场工具不存在: " + marketToolId);
        }
        if ("1".equals(mt.getIsLoaded())) {
            log.warn("[MCP市场] 工具已加载: toolName={}, localToolId={}", mt.getToolName(), mt.getLocalToolId());
            return mt.getLocalToolId();
        }

        try {
            JsonNode metadata = objectMapper.readTree(mt.getToolMetadata());

            AiTool tool = new AiTool();
            tool.setToolName(mt.getToolName());
            tool.setDisplayName(mt.getToolName());
            tool.setDescription(mt.getToolDescription());

            // 判断 REMOTE（有 baseUrl/url）或 LOCAL（有 command/args/package）
            String baseUrl = metadata.has("baseUrl") ? metadata.path("baseUrl").asText(null)
                : metadata.has("url") ? metadata.path("url").asText(null) : null;

            if (baseUrl != null && !baseUrl.isBlank()) {
                tool.setToolType("REMOTE");
                tool.setConfigJson(objectMapper.writeValueAsString(
                    java.util.Map.of("baseUrl", baseUrl)));
            } else {
                tool.setToolType("LOCAL");
                // 市场元数据通常只含 package（npm 包名），而 McpClientManager.createStdioClient 只认 command/args，
                // 需物化为 {command:"npx", args:["-y", package]}，否则启用即报错熔断（S2-C-2 修复）
                tool.setConfigJson(buildLocalConfig(metadata));
            }

            // 加载后默认 DISABLED（ai_tool.status 使用 ENABLED/DISABLED 串，管理员需手动启用+测试，安全要求）
            tool.setStatus("DISABLED");
            toolMapper.insert(tool);

            // 更新市场工具状态
            mt.setIsLoaded("1");
            mt.setLocalToolId(tool.getId());
            marketToolMapper.updateById(mt);

            log.info("[MCP市场] 工具已加载: toolName={}, toolId={}", mt.getToolName(), tool.getId());
            return tool.getId();
        } catch (Exception e) {
            throw new RuntimeException("加载市场工具失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 LOCAL 工具的 configJson
     * <p>McpClientManager.createStdioClient 只识别 command/args 字段；市场元数据通常只含 package（npm 包名），
     * 需物化为 {command:"npx", args:["-y", package]}，否则启用即报"command 不能为空"熔断（S2-C-2 修复）</p>
     *
     * @param metadata 市场工具元数据 JSON
     * @return LOCAL 工具 configJson
     */
    private String buildLocalConfig(JsonNode metadata) throws Exception {
        ObjectNode cfg = objectMapper.createObjectNode();
        if (metadata.has("command") && !metadata.get("command").asText("").isBlank()) {
            // 元数据已含 command，直接保留 command + args
            cfg.put("command", metadata.get("command").asText());
            JsonNode args = metadata.path("args");
            if (args.isArray()) {
                cfg.set("args", args);
            }
        } else if (metadata.has("package") && !metadata.get("package").asText("").isBlank()) {
            // 市场元数据只有 package，合成 npx -y <package>
            cfg.put("command", "npx");
            ArrayNode args = cfg.putArray("args");
            args.add("-y");
            args.add(metadata.get("package").asText());
            JsonNode extraArgs = metadata.path("args");
            if (extraArgs.isArray()) {
                for (JsonNode a : extraArgs) {
                    args.add(a.asText());
                }
            }
        } else {
            // 无 command 无 package，原样回退（createStdioClient 会抛出明确错误）
            return metadata.toString();
        }
        // 保留 env（如有）
        JsonNode env = metadata.path("env");
        if (env.isObject()) {
            cfg.set("env", env);
        }
        return objectMapper.writeValueAsString(cfg);
    }

    @Override
    public List<Long> batchLoadTools(List<Long> marketToolIds) {
        List<Long> loadedIds = new ArrayList<>();
        for (Long id : marketToolIds) {
            try {
                loadedIds.add(loadToolToLocal(id));
            } catch (Exception e) {
                log.warn("[MCP市场] 批量加载跳过 {}: {}", id, e.getMessage());
            }
        }
        return loadedIds;
    }
}
