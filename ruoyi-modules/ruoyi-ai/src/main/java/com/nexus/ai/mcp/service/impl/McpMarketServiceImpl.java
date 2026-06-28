package com.nexus.ai.mcp.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final AiMcpMarketMapper marketMapper;
    private final AiMcpMarketToolMapper marketToolMapper;
    private final AiToolMapper toolMapper;
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
            // 兼容 {data:[...]} 或 [...]
            JsonNode tools = root.isArray() ? root : root.path("data");
            if (!tools.isArray()) {
                tools = root.path("tools");
            }
            if (!tools.isArray()) {
                log.warn("[MCP市场] 响应格式异常，无 data 或 tools 数组: {}", market.getUrl());
                return new McpMarketRefreshResult(0, 0);
            }

            int added = 0, updated = 0;
            for (JsonNode item : tools) {
                String toolName = item.path("name").asText(null);
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
                tool.setConfigJson(mt.getToolMetadata());
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
