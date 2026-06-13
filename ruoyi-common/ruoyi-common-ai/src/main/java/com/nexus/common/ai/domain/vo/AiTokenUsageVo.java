package com.nexus.common.ai.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI Token 用量日统计视图对象
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
public class AiTokenUsageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 用户ID */
    private Long userId;

    /** 用户名称 */
    private String userName;

    /** 模型ID */
    private Long modelId;

    /** 模型名称 */
    private String modelName;

    /** 统计日期 */
    private LocalDate usageDate;

    /** 当日输入 Token 总量 */
    private Long totalInputTokens;

    /** 当日输出 Token 总量 */
    private Long totalOutputTokens;

    /** 当日 Token 总量 */
    private Long totalTokens;

    /** 当日请求次数 */
    private Integer requestCount;

    /** 当日总成本（元） */
    private BigDecimal totalCost;

    /** 平均每次请求 Token 数（计算字段） */
    private BigDecimal avgTokensPerRequest;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;

    /** 更新时间 */
    private java.time.LocalDateTime updateTime;
}
