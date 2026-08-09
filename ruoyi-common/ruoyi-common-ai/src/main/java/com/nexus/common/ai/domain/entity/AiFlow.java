package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 流程定义实体 ai_flow
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_flow")
public class AiFlow extends TenantEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 流程名称 */
    private String flowName;

    /** 流程标识(唯一, 定时/Webhook 引用) */
    private String flowKey;

    /** 描述 */
    private String description;

    /** 状态: DRAFT/PUBLISHED/OFFLINE */
    private String status;

    /** 版本号(每次发布+1) */
    private Integer version;

    /** 画布配置(JSON: nodes+edges) */
    private String configJson;

    /** 触发方式: MANUAL/SCHEDULE (逗号分隔可多选) */
    private String triggerType;

    /** 定时 Cron 表达式 */
    private String cronExpr;

    /** Webhook 访问令牌（空 = 不校验，flowKey 本身即令牌） */
    private String webhookToken;

    /** 节点失败最大重试次数 */
    private Integer maxRetry;
}
