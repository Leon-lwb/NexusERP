package com.nexus.erp.fin.gl.domain.bo;

import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.Date;
import java.util.List;

/**
 * 记账凭证业务对象 gl_voucher
 * <p>voucherNo 不加 @NotBlank:由 Service 用 IdWorker 自动生成(同 so_invoice 发票号策略)。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = GlVoucher.class, reverseConvertGenerate = false)
public class GlVoucherBo extends BaseEntity {

    /** 凭证ID */
    private Long voucherId;

    /** 凭证号(自动生成) */
    private String voucherNo;

    /** 凭证字 J/R/P/T */
    private String voucherWord;

    @NotNull(message = "凭证日期不能为空")
    private Date voucherDate;

    /** 来源 */
    private String voucherSource;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据号 */
    private String sourceNo;

    /** 附件张数 */
    private Integer attachmentCount;

    /** 本位币 */
    private String currency;

    /** 状态(查询用 DRAFT/POSTED/CANCELLED) */
    private String status;

    /** 期间ID(查询用) */
    private Long periodId;

    private String remark;

    /** 明细行 */
    private List<GlVoucherLine> voucherLineList;

    /** 批量删除ID */
    private Long[] ids;
}
