package com.nexus.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.ai.mcp.McpConfigSanitizer;
import com.nexus.ai.mcp.McpEndpointPolicy;
import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.ai.domain.dto.McpToolTestResult;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /** 仅来自运维/启动参数的精确本地 URI 白名单，默认不授权任何本地地址。 */
    @Value("${nexus.ai.mcp.allowed-local-endpoints:}")
    private String allowedLocalEndpoints = "";

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
        wrapper.eq(AiTool::getStatus, AiToolStatusContract.ACTIVE);
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
        validateTool(tool);
        // 不接受客户端租户编号，避免绕过租户插件的 INSERT 注入。
        tool.setTenantId(null);
        return aiToolMapper.insert(tool);
    }

    /**
     * 更新工具
     * <p>安全：查询接口出参已对 configJson 敏感值脱敏为 ****，编辑回显原样提交会把
     * 掩码写回库破坏连接——按 JSON 树恢复掩码叶子，保留真正修改的非敏感字段。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateTool(AiTool tool) {
        AiTool existing = aiToolMapper.selectById(tool.getId());
        if (existing == null) {
            throw new ServiceException("工具不存在或无权访问");
        }
        tool.setTenantId(null);
        if (tool.getConfigJson() != null) {
            tool.setConfigJson(McpConfigSanitizer.restoreMasks(tool.getConfigJson(), existing.getConfigJson()));
        }
        if (tool.getToolType() == null) {
            tool.setToolType(existing.getToolType());
        }
        validateStatus(tool.getStatus());
        if (tool.getConfigJson() != null || !tool.getToolType().equals(existing.getToolType())) {
            validateTool(tool);
        }
        int result = aiToolMapper.updateById(tool);
        // 如果是 MCP 外部工具且配置变更，刷新连接
        if (result > 0 && (!BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType())
            || !BuiltinToolRegistry.TYPE_BUILTIN.equals(existing.getToolType()))) {
            refreshAfterCommit(tool.getId());
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
                return tool != null && !BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType());
            })
            .toList();

        if (deletableIds.isEmpty()) {
            return 0;
        }

        int result = aiToolMapper.deleteBatchIds(deletableIds);
        // 仅当前租户确实删除了记录时才失效连接，不能触碰不可见 ID 的缓存。
        if (result > 0) {
            deletableIds.forEach(this::refreshAfterCommit);
        }
        return result;
    }

    /**
     * 启用/停用工具
     * <p>status 白名单校验：仅接受 {@link AiToolStatusContract#ACTIVE}(0=启用) /
     * {@link AiToolStatusContract#DISABLED}(1=停用，字典 sys_normal_disable)。
     * 旧前端(字典化改造前)仍提交 ENABLED/DISABLED 串，迁移后的读取端只认 0/1，
     * 放行会写入任何查询都匹配不到的脏状态值，故直接拒绝。</p>
     *
     * @param id     工具ID
     * @param status 目标状态（0=启用 / 1=停用）
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int toggleToolStatus(Long id, String status) {
        if (!AiToolStatusContract.ACTIVE.equals(status) && !AiToolStatusContract.DISABLED.equals(status)) {
            throw new ServiceException("非法的工具状态值: " + status + "，仅支持 0=启用 / 1=停用");
        }
        if (aiToolMapper.selectById(id) == null) {
            throw new ServiceException("工具不存在或无权访问");
        }
        AiTool tool = new AiTool();
        tool.setId(id);
        tool.setStatus(status);
        int result = aiToolMapper.updateById(tool);
        if (result > 0) {
            refreshAfterCommit(id);
        }
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
                    // 安全：新内置工具默认停用（1，字典 sys_normal_disable），由管理员在工具管理页显式启用后再对聊天用户开放
                    // （原默认启用：首次部署即把全库 SELECT 能力暴露给所有登录用户）
                    newTool.setStatus(AiToolStatusContract.DISABLED);
                    aiToolMapper.insert(newTool);
                    log.info("[MCP] 新增内置工具（默认禁用，需手动启用）: {} ({})", provider.getToolName(), provider.getDisplayName());
                    addedCount++;
                }
            } catch (Exception e) {
                // 任一提供者失败都中止整次同步，由外层事务回滚此前成功的写入，不能误报成功。
                // 不附带原异常、cause或提供者属性，避免泄漏SQL、凭据及其他租户信息。
                log.error("[MCP] 同步内置工具失败，本次事务将回滚");
                throw new ServiceException("内置工具同步失败，本次同步未完成，请联系管理员");
            }
        }

        log.info("[MCP] 内置工具同步完成: 新增 {} 个, 已存在 {} 个", addedCount, existingCount);
    }

    // ==================== 私有方法 ====================

    /** 事务内只登记提交后失效；回滚保留旧连接，不让未提交配置窗口重新填充旧缓存。 */
    private void refreshAfterCommit(Long toolId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            mcpClientManager.refreshClient(toolId);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new ServiceException("MCP事务同步不可用，配置修改未完成");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mcpClientManager.refreshClient(toolId);
            }
        });
    }

    private void validateStatus(String status) {
        if (status != null && !AiToolStatusContract.ACTIVE.equals(status) && !AiToolStatusContract.DISABLED.equals(status)) {
            throw new ServiceException("工具状态仅支持 0=启用 / 1=停用");
        }
    }

    private void validateTool(AiTool tool) {
        validateStatus(tool.getStatus());
        if (BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getToolType())) {
            return;
        }
        var config = McpConfigSanitizer.parseObject(tool.getConfigJson());
        McpConfigSanitizer.stringMap(config.path("headers"), "headers");
        McpConfigSanitizer.stringMap(config.path("env"), "env");
        if ("REMOTE".equals(tool.getToolType())) {
            McpEndpointPolicy.validateRemoteTransport(config.path("baseUrl").asText(null), allowedLocalEndpoints);
        } else if ("LOCAL".equals(tool.getToolType())) {
            if (!config.path("command").isTextual() || config.path("command").asText().isBlank()) {
                throw new ServiceException("LOCAL 工具的 command 不能为空");
            }
        } else {
            throw new ServiceException("工具类型仅支持 BUILTIN/LOCAL/REMOTE");
        }
    }

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
            log.error("[MCP] 测试内置工具失败: {} - {}", tool.getToolName(), e.getClass().getSimpleName());
            return McpToolTestResult.fail("测试失败: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 测试 MCP 外部工具连接
     */
    private McpToolTestResult testMcpTool(AiTool tool) {
        try {
            List<String> discovered = mcpClientManager.discoverToolNames(tool.getId());
            if (discovered != null) {
                return McpToolTestResult.success(
                    String.format("MCP工具 [%s] 连接测试成功", tool.getToolName()),
                    discovered.size(),
                    discovered
                );
            } else {
                return McpToolTestResult.fail(
                    String.format("MCP工具 [%s] 连接测试失败", tool.getToolName())
                );
            }
        } catch (Exception e) {
            log.error("[MCP] 测试MCP工具失败: {} - {}", tool.getToolName(), e.getClass().getSimpleName());
            return McpToolTestResult.fail("测试失败: " + e.getClass().getSimpleName());
        }
    }
}
