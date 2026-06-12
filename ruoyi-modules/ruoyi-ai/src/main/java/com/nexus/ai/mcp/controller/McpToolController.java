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
}
