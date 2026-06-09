package com.nexus.common.ai.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 聊天请求 DTO
 *
 * @author NexusERP
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
}
