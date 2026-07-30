package com.nexus.erp.fin.gl.poster;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 过账上下文(AccountingPoster 输入)。
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据号 */
    private String sourceNo;

    /** 租户ID(异步消费时恢复,由 GlPostingConsumer 从消息填充) */
    private String tenantId;

    /** 凭证日期(默认来源单据日期) */
    private Date voucherDate;

    /** 默认摘要 */
    private String summary;
}
