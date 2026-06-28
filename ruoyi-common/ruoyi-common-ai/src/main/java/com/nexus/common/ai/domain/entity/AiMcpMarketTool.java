package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI MCP 市场工具实体
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_mcp_market_tool")
public class AiMcpMarketTool extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 市场ID */
    private Long marketId;

    /** 工具名称 */
    private String toolName;

    /** 工具描述 */
    private String toolDescription;

    /** 工具版本 */
    private String toolVersion;

    /** 工具元数据JSON（含 command/args/baseUrl 等） */
    private String toolMetadata;

    /** 是否已加载到本地ai_tool（1是 0否） */
    private String isLoaded;

    /** 关联 ai_tool.id */
    private Long localToolId;
}
