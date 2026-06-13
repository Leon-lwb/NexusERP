package com.nexus.common.ai.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI A/B 测试结果 Vo
 *
 * @author NexusERP
 */
@Data
public class AiAbTestResultVo {

    /** 主键 */
    private Long id;

    /** 测试ID */
    private Long testId;

    /** 测试问题 */
    private String question;

    /** 模型ID */
    private Long modelId;

    /** 模型名称 */
    private String modelName;

    /** 模型回答 */
    private String response;

    /** Token 用量 */
    private Integer tokenUsage;

    /** 响应延迟（毫秒） */
    private Integer latencyMs;

    /** 人工评分（1-5） */
    private Integer score;

    /** 创建时间 */
    private LocalDateTime createTime;
}
