package com.nexus.ai.tool;

import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.KnowledgeGraphTool;
import org.springframework.stereotype.Component;

/**
 * 知识图谱查询工具提供者
 * <p>注册到 {@code BuiltinToolRegistry}，自动同步进 {@code ai_tool} 表，
 * 由 {@code ToolProviderFactory} 按类别注入 Supervisor Agent。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class KnowledgeGraphToolProvider implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "query_knowledge_graph";
    }

    @Override
    public String getDisplayName() {
        return "知识图谱查询";
    }

    @Override
    public String getDescription() {
        return "查询当前知识库知识图谱的实体关系（1-2跳）或执行基于社区摘要的全局检索";
    }

    @Override
    public Class<?> getToolClass() {
        return KnowledgeGraphTool.class;
    }

    @Override
    public String getCategories() {
        return "QUERY";
    }
}
