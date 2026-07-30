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

    /** 会计期间ID */
    private Long periodId;

    @ExcelProperty(value = "来源")
    private String voucherSource;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据号 */
    private String sourceNo;

    /** 附件张数 */
    private Integer attachmentCount;

    @ExcelProperty(value = "借方合计")
    private BigDecimal totalDebit;

    @ExcelProperty(value = "贷方合计")
    private BigDecimal totalCredit;

    /** 本位币 */
    private String currency;

    @ExcelProperty(value = "状态")
    private String status;

    /** 过账人 */
    private Long postedBy;

    /** 过账时间 */
    private Date postedTime;

    /** 乐观锁版本号 */
    private Integer version;

    /** 备注 */
    private String remark;

    /** 明细行 */
    private List<GlVoucherLine> voucherLineList;
}
