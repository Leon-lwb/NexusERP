package com.nexus.erp.fin.report.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 年结请求
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
public class ClosingYearlyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会计年度 */
    @NotNull(message = "会计年度不能为空")
    private Integer fiscalYear;
}
