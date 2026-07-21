package com.nexus.ai.config;

import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import com.nexus.common.ai.mapper.AiPromptTemplateMapper;
import com.nexus.common.core.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * AI Prompt 模板初始化器
 * <p>应用启动时自动插入默认 Prompt 模板（如果不存在）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPromptTemplateInitializer implements ApplicationRunner {

    /** Prompt 模板持久层 Mapper，用于判断模板是否已存在并执行插入。 */
    private final AiPromptTemplateMapper promptTemplateMapper;

    /**
     * 应用启动时执行，初始化 ErpQueryAgent、ErpReportAgent、Supervisor 三类默认 Prompt 模板。
     *
     * @param args Spring 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        initDefaultTemplate("erp_query_agent", "ERP 数据查询助手上下文",
            """
            === ERP 数据库表结构 ===
            {{schemaSummary}}

            === 当前日期 ===
            {{date}}

            === 可用工具列表 ===
            {{availableTools}}

            === SQL 查询示例 ===
            {{sqlExamples}}

            请参考以上示例生成 SQL，但不要照搬，需要根据实际问题灵活调整。
            """,
            "为 ErpQueryAgent 提供动态 Schema 摘要、工具列表和 SQL 示例上下文");

        initDefaultTemplate("erp_report_agent", "ERP 报表生成助手上下文",
            """
            === ERP 数据库表结构 ===
            {{schemaSummary}}

            === 当前日期 ===
            {{date}}
            """,
            "为 ErpReportAgent 提供动态 Schema 摘要上下文");

        initDefaultTemplate("supervisor", "Supervisor 调度中心上下文",
            """
            你是 NexusERP 智能助手调度中心。根据用户问题，选择最合适的子 Agent 处理：

            1. **数据查询助手**（ERP 数据查询助手）：适用于需要查询具体数据、执行 SQL、回答事实性问题的场景
               - 例："本月采购总额是多少？"、"库存低于安全线的物料有哪些？"

            2. **报表生成助手**（ERP 数据可视化专家）：适用于需要生成图表、可视化数据趋势的场景
               - 例："展示最近6个月销售趋势图"、"各品类采购金额占比饼图"

            路由规则：
            - 如果用户只问数据 → 分发给数据查询助手
            - 如果用户要求图表/可视化 → 分发给报表生成助手
            - 如果用户同时要数据和图表 → 先让查询助手获取数据，再让报表助手生成图表
            - 如果不确定 → 优先分发给数据查询助手

            当前日期：{{date}}
            """,
            "Supervisor Agent 的路由引导 SystemMessage");

        log.info("[PromptTemplate] 默认模板初始化完成");
    }

    /**
     * 初始化单个默认模板（仅当 templateKey 不存在时插入）
     *
     * @param templateKey   模板标识
     * @param templateName  模板名称
     * @param content       模板内容
     * @param description   描述
     */
    private void initDefaultTemplate(String templateKey, String templateName, String content, String description) {
        LambdaQueryWrapper<AiPromptTemplate> lqw = new LambdaQueryWrapper<>();
        lqw.eq(AiPromptTemplate::getTemplateKey, templateKey);
        if (promptTemplateMapper.selectCount(lqw) > 0) {
            log.debug("[PromptTemplate] 模板 {} 已存在，跳过初始化", templateKey);
            return;
        }
        AiPromptTemplate template = new AiPromptTemplate();
        template.setTenantId(0L);  // 默认模板使用全局租户ID，确保多租户环境下可访问
        template.setTemplateName(templateName);
        template.setTemplateKey(templateKey);
        template.setContent(content);
        template.setDescription(description);
        template.setIsDefault("1");
        template.setVersion(1);
        template.setStatus("1");
        promptTemplateMapper.insert(template);
        log.info("[PromptTemplate] 已初始化默认模板: {}", templateKey);
    }
}
