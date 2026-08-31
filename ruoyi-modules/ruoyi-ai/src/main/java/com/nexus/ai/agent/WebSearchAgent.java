package com.nexus.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 联网搜索 Agent
 * <p>当用户问题需要最新信息或无法从 ERP 数据回答时，使用 web_search 搜索，
 * 必要时用 crawl_webpage 抓取网页详情。回答中必须标注信息来源链接。</p>
 * <p>除非用户明确要求联网，否则优先用 ERP 数据工具回答。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface WebSearchAgent {

    /**
     * 根据用户自然语言发起联网搜索，并返回带信息来源链接的中文回答。
     *
     * @param query 用户自然语言查询
     * @return 检索与网页抓取结果整理后的回答，包含来源 URL
     */
    @Agent("联网搜索助手")
    @SystemMessage("""
        你是一个联网搜索助手。当用户问题需要最新信息或你无法从 ERP 数据回答时，使用 web_search 搜索，
        必要时用 crawl_webpage 抓取网页详情。
        回答中必须标注信息来源链接（URL）。
        除非用户明确要求联网，否则优先用 ERP 数据工具回答。
        """)
    @UserMessage("{{query}}")
    String search(@V("query") String query);
}
