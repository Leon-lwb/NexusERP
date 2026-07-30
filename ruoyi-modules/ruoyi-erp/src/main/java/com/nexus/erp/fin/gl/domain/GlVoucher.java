package com.nexus.erp.fin.gl.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 记账凭证头 gl_voucher
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("gl_voucher")
public class GlVoucher extends TenantEntity {

    @TableId(value = "voucher_id")
    private Long voucherId;

    /** 凭证号(业务号) */
    private String voucherNo;

    /** 凭证字 J-记 R-收 P-付 T-转 */
    private String voucherWord;

    /** 凭证日期(决定会计期间) */
    private Date voucherDate;

    /** 会计期间ID(由 voucher_date 推导) */
    private Long periodId;

    /** 来源 MANUAL/SO_INVOICE/PU_INVOICE/RECEIPT/DEPRECIATION/COST/ADJUSTMENT */
    private String voucherSource;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据号(冗余) */
    private String sourceNo;

    /** 附件张数 */
    private Integer attachmentCount;

    /** 借方合计 */
    private BigDecimal totalDebit;

    /** 贷方合计 */
    private BigDecimal totalCredit;

    /** 本位币 */
    private String currency;

    /** 状态 DRAFT/POSTED/CANCELLED */
    private String status;

    /** 过账人 */
    private Long postedBy;

    /** 过账时间 */
    private Date postedTime;

    @Version
    private Integer version;

    private String remark;

    @TableLogic
    private String delFlag;

    /** 明细行(非持久化) */
    @TableField(exist = false)
    private List<GlVoucherLine> voucherLineList;
}
