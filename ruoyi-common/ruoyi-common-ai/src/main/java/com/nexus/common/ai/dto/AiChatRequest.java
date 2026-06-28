package com.nexus.common.ai.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * AI 聊天请求 DTO
 * <p>支持三种模式：普通流式对话、Agent 编排、RAG 增强</p>
 * <p>mode 字段用于前端显式指定模式，为空时由后端根据关键词自动路由</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
public class AiChatRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话ID（新会话传 null） */
    private Long sessionId;

    /** 消息内容 */
    private String content;

    /** 模型名称（可选，为空使用默认模型） */
    private String model;

    /** 知识库ID（可选，指定后走 RAG 增强模式） */
    private Long knowledgeId;

    /**
     * 对话模式（可选，为空时由后端自动路由）
     * <ul>
     *   <li>{@code auto} — 自动路由（默认，根据关键词判断）</li>
     *   <li>{@code chat} — 普通流式对话</li>
     *   <li>{@code agent} — Agent 编排模式（数据查询/报表生成）</li>
     *   <li>{@code rag} — RAG 增强模式（需配合 knowledgeId 使用）</li>
     * </ul>
     */
    private String mode;

    /**
     * 本次对话启用的 MCP 工具 ID 列表
     * <p>null 表示使用会话默认工具集，非空时覆盖会话设置</p>
     */
    private List<Long> enabledToolIds;

    /**
     * 是否开启推理（Thinking）模式
     * <p>开启后输出推理 token 流（DeepSeek-R1 等推理模型），前端折叠展示思考过程</p>
     * <p>仅对支持 returnThinking 的厂商（OpenAI 兼容类）生效</p>
     */
    private Boolean enableThinking = false;

    /**
     * 是否开启联网搜索模式（S2-C）
     * <p>开启后路由到 WebSearchAgent，回答需标注信息来源链接</p>
     */
    private Boolean enableWebSearch = false;
}
