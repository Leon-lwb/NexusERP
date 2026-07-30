package com.nexus.erp.fin.gl.service;

import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 凭证构建请求(供 AccountingPoster 自动制单调用)
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
public class VoucherBuildRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 凭证日期 */
    private Date voucherDate;

    /** 凭证字 J/R/P/T */
    private String voucherWord;

    /** 来源(MANUAL/SO_INVOICE/...) */
    private String voucherSource;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单据号 */
    private String sourceNo;

    /** 默认摘要(行摘要为空时回填) */
    private String summary;

    /** 分录行(借贷须平衡) */
    private List<GlVoucherLine> lines;
}
