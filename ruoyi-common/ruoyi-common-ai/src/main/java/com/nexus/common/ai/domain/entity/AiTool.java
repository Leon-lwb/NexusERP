package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 工具实体
 * <p>对应 ai_tool 表，存储内置工具和 MCP 工具的配置信息</p>
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_tool")
public class AiTool extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 工具名称（唯一标识，snake_case） */
    private String toolName;

    /** 显示名称 */
    private String displayName;

    /** 工具描述（AI 理解用途） */
    private String description;

    /** 工具类型（BUILTIN/LOCAL/REMOTE，字典 ai_mcp_tool_type） */
    private String toolType;

    /** 工具配置（JSON 格式，LOCAL/REMOTE 类型使用） */
    private String configJson;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    private String status;
}
