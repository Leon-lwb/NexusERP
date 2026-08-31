package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI A/B 测试结果实体
 * <p>记录每个测试问题在每个模型下的回答、Token 用量、延迟和人工评分</p>
 *
 * @author NexusERP
 */
@Data
@TableName("ai_ab_test_result")
public class AiAbTestResult {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

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
