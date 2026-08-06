package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiFlow;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 流程定义业务对象 ai_flow
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiFlow.class, reverseConvertGenerate = false)
public class AiFlowBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 流程名称 */
    @NotBlank(message = "流程名称不能为空")
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

    /** 节点失败最大重试次数 */
    private Integer maxRetry;

    /** 批量删除ID集合 */
    private Long[] ids;
}
