package com.nexus.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.mcp.BuiltinToolProvider;
import com.nexus.ai.mcp.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MCP 工具服务实现
 * <p>提供工具 CRUD 和同步内置工具到数据库的功能</p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolServiceImpl {

    private final AiToolMapper aiToolMapper;
    private final BuiltinToolRegistry builtinToolRegistry;

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
    @Transactional
    public int addTool(AiTool tool) {
        return aiToolMapper.insert(tool);
    }

    /**
     * 更新工具
     */
    @Transactional
    public int updateTool(AiTool tool) {
        return aiToolMapper.updateById(tool);
    }

    /**
     * 删除工具
     */
    @Transactional
    public int deleteTools(List<Long> ids) {
        return aiToolMapper.deleteBatchIds(ids);
    }

    /**
     * 启用/停用工具
     */
    @Transactional
    public int toggleToolStatus(Long id, String status) {
        AiTool tool = new AiTool();
        tool.setId(id);
        tool.setStatus(status);
        return aiToolMapper.updateById(tool);
    }

    /**
     * 同步内置工具到数据库
     * <p>只同步新增工具，不覆盖已有状态</p>
     */
    @Transactional
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
                    newTool.setStatus("ENABLED");
                    aiToolMapper.insert(newTool);
                    log.info("[MCP] 新增内置工具: {} ({})", provider.getToolName(), provider.getDisplayName());
                    addedCount++;
                }
            } catch (Exception e) {
                log.error("[MCP] 同步内置工具失败: {}", provider.getToolName(), e);
            }
        }

        log.info("[MCP] 内置工具同步完成: 新增 {} 个, 已存在 {} 个", addedCount, existingCount);
    }
}
