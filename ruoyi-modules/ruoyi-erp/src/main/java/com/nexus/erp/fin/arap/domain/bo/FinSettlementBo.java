package com.nexus.erp.fin.arap.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.fin.arap.domain.FinSettlement;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 核销记录业务对象 fin_settlement(亦作核销操作入参:direction+invoiceId+receiptId+settleAmount)
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = FinSettlement.class, reverseConvertGenerate = false)
public class FinSettlementBo extends BaseEntity {

    /** 核销记录ID */
    private Long settlementId;

    /** 核销单号(自动生成,可空) */
    private String settlementNo;

    /** 方向 AR/AP */
    private String direction;

    /** 核销发票ID */
    private Long invoiceId;

    /** 发票号(冗余) */
    private String invoiceNo;

    /** 发票类型(冗余) */
    private String invoiceType;

    /** 核销收付款单ID */
    private Long receiptId;

    @NotNull(message = "核销金额不能为空")
    private BigDecimal settleAmount;

    /** 核销日期 */
    private Date settleDate;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;

    /** 批量操作ID */
    private Long[] ids;
}
