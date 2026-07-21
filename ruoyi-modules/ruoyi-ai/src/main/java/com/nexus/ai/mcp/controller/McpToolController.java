package com.nexus.ai.mcp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.mcp.dto.McpToolTestResult;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.ai.mcp.service.McpToolServiceImpl;
import com.nexus.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 工具管理控制器
 *
 * @author NexusERP
 */
@RestController
@RequestMapping("/ai/mcp/tool")
@RequiredArgsConstructor
public class McpToolController {

    /** MCP 工具业务服务，负责工具 CRUD、启用停用、同步与测试。 */
    private final McpToolServiceImpl mcpToolService;

    /**
     * 查询所有工具列表
     */
    @SaCheckPermission("ai:mcp:list")
    @GetMapping("/list")
    public R<List<AiTool>> list() {
        return R.ok(mcpToolService.getAllTools());
    }

    /**
     * 查询所有启用的工具
     */
    @SaCheckPermission("ai:mcp:list")
    @GetMapping("/enabled")
    public R<List<AiTool>> listEnabled() {
        return R.ok(mcpToolService.getAllEnabledTools());
    }

    /**
     * 根据 ID 查询工具详情
     */
    @SaCheckPermission("ai:mcp:query")
    @GetMapping("/{id}")
    public R<AiTool> getInfo(@PathVariable Long id) {
        return R.ok(mcpToolService.getToolById(id));
    }

    /**
     * 新增工具
     */
    @SaCheckPermission("ai:mcp:add")
    @PostMapping
    public R<Void> add(@RequestBody AiTool tool) {
        mcpToolService.addTool(tool);
        return R.ok();
    }

    /**
     * 修改工具
     */
    @SaCheckPermission("ai:mcp:edit")
    @PutMapping
    public R<Void> edit(@RequestBody AiTool tool) {
        mcpToolService.updateTool(tool);
        return R.ok();
    }

    /**
     * 删除工具
     */
    @SaCheckPermission("ai:mcp:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable List<Long> ids) {
        mcpToolService.deleteTools(ids);
        return R.ok();
    }

    /**
     * 启用/停用工具
     */
    @SaCheckPermission("ai:mcp:edit")
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id, @RequestParam String status) {
        mcpToolService.toggleToolStatus(id, status);
        return R.ok();
    }

    /**
     * 同步内置工具到数据库
     */
    @SaCheckPermission("ai:mcp:edit")
    @PostMapping("/sync")
    public R<Void> sync() {
        mcpToolService.syncBuiltinTools();
        return R.ok();
    }

    /**
     * 测试工具连接
     */
    @SaCheckPermission("ai:mcp:query")
    @PostMapping("/{id}/test")
    public R<McpToolTestResult> test(@PathVariable Long id) {
        return R.ok(mcpToolService.testTool(id));
    }

    /**
     * 获取当前可用的 MCP 工具列表（供前端工具选择器使用）
     * <p>返回所有状态为 ENABLED 的非内置工具，含 id/toolName/displayName/description</p>
     *
     * @return 可用 MCP 工具列表
     */
    @SaCheckPermission("ai:mcp:list")
    @GetMapping("/available")
    public R<List<AiTool>> getAvailableMcpTools() {
        List<AiTool> allEnabled = mcpToolService.getAllEnabledTools();
        // 过滤掉内置工具，只返回 MCP 外部工具
        List<AiTool> mcpTools = allEnabled.stream()
            .filter(t -> !"BUILTIN".equals(t.getToolType()))
            .toList();
        return R.ok(mcpTools);
    }
}
