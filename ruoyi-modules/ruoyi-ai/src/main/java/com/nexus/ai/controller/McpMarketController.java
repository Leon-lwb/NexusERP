package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.mcp.service.IMcpMarketService;
import com.nexus.ai.mcp.McpConfigSanitizer;
import com.nexus.ai.mcp.McpEndpointPolicy;
import com.nexus.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiMcpMarket;
import com.nexus.common.ai.domain.entity.AiMcpMarketTool;
import com.nexus.common.ai.mapper.AiMcpMarketMapper;
import com.nexus.common.ai.mapper.AiMcpMarketToolMapper;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * AI MCP 市场管理 Controller
 * <p>管理市场源 CRUD、刷新市场工具列表、一键加载工具到本地 ai_tool</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/mcp/market")
public class McpMarketController extends BaseController {

    /** 仅来自运维/启动参数的精确本地 URI 白名单，默认不授权任何本地地址。 */
    @Value("${nexus.ai.mcp.allowed-local-endpoints:}")
    private String allowedLocalEndpoints = "";

    /** MCP 市场源表 Mapper，负责市场源 CRUD 与分页。 */
    private final AiMcpMarketMapper marketMapper;
    /** MCP 市场工具表 Mapper，负责市场工具列表查询。 */
    private final AiMcpMarketToolMapper marketToolMapper;
    /** MCP 市场业务服务，负责工具刷新与加载到本地。 */
    private final IMcpMarketService mcpMarketService;

    /**
     * 市场源列表
     */
    @SaCheckPermission("ai:mcp:market:list")
    @GetMapping("/list")
    public TableDataInfo<AiMcpMarket> list(AiMcpMarket query, PageQuery pageQuery) {
        LambdaQueryWrapper<AiMcpMarket> lqw = new LambdaQueryWrapper<>();
        lqw.like(query.getName() != null, AiMcpMarket::getName, query.getName());
        lqw.orderByDesc(AiMcpMarket::getCreateTime);
        Page<AiMcpMarket> page = marketMapper.selectPage(pageQuery.build(), lqw);
        page.setRecords(page.getRecords().stream().map(McpConfigSanitizer::maskMarket).toList());
        return TableDataInfo.build(page);
    }

    /**
     * 获取市场源详情
     */
    @SaCheckPermission("ai:mcp:market:query")
    @GetMapping("/{id}")
    public R<AiMcpMarket> getInfo(@PathVariable Long id) {
        return R.ok(McpConfigSanitizer.maskMarket(marketMapper.selectById(id)));
    }

    /**
     * 新增市场源
     */
    @SaCheckPermission("ai:mcp:market:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiMcpMarket market) {
        McpEndpointPolicy.validate(market.getUrl(), allowedLocalEndpoints);
        // 请求体不能指定租户，由当前租户上下文和 SQL 插件填充。
        market.setTenantId(null);
        marketMapper.insert(market);
        return R.ok();
    }

    /**
     * 修改市场源
     */
    @SaCheckPermission("ai:mcp:market:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiMcpMarket market) {
        AiMcpMarket existing = marketMapper.selectById(market.getId());
        if (existing == null) {
            throw new ServiceException("市场不存在或无权访问");
        }
        market.setTenantId(null);
        if (McpConfigSanitizer.MASK.equals(market.getAuthConfig())) {
            market.setAuthConfig(existing.getAuthConfig());
        }
        if (market.getUrl() != null && market.getUrl().contains(McpConfigSanitizer.MASK)) {
            market.setUrl(McpConfigSanitizer.restoreMaskedUrl(market.getUrl(), existing.getUrl()));
        }
        McpEndpointPolicy.validate(market.getUrl() == null ? existing.getUrl() : market.getUrl(), allowedLocalEndpoints);
        marketMapper.updateById(market);
        return R.ok();
    }

    /**
     * 删除市场源
     */
    @SaCheckPermission("ai:mcp:market:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        marketMapper.deleteByIds(Arrays.asList(ids));
        return R.ok();
    }

    /**
     * 查询市场工具列表
     */
    @SaCheckPermission("ai:mcp:market:query")
    @GetMapping("/{marketId}/tools")
    public TableDataInfo<AiMcpMarketTool> getMarketTools(@PathVariable Long marketId, PageQuery pageQuery) {
        LambdaQueryWrapper<AiMcpMarketTool> lqw = new LambdaQueryWrapper<>();
        lqw.eq(AiMcpMarketTool::getMarketId, marketId);
        lqw.orderByDesc(AiMcpMarketTool::getCreateTime);
        Page<AiMcpMarketTool> page = marketToolMapper.selectPage(pageQuery.build(), lqw);
        page.setRecords(page.getRecords().stream().map(McpConfigSanitizer::maskMarketTool).toList());
        return TableDataInfo.build(page);
    }

    /**
     * 刷新市场工具列表（HTTP GET market.url，解析工具列表）
     */
    @SaCheckPermission("ai:mcp:market:edit")
    @PostMapping("/{marketId}/refresh")
    public R<IMcpMarketService.McpMarketRefreshResult> refreshMarketTools(@PathVariable Long marketId) {
        return R.ok(mcpMarketService.refreshMarketTools(marketId));
    }

    /**
     * 一键加载市场工具到本地 ai_tool（默认停用 status=1，字典 sys_normal_disable，需手动启用）
     */
    @SaCheckPermission("ai:mcp:market:edit")
    @PostMapping("/tools/{toolId}/load")
    public R<Long> loadToolToLocal(@PathVariable Long toolId) {
        return R.ok(mcpMarketService.loadToolToLocal(toolId));
    }

    /**
     * 批量加载市场工具
     */
    @SaCheckPermission("ai:mcp:market:edit")
    @PostMapping("/tools/batch-load")
    public R<List<Long>> batchLoadTools(@RequestBody List<Long> toolIds) {
        return R.ok(mcpMarketService.batchLoadTools(toolIds));
    }
}
