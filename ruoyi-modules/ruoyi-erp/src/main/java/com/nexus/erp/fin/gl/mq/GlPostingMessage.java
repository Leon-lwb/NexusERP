package com.nexus.erp.fin.gl.mq;

import com.nexus.common.mq.domain.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * GL 过账消息(业务单据事件 -> AccountingPoster 异步制单)。
 * <p>tenantId 通过 BaseMessage.tenantIdStr 传递(字符串,避免雪花 ID 精度丢失)。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GlPostingMessage extends BaseMessage {

    private static final long serialVersionUID = 1L;

    /** 租户ID(字符串,避免雪花ID精度丢失,参考 PurchaseReceiveWritebackMessage) */
    private String tenantIdStr;

    /** 来源类型(MANUAL/SO_INVOICE/PU_INVOICE/RECEIPT/DEPRECIATION/COST) */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据号 */
    private String sourceNo;

    /** 凭证日期 */
    private Date voucherDate;
}
