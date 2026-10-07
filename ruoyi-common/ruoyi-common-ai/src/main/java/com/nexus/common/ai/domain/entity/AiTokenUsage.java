package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI Token 用量日统计实体
 * <p>按 (租户+用户+模型+日期) 聚合 Token 用量和成本，支持增量更新</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@TableName("ai_token_usage")
public class AiTokenUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 用户ID */
    private Long userId;

    /** 用户名称（冗余，避免关联查询） */
    private String userName;

    /** 模型ID */
    private Long modelId;

    /** 模型名称（冗余） */
    private String modelName;

    /** 统计日期 */
    private LocalDate usageDate;

    /** 当日输入 Token 总量 */
    private Long totalInputTokens;

    /** 当日输出 Token 总量 */
    private Long totalOutputTokens;

    /** 当日 Token 总量（input+output） */
    private Long totalTokens;

    /** 当日请求次数 */
    private Integer requestCount;

    /** 当日总成本（元） */
    private BigDecimal totalCost;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;

    /** 更新时间 */
    private java.time.LocalDateTime updateTime;
}
