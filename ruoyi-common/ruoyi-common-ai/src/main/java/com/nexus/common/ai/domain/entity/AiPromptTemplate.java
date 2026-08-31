package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Prompt 模板实体
 * <p>对应 ai_prompt_template 表，存储 Agent 的系统提示词模板</p>
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt_template")
public class AiPromptTemplate extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 模板名称 */
    private String templateName;

    /** 模板标识（如 erp_query_agent/erp_report_agent/supervisor） */
    private String templateKey;

    /** 模板内容（支持 {{variable}} 占位符） */
    private String content;

    /** 描述 */
    private String description;

    /** 是否默认模板（Y是 N否，字典 sys_yes_no） */
    private String isDefault;

    /** 版本号 */
    private Integer version;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    private String status;
}
