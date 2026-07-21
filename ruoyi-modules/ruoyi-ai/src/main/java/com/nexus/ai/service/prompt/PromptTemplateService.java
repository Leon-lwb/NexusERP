package com.nexus.ai.service.prompt;

import com.nexus.ai.mcp.BuiltinToolRegistry;
import com.nexus.ai.service.query.ErpTableSchemaManager;
import com.nexus.ai.service.query.SqlExampleService;
import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import com.nexus.common.ai.domain.vo.AiSqlExampleVo;
import com.nexus.common.ai.service.IAiPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板渲染服务
 * <p>
 * 从数据库加载 Prompt 模板，支持 {{variable}} 占位符替换。
 * 为 Agent 提供动态 SystemMessage 上下文（Schema 摘要、日期、可用工具等）。
 * </p>
 * <p>
 * 由于 LangChain4j AgenticServices.agentBuilder() 不支持动态 systemMessage，
 * 本服务渲染的上下文通过 AiServiceFacade.buildContextualQuery() 注入到用户消息中。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptTemplateService {

    /** Prompt 模板持久化服务，按 templateKey 加载模板内容 */
    private final IAiPromptTemplateService promptTemplateService;
    /** ERP 表结构缓存管理器，提供 Schema 摘要 */
    private final ErpTableSchemaManager schemaManager;
    /** 内置工具注册表，提供可用工具描述 */
    private final BuiltinToolRegistry builtinToolRegistry;
    /** SQL 示例匹配服务，提供 Few-Shot 示例 */
    private final SqlExampleService sqlExampleService;

    /** 模板内容缓存：templateKey -> content（变更时清除） */
    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 渲染 Prompt 模板
     * <p>将 {{variable}} 占位符替换为实际值</p>
     *
     * @param templateKey 模板标识
     * @param variables   变量映射
     * @return 渲染后的内容，模板不存在时返回空字符串
     */
    public String render(String templateKey, Map<String, String> variables) {
        String template = getTemplate(templateKey);
        if (template == null || template.isEmpty()) {
            log.warn("[PromptTemplate] 模板 {} 不存在，返回空", templateKey);
            return "";
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    /**
     * 渲染 ErpQueryAgent 的上下文
     * <p>自动注入：schemaSummary（按优先级裁剪）、date、availableTools、sqlExamples</p>
     *
     * @return 渲染后的上下文文本
     */
    public String renderQueryAgentContext() {
        Map<String, String> variables = new HashMap<>();
        variables.put("schemaSummary", schemaManager.getOptimizedSchemaSummary(4000));
        variables.put("date", LocalDate.now().toString());
        variables.put("availableTools", getAvailableToolsDescription());
        // 注入 SQL Few-Shot 示例
        List<AiSqlExampleVo> examples = sqlExampleService.getAllEnabledExamples();
        variables.put("sqlExamples", sqlExampleService.formatExamplesForPrompt(examples));
        return render("erp_query_agent", variables);
    }

    /**
     * 渲染 ErpReportAgent 的上下文
     * <p>自动注入：schemaSummary（按优先级裁剪）、date</p>
     *
     * @return 渲染后的上下文文本
     */
    public String renderReportAgentContext() {
        Map<String, String> variables = new HashMap<>();
        variables.put("schemaSummary", schemaManager.getOptimizedSchemaSummary(4000));
        variables.put("date", LocalDate.now().toString());
        return render("erp_report_agent", variables);
    }

    /**
     * 渲染 Supervisor 的上下文
     * <p>自动注入：date</p>
     *
     * @return 渲染后的上下文文本
     */
    public String renderSupervisorContext() {
        Map<String, String> variables = new HashMap<>();
        variables.put("date", LocalDate.now().toString());
        return render("supervisor", variables);
    }

    /**
     * 清除指定模板的缓存（模板变更时调用）
     *
     * @param templateKey 模板标识
     */
    public void evictCache(String templateKey) {
        templateCache.remove(templateKey);
        log.info("[PromptTemplate] 已清除模板缓存: {}", templateKey);
    }

    /**
     * 清除全部模板缓存
     */
    public void evictAllCache() {
        templateCache.clear();
        log.info("[PromptTemplate] 已清除全部模板缓存");
    }

    /**
     * 获取模板内容（优先缓存，缓存未命中时查库）
     *
     * @param templateKey 模板标识
     * @return 模板内容
     */
    private String getTemplate(String templateKey) {
        return templateCache.computeIfAbsent(templateKey, key -> {
            AiPromptTemplate template = promptTemplateService.selectByTemplateKey(key);
            if (template != null) {
                return template.getContent();
            }
            log.warn("[PromptTemplate] 数据库中未找到模板: {}", key);
            return "";
        });
    }

    /**
     * 获取可用工具描述文本（用于注入到 Prompt 上下文）
     *
     * @return 工具描述列表
     */
    private String getAvailableToolsDescription() {
        StringBuilder sb = new StringBuilder();
        builtinToolRegistry.getAllProviders().forEach(provider -> {
            sb.append("- ").append(provider.getToolName()).append(": ").append(provider.getDescription()).append("\n");
        });
        return sb.toString();
    }
}
