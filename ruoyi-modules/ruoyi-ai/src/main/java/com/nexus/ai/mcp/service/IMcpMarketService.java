package com.nexus.ai.mcp.service;

import com.nexus.common.ai.domain.entity.AiMcpMarket;

import java.util.List;

/**
 * MCP 市场服务接口
 * <p>管理市场源（CRUD）、刷新市场工具列表、一键加载工具到本地 ai_tool</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface IMcpMarketService {

    /**
     * 刷新市场工具列表
     * <p>HTTP GET market.url，解析返回的 JSON 工具列表，按 tool_name upsert ai_mcp_market_tool</p>
     *
     * @param marketId 市场ID
     * @return 刷新结果（新增数、更新数）
     */
    McpMarketRefreshResult refreshMarketTools(Long marketId);

    /**
     * 一键加载市场工具到本地 ai_tool
     * <p>解析 tool_metadata，判断 REMOTE（有 baseUrl）或 LOCAL（command/args），
     * 创建一条 ai_tool 记录（默认停用 status=1，字典 sys_normal_disable，需手动启用），并更新 is_loaded/local_tool_id</p>
     *
     * @param marketToolId 市场工具ID
     * @return 创建的 ai_tool.id
     */
    Long loadToolToLocal(Long marketToolId);

    /**
     * 批量加载市场工具
     *
     * @param marketToolIds 市场工具ID列表
     * @return 成功加载的 ai_tool.id 列表
     */
    List<Long> batchLoadTools(List<Long> marketToolIds);

    /**
     * 刷新结果
     */
    record McpMarketRefreshResult(int addedCount, int updatedCount) {
    }
}
