package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 对话消息实体
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessage extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 会话ID */
    private Long sessionId;

    /** 角色: USER/ASSISTANT/SYSTEM */
    private String role;

    /** 消息内容 */
    private String content;

    /** 推理过程内容（enableThinking 时存） */
    private String thinkingContent;

    /** Token 消耗（总量 = inputTokens + outputTokens） */
    private Long tokenUsage;

    /** 使用的模型ID */
    private Long modelId;

    /** 输入 Token 数 */
    private Integer inputTokens;

    /** 输出 Token 数 */
    private Integer outputTokens;

    /** 用户反馈: LIKE/DISLIKE */
    private String feedback;

    /** 反馈内容 */
    private String feedbackContent;
}
