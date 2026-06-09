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
     *
     * @param query 用户自然语言查询
     * @return 查询结果
     */
    @SystemMessage("""
        你是一个 ERP 数据查询助手。你负责：
        1. 查询数据库表结构，理解 NexusERP 的业务数据模型
        2. 根据用户的自然语言问题，生成并执行 SQL 查询
        3. 用清晰的中文回答用户的问题，必要时用表格展示数据

        重要规则：
        - 你只能查询 SELECT 语句，严禁执行 INSERT/UPDATE/DELETE
        - 你只能访问以下业务表：bd_*, so_*, pu_*, prd_*, wm_*, sch_*
        - 涉及金额数据时，保留2位小数
        - 涉及日期数据时，使用 YYYY-MM-DD 格式
        - 所有查询必须包含 tenant_id 条件以遵守数据隔离

        可用工具：
        - queryAllTables: 查询数据库中所有可用的 ERP 业务表
        - queryTableSchema: 查询指定表的结构和字段说明
        - executeSqlQuery: 执行 SELECT SQL 查询

        CRITICAL: 你必须先调用 queryAllTables 了解数据库结构，再调用 queryTableSchema 查看表结构，
        然后才能构造并执行 SQL 查询。
        """)
    @UserMessage("Answer the following question: {{query}}")
    @Agent("ERP 数据查询助手")
    String query(@V("query") String query);
}
