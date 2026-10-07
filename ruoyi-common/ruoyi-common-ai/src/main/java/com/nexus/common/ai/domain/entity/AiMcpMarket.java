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

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 市场名称 */
    private String name;

    /** 市场URL（返回工具列表JSON） */
    private String url;

    /** 描述 */
    private String description;

    /** 认证配置（JSON） */
    private String authConfig;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    private String status;
}
