package com.nexus.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.ai.domain.dto.McpToolTestResult;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MCP 工具服务实现
 * <p>提供工具 CRUD、同步内置工具到数据库、工具测试连接等功能</p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolServiceImpl {

    /** AI 工具表 Mapper，负责工具 CRUD 与查询。 */
    private final AiToolMapper aiToolMapper;
    /** 内置工具注册中心，用于同步与测试内置工具。 */
    private final BuiltinToolRegistry builtinToolRegistry;
    /** MCP 客户端管理器，负责外部工具连接刷新与健康检查。 */
    private final McpClientManager mcpClientManager;

    /**
     * 查询所有启用的工具
     */
    public List<AiTool> getAllEnabledTools() {
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiTool::getStatus, "ENABLED");
        return aiToolMapper.selectList(wrapper);
    }

    /**
     * 查询所有工具（包括禁用的）
     */
    public List<AiTool> getAllTools() {
        return aiToolMapper.selectList(null);
    }

    /**
     * 根据 ID 查询工具
     */
    public AiTool getToolById(Long id) {
        return aiToolMapper.selectById(id);
    }

    /**
     * 新增工具
     */
    @Transactional(rollbackFor = Exception.class)
    public int addTool(AiTool tool) {
        return aiToolMapper.insert(tool);
    }

    /**
     * 更新工具
     * <p>安全：查询接口出参已对 configJson 敏感值脱敏为 ****，编辑回显原样提交会把
     * 掩码写回库破坏连接——含掩码值时保留库内原 configJson（参照 AiProvider 的 apiKey 保护模式）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateTool(AiTool tool) {
        if (tool.getConfigJson() != null && tool.getConfigJson().contains("\":\"****\"")) {
            AiTool existing = aiToolMapper.selectById(tool.getId());
            if (existing != null) {
                tool.setConfigJson(existing.getConfigJson());
            }
        }
        int result = aiToolMapper.updateById(tool);
        // 如果是 MCP 外部工具且配置变更，刷新连接
        if (!BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType())) {
            mcpClientManager.refreshClient(tool.getId());
        }
        return result;
    }

    /**
     * 删除工具
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteTools(List<Long> ids) {
        // 过滤掉内置工具（不允许删除）
        List<Long> deletableIds = ids.stream()
            .filter(id -> {
                AiTool tool = aiToolMapper.selectById(id);
                return tool == null || !BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType());
            })
            .toList();

        if (deletableIds.isEmpty()) {
            return 0;
        }

        // 刷新 MCP 连接
        deletableIds.forEach(id -> mcpClientManager.refreshClient(id));
        return aiToolMapper.deleteBatchIds(deletableIds);
    }

    /**
     * 启用/停用工具
     */
    @Transactional(rollbackFor = Exception.class)
    public int toggleToolStatus(Long id, String status) {
        AiTool tool = new AiTool();
        tool.setId(id);
        tool.setStatus(status);
        int result = aiToolMapper.updateById(tool);
        // 刷新 MCP 连接
        mcpClientManager.refreshClient(id);
        return result;
    }

    /**
     * 测试工具连接
     *
     * @param id 工具ID
     * @return 测试结果
     */
    public McpToolTestResult testTool(Long id) {
        AiTool tool = aiToolMapper.selectById(id);
        if (tool == null) {
            return McpToolTestResult.fail("工具不存在");
        }

        if (BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType())) {
            return testBuiltinTool(tool);
        } else {
            return testMcpTool(tool);
        }
    }

    /**
     * 同步内置工具到数据库
     * <p>只同步新增工具，不覆盖已有状态</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncBuiltinTools() {
        log.info("[MCP] 开始同步内置工具到数据库...");
        int addedCount = 0;
        int existingCount = 0;

        for (BuiltinToolProvider provider : builtinToolRegistry.getAllProviders()) {
            try {
                // 检查是否已存在
                LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AiTool::getToolName, provider.getToolName())
                    .eq(AiTool::getToolType, BuiltinToolRegistry.TYPE_BUILTIN);

                AiTool existing = aiToolMapper.selectOne(wrapper);

                if (existing != null) {
                    // 已存在，更新描述信息（保留状态不变）
                    if (!java.util.Objects.equals(provider.getDescription(), existing.getDescription())) {
                        existing.setDescription(provider.getDescription());
                        existing.setDisplayName(provider.getDisplayName());
                        aiToolMapper.updateById(existing);
                        log.debug("[MCP] 更新内置工具描述: {}", provider.getToolName());
                    }
                    existingCount++;
                } else {
                    // 新增
                    AiTool newTool = new AiTool();
                    newTool.setToolName(provider.getToolName());
                    newTool.setDisplayName(provider.getDisplayName());
                    newTool.setDescription(provider.getDescription());
                    newTool.setToolType(BuiltinToolRegistry.TYPE_BUILTIN);
                    // 安全：新内置工具默认 DISABLED，由管理员在工具管理页显式启用后再对聊天用户开放
                    // （原默认 ENABLED：首次部署即把全库 SELECT/财务数据工具暴露给所有登录用户）
                    newTool.setStatus("DISABLED");
                    aiToolMapper.insert(newTool);
                    log.info("[MCP] 新增内置工具（默认禁用，需手动启用）: {} ({})", provider.getToolName(), provider.getDisplayName());
                    addedCount++;
                }
            } catch (Exception e) {
                log.error("[MCP] 同步内置工具失败: {}", provider.getToolName(), e);
            }
        }

        log.info("[MCP] 内置工具同步完成: 新增 {} 个, 已存在 {} 个", addedCount, existingCount);
    }

    // ==================== 私有方法 ====================

    /**
     * 测试内置工具
     */
    private McpToolTestResult testBuiltinTool(AiTool tool) {
        try {
            boolean isRegistered = builtinToolRegistry.hasTool(tool.getToolName());
            if (isRegistered) {
                return McpToolTestResult.success(
                    String.format("内置工具 [%s] 已注册，可正常使用", tool.getToolName()),
                    1,
                    List.of(tool.getToolName())
                );
            } else {
                return McpToolTestResult.fail(
                    String.format("内置工具 [%s] 未在注册表中找到", tool.getToolName())
                );
            }
        } catch (Exception e) {
            log.error("[MCP] 测试内置工具失败: {} - {}", tool.getToolName(), e.getMessage());
            return McpToolTestResult.fail("测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 MCP 外部工具连接
     */
    private McpToolTestResult testMcpTool(AiTool tool) {
        try {
            boolean isHealthy = mcpClientManager.checkToolHealth(tool.getId());
            if (isHealthy) {
                return McpToolTestResult.success(
                    String.format("MCP工具 [%s] 连接测试成功", tool.getToolName()),
                    1,
                    List.of(tool.getToolName())
                );
            } else {
                return McpToolTestResult.fail(
                    String.format("MCP工具 [%s] 连接测试失败", tool.getToolName())
                );
            }
        } catch (Exception e) {
            log.error("[MCP] 测试MCP工具失败: {} - {}", tool.getToolName(), e.getMessage());
            return McpToolTestResult.fail("测试失败: " + e.getMessage());
        }
    }
}
