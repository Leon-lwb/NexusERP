package com.nexus.erp.fin.gl.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 记账凭证视图对象 gl_voucher
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = GlVoucher.class)
public class GlVoucherVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "凭证ID")
    private Long voucherId;

    @ExcelProperty(value = "凭证号")
    private String voucherNo;

    @ExcelProperty(value = "凭证字")
    private String voucherWord;

    @ExcelProperty(value = "凭证日期")
    private Date voucherDate;

    private Long periodId;

    @ExcelProperty(value = "来源")
    private String voucherSource;

    private Long sourceId;

    private String sourceNo;

    private Integer attachmentCount;

    @ExcelProperty(value = "借方合计")
    private BigDecimal totalDebit;

    @ExcelProperty(value = "贷方合计")
    private BigDecimal totalCredit;

    private String currency;

    @ExcelProperty(value = "状态")
    private String status;

    private Long postedBy;

    private Date postedTime;

    private Integer version;

    private String remark;

    /** 明细行 */
    private List<GlVoucherLine> voucherLineList;
}
