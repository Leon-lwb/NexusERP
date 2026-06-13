package com.nexus.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * ERP 数据查询 Agent
 * <p>
 * 注解放在接口方法上（非类级别），与 ruoyi-ai 的 SqlAgent 一致。
 * 由 LangChain4j agentic 框架在运行时动态代理实现。
 * </p>
 *
 * @author NexusERP
 */
public interface ErpQueryAgent {

    /**
     * 根据自然语言查询 ERP 数据
     * <p>
     * SystemMessage 仅包含角色定义和业务规则，动态上下文（Schema 摘要、工具列表、日期）
     * 由 PromptTemplateService 渲染后通过 AiServiceFacade 注入到用户消息中。
     * </p>
     *
     * @param query 用户自然语言查询
     * @return 查询结果
     */
    @SystemMessage("""
        你是一个 ERP 数据查询助手。你负责：
        1. 理解用户的自然语言问题，选择合适的工具查询 ERP 数据
        2. 用清晰的中文回答用户的问题，必要时用表格展示数据
        3. 涉及金额时保留2位小数，涉及日期时使用 YYYY-MM-DD 格式

        === 业务规则 ===
        - 优先使用业务专用工具（如 queryLowStockMaterials），业务工具无法满足时再使用通用查询工具
        - 使用通用查询工具时，必须使用表别名，金额字段使用 ROUND(x, 2)
        - 所有查询必须包含 tenant_id 条件以遵守数据隔离
        - 库存事务出库使用 wm_material_stock_tx 表的 tx_flag = -1 表示出库
        - 采购订单状态流转：DRAFT→SUBMITTED→APPROVING→APPROVED→PARTIALLY_RECEIVED→RECEIVED→CLOSED
        - 销售退货表为 so_return（非 so_sales_return），主键为 return_id
        - BOM 父件字段为 parent_material_id，子件字段为 child_material_id，用量字段为 quantity_per_parent
        - 如果 SQL 执行报错，请根据错误信息修正 SQL 后重试
        """)
    @UserMessage("请回答以下问题：{{query}}")
    @Agent("ERP 数据查询助手")
    String query(@V("query") String query);
}
