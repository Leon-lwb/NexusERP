package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI A/B 测试实体
 * <p>支持同一问题对比不同模型的回答质量，帮助管理员选择最优模型</p>
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_ab_test")
public class AiAbTest extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 测试名称 */
    private String testName;

    /** 测试描述 */
    private String description;

    /** 模型A的ID */
    private Long modelAId;

    /** 模型B的ID */
    private Long modelBId;

    /** 测试问题列表（JSON数组） */
    private String testQuestions;

    /** 状态：PENDING/RUNNING/COMPLETED */
    private String status;

    /** 结果摘要（JSON） */
    private String resultSummary;
}
