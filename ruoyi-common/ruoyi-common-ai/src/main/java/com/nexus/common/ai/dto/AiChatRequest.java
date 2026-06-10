package com.nexus.common.ai.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 聊天请求 DTO
 * <p>支持三种模式：普通流式对话、Agent 编排（含数据查询关键词时自动路由）、RAG 增强（指定 knowledgeId 或含知识检索关键词时自动路由）</p>
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
}
