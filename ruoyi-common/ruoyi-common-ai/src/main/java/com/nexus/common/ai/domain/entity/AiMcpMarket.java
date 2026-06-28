package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI MCP 市场源实体
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_mcp_market")
public class AiMcpMarket extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 市场名称 */
    private String name;

    /** 市场URL（返回工具列表JSON） */
    private String url;

    /** 描述 */
    private String description;

    /** 认证配置（JSON） */
    private String authConfig;

    /** 状态（1启用 0停用） */
    private String status;
}
